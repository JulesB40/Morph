"""Bounded external-server/single-client component and fault probe lane."""
from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
import re
import time

from .multiplayer import BridgeError, BridgeTimeout, ClientChannel
from .session import _Owned


PROBES = ("wither-heads", "sniffer-middle-legs", "dragon-renderer", "renderer-fault-recovery", "descriptor-rendering", "captured-equipment")


def run_probe_session(spec: dict, run: str | Path, source: str | Path) -> dict:
    """Launch exactly server+actor; use session role templates and owned cleanup.

    Optional fixture_form is minecraft:bat or minecraft:pig. component_probes
    defaults to all four native component probes; capture_recovery defaults true.
    frame_scene optionally requests third-person front view with the GUI hidden.
    """
    run, source = Path(run).resolve(), Path(source).resolve(strict=True)
    if set(spec.get("roles", {})) != {"server", "actor"}:
        raise ValueError("single-client probe roles must be exactly server and actor")
    timeout, step = spec.get("timeout", 300), spec.get("step_timeout", 30)
    for value in (timeout, step):
        if type(value) not in (int, float) or not math.isfinite(value) or value <= 0:
            raise ValueError("timeouts must be finite and positive")
    port, floor = spec.get("port"), spec.get("min_free_memory_mb")
    if type(port) is not int or not 1 <= port <= 65535:
        raise ValueError("port must be explicitly reserved")
    if floor is not None and (type(floor) is not int or floor < 0):
        raise ValueError("min_free_memory_mb must be a nonnegative integer")
    probes = spec.get("component_probes", list(PROBES))
    if not isinstance(probes, list) or any(not isinstance(p, str) or p not in PROBES for p in probes) or len(set(probes)) != len(probes):
        raise ValueError("component_probes must contain distinct supported names")
    for flag in ("capture_recovery", "frame_scene"):
        if flag in spec and type(spec[flag]) is not bool:
            raise ValueError(f"{flag} must be boolean")
    form = spec.get("fixture_form")
    if form not in (None, "minecraft:bat", "minecraft:pig"):
        raise ValueError("fixture_form must be minecraft:bat or minecraft:pig")
    templates = {}
    for role, config in spec["roles"].items():
        fields = dict(source=str(source), run=str(run), role=role, port=port,
                      control=str(run/role/"control-1"), game=str(run/role/"game"))
        argv = config.get("argv")
        if not isinstance(argv, list) or not argv or any(not isinstance(v, str) or not v or "\0" in v for v in argv):
            raise ValueError("roles require explicit argv lists")
        templates[role] = ([v.format_map(fields) for v in argv],
                           config["cwd"].format_map(fields),
                           {k:v.format_map(fields) for k,v in config.get("env", {}).items()})
    run.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic()+timeout
    owned, active, channels = [], {}, {}
    result = {"schema":1,"lane":"single-client-components","status":"infrastructure_failure",
              "spec":spec,"component_probes":[],"checks":[],"captures":[],
              "limitations":["One client only: no multiplayer isolation or restart verification.",
                             "Native component results and PNG files do not alone prove visible rendering correctness."]}

    def budget(end=None):
        remaining = min(deadline, end if end is not None else deadline)-time.monotonic()
        if remaining <= 0: raise BridgeTimeout("single-client probe deadline expired")
        return remaining

    def memory():
        if floor is not None:
            from .cli import available_memory_mb
            if available_memory_mb(0) < floor:
                raise BridgeError("Stopped owned probe session because physical memory headroom is too low")

    def await_event(role, ident=None, allow_failed=False):
        end = time.monotonic()+min(step,budget())
        while True:
            memory()
            try:
                channel = channels[role]
                return (channel.wait_ready(min(.1,budget(end))) if ident is None else
                        channel.wait(ident,min(.1,budget(end)),allow_failed=allow_failed))
            except BridgeTimeout:
                budget(end)
                for name, process in active.items():
                    if process.result is not None:
                        raise BridgeError(f"{name} exited before its acknowledgement: {process.result}")

    def request(role, op, **args):
        return await_event(role,channels[role].request(op,**args))["detail"]

    def stop(role, op):
        request(role,op)
        process=active.pop(role)
        process.thread.join(min(step,budget()))
        if process.thread.is_alive(): raise BridgeTimeout(f"{role} did not exit")
        if process.result.get("status") != "passed" or process.result.get("cleanup_confirmed") is not True:
            raise BridgeError(f"{role} did not stop cleanly: {process.result}")

    try:
        for role in ("server","actor"):
            memory()
            (run/role/"game").mkdir(parents=True,exist_ok=True)
            channels[role]=ClientChannel(run/role/"control-1",expected_role=role)
            argv,cwd,env=templates[role]
            process=_Owned(argv,cwd,run/role/"logs-1",budget(),env)
            owned.append(process)
            active[role]=process
            await_event(role)
        actor=request("actor","state")
        name, uuid=actor.get("name"),actor.get("uuid")
        if actor.get("connected") is not True or not isinstance(name,str) or not re.fullmatch(r"[A-Za-z0-9_]{1,16}",name) or not uuid:
            raise BridgeError("actor readiness lacks a connected player identity")
        result["identity"]={"name":name,"uuid":uuid}
        request("server","command",command=f"op {name}")
        if form is not None:
            for command in (f"gamemode creative {name}","fill -8 79 -8 8 79 8 minecraft:stone",
                            "fill -8 80 -8 8 84 8 minecraft:air",f"tp {name} 0 80 0"):
                request("server","command",command=command)
            request("actor","command",command=f"morph grant {form}")
            request("actor","command",command=f"morph select {form}")
            end=time.monotonic()+min(step,budget())
            while True:
                server_state=request("server","state")
                actor_state=request("actor","state")
                if all(any(p.get("uuid")==uuid and p.get("form")==form for p in state.get("players",[]))
                       for state in (server_state,actor_state)):
                    result["fixture"]={"form":form,"server":server_state,"actor":actor_state}
                    break
                budget(end)
        for name in probes:
            event=await_event("actor",channels["actor"].request("probe",name=name),allow_failed=True)
            result["component_probes"].append({"name":name,"event":event,
                "passed":event["event"]=="completed" and event.get("detail",{}).get("passed") is True})
        if spec.get("frame_scene",False):
            request("actor","input",keys=[],ticks=120)
            request("actor","view",perspective="third_person_front",hideGui=True)
        if spec.get("capture_recovery",True):
            event=await_event("actor",channels["actor"].request("probe",name="capture-failure"),allow_failed=True)
            result["capture_recovery"]={"injection":event}
            detail=event.get("detail",{})
            if event["event"]!="failed" or detail.get("probe")!="capture-failure" or detail.get("injected") is not True or detail.get("recoverable") is not True:
                raise AssertionError("expected recoverable capture failure did not occur")
            state=request("actor","state")
            result["capture_recovery"]["state_after_failure"]=state
            if state.get("connected") is not True or state.get("uuid")!=uuid:
                raise AssertionError("actor state did not recover after injected failure")
        detail=request("actor","capture")
        capture=(channels["actor"].directory/detail["png"]).resolve()
        if channels["actor"].directory not in capture.parents:
            raise BridgeError("capture escaped actor directory")
        content=capture.read_bytes()
        if not content.startswith(b"\x89PNG\r\n\x1a\n") or len(content)<=24:
            raise BridgeError("capture lacks a nonempty PNG signature")
        result["captures"].append({"role":"actor","path":str(capture),"sha256":hashlib.sha256(content).hexdigest()})
        if spec.get("capture_recovery",True): result["checks"].append("injected_capture_failure_then_state_and_png")
        stop("actor","exit")
        stop("server","stop")
        result["status"]="passed" if all(p["passed"] for p in result["component_probes"]) else "failed"
    except BridgeTimeout as error:
        result.update(status="timeout",error=str(error))
    except AssertionError as error:
        result.update(status="failed",error=str(error))
    except Exception as error:
        result.update(status="infrastructure_failure",error=f"{type(error).__name__}: {error}")
    finally:
        for process in owned: process.cancel.set()
        cleanup_deadline=time.monotonic()+12
        for process in owned: process.thread.join(max(0,cleanup_deadline-time.monotonic()))
        result["processes"]=[process.result or {"cleanup_confirmed":False,"error":"supervisor still running"} for process in owned]
        result["cleanup_confirmed"]=all(p.get("cleanup_confirmed") is True for p in result["processes"])
        if not result["cleanup_confirmed"]: result["status"]="infrastructure_failure"
        (run/"session-result.json").write_text(json.dumps(result,indent=2)+"\n")
    return result
