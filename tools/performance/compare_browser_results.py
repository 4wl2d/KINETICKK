#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Compare two KINETICKK browser/Wasm benchmark result documents."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import random
import re
import statistics
from typing import Any


SUITE = "kinetickk-browser-wasm"
SUPPORTED_SCHEMA_VERSIONS = {1, 2, 3}
HIGHER_IS_BETTER = {
    "frameMeasurement.statistics.framesPerSecond",
    "frameMeasurement.statistics.onePercentLowFramesPerSecond",
}


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, type=pathlib.Path)
    parser.add_argument("--candidate", required=True, type=pathlib.Path)
    parser.add_argument("--output-json", required=True, type=pathlib.Path)
    parser.add_argument("--output-markdown", required=True, type=pathlib.Path)
    parser.add_argument("--effect-threshold-percent", type=float, default=5.0)
    parser.add_argument("--bootstrap-resamples", type=int, default=10_000)
    return parser.parse_args()


def load(path: pathlib.Path) -> dict[str, Any]:
    raw = path.read_bytes()
    document = json.loads(raw.decode("utf-8"))
    if document.get("schemaVersion") not in SUPPORTED_SCHEMA_VERSIONS or document.get("suite") != SUITE:
        raise ValueError(f"unsupported browser result: {path}")
    if document.get("status") != "ok":
        raise ValueError(f"browser result is not successful: {path}")
    if document.get("schemaVersion") == 3:
        source = document.get("source")
        if not isinstance(source, dict):
            raise ValueError(f"browser schema v3 source must be an object: {path}")
        revision = source.get("revision")
        if not isinstance(revision, str) or re.fullmatch(
            r"[0-9a-f]{40}|[0-9a-f]{64}",
            revision,
        ) is None:
            raise ValueError(f"browser schema v3 revision must be a full Git object ID: {path}")
        source_tree_sha256 = source.get("sourceTreeSha256")
        if not isinstance(source_tree_sha256, str) or re.fullmatch(
            r"[0-9a-f]{64}",
            source_tree_sha256,
        ) is None:
            raise ValueError(f"browser schema v3 sourceTreeSha256 is invalid: {path}")
        if not isinstance(source.get("dirty"), bool):
            raise ValueError(f"browser schema v3 dirty must be boolean: {path}")
        if source.get("repository") != f"repository/{revision}":
            raise ValueError(f"browser schema v3 repository identity is invalid: {path}")
        environment = document.get("runnerEnvironment")
        if not isinstance(environment, dict):
            raise ValueError(f"browser schema v3 runnerEnvironment must be an object: {path}")
        tool_source = environment.get("toolSource")
        playwright = environment.get("playwrightCli")
        probe = environment.get("probe")
        if not all(isinstance(value, dict) for value in (tool_source, playwright, probe)):
            raise ValueError(f"browser schema v3 tool provenance is incomplete: {path}")
        tool_revision = tool_source.get("repositoryRevision")
        tool_tree = tool_source.get("repositorySourceTreeSha256")
        if (
            not isinstance(tool_revision, str)
            or re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", tool_revision) is None
            or not isinstance(tool_source.get("repositoryDirty"), bool)
            or not isinstance(tool_tree, str)
            or re.fullmatch(r"[0-9a-f]{64}", tool_tree) is None
        ):
            raise ValueError(f"browser schema v3 tool source provenance is invalid: {path}")
        if (
            playwright.get("kind") != "repository-wrapper"
            or playwright.get("repositoryRevision") != tool_revision
            or playwright.get("repositoryDirty") != tool_source.get("repositoryDirty")
            or playwright.get("repositorySourceTreeSha256") != tool_tree
        ):
            raise ValueError(f"browser schema v3 wrapper provenance disagrees with tool source: {path}")
        for value, context in (
            (playwright.get("sha256"), "wrapper"),
            (probe.get("sha256"), "probe"),
        ):
            if not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{64}", value) is None:
                raise ValueError(f"browser schema v3 {context} SHA-256 is invalid: {path}")
    document["_inputSource"] = {
        "file": path.name,
        "sha256": hashlib.sha256(raw).hexdigest(),
    }
    return document


def source_identity(document: dict[str, Any]) -> dict[str, Any]:
    source = document["source"]
    return {
        key: source.get(key)
        for key in ("label", "revision", "branch", "dirty", "sourceTreeSha256")
    } | {"inputSource": document.get("_inputSource")}


def normalized_protocol(document: dict[str, Any]) -> dict[str, Any]:
    protocol = dict(document["protocol"])
    protocol.pop("targetUrl", None)
    return protocol


