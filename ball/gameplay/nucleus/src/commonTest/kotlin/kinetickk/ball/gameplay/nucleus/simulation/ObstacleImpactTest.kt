// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 0.6.0 impact rule on the 1280×720 test viewport: a collision counts its normal speed plus a share of its
 * sliding speed (never more than the Core's speed), every hurting hit costs at least the damage floor and part of
 * its sliding speed, shields let a share through, one cooldown spaces hits per surface, ricochets need their own
 * speed and the lethal-speed bonuses stop at the build's ceiling.
 */
class ObstacleImpactTest {
    private fun MutableGameState.bandSpeed(fraction: Float): Float {
        val start = obstacleDamageStartSpeed()
        return start + (obstacleLethalSpeed() - start) * fraction
    }

    /** Flies the Core along +x into a rock centred at ([rockX], [rockY]) so it meets the surface [degrees] off the normal. */
    private fun MutableGameState.hit(speed: Float, degrees: Float = 0f, rockX: Float = ROCK_X, rockY: Float = 0f) {
        val combined = ROCK_RADIUS + coreRadius
        val angle = degrees * PI.toFloat() / 180f
        val contactX = rockX - combined * cos(angle)
        flyIntoObstaclesForTesting(contactX + speed / 240f, rockY + combined * sin(angle), speed, 0f)
    }

    private fun MutableGameState.ramRock(speed: Float, degrees: Float = 0f, kind: ObstacleKind = ObstacleKind.ASTEROID) {
        placeObstaclesForTesting(testObstacle(ROCK_X, 0f, ROCK_RADIUS, kind))
        hit(speed, degrees)
    }

    private fun MutableGameState.lost(): Float = maxHp - hp

    /** Damage the shipped band gives a hit at [impact] on this state, tier-0 forgiveness included. */
    private fun MutableGameState.shippedBandDamage(impact: Float): Float {
        val start = obstacleDamageStartSpeed()
        val band = ((impact - start) / (obstacleLethalSpeed() - start)).coerceIn(0f, 1f)
        return maxHp * (0.06f + 0.49f * band) * 0.7f
    }

    @Test
    fun aBandSpeedHeadOnHitAlwaysDamagesAtLeastTheFloor() {
        val tierZero = buildTestState()
        val start = tierZero.obstacleDamageStartSpeed()
        tierZero.ramRock(start + 1f)
        // Tier 0: 6 % of maximum integrity × forgiving 0.7 (the 1 u/s into the band adds 0.003 %).
        assertEquals(tierZero.maxHp * 0.06f * 0.7f, tierZero.lost(), tierZero.maxHp * 0.001f)
        assertTrue(tierZero.lost() >= tierZero.maxHp * 0.06f * 0.7f)

        val below = buildTestState()
        below.ramRock(start - 1f)
        assertEquals(0f, below.lost(), "just below the damage start the hit is free")

        // Tier 3: forgiveness is spent and the collision tier factor 1 + 0.04 × 3 applies.
        val tierThree = buildTestState(rebirth = 3)
        tierThree.ramRock(tierThree.obstacleDamageStartSpeed() + 1f)
        assertEquals(tierThree.maxHp * 0.06f * 1.12f, tierThree.lost(), tierThree.maxHp * 0.001f)
    }

    @Test
    fun aGlancingHitAtSpeedHurtsByItsScrape() {
        val state = buildTestState()
        val speed = state.bandSpeed(0.6f)
        val diagonal = sin(PI.toFloat() / 4f)
        val impact = speed * diagonal + 0.3f * speed * diagonal
        assertTrue(impact < speed)
        state.ramRock(speed, degrees = 45f)
        assertEquals(state.shippedBandDamage(impact), state.lost(), 0.05f)

        // Without the scrape the same glance counts only its normal speed and costs far less.
        val normalOnly = buildTestState(
            content = canonicalGameplayContent.copy(obstacles = canonicalGameplayContent.obstacles.copy(scrapeShare = 0f)),
        )
        normalOnly.ramRock(speed, degrees = 45f)
        assertEquals(normalOnly.shippedBandDamage(speed * diagonal), normalOnly.lost(), 0.05f)
        assertTrue(normalOnly.lost() < state.lost() * 0.5f, "${normalOnly.lost()} vs ${state.lost()}")
    }

    @Test
    fun theImpactNeverExceedsTheCoreSpeed() {
        val probe = buildTestState()
        val speed = probe.bandSpeed(0.5f)
        val angle = 20f * PI.toFloat() / 180f
        val normal = speed * cos(angle)
        val sliding = speed * sin(angle)
        assertTrue(normal + 0.3f * sliding > speed, "the plain sum would pass the Core's speed at 20°")
        assertEquals(speed, probe.obstacleImpactSpeed(normal, sliding), 0.01f)

        // So a 20° glance costs exactly what a head-on hit at the same speed costs.
        val glance = buildTestState()
        glance.ramRock(speed, degrees = 20f)
        val headOn = buildTestState()
        headOn.ramRock(speed)
        assertEquals(headOn.lost(), glance.lost(), 0.05f)
        assertEquals(headOn.shippedBandDamage(speed), headOn.lost(), 0.05f)
    }

    @Test
    fun aSecondRockHurtsInsideTheFirstRocksCooldown() {
        val state = buildTestState()
        state.placeObstaclesForTesting(
            testObstacle(ROCK_X, 0f, ROCK_RADIUS),
            testObstacle(ROCK_X, SECOND_ROCK_Y, ROCK_RADIUS),
        )
        val speed = state.bandSpeed(0.4f)
        state.hit(speed)
        val afterFirst = state.hp
        assertTrue(afterFirst < state.maxHp)
        state.elapsed += 0.1f
        state.hit(speed, rockY = SECOND_ROCK_Y)
        assertEquals(afterFirst - state.shippedBandDamage(speed), state.hp, 0.05f, "another rock lands at once")
    }

    @Test
    fun theSameRockWaitsForItsCooldown() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(ROCK_X, 0f, ROCK_RADIUS))
        val speed = state.bandSpeed(0.4f)
        state.hit(speed)
        val afterFirst = state.hp
        assertTrue(afterFirst < state.maxHp)
        state.elapsed += 0.2f
        state.hit(speed)
        assertEquals(afterFirst, state.hp, "0.2 s later the same rock is still cooling down")
        state.elapsed += 0.1f
        state.hit(speed)
        assertEquals(afterFirst - state.shippedBandDamage(speed), state.hp, 0.05f, "after 0.25 s it hurts again")
    }

    @Test
    fun aHurtingHitCostsSlidingSpeed() {
        // Unit tangent of a +x flight meeting the rock 45° off the normal (-cos, sin).
        val diagonal = sin(PI.toFloat() / 4f)
        fun MutableGameState.sliding(): Float = velocityX * diagonal + velocityY * diagonal

        val hurt = buildTestState()
        val speed = hurt.bandSpeed(0.5f)
        val impact = 1.3f * speed * diagonal
        val start = hurt.obstacleDamageStartSpeed()
        val share = 0.06f + 0.49f * (impact - start) / (hurt.obstacleLethalSpeed() - start)
        hurt.ramRock(speed, degrees = 45f)
        assertTrue(hurt.hp < hurt.maxHp)
        assertEquals(speed * diagonal * (1f - 0.35f * share / 0.55f), hurt.sliding(), 0.5f)

        val gentle = buildTestState()
        val slow = gentle.obstacleDamageStartSpeed() * 0.8f
        gentle.ramRock(slow, degrees = 45f)
        assertEquals(gentle.maxHp, gentle.hp, "a glance below the damage start is free")
        assertEquals(slow * diagonal, gentle.sliding(), 0.5f, "and keeps its sliding speed")
    }

    @Test
    fun aRicochetNeedsTheRicochetSpeed() {
        val content = contentWithClauses(
            "Calibrated Coil",
            EffectClause(EffectTrigger.RICOCHET, actions = immutableListOf(EffectAction.Overdrive(20f)), cooldownSeconds = 0.5f),
        )
        fun armed() = buildTestState(content = content).apply { own("Calibrated Coil") }
        val probe = armed()
        val start = probe.obstacleDamageStartSpeed()
        val ricochet = probe.obstacleRicochetSpeed()
        assertTrue(ricochet > start + 100f, "the ricochet speed sits above the damage start: $start / $ricochet")

        val band = armed()
        band.ramRock((start + ricochet) * 0.5f)
        assertTrue(band.hp < band.maxHp, "it hurts")
        assertEquals(0, band.pendingRicochets)
        assertEquals(0f, band.overdriveCharge, "no RICOCHET clause fired")

        val justBelow = armed()
        justBelow.ramRock(ricochet - 1f)
        assertEquals(0, justBelow.pendingRicochets)

        val atSpeed = armed()
        atSpeed.ramRock(ricochet)
        assertEquals(1, atSpeed.pendingRicochets)
        assertTrue(atSpeed.overdriveCharge >= 20f, "the RICOCHET clause fired")

        // The ricochet reads the normal speed: a glance whose scrape lifts it past the ricochet speed is a band hit.
        val glance = armed()
        val fast = ricochet * 1.2f
        glance.ramRock(fast, degrees = 45f)
        assertTrue(1.3f * fast * sin(PI.toFloat() / 4f) >= ricochet)
        assertTrue(glance.hp < glance.maxHp)
        assertEquals(0, glance.pendingRicochets)
    }

    @Test
    fun shieldsLetTheBleedThrough() {
        val plain = buildTestState()
        val speed = plain.bandSpeed(0.5f)
        plain.ramRock(speed)
        val damage = plain.lost()
        assertTrue(damage > 0f)

        val shielded = buildTestState().apply {
            maxShield = 200f
            shield = 200f
        }
        shielded.ramRock(speed)
        assertEquals(damage * 0.4f, shielded.lost(), 0.01f, "40 % reaches integrity")
        assertEquals(200f - damage * 0.6f, shielded.shield, 0.01f, "the shield takes the rest")

        val thin = buildTestState().apply {
            maxShield = 200f
            shield = 2f
        }
        thin.ramRock(speed)
        assertEquals(0f, thin.shield)
        assertEquals(damage - 2f, thin.lost(), 0.01f, "a thin shield absorbs only what it holds")

        val glass = buildTestState(RelicId.GLASS_COMET).apply {
            maxShield = 200f
            shield = 200f
        }
        glass.ramRock(speed)
        assertEquals(200f, glass.shield, "Glass Comet keeps shields out")
        assertEquals(damage * 2f, glass.lost(), 0.01f, "all of the doubled hit reaches integrity")
    }

    @Test
    fun lethalBonusesAreCappedByTheBuildCeiling() {
        val plain = buildTestState()
        val tuning = plain.content.obstacles
        val steadyLethal = plain.steadyCursorSpeed(tuning.lethalReach)
        assertEquals(steadyLethal, plain.obstacleLethalSpeed(), 0.01f, "no bonus, no floor on this viewport")
        val ceiling = plain.buildSteadyCursorSpeed(tuning.lethalCeilingReach)
        val room = ceiling - steadyLethal
        assertTrue(room > 150f, "the base build has room for a bonus: $room")
        fun lethalWith(bonus: Float, drag: Float = 1f) = buildTestState().apply {
            dragCoefficient *= drag
            matrix = matrix.copy(obstacleLethalSpeedBonus = bonus)
        }.obstacleLethalSpeed()

        assertEquals(steadyLethal + 150f, lethalWith(150f), 0.01f, "a bonus under the cap adds exactly")
        assertEquals(ceiling - 1f, lethalWith(room - 1f), 0.01f)
        assertEquals(ceiling, lethalWith(room), 0.01f, "the bonus that fills the room reaches the cap")
        assertEquals(ceiling, lethalWith(room + 1f), 0.01f, "one more stops at it")
        assertEquals(ceiling, lethalWith(10_000f), 0.01f)

        // Juggernaut's drag (×1.1) flies slower, so its cap is lower.
        val heavy = buildTestState().apply { dragCoefficient *= 1.1f }
        val heavyCeiling = heavy.buildSteadyCursorSpeed(tuning.lethalCeilingReach)
        assertTrue(heavyCeiling < ceiling)
        assertEquals(heavyCeiling, lethalWith(10_000f, drag = 1.1f), 0.01f)
        assertEquals(steadyLethal, lethalWith(0f, drag = 1.1f), 0.01f, "without a bonus the build does not matter")
    }

    @Test
    fun theDamageScaleOfASolidSurfaceScalesOnlyItsBandDamage() {
        fun strike(speed: Float, damageScale: Float): MutableGameState = buildTestState().apply {
            setVelocityForTesting(speed, 0f)
            resolveSolidImpact(SURFACE_ID, -1f, 0f, coreX, coreY, damageScale)
        }
        val probe = buildTestState()
        val speed = probe.bandSpeed(0.5f)
        val full = strike(speed, 1f)
        val half = strike(speed, 0.5f)
        assertEquals(full.lost() * 0.5f, half.lost(), 0.01f)
        assertEquals(full.velocityX, half.velocityX, "the bounce is the same")
        assertEquals(full.pendingRicochets, half.pendingRicochets)

        val lethal = strike(probe.obstacleLethalSpeed() + 50f, 0.1f)
        assertEquals(GamePhase.GAME_OVER, lethal.phase, "a scaled surface still kills at the lethal speed")
    }

    @Test
    fun aGlancingHitBurstsAnUnstableAsteroidAtItsImpactSpeed() {
        val probe = buildTestState()
        val start = probe.obstacleDamageStartSpeed()
        val diagonal = sin(PI.toFloat() / 4f)
        // Its normal speed stays under the damage start; normal plus scrape passes it.
        val speed = start * 1.25f
        assertTrue(speed * diagonal < start && 1.3f * speed * diagonal >= start)
        val glance = buildTestState()
        glance.ramRock(speed, degrees = 45f, kind = ObstacleKind.UNSTABLE)
        assertEquals(0, glance.obstacleField.size, "the glance burst it")

        val gentle = buildTestState()
        gentle.ramRock(start * 0.9f, degrees = 45f, kind = ObstacleKind.UNSTABLE)
        assertEquals(1, gentle.obstacleField.size, "below the damage start it is an ordinary rock")
    }

    private companion object {
        const val ROCK_X: Float = 300f
        const val ROCK_RADIUS: Float = 80f
        const val SECOND_ROCK_Y: Float = 600f
        const val SURFACE_ID: Long = 0x0F00_0000_0000_00FFL
    }
}
