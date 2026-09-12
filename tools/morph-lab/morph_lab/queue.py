"""Durable local admission queue; this module never starts or kills processes.

Expired leases retain reservations until a controller confirms owned-process
cleanup via recover(). A running cancellation is likewise only a request.
All resources of a multi-process job must be submitted as one reservation.
"""

from contextlib import contextmanager
import json
import math
from pathlib import Path
import sqlite3
import time
import uuid


TERMINAL = frozenset({"pass", "fail", "infra_error", "timeout", "canceled"})
STATUSES = TERMINAL | {"queued", "running"}


def _positive(value, name):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
        raise ValueError(f"{name} must be finite and positive")
    return value


def _resources(value, defaults=False):
    if not isinstance(value, dict):
        raise ValueError("resources/capacities must be an object")
    result = {}
    for key, default in (("memory_mb", 512), ("cpu", 1), ("gpu", 0)):
        number = value.get(key, default if defaults else 0)
        if isinstance(number, bool) or not isinstance(number, int) or number < 0:
            raise ValueError(f"{key} must be a nonnegative integer")
        result[key] = number
    if defaults and (result["memory_mb"] == 0 or result["cpu"] == 0):
        raise ValueError("jobs must reserve memory and CPU")
    exclusive = value.get("exclusive", False)
    if not isinstance(exclusive, bool):
        raise ValueError("exclusive must be boolean")
    result["exclusive"] = exclusive
    return result


def _json(value):
    encoded = json.dumps(value, allow_nan=False, sort_keys=True)
    if len(encoded.encode("utf-8")) > 1024 * 1024:
        raise ValueError("JSON payload exceeds 1 MiB")
    return encoded


