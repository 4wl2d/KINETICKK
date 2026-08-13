#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Focused tests for portable raw benchmark provenance."""

from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

import browser_benchmark
import collect_artifacts
import measure_command
import source_provenance


REVISION = "0123456789abcdef0123456789abcdef01234567"
STATIC_BUILD_ENVIRONMENT = {
    "os": "TestOS-1.0-x86_64",
    "architecture": "x86_64",
    "pythonVersion": "3.12.0",
    "processorCount": 8,
    "javaVersion": (
        "Launcher JVM: 17.0.20 (Eclipse Adoptium 17.0.20+8)\n"
        "Daemon JVM: /opt/java/openjdk (no JDK specified, using current Java home)"
    ),
    "gradleDistributionSha256": "d" * 64,
    "kotlinVersion": "2.3.20",
}


def git_output(repository: pathlib.Path, *arguments: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repository), *arguments],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def initialize_git_repository(repository: pathlib.Path) -> str:
    repository.mkdir()
    (repository / ".gitignore").write_text("build/\n", encoding="utf-8")
    (repository / "README.md").write_text("test repository\n", encoding="utf-8")
    wrapper = repository / "gradlew"
    wrapper.write_text("#!/bin/sh\nexit 99\n", encoding="utf-8")
    wrapper.chmod(0o755)
    git_output(repository, "init", "-q")
    git_output(repository, "add", ".gitignore", "README.md", "gradlew")
    git_output(
        repository,
        "-c",
        "user.name=Performance Test",
        "-c",
        "user.email=performance@example.invalid",
        "commit",
        "-q",
        "-m",
        "initial",
    )
    return git_output(repository, "rev-parse", "HEAD")
GRADLE_TOOL_VERSIONS = {
    "nodeVersion": "25.0.0",
    "binaryenVersion": "125",
}


