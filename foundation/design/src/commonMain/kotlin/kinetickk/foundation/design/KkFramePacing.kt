// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * The most frames per second the continuous frame loops draw; 0 = no limit. Platforms offer no
 * frame cap of their own, so the loops pace themselves in presentation only: the fixed-step
 * simulation takes its delta from frame times and plays the same at any rate.
 */
val LocalKkFrameRateCap = staticCompositionLocalOf { 0 }

/** Wake this much early: a timer that fires late would otherwise miss the frame it waits for. */
private const val PACING_SLACK_NANOS: Long = 500_000L

/**
 * Milliseconds to wait before awaiting the next frame so frames start at least 1/[capHz] s apart,
 * [sinceLastFrameNanos] after the previous one. 0 when there is no cap or the interval has passed.
 */
fun kkPacingDelayMillis(capHz: Int, sinceLastFrameNanos: Long): Long =
    if (capHz <= 0) 0L else ((1_000_000_000L / capHz - sinceLastFrameNanos - PACING_SLACK_NANOS) / 1_000_000L).coerceAtLeast(0L)

/**
 * One continuous frame loop's pacing: [awaitFrame] waits out the rest of the cap's interval, then
 * the next frame. The previous frame is a monotonic value mark, so pacing allocates nothing per
 * frame beyond the platform timer of an actual wait; without a cap it is a plain frame await.
 */
class KkFramePacer(private val capHz: Int) {
    private var lastFrame = TimeSource.Monotonic.markNow()
    private var started = false

    suspend fun awaitFrame(): Long {
        if (started && capHz > 0) {
            val wait = kkPacingDelayMillis(capHz, lastFrame.elapsedNow().inWholeNanoseconds)
            if (wait > 0L) delay(wait)
        }
        val frame = withFrameNanos { it }
        lastFrame = TimeSource.Monotonic.markNow()
        started = true
        return frame
    }
}
