// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * The target upper average frame rate of the continuous frame loops; 0 = no limit. Platforms offer no
 * frame cap of their own, so the loops pace themselves in presentation only: the fixed-step
 * simulation takes its delta from frame times and plays the same at any rate.
 */
val LocalKkFrameRateCap = staticCompositionLocalOf { 0 }

/** Wake this much early: a timer that fires late would otherwise miss the frame it waits for. */
private const val PACING_SLACK_NANOS: Long = 500_000L

/**
 * Milliseconds to wait before requesting the next frame, waking early enough to catch V-Sync.
 * [sinceLastFrameNanos] is elapsed time from the previous scheduled frame, which can be negative
 * after an early wake. [KkFramePacer] carries that remainder so timer rounding cannot raise the cap.
 */
fun kkPacingDelayMillis(capHz: Int, sinceLastFrameNanos: Long): Long =
    if (capHz <= 0) 0L else ((1_000_000_000L / capHz - sinceLastFrameNanos - PACING_SLACK_NANOS) / 1_000_000L).coerceAtLeast(0L)

/**
 * Keeps a monotonic schedule instead of starting each interval at an early timer wake. Whole
 * millisecond timers can wake up to 1.5 ms early; carrying that remainder preserves the average cap
 * without rounding every wait up and missing matching V-Sync refreshes. A missed whole interval
 * rebases the schedule so a stall never creates a catch-up backlog. No spinning or blocking;
 * without a cap this is a plain frame await, and returned timestamps always come from Compose.
 */
class KkFramePacer internal constructor(
    private val capHz: Int,
    private val nowNanos: () -> Long,
    private val waitMillis: suspend (Long) -> Unit,
) {
    constructor(capHz: Int) : this(capHz, monotonicNanoClock(), { delay(it) })

    private val intervalNanos = if (capHz > 0) 1_000_000_000L / capHz else 0L
    private var scheduledFrameNanos = 0L
    private var started = false

    suspend fun awaitFrame(): Long {
        if (capHz <= 0) return withFrameNanos { it }
        if (started) {
            val wait = kkPacingDelayMillis(capHz, nowNanos() - scheduledFrameNanos)
            if (wait > 0L) waitMillis(wait)
        }
        val frame = withFrameNanos { it }
        val now = nowNanos()
        val nextScheduledFrame = scheduledFrameNanos + intervalNanos
        scheduledFrameNanos = if (!started || now - nextScheduledFrame >= intervalNanos) now else nextScheduledFrame
        started = true
        return frame
    }
}

private fun monotonicNanoClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeNanoseconds }
}
