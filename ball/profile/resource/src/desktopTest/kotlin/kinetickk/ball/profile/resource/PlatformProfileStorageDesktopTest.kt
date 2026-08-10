// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import java.util.UUID
import java.util.prefs.Preferences
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformProfileStorageDesktopTest {
    @Test
    fun isolatedPreferencesNodesUseOnlyExactKeysAndPreserveUnrelatedData() {
        val testRoot = Preferences.userRoot().node(
            "kinetickk-test/profile-resource/${UUID.randomUUID()}",
        )
        val testParent = testRoot.parent()
        val profileNode = testRoot.node("profile")
        val legacyNode = testRoot.node("legacy")
        try {
            val resource = createDesktopProfileResource(
                profileNode = profileNode,
                legacyNode = legacyNode,
            )
            assertEquals(
                ProfileBootstrapResourceResult.Observed(
                    snapshot = null,
                    legacyKeys = ProfileLegacyKeys.NONE,
                ),
                resource.readBootstrap(),
            )

            legacyNode.put(ProfileStorageKeys.DESKTOP_LEGACY_PROGRESS_V2, "legacy")
            legacyNode.put(ProfileStorageKeys.DESKTOP_LEGACY_MATTER, "1")
            legacyNode.put("unrelated", "preserve-me")
            legacyNode.flush()
            val confirmed = testV4Snapshot(revision = 5L, legacyResetConfirmed = true)

            assertEquals(
                ProfileV4WriteResult.Written(confirmed.revision),
                resource.writeV4(confirmed),
            )
            assertEquals(requireEncoded(confirmed), profileNode.get(ProfileStorageKeys.DESKTOP_SNAPSHOT_V4, null))
            assertNull(legacyNode.get(ProfileStorageKeys.DESKTOP_SNAPSHOT_V4, null))
            assertEquals(ProfileLegacyPurgeResult.Purged, resource.purgeLegacy())
            assertNull(legacyNode.get(ProfileStorageKeys.DESKTOP_LEGACY_PROGRESS_V2, null))
            assertNull(legacyNode.get(ProfileStorageKeys.DESKTOP_LEGACY_MATTER, null))
            assertEquals("preserve-me", legacyNode.get("unrelated", null))
        } finally {
            testRoot.removeNode()
            testParent.flush()
        }
    }
}
