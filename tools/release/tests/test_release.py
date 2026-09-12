import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("release", Path(__file__).resolve().parents[1] / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
VERSION = "11.0.0-alpha.8.dev.1.abcdef0"
SHA = "abcdef0" + "1" * 33


def jar(root, loader, version=VERSION, harness=False):
    path = root / f"Morph-{loader}-26.2-{version}.jar"
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("me/ichun/mods/morph/Morph.class", b"fixture")
        if harness:
            archive.writestr("me/ichun/mods/morph/lab/Probe.class", b"fixture")
        if loader == "Fabric":
            archive.writestr("fabric.mod.json", json.dumps({"id":"morph", "version":version, "depends":{"minecraft":"26.2"}}))
        else:
            archive.writestr("META-INF/neoforge.mods.toml", f'[[mods]]\nmodId="morph"\nversion="{version}"\n')
    return path


class ReleaseTests(unittest.TestCase):
    def test_unique_versions_and_stable_retry(self):
        self.assertEqual(release.build_version("11.0.0-alpha.8", "1", SHA), VERSION)
        self.assertEqual(release.build_version("11.0.0", "2", SHA), "11.0.0-dev.2.abcdef0")
        self.assertNotEqual(release.build_version("11.0.0", "2", SHA), release.build_version("11.0.0", "3", SHA))
        for base, run, sha in [("bad\nversion", "1", SHA), ("11.0.0", "../1", SHA), ("11.0.0", "1", "main")]:
            with self.assertRaises(ValueError): release.build_version(base, run, sha)

    def test_both_loaders_exact_bytes_and_no_sources(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); inputs = root / "in"; inputs.mkdir()
            originals = [jar(inputs, loader) for loader in ("NeoForge", "Fabric")]
            (inputs / f"Morph-Fabric-26.2-{VERSION}-sources.jar").write_bytes(b"not installable")
            out = root / "out"
            release.bundle(inputs, out, VERSION, SHA, "JulesB40/Morph", "123")
            self.assertEqual(len(list(out.iterdir())), 4)
            for original in originals: self.assertEqual(original.read_bytes(), (out / original.name).read_bytes())
            manifest = json.loads((out / "manifest.json").read_text())
            self.assertEqual(manifest["commit"], SHA)
            for line in (out / "SHA256SUMS.txt").read_text().splitlines():
                digest, name = line.split("  ")
                self.assertEqual(digest, hashlib.sha256((out / name).read_bytes()).hexdigest())

    def test_missing_loader_and_duplicate_binaries_fail(self):
        for duplicate in (False, True):
            with tempfile.TemporaryDirectory() as temp:
                root = Path(temp); inputs = root / "in"; inputs.mkdir()
                jar(inputs, "NeoForge")
                if duplicate:
                    jar(inputs, "NeoForge", "11.0.0-old")
                    jar(inputs, "Fabric")
                with self.assertRaises(ValueError): release.bundle(inputs, root / "out", VERSION, SHA, "JulesB40/Morph", "123")

    def test_wrong_metadata_and_harness_leak_fail(self):
        with tempfile.TemporaryDirectory() as temp:
            for loader in ("NeoForge", "Fabric"):
                with self.assertRaises(ValueError): release.validate_jar(jar(Path(temp), loader, "11.0.0-old"), loader, VERSION)
                with self.assertRaises(ValueError): release.validate_jar(jar(Path(temp), loader, harness=True), loader, VERSION)

    def test_publish_new_partial_and_completed_release(self):
        for initial in ("new", "partial", "published"):
            with self.subTest(initial=initial), tempfile.TemporaryDirectory() as temp:
                root, out = self.release_fixture(temp)
                state, calls, fake_gh = self.fake_github(out, initial)
                with patch.object(release, "ROOT", root), patch.object(release, "gh", fake_gh):
                    release.publish(out, VERSION, SHA, "JulesB40/Morph")
                self.assertFalse(state["draft"])
                self.assertEqual(len(state["assets"]), 4)
                if initial == "published":
                    self.assertFalse(any(call[1] in ("create", "upload", "edit") for call in calls))
                if initial == "partial":
                    self.assertEqual(sum(call[:2] == ("release", "upload") for call in calls), 3)

    def test_publish_refuses_conflicting_or_incomplete_release(self):
        for conflict in ("source", "digest", "missing", "extra"):
            with self.subTest(conflict=conflict), tempfile.TemporaryDirectory() as temp:
                root, out = self.release_fixture(temp)
                state, calls, fake_gh = self.fake_github(out, "published")
                if conflict == "source": state["target_commitish"] = "0" * 40
                if conflict == "digest": state["assets"][0]["digest"] = "sha256:wrong"
                if conflict == "missing": state["assets"].pop()
                if conflict == "extra": state["assets"].append({"name": "unexpected.jar"})
                with patch.object(release, "ROOT", root), patch.object(release, "gh", fake_gh):
                    with self.assertRaises(ValueError): release.publish(out, VERSION, SHA, "JulesB40/Morph")
                self.assertFalse(any(call[1] in ("create", "upload", "edit") for call in calls))

    def release_fixture(self, temp):
        root = Path(temp); (root / "build").mkdir(); inputs = root / "in"; inputs.mkdir()
        for loader in ("NeoForge", "Fabric"): jar(inputs, loader)
        out = root / "build/release"
        release.bundle(inputs, out, VERSION, SHA, "JulesB40/Morph", "123")
        return root, out

    def fake_github(self, out, initial):
        assets = [{"name": p.name, "digest": f"sha256:{hashlib.sha256(p.read_bytes()).hexdigest()}"}
                  for p in sorted(out.iterdir())]
        state = {"target_commitish": SHA, "draft": initial != "published",
                 "assets": assets[:1] if initial == "partial" else list(assets)}
        exists = initial != "new"
        calls = []

        def fake_gh(*args, check=True):
            nonlocal exists
            calls.append(args)
            if args[:2] == ("release", "view"):
                return SimpleNamespace(returncode=0 if exists else 1, stdout='"https://example.test/release"')
            if args[:2] == ("release", "create"):
                exists = True
            elif args[:2] == ("release", "upload"):
                state["assets"].append(next(asset for asset in assets if asset["name"] == Path(args[3]).name))
            elif args[:2] == ("release", "edit"):
                self.assertEqual({asset["name"] for asset in state["assets"]}, {p.name for p in out.iterdir()})
                state["draft"] = False
            elif args == ("api", f"repos/JulesB40/Morph/releases/tags/26.2-{VERSION}"):
                return SimpleNamespace(returncode=0, stdout=json.dumps(state))
            else:
                self.fail(f"Unexpected GitHub command: {args}")
            return SimpleNamespace(returncode=0, stdout="")

        return state, calls, fake_gh


if __name__ == "__main__": unittest.main()
