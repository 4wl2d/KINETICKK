// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.ProfileResource
import kotlinx.browser.localStorage

actual fun createPlatformProfileResource(): ProfileResource = FixedKeyProfileResource(
    readProfilePayload = { localStorage.getItem(ProfileStorageKeys.WEB_PRIMARY) },
    readLegacyMatter = { localStorage.getItem(ProfileStorageKeys.LEGACY_MATTER) },
    writeProfilePayload = { value -> localStorage.setItem(ProfileStorageKeys.WEB_PRIMARY, value) },
    writeLegacyMatter = { value -> localStorage.setItem(ProfileStorageKeys.LEGACY_MATTER, value.toString()) },
)
