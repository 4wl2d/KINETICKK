// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteReward
import kinetickk.ball.gameplay.nucleus.model.SignalPrompt
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.sites.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The run's Relic budget and the Meld: four Relic opportunities a run, claimed by every Relic offer opened (a reroll
 * claims none) and every Heirloom taken; Elite-class drops on the unlock schedule; opt-in sources ahead of it while the
 * budget lasts, then Matter, a locked card or no Relic site at all; a stray offer past the budget salvages; and the
 * Meld seals the empty slot for a lasting damage and integrity bonus.
 */
class RelicBudgetTest {
    private val policy = canonicalGameplayContent.relicPolicy

    @Test
    fun theShippedBudgetIsFourOpportunitiesUnlockedAtThirtySecondsAndEvery165After() {
        assertEquals(4, policy.acquisitionsPerRun)
        assertEquals(
            listOf(0, 1, 1, 2, 3, 4, 4),
            listOf(29.9f, 30f, 194.9f, 195f, 360f, 525f, 720f).map(policy::unlockedOpportunities),
        )
    }

    @Test
    fun anOfferClaimsOneOpportunityAndRerollsRedrawItWithoutClaimingAnother() {
        val run = buildTestState(seed = 81)
        run.rerollsRemaining = 3
        run.openRelicChoiceForTesting()
        assertEquals(ChoiceType.RELIC, run.choiceType)
        assertEquals(1, run.relicRun.relicClaims)
        repeat(3) { run.rerollChoices() }
        assertEquals(0, run.rerollsRemaining)
        assertEquals(1, run.relicRun.relicClaims, "a reroll redraws the same opportunity")
        run.choose(0)
        assertEquals(1, run.relicRun.relicClaims)
        assertEquals(policy.acquisitionsPerRun - 1, run.relicBudgetLeft())
    }

    @Test
    fun aDuplicateRankUpSpendsAnOpportunityAndASpentBudgetDropsNoMoreRelics() {
        val run = buildTestState(seed = 82)
        run.elapsed = 600f
        // Three offers take the Gyre Engine (a bind, then two rank-ups of the duplicate), the fourth another Relic.
        listOf(RelicId.GYRE_ENGINE, RelicId.GYRE_ENGINE, RelicId.GYRE_ENGINE, RelicId.BLINK_DRIVE).forEach { id ->
            run.openRelicChoiceForTesting()
            run.choices = listOf(relicCard(id))
            run.choose(0)
        }
        assertEquals(3, run.relicRank(RelicId.GYRE_ENGINE))
        assertEquals(policy.acquisitionsPerRun, run.relicRun.relicClaims)
        assertEquals(0, run.relicBudgetLeft())
        assertTrue(run.buildProjection().relicBudgetSpent)

        run.killEnemyForTesting(EnemyType.ELITE, x = run.coreX + 400f)
        assertEquals(1, run.pickups.count { it.type == PickupType.KEY })
        assertEquals(0, run.pickups.count { it.type == PickupType.RELIC }, "the budget is spent")
    }

    @Test
    fun eliteKillsDropARelicOnlyForOpportunitiesTheScheduleUnlocked() {
        val run = buildTestState(seed = 83)
        fun dropsAt(seconds: Float): Boolean {
            run.elapsed = seconds
            val before = run.pickups.count { it.type == PickupType.RELIC }
            run.killEnemyForTesting(EnemyType.ELITE, x = run.coreX + 400f)
            return run.pickups.count { it.type == PickupType.RELIC } > before
        }
        assertFalse(dropsAt(29f), "nothing unlocks before 30 s")
        assertTrue(dropsAt(30f))
        assertFalse(dropsAt(100f), "the unlocked opportunity still lies on the field")
        // The run collects it and takes a card: one claimed, one unlocked.
        run.pickups.removeAll { it.type == PickupType.RELIC }
        run.openRelicChoiceForTesting()
        run.choose(0)
        assertFalse(dropsAt(194f))
        assertTrue(dropsAt(195f))
    }

