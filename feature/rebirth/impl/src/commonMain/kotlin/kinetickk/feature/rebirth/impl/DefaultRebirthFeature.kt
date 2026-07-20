// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.rebirth.impl

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
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.design.CanvasTextMeasurer
import kinetickk.core.profile.api.PreferencesReader
import kinetickk.core.profile.api.ProfileMutationResult
import kinetickk.core.profile.api.RebirthCapability
import kinetickk.feature.rebirth.api.RebirthFeature
import kinetickk.feature.rebirth.api.RebirthOutput

class DefaultRebirthFeature(
    private val capability: RebirthCapability,
    private val preferencesReader: PreferencesReader,
) : RebirthFeature {
    @Composable
    override fun Content(
        routeToken: Int,
        eligible: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    ) {
        var renderModelValue by remember(capability, routeToken, eligible) {
            mutableStateOf(capability.rebirthSnapshot().toRenderModel(eligible))
        }
        var confirmationArmedValue by rememberSaveable(routeToken, eligible) { mutableStateOf(false) }
        val textScale = remember(preferencesReader, routeToken) {
            preferencesReader.preferences().textScale
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
                        val result = capability.advanceRebirth()
                        renderModelValue = capability.rebirthSnapshot().toRenderModel(eligible)
                        confirmationArmedValue = false
                        if (result is ProfileMutationResult.Applied) {
                            val progress = capability.rebirthSnapshot().progress
                            onOutput(RebirthOutput.Cue(AudioCue.PURCHASE))
                            onOutput(RebirthOutput.CycleAdvanced(progress))
                        }
                    }
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
