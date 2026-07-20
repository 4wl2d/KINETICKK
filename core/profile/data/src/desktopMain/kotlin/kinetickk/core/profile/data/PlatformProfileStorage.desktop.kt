// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.data

import java.util.prefs.Preferences
import kinetickk.core.profile.api.ProfileResource

private val preferences: Preferences by lazy {
    Preferences.userRoot().node(ProfileStorageKeys.DESKTOP_NODE)
}

internal actual fun createPlatformProfileResource(): ProfileResource = FixedKeyProfileResource(
    readProfilePayload = { preferences.get(ProfileStorageKeys.DESKTOP_PRIMARY, null) },
    readLegacyMatter = { preferences.get(ProfileStorageKeys.LEGACY_MATTER, null) },
    writeProfilePayload = { value ->
        preferences.put(ProfileStorageKeys.DESKTOP_PRIMARY, value)
        preferences.flush()
    },
    writeLegacyMatter = { value ->
        preferences.putInt(ProfileStorageKeys.LEGACY_MATTER, value)
        preferences.flush()
    },
)
