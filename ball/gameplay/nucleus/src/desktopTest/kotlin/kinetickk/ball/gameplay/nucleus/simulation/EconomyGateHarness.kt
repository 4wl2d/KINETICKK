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

/*
 * Shared full-run harness of the economy gates (run level calibration, Matter income, upgrades). It plays
 * exactly like RebirthBalanceGateTest: the input-only NoPoi bot, 0.02 s steps until the run ends or 900 s,
 * sound and visual cues taken every step, on seeds fixed by definition and never picked by outcome. The gates restated
 * as population gates play [PopulationSeeds] through [populationOf].
 */

/** The gate seeds of RebirthBalanceGateTest: the second half of the NoPoi probe (`i * 7_919 + 13`, i = 6..10). */
internal val EconomyGateSeeds: List<Int> = (6..10).map { it * 7_919 + 13 }

/**
 * The population of the seeded gates restated for active items (0.5.0; policy.md, "Balance gate populations"): the
 * NoPoi probe sequence `i * 7_919 + 13` for i = 1,001..1,300. On the 05885fa9 base or with the active items those
 * gates' own fixed seeds passed with a probability below 0.90 (where these runs and the calibration windows
 * disagreed, as for the Apex Prism, both pooled decided), so a change to the reward stream re-rolled them; each now
 * states its rule over these 300 runs. They are disjoint from every fixed gate seed set (i ≤ 205, and 2026) and from
 * the windows the active-item calibration tunes on (i ≤ 900, and 1,301 onward). A restated threshold follows the
 * population threshold rule: a floor is the lower of the seeded gate's own threshold on the same statement (k of n read
 * as k / n, a median or ratio bound as is) and the 05885fa9 base value on these seeds minus two standard errors; a
 * ceiling is the higher of that threshold and the base plus two standard errors; both rounded outward to 0.01. A 0.5.0
 * accepted trade-off bounds a gate more strictly only where the base itself meets it (ItemVariety V2's 0.81 does; the
 * tier-0 fresh survival 0.73 did not and is dropped). Each test's KDoc records its base value, SE and threshold.
 */
internal val PopulationSeeds: List<Int> = (1_001..1_300).map { it * 7_919 + 13 }

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

/** The weapons a gate profile owns: the reference armory plus its starting weapon. */
internal fun gateOwnedWeapons(weapon: WeaponId): Set<WeaponId> = ReferenceArmory.toSet() + weapon

/** A gate's starting profile: its loadout, Matrix, tier (highest clear one below), economy and upgrades. */
internal fun gateProfile(
    matrix: GateMatrix,
    tier: Int,
    shape: CoreShape = CoreShape.ORB,
    weapon: WeaponId = WeaponId.FLUX_WAKE,
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
 * start; [observe] sees the state after every bot decision or step, before the cues are taken; [shown] sees every
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
    val state = MutableGameState(content, seed = seed, bootstrapProgress = profile)
    state.unlockWeaponsForTesting(ReferenceArmory)
    state.startRun()
    relics.forEach(state::acquireRelicForTesting)
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

/** The median of an odd-sized sample (the upper middle of an even one). */
internal fun List<Float>.gateMedian(): Float = sorted()[size / 2]
