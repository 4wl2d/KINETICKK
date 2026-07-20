// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kinetickk.core.content.ItemRarity
import kinetickk.core.content.WeaponId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAU = 6.2831855f

fun DrawScope.drawOverlayFrame(bounds: Rect, accent: Color) {
    drawRect(OverlayPanel, bounds.topLeft, bounds.size)
    drawRect(accent.copy(alpha = 0.85f), bounds.topLeft, bounds.size, style = Stroke(d(1.5f)))
    drawRect(accent.copy(alpha = 0.09f), bounds.topLeft, Size(bounds.width, d(61f)))
}

fun DrawScope.drawFooterBack(textMeasurer: TextMeasurer, bounds: Rect, accent: Color) {
    val top = bounds.bottom - d(55f)
    drawRect(accent.copy(alpha = 0.08f), Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), d(41f)))
    drawRect(accent, Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), d(41f)), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "BACK [ESC / ENTER]", bounds.center.x, top + d(13f), 9f, accent, centered = true, weight = FontWeight.Bold)
}

fun DrawScope.drawLabFooter(textMeasurer: TextMeasurer, bounds: Rect, accent: Color) {
    val top = bounds.bottom - d(55f)
    drawRect(accent.copy(alpha = 0.08f), Offset(bounds.left, top), Size(bounds.width, d(55f)))
    drawLine(accent.copy(alpha = 0.65f), Offset(bounds.left, top), Offset(bounds.right, top), d(1f))
    drawLabel(textMeasurer, "BACK [ESC / ENTER]", bounds.center.x, top + d(18f), 9f, accent, centered = true, weight = FontWeight.Bold)
}

fun DrawScope.drawPagedFooter(textMeasurer: TextMeasurer, bounds: Rect, page: Int, maxPage: Int, accent: Color) {
    val top = bounds.bottom - d(55f)
    val closeRight = bounds.left + bounds.width * 0.45f
    val nextLeft = bounds.right - d(85f)
    drawRect(accent.copy(alpha = 0.07f), Offset(bounds.left, top), Size(bounds.width, d(55f)))
    drawLine(DarkLine, Offset(closeRight, top), Offset(closeRight, bounds.bottom), d(1f))
    drawLine(DarkLine, Offset(nextLeft, top), Offset(nextLeft, bounds.bottom), d(1f))
    drawLabel(textMeasurer, "BACK [ESC]", bounds.left + d(25f), top + d(18f), 9f, accent, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "‹  PAGE ${page + 1}/${maxPage + 1}", (closeRight + nextLeft) * 0.5f, top + d(18f), 9f, if (page > 0) White else Muted, centered = true)
    drawLabel(textMeasurer, "NEXT ›", bounds.right - d(42f), top + d(18f), 8f, if (page < maxPage) White else Muted, centered = true)
}

fun DrawScope.overlayBounds(maxWidth: Float = 900f, maxHeight: Float = 650f): Rect {
    val width = min(d(maxWidth), size.width - d(30f))
    val height = min(d(maxHeight), size.height - d(30f))
    val left = (size.width - width) * 0.5f
    val top = (size.height - height) * 0.5f
    return Rect(left, top, left + width, top + height)
}

