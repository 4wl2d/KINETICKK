// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus.protocol

import kinetickk.features.game.nucleus.ParticleDensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoundedVisualFxCueAccumulatorTest {
    @Test
    fun redundantCosmeticOverflowIsBoundedAndReportedWithoutLosingSynchronization() {
        val accumulator = BoundedVisualFxCueAccumulator()
        repeat(VisualFxCueLimits.MAX_CUES_PER_PROJECTION + 500) { index ->
            accumulator.record(
                VisualFxCue.Burst(
                    x = index.toFloat(),
                    y = 0f,
                    requestedCount = 1,
                    colorIndex = 1,
                    density = ParticleDensity.NORMAL,
                ),
            )
        }
        val requiredAdvance = VisualFxCue.EffectsAdvanced(1f / 120f)
        accumulator.record(requiredAdvance)

        val batch = accumulator.drain()

        assertEquals(VisualFxCueLimits.MAX_CUES_PER_PROJECTION, batch.size)
        assertTrue(requiredAdvance in batch)
        val dropped = assertIs<VisualFxCue.VisualCuesDropped>(batch.last())
        assertEquals(502, dropped.count)
    }

    @Test
    fun drainResetsTheAccumulatorForTheNextAcceptedDecision() {
        val accumulator = BoundedVisualFxCueAccumulator()
        accumulator.record(
            VisualFxCue.ShockwaveAdded(0f, 0f, 1f, 10f, 2),
        )
        assertEquals(1, accumulator.drain().size)
        assertTrue(accumulator.drain().isEmpty())
    }
}
