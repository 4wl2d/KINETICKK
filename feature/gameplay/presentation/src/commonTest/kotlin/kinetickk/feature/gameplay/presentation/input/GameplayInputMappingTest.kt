// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.presentation.input

import kinetickk.feature.gameplay.domain.engine.GameDispatchResult
import kinetickk.feature.gameplay.domain.engine.GameEngine
import kinetickk.feature.gameplay.domain.protocol.GameplayAction
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class GameplayInputMappingTest {
    @Test
    fun runningHudMapsOnlyToLiveRunActions() {
        val model = engine().snapshot().renderModel

        val dash = assertIs<GameplayInput.Action>(model.resolveGameplayPress(1_198f, 632f))
        val brake = assertIs<GameplayInput.Action>(model.resolveGameplayPress(1_090f, 653f))

        assertSame(GameplayAction.DashRequested, dash.action)
        assertIs<GameplayAction.BrakeChanged>(brake.action)
    }

    @Test
    fun pausedButtonsReturnShellRequestsInsteadOfNavigationActions() {
        val engine = engine()
        val paused = assertIs<GameDispatchResult.Committed>(
            engine.dispatch(GameplayAction.PauseToggled),
        ).snapshot.renderModel

        val resume = assertIs<GameplayInput.Action>(paused.resolveGameplayPress(640f, 386f))
        assertSame(GameplayAction.PauseToggled, resume.action)
        assertSame(GameplayInput.OpenSettings, paused.resolveGameplayPress(640f, 472f))
        assertSame(GameplayInput.ExitToHome, paused.resolveGameplayPress(640f, 558f))
    }

    private fun engine(): GameEngine = GameEngine.create(
        bootstrapProgress = null,
        seed = 73,
        initialMatter = 0,
    )
}
