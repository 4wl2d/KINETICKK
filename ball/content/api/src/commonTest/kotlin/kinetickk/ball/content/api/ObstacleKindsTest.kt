// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObstacleKindsTest {
    @Test
    fun everyKindCarriesItsPropertiesAndTheShippedKindsKeepTodaysBehaviour() {
        // solid, phased, stopsShots, hurtsOnImpact, grazeable, echoes, breakable, field
        val expected = mapOf(
            ObstacleKind.ASTEROID to "SsHGE",
            ObstacleKind.BUMPER to "SE",
            ObstacleKind.UNSTABLE to "SsHGEB",
            ObstacleKind.GRAVITY_WELL to "SsHGEF",
            ObstacleKind.ACCEL_GATE to "F",
            ObstacleKind.CRYSTAL to "SsGEB",
            ObstacleKind.DERELICT_MINE to "BF",
            ObstacleKind.PHASE_SHARD to "SPsHG",
            ObstacleKind.THERMAL_VENT to "F",
        )
        assertEquals(ObstacleKind.entries.toSet(), expected.keys)
        expected.forEach { (kind, flags) -> assertEquals(flags, kind.flags(), kind.name) }
        // The three 0.5.0 kinds keep their ordinals (fingerprints mix them) and every one blocks and echoes.
        assertEquals(listOf(ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE), ObstacleKind.entries.take(3))
        assertTrue(ObstacleKind.BUMPER.solid && !ObstacleKind.BUMPER.stopsShots && !ObstacleKind.BUMPER.grazeable)
        assertTrue(ObstacleKind.entries.take(3).all { it.solid && it.echoes && !it.phased })
    }

    @Test
    fun aMixAcceptsSharesSummingToOneAndRejectsTheFirstValuePastEachBound() {
        assertEquals(1f, ObstacleMix(gravityWell = 0.5f, accelGate = 0.5f).total)
        ObstacleMix(crystal = 0.25f, derelictMine = 0.25f, phaseShard = 0.25f, thermalVent = 0.25f)
        assertFailsWith<IllegalArgumentException> { ObstacleMix(gravityWell = 0.5f, accelGate = 0.5001f) }
        assertFailsWith<IllegalArgumentException> { ObstacleMix(crystal = -0.01f) }
        assertFailsWith<IllegalArgumentException> { ObstacleMix(thermalVent = 1.01f) }
        assertFailsWith<IllegalArgumentException> { ObstacleMix(phaseShard = Float.NaN) }
        ObstacleMix(densityScale = 0.25f)
        ObstacleMix(densityScale = 2f)
        assertFailsWith<IllegalArgumentException> { ObstacleMix(densityScale = 0.249f) }
        assertFailsWith<IllegalArgumentException> { ObstacleMix(densityScale = 2.001f) }
        ObstacleMix(bumperScale = 0f)
        ObstacleMix(bumperScale = 2f)
        assertFailsWith<IllegalArgumentException> { ObstacleMix(bumperScale = -0.01f) }
        assertFailsWith<IllegalArgumentException> { ObstacleMix(bumperScale = 2.01f) }
        ObstacleMix(repelShare = 0f)
        ObstacleMix(repelShare = 1f)
        assertFailsWith<IllegalArgumentException> { ObstacleMix(repelShare = 1.01f) }
    }

    @Test
    fun theMaximumInfluenceCoversEveryReachWithinItsBound() {
        val tuning = ObstacleKindTuning()
        assertEquals(260f, tuning.largestReach)
        assertEquals(400f, ContentBounds.MAX_OBSTACLE_INFLUENCE)
        ObstacleKindTuning(maximumInfluence = ContentBounds.MAX_OBSTACLE_INFLUENCE)
        ObstacleKindTuning(maximumInfluence = tuning.largestReach)
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(maximumInfluence = 400.01f) }
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(maximumInfluence = 259.99f) }
        // A wider reach must raise the maximum influence with it.
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(mineBlastRadius = 330f) }
        ObstacleKindTuning(mineBlastRadius = 330f, maximumInfluence = 330f)
    }

    @Test
    fun kindMechanicsAcceptTheirBoundsAndRejectTheNextValue() {
        ObstacleKindTuning(maximumMineDetonationsPerStep = 8)
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(maximumMineDetonationsPerStep = 9) }
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(maximumMineDetonationsPerStep = 0) }
        ObstacleKindTuning(crystalShards = ContentBounds.MAX_SHARDS)
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(crystalShards = ContentBounds.MAX_SHARDS + 1) }
        ObstacleKindTuning(crystalData = 4)
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(crystalData = 5) }
        ObstacleKindTuning(grazeDistance = NEAR_OBSTACLE_MAX_DISTANCE)
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(grazeDistance = 320.01f) }
        ObstacleKindTuning(grazeCooldownSeconds = 0.1f)
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(grazeCooldownSeconds = 0.099f) }
        ObstacleKindTuning(mineBlastShare = 0.5f)
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(mineBlastShare = 0.501f) }
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(mineQuickFuseSeconds = 0.6f) }
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(boostLethalShare = 1f) }
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(phaseSolidShare = 1f) }
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(ventEruptSeconds = 4f) }
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(slingTurnRadians = 3.15f) }
        assertFailsWith<IllegalArgumentException> { ObstacleKindTuning(gatePostRadius = 35f) }
    }

    @Test
    fun obstacleTuningKeepsEveryKindBodyInsideTheAsteroidRange() {
        val kinds = ObstacleTuning().kinds
        assertEquals(ObstacleKindTuning(), kinds)
        ObstacleTuning(kinds = ObstacleKindTuning(wellCoreRadius = 34f))
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(kinds = ObstacleKindTuning(wellCoreRadius = 33f)) }
        ObstacleTuning(kinds = ObstacleKindTuning(gateMaximumRadius = 150f))
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(kinds = ObstacleKindTuning(gateMaximumRadius = 151f)) }
        ObstacleTuning(kinds = ObstacleKindTuning(crystalMaximumCandidate = 46f))
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(kinds = ObstacleKindTuning(crystalMaximumCandidate = 45f)) }
    }

    @Test
    fun theShippedMixesTurnTheirTableShareOfCandidatesIntoNewKinds() {
        val obstacles = ObstacleTuning()
        val kinds = obstacles.kinds
        // Asteroid candidates take a uniform radius between the smallest and the largest asteroid; integrating pick over
        // that radius and a uniform roll gives the share that becomes a new kind once eligibility has had its say.
        // Times the layer's non-bumper share, it is the share of all candidates (spec table: 29.5, 34.5, 46.4, 44.7 %).
        val layers = listOf(
            Triple("space", kinds.space, 0.359f to 0.295f),
            Triple("zenith", kinds.zenith, 0.451f to 0.345f),
            Triple("nadir", kinds.nadir, 0.520f to 0.464f),
            Triple("archive", kinds.archive, 0.522f to 0.447f),
        )
        for ((name, mix, expected) in layers) {
            val (ofAsteroids, ofAll) = expected
            val converted = convertedShare(mix, kinds, obstacles.asteroidMinimumRadius, obstacles.asteroidMaximumRadius)
            assertEquals(ofAsteroids, converted, 0.005f, "$name: share of asteroid candidates that become new kinds")
            val bumpers = obstacles.bumperShare * mix.bumperScale
            assertEquals(ofAll, converted * (1f - bumpers), 0.005f, "$name: share of all candidates")
            assertTrue(converted < mix.total, "$name: eligibility leaves some rolled candidates asteroids")
        }
        // Realspace stays inside the 25-35 % the obstacle decision allows, with today's density and bumpers.
        val space = convertedShare(kinds.space, kinds, obstacles.asteroidMinimumRadius, obstacles.asteroidMaximumRadius) *
            (1f - obstacles.bumperShare)
        assertTrue(space in 0.25f..0.35f, "realspace share $space")
        assertEquals(1f, kinds.space.densityScale)
        assertEquals(1f, kinds.space.bumperScale)
        // The layers read apart: no mines or vents in the Zenith, the most vents in the Nadir, the most shards in the Archive.
        assertEquals(0f, kinds.zenith.share(ObstacleKind.DERELICT_MINE) + kinds.zenith.share(ObstacleKind.THERMAL_VENT))
        val mixes = listOf(kinds.space, kinds.zenith, kinds.nadir, kinds.archive)
        assertEquals(kinds.nadir, mixes.maxBy { it.thermalVent })
        assertEquals(kinds.nadir, mixes.maxBy { it.derelictMine })
        assertEquals(kinds.archive, mixes.maxBy { it.phaseShard })
        assertTrue(kinds.zenith.repelShare > kinds.space.repelShare && kinds.nadir.repelShare < kinds.space.repelShare)
    }

    /** The share of rolls over a uniform candidate radius in [minimum, maximum] that [ObstacleMix.pick] makes a new kind. */
    private fun convertedShare(mix: ObstacleMix, kinds: ObstacleKindTuning, minimum: Float, maximum: Float): Float {
        val radii = 520
        val rolls = 1_000
        var converted = 0
        for (r in 0 until radii) {
            val radius = minimum + (maximum - minimum) * (r + 0.5f) / radii
            for (roll in 0 until rolls) if (mix.pick((roll + 0.5f) / rolls, radius, kinds) != ObstacleKind.ASTEROID) converted++
        }
        return converted.toFloat() / (radii * rolls)
    }

    @Test
    fun pickWalksTheKindOrderAndFallsBackToAnAsteroidWhenTheCandidateDoesNotFit() {
        val kinds = ObstacleKindTuning()
        val mix = ObstacleMix(gravityWell = 0.2f, crystal = 0.3f, thermalVent = 0.1f)
        assertEquals(ObstacleKind.GRAVITY_WELL, mix.pick(0.1f, 100f, kinds))
        assertEquals(ObstacleKind.ASTEROID, mix.pick(0.1f, 60f, kinds), "A knot needs a candidate of 70 or more")
        assertEquals(ObstacleKind.CRYSTAL, mix.pick(0.3f, 100f, kinds))
        assertEquals(ObstacleKind.ASTEROID, mix.pick(0.3f, 120f, kinds), "A crystal replaces candidates of 110 or less")
        assertEquals(ObstacleKind.THERMAL_VENT, mix.pick(0.55f, 100f, kinds))
        assertEquals(ObstacleKind.ASTEROID, mix.pick(0.6f, 100f, kinds))
        assertEquals(ObstacleKind.ASTEROID, mix.pick(0.99f, 100f, kinds))
        listOf(ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE).forEach {
            assertFalse(kinds.eligible(it, 100f))
            assertEquals(0f, mix.share(it))
        }
    }

    @Test
    fun aBodyNeverOutgrowsItsCandidate() {
        val kinds = ObstacleKindTuning()
        for (radius in listOf(46f, 70f, 110f, 130f, 150f)) {
            ObstacleKind.entries.forEach { kind ->
                assertTrue(kinds.bodyRadius(kind, radius) <= radius, "$kind at $radius")
            }
        }
        assertEquals(34f, kinds.bodyRadius(ObstacleKind.GRAVITY_WELL, 150f))
        assertEquals(130f, kinds.bodyRadius(ObstacleKind.ACCEL_GATE, 150f))
        assertEquals(64f, kinds.bodyRadius(ObstacleKind.DERELICT_MINE, 150f))
        assertEquals(110f, kinds.bodyRadius(ObstacleKind.THERMAL_VENT, 150f))
    }

    @Test
    fun phaseShardsAndVentsCycleAtTheirSharesDeterministicallyWithSpreadOffsets() {
        val kinds = ObstacleKindTuning()
        val steps = 32 * 120
        for (seed in listOf(1, 17, -40_000, Int.MAX_VALUE)) {
            var solid = 0
            var erupting = 0
            for (step in 0 until steps) {
                val elapsed = step / 120f
                val isSolid = kinds.phaseShardSolid(elapsed, seed)
                val isErupting = kinds.ventErupting(elapsed, seed)
                if (isSolid) solid++
                if (isErupting) erupting++
                assertEquals(isSolid, kinds.phaseShardSecondsToSolid(elapsed, seed) == 0f, "seed $seed at $elapsed")
                assertEquals(isErupting, kinds.ventSecondsToEruption(elapsed, seed) == 0f, "seed $seed at $elapsed")
                assertEquals(isSolid, kinds.phaseShardSolid(elapsed, seed), "same answer for the same inputs")
                if (isErupting) assertTrue(kinds.ventEruptionEnd(elapsed, seed) in elapsed..elapsed + kinds.ventEruptSeconds)
            }
            assertEquals(0.6f, solid.toFloat() / steps, 0.01f, "phase share of seed $seed")
            assertEquals(0.25f, erupting.toFloat() / steps, 0.01f, "vent duty of seed $seed")
        }
        val offsets = (0 until 64).map { obstacleSeedUnit(it, 61) }
        assertTrue(offsets.toSet().size > 32, "64 seeds spread their phase offsets")
        assertTrue(offsets.all { it >= 0f && it < 1f })
    }

    @Test
    fun aGateAngleRoundTripsThroughItsSeed() {
        val tau = (2.0 * PI).toFloat()
        for (angle in listOf(0f, 0.5f, 1.5707964f, 3f, 6.2f)) {
            val seed = (angle / tau * 65_536f).roundToInt()
            assertTrue(abs(gateAngle(seed) - angle) < tau / 65_536f, "angle $angle")
        }
        assertEquals(gateAngle(0x1234), gateAngle(0x7FFF_1234), "Only the low 16 bits pick the angle")
    }

    private fun ObstacleKind.flags(): String = buildString {
        if (solid) append('S')
        if (phased) append('P')
        if (stopsShots) append('s')
        if (hurtsOnImpact) append('H')
        if (grazeable) append('G')
        if (echoes) append('E')
        if (breakable) append('B')
        if (field) append('F')
    }
}
