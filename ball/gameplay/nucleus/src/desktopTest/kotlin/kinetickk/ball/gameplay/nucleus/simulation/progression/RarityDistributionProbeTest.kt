// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test

/**
 * The rarity design's measurement on full runs (decisions D4): Legendary and Epic acquisitions per run as histograms,
 * played by the gate harness's NoPoi bot to the run's end or 900 s. Off by default; `KK_RARITY_PROBE=first..last` names
 * the calibration window (`i × 7,919 + 13`; the item rarity window is 3,401..4,400, policy.md), `KK_RARITY_ARMS` the
 * arms ([RarityArm] names, default MAX_LUCK,BASE_LUCK) and `KK_RARITY_TIERS` the Rebirth tiers (default 3,6,10). Runs
 * use every core. Each run prints one `RARITY-RUN` line (acquisitions, cards shown, offers, level, Luck, the time of
 * the first Epic and Legendary), each arm and tier one `RARITY` summary with both histograms, the median, P(≥ 3) and
 * P(≥ 4) of Legendaries with binomial standard errors. [RarityDistributionGateTest] pins the targets on offer-only runs
 * whose schedules these runs measured.
 */
class RarityDistributionProbeTest {
    @Test
    fun probeLegendaryAndEpicAcquisitionsOnFullRuns() {
        val window = System.getenv("KK_RARITY_PROBE")?.let(::probeWindow) ?: return
        val arms = System.getenv("KK_RARITY_ARMS")?.split(",")?.map { RarityArm.valueOf(it.trim()) }
            ?: listOf(RarityArm.MAX_LUCK, RarityArm.BASE_LUCK)
        val tiers = System.getenv("KK_RARITY_TIERS")?.split(",")?.map { it.trim().toInt() } ?: listOf(3, 6, 10)
        val seeds = windowSeeds(window)
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()) { task ->
            Thread(task, "rarity-probe").apply { isDaemon = true }
        }
        try {
            for (arm in arms) for (tier in tiers) {
                val runs = seeds.map { seed -> pool.submit(Callable { flyRarityRun(arm, tier, seed) }) }.map { it.get() }
                runs.forEach { println("RARITY-RUN ${it.line()}") }
                println("RARITY $arm tier $tier ${summary(runs)}")
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun summary(runs: List<RarityRun>): String {
        fun histogram(values: List<Int>) = values.groupingBy { it }.eachCount().toSortedMap().entries.joinToString(" ") { "${it.key}:${it.value}" }
        fun share(predicate: (RarityRun) -> Boolean): String {
            val p = runs.shareOf(predicate)
            return "%.3f(SE %.3f)".format(p, kotlin.math.sqrt(p * (1 - p) / runs.size))
        }
        val legendaries = runs.map { it.legendaries }.sorted()
        val epics = runs.map { it.epics }.sorted()
        return "runs ${runs.size} won ${share { it.won }} L median ${legendaries[runs.size / 2]} P>=1 ${share { it.legendaries >= 1 }} " +
            "P>=2 ${share { it.legendaries >= 2 }} P>=3 ${share { it.legendaries >= 3 }} P>=4 ${share { it.legendaries >= 4 }} " +
            "max ${legendaries.last()} hist [${histogram(legendaries)}] | E median ${epics[runs.size / 2]} q25 ${epics[runs.size / 4]} " +
            "q75 ${epics[3 * runs.size / 4]} hist [${histogram(epics)}] | offers median ${runs.map { it.offers }.sorted()[runs.size / 2]} " +
            "level median ${runs.map { it.level }.sorted()[runs.size / 2]} luck median ${"%.2f".format(runs.map { it.luck }.sorted()[runs.size / 2])}"
    }
}

/** The probe arms: the max-Luck player and the player who never invests in Luck (decisions D4). */
internal enum class RarityArm {
    /** The full Matrix (Lucky Charm included), taking every Legendary, then Luck, then every Epic. */
    MAX_LUCK,
    /** The full Matrix without Lucky Charm, taking every Legendary and every Epic but never seeking Luck. */
    BASE_LUCK,
    /** The full Matrix, played by the reference bot (its own card scores). */
    REFERENCE,
}

/** One probe run: what it acquired and was shown, its offers, end level and Luck, and when its first Epic and Legendary came. */
internal class RarityRun(
    val arm: RarityArm,
    val tier: Int,
    val seed: Int,
    val won: Boolean,
    val elapsed: Float,
    val level: Int,
    val offers: Int,
    val legendaries: Int,
    val epics: Int,
    val legendaryCards: Int,
    val epicCards: Int,
    val luck: Float,
    val firstEpicAt: Float?,
    val firstLegendaryAt: Float?,
) {
    fun line(): String = "$arm $tier $seed won=$won t=${"%.1f".format(elapsed)} level=$level offers=$offers L=$legendaries E=$epics " +
        "Lcards=$legendaryCards Ecards=$epicCards luck=${"%.2f".format(luck)} firstE=${firstEpicAt?.let { "%.0f".format(it) }} " +
        "firstL=${firstLegendaryAt?.let { "%.0f".format(it) }}"
}

/**
 * The arm's starting profile: the veteran of decisions D2's P5 (the full Matrix, Refit and Honing maxed), without Lucky
 * Charm for [RarityArm.BASE_LUCK].
 */
internal fun rarityArmProfile(arm: RarityArm, tier: Int): GameplayProfileSnapshot {
    val weapon = botLoadout(BotWeaponOrder, ReferenceArmory)
    val maxed = canonicalGameplayContent.upgrades.maxLevel
    val profile = gateProfile(GateMatrix.FULL, tier, weapon = weapon,
        upgrades = gateUpgrades(CoreShape.ORB, maxed, gateOwnedWeapons(weapon), maxed))
    if (arm != RarityArm.BASE_LUCK) return profile
    val charm = MatrixNodeId.LUCKY_CHARM.ordinal
    return profile.copy(matrix = MatrixProgress(profile.matrix.ranks.mapIndexed { node, rank -> if (node == charm) 0 else rank }))
}

/** The arm's bot: the reference bot with the arm's rarity taste. */
internal fun rarityArmBot(arm: RarityArm): NoPoiBot = NoPoiBot(rarityTaste = when (arm) {
    RarityArm.MAX_LUCK -> RarityTaste.LUCK_SEEKING
    RarityArm.BASE_LUCK -> RarityTaste.GREEDY
    RarityArm.REFERENCE -> RarityTaste.NONE
})

/** One full gate run of [arm] at [tier] on [seed], counting distinct Epics and Legendaries ever held. */
internal fun flyRarityRun(arm: RarityArm, tier: Int, seed: Int): RarityRun {
    val held = HashSet<Int>()
    var legendaries = 0
    var epics = 0
    var legendaryCards = 0
    var epicCards = 0
    var offers = 0
    var firstEpicAt: Float? = null
    var firstLegendaryAt: Float? = null
    var counted = 0
    val state = playGateRun(seed, rarityArmProfile(arm, tier), rarityArmBot(arm), shown = shown@{ s ->
        if (s.choiceType != ChoiceType.ITEM) return@shown
        offers++
        for (choice in s.choices) {
            val item = choice.itemId?.let(s.content::item) ?: continue
            if (item.rarity == ItemRarity.LEGENDARY) legendaryCards++
            if (item.rarity == ItemRarity.EPIC) epicCards++
        }
    }) { s ->
        if (s.offerRun.acquiredItemCount == counted) return@playGateRun
        counted = s.offerRun.acquiredItemCount
        for (item in s.content.items) {
            if (s.itemStacks[item.id] == 0 || !held.add(item.id)) continue
            when (item.rarity) {
                ItemRarity.LEGENDARY -> { legendaries++; if (firstLegendaryAt == null) firstLegendaryAt = s.elapsed }
                ItemRarity.EPIC -> { epics++; if (firstEpicAt == null) firstEpicAt = s.elapsed }
                else -> Unit
            }
        }
    }
    return RarityRun(arm, tier, seed, state.phase == GamePhase.VICTORY, state.elapsed, state.level, offers, legendaries, epics,
        legendaryCards, epicCards, state.effectiveLuck(), firstEpicAt, firstLegendaryAt)
}
