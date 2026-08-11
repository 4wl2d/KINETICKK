// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kotlinx.browser.localStorage
import org.w3c.dom.Storage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformProfileStorageWebTest {
    @Test
    fun isolatedBrowserKeysUseExactCapabilitiesAndPreserveUnrelatedData() {
        val prefix = "kinetickk_profile_resource_test_${Random.nextLong()}"
        val keys = IsolatedWebKeys(
            snapshotV4 = "${prefix}_v4",
            legacyProgressV2 = "${prefix}_v2",
            legacyMatter = "${prefix}_matter",
        )
        val unrelated = "${prefix}_unrelated"
        try {
            val resource = createProfileResource(IsolatedWebPersistence(localStorage, keys))
            assertEquals(
                ProfileBootstrapResourceResult.Observed(
                    snapshot = null,
                    legacyKeys = ProfileLegacyKeys.NONE,
                ),
                resource.readBootstrap(),
            )

            localStorage.setItem(keys.legacyProgressV2, "legacy")
            localStorage.setItem(keys.legacyMatter, "1")
            localStorage.setItem(unrelated, "preserve-me")
            val confirmed = testV4Snapshot(revision = 6L, legacyResetConfirmed = true)

            assertEquals(
                ProfileV4WriteResult.Written(confirmed.revision),
                resource.writeV4(confirmed),
            )
            assertEquals(requireEncoded(confirmed), localStorage.getItem(keys.snapshotV4))
            assertEquals(ProfileLegacyPurgeResult.Purged, resource.purgeLegacy())
            assertNull(localStorage.getItem(keys.legacyProgressV2))
            assertNull(localStorage.getItem(keys.legacyMatter))
            assertEquals("preserve-me", localStorage.getItem(unrelated))
        } finally {
            listOf(keys.snapshotV4, keys.legacyProgressV2, keys.legacyMatter, unrelated).forEach {
                localStorage.removeItem(it)
            }
        }
    }
}

private data class IsolatedWebKeys(
    val snapshotV4: String,
    val legacyProgressV2: String,
    val legacyMatter: String,
)

private class IsolatedWebPersistence(
    private val storage: Storage,
    private val keys: IsolatedWebKeys,
) : ExactProfilePersistence {
    override fun readV4(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(storage.getItem(keys.snapshotV4))

    override fun writeV4(payload: String): ProfileProviderMutationResult {
        storage.setItem(keys.snapshotV4, payload)
        return ProfileProviderMutationResult.COMPLETED
    }

    override fun readLegacyProgressV2(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(storage.getItem(keys.legacyProgressV2))

    override fun readLegacyMatter(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(storage.getItem(keys.legacyMatter))

    override fun removeLegacyProgressV2(): ProfileProviderMutationResult {
        storage.removeItem(keys.legacyProgressV2)
        return ProfileProviderMutationResult.COMPLETED
    }

    override fun removeLegacyMatter(): ProfileProviderMutationResult {
        storage.removeItem(keys.legacyMatter)
        return ProfileProviderMutationResult.COMPLETED
    }
}
