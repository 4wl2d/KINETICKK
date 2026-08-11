// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeRejection
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfilePurgeOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun typedProviderReadFailureIsAConfirmedNondestructiveResourceFailure() {
        listOf("readV4", "readLegacyProgressV2", "readLegacyMatter").forEach { operation ->
            val provider = RecordingStorageProvider(failedReads = setOf(operation))

            assertEquals(
                ProfileBootstrapResourceResult.ResourceFailure(
                    ProfileReadFailure.PROVIDER_READ_FAILED,
                ),
                resource(provider).readBootstrap(),
                operation,
            )
            assertEquals(emptyList(), provider.operations.filter(String::startsWithWriteOrRemove))
        }
    }

    @Test
    fun providerProgrammingFaultPropagatesWithoutFabricatedResourceEvidence() {
        val provider = RecordingStorageProvider(programmingFaultOn = setOf("readV4"))

        assertFailsWith<IllegalStateException> {
            resource(provider).readBootstrap()
        }
        assertEquals(listOf("readV4"), provider.operations)
        assertEquals(emptyList(), provider.operations.filter(String::startsWithWriteOrRemove))
    }

    @Test
    fun providerWriteProgrammingFaultPropagatesWithoutFabricatedWriteEvidence() {
        val snapshot = testV4Snapshot(revision = 9L)
        val provider = RecordingStorageProvider(programmingFaultOn = setOf("writeV4"))

        assertFailsWith<IllegalStateException> {
            resource(provider).writeV4(snapshot)
        }
        assertEquals(listOf("writeV4"), provider.operations)
        assertEquals(null, provider.v4)
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
    fun typedPossibleWriteExecutionOrUnconfirmedReadBackIsOutcomeUnknownWithoutLegacyMutation() {
        val snapshot = testV4Snapshot(revision = 12L)
        val possibleExecution = RecordingStorageProvider(possibleMutations = setOf("writeV4"))
        val ignored = RecordingStorageProvider(ignoreV4Writes = true)

        listOf(possibleExecution, ignored).forEach { provider ->
            assertEquals(
                ProfileV4WriteResult.OutcomeUnknown(
                    ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
                resource(provider).writeV4(snapshot),
            )
            assertEquals(emptyList(), provider.operations.filter(String::startsWithRemove))
        }
    }

    @Test
    fun typedWriteFailureBeforeExecutionIsKnownResourceFailure() {
        val snapshot = testV4Snapshot(revision = 13L)
        val provider = RecordingStorageProvider(failedBeforeMutations = setOf("writeV4"))

        assertEquals(
            ProfileV4WriteResult.ResourceFailure(
                ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
            ),
            resource(provider).writeV4(snapshot),
        )
        assertEquals(listOf("writeV4"), provider.operations)
        assertEquals(null, provider.v4)
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
                "readLegacyProgressV2",
                "readLegacyMatter",
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
    fun typedPossiblePurgeExecutionReportsUnknownAndKnownRemainingKeys() {
        val provider = confirmedProvider(
            retainProgressV2 = true,
            possibleMutations = setOf("removeLegacyMatter"),
        )

        assertEquals(
            ProfileLegacyPurgeResult.OutcomeUnknown(
                remaining = ProfileLegacyKeys(progressV2 = true, matter = false),
                unknown = ProfileLegacyKeys(progressV2 = false, matter = true),
                reason = ProfilePurgeOutcomeUnknownReason.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
            ),
            resource(provider).purgeLegacy(),
        )
    }

    @Test
    fun mixedPurgeFailureBeforeExecutionRetainsExactKnownAndUnknownKeyEvidence() {
        val knownPartial = confirmedProvider(
            failedBeforeMutations = setOf("removeLegacyMatter"),
        )
        assertEquals(
            ProfileLegacyPurgeResult.Partial(
                ProfileLegacyKeys(progressV2 = false, matter = true),
            ),
            resource(knownPartial).purgeLegacy(),
        )
        assertEquals(null, knownPartial.legacyProgressV2)
        assertEquals("1", knownPartial.legacyMatter)

        val mixedUnknown = confirmedProvider(
            possibleMutations = setOf("removeLegacyProgressV2"),
            failedBeforeMutations = setOf("removeLegacyMatter"),
        )
        assertEquals(
            ProfileLegacyPurgeResult.OutcomeUnknown(
                remaining = ProfileLegacyKeys(progressV2 = false, matter = true),
                unknown = ProfileLegacyKeys(progressV2 = true, matter = false),
                reason = ProfilePurgeOutcomeUnknownReason.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
            ),
            resource(mixedUnknown).purgeLegacy(),
        )
    }

    @Test
    fun purgeGuardReadFailureIsKnownNondestructiveAndAttemptsNoRemoval() {
        val provider = confirmedProvider(failedReads = setOf("readV4"))

        assertEquals(
            ProfileLegacyPurgeResult.ResourceFailure(
                reason = ProfileReadFailure.PROVIDER_READ_FAILED,
            ),
            resource(provider).purgeLegacy(),
        )
        assertEquals(emptyList(), provider.operations.filter(String::startsWithRemove))
        assertEquals("legacy", provider.legacyProgressV2)
        assertEquals("1", provider.legacyMatter)
    }

    @Test
    fun purgePreReadsBothLegacyKeysBeforeAnyRemoval() {
        listOf("readLegacyProgressV2", "readLegacyMatter").forEach { failedRead ->
            val provider = confirmedProvider(failedReads = setOf(failedRead))

            assertEquals(
                ProfileLegacyPurgeResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
                resource(provider).purgeLegacy(),
                failedRead,
            )
            assertEquals(emptyList(), provider.operations.filter(String::startsWithRemove))
            assertEquals("legacy", provider.legacyProgressV2)
            assertEquals("1", provider.legacyMatter)
        }
    }

    @Test
    fun providerPurgeProgrammingFaultPropagatesWithoutFabricatedPurgeEvidence() {
        val provider = confirmedProvider(
            programmingFaultOn = setOf("removeLegacyProgressV2"),
        )

        assertFailsWith<IllegalStateException> {
            resource(provider).purgeLegacy()
        }
        assertEquals(
            listOf(
                "readV4",
                "readLegacyProgressV2",
                "readLegacyMatter",
                "removeLegacyProgressV2",
            ),
            provider.operations,
        )
        assertEquals("legacy", provider.legacyProgressV2)
        assertEquals("1", provider.legacyMatter)
    }

    private fun resource(provider: ExactProfilePersistence): ProfileResource =
        createProfileResource(provider)

    private fun confirmedProvider(
        retainProgressV2: Boolean = false,
        failedReads: Set<String> = emptySet(),
        possibleMutations: Set<String> = emptySet(),
        failedBeforeMutations: Set<String> = emptySet(),
        programmingFaultOn: Set<String> = emptySet(),
    ): RecordingStorageProvider = RecordingStorageProvider(
        v4 = requireEncoded(testV4Snapshot(legacyResetConfirmed = true)),
        legacyProgressV2 = "legacy",
        legacyMatter = "1",
        retainProgressV2 = retainProgressV2,
        failedReads = failedReads,
        possibleMutations = possibleMutations,
        failedBeforeMutations = failedBeforeMutations,
        programmingFaultOn = programmingFaultOn,
    )
}

private class RecordingStorageProvider(
    var v4: String? = null,
    var legacyProgressV2: String? = null,
    var legacyMatter: String? = null,
    private val retainProgressV2: Boolean = false,
    private val retainMatter: Boolean = false,
    private val ignoreV4Writes: Boolean = false,
    private val failedReads: Set<String> = emptySet(),
    private val possibleMutations: Set<String> = emptySet(),
    private val failedBeforeMutations: Set<String> = emptySet(),
    private val programmingFaultOn: Set<String> = emptySet(),
) : ExactProfilePersistence {
    val operations = mutableListOf<String>()

    override fun readV4(): ProfileProviderReadResult = readOperation("readV4") { v4 }

    override fun writeV4(payload: String): ProfileProviderMutationResult =
        mutationOperation("writeV4") {
            if (!ignoreV4Writes) v4 = payload
        }

    override fun readLegacyProgressV2(): ProfileProviderReadResult =
        readOperation("readLegacyProgressV2") { legacyProgressV2 }

    override fun readLegacyMatter(): ProfileProviderReadResult =
        readOperation("readLegacyMatter") { legacyMatter }

    override fun removeLegacyProgressV2(): ProfileProviderMutationResult =
        mutationOperation("removeLegacyProgressV2") {
            if (!retainProgressV2) legacyProgressV2 = null
        }

    override fun removeLegacyMatter(): ProfileProviderMutationResult =
        mutationOperation("removeLegacyMatter") {
            if (!retainMatter) legacyMatter = null
        }

    private inline fun readOperation(name: String, block: () -> String?): ProfileProviderReadResult {
        operations += name
        if (name in programmingFaultOn) error("private provider programming fault")
        return if (name in failedReads) {
            ProfileProviderReadResult.Failed
        } else {
            ProfileProviderReadResult.Observed(block())
        }
    }

    private inline fun mutationOperation(name: String, block: () -> Unit): ProfileProviderMutationResult {
        operations += name
        if (name in programmingFaultOn) error("private provider programming fault")
        if (name in failedBeforeMutations) return ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION
        if (name in possibleMutations) return ProfileProviderMutationResult.POSSIBLE_EXECUTION
        block()
        return ProfileProviderMutationResult.COMPLETED
    }
}

private fun String.startsWithWriteOrRemove(): Boolean =
    startsWith("write") || startsWith("remove")

private fun String.startsWithRemove(): Boolean = startsWith("remove")
