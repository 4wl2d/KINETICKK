// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.profileFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RebirthPointerResolverTest {
    private fun layout(width: Float, height: Float, target: Int = 4, textScale: Float = 1f) =
        rebirthLayout(profileFrame(width, height, 1f), 0, 10, target, textScale, backWidth = 80f)

    @Test
    fun regularGeometryFollowsTheBoard() {
        val layout = layout(1440f, 810f)
        assertEquals(11, layout.cells.size)
        assertEquals(56f, layout.cells.first().left, 0.01f)
        assertEquals(464f, layout.ladder.top, 0.01f)
        assertEquals(70f, layout.ladder.height, 0.01f)
        assertEquals((820f - 60f) / 11f, layout.cells.first().width, 0.01f)
        assertEquals(930f, layout.action.left, 0.01f)
        assertEquals(72f, layout.action.height, 0.01f)
        assertTrue(layout.action.top in 540f..556f, "${layout.action}")
        // Two-digit tiers move the direction name right of the wider numeral.
        assertEquals(56f + 250f, layout.name.left, 0.01f)
        assertEquals(56f + 400f, layout(1440f, 810f, target = 10).name.left, 0.01f)
        assertEquals(0f, layout.scrollMax)
    }

    @Test
    fun advanceAndBackAreTheOnlyTargets() {
        val layout = layout(1440f, 810f)
        val action = layout.action.center
        assertEquals(RebirthAction.AdvanceRequested, resolveRebirthPress(layout, 0f, action.x, action.y))
        assertEquals(RebirthAction.Back, resolveRebirthPress(layout, 0f, layout.back.center.x, layout.back.center.y))
        assertNull(resolveRebirthPress(layout, 0f, layout.info.center.x, layout.info.center.y))
        assertNull(resolveRebirthPress(layout, 0f, layout.cells[3].center.x, layout.cells[3].center.y))
        assertNull(resolveRebirthPress(layout, 0f, action.x, layout.action.bottom + 20f))
    }

    @Test
    fun compactLayoutsScrollToTheAdvanceButton() {
        for ((size, mode) in listOf(
            (844f to 390f) to ProfileLayoutMode.COMPACT_LANDSCAPE,
            (720f to 360f) to ProfileLayoutMode.COMPACT_LANDSCAPE,
            (390f to 844f) to ProfileLayoutMode.COMPACT_PORTRAIT,
        )) {
            val layout = layout(size.first, size.second, textScale = 1.75f)
            assertEquals(mode, layout.frame.mode)
            val scroll = layout.scrollMax
            // At the end of the scroll the whole button is inside the viewport and still maps.
            assertTrue(layout.action.bottom - scroll <= layout.viewport.bottom + 0.01f, "$size")
            val center = layout.action.center
            assertEquals(RebirthAction.AdvanceRequested, resolveRebirthPress(layout, scroll, center.x, center.y - scroll), "$size")
            assertTrue(layout.cells.last().right <= layout.frame.width, "$size")
        }
    }
}
