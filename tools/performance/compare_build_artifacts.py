#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Compare clean build wall times and deterministic artifact inventories."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import posixpath
import pathlib
import re
import statistics
from typing import Any


LEGACY_COMMAND_SCHEMA_VERSION = 1
COMMAND_SCHEMA_VERSION = 2
SUPPORTED_COMMAND_SCHEMA_VERSIONS = {
    LEGACY_COMMAND_SCHEMA_VERSION,
    COMMAND_SCHEMA_VERSION,
}
LEGACY_ARTIFACT_SCHEMA_VERSION = 2
ARTIFACT_SCHEMA_VERSION = 3
SUPPORTED_ARTIFACT_SCHEMA_VERSIONS = {
    LEGACY_ARTIFACT_SCHEMA_VERSION,
    ARTIFACT_SCHEMA_VERSION,
}
COMMAND_SUITE = "kinetickk-command-performance"
ARTIFACT_SUITE = "kinetickk-artifact-inventory"
APPLICATION_WASM_MATCHING_METHOD = "sha256-and-byte-equality"
BUILD_SAMPLE_FIELDS = (
    "index",
    "startedAt",
    "finishedAt",
    "wallNs",
    "userCpuNs",
    "systemCpuNs",
    "exitCode",
    "timedOut",
)
BUILD_CPU_UTILIZATION_FIELDS = (
    "waitedChildCpuUtilizationPercent",
    "processTreeCpuUtilizationPercent",
)
BUILD_ENVIRONMENT_FIELDS = (
    "os",
    "architecture",
    "pythonVersion",
    "processorCount",
)
BUILD_ENVIRONMENT_V2_FIELDS = BUILD_ENVIRONMENT_FIELDS + (
    "javaVersion",
    "gradleDistributionSha256",
    "kotlinVersion",
    "nodeVersion",
    "binaryenVersion",
)
GRADLE_JVM_IDENTITY_PREFIXES = (
    "Launcher JVM:",
    "Daemon JVM:",
)


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-build", nargs="+", required=True, type=pathlib.Path)
    parser.add_argument("--candidate-build", nargs="+", required=True, type=pathlib.Path)
    parser.add_argument("--baseline-artifacts", required=True, type=pathlib.Path)
    parser.add_argument("--candidate-artifacts", required=True, type=pathlib.Path)
    parser.add_argument("--output-json", required=True, type=pathlib.Path)
    parser.add_argument("--output-markdown", required=True, type=pathlib.Path)
    return parser.parse_args()


def read_json(path: pathlib.Path) -> dict[str, Any]:
    raw = path.read_bytes()
    document = json.loads(raw.decode("utf-8"))
    if not isinstance(document, dict):
        raise ValueError(f"expected a JSON object: {path}")
    document["_inputSource"] = {
        "file": path.name,
        "sha256": hashlib.sha256(raw).hexdigest(),
    }
    return document


def percent(candidate: float, baseline: float) -> float | None:
    return None if baseline == 0.0 else (candidate - baseline) / baseline * 100.0


