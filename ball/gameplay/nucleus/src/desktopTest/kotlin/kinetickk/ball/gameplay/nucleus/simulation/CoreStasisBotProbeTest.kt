// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreStasisTuning
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Core Stasis bot probe (decision G5, spec §13): plays the reference bots in measure-only mode (gameplay identical
 * to Core Stasis off) and prints the largest still clock each run reaches, so the lead can confirm that no gate run
 * comes near the grace. Runs only when `KK_STASIS_PROBE` is set:
 * - `KK_STASIS_PROBE=gates` mirrors the gate populations exactly (confirmation only, never tuned on);
 *   `KK_STASIS_PROBE=first..last` flies the same arms on calibration window seeds `i * 7_919 + 13` (thresholds are
 *   chosen on 2,501..2,800).
 * - `KK_STASIS_BOTS` picks populations (comma list of NOPOI, SITE, DUNGEON, DIMENSION, APEX; all by default).
 * - `KK_STASIS_RUNS` caps the seeds per population (quick looks).
 * - `KK_STASIS_KNOBS` overrides detector knobs (`anchorRadius=…,stillSpeed=…,travelSpeed=…,speedEmaSeconds=…,
 *   decayRate=…,graceSeconds=…`); `;` separates sets that run one after another.
 * - `KK_STASIS_APEX_BENEFIT=1` also plays the Apex benefit population (heavy).
 * - `KK_STASIS_PROBE_ASSERT=1` fails a `gates` run in which any run crosses the grace.
 *
 * The ramp is raised to 120 s so stills far past the shipped full stasis stay visible. Sampling is per input frame
 * (0.02 s). One command per mode; `--rerun` makes Gradle run the test again with new variables, and the lines land in
 * the suite's JUnit XML (`build/test-results/desktopTest/TEST-…CoreStasisBotProbeTest.xml`):
 *
 * `KK_STASIS_PROBE=2501..2800 ./gradlew -Pkinetickk.variant=release :ball:gameplay:nucleus:desktopTest --tests
 * 'kinetickk.ball.gameplay.nucleus.simulation.CoreStasisBotProbeTest' --rerun`, then the same with `KK_STASIS_PROBE=gates`.
 */
class CoreStasisBotProbeTest {
    @Test
    fun printsTheLargestStillClockOfEveryBotRun() {
        val mode = env("KK_STASIS_PROBE") ?: return
        val gates = mode == "gates"
        val window = if (gates) null else probeWindow(mode, defaultLength = 300)
        val bots = env("KK_STASIS_BOTS")?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }
            ?: listOf("NOPOI", "SITE", "DUNGEON", "DIMENSION", "APEX")
        val cap = env("KK_STASIS_RUNS")?.toInt()
        val benefit = env("KK_STASIS_APEX_BENEFIT") == "1"
        for (knobs in env("KK_STASIS_KNOBS").orEmpty().split(';')) {
            val tuning = probeTuning(knobs)
            val probe = StasisProbe(canonicalGameplayContent.copy(coreStasis = tuning), tuning.graceSeconds, window, cap)
            println("STASIS-PROBE mode=${window ?: "gates"} knobs='$knobs' grace=${tuning.graceSeconds} cap=${tuning.fullStasisSeconds}")
            for (bot in bots) when (bot) {
                "NOPOI" -> probe.noPoi()
                "SITE" -> probe.sites()
                "DUNGEON" -> probe.dungeons()
                "DIMENSION" -> probe.dimensions()
                "APEX" -> probe.apex(benefit)
                else -> error("unknown bot $bot")
            }
            println("STASIS-PROBE VERDICT knobs='$knobs' overGrace=${probe.overGrace} (pass when 0)")
            if (gates && env("KK_STASIS_PROBE_ASSERT") == "1") assertEquals(0, probe.overGrace, "gate runs crossed the grace")
        }
    }
}

private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

