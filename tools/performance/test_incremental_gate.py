#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Static contract checks for incremental PR performance gating."""

from __future__ import annotations

import json
import pathlib
import unittest


PERFORMANCE_ROOT = pathlib.Path(__file__).resolve().parent
MARKER = PERFORMANCE_ROOT / "contracts" / "incremental-gate-v2.json"
RUNNER = PERFORMANCE_ROOT / "scripts" / "compare-pr-base.sh"


class IncrementalGateContractTest(unittest.TestCase):
    def test_versioned_capability_marker_declares_exact_protocol(self) -> None:
        marker = json.loads(MARKER.read_text(encoding="utf-8"))

        self.assertEqual(2, marker["schemaVersion"])
        self.assertEqual("kinetickk-incremental-performance-gate", marker["capability"])
        self.assertEqual(2, marker["rawReportSchemaVersion"])
        self.assertEqual(2, marker["sourceContractVersion"])
        self.assertEqual(1, marker["validationContractVersion"])
        self.assertEqual(
            ["adapter", "harness", "runner", "comparator", "provenanceEmitter"],
            marker["sourceRoles"],
        )
        self.assertEqual("exact-five-role-identity", marker["crossSideSourcePolicy"])
        self.assertEqual(
            ["candidate", "baseline", "baseline", "candidate"],
            marker["runOrder"],
        )
        self.assertEqual("outcome-fingerprint", marker["gameplaySemanticContract"])
        self.assertEqual("exact-metadata", marker["profileSemanticContract"])

    def test_runner_uses_marker_and_expected_raw_identity_flags(self) -> None:
        runner = RUNNER.read_text(encoding="utf-8")

        self.assertIn("CAPABILITY_MARKER=", runner)
        self.assertIn("incremental-gate-v2.json", runner)
        self.assertIn("ATTESTED_SOURCE_PATHS=", runner)
        self.assertIn("drifted_paths", runner)
        self.assertIn("exit 3", runner)
        self.assertNotIn("grep -F '\"outcomeFingerprint\"'", runner)
        for flag in (
            "--expected-baseline-revision",
            "--expected-candidate-revision",
            "--expected-baseline-label",
            "--expected-candidate-label",
            "--expected-forks",
            "--require-clean-inputs",
        ):
            self.assertIn(flag, runner)


if __name__ == "__main__":
    unittest.main()
