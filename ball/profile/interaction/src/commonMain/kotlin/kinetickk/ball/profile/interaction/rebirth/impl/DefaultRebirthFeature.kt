// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.resource.audio.api.AudioService

class DefaultRebirthFeature(
    private val profilePort: ProfilePort,
    private val rebirthPolicy: RebirthPolicySnapshot,
    audioService: AudioService,
) : RebirthFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    @Composable
    override fun Content(
        routeToken: Int,
        eligible: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    ) {
        var renderModelValue by remember(profilePort, rebirthPolicy, routeToken, eligible) {
            mutableStateOf(
                profilePort
                    .query(ProfileQuery.GetRebirthProgress)
                    .toRenderModel(rebirthPolicy, eligible),
            )
        }
        var confirmationArmedValue by rememberSaveable(routeToken, eligible) { mutableStateOf(false) }
        val textScale = remember(profilePort, routeToken) {
            profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        }
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        val textMeasurer = CanvasTextMeasurer(
            delegate = composeTextMeasurer,
            scale = textScale,
        )

        fun dispatch(action: RebirthAction) {
            val reduction = RebirthReducer.reduce(
                state = RebirthState(renderModelValue, confirmationArmedValue),
                action = action,
            )
            renderModelValue = reduction.state.model
            confirmationArmedValue = reduction.state.armed
            reduction.effects.forEach { effect ->
                when (effect) {
                    RebirthEffect.AdvanceCycle -> {
                        val acceptance = profilePort.accept(ProfilePulse.AdvanceRebirth)
                        val projection = profilePort.query(ProfileQuery.GetRebirthProgress)
                        renderModelValue = projection.toRenderModel(rebirthPolicy, eligible)
                        confirmationArmedValue = false
                        if (acceptance is ProfileAcceptance.Accepted) {
                            audioExecutor.play(ProfileAudioCue.PURCHASE)
                            onOutput(RebirthOutput.CycleAdvanced(projection.snapshot.progress))
                        }
                    }
                    is RebirthEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is RebirthEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(routeToken, eligible, renderModelValue, confirmationArmedValue, onOutput) {
                    detectTapGestures { position ->
                        resolveRebirthPress(
                            screenWidth = size.width.toFloat(),
                            screenHeight = size.height.toFloat(),
                            density = density,
                            x = position.x,
                            y = position.y,
                        )?.let(::dispatch)
                    }
                },
        ) {
            drawRebirth(
                model = renderModelValue,
                confirmationArmed = confirmationArmedValue,
                textMeasurer = textMeasurer,
            )
        }
    }
}
