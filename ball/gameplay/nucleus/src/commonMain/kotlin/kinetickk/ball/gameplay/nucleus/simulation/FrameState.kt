// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/**
 * Frame admission and display-only state: the viewport scale, the step accumulator, the camera at the last step start,
 * the interpolation flag, and the impact, flash, shake and message feedback. Written by the frame loop and the run's
 * feedback; the simulation never decides from it.
 */
internal class FrameState(source: FrameState?) {
    internal var uiScale: Float = source?.uiScale ?: 1f
    /** Device px per dp of the accepted viewport; the simulation itself runs in dp. */
    internal var viewportDensity: Float = source?.viewportDensity ?: 1f
    internal var accumulator: Float = source?.accumulator ?: 0f
    internal var lastTransitionSteps: Int = source?.lastTransitionSteps ?: 0
    /** Camera at the start of the last fixed step; renderers interpolate toward [cameraX]. */
    internal var previousCameraX: Float = source?.previousCameraX ?: 0f
    internal var previousCameraY: Float = source?.previousCameraY ?: 0f
    /**
     * Whether a fixed step has run since the simulation last entered RUNNING, so every previous
     * position is that step's start. Until then renderers present the exact state (render only).
     */
    internal var interpolationPrimed: Boolean = source?.interpolationPrimed ?: false
    internal var lastImpact: Float = source?.lastImpact ?: 0f
    internal var lastImpactTime: Float = source?.lastImpactTime ?: 0f
    internal var damageFlash: Float = source?.damageFlash ?: 0f
    internal var screenShake: Float = source?.screenShake ?: 0f
    internal var message: String = source?.message ?: ""
    internal var messageTime: Float = source?.messageTime ?: 0f

    /** A new run: nothing admitted or primed, the camera's step start at the origin, no feedback and the opening banner. */
    fun reset() {
        interpolationPrimed = false
        accumulator = 0f
        previousCameraX = 0f
        previousCameraY = 0f
        lastImpact = 0f
        lastImpactTime = 0f
        damageFlash = 0f
        screenShake = 0f
        message = "DRIFT PHASE"
        messageTime = 2.2f
    }
}
