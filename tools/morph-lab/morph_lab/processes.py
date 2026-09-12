"""Run one owned process tree with bounded lifetime and durable diagnostics.

Windows children start suspended and join a kill-on-close Job Object before
their first instruction. Job handles are never inherited, so controller death
also kills its children. No cleanup operation selects processes by name.
"""

from __future__ import annotations

import ctypes
import datetime as dt
import json
import math
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time


# Deliberately omit injection variables (PYTHONPATH, JAVA_TOOL_OPTIONS, etc.).
ENV_ALLOWLIST = frozenset({
    "SYSTEMROOT", "WINDIR", "COMSPEC", "PATH", "PATHEXT", "TEMP", "TMP",
    "HOME", "USERPROFILE", "APPDATA", "LOCALAPPDATA", "PROGRAMDATA",
    "PROGRAMFILES", "PROGRAMFILES(X86)", "COMMONPROGRAMFILES", "JAVA_HOME",
    "LANG", "LC_ALL", "NUMBER_OF_PROCESSORS",
})


def isolated_environment(overrides=None):
    """Inherit only basic runtime paths; explicit job overrides are permitted."""
    result = {k: v for k, v in os.environ.items() if k.upper() in ENV_ALLOWLIST}
    for key, value in (overrides or {}).items():
        if not isinstance(key, str) or not key or "=" in key or "\0" in key:
            raise ValueError("invalid environment key")
        if not isinstance(value, str) or "\0" in value:
            raise ValueError("environment values must be strings without NUL")
        if os.name == "nt":
            for old in list(result):
                if old.upper() == key.upper():
                    del result[old]
        result[key] = value
    return result


def _now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def _write_json(path, data):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


class _LaunchFailure(OSError):
    def __init__(self, original, pid, cleanup_confirmed, cleanup_error):
        super().__init__(str(original))
        self.pid = pid
        self.cleanup_confirmed = cleanup_confirmed
        self.cleanup_error = cleanup_error