class JobQueue:
    def __init__(self, dbpath, *, max_pending=1000):
        if isinstance(max_pending, bool) or not isinstance(max_pending, int) or max_pending < 1:
            raise ValueError("max_pending must be a positive integer")
        self.dbpath = str(Path(dbpath).resolve())
        self.max_pending = max_pending
        Path(self.dbpath).parent.mkdir(parents=True, exist_ok=True)
        with self._transaction() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS jobs (
                id TEXT PRIMARY KEY, spec TEXT NOT NULL, resources TEXT NOT NULL,
                status TEXT NOT NULL, created_at REAL NOT NULL, updated_at REAL NOT NULL,
                worker TEXT, lease_token TEXT, lease_until REAL,
                cancel_requested INTEGER NOT NULL DEFAULT 0, result TEXT,
                blocked_reason TEXT
            )""")
            db.execute("CREATE INDEX IF NOT EXISTS jobs_status ON jobs(status, created_at)")

    @contextmanager
    def _transaction(self):
        db = sqlite3.connect(self.dbpath, timeout=30, isolation_level=None)
        db.row_factory = sqlite3.Row
        try:
            db.execute("BEGIN IMMEDIATE")
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()

    @staticmethod
    def _decode(row):
        job = dict(row)
        for field in ("spec", "resources", "result"):
            if job[field] is not None:
                job[field] = json.loads(job[field])
        job["cancel_requested"] = bool(job["cancel_requested"])
        job["lease_expired"] = job["status"] == "running" and job["lease_until"] <= time.time()
        return job

    @staticmethod
    def _get(db, job_id):
        row = db.execute("SELECT * FROM jobs WHERE id = ?", (job_id,)).fetchone()
        if row is None:
            raise KeyError(job_id)
        return row

    @staticmethod
    def _owned(row, worker, token):
        if (row["status"] != "running" or row["worker"] != worker
                or row["lease_token"] != token or row["lease_until"] <= time.time()):
            raise ValueError("job has no live lease owned by this worker/token")

    def submit(self, spec):
        if not isinstance(spec, dict):
            raise ValueError("spec must be a JSON object")
        encoded = _json(spec)
        resources = _json(_resources(spec.get("resources", {}), defaults=True))
        job_id = uuid.uuid4().hex
        now = time.time()
        with self._transaction() as db:
            pending = db.execute("SELECT COUNT(*) FROM jobs WHERE status IN ('queued', 'running')").fetchone()[0]
            if pending >= self.max_pending:
                raise ValueError("pending job limit reached")
            db.execute("INSERT INTO jobs(id,spec,resources,status,created_at,updated_at) VALUES(?,?,?,'queued',?,?)",
                       (job_id, encoded, resources, now, now))
        return job_id

    def claim(self, worker, capacities, free_memory_mb, *, lease_seconds=30, headroom_mb=512):
        """Reserve a complete job. Free-memory check conservatively includes all leases.

        capacities describes this queue's shared machine budget, not a worker's
        individual budget. Every worker must use the same configured capacities.
        """
        if not isinstance(worker, str) or not worker:
            raise ValueError("worker must be a nonempty string")
        _positive(lease_seconds, "lease_seconds")
        capacities = _resources(capacities)
        for value in (free_memory_mb, headroom_mb):
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError("free memory and headroom must be nonnegative integers")
        with self._transaction() as db:
            active = [json.loads(row[0]) for row in db.execute("SELECT resources FROM jobs WHERE status='running'")]
            used = {key: sum(r[key] for r in active) for key in ("memory_mb", "cpu", "gpu")}
            for row in db.execute("SELECT * FROM jobs WHERE status='queued' ORDER BY created_at, rowid").fetchall():
                resources = json.loads(row["resources"])
                reason = None
                if any(r["exclusive"] for r in active) or (resources["exclusive"] and active):
                    reason = "exclusive job requires an idle queue"
                else:
                    for key in used:
                        if used[key] + resources[key] > capacities[key]:
                            reason = f"insufficient {key} capacity"
                            break
                if reason is None and used["memory_mb"] + resources["memory_mb"] + headroom_mb > free_memory_mb:
                    reason = "insufficient free memory headroom"
                if reason:
                    db.execute("UPDATE jobs SET blocked_reason=? WHERE id=?", (reason, row["id"]))
                    continue
                now = time.time()
                db.execute("""UPDATE jobs SET status='running', worker=?, lease_token=?,
                    lease_until=?, updated_at=?, blocked_reason=NULL WHERE id=?""",
                           (worker, uuid.uuid4().hex, now + lease_seconds, now, row["id"]))
                return self._decode(self._get(db, row["id"]))
        return None

    def heartbeat(self, job_id, worker, lease_token, *, lease_seconds=30):
        _positive(lease_seconds, "lease_seconds")
        with self._transaction() as db:
            row = self._get(db, job_id)
            self._owned(row, worker, lease_token)
            now = time.time()
            db.execute("UPDATE jobs SET lease_until=?,updated_at=? WHERE id=?", (now + lease_seconds, now, job_id))
            return self._decode(self._get(db, job_id))

    def finish(self, job_id, worker, lease_token, status, result=None, *, cleanup_confirmed=False):
        if status not in TERMINAL:
            raise ValueError("finish requires a terminal status")
        if cleanup_confirmed is not True:
            raise ValueError("owned-process cleanup must be confirmed before releasing resources")
        encoded = _json(result)
        with self._transaction() as db:
            row = self._get(db, job_id)
            self._owned(row, worker, lease_token)
            if row["cancel_requested"]:
                status = "canceled"
            db.execute("UPDATE jobs SET status=?,result=?,updated_at=? WHERE id=?", (status, encoded, time.time(), job_id))
            return self._decode(self._get(db, job_id))

    def cancel(self, job_id):
        with self._transaction() as db:
            row = self._get(db, job_id)
            if row["status"] in {"queued", "running"}:
                status = "canceled" if row["status"] == "queued" else "running"
                db.execute("UPDATE jobs SET status=?,cancel_requested=1,updated_at=? WHERE id=?", (status, time.time(), job_id))
            return self._decode(self._get(db, job_id))

    def recover(self, job_id, *, cleanup_confirmed=False):
        """Controller attestation after cleaning an expired/canceled job's processes.

        Never retry automatically: a retry is a separate submission with its own
        evidence. This operation is reserved for the process-owning controller.
        """
        if cleanup_confirmed is not True:
            raise ValueError("owned-process cleanup must be confirmed before recovery")
        with self._transaction() as db:
            row = self._get(db, job_id)
            if row["status"] != "running" or (row["lease_until"] > time.time() and not row["cancel_requested"]):
                raise ValueError("only expired or canceled running jobs can be recovered")
            status = "canceled" if row["cancel_requested"] else "infra_error"
            result = _json({"reason": "controller confirmed cleanup after cancellation or expired lease"})
            db.execute("UPDATE jobs SET status=?,result=?,updated_at=? WHERE id=?", (status, result, time.time(), job_id))
            return self._decode(self._get(db, job_id))

    def status(self, job_id):
        with self._transaction() as db:
            return self._decode(self._get(db, job_id))

    def list(self, status=None, *, limit=1000, offset=0):
        if status is not None and status not in STATUSES:
            raise ValueError("unknown status")
        if not isinstance(limit, int) or not 1 <= limit <= 1000 or not isinstance(offset, int) or offset < 0:
            raise ValueError("limit must be 1..1000 and offset nonnegative")
        with self._transaction() as db:
            rows = db.execute("SELECT * FROM jobs WHERE (? IS NULL OR status=?) ORDER BY created_at,rowid LIMIT ? OFFSET ?",
                              (status, status, limit, offset)).fetchall()
            return [self._decode(row) for row in rows]
