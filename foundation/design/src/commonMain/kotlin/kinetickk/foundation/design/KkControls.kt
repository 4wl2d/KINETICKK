// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Controls from kk.css (.btn, .ibtn, .stepper, .mi, .tile, .lrow, .tab, .segctl, .tgl, .sld).
// Geometry parameters are in px (Rect/Offset) and the CSS numbers are dp (scaled by density).
// These helpers measure text through CanvasTextMeasurer (cached, but allocating a cache key);
// they are meant for menus and overlays, not for per-frame HUD text.

/** Button face variants (`.btn`, `.btn-bone`, `.btn-ghost`, `.btn-haz`). */
enum class KkButtonVariant { PRIMARY, BONE, GHOST, HAZARD }

/**
 * Button sizes: height, horizontal padding, cond font size, horizontal cut, hover echo offset and
 * icon size, all in dp (`.btn-xs` 30, `.btn-sm` 38, `.btn` 52, `.btn-lg` 72; touch min 44).
 */
enum class KkButtonSize(
    val heightDp: Float,
    val paddingDp: Float,
    val fontSp: Float,
    val cutDp: Float,
    val echoDp: Float,
    val iconDp: Float,
) {
    XS(30f, 14f, 16f, 8f, 6f, 12f),
    SM(38f, 20f, 19f, 10f, 6f, 14f),
    MD(52f, 30f, 26f, 14f, 7f, 16f),
    LG(72f, 42f, 40f, 18f, 10f, 22f),
}

private const val BUTTON_ICON_GAP_DP = 12f

/** Natural button width in px for [label] (uppercase cond 900 italic) plus padding and [icon]. */
fun kkButtonWidth(
    measurer: CanvasTextMeasurer,
    label: String,
    size: KkButtonSize,
    density: Float,
    icon: KkIcon? = null,
): Float {
    val text = if (label.isEmpty()) 0f else measureKkText(measurer, label, buttonStyle(measurer, size), uppercase = true).size.width.toFloat()
    val iconWidth = if (icon == null) 0f else size.iconDp * density + if (label.isEmpty()) 0f else BUTTON_ICON_GAP_DP * density
    return size.paddingDp * density * 2f + text + iconWidth
}

private fun buttonStyle(measurer: CanvasTextMeasurer, size: KkButtonSize) =
    measurer.typography.condStyle(size.fontSp, trackingEm = 0.02f, lineHeightEm = 1f)

/**
 * Draws a slab button inside [bounds] (the resting face rect).
 *
 * - [hover] 0..1 is the caller-animated hover/focus emphasis ([KkEase.Pull], ~200 ms): the echo
 *   slab slides out to [KkButtonSize.echoDp] and the button shifts 5 dp right. Idle shows no echo.
 * - [focused] draws the 2 dp bone focus outline 4 dp outside the face.
 * - [pressed] is the 1-frame bone flash: bone face, echo collapsed, 3 dp press offset.
 * - [enabled] false: ink-3 face, mute-2 text, no echo. [locked]: ink-3 face + hatch, mute text,
 *   lock icon. [armed]: threat face pulsing (second press confirms; the caller owns the logic).
 * - [holdProgress] 0..1 fills a threat slab under the label (hold-to-confirm).
 * - [time] in seconds drives the armed pulse.
 *
 * Role colors come from `measurer.roles`. Allocation-free apart from text measurement.
 */
