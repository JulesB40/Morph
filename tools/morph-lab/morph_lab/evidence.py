"""Local evidence indexes and bounded, tick-labelled FFmpeg review clips."""
from __future__ import annotations

import hashlib
import html
import json
import math
from pathlib import Path
import shutil
import subprocess
import tempfile
from typing import Sequence
from urllib.parse import quote

STATUSES = ("pass", "fail", "infra_error", "infrastructure_failure", "timeout", "canceled", "skipped", "unsupported")


def _sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def _read(path: Path) -> tuple[dict, str]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(value, dict):
            raise ValueError("expected an object")
        return value, ""
    except (OSError, ValueError) as exc:
        return {}, f"{path.name}: {exc}"


def _inside(path: Path, root: Path) -> bool:
    return path.resolve().is_relative_to(root.resolve())


def write_report(root: Path | str) -> Path:
    """Index a single run or immediate run children; never follow external links.

    Manifest evidence entries are optional objects with path and role fields.
    Raw result status remains visible even when malformed/missing evidence makes
    the bundle incomplete. Hashes are computed from files at report generation.
    """
    root = Path(root).resolve()
    root.mkdir(parents=True, exist_ok=True)
    runs = [root] if (root / "manifest.json").exists() or (root / "result.json").exists() else [
        p for p in sorted(root.iterdir()) if p.is_dir() and _inside(p, root)
    ]
    esc = lambda value: html.escape(str(value), quote=True)
    sections = []
    for run in runs:
        manifest, manifest_error = _read(run / "manifest.json") if _inside(run / "manifest.json", root) else ({}, "external manifest refused")
        result, result_error = _read(run / "result.json") if _inside(run / "result.json", root) else ({}, "external result refused")
        status = str(result.get("status", "infrastructure_failure"))
        issues = [e for e in (manifest_error, result_error) if e]
        if status not in STATUSES:
            issues.append(f"Unknown result status: {status}")
        roles = {}
        entries = manifest.get("evidence", [])
        if not isinstance(entries, list):
            issues.append("manifest evidence must be a list")
            entries = []
        for entry in entries:
            if not isinstance(entry, dict) or not isinstance(entry.get("path"), str):
                issues.append("Invalid evidence entry")
                continue
            relative = Path(entry["path"])
            path = run / relative
            if relative.is_absolute() or ".." in relative.parts or not _inside(path, run):
                issues.append(f"Refused evidence path: {entry['path']}")
            elif not path.is_file():
                issues.append(f"Missing evidence: {entry['path']}")
            else:
                roles[path.resolve()] = entry.get("role", "unlabelled")
        artifacts = []
        media_count = 0
        for path in sorted(run.rglob("*")):
            if "source" in path.relative_to(run).parts or "__pycache__" in path.parts:
                continue
            if not path.is_file() or not _inside(path, run) or path.name == "index.html":
                continue
            href = quote(path.relative_to(root).as_posix(), safe="/")
            role = esc(roles.get(path.resolve(), "unlabelled"))
            label = esc(path.relative_to(run))
            digest = _sha(path)
            preview = ""
            if path.suffix.lower() == ".png":
                media_count += 1
                preview = f'<img loading="lazy" src="{href}" alt="{role}: {label}">'
            elif path.suffix.lower() in (".mp4", ".webm"):
                media_count += 1
                preview = f'<video controls preload="metadata" src="{href}"></video>'
            artifacts.append(f'<li><a href="{href}">{label}</a> — role: {role}<br><code>SHA-256 {digest}</code>{preview}</li>')
        if not media_count:
            issues.append("No captured PNG/video evidence available; visible behavior is unverified.")
        issues_html = "".join(f"<li>{esc(issue)}</li>" for issue in issues)
        search = esc(json.dumps(manifest, ensure_ascii=False).lower())
        sections.append(f'<section data-status="{esc(status)}" data-search="{search}"><h2>{esc(run.name)} — {esc(status)}</h2>'
                        f'<p>Reason: {esc(result.get("reason", "No reason recorded"))}</p>'
                        f'<ul class="issues">{issues_html}</ul><details><summary>Manifest and result</summary>'
                        f'<pre>{esc(json.dumps({"manifest": manifest, "result": result}, indent=2))}</pre></details>'
                        f'<ul class="artifacts">{"".join(artifacts)}</ul></section>')
    options = ''.join(f'<option>{s}</option>' for s in STATUSES)
    document = '<!doctype html><html lang="en"><meta charset="utf-8"><title>Morph Lab evidence</title>'
    document += '<style>body{font:16px system-ui;margin:2rem;max-width:1100px}code,pre{overflow-wrap:anywhere;white-space:pre-wrap}img,video{display:block;max-width:48rem;width:100%;margin:1rem 0}li{margin:.6rem 0}.artifacts{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:1.5rem}.artifacts li{min-width:0}.issues{color:#963600}section{border-top:1px solid #aaa;padding:1rem 0}</style>'
    document += '<h1>Morph Lab evidence</h1><p>Result statuses: ' + ', '.join(STATUSES) + '.</p><p>Media supports assertions; absent capture does not prove rendering. Tick-sampled clips do not measure real-time FPS. Hashes below describe current evidence bytes.</p>'
    document += f'<label>Status <select id="status"><option value="">all</option>{options}</select></label>'
    document += ' <label>Form, feature, loader or manifest text <input id="search" type="search"></label>'
    document += ''.join(sections) or '<p>No runs available.</p>'
    document += '<script>const statusFilter=document.getElementById("status"),searchFilter=document.getElementById("search");function filter(){document.querySelectorAll("section").forEach(s=>s.hidden=(!!statusFilter.value&&s.dataset.status!==statusFilter.value)||!s.dataset.search.includes(searchFilter.value.toLowerCase()))}statusFilter.addEventListener("change",filter);searchFilter.addEventListener("input",filter)</script></html>'
    output = root / "index.html"
    if output.is_symlink():
        raise ValueError("report output must not be a symbolic link")
    output.write_text(document, encoding="utf-8")
    return output


