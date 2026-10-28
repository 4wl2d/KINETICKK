// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SteeringTuning
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.random.Random
import kotlin.test.Test

/**
 * The terrain lane's calibration probe, not a gate: it returns at once unless `KK_TERRAIN_PROBE=first..last` names a
 * calibration window, then plays the seeds `i * 7_919 + 13` for i in it and prints one line per run and a summary
 * per arm. It never plays the gate population (i = 1,001..1,300). `KK_TERRAIN_STEERING=name=value,...` overrides the
 * shipped flight-law knobs for the probed arms.
 *
 * The arms are Gate A's fresh tier-0 run (the default NoPoi bot, the Orb and the starter weapon) and Gate D's strong
 * build at tier 10, both played by [playGateRun] for up to 900 s. Per run: outcome, survival, obstacle contacts (the
 * frame the Core first touches a rock or bumper, sampled every 0.02 s input frame, as in SingularityContactGateTest),
 * hurting obstacle hits (the collision cooldown restarts), ricochets, ambushes planned, Overdrive activations, Matter
 * and whether a singularity contact ended it. Summaries carry bootstrap standard errors (20,000 resamples of the runs,
 * RNG seed 20261027).
 */
class TerrainCalibrationProbeTest {
    @Test
    fun probeTheCalibrationWindow() {
        val window = System.getenv("KK_TERRAIN_PROBE")?.takeIf { it.isNotBlank() } ?: return
        val (first, last) = window.split("..").map { it.trim().toInt() }
        require(first in 1..last) { "KK_TERRAIN_PROBE must be first..last, got $window" }
        require(last < GATE_FIRST || first > GATE_LAST) { "the gate population ($GATE_FIRST..$GATE_LAST) is never probed" }
        val seeds = (first..last).map { it * 7_919 + 13 }
        val content = probedContent(System.getenv("KK_TERRAIN_STEERING").orEmpty())
        println("TERRAIN PROBE window $first..$last steering ${content.tempo.steering}")
        for (arm in Arm.entries) {
            val runs = populationOf(seeds) { seed -> play(seed, arm, content) }
            runs.forEach { println("TERRAIN PROBE ${arm.label} $it") }
            println("TERRAIN PROBE SUMMARY ${arm.label} ${summary(runs)}")
        }
    }

    private enum class Arm(val label: String) { FRESH_TIER_0("fresh-t0"), STRONG_TIER_10("strong-t10") }

    private class Run(
        val seed: Int,
        val outcome: String,
        val seconds: Float,
        val singularity: Boolean,
        val contacts: Int,
        val hurts: Int,
        val ricochets: Int,
        val ambushes: Int,
        val overdrives: Int,
        val matter: Long,
    ) {
        val won: Boolean get() = outcome == "WIN"

        override fun toString(): String =
            "seed=$seed outcome=$outcome seconds=$seconds singularity=${if (singularity) "y" else "n"} contacts=$contacts " +
                "hurts=$hurts ricochets=$ricochets ambushes=$ambushes overdrives=$overdrives matter=$matter"
    }

