// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Tag plates (`.tag`, `.tag-volt`, `.tag-haz`, `.tag-bone`, `.tag-line`). */
enum class KkTagVariant { DEFAULT, YOU, THREAT, BONE, LINE }

private fun tagStyle(measurer: CanvasTextMeasurer, fontSize: Float) = measurer.typography.labelStyle(fontSize, trackingEm = 0.1f)

/** Size in px of a tag with [text] (height [heightDp], 10 dp padding). */
fun kkTagSize(measurer: CanvasTextMeasurer, text: String, density: Float, heightDp: Float = 22f, fontSize: Float = 13f): Size {
    val layout = measureKkText(measurer, text, tagStyle(measurer, fontSize), uppercase = true)
    return Size(layout.size.width + 20f * density, heightDp * density)
}

/**
 * Tag (`.tag`): small cond 800 label on a sheared plate. [background]/[foreground] (when
 * specified) override the variant (e.g. rarity tags: rarity face, ink text). Returns the plate rect.
 */
fun DrawScope.drawKkTag(
    measurer: CanvasTextMeasurer,
    text: String,
    topLeft: Offset,
    variant: KkTagVariant = KkTagVariant.DEFAULT,
    heightDp: Float = 22f,
    fontSize: Float = 13f,
    background: Color = Color.Unspecified,
    foreground: Color = Color.Unspecified,
    alpha: Float = 1f,
): Rect {
    val roles = measurer.roles
    val layout = measureKkText(measurer, text, tagStyle(measurer, fontSize), uppercase = true)
    val rect = KkRects.of(topLeft.x, topLeft.y, topLeft.x + layout.size.width + d(20f), topLeft.y + d(heightDp))
    val face = if (background.isSpecified) background else when (variant) {
        KkTagVariant.DEFAULT -> Kk.Ink3
        KkTagVariant.YOU -> roles.you
        KkTagVariant.THREAT -> roles.threat
        KkTagVariant.BONE -> Kk.Bone
        KkTagVariant.LINE -> Color.Transparent
    }
    val fg = if (foreground.isSpecified) foreground else when (variant) {
        KkTagVariant.DEFAULT, KkTagVariant.LINE -> Kk.Bone
        else -> Kk.Ink
    }
    val path = KkPathMemo.slab(rect, d(6f))
    if (face.alpha > 0f) drawPath(path, face, alpha)
    if (variant == KkTagVariant.LINE && background.isUnspecified) {
        drawPath(KkPathMemo.slab(rect.left + d(0.75f), rect.top + d(0.75f), rect.right - d(0.75f), rect.bottom - d(0.75f), d(6f)),
            Kk.Line2, alpha, kkStroke(d(1.5f)))
    }
    drawKkText(layout, rect.center.x, rect.center.y, fg, align = KkAlign.CENTER, valign = KkVAlign.CENTER, alpha = alpha)
    return rect
}

/** Stamps (`.stamp`, `.stamp-haz`, `.stamp-bone`, `.stamp-o` outline). */
enum class KkStampVariant { YOU, THREAT, BONE, OUTLINE }

private fun stampStyle(measurer: CanvasTextMeasurer, fontSize: Float) =
    measurer.typography.condStyle(fontSize, trackingEm = 0.06f, lineHeightEm = 1f)

/** Unrotated stamp size in px (padding 5/11/4 dp). */
fun kkStampSize(measurer: CanvasTextMeasurer, text: String, density: Float, fontSize: Float = 17f): Size {
    val layout = measureKkText(measurer, text, stampStyle(measurer, fontSize), uppercase = true)
    val k = fontSize / 17f
    return Size(layout.size.width + 22f * k * density, layout.kkBoxHeight + 9f * k * density)
}

/**
 * Stamp (`.stamp`): rotated cond 900 italic label with a hard ink offset shadow, for events
 * ("New!", "Overheat", "Max"). [slam] 0..1 slams it in (1 = settled). [color] (when specified)
 * overrides the variant face (or outline color). Returns the unrotated rect.
 */
