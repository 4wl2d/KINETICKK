// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorldContentTest {
    @Test
    fun shippedObstacleTuningMatchesTheSpecifiedField() {
        val tuning = ObstacleTuning()
        assertEquals(1_200f, tuning.chunkSize)
        assertEquals(5, tuning.windowChunks)
        assertEquals(0.85f, tuning.meanPerChunk)
        assertEquals(3, tuning.maximumPerChunk)
        assertTrue(tuning.windowChunks * tuning.windowChunks * tuning.maximumPerChunk <= ContentBounds.MAX_OBSTACLES)
        assertEquals(128, ContentBounds.MAX_OBSTACLES)
        assertEquals(256, ContentBounds.MAX_DESTROYED_OBSTACLES)
        assertEquals(46f, tuning.asteroidMinimumRadius)
        assertEquals(150f, tuning.asteroidMaximumRadius)
        assertEquals(34f, tuning.bumperRadius)
        assertEquals(1_200f, tuning.safeRadius)
        assertEquals(300f, tuning.minimumGap)
        assertEquals(0.6f, tuning.restitution)
        // The 0.6.0 impact band: impacts count the scrape, start lower, keep a floor and cap the lethal bonuses.
        assertEquals(0.32f, tuning.damageStartReach)
        assertEquals(0.67f, tuning.lethalReach)
        assertEquals(0.30f, tuning.dashSafeReach)
        assertEquals(40f, tuning.forgivingStartBonus)
        assertEquals(0.55f, tuning.damageShare)
        assertEquals(1.0f, tuning.damageExponent)
        assertEquals(0.25f, tuning.hitCooldownSeconds)
        assertEquals(0.3f, tuning.scrapeShare)
        assertEquals(0.45f, tuning.ricochetReach)
        assertEquals(0.77f, tuning.lethalCeilingReach)
        assertEquals(0.06f, tuning.minimumDamageShare)
        assertEquals(0.4f, tuning.shieldBleed)
        assertEquals(0.35f, tuning.scrapeFriction)
        assertEquals(2_200f, tuning.minimapRange)
        assertEquals(1.4f, tuning.minimapViewportFactor)
        assertEquals(0.5f, tuning.blackoutMinimapScale)
    }

    @Test
    fun shippedObstacleKindsMatchTheSpecifiedMixesAndMechanics() {
        val tuning = ObstacleTuning()
        val specified = ObstacleKindTuning(
            space = ObstacleMix(gravityWell = 0.09f, accelGate = 0.09f, crystal = 0.11f, derelictMine = 0.05f, phaseShard = 0.06f, thermalVent = 0.05f),
            zenith = ObstacleMix(gravityWell = 0.06f, accelGate = 0.22f, crystal = 0.22f, phaseShard = 0.10f, repelShare = 0.7f, densityScale = 0.9f, bumperScale = 1.3f),
            nadir = ObstacleMix(
                gravityWell = 0.10f, accelGate = 0.04f, crystal = 0.04f, derelictMine = 0.14f, phaseShard = 0.04f, thermalVent = 0.24f,
                repelShare = 0.25f, densityScale = 1.15f, bumperScale = 0.6f,
            ),
            archive = ObstacleMix(gravityWell = 0.10f, accelGate = 0.08f, crystal = 0.08f, derelictMine = 0.06f, phaseShard = 0.24f, thermalVent = 0.04f, bumperScale = 0.8f),
            wellMinimumCandidate = 70f, gateMinimumCandidate = 70f, crystalMaximumCandidate = 110f, ventMinimumCandidate = 60f,
            wellCoreRadius = 34f, wellInfluence = 260f, wellCorePull = 900f, wellEnemyPull = 1_200f,
            slingTurnRadians = 1.5708f, slingMinimumSpeed = 600f, slingImpulse = 220f,
            gateMaximumRadius = 130f, gatePostRadius = 12f, gateImpulse = 380f, gateAlignment = 0.5f, gateCooldownSeconds = 0.8f,
            boostLethalShare = 0.9f,
            crystalSpeedRetention = 0.88f, crystalShards = 6, crystalShardDamage = 14f, crystalData = 2,
            mineClusterMaximumRadius = 64f, mineArmMargin = 110f, mineFuseSeconds = 0.6f, mineQuickFuseSeconds = 0.1f,
            mineBlastRadius = 160f, mineBlastShare = 0.10f, mineBlastEnemyDamage = 60f, maximumMineDetonationsPerStep = 4,
            phasePeriodSeconds = 3.2f, phaseSolidShare = 0.6f, phaseTelegraphSeconds = 0.45f,
            ventPeriodSeconds = 4f, ventEruptSeconds = 1f, ventTelegraphSeconds = 0.6f, ventMaximumRadius = 110f,
            ventPlumeMargin = 40f, ventHeat = 30f, ventImpulse = 160f, ventStatusInterval = 0.25f,
            grazeDistance = 40f, grazeMinimumSpeed = 900f, grazeCooldownSeconds = 0.5f,
            maximumInfluence = 320f,
        )
        assertEquals(specified, tuning.kinds)
        // Cross-checks with the field: every kind a layer names can take some asteroid candidate, and every body it can
        // take stays in [bumper, largest asteroid], so the gaps, the safe radius and the 128 window bound all still hold.
        val kinds = tuning.kinds
        val radii = (0..104).map { tuning.asteroidMinimumRadius + (tuning.asteroidMaximumRadius - tuning.asteroidMinimumRadius) * it / 104f }
        for ((layer, mix) in listOf("space" to kinds.space, "zenith" to kinds.zenith, "nadir" to kinds.nadir, "archive" to kinds.archive)) {
            ObstacleKind.entries.filter { mix.share(it) > 0f }.forEach { kind ->
                val eligible = radii.filter { kinds.eligible(kind, it) }
                assertTrue(eligible.isNotEmpty(), "$layer: $kind can take an asteroid candidate")
                eligible.forEach { radius ->
                    assertTrue(kinds.bodyRadius(kind, radius) in tuning.bumperRadius..radius, "$layer: $kind body at candidate $radius")
                }
            }
            assertTrue(tuning.bumperShare * mix.bumperScale <= 1f, "$layer: bumper share")
            assertTrue(tuning.meanPerChunk * mix.densityScale <= tuning.maximumPerChunk, "$layer: the mean fits the chunk's slots")
        }
        assertTrue(tuning.windowChunks * tuning.windowChunks * tuning.maximumPerChunk <= ContentBounds.MAX_OBSTACLES)
    }

    @Test
    fun obstacleTuningRejectsAWindowPastTheBoundAndInvertedReaches() {
        // 5 × 5 × 5 = 125 fits; a sixth slot per chunk (150) does not.
        assertEquals(5, ObstacleTuning(maximumPerChunk = 5).maximumPerChunk)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(maximumPerChunk = 6) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(windowChunks = 4) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(damageStartReach = 0.8f, lethalReach = 0.7f) }
        assertEquals(0.32f, ObstacleTuning(dashSafeReach = 0.32f).dashSafeReach)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(dashSafeReach = 0.33f) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(dashLethalMargin = -1f) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(restitution = 1.01f) }
        assertEquals(1f, ObstacleTuning(restitution = 1f).restitution)
        assertEquals(0.9f, ObstacleTuning().bumperLethalShare)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(bumperLethalShare = 1f) }
    }

    @Test
    fun obstacleImpactTuningRejectsSharesOutsideTheirBounds() {
        val shipped = ObstacleTuning()
        ObstacleTuning(scrapeShare = 0f)
        ObstacleTuning(scrapeShare = 1f)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(scrapeShare = 1.01f) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(scrapeShare = -0.01f) }
        ObstacleTuning(shieldBleed = 0f)
        ObstacleTuning(shieldBleed = 1f)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(shieldBleed = 1.01f) }
        ObstacleTuning(scrapeFriction = 0f)
        ObstacleTuning(scrapeFriction = 1f)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(scrapeFriction = -0.01f) }
        // The damage floor stays below the lethal share.
        ObstacleTuning(damageShare = 0.5f, minimumDamageShare = 0.49f)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(damageShare = 0.5f, minimumDamageShare = 0.5f) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(minimumDamageShare = -0.01f) }
        // A ricochet is always a hurting hit: its reach may equal the damage start, never lie below it.
        ObstacleTuning(ricochetReach = shipped.damageStartReach)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(ricochetReach = shipped.damageStartReach - 0.01f) }
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(ricochetReach = 1f) }
        // The lethal ceiling lies past the lethal reach and inside the viewport.
        ObstacleTuning(lethalCeilingReach = shipped.lethalReach + 0.01f)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(lethalCeilingReach = shipped.lethalReach) }
        ObstacleTuning(lethalCeilingReach = 0.99f)
        assertFailsWith<IllegalArgumentException> { ObstacleTuning(lethalCeilingReach = 1f) }
    }

    @Test
    fun pickupTuningShipsTheWiderMagnetAndRejectsAVacuumSlowerThanTheCore() {
        val tuning = PickupTuning()
        assertEquals(320f, tuning.magnetRadius)
        assertEquals(56f, tuning.collectionRadius)
        assertEquals(950f, tuning.vacuumSpeed)
        assertEquals(1.3f, tuning.vacuumCoreSpeedFactor)
        assertEquals(120f, tuning.minimumMagnetRadius)
        assertFailsWith<IllegalArgumentException> { PickupTuning(vacuumCoreSpeedFactor = 0.99f) }
        assertFailsWith<IllegalArgumentException> { PickupTuning(collectionRadius = 320f) }
        assertFailsWith<IllegalArgumentException> { PickupTuning(minimumMagnetRadius = 330f) }
    }
}
