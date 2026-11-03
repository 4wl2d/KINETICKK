// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.canvas.hud.GameplayFpsCounter
import kinetickk.ball.gameplay.interaction.canvas.hud.RunningControlPressLog
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.interaction.input.ValidationFailure
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkFramePacer
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.foundation.diagnostics.DIAGNOSTICS_ENABLED
import kinetickk.foundation.diagnostics.PerformanceDiagnostics
import kinetickk.foundation.diagnostics.diagnosticFrameBudgetNanos

internal const val MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS: Float = 0.1f

internal fun selectGameplayPresentationDelta(realDeltaSeconds: Float): Float =
    realDeltaSeconds.coerceAtMost(MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS)

/**
 * The gameplay host's frame loop; it runs until its effect is cancelled. [frameRateCap] paces it (the
 * fixed-step simulation plays the same at any frame rate). Every frame feeds the Show FPS counter
 * ([renderModel]'s setting) and the performance diagnostics, advances [renderTimeSeconds] by the capped
 * presentation delta and dispatches the validated FrameElapsed; a recent crash context is kept at most
 * once a second. A plain function, not a composable: the host's recomposition scopes stay as they are.
 */
internal suspend fun runGameplayFrameLoop(
    component: GameplayInteractionPort,
    validator: GameInteractionValidator,
    frameRateCap: Int,
    diagnostics: CrashDiagnostics,
    performanceDiagnostics: PerformanceDiagnostics?,
    renderModel: State<GameplayRenderModel>,
    renderTimeSeconds: MutableFloatState,
    fpsCounter: GameplayFpsCounter,
    fpsReadout: MutableIntState,
    controlLog: RunningControlPressLog,
    dispatch: (GameplayInteractionPulse) -> Unit,
    reportInvalid: (ValidationFailure) -> Unit,
) {
    val pacer = KkFramePacer(frameRateCap)
    var previousFrame = pacer.awaitFrame()
    var previousContextFrame = 0L
    while (true) {
        val frame = pacer.awaitFrame()
        val frameIntervalNanos = frame - previousFrame
        val delta = frameIntervalNanos / 1_000_000_000f
        previousFrame = frame
        val fpsChanged = if (renderModel.value.settings.showFps) fpsCounter.frame(frame) else fpsCounter.hide()
        if (fpsChanged) {
            controlLog.fps = fpsCounter.fps
            fpsReadout.intValue = fpsCounter.fps
        }
        if (DIAGNOSTICS_ENABLED && performanceDiagnostics?.capturesPerformance == true) {
            performanceDiagnostics.frame(frame, diagnosticFrameBudgetNanos(frameRateCap, performanceDiagnostics.nominalFrameBudgetNanos))
        }
        when (val result = validator.frameElapsed(delta)) {
            is InteractionValidationResult.Valid -> {
                renderTimeSeconds.floatValue += selectGameplayPresentationDelta(
                    result.intent.realDeltaSeconds,
                )
                dispatch(result.intent)
            }
            is InteractionValidationResult.Invalid -> reportInvalid(result.failure)
        }
        // Preserve a recent immutable crash context without per-frame closures or formatting.
        if (DIAGNOSTICS_ENABLED && diagnostics.enabled &&
            (previousContextFrame == 0L || frame - previousContextFrame >= 1_000_000_000L)
        ) {
            previousContextFrame = frame
            val committed = component.renderSnapshot()
            diagnostics.context("gameplay.committed") { committed.crashContext() }
            diagnostics.context("gameplay.frame") { "FrameElapsed(realDeltaSeconds=$delta) callback_ns=$frame" }
        }
    }
}
