// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kinetickk.foundation.design.generated.resources.Res
import kinetickk.foundation.design.generated.resources.kk_body_bold
import kinetickk.foundation.design.generated.resources.kk_body_medium
import kinetickk.foundation.design.generated.resources.kk_body_regular
import kinetickk.foundation.design.generated.resources.kk_cond_black
import kinetickk.foundation.design.generated.resources.kk_cond_black_italic
import kinetickk.foundation.design.generated.resources.kk_cond_extrabold
import kinetickk.foundation.design.generated.resources.kk_mono_bold
import kinetickk.foundation.design.generated.resources.kk_mono_medium
import kinetickk.foundation.design.generated.resources.kk_wide_black
import kinetickk.foundation.design.generated.resources.kk_wide_bold
import org.jetbrains.compose.resources.Font

/**
 * Font families per type role (SPEC section 4):
 * - [wide]: Unbounded 900/700 (clock, titles, big numerals, speed).
 * - [cond]: Sofia Sans Extra Condensed 900 italic, 900 upright, 800 upright (menus, buttons,
 *   headings, damage numbers).
 * - [label]: the cond family used at 800 upright with +9 % tracking (labels, tabs, tags).
 * - [body]: Sofia Sans Semi Condensed 400/500/700 (descriptions, tooltips).
 * - [mono]: Martian Mono 500/700 (readouts, small data).
 *
 * [display] is the heading family for plain [textStyle] call sites: its bold weight resolves to
 * cond 900 italic. New code uses the role builders in `KkText.kt`.
 */
@Immutable
data class InterfaceTypography(
    val body: FontFamily = FontFamily.SansSerif,
    val display: FontFamily = FontFamily.SansSerif,
    val wide: FontFamily = FontFamily.SansSerif,
    val cond: FontFamily = FontFamily.SansSerif,
    val label: FontFamily = cond,
    val mono: FontFamily = FontFamily.Monospace,
) {
    /** Per-instance memo behind the role builders; not part of equality. */
    internal val styleMemo: KkStyleMemo = KkStyleMemo()
}

/** Loads the bundled redesign fonts once per composition. */
@Composable
fun rememberInterfaceTypography(): InterfaceTypography {
    val wideBlack = Font(Res.font.kk_wide_black, FontWeight.Black)
    val wideBold = Font(Res.font.kk_wide_bold, FontWeight.Bold)
    val condBlackItalic = Font(Res.font.kk_cond_black_italic, FontWeight.Black, FontStyle.Italic)
    val condBlack = Font(Res.font.kk_cond_black, FontWeight.Black)
    val condExtraBold = Font(Res.font.kk_cond_extrabold, FontWeight.ExtraBold)
    val bodyRegular = Font(Res.font.kk_body_regular, FontWeight.Normal)
    val bodyMedium = Font(Res.font.kk_body_medium, FontWeight.Medium)
    val bodyBold = Font(Res.font.kk_body_bold, FontWeight.Bold)
    val monoMedium = Font(Res.font.kk_mono_medium, FontWeight.Medium)
    val monoBold = Font(Res.font.kk_mono_bold, FontWeight.Bold)
    // Legacy headings request Bold/Normal upright: map them onto the new condensed heading faces.
    val displayBold = Font(Res.font.kk_cond_black_italic, FontWeight.Bold)
    val displayNormal = Font(Res.font.kk_cond_extrabold, FontWeight.Normal)
    return remember(
        wideBlack, wideBold, condBlackItalic, condBlack, condExtraBold, bodyRegular, bodyMedium,
        bodyBold, monoMedium, monoBold, displayBold, displayNormal,
    ) {
        val cond = FontFamily(condBlackItalic, condBlack, condExtraBold)
        InterfaceTypography(
            body = FontFamily(bodyRegular, bodyMedium, bodyBold),
            display = FontFamily(displayBold, displayNormal),
            wide = FontFamily(wideBlack, wideBold),
            cond = cond,
            label = cond,
            mono = FontFamily(monoMedium, monoBold),
        )
    }
}
