// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.reset.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.reset.api.ResetModalFeature
import kinetickk.flow.session.interaction.reset.api.ResetModalMode
import kinetickk.flow.session.interaction.reset.api.ResetModalOutput
import kinetickk.flow.session.interaction.reset.api.ResetModalRenderModel
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.DarkLine
import kinetickk.foundation.design.Muted
import kinetickk.foundation.design.Orange
import kinetickk.foundation.design.Red
import kinetickk.foundation.design.SpaceBlack
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.White
import kinetickk.foundation.design.d
import kinetickk.foundation.design.drawLabel
import kinetickk.foundation.design.drawOverlayFrame
import kinetickk.resource.audio.api.AudioService

class DefaultResetModalFeature(
    audioService: AudioService,
) : ResetModalFeature {
    private val audioExecutor = SessionAudioExecutor(audioService)

    @Composable
    override fun Content(
        model: ResetModalRenderModel,
        onOutput: (ResetModalOutput) -> Unit,
    ) {
        val density = LocalDensity.current.density
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 32)
        var viewportValue by remember { mutableStateOf(ResetModalViewport(1f, 1f, density)) }
        val textMeasurer = CanvasTextMeasurer(composeTextMeasurer, scale = 1f)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewportValue = ResetModalViewport(size.width.toFloat(), size.height.toFloat(), density)
                }
                .pointerInput(model, viewportValue, onOutput) {
                    detectTapGestures { position ->
                        resolveResetModalPress(
                            model = model,
                            viewport = viewportValue,
                            x = position.x,
                            y = position.y,
                        )?.let { output ->
                            audioExecutor.playUiClick()
                            onOutput(output)
                        }
                    }
                },
        ) {
            drawResetModal(model, textMeasurer)
        }
    }
}

private fun DrawScope.drawResetModal(
    model: ResetModalRenderModel,
    textMeasurer: TextMeasurer,
) {
    drawRect(SpaceBlack.copy(alpha = 0.94f))
    val width = minOf(d(720f), size.width - d(30f))
    val height = minOf(d(420f), size.height - d(30f))
    val left = (size.width - width) * 0.5f
    val top = (size.height - height) * 0.5f
    val bounds = androidx.compose.ui.geometry.Rect(left, top, left + width, top + height)
    val accent = when (model.mode) {
        ResetModalMode.CONFIRMATION_REQUIRED -> Red
        ResetModalMode.RESET_IN_PROGRESS -> Orange
        ResetModalMode.PURGE_NEEDS_ATTENTION -> Orange
        ResetModalMode.BOOTSTRAP_UNAVAILABLE -> Red
    }
    drawOverlayFrame(bounds, accent)

    val title = when (model.mode) {
        ResetModalMode.CONFIRMATION_REQUIRED -> "SAVE RESET REQUIRED"
        ResetModalMode.RESET_IN_PROGRESS -> "RESETTING PROFILE"
        ResetModalMode.PURGE_NEEDS_ATTENTION -> "RESET NEEDS ATTENTION"
        ResetModalMode.BOOTSTRAP_UNAVAILABLE -> "PROFILE UNAVAILABLE"
    }
    val message = when (model.mode) {
        ResetModalMode.CONFIRMATION_REQUIRED ->
            "THIS VERSION USES A NEW SAVE FORMAT. STARTING FRESH WILL WRITE A V4 SAVE BEFORE REMOVING ONLY THE KNOWN LEGACY KEYS."
        ResetModalMode.RESET_IN_PROGRESS ->
            "WRITING A FRESH V4 SAVE AND VERIFYING LEGACY CLEANUP. DO NOT CLOSE THE APPLICATION."
        ResetModalMode.PURGE_NEEDS_ATTENTION ->
            "THE FRESH SAVE EXISTS, BUT LEGACY CLEANUP COULD NOT BE CONFIRMED. RETRY THE PURGE MANUALLY."
        ResetModalMode.BOOTSTRAP_UNAVAILABLE ->
            "THE LOCAL PROFILE PROVIDER COULD NOT BE READ SAFELY. NO SAVE DATA WAS CHANGED. RESTART THE APPLICATION TO TRY AGAIN."
    }
    drawLabel(
        textMeasurer,
        title,
        bounds.left + d(32f),
        bounds.top + d(34f),
        20f,
        accent,
        weight = FontWeight.Bold,
    )
    drawLabel(
        textMeasurer,
        message,
        bounds.left + d(40f),
        bounds.top + d(112f),
        10f,
        White,
        maxWidth = bounds.width - d(80f),
        maxLines = 4,
    )
    if (model.mode == ResetModalMode.CONFIRMATION_REQUIRED) {
        drawLabel(
            textMeasurer,
            "THIS CANNOT BE UNDONE. LEGACY PROGRESS WILL NOT BE IMPORTED.",
            bounds.left + d(40f),
            bounds.top + d(224f),
            8f,
            Red,
            maxWidth = bounds.width - d(80f),
            maxLines = 2,
        )
    }

    if (model.mode == ResetModalMode.RESET_IN_PROGRESS) {
        drawLabel(
            textMeasurer,
            "PLEASE WAIT",
            bounds.left + bounds.width * 0.5f,
            bounds.bottom - d(62f),
            10f,
            Orange,
            centered = true,
            weight = FontWeight.Bold,
        )
        return
    }

    val gap = d(16f)
    val buttonWidth = (bounds.width - d(80f) - gap) * 0.5f
    val buttonTop = bounds.bottom - d(82f)
    val buttonHeight = d(52f)
    drawResetButton(
        textMeasurer = textMeasurer,
        label = "CANCEL",
        x = bounds.left + d(40f),
        y = buttonTop,
        width = buttonWidth,
        height = buttonHeight,
        accent = DarkLine,
    )
    if (model.mode == ResetModalMode.BOOTSTRAP_UNAVAILABLE) return
    drawResetButton(
        textMeasurer = textMeasurer,
        label = when (model.mode) {
            ResetModalMode.CONFIRMATION_REQUIRED -> "DELETE SAVE & START FRESH"
            ResetModalMode.PURGE_NEEDS_ATTENTION -> "RETRY PURGE"
            ResetModalMode.RESET_IN_PROGRESS -> error("Handled above")
            ResetModalMode.BOOTSTRAP_UNAVAILABLE -> error("Handled above")
        },
        x = bounds.left + d(40f) + buttonWidth + gap,
        y = buttonTop,
        width = buttonWidth,
        height = buttonHeight,
        accent = accent,
    )
}

private fun DrawScope.drawResetButton(
    textMeasurer: TextMeasurer,
    label: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    accent: Color,
) {
    drawRect(Color(0xCC101225), Offset(x, y), Size(width, height))
    drawRect(accent, Offset(x, y), Size(width, height), style = Stroke(d(1.5f)))
    drawLabel(
        textMeasurer,
        label,
        x + width * 0.5f,
        y + d(18f),
        9f,
        if (accent == DarkLine) Muted else accent,
        centered = true,
        weight = FontWeight.Bold,
    )
}
