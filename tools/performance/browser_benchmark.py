#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Measure KINETICKK browser/Wasm navigation, rendering, and runtime health.

The runner deliberately uses Playwright CLI rather than ``@playwright/test``.  Every
fork receives a unique CLI session, working directory, and previously nonexistent
persistent browser profile.  The first target navigation is therefore cold; the
second navigation uses the same isolated profile and represents a warm cache.
"""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import math
import os
import pathlib
import platform
import secrets
import shutil
import statistics
import subprocess
import sys
import time
import urllib.parse
from typing import Any, Iterable

import source_provenance


SCHEMA_VERSION = 3
PROBE_SCHEMA_VERSION = 2
SUITE_NAME = "kinetickk-browser-wasm"
DEFAULT_VIEWPORT = (1280, 720)
DEFAULT_WARMUP_FRAMES = 120
DEFAULT_MEASURE_FRAMES = 600
DEFAULT_FORKS = 5
PINNED_PLAYWRIGHT_CLI_PACKAGE = "@playwright/cli"
PINNED_PLAYWRIGHT_CLI_VERSION = "0.1.18"
PINNED_PLAYWRIGHT_CLI_SPEC = (
    f"{PINNED_PLAYWRIGHT_CLI_PACKAGE}@{PINNED_PLAYWRIGHT_CLI_VERSION}"
)


def logical_repository_namespace(revision: str) -> str:
    if not revision or revision in {".", ".."} or any(
        character not in "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-"
        for character in revision
    ):
        raise ValueError("revision must be a non-empty path-safe source identity")
    return f"repository/{revision}"


def repository_wrapper_provenance(
    pwcli: pathlib.Path,
    tool_repository: pathlib.Path,
    reported_version: str,
    source_state: source_provenance.SourceState | None = None,
) -> dict[str, Any]:
    expected_wrapper = tool_repository / "tools/performance/playwright_cli.sh"
    if pwcli.resolve() != expected_wrapper.resolve():
        raise RuntimeError(
            "browser evidence requires the versioned repository Playwright wrapper: "
            "tools/performance/playwright_cli.sh",
        )
    wrapper_bytes = pwcli.read_bytes()
    if PINNED_PLAYWRIGHT_CLI_SPEC.encode("utf-8") not in wrapper_bytes:
        raise RuntimeError(
            f"repository Playwright wrapper does not pin {PINNED_PLAYWRIGHT_CLI_SPEC}",
        )
    attested_source = source_state or source_provenance.capture_source_state(tool_repository)
    return {
        "kind": "repository-wrapper",
        "path": "tools/performance/playwright_cli.sh",
        "sha256": hashlib.sha256(wrapper_bytes).hexdigest(),
        "repositoryRevision": attested_source.revision,
        "repositoryDirty": attested_source.dirty,
        "repositorySourceTreeSha256": attested_source.source_tree_sha256,
        "package": PINNED_PLAYWRIGHT_CLI_PACKAGE,
        "packageVersion": PINNED_PLAYWRIGHT_CLI_VERSION,
        "reportedVersion": reported_version,
    }


def capture_browser_source_states(
    repository: pathlib.Path,
    tool_repository: pathlib.Path,
) -> tuple[source_provenance.SourceState, source_provenance.SourceState]:
    """Attest the measured checkout independently from the benchmark harness checkout."""
    target_source = source_provenance.capture_source_state(repository)
    tool_source = (
        target_source
        if tool_repository == repository
        else source_provenance.capture_source_state(tool_repository)
    )
    return target_source, tool_source


def validate_browser_evidence_path(
    *,
    repository: pathlib.Path,
    tool_repository: pathlib.Path,
    path: pathlib.Path,
    allow_existing: bool = False,
) -> None:
    """Apply output safety rules for every attested worktree containing the path."""
    source_provenance.validate_output_path(
        repository=repository,
        output=path,
        allow_existing=allow_existing,
    )
    if tool_repository != repository:
        source_provenance.validate_output_path(
            repository=tool_repository,
            output=path,
            allow_existing=allow_existing,
        )


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def parse_positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def parse_non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must not be negative")
    return parsed


def parse_viewport(value: str) -> tuple[int, int]:
    normalized = value.lower().replace("×", "x")
    try:
        width_text, height_text = normalized.split("x", maxsplit=1)
        width = int(width_text)
        height = int(height_text)
    except (ValueError, TypeError) as error:
        raise argparse.ArgumentTypeError("expected WIDTHxHEIGHT, for example 1280x720") from error
    if width <= 0 or height <= 0:
        raise argparse.ArgumentTypeError("viewport dimensions must be greater than zero")
    return width, height


def parse_boolean_mode(value: str) -> str:
    normalized = value.lower()
    if normalized not in {"auto", "true", "false"}:
        raise argparse.ArgumentTypeError("expected auto, true, or false")
    return normalized


def percentile(values: Iterable[float], quantile: float) -> float | None:
    ordered = sorted(float(value) for value in values)
    if not ordered:
        return None
    if len(ordered) == 1:
        return ordered[0]
    index = (len(ordered) - 1) * quantile
    lower = math.floor(index)
    upper = math.ceil(index)
    if lower == upper:
        return ordered[lower]
    fraction = index - lower
    return ordered[lower] * (1.0 - fraction) + ordered[upper] * fraction


def sample_statistics(values: Iterable[float]) -> dict[str, Any]:
    raw = [float(value) for value in values if isinstance(value, (int, float)) and math.isfinite(value)]
    if not raw:
        return {
            "sampleCount": 0,
            "rawSamples": [],
            "minimum": None,
            "maximum": None,
            "mean": None,
            "median": None,
            "p95": None,
            "p99": None,
            "standardDeviation": None,
            "coefficientOfVariation": None,
        }
    mean = statistics.fmean(raw)
    standard_deviation = statistics.stdev(raw) if len(raw) > 1 else 0.0
    return {
        "sampleCount": len(raw),
        "rawSamples": raw,
        "minimum": min(raw),
        "maximum": max(raw),
        "mean": mean,
        "median": statistics.median(raw),
        "p95": percentile(raw, 0.95),
        "p99": percentile(raw, 0.99),
        "standardDeviation": standard_deviation,
        "coefficientOfVariation": standard_deviation / mean if mean else None,
    }


def deep_get(value: dict[str, Any], dotted_path: str) -> Any:
    current: Any = value
    for component in dotted_path.split("."):
        if not isinstance(current, dict) or component not in current:
            return None
        current = current[component]
    return current


def git_output(repository: pathlib.Path, *arguments: str) -> str | None:
    try:
        result = subprocess.run(
            ["git", *arguments],
            cwd=repository,
            check=True,
            capture_output=True,
            text=True,
            timeout=30,
        )
    except (FileNotFoundError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return None
    return result.stdout.strip()


def resolve_dirty(mode: str, repository: pathlib.Path) -> bool | None:
    if mode == "true":
        return True
    if mode == "false":
        return False
    status = git_output(repository, "status", "--porcelain")
    return bool(status) if status is not None else None


def resolve_pwcli(explicit_path: str | None) -> pathlib.Path:
    candidates: list[pathlib.Path] = []
    if explicit_path:
        candidates.append(pathlib.Path(explicit_path).expanduser())
    if os.environ.get("PWCLI"):
        candidates.append(pathlib.Path(os.environ["PWCLI"]).expanduser())
    candidates.append(pathlib.Path(__file__).resolve().with_name("playwright_cli.sh"))
    codex_root = pathlib.Path(os.environ.get("CODEX_HOME", pathlib.Path.home() / ".codex"))
    candidates.append(codex_root / "skills" / "playwright" / "scripts" / "playwright_cli.sh")
    global_cli = shutil.which("playwright-cli")
    if global_cli:
        candidates.append(pathlib.Path(global_cli))

    for candidate in candidates:
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return candidate.resolve()
    searched = ", ".join(str(candidate) for candidate in candidates)
    raise RuntimeError(f"Playwright CLI wrapper was not found or executable; searched: {searched}")


def command_result(
    command: list[str],
    *,
    working_directory: pathlib.Path,
    timeout_seconds: float,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=working_directory,
        check=False,
        capture_output=True,
        text=True,
        timeout=timeout_seconds,
        env=os.environ.copy(),
    )


def write_command_logs(
    directory: pathlib.Path,
    stem: str,
    result: subprocess.CompletedProcess[str],
) -> None:
    (directory / f"{stem}.stdout.log").write_text(result.stdout, encoding="utf-8")
    (directory / f"{stem}.stderr.log").write_text(result.stderr, encoding="utf-8")


def require_success(
    result: subprocess.CompletedProcess[str],
    description: str,
    command: list[str],
) -> None:
    if result.returncode == 0:
        return
    stdout_tail = result.stdout[-4_000:].strip()
    stderr_tail = result.stderr[-4_000:].strip()
    raise RuntimeError(
        f"{description} failed with exit code {result.returncode}\n"
        f"command: {' '.join(command)}\n"
        f"stdout:\n{stdout_tail}\n"
        f"stderr:\n{stderr_tail}"
    )


def find_probe_result(value: Any) -> dict[str, Any] | None:
    if isinstance(value, dict):
        if value.get("schemaVersion") == PROBE_SCHEMA_VERSION and "coldNavigation" in value:
            return value
        for nested in value.values():
            found = find_probe_result(nested)
            if found is not None:
                return found
    elif isinstance(value, list):
        for nested in value:
            found = find_probe_result(nested)
            if found is not None:
                return found
    elif isinstance(value, str):
        try:
            decoded = json.loads(value)
        except json.JSONDecodeError:
            return extract_json_payload(value, allow_failure=True)
        return find_probe_result(decoded)
    return None


def extract_json_payload(output: str, *, allow_failure: bool = False) -> dict[str, Any] | None:
    stripped = output.strip()
    if stripped:
        try:
            parsed = json.loads(stripped)
        except json.JSONDecodeError:
            parsed = None
        if parsed is not None:
            found = find_probe_result(parsed)
            if found is not None:
                return found

    decoder = json.JSONDecoder()
    position = 0
    while position < len(output):
        opening = output.find("{", position)
        if opening < 0:
            break
        try:
            parsed, consumed = decoder.raw_decode(output[opening:])
        except json.JSONDecodeError:
            position = opening + 1
            continue
        found = find_probe_result(parsed)
        if found is not None:
            return found
        position = opening + max(consumed, 1)

    if allow_failure:
        return None
    raise RuntimeError(f"Playwright CLI did not return a browser probe JSON object:\n{output[-8_000:]}")


def encode_bootstrap_configuration(configuration: dict[str, Any]) -> str:
    serialized = json.dumps(configuration, sort_keys=True, separators=(",", ":")).encode("utf-8")
    encoded = base64.urlsafe_b64encode(serialized).decode("ascii")
    return f"about:blank#{encoded}"


def metric_delta(before: dict[str, Any] | None, after: dict[str, Any] | None) -> dict[str, float]:
    if not before or not after:
        return {}
    result: dict[str, float] = {}
    for name, after_value in after.items():
        before_value = before.get(name)
        if isinstance(before_value, (int, float)) and isinstance(after_value, (int, float)):
            result[name] = float(after_value) - float(before_value)
    return result


def enrich_probe_result(probe: dict[str, Any]) -> None:
    measurement = probe["frameMeasurement"]
    intervals = [
        float(value)
        for value in measurement.get("intervalsMillis", [])
        if isinstance(value, (int, float)) and math.isfinite(value) and value > 0
    ]
    statistics_result = sample_statistics(intervals)
    mean_interval = statistics_result["mean"]
    sorted_slowest_first = sorted(intervals, reverse=True)
    slowest_one_percent_count = max(1, math.ceil(len(intervals) * 0.01)) if intervals else 0
    slowest_one_percent = sorted_slowest_first[:slowest_one_percent_count]
    slowest_one_percent_mean = statistics.fmean(slowest_one_percent) if slowest_one_percent else None
    statistics_result.update({
        "framesPerSecond": 1_000.0 / mean_interval if mean_interval else None,
        "onePercentLowFramesPerSecond":
            1_000.0 / slowest_one_percent_mean if slowest_one_percent_mean else None,
        "over16_67MillisCount": sum(value > 16.67 for value in intervals),
        "over16_67MillisProportion":
            sum(value > 16.67 for value in intervals) / len(intervals) if intervals else None,
        "over33_33MillisCount": sum(value > 33.33 for value in intervals),
        "over33_33MillisProportion":
            sum(value > 33.33 for value in intervals) / len(intervals) if intervals else None,
    })
    measurement["statistics"] = statistics_result

    long_tasks = measurement.get("longTasks", [])
    long_task_durations = [
        float(task["durationMillis"])
        for task in long_tasks
        if isinstance(task, dict) and isinstance(task.get("durationMillis"), (int, float))
    ]
    measurement["longTaskSummary"] = {
        "count": len(long_task_durations),
        "totalDurationMillis": sum(long_task_durations),
        "maximumDurationMillis": max(long_task_durations, default=0.0),
        "durationStatistics": sample_statistics(long_task_durations),
    }

    heap_before = measurement.get("heapBefore")
    heap_after = measurement.get("heapAfter")
    measurement["heapDeltaBytes"] = {
        name: heap_after[name] - heap_before[name]
        for name in ("totalJsHeapSizeBytes", "usedJsHeapSizeBytes")
        if isinstance(heap_before, dict)
        and isinstance(heap_after, dict)
        and isinstance(heap_before.get(name), (int, float))
        and isinstance(heap_after.get(name), (int, float))
    }

    cdp = probe.get("cdp", {})
    cdp["delta"] = metric_delta(cdp.get("before"), cdp.get("after"))
    cdp["postGcReclaimedBytes"] = {
        name: cdp["after"][name] - cdp["postGc"][name]
        for name in ("JSHeapUsedSize", "JSHeapTotalSize")
        if isinstance(cdp.get("after"), dict)
        and isinstance(cdp.get("postGc"), dict)
        and isinstance(cdp["after"].get(name), (int, float))
        and isinstance(cdp["postGc"].get(name), (int, float))
    }
    diagnostics = probe.get("diagnostics", {})
    diagnostics["counts"] = {
        "consoleMessages": len(diagnostics.get("consoleMessages", [])),
        "consoleErrors": sum(
            message.get("type") == "error"
            for message in diagnostics.get("consoleMessages", [])
            if isinstance(message, dict)
        ),
        "consoleWarnings": sum(
            message.get("type") == "warning"
            for message in diagnostics.get("consoleMessages", [])
            if isinstance(message, dict)
        ),
        "pageErrors": len(diagnostics.get("pageErrors", [])),
        "requestFailures": len(diagnostics.get("requestFailures", [])),
        "httpErrors": len(diagnostics.get("httpErrors", [])),
    }


SUMMARY_METRICS: tuple[tuple[str, str], ...] = (
    ("coldNavigation.wallNavigationMillis", "ms"),
    ("coldNavigation.wallReadyMillis", "ms"),
    ("coldNavigation.navigation.ttfbMillis", "ms"),
    ("coldNavigation.navigation.domContentLoadedMillis", "ms"),
    ("coldNavigation.navigation.loadMillis", "ms"),
    ("coldNavigation.firstPaintMillis", "ms"),
    ("coldNavigation.firstContentfulPaintMillis", "ms"),
    ("coldNavigation.resources.totals.transferSizeBytes", "bytes"),
    ("coldNavigation.resources.totals.decodedBodySizeBytes", "bytes"),
    ("coldNavigation.resources.wasm.transferSizeBytes", "bytes"),
    ("coldNavigation.resources.wasm.decodedBodySizeBytes", "bytes"),
    ("warmNavigation.wallNavigationMillis", "ms"),
    ("warmNavigation.wallReadyMillis", "ms"),
    ("warmNavigation.navigation.ttfbMillis", "ms"),
    ("warmNavigation.navigation.domContentLoadedMillis", "ms"),
    ("warmNavigation.navigation.loadMillis", "ms"),
    ("warmNavigation.firstPaintMillis", "ms"),
    ("warmNavigation.firstContentfulPaintMillis", "ms"),
    ("warmNavigation.resources.totals.transferSizeBytes", "bytes"),
    ("warmNavigation.resources.totals.decodedBodySizeBytes", "bytes"),
    ("warmNavigation.resources.wasm.transferSizeBytes", "bytes"),
    ("warmNavigation.resources.wasm.decodedBodySizeBytes", "bytes"),
    ("warmNavigation.memory.usedJsHeapSizeBytes", "bytes"),
    ("frameMeasurement.statistics.mean", "ms"),
    ("frameMeasurement.statistics.median", "ms"),
    ("frameMeasurement.statistics.p95", "ms"),
    ("frameMeasurement.statistics.p99", "ms"),
    ("frameMeasurement.statistics.maximum", "ms"),
    ("frameMeasurement.statistics.framesPerSecond", "frames/s"),
    ("frameMeasurement.statistics.onePercentLowFramesPerSecond", "frames/s"),
    ("frameMeasurement.statistics.over16_67MillisProportion", "ratio"),
    ("frameMeasurement.statistics.over33_33MillisProportion", "ratio"),
    ("frameMeasurement.longTaskSummary.count", "count"),
    ("frameMeasurement.longTaskSummary.totalDurationMillis", "ms"),
    ("frameMeasurement.longTaskSummary.maximumDurationMillis", "ms"),
    ("frameMeasurement.heapDeltaBytes.usedJsHeapSizeBytes", "bytes"),
    ("cdp.after.JSHeapUsedSize", "bytes"),
    ("cdp.after.JSHeapTotalSize", "bytes"),
    ("cdp.postGc.JSHeapUsedSize", "bytes"),
    ("cdp.postGc.JSHeapTotalSize", "bytes"),
    ("cdp.postGcHeapUsage.usedSize", "bytes"),
    ("cdp.postGcHeapUsage.totalSize", "bytes"),
    ("cdp.delta.TaskDuration", "seconds"),
    ("cdp.delta.ScriptDuration", "seconds"),
    ("cdp.delta.LayoutDuration", "seconds"),
    ("cdp.delta.RecalcStyleDuration", "seconds"),
)

DIAGNOSTIC_COUNT_NAMES = (
    "consoleMessages",
    "consoleErrors",
    "consoleWarnings",
    "pageErrors",
    "requestFailures",
    "httpErrors",
)


def validate_probe(probe: dict[str, Any], expected_measure_frames: int) -> None:
    if not isinstance(probe, dict):
        raise ValueError("browser probe must be an object")
    if probe.get("schemaVersion") != PROBE_SCHEMA_VERSION:
        raise ValueError(
            f"browser probe schemaVersion must equal {PROBE_SCHEMA_VERSION}",
        )
    cdp = probe.get("cdp")
    if not isinstance(cdp, dict) or not cdp.get("supported"):
        raise ValueError("browser probe requires CDP performance metrics")
    if not cdp.get("postGcSupported"):
        detail = cdp.get("error") if isinstance(cdp, dict) else None
        suffix = f": {detail}" if detail else ""
        raise ValueError("browser probe requires successful post-GC retention metrics" + suffix)
    collection = cdp.get("postGcCollection")
    if collection != {
        "method": "HeapProfiler.collectGarbage",
        "passes": 1,
        "succeeded": True,
    }:
        raise ValueError("browser probe post-GC collection attestation is invalid")
    intervals = deep_get(probe, "frameMeasurement.intervalsMillis")
    if not isinstance(intervals, list) or len(intervals) != expected_measure_frames:
        actual = len(intervals) if isinstance(intervals, list) else "missing"
        raise ValueError(
            f"browser probe returned {actual} measured frame intervals; "
            f"expected {expected_measure_frames}",
        )
    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0
        for value in intervals
    ):
        raise ValueError("browser probe frame intervals must be finite positive numbers")
    missing_metrics = [
        path
        for path, _ in SUMMARY_METRICS
        if not isinstance((value := deep_get(probe, path)), (int, float))
        or not math.isfinite(float(value))
    ]
    if missing_metrics:
        raise ValueError("browser probe is missing core metrics: " + ", ".join(missing_metrics))
    canvas_count = deep_get(probe, "warmNavigation.canvas.count")
    if not isinstance(canvas_count, int) or canvas_count < 0:
        raise ValueError("browser probe warm canvas count is missing or invalid")
    diagnostic_counts = deep_get(probe, "diagnostics.counts")
    if not isinstance(diagnostic_counts, dict):
        raise ValueError("browser probe diagnostic counts are missing")
    invalid_diagnostics = [
        name
        for name in DIAGNOSTIC_COUNT_NAMES
        if not isinstance(diagnostic_counts.get(name), int) or diagnostic_counts[name] < 0
    ]
    if invalid_diagnostics:
        raise ValueError(
            "browser probe diagnostic counts are missing or invalid: "
            + ", ".join(invalid_diagnostics),
        )


def build_summary(forks: list[dict[str, Any]]) -> dict[str, Any]:
    successful = [fork for fork in forks if fork.get("status") == "ok"]
    metrics: dict[str, Any] = {}
    for path, unit in SUMMARY_METRICS:
        samples = [deep_get(fork["probe"], path) for fork in successful]
        numeric_samples = [
            float(sample)
            for sample in samples
            if isinstance(sample, (int, float)) and math.isfinite(sample)
        ]
        if len(numeric_samples) != len(successful):
            raise ValueError(f"successful browser fork is missing core metric: {path}")
        metrics[path] = {
            "unit": unit,
            **sample_statistics(numeric_samples),
        }

    diagnostics = [fork["probe"].get("diagnostics", {}).get("counts", {}) for fork in successful]
    return {
        "successfulForkCount": len(successful),
        "failedForkCount": len(forks) - len(successful),
        "metrics": metrics,
        "diagnosticTotals": {
            key: sum(int(counts.get(key, 0)) for counts in diagnostics)
            for key in DIAGNOSTIC_COUNT_NAMES
        },
    }


def browser_launch_configuration(viewport: tuple[int, int], browser: str) -> dict[str, Any]:
    launch_options: dict[str, Any] = {"headless": True}
    if browser in {"default", "chrome"}:
        launch_options["args"] = [
            "--disable-background-networking",
            "--disable-background-timer-throttling",
            "--disable-backgrounding-occluded-windows",
            "--disable-component-update",
            "--disable-default-apps",
            "--disable-renderer-backgrounding",
            "--force-device-scale-factor=1",
            "--no-first-run",
        ]
    return {
        "browser": {
            # Playwright CLI itself defaults to the branded Chrome channel.  Pin
            # the default benchmark to Playwright's revisioned Chromium so that
            # a machine-wide browser install cannot silently change the binary.
            "browserName": "chromium" if browser == "default" else browser,
            "launchOptions": launch_options,
            "contextOptions": {
                "viewport": {"width": viewport[0], "height": viewport[1]},
                "deviceScaleFactor": 1,
                "colorScheme": "dark",
                "locale": "en-US",
                "reducedMotion": "no-preference",
                "timezoneId": "UTC",
                "serviceWorkers": "allow",
            },
        },
    }


def run_fork(
    *,
    fork_index: int,
    run_id: str,
    arguments: argparse.Namespace,
    pwcli: pathlib.Path,
    probe_file: pathlib.Path,
    session_root: pathlib.Path,
) -> dict[str, Any]:
    fork_started = utc_now()
    fork_directory = session_root / f"fork-{fork_index:02d}"
    profile_directory = fork_directory / "browser-profile"
    if fork_directory.exists() or profile_directory.exists():
        raise RuntimeError(f"refusing to reuse browser benchmark isolation directory: {fork_directory}")
    fork_directory.mkdir(parents=True)

    config_path = fork_directory / "playwright-cli.json"
    config_path.write_text(
        json.dumps(browser_launch_configuration(arguments.viewport, arguments.browser), indent=2) + "\n",
        encoding="utf-8",
    )
    profile_existed_before_open = profile_directory.exists()
    session_name = f"kinetickk-browser-{run_id}-f{fork_index:02d}"
    probe_configuration = {
        "targetUrl": arguments.url,
        "readySelector": arguments.ready_selector or None,
        "readyState": arguments.ready_state,
        "settleMillis": arguments.settle_millis,
        "timeoutMillis": arguments.timeout_millis,
        "frameTimeoutMillis": arguments.frame_timeout_millis,
        "warmupFrames": arguments.warmup_frames,
        "measureFrames": arguments.measure_frames,
    }
    bootstrap_url = encode_bootstrap_configuration(probe_configuration)

    session_argument = f"-s={session_name}"
    open_command = [
        str(pwcli),
        session_argument,
        "open",
        bootstrap_url,
        "--config",
        str(config_path),
        "--persistent",
        "--profile",
        str(profile_directory),
    ]
    if arguments.browser != "default":
        open_command.extend(["--browser", arguments.browser])

    probe_command = [
        str(pwcli),
        session_argument,
        "--raw",
        "run-code",
        "--filename",
        str(probe_file),
    ]
    close_command = [str(pwcli), session_argument, "close"]
    close_diagnostics: dict[str, Any] | None = None
    try:
        opened = command_result(
            open_command,
            working_directory=fork_directory,
            timeout_seconds=arguments.command_timeout_seconds,
        )
        write_command_logs(fork_directory, "open", opened)
        require_success(opened, "opening isolated browser session", open_command)

        measured = command_result(
            probe_command,
            working_directory=fork_directory,
            timeout_seconds=arguments.command_timeout_seconds,
        )
        write_command_logs(fork_directory, "probe", measured)
        require_success(measured, "running browser performance probe", probe_command)
        probe = extract_json_payload(measured.stdout)
        if probe is None:
            raise RuntimeError("browser probe result unexpectedly resolved to null")
        enrich_probe_result(probe)
        validate_probe(probe, arguments.measure_frames)
        status = "ok"
        error = None
    except (KeyError, TypeError, ValueError, RuntimeError, subprocess.TimeoutExpired) as exception:
        probe = None
        status = "error"
        error = {
            "type": type(exception).__name__,
            "message": str(exception),
        }
    finally:
        try:
            closed = command_result(
                close_command,
                working_directory=fork_directory,
                timeout_seconds=min(arguments.command_timeout_seconds, 60.0),
            )
            write_command_logs(fork_directory, "close", closed)
            close_diagnostics = {
                "exitCode": closed.returncode,
                "stdoutTail": closed.stdout[-2_000:],
                "stderrTail": closed.stderr[-2_000:],
            }
        except subprocess.TimeoutExpired as exception:
            close_diagnostics = {
                "exitCode": None,
                "error": f"close command timed out: {exception}",
            }

    browser_closed = close_diagnostics is not None and close_diagnostics.get("exitCode") == 0
    if not browser_closed:
        close_failure = {
            "type": "BrowserCloseError",
            "message": "isolated browser session did not close successfully",
            "diagnostics": close_diagnostics,
        }
        if error is None:
            error = close_failure
        else:
            error["closeFailure"] = close_failure
        status = "error"

    return {
        "fork": fork_index,
        "status": status,
        "startedAtUtc": fork_started,
        "finishedAtUtc": utc_now(),
        "isolation": {
            "sessionName": session_name,
            "workingDirectory": f"session/fork-{fork_index:02d}",
            "persistentProfileDirectory": f"session/fork-{fork_index:02d}/browser-profile",
            "profileExistedBeforeOpen": profile_existed_before_open,
            "freshProfileRequired": True,
            "browserClosedAfterFork": browser_closed,
        },
        "probe": probe,
        "error": error,
        "closeDiagnostics": close_diagnostics,
    }


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Benchmark a browser/Wasm build through Playwright CLI with isolated cold and warm "
            "navigations plus raw requestAnimationFrame samples."
        ),
    )
    parser.add_argument("--url", required=True, help="HTTP(S) URL of the served Wasm application")
    parser.add_argument("--output", required=True, type=pathlib.Path, help="JSON result file")
    parser.add_argument("--label", required=True, help="human-readable branch/build label")
    parser.add_argument("--revision", help="exact source revision; defaults to repository HEAD")
    parser.add_argument("--dirty", type=parse_boolean_mode, default="auto")
    parser.add_argument("--repo-root", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--forks", type=parse_positive_int, default=DEFAULT_FORKS)
    parser.add_argument("--warmup-frames", type=parse_non_negative_int, default=DEFAULT_WARMUP_FRAMES)
    parser.add_argument("--measure-frames", type=parse_positive_int, default=DEFAULT_MEASURE_FRAMES)
    parser.add_argument("--viewport", type=parse_viewport, default=DEFAULT_VIEWPORT)
    parser.add_argument("--ready-selector", default="canvas")
    parser.add_argument("--ready-state", choices=("attached", "visible"), default="attached")
    parser.add_argument("--settle-millis", type=parse_non_negative_int, default=1_000)
    parser.add_argument("--timeout-millis", type=parse_positive_int, default=120_000)
    parser.add_argument("--frame-timeout-millis", type=parse_positive_int, default=120_000)
    parser.add_argument("--command-timeout-seconds", type=float, default=300.0)
    parser.add_argument(
        "--browser",
        choices=("default", "chrome", "firefox", "webkit", "msedge"),
        default="default",
        help="default uses Playwright CLI's bundled Chromium",
    )
    parser.add_argument("--pwcli", help="path to playwright_cli.sh or a global playwright-cli")
    parser.add_argument(
        "--session-root",
        type=pathlib.Path,
        default=pathlib.Path("output/playwright/browser-performance"),
    )
    parser.add_argument("--require-canvas", action="store_true")
    parser.add_argument("--fail-on-diagnostics", action="store_true")
    parser.add_argument("--overwrite", action="store_true")
    arguments = parser.parse_args()

    parsed_url = urllib.parse.urlparse(arguments.url)
    if parsed_url.scheme not in {"http", "https"}:
        parser.error("--url must use http:// or https:// so browser loading behavior is measurable")
    if arguments.command_timeout_seconds <= 0:
        parser.error("--command-timeout-seconds must be greater than zero")
    return arguments


def main() -> int:
    arguments = parse_arguments()
    if shutil.which("npx") is None:
        raise RuntimeError("npx is required by the bundled Playwright CLI wrapper but was not found")

    repository = arguments.repo_root.expanduser().resolve()
    tool_repository = pathlib.Path(__file__).resolve().parents[2].resolve()
    source_before, tool_source_before = capture_browser_source_states(
        repository,
        tool_repository,
    )
    declared_dirty = (
        None
        if arguments.dirty == "auto"
        else arguments.dirty == "true"
    )
    revision, dirty = source_provenance.validate_declared_source(
        source_before,
        revision=arguments.revision,
        dirty=declared_dirty,
    )
    output_path = arguments.output.expanduser().resolve()
    if output_path.exists() and not arguments.overwrite:
        raise RuntimeError(f"refusing to overwrite existing result without --overwrite: {output_path}")
    validate_browser_evidence_path(
        repository=repository,
        tool_repository=tool_repository,
        path=output_path,
        allow_existing=arguments.overwrite,
    )
    output_path.parent.mkdir(parents=True, exist_ok=True)

    pwcli = resolve_pwcli(arguments.pwcli)
    probe_file = pathlib.Path(__file__).resolve().with_name("browser_probe.js")
    if not probe_file.is_file():
        raise RuntimeError(f"browser probe file is missing: {probe_file}")
    version_result = command_result(
        [str(pwcli), "--version"],
        working_directory=repository,
        timeout_seconds=60.0,
    )
    require_success(version_result, "reading Playwright CLI version", [str(pwcli), "--version"])
    wrapper_provenance = repository_wrapper_provenance(
        pwcli,
        tool_repository,
        version_result.stdout.strip(),
        tool_source_before,
    )

    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(3)
    session_root = arguments.session_root.expanduser().resolve() / run_id
    if session_root.exists():
        raise RuntimeError(f"refusing to reuse session root: {session_root}")
    validate_browser_evidence_path(
        repository=repository,
        tool_repository=tool_repository,
        path=session_root,
    )
    session_root.mkdir(parents=True)

    branch = source_provenance.current_branch(repository)
    result: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "spdxFileCopyrightText": "2026 Vladislav Tomilov",
        "spdxLicenseIdentifier": "GPL-3.0-or-later",
        "suite": SUITE_NAME,
        "status": "running",
        "generatedAtUtc": utc_now(),
        "runId": run_id,
        "source": {
            "label": arguments.label,
            "revision": revision,
            "branch": branch,
            "dirty": dirty,
            "sourceTreeSha256": source_before.source_tree_sha256,
            "repository": logical_repository_namespace(revision),
        },
        "protocol": {
            "targetUrl": arguments.url,
            "forks": arguments.forks,
            "warmupFrames": arguments.warmup_frames,
            "measureFrames": arguments.measure_frames,
            "viewport": {
                "widthPixels": arguments.viewport[0],
                "heightPixels": arguments.viewport[1],
                "deviceScaleFactor": 1,
            },
            "readySelector": arguments.ready_selector or None,
            "readyState": arguments.ready_state,
            "settleMillis": arguments.settle_millis,
            "timeoutMillis": arguments.timeout_millis,
            "frameTimeoutMillis": arguments.frame_timeout_millis,
            "browserSelection": arguments.browser,
            "navigationOrder": ["cold-fresh-profile", "warm-same-profile"],
            "frameThresholdsMillis": [16.67, 33.33],
            "postGcRetention": {
                "required": True,
                "method": "HeapProfiler.collectGarbage",
                "passes": 1,
                "sample": "immediate-Performance.getMetrics-and-Runtime.getHeapUsage",
            },
            "headless": True,
        },
        "runnerEnvironment": {
            "hostPlatform": platform.platform(),
            "operatingSystem": platform.system(),
            "operatingSystemRelease": platform.release(),
            "machine": platform.machine(),
            "processor": platform.processor() or None,
            "logicalCpuCount": os.cpu_count(),
            "pythonVersion": platform.python_version(),
            "nodeVersion": subprocess.run(
                ["node", "--version"], capture_output=True, text=True, check=False, timeout=30,
            ).stdout.strip() or None,
            "playwrightCli": wrapper_provenance,
            "probe": {
                "path": "tools/performance/browser_probe.js",
                "sha256": hashlib.sha256(probe_file.read_bytes()).hexdigest(),
            },
            "toolSource": {
                "repositoryRevision": tool_source_before.revision,
                "repositoryDirty": tool_source_before.dirty,
                "repositorySourceTreeSha256": tool_source_before.source_tree_sha256,
            },
            "sessionRoot": f"session/{run_id}",
        },
        "forks": [],
        "summary": None,
    }

    start = time.monotonic()
    for fork_index in range(arguments.forks):
        fork = run_fork(
            fork_index=fork_index,
            run_id=run_id,
            arguments=arguments,
            pwcli=pwcli,
            probe_file=probe_file,
            session_root=session_root,
        )
        result["forks"].append(fork)
        if not fork["isolation"]["browserClosedAfterFork"]:
            result["abortedAfterFork"] = fork_index
            break

    result["summary"] = build_summary(result["forks"])
    result["durationSeconds"] = time.monotonic() - start
    failed_forks = result["summary"]["failedForkCount"]
    result["status"] = "ok" if failed_forks == 0 else "error"
    result["finishedAtUtc"] = utc_now()

    violations: list[str] = []
    for fork in result["forks"]:
        if fork.get("status") != "ok":
            continue
        probe = fork["probe"]
        canvas_count = deep_get(probe, "warmNavigation.canvas.count")
        if arguments.require_canvas and (not isinstance(canvas_count, int) or canvas_count < 1):
            violations.append(f"fork {fork['fork']}: no canvas was attached after warm navigation")
        if arguments.fail_on_diagnostics:
            counts = probe.get("diagnostics", {}).get("counts", {})
            for name in ("consoleErrors", "pageErrors", "requestFailures", "httpErrors"):
                if counts.get(name, 0):
                    violations.append(f"fork {fork['fork']}: {name}={counts[name]}")
    result["violations"] = violations
    if violations:
        result["status"] = "error"

    temporary_output = output_path.with_name(output_path.name + f".{run_id}.tmp")
    validate_browser_evidence_path(
        repository=repository,
        tool_repository=tool_repository,
        path=temporary_output,
    )
    source_after = source_provenance.capture_source_state(repository)
    source_provenance.require_unchanged_source(source_before, source_after)
    if tool_repository != repository:
        tool_source_after = source_provenance.capture_source_state(tool_repository)
        source_provenance.require_unchanged_source(tool_source_before, tool_source_after)
    temporary_output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.replace(temporary_output, output_path)

    print(
        json.dumps({
            "status": result["status"],
            "output": str(output_path),
            "successfulForks": result["summary"]["successfulForkCount"],
            "failedForks": failed_forks,
            "violations": violations,
        }, sort_keys=True),
    )
    return 0 if result["status"] == "ok" else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError) as error:
        print(f"browser benchmark failed: {error}", file=sys.stderr)
        raise SystemExit(2)
