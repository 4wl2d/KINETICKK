// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.performance.canonicalStateFingerprint
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dimension visit bands (spec §12.3), flown by [DimensionPilot] on the first 120 [PopulationSeeds] (i = 1,001..1,120):
 * each arm opens its rift 560 u ahead of the Core at its time, the pilot flies through it and plays the visit, and the run
 * is read five seconds after the return. A window arm forces its first objective on the first 60 seeds and its second
 * objective on the other 60; the Archive arms play the Antecedent on all 120. A visit's survival counts the runs that
 * entered (a run that falls before its rift opens measures realspace, not the visit). The thresholds are the spec's
 * design bands; the knobs (Grace, pace reach, scorch, ring and ember counts) are calibrated on the lane's window
 * i = 2,401..2,500 only, through the probe at the end of this class.
 *
 * Measured on the 0.6.0 lane tree (shares with bootstrap SE; the records step restates the bands by the population
 * rule on the final tree): on the window every band a lane knob reaches holds with the shipped knobs, while the
 * Ascent and the Harvest complete every visit (12 s and 15 s at the median; 16 rings and 24 embers still complete
 * 1.00 and 0.98) and the objective bosses fall in at most 0.15 of the visits (their integrity is ENEMIES'). On the gate
 * seeds: Z0 survival 0.965 (0.017), Ascent 1.000, Judgement 0.000, median 12.2 s; N0 survival 0.843 (0.034), Harvest
 * 1.000, Pyre 0.035 (0.024), scorch 0.100 (0.013); Z10 survival 0.796 (0.044), completion 0.500 (0.054); N10 0.966
 * (0.019) and 0.489 (0.054); A3 survival 1.000, defeat 0.168 (0.034); A0 survival 0.857 (0.036), defeat 0.092 (0.029).
 */
class DimensionPilotGateTest {
    /**
     * Z0: a fresh tier-0 run sent into the Zenith at 165 s survives the visit, completes the Ascent and the Judgement in
     * their bands, and a completed trial takes at most [Z0_MEDIAN_COMPLETION] s at the median.
     */
    @Test fun aFreshRunFliesTheZenithTrialsInTheirBands() {
        val runs = runsOf(DimensionArm.Z0)
        val ascent = runs.filter { it.objective == DimensionObjective.ASCENT }
        val judgement = runs.filter { it.objective == DimensionObjective.JUDGEMENT }
        val median = runs.filter { it.completed }.map { it.completionSeconds }.finiteMedian()
        assertEveryOpenRiftEntered(DimensionArm.Z0, runs)
        assertShare("Z0 survival", runs.entered(), Z0_SURVIVAL, 1.0) { it.survived }
        assertShare("Z0 ascent", ascent, Z0_ASCENT.start, Z0_ASCENT.endInclusive) { it.completed }
        assertShare("Z0 judgement", judgement, Z0_JUDGEMENT.start, Z0_JUDGEMENT.endInclusive) { it.completed }
        assertTrue(median <= Z0_MEDIAN_COMPLETION, "Z0 median completion $median s, above $Z0_MEDIAN_COMPLETION s")
    }

    /**
     * N0: a fresh tier-0 run sent into the Nadir at 165 s survives the visit, completes the Harvest and the Pyre in their
     * bands, and the scorch takes at most [N0_SCORCH_SHARE] of the integrity the visits lose.
     */
    @Test fun aFreshRunKeepsPaceThroughTheNadirTrials() {
        val runs = runsOf(DimensionArm.N0)
        val harvest = runs.filter { it.objective == DimensionObjective.HARVEST }
        val pyre = runs.filter { it.objective == DimensionObjective.PYRE }
        val scorch = runs.entered().scorchShare()
        assertEveryOpenRiftEntered(DimensionArm.N0, runs)
        assertShare("N0 survival", runs.entered(), N0_SURVIVAL, 1.0) { it.survived }
        assertShare("N0 harvest", harvest, N0_HARVEST.start, N0_HARVEST.endInclusive) { it.completed }
        assertShare("N0 pyre", pyre, N0_PYRE.start, N0_PYRE.endInclusive) { it.completed }
        assertTrue(scorch <= N0_SCORCH_SHARE, "N0 scorch took $scorch of the visit damage, above $N0_SCORCH_SHARE")
    }

    /**
     * Z10 and N10: the strong tier-10 build sent in at 285 s survives each visit and completes at least half of them.
     * N10 takes the Refuse card of its Pact offer, so the gate also flies the decline through the reward pipeline.
     */
    @Test fun aStrongBuildCompletesItsTierTenVisits() {
        val arms = listOf(DimensionArm.Z10, DimensionArm.N10).associateWith { runsOf(it) }
        for ((arm, runs) in arms) {
            assertEveryOpenRiftEntered(arm, runs)
            assertShare("${arm.label} survival", runs.entered(), STRONG_SURVIVAL, 1.0) { it.survived }
            assertShare("${arm.label} completion", runs.entered(), STRONG_COMPLETION, 1.0) { it.completed }
        }
    }

    /**
     * A3 and A0: the Archive's glitch rift at 405 s. A full Matrix with the signature weapon at tier 3 survives it and
     * defeats the Antecedent in its band; a fresh tier-0 run survives it less often and rarely defeats it (the secret
     * stays hard).
     */
    @Test fun theArchiveStaysASecretForAFreshRunAndAFightForTheMatrix() {
        val matrix = runsOf(DimensionArm.A3)
        val fresh = runsOf(DimensionArm.A0)
        assertEveryOpenRiftEntered(DimensionArm.A3, matrix)
        assertShare("A3 survival", matrix.entered(), A3_SURVIVAL, 1.0) { it.survived }
        assertShare("A3 defeat", matrix.entered(), A3_DEFEAT.start, A3_DEFEAT.endInclusive) { it.completed }
        assertEveryOpenRiftEntered(DimensionArm.A0, fresh)
        assertShare("A0 survival", fresh.entered(), A0_SURVIVAL, 1.0) { it.survived }
        assertShare("A0 defeat", fresh.entered(), 0.0, A0_DEFEAT) { it.completed }
    }

    /** One seed's Nadir visit flown twice by the pilot ends in the same canonical state at the return. */
    @Test fun aDimensionVisitReplaysIdentically() {
        val seed = PopulationSeeds.first()
        val first = flyToTheReturn(seed)
        val second = flyToTheReturn(seed)
        assertEquals(WorldLayer.BASE, first.worldLayer, "the visit returned")
        assertEquals(WorldLayer.NADIR.bit, first.dimensionRun.visitedMask)
        assertEquals(canonicalStateFingerprint(first), canonicalStateFingerprint(second))
        assertEquals(first.elapsed, second.elapsed)
    }

    /**
     * The lane's calibration probe, not a gate: it returns at once unless `KK_DIMENSION_PROBE=first..last` names a
     * calibration window, then flies every arm (or those `KK_DIMENSION_ARMS` lists) on the seeds `i * 7_919 + 13` for i in
     * it and prints one line per run and a summary per arm with bootstrap standard errors. It never plays the gate
     * population. `KK_DIMENSION_KNOBS=name=value,...` overrides the shipped dimension knobs (graceDrag, paceReach, scorch,
     * rings, embers, emberLife, emberRadius, haloRadius, turnMin, turnMax, spacingMin, spacingMax, zenithBossShare,
     * nadirBossShare). The outcome arms OC (no rift), OZ and ON (a Zenith or Nadir visit at 165 s with its rolled
     * objective) play whole runs and print the win shares the outcome gate compares.
     */
    @Test fun probeTheCalibrationWindow() {
        val window = System.getenv("KK_DIMENSION_PROBE")?.takeIf { it.isNotBlank() } ?: return
        val (first, last) = window.split("..").map { it.trim().toInt() }
        require(first in 1..last && (last < GATE_FIRST || first > GATE_LAST)) { "the gate population is never probed: $window" }
        val seeds = (first..last).map { it * 7_919 + 13 }
        val content = probedContent(System.getenv("KK_DIMENSION_KNOBS").orEmpty())
        val wanted = System.getenv("KK_DIMENSION_ARMS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        println("DIMENSION PROBE window $first..$last knobs ${knobs(content)}")
        for (arm in DimensionArm.entries) {
            if (wanted != null && arm.label !in wanted) continue
            val runs = flyArm(arm, seeds, content)
            runs.forEach { println("DIMENSION PROBE ${arm.label} $it") }
            println("DIMENSION PROBE SUMMARY ${arm.label} ${summary(runs)}")
        }
        for ((label, target) in listOf("OC" to null, "OZ" to WorldLayer.ZENITH, "ON" to WorldLayer.NADIR)) {
            if (wanted != null && label !in wanted) continue
            val runs = populationOf(seeds) { seed ->
                flyDimensionRun(seed, DimensionProfile.FRESH_TIER_0, target, FRESH_RIFT_AT, runToEnd = true, content = content)
            }
            runs.forEach { println("DIMENSION PROBE $label $it") }
            println("DIMENSION PROBE SUMMARY $label n=${runs.size} win=${describe(runs) { s -> s.count { it.won }.toDouble() / s.size }} " +
                "entered=${runs.count { it.entered }} completed=${runs.count { it.completed }}")
        }
    }

    private fun flyToTheReturn(seed: Int): MutableGameState {
        val state = MutableGameState(canonicalGameplayContent, seed = seed, bootstrapProgress = DimensionProfile.FRESH_TIER_0.snapshot(canonicalGameplayContent))
        state.unlockWeaponsForTesting(ReferenceArmory)
        state.startRun()
        val pilot = DimensionPilot(WorldLayer.NADIR, DimensionProfile.FRESH_TIER_0.bot())
        var opened = false
        while (state.elapsed < 400f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (!opened && state.phase == GamePhase.RUNNING && state.elapsed >= FRESH_RIFT_AT) {
                state.openPortalForTesting(WorldLayer.NADIR, clear = true)
                pilot.riftDue = true
                opened = true
            }
            if (state.phase == GamePhase.CHOICE) pilot.choose(state) else {
                pilot.control(state)
                state.update(0.02f)
            }
            state.takeSoundCues()
            state.takeVisualFxCues()
            if (state.dimensionRun.visitedMask != 0 && state.worldLayer == WorldLayer.BASE) break
        }
        return state
    }

    /** The pilot flies through every rift that opened while its run lasted (a rift is never left to close unentered). */
    private fun assertEveryOpenRiftEntered(arm: DimensionArm, runs: List<DimensionVisitRun>) {
        val missed = runs.filter { !it.entered && !it.openedAt.isNaN() && it.phase != GamePhase.GAME_OVER }
        assertTrue(missed.isEmpty(), "${arm.label}: the pilot left rifts unentered: ${missed.joinToString { "${it.seed}@${it.seconds}" }}")
    }

    private fun assertShare(label: String, runs: List<DimensionVisitRun>, low: Double, high: Double, predicate: (DimensionVisitRun) -> Boolean) {
        val share = runs.shareOf(predicate)
        assertTrue(runs.isNotEmpty() && share >= low && share <= high,
            "$label ${"%.3f".format(share)} of ${runs.size} runs, outside [$low, $high]")
    }

    private companion object {
        const val GATE_FIRST = 1_001
        const val GATE_LAST = 1_300

        /** The gate seeds: the first 120 of the population (C42), never tuned on. */
        val GateSeeds: List<Int> = PopulationSeeds.take(120)

        /** Each arm's runs, flown once per class and shared by its rules. */
        val armRuns = HashMap<DimensionArm, List<DimensionVisitRun>>()

        @Synchronized fun runsOf(arm: DimensionArm): List<DimensionVisitRun> = armRuns.getOrPut(arm) {
            flyArm(arm, GateSeeds, canonicalGameplayContent).also { runs ->
                println("DIMENSION GATE ${arm.label} ${summary(runs)}")
            }
        }

        /** [arm] on [seeds]: the first half forced to the arm's first objective, the rest to its last. */
        fun flyArm(arm: DimensionArm, seeds: List<Int>, content: GameplayContentSnapshot): List<DimensionVisitRun> {
            val half = (seeds.size + 1) / 2
            return populationOf(seeds.withIndex().toList()) { (index, seed) ->
                val objective = if (index < half) arm.objectives.first() else arm.objectives.last()
                flyDimensionRun(seed, arm.profile, arm.target, arm.riftAt, objective, arm.takeRefuse, content = content)
            }
        }

        fun List<DimensionVisitRun>.entered(): List<DimensionVisitRun> = filter { it.entered }

        fun List<DimensionVisitRun>.scorchShare(): Double {
            val damage = sumOf { it.visitDamage.toDouble() }
            return if (damage > 0.0) sumOf { it.scorchDamage.toDouble() } / damage else 0.0
        }

        /** Entry, survival, completion per objective, completion time, scorch share and integrity at the return. */
        fun summary(runs: List<DimensionVisitRun>): String {
            val entered = runs.entered()
            val objectives = entered.mapNotNull { it.objective }.distinct()
            return "n=${runs.size} entered=${entered.size} fellBefore=${runs.count { !it.entered && it.phase == GamePhase.GAME_OVER }} " +
                "neverOpened=${runs.count { !it.entered && it.openedAt.isNaN() && it.phase != GamePhase.GAME_OVER }} " +
                "medianOpenedAt=${runs.map { it.openedAt }.finiteMedian()} " +
                "survival=${describe(entered) { s -> s.count { it.survived }.toDouble() / s.size }} " +
                "completion=${describe(entered) { s -> s.count { it.completed }.toDouble() / s.size }} " +
                objectives.joinToString(" ") { objective ->
                    val played = entered.filter { it.objective == objective }
                    "$objective=${describe(played) { s -> s.count { it.completed }.toDouble() / s.size }}(n=${played.size})"
                } +
                " medianCompletion=${describe(entered) { s -> s.filter { it.completed }.map { it.completionSeconds }.finiteMedian() }}" +
                " scorchShare=${describe(entered) { it.scorchShare() }}" +
                " medianBossLost=${describe(entered.filter { it.objective?.boss != null }) { s -> s.map { it.bossLost }.finiteMedian() }}" +
                " medianIntegrityAtReturn=${describe(entered) { s -> s.map { it.integrityAtReturn }.finiteMedian() }}" +
                " deaths=${entered.filter { !it.survived }.groupingBy { it.reason }.eachCount()}"
        }

        /** The shipped content with the dimension knobs of [overrides] (`name=value`, comma-separated). */
        fun probedContent(overrides: String): GameplayContentSnapshot {
            var content = canonicalGameplayContent
            overrides.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
                val (name, text) = entry.split('=').map { it.trim() }
                val value = text.toFloat()
                content = content.withDimensions {
                    when (name) {
                        "graceDrag" -> copy(definitions = definitions.map { d ->
                            if (d.layer == WorldLayer.ZENITH) d.copy(physics = d.physics.copy(dragMultiplier = value)) else d
                        }.toImmutableList())
                        "scorch" -> copy(definitions = definitions.map { d ->
                            if (d.layer == WorldLayer.NADIR) d.copy(physics = d.physics.copy(scorchSharePerSecond = value)) else d
                        }.toImmutableList())
                        "paceReach" -> copy(paceReach = value)
                        "rings" -> copy(objectives = objectives.copy(ascentRings = value.toInt()))
                        "embers" -> copy(objectives = objectives.copy(harvestEmbers = value.toInt()))
                        "emberLife" -> copy(objectives = objectives.copy(emberLifeSeconds = value))
                        "emberRadius" -> copy(objectives = objectives.copy(emberRadius = value))
                        "haloRadius" -> copy(objectives = objectives.copy(haloRadius = value))
                        "turnMin" -> copy(objectives = objectives.copy(ascentMinimumTurnDegrees = value))
                        "turnMax" -> copy(objectives = objectives.copy(ascentMaximumTurnDegrees = value))
                        "spacingMin" -> copy(objectives = objectives.copy(ascentMinimumSpacing = value))
                        "spacingMax" -> copy(objectives = objectives.copy(ascentMaximumSpacing = value))
                        "zenithBossShare", "nadirBossShare" -> copy(definitions = definitions.map { d ->
                            val layer = if (name == "zenithBossShare") WorldLayer.ZENITH else WorldLayer.NADIR
                            if (d.layer == layer) d.copy(bossIntegrityShare = value) else d
                        }.toImmutableList())
                        else -> error("unknown dimension knob $name")
                    }
                }
            }
            return content
        }

        fun knobs(content: GameplayContentSnapshot): String {
            val policy = content.dimensions
            return "graceDrag=${policy.definition(WorldLayer.ZENITH).physics.dragMultiplier} paceReach=${policy.paceReach} " +
                "scorch=${policy.definition(WorldLayer.NADIR).physics.scorchSharePerSecond} rings=${policy.objectives.ascentRings} " +
                "embers=${policy.objectives.harvestEmbers} emberLife=${policy.objectives.emberLifeSeconds} " +
                "emberRadius=${policy.objectives.emberRadius} haloRadius=${policy.objectives.haloRadius} " +
                "turns=${policy.objectives.ascentMinimumTurnDegrees}..${policy.objectives.ascentMaximumTurnDegrees} " +
                "spacing=${policy.objectives.ascentMinimumSpacing}..${policy.objectives.ascentMaximumSpacing} " +
                "zenithBossShare=${policy.definition(WorldLayer.ZENITH).bossIntegrityShare} " +
                "nadirBossShare=${policy.definition(WorldLayer.NADIR).bossIntegrityShare}"
        }

        /** Z0: a fresh run's Zenith survival floor, its Ascent and Judgement completion bands and the completion median. */
        const val Z0_SURVIVAL = 0.92
        val Z0_ASCENT = 0.55..0.92
        val Z0_JUDGEMENT = 0.45..0.88
        const val Z0_MEDIAN_COMPLETION = 55.0

        /** N0: a fresh run's Nadir survival floor, its Harvest and Pyre completion bands and the scorch's share of damage. */
        const val N0_SURVIVAL = 0.85
        val N0_HARVEST = 0.50..0.90
        val N0_PYRE = 0.40..0.85
        const val N0_SCORCH_SHARE = 0.45

        /** Z10 and N10: the strong tier-10 build's survival and completion floors. */
        const val STRONG_SURVIVAL = 0.75
        const val STRONG_COMPLETION = 0.50

        /** A3: the tier-3 full Matrix's Archive survival floor and Antecedent defeat band; A0: the fresh run's. */
        const val A3_SURVIVAL = 0.65
        val A3_DEFEAT = 0.20..0.65
        const val A0_SURVIVAL = 0.45
        const val A0_DEFEAT = 0.35
    }
}

/** A rift time shared by the fresh window arms and the outcome gate. */
internal const val FRESH_RIFT_AT = 165f

/** The pilot gate's arms (spec §12.3): profile, rift, its time, the objectives it forces and the reward card it takes. */
internal enum class DimensionArm(
    val label: String,
    val profile: DimensionProfile,
    val target: WorldLayer,
    val riftAt: Float,
    val objectives: List<DimensionObjective>,
    val takeRefuse: Boolean = false,
) {
    Z0("Z0", DimensionProfile.FRESH_TIER_0, WorldLayer.ZENITH, FRESH_RIFT_AT, listOf(DimensionObjective.ASCENT, DimensionObjective.JUDGEMENT)),
    N0("N0", DimensionProfile.FRESH_TIER_0, WorldLayer.NADIR, FRESH_RIFT_AT, listOf(DimensionObjective.HARVEST, DimensionObjective.PYRE)),
    Z10("Z10", DimensionProfile.STRONG_TIER_10, WorldLayer.ZENITH, 285f, listOf(DimensionObjective.ASCENT, DimensionObjective.JUDGEMENT)),
    N10("N10", DimensionProfile.STRONG_TIER_10, WorldLayer.NADIR, 285f, listOf(DimensionObjective.HARVEST, DimensionObjective.PYRE), takeRefuse = true),
    A3("A3", DimensionProfile.SIGNATURE_TIER_3, WorldLayer.ARCHIVE, 405f, listOf(DimensionObjective.ANTECEDENT)),
    A0("A0", DimensionProfile.FRESH_TIER_0, WorldLayer.ARCHIVE, 405f, listOf(DimensionObjective.ANTECEDENT)),
}
