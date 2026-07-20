// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kinetickk.core.design.SpaceBlack
import kinetickk.core.profile.api.GameplayProgressCapability
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.feature.gameplay.api.GameplayFeature
import kinetickk.feature.gameplay.api.GameplayOutput
import kinetickk.feature.gameplay.api.GameplayUiModel
import kinetickk.feature.gameplay.api.GameplayUiPhase
import kinetickk.feature.gameplay.api.RunConfiguration
import kinetickk.feature.gameplay.domain.engine.GameDispatchResult
import kinetickk.feature.gameplay.domain.model.GamePhase
import kinetickk.feature.gameplay.domain.protocol.GameplayAction

class DefaultGameplayFeature(
    private val progressCapability: GameplayProgressCapability,
) : GameplayFeature {
    private var componentValue by mutableStateOf<GameComponent?>(null)
    private var onOutputValue: (GameplayOutput) -> Unit = {}

    override fun start(configuration: RunConfiguration) {
        componentValue = GameComponent.create(
            configuration = configuration,
            progressCapability = progressCapability,
            onOutput = { output -> onOutputValue(output) },
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
        onOutputValue = onOutput
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
