// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.matrix.impl

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.matrix.api.MatrixFeature
import kinetickk.ball.profile.interaction.matrix.api.MatrixOutput
import kinetickk.foundation.collections.ImmutableList
import kinetickk.resource.audio.api.AudioService

class DefaultMatrixFeature(
    private val profilePort: ProfilePort,
    private val matrixNodes: ImmutableList<MatrixNodeDefinition>,
    audioService: AudioService,
) : MatrixFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    @Composable
    override fun Content(
        routeToken: Long,
        onOutput: (MatrixOutput) -> Unit,
    ) {
        fun model() = profilePort.query(ProfileQuery.GetMatrixProgress).snapshot.toRenderModel(matrixNodes)
        var stateValue by remember(profilePort, matrixNodes, routeToken) { mutableStateOf(MatrixState(model())) }
        val textScale = remember(profilePort, routeToken) {
            profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        }
        val gridScroll = rememberScrollState()
        fun dispatch(action: MatrixAction) {
            val reduction = MatrixReducer.reduce(stateValue, action)
            stateValue = reduction.state
            reduction.effects.forEach { effect ->
                when (effect) {
                    is MatrixUiEffect.Purchase -> {
                        val accepted = profilePort.accept(ProfilePulse.PurchaseMatrixNode(effect.id)) is ProfileAcceptance.Accepted
                        stateValue = MatrixReducer.purchased(stateValue, effect.id, model(), accepted)
                        if (accepted) audioExecutor.play(ProfileAudioCue.PURCHASE)
                    }
                    MatrixUiEffect.Reset -> {
                        val accepted = profilePort.accept(ProfilePulse.ResetMatrix) is ProfileAcceptance.Accepted
                        stateValue = MatrixReducer.reset(stateValue, model())
                        audioExecutor.play(if (accepted) ProfileAudioCue.PURCHASE else ProfileAudioCue.UI_CLICK)
                    }
                    is MatrixUiEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is MatrixUiEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        MatrixContent(stateValue, textScale, gridScroll, onAction = ::dispatch)
    }
}
