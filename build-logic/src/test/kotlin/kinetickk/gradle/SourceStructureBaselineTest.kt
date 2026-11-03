// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourceStructureBaselineTest {
    private val header = listOf(
        "# SPDX-FileCopyrightText: 2026 Vladislav Tomilov",
        "# SPDX-License-Identifier: GPL-3.0-or-later",
        "# Ceilings for verifySourceStructure (docs/project/ARCHITECTURE.md). Lower only: ./gradlew updateSourceStructureBaseline",
        "schema\tkinetickk-source-structure-baseline/v1",
    )

    private fun baseline(vararg rows: String): String = (header + rows).joinToString("\n", postfix = "\n")

    private val mainFile = "a/one/src/commonMain/kotlin/a/b/Big.kt"
    private val movedFile = "a/two/src/commonMain/kotlin/c/d/Big.kt"

    private fun main(key: String, value: Int) = Measurement(StructureRule.MAIN_FILE_LINES, key, value)

    private fun row(key: String, ceiling: Int, rule: StructureRule = StructureRule.MAIN_FILE_LINES) = BaselineRow(rule, key, ceiling)

    @Test
    fun parseAcceptsTheCanonicalFormAndRenderRoundTripsIt() {
        val text = baseline(
            "function-lines\t$mainFile#draw\t140",
            "main-file-lines\t$mainFile\t900",
            "package-cycles\t:a:one\t3",
        )
        val rows = parseStructureBaseline(text)
        assertEquals(
            listOf(
                row("$mainFile#draw", 140, StructureRule.FUNCTION_LINES),
                row(mainFile, 900),
                row(":a:one", 3, StructureRule.PACKAGE_CYCLES),
            ),
            rows,
        )
        assertEquals(text, renderStructureBaseline(rows))
        assertEquals(baseline(), renderStructureBaseline(emptyList()))
    }

    @Test
    fun parseFailsClosedOnEveryNonCanonicalInput() {
        val valid = "main-file-lines\t$mainFile\t900"
        val rejected = mapOf(
            "CR" to baseline(valid).replace("\n", "\r\n"),
            "missing final LF" to baseline(valid).dropLast(1),
            "wrong schema" to baseline(valid).replace("/v1", "/v2"),
            "comment after the schema" to baseline("# late comment", valid),
            "unsorted rows" to baseline(valid, "function-lines\t$mainFile#draw\t140"),
            "duplicate rows" to baseline(valid, valid),
            "unknown rule" to baseline("file-lines\t$mainFile\t900"),
            "hard rule" to baseline("package-directory\t$mainFile\t1"),
            "ceiling 0" to baseline("package-cycles\t:a:one\t0"),
            "ceiling -1" to baseline("main-file-lines\t$mainFile\t-1"),
            "leading zero" to baseline("main-file-lines\t$mainFile\t0900"),
            "not a number" to baseline("main-file-lines\t$mainFile\tabc"),
            "ceiling at the limit" to baseline("main-file-lines\t$mainFile\t600"),
            "tab in key" to baseline("main-file-lines\ta\tb\t900"),
            "empty key" to baseline("main-file-lines\t\t900"),
            "two fields" to baseline("main-file-lines\t$mainFile"),
        )
        rejected.forEach { (case, text) ->
            val failure = assertFailsWith<StructureBaselineException>(case) { parseStructureBaseline(text, "baseline.tsv") }
            assertTrue(failure.message.orEmpty().startsWith("baseline.tsv:"), "$case: ${failure.message}")
        }
    }

    @Test
    fun evaluateAllowsCeilingsReportsGrowthNewItemsAndStaleRows() {
        val rows = listOf(row(mainFile, 900), row("a/one/src/commonMain/kotlin/a/b/Fixed.kt", 700))
        assertEquals(StructureEvaluation(emptyList(), listOf(rows[1]), emptyList()), evaluateStructure(listOf(main(mainFile, 900)), rows))

        val grown = evaluateStructure(listOf(main(mainFile, 901)), rows.take(1))
        assertEquals(listOf(StructureViolation(main(mainFile, 901), ceiling = 900)), grown.violations)
        assertTrue("ceiling 900" in structureFailureMessage(grown.violations, grown.stale))

        val shrunk = evaluateStructure(listOf(main(mainFile, 650)), rows.take(1))
        assertTrue(!shrunk.failed)
        assertEquals(rows.take(1), shrunk.tightenable)

        val fresh = evaluateStructure(listOf(main(movedFile, 610)), emptyList())
        assertEquals(listOf(StructureViolation(main(movedFile, 610))), fresh.violations)
        assertTrue("610 lines, not baselined" in structureFailureMessage(fresh.violations, fresh.stale))
    }

    @Test
    fun aNewItemWithTheIdentityOfAUniqueStaleRowLooksMoved() {
        val evaluation = evaluateStructure(listOf(main(movedFile, 850)), listOf(row(mainFile, 900)))
        assertEquals(listOf(StructureViolation(main(movedFile, 850), movedFrom = mainFile)), evaluation.violations)
        assertEquals(listOf(row(mainFile, 900)), evaluation.stale)
        val message = structureFailureMessage(evaluation.violations, evaluation.stale)
        assertTrue("looks moved from $mainFile" in message, message)
        assertTrue("stale baseline rows:\n - main-file-lines $mainFile: within the limit or gone" in message, message)
    }

    @Test
    fun hardRuleMeasurementsAreAlwaysViolations() {
        val hard = Measurement(StructureRule.PACKAGE_DIRECTORY, mainFile, 1, "declares package 'x', directory expects 'a.b'")
        val evaluation = evaluateStructure(listOf(hard), emptyList())
        assertEquals(listOf(StructureViolation(hard)), evaluation.violations)
        val message = structureFailureMessage(evaluation.violations, evaluation.stale)
        assertTrue("package-directory (hard rule, never baselined):\n - $mainFile\n   declares package 'x'" in message, message)
    }

    @Test
    fun tightenLowersDeletesAndCarriesButNeverAddsOrRaises() {
        val function = "a/one/src/commonMain/kotlin/a/b/Big.kt#draw"
        val movedFunction = "a/two/src/commonMain/kotlin/c/d/Big.kt#draw"
        val rows = listOf(
            row(function, 150, StructureRule.FUNCTION_LINES),
            row(mainFile, 900),
            row("a/one/src/commonMain/kotlin/a/b/Fixed.kt", 700),
            row("a/one/src/commonMain/kotlin/a/b/Lowered.kt", 800),
        )
        val update = tightenStructureBaseline(
            listOf(
                Measurement(StructureRule.FUNCTION_LINES, movedFunction, 120),
                main(movedFile, 900),
                main("a/one/src/commonMain/kotlin/a/b/Lowered.kt", 640),
            ),
            rows,
        )
        assertEquals(
            listOf(
                row(movedFunction, 120, StructureRule.FUNCTION_LINES),
                row("a/one/src/commonMain/kotlin/a/b/Lowered.kt", 640),
                row(movedFile, 900),
            ),
            update.rows,
        )
        assertEquals(emptyList(), update.remaining)
        assertEquals(1, update.lowered)
        assertEquals(listOf(row("a/one/src/commonMain/kotlin/a/b/Fixed.kt", 700)), update.deleted)
        assertEquals(2, update.carried.size)
    }

    @Test
    fun tightenRefusesAmbiguousOrGrownCarriesAndReportsWhatRemains() {
        val otherMove = "a/two/src/commonMain/kotlin/e/f/Big.kt"
        val ambiguous = tightenStructureBaseline(listOf(main(movedFile, 700), main(otherMove, 700)), listOf(row(mainFile, 900)))
        assertEquals(emptyList(), ambiguous.rows)
        assertEquals(listOf(movedFile, otherMove), ambiguous.remaining.map { it.measurement.key })

        val grownCarry = tightenStructureBaseline(listOf(main(movedFile, 950)), listOf(row(mainFile, 900)))
        assertEquals(emptyList(), grownCarry.rows)
        assertEquals(listOf(StructureViolation(main(movedFile, 950), movedFrom = mainFile)), grownCarry.remaining)

        val grownInPlace = tightenStructureBaseline(listOf(main(mainFile, 950)), listOf(row(mainFile, 900)))
        assertEquals(listOf(row(mainFile, 900)), grownInPlace.rows)
        assertEquals(listOf(StructureViolation(main(mainFile, 950), ceiling = 900)), grownInPlace.remaining)

        val hard = Measurement(StructureRule.CONTRACT_IMPORTS, mainFile, 1)
        assertEquals(listOf(StructureViolation(hard)), tightenStructureBaseline(listOf(hard), emptyList()).remaining)
    }

    @Test
    fun resetWritesEveryBaselinableMeasurementAndKeepsHardViolations() {
        val hard = Measurement(StructureRule.FUNCTION_SCAN, mainFile, 1, "braces do not balance after masking literals")
        val update = resetStructureBaseline(
            listOf(
                main(movedFile, 700),
                Measurement(StructureRule.PACKAGE_CYCLES, ":a:one", 2),
                hard,
                Measurement(StructureRule.FEATURE_IMPL_IMPORTS, mainFile, 3),
            ),
        )
        assertEquals(
            listOf(
                row(mainFile, 3, StructureRule.FEATURE_IMPL_IMPORTS),
                row(movedFile, 700),
                row(":a:one", 2, StructureRule.PACKAGE_CYCLES),
            ),
            update.rows,
        )
        assertEquals(listOf(StructureViolation(hard)), update.remaining)
    }

    @Test
    fun theFailureMessageNamesTheFixTheBaselineTheRulesAndTheUpdateTask() {
        val violation = StructureViolation(Measurement(StructureRule.FUNCTION_LINES, "$mainFile#draw", 131, line = 59))
        val message = structureFailureMessage(listOf(violation), listOf(row(movedFile, 700)), "build/reports/source-structure/report.txt")
        assertTrue(message.startsWith("Source structure verification failed with 2 violation(s)."), message)
        assertTrue("Baseline: build-logic/source-structure-baseline.tsv." in message, message)
        assertTrue("Rules: docs/project/ARCHITECTURE.md (section \"Size limits and ratchets\")." in message, message)
        assertTrue("function-lines (limit 100 lines; a baselined item may not grow):" in message, message)
        assertTrue(" - $mainFile#draw (line 59): 131 lines, not baselined" in message, message)
        assertTrue("   Fix: ${StructureRule.FUNCTION_LINES.fix}" in message, message)
        assertTrue("./gradlew updateSourceStructureBaseline" in message, message)
        assertTrue(message.endsWith("Full report: build/reports/source-structure/report.txt"), message)
        assertTrue("updateSourceStructureBaseline -Pkinetickk.sourceStructure.resetBaseline=true" in missingStructureBaselineMessage())
    }

    @Test
    fun theReportListsEveryRowWithItsValueAndSlack() {
        val report = structureReport("ok", listOf(row(mainFile, 900), row(movedFile, 700)), listOf(main(mainFile, 850)))
        assertTrue(" - main-file-lines $mainFile: 850 lines, ceiling 900, slack 50" in report, report)
        assertTrue(" - main-file-lines $movedFile: within the limit or gone, ceiling 700" in report, report)
    }
}
