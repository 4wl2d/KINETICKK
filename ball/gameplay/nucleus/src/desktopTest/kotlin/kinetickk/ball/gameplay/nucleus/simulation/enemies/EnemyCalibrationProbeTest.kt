// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.enemies

import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.director.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test

/**
 * The calibration probe of the involuntary space kinds (COMET, PYLON), not a gate: it returns at once unless
 * `KK_ENEMY_PROBE=first..last` names a calibration window (ENEMIES owns i = 1,301..1,600), then plays the seeds
 * `i * 7_919 + 13` for i in it. It never plays the gate population (i = 1,001..1,300).
 *
 * Each arm (Gate A's fresh tier-0 run and Gate D's strong tier-10 build, both the NoPoi bot for up to 900 s) plays every
 * seed twice: on the content without the new space kinds (COMET and PYLON at SPACE weight 0, every other row as
 * shipped) and on the shipped content. Per run: outcome, survival, the lethal reason, kills, and the ordinary enemies
 * that appeared in open space during the first 600 s (each enemy once, by id; elites, heralds and bosses left out), with
 * the Comets and Pylons among them. Summaries carry bootstrap standard errors (20,000 resamples of the seeds, RNG seed
 * 20261027) and the paired difference shipped − without with its own. The plain director's weight share of the two
 * kinds over the first 600 s at tier 0 (recency, personality and themes left out) is printed beside them.
 *
 * `KK_ENEMY_PROBE_WEIGHTS=COMET=w,PYLON=w` replaces the SPACE weights of the shipped arm (calibration only).
 */
class EnemyCalibrationProbeTest {
    @Test
    fun probeTheSpaceKindsOnTheCalibrationWindow() {
        val window = System.getenv("KK_ENEMY_PROBE")?.takeIf { it.isNotBlank() } ?: return
        val indices = probeWindow(window)
        val seeds = windowSeeds(indices)
        val shipped = withSpaceWeights(canonicalGameplayContent, probeWeights())
        val without = withSpaceWeights(canonicalGameplayContent, SpaceKinds.associateWith { 0f })
        println("ENEMY PROBE window $indices weights " +
            SpaceKinds.joinToString(",") { "$it=${shipped.enemy(it).weight}" })
        for (tier in listOf(0, 10)) {
            println("ENEMY PROBE WEIGHT SHARE tier=$tier 0..600s ${weightShare(shipped, tier)}")
        }
        for (arm in listOf(DimensionProfile.FRESH_TIER_0, DimensionProfile.STRONG_TIER_10)) {
            val runsWithout = populationOf(seeds) { seed -> play(seed, arm, without) }
            val runsWith = populationOf(seeds) { seed -> play(seed, arm, shipped) }
            runsWithout.forEach { println("ENEMY PROBE ${arm.label} without $it") }
            runsWith.forEach { println("ENEMY PROBE ${arm.label} shipped $it") }
            println("ENEMY PROBE SUMMARY ${arm.label} without ${summary(runsWithout)}")
            println("ENEMY PROBE SUMMARY ${arm.label} shipped ${summary(runsWith)}")
            println("ENEMY PROBE DELTA ${arm.label} shipped-without ${delta(runsWithout, runsWith)}")
        }
    }

    private class Run(
        val seed: Int,
        val outcome: String,
        val seconds: Float,
        val reason: String,
        val kills: Int,
        /** Ordinary enemies that appeared in open space before 600 s, and the Comets and Pylons among them. */
        val spaceSpawns: Int,
        val comets: Int,
        val pylons: Int,
    ) {
        val won: Boolean get() = outcome == "WIN"

        override fun toString(): String =
            "seed=$seed outcome=$outcome seconds=$seconds reason=${reason.replace(' ', '_')} kills=$kills " +
                "spaceSpawns600=$spaceSpawns comets600=$comets pylons600=$pylons"
    }

    private fun play(seed: Int, arm: DimensionProfile, content: GameplayContentSnapshot): Run {
        val seen = HashSet<Int>()
        var spaceSpawns = 0
        var comets = 0
        var pylons = 0
        val observe = { state: MutableGameState ->
            val counting = state.elapsed < MIX_SECONDS && state.ambientHabitat() == EnemyHabitat.SPACE
            for (index in state.enemies.indices) {
                val enemy = state.enemies[index]
                if (!seen.add(enemy.id) || !counting || enemy.type.profile.rank != EnemyRank.ORDINARY) continue
                spaceSpawns++
                if (enemy.type == EnemyType.COMET) comets++
                if (enemy.type == EnemyType.PYLON) pylons++
            }
        }
        val state = arm.play(seed, content, observe = observe)
        val outcome = when (state.phase) {
            GamePhase.VICTORY -> "WIN"
            GamePhase.GAME_OVER -> "LOSS"
            else -> "TIMEOUT"
        }
        val reason = if (state.phase == GamePhase.GAME_OVER) state.message else "-"
        return Run(seed, outcome, state.elapsed, reason, state.kills, spaceSpawns, comets, pylons)
    }

