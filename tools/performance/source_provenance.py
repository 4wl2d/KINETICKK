#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Fail-closed Git source provenance shared by performance evidence producers."""

from __future__ import annotations

import hashlib
import os
import pathlib
import re
import shutil
import stat
import subprocess
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class SourceState:
    revision: str
    dirty: bool
    status: bytes
    source_tree_sha256: str


def git_command(
    repository: pathlib.Path,
    arguments: list[str],
    *,
    accepted_exit_codes: tuple[int, ...] = (0,),
    input_bytes: bytes | None = None,
) -> subprocess.CompletedProcess[bytes]:
    git = shutil.which("git")
    if git is None:
        raise RuntimeError("git is required to attest performance evidence")
    environment = {
        key: value
        for key, value in os.environ.items()
        if not key.startswith("GIT_")
    }
    environment["GIT_CONFIG_NOSYSTEM"] = "1"
    environment["GIT_CONFIG_GLOBAL"] = os.devnull
    completed = subprocess.run(
        [git, "-C", str(repository), *arguments],
        check=False,
        capture_output=True,
        timeout=30,
        input=input_bytes,
        env=environment,
    )
    if completed.returncode not in accepted_exit_codes:
        stderr = completed.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(
            f"git command failed ({completed.returncode}): git {' '.join(arguments)}: {stderr}",
        )
    return completed


def git_path(repository: pathlib.Path, selector: str) -> pathlib.Path:
    raw_path = git_command(repository, ["rev-parse", selector]).stdout.rstrip(b"\r\n")
    selected = pathlib.Path(os.fsdecode(raw_path))
    if not selected.is_absolute():
        selected = repository / selected
    return selected.resolve(strict=True)


def capture_git_worktree_state(repository: pathlib.Path) -> tuple[str, bytes]:
    repository = repository.resolve(strict=True)
    inside_worktree = git_command(
        repository,
        ["rev-parse", "--is-inside-work-tree"],
    ).stdout.decode("ascii", errors="strict").strip()
    if inside_worktree != "true":
        raise RuntimeError("--repo-root/--cwd must be inside a Git worktree")

    top_level_raw = git_command(repository, ["rev-parse", "--show-toplevel"]).stdout
    top_level = pathlib.Path(os.fsdecode(top_level_raw.rstrip(b"\r\n"))).resolve(strict=True)
    if top_level != repository:
        raise RuntimeError("--repo-root/--cwd must be the exact Git worktree root")

    revision = (
        git_command(repository, ["rev-parse", "--verify", "HEAD^{commit}"])
        .stdout.decode("ascii", errors="strict")
        .strip()
    )
    if re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", revision) is None:
        raise RuntimeError("Git HEAD did not resolve to a full commit object ID")
    status = git_command(
        repository,
        [
            "status",
            "--porcelain=v1",
            "-z",
            "--untracked-files=all",
            "--ignore-submodules=none",
        ],
    ).stdout
    return revision, status


def validate_declared_source(
    state: SourceState,
    *,
    revision: str | None,
    dirty: bool | None,
) -> tuple[str, bool]:
    if revision is not None and revision != state.revision:
        raise RuntimeError("--revision must exactly match git rev-parse HEAD^{commit}")
    if dirty is not None and dirty != state.dirty:
        actual = "true" if state.dirty else "false"
        raise RuntimeError(f"--dirty must exactly match Git worktree status ({actual})")
    return state.revision, state.dirty


def validate_output_path(
    *,
    repository: pathlib.Path,
    output: pathlib.Path,
    allow_existing: bool = False,
) -> None:
    repository = repository.resolve(strict=True)
    output = output.resolve(strict=False)
    if os.path.lexists(output):
        if not allow_existing:
            raise RuntimeError("evidence output must not exist before provenance validation")
        if output.is_symlink() or not output.is_file():
            raise RuntimeError("existing evidence output must be a regular non-symlink file")
    for selector in ("--absolute-git-dir", "--git-common-dir"):
        metadata_root = git_path(repository, selector)
        if output == metadata_root or metadata_root in output.parents:
            raise RuntimeError("evidence output must not be inside Git metadata")
    try:
        relative_output = output.relative_to(repository)
    except ValueError:
        return
    tracked = git_command(
        repository,
        [
            "ls-files",
            "--error-unmatch",
            "--",
            f":(literal){relative_output.as_posix()}",
        ],
        accepted_exit_codes=(0, 1),
    )
    if tracked.returncode == 0:
        raise RuntimeError("evidence output inside the worktree must not be tracked by Git")
    ignored = git_command(
        repository,
        ["check-ignore", "--no-index", "-q", "--stdin", "-z"],
        accepted_exit_codes=(0, 1),
        input_bytes=os.fsencode(relative_output.as_posix()) + b"\0",
    )
    if ignored.returncode != 0:
        raise RuntimeError("evidence output inside the worktree must be ignored by Git")


def nul_separated_paths(raw: bytes, context: str) -> list[bytes]:
    if raw and not raw.endswith(b"\0"):
        raise RuntimeError(f"{context} did not return NUL-terminated paths")
    paths = raw.rstrip(b"\0").split(b"\0") if raw else []
    for path in paths:
        components = path.split(b"/")
        if (
            not path
            or path.startswith(b"/")
            or any(component in {b"", b".", b".."} for component in components)
        ):
            raise RuntimeError(f"{context} returned an unsafe repository path")
    return paths