fun DrawScope.drawKkButton(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    label: String,
    variant: KkButtonVariant = KkButtonVariant.PRIMARY,
    size: KkButtonSize = KkButtonSize.MD,
    hover: Float = 0f,
    focused: Boolean = false,
    pressed: Boolean = false,
    enabled: Boolean = true,
    locked: Boolean = false,
    armed: Boolean = false,
    icon: KkIcon? = if (locked) KkIcon.SYSTEM_LOCKED else null,
    holdProgress: Float = 0f,
    time: Float = 0f,
) {
    val roles = measurer.roles
    val interactive = enabled && !locked
    val emphasis = if (interactive && !pressed) hover.coerceIn(0f, 1.2f) else 0f
    var face: Color
    var fg: Color
    val echo: Color
    when (variant) {
        KkButtonVariant.PRIMARY -> { face = roles.you; fg = Kk.Ink; echo = Kk.Bone }
        KkButtonVariant.BONE -> { face = Kk.Bone; fg = Kk.Ink; echo = roles.you }
        KkButtonVariant.GHOST -> { face = Kk.Ink3; fg = Kk.Bone; echo = roles.you }
        KkButtonVariant.HAZARD -> { face = roles.threat; fg = Kk.Ink; echo = Kk.Bone }
    }
    if (armed) { face = roles.threat; fg = Kk.Ink }
    if (variant == KkButtonVariant.GHOST && emphasis > 0.5f) { face = Kk.Bone; fg = Kk.Ink }
    if (pressed && interactive) { face = Kk.Bone; fg = Kk.Ink }
    if (!enabled) { face = Kk.Ink3; fg = Kk.Mute2 } else if (locked) { face = Kk.Ink3; fg = Kk.Mute }

    val cut = size.cutDp * density
    val shiftX = d(5f) * emphasis + if (pressed && interactive) d(3f) else 0f
    val shiftY = if (pressed && interactive) d(3f) else 0f
    val pressScale = if (pressed && interactive) 0.97f else 1f
    translate(shiftX, shiftY) {
        scale(pressScale, pressScale, bounds.center) {
            val echoOffset = size.echoDp * density * emphasis
            if (echoOffset > 0.25f) {
                val path = KkPathMemo.slab(
                    bounds.left + echoOffset, bounds.top + echoOffset,
                    bounds.right + echoOffset, bounds.bottom + echoOffset, cut,
                )
                drawPath(path, echo)
            }
            val facePath = KkPathMemo.slab(bounds, cut)
            drawPath(facePath, face)
            if (armed && interactive) drawPath(facePath, Color.White, alpha = 0.2f * kkPulse(time, 0.9f))
            if (locked) drawKkHatch(facePath)
            if ((variant == KkButtonVariant.HAZARD || armed) && interactive && roles.hatchThreats) {
                // MONO: the hatch stays a rim so the label keeps a solid face.
                drawKkThreatHatch(facePath, roles, Kk.Ink)
                val rim = d(4f)
                drawPath(KkPathMemo.slab(bounds.left + rim, bounds.top + rim, bounds.right - rim, bounds.bottom - rim, cut), face)
            }
            if (holdProgress > 0f && interactive) {
                val holdLeft = bounds.left + d(10f)
                val holdRight = holdLeft + (bounds.width - d(20f)) * holdProgress.coerceIn(0f, 1f)
                drawPath(KkPathMemo.slab(holdLeft, bounds.top, holdRight, bounds.bottom, d(10f)), roles.threat)
            }
            drawButtonContent(measurer, bounds, label, size, icon, fg)
            if (focused && enabled) {
                val inset = d(4f) + d(1f)
                drawRect(
                    Kk.Bone,
                    Offset(bounds.left - inset, bounds.top - inset),
                    Size(bounds.width + inset * 2f, bounds.height + inset * 2f),
                    style = kkStroke(d(2f)),
                )
            }
        }
    }
}

private fun DrawScope.drawButtonContent(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    label: String,
    size: KkButtonSize,
    icon: KkIcon?,
    color: Color,
) {
    val layout = if (label.isEmpty()) null else measureKkText(measurer, label, buttonStyle(measurer, size), uppercase = true)
    val iconSize = size.iconDp * density
    val gap = if (icon != null && layout != null) BUTTON_ICON_GAP_DP * density else 0f
    val textWidth = if (layout != null) layout.size.width.toFloat() else 0f
    val contentWidth = textWidth + (if (icon != null) iconSize else 0f) + gap
    var x = bounds.center.x - contentWidth * 0.5f
    if (icon != null) {
        drawKkIcon(icon, Offset(x + iconSize * 0.5f, bounds.center.y), iconSize, color)
        x += iconSize + gap
    }
    if (layout != null) drawKkText(layout, x, bounds.center.y, color, valign = KkVAlign.CENTER)
}

