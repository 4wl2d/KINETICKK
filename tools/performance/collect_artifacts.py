#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Create a deterministic size and structure inventory for build artifacts."""

from __future__ import annotations

import argparse
import datetime as dt
import fnmatch
import hashlib
import json
import os
import pathlib
import platform
import subprocess
import sys
import zipfile
import zlib
from collections import defaultdict
from typing import Any

import source_provenance


SCHEMA_VERSION = 3
SUITE_NAME = "kinetickk-artifact-inventory"
READ_CHUNK_BYTES = 1_048_576
APPLICATION_WASM_MATCHING_METHOD = "sha256-and-byte-equality"


def logical_repository_namespace(revision: str) -> str:
    if not revision or revision in {".", ".."} or any(
        character not in "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-"
        for character in revision
    ):
        raise ValueError("revision must be a non-empty path-safe source identity")
    return f"repository/{revision}"


def logical_repository_path(
    path: pathlib.Path,
    repository: pathlib.Path,
    revision: str,
) -> str:
    try:
        relative = path.relative_to(repository)
    except ValueError as error:
        raise ValueError("artifact root must be inside the source repository") from error
    if relative == pathlib.Path("."):
        raise ValueError("artifact root must identify a repository-relative artifact")
    return f"{logical_repository_namespace(revision)}/{relative.as_posix()}"


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


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


def classify(relative_path: str) -> str:
    lower = relative_path.lower()
    suffix = pathlib.PurePosixPath(lower).suffix
    if suffix == ".wasm":
        return "wasm"
    if suffix in {".js", ".mjs", ".cjs"}:
        return "javascript"
    if suffix == ".map":
        return "source-map"
    if suffix in {".html", ".htm"}:
        return "html"
    if suffix in {".css"}:
        return "css"
    if suffix in {".jar", ".zip", ".aar"}:
        return "archive"
    if suffix in {".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico"}:
        return "image"
    if suffix in {".mp3", ".ogg", ".wav", ".m4a", ".aac", ".flac"}:
        return "audio"
    if suffix in {".woff", ".woff2", ".ttf", ".otf"}:
        return "font"
    if pathlib.PurePosixPath(lower).name in {
        "license", "license.txt", "notice", "notice.txt", "third_party_notices.md",
    } or "/meta-inf/" in f"/{lower}":
        return "legal-metadata"
    return "other"


