// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
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
 * Measured with the shipped tier formulas before the weapon rework (seed:outcome@seconds):
 * - A, tier 0 fresh: 47527 loss@682, 55446 win@778, 63365 win@773, 71284 win@769, 79203 win@758 (4/5).
 * - B, tier 3 full Matrix + Pentagon/Arc Coil: all five win (757, 743, 754, 765, 758 s).
 * - C, tier 6 fresh: all five lose within 15 s (the bot rams the larger opening at low speed and
 *   tier-6 contact damage, about 21 per touch, breaks the Core).
 * - D, tier 10 strong build: 47527 win@761, 55446 win@753, 63365 win@768, 71284 loss@386, 79203 win@765
 *   (median: a win).
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

    @Test fun aStrongBuildSurvivesEightMinutesOfTierTen() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 10, setup = Strong) }
        report("GATE D tier=10 strong", outcomes)
        val median = outcomes.map { it.seconds }.sorted()[outcomes.size / 2]
        assertTrue(median >= 480f, "tier 10 must be survivable for a strong build: median $median s, ${outcomes.summary()}")
    }

    private data class Outcome(val seed: Int, val won: Boolean, val seconds: Float, val reason: String) {
        override fun toString(): String = "$seed:${if (won) "WIN" else "LOSS"}@${seconds.toInt()}s${if (won) "" else "($reason)"}"
    }

    private fun List<Outcome>.summary(): String = joinToString(" ")

    /** A starting profile: Matrix ranks, character, starting weapon and Relics bound at the start. */
    private class Setup(
        val fullMatrix: Boolean,
        val shape: CoreShape,
        val weapon: WeaponId,
        val weaponOrder: List<WeaponId>,
        val relics: List<RelicId> = emptyList(),
    )

    private fun play(seed: Int, tier: Int, setup: Setup): Outcome {
        val content = canonicalGameplayContent
        val ranks = content.matrixNodes.map { if (setup.fullMatrix) it.maxRanks else 0 }
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
        while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                bot.control(state)
                state.update(0.02f)
            }
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        return Outcome(seed, state.phase == GamePhase.VICTORY, state.elapsed, state.message)
    }

    private fun report(label: String, outcomes: List<Outcome>) {
        println("$label ${outcomes.summary()}")
    }

    private companion object {
        val GateSeeds: List<Int> = (6..10).map { it * 7_919 + 13 }
        val BotWeapons: List<WeaponId> = listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.ENTROPY_FIELD, WeaponId.PRISM_RELAY)

        /** No Matrix ranks, the Orb and the starter weapon. */
        val Fresh = Setup(fullMatrix = false, shape = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponOrder = BotWeapons)

        /** Every Matrix rank and the Pentagon with its signature Arc Coil from the start. */
        val Signature = Setup(fullMatrix = true, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, weaponOrder = BotWeapons)

        /**
         * The strong build: the signature Arc Coil, a Charge-key Relic in harmony with it and three proven
         * Relics bound from the start, every Matrix rank.
         */
        val Strong = Setup(
            fullMatrix = true, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, weaponOrder = BotWeapons,
            relics = listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN),
        )
    }
}