/** Square sheared icon button (`.ibtn`, 44 dp): ink-3 face, bone on hover with ink icon. */
fun DrawScope.drawKkIconButton(
    bounds: Rect,
    icon: KkIcon,
    hovered: Boolean = false,
    focused: Boolean = false,
    enabled: Boolean = true,
    iconSizeDp: Float = 20f,
) {
    val face = if (hovered && enabled) Kk.Bone else Kk.Ink3
    val fg = when {
        !enabled -> Kk.Mute2
        hovered -> Kk.Ink
        else -> Kk.Bone
    }
    drawPath(KkPathMemo.slab(bounds, d(8f)), face)
    drawKkIcon(icon, bounds.center, d(iconSizeDp), fg)
    if (focused && enabled) drawFocusOutline(bounds)
}

/** Stepper button (`.stepper button`, 34 dp): ink-3 square with a − or + mark. */
fun DrawScope.drawKkStepButton(bounds: Rect, plus: Boolean, hovered: Boolean = false, enabled: Boolean = true) {
    val face = if (hovered && enabled) Kk.Bone else Kk.Ink3
    val fg = when {
        !enabled -> Kk.Mute2
        hovered -> Kk.Ink
        else -> Kk.Bone
    }
    drawPath(KkPathMemo.slab(bounds, d(8f)), face)
    val half = d(5f)
    val c = bounds.center
    drawLine(fg, Offset(c.x - half, c.y), Offset(c.x + half, c.y), d(2f))
    if (plus) drawLine(fg, Offset(c.x, c.y - half), Offset(c.x, c.y + half), d(2f))
}

private fun DrawScope.drawFocusOutline(bounds: Rect) {
    val inset = d(5f)
    drawRect(
        Kk.Bone,
        Offset(bounds.left - inset, bounds.top - inset),
        Size(bounds.width + inset * 2f, bounds.height + inset * 2f),
        style = kkStroke(d(2f)),
    )
}

/**
 * Big stacked menu item (`.mi`, cond 900 italic, 64 px on desktop Home; pass [fontSize] 46 for
 * pause). [left] is the item's left edge and [centerY] its vertical center.
 *
 * [selection] 0..1 (animate with [KkEase.Pull] over [KkTime.MenuSelect] ms; overshoot allowed)
 * grows a bone slab from the screen edge ([edgeRight]) to the item, slides the `you` echo in to
 * (−14, 10), shows three trailing speed lines, turns the text ink and moves the item −26 dp with a
 * −2° tilt. [dim] (secondary item) uses mute-2; [locked] adds a lock icon. Optional [stamp]
 * (e.g. an unlockable event) rides after the label; optional mono [sub] value shows only when
 * selected. No index numbers. Returns the unrotated item bounds for hit testing.
 */