def normalized_environment(document: dict[str, Any]) -> dict[str, Any]:
    environment = dict(document["runnerEnvironment"])
    for key in ("sessionRoot", "probeFile", "playwrightCliPath"):
        environment.pop(key, None)
    return environment


def browser_versions(document: dict[str, Any]) -> list[str]:
    return sorted(
        {
            str(fork["probe"]["browser"]["version"])
            for fork in document["forks"]
            if fork.get("status") == "ok"
        },
    )


def percentile(values: list[float], quantile: float) -> float:
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = quantile * (len(ordered) - 1)
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    fraction = position - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * fraction


def delta_percent(candidate: float, baseline: float) -> float | None:
    if baseline == 0.0:
        return 0.0 if candidate == 0.0 else None
    return (candidate - baseline) / baseline * 100.0


def bootstrap_interval(
    name: str,
    baseline: list[float],
    candidate: list[float],
    resamples: int,
) -> tuple[float, float] | None:
    if not baseline or not candidate or statistics.median(baseline) == 0.0:
        return None
    seed = int.from_bytes(hashlib.sha256(name.encode("utf-8")).digest()[:8], "big")
    generator = random.Random(seed)
    deltas: list[float] = []
    for _ in range(resamples):
        baseline_median = statistics.median(generator.choices(baseline, k=len(baseline)))
        candidate_median = statistics.median(generator.choices(candidate, k=len(candidate)))
        delta = delta_percent(candidate_median, baseline_median)
        if delta is not None:
            deltas.append(delta)
    return (percentile(deltas, 0.025), percentile(deltas, 0.975)) if deltas else None


def classify(
    name: str,
    point: float | None,
    interval: tuple[float, float] | None,
    threshold: float,
    baseline: float | None = None,
    candidate: float | None = None,
) -> str:
    if baseline == 0.0 and candidate is not None:
        if candidate == 0.0:
            return "stable"
        candidate_is_worse = candidate < 0.0 if name in HIGHER_IS_BETTER else candidate > 0.0
        return "regression" if candidate_is_worse else "improvement"
    if point is None:
        return "diagnostic"
    if abs(point) < threshold:
        return "stable"
    if interval is None or interval[0] <= 0.0 <= interval[1]:
        return "inconclusive"
    candidate_is_worse = point < 0.0 if name in HIGHER_IS_BETTER else point > 0.0
    return "regression" if candidate_is_worse else "improvement"


def format_value(value: float, unit: str) -> str:
    if unit == "bytes":
        if abs(value) >= 1024 * 1024:
            return f"{value / 1024 / 1024:.2f} MiB"
        if abs(value) >= 1024:
            return f"{value / 1024:.2f} KiB"
        return f"{value:.0f} B"
    if unit == "ms":
        return f"{value:.2f} ms"
    if unit == "seconds":
        return f"{value:.3f} s"
    if unit == "frames/s":
        return f"{value:.3f} FPS"
    if unit == "ratio":
        return f"{value * 100:.2f}%"
    return f"{value:.3f} {unit}".rstrip()


def compare(
    baseline: dict[str, Any],
    candidate: dict[str, Any],
    threshold: float,
    resamples: int,
) -> dict[str, Any]:
    if baseline["schemaVersion"] != candidate["schemaVersion"]:
        raise ValueError("browser result schema versions differ")
    if normalized_protocol(baseline) != normalized_protocol(candidate):
        raise ValueError("browser benchmark protocols differ")
    if normalized_environment(baseline) != normalized_environment(candidate):
        raise ValueError("browser runner environments differ")
    if browser_versions(baseline) != browser_versions(candidate):
        raise ValueError("browser versions differ")
    baseline_metrics = baseline["summary"]["metrics"]
    candidate_metrics = candidate["summary"]["metrics"]
    if set(baseline_metrics) != set(candidate_metrics):
        raise ValueError("browser metric sets differ")

    metrics: list[dict[str, Any]] = []
    for name in sorted(baseline_metrics):
        baseline_metric = baseline_metrics[name]
        candidate_metric = candidate_metrics[name]
        if baseline_metric["unit"] != candidate_metric["unit"]:
            raise ValueError(f"metric unit differs: {name}")
        baseline_samples = [float(value) for value in baseline_metric["rawSamples"]]
        candidate_samples = [float(value) for value in candidate_metric["rawSamples"]]
        baseline_median = statistics.median(baseline_samples)
        candidate_median = statistics.median(candidate_samples)
        point = delta_percent(candidate_median, baseline_median)
        interval = bootstrap_interval(name, baseline_samples, candidate_samples, resamples)
        metrics.append(
            {
                "name": name,
                "unit": baseline_metric["unit"],
                "direction": "higher-is-better" if name in HIGHER_IS_BETTER else "lower-is-better",
                "baselineMedian": baseline_median,
                "candidateMedian": candidate_median,
                "absoluteDelta": candidate_median - baseline_median,
                "zeroBaselineTransition": baseline_median == 0.0 and candidate_median != 0.0,
                "deltaPercent": point,
                "bootstrap95Percent": list(interval) if interval else None,
                "classification": classify(
                    name,
                    point,
                    interval,
                    threshold,
                    baseline=baseline_median,
                    candidate=candidate_median,
                ),
            },
        )
    return {
        "schemaVersion": 1,
        "spdxFileCopyrightText": "2026 Vladislav Tomilov",
        "spdxLicenseIdentifier": "GPL-3.0-or-later",
        "suite": "kinetickk-browser-comparison",
        "inputSchemaVersion": baseline["schemaVersion"],
        "baseline": source_identity(baseline),
        "candidate": source_identity(candidate),
        "protocol": normalized_protocol(baseline),
        "browserVersions": browser_versions(baseline),
        "effectThresholdPercent": threshold,
        "bootstrapResamples": resamples,
        "diagnostics": {
            "baseline": baseline["summary"]["diagnosticTotals"],
            "candidate": candidate["summary"]["diagnosticTotals"],
        },
        "metrics": metrics,
    }


