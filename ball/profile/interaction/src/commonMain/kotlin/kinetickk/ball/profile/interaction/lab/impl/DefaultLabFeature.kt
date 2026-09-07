// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.lab.api.LabFeature
import kinetickk.ball.profile.interaction.lab.api.LabOutput
import kinetickk.foundation.collections.ImmutableList
import kinetickk.resource.audio.api.AudioService

class DefaultLabFeature(
    private val profilePort: ProfilePort,
    private val metaUpgrades: ImmutableList<MetaUpgradeDefinition>,
    audioService: AudioService,
) : LabFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    @Composable
    override fun Content(
        routeToken: Long,
        onOutput: (LabOutput) -> Unit,
    ) {
        var renderModelValue by remember(profilePort, metaUpgrades, routeToken) {
            mutableStateOf(
                profilePort
                    .query(ProfileQuery.GetLabProgress)
                    .snapshot
                    .toRenderModel(metaUpgrades),
            )
        }
        val textScale = remember(profilePort, routeToken) {
            profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        }
        fun dispatch(action: LabAction) {
            val reduction = LabReducer.reduce(LabState(renderModelValue), action)
            renderModelValue = reduction.state.model
            reduction.effects.forEach { effect ->
                when (effect) {
                    is LabEffect.Purchase -> {
                        val acceptance = profilePort.accept(ProfilePulse.PurchaseMetaUpgrade(effect.id))
                        renderModelValue = profilePort
                            .query(ProfileQuery.GetLabProgress)
                            .snapshot
                            .toRenderModel(metaUpgrades)
                        if (acceptance is ProfileAcceptance.Accepted) {
                            audioExecutor.play(ProfileAudioCue.PURCHASE)
                        }
                    }
                    is LabEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is LabEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        LabContent(renderModelValue, textScale, ::dispatch)
    }
}
