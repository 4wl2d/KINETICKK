// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ObstacleTuning
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SteeringTuning
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.random.Random
import kotlin.test.Test

/**
 * The terrain lane's calibration probe, not a gate: it returns at once unless `KK_TERRAIN_PROBE=first..last` names a
 * calibration window (or several, comma-separated, e.g. `601..900,1901..2200`), then plays the seeds `i * 7_919 + 13`
 * for i in them and prints one line per run and a summary per arm (per window and pooled when there are several). It
 * never plays the gate population (i = 1,001..1,300). `KK_TERRAIN_STEERING=name=value,...` overrides the shipped
 * flight-law knobs and `KK_TERRAIN_OBSTACLES=name=value,...` the obstacle impact knobs for the probed arms;
 * `KK_TERRAIN_DASH=legacy` flies the bot's old Dash rule ([DashRule.LEGACY]) instead of the human one (`human`, the
 * default).
 *
 * The arms are Gate A's fresh tier-0 run (the default NoPoi bot, the Orb and the starter weapon) and Gate D's strong
 * build at tier 10, both played by [playGateRun] for up to 900 s. Per run: outcome, survival, obstacle contacts (the
 * frame the Core first touches a solid body, a materialised phase shard included, sampled every 0.02 s input frame, as
 * in SingularityContactGateTest), hurting obstacle hits (the frames in which a hit restarts the collision cooldown:
 * with one cooldown per surface, every landed hit, a lethal one included), ricochets, ambushes planned, Overdrive
 * activations, Matter, whether a singularity contact or an obstacle collision ended it (and whether that collision was
 * a lethal-speed hit, which leaves integrity at exactly zero), the Core's ambush-speed holds (the times it kept the
 * ambush speed for the ambush hold, planned or not) with the seconds it spent at that speed (the NoPoi bot rarely holds
 * it, so the ambush pace is read from the holds as well as the plans), the Dashes made and the message that ended a
 * lost run. Summaries carry bootstrap standard errors (20,000 resamples of the runs, RNG seed 20261027).
 */
class TerrainCalibrationProbeTest {
    @Test
    fun probeTheCalibrationWindow() {
        val probe = System.getenv("KK_TERRAIN_PROBE")?.takeIf { it.isNotBlank() } ?: return
        val windows = probe.split(',').map { window ->
            val (first, last) = window.split("..").map { it.trim().toInt() }
            require(first in 1..last) { "KK_TERRAIN_PROBE must be first..last[,first..last...], got $probe" }
            require(last < GATE_FIRST || first > GATE_LAST) { "the gate population ($GATE_FIRST..$GATE_LAST) is never probed" }
            first..last
        }
        require(windows.flatten().toSet().size == windows.sumOf { it.count() }) { "KK_TERRAIN_PROBE windows overlap: $probe" }
        val seeds = windows.flatMap { window -> window.map { it * 7_919 + 13 } }
        val label = windows.joinToString(",") { "${it.first}..${it.last}" }
        val content = probedContent(
            System.getenv("KK_TERRAIN_STEERING").orEmpty(),
            System.getenv("KK_TERRAIN_OBSTACLES").orEmpty(),
        )
        val dashRule = when (System.getenv("KK_TERRAIN_DASH")?.trim().orEmpty().lowercase()) {
            "", "human" -> DashRule.HUMAN
            "legacy" -> DashRule.LEGACY
            else -> error("KK_TERRAIN_DASH must be human or legacy, got ${System.getenv("KK_TERRAIN_DASH")}")
        }
        println("TERRAIN PROBE window $label steering ${content.tempo.steering}")
        println("TERRAIN PROBE window $label obstacles ${content.obstacles.impactKnobs()}")
        println("TERRAIN PROBE window $label dash $dashRule")
        for (arm in Arm.entries) {
            val runs = populationOf(seeds) { seed -> play(seed, arm, content, dashRule) }
            runs.forEach { println("TERRAIN PROBE ${arm.label} $it") }
            if (windows.size > 1) {
                windows.forEach { window ->
                    val inWindow = window.map { it * 7_919 + 13 }.toSet()
                    println("TERRAIN PROBE SUMMARY ${arm.label} window ${window.first}..${window.last} " +
                        summary(runs.filter { it.seed in inWindow }))
                }
            }
            println("TERRAIN PROBE SUMMARY ${arm.label} ${summary(runs)}")
        }
    }