class PortableProvenanceTest(unittest.TestCase):
    def test_generators_share_stable_repository_namespace(self) -> None:
        expected = f"repository/{REVISION}"
        self.assertEqual(expected, browser_benchmark.logical_repository_namespace(REVISION))
        self.assertEqual(expected, collect_artifacts.logical_repository_namespace(REVISION))
        self.assertEqual(expected, measure_command.logical_repository_namespace(REVISION))
        with self.assertRaises(ValueError):
            collect_artifacts.logical_repository_namespace("feature/unsafe")

    def test_repository_wrapper_provenance_is_relative_and_versioned(self) -> None:
        repository = pathlib.Path(__file__).resolve().parents[2]
        wrapper = repository / "tools/performance/playwright_cli.sh"
        state = source_provenance.SourceState(REVISION, True, b" M README.md\0", "c" * 64)
        provenance = browser_benchmark.repository_wrapper_provenance(
            wrapper,
            repository,
            "playwright-cli 0.1.18",
            state,
        )

        serialized = json.dumps(provenance)
        self.assertNotIn(str(repository), serialized)
        self.assertEqual("tools/performance/playwright_cli.sh", provenance["path"])
        self.assertEqual("@playwright/cli", provenance["package"])
        self.assertEqual("0.1.18", provenance["packageVersion"])
        self.assertEqual(64, len(provenance["sha256"]))
        self.assertTrue(provenance["repositoryDirty"])

    def test_browser_attests_external_target_and_tool_worktrees_independently(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            target = root / "target"
            tool = root / "tool"
            target_revision = initialize_git_repository(target)
            tool_revision = initialize_git_repository(tool)
            (target / "README.md").write_text("target dirty\n", encoding="utf-8")

            target_state, tool_state = browser_benchmark.capture_browser_source_states(
                target,
                tool,
            )

            self.assertEqual(target_revision, target_state.revision)
            self.assertEqual(tool_revision, tool_state.revision)
            self.assertTrue(target_state.dirty)
            self.assertFalse(tool_state.dirty)
            self.assertNotEqual(target_state.source_tree_sha256, tool_state.source_tree_sha256)

    def test_artifact_result_contains_only_logical_source_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary) / "checkout"
            revision = initialize_git_repository(repository)
            artifact_root = repository / "app/web/build/dist/wasmJs/productionExecutable"
            artifact_root.mkdir(parents=True)
            (artifact_root / "index.html").write_text("ok", encoding="utf-8")
            application_source = (
                repository
                / "app/web/build/compileSync/wasmJs/main/productionExecutable/optimized/kinetickk.wasm"
            )
            application_source.parent.mkdir(parents=True)
            application_source.write_bytes(b"\x00asm\x01\x00\x00\x00")
            (artifact_root / "application.wasm").write_bytes(application_source.read_bytes())
            output = pathlib.Path(temporary) / "artifacts.json"
            arguments = [
                "collect_artifacts.py",
                "--root", str(artifact_root),
                "--application-wasm-source", str(application_source),
                "--output", str(output),
                "--label", "feature",
                "--revision", revision,
                "--repo-root", str(repository),
                "--dirty", "false",
            ]
            with (
                mock.patch.object(sys, "argv", arguments),
            ):
                self.assertEqual(0, collect_artifacts.main())
            document = json.loads(output.read_text(encoding="utf-8"))

        serialized = json.dumps(document)
        self.assertNotIn(temporary, serialized)
        self.assertEqual(revision, document["source"]["revision"])
        self.assertEqual(64, len(document["source"]["sourceTreeSha256"]))
        self.assertEqual(f"repository/{revision}", document["source"]["repository"])
        self.assertEqual(
            f"repository/{revision}/app/web/build/dist/wasmJs/productionExecutable",
            document["artifactRoot"],
        )
        self.assertEqual(
            (
                f"repository/{revision}/app/web/build/compileSync/wasmJs/main/"
                "productionExecutable/optimized/kinetickk.wasm"
            ),
            document["applicationWasm"]["source"]["path"],
        )
        self.assertEqual(
            "application.wasm",
            document["applicationWasm"]["distribution"]["path"],
        )

    def test_measurement_result_uses_logical_working_directory(self) -> None:
        sample = {
            "index": 1,
            "startedAt": "2026-01-01T00:00:00Z",
            "finishedAt": "2026-01-01T00:00:01Z",
            "wallNs": 1_000_000_000,
            "userCpuNs": 1,
            "systemCpuNs": 1,
            "waitedChildCpuUtilizationPercent": 0.0,
            "exitCode": 0,
            "timedOut": False,
        }
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary) / "checkout"
            revision = initialize_git_repository(repository)
            output = repository / "build/performance/measurement.json"
            arguments = [
                "measure_command.py",
                "--output", str(output),
                "--cwd", str(repository),
                "--label", "feature",
                "--revision", revision,
                "--dirty", "false",
                "--", "./gradlew", "task",
            ]
            with (
                mock.patch.object(sys, "argv", arguments),
                mock.patch.object(measure_command, "run_once", return_value=sample),
                mock.patch.object(
                    measure_command,
                    "capture_static_build_environment",
                    return_value=STATIC_BUILD_ENVIRONMENT,
                ) as static_environment,
                mock.patch.object(
                    measure_command,
                    "capture_gradle_tool_versions",
                    return_value=GRADLE_TOOL_VERSIONS,
                ),
            ):
                self.assertEqual(0, measure_command.main())
            document = json.loads(output.read_text(encoding="utf-8"))
            status_after = git_output(repository, "status", "--porcelain=v1")

        self.assertNotIn(temporary, json.dumps(document))
        self.assertEqual(2, document["schemaVersion"])
        self.assertEqual(f"repository/{revision}", document["workingDirectory"])
        self.assertEqual(
            {**STATIC_BUILD_ENVIRONMENT, **GRADLE_TOOL_VERSIONS},
            document["environment"],
        )
        self.assertEqual([], document["warmupSamples"])
        self.assertEqual("", status_after)
        self.assertEqual(2, static_environment.call_count)

    def test_git_provenance_requires_root_exact_head_and_exact_dirty_state(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary) / "checkout"
            revision = initialize_git_repository(repository)
            actual_revision, status = source_provenance.capture_git_worktree_state(repository)
            self.assertEqual(revision, actual_revision)
            self.assertEqual(b"", status)
            source_provenance.validate_declared_source(
                source_provenance.capture_source_state(repository),
                revision=revision,
                dirty=False,
            )
            with self.assertRaisesRegex(RuntimeError, "--revision must exactly match"):
                source_provenance.validate_declared_source(
                    source_provenance.capture_source_state(repository),
                    revision="0" * 40,
                    dirty=False,
                )

            (repository / "untracked.txt").write_text("included\n", encoding="utf-8")
            actual_revision, status = source_provenance.capture_git_worktree_state(repository)
            self.assertIn(b"untracked.txt", status)
            source_provenance.validate_declared_source(
                source_provenance.capture_source_state(repository),
                revision=revision,
                dirty=True,
            )
            with self.assertRaisesRegex(RuntimeError, "--dirty must exactly match.*true"):
                source_provenance.validate_declared_source(
                    source_provenance.capture_source_state(repository),
                    revision=revision,
                    dirty=False,
                )

            subdirectory = repository / "subdirectory"
            subdirectory.mkdir()
            with self.assertRaisesRegex(RuntimeError, "Git worktree root"):
                source_provenance.capture_git_worktree_state(subdirectory)

        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(RuntimeError, "git command failed"):
                source_provenance.capture_git_worktree_state(pathlib.Path(temporary))

    def test_output_must_be_new_and_git_ignored_inside_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary) / "checkout"
            initialize_git_repository(repository)
            ignored_output = repository / "build/performance/result.json"
            source_provenance.validate_output_path(
                repository=repository,
                output=ignored_output,
            )

            ignored_output.parent.mkdir(parents=True)
            ignored_output.write_text("old\n", encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "must not exist"):
                source_provenance.validate_output_path(
                    repository=repository,
                    output=ignored_output,
                )

            with self.assertRaisesRegex(RuntimeError, "must be ignored by Git"):
                source_provenance.validate_output_path(
                    repository=repository,
                    output=repository / "measurement.json",
                )

            tracked_output = repository / "tracked-result.json"
            tracked_output.write_text("tracked\n", encoding="utf-8")
            git_output(repository, "add", "tracked-result.json")
            git_output(
                repository,
                "-c",
                "user.name=Performance Test",
                "-c",
                "user.email=performance@example.invalid",
                "commit",
                "-q",
                "-m",
                "track output path",
            )
            tracked_output.unlink()
            with self.assertRaisesRegex(RuntimeError, "must not be tracked by Git"):
                source_provenance.validate_output_path(
                    repository=repository,
                    output=tracked_output,
                )

            git_output(repository, "restore", "tracked-result.json")
            with self.assertRaisesRegex(RuntimeError, "inside Git metadata"):
                source_provenance.validate_output_path(
                    repository=repository.resolve(),
                    output=(repository / ".git/result.json").resolve(strict=False),
                )

    def test_git_environment_cannot_redirect_provenance_commands(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            repository = root / "checkout"
            revision = initialize_git_repository(repository)
            attacker = root / "attacker"
            initialize_git_repository(attacker)
            (attacker / "README.md").write_text("different repository\n", encoding="utf-8")
            git_output(attacker, "add", "README.md")
            git_output(
                attacker,
                "-c",
                "user.name=Performance Test",
                "-c",
                "user.email=performance@example.invalid",
                "commit",
                "-q",
                "-m",
                "different",
            )
            attacker_revision = git_output(attacker, "rev-parse", "HEAD")
            self.assertNotEqual(revision, attacker_revision)
            poisoned_environment = {
                "GIT_DIR": str(attacker / ".git"),
                "GIT_WORK_TREE": str(attacker),
                "GIT_INDEX_FILE": str(attacker / ".git/index"),
                "GIT_OBJECT_DIRECTORY": str(attacker / ".git/objects"),
                "GIT_CONFIG_COUNT": "1",
                "GIT_CONFIG_KEY_0": "core.worktree",
                "GIT_CONFIG_VALUE_0": str(attacker),
            }
            with mock.patch.dict(measure_command.os.environ, poisoned_environment):
                actual_revision, status = source_provenance.capture_git_worktree_state(repository)

            self.assertEqual(revision, actual_revision)
            self.assertEqual(b"", status)

    def test_measurement_rejects_gradle_jvm_identity_change(self) -> None:
        sample = {
            "index": 1,
            "startedAt": "2026-01-01T00:00:00Z",
            "finishedAt": "2026-01-01T00:00:01Z",
            "wallNs": 1_000_000_000,
            "userCpuNs": 1,
            "systemCpuNs": 1,
            "waitedChildCpuUtilizationPercent": 0.0,
            "exitCode": 0,
            "timedOut": False,
        }
        changed_environment = {
            **STATIC_BUILD_ENVIRONMENT,
            "javaVersion": (
                "Launcher JVM: 21.0.11 (Eclipse Adoptium 21.0.11+10-LTS)\n"
                "Daemon JVM: /opt/java-21 (no JDK specified, using current Java home)"
            ),
        }
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary) / "checkout"
            revision = initialize_git_repository(repository)
            output = repository / "build/performance/measurement.json"
            arguments = [
                "measure_command.py",
                "--output", str(output),
                "--cwd", str(repository),
                "--label", "feature",
                "--revision", revision,
                "--dirty", "false",
                "--", "./gradlew", "task",
            ]
            with (
                mock.patch.object(sys, "argv", arguments),
                mock.patch.object(measure_command, "run_once", return_value=sample),
                mock.patch.object(
                    measure_command,
                    "capture_static_build_environment",
                    side_effect=[STATIC_BUILD_ENVIRONMENT, changed_environment],
                ),
                self.assertRaisesRegex(RuntimeError, "static build environment changed"),
            ):
                measure_command.main()
            self.assertFalse(output.exists())

    def test_measurement_rejects_worktree_changes_during_command(self) -> None:
        sample = {
            "index": 1,
            "startedAt": "2026-01-01T00:00:00Z",
            "finishedAt": "2026-01-01T00:00:01Z",
            "wallNs": 1_000_000_000,
            "userCpuNs": 1,
            "systemCpuNs": 1,
            "waitedChildCpuUtilizationPercent": 0.0,
            "exitCode": 0,
            "timedOut": False,
        }
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary) / "checkout"
            revision = initialize_git_repository(repository)
            output = repository / "build/performance/measurement.json"
            arguments = [
                "measure_command.py",
                "--output", str(output),
                "--cwd", str(repository),
                "--label", "feature",
                "--revision", revision,
                "--dirty", "false",
                "--", "./gradlew", "task",
            ]

            def mutate_worktree(*_args: object, **_kwargs: object) -> dict[str, object]:
                (repository / "unexpected.txt").write_text("changed\n", encoding="utf-8")
                return sample

            with (
                mock.patch.object(sys, "argv", arguments),
                mock.patch.object(measure_command, "run_once", side_effect=mutate_worktree),
                mock.patch.object(
                    measure_command,
                    "capture_static_build_environment",
                    return_value=STATIC_BUILD_ENVIRONMENT,
                ),
                mock.patch.object(
                    measure_command,
                    "capture_gradle_tool_versions",
                    return_value=GRADLE_TOOL_VERSIONS,
                ),
                self.assertRaisesRegex(RuntimeError, "exact Git source tree changed"),
            ):
                measure_command.main()
            self.assertFalse(output.exists())

    def test_source_tree_digest_detects_same_status_content_changes_and_ignores_builds(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary) / "checkout"
            initialize_git_repository(repository)
            tracked = repository / "README.md"
            tracked.write_text("dirty-one\n", encoding="utf-8")
            first = source_provenance.capture_source_state(repository)
            self.assertTrue(first.dirty)

            tracked.write_text("dirty-two\n", encoding="utf-8")
            second = source_provenance.capture_source_state(repository)
            self.assertEqual(first.status, second.status)
            self.assertNotEqual(first.source_tree_sha256, second.source_tree_sha256)
            with self.assertRaisesRegex(RuntimeError, "exact Git source tree changed"):
                source_provenance.require_unchanged_source(first, second)

            untracked = repository / "untracked.txt"
            untracked.write_text("untracked-one\n", encoding="utf-8")
            third = source_provenance.capture_source_state(repository)
            untracked.write_text("untracked-two\n", encoding="utf-8")
            fourth = source_provenance.capture_source_state(repository)
            self.assertEqual(third.status, fourth.status)
            self.assertNotEqual(third.source_tree_sha256, fourth.source_tree_sha256)

            ignored = repository / "build/generated.bin"
            ignored.parent.mkdir()
            before_ignored = source_provenance.capture_source_state(repository)
            ignored.write_bytes(b"first")
            after_ignored = source_provenance.capture_source_state(repository)
            ignored.write_bytes(b"second")
            final_ignored = source_provenance.capture_source_state(repository)
            self.assertEqual(before_ignored, after_ignored)
            self.assertEqual(after_ignored, final_ignored)

    def test_source_tree_digest_includes_index_and_symlink_targets(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary) / "checkout"
            initialize_git_repository(repository)
            before = source_provenance.capture_source_state(repository)

            (repository / "README.md").write_text("staged\n", encoding="utf-8")
            git_output(repository, "add", "README.md")
            staged = source_provenance.capture_source_state(repository)
            self.assertNotEqual(before.source_tree_sha256, staged.source_tree_sha256)

            link = repository / "untracked-link"
            link.symlink_to("target-one")
            link_one = source_provenance.capture_source_state(repository)
            link.unlink()
            link.symlink_to("target-two")
            link_two = source_provenance.capture_source_state(repository)
            self.assertEqual(link_one.status, link_two.status)
            self.assertNotEqual(link_one.source_tree_sha256, link_two.source_tree_sha256)

    def test_gradle_jvm_identity_comes_from_wrapper_version_output(self) -> None:
        wrapper_output = """
Gradle 8.14.3
Launcher JVM: 17.0.20 (Eclipse Adoptium 17.0.20+8)
Daemon JVM: /opt/java/openjdk (no JDK specified, using current Java home)
OS: Linux 6.11 amd64
"""
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary)
            wrapper = repository / "gradlew"
            wrapper.write_text("#!/bin/sh\nexit 99\n", encoding="utf-8")
            wrapper.chmod(0o755)
            with mock.patch.object(
                measure_command,
                "successful_command_output",
                return_value=wrapper_output,
            ) as version_command:
                identity = measure_command.gradle_jvm_identity(repository)

            self.assertEqual(STATIC_BUILD_ENVIRONMENT["javaVersion"], identity)
            version_command.assert_called_once_with(
                [str(wrapper), "--version", "--no-daemon"],
                cwd=repository,
            )

        for invalid_output in (
            "Launcher JVM: 17.0.20",
            "Daemon JVM: /opt/java/openjdk",
            "Launcher JVM:\nDaemon JVM: /opt/java/openjdk",
            "Launcher JVM: one\nLauncher JVM: two\nDaemon JVM: three",
        ):
            with self.subTest(output=invalid_output), self.assertRaisesRegex(
                RuntimeError,
                "Gradle --version output must contain exactly one",
            ):
                measure_command.parse_gradle_jvm_identity(invalid_output)

    def test_build_metadata_versions_are_read_from_pinned_repository_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repository = pathlib.Path(temporary)
            wrapper = repository / "gradle/wrapper/gradle-wrapper.properties"
            wrapper.parent.mkdir(parents=True)
            wrapper.write_text(
                "distributionSha256Sum=" + "a" * 64 + "\n",
                encoding="utf-8",
            )
            catalog = repository / "gradle/libs.versions.toml"
            catalog.write_text('[versions]\nkotlin = "2.3.20"\n', encoding="utf-8")

            self.assertEqual("a" * 64, measure_command.wrapper_distribution_sha256(repository))
            self.assertEqual("2.3.20", measure_command.kotlin_version(repository))

    def test_ambiguous_gradle_tool_versions_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            executables = [root / "node-a", root / "node-b"]
            for executable in executables:
                executable.write_text("", encoding="utf-8")
                executable.chmod(0o755)
            with (
                mock.patch.object(
                    measure_command,
                    "successful_command_output",
                    side_effect=["v24.0.0", "v25.0.0"],
                ),
                self.assertRaisesRegex(RuntimeError, "ambiguous Gradle-managed Node.js versions"),
            ):
                measure_command.installed_gradle_tool_versions(
                    family="Node.js",
                    executable_candidates=executables,
                    arguments=["--version"],
                    version_pattern=measure_command.re.compile(r"v(\d+\.\d+\.\d+)"),
                )


if __name__ == "__main__":
    unittest.main()
