#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Aggregate KINETICKK benchmark forks and compare two branch adapters."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
import re
import statistics
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence


SCHEMA_VERSION = 2
SOURCE_CONTRACT_VERSION = 2
VALIDATION_CONTRACT_VERSION = 1
SOURCE_ROLES = ("adapter", "harness", "runner", "comparator", "provenanceEmitter")
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")
SIGNED_LONG_PATTERN = re.compile(r"-?(?:0|[1-9][0-9]*)")
METRICS = (
    "wallNanosPerOperation",
    "cpuNanosPerOperation",
    "allocatedBytesPerOperation",
    "gcCollectionsPerOperation",
    "gcNanosPerOperation",
)
LOWER_IS_BETTER = set(METRICS)
@dataclass(frozen=True)
class Summary:
    count: int
    mean: float
    median: float
    p95: float
    minimum: float
    maximum: float
    coefficient_of_variation: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Pool raw benchmark samples, bootstrap deltas and write JSON/Markdown reports.",
    )
    parser.add_argument("--baseline", nargs="+", required=True, type=Path, help="Baseline fork JSON files")
    parser.add_argument("--candidate", nargs="+", required=True, type=Path, help="Candidate fork JSON files")
    parser.add_argument("--baseline-name", default="main")
    parser.add_argument("--candidate-name", default="feature/pokeball-full-refactor")
    parser.add_argument("--expected-baseline-revision")
    parser.add_argument("--expected-candidate-revision")
    parser.add_argument("--expected-baseline-label")
    parser.add_argument("--expected-candidate-label")
    parser.add_argument(
        "--expected-forks",
        help="Comma-separated positive fork identities required on both sides.",
    )
    parser.add_argument("--require-clean-inputs", action="store_true")
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-markdown", required=True, type=Path)
    parser.add_argument("--effect-threshold-percent", type=float, default=5.0)
    parser.add_argument("--bootstrap-resamples", type=int, default=10_000)
    parser.add_argument(
        "--semantic-contract",
        choices=("outcome-fingerprint", "exact-metadata"),
        default="outcome-fingerprint",
        help=(
            "Require an explicit outcomeFingerprint for every non-harness scenario, or rely "
            "on exact category/description/metadata equality for suites whose metadata already "
            "contains their observable outcome contract."
        ),
    )
    parser.add_argument("--fail-on-regression", action="store_true")
    parser.add_argument("--fail-on-incomparable", action="store_true")
    args = parser.parse_args()
    if args.effect_threshold_percent < 0:
        parser.error("--effect-threshold-percent must be non-negative")
    if args.bootstrap_resamples < 1_000:
        parser.error("--bootstrap-resamples must be at least 1000")
    return args


def load_runs(paths: Sequence[Path]) -> list[dict[str, Any]]:
    runs: list[dict[str, Any]] = []
    for path in paths:
        raw = path.read_bytes()
        run = json.loads(raw.decode("utf-8"))
        if run.get("schemaVersion") != SCHEMA_VERSION:
            raise ValueError(f"{path}: unsupported schemaVersion {run.get('schemaVersion')!r}")
        run["_source"] = {
            "file": path.name,
            "sha256": hashlib.sha256(raw).hexdigest(),
        }
        runs.append(run)
    if not runs:
        raise ValueError("At least one input run is required")
    return runs


def validated_source_contract(run: dict[str, Any], group_name: str) -> dict[str, Any]:
    contract = run.get("sourceContract")
    if not isinstance(contract, dict):
        raise ValueError(f"{group_name}: sourceContract must be an object")
    expected_keys = {"contractVersion", "algorithm", *SOURCE_ROLES}
    if set(contract) != expected_keys:
        raise ValueError(
            f"{group_name}: sourceContract keys differ from {sorted(expected_keys)}",
        )
    if contract.get("contractVersion") != SOURCE_CONTRACT_VERSION:
        raise ValueError(f"{group_name}: unsupported source contract version")
    if contract.get("algorithm") != "SHA-256":
        raise ValueError(f"{group_name}: sourceContract.algorithm must be SHA-256")
    for role in SOURCE_ROLES:
        source = contract.get(role)
        if not isinstance(source, dict) or set(source) != {"path", "sha256"}:
            raise ValueError(f"{group_name}: sourceContract.{role} must contain path and sha256")
        path = source.get("path")
        if (
            not isinstance(path, str)
            or not path
            or "\\" in path
            or Path(path).is_absolute()
            or Path(path).as_posix() != path
            or any(part in ("", ".", "..") for part in path.split("/"))
        ):
            raise ValueError(
                f"{group_name}: sourceContract.{role}.path must be normalized and repository-relative",
            )
        digest = source.get("sha256")
        if not isinstance(digest, str) or SHA256_PATTERN.fullmatch(digest) is None:
            raise ValueError(f"{group_name}: sourceContract.{role}.sha256 is invalid")
    return contract


