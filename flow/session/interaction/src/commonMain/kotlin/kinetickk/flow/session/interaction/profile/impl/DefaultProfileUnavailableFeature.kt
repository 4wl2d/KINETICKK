// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.profile.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.flow.session.interaction.profile.api.ProfileUnavailableFeature
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Red
import kinetickk.foundation.design.SpaceBlack
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.White
import kinetickk.foundation.design.d
import kinetickk.foundation.design.drawLabel
import kinetickk.foundation.design.drawOverlayFrame

class DefaultProfileUnavailableFeature : ProfileUnavailableFeature {
    @Composable
    override fun Content() {
        val textMeasurer = CanvasTextMeasurer(rememberTextMeasurer(cacheSize = 8), scale = 1f)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        ) {
            drawProfileUnavailable(textMeasurer)
        }
    }
}

private fun DrawScope.drawProfileUnavailable(textMeasurer: TextMeasurer) {
    drawRect(SpaceBlack.copy(alpha = 0.94f))
    val width = minOf(d(720f), size.width - d(30f))
    val height = minOf(d(300f), size.height - d(30f))
    val left = (size.width - width) * 0.5f
    val top = (size.height - height) * 0.5f
    val bounds = androidx.compose.ui.geometry.Rect(left, top, left + width, top + height)
    drawOverlayFrame(bounds, Red)
    drawLabel(
        textMeasurer,
        "PROFILE UNAVAILABLE",
        bounds.left + d(32f),
        bounds.top + d(34f),
        20f,
        Red,
        weight = FontWeight.Bold,
    )
    drawLabel(
        textMeasurer,
        "THE LOCAL PROFILE COULD NOT BE READ SAFELY. NO LOCAL DATA WAS CHANGED. RESTART THE APPLICATION TO TRY AGAIN.",
        bounds.left + d(40f),
        bounds.top + d(112f),
        10f,
        White,
        maxWidth = bounds.width - d(80f),
        maxLines = 4,
    )
}
