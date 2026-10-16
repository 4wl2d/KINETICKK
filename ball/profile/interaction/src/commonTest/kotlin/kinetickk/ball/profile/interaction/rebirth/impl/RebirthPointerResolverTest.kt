// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.ui.geometry.Offset
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.profileFrame
import kinetickk.ball.profile.interaction.profileTextScale
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RebirthPointerResolverTest {
    private fun layout(
        width: Float,
        height: Float,
        target: Int = 4,
        textScale: Float = 1f,
        buildRows: Int = 0,
        heirloomRows: Int = 0,
        unlockLines: List<Int> = emptyList(),
    ) = rebirthLayout(profileFrame(width, height, 1f), 0, 10, target, textScale, backWidth = 80f, buildRows, heirloomRows, unlockLines)

    /** Advance lies wholly on screen below the header and maps at its center, at the top, middle and end of the scroll. */
    private fun assertAdvanceReachable(layout: RebirthLayout, context: String) {
        for (scroll in listOf(0f, layout.scrollMax * 0.5f, layout.scrollMax)) {
            val action = layout.actionAt(scroll)
            assertTrue(action.top >= layout.frame.headerHeight && action.bottom <= layout.frame.height,
                "$context scroll $scroll: Advance $action outside ${layout.frame.headerHeight}..${layout.frame.height}")
            for (point in listOf(action.center, Offset(action.left + 4f, action.top + 4f), Offset(action.right - 4f, action.bottom - 4f))) {
                assertEquals(RebirthAction.AdvanceRequested, resolveRebirthPress(layout, scroll, point.x, point.y), "$context $scroll $point")
            }
        }
    }

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
            // Advance is on screen without scrolling (pinned when the content overflows, or in the
            // fixed hero column of a landscape phone) and maps at the same place whatever the scroll.
            assertTrue(layout.action.bottom <= layout.frame.height && layout.action.top >= layout.viewport.top, "$size")
            assertAdvanceReachable(layout, "$size")
            if (layout.actionPinned) assertTrue(layout.viewport.bottom <= layout.action.top, "$size")
            assertTrue(layout.cells.last().right <= layout.frame.width, "$size")
        }
    }

    @Test
    fun boardSetsTheBuildSectionsInTwoColumnsUnderTheLadder() {
        assertNull(layout(1440f, 810f).buildHeader)
        for (textScale in listOf(1f, profileTextScale(1.75f))) {
            // Tier 2 is the tallest table: the Reborn row and two featured items of two lines each.
            val layout = layout(1440f, 810f, textScale = textScale, buildRows = 3, heirloomRows = 4, unlockLines = listOf(2, 2))
            val header = assertNotNull(layout.buildHeader)
            val heirlooms = assertNotNull(layout.heirloomLabel)
            assertEquals(3, layout.buildRows.size)
            assertEquals(4, layout.heirloomRows.size)
            // Under the ladder, inside the left section, beside each other with a shared header line.
            assertTrue(header.top > layout.ladder.bottom, "$header")
            assertEquals(layout.ladder.left, header.left, 0.01f)
            assertTrue(header.right < heirlooms.left && heirlooms.right <= layout.ladder.right + 0.01f, "$header $heirlooms")
            assertEquals(header.top, heirlooms.top, 0.01f)
            assertTrue(layout.heirloomRows.last().right < layout.tableHeader.left, "clear of the comparison tables")
            // The whole board still fits without scrolling, and the Advance button keeps its place.
            assertEquals(0f, layout.scrollMax, "$textScale")
            assertEquals(layout(1440f, 810f, textScale = textScale).action, layout.action)
            assertTrue(layout.buildRows.zipWithNext().all { (a, b) -> a.bottom <= b.top + 0.01f })
        }
    }

    @Test
    fun phonesStackTheBuildSectionsUnderTheCompensationRows() {
        for ((width, height) in listOf(844f to 390f, 740f to 360f, 915f to 412f, 390f to 844f, 360f to 640f)) {
            for (textScale in listOf(1f, profileTextScale(1.5f), profileTextScale(1.75f))) {
                val context = "$width x $height @$textScale"
                val plain = layout(width, height, textScale = textScale)
                // The worst case: a tier 2 target (Reborn row and two items) and four Heirlooms.
                val layout = layout(width, height, textScale = textScale, buildRows = 3, heirloomRows = 4, unlockLines = listOf(3, 3))
                val header = assertNotNull(layout.buildHeader, context)
                val heirlooms = assertNotNull(layout.heirloomLabel, context)
                assertTrue(header.top >= layout.compensationRows.last().bottom, context)
                assertEquals(layout.tableHeader.left, header.left, 0.01f, context)
                assertEquals(layout.tableHeader.right, header.right, 0.01f, context)
                assertTrue(heirlooms.top >= layout.buildRows.last().bottom, context)
                // The scroll reaches the last Heirloom above the fold of a pinned Advance.
                val last = layout.heirloomRows.last()
                assertTrue(last.bottom - layout.scrollMax <= layout.viewport.bottom + 0.5f, "$context ${layout.scrollMax}")
                assertTrue(layout.scrollMax >= plain.scrollMax, context)
                // Advance stays wholly on screen and maps at every scroll.
                assertTrue(layout.action.bottom <= layout.frame.height && layout.action.top >= layout.viewport.top, context)
                assertAdvanceReachable(layout, context)
                // The build rows never take presses.
                val row = layout.buildRows.first().center
                if (!layout.action.contains(row)) assertNull(resolveRebirthPress(layout, 0f, row.x, row.y), context)
            }
        }
        // The portrait Advance follows the build sections when nothing overflows.
        val tall = layout(390f, 1400f, buildRows = 2, heirloomRows = 3)
        assertTrue(!tall.actionPinned && tall.action.top > tall.heirloomRows.last().bottom, "${tall.action}")
    }

    @Test
    fun landscapePhonesKeepTheHeroColumnInPlaceAndScrollOnlyTheTables() {
        for ((width, height) in listOf(844f to 390f, 740f to 360f, 915f to 412f)) {
            val layout = layout(width, height, textScale = profileTextScale(1.75f), buildRows = 3, heirloomRows = 4,
                unlockLines = listOf(3, 3))
            val context = "$width x $height"
            assertTrue(layout.heroFixed && !layout.actionPinned && !layout.actionScrolls, context)
            // The scroll area is the aside column: it starts right of the ladder and the Advance button.
            assertTrue(layout.viewport.left >= layout.ladder.right && layout.viewport.left >= layout.action.right, context)
            assertTrue(layout.viewport.left <= layout.tableHeader.left, context)
            assertTrue(layout.scrollMax > 0f, "$context the tables overflow at this size")
            // The hero column fits below the header; scrolling reaches the last Heirloom.
            assertTrue(layout.from.top >= layout.frame.headerHeight && layout.action.bottom <= layout.frame.height, context)
            assertTrue(layout.heirloomRows.last().bottom - layout.scrollMax <= layout.viewport.bottom + 0.5f, context)
            assertEquals(layout.action, layout.actionAt(layout.scrollMax), context)
        }
        // Portrait phones keep a single scroll (Advance pinned or following the tables).
        assertFalse(layout(390f, 844f, textScale = profileTextScale(1.75f), buildRows = 3, heirloomRows = 4).heroFixed)
    }

    @Test
    fun featuredUnlockRowsGrowWithTheirDescriptionAndTheTextSize() {
        for ((width, height) in listOf(1440f to 810f, 844f to 390f, 390f to 844f)) {
            val context = "$width x $height"
            val one = layout(width, height, buildRows = 2, unlockLines = listOf(1))
            val three = layout(width, height, buildRows = 2, unlockLines = listOf(3))
            val large = layout(width, height, textScale = profileTextScale(1.75f), buildRows = 2, unlockLines = listOf(3))
            // The Reborn row keeps the table row; the featured row holds its mark and grows with its lines and the text.
            assertEquals(one.compensationRows.first().height, one.buildRows.first().height, 0.01f, context)
            assertTrue(one.buildRows[1].height >= rebirthUnlockIconSize(one.frame), context)
            assertTrue(three.buildRows[1].height > one.buildRows[1].height, context)
            assertTrue(large.buildRows[1].height > three.buildRows[1].height, context)
            // More lines than the row holds keep the largest row.
            assertEquals(three.buildRows[1], layout(width, height, buildRows = 2, unlockLines = listOf(7)).buildRows[1], context)
            assertTrue(three.buildRows.zipWithNext().all { (a, b) -> abs(a.bottom - b.top) < 0.01f }, context)
        }
    }

    @Test
    fun smallestTextSizeKeepsTheBoardTableRows() {
        for ((width, height) in listOf(1440f to 810f, 844f to 390f, 390f to 844f)) {
            val small = layout(width, height, textScale = profileTextScale(1f))
            val default = layout(width, height, textScale = profileTextScale(1.25f))
            val context = "$width x $height"
            assertEquals(default.tableHeader, small.tableHeader, context)
            assertEquals(default.hostileRows, small.hostileRows, context)
            assertEquals(default.compensationRows, small.compensationRows, context)
        }
    }
}
