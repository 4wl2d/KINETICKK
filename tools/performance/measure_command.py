#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Measure wall and waited-child CPU time for an external build command."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import platform
import re
import resource
import shutil
import statistics
import subprocess
import sys
import time
import tomllib
from typing import Any

import source_provenance


SCHEMA_VERSION = 2
SUITE_NAME = "kinetickk-command-performance"
STATIC_BUILD_ENVIRONMENT_FIELDS = (
    "os",
    "architecture",
    "pythonVersion",
    "processorCount",
    "javaVersion",
    "gradleDistributionSha256",
    "kotlinVersion",
)
GRADLE_JVM_IDENTITY_PREFIXES = (
    "Launcher JVM:",
    "Daemon JVM:",
)


def logical_repository_namespace(revision: str) -> str:
    if not revision or revision in {".", ".."} or any(
        character not in "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-"
        for character in revision
    ):
        raise ValueError("revision must be a non-empty path-safe source identity")
    return f"repository/{revision}"


def positive_integer(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def non_negative_integer(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must not be negative")
    return parsed


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--cwd", required=True, type=pathlib.Path)
    parser.add_argument("--label", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--dirty", choices=("true", "false"), default="false")
    parser.add_argument("--warmups", type=non_negative_integer, default=0)
    parser.add_argument("--repetitions", type=positive_integer, default=1)
    parser.add_argument("--timeout-seconds", type=positive_integer, default=1_800)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    arguments = parser.parse_args()
    if arguments.command and arguments.command[0] == "--":
        arguments.command = arguments.command[1:]
    if not arguments.command:
        parser.error("a command is required after --")
    return arguments


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def percentile(values: list[float], quantile: float) -> float:
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = quantile * (len(ordered) - 1)
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    fraction = position - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * fraction


def summary(values: list[int]) -> dict[str, float]:
    floating = [float(value) for value in values]
    return {
        "min": min(floating),
        "median": statistics.median(floating),
        "p95": percentile(floating, 0.95),
        "max": max(floating),
    }


def successful_command_output(command: list[str], *, cwd: pathlib.Path | None = None) -> str:
    completed = subprocess.run(
        command,
        cwd=cwd,
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    output = "\n".join(part.strip() for part in (completed.stdout, completed.stderr) if part.strip())
    if completed.returncode != 0:
        raise RuntimeError(
            f"tool fingerprint command failed ({completed.returncode}): {' '.join(command)}: {output}",
        )
    if not output:
        raise RuntimeError(f"tool fingerprint command produced no version: {' '.join(command)}")
    return output


def first_non_empty_line(value: str) -> str:
    for line in value.splitlines():
        normalized = line.strip()
        if normalized:
            return normalized
    raise ValueError("version output contains no non-empty line")


def parse_gradle_jvm_identity(output: str) -> str:
    identity_lines: list[str] = []
    normalized_lines = [line.strip() for line in output.splitlines()]
    for prefix in GRADLE_JVM_IDENTITY_PREFIXES:
        matches = [line for line in normalized_lines if line.startswith(prefix)]
        if len(matches) != 1 or not matches[0][len(prefix):].strip():
            raise RuntimeError(
                f"Gradle --version output must contain exactly one non-empty {prefix} line",
            )
        identity_lines.append(matches[0])
    return "\n".join(identity_lines)


def gradle_jvm_identity(repository: pathlib.Path) -> str:
    wrapper = repository / "gradlew"
    if not wrapper.is_file() or not os.access(wrapper, os.X_OK):
        raise RuntimeError(f"executable Gradle wrapper is required: {wrapper}")
    output = successful_command_output(
        [str(wrapper), "--version", "--no-daemon"],
        cwd=repository,
    )
    return parse_gradle_jvm_identity(output)


def wrapper_distribution_sha256(repository: pathlib.Path) -> str:
    properties = repository / "gradle/wrapper/gradle-wrapper.properties"
    value = None
    for raw_line in properties.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        key, candidate = line.split("=", 1)
        if key.strip() == "distributionSha256Sum":
            value = candidate.strip()
            break
    if value is None:
        raise RuntimeError(f"missing distributionSha256Sum in {properties}")
    if len(value) != 64 or any(character not in "0123456789abcdef" for character in value):
        raise RuntimeError(f"invalid distributionSha256Sum in {properties}")
    return value


def kotlin_version(repository: pathlib.Path) -> str:
    catalog = repository / "gradle/libs.versions.toml"
    with catalog.open("rb") as stream:
        document = tomllib.load(stream)
    value = document.get("versions", {}).get("kotlin")
    if not isinstance(value, str) or not value.strip():
        raise RuntimeError(f"missing versions.kotlin in {catalog}")
    return value.strip()


def capture_static_build_environment(repository: pathlib.Path) -> dict[str, Any]:
    processor_count = os.cpu_count()
    if processor_count is None or processor_count <= 0:
        raise RuntimeError("could not determine a positive processor count")
    return {
        "os": platform.platform(),
        "architecture": platform.machine(),
        "pythonVersion": platform.python_version(),
        "processorCount": processor_count,
        "javaVersion": gradle_jvm_identity(repository),
        "gradleDistributionSha256": wrapper_distribution_sha256(repository),
        "kotlinVersion": kotlin_version(repository),
    }


def gradle_user_home() -> pathlib.Path:
    configured = os.environ.get("GRADLE_USER_HOME")
    return pathlib.Path(configured).expanduser() if configured else pathlib.Path.home() / ".gradle"


def installed_gradle_tool_versions(
    *,
    family: str,
    executable_candidates: list[pathlib.Path],
    arguments: list[str],
    version_pattern: re.Pattern[str],
) -> str:
    executables = sorted(
        {
            candidate.resolve()
            for candidate in executable_candidates
            if candidate.is_file() and os.access(candidate, os.X_OK)
        },
        key=lambda path: path.as_posix(),
    )
    if not executables:
        raise RuntimeError(
            f"no Gradle-managed {family} executable was produced under {gradle_user_home()}",
        )

    versions: set[str] = set()
    for executable in executables:
        output = successful_command_output([str(executable), *arguments])
        match = version_pattern.search(output)
        if match is None:
            raise RuntimeError(f"could not parse {family} version from: {first_non_empty_line(output)}")
        versions.add(match.group(1))
    if len(versions) != 1:
        raise RuntimeError(
            f"ambiguous Gradle-managed {family} versions: {', '.join(sorted(versions))}; "
            "use an isolated GRADLE_USER_HOME for build evidence",
        )
    return next(iter(versions))


def capture_gradle_tool_versions() -> dict[str, str]:
    root = gradle_user_home()
    node_candidates = [
        candidate
        for directory in (root / "nodejs").glob("node-v*")
        for candidate in (directory / "bin/node", directory / "bin/node.exe", directory / "node.exe")
    ]
    binaryen_candidates = [
        candidate
        for directory in (root / "binaryen").glob("binaryen-version_*")
        for candidate in (directory / "bin/wasm-opt", directory / "bin/wasm-opt.exe")
    ]
    return {
        "nodeVersion": installed_gradle_tool_versions(
            family="Node.js",
            executable_candidates=node_candidates,
            arguments=["--version"],
            version_pattern=re.compile(r"\bv?(\d+\.\d+\.\d+)\b"),
        ),
        "binaryenVersion": installed_gradle_tool_versions(
            family="Binaryen",
            executable_candidates=binaryen_candidates,
            arguments=["--version"],
            version_pattern=re.compile(r"\bversion\s+([0-9]+(?:\.[0-9]+)*)\b", re.IGNORECASE),
        ),
    }


def run_once(
    command: list[str],
    cwd: pathlib.Path,
    timeout_seconds: int,
    phase: str,
    index: int,
) -> dict[str, Any]:
    print(f"{phase} {index}: {' '.join(command)}", flush=True)
    usage_before = resource.getrusage(resource.RUSAGE_CHILDREN)
    started_at = utc_now()
    wall_start = time.perf_counter_ns()
    timed_out = False
    try:
        completed = subprocess.run(command, cwd=cwd, check=False, timeout=timeout_seconds)
        exit_code = completed.returncode
    except subprocess.TimeoutExpired:
        timed_out = True
        exit_code = 124
    wall_ns = time.perf_counter_ns() - wall_start
    usage_after = resource.getrusage(resource.RUSAGE_CHILDREN)
    user_cpu_ns = round((usage_after.ru_utime - usage_before.ru_utime) * 1_000_000_000)
    system_cpu_ns = round((usage_after.ru_stime - usage_before.ru_stime) * 1_000_000_000)
    cpu_ns = user_cpu_ns + system_cpu_ns
    return {
        "index": index,
        "startedAt": started_at,
        "finishedAt": utc_now(),
        "wallNs": wall_ns,
        "userCpuNs": user_cpu_ns,
        "systemCpuNs": system_cpu_ns,
        "waitedChildCpuUtilizationPercent": 0.0 if wall_ns == 0 else cpu_ns / wall_ns * 100.0,
        "exitCode": exit_code,
        "timedOut": timed_out,
    }


def main() -> int:
    arguments = parse_arguments()
    cwd = arguments.cwd.resolve(strict=True)
    output = arguments.output.resolve(strict=False)
    command = list(arguments.command)
    if command[0].replace("\\", "/") != "./gradlew":
        raise RuntimeError("command schema v2 requires the worktree ./gradlew launcher")
    logical_working_directory = logical_repository_namespace(arguments.revision)
    declared_dirty = arguments.dirty == "true"
    source_before = source_provenance.capture_source_state(cwd)
    source_provenance.validate_declared_source(
        source_before,
        revision=arguments.revision,
        dirty=declared_dirty,
    )
    source_provenance.validate_output_path(
        repository=cwd,
        output=output,
    )
    static_environment_before = capture_static_build_environment(cwd)
    warmup_samples: list[dict[str, Any]] = []
    for index in range(1, arguments.warmups + 1):
        warmup = run_once(command, cwd, arguments.timeout_seconds, "warmup", index)
        warmup_samples.append(warmup)
        if warmup["exitCode"] != 0:
            print(f"warmup failed with exit code {warmup['exitCode']}", file=sys.stderr)
            return int(warmup["exitCode"])

    samples = [
        run_once(command, cwd, arguments.timeout_seconds, "measure", index)
        for index in range(1, arguments.repetitions + 1)
    ]
    static_environment_after = capture_static_build_environment(cwd)
    if any(
        static_environment_before[field] != static_environment_after[field]
        for field in STATIC_BUILD_ENVIRONMENT_FIELDS
    ):
        raise RuntimeError("static build environment changed during command measurement")
    environment = {
        **static_environment_after,
        **capture_gradle_tool_versions(),
    }
    source_after = source_provenance.capture_source_state(cwd)
    source_provenance.require_unchanged_source(source_before, source_after)
    wall_values = [int(sample["wallNs"]) for sample in samples]
    cpu_values = [int(sample["userCpuNs"]) + int(sample["systemCpuNs"]) for sample in samples]
    document = {
        "schemaVersion": SCHEMA_VERSION,
        "spdxFileCopyrightText": "2026 Vladislav Tomilov",
        "spdxLicenseIdentifier": "GPL-3.0-or-later",
        "suite": SUITE_NAME,
        "generatedAt": utc_now(),
        "label": arguments.label,
        "revision": arguments.revision,
        "dirty": declared_dirty,
        "sourceTreeSha256": source_after.source_tree_sha256,
        "workingDirectory": logical_working_directory,
        "command": command,
        "environment": environment,
        "cpuScope": (
            "resource.RUSAGE_CHILDREN; diagnostic only because detached daemons are not included"
        ),
        "warmups": arguments.warmups,
        "warmupSamples": warmup_samples,
        "repetitions": arguments.repetitions,
        "timeoutSeconds": arguments.timeout_seconds,
        "samples": samples,
        "summary": {
            "wallNs": summary(wall_values),
            "waitedChildCpuNs": summary(cpu_values),
        },
        "status": "ok" if all(sample["exitCode"] == 0 for sample in samples) else "failed",
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(document, indent=2) + "\n")
    print(f"Wrote {output}")
    return 0 if document["status"] == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())
