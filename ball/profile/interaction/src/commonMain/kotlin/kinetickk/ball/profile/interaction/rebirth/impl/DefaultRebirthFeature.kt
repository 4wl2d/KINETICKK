// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.resource.audio.api.AudioService

class DefaultRebirthFeature(
    private val profilePort: ProfilePort,
    private val rebirthPolicy: RebirthPolicySnapshot,
    audioService: AudioService,
) : RebirthFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    override fun playAcceptedFeedback() {
        audioExecutor.play(ProfileAudioCue.PURCHASE)
    }

    @Composable
    override fun Content(
        routeToken: Long,
        eligible: Boolean,
        confirmationArmed: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    ) {
        val renderModelValue = remember(
            profilePort,
            rebirthPolicy,
            routeToken,
            eligible,
            confirmationArmed,
        ) {
            profilePort
                .query(ProfileQuery.GetRebirthProgress)
                .toRenderModel(rebirthPolicy, eligible)
        }
        val textScale = remember(profilePort, routeToken) {
            profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        }
        fun dispatch(action: RebirthAction) {
            val reduction = RebirthReducer.reduce(
                state = RebirthState(renderModelValue, confirmationArmed),
                action = action,
            )
            reduction.effects.forEach { effect ->
                when (effect) {
                    is RebirthEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is RebirthEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        RebirthContent(renderModelValue, confirmationArmed, textScale, ::dispatch)
    }
}
