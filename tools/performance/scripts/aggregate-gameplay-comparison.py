#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Aggregate interleaved KINETICKK benchmark forks without third-party dependencies."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import pathlib
import re
import statistics
from typing import Any


RESULT_NAME = re.compile(r"^(?P<sequence>[0-9]+)-(?P<branch>feature|main)-fork-(?P<fork>[0-9]+)\.json$")
IGNORED_JVM_ARGUMENT_PREFIX = "-Dkinetickk.benchmark."


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=pathlib.Path)
    parser.add_argument("--output-json", required=True, type=pathlib.Path)
    parser.add_argument("--output-markdown", required=True, type=pathlib.Path)
    parser.add_argument(
        "--baseline-kind",
        choices=("literal-main", "origin-main"),
        default="literal-main",
    )
    return parser.parse_args()


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


def summarize_optional(values: list[float | None]) -> dict[str, float] | None:
    present = [value for value in values if value is not None]
    return summarize(present) if present else None


def delta_percent(feature: float | None, main: float | None) -> float | None:
    if feature is None or main is None or main == 0.0:
        return None
    return (feature - main) / main * 100.0


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
    if document.get("suiteVersion") != "gameplay-core-v1":
        raise ValueError(f"Unexpected suiteVersion in {path}: {document.get('suiteVersion')!r}")
    require_mapping(document.get("profile"), f"{path}: profile")
    require_mapping(document.get("environment"), f"{path}: environment")
    require_list(document.get("scenarios"), f"{path}: scenarios")
    return document


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


def metric_samples(scenarios: list[dict[str, Any]], metric: str) -> list[float | None]:
    values: list[float | None] = []
    for scenario in scenarios:
        for raw_sample in require_list(scenario["samples"], f"{scenario['name']}.samples"):
            sample = require_mapping(raw_sample, f"{scenario['name']}.sample")
            value = sample.get(metric)
            if value is not None and not isinstance(value, (int, float)):
                raise ValueError(f"{scenario['name']}.{metric} must be numeric or null")
            values.append(float(value) if value is not None else None)
    return values


def format_number(value: float | None) -> str:
    return "n/a" if value is None else f"{value:,.2f}"


def format_delta(value: float | None) -> str:
    return "n/a" if value is None else f"{value:+.2f}%"


