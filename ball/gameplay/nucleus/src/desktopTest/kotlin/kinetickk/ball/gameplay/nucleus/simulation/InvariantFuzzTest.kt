// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The invariant fuzz of [fuzzSession] (InvariantFuzzHarness.kt) on seeds `i * 7_919 + 13`, never on the gate population
 * (i = 1,001..1,300).
 *
 * - The detector check proves the harness's own scans and comparisons see what they guard.
 * - The smoke runs on every build: [SMOKE_SEEDS] sessions from i = [SMOKE_FIRST], each capped at 300 simulated seconds,
 *   every tier in both modes, and the first one played twice to show a session is deterministic.
 * - The wide fuzz runs only with `KK_FUZZ=first..last` (seed indices): full sessions of up to 900 s (every 25th seed
 *   twice, to show it ends identically) on a pool of
 *   `KK_FUZZ_THREADS` threads (default one per processor, at most eight), every result and break appended to
 *   `KK_FUZZ_REPORT` (default `kinetickk-fuzz-report.txt` in the temporary directory) as it lands. For exact rechecks,
 *   `KK_FUZZ_INDICES` selects comma-separated unique indices inside the window, excluding the gate population; without
 *   it, the whole window runs. `KK_FUZZ_TRACE=from..to`
 *   prints every frame of that step range (with a single seed, to minimise a break), `KK_FUZZ_SCAN=n` scans every
 *   retained value for non-finite numbers every n frames instead of every frame. To minimise a break, `KK_FUZZ_JITTER`,
 *   `KK_FUZZ_RANDOM_CHOICES` replace the plan's jitter and random-choice share, `KK_FUZZ_NO_RESTART=1` drops its restart
 *   and rebirth, and `KK_FUZZ_PILOT` (bot, site, dungeon, dimension, seek) flies one pilot kind only.
 */
class InvariantFuzzTest {
    @Test fun smokeSessionsHoldEveryInvariant() {
        val indices = (SMOKE_FIRST until SMOKE_FIRST + SMOKE_SEEDS).toList()
        val results = populationOf(indices) { index -> fuzzSession(FuzzPlan.of(index, smoke = true)) }
        results.forEach { result ->
            println("FUZZ SMOKE ${result.summary()} | ${result.plan.label()}")
            result.breaks.forEach { println(it.line()) }
        }
        val replay = fuzzSession(FuzzPlan.of(indices.first(), smoke = true))
        assertEquals(results.first().summary(), replay.summary(), "seed index ${indices.first()} replays identically")
        val breaks = results.flatMap { it.breaks }
        assertTrue(breaks.isEmpty(), "${breaks.size} invariant breaks:\n" + breaks.joinToString("\n") { it.line() })
    }

    /**
     * The harness's own detectors are not vacuous: a played state scans clean and equals its reduction copy, while a NaN
     * deep in an entity or a value, an infinity outside the sentinel fields, and a fact the copy lost or a reduction wrote
     * are each found and named.
     */
    @Test fun detectorsFindWhatTheyGuard() {
        val state = MutableGameState(canonicalGameplayContent, seed = 3_401 * 7_919 + 13)
        state.startRun()
        repeat(500) { if (state.phase == GamePhase.RUNNING) state.update(0.02f) }
        assertTrue(state.enemies.isNotEmpty(), "the probe state has enemies")
        assertNull(FuzzFiniteScan.first(state), "a played state holds no non-finite value")
        assertNull(FuzzStateDiff.first(state, state.copyForReduction(), "state", FuzzScratchFields), "a reduction copy loses nothing")

        val copy = state.copyForReduction()
        copy.enemies[0].hp += 1f
        assertEquals("state.enemies[0].hp", FuzzStateDiff.first(state, copy, "state", FuzzScratchFields)?.substringBefore(':'))
        val written = state.copyForReduction()
        written.runBoons = immutableListOf(0)
        assertEquals("state.runBoons.size", FuzzStateDiff.first(state, written, "state", FuzzScratchFields)?.substringBefore(':'))

        state.coreObstacleGap = Float.POSITIVE_INFINITY
        assertNull(FuzzFiniteScan.first(state), "an infinite obstacle gap means no obstacle near")
        state.encounters = immutableListOf(EncounterState(encounterTag(EncounterOwner.SITE, 7L), Float.NaN, 0f, 300f, 2_400f, 1, 0, -1, 0f))
        assertEquals("state.encounters[0].centerX = NaN", FuzzFiniteScan.first(state))
        state.encounters = immutableListOf()
        state.enemies[0].vy = Float.NaN
        assertEquals("state.enemies[0].vy = NaN", FuzzFiniteScan.first(state))
        state.enemies[0].vy = 0f
        state.overheatHoldTime = Float.POSITIVE_INFINITY
        assertEquals("state.overheatHoldTime = Infinity", FuzzFiniteScan.first(state))
    }

