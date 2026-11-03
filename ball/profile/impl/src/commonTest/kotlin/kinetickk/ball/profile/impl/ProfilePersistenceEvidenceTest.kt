// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.profile.api.ProfileWriteFailure
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.impl.storage.requireEncoded
import kinetickk.ball.profile.impl.storage.testSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fault evidence for the local storage boundary, not a Ballarch runtime conformance suite. */
class ProfilePersistenceEvidenceTest {
    @Test
    fun writeOutcomesStayHonestAcrossDestinationFatesWithoutRepeatingTheWrite() {
        val snapshot = testSnapshot(revision = 21L)
        val payload = requireEncoded(snapshot)
        val before = requireEncoded(testSnapshot(revision = 19L))

        persistenceFates.forEach { fate ->
            val destination = EvidenceDestination(before, fate)
            val result = createProfileResource(destination).writeSnapshot(snapshot)

            val expected = when (fate.expected) {
                WriteKnowledge.CONFIRMED -> ProfileWriteResult.Written(snapshot.revision)
                WriteKnowledge.NOT_APPLIED -> ProfileWriteResult.ResourceFailure(
                    ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
                )
                WriteKnowledge.UNCERTAIN -> ProfileWriteResult.OutcomeUnknown(
                    ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                )
            }
            assertEquals(expected, result, fate.name)
            assertEquals(if (fate.applied) payload else before, destination.durablePayload, fate.name)
            assertEquals(1, destination.writes, "${fate.name}: never blindly retry a write")
            assertEquals(fate.expectedReads, destination.reads, fate.name)
        }
    }
}

private enum class WriteKnowledge { CONFIRMED, NOT_APPLIED, UNCERTAIN }
private enum class ReadEvidence { NEW_PAYLOAD, OLD_PAYLOAD, FAILED }

private data class PersistenceFate(
    val name: String,
    val applied: Boolean,
    val report: ProfileProviderMutationResult,
    val readEvidence: ReadEvidence,
    val expected: WriteKnowledge,
    val expectedReads: Int,
)

private val persistenceFates = listOf(
    PersistenceFate("applied and confirmed", true, ProfileProviderMutationResult.COMPLETED,
        ReadEvidence.NEW_PAYLOAD, WriteKnowledge.CONFIRMED, 1),
    PersistenceFate("applied but confirmation lost", true, ProfileProviderMutationResult.POSSIBLE_EXECUTION,
        ReadEvidence.NEW_PAYLOAD, WriteKnowledge.UNCERTAIN, 0),
    PersistenceFate("not applied but confirmation lost", false, ProfileProviderMutationResult.POSSIBLE_EXECUTION,
        ReadEvidence.OLD_PAYLOAD, WriteKnowledge.UNCERTAIN, 0),
    // Preferences/SharedPreferences may expose the new value in memory after a failed flush.
    // A matching read cannot upgrade the ambiguous provider report to durable success.
    PersistenceFate("cache updated but flush not confirmed", false, ProfileProviderMutationResult.POSSIBLE_EXECUTION,
        ReadEvidence.NEW_PAYLOAD, WriteKnowledge.UNCERTAIN, 0),
    PersistenceFate("refused before execution", false, ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION,
        ReadEvidence.OLD_PAYLOAD, WriteKnowledge.NOT_APPLIED, 0),
    PersistenceFate("applied but readback differs", true, ProfileProviderMutationResult.COMPLETED,
        ReadEvidence.OLD_PAYLOAD, WriteKnowledge.UNCERTAIN, 1),
    PersistenceFate("applied but readback fails", true, ProfileProviderMutationResult.COMPLETED,
        ReadEvidence.FAILED, WriteKnowledge.UNCERTAIN, 1),
)

private class EvidenceDestination(
    private val before: String,
    private val fate: PersistenceFate,
) : ExactProfilePersistence {
    var durablePayload: String = before
        private set
    var writes: Int = 0
        private set
    var reads: Int = 0
        private set
    private var receivedPayload: String? = null

    override fun writeSnapshot(payload: String): ProfileProviderMutationResult {
        writes++
        receivedPayload = payload
        if (fate.applied) durablePayload = payload
        return fate.report
    }

    override fun readSnapshot(): ProfileProviderReadResult {
        reads++
        return when (fate.readEvidence) {
            ReadEvidence.NEW_PAYLOAD -> ProfileProviderReadResult.Observed(checkNotNull(receivedPayload))
            ReadEvidence.OLD_PAYLOAD -> ProfileProviderReadResult.Observed(before)
            ReadEvidence.FAILED -> ProfileProviderReadResult.Failed
        }
    }
}