def require_mapping(value: Any, context: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{context} must be an object")
    return value


def require_list(value: Any, context: str) -> list[Any]:
    if not isinstance(value, list):
        raise ValueError(f"{context} must be an array")
    return value


def require_sha256(value: Any, context: str) -> str:
    if (
        not isinstance(value, str)
        or len(value) != 64
        or any(character not in "0123456789abcdef" for character in value)
    ):
        raise ValueError(f"{context} must be a lowercase SHA-256 digest")
    return value


def require_non_negative_integer(value: Any, context: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError(f"{context} must be a non-negative integer")
    return value


def require_positive_integer(value: Any, context: str) -> int:
    parsed = require_non_negative_integer(value, context)
    if parsed == 0:
        raise ValueError(f"{context} must be positive")
    return parsed


def require_relative_artifact_path(value: Any, context: str) -> str:
    if not isinstance(value, str) or not value or "\\" in value:
        raise ValueError(f"{context} must be a non-empty POSIX relative path")
    normalized = posixpath.normpath(value)
    if normalized != value or normalized in {".", ".."} or normalized.startswith("../"):
        raise ValueError(f"{context} must be a normalized relative path")
    return value


def normalize_working_directory(value: Any, context: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{context} must be a non-empty path")
    return posixpath.normpath(value.replace("\\", "/"))


def repository_relative_path(path: Any, repository: str, context: str) -> str:
    normalized = normalize_working_directory(path, context)
    prefix = repository.rstrip("/") + "/"
    if not normalized.startswith(prefix):
        raise ValueError(f"{context} must be inside its artifact repository")
    relative = normalized[len(prefix):]
    if not relative or relative.startswith("../"):
        raise ValueError(f"{context} must identify a repository-relative artifact")
    return relative


def validate_build_environment(
    value: Any,
    context: str,
    schema_version: int,
) -> dict[str, Any]:
    environment = require_mapping(value, context)
    required_fields = (
        BUILD_ENVIRONMENT_FIELDS
        if schema_version == LEGACY_COMMAND_SCHEMA_VERSION
        else BUILD_ENVIRONMENT_V2_FIELDS
    )
    missing = [field for field in required_fields if field not in environment]
    if missing:
        raise ValueError(f"{context} is missing fields: {', '.join(missing)}")
    if schema_version == COMMAND_SCHEMA_VERSION:
        unexpected = sorted(set(environment) - set(required_fields))
        if unexpected:
            raise ValueError(
                f"{context} has fields outside command schema v2: {', '.join(unexpected)}",
            )
    string_fields = ["os", "architecture", "pythonVersion"]
    if schema_version == COMMAND_SCHEMA_VERSION:
        string_fields += [
            "javaVersion",
            "kotlinVersion",
            "nodeVersion",
            "binaryenVersion",
        ]
    for field in string_fields:
        if not isinstance(environment[field], str) or not environment[field]:
            raise ValueError(f"{context}.{field} must be a non-empty string")
    if schema_version == COMMAND_SCHEMA_VERSION:
        java_identity_lines = environment["javaVersion"].splitlines()
        if len(java_identity_lines) != len(GRADLE_JVM_IDENTITY_PREFIXES) or any(
            line != line.strip()
            or not line.startswith(prefix)
            or not line[len(prefix):].strip()
            for line, prefix in zip(
                java_identity_lines,
                GRADLE_JVM_IDENTITY_PREFIXES,
                strict=True,
            )
        ):
            raise ValueError(
                f"{context}.javaVersion must contain exact Launcher JVM and Daemon JVM lines",
            )
        require_sha256(
            environment["gradleDistributionSha256"],
            f"{context}.gradleDistributionSha256",
        )
    processor_count = environment["processorCount"]
    if isinstance(processor_count, bool) or not isinstance(processor_count, int) or processor_count <= 0:
        raise ValueError(f"{context}.processorCount must be a positive integer")
    return dict(environment)


def parse_timestamp(value: Any, context: str) -> dt.datetime:
    if not isinstance(value, str) or not value:
        raise ValueError(f"{context} must be a timestamp")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(f"{context} is not an ISO-8601 timestamp") from error
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError(f"{context} must include a UTC offset")
    return parsed.astimezone(dt.timezone.utc)


def normalize_semantic_build_command(
    command: Any,
    context: str,
    schema_version: int,
) -> tuple[dict[str, Any], str]:
    tokens = require_list(command, f"{context}.command")
    if not tokens or not all(isinstance(token, str) and token for token in tokens):
        raise ValueError(f"{context}.command must contain non-empty string arguments")
    executable = pathlib.PurePath(tokens[0].replace("\\", "/")).name.lower()
    if executable not in {"gradlew", "gradlew.bat"}:
        raise ValueError(f"{context}: build command must use the Gradle wrapper")
    if (
        schema_version == COMMAND_SCHEMA_VERSION
        and tokens[0].replace("\\", "/") != "./gradlew"
    ):
        raise ValueError(f"{context}: command schema v2 must use the worktree ./gradlew")

    flags: list[str] = []
    tasks: list[str] = []
    index = 1
    while index < len(tokens):
        token = tokens[index]
        if token == "--console":
            if index + 1 >= len(tokens):
                raise ValueError(f"{context}: --console requires a value")
            flags.append(f"--console={tokens[index + 1]}")
            index += 2
            continue
        if token.startswith("-"):
            flags.append(token)
        else:
            tasks.append(token)
        index += 1

    required_flags = {"--no-daemon", "--offline", "--console=plain"}
    if schema_version == COMMAND_SCHEMA_VERSION:
        required_flags.add("--no-build-cache")
    if set(flags) != required_flags or len(flags) != len(required_flags):
        raise ValueError(
            f"{context}: build command flags must be exactly {', '.join(sorted(required_flags))}",
        )
    task_aliases = {
        ("clean", "wasmJsBrowserDistribution"): "legacy-root",
        ("clean", ":app:web:wasmJsBrowserDistribution"): "app-web-module",
    }
    task_alias = task_aliases.get(tuple(tasks))
    if task_alias is None:
        raise ValueError(
            f"{context}: expected clean followed by the legacy-root or app:web production task",
        )
    return (
        {
            "operationId": "app-web-wasm-production-distribution-v1",
            "runner": "gradlew",
            "tasks": ["clean", "app-web-wasm-production-distribution"],
            "offline": True,
            "daemon": "disabled",
            "buildCache": "disabled" if schema_version == COMMAND_SCHEMA_VERSION else "legacy-unspecified",
            "console": "plain",
        },
        task_alias,
    )


def validate_build_sample(
    sample: Any,
    context: str,
    expected_index: int,
) -> tuple[dict[str, Any], dt.datetime, dt.datetime, str]:
    mapped = require_mapping(sample, context)
    missing = [field for field in BUILD_SAMPLE_FIELDS if field not in mapped]
    if missing:
        raise ValueError(f"{context} is missing fields: {', '.join(missing)}")
    if (
        isinstance(mapped["index"], bool)
        or not isinstance(mapped["index"], int)
        or mapped["index"] != expected_index
    ):
        raise ValueError(f"{context}.index must be {expected_index}")
    if (
        isinstance(mapped["exitCode"], bool)
        or not isinstance(mapped["exitCode"], int)
        or mapped["exitCode"] != 0
        or mapped["timedOut"] is not False
    ):
        raise ValueError(f"{context} must be successful and must not time out")
    wall_ns = mapped["wallNs"]
    if isinstance(wall_ns, bool) or not isinstance(wall_ns, (int, float)) or wall_ns <= 0:
        raise ValueError(f"{context}.wallNs must be positive")
    for field in ("userCpuNs", "systemCpuNs"):
        value = mapped[field]
        if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
            raise ValueError(f"{context}.{field} must be non-negative")
    present_utilization_fields = [
        field
        for field in BUILD_CPU_UTILIZATION_FIELDS
        if field in mapped
    ]
    if len(present_utilization_fields) != 1:
        raise ValueError(
            f"{context} must contain exactly one supported CPU-utilization field",
        )
    utilization_field = present_utilization_fields[0]
    utilization = mapped[utilization_field]
    if isinstance(utilization, bool) or not isinstance(utilization, (int, float)) or utilization < 0:
        raise ValueError(f"{context}.{utilization_field} must be non-negative")
    started = parse_timestamp(mapped["startedAt"], f"{context}.startedAt")
    finished = parse_timestamp(mapped["finishedAt"], f"{context}.finishedAt")
    if finished < started:
        raise ValueError(f"{context} finishes before it starts")
    raw_sample = {field: mapped[field] for field in BUILD_SAMPLE_FIELDS}
    raw_sample[utilization_field] = mapped[utilization_field]
    return raw_sample, started, finished, utilization_field


def load_builds(paths: list[pathlib.Path]) -> dict[str, Any]:
    documents = [read_json(path) for path in paths]
    measurements: list[dict[str, Any]] = []
    labels: set[str] = set()
    revisions: set[str] = set()
    environments: list[dict[str, Any]] = []
    semantic_commands: list[dict[str, Any]] = []
    task_aliases: set[str] = set()
    working_directories: set[str] = set()
    source_tree_sha256s: set[str] = set()
    schema_versions: set[int] = set()
    warmup_measurements: list[dict[str, Any]] = []
    for path, document in zip(paths, documents, strict=True):
        context = str(path)
        schema_version = document.get("schemaVersion")
        if (
            isinstance(schema_version, bool)
            or schema_version not in SUPPORTED_COMMAND_SCHEMA_VERSIONS
        ):
            raise ValueError(f"unsupported command result schemaVersion: {path}")
        schema_versions.add(schema_version)
        if document.get("suite") != COMMAND_SUITE:
            raise ValueError(f"unexpected command result suite: {path}")
        if document.get("status") != "ok":
            raise ValueError(f"expected successful build measurements: {path}")
        if document.get("dirty") is not False:
            raise ValueError(f"build measurement must attest dirty=false: {path}")
        repetitions = document.get("repetitions")
        warmup_count = document.get("warmups")
        if (
            isinstance(repetitions, bool)
            or not isinstance(repetitions, int)
            or repetitions <= 0
        ):
            raise ValueError(f"build measurement repetitions must be positive: {path}")
        if (
            isinstance(warmup_count, bool)
            or not isinstance(warmup_count, int)
            or warmup_count < 0
        ):
            raise ValueError(f"build measurement warmups must be non-negative: {path}")
        if schema_version == LEGACY_COMMAND_SCHEMA_VERSION and (
            repetitions != 1 or warmup_count != 0
        ):
            raise ValueError(f"legacy build documents require one measurement and no warmups: {path}")
        label = document.get("label")
        revision = document.get("revision")
        if not isinstance(label, str) or not label or not isinstance(revision, str) or not revision:
            raise ValueError(f"build label and revision must be non-empty: {path}")
        if (
            schema_version == COMMAND_SCHEMA_VERSION
            and re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", revision) is None
        ):
            raise ValueError(f"command schema v2 revision must be a full Git object ID: {path}")
        if schema_version == COMMAND_SCHEMA_VERSION:
            source_tree_sha256s.add(
                require_sha256(
                    document.get("sourceTreeSha256"),
                    f"{context}.sourceTreeSha256",
                ),
            )
        labels.add(label)
        revisions.add(revision)

        environment = validate_build_environment(
            document.get("environment"),
            f"{context}.environment",
            schema_version,
        )
        environments.append(environment)
        semantic_command, task_alias = normalize_semantic_build_command(
            document.get("command"),
            context,
            schema_version,
        )
        semantic_commands.append(semantic_command)
        task_aliases.add(task_alias)
        working_directory = normalize_working_directory(
            document.get("workingDirectory"),
            f"{context}.workingDirectory",
        )
        if (
            schema_version == COMMAND_SCHEMA_VERSION
            and working_directory != f"repository/{revision}"
        ):
            raise ValueError(
                f"{context}.workingDirectory must match its schema v2 revision",
            )
        working_directories.add(working_directory)

        samples = require_list(document.get("samples"), f"{context}.samples")
        if len(samples) != repetitions:
            raise ValueError(f"{context}.samples must match repetitions")
        if schema_version == COMMAND_SCHEMA_VERSION:
            warmup_samples = require_list(
                document.get("warmupSamples"),
                f"{context}.warmupSamples",
            )
            if len(warmup_samples) != warmup_count:
                raise ValueError(f"{context}.warmupSamples must match warmups")
        else:
            warmup_samples = []

        document_events: list[tuple[dt.datetime, dt.datetime]] = []
        for index, sample in enumerate(warmup_samples, start=1):
            raw_sample, started, finished, utilization_field = validate_build_sample(
                sample,
                f"{context}.warmupSamples[{index - 1}]",
                index,
            )
            document_events.append((started, finished))
            warmup_measurements.append(
                {
                    "source": dict(document["_inputSource"]),
                    "sample": raw_sample,
                    "_started": started,
                    "_finished": finished,
                    "_utilizationField": utilization_field,
                },
            )

        validated_samples = []
        for index, sample in enumerate(samples, start=1):
            raw_sample, started, finished, utilization_field = validate_build_sample(
                sample,
                f"{context}.samples[{index - 1}]",
                index,
            )
            document_events.append((started, finished))
            validated_samples.append((raw_sample, started, finished, utilization_field))
        for previous, current in zip(document_events, document_events[1:]):
            if current[0] < previous[1]:
                raise ValueError(f"{context} warmup/measurement timestamps overlap or are out of order")

        generated = parse_timestamp(document.get("generatedAt"), f"{context}.generatedAt")
        if document_events and generated < document_events[-1][1]:
            raise ValueError(f"{context}.generatedAt precedes the completed measurement")
        utilization_fields = {
            entry[3]
            for entry in validated_samples
        } | {
            measurement["_utilizationField"]
            for measurement in warmup_measurements
            if measurement["source"] == document["_inputSource"]
        }
        if len(utilization_fields) != 1:
            raise ValueError(f"{context} CPU-utilization field changes within the document")
        utilization_field = next(iter(utilization_fields))
        if utilization_field == "waitedChildCpuUtilizationPercent":
            expected_cpu_scope = (
                "resource.RUSAGE_CHILDREN; diagnostic only because detached daemons are not included"
            )
            if document.get("cpuScope") != expected_cpu_scope:
                raise ValueError(f"{context}.cpuScope does not match waited-child CPU samples")
            measurement_variant = f"waited-child-v{schema_version}"
        else:
            measurement_variant = f"process-tree-v{schema_version}"
        for raw_sample, started, finished, _ in validated_samples:
            measurements.append(
                {
                    "source": dict(document["_inputSource"]),
                    "generatedAt": document["generatedAt"],
                    "sample": raw_sample,
                    "_started": started,
                    "_finished": finished,
                    "_generated": generated,
                    "_variant": measurement_variant,
                },
            )

    if len(schema_versions) != 1:
        raise ValueError("command result schemaVersion changed within a build group")
    if len(labels) != 1 or len(revisions) != 1:
        raise ValueError("build label or revision changed between repetitions")
    if any(environment != environments[0] for environment in environments[1:]):
        raise ValueError("build environment changed between repetitions")
    if any(command != semantic_commands[0] for command in semantic_commands[1:]):
        raise ValueError("semantic build command changed between repetitions")
    if len(task_aliases) != 1:
        raise ValueError("production task alias changed between branch repetitions")
    if len(working_directories) != 1:
        raise ValueError("working directory changed between branch repetitions")
    if schema_version == COMMAND_SCHEMA_VERSION and len(source_tree_sha256s) != 1:
        raise ValueError("source tree identity changed between repetitions")
    measurement_variants = {measurement["_variant"] for measurement in measurements}
    if len(measurement_variants) != 1:
        raise ValueError("command measurement schema variant changed between repetitions")
    measurements.sort(key=lambda measurement: measurement["_started"])
    wall_seconds = [measurement["sample"]["wallNs"] / 1_000_000_000 for measurement in measurements]
    return {
        "schemaVersion": next(iter(schema_versions)),
        "label": next(iter(labels)),
        "revision": next(iter(revisions)),
        "dirty": False,
        "sourceTreeSha256": (
            next(iter(source_tree_sha256s))
            if schema_version == COMMAND_SCHEMA_VERSION
            else None
        ),
        "environment": environments[0],
        "semanticCommand": semantic_commands[0],
        "taskAlias": next(iter(task_aliases)),
        "measurementVariant": next(iter(measurement_variants)),
        "rawWarmups": [
            {
                "source": measurement["source"],
                "sample": measurement["sample"],
            }
            for measurement in sorted(warmup_measurements, key=lambda measurement: measurement["_started"])
        ],
        "rawSamples": [
            {
                "source": measurement["source"],
                "generatedAt": measurement["generatedAt"],
                "sample": measurement["sample"],
            }
            for measurement in measurements
        ],
        "wallSeconds": wall_seconds,
        "medianWallSeconds": statistics.median(wall_seconds),
        "_measurements": measurements,
        "_workingDirectory": next(iter(working_directories)),
    }


def validate_artifacts(document: dict[str, Any], build: dict[str, Any], role: str) -> dict[str, Any]:
    artifact_schema_version = document.get("schemaVersion")
    if artifact_schema_version not in SUPPORTED_ARTIFACT_SCHEMA_VERSIONS:
        raise ValueError(f"unsupported {role} artifact inventory schemaVersion")
    if (
        build["schemaVersion"] == COMMAND_SCHEMA_VERSION
        and artifact_schema_version != ARTIFACT_SCHEMA_VERSION
    ):
        raise ValueError(f"{role} command schema v2 requires artifact schema v3")
    if document.get("suite") != ARTIFACT_SUITE:
        raise ValueError(f"unexpected {role} artifact inventory suite")
    source = require_mapping(document.get("source"), f"{role} artifact source")
    if source["revision"] != build["revision"] or source["label"] != build["label"]:
        raise ValueError(f"{role} artifact inventory source does not match build source")
    if source.get("dirty") is not False:
        raise ValueError(f"{role} artifact inventory must attest dirty=false")
    if artifact_schema_version == ARTIFACT_SCHEMA_VERSION:
        artifact_source_tree_sha256 = require_sha256(
            source.get("sourceTreeSha256"),
            f"{role} artifact source.sourceTreeSha256",
        )
        if artifact_source_tree_sha256 != build.get("sourceTreeSha256"):
            raise ValueError(f"{role} artifact source tree does not match its measured build")
    else:
        artifact_source_tree_sha256 = None
    repository = normalize_working_directory(
        source.get("repository"),
        f"{role} artifact source.repository",
    )
    if repository != build["_workingDirectory"]:
        raise ValueError(f"{role} artifact inventory repository does not match build worktree")
    artifact_root = repository_relative_path(
        document.get("artifactRoot"),
        repository,
        f"{role} artifact root",
    )
    expected_artifact_roots = {
        "baseline": "build/dist/wasmJs/productionExecutable",
        "candidate": "app/web/build/dist/wasmJs/productionExecutable",
    }
    if artifact_root != expected_artifact_roots[role]:
        raise ValueError(f"{role} artifact root does not match the approved production output")
    application = application_wasm(document)
    application_source = repository_relative_path(
        application["sourcePath"],
        repository,
        f"{role} application Wasm source path",
    )
    expected_application_sources = {
        "baseline": "build/compileSync/wasmJs/main/productionExecutable/optimized/kinetickk.wasm",
        "candidate": (
            "app/web/build/compileSync/wasmJs/main/productionExecutable/optimized/kinetickk.wasm"
        ),
    }
    if application_source != expected_application_sources[role]:
        raise ValueError(f"{role} application Wasm source is not the approved optimized linker output")
    generated_at_text = document.get("generatedAtUtc")
    generated_at = parse_timestamp(generated_at_text, f"{role} artifact generatedAtUtc")
    last_build_result = max(
        measurement["_generated"]
        for measurement in build["_measurements"]
    )
    if generated_at < last_build_result:
        raise ValueError(f"{role} artifact inventory predates its final build result")

    selection = require_mapping(document.get("selection"), f"{role} artifact selection")
    compression = require_mapping(document.get("compression"), f"{role} artifact compression")
    environment = require_mapping(document.get("environment"), f"{role} artifact environment")
    for field in ("includes", "excludes"):
        patterns = require_list(selection.get(field), f"{role} artifact selection.{field}")
        if not all(isinstance(pattern, str) and pattern for pattern in patterns):
            raise ValueError(f"{role} artifact selection.{field} must contain non-empty strings")
        if len(set(patterns)) != len(patterns):
            raise ValueError(f"{role} artifact selection.{field} contains duplicates")
        if any(
            pattern.startswith(("/", "\\"))
            or (len(pattern) >= 3 and pattern[1:3] in {":/", ":\\"})
            for pattern in patterns
        ):
            raise ValueError(f"{role} artifact selection.{field} must use relative globs")
    if selection.get("symlinksFollowed") is not False:
        raise ValueError(f"{role} artifact selection must not follow symlinks")
    if compression.get("algorithm") != "gzip" or compression.get("level") != 9:
        raise ValueError(f"{role} artifact inventory must use gzip level 9")
    if compression.get("implementation") != "Python stdlib zlib":
        raise ValueError(f"{role} artifact inventory uses an unexpected gzip implementation")
    if compression.get("aggregation") != "sum of independently compressed file sizes":
        raise ValueError(f"{role} artifact inventory uses an unexpected compression aggregation")
    for field in ("platform", "pythonVersion", "zlibVersion"):
        if not isinstance(environment.get(field), str) or not environment[field]:
            raise ValueError(f"{role} artifact environment.{field} must be a non-empty string")
    if environment.get("platform") != build["environment"].get("os"):
        raise ValueError(f"{role} artifact platform does not match build environment")
    if environment.get("pythonVersion") != build["environment"].get("pythonVersion"):
        raise ValueError(f"{role} artifact Python version does not match build environment")
    input_source = require_mapping(document.get("_inputSource"), f"{role} artifact input source")
    return {
        "source": {
            "label": source["label"],
            "revision": source["revision"],
            "dirty": False,
            "sourceTreeSha256": artifact_source_tree_sha256,
        },
        "inputSource": dict(input_source),
        "artifactRoot": artifact_root,
        "generatedAtUtc": generated_at_text,
        "selection": dict(selection),
        "compression": dict(compression),
        "environment": dict(environment),
        "applicationWasm": {
            **application,
            "sourcePath": application_source,
        },
    }


def validate_build_protocol(
    baseline_build: dict[str, Any],
    candidate_build: dict[str, Any],
) -> dict[str, Any]:
    baseline_count = len(baseline_build["_measurements"])
    candidate_count = len(candidate_build["_measurements"])
    if baseline_count != candidate_count:
        raise ValueError("baseline and candidate build repetition counts differ")
    if baseline_count < 2 or baseline_count % 2 != 0:
        raise ValueError("A-B-B-A requires an even count of at least two repetitions per branch")
    if baseline_build["schemaVersion"] != candidate_build["schemaVersion"]:
        raise ValueError("baseline and candidate command schema versions differ")
    if baseline_build["rawWarmups"] or candidate_build["rawWarmups"]:
        raise ValueError("A-B-B-A branch comparisons must not contain warmup commands")
    if baseline_build["environment"] != candidate_build["environment"]:
        raise ValueError("baseline and candidate build environments differ")
    if baseline_build["semanticCommand"] != candidate_build["semanticCommand"]:
        raise ValueError("baseline and candidate semantic build commands differ")
    if baseline_build["taskAlias"] != "legacy-root":
        raise ValueError("baseline build must use the approved legacy-root production task")
    if candidate_build["taskAlias"] != "app-web-module":
        raise ValueError("candidate build must use the approved app:web module production task")
    if baseline_build["measurementVariant"] != candidate_build["measurementVariant"]:
        raise ValueError("baseline and candidate command measurement schema variants differ")
    if baseline_build["_workingDirectory"] == candidate_build["_workingDirectory"]:
        raise ValueError("baseline and candidate builds must use distinct working directories")

    combined = [
        (role, measurement)
        for role, build in (("baseline", baseline_build), ("candidate", candidate_build))
        for measurement in build["_measurements"]
    ]
    combined.sort(key=lambda entry: entry[1]["_started"])
    expected_roles = [
        role
        for _ in range(baseline_count // 2)
        for role in ("candidate", "baseline", "baseline", "candidate")
    ]
    actual_roles = [role for role, _ in combined]
    if actual_roles != expected_roles:
        raise ValueError(
            "build timestamps do not form candidate-baseline-baseline-candidate (A-B-B-A) cycles",
        )
    for previous, current in zip(combined, combined[1:]):
        if current[1]["_started"] < previous[1]["_finished"]:
            raise ValueError("build result timestamps overlap the next measurement")

    return {
        "order": "A-B-B-A",
        "roleMapping": {"A": "candidate", "B": "baseline"},
        "cycles": baseline_count // 2,
        "nonOverlapping": True,
        "distinctWorkingDirectories": True,
        "environment": dict(candidate_build["environment"]),
        "semanticBuildCommand": dict(candidate_build["semanticCommand"]),
        "taskAliases": {
            "baseline": baseline_build["taskAlias"],
            "candidate": candidate_build["taskAlias"],
        },
        "measurementVariant": candidate_build["measurementVariant"],
        "commandSchemaVersion": candidate_build["schemaVersion"],
        "runOrder": [
            {
                "sequence": sequence,
                "role": role,
                "source": dict(measurement["source"]),
                "startedAt": measurement["sample"]["startedAt"],
                "finishedAt": measurement["sample"]["finishedAt"],
                "generatedAt": measurement["generatedAt"],
            }
            for sequence, (role, measurement) in enumerate(combined, start=1)
        ],
    }


def public_build(build: dict[str, Any]) -> dict[str, Any]:
    return {
        key: build[key]
        for key in (
            "schemaVersion",
            "label",
            "revision",
            "dirty",
            "sourceTreeSha256",
            "environment",
            "semanticCommand",
            "taskAlias",
            "measurementVariant",
            "rawWarmups",
            "rawSamples",
            "wallSeconds",
            "medianWallSeconds",
        )
    }


def application_wasm(document: dict[str, Any]) -> dict[str, Any]:
    provenance = require_mapping(document.get("applicationWasm"), "applicationWasm")
    if provenance.get("matchingMethod") != APPLICATION_WASM_MATCHING_METHOD:
        raise ValueError("applicationWasm.matchingMethod is unsupported")
    source = require_mapping(provenance.get("source"), "applicationWasm.source")
    distribution = require_mapping(
        provenance.get("distribution"),
        "applicationWasm.distribution",
    )
    source_path = normalize_working_directory(source.get("path"), "applicationWasm.source.path")
    source_sha256 = require_sha256(source.get("sha256"), "applicationWasm.source.sha256")
    source_raw_bytes = require_positive_integer(
        source.get("rawBytes"),
        "applicationWasm.source.rawBytes",
    )
    distribution_path = require_relative_artifact_path(
        distribution.get("path"),
        "applicationWasm.distribution.path",
    )
    distribution_sha256 = require_sha256(
        distribution.get("sha256"),
        "applicationWasm.distribution.sha256",
    )
    distribution_raw_bytes = require_positive_integer(
        distribution.get("rawBytes"),
        "applicationWasm.distribution.rawBytes",
    )
    if source_sha256 != distribution_sha256 or source_raw_bytes != distribution_raw_bytes:
        raise ValueError("application Wasm source and distribution identities differ")

    files = require_list(document.get("files"), "files")
    wasm_matches = []
    for index, value in enumerate(files):
        item = require_mapping(value, f"files[{index}]")
        if (
            item.get("category") == "wasm"
            and item.get("sha256") == source_sha256
            and item.get("rawBytes") == source_raw_bytes
        ):
            wasm_matches.append(item)
    if len(wasm_matches) != 1:
        raise ValueError(
            "application Wasm provenance must resolve to exactly one distribution file, "
            f"found {len(wasm_matches)}",
        )
    item = wasm_matches[0]
    if item.get("path") != distribution_path:
        raise ValueError("application Wasm provenance distribution path does not match inventory")
    if "inspectionError" in item:
        raise ValueError("application Wasm inventory entry contains an inspection error")
    wasm = require_mapping(item.get("wasm"), "application Wasm inspection")
    section_counts = require_mapping(
        wasm.get("sectionEntryCounts"),
        "application Wasm sectionEntryCounts",
    )
    declared_function_count = require_non_negative_integer(
        section_counts.get("function"),
        "application Wasm declared function count",
    )
    gzip_bytes = require_positive_integer(item.get("gzipBytes"), "application Wasm gzipBytes")
    return {
        "matchingMethod": APPLICATION_WASM_MATCHING_METHOD,
        "sourcePath": source_path,
        "sourceSha256": source_sha256,
        "path": item["path"],
        "rawBytes": item["rawBytes"],
        "gzipBytes": gzip_bytes,
        "declaredFunctionCount": declared_function_count,
    }


def build_report(
    baseline_build: dict[str, Any],
    candidate_build: dict[str, Any],
    baseline_artifacts: dict[str, Any],
    candidate_artifacts: dict[str, Any],
) -> dict[str, Any]:
    build_protocol = validate_build_protocol(baseline_build, candidate_build)
    baseline_artifact_contract = validate_artifacts(
        baseline_artifacts,
        baseline_build,
        "baseline",
    )
    candidate_artifact_contract = validate_artifacts(
        candidate_artifacts,
        candidate_build,
        "candidate",
    )
    for field in ("selection", "compression", "environment"):
        if baseline_artifact_contract[field] != candidate_artifact_contract[field]:
            raise ValueError(f"baseline and candidate artifact {field} differ")
    baseline_hashes = {item["sha256"] for item in baseline_artifacts["files"]}
    candidate_hashes = {item["sha256"] for item in candidate_artifacts["files"]}
    shared_wasm_hashes = {
        item["sha256"]
        for item in baseline_artifacts["files"]
        if item["category"] == "wasm" and item["sha256"] in candidate_hashes
    }
    baseline_app_wasm = baseline_artifact_contract["applicationWasm"]
    candidate_app_wasm = candidate_artifact_contract["applicationWasm"]
    shared_wasm_hashes.discard(baseline_app_wasm["sourceSha256"])
    shared_wasm_hashes.discard(candidate_app_wasm["sourceSha256"])
    categories: list[dict[str, Any]] = []
    baseline_categories = baseline_artifacts["summary"]["byCategory"]
    candidate_categories = candidate_artifacts["summary"]["byCategory"]
    for category in sorted(set(baseline_categories) | set(candidate_categories)):
        baseline = baseline_categories.get(category, {"rawBytes": 0, "gzipBytes": 0, "fileCount": 0})
        candidate = candidate_categories.get(category, {"rawBytes": 0, "gzipBytes": 0, "fileCount": 0})
        categories.append(
            {
                "category": category,
                "baseline": baseline,
                "candidate": candidate,
                "rawDeltaPercent": percent(candidate["rawBytes"], baseline["rawBytes"]),
                "gzipDeltaPercent": percent(candidate["gzipBytes"], baseline["gzipBytes"]),
            },
        )
    return {
        "schemaVersion": 1,
        "spdxFileCopyrightText": "2026 Vladislav Tomilov",
        "spdxLicenseIdentifier": "GPL-3.0-or-later",
        "suite": "kinetickk-build-artifact-comparison",
        "buildProtocol": build_protocol,
        "artifactInventoryContract": {
            "artifactRoots": {
                "baseline": baseline_artifact_contract["artifactRoot"],
                "candidate": candidate_artifact_contract["artifactRoot"],
            },
            "applicationWasmSources": {
                "baseline": baseline_app_wasm["sourcePath"],
                "candidate": candidate_app_wasm["sourcePath"],
            },
            "selection": baseline_artifact_contract["selection"],
            "compression": baseline_artifact_contract["compression"],
            "environment": baseline_artifact_contract["environment"],
        },
        "baseline": {
            "build": public_build(baseline_build),
            "artifacts": baseline_artifact_contract["source"],
            "artifactInputSource": baseline_artifact_contract["inputSource"],
            "artifactGeneratedAtUtc": baseline_artifact_contract["generatedAtUtc"],
        },
        "candidate": {
            "build": public_build(candidate_build),
            "artifacts": candidate_artifact_contract["source"],
            "artifactInputSource": candidate_artifact_contract["inputSource"],
            "artifactGeneratedAtUtc": candidate_artifact_contract["generatedAtUtc"],
        },
        "buildWallDeltaPercent": percent(
            candidate_build["medianWallSeconds"], baseline_build["medianWallSeconds"]
        ),
        "artifacts": {
            "baselineSummary": baseline_artifacts["summary"],
            "candidateSummary": candidate_artifacts["summary"],
            "rawDeltaPercent": percent(
                candidate_artifacts["summary"]["rawBytes"],
                baseline_artifacts["summary"]["rawBytes"],
            ),
            "gzipDeltaPercent": percent(
                candidate_artifacts["summary"]["gzipBytes"],
                baseline_artifacts["summary"]["gzipBytes"],
            ),
            "declaredFunctionDeltaPercent": percent(
                candidate_artifacts["summary"]["wasmDeclaredFunctionCount"],
                baseline_artifacts["summary"]["wasmDeclaredFunctionCount"],
            ),
            "categories": categories,
            "applicationWasm": {
                "baseline": baseline_app_wasm,
                "candidate": candidate_app_wasm,
                "rawDeltaPercent": percent(
                    candidate_app_wasm["rawBytes"], baseline_app_wasm["rawBytes"]
                ),
                "gzipDeltaPercent": percent(
                    candidate_app_wasm["gzipBytes"], baseline_app_wasm["gzipBytes"]
                ),
                "declaredFunctionDeltaPercent": percent(
                    candidate_app_wasm["declaredFunctionCount"],
                    baseline_app_wasm["declaredFunctionCount"],
                ),
            },
            "sharedWasmHashes": sorted(shared_wasm_hashes),
        },
    }


def format_bytes(value: int) -> str:
    return f"{value / 1024 / 1024:.2f} MiB"


def format_percent(value: float | None) -> str:
    return "new" if value is None else f"{value:+.2f}%"


def render_markdown(report: dict[str, Any]) -> str:
    baseline = report["baseline"]["build"]
    candidate = report["candidate"]["build"]
    artifacts = report["artifacts"]
    application = artifacts["applicationWasm"]
    repetition_count = len(baseline["wallSeconds"])
    run_headers = " | ".join(
        f"Repetition {index}" for index in range(1, repetition_count + 1)
    )
    separator = "|---|" + "---:|" * (repetition_count + 1)
    baseline_runs = " | ".join(f"{value:.2f} s" for value in baseline["wallSeconds"])
    candidate_runs = " | ".join(f"{value:.2f} s" for value in candidate["wallSeconds"])
    lines = [
        "<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->",
        "<!-- SPDX-License-Identifier: GPL-3.0-or-later -->",
        "",
        "# Build and artifact performance comparison",
        "",
        f"Baseline: `{baseline['label']}` at `{baseline['revision']}`.",
        "",
        f"Candidate: `{candidate['label']}` at `{candidate['revision']}`.",
        "",
        (
            "Build measurements are a validated, non-overlapping candidate-baseline-baseline-candidate "
            "sequence (A-B-B-A with A=candidate) of clean, offline, no-daemon production builds in "
            "distinct working directories. Positive deltas mean the candidate is larger/slower."
        ),
        "",
        "## Clean production build",
        "",
        f"| Branch | {run_headers} | Median |",
        separator,
        f"| Baseline | {baseline_runs} | {baseline['medianWallSeconds']:.2f} s |",
        f"| Candidate | {candidate_runs} | {candidate['medianWallSeconds']:.2f} s |",
        "",
        f"Median delta: **{report['buildWallDeltaPercent']:+.2f}%**.",
        "",
        "## Distribution inventory",
        "",
        "| Scope | Baseline raw | Candidate raw | Raw delta | Gzip delta |",
        "|---|---:|---:|---:|---:|",
        (
            f"| Entire distribution | {format_bytes(artifacts['baselineSummary']['rawBytes'])} | "
            f"{format_bytes(artifacts['candidateSummary']['rawBytes'])} | "
            f"{artifacts['rawDeltaPercent']:+.2f}% | {artifacts['gzipDeltaPercent']:+.2f}% |"
        ),
    ]
    for category in artifacts["categories"]:
        lines.append(
            f"| `{category['category']}` | {format_bytes(category['baseline']['rawBytes'])} | "
            f"{format_bytes(category['candidate']['rawBytes'])} | "
            f"{format_percent(category['rawDeltaPercent'])} | "
            f"{format_percent(category['gzipDeltaPercent'])} |"
        )
    lines.extend(
        [
            "",
            "## Branch-specific application Wasm",
            "",
            "| Metric | Baseline | Candidate | Delta |",
            "|---|---:|---:|---:|",
            (
                f"| Raw bytes | {format_bytes(application['baseline']['rawBytes'])} | "
                f"{format_bytes(application['candidate']['rawBytes'])} | "
                f"{application['rawDeltaPercent']:+.2f}% |"
            ),
            (
                f"| Gzip-9 bytes | {format_bytes(application['baseline']['gzipBytes'])} | "
                f"{format_bytes(application['candidate']['gzipBytes'])} | "
                f"{application['gzipDeltaPercent']:+.2f}% |"
            ),
            (
                f"| Declared functions | {application['baseline']['declaredFunctionCount']:,} | "
                f"{application['candidate']['declaredFunctionCount']:,} | "
                f"{application['declaredFunctionDeltaPercent']:+.2f}% |"
            ),
            "",
            (
                "Application Wasm is identified by an explicit SHA-256 and byte-equality match "
                "to the optimized linker output. "
                f"Byte-identical non-application Wasm files: {len(artifacts['sharedWasmHashes'])}."
            ),
            "",
        ],
    )
    return "\n".join(lines)


def main() -> int:
    arguments = parse_arguments()
    try:
        report = build_report(
            load_builds(arguments.baseline_build),
            load_builds(arguments.candidate_build),
            read_json(arguments.baseline_artifacts),
            read_json(arguments.candidate_artifacts),
        )
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"build/artifact comparison failed: {error}")
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
