// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.data.progress

import java.util.prefs.Preferences
import kinetickk.feature.game.domain.port.progress.ProgressStore

private val preferences: Preferences by lazy {
    Preferences.userRoot().node("kinetickk/progression")
}

actual fun createPlatformProgressStore(): ProgressStore = FixedKeyProgressStore(
    readProgressPayload = { preferences.get(PROGRESS_KEY, null) },
    readLegacyMatter = { preferences.get(LEGACY_MATTER_KEY, null) },
    writeProgressPayload = { value ->
        preferences.put(PROGRESS_KEY, value)
        preferences.flush()
    },
    writeLegacyMatter = { value ->
        preferences.putInt(LEGACY_MATTER_KEY, value)
        preferences.flush()
    },
)

private const val PROGRESS_KEY = "progress_v2"
private const val LEGACY_MATTER_KEY = "kinetickk_matter"
