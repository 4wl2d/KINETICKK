// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.model

import kotlinx.browser.localStorage

actual fun loadMatter(): Int = runCatching {
    localStorage.getItem("kinetickk_matter")?.toIntOrNull() ?: 0
}.getOrDefault(0)

actual fun saveMatter(value: Int) {
    runCatching { localStorage.setItem("kinetickk_matter", value.coerceAtLeast(0).toString()) }
}

actual fun loadProgress(): String? = runCatching {
    localStorage.getItem("kinetickk_progress_v2")
}.getOrNull()

actual fun saveProgress(value: String) {
    runCatching { localStorage.setItem("kinetickk_progress_v2", value) }
}
