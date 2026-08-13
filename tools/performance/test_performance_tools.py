#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Focused failure-path tests for browser and artifact performance tooling."""

from __future__ import annotations

import pathlib
import subprocess
import tempfile
import types
import unittest
from unittest import mock

import browser_benchmark
import collect_artifacts
import compare_browser_results
import compare_build_artifacts


class BuildArtifactRenderingTest(unittest.TestCase):
    def test_new_category_and_three_repetitions_render_without_crashing(self) -> None:
        build = {
            "label": "branch",
            "revision": "abc",
            "wallSeconds": [1.0, 2.0, 3.0],
            "medianWallSeconds": 2.0,
        }
        summary = {"rawBytes": 100, "gzipBytes": 50}
        application = {
            "rawBytes": 10,
            "gzipBytes": 5,
            "declaredFunctionCount": 2,
        }
        report = {
            "baseline": {"build": build},
            "candidate": {"build": build},
            "buildWallDeltaPercent": 0.0,
            "artifacts": {
                "baselineSummary": summary,
                "candidateSummary": summary,
                "rawDeltaPercent": 0.0,
                "gzipDeltaPercent": 0.0,
                "categories": [
                    {
                        "category": "image",
                        "baseline": {"rawBytes": 0},
                        "candidate": {"rawBytes": 10},
                        "rawDeltaPercent": None,
                        "gzipDeltaPercent": None,
                    },
                ],
                "applicationWasm": {
                    "baseline": application,
                    "candidate": application,
                    "rawDeltaPercent": 0.0,
                    "gzipDeltaPercent": 0.0,
                    "declaredFunctionDeltaPercent": 0.0,
                },
                "sharedWasmHashes": [],
            },
        }

        rendered = compare_build_artifacts.render_markdown(report)

        self.assertIn("Repetition 3", rendered)
        self.assertIn("| `image` |", rendered)
        self.assertIn("new | new", rendered)

    def test_application_wasm_uses_explicit_provenance_without_source_map(self) -> None:
        application_sha256 = "b" * 64
        document = {
            "applicationWasm": {
                "matchingMethod": "sha256-and-byte-equality",
                "source": {
                    "path": "repository/revision/build/optimized/application.wasm",
                    "sha256": application_sha256,
                    "rawBytes": 50,
                },
                "distribution": {
                    "path": "application.wasm",
                    "sha256": application_sha256,
                    "rawBytes": 50,
                },
            },
            "files": [
                {
                    "path": "runtime.wasm",
                    "category": "wasm",
                    "sha256": "a" * 64,
                    "rawBytes": 100,
                    "gzipBytes": 80,
                    "wasm": {
                        "customSectionNames": [],
                        "sectionEntryCounts": {"function": 10},
                    },
                },
                {
                    "path": "application.wasm",
                    "category": "wasm",
                    "sha256": application_sha256,
                    "rawBytes": 50,
                    "gzipBytes": 30,
                    "wasm": {
                        "customSectionNames": [],
                        "sectionEntryCounts": {"function": 5},
                    },
                },
            ],
        }

        self.assertEqual("application.wasm", compare_build_artifacts.application_wasm(document)["path"])


