// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.settings.impl

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
import kinetickk.core.design.CanvasTextMeasurer
import kinetickk.core.profile.api.SettingsProfileCapability
import kinetickk.feature.settings.api.SettingsFeature
import kinetickk.feature.settings.api.SettingsOutput

class DefaultSettingsFeature(
    private val capability: SettingsProfileCapability,
) : SettingsFeature {
    @Composable
    override fun Content(
        routeToken: Int,
        onOutput: (SettingsOutput) -> Unit,
    ) {
        var renderModelValue by remember(capability, routeToken) {
            mutableStateOf(capability.preferences().toRenderModel())
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
                    is SettingsEffect.UpdatePreferences -> {
                        capability.updatePreferences(effect.preferences)
                        renderModelValue = capability.preferences().toRenderModel()
                    }
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
