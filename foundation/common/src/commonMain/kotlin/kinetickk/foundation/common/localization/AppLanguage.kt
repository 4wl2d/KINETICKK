// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.common.localization

/** Supported interface languages, persisted by their stable language code. */
enum class AppLanguage(val code: String, val nativeName: String) {
    Russian("ru", "Русский"),
    English("en", "English"),
}
