// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.WeaponSynergyProjection
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rebirth difficulty gates (SPEC 11.2), played by the input-only NoPoi bot on one fixed seed set: the
 * second half of the NoPoi probe (`i * 7_919 + 13`, i = 6..10), chosen by its definition, never by
 * outcome. The weapons are the reworked 24 with their synergy rules; the gates read the weapon synergy
 * projection to confirm that the Pentagon's signature bonus (and, for the strong build, Stormheart
 * Capacitor's Charge-key harmony with Arc Coil) is active at the start and when the Architect arrives.
 *
 * A-D are SPEC 11.2's bounds; H (0.5.0) holds D's bounds for the strong build seeking Data with the Apex, and I keeps
 * skills from trivialising tier 0. E and F require the difficulty to rise with the tier for a partly
 * upgraded profile and for the strong build, so a curve where the tier stops mattering once the Matrix
 * is bought fails. D's upper bound and G keep tier 10 an achievement: the strong build clears it in at
 * most three seeds of five and a full Matrix with the Pentagon and Arc Coil never in all five.
 *
 * Retuned after the weapon rework, which left E failing (the half Matrix won tier 8 in 2/5) and D's
 * new upper bound on its edge (3/5): enemy integrity's quadratic tier term rose from 0.035 to 0.05 and
 * the Architect's per-hit cap shrinks by 2 % of its tier-0 share per tier. Each of the nine
 * combinations of a quadratic term of 0.048, 0.05 or 0.052 with a cap shrink of 1.5, 2 or 2.5 % per
 * tier passed every check on this seed set. The term alone passed at 0.048, 0.05, 0.051, 0.055 and 0.06
 * but failed at 0.042, 0.046, 0.047, 0.049 and 0.052 (mostly D's upper bound at 4/5); a cap shrink
 * alone (3 or 4 % per tier) left E failing or passing only through a 900 s timeout. The R10 enrage
 * rate (1.45, probed up to 4.5) did not move the outcomes and stays.
 *
 * Measured with the shipped tuning (seed:outcome@seconds; a "fight" is the Architect's arrival at
 * 720 s to the Victory; a run still fighting at 900 s counts as a loss), re-measured after the final
 * weapon balance (Ion Swarm, second in the bot's weapon order, at 4.5 per shot; Prism Relay's twin at
 * 20 % and its third form bursting 12 % within 60 without an extra bounce; the Flux Wake trail sharing
 * its heat above six enemies); no gate flipped and no difficulty knob moved:
 * - A, tier 0 fresh: 47527 win@780, 55446 win@800, 63365 win@800, 71284 win@786, 79203 win@764 (5/5).
 * - B, tier 3 full Matrix + Pentagon/Arc Coil: all five win (fights 71, 49, 47, 70, 78 s).
 * - C, tier 6 fresh: all five lose within 18 s (the bot rams the larger opening at low speed).
 * - D, tier 10 strong build: 47527 win@850, 55446 loss@469, 63365 loss@887 (an obstacle collision in the
 *   fight), 71284 loss@83, 79203 still fighting at 900 (1/5 wins, median 850 s).
 * - E, half Matrix (Orb, starter weapon): tier 2 wins 5/5 (fights 40-86 s); tier 8 wins none: 55446 is
 *   still fighting at 900 s, the others lose at 13, 23, 44 and 475 s. The same profile wins 5/5 at
 *   tier 2, 4/5 at 3, 3/5 at 4, 5/5 at 5, 3/5 at 6 and none at 7-9.
 * - F, strong build fights: tier 3 median 74 s (74, 75, 52, 74, 62); tier 10 median never (one clear
 *   in 130 s, four losses).
 * - G, tier 10 full Matrix + Pentagon/Arc Coil: 55446 wins (fight 143 s); 47527, 63365 and 79203 are
 *   still fighting at 900 s and 71284 loses at 351 s (1/5).
 * Fresh profiles win 5/5 at tier 1 and none from tier 2. Before the retune (reworked weapons, old
 * tuning) the strong build won tier 10 3/5 (losses at 83 and 85 s), the full-Matrix Pentagon 4/5 and
 * the half Matrix tier 8 2/5.
 */
class RebirthBalanceGateTest {
    @Test fun aFreshNoPoiBotStillWinsTierZero() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 0, setup = Fresh) }
        report("GATE A tier=0 fresh", outcomes)
        assertTrue(outcomes.count { it.won } >= 4, "tier 0 must stay winnable: ${outcomes.summary()}")
    }

    /**
     * Gate I (0.5.0 skills): skills help a fresh tier-0 run but do not trivialise it. The same bot with no skill slot in
     * reach of the run clears the Architect no more than a fifth slower than with its drafted, cast skills.
     *
     * Measured on the NoPoi probe sequence i = 1..31 ([GateISeeds]), not the five gate seeds: a run that dies counts as
     * never clearing, and about a quarter of fresh runs die with or without skills, so a five-seed median lands on
     * whichever arm lost two seeds (the five seeds read 0.37–0.49 while 400 seeds read 0.78–0.83, and five-seed blocks
     * of those 400 passed in 53 %).
     */
    @Test fun skillsHelpAFreshTierZeroRunWithoutTrivialisingIt() {
        val withSkills = GateISeeds.map { seed -> play(seed, tier = 0, setup = Fresh) }
        val without = GateISeeds.map { seed -> play(seed, tier = 0, setup = Fresh, content = NoSkillSlots) }
        report("GATE I tier=0 fresh skills", withSkills)
        report("GATE I tier=0 fresh no-skills", without)
        val skilled = withSkills.medianClear()
        val unskilled = without.medianClear()
        println("GATE I median clear skills=$skilled no-skills=$unskilled ratio=${skilled / unskilled}")
        assertTrue(skilled >= unskilled * 0.8f,
            "skills must not trivialise tier 0: median clear $skilled s against $unskilled s without skills")
    }

    @Test fun aFullMatrixAndASignatureWeaponCanWinTierThree() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 3, setup = Signature) }
        report("GATE B tier=3 matrix+signature", outcomes)
        assertSignatureAndHarmony(outcomes, harmony = null)
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
        assertSignatureAndHarmony(low + high, harmony = RelicId.STORMHEART_CAPACITOR)
        val lowClear = low.medianClear()
        val highClear = high.medianClear()
        assertTrue(highClear >= lowClear * 1.3f,
            "tier 10 must take the strong build clearly longer than tier 3: median clear $highClear s vs $lowClear s, " +
                "${high.summary()} / ${low.summary()}")
    }

    @Test fun aStrongBuildSurvivesEightMinutesOfTierTenButRarelyClearsIt() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 10, setup = Strong) }
        report("GATE D tier=10 strong", outcomes)
        assertSignatureAndHarmony(outcomes, harmony = RelicId.STORMHEART_CAPACITOR)
        val median = outcomes.map { it.seconds }.sorted()[outcomes.size / 2]
        assertTrue(median >= 480f, "tier 10 must be survivable for a strong build: median $median s, ${outcomes.summary()}")
        assertTrue(outcomes.count { it.won } <= 3, "tier 10 must stay an achievement for the strong build: ${outcomes.summary()}")
    }

    /**
     * Gate H (0.5.0 Apex, combat-final §10): the strong build seeking Data at tier 10 — every Matrix rank and lifetime
     * Matter equal to its price, so every rank-0 Data item can be offered; the bot takes Data cards first, drafts Decay
     * Spores and Gravity Well, reaches level 42 soonest and casts the Apex as its post-cap Data recharges it. D's bounds
     * still hold: it survives a median of eight minutes and clears in at most three seeds of five.
     */
    @Test fun aStrongBuildSeekingDataWithTheApexStillFindsTierTenHard() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 10, setup = Strong, seekData = true) }
        report("GATE H tier=10 strong+data", outcomes)
        assertSignatureAndHarmony(outcomes, harmony = RelicId.STORMHEART_CAPACITOR)
        val median = outcomes.map { it.seconds }.sorted()[outcomes.size / 2]
        assertTrue(median >= 480f, "tier 10 must stay survivable for a Data-max strong build: median $median s, ${outcomes.summary()}")
        assertTrue(outcomes.count { it.won } <= 3, "the Apex must not make tier 10 a formality: ${outcomes.summary()}")
    }

    @Test fun aFullMatrixPentagonWithArcCoilCannotClearTierTenEveryTime() {
        val outcomes = GateSeeds.map { seed -> play(seed, tier = 10, setup = Signature) }
        report("GATE G tier=10 matrix+signature", outcomes)
        assertSignatureAndHarmony(outcomes, harmony = null)
        assertTrue(outcomes.count { it.won } < outcomes.size, "tier 10 must not be a formality for a full Matrix: ${outcomes.summary()}")
    }

    /**
     * The synergy a setup is measured with is really live: read from the render model's weapon synergy
     * projection once the run's Relics are bound, and again when the Architect arrives (if it does).
     */
    private fun assertSignatureAndHarmony(outcomes: List<Outcome>, harmony: RelicId?) {
        outcomes.forEach { outcome ->
            outcome.synergy.forEach { (moment, synergy) ->
                assertTrue(synergy.signatureActive, "${outcome.seed} $moment: the signature bonus must be active")
                assertEquals(harmony, synergy.harmonizingRelic, "${outcome.seed} $moment: the key-harmony Relic")
            }
            assertTrue(outcome.synergy.containsKey("start"), "${outcome.seed}: the synergy was read at the start")
        }
    }

    private data class Outcome(
        val seed: Int,
        val won: Boolean,
        val seconds: Float,
        val reason: String,
        val architectAt: Float?,
        /** The weapon synergy projection at the start ("start") and at the Architect's arrival ("architect"). */
        val synergy: Map<String, WeaponSynergyProjection>,
    ) {
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
        /** The bot never binds a Relic in key harmony with its weapon, so the setup holds without harmony by construction. */
        val withoutHarmony: Boolean = false,
    )

    private fun play(
        seed: Int,
        tier: Int,
        setup: Setup,
        content: GameplayContentSnapshot = canonicalGameplayContent,
        seekData: Boolean = false,
    ): Outcome {
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
                economy = if (seekData) PlayerEconomy(0L, fullMatrixPrice(content)) else PlayerEconomy(),
                loadout = PlayerLoadout(setup.shape, setup.weapon, ReferenceArmory.toSet() + setup.weapon),
                matrix = MatrixProgress(ranks),
                collection = PlayerCollection(),
                rebirthProgress = RebirthProgress(level = tier, highestCleared = tier - 1),
            ),
        )
        state.unlockWeaponsForTesting(ReferenceArmory)
        state.startRun()
        setup.relics.forEach(state::acquireRelicForTesting)
        val synergy = linkedMapOf("start" to state.toRenderModel().weaponSynergy)
        val bot = NoPoiBot(archetypeTag = BuildTag.RAM, weaponOrder = setup.weaponOrder,
            dataPolicy = if (seekData) DataPolicy.SEEK else DataPolicy.NEUTRAL, avoidWeaponHarmony = setup.withoutHarmony)
        var architectAt: Float? = null
        while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                bot.control(state)
                state.update(0.02f)
            }
            if (architectAt == null && state.enemies.any { it.type == EnemyType.ARCHITECT }) {
                architectAt = state.elapsed
                synergy["architect"] = state.toRenderModel().weaponSynergy
            }
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        return Outcome(seed, state.phase == GamePhase.VICTORY, state.elapsed, state.message, architectAt, synergy)
    }

    private fun report(label: String, outcomes: List<Outcome>) {
        println("$label ${outcomes.summary()}")
    }

    private companion object {
        val GateSeeds: List<Int> = (6..10).map { it * 7_919 + 13 }

        /** Gate I's seeds: the NoPoi probe sequence (`i * 7_919 + 13`) for i = 1..31, which holds the gate seeds. */
        val GateISeeds: List<Int> = (1..31).map { it * 7_919 + 13 }

        /** The shipped content with every skill slot opening just below the level cap: no run here drafts a skill. */
        val NoSkillSlots: GameplayContentSnapshot = withoutReachableSkillSlots()
        val BotWeapons: List<WeaponId> = listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.ENTROPY_FIELD, WeaponId.PRISM_RELAY)

        /** No Matrix ranks, the Orb and the starter weapon. */
        val Fresh = Setup(MatrixPlan.NONE, shape = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponOrder = BotWeapons)

        /**
         * Every Matrix rank and the Pentagon with its signature Arc Coil from the start, measured without key harmony
         * (B and G check it): the bot leaves a slot hollow rather than bind a Relic carrying its weapon's Charge key. It
         * used to bind Stormheart Capacitor or Aegis Inversion when every other Relic card was one it dislikes, in about
         * one tier-3 run in fifteen, which made the harmony check a draw of the offers rather than a property of the setup.
         */
        val Signature = Setup(
            MatrixPlan.FULL, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, weaponOrder = BotWeapons, withoutHarmony = true,
        )

        /** Half the Matrix (see [halfMatrixRanks]), the Orb and the starter weapon. */
        val Half = Setup(MatrixPlan.HALF, shape = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponOrder = BotWeapons)

        /**
         * The strong build: the signature Arc Coil, a Charge-key Relic (Stormheart Capacitor) in harmony with
         * it and three proven Relics bound from the start, every Matrix rank. The gates read the weapon
         * synergy projection to confirm the signature and harmony bonuses are active.
         */
        val Strong = Setup(
            MatrixPlan.FULL, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, weaponOrder = BotWeapons,
            relics = listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN),
        )
    }
}
