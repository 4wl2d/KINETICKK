// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.ProfileWriteFailure
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileCompletionTest {
    @Test
    fun everyWriteOutcomeSettlesOnceWithoutRepeatingTheEffectOrRollingBackTheAcceptedProfile() {
        val mutation = pendingMutation(initial())
        val call = assertIs<ProfileOutput.PersistSnapshot>(mutation.outputs.first())
        val revision = call.snapshot.revision
        val rejection = ProfileSnapshotRejection.INCONSISTENT_PROFILE
        val failure = ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION
        val unknown = ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED
        val outcomes = listOf(
            ProfileWriteResult.Written(revision) to ProfilePersistenceStatus.Persisted(revision),
            ProfileWriteResult.Rejected(rejection) to ProfilePersistenceStatus.Rejected(revision, rejection),
            ProfileWriteResult.ResourceFailure(failure) to ProfilePersistenceStatus.ResourceFailure(revision, failure),
            ProfileWriteResult.OutcomeUnknown(unknown) to ProfilePersistenceStatus.OutcomeUnknown(revision, unknown),
        )

        for ((outcome, status) in outcomes) {
            val settled = ProfileNucleus.complete(mutation.nextState, call.effectRef, outcome)
            assertEquals(status, settled.nextState.persistence)
            assertEquals(mutation.nextState.profile, settled.nextState.profile)
            assertEquals(ProfileRevision(revision.value + 1L), settled.nextState.revision)
            assertTrue(settled.outputs.isEmpty(), "Completion must not retry $outcome")
            assertFailsWith<IllegalStateException> {
                ProfileNucleus.complete(settled.nextState, call.effectRef, outcome)
            }
        }
        assertIs<ProfilePersistenceStatus.Pending>(mutation.nextState.persistence)
    }

    @Test
    fun anOldCompletionCannotSettleANewerWriteEvenWhenItWasSuccessful() {
        val first = pendingMutation(initial())
        val oldCall = assertIs<ProfileOutput.PersistSnapshot>(first.outputs.first())
        val oldResult = ProfileWriteResult.Written(oldCall.snapshot.revision)
        val settled = ProfileNucleus.complete(first.nextState, oldCall.effectRef, oldResult)
        val second = pendingMutation(settled.nextState)
        val newCall = assertIs<ProfileOutput.PersistSnapshot>(second.outputs.first())

        assertFailsWith<IllegalStateException> {
            ProfileNucleus.complete(second.nextState, oldCall.effectRef, oldResult)
        }
        assertEquals(newCall.effectRef, assertIs<ProfilePersistenceStatus.Pending>(second.nextState.persistence).effectRef)
        val completed = ProfileNucleus.complete(
            second.nextState, newCall.effectRef, ProfileWriteResult.Written(newCall.snapshot.revision),
        )
        assertEquals(ProfilePersistenceStatus.Persisted(newCall.snapshot.revision), completed.nextState.persistence)
    }

    @Test
    fun wrongReferenceOrWrittenRevisionNeverCommitsACompletion() {
        val mutation = pendingMutation(initial())
        val call = assertIs<ProfileOutput.PersistSnapshot>(mutation.outputs.first())
        val wrongRevision = ProfileRevision(call.snapshot.revision.value + 1L)
        val wrongReference = ProfileEffectRef(wrongRevision, call.effectRef.ordinal)
        assertFailsWith<IllegalStateException> {
            ProfileNucleus.complete(mutation.nextState, wrongReference, ProfileWriteResult.Written(wrongRevision))
        }
        assertFailsWith<IllegalStateException> {
            ProfileNucleus.complete(
                mutation.nextState,
                ProfileEffectRef(call.effectRef.sourceRevision, call.effectRef.ordinal + 1),
                ProfileWriteResult.Written(call.snapshot.revision),
            )
        }
        assertFailsWith<IllegalStateException> {
            ProfileNucleus.complete(mutation.nextState, call.effectRef, ProfileWriteResult.Written(wrongRevision))
        }
        assertIs<ProfilePersistenceStatus.Pending>(mutation.nextState.persistence)
    }

    private fun initial(): ProfileState = ProfileState.initial(
        TestProfilePolicy, ProfileSnapshotReadResult.Observed(null),
    )

    private fun pendingMutation(state: ProfileState): ProfileAcceptedFrame =
        assertIs<ProfileDecision.Accepted>(ProfileNucleus.decide(state, ProfileNucleusPulse.ToggleMute)).frame
}