/** Measure-only shipped thresholds with a 120 s ramp, overridden by [knobs] (`name=value`, comma-separated). */
private fun probeTuning(knobs: String): CoreStasisTuning {
    var tuning = CoreStasisTuning(applyEffects = false, rampSeconds = 120f)
    for (pair in knobs.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
        val (name, text) = pair.split('=').map { it.trim() }
        val value = text.toFloat()
        tuning = when (name) {
            "anchorRadius" -> tuning.copy(anchorRadius = value)
            "stillSpeed" -> tuning.copy(stillSpeed = value)
            "travelSpeed" -> tuning.copy(travelSpeed = value)
            "speedEmaSeconds" -> tuning.copy(speedEmaSeconds = value)
            "decayRate" -> tuning.copy(decayRate = value)
            "graceSeconds" -> tuning.copy(graceSeconds = value)
            else -> error("unknown Core Stasis knob $name")
        }
    }
    return tuning
}

/** The populations of one knob set: each run gets its own [StasisTrack]; [overGrace] counts runs past the grace. */
private class StasisProbe(
    val content: GameplayContentSnapshot,
    val grace: Float,
    val window: IntRange?,
    val cap: Int?,
) {
    var overGrace = 0
        private set

    private fun seeds(gateSeeds: List<Int>): List<Int> {
        val all = window?.let(::windowSeeds) ?: gateSeeds
        return if (cap != null) all.take(cap) else all
    }

    private fun track(population: String, seed: Int, case: String = ""): StasisTrack = StasisTrack(population, seed, grace, case)

    private fun report(population: String, tracks: List<StasisTrack>) {
        tracks.forEach { println(it.line()) }
        val sorted = tracks.map { it.maxStill }.sorted()
        fun quantile(q: Double): Float = if (sorted.isEmpty()) 0f else sorted[min(sorted.lastIndex, ceil(q * sorted.size).toInt() - 1).coerceAtLeast(0)]
        val over = tracks.count { it.maxStill >= grace }
        overGrace += over
        val worst = tracks.sortedByDescending { it.maxStill }.take(5).joinToString(",") { "${it.seed}${it.caseTag}:${"%.2f".format(it.maxStill)}" }
        println("STASIS-PROBE SUMMARY $population runs=${tracks.size} max=${"%.2f".format(sorted.lastOrNull() ?: 0f)} " +
            "p99=${"%.2f".format(quantile(0.99))} p95=${"%.2f".format(quantile(0.95))} median=${"%.2f".format(quantile(0.5))} " +
            "overGrace=$over/${tracks.size} overHalfGrace=${tracks.count { it.maxStill >= grace * 0.5f }}/${tracks.size} worst=$worst")
    }

    /** NoPoiPlaythroughTest's population: the reference seed and [PopulationSeeds]. */
    fun noPoi() {
        val seeds = seeds(listOf(NoPoiPlaythroughTest.REFERENCE_SEED) + PopulationSeeds)
        report("NOPOI", populationOf(seeds) { seed ->
            track("NOPOI", seed).also { tracker -> NoPoiPlaythroughTest.fly(seed, content = content, observer = tracker::observe) }
        })
    }

    /** SiteEngagementGateTest's arms on its 150 seeds, plus the sigil as SiteCalibrationProbeTest flies it. */
    fun sites() {
        val seeds = seeds(PopulationSeeds.take(150))
        val arms = listOf("RACE", "RACE_PHONE", "BRAKELESS", "RAM", "UNTOUCHED", "VELOCITY", "SLALOM", "MIXED", "NOPOI", "SIGIL")
        for (arm in arms) report("SITE-$arm", populationOf(seeds) { seed ->
            track("SITE-$arm", seed).also { tracker ->
                val board = SiteRunSetup(base = content, stopAfterWindows = true)
                when (arm) {
                    "RACE_PHONE" -> flySiteRun(seed, SiteArm.RACE, SiteRunSetup(412f, 915f, base = content, stopAfterWindows = true), observer = tracker::observe)
                    "MIXED" -> flySiteRun(seed, SiteArm.MIXED, SiteRunSetup(base = content), observer = tracker::observe)
                    "NOPOI" -> flyNoPoiRun(seed, content, observer = tracker::observe)
                    else -> flySiteRun(seed, SiteArm.valueOf(arm), board, observer = tracker::observe)
                }
            }
        })
    }

    /** DungeonPilotGateTest's two arms on its seeds. */
    fun dungeons() {
        val seeds = seeds(PopulationSeeds.take(DungeonPilotGateTest.GATE_RUNS))
        for (arm in DungeonPilotGateTest.Arm.entries) report("DUNGEON-$arm", populationOf(seeds) { seed ->
            track("DUNGEON-$arm", seed).also { tracker -> DungeonPilotGateTest.fly(seed, arm, content, observer = tracker::observe) }
        })
    }

    /** DimensionPilotGateTest's six arms on its seeds, objectives split as the gate splits them. */
    fun dimensions() {
        val seeds = seeds(DimensionPilotGateTest.GateSeeds)
        for (arm in DimensionArm.entries) {
            val tracks = ConcurrentHashMap<Int, StasisTrack>()
            DimensionPilotGateTest.flyArm(arm, seeds, content) { seed -> tracks.getOrPut(seed) { track("DIMENSION-${arm.label}", seed) }::observe }
            report("DIMENSION-${arm.label}", seeds.map { checkNotNull(tracks[it]) })
        }
    }

    /**
     * ApexArchitectFightTest's contract: per character and tier 0 and 10, the first [ApexArchitectFightHarness.FIGHTS_PER_CASE]
     * runs along its seed pool (window seeds in window mode) that reach the Architect: the approach and both fight
     * arms; then the scripted arena per character and tier. [benefit] adds the Offence and Defence populations.
     */
    fun apex(benefit: Boolean) {
        val harness = ApexArchitectFightHarness
        val pool = seeds(harness.SEED_POOL)
        val approaches = mutableListOf<StasisTrack>()
        val starts = CoreShape.entries.flatMap { shape ->
            listOf(0, 10).flatMap { tier -> firstApproaches(shape, tier, harness.FIGHTS_PER_CASE, pool, approaches) }
        }
        val fights = populationOf(starts) { (seed, case, start) ->
            val (state, bot) = start
            val apexTrack = track("APEX-FIGHT", seed, "$case apex=true")
            val apex = harness.fight(state.deepCopy(), bot.copy(), apex = true, windows = emptyList(), observer = apexTrack::observe)
            val plainTrack = track("APEX-FIGHT", seed, "$case apex=false")
            harness.fight(state.deepCopy(), bot.copy(), apex = false, windows = apex.windows, observer = plainTrack::observe)
            listOf(apexTrack, plainTrack)
        }.flatten()
        report("APEX-APPROACH", approaches)
        report("APEX-FIGHT", fights)
        val arenas = CoreShape.entries.flatMap { shape -> listOf(0, 10).flatMap { tier ->
            listOf(true, false).map { apex ->
                track("APEX-ARENA", 4_242, " shape=$shape tier=$tier apex=$apex").also { harness.arena(shape, tier, apex, content, it::observe) }
            }
        } }
        report("APEX-ARENA", arenas)
        if (!benefit) return
        val benefitSeeds = seeds(PopulationSeeds)
        val benefitApproaches = mutableListOf<StasisTrack>()
        val benefitStarts = CoreShape.entries.flatMap { shape ->
            val defensive = shape in harness.DEFENSIVE
            val runs = if (defensive) harness.DEFENSIVE_RUNS else harness.BENEFIT_RUNS
            firstApproaches(shape, if (defensive) harness.DEFENSIVE_TIER else 0, runs, benefitSeeds, benefitApproaches, "APEX-BENEFIT-APPROACH")
        }
        val benefitFights = populationOf(benefitStarts) { (seed, case, start) ->
            val (state, bot) = start
            (0 until harness.FORKS).flatMap { fork ->
                val apexTrack = track("APEX-BENEFIT", seed, "$case fork=$fork apex=true")
                val apex = with(harness) { fight(state.forked(fork), bot.copy(), apex = true, windows = emptyList(), observer = apexTrack::observe) }
                val plainTrack = track("APEX-BENEFIT", seed, "$case fork=$fork apex=false")
                with(harness) { fight(state.forked(fork), bot.copy(), apex = false, windows = apex.windows, observer = plainTrack::observe) }
                listOf(apexTrack, plainTrack)
            }
        }.flatten()
        report("APEX-BENEFIT-APPROACH", benefitApproaches)
        report("APEX-BENEFIT", benefitFights)
    }

    /**
     * The first [count] runs of [shape] at [tier] along [seeds] that reach the Architect, approached
     * [ApexArchitectFightHarness.SEED_BATCH] at a time in seed order (the harness's `firstRuns`); every approach's
     * track joins [approaches].
     */
    private fun firstApproaches(
        shape: CoreShape,
        tier: Int,
        count: Int,
        seeds: List<Int>,
        approaches: MutableList<StasisTrack>,
        label: String = "APEX-APPROACH",
    ): List<Triple<Int, String, Pair<MutableGameState, NoPoiBot>>> {
        val harness = ApexArchitectFightHarness
        val found = mutableListOf<Triple<Int, String, Pair<MutableGameState, NoPoiBot>>>()
        var next = 0
        while (found.size < count && next < seeds.size) {
            val batch = seeds.subList(next, min(next + harness.SEED_BATCH, seeds.size))
            next += batch.size
            val results = batch.map { seed ->
                harness.pool.submit(Callable {
                    val tracker = track(label, seed, " shape=$shape tier=$tier")
                    Triple(seed, tracker, harness.approach(shape, tier, seed, content, tracker::observe))
                })
            }.map { it.get() }
            results.forEach { (_, tracker, _) -> approaches += tracker }
            results.mapNotNull { (seed, _, start) -> start?.let { Triple(seed, " shape=$shape tier=$tier", it) } }
                .take(count - found.size).forEach { found += it }
        }
        return found
    }
}

