"""Version, validate and publish the two JARs from one successful Actions run."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def build_version(base: str, run: str, sha: str) -> str:
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", base):
        raise ValueError("mod_version must be a semantic version without build metadata")
    if not re.fullmatch(r"[1-9][0-9]*", run) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Expected Actions run number and full source SHA")
    return f"{base}{'.' if '-' in base else '-'}dev.{run}.{sha[:7]}"


def context() -> tuple[str, str, str]:
    props = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
                 if "=" in line and not line.lstrip().startswith("#"))
    sha = os.environ["GITHUB_SHA"]
    version = build_version(props["mod_version"].strip(), os.environ["GITHUB_RUN_NUMBER"], sha)
    repo = os.environ["GITHUB_REPOSITORY"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise ValueError("Invalid repository")
    return version, sha, repo


def validate_jar(path: Path, loader: str, version: str) -> None:
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if jar.testzip() is not None:
            raise ValueError("Corrupt JAR")
        if any(name.startswith("me/ichun/mods/morph/lab/") or "morph_lab" in name for name in names):
            raise ValueError("Test harness leaked into release JAR")
        if not any(name.endswith(".class") for name in names):
            raise ValueError("Installable JAR has no compiled classes")
        if loader == "Fabric":
            metadata = json.loads(jar.read("fabric.mod.json"))
            actual = metadata["version"]
            if metadata["id"] != "morph" or metadata["depends"]["minecraft"] != "26.2":
                raise ValueError("Unexpected Fabric mod or Minecraft version")
        else:
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
            mods = [mod for mod in metadata["mods"] if mod["modId"] == "morph"]
            if len(mods) != 1:
                raise ValueError("Expected one Morph mod")
            actual = mods[0]["version"]
        if actual != version:
            raise ValueError(f"{loader} metadata version {actual!r} differs from {version!r}")


def bundle(inputs: Path, output: Path, version: str, sha: str, repo: str, run_id: str) -> None:
    output.mkdir(parents=True, exist_ok=False)
    assets = []
    for loader in ("NeoForge", "Fabric"):
        candidates = [p for p in inputs.glob(f"Morph-{loader}-26.2-*.jar") if not p.name.endswith("-sources.jar")]
        if len(candidates) != 1:
            raise ValueError(f"Expected exactly one installable {loader} JAR, found {len(candidates)}")
        source = candidates[0]
        validate_jar(source, loader, version)
        target = output / f"Morph-{loader}-26.2-{version}.jar"
        shutil.copyfile(source, target)
        assets.append({"name": target.name, "loader": loader, "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    manifest = {"version": version, "minecraft": "26.2", "commit": sha,
                "run": f"https://github.com/{repo}/actions/runs/{run_id}", "assets": assets}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    files = sorted(output.iterdir())
    (output / "SHA256SUMS.txt").write_text("".join(
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n" for path in files))


def gh(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(["gh", *args], text=True, capture_output=True)
    if check and result.returncode:
        print(result.stderr, file=sys.stderr)
        result.check_returncode()
    return result


def publish(output: Path, version: str, sha: str, repo: str) -> None:
    tag = f"26.2-{version}"
    files = sorted(output.iterdir())
    if {p.name for p in files} != {"manifest.json", "SHA256SUMS.txt",
            f"Morph-NeoForge-26.2-{version}.jar", f"Morph-Fabric-26.2-{version}.jar"}:
        raise ValueError("Release must contain exactly two installable JARs, manifest and checksums")
    notes = ROOT / "build/release-notes.md"
    notes.write_text(f"Development build for Minecraft Java 26.2. Requires Java 25.\n\n"
        f"Download the JAR for your loader; Fabric also needs Fabric API. Install matching versions on client and server.\n\n"
        f"Both loader CI jobs passed for commit `{sha}`. This is an unfinished prerelease; all-mob visual and full multiplayer validation remain incomplete.\n\n"
        f"[Current scope and known limitations](https://github.com/{repo}/blob/{sha}/docs/IMPLEMENTATION_TODO.md). "
        f"The attached manifest records the source commit, workflow run and JAR hashes.\n")
    view = gh("release", "view", tag, "--repo", repo, "--json", "targetCommitish,isDraft,assets", check=False)
    if view.returncode:
        gh("release", "create", tag, *(str(p) for p in files), "--repo", repo, "--target", sha,
           "--title", f"Morph 26.2 — {version}", "--prerelease", "--draft", "--notes-file", str(notes))
    # Resume partial drafts without replacing any already-uploaded bytes.
    endpoint = f"repos/{repo}/releases/tags/{tag}"
    current = json.loads(gh("api", endpoint).stdout)
    if current["target_commitish"] != sha:
        raise ValueError("Existing release targets another source commit")
    remote = {asset["name"]: asset for asset in current["assets"]}
    if set(remote) - {p.name for p in files}:
        raise ValueError("Existing release contains unexpected assets")
    for path in files:
        if path.name not in remote:
            if not current["draft"]:
                raise ValueError("Published release is missing an asset")
            gh("release", "upload", tag, str(path), "--repo", repo)
    remote = {asset["name"]: asset for asset in json.loads(gh("api", endpoint).stdout)["assets"]}
    for path in files:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if remote.get(path.name, {}).get("digest") != f"sha256:{digest}":
            raise ValueError("Release asset differs from this tested build")
    if current["draft"]:
        gh("release", "edit", tag, "--repo", repo, "--draft=false")
    print(gh("release", "view", tag, "--repo", repo, "--json", "url", "--jq", ".url").stdout.strip())


if __name__ == "__main__":
    version, sha, repo = context()
    action = sys.argv[1]
    if action == "version":
        with open(os.environ["GITHUB_ENV"], "a") as env:
            env.write(f"MORPH_VERSION={version}\n")
        print(version)
    elif action == "bundle":
        bundle(ROOT / "build/release-input", ROOT / "build/release", version, sha, repo, os.environ["GITHUB_RUN_ID"])
    elif action == "publish":
        if repo != "JulesB40/Morph" or os.environ["GITHUB_REF"] != "refs/heads/codex/port-26.2" or os.environ["GITHUB_EVENT_NAME"] not in ("push", "workflow_dispatch"):
            raise ValueError("Publication is restricted to trusted port-branch runs on the fork")
        publish(ROOT / "build/release", version, sha, repo)
    else:
        raise ValueError("Unknown release action")
