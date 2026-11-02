// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.performance

import kotlin.math.ceil

private const val DEFAULT_MAX_PERFORMANCE_SAMPLES: Int = 600
internal const val PERFORMANCE_SNAPSHOT_INTERVAL_NANOS: Long = 500_000_000L

internal data class PerformanceDurationStats(
    val sampleCount: Int,
    val totalSampleCount: Long,
    val meanMillis: Double,
    val p50Millis: Double,
    val p95Millis: Double,
    val p99Millis: Double,
    val maxMillis: Double,
    val slowestOnePercentMeanMillis: Double,
) {
    companion object {
        val Empty = PerformanceDurationStats(
            sampleCount = 0,
            totalSampleCount = 0L,
            meanMillis = 0.0,
            p50Millis = 0.0,
            p95Millis = 0.0,
            p99Millis = 0.0,
            maxMillis = 0.0,
            slowestOnePercentMeanMillis = 0.0,
        )
    }
}

internal data class GameplayPerformanceSnapshot(
    val frameInterval: PerformanceDurationStats,
    val dispatchPipeline: PerformanceDurationStats,
    val canvasDraw: PerformanceDurationStats,
    val framesPerSecond: Double,
    val onePercentLowFramesPerSecond: Double,
    val framesOver16MillisFraction: Double,
    val framesOver33MillisFraction: Double,
    val currentEnemies: Int,
    val peakEnemies: Int,
    val currentProjectiles: Int,
    val peakProjectiles: Int,
    val currentPickups: Int,
    val peakPickups: Int,
    val currentTrailPoints: Int,
    val peakTrailPoints: Int,
) {
    companion object {
        val Empty = GameplayPerformanceSnapshot(
            frameInterval = PerformanceDurationStats.Empty,
            dispatchPipeline = PerformanceDurationStats.Empty,
            canvasDraw = PerformanceDurationStats.Empty,
            framesPerSecond = 0.0,
            onePercentLowFramesPerSecond = 0.0,
            framesOver16MillisFraction = 0.0,
            framesOver33MillisFraction = 0.0,
            currentEnemies = 0,
            peakEnemies = 0,
            currentProjectiles = 0,
            peakProjectiles = 0,
            currentPickups = 0,
            peakPickups = 0,
            currentTrailPoints = 0,
            peakTrailPoints = 0,
        )
    }
}

/**
 * Interaction-owned rolling telemetry. It contains no clock and never crosses into Nucleus.
 * Callers provide measured durations and the frame timestamp used only to rate-limit snapshots.
 */
