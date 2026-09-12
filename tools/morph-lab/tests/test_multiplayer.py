"""Protocol tests use temporary files and harmless child Python interpreters."""

import json
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from morph_lab.multiplayer import (BridgeError, BridgeTimeout, ClientChannel,
                                   MultiplayerCoordinator, validate_restart_evidence)


FAKE_BRIDGE = r'''
import json, pathlib, sys, time
directory = pathlib.Path(sys.argv[1])
role = sys.argv[2]
seen = set()
deadline = time.monotonic() + 5
def emit(event):
    with (directory / "events.ndjson").open("a", encoding="utf-8") as out:
        out.write(json.dumps(event) + "\n")
emit({"event":"ready", "role":role})
while time.monotonic() < deadline:
    for path in sorted((directory / "requests").glob("*.json")):
        if path.name in seen: continue
        seen.add(path.name)
        request = json.loads(path.read_text())
        emit({"event":"completed", "id":request["id"], "detail":{
            "role":role, "op":request["op"], "form":"minecraft:player"}})
        if request["op"] == "exit": sys.exit(0)
    time.sleep(.005)
'''


class MultiplayerTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.processes = []

    def tearDown(self):
        for process in self.processes:
            if process.poll() is None:
                process.terminate()
            process.wait(timeout=5)
        self.temporary.cleanup()

    def channel(self, name="actor"):
        return ClientChannel(self.root / name, poll_interval=.005)

    def emit(self, channel, event):
        with channel.events_path.open("a", encoding="utf-8") as out:
            out.write(json.dumps(event) + "\n")

    def bridge(self, channel, role):
        kwargs = {"creationflags": subprocess.CREATE_NO_WINDOW} if sys.platform == "win32" else {}
        process = subprocess.Popen([sys.executable, "-c", FAKE_BRIDGE,
                                    str(channel.directory), role], **kwargs)
        self.processes.append(process)

    def test_atomic_request_and_out_of_order_ack(self):
        channel = self.channel()
        first = channel.request("look", yaw=12, pitch=-3)
        second = channel.request("state")
        self.assertEqual(list(channel.requests.glob("*.tmp")), [])
        self.assertEqual(json.loads((channel.requests / f"{first}.json").read_text()),
                         {"id":first,"op":"look","yaw":12,"pitch":-3})
        self.emit(channel, {"event":"completed","id":second})
        self.emit(channel, {"event":"completed","id":first})
        self.assertEqual(channel.wait(first, .2)["id"], first)
        self.assertEqual(channel.wait(second, .2)["id"], second)

    def test_partial_record_waits_for_newline(self):
        channel = self.channel()
        request_id = channel.request("state")
        record = json.dumps({"event":"completed","id":request_id})
        channel.events_path.write_text(record, encoding="utf-8")
        with self.assertRaises(BridgeTimeout):
            channel.wait(request_id, .03)
        with channel.events_path.open("a") as out:
            out.write("\n")
        self.assertEqual(channel.wait(request_id, .2)["id"], request_id)

    def test_unknown_failed_and_duplicate_responses(self):
        channel = self.channel()
        self.emit(channel, {"event":"completed","id":"foreign"})
        with self.assertRaises(BridgeError): channel.wait_ready(.1)
        other = self.channel("other")
        request_id = other.request("state")
        self.emit(other, {"event":"failed","id":request_id,"detail":"failure"})
        with self.assertRaises(BridgeError): other.wait(request_id, .1)
        self.emit(other, {"event":"completed","id":request_id})
        with self.assertRaises(BridgeError): other.wait(request_id, .1)

    def test_stale_malformed_and_truncated_events_rejected(self):
        channel = self.channel()
        self.emit(channel, {"event":"ready"})
        with self.assertRaises(BridgeError): self.channel()
        channel.wait_ready(.1)
        channel.events_path.write_text("")
        with self.assertRaises(BridgeError): channel.wait_ready(.1)
        other = self.channel("other")
        other.events_path.write_text("{bad}\n")
        with self.assertRaises(BridgeError): other.wait_ready(.1)

    def test_bounded_deadlines_and_invalid_operations(self):
        channel = self.channel()
        start = time.monotonic()
        with self.assertRaises(BridgeTimeout): channel.wait_ready(.03)
        self.assertLess(time.monotonic() - start, .5)
        for timeout in (0, -1, float("inf"), float("nan")):
            with self.assertRaises(ValueError): channel.wait_ready(timeout)
        with self.assertRaises(ValueError): channel.request("arbitrary")
        with self.assertRaises(ValueError): channel.request("state", id="spoof")

    def test_real_fake_processes_are_isolated_and_ack_is_not_state(self):
        actor, observer = self.channel(), self.channel("observer")
        self.bridge(actor, "actor")
        self.bridge(observer, "observer")
        coordinator = MultiplayerCoordinator({"actor":actor,"observer":observer})
        ready = coordinator.ready(3)
        self.assertEqual(ready["actor"]["role"], "actor")
        self.assertEqual(ready["observer"]["role"], "observer")
        states = coordinator.states(2)
        for role in ("actor", "observer"):
            self.assertEqual(states[role]["detail"]["role"], role)
        with self.assertRaises(BridgeTimeout):
            coordinator.command_and_observe("actor", "morph select minecraft:bat",
                lambda rows: all(row["detail"]["form"] == "minecraft:bat"
                                 for row in rows.values()), timeout=.15)
        for channel in (actor, observer):
            channel.wait(channel.request("exit"), 1)
        for process in self.processes:
            self.assertEqual(process.wait(timeout=2), 0)

    def test_role_directory_and_restart_requirements(self):
        actor = self.channel()
        with self.assertRaises(ValueError):
            MultiplayerCoordinator({"actor":actor,"observer":actor})
        before = {"pid":12,"started_at":"first","exit_observed":True}
        validate_restart_evidence(before, {"pid":12,"started_at":"second"})
        with self.assertRaises(BridgeError): validate_restart_evidence(before, before)
        with self.assertRaises(BridgeError):
            validate_restart_evidence({"pid":12,"started_at":"first"},
                                      {"pid":13,"started_at":"second"})

    def test_crossed_role_response_is_rejected_even_with_matching_id(self):
        actor = ClientChannel(self.root / "actor", expected_role="actor")
        request_id = actor.request("state")
        self.emit(actor, {"event":"completed", "id":request_id, "role":"observer"})
        with self.assertRaises(BridgeError): actor.wait(request_id, .1)

    def test_failed_outcome_requires_explicit_opt_in(self):
        channel = self.channel()
        request_id = channel.request("probe", name="capture-failure")
        self.emit(channel, {"event":"failed", "id":request_id, "detail":{"injected":True}})
        with self.assertRaises(BridgeError): channel.wait(request_id, .1)
        self.assertEqual(channel.wait(request_id, .1, allow_failed=True)["event"], "failed")
        with self.assertRaises(BridgeError): channel.wait(request_id, .1)


if __name__ == "__main__":
    unittest.main()
