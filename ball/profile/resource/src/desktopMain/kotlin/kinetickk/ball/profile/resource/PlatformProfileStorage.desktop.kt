// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import java.util.prefs.Preferences

actual fun createPlatformProfileResource(): ProfileResource =
    FixedKeyProfileResource(
        provider = DesktopProfileStorageProvider(
            profileNode = {
                Preferences.userRoot().node(ProfileStorageKeys.DESKTOP_PROFILE_NODE)
            },
            legacyNode = {
                Preferences.userRoot().node(ProfileStorageKeys.DESKTOP_LEGACY_NODE)
            },
        ),
    )

internal fun createDesktopProfileResource(
    profileNode: Preferences,
    legacyNode: Preferences,
): ProfileResource = FixedKeyProfileResource(
    provider = DesktopProfileStorageProvider(
        profileNode = { profileNode },
        legacyNode = { legacyNode },
    ),
)

private class DesktopProfileStorageProvider(
    private val profileNode: () -> Preferences,
    private val legacyNode: () -> Preferences,
) : ProfileStorageProvider {
    override fun readV4(): String? =
        profileNode().get(ProfileStorageKeys.DESKTOP_SNAPSHOT_V4, null)

    override fun writeV4(payload: String) {
        profileNode().apply {
            put(ProfileStorageKeys.DESKTOP_SNAPSHOT_V4, payload)
            flush()
        }
    }

    override fun readLegacyProgressV2(): String? =
        legacyNode().get(ProfileStorageKeys.DESKTOP_LEGACY_PROGRESS_V2, null)

    override fun readLegacyMatter(): String? =
        legacyNode().get(ProfileStorageKeys.DESKTOP_LEGACY_MATTER, null)

    override fun removeLegacyProgressV2() {
        legacyNode().apply {
            remove(ProfileStorageKeys.DESKTOP_LEGACY_PROGRESS_V2)
            flush()
        }
    }

    override fun removeLegacyMatter() {
        legacyNode().apply {
            remove(ProfileStorageKeys.DESKTOP_LEGACY_MATTER)
            flush()
        }
    }
}
