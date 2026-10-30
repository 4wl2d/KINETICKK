// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test

/**
 * The obstacle kind mix check, not a gate: it returns at once unless `KK_OBSTACLE_MIX_PROBE=first..last` names a
 * calibration window (the terrain kinds own i = 1..300), then plays the seeds `i * 7_919 + 13` for i in it. It never
 * plays the gate population (i = 1,001..1,300).
 *
 * Each arm (Gate A's fresh tier-0 run and Gate D's strong tier-10 build, both the NoPoi bot for up to 900 s) plays every
 * seed twice: with realspace's mix empty (the 0.5.0 field of asteroids and bumpers on the same layout) and with the
 * shipped mix. Per run: outcome, survival, whether a singularity contact or an obstacle collision ended it, solid
 * contacts (the frame the Core first touches a body that is solid at that time), hurting hits (the frames a hit restarts
 * the collision cooldown), gate and vent boosts (a gate or vent rests after lifting the Core) with the largest share of
 * the lethal speed the Core held at the end of such a frame (and of those frames it began below the boost cap, which
 * reads the cap in play: steering inside the 0.02 s frame may add a little), obstacles broken (new ids in the destroyed
 * memory: crystals and mines, unstable asteroids in Fractured Space) and Matter. Summaries carry bootstrap standard errors (20,000 resamples of the seeds, RNG seed 20261027) and the paired
 * difference shipped − empty with its own.
 */
class ObstacleMixProbeTest {
    @Test
    fun probeTheMixOnTheCalibrationWindow() {
        val window = System.getenv("KK_OBSTACLE_MIX_PROBE")?.takeIf { it.isNotBlank() } ?: return
        val (first, last) = window.split("..").map { it.trim().toInt() }
        require(first in 1..last) { "KK_OBSTACLE_MIX_PROBE must be first..last, got $window" }
        require(last < GATE_FIRST || first > GATE_LAST) { "the gate population ($GATE_FIRST..$GATE_LAST) is never probed" }
        val seeds = (first..last).map { it * 7_919 + 13 }
        val shipped = canonicalGameplayContent
        val empty = shipped.copy(obstacles = shipped.obstacles.copy(kinds = shipped.obstacles.kinds.copy(space = ObstacleMix())))
        println("OBSTACLE MIX PROBE window $first..$last space ${shipped.obstacles.kinds.space}")
        for (arm in Arm.entries) {
            val without = populationOf(seeds) { seed -> play(seed, arm, empty) }
            val with = populationOf(seeds) { seed -> play(seed, arm, shipped) }
            without.forEach { println("OBSTACLE MIX PROBE ${arm.label} empty $it") }
            with.forEach { println("OBSTACLE MIX PROBE ${arm.label} shipped $it") }
            println("OBSTACLE MIX PROBE SUMMARY ${arm.label} empty ${summary(without)}")
            println("OBSTACLE MIX PROBE SUMMARY ${arm.label} shipped ${summary(with)}")
            println("OBSTACLE MIX PROBE DELTA ${arm.label} shipped-empty ${delta(without, with)}")
        }
    }

    private enum class Arm(val label: String) { FRESH_TIER_0("fresh-t0"), STRONG_TIER_10("strong-t10") }

    private class Run(
        val seed: Int,
        val outcome: String,
        val seconds: Float,
        val singularity: Boolean,
        val obstacleKill: Boolean,
        val contacts: Int,
        val hurts: Int,
        val boosts: Int,
        val boostShare: Float,
        val boostRiseShare: Float,
        val broken: Int,
        val matter: Long,
    ) {
        val won: Boolean get() = outcome == "WIN"

        override fun toString(): String =
            "seed=$seed outcome=$outcome seconds=$seconds singularity=${if (singularity) "y" else "n"} " +
                "obstacleKill=${if (obstacleKill) "y" else "n"} contacts=$contacts hurts=$hurts boosts=$boosts " +
                "boostShare=${"%.3f".format(boostShare)} boostRiseShare=${"%.3f".format(boostRiseShare)} broken=$broken matter=$matter"
    }

