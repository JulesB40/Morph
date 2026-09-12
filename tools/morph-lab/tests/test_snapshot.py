import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from morph_lab import snapshot


class SnapshotTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.git("init", "-q")
        self.git("config", "user.email", "test@example.invalid")
        self.git("config", "user.name", "Snapshot test")
        self.write("tracked.txt", "committed")
        self.git("add", ".")
        self.git("commit", "-qm", "baseline")
        self.dest = self.root / "snapshot"

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.repo), *args], check=True,
                              capture_output=True).stdout

    def write(self, name, text):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def test_dirty_and_untracked_exact_bytes_with_exclusions(self):
        self.write("tracked.txt", "dirty")
        self.write("new/é.txt", "new source")
        self.write(".gitignore", "ignored.txt\n")
        self.write("ignored.txt", "ignored")
        self.write("module/build/generated.txt", "generated")
        self.git("add", "module/build/generated.txt")
        manifest = snapshot.capture_snapshot(self.repo, self.dest)
        self.assertEqual(set(manifest["files"]), {"tracked.txt", "new/é.txt", ".gitignore"})
        self.assertEqual(manifest["commit"], self.git("rev-parse", "HEAD").decode().strip())
        self.assertTrue(manifest["dirty"])
        self.assertEqual(manifest["files"]["tracked.txt"]["sha256"], hashlib.sha256(b"dirty").hexdigest())
        self.assertEqual(snapshot.verify_snapshot(self.dest, manifest), [])
        status = snapshot.source_status(self.dest, manifest)
        self.assertEqual(status["status"], "unchanged")
        self.assertEqual(status["expected_sha256"], status["actual_sha256"])
        self.assertFalse((self.dest / ".git").exists())

    def test_deletion_is_frozen(self):
        (self.repo / "tracked.txt").unlink()
        manifest = snapshot.capture_snapshot(self.repo, self.dest)
        self.assertEqual(manifest["files"], {})
        self.assertTrue(manifest["dirty"])

    def test_generated_outputs_do_not_change_source_fingerprint(self):
        manifest = snapshot.capture_snapshot(self.repo, self.dest)
        output = self.dest / "module" / "build" / "test.jar"
        output.parent.mkdir(parents=True)
        output.write_bytes(b"actual artifact")
        self.assertEqual(snapshot.verify_snapshot(self.dest, manifest), [])
        self.assertEqual(snapshot.source_status(self.dest, manifest)["actual_sha256"],
                         manifest["source_sha256"])
        self.assertEqual(snapshot.artifact_provenance(output)["sha256"],
                         hashlib.sha256(b"actual artifact").hexdigest())

    def test_verification_detects_changes_deletion_and_additions(self):
        self.write("other.txt", "other")
        manifest = snapshot.capture_snapshot(self.repo, self.dest)
        (self.dest / "tracked.txt").write_text("modified")
        (self.dest / "other.txt").unlink()
        (self.dest / "extra.txt").write_text("extra")
        self.assertEqual(snapshot.verify_snapshot(self.dest, manifest),
                         ["missing or unsafe: other.txt", "changed: tracked.txt", "unexpected: extra.txt"])
        self.assertEqual(snapshot.source_status(self.dest, manifest)["status"], "source_changed")

    def test_mutation_during_copy_aborts_and_cleans_staging(self):
        original = snapshot.shutil.copy2
        def mutate(source, target):
            result = original(source, target)
            self.write("tracked.txt", "changed during copy")
            return result
        with patch.object(snapshot.shutil, "copy2", side_effect=mutate):
            with self.assertRaises(snapshot.SourceChangedError):
                snapshot.capture_snapshot(self.repo, self.dest)
        self.assertFalse(self.dest.exists())
        self.assertEqual(list(self.root.glob(".morph-snapshot-*")), [])

    def test_new_source_during_copy_aborts(self):
        original = snapshot.shutil.copy2
        def mutate(source, target):
            result = original(source, target)
            self.write("arrived.txt", "new source")
            return result
        with patch.object(snapshot.shutil, "copy2", side_effect=mutate):
            with self.assertRaises(snapshot.SourceChangedError):
                snapshot.capture_snapshot(self.repo, self.dest)

    def test_nested_and_existing_destinations_rejected(self):
        with self.assertRaises(snapshot.SnapshotError):
            snapshot.capture_snapshot(self.repo, self.repo / "build" / "snapshot")
        self.dest.mkdir()
        with self.assertRaises(snapshot.SnapshotError):
            snapshot.capture_snapshot(self.repo, self.dest)

    def test_traversal_manifest_is_rejected_without_reading_external_file(self):
        self.dest.mkdir()
        for name in ("../secret", "/secret", "C:/secret", "a\\secret", "a/../secret", "a//b"):
            with self.subTest(name=name):
                manifest = {"files": {name: {}}, "source_sha256": "invalid"}
                self.assertIn("missing or unsafe: " + name, snapshot.verify_snapshot(self.dest, manifest))

    def test_symlink_source_and_snapshot_rejected(self):
        link = self.repo / "linked.txt"
        try:
            link.symlink_to(self.repo / "tracked.txt")
        except OSError as exc:
            self.skipTest("Symlink creation unavailable: " + str(exc))
        with self.assertRaises(snapshot.SnapshotError):
            snapshot.capture_snapshot(self.repo, self.dest)
        link.unlink()
        manifest = snapshot.capture_snapshot(self.repo, self.dest)
        (self.dest / "tracked.txt").unlink()
        (self.dest / "tracked.txt").symlink_to(self.repo / "tracked.txt")
        self.assertIn("missing or unsafe: tracked.txt", snapshot.verify_snapshot(self.dest, manifest))

    def test_artifact_and_executable_hash_the_actual_file(self):
        artifact = self.repo / "tracked.txt"
        entry = snapshot.artifact_provenance(artifact)
        self.assertEqual(entry["sha256"], hashlib.sha256(b"committed").hexdigest())
        self.assertEqual(entry["name"], "tracked.txt")
        self.assertEqual(snapshot.executable_provenance(artifact, version="26.0.1")["version"], "26.0.1")
        with self.assertRaises(snapshot.SnapshotError):
            snapshot.executable_provenance(artifact, version="version\nenvironment")


if __name__ == "__main__":
    unittest.main()
