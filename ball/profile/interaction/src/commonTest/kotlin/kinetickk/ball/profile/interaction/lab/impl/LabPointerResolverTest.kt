// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.TestMetaUpgrades
import kinetickk.ball.profile.interaction.profileFrame
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabPointerResolverTest {
    private val model = LabProfileSnapshot(PlayerEconomy(), LabProgress()).toRenderModel(TestMetaUpgrades)

    private fun layout(width: Float, height: Float, textScale: Float = 1f) = labLayout(
        profileFrame(width, height, 1f), model.upgrades.size, model.upgrades.maxOf { it.maxRanks }, textScale, backWidth = 80f,
    )

    @Test
    fun regularRowsMatchTheBoardList() {
        val layout = layout(1440f, 810f)
        val list = layout.listViewport
        val first = layout.rows.first().translate(list.left, list.top)
        assertEquals(44f, first.left, 0.01f)
        assertEquals(106f, first.top, 0.01f)
        assertEquals(950f, first.width, 0.01f)
        assertEquals(72f, first.height, 0.01f)
        assertEquals(106f + 80f, layout.rows[1].top + list.top, 0.01f)
        assertEquals(1060f, layout.icon.left, 0.01f)
        assertEquals(0f, layout.listScrollMax)
    }

    @Test
    fun rowsMapToActivationInTheInjectedOrder() {
        val layout = layout(1440f, 810f)
        val list = layout.listViewport
        fun rowCenter(index: Int) = layout.rows[index].center.let { it.x + list.left to it.y + list.top }
        val state = LabState(model)

        val (x0, y0) = rowCenter(0)
        assertEquals(LabAction.Activate(MetaUpgradeId.CORE_INTEGRITY), resolveLabPress(layout, model, state, 0f, x0, y0))
        val (x7, y7) = rowCenter(7)
        assertEquals(LabAction.Activate(MetaUpgradeId.ARMORY_LICENSE), resolveLabPress(layout, model, state, 0f, x7, y7))

        val reversed = LabProfileSnapshot(PlayerEconomy(), LabProgress()).toRenderModel(TestMetaUpgrades.reversed().toImmutableList())
        assertEquals(LabAction.Activate(MetaUpgradeId.ARMORY_LICENSE), resolveLabPress(layout, reversed, LabState(reversed), 0f, x0, y0))
        // The 8 px gap between rows is not a target.
        assertNull(resolveLabPress(layout, model, state, 0f, x0, layout.rows[0].bottom + list.top + 4f))
    }

    @Test
    fun buyRankAndBackUseTheSelectedUpgradeAndHeaderSlot() {
        val layout = layout(1440f, 810f)
        val selected = LabState(model, selected = MetaUpgradeId.CRYO_VENTS)
        val buy = layout.buy.center
        assertEquals(LabAction.PurchaseRequested(MetaUpgradeId.CRYO_VENTS), resolveLabPress(layout, model, selected, 0f, buy.x, buy.y))
        assertEquals(LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
            resolveLabPress(layout, model, LabState(model), 0f, buy.x, buy.y))
        assertEquals(LabAction.Back, resolveLabPress(layout, model, selected, 0f, layout.back.center.x, layout.back.center.y))
        assertNull(resolveLabPress(layout, model, selected, 0f, layout.buy.center.x, layout.buy.bottom + 40f))
    }

    @Test
    fun buyRankStaysOnScreenAtEveryTextSize() {
        for ((width, height) in listOf(1440f to 810f, 1000f to 700f, 844f to 390f, 720f to 360f, 390f to 844f)) {
            for (textScale in listOf(1f, 1.75f)) {
                val layout = layout(width, height, textScale)
                assertTrue(layout.buy.bottom <= height && layout.buy.top >= layout.frame.headerHeight, "$width x $height @$textScale")
                if (layout.buyPinned) {
                    assertTrue(layout.detailViewport.bottom <= layout.buy.top, "$width x $height @$textScale")
                } else {
                    assertEquals(0f, layout.detailScrollMax, "$width x $height @$textScale")
                    assertTrue(layout.rank.bottom <= layout.buy.top, "$width x $height @$textScale")
                }
                val buy = layout.buy.center
                assertEquals(LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
                    resolveLabPress(layout, model, LabState(model), 0f, buy.x, buy.y), "$width x $height @$textScale")
            }
        }
        assertTrue(layout(390f, 844f, 1.75f).buyPinned)
    }

    @Test
    fun onlyAnEntirelyHiddenSelectedRowScrollsIntoView() {
        // Rows are 64 px tall in a 200 px viewport scrolled to 100.
        assertNull(labRevealScroll(100f, 80f, 144f, 200f, 500f), "partly visible above")
        assertNull(labRevealScroll(100f, 280f, 344f, 200f, 500f), "partly visible below")
        assertEquals(10f, labRevealScroll(100f, 10f, 74f, 200f, 500f), "hidden above: its top")
        assertEquals(224f, labRevealScroll(100f, 360f, 424f, 200f, 500f), "hidden below: its bottom")
        assertEquals(500f, labRevealScroll(0f, 900f, 964f, 200f, 500f), "clamped to the end")
        // Opening on a selection also reveals a partly clipped row.
        assertEquals(144f, labRevealScroll(100f, 280f, 344f, 200f, 500f, whenHidden = false))
        assertEquals(80f, labRevealScroll(100f, 80f, 144f, 200f, 500f, whenHidden = false))
        assertNull(labRevealScroll(100f, 120f, 184f, 200f, 500f, whenHidden = false), "already whole")

        // The last row of the phone list is hidden at rest and the reveal target shows it whole.
        val layout = layout(390f, 844f)
        val last = layout.rows.last()
        val target = assertNotNull(labRevealScroll(0f, last.top, last.bottom, layout.listViewport.height, layout.listScrollMax))
        assertTrue(last.top >= target && last.bottom <= target + layout.listViewport.height)
    }

    @Test
    fun revealedRowsClearTheScrollCueFades() {
        var scrolling = 0
        for ((width, height) in listOf(390f to 844f, 844f to 390f)) {
            for (setting in listOf(1f, 1.25f, 1.75f)) {
                val layout = layout(width, height, profileTextScale(setting))
                val context = "$width x $height @$setting"
                val fade = labListFade(layout.frame)
                val viewport = layout.listViewport.height
                val max = layout.listScrollMax
                // (At 100 % text the landscape list fits without scrolling.)
                if (max <= 0f) continue
                scrolling++
                // Revealing the last row scrolls the list to its end, where the cue draws no bottom
                // fade; revealing the first scrolls back to the start.
                val last = layout.rows.last()
                assertEquals(max, labRevealScroll(0f, last.top, last.bottom, viewport, max, whenHidden = false, fade = fade), context)
                if (last.top >= viewport) assertEquals(max, labRevealScroll(0f, last.top, last.bottom, viewport, max, fade = fade), context)
                val first = layout.rows.first()
                assertEquals(0f, labRevealScroll(max, first.top, first.bottom, viewport, max, whenHidden = false, fade = fade), context)
                // Any row revealed from either end, or from mid-list, ends clear of the active fades.
                for (row in layout.rows) {
                    for (from in listOf(0f, max * 0.5f, max)) {
                        val target = labRevealScroll(from, row.top, row.bottom, viewport, max, whenHidden = false, fade = fade) ?: from
                        val topFade = if (target > 0f) fade else 0f
                        val bottomFade = if (target < max) fade else 0f
                        assertTrue(row.top >= target + topFade - 0.01f && row.bottom <= target + viewport - bottomFade + 0.01f,
                            "$context row $row from $from -> $target")
                    }
                }
            }
        }
        assertTrue(scrolling >= 5, "$scrolling scrolling lists")
    }

    @Test
    fun costColumnSitsBetweenTheValueAndTheRowEnd() {
        for ((width, height) in listOf(1440f to 810f, 844f to 390f, 390f to 844f)) {
            for (textScale in listOf(1f, 1.75f)) {
                val columns = layout(width, height, textScale).columns
                assertTrue(columns.costLeft < columns.costRight, "$width x $height")
                if (!columns.twoLines) assertTrue(columns.valueLeft + columns.valueWidth <= columns.costLeft, "$width x $height @$textScale")
                else assertTrue(columns.nameLeft + columns.nameWidth <= columns.costLeft, "$width x $height @$textScale")
            }
        }
    }

    @Test
    fun compactListsScrollAndMapTheScrolledRow() {
        for ((size, mode) in listOf(
            (844f to 390f) to ProfileLayoutMode.COMPACT_LANDSCAPE,
            (390f to 844f) to ProfileLayoutMode.COMPACT_PORTRAIT,
        )) {
            val layout = layout(size.first, size.second)
            assertEquals(mode, layout.frame.mode)
            assertTrue(layout.listScrollMax > 0f, "$size")
            val list = layout.listViewport
            val pitch = layout.rows[1].top - layout.rows[0].top
            val point = layout.rows[0].center
            assertEquals(LabAction.Activate(MetaUpgradeId.KINETIC_AMPLIFIER),
                resolveLabPress(layout, model, LabState(model), pitch, point.x + list.left, point.y + list.top), "$size")
            // Columns stay inside the row.
            val columns = layout.columns
            assertTrue(columns.costRight <= layout.rows[0].width && columns.pipsLeft >= columns.nameLeft, "$size")
            val widest = model.upgrades.maxOf { it.maxRanks }
            assertTrue(columns.pipsLeft + widest * columns.pipWidth + (widest - 1) * columns.pipGap <= columns.pipsLeft + columns.pipsWidth + 0.01f, "$size")
        }
    }

    @Test
    fun smallestTextSizeKeepsTheBoardRowsAndTouchTargets() {
        // Board row heights: 72 (1440x810), 40 (844x390), 64 (390x844). At 100 % text the labels
        // shrink; the rows (the press targets) and the portrait dock keep the board's size.
        for ((size, board) in listOf((1440f to 810f) to 72f, (844f to 390f) to 40f, (390f to 844f) to 64f)) {
            val small = layout(size.first, size.second, profileTextScale(1f))
            val default = layout(size.first, size.second, profileTextScale(1.25f))
            small.rows.forEach { assertTrue(it.height >= small.frame.d(board) - 0.01f, "$size row ${it.height} < ${small.frame.d(board)}") }
            assertEquals(default.rows, small.rows, "$size")
            assertEquals(default.listViewport, small.listViewport, "$size")
        }
    }
}
