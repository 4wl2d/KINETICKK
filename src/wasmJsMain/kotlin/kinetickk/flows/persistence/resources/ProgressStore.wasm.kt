// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.persistence.resources

import kotlinx.browser.localStorage

actual fun createPlatformProgressStore(): ProgressStore = FixedKeyProgressStore(
    readProgressPayload = { localStorage.getItem(PROGRESS_KEY) },
    readLegacyMatter = { localStorage.getItem(LEGACY_MATTER_KEY) },
    writeProgressPayload = { value -> localStorage.setItem(PROGRESS_KEY, value) },
    writeLegacyMatter = { value -> localStorage.setItem(LEGACY_MATTER_KEY, value.toString()) },
)

private const val PROGRESS_KEY = "kinetickk_progress_v2"
private const val LEGACY_MATTER_KEY = "kinetickk_matter"
