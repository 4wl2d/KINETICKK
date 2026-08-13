#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Unit tests for the adb-independent Android benchmark contracts and parsers."""

from __future__ import annotations

import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest import mock

import android_device_benchmark as benchmark


class DisplayParserTest(unittest.TestCase):
    def test_current_oem_display_formats_resolve_active_mode(self) -> None:
        cases = {
            120.00001: (
                'DisplayDeviceInfo{"Built-in Screen": uniqueId="local:1", 1080 x 2412, '
                "modeId 2, renderFrameRate 120.00001, defaultModeId 2, supportedModes "
                "[{id=1, width=1080, height=2412, fps=60.0}, "
                "{id=2, width=1080, height=2412, fps=120.00001}], rotation 0}"
            ),
            90.0: (
                'DisplayDeviceInfo{"Built-in Screen": uniqueId="local:0", 1080 x 2400, '
                "modeId 2, defaultModeId 3, supportedModes "
                "[{id=1, width=1080, height=2400, fps=45.0}, "
                "{id=2, width=1080, height=2400, fps=90.0}, "
                "{id=3, width=1080, height=2400, fps=60.0}], rotation 0}"
            ),
            60.000004: (
                'DisplayDeviceInfo{"Built-in Screen": uniqueId="local:2", 1080 x 2400, '
                "modeId 1, defaultModeId 1, supportedModes "
                "[{id=1, width=1080, height=2400, fps=60.000004}], rotation 0}"
            ),
        }
        for expected, payload in cases.items():
            with self.subTest(expected=expected):
                self.assertAlmostEqual(
                    expected,
                    benchmark.parse_active_refresh_rate(payload),
                    places=5,
                )

    def test_active_surface_flinger_fallback_is_supported(self) -> None:
        payload = (
            "unrelated\n"
            "mActiveSfDisplayMode=DisplayMode{id=1, width=1080, height=2400, "
            "refreshRate=90.0, group=-1}\n"
        )
        self.assertEqual(90.0, benchmark.parse_active_refresh_rate(payload))

    def test_missing_active_refresh_fails_closed(self) -> None:
        with self.assertRaisesRegex(benchmark.BenchmarkError, "could not be determined"):
            benchmark.parse_active_refresh_rate("supported refreshRate=120 but no active mode")


class DeviceEnvironmentParserTest(unittest.TestCase):
    def test_navigation_modes_are_explicit(self) -> None:
        self.assertEqual("three-button", benchmark.parse_navigation_mode("0\n")["mode"])
        self.assertEqual("gesture", benchmark.parse_navigation_mode("2\n")["mode"])
        with self.assertRaises(benchmark.BenchmarkError):
            benchmark.parse_navigation_mode("null\n")

    def test_battery_requires_power_and_core_metrics(self) -> None:
        parsed = benchmark.parse_battery(
            """AC powered: false
USB powered: true
Wireless powered: false
status: 5
level: 100
temperature: 301
""",
        )
        self.assertEqual(30.1, parsed["temperatureCelsius"])
        self.assertTrue(parsed["usbPowered"])
        with self.assertRaisesRegex(benchmark.BenchmarkError, "battery metrics missing"):
            benchmark.parse_battery("level: 100\n")

    def test_thermal_deduplicates_oem_sensor_rows(self) -> None:
        parsed = benchmark.parse_thermal(
            """Thermal Status: 0
Temperature{mValue=38.155, mType=3, mName=SKIN, mStatus=0}
Temperature{mValue=38.155, mType=3, mName=SKIN, mStatus=0}
Temperature{mValue=30.1, mType=2, mName=BATTERY, mStatus=0}
""",
        )
        self.assertEqual(0, parsed["statusCode"])
        self.assertEqual(2, len(parsed["temperatures"]))
        self.assertEqual(38.155, parsed["maximumSkinTemperatureCelsius"])

    def test_xiaomi_style_status_without_sensor_rows_is_valid(self) -> None:
        parsed = benchmark.parse_thermal("Thermal Status: 0\n")
        self.assertEqual([], parsed["temperatures"])
        self.assertIsNone(parsed["maximumSkinTemperatureCelsius"])


