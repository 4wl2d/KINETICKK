// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.api.GamePhase
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class GameplayInputMappingTest {
    @Test
    fun runningHudMapsOnlyToLiveRunActions() {
        val model = hitTestState(GamePhase.RUNNING)

        val dash = assertIs<GameplayInput.Action>(model.resolveGameplayPress(1_198f, 632f))
        val brake = assertIs<GameplayInput.Action>(model.resolveGameplayPress(1_090f, 653f))

        assertSame(GameplayInteractionPulse.DashRequested, dash.action)
        assertIs<GameplayInteractionPulse.BrakeChanged>(brake.action)
    }

    @Test
    fun pausedButtonsReturnShellRequestsInsteadOfNavigationActions() {
        val paused = hitTestState(GamePhase.PAUSED)

        val resume = assertIs<GameplayInput.Action>(paused.resolveGameplayPress(640f, 386f))
        assertSame(GameplayInteractionPulse.PauseToggled, resume.action)
        assertSame(GameplayInput.OpenSettings, paused.resolveGameplayPress(640f, 472f))
        assertSame(GameplayInput.ExitToHome, paused.resolveGameplayPress(640f, 558f))
    }

    @Test
    fun choiceCardsAndRerollRemainTypedGameplayActions() {
        val choice = hitTestState(
            phase = GamePhase.CHOICE,
            choiceCount = 3,
            choicesCanReroll = true,
        )

        val selected = assertIs<GameplayInput.Action>(choice.resolveGameplayPress(800f, 260f))
        val rerolled = assertIs<GameplayInput.Action>(choice.resolveGameplayPress(640f, 648f))

        assertEquals(GameplayInteractionPulse.ChoiceSelected(index = 2), selected.action)
        assertSame(GameplayInteractionPulse.ChoicesRerolled, rerolled.action)
    }

    @Test
    fun terminalButtonsEmitOnlySessionOwnedShellRequests() {
        val gameOver = hitTestState(GamePhase.GAME_OVER)
        val victory = hitTestState(GamePhase.VICTORY)

        assertSame(GameplayInput.RestartRun, gameOver.resolveGameplayPress(640f, 518f))
        assertSame(GameplayInput.ExitToHome, gameOver.resolveGameplayPress(640f, 600f))
        assertSame(GameplayInput.RestartRun, victory.resolveGameplayPress(640f, 518f))
        assertSame(GameplayInput.OpenRebirth, victory.resolveGameplayPress(640f, 580f))
        assertSame(GameplayInput.ExitToHome, victory.resolveGameplayPress(640f, 650f))
    }

    private fun hitTestState(
        phase: GamePhase,
        choiceCount: Int = 0,
        choicesCanReroll: Boolean = false,
    ): GameplayHitTestState = GameplayHitTestState(
        phase = phase,
        screenWidth = 1_280f,
        screenHeight = 720f,
        uiScale = 1f,
        choiceCount = choiceCount,
        choicesCanReroll = choicesCanReroll,
    )
}
