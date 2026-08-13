#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Device-independent tests for the Android Perfetto diagnostic contract."""

from __future__ import annotations

import contextlib
import hashlib
import inspect
import io
import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest import mock

from jsonschema.validators import Draft202012Validator

import android_trace_capture as trace


class PerfettoConfigContractTest(unittest.TestCase):
    def test_input_snapshot_is_stable_when_source_is_replaced(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = pathlib.Path(temporary)
            source = directory / "source.bin"
            retained = directory / "retained.bin"
            source.write_bytes(b"artifact-a")
            snapshot = trace.snapshot_input_file(source, retained)
            source.write_bytes(b"artifact-b")
            self.assertEqual(b"artifact-a", retained.read_bytes())
            self.assertEqual(
                hashlib.sha256(b"artifact-a").hexdigest(),
                snapshot.sha256,
            )
            trace.require_input_snapshot_unchanged(snapshot)
            retained.write_bytes(b"tampered!")
            with self.assertRaisesRegex(trace.TraceError, "changed during the run"):
                trace.require_input_snapshot_unchanged(snapshot)

    def test_default_template_materializes_exact_package_and_duration(self) -> None:
        template = trace.DEFAULT_CONFIG_TEMPLATE.read_text(encoding="utf-8")
        materialized = trace.materialize_perfetto_config(
            template,
            package_name=trace.DEFAULT_PACKAGE,
            duration_millis=45_000,
        )
        self.assertIn("duration_ms: 45000", materialized)
        self.assertEqual(1, materialized.count(f'atrace_apps: "{trace.DEFAULT_PACKAGE}"'))
        self.assertNotIn("{{", materialized)
        self.assertEqual(
            "6ceb820a0b6eb51c44642df9753ad333b2fa801b39e695b5f9a065ad0943289b",
            hashlib.sha256(materialized.encode("utf-8")).hexdigest(),
        )

    def test_template_placeholders_are_exactly_once_and_closed(self) -> None:
        valid = 'duration_ms: {{DURATION_MILLIS}}\natrace_apps: "{{PACKAGE_NAME}}"\n'
        with self.assertRaisesRegex(trace.TraceError, "PACKAGE_NAME.*exactly once"):
            trace.materialize_perfetto_config(
                valid.replace("{{PACKAGE_NAME}}", "fixed"),
                package_name="com.example.app",
                duration_millis=1_000,
            )
        with self.assertRaisesRegex(trace.TraceError, "DURATION_MILLIS.*exactly once"):
            trace.materialize_perfetto_config(
                valid + "duration_ms: {{DURATION_MILLIS}}\n",
                package_name="com.example.app",
                duration_millis=1_000,
            )
        with self.assertRaisesRegex(trace.TraceError, "unresolved"):
            trace.materialize_perfetto_config(
                valid + "unknown: {{EXTRA}}\n",
                package_name="com.example.app",
                duration_millis=1_000,
            )

    def test_perfetto_query_supports_legacy_and_modern_formats(self) -> None:
        self.assertEqual(
            {"num_sessions": 2, "num_sessions_started": 7},
            trace.parse_perfetto_query(
                "num_sessions: 2\nnum_sessions_started: 7\n",
            ),
        )
        self.assertEqual(
            {"num_sessions": 3, "num_sessions_started": 9},
            trace.parse_perfetto_query("Tracing sessions: 3 (started: 9)\n"),
        )

    def test_perfetto_query_rejects_missing_duplicate_or_mixed_formats(self) -> None:
        invalid_values = (
            "",
            "num_sessions: 1\n",
            "Tracing sessions: 1 (started: 2)\nTracing sessions: 1 (started: 2)\n",
            (
                "num_sessions: 1\nnum_sessions_started: 2\n"
                "Tracing sessions: 1 (started: 2)\n"
            ),
        )
        for invalid in invalid_values:
            with self.subTest(invalid=invalid):
                with self.assertRaises(trace.TraceError):
                    trace.parse_perfetto_query(invalid)

    def test_perfetto_baseline_must_have_no_active_session(self) -> None:
        trace.require_idle_perfetto_baseline(
            {"num_sessions": 0, "num_sessions_started": 0},
        )
        for non_idle in (
            {"num_sessions": 1, "num_sessions_started": 1},
            {"num_sessions": 0, "num_sessions_started": 1},
        ):
            with self.subTest(non_idle=non_idle):
                with self.assertRaisesRegex(trace.TraceError, "fully idle"):
                    trace.require_idle_perfetto_baseline(non_idle)

    def test_readiness_requires_exactly_one_new_session(self) -> None:
        baseline = {"num_sessions": 0, "num_sessions_started": 0}
        self.assertTrue(
            trace.is_exact_single_session_delta(
                baseline,
                {"num_sessions": 1, "num_sessions_started": 1},
            ),
        )
        self.assertFalse(
            trace.is_exact_single_session_delta(
                baseline,
                {"num_sessions": 2, "num_sessions_started": 2},
            ),
        )
        self.assertFalse(
            trace.is_exact_single_session_delta(
                baseline,
                {"num_sessions": 1, "num_sessions_started": 2},
            ),
        )

    def test_manifest_must_be_observed_profileable_and_non_debuggable(self) -> None:
        valid = {
            "inspectionStatus": "observed",
            "package": "com.example.app",
            "profileableByShellDeclared": True,
            "debuggableDeclared": False,
        }
        trace.require_profileable_manifest(valid, "com.example.app")
        for replacement, message in (
            ({"inspectionStatus": "tool-unavailable"}, "aapt2"),
            ({"package": "com.other.app"}, "does not match"),
            ({"profileableByShellDeclared": False}, "profileable"),
            ({"debuggableDeclared": True}, "non-debuggable"),
        ):
            invalid = {**valid, **replacement}
            with self.subTest(replacement=replacement):
                with self.assertRaisesRegex(trace.TraceError, message):
                    trace.require_profileable_manifest(invalid, "com.example.app")


class InstalledIdentityTest(unittest.TestCase):
    class FakeTarget:
        def shell(self, *arguments: str, **_kwargs) -> str:
            if arguments == ("dumpsys", "package", "com.example.app"):
                return """versionCode=7 minSdk=26 targetSdk=36
versionName=1.0
pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]
isProfileableByShell=true
"""
            raise AssertionError(arguments)

    MANIFEST = {
        "inspectionStatus": "observed",
        "package": "com.example.app",
        "profileableByShellDeclared": True,
        "debuggableDeclared": False,
    }

    def test_exact_installed_apk_and_profileable_provenance_are_required(self) -> None:
        digest = "a" * 64
        with tempfile.TemporaryDirectory() as temporary, mock.patch.object(
            trace,
            "installed_standalone_apk_path",
            return_value="/data/app/example/base.apk",
        ), mock.patch.object(
            trace.benchmark,
            "installed_apk_sha256",
            return_value=(digest, 1234),
        ):
            identity = trace.verify_installed_profileable_apk(
                self.FakeTarget(),
                apk_sha256=digest,
                package_name="com.example.app",
                manifest=self.MANIFEST,
                directory=pathlib.Path(temporary),
            )
            self.assertEqual(
                "exact-existing-no-install-pre-and-post",
                identity["verificationMode"],
            )
            self.assertEqual(digest, identity["installedBaseApkSha256"])
            self.assertTrue(identity["profileableByShell"])
            self.assertFalse(identity["debuggable"])

    def test_installed_apk_hash_mismatch_stops_before_tracing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary, mock.patch.object(
            trace,
            "installed_standalone_apk_path",
            return_value="/data/app/example/base.apk",
        ), mock.patch.object(
            trace.benchmark,
            "installed_apk_sha256",
            return_value=("b" * 64, 1234),
        ):
            with self.assertRaisesRegex(trace.TraceError, "does not match"):
                trace.verify_installed_profileable_apk(
                    self.FakeTarget(),
                    apk_sha256="a" * 64,
                    package_name="com.example.app",
                    manifest=self.MANIFEST,
                    directory=pathlib.Path(temporary),
                )

    def test_split_install_is_rejected_as_a_different_effective_artifact(self) -> None:
        class SplitTarget:
            def shell(self, *arguments: str) -> str:
                self.assertions = arguments
                return (
                    "package:/data/app/example/base.apk\n"
                    "package:/data/app/example/split_config.arm64_v8a.apk\n"
                )

        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(trace.TraceError, "split or ambiguous"):
                trace.installed_standalone_apk_path(
                    SplitTarget(),
                    "com.example.app",
                    pathlib.Path(temporary),
                )

    def test_post_trace_installed_identity_must_match_every_bound_field(self) -> None:
        before = {
            "installedBaseApkPath": "/data/app/example/base.apk",
            "installedBaseApkSha256": "a" * 64,
            "installedBaseApkBytes": 1234,
            "versionName": "1.0",
            "versionCode": 7,
            "debuggable": False,
            "profileableByShell": True,
        }
        trace.require_installed_package_unchanged(
            before,
            dict(before),
            expected_apk_sha256="a" * 64,
        )
        changed = {**before, "versionCode": 8}
        with self.assertRaisesRegex(trace.TraceError, "changed during"):
            trace.require_installed_package_unchanged(
                before,
                changed,
                expected_apk_sha256="a" * 64,
            )

    def test_app_restart_does_not_satisfy_process_continuity(self) -> None:
        self.assertEqual({10}, trace.require_process_continuity({10}, {10, 11}))
        with self.assertRaisesRegex(trace.TraceError, "continuity was lost"):
            trace.require_process_continuity({10}, {11})


class SafeAdbPolicyTest(unittest.TestCase):
    PACKAGE = "com.example.app"
    COMPONENT = "com.example.app/.MainActivity"
    REMOTE = "/data/misc/perfetto-traces/kinetickk-0123456789abcdef.perfetto-trace"

    def validate(
        self,
        arguments: list[str],
        *,
        input_authorized: bool = False,
    ) -> None:
        trace.validate_safe_adb_arguments(
            arguments,
            package_name=self.PACKAGE,
            component=self.COMPONENT,
            selector_input_authorized=input_authorized,
            owned_trace_paths={self.REMOTE},
            trace_pids={9123},
        )

    def test_required_read_and_scoped_runtime_commands_are_allowed(self) -> None:
        allowed = (
            ["shell", "getprop"],
            ["shell", "wm", "size"],
            ["shell", "settings", "get", "global", "low_power"],
            ["shell", "dumpsys", "package", self.PACKAGE],
            ["shell", "pm", "path", self.PACKAGE],
            ["shell", "am", "force-stop", self.PACKAGE],
            ["shell", "am", "start", "-W", "-n", self.COMPONENT],
            ["exec-out", "uiautomator", "dump", "/dev/tty"],
            ["exec-out", "cat", self.REMOTE],
            ["shell", "kill", "-TERM", "9123"],
            ["shell", "rm", "-f", self.REMOTE],
        )
        for command in allowed:
            with self.subTest(command=command):
                self.validate(command)

    def test_mutations_arbitrary_input_and_gfxinfo_are_forbidden(self) -> None:
        forbidden = (
            ["install", "app.apk"],
            ["uninstall", self.PACKAGE],
            ["shell", "pm", "clear", self.PACKAGE],
            ["shell", "settings", "put", "global", "low_power", "0"],
            ["shell", "setprop", "persist.traced.enable", "1"],
            ["shell", "dumpsys", "gfxinfo", self.PACKAGE, "framestats"],
            ["shell", "input", "keyevent", "KEYCODE_WAKEUP"],
            ["shell", "input", "tap", "10", "20"],
            ["shell", "rm", "-rf", "/data/local/tmp"],
            ["shell", "kill", "-TERM", "9999"],
            ["shell", "kill", "-0", "9123"],
        )
        for command in forbidden:
            with self.subTest(command=command):
                with self.assertRaises(trace.TraceError):
                    self.validate(command)

    def test_only_numeric_selector_authorized_gameplay_input_is_allowed(self) -> None:
        self.validate(
            ["shell", "input", "tap", "100", "200"],
            input_authorized=True,
        )
        self.validate(
            ["shell", "input", "swipe", "1", "2", "1", "2", "650"],
            input_authorized=True,
        )
        with self.assertRaises(trace.TraceError):
            self.validate(
                ["shell", "input", "tap", "not-a-number", "2"],
                input_authorized=True,
            )

    def test_absence_preflight_does_not_grant_cleanup_ownership(self) -> None:
        kwargs = {
            "package_name": self.PACKAGE,
            "component": self.COMPONENT,
            "selector_input_authorized": False,
            "owned_trace_paths": set(),
            "trace_pids": set(),
        }
        trace.validate_safe_adb_arguments(
            ["shell", "test", "!", "-e", self.REMOTE],
            **kwargs,
        )
        with self.assertRaises(trace.TraceError):
            trace.validate_safe_adb_arguments(
                ["shell", "rm", "-f", self.REMOTE],
                **kwargs,
            )

    def test_perfetto_liveness_uses_exact_pidof_membership(self) -> None:
        target = object.__new__(trace.SafeTraceTarget)
        target.trace_pids = {9123}
        target.perfetto_process_ids = mock.Mock(return_value={9123, 9444})
        self.assertTrue(target.trace_pid_alive(9123))
        target.perfetto_process_ids.return_value = {9444}
        self.assertFalse(target.trace_pid_alive(9123))
        with self.assertRaisesRegex(trace.TraceError, "unowned"):
            target.trace_pid_alive(9444)


class ConsentGuardTest(unittest.TestCase):
    def test_play_protect_blocks_even_when_app_nodes_remain_visible(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy>
  <node package="com.example.app" text="" content-desc="Start run" />
  <node package="com.android.vending" text="Scan app" content-desc="" />
</hierarchy>"""
        blocker = trace.consent_or_permission_blocker(xml, "com.example.app")
        self.assertEqual("com.android.vending", blocker["package"])

    def test_permission_controller_is_always_a_blocker(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy>
  <node package="com.google.android.permissioncontroller" text="Allow" content-desc="" />
</hierarchy>"""
        self.assertIsNotNone(
            trace.consent_or_permission_blocker(xml, "com.example.app"),
        )

    def test_any_foreign_package_is_blocked_without_language_assumptions(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy>
  <node package="com.example.app" text="" content-desc="Start run" />
  <node package="com.unknown.oem.security" text="Разрешить" content-desc="" />
</hierarchy>"""
        blocker = trace.consent_or_permission_blocker(xml, "com.example.app")
        self.assertEqual("com.unknown.oem.security", blocker["package"])
        self.assertFalse(blocker["knownSensitiveText"])

    def test_app_only_hierarchy_is_safe(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy>
  <node package="com.example.app" text="" content-desc="Start run" />
</hierarchy>"""
        self.assertIsNone(
            trace.consent_or_permission_blocker(xml, "com.example.app"),
        )

    def test_package_less_actionable_overlay_is_blocked(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy>
  <node package="com.example.app" text="" content-desc="Start run" />
  <node package="" text="Allow" content-desc="" clickable="true" />
</hierarchy>"""
        blocker = trace.consent_or_permission_blocker(xml, "com.example.app")
        self.assertEqual(
            "sensitive-consent-text-is-never-an-input-target",
            blocker["reason"],
        )


class DiagnosticFlowTest(unittest.TestCase):
    class FakeTarget:
        def __init__(self) -> None:
            self.inputs: list[tuple[str, ...]] = []
            self.authorized = False

        @contextlib.contextmanager
        def semantic_selector_input(self):
            self.authorized = True
            try:
                yield
            finally:
                self.authorized = False

        def shell(self, *arguments: str, **_kwargs):
            if arguments[0] == "input":
                if not self.authorized:
                    raise AssertionError("input escaped selector authorization")
                self.inputs.append(arguments)
            return ""

    def test_reuse_request_still_gets_fresh_safety_hierarchy_per_input(self) -> None:
        flow = {
            "steps": [
                {
                    "action": "tap",
                    "selector": {"resourceId": "kinetickk.gameplay.dash"},
                    "timeoutMillis": 1000,
                    "repeat": 2,
                    "intervalMillis": 0,
                    "reusePreviousHierarchy": True,
                },
            ],
        }
        target = self.FakeTarget()
        xml = "<?xml version='1.0'?><hierarchy />"
        with tempfile.TemporaryDirectory() as temporary, mock.patch.object(
            trace,
            "wait_for_safe_selector",
            side_effect=[
                ({"center": [10, 20]}, xml, 1, {"resourceId": "x"}),
                ({"center": [11, 21]}, xml, 1, {"resourceId": "x"}),
            ],
        ) as wait:
            events = trace.execute_diagnostic_flow_steps(
                target,
                flow,
                "com.example.app",
                pathlib.Path(temporary),
                start_index=0,
                end_index=1,
                phase="trace",
            )
        self.assertEqual(2, wait.call_count)
        self.assertEqual(2, len(target.inputs))
        self.assertTrue(all(event["requestedHierarchyReuse"] for event in events))
        self.assertTrue(
            all(event["actualHierarchyMode"] == "fresh-before-every-input" for event in events),
        )


class TraceArtifactTest(unittest.TestCase):
    REMOTE = "/data/misc/perfetto-traces/kinetickk-0123456789abcdef.perfetto-trace"

    class FakeTarget:
        class HostProcess:
            def poll(self):
                return None

        def __init__(self, payload: bytes) -> None:
            self.payload = payload
            self.owned_trace_paths = {TraceArtifactTest.REMOTE}
            self.trace_cleanup_evidence = {}
            self.removed = False
            self.aborted = False

        def start_perfetto(self, _config: str, _path: str, settle: int):
            return trace.PerfettoSession(
                device_pid=321,
                host_process=self.HostProcess(),
                command=("adb", "shell", "perfetto"),
                started_monotonic=0.0,
                readiness_mode="test-readiness",
                readiness_strength="test-strength",
                readiness_settle_millis=settle,
                query_before="num_sessions: 0\nnum_sessions_started: 0\n",
                query_ready="num_sessions: 1\nnum_sessions_started: 1\n",
            )

        def trace_pid_alive(self, _pid: int) -> bool:
            return True

        def finish_perfetto(self, _session, **_kwargs):
            return subprocess.CompletedProcess([], 0, "capture complete\n", "")

        def verify_perfetto_session_closed(self, _session) -> str:
            return "num_sessions: 0\nnum_sessions_started: 0\n"

        def abort_perfetto_session(self, _session) -> None:
            self.aborted = True

        def shell(self, *arguments: str, **_kwargs) -> str:
            if arguments[:3] == ("stat", "-c", "%s"):
                return f"{len(self.payload)}\n"
            raise AssertionError(f"unexpected shell command: {arguments}")

        def exec_out_bytes(self, arguments, **_kwargs) -> bytes:
            if arguments == ["cat", TraceArtifactTest.REMOTE]:
                return self.payload
            raise AssertionError(f"unexpected exec-out: {arguments}")

        def remove_owned_trace(self, path: str) -> None:
            self.removed = path == TraceArtifactTest.REMOTE

    def test_capture_retains_hash_and_marks_remote_cleanup(self) -> None:
        payload = b"trace" * 1_000
        target = self.FakeTarget(payload)
        with tempfile.TemporaryDirectory() as temporary:
            directory = pathlib.Path(temporary)
            capture, value = trace.capture_perfetto_around(
                target,
                config="duration_ms: 1000\n",
                duration_millis=1_000,
                startup_settle_millis=250,
                remote_trace_path=self.REMOTE,
                local_trace_path=directory / "diagnostic.perfetto-trace",
                minimum_trace_bytes=4_096,
                directory=directory,
                action=lambda: ["flow-complete"],
            )
            self.assertEqual(payload, (directory / "diagnostic.perfetto-trace").read_bytes())
        self.assertEqual(["flow-complete"], value)
        self.assertEqual(hashlib.sha256(payload).hexdigest(), capture["artifact"]["sha256"])
        self.assertTrue(capture["artifact"]["remoteTemporaryArtifactRemoved"])
        self.assertEqual(
            "owned-pid-absent-query-idle-baseline-restored",
            capture["closureMode"],
        )
        self.assertEqual(
            hashlib.sha256(
                b"num_sessions: 0\nnum_sessions_started: 0\n",
            ).hexdigest(),
            capture["finalQuerySha256"],
        )
        self.assertFalse(capture["eligibleForGfxinfoVerdict"])
        self.assertTrue(target.removed)

    def test_short_or_size_mismatched_trace_fails_closed(self) -> None:
        class SizeMismatchTarget(self.FakeTarget):
            def shell(self, *arguments: str, **_kwargs) -> str:
                return f"{len(self.payload) + 1}\n"

        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "trace"
            with self.assertRaisesRegex(trace.TraceError, "size mismatch"):
                trace.collect_owned_trace(
                    SizeMismatchTarget(b"x" * 5_000),
                    self.REMOTE,
                    path,
                    1,
                )
            with self.assertRaisesRegex(trace.TraceError, "at least"):
                trace.collect_owned_trace(
                    self.FakeTarget(b"x" * 8),
                    self.REMOTE,
                    path,
                    100,
                )

    def test_host_artifact_write_failure_aborts_before_remote_cleanup(self) -> None:
        target = self.FakeTarget(b"trace" * 1_000)
        with tempfile.TemporaryDirectory() as temporary, mock.patch.object(
            trace.benchmark,
            "write_text",
            side_effect=OSError("disk full"),
        ):
            directory = pathlib.Path(temporary)
            with self.assertRaisesRegex(trace.TraceError, "disk full"):
                trace.capture_perfetto_around(
                    target,
                    config="duration_ms: 1000\n",
                    duration_millis=1_000,
                    startup_settle_millis=250,
                    remote_trace_path=self.REMOTE,
                    local_trace_path=directory / "diagnostic.perfetto-trace",
                    minimum_trace_bytes=1,
                    directory=directory,
                    action=lambda: ["never-reached"],
                )
        self.assertTrue(target.aborted)
        self.assertTrue(target.removed)

    def test_unclosed_startup_retains_remote_path(self) -> None:
        class RetainedStartTarget(self.FakeTarget):
            def __init__(self) -> None:
                super().__init__(b"trace" * 1_000)
                self.trace_cleanup_evidence[TraceArtifactTest.REMOTE] = {
                    "state": "retained-unclosed",
                }

            def start_perfetto(self, *_args, **_kwargs):
                raise trace.TraceError(
                    "closure not proven; remote trace retained at "
                    f"{TraceArtifactTest.REMOTE}",
                )

            def remove_owned_trace(self, _path: str) -> None:
                raise AssertionError("an unclosed trace must never be removed")

        target = RetainedStartTarget()
        with tempfile.TemporaryDirectory() as temporary:
            directory = pathlib.Path(temporary)
            with self.assertRaisesRegex(trace.TraceError, "retained"):
                trace.capture_perfetto_around(
                    target,
                    config="duration_ms: 1000\n",
                    duration_millis=1_000,
                    startup_settle_millis=250,
                    remote_trace_path=self.REMOTE,
                    local_trace_path=directory / "diagnostic.perfetto-trace",
                    minimum_trace_bytes=1,
                    directory=directory,
                    action=lambda: ["never-reached"],
                )
        self.assertFalse(target.removed)


class PerfettoStartupCleanupTest(unittest.TestCase):
    class FaultProcess:
        def __init__(self) -> None:
            self.stdin = io.BytesIO()
            self.terminated = False
            self.killed = False
            self.reaped = False

        def poll(self):
            return -15 if self.terminated or self.killed else None

        def terminate(self) -> None:
            self.terminated = True

        def kill(self) -> None:
            self.killed = True

        def communicate(self, **_kwargs):
            self.reaped = True
            return b"", b""

    def test_pre_attribution_failure_reaps_host_without_signaling_new_pid(self) -> None:
        target = object.__new__(trace.SafeTraceTarget)
        target.adb = pathlib.Path("/fake/adb")
        target.serial = "serial"
        target.timeout_seconds = 1.0
        target.package_name = "com.example.app"
        target.component = "com.example.app/.MainActivity"
        target.owned_trace_paths = set()
        target.trace_cleanup_evidence = {}
        target.trace_pids = set()
        target._selector_input_depth = 0
        target.shell = mock.Mock(return_value="")
        target.perfetto_query = mock.Mock(
            return_value=(
                "num_sessions: 0\nnum_sessions_started: 0\n",
                {"num_sessions": 0, "num_sessions_started": 0},
            ),
        )
        target.perfetto_process_ids = mock.Mock(
            side_effect=[
                set(),
                trace.TraceError("readiness PID probe failed"),
            ],
        )
        target.stop_trace_process = mock.Mock()
        process = self.FaultProcess()
        remote = TraceArtifactTest.REMOTE
        with mock.patch.object(trace.subprocess, "Popen", return_value=process):
            with self.assertRaisesRegex(trace.TraceError, "readiness PID probe failed"):
                target.start_perfetto(
                    "duration_ms: 1000\n",
                    remote,
                    250,
                )
        target.stop_trace_process.assert_not_called()
        self.assertNotIn(77, target.trace_pids)
        self.assertTrue(process.terminated)
        self.assertTrue(process.reaped)
        with tempfile.TemporaryDirectory() as temporary:
            retained = trace.retain_perfetto_startup_failure_evidence(
                target,
                pathlib.Path(temporary),
            )
            self.assertEqual("TraceError", retained["failureType"])
            self.assertIn("queryBefore", retained["artifacts"])
            self.assertIn("hostStderr", retained["artifacts"])

    def test_settle_exception_also_stops_and_reaps(self) -> None:
        target = object.__new__(trace.SafeTraceTarget)
        target.adb = pathlib.Path("/fake/adb")
        target.serial = "serial"
        target.timeout_seconds = 1.0
        target.package_name = "com.example.app"
        target.component = "com.example.app/.MainActivity"
        target.owned_trace_paths = set()
        target.trace_cleanup_evidence = {}
        target.trace_pids = set()
        target._selector_input_depth = 0
        target.shell = mock.Mock(return_value="")
        target.run = mock.Mock(
            return_value=subprocess.CompletedProcess([], 0, "", ""),
        )
        target.perfetto_query = mock.Mock(
            side_effect=[
                (
                    "num_sessions: 0\nnum_sessions_started: 0\n",
                    {"num_sessions": 0, "num_sessions_started": 0},
                ),
                (
                    "num_sessions: 1\nnum_sessions_started: 1\n",
                    {"num_sessions": 1, "num_sessions_started": 1},
                ),
                (
                    "num_sessions: 0\nnum_sessions_started: 0\n",
                    {"num_sessions": 0, "num_sessions_started": 0},
                ),
            ],
        )
        target.perfetto_process_ids = mock.Mock(side_effect=[set(), {88}])
        target.trace_pid_alive = mock.Mock(return_value=False)
        target.stop_trace_process = mock.Mock()
        process = self.FaultProcess()
        with mock.patch.object(trace.subprocess, "Popen", return_value=process):
            with self.assertRaisesRegex(trace.TraceError, "readiness settle"):
                target.start_perfetto(
                    "duration_ms: 1000\n",
                    TraceArtifactTest.REMOTE,
                    250,
                )
        target.stop_trace_process.assert_called_once_with(88)
        self.assertTrue(process.terminated)
        self.assertTrue(process.reaped)

    def test_keyboard_interrupt_during_startup_still_closes_and_reaps(self) -> None:
        target = object.__new__(trace.SafeTraceTarget)
        target.adb = pathlib.Path("/fake/adb")
        target.serial = "serial"
        target.timeout_seconds = 1.0
        target.package_name = "com.example.app"
        target.component = "com.example.app/.MainActivity"
        target.owned_trace_paths = set()
        target.trace_cleanup_evidence = {}
        target.trace_pids = set()
        target._selector_input_depth = 0
        target.shell = mock.Mock(return_value="")
        target.perfetto_query = mock.Mock(
            return_value=(
                "num_sessions: 0\nnum_sessions_started: 0\n",
                {"num_sessions": 0, "num_sessions_started": 0},
            ),
        )
        target.perfetto_process_ids = mock.Mock(
            side_effect=[set(), KeyboardInterrupt()],
        )
        process = self.FaultProcess()
        with mock.patch.object(trace.subprocess, "Popen", return_value=process):
            with self.assertRaises(KeyboardInterrupt):
                target.start_perfetto(
                    "duration_ms: 1000\n",
                    TraceArtifactTest.REMOTE,
                    250,
                )
        self.assertTrue(process.terminated)
        self.assertTrue(process.reaped)

    def test_device_term_failure_still_terminates_and_reaps_host(self) -> None:
        target = object.__new__(trace.SafeTraceTarget)
        target.trace_pids = {99}
        target.stop_trace_process = mock.Mock(
            side_effect=trace.TraceError("TERM denied"),
        )
        target.trace_pid_alive = mock.Mock(side_effect=[True, False])
        target.perfetto_query = mock.Mock(
            return_value=(
                "num_sessions: 0\nnum_sessions_started: 0\n",
                {"num_sessions": 0, "num_sessions_started": 0},
            ),
        )
        process = self.FaultProcess()
        session = trace.PerfettoSession(
            device_pid=99,
            host_process=process,
            command=("adb", "shell", "perfetto"),
            started_monotonic=0.0,
            readiness_mode="test",
            readiness_strength="test",
            readiness_settle_millis=250,
            query_before="num_sessions: 0\nnum_sessions_started: 0\n",
            query_ready="num_sessions: 1\nnum_sessions_started: 1\n",
        )
        with self.assertRaisesRegex(trace.TraceError, "TERM denied"):
            target.abort_perfetto_session(session)
        self.assertTrue(process.terminated)
        self.assertTrue(process.reaped)

    def test_abnormal_host_exit_still_stops_and_proves_device_closure(self) -> None:
        target = object.__new__(trace.SafeTraceTarget)
        target.trace_pids = {101}
        target.trace_pid_alive = mock.Mock(side_effect=[True, False])
        target.stop_trace_process = mock.Mock()
        target.perfetto_query = mock.Mock(
            return_value=(
                "num_sessions: 0\nnum_sessions_started: 0\n",
                {"num_sessions": 0, "num_sessions_started": 0},
            ),
        )
        process = self.FaultProcess()
        process.terminated = True
        session = trace.PerfettoSession(
            device_pid=101,
            host_process=process,
            command=("adb", "shell", "perfetto"),
            started_monotonic=0.0,
            readiness_mode="test",
            readiness_strength="test",
            readiness_settle_millis=250,
            query_before="num_sessions: 0\nnum_sessions_started: 0\n",
            query_ready="num_sessions: 1\nnum_sessions_started: 1\n",
        )
        target.abort_perfetto_session(session)
        target.stop_trace_process.assert_called_once_with(101)
        self.assertTrue(process.reaped)

    def test_natural_closure_restores_started_count_to_idle_zero(self) -> None:
        target = object.__new__(trace.SafeTraceTarget)
        target.trace_pids = {105}
        target.trace_pid_alive = mock.Mock(return_value=False)
        target.perfetto_query = mock.Mock(
            return_value=(
                "Tracing sessions: 0 (started: 0)\n",
                {"num_sessions": 0, "num_sessions_started": 0},
            ),
        )
        session = trace.PerfettoSession(
            device_pid=105,
            host_process=self.FaultProcess(),
            command=("adb", "shell", "perfetto"),
            started_monotonic=0.0,
            readiness_mode="test",
            readiness_strength="test",
            readiness_settle_millis=250,
            query_before="Tracing sessions: 0 (started: 0)\n",
            query_ready="Tracing sessions: 1 (started: 1)\n",
        )
        self.assertIn("started: 0", target.verify_perfetto_session_closed(session))
        target.perfetto_query.return_value = (
            "Tracing sessions: 0 (started: 1)\n",
            {"num_sessions": 0, "num_sessions_started": 1},
        )
        with mock.patch.object(trace, "PERFETTO_CLOSURE_TIMEOUT_MILLIS", 0):
            with self.assertRaisesRegex(trace.TraceError, "not proven"):
                target.verify_perfetto_session_closed(session)


class SchemaAndIsolationTest(unittest.TestCase):
    ISOLATION = {
        "separateFromGfxinfoBenchmark": True,
        "traceOverheadExpected": True,
        "eligibleForGfxinfoVerdict": False,
        "collectsGfxinfo": False,
    }

    @classmethod
    def schema_report(cls, status: str) -> dict:
        digest = "a" * 64
        timestamp = "2026-08-11T12:00:00+00:00"
        remote = TraceArtifactTest.REMOTE
        report = {
            "$schema": "android_trace_capture.schema.json",
            "schemaVersion": 1,
            "suite": "kinetickk-android-perfetto-diagnostic",
            "status": status,
            "runId": "android-trace-test",
            "createdAtUtc": timestamp,
            "completedAtUtc": timestamp,
            "source": {
                "label": "test",
                "branch": "feature/test",
                "revision": "b" * 40,
                "dirty": False,
                "gitStatusSha256": digest,
                "gitStatusEntryCount": 0,
            },
            "apk": {
                "path": "app.apk",
                "snapshotPath": "input.apk",
                "snapshotMode": "single-open-fstat-stable-copy",
                "sha256": digest,
                "bytes": 1,
                "modifiedAtUnixNanos": 1,
                "manifest": {
                    "inspectionStatus": "observed",
                    "package": "com.kinetickk.app",
                    "minSdk": 26,
                    "targetSdk": 36,
                    "debuggableDeclared": False,
                    "profileableByShellDeclared": True,
                },
            },
            "tool": {
                "scriptPath": "tools/performance/android_trace_capture.py",
                "scriptSha256": digest,
                "benchmarkDependencyPath": (
                    "tools/performance/android_device_benchmark.py"
                ),
                "benchmarkDependencySha256": digest,
                "schemaPath": "tools/performance/android_trace_capture.schema.json",
                "schemaSha256": digest,
                "flowPath": "tools/performance/android_gameplay_flow.json",
                "flowSnapshotPath": "android-gameplay-flow.json",
                "flowSha256": digest,
                "flowSchemaPath": (
                    "tools/performance/android_gameplay_flow.schema.json"
                ),
                "flowSchemaSha256": digest,
                "configTemplatePath": (
                    "tools/performance/android_trace_perfetto.pbtxt.in"
                ),
                "configTemplateSnapshotPath": (
                    "perfetto-config-template.pbtxt.in"
                ),
                "configTemplateSha256": digest,
                "materializedConfigSha256": digest,
                "pythonVersion": "3.12.0",
                "hostPlatform": "test",
                "adbBinary": {
                    "basename": "adb",
                    "logicalPath": "android-sdk/platform-tools/adb",
                    "sha256": digest,
                },
                "adbVersion": "test",
                "aapt2Binary": {
                    "basename": "aapt2",
                    "logicalPath": "android-sdk/build-tools/36.0.0/aapt2",
                    "sha256": digest,
                },
            },
            "protocol": {
                "backend": "perfetto",
                "serial": "serial",
                "package": "com.kinetickk.app",
                "component": "com.kinetickk.app/.MainActivity",
                "durationMillis": 1000,
                "minimumTraceBytes": 1,
                "startupSettleMillis": 0,
                "captureMode": "foreground-adb-session",
                "perfettoReadinessTimeoutMillis": 15000,
                "perfettoClosureTimeoutMillis": 10000,
                "perfettoReadinessSettleMillis": 250,
                "commandTimeoutSeconds": 1,
                "traceCompletionGraceMillis": 15000,
                "exclusiveHostSerialLock": True,
                "maximumThermalStatus": 3,
                "minimumBatteryLevelPercent": 1,
                "flow": {
                    "name": "test",
                    "schemaVersion": 1,
                    "measurementStartStep": 0,
                    "stepCount": 1,
                    "sha256": digest,
                    "traceExecutorAlwaysUsesFreshHierarchy": True,
                },
                "decisionIsolation": cls.ISOLATION,
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
                "serial": "serial",
                "status": status,
            },
        }
        installed = {
            "verificationMode": "exact-existing-no-install-pre-and-post",
            "observedAtUtc": timestamp,
            "installedBaseApkPath": "/data/app/example/base.apk",
            "installedBaseApkSha256": digest,
            "installedBaseApkBytes": 1,
            "versionName": "1",
            "versionCode": 1,
            "debuggable": False,
            "profileableByShell": True,
        }
        if status == "error":
            report["device"].update(
                {
                    "installedPackage": installed,
                    "error": {"type": "TraceError", "message": "blocked"},
                },
            )
            return report
        installed["postTraceVerification"] = {
            key: value
            for key, value in installed.items()
            if key not in {"verificationMode", "postTraceVerification"}
        }
        installed["unchangedDuringCapture"] = True
        report["device"].update(
            {
                "hostSerialLock": {
                    "key": "1" * 16,
                    "logicalPath": (
                        "host-temp/kinetickk-android-trace-1111111111111111.lock"
                    ),
                },
                "installedPackage": installed,
                "environment": {},
                "perfetto": {
                    "logicalPath": "/system/bin/perfetto",
                    "version": None,
                    "versionProbeExitCode": 1,
                    "helpProbeExitCode": 1,
                    "versionProbeArtifact": "perfetto-version-probe.txt",
                    "helpArtifact": "perfetto-help.txt",
                    "helpSha256": digest,
                    "capabilities": {
                        "background": True,
                        "backgroundWait": False,
                        "detachAttach": True,
                        "query": True,
                    },
                    "bytes": 1,
                    "sha256": digest,
                },
                "session": {
                    "status": "ok",
                    "startedAtUtc": timestamp,
                    "completedAtUtc": timestamp,
                    "processColdStart": True,
                    "processIds": [1],
                    "initialProcessIds": [1],
                    "finalProcessIds": [1],
                    "startup": {},
                    "runtimeBefore": {},
                    "runtimeAfter": {},
                    "uiEventCount": 1,
                    "trace": {
                        "backend": "perfetto",
                        "pid": 2,
                        "captureMode": "foreground-adb-session",
                        "readinessMode": (
                            "foreground-query-session-delta-pid-trace-file-plus-settle"
                        ),
                        "readinessStrength": (
                            "session-started-not-all-data-sources-acknowledged"
                        ),
                        "readinessSettleMillis": 250,
                        "closureMode": (
                            "owned-pid-absent-query-idle-baseline-restored"
                        ),
                        "finalQuerySha256": digest,
                        "durationMillisConfigured": 1000,
                        "elapsedMillis": 1,
                        "diagnosticOnly": True,
                        "eligibleForGfxinfoVerdict": False,
                        "artifact": {
                            "path": "diagnostic.perfetto-trace",
                            "bytes": 1,
                            "sha256": digest,
                            "remoteTemporaryPath": remote,
                            "remoteTemporaryArtifactRemoved": True,
                        },
                    },
                    "artifacts": {},
                },
                "traceCleanupEvidence": [
                    {
                        "remoteTemporaryPath": remote,
                        "ownershipAcquired": True,
                        "state": "removed",
                        "removed": True,
                        "reason": "verified-absent-after-owned-cleanup",
                    },
                ],
            },
        )
        return report

    def test_schema_is_valid_and_hard_codes_diagnostic_isolation(self) -> None:
        schema = json.loads(trace.DEFAULT_SCHEMA.read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(schema)
        references: list[str] = []

        def visit(value) -> None:
            if isinstance(value, dict):
                if isinstance(value.get("$ref"), str):
                    references.append(value["$ref"])
                for child in value.values():
                    visit(child)
            elif isinstance(value, list):
                for child in value:
                    visit(child)

        visit(schema)
        for reference in references:
            self.assertTrue(reference.startswith("#/$defs/"), reference)
            self.assertIn(reference.removeprefix("#/$defs/"), schema["$defs"])
        decision = schema["$defs"]["protocol"]["properties"]["decisionIsolation"]
        properties = decision["properties"]
        self.assertEqual(False, properties["eligibleForGfxinfoVerdict"]["const"])
        self.assertEqual(False, properties["collectsGfxinfo"]["const"])
        self.assertEqual(True, properties["separateFromGfxinfoBenchmark"]["const"])
        closure = schema["$defs"]["trace"]["properties"]["closureMode"]
        self.assertIn("Endpoint evidence", closure["description"])
        self.assertIn("not continuous detection", closure["description"])

    def test_docs_do_not_overclaim_continuous_detached_session_isolation(self) -> None:
        documentation = trace.DEFAULT_SCHEMA.with_name(
            "android_trace_capture.md",
        ).read_text(encoding="utf-8")
        self.assertIn("endpoint/current-state probes", documentation)
        self.assertIn("theoretically unobservable", documentation)
        self.assertNotIn(
            "fails if another detached session starts during the capture",
            documentation,
        )

    def test_schema_accepts_concrete_success_and_partial_error_reports(self) -> None:
        schema = json.loads(trace.DEFAULT_SCHEMA.read_text(encoding="utf-8"))
        validator = Draft202012Validator(schema)
        validator.validate(self.schema_report("ok"))
        validator.validate(self.schema_report("error"))

    def test_trace_session_has_no_gfxinfo_collection(self) -> None:
        source = inspect.getsource(trace.run_trace_session)
        self.assertNotIn("gfxinfo", source.lower())
        self.assertNotIn("framestats", source.lower())

    def test_runtime_final_report_validator_links_status_and_evidence(self) -> None:
        error_report = {
            "status": "error",
            "protocol": {"decisionIsolation": self.ISOLATION},
            "device": {
                "status": "error",
                "error": {"type": "TraceError", "message": "blocked"},
            },
        }
        trace.validate_final_report(error_report)
        success_report = {
            "status": "ok",
            "protocol": {"decisionIsolation": self.ISOLATION},
            "device": {
                "status": "ok",
                "hostSerialLock": {},
                "installedPackage": {},
                "environment": {},
                "perfetto": {},
                "traceCleanupEvidence": [
                    {
                        "remoteTemporaryPath": TraceArtifactTest.REMOTE,
                        "state": "removed",
                        "removed": True,
                    },
                ],
                "session": {
                    "trace": {
                        "eligibleForGfxinfoVerdict": False,
                        "artifact": {
                            "remoteTemporaryPath": TraceArtifactTest.REMOTE,
                            "remoteTemporaryArtifactRemoved": True,
                        },
                    },
                },
            },
        }
        trace.validate_final_report(success_report)
        success_report["device"]["session"]["trace"]["artifact"][
            "remoteTemporaryArtifactRemoved"
        ] = False
        with self.assertRaisesRegex(trace.TraceError, "cleanup"):
            trace.validate_final_report(success_report)
        error_report["device"]["status"] = "ok"
        with self.assertRaisesRegex(trace.TraceError, "statuses"):
            trace.validate_final_report(error_report)

    def test_cli_requires_one_explicit_serial(self) -> None:
        parser = trace.build_parser()
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            parser.parse_args(["--apk", "app.apk", "--output", "out"])

    def test_remote_names_are_nonce_scoped_and_serial_lock_is_exclusive(self) -> None:
        first = trace.remote_trace_path("serial", "run")
        second = trace.remote_trace_path("serial", "run")
        self.assertNotEqual(first, second)
        self.assertRegex(first, trace.REMOTE_TRACE_PATTERN)
        handle, identity = trace.acquire_serial_trace_lock("unit-test-serial")
        try:
            self.assertRegex(identity["key"], r"^[0-9a-f]{16}$")
            with self.assertRaisesRegex(trace.TraceError, "holds the serial lock"):
                trace.acquire_serial_trace_lock("unit-test-serial")
        finally:
            trace.release_serial_trace_lock(handle)


if __name__ == "__main__":
    unittest.main()