    @Test
    fun aHeldBackEliteDropPaysItsSalvageMatterAndTheKeyStillDrops() {
        fun killAt(seconds: Float) = buildTestState(seed = 84).apply {
            elapsed = seconds
            killEnemyForTesting(EnemyType.ELITE, x = coreX + 400f)
        }
        val early = killAt(29f)
        val unlocked = killAt(30f)
        assertEquals(1, early.pickups.count { it.type == PickupType.KEY })
        assertEquals(0, early.pickups.count { it.type == PickupType.RELIC })
        assertEquals(1, unlocked.pickups.count { it.type == PickupType.RELIC })
        assertEquals(unlocked.matter() + policy.salvageMatter * early.matterGain, early.matter(), 0.01f)
    }

    @Test
    fun optInSourcesClaimAheadOfTheScheduleWhileTheBudgetLasts() {
        val run = buildTestState(seed = 85)
        run.elapsed = 10f
        run.grantSiteReward(SiteReward(relics = 1), run.coreX + 300f, run.coreY, scatterSeed = 1L)
        assertEquals(1, run.pickups.count { it.type == PickupType.RELIC }, "a drift cache's Relic")
        run.grantSiteReward(SiteReward(directed = DirectedReward.RELIC), run.coreX, run.coreY, scatterSeed = 2L)
        assertEquals(listOf(DirectedReward.RELIC), run.pendingDirectedRewards)
        assertEquals(2, run.relicOpportunitiesHeld())
        assertTrue(SiteKind.entries.filter { it.grantsDirectedRelic() }.all { run.windowEligible(it, 225f) })
        assertFalse(run.signalCard(SignalKind.SHRINE, SignalOption.SHRINE_RELIC).locked)
    }

    @Test
    fun aSpentBudgetPaysOptInSourcesInMatterLocksSignalsAndOffersNoRelicSite() {
        val run = spentBudget(seed = 86)
        val matter = run.matter()
        run.grantSiteReward(SiteReward(relics = 1), run.coreX + 300f, run.coreY, scatterSeed = 1L)
        run.grantSiteReward(SiteReward(directed = DirectedReward.RELIC), run.coreX, run.coreY, scatterSeed = 2L)
        assertEquals(0, run.pickups.count { it.type == PickupType.RELIC })
        assertTrue(run.pendingDirectedRewards.isEmpty())
        // The cache's Relic pays the Relic salvage, the refused directed reward the site salvage (8 Matter).
        assertEquals(matter + (policy.salvageMatter + 8) * run.matterGain, run.matter(), 0.01f)

        assertTrue(spentBudget(seed = 86).signalCard(SignalKind.SHRINE, SignalOption.SHRINE_RELIC).locked)
        assertTrue(spentBudget(seed = 86).signalCard(SignalKind.CURSED_CACHE, SignalOption.CACHE_IDOL).locked)
        assertFalse(spentBudget(seed = 86).signalCard(SignalKind.CURSED_CACHE, SignalOption.CACHE_CRATE).locked)

        val relicSites = SiteKind.entries.filter { it.grantsDirectedRelic() }
        assertTrue(relicSites.isNotEmpty())
        assertTrue(relicSites.none { run.windowEligible(it, 225f) })
        repeat(200) {
            val (first, second) = run.rollWindowKinds(225f)
            assertFalse(first.grantsDirectedRelic() || second.grantsDirectedRelic(), "no Relic site with the budget spent")
        }
    }

    @Test
    fun aStrayQueuedOfferPastTheBudgetSalvagesInsteadOfOpening() {
        val run = spentBudget(seed = 87)
        val matter = run.matter()
        run.openRelicChoiceForTesting()
        run.pendingDirectedRewards = listOf(DirectedReward.RELIC)
        run.openNextPendingChoice()
        assertEquals(GamePhase.RUNNING, run.phase)
        assertEquals(policy.acquisitionsPerRun, run.relicRun.relicClaims)
        assertTrue(run.pendingDirectedRewards.isEmpty())
        assertEquals(matter + 2 * policy.salvageMatter * run.matterGain, run.matter(), 0.01f)
    }

    @Test
    fun takingTheHeirloomClaimsAnOpportunityAndLeavingItClaimsNone() {
        val taken = heirloomRun()
        assertEquals(ChoiceType.RELIC, taken.choiceType)
        taken.choose(0)
        assertEquals(1, taken.relicRun.relicClaims)
        assertEquals(1, taken.equippedRelics.size)

        val left = heirloomRun()
        left.choose(left.choices.lastIndex)
        assertEquals(0, left.relicRun.relicClaims)
        assertTrue(left.equippedRelics.isEmpty())
    }