    private fun play(seed: Int, arm: Arm, content: GameplayContentSnapshot): Run {
        val kinds = content.obstacles.kinds
        var contacts = 0
        var touching = false
        var hurts = 0
        var hitReadyAt = 0f
        var boosts = 0
        var boostShare = 0f
        var boostRiseShare = 0f
        var speedBefore = 0f
        val restingUntil = HashMap<Long, Float>()
        var destroyed: List<Long> = emptyList()
        val broken = HashSet<Long>()
        val observe = { state: MutableGameState ->
            var touches = false
            var boosted = false
            for (obstacle in state.obstacleField.obstacles) {
                val dx = obstacle.x - state.coreX
                val dy = obstacle.y - state.coreY
                if (!touches && obstacle.solidAt(state.elapsed, kinds)) {
                    val reach = obstacle.radius + state.coreRadius + CONTACT_SLACK
                    touches = dx * dx + dy * dy <= reach * reach
                }
                if (obstacle.kind == ObstacleKind.ACCEL_GATE || obstacle.kind == ObstacleKind.THERMAL_VENT) {
                    val before = restingUntil.put(obstacle.id, obstacle.ricochetReadyAt)
                    if (before != null && obstacle.ricochetReadyAt > before) {
                        boosts++
                        boosted = true
                    }
                }
            }
            if (touches && !touching) contacts++
            touching = touches
            val lethal = state.obstacleLethalSpeed()
            if (boosted) {
                boostShare = max(boostShare, state.speed / lethal)
                if (speedBefore < lethal * kinds.boostLethalShare) boostRiseShare = max(boostRiseShare, state.speed / lethal)
            }
            speedBefore = state.speed
            if (state.obstacleHitReadyAt > hitReadyAt) hurts++
            hitReadyAt = state.obstacleHitReadyAt
            if (state.destroyedObstacleIds !== destroyed) {
                destroyed = state.destroyedObstacleIds
                broken.addAll(destroyed)
            }
        }
        val state = when (arm) {
            Arm.FRESH_TIER_0 -> playGateRun(seed, gateProfile(GateMatrix.NONE, tier = 0, content = content), NoPoiBot(),
                content = content, observe = observe)
            Arm.STRONG_TIER_10 -> playGateRun(
                seed,
                gateProfile(GateMatrix.FULL, tier = 10, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, content = content),
                NoPoiBot(),
                relics = StrongRelics,
                content = content,
                observe = observe,
            )
        }
        val outcome = when (state.phase) {
            GamePhase.VICTORY -> "WIN"
            GamePhase.GAME_OVER -> "LOSS"
            else -> "TIMEOUT"
        }
        val ended = state.phase == GamePhase.GAME_OVER
        return Run(seed, outcome, state.elapsed, ended && state.message == SINGULARITY_CONTACT,
            ended && state.message == OBSTACLE_COLLISION_REASON, contacts, hurts, boosts, boostShare, boostRiseShare,
            broken.size, state.runMatter)
    }

    private val metrics = listOf<Pair<String, (List<Run>) -> Double>>(
        "winShare" to { sample -> sample.count { it.won }.toDouble() / sample.size },
        "medianSurvival" to { sample -> sample.map { it.seconds }.gateMedian().toDouble() },
        "singularityShare" to { sample -> sample.count { it.singularity }.toDouble() / sample.size },
        "obstacleKillShare" to { sample -> sample.count { it.obstacleKill }.toDouble() / sample.size },
        "contactsPerSecond" to { sample -> sample.sumOf { it.contacts }.toDouble() / sample.sumOf { it.seconds.toDouble() } },
        "hurtsPerMinute" to { sample -> sample.sumOf { it.hurts } * 60.0 / sample.sumOf { it.seconds.toDouble() } },
        "boostsPerMinute" to { sample -> sample.sumOf { it.boosts } * 60.0 / sample.sumOf { it.seconds.toDouble() } },
        "maxBoostShare" to { sample -> sample.maxOf { it.boostShare }.toDouble() },
        "maxBoostRiseShare" to { sample -> sample.maxOf { it.boostRiseShare }.toDouble() },
        "brokenPerRun" to { sample -> sample.sumOf { it.broken }.toDouble() / sample.size },
        "meanMatter" to { sample -> sample.sumOf { it.matter }.toDouble() / sample.size },
    )

    /** Point values of the arm's metrics with bootstrap standard errors over its runs. */
    private fun summary(runs: List<Run>): String {
        val random = Random(BOOTSTRAP_SEED)
        val resampled = List(metrics.size) { DoubleArray(BOOTSTRAP_RESAMPLES) }
        repeat(BOOTSTRAP_RESAMPLES) { resample ->
            val sample = List(runs.size) { runs[random.nextInt(runs.size)] }
            metrics.forEachIndexed { index, (_, metric) -> resampled[index][resample] = metric(sample) }
        }
        return "n=${runs.size} " + metrics.mapIndexed { index, (name, metric) ->
            "$name=${"%.4f".format(metric(runs))}(SE ${"%.4f".format(standardError(resampled[index]))})"
        }.joinToString(" ")
    }

    /** Shipped minus empty for every metric, resampling the seeds in pairs (both arms play the same seeds). */
    private fun delta(without: List<Run>, with: List<Run>): String {
        val random = Random(BOOTSTRAP_SEED)
        val resampled = List(metrics.size) { DoubleArray(BOOTSTRAP_RESAMPLES) }
        repeat(BOOTSTRAP_RESAMPLES) { resample ->
            val picks = IntArray(without.size) { random.nextInt(without.size) }
            val a = picks.map { without[it] }
            val b = picks.map { with[it] }
            metrics.forEachIndexed { index, (_, metric) -> resampled[index][resample] = metric(b) - metric(a) }
        }
        return metrics.mapIndexed { index, (name, metric) ->
            "$name=${"%+.4f".format(metric(with) - metric(without))}(SE ${"%.4f".format(standardError(resampled[index]))})"
        }.joinToString(" ")
    }

    private fun standardError(values: DoubleArray): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }

    private companion object {
        /** Gate D's strong build: Stormheart Capacitor in key harmony with Arc Coil and three proven Relics. */
        val StrongRelics =
            listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN)
        const val GATE_FIRST = 1_001
        const val GATE_LAST = 1_300
        const val CONTACT_SLACK = 2f
        const val SINGULARITY_CONTACT = "SINGULARITY CONTACT"
        const val BOOTSTRAP_RESAMPLES = 20_000
        const val BOOTSTRAP_SEED = 20_261_027
    }
}