class StartupAndFrameParserTest(unittest.TestCase):
    def test_cold_start_metrics_require_am_status_and_times(self) -> None:
        parsed = benchmark.parse_startup(
            """Starting: Intent { cmp=com.example/.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.example/.MainActivity
ThisTime: 401
TotalTime: 401
WaitTime: 409
Complete
""",
        )
        self.assertEqual(401, parsed["totalTimeMillis"])
        self.assertEqual("COLD", parsed["launchState"])
        with self.assertRaisesRegex(benchmark.BenchmarkError, "metrics missing"):
            benchmark.parse_startup("Status: ok\n")

    def test_refresh_aware_frame_metrics_filter_flags_and_deduplicate(self) -> None:
        payload = """Window: com.example/.MainActivity
---PROFILEDATA---
Flags,IntendedVsync,SyncQueued,FrameDeadline,FrameCompleted,
0,1000000000,1004000000,1016666667,1010000000,
0,2000000000,2005000000,2016666667,2020000000,
0,3000000000,3005000000,3016666667,3040000000,
1,4000000000,4005000000,4016666667,4020000000,
---PROFILEDATA---
Window: duplicate
---PROFILEDATA---
Flags,IntendedVsync,SyncQueued,FrameDeadline,FrameCompleted,
0,1000000000,1004000000,1016666667,1010000000,
---PROFILEDATA---
"""
        parsed = benchmark.analyze_framestats(
            payload,
            60.0,
            minimum_frames=3,
            api_level=33,
        )
        self.assertEqual(3, parsed["validFrameCount"])
        self.assertEqual(1, parsed["excludedFlaggedRowCount"])
        self.assertAlmostEqual(
            2 / 3,
            parsed["singleRefreshCompletionOverrunRate"],
        )
        self.assertAlmostEqual(2 / 3, parsed["terminalJank"]["deadlineMissRate"])
        self.assertEqual(20.0, parsed["frameCompletionLatencyMillis"]["median"])
        self.assertEqual(40.0, parsed["frameCompletionLatencyMillis"]["maximum"])
        self.assertNotIn("estimatedMissedVsyncCount", parsed)
        self.assertNotIn("refreshBudgetJankRate", parsed)

    def test_missing_or_too_small_frame_window_fails_closed(self) -> None:
        with self.assertRaisesRegex(benchmark.BenchmarkError, "PROFILEDATA"):
            benchmark.analyze_framestats(
                "no stats",
                60.0,
                minimum_frames=1,
                api_level=30,
            )
        one_frame = """---PROFILEDATA---
Flags,IntendedVsync,SyncQueued,FrameCompleted,
0,1000000000,1001000000,1010000000,
---PROFILEDATA---
"""
        with self.assertRaisesRegex(benchmark.BenchmarkError, "at least 2"):
            benchmark.analyze_framestats(
                one_frame,
                60.0,
                minimum_frames=2,
                api_level=30,
            )

    def test_api30_terminal_deadline_jank_and_present_cadence_are_unavailable(self) -> None:
        payload = """---PROFILEDATA---
Flags,IntendedVsync,SyncQueued,FrameCompleted,
0,1000000000,1001000000,1010000000,
0,1016666667,1017666667,1026666667,
0,1033333334,1034333334,1043333334,
---PROFILEDATA---
"""
        parsed = benchmark.analyze_framestats(
            payload,
            60.0,
            minimum_frames=3,
            api_level=30,
        )
        self.assertFalse(parsed["terminalJank"]["available"])
        self.assertIsNone(parsed["terminalJank"]["deadlineMissRate"])
        self.assertIsNone(parsed["terminalJank"]["completionDeadlineOverrunMillis"])
        self.assertTrue(
            all(frame["frameDeadlineNanos"] is None for frame in parsed["rawFrames"]),
        )
        self.assertFalse(parsed["displayPresentCadence"]["available"])
        self.assertEqual(
            "column-absent",
            parsed["displayPresentCadence"]["availabilityState"],
        )
        self.assertIsNone(parsed["excludedUnpresentedTailRowCount"])
        self.assertAlmostEqual(
            60.0,
            parsed["intendedVsyncCadence"]["producedFramesPerSecond"],
            places=4,
        )

    def test_modern_terminal_cadence_uses_intended_and_present_timestamps(self) -> None:
        payload = """---PROFILEDATA---
Flags,IntendedVsync,SyncQueued,FrameDeadline,FrameCompleted,DisplayPresentTime,
0,1000000000,1001000000,1016666667,1020000000,1021000000,
0,1016666667,1017666667,1033333334,1036000000,1037666667,
0,1050000001,1051000001,1066666668,1070000001,1071000001,
---PROFILEDATA---
"""
        parsed = benchmark.analyze_framestats(
            payload,
            60.0,
            minimum_frames=3,
            api_level=33,
        )
        intended = parsed["intendedVsyncCadence"]
        presented = parsed["displayPresentCadence"]
        self.assertEqual(1, intended["cadenceMissedVsyncCount"])
        self.assertAlmostEqual(1 / 3, intended["cadenceMissedVsyncRate"])
        self.assertAlmostEqual(40.0, intended["producedFramesPerSecond"], places=3)
        self.assertTrue(presented["available"])
        self.assertEqual("available", presented["availabilityState"])
        self.assertAlmostEqual(40.0, presented["presentedFramesPerSecond"], places=3)
        self.assertEqual(1, presented["cadenceMissedVsyncCount"])
        self.assertAlmostEqual(1 / 3, presented["cadenceMissedVsyncRate"])
        self.assertEqual("FrameCompleted-minus-FrameDeadline", parsed["terminalJank"]["basis"])

    def test_modern_unpresented_terminal_tail_is_explicitly_excluded(self) -> None:
        payload = """---PROFILEDATA---
Flags,IntendedVsync,SyncQueued,FrameDeadline,FrameCompleted,DisplayPresentTime,
0,1000000000,1001000000,1016666667,1020000000,1021000000,
0,1016666667,1017666667,1033333334,1036000000,1037666667,
0,1033333334,1034333334,1050000001,1053000000,1000000000,
---PROFILEDATA---
"""
        parsed = benchmark.analyze_framestats(
            payload,
            60.0,
            minimum_frames=3,
            api_level=33,
        )
        self.assertEqual(1, parsed["excludedUnpresentedTailRowCount"])
        self.assertTrue(parsed["displayPresentCadence"]["available"])
        self.assertEqual(
            "available",
            parsed["displayPresentCadence"]["availabilityState"],
        )

    def test_api31_all_zero_display_present_column_is_platform_unavailable(self) -> None:
        payload = (
            pathlib.Path(__file__)
            .with_name("android_gfxinfo_api31_zero_display_present.fixture.txt")
            .read_text(encoding="utf-8")
        )
        parsed = benchmark.analyze_framestats(
            payload,
            60.0,
            minimum_frames=3,
            api_level=31,
        )
        presented = parsed["displayPresentCadence"]
        self.assertTrue(parsed["terminalJank"]["available"])
        self.assertFalse(presented["available"])
        self.assertEqual("column-present-all-unset", presented["availabilityState"])
        self.assertIn("zero/unset", presented["unavailableReason"])
        self.assertIsNone(presented["intervalMillis"])
        self.assertIsNone(presented["presentedFramesPerSecond"])
        self.assertIsNone(presented["cadenceMissedVsyncCount"])
        self.assertIsNone(presented["cadenceMissedVsyncRate"])
        self.assertIsNone(parsed["excludedUnpresentedTailRowCount"])

    def test_modern_internal_missing_present_timestamp_fails_closed(self) -> None:
        payload = """---PROFILEDATA---
Flags,IntendedVsync,SyncQueued,FrameDeadline,FrameCompleted,DisplayPresentTime,
0,1000000000,1001000000,1016666667,1020000000,1021000000,
0,1016666667,1017666667,1033333334,1036000000,1000000000,
0,1033333334,1034333334,1050000001,1053000000,1054333334,
---PROFILEDATA---
"""
        with self.assertRaisesRegex(benchmark.BenchmarkError, "missing inside"):
            benchmark.analyze_framestats(
                payload,
                60.0,
                minimum_frames=3,
                api_level=33,
            )

    def test_malformed_framestats_row_fails_closed(self) -> None:
        payload = """---PROFILEDATA---
Flags,IntendedVsync,SyncQueued,FrameCompleted,
0,1000000000,1001000000,1020000000,
0,not-a-timestamp,1017666667,1036000000,
0,1033333334,1034333334,1053000000,
---PROFILEDATA---
"""
        with self.assertRaisesRegex(benchmark.BenchmarkError, "malformed numeric row"):
            benchmark.analyze_framestats(
                payload,
                60.0,
                minimum_frames=2,
                api_level=30,
            )

    def test_api31_missing_frame_deadline_fails_closed(self) -> None:
        payload = """---PROFILEDATA---
Flags,IntendedVsync,SyncQueued,FrameCompleted,
0,1000000000,1001000000,1020000000,
0,1016666667,1017666667,1036000000,
---PROFILEDATA---
"""
        with self.assertRaisesRegex(benchmark.BenchmarkError, "FrameDeadline"):
            benchmark.analyze_framestats(
                payload,
                60.0,
                minimum_frames=2,
                api_level=31,
            )

    def test_missing_ui_submission_timestamp_fails_closed(self) -> None:
        payload = """---PROFILEDATA---
Flags,IntendedVsync,FrameCompleted,
0,1000000000,1020000000,
0,1016666667,1036000000,
---PROFILEDATA---
"""
        with self.assertRaisesRegex(benchmark.BenchmarkError, "SyncQueued"):
            benchmark.analyze_framestats(
                payload,
                60.0,
                minimum_frames=2,
                api_level=30,
            )

    def test_full_flow_gfxinfo_summary_is_parsed_separately(self) -> None:
        payload = """Applications Graphics Acceleration Info:
Stats since: 1000ns
Total frames rendered: 100
Janky frames: 25 (25.00%)
50th percentile: 8ms
90th percentile: 12ms
95th percentile: 14ms
99th percentile: 20ms
Number Missed Vsync: 2
Number High input latency: 3
Number Slow UI thread: 4
Number Slow bitmap uploads: 0
Number Slow issue draw commands: 5
Number Frame deadline missed: 6
HISTOGRAM: 5ms=10 6ms=20
50th gpu percentile: 5ms
90th gpu percentile: 7ms
95th gpu percentile: 8ms
99th gpu percentile: 9ms
GPU HISTOGRAM: 4ms=50 5ms=50
Pipeline=Skia
"""
        parsed = benchmark.parse_gfxinfo_summary(payload)
        self.assertEqual("full-measured-flow-since-gfxinfo-reset", parsed["measurementScope"])
        self.assertEqual(100, parsed["totalFramesRendered"])
        self.assertEqual(25, parsed["platformJankyFrames"])
        self.assertEqual(0.25, parsed["platformJankyFrameRate"])
        self.assertEqual({"p50": 8, "p90": 12, "p95": 14, "p99": 20}, parsed["frameTimePercentilesMillis"])
        self.assertEqual(6, parsed["counters"]["frameDeadlineMissed"])

    def test_full_flow_gfxinfo_summary_count_rate_mismatch_fails_closed(self) -> None:
        payload = """Stats since: 1000ns
Total frames rendered: 100
Janky frames: 25 (20.00%)
50th percentile: 8ms
90th percentile: 12ms
95th percentile: 14ms
99th percentile: 20ms
Pipeline=Skia
"""
        with self.assertRaisesRegex(benchmark.BenchmarkError, "disagrees"):
            benchmark.parse_gfxinfo_summary(payload)

    def test_meminfo_requires_every_decision_metric(self) -> None:
        parsed = benchmark.parse_meminfo(
            """ App Summary
           Java Heap:    53920
         Native Heap:    62048
            Graphics:    36124
           TOTAL PSS:   342332            TOTAL RSS:   442512       TOTAL SWAP PSS:   109781
               Views:     1869         ViewRootImpl:       11
         AppContexts:       54           Activities:        1
""",
        )
        self.assertEqual(342332, parsed["totalPssKibibytes"])
        self.assertEqual(1, parsed["activityCount"])
        with self.assertRaisesRegex(benchmark.BenchmarkError, "metrics missing"):
            benchmark.parse_meminfo("TOTAL PSS: 1\n")


