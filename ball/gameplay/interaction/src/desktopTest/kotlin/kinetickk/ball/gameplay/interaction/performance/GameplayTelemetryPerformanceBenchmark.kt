// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.performance

import kinetickk.ball.gameplay.interaction.canvas.toPerformanceHudProjection
import kinetickk.performance.BenchmarkScenario
import kinetickk.performance.BenchmarkSuiteIdentity
import kinetickk.performance.BenchmarkValidation
import kinetickk.performance.BenchmarkValidationContext
import kinetickk.performance.runBenchmarkSuite
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private const val TELEMETRY_SUITE_VERSION = "gameplay-telemetry-v2"
private const val WINDOW_SIZE = 600

fun main() {
    runBenchmarkSuite(
        identity = BenchmarkSuiteIdentity(
            suiteVersion = TELEMETRY_SUITE_VERSION,
            adapter = "feature-pokeball-full-refactor",
            label = System.getProperty(
                "kinetickk.benchmark.label",
                "feature/pokeball-full-refactor",
            ),
            revision = System.getProperty("kinetickk.benchmark.revision", "unknown"),
            dirty = System.getProperty("kinetickk.benchmark.dirty", "false").toBoolean(),
        ),
        scenarios = GameplayTelemetryBenchmarkFixtures().scenarios(),
    )
}

private class GameplayTelemetryBenchmarkFixtures {
    private var recordingTelemetry = populatedTelemetry()
    private val fullWindowTelemetry = populatedTelemetry()
    private val emptyTelemetry = GameplayPerformanceTelemetry()
    private val fullWindowSnapshot = fullWindowTelemetry.snapshot()
    private var counter = 1L
    @Volatile
    private var performanceEnabled = false

