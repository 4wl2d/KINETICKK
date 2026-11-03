// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.flight

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreStasisNotices
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
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*

/**
 * The Core Freeze bot probe (decision D5): plays the reference bots and prints, per run, the peak freeze, the longest
 * spell under the freeze speed (raw and filtered) and whether the run ended frozen, so the lead can confirm that normal
 * bot play never freezes. Runs only when `KK_STASIS_PROBE` is set:
 * - `KK_STASIS_PROBE=gates` mirrors the gate populations exactly (confirmation only, never tuned on);
 *   `KK_STASIS_PROBE=first..last` flies the same arms on calibration window seeds `i * 7_919 + 13` (bot rules are
 *   chosen on 2,501..2,800).
 * - `KK_STASIS_BOTS` picks populations (comma list of NOPOI, SITE, DUNGEON, DIMENSION, APEX; all by default).
 * - `KK_STASIS_RUNS` caps the seeds per population (quick looks).
 * - `KK_STASIS_LETHAL=1` plays the shipped rule (a full freeze ends the run); by default the probe measures only, so
 *   the gameplay is identical to Core Freeze off and a peak of 1 marks a run that would have frozen.
 * - `KK_STASIS_KNOBS` overrides knobs (`freezeSpeed=…,thawSpeed=…,speedFilterSeconds=…,freezeSeconds=…,
 *   thawSeconds=…`); `;` separates sets that run one after another.
 * - `KK_STASIS_APEX_BENEFIT=1` also plays the Apex benefit population (heavy).
 * - `KK_STASIS_PROBE_ASSERT=1` fails a `gates` run in which any run froze (or would have).
 *
 * Sampling is per input frame (0.02 s). One command per mode; `--rerun` makes Gradle run the test again with new
 * variables, and the lines land in the suite's JUnit XML (`build/test-results/desktopTest/TEST-…CoreStasisBotProbeTest.xml`):
 *
 * `KK_STASIS_PROBE=2501..2800 ./gradlew -Pkinetickk.variant=release :ball:gameplay:nucleus:desktopTest --tests
 * 'kinetickk.ball.gameplay.nucleus.simulation.flight.CoreStasisBotProbeTest' --rerun`, then the same with
 * `KK_STASIS_PROBE=gates`.
 */
class CoreStasisBotProbeTest {
    @Test
    fun printsThePeakFreezeOfEveryBotRun() {
        val mode = env("KK_STASIS_PROBE") ?: return
        val gates = mode == "gates"
        val window = if (gates) null else probeWindow(mode, defaultLength = 300)
        val bots = env("KK_STASIS_BOTS")?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }
            ?: listOf("NOPOI", "SITE", "DUNGEON", "DIMENSION", "APEX")
        val cap = env("KK_STASIS_RUNS")?.toInt()
        val benefit = env("KK_STASIS_APEX_BENEFIT") == "1"
        val lethal = env("KK_STASIS_LETHAL") == "1"
        for (knobs in env("KK_STASIS_KNOBS").orEmpty().split(';')) {
            val tuning = probeTuning(knobs, lethal)
            val probe = StasisProbe(canonicalGameplayContent.copy(coreStasis = tuning), tuning.freezeSpeed, window, cap)
            println("STASIS-PROBE mode=${window ?: "gates"} knobs='$knobs' lethal=$lethal freezeSpeed=${tuning.freezeSpeed} " +
                "thawSpeed=${tuning.thawSpeed} freezeSeconds=${tuning.freezeSeconds}")
            for (bot in bots) when (bot) {
                "NOPOI" -> probe.noPoi()
                "SITE" -> probe.sites()
                "DUNGEON" -> probe.dungeons()
                "DIMENSION" -> probe.dimensions()
                "APEX" -> probe.apex(benefit)
                else -> error("unknown bot $bot")
            }
            println("STASIS-PROBE VERDICT knobs='$knobs' lethal=$lethal frozen=${probe.frozen} (pass when 0)")
            if (gates && env("KK_STASIS_PROBE_ASSERT") == "1") assertEquals(0, probe.frozen, "gate runs froze")
        }
    }
}

private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

/** The shipped rule, measure-only unless [lethal], overridden by [knobs] (`name=value`, comma-separated). */
private fun probeTuning(knobs: String, lethal: Boolean): CoreStasisTuning {
    var tuning = CoreStasisTuning(applyEffects = lethal)
    for (pair in knobs.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
        val (name, text) = pair.split('=').map { it.trim() }
        val value = text.toFloat()
        tuning = when (name) {
            "freezeSpeed" -> tuning.copy(freezeSpeed = value)
            "thawSpeed" -> tuning.copy(thawSpeed = value)
            "speedFilterSeconds" -> tuning.copy(speedFilterSeconds = value)
            "freezeSeconds" -> tuning.copy(freezeSeconds = value)
            "thawSeconds" -> tuning.copy(thawSeconds = value)
            else -> error("unknown Core Freeze knob $name")
        }
    }
    return tuning
}

