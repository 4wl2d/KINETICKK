// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SpaceBlack = Color(0xFF050610)
val OverlayPanel = Color(0xE60B0D1D)
val GridBlue = Color(0xFF151B38)
val Cyan = Color(0xFF42F5E9)
val CyanSoft = Color(0x5542F5E9)
val Violet = Color(0xFFA96CFF)
val VioletSoft = Color(0x55A96CFF)
val Magenta = Color(0xFFFF4DC4)
val Acid = Color(0xFFB6FF5B)
val Orange = Color(0xFFFFA14B)
val Blue = Color(0xFF73A6FF)
val Gold = Color(0xFFFFD45B)
val Red = Color(0xFFFF426D)
val DamagePale = Color(0xFFFFF2C2)
val White = Color(0xFFF4F6FF)
val Muted = Color(0xFF8F98B5)
val DarkLine = Color(0xFF252C4F)
val dashEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))
val mono = FontFamily.Monospace
val ParticleColors = listOf(Cyan, Violet, Magenta, Acid, Red)
val RarityColors = listOf(Muted, Cyan, Violet, Magenta, Acid)
val WeaponColors = listOf(
    Cyan,
    Violet,
    Magenta,
    Acid,
    Orange,
    Cyan,
    Magenta,
    Violet,
    Orange,
    Red,
    White,
    Blue,
)
val DamageNumberColors = listOf(DamagePale, Gold, Orange, Red)
val DamageNumberTierScales = floatArrayOf(0.95f, 1.03f, 1.12f, 1.25f)

fun textStyle(size: Float, color: Color = White, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = mono, fontSize = size.sp, color = color, fontWeight = weight)

class CanvasTextMeasurer(
    val delegate: ComposeTextMeasurer,
    val scale: Float,
)

typealias TextMeasurer = CanvasTextMeasurer
