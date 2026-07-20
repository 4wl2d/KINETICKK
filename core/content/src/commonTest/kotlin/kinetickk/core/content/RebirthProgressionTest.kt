// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.content

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class RebirthProgressionTest {
    @Test
    fun tierZeroPreservesBaselineTuning() {
        val profile = RebirthProgression.profile(0)

        assertEquals(0, profile.tier)
        assertEquals(RebirthDirective.BASELINE, profile.directive)
        assertEquals(5, profile.openingEnemyCount)
        assertEquals(1f, profile.enemyCapMultiplier)
        assertEquals(1f, profile.spawnRateMultiplier)
        assertEquals(1f, profile.enemyHealthMultiplier)
        assertEquals(1f, profile.enemySpeedMultiplier)
        assertEquals(1f, profile.incomingDamageMultiplier)
        assertEquals(1f, profile.eliteRateMultiplier)
        assertEquals(0f, profile.threatTimeOffsetSeconds)
        assertEquals(1f, profile.playerPowerMultiplier)
        assertEquals(0f, profile.playerIntegrityBonus)
        assertEquals(1f, profile.matterGainMultiplier)
        assertEquals(0, profile.bonusRerolls)

        assertEquals(14, profile.enemyCap(14))
        assertEquals(90, profile.enemyCap(90))
        assertEquals(0.84f, profile.spawnInterval(0.84f))
        assertEquals(0.13f, profile.spawnInterval(0.13f))
        assertEquals(38f, profile.eliteInterval(38f))
        assertEquals(48f, profile.eliteInterval(48f))
        assertEquals(30f, profile.enemyHealth(30f))
        assertEquals(5_400f, profile.enemyHealth(5_400f))
    }

    @Test
    fun profilesProgressMonotonicallyFromZeroThroughMaxLevel() {
        val profiles = (0..RebirthProgression.MAX_LEVEL).map(RebirthProgression::profile)

        profiles.zipWithNext().forEach { (previous, current) ->
            assertEquals(previous.tier + 1, current.tier)
            assertTrue(current.openingEnemyCount >= previous.openingEnemyCount)
            assertTrue(current.enemyCapMultiplier >= previous.enemyCapMultiplier)
            assertTrue(current.spawnRateMultiplier >= previous.spawnRateMultiplier)
            assertTrue(current.enemyHealthMultiplier >= previous.enemyHealthMultiplier)
            assertTrue(current.enemySpeedMultiplier >= previous.enemySpeedMultiplier)
            assertTrue(current.incomingDamageMultiplier >= previous.incomingDamageMultiplier)
            assertTrue(current.eliteRateMultiplier >= previous.eliteRateMultiplier)
            assertTrue(current.threatTimeOffsetSeconds >= previous.threatTimeOffsetSeconds)
            assertTrue(current.playerPowerMultiplier >= previous.playerPowerMultiplier)
            assertTrue(current.playerIntegrityBonus >= previous.playerIntegrityBonus)
            assertTrue(current.matterGainMultiplier >= previous.matterGainMultiplier)
            assertTrue(current.bonusRerolls >= previous.bonusRerolls)

            assertTrue(current.enemyCap(90) >= previous.enemyCap(90))
            assertTrue(current.spawnInterval(0.84f) <= previous.spawnInterval(0.84f))
            assertTrue(current.eliteInterval(48f) <= previous.eliteInterval(48f))
            assertTrue(current.enemyHealth(100f) >= previous.enemyHealth(100f))
        }
    }

    @Test
    fun profileInputsClampAndDerivedValuesRemainFiniteAndBounded() {
        assertEquals(0, RebirthProgression.profile(Int.MIN_VALUE).tier)
        assertEquals(RebirthProgression.MAX_LEVEL, RebirthProgression.profile(Int.MAX_VALUE).tier)

        (-2..RebirthProgression.MAX_LEVEL + 2).forEach { requestedLevel ->
            val profile = RebirthProgression.profile(requestedLevel)
            val scalarValues = listOf(
                profile.enemyCapMultiplier,
                profile.spawnRateMultiplier,
                profile.enemyHealthMultiplier,
                profile.enemySpeedMultiplier,
                profile.incomingDamageMultiplier,
                profile.eliteRateMultiplier,
                profile.threatTimeOffsetSeconds,
                profile.playerPowerMultiplier,
                profile.playerIntegrityBonus,
                profile.matterGainMultiplier,
            )

            assertTrue(scalarValues.all(Float::isFinite), "Non-finite profile for requested level $requestedLevel: $profile")
            assertTrue(profile.openingEnemyCount in 5..RebirthProgression.MAX_ACTIVE_ENEMIES)
            assertTrue(profile.enemyCap(10_000) <= RebirthProgression.MAX_ACTIVE_ENEMIES)
            assertTrue(profile.spawnInterval(0f) >= RebirthProgression.MIN_SPAWN_INTERVAL_SECONDS)
            assertTrue(profile.eliteInterval(0f) >= RebirthProgression.MIN_ELITE_INTERVAL_SECONDS)
            assertTrue(profile.enemyHealth(5_400f).isFinite())
            assertTrue(profile.enemyHealth(5_400f) > 0f)
        }
    }
}