internal class GameplayPerformanceTelemetry(
    private val maxSamples: Int = DEFAULT_MAX_PERFORMANCE_SAMPLES,
    private val snapshotIntervalNanos: Long = PERFORMANCE_SNAPSHOT_INTERVAL_NANOS,
) {
    // Telemetry is disabled by default. Allocate the three 600-sample buffers only after the
    // player enables it, and release them again on reset/disable.
    private var frameIntervals: BoundedDurationSamples? = null
    private var dispatchPipelines: BoundedDurationSamples? = null
    private var canvasDraws: BoundedDurationSamples? = null

    private var lastSnapshotFrameTimeNanos: Long? = null
    private var currentEnemies: Int = 0
    private var peakEnemies: Int = 0
    private var currentProjectiles: Int = 0
    private var peakProjectiles: Int = 0
    private var currentPickups: Int = 0
    private var peakPickups: Int = 0
    private var currentTrailPoints: Int = 0
    private var peakTrailPoints: Int = 0

    init {
        require(maxSamples > 0) { "maxSamples must be positive" }
        require(snapshotIntervalNanos > 0L) { "snapshotIntervalNanos must be positive" }
    }

    fun recordFrameIntervalMillis(value: Double) {
        frameIntervals().add(value)
    }

    fun recordDispatchPipelineMillis(value: Double) {
        dispatchPipelines().add(value)
    }

    fun recordCanvasDrawMillis(value: Double) {
        canvasDraws().add(value)
    }

    fun recordEntityCounts(
        enemies: Int,
        projectiles: Int,
        pickups: Int,
        trailPoints: Int,
    ) {
        require(enemies >= 0 && projectiles >= 0 && pickups >= 0 && trailPoints >= 0) {
            "entity counts must be non-negative"
        }
        currentEnemies = enemies
        peakEnemies = maxOf(peakEnemies, enemies)
        currentProjectiles = projectiles
        peakProjectiles = maxOf(peakProjectiles, projectiles)
        currentPickups = pickups
        peakPickups = maxOf(peakPickups, pickups)
        currentTrailPoints = trailPoints
        peakTrailPoints = maxOf(peakTrailPoints, trailPoints)
    }

    /** Starts a fresh 500 ms window on the first frame and publishes at most twice per second. */
    fun shouldPublishSnapshot(frameTimeNanos: Long): Boolean {
        val previousFrameTimeNanos = lastSnapshotFrameTimeNanos
        if (previousFrameTimeNanos == null || frameTimeNanos < previousFrameTimeNanos) {
            lastSnapshotFrameTimeNanos = frameTimeNanos
            return false
        }
        if (frameTimeNanos - previousFrameTimeNanos < snapshotIntervalNanos) return false
        lastSnapshotFrameTimeNanos = frameTimeNanos
        return true
    }

    fun snapshot(): GameplayPerformanceSnapshot {
        val frameInterval = frameIntervals?.snapshot() ?: PerformanceDurationStats.Empty
        return GameplayPerformanceSnapshot(
            frameInterval = frameInterval,
            dispatchPipeline = dispatchPipelines?.snapshot() ?: PerformanceDurationStats.Empty,
            canvasDraw = canvasDraws?.snapshot() ?: PerformanceDurationStats.Empty,
            framesPerSecond = framesPerSecond(frameInterval.meanMillis),
            onePercentLowFramesPerSecond = framesPerSecond(frameInterval.slowestOnePercentMeanMillis),
            framesOver16MillisFraction = frameIntervals?.fractionAbove(16.67) ?: 0.0,
            framesOver33MillisFraction = frameIntervals?.fractionAbove(33.33) ?: 0.0,
            currentEnemies = currentEnemies,
            peakEnemies = peakEnemies,
            currentProjectiles = currentProjectiles,
            peakProjectiles = peakProjectiles,
            currentPickups = currentPickups,
            peakPickups = peakPickups,
            currentTrailPoints = currentTrailPoints,
            peakTrailPoints = peakTrailPoints,
        )
    }

    fun reset() {
        frameIntervals = null
        dispatchPipelines = null
        canvasDraws = null
        lastSnapshotFrameTimeNanos = null
        currentEnemies = 0
        peakEnemies = 0
        currentProjectiles = 0
        peakProjectiles = 0
        currentPickups = 0
        peakPickups = 0
        currentTrailPoints = 0
        peakTrailPoints = 0
    }

    private fun frameIntervals(): BoundedDurationSamples =
        frameIntervals ?: BoundedDurationSamples(maxSamples).also { frameIntervals = it }

    private fun dispatchPipelines(): BoundedDurationSamples =
        dispatchPipelines ?: BoundedDurationSamples(maxSamples).also { dispatchPipelines = it }

    private fun canvasDraws(): BoundedDurationSamples =
        canvasDraws ?: BoundedDurationSamples(maxSamples).also { canvasDraws = it }
}

private class BoundedDurationSamples(capacity: Int) {
    private val values = DoubleArray(capacity)
    private var nextIndex: Int = 0
    private var size: Int = 0
    private var totalSampleCount: Long = 0L

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun add(value: Double) {
        if (!value.isFinite() || value < 0.0) return
        values[nextIndex] = value
        nextIndex = (nextIndex + 1) % values.size
        if (size < values.size) size += 1
        totalSampleCount += 1L
    }

    fun snapshot(): PerformanceDurationStats {
        if (size == 0) return PerformanceDurationStats.Empty
        val sortedValues = DoubleArray(size) { values[it] }
        sortedValues.sort()
        return PerformanceDurationStats(
            sampleCount = size,
            totalSampleCount = totalSampleCount,
            meanMillis = sortedValues.sum() / size,
            p50Millis = sortedValues.percentile(0.50),
            p95Millis = sortedValues.percentile(0.95),
            p99Millis = sortedValues.percentile(0.99),
            maxMillis = sortedValues.last(),
            slowestOnePercentMeanMillis = sortedValues.slowestFractionMean(0.01),
        )
    }

    fun fractionAbove(threshold: Double): Double {
        if (size == 0) return 0.0
        var samplesAboveThreshold = 0
        repeat(size) { index ->
            if (values[index] > threshold) samplesAboveThreshold += 1
        }
        return samplesAboveThreshold.toDouble() / size
    }

}

private fun DoubleArray.percentile(fraction: Double): Double {
    if (isEmpty()) return 0.0
    val position = (lastIndex * fraction.coerceIn(0.0, 1.0))
    val lowerIndex = position.toInt()
    val upperIndex = minOf(lowerIndex + 1, lastIndex)
    val upperWeight = position - lowerIndex
    return this[lowerIndex] * (1.0 - upperWeight) + this[upperIndex] * upperWeight
}

private fun DoubleArray.slowestFractionMean(fraction: Double): Double {
    if (isEmpty()) return 0.0
    val sampleCount = ceil(size * fraction.coerceIn(0.0, 1.0)).toInt().coerceIn(1, size)
    var sum = 0.0
    for (index in size - sampleCount until size) sum += this[index]
    return sum / sampleCount
}

private fun framesPerSecond(frameMillis: Double): Double =
    if (frameMillis > 0.0) 1_000.0 / frameMillis else 0.0
