// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugReportPagesTest {
    @Test
    fun largeReportIsBoundedForLayoutAndAllPagesReconstructTheOriginal() {
        val report = buildString {
            repeat(6_000) { appendLine("frame=$it\t16000000\t16666667\t5000000\t3000000\t4000000\t8\t16") }
        }
        assertTrue(report.length > 100_000)
        val pages = DebugReportPages(report)
        assertTrue(pages.count > 1)
        val reconstructed = buildString {
            repeat(pages.count) { index ->
                val page = pages.page(index)
                assertTrue(page.length <= DebugReportPages.PAGE_CHAR_LIMIT)
                assertTrue(page.isNotEmpty())
                append(page)
            }
        }
        assertEquals(report, reconstructed)
    }

    @Test
    fun pagesNeverSplitSurrogatePairsEvenInsideAnUnbrokenLongLine() {
        val report = "a".repeat(8_191) + "💥" + "z".repeat(20_000)
        val pages = DebugReportPages(report)
        val allPages = (0 until pages.count).map(pages::page)
        assertTrue(allPages.all { it.length <= DebugReportPages.PAGE_CHAR_LIMIT })
        assertFalse(allPages.any { it.last() in '\uD800'..'\uDBFF' })
        assertFalse(allPages.any { it.first() in '\uDC00'..'\uDFFF' })
        assertEquals(report, allPages.joinToString(""))
    }

    @Test
    fun emptyReportHasOneEmptyPage() {
        val pages = DebugReportPages("")
        assertEquals(1, pages.count)
        assertEquals("", pages.page(0))
    }
}
