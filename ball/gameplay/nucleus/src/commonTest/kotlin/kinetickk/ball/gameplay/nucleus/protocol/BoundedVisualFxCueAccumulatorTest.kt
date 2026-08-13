// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.protocol

import kinetickk.ball.profile.api.ParticleDensity
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.Test

class BoundedVisualFxCueAccumulatorTest {
    @Test
    fun reservedCueCapacityAcceptsTwoThousandFortySevenWithoutDropMetadata() {
        val accumulator = BoundedVisualFxCueAccumulator()
        repeat(VisualFxCueLimits.MAX_CUES_PER_PROJECTION - 1) { index ->
            accumulator.record(burst(index))
        }

        val batch = accumulator.drain()

        assertEquals(VisualFxCueLimits.MAX_CUES_PER_PROJECTION - 1, batch.size)
        assertTrue(batch.none { it is VisualFxCue.VisualCuesDropped })
        assertEquals(
            (VisualFxCueLimits.MAX_CUES_PER_PROJECTION - 2).toFloat(),
            assertIs<VisualFxCue.Burst>(batch.last()).x,
        )
    }

    @Test
    fun firstCueBeyondReservedCapacityIsDroppedAndReportedInTwoThousandFortyEightBound() {
        val accumulator = BoundedVisualFxCueAccumulator()
        repeat(VisualFxCueLimits.MAX_CUES_PER_PROJECTION) { index ->
            accumulator.record(burst(index))
        }

        val batch = accumulator.drain()

        assertEquals(VisualFxCueLimits.MAX_CUES_PER_PROJECTION, batch.size)
        assertEquals(
            (VisualFxCueLimits.MAX_CUES_PER_PROJECTION - 2).toFloat(),
            assertIs<VisualFxCue.Burst>(batch[batch.lastIndex - 1]).x,
        )
        assertEquals(1, assertIs<VisualFxCue.VisualCuesDropped>(batch.last()).count)
    }

    @Test
    fun outputCapTwoThousandFortyEightReportsAttemptedTwoThousandFortyNinthWithoutGrowth() {
        val accumulator = BoundedVisualFxCueAccumulator()
        repeat(VisualFxCueLimits.MAX_CUES_PER_PROJECTION) { index ->
            accumulator.record(burst(index))
        }
        val requiredAdvance = VisualFxCue.EffectsAdvanced(1f / 120f)
        accumulator.record(requiredAdvance)

        val batch = accumulator.drain()

        assertEquals(VisualFxCueLimits.MAX_CUES_PER_PROJECTION, batch.size)
        assertTrue(requiredAdvance in batch)
        assertEquals(1f, assertIs<VisualFxCue.Burst>(batch.first()).x)
        val dropped = assertIs<VisualFxCue.VisualCuesDropped>(batch.last())
        assertEquals(2, dropped.count)
    }

    @Test
    fun drainResetsTheAccumulatorForTheNextReduction() {
        val accumulator = BoundedVisualFxCueAccumulator()
        accumulator.record(
            VisualFxCue.ShockwaveAdded(0f, 0f, 1f, 10f, 2),
        )
        assertEquals(1, accumulator.drain().size)
        assertTrue(accumulator.drain().isEmpty())
    }

    @Test
    fun drainingAndLaterRecordingOneCopyCannotMutateSourceOrSibling() {
        val source = BoundedVisualFxCueAccumulator().apply {
            record(burst(1))
            record(VisualFxCue.EffectsAdvanced(0.125f))
        }
        val drainedCopy = source.copy()
        val retainedSibling = source.copy()

        assertEquals(
            listOf(burst(1), VisualFxCue.EffectsAdvanced(0.125f)),
            drainedCopy.drain(),
        )
        drainedCopy.record(VisualFxCue.ClearAll)
        source.record(burst(2))

        assertEquals(
            listOf(burst(1), VisualFxCue.EffectsAdvanced(0.125f)),
            retainedSibling.drain(),
        )
        assertEquals(listOf(VisualFxCue.ClearAll), drainedCopy.drain().toList())
        assertEquals(
            listOf(burst(1), VisualFxCue.EffectsAdvanced(0.125f), burst(2)),
            source.drain(),
        )
    }

    private fun burst(index: Int) = VisualFxCue.Burst(
        x = index.toFloat(),
        y = 0f,
        requestedCount = 1,
        colorIndex = 1,
        density = ParticleDensity.NORMAL,
    )
}
