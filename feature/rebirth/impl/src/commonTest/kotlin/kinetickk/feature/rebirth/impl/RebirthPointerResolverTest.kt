// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.rebirth.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RebirthPointerResolverTest {
    @Test
    fun actionAndFooterRetainTheirOriginalVerticalBands() {
        assertEquals(
            RebirthAction.AdvanceRequested,
            resolveRebirthPress(1_280f, 720f, 1f, x = 640f, y = 580f),
        )
        assertEquals(
            RebirthAction.Back,
            resolveRebirthPress(1_280f, 720f, 1f, x = 640f, y = 660f),
        )
        assertNull(resolveRebirthPress(1_280f, 720f, 1f, x = 640f, y = 623f))
    }

    @Test
    fun clicksOutsideThePanelDoNotNavigate() {
        assertNull(resolveRebirthPress(1_280f, 720f, 1f, x = 20f, y = 660f))
    }
}
