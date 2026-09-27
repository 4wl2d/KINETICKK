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

// Legacy token names, repointed to the redesign palette so untouched screens already use it.
// Migrate call sites to Kk structural tokens or KkRolePalette roles by meaning, then delete these.

@Deprecated("Use Kk.Ink", ReplaceWith("Kk.Ink", "kinetickk.foundation.design.Kk"))
val SpaceBlack = Kk.Ink

@Deprecated("Use Kk.Ink2", ReplaceWith("Kk.Ink2", "kinetickk.foundation.design.Kk"))
val OverlayPanel = Kk.Ink2

@Deprecated("Use Kk.Ink3", ReplaceWith("Kk.Ink3", "kinetickk.foundation.design.Kk"))
val GridBlue = Kk.Ink3

@Deprecated("Use KkRolePalette.you (Kk.Volt is the Default palette)", ReplaceWith("Kk.Volt", "kinetickk.foundation.design.Kk"))
val KineticAccent = Kk.Volt

@Deprecated("Use KkRolePalette.shield (Kk.Shield is the Default palette)", ReplaceWith("Kk.Shield", "kinetickk.foundation.design.Kk"))
val Cyan = Kk.Shield

@Deprecated("Use KkRolePalette.pol (Kk.Pol is the Default palette)", ReplaceWith("Kk.Pol", "kinetickk.foundation.design.Kk"))
val Violet = Kk.Pol

@Deprecated("Map by meaning; rift aspect is Kk.ARift", ReplaceWith("Kk.ARift", "kinetickk.foundation.design.Kk"))
val Magenta = Kk.ARift

@Deprecated("Map by meaning; uncommon rarity is Kk.RUncommon", ReplaceWith("Kk.RUncommon", "kinetickk.foundation.design.Kk"))
val Acid = Kk.RUncommon

@Deprecated("Use KkRolePalette.heat (Kk.Heat is the Default palette)", ReplaceWith("Kk.Heat", "kinetickk.foundation.design.Kk"))
val Orange = Kk.Heat

@Deprecated("Use Kk.RRare", ReplaceWith("Kk.RRare", "kinetickk.foundation.design.Kk"))
val Blue = Kk.RRare

@Deprecated("Use Kk.RLegend", ReplaceWith("Kk.RLegend", "kinetickk.foundation.design.Kk"))
val Gold = Kk.RLegend

@Deprecated("Use KkRolePalette.threat (Kk.Hazard is the Default palette)", ReplaceWith("Kk.Hazard", "kinetickk.foundation.design.Kk"))
val Red = Kk.Hazard

@Deprecated("Use Kk.Bone", ReplaceWith("Kk.Bone", "kinetickk.foundation.design.Kk"))
val White = Kk.Bone

@Deprecated("Use Kk.Mute", ReplaceWith("Kk.Mute", "kinetickk.foundation.design.Kk"))
val Muted = Kk.Mute

@Deprecated("Use Kk.Line", ReplaceWith("Kk.Line", "kinetickk.foundation.design.Kk"))
val DarkLine = Kk.Line

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