fun DrawScope.drawKkStamp(
    measurer: CanvasTextMeasurer,
    text: String,
    topLeft: Offset,
    variant: KkStampVariant = KkStampVariant.YOU,
    fontSize: Float = 17f,
    rotationDeg: Float = -6f,
    slam: Float = 1f,
    color: Color = Color.Unspecified,
    shadow: Boolean = true,
): Rect {
    val roles = measurer.roles
    val layout = measureKkText(measurer, text, stampStyle(measurer, fontSize), uppercase = true)
    val k = fontSize / 17f
    val rect = KkRects.of(topLeft.x, topLeft.y, topLeft.x + layout.size.width + d(22f) * k, topLeft.y + layout.kkBoxHeight + d(9f) * k)
    val face = if (color.isSpecified) color else when (variant) {
        KkStampVariant.YOU, KkStampVariant.OUTLINE -> roles.you
        KkStampVariant.THREAT -> roles.threat
        KkStampVariant.BONE -> Kk.Bone
    }
    val alpha = if (slam >= 1f) 1f else KkSlam.alpha(slam)
    withKkSlam(slam.coerceIn(0f, 1f), rect.center, rotationDeg) {
        if (variant == KkStampVariant.OUTLINE) {
            val inset = d(1f)
            drawRect(face, Offset(rect.left + inset, rect.top + inset), Size(rect.width - inset * 2f, rect.height - inset * 2f), alpha, kkStroke(d(2f)))
            drawKkText(layout, rect.center.x, rect.center.y + d(0.5f) * k, face, KkAlign.CENTER, KkVAlign.CENTER, alpha)
        } else {
            if (shadow) drawRect(Kk.Ink, Offset(rect.left + d(3f) * k, rect.top + d(3f) * k), rect.size, alpha)
            drawRect(face, rect.topLeft, rect.size, alpha)
            if (variant == KkStampVariant.THREAT && roles.hatchThreats) {
                // MONO: the hatch stays a rim so the label keeps a solid face.
                drawKkThreatHatch(rect, roles, Kk.Ink)
                val rim = d(3f) * k
                drawRect(face, Offset(rect.left + rim, rect.top + rim), Size(rect.width - rim * 2f, rect.height - rim * 2f), alpha)
            }
            drawKkText(layout, rect.center.x, rect.center.y + d(0.5f) * k, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER, alpha)
        }
    }
    return rect
}

/** Matter gem (`.gem`): a vertical hexagon [sizeDp] tall. Allocation-free. */
fun DrawScope.drawKkGem(center: Offset, color: Color, sizeDp: Float = 14f) {
    drawPath(KkPathMemo.gem(center.x, center.y, d(sizeDp)), color)
}

/**
 * Header chip (`.chip`, 34 dp): gem (default) or [icon], a cond 900 italic tabular [value] and an
 * optional small mono [label]. Returns the chip rect.
 */
fun DrawScope.drawKkChip(
    measurer: CanvasTextMeasurer,
    topLeft: Offset,
    value: String,
    label: String? = null,
    icon: KkIcon? = null,
    iconColor: Color = if (icon == null) measurer.roles.you else Kk.Bone,
    heightDp: Float = 34f,
): Rect {
    val valueLayout = measureKkText(measurer, value, measurer.typography.condStyle(21f, trackingEm = 0.02f, tabular = true, lineHeightEm = 1f), uppercase = true)
    val labelLayout = label?.let { measureKkText(measurer, it, measurer.typography.monoStyle(9f, trackingEm = 0.06f), uppercase = true) }
    val lead = if (icon == null) d(14f) else d(16f)
    val gap = d(8f)
    val width = d(10f) + lead + gap + valueLayout.size.width + (if (labelLayout != null) gap + labelLayout.size.width else 0f) + d(14f)
    val rect = KkRects.of(topLeft.x, topLeft.y, topLeft.x + width, topLeft.y + d(heightDp))
    drawPath(KkPathMemo.slab(rect, d(8f)), Kk.Ink2)
    var x = rect.left + d(10f)
    val cy = rect.center.y
    if (icon == null) drawKkGem(Offset(x + lead * 0.5f, cy), iconColor) else drawKkIcon(icon, Offset(x + lead * 0.5f, cy), lead, iconColor)
    x += lead + gap
    drawKkText(valueLayout, x, cy, Kk.Bone, valign = KkVAlign.CENTER)
    x += valueLayout.size.width
    if (labelLayout != null) drawKkText(labelLayout, x + gap, cy, Kk.Mute, valign = KkVAlign.CENTER)
    return rect
}

