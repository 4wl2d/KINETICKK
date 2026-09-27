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
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(LabAction.Activate(MetaUpgradeId.CORE_INTEGRITY), resolveLabPress(layout, model, state, 0f, 0f, x0, y0))
        val (x7, y7) = rowCenter(7)
        assertEquals(LabAction.Activate(MetaUpgradeId.ARMORY_LICENSE), resolveLabPress(layout, model, state, 0f, 0f, x7, y7))

        val reversed = LabProfileSnapshot(PlayerEconomy(), LabProgress()).toRenderModel(TestMetaUpgrades.reversed().toImmutableList())
        assertEquals(LabAction.Activate(MetaUpgradeId.ARMORY_LICENSE), resolveLabPress(layout, reversed, LabState(reversed), 0f, 0f, x0, y0))
        // The 8 px gap between rows is not a target.
        assertNull(resolveLabPress(layout, model, state, 0f, 0f, x0, layout.rows[0].bottom + list.top + 4f))
    }

    @Test
    fun buyRankAndBackUseTheSelectedUpgradeAndHeaderSlot() {
        val layout = layout(1440f, 810f)
        val selected = LabState(model, selected = MetaUpgradeId.CRYO_VENTS)
        val buy = layout.buy.center
        assertEquals(LabAction.PurchaseRequested(MetaUpgradeId.CRYO_VENTS), resolveLabPress(layout, model, selected, 0f, 0f, buy.x, buy.y))
        assertEquals(LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
            resolveLabPress(layout, model, LabState(model), 0f, 0f, buy.x, buy.y))
        assertEquals(LabAction.Back, resolveLabPress(layout, model, selected, 0f, 0f, layout.back.center.x, layout.back.center.y))
        assertNull(resolveLabPress(layout, model, selected, 0f, 0f, layout.buy.center.x, layout.buy.bottom + 40f))
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
                resolveLabPress(layout, model, LabState(model), pitch, 0f, point.x + list.left, point.y + list.top), "$size")
            // Columns stay inside the row.
            val columns = layout.columns
            assertTrue(columns.costRight <= layout.rows[0].width && columns.pipsLeft >= columns.nameLeft, "$size")
            val widest = model.upgrades.maxOf { it.maxRanks }
            assertTrue(columns.pipsLeft + widest * columns.pipWidth + (widest - 1) * columns.pipGap <= columns.pipsLeft + columns.pipsWidth + 0.01f, "$size")
        }
    }
}
