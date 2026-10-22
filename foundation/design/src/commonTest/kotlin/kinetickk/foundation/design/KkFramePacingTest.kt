// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import kotlin.test.Test
import kotlin.test.assertEquals

class KkFramePacingTest {
    @Test
    fun noCapNeverWaits() {
        listOf(0L, 1L, 16_000_000L, 1_000_000_000L).forEach { since ->
            assertEquals(0L, kkPacingDelayMillis(0, since))
            assertEquals(0L, kkPacingDelayMillis(-60, since))
        }
    }

    @Test
    fun aCapWaitsOutTheRestOfItsIntervalHalfAMillisecondEarlyInWholeMilliseconds() {
        // 30 Hz: 33.33 ms per frame.
        assertEquals(32L, kkPacingDelayMillis(30, 0L))
        assertEquals(22L, kkPacingDelayMillis(30, 10_000_000L))
        // 60 Hz: 16.67 ms per frame; a frame that took 16 ms waits nothing.
        assertEquals(16L, kkPacingDelayMillis(60, 0L))
        assertEquals(6L, kkPacingDelayMillis(60, 10_000_000L))
        assertEquals(0L, kkPacingDelayMillis(60, 16_000_000L))
        // 144 Hz: 6.94 ms; 240 Hz: 4.17 ms.
        assertEquals(6L, kkPacingDelayMillis(144, 0L))
        assertEquals(1L, kkPacingDelayMillis(144, 5_000_000L))
        assertEquals(3L, kkPacingDelayMillis(240, 0L))
        assertEquals(0L, kkPacingDelayMillis(240, 4_000_000L))
        // A frame later than the interval never yields a negative wait.
        assertEquals(0L, kkPacingDelayMillis(30, 1_000_000_000L))
    }
}