def render_markdown(report: dict[str, Any]) -> str:
    counts: dict[str, int] = {}
    for metric in report["metrics"]:
        verdict = metric["classification"]
        counts[verdict] = counts.get(verdict, 0) + 1
    lines = [
        "<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->",
        "<!-- SPDX-License-Identifier: GPL-3.0-or-later -->",
        "",
        "# Browser/Wasm performance comparison",
        "",
        f"Baseline: `{report['baseline']['label']}` at `{report['baseline']['revision']}`.",
        "",
        f"Candidate: `{report['candidate']['label']}` at `{report['candidate']['revision']}`.",
        "",
        (
            f"Pinned Chromium `{', '.join(report['browserVersions'])}`; "
            f"{report['protocol']['forks']} isolated fresh profiles; "
            f"{report['protocol']['warmupFrames']} warmup + "
            f"{report['protocol']['measureFrames']} measured rAF intervals per fork."
        ),
        "",
        "Outcome: " + ", ".join(f"{key} **{value}**" for key, value in sorted(counts.items())) + ".",
        "",
        (
            f"A verdict needs a {report['effectThresholdPercent']:.2f}% effect and a bootstrap "
            "interval excluding zero. Localhost "
            "navigation and idle rAF are browser-health measurements, not an interactive gameplay trace."
        ),
        "",
        "| Metric | Baseline | Candidate | Delta (95% bootstrap) | Verdict |",
        "|---|---:|---:|---:|---|",
    ]
    for metric in report["metrics"]:
        interval = metric["bootstrap95Percent"]
        if metric["zeroBaselineTransition"]:
            delta = "new " + format_value(metric["absoluteDelta"], metric["unit"])
        elif metric["deltaPercent"] is None:
            delta = "n/a"
        elif interval is None:
            delta = f"{metric['deltaPercent']:+.2f}%"
        else:
            delta = (
                f"{metric['deltaPercent']:+.2f}% "
                f"[{interval[0]:+.2f}%, {interval[1]:+.2f}%]"
            )
        lines.append(
            "| `{name}` | {baseline} | {candidate} | {delta} | **{verdict}** |".format(
                name=metric["name"],
                baseline=format_value(metric["baselineMedian"], metric["unit"]),
                candidate=format_value(metric["candidateMedian"], metric["unit"]),
                delta=delta,
                verdict=metric["classification"],
            ),
        )
    lines.extend(
        [
            "",
            "## Diagnostics",
            "",
            f"Baseline: `{json.dumps(report['diagnostics']['baseline'], sort_keys=True)}`",
            "",
            f"Candidate: `{json.dumps(report['diagnostics']['candidate'], sort_keys=True)}`",
            "",
        ],
    )
    return "\n".join(lines)


def main() -> int:
    arguments = parse_arguments()
    try:
        report = compare(
            load(arguments.baseline),
            load(arguments.candidate),
            arguments.effect_threshold_percent,
            arguments.bootstrap_resamples,
        )
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"browser comparison failed: {error}")
        return 2
    arguments.output_json.parent.mkdir(parents=True, exist_ok=True)
    arguments.output_markdown.parent.mkdir(parents=True, exist_ok=True)
    arguments.output_json.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    arguments.output_markdown.write_text(render_markdown(report), encoding="utf-8")
    print(f"Wrote {arguments.output_json.resolve()}")
    print(f"Wrote {arguments.output_markdown.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