class SelectorFlowTest(unittest.TestCase):
    XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node package="com.example" resource-id="kinetickk.home" text="" class="android.view.View" content-desc="KINETICKK home" clickable="false" enabled="true" bounds="[0,0][1080,2200]" />
  <node package="com.example" resource-id="kinetickk.home.start" text="" class="android.view.View" content-desc="Start run" clickable="true" enabled="true" bounds="[300,900][780,1100]" />
</hierarchy>"""

    def test_ordered_any_of_prefers_resource_id(self) -> None:
        selector = {
            "anyOf": [
                {"resourceId": "kinetickk.home.start"},
                {"contentDescription": "Start run"},
            ],
        }
        candidates = benchmark.selector_alternatives(selector)
        primary = benchmark.selector_matches(self.XML, candidates[0], "com.example")
        self.assertEqual([[540, 1000]], [item["center"] for item in primary])

    def test_selector_never_matches_another_package(self) -> None:
        self.assertEqual(
            [],
            benchmark.selector_matches(
                self.XML,
                {"resourceId": "kinetickk.home.start"},
                "not.the.app",
            ),
        )

    def test_tracked_flows_are_semantic_and_have_expected_reuse_boundaries(self) -> None:
        primary = benchmark.load_flow(
            pathlib.Path(__file__).with_name("android_gameplay_flow.json"),
        )
        telemetry = benchmark.load_flow(
            pathlib.Path(__file__).with_name("android_gameplay_telemetry_flow.json"),
        )
        self.assertEqual(3, primary["measurementStartStep"])
        self.assertEqual(3, telemetry["measurementStartStep"])
        self.assertEqual("sleep", primary["steps"][-1]["action"])
        self.assertEqual("sleep", telemetry["steps"][-1]["action"])
        self.assertEqual(
            [4, 5, 7],
            [
                index
                for index, step in enumerate(primary["steps"])
                if step.get("reusePreviousHierarchy", False)
            ],
        )
        self.assertEqual(
            [4, 5, 6, 8],
            [
                index
                for index, step in enumerate(telemetry["steps"])
                if step.get("reusePreviousHierarchy", False)
            ],
        )
        self.assertNotIn("kinetickk.gameplay.performance", json.dumps(primary))
        self.assertIn("kinetickk.gameplay.performance", json.dumps(telemetry))
        for flow in (primary, telemetry):
            for step in flow["steps"]:
                self.assertNotIn('"x"', json.dumps(step))
                if step["action"] != "sleep":
                    self.assertIn("selector", step)

    def test_coordinate_or_unknown_flow_keys_are_rejected(self) -> None:
        invalid = {
            "schemaVersion": 1,
            "name": "bad",
            "steps": [{"action": "tap", "selector": {"x": 10, "y": 20}}],
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "flow.json"
            path.write_text(json.dumps(invalid), encoding="utf-8")
            with self.assertRaisesRegex(benchmark.BenchmarkError, "unsupported selector"):
                benchmark.load_flow(path)

    def test_reuse_is_rejected_on_wait_or_before_phase_capture(self) -> None:
        invalid_wait = {
            "schemaVersion": 1,
            "name": "bad-wait",
            "measurementStartStep": 1,
            "steps": [
                {
                    "action": "wait",
                    "selector": {"text": "Home"},
                    "reusePreviousHierarchy": True,
                },
            ],
        }
        invalid_first_measurement = {
            "schemaVersion": 1,
            "name": "bad-phase",
            "measurementStartStep": 1,
            "steps": [
                {"action": "wait", "selector": {"text": "Home"}},
                {
                    "action": "tap",
                    "selector": {"text": "Dash"},
                    "reusePreviousHierarchy": True,
                },
            ],
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "flow.json"
            path.write_text(json.dumps(invalid_wait), encoding="utf-8")
            with self.assertRaisesRegex(benchmark.BenchmarkError, "unknown keys"):
                benchmark.load_flow(path)
            path.write_text(json.dumps(invalid_first_measurement), encoding="utf-8")
            with self.assertRaisesRegex(benchmark.BenchmarkError, "before measurement"):
                benchmark.load_flow(path)

    def test_execute_flow_reuses_one_phase_local_hierarchy_with_provenance(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node package="com.example" text="Perf" content-desc="" class="android.view.View" resource-id="perf" bounds="[0,0][20,20]" />
  <node package="com.example" text="Dash" content-desc="" class="android.view.View" resource-id="dash" bounds="[20,0][40,20]" />
  <node package="com.example" text="Brake" content-desc="" class="android.view.View" resource-id="brake" bounds="[40,0][60,20]" />
</hierarchy>"""
        flow = {
            "steps": [
                {
                    "action": "tap",
                    "selector": {"text": "Perf"},
                    "timeoutMillis": 1000,
                    "repeat": 1,
                    "intervalMillis": 0,
                    "reusePreviousHierarchy": False,
                },
                {
                    "action": "tap",
                    "selector": {"text": "Dash"},
                    "timeoutMillis": 1000,
                    "repeat": 1,
                    "intervalMillis": 0,
                    "reusePreviousHierarchy": True,
                },
                {
                    "action": "longPress",
                    "selector": {"text": "Brake"},
                    "timeoutMillis": 1000,
                    "durationMillis": 100,
                    "repeat": 1,
                    "intervalMillis": 0,
                    "reusePreviousHierarchy": True,
                },
            ],
        }
        perf_match = benchmark.selector_matches(xml, {"text": "Perf"}, "com.example")[0]
        target = mock.Mock()
        with (
            tempfile.TemporaryDirectory() as temporary,
            mock.patch.object(
                benchmark,
                "wait_for_selector",
                return_value=(perf_match, xml, 1, {"text": "Perf"}),
            ) as wait,
        ):
            events = benchmark.execute_flow_steps(
                target,
                flow,
                "com.example",
                pathlib.Path(temporary),
                start_index=0,
                end_index=3,
                phase="measurement",
            )
        self.assertEqual(1, wait.call_count)
        self.assertEqual(
            ["fresh-capture", "reused-previous-capture", "reused-previous-capture"],
            [event["hierarchy"]["source"] for event in events],
        )
        self.assertEqual([0, 0, 0], [event["hierarchy"]["captureStepIndex"] for event in events])
        self.assertEqual(1, events[0]["selectorAttempts"])
        self.assertEqual(0, events[1]["selectorAttempts"])
        self.assertEqual(
            events[0]["hierarchy"]["sha256"],
            events[2]["hierarchy"]["sha256"],
        )

    def test_reused_hierarchy_missing_selector_fails_without_fresh_dump(self) -> None:
        flow = {
            "steps": [
                {
                    "action": "tap",
                    "selector": {"text": "Start run"},
                    "timeoutMillis": 1000,
                    "repeat": 1,
                    "intervalMillis": 0,
                    "reusePreviousHierarchy": False,
                },
                {
                    "action": "tap",
                    "selector": {"text": "Missing"},
                    "timeoutMillis": 1000,
                    "repeat": 1,
                    "intervalMillis": 0,
                    "reusePreviousHierarchy": True,
                },
            ],
        }
        start_match = benchmark.selector_matches(
            self.XML,
            {"text": ""},
            "com.example",
        )[0]
        target = mock.Mock()
        with (
            tempfile.TemporaryDirectory() as temporary,
            mock.patch.object(
                benchmark,
                "wait_for_selector",
                return_value=(start_match, self.XML, 1, {"text": "Start run"}),
            ) as wait,
        ):
            with self.assertRaisesRegex(benchmark.BenchmarkError, "absent from"):
                benchmark.execute_flow_steps(
                    target,
                    flow,
                    "com.example",
                    pathlib.Path(temporary),
                    start_index=0,
                    end_index=2,
                    phase="setup",
                )
        self.assertEqual(1, wait.call_count)

    def test_wait_for_selector_retries_transient_hierarchy_error(self) -> None:
        with (
            mock.patch.object(
                benchmark,
                "dump_ui",
                side_effect=[benchmark.BenchmarkError("transient OEM failure"), self.XML],
            ) as dump,
            mock.patch.object(benchmark.time, "sleep"),
        ):
            match, _xml, attempts, resolved = benchmark.wait_for_selector(
                mock.Mock(),
                {"resourceId": "kinetickk.home.start"},
                "com.example",
                1000,
            )
        self.assertEqual(2, attempts)
        self.assertEqual(2, dump.call_count)
        self.assertEqual([540, 1000], match["center"])
        self.assertEqual({"resourceId": "kinetickk.home.start"}, resolved)

    def test_wait_timeout_preserves_last_transient_hierarchy_error(self) -> None:
        with (
            mock.patch.object(
                benchmark,
                "dump_ui",
                side_effect=benchmark.BenchmarkError("MIUI hierarchy transport failed"),
            ),
            mock.patch.object(
                benchmark.time,
                "monotonic",
                side_effect=[0.0, 0.0, 0.002, 0.002],
            ),
            mock.patch.object(benchmark.time, "sleep"),
        ):
            with self.assertRaisesRegex(
                benchmark.BenchmarkError,
                "last hierarchy error=MIUI hierarchy transport failed",
            ):
                benchmark.wait_for_selector(
                    mock.Mock(),
                    {"resourceId": "kinetickk.home"},
                    "com.example",
                    1,
                )

    def test_noisy_miui_prefix_still_yields_valid_xml(self) -> None:
        noisy = (
            "java.io.FileNotFoundException: theme_compatibility.xml\n"
            + self.XML
            + "UI hierchary dumped to: /dev/tty\n"
        )
        self.assertEqual(self.XML, benchmark.extract_ui_xml(noisy))

    def test_dump_ui_retries_compressed_without_using_coordinates(self) -> None:
        target = mock.Mock()
        target.run.side_effect = [
            subprocess.CompletedProcess(["adb"], 0, "MIUI warning without XML", ""),
            subprocess.CompletedProcess(
                ["adb"],
                0,
                "MIUI warning\n" + self.XML + "dump complete\n",
                "",
            ),
        ]

        self.assertEqual(self.XML, benchmark.dump_ui(target))
        self.assertEqual(2, target.run.call_count)
        fallback_command = target.run.call_args_list[1].args[0]
        self.assertEqual(
            ["exec-out", "uiautomator", "dump", "--compressed", "/dev/tty"],
            fallback_command,
        )

    def test_play_protect_scan_consent_is_a_hard_blocker(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node package="com.android.vending" text="App scan recommended" content-desc="" bounds="[0,0][10,10]" />
  <node package="com.android.vending" text="Scan app" content-desc="" bounds="[0,10][10,20]" />
  <node package="com.android.vending" text="Don't install app" content-desc="" bounds="[0,20][10,30]" />
</hierarchy>"""
        blocker = benchmark.known_blocking_ui(xml, "com.example")
        self.assertEqual("play-protect-scan-consent", blocker["kind"])
        self.assertIn("will not upload", blocker["message"])

    def test_miui_privacy_consent_is_a_hard_blocker(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node package="com.miui.packageinstaller" text="Privacy Policy" content-desc="" bounds="[0,0][10,10]" />
</hierarchy>"""
        blocker = benchmark.known_blocking_ui(xml, "com.example")
        self.assertEqual("miui-privacy-consent", blocker["kind"])
        self.assertIn("will not accept", blocker["message"])

    def test_play_protect_blocks_before_mixed_app_selector_can_resolve(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node package="com.example" resource-id="kinetickk.home.start" text="" content-desc="Start run" bounds="[100,100][300,200]" />
  <node package="com.android.vending" text="App scan recommended" content-desc="" bounds="[0,0][400,300]" />
  <node package="com.android.vending" text="Scan app" content-desc="" bounds="[100,220][300,280]" />
</hierarchy>"""

        with self.assertRaisesRegex(benchmark.BenchmarkError, "will not upload"):
            benchmark.resolve_selector_in_hierarchy(
                xml,
                {"resourceId": "kinetickk.home.start"},
                "com.example",
            )

    def test_miui_consent_blocks_before_mixed_app_selector_can_resolve(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node package="com.example" resource-id="kinetickk.home.start" text="" content-desc="Start run" bounds="[100,100][300,200]" />
  <node package="com.miui.packageinstaller" text="User Agreement" content-desc="" bounds="[0,0][400,300]" />
</hierarchy>"""

        with self.assertRaisesRegex(benchmark.BenchmarkError, "will not accept"):
            benchmark.resolve_selector_in_hierarchy(
                xml,
                {"resourceId": "kinetickk.home.start"},
                "com.example",
            )

    def test_expected_app_hierarchy_wins_over_foreign_overlay_text(self) -> None:
        xml = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node package="com.example" text="" content-desc="Home" bounds="[0,0][10,10]" />
  <node package="com.android.vending" text="Scan app" content-desc="" bounds="[0,10][10,20]" />
</hierarchy>"""
        self.assertIsNone(benchmark.known_blocking_ui(xml, "com.example"))


class ReportContractTest(unittest.TestCase):
    def test_aapt2_manifest_is_authoritative_when_dumpsys_omits_profileable(self) -> None:
        manifest = """N: android=http://schemas.android.com/apk/res/android
  E: manifest
    A: package="com.example" (Raw: "com.example")
      E: uses-sdk
        A: http://schemas.android.com/apk/res/android:minSdkVersion(0x0101020c)=26
        A: http://schemas.android.com/apk/res/android:targetSdkVersion(0x01010270)=36
      E: application
        A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=true
          E: profileable
            A: http://schemas.android.com/apk/res/android:shell(0x01010594)=true
"""
        parsed = benchmark.parse_apk_manifest_tree(manifest)
        package_dump = """versionCode=1 minSdk=26 targetSdk=36
versionName=0.1.0
pkgFlags=[ DEBUGGABLE HAS_CODE ]
"""
        runtime = benchmark.parse_package_dump(package_dump)
        provenance = benchmark.profileable_provenance(
            parsed["profileableByShellDeclared"],
            runtime["profileableByShellObserved"],
        )
        self.assertTrue(parsed["profileableByShellDeclared"])
        self.assertIsNone(runtime["profileableByShellObserved"])
        self.assertTrue(provenance["effective"])
        self.assertEqual("apk-manifest", provenance["source"])

    def test_explicit_profileable_conflict_fails_closed(self) -> None:
        with self.assertRaisesRegex(benchmark.BenchmarkError, "provenance conflicts"):
            benchmark.profileable_provenance(True, False)

    def test_repository_provenance_paths_are_portable(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary) / "workspace"
            flow = repository / "tools/performance/flow.json"
            apk = repository / "app/build/app.apk"
            adb = pathlib.Path(temporary) / "sdk/platform-tools/adb"
            flow.parent.mkdir(parents=True)
            apk.parent.mkdir(parents=True)
            adb.parent.mkdir(parents=True)
            flow.write_text("{}", encoding="utf-8")
            apk.write_bytes(b"apk")
            adb.write_bytes(b"adb")
            with mock.patch.object(
                benchmark,
                "command_text",
                return_value=subprocess.CompletedProcess(
                    [str(adb), "version"],
                    0,
                    f"Android Debug Bridge version 1.0.41\nInstalled as {adb}\n",
                    "",
                ),
            ):
                provenance = benchmark.tool_provenance(adb, flow, repository, None)
            document = {
                "apk": {
                    "path": benchmark.logical_artifact_path(apk, repository, "external-apk"),
                },
                "tool": provenance,
            }
            serialized = json.dumps(document)
            self.assertEqual("app/build/app.apk", document["apk"]["path"])
            self.assertEqual("tools/performance/flow.json", provenance["flowPath"])
            self.assertEqual("android-sdk/platform-tools/adb", provenance["adbBinary"]["logicalPath"])
            self.assertNotIn(str(repository), serialized)
            self.assertNotIn(str(adb), serialized)

    def test_existing_play_protect_prompt_stops_before_adb_install(self) -> None:
        target = mock.Mock()
        with (
            tempfile.TemporaryDirectory() as temporary,
            mock.patch.object(
                benchmark,
                "inspect_known_blocking_ui",
                return_value={
                    "kind": "play-protect-scan-consent",
                    "message": "Play Protect consent is required; no action taken.",
                },
            ),
        ):
            with self.assertRaisesRegex(benchmark.BenchmarkError, "no action taken"):
                benchmark.install_and_verify_apk(
                    target,
                    pathlib.Path(temporary) / "app.apk",
                    "a" * 64,
                    "com.example",
                    skip_install=False,
                    directory=pathlib.Path(temporary),
                )
        target.run.assert_not_called()

    def test_schema_is_parseable_and_pins_suite(self) -> None:
        schema = json.loads(
            pathlib.Path(__file__)
            .with_name("android_device_benchmark.schema.json")
            .read_text(encoding="utf-8"),
        )
        self.assertEqual(1, schema["properties"]["schemaVersion"]["const"])
        self.assertEqual(
            benchmark.SUITE_NAME,
            schema["properties"]["suite"]["const"],
        )
        terminal_required = set(schema["$defs"]["terminalFrameRing"]["required"])
        self.assertIn("frameCompletionLatencyMillis", terminal_required)
        self.assertIn("uiSubmissionLatencyMillis", terminal_required)
        self.assertIn("singleRefreshCompletionOverrunRate", terminal_required)
        self.assertIn("intendedVsyncCadence", terminal_required)
        self.assertIn("displayPresentCadence", terminal_required)
        self.assertIn("terminalJank", terminal_required)
        self.assertNotIn("frameDurationMillis", terminal_required)
        self.assertIn(
            "availabilityState",
            schema["$defs"]["terminalDisplayCadence"]["required"],
        )
        self.assertIn("gfxinfo", schema["$defs"]["fork"]["properties"])
        self.assertNotIn("frames", schema["$defs"]["fork"]["properties"])
        flow_schema = json.loads(
            pathlib.Path(__file__)
            .with_name("android_gameplay_flow.schema.json")
            .read_text(encoding="utf-8"),
        )
        self.assertEqual(1, flow_schema["properties"]["schemaVersion"]["const"])

    def test_summary_does_not_compare_raw_frame_counts_across_devices(self) -> None:
        def device(serial: str, model: str, refresh: float) -> dict:
            return {
                "serial": serial,
                "status": "ok",
                "environment": {
                    "manufacturer": "Vendor",
                    "model": model,
                    "apiLevel": 33,
                    "androidRelease": "13",
                    "display": {"effectiveWidthDp": 360, "effectiveHeightDp": 800},
                    "navigation": {"mode": "gesture"},
                    "battery": {"levelPercent": 100},
                    "thermal": {"statusCode": 0},
                },
                "forks": [],
                "aggregate": {
                    "refreshRateHz": refresh,
                    "frameBudgetMillis": 1000 / refresh,
                    "startupTotalTimeMillis": {"median": 100},
                    "fullFlowGfxinfoSummary": {
                        "totalFramesRendered": 3000,
                        "platformJankyFrameRate": 0.02,
                    },
                    "terminalFrameCompletionLatencyMillis": {
                        "median": 8,
                        "p95": 12,
                        "p99": 18,
                    },
                    "terminalSingleRefreshCompletionOverrunRate": 0.01,
                    "terminalJank": {
                        "available": True,
                        "deadlineMissRate": 0.005,
                    },
                    "terminalIntendedVsyncCadence": {
                        "producedFramesPerSecond": {"median": refresh},
                        "cadenceMissedVsyncRate": 0.01,
                    },
                    "terminalDisplayPresentCadence": {
                        "available": True,
                        "presentedFramesPerSecond": {"median": refresh - 1},
                    },
                    "totalPssKibibytes": {"median": 100 * 1024},
                    "terminalRawFrameSampleCount": 999,
                    "forkCount": 3,
                },
            }

        report = {
            "status": "ok",
            "source": {"label": "branch", "revision": "a" * 40, "dirty": True},
            "apk": {"sha256": "b" * 64},
            "protocol": {
                "forks": 3,
                "minimumFramesPerFork": 30,
                "flow": {"name": "flow"},
            },
            "devices": [device("one", "One", 120.0), device("two", "Two", 60.0)],
        }
        markdown = benchmark.render_markdown(report)
        summary = markdown.split("## Per-device evidence", maxsplit=1)[0]
        self.assertNotIn("999", summary)
        self.assertIn("never compared across devices", summary)


if __name__ == "__main__":
    unittest.main()
