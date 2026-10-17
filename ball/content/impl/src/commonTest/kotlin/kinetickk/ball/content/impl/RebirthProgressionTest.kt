// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RebirthProgressionTest {
    private val rebirth = createContentCatalog().gameplayContent().rebirth

    @Test
    fun tierZeroPreservesBaselineTuning() {
        val profile = rebirth.profile(0)

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
        assertEquals(emptyList(), profile.mutators)
        assertEquals(0, profile.packSizeBonus)
        assertEquals(1f, profile.obstacleDensityMultiplier)
        RebirthMutator.entries.forEach { assertFalse(profile.has(it), "tier 0 has $it") }

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
        val profiles = rebirth.profiles

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
            assertTrue(current.mutators.containsAll(previous.mutators))
            assertTrue(current.packSizeBonus >= previous.packSizeBonus)
            assertTrue(current.obstacleDensityMultiplier >= previous.obstacleDensityMultiplier)

            assertTrue(current.enemyCap(90) >= previous.enemyCap(90))
            assertTrue(current.spawnInterval(0.84f) <= previous.spawnInterval(0.84f))
            assertTrue(current.eliteInterval(48f) <= previous.eliteInterval(48f))
            assertTrue(current.enemyHealth(100f) >= previous.enemyHealth(100f))
        }
    }

    @Test
    fun eachTierActivatesTheNextMutatorCumulatively() {
        rebirth.profiles.forEach { profile ->
            val tier = profile.tier
            assertEquals(RebirthMutator.entries.take(tier), profile.mutators, "tier $tier")
            RebirthMutator.entries.forEachIndexed { index, mutator ->
                assertEquals(index < tier, profile.has(mutator), "tier $tier has $mutator")
            }
            assertEquals(if (tier >= 1) 1 else 0, profile.packSizeBonus, "tier $tier pack bonus")
            assertEquals(1f + tier * 0.05f, profile.obstacleDensityMultiplier, "tier $tier obstacle density")
        }
        assertEquals(RebirthMutator.entries.toList(), rebirth.profile(rebirth.maximumLevel).mutators)
    }

    @Test
    fun mutatorFieldsRejectDuplicatesNegativeBonusesAndInvalidDensity() {
        val base = rebirth.profile(0)
        val duplicated = immutableListOf(RebirthMutator.PACK_HUNTERS, RebirthMutator.PACK_HUNTERS)
        assertFailsWith<IllegalArgumentException> { base.copy(mutators = duplicated) }
        assertFailsWith<IllegalArgumentException> { base.copy(packSizeBonus = -1) }
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { density ->
            assertFailsWith<IllegalArgumentException>("density $density") { base.copy(obstacleDensityMultiplier = density) }
        }
        val accepted: RebirthProfile = base.copy(packSizeBonus = 0, obstacleDensityMultiplier = Float.MIN_VALUE)
        assertEquals(Float.MIN_VALUE, accepted.obstacleDensityMultiplier)
    }

    @Test
    fun profileInputsClampAndDerivedValuesRemainFiniteAndBounded() {
        assertEquals(rebirth.minimumLevel, rebirth.profile(Int.MIN_VALUE).tier)
        assertEquals(rebirth.maximumLevel, rebirth.profile(Int.MAX_VALUE).tier)

        (-2..rebirth.maximumLevel + 2).forEach { requestedLevel ->
            val profile = rebirth.profile(requestedLevel)
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
                profile.obstacleDensityMultiplier,
            )

            assertTrue(
                scalarValues.all(Float::isFinite),
                "Non-finite profile for requested level $requestedLevel: $profile",
            )
            assertTrue(profile.openingEnemyCount in 5..rebirth.maxActiveEnemies)
            assertTrue(profile.enemyCap(10_000) <= rebirth.maxActiveEnemies)
            assertTrue(profile.spawnInterval(0f) >= rebirth.minSpawnIntervalSeconds)
            assertTrue(profile.eliteInterval(0f) >= rebirth.minEliteIntervalSeconds)
            assertTrue(profile.enemyHealth(5_400f).isFinite())
            assertTrue(profile.enemyHealth(5_400f) > 0f)
        }
    }
}
