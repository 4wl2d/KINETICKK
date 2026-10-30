#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Check that the desktop test shards of one CI run together ran every desktop test exactly once.

Each shard's report artifact is downloaded into its own directory, named desktop-reports-<shard>-<run>-<attempt>;
a shard's latest attempt counts, so re-running only the failed shards still checks the whole run. Every JUnit XML
report under a desktopTest result directory counts. The union of the shards must hold each
`@Test` method of the tree's commonTest and desktopTest sources once: no test may run in two shards, or twice in
one, and none may be missing. The tree has no test base classes, suites, nested test classes or parameterized
tests, so a declared `@Test` method is exactly one reported test case.
"""

import argparse
from collections import Counter, defaultdict
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ElementTree

ARTIFACT = re.compile(r"desktop-reports-(?P<shard>[a-z0-9-]+?)-(?P<run>\d+)-(?P<attempt>\d+)")
TEST_SOURCE_ROOTS = ("/src/commonTest/kotlin/", "/src/desktopTest/kotlin/")
SKIPPED_DIRECTORIES = {".git", ".gradle", ".claude", ".kotlin", "build", "build-logic", "node_modules"}
PACKAGE = re.compile(r"^package ([\w.]+)", re.MULTILINE)
TOP_LEVEL_CLASS = re.compile(r"^(?:(?:public|internal|private|abstract|open|data)\s+)*class\s+(\w+)")
TEST_ANNOTATION = re.compile(r"^\s+@Test\b")


def declared_tests(source):
    """Counts the `@Test` methods of each top-level class in one Kotlin source (column-0 declarations)."""
    package = PACKAGE.search(source)
    if package is None:
        return Counter()
    tests = Counter()
    current = None
    for line in source.splitlines():
        declaration = TOP_LEVEL_CLASS.match(line)
        if declaration:
            current = declaration.group(1)
        elif line[:1].isalpha():
            current = None
        if current and TEST_ANNOTATION.match(line):
            tests[f"{package.group(1)}.{current}"] += 1
    return tests


def tree_tests(root):
    """Counts the declared `@Test` methods per class over every desktop test source set under root."""
    tests = Counter()
    for directory, subdirectories, files in os.walk(root):
        subdirectories[:] = sorted(name for name in subdirectories if name not in SKIPPED_DIRECTORIES)
        path = Path(directory).as_posix() + "/"
        if not any(source_root in path for source_root in TEST_SOURCE_ROOTS):
            continue
        for name in sorted(files):
            if name.endswith(".kt"):
                tests.update(declared_tests((Path(directory) / name).read_text(encoding="utf-8")))
    return tests


def latest_artifacts(reports):
    """Maps each shard to the directory of its latest attempt's artifact."""
    latest = {}
    for artifact in sorted(path for path in Path(reports).iterdir() if path.is_dir()):
        match = ARTIFACT.fullmatch(artifact.name)
        if match is None:
            raise ValueError(f"unexpected report artifact {artifact.name}")
        shard, attempt = match.group("shard"), int(match.group("attempt"))
        if shard not in latest or attempt > latest[shard][0]:
            latest[shard] = (attempt, artifact)
    return {shard: artifact for shard, (_, artifact) in latest.items()}


def shard_results(reports):
    """Maps each shard to the (class, test case) pairs its desktopTest reports hold, repeats included."""
    results = {}
    for shard, artifact in sorted(latest_artifacts(reports).items()):
        cases = []
        for report in sorted(artifact.rglob("TEST-*.xml")):
            if "desktopTest" not in report.parent.parts:
                continue
            for case in ElementTree.parse(report).getroot().iter("testcase"):
                cases.append((case.get("classname"), case.get("name")))
        results[shard] = cases
    return results


def check(results, declared):
    """Returns the problems that keep the shards' union from running every declared test exactly once."""
    problems = []
    runs = defaultdict(list)
    for shard, cases in results.items():
        if not cases:
            problems.append(f"shard {shard} reported no desktop test")
        for case in cases:
            runs[case].append(shard)
    for (test_class, name), shards in sorted(runs.items()):
        if len(shards) > 1:
            problems.append(f"{test_class}.{name} ran {len(shards)} times: {', '.join(sorted(shards))}")
    executed = Counter(test_class for test_class, _ in runs)
    for test_class in sorted(set(declared) | set(executed)):
        if executed[test_class] != declared[test_class]:
            problems.append(
                f"{test_class}: {declared[test_class]} declared tests, {executed[test_class]} ran in the shards"
            )
    return problems


def summary(results, declared):
    lines = ["| Shard | Classes | Tests |", "|---|---|---|"]
    for shard, cases in sorted(results.items()):
        lines.append(f"| {shard} | {len({test_class for test_class, _ in cases})} | {len(cases)} |")
    total = sum(len(cases) for cases in results.values())
    lines.append(f"| union | {len({c for cases in results.values() for c, _ in cases})} | {total} |")
    lines.append(f"| declared | {len(declared)} | {sum(declared.values())} |")
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--reports", required=True, help="directory holding one directory per shard artifact")
    parser.add_argument("--sources", default=".", help="repository root")
    arguments = parser.parse_args(argv)

    results = shard_results(arguments.reports)
    declared = tree_tests(arguments.sources)
    table = summary(results, declared)
    print(table)
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as output:
            output.write(f"### Desktop test shards\n\n{table}\n")
    problems = check(results, declared)
    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        print(f"{len(problems)} problem(s): the shards did not run every desktop test exactly once", file=sys.stderr)
        return 1
    print(f"Every one of {sum(declared.values())} desktop tests ran exactly once across {len(results)} shards.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
