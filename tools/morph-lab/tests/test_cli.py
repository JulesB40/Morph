import argparse
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from morph_lab import cli
from morph_lab.queue import JobQueue


class CliTests(unittest.TestCase):
    def execute(self, spec_extra, outcome=None, exception=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run = root / "runs" / "fixture"
            source = run / "source"
            source.mkdir(parents=True)
            cli.write_json(run / "manifest.json", {"source": {}})
            spec = dict(argv=["test-program"], source_dir=str(source), run_dir=str(run), resources={"memory_mb": 1}, **spec_extra)
            queue = JobQueue(root / "queue.sqlite3")
            job_id = queue.submit(spec)
            args = argparse.Namespace(root=root, memory_mb=10000, reserve_mb=0, cpu=2, gpu=1)
            with patch("morph_lab.cli.available_memory_mb", return_value=10000), \
                 patch("morph_lab.snapshot.verify_snapshot", return_value=[]), \
                 patch("morph_lab.processes.supervise", return_value=outcome, side_effect=exception) as process, \
                 patch("morph_lab.evidence.write_report"), contextlib.redirect_stdout(io.StringIO()):
                cli.worker(args)
            return queue.status(job_id), process.call_count, cli.read_json(run / "result.json")

    def test_infrastructure_error_with_zero_exit_never_passes(self):
        job, _, result = self.execute({}, {"status": "infrastructure_failure", "returncode": 0, "cleanup_confirmed": True})
        self.assertEqual("infra_error", job["status"])
        self.assertEqual("infra_error", result["status"])

    def test_unknown_supervisor_outcome_never_passes(self):
        job, _, _ = self.execute({}, {"status": "unknown", "returncode": 0, "cleanup_confirmed": True})
        self.assertEqual("infra_error", job["status"])

    def test_exception_after_supervision_retains_reservation(self):
        job, _, result = self.execute({}, exception=OSError("cannot attest cleanup"))
        self.assertEqual("running", job["status"])
        self.assertFalse(result["cleanup_confirmed"])

    def test_invalid_timeout_does_not_launch_or_strand_reservation(self):
        job, calls, _ = self.execute({"timeout_seconds": "abc"})
        self.assertEqual(0, calls)
        self.assertEqual("infra_error", job["status"])

    def test_missing_required_scenario_result_cannot_pass(self):
        job, _, _ = self.execute({"scenario_results": ["probes/result.json"]},
                                {"status": "passed", "returncode": 0, "cleanup_confirmed": True})
        self.assertEqual("infra_error", job["status"])

    def test_argument_validation(self):
        for change in ({"timeout_seconds": float("nan")}, {"timeout_seconds": -1},
                       {"scenario_results": ["../outside.json"]}, {"env": {"X": 1}}):
            with self.assertRaises(ValueError):
                cli.validate_spec({"argv": ["test"], **change})


if __name__ == "__main__":
    unittest.main()
