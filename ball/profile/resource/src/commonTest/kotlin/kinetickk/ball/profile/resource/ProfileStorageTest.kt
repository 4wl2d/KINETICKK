// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeRejection
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileResourceFailure
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileStorageTest {
    @Test
    fun bootstrapObservesV4AndExactLegacyPresenceWithoutImportingValues() {
        val snapshot = testV4Snapshot(revision = 7L)
        val provider = RecordingStorageProvider(
            v4 = requireEncoded(snapshot),
            legacyProgressV2 = "malformed-v2-is-presence-only",
            legacyMatter = "not-a-number",
        )
        val resource = resource(provider)

        assertEquals(
            ProfileBootstrapResourceResult.Observed(
                snapshot = snapshot,
                legacyKeys = ProfileLegacyKeys.ALL,
            ),
            resource.readBootstrap(),
        )
        assertEquals(listOf("readV4", "readLegacyProgressV2", "readLegacyMatter"), provider.operations)
    }

    @Test
    fun missingV4AndNoLegacyIsObservedWithoutWriting() {
        val provider = RecordingStorageProvider()

        assertEquals(
            ProfileBootstrapResourceResult.Observed(
                snapshot = null,
                legacyKeys = ProfileLegacyKeys.NONE,
            ),
            resource(provider).readBootstrap(),
        )
        assertEquals(listOf("readV4", "readLegacyProgressV2", "readLegacyMatter"), provider.operations)
    }

    @Test
    fun malformedV4IsRejectedAndNeverFallsBackToLegacyData() {
        val provider = RecordingStorageProvider(
            v4 = "3|75|100|legacy",
            legacyProgressV2 = "valid-or-invalid-is-irrelevant",
            legacyMatter = "37",
        )

        assertEquals(
            ProfileBootstrapResourceResult.Rejected(
                reason = ProfileV4Rejection.MALFORMED_JSON,
                legacyKeys = ProfileLegacyKeys.ALL,
            ),
            resource(provider).readBootstrap(),
        )
    }

    @Test
    fun everyProviderReadExceptionIsContainedAsOutcomeUnknown() {
        listOf("readV4", "readLegacyProgressV2", "readLegacyMatter").forEach { operation ->
            val provider = RecordingStorageProvider(throwOn = setOf(operation))

            assertEquals(
                ProfileBootstrapResourceResult.OutcomeUnknown(
                    ProfileResourceFailure.PROVIDER_READ_FAILED,
                ),
                resource(provider).readBootstrap(),
                operation,
            )
            assertEquals(emptyList(), provider.operations.filter(String::startsWithWriteOrRemove))
        }
    }

    @Test
    fun writeV4WritesOnlyCanonicalPayloadAndRequiresExactReadBack() {
        val snapshot = testV4Snapshot(revision = 11L)
        val provider = RecordingStorageProvider()

        assertEquals(
            ProfileV4WriteResult.Written(snapshot.revision),
            resource(provider).writeV4(snapshot),
        )
        assertEquals(requireEncoded(snapshot), provider.v4)
        assertEquals(listOf("writeV4", "readV4"), provider.operations)
        assertEquals(null, provider.legacyProgressV2)
        assertEquals(null, provider.legacyMatter)
    }

    @Test
    fun invalidOutboundSnapshotIsRejectedBeforeProviderWrite() {
        val invalid = testV4Snapshot(
            profile = kinetickk.ball.profile.api.PlayerProfile(
                economy = kinetickk.ball.profile.api.PlayerEconomy(-1L, 0L),
            ),
        )
        val provider = RecordingStorageProvider()

        assertEquals(
            ProfileV4WriteResult.Rejected(ProfileV4Rejection.INCONSISTENT_PROFILE),
            resource(provider).writeV4(invalid),
        )
        assertEquals(emptyList(), provider.operations)
    }

    @Test
    fun writeExceptionOrUnconfirmedReadBackIsOutcomeUnknownWithoutLegacyMutation() {
        val snapshot = testV4Snapshot(revision = 12L)
        val throwing = RecordingStorageProvider(throwOn = setOf("writeV4"))
        val ignored = RecordingStorageProvider(ignoreV4Writes = true)

        listOf(throwing, ignored).forEach { provider ->
            assertEquals(
                ProfileV4WriteResult.OutcomeUnknown(
                    ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
                resource(provider).writeV4(snapshot),
            )
            assertEquals(emptyList(), provider.operations.filter(String::startsWithRemove))
        }
    }

    @Test
    fun purgeRefusesToDeleteWithoutAConfirmedStrictV4Snapshot() {
        listOf(
            null,
            "malformed",
            requireEncoded(testV4Snapshot(legacyResetConfirmed = false)),
        ).forEach { guard ->
            val provider = RecordingStorageProvider(
                v4 = guard,
                legacyProgressV2 = "legacy",
                legacyMatter = "1",
            )

            assertEquals(
                ProfileLegacyPurgeResult.Rejected(
                    ProfileLegacyPurgeRejection.RESET_NOT_CONFIRMED,
                ),
                resource(provider).purgeLegacy(),
            )
            assertEquals(emptyList(), provider.operations.filter(String::startsWithRemove))
            assertEquals("legacy", provider.legacyProgressV2)
            assertEquals("1", provider.legacyMatter)
        }
    }

    @Test
    fun confirmedPurgeRemovesAndVerifiesOnlyTheTwoLegacyKeysInFixedOrder() {
        val provider = confirmedProvider()

        assertEquals(ProfileLegacyPurgeResult.Purged, resource(provider).purgeLegacy())
        assertEquals(
            listOf(
                "readV4",
                "removeLegacyProgressV2",
                "readLegacyProgressV2",
                "removeLegacyMatter",
                "readLegacyMatter",
            ),
            provider.operations,
        )
        assertEquals(null, provider.legacyProgressV2)
        assertEquals(null, provider.legacyMatter)
    }

    @Test
    fun purgeReportsKnownPartialDeletionForUserInitiatedRetry() {
        val provider = confirmedProvider(retainProgressV2 = true)

        assertEquals(
            ProfileLegacyPurgeResult.Partial(
                ProfileLegacyKeys(progressV2 = true, matter = false),
            ),
            resource(provider).purgeLegacy(),
        )
        assertEquals("legacy", provider.legacyProgressV2)
        assertEquals(null, provider.legacyMatter)
    }

    @Test
    fun purgeReportsUnknownAndKnownRemainingKeysWithoutThrowing() {
        val provider = confirmedProvider(
            retainProgressV2 = true,
            throwOn = setOf("removeLegacyMatter"),
        )

        assertEquals(
            ProfileLegacyPurgeResult.OutcomeUnknown(
                remaining = ProfileLegacyKeys(progressV2 = true, matter = false),
                unknown = ProfileLegacyKeys(progressV2 = false, matter = true),
                reason = ProfileResourceFailure.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
            ),
            resource(provider).purgeLegacy(),
        )
    }

    @Test
    fun purgeGuardReadFailureIsNondestructiveAndUnknown() {
        val provider = confirmedProvider(throwOn = setOf("readV4"))

        assertEquals(
            ProfileLegacyPurgeResult.OutcomeUnknown(
                remaining = ProfileLegacyKeys.NONE,
                unknown = ProfileLegacyKeys.ALL,
                reason = ProfileResourceFailure.PROVIDER_READ_FAILED,
            ),
            resource(provider).purgeLegacy(),
        )
        assertEquals(emptyList(), provider.operations.filter(String::startsWithRemove))
        assertEquals("legacy", provider.legacyProgressV2)
        assertEquals("1", provider.legacyMatter)
    }

    @Test
    fun productionStorageKeysAreExactAndClosed() {
        assertEquals("kinetickk/profile", ProfileStorageKeys.DESKTOP_PROFILE_NODE)
        assertEquals("snapshot_v4", ProfileStorageKeys.DESKTOP_SNAPSHOT_V4)
        assertEquals("kinetickk/progression", ProfileStorageKeys.DESKTOP_LEGACY_NODE)
        assertEquals("progress_v2", ProfileStorageKeys.DESKTOP_LEGACY_PROGRESS_V2)
        assertEquals("kinetickk_matter", ProfileStorageKeys.DESKTOP_LEGACY_MATTER)
        assertEquals("kinetickk_profile_v4", ProfileStorageKeys.WEB_SNAPSHOT_V4)
        assertEquals("kinetickk_progress_v2", ProfileStorageKeys.WEB_LEGACY_PROGRESS_V2)
        assertEquals("kinetickk_matter", ProfileStorageKeys.WEB_LEGACY_MATTER)
    }

    private fun resource(provider: ProfileStorageProvider): ProfileResource =
        FixedKeyProfileResource(provider)

    private fun confirmedProvider(
        retainProgressV2: Boolean = false,
        throwOn: Set<String> = emptySet(),
    ): RecordingStorageProvider = RecordingStorageProvider(
        v4 = requireEncoded(testV4Snapshot(legacyResetConfirmed = true)),
        legacyProgressV2 = "legacy",
        legacyMatter = "1",
        retainProgressV2 = retainProgressV2,
        throwOn = throwOn,
    )
}

private class RecordingStorageProvider(
    var v4: String? = null,
    var legacyProgressV2: String? = null,
    var legacyMatter: String? = null,
    private val retainProgressV2: Boolean = false,
    private val retainMatter: Boolean = false,
    private val ignoreV4Writes: Boolean = false,
    private val throwOn: Set<String> = emptySet(),
) : ProfileStorageProvider {
    val operations = mutableListOf<String>()

    override fun readV4(): String? = operation("readV4") { v4 }

    override fun writeV4(payload: String) {
        operation("writeV4") {
            if (!ignoreV4Writes) v4 = payload
        }
    }

    override fun readLegacyProgressV2(): String? =
        operation("readLegacyProgressV2") { legacyProgressV2 }

    override fun readLegacyMatter(): String? =
        operation("readLegacyMatter") { legacyMatter }

    override fun removeLegacyProgressV2() {
        operation("removeLegacyProgressV2") {
            if (!retainProgressV2) legacyProgressV2 = null
        }
    }

    override fun removeLegacyMatter() {
        operation("removeLegacyMatter") {
            if (!retainMatter) legacyMatter = null
        }
    }

    private inline fun <T> operation(name: String, block: () -> T): T {
        operations += name
        if (name in throwOn) error("private provider detail")
        return block()
    }
}

private fun String.startsWithWriteOrRemove(): Boolean =
    startsWith("write") || startsWith("remove")

private fun String.startsWithRemove(): Boolean = startsWith("remove")