/** One run's still clock: its largest value and when, time above 5 s, grace crossings and where the peak happened. */
private class StasisTrack(val population: String, val seed: Int, private val grace: Float, private val case: String) {
    var maxStill = 0f
        private set
    /** The case compactly (`/RING/10`), for the summary's worst list. */
    val caseTag: String get() = case.split(' ').filter { it.startsWith("shape=") || it.startsWith("tier=") }
        .joinToString("") { "/" + it.substringAfter('=') }
    private var maxAt = 0f
    private var above5 = 0f
    private var crossings = 0
    private var context = ""
    private var lastElapsed = Float.NaN
    private var wasOverGrace = false
    private var endPhase = GamePhase.RUNNING
    private var endAt = 0f

    fun observe(s: MutableGameState) {
        val still = s.coreStasis.stillSeconds
        if (!lastElapsed.isNaN() && still > 5f) above5 += s.elapsed - lastElapsed
        lastElapsed = s.elapsed
        if (still >= grace && !wasOverGrace) crossings++
        wasOverGrace = still >= grace
        endPhase = s.phase
        endAt = s.elapsed
        if (still > maxStill) {
            maxStill = still
            maxAt = s.elapsed
            context = "layer=${s.worldLayer} sealed=${s.structureEngagementActive()} site=${s.activeWindowSite()?.kind} " +
                "boss=${s.enemies.any { it.alive && it.isBossClass }} architect=${s.liveArchitect() != null} " +
                "braking=${s.braking} speed=${s.speed.toInt()} ema=${s.coreStasis.speedEma.toInt()} " +
                "core=${s.coreX.toInt()},${s.coreY.toInt()}"
        }
    }

    fun line(): String = "STASIS-PROBE $population seed=$seed$case end=$endPhase@${"%.1f".format(endAt)}s maxStill=${"%.2f".format(maxStill)} " +
        "at=${"%.1f".format(maxAt)}s above5=${"%.1f".format(above5)} crossings=$crossings $context"
}
