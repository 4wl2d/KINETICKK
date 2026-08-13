// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.ProfileWriteFailure
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfileStorageTest {
    @Test
    fun readObservesTheCurrentSnapshotWithoutConsultingAnyOtherKey() {
        val snapshot = testSnapshot(revision = 7L)
        val provider = RecordingStorageProvider(payload = requireEncoded(snapshot))

        assertEquals(
            ProfileSnapshotReadResult.Observed(snapshot),
            resource(provider).readSnapshot(),
        )
        assertEquals(listOf("readSnapshot"), provider.operations)
    }

    @Test
    fun missingSnapshotIsObservedWithoutWriting() {
        val provider = RecordingStorageProvider()

        assertEquals(
            ProfileSnapshotReadResult.Observed(snapshot = null),
            resource(provider).readSnapshot(),
        )
        assertEquals(listOf("readSnapshot"), provider.operations)
    }

    @Test
    fun malformedSnapshotIsRejectedWithoutFallback() {
        val provider = RecordingStorageProvider(payload = "3|75|100|legacy")

        assertEquals(
            ProfileSnapshotReadResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON),
            resource(provider).readSnapshot(),
        )
        assertEquals(listOf("readSnapshot"), provider.operations)
    }

    @Test
    fun typedProviderReadFailureIsAConfirmedNondestructiveResourceFailure() {
        val provider = RecordingStorageProvider(failedReads = true)

        assertEquals(
            ProfileSnapshotReadResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
            resource(provider).readSnapshot(),
        )
        assertEquals(listOf("readSnapshot"), provider.operations)
    }

    @Test
    fun providerProgrammingFaultPropagatesWithoutFabricatedEvidence() {
        val provider = RecordingStorageProvider(programmingFaultOn = setOf("readSnapshot"))

        assertFailsWith<IllegalStateException> {
            resource(provider).readSnapshot()
        }
        assertEquals(listOf("readSnapshot"), provider.operations)
    }

    @Test
    fun successfulWriteIsConfirmedByExactReadBack() {
        val snapshot = testSnapshot(revision = 11L)
        val provider = RecordingStorageProvider()

        assertEquals(
            ProfileWriteResult.Written(snapshot.revision),
            resource(provider).writeSnapshot(snapshot),
        )
        assertEquals(requireEncoded(snapshot), provider.payload)
        assertEquals(listOf("writeSnapshot", "readSnapshot"), provider.operations)
    }

    @Test
    fun invalidOutboundSnapshotIsRejectedBeforeProviderWrite() {
        val invalid = testSnapshot(
            profile = PlayerProfile(economy = PlayerEconomy(-1L, 0L)),
        )
        val provider = RecordingStorageProvider()

        assertEquals(
            ProfileWriteResult.Rejected(ProfileSnapshotRejection.INCONSISTENT_PROFILE),
            resource(provider).writeSnapshot(invalid),
        )
        assertEquals(emptyList(), provider.operations)
    }

    @Test
    fun possibleWriteExecutionOrUnconfirmedReadBackIsOutcomeUnknown() {
        val snapshot = testSnapshot(revision = 12L)
        val providers = listOf(
            RecordingStorageProvider(possibleWrite = true),
            RecordingStorageProvider(ignoreWrite = true),
            RecordingStorageProvider(failedReads = true),
        )

        providers.forEach { provider ->
            assertEquals(
                ProfileWriteResult.OutcomeUnknown(
                    ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
                resource(provider).writeSnapshot(snapshot),
            )
        }
    }

    @Test
    fun writeFailureBeforeExecutionIsKnownResourceFailure() {
        val snapshot = testSnapshot(revision = 13L)
        val provider = RecordingStorageProvider(failWriteBeforeExecution = true)

        assertEquals(
            ProfileWriteResult.ResourceFailure(
                ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
            ),
            resource(provider).writeSnapshot(snapshot),
        )
        assertEquals(listOf("writeSnapshot"), provider.operations)
        assertEquals(null, provider.payload)
    }

    private fun resource(provider: ExactProfilePersistence): ProfileResource =
        createProfileResource(provider)
}

private class RecordingStorageProvider(
    var payload: String? = null,
    private val ignoreWrite: Boolean = false,
    private val failedReads: Boolean = false,
    private val possibleWrite: Boolean = false,
    private val failWriteBeforeExecution: Boolean = false,
    private val programmingFaultOn: Set<String> = emptySet(),
) : ExactProfilePersistence {
    val operations = mutableListOf<String>()

    override fun readSnapshot(): ProfileProviderReadResult {
        operations += "readSnapshot"
        if ("readSnapshot" in programmingFaultOn) error("private provider programming fault")
        return if (failedReads) {
            ProfileProviderReadResult.Failed
        } else {
            ProfileProviderReadResult.Observed(payload)
        }
    }

    override fun writeSnapshot(payload: String): ProfileProviderMutationResult {
        operations += "writeSnapshot"
        if ("writeSnapshot" in programmingFaultOn) error("private provider programming fault")
        if (failWriteBeforeExecution) return ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION
        if (possibleWrite) return ProfileProviderMutationResult.POSSIBLE_EXECUTION
        if (!ignoreWrite) this.payload = payload
        return ProfileProviderMutationResult.COMPLETED
    }
}
