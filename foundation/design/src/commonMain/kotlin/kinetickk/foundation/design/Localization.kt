// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.compositionLocalOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.TextResource

/** A presentation projection of the accepted Profile preference. */
val LocalAppLanguage = compositionLocalOf { AppLanguage.Russian }

internal enum class NavigationText(
    override val english: String,
    override val russian: String,
) : TextResource {
    Back("Back · Esc", "Назад · Esc"),
    BackEscape("Back · Esc", "Назад · Esc"),
    Page("‹  {0}/{1}", "‹  {0}/{1}"),
    Next("Next ›", "Далее ›"),
}