    fun scenarios(): List<BenchmarkScenario> = listOf(
        BenchmarkScenario(
            name = "telemetry_harness_control",
            category = "harness",
            description = "Counter and blackhole floor for telemetry observer scenarios.",
            maximumOperations = 5_000_000,
        ) { validation ->
            val result = nextCounter()
            validation.validated(result) { result }
        },
        BenchmarkScenario(
            name = "telemetry_disabled_guard",
            category = "disabled_overhead",
            description = "Disabled per-frame branch; it must not read the clock or mutate telemetry.",
            metadata = telemetryMetadata("enabled" to "false"),
            maximumOperations = 5_000_000,
        ) { validation ->
            if (performanceEnabled) recordingTelemetry.recordFrameIntervalMillis(16.67)
            val result = nextCounter()
            validation.validated(result) { telemetryWitness(result, recordingTelemetry) }
        },
        BenchmarkScenario(
            name = "telemetry_monotonic_timer_pair",
            category = "enabled_overhead",
            description = "Monotonic mark plus elapsed read used around dispatch and Canvas recording.",
            metadata = telemetryMetadata("clockReads" to "2"),
            maximumOperations = 1_000_000,
        ) { validation ->
            val mark = TimeSource.Monotonic.markNow()
            mark.elapsedNow().toLong(DurationUnit.NANOSECONDS)
            val result = nextCounter()
            validation.validated(result) { result }
        },
        BenchmarkScenario(
            name = "telemetry_record_frame",
            category = "enabled_overhead",
            description = "Append one frame interval into an already full bounded rolling window.",
            metadata = telemetryMetadata("distribution" to "frame"),
            maximumOperations = 2_000_000,
        ) { validation ->
            val value = 12.0 + (nextCounter() and 15L)
            recordingTelemetry.recordFrameIntervalMillis(value)
            validation.validated(counter) { telemetryWitness(counter, recordingTelemetry) }
        },
        BenchmarkScenario(
            name = "telemetry_record_dispatch",
            category = "enabled_overhead",
            description = "Append one dispatch duration into an already full bounded rolling window.",
            metadata = telemetryMetadata("distribution" to "dispatch"),
            maximumOperations = 2_000_000,
        ) { validation ->
            val value = 0.1 + (nextCounter() and 7L) / 10.0
            recordingTelemetry.recordDispatchPipelineMillis(value)
            validation.validated(counter) { telemetryWitness(counter, recordingTelemetry) }
        },
        BenchmarkScenario(
            name = "telemetry_record_canvas",
            category = "enabled_overhead",
            description = "Append one Canvas CPU duration into an already full bounded rolling window.",
            metadata = telemetryMetadata("distribution" to "canvas"),
            maximumOperations = 2_000_000,
        ) { validation ->
            val value = 0.2 + (nextCounter() and 7L) / 10.0
            recordingTelemetry.recordCanvasDrawMillis(value)
            validation.validated(counter) { telemetryWitness(counter, recordingTelemetry) }
        },
        BenchmarkScenario(
            name = "telemetry_record_entity_counts",
            category = "enabled_overhead",
            description = "Publish current and peak entity cardinalities after an accepted frame.",
            metadata = telemetryMetadata("entities" to "canonical-capacity"),
            maximumOperations = 5_000_000,
        ) { validation ->
            recordingTelemetry.recordEntityCounts(
                enemies = 120,
                projectiles = 650,
                pickups = 420,
                trailPoints = 110,
            )
            val result = nextCounter()
            validation.validated(result) { telemetryWitness(result, recordingTelemetry) }
        },
        BenchmarkScenario(
            name = "telemetry_snapshot_empty",
            category = "publication",
            description = "Create the immutable HUD snapshot before any samples exist.",
            metadata = telemetryMetadata("samples" to "0"),
            maximumOperations = 1_000_000,
        ) { validation ->
            val result = snapshotSignature(emptyTelemetry.snapshot())
            validation.validated(result) { result }
        },
        BenchmarkScenario(
            name = "telemetry_snapshot_full_window",
            category = "publication",
            description = "Copy, sort and summarize all three full 600-sample distributions.",
            metadata = telemetryMetadata("samplesPerDistribution" to WINDOW_SIZE.toString()),
            maximumOperations = 20_000,
        ) { validation ->
            val result = snapshotSignature(fullWindowTelemetry.snapshot())
            validation.validated(result) { result }
        },
        BenchmarkScenario(
            name = "telemetry_hud_projection_full_window",
            category = "publication",
            description = "Format all immutable HUD lines from one full-window snapshot.",
            metadata = telemetryMetadata("samplesPerDistribution" to WINDOW_SIZE.toString()),
            maximumOperations = 100_000,
        ) { validation ->
            val projection = fullWindowSnapshot.toPerformanceHudProjection()
            val result = projection.frameLine.length.toLong() +
                projection.dispatchLine.length +
                projection.canvasLine.length +
                projection.rateLine.length +
                projection.entitiesLine.length
            validation.validated(result) {
                var witness = projection.frameLine.hashCode().toLong()
                witness = mixTelemetryWitness(witness, projection.dispatchLine.hashCode().toLong())
                witness = mixTelemetryWitness(witness, projection.canvasLine.hashCode().toLong())
                witness = mixTelemetryWitness(witness, projection.rateLine.hashCode().toLong())
                mixTelemetryWitness(witness, projection.entitiesLine.hashCode().toLong())
            }
        },
    ).map { scenario ->
        val expectedTimedResult = expectedTimedResult(scenario.name)
        val expectedOutcomeWitness = expectedOutcomeWitness(scenario.name)
        scenario.copy(
            metadata = scenario.metadata +
                ("outcomeFingerprint" to expectedOutcomeWitness.toString()),
            validation = BenchmarkValidation(
                expectedTimedResult = expectedTimedResult,
                expectedOutcomeWitness = expectedOutcomeWitness,
                prepareProbe = { prepareProbe(scenario.name) },
            ),
        )
    }

    private fun expectedTimedResult(name: String): Long = when (name) {
        "telemetry_harness_control",
        "telemetry_disabled_guard",
        "telemetry_monotonic_timer_pair",
        "telemetry_record_frame",
        "telemetry_record_dispatch",
        "telemetry_record_canvas",
        "telemetry_record_entity_counts" -> nextCounterValue(1L)
        "telemetry_snapshot_empty" -> snapshotSignature(GameplayPerformanceTelemetry().snapshot())
        "telemetry_snapshot_full_window" -> snapshotSignature(populatedTelemetry().snapshot())
        "telemetry_hud_projection_full_window" -> hudTimedResult()
        else -> error("Missing expected timed result for telemetry benchmark scenario: $name")
    }

