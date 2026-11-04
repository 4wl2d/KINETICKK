// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.GameAction
import kinetickk.foundation.design.toBindableKey
import kinetickk.foundation.diagnostics.DIAGNOSTICS_ENABLED

/**
 * What one key event does in the gameplay host, decided by [gameplayKeyInput] without side effects.
 * The host applies it in this order: [keyboardPlay] (key caps show, touch input ends), [gesture]
 * (a user gesture is observed), then the running [control] press, the [choice] (validated) or the
 * [input]; it returns [consumed], and an event it does not consume bubbles to the session shortcuts.
 */
internal data class GameplayKeyInput(
    val consumed: Boolean,
    val gesture: Boolean = false,
    val keyboardPlay: Boolean = false,
    val input: GameplayInput? = null,
    val control: RunningControlTarget? = null,
    val choice: Int = NO_CHOICE,
) {
    companion object {
        const val NO_CHOICE = -1
        val IGNORED = GameplayKeyInput(consumed = false)
        val CONSUMED = GameplayKeyInput(consumed = true)
    }
}

/**
 * Maps a key event of [key] and [type] over the committed [model]. Bound keys come from the
 * player's key bindings; Escape and Enter stay fixed aliases. Nothing reaches gameplay while the
 * host is not [enabled]; a [terminalLocked] report swallows every key until its actions are ready.
 * A bound key is consumed only in the phases where its action applies; otherwise it bubbles to the
 * session shortcuts (C opens the Codex while paused).
 */
internal fun gameplayKeyInput(
    key: Key,
    type: KeyEventType,
    model: GameplayRenderModel,
    enabled: Boolean,
    terminalLocked: Boolean,
): GameplayKeyInput {
    val bindable = key.toBindableKey()
    val action = bindable?.let { model.settings.keyBindings.action(it) }
    val down = type == KeyEventType.KeyDown
    if (DIAGNOSTICS_ENABLED && action == GameAction.PERFORMANCE_HUD) {
        return GameplayKeyInput(consumed = true, input = if (down) GameplayInput.TogglePerformance else null)
    }
    if (!enabled) return GameplayKeyInput.IGNORED
    if (terminalLocked) return GameplayKeyInput.CONSUMED
    // Keyboard play is a key a player could bind or a fixed alias; a device key (a phone's volume
    // rocker) shows no key caps and leaves a touch player's cards naming slots by number.
    val keyboardPlay = down && (bindable != null || key == Key.Escape || key == Key.Enter)

    /** Consumed when [applies]; acts on the key going down only. */
    fun onDown(
        applies: Boolean,
        input: GameplayInput? = null,
        control: RunningControlTarget? = null,
        choice: Int = GameplayKeyInput.NO_CHOICE,
    ): GameplayKeyInput {
        val acts = applies && down
        return GameplayKeyInput(
            consumed = applies,
            gesture = down,
            keyboardPlay = keyboardPlay,
            input = if (acts) input else null,
            control = if (acts) control else null,
            choice = if (acts) choice else GameplayKeyInput.NO_CHOICE,
        )
    }

    fun result(consumed: Boolean, input: GameplayInput? = null): GameplayKeyInput =
        GameplayKeyInput(consumed = consumed, gesture = down, keyboardPlay = keyboardPlay, input = input)

    val phase = model.phase
    val running = phase == GamePhase.RUNNING
    val choosing = phase == GamePhase.CHOICE
    return when (action) {
        GameAction.DASH -> onDown(running, control = RunningControlTarget.DASH)
        GameAction.SKILL_1 -> onDown(running, control = RunningControlTarget.SKILL_1)
        GameAction.SKILL_2 -> onDown(running, control = RunningControlTarget.SKILL_2)
        GameAction.SKILL_3 -> onDown(running, control = RunningControlTarget.SKILL_3)
        GameAction.APEX -> onDown(running, control = if (model.apex.unlocked) RunningControlTarget.APEX else null)
        GameAction.BRAKE -> when {
            // A release ends the brake in every phase (the key may lift after a pause).
            type == KeyEventType.KeyUp -> result(true, keyboardBrake(active = false))
            down && running -> result(true, keyboardBrake(active = true))
            else -> result(false)
        }
        GameAction.PAUSE -> onDown(running || phase == GamePhase.PAUSED, GameplayInput.Action(GameplayInteractionPulse.PauseToggled))
        GameAction.REROLL -> onDown(choosing, GameplayInput.Action(GameplayInteractionPulse.ChoicesRerolled))
        GameAction.CHOICE_1 -> onDown(choosing, choice = 0)
        GameAction.CHOICE_2 -> onDown(choosing, choice = 1)
        GameAction.CHOICE_3 -> onDown(choosing, choice = 2)
        GameAction.CHOICE_4 -> onDown(choosing, choice = 3)
        GameAction.RESTART -> onDown(phase == GamePhase.GAME_OVER || phase == GamePhase.VICTORY, GameplayInput.RestartRun)
        GameAction.PERFORMANCE_HUD -> result(DIAGNOSTICS_ENABLED) // handled above in Debug
        null -> when (key) {
            Key.Escape -> onDown(true, GameplayInput.Action(GameplayInteractionPulse.PauseToggled))
            Key.Enter -> onDown(
                true,
                when (phase) {
                    GamePhase.PAUSED -> GameplayInput.Action(GameplayInteractionPulse.PauseToggled)
                    GamePhase.GAME_OVER,
                    GamePhase.VICTORY,
                    -> GameplayInput.RestartRun
                    GamePhase.RUNNING,
                    GamePhase.CHOICE,
                    -> null
                },
            )
            else -> result(false)
        }
    }
}

private fun keyboardBrake(active: Boolean): GameplayInput =
    GameplayInput.Action(GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, active = active))