if os.name == "nt":
    from ctypes import wintypes as w
    import msvcrt

    SIZE_T = ctypes.c_size_t
    ULONG_PTR = ctypes.c_size_t
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)

    class _StartupInfo(ctypes.Structure):
        _fields_ = [("cb", w.DWORD), ("lpReserved", w.LPWSTR),
                    ("lpDesktop", w.LPWSTR), ("lpTitle", w.LPWSTR),
                    ("dwX", w.DWORD), ("dwY", w.DWORD),
                    ("dwXSize", w.DWORD), ("dwYSize", w.DWORD),
                    ("dwXCountChars", w.DWORD), ("dwYCountChars", w.DWORD),
                    ("dwFillAttribute", w.DWORD), ("dwFlags", w.DWORD),
                    ("wShowWindow", w.WORD), ("cbReserved2", w.WORD),
                    ("lpReserved2", ctypes.c_void_p), ("hStdInput", w.HANDLE),
                    ("hStdOutput", w.HANDLE), ("hStdError", w.HANDLE)]

    class _StartupInfoEx(ctypes.Structure):
        _fields_ = [("StartupInfo", _StartupInfo), ("lpAttributeList", ctypes.c_void_p)]

    class _ProcessInfo(ctypes.Structure):
        _fields_ = [("hProcess", w.HANDLE), ("hThread", w.HANDLE),
                    ("dwProcessId", w.DWORD), ("dwThreadId", w.DWORD)]

    class _BasicLimit(ctypes.Structure):
        _fields_ = [("PerProcessUserTimeLimit", ctypes.c_longlong),
                    ("PerJobUserTimeLimit", ctypes.c_longlong), ("LimitFlags", w.DWORD),
                    ("MinimumWorkingSetSize", SIZE_T), ("MaximumWorkingSetSize", SIZE_T),
                    ("ActiveProcessLimit", w.DWORD), ("Affinity", ULONG_PTR),
                    ("PriorityClass", w.DWORD), ("SchedulingClass", w.DWORD)]

    class _IoCounters(ctypes.Structure):
        _fields_ = [(name, ctypes.c_ulonglong) for name in (
            "ReadOperationCount", "WriteOperationCount", "OtherOperationCount",
            "ReadTransferCount", "WriteTransferCount", "OtherTransferCount")]

    class _ExtendedLimit(ctypes.Structure):
        _fields_ = [("BasicLimitInformation", _BasicLimit), ("IoInfo", _IoCounters),
                    ("ProcessMemoryLimit", SIZE_T), ("JobMemoryLimit", SIZE_T),
                    ("PeakProcessMemoryUsed", SIZE_T), ("PeakJobMemoryUsed", SIZE_T)]

    class _Accounting(ctypes.Structure):
        _fields_ = [("TotalUserTime", ctypes.c_longlong), ("TotalKernelTime", ctypes.c_longlong),
                    ("ThisPeriodTotalUserTime", ctypes.c_longlong),
                    ("ThisPeriodTotalKernelTime", ctypes.c_longlong),
                    ("TotalPageFaultCount", w.DWORD), ("TotalProcesses", w.DWORD),
                    ("ActiveProcesses", w.DWORD), ("TotalTerminatedProcesses", w.DWORD)]

    class _MemoryCounters(ctypes.Structure):
        _fields_ = [("cb", w.DWORD), ("PageFaultCount", w.DWORD)] + [
            (name, SIZE_T) for name in ("PeakWorkingSetSize", "WorkingSetSize",
                "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
                "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage", "PrivateUsage")]

    def _declare(name, args, result=w.BOOL):
        function = getattr(kernel, name)
        function.argtypes = args
        function.restype = result
        return function

    _declare("CreateJobObjectW", [ctypes.c_void_p, w.LPCWSTR], w.HANDLE)
    _declare("SetInformationJobObject", [w.HANDLE, ctypes.c_int, ctypes.c_void_p, w.DWORD])
    _declare("QueryInformationJobObject", [w.HANDLE, ctypes.c_int, ctypes.c_void_p, w.DWORD, ctypes.c_void_p])
    _declare("TerminateJobObject", [w.HANDLE, w.UINT])
    _declare("TerminateProcess", [w.HANDLE, w.UINT])
    _declare("CloseHandle", [w.HANDLE])
    _declare("ResumeThread", [w.HANDLE], w.DWORD)
    _declare("WaitForSingleObject", [w.HANDLE, w.DWORD], w.DWORD)
    _declare("GetExitCodeProcess", [w.HANDLE, ctypes.POINTER(w.DWORD)])
    _declare("OpenProcess", [w.DWORD, w.BOOL, w.DWORD], w.HANDLE)
    _declare("InitializeProcThreadAttributeList", [ctypes.c_void_p, w.DWORD, w.DWORD, ctypes.POINTER(SIZE_T)])
    _declare("UpdateProcThreadAttribute", [ctypes.c_void_p, w.DWORD, ULONG_PTR, ctypes.c_void_p,
                                           SIZE_T, ctypes.c_void_p, ctypes.c_void_p])
    _declare("DeleteProcThreadAttributeList", [ctypes.c_void_p], None)
    _declare("CreateProcessW", [w.LPCWSTR, w.LPWSTR, ctypes.c_void_p, ctypes.c_void_p,
                               w.BOOL, w.DWORD, ctypes.c_void_p, w.LPCWSTR,
                               ctypes.POINTER(_StartupInfoEx), ctypes.POINTER(_ProcessInfo)])
    psapi = ctypes.WinDLL("psapi", use_last_error=True)
    psapi.GetProcessMemoryInfo.argtypes = [w.HANDLE, ctypes.c_void_p, w.DWORD]
    psapi.GetProcessMemoryInfo.restype = w.BOOL

    def _check(value):
        if not value:
            raise ctypes.WinError(ctypes.get_last_error())
        return value

    class _WindowsProcess:
        def __init__(self, argv, cwd, environment, stdout, stderr):
            self.job = self.process = self.thread = None
            self.pid = None
            attributes = None
            attributes_initialized = False
            try:
                self.job = _check(kernel.CreateJobObjectW(None, None))
                limits = _ExtendedLimit()
                limits.BasicLimitInformation.LimitFlags = 0x2000  # KILL_ON_JOB_CLOSE
                _check(kernel.SetInformationJobObject(self.job, 9, ctypes.byref(limits), ctypes.sizeof(limits)))
                size = SIZE_T()
                kernel.InitializeProcThreadAttributeList(None, 2, 0, ctypes.byref(size))
                attributes = ctypes.create_string_buffer(size.value)
                _check(kernel.InitializeProcThreadAttributeList(attributes, 2, 0, ctypes.byref(size)))
                attributes_initialized = True
                # Windows 10+ attaches the job during creation, eliminating even
                # the controller-crash gap between CreateProcess and assignment.
                jobs = (w.HANDLE * 1)(self.job)
                _check(kernel.UpdateProcThreadAttribute(attributes, 0, 0x2000D, jobs,
                                                       ctypes.sizeof(jobs), None, None))
                with (open(os.devnull, "rb") as stdin,
                      os.fdopen(os.dup(stdout.fileno()), "wb") as out_copy,
                      os.fdopen(os.dup(stderr.fileno()), "wb") as err_copy):
                    # Only these handles enter the child, even in a multithreaded controller.
                    handles = [msvcrt.get_osfhandle(stream.fileno()) for stream in (stdin, out_copy, err_copy)]
                    for handle in handles:
                        os.set_handle_inheritable(handle, True)
                    inherited = (w.HANDLE * 3)(*handles)
                    _check(kernel.UpdateProcThreadAttribute(attributes, 0, 0x20002, inherited,
                                                           ctypes.sizeof(inherited), None, None))
                    startup = _StartupInfoEx()
                    startup.StartupInfo.cb = ctypes.sizeof(startup)
                    startup.StartupInfo.dwFlags = 0x101  # USESTDHANDLES | USESHOWWINDOW
                    startup.StartupInfo.wShowWindow = 0
                    startup.StartupInfo.hStdInput, startup.StartupInfo.hStdOutput, startup.StartupInfo.hStdError = handles
                    startup.lpAttributeList = ctypes.cast(attributes, ctypes.c_void_p)
                    info = _ProcessInfo()
                    command = ctypes.create_unicode_buffer(subprocess.list2cmdline(argv))
                    block = ctypes.create_unicode_buffer("\0".join(
                        f"{key}={value}" for key, value in sorted(environment.items(), key=lambda item: item[0].upper())) + "\0\0")
                    flags = 0x08000000 | 0x00000004 | 0x00000400 | 0x00080000
                    _check(kernel.CreateProcessW(argv[0], command, None, None, True, flags, block,
                                                 str(cwd), ctypes.byref(startup), ctypes.byref(info)))
                    self.process, self.thread, self.pid = info.hProcess, info.hThread, info.dwProcessId
            except BaseException as original:
                # Assignment errors fail closed: the initial thread has never run.
                cleanup_error = None
                try:
                    if self.process:
                        _check(kernel.TerminateProcess(self.process, 1))
                        if kernel.WaitForSingleObject(self.process, 5000) != 0:
                            raise TimeoutError("suspended root termination not confirmed")
                except Exception as exc:
                    cleanup_error = str(exc)
                try:
                    self.close()
                except Exception as exc:
                    cleanup_error = f"{cleanup_error or ''} handle cleanup: {exc}"
                raise _LaunchFailure(original, self.pid, cleanup_error is None, cleanup_error) from original
            finally:
                if attributes_initialized:
                    kernel.DeleteProcThreadAttributeList(attributes)

        def start(self):
            if kernel.ResumeThread(self.thread) == 0xFFFFFFFF:
                raise ctypes.WinError(ctypes.get_last_error())
            _check(kernel.CloseHandle(self.thread))
            self.thread = None

        def poll(self):
            state = kernel.WaitForSingleObject(self.process, 0)
            if state == 258:
                return None
            if state != 0:
                raise ctypes.WinError(ctypes.get_last_error())
            code = w.DWORD()
            _check(kernel.GetExitCodeProcess(self.process, ctypes.byref(code)))
            return code.value

        def memory(self):
            return _windows_memory(self.process)

        def cleanup(self):
            _check(kernel.TerminateJobObject(self.job, 1))
            deadline = time.monotonic() + 5
            while True:
                accounting = _Accounting()
                _check(kernel.QueryInformationJobObject(self.job, 1, ctypes.byref(accounting),
                                                        ctypes.sizeof(accounting), None))
                if accounting.ActiveProcesses == 0:
                    return
                if time.monotonic() >= deadline:
                    raise TimeoutError("owned Job Object still has active processes")
                time.sleep(0.02)

        def close(self):
            errors = []
            for field in ("thread", "process", "job"):
                handle = getattr(self, field)
                if handle:
                    if kernel.CloseHandle(handle):
                        setattr(self, field, None)
                    else:
                        errors.append(f"{field}: {ctypes.WinError(ctypes.get_last_error())}")
            if errors:
                raise OSError("; ".join(errors))

    def _windows_memory(handle):
        counters = _MemoryCounters()
        counters.cb = ctypes.sizeof(counters)
        _check(psapi.GetProcessMemoryInfo(handle, ctypes.byref(counters), counters.cb))
        return {"working_set_bytes": counters.WorkingSetSize,
                "peak_working_set_bytes": counters.PeakWorkingSetSize,
                "private_bytes": counters.PrivateUsage}


