// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.ProfileWriteFailure
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.api.ProfileSettings
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileRebirthAdvanced
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultProfileComponentTest {
    @Test
    fun gameplayProgressCapabilityPublishesEveryCapturedFieldBeforeSaveAndReply() {
        val initial = representativeProfile().copy(rebirthProgress = RebirthProgress(2, 1))
        val resource = RecordingProfileResource(ProfileSnapshotReadResult.Observed(profileSnapshot(initial)))
        val component = testProfileComponent(resource)
        val caller = ProfileCommandTestCaller<ProfileProgressApplied>()
        val update = GameplayProgressUpdate(
            bankedMatter = 7L,
            discoveredItemIds = setOf(1, 3),
            clearedRebirthLevel = 2,
            eliteKills = 5,
            dashHits = 8,
            completedOrbits = 13,
            architectDefeatedWith = CoreShape.ORB,
        )

        caller.call { reply ->
            resource.beforeWrite = { snapshot ->
                reply.checkAvailable()
                assertEquals(snapshot.profile, component.stateSnapshot().profile)
                assertEquals(snapshot.revision, component.stateSnapshot().revision)
                assertIs<ProfilePersistenceStatus.Pending>(component.stateSnapshot().persistence)
            }
            component.applyGameplayProgress(update, reply)
        }

        val result = caller.changed.single()
        val next = queriedProfile(component)
        assertEquals(initial.economy.matter + 7L, next.economy.matter)
        assertEquals(initial.economy.lifetimeMatter + 7L, next.economy.lifetimeMatter)
        assertEquals(initial.collection.discoveredItemIds + setOf(1, 3), next.collection.discoveredItemIds)
        assertEquals(RebirthProgress(2, 2), next.rebirthProgress)
        assertEquals(initial.characterAchievements.eliteKills + 5L, next.characterAchievements.eliteKills)
        assertEquals(initial.characterAchievements.dashHits + 8L, next.characterAchievements.dashHits)
        assertEquals(initial.characterAchievements.completedOrbits + 13L, next.characterAchievements.completedOrbits)
        assertEquals(initial.characterAchievements.architectVictories + 1L, next.characterAchievements.architectVictories)
        assertEquals(initial.characterAchievements.victoriousCharacters + CoreShape.ORB, next.characterAchievements.victoriousCharacters)
        assertEquals(initial.copy(
            economy = next.economy,
            collection = next.collection,
            rebirthProgress = next.rebirthProgress,
            characterAchievements = next.characterAchievements,
        ), next)
        assertEquals(result.revision, resource.writes.single().revision)
        assertEquals(ProfilePersistenceStatus.Persisted(result.revision), component.stateSnapshot().persistence)
        assertTrue(caller.refused.isEmpty())
    }

    @Test
    fun gameplayProgressAcceptedResultSurvivesSaveFaultWithoutRollback() {
        val fault = IllegalStateException("save failed after gameplay progress acceptance")
        val initial = representativeProfile()
        val resource = RecordingProfileResource(ProfileSnapshotReadResult.Observed(profileSnapshot(initial)))
            .apply { writeBehavior = { throw fault } }
        val component = testProfileComponent(resource)
        val caller = ProfileCommandTestCaller<ProfileProgressApplied>()

        assertEquals(fault, assertFailsWith<IllegalStateException> {
            caller.call { reply -> component.applyGameplayProgress(GameplayProgressUpdate(bankedMatter = 7L), reply) }
        })

        assertEquals(component.stateSnapshot().revision, caller.changed.single().revision)
        assertEquals(initial.economy.matter + 7L, component.stateSnapshot().profile.economy.matter)
        assertEquals(initial.economy.lifetimeMatter + 7L, component.stateSnapshot().profile.economy.lifetimeMatter)
        assertIs<ProfilePersistenceStatus.Pending>(component.stateSnapshot().persistence)
        assertTrue(caller.refused.isEmpty())
        assertEquals(1, resource.writes.size)
    }

    @Test
    fun gameplayProgressSaveFailureAndUnknownOutcomeRetainTheAcceptedMutation() {
        listOf(
            ProfileWriteResult.ResourceFailure(ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION),
            ProfileWriteResult.OutcomeUnknown(ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED),
        ).forEach { writeResult ->
            val resource = RecordingProfileResource().apply { writeBehavior = { writeResult } }
            val component = testProfileComponent(resource)
            val caller = ProfileCommandTestCaller<ProfileProgressApplied>()

            caller.call { reply -> component.applyGameplayProgress(GameplayProgressUpdate(bankedMatter = 7L), reply) }

            val result = caller.changed.single()
            assertEquals(7L, component.stateSnapshot().profile.economy.matter)
            assertEquals(7L, component.stateSnapshot().profile.economy.lifetimeMatter)
            assertTrue(caller.refused.isEmpty())
            assertEquals(1, resource.writes.size)
            when (writeResult) {
                is ProfileWriteResult.ResourceFailure -> assertEquals(
                    ProfilePersistenceStatus.ResourceFailure(result.revision, writeResult.reason),
                    component.stateSnapshot().persistence,
                )
                is ProfileWriteResult.OutcomeUnknown -> assertEquals(
                    ProfilePersistenceStatus.OutcomeUnknown(result.revision, writeResult.reason),
                    component.stateSnapshot().persistence,
                )
                else -> error("Not a failure case")
            }
        }
    }

    @Test
    fun rebirthCapabilityPublishesAndSavesBeforeCompletingItsOwnedResult() {
        val initial = representativeProfile()
        val resource = RecordingProfileResource(ProfileSnapshotReadResult.Observed(profileSnapshot(initial)))
        val component = testProfileComponent(resource)
        val before = component.stateSnapshot()
        val caller = ProfileCommandTestCaller<ProfileRebirthAdvanced>()

        caller.call { reply ->
            resource.beforeWrite = { snapshot ->
                reply.checkAvailable()
                assertEquals(snapshot.profile, component.stateSnapshot().profile)
                assertEquals(snapshot.revision, component.stateSnapshot().revision)
                assertIs<ProfilePersistenceStatus.Pending>(component.stateSnapshot().persistence)
            }
            component.advanceRebirth(reply)
        }

        val result = caller.changed.single()
        assertEquals(ProfileRevision(before.revision.value + 1L), result.revision)
        assertEquals(RebirthProgress(3, 2), result.progress)
        assertEquals(initial.copy(rebirthProgress = result.progress), queriedProfile(component))
        assertEquals(result.revision, resource.writes.single().revision)
        assertEquals(ProfilePersistenceStatus.Persisted(result.revision), component.stateSnapshot().persistence)
        assertEquals(ProfileRevision(before.revision.value + 2L), component.stateSnapshot().revision)
        assertTrue(caller.refused.isEmpty())
    }

    @Test
    fun rebirthAcceptedResultSurvivesSaveFaultWithoutRollback() {
        val fault = IllegalStateException("save failed after rebirth acceptance")
        val initial = representativeProfile()
        val resource = RecordingProfileResource(ProfileSnapshotReadResult.Observed(profileSnapshot(initial)))
            .apply { writeBehavior = { throw fault } }
        val component = testProfileComponent(resource)
        val caller = ProfileCommandTestCaller<ProfileRebirthAdvanced>()

        assertEquals(fault, assertFailsWith<IllegalStateException> { caller.call(component::advanceRebirth) })

        val result = caller.changed.single()
        assertEquals(initial.copy(rebirthProgress = RebirthProgress(3, 2)), component.stateSnapshot().profile)
        assertEquals(component.stateSnapshot().revision, result.revision)
        assertEquals(component.stateSnapshot().profile.rebirthProgress, result.progress)
        assertIs<ProfilePersistenceStatus.Pending>(component.stateSnapshot().persistence)
        assertTrue(caller.refused.isEmpty())
        assertEquals(1, resource.writes.size)
    }

    @Test
    fun rebirthSaveFailureAndUnknownOutcomeRetainTheAcceptedProgress() {
        listOf(
            ProfileWriteResult.ResourceFailure(ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION),
            ProfileWriteResult.OutcomeUnknown(ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED),
        ).forEach { writeResult ->
            val initial = representativeProfile()
            val resource = RecordingProfileResource(ProfileSnapshotReadResult.Observed(profileSnapshot(initial)))
                .apply { writeBehavior = { writeResult } }
            val component = testProfileComponent(resource)
            val caller = ProfileCommandTestCaller<ProfileRebirthAdvanced>()

            caller.call(component::advanceRebirth)

            val result = caller.changed.single()
            assertEquals(initial.copy(rebirthProgress = RebirthProgress(3, 2)), queriedProfile(component))
            assertEquals(RebirthProgress(3, 2), result.progress)
            assertTrue(caller.refused.isEmpty())
            assertEquals(1, resource.writes.size)
            when (writeResult) {
                is ProfileWriteResult.ResourceFailure -> assertEquals(
                    ProfilePersistenceStatus.ResourceFailure(result.revision, writeResult.reason),
                    component.stateSnapshot().persistence,
                )
                is ProfileWriteResult.OutcomeUnknown -> assertEquals(
                    ProfilePersistenceStatus.OutcomeUnknown(result.revision, writeResult.reason),
                    component.stateSnapshot().persistence,
                )
                else -> error("Not a failure case")
            }
        }
    }

    @Test
    fun muteCapabilityPublishesBeforeSaveAndCompletesThroughTheSharedBinding() {
        val resource = RecordingProfileResource()
        val caller = ProfileCommandTestCaller<ProfileSettingsChanged>()
        lateinit var component: DefaultProfileComponent
        resource.beforeWrite = { snapshot ->
            assertEquals(snapshot.profile.preferences, component.query(ProfileQuery.GetPreferences).preferences)
            assertTrue(caller.changed.isEmpty())
            assertIs<ProfilePersistenceStatus.Pending>(component.stateSnapshot().persistence)
        }
        component = testProfileComponent(resource)

        caller.call(component::toggleMute)

        val result = caller.changed.single()
        assertEquals(ProfileRevision(2), result.revision)
        assertFalse(result.preferences.soundEnabled)
        assertFalse(result.preferences.musicEnabled)
        assertEquals(result.preferences, component.query(ProfileQuery.GetPreferences).preferences)
        assertEquals(ProfileRevision(3), component.stateSnapshot().revision)
        assertEquals(1, resource.writes.size)
        assertTrue(caller.refused.isEmpty())

        resource.beforeWrite = null
        val additionalConsumer = ProfileCommandTestCaller<ProfileSettingsChanged>()
        additionalConsumer.call(component::toggleMute)
        assertTrue(additionalConsumer.changed.single().preferences.soundEnabled)
        assertEquals(2, resource.writes.size)
    }

    @Test
    fun muteAcceptedResultSurvivesSaveFaultWithoutRollback() {
        val fault = IllegalStateException("save failed after Profile acceptance")
        val resource = RecordingProfileResource().apply { writeBehavior = { throw fault } }
        val component = testProfileComponent(resource)
        val caller = ProfileCommandTestCaller<ProfileSettingsChanged>()

        assertEquals(fault, assertFailsWith<IllegalStateException> { caller.call(component::toggleMute) })

        assertEquals(ProfileRevision(2), caller.changed.single().revision)
        assertFalse(caller.changed.single().preferences.soundEnabled)
        assertTrue(caller.refused.isEmpty())
        assertEquals(caller.changed.single().preferences, component.stateSnapshot().profile.preferences)
        assertIs<ProfilePersistenceStatus.Pending>(component.stateSnapshot().persistence)
        assertEquals(1, resource.writes.size)
    }

    @Test
    fun muteScopeRejectsLateOrRepeatedUseBeforeAnotherProfileAcceptance() {
        val resource = RecordingProfileResource()
        val component = testProfileComponent(resource)
        lateinit var retained: InlineReply<ProfileSettingsChanged, ProfileRefusal>
        val capture = object : ProfileSettings {
            override fun toggleMute(reply: InlineReply<ProfileSettingsChanged, ProfileRefusal>) {
                retained = reply
                component.toggleMute(reply)
                assertFailsWith<IllegalStateException> { component.toggleMute(reply) }
            }
        }
        val caller = ProfileCommandTestCaller<ProfileSettingsChanged>()
        caller.call(capture::toggleMute)
        val before = component.stateSnapshot()

        assertFailsWith<IllegalStateException> { component.toggleMute(retained) }
        assertFailsWith<IllegalStateException> { retained.accepted(caller.changed.single()) }
        assertEquals(before, component.stateSnapshot())
        assertEquals(1, resource.writes.size)
    }

    @Test
    fun muteTypedRefusalPreservesStateAndDoesNotRunPersistence() {
        val resource = RecordingProfileResource(
            ProfileSnapshotReadResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
        )
        val component = testProfileComponent(resource)
        val before = component.stateSnapshot()
        val caller = ProfileCommandTestCaller<ProfileSettingsChanged>()

        caller.call(component::toggleMute)

        assertEquals(
            ProfileRefusal.DecisionRejected(ProfileRejection.BootstrapNotReady),
            caller.refused.single(),
        )
        assertTrue(caller.changed.isEmpty())
        assertEquals(before, component.stateSnapshot())
        assertTrue(resource.writes.isEmpty())
    }

    @Test
    fun muteReentrantInvocationReturnsBusyAndCannotAcceptAnotherMutation() {
        val resource = RecordingProfileResource()
        val nested = ProfileCommandTestCaller<ProfileSettingsChanged>()
        lateinit var component: DefaultProfileComponent
        resource.beforeWrite = { nested.call(component::toggleMute) }
        component = testProfileComponent(resource)
        val root = ProfileCommandTestCaller<ProfileSettingsChanged>()

        root.call(component::toggleMute)

        assertEquals(ProfileRefusal.Busy, nested.refused.single())
        assertTrue(nested.changed.isEmpty())
        assertEquals(1, root.changed.size)
        assertEquals(1, resource.writes.size)
    }

    @Test
    fun selectedLanguageIsPersistedRestoredAndPublishedThroughPreferenceQuery() {
        val resource = RecordingProfileResource()
        val component = testProfileComponent(resource)
        val selectEnglish = ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.SetLanguage(AppLanguage.English))

        assertIs<ProfileAcceptance.Accepted>(component.accept(selectEnglish))
        assertEquals(AppLanguage.English, component.query(ProfileQuery.GetPreferences).preferences.language)
        val persisted = resource.writes.single()
        assertEquals(AppLanguage.English, persisted.profile.preferences.language)
        assertEquals(ProfileRejection.NoChange, assertIs<ProfileAcceptance.Rejected>(
            component.accept(selectEnglish),
        ).reason)
        assertEquals(1, resource.writes.size)

        val restored = testProfileComponent(RecordingProfileResource(ProfileSnapshotReadResult.Observed(persisted)))
        assertEquals(AppLanguage.English, restored.query(ProfileQuery.GetPreferences).preferences.language)
        assertIs<ProfileAcceptance.Accepted>(restored.accept(ProfilePulse.AdjustPreference(
            ProfilePreferenceAdjustment.SetLanguage(AppLanguage.Russian),
        )))
        assertEquals(AppLanguage.Russian, restored.query(ProfileQuery.GetPreferences).preferences.language)
    }

    @Test
    fun commandRevisionAdmissionReservesTheAcceptedInlineChain() {
        assertTrue(hasProfileCommandRevisionCapacity(ProfileRevision(Long.MAX_VALUE - 2L)))
        assertFalse(hasProfileCommandRevisionCapacity(ProfileRevision(Long.MAX_VALUE - 1L)))
    }

    @Test
    fun highestLoadableSnapshotReservesOneCompleteMutation() {
        val loadedProfile = representativeProfile()
        val resource = RecordingProfileResource(
            ProfileSnapshotReadResult.Observed(
                profileSnapshot(loadedProfile, revision = Long.MAX_VALUE - 3L),
            ),
        )
        val component = testProfileComponent(resource)

        assertProfileQueries(loadedProfile, component, ProfileRevision(Long.MAX_VALUE - 2L))
        val acceptance = assertIs<ProfileAcceptance.Accepted>(
            component.accept(
                ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ),
        )

        assertEquals(ProfileRevision(Long.MAX_VALUE - 1L), acceptance.revision)
        assertEquals(ProfileRevision(Long.MAX_VALUE - 1L), resource.writes.single().revision)
        assertEquals(
            ProfilePersistenceStatus.Persisted(ProfileRevision(Long.MAX_VALUE - 1L)),
            component.query(ProfileQuery.GetPersistenceStatus).persistence,
        )
        assertEquals(ProfileRevision(Long.MAX_VALUE), component.stateSnapshot().revision)
    }

    @Test
    fun constructionReadsOnceAndNeverWritesFreshState() {
        val loadedProfile = representativeProfile()
        val malformed = ProfileSnapshotReadResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        val incompatible = ProfileSnapshotReadResult.Observed(
            profileSnapshot(
                profile = testDefaultProfile().copy(
                    economy = PlayerEconomy(matter = 1L, lifetimeMatter = 0L),
                ),
            ),
        )
        val cases = listOf(
            BootstrapCase(
                result = ProfileSnapshotReadResult.Observed(null),
                expectedProfile = testDefaultProfile(),
                expectedRevision = ProfileRevision(1L),
                expectedBootstrap = ProfileBootstrapStatus.Ready,
            ),
            BootstrapCase(
                result = ProfileSnapshotReadResult.Observed(
                    profileSnapshot(loadedProfile, revision = 41L),
                ),
                expectedProfile = loadedProfile,
                expectedRevision = ProfileRevision(42L),
                expectedBootstrap = ProfileBootstrapStatus.Ready,
            ),
            BootstrapCase(
                result = malformed,
                expectedProfile = testDefaultProfile(),
                expectedRevision = ProfileRevision(1L),
                expectedBootstrap = ProfileBootstrapStatus.Ready,
            ),
            BootstrapCase(
                result = incompatible,
                expectedProfile = testDefaultProfile(),
                expectedRevision = ProfileRevision(1L),
                expectedBootstrap = ProfileBootstrapStatus.Ready,
            ),
            BootstrapCase(
                result = ProfileSnapshotReadResult.ResourceFailure(
                    ProfileReadFailure.PROVIDER_READ_FAILED,
                ),
                expectedProfile = testDefaultProfile(),
                expectedRevision = ProfileRevision(1L),
                expectedBootstrap = ProfileBootstrapStatus.Blocked(
                    ProfileBootstrapBlockReason.ResourceFailure(
                        ProfileReadFailure.PROVIDER_READ_FAILED,
                    ),
                ),
            ),
        )

        cases.forEach { case ->
            val resource = RecordingProfileResource(case.result)
            val component = testProfileComponent(resource)

            assertEquals(1, resource.readCount)
            assertTrue(resource.writes.isEmpty())
            assertProfileQueries(case.expectedProfile, component, case.expectedRevision)
            assertEquals(
                case.expectedBootstrap,
                component.query(ProfileQuery.GetPersistenceStatus).bootstrap,
            )
        }
    }

    @Test
    fun localMutationPublishesBeforeOneResourceFactAndAdvancesBothRevisions() {
        val resource = RecordingProfileResource()
        lateinit var component: DefaultProfileComponent
        var observedPending: ProfilePersistenceStatus.Pending? = null
        resource.beforeWrite = { snapshot ->
            assertProfileQueries(snapshot.profile, component, snapshot.revision)
            observedPending = assertIs<ProfilePersistenceStatus.Pending>(
                component.query(ProfileQuery.GetPersistenceStatus).persistence,
            )
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
        assertEquals(0, assertNotNull(observedPending).effectRef.ordinal)
    }

    @Test
    fun rejectedAndUnknownWritesDoNotRollbackOrRetryAcceptedMutation() {
        listOf<ProfileWriteResult>(
            ProfileWriteResult.Rejected(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            ProfileWriteResult.ResourceFailure(
                ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
            ),
            ProfileWriteResult.OutcomeUnknown(
                ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        ).forEach { writeResult ->
            val resource = RecordingProfileResource().apply { writeBehavior = { writeResult } }
            val component = testProfileComponent(resource)

            assertIs<ProfileAcceptance.Accepted>(
                component.accept(
                    ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
                ),
            )
            assertFalse(component.query(ProfileQuery.GetPreferences).preferences.soundEnabled)
            assertEquals(1, resource.writes.size)
            when (writeResult) {
                is ProfileWriteResult.Rejected -> assertEquals(
                    ProfilePersistenceStatus.Rejected(ProfileRevision(2L), writeResult.reason),
                    component.query(ProfileQuery.GetPersistenceStatus).persistence,
                )
                is ProfileWriteResult.ResourceFailure -> assertEquals(
                    ProfilePersistenceStatus.ResourceFailure(ProfileRevision(2L), writeResult.reason),
                    component.query(ProfileQuery.GetPersistenceStatus).persistence,
                )
                is ProfileWriteResult.OutcomeUnknown -> assertEquals(
                    ProfilePersistenceStatus.OutcomeUnknown(ProfileRevision(2L), writeResult.reason),
                    component.query(ProfileQuery.GetPersistenceStatus).persistence,
                )
                is ProfileWriteResult.Written -> error("Not a failure case")
            }
            repeat(3) { component.query(ProfileQuery.GetPersistenceStatus) }
            assertEquals(1, resource.writes.size)
        }
    }

    @Test
    fun callerWrapperFaultPreservesAcceptedWriteAndAllowsTheNextCommand() {
        val resource = RecordingProfileResource()
        val fault = IllegalStateException("result observer failed")
        val component = testProfileComponent(resource)
        val caller = ProfileCommandTestCaller<ProfileProgressApplied>()

        val thrown = assertFailsWith<IllegalStateException> {
            caller.call { reply ->
                component.applyGameplayProgress(GameplayProgressUpdate(bankedMatter = 1L), reply)
                throw fault
            }
        }

        assertEquals(fault, thrown)
        assertEquals(ProfileRevision(2L), caller.changed.single().revision)
        assertTrue(caller.refused.isEmpty())
        assertEquals(ProfileRevision(3L), component.stateSnapshot().revision)
        assertEquals(
            ProfilePersistenceStatus.Persisted(ProfileRevision(2L)),
            component.query(ProfileQuery.GetPersistenceStatus).persistence,
        )
        val nextCaller = ProfileCommandTestCaller<ProfileProgressApplied>()
        nextCaller.call { reply -> component.applyGameplayProgress(GameplayProgressUpdate(bankedMatter = 1L), reply) }
        assertEquals(ProfileRevision(4L), nextCaller.changed.single().revision)
        assertTrue(nextCaller.refused.isEmpty())
        assertEquals(2, resource.writes.size)
    }

    @Test
    fun providerReadFailureBlocksOrdinaryCommandsWithoutEffects() {
        val resource = RecordingProfileResource(
            ProfileSnapshotReadResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
        )
        val component = testProfileComponent(resource)
        val before = component.stateSnapshot()
        val caller = ProfileCommandTestCaller<ProfileProgressApplied>()
        caller.call { reply -> component.applyGameplayProgress(GameplayProgressUpdate(bankedMatter = 1L), reply) }

        assertEquals(
            ProfileRefusal.DecisionRejected(ProfileRejection.BootstrapNotReady),
            caller.refused.single(),
        )
        assertTrue(caller.changed.isEmpty())
        assertEquals(before, component.stateSnapshot())
        assertTrue(resource.writes.isEmpty())
    }

    @Test
    fun activeLocalDispatchRefusesReentrantProgressWithoutAnotherMutation() {
        val resource = RecordingProfileResource()
        lateinit var component: DefaultProfileComponent
        val reentrant = ProfileCommandTestCaller<ProfileProgressApplied>()
        resource.beforeWrite = {
            reentrant.call { reply -> component.applyGameplayProgress(GameplayProgressUpdate(bankedMatter = 1L), reply) }
        }
        component = testProfileComponent(resource)

        assertIs<ProfileAcceptance.Accepted>(
            component.accept(
                ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ),
        )
        assertEquals(ProfileRefusal.Busy, reentrant.refused.single())
        assertTrue(reentrant.changed.isEmpty())
        assertEquals(0L, component.stateSnapshot().profile.economy.matter)
        assertEquals(1, resource.writes.size)
    }

    @Test
    fun trustedResourceCompletionIsValidatedBeforeFactConstruction() {
        val resource = RecordingProfileResource().apply {
            writeBehavior = { snapshot ->
                ProfileWriteResult.Written(ProfileRevision(snapshot.revision.value + 1L))
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
        assertEquals(
            ProfileEffectRef(ProfileRevision(2L), ordinal = 0),
            assertIs<ProfilePersistenceStatus.Pending>(
                component.query(ProfileQuery.GetPersistenceStatus).persistence,
            ).effectRef,
        )
    }

    @Test
    fun deployedCompletionAndStaticAcceptorBoundsAcceptNAndRefuseNPlusOne() {
        val completions = profileCompletionDeque<Int>()
        repeat(8) { value -> assertTrue(completions.tryAddLast(value)) }
        assertFalse(completions.tryAddLast(8))
        assertEquals((0 until 8).toList(), List(8) { completions.removeFirstOrNull() })

        requireProfileSynchronousResourceEffectBound(1)
        assertFailsWith<IllegalStateException> { requireProfileSynchronousResourceEffectBound(2) }
        requireProfileCompletionCapacity(remainingCapacity = 1, requiredCompletions = 1)
        assertFailsWith<IllegalStateException> {
            requireProfileCompletionCapacity(remainingCapacity = 0, requiredCompletions = 1)
        }
    }
}

private data class BootstrapCase(
    val result: ProfileSnapshotReadResult,
    val expectedProfile: kinetickk.ball.profile.api.PlayerProfile,
    val expectedRevision: ProfileRevision,
    val expectedBootstrap: ProfileBootstrapStatus,
)
