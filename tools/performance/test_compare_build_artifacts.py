#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Focused contract tests for build and artifact performance comparisons."""

from __future__ import annotations

import copy
import datetime as dt
import json
import pathlib
import tempfile
import unittest

import compare_build_artifacts


COMMON_BUILD_ENVIRONMENT = {
    "os": "TestOS-1.0-x86_64",
    "architecture": "x86_64",
    "pythonVersion": "3.12.0",
    "processorCount": 8,
}
COMMON_BUILD_ENVIRONMENT_V2 = {
    **COMMON_BUILD_ENVIRONMENT,
    "javaVersion": (
        "Launcher JVM: 17.0.20 (Eclipse Adoptium 17.0.20+8)\n"
        "Daemon JVM: /opt/java/openjdk (no JDK specified, using current Java home)"
    ),
    "gradleDistributionSha256": "d" * 64,
    "kotlinVersion": "2.3.20",
    "nodeVersion": "25.0.0",
    "binaryenVersion": "125",
}
COMMON_SELECTION = {
    "includes": [],
    "excludes": [],
    "symlinksFollowed": False,
}
COMMON_COMPRESSION = {
    "algorithm": "gzip",
    "level": 9,
    "implementation": "Python stdlib zlib",
    "aggregation": "sum of independently compressed file sizes",
    "brotliAvailableInStandardLibrary": False,
}
COMMON_ARTIFACT_ENVIRONMENT = {
    "platform": COMMON_BUILD_ENVIRONMENT["os"],
    "pythonVersion": COMMON_BUILD_ENVIRONMENT["pythonVersion"],
    "zlibVersion": "1.2.13",
}


def utc_timestamp(seconds: float) -> str:
    instant = dt.datetime(2026, 1, 1, tzinfo=dt.timezone.utc) + dt.timedelta(seconds=seconds)
    return instant.isoformat().replace("+00:00", "Z")


def build_document(
    *,
    role: str,
    fork: int,
    start_seconds: float,
    working_directory: pathlib.Path,
    dirty: bool = False,
    environment: dict[str, object] | None = None,
    command: list[str] | None = None,
    utilization_field: str = "processTreeCpuUtilizationPercent",
    schema_version: int = 1,
    repetitions: int = 1,
    warmups: int = 0,
) -> dict[str, object]:
    candidate = role == "candidate"
    revision = (
        ("c" if candidate else "b") * 40
        if schema_version == 2
        else ("candidate-sha" if candidate else "baseline-sha")
    )
    source_tree_sha256 = ("e" if candidate else "f") * 64

    def sample(index: int, event_start: float) -> dict[str, object]:
        return {
            "index": index,
            "startedAt": utc_timestamp(event_start),
            "finishedAt": utc_timestamp(event_start + 1.0),
            "wallNs": 1_000_000_000 + fork + index,
            "userCpuNs": 100_000_000,
            "systemCpuNs": 10_000_000,
            utilization_field: 11.0,
            "exitCode": 0,
            "timedOut": False,
        }

    warmup_samples = [
        sample(index, start_seconds + (index - 1) * 2.0)
        for index in range(1, warmups + 1)
    ]
    measured_start = start_seconds + warmups * 2.0
    samples = [
        sample(index, measured_start + (index - 1) * 2.0)
        for index in range(1, repetitions + 1)
    ]
    finished_seconds = measured_start + (repetitions - 1) * 2.0 + 1.0
    document = {
        "schemaVersion": schema_version,
        "suite": "kinetickk-command-performance",
        "generatedAt": utc_timestamp(finished_seconds),
        "label": "feature" if candidate else "main",
        "revision": revision,
        "dirty": dirty,
        **(
            {"sourceTreeSha256": source_tree_sha256}
            if schema_version == 2
            else {}
        ),
        "workingDirectory": (
            f"repository/{revision}"
            if schema_version == 2
            else str(working_directory)
        ),
        "command": command
        or [
            "./gradlew",
            "--offline",
            "--console=plain",
            "--no-daemon",
            *(["--no-build-cache"] if schema_version == 2 else []),
            "clean",
            ":app:web:wasmJsBrowserDistribution" if candidate else "wasmJsBrowserDistribution",
        ],
        "environment": dict(
            environment
            or (COMMON_BUILD_ENVIRONMENT_V2 if schema_version == 2 else COMMON_BUILD_ENVIRONMENT)
        ),
        "warmups": warmups,
        "repetitions": repetitions,
        "timeoutSeconds": 3_600,
        "samples": samples,
        "summary": {},
        "status": "ok",
    }
    if utilization_field == "waitedChildCpuUtilizationPercent":
        document["cpuScope"] = (
            "resource.RUSAGE_CHILDREN; diagnostic only because detached daemons are not included"
        )
    if schema_version == 2:
        document["warmupSamples"] = warmup_samples
    return document


