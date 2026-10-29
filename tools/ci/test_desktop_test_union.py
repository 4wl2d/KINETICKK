# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

from collections import Counter
from pathlib import Path
import tempfile
import unittest

import desktop_test_union as union

SOURCE = """package a.b

import kotlin.test.Test

/** A KDoc line. */
class FirstTest {
    @Test
    fun one() {}

    @Test
    fun two() {}
}

private class Helper(val x: Int) {
    fun notATest() {}
}

internal fun helper() = Unit

internal class SecondTest(private val value: Int = 1) {
    @Test fun three() {}
}
"""


def report(cases):
    rows = "".join(f'<testcase name="{name}" classname="{test_class}" time="0.1"/>' for test_class, name in cases)
    return f'<?xml version="1.0" encoding="UTF-8"?><testsuite name="s" tests="{len(cases)}">{rows}</testsuite>'


class DesktopTestUnionTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def test_counts_the_tests_of_top_level_classes(self):
        self.assertEqual(Counter({"a.b.FirstTest": 2, "a.b.SecondTest": 1}), union.declared_tests(SOURCE))

    def test_reads_only_desktop_test_source_sets(self):
        self.write("ball/x/src/commonTest/kotlin/a/b/First.kt", SOURCE)
        self.write("ball/x/src/androidDeviceTest/kotlin/a/b/Device.kt", SOURCE.replace("First", "Device"))
        self.write("ball/x/build/src/desktopTest/kotlin/a/b/Generated.kt", SOURCE.replace("First", "Generated"))
        self.write(".claude/worktrees/w/ball/x/src/desktopTest/kotlin/a/b/Other.kt", SOURCE.replace("First", "Other"))

        self.assertEqual(Counter({"a.b.FirstTest": 2, "a.b.SecondTest": 1}), union.tree_tests(self.root))

    def shards(self, attempt=1, **cases):
        reports = self.root / "reports"
        for shard, shard_cases in cases.items():
            artifact = reports / f"desktop-reports-{shard}-41-{attempt}"
            self.write(artifact / "ball/x/build/test-results/desktopTest/TEST-a.xml", report(shard_cases))
            # build-logic's own results ride in the same artifact and are not desktop tests.
            self.write(artifact / "build-logic/build/test-results/test/TEST-b.xml", report([("k.Gradle", "x")]))
        return union.shard_results(reports)

    def test_accepts_shards_that_run_every_test_once(self):
        results = self.shards(**{
            "rest": [("a.b.FirstTest", "one"), ("a.b.FirstTest", "two")],
            "gates-a": [("a.b.SecondTest", "three")],
        })

        self.assertEqual({"rest", "gates-a"}, set(results))
        self.assertEqual([], union.check(results, union.declared_tests(SOURCE)))

    def test_reports_a_test_that_ran_in_two_shards(self):
        results = self.shards(**{
            "rest": [("a.b.FirstTest", "one"), ("a.b.FirstTest", "two"), ("a.b.SecondTest", "three")],
            "gates-a": [("a.b.SecondTest", "three")],
        })

        problems = union.check(results, union.declared_tests(SOURCE))
        self.assertIn("a.b.SecondTest.three ran 2 times: gates-a, rest", problems)

    def test_reports_a_missing_test_and_an_empty_shard(self):
        results = self.shards(rest=[("a.b.FirstTest", "one"), ("a.b.SecondTest", "three")], **{"gates-a": []})

        problems = union.check(results, union.declared_tests(SOURCE))
        self.assertIn("shard gates-a reported no desktop test", problems)
        self.assertIn("a.b.FirstTest: 2 declared tests, 1 ran in the shards", problems)

    def test_reports_an_undeclared_class(self):
        results = self.shards(rest=[("a.b.FirstTest", "one"), ("a.b.FirstTest", "two"),
                                    ("a.b.SecondTest", "three"), ("a.b.Stray", "x")])

        self.assertEqual(["a.b.Stray: 0 declared tests, 1 ran in the shards"],
                         union.check(results, union.declared_tests(SOURCE)))

    def test_reads_the_latest_attempt_of_each_shard(self):
        self.shards(rest=[("a.b.FirstTest", "one")], **{"gates-1": [("a.b.SecondTest", "three")]})
        # A re-run of the failed rest shard only: gates-1 keeps its first attempt.
        results = self.shards(attempt=2, rest=[("a.b.FirstTest", "one"), ("a.b.FirstTest", "two")])

        self.assertEqual([("a.b.FirstTest", "one"), ("a.b.FirstTest", "two")], results["rest"])
        self.assertEqual([("a.b.SecondTest", "three")], results["gates-1"])
        self.assertEqual([], union.check(results, union.declared_tests(SOURCE)))

    def test_rejects_an_unknown_artifact(self):
        (self.root / "reports" / "wasm-reports-1-1").mkdir(parents=True)

        with self.assertRaises(ValueError):
            union.shard_results(self.root / "reports")


if __name__ == "__main__":
    unittest.main()
