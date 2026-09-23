// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.containsInclusive
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel

/** A pointer result is either a live-run action or a request for the Session-owned host. */
sealed interface GameplayInput {
    data class Action(val action: GameplayInteractionPulse) : GameplayInput
    data object OpenSettings : GameplayInput
    data object OpenRebirth : GameplayInput
    data object ExitToHome : GameplayInput
    data object RestartRun : GameplayInput
    data object TogglePerformance : GameplayInput
}

/** Interaction-ephemeral values required for deterministic canvas hit testing. */
internal data class GameplayHitTestState(
    val phase: GamePhase,
    val screenWidth: Float,
    val screenHeight: Float,
    val uiScale: Float,
    val choiceCount: Int,
    val choicesCanReroll: Boolean,
)

/** Maps gameplay canvas coordinates without encoding any app destination in domain state. */
fun GameplayRenderModel.resolveGameplayPress(x: Float, y: Float): GameplayInput? =
    resolveGameplayPress(
        phase = phase,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        x = x,
        y = y,
    )

fun GameplayRenderModel.isHudControlPosition(x: Float, y: Float): Boolean =
    isHudControlPosition(
        phase = phase,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        x = x,
        y = y,
    )

internal fun GameplayHitTestState.resolveGameplayPress(x: Float, y: Float): GameplayInput? =
    resolveGameplayPress(
        phase = phase,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        x = x,
        y = y,
    )

internal fun GameplayHitTestState.isHudControlPosition(x: Float, y: Float): Boolean =
    isHudControlPosition(
        phase = phase,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        x = x,
        y = y,
    )

private fun resolveGameplayPress(
    phase: GamePhase,
    screenWidth: Float,
    screenHeight: Float,
    uiScale: Float,
    x: Float,
    y: Float,
): GameplayInput? = when (phase) {
    GamePhase.RUNNING -> resolveHudPress(screenWidth, screenHeight, uiScale, x, y)
    GamePhase.PAUSED -> resolvePausePress(screenWidth, screenHeight, uiScale, x, y)
    GamePhase.CHOICE -> null // Compose activates on click, after scroll gestures resolve.
    GamePhase.GAME_OVER, GamePhase.VICTORY -> null // TerminalContent owns click and scroll handling.
}

private fun isHudControlPosition(
    phase: GamePhase,
    screenWidth: Float,
    screenHeight: Float,
    uiScale: Float,
    x: Float,
    y: Float,
): Boolean = phase == GamePhase.RUNNING &&
    runningControlTargetAt(screenWidth, screenHeight, uiScale, x, y) != null

private fun resolvePausePress(
    screenWidth: Float,
    screenHeight: Float,
    uiScale: Float,
    x: Float,
    y: Float,
): GameplayInput? {
    val target = pauseLayoutGeometry(screenWidth, screenHeight, uiScale)
        .actions
        .firstOrNull { containsInclusive(it.bounds, x, y) }
        ?.target
        ?: return null
    return when (target) {
        PauseTarget.RESUME -> GameplayInput.Action(GameplayInteractionPulse.PauseToggled)
        PauseTarget.SETTINGS -> GameplayInput.OpenSettings
        PauseTarget.PERFORMANCE -> GameplayInput.TogglePerformance
        PauseTarget.EXIT -> GameplayInput.ExitToHome
    }
}

private fun resolveHudPress(
    screenWidth: Float,
    screenHeight: Float,
    uiScale: Float,
    x: Float,
    y: Float,
): GameplayInput? = when (
    runningControlTargetAt(screenWidth, screenHeight, uiScale, x, y)
) {
    RunningControlTarget.DASH -> GameplayInput.Action(GameplayInteractionPulse.DashRequested)
    RunningControlTarget.BRAKE -> GameplayInput.Action(
        GameplayInteractionPulse.BrakeChanged(BrakeSource.TOUCH_CONTROL, active = true),
    )
    RunningControlTarget.PERFORMANCE -> GameplayInput.TogglePerformance
    RunningControlTarget.PAUSE -> GameplayInput.Action(GameplayInteractionPulse.PauseToggled)
    null -> null
}

private fun runningControlTargetAt(
    screenWidth: Float,
    screenHeight: Float,
    uiScale: Float,
    x: Float,
    y: Float,
): RunningControlTarget? {
    var matched: RunningControlTarget? = null
    forEachRunningControlBounds(
        screenWidth,
        screenHeight,
        uiScale,
    ) { target, left, top, right, bottom ->
        if (matched != null) return@forEachRunningControlBounds
        val hit = x in left..right && y in top..bottom
        if (hit) matched = target
    }
    return matched
}