    private val metrics = listOf<Pair<String, (List<Run>) -> Double>>(
        "winShare" to { sample -> sample.count { it.won }.toDouble() / sample.size },
        "medianSurvival" to { sample -> sample.map { it.seconds }.gateMedian().toDouble() },
        "meanSurvival" to { sample -> sample.sumOf { it.seconds.toDouble() } / sample.size },
        "coreFracturedShare" to { sample -> sample.count { it.reason == CORE_FRACTURED }.toDouble() / sample.size },
        "singularityShare" to { sample -> sample.count { it.reason == SINGULARITY_CONTACT }.toDouble() / sample.size },
        "obstacleKillShare" to { sample -> sample.count { it.reason == OBSTACLE_COLLISION_REASON }.toDouble() / sample.size },
        "killsPerMinute" to { sample -> sample.sumOf { it.kills } * 60.0 / sample.sumOf { it.seconds.toDouble() } },
        "spaceSpawns600PerRun" to { sample -> sample.sumOf { it.spaceSpawns }.toDouble() / sample.size },
        "cometPylonMix600" to { sample ->
            val all = sample.sumOf { it.spaceSpawns }
            if (all == 0) 0.0 else sample.sumOf { it.comets + it.pylons }.toDouble() / all
        },
        "cometMix600" to { sample ->
            val all = sample.sumOf { it.spaceSpawns }
            if (all == 0) 0.0 else sample.sumOf { it.comets }.toDouble() / all
        },
        "pylonMix600" to { sample ->
            val all = sample.sumOf { it.spaceSpawns }
            if (all == 0) 0.0 else sample.sumOf { it.pylons }.toDouble() / all
        },
    )

    /** Point values of the arm's metrics with bootstrap standard errors over its runs. */
    private fun summary(runs: List<Run>): String =
        "n=${runs.size} " + metrics.joinToString(" ") { (name, metric) -> bootstrapLine(name, runs, metric = metric) }

    /** Shipped minus without for every metric, resampling the seeds in pairs (both arms play the same seeds). */
    private fun delta(without: List<Run>, with: List<Run>): String = metrics.joinToString(" ") { (name, metric) ->
        bootstrapLine(name, without.zip(with), "%+.4f") { pairs -> metric(pairs.map { it.second }) - metric(pairs.map { it.first }) }
    }

    /**
     * The plain director's open-space weight share of the Comet and the Pylon at [tier] (no recency, personality or
     * theme): averaged over each second of the first 600 s of threat time, and at 600 s itself.
     */
    private fun weightShare(content: GameplayContentSnapshot, tier: Int): String {
        val state = MutableGameState(content, seed = 1, initialMatter = 0, initialRebirthLevel = tier)
        var shareSum = 0.0
        var spaceSum = 0.0
        var totalSum = 0.0
        var atEnd = 0.0
        for (second in 0..MIX_SECONDS.toInt()) {
            var space = 0.0
            var total = 0.0
            for (definition in content.enemies) {
                val weight = state.typeWeight(definition, weakOnly = false, recency = false, habitat = EnemyHabitat.SPACE,
                    threatSeconds = second.toFloat(), personality = false).toDouble()
                total += weight
                if (definition.type in SpaceKinds) space += weight
            }
            val share = if (total > 0.0) space / total else 0.0
            shareSum += share
            spaceSum += space
            totalSum += total
            if (second == MIX_SECONDS.toInt()) atEnd = share
        }
        val seconds = MIX_SECONDS.toInt() + 1
        return "meanShare=${"%.4f".format(shareSum / seconds)} integralShare=${"%.4f".format(spaceSum / totalSum)} " +
            "at600=${"%.4f".format(atEnd)}"
    }

    private fun probeWeights(): Map<EnemyType, Float> {
        val text = System.getenv("KK_ENEMY_PROBE_WEIGHTS")?.takeIf { it.isNotBlank() }
            ?: return SpaceKinds.associateWith { canonicalGameplayContent.enemy(it).weight }
        val parsed = text.split(",").associate { entry ->
            val (name, value) = entry.split("=").map { it.trim() }
            EnemyType.valueOf(name) to value.toFloat()
        }
        require(parsed.keys.all { it in SpaceKinds }) { "KK_ENEMY_PROBE_WEIGHTS sets only ${SpaceKinds.joinToString()}" }
        return SpaceKinds.associateWith { parsed[it] ?: canonicalGameplayContent.enemy(it).weight }
    }

    /** [content] with the SPACE weights of [weights]' kinds replaced; every other field as given. */
    private fun withSpaceWeights(content: GameplayContentSnapshot, weights: Map<EnemyType, Float>): GameplayContentSnapshot =
        content.copy(
            enemies = content.enemies.map { definition ->
                weights[definition.type]?.let { definition.copy(weight = it) } ?: definition
            }.toImmutableList(),
        )

    private companion object {
        val SpaceKinds = listOf(EnemyType.COMET, EnemyType.PYLON)

        const val MIX_SECONDS = 600f
        const val CORE_FRACTURED = "CORE FRACTURED"
        const val SINGULARITY_CONTACT = "SINGULARITY CONTACT"
    }
}
