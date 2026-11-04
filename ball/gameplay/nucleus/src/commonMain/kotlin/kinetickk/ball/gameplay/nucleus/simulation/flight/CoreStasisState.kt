// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.flight

/**
 * Core Freeze (Core Stasis): the Core's filtered speed, the freeze it has gathered, the hold window that keeps the
 * freeze where it is and whether the host takes input. Written only by the functions in `CoreStasisSystem.kt`; read
 * through the accessors there. Every field is a plain scalar and none is a world position, so a deep copy is a field
 * copy and a rebase leaves it alone.
 */
internal class CoreStasisState(source: CoreStasisState?) {
    /** Exponential average of the Core's scalar speed (u/s, the HUD's speed unit). */
    internal var speedFilter: Float = source?.speedFilter ?: 0f
    /** The freeze, 0..1; 1 froze the Core solid. */
    internal var freeze: Float = source?.freeze ?: 0f
    /** Simulation seconds left in the current hold window (run start, a closed choice, a layer switch). */
    internal var holdSeconds: Float = source?.holdSeconds ?: 0f
    /**
     * The host takes no input (a session workflow is landing). A fact of the host, not of the run, so a run start
     * keeps it; the host reports every change.
     */
    internal var inputHeld: Boolean = source?.inputHeld ?: false

    /** A new run: at rest, no freeze, holding for [holdSeconds]. */
    fun reset(holdSeconds: Float) {
        speedFilter = 0f
        freeze = 0f
        this.holdSeconds = holdSeconds
    }
}
