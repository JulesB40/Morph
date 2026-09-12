"""Prepare one frozen build, then run the isolated two-client restart smoke."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import socket
import sys

from morph_lab.processes import supervise
from morph_lab.session import run_session


def prepare_fixture(run: Path, port: int) -> None:
    for role in ("server", "actor", "observer"):
        for path in (run / role, run / role / "game"):
            if path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction()):
                raise ValueError("Fixture directories must not be links")
            if not path.resolve().is_relative_to(run.resolve()):
                raise ValueError("Fixture directory escapes the owned run")
    server = run / "server" / "game"
    server.mkdir(parents=True, exist_ok=False)
    # This fixture is a disposable local development server, bound to loopback.
    (server / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (server / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "enforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\n"
        "spawn-protection=0\nlevel-type=minecraft:flat\nlevel-seed=4815162342\n"
        "generate-structures=false\nmax-players=2\ndifficulty=normal\n"
        "pause-when-empty-seconds=-1\n", encoding="utf-8")
    for role in ("actor", "observer"):
        game = run / role / "game"
        game.mkdir(parents=True, exist_ok=False)
        (game / "options.txt").write_text(
            "pauseOnLostFocus:false\nrenderDistance:2\nsimulationDistance:2\n"
            "maxFps:30\nenableVsync:false\nguiScale:2\n"
            "overrideWidth:854\noverrideHeight:480\n", encoding="utf-8")


def launch_provenance(source: Path, launch_file: Path, launch: dict) -> dict:
    """Hash exact launcher inputs, including classpath files omitted by *Args.txt.

    This accounts for the exported Java executable and argument-file definitions;
    it does not claim to hash every dependency or compiled class referenced inside
    the classpath. The queue's source/artifact manifest remains necessary.
    """
    if launch.get("schema") != 1 or set(launch.get("roles", {})) != {"client", "server"}:
        raise ValueError("Expected schema-1 client/server launch export")
    if json.loads(launch_file.read_text(encoding="utf-8")) != launch:
        raise ValueError("Launch export changed while it was being read")
    files = {launch_file.resolve()}
    for entry in launch["roles"].values():
        if entry.get("cwd") != "{game}":
            raise ValueError("Every launch cwd must be its owned {game} directory")
        argv = entry.get("argv")
        if not isinstance(argv, list) or not argv or any(not isinstance(arg, str) for arg in argv):
            raise ValueError("Launch argv must be a nonempty string list")
        executable = Path(argv[0])
        if not executable.is_absolute() or not executable.is_file():
            raise ValueError("Launch executable must be an existing absolute file")
        files.add(executable.resolve())
        for arg in argv[1:]:
            if arg.startswith("@"):
                path = Path(arg[1:])
                if not path.is_absolute() or not path.resolve().is_relative_to(source):
                    raise ValueError("Argument files must be absolute paths in the prepared source")
                content = path.read_bytes()
                # The pinned exporter writes flat files. Refuse nested expansion
                # rather than silently leave an indirect launcher input unhashed.
                if re.search(rb"(?:^|\s)[\"']?@", content):
                    raise ValueError("Nested argument files are not supported by this exporter")
                files.add(path.resolve())
    return {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(files)}


def execute(source: Path, run: Path) -> dict:
    source, run = source.resolve(strict=True), run.resolve()
    if not source.is_dir() or run.is_relative_to(source) or source.is_relative_to(run):
        raise ValueError("Source and disposable run must be separate directories")
    # Failure here grants no ownership: main must not replace existing evidence.
    run.mkdir(parents=True, exist_ok=False)
    result = {"status": "infrastructure_failure", "cleanup_confirmed": False, "phase": "prepare"}
    prepared = None
    provenance = None
    try:
        prepared = supervise(
            ["cmd.exe", "/d", "/c", "gradlew.bat", "--no-daemon", "--max-workers=2",
             "-Dorg.gradle.jvmargs=-Xmx1G", "-PmorphLab=true", "-I",
             "tools/morph-lab/export-launch.gradle", "exportMorphLabLaunch"],
            source, run / "prepare-logs", 600)
        result.update(prepared)
        if prepared.get("cleanup_confirmed") is not True:
            raise RuntimeError("Preparation process cleanup was not confirmed")
        if prepared["status"] == "passed":
            result["phase"] = "fixture"
            launch_file = source / "build/morph-lab/launch.json"
            launch = json.loads(launch_file.read_text(encoding="utf-8"))
            # The socket is released on every fixture/validation failure. This is
            # only an availability probe, not an atomic reservation across exec.
            with socket.socket() as reservation:
                reservation.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
                reservation.bind(("127.0.0.1", 0))
                port = reservation.getsockname()[1]
                prepare_fixture(run, port)
                provenance = launch_provenance(source, launch_file, launch)
                (run / "launch-provenance.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
                roles = {}
                for role in ("server", "actor", "observer"):
                    entry = launch["roles"]["server" if role == "server" else "client"]
                    name = "MorphActor" if role == "actor" else "MorphObserver"
                    roles[role] = {"argv": [arg.replace("{username}", name) for arg in entry["argv"]],
                                   "cwd": entry["cwd"], "env": entry.get("env", {})}
            result.update(phase="session", cleanup_confirmed=False)
            result = run_session({"roles": roles, "port": port, "timeout": 480,
                                  "step_timeout": 90, "min_free_memory_mb": 384,
                                  "component_probes": ["wither-heads", "sniffer-middle-legs", "dragon-renderer", "renderer-fault-recovery"],
                                  "capture_recovery": True, "frame_scene": True}, run, source)
            result["port_reservation"] = "Loopback availability probe; a successful owned-server ready event is also required."
    except Exception as error:
        result.update(status="infrastructure_failure", error=f"{type(error).__name__}: {error}")
    if prepared is not None:
        result["preparation"] = prepared
    if provenance is not None:
        changed = []
        for filename, expected in provenance.items():
            try:
                actual = hashlib.sha256(Path(filename).read_bytes()).hexdigest()
            except OSError:
                actual = None
            if actual != expected:
                changed.append(filename)
        result["launch_provenance"] = {"before": provenance, "changed_after_session": changed,
                                       "verified": not changed,
                                       "scope": "launch JSON, Java executable and flat argument files; not files referenced by classpath entries"}
        if changed:
            result.update(status="infrastructure_failure", error="Launcher inputs changed during the session")
    if result.get("cleanup_confirmed") is not True:
        result["status"] = "infrastructure_failure"
    persisted = dict(result, status={"passed": "pass", "failed": "fail",
                                    "infrastructure_failure": "infra_error"}.get(result["status"], result["status"]))
    (run / "result.json").write_text(json.dumps(persisted, indent=2) + "\n", encoding="utf-8")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--run", required=True, type=Path)
    args = parser.parse_args()
    try:
        result = execute(args.source.resolve(strict=True), args.run.resolve())
    except Exception as error:
        result = {"status": "infrastructure_failure", "error": f"{type(error).__name__}: {error}"}
    result["status"] = {"passed": "pass", "failed": "fail",
                        "infrastructure_failure": "infra_error"}.get(result["status"], result["status"])
    print(json.dumps({"status": result["status"], "error": result.get("error")}))
    return 0 if result["status"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
