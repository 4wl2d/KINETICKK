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

// Construct the native-backed effect only when a Canvas actually draws it. Eager initialization
// made every consumer of an unrelated color token load Skiko, including pure geometry tests.
val dashEffect: PathEffect by lazy {
    PathEffect.dashPathEffect(floatArrayOf(9f, 9f))
}
fun textStyle(size: Float, color: Color = Kk.Bone, weight: FontWeight = FontWeight.Normal, family: FontFamily = FontFamily.SansSerif) =
    TextStyle(fontFamily = family, fontSize = size.sp, color = color, fontWeight = weight)

/**
 * The render context passed to every Canvas renderer: text measurement, the text-size [scale],
 * [language], [typography] and the Color vision [roles]. Canvas renderers read the five role
 * colors (you, threat, heat, shield, pol) only from [roles]; composables use `LocalKkRolePalette`.
 */
class CanvasTextMeasurer(
    val delegate: ComposeTextMeasurer,
    val scale: Float,
    val language: AppLanguage = AppLanguage.English,
    val typography: InterfaceTypography = InterfaceTypography(),
    val roles: KkRolePalette = KkRolePalette.Default,
)

typealias TextMeasurer = CanvasTextMeasurer
