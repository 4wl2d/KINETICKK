// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.localizedContent

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.foundation.design.*
import kotlin.math.min

/** Reserved above Dash/Brake; notices are already grouped by the accepted domain transition. */
internal fun DrawScope.drawBuildNotifications(fx: VisualFxProjection, textMeasurer: TextMeasurer) {
    val notices = fx.buildNotifications
    if (notices.isEmpty()) return
    val width = min(d(340f), size.width * 0.72f)
    val rowHeight = min(d(72f), (size.height - d(155f)).coerceAtLeast(d(96f)) / 3f)
    val right = size.width - d(12f)
    val bottom = size.height - d(if (size.height / density < 480f) 90f else 150f)
    notices.forEachIndexed { index, notice ->
        val top = bottom - (notices.size - index) * rowHeight
        val alpha = (notice.life / 0.6f).coerceIn(0f, 1f)
        drawRect(Color(0xE80D1729).copy(alpha = 0.9f * alpha), Offset(right - width, top), Size(width, rowHeight - d(4f)))
        drawLabel(textMeasurer, notice.title.localizedContent(textMeasurer.language), right - width + d(9f), top + d(6f), 8f, Cyan, maxWidth = width - d(18f), maxLines = 1, alpha = alpha)
        drawLabel(textMeasurer, notice.details.joinToString(" · ") { it.localizedContent(textMeasurer.language) }.ifEmpty { textMeasurer.language.text(GameplayText.BuildUpdated) }, right - width + d(9f), top + d(22f), 7f, White, maxWidth = width - d(18f), maxLines = 3, alpha = alpha)
    }
}
