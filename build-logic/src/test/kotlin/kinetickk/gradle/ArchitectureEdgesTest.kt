// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.GradleException
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArchitectureEdgesTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun exportIsCanonicalSortedAndDeduplicated() {
        val text = architectureEdgeReportText(
            sourceProjectPath = ":app:desktop",
            encodedEdges = listOf(
                ":app:desktop\ttestImplementation\t:foundation:common",
                ":app:desktop\timplementation\t:app:shared",
                ":app:desktop\timplementation\t:app:shared",
            ),
        )

        assertEquals(
            """
                schema	kinetickk-architecture-edges/v1
                source	:app:desktop
                edge	:app:desktop	implementation	:app:shared
                edge	:app:desktop	testImplementation	:foundation:common
            """.trimIndent() + "\n",
            text,
        )
    }

    @Test
    fun reportsLoadAsAnExactLeafSet() {
        val desktop = writeReport(
            "desktop.tsv",
            architectureEdgeReportText(
                ":app:desktop",
                listOf(":app:desktop\timplementation\t:app:shared"),
            ),
        )
        val shared = writeReport(
            "shared.tsv",
            architectureEdgeReportText(":app:shared", emptyList()),
        )

        assertEquals(
            setOf(":app:desktop\timplementation\t:app:shared"),
            loadArchitectureEdges(
                reportFiles = listOf(shared, desktop),
                expectedSourceProjectPaths = setOf(":app:desktop", ":app:shared"),
            ),
        )
    }

    @Test
    fun missingReportsFailClosed() {
        val desktop = writeReport(
            "desktop.tsv",
            architectureEdgeReportText(":app:desktop", emptyList()),
        )

        val failure = assertFailsWith<GradleException> {
            loadArchitectureEdges(
                reportFiles = listOf(desktop),
                expectedSourceProjectPaths = setOf(":app:desktop", ":app:shared"),
            )
        }

        assertTrue(":app:shared" in failure.message.orEmpty())
    }

    @Test
    fun duplicateReportsFailClosed() {
        val first = writeReport(
            "first.tsv",
            architectureEdgeReportText(":app:shared", emptyList()),
        )
        val second = writeReport(
            "second.tsv",
            architectureEdgeReportText(":app:shared", emptyList()),
        )

        val failure = assertFailsWith<GradleException> {
            loadArchitectureEdges(
                reportFiles = listOf(first, second),
                expectedSourceProjectPaths = setOf(":app:shared"),
            )
        }

        assertTrue("Multiple architecture edge reports" in failure.message.orEmpty())
    }

    @Test
    fun malformedAndNonCanonicalReportsFailClosed() {
        val unsorted = writeReport(
            "unsorted.tsv",
            """
                schema	kinetickk-architecture-edges/v1
                source	:app:desktop
                edge	:app:desktop	testImplementation	:foundation:common
                edge	:app:desktop	implementation	:app:shared
            """.trimIndent() + "\n",
        )
        val invalidUtf8 = temporaryDirectory.resolve("invalid-utf8.tsv")
        invalidUtf8.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))

        assertFailsWith<GradleException> {
            loadArchitectureEdges(listOf(unsorted), setOf(":app:desktop"))
        }
        assertFailsWith<GradleException> {
            loadArchitectureEdges(listOf(invalidUtf8.toFile()), setOf(":app:desktop"))
        }
    }

    @Test
    fun exportRejectsForeignSourceRows() {
        assertFailsWith<IllegalArgumentException> {
            architectureEdgeReportText(
                sourceProjectPath = ":app:desktop",
                encodedEdges = listOf(":app:web\timplementation\t:app:shared"),
            )
        }
    }

    private fun writeReport(name: String, text: String) =
        temporaryDirectory.resolve(name).also { path ->
            path.writeText(text, StandardCharsets.UTF_8)
        }.toFile()
}