def artifact_document(
    *,
    role: str,
    repository: pathlib.Path,
) -> dict[str, object]:
    candidate = role == "candidate"
    source_tree_sha256 = ("e" if candidate else "f") * 64
    app_raw = 120 if candidate else 100
    app_gzip = 60 if candidate else 50
    application_sha256 = ("c" if candidate else "b") * 64
    application_source = repository / (
        "app/web/build/compileSync/wasmJs/main/productionExecutable/optimized/kinetickk.wasm"
        if candidate
        else "build/compileSync/wasmJs/main/productionExecutable/optimized/kinetickk.wasm"
    )
    return {
        "schemaVersion": 2,
        "suite": "kinetickk-artifact-inventory",
        "generatedAtUtc": utc_timestamp(10.0),
        "artifactRoot": str(
            repository
            / (
                "app/web/build/dist/wasmJs/productionExecutable"
                if candidate
                else "build/dist/wasmJs/productionExecutable"
            )
        ),
        "source": {
            "label": "feature" if candidate else "main",
            "revision": "candidate-sha" if candidate else "baseline-sha",
            "dirty": False,
            "repository": str(repository),
        },
        "applicationWasm": {
            "matchingMethod": "sha256-and-byte-equality",
            "source": {
                "path": str(application_source),
                "sha256": application_sha256,
                "rawBytes": app_raw,
            },
            "distribution": {
                "path": "application.wasm",
                "sha256": application_sha256,
                "rawBytes": app_raw,
            },
        },
        "selection": copy.deepcopy(COMMON_SELECTION),
        "compression": copy.deepcopy(COMMON_COMPRESSION),
        "environment": copy.deepcopy(COMMON_ARTIFACT_ENVIRONMENT),
        "summary": {
            "fileCount": 2,
            "rawBytes": app_raw + 200,
            "gzipBytes": app_gzip + 100,
            "wasmDeclaredFunctionCount": 15,
            "byCategory": {
                "wasm": {
                    "fileCount": 2,
                    "rawBytes": app_raw + 200,
                    "gzipBytes": app_gzip + 100,
                },
            },
        },
        "files": [
            {
                "path": "runtime.wasm",
                "category": "wasm",
                "sha256": "a" * 64,
                "rawBytes": 200,
                "gzipBytes": 100,
                "wasm": {
                    "customSectionNames": [],
                    "sectionEntryCounts": {"function": 10},
                },
            },
            {
                "path": "application.wasm",
                "category": "wasm",
                "sha256": application_sha256,
                "rawBytes": app_raw,
                "gzipBytes": app_gzip,
                "wasm": {
                    "customSectionNames": [],
                    "sectionEntryCounts": {"function": 5},
                },
            },
        ],
    }


def write_json(directory: pathlib.Path, name: str, document: dict[str, object]) -> pathlib.Path:
    path = directory / name
    path.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    return path


