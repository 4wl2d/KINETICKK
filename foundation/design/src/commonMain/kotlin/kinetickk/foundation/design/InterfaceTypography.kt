// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import kinetickk.foundation.common.localization.AppLanguage
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
    /**
     * The app language's locale for every role style. Sofia Sans draws Bulgarian Cyrillic forms by
     * default and switches to Russian forms through its `locl` feature, which needs this locale.
     */
    val localeList: LocaleList? = null,
) {
    /** Per-instance memo behind the role builders; not part of equality. */
    internal val styleMemo: KkStyleMemo = KkStyleMemo()
}

/** The app supplies ready font families; standalone previews can still load their own fonts. */
val LocalInterfaceTypography = staticCompositionLocalOf<InterfaceTypography?> { null }

/** Reuses the startup fonts, retaining language-specific shaping without reloading font data. */
@Composable
fun rememberInterfaceTypography(): InterfaceTypography {
    val language = LocalAppLanguage.current
    val prepared = LocalInterfaceTypography.current
    if (prepared != null) {
        return remember(prepared, language) { prepared.copy(localeList = language.localeList()) }
    }
    val fonts = InterfaceFontResources.all.map { Font(it.resource, it.weight, it.style) }
    return remember(fonts, language) {
        interfaceTypography(fonts).copy(localeList = language.localeList())
    }
}

/** Text shaping locale of an app language (selects the fonts' Russian letterforms for Russian). */
fun AppLanguage.localeList(): LocaleList = when (this) {
    AppLanguage.English -> LocaleList("en")
    AppLanguage.Russian -> LocaleList("ru")
}
