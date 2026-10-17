// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.WeaponHitCadence
import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The weapon proc rule (SPEC 10.2.8) and the weapon state a new run starts from. */
class WeaponProcRuleTest {
    @Test
    fun onlyAnActivationsFirstHitAndEachSourceOncePerQuarterSecondDispatchWeaponHitClauses() {
        // The every-64th gate never fires its action here, so the clause counter counts dispatches.
        val content = contentWithClauses(
            "Calibrated Coil",
            EffectClause(EffectTrigger.WEAPON_HIT, actions = immutableListOf(EffectAction.Strike(DamageBasis.FLAT, 1f)), everyNth = 64),
        )
        val state = buildTestState(content = content, weapon = WeaponId.ARC_COIL)
        val coil = state.own("Calibrated Coil")
        val clause = content.clauseIndex(coil.id, 0)
        // Level 10 opens all three forms: the chain reaches four targets.
        state.weaponLevel = 10
        val targets = List(4) { index -> state.dummy(x = 60f + index * 50f) }

        state.fireArcCoil(10f, state.beginWeaponActivation())
        assertTrue(targets.all { it.hp < it.maxHp }, "every hop deals damage")
        // The coil's own Charge lands on every hop (the third form Overloads the last one at once).
        assertTrue(targets.dropLast(1).all { it.stacksOf(StatusElement.ION) > 0 }, "every hop applies the coil's own Charge")
        assertEquals(1, state.clauseCounters[clause], "one activation dispatches WEAPON_HIT once")
        state.fireArcCoil(10f, state.beginWeaponActivation())
        assertEquals(2, state.clauseCounters[clause], "the next activation qualifies again")

        // A persistent source (here a contact weapon's main source) qualifies at most once per 0.25 s.
        val start = state.elapsed
        state.dealWeaponDamage(targets[0], 1f, cadence = WeaponHitCadence.CONTACT)
        assertEquals(3, state.clauseCounters[clause])
        state.elapsed = start + 0.1f
        state.dealWeaponDamage(targets[1], 1f, cadence = WeaponHitCadence.CONTACT)
        assertEquals(3, state.clauseCounters[clause], "the same source inside its interval deals damage only")
        state.dealWeaponDamage(targets[1], 1f, cadence = WeaponHitCadence.CONTACT, procKey = WeaponRuntime.sourceKey(1))
        assertEquals(4, state.clauseCounters[clause], "another source keeps its own interval")
        state.elapsed = start + WeaponRuntime.SOURCE_INTERVAL + 0.01f
        state.dealWeaponDamage(targets[2], 1f, cadence = WeaponHitCadence.CONTACT)
        assertEquals(5, state.clauseCounters[clause], "the source qualifies again after its interval")
    }

    @Test
    fun aRestartedRunCarriesNoDashFromTheRunBefore() {
        val state = buildTestState(weapon = WeaponId.SCATTER_BLOOM)
        state.requestDash()
        var steps = 0
        while (state.characterRuntime.dashSequence == 0) {
            state.update(STEP)
            assertTrue(++steps < 120, "the Dash never started")
        }

        state.startRun()
        state.enemies.clear()
        state.projectiles.clear()
        state.spawnClock = Float.MAX_VALUE
        state.runGrace = 0f
        val before = state.elapsed
        state.update(STEP)
        assertTrue(state.elapsed > before, "one step ran")
        // The passive volley fires on the first step; a stale Dash would add a Dash cone to it.
        assertEquals(SCATTER_PASSIVE_PELLETS, state.projectiles.count { !it.hostile && it.sourceWeapon == WeaponId.SCATTER_BLOOM })
    }

    private companion object {
        const val STEP = 1f / 120f
    }
}
