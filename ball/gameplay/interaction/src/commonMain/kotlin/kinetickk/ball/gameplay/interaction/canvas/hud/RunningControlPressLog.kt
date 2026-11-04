// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget

/**
 * Presentation-only record of the running controls, written by the key handler and the touch
 * tracker in render time: when each control was last pressed (its 90 ms face flash), when an Apex
 * press found it cooling (the badge's refusal shake), whether key caps show ([keyHints]) and whether the input is
 * touch ([touchInput]; reward cards then name a Z/X/C slot by number). A touch sets touch input and hides the key caps;
 * a keyboard key that reaches gameplay (one a player could bind, or the Escape/Enter aliases) does the opposite; a
 * device key such as volume and a mouse change neither. It never feeds gameplay. One instance per gameplay host; no
 * allocation.
 */
internal class RunningControlPressLog {
    private val pressedAt = FloatArray(RunningControlTarget.entries.size) { Float.NEGATIVE_INFINITY }

    var apexRefusedAt: Float = Float.NEGATIVE_INFINITY
        private set

    var keyHints: Boolean = false

    var touchInput: Boolean = false

    /** Lightweight frame-loop counter's latest value; [GameplayFpsCounter.NO_VALUE] until its first half-second window. */
    var fps: Int = GameplayFpsCounter.NO_VALUE

    fun press(target: RunningControlTarget, renderTime: Float) {
        pressedAt[target.ordinal] = renderTime
    }

    fun refuseApex(renderTime: Float) {
        apexRefusedAt = renderTime
    }

    /** 1 while [target]'s press flash shows ([PRESS_FLASH_SECONDS] after the press), else 0. */
    fun pressFlash(target: RunningControlTarget, renderTime: Float): Float {
        val age = renderTime - pressedAt[target.ordinal]
        return if (age >= 0f && age < PRESS_FLASH_SECONDS) 1f else 0f
    }

    companion object {
        const val PRESS_FLASH_SECONDS = 0.09f
    }
}
