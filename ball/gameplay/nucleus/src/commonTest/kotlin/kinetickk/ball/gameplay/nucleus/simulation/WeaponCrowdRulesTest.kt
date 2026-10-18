// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.model.WeaponEntity
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the weapons balanced against one another treat a lone enemy and a crowd: a crowd weapon's Concussion
 * wave strikes harder the more enemies it catches, while a Solar beam and the Flux Wake trail spend their full
 * heat on few enemies.
 */
class WeaponCrowdRulesTest {
    @Test
    fun aConcussionWaveStrikesHarderTheMoreEnemiesItCatches() {
        /** Damage each of [count] enemies inside the wave takes from one impact. */
        fun wave(count: Int): List<Float> {
            val state = buildTestState(weapon = WeaponId.CONCUSSION_ENGINE, shape = CoreShape.HEXAGON)
            state.critChance = 0f
            val targets = List(count) { index -> state.dummy(x = state.coreX + 30f + index * 8f, y = state.coreY + 20f, hp = TARGET_HP) }
            state.onWeaponImpact(IMPACT_SPEED, state.impactPower(IMPACT_SPEED))
            return targets.map { TARGET_HP - it.hp }
        }
        val lone = wave(1).single()
        assertTrue(lone > 0f, "the wave strikes a lone enemy")
        for (count in listOf(3, CONCUSSION_CROWD_CAP + 1, CONCUSSION_CROWD_CAP + 3)) {
            val expected = 1f + CONCUSSION_CROWD_BONUS * min(count - 1, CONCUSSION_CROWD_CAP)
            wave(count).forEach { taken -> assertEquals(expected, taken / lone, 1e-3f, "$count enemies") }
        }
    }

    @Test
    fun aSolarBeamBurnsTheNearestEnemyInItsPathHottest() {
        val state = buildTestState(weapon = WeaponId.SOLAR_SWEEP, shape = CoreShape.HEXAGON)
        state.critChance = 0f
        val near = state.dummy(x = state.coreX + 80f, y = state.coreY, hp = TARGET_HP)
        val far = state.dummy(x = state.coreX + 170f, y = state.coreY, hp = TARGET_HP)
        val beam = WeaponEntity(WeaponEntityKind.BEAM, state.coreX, state.coreY, 0f, 1f, 1f, extent = SOLAR_LENGTH, angle = 0f)
        state.weaponRuntime.entities.clear()
        state.weaponRuntime.entities += beam
        repeat(STEPS) { state.resolveWeaponEntityHits(STEP) }

        val nearTaken = TARGET_HP - near.hp
        assertTrue(nearTaken > 0f, "the beam burns the nearest enemy in its path")
        assertEquals(SOLAR_SHADOW_SHARE, (TARGET_HP - far.hp) / nearTaken, 1e-3f, "the enemy it shadows")
    }

    @Test
    fun aCrowdInTheFluxWakeTrailSharesItsHeat() {
        /** Damage each of [count] enemies standing on a live trail takes over [STEPS] steps. */
        fun burn(count: Int): List<Float> {
            val state = buildTestState(weapon = WeaponId.FLUX_WAKE, shape = CoreShape.HEXAGON)
            state.critChance = 0f
            state.trail.clear()
            // Far apart: each enemy touches its own trail point only.
            val targets: List<Enemy> = List(count) { index ->
                val x = state.coreX + 200f + index * 120f
                state.trail += TrailPoint(x, state.coreY, life = 60f)
                state.dummy(x = x, y = state.coreY, hp = TARGET_HP)
            }
            repeat(STEPS) { state.resolveWeaponDamage(STEP) }
            return targets.map { TARGET_HP - it.hp }
        }
        val lone = burn(1).single()
        assertTrue(lone > 0f, "the trail burns an enemy in it")
        burn(FLUX_FULL_HEAT_ENEMIES).forEach { taken -> assertEquals(1f, taken / lone, 1e-3f, "up to the full-heat count") }
        val crowd = FLUX_FULL_HEAT_ENEMIES + 2
        burn(crowd).forEach { taken ->
            assertEquals(FLUX_FULL_HEAT_ENEMIES.toFloat() / crowd, taken / lone, 1e-3f, "a crowd of $crowd shares the heat")
        }
    }

    private companion object {
        const val STEP = MutableGameState.FIXED_STEP
        const val STEPS = 12
        const val IMPACT_SPEED = 600f
        /** Low enough integrity that small hits read exactly in Float. */
        const val TARGET_HP = 1_000f
    }
}
