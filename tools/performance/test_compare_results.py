#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Focused regression tests for the performance result comparator."""

from __future__ import annotations

import argparse
import unittest

import compare_results


def validation_evidence(timed: str = "7", witness: str = "11") -> dict[str, object]:
    return {
        "contractVersion": 1,
        "expectedTimedResult": timed,
        "actualTimedResult": timed,
        "expectedOutcomeWitness": witness,
        "actualOutcomeWitness": witness,
    }


def source_contract(digest: str = "a" * 64) -> dict[str, object]:
    return {
        "contractVersion": 2,
        "algorithm": "SHA-256",
        "adapter": {"path": "adapter.kt", "sha256": digest},
        "harness": {"path": "harness.kt", "sha256": digest},
        "runner": {"path": "runner.gradle.kts", "sha256": digest},
        "comparator": {"path": "compare.py", "sha256": digest},
        "provenanceEmitter": {"path": "provenance.kt", "sha256": digest},
    }


def gc_comparison(baseline: list[float], candidate: list[float]) -> dict[str, object]:
    def run(collections: list[float]) -> dict[str, object]:
        return {
            "adapter": "test", "revision": "a" * 40, "dirty": False,
            "environment": {}, "profile": {"name": "standard"},
            "sourceContract": source_contract(), "_source": {"file": "test.json"},
            "scenarios": [{
                "name": "sparse_gc", "category": "harness", "description": "GC counter samples.",
                "metadata": {}, "validation": validation_evidence(),
                "samples": [{
                    "wallNanosPerOperation": 1.0, "cpuNanosPerOperation": 1.0,
                    "allocatedBytesPerOperation": 16.0, "gcNanosPerOperation": 0.0,
                    "gcCollectionsPerOperation": value,
                } for value in collections],
            }],
        }

    return compare_results.build_comparison(
        [run(baseline) for _ in range(4)], [run(candidate) for _ in range(4)],
        argparse.Namespace(
            baseline_name="base", candidate_name="candidate", semantic_contract="outcome-fingerprint",
            bootstrap_resamples=1_000, effect_threshold_percent=5.0,
        ),
    )


