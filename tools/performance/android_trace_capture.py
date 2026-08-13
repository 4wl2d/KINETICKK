#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Capture a fail-closed Android Perfetto diagnostic outside benchmark runs.

This tool deliberately has no gfxinfo collection, score, threshold, or verdict.
Trace overhead is expected and the report marks every artifact as diagnostic-only.
The exact already-installed APK must match ``--apk`` and declare
``<profileable android:shell="true">``; the tool never installs, uninstalls,
clears application data, changes settings, wakes/unlocks a device, or accepts a
system/OEM consent prompt.
"""

from __future__ import annotations

import argparse
import contextlib
import datetime as dt
import fcntl
import hashlib
import json
import math
import os
import pathlib
import platform
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from collections.abc import Callable, Iterator, Sequence
from dataclasses import dataclass
from typing import Any, TypeVar

import android_device_benchmark as benchmark


SCHEMA_VERSION = 1
SUITE_NAME = "kinetickk-android-perfetto-diagnostic"
DEFAULT_PACKAGE = benchmark.DEFAULT_PACKAGE
DEFAULT_COMPONENT = benchmark.DEFAULT_COMPONENT
DEFAULT_FLOW = benchmark.DEFAULT_FLOW
DEFAULT_FLOW_SCHEMA = benchmark.DEFAULT_FLOW_SCHEMA
DEFAULT_SCHEMA = pathlib.Path(__file__).resolve().with_name(
    "android_trace_capture.schema.json",
)
DEFAULT_CONFIG_TEMPLATE = pathlib.Path(__file__).resolve().with_name(
    "android_trace_perfetto.pbtxt.in",
)
DEFAULT_DURATION_MILLIS = 45_000
DEFAULT_MINIMUM_TRACE_BYTES = 4_096
DEFAULT_PERFETTO_STARTUP_SETTLE_MILLIS = 1_000
PERFETTO_READINESS_TIMEOUT_MILLIS = 15_000
PERFETTO_CLOSURE_TIMEOUT_MILLIS = 10_000
PACKAGE_PATTERN = re.compile(
    r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$",
)
REMOTE_TRACE_PATTERN = re.compile(
    r"^/data/misc/perfetto-traces/kinetickk-[0-9a-f]{16}\.perfetto-trace$",
)
BASE_APK_PATTERN = re.compile(r"^/data/app/(?:[^/]+/)+base\.apk$")
INTEGER_PATTERN = re.compile(r"^-?\d+$")
SENSITIVE_UI_PACKAGES = {
    "com.android.packageinstaller",
    "com.android.permissioncontroller",
    "com.android.vending",
    "com.google.android.packageinstaller",
    "com.google.android.permissioncontroller",
    "com.miui.packageinstaller",
    "com.miui.securityadd",
    "com.miui.securitycenter",
}
SENSITIVE_UI_MARKERS = (
    "accept",
    "agree",
    "allow",
    "app scan recommended",
    "install anyway",
    "install without scanning",
    "only this time",
    "privacy policy",
    "scan app",
    "user agreement",
)


class TraceError(benchmark.BenchmarkError):
    """A missing or unsafe condition that invalidates diagnostic evidence."""


@dataclass(frozen=True)
class InputSnapshot:
    source_path: pathlib.Path
    snapshot_path: pathlib.Path
    sha256: str
    byte_count: int
    modified_at_unix_nanos: int


def snapshot_input_file(
    source_path: pathlib.Path,
    snapshot_path: pathlib.Path,
) -> InputSnapshot:
    """Copy one stable descriptor read and retain its exact identity."""

    try:
        with source_path.open("rb") as source:
            before = os.fstat(source.fileno())
            if not stat.S_ISREG(before.st_mode):
                raise TraceError(f"input is not a regular file: {source_path}")
            payload = source.read()
            after = os.fstat(source.fileno())
    except OSError as error:
        raise TraceError(f"cannot snapshot input {source_path}: {error}") from error
    identity_before = (
        before.st_dev,
        before.st_ino,
        before.st_size,
        before.st_mtime_ns,
    )
    identity_after = (
        after.st_dev,
        after.st_ino,
        after.st_size,
        after.st_mtime_ns,
    )
    if identity_before != identity_after or len(payload) != before.st_size:
        raise TraceError(f"input changed while being snapshotted: {source_path}")
    digest = hashlib.sha256(payload).hexdigest()
    try:
        written = snapshot_path.write_bytes(payload)
    except OSError as error:
        raise TraceError(f"cannot retain input snapshot {snapshot_path}: {error}") from error
    if written != len(payload) or benchmark.sha256_file(snapshot_path) != digest:
        raise TraceError(f"retained input snapshot failed verification: {snapshot_path}")
    return InputSnapshot(
        source_path=source_path,
        snapshot_path=snapshot_path,
        sha256=digest,
        byte_count=len(payload),
        modified_at_unix_nanos=before.st_mtime_ns,
    )


def require_input_snapshot_unchanged(snapshot: InputSnapshot) -> None:
    if (
        not snapshot.snapshot_path.is_file()
        or snapshot.snapshot_path.stat().st_size != snapshot.byte_count
        or benchmark.sha256_file(snapshot.snapshot_path) != snapshot.sha256
    ):
        raise TraceError(
            f"retained input snapshot changed during the run: {snapshot.snapshot_path}",
        )


def parse_duration_millis(value: str) -> int:
    parsed = int(value)
    if not 1_000 <= parsed <= 60_000:
        raise argparse.ArgumentTypeError("must be in 1000..60000")
    return parsed


def parse_minimum_trace_bytes(value: str) -> int:
    parsed = int(value)
    if not 1 <= parsed <= 512 * 1024 * 1024:
        raise argparse.ArgumentTypeError("must be in 1..536870912")
    return parsed


def parse_perfetto_startup_settle_millis(value: str) -> int:
    parsed = int(value)
    if not 250 <= parsed <= 5_000:
        raise argparse.ArgumentTypeError("must be in 250..5000")
    return parsed


def require_package_and_component(package_name: str, component: str) -> None:
    if not PACKAGE_PATTERN.fullmatch(package_name):
        raise TraceError(f"invalid Android package name: {package_name!r}")
    if not component.startswith(package_name + "/"):
        raise TraceError("component package must match --package")
    activity = component.removeprefix(package_name + "/")
    if not activity or not re.fullmatch(r"[A-Za-z0-9_.$]+", activity):
        raise TraceError(f"invalid Android component activity: {activity!r}")


def materialize_perfetto_config(
    template: str,
    *,
    package_name: str,
    duration_millis: int,
) -> str:
    require_package_and_component(package_name, f"{package_name}/.Placeholder")
    placeholders = {
        "{{PACKAGE_NAME}}": package_name,
        "{{DURATION_MILLIS}}": str(duration_millis),
    }
    materialized = template
    for placeholder, replacement in placeholders.items():
        count = materialized.count(placeholder)
        if count != 1:
            raise TraceError(
                f"Perfetto config template must contain {placeholder} exactly once; "
                f"found {count}",
            )
        materialized = materialized.replace(placeholder, replacement)
    unresolved = sorted(set(re.findall(r"\{\{[A-Z0-9_]+}}", materialized)))
    if unresolved:
        raise TraceError(f"unresolved Perfetto config placeholders: {unresolved}")
    duration_matches = re.findall(r"^\s*duration_ms:\s*(\d+)\s*$", materialized, re.MULTILINE)
    if duration_matches != [str(duration_millis)]:
        raise TraceError("materialized Perfetto config has an ambiguous duration_ms")
    atrace_apps = re.findall(r'^\s*atrace_apps:\s*"([^"]+)"\s*$', materialized, re.MULTILINE)
    if atrace_apps != [package_name]:
        raise TraceError("materialized Perfetto config does not target exactly one package")
    return materialized.rstrip() + "\n"


def parse_perfetto_query(output: str) -> dict[str, int]:
    legacy_sessions = re.findall(
        r"^\s*num_sessions:\s*(\d+)\s*$",
        output,
        re.MULTILINE,
    )
    legacy_started = re.findall(
        r"^\s*num_sessions_started:\s*(\d+)\s*$",
        output,
        re.MULTILINE,
    )
    modern = re.findall(
        r"^\s*Tracing sessions:\s*(\d+)\s*\(started:\s*(\d+)\)\s*$",
        output,
        re.MULTILINE,
    )
    if len(legacy_sessions) == 1 and len(legacy_started) == 1 and not modern:
        return {
            "num_sessions": int(legacy_sessions[0]),
            "num_sessions_started": int(legacy_started[0]),
        }
    if len(modern) == 1 and not legacy_sessions and not legacy_started:
        sessions, started = modern[0]
        return {
            "num_sessions": int(sessions),
            "num_sessions_started": int(started),
        }
    raise TraceError(
        "perfetto --query session counters are missing, duplicated, or ambiguous: "
        f"{output!r}",
    )


def require_idle_perfetto_baseline(query: dict[str, int]) -> None:
    if query.get("num_sessions") != 0 or query.get("num_sessions_started") != 0:
        raise TraceError(
            "Perfetto does not have a fully idle tracing-service baseline; "
            "diagnostic capture "
            f"requires an idle service baseline: {query}",
        )


def is_exact_single_session_delta(
    baseline: dict[str, int],
    current: dict[str, int],
) -> bool:
    return (
        baseline.get("num_sessions") == 0
        and baseline.get("num_sessions_started") == 0
        and current.get("num_sessions") == 1
        and current.get("num_sessions_started") == 1
    )


@dataclass(frozen=True)
class PerfettoSession:
    """One foreground adb shell whose lifetime encloses the remote tracer."""

    device_pid: int
    host_process: subprocess.Popen[bytes]
    command: tuple[str, ...]
    started_monotonic: float
    readiness_mode: str
    readiness_strength: str
    readiness_settle_millis: int
    query_before: str
    query_ready: str


def _allowed_read_path(path: str, owned_trace_paths: set[str]) -> bool:
    return (
        path == "/proc/meminfo"
        or path == "/system/bin/perfetto"
        or bool(BASE_APK_PATTERN.fullmatch(path))
        or path in owned_trace_paths
    )


def validate_safe_adb_arguments(
    arguments: Sequence[str],
    *,
    package_name: str,
    component: str,
    selector_input_authorized: bool,
    owned_trace_paths: set[str],
    trace_pids: set[int],
) -> None:
    """Allow only commands needed by the read-mostly trace protocol.

    In particular, this rejects install/uninstall, package-data clearing,
    setting writes, wake/unlock keys, arbitrary input, and every gfxinfo call.
    """

    args = list(arguments)
    if not args:
        raise TraceError("empty adb command is forbidden")
    if args[0] == "exec-out":
        payload = args[1:]
        if payload in (
            ["uiautomator", "dump", "/dev/tty"],
            ["uiautomator", "dump", "--compressed", "/dev/tty"],
        ):
            return
        if len(payload) == 2 and payload[0] == "cat" and _allowed_read_path(
            payload[1],
            owned_trace_paths,
        ):
            return
        raise TraceError(f"unsafe or unsupported adb exec-out command: {payload}")
    if args[0] != "shell":
        raise TraceError(f"unsafe or unsupported adb command: {args}")
    shell = args[1:]
    if not shell:
        raise TraceError("interactive adb shell is forbidden")
    command = shell[0]
    tail = shell[1:]
    if command == "getprop" and not tail:
        return
    if command == "wm" and tail in (["size"], ["density"]):
        return
    if command == "dumpsys":
        if tail and tail[0] == "gfxinfo":
            raise TraceError("gfxinfo is forbidden in a diagnostic trace run")
        allowed = (
            tail in (["display"], ["battery"], ["thermalservice"], ["window", "policy"])
            or tail == ["package", package_name]
        )
        if allowed:
            return
    if command == "settings" and len(tail) == 3 and tail[0] == "get":
        if tail[1] in {"global", "secure", "system"}:
            return
    if command == "cat" and len(tail) == 1 and _allowed_read_path(
        tail[0],
        owned_trace_paths,
    ):
        return
    if command == "pm" and tail == ["path", package_name]:
        return
    if command == "pidof" and tail in ([package_name], ["perfetto"]):
        return
    if command == "which" and tail == ["perfetto"]:
        return
    if command == "perfetto" and tail in (["--help"], ["--query"], ["--version"]):
        return
    if command == "am" and tail == ["force-stop", package_name]:
        return
    if command == "am" and tail == ["start", "-W", "-n", component]:
        return
    if command == "input":
        if not selector_input_authorized:
            raise TraceError("input is allowed only immediately after a semantic selector match")
        if len(tail) == 3 and tail[0] == "tap" and all(
            INTEGER_PATTERN.fullmatch(value) for value in tail[1:]
        ):
            return
        if len(tail) == 6 and tail[0] == "swipe" and all(
            INTEGER_PATTERN.fullmatch(value) for value in tail[1:]
        ):
            return
    if command == "test":
        if len(tail) == 3 and tail[:2] == ["!", "-e"]:
            if tail[-1] in owned_trace_paths or REMOTE_TRACE_PATTERN.fullmatch(
                tail[-1],
            ):
                return
        if len(tail) == 2 and tail[0] == "-e":
            if tail[-1] in owned_trace_paths:
                return
    if command == "stat" and len(tail) == 3 and tail[:2] == ["-c", "%s"]:
        if tail[-1] in owned_trace_paths:
            return
    if command == "rm" and len(tail) == 2 and tail[0] == "-f":
        if tail[1] in owned_trace_paths:
            return
    if command == "kill" and len(tail) == 2 and tail[0] == "-TERM":
        if tail[1].isdigit() and int(tail[1]) in trace_pids:
            return
    raise TraceError(f"unsafe or unsupported adb shell command: {shell}")


class SafeTraceTarget(benchmark.AdbTarget):
    """AdbTarget with an explicit command/mutation boundary for trace runs."""

    def __init__(
        self,
        adb: pathlib.Path,
        serial: str,
        timeout_seconds: float,
        *,
        package_name: str,
        component: str,
    ) -> None:
        super().__init__(adb, serial, timeout_seconds)
        self.package_name = package_name
        self.component = component
        self.owned_trace_paths: set[str] = set()
        self.trace_cleanup_evidence: dict[str, dict[str, Any]] = {}
        self.trace_pids: set[int] = set()
        self.perfetto_startup_evidence: dict[str, Any] | None = None
        self._selector_input_depth = 0

    def run(
        self,
        arguments: Sequence[str],
        *,
        timeout_seconds: float | None = None,
        check: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        validate_safe_adb_arguments(
            arguments,
            package_name=self.package_name,
            component=self.component,
            selector_input_authorized=self._selector_input_depth > 0,
            owned_trace_paths=self.owned_trace_paths,
            trace_pids=self.trace_pids,
        )
        return super().run(arguments, timeout_seconds=timeout_seconds, check=check)

    def exec_out_bytes(
        self,
        arguments: Sequence[str],
        *,
        timeout_seconds: float | None = None,
    ) -> bytes:
        validate_safe_adb_arguments(
            ["exec-out", *arguments],
            package_name=self.package_name,
            component=self.component,
            selector_input_authorized=False,
            owned_trace_paths=self.owned_trace_paths,
            trace_pids=self.trace_pids,
        )
        return super().exec_out_bytes(arguments, timeout_seconds=timeout_seconds)

    @contextlib.contextmanager
    def semantic_selector_input(self) -> Iterator[None]:
        self._selector_input_depth += 1
        try:
            yield
        finally:
            self._selector_input_depth -= 1

    def start_perfetto(
        self,
        config: str,
        remote_trace_path: str,
        startup_settle_millis: int,
    ) -> PerfettoSession:
        if not REMOTE_TRACE_PATTERN.fullmatch(remote_trace_path):
            raise TraceError(f"unsafe remote trace path: {remote_trace_path!r}")
        preexisting = self.perfetto_process_ids()
        if preexisting:
            raise TraceError(
                "another perfetto command is already running on this serial: "
                f"{sorted(preexisting)}",
            )
        query_before_raw, query_before = self.perfetto_query()
        require_idle_perfetto_baseline(query_before)
        self.perfetto_startup_evidence = {
            "queryBefore": query_before_raw,
            "queryLast": query_before_raw,
            "queryReady": None,
            "hostStdout": "",
            "hostStderr": "",
            "hostExitCode": None,
            "readyPid": None,
            "failureType": None,
            "failureMessage": None,
        }
        self.shell("test", "!", "-e", remote_trace_path)
        self.owned_trace_paths.add(remote_trace_path)
        self.trace_cleanup_evidence[remote_trace_path] = {
            "remoteTemporaryPath": remote_trace_path,
            "ownershipAcquired": True,
            "state": "owned-active",
            "removed": False,
            "reason": None,
        }
        command = (
            str(self.adb),
            "-s",
            self.serial,
            "shell",
            "perfetto",
            "--txt",
            "-c",
            "-",
            "-o",
            remote_trace_path,
        )
        started_monotonic = time.monotonic()
        try:
            process = subprocess.Popen(
                list(command),
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=os.environ.copy(),
            )
        except OSError as error:
            raise TraceError(f"failed to start foreground perfetto adb session: {error}") from error
        try:
            return self._establish_perfetto_session(
                process=process,
                command=command,
                config=config,
                remote_trace_path=remote_trace_path,
                startup_settle_millis=startup_settle_millis,
                started_monotonic=started_monotonic,
                query_before_raw=query_before_raw,
                query_before=query_before,
            )
        except BaseException as error:
            if self.perfetto_startup_evidence is not None:
                self.perfetto_startup_evidence["failureType"] = type(error).__name__
                self.perfetto_startup_evidence["failureMessage"] = str(error)
            try:
                self._abort_failed_perfetto_start(process, query_before)
            except Exception as cleanup_error:
                retention_reason = (
                    "Perfetto startup failed and device-session closure could not "
                    f"be proven: {cleanup_error}"
                )
                self.retain_unclosed_owned_trace(
                    remote_trace_path,
                    retention_reason,
                )
                raise TraceError(
                    f"Perfetto startup failed ({error}); cleanup also failed: "
                    f"{cleanup_error}; remote trace retained at "
                    f"{remote_trace_path}",
                ) from error
            if isinstance(error, Exception):
                evidence = self.perfetto_startup_evidence or {}
                detail = (evidence.get("hostStderr") or evidence.get("hostStdout") or "").strip()
                if detail:
                    raise TraceError(
                        f"{error}; foreground perfetto output: {detail}",
                    ) from error
            raise

    def _establish_perfetto_session(
        self,
        *,
        process: subprocess.Popen[bytes],
        command: tuple[str, ...],
        config: str,
        remote_trace_path: str,
        startup_settle_millis: int,
        started_monotonic: float,
        query_before_raw: str,
        query_before: dict[str, int],
    ) -> PerfettoSession:
        if process.stdin is None:
            raise TraceError("foreground perfetto adb session has no stdin")
        try:
            process.stdin.write(config.encode("utf-8"))
            process.stdin.close()
            process.stdin = None
        except (BrokenPipeError, OSError) as error:
            if process.stdin is not None and process.stdin.closed:
                process.stdin = None
            stdout, stderr = process.communicate()
            self._record_perfetto_startup_output(process, stdout, stderr)
            detail = (stderr or stdout).decode(errors="replace").strip()
            raise TraceError(
                f"perfetto rejected config input: {detail or error}",
            ) from error

        readiness_deadline = (
            time.monotonic() + PERFETTO_READINESS_TIMEOUT_MILLIS / 1000.0
        )
        ready_pid: int | None = None
        query_ready_raw = ""
        last_readiness = "no query attempted"
        while time.monotonic() < readiness_deadline:
            return_code = process.poll()
            if return_code is not None:
                stdout, stderr = process.communicate()
                self._record_perfetto_startup_output(process, stdout, stderr)
                detail = (stderr or stdout).decode(errors="replace").strip()
                raise TraceError(
                    f"perfetto exited before readiness ({return_code}): {detail}",
                )
            process_ids = self.perfetto_process_ids()
            if len(process_ids) > 1:
                last_readiness = f"ambiguous perfetto PIDs {sorted(process_ids)}"
                break
            try:
                query_raw, query = self.perfetto_query()
            except TraceError as error:
                last_readiness = str(error)
                time.sleep(0.25)
                continue
            if self.perfetto_startup_evidence is not None:
                self.perfetto_startup_evidence["queryLast"] = query_raw
            trace_exists = (
                self.run(
                    ["shell", "test", "-e", remote_trace_path],
                    check=False,
                ).returncode
                == 0
            )
            session_delta = is_exact_single_session_delta(query_before, query)
            last_readiness = (
                f"pids={sorted(process_ids)}, sessionDelta={session_delta}, "
                f"traceExists={trace_exists}, query={query}"
            )
            if len(process_ids) == 1 and session_delta and trace_exists:
                ready_pid = next(iter(process_ids))
                query_ready_raw = query_raw
                if self.perfetto_startup_evidence is not None:
                    self.perfetto_startup_evidence["queryReady"] = query_raw
                    self.perfetto_startup_evidence["readyPid"] = ready_pid
                break
            time.sleep(0.25)
        if ready_pid is None:
            raise TraceError(
                "foreground perfetto did not reach fail-closed readiness within "
                f"{PERFETTO_READINESS_TIMEOUT_MILLIS}ms: {last_readiness}",
            )
        pid = ready_pid
        self.trace_pids.add(pid)
        settle_deadline = time.monotonic() + startup_settle_millis / 1000.0
        while time.monotonic() < settle_deadline:
            return_code = process.poll()
            if return_code is not None:
                stdout, stderr = process.communicate()
                self._record_perfetto_startup_output(
                    process,
                    stdout,
                    stderr,
                )
                detail = (stderr or stdout).decode(errors="replace").strip()
                raise TraceError(
                    "foreground adb/perfetto exited during readiness settle "
                    f"({return_code}): {detail or 'no output'}",
                )
            if not self.trace_pid_alive(pid):
                raise TraceError(
                    f"attributed perfetto PID {pid} disappeared during readiness settle",
                )
            time.sleep(0.05)
        return PerfettoSession(
            device_pid=pid,
            host_process=process,
            command=command,
            started_monotonic=started_monotonic,
            readiness_mode=(
                "foreground-query-session-delta-pid-trace-file-plus-settle"
            ),
            readiness_strength="session-started-not-all-data-sources-acknowledged",
            readiness_settle_millis=startup_settle_millis,
            query_before=query_before_raw,
            query_ready=query_ready_raw,
        )

    def _abort_failed_perfetto_start(
        self,
        process: subprocess.Popen[bytes],
        query_before: dict[str, int],
    ) -> None:
        cleanup_errors: list[str] = []
        for owned_pid in sorted(self.trace_pids):
            try:
                self.stop_trace_process(owned_pid)
            except Exception as error:
                cleanup_errors.append(f"owned device PID {owned_pid}: {error}")
        if process.stdin is not None and process.stdin.closed:
            process.stdin = None
        try:
            if process.poll() is None:
                process.terminate()
            stdout, stderr = process.communicate(timeout=5.0)
            self._record_perfetto_startup_output(process, stdout, stderr)
        except subprocess.TimeoutExpired:
            process.kill()
            try:
                stdout, stderr = process.communicate(timeout=5.0)
                self._record_perfetto_startup_output(process, stdout, stderr)
            except subprocess.TimeoutExpired as error:
                cleanup_errors.append(f"host adb process was not reaped: {error}")
        except Exception as error:
            cleanup_errors.append(f"host adb process cleanup: {error}")
        try:
            self._wait_for_perfetto_closure(
                baseline=query_before,
                owned_pids=set(self.trace_pids),
            )
        except Exception as error:
            cleanup_errors.append(f"device trace closure proof: {error}")
        if cleanup_errors:
            raise TraceError("; ".join(cleanup_errors))

    def _record_perfetto_startup_output(
        self,
        process: subprocess.Popen[bytes],
        stdout: bytes,
        stderr: bytes,
    ) -> None:
        if self.perfetto_startup_evidence is None:
            return
        decoded_stdout = stdout.decode(errors="replace")
        decoded_stderr = stderr.decode(errors="replace")
        if decoded_stdout or not self.perfetto_startup_evidence["hostStdout"]:
            self.perfetto_startup_evidence["hostStdout"] = decoded_stdout
        if decoded_stderr or not self.perfetto_startup_evidence["hostStderr"]:
            self.perfetto_startup_evidence["hostStderr"] = decoded_stderr
        self.perfetto_startup_evidence["hostExitCode"] = getattr(
            process,
            "returncode",
            process.poll(),
        )

    def perfetto_process_ids(self) -> set[int]:
        raw = self.shell("pidof", "perfetto", check=False).strip()
        if not raw:
            return set()
        try:
            return {int(value) for value in raw.split()}
        except ValueError as error:
            raise TraceError(f"pidof perfetto returned invalid output: {raw!r}") from error

    def perfetto_query(self) -> tuple[str, dict[str, int]]:
        result = self.run(["shell", "perfetto", "--query"], check=False)
        raw = result.stdout
        if result.stderr:
            raw += ("\n" if raw else "") + result.stderr
        if result.returncode != 0:
            raise TraceError(
                f"perfetto --query failed ({result.returncode}): {raw.strip()}",
            )
        return raw, parse_perfetto_query(raw)

    def trace_pid_alive(self, pid: int) -> bool:
        if pid not in self.trace_pids:
            raise TraceError(f"refusing to inspect an unowned trace PID: {pid}")
        return pid in self.perfetto_process_ids()

    def stop_trace_process(self, pid: int) -> None:
        if self.trace_pid_alive(pid):
            self.shell("kill", "-TERM", str(pid))

    def finish_perfetto(
        self,
        session: PerfettoSession,
        *,
        deadline_monotonic: float,
    ) -> subprocess.CompletedProcess[str]:
        remaining = deadline_monotonic - time.monotonic()
        if remaining <= 0:
            remaining = 0.001
        try:
            stdout, stderr = session.host_process.communicate(timeout=remaining)
        except subprocess.TimeoutExpired as error:
            cleanup_errors: list[str] = []
            try:
                self.stop_trace_process(session.device_pid)
            except Exception as stop_error:
                cleanup_errors.append(f"owned device PID: {stop_error}")
            try:
                if session.host_process.poll() is None:
                    session.host_process.terminate()
                session.host_process.communicate(timeout=10.0)
            except subprocess.TimeoutExpired:
                session.host_process.kill()
                try:
                    session.host_process.communicate(timeout=5.0)
                except subprocess.TimeoutExpired as reap_error:
                    cleanup_errors.append(f"host adb reap: {reap_error}")
            raise TraceError(
                f"perfetto process {session.device_pid} exceeded its configured duration"
                + (f"; cleanup errors: {cleanup_errors}" if cleanup_errors else ""),
            ) from error
        result = subprocess.CompletedProcess(
            list(session.command),
            session.host_process.returncode,
            stdout.decode(errors="replace"),
            stderr.decode(errors="replace"),
        )
        if result.returncode != 0:
            detail = (result.stderr or result.stdout).strip()
            raise TraceError(
                f"foreground perfetto capture failed ({result.returncode}): {detail}",
            )
        return result

    def verify_perfetto_session_closed(self, session: PerfettoSession) -> str:
        baseline = parse_perfetto_query(session.query_before)
        return self._wait_for_perfetto_closure(
            baseline=baseline,
            owned_pids={session.device_pid},
        )

    def _wait_for_perfetto_closure(
        self,
        *,
        baseline: dict[str, int],
        owned_pids: set[int],
    ) -> str:
        baseline_sessions = baseline["num_sessions"]
        baseline_started = baseline["num_sessions_started"]
        deadline = (
            time.monotonic() + PERFETTO_CLOSURE_TIMEOUT_MILLIS / 1000.0
        )
        last_state = "not probed"
        while True:
            try:
                alive_pids = sorted(
                    pid for pid in owned_pids if self.trace_pid_alive(pid)
                )
                final_raw, final_query = self.perfetto_query()
                last_state = (
                    f"aliveOwnedPids={alive_pids}, baseline={baseline}, "
                    f"final={final_query}"
                )
                if (
                    not alive_pids
                    and final_query["num_sessions"] == baseline_sessions
                    and final_query["num_sessions_started"] == baseline_started
                ):
                    return final_raw
            except Exception as error:
                last_state = f"probeError={type(error).__name__}: {error}"
            if time.monotonic() >= deadline:
                raise TraceError(
                    "Perfetto closure/isolation invariant was not proven within "
                    f"{PERFETTO_CLOSURE_TIMEOUT_MILLIS}ms: {last_state}",
                )
            time.sleep(0.25)

    def abort_perfetto_session(self, session: PerfettoSession) -> None:
        cleanup_errors: list[str] = []
        try:
            if self.trace_pid_alive(session.device_pid):
                self.stop_trace_process(session.device_pid)
        except Exception as error:
            cleanup_errors.append(f"owned device PID {session.device_pid}: {error}")
        try:
            if session.host_process.poll() is None:
                session.host_process.terminate()
            session.host_process.communicate(timeout=10.0)
        except subprocess.TimeoutExpired:
            session.host_process.kill()
            try:
                session.host_process.communicate(timeout=5.0)
            except subprocess.TimeoutExpired as error:
                cleanup_errors.append(f"host adb process was not reaped: {error}")
        except Exception as error:
            cleanup_errors.append(f"host adb process cleanup: {error}")
        try:
            self._wait_for_perfetto_closure(
                baseline=parse_perfetto_query(session.query_before),
                owned_pids={session.device_pid},
            )
        except Exception as error:
            cleanup_errors.append(f"device trace closure proof: {error}")
        if cleanup_errors:
            raise TraceError("; ".join(cleanup_errors))

    def remove_owned_trace(self, remote_trace_path: str) -> None:
        if remote_trace_path not in self.owned_trace_paths:
            raise TraceError(f"refusing to remove an unowned trace: {remote_trace_path}")
        evidence = self.trace_cleanup_evidence[remote_trace_path]
        try:
            self.shell("rm", "-f", remote_trace_path)
            self.shell("test", "!", "-e", remote_trace_path)
        except Exception as error:
            evidence["state"] = "cleanup-failed"
            evidence["removed"] = False
            evidence["reason"] = (
                f"owned remote trace cleanup was not proven: "
                f"{type(error).__name__}: {error}"
            )
            raise
        else:
            evidence["state"] = "removed"
            evidence["removed"] = True
            evidence["reason"] = "verified-absent-after-owned-cleanup"

    def retain_unclosed_owned_trace(
        self,
        remote_trace_path: str,
        reason: str,
    ) -> None:
        if remote_trace_path not in self.owned_trace_paths:
            raise TraceError(f"cannot retain an unowned trace: {remote_trace_path}")
        evidence = self.trace_cleanup_evidence[remote_trace_path]
        evidence["state"] = "retained-unclosed"
        evidence["removed"] = False
        evidence["reason"] = reason


def require_profileable_manifest(
    manifest: dict[str, Any],
    package_name: str,
) -> None:
    if manifest.get("inspectionStatus") != "observed":
        raise TraceError("aapt2 manifest inspection is mandatory for tracing")
    if manifest.get("package") != package_name:
        raise TraceError(
            f"APK manifest package {manifest.get('package')!r} does not match "
            f"--package {package_name!r}",
        )
    if manifest.get("profileableByShellDeclared") is not True:
        raise TraceError("APK must declare <profileable android:shell=\"true\">")
    if manifest.get("debuggableDeclared") is not False:
        raise TraceError("diagnostic timing requires a non-debuggable APK")


def installed_standalone_apk_path(
    target: SafeTraceTarget,
    package_name: str,
    directory: pathlib.Path,
) -> str:
    raw = target.shell("pm", "path", package_name)
    benchmark.write_text(directory / "pm-path.txt", raw)
    paths = [
        line.removeprefix("package:").strip()
        for line in raw.splitlines()
        if line.startswith("package:")
    ]
    if len(paths) != 1 or not paths[0].endswith("/base.apk"):
        raise TraceError(
            "the installed package must contain exactly one standalone base.apk; "
            f"split or ambiguous install detected: {paths}",
        )
    return paths[0]


def observe_installed_profileable_apk(
    target: SafeTraceTarget,
    *,
    package_name: str,
    manifest: dict[str, Any],
    directory: pathlib.Path,
) -> dict[str, Any]:
    require_profileable_manifest(manifest, package_name)
    base_path = installed_standalone_apk_path(target, package_name, directory)
    installed_sha, installed_bytes = benchmark.installed_apk_sha256(target, base_path)
    package_dump = target.shell("dumpsys", "package", package_name)
    benchmark.write_text(directory / "package-dump.txt", package_dump)
    identity = benchmark.parse_package_dump(package_dump)
    provenance = benchmark.profileable_provenance(
        manifest["profileableByShellDeclared"],
        identity["profileableByShellObserved"],
    )
    if provenance["effective"] is not True:
        raise TraceError("installed package is not shell-profileable")
    if identity["debuggable"]:
        raise TraceError("installed package is debuggable; diagnostic timing is invalid")
    return {
        "observedAtUtc": benchmark.utc_now(),
        "installedBaseApkPath": base_path,
        "installedBaseApkSha256": installed_sha,
        "installedBaseApkBytes": installed_bytes,
        **identity,
        "profileableByShell": True,
        "profileableByShellProvenance": provenance,
    }


def verify_installed_profileable_apk(
    target: SafeTraceTarget,
    *,
    apk_sha256: str,
    package_name: str,
    manifest: dict[str, Any],
    directory: pathlib.Path,
) -> dict[str, Any]:
    identity = observe_installed_profileable_apk(
        target,
        package_name=package_name,
        manifest=manifest,
        directory=directory,
    )
    if identity["installedBaseApkSha256"] != apk_sha256:
        raise TraceError(
            "installed base APK SHA-256 does not match the snapshotted --apk; "
            "this tool never installs: "
            f"installed={identity['installedBaseApkSha256']}, supplied={apk_sha256}",
        )
    return {
        "verificationMode": "exact-existing-no-install-pre-and-post",
        **identity,
    }


def require_installed_package_unchanged(
    before: dict[str, Any],
    after: dict[str, Any],
    *,
    expected_apk_sha256: str,
) -> None:
    compared_fields = (
        "installedBaseApkPath",
        "installedBaseApkSha256",
        "installedBaseApkBytes",
        "versionName",
        "versionCode",
        "debuggable",
        "profileableByShell",
    )
    differences = {
        field: {"before": before.get(field), "after": after.get(field)}
        for field in compared_fields
        if before.get(field) != after.get(field)
    }
    if after.get("installedBaseApkSha256") != expected_apk_sha256:
        differences["expectedApkSha256"] = {
            "expected": expected_apk_sha256,
            "after": after.get("installedBaseApkSha256"),
        }
    if differences:
        raise TraceError(
            "installed package identity changed during diagnostic capture: "
            f"{differences}",
        )


def require_process_continuity(
    initial_processes: set[int],
    final_processes: set[int],
) -> set[int]:
    continuous = initial_processes & final_processes
    if not continuous:
        raise TraceError(
            "application process continuity was lost during diagnostic tracing: "
            f"initial={sorted(initial_processes)}, final={sorted(final_processes)}",
        )
    return continuous


def require_awake_and_unlocked(
    target: SafeTraceTarget,
    directory: pathlib.Path,
) -> dict[str, Any]:
    raw = target.shell("dumpsys", "window", "policy")
    benchmark.write_text(directory / "window-policy.txt", raw)
    policy = benchmark.parse_window_policy(raw)
    if policy["interactiveState"] != "INTERACTIVE_STATE_AWAKE":
        raise TraceError(
            "device must already be awake; the trace tool does not inject a wake/unlock key",
        )
    if policy["keyguardShowing"] is not False:
        raise TraceError("device must already be unlocked before tracing")
    return policy


def consent_or_permission_blocker(
    xml: str,
    expected_package: str,
) -> dict[str, Any] | None:
    try:
        root = ET.fromstring(xml)
    except ET.ParseError as error:
        raise TraceError(f"malformed UI hierarchy: {error}") from error
    nodes = list(root.iter("node"))
    for node in nodes:
        package_name = node.attrib.get("package", "")
        text = " ".join(
            value.strip()
            for value in (
                node.attrib.get("text", ""),
                node.attrib.get("content-desc", ""),
            )
            if value.strip()
        )
        lowered = text.lower()
        sensitive_text = any(marker in lowered for marker in SENSITIVE_UI_MARKERS)
        actionable = any(
            node.attrib.get(attribute) == "true"
            for attribute in ("clickable", "long-clickable", "checkable", "focusable")
        )
        if sensitive_text:
            return {
                "package": package_name or None,
                "text": text or None,
                "knownSensitivePackage": package_name in SENSITIVE_UI_PACKAGES,
                "knownSensitiveText": True,
                "reason": "sensitive-consent-text-is-never-an-input-target",
            }
        if not package_name and (text or actionable):
            return {
                "package": None,
                "text": text or None,
                "knownSensitivePackage": False,
                "knownSensitiveText": False,
                "reason": "package-less-meaningful-or-actionable-ui",
            }
        if package_name and package_name != expected_package:
            return {
                "package": package_name,
                "text": text or None,
                "knownSensitivePackage": package_name in SENSITIVE_UI_PACKAGES,
                "knownSensitiveText": False,
                "reason": "foreign-package-ui-is-never-an-input-target",
            }
    return None


def wait_for_safe_selector(
    target: SafeTraceTarget,
    selector: dict[str, Any],
    expected_package: str,
    timeout_millis: int,
) -> tuple[dict[str, Any], str, int, dict[str, Any]]:
    deadline = time.monotonic() + timeout_millis / 1000.0
    attempts = 0
    last_error: str | None = None
    while time.monotonic() < deadline:
        attempts += 1
        try:
            xml = benchmark.dump_ui(target)
        except benchmark.BenchmarkError as error:
            last_error = str(error)
            time.sleep(0.25)
            continue
        blocker = consent_or_permission_blocker(xml, expected_package)
        if blocker:
            raise TraceError(
                "system/OEM consent or permission UI is visible; no input was injected: "
                f"{blocker}",
            )
        resolved = benchmark.resolve_selector_in_hierarchy(
            xml,
            selector,
            expected_package,
        )
        if resolved is not None:
            match, resolved_selector = resolved
            return match, xml, attempts, resolved_selector
        time.sleep(0.25)
    suffix = f"; last hierarchy error={last_error}" if last_error else ""
    raise TraceError(
        f"UI selector was not found after {attempts} attempts: {selector}{suffix}",
    )


def execute_diagnostic_flow_steps(
    target: SafeTraceTarget,
    flow: dict[str, Any],
    package_name: str,
    directory: pathlib.Path,
    *,
    start_index: int,
    end_index: int,
    phase: str,
) -> list[dict[str, Any]]:
    """Execute semantic steps with a fresh safety hierarchy before every input."""

    events: list[dict[str, Any]] = []
    for index in range(start_index, end_index):
        step = flow["steps"][index]
        action = step["action"]
        for repetition in range(step["repeat"]):
            started = time.monotonic()
            event: dict[str, Any] = {
                "stepIndex": index,
                "repetition": repetition + 1,
                "phase": phase,
                "action": action,
                "startedAtUtc": benchmark.utc_now(),
                "requestedHierarchyReuse": step.get("reusePreviousHierarchy", False),
                "actualHierarchyMode": "fresh-before-every-input",
            }
            if action == "sleep":
                time.sleep(step["durationMillis"] / 1000.0)
                event["durationMillisRequested"] = step["durationMillis"]
            else:
                match, xml, attempts, resolved_selector = wait_for_safe_selector(
                    target,
                    step["selector"],
                    package_name,
                    step["timeoutMillis"],
                )
                artifact = (
                    f"ui-{index:03d}-{phase}-{repetition + 1:03d}-{action}.xml"
                )
                benchmark.write_text(directory / artifact, xml)
                event.update(
                    {
                        "selector": step["selector"],
                        "resolvedSelector": resolved_selector,
                        "match": match,
                        "selectorAttempts": attempts,
                        "hierarchy": {
                            "artifact": artifact,
                            "sha256": hashlib.sha256(xml.encode("utf-8")).hexdigest(),
                        },
                    },
                )
                if action in {"tap", "longPress"}:
                    x, y = match["center"]
                    with target.semantic_selector_input():
                        if action == "tap":
                            target.shell("input", "tap", str(x), str(y))
                        else:
                            target.shell(
                                "input",
                                "swipe",
                                str(x),
                                str(y),
                                str(x),
                                str(y),
                                str(step["durationMillis"]),
                            )
                            event["durationMillisRequested"] = step["durationMillis"]
            event["elapsedMillis"] = (time.monotonic() - started) * 1000.0
            events.append(event)
            if repetition + 1 < step["repeat"] and step["intervalMillis"]:
                time.sleep(step["intervalMillis"] / 1000.0)
    return events


def collect_owned_trace(
    target: SafeTraceTarget,
    remote_trace_path: str,
    local_trace_path: pathlib.Path,
    minimum_trace_bytes: int,
) -> dict[str, Any]:
    size_raw = target.shell("stat", "-c", "%s", remote_trace_path).strip()
    if not re.fullmatch(r"\d+", size_raw):
        raise TraceError(f"remote trace size is invalid: {size_raw!r}")
    remote_bytes = int(size_raw)
    payload = target.exec_out_bytes(
        ["cat", remote_trace_path],
        timeout_seconds=180.0,
    )
    if len(payload) != remote_bytes:
        raise TraceError(
            f"downloaded trace size mismatch: remote={remote_bytes}, host={len(payload)}",
        )
    if len(payload) < minimum_trace_bytes:
        raise TraceError(
            f"Perfetto trace is only {len(payload)} bytes; at least "
            f"{minimum_trace_bytes} are required",
        )
    local_trace_path.write_bytes(payload)
    return {
        "path": local_trace_path.name,
        "bytes": len(payload),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "remoteTemporaryPath": remote_trace_path,
        "remoteTemporaryArtifactRemoved": False,
    }


T = TypeVar("T")


def capture_perfetto_around(
    target: SafeTraceTarget,
    *,
    config: str,
    duration_millis: int,
    startup_settle_millis: int,
    remote_trace_path: str,
    local_trace_path: pathlib.Path,
    minimum_trace_bytes: int,
    directory: pathlib.Path,
    action: Callable[[], T],
) -> tuple[dict[str, Any], T]:
    session: PerfettoSession | None = None
    action_value: T | None = None
    trace_artifact: dict[str, Any] | None = None
    final_query_raw: str | None = None
    session_closed = False
    errors: list[tuple[str, Exception]] = []
    started_monotonic = time.monotonic()
    try:
        session = target.start_perfetto(
            config,
            remote_trace_path,
            startup_settle_millis,
        )
        benchmark.write_text(
            directory / "perfetto-query-before.txt",
            session.query_before,
        )
        benchmark.write_text(
            directory / "perfetto-query-ready.txt",
            session.query_ready,
        )
        action_value = action()
        if session.host_process.poll() is not None or not target.trace_pid_alive(
            session.device_pid,
        ):
            raise TraceError("Perfetto ended before the diagnostic UI flow completed")
        completed = target.finish_perfetto(
            session,
            deadline_monotonic=started_monotonic
            + duration_millis / 1000.0
            + 15.0,
        )
        benchmark.write_text(directory / "perfetto-stdout.txt", completed.stdout)
        benchmark.write_text(directory / "perfetto-stderr.txt", completed.stderr)
        final_query_raw = target.verify_perfetto_session_closed(session)
        benchmark.write_text(
            directory / "perfetto-query-final.txt",
            final_query_raw,
        )
        session_closed = True
        trace_artifact = collect_owned_trace(
            target,
            remote_trace_path,
            local_trace_path,
            minimum_trace_bytes,
        )
    except Exception as error:
        errors.append(("capture", error))
    finally:
        if session is not None and not session_closed:
            try:
                target.abort_perfetto_session(session)
                session_closed = True
            except Exception as cleanup_error:
                errors.append(("session-cleanup", cleanup_error))
        if remote_trace_path in target.owned_trace_paths:
            cleanup_evidence = getattr(target, "trace_cleanup_evidence", {}).get(
                remote_trace_path,
                {},
            )
            already_retained = cleanup_evidence.get("state") == "retained-unclosed"
            if already_retained:
                if not any(
                    remote_trace_path in str(error)
                    for _phase, error in errors
                ):
                    errors.append(
                        (
                            "remote-cleanup",
                            TraceError(
                                "unclosed Perfetto startup retained its remote trace: "
                                f"{remote_trace_path}",
                            ),
                        ),
                    )
            elif session is None or session_closed:
                try:
                    target.remove_owned_trace(remote_trace_path)
                    if trace_artifact is not None:
                        trace_artifact["remoteTemporaryArtifactRemoved"] = True
                except Exception as cleanup_error:
                    errors.append(("remote-cleanup", cleanup_error))
            else:
                retention_reason = (
                    "foreground session closure was not proven; live trace path "
                    "was not unlinked"
                )
                target.retain_unclosed_owned_trace(
                    remote_trace_path,
                    retention_reason,
                )
                errors.append(
                    (
                        "remote-cleanup",
                        TraceError(
                            f"{retention_reason}: {remote_trace_path}",
                        ),
                    ),
                )
    if errors:
        details = [
            f"{phase}={type(error).__name__}: {error}"
            for phase, error in errors
        ]
        raise TraceError("; ".join(details))
    if (
        session is None
        or trace_artifact is None
        or final_query_raw is None
        or action_value is None
    ):
        raise TraceError("Perfetto capture completed without required evidence")
    return {
        "backend": "perfetto",
        "pid": session.device_pid,
        "captureMode": "foreground-adb-session",
        "readinessMode": session.readiness_mode,
        "readinessStrength": session.readiness_strength,
        "readinessSettleMillis": session.readiness_settle_millis,
        "closureMode": "owned-pid-absent-query-idle-baseline-restored",
        "finalQuerySha256": hashlib.sha256(
            final_query_raw.encode("utf-8"),
        ).hexdigest(),
        "durationMillisConfigured": duration_millis,
        "elapsedMillis": (time.monotonic() - started_monotonic) * 1000.0,
        "diagnosticOnly": True,
        "eligibleForGfxinfoVerdict": False,
        "artifact": trace_artifact,
    }, action_value


def remote_trace_path(serial: str, run_id: str) -> str:
    nonce = secrets.token_hex(16)
    token = hashlib.sha256(
        f"{serial}:{run_id}:{nonce}".encode("utf-8"),
    ).hexdigest()[:16]
    return f"/data/misc/perfetto-traces/kinetickk-{token}.perfetto-trace"


def acquire_serial_trace_lock(serial: str) -> tuple[Any, dict[str, str]]:
    key = hashlib.sha256(serial.encode("utf-8")).hexdigest()[:16]
    name = f"kinetickk-android-trace-{key}.lock"
    path = pathlib.Path(tempfile.gettempdir()) / name
    handle = path.open("a+", encoding="utf-8")
    try:
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError as error:
        handle.close()
        raise TraceError(
            f"another KINETICKK trace process holds the serial lock {key}",
        ) from error
    return handle, {"key": key, "logicalPath": f"host-temp/{name}"}


def release_serial_trace_lock(handle: Any) -> None:
    try:
        fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
    finally:
        handle.close()


def device_perfetto_provenance(
    target: SafeTraceTarget,
    directory: pathlib.Path,
) -> dict[str, Any]:
    binary_path = target.shell("which", "perfetto").strip()
    if binary_path != "/system/bin/perfetto":
        raise TraceError(
            f"expected platform Perfetto at /system/bin/perfetto, found {binary_path!r}",
        )
    binary = target.exec_out_bytes(["cat", binary_path], timeout_seconds=180.0)
    if not binary:
        raise TraceError("device Perfetto binary is unreadable")
    help_result = target.run(["shell", "perfetto", "--help"], check=False)
    help_raw = help_result.stdout
    if help_result.stderr:
        help_raw += ("\n" if help_raw else "") + help_result.stderr
    if (
        not help_raw.strip()
        or "usage" not in help_raw.lower()
        or "perfetto" not in help_raw.lower()
    ):
        raise TraceError(
            f"device Perfetto help probe is not parseable ({help_result.returncode})",
        )
    benchmark.write_text(directory / "perfetto-help.txt", help_raw)
    version_result = target.run(["shell", "perfetto", "--version"], check=False)
    version_raw = version_result.stdout
    if version_result.stderr:
        version_raw += ("\n" if version_raw else "") + version_result.stderr
    benchmark.write_text(directory / "perfetto-version-probe.txt", version_raw)
    version = version_raw.strip() if version_result.returncode == 0 else None
    return {
        "logicalPath": "/system/bin/perfetto",
        "version": version,
        "versionProbeExitCode": version_result.returncode,
        "helpProbeExitCode": help_result.returncode,
        "versionProbeArtifact": "perfetto-version-probe.txt",
        "helpArtifact": "perfetto-help.txt",
        "helpSha256": hashlib.sha256(help_raw.encode("utf-8")).hexdigest(),
        "capabilities": {
            "background": "--background" in help_raw,
            "backgroundWait": "--background-wait" in help_raw,
            "detachAttach": "--detach" in help_raw and "--attach" in help_raw,
            "query": "--query" in help_raw,
        },
        "bytes": len(binary),
        "sha256": hashlib.sha256(binary).hexdigest(),
    }


def host_tool_provenance(
    adb: pathlib.Path,
    aapt2: pathlib.Path,
    *,
    flow_path: pathlib.Path,
    flow_sha256: str,
    config_template_path: pathlib.Path,
    config_template_sha256: str,
    config_materialized: str,
    repository: pathlib.Path,
) -> dict[str, Any]:
    script_path = pathlib.Path(__file__).resolve()
    benchmark_dependency = pathlib.Path(benchmark.__file__).resolve()
    adb_version_raw = benchmark.command_text([str(adb), "version"]).stdout.strip()
    adb_version = "\n".join(
        line for line in adb_version_raw.splitlines() if not line.startswith("Installed as ")
    )
    aapt2_version_result = benchmark.command_text([str(aapt2), "version"])
    return {
        "scriptPath": "tools/performance/android_trace_capture.py",
        "scriptSha256": benchmark.sha256_file(script_path),
        "benchmarkDependencyPath": "tools/performance/android_device_benchmark.py",
        "benchmarkDependencySha256": benchmark.sha256_file(benchmark_dependency),
        "schemaPath": "tools/performance/android_trace_capture.schema.json",
        "schemaSha256": benchmark.sha256_file(DEFAULT_SCHEMA),
        "flowPath": benchmark.logical_artifact_path(
            flow_path,
            repository,
            "external-flow",
        ),
        "flowSnapshotPath": "android-gameplay-flow.json",
        "flowSha256": flow_sha256,
        "flowSchemaPath": "tools/performance/android_gameplay_flow.schema.json",
        "flowSchemaSha256": benchmark.sha256_file(DEFAULT_FLOW_SCHEMA),
        "configTemplatePath": benchmark.logical_artifact_path(
            config_template_path,
            repository,
            "external-config-template",
        ),
        "configTemplateSnapshotPath": "perfetto-config-template.pbtxt.in",
        "configTemplateSha256": config_template_sha256,
        "materializedConfigSha256": hashlib.sha256(
            config_materialized.encode("utf-8"),
        ).hexdigest(),
        "pythonVersion": platform.python_version(),
        "hostPlatform": platform.platform(),
        "adbBinary": {
            "basename": adb.name,
            "logicalPath": "android-sdk/platform-tools/adb",
            "sha256": benchmark.sha256_file(adb),
        },
        "adbVersion": adb_version,
        "aapt2Binary": {
            "basename": aapt2.name,
            "logicalPath": f"android-sdk/build-tools/{aapt2.parent.name}/aapt2",
            "sha256": benchmark.sha256_file(aapt2),
            "version": (
                aapt2_version_result.stdout or aapt2_version_result.stderr
            ).strip(),
        },
    }


def retain_perfetto_startup_failure_evidence(
    target: SafeTraceTarget,
    directory: pathlib.Path,
) -> dict[str, Any] | None:
    evidence = target.perfetto_startup_evidence
    if not evidence or not evidence.get("failureType"):
        return None
    artifacts: dict[str, dict[str, Any]] = {}
    for field, filename in (
        ("queryBefore", "perfetto-startup-query-before.txt"),
        ("queryReady", "perfetto-startup-query-ready.txt"),
        ("queryLast", "perfetto-startup-query-last.txt"),
        ("hostStdout", "perfetto-startup-stdout.txt"),
        ("hostStderr", "perfetto-startup-stderr.txt"),
    ):
        raw = evidence.get(field)
        if raw is None:
            continue
        encoded = raw.encode("utf-8")
        benchmark.write_text(directory / filename, raw)
        artifacts[field] = {
            "path": filename,
            "sha256": hashlib.sha256(encoded).hexdigest(),
            "bytes": len(encoded),
        }
    return {
        "failureType": evidence["failureType"],
        "failureMessage": evidence["failureMessage"],
        "readyPid": evidence["readyPid"],
        "hostExitCode": evidence["hostExitCode"],
        "artifacts": artifacts,
    }


def run_trace_session(
    target: SafeTraceTarget,
    *,
    package_name: str,
    component: str,
    flow: dict[str, Any],
    config: str,
    duration_millis: int,
    perfetto_startup_settle_millis: int,
    minimum_trace_bytes: int,
    startup_settle_millis: int,
    maximum_thermal_status: int,
    minimum_battery_level: int,
    remote_path: str,
    directory: pathlib.Path,
) -> dict[str, Any]:
    directory.mkdir(parents=True, exist_ok=False)
    started_at = benchmark.utc_now()
    require_awake_and_unlocked(target, directory)
    target.shell("am", "force-stop", package_name)
    time.sleep(0.2)
    if benchmark.process_ids(target, package_name):
        raise TraceError("force-stop left application processes alive")
    startup_raw = target.shell(
        "am",
        "start",
        "-W",
        "-n",
        component,
        timeout_seconds=60.0,
    )
    benchmark.write_text(directory / "startup.txt", startup_raw)
    startup = benchmark.parse_startup(startup_raw)
    time.sleep(startup_settle_millis / 1000.0)
    initial_processes = benchmark.process_ids(target, package_name)
    if not initial_processes:
        raise TraceError("application process is absent after process-cold startup")

    measurement_start = flow["measurementStartStep"]
    setup_events = execute_diagnostic_flow_steps(
        target,
        flow,
        package_name,
        directory,
        start_index=0,
        end_index=measurement_start,
        phase="setup",
    )
    before = benchmark.capture_runtime_snapshot(target, directory, "pre-trace")
    benchmark.require_runtime_health(
        before,
        maximum_thermal_status=maximum_thermal_status,
        minimum_battery_level=minimum_battery_level,
    )

    trace, measurement_events = capture_perfetto_around(
        target,
        config=config,
        duration_millis=duration_millis,
        startup_settle_millis=perfetto_startup_settle_millis,
        remote_trace_path=remote_path,
        local_trace_path=directory / "diagnostic.perfetto-trace",
        minimum_trace_bytes=minimum_trace_bytes,
        directory=directory,
        action=lambda: execute_diagnostic_flow_steps(
            target,
            flow,
            package_name,
            directory,
            start_index=measurement_start,
            end_index=len(flow["steps"]),
            phase="traced-diagnostic",
        ),
    )
    events = setup_events + measurement_events
    benchmark.write_json(directory / "ui-events.json", events)
    after = benchmark.capture_runtime_snapshot(target, directory, "post-trace")
    benchmark.require_runtime_health(
        after,
        maximum_thermal_status=maximum_thermal_status,
        minimum_battery_level=minimum_battery_level,
    )
    final_xml = benchmark.dump_ui(target)
    blocker = consent_or_permission_blocker(final_xml, package_name)
    if blocker:
        raise TraceError(f"consent or permission UI appeared after tracing: {blocker}")
    if not any(
        node.attrib.get("package") == package_name
        for node in ET.fromstring(final_xml).iter("node")
    ):
        raise TraceError("final UI hierarchy does not contain the traced package")
    benchmark.write_text(directory / "ui-final.xml", final_xml)
    final_processes = benchmark.process_ids(target, package_name)
    if not final_processes:
        raise TraceError("application process exited during diagnostic tracing")
    continuous_processes = require_process_continuity(
        initial_processes,
        final_processes,
    )
    return {
        "status": "ok",
        "startedAtUtc": started_at,
        "completedAtUtc": benchmark.utc_now(),
        "processColdStart": True,
        "processIds": sorted(continuous_processes),
        "initialProcessIds": sorted(initial_processes),
        "finalProcessIds": sorted(final_processes),
        "startup": startup,
        "runtimeBefore": before,
        "runtimeAfter": after,
        "uiEventCount": len(events),
        "trace": trace,
        "artifacts": {
            "startup": "startup.txt",
            "uiEvents": "ui-events.json",
            "finalUiHierarchy": "ui-final.xml",
            "perfettoTrace": "diagnostic.perfetto-trace",
            "perfettoStdout": "perfetto-stdout.txt",
            "perfettoStderr": "perfetto-stderr.txt",
            "perfettoQueryBefore": "perfetto-query-before.txt",
            "perfettoQueryReady": "perfetto-query-ready.txt",
            "perfettoQueryFinal": "perfetto-query-final.txt",
            "materializedConfig": "../../perfetto-config.pbtxt",
        },
    }


def render_markdown(report: dict[str, Any]) -> str:
    device = report.get("device") or {}
    environment = device.get("environment") or {}
    session = device.get("session") or {}
    trace = session.get("trace") or {}
    artifact = trace.get("artifact") or {}
    cleanup_entries = device.get("traceCleanupEvidence") or []
    cleanup = cleanup_entries[0] if cleanup_entries else {}
    if artifact.get("remoteTemporaryArtifactRemoved") is True:
        cleanup_summary = (
            "- The exact remote trace temporary file was removed after its size "
            "and host bytes were verified; application data was untouched."
        )
    elif cleanup.get("state") == "removed":
        cleanup_summary = (
            "- The owned remote trace path was removed only after session closure; "
            "trace bytes were not collected or verified in this failed run."
        )
    elif cleanup:
        cleanup_summary = (
            "- Owned remote trace cleanup state: `"
            f"{cleanup.get('state')}` — {cleanup.get('reason') or 'no reason recorded'}."
        )
    else:
        cleanup_summary = "- No remote trace-file ownership was acquired."
    lines = [
        "# Android Perfetto diagnostic",
        "",
        f"Status: **{report.get('status', 'error')}**.",
        "",
        (
            "This is a separate, overhead-bearing diagnostic run. It has no gfxinfo "
            "score or benchmark verdict and must not be merged into the physical-device "
            "benchmark sample set."
        ),
        "",
        (
            f"- Source: `{report.get('source', {}).get('label', 'n/a')}` at "
            f"`{report.get('source', {}).get('revision', 'n/a')}`"
        ),
        f"- Serial: `{device.get('serial', 'n/a')}`",
        (
            f"- Device: {environment.get('manufacturer', 'n/a')} "
            f"{environment.get('model', 'n/a')} / API "
            f"{environment.get('apiLevel', 'n/a')}"
        ),
        f"- Exact APK SHA-256: `{report.get('apk', {}).get('sha256', 'n/a')}`",
        (
            "- Perfetto config SHA-256: `"
            f"{report.get('tool', {}).get('materializedConfigSha256', 'n/a')}`"
        ),
        (
            f"- Trace: `{artifact.get('path', 'n/a')}`, "
            f"{artifact.get('bytes', 'n/a')} bytes, SHA-256 "
            f"`{artifact.get('sha256', 'n/a')}`"
        ),
        "",
        "## Safety and interpretation",
        "",
        (
            "- The already-installed base APK had to match `--apk` byte-for-byte "
            "and be non-debuggable plus shell-profileable."
        ),
        (
            "- No APK install/uninstall, app-data clear, setting write, wake/unlock "
            "key, permission grant, or consent action is performed."
        ),
        (
            "- The tool process-cold-starts only this package and injects only "
            "selector-authorized gameplay taps/long presses."
        ),
        cleanup_summary,
        "- Use the trace for diagnosis in Perfetto UI, never as a frame/jank verdict.",
        "",
    ]
    error = device.get("error")
    if error:
        lines.extend(
            [
                "## Failure",
                "",
                f"`{error.get('type')}` — {error.get('message')}",
                "",
            ],
        )
    return "\n".join(lines)


def validate_final_report(report: dict[str, Any]) -> None:
    status = report.get("status")
    device = report.get("device")
    if status not in {"ok", "error"} or not isinstance(device, dict):
        raise TraceError("final trace report has invalid top-level status/device")
    if device.get("status") != status:
        raise TraceError("top-level and device trace statuses do not match")
    protocol = report.get("protocol") or {}
    isolation = protocol.get("decisionIsolation") or {}
    required_isolation = {
        "separateFromGfxinfoBenchmark": True,
        "traceOverheadExpected": True,
        "eligibleForGfxinfoVerdict": False,
        "collectsGfxinfo": False,
    }
    if isolation != required_isolation:
        raise TraceError("final report lost the diagnostic-only isolation contract")
    if status == "error":
        error = device.get("error")
        if not isinstance(error, dict) or not error.get("type") or not error.get("message"):
            raise TraceError("failed trace report does not retain a structured error")
        return
    required_device = {
        "hostSerialLock",
        "installedPackage",
        "environment",
        "perfetto",
        "session",
        "traceCleanupEvidence",
    }
    missing = required_device - set(device)
    if missing:
        raise TraceError(f"successful trace report is missing evidence: {sorted(missing)}")
    session = device["session"]
    trace_result = session.get("trace") if isinstance(session, dict) else None
    artifact = trace_result.get("artifact") if isinstance(trace_result, dict) else None
    if not isinstance(artifact, dict) or artifact.get(
        "remoteTemporaryArtifactRemoved",
    ) is not True:
        raise TraceError("successful trace report did not verify owned remote cleanup")
    cleanup_evidence = device.get("traceCleanupEvidence")
    if (
        not isinstance(cleanup_evidence, list)
        or len(cleanup_evidence) != 1
        or cleanup_evidence[0].get("state") != "removed"
        or cleanup_evidence[0].get("removed") is not True
        or cleanup_evidence[0].get("remoteTemporaryPath")
        != artifact.get("remoteTemporaryPath")
    ):
        raise TraceError(
            "successful trace report did not retain matching observed cleanup evidence",
        )
    if trace_result.get("eligibleForGfxinfoVerdict") is not False:
        raise TraceError("successful trace became eligible for a gfxinfo verdict")


def run(arguments: argparse.Namespace) -> tuple[dict[str, Any], pathlib.Path]:
    repository = arguments.repository.expanduser().resolve()
    apk = arguments.apk.expanduser().resolve()
    flow_path = arguments.flow.expanduser().resolve()
    config_template_path = arguments.config_template.expanduser().resolve()
    require_package_and_component(arguments.package, arguments.component)
    if not benchmark.SERIAL_PATTERN.fullmatch(arguments.serial):
        raise TraceError(f"invalid adb serial: {arguments.serial!r}")
    for path, description in (
        (repository, "repository"),
        (apk, "APK"),
        (flow_path, "UI flow"),
        (config_template_path, "Perfetto config template"),
        (DEFAULT_SCHEMA, "trace schema"),
        (DEFAULT_FLOW_SCHEMA, "flow schema"),
    ):
        expected = path.is_dir() if description == "repository" else path.is_file()
        if not expected:
            raise TraceError(f"{description} does not exist: {path}")
    adb = benchmark.resolve_adb(arguments.adb)
    aapt2 = benchmark.resolve_aapt2(adb)
    if aapt2 is None:
        raise TraceError("aapt2 is required to verify the profileable APK manifest")
    source = benchmark.source_identity(repository, arguments.label)
    output = benchmark.prepare_output(arguments.output)
    apk_snapshot = snapshot_input_file(apk, output / "input.apk")
    flow_snapshot = snapshot_input_file(
        flow_path,
        output / "android-gameplay-flow.json",
    )
    config_template_snapshot = snapshot_input_file(
        config_template_path,
        output / "perfetto-config-template.pbtxt.in",
    )
    flow = benchmark.load_flow(flow_snapshot.snapshot_path)
    template = config_template_snapshot.snapshot_path.read_text(encoding="utf-8")
    config = materialize_perfetto_config(
        template,
        package_name=arguments.package,
        duration_millis=arguments.duration_millis,
    )
    apk_sha = apk_snapshot.sha256
    manifest, manifest_raw = benchmark.inspect_apk_manifest(
        apk_snapshot.snapshot_path,
        aapt2,
    )
    require_profileable_manifest(manifest, arguments.package)
    for snapshot in (apk_snapshot, flow_snapshot, config_template_snapshot):
        require_input_snapshot_unchanged(snapshot)

    shutil.copyfile(DEFAULT_SCHEMA, output / DEFAULT_SCHEMA.name)
    shutil.copyfile(DEFAULT_FLOW_SCHEMA, output / DEFAULT_FLOW_SCHEMA.name)
    benchmark.write_text(output / "perfetto-config.pbtxt", config)
    if manifest_raw is not None:
        benchmark.write_text(output / "apk-manifest-aapt2.txt", manifest_raw)
    run_id = (
        "android-trace-"
        + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    )
    report: dict[str, Any] = {
        "$schema": "android_trace_capture.schema.json",
        "schemaVersion": SCHEMA_VERSION,
        "suite": SUITE_NAME,
        "status": "running",
        "runId": run_id,
        "createdAtUtc": benchmark.utc_now(),
        "source": source,
        "apk": {
            "path": benchmark.logical_artifact_path(apk, repository, "external-apk"),
            "snapshotPath": "input.apk",
            "snapshotMode": "single-open-fstat-stable-copy",
            "sha256": apk_sha,
            "bytes": apk_snapshot.byte_count,
            "modifiedAtUnixNanos": apk_snapshot.modified_at_unix_nanos,
            "manifest": manifest,
        },
        "tool": host_tool_provenance(
            adb,
            aapt2,
            flow_path=flow_path,
            flow_sha256=flow_snapshot.sha256,
            config_template_path=config_template_path,
            config_template_sha256=config_template_snapshot.sha256,
            config_materialized=config,
            repository=repository,
        ),
        "protocol": {
            "backend": "perfetto",
            "serial": arguments.serial,
            "package": arguments.package,
            "component": arguments.component,
            "durationMillis": arguments.duration_millis,
            "minimumTraceBytes": arguments.minimum_trace_bytes,
            "startupSettleMillis": arguments.startup_settle_millis,
            "captureMode": "foreground-adb-session",
            "perfettoReadinessTimeoutMillis": PERFETTO_READINESS_TIMEOUT_MILLIS,
            "perfettoClosureTimeoutMillis": PERFETTO_CLOSURE_TIMEOUT_MILLIS,
            "perfettoReadinessSettleMillis": (
                arguments.perfetto_startup_settle_millis
            ),
            "commandTimeoutSeconds": arguments.command_timeout_seconds,
            "traceCompletionGraceMillis": 15_000,
            "exclusiveHostSerialLock": True,
            "maximumThermalStatus": arguments.maximum_thermal_status,
            "minimumBatteryLevelPercent": arguments.minimum_battery_level,
            "flow": {
                "name": flow["name"],
                "schemaVersion": flow["schemaVersion"],
                "measurementStartStep": flow["measurementStartStep"],
                "stepCount": len(flow["steps"]),
                "sha256": flow_snapshot.sha256,
                "traceExecutorAlwaysUsesFreshHierarchy": True,
            },
            "decisionIsolation": {
                "separateFromGfxinfoBenchmark": True,
                "traceOverheadExpected": True,
                "eligibleForGfxinfoVerdict": False,
                "collectsGfxinfo": False,
            },
            "deviceMutationPolicy": {
                "apkInstalled": False,
                "applicationDataCleared": False,
                "packageUninstalled": False,
                "globalSettingsChanged": False,
                "screenWakeOrUnlockInjected": False,
                "consentOrPermissionPromptAccepted": False,
                "packageForceStoppedForProcessColdStart": True,
                "selectorAuthorizedGameplayInputInjected": True,
                "ownedRemoteTraceRemovalRequiredForSuccess": True,
            },
        },
        "device": {
            "serial": arguments.serial,
            "status": "running",
        },
    }
    report_path = output / "android-trace-capture.json"
    benchmark.write_json(report_path, report)
    device = report["device"]
    device_directory = output / "device"
    device_directory.mkdir(parents=True, exist_ok=False)
    serial_lock_handle: Any | None = None
    target: SafeTraceTarget | None = None
    try:
        serial_lock_handle, lock_identity = acquire_serial_trace_lock(arguments.serial)
        device["hostSerialLock"] = lock_identity
        state = benchmark.connected_devices(adb).get(arguments.serial)
        if state != "device":
            raise TraceError(
                f"required adb target is unavailable or unauthorized: state={state!r}",
            )
        target = SafeTraceTarget(
            adb,
            arguments.serial,
            arguments.command_timeout_seconds,
            package_name=arguments.package,
            component=arguments.component,
        )
        device["installedPackage"] = verify_installed_profileable_apk(
            target,
            apk_sha256=apk_sha,
            package_name=arguments.package,
            manifest=manifest,
            directory=device_directory,
        )
        require_awake_and_unlocked(target, device_directory)
        environment = benchmark.capture_device_environment(target, device_directory)
        device["environment"] = environment
        if environment["lowPowerModeEnabled"]:
            raise TraceError("low-power mode is enabled")
        benchmark.require_runtime_health(
            {
                "battery": environment["battery"],
                "thermal": environment["thermal"],
            },
            maximum_thermal_status=arguments.maximum_thermal_status,
            minimum_battery_level=arguments.minimum_battery_level,
        )
        benchmark.write_json(device_directory / "environment.json", environment)
        device["perfetto"] = device_perfetto_provenance(target, device_directory)
        session_error: Exception | None = None
        try:
            device["session"] = run_trace_session(
                target,
                package_name=arguments.package,
                component=arguments.component,
                flow=flow,
                config=config,
                duration_millis=arguments.duration_millis,
                perfetto_startup_settle_millis=(
                    arguments.perfetto_startup_settle_millis
                ),
                minimum_trace_bytes=arguments.minimum_trace_bytes,
                startup_settle_millis=arguments.startup_settle_millis,
                maximum_thermal_status=arguments.maximum_thermal_status,
                minimum_battery_level=arguments.minimum_battery_level,
                remote_path=remote_trace_path(arguments.serial, run_id),
                directory=device_directory / "session",
            )
        except Exception as error:
            session_error = error
        post_identity_error: Exception | None = None
        post_identity_directory = device_directory / "installed-after"
        post_identity_directory.mkdir(parents=True, exist_ok=False)
        try:
            post_identity = observe_installed_profileable_apk(
                target,
                package_name=arguments.package,
                manifest=manifest,
                directory=post_identity_directory,
            )
            device["installedPackage"]["postTraceVerification"] = post_identity
            try:
                require_installed_package_unchanged(
                    device["installedPackage"],
                    post_identity,
                    expected_apk_sha256=apk_sha,
                )
            except Exception:
                device["installedPackage"]["unchangedDuringCapture"] = False
                raise
            device["installedPackage"]["unchangedDuringCapture"] = True
        except Exception as error:
            post_identity_error = error
        snapshot_error: Exception | None = None
        try:
            for snapshot in (apk_snapshot, flow_snapshot, config_template_snapshot):
                require_input_snapshot_unchanged(snapshot)
        except Exception as error:
            snapshot_error = error
        run_errors = [
            (phase, error)
            for phase, error in (
                ("session", session_error),
                ("post-installed-identity", post_identity_error),
                ("input-snapshot-integrity", snapshot_error),
            )
            if error is not None
        ]
        if run_errors:
            raise TraceError(
                "; ".join(
                    f"{phase}={type(error).__name__}: {error}"
                    for phase, error in run_errors
                ),
            )
        device["status"] = "ok"
    except Exception as error:  # Keep all prior evidence and fail the whole run closed.
        device["status"] = "error"
        device["error"] = {"type": type(error).__name__, "message": str(error)}
    finally:
        if target is not None:
            startup_failure = retain_perfetto_startup_failure_evidence(
                target,
                device_directory,
            )
            if startup_failure is not None:
                device["perfettoStartupFailure"] = startup_failure
        if target is not None and target.trace_cleanup_evidence:
            device["traceCleanupEvidence"] = [
                dict(value)
                for value in target.trace_cleanup_evidence.values()
            ]
        if serial_lock_handle is not None:
            release_serial_trace_lock(serial_lock_handle)
    report["status"] = "ok" if device["status"] == "ok" else "error"
    report["completedAtUtc"] = benchmark.utc_now()
    validate_final_report(report)
    benchmark.write_json(device_directory / "device-result.json", device)
    benchmark.write_json(report_path, report)
    benchmark.write_text(output / "android-trace-capture.md", render_markdown(report))
    return report, output


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Capture a separate diagnostic Perfetto trace from one physical Android "
            "device without producing a gfxinfo verdict."
        ),
    )
    parser.add_argument("--apk", required=True, type=pathlib.Path)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--repository", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--label")
    parser.add_argument("--adb")
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--component", default=DEFAULT_COMPONENT)
    parser.add_argument("--flow", type=pathlib.Path, default=DEFAULT_FLOW)
    parser.add_argument(
        "--config-template",
        type=pathlib.Path,
        default=DEFAULT_CONFIG_TEMPLATE,
    )
    parser.add_argument(
        "--duration-millis",
        type=parse_duration_millis,
        default=DEFAULT_DURATION_MILLIS,
    )
    parser.add_argument(
        "--minimum-trace-bytes",
        type=parse_minimum_trace_bytes,
        default=DEFAULT_MINIMUM_TRACE_BYTES,
    )
    parser.add_argument(
        "--perfetto-startup-settle-millis",
        type=parse_perfetto_startup_settle_millis,
        default=DEFAULT_PERFETTO_STARTUP_SETTLE_MILLIS,
    )
    parser.add_argument(
        "--startup-settle-millis",
        type=benchmark.parse_non_negative_int,
        default=750,
    )
    parser.add_argument(
        "--maximum-thermal-status",
        type=benchmark.parse_non_negative_int,
        default=0,
    )
    parser.add_argument(
        "--minimum-battery-level",
        type=benchmark.parse_non_negative_int,
        default=50,
    )
    parser.add_argument("--command-timeout-seconds", type=float, default=30.0)
    return parser


def main() -> int:
    parser = build_parser()
    arguments = parser.parse_args()
    if (
        not math.isfinite(arguments.command_timeout_seconds)
        or arguments.command_timeout_seconds <= 0
    ):
        parser.error("--command-timeout-seconds must be greater than zero")
    if arguments.minimum_battery_level > 100:
        parser.error("--minimum-battery-level must be in 0..100")
    if arguments.duration_millis < arguments.perfetto_startup_settle_millis + 1_000:
        parser.error(
            "--duration-millis must leave at least 1000ms after Perfetto readiness "
            "settle",
        )
    try:
        report, output = run(arguments)
    except (benchmark.BenchmarkError, OSError, ValueError) as error:
        print(f"Android diagnostic trace failed: {error}", file=sys.stderr)
        return 1
    print(output / "android-trace-capture.json")
    print(output / "android-trace-capture.md")
    return 0 if report["status"] == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())
