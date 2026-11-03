// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.flight

/**
 * How still the Core has been (Core Stasis): its leash anchor, its speed average and the stillness, full-stasis and
 * fracture clocks. Written only by [updateCoreStasis]; read through the accessors next to it. Every field is a plain
 * scalar, so a deep copy is a field copy.
 */
internal class CoreStasisState(source: CoreStasisState?) {
    /** The leash point (world units): dragged to the anchor radius behind a Core that leaves it. */
    internal var anchorX: Float = source?.anchorX ?: 0f
    internal var anchorY: Float = source?.anchorY ?: 0f
    /** Exponential moving average of the Core's scalar speed (u/s). */
    internal var speedEma: Float = source?.speedEma ?: 0f
    /** The still clock (s), 0..[kinetickk.ball.content.api.CoreStasisTuning.fullStasisSeconds]. */
    internal var stillSeconds: Float = source?.stillSeconds ?: 0f
    /** Time held at full stasis (s), 0..the fracture delay; 0 whenever the Core is below full stasis or in Easy mode. */
    internal var frozenSeconds: Float = source?.frozenSeconds ?: 0f
    /** Time since the last fracture tick (s), 0..the tick period; 0 while not fracturing. */
    internal var fractureClock: Float = source?.fractureClock ?: 0f

    /** A new run: the anchor sits on the Core at ([coreX], [coreY]) and every clock is 0. */
    fun reset(coreX: Float, coreY: Float) {
        anchorX = coreX
        anchorY = coreY
        speedEma = 0f
        stillSeconds = 0f
        frozenSeconds = 0f
        fractureClock = 0f
    }

    /** The world origin moved by ([shiftX], [shiftY]); the anchor moves with it. */
    fun rebase(shiftX: Float, shiftY: Float) {
        anchorX -= shiftX
        anchorY -= shiftY
    }
}
