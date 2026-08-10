// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kotlinx.browser.localStorage
import org.w3c.dom.Storage

actual fun createPlatformProfileResource(): ProfileResource =
    createWebProfileResource(
        storage = { localStorage },
        keys = WebProfileStorageKeys(
            snapshotV4 = ProfileStorageKeys.WEB_SNAPSHOT_V4,
            legacyProgressV2 = ProfileStorageKeys.WEB_LEGACY_PROGRESS_V2,
            legacyMatter = ProfileStorageKeys.WEB_LEGACY_MATTER,
        ),
    )

internal data class WebProfileStorageKeys(
    val snapshotV4: String,
    val legacyProgressV2: String,
    val legacyMatter: String,
)

internal fun createWebProfileResource(
    storage: Storage,
    keys: WebProfileStorageKeys,
): ProfileResource = createWebProfileResource(storage = { storage }, keys = keys)

private fun createWebProfileResource(
    storage: () -> Storage,
    keys: WebProfileStorageKeys,
): ProfileResource = FixedKeyProfileResource(
    provider = WebProfileStorageProvider(storage, keys),
)

private class WebProfileStorageProvider(
    private val storage: () -> Storage,
    private val keys: WebProfileStorageKeys,
) : ProfileStorageProvider {
    override fun readV4(): String? = storage().getItem(keys.snapshotV4)

    override fun writeV4(payload: String) {
        storage().setItem(keys.snapshotV4, payload)
    }

    override fun readLegacyProgressV2(): String? = storage().getItem(keys.legacyProgressV2)

    override fun readLegacyMatter(): String? = storage().getItem(keys.legacyMatter)

    override fun removeLegacyProgressV2() {
        storage().removeItem(keys.legacyProgressV2)
    }

    override fun removeLegacyMatter() {
        storage().removeItem(keys.legacyMatter)
    }
}
