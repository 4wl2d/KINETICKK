// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

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
        assertEquals("Baseline", profile.displayName)
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
    fun everyTierFollowsTheShippedDifficultyFormulas() {
        rebirth.profiles.forEach { profile ->
            val t = profile.tier.toFloat()
            val where = "tier ${profile.tier}"
            assertEquals(1f + 0.32f * t + 0.035f * t * t, profile.enemyHealthMultiplier, 0.0001f, where)
            assertEquals(1f + 0.14f * t + 0.008f * t * t, profile.incomingDamageMultiplier, 0.0001f, where)
            assertEquals(1f + 0.035f * t, profile.enemySpeedMultiplier, 0.0001f, where)
            assertEquals(1f + 0.09f * t, profile.spawnRateMultiplier, 0.0001f, where)
            assertEquals(1f + 0.10f * t, profile.enemyCapMultiplier, 0.0001f, where)
            assertEquals(1f + 0.07f * t, profile.eliteRateMultiplier, 0.0001f, where)
            assertEquals(12f * t, profile.threatTimeOffsetSeconds, 0.0001f, where)
            assertEquals(1f + 0.2f * t, profile.matterGainMultiplier, 0.0001f, where)
            assertEquals(1f + 0.03f * t, profile.playerPowerMultiplier, 0.0001f, where)
            assertEquals(0f, profile.playerIntegrityBonus, where)
            assertEquals(profile.tier / 4, profile.bonusRerolls, where)
            assertEquals(5 + profile.tier, profile.openingEnemyCount, where)
            assertEquals(1f + 0.05f * t, profile.obstacleDensityMultiplier, 0.0001f, where)
            assertEquals(profile.newestMutator?.displayName ?: "Baseline", profile.displayName, where)
        }
        // Tier 10 doubles the ordinary cap but the hard cap still holds it at 120.
        assertEquals(120, rebirth.profile(10).enemyCap(90))
        assertEquals(RebirthMutator.SINGULARITY, rebirth.profile(10).newestMutator)
        assertEquals(null, rebirth.profile(0).newestMutator)
        assertEquals(rebirth.profiles.size, rebirth.profiles.map { it.displayName }.toSet().size)
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
        assertFailsWith<IllegalArgumentException> { base.copy(displayName = " ") }
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

class RebirthRankUnlocksTest {
    private val ui = createContentCatalog().uiCatalog()

    private fun unlocks(rank: Int) = kinetickk.ball.content.api.rebirthRankUnlocks(
        rank, ui.weapons, ui.coreShapes, ui.relics, ui.items, ui.matrixNodes, ui.enemies,
    )

    @Test
    fun everyRankFromOneOpensContentAndEveryGatedPieceOpensAtExactlyOneRank() {
        assertTrue(unlocks(0).isEmpty)
        (1..10).forEach { rank -> assertFalse(unlocks(rank).isEmpty, "rank $rank opens nothing") }
        val all = (1..10).map(::unlocks)
        assertEquals(ui.weapons.count { it.minimumRebirth > 0 }, all.sumOf { it.weapons.size })
        assertEquals(ui.items.count { it.minimumRebirth > 0 }, all.sumOf { it.items.size })
        assertEquals(ui.relics.count { it.minimumRebirth > 0 }, all.sumOf { it.relics.size })
        assertEquals(ui.matrixNodes.count { it.minimumRebirth > 0 }, all.sumOf { it.matrixNodes.size })
        assertEquals(ui.enemies.count { it.minimumRebirth > 0 }, all.sumOf { it.enemies.size })
        // Spiral (rank 1), Pentagon (3), Crescent (5) and Hexagon (7) are the Reborn-rank characters.
        assertEquals(listOf(1, 3, 5, 7), (1..10).filter { rank -> unlocks(rank).characters.isNotEmpty() })
        assertEquals(listOf("Glacier Bell"), unlocks(1).weapons.map { it.name })
    }
}
