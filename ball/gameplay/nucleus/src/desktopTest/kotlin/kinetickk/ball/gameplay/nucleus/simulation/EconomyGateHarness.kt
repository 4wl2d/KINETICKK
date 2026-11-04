// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Shared full-run harness of the economy gates (run level calibration, Matter income, upgrades). It plays
 * exactly like RebirthBalanceGateTest: the input-only NoPoi bot, 0.02 s steps until the run ends or 900 s,
 * sound and visual cues taken every step, on seeds fixed by definition and never picked by outcome. The gates restated
 * as population gates play [PopulationSeeds] through [populationOf].
 */

/** The gate seeds of RebirthBalanceGateTest: the second half of the NoPoi probe (`i * 7_919 + 13`, i = 6..10). */
internal val EconomyGateSeeds: List<Int> = (6..10).map { it * 7_919 + 13 }

/** The probe indices `i` of [PopulationSeeds]; no calibration window ever overlaps them. */
internal val PopulationIndices: IntRange = 1_001..1_300

/**
 * The population of the seeded gates restated as population gates (policy.md, "Balance gate populations"): the NoPoi
 * probe sequence `i * 7_919 + 13` for i = 1,001..1,300. 0.5.0 moved here every gate whose own fixed seeds passed with a
 * probability below 0.90 on the 05885fa9 tree or with the active items (where these runs and the calibration windows
 * disagreed, as for the Apex Prism, both pooled decided), since a change to the reward stream re-rolled them; each
 * states its rule over these 300 runs. They are disjoint from every fixed gate seed set (i ≤ 205, and 2026), from the
 * calibration windows (i ≤ 900 and 1,301..2,800) and from the window of the D and H floors (2,801..3,400). The
 * thresholds follow the population threshold rule on the Elite-resonance base (policy.md: the tree where Elite-class
 * kills amplify the weapon in place of the open-world Totem, dungeons hold Totem chests and collapse, and a still Core
 * freezes; the 0.6.0 tree 15b23096 was the base before it): a floor is the lower of the gate's design or seeded
 * threshold on the same statement (k of n read as k / n, a median or ratio bound as is) and the base value on these
 * seeds minus two standard errors; a ceiling is the higher of that threshold and the base plus two standard errors;
 * both rounded outward to 0.01, and a design target never weakens. A 0.5.0 accepted trade-off bounds a gate more
 * strictly only where the base itself meets it (ItemVariety V2's 0.81 does). Each test's KDoc records its base value,
 * SE and threshold.
 */
internal val PopulationSeeds: List<Int> = windowSeeds(PopulationIndices)

/** The probe seeds `i * 7_919 + 13` for i in [indices]. */
internal fun windowSeeds(indices: IntRange): List<Int> = indices.map { it * 7_919 + 13 }

/**
 * The calibration window a probe's [text] names: `first..last`, or `first` alone for [defaultLength] indices from it.
 * It starts at 1 or above and never overlaps the gate population ([PopulationIndices]).
 */
internal fun probeWindow(text: String, defaultLength: Int? = null): IntRange {
    val bounds = text.split("..").map { it.trim().toInt() }
    val first = bounds[0]
    val last = bounds.getOrNull(1) ?: (first + requireNotNull(defaultLength) { "a probe window is first..last, got $text" } - 1)
    require(first in 1..last) { "a probe window is first..last, got $text" }
    require(last < PopulationIndices.first || first > PopulationIndices.last) { "the gate population ($PopulationIndices) is never probed: $text" }
    return first..last
}

/**
 * [work] on every one of [items] on a small pool of daemon threads. Each run is independent and deterministic and the
 * results keep the order of [items], so the outcome does not depend on scheduling.
 */
internal fun <I, T> populationOf(items: List<I>, work: (I) -> T): List<T> =
    items.map { item -> PopulationPool.submit(Callable { work(item) }) }.map { it.get() }

/** The share of the list's elements matching [predicate] (0 for an empty list). */
internal fun <T> List<T>.shareOf(predicate: (T) -> Boolean): Double = if (isEmpty()) 0.0 else count(predicate).toDouble() / size

