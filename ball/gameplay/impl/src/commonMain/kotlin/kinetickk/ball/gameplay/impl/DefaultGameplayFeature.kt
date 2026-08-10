// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kinetickk.foundation.design.SpaceBlack
import kinetickk.ball.profile.api.GameplayProgressCapability
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.gameplay.api.GameplayOutput
import kinetickk.ball.gameplay.api.GameplayUiModel
import kinetickk.ball.gameplay.api.GameplayUiPhase
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.gameplay.nucleus.engine.GameDispatchResult
import kinetickk.ball.gameplay.nucleus.model.GamePhase
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAction
import kinetickk.resource.audio.api.AudioService

class DefaultGameplayFeature(
    private val progressCapability: GameplayProgressCapability,
    audioService: AudioService,
) : GameplayFeature {
    private val audioExecutor = ResourceGameplayAudioExecutor(audioService)
    private var componentValue by mutableStateOf<GameComponent?>(null)

    override fun start(configuration: RunConfiguration) {
        componentValue = GameComponent.create(
            configuration = configuration,
            progressCapability = progressCapability,
            audioExecutor = audioExecutor,
        )
    }

    override fun applyPreferences(preferences: PlayerPreferences) {
        componentValue?.dispatch(GameplayAction.PreferencesChanged(preferences))
    }

    override fun pauseForOverlay(): Boolean {
        val component = componentValue ?: return false
        if (component.snapshot().renderModel.phase != GamePhase.RUNNING) return false
        val result = component.dispatch(GameplayAction.PauseForOverlay)
        return result is GameDispatchResult.Committed &&
            result.snapshot.renderModel.phase == GamePhase.PAUSED
    }

    override fun togglePause() {
        componentValue?.dispatch(GameplayAction.PauseToggled)
    }

    override fun uiModel(): GameplayUiModel {
        val renderModel = componentValue?.snapshot()?.renderModel ?: return GameplayUiModel()
        return GameplayUiModel(
            phase = renderModel.phase.toUiPhase(),
            activeWeapon = renderModel.weapon,
            itemStacks = renderModel.itemStacksSnapshot,
        )
    }

    @Composable
    override fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayOutput) -> Unit,
    ) {
        val component = componentValue
        if (component == null) {
            Canvas(Modifier.fillMaxSize()) { drawRect(SpaceBlack) }
        } else {
            GameplayContent(
                component = component,
                inputEnabled = inputEnabled,
                onShellOutput = onOutput,
            )
        }
    }
}

private fun GamePhase.toUiPhase(): GameplayUiPhase = when (this) {
    GamePhase.RUNNING -> GameplayUiPhase.RUNNING
    GamePhase.PAUSED -> GameplayUiPhase.PAUSED
    GamePhase.CHOICE -> GameplayUiPhase.CHOICE
    GamePhase.GAME_OVER -> GameplayUiPhase.GAME_OVER
    GamePhase.VICTORY -> GameplayUiPhase.VICTORY
}