/** Toast tone: build-update feed row or threat warning row. */
enum class KkToastTone { NORMAL, WARNING }

/**
 * Toast (`.toast`, 44 dp): ink-2 sheared row with an optional leading gem/[icon], cond 900 italic
 * [title] (20 px) and optional mono [detail]; [KkToastTone.WARNING] uses the threat face with ink
 * text. [alpha] fades it (drift out over [KkTime.Drift]). Returns the row rect.
 */
fun DrawScope.drawKkToast(
    measurer: CanvasTextMeasurer,
    topLeft: Offset,
    title: String,
    detail: String? = null,
    icon: KkIcon? = null,
    gem: Boolean = false,
    iconColor: Color = measurer.roles.you,
    tone: KkToastTone = KkToastTone.NORMAL,
    heightDp: Float = 44f,
    alpha: Float = 1f,
): Rect {
    val roles = measurer.roles
    val titleLayout = measureKkText(measurer, title, measurer.typography.condStyle(20f, lineHeightEm = 1f), uppercase = true)
    val detailLayout = detail?.let { measureKkText(measurer, it, measurer.typography.monoStyle(), uppercase = true) }
    val lead = when {
        icon != null -> d(16f)
        gem -> d(14f)
        else -> 0f
    }
    val gap = d(12f)
    val width = d(12f) + (if (lead > 0f) lead + gap else 0f) + titleLayout.size.width +
        (if (detailLayout != null) gap + detailLayout.size.width else 0f) + d(18f)
    val rect = KkRects.of(topLeft.x, topLeft.y, topLeft.x + width, topLeft.y + d(heightDp))
    val warning = tone == KkToastTone.WARNING
    val path = KkPathMemo.slab(rect, d(10f))
    drawPath(path, if (warning) roles.threat else Kk.Ink2, alpha)
    if (warning && roles.hatchThreats) {
        // MONO: the hatch stays a rim so the title keeps a solid face.
        drawKkThreatHatch(path, roles, Kk.Ink)
        val rim = d(3f)
        drawPath(KkPathMemo.slab(rect.left + rim, rect.top + rim, rect.right - rim, rect.bottom - rim, d(9f)), roles.threat, alpha)
    }
    var x = rect.left + d(12f)
    val cy = rect.center.y
    if (icon != null) drawKkIcon(icon, Offset(x + lead * 0.5f, cy), lead, iconColor, alpha)
    else if (gem) drawKkGem(Offset(x + lead * 0.5f, cy), iconColor.copy(alpha = iconColor.alpha * alpha))
    if (lead > 0f) x += lead + gap
    val fg = if (warning) Kk.Ink else Kk.Bone
    drawKkText(titleLayout, x, cy, fg, valign = KkVAlign.CENTER, alpha = alpha)
    x += titleLayout.size.width
    if (detailLayout != null) drawKkText(detailLayout, x + gap, cy, if (warning) Kk.Ink else Kk.Mute, valign = KkVAlign.CENTER, alpha = alpha)
    return rect
}

/**
 * Thin sheared separator (`.sep`): 2 dp × 0.85 em bar at 40 % opacity for text of
 * [fontSizePx], centered at ([x], [centerY]). Use sparingly. Allocation-free.
 */
fun DrawScope.drawKkSeparator(x: Float, centerY: Float, fontSizePx: Float, color: Color) {
    val h = fontSizePx * 0.85f
    drawPath(KkPathMemo.sheared(x - d(1f), centerY - h * 0.5f, x + d(1f), centerY + h * 0.5f), color, alpha = 0.4f)
}