    private fun expectedOutcomeWitness(name: String): Long {
        val result = expectedTimedResult(name)
        return when (name) {
            "telemetry_harness_control", "telemetry_monotonic_timer_pair" -> result
            "telemetry_disabled_guard" -> telemetryWitness(
                result,
                GameplayPerformanceTelemetry(),
            )
            "telemetry_record_frame" -> populatedTelemetry().let { telemetry ->
                telemetry.recordFrameIntervalMillis(12.0 + (result and 15L))
                telemetryWitness(result, telemetry)
            }
            "telemetry_record_dispatch" -> populatedTelemetry().let { telemetry ->
                telemetry.recordDispatchPipelineMillis(0.1 + (result and 7L) / 10.0)
                telemetryWitness(result, telemetry)
            }
            "telemetry_record_canvas" -> populatedTelemetry().let { telemetry ->
                telemetry.recordCanvasDrawMillis(0.2 + (result and 7L) / 10.0)
                telemetryWitness(result, telemetry)
            }
            "telemetry_record_entity_counts" -> GameplayPerformanceTelemetry().let { telemetry ->
                telemetry.recordEntityCounts(
                    enemies = 120,
                    projectiles = 650,
                    pickups = 420,
                    trailPoints = 110,
                )
                telemetryWitness(result, telemetry)
            }
            "telemetry_snapshot_empty", "telemetry_snapshot_full_window" -> result
            "telemetry_hud_projection_full_window" -> hudOutcomeWitness()
            else -> error("Missing expected outcome witness for telemetry benchmark scenario: $name")
        }
    }

    private fun prepareProbe(name: String) {
        counter = 1L
        performanceEnabled = false
        recordingTelemetry = when (name) {
            "telemetry_disabled_guard", "telemetry_record_entity_counts" ->
                GameplayPerformanceTelemetry()
            else -> populatedTelemetry()
        }
    }

    private fun hudTimedResult(): Long {
        val projection = populatedTelemetry().snapshot().toPerformanceHudProjection()
        return projection.frameLine.length.toLong() +
            projection.dispatchLine.length +
            projection.canvasLine.length +
            projection.rateLine.length +
            projection.entitiesLine.length
    }

    private fun hudOutcomeWitness(): Long {
        val projection = populatedTelemetry().snapshot().toPerformanceHudProjection()
        var witness = projection.frameLine.hashCode().toLong()
        witness = mixTelemetryWitness(witness, projection.dispatchLine.hashCode().toLong())
        witness = mixTelemetryWitness(witness, projection.canvasLine.hashCode().toLong())
        witness = mixTelemetryWitness(witness, projection.rateLine.hashCode().toLong())
        return mixTelemetryWitness(witness, projection.entitiesLine.hashCode().toLong())
    }

    private fun nextCounter(): Long {
        counter = nextCounterValue(counter)
        return counter
    }

    private fun nextCounterValue(value: Long): Long =
        value * 2_862_933_555_777_941_757L + 3_037_000_493L
}

private fun populatedTelemetry(): GameplayPerformanceTelemetry =
    GameplayPerformanceTelemetry(maxSamples = WINDOW_SIZE).also { telemetry ->
        repeat(WINDOW_SIZE) { index ->
            telemetry.recordFrameIntervalMillis(8.0 + (index % 48) * 0.5)
            telemetry.recordDispatchPipelineMillis(0.1 + (index % 17) * 0.05)
            telemetry.recordCanvasDrawMillis(0.2 + (index % 23) * 0.1)
        }
        telemetry.recordEntityCounts(
            enemies = 120,
            projectiles = 650,
            pickups = 420,
            trailPoints = 110,
        )
    }

private fun telemetryMetadata(vararg values: Pair<String, String>): Map<String, String> = buildMap {
    put("windowCapacity", WINDOW_SIZE.toString())
    put("publicationIntervalMillis", "500")
    putAll(values)
}

private inline fun BenchmarkValidationContext.validated(
    result: Long,
    witness: () -> Long,
): Long {
    observeOutcome(witness)
    return result
}

private fun telemetryWitness(
    result: Long,
    telemetry: GameplayPerformanceTelemetry,
): Long = mixTelemetryWitness(result, snapshotSignature(telemetry.snapshot()))

private fun mixTelemetryWitness(left: Long, right: Long): Long =
    (left xor java.lang.Long.rotateLeft(right, 23)) * -7_046_029_254_386_353_131L

private fun snapshotSignature(snapshot: GameplayPerformanceSnapshot): Long {
    var signature = snapshot.frameInterval.totalSampleCount
    signature = signature xor java.lang.Long.rotateLeft(
        snapshot.dispatchPipeline.totalSampleCount,
        11,
    )
    signature = signature xor java.lang.Long.rotateLeft(snapshot.canvasDraw.totalSampleCount, 23)
    signature = signature xor snapshot.frameInterval.p99Millis.toBits()
    signature = signature xor snapshot.framesPerSecond.toBits()
    signature = signature xor snapshot.peakEnemies.toLong()
    signature = signature xor java.lang.Long.rotateLeft(snapshot.peakProjectiles.toLong(), 7)
    return signature
}
