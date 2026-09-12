"""Content-addressed source copies. Immutability is checked, not an OS flag.

Snapshots must be outside the source checkout. Git supplies tracked and
nonignored untracked names; generated directories are excluded even if tracked.
No environment variables, remote URLs, or command output are collected.
"""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import tempfile


EXCLUDED_DIRS = frozenset({".git", ".gradle", "build", "out", "__pycache__", ".pytest_cache"})


class SnapshotError(ValueError):
    """Invalid or unsafe snapshot input."""


class SourceChangedError(SnapshotError):
    """Source changed while the snapshot was being copied."""


def _git(repo: Path, *args: str) -> bytes:
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True, check=False)
    if result.returncode:
        raise SnapshotError("Git command failed: " + " ".join(args))
    return result.stdout


def _relative(name: str) -> PurePosixPath:
    path = PurePosixPath(name)
    if (not name or path.is_absolute() or "\\" in name or ":" in name
            or any(part in {"", ".", ".."} for part in name.split("/"))):
        raise SnapshotError("Unsafe relative source path")
    return path


def _safe_file(root: Path, name: str) -> Path:
    relative = _relative(name)
    current = root
    for part in relative.parts:
        current = current / part
        if current.is_symlink() or (hasattr(current, "is_junction") and current.is_junction()):
            raise SnapshotError("Symlinks and junctions are not source files: " + name)
    if not current.resolve().is_relative_to(root.resolve()):
        raise SnapshotError("Source path escapes its root")
    if not current.is_file():
        raise SnapshotError("Not a regular source file: " + name)
    return current


def sha256_file(path: str | Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _entry(path: Path) -> dict:
    info = path.stat()
    return {"sha256": sha256_file(path), "size": info.st_size,
            "mode": stat.S_IMODE(info.st_mode)}


def _digest(files: dict) -> str:
    return hashlib.sha256(json.dumps(files, sort_keys=True, separators=(",", ":"),
                                     ensure_ascii=True).encode("ascii")).hexdigest()


def _source_manifest(repo: Path) -> dict:
    top = Path(os.fsdecode(_git(repo, "rev-parse", "--show-toplevel")).strip()).resolve()
    if top != repo:
        raise SnapshotError("Source must be the Git checkout root")
    names = _git(repo, "ls-files", "-z", "--cached", "--others", "--exclude-standard")
    files = {}
    for name in sorted({os.fsdecode(value) for value in names.split(b"\0") if value}):
        relative = _relative(name)
        if any(part in EXCLUDED_DIRS for part in relative.parts[:-1]):
            continue
        candidate = repo.joinpath(*relative.parts)
        # A tracked deletion is represented by its absence from the file map.
        if not candidate.exists() and not candidate.is_symlink():
            continue
        files[name] = _entry(_safe_file(repo, name))
    commit = _git(repo, "rev-parse", "--verify", "HEAD").decode("ascii").strip()
    dirty = bool(_git(repo, "status", "--porcelain", "-z", "--untracked-files=all"))
    return {"schema_version": 1, "commit": commit, "dirty": dirty,
            "files": files, "source_sha256": _digest(files)}


def capture_snapshot(repo: str | Path, dest: str | Path) -> dict:
    """Publish a verified copy atomically; reject existing/nested destinations.

    The returned manifest belongs in the evidence directory, outside this copy.
    Before/after hashes detect observed mutation, not transient change-and-revert.
    """
    repo, dest = Path(repo).resolve(), Path(dest).absolute()
    if dest.is_symlink() or dest.exists():
        raise SnapshotError("Snapshot destination already exists")
    dest = dest.resolve()
    if dest.is_relative_to(repo) or repo.is_relative_to(dest):
        raise SnapshotError("Snapshot destination must be outside the source checkout")
    before = _source_manifest(repo)
    dest.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".morph-snapshot-", dir=dest.parent))
    try:
        for name in before["files"]:
            source = _safe_file(repo, name)
            target = staging.joinpath(*_relative(name).parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        after = _source_manifest(repo)
        if before != after or verify_snapshot(staging, before):
            raise SourceChangedError("Source changed during snapshot capture")
        if dest.exists() or dest.is_symlink():
            raise SnapshotError("Snapshot destination appeared during capture")
        staging.rename(dest)
        return before
    except (OSError, SnapshotError):
        shutil.rmtree(staging)
        raise


def verify_snapshot(dest: str | Path, manifest: dict) -> list[str]:
    """Return deviations, including unexpected files and unsafe paths."""
    root = Path(dest).absolute()
    deviations = []
    files = manifest["files"]
    if _digest(files) != manifest.get("source_sha256"):
        deviations.append("manifest hash mismatch")
    if root.is_symlink() or not root.is_dir():
        return deviations + ["missing or unsafe snapshot root"]
    for name, expected in sorted(files.items()):
        try:
            if _entry(_safe_file(root, name)) != expected:
                deviations.append("changed: " + name)
        except (OSError, SnapshotError):
            deviations.append("missing or unsafe: " + name)
    actual_names = set()
    for directory, dirs, names in os.walk(root, followlinks=False):
        dirs[:] = [name for name in dirs if name not in EXCLUDED_DIRS]
        for name in list(dirs):
            path = Path(directory) / name
            if path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction()):
                deviations.append("unsafe directory: " + path.relative_to(root).as_posix())
                dirs.remove(name)
        actual_names.update((Path(directory) / name).relative_to(root).as_posix() for name in names)
    deviations.extend("unexpected: " + name for name in sorted(actual_names - files.keys()))
    return deviations


def source_status(dest: str | Path, manifest: dict) -> dict:
    """Machine-readable verification result, with actual content fingerprint."""
    deviations = verify_snapshot(dest, manifest)
    actual = {}
    root = Path(dest).absolute()
    if not root.is_symlink() and root.is_dir():
        for directory, dirs, names in os.walk(root, followlinks=False):
            dirs[:] = [name for name in dirs if name not in EXCLUDED_DIRS
                       and not (Path(directory) / name).is_symlink()
                       and not (hasattr(Path(directory) / name, "is_junction")
                                and (Path(directory) / name).is_junction())]
            for name in names:
                relative = (Path(directory) / name).relative_to(root).as_posix()
                try:
                    actual[relative] = _entry(_safe_file(root, relative))
                except (OSError, SnapshotError):
                    actual[relative] = {"unsafe_or_missing": True}
    return {"status": "source_changed" if deviations else "unchanged",
            "expected_sha256": manifest["source_sha256"],
            "actual_sha256": _digest(actual), "deviations": deviations}


def artifact_provenance(path: str | Path) -> dict:
    """Hash the actual artifact bytes, without recording machine-specific paths."""
    path = Path(path)
    return {"name": path.name, **_entry(path)}


def executable_provenance(path: str | Path, *, version: str) -> dict:
    """Record an explicitly supplied, reviewed version; never run arbitrary tools.

    Callers supply a version identifier, not raw command output or environment.
    """
    if not version or len(version) > 200 or any(ord(c) < 32 for c in version):
        raise SnapshotError("Expected a short, single-line executable version")
    return {**artifact_provenance(path), "version": version}
