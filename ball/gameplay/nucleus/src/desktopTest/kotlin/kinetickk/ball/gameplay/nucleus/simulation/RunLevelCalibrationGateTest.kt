// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Level 42 is the goal of a maximal Data build (economy §2.1, decisions §1 and Addendum A1). Every arm is a
 * full run of the NoPoi bot on the gate harness; the bot's [DataPolicy] decides how it drafts Data-gain cards.
 *
 * - FRESH: a fresh profile reaches level 22-30 at 12 active minutes.
 * - DATA_MAX: a full Matrix (lifetime Matter = its price, so the whole catalog is offered from level 1) that
 *   seeks Data reaches 42 between 510 and 630 s.
 * - NO_DATA: the same profile avoiding Data never reaches 42 before 720 s; NO_DATA_MAXED adds Refit 10 and
 *   Honing 10 on every owned weapon (upgrades are not a Data source).
 * - A1: at Rebirth tiers 3 and 6 the same two arms keep their meaning (NO_DATA never before 720 s, DATA_MAX
 *   in 480-660 s on at least three of five seeds).
 *
 * The thresholds never move; the re-tune ladder of economy §2.1 (rampPlateau, topPlateau, the §1.5 Data
 * levers, the Rebirth Data requirement scale) is the only way to make an arm pass.
 */
class RunLevelCalibrationGateTest {
    @Test fun aFreshNoPoiBotIsAtLevelTwentyTwoToThirtyAfterTwelveActiveMinutes() {
        val seeds = listOf(2026) + (1..10).map { it * 7_919 + 13 }
        val runs = seeds.map { seed -> play("FRESH", seed, tier = 0, profile = gateProfile(GateMatrix.NONE, tier = 0), bot = NoPoiBot()) }
        report("XP_GATE FRESH", runs)
        val alive = runs.filter { it.levelAtTwelveMinutes != null }
        assertTrue(alive.size >= 9, "at least 9 of 11 fresh runs must be alive at 720 s: ${runs.summary()}")
        assertTrue(alive.all { (it.levelAtTwelveMinutes ?: 0) in 22..30 },
            "every fresh run alive at 720 s must be at level 22-30: ${runs.summary()}")
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
                val dataItems = content.items.filter { it.isDataGain() && state.itemStacks[it.id] > 0 }
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
}