fun DrawScope.drawWeaponGlyph(id: WeaponId, center: Offset, radius: Float, time: Float, color: Color) {
    when (id) {
        WeaponId.FLUX_WAKE -> {
            drawLine(color.copy(alpha = 0.35f), Offset(center.x - radius, center.y + radius * 0.55f), Offset(center.x + radius, center.y - radius * 0.55f), radius * 0.35f, StrokeCap.Round)
            drawLine(White, Offset(center.x - radius * 0.7f, center.y + radius * 0.4f), Offset(center.x + radius * 0.75f, center.y - radius * 0.4f), radius * 0.08f, StrokeCap.Round)
        }
        WeaponId.MORNINGSTAR -> {
            drawCircle(color.copy(alpha = 0.3f), radius, center, style = Stroke(radius * 0.08f, pathEffect = dashEffect))
            val ball = polar(center, radius, time * 2f)
            drawLine(color, center, ball, radius * 0.08f)
            drawPolygon(ball, radius * 0.32f, 8, time, White, Fill)
        }
        WeaponId.PHASE_LATTICE -> {
            drawCircle(color.copy(alpha = 0.22f), radius, center)
            drawCircle(color, radius * 0.78f, center, style = Stroke(radius * 0.09f))
            drawCircle(White, radius * 0.35f, center, style = Stroke(radius * 0.05f, pathEffect = dashEffect))
        }
        WeaponId.NULL_LANCE -> {
            drawLine(color.copy(alpha = 0.3f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * 0.28f, StrokeCap.Round)
            drawPolygon(Offset(center.x + radius * 0.72f, center.y), radius * 0.34f, 3, 0f, White, Fill)
        }
        WeaponId.GRAVITY_MINES -> {
            drawCircle(color.copy(alpha = 0.2f), radius, center)
            drawCircle(color, radius * 0.75f, center, style = Stroke(radius * 0.07f, pathEffect = dashEffect))
            drawPolygon(center, radius * 0.42f, 6, time, White, Stroke(radius * 0.07f))
        }
        WeaponId.ION_SWARM -> repeat(3) { index ->
            val point = polar(center, radius * 0.78f, time + index * TAU / 3f)
            drawPolygon(point, radius * 0.2f, 4, time + index, color, Fill)
        }
        WeaponId.RIFT_BLADES -> {
            drawPolygon(Offset(center.x - radius * 0.38f, center.y), radius * 0.55f, 4, time, color, Fill)
            drawPolygon(Offset(center.x + radius * 0.38f, center.y), radius * 0.55f, 4, -time, White, Fill)
        }
        WeaponId.ARC_COIL -> {
            drawCircle(color, radius, center, style = Stroke(radius * 0.1f))
            var previous = Offset(center.x - radius, center.y)
            repeat(5) { index ->
                val next = Offset(center.x - radius + radius * 0.4f * (index + 1), center.y + if (index % 2 == 0) -radius * 0.42f else radius * 0.42f)
                drawLine(White, previous, next, radius * 0.07f)
                previous = next
            }
        }
        WeaponId.QUASAR_CANNON -> {
            drawLine(color.copy(alpha = 0.35f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * 0.42f, StrokeCap.Round)
            drawCircle(White, radius * 0.23f, Offset(center.x + radius * 0.72f, center.y))
            drawCircle(color, radius * 0.82f, center, style = Stroke(radius * 0.07f))
        }
        WeaponId.ENTROPY_FIELD -> {
            drawCircle(color.copy(alpha = 0.12f), radius, center)
            drawCircle(color, radius, center, style = Stroke(radius * 0.07f, pathEffect = dashEffect))
            drawPolygon(center, radius * 0.48f, 7, time * 0.3f, White, Stroke(radius * 0.06f))
        }
        WeaponId.SINGULARITY_SPEAR -> {
            drawLine(color.copy(alpha = 0.28f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * 0.35f, StrokeCap.Round)
            drawLine(White, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * 0.09f, StrokeCap.Round)
            drawPolygon(Offset(center.x + radius * 0.8f, center.y), radius * 0.37f, 3, 0f, color, Fill)
        }
        WeaponId.PRISM_RELAY -> {
            val first = polar(center, radius * 0.78f, time * 1.8f)
            val second = polar(center, radius * 0.78f, time * 1.8f + TAU / 3f)
            val third = polar(center, radius * 0.78f, time * 1.8f + TAU * 2f / 3f)
            drawLine(color.copy(alpha = 0.65f), first, second, radius * 0.08f)
            drawLine(color.copy(alpha = 0.65f), second, third, radius * 0.08f)
            drawLine(color.copy(alpha = 0.65f), third, first, radius * 0.08f)
            drawPolygon(center, radius * 0.42f, 4, time, White, Fill)
        }
    }
}

fun weaponColor(id: WeaponId): Color = WeaponColors[id.ordinal.coerceIn(WeaponColors.indices)]

fun rarityColor(rarity: ItemRarity): Color = RarityColors[(rarity.rank - 1).coerceIn(RarityColors.indices)]

fun polar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)

fun formatCompact(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    val divisor = when {
        safe >= 1_000_000_000_000L -> 1_000_000_000_000L
        safe >= 1_000_000_000L -> 1_000_000_000L
        safe >= 1_000_000L -> 1_000_000L
        safe >= 1_000L -> 1_000L
        else -> return safe.toString()
    }
    val suffix = when (divisor) {
        1_000L -> "K"
        1_000_000L -> "M"
        1_000_000_000L -> "B"
        else -> "T"
    }
    val tenths = safe / (divisor / 10L)
    return if (tenths % 10L == 0L) "${tenths / 10L}$suffix" else "${tenths / 10L}.${tenths % 10L}$suffix"
}

fun DrawScope.drawBar(x: Float, y: Float, width: Float, height: Float, progress: Float, foreground: Color, background: Color) {
    drawRect(background, Offset(x, y), Size(width, height))
    drawRect(foreground, Offset(x, y), Size(width * progress.coerceIn(0f, 1f), height))
}

fun DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    fontSize: Float,
    color: Color,
    centered: Boolean = false,
    alignRight: Boolean = false,
    weight: FontWeight = FontWeight.Normal,
    alpha: Float = 1f,
    maxWidth: Float? = null,
    maxLines: Int = 1,
) {
    val style = textStyle(fontSize * textMeasurer.scale, color.copy(alpha = color.alpha * alpha), weight)
    val result = if (maxWidth != null) {
        textMeasurer.delegate.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = max(1, maxWidth.toInt())),
        )
    } else {
        textMeasurer.delegate.measure(text, style)
    }
    val actualX = when {
        centered -> x - result.size.width * 0.5f
        alignRight -> x - result.size.width
        else -> x
    }
    drawText(result, topLeft = Offset(actualX, y))
}

fun DrawScope.drawPolygon(center: Offset, radius: Float, sides: Int, rotation: Float, color: Color, style: androidx.compose.ui.graphics.drawscope.DrawStyle) {
    val path = Path()
    repeat(sides) { index ->
        val angle = rotation + index * TAU / sides
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = style)
}

fun DrawScope.isOnScreen(point: Offset, margin: Float = 0f): Boolean =
    point.x >= -margin && point.y >= -margin && point.x <= size.width + margin && point.y <= size.height + margin

fun DrawScope.d(value: Float): Float = value * density

fun positiveModulo(value: Float, modulus: Float): Float = ((value % modulus) + modulus) % modulus

fun formatOneDecimal(value: Float): String {
    val scaled = (value * 10f).toInt()
    return "${scaled / 10}.${abs(scaled % 10)}"
}

fun formatMultiplier(value: Float): String {
    val hundredths = (value * 100f + 0.5f).toInt()
    val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "${hundredths / 100}x" else "${hundredths / 100}.$fraction" + "x"
}
