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

/*
 * Shared full-run harness of the economy gates (run level calibration, Matter income, upgrades). It plays
 * exactly like RebirthBalanceGateTest: the input-only NoPoi bot, 0.02 s steps until the run ends or 900 s,
 * sound and visual cues taken every step, on seeds fixed by definition and never picked by outcome.
 */

/** The gate seeds of RebirthBalanceGateTest: the second half of the NoPoi probe (`i * 7_919 + 13`, i = 6..10). */
internal val EconomyGateSeeds: List<Int> = (6..10).map { it * 7_919 + 13 }

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
 * start; [observe] sees the state after every bot decision or step, before the cues are taken.
 */
internal fun playGateRun(
    seed: Int,
    profile: GameplayProfileSnapshot,
    bot: NoPoiBot,
    relics: List<RelicId> = emptyList(),
    content: GameplayContentSnapshot = canonicalGameplayContent,
    observe: (MutableGameState) -> Unit = {},
): MutableGameState {
    val state = MutableGameState(content, seed = seed, bootstrapProgress = profile)
    state.unlockWeaponsForTesting(ReferenceArmory)
    state.startRun()
    relics.forEach(state::acquireRelicForTesting)
    while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
        if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
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
