// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kinetickk.foundation.common.localization.AppLanguage

val SpaceBlack = Color(0xFF101212)
val OverlayPanel = Color(0xFF181B1A)
val GridBlue = Color(0xFF202726)
val KineticAccent = Color(0xFFD8FF3E)
val Cyan = Color(0xFF42F5E9)
val Violet = Color(0xFFA96CFF)
val Magenta = Color(0xFFFF4DC4)
val Acid = Color(0xFFB6FF5B)
val Orange = Color(0xFFFFA14B)
val Blue = Color(0xFF73A6FF)
val Gold = Color(0xFFFFD45B)
val Red = Color(0xFFFF426D)
val White = Color(0xFFF1F0E8)
val Muted = Color(0xFFA8AEA6)
val DarkLine = Color(0xFF353B36)
// Construct the native-backed effect only when a Canvas actually draws it. Eager initialization
// made every consumer of an unrelated color token load Skiko, including pure geometry tests.
val dashEffect: PathEffect by lazy {
    PathEffect.dashPathEffect(floatArrayOf(9f, 9f))
}
fun textStyle(size: Float, color: Color = White, weight: FontWeight = FontWeight.Normal, family: FontFamily = FontFamily.SansSerif) =
    TextStyle(fontFamily = family, fontSize = size.sp, color = color, fontWeight = weight)

class CanvasTextMeasurer(
    val delegate: ComposeTextMeasurer,
    val scale: Float,
    val language: AppLanguage = AppLanguage.English,
    val typography: InterfaceTypography = InterfaceTypography(),
)

typealias TextMeasurer = CanvasTextMeasurer
