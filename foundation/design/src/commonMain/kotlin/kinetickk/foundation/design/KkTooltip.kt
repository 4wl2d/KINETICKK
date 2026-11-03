// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max

/**
 * Info (!) button (`.info`, 24 dp): sheared 1.5 dp outline square with an italic "!", mute, or
 * `you` when [active] (hover/focus/open). Use [KkInfoButton] in composables; Canvas screens draw
 * this and expose a focusable semantics node carrying the explanation.
 */
fun DrawScope.drawKkInfoButton(measurer: CanvasTextMeasurer, bounds: Rect, active: Boolean = false) {
    val color = if (active) measurer.roles.you else Kk.Mute
    val inset = d(0.75f)
    drawPath(KkPathMemo.sheared(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset), color, style = kkStroke(d(1.5f)))
    drawKkText(measurer, "!", measurer.typography.condStyle(15f, lineHeightEm = 1f), bounds.center.x, bounds.center.y, color,
        align = KkAlign.CENTER, valign = KkVAlign.CENTER)
}

/** Where a tooltip sits relative to its anchor (`.info`, `.tip-r`, `.tip-l`, `.tip-b`). */
enum class KkTooltipPlacement { ABOVE, ABOVE_END, ABOVE_START, BELOW }

/** Tooltip body layout: body 500 14 px / 1.35, ink, wrapped to [widthDp] − 26 dp. */
fun measureKkTooltip(measurer: CanvasTextMeasurer, text: String, density: Float, widthDp: Float = 270f): TextLayoutResult =
    measureKkText(measurer, text, measurer.typography.bodyStyle(14f, FontWeight.Medium, lineHeightEm = 1.35f),
        maxWidth = (widthDp - 26f) * density, maxLines = 12)

/**
 * Tooltip rect for [anchor] and [placement] (12 dp gap), flipped below when there is no room
 * above and clamped into [within] with an 8 dp margin.
 */
fun kkTooltipRect(
    anchor: Rect,
    bodyHeight: Float,
    density: Float,
    within: Rect,
    placement: KkTooltipPlacement = KkTooltipPlacement.ABOVE,
    widthDp: Float = 270f,
): Rect {
    val w = widthDp * density
    val h = bodyHeight + 22f * density
    val gap = 12f * density
    val margin = 8f * density
    var left = when (placement) {
        KkTooltipPlacement.ABOVE, KkTooltipPlacement.BELOW -> anchor.center.x - w * 0.5f
        KkTooltipPlacement.ABOVE_END -> anchor.right + 6f * density - w
        KkTooltipPlacement.ABOVE_START -> anchor.left - 6f * density
    }
    var top = if (placement == KkTooltipPlacement.BELOW) anchor.bottom + gap else anchor.top - gap - h
    if (placement != KkTooltipPlacement.BELOW && top < within.top + margin) top = anchor.bottom + gap
    if (top + h > within.bottom - margin) top = max(within.top + margin, anchor.top - gap - h)
    left = left.coerceIn(within.left + margin, max(within.left + margin, within.right - margin - w))
    return Rect(left, top, left + w, top + h)
}

/**
 * Tooltip slip (`.info .tipbox`): bone slip with a 10 dp top-right cut and a 2 dp ink edge (so it
 * stays a separate layer over a bone slab under it), ink body text 14 px, 270 dp wide, placed around
 * [anchor] (see [kkTooltipRect]). Returns the slip rect. Allocates its rects per call: fine for
 * menus; in-run screens cache the placement and draw the slip.
 */
fun DrawScope.drawKkTooltip(
    measurer: CanvasTextMeasurer,
    anchor: Rect,
    text: String,
    placement: KkTooltipPlacement = KkTooltipPlacement.ABOVE,
    widthDp: Float = 270f,
    alpha: Float = 1f,
    within: Rect = Rect(0f, 0f, size.width, size.height),
): Rect {
    val body = measureKkTooltip(measurer, text, density, widthDp)
    val rect = kkTooltipRect(anchor, body.kkBoxHeight, density, within, placement, widthDp)
    val slip = KkPathMemo.chamferTopRight(rect, d(10f))
    drawPath(slip, Kk.Bone, alpha)
    drawPath(slip, Kk.Ink, alpha, style = kkStroke(d(KK_TOOLTIP_EDGE_DP)))
    drawKkText(body, rect.left + d(13f), rect.top + d(11f), Kk.Ink, alpha = alpha)
    return rect
}

/** Width of a tooltip slip's ink edge: half over the slip's rim, half outside it. */
private const val KK_TOOLTIP_EDGE_DP = 2f

/** Bone slip panel (`.tip`, stat-compare tooltip): top-right corner cut [cutDp]. */
fun DrawScope.drawKkSlip(bounds: Rect, cutDp: Float = 12f, color: Color = Kk.Bone) {
    drawPath(KkPathMemo.chamferTopRight(bounds, d(cutDp)), color)
}
