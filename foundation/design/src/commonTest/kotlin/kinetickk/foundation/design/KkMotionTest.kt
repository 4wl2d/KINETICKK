// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KkMotionTest {
    @Test
    fun shakeReachFallsOffWithTheSquareOfTrauma() {
        for (trauma in listOf(0.25f, 0.5f, 1f)) {
            val reach = 10f * trauma * trauma
            var peak = 0f
            for (frame in 0 until 600) {
                val offset = kkShake(trauma, frame / 60f, 10f)
                peak = maxOf(peak, abs(offset.x), abs(offset.y))
            }
            assertTrue(peak <= reach + 1e-4f && peak > reach * 0.8f, "trauma $trauma: peak $peak of reach $reach")
        }
        assertEquals(Offset.Zero, kkShake(0f, 1f, 10f))
        assertEquals(Offset.Zero, kkShake(-1f, 1f, 10f))
        assertEquals(kkShake(1f, 0.7f, 10f), kkShake(3f, 0.7f, 10f), "trauma above 1 is full trauma")
    }

    @Test
    fun shakeSwaysSmoothlyInsteadOfRattlingEveryFrame() {
        // At 60 fps a full shake moves at most about half its reach between frames, so the eye
        // follows a sway rather than a per-frame jump across the whole range.
        var previous = kkShake(1f, 0f, 1f)
        for (frame in 1..600) {
            val offset = kkShake(1f, frame / 60f, 1f)
            val step = maxOf(abs(offset.x - previous.x), abs(offset.y - previous.y))
            assertTrue(step < 0.55f, "frame $frame jumps $step of the reach")
            previous = offset
        }
    }
}
