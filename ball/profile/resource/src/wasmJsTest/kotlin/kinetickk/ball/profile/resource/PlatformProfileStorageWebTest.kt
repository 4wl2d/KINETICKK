// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileWriteResult
import kotlinx.browser.localStorage
import org.w3c.dom.Storage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformProfileStorageWebTest {
    @Test
    fun isolatedBrowserKeyUsesCurrentSnapshotAndLeavesLegacyStorageUntouched() {
        val prefix = "kinetickk_profile_resource_test_${Random.nextLong()}"
        val snapshotKey = "${prefix}_profile"
        val legacyProgressKey = "${prefix}_progress_v2"
        val legacyMatterKey = "${prefix}_matter"
        try {
            localStorage.setItem(legacyProgressKey, "legacy")
            localStorage.setItem(legacyMatterKey, "1")
            val resource = createProfileResource(
                IsolatedWebPersistence(localStorage, snapshotKey),
            )
            assertEquals(
                ProfileSnapshotReadResult.Observed(snapshot = null),
                resource.readSnapshot(),
            )

            val snapshot = testSnapshot(revision = 6L)
            assertEquals(
                ProfileWriteResult.Written(snapshot.revision),
                resource.writeSnapshot(snapshot),
            )
            assertEquals(requireEncoded(snapshot), localStorage.getItem(snapshotKey))
            assertEquals("legacy", localStorage.getItem(legacyProgressKey))
            assertEquals("1", localStorage.getItem(legacyMatterKey))
        } finally {
            listOf(snapshotKey, legacyProgressKey, legacyMatterKey).forEach(localStorage::removeItem)
        }
    }
}

private class IsolatedWebPersistence(
    private val storage: Storage,
    private val snapshotKey: String,
) : ExactProfilePersistence {
    override fun readSnapshot(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(storage.getItem(snapshotKey))

    override fun writeSnapshot(payload: String): ProfileProviderMutationResult {
        storage.setItem(snapshotKey, payload)
        return ProfileProviderMutationResult.COMPLETED
    }
}
