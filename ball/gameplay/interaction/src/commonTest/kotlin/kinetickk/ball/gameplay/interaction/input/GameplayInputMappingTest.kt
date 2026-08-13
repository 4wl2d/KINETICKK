// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun regularBrakeBoundingSquareCornersRemainOutsideTheCanonicalCircle() {
        val running = hitTestState(GamePhase.RUNNING)
        val brakeBounds = runningControlBounds(
            running.screenWidth,
            running.screenHeight,
            running.uiScale,
        ).single { it.target == RunningControlTarget.BRAKE }.bounds
        val cornerX = brakeBounds.left + 1f
        val cornerY = brakeBounds.top + 1f

        assertNull(running.resolveGameplayPress(cornerX, cornerY))
        assertFalse(running.isHudControlPosition(cornerX, cornerY))
    }

    @Test
    fun runningHudClassificationUsesTheSameCanonicalTargetsAsActionResolution() {
        val running = hitTestState(GamePhase.RUNNING)

        runningControlBounds(running.screenWidth, running.screenHeight, running.uiScale)
            .forEach { control ->
                val center = control.bounds.center
                assertTrue(running.isHudControlPosition(center.x, center.y))
                assertIs<GameplayInput>(running.resolveGameplayPress(center.x, center.y))
            }

        assertFalse(running.isHudControlPosition(640f, 360f))
        assertNull(running.resolveGameplayPress(640f, 360f))
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

        assertEquals(2, assertIs<GameplayInteractionPulse.ChoiceSelected>(selected.action).index)
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

    @Test
    fun compactRunningControlsMapDashBrakePauseAndPerformanceFromSharedGeometry() {
        val running = hitTestState(
            phase = GamePhase.RUNNING,
            screenWidth = 2_400f,
            screenHeight = 1_080f,
            uiScale = 3f,
        )

        runningControlBounds(running.screenWidth, running.screenHeight, running.uiScale).forEach { control ->
            val input = running.resolveGameplayPress(control.bounds.center.x, control.bounds.center.y)
            when (control.target) {
                RunningControlTarget.DASH -> assertSame(
                    GameplayInteractionPulse.DashRequested,
                    assertIs<GameplayInput.Action>(input).action,
                )
                RunningControlTarget.BRAKE -> assertIs<GameplayInteractionPulse.BrakeChanged>(
                    assertIs<GameplayInput.Action>(input).action,
                )
                RunningControlTarget.PERFORMANCE -> assertSame(GameplayInput.TogglePerformance, input)
                RunningControlTarget.PAUSE -> assertSame(
                    GameplayInteractionPulse.PauseToggled,
                    assertIs<GameplayInput.Action>(input).action,
                )
            }
        }
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
        layout.cards.forEachIndexed { index, bounds ->
            val input = assertIs<GameplayInput.Action>(
                choice.resolveGameplayPress(bounds.center.x, bounds.center.y),
            )
            assertEquals(index, assertIs<GameplayInteractionPulse.ChoiceSelected>(input.action).index)
        }
        val reroll = requireNotNull(layout.reroll).center
        assertSame(
            GameplayInteractionPulse.ChoicesRerolled,
            assertIs<GameplayInput.Action>(choice.resolveGameplayPress(reroll.x, reroll.y)).action,
        )
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