def read_uleb128(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    for _ in range(10):
        if offset >= len(data):
            raise ValueError("truncated unsigned LEB128 value")
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte & 0x80 == 0:
            return value, offset
        shift += 7
    raise ValueError("unsigned LEB128 value exceeds ten bytes")


def inspect_wasm(path: pathlib.Path) -> dict[str, Any]:
    data = path.read_bytes()
    if len(data) < 8 or data[:4] != b"\x00asm":
        raise ValueError("not a WebAssembly binary module")
    version = int.from_bytes(data[4:8], "little")
    offset = 8
    sections: list[dict[str, Any]] = []
    section_counts: dict[str, int] = {}
    custom_names: list[str] = []
    section_names = {
        0: "custom",
        1: "type",
        2: "import",
        3: "function",
        4: "table",
        5: "memory",
        6: "global",
        7: "export",
        8: "start",
        9: "element",
        10: "code",
        11: "data",
        12: "data-count",
        13: "tag",
    }
    count_sections = {1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 13}
    while offset < len(data):
        section_id = data[offset]
        offset += 1
        payload_size, payload_start = read_uleb128(data, offset)
        payload_end = payload_start + payload_size
        if payload_end > len(data):
            raise ValueError("WebAssembly section exceeds file boundary")
        name = section_names.get(section_id, f"unknown-{section_id}")
        section: dict[str, Any] = {
            "id": section_id,
            "name": name,
            "payloadBytes": payload_size,
        }
        if section_id in count_sections and payload_size:
            entry_count, _ = read_uleb128(data, payload_start)
            section["entryCount"] = entry_count
            section_counts[name] = section_counts.get(name, 0) + entry_count
        elif section_id == 0 and payload_size:
            name_length, name_start = read_uleb128(data, payload_start)
            name_end = name_start + name_length
            if name_end <= payload_end:
                custom_name = data[name_start:name_end].decode("utf-8", errors="replace")
                section["customName"] = custom_name
                custom_names.append(custom_name)
        sections.append(section)
        offset = payload_end
    return {
        "version": version,
        "sectionCount": len(sections),
        "sectionEntryCounts": section_counts,
        "customSectionNames": custom_names,
        "sections": sections,
    }


def inspect_archive(path: pathlib.Path) -> dict[str, Any]:
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        class_entries = [entry for entry in entries if entry.filename.endswith(".class")]
        kotlin_metadata_entries = [
            entry for entry in entries
            if entry.filename.endswith((".kotlin_module", ".kotlin_metadata"))
        ]
        return {
            "entryCount": len(entries),
            "fileEntryCount": sum(not entry.is_dir() for entry in entries),
            "directoryEntryCount": sum(entry.is_dir() for entry in entries),
            "classFileCount": len(class_entries),
            "kotlinMetadataFileCount": len(kotlin_metadata_entries),
            "entryUncompressedBytes": sum(entry.file_size for entry in entries),
            "entryCompressedBytes": sum(entry.compress_size for entry in entries),
        }


def measure_file(path: pathlib.Path) -> tuple[int, int, str, int]:
    digest = hashlib.sha256()
    compressor = zlib.compressobj(level=9, method=zlib.DEFLATED, wbits=31)
    raw_bytes = 0
    gzip_bytes = 0
    newline_count = 0
    with path.open("rb") as stream:
        while True:
            chunk = stream.read(READ_CHUNK_BYTES)
            if not chunk:
                break
            raw_bytes += len(chunk)
            newline_count += chunk.count(b"\n")
            digest.update(chunk)
            gzip_bytes += len(compressor.compress(chunk))
    gzip_bytes += len(compressor.flush())
    return raw_bytes, gzip_bytes, digest.hexdigest(), newline_count


def file_identity(path: pathlib.Path) -> tuple[int, str]:
    digest = hashlib.sha256()
    raw_bytes = 0
    with path.open("rb") as stream:
        while True:
            chunk = stream.read(READ_CHUNK_BYTES)
            if not chunk:
                break
            raw_bytes += len(chunk)
            digest.update(chunk)
    return raw_bytes, digest.hexdigest()


def files_equal(left: pathlib.Path, right: pathlib.Path) -> bool:
    with left.open("rb") as left_stream, right.open("rb") as right_stream:
        while True:
            left_chunk = left_stream.read(READ_CHUNK_BYTES)
            right_chunk = right_stream.read(READ_CHUNK_BYTES)
            if left_chunk != right_chunk:
                return False
            if not left_chunk:
                return True


def resolve_application_wasm(
    source: pathlib.Path,
    artifacts: list[tuple[dict[str, Any], pathlib.Path]],
) -> tuple[dict[str, Any], dict[str, Any]]:
    if source.is_symlink() or not source.is_file():
        raise RuntimeError("application Wasm source must be a regular non-symlink file")
    if source.suffix.lower() != ".wasm":
        raise RuntimeError("application Wasm source must use the .wasm suffix")
    try:
        inspect_wasm(source)
    except (OSError, ValueError) as error:
        raise RuntimeError(f"application Wasm source is invalid: {error}") from error

    source_raw_bytes, source_sha256 = file_identity(source)
    digest_matches = [
        (entry, path)
        for entry, path in artifacts
        if entry["category"] == "wasm"
        and entry["rawBytes"] == source_raw_bytes
        and entry["sha256"] == source_sha256
    ]
    exact_matches = [
        (entry, path)
        for entry, path in digest_matches
        if files_equal(source, path)
    ]
    if len(exact_matches) != 1:
        raise RuntimeError(
            "application Wasm provenance requires exactly one byte-identical distribution file; "
            f"found {len(exact_matches)} among {len(artifacts)} selected files",
        )
    entry, _ = exact_matches[0]
    if "inspectionError" in entry or "wasm" not in entry:
        raise RuntimeError("byte-identical application Wasm distribution file is not valid WebAssembly")
    return (
        {
            "rawBytes": source_raw_bytes,
            "sha256": source_sha256,
        },
        entry,
    )


def selected(path: str, includes: list[str], excludes: list[str]) -> bool:
    if includes and not any(fnmatch.fnmatch(path, pattern) for pattern in includes):
        return False
    return not any(fnmatch.fnmatch(path, pattern) for pattern in excludes)


def discover_files(root: pathlib.Path, includes: list[str], excludes: list[str]) -> list[tuple[str, pathlib.Path]]:
    if root.is_file():
        return [(root.name, root)] if selected(root.name, includes, excludes) else []
    discovered: list[tuple[str, pathlib.Path]] = []
    for path in root.rglob("*"):
        if path.is_symlink() or not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        if selected(relative, includes, excludes):
            discovered.append((relative, path))
    return sorted(discovered, key=lambda entry: entry[0])


def aggregate(files: list[dict[str, Any]], field: str) -> dict[str, Any]:
    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for file in files:
        groups[str(file[field])].append(file)
    return {
        key: {
            "fileCount": len(entries),
            "rawBytes": sum(entry["rawBytes"] for entry in entries),
            "gzipBytes": sum(entry["gzipBytes"] for entry in entries),
            "gzipToRawRatio": (
                sum(entry["gzipBytes"] for entry in entries)
                / sum(entry["rawBytes"] for entry in entries)
                if sum(entry["rawBytes"] for entry in entries)
                else None
            ),
        }
        for key, entries in sorted(groups.items())
    }


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Inventory raw and deterministic gzip-9 sizes plus Wasm/archive structure.",
    )
    parser.add_argument("--root", required=True, type=pathlib.Path, help="artifact file or directory")
    parser.add_argument(
        "--application-wasm-source",
        required=True,
        type=pathlib.Path,
        help="optimized application Wasm before bundling; must match exactly one selected artifact",
    )
    parser.add_argument("--output", required=True, type=pathlib.Path, help="JSON result file")
    parser.add_argument("--label", required=True)
    parser.add_argument("--revision", help="exact source revision; defaults to repository HEAD")
    parser.add_argument("--repo-root", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--dirty", choices=("auto", "true", "false"), default="auto")
    parser.add_argument("--include", action="append", default=[], help="relative-path glob; repeatable")
    parser.add_argument("--exclude", action="append", default=[], help="relative-path glob; repeatable")
    parser.add_argument("--largest", type=int, default=25)
    parser.add_argument("--overwrite", action="store_true")
    arguments = parser.parse_args()
    if arguments.largest < 0:
        parser.error("--largest must not be negative")
    return arguments


def main() -> int:
    arguments = parse_arguments()
    root = arguments.root.expanduser().resolve()
    application_wasm_argument = arguments.application_wasm_source.expanduser().absolute()
    if application_wasm_argument.is_symlink():
        raise RuntimeError("application Wasm source must not be a symlink")
    application_wasm_source = application_wasm_argument.resolve()
    repository = arguments.repo_root.expanduser().resolve()
    output = arguments.output.expanduser().resolve()
    temporary = output.with_name(output.name + f".{os.getpid()}.tmp")
    source_before = source_provenance.capture_source_state(repository)
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
    source_provenance.validate_output_path(
        repository=repository,
        output=output,
        allow_existing=arguments.overwrite,
    )
    source_provenance.validate_output_path(
        repository=repository,
        output=temporary,
    )
    if not root.exists():
        raise RuntimeError(f"artifact root does not exist: {root}")
    try:
        application_wasm_source.relative_to(repository)
    except ValueError as error:
        raise RuntimeError("application Wasm source must be inside the source repository") from error
    try:
        application_wasm_source.relative_to(root)
    except ValueError:
        pass
    else:
        raise RuntimeError("application Wasm source must be independent from the artifact root")
    if output.exists() and not arguments.overwrite:
        raise RuntimeError(f"refusing to overwrite existing result without --overwrite: {output}")

    discovered = discover_files(root, arguments.include, arguments.exclude)
    if not discovered:
        raise RuntimeError("artifact selection contains no regular files")
    files: list[dict[str, Any]] = []
    artifact_files: list[tuple[dict[str, Any], pathlib.Path]] = []
    manifest_digest = hashlib.sha256()
    for relative, path in discovered:
        raw_bytes, gzip_bytes, sha256, newline_count = measure_file(path)
        category = classify(relative)
        suffix = pathlib.PurePosixPath(relative).suffix.lower() or "<none>"
        entry: dict[str, Any] = {
            "path": relative,
            "category": category,
            "suffix": suffix,
            "rawBytes": raw_bytes,
            "gzipBytes": gzip_bytes,
            "gzipToRawRatio": gzip_bytes / raw_bytes if raw_bytes else None,
            "sha256": sha256,
        }
        if category in {"javascript", "source-map", "html", "css"}:
            entry["newlineCount"] = newline_count
        try:
            if category == "wasm":
                entry["wasm"] = inspect_wasm(path)
            elif category == "archive":
                entry["archive"] = inspect_archive(path)
        except (OSError, ValueError, zipfile.BadZipFile) as error:
            entry["inspectionError"] = str(error)
        files.append(entry)
        artifact_files.append((entry, path))
        manifest_digest.update(relative.encode("utf-8"))
        manifest_digest.update(b"\x00")
        manifest_digest.update(sha256.encode("ascii"))
        manifest_digest.update(b"\x00")
        manifest_digest.update(str(raw_bytes).encode("ascii"))
        manifest_digest.update(b"\n")

    total_raw = sum(file["rawBytes"] for file in files)
    total_gzip = sum(file["gzipBytes"] for file in files)
    class_count = sum(
        file.get("archive", {}).get("classFileCount", 0)
        for file in files
    )
    wasm_function_count = sum(
        file.get("wasm", {}).get("sectionEntryCounts", {}).get("function", 0)
        for file in files
    )
    repository_namespace = logical_repository_namespace(revision)
    artifact_root = logical_repository_path(root, repository, revision)
    application_source_identity, application_distribution = resolve_application_wasm(
        application_wasm_source,
        artifact_files,
    )
    result = {
        "schemaVersion": SCHEMA_VERSION,
        "spdxFileCopyrightText": "2026 Vladislav Tomilov",
        "spdxLicenseIdentifier": "GPL-3.0-or-later",
        "suite": SUITE_NAME,
        "generatedAtUtc": utc_now(),
        "source": {
            "label": arguments.label,
            "revision": revision,
            "branch": source_provenance.current_branch(repository),
            "dirty": dirty,
            "sourceTreeSha256": source_before.source_tree_sha256,
            "repository": repository_namespace,
        },
        "artifactRoot": artifact_root,
        "applicationWasm": {
            "matchingMethod": APPLICATION_WASM_MATCHING_METHOD,
            "source": {
                "path": logical_repository_path(application_wasm_source, repository, revision),
                **application_source_identity,
            },
            "distribution": {
                "path": application_distribution["path"],
                "rawBytes": application_distribution["rawBytes"],
                "sha256": application_distribution["sha256"],
            },
        },
        "selection": {
            "includes": arguments.include,
            "excludes": arguments.exclude,
            "symlinksFollowed": False,
        },
        "compression": {
            "algorithm": "gzip",
            "level": 9,
            "implementation": "Python stdlib zlib",
            "aggregation": "sum of independently compressed file sizes",
            "brotliAvailableInStandardLibrary": False,
        },
        "environment": {
            "platform": platform.platform(),
            "pythonVersion": platform.python_version(),
            "zlibVersion": zlib.ZLIB_VERSION,
        },
        "summary": {
            "fileCount": len(files),
            "rawBytes": total_raw,
            "gzipBytes": total_gzip,
            "gzipToRawRatio": total_gzip / total_raw if total_raw else None,
            "archiveClassFileCount": class_count,
            "wasmDeclaredFunctionCount": wasm_function_count,
            "manifestSha256": manifest_digest.hexdigest(),
            "byCategory": aggregate(files, "category"),
            "bySuffix": aggregate(files, "suffix"),
            "largestRawFiles": sorted(
                ({"path": file["path"], "bytes": file["rawBytes"]} for file in files),
                key=lambda entry: (-entry["bytes"], entry["path"]),
            )[:arguments.largest],
            "largestGzipFiles": sorted(
                ({"path": file["path"], "bytes": file["gzipBytes"]} for file in files),
                key=lambda entry: (-entry["bytes"], entry["path"]),
            )[:arguments.largest],
        },
        "files": files,
    }

    source_after = source_provenance.capture_source_state(repository)
    source_provenance.require_unchanged_source(source_before, source_after)

    output.parent.mkdir(parents=True, exist_ok=True)
    temporary.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.replace(temporary, output)
    print(json.dumps({
        "output": str(output),
        "fileCount": len(files),
        "rawBytes": total_raw,
        "gzipBytes": total_gzip,
        "manifestSha256": manifest_digest.hexdigest(),
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError) as error:
        print(f"artifact collection failed: {error}", file=sys.stderr)
        raise SystemExit(2)
