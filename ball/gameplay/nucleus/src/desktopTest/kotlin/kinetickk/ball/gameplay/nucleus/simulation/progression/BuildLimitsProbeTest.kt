// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The build-limit probe (a calibration window, by default the combined confirmation window i = 2,501..2,800; never the
 * gate population). Two parts:
 * - BUDGET flies the reference bot (NoPoiBot) and the opt-in pilots and reads, per arm, the Relic opportunities claimed
 *   per run (never above the budget), their distribution, the run second of each claim, the Meld rate, rank-ups and the
 *   weapon changes. Arms: the fresh tier-0 Orb and the full-Matrix tier-6 Orb without POIs, the site pilot playing every
 *   window (SiteArm.MIXED, opt-in Relic sites and caches) and the dungeon campaign (Totem chests, guardians; odd seeds
 *   start on the Flux Wake, off the bot's order, so its first chest changes weapon).
 * - VALUE compares Relic policies on the same seeds and profile (balanceSweep's fresh tier 2, half tier 7 and full tier 10): the reference bot's
 *   own picks; every offer melded; every offer a random Relic card; and, all claimed at the run start, four Melds, four
 *   random Relics, the strong build's four Relics (balanceSweep's strong arm) and four Relics that fit the Arc Coil all
 *   the probed profiles fly ([FitRelics]). It prints win share, median survival
 *   and median clear time per arm, with paired bootstrap standard errors of each arm's difference to the all-Meld arm
 *   (policy.md: 20,000 resamples, RNG seed 20261027). Target (D3): melding beats random off-build Relics and loses to the
 *   strong build, so a Meld never outweighs a strong Relic.
 *
 * `KK_LIMITS_PROBE=2501..2800` names the windows (comma-separated); `KK_LIMITS_PARTS=BUDGET,VALUE` picks the parts;
 * `KK_LIMITS_ARMS=MELD_START,STRONG_START` the VALUE arms (the all-Meld arm always flies, the differences read against it);
 * `KK_LIMITS_KNOBS=damage=0.12,integrity=0.10` sets the Meld bonus (`;` separates sets flown one after another). `--rerun`
 * makes Gradle run it again with new variables; the lines land in the suite's JUnit XML.
 */
class BuildLimitsProbeTest {
    @Test
    fun probeTheRelicBudgetTheMeldAndTheWeaponChange() {
        val text = System.getenv("KK_LIMITS_PROBE")?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val seeds = text.split(',').flatMap { windowSeeds(probeWindow(it.trim())) }
        val parts = System.getenv("KK_LIMITS_PARTS")?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }
            ?: listOf("BUDGET", "VALUE")
        val knobSets = System.getenv("KK_LIMITS_KNOBS")?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("")
        val valueArms = System.getenv("KK_LIMITS_ARMS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.map { ValueArm.valueOf(it.uppercase()) }?.let { it + ValueArm.MELD }?.distinct() ?: ValueArm.entries
        for (knobs in knobSets) {
            val content = meldContent(knobs)
            val label = "damage=${content.relicPolicy.meldDamage} integrity=${content.relicPolicy.meldIntegrity}"
            if ("BUDGET" in parts) BudgetArm.entries.forEach { arm -> reportBudget("$label window=$text", arm, populationOf(seeds) { flyBudget(it, arm, content) }) }
            if ("VALUE" in parts) for (tier in ValueTier.entries) {
                val runs = valueArms.associateWith { arm -> populationOf(seeds) { flyValue(it, tier, arm, content) } }
                reportValue("$label window=$text tier=$tier", runs)
            }
        }
    }

    private enum class BudgetArm { REFERENCE_T0, REFERENCE_T6_FULL, SITES_T0, CAMPAIGN_T0 }

    private enum class ValueTier(val arm: SweepArm, val tier: Int) { FRESH_T2(SweepArm.FRESH, 2), HALF_T7(SweepArm.HALF, 7), FULL_T10(SweepArm.FULL, 10) }

    private enum class ValueArm { REFERENCE, MELD, RANDOM, MELD_START, RANDOM_START, STRONG_START, FIT_START }

    /**
     * One run's limits: the second of each claim, Melds, bound Relics and their ranks, weapon changes, its end, and the
     * pace marks: the second of the first Epic item taken and of the weapon change (null: none).
     */
    private class LimitRun(
        val seed: Int,
        val won: Boolean,
        val seconds: Float,
        val claimTimes: List<Float>,
        val melds: Int,
        val bound: Int,
        val ranks: Int,
        val weaponChanges: Int,
        val firstEpicAt: Float?,
        val weaponChangeAt: Float?,
    )

    /** Records the second of every Relic claim (a claim never comes back, so a rising count is a claim) and the pace marks. */
    private class ClaimClock {
        val times = ArrayList<Float>()
        private var items = 0
        private var firstEpicAt: Float? = null
        private var weaponChangeAt: Float? = null
        fun observe(s: MutableGameState) {
            while (times.size < s.relicRun.relicClaims) times += s.elapsed
            if (s.acquiredItemCount != items) {
                items = s.acquiredItemCount
                if (firstEpicAt == null && s.recentItem?.rarity == ItemRarity.EPIC) firstEpicAt = s.elapsed
            }
            if (weaponChangeAt == null && s.offerRun.weaponChanges > 0) weaponChangeAt = s.elapsed
        }

        fun run(seed: Int, s: MutableGameState) = LimitRun(
            seed, s.phase == GamePhase.VICTORY, s.elapsed, times.toList(), s.relicRun.meldedSlots, s.equippedRelics.size,
            s.equippedRelics.sumOf { it.rank }, s.offerRun.weaponChanges, firstEpicAt, weaponChangeAt,
        )
    }

    private fun flyBudget(seed: Int, arm: BudgetArm, content: GameplayContentSnapshot): LimitRun {
        val clock = ClaimClock()
        var last: MutableGameState? = null
        when (arm) {
            BudgetArm.REFERENCE_T0, BudgetArm.REFERENCE_T6_FULL -> {
                val profile = if (arm == BudgetArm.REFERENCE_T0) gateProfile(GateMatrix.NONE, 0, content = content)
                else gateProfile(GateMatrix.FULL, 6, content = content)
                last = playGateRun(seed, profile, NoPoiBot(), content = content, observe = clock::observe)
            }
            BudgetArm.SITES_T0 -> flySiteRun(seed, SiteArm.MIXED, SiteRunSetup(base = content)) { s -> clock.observe(s); last = s }
            BudgetArm.CAMPAIGN_T0 -> {
                val weapon = if (seed % 2 == 0) WeaponId.ARC_COIL else WeaponId.FLUX_WAKE
                val state = startGateRun(seed, gateProfile(GateMatrix.NONE, 0, weapon = weapon, content = content), content = content)
                val campaign = DungeonCampaignPilot(NoPoiBot(), content.structures.signalRange)
                while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                    if (state.phase == GamePhase.CHOICE) campaign.choose(state) else {
                        campaign.control(state)
                        state.update(0.02f)
                    }
                    campaign.observe(state)
                    clock.observe(state)
                    state.takeSoundCues()
                    state.takeVisualFxCues()
                }
                last = state
            }
        }
        return clock.run(seed, checkNotNull(last))
    }

    private fun flyValue(seed: Int, tier: ValueTier, arm: ValueArm, content: GameplayContentSnapshot): LimitRun {
        val profile = tier.arm.profile(tier.tier, content)
        val strong = when (arm) {
            ValueArm.STRONG_START -> SweepStrongRelics
            ValueArm.FIT_START -> FitRelics
            else -> emptyList()
        }
        val state = startGateRun(seed, profile, strong, content)
        val picks = Random(seed * 31 + arm.ordinal)
        when (arm) {
            ValueArm.MELD_START -> repeat(content.relicPolicy.acquisitionsPerRun) {
                state.relicRun.relicClaims++
                state.meldRelicSlot()
            }
            ValueArm.RANDOM_START -> state.offerableRelics().shuffled(picks).take(content.relicPolicy.acquisitionsPerRun)
                .forEach { state.claimRelicForTesting(it.id) }
            else -> Unit
        }
        val clock = ClaimClock()
        val bot = NoPoiBot()
        while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) {
                val forced = relicPolicyPick(state, arm, picks)
                if (forced != null) state.choose(forced) else bot.choose(state)
            } else {
                bot.control(state)
                state.update(0.02f)
            }
            clock.observe(state)
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        return clock.run(seed, state)
    }

    /** The card the arm's Relic policy takes on an open Relic offer (never the Heirloom or a direction), or null. */
    private fun relicPolicyPick(s: MutableGameState, arm: ValueArm, picks: Random): Int? {
        if (s.choiceType != ChoiceType.RELIC || s.choices.any { it.heirloom || it.rewardFocus != null }) return null
        return when (arm) {
            ValueArm.MELD -> s.choices.indexOfFirst { it.relicAction == RelicChoiceAction.MELD }.takeIf { it >= 0 }
            ValueArm.RANDOM -> s.choices.indices.filter { s.choices[it].relicAction == RelicChoiceAction.ACQUIRE }
                .takeIf { it.isNotEmpty() }?.let { it[picks.nextInt(it.size)] }
            else -> null
        }
    }

    private fun reportBudget(label: String, arm: BudgetArm, runs: List<LimitRun>) {
        val budget = canonicalGameplayContent.relicPolicy.acquisitionsPerRun
        val claims = runs.map { it.claimTimes.size }
        assertTrue(claims.all { it <= budget }, "$arm: a run claimed more than the budget: ${claims.max()}")
        val distribution = (0..budget).joinToString(" ") { k -> "$k:${claims.count { it == k }}" }
        val times = (0 until budget).joinToString(" ") { k ->
            val at = runs.mapNotNull { it.claimTimes.getOrNull(k) }.sorted()
            if (at.isEmpty()) "#${k + 1}=none" else "#${k + 1}=n${at.size}/p10 ${at.quantile(0.1)}/p50 ${at.quantile(0.5)}/p90 ${at.quantile(0.9)}"
        }
        val claimed = claims.sum().coerceAtLeast(1)
        val melds = runs.sumOf { it.melds }
        val rankUps = runs.sumOf { it.ranks - it.bound }
        val changes = (0..2).joinToString(" ") { k -> "$k:${runs.count { it.weaponChanges == k }}" }
        fun pace(marks: List<Float>) = if (marks.isEmpty()) "none" else
            "n${marks.size}/p10 ${marks.sorted().quantile(0.1)}/p50 ${marks.sorted().quantile(0.5)}/p90 ${marks.sorted().quantile(0.9)}"
        println("LIMITS BUDGET $label arm=$arm runs=${runs.size} win=${"%.3f".format(runs.shareOf { it.won })} " +
            "claims[$distribution] mean=${"%.2f".format(claims.average())} times[$times] " +
            "meldRate=${"%.3f".format(melds.toDouble() / claimed)} rankUpRate=${"%.3f".format(rankUps.toDouble() / claimed)} " +
            "meldRuns=${runs.count { it.melds > 0 }} weaponChanges[$changes] changeAt[${pace(runs.mapNotNull { it.weaponChangeAt })}] " +
            "firstEpicAt[${pace(runs.mapNotNull { it.firstEpicAt })}]")
    }

    private fun reportValue(label: String, runs: Map<ValueArm, List<LimitRun>>) {
        val meld = runs.getValue(ValueArm.MELD)
        runs.forEach { (arm, armRuns) ->
            val paired = meld.zip(armRuns)
            println("LIMITS VALUE $label arm=$arm win=${"%.3f".format(armRuns.shareOf { it.won })} " +
                "survival=${armRuns.map { it.seconds }.gateMedian()} clear=${armRuns.filter { it.won }.map { it.seconds }.finiteMedian()} " +
                "melds=${"%.2f".format(armRuns.map { it.melds }.average())} " +
                bootstrapLine("dWinVsMeld", paired) { pairs -> pairs.map { (m, a) -> (if (a.won) 1.0 else 0.0) - (if (m.won) 1.0 else 0.0) }.average() } + " " +
                bootstrapLine("dSurvivalVsMeld", paired, "%.2f") { pairs ->
                    pairs.map { it.second.seconds }.gateMedian().toDouble() - pairs.map { it.first.seconds }.gateMedian()
                })
        }
    }

    private fun List<Float>.quantile(q: Double): Int = this[((size - 1) * q).toInt()].toInt()

    /** The canonical content with the Meld bonus set by [knobs] (`damage=0.12,integrity=0.10`; blank keeps it). */
    private fun meldContent(knobs: String): GameplayContentSnapshot {
        var policy = canonicalGameplayContent.relicPolicy
        knobs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { knob ->
            val (name, value) = knob.split('=').map { it.trim() }
            policy = when (name) {
                "damage" -> policy.copy(meldDamage = value.toFloat())
                "integrity" -> policy.copy(meldIntegrity = value.toFloat())
                else -> error("unknown Meld knob $name")
            }
        }
        return canonicalGameplayContent.copy(relicPolicy = policy)
    }

    private companion object {
        /**
         * A strong build that fits every probed profile's Arc Coil (Charge key, Arsenal and Chain tags, Ion): the two Charge
         * Relics, the weapon echo and the contagion that spreads its Ion.
         */
        val FitRelics: List<RelicId> = SweepStrongRelics
    }
}
