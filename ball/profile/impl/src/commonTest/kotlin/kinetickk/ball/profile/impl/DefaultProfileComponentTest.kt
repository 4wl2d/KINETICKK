// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandAdmissionFailureReason
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileCommandValidationFailureReason
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSemanticHandle
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.ProfileWriteFailure
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultProfileComponentTest {
    @Test
    fun effectiveProtocolIdentityRejectsForeignProfileResultFamilies() {
        assertTrue(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.SESSION_MUTE,
                ProfileModuleResult.PreferencesChanged(PlayerPreferences()),
            ),
        )
        assertTrue(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
                ProfileModuleResult.GameplayProgressApplied,
            ),
        )
        assertFalse(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.SESSION_MUTE,
                ProfileModuleResult.GameplayProgressApplied,
            ),
        )
        assertFalse(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
                ProfileModuleResult.PreferencesChanged(PlayerPreferences()),
            ),
        )
        assertFalse(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.SESSION_REBIRTH,
                ProfileModuleResult.CoreShapeSelected(CoreShape.SHARD),
            ),
        )
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
    fun acceptedCommandDeliversExactSourceTargetProtocolAndCausalEvidence() {
        val events = mutableListOf<String>()
        val deliveries = mutableListOf<ProfileModuleResultDelivery>()
        val resource = RecordingProfileResource().apply {
            beforeWrite = { events += "write" }
        }
        val component = testProfileComponent(resource) { delivery ->
            events += "delivery"
            deliveries += delivery
        }
        val request = request(
            component = component,
            command = ProfileModuleCommand.ToggleMute,
            sourceRevision = 7L,
            sourceOrdinal = 3,
        )

        val acceptance = assertIs<ProfileCommandIngressResult.Accepted>(
            component.acceptTestCommand(request, causalScope = 91L, causalDepth = 2),
        )

        assertEquals(ProfileRevision(2L), acceptance.targetRevision)
        assertEquals(LOCAL_PROFILE_INSTANCE_ID, acceptance.targetInstance)
        assertEquals(listOf("write", "delivery"), events)
        val delivery = deliveries.single()
        assertEquals(request.semanticHandle, delivery.commandSource.semanticHandle)
        assertEquals(91L, delivery.commandSource.causalScope)
        assertEquals(2, delivery.commandSource.causalDepth)
        assertEquals(request.semanticHandle, delivery.resultSource.semanticHandle)
        assertEquals(acceptance.targetRevision, delivery.resultSource.targetRevision)
        assertEquals(1, delivery.resultSource.sourceOrdinal)
        assertEquals(91L, delivery.resultSource.causalScope)
        assertEquals(3, delivery.resultSource.causalDepth)
        assertEquals(ProfileEffectiveProtocolIdentity.SESSION_MUTE, delivery.effectiveProtocolIdentity)
        assertIs<ProfileModuleResult.PreferencesChanged>(delivery.result)
        assertEquals(
            ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING,
            delivery.issuerProvenance,
        )
        assertEquals(ProfileRevision(3L), component.query(ProfileQuery.GetPreferences).revision)
    }

    @Test
    fun wrongSourceKindReturnsTypedValidationCarrierWithoutAcceptanceOrEffect() {
        val resource = RecordingProfileResource()
        val component = testProfileComponent(resource)
        val request = request(
            component = component,
            command = ProfileModuleCommand.SelectCoreShape(CoreShape.SHARD),
            source = ProfileCommandSource.GameplayRun(4L),
        )
        val before = component.stateSnapshot()

        val rejection = assertIs<ProfileCommandIngressResult.RejectedBeforeAcceptance>(
            component.acceptTestCommand(request, causalScope = 44L, causalDepth = 1),
        ).refusal

        assertEquals(
            ProfileCommandBoundaryResponse.ValidationFailure(
                ProfileCommandValidationFailureReason.WRONG_SOURCE_KIND,
            ),
            rejection.boundaryResponse,
        )
        assertEquals(ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE, rejection.effectiveProtocolIdentity)
        assertEquals(before, component.stateSnapshot())
        assertTrue(resource.writes.isEmpty())
    }

    @Test
    fun providerReadFailureBlocksOrdinaryCommandsWithoutEffects() {
        val resource = RecordingProfileResource(
            ProfileSnapshotReadResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
        )
        val component = testProfileComponent(resource)

        val refusal = assertIs<ProfileCommandIngressResult.RejectedBeforeAcceptance>(
            component.acceptTestCommand(
                request(component, ProfileModuleCommand.ToggleMute),
                causalScope = 10L,
                causalDepth = 0,
            ),
        ).refusal

        assertEquals(
            ProfileCommandBoundaryResponse.DecisionRejected(ProfileRejection.BootstrapNotReady),
            refusal.boundaryResponse,
        )
        assertTrue(resource.writes.isEmpty())
    }

    @Test
    fun activeDispatchReturnsCompletionCapacityCarrierForReentrantCommand() {
        val resource = RecordingProfileResource()
        lateinit var component: DefaultProfileComponent
        lateinit var reentrant: ProfileCommandIngressResult
        resource.beforeWrite = {
            reentrant = component.acceptTestCommand(
                request(component, ProfileModuleCommand.ToggleMute, sourceRevision = 99L),
                causalScope = 52L,
                causalDepth = 0,
            )
        }
        component = testProfileComponent(resource)

        assertIs<ProfileAcceptance.Accepted>(
            component.accept(
                ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ),
        )
        val refusal = assertIs<ProfileCommandIngressResult.RejectedBeforeAcceptance>(reentrant).refusal
        assertEquals(
            ProfileCommandBoundaryResponse.AdmissionFailure(
                ProfileCommandAdmissionFailureReason.CompletionCapacityExhausted,
            ),
            refusal.boundaryResponse,
        )
        assertEquals(1, resource.writes.size)
    }

    @Test
    fun causalDepthAcceptsExactBoundAndRejectsFirstOverflow() {
        val acceptedResource = RecordingProfileResource()
        val accepted = testProfileComponent(acceptedResource)
        assertIs<ProfileCommandIngressResult.Accepted>(
            accepted.acceptTestCommand(
                request(accepted, ProfileModuleCommand.ToggleMute),
                causalScope = 61L,
                causalDepth = 5,
            ),
        )

        val overflowResource = RecordingProfileResource()
        val overflow = testProfileComponent(overflowResource)
        val refusal = assertIs<ProfileCommandIngressResult.RejectedBeforeAcceptance>(
            overflow.acceptTestCommand(
                request(overflow, ProfileModuleCommand.ToggleMute),
                causalScope = 62L,
                causalDepth = 6,
            ),
        ).refusal
        assertEquals(
            ProfileCommandBoundaryResponse.AdmissionFailure(
                ProfileCommandAdmissionFailureReason.CausalBudgetExceeded(62L, limit = 8),
            ),
            refusal.boundaryResponse,
        )
        assertTrue(overflowResource.writes.isEmpty())
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

        repeat(8, ::requireProfileCausalDepth)
        assertFailsWith<IllegalStateException> { requireProfileCausalDepth(8) }
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

private fun request(
    component: DefaultProfileComponent,
    command: ProfileModuleCommand,
    source: ProfileCommandSource = if (command is ProfileModuleCommand.ApplyGameplayProgress) {
        ProfileCommandSource.GameplayRun(8L)
    } else {
        ProfileCommandSource.LocalSession
    },
    sourceRevision: Long = 7L,
    sourceOrdinal: Int = 0,
): ProfileModuleCommandRequest {
    val handle = ProfileSemanticHandle(source, sourceRevision, sourceOrdinal)
    return ProfileModuleCommandRequest(
        semanticHandle = handle,
        sourceOrdinal = sourceOrdinal,
        targetInstance = component.instanceId,
        command = command,
    )
}

private fun DefaultProfileComponent.acceptTestCommand(
    request: ProfileModuleCommandRequest,
    causalScope: Long,
    causalDepth: Int,
): ProfileCommandIngressResult =
    if (request.command is ProfileModuleCommand.ApplyGameplayProgress) {
        acceptFromGameplay(request, causalScope, causalDepth)
    } else {
        acceptFromSession(request, causalScope, causalDepth)
    }