fun DrawScope.drawKkMenuItem(
    measurer: CanvasTextMeasurer,
    left: Float,
    centerY: Float,
    label: String,
    selection: Float = 0f,
    time: Float = 0f,
    fontSize: Float = 64f,
    dim: Boolean = false,
    locked: Boolean = false,
    stamp: String? = null,
    sub: String? = null,
    edgeRight: Float = size.width,
): Rect {
    val roles = measurer.roles
    val k = fontSize / 64f
    fun u(value: Float) = value * k * density
    val s = selection.coerceAtLeast(0f)
    val textLayout = measureKkText(measurer, label, measurer.typography.condStyle(fontSize, lineHeightEm = 1f), uppercase = true)
    val subLayout = sub?.let {
        measureKkText(measurer, it, measurer.typography.monoStyle(11f * k.coerceAtLeast(0.8f), trackingEm = 0.06f), uppercase = true)
    }
    val stampSize = 14f * k.coerceAtLeast(0.8f)
    val stampWidth = if (stamp != null) kkStampSize(measurer, stamp, density, stampSize).width else 0f
    val lockSize = u(24f)
    var trailing = 0f
    if (subLayout != null) trailing += u(18f) + subLayout.size.width
    if (stamp != null) trailing += u(18f) + stampWidth
    if (locked) trailing += u(18f) + lockSize
    val width = u(22f) + textLayout.size.width + trailing + u(44f)
    val top = centerY - u(33f)
    val bounds = Rect(left, top, left + width, top + u(66f))

    val base = when {
        dim || locked -> Kk.Mute2
        else -> Kk.Bone
    }
    val textColor = lerp(base, Kk.Ink, (s * 1.6f).coerceIn(0f, 1f))
    translate(-u(26f) * s, 0f) {
        rotate(-2f * s, bounds.center) {
            if (s > 0.001f) {
                val slabRight = max(bounds.right + u(400f), edgeRight + d(80f))
                val slabTop = bounds.top - u(6f)
                val slabBottom = bounds.bottom + u(6f)
                val scaleX = s.coerceAtMost(1.15f)
                // Volt echo: translate (-40,22) scaleX 0 -> (-14,10) scaleX 1, right origin.
                val echoDx = kkLerp(-40f, -14f, s.coerceAtMost(1f))
                val echoDy = kkLerp(22f, 10f, s.coerceAtMost(1f))
                val echoLeft = slabRight - (slabRight - bounds.left) * scaleX
                drawPath(
                    KkPathMemo.slab(echoLeft + u(echoDx), slabTop + u(echoDy), slabRight + u(echoDx), slabBottom + u(echoDy), u(22f), KkSlabKind.LEFT),
                    roles.you,
                )
                val slabLeft = slabRight - (slabRight - bounds.left) * scaleX
                drawPath(KkPathMemo.slab(slabLeft, slabTop, slabRight, slabBottom, u(22f), KkSlabKind.LEFT), Kk.Bone)
                drawMenuTrail(bounds, time, s.coerceAtMost(1f), roles.you, k)
            }
            var x = bounds.left + u(22f)
            drawKkText(textLayout, x, centerY, textColor, valign = KkVAlign.CENTER)
            x += textLayout.size.width
            if (subLayout != null) {
                x += u(18f)
                val subAlpha = s.coerceIn(0f, 1f)
                if (subAlpha > 0f) {
                    drawKkText(subLayout, x, centerY + u(12f), if (s > 0.5f) Kk.Ink else Kk.Mute, valign = KkVAlign.CENTER, alpha = subAlpha)
                }
                x += subLayout.size.width
            }
            if (stamp != null) {
                x += u(18f)
                drawKkStamp(measurer, stamp, Offset(x + u(2f), centerY - u(4f) - kkStampSize(measurer, stamp, density, stampSize).height * 0.5f), fontSize = stampSize)
                x += stampWidth
            }
            if (locked) {
                x += u(18f)
                drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(x + lockSize * 0.5f, centerY), lockSize, Kk.Mute)
            }
        }
    }
    return bounds
}

private val TrailOffsets = floatArrayOf(2f, 14f, 26f)
private val TrailWidths = floatArrayOf(120f, 80f, 100f)
private val TrailDelays = floatArrayOf(0f, 0.15f, 0.3f)

