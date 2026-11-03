// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rarity design's targets (decisions D4), a design gate without a base: its bands are the design's. It plays
 * offer-only runs (no flight): each run starts from its arm's profile at its tier ([rarityArmProfile]) and opens the
 * item offers a full run of that arm opens ([OFFERS_PER_RUN], the median of the full-run probe at tiers 3 and 6, where
 * the bot reaches level 42 and every Elite pays its Bounty offer), at the levels they open at, and takes cards as the
 * arm's player does ([RarityArm]; any other card is the aligned one). Every draw is the real offer code: the deck,
 * lanes, pity, Luck the run takes, the owned-tier decay. Over [RUNS] runs per arm and tier on the gate seeds
 * (i = 4,401..6,400 of `i × 7,919 + 13`, never tuned on; the calibration window is 3,401..4,400, policy.md):
 * - max Luck at tiers 3, 6 and 10: the median run acquires 2 Legendaries, P(≥ 3) lies in 0.15..0.35, P(≥ 4) ≤ 0.08;
 * - base Luck: the median run acquires at most 1 Legendary;
 * - Epics: the max-Luck median lies in 4..7, the base-Luck median in 2..4, and each arm's Epic median is at least two
 *   above its Legendary median.
 * [RarityDistributionProbeTest] measures the same arms on full runs (`KK_RARITY_PROBE`); `KK_RARITY_SYNTH=first..last`
 * plays these offer-only runs on another window and prints their histograms (10,000 runs per arm read the tails).
 */
