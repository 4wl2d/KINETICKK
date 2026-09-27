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
    fun phoneBandAndStrikeStayClearOfContent() {
        for ((width, height) in listOf(844f to 390f, 390f to 844f)) {
            val layout = layout(width, height, target = 6)
            val band = rebirthBandRect(layout.frame, androidx.compose.ui.geometry.Offset.Zero, 1f, width)
            // The tier 5+ band runs along the header's lower edge, above every content row.
            assertTrue(band.bottom <= layout.from.top, "$width x $height $band ${layout.from}")
            assertTrue(band.bottom <= layout.tableHeader.top, "$width x $height")
        }
        // The strike keeps the board's proportion: 8 px on the 58 px board numeral.
        assertEquals(8f, rebirthStrikeThickness(58f * 0.9f), 0.05f)
        assertTrue(rebirthStrikeThickness(30f * 0.9f) < 5f)
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
            // Advance is on screen without scrolling (pinned when the content overflows) and maps
            // at the same place whatever the scroll.
            assertTrue(layout.action.bottom <= layout.frame.height && layout.action.top >= layout.viewport.top, "$size")
            val center = layout.action.center
            for (scroll in listOf(0f, layout.scrollMax)) {
                val y = if (layout.actionPinned) center.y else center.y - scroll
                assertEquals(RebirthAction.AdvanceRequested, resolveRebirthPress(layout, scroll, center.x, y), "$size $scroll")
            }
            if (layout.actionPinned) assertTrue(layout.viewport.bottom <= layout.action.top, "$size")
            assertTrue(layout.cells.last().right <= layout.frame.width, "$size")
        }
    }
}
