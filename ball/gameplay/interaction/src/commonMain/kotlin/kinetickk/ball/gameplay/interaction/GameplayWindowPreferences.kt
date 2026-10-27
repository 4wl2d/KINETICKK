// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.nucleus.render.GamePhase

/**
 * Pause when unfocused: only the edge from a focused to an unfocused window pauses, and only a
 * running run whose player keeps the preference on. A window that never had focus (a headless test,
 * a window opened again for a display change) never pauses by itself.
 */
internal fun pausesOnFocusLoss(
    wasFocused: Boolean,
    focused: Boolean,
    phase: GamePhase,
    pauseOnFocusLoss: Boolean,
    inputEnabled: Boolean,
): Boolean = wasFocused && !focused && pauseOnFocusLoss && inputEnabled && phase == GamePhase.RUNNING

/** The window focus the run saw last (Interaction-ephemeral). */
internal class GameplayWindowFocus {
    var focused: Boolean = false
}