def validated_long_string(value: Any, context: str) -> int:
    if not isinstance(value, str) or SIGNED_LONG_PATTERN.fullmatch(value) is None:
        raise ValueError(f"{context} must be a canonical signed decimal string")
    parsed = int(value)
    if not -(2**63) <= parsed <= 2**63 - 1:
        raise ValueError(f"{context} is outside the signed 64-bit range")
    return parsed


def validated_scenario_evidence(
    scenario: dict[str, Any],
    group_name: str,
    scenario_name: str,
    metadata: dict[str, Any],
) -> tuple[str, str]:
    validation = scenario.get("validation")
    expected_keys = {
        "contractVersion",
        "expectedTimedResult",
        "actualTimedResult",
        "expectedOutcomeWitness",
        "actualOutcomeWitness",
    }
    if not isinstance(validation, dict) or set(validation) != expected_keys:
        raise ValueError(
            f"{group_name}: {scenario_name}.validation must contain the exact v1 evidence fields",
        )
    if validation.get("contractVersion") != VALIDATION_CONTRACT_VERSION:
        raise ValueError(f"{group_name}: {scenario_name} has unsupported validation contract")
    expected_timed = validated_long_string(
        validation.get("expectedTimedResult"),
        f"{group_name}: {scenario_name}.validation.expectedTimedResult",
    )
    actual_timed = validated_long_string(
        validation.get("actualTimedResult"),
        f"{group_name}: {scenario_name}.validation.actualTimedResult",
    )
    expected_witness = validated_long_string(
        validation.get("expectedOutcomeWitness"),
        f"{group_name}: {scenario_name}.validation.expectedOutcomeWitness",
    )
    actual_witness = validated_long_string(
        validation.get("actualOutcomeWitness"),
        f"{group_name}: {scenario_name}.validation.actualOutcomeWitness",
    )
    if actual_timed != expected_timed:
        raise ValueError(f"{group_name}: {scenario_name} timed validation evidence does not match")
    if actual_witness != expected_witness:
        raise ValueError(f"{group_name}: {scenario_name} outcome witness evidence does not match")
    metadata_witness = metadata.get("outcomeFingerprint")
    if metadata_witness != str(expected_witness):
        raise ValueError(
            f"{group_name}: {scenario_name}.metadata.outcomeFingerprint does not bind its witness",
        )
    return str(expected_timed), str(expected_witness)


def validated_scenario_contract(
    run: dict[str, Any],
    group_name: str,
) -> list[tuple[Any, ...]]:
    scenarios = run.get("scenarios")
    if not isinstance(scenarios, list) or not scenarios:
        raise ValueError(f"{group_name}: scenarios must be a non-empty array")
    contract: list[tuple[Any, ...]] = []
    names: set[str] = set()
    for scenario in scenarios:
        if not isinstance(scenario, dict):
            raise ValueError(f"{group_name}: scenario must be an object")
        name = scenario.get("name")
        if not isinstance(name, str) or not name:
            raise ValueError(f"{group_name}: scenario has invalid name {name!r}")
        if name in names:
            raise ValueError(f"{group_name}: duplicate scenario {name!r} in a fork")
        names.add(name)
        category = scenario.get("category")
        description = scenario.get("description")
        if not isinstance(category, str) or not category:
            raise ValueError(f"{group_name}: {name}.category must be a non-empty string")
        if not isinstance(description, str) or not description:
            raise ValueError(f"{group_name}: {name}.description must be a non-empty string")
        metadata = scenario.get("metadata", {})
        if not isinstance(metadata, dict):
            raise ValueError(f"{group_name}: {name}.metadata must be an object")
        if any(not isinstance(key, str) or not isinstance(value, str) for key, value in metadata.items()):
            raise ValueError(f"{group_name}: {name}.metadata values must be strings")
        validation_contract = validated_scenario_evidence(
            scenario,
            group_name,
            name,
            metadata,
        )
        samples = scenario.get("samples")
        if not isinstance(samples, list) or not samples:
            raise ValueError(f"{group_name}: {name}.samples must be a non-empty array")
        for sample_index, sample in enumerate(samples, start=1):
            if not isinstance(sample, dict):
                raise ValueError(f"{group_name}: {name} sample {sample_index} must be an object")
            operations = sample.get("operations")
            if isinstance(operations, bool) or not isinstance(operations, int) or operations <= 0:
                raise ValueError(
                    f"{group_name}: {name} sample {sample_index} has invalid operations",
                )
            for metric in METRICS:
                value = sample.get(metric)
                if value is None and metric != "wallNanosPerOperation":
                    continue
                if (
                    isinstance(value, bool)
                    or not isinstance(value, (int, float))
                    or not math.isfinite(float(value))
                    or float(value) < 0.0
                    or (metric == "wallNanosPerOperation" and float(value) == 0.0)
                ):
                    raise ValueError(
                        f"{group_name}: {name} sample {sample_index} has invalid {metric}",
                    )
        contract.append(
            (
                name,
                category,
                description,
                metadata,
                validation_contract,
            ),
        )
    return contract


