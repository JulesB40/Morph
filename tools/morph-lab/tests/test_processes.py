"""Harmless real Python processes exercise ownership, failure and isolation."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from morph_lab import processes


class ProcessTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.counter = 0

    def tearDown(self):
        self.temporary.cleanup()

    def run_child(self, source, timeout=5, **kwargs):
        self.counter += 1
        return processes.supervise([sys.executable, "-c", source], self.root,
                                   self.root / f"logs-{self.counter}", timeout, **kwargs)

    def wait_file(self, path, seconds=5):
        deadline = time.monotonic() + seconds
        while not path.exists():
            if time.monotonic() >= deadline:
                self.fail(f"child did not create {path}")
            time.sleep(0.02)

    def assert_stopped(self, pid):
        if os.name == "nt":
            handle = processes.kernel.OpenProcess(0x100000, False, pid)
            if handle:
                try:
                    self.assertEqual(processes.kernel.WaitForSingleObject(handle, 5000), 0)
                finally:
                    processes.kernel.CloseHandle(handle)
        else:
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                try:
                    state = Path(f"/proc/{pid}/stat").read_text().split()[2]
                except FileNotFoundError:
                    return
                if state == "Z":
                    return
                time.sleep(0.02)
            self.fail(f"owned process {pid} remains active")

    def test_argv_cwd_logs_and_environment(self):
        with mock.patch.dict(os.environ, {"MORPH_SECRET_TEST": "must-not-leak", "JAVA_TOOL_OPTIONS": "injection"}):
            outcome = self.run_child(
                "import os,json,sys; print(json.dumps([os.getcwd(),os.getenv('MORPH_SECRET_TEST'),"
                "os.getenv('JAVA_TOOL_OPTIONS'),os.getenv('MORPH_EXPLICIT')])); print('stderr',file=sys.stderr)",
                env={"MORPH_EXPLICIT": "literal & | > value"})
        self.assertEqual(outcome["status"], "passed")
        self.assertTrue(outcome["cleanup_confirmed"])
        self.assertEqual(json.loads(Path(outcome["stdout_log"]).read_text()),
                         [str(self.root), None, None, "literal & | > value"])
        self.assertEqual(Path(outcome["stderr_log"]).read_text().strip(), "stderr")
        self.assertTrue(outcome["pid"])
        self.assertEqual(Path(outcome["stdout_log"]).parent.joinpath("process.json").read_text(),
                         json.dumps(outcome, indent=2) + "\n")

    def test_nonzero_crash_and_following_success(self):
        failed = self.run_child("import os; os._exit(23)")
        self.assertEqual((failed["status"], failed["returncode"]), ("failed", 23))
        self.assertTrue(failed["cleanup_confirmed"])
        self.assertEqual(self.run_child("pass")["status"], "passed")

    def test_arguments_are_literal_and_existing_logs_are_preserved(self):
        arguments = ['space value', 'quote"inside', 'tail\\', '& echo injected > stolen']
        outcome = processes.supervise(
            [sys.executable, "-c", "import json,sys; print(json.dumps(sys.argv[1:]))", *arguments],
            self.root, self.root / "literal", 5)
        self.assertEqual(outcome["status"], "passed")
        self.assertEqual(json.loads(Path(outcome["stdout_log"]).read_text()), arguments)
        with self.assertRaises(FileExistsError):
            processes.supervise([sys.executable, "-c", "pass"], self.root, self.root / "literal", 5)

    def test_timeout(self):
        result = self.run_child("import time; time.sleep(60)", timeout=0.25)
        self.assertEqual(result["status"], "timeout")
        self.assertTrue(result["timed_out"])
        self.assertTrue(result["cleanup_confirmed"])
        self.assert_stopped(result["pid"])

    def test_cancellation_and_callback_failure(self):
        result = self.run_child("import time; time.sleep(60)", cancelcallback=lambda: True)
        self.assertEqual(result["status"], "cancelled")
        self.assertTrue(result["cleanup_confirmed"])
        def broken_callback():
            raise RuntimeError("controller error")
        result = self.run_child("import time; time.sleep(60)", cancelcallback=broken_callback)
        self.assertEqual(result["status"], "infrastructure_failure")
        self.assertIn("controller error", result["error"])
        self.assertTrue(result["cleanup_confirmed"])
        self.assert_stopped(result["pid"])

    def test_descendant_timeout_and_unrelated_sentinel(self):
        flags = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
        sentinel = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"], **flags)
        try:
            result = self.run_child(
                "import subprocess,sys,time,pathlib; "
                "p=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)']); "
                "pathlib.Path('descendant.pid').write_text(str(p.pid)); time.sleep(60)", timeout=1)
            self.assertEqual(result["status"], "timeout")
            self.assertTrue(result["cleanup_confirmed"])
            self.assert_stopped(int((self.root / "descendant.pid").read_text()))
            self.assertIsNone(sentinel.poll(), "cleanup touched an unrelated process")
        finally:
            sentinel.terminate()
            sentinel.wait(timeout=5)

    def test_successful_root_cannot_leave_descendants(self):
        result = self.run_child(
            "import subprocess,sys,pathlib; "
            "p=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)']); "
            "pathlib.Path('descendant.pid').write_text(str(p.pid))")
        self.assertEqual(result["status"], "passed")
        self.assertTrue(result["cleanup_confirmed"])
        self.assert_stopped(int((self.root / "descendant.pid").read_text()))

    def test_manifest_exists_before_child_code(self):
        result = self.run_child(
            "import json,pathlib; p=json.loads(pathlib.Path('logs-1/process.json').read_text()); "
            "assert p['pid']; assert p['cleanup_confirmed'] is False")
        self.assertEqual(result["status"], "passed")

    def test_launch_error_and_input_validation(self):
        result = processes.supervise([str(self.root / "absent.exe")], self.root,
                                     self.root / "missing", 1)
        self.assertEqual(result["status"], "infrastructure_failure")
        self.assertTrue(result["cleanup_confirmed"])
        for argv, timeout in [("echo nope", 1), ([], 1), ([sys.executable], float("nan"))]:
            with self.assertRaises(ValueError):
                processes.supervise(argv, self.root, self.root / "unused", timeout)

    def test_memory_probe(self):
        memory = processes.probe_memory(os.getpid())
        self.assertGreater(memory["working_set_bytes"], 0)
        self.assertGreaterEqual(memory["peak_working_set_bytes"], memory["working_set_bytes"])

    @unittest.skipUnless(os.name == "nt", "Windows suspended startup contract")
    def test_resume_failure_never_executes_child(self):
        with mock.patch.object(processes._WindowsProcess, "start", side_effect=OSError("resume rejected")):
            result = self.run_child("import pathlib; pathlib.Path('executed').touch()")
        self.assertEqual(result["status"], "infrastructure_failure")
        self.assertTrue(result["cleanup_confirmed"])
        self.assertFalse((self.root / "executed").exists())
        self.assert_stopped(result["pid"])

    @unittest.skipUnless(os.name == "nt", "Windows cleanup failure contract")
    def test_cleanup_failure_withholds_resource_release(self):
        cleanup = processes._WindowsProcess.cleanup
        def rejected(process):
            cleanup(process)  # Leave no test descendants behind.
            raise OSError("injected accounting failure")
        with mock.patch.object(processes._WindowsProcess, "cleanup", rejected):
            result = self.run_child("pass")
        self.assertEqual(result["status"], "infrastructure_failure")
        self.assertFalse(result["cleanup_confirmed"])
        self.assertIn("accounting failure", result["cleanup_error"])

    @unittest.skipUnless(os.name == "nt", "Windows Job Object recovery contract")
    def test_supervisor_crash_kills_owned_tree(self):
        script = self.root / "controller.py"
        package = str(Path(processes.__file__).resolve().parents[1])
        source = ("import subprocess,sys,time,pathlib; "
                  "p=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)']); "
                  "pathlib.Path('descendant.pid').write_text(str(p.pid)); time.sleep(60)")
        script.write_text(
            f"import sys\nsys.path.insert(0, {package!r})\n"
            f"from morph_lab.processes import supervise\n"
            f"supervise([{sys.executable!r}, '-c', {source!r}], {str(self.root)!r}, "
            f"{str(self.root / 'crash-logs')!r}, 60)\n")
        controller = subprocess.Popen([sys.executable, str(script)], creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            self.wait_file(self.root / "descendant.pid")
            manifest = json.loads((self.root / "crash-logs/process.json").read_text())
            descendant = int((self.root / "descendant.pid").read_text())
            controller.kill()
            controller.wait(timeout=5)
            self.assert_stopped(manifest["pid"])
            self.assert_stopped(descendant)
        finally:
            if controller.poll() is None:
                controller.kill()
                controller.wait(timeout=5)


if __name__ == "__main__":
    unittest.main()
