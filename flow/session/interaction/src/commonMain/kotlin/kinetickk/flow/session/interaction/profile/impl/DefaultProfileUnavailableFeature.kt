// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.profile.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.flow.session.interaction.profile.api.ProfileUnavailableFeature
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.drawKkGrid
import kinetickk.foundation.design.drawKkStripes
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.wideStyle
import kotlin.math.max
import kotlin.math.min

class DefaultProfileUnavailableFeature : ProfileUnavailableFeature {
    @Composable
    override fun Content() {
        val language = LocalAppLanguage.current
        val roles = LocalKkRolePalette.current
        val delegate = rememberTextMeasurer(cacheSize = 8)
        val typography = rememberInterfaceTypography()
        val textMeasurer = remember(delegate, language, typography, roles) {
            CanvasTextMeasurer(delegate, scale = 1f, language = language, typography = typography, roles = roles)
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = language.text(SessionText.PROFILE_UNAVAILABLE) + ". " +
                        language.text(SessionText.PROFILE_UNAVAILABLE_BODY)
                }
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

/**
 * Blocking ink screen: threat stripes, a wide title and the body text. It has no actions (the
 * player restarts the application), so it shows no buttons.
 */
private fun DrawScope.drawProfileUnavailable(textMeasurer: CanvasTextMeasurer) {
    val language = textMeasurer.language
    drawRect(Kk.Ink)
    drawKkGrid(Rect(Offset.Zero, size), Kk.Bone.copy(alpha = 0.045f), 48f)
    val margin = max(24f * density, min(size.width, size.height) * 0.06f)
    val width = min(760f * density, size.width - margin * 2f)
    val left = (size.width - width) * 0.5f
    val titleText = language.text(SessionText.PROFILE_UNAVAILABLE)
    // The wide title shrinks until it fits two lines inside the column.
    var titleSize = min(64f, size.width / density * 0.075f).coerceAtLeast(22f)
    var title = measureKkText(textMeasurer, titleText, textMeasurer.typography.wideStyle(titleSize, lineHeightEm = 1f),
        uppercase = true, maxWidth = width, maxLines = 2)
    while (titleSize > 18f && (title.didOverflowWidth || title.didOverflowHeight || title.hasVisualOverflow)) {
        titleSize -= 2f
        title = measureKkText(textMeasurer, titleText, textMeasurer.typography.wideStyle(titleSize, lineHeightEm = 1f),
            uppercase = true, maxWidth = width, maxLines = 2)
    }
    val body = measureKkText(textMeasurer, language.text(SessionText.PROFILE_UNAVAILABLE_BODY),
        textMeasurer.typography.bodyStyle(if (size.width < 600f * density) 16f else 19f, lineHeightEm = 1.45f),
        maxWidth = width, maxLines = 8)
    val stripeHeight = 10f * density
    val gap = 28f * density
    val total = stripeHeight + gap + title.kkBoxHeight + gap + body.kkBoxHeight
    var y = (size.height - total) * 0.5f
    drawKkStripes(Rect(left, y, left + min(width, 180f * density), y + stripeHeight), textMeasurer.roles.threat)
    y += stripeHeight + gap
    drawKkText(title, left, y, Kk.Bone)
    y += title.kkBoxHeight + gap
    drawKkText(body, left, y, Kk.Bone2)
}
