// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import kinetickk.foundation.common.localization.text
import kinetickk.foundation.common.localization.AppLanguage
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val TAU = 6.2831855f

fun polar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)

fun formatCompact(value: Long, language: AppLanguage = AppLanguage.English): String {
    val safe = value.coerceAtLeast(0L)
    val divisor = when {
        safe >= 1_000_000_000_000L -> 1_000_000_000_000L
        safe >= 1_000_000_000L -> 1_000_000_000L
        safe >= 1_000_000L -> 1_000_000L
        safe >= 1_000L -> 1_000L
        else -> return safe.toString()
    }
    val suffix = when (language) {
        AppLanguage.English -> when (divisor) {
            1_000L -> "K"
            1_000_000L -> "M"
            1_000_000_000L -> "B"
            else -> "T"
        }
        AppLanguage.Russian -> when (divisor) {
            1_000L -> " тыс."
            1_000_000L -> " млн"
            1_000_000_000L -> " млрд"
            else -> " трлн"
        }
    }
    val tenths = safe / (divisor / 10L)
    val separator = if (language == AppLanguage.Russian) ',' else '.'
    return if (tenths % 10L == 0L) "${tenths / 10L}$suffix" else "${tenths / 10L}$separator${tenths % 10L}$suffix"
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

fun formatOneDecimal(value: Float, language: AppLanguage = AppLanguage.English): String {
    val scaled = (value * 10f).toInt()
    val separator = if (language == AppLanguage.Russian) ',' else '.'
    return "${scaled / 10}$separator${abs(scaled % 10)}"
}

fun formatMultiplier(value: Float, language: AppLanguage = AppLanguage.English): String {
    val hundredths = (value * 100f + 0.5f).toInt()
    val cents = hundredths % 100
    val tens = cents / 10
    val ones = cents % 10
    val fraction = when {
        ones != 0 -> "$tens$ones"
        tens != 0 -> tens.toString()
        else -> ""
    }
    val separator = if (language == AppLanguage.Russian) ',' else '.'
    return if (fraction.isEmpty()) "${hundredths / 100}x" else "${hundredths / 100}$separator${fraction}x"
}
