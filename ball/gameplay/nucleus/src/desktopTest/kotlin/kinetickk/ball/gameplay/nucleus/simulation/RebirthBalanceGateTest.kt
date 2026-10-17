// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Rebirth difficulty gates (SPEC 11.2), played by the input-only NoPoi bot on one fixed seed set: the
 * second half of the NoPoi probe (`i * 7_919 + 13`, i = 6..10), chosen by its definition, never by
 * outcome. Weapons are the pre-rework set (the 24-weapon rework lands separately and the gates are
 * re-measured after it), so the signature and harmony bonuses of the strong builds are not active yet.
 *
 * The first four gates are SPEC 11.2's lower bounds; E and F require the difficulty to rise with the
 * tier for a partly upgraded profile and for the strong build, so a curve where the tier stops
 * mattering once the Matrix is bought fails (F failed while the Architect's hit cap was a share of the
 * tier-scaled integrity: the strong build's fight took a median 34 s at tier 3 and 35 s at tier 10).
 *
 * Measured with the shipped tier formulas before the weapon rework (seed:outcome@seconds; a "fight" is
 * the Architect's arrival at 720 s to the Victory):
 * - A, tier 0 fresh: 47527 loss@682, 55446 win@778, 63365 win@773, 71284 win@769, 79203 win@758 (4/5).
 * - B, tier 3 full Matrix + Pentagon/Arc Coil: all five win (fights 58, 32, 53, 72, 56 s).
 * - C, tier 6 fresh: all five lose within 15 s (the bot rams the larger opening at low speed and
 *   tier-6 contact damage, about 21 per touch, breaks the Core).
 * - D, tier 10 strong build: 47527 win@790, 55446 win@800, 63365 win@827, 71284 loss@567 (obstacle),
 *   79203 win@782 (median: a win).
 * - E, half Matrix (Orb, starter weapon): tier 2 wins 5/5; tier 8 loses 5/5 within 70 s. The same
 *   profile wins 5/5 through tier 4, 3/5 at 5, 2/5 at 6 and none from tier 7.
 * - F, strong build fights: tier 3 median 50 s (56, 49, 49, 52, 50), tier 10 median 80 s (70, 80, 107,
 *   a loss, 62).
 * Fresh profiles win 2/5 at tier 1 and none from tier 2; a full Matrix with the Pentagon and Arc Coil
 * wins every tier 5/5 while its fight grows from 35 s (tier 0) to about 100 s (tier 10).
 */
class RebirthBalanceGateTest {
    @Test fun aFreshNoPoiBotStillWinsTierZero() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 0, setup = Fresh) }
        report("GATE A tier=0 fresh", outcomes)
        assertTrue(outcomes.count { it.won } >= 4, "tier 0 must stay winnable: ${outcomes.summary()}")
    }

    @Test fun aFullMatrixAndASignatureWeaponCanWinTierThree() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 3, setup = Signature) }
        report("GATE B tier=3 matrix+signature", outcomes)
        assertTrue(outcomes.count { it.won } >= 2, "tier 3 must be winnable with progression: ${outcomes.summary()}")
    }

    @Test fun aFreshProfileCannotWinTierSix() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 6, setup = Fresh) }
        report("GATE C tier=6 fresh", outcomes)
        assertTrue(outcomes.none { it.won }, "tier 6 must demand progression: ${outcomes.summary()}")
    }

    @Test fun aHalfMatrixProfileWinsTierTwoButNotTierEight() {
        val low = GateSeeds.map { seed -> play(seed, tier = 2, setup = Half) }
        val high = GateSeeds.map { seed -> play(seed, tier = 8, setup = Half) }
        report("GATE E tier=2 half", low)
        report("GATE E tier=8 half", high)
        assertTrue(low.count { it.won } >= 2, "tier 2 must be winnable halfway through the Matrix: ${low.summary()}")
        assertTrue(high.count { !it.won } >= 4, "tier 8 must outgrow half the Matrix: ${high.summary()}")
    }

    @Test fun theStrongBuildClearsTierTenLaterThanTierThree() {
        val low = GateSeeds.map { seed -> play(seed, tier = 3, setup = Strong) }
        val high = GateSeeds.map { seed -> play(seed, tier = 10, setup = Strong) }
        report("GATE F tier=3 strong", low)
        report("GATE F tier=10 strong", high)
        val lowClear = low.medianClear()
        val highClear = high.medianClear()
        assertTrue(highClear >= lowClear * 1.3f,
            "tier 10 must take the strong build clearly longer than tier 3: median clear $highClear s vs $lowClear s, " +
                "${high.summary()} / ${low.summary()}")
    }

    @Test fun aStrongBuildSurvivesEightMinutesOfTierTen() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 10, setup = Strong) }
        report("GATE D tier=10 strong", outcomes)
        val median = outcomes.map { it.seconds }.sorted()[outcomes.size / 2]
        assertTrue(median >= 480f, "tier 10 must be survivable for a strong build: median $median s, ${outcomes.summary()}")
    }

    private data class Outcome(val seed: Int, val won: Boolean, val seconds: Float, val reason: String, val architectAt: Float?) {
        /** Seconds from the Architect's arrival to the Victory; a loss never clears. */
        val clear: Float get() = if (won && architectAt != null) seconds - architectAt else Float.POSITIVE_INFINITY

        override fun toString(): String = "$seed:${if (won) "WIN" else "LOSS"}@${seconds.toInt()}s" +
            if (won) architectAt?.let { "(fight ${(seconds - it).toInt()}s)" }.orEmpty() else "($reason)"
    }

    private fun List<Outcome>.summary(): String = joinToString(" ")

    private fun List<Outcome>.medianClear(): Float = map { it.clear }.sorted()[size / 2]

    /** How many ranks of the Core Matrix a starting profile owns. */
    private enum class MatrixPlan { NONE, HALF, FULL }

    /** A starting profile: Matrix ranks, character, starting weapon and Relics bound at the start. */
    private class Setup(
        val matrix: MatrixPlan,
        val shape: CoreShape,
        val weapon: WeaponId,
        val weaponOrder: List<WeaponId>,
        val relics: List<RelicId> = emptyList(),
    )

    private fun play(seed: Int, tier: Int, setup: Setup): Outcome {
        val content = canonicalGameplayContent
        val ranks = when (setup.matrix) {
            MatrixPlan.NONE -> content.matrixNodes.map { 0 }
            MatrixPlan.HALF -> halfMatrixRanks(content.matrixNodes)
            MatrixPlan.FULL -> content.matrixNodes.map { it.maxRanks }
        }
        val state = MutableGameState(
            content,
            seed = seed,
            bootstrapProgress = GameplayProfileSnapshot(
                preferences = PlayerPreferences(simulationSpeed = 1f),
                economy = PlayerEconomy(),
                loadout = PlayerLoadout(setup.shape, setup.weapon, ReferenceArmory.toSet() + setup.weapon),
                matrix = MatrixProgress(ranks),
                collection = PlayerCollection(),
                rebirthProgress = RebirthProgress(level = tier, highestCleared = tier - 1),
            ),
        )
        state.unlockWeaponsForTesting(ReferenceArmory)
        state.startRun()
        setup.relics.forEach(state::acquireRelicForTesting)
        val bot = NoPoiBot(archetypeTag = BuildTag.RAM, weaponOrder = setup.weaponOrder)
        var architectAt: Float? = null
        while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                bot.control(state)
                state.update(0.02f)
            }
            if (architectAt == null && state.enemies.any { it.type == EnemyType.ARCHITECT }) architectAt = state.elapsed
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        return Outcome(seed, state.phase == GamePhase.VICTORY, state.elapsed, state.message, architectAt)
    }

    /**
     * Half the Core Matrix by Matter: ranks bought cheapest first (each node's ranks in order) while the
     * total stays within half the full price. On the shipped Matrix this is every rank of the first two
     * rows and one rank of most third-row nodes up to Rebirth 1, with no keystone or deeper node: the
     * profile of a player a few tiers in.
     */
    private fun halfMatrixRanks(nodes: List<MatrixNodeDefinition>): List<Int> {
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

    private fun report(label: String, outcomes: List<Outcome>) {
        println("$label ${outcomes.summary()}")
    }

    private companion object {
        val GateSeeds: List<Int> = (6..10).map { it * 7_919 + 13 }
        val BotWeapons: List<WeaponId> = listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.ENTROPY_FIELD, WeaponId.PRISM_RELAY)

        /** No Matrix ranks, the Orb and the starter weapon. */
        val Fresh = Setup(MatrixPlan.NONE, shape = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponOrder = BotWeapons)

        /** Every Matrix rank and the Pentagon with its signature Arc Coil from the start. */
        val Signature = Setup(MatrixPlan.FULL, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, weaponOrder = BotWeapons)

        /** Half the Matrix (see [halfMatrixRanks]), the Orb and the starter weapon. */
        val Half = Setup(MatrixPlan.HALF, shape = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponOrder = BotWeapons)

        /**
         * The strong build: the signature Arc Coil, a Charge-key Relic in harmony with it and three proven
         * Relics bound from the start, every Matrix rank. Before the weapon rework neither the signature nor
         * the key-harmony bonus exists, so this measures the Pentagon, Arc Coil and four Relics only.
         */
        val Strong = Setup(
            MatrixPlan.FULL, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, weaponOrder = BotWeapons,
            relics = listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN),
        )
    }
}
