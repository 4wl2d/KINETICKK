// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.test.*

class BuildSummaryTest {
    @Test
    fun armoryPowerDependsOnRankAndNeverUnlockedWeaponCount() {
        fun power(allWeapons: Boolean): Float = MutableGameState(canonicalGameplayContent, seed = 11).apply {
            metaRanks[MetaUpgradeId.ARMORY_LICENSE.ordinal] = 5
            if (allWeapons) unlockedWeaponSet.addAll(WeaponId.entries)
            startRun()
        }.weaponPower
        assertEquals(1.2f, power(false))
        assertEquals(power(false), power(true))
    }

    @Test
    fun coherentBuildReadIsDetachedAndSourceContributionsSumToEffectiveStats() {
        val game = MutableGameState(canonicalGameplayContent, seed = 13).apply {
            metaRanks[MetaUpgradeId.ARMORY_LICENSE.ordinal] = 4
            metaRanks[MetaUpgradeId.DASH_CAPACITOR.ordinal] = 2
            startRun()
            acquireItem(0)
            acquireItem(1)
            acquireItem(2)
            weaponLevel = 7
            overdriveTime = 2f
        }
        val instance = GameplayInstanceId(RunId(1))
        val revision = GameplayRevision(8)
        val before = game.buildSummary(instance, revision)
        assertEquals(before, game.buildSummary(instance, revision))
        before.stats.forEach { stat ->
            assertTrue(abs(stat.value - stat.contributions.sumOf { it.amount.toDouble() }.toFloat()) < 0.0002f, stat.name)
        }
        assertEquals(game.effectiveWeaponPower(), before.stats.first { it.name == "Weapon power" }.value)
        val retainedStack = before.itemStacks[0]
        game.acquireItem(0)
        assertEquals(retainedStack, before.itemStacks[0])
        assertEquals(retainedStack + 1, game.buildSummary(instance, revision).itemStacks[0])
    }

    @Test
    fun cappedModifiersAreExcludedUnlessAnotherModifierOrFamilyProcStillHelps() {
        val game = MutableGameState(canonicalGameplayContent, seed = 15).apply { startRun() }
        val item = ItemDefinition(0, "Capped", "Capped test", ItemRarity.COMMON,
            ItemModifier(ItemEffect.CRIT_CHANCE, 0.1f), ItemModifier(ItemEffect.DAMAGE_REDUCTION, 0.1f),
            8, 1, "Test")
        game.critChance = 0.75f
        game.damageReduction = 0.65f
        assertFalse(game.hasUsefulItemEffect(item))
        game.damageReduction = 0.64f
        assertTrue(game.hasUsefulItemEffect(item))
        game.damageReduction = 0.65f
        game.familyStacks[0] = 2
        assertTrue(game.hasUsefulItemEffect(item))
        game.itemStacks[0] = 8
        assertFalse(game.hasUsefulItemEffect(item))
    }

    @Test
    fun directedItemsActuallyOfferedAreAvailableAboveOrdinaryUnlockLevelWhileCapsStillApply() {
        val content = canonicalGameplayContent.copy(items = canonicalGameplayContent.items.take(3).toImmutableList())
        val game = MutableGameState(content, seed = 18).apply { startRun() }
        val instance = GameplayInstanceId(RunId(1))
        val revision = GameplayRevision(8)
        val aboveLevel = content.items.first { it.unlockLevel > game.level }
        val ordinary = game.buildSummary(instance, revision)
        assertTrue(0 in ordinary.eligibleItemIds)
        assertFalse(aboveLevel.id in ordinary.eligibleItemIds)

        game.openDirectedReward(DirectedReward.ITEM_AND_REPAIR)
        game.choose(game.choices.indexOfFirst { it.rewardFocus == RewardFocus.OFFENSE })
        assertTrue(game.choices.any { it.itemId == aboveLevel.id })
        val retainedChoices = game.choices
        val directed = game.buildSummary(instance, revision)
        assertTrue(aboveLevel.id in directed.eligibleItemIds)
        assertTrue(0 in directed.eligibleItemIds)
        assertEquals(directed, game.buildSummary(instance, revision))
        assertEquals(retainedChoices, game.choices)
        assertFalse(aboveLevel.id in ordinary.eligibleItemIds)

        game.itemStacks[aboveLevel.id] = aboveLevel.maxStacks
        assertFalse(aboveLevel.id in game.buildSummary(instance, revision).eligibleItemIds)
    }

    @Test
    fun noticesReportEffectiveAcquisitionDeltaWhileCombatUpdatesStayQuiet() {
        val game = MutableGameState(canonicalGameplayContent, seed = 14).apply { startRun(); takeVisualFxCues() }
        game.overdriveTime = 5f
        game.update(1f / 60f)
        assertTrue(game.takeVisualFxCues().filterIsInstance<VisualFxCue.BuildChanged>().isEmpty())
        val item = game.content.items.first { it.primary.effect == ItemEffect.WEAPON_POWER }
        val before = game.captureBuildChange()
        val powerBefore = game.effectiveWeaponPower()
        game.acquireItem(item.id)
        val delta = game.effectiveWeaponPower() - powerBefore
        game.emitBuildChange(before, "Item acquired")
        val notice = game.takeVisualFxCues().filterIsInstance<VisualFxCue.BuildChanged>().single()
        assertTrue(notice.details.contains("Weapon power +${(delta * 100f).toInt() / 100f}×"))
    }
}