private fun DrawScope.drawMenuTrail(bounds: Rect, time: Float, alpha: Float, you: Color, k: Float) {
    fun u(value: Float) = value * k * density
    val right = bounds.left - u(20f)
    val top = bounds.center.y - u(15f)
    for (index in 0..2) {
        val offsetY = TrailOffsets[index]
        val width = TrailWidths[index]
        val delay = TrailDelays[index]
        val p = kkLoop(time, 0.9f, delay)
        val scaleX = kkLerp(0.15f, 1f, KkEase.Out.transform(p))
        val opacity = when {
            p < 0.18f -> KkEase.Out.transform(p / 0.18f)
            p < 0.72f -> kkLerp(1f, 0.9f, KkEase.Out.transform((p - 0.18f) / 0.54f))
            else -> kkLerp(0.9f, 0f, KkEase.Out.transform((p - 0.72f) / 0.28f))
        }
        val w = u(width) * scaleX
        drawRect(
            if (index == 2) Kk.Bone else you,
            Offset(right - w, top + u(offsetY)),
            Size(w, u(3f)),
            alpha = (opacity * alpha).coerceIn(0f, 1f),
        )
    }
}

/**
 * Selector tile (`.tile`): ink-2 face with [cutDp] cut; hover ink-3; [selected] 0..1 lifts it
 * 8 dp with a bone face, `you` echo (7, 7) and ink content; [locked] hatches the face and mutes
 * the content. Draws the optional [icon] (32 dp) above the optional label (label 13 px).
 */
fun DrawScope.drawKkTile(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    label: String? = null,
    icon: KkIcon? = null,
    selected: Float = 0f,
    hovered: Boolean = false,
    locked: Boolean = false,
    cutDp: Float = 12f,
    iconSizeDp: Float = 32f,
    labelSize: Float = 13f,
    focused: Boolean = false,
) {
    val s = selected.coerceIn(0f, 1.2f)
    val on = s > 0.5f
    val cut = d(cutDp)
    val face = when {
        locked && on -> Kk.Ink4
        on -> Kk.Bone
        hovered -> Kk.Ink3
        else -> Kk.Ink2
    }
    val fg = when {
        locked && on -> Kk.Bone
        locked -> Kk.Mute
        on -> Kk.Ink
        else -> Kk.Bone
    }
    translate(0f, -d(8f) * s) {
        if (s > 0.01f) {
            val e = d(7f) * s
            drawPath(KkPathMemo.slab(bounds.left + e, bounds.top + e, bounds.right + e, bounds.bottom + e, cut), measurer.roles.you, alpha = s.coerceAtMost(1f))
        }
        val path = KkPathMemo.slab(bounds, cut)
        drawPath(path, face)
        if (locked) drawKkHatch(path, Color(0x12F1F0E8))
        val labelLayout = label?.let { measureKkText(measurer, it, measurer.typography.labelStyle(labelSize), uppercase = true) }
        val iconSize = if (icon != null) d(iconSizeDp) else 0f
        val gap = if (icon != null && labelLayout != null) d(6f) else 0f
        val total = iconSize + gap + (if (labelLayout != null) labelLayout.kkBoxHeight else 0f)
        var y = bounds.center.y - total * 0.5f
        if (icon != null) {
            drawKkIcon(icon, Offset(bounds.center.x, y + iconSize * 0.5f), iconSize, fg)
            y += iconSize + gap
        }
        if (labelLayout != null) drawKkText(labelLayout, bounds.center.x, y, fg, align = KkAlign.CENTER)
        if (focused) drawFocusOutline(bounds)
    }
}

/** Horizontal shift applied to a selected list row's content (`.lrow.on`, −10 dp). */
const val KK_LIST_ROW_SELECTED_SHIFT_DP = -10f

/**
 * List row background (`.lrow`, 48 dp): transparent, ink-3 on hover, bone when [selected]
 * (0..1, shifted [KK_LIST_ROW_SELECTED_SHIFT_DP]) with an optional `you` [echo]. Returns the
 * content color (ink when selected, mute when locked, else bone). Content drawn by the caller
 * should be shifted by `KK_LIST_ROW_SELECTED_SHIFT_DP * selected` dp.
 */