def probe_memory(pid):
    """Read a process's memory; unavailable counters are reported, never guessed."""
    try:
        if os.name == "nt":
            handle = _check(kernel.OpenProcess(0x410, False, pid))
            try:
                return _windows_memory(handle)
            finally:
                kernel.CloseHandle(handle)
        values = {}
        for line in Path(f"/proc/{pid}/status").read_text().splitlines():
            key, _, value = line.partition(":")
            if key in {"VmRSS", "VmHWM"}:
                values[{"VmRSS": "working_set_bytes", "VmHWM": "peak_working_set_bytes"}[key]] = int(value.split()[0]) * 1024
        return values or {"unavailable": "no process memory counters"}
    except (OSError, ValueError) as exc:
        return {"unavailable": str(exc)}


class _PosixProcess:
    """Portable process-group fallback; deliberate setsid escape is unsupported."""
    def __init__(self, argv, cwd, environment, stdout, stderr):
        self.child = subprocess.Popen(argv, cwd=cwd, env=environment, stdin=subprocess.DEVNULL,
                                      stdout=stdout, stderr=stderr, shell=False, start_new_session=True)
        self.pid = self.child.pid

    def start(self):
        pass

    def poll(self):
        return self.child.poll()

    def memory(self):
        return probe_memory(self.pid)

    def cleanup(self):
        try:
            os.killpg(self.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        self.child.wait(timeout=5)
        deadline = time.monotonic() + 5
        while True:
            try:
                os.killpg(self.pid, 0)
            except ProcessLookupError:
                return
            if Path("/proc").is_dir():
                # Linux can retain orphan zombies after they stop executing.
                active = False
                for entry in Path("/proc").iterdir():
                    if not entry.name.isdecimal():
                        continue
                    try:
                        fields = (entry / "stat").read_text().rsplit(")", 1)[1].split()
                        if int(fields[2]) == self.pid and fields[0] != "Z":
                            active = True
                            break
                    except FileNotFoundError:
                        pass
                if not active:
                    return
            if time.monotonic() >= deadline:
                raise TimeoutError("owned process group termination not confirmed")
            time.sleep(0.02)

    def close(self):
        pass


def supervise(argv, cwd, logdir, timeout, env=None, cancelcallback=None):
    """Run explicit argv, returning an outcome even on launch/control failures.

    Each invocation requires a fresh log directory. ``env`` explicitly adds to
    the parent allowlist. Cancellation callbacks run on this thread and should
    return promptly. A callback exception is an infrastructure failure. A zero
    root exit does not permit descendants to outlive the job: cleanup is always
    performed before returning. ``cleanup_confirmed`` is a resource-release gate.
    """
    if isinstance(argv, (str, bytes)) or not argv or any(not isinstance(a, str) or "\0" in a for a in argv):
        raise ValueError("argv must be a nonempty sequence of strings without NUL")
    if not isinstance(timeout, (int, float)) or not math.isfinite(timeout) or timeout <= 0:
        raise ValueError("timeout must be finite and positive")
    cwd = Path(cwd).resolve(strict=True)
    if not cwd.is_dir():
        raise ValueError("cwd must be a directory")
    environment = isolated_environment(env)
    argv = list(argv)
    if Path(argv[0]).is_absolute():
        executable = str(Path(argv[0]).resolve())
    elif os.path.dirname(argv[0]):
        executable = str((cwd / argv[0]).resolve())
    else:
        executable = shutil.which(argv[0], path=environment.get("PATH", environment.get("Path", "")))
        if not executable:
            executable = str(cwd / argv[0])
    argv[0] = executable
    logdir = Path(logdir).resolve()
    logdir.mkdir(parents=True, exist_ok=False)
    result = {"status": "infrastructure_failure", "argv": argv, "cwd": str(cwd),
              "executable": executable, "supervisor_pid": os.getpid(), "pid": None,
              "started_at": _now(), "finished_at": None, "returncode": None,
              "timed_out": False, "cancelled": False, "cleanup_confirmed": True,
              "cleanup_error": None, "error": None, "memory": {},
              "stdout_log": str(logdir / "stdout.log"), "stderr_log": str(logdir / "stderr.log"),
              "ownership": "windows_job_kill_on_close" if os.name == "nt" else "posix_process_group"}
    manifest = logdir / "process.json"
    process = None
    start = time.monotonic()
    try:
        with open(result["stdout_log"], "xb") as stdout, open(result["stderr_log"], "xb") as stderr:
            process = (_WindowsProcess if os.name == "nt" else _PosixProcess)(argv, cwd, environment, stdout, stderr)
            result["pid"] = process.pid
            result["cleanup_confirmed"] = False
            _write_json(manifest, result)  # Windows root is still suspended here.
            process.start()
            while True:
                code = process.poll()
                if code is not None:
                    result["returncode"] = code
                    result["status"] = "passed" if code == 0 else "failed"
                    break
                try:
                    sample = process.memory()
                    for key, value in sample.items():
                        if isinstance(value, int):
                            result["memory"][key] = max(value, result["memory"].get(key, 0))
                except OSError:
                    pass  # The process may exit between poll and memory sampling.
                if cancelcallback is not None and cancelcallback():
                    result.update(status="cancelled", cancelled=True)
                    break
                if time.monotonic() - start >= timeout:
                    result.update(status="timeout", timed_out=True)
                    break
                time.sleep(0.05)
    except Exception as exc:
        result.update(status="infrastructure_failure", error=f"{type(exc).__name__}: {exc}")
        if isinstance(exc, _LaunchFailure):
            result.update(pid=exc.pid, cleanup_confirmed=exc.cleanup_confirmed,
                          cleanup_error=exc.cleanup_error)
    finally:
        if process is not None:
            try:
                process.cleanup()
                result["cleanup_confirmed"] = True
                if result["returncode"] is None:
                    result["returncode"] = process.poll()
            except Exception as exc:
                result.update(status="infrastructure_failure", cleanup_confirmed=False,
                              cleanup_error=f"{type(exc).__name__}: {exc}")
            finally:
                try:
                    process.close()
                except Exception as exc:
                    result.update(status="infrastructure_failure", cleanup_confirmed=False,
                                  cleanup_error=f"handle cleanup: {exc}")
        result["finished_at"] = _now()
        result["duration_seconds"] = time.monotonic() - start
        _write_json(manifest, result)
    return result