/** The populations of one knob set: each run gets its own [StasisTrack]; [frozen] counts runs that froze (or would). */
private class StasisProbe(
    val content: GameplayContentSnapshot,
    val freezeSpeed: Float,
    val window: IntRange?,
    val cap: Int?,
) {
    var frozen = 0
        private set

    private fun seeds(gateSeeds: List<Int>): List<Int> {
        val all = window?.let(::windowSeeds) ?: gateSeeds
        return if (cap != null) all.take(cap) else all
    }

    private fun track(population: String, seed: Int, case: String = ""): StasisTrack = StasisTrack(population, seed, freezeSpeed, case)

    private fun report(population: String, tracks: List<StasisTrack>) {
        tracks.forEach { println(it.line()) }
        fun quantile(values: List<Float>, q: Double): Float {
            val sorted = values.sorted()
            return if (sorted.isEmpty()) 0f else sorted[min(sorted.lastIndex, ceil(q * sorted.size).toInt() - 1).coerceAtLeast(0)]
        }
        val peaks = tracks.map { it.peakFreeze }
        val slow = tracks.map { it.longestSlow }
        val froze = tracks.count { it.froze }
        frozen += froze
        val worst = tracks.sortedByDescending { it.peakFreeze }.take(5).joinToString(",") { "${it.seed}${it.caseTag}:${"%.2f".format(it.peakFreeze)}" }
        println("STASIS-PROBE SUMMARY $population runs=${tracks.size} frozen=$froze/${tracks.size} " +
            "peak max=${"%.2f".format(peaks.maxOrNull() ?: 0f)} p99=${"%.2f".format(quantile(peaks, 0.99))} " +
            "p95=${"%.2f".format(quantile(peaks, 0.95))} median=${"%.2f".format(quantile(peaks, 0.5))} " +
            "over0.5=${peaks.count { it >= 0.5f }} slow max=${"%.2f".format(slow.maxOrNull() ?: 0f)}s " +
            "p99=${"%.2f".format(quantile(slow, 0.99))}s median=${"%.2f".format(quantile(slow, 0.5))}s " +
            "filteredSlow max=${"%.2f".format(tracks.maxOfOrNull { it.longestFilteredSlow } ?: 0f)}s worst=$worst")
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

/**
 * One run under Core Freeze: its peak freeze and where it happened, the longest spells with the raw and the filtered
 * speed under the freeze speed, and whether it ended frozen (lethal) or reached a full freeze (measure-only).
 */
private class StasisTrack(val population: String, val seed: Int, private val freezeSpeed: Float, private val case: String) {
    var peakFreeze = 0f
        private set
    var longestSlow = 0f
        private set
    var longestFilteredSlow = 0f
        private set
    var froze = false
        private set
    /** The case compactly (`/RING/10`), for the summary's worst list. */
    val caseTag: String get() = case.split(' ').filter { it.startsWith("shape=") || it.startsWith("tier=") }
        .joinToString("") { "/" + it.substringAfter('=') }
    private var peakAt = 0f
    private var slowSince = Float.NaN
    private var filteredSince = Float.NaN
    private var context = ""
    private var endPhase = GamePhase.RUNNING
    private var endAt = 0f

    fun observe(s: MutableGameState) {
        endPhase = s.phase
        endAt = s.elapsed
        val stasis = s.coreStasis
        if (s.phase == GamePhase.GAME_OVER && s.message == CoreStasisNotices.FROZEN) froze = true
        if (s.phase == GamePhase.RUNNING) {
            slowSince = spell(s.speed < freezeSpeed, slowSince, s.elapsed) { longestSlow = max(longestSlow, it) }
            filteredSince = spell(stasis.speedFilter < freezeSpeed, filteredSince, s.elapsed) { longestFilteredSlow = max(longestFilteredSlow, it) }
        }
        if (stasis.freeze >= 1f) froze = true
        if (stasis.freeze > peakFreeze) {
            peakFreeze = stasis.freeze
            peakAt = s.elapsed
            context = "layer=${s.worldLayer} sealed=${s.structureEngagementActive()} site=${s.activeWindowSite()?.kind} " +
                "boss=${s.enemies.any { it.alive && it.isBossClass }} architect=${s.liveArchitect() != null} " +
                "braking=${s.braking} speed=${s.speed.toInt()} filtered=${stasis.speedFilter.toInt()} " +
                "core=${s.coreX.toInt()},${s.coreY.toInt()}"
        }
    }

    /** The start of the running spell after this frame ([slow] or not), reporting each spell's length so far. */
    private inline fun spell(slow: Boolean, since: Float, now: Float, report: (Float) -> Unit): Float {
        if (!slow) return Float.NaN
        val start = if (since.isNaN()) now else since
        report(now - start)
        return start
    }

    fun line(): String = "STASIS-PROBE $population seed=$seed$case end=$endPhase@${"%.1f".format(endAt)}s froze=$froze " +
        "peak=${"%.2f".format(peakFreeze)} at=${"%.1f".format(peakAt)}s slow=${"%.2f".format(longestSlow)}s " +
        "filteredSlow=${"%.2f".format(longestFilteredSlow)}s $context"
}