private val PopulationPool: ExecutorService by lazy {
    Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(1, 8)) { task ->
        Thread(task, "gate-population").apply { isDaemon = true }
    }
}

/** How many ranks of the Core Matrix a starting profile owns. */
internal enum class GateMatrix { NONE, HALF, FULL }

/** The Matter a profile spent on every rank of the Core Matrix: the least lifetime Matter a full Matrix can have. */
internal fun fullMatrixPrice(content: GameplayContentSnapshot = canonicalGameplayContent): Long =
    content.matrixNodes.sumOf { node -> node.costs.sumOf { it.toLong() } }

/** Matrix ranks of [plan]; HALF is RebirthBalanceGateTest's half Matrix (cheapest ranks within half the price). */
internal fun gateMatrixRanks(plan: GateMatrix, content: GameplayContentSnapshot = canonicalGameplayContent): List<Int> =
    when (plan) {
        GateMatrix.NONE -> content.matrixNodes.map { 0 }
        GateMatrix.HALF -> halfMatrixRanks(content.matrixNodes)
        GateMatrix.FULL -> content.matrixNodes.map { it.maxRanks }
    }

/**
 * Half the Core Matrix by Matter: ranks bought cheapest first (each node's ranks in order) while the
 * total stays within half the full price. On the shipped Matrix this is every rank of the first two
 * rows and one rank of most third-row nodes up to Rebirth 1, with no keystone or deeper node: the
 * profile of a player a few tiers in.
 */
internal fun halfMatrixRanks(nodes: List<MatrixNodeDefinition>): List<Int> {
    val ranks = IntArray(nodes.size)
    val budget = nodes.sumOf { it.costs.sum() } / 2
    var spent = 0
    val purchases = nodes.indices.flatMap { node -> nodes[node].costs.indices.map { rank -> node to rank } }
        .sortedWith(compareBy({ (node, rank) -> nodes[node].costs[rank] }, { it.second }))
    for ((node, rank) in purchases) {
        val cost = nodes[node].costs[rank]
        if (ranks[node] != rank || spent + cost > budget) continue
        ranks[node]++
        spent += cost
    }
    return ranks.toList()
}

/** Refit [refit] for [shape] and Honing [honing] on every weapon of [weapons]; every other level 0. */
internal fun gateUpgrades(shape: CoreShape, refit: Int, weapons: Set<WeaponId>, honing: Int): PermanentUpgrades =
    PermanentUpgrades(
        List(CoreShape.entries.size) { if (it == shape.ordinal) refit else 0 },
        List(WeaponId.entries.size) { if (WeaponId.entries[it] in weapons) honing else 0 },
    )

/**
 * The progression model's fully progressed profile (docs/balance/progression.md): every Matrix rank, [shape] with its
 * Refit and Arc Coil with its Honing at the maximum level; the full and strong sweep arms fly it with the Pentagon.
 */
internal fun progressedProfile(
    tier: Int,
    shape: CoreShape = CoreShape.PENTAGON,
    content: GameplayContentSnapshot = canonicalGameplayContent,
): GameplayProfileSnapshot {
    val maxLevel = content.upgrades.maxLevel
    return gateProfile(GateMatrix.FULL, tier, shape, WeaponId.ARC_COIL, content = content,
        upgrades = gateUpgrades(shape, maxLevel, gateOwnedWeapons(WeaponId.ARC_COIL), maxLevel))
}

/** The weapons a gate profile owns: the reference armory plus its starting weapon. */
internal fun gateOwnedWeapons(weapon: WeaponId): Set<WeaponId> = ReferenceArmory.toSet() + weapon