fun DrawScope.drawKkListRowBackground(
    bounds: Rect,
    roles: KkRolePalette,
    selected: Float = 0f,
    hovered: Boolean = false,
    locked: Boolean = false,
    echo: Boolean = false,
): Color {
    val s = selected.coerceIn(0f, 1.2f)
    val cut = d(9f)
    val shift = d(KK_LIST_ROW_SELECTED_SHIFT_DP) * s
    translate(shift, 0f) {
        if (echo && s > 0.01f) {
            val e = d(6f) * s
            drawPath(KkPathMemo.slab(bounds.left + e, bounds.top + e, bounds.right + e, bounds.bottom + e, cut), roles.you)
        }
        when {
            s > 0.5f -> drawPath(KkPathMemo.slab(bounds, cut), Kk.Bone)
            hovered -> drawPath(KkPathMemo.slab(bounds, cut), Kk.Ink3)
        }
    }
    return when {
        s > 0.5f -> Kk.Ink
        locked -> Kk.Mute
        else -> Kk.Bone
    }
}

/** Secondary text color on a list row (mute; `#3D3D37` on the bone selected face). */
fun kkListRowSecondary(selected: Float): Color = if (selected > 0.5f) Color(0xFF3D3D37) else Kk.Mute

/**
 * Full list row: background plus a cond 900 italic [title] (22 px) at the left and an optional
 * mono [trailing] value at the right, 18 dp padding.
 */
fun DrawScope.drawKkListRow(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    title: String,
    trailing: String? = null,
    selected: Float = 0f,
    hovered: Boolean = false,
    locked: Boolean = false,
    echo: Boolean = false,
    titleSize: Float = 22f,
) {
    val fg = drawKkListRowBackground(bounds, measurer.roles, selected, hovered, locked, echo)
    val shift = d(KK_LIST_ROW_SELECTED_SHIFT_DP) * selected.coerceIn(0f, 1.2f)
    val trailingLayout = trailing?.let { measureKkText(measurer, it, measurer.typography.monoStyle(), uppercase = true) }
    val titleMax = bounds.width - d(36f) - (if (trailingLayout != null) trailingLayout.size.width + d(14f) else 0f)
    drawKkText(measurer, title, measurer.typography.condStyle(titleSize), bounds.left + d(18f) + shift, bounds.center.y, fg,
        valign = KkVAlign.CENTER, uppercase = true, maxWidth = titleMax)
    if (trailingLayout != null) {
        drawKkText(trailingLayout, bounds.right - d(18f) + shift, bounds.center.y, kkListRowSecondary(selected), align = KkAlign.END, valign = KkVAlign.CENTER)
    }
}

/** Natural tab width in px (`.tab`: 20 dp padding, cond 900 italic 21 px, +3 % tracking). */
fun kkTabWidth(measurer: CanvasTextMeasurer, label: String, density: Float): Float =
    measureKkText(measurer, label, measurer.typography.condStyle(21f, trackingEm = 0.03f), uppercase = true).size.width + 40f * density

/**
 * Tab (`.tab`, 40 dp): mute label; hover bone label on ink-3; [selected] bone slab, ink label and
 * a sheared `you` underline 8 dp below.
 */
fun DrawScope.drawKkTab(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    label: String,
    selected: Boolean,
    hovered: Boolean = false,
    focused: Boolean = false,
) {
    val cut = d(10f)
    when {
        selected -> drawPath(KkPathMemo.slab(bounds, cut), Kk.Bone)
        hovered -> drawPath(KkPathMemo.slab(bounds, cut), Kk.Ink3)
    }
    if (selected) {
        drawPath(KkPathMemo.sheared(bounds.left + d(12f), bounds.bottom + d(5f), bounds.right - d(12f), bounds.bottom + d(8f)), measurer.roles.you)
    }
    val color = when {
        selected -> Kk.Ink
        hovered -> Kk.Bone
        else -> Kk.Mute
    }
    drawKkText(measurer, label, measurer.typography.condStyle(21f, trackingEm = 0.03f), bounds.center.x, bounds.center.y, color,
        align = KkAlign.CENTER, valign = KkVAlign.CENTER, uppercase = true)
    if (focused) drawFocusOutline(bounds)
}

