// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.lab.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.design.CanvasTextMeasurer
import kinetickk.core.profile.api.LabPurchaseCapability
import kinetickk.core.profile.api.PreferencesReader
import kinetickk.core.profile.api.ProfileMutationResult
import kinetickk.feature.lab.api.LabFeature
import kinetickk.feature.lab.api.LabOutput

class DefaultLabFeature(
    private val capability: LabPurchaseCapability,
    private val preferencesReader: PreferencesReader,
) : LabFeature {
    @Composable
    override fun Content(
        routeToken: Int,
        onOutput: (LabOutput) -> Unit,
    ) {
        var renderModelValue by remember(capability, routeToken) {
            mutableStateOf(capability.labSnapshot().toRenderModel())
        }
        val textScale = remember(preferencesReader, routeToken) {
            preferencesReader.preferences().textScale
        }
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        val textMeasurer = CanvasTextMeasurer(
            delegate = composeTextMeasurer,
            scale = textScale,
        )

        fun dispatch(action: LabAction) {
            val reduction = LabReducer.reduce(LabState(renderModelValue), action)
            renderModelValue = reduction.state.model
            reduction.effects.forEach { effect ->
                when (effect) {
                    is LabEffect.Purchase -> {
                        val result = capability.purchaseMetaUpgrade(effect.id)
                        renderModelValue = capability.labSnapshot().toRenderModel()
                        if (result is ProfileMutationResult.Applied) {
                            onOutput(LabOutput.Cue(AudioCue.PURCHASE))
                        }
                    }
                    is LabEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(routeToken, renderModelValue, onOutput) {
                    detectTapGestures { position ->
                        resolveLabPress(
                            screenWidth = size.width.toFloat(),
                            screenHeight = size.height.toFloat(),
                            density = density,
                            x = position.x,
                            y = position.y,
                        )?.let(::dispatch)
                    }
                },
        ) {
            drawLab(renderModelValue, textMeasurer)
        }
    }
}
