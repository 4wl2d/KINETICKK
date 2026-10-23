// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.performance

/**
 * The Show FPS counter: frames per second over [WINDOW_NANOS] windows of the gameplay frame loop.
 * [frame] counts one presented frame and closes a window once it has lasted [WINDOW_NANOS]; [fps]
 * changes only when a closed window measured a different whole value, so the HUD reads a value that
 * moves at most twice a second. A gap of more than [STALL_NANOS] between two frames is a stall (a
 * hidden tab, the app in the background), not a frame rate: a new window starts after it. Hiding the
 * counter ([hide]) drops its open window. Interaction-ephemeral and frame-loop confined: three
 * counters, no allocation, never fed to gameplay.
 */
internal class GameplayFpsCounter {
    private var frames = 0
    private var windowStartNanos = NO_WINDOW
    private var lastFrameNanos = NO_WINDOW

    /** The last measured value; [NO_VALUE] until the first window closes. */
    var fps: Int = NO_VALUE
        private set

    /** Counts the frame presented at [frameNanos]; true when it closed a window with a new [fps]. */
    fun frame(frameNanos: Long): Boolean {
        val previous = lastFrameNanos
        lastFrameNanos = frameNanos
        // The first frame, a clock that went backwards (a new loop) or a stall opens a fresh window.
        if (windowStartNanos == NO_WINDOW || frameNanos < previous || frameNanos - previous > STALL_NANOS) {
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

    /**
     * The counter is hidden on this frame: it starts over, so the next window measures from the next
     * frame shown. True when that cleared a measured [fps].
     */
    fun hide(): Boolean {
        val cleared = fps != NO_VALUE
        windowStartNanos = NO_WINDOW
        lastFrameNanos = NO_WINDOW
        frames = 0
        fps = NO_VALUE
        return cleared
    }

    companion object {
        const val WINDOW_NANOS: Long = 500_000_000L

        /** The longest frame interval still measured as a frame rate (1 FPS). */
        const val STALL_NANOS: Long = 2 * WINDOW_NANOS
        const val NO_VALUE: Int = -1
        private const val NO_WINDOW: Long = Long.MIN_VALUE
        private const val NANOS_PER_SECOND: Long = 1_000_000_000L
    }
}