/** Natural segmented-cell width in px (`.segctl button`: min 64 dp, 14 dp padding). */
fun kkSegmentWidth(measurer: CanvasTextMeasurer, label: String, density: Float): Float = max(
    64f * density,
    measureKkText(measurer, label, measurer.typography.labelStyle(16f, trackingEm = 0.07f), uppercase = true).size.width + 28f * density,
)

/** Segmented control cell (`.segctl`, 34 dp): ink-3 sheared cell, `you` face when [selected]. */
fun DrawScope.drawKkSegment(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    label: String,
    selected: Boolean,
    hovered: Boolean = false,
    focused: Boolean = false,
) {
    drawPath(KkPathMemo.slab(bounds, d(7f)), if (selected) measurer.roles.you else Kk.Ink3)
    val color = when {
        selected -> Kk.Ink
        hovered -> Kk.Bone
        else -> Kk.Mute
    }
    drawKkText(measurer, label, measurer.typography.labelStyle(16f, trackingEm = 0.07f), bounds.center.x, bounds.center.y, color,
        align = KkAlign.CENTER, valign = KkVAlign.CENTER, uppercase = true)
    if (focused) drawFocusOutline(bounds)
}

/**
 * Toggle (`.tgl`, 62 × 28 dp): sheared ink-4 track with a sheared thumb; [on] 0..1 (animate with
 * Pull) slides the thumb 26 dp and turns it `you` on a `you`-ink track. Optional short state
 * labels ([onLabel]/[offLabel], mono 700 9 px) sit on the free side, at least 2 dp clear of the
 * thumb: a word longer than the design's (e.g. Russian "Выкл") moves toward the track edge and,
 * if it still does not fit, shrinks to the free lane.
 */
fun DrawScope.drawKkToggle(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    on: Float,
    onLabel: String? = null,
    offLabel: String? = null,
    focused: Boolean = false,
) {
    val roles = measurer.roles
    val t = on.coerceIn(0f, 1.2f)
    val isOn = t > 0.5f
    val track = if (isOn) kkMix(Kk.Ink, roles.you, 0.07f) else Kk.Ink4
    drawPath(KkPathMemo.slab(bounds, d(7f)), track)
    val thumbLeft = bounds.left + d(TOGGLE_THUMB_INSET_DP) + d(TOGGLE_THUMB_TRAVEL_DP) * t
    val thumbTop = bounds.top + d(4f)
    drawPath(KkPathMemo.slab(thumbLeft, thumbTop, thumbLeft + d(TOGGLE_THUMB_WIDTH_DP), thumbTop + d(20f), d(5f)),
        lerp(Kk.Mute, roles.you, t.coerceIn(0f, 1f)))
    val text = if (isOn) onLabel else offLabel
    if (text != null) {
        // Free lane beside the resting thumb (its sheared slab reaches its full width at the top
        // and its left edge at the bottom), inside the track's sheared ends.
        val gap = d(TOGGLE_LABEL_GAP_DP)
        val laneStart = if (isOn) bounds.left + d(7f) else bounds.left + d(TOGGLE_THUMB_INSET_DP + TOGGLE_THUMB_WIDTH_DP) + gap
        val laneEnd = if (isOn) bounds.left + d(TOGGLE_THUMB_INSET_DP + TOGGLE_THUMB_TRAVEL_DP) - gap else bounds.right - d(7f)
        val layout = kkToggleLabelLayout(measurer, text, laneEnd - laneStart)
        val width = layout.size.width
        if (isOn) {
            val left = min(bounds.left + d(9f), laneEnd - width).coerceAtLeast(laneStart)
            drawKkText(layout, left, bounds.center.y, roles.you, valign = KkVAlign.CENTER)
        } else {
            val right = max(bounds.right - d(9f), laneStart + width).coerceAtMost(laneEnd)
            drawKkText(layout, right, bounds.center.y, Kk.Mute2, align = KkAlign.END, valign = KkVAlign.CENTER)
        }
    }
    if (focused) drawFocusOutline(bounds)
}

