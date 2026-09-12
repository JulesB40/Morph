"""Prepare one frozen build, then run the isolated two-client restart smoke."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import socket
import sys

from morph_lab.processes import supervise
from morph_lab.session import run_session


def prepare_fixture(run: Path, port: int) -> None:
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


def execute(source: Path, run: Path) -> dict:
    run.mkdir(parents=True, exist_ok=False)
    prepared = supervise(
        ["cmd.exe", "/d", "/c", "gradlew.bat", "--no-daemon", "--max-workers=2",
         "-Dorg.gradle.jvmargs=-Xmx1G", "-PmorphLab=true", "-I",
         "tools/morph-lab/export-launch.gradle", "exportMorphLabLaunch"],
        source, run / "prepare-logs", 600)
    if prepared["status"] != "passed":
        return dict(prepared, phase="prepare")
    launch = json.loads((source / "build/morph-lab/launch.json").read_text(encoding="utf-8"))
    # The queue reserves the entire session. The bind probes OS availability;
    # a foreign bind after release fails startup rather than connecting elsewhere.
    with socket.socket() as reservation:
        reservation.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
        prepare_fixture(run, port)
        roles = {}
        for role in ("server", "actor", "observer"):
            entry = launch["roles"]["server" if role == "server" else "client"]
            name = "MorphActor" if role == "actor" else "MorphObserver"
            roles[role] = {
                "argv": [arg.replace("{username}", name) for arg in entry["argv"]],
                "cwd": entry.get("cwd", "{game}"), "env": entry.get("env", {})}
    result = run_session({"roles": roles, "port": port, "timeout": 480,
                          "step_timeout": 90,
                          "component_probes": ["wither-heads", "sniffer-middle-legs"],
                          "capture_recovery": True, "frame_scene": True}, run, source)
    result["preparation"] = prepared
    result["port_reservation"] = "Loopback availability probe; server bind races fail closed."
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
    args.run.mkdir(parents=True, exist_ok=True)
    (args.run / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "error": result.get("error")}))
    return 0 if result["status"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