    private enum class Arm(val label: String) { FRESH_TIER_0("fresh-t0"), STRONG_TIER_10("strong-t10") }

    private class Run(
        val seed: Int,
        val outcome: String,
        val seconds: Float,
        val singularity: Boolean,
        val obstacleKill: Boolean,
        val lethalKill: Boolean,
        val contacts: Int,
        val hurts: Int,
        val ricochets: Int,
        val ambushes: Int,
        val overdrives: Int,
        val matter: Long,
        val ambushHolds: Int,
        val ambushSeconds: Float,
        val dashes: Int,
        val end: String,
    ) {
        val won: Boolean get() = outcome == "WIN"

        override fun toString(): String =
            "seed=$seed outcome=$outcome seconds=$seconds singularity=${if (singularity) "y" else "n"} contacts=$contacts " +
                "hurts=$hurts ricochets=$ricochets ambushes=$ambushes overdrives=$overdrives matter=$matter " +
                "obstacleKill=${if (obstacleKill) "y" else "n"} lethalKill=${if (lethalKill) "y" else "n"} " +
                "ambushHolds=$ambushHolds ambushSeconds=$ambushSeconds dashes=$dashes end=$end"
    }

    private fun play(seed: Int, arm: Arm, content: GameplayContentSnapshot, dashRule: DashRule): Run {
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
        var ambushHolds = 0
        var ambushSeconds = 0f
        var heldBefore = 0f
        val observe = { state: MutableGameState ->
            val touches = state.obstacleField.obstacles.any { obstacle ->
                if (!obstacle.solidAt(state.elapsed, state.content.obstacles.kinds)) return@any false
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
            val held = state.ambushSpeedSeconds
            ambushSeconds += if (held >= heldBefore) held - heldBefore else held
            if (held >= AMBUSH_HOLD_SECONDS && heldBefore < AMBUSH_HOLD_SECONDS) ambushHolds++
            heldBefore = held
        }
        val state = when (arm) {
            Arm.FRESH_TIER_0 -> playGateRun(
                seed, gateProfile(GateMatrix.NONE, tier = 0, content = content), NoPoiBot(dashRule = dashRule),
                content = content, observe = observe,
            )
            Arm.STRONG_TIER_10 -> playGateRun(
                seed,
                gateProfile(
                    GateMatrix.FULL, tier = 10, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, content = content,
                ),
                NoPoiBot(dashRule = dashRule),
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
        val obstacleKill = ended && state.message == OBSTACLE_COLLISION
        return Run(
            // A lethal-speed hit sets integrity to exactly zero; band damage leaves it below.
            seed, outcome, state.elapsed, ended && state.message == SINGULARITY_CONTACT, obstacleKill,
            obstacleKill && state.hp == 0f, contacts, hurts, ricochets, ambushes, overdrives, state.runMatter,
            ambushHolds, ambushSeconds, state.characterRuntime.dashSequence,
            if (ended) state.message.replace(' ', '_') else "-",
        )
    }

    /** Point values of the arm's metrics with bootstrap standard errors over its runs. */
    private fun summary(runs: List<Run>): String {
        val metrics = listOf<Pair<String, (List<Run>) -> Double>>(
            "winShare" to { sample -> sample.count { it.won }.toDouble() / sample.size },
            "medianSurvival" to { sample -> sample.map { it.seconds }.gateMedian().toDouble() },
            "singularityShare" to { sample -> sample.count { it.singularity }.toDouble() / sample.size },
            "obstacleKillShare" to { sample -> sample.count { it.obstacleKill }.toDouble() / sample.size },
            "lethalKillShare" to { sample -> sample.count { it.lethalKill }.toDouble() / sample.size },
            "contactsPerSecond" to { sample -> sample.sumOf { it.contacts }.toDouble() / sample.sumOf { it.seconds.toDouble() } },
            "hurtsPerMinute" to { sample -> sample.sumOf { it.hurts } * 60.0 / sample.sumOf { it.seconds.toDouble() } },
            "ricochetsPerMinute" to { sample -> sample.sumOf { it.ricochets } * 60.0 / sample.sumOf { it.seconds.toDouble() } },
            "ambushesPerRun" to { sample -> sample.sumOf { it.ambushes }.toDouble() / sample.size },
            "overdrivesPerRun" to { sample -> sample.sumOf { it.overdrives }.toDouble() / sample.size },
            "meanMatter" to { sample -> sample.sumOf { it.matter }.toDouble() / sample.size },
            "ambushHoldsPerRun" to { sample -> sample.sumOf { it.ambushHolds }.toDouble() / sample.size },
            "ambushSecondsPerMinute" to { sample ->
                sample.sumOf { it.ambushSeconds.toDouble() } * 60.0 / sample.sumOf { it.seconds.toDouble() }
            },
            "meanSurvival" to { sample -> sample.sumOf { it.seconds.toDouble() } / sample.size },
            "dashesPerMinute" to { sample -> sample.sumOf { it.dashes } * 60.0 / sample.sumOf { it.seconds.toDouble() } },
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

    /**
     * The shipped content with the flight-law knobs of [steeringOverrides] and the obstacle impact knobs of
     * [obstacleOverrides] (each `name=value`, comma-separated).
     */
    private fun probedContent(steeringOverrides: String, obstacleOverrides: String): GameplayContentSnapshot {
        var steering = canonicalGameplayContent.tempo.steering
        overrides(steeringOverrides).forEach { (name, value) -> steering = steering.with(name, value) }
        var obstacles = canonicalGameplayContent.obstacles
        overrides(obstacleOverrides).forEach { (name, value) -> obstacles = obstacles.with(name, value) }
        return canonicalGameplayContent.copy(
            tempo = canonicalGameplayContent.tempo.copy(steering = steering),
            obstacles = obstacles,
        )
    }

    private fun overrides(text: String): List<Pair<String, Float>> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
            val (name, value) = entry.split('=').map { it.trim() }
            name to value.toFloat()
        }

    /** The obstacle knobs the terrain calibration may move (spec §7 rules 3 and 4). */
    private fun ObstacleTuning.with(name: String, value: Float): ObstacleTuning = when (name) {
        "minimumDamageShare" -> copy(minimumDamageShare = value)
        "damageShare" -> copy(damageShare = value)
        "scrapeShare" -> copy(scrapeShare = value)
        "shieldBleed" -> copy(shieldBleed = value)
        "scrapeFriction" -> copy(scrapeFriction = value)
        "damageStartReach" -> copy(damageStartReach = value)
        "lethalReach" -> copy(lethalReach = value)
        "ricochetReach" -> copy(ricochetReach = value)
        else -> error("unknown obstacle knob $name")
    }

    private fun ObstacleTuning.impactKnobs(): String =
        "minimumDamageShare=$minimumDamageShare damageShare=$damageShare scrapeShare=$scrapeShare " +
            "shieldBleed=$shieldBleed scrapeFriction=$scrapeFriction damageStartReach=$damageStartReach " +
            "lethalReach=$lethalReach ricochetReach=$ricochetReach"

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
        const val OBSTACLE_COLLISION = "OBSTACLE COLLISION"
        const val BOOTSTRAP_RESAMPLES = 20_000
        const val BOOTSTRAP_SEED = 20_261_027
    }
}
