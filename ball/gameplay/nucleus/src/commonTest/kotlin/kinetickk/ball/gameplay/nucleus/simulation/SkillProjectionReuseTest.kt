// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SkillProjectionReuseTest {
    @Test
    fun oneChangingSlotRetainsOtherSlotsAndPreviousSnapshots() {
        val state = buildTestState()
        state.acquireActive(SkillId.METEOR, 1)
        state.acquireActive(SkillId.MASS_DRIVER, 1)
        val meteor = state.skillLoadout.slotOf(SkillId.METEOR)
        val driver = state.skillLoadout.slotOf(SkillId.MASS_DRIVER)
        val initial = state.toRenderModel()
        val retained = initial.skillBar.slots.toList()
        state.skillRuntime[meteor, SkillCell.PENDING_AT] = state.elapsed + 0.5f
        state.skillRuntime[meteor, SkillCell.ANCHOR_X] = 321f
        state.skillRuntime[meteor, SkillCell.ANCHOR_Y] = -123f
        val pending = state.toRenderModel(initial)
        assertEquals(state.toRenderModel().skillBar, pending.skillBar)
        assertNotSame(initial.skillBar.slot(meteor), pending.skillBar.slot(meteor))
        assertSame(initial.skillBar.slot(driver), pending.skillBar.slot(driver))
        assertEquals(retained, initial.skillBar.slots)
        assertSame(pending.skillBar, state.toRenderModel(pending).skillBar)

        // Every stored slot scalar and the elapsed-time-derived values must invalidate reuse correctly.
        for (cell in listOf(
            SkillCell.READY_AT, SkillCell.LAST_COOLDOWN, SkillCell.ACTIVE_UNTIL,
            SkillCell.REFUNDED, SkillCell.REFUSAL, SkillCell.REFUSED_AT,
        )) {
            state.skillRuntime[meteor, cell] = state.elapsed + 3f
            val next = state.toRenderModel(pending)
            assertEquals(state.toRenderModel().skillBar, next.skillBar, "cell $cell")
        }
        state.elapsed += 1f
        assertEquals(state.toRenderModel().skillBar, state.toRenderModel(pending).skillBar)
        assertEquals(retained, initial.skillBar.slots)
    }
}