class CompareResultsTest(unittest.TestCase):
    def test_percentile_interpolates_and_clamps(self) -> None:
        values = [10.0, 20.0, 30.0, 40.0]

        self.assertEqual(10.0, compare_results.percentile(values, -1.0))
        self.assertEqual(25.0, compare_results.percentile(values, 0.5))
        self.assertEqual(40.0, compare_results.percentile(values, 2.0))

    def test_classification_requires_effect_and_interval(self) -> None:
        classify = compare_results.classify

        self.assertEqual("stable", classify(4.9, (4.0, 6.0), 5.0, True))
        self.assertEqual("inconclusive", classify(8.0, (-1.0, 12.0), 5.0, True))
        self.assertEqual("regression", classify(8.0, (6.0, 10.0), 5.0, True))
        self.assertEqual("improvement", classify(-8.0, (-10.0, -6.0), 5.0, True))
        self.assertEqual("insufficient-data", classify(None, None, 5.0, True))

    def test_zero_baseline_requires_evidence_of_a_positive_cost(self) -> None:
        classify = compare_results.classify_with_zero_baseline

        self.assertEqual("stable", classify(None, None, 5.0, True, 0.0, 0.0))
        self.assertEqual("insufficient-data", classify(None, None, 5.0, True, 0.0, 0.001))
        self.assertEqual("regression", classify(None, None, 5.0, True, 0.0, 0.001, (0.001, 0.001)))
        self.assertEqual("inconclusive", classify(None, (-100.0, 100.0), 5.0, True, 0.0, 0.001, (-0.001, 0.001)))

    def test_sparse_gc_samples_do_not_become_a_regression_at_zero_median(self) -> None:
        report = gc_comparison([0.0] * 6 + [0.00001] * 4, [0.0] * 5 + [0.00001] * 5)
        metric = report["scenarios"][0]["metrics"]["gcCollectionsPerOperation"]
        interval = metric["bootstrap95Absolute"]
        self.assertIsNotNone(interval)
        assert interval is not None
        self.assertLessEqual(interval[0], 0.0)
        self.assertGreaterEqual(interval[1], 0.0)
        self.assertEqual("inconclusive", metric["classification"])
        self.assertFalse(compare_results.has_regression(report))

    def test_new_consistent_cost_from_zero_still_fails_the_gate(self) -> None:
        report = gc_comparison([0.0] * 10, [0.001] * 10)
        metrics = report["scenarios"][0]["metrics"]
        self.assertEqual("stable", metrics["wallNanosPerOperation"]["classification"])
        self.assertEqual("regression", metrics["gcCollectionsPerOperation"]["classification"])
        self.assertTrue(compare_results.has_regression(report))
        # A stable wall-time summary must not hide the metric that actually fails CI.
        self.assertIn("`sparse_gc` | `gcCollectionsPerOperation`", compare_results.render_markdown(report))

    def test_semantic_metadata_blocks_invalid_comparison(self) -> None:
        compatible, differences = compare_results.compatible_metadata(
            {"seed": "1", "enemies": "120", "note": "baseline"},
            {"seed": "2", "enemies": "120", "note": "candidate"},
        )

        self.assertFalse(compatible)
        self.assertEqual(["note", "seed"], differences)

        compatible, differences = compare_results.compatible_metadata(
            {"outcomeFingerprint": "101"},
            {"outcomeFingerprint": "202"},
        )
        self.assertFalse(compatible)
        self.assertEqual(["outcomeFingerprint"], differences)

    def test_semantic_contract_controls_explicit_outcome_requirement(self) -> None:
        scenario = {
            "category": "codec",
            "description": "Encode a fixed payload.",
            "metadata": {"payloadSha256": "abc123", "expectedOutcome": "encoded"},
        }

        self.assertEqual(
            [
                "outcomeFingerprint:missing-baseline",
                "outcomeFingerprint:missing-candidate",
            ],
            compare_results.semantic_compatibility_differences(
                scenario,
                scenario,
                "outcome-fingerprint",
            ),
        )
        self.assertEqual(
            [],
            compare_results.semantic_compatibility_differences(
                scenario,
                scenario,
                "exact-metadata",
            ),
        )

    def test_hierarchical_bootstrap_is_deterministic_and_fork_aware(self) -> None:
        baseline = [[9.5, 10.0, 10.5], [9.8, 10.1, 10.2]]
        candidate = [[19.5, 20.0, 20.5], [19.8, 20.1, 20.2]]

        first = compare_results.hierarchical_bootstrap_median_delta(
            baseline,
            candidate,
            resamples=1_000,
            seed_material="scenario:wall",
        )
        second = compare_results.hierarchical_bootstrap_median_delta(
            baseline,
            candidate,
            resamples=1_000,
            seed_material="scenario:wall",
        )

        self.assertEqual(first, second)
        self.assertIsNotNone(first)
        assert first is not None
        self.assertGreater(first[0], 80.0)
        self.assertLess(first[1], 120.0)

    def test_cross_environment_checks_heap_and_profile(self) -> None:
        baseline = {
            "suiteVersion": "one",
            "profile": {"name": "standard"},
            "environment": {
                "osName": "OS",
                "osVersion": "1",
                "architecture": "arm64",
                "javaVersion": "21",
                "javaVendor": "vendor",
                "vmName": "vm",
                "availableProcessors": 8,
                "maxHeapBytes": 1024,
                "garbageCollectors": ["G1"],
                "jvmArguments": [],
            },
        }
        candidate = {
            **baseline,
            "profile": {"name": "deep"},
            "environment": {**baseline["environment"], "maxHeapBytes": 2048},
        }

        self.assertEqual(
            ["environment.maxHeapBytes differs", "benchmark profile differs"],
            compare_results.cross_environment_warnings(baseline, candidate),
        )

    def test_incomparability_includes_semantic_and_unpaired_scenarios(self) -> None:
        comparable = {
            "baselineOnlyScenarios": [],
            "candidateOnlyScenarios": [],
            "scenarios": [{"metadataCompatible": True}],
        }
        self.assertFalse(compare_results.has_incomparability(comparable))

        semantic_mismatch = {
            **comparable,
            "scenarios": [{"metadataCompatible": False}],
        }
        self.assertTrue(compare_results.has_incomparability(semantic_mismatch))

        unpaired = {
            **comparable,
            "candidateOnlyScenarios": ["new_scenario"],
        }
        self.assertTrue(compare_results.has_incomparability(unpaired))

        reordered = {
            **comparable,
            "scenarioOrderCompatible": False,
        }
        self.assertTrue(compare_results.has_incomparability(reordered))

        source_drift = {
            **comparable,
            "comparisonContractCompatible": False,
        }
        self.assertTrue(compare_results.has_incomparability(source_drift))

    def test_empty_scenarios_and_samples_are_invalid_evidence(self) -> None:
        with self.assertRaisesRegex(ValueError, "scenarios must be a non-empty array"):
            compare_results.validated_scenario_contract({"scenarios": []}, "candidate")

        scenario = {
            "name": "empty",
            "category": "test",
            "description": "No evidence.",
            "metadata": {"outcomeFingerprint": "11"},
            "validation": validation_evidence(),
            "samples": [],
        }
        with self.assertRaisesRegex(ValueError, "samples must be a non-empty array"):
            compare_results.validated_scenario_contract(
                {"scenarios": [scenario]},
                "candidate",
            )

    def test_validation_evidence_is_fail_closed_and_binds_metadata(self) -> None:
        scenario = {
            "name": "validated",
            "category": "test",
            "description": "Validated evidence.",
            "metadata": {"outcomeFingerprint": "11"},
            "validation": validation_evidence(),
            "samples": [{
                "operations": 1,
                "wallNanosPerOperation": 1.0,
                "cpuNanosPerOperation": None,
                "allocatedBytesPerOperation": None,
                "gcCollectionsPerOperation": 0.0,
                "gcNanosPerOperation": 0.0,
            }],
        }
        compare_results.validated_scenario_contract({"scenarios": [scenario]}, "candidate")

        for field, changed in (
            ("actualTimedResult", "8"),
            ("actualOutcomeWitness", "12"),
            ("expectedTimedResult", "07"),
        ):
            invalid = {**scenario, "validation": {**scenario["validation"], field: changed}}
            with self.assertRaises(ValueError, msg=field):
                compare_results.validated_scenario_contract(
                    {"scenarios": [invalid]},
                    "candidate",
                )

        invalid_metadata = {**scenario, "metadata": {"outcomeFingerprint": "12"}}
        with self.assertRaisesRegex(ValueError, "does not bind its witness"):
            compare_results.validated_scenario_contract(
                {"scenarios": [invalid_metadata]},
                "candidate",
            )

    def test_source_contract_is_strict_and_cross_side_drift_is_incomparable(self) -> None:
        baseline = {"adapter": "same", "sourceContract": source_contract()}
        candidate = {"adapter": "same", "sourceContract": source_contract()}
        self.assertEqual([], compare_results.comparison_contract_differences(baseline, candidate))

        candidate["sourceContract"] = {
            **source_contract(),
            "adapter": {"path": "adapter.kt", "sha256": "b" * 64},
        }
        self.assertEqual(
            ["sourceContract.adapter"],
            compare_results.comparison_contract_differences(baseline, candidate),
        )
        candidate["sourceContract"] = {
            **source_contract(),
            "provenanceEmitter": {"path": "provenance.kt", "sha256": "b" * 64},
        }
        self.assertEqual(
            ["sourceContract.provenanceEmitter"],
            compare_results.comparison_contract_differences(baseline, candidate),
        )
        validated = compare_results.validated_source_contract(baseline, "baseline")
        self.assertEqual("SHA-256", validated["algorithm"])

        for invalid in (
            {**source_contract(), "runner": {"path": "../runner", "sha256": "a" * 64}},
            {**source_contract(), "harness": {"path": "harness.kt", "sha256": "ABC"}},
            {key: value for key, value in source_contract().items() if key != "provenanceEmitter"},
        ):
            with self.assertRaises(ValueError):
                compare_results.validated_source_contract(
                    {"sourceContract": invalid},
                    "candidate",
                )

    def test_expected_identity_binds_raw_evidence_to_runner_protocol(self) -> None:
        runs = [
            {"fork": "1", "revision": "abc", "label": "candidate", "dirty": False},
            {"fork": "2", "revision": "abc", "label": "candidate", "dirty": False},
        ]
        expected_forks = compare_results.parse_expected_forks("1,2")
        compare_results.validate_expected_run_identity(
            runs,
            "candidate",
            expected_revision="abc",
            expected_label="candidate",
            expected_forks=expected_forks,
            require_clean=True,
        )

        for field, changed in (
            ("revision", "wrong"),
            ("label", "wrong"),
            ("dirty", True),
            ("fork", "3"),
        ):
            invalid_runs = [dict(run) for run in runs]
            invalid_runs[0][field] = changed
            with self.assertRaises(ValueError, msg=field):
                compare_results.validate_expected_run_identity(
                    invalid_runs,
                    "candidate",
                    expected_revision="abc",
                    expected_label="candidate",
                    expected_forks=expected_forks,
                    require_clean=True,
                )

    def test_expected_forks_reject_duplicates_and_invalid_values(self) -> None:
        self.assertEqual({"1", "2"}, compare_results.parse_expected_forks("01,2"))
        for value in ("", "0,1", "1,-2", "1,1"):
            with self.assertRaises(ValueError, msg=value):
                compare_results.parse_expected_forks(value)


if __name__ == "__main__":
    unittest.main()