class BuildArtifactContractTest(unittest.TestCase):
    def valid_inputs(
        self,
        root: pathlib.Path,
        *,
        candidate_second_start: float = 6.0,
        baseline_environment: dict[str, object] | None = None,
        candidate_environment: dict[str, object] | None = None,
        shared_working_directory: bool = False,
    ) -> tuple[dict[str, object], dict[str, object], dict[str, object], dict[str, object]]:
        baseline_worktree = root / "baseline-worktree"
        candidate_worktree = baseline_worktree if shared_working_directory else root / "candidate-worktree"
        candidate_paths = [
            write_json(
                root,
                "01-candidate.json",
                build_document(
                    role="candidate",
                    fork=1,
                    start_seconds=0.0,
                    working_directory=candidate_worktree,
                    environment=candidate_environment,
                ),
            ),
            write_json(
                root,
                "04-candidate.json",
                build_document(
                    role="candidate",
                    fork=2,
                    start_seconds=candidate_second_start,
                    working_directory=candidate_worktree,
                    environment=candidate_environment,
                ),
            ),
        ]
        baseline_paths = [
            write_json(
                root,
                "02-baseline.json",
                build_document(
                    role="baseline",
                    fork=1,
                    start_seconds=2.0,
                    working_directory=baseline_worktree,
                    environment=baseline_environment,
                ),
            ),
            write_json(
                root,
                "03-baseline.json",
                build_document(
                    role="baseline",
                    fork=2,
                    start_seconds=4.0,
                    working_directory=baseline_worktree,
                    environment=baseline_environment,
                ),
            ),
        ]
        baseline_artifacts = compare_build_artifacts.read_json(
            write_json(
                root,
                "artifacts-baseline.json",
                artifact_document(role="baseline", repository=baseline_worktree),
            ),
        )
        candidate_artifacts = compare_build_artifacts.read_json(
            write_json(
                root,
                "artifacts-candidate.json",
                artifact_document(role="candidate", repository=candidate_worktree),
            ),
        )
        return (
            compare_build_artifacts.load_builds(baseline_paths),
            compare_build_artifacts.load_builds(candidate_paths),
            baseline_artifacts,
            candidate_artifacts,
        )

    def test_valid_contract_preserves_raw_samples_and_relative_input_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            report = compare_build_artifacts.build_report(*self.valid_inputs(root))

            serialized = json.dumps(report)
            self.assertNotIn(str(root), serialized)
            self.assertEqual(
                ["candidate", "baseline", "baseline", "candidate"],
                [entry["role"] for entry in report["buildProtocol"]["runOrder"]],
            )
            self.assertEqual(
                "app-web-wasm-production-distribution",
                report["buildProtocol"]["semanticBuildCommand"]["tasks"][1],
            )
            self.assertEqual(
                {"baseline": "legacy-root", "candidate": "app-web-module"},
                report["buildProtocol"]["taskAliases"],
            )
            self.assertEqual(
                {
                    "baseline": "build/dist/wasmJs/productionExecutable",
                    "candidate": "app/web/build/dist/wasmJs/productionExecutable",
                },
                report["artifactInventoryContract"]["artifactRoots"],
            )
            self.assertEqual(
                {
                    "baseline": (
                        "build/compileSync/wasmJs/main/productionExecutable/optimized/kinetickk.wasm"
                    ),
                    "candidate": (
                        "app/web/build/compileSync/wasmJs/main/productionExecutable/optimized/kinetickk.wasm"
                    ),
                },
                report["artifactInventoryContract"]["applicationWasmSources"],
            )
            candidate_samples = report["candidate"]["build"]["rawSamples"]
            self.assertEqual("01-candidate.json", candidate_samples[0]["source"]["file"])
            self.assertEqual(64, len(candidate_samples[0]["source"]["sha256"]))
            self.assertIn("processTreeCpuUtilizationPercent", candidate_samples[0]["sample"])
            self.assertEqual(
                "artifacts-baseline.json",
                report["baseline"]["artifactInputSource"]["file"],
            )
            markdown = compare_build_artifacts.render_markdown(report)
            self.assertIn("A-B-B-A with A=candidate", markdown)
            self.assertNotIn("warm global dependency", markdown)

    def test_build_contract_rejects_wrong_order_overlap_environment_and_worktree(self) -> None:
        cases = (
            ("A-B-B-A", {"candidate_second_start": 3.0}),
            ("overlap", {"candidate_second_start": 4.5}),
            (
                "environments differ",
                {
                    "candidate_environment": {
                        **COMMON_BUILD_ENVIRONMENT,
                        "architecture": "arm64",
                    },
                },
            ),
            ("distinct working directories", {"shared_working_directory": True}),
        )
        for message, options in cases:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as temporary:
                inputs = self.valid_inputs(pathlib.Path(temporary), **options)
                with self.assertRaisesRegex(ValueError, message):
                    compare_build_artifacts.build_report(*inputs)

    def test_dirty_or_non_semantic_build_input_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            dirty_path = write_json(
                root,
                "dirty.json",
                build_document(
                    role="candidate",
                    fork=1,
                    start_seconds=0.0,
                    working_directory=root / "candidate",
                    dirty=True,
                ),
            )
            with self.assertRaisesRegex(ValueError, "dirty=false"):
                compare_build_artifacts.load_builds([dirty_path])

            invalid_command = build_document(
                role="candidate",
                fork=1,
                start_seconds=0.0,
                working_directory=root / "candidate",
                command=[
                    "./gradlew",
                    "--no-daemon",
                    "--offline",
                    "--console=plain",
                    "clean",
                    ":other:web:wasmJsBrowserDistribution",
                ],
            )
            invalid_path = write_json(root, "invalid-command.json", invalid_command)
            with self.assertRaisesRegex(ValueError, "legacy-root or app:web"):
                compare_build_artifacts.load_builds([invalid_path])

    def test_schema_status_sample_and_measurement_variant_are_validated(self) -> None:
        mutations = (
            ("schemaVersion", lambda document: document.update({"schemaVersion": 3})),
            ("successful build measurement", lambda document: document.update({"status": "failed"})),
            (
                "must be successful",
                lambda document: document["samples"][0].update({"exitCode": 9}),
            ),
        )
        for message, mutate in mutations:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as temporary:
                root = pathlib.Path(temporary)
                document = build_document(
                    role="candidate",
                    fork=1,
                    start_seconds=0.0,
                    working_directory=root / "candidate",
                )
                mutate(document)
                path = write_json(root, "invalid.json", document)
                with self.assertRaisesRegex(ValueError, message):
                    compare_build_artifacts.load_builds([path])

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            paths = [
                write_json(
                    root,
                    f"current-{fork}.json",
                    build_document(
                        role="candidate",
                        fork=fork,
                        start_seconds=float(fork * 2),
                        working_directory=root / "candidate",
                        utilization_field="waitedChildCpuUtilizationPercent",
                    ),
                )
                for fork in (1, 2)
            ]
            loaded = compare_build_artifacts.load_builds(paths)
            self.assertEqual("waited-child-v1", loaded["measurementVariant"])

    def test_v2_document_preserves_warmup_and_multiple_measurements(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            path = write_json(
                root,
                "current.json",
                build_document(
                    role="candidate",
                    fork=1,
                    start_seconds=0.0,
                    working_directory=root / "candidate",
                    utilization_field="waitedChildCpuUtilizationPercent",
                    schema_version=2,
                    repetitions=3,
                    warmups=1,
                ),
            )

            loaded = compare_build_artifacts.load_builds([path])

        self.assertEqual(2, loaded["schemaVersion"])
        self.assertEqual("waited-child-v2", loaded["measurementVariant"])
        self.assertEqual(COMMON_BUILD_ENVIRONMENT_V2, loaded["environment"])
        self.assertEqual(1, len(loaded["rawWarmups"]))
        self.assertEqual(3, len(loaded["rawSamples"]))
        self.assertEqual(3, len(loaded["wallSeconds"]))
        self.assertEqual([1, 2, 3], [entry["sample"]["index"] for entry in loaded["rawSamples"]])

    def test_v2_environment_and_command_contract_fail_closed(self) -> None:
        mutations = (
            (
                "missing fields: nodeVersion",
                lambda document: document["environment"].pop("nodeVersion"),
            ),
            (
                "outside command schema v2",
                lambda document: document["environment"].update({"unexpected": "value"}),
            ),
            (
                "exact Launcher JVM and Daemon JVM lines",
                lambda document: document["environment"].update(
                    {"javaVersion": 'openjdk version "17.0.20"'},
                ),
            ),
            (
                "build command flags must be exactly",
                lambda document: document["command"].remove("--no-build-cache"),
            ),
            (
                "must use the worktree ./gradlew",
                lambda document: document["command"].__setitem__(0, "/tmp/gradlew"),
            ),
            (
                "revision must be a full Git object ID",
                lambda document: document.update({"revision": "candidate-sha"}),
            ),
            (
                "workingDirectory must match its schema v2 revision",
                lambda document: document.update(
                    {"workingDirectory": "repository/" + "a" * 40},
                ),
            ),
            (
                "sourceTreeSha256 must be a lowercase SHA-256 digest",
                lambda document: document.update({"sourceTreeSha256": "invalid"}),
            ),
        )
        for message, mutate in mutations:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as temporary:
                root = pathlib.Path(temporary)
                document = build_document(
                    role="candidate",
                    fork=1,
                    start_seconds=0.0,
                    working_directory=root / "candidate",
                    schema_version=2,
                )
                mutate(document)
                path = write_json(root, "invalid-v2.json", document)
                with self.assertRaisesRegex(ValueError, message):
                    compare_build_artifacts.load_builds([path])

    def test_legacy_and_v2_build_documents_are_never_pooled_or_compared(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            working_directory = root / "candidate"
            paths = [
                write_json(
                    root,
                    "legacy.json",
                    build_document(
                        role="candidate",
                        fork=1,
                        start_seconds=0.0,
                        working_directory=working_directory,
                    ),
                ),
                write_json(
                    root,
                    "v2.json",
                    build_document(
                        role="candidate",
                        fork=2,
                        start_seconds=2.0,
                        working_directory=working_directory,
                        schema_version=2,
                    ),
                ),
            ]
            with self.assertRaisesRegex(ValueError, "schemaVersion changed within a build group"):
                compare_build_artifacts.load_builds(paths)

            baseline, candidate, _, _ = self.valid_inputs(root)
            candidate["schemaVersion"] = 2
            with self.assertRaisesRegex(ValueError, "command schema versions differ"):
                compare_build_artifacts.validate_build_protocol(baseline, candidate)

    def test_branch_comparison_rejects_unpaired_warmup_commands(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            baseline, candidate, _, _ = self.valid_inputs(pathlib.Path(temporary))
            candidate["rawWarmups"] = [{"sample": {}}]
            with self.assertRaisesRegex(ValueError, "must not contain warmup commands"):
                compare_build_artifacts.validate_build_protocol(baseline, candidate)

    def test_artifact_selection_compression_environment_and_dirty_must_match(self) -> None:
        mutations = (
            (
                "selection differ",
                lambda document: document["selection"].update({"includes": ["*.wasm"]}),
            ),
            (
                "gzip implementation",
                lambda document: document["compression"].update({"implementation": "other"}),
            ),
            (
                "environment differ",
                lambda document: document["environment"].update({"zlibVersion": "other"}),
            ),
            (
                "dirty=false",
                lambda document: document["source"].update({"dirty": True}),
            ),
        )
        for message, mutate in mutations:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as temporary:
                baseline, candidate, baseline_artifacts, candidate_artifacts = self.valid_inputs(
                    pathlib.Path(temporary),
                )
                candidate_artifacts = copy.deepcopy(candidate_artifacts)
                mutate(candidate_artifacts)
                with self.assertRaisesRegex(ValueError, message):
                    compare_build_artifacts.build_report(
                        baseline,
                        candidate,
                        baseline_artifacts,
                        candidate_artifacts,
                    )

    def test_application_wasm_provenance_mutations_fail_closed(self) -> None:
        mutations = (
            (
                "source and distribution identities differ",
                lambda document: document["applicationWasm"]["source"].update(
                    {"sha256": "d" * 64},
                ),
            ),
            (
                "distribution path does not match inventory",
                lambda document: document["applicationWasm"]["distribution"].update(
                    {"path": "missing.wasm"},
                ),
            ),
            (
                "exactly one distribution file",
                lambda document: document["files"].append(copy.deepcopy(document["files"][1])),
            ),
            (
                "approved optimized linker output",
                lambda document: document["applicationWasm"]["source"].update(
                    {"path": str(pathlib.Path(document["source"]["repository"]) / "other.wasm")},
                ),
            ),
        )
        for message, mutate in mutations:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as temporary:
                baseline, candidate, baseline_artifacts, candidate_artifacts = self.valid_inputs(
                    pathlib.Path(temporary),
                )
                candidate_artifacts = copy.deepcopy(candidate_artifacts)
                mutate(candidate_artifacts)
                with self.assertRaisesRegex(ValueError, message):
                    compare_build_artifacts.build_report(
                        baseline,
                        candidate,
                        baseline_artifacts,
                        candidate_artifacts,
                    )


if __name__ == "__main__":
    unittest.main()
