// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameplayPerformanceTelemetryTest {
    @Test
    fun rollingWindowIsBoundedAndReportsInterpolatedPercentiles() {
        val telemetry = GameplayPerformanceTelemetry(maxSamples = 4)
        listOf(10.0, 20.0, 30.0, 40.0, 50.0).forEach(telemetry::recordFrameIntervalMillis)

        val snapshot = telemetry.snapshot()

        assertEquals(4, snapshot.frameInterval.sampleCount)
        assertEquals(5L, snapshot.frameInterval.totalSampleCount)
        assertEquals(35.0, snapshot.frameInterval.meanMillis, absoluteTolerance = 0.000_001)
        assertEquals(35.0, snapshot.frameInterval.p50Millis, absoluteTolerance = 0.000_001)
        assertEquals(48.5, snapshot.frameInterval.p95Millis, absoluteTolerance = 0.000_001)
        assertEquals(49.7, snapshot.frameInterval.p99Millis, absoluteTolerance = 0.000_001)
        assertEquals(50.0, snapshot.frameInterval.maxMillis, absoluteTolerance = 0.000_001)
        assertEquals(1.0, snapshot.framesOver16MillisFraction, absoluteTolerance = 0.000_001)
        assertEquals(0.5, snapshot.framesOver33MillisFraction, absoluteTolerance = 0.000_001)
        assertEquals(1_000.0 / 35.0, snapshot.framesPerSecond, absoluteTolerance = 0.000_001)
        assertEquals(20.0, snapshot.onePercentLowFramesPerSecond, absoluteTolerance = 0.000_001)
    }

    @Test
    fun distributionsAndEntityPeaksAreIndependentAndResetTogether() {
        val telemetry = GameplayPerformanceTelemetry(maxSamples = 8)
        telemetry.recordDispatchPipelineMillis(1.0)
        telemetry.recordDispatchPipelineMillis(5.0)
        telemetry.recordCanvasDrawMillis(2.0)
        telemetry.recordEntityCounts(enemies = 9, projectiles = 20, pickups = 4, trailPoints = 7)
        telemetry.recordEntityCounts(enemies = 3, projectiles = 21, pickups = 1, trailPoints = 5)

        val populated = telemetry.snapshot()
        assertEquals(2, populated.dispatchPipeline.sampleCount)
        assertEquals(3.0, populated.dispatchPipeline.p50Millis, absoluteTolerance = 0.000_001)
        assertEquals(1, populated.canvasDraw.sampleCount)
        assertEquals(3, populated.currentEnemies)
        assertEquals(9, populated.peakEnemies)
        assertEquals(21, populated.currentProjectiles)
        assertEquals(21, populated.peakProjectiles)
        assertEquals(1, populated.currentPickups)
        assertEquals(4, populated.peakPickups)
        assertEquals(5, populated.currentTrailPoints)
        assertEquals(7, populated.peakTrailPoints)

        telemetry.reset()

        assertEquals(GameplayPerformanceSnapshot.Empty, telemetry.snapshot())

        telemetry.recordFrameIntervalMillis(8.0)
        telemetry.recordDispatchPipelineMillis(2.0)
        telemetry.recordCanvasDrawMillis(3.0)

        val restarted = telemetry.snapshot()
        assertEquals(1, restarted.frameInterval.sampleCount)
        assertEquals(1, restarted.dispatchPipeline.sampleCount)
        assertEquals(1, restarted.canvasDraw.sampleCount)
        assertEquals(8.0, restarted.frameInterval.meanMillis, absoluteTolerance = 0.000_001)
    }

    @Test
    fun onePercentLowUsesTheMeanOfTheSlowestOnePercentOfFrames() {
        val telemetry = GameplayPerformanceTelemetry(maxSamples = 200)
        repeat(198) { telemetry.recordFrameIntervalMillis(10.0) }
        telemetry.recordFrameIntervalMillis(100.0)
        telemetry.recordFrameIntervalMillis(200.0)

        val snapshot = telemetry.snapshot()

        assertEquals(1_000.0 / 150.0, snapshot.onePercentLowFramesPerSecond, absoluteTolerance = 0.000_001)
    }

    @Test
    fun snapshotPublicationIsLimitedToTwoPerSecondAndResetStartsANewWindow() {
        val telemetry = GameplayPerformanceTelemetry(maxSamples = 8)
        val firstFrame = 1_000L

        assertFalse(telemetry.shouldPublishSnapshot(firstFrame))
        assertFalse(telemetry.shouldPublishSnapshot(firstFrame + PERFORMANCE_SNAPSHOT_INTERVAL_NANOS - 1L))
        assertTrue(telemetry.shouldPublishSnapshot(firstFrame + PERFORMANCE_SNAPSHOT_INTERVAL_NANOS))
        assertFalse(telemetry.shouldPublishSnapshot(firstFrame + PERFORMANCE_SNAPSHOT_INTERVAL_NANOS + 1L))

        telemetry.reset()

        assertFalse(telemetry.shouldPublishSnapshot(firstFrame + PERFORMANCE_SNAPSHOT_INTERVAL_NANOS * 3L))
    }

    @Test
    fun invalidDurationsAreIgnored() {
        val telemetry = GameplayPerformanceTelemetry(maxSamples = 8)
        telemetry.recordFrameIntervalMillis(Double.NaN)
        telemetry.recordDispatchPipelineMillis(Double.POSITIVE_INFINITY)
        telemetry.recordCanvasDrawMillis(-1.0)

        val snapshot = telemetry.snapshot()

        assertEquals(0, snapshot.frameInterval.sampleCount)
        assertEquals(0, snapshot.dispatchPipeline.sampleCount)
        assertEquals(0, snapshot.canvasDraw.sampleCount)
    }
}