class RarityDistributionGateTest {
    @Test
    fun maxLuckRunsTakeAboutTwoLegendariesAndBaseLuckRunsAtMostOne() {
        val failures = ArrayList<String>()
        for (tier in TIERS) {
            val max = synthesized(RarityArm.MAX_LUCK, tier)
            val base = synthesized(RarityArm.BASE_LUCK, tier)
            println("RARITY-GATE MAX_LUCK tier $tier ${max.summary()}")
            println("RARITY-GATE BASE_LUCK tier $tier ${base.summary()}")
            if (max.legendaryMedian != 2) failures += "max Luck tier $tier Legendary median ${max.legendaryMedian}"
            if (max.atLeast(3) !in 0.15..0.35) failures += "max Luck tier $tier P(L >= 3) ${max.atLeast(3)}"
            if (max.atLeast(4) > 0.08) failures += "max Luck tier $tier P(L >= 4) ${max.atLeast(4)}"
            if (base.legendaryMedian > 1) failures += "base Luck tier $tier Legendary median ${base.legendaryMedian}"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun epicsStayRareAndClearlyAboveLegendaries() {
        val failures = ArrayList<String>()
        for (tier in TIERS) {
            val max = synthesized(RarityArm.MAX_LUCK, tier)
            val base = synthesized(RarityArm.BASE_LUCK, tier)
            if (max.epicMedian !in 4..7) failures += "max Luck tier $tier Epic median ${max.epicMedian}"
            if (base.epicMedian !in 2..4) failures += "base Luck tier $tier Epic median ${base.epicMedian}"
            for ((arm, runs) in listOf("max" to max, "base" to base)) {
                if (runs.epicMedian < runs.legendaryMedian + 2) failures += "$arm Luck tier $tier Epic ${runs.epicMedian} vs Legendary ${runs.legendaryMedian}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun probeOfferOnlyRunsOnACalibrationWindow() {
        val window = System.getenv("KK_RARITY_SYNTH")?.let(::probeWindow) ?: return
        for (arm in listOf(RarityArm.MAX_LUCK, RarityArm.BASE_LUCK)) for (tier in TIERS) {
            println("RARITY-SYNTH $arm tier $tier ${synthesize(arm, tier, windowSeeds(window)).summary()}")
        }
    }

    private companion object {
        val TIERS = listOf(3, 6, 10)
        const val RUNS = 2_000
        val GateSeeds: List<Int> = windowSeeds(4_401 until 4_401 + RUNS)
        val results = HashMap<Pair<RarityArm, Int>, SynthesizedRuns>()

        @Synchronized
        fun synthesized(arm: RarityArm, tier: Int): SynthesizedRuns = results.getOrPut(arm to tier) { synthesize(arm, tier, GateSeeds) }
    }
}

/** The full-run probe's median item offers per run for both arms at tiers 3 and 6 (57 and 60): 41 level offers and the Elite Bounties. */
internal const val OFFERS_PER_RUN = 58

/** Legendaries and Epics acquired per offer-only run of one arm and tier. */
internal class SynthesizedRuns(private val legendaries: IntArray, private val epics: IntArray) {
    val legendaryMedian: Int = legendaries.sorted()[legendaries.size / 2]
    val epicMedian: Int = epics.sorted()[epics.size / 2]

    fun atLeast(count: Int): Double = legendaries.count { it >= count }.toDouble() / legendaries.size

    fun summary(): String {
        fun histogram(values: IntArray) = values.toList().groupingBy { it }.eachCount().toSortedMap().entries.joinToString(" ") { "${it.key}:${it.value}" }
        fun share(count: Int): String {
            val p = atLeast(count)
            return "%.3f(SE %.3f)".format(p, sqrt(p * (1 - p) / legendaries.size))
        }
        return "runs ${legendaries.size} L median $legendaryMedian P>=2 ${share(2)} P>=3 ${share(3)} P>=4 ${share(4)} " +
            "max ${legendaries.max()} hist [${histogram(legendaries)}] | E median $epicMedian hist [${histogram(epics)}]"
    }
}

/** [arm]'s offer-only runs at [tier] on [seeds], on every core. */
internal fun synthesize(arm: RarityArm, tier: Int, seeds: List<Int>): SynthesizedRuns {
    val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()) { task ->
        Thread(task, "rarity-synth").apply { isDaemon = true }
    }
    try {
        val runs = seeds.map { seed -> pool.submit(Callable { synthesizeRun(arm, tier, seed) }) }.map { it.get() }
        return SynthesizedRuns(IntArray(runs.size) { runs[it].first }, IntArray(runs.size) { runs[it].second })
    } finally {
        pool.shutdownNow()
    }
}

/**
 * One offer-only run: [OFFERS_PER_RUN] item offers at the levels a full run opens them (the level offers in order, the
 * Elite Bounties spread among them), each card taken as [arm]'s player takes it. Returns the distinct Legendaries and
 * Epics acquired.
 */
internal fun synthesizeRun(arm: RarityArm, tier: Int, seed: Int): Pair<Int, Int> {
    val state = startGateRun(seed, rarityArmProfile(arm, tier))
    state.varietyRun.omenPending = false
    val held = HashSet<Int>()
    var legendaries = 0
    var epics = 0
    val maximumLevel = state.content.tempo.maximumLevel
    for (offer in 0 until OFFERS_PER_RUN) {
        state.level = 2 + offer * (maximumLevel - 1) / OFFERS_PER_RUN
        state.openItemChoice()
        if (state.phase != GamePhase.CHOICE) continue
        state.choose(synthesizedPick(state, arm))
        if (state.phase == GamePhase.CHOICE && state.choiceType == ChoiceType.ACTIVE_SLOT) state.choose(state.choices.lastIndex)
        for (item in state.content.items) {
            if (state.itemStacks[item.id] == 0 || !held.add(item.id)) continue
            if (item.rarity == ItemRarity.LEGENDARY) legendaries++
            if (item.rarity == ItemRarity.EPIC) epics++
        }
    }
    return legendaries to epics
}

/**
 * The card [arm]'s player takes ([rarityArmBot]'s taste: never one it would only salvage or that asks for an active
 * slot), else the aligned card; a base-Luck player passes Luck by.
 */
private fun synthesizedPick(state: MutableGameState, arm: RarityArm): Int {
    fun item(index: Int) = state.content.items[requireNotNull(state.choices[index].itemId)]
    fun luck(index: Int) = item(index).stats.filter { it.effect == ItemEffect.LUCK }.sumOf { it.amount.toDouble() }
    fun wanted(index: Int) = item(index).let { state.itemStacks[it.id] < it.maxStacks && !state.needsActiveSlot(it) }
    val indices = state.choices.indices
    val allowed = if (arm == RarityArm.BASE_LUCK) indices.filter { luck(it) == 0.0 }.ifEmpty { indices.toList() } else indices.toList()
    val tasted = allowed.filter(::wanted)
    return tasted.firstOrNull { item(it).rarity == ItemRarity.LEGENDARY }
        ?: (if (arm == RarityArm.MAX_LUCK) tasted.filter { luck(it) > 0.0 }.maxByOrNull(::luck) else null)
        ?: tasted.firstOrNull { item(it).rarity == ItemRarity.EPIC }
        ?: allowed.first()
}