def encode_video(frames: Sequence[Path | str], output: Path | str, *,
                 framerate: float = 20.0, ticks: Sequence[int] | None = None,
                 dropped_frames: int = 0, ffmpeg: str = "ffmpeg", timeout: float = 60.0) -> dict:
    """Encode supplied PNGs in order. Return and persist honest capture provenance.

    Output must be new. Failed encoding never publishes a partial clip. Frames
    are copied to a private numbered sequence, avoiding FFmpeg path expressions.
    Dropped-frame count is provided by the capture adapter, not inferred from ticks.
    """
    output = Path(output).resolve()
    if output.suffix.lower() != ".mp4":
        raise ValueError("output must use .mp4")
    if not math.isfinite(framerate) or framerate <= 0 or not math.isfinite(timeout) or timeout <= 0:
        raise ValueError("framerate and timeout must be finite and positive")
    if not isinstance(dropped_frames, int) or isinstance(dropped_frames, bool) or dropped_frames < 0:
        raise ValueError("dropped_frames must be a nonnegative integer")
    frames = [Path(p).resolve() for p in frames]
    if ticks is not None and (len(ticks) != len(frames) or any(type(t) is not int for t in ticks) or any(a > b for a, b in zip(ticks, ticks[1:]))):
        raise ValueError("ticks must match frames and be nondecreasing integers")
    sidecar = output.with_suffix(output.suffix + ".json")
    if output.exists() or sidecar.exists():
        raise FileExistsError("refusing to overwrite an existing clip or metadata")
    output.parent.mkdir(parents=True, exist_ok=True)
    metadata = {"status": "infrastructure_failure", "reason": "", "classification": "tick_sampled_replay" if ticks is not None else "frame_sequence_replay",
                "real_time_fps_measurement": False, "framerate": framerate, "ticks": list(ticks) if ticks is not None else None,
                "dropped_frames": dropped_frames, "frame_count": len(frames), "frames": []}
    try:
        if not frames:
            raise ValueError("No captured frames supplied")
        with tempfile.TemporaryDirectory(prefix="morph-encode-", dir=output.parent) as directory:
            stage = Path(directory)
            for index, frame in enumerate(frames):
                with frame.open("rb") as stream:
                    if stream.read(8) != b"\x89PNG\r\n\x1a\n":
                        raise ValueError(f"Not a captured PNG: {frame}")
                copied = stage / f"{index:08d}.png"
                shutil.copyfile(frame, copied)
                metadata["frames"].append({"path": str(frame), "sha256": _sha(copied)})
            executable = shutil.which(ffmpeg)
            if executable is None:
                raise FileNotFoundError(f"FFmpeg unavailable: {ffmpeg}")
            argv = [executable, "-nostdin", "-hide_banner", "-loglevel", "error", "-framerate", str(framerate),
                    "-i", str(stage / "%08d.png"), "-frames:v", str(len(frames)), "-an", "-c:v", "libx264",
                    "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(stage / "clip.mp4")]
            metadata.update({"command": argv, "ffmpeg_sha256": _sha(Path(executable))})
            completed = subprocess.run(argv, capture_output=True, timeout=timeout, shell=False,
                                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            metadata["stderr"] = completed.stderr.decode("utf-8", errors="replace")[-16384:]
            metadata["returncode"] = completed.returncode
            clip = stage / "clip.mp4"
            if completed.returncode != 0 or not clip.is_file() or clip.stat().st_size == 0:
                raise ValueError("FFmpeg did not produce a complete clip")
            metadata["sha256"] = _sha(clip)
            clip.rename(output)
            metadata.update(status="pass", reason="Encoded supplied frame sequence")
    except subprocess.TimeoutExpired:
        metadata.update(status="timeout", reason="FFmpeg encoding exceeded timeout")
    except (OSError, ValueError) as exc:
        metadata["reason"] = str(exc)
    sidecar.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    return metadata
