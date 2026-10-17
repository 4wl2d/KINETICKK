// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PickupVacuumTest {
    private val step = MutableGameState.FIXED_STEP

    @Test
    fun aRunStartsWithTheWideMagnetAndCollectsAtFiftySix() {
        val state = buildTestState()
        assertEquals(320f, canonicalGameplayContent.pickups.magnetRadius)
        assertEquals(320f, state.pickupRadius)
        assertEquals(320f, state.toRenderModel().pickupRadius)
        state.pickups += Pickup(PickupType.DATA, 55f, 0f)
        state.pickups += Pickup(PickupType.DATA, 57f + 1f, 30f)
        state.resolvePickupCollection()
        assertEquals(1, state.pickups.size, "only the pickup within 56 is collected")
    }

    @Test
    fun enteringTheRadiusLatchesAPickupThatHomesFasterThanTheCoreAndIsNeverLost() {
        val state = buildTestState()
        val inside = Pickup(PickupType.DATA, 300f, 0f)
        val outside = Pickup(PickupType.DATA, 0f, 330f)
        state.pickups += listOf(inside, outside)
        state.updatePickups(step)
        assertTrue(inside.attracted)
        assertFalse(outside.attracted)
        assertEquals(-950f, inside.vx, 0.01f, "homes at the vacuum floor")
        assertEquals(0f, outside.vx)

        // The Core races away at 2 000 u/s: the latched pickup follows at 1.3 x that, the other stays behind.
        state.setVelocityForTesting(2_000f, 0f)
        repeat(60) {
            state.coreX += 2_000f * step
            state.updatePickups(step)
        }
        assertTrue(inside.attracted, "a latched pickup is never lost")
        assertEquals(2_600f, length(inside.vx, inside.vy), 0.5f)
        // It caught up: within one homing step of the Core (collection would have taken it at 56).
        assertTrue(length(inside.x - state.coreX, inside.y - state.coreY) < 2_600f * step + 1f)
        assertFalse(outside.attracted)
    }

    @Test
    fun aLevelUpAndTheEndOfAChoiceVacuumOnScreenDataOnly() {
        val state = buildTestState()
        val onScreen = Pickup(PickupType.DATA, 500f, 200f)
        val offScreen = Pickup(PickupType.DATA, 2_000f, 0f)
        val key = Pickup(PickupType.KEY, 450f, -200f)
        state.pickups += listOf(onScreen, offScreen, key)
        state.grantDataForTesting(state.nextLevelData.toFloat() / state.dataGainLiability() + 1f)
        assertTrue(onScreen.attracted)
        assertFalse(offScreen.attracted)
        assertFalse(key.attracted, "only Data is vacuumed")

        val chosen = buildTestState()
        val loose = Pickup(PickupType.DATA, -500f, -250f)
        chosen.pickups += loose
        chosen.pendingLevelChoices = 1
        chosen.openNextPendingChoice()
        assertEquals(GamePhase.CHOICE, chosen.phase)
        chosen.choose(0)
        assertEquals(GamePhase.RUNNING, chosen.phase)
        assertTrue(loose.attracted, "the end of a choice vacuums Data too")
    }

    @Test
    fun eventHorizonDisablesTheLatchAndTheLevelUpVacuumButKeepsTheRadius() {
        val state = buildTestState(RelicId.EVENT_HORIZON)
        assertEquals(state.pickupRadius, state.effectivePickupRadius())
        val near = Pickup(PickupType.DATA, 200f, 0f)
        val far = Pickup(PickupType.DATA, 600f, 0f)
        state.pickups += listOf(near, far)
        state.updatePickups(step)
        assertFalse(near.attracted)
        assertTrue(near.vx < 0f, "inside the radius it is still pulled")
        assertTrue(near.vx > -950f * 0.5f, "but far slower than the vacuum")
        state.grantDataForTesting(state.nextLevelData.toFloat() / state.dataGainLiability() + 1f)
        assertFalse(far.attracted, "no level-up vacuum")
    }

    @Test
    fun keysAndRelicsNeverExpireWhileDataFades() {
        val state = buildTestState()
        val data = Pickup(PickupType.DATA, 5_000f, 0f, life = 0.01f)
        val key = Pickup(PickupType.KEY, 5_000f, 100f, life = 0.01f)
        val relic = Pickup(PickupType.RELIC, 5_000f, 200f, life = 0.01f)
        state.pickups += listOf(data, key, relic)
        state.updatePickups(step * 4)
        assertFalse(data in state.pickups)
        assertTrue(key in state.pickups)
        assertTrue(relic in state.pickups)
    }

    @Test
    fun theCapDropsNewDataButEvictsTheOldestDataForKeysAndRelics() {
        val state = buildTestState()
        val first = Pickup(PickupType.DATA, 9_000f, 0f)
        val oldestKey = Pickup(PickupType.KEY, 9_000f, 10f)
        state.pickups += oldestKey
        state.pickups += first
        repeat(MutableGameState.MAX_PICKUPS - 2) { state.pickups += Pickup(PickupType.DATA, 9_000f, 20f) }
        assertEquals(MutableGameState.MAX_PICKUPS, state.pickups.size)

        val lateData = Pickup(PickupType.DATA, 9_000f, 30f)
        state.addPickup(lateData)
        assertFalse(lateData in state.pickups)
        assertEquals(MutableGameState.MAX_PICKUPS, state.pickups.size)

        val relic = Pickup(PickupType.RELIC, 9_000f, 40f)
        state.addPickup(relic)
        assertTrue(relic in state.pickups)
        assertTrue(oldestKey in state.pickups, "a Key is never evicted")
        assertFalse(first in state.pickups, "the oldest Data made room")
        assertEquals(MutableGameState.MAX_PICKUPS, state.pickups.size)
    }

    @Test
    fun pickupRadiusNeverFallsBelowOneHundredTwenty() {
        val state = buildTestState()
        state.applyStatDelta(ItemEffect.PICKUP_RADIUS, -1_000f)
        assertEquals(120f, state.pickupRadius)
        state.applyStatDelta(ItemEffect.PICKUP_RADIUS, 30f)
        assertEquals(150f, state.pickupRadius)
    }

    @Test
    fun magnetLensWidensTheMagnetByNinety() {
        val state = buildTestState()
        val before = state.pickupRadius
        state.own("Magnet Lens")
        assertEquals(before + 90f, state.pickupRadius, 0.01f)
    }
}