private const val TOGGLE_THUMB_INSET_DP = 6f
private const val TOGGLE_THUMB_WIDTH_DP = 24f
private const val TOGGLE_THUMB_TRAVEL_DP = 26f
private const val TOGGLE_LABEL_GAP_DP = 2f

/** The toggle state word in mono 700 9 px, shrunk (never ellipsized) to [lane] px when it is wider. */
private fun kkToggleLabelLayout(measurer: CanvasTextMeasurer, text: String, lane: Float): TextLayoutResult {
    val natural = measureKkText(measurer, text, toggleLabelStyle(measurer, TOGGLE_LABEL_SP), uppercase = true)
    if (natural.size.width <= lane) return natural
    // Width scales with the font size; step down in 0.1 sp so the shrunk style stays memoized.
    var size = floor(TOGGLE_LABEL_SP * lane / natural.size.width * 10f) / 10f
    var layout = measureKkText(measurer, text, toggleLabelStyle(measurer, size), uppercase = true)
    while (layout.size.width > lane && size > TOGGLE_LABEL_MIN_SP) {
        size -= 0.1f
        layout = measureKkText(measurer, text, toggleLabelStyle(measurer, size), uppercase = true)
    }
    return layout
}

private const val TOGGLE_LABEL_SP = 9f
private const val TOGGLE_LABEL_MIN_SP = 5f

private fun toggleLabelStyle(measurer: CanvasTextMeasurer, size: Float) =
    measurer.typography.monoStyle(size, weight = FontWeight.Bold, trackingEm = 0.06f)

/**
 * Slider (`.sld`): sheared 8 dp ink-4 track across [bounds], `you` fill to [value] (0..1) and a
 * sheared 14 × 26 dp bone thumb with a 3 dp ink ring. Pair with [drawKkStepButton] for −/+.
 * Allocation-free.
 */
fun DrawScope.drawKkSlider(bounds: Rect, value: Float, roles: KkRolePalette, focused: Boolean = false) {
    val v = value.coerceIn(0f, 1f)
    val cy = bounds.center.y
    val trackTop = cy - d(4f)
    val trackBottom = cy + d(4f)
    drawPath(KkPathMemo.sheared(bounds.left, trackTop, bounds.right, trackBottom), Kk.Ink4)
    val fillRight = bounds.left + bounds.width * v
    if (fillRight > bounds.left) drawPath(KkPathMemo.sheared(bounds.left, trackTop, fillRight, trackBottom), roles.you)
    val ring = d(3f)
    val thumbLeft = fillRight - d(7f)
    drawPath(KkPathMemo.sheared(thumbLeft - ring, cy - d(13f) - ring, thumbLeft + d(14f) + ring, cy + d(13f) + ring), Kk.Ink)
    drawPath(KkPathMemo.sheared(thumbLeft, cy - d(13f), thumbLeft + d(14f), cy + d(13f)), Kk.Bone)
    if (focused) drawFocusOutline(bounds)
}

/** Text field background (Codex search): ink-2 with a 2 dp bottom rule, `you` when focused. */
fun DrawScope.drawKkField(bounds: Rect, focused: Boolean, roles: KkRolePalette) {
    drawRect(Kk.Ink2, bounds.topLeft, bounds.size)
    drawRect(if (focused) roles.you else Kk.Line2, Offset(bounds.left, bounds.bottom - d(2f)), Size(bounds.width, d(2f)))
}

/**
 * Draws [block] sheared by [degrees] (default −12°) around the line y = [pivotY]: plain rects
 * drawn inside become sheared cells without building paths. Allocation-free (inline).
 */
inline fun DrawScope.withKkShear(pivotY: Float, degrees: Float = KkShape.Shear, block: DrawScope.() -> Unit) {
    withTransform({ kkShear(pivotY, degrees) }) { block() }
}
