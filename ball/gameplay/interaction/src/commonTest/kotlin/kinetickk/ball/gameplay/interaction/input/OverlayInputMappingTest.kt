// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class OverlayInputMappingTest {
    @Test
    fun pausedButtonsReturnShellRequestsInsteadOfNavigationActions() {
        val paused = hitTestState(GamePhase.PAUSED)
        val layout = pauseLayoutGeometry(paused.screenWidth, paused.screenHeight, paused.uiScale)
        fun center(target: PauseTarget) = layout.actions.single { it.target == target }.bounds.center

        val resume = assertIs<GameplayInput.Action>(paused.resolveGameplayPress(center(PauseTarget.RESUME).x, center(PauseTarget.RESUME).y))
        assertSame(GameplayInteractionPulse.PauseToggled, resume.action)
        assertSame(GameplayInput.OpenSettings, paused.resolveGameplayPress(center(PauseTarget.SETTINGS).x, center(PauseTarget.SETTINGS).y))
        assertSame(GameplayInput.OpenCodex, paused.resolveGameplayPress(center(PauseTarget.CODEX).x, center(PauseTarget.CODEX).y))
        assertSame(GameplayInput.ExitToHome, paused.resolveGameplayPress(center(PauseTarget.EXIT).x, center(PauseTarget.EXIT).y))
        // The menu sits on the left panel; the build overview on the right is not a target.
        assertNull(paused.resolveGameplayPress(layout.build.center.x, layout.build.center.y))
        assertNull(paused.resolveGameplayPress(layout.titleX + 4f, layout.titleY + 4f))
    }

    @Test
    fun canvasPressNeverSelectsChoiceCardsOrReroll() {
        val choice = hitTestState(
            phase = GamePhase.CHOICE,
            choiceCount = 3,
            choicesCanReroll = true,
        )

        assertNull(choice.resolveGameplayPress(800f, 260f))
        assertNull(choice.resolveGameplayPress(640f, 648f))
    }

    @Test
    fun terminalCanvasCannotActivateHiddenOrDuplicateActions() {
        val gameOver = hitTestState(GamePhase.GAME_OVER)
        val victory = hitTestState(GamePhase.VICTORY)

        assertNull(gameOver.resolveGameplayPress(640f, 518f))
        assertNull(gameOver.resolveGameplayPress(640f, 600f))
        assertNull(victory.resolveGameplayPress(640f, 518f))
        assertNull(victory.resolveGameplayPress(640f, 580f))
        assertNull(victory.resolveGameplayPress(640f, 650f))
    }

    @Test
    fun compactPauseAndChoiceMappingMatchesRenderedTargets() {
        val paused = hitTestState(
            phase = GamePhase.PAUSED,
            screenWidth = 2_400f,
            screenHeight = 1_080f,
            uiScale = 3f,
        )
        val performance = pauseLayoutGeometry(paused.screenWidth, paused.screenHeight, paused.uiScale)
            .actions
            .single { it.target == PauseTarget.PERFORMANCE }
            .bounds
            .center
        assertSame(
            GameplayInput.TogglePerformance,
            paused.resolveGameplayPress(performance.x, performance.y),
        )

        val choice = hitTestState(
            phase = GamePhase.CHOICE,
            choiceCount = 4,
            choicesCanReroll = true,
            screenWidth = 2_400f,
            screenHeight = 1_080f,
            uiScale = 3f,
        )
        val layout = choiceLayoutGeometry(
            choice.screenWidth,
            choice.screenHeight,
            choice.uiScale,
            choice.choiceCount,
            choice.choicesCanReroll,
        )
        layout.cards.forEach { bounds ->
            assertNull(choice.resolveGameplayPress(bounds.center.x, bounds.center.y))
        }
        val reroll = requireNotNull(layout.reroll).center
        assertNull(choice.resolveGameplayPress(reroll.x, reroll.y))
    }

    private fun hitTestState(
        phase: GamePhase,
        choiceCount: Int = 0,
        choicesCanReroll: Boolean = false,
        screenWidth: Float = 1_280f,
        screenHeight: Float = 720f,
        uiScale: Float = 1f,
    ): GameplayHitTestState = GameplayHitTestState(
        phase = phase,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        choiceCount = choiceCount,
        choicesCanReroll = choicesCanReroll,
    )
}
