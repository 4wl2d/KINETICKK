// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import java.util.UUID
import java.util.prefs.Preferences
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.impl.storage.requireEncoded
import kinetickk.ball.profile.impl.storage.testSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformProfileStorageDesktopTest {
    @Test
    fun isolatedPreferencesNodeUsesSnapshotAndLeavesLegacyStorageUntouched() {
        val testRoot = Preferences.userRoot().node(
            "kinetickk-test/profile-resource/${UUID.randomUUID()}",
        )
        val testParent = testRoot.parent()
        val profileNode = testRoot.node("profile")
        val legacyNode = testRoot.node("legacy")
        try {
            legacyNode.put(LEGACY_PROGRESS, "legacy")
            legacyNode.put(LEGACY_MATTER, "1")
            legacyNode.flush()

            val resource = createProfileResource(IsolatedDesktopPersistence(profileNode))
            assertEquals(
                ProfileSnapshotReadResult.Observed(snapshot = null),
                resource.readSnapshot(),
            )

            val snapshot = testSnapshot(revision = 5L)
            assertEquals(
                ProfileWriteResult.Written(snapshot.revision),
                resource.writeSnapshot(snapshot),
            )
            assertEquals(requireEncoded(snapshot), profileNode.get(SNAPSHOT, null))
            assertEquals("legacy", legacyNode.get(LEGACY_PROGRESS, null))
            assertEquals("1", legacyNode.get(LEGACY_MATTER, null))
        } finally {
            testRoot.removeNode()
            testParent.flush()
        }
    }
}

private const val SNAPSHOT = "snapshot"
private const val LEGACY_PROGRESS = "progress_v2"
private const val LEGACY_MATTER = "kinetickk_matter"

private class IsolatedDesktopPersistence(
    private val profileNode: Preferences,
) : ProfilePersistenceCapability {
    override fun readSnapshot(): ProfilePersistenceReadResult =
        ProfilePersistenceReadResult.Observed(profileNode.get(SNAPSHOT, null))

    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        profileNode.put(SNAPSHOT, payload)
        profileNode.flush()
        return ProfilePersistenceMutationResult.COMPLETED
    }
}
