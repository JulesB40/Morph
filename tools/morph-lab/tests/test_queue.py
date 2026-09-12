import concurrent.futures
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

from morph_lab.queue import JobQueue


class QueueTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "queue.db"
        self.queue = JobQueue(self.path)
        self.capacity = {"memory_mb": 4096, "cpu": 4, "gpu": 1}

    def claim(self, queue=None, worker="worker", **kwargs):
        return (queue or self.queue).claim(worker, self.capacity, 8192, **kwargs)

    def finish(self, job, status="pass"):
        return self.queue.finish(job["id"], job["worker"], job["lease_token"], status, cleanup_confirmed=True)

    def test_round_trip_and_terminal_statuses(self):
        for status in ("pass", "fail", "infra_error", "timeout", "canceled"):
            spec = {"argv": ["opaque.exe", "two words", "é"], "cwd": "C:/a b", "labels": {"loader": "fabric"}}
            job_id = self.queue.submit(spec)
            reopened = JobQueue(self.path)
            self.assertEqual(reopened.status(job_id)["spec"], spec)
            job = self.claim(reopened)
            self.assertEqual(job["id"], job_id)
            self.assertEqual(self.finish(job, status)["status"], status)
        self.assertEqual(len(self.queue.list()), 5)
        self.assertEqual(len(self.queue.list("pass")), 1)

    def test_competing_workers_claim_once(self):
        job_id = self.queue.submit({})
        barrier = threading.Barrier(8)

        def compete(index):
            queue = JobQueue(self.path)
            barrier.wait(timeout=10)
            return self.claim(queue, worker=str(index))

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(compete, range(8)))
        claimed = [job for job in results if job]
        self.assertEqual([job["id"] for job in claimed], [job_id])

    def test_competing_workers_cannot_overreserve_gpu(self):
        for _ in range(8):
            self.queue.submit({"resources": {"gpu": 1}})
        barrier = threading.Barrier(8)

        def compete(index):
            queue = JobQueue(self.path)
            barrier.wait(timeout=10)
            return self.claim(queue, worker=str(index))

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(compete, range(8)))
        self.assertEqual(sum(job is not None for job in results), 1)
        self.assertEqual(len(self.queue.list("queued")), 7)

    def test_multiplayer_reservation_is_atomic_and_smaller_work_can_run(self):
        large = self.queue.submit({"resources": {"memory_mb": 2048, "cpu": 3, "gpu": 2}})
        small = self.queue.submit({})
        self.assertEqual(self.claim()["id"], small)
        self.assertEqual(self.queue.status(large)["status"], "queued")
        self.assertIn("gpu", self.queue.status(large)["blocked_reason"])

    def test_memory_cpu_and_headroom_boundaries(self):
        for resource in ("memory_mb", "cpu"):
            with self.subTest(resource=resource):
                job_id = self.queue.submit({"resources": {resource: self.capacity[resource] + 1}})
                self.assertIsNone(self.claim())
                self.assertIn(resource, self.queue.status(job_id)["blocked_reason"])
                self.queue.cancel(job_id)
        job_id = self.queue.submit({"resources": {"memory_mb": 1024}})
        self.assertIsNone(self.queue.claim("w", self.capacity, 1535))
        self.assertIn("headroom", self.queue.status(job_id)["blocked_reason"])
        self.assertEqual(self.queue.claim("w", self.capacity, 1536)["id"], job_id)
        second = self.queue.submit({"resources": {"memory_mb": 1024}})
        self.assertIsNone(self.queue.claim("w2", self.capacity, 2559))
        self.assertEqual(self.queue.claim("w2", self.capacity, 2560)["id"], second)

    def test_exclusive_jobs_block_both_directions(self):
        ordinary = self.queue.submit({})
        active = self.claim()
        exclusive = self.queue.submit({"resources": {"exclusive": True}})
        self.assertIsNone(self.claim())
        self.assertEqual(active["id"], ordinary)
        self.finish(active)
        active = self.claim()
        self.assertEqual(active["id"], exclusive)
        self.queue.submit({})
        self.assertIsNone(self.claim())
        self.finish(active)
        self.assertIsNotNone(self.claim())

    def test_stale_lease_retains_resources_across_reopen_until_cleanup(self):
        with patch("morph_lab.queue.time.time", return_value=100):
            first = self.queue.submit({"resources": {"gpu": 1}})
            job = self.claim(lease_seconds=10)
            second = self.queue.submit({"resources": {"gpu": 1}})
        with patch("morph_lab.queue.time.time", return_value=111):
            reopened = JobQueue(self.path)
            self.assertTrue(reopened.status(first)["lease_expired"])
            self.assertIsNone(self.claim(reopened))
            with self.assertRaises(ValueError):
                reopened.heartbeat(first, job["worker"], job["lease_token"])
            with self.assertRaises(ValueError):
                self.finish(job)
            with self.assertRaises(ValueError):
                reopened.recover(first)
            self.assertEqual(reopened.recover(first, cleanup_confirmed=True)["status"], "infra_error")
            self.assertEqual(self.claim(reopened)["id"], second)
            self.assertEqual(reopened.status(first)["status"], "infra_error")

    def test_heartbeat_requires_current_owner_and_extends_lease(self):
        with patch("morph_lab.queue.time.time", return_value=100):
            job_id = self.queue.submit({})
            job = self.claim(lease_seconds=10)
        with patch("morph_lab.queue.time.time", return_value=109):
            for worker, token in (("wrong", job["lease_token"]), (job["worker"], "wrong")):
                with self.assertRaises(ValueError):
                    self.queue.heartbeat(job_id, worker, token)
            self.assertEqual(self.queue.heartbeat(job_id, job["worker"], job["lease_token"])["lease_until"], 139)
            with self.assertRaises(ValueError):
                self.queue.recover(job_id, cleanup_confirmed=True)
        with patch("morph_lab.queue.time.time", return_value=120):
            self.assertEqual(self.finish(job)["status"], "pass")
            with self.assertRaises(ValueError):
                self.finish(job)

    def test_cancel_retains_running_reservation_and_terminal_is_immutable(self):
        queued = self.queue.submit({})
        self.assertEqual(self.queue.cancel(queued)["status"], "canceled")
        self.assertIsNone(self.claim())
        self.queue.submit({"resources": {"gpu": 1}})
        job = self.claim()
        self.queue.submit({"resources": {"gpu": 1}})
        canceled = self.queue.cancel(job["id"])
        self.assertEqual(canceled["status"], "running")
        self.assertTrue(canceled["cancel_requested"])
        self.assertIsNone(self.claim())
        with self.assertRaises(ValueError):
            self.queue.finish(job["id"], job["worker"], job["lease_token"], "pass")
        self.assertEqual(self.finish(job)["status"], "canceled")
        next_job = self.claim()
        self.finish(next_job)
        self.assertEqual(self.queue.cancel(next_job["id"])["status"], "pass")

    def test_recover_cancel_request_without_waiting_for_expiry(self):
        job_id = self.queue.submit({})
        self.claim()
        self.queue.cancel(job_id)
        self.assertEqual(self.queue.recover(job_id, cleanup_confirmed=True)["status"], "canceled")

    def test_invalid_specs_limits_and_missing_jobs(self):
        for spec in ([], {"resources": {"gpu": -1}}, {"resources": {"cpu": True}},
                     {"resources": {"memory_mb": 0}}, {"resources": {"exclusive": "yes"}},
                     {"value": float("nan")}, {"value": "x" * (1024 * 1024)}):
            with self.subTest(spec=str(spec)[:100]), self.assertRaises(ValueError):
                self.queue.submit(spec)
        with self.assertRaises(KeyError):
            self.queue.status("absent")
        limited = JobQueue(self.path, max_pending=1)
        job_id = limited.submit({})
        with self.assertRaises(ValueError):
            limited.submit({})
        limited.cancel(job_id)
        limited.submit({})
        with self.assertRaises(ValueError):
            limited.list(limit=1001)


if __name__ == "__main__":
    unittest.main()
