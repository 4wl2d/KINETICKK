// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.resource.audio.api.AudioService

class DefaultSettingsFeature(
    private val profilePort: ProfilePort,
    audioService: AudioService,
) : SettingsFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    @Composable
    override fun Content(
        routeToken: Int,
        onOutput: (SettingsOutput) -> Unit,
    ) {
        var renderModelValue by remember(profilePort, routeToken) {
            mutableStateOf(
                profilePort.query(ProfileQuery.GetPreferences).preferences.toRenderModel(),
            )
        }
        var pageValue by rememberSaveable(routeToken) { mutableIntStateOf(0) }
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        val textMeasurer = CanvasTextMeasurer(
            delegate = composeTextMeasurer,
            scale = renderModelValue.preferences.textScale,
        )

        fun dispatch(action: SettingsAction) {
            val reduction = SettingsReducer.reduce(
                state = SettingsState(renderModelValue, pageValue),
                action = action,
            )
            renderModelValue = reduction.state.model
            pageValue = reduction.state.page
            reduction.effects.forEach { effect ->
                when (effect) {
                    is SettingsEffect.AdjustPreference -> {
                        profilePort.accept(ProfilePulse.AdjustPreference(effect.adjustment))
                        renderModelValue = profilePort
                            .query(ProfileQuery.GetPreferences)
                            .preferences
                            .toRenderModel()
                        audioExecutor.updatePreferences(renderModelValue.preferences)
                    }
                    is SettingsEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is SettingsEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(routeToken, renderModelValue, pageValue, onOutput) {
                    detectTapGestures { position ->
                        resolveSettingsPress(
                            screenWidth = size.width.toFloat(),
                            screenHeight = size.height.toFloat(),
                            density = density,
                            page = pageValue,
                            x = position.x,
                            y = position.y,
                        )?.let(::dispatch)
                    }
                },
        ) {
            drawSettings(
                model = renderModelValue,
                page = pageValue,
                textMeasurer = textMeasurer,
            )
        }
    }
}
