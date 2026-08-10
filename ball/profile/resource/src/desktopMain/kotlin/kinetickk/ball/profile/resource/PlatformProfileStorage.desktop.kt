// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import java.util.prefs.Preferences
import kinetickk.ball.profile.api.ProfileResource

private val preferences: Preferences by lazy {
    Preferences.userRoot().node(ProfileStorageKeys.DESKTOP_NODE)
}

actual fun createPlatformProfileResource(): ProfileResource = FixedKeyProfileResource(
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
