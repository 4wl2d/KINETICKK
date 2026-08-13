// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class SkikoWasmRuntimeTest {
    @Test
    fun runtimeSelectionRequiresExactlyOneArchive() {
        val emptyViolation = skikoRuntimeArchiveSelectionViolation(emptyList())
        val multipleViolation = skikoRuntimeArchiveSelectionViolation(
            listOf("/cache/skiko-a.jar", "/cache/skiko-b.jar"),
        )

        assertContains(requireNotNull(emptyViolation), "found 0")
        assertContains(requireNotNull(multipleViolation), "found 2")
        assertContains(multipleViolation, "skiko-a.jar, skiko-b.jar")
    }

    @Test
    fun runtimeSelectionRequiresJarButAcceptsCaseInsensitiveExtension() {
        val violation = skikoRuntimeArchiveSelectionViolation(listOf("/cache/skiko.zip"))

        assertContains(requireNotNull(violation), "to be a JAR")
        assertNull(skikoRuntimeArchiveSelectionViolation(listOf("/cache/skiko.JAR")))
    }

    @Test
    fun runtimeArchiveAcceptsCurrentAuxiliaryEntriesButSelectsExactLinkPayload() {
        assertNull(
            skikoRuntimeArchiveViolation(
                listOf(
                    "META-INF/MANIFEST.MF",
                    "js-reexport-symbols.mjs",
                    "skikod8.mjs",
                    "skiko.mjs",
                    "skiko.wasm",
                ),
            ),
        )
    }

    @Test
    fun runtimeArchiveRejectsMissingRequiredEntry() {
        val violation = skikoRuntimeArchiveViolation(listOf("skiko.mjs"))

        assertContains(requireNotNull(violation), "missing [skiko.wasm]")
    }

    @Test
    fun runtimeArchiveRejectsDuplicateRequiredEntry() {
        val violation = skikoRuntimeArchiveViolation(
            listOf("skiko.mjs", "skiko.wasm", "skiko.wasm"),
        )

        assertContains(requireNotNull(violation), "duplicates [skiko.wasm]")
    }

    @Test
    fun runtimeArchiveRejectsNestedAliasesOfRequiredEntries() {
        val violation = skikoRuntimeArchiveViolation(
            listOf("skiko.mjs", "skiko.wasm", "nested/skiko.wasm"),
        )

        assertContains(requireNotNull(violation), "aliases [nested/skiko.wasm]")
    }

    @Test
    fun runtimeArchiveRejectsTraversalEntriesBeforeExtraction() {
        val violation = skikoRuntimeArchiveViolation(
            listOf("skiko.mjs", "skiko.wasm", "../unexpected.txt"),
        )

        assertContains(requireNotNull(violation), "unsafe archive entries [../unexpected.txt]")
    }

    @Test
    fun filteredRuntimeOutputRequiresExactlyTwoLinkInputs() {
        assertNull(skikoRuntimeOutputViolation(listOf("skiko.wasm", "skiko.mjs")))

        val violation = skikoRuntimeOutputViolation(listOf("skiko.mjs", "unexpected.mjs"))
        val message = requireNotNull(violation)
        assertContains(message, "missing [skiko.wasm]")
        assertContains(message, "extra [unexpected.mjs]")
    }

    @Test
    fun exactPinnedAndResolvedVersionPasses() {
        assertNull(skikoVersionConsistencyViolation("0.144.6", setOf("0.144.6")))
    }

    @Test
    fun missingComposeSkikoVersionFailsClosed() {
        val violation = skikoVersionConsistencyViolation("0.144.6", emptySet())

        requireNotNull(violation)
        assertContains(violation, "resolved no org.jetbrains.skiko component")
    }

    @Test
    fun mismatchedVersionReportsBothValues() {
        val violation = skikoVersionConsistencyViolation("0.144.6", setOf("0.145.0"))

        requireNotNull(violation)
        assertContains(violation, "0.144.6")
        assertContains(violation, "0.145.0")
    }

    @Test
    fun multipleResolvedVersionsFailDeterministically() {
        val violation = skikoVersionConsistencyViolation(
            pinnedVersion = "0.144.6",
            resolvedVersions = setOf("0.145.0", "0.143.2"),
        )

        requireNotNull(violation)
        assertContains(violation, "[0.143.2, 0.145.0]")
    }

    @Test
    fun blankPinFailsClosed() {
        val violation = skikoVersionConsistencyViolation("", setOf("0.144.6"))

        requireNotNull(violation)
        assertContains(violation, "blank")
    }
}
