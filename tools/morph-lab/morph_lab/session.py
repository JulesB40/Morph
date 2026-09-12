"""Explicitly configured external-server/two-client smoke session.

No default executable is supplied. The caller owns atomic resource admission,
fixture/world preparation and artifact provenance. All launched trees belong to
the process supervisor; a session cannot pass without confirmed cleanup.
"""
from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
import re
import threading
import time

from .multiplayer import BridgeError, BridgeTimeout, ClientChannel, validate_restart_evidence
from .processes import supervise


class _Owned:
    def __init__(self, argv, cwd, logs, timeout, env):
        self.cancel = threading.Event()
        self.result = None
        self.logs = logs
        def execute():
            try:
                self.result = supervise(argv, cwd, logs, timeout, env=env,
                                        cancelcallback=self.cancel.is_set)
            except Exception as error:
                self.result = {"status":"infrastructure_failure", "error":str(error),
                               "cleanup_confirmed":False}
        self.thread = threading.Thread(target=execute, daemon=True)
        self.thread.start()


def run_session(spec: dict, run: str | Path, source: str | Path) -> dict:
    """Run the fixed smoke contract; return and persist honest evidence/status.

    spec.roles requires server/actor/observer argv lists and cwd strings, with
    optional env mappings. Placeholders: source, run, role, port, control, game.
    game is stable across server restart; control and logs are generation-specific.
    Spec timeout and step_timeout are positive seconds. No shell is invoked.
    """
    run, source = Path(run).resolve(), Path(source).resolve(strict=True)
    if set(spec.get("roles", {})) != {"server", "actor", "observer"}:
        raise ValueError("spec.roles requires server, actor and observer")
    timeout, step_timeout = spec.get("timeout", 300), spec.get("step_timeout", 30)
    for value in (timeout, step_timeout):
        if not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
            raise ValueError("timeouts must be finite and positive")
    port = spec.get("port")
    if type(port) is not int or not 1 <= port <= 65535:
        raise ValueError("spec.port must be an explicitly reserved port")
    probes = spec.get("component_probes", [])
    if not isinstance(probes, list) or any(name not in {"wither-heads", "sniffer-middle-legs"}
                                           for name in probes) or len(probes) != len(set(probes)):
        raise ValueError("component_probes must list distinct supported probe names")
    if type(spec.get("capture_recovery", False)) is not bool:
        raise ValueError("capture_recovery must be boolean")
    if type(spec.get("frame_scene", False)) is not bool:
        raise ValueError("frame_scene must be boolean")
    names = spec.get("players", {"actor":"MorphActor", "observer":"MorphObserver"})
    if set(names) != {"actor", "observer"} or len(set(names.values())) != 2 or any(
            not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9_]{1,16}", name)
            for name in names.values()):
        raise ValueError("two distinct Minecraft player names are required")
    # Validate templates before any process can be launched.
    for role, config in spec["roles"].items():
        fields = dict(source=str(source), run=str(run), role=role, port=port,
                      control=str(run / role / "control-1"), game=str(run / role / "game"))
        argv = config.get("argv")
        if not isinstance(argv, list) or not argv or any(not isinstance(x, str) for x in argv):
            raise ValueError("every role needs an explicit argv list")
        for value in argv + [config["cwd"]] + list(config.get("env", {}).values()):
            value.format_map(fields)
    run.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + timeout
    owned, active, channels = [], {}, {}
    result = {"schema":1, "status":"infrastructure_failure", "spec":spec, "checks":[],
              "processes":[], "captures":[], "cleanup_confirmed":False,
              "limitations":["PNG presence and client state do not prove visual correctness.",
                             "Input isolation checks process-owned state, not foreground-app behavior."]}

    def remaining(limit=None):
        value = deadline - time.monotonic()
        if value <= 0: raise BridgeTimeout("session wall-clock deadline expired")
        return min(value, limit) if limit is not None else value

    def healthy():
        for role, process in active.items():
            if process.result is not None:
                raise BridgeError(f"{role} exited unexpectedly: {process.result}")

    def launch(role, generation=1):
        config = spec["roles"][role]
        control, game = run / role / f"control-{generation}", run / role / "game"
        game.mkdir(parents=True, exist_ok=True)
        channel = ClientChannel(control, expected_role=role)
        fields = dict(source=str(source), run=str(run), role=role, port=port,
                      control=str(control), game=str(game))
        argv = [value.format_map(fields) for value in config["argv"]]
        cwd = config["cwd"].format_map(fields)
        env = {key:value.format_map(fields) for key,value in config.get("env", {}).items()}
        process = _Owned(argv, cwd, run / role / f"logs-{generation}", remaining(), env)
        owned.append(process)
        active[role], channels[role] = process, channel

    def await_event(role, request_id=None, end=None, *, allow_failed=False):
        end = end if end is not None else time.monotonic() + remaining(step_timeout)
        while True:
            budget = min(remaining(), end - time.monotonic())
            if budget <= 0: raise BridgeTimeout(f"{role} bridge barrier expired")
            try:
                channel = channels[role]
                return (channel.wait_ready(min(.1, budget)) if request_id is None
                        else channel.wait(request_id, min(.1, budget), allow_failed=allow_failed))
            except BridgeTimeout:
                healthy()

    def request(role, op, **args):
        return await_event(role, channels[role].request(op, **args))["detail"]

    def states(end=None):
        end = end if end is not None else time.monotonic() + remaining(step_timeout)
        ids = {role:channel.request("state") for role,channel in channels.items()}
        return {role:await_event(role, request_id, end)["detail"] for role,request_id in ids.items()}

    def barrier(predicate):
        end = time.monotonic() + remaining(step_timeout)
        while True:
            rows = states(end)
            if predicate(rows): return rows
            if time.monotonic() >= end: raise BridgeTimeout("state expectation did not become true")
            time.sleep(min(.05, remaining()))

    def player(state, uuid):
        matches = [p for p in state.get("players", []) if p.get("uuid") == uuid]
        return matches[0] if len(matches) == 1 else {}

    def exit_process(role):
        process = active.pop(role)
        process.thread.join(remaining(step_timeout))
        if process.thread.is_alive(): raise BridgeTimeout(f"{role} did not exit after stop")
        if process.result.get("status") != "passed" or not process.result.get("cleanup_confirmed"):
            raise BridgeError(f"{role} did not exit cleanly: {process.result}")
        return dict(process.result, exit_observed=True)

    try:
        launch("server")
        await_event("server")
        for role in ("actor", "observer"): launch(role)
        for role in ("actor", "observer"): await_event(role)
        initial = barrier(lambda rows: all(rows[r].get("connected") is True for r in names)
                          and all(any(p.get("name") == name for p in rows["server"].get("players", []))
                                  for name in names.values()))
        uuids = {role: initial[role]["uuid"] for role in names}
        if len(set(uuids.values())) != 2: raise BridgeError("client identities are not distinct")
        if any(player(initial["server"], uuids[role]).get("name") != names[role] for role in names):
            raise BridgeError("server identities do not match configured client names")
        result["identities"] = uuids
        for role, name in names.items():
            request("server", "command", command=f"op {name}")
            request("server", "command", command=f"gamemode creative {name}")
        # Explicit flat staging pad and separated spawn positions for movement.
        for command in ("fill -16 79 -16 16 79 16 minecraft:stone",
                        "fill -16 80 -16 16 84 16 minecraft:air",
                        f"tp {names['actor']} 0 80 0", f"tp {names['observer']} 6 80 0"):
            request("server", "command", command=command)
        request("actor", "command", command="morph grant minecraft:bat")
        request("actor", "command", command="morph select minecraft:bat")
        request("actor", "command", command="morph nametag off")
        def appearance(rows):
            return (all(rows[role].get("uuid") == uuid for role, uuid in uuids.items())
                    and all(player(rows[role], uuids["actor"]).get("form") == "minecraft:bat"
                       and player(rows[role], uuids["actor"]).get("showNameTag") is False
                       for role in ("server", "actor", "observer")))
        result["appearance_before_restart"] = barrier(appearance)
        result["checks"].append("authoritative_and_two_client_appearance")
        result["component_probes"] = []
        for name in probes:
            event = await_event("actor", channels["actor"].request("probe", name=name), allow_failed=True)
            result["component_probes"].append({"name":name,"event":event,
                "passed":event["event"] == "completed" and event.get("detail", {}).get("passed") is True})
        request("actor", "look", yaw=0, pitch=0)
        request("observer", "look", yaw=90, pitch=0)
        before = states()
        request("actor", "input", keys=["forward"], ticks=20)
        def horizontal(rows, role):
            position = player(rows["server"], uuids[role])["position"]
            return position[0], position[2]
        def movement_received(rows):
            distances = {role: math.dist(horizontal(before, role), horizontal(rows, role)) for role in names}
            if distances["observer"] > .1:
                raise AssertionError(f"observer moved during actor input: {distances}")
            return distances["actor"] >= .1
        # The input acknowledgement releases client keys. It does not establish
        # that the dedicated server has processed that client's movement packets.
        after = barrier(movement_received)
        moved = {role: math.dist(horizontal(before, role), horizontal(after, role)) for role in names}
        result["movement"] = {"before":before, "after":after, "horizontal_distance":moved}
        result["checks"].append("actor_input_observer_stationary")
        if spec.get("frame_scene", False):
            # Wait in client ticks beyond the current 100-tick transformation.
            request("actor", "input", keys=[], ticks=120)
            request("actor", "view", perspective="third_person_front", hideGui=True)
            request("observer", "view", perspective="first_person", hideGui=True)
            scene = states()
            eye = scene["observer"]["eyePosition"]
            target = list(player(scene["server"], uuids["actor"])["position"])
            target[1] += .4
            if len(eye) != 3 or not all(math.isfinite(value) for value in list(eye) + target):
                raise BridgeError("scene camera positions must be finite three-dimensional coordinates")
            dx, dy, dz = (target[index] - eye[index] for index in range(3))
            yaw = math.degrees(math.atan2(-dx, dz))
            pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
            request("observer", "look", yaw=yaw, pitch=pitch)
            result["frame_scene"] = {"eye":eye,"target":target,"yaw":yaw,"pitch":pitch,
                                      "meaning":"camera framing requested; PNG requires visual inspection"}
        if spec.get("capture_recovery", False):
            event = await_event("actor", channels["actor"].request("probe", name="capture-failure"),
                                allow_failed=True)
            result["capture_recovery"] = {"injection":event}
            detail = event.get("detail", {})
            if (event["event"] != "failed" or detail.get("probe") != "capture-failure"
                    or detail.get("injected") is not True or detail.get("recoverable") is not True):
                raise AssertionError("capture probe did not report the expected recoverable injected failure")
            recovered_state = request("actor", "state")
            result["capture_recovery"]["state_after_failure"] = recovered_state
            if recovered_state.get("connected") is not True or recovered_state.get("uuid") != uuids["actor"]:
                raise AssertionError("actor did not remain connected after capture failure")
        for role in names:
            detail = request(role, "capture")
            path = (channels[role].directory / detail["png"]).resolve()
            if channels[role].directory not in path.parents:
                raise BridgeError("capture escaped client directory")
            content = path.read_bytes()
            if not content.startswith(b"\x89PNG\r\n\x1a\n") or len(content) <= 24:
                raise BridgeError("capture is not a nonempty PNG")
            result["captures"].append({"role":role,"path":str(path),
                                       "sha256":hashlib.sha256(content).hexdigest()})
        if spec.get("capture_recovery", False):
            result["capture_recovery"]["capture"] = next(row for row in result["captures"] if row["role"] == "actor")
            result["checks"].append("injected_capture_failure_then_state_and_png")
        for role in names: request(role, "disconnect")
        barrier(lambda rows: not rows["server"].get("players") and
                all(rows[role].get("connected") is False for role in names))
        request("server", "save")
        request("server", "stop")
        old_server = exit_process("server")
        launch("server", 2)
        await_event("server")
        # Supervisor writes this before resuming the root process on Windows.
        new_server = json.loads((active["server"].logs / "process.json").read_text())
        validate_restart_evidence(old_server, new_server)
        result["restart"] = {"before":old_server,"after":new_server}
        for role in names: request(role, "reconnect")
        result["appearance_after_restart"] = barrier(appearance)
        result["checks"].append("real_server_restart_persisted_appearance")
        for role in names:
            request(role, "exit")
            exit_process(role)
        request("server", "stop")
        exit_process("server")
        result["status"] = "passed" if all(probe["passed"] for probe in result["component_probes"]) else "failed"
    except BridgeTimeout as error:
        result.update(status="timeout", error=str(error))
    except AssertionError as error:
        result.update(status="failed", error=str(error))
    except Exception as error:
        result.update(status="infrastructure_failure", error=f"{type(error).__name__}: {error}")
    finally:
        for process in owned: process.cancel.set()
        cleanup_deadline = time.monotonic() + 12
        for process in owned: process.thread.join(max(0, cleanup_deadline - time.monotonic()))
        result["processes"] = [process.result or {"status":"infrastructure_failure",
                               "cleanup_confirmed":False, "error":"supervisor still running"}
                               for process in owned]
        result["cleanup_confirmed"] = all(row.get("cleanup_confirmed") is True
                                           for row in result["processes"])
        if not result["cleanup_confirmed"]:
            result["status"] = "infrastructure_failure"
        (run / "session-result.json").write_text(json.dumps(result, indent=2) + "\n")
    return result