/** A gate's starting profile: its loadout, Matrix, tier (highest clear one below), economy and upgrades. */
internal fun gateProfile(
    matrix: GateMatrix,
    tier: Int,
    shape: CoreShape = CoreShape.ORB,
    /** The bot's loadout by default (harness H1); a gate whose statement names a start weapon passes it. */
    weapon: WeaponId = botLoadout(BotWeaponOrder, ReferenceArmory),
    economy: PlayerEconomy = PlayerEconomy(),
    upgrades: PermanentUpgrades = PermanentUpgrades(),
    content: GameplayContentSnapshot = canonicalGameplayContent,
): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences = PlayerPreferences(simulationSpeed = 1f),
    economy = economy,
    loadout = PlayerLoadout(shape, weapon, gateOwnedWeapons(weapon)),
    matrix = MatrixProgress(gateMatrixRanks(matrix, content)),
    collection = PlayerCollection(),
    rebirthProgress = RebirthProgress(level = tier, highestCleared = tier - 1),
    upgrades = upgrades,
)

/**
 * Plays one gate run of [profile] on [seed] with [bot] and returns the final state. [relics] are bound at the
 * start, each claiming one of the run's Relic opportunities; [observe] sees the state after every bot decision or step, before the cues are taken; [shown] sees every
 * choice screen the bot is shown (a reroll's new cards are a new screen) before the bot decides.
 */
internal fun playGateRun(
    seed: Int,
    profile: GameplayProfileSnapshot,
    bot: NoPoiBot,
    relics: List<RelicId> = emptyList(),
    content: GameplayContentSnapshot = canonicalGameplayContent,
    shown: (MutableGameState) -> Unit = {},
    observe: (MutableGameState) -> Unit = {},
): MutableGameState {
    val state = startGateRun(seed, profile, relics, content)
    while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
        if (state.phase == GamePhase.CHOICE) {
            shown(state)
            bot.choose(state)
        } else {
            bot.control(state)
            state.update(0.02f)
        }
        observe(state)
        state.takeSoundCues()
        state.takeVisualFxCues()
    }
    return state
}

/** A started gate run of [profile] on [seed]: the reference armory unlocked and [relics] bound (and claimed) at the start. */
internal fun startGateRun(
    seed: Int,
    profile: GameplayProfileSnapshot,
    relics: List<RelicId> = emptyList(),
    content: GameplayContentSnapshot = canonicalGameplayContent,
): MutableGameState {
    val state = MutableGameState(content, seed = seed, bootstrapProgress = profile)
    state.unlockWeaponsForTesting(ReferenceArmory)
    state.startRun()
    relics.forEach(state::claimRelicForTesting)
    return state
}

/** The median of an odd-sized sample (the upper middle of an even one). */
internal fun List<Float>.gateMedian(): Float = sorted()[size / 2]

/** The median of the finite values (NaN for none); the upper middle of an even count. */
internal fun List<Float>.finiteMedian(): Double {
    val finite = filter { it.isFinite() }.sorted()
    return if (finite.isEmpty()) Double.NaN else finite[finite.size / 2].toDouble()
}

/** Bootstrap standard errors of the gates and probes: 20,000 resamples with RNG seed 20261027 (policy.md procedure). */
internal const val BOOTSTRAP_RESAMPLES: Int = 20_000
internal const val BOOTSTRAP_SEED: Int = 20_261_027

/**
 * [metric] over [runs] and its bootstrap standard error over the finite resamples (NaN for no runs). Runs are resampled
 * whole, so a run's engagements (or a seed's paired arms) stay together.
 */
internal fun <T> bootstrap(runs: List<T>, metric: (List<T>) -> Double): Pair<Double, Double> {
    if (runs.isEmpty()) return Double.NaN to Double.NaN
    val random = Random(BOOTSTRAP_SEED)
    val values = DoubleArray(BOOTSTRAP_RESAMPLES) {
        metric(List(runs.size) { runs[random.nextInt(runs.size)] })
    }
    val finite = values.filter { it.isFinite() }
    val mean = finite.average()
    val se = sqrt(finite.sumOf { (it - mean) * (it - mean) } / max(1, finite.size - 1))
    return metric(runs) to se
}

/** "name=value(SE se)": the value in [format], the standard error with four decimals. */
internal fun <T> bootstrapLine(name: String, runs: List<T>, format: String = "%.4f", metric: (List<T>) -> Double): String {
    val (value, se) = bootstrap(runs, metric)
    return "$name=${format.format(value)}(SE ${"%.4f".format(se)})"
}
