#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Aggregate interleaved branch-native profile codec benchmark forks."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
import pathlib
import re
import statistics
import sys
from typing import Any


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import compare_results as statistical_engine


RESULT_NAME = re.compile(
    r"^(?P<sequence>[0-9]+)-(?P<branch>feature|main)-fork-(?P<fork>[0-9]+)\.json$",
)
IGNORED_JVM_ARGUMENT_PREFIX = "-Dkinetickk.benchmark."
SUITE_VERSION = "profile-persistence-v1"
COMPARISON_CONTRACT = "branch-native-logical-profile"
SEMANTIC_METADATA_KEYS = (
    "comparisonContract",
    "logicalShape",
    "unlockedWeapons",
    "labRanks",
    "discoveries",
)
WIRE_METADATA_KEYS = (
    "wireFormat",
    "schemaVersion",
    "payloadBytes",
    "payloadSha256",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=pathlib.Path)
    parser.add_argument("--output-json", required=True, type=pathlib.Path)
    parser.add_argument("--output-markdown", required=True, type=pathlib.Path)
    parser.add_argument("--effect-threshold-percent", type=float, default=5.0)
    parser.add_argument("--bootstrap-resamples", type=int, default=10_000)
    return parser.parse_args()


def require_mapping(value: Any, context: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{context} must be an object")
    return value


def require_list(value: Any, context: str) -> list[Any]:
    if not isinstance(value, list):
        raise ValueError(f"{context} must be an array")
    return value


def load_result(path: pathlib.Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as stream:
        document = require_mapping(json.load(stream), str(path))
    if document.get("schemaVersion") != 1:
        raise ValueError(f"Unsupported schemaVersion in {path}: {document.get('schemaVersion')!r}")
    if document.get("suiteVersion") != SUITE_VERSION:
        raise ValueError(f"Unexpected suiteVersion in {path}: {document.get('suiteVersion')!r}")
    require_mapping(document.get("profile"), f"{path}: profile")
    require_mapping(document.get("environment"), f"{path}: environment")
    require_list(document.get("scenarios"), f"{path}: scenarios")
    return document


def sha256_file(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalized_environment(document: dict[str, Any]) -> dict[str, Any]:
    environment = require_mapping(document["environment"], "environment")
    arguments = require_list(environment.get("jvmArguments"), "environment.jvmArguments")
    return {
        "osName": environment.get("osName"),
        "osVersion": environment.get("osVersion"),
        "architecture": environment.get("architecture"),
        "javaVersion": environment.get("javaVersion"),
        "javaVendor": environment.get("javaVendor"),
        "vmName": environment.get("vmName"),
        "availableProcessors": environment.get("availableProcessors"),
        "maxHeapBytes": environment.get("maxHeapBytes"),
        "garbageCollectors": environment.get("garbageCollectors"),
        "jvmArguments": sorted(
            argument
            for argument in arguments
            if isinstance(argument, str) and not argument.startswith(IGNORED_JVM_ARGUMENT_PREFIX)
        ),
    }


def scenario_map(document: dict[str, Any], source: pathlib.Path) -> dict[str, dict[str, Any]]:
    mapped: dict[str, dict[str, Any]] = {}
    for raw_scenario in require_list(document["scenarios"], f"{source}: scenarios"):
        scenario = require_mapping(raw_scenario, f"{source}: scenario")
        name = scenario.get("name")
        if not isinstance(name, str) or not name:
            raise ValueError(f"{source}: scenario has invalid name {name!r}")
        if name in mapped:
            raise ValueError(f"{source}: duplicate scenario {name}")
        require_mapping(scenario.get("metadata"), f"{source}: {name}.metadata")
        samples = require_list(scenario.get("samples"), f"{source}: {name}.samples")
        if not samples:
            raise ValueError(f"{source}: scenario {name} has no samples")
        mapped[name] = scenario
    return mapped


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = min(1.0, max(0.0, fraction)) * (len(ordered) - 1)
    lower = math.floor(position)
    upper = min(len(ordered) - 1, lower + 1)
    remainder = position - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * remainder


def summarize(values: list[float]) -> dict[str, float]:
    if not values:
        raise ValueError("Cannot summarize an empty sample list")
    mean = statistics.fmean(values)
    standard_deviation = statistics.stdev(values) if len(values) > 1 else 0.0
    return {
        "mean": mean,
        "median": statistics.median(values),
        "p95": percentile(values, 0.95),
        "min": min(values),
        "max": max(values),
        "coefficientOfVariation": 0.0 if mean == 0.0 else standard_deviation / mean,
    }


def metric_samples(
    scenarios: list[dict[str, Any]],
    metric: str,
) -> list[float]:
    values: list[float] = []
    for scenario in scenarios:
        for raw_sample in require_list(scenario["samples"], f"{scenario['name']}.samples"):
            sample = require_mapping(raw_sample, f"{scenario['name']}.sample")
            value = sample.get(metric)
            if value is None:
                continue
            if not isinstance(value, (int, float)):
                raise ValueError(f"{scenario['name']}.{metric} must be numeric or null")
            values.append(float(value))
    return values


def summarize_metric(
    scenarios: list[dict[str, Any]],
    metric: str,
) -> dict[str, float] | None:
    values = metric_samples(scenarios, metric)
    return summarize(values) if values else None


def metric_samples_by_fork(
    scenarios: list[dict[str, Any]],
    metric: str,
) -> list[list[float]]:
    return [metric_samples([scenario], metric) for scenario in scenarios]


def delta_percent(feature: float | None, main: float | None) -> float | None:
    if feature is None or main is None or main == 0.0:
        return None
    return (feature - main) / main * 100.0


def metadata_subset(metadata: dict[str, Any], keys: tuple[str, ...]) -> dict[str, Any]:
    return {key: metadata.get(key) for key in keys}


def format_number(value: float | None) -> str:
    return "n/a" if value is None else f"{value:,.2f}"


def format_delta(value: float | None) -> str:
    return "n/a" if value is None else f"{value:+.2f}%"


def metric_median(summary: dict[str, float] | None) -> float | None:
    return summary["median"] if summary else None


def statistical_metric(
    name: str,
    metric: str,
    main_scenarios: list[dict[str, Any]],
    feature_scenarios: list[dict[str, Any]],
    main_summary: dict[str, float] | None,
    feature_summary: dict[str, float] | None,
    effect_threshold_percent: float,
    bootstrap_resamples: int,
) -> dict[str, Any]:
    delta = delta_percent(metric_median(feature_summary), metric_median(main_summary))
    interval = statistical_engine.hierarchical_bootstrap_median_delta(
        metric_samples_by_fork(main_scenarios, metric),
        metric_samples_by_fork(feature_scenarios, metric),
        bootstrap_resamples,
        f"profile:{name}:{metric}",
    )
    return {
        "medianDeltaPercent": delta,
        "bootstrap95Percent": list(interval) if interval else None,
        "classification": statistical_engine.classify(
            delta,
            interval,
            effect_threshold_percent,
            lower_is_better=True,
        ),
    }


def format_statistical_delta(metric: dict[str, Any]) -> str:
    delta = metric["medianDeltaPercent"]
    interval = metric["bootstrap95Percent"]
    if delta is None:
        return "n/a"
    if interval is None:
        return f"{delta:+.2f}%"
    return f"{delta:+.2f}% [{interval[0]:+.2f}%, {interval[1]:+.2f}%]"


def main() -> None:
    args = parse_args()
    if args.effect_threshold_percent < 0.0:
        raise ValueError("effect threshold must not be negative")
    if args.bootstrap_resamples <= 0:
        raise ValueError("bootstrap resamples must be positive")
    input_directory = args.input.resolve(strict=True)
    entries: list[dict[str, Any]] = []
    for path in sorted(input_directory.glob("*.json")):
        match = RESULT_NAME.match(path.name)
        if match:
            entries.append(
                {
                    "sequence": int(match.group("sequence")),
                    "branch": match.group("branch"),
                    "fork": int(match.group("fork")),
                    "path": path,
                    "sha256": sha256_file(path),
                    "document": load_result(path),
                },
            )
    entries.sort(key=lambda entry: entry["sequence"])
    if not entries:
        raise ValueError(f"No benchmark fork JSON files found in {input_directory}")
    expected_sequence = list(range(1, len(entries) + 1))
    if [entry["sequence"] for entry in entries] != expected_sequence:
        raise ValueError("Benchmark result sequence is not contiguous")

    by_branch = {
        branch: [entry for entry in entries if entry["branch"] == branch]
        for branch in ("feature", "main")
    }
    if not by_branch["feature"] or not by_branch["main"]:
        raise ValueError("Both feature and main fork results are required")
    if len(by_branch["feature"]) != len(by_branch["main"]):
        raise ValueError("Feature and main fork counts differ")

    expected_profile = by_branch["feature"][0]["document"]["profile"]
    expected_environment = normalized_environment(by_branch["feature"][0]["document"])
    identity_by_branch = {
        branch: {
            key: by_branch[branch][0]["document"].get(key)
            for key in ("adapter", "label", "revision", "dirty")
        }
        for branch in ("feature", "main")
    }
    expected_names: list[str] | None = None
    mapped_by_path: dict[pathlib.Path, dict[str, dict[str, Any]]] = {}
    contract_by_branch: dict[str, dict[str, tuple[Any, Any, Any]]] = {}
    for entry in entries:
        document = entry["document"]
        if document["profile"] != expected_profile:
            raise ValueError(f"Profile mismatch in {entry['path']}")
        if normalized_environment(document) != expected_environment:
            raise ValueError(f"Runtime environment mismatch in {entry['path']}")
        identity = {key: document.get(key) for key in ("adapter", "label", "revision", "dirty")}
        if identity != identity_by_branch[entry["branch"]]:
            raise ValueError(f"Branch identity mismatch in {entry['path']}")
        if str(document.get("fork")) != str(entry["fork"]):
            raise ValueError(f"Fork identity mismatch in {entry['path']}")
        mapped = scenario_map(document, entry["path"])
        names = list(mapped)
        if expected_names is None:
            expected_names = names
        elif names != expected_names:
            raise ValueError(f"Scenario order/set mismatch in {entry['path']}")
        contract = {
            name: (
                scenario.get("category"),
                scenario.get("description"),
                scenario.get("metadata"),
            )
            for name, scenario in mapped.items()
        }
        previous_contract = contract_by_branch.setdefault(entry["branch"], contract)
        if contract != previous_contract:
            raise ValueError(f"Scenario contract mismatch across forks in {entry['path']}")
        mapped_by_path[entry["path"]] = mapped

    assert expected_names is not None
    comparisons: list[dict[str, Any]] = []
    for name in expected_names:
        feature_scenarios = [
            mapped_by_path[entry["path"]][name]
            for entry in by_branch["feature"]
        ]
        main_scenarios = [
            mapped_by_path[entry["path"]][name]
            for entry in by_branch["main"]
        ]
        feature_reference = feature_scenarios[0]
        main_reference = main_scenarios[0]
        feature_metadata = require_mapping(feature_reference["metadata"], f"feature {name}")
        main_metadata = require_mapping(main_reference["metadata"], f"main {name}")
        feature_semantics = metadata_subset(feature_metadata, SEMANTIC_METADATA_KEYS)
        main_semantics = metadata_subset(main_metadata, SEMANTIC_METADATA_KEYS)
        if feature_semantics.get("comparisonContract") != COMPARISON_CONTRACT:
            raise ValueError(f"Feature {name} does not use {COMPARISON_CONTRACT}")
        if main_semantics.get("comparisonContract") != COMPARISON_CONTRACT:
            raise ValueError(f"Main {name} does not use {COMPARISON_CONTRACT}")
        semantic_differences = [
            key
            for key in SEMANTIC_METADATA_KEYS
            if feature_semantics.get(key) != main_semantics.get(key)
        ]
        if semantic_differences:
            raise ValueError(
                f"Logical fixture mismatch for {name}: {', '.join(semantic_differences)}",
            )

        feature_wall = summarize_metric(feature_scenarios, "wallNanosPerOperation")
        main_wall = summarize_metric(main_scenarios, "wallNanosPerOperation")
        feature_cpu = summarize_metric(feature_scenarios, "cpuNanosPerOperation")
        main_cpu = summarize_metric(main_scenarios, "cpuNanosPerOperation")
        feature_allocation = summarize_metric(feature_scenarios, "allocatedBytesPerOperation")
        main_allocation = summarize_metric(main_scenarios, "allocatedBytesPerOperation")
        if feature_wall is None or main_wall is None:
            raise ValueError(f"Missing wall time samples for {name}")

        statistics_by_metric = {
            "wallNanosPerOperation": statistical_metric(
                name,
                "wallNanosPerOperation",
                main_scenarios,
                feature_scenarios,
                main_wall,
                feature_wall,
                args.effect_threshold_percent,
                args.bootstrap_resamples,
            ),
            "cpuNanosPerOperation": statistical_metric(
                name,
                "cpuNanosPerOperation",
                main_scenarios,
                feature_scenarios,
                main_cpu,
                feature_cpu,
                args.effect_threshold_percent,
                args.bootstrap_resamples,
            ),
            "allocatedBytesPerOperation": statistical_metric(
                name,
                "allocatedBytesPerOperation",
                main_scenarios,
                feature_scenarios,
                main_allocation,
                feature_allocation,
                args.effect_threshold_percent,
                args.bootstrap_resamples,
            ),
        }
        comparisons.append(
            {
                "name": name,
                "category": feature_reference.get("category"),
                "semanticFixture": feature_semantics,
                "wireOutcome": {
                    "feature": metadata_subset(feature_metadata, WIRE_METADATA_KEYS),
                    "main": metadata_subset(main_metadata, WIRE_METADATA_KEYS),
                },
                "description": {
                    "feature": feature_reference.get("description"),
                    "main": main_reference.get("description"),
                },
                "rawForkSamples": {
                    "feature": [scenario["samples"] for scenario in feature_scenarios],
                    "main": [scenario["samples"] for scenario in main_scenarios],
                },
                "feature": {
                    "wallNanosPerOperation": feature_wall,
                    "cpuNanosPerOperation": feature_cpu,
                    "allocatedBytesPerOperation": feature_allocation,
                },
                "main": {
                    "wallNanosPerOperation": main_wall,
                    "cpuNanosPerOperation": main_cpu,
                    "allocatedBytesPerOperation": main_allocation,
                },
                "featureVsMain": {
                    "wallMedianDeltaPercent": delta_percent(
                        metric_median(feature_wall),
                        metric_median(main_wall),
                    ),
                    "cpuMedianDeltaPercent": delta_percent(
                        metric_median(feature_cpu),
                        metric_median(main_cpu),
                    ),
                    "allocationMedianDeltaPercent": delta_percent(
                        metric_median(feature_allocation),
                        metric_median(main_allocation),
                    ),
                },
                "statistics": statistics_by_metric,
            },
        )

    first_feature = by_branch["feature"][0]["document"]
    first_main = by_branch["main"][0]["document"]
    caveat = (
        "The logical fixtures match, but each branch measures its native wire contract: "
        "main legacy-v3 versus feature strict-v4. Deltas are end-to-end migration outcomes, "
        "not same-byte codec microbenchmarks; strict-v4 validation and canonical re-encoding "
        "are intentionally included. Payload bytes and SHA-256 values are outcomes, not inputs."
    )
    aggregate = {
        "schemaVersion": 1,
        "spdxFileCopyrightText": "2026 Vladislav Tomilov",
        "spdxLicenseIdentifier": "GPL-3.0-or-later",
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "suiteVersion": SUITE_VERSION,
        "comparisonContract": COMPARISON_CONTRACT,
        "effectThresholdPercent": args.effect_threshold_percent,
        "bootstrapResamples": args.bootstrap_resamples,
        "profile": expected_profile,
        "environment": expected_environment,
        "feature": {
            "adapter": first_feature.get("adapter"),
            "label": first_feature.get("label"),
            "revision": first_feature.get("revision"),
            "dirty": first_feature.get("dirty"),
            "forks": len(by_branch["feature"]),
        },
        "main": {
            "adapter": first_main.get("adapter"),
            "label": first_main.get("label"),
            "revision": first_main.get("revision"),
            "dirty": first_main.get("dirty"),
            "forks": len(by_branch["main"]),
        },
        "runOrder": [
            {
                "sequence": entry["sequence"],
                "branch": entry["branch"],
                "fork": entry["fork"],
                "file": entry["path"].name,
                "sha256": entry["sha256"],
            }
            for entry in entries
        ],
        "deltaConvention": "positive featureVsMain values mean feature is slower or allocates more",
        "comparisonCaveat": caveat,
        "scenarios": comparisons,
    }

    output_json = args.output_json.resolve()
    output_markdown = args.output_markdown.resolve()
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_markdown.parent.mkdir(parents=True, exist_ok=True)
    with output_json.open("w", encoding="utf-8") as stream:
        json.dump(aggregate, stream, ensure_ascii=False, indent=2, sort_keys=False)
        stream.write("\n")

    lines = [
        "<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->",
        "<!-- SPDX-License-Identifier: GPL-3.0-or-later -->",
        "",
        "# Branch-native profile codec comparison",
        "",
        f"- Feature: `{aggregate['feature']['label']}` at `{aggregate['feature']['revision']}`",
        f"- Main: `{aggregate['main']['label']}` at `{aggregate['main']['revision']}`",
        f"- Profile: `{expected_profile['name']}`; forks per branch: {len(by_branch['feature'])}",
        f"- Effect threshold: {args.effect_threshold_percent:.2f}%; hierarchical bootstrap: "
        f"{args.bootstrap_resamples:,} resamples.",
        "- Delta convention: positive means feature is slower or allocates more.",
        f"- Comparison caveat: {caveat}",
        "",
        "| Scenario | Feature wire/bytes | Main wire/bytes | Feature ns/op | Main ns/op | "
        "Wall delta (95% bootstrap) | Verdict | Feature B/op | Main B/op | "
        "Allocation delta (95% bootstrap) | Verdict |",
        "|---|---:|---:|---:|---:|---:|---|---:|---:|---:|---|",
    ]
    for comparison in comparisons:
        feature_wire = comparison["wireOutcome"]["feature"]
        main_wire = comparison["wireOutcome"]["main"]
        feature_wall = comparison["feature"]["wallNanosPerOperation"]
        main_wall = comparison["main"]["wallNanosPerOperation"]
        feature_allocation = comparison["feature"]["allocatedBytesPerOperation"]
        main_allocation = comparison["main"]["allocatedBytesPerOperation"]
        lines.append(
            "| "
            + " | ".join(
                (
                    f"`{comparison['name']}`",
                    f"{feature_wire['wireFormat']} / {feature_wire['payloadBytes']}",
                    f"{main_wire['wireFormat']} / {main_wire['payloadBytes']}",
                    format_number(metric_median(feature_wall)),
                    format_number(metric_median(main_wall)),
                    format_statistical_delta(comparison["statistics"]["wallNanosPerOperation"]),
                    comparison["statistics"]["wallNanosPerOperation"]["classification"],
                    format_number(metric_median(feature_allocation)),
                    format_number(metric_median(main_allocation)),
                    format_statistical_delta(
                        comparison["statistics"]["allocatedBytesPerOperation"],
                    ),
                    comparison["statistics"]["allocatedBytesPerOperation"]["classification"],
                ),
            )
            + " |",
        )
    lines.extend(
        (
            "",
            "The aggregate JSON preserves every raw fork sample, input-file SHA-256, and full "
            "wire SHA-256 outcome, so the result remains auditable after local build output is removed. "
            "Medians combine all "
            "measurement samples from equally sized, interleaved branch fork sets.",
            "",
        ),
    )
    output_markdown.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote aggregate JSON: {output_json}")
    print(f"Wrote comparison report: {output_markdown}")


if __name__ == "__main__":
    main()
