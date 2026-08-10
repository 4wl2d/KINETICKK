// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kotlinx.browser.localStorage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformProfileStorageWebTest {
    @Test
    fun isolatedBrowserKeysUseExactCapabilitiesAndPreserveUnrelatedData() {
        val prefix = "kinetickk_profile_resource_test_${Random.nextLong()}"
        val keys = WebProfileStorageKeys(
            snapshotV4 = "${prefix}_v4",
            legacyProgressV2 = "${prefix}_v2",
            legacyMatter = "${prefix}_matter",
        )
        val unrelated = "${prefix}_unrelated"
        try {
            val resource = createWebProfileResource(localStorage, keys)
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