def main() -> None:
    args = parse_args()
    input_directory = args.input.resolve(strict=True)
    result_entries: list[dict[str, Any]] = []
    for path in sorted(input_directory.glob("*.json")):
        match = RESULT_NAME.match(path.name)
        if not match:
            continue
        result_entries.append(
            {
                "sequence": int(match.group("sequence")),
                "branch": match.group("branch"),
                "fork": int(match.group("fork")),
                "path": path,
                "document": load_result(path),
            },
        )
    result_entries.sort(key=lambda entry: entry["sequence"])
    if not result_entries:
        raise ValueError(f"No benchmark fork JSON files found in {input_directory}")
    if [entry["sequence"] for entry in result_entries] != list(range(1, len(result_entries) + 1)):
        raise ValueError("Benchmark result sequence is not contiguous")

    by_branch = {
        branch: [entry for entry in result_entries if entry["branch"] == branch]
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
    mapped_by_entry: dict[pathlib.Path, dict[str, dict[str, Any]]] = {}
    contract_by_branch: dict[str, dict[str, tuple[Any, Any, Any]]] = {}
    for entry in result_entries:
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
        mapped_by_entry[entry["path"]] = mapped

    assert expected_names is not None
    comparisons: list[dict[str, Any]] = []
    for name in expected_names:
        feature_scenarios = [mapped_by_entry[entry["path"]][name] for entry in by_branch["feature"]]
        main_scenarios = [mapped_by_entry[entry["path"]][name] for entry in by_branch["main"]]
        feature_reference = feature_scenarios[0]
        main_reference = main_scenarios[0]
        compatibility_differences = []
        for field in ("category", "description", "metadata"):
            if feature_reference.get(field) != main_reference.get(field):
                compatibility_differences.append(field)

        feature_wall = summarize(
            [
                value
                for value in metric_samples(feature_scenarios, "wallNanosPerOperation")
                if value is not None
            ],
        )
        main_wall = summarize(
            [
                value
                for value in metric_samples(main_scenarios, "wallNanosPerOperation")
                if value is not None
            ],
        )
        feature_allocation = summarize_optional(
            metric_samples(feature_scenarios, "allocatedBytesPerOperation"),
        )
        main_allocation = summarize_optional(
            metric_samples(main_scenarios, "allocatedBytesPerOperation"),
        )
        comparable = not compatibility_differences
        comparisons.append(
            {
                "name": name,
                "category": feature_reference.get("category"),
                "description": feature_reference.get("description"),
                "metadata": {
                    "feature": feature_reference.get("metadata"),
                    "main": main_reference.get("metadata"),
                },
                "comparable": comparable,
                "compatibilityDifferences": compatibility_differences,
                "feature": {
                    "wallNanosPerOperation": feature_wall,
                    "allocatedBytesPerOperation": feature_allocation,
                },
                "main": {
                    "wallNanosPerOperation": main_wall,
                    "allocatedBytesPerOperation": main_allocation,
                },
                "featureVsMain": {
                    "wallMedianDeltaPercent": delta_percent(
                        feature_wall["median"] if comparable else None,
                        main_wall["median"] if comparable else None,
                    ),
                    "allocationMedianDeltaPercent": delta_percent(
                        feature_allocation["median"] if comparable and feature_allocation else None,
                        main_allocation["median"] if comparable and main_allocation else None,
                    ),
                },
            },
        )

    first_feature = by_branch["feature"][0]["document"]
    first_main = by_branch["main"][0]["document"]
    aggregate = {
        "schemaVersion": 1,
        "spdxFileCopyrightText": "2026 Vladislav Tomilov",
        "spdxLicenseIdentifier": "GPL-3.0-or-later",
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "suiteVersion": "gameplay-core-v1",
        "baselineKind": args.baseline_kind,
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
            }
            for entry in result_entries
        ],
        "deltaConvention": "positive featureVsMain values mean feature is slower or allocates more",
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
        "# Gameplay performance comparison",
        "",
        f"- Feature: `{aggregate['feature']['label']}` at `{aggregate['feature']['revision']}`",
        f"- Baseline ({args.baseline_kind}): `{aggregate['main']['label']}` at "
        f"`{aggregate['main']['revision']}`",
        f"- Profile: `{expected_profile['name']}`; forks per branch: {len(by_branch['feature'])}",
        "- Delta convention: positive means feature is slower or allocates more.",
        "",
        "| Scenario | Feature ns/op | Baseline ns/op | Wall delta | Feature B/op | Baseline B/op | Allocation delta |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for comparison in comparisons:
        feature_wall_median = comparison["feature"]["wallNanosPerOperation"]["median"]
        main_wall_median = comparison["main"]["wallNanosPerOperation"]["median"]
        feature_allocation_summary = comparison["feature"]["allocatedBytesPerOperation"]
        main_allocation_summary = comparison["main"]["allocatedBytesPerOperation"]
        lines.append(
            "| "
            + " | ".join(
                (
                    f"`{comparison['name']}`" + (" ⚠" if not comparison["comparable"] else ""),
                    format_number(feature_wall_median),
                    format_number(main_wall_median),
                    format_delta(comparison["featureVsMain"]["wallMedianDeltaPercent"]),
                    format_number(
                        feature_allocation_summary["median"] if feature_allocation_summary else None,
                    ),
                    format_number(main_allocation_summary["median"] if main_allocation_summary else None),
                    format_delta(comparison["featureVsMain"]["allocationMedianDeltaPercent"]),
                ),
            )
            + " |",
        )
    incompatible = [comparison for comparison in comparisons if not comparison["comparable"]]
    if incompatible:
        lines.extend(("", "## Incompatible scenario metadata", ""))
        for comparison in incompatible:
            lines.append(
                f"- `{comparison['name']}`: "
                + ", ".join(comparison["compatibilityDifferences"]),
            )
    lines.extend(
        (
            "",
            "Raw fork samples remain in this directory. Medians above combine all measurement "
            "samples from equally sized, interleaved branch fork sets.",
            "",
        ),
    )
    output_markdown.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote aggregate JSON: {output_json}")
    print(f"Wrote comparison report: {output_markdown}")


if __name__ == "__main__":
    main()
