// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.impl

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.hangar.api.HangarFeature
import kinetickk.ball.profile.interaction.hangar.api.HangarOutput
import kinetickk.foundation.collections.ImmutableList
import kinetickk.resource.audio.api.AudioService

/** Character selection: the Profile's Hangar query feeds the roster, its local pulse selects. */
class DefaultHangarFeature(
    private val profilePort: ProfilePort,
    private val coreShapes: ImmutableList<CoreShapeDefinition>,
    audioService: AudioService,
) : HangarFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    @Composable
    override fun Content(
        routeToken: Long,
        onOutput: (HangarOutput) -> Unit,
    ) {
        fun model() = profilePort.query(ProfileQuery.GetHangar).toRenderModel(
            coreShapes,
            profilePort.query(ProfileQuery.GetLoadout).snapshot.economy.matter,
        )
        var stateValue by remember(profilePort, coreShapes, routeToken) { mutableStateOf(HangarState(model())) }
        val textScale = remember(profilePort, routeToken) {
            profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        }
        val detailScroll = rememberScrollState()
        fun dispatch(action: HangarAction) {
            val reduction = HangarReducer.reduce(stateValue, action)
            stateValue = reduction.state
            reduction.effects.forEach { effect ->
                when (effect) {
                    is HangarUiEffect.Select -> {
                        // Unlock rules belong to the Profile Nucleus; a refusal leaves the selection unchanged.
                        val accepted = profilePort.accept(ProfilePulse.SelectCoreShape(effect.shape)) is ProfileAcceptance.Accepted
                        stateValue = HangarReducer.selected(stateValue, model())
                        audioExecutor.play(if (accepted) ProfileAudioCue.PURCHASE else ProfileAudioCue.UI_CLICK)
                    }
                    is HangarUiEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is HangarUiEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        HangarContent(stateValue, textScale, detailScroll, onAction = ::dispatch)
    }
}
