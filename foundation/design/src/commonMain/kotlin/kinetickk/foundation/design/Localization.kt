// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.compositionLocalOf
import kinetickk.foundation.common.localization.AppLanguage

/** A presentation projection of the accepted Profile preference. */
val LocalAppLanguage = compositionLocalOf { AppLanguage.Russian }
