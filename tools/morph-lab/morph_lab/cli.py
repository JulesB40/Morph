"""Queue immutable verification jobs; never run tests from an editable checkout."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import shutil
import sys
import time
import threading
import uuid


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    temp.replace(path)


def read_json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected an object: {path}")
    return value


def contained(root: Path, path: Path) -> Path:
    result = path.resolve()
    if not result.is_relative_to(root.resolve()) or result == root.resolve():
        raise ValueError(f"Path must be inside {root}: {path}")
    return result


def expand(value: str, source: Path, run: Path) -> str:
    return value.replace("{source}", str(source)).replace("{run}", str(run))


def validate_spec(spec: dict) -> float:
    argv = spec.get("argv")
    if not isinstance(argv, list) or not argv or any(not isinstance(x, str) or not x or "\0" in x for x in argv):
        raise ValueError("spec.argv must be a nonempty list of argument strings")
    timeout = spec.get("timeout_seconds", 300)
    if isinstance(timeout, bool) or not isinstance(timeout, (int, float)) or not math.isfinite(timeout) or not 0 < timeout <= 86400:
        raise ValueError("timeout_seconds must be finite and in (0, 86400]")
    if not isinstance(spec.get("cwd", "{source}"), str):
        raise ValueError("cwd must be a string")
    env = spec.get("env", {})
    if not isinstance(env, dict) or any(not isinstance(k, str) or not k or "=" in k or "\0" in k
                                      or not isinstance(v, str) or "\0" in v for k, v in env.items()):
        raise ValueError("env must contain valid string keys and values")
    for key in ("artifacts", "scenario_results"):
        values = spec.get(key, [])
        if not isinstance(values, list) or any(not isinstance(x, str) or not x or Path(x).is_absolute() or ".." in Path(x).parts for x in values):
            raise ValueError(f"{key} must contain relative paths inside the run")
    return float(timeout)


def submit(args: argparse.Namespace) -> int:
    from .queue import JobQueue
    from .snapshot import capture_snapshot

    spec = read_json(args.spec)
    validate_spec(spec)
    root = args.root.resolve()
    run = root / "runs" / uuid.uuid4().hex
    run.mkdir(parents=True)
    source = run / "source"
    try:
        source_manifest = capture_snapshot(args.repo.resolve(), source)
        manifest = {
            "schema_version": 1, "run_id": run.name,
            "created_at": time.time(), "scenario": spec.get("scenario", "command"),
            "loader": spec.get("loader", "none"), "role": spec.get("role", "candidate"),
            "source": source_manifest, "spec": spec,
            "host": {"platform": platform.platform(), "python": sys.version},
        }
        write_json(run / "manifest.json", manifest)
        queued = dict(spec, run_dir=str(run), source_dir=str(source))
        job_id = JobQueue(root / "queue.sqlite3").submit(queued)
        manifest["job_id"] = job_id
        write_json(run / "manifest.json", manifest)
    except Exception as exc:
        write_json(run / "result.json", {"status": "infra_error", "error": str(exc)})
        raise
    print(json.dumps({"job_id": job_id, "run_dir": str(run)}))
    return 0


def worker(args: argparse.Namespace) -> int:
    from .queue import JobQueue
    from .snapshot import verify_snapshot
    from .processes import supervise
    from .evidence import write_report

    root = args.root.resolve()
    queue = JobQueue(root / "queue.sqlite3")
    worker_id = f"{platform.node()}-{os.getpid()}"
    capacities = {"memory_mb": args.memory_mb, "cpu": args.cpu, "gpu": args.gpu}
    job = queue.claim(worker_id, capacities, free_memory_mb=available_memory_mb(args.reserve_mb), lease_seconds=60)
    if job is None:
        print(json.dumps({"status": "idle", "reason": "No queued job fits current resource budget"}))
        return 0
    job_id, token, spec = job["id"], job["lease_token"], job["spec"]
    result: dict = {"status": "infra_error", "cleanup_confirmed": True}
    run = None
    stop_heartbeat = threading.Event()
    heartbeat_errors = []

    def keep_lease() -> None:
        while not stop_heartbeat.wait(5):
            try:
                queue.heartbeat(job_id, worker_id, token, lease_seconds=60)
            except Exception as exc:
                heartbeat_errors.append(str(exc))
                return

    heartbeat = threading.Thread(target=keep_lease, daemon=True)
    heartbeat.start()

    def canceled() -> bool:
        if heartbeat_errors:
            return True
        state = queue.status(job_id)
        return bool(state.get("cancel_requested"))

    try:
        run = contained(root / "runs", Path(spec["run_dir"]))
        timeout = validate_spec(spec)
        source = contained(run, Path(spec["source_dir"]))
        manifest = read_json(run / "manifest.json")
        deviations = verify_snapshot(source, manifest["source"])
        if deviations:
            raise ValueError(f"Snapshot changed before run: {deviations[:5]}")
        argv = [expand(x, source, run) for x in spec["argv"]]
        cwd = contained(run, Path(expand(spec.get("cwd", "{source}"), source, run)))
        if not cwd.is_dir():
            raise ValueError("Job cwd does not exist")
        env = {key: expand(value, source, run) for key, value in spec.get("env", {}).items()}
        result["cleanup_confirmed"] = False
        result = supervise(argv, cwd, run / "logs", timeout, env=env, cancelcallback=canceled)
        result["status"] = {"passed": "pass", "failed": "fail", "cancelled": "canceled",
                            "infrastructure_failure": "infra_error"}.get(result.get("status"), result.get("status"))
        scenario_results = []
        for relative in spec.get("scenario_results", []):
            path = contained(run, run / relative)
            detail = read_json(path)
            scenario_results.append({"path": relative, "result": detail})
            if result["status"] == "pass" and detail.get("status") != "pass":
                result["status"] = "fail" if detail.get("status") == "fail" else "infra_error"
        result["scenario_results"] = scenario_results
        deviations = verify_snapshot(source, manifest["source"])
        if deviations:
            result.update(status="infra_error", source_deviations=deviations)
        result["source_verified"] = not deviations
        artifacts = {}
        for pattern in spec.get("artifacts", []):
            for path in source.glob(pattern):
                if path.is_file() and path.resolve().is_relative_to(source):
                    artifacts[path.relative_to(source).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
        manifest["artifacts"] = artifacts
        write_json(run / "manifest.json", manifest)
        result["job_id"] = job_id
    except Exception as exc:
        result.update(status="infra_error", error=f"{type(exc).__name__}: {exc}")
    finally:
        stop_heartbeat.set()
        heartbeat.join(timeout=35)
        if heartbeat_errors:
            result.update(status="infra_error", lease_errors=heartbeat_errors)
        status = result.get("status", "infra_error")
        if status not in {"pass", "fail", "infra_error", "timeout", "canceled"}:
            status = "infra_error"
            result["status"] = status
        if run is not None:
            write_json(run / "result.json", result)
        cleanup = result.get("cleanup_confirmed") is True
        if cleanup:
            completed = queue.finish(job_id, worker_id, token, status, result=result, cleanup_confirmed=True)
            result["status"] = completed["status"]
            if run is not None:
                write_json(run / "result.json", result)
        else:
            result["resource_release"] = "blocked: owned process cleanup unconfirmed"
            if run is not None:
                write_json(run / "result.json", result)
        write_report(root / "runs")
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result["status"] == "pass" else 1


def available_memory_mb(reserve_mb: int) -> int:
    if os.name == "nt":
        import ctypes
        class Memory(ctypes.Structure):
            _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong)] + [
                (name, ctypes.c_ulonglong) for name in
                ("total_phys", "avail_phys", "total_page", "avail_page", "total_virtual", "avail_virtual", "avail_extended")]
        state = Memory()
        state.length = ctypes.sizeof(state)
        if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(state)):
            raise OSError("Cannot read available physical memory")
        return max(0, state.avail_phys // 1048576 - reserve_mb)
    try:
        values = dict(line.split(":", 1) for line in Path("/proc/meminfo").read_text().splitlines())
        return max(0, int(values["MemAvailable"].split()[0]) // 1024 - reserve_mb)
    except (OSError, KeyError):
        raise OSError("Memory admission requires a supported platform probe")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3].parent / "Morph-lab-runs")
    commands = parser.add_subparsers(dest="command", required=True)
    request = commands.add_parser("submit")
    request.add_argument("--repo", type=Path, default=Path.cwd())
    request.add_argument("--spec", type=Path, required=True)
    run = commands.add_parser("worker", help="Claim and execute at most one immutable job")
    run.add_argument("--memory-mb", type=int, default=6144)
    run.add_argument("--reserve-mb", type=int, default=2048)
    run.add_argument("--cpu", type=int, default=2)
    run.add_argument("--gpu", type=int, default=1)
    commands.add_parser("status")
    cancel = commands.add_parser("cancel")
    cancel.add_argument("job_id")
    commands.add_parser("report")
    commands.add_parser("doctor")
    args = parser.parse_args(argv)
    try:
        if args.command == "submit":
            return submit(args)
        if args.command == "worker":
            return worker(args)
        if args.command == "report":
            from .evidence import write_report
            print(write_report(args.root.resolve() / "runs"))
        elif args.command == "doctor":
            print(json.dumps({"python": sys.executable, "java": shutil.which("java"),
                              "ffmpeg": shutil.which("ffmpeg"), "available_memory_mb": available_memory_mb(0)}, indent=2))
        else:
            from .queue import JobQueue
            queue = JobQueue(args.root / "queue.sqlite3")
            print(json.dumps(queue.cancel(args.job_id) if args.command == "cancel" else queue.list(), indent=2))
        return 0
    except (OSError, ValueError, RuntimeError) as exc:
        print(f"Morph Lab: {exc}", file=sys.stderr)
        return 2