def update_digest_record(
    digest: Any,
    record_type: bytes,
    payload: bytes,
) -> None:
    digest.update(len(record_type).to_bytes(4, byteorder="big"))
    digest.update(record_type)
    digest.update(len(payload).to_bytes(8, byteorder="big"))
    digest.update(payload)


def filesystem_entry_identity(
    repository: pathlib.Path,
    relative_path: bytes,
    *,
    allow_missing: bool,
) -> bytes:
    path = os.path.join(os.fsencode(repository), *relative_path.split(b"/"))
    try:
        entry_stat = os.lstat(path)
    except FileNotFoundError:
        if allow_missing:
            return b"missing"
        raise RuntimeError(
            f"untracked source entry disappeared during fingerprinting: {os.fsdecode(relative_path)}",
        ) from None

    if stat.S_ISLNK(entry_stat.st_mode):
        target = os.readlink(path)
        target_bytes = target if isinstance(target, bytes) else os.fsencode(target)
        nested = hashlib.sha256()
        update_digest_record(nested, b"type", b"symlink")
        update_digest_record(nested, b"target", target_bytes)
        return nested.digest()
    if not stat.S_ISREG(entry_stat.st_mode):
        raise RuntimeError(
            "source-tree fingerprint supports only regular files, symlinks, and "
            f"missing tracked files: {os.fsdecode(relative_path)}",
        )

    content_digest = hashlib.sha256()
    open_flags = os.O_RDONLY
    if hasattr(os, "O_NOFOLLOW"):
        open_flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(path, open_flags)
    except OSError as error:
        raise RuntimeError(
            f"could not open source entry safely: {os.fsdecode(relative_path)}: {error}",
        ) from error
    try:
        opened_stat = os.fstat(descriptor)
        if not stat.S_ISREG(opened_stat.st_mode):
            raise RuntimeError(
                f"source entry changed type during fingerprinting: {os.fsdecode(relative_path)}",
            )
        while True:
            chunk = os.read(descriptor, 1024 * 1024)
            if not chunk:
                break
            content_digest.update(chunk)
        finished_stat = os.fstat(descriptor)
    finally:
        os.close(descriptor)
    stable_fields = ("st_dev", "st_ino", "st_size", "st_mtime_ns", "st_ctime_ns")
    if any(
        getattr(opened_stat, field) != getattr(finished_stat, field)
        for field in stable_fields
    ):
        raise RuntimeError(
            f"source entry changed during fingerprinting: {os.fsdecode(relative_path)}",
        )
    nested = hashlib.sha256()
    update_digest_record(nested, b"type", b"regular")
    update_digest_record(nested, b"content-sha256", content_digest.digest())
    return nested.digest()


def source_tree_sha256(
    repository: pathlib.Path,
    *,
    revision: str,
    status: bytes,
) -> str:
    index = git_command(
        repository,
        ["ls-files", "--stage", "-v", "-z", "--full-name"],
    ).stdout
    tracked_paths = sorted(
        set(
            nul_separated_paths(
                git_command(
                    repository,
                    ["ls-files", "--cached", "-z", "--full-name"],
                ).stdout,
                "git ls-files --cached",
            ),
        ),
    )
    untracked_paths = sorted(
        set(
            nul_separated_paths(
                git_command(
                    repository,
                    [
                        "ls-files",
                        "--others",
                        "--exclude-standard",
                        "-z",
                        "--full-name",
                    ],
                ).stdout,
                "git ls-files --others",
            ),
        ),
    )

    digest = hashlib.sha256()
    update_digest_record(digest, b"schema", b"kinetickk-source-tree-v1")
    update_digest_record(digest, b"head-commit", revision.encode("ascii"))
    update_digest_record(digest, b"porcelain-status-v1-z", status)
    update_digest_record(digest, b"index-stage-v-z", index)
    for relative_path in tracked_paths:
        update_digest_record(digest, b"tracked-path", relative_path)
        update_digest_record(
            digest,
            b"tracked-worktree-entry",
            filesystem_entry_identity(repository, relative_path, allow_missing=True),
        )
    for relative_path in untracked_paths:
        update_digest_record(digest, b"untracked-path", relative_path)
        update_digest_record(
            digest,
            b"untracked-worktree-entry",
            filesystem_entry_identity(repository, relative_path, allow_missing=False),
        )
    return digest.hexdigest()


def capture_source_state(repository: pathlib.Path) -> SourceState:
    revision, status = capture_git_worktree_state(repository)
    return SourceState(
        revision=revision,
        dirty=bool(status),
        status=status,
        source_tree_sha256=source_tree_sha256(
            repository,
            revision=revision,
            status=status,
        ),
    )


def require_unchanged_source(before: SourceState, after: SourceState) -> None:
    if before != after:
        raise RuntimeError("exact Git source tree changed while evidence was recorded")


def current_branch(repository: pathlib.Path) -> str | None:
    branch = (
        git_command(repository, ["branch", "--show-current"])
        .stdout.decode("utf-8", errors="strict")
        .strip()
    )
    return branch or None
