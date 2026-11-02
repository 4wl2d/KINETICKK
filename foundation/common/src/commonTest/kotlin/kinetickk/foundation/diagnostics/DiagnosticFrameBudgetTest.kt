// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticFrameBudgetTest {
    @Test
    fun activeDisplayConstrainsFasterSoftwareCaps() {
        val display60Hz = 1_000_000_000L / 60
        assertEquals(display60Hz, diagnosticFrameBudgetNanos(144, display60Hz))
        assertEquals(display60Hz, diagnosticFrameBudgetNanos(0, display60Hz))
        assertEquals(1_000_000_000L / 30, diagnosticFrameBudgetNanos(30, display60Hz))
    }

    @Test
    fun unknownOrUncappedHostDoesNotInventARefreshRate() {
        assertEquals(0L, diagnosticFrameBudgetNanos(0, 0L))
        assertEquals(0L, diagnosticFrameBudgetNanos(0, -1L))
        assertEquals(1_000_000_000L / 120, diagnosticFrameBudgetNanos(120, 0L))
    }
}