    @Test
    fun meldSealsTheEmptySlotMultipliesDamageAndRaisesAndHealsIntegrity() {
        val run = buildTestState(RelicId.GYRE_ENGINE, seed = 88)
        run.hp = run.maxHp * 0.5f
        val maxBefore = run.maxHp
        val hpBefore = run.hp
        run.openRelicChoiceForTesting()
        assertEquals(RelicChoiceAction.MELD, run.choices.last().relicAction)
        run.choose(run.choices.lastIndex)

        assertEquals(GamePhase.RUNNING, run.phase)
        assertEquals(SLOT_SEALED, run.message)
        assertEquals(1, run.relicRun.meldedSlots)
        assertEquals(policy.maxSlots - 2, run.openRelicSlots(), "one slot bound, one sealed")
        assertEquals(maxBefore * (1f + policy.meldIntegrity), run.maxHp, 0.01f)
        assertEquals(hpBefore + maxBefore * policy.meldIntegrity, run.hp, 0.01f, "the gain heals")
        assertEquals(run.matrix.damageMultiplier * (1f + policy.meldDamage), run.runDamageMultiplier(), 0.0001f)
        val build = run.buildProjection()
        assertEquals(1, build.meldedSlots)
        assertEquals(1, build.relicClaims)
    }

    @Test
    fun meldsMultiplyEveryDamageInstanceOnce() {
        val plain = buildTestState(seed = 89)
        val melded = buildTestState(seed = 89).apply { repeat(2) { meldRelicSlot() } }
        val multiplier = policy.meldDamageMultiplier(2)
        assertEquals((1f + policy.meldDamage) * (1f + policy.meldDamage), multiplier, 0.0001f)
        val hit = plain.dealHit(plain.dummy(), 100f, HitSource.WEAPON, canCrit = false).amount
        assertEquals(hit * multiplier, melded.dealHit(melded.dummy(), 100f, HitSource.WEAPON, canCrit = false).amount, hit * 0.0001f)
        val proc = plain.dealProcDamage(plain.dummy(x = 500f), 40f).amount
        assertEquals(proc * multiplier, melded.dealProcDamage(melded.dummy(x = 500f), 40f).amount, proc * 0.0001f)
    }

    private fun relicCard(id: RelicId) = ChoiceOption(ChoiceType.RELIC, "", "", "", relicId = id, relicAction = RelicChoiceAction.ACQUIRE)

    private fun MutableGameState.matter(): Float = runMatter + matterFraction

    /** A quiet run whose whole Relic budget is already claimed. */
    private fun spentBudget(seed: Int): MutableGameState =
        buildTestState(seed = seed).apply { relicRun.relicClaims = policy.acquisitionsPerRun }

    /** The [option] card of a freshly opened [kind] signal prompt. */
    private fun MutableGameState.signalCard(kind: SignalKind, option: SignalOption): ChoiceOption {
        pendingSignalPrompts = immutableListOf(SignalPrompt(kind, 0))
        openSignalPrompt()
        return choices.single { it.signalOption == option }
    }

    private fun SiteKind.grantsDirectedRelic(content: GameplayContentSnapshot = canonicalGameplayContent): Boolean =
        content.sites.definition(this).reward.directed == DirectedReward.RELIC

    /** A Rebirth 1 run opening with the Heirloom offer of three carried Relics. */
    private fun heirloomRun(): MutableGameState {
        val heirlooms = listOf(RelicId.GYRE_ENGINE, RelicId.GLASS_COMET, RelicId.ECHO_CHAMBER)
        val snapshot = GameplayProfileSnapshot(
            preferences = PlayerPreferences(),
            economy = PlayerEconomy(),
            loadout = PlayerLoadout(),
            matrix = MatrixProgress(),
            collection = PlayerCollection(heirloomRelicIds = heirlooms.toImmutableList()),
            rebirthProgress = RebirthProgress(level = 1),
        )
        return MutableGameState(canonicalGameplayContent, seed = 4_404, bootstrapProgress = snapshot).apply {
            startRun()
            enemies.clear()
            spawnClock = Float.MAX_VALUE
            update(MutableGameState.FIXED_STEP * 1.5f)
        }
    }
}
