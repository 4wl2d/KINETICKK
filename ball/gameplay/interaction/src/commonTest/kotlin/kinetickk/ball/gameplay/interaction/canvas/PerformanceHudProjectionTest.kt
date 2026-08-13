// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.performance.GameplayPerformanceSnapshot
import kinetickk.ball.gameplay.interaction.performance.PerformanceDurationStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceHudProjectionTest {
    @Test
    fun compactProjectionUsesReadableMobileLabelsWithoutDesktopShortcut() {
        val stats = PerformanceDurationStats(
            sampleCount = 600,
            totalSampleCount = 12_345L,
            meanMillis = 10.0,
            p50Millis = 10.1,
            p95Millis = 20.2,
            p99Millis = 30.3,
            maxMillis = 40.4,
            slowestOnePercentMeanMillis = 50.5,
        )
        val projection = GameplayPerformanceSnapshot(
            frameInterval = stats,
            dispatchPipeline = stats,
            canvasDraw = stats,
            framesPerSecond = 59.9,
            onePercentLowFramesPerSecond = 48.8,
            framesOver16MillisFraction = 0.051,
            framesOver33MillisFraction = 0.012,
            currentEnemies = 120,
            peakEnemies = 650,
            currentProjectiles = 2_400,
            peakProjectiles = 12_300,
            currentPickups = 42,
            peakPickups = 84,
            currentTrailPoints = 1_200,
            peakTrailPoints = 3_400,
        ).toPerformanceHudProjection()

        assertEquals("PERFORMANCE // TAP PERF // ROLLING", COMPACT_PERFORMANCE_TITLE)
        assertEquals(5, projection.compactLines.size)
        assertEquals("FRM P50 10.1 P95 20.2 P99 30.3 MAX 40.4", projection.compactLines[0])
        assertEquals("N 12.3K W 600 | PIPE P50 10.1 P95 20.2", projection.compactLines[1])
        assertEquals("DRAW P50 10.1 P95 20.2 | FPS 59.9 LOW 48.8", projection.compactLines[2])
        assertEquals(">16 5.1% >33 1.2% | E 120/650 PRJ 2.4K/12.3K", projection.compactLines[3])
        assertEquals("PICK 42/84 TRAIL 1.2K/3.4K", projection.compactLines[4])
        assertTrue(projection.compactLines.all { it.length <= MAX_COMPACT_HUD_LINE_CHARACTERS })
        assertFalse((listOf(COMPACT_PERFORMANCE_TITLE) + projection.compactLines).any { "F3" in it })
    }

    @Test
    fun compactDrawContractUsesNoMoreThanSixTextDrawsIncludingTitle() {
        val projection = GameplayPerformanceSnapshot.Empty.toPerformanceHudProjection()

        assertEquals(COMPACT_PERFORMANCE_TEXT_DRAW_BUDGET, 1 + projection.compactLines.size)
    }

    private companion object {
        const val COMPACT_PERFORMANCE_TEXT_DRAW_BUDGET = 6
        const val MAX_COMPACT_HUD_LINE_CHARACTERS = 48
    }
}