def validate_run_group(runs: Sequence[dict[str, Any]], group_name: str) -> None:
    reference = runs[0]
    invariants = (
        "suiteVersion",
        "adapter",
        "label",
        "revision",
        "dirty",
    )
    environment_invariants = (
        "osName",
        "osVersion",
        "architecture",
        "javaVersion",
        "javaVendor",
        "vmName",
        "availableProcessors",
        "maxHeapBytes",
        "garbageCollectors",
        "jvmArguments",
    )
    profile_invariants = (
        "name",
        "warmupIterations",
        "measurementIterations",
        "targetIterationMillis",
    )
    suite_version = reference.get("suiteVersion")
    if not isinstance(suite_version, str) or re.fullmatch(r"[a-z0-9-]+-v(?:[2-9]|[1-9][0-9]+)", suite_version) is None:
        raise ValueError(f"{group_name}: suiteVersion must declare raw schema v2 or newer")
    reference_source_contract = validated_source_contract(reference, group_name)
    reference_contract = validated_scenario_contract(reference, group_name)
    seen_forks: set[str] = set()
    for run in runs:
        if validated_source_contract(run, group_name) != reference_source_contract:
            raise ValueError(f"{group_name}: sourceContract differs across forks")
        fork = run.get("fork")
        if fork is None or not str(fork).isdigit() or int(str(fork)) <= 0:
            raise ValueError(f"{group_name}: invalid fork identity {fork!r}")
        fork_identity = str(int(str(fork)))
        if fork_identity in seen_forks:
            raise ValueError(f"{group_name}: duplicate fork identity {fork!r}")
        seen_forks.add(fork_identity)
        contract = validated_scenario_contract(run, group_name)
        if contract != reference_contract:
            raise ValueError(f"{group_name}: scenario set/order/contract differs across forks")
    for run in runs[1:]:
        for key in invariants:
            if run.get(key) != reference.get(key):
                raise ValueError(f"{group_name}: {key} differs across forks")
        for key in environment_invariants:
            if run["environment"].get(key) != reference["environment"].get(key):
                raise ValueError(f"{group_name}: environment.{key} differs across forks")
        for key in profile_invariants:
            if run["profile"].get(key) != reference["profile"].get(key):
                raise ValueError(f"{group_name}: profile.{key} differs across forks")


def parse_expected_forks(raw: str | None) -> set[str] | None:
    if raw is None:
        return None
    values = raw.split(",")
    if not values or any(not value.isdigit() or int(value) <= 0 for value in values):
        raise ValueError("expected forks must be comma-separated positive integers")
    normalized = {str(int(value)) for value in values}
    if len(normalized) != len(values):
        raise ValueError("expected forks must not contain duplicates")
    return normalized


def validate_expected_run_identity(
    runs: Sequence[dict[str, Any]],
    group_name: str,
    expected_revision: str | None,
    expected_label: str | None,
    expected_forks: set[str] | None,
    require_clean: bool,
) -> None:
    actual_forks = {str(int(str(run["fork"]))) for run in runs}
    if expected_forks is not None and actual_forks != expected_forks:
        raise ValueError(
            f"{group_name}: fork identities {sorted(actual_forks)} do not match "
            f"expected {sorted(expected_forks)}",
        )
    for run in runs:
        if expected_revision is not None and run.get("revision") != expected_revision:
            raise ValueError(
                f"{group_name}: revision {run.get('revision')!r} does not match "
                f"expected {expected_revision!r}",
            )
        if expected_label is not None and run.get("label") != expected_label:
            raise ValueError(
                f"{group_name}: label {run.get('label')!r} does not match "
                f"expected {expected_label!r}",
            )
        if require_clean and run.get("dirty") is not False:
            raise ValueError(f"{group_name}: dirty must be exactly false")


