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
            val persistence = IsolatedDesktopPersistence(
                profileNode = profileNode,
                legacyNode = legacyNode,
            )
            val resource = createProfileResource(persistence)
            assertEquals(
                ProfileBootstrapResourceResult.Observed(
                    snapshot = null,
                    legacyKeys = ProfileLegacyKeys.NONE,
                ),
                resource.readBootstrap(),
            )

            legacyNode.put(LEGACY_PROGRESS_V2, "legacy")
            legacyNode.put(LEGACY_MATTER, "1")
            legacyNode.put("unrelated", "preserve-me")
            legacyNode.flush()
            val confirmed = testV4Snapshot(revision = 5L, legacyResetConfirmed = true)

            assertEquals(
                ProfileV4WriteResult.Written(confirmed.revision),
                resource.writeV4(confirmed),
            )
            assertEquals(requireEncoded(confirmed), profileNode.get(SNAPSHOT_V4, null))
            assertNull(legacyNode.get(SNAPSHOT_V4, null))
            assertEquals(ProfileLegacyPurgeResult.Purged, resource.purgeLegacy())
            assertNull(legacyNode.get(LEGACY_PROGRESS_V2, null))
            assertNull(legacyNode.get(LEGACY_MATTER, null))
            assertEquals("preserve-me", legacyNode.get("unrelated", null))
        } finally {
            testRoot.removeNode()
            testParent.flush()
        }
    }
}

private const val SNAPSHOT_V4 = "snapshot_v4"
private const val LEGACY_PROGRESS_V2 = "progress_v2"
private const val LEGACY_MATTER = "kinetickk_matter"

private class IsolatedDesktopPersistence(
    private val profileNode: Preferences,
    private val legacyNode: Preferences,
) : ExactProfilePersistence {
    override fun readV4(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(profileNode.get(SNAPSHOT_V4, null))

    override fun writeV4(payload: String): ProfileProviderMutationResult {
        profileNode.put(SNAPSHOT_V4, payload)
        profileNode.flush()
        return ProfileProviderMutationResult.COMPLETED
    }

    override fun readLegacyProgressV2(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(legacyNode.get(LEGACY_PROGRESS_V2, null))

    override fun readLegacyMatter(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(legacyNode.get(LEGACY_MATTER, null))

    override fun removeLegacyProgressV2(): ProfileProviderMutationResult {
        legacyNode.remove(LEGACY_PROGRESS_V2)
        legacyNode.flush()
        return ProfileProviderMutationResult.COMPLETED
    }

    override fun removeLegacyMatter(): ProfileProviderMutationResult {
        legacyNode.remove(LEGACY_MATTER)
        legacyNode.flush()
        return ProfileProviderMutationResult.COMPLETED
    }
}
