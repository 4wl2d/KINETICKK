#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later
"""Create a portable, byte-reproducible archive of benchmark evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import stat
import zipfile


FIXED_ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)


class EvidenceArchiveError(RuntimeError):
    pass


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def archive_entry(name: str, data: bytes) -> tuple[zipfile.ZipInfo, bytes]:
    info = zipfile.ZipInfo(name, FIXED_ZIP_TIMESTAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = (stat.S_IFREG | 0o644) << 16
    return info, data


def collect_files(source: pathlib.Path) -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for path in sorted(source.rglob("*"), key=lambda item: item.as_posix()):
        if path.is_symlink():
            raise EvidenceArchiveError(f"symlinks are not allowed: {path}")
        if path.is_dir():
            continue
        if not path.is_file():
            raise EvidenceArchiveError(f"unsupported evidence entry: {path}")
        files.append(path)
    if not files:
        raise EvidenceArchiveError(f"evidence directory is empty: {source}")
    return files


def create_archive(
    source: pathlib.Path,
    output: pathlib.Path,
    logical_root: str,
) -> dict[str, object]:
    source = source.resolve(strict=True)
    output = output.resolve(strict=False)
    if not source.is_dir():
        raise EvidenceArchiveError(f"source is not a directory: {source}")
    if not logical_root or pathlib.PurePosixPath(logical_root).name != logical_root:
        raise EvidenceArchiveError("logical root must be one portable path segment")
    if output.exists():
        raise EvidenceArchiveError(f"refusing to overwrite archive: {output}")
    files = collect_files(source)
    entries: list[dict[str, object]] = []
    payloads: list[tuple[str, bytes]] = []
    for path in files:
        relative = path.relative_to(source).as_posix()
        data = path.read_bytes()
        entries.append(
            {"path": relative, "bytes": len(data), "sha256": sha256_bytes(data)},
        )
        payloads.append((f"{logical_root}/{relative}", data))
    manifest: dict[str, object] = {
        "schemaVersion": 1,
        "logicalRoot": logical_root,
        "fileCount": len(entries),
        "totalBytes": sum(int(entry["bytes"]) for entry in entries),
        "files": entries,
    }
    manifest_bytes = (
        json.dumps(manifest, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    ).encode("utf-8")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(f".{output.name}.tmp-{os.getpid()}")
    if temporary.exists():
        raise EvidenceArchiveError(f"temporary archive already exists: {temporary}")
    try:
        with zipfile.ZipFile(
            temporary,
            mode="x",
            compression=zipfile.ZIP_DEFLATED,
            compresslevel=9,
        ) as archive:
            for name, data in payloads:
                archive.writestr(*archive_entry(name, data))
            archive.writestr(
                *archive_entry(f"{logical_root}/evidence-manifest.json", manifest_bytes),
            )
        os.replace(temporary, output)
    finally:
        if temporary.exists():
            temporary.unlink()
    return {
        **manifest,
        "archive": output.name,
        "archiveBytes": output.stat().st_size,
        "archiveSha256": sha256_bytes(output.read_bytes()),
    }


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create a deterministic ZIP with a SHA-256 evidence manifest.",
    )
    parser.add_argument("--source", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--logical-root", required=True)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    result = create_archive(
        arguments.source,
        arguments.output,
        arguments.logical_root,
    )
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
