// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandRef
import kinetickk.ball.profile.api.ProfileCommandRefRejection
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResetReason
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileResourceFailure
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.ProfileV4WritePurpose
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultProfileComponentTest {
    @Test
    fun bootstrapMissingLoadedInvalidAndUnknownNeverWriteFreshState() {
        val loadedProfile = representativeProfile()
        val cases = listOf(
            BootstrapCase(
                result = ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.NONE),
                expectedProfile = testDefaultProfile(),
                expectedRevision = ProfileRevision(1L),
                expectedBootstrap = ProfileBootstrapStatus.Ready,
            ),
            BootstrapCase(
                result = ProfileBootstrapResourceResult.Observed(
                    snapshot = v4Snapshot(loadedProfile, revision = 41L),
                    legacyKeys = ProfileLegacyKeys.NONE,
                ),
                expectedProfile = loadedProfile,
                expectedRevision = ProfileRevision(42L),
                expectedBootstrap = ProfileBootstrapStatus.Ready,
            ),
            BootstrapCase(
                result = ProfileBootstrapResourceResult.Rejected(
                    ProfileV4Rejection.MALFORMED_JSON,
                    ProfileLegacyKeys.NONE,
                ),
                expectedProfile = testDefaultProfile(),
                expectedRevision = ProfileRevision(1L),
                expectedBootstrap = ProfileBootstrapStatus.Blocked(
                    ProfileBootstrapBlockReason.ResetRequired(
                        ProfileResetReason.InvalidV4(ProfileV4Rejection.MALFORMED_JSON),
                    ),
                ),
            ),
            BootstrapCase(
                result = ProfileBootstrapResourceResult.OutcomeUnknown(
                    ProfileResourceFailure.PROVIDER_READ_FAILED,
                ),
                expectedProfile = testDefaultProfile(),
                expectedRevision = ProfileRevision(1L),
                expectedBootstrap = ProfileBootstrapStatus.Blocked(
                    ProfileBootstrapBlockReason.ResourceOutcomeUnknown(
                        ProfileResourceFailure.PROVIDER_READ_FAILED,
                    ),
                ),
            ),
        )

        cases.forEach { case ->
            val resource = RecordingProfileResource(case.result)
            val component = testProfileComponent(resource)

            assertEquals(1, resource.readCount)
            assertTrue(resource.writes.isEmpty())
            assertEquals(0, resource.purgeCount)
            assertProfileQueries(case.expectedProfile, component, case.expectedRevision)
            val persistence = component.query(ProfileQuery.GetPersistenceStatus)
            assertEquals(case.expectedBootstrap, persistence.bootstrap)
            val observedSnapshot = (case.result as? ProfileBootstrapResourceResult.Observed)?.snapshot
            if (observedSnapshot != null) {
                assertEquals(
                    ProfilePersistenceStatus.Persisted(observedSnapshot.revision),
                    persistence.persistence,
                )
            } else {
                assertEquals(ProfilePersistenceStatus.NotAttempted, persistence.persistence)
            }
        }

        val throwingResource = RecordingProfileResource().apply {
            readBehavior = { error("provider read") }
        }
        val throwingComponent = testProfileComponent(throwingResource)
        assertEquals(
            ProfileBootstrapStatus.Blocked(
                ProfileBootstrapBlockReason.ResourceOutcomeUnknown(
                    ProfileResourceFailure.PROVIDER_READ_FAILED,
                ),
            ),
            throwingComponent.query(ProfileQuery.GetPersistenceStatus).bootstrap,
        )
        assertTrue(throwingResource.writes.isEmpty())
    }

    @Test
    fun ordinaryMutationPublishesBeforeOneWriteAndAdvancesBusinessAndCompletionRevisions() {
        val resource = RecordingProfileResource()
        lateinit var component: DefaultProfileComponent
        var observedPending: ProfilePersistenceStatus.Pending? = null
        resource.beforeWrite = { snapshot ->
            assertProfileQueries(snapshot.profile, component, snapshot.revision)
            val persistence = component.query(ProfileQuery.GetPersistenceStatus).persistence
            observedPending = assertIs<ProfilePersistenceStatus.Pending>(persistence)
            assertEquals(snapshot.revision, observedPending.snapshotRevision)
            assertEquals(ProfileV4WritePurpose.MUTATION, observedPending.purpose)
        }
        component = testProfileComponent(resource)
        resource.events.clear()

        val acceptance = assertIs<ProfileAcceptance.Accepted>(
            component.accept(
                ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ),
        )

        assertEquals(ProfileRevision(2L), acceptance.revision)
        assertEquals(listOf("write"), resource.events)
        assertEquals(1, resource.writes.size)
        assertFalse(resource.writes.single().profile.preferences.soundEnabled)
        assertProfileQueries(resource.writes.single().profile, component, ProfileRevision(3L))
        assertEquals(
            ProfilePersistenceStatus.Persisted(ProfileRevision(2L)),
            component.query(ProfileQuery.GetPersistenceStatus).persistence,
        )
        assertEquals(ProfileRevision(2L), assertNotNull(observedPending).effectRef.sourceRevision)
    }

    @Test
    fun rejectedAndUnknownWritesDoNotRollbackOrBlindlyRetryAcceptedMutation() {
        val results = listOf(
            ProfileV4WriteResult.Rejected(ProfileV4Rejection.VALUE_OUT_OF_RANGE),
            ProfileV4WriteResult.OutcomeUnknown(
                ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        )

        results.forEach { writeResult ->
            val resource = RecordingProfileResource().apply {
                writeBehavior = { writeResult }
            }
            val component = testProfileComponent(resource)

            assertIs<ProfileAcceptance.Accepted>(component.accept(ProfilePulse.ToggleMute))

            val preferences = component.query(ProfileQuery.GetPreferences)
            assertFalse(preferences.preferences.soundEnabled)
            assertFalse(preferences.preferences.musicEnabled)
            assertEquals(ProfileRevision(3L), preferences.revision)
            assertEquals(1, resource.writes.size)
            val persistence = component.query(ProfileQuery.GetPersistenceStatus).persistence
            when (writeResult) {
                is ProfileV4WriteResult.Rejected -> assertEquals(
                    ProfilePersistenceStatus.Rejected(ProfileRevision(2L), writeResult.reason),
                    persistence,
                )
                is ProfileV4WriteResult.OutcomeUnknown -> assertEquals(
                    ProfilePersistenceStatus.OutcomeUnknown(ProfileRevision(2L), writeResult.reason),
                    persistence,
                )
                is ProfileV4WriteResult.Written -> error("Not a failure case")
            }

            repeat(3) { component.query(ProfileQuery.GetPersistenceStatus) }
            assertEquals(1, resource.writes.size)
        }
    }

    @Test
    fun reentrantWriteCallbackCannotRecursivelyAcceptAnotherPulse() {
        val resource = RecordingProfileResource()
        lateinit var component: DefaultProfileComponent
        var recursiveFailure: Throwable? = null
        resource.beforeWrite = {
            recursiveFailure = runCatching {
                component.accept(ProfilePulse.ToggleMute)
            }.exceptionOrNull()
        }
        component = testProfileComponent(resource)

        assertIs<ProfileAcceptance.Accepted>(
            component.accept(
                ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ),
        )

        assertIs<IllegalStateException>(recursiveFailure)
        assertEquals(1, resource.writes.size)
        val preferences = component.query(ProfileQuery.GetPreferences).preferences
        assertFalse(preferences.soundEnabled)
        assertTrue(preferences.musicEnabled)
    }

    @Test
    fun commandCompletionIsEmittedAfterWriteAttemptAndKeepsBusinessRevision() {
        val events = mutableListOf<String>()
        val completions = mutableListOf<ProfileCommandResult.Accepted>()
        val resource = RecordingProfileResource().apply {
            beforeWrite = { events += "write" }
        }
        val component = testProfileComponent(resource) { result ->
            events += "completion"
            completions += result
        }
        val ref = ProfileCommandRef(
            sourceInstance = ProfileCommandSource.LocalSession,
            targetInstance = component.instanceId,
            sourceRevision = 7L,
            ordinal = 3,
        )
        val command = ProfileCommand(ref, ProfilePulse.ToggleMute)

        val acceptance = assertIs<ProfileAcceptance.Accepted>(
            component.accept(command, ProfileCommandAdmission(ref)),
        )

        assertEquals(listOf("write", "completion"), events)
        val completion = completions.single()
        assertEquals(ref, completion.commandRef)
        assertEquals(acceptance.revision, completion.targetRevision)
        assertIs<ProfileCommandOutcome.PreferencesChanged>(completion.outcome)
        assertEquals(ProfileRevision(3L), component.query(ProfileQuery.GetPreferences).revision)
    }

    @Test
    fun sessionCoreSelectionPublishesAndWritesBeforeItsExactAcceptedCompletion() {
        val initial = testDefaultProfile().copy(
            economy = PlayerEconomy(matter = 7L, lifetimeMatter = 90L),
        )
        val resource = RecordingProfileResource(
            ProfileBootstrapResourceResult.Observed(
                snapshot = v4Snapshot(profile = initial, revision = 10L),
                legacyKeys = ProfileLegacyKeys.NONE,
            ),
        )
        val events = mutableListOf<String>()
        val completions = mutableListOf<ProfileCommandResult.Accepted>()
        lateinit var component: DefaultProfileComponent
        resource.beforeWrite = { snapshot ->
            events += "write"
            assertEquals(CoreShape.PRISM, snapshot.profile.loadout.coreShape)
            val published = component.query(ProfileQuery.GetLoadout)
            assertEquals(snapshot.revision, published.revision)
            assertEquals(CoreShape.PRISM, published.snapshot.loadout.coreShape)
        }
        component = testProfileComponent(resource) { result ->
            events += "completion"
            completions += result
            val published = component.query(ProfileQuery.GetLoadout)
            assertEquals(result.targetRevision, published.revision)
            assertEquals(CoreShape.PRISM, published.snapshot.loadout.coreShape)
        }
        val ref = ProfileCommandRef(
            sourceInstance = ProfileCommandSource.LocalSession,
            targetInstance = component.instanceId,
            sourceRevision = 22L,
            ordinal = 5,
        )
        val command = ProfileCommand(ref, ProfilePulse.SelectCoreShape(CoreShape.PRISM))

        val acceptance = assertIs<ProfileAcceptance.Accepted>(
            component.accept(command, ProfileCommandAdmission(ref)),
        )

        assertEquals(ProfileRevision(12L), acceptance.revision)
        assertEquals(listOf("write", "completion"), events)
        val completion = completions.single()
        assertEquals(ref, completion.commandRef)
        assertEquals(acceptance.revision, completion.targetRevision)
        assertEquals(
            ProfileCommandOutcome.CoreShapeSelected(CoreShape.PRISM),
            completion.outcome,
        )
        assertEquals(1, resource.writes.size)
        assertEquals(ProfileRevision(13L), component.query(ProfileQuery.GetLoadout).revision)
        assertEquals(initial.economy, component.query(ProfileQuery.GetLoadout).snapshot.economy)

        val beforeRejected = component.stateSnapshot()
        val noChangeRef = ref.copy(sourceRevision = 23L, ordinal = 6)
        val noChange = ProfileCommand(
            noChangeRef,
            ProfilePulse.SelectCoreShape(CoreShape.PRISM),
        )
        val noChangeAcceptance = assertIs<ProfileAcceptance.Rejected>(
            component.accept(noChange, ProfileCommandAdmission(noChangeRef)),
        )
        assertEquals(ProfileRejection.NoChange, noChangeAcceptance.reason)
        assertEquals(beforeRejected, component.stateSnapshot())
        assertEquals(1, resource.writes.size)
        assertEquals(1, completions.size)

        val wrongSourceRef = ref.copy(
            sourceInstance = ProfileCommandSource.GameplayRun(4L),
            sourceRevision = 24L,
            ordinal = 7,
        )
        val wrongSource = ProfileCommand(
            wrongSourceRef,
            ProfilePulse.SelectCoreShape(CoreShape.SHARD),
        )
        val wrongSourceAcceptance = assertIs<ProfileAcceptance.Rejected>(
            component.accept(wrongSource, ProfileCommandAdmission(wrongSourceRef)),
        )
        assertEquals(
            ProfileRejection.InvalidCommandRef(ProfileCommandRefRejection.WRONG_SOURCE_KIND),
            wrongSourceAcceptance.reason,
        )
        assertEquals(beforeRejected, component.stateSnapshot())
        assertEquals(1, resource.writes.size)
        assertEquals(1, completions.size)
    }

    @Test
    fun lockedSessionCoreSelectionRejectsAtomicallyWithoutCompletion() {
        val resource = RecordingProfileResource()
        val completions = mutableListOf<ProfileCommandResult.Accepted>()
        val component = testProfileComponent(resource, commandResultSink = completions::add)
        val ref = ProfileCommandRef(
            sourceInstance = ProfileCommandSource.LocalSession,
            targetInstance = component.instanceId,
            sourceRevision = 2L,
            ordinal = 0,
        )
        val command = ProfileCommand(ref, ProfilePulse.SelectCoreShape(CoreShape.PRISM))
        val before = component.stateSnapshot()

        val rejection = assertIs<ProfileAcceptance.Rejected>(
            component.accept(command, ProfileCommandAdmission(ref)),
        )

        assertEquals(ProfileRejection.CoreShapeLocked, rejection.reason)
        assertEquals(before.revision, rejection.observedRevision)
        assertEquals(before, component.stateSnapshot())
        assertTrue(resource.writes.isEmpty())
        assertTrue(completions.isEmpty())
    }

    @Test
    fun mismatchedWriteRevisionFailsTrustedEffectCorrelationWithoutSecondWrite() {
        val resource = RecordingProfileResource().apply {
            writeBehavior = { snapshot ->
                ProfileV4WriteResult.Written(ProfileRevision(snapshot.revision.value + 1L))
            }
        }
        val component = testProfileComponent(resource)

        assertFailsWith<IllegalStateException> {
            component.accept(
                ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleMusic),
            )
        }

        assertEquals(1, resource.writes.size)
        assertFalse(component.query(ProfileQuery.GetPreferences).preferences.musicEnabled)
        val pending = assertIs<ProfilePersistenceStatus.Pending>(
            component.query(ProfileQuery.GetPersistenceStatus).persistence,
        )
        assertEquals(
            ProfileEffectRef(ProfileRevision(2L), ordinal = 0),
            pending.effectRef,
        )
        assertEquals(ProfileRevision(2L), component.query(ProfileQuery.GetPreferences).revision)
    }

    @Test
    fun resetRequiredStateWaitsForExplicitConfirmationWithoutAnyEffect() {
        val resource = RecordingProfileResource(
            ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
        )
        val component = testProfileComponent(resource)
        resource.events.clear()

        repeat(3) {
            val run = component.query(ProfileQuery.GetRunBootstrap)
            assertIs<ProfileRunBootstrapResult.Unavailable>(run.result)
            component.query(ProfileQuery.GetPersistenceStatus)
        }

        assertTrue(resource.events.isEmpty())
        assertTrue(resource.writes.isEmpty())
        assertEquals(0, resource.purgeCount)
        assertEquals(
            ProfileResetStatus.ConfirmationRequired(
                ProfileResetReason.LegacyDataDetected,
                ProfileLegacyKeys.ALL,
            ),
            component.query(ProfileQuery.GetPersistenceStatus).reset,
        )
    }

    @Test
    fun confirmedResetWritesFreshV4BeforePurgeAndCorrelatesBothEffects() {
        val resource = RecordingProfileResource(
            ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
        )
        lateinit var component: DefaultProfileComponent
        var writeEffect: ProfileEffectRef? = null
        var purgeEffect: ProfileEffectRef? = null
        resource.beforeWrite = { snapshot ->
            assertTrue(snapshot.legacyResetConfirmed)
            assertEquals(testDefaultProfile(), snapshot.profile)
            assertEquals(0, resource.purgeCount)
            writeEffect = assertIs<ProfilePersistenceStatus.Pending>(
                component.query(ProfileQuery.GetPersistenceStatus).persistence,
            ).effectRef
        }
        resource.beforePurge = {
            assertEquals(1, resource.writes.size)
            purgeEffect = assertIs<ProfileResetStatus.PurgingLegacy>(
                component.query(ProfileQuery.GetPersistenceStatus).reset,
            ).effectRef
        }
        component = testProfileComponent(resource)
        resource.events.clear()

        val acceptance = assertIs<ProfileAcceptance.Accepted>(
            component.accept(ProfilePulse.ConfirmLegacyReset),
        )

        assertEquals(ProfileRevision(2L), acceptance.revision)
        assertEquals(listOf("write", "purge"), resource.events)
        assertEquals(ProfileEffectRef(ProfileRevision(2L), 0), writeEffect)
        assertEquals(ProfileEffectRef(ProfileRevision(3L), 1), purgeEffect)
        assertProfileQueries(testDefaultProfile(), component, ProfileRevision(4L))
        val status = component.query(ProfileQuery.GetPersistenceStatus)
        assertEquals(ProfileBootstrapStatus.Ready, status.bootstrap)
        assertEquals(ProfileResetStatus.NotRequired(legacyResetConfirmed = true), status.reset)
        assertEquals(ProfilePersistenceStatus.Persisted(ProfileRevision(2L)), status.persistence)
    }

    @Test
    fun rejectedOrUnknownResetWritePreservesLegacyAndNeverPurges() {
        val results = listOf(
            ProfileV4WriteResult.Rejected(ProfileV4Rejection.INCONSISTENT_PROFILE),
            ProfileV4WriteResult.OutcomeUnknown(
                ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        )

        results.forEach { result ->
            val resource = RecordingProfileResource(
                ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
            ).apply {
                writeBehavior = { result }
            }
            val component = testProfileComponent(resource)

            assertIs<ProfileAcceptance.Accepted>(component.accept(ProfilePulse.ConfirmLegacyReset))

            assertEquals(1, resource.writes.size)
            assertEquals(0, resource.purgeCount)
            assertTrue(resource.writes.single().legacyResetConfirmed)
            assertEquals(
                ProfileResetStatus.ConfirmationRequired(
                    ProfileResetReason.LegacyDataDetected,
                    ProfileLegacyKeys.ALL,
                ),
                component.query(ProfileQuery.GetPersistenceStatus).reset,
            )
            repeat(2) { component.query(ProfileQuery.GetPersistenceStatus) }
            assertEquals(1, resource.writes.size)
            assertEquals(0, resource.purgeCount)
        }
    }

    @Test
    fun partialAndUnknownPurgeResultsEnterNeedsAttentionWithoutAutomaticRetry() {
        val results = listOf(
            ProfileLegacyPurgeResult.Partial(
                ProfileLegacyKeys(progressV2 = true, matter = false),
            ),
            ProfileLegacyPurgeResult.OutcomeUnknown(
                remaining = ProfileLegacyKeys(progressV2 = true, matter = false),
                unknown = ProfileLegacyKeys(progressV2 = false, matter = true),
                reason = ProfileResourceFailure.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
            ),
        )

        results.forEach { purgeResult ->
            val resource = RecordingProfileResource(
                ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
            ).apply {
                purgeBehavior = { purgeResult }
            }
            val component = testProfileComponent(resource)

            assertIs<ProfileAcceptance.Accepted>(component.accept(ProfilePulse.ConfirmLegacyReset))

            val status = component.query(ProfileQuery.GetPersistenceStatus)
            val attention = assertIs<ProfileResetStatus.NeedsAttention>(status.reset)
            assertEquals(purgeResult, attention.result)
            assertIs<ProfileBootstrapBlockReason.ResetNeedsAttention>(
                assertIs<ProfileBootstrapStatus.Blocked>(status.bootstrap).reason,
            )
            assertEquals(1, resource.writes.size)
            assertEquals(1, resource.purgeCount)
            repeat(3) { component.query(ProfileQuery.GetPersistenceStatus) }
            assertEquals(1, resource.purgeCount)
        }
    }

    @Test
    fun confirmedRestartNeverRewritesAndOnlyExplicitRetryPurgesRemainingLegacy() {
        val loaded = representativeProfile()
        val resource = RecordingProfileResource(
            ProfileBootstrapResourceResult.Observed(
                snapshot = v4Snapshot(
                    profile = loaded,
                    revision = 40L,
                    legacyResetConfirmed = true,
                ),
                legacyKeys = ProfileLegacyKeys.ALL,
            ),
        )
        val component = testProfileComponent(resource)

        assertProfileQueries(loaded, component, ProfileRevision(41L))
        assertIs<ProfileResetStatus.NeedsAttention>(
            component.query(ProfileQuery.GetPersistenceStatus).reset,
        )
        assertTrue(resource.writes.isEmpty())
        assertEquals(0, resource.purgeCount)
        repeat(2) { component.query(ProfileQuery.GetPersistenceStatus) }
        assertEquals(0, resource.purgeCount)

        val acceptance = assertIs<ProfileAcceptance.Accepted>(
            component.accept(ProfilePulse.RetryLegacyPurge),
        )

        assertEquals(ProfileRevision(42L), acceptance.revision)
        assertTrue(resource.writes.isEmpty())
        assertEquals(1, resource.purgeCount)
        assertProfileQueries(loaded, component, ProfileRevision(43L))
        val status = component.query(ProfileQuery.GetPersistenceStatus)
        assertEquals(ProfileBootstrapStatus.Ready, status.bootstrap)
        assertEquals(ProfileResetStatus.NotRequired(legacyResetConfirmed = true), status.reset)
    }
}

private data class BootstrapCase(
    val result: ProfileBootstrapResourceResult,
    val expectedProfile: kinetickk.ball.profile.api.PlayerProfile,
    val expectedRevision: ProfileRevision,
    val expectedBootstrap: ProfileBootstrapStatus,
)