def cross_environment_warnings(
    baseline: dict[str, Any],
    candidate: dict[str, Any],
) -> list[str]:
    warnings: list[str] = []
    keys = (
        "osName",
        "osVersion",
        "architecture",
        "javaVersion",
        "javaVendor",
        "vmName",
        "availableProcessors",
        "maxHeapBytes",
        "garbageCollectors",
        "jvmArguments",
    )
    for key in keys:
        if baseline["environment"].get(key) != candidate["environment"].get(key):
            warnings.append(f"environment.{key} differs")
    if baseline.get("suiteVersion") != candidate.get("suiteVersion"):
        warnings.append("suiteVersion differs")
    if baseline.get("profile") != candidate.get("profile"):
        warnings.append("benchmark profile differs")
    return warnings


def index_scenarios(runs: Sequence[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for run in runs:
        for scenario in run["scenarios"]:
            name = scenario["name"]
            target = indexed.setdefault(
                name,
                {
                    "name": name,
                    "category": scenario["category"],
                    "description": scenario["description"],
                    "metadata": scenario.get("metadata", {}),
                    "validationContract": {
                        "expectedTimedResult": scenario["validation"]["expectedTimedResult"],
                        "expectedOutcomeWitness": scenario["validation"]["expectedOutcomeWitness"],
                    },
                    "metrics": {metric: [] for metric in METRICS},
                    "metricForks": {metric: [] for metric in METRICS},
                    "forkSamples": [],
                    "forks": 0,
                },
            )
            if target["category"] != scenario["category"]:
                raise ValueError(f"{name}: category differs across forks")
            if target["metadata"] != scenario.get("metadata", {}):
                raise ValueError(f"{name}: metadata differs across forks")
            validation_contract = {
                "expectedTimedResult": scenario["validation"]["expectedTimedResult"],
                "expectedOutcomeWitness": scenario["validation"]["expectedOutcomeWitness"],
            }
            if target["validationContract"] != validation_contract:
                raise ValueError(f"{name}: validation contract differs across forks")
            target["forks"] += 1
            target["forkSamples"].append(scenario["samples"])
            for metric in METRICS:
                fork_values: list[float] = []
                for sample in scenario["samples"]:
                    value = sample.get(metric)
                    if value is not None:
                        fork_values.append(float(value))
                target["metrics"][metric].extend(fork_values)
                target["metricForks"][metric].append(fork_values)
    return indexed


def summarize(values: Sequence[float]) -> Summary | None:
    if not values:
        return None
    ordered = sorted(values)
    mean = statistics.fmean(ordered)
    deviation = statistics.stdev(ordered) if len(ordered) > 1 else 0.0
    return Summary(
        count=len(ordered),
        mean=mean,
        median=statistics.median(ordered),
        p95=percentile(ordered, 0.95),
        minimum=ordered[0],
        maximum=ordered[-1],
        coefficient_of_variation=0.0 if mean == 0.0 else deviation / mean,
    )


def percentile(ordered: Sequence[float], fraction: float) -> float:
    if len(ordered) == 1:
        return ordered[0]
    position = max(0.0, min(1.0, fraction)) * (len(ordered) - 1)
    lower = math.floor(position)
    upper = min(len(ordered) - 1, lower + 1)
    weight = position - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * weight


def percent_delta(candidate: float, baseline: float) -> float | None:
    if baseline == 0.0:
        return None
    return (candidate / baseline - 1.0) * 100.0


def hierarchical_bootstrap_median_delta(
    baseline_forks: Sequence[Sequence[float]],
    candidate_forks: Sequence[Sequence[float]],
    resamples: int,
    seed_material: str,
) -> tuple[float, float] | None:
    baseline = [list(fork) for fork in baseline_forks if fork]
    candidate = [list(fork) for fork in candidate_forks if fork]
    if not baseline or not candidate:
        return None
    digest = hashlib.sha256(seed_material.encode("utf-8")).digest()
    rng = random.Random(int.from_bytes(digest[:8], "big"))

    def resample_group(forks: Sequence[Sequence[float]]) -> float:
        values: list[float] = []
        for _ in range(len(forks)):
            fork = forks[rng.randrange(len(forks))]
            values.extend(fork[rng.randrange(len(fork))] for _ in range(len(fork)))
        return statistics.median(values)

    deltas: list[float] = []
    for _ in range(resamples):
        baseline_median = resample_group(baseline)
        candidate_median = resample_group(candidate)
        value = percent_delta(candidate_median, baseline_median)
        if value is not None:
            deltas.append(value)
    if not deltas:
        return None
    deltas.sort()
    return percentile(deltas, 0.025), percentile(deltas, 0.975)


def compatible_metadata(
    baseline: dict[str, str], candidate: dict[str, str]
) -> tuple[bool, list[str]]:
    differences = [
        key
        for key in sorted(baseline.keys() | candidate.keys())
        if baseline.get(key) != candidate.get(key)
    ]
    return not differences, differences


def semantic_compatibility_differences(
    baseline: dict[str, Any],
    candidate: dict[str, Any],
    semantic_contract: str,
) -> list[str]:
    _, differences = compatible_metadata(baseline["metadata"], candidate["metadata"])
    if baseline["category"] != candidate["category"]:
        differences.append("category")
    if baseline["description"] != candidate["description"]:
        differences.append("description")
    if baseline.get("validationContract") != candidate.get("validationContract"):
        differences.append("validationContract")
    if (
        semantic_contract == "outcome-fingerprint"
        and (baseline["category"] != "harness" or candidate["category"] != "harness")
    ):
        if "outcomeFingerprint" not in baseline["metadata"]:
            differences.append("outcomeFingerprint:missing-baseline")
        if "outcomeFingerprint" not in candidate["metadata"]:
            differences.append("outcomeFingerprint:missing-candidate")
    return differences


def comparison_contract_differences(
    baseline: dict[str, Any],
    candidate: dict[str, Any],
) -> list[str]:
    differences: list[str] = []
    if baseline.get("adapter") != candidate.get("adapter"):
        differences.append("adapter")
    baseline_sources = baseline["sourceContract"]
    candidate_sources = candidate["sourceContract"]
    for role in SOURCE_ROLES:
        if baseline_sources[role] != candidate_sources[role]:
            differences.append(f"sourceContract.{role}")
    return differences


def classify(
    delta: float | None,
    interval: tuple[float, float] | None,
    threshold: float,
    lower_is_better: bool,
) -> str:
    if delta is None or interval is None:
        return "insufficient-data"
    if abs(delta) < threshold:
        return "stable"
    low, high = interval
    if low <= 0.0 <= high:
        return "inconclusive"
    is_regression = delta > 0.0 if lower_is_better else delta < 0.0
    return "regression" if is_regression else "improvement"


def classify_with_zero_baseline(
    delta: float | None,
    interval: tuple[float, float] | None,
    threshold: float,
    lower_is_better: bool,
    baseline_median: float | None,
    candidate_median: float | None,
) -> str:
    if baseline_median == 0.0 and candidate_median is not None:
        if candidate_median == 0.0:
            return "stable"
        is_regression = candidate_median > 0.0 if lower_is_better else candidate_median < 0.0
        return "regression" if is_regression else "improvement"
    return classify(delta, interval, threshold, lower_is_better)


def summary_dict(value: Summary | None) -> dict[str, Any] | None:
    if value is None:
        return None
    return {
        "count": value.count,
        "mean": value.mean,
        "median": value.median,
        "p95": value.p95,
        "min": value.minimum,
        "max": value.maximum,
        "coefficientOfVariation": value.coefficient_of_variation,
    }


def build_comparison(
    baseline_runs: Sequence[dict[str, Any]],
    candidate_runs: Sequence[dict[str, Any]],
    args: argparse.Namespace,
) -> dict[str, Any]:
    if len(baseline_runs) != len(candidate_runs):
        raise ValueError("baseline and candidate fork counts differ")
    baseline_index = index_scenarios(baseline_runs)
    candidate_index = index_scenarios(candidate_runs)
    baseline_scenario_order = list(baseline_index)
    candidate_scenario_order = list(candidate_index)
    scenario_order_compatible = baseline_scenario_order == candidate_scenario_order
    contract_differences = comparison_contract_differences(
        baseline_runs[0],
        candidate_runs[0],
    )
    comparison_contract_compatible = not contract_differences
    shared = [name for name in baseline_scenario_order if name in candidate_index]
    comparisons: list[dict[str, Any]] = []
    for name in shared:
        baseline = baseline_index[name]
        candidate = candidate_index[name]
        compatibility_differences = semantic_compatibility_differences(
            baseline,
            candidate,
            args.semantic_contract,
        )
        compatibility_ok = comparison_contract_compatible and not compatibility_differences
        metrics: dict[str, Any] = {}
        for metric in METRICS:
            baseline_values = baseline["metrics"][metric]
            candidate_values = candidate["metrics"][metric]
            baseline_summary = summarize(baseline_values)
            candidate_summary = summarize(candidate_values)
            delta = (
                percent_delta(candidate_summary.median, baseline_summary.median)
                if baseline_summary is not None and candidate_summary is not None
                else None
            )
            interval = hierarchical_bootstrap_median_delta(
                baseline["metricForks"][metric],
                candidate["metricForks"][metric],
                args.bootstrap_resamples,
                f"{name}:{metric}",
            )
            metrics[metric] = {
                "baseline": summary_dict(baseline_summary),
                "candidate": summary_dict(candidate_summary),
                "medianDeltaPercent": delta,
                "bootstrap95Percent": list(interval) if interval else None,
                "classification": (
                    classify_with_zero_baseline(
                        delta,
                        interval,
                        args.effect_threshold_percent,
                        metric in LOWER_IS_BETTER,
                        baseline_summary.median if baseline_summary is not None else None,
                        candidate_summary.median if candidate_summary is not None else None,
                    )
                    if compatibility_ok
                    else "incomparable"
                ),
            }
        comparisons.append(
            {
                "name": name,
                "category": baseline["category"],
                "description": baseline["description"],
                "metadataCompatible": compatibility_ok,
                "metadataDifferences": compatibility_differences,
                "baselineMetadata": baseline["metadata"],
                "candidateMetadata": candidate["metadata"],
                "rawForkSamples": {
                    "baseline": baseline["forkSamples"],
                    "candidate": candidate["forkSamples"],
                },
                "metrics": metrics,
            }
        )

    environment_warnings = cross_environment_warnings(baseline_runs[0], candidate_runs[0])
    if environment_warnings:
        raise ValueError(
            "cross-branch benchmark contract differs: " + "; ".join(environment_warnings),
        )
    return {
        "schemaVersion": 2,
        "spdxFileCopyrightText": "2026 Vladislav Tomilov",
        "spdxLicenseIdentifier": "GPL-3.0-or-later",
        "baseline": {
            "name": args.baseline_name,
            "adapter": baseline_runs[0]["adapter"],
            "revision": baseline_runs[0]["revision"],
            "dirty": baseline_runs[0]["dirty"],
            "forks": len(baseline_runs),
            "sources": [
                {
                    **run["_source"],
                    "fork": run.get("fork"),
                    "generatedAt": run.get("generatedAt"),
                }
                for run in baseline_runs
            ],
            "environment": baseline_runs[0]["environment"],
            "sourceContract": baseline_runs[0]["sourceContract"],
        },
        "candidate": {
            "name": args.candidate_name,
            "adapter": candidate_runs[0]["adapter"],
            "revision": candidate_runs[0]["revision"],
            "dirty": candidate_runs[0]["dirty"],
            "forks": len(candidate_runs),
            "sources": [
                {
                    **run["_source"],
                    "fork": run.get("fork"),
                    "generatedAt": run.get("generatedAt"),
                }
                for run in candidate_runs
            ],
            "environment": candidate_runs[0]["environment"],
            "sourceContract": candidate_runs[0]["sourceContract"],
        },
        "profile": baseline_runs[0]["profile"],
        "effectThresholdPercent": args.effect_threshold_percent,
        "bootstrapResamples": args.bootstrap_resamples,
        "semanticContract": args.semantic_contract,
        "comparisonContractCompatible": comparison_contract_compatible,
        "comparisonContractDifferences": contract_differences,
        "environmentWarnings": environment_warnings,
        "sharedScenarioCount": len(shared),
        "scenarioOrderCompatible": scenario_order_compatible,
        "baselineScenarioOrder": baseline_scenario_order,
        "candidateScenarioOrder": candidate_scenario_order,
        "baselineOnlyScenarios": sorted(baseline_index.keys() - candidate_index.keys()),
        "candidateOnlyScenarios": sorted(candidate_index.keys() - baseline_index.keys()),
        "scenarios": comparisons,
    }


def fmt_number(value: float | None, digits: int = 2) -> str:
    return "n/a" if value is None else f"{value:.{digits}f}"


def fmt_time(nanos: float | None) -> str:
    if nanos is None:
        return "n/a"
    if nanos >= 1_000_000.0:
        return f"{nanos / 1_000_000.0:.3f} ms"
    if nanos >= 1_000.0:
        return f"{nanos / 1_000.0:.3f} µs"
    return f"{nanos:.2f} ns"


def fmt_bytes(value: float | None) -> str:
    if value is None:
        return "n/a"
    if value >= 1024.0:
        return f"{value / 1024.0:.2f} KiB"
    return f"{value:.1f} B"


def fmt_delta(value: float | None) -> str:
    return "n/a" if value is None else f"{value:+.2f}%"


def render_markdown(report: dict[str, Any]) -> str:
    scenarios = report["scenarios"]
    wall_classifications = [
        item["metrics"]["wallNanosPerOperation"]["classification"] for item in scenarios
    ]
    counts = {name: wall_classifications.count(name) for name in sorted(set(wall_classifications))}
    lines = [
        "<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->",
        "<!-- SPDX-License-Identifier: GPL-3.0-or-later -->",
        "",
        "# KINETICKK performance comparison",
        "",
        f"Baseline: `{report['baseline']['name']}` at `{report['baseline']['revision']}` "
        f"({report['baseline']['forks']} forks).",
        "",
        f"Candidate: `{report['candidate']['name']}` at `{report['candidate']['revision']}` "
        f"({report['candidate']['forks']} forks).",
        "",
        f"Profile: `{report['profile']['name']}`; effect threshold: "
        f"{report['effectThresholdPercent']:.2f}%; bootstrap resamples: "
        f"{report['bootstrapResamples']:,}.",
        "",
        f"Semantic contract: `{report['semanticContract']}`.",
        "",
        "## Outcome",
        "",
        ", ".join(f"{name}: **{count}**" for name, count in counts.items()) or "No shared scenarios.",
        "",
    ]
    if not report.get("comparisonContractCompatible", True):
        lines.extend(
            [
                "> [!CAUTION]",
                "> Source/adapter comparison contract differs: "
                + "; ".join(report["comparisonContractDifferences"])
                + ". All timing evidence is incomparable.",
                "",
            ],
        )
    if report["environmentWarnings"]:
        lines.extend(
            [
                "> [!WARNING]",
                "> Environment compatibility warnings: "
                + "; ".join(report["environmentWarnings"])
                + ". Timing deltas are not a strict A/B result.",
                "",
            ]
        )
    lines.extend(
        [
            "Lower wall time, CPU time, allocation and GC values are better. A verdict requires "
            "both the configured effect size and a bootstrap interval that excludes zero.",
            "",
            "## Wall time and allocation",
            "",
            "| Scenario | Baseline median | Candidate median | Wall Δ (95% bootstrap) | "
            "Baseline B/op | Candidate B/op | Allocation Δ | Verdict |",
            "|---|---:|---:|---:|---:|---:|---:|---|",
        ]
    )
    for scenario in scenarios:
        wall = scenario["metrics"]["wallNanosPerOperation"]
        allocation = scenario["metrics"]["allocatedBytesPerOperation"]
        wall_baseline = wall["baseline"]["median"] if wall["baseline"] else None
        wall_candidate = wall["candidate"]["median"] if wall["candidate"] else None
        allocation_baseline = allocation["baseline"]["median"] if allocation["baseline"] else None
        allocation_candidate = allocation["candidate"]["median"] if allocation["candidate"] else None
        interval = wall["bootstrap95Percent"]
        interval_text = (
            f"{fmt_delta(wall['medianDeltaPercent'])} "
            f"[{fmt_delta(interval[0])}, {fmt_delta(interval[1])}]"
            if interval
            else "n/a"
        )
        lines.append(
            f"| `{scenario['name']}` | {fmt_time(wall_baseline)} | {fmt_time(wall_candidate)} | "
            f"{interval_text} | {fmt_bytes(allocation_baseline)} | {fmt_bytes(allocation_candidate)} | "
            f"{fmt_delta(allocation['medianDeltaPercent'])} | **{wall['classification']}** |"
        )
    lines.extend(
        [
            "",
            "## Tail latency, CPU and variability",
            "",
            "| Scenario | Baseline p95 | Candidate p95 | Baseline CPU median | Candidate CPU median | "
            "Baseline CV | Candidate CV |",
            "|---|---:|---:|---:|---:|---:|---:|",
        ]
    )
    for scenario in scenarios:
        wall = scenario["metrics"]["wallNanosPerOperation"]
        cpu = scenario["metrics"]["cpuNanosPerOperation"]
        wb = wall["baseline"]
        wc = wall["candidate"]
        cb = cpu["baseline"]
        cc = cpu["candidate"]
        lines.append(
            f"| `{scenario['name']}` | {fmt_time(wb['p95'] if wb else None)} | "
            f"{fmt_time(wc['p95'] if wc else None)} | {fmt_time(cb['median'] if cb else None)} | "
            f"{fmt_time(cc['median'] if cc else None)} | "
            f"{fmt_number(wb['coefficientOfVariation'] * 100 if wb else None)}% | "
            f"{fmt_number(wc['coefficientOfVariation'] * 100 if wc else None)}% |"
        )
    incompatible = [item for item in scenarios if item["metadataDifferences"]]
    if incompatible:
        lines.extend(["", "## Incomparable semantic checkpoints", ""])
        for item in incompatible:
            lines.append(
                f"- `{item['name']}` differs in: {', '.join(item['metadataDifferences'])}."
            )
    if report["baselineOnlyScenarios"] or report["candidateOnlyScenarios"]:
        lines.extend(["", "## Unpaired scenarios", ""])
        if report["baselineOnlyScenarios"]:
            lines.append("- Baseline only: " + ", ".join(f"`{x}`" for x in report["baselineOnlyScenarios"]))
        if report["candidateOnlyScenarios"]:
            lines.append("- Candidate only: " + ", ".join(f"`{x}`" for x in report["candidateOnlyScenarios"]))
    if not report["scenarioOrderCompatible"]:
        lines.extend(
            [
                "",
                "## Incompatible execution order",
                "",
                "Baseline and candidate scenario order differs. Sequential JVM/JIT/GC position "
                "is part of the benchmark contract.",
            ],
        )
    lines.extend(
        [
            "",
            "## Environment",
            "",
            "| Field | Baseline | Candidate |",
            "|---|---|---|",
        ]
    )
    baseline_environment = report["baseline"]["environment"]
    candidate_environment = report["candidate"]["environment"]
    for key in (
        "osName",
        "osVersion",
        "architecture",
        "javaVersion",
        "javaVendor",
        "vmName",
        "availableProcessors",
        "maxHeapBytes",
        "garbageCollectors",
        "jvmArguments",
    ):
        lines.append(
            f"| `{key}` | `{baseline_environment.get(key)}` | `{candidate_environment.get(key)}` |"
        )
    lines.append("")
    return "\n".join(lines)


def has_regression(report: dict[str, Any]) -> bool:
    return any(
        metric["classification"] == "regression"
        for scenario in report["scenarios"]
        for metric in scenario["metrics"].values()
    )


def has_incomparability(report: dict[str, Any]) -> bool:
    return bool(
        not report.get("comparisonContractCompatible", True)
        or not report.get("scenarioOrderCompatible", True)
        or report["baselineOnlyScenarios"]
        or report["candidateOnlyScenarios"]
        or any(not scenario["metadataCompatible"] for scenario in report["scenarios"])
    )


def main() -> int:
    args = parse_args()
    try:
        baseline_runs = load_runs(args.baseline)
        candidate_runs = load_runs(args.candidate)
        validate_run_group(baseline_runs, args.baseline_name)
        validate_run_group(candidate_runs, args.candidate_name)
        expected_forks = parse_expected_forks(args.expected_forks)
        validate_expected_run_identity(
            baseline_runs,
            args.baseline_name,
            args.expected_baseline_revision,
            args.expected_baseline_label,
            expected_forks,
            args.require_clean_inputs,
        )
        validate_expected_run_identity(
            candidate_runs,
            args.candidate_name,
            args.expected_candidate_revision,
            args.expected_candidate_label,
            expected_forks,
            args.require_clean_inputs,
        )
        report = build_comparison(baseline_runs, candidate_runs, args)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"performance comparison failed: {error}", file=sys.stderr)
        return 2

    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_markdown.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(report, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    args.output_markdown.write_text(render_markdown(report), encoding="utf-8")
    print(f"Wrote {args.output_json.resolve()}")
    print(f"Wrote {args.output_markdown.resolve()}")
    if (
        (args.fail_on_regression and has_regression(report))
        or (args.fail_on_incomparable and has_incomparability(report))
    ):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
