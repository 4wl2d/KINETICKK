#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect the actual build outputs, without trusting filenames as variant evidence.

Example:
  uv run python tools/build/verify_variants.py --platform android \
    --debug app/android/build-debug/outputs/apk/debug/app-android-debug.apk \
    --release app/android/build/outputs/apk/release/app-android-release-unsigned.apk \
    --output build/variant-verification/android.json

Use app/desktop's compose/jars directories (uber JARs), or complete packaged app directories, for desktop.
This verifies bytes and packaging, not gameplay, FPS or GPU presentation.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

SENTINEL = "KINETICKK_DEBUG_DIAGNOSTICS_V1"
# These implementations must be physically absent, even when the no-op API remains.
FORBIDDEN = (
    SENTINEL,
    "kinetickk/app/shared/diagnostics/DebugSession",
    "kinetickk/app/shared/DesktopCrashReporter",
    "kinetickk/ball/gameplay/interaction/performance/GameplayPerformanceTelemetry",
    "kinetickk/ball/gameplay/interaction/canvas/PerformanceHudRenderer",
)
ENCODED = {marker: (marker.encode(), marker.encode("utf-16-le")) for marker in FORBIDDEN}
OVERLAP = max(len(value) for values in ENCODED.values() for value in values) - 1
CODE_SUFFIXES = {".class", ".dex", ".wasm", ".js", ".mjs", ".cjs", ".kotlin_metadata", ".knm"}


def scan_stream(stream) -> tuple[str, int, list[str]]:
    digest = hashlib.sha256()
    size = 0
    tail = b""
    matches: set[str] = set()
    while chunk := stream.read(1 << 20):
        digest.update(chunk)
        size += len(chunk)
        window = tail + chunk
        for marker, encodings in ENCODED.items():
            if any(encoded in window for encoded in encodings):
                matches.add(marker)
        tail = window[-OVERLAP:]
    return digest.hexdigest(), size, sorted(matches)


def inspect_file(path: Path, label: str) -> dict:
    with path.open("rb") as stream:
        digest, size, raw_matches = scan_stream(stream)
    matches = [{"entry": label, "markers": raw_matches}] if raw_matches else []
    entries = 0
    if path.suffix in {".jar", ".apk", ".aab", ".zip"}:
        with zipfile.ZipFile(path) as archive:
            entries = inspect_archive(archive, label, matches)
    return {"path": label, "bytes": size, "sha256": digest, "archiveEntries": entries, "diagnosticMatches": matches}


def inspect_archive(archive: zipfile.ZipFile, label: str, matches: list, depth: int = 0) -> int:
    if depth > 4:
        raise ValueError(f"Archive nesting exceeds the inspection limit: {label}")
    entries = 0
    for item in archive.infolist():
        if item.is_dir():
            continue
        entries += 1
        entry_label = f"{label}!{item.filename}"
        found = {marker for marker in FORBIDDEN if marker in item.filename}
        suffix = Path(item.filename).suffix
        if suffix in {".jar", ".zip"}:
            if item.file_size > 512 * 1024 * 1024:
                raise ValueError(f"Nested archive exceeds the inspection limit: {entry_label}")
            with zipfile.ZipFile(io.BytesIO(archive.read(item))) as nested:
                entries += inspect_archive(nested, entry_label, matches, depth + 1)
        elif suffix in CODE_SUFFIXES:
            with archive.open(item) as stream:
                _, _, content_matches = scan_stream(stream)
            found.update(content_matches)
        if found:
            matches.append({"entry": entry_label, "markers": sorted(found)})
    return entries


def inspect_paths(paths: list[Path]) -> dict:
    artifacts = []
    seen: set[Path] = set()
    for selected in paths:
        if not selected.exists():
            raise ValueError(f"Missing artifact: {selected}")
        if selected.is_symlink():
            raise ValueError(f"Select the real artifact, not a symlink: {selected}")
        files = sorted(p for p in selected.rglob("*") if p.is_file() and not p.is_symlink()) if selected.is_dir() else [selected]
        if not files:
            raise ValueError(f"Empty artifact directory: {selected}")
        for path in files:
            resolved = path.resolve()
            if resolved not in seen:
                seen.add(resolved)
                artifacts.append(inspect_file(path, str(path)))
    return {"totalBytes": sum(item["bytes"] for item in artifacts), "artifacts": artifacts}


def verify(debug: dict, release: dict, platform: str) -> list[str]:
    failures = []
    if not any(SENTINEL in match["markers"] for item in debug["artifacts"] for match in item["diagnosticMatches"]):
        failures.append("Debug output lacks the recorder sentinel; wrong or incomplete artifacts were selected.")
    for item in release["artifacts"]:
        for match in item["diagnosticMatches"]:
            failures.append(f"Release contains diagnostics: {match['entry']}: {', '.join(match['markers'])}")
        if platform == "web" and item["path"].endswith(".map"):
            failures.append(f"Release web output contains a source map: {item['path']}")
    return failures


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--platform", choices=("desktop", "android", "web"), required=True)
    parser.add_argument("--debug", type=Path, action="append", required=True)
    parser.add_argument("--release", type=Path, action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reference", type=Path, help="Previous passing report from the same paths; require byte-identical rebuilt artifacts")
    args = parser.parse_args(argv)
    try:
        debug = inspect_paths(args.debug)
        release = inspect_paths(args.release)
        failures = verify(debug, release, args.platform)
        if args.reference:
            reference = json.loads(args.reference.read_text())
            if reference.get("schemaVersion") != 1 or not reference.get("passed") or reference.get("platform") != args.platform:
                raise ValueError("Repeatability reference must be a passing schema-v1 report for the same platform")
            for variant, current in (("debug", debug), ("release", release)):
                previous = {item["path"]: item["sha256"] for item in reference[variant]["artifacts"]}
                current_hashes = {item["path"]: item["sha256"] for item in current["artifacts"]}
                if previous != current_hashes:
                    failures.append(f"{variant} rebuilt output is not byte-identical to the reference (or selected paths differ).")
        report = {
            "schemaVersion": 1,
            "platform": args.platform,
            "debug": debug,
            "release": release,
            "releaseBytesSavedAgainstDebug": debug["totalBytes"] - release["totalBytes"],
            "releaseReductionPercentAgainstDebug": 100 * (1 - release["totalBytes"] / debug["totalBytes"]) if debug["totalBytes"] else None,
            "repeatabilityReference": str(args.reference) if args.reference else None,
            "passed": not failures,
            "failures": failures,
            "limits": [
                "Selected artifacts must belong to the current build; hashes permit checking that identity.",
                "Byte scans complement source-set exclusion; they do not prove functional equivalence or runtime speed.",
                "Debug-to-Release size includes both removal of diagnostics and compiler/packaging optimizations.",
                "Frame time, CPU/GPU, memory and startup require matched runtime measurements.",
            ],
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
        print(json.dumps({"passed": not failures, "debugBytes": debug["totalBytes"], "releaseBytes": release["totalBytes"], "failures": failures, "report": str(args.output)}, ensure_ascii=False))
        return 1 if failures else 0
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
        print(f"Variant verification failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
