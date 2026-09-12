"""Bounded coordination for isolated, opt-in client file bridges.

Each client process needs a fresh run directory. Completed command events mean
the client sent the command; only subsequent state observations prove its effect.
This module launches no processes and never uses desktop input.
"""

from __future__ import annotations

import json
import math
import os
import time
from pathlib import Path
from typing import Callable, Mapping


class BridgeError(RuntimeError):
    """Invalid protocol data or an explicitly failed bridge operation."""


class BridgeTimeout(TimeoutError):
    """A readiness, acknowledgement, or state barrier expired."""


def _deadline(timeout: float) -> float:
    if not math.isfinite(timeout) or timeout <= 0:
        raise ValueError("timeout must be finite and positive")
    return time.monotonic() + timeout


class ClientChannel:
    """One controller per fresh client run directory; not thread-safe.

    A nonempty event log or requests directory is rejected at construction to
    prevent stale readiness and acknowledgements certifying a new process. Open
    the channel before launching its bridge, and use new directories on restart.
    """

    OPERATIONS = frozenset({"input", "look", "command", "capture", "state", "release", "exit",
                            "disconnect", "reconnect", "save", "stop", "probe", "view"})
    MAX_RECORD_BYTES = 1024 * 1024

    def __init__(self, directory: str | Path, *, poll_interval: float = 0.02,
                 expected_role: str | None = None):
        if not math.isfinite(poll_interval) or poll_interval <= 0:
            raise ValueError("poll_interval must be finite and positive")
        self.directory = Path(directory).resolve()
        self.directory.mkdir(parents=True, exist_ok=True)
        self.requests = self.directory / "requests"
        self.requests.mkdir(exist_ok=True)
        self.events_path = self.directory / "events.ndjson"
        if any(self.requests.iterdir()) or (
            self.events_path.exists() and self.events_path.stat().st_size
        ):
            raise BridgeError("client channel requires a fresh run directory")
        self.poll_interval = poll_interval
        self.expected_role = expected_role
        self._sequence = 0
        self._offset = 0
        self._partial = b""
        self._pending: set[str] = set()
        self._results: dict[str, dict] = {}
        self._ready: dict | None = None

    def request(self, op: str, **args) -> str:
        if op not in self.OPERATIONS:
            raise ValueError(f"unsupported bridge operation: {op}")
        if "id" in args:
            raise ValueError("request id is assigned by the channel")
        self._sequence += 1
        request_id = f"{self._sequence:08d}"
        payload = json.dumps({"id": request_id, "op": op, **args}, allow_nan=False)
        temporary = self.requests / f"{request_id}.tmp"
        destination = temporary.with_suffix(".json")
        with temporary.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(payload + "\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, destination)
        self._pending.add(request_id)
        return request_id

    def _read(self) -> None:
        try:
            with self.events_path.open("rb") as stream:
                if os.fstat(stream.fileno()).st_size < self._offset:
                    raise BridgeError("event log was truncated; use a fresh channel after restart")
                stream.seek(self._offset)
                data = stream.read(self.MAX_RECORD_BYTES + 1)
        except FileNotFoundError:
            return
        self._offset += len(data)
        records = (self._partial + data).split(b"\n")
        self._partial = records.pop()
        if len(self._partial) > self.MAX_RECORD_BYTES:
            raise BridgeError("bridge event exceeds record size limit")
        for record in records:
            if len(record) > self.MAX_RECORD_BYTES:
                raise BridgeError("bridge event exceeds record size limit")
            try:
                event = json.loads(record)
            except (ValueError, UnicodeDecodeError) as error:
                raise BridgeError("malformed complete bridge event") from error
            if not isinstance(event, dict):
                raise BridgeError("bridge event must be an object")
            if self.expected_role is not None and event.get("role") != self.expected_role:
                raise BridgeError("bridge event role does not match its client channel")
            kind = event.get("event")
            if kind == "ready":
                self._ready = event
            elif kind in {"completed", "failed"}:
                request_id = event.get("id")
                if not isinstance(request_id, str) or request_id not in self._pending:
                    raise BridgeError(f"response for unknown request: {request_id!r}")
                if request_id in self._results:
                    raise BridgeError(f"duplicate response for request: {request_id}")
                self._results[request_id] = event

    def wait(self, request_id: str, timeout: float = 30, *, allow_failed: bool = False) -> dict:
        """Return a completion; only explicit fault inspection may allow failed events."""
        if not isinstance(request_id, str) or request_id not in self._pending:
            raise ValueError("request id does not belong to this channel")
        deadline = _deadline(timeout)
        while True:
            self._read()
            if request_id in self._results:
                event = self._results[request_id]
                if event["event"] == "failed" and not allow_failed:
                    raise BridgeError(f"request {request_id} failed: {event}")
                return event
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise BridgeTimeout(f"request {request_id} timed out in {self.directory}")
            time.sleep(min(self.poll_interval, remaining))

    def wait_ready(self, timeout: float = 30) -> dict:
        deadline = _deadline(timeout)
        while True:
            self._read()
            if self._ready is not None:
                return self._ready
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise BridgeTimeout(f"client readiness timed out in {self.directory}")
            time.sleep(min(self.poll_interval, remaining))


class MultiplayerCoordinator:
    """Actor/observer barriers with a shared wall-clock budget per operation."""

    def __init__(self, clients: Mapping[str, ClientChannel]):
        if set(clients) != {"actor", "observer"}:
            raise ValueError("a multiplayer session requires actor and observer channels")
        paths = [channel.directory for channel in clients.values()]
        if paths[0] == paths[1] or paths[0] in paths[1].parents or paths[1] in paths[0].parents:
            raise ValueError("client run directories must be separate and non-nested")
        self.clients = dict(clients)

    def ready(self, timeout: float = 60) -> dict:
        deadline = _deadline(timeout)
        return {role: channel.wait_ready(self._remaining(deadline))
                for role, channel in self.clients.items()}

    @staticmethod
    def _remaining(deadline: float) -> float:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise BridgeTimeout("multiplayer barrier timed out")
        return remaining

    def states(self, timeout: float = 30) -> dict:
        deadline = _deadline(timeout)
        requests = {role: channel.request("state") for role, channel in self.clients.items()}
        return {role: channel.wait(requests[role], self._remaining(deadline))
                for role, channel in self.clients.items()}

    def wait_state(self, predicate: Callable[[dict], bool], timeout: float = 30) -> dict:
        """Poll fresh state responses until the independently supplied oracle holds."""
        deadline = _deadline(timeout)
        while True:
            states = self.states(self._remaining(deadline))
            if predicate(states):
                return states
            time.sleep(min(0.05, self._remaining(deadline)))

    def command_and_observe(self, role: str, command: str,
                            predicate: Callable[[dict], bool], timeout: float = 30) -> dict:
        deadline = _deadline(timeout)
        channel = self.clients[role]
        request_id = channel.request("command", command=command)
        channel.wait(request_id, self._remaining(deadline))
        return self.wait_state(predicate, self._remaining(deadline))


def validate_restart_evidence(before: Mapping, after: Mapping) -> None:
    """Reject an in-process reload presented as a dedicated-server restart.

    The supervisor must record process identity as PID plus creation time, and
    populate exit_observed only after its owned process has actually terminated.
    This validates evidence shape; it cannot independently authenticate a caller.
    """
    for record in (before, after):
        if not record.get("pid") or not record.get("started_at"):
            raise BridgeError("restart evidence requires pid and started_at")
    if before.get("exit_observed") is not True:
        raise BridgeError("old server process exit was not observed")
    if (before["pid"], before["started_at"]) == (after["pid"], after["started_at"]):
        raise BridgeError("server restart reused the same process identity")
