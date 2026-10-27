// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.isDataEconomy
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Level 42 is the goal of a maximal Data build at every Rebirth tier (0.5.0's capped Data curve, economy §2.1). Every
 * arm is a full run of the NoPoi bot on the gate harness; the bot's [DataPolicy] decides how it drafts Data-gain cards.
 *
 * - FRESH: a fresh profile reaches level 22-30 at 12 active minutes (a population gate, see its KDoc).
 * - DATA_MAX: a full Matrix (lifetime Matter = its price, so the whole catalog is offered from level 1) that
 *   seeks Data reaches 42 between 510 and 630 s.
 * - NO_DATA: the same profile avoiding Data never reaches 42 before 720 s; NO_DATA_MAXED adds Refit 10 and
 *   Honing 10 on every owned weapon (upgrades are not a Data source).
 * - Rebirth tiers 3 and 6: the same two arms keep their meaning (NO_DATA never before 720 s, DATA_MAX in 480-660 s
 *   on at least three of five seeds).
 *
 * The thresholds never move; the re-tune ladder of economy §2.1 (rampPlateau, topPlateau, the §1.5 Data levers, the
 * Rebirth Data requirement scale) is the only way to make an arm pass. FRESH alone was restated as a population gate
 * (0.5.0 active items; policy.md, "Balance gate populations"), anchored to the 05885fa9 base; the other arms passed
 * their seeds with probability 0.92 (DATA_MAX tier 0), 0.96 (DATA_MAX tiers 3 and 6) and 1.00 (every NO_DATA arm) at
 * the base.
 */
class RunLevelCalibrationGateTest {
    /**
     * FRESH over the population: on the 05885fa9 base 214 of the 300 [PopulationSeeds] runs were alive at 720 s (0.713,
     * SE 0.026) and 6 of those 214 stood outside level 22-30 (all at 31: 0.028, SE 0.011), so the eleven fixed seeds
     * (2026 and the probe i = 1..10) met both rules with probability 0.27. At least [FRESH_MIN_ALIVE_SHARE] of the
     * population must be alive at 720 s: by the population threshold rule the lower of the seeded rule's 9 of 11 (0.82)
     * and the base share minus two standard errors (0.66). The 0.5.0 accepted fresh tier-0 survival of 0.73 is not a
     * floor here: the base itself reads 0.713 on these seeds (0.715 on 0.5.0's 400 audit seeds), so it recorded a point
     * estimate of the base. At most [FRESH_MAX_OUTSIDE_SHARE] of the alive runs may stand outside level 22-30: the
     * higher of the seeded rule's 0 and the base share plus two standard errors, rounded up.
     */
    @Test fun aFreshNoPoiBotIsAtLevelTwentyTwoToThirtyAfterTwelveActiveMinutes() {
        val runs = populationOf(PopulationSeeds) { seed ->
            play("FRESH", seed, tier = 0, profile = gateProfile(GateMatrix.NONE, tier = 0), bot = NoPoiBot())
        }
        val alive = runs.filter { it.levelAtTwelveMinutes != null }
        val outside = alive.filter { (it.levelAtTwelveMinutes ?: 0) !in 22..30 }
        val aliveShare = runs.shareOf { it.levelAtTwelveMinutes != null }
        val outsideShare = alive.shareOf { (it.levelAtTwelveMinutes ?: 0) !in 22..30 }
        println("XP_GATE FRESH population alive at 720 s ${alive.size} of ${runs.size} (${"%.3f".format(aliveShare)}), outside 22-30 " +
            "${outside.size} (${"%.3f".format(outsideShare)}): ${outside.summary()}")
        assertTrue(aliveShare >= FRESH_MIN_ALIVE_SHARE,
            "at least $FRESH_MIN_ALIVE_SHARE of the fresh runs must be alive at 720 s: ${alive.size} of ${runs.size}")
        assertTrue(outsideShare <= FRESH_MAX_OUTSIDE_SHARE,
            "at most $FRESH_MAX_OUTSIDE_SHARE of the fresh runs alive at 720 s may stand outside level 22-30: ${outside.summary()}")
    }

    @Test fun aFullMatrixThatSeeksDataReachesTheCapBetweenEightAndAHalfAndTenAndAHalfMinutes() {
        val runs = EconomyGateSeeds.map { seed -> play("DATA_MAX", seed, tier = 0, profile = fullMatrix(tier = 0), bot = dataBot(DataPolicy.SEEK)) }
        report("XP_GATE DATA_MAX tier=0", runs)
        assertTrue(runs.count { it.capAt != null && it.capAt in 510f..630f } >= 4,
            "a Data-max build must reach level 42 in 510-630 s on at least 4 of 5 seeds: ${runs.summary()}")
    }

    @Test fun aFullMatrixThatAvoidsDataNeverReachesTheCapBeforeTwelveMinutes() {
        val runs = EconomyGateSeeds.map { seed -> play("NO_DATA", seed, tier = 0, profile = fullMatrix(tier = 0), bot = dataBot(DataPolicy.AVOID)) }
        report("XP_GATE NO_DATA tier=0", runs)
        assertTrue(runs.none { it.capAt != null && it.capAt < 720f }, "without Data gain level 42 must wait for 720 s: ${runs.summary()}")
        assertTrue(runs.count { it.avoidBreaches == 0 } >= 4,
            "the avoiding bot may be forced to take Data on at most one seed: ${runs.summary()}")
    }

    @Test fun maxedUpgradesAreNotADataSource() {
        val weapon = WeaponId.FLUX_WAKE
        val upgrades = gateUpgrades(CoreShape.ORB, refit = 10, weapons = gateOwnedWeapons(weapon), honing = 10)
        val runs = EconomyGateSeeds.map { seed ->
            play("NO_DATA_MAXED", seed, tier = 0, profile = fullMatrix(tier = 0).copy(upgrades = upgrades), bot = dataBot(DataPolicy.AVOID))
        }
        report("XP_GATE NO_DATA_MAXED tier=0", runs)
        assertTrue(runs.none { it.capAt != null && it.capAt < 720f },
            "Refit and Honing must not bring level 42 before 720 s without Data gain: ${runs.summary()}")
    }

    @Test fun atRebirthTiersThreeAndSixAvoidingDataStillNeverReachesTheCapBeforeTwelveMinutes() {
        val outside = mutableListOf<String>()
        for (tier in listOf(3, 6)) {
            val runs = EconomyGateSeeds.map { seed -> play("NO_DATA", seed, tier, fullMatrix(tier), dataBot(DataPolicy.AVOID)) }
            report("XP_GATE NO_DATA tier=$tier", runs)
            if (runs.any { it.capAt != null && it.capAt < 720f }) outside += "tier $tier: ${runs.summary()}"
        }
        assertTrue(outside.isEmpty(), "without Data gain level 42 must wait for 720 s at every tier: $outside")
    }

    @Test fun atRebirthTiersThreeAndSixSeekingDataStillReachesTheCapInTheWindow() {
        val outside = mutableListOf<String>()
        for (tier in listOf(3, 6)) {
            val runs = EconomyGateSeeds.map { seed -> play("DATA_MAX", seed, tier, fullMatrix(tier), dataBot(DataPolicy.SEEK)) }
            report("XP_GATE DATA_MAX tier=$tier", runs)
            if (runs.count { it.capAt != null && it.capAt in 480f..660f } < 3) outside += "tier $tier: ${runs.summary()}"
        }
        assertTrue(outside.isEmpty(),
            "a Data-max build must reach level 42 in 480-660 s on at least 3 of 5 seeds at tiers 3 and 6: $outside")
    }

    /** One measured run: the level at the first step at or after 720 s (null when it ended before) and the cap time. */
    private data class LevelRun(
        val arm: String,
        val tier: Int,
        val seed: Int,
        val won: Boolean,
        val seconds: Float,
        val reason: String,
        val levelAtTwelveMinutes: Int?,
        val capAt: Float?,
        val dataAfterCap: Long,
        val avoidBreaches: Int,
        val minutes: List<String>,
    ) {
        override fun toString(): String = "$seed:${if (won) "WIN" else "LOSS"}@${seconds.toInt()}s" +
            "(L720=${levelAtTwelveMinutes ?: "-"} t42=${capAt?.let { (it * 10f).toInt() / 10f } ?: "-"}" +
            (if (avoidBreaches > 0) " breaches=$avoidBreaches" else "") + (if (won) "" else " $reason") + ")"
    }

    private fun List<LevelRun>.summary(): String = joinToString(" ")

    private fun fullMatrix(tier: Int): GameplayProfileSnapshot =
        gateProfile(GateMatrix.FULL, tier, economy = PlayerEconomy(0L, fullMatrixPrice()))

    private fun dataBot(policy: DataPolicy) =
        NoPoiBot(archetypeTag = BuildTag.FORTUNE, element = StatusElement.ENTROPY, dataPolicy = policy)

    private fun play(arm: String, seed: Int, tier: Int, profile: GameplayProfileSnapshot, bot: NoPoiBot): LevelRun {
        val content = canonicalGameplayContent
        val cap = content.tempo.maximumLevel
        var levelAtTwelveMinutes: Int? = null
        var capAt: Float? = null
        var dataAtCap = 0L
        var minute = 1
        val minutes = mutableListOf<String>()
        val state = playGateRun(seed, profile, bot) { state ->
            if (capAt == null && state.level >= cap) {
                capAt = state.elapsed
                dataAtCap = state.dataCollected
            }
            if (levelAtTwelveMinutes == null && state.elapsed >= 720f) levelAtTwelveMinutes = state.level
            if (state.elapsed >= minute * 60f) {
                val dataItems = content.items.filter { it.isDataEconomy && state.itemStacks[it.id] > 0 }
                    .joinToString(",") { "${it.name}x${state.itemStacks[it.id]}" }
                minutes += "m$minute L${state.level} D${state.dataCollected} g${(state.dataGain * 100f).toInt() / 100f} " +
                    "k${state.kills} M${state.runMatter} data=[$dataItems]"
                minute++
            }
        }
        val run = LevelRun(arm, tier, seed, state.phase == GamePhase.VICTORY, state.elapsed, state.message,
            levelAtTwelveMinutes, capAt, if (capAt == null) 0L else state.dataCollected - dataAtCap, bot.avoidBreaches, minutes)
        println("XP_RUN arm=$arm tier=$tier seed=$seed " + run.minutes.joinToString(" | ") +
            " | END ${state.phase} ${state.elapsed.toInt()}s L${state.level} D${state.dataCollected} M${state.runMatter}" +
            " t42=${capAt ?: "-"} dataAfter42=${run.dataAfterCap} breaches=${bot.avoidBreaches}")
        return run
    }

    private fun report(label: String, runs: List<LevelRun>) = println("$label ${runs.summary()}")

    private companion object {
        /**
         * FRESH: the least share of [PopulationSeeds] runs alive at 720 s (base 0.713, SE 0.026, n 300; the population
         * threshold rule).
         */
        const val FRESH_MIN_ALIVE_SHARE = 0.66

        /** FRESH: the largest share of the alive runs outside level 22-30 at 720 s (base 0.028, SE 0.011). */
        const val FRESH_MAX_OUTSIDE_SHARE = 0.06
    }
}