class ApplicationWasmCollectorProvenanceTest(unittest.TestCase):
    @staticmethod
    def wasm(name: bytes = b"") -> bytes:
        header = b"\x00asm\x01\x00\x00\x00"
        if not name:
            return header
        payload = bytes([len(name)]) + name
        return header + bytes([0, len(payload)]) + payload

    @staticmethod
    def artifact(path: pathlib.Path, logical_path: str) -> tuple[dict[str, object], pathlib.Path]:
        raw_bytes, sha256 = collect_artifacts.file_identity(path)
        return (
            {
                "path": logical_path,
                "category": "wasm",
                "rawBytes": raw_bytes,
                "sha256": sha256,
                "wasm": collect_artifacts.inspect_wasm(path),
            },
            path,
        )

    def test_positive_exact_match(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            source = root / "source.wasm"
            distribution = root / "application.wasm"
            source.write_bytes(self.wasm(b"application"))
            distribution.write_bytes(source.read_bytes())

            identity, entry = collect_artifacts.resolve_application_wasm(
                source,
                [self.artifact(distribution, "application.wasm")],
            )

        self.assertEqual("application.wasm", entry["path"])
        self.assertEqual(entry["sha256"], identity["sha256"])

    def test_zero_match_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            source = root / "source.wasm"
            source.write_bytes(self.wasm(b"application"))
            with self.assertRaisesRegex(RuntimeError, "found 0"):
                collect_artifacts.resolve_application_wasm(source, [])

    def test_ambiguous_identical_matches_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            source = root / "source.wasm"
            first = root / "first.wasm"
            second = root / "second.wasm"
            source.write_bytes(self.wasm(b"application"))
            first.write_bytes(source.read_bytes())
            second.write_bytes(source.read_bytes())
            with self.assertRaisesRegex(RuntimeError, "found 2"):
                collect_artifacts.resolve_application_wasm(
                    source,
                    [self.artifact(first, "first.wasm"), self.artifact(second, "second.wasm")],
                )

    def test_one_byte_mutation_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            source = root / "source.wasm"
            distribution = root / "application.wasm"
            source.write_bytes(self.wasm(b"application-a"))
            distribution.write_bytes(self.wasm(b"application-b"))
            with self.assertRaisesRegex(RuntimeError, "found 0"):
                collect_artifacts.resolve_application_wasm(
                    source,
                    [self.artifact(distribution, "application.wasm")],
                )


class BrowserFailurePathTest(unittest.TestCase):
    def test_probe_discovery_requires_current_schema(self) -> None:
        current = {
            "schemaVersion": browser_benchmark.PROBE_SCHEMA_VERSION,
            "coldNavigation": {},
        }

        self.assertIs(current, browser_benchmark.find_probe_result({"nested": current}))
        self.assertIsNone(
            browser_benchmark.find_probe_result({"schemaVersion": 1, "coldNavigation": {}}),
        )

    def test_post_gc_retention_is_fail_closed(self) -> None:
        probe = {
            "schemaVersion": browser_benchmark.PROBE_SCHEMA_VERSION,
            "cdp": {
                "supported": True,
                "postGcSupported": False,
                "error": "HeapProfiler.collectGarbage was unavailable",
            },
            "frameMeasurement": {"intervalsMillis": [16.7]},
        }

        with self.assertRaisesRegex(ValueError, "requires successful post-GC"):
            browser_benchmark.validate_probe(probe, expected_measure_frames=1)

    def test_post_gc_metrics_are_summarized_without_replacing_natural_heap(self) -> None:
        paths = dict(browser_benchmark.SUMMARY_METRICS)

        self.assertEqual("bytes", paths["cdp.after.JSHeapUsedSize"])
        self.assertEqual("bytes", paths["cdp.postGc.JSHeapUsedSize"])
        self.assertEqual("bytes", paths["cdp.postGcHeapUsage.usedSize"])

    def test_failed_close_invalidates_otherwise_successful_fork(self) -> None:
        arguments = types.SimpleNamespace(
            url="http://127.0.0.1/",
            ready_selector="canvas",
            ready_state="attached",
            settle_millis=0,
            timeout_millis=1_000,
            frame_timeout_millis=1_000,
            warmup_frames=1,
            measure_frames=1,
            command_timeout_seconds=5.0,
            browser="default",
            viewport=(1280, 720),
        )
        completed = [
            subprocess.CompletedProcess(["pwcli", "open"], 0, "", ""),
            subprocess.CompletedProcess(["pwcli", "probe"], 0, "{}", ""),
            subprocess.CompletedProcess(["pwcli", "close"], 9, "", "close failed"),
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            with (
                mock.patch.object(browser_benchmark, "command_result", side_effect=completed),
                mock.patch.object(browser_benchmark, "extract_json_payload", return_value={}),
                mock.patch.object(browser_benchmark, "enrich_probe_result"),
                mock.patch.object(browser_benchmark, "validate_probe"),
            ):
                result = browser_benchmark.run_fork(
                    fork_index=0,
                    run_id="test",
                    arguments=arguments,
                    pwcli=root / "pwcli",
                    probe_file=root / "probe.js",
                    session_root=root / "sessions",
                )

        self.assertEqual("error", result["status"])
        self.assertFalse(result["isolation"]["browserClosedAfterFork"])
        self.assertEqual("BrowserCloseError", result["error"]["type"])

    def test_browser_report_uses_configured_effect_threshold(self) -> None:
        report = {
            "baseline": {"label": "main", "revision": "a"},
            "candidate": {"label": "candidate", "revision": "b"},
            "browserVersions": ["1"],
            "protocol": {"forks": 1, "warmupFrames": 1, "measureFrames": 1},
            "effectThresholdPercent": 7.5,
            "metrics": [],
            "diagnostics": {"baseline": {}, "candidate": {}},
        }

        self.assertIn("7.50% effect", compare_browser_results.render_markdown(report))

    def test_incomplete_browser_probe_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "schemaVersion"):
            browser_benchmark.validate_probe({"frameMeasurement": {}}, expected_measure_frames=5)

    def test_browser_comparison_rejects_mixed_heap_schemas(self) -> None:
        baseline = {
            "schemaVersion": 1,
            "protocol": {},
            "runnerEnvironment": {},
            "forks": [],
        }
        candidate = baseline | {"schemaVersion": 2}

        with self.assertRaisesRegex(ValueError, "schema versions differ"):
            compare_browser_results.compare(baseline, candidate, threshold=5.0, resamples=1)

    def test_browser_zero_baseline_new_event_is_a_regression(self) -> None:
        self.assertEqual(
            "regression",
            compare_browser_results.classify(
                "frameMeasurement.statistics.framesOver33Millis",
                point=None,
                interval=None,
                threshold=5.0,
                baseline=0.0,
                candidate=1.0,
            ),
        )
        self.assertEqual(
            "stable",
            compare_browser_results.classify(
                "frameMeasurement.statistics.framesOver33Millis",
                point=0.0,
                interval=None,
                threshold=5.0,
                baseline=0.0,
                candidate=0.0,
            ),
        )


if __name__ == "__main__":
    unittest.main()
