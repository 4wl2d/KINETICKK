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
    fun matrixWeaponPowerDependsOnRankAndNeverUnlockedWeaponCount() {
        fun power(allWeapons: Boolean): Float = MutableGameState(canonicalGameplayContent, seed = 11).apply {
            matrixRanks[MatrixNodeId.WEAPON_CALIBRATION.ordinal] = 3
            if (allWeapons) unlockedWeaponSet.addAll(WeaponId.entries)
            startRun()
        }.weaponPower
        assertEquals(1.36f, power(false), 0.0001f)
        assertEquals(power(false), power(true))
    }

    @Test
    fun coherentBuildReadIsDetachedAndSourceContributionsSumToEffectiveStats() {
        val game = MutableGameState(canonicalGameplayContent, seed = 13).apply {
            // Every node at its maximum: each stat row then splits into character, Matrix, items and relics.
            for (index in 0 until matrixRanks.size) matrixRanks[index] = content.matrixNodes[index].maxRanks
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
    fun fullySuppressedAndMaxedItemsAreExcludedWhilePartlySuppressedItemsStillHelp() {
        val game = MutableGameState(canonicalGameplayContent, seed = 15).apply { startRun() }
        val shieldCell = game.content.items.single { it.name == "Shield Cell" }
        val naniteMesh = game.content.items.single { it.name == "Nanite Mesh" }
        assertTrue(game.hasUsefulItemEffect(shieldCell))

        // Glass Comet removes shields: every effect of Shield Cell is disabled, so it is never useful.
        game.acquireRelic(RelicId.GLASS_COMET)
        assertEquals(1f, game.itemSuppressedShare(shieldCell))
        assertFalse(game.hasUsefulItemEffect(shieldCell))
        assertFalse(shieldCell in game.offerableItems())

        // Verdant Rot disables regeneration but not integrity: half the item still helps.
        game.acquireRelic(RelicId.VERDANT_ROT)
        assertEquals(0.5f, game.itemSuppressedShare(naniteMesh))
        assertTrue(game.hasUsefulItemEffect(naniteMesh))
        game.itemStacks[naniteMesh.id] = naniteMesh.maxStacks
        assertFalse(game.hasUsefulItemEffect(naniteMesh))

        // Owned, fully disabled items are reported to the build read.
        game.acquireItem(shieldCell.id)
        val summary = game.buildSummary(GameplayInstanceId(RunId(1)), GameplayRevision(1))
        assertEquals(listOf(shieldCell.id), summary.suppressedItemIds.toList())
        assertEquals(listOf(shieldCell.id), game.buildProjection().suppressedItemIds.toList())
    }

    @Test
    fun buildReadCarriesTagsAffinityLinksReachableReactionsAndRebornItems() {
        val game = MutableGameState(canonicalGameplayContent, seed = 16).apply { startRun() }
        val igniter = game.content.items.single { it.name == "Igniter" }
        val rime = game.content.items.single { it.name == "Rime Rounds" }
        game.acquireItem(igniter.id, reborn = true)
        game.acquireItem(rime.id)
        game.acquireRelic(RelicId.FURNACE_COVENANT)
        game.acquireRelic(RelicId.OVERDRIVE_CASCADE)
        val summary = game.buildSummary(GameplayInstanceId(RunId(1)), GameplayRevision(2))

        // A Reborn item counts double; each Thermal-aspect relic adds one; ORB leans toward Drive.
        assertEquals(2, summary.tags.single { it.tag == BuildTag.OVERCLOCK }.points)
        assertEquals(1, summary.tags.single { it.tag == BuildTag.DRIVE }.points)
        val thermal = summary.elements.single { it.element == StatusElement.THERMAL }
        assertEquals(4, thermal.affinity)
        assertEquals(2, thermal.tier)
        assertTrue(thermal.dominant)
        assertEquals(listOf(ReactionId.THERMAL_SHOCK), summary.reachableReactions.toList())
        assertEquals(listOf(igniter.id), summary.rebornItemIds.toList())
        val link = summary.relicLinks.single()
        assertEquals(BuildRelicLinkKind.CONCORD, link.kind)
        assertEquals(RelicPairingId.MELTDOWN, link.pairing)
        assertTrue(link.discovered)
        assertTrue(summary.severedRelicIds.isEmpty())

        game.replaceRelic(0, RelicId.GYRE_ENGINE)
        val replaced = game.buildSummary(GameplayInstanceId(RunId(1)), GameplayRevision(3))
        assertEquals(listOf(RelicId.FURNACE_COVENANT), replaced.severedRelicIds.toList())
        assertTrue(replaced.relicLinks.isEmpty())
    }

    @Test
    fun directedItemsActuallyOfferedAreAvailableAboveOrdinaryUnlockLevelWhileCapsStillApply() {
        val content = canonicalGameplayContent.copy(items = canonicalGameplayContent.items.take(3).toImmutableList())
        val game = MutableGameState(content, seed = 18).apply { startRun() }
        val instance = GameplayInstanceId(RunId(1))
        val revision = GameplayRevision(8)
        val aboveLevel = content.items.first { it.unlockLevel > game.level && itemRewardFocus(it) == RewardFocus.OFFENSE }
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
        val item = game.content.items.first { item -> item.stats.any { it.effect == ItemEffect.WEAPON_POWER && it.amount > 0f } }
        val before = game.captureBuildChange()
        val powerBefore = game.effectiveWeaponPower()
        game.acquireItem(item.id)
        val delta = game.effectiveWeaponPower() - powerBefore
        game.emitBuildChange(before, "Item acquired")
        val notice = game.takeVisualFxCues().filterIsInstance<VisualFxCue.BuildChanged>().single()
        assertTrue(notice.details.contains("Weapon power +${(delta * 100f).toInt() / 100f}×"))
    }
}