    private fun play(seed: Int, arm: Arm, content: GameplayContentSnapshot): Run {
        var contacts = 0
        var touching = false
        var hurts = 0
        var hitReadyAt = 0f
        var ricochets = 0
        var pending = 0
        var ambushes = 0
        var ambushReadyAt = Float.NaN
        var overdrives = 0
        var overdriveBefore = 0f
        val observe = { state: MutableGameState ->
            val touches = state.obstacleField.obstacles.any { obstacle ->
                val reach = obstacle.radius + state.coreRadius + CONTACT_SLACK
                (obstacle.x - state.coreX) * (obstacle.x - state.coreX) +
                    (obstacle.y - state.coreY) * (obstacle.y - state.coreY) <= reach * reach
            }
            if (touches && !touching) contacts++
            touching = touches
            if (state.obstacleHitReadyAt > hitReadyAt) hurts++
            hitReadyAt = state.obstacleHitReadyAt
            ricochets += if (state.pendingRicochets >= pending) state.pendingRicochets - pending else state.pendingRicochets
            pending = state.pendingRicochets
            if (!ambushReadyAt.isNaN() && state.ambushReadyAt > ambushReadyAt) ambushes++
            ambushReadyAt = state.ambushReadyAt
            if (overdriveBefore <= 0f && state.overdriveTime > 0f) overdrives++
            overdriveBefore = state.overdriveTime
        }
        val state = when (arm) {
            Arm.FRESH_TIER_0 -> playGateRun(seed, gateProfile(GateMatrix.NONE, tier = 0, content = content), NoPoiBot(),
                content = content, observe = observe)
            Arm.STRONG_TIER_10 -> playGateRun(
                seed,
                gateProfile(
                    GateMatrix.FULL, tier = 10, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, content = content,
                ),
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
        return Run(seed, outcome, state.elapsed, state.phase == GamePhase.GAME_OVER && state.message == SINGULARITY_CONTACT,
            contacts, hurts, ricochets, ambushes, overdrives, state.runMatter)
    }

    /** Point values of the arm's metrics with bootstrap standard errors over its runs. */
    private fun summary(runs: List<Run>): String {
        val metrics = listOf<Pair<String, (List<Run>) -> Double>>(
            "winShare" to { sample -> sample.count { it.won }.toDouble() / sample.size },
            "medianSurvival" to { sample -> sample.map { it.seconds }.gateMedian().toDouble() },
            "singularityShare" to { sample -> sample.count { it.singularity }.toDouble() / sample.size },
            "contactsPerSecond" to { sample -> sample.sumOf { it.contacts }.toDouble() / sample.sumOf { it.seconds.toDouble() } },
            "hurtsPerMinute" to { sample -> sample.sumOf { it.hurts } * 60.0 / sample.sumOf { it.seconds.toDouble() } },
            "ricochetsPerMinute" to { sample -> sample.sumOf { it.ricochets } * 60.0 / sample.sumOf { it.seconds.toDouble() } },
            "ambushesPerRun" to { sample -> sample.sumOf { it.ambushes }.toDouble() / sample.size },
            "overdrivesPerRun" to { sample -> sample.sumOf { it.overdrives }.toDouble() / sample.size },
            "meanMatter" to { sample -> sample.sumOf { it.matter }.toDouble() / sample.size },
        )
        val random = Random(BOOTSTRAP_SEED)
        val resampled = List(metrics.size) { DoubleArray(BOOTSTRAP_RESAMPLES) }
        repeat(BOOTSTRAP_RESAMPLES) { resample ->
            val sample = List(runs.size) { runs[random.nextInt(runs.size)] }
            metrics.forEachIndexed { index, (_, metric) -> resampled[index][resample] = metric(sample) }
        }
        return "n=${runs.size} " + metrics.mapIndexed { index, (name, metric) ->
            val values = resampled[index]
            val mean = values.average()
            val se = kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
            "$name=${"%.4f".format(metric(runs))}(SE ${"%.4f".format(se)})"
        }.joinToString(" ")
    }

    /** The shipped content with the flight-law knobs of [overrides] (`name=value`, comma-separated). */
    private fun probedContent(overrides: String): GameplayContentSnapshot {
        var steering = canonicalGameplayContent.tempo.steering
        overrides.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
            val (name, text) = entry.split('=').map { it.trim() }
            steering = steering.with(name, text.toFloat())
        }
        return canonicalGameplayContent.copy(tempo = canonicalGameplayContent.tempo.copy(steering = steering))
    }

    private fun SteeringTuning.with(name: String, value: Float): SteeringTuning = when (name) {
        "basePull" -> copy(basePull = value)
        "counterThrustGate" -> copy(counterThrustGate = value)
        "counterThrustDeadzoneReach" -> copy(counterThrustDeadzoneReach = value)
        "gripGain" -> copy(gripGain = value)
        "gripFullReach" -> copy(gripFullReach = value)
        "slingShare" -> copy(slingShare = value)
        "slingHeadroom" -> copy(slingHeadroom = value)
        "slingKneeShare" -> copy(slingKneeShare = value)
        "spoolGain" -> copy(spoolGain = value)
        "straightDrag" -> copy(straightDrag = value)
        "straightCurvatureRate" -> copy(straightCurvatureRate = value)
        "straightRiseSeconds" -> copy(straightRiseSeconds = value)
        "straightFallSeconds" -> copy(straightFallSeconds = value)
        "maximumStatMultiplier" -> copy(maximumStatMultiplier = value)
        else -> error("unknown steering knob $name")
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
