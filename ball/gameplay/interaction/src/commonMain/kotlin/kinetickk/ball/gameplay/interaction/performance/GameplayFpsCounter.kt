// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.performance

/**
 * The Show FPS counter: frames per second over [WINDOW_NANOS] windows of the gameplay frame loop.
 * [frame] counts one presented frame and closes a window once it has lasted [WINDOW_NANOS]; [fps]
 * changes only when a closed window measured a different whole value, so the HUD reads a value that
 * moves at most twice a second. Interaction-ephemeral and frame-loop confined: two counters, no
 * allocation, never fed to gameplay.
 */
internal class GameplayFpsCounter {
    private var frames = 0
    private var windowStartNanos = NO_WINDOW

    /** The last measured value; [NO_VALUE] until the first window closes. */
    var fps: Int = NO_VALUE
        private set

    /** Counts the frame presented at [frameNanos]; true when it closed a window with a new [fps]. */
    fun frame(frameNanos: Long): Boolean {
        if (windowStartNanos == NO_WINDOW || frameNanos < windowStartNanos) {
            windowStartNanos = frameNanos
            frames = 0
            return false
        }
        frames++
        val elapsed = frameNanos - windowStartNanos
        if (elapsed < WINDOW_NANOS) return false
        val value = ((frames * NANOS_PER_SECOND + elapsed / 2L) / elapsed).toInt()
        windowStartNanos = frameNanos
        frames = 0
        if (value == fps) return false
        fps = value
        return true
    }

    /** Starts over (the counter was hidden): the next window measures from the next frame. */
    fun reset() {
        windowStartNanos = NO_WINDOW
        frames = 0
        fps = NO_VALUE
    }

    companion object {
        const val WINDOW_NANOS: Long = 500_000_000L
        const val NO_VALUE: Int = -1
        private const val NO_WINDOW: Long = Long.MIN_VALUE
        private const val NANOS_PER_SECOND: Long = 1_000_000_000L
    }
}
