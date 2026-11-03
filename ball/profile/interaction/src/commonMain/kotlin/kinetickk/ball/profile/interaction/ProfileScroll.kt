// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Scroll cue for a scroll viewport: a fade into [color] at the edge that has more content
 * ([fade] px tall), drawn over the scrolling content and redrawn as it scrolls.
 */
internal fun Modifier.profileScrollCue(scroll: ScrollState, color: Color, fade: Float): Modifier = drawWithCache {
    val bottom = Brush.verticalGradient(listOf(color.copy(alpha = 0f), color), startY = size.height - fade, endY = size.height)
    val top = Brush.verticalGradient(listOf(color, color.copy(alpha = 0f)), startY = 0f, endY = fade)
    onDrawWithContent {
        drawContent()
        if (scroll.value < scroll.maxValue) drawRect(bottom, Offset(0f, size.height - fade), Size(size.width, fade))
        if (scroll.value > 0) drawRect(top, Offset.Zero, Size(size.width, fade))
    }
}

/**
 * Scroll bar for a scroll viewport whose content overflows: a [width] px track at [x] px from the
 * viewport's left, [inset] px short of its top and bottom, with a thumb sized and placed by the
 * scroll. Unlike the fades it shows whenever more content exists, even when the fold falls in the
 * gap between two rows. Reported to [ProfileTextProbe] as [role].
 */
internal fun Modifier.profileScrollBar(
    scroll: ScrollState,
    x: Float,
    width: Float,
    inset: Float,
    thumb: Color,
    track: Color,
    role: String,
): Modifier = drawWithContent {
    drawContent()
    val max = scroll.maxValue
    val length = size.height - inset * 2f
    if (max <= 0 || length <= 0f) return@drawWithContent
    drawRect(track, Offset(x, inset), Size(width, length))
    val content = scroll.viewportSize + max
    // The thumb is the visible share of the content (at least a short bar), placed by the scroll.
    val thumbLength = if (content > 0) (length * scroll.viewportSize / content).coerceIn(min(length, width * 8f), length) else length
    val thumbTop = inset + (length - thumbLength) * scroll.value.coerceIn(0, max) / max
    drawRect(thumb, Offset(x, thumbTop), Size(width, thumbLength))
    if (ProfileTextProbe.sink != null) {
        ProfileTextProbe.record(role, scroll.value, null, Rect(x, thumbTop, x + width, thumbTop + thumbLength), thumb)
    }
}
