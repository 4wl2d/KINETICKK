// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.WeaponEntity
import kinetickk.ball.gameplay.nucleus.model.WeaponHitCadence
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** Weapon timers and motion: cooldown refunds, orbit phase, the trail's life at cruise and a disc meeting a rock. */
class WeaponTimersTest {
    @Test
    fun aKillRefundAdvancesTheTimersOfWeaponsWithoutAWeaponClock() {
        for (weapon in listOf(WeaponId.SCATTER_BLOOM, WeaponId.GLACIER_BELL, WeaponId.HOLLOW_RIFT, WeaponId.CONCUSSION_ENGINE)) {
            val plain = secondsBetweenActivations(weapon, refund = false)
            val refunded = secondsBetweenActivations(weapon, refund = true)
            assertTrue(refunded <= plain - 0.1f, "$weapon: a Hair Trigger refund changed its cadence from $plain s to $refunded s")
        }
        // Contact weapons have no cooldown to advance: the refund surges their damage instead.
        for (weapon in listOf(WeaponId.MORNINGSTAR, WeaponId.RIFT_BLADES)) {
            val state = buildTestState(weapon = weapon, shape = CoreShape.HEXAGON)
            state.own("Hair Trigger")
            val target = state.dummy()
            val before = state.dealWeaponDamage(target, 10f, cadence = WeaponHitCadence.CONTACT).amount
            state.fireClauses(EffectTrigger.KILL, target = state.dummy(x = -400f))
            val after = state.dealWeaponDamage(target, 10f, cadence = WeaponHitCadence.CONTACT).amount
            assertTrue(after > before * 1.2f, "$weapon: a refund raised a contact hit only from $before to $after")
        }
    }

    @Test
    fun achangeOfAttackSpeedBendsTheRiftBladesOrbitWithoutAJump() {
        val state = buildTestState(weapon = WeaponId.RIFT_BLADES, shape = CoreShape.HEXAGON)
        // Late in a run: an orbit phase derived from the run clock would jump by elapsed × the speed change.
        state.elapsed = 300f
        state.stepWeapons()
        val steady = state.stepWeapons().max()
        assertTrue(steady > 0f, "the blades orbit")
        state.attackSpeed += 0.25f
        val changed = state.stepWeapons()
        assertTrue(changed.all { it <= steady * 1.5f }, "a blade jumped ${changed.max()} in one step (steady $steady)")
    }

    @Test
    fun aLongTrailKeepsItsWholeLifeAtCruiseSpeed() {
        // Second form (level 6): the trail lasts 4 s.
        val straight = buildTestState(weapon = WeaponId.FLUX_WAKE, shape = CoreShape.DIAMOND).apply { weaponLevel = 6 }
        straight.fly(seconds = 6.5f) { _ -> CRUISE to 0f }
        val straightOldest = straight.oldestBurningTrailAge()
        assertTrue(straightOldest >= 3.9f, "the oldest burning point of a 4 s trail at 1 100 u/s is $straightOldest s old")

        // Orb signature: a trail laid while turning lasts 50 % longer (6 s).
        val radius = 80f
        val turning = buildTestState(weapon = WeaponId.FLUX_WAKE, shape = CoreShape.ORB).apply { weaponLevel = 6 }
        turning.fly(seconds = 6.5f) { t ->
            val angle = t * CRUISE / radius
            -sin(angle) * CRUISE to cos(angle) * CRUISE
        }
        val turningOldest = turning.oldestBurningTrailAge()
        assertTrue(turningOldest >= 5.9f, "the oldest burning point of a 6 s turning trail at 1 100 u/s is $turningOldest s old")
        assertTrue(turning.trail.size <= MutableGameState.MAX_TRAIL_POINTS)
    }