    @Test fun wideFuzzHoldsEveryInvariant() {
        val window = System.getenv("KK_FUZZ")?.takeIf { it.isNotBlank() } ?: return
        val (first, last) = window.split("..").map { it.trim().toInt() }
        val selection = System.getenv("KK_FUZZ_INDICES")
        val indices = wideFuzzIndices(first, last, selection)
        val threads = System.getenv("KK_FUZZ_THREADS")?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val trace = System.getenv("KK_FUZZ_TRACE")?.takeIf { it.isNotBlank() }?.split("..")?.map { it.trim().toLong() }
        val options = FuzzOptions(
            scanEvery = System.getenv("KK_FUZZ_SCAN")?.toIntOrNull() ?: 1,
            traceFrom = trace?.get(0) ?: Long.MAX_VALUE,
            traceTo = trace?.get(1) ?: Long.MAX_VALUE,
            jitter = System.getenv("KK_FUZZ_JITTER")?.toIntOrNull(),
            randomChoiceShare = System.getenv("KK_FUZZ_RANDOM_CHOICES")?.toFloatOrNull(),
            noRestart = System.getenv("KK_FUZZ_NO_RESTART") == "1",
            onlyPilot = System.getenv("KK_FUZZ_PILOT")?.takeIf { it.isNotBlank() },
        )
        val report = File(System.getenv("KK_FUZZ_REPORT") ?: File(System.getProperty("java.io.tmpdir"), "kinetickk-fuzz-report.txt").path)
        report.parentFile?.mkdirs()
        report.writeText("FUZZ window=$first..$last seeds=${indices.size} threads=$threads selection=${if (selection == null) "range" else "explicit"}\n")
        val pool = Executors.newFixedThreadPool(threads) { task -> Thread(task, "fuzz").apply { isDaemon = true } }
        val started = System.nanoTime()
        val results = try {
            indices.map { index ->
                pool.submit(Callable {
                    val plan = FuzzPlan.of(index, smoke = false)
                    var result = fuzzSession(plan, options)
                    // Every [REPLAY_EVERY]-th seed is played twice: the same seed must end identically.
                    if (index % REPLAY_EVERY == 0) {
                        val replay = fuzzSession(plan, options)
                        if (replay.summary() != result.summary()) {
                            result = result.copy(breaks = result.breaks + FuzzBreak(
                                plan.seed, 0, replay.steps, replay.seconds, FuzzInvariant.DETERMINISM,
                                "the replay ended differently: ${result.summary()} vs ${replay.summary()}", plan.label(),
                            ))
                        }
                    }
                    synchronized(report) {
                        report.appendText(buildString {
                            append("FUZZ i=$index ${result.summary()} | ${result.plan.label()}\n")
                            result.breaks.forEach { append(it.line()).append('\n') }
                        })
                    }
                    result
                })
            }.map { it.get() }
        } finally {
            pool.shutdown()
        }
        val breaks = results.flatMap { it.breaks }
        val seconds = (System.nanoTime() - started) / 1e9
        val byInvariant = breaks.groupBy { it.invariant }.entries.joinToString { "${it.key}=${it.value.size}" }
        val summary = "FUZZ DONE seeds=${results.size} breaks=${breaks.size} ($byInvariant) wall=${"%.0f".format(seconds)}s " +
            "simulated=${"%.0f".format(results.sumOf { it.seconds.toDouble() })}s visits=${results.sumOf { it.visits }} " +
            "byLayer=${(1 until 4).map { layer -> results.sumOf { it.visitedLayers[layer] } }} " +
            "seals=${results.sumOf { it.seals }} trials=${results.sumOf { it.siteTrials }} signals=${results.sumOf { it.signalPrompts }} " +
            "choices=${results.sumOf { it.choiceScreens }} runs=${results.sumOf { it.runs }}"
        synchronized(report) { report.appendText(summary + "\n") }
        println(summary)
        assertTrue(breaks.isEmpty(), "${breaks.size} invariant breaks (report ${report.path}):\n" + breaks.joinToString("\n") { it.line() })
    }

    @Test fun wideFuzzWithoutSelectionKeepsTheWholeWindowExceptTheGatePopulation() {
        assertEquals((3_401..3_404).toList(), wideFuzzIndices(3_401, 3_404, null))
        assertEquals(listOf(999, 1_000, 1_301, 1_302), wideFuzzIndices(999, 1_302, null))
    }

    @Test fun wideFuzzSelectionKeepsTheRequestedIndicesAndOrder() {
        assertEquals(listOf(5_488, 3_416, 7_400), wideFuzzIndices(3_401, 7_400, "5488, 3416,7400"))
    }

    @Test fun wideFuzzSelectionRejectsMalformedDuplicateOutOfWindowAndGateIndices() {
        for (selection in listOf("", " ", "3416,,5488", "3416,unknown", "3416,3416", "3400", "7401", "2147483648")) {
            assertFailsWith<IllegalArgumentException>("selection '$selection' must be rejected") {
                wideFuzzIndices(3_401, 7_400, selection)
            }
        }
        for (selection in listOf("1001", "1300", "1000,1100,3401")) {
            assertFailsWith<IllegalArgumentException>("gate selection '$selection' must be rejected") {
                wideFuzzIndices(1_000, 3_401, selection)
            }
        }
        assertFailsWith<IllegalArgumentException> { wideFuzzIndices(3_402, 3_401, null) }
    }

    private fun wideFuzzIndices(first: Int, last: Int, selection: String?): List<Int> {
        require(first <= last) { "KK_FUZZ must be an ascending window" }
        if (selection == null) return (first..last).filter { it !in POPULATION_INDICES }
        val indices = selection.split(',').map { token ->
            requireNotNull(token.trim().toIntOrNull()) { "KK_FUZZ_INDICES contains an invalid index: '$token'" }
        }
        require(indices.distinct().size == indices.size) { "KK_FUZZ_INDICES must not contain duplicates" }
        require(indices.all { it in first..last }) { "KK_FUZZ_INDICES must stay inside KK_FUZZ=$first..$last" }
        require(indices.none { it in POPULATION_INDICES }) { "KK_FUZZ_INDICES must not include the gate population" }
        return indices
    }

    private companion object {
        /** The smoke's first seed index: past every gate and calibration window (i ≤ 3,400). */
        const val SMOKE_FIRST = 3_401
        const val SMOKE_SEEDS = 20

        /** The wide fuzz plays every seed index divisible by this twice. */
        const val REPLAY_EVERY = 25

        /** The gate population's indices ([PopulationSeeds]); the fuzz never plays them. */
        val POPULATION_INDICES = 1_001..1_300
    }
}