    @Test
    fun aRicochetingDiscComesHomeAroundARockOnItsReturnPass() {
        val state = buildTestState(weapon = WeaponId.GYRE_DISC, shape = CoreShape.HEXAGON)
        state.weaponLevel = 10
        val coreX = state.coreX
        val coreY = state.coreY
        // The rock stands between the returning disc and the Core.
        state.placeObstaclesForTesting(testObstacle(coreX + 200f, coreY, radius = 60f))
        val entities = state.weaponRuntime.entities
        entities.clear()
        val disc = WeaponEntity(WeaponEntityKind.DISC, coreX + 400f, coreY, GYRE_RADIUS, GYRE_MAX_LIFE, GYRE_MAX_LIFE, phase = 1)
        entities += disc
        state.takeVisualFxCues()
        var bursts = 0
        while (disc in entities) {
            val x = disc.x
            val y = disc.y
            disc.capturePrevious()
            state.advanceWeaponEntities(STEP)
            bursts += state.takeVisualFxCues().count { it is VisualFxCue.Burst }
            if (disc !in entities) break
            assertTrue(disc.x != x || disc.y != y, "the disc stopped at ($x, $y)")
        }
        val distance = length(disc.x - coreX, disc.y - coreY)
        assertTrue(distance <= GYRE_CATCH_RADIUS, "the disc ended $distance from the Core instead of returning")
        assertTrue(bursts <= 1, "the rock sparked $bursts times")
    }

    private fun secondsBetweenActivations(weapon: WeaponId, refund: Boolean): Float {
        val state = buildTestState(weapon = weapon, shape = CoreShape.HEXAGON)
        if (refund) state.own("Hair Trigger")
        val first = state.stepUntilActivation()
        // A kill right after the activation: Hair Trigger refunds part of the cooldown (an unowned item does nothing).
        state.fireClauses(EffectTrigger.KILL, target = state.dummy(x = -400f))
        return state.stepUntilActivation() - first
    }

    /** Steps the weapon (moving fast, striking every step) until an activation starts; returns its time. */
    private fun MutableGameState.stepUntilActivation(): Float {
        val serial = weaponRuntime.activationSerial
        repeat(MAX_STEPS) {
            velocityX = CRUISE
            velocityY = 0f
            if (weapon == WeaponId.CONCUSSION_ENGINE) onWeaponImpact(CRUISE, 10f)
            if (weaponRuntime.activationSerial != serial) return elapsed
            elapsed += STEP
            updateWeapons(STEP)
            takeVisualFxCues()
            takeSoundCues()
            if (weaponRuntime.activationSerial != serial) return elapsed
        }
        error("$weapon never activated")
    }

    /** One weapon step with the Core at rest; returns how far each orbital moved. */
    private fun MutableGameState.stepWeapons(): List<Float> {
        val before = weaponOrbitals.map { it.x to it.y }
        elapsed += STEP
        updateWeapons(STEP)
        takeVisualFxCues()
        return weaponOrbitals.mapIndexed { index, orbital ->
            before.getOrNull(index)?.let { (x, y) -> length(orbital.x - x, orbital.y - y) } ?: 0f
        }
    }

    /** Flies the Core at [velocity] (of the flight time) for [seconds], stepping the weapon. */
    private fun MutableGameState.fly(seconds: Float, velocity: (Float) -> Pair<Float, Float>) {
        trail.clear()
        trailLastX = coreX
        trailLastY = coreY
        var t = 0f
        while (t < seconds) {
            t += STEP
            val (vx, vy) = velocity(t)
            previousCoreX = coreX
            previousCoreY = coreY
            velocityX = vx
            velocityY = vy
            coreX += vx * STEP
            coreY += vy * STEP
            elapsed += STEP
            updateWeapons(STEP)
            takeVisualFxCues()
        }
    }

    private fun MutableGameState.oldestBurningTrailAge(): Float =
        trail.filter { it.age < it.life }.maxOfOrNull { it.age } ?: 0f

    private companion object {
        const val STEP = MutableGameState.FIXED_STEP
        const val CRUISE = 1_100f
        const val MAX_STEPS = 1_200
    }
}
