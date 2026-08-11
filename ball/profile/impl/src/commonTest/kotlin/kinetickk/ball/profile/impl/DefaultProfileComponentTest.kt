// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandAdmissionFailureReason
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileCommandValidationFailureReason
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfilePurgeOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResetReason
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSemanticHandle
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.ProfileV4WritePurpose
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteFailure
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
        assertTrue(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY,
                ProfileModuleResult.ResetCompleted,
            ),
        )
        assertTrue(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.SESSION_RESET_CONFIRM,
                ProfileModuleResult.ResetWriteResourceFailure(
                    ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
                ),
            ),
        )
        assertTrue(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY,
                ProfileModuleResult.ResetNeedsAttention(
                    ProfileResetStatus.NeedsAttention(
                        legacyKeys = ProfileLegacyKeys(progressV2 = true, matter = false),
                        result = ProfileLegacyPurgeResult.Partial(
                            ProfileLegacyKeys(progressV2 = true, matter = false),
                        ),
                    ),
                ),
            ),
        )
        assertFalse(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY,
                ProfileModuleResult.ResetWriteRejected(ProfileV4Rejection.INCONSISTENT_PROFILE),
            ),
        )
        assertFalse(
            profileResultMatches(
                ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY,
                ProfileModuleResult.ResetWriteOutcomeUnknown(
                    ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
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
                ProfileEffectiveProtocolIdentity.SESSION_RESET_CONFIRM,
                ProfileModuleResult.CoreShapeSelected(CoreShape.SHARD),
            ),
        )
    }

    @Test
    fun commandRevisionAdmissionReservesTheWholeAcceptedInlineChain() {
        val ready = testProfileComponent(RecordingProfileResource()).stateSnapshot()
        assertTrue(
            hasProfileCommandRevisionCapacity(
                ProfileRevision(Long.MAX_VALUE - 2),
                ready.reset,
                ProfileModuleCommand.ToggleMute,
            ),
        )
        assertFalse(
            hasProfileCommandRevisionCapacity(
                ProfileRevision(Long.MAX_VALUE - 1),
                ready.reset,
                ProfileModuleCommand.ToggleMute,
            ),
        )

        val reset = testProfileComponent(
            RecordingProfileResource(
                ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
            ),
        ).stateSnapshot()
        assertTrue(
            hasProfileCommandRevisionCapacity(
                ProfileRevision(Long.MAX_VALUE - 3),
                reset.reset,
                ProfileModuleCommand.ConfirmLegacyReset,
            ),
        )
        assertFalse(
            hasProfileCommandRevisionCapacity(
                ProfileRevision(Long.MAX_VALUE - 2),
                reset.reset,
                ProfileModuleCommand.ConfirmLegacyReset,
            ),
        )
    }

    @Test
    fun constructionBootstrapReadsOnceAndNeverWritesFreshState() {
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
                result = ProfileBootstrapResourceResult.ResourceFailure(
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
            assertEquals(0, resource.purgeCount)
            assertProfileQueries(case.expectedProfile, component, case.expectedRevision)
            assertEquals(
                case.expectedBootstrap,
                component.query(ProfileQuery.GetPersistenceStatus).bootstrap,
            )
        }

        val throwingResource = RecordingProfileResource().apply {
            readBehavior = { error("resource programming fault") }
        }
        assertFailsWith<IllegalStateException> { testProfileComponent(throwingResource) }
        assertEquals(1, throwingResource.readCount)
        assertTrue(throwingResource.writes.isEmpty())
    }

    @Test
    fun componentFactoryAcceptsOnlyTheExactPersistenceCapability() {
        val persistence = RecordingPersistenceCapability()
        val component = createProfileComponent(persistence, TestProfilePolicy)

        assertEquals(1, persistence.readV4Count)
        assertEquals(ProfileBootstrapStatus.Ready, component.query(ProfileQuery.GetPersistenceStatus).bootstrap)
        assertIs<ProfileAcceptance.Accepted>(
            component.accept(
                ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ),
        )
        assertEquals(1, persistence.writeV4Count)
        assertNotNull(persistence.v4)
        assertEquals(0, persistence.removeLegacyProgressV2Count)
        assertEquals(0, persistence.removeLegacyMatterCount)
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
            assertEquals(ProfileV4WritePurpose.MUTATION, assertNotNull(observedPending).purpose)
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
    fun rejectedAndUnknownWritesDoNotRollbackOrBlindlyRetryAcceptedMutation() {
        listOf<ProfileV4WriteResult>(
            ProfileV4WriteResult.Rejected(ProfileV4Rejection.VALUE_OUT_OF_RANGE),
            ProfileV4WriteResult.ResourceFailure(
                ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
            ),
            ProfileV4WriteResult.OutcomeUnknown(
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
                is ProfileV4WriteResult.Rejected -> assertEquals(
                    ProfilePersistenceStatus.Rejected(ProfileRevision(2L), writeResult.reason),
                    component.query(ProfileQuery.GetPersistenceStatus).persistence,
                )
                is ProfileV4WriteResult.ResourceFailure -> assertEquals(
                    ProfilePersistenceStatus.ResourceFailure(ProfileRevision(2L), writeResult.reason),
                    component.query(ProfileQuery.GetPersistenceStatus).persistence,
                )
                is ProfileV4WriteResult.OutcomeUnknown -> assertEquals(
                    ProfilePersistenceStatus.OutcomeUnknown(ProfileRevision(2L), writeResult.reason),
                    component.query(ProfileQuery.GetPersistenceStatus).persistence,
                )
                is ProfileV4WriteResult.Written -> error("Not a failure case")
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
        assertEquals(request.targetInstance, delivery.commandSource.targetInstance)
        assertEquals(91L, delivery.commandSource.causalScope)
        assertEquals(2, delivery.commandSource.causalDepth)
        assertEquals(request.semanticHandle, delivery.resultSource.semanticHandle)
        assertEquals(LOCAL_PROFILE_INSTANCE_ID, delivery.resultSource.targetInstance)
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
        val deliveries = mutableListOf<ProfileModuleResultDelivery>()
        val component = testProfileComponent(resource, commandResultSink = deliveries::add)
        val request = request(
            component = component,
            command = ProfileModuleCommand.SelectCoreShape(CoreShape.SHARD),
            source = ProfileCommandSource.GameplayRun(4L),
        )
        val before = component.stateSnapshot()

        val rejection = assertIs<ProfileCommandIngressResult.RejectedBeforeAcceptance>(
            component.acceptTestCommand(request, causalScope = 44L, causalDepth = 1),
        ).refusal

        assertEquals(request.semanticHandle, rejection.commandSource.semanticHandle)
        assertEquals(44L, rejection.commandSource.causalScope)
        assertEquals(1, rejection.commandSource.causalDepth)
        assertEquals(
            ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE,
            rejection.effectiveProtocolIdentity,
        )
        assertEquals(
            ProfileCommandBoundaryResponse.ValidationFailure(
                ProfileCommandValidationFailureReason.WRONG_SOURCE_KIND,
            ),
            rejection.boundaryResponse,
        )
        assertEquals(LOCAL_PROFILE_INSTANCE_ID, rejection.targetBoundaryProvenance.targetInstance)
        assertEquals(before, component.stateSnapshot())
        assertTrue(resource.writes.isEmpty())
        assertTrue(deliveries.isEmpty())
    }

    @Test
    fun decisionRejectionReturnsTypedPreAcceptanceCarrier() {
        val resource = RecordingProfileResource()
        val component = testProfileComponent(resource)
        val request = request(
            component,
            ProfileModuleCommand.SelectCoreShape(CoreShape.PRISM),
        )
        val before = component.stateSnapshot()

        val refusal = assertIs<ProfileCommandIngressResult.RejectedBeforeAcceptance>(
            component.acceptTestCommand(request, causalScope = 10L, causalDepth = 0),
        ).refusal

        assertEquals(
            ProfileCommandBoundaryResponse.DecisionRejected(ProfileRejection.CoreShapeLocked),
            refusal.boundaryResponse,
        )
        assertEquals(before, component.stateSnapshot())
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
    fun causalDepthAndFullChainReservationAcceptExactNAndRejectNPlusOne() {
        val ordinaryResource = RecordingProfileResource()
        val ordinary = testProfileComponent(ordinaryResource)
        assertIs<ProfileCommandIngressResult.Accepted>(
            ordinary.acceptTestCommand(
                request(ordinary, ProfileModuleCommand.ToggleMute),
                causalScope = 61L,
                causalDepth = 5,
            ),
        )
        assertEquals(1, ordinaryResource.writes.size)

        val overflowResource = RecordingProfileResource()
        val overflow = testProfileComponent(overflowResource)
        val overflowRefusal = assertIs<ProfileCommandIngressResult.RejectedBeforeAcceptance>(
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
            overflowRefusal.boundaryResponse,
        )
        assertTrue(overflowResource.writes.isEmpty())

        val resetAtNResource = RecordingProfileResource(
            ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
        )
        val resetAtN = testProfileComponent(resetAtNResource)
        assertIs<ProfileCommandIngressResult.Accepted>(
            resetAtN.acceptTestCommand(
                request(resetAtN, ProfileModuleCommand.ConfirmLegacyReset),
                causalScope = 63L,
                causalDepth = 3,
            ),
        )
        assertEquals(1, resetAtNResource.writes.size)
        assertEquals(1, resetAtNResource.purgeCount)

        val resetOverflowResource = RecordingProfileResource(
            ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
        )
        val resetOverflow = testProfileComponent(resetOverflowResource)
        val resetRefusal = assertIs<ProfileCommandIngressResult.RejectedBeforeAcceptance>(
            resetOverflow.acceptTestCommand(
                request(resetOverflow, ProfileModuleCommand.ConfirmLegacyReset),
                causalScope = 64L,
                causalDepth = 4,
            ),
        ).refusal
        assertIs<ProfileCommandBoundaryResponse.AdmissionFailure>(resetRefusal.boundaryResponse)
        assertTrue(resetOverflowResource.writes.isEmpty())
        assertEquals(0, resetOverflowResource.purgeCount)
    }

    @Test
    fun trustedResourceCompletionIsValidatedBeforeFactConstruction() {
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
        assertEquals(
            ProfileEffectRef(ProfileRevision(2L), ordinal = 0),
            assertIs<ProfilePersistenceStatus.Pending>(
                component.query(ProfileQuery.GetPersistenceStatus).persistence,
            ).effectRef,
        )
    }

    @Test
    fun resourceProgrammingFaultAfterPublicationDrainsAcceptedResultThenRethrowsFirstFault() {
        val resource = RecordingProfileResource().apply {
            writeBehavior = { error("resource programming fault") }
        }
        val deliveries = mutableListOf<ProfileModuleResultDelivery>()
        val component = testProfileComponent(resource, commandResultSink = deliveries::add)
        val request = request(
            component,
            ProfileModuleCommand.ToggleMute,
            sourceRevision = 25L,
        )

        val failure = assertFailsWith<IllegalStateException> {
            component.acceptTestCommand(
                request,
                causalScope = 75L,
                causalDepth = 0,
            )
        }

        assertEquals("resource programming fault", failure.message)
        assertEquals(1, resource.writes.size)
        val delivery = deliveries.single()
        assertEquals(request.semanticHandle, delivery.commandSource.semanticHandle)
        assertEquals(ProfileRevision(2L), delivery.resultSource.targetRevision)
        assertEquals(1, delivery.resultSource.sourceOrdinal)
        assertEquals(
            PlayerPreferences(soundEnabled = false, musicEnabled = false),
            assertIs<ProfileModuleResult.PreferencesChanged>(delivery.result).preferences,
        )
        assertFalse(component.query(ProfileQuery.GetPreferences).preferences.soundEnabled)
        assertFalse(component.query(ProfileQuery.GetPreferences).preferences.musicEnabled)
        assertEquals(
            ProfileEffectRef(ProfileRevision(2L), ordinal = 0),
            assertIs<ProfilePersistenceStatus.Pending>(
                component.query(ProfileQuery.GetPersistenceStatus).persistence,
            ).effectRef,
        )
    }

    @Test
    fun commandResultSinkFaultDrainsQueuedResourceFactBeforeRethrowAndNextDispatch() {
        val resource = RecordingProfileResource()
        val deliveries = mutableListOf<ProfileModuleResultDelivery>()
        val component = testProfileComponent(resource) { delivery ->
            deliveries += delivery
            if (deliveries.size == 1) error("command result sink programming fault")
        }
        val firstRequest = request(
            component,
            ProfileModuleCommand.ToggleMute,
            sourceRevision = 26L,
        )

        val failure = assertFailsWith<IllegalStateException> {
            component.acceptTestCommand(
                firstRequest,
                causalScope = 76L,
                causalDepth = 0,
            )
        }

        assertEquals("command result sink programming fault", failure.message)
        assertEquals(1, resource.writes.size)
        assertEquals(firstRequest.semanticHandle, deliveries.single().commandSource.semanticHandle)
        assertEquals(
            ProfilePersistenceStatus.Persisted(ProfileRevision(2L)),
            component.query(ProfileQuery.GetPersistenceStatus).persistence,
        )
        assertEquals(ProfileRevision(3L), component.query(ProfileQuery.GetPreferences).revision)

        val secondRequest = request(
            component,
            ProfileModuleCommand.ToggleMute,
            sourceRevision = 27L,
        )
        assertIs<ProfileCommandIngressResult.Accepted>(
            component.acceptTestCommand(
                secondRequest,
                causalScope = 77L,
                causalDepth = 0,
            ),
        )

        assertEquals(2, resource.writes.size)
        assertEquals(2, deliveries.size)
        assertEquals(secondRequest.semanticHandle, deliveries.last().commandSource.semanticHandle)
        assertEquals(
            ProfilePersistenceStatus.Persisted(ProfileRevision(4L)),
            component.query(ProfileQuery.GetPersistenceStatus).persistence,
        )
        assertEquals(ProfileRevision(5L), component.query(ProfileQuery.GetPreferences).revision)
    }

    @Test
    fun resetRequiredConstructionWaitsForAnExplicitCommandWithoutEffects() {
        val resource = RecordingProfileResource(
            ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
        )
        val component = testProfileComponent(resource)
        resource.events.clear()

        repeat(3) {
            assertIs<ProfileRunBootstrapResult.Unavailable>(
                component.query(ProfileQuery.GetRunBootstrap).result,
            )
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
    fun confirmedResetWritesThenPurgesAndDeliversOnlyAfterBothFacts() {
        val resource = RecordingProfileResource(
            ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
        )
        val deliveries = mutableListOf<ProfileModuleResultDelivery>()
        lateinit var component: DefaultProfileComponent
        resource.beforeWrite = { snapshot ->
            assertTrue(snapshot.legacyResetConfirmed)
            assertEquals(0, resource.purgeCount)
            assertTrue(deliveries.isEmpty())
        }
        resource.beforePurge = {
            assertEquals(1, resource.writes.size)
            assertTrue(deliveries.isEmpty())
        }
        component = testProfileComponent(resource, commandResultSink = deliveries::add)
        resource.events.clear()
        val request = request(
            component,
            ProfileModuleCommand.ConfirmLegacyReset,
            sourceRevision = 30L,
            sourceOrdinal = 2,
        )

        val acceptance = assertIs<ProfileCommandIngressResult.Accepted>(
            component.acceptTestCommand(request, causalScope = 81L, causalDepth = 3),
        )

        assertEquals(ProfileRevision(2L), acceptance.targetRevision)
        assertEquals(listOf("write", "purge"), resource.events)
        assertEquals(ProfileRevision(4L), component.query(ProfileQuery.GetPreferences).revision)
        val delivery = deliveries.single()
        assertEquals(request.semanticHandle, delivery.commandSource.semanticHandle)
        assertEquals(81L, delivery.resultSource.causalScope)
        assertEquals(6, delivery.resultSource.causalDepth)
        assertEquals(ProfileRevision(4L), delivery.resultSource.targetRevision)
        assertEquals(0, delivery.resultSource.sourceOrdinal)
        assertEquals(ProfileEffectiveProtocolIdentity.SESSION_RESET_CONFIRM, delivery.effectiveProtocolIdentity)
        assertEquals(ProfileModuleResult.ResetCompleted, delivery.result)
        assertEquals(ProfileBootstrapStatus.Ready, component.query(ProfileQuery.GetPersistenceStatus).bootstrap)
    }

    @Test
    fun acceptedResetPurgeProgrammingFaultPropagatesWithoutFakeCompletionOrResult() {
        val resource = RecordingProfileResource(
            ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
        ).apply {
            purgeBehavior = { error("resource purge programming fault") }
        }
        val deliveries = mutableListOf<ProfileModuleResultDelivery>()
        val component = testProfileComponent(resource, commandResultSink = deliveries::add)
        resource.events.clear()

        assertFailsWith<IllegalStateException> {
            component.acceptTestCommand(
                request(
                    component,
                    ProfileModuleCommand.ConfirmLegacyReset,
                    sourceRevision = 35L,
                ),
                causalScope = 85L,
                causalDepth = 0,
            )
        }

        assertEquals(listOf("write", "purge"), resource.events)
        assertEquals(1, resource.writes.size)
        assertEquals(1, resource.purgeCount)
        assertTrue(deliveries.isEmpty())
        val status = component.query(ProfileQuery.GetPersistenceStatus)
        assertEquals(
            ProfileBootstrapStatus.Blocked(ProfileBootstrapBlockReason.ResetInProgress),
            status.bootstrap,
        )
        assertEquals(
            ProfilePersistenceStatus.Persisted(ProfileRevision(2L)),
            status.persistence,
        )
        val reset = assertIs<ProfileResetStatus.PurgingLegacy>(status.reset)
        assertEquals(ProfileEffectRef(ProfileRevision(3L), ordinal = 0), reset.effectRef)
        assertEquals(ProfileLegacyKeys.ALL, reset.legacyKeys)
        assertEquals(
            ProfileSemanticHandle(
                sourceInstance = ProfileCommandSource.LocalSession,
                sourceRevision = 35L,
                sourceOrdinal = 0,
            ),
            reset.completion.commandSource.semanticHandle,
        )
        assertEquals(85L, reset.completion.commandSource.causalScope)
        assertEquals(0, reset.completion.commandSource.causalDepth)
    }

    @Test
    fun rejectedOrUnknownResetWritePreservesLegacyAndNeverPurges() {
        listOf<ProfileV4WriteResult>(
            ProfileV4WriteResult.Rejected(ProfileV4Rejection.INCONSISTENT_PROFILE),
            ProfileV4WriteResult.ResourceFailure(
                ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
            ),
            ProfileV4WriteResult.OutcomeUnknown(
                ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        ).forEachIndexed { index, result ->
            val resource = RecordingProfileResource(
                ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
            ).apply { writeBehavior = { result } }
            val deliveries = mutableListOf<ProfileModuleResultDelivery>()
            val component = testProfileComponent(resource, commandResultSink = deliveries::add)

            assertIs<ProfileCommandIngressResult.Accepted>(
                component.acceptTestCommand(
                    request(
                        component,
                        ProfileModuleCommand.ConfirmLegacyReset,
                        sourceRevision = 40L + index,
                    ),
                    causalScope = 90L + index,
                    causalDepth = 0,
                ),
            )

            assertEquals(1, resource.writes.size)
            assertEquals(0, resource.purgeCount)
            assertIs<ProfileResetStatus.ConfirmationRequired>(
                component.query(ProfileQuery.GetPersistenceStatus).reset,
            )
            when (result) {
                is ProfileV4WriteResult.Rejected ->
                    assertEquals(ProfileModuleResult.ResetWriteRejected(result.reason), deliveries.single().result)
                is ProfileV4WriteResult.ResourceFailure ->
                    assertEquals(
                        ProfileModuleResult.ResetWriteResourceFailure(result.reason),
                        deliveries.single().result,
                    )
                is ProfileV4WriteResult.OutcomeUnknown ->
                    assertEquals(
                        ProfileModuleResult.ResetWriteOutcomeUnknown(result.reason),
                        deliveries.single().result,
                    )
                is ProfileV4WriteResult.Written -> error("Not a failure case")
            }
        }
    }

    @Test
    fun resetWriteFailureRetryRequiresExplicitNewCommandAndOneFreshWritePerCommand() {
        listOf<ProfileV4WriteResult>(
            ProfileV4WriteResult.Rejected(ProfileV4Rejection.INCONSISTENT_PROFILE),
            ProfileV4WriteResult.ResourceFailure(
                ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
            ),
            ProfileV4WriteResult.OutcomeUnknown(
                ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        ).forEachIndexed { index, firstResult ->
            val resource = RecordingProfileResource(
                ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
            )
            resource.writeBehavior = { snapshot ->
                if (resource.writes.size == 1) {
                    firstResult
                } else {
                    ProfileV4WriteResult.Written(snapshot.revision)
                }
            }
            val deliveries = mutableListOf<ProfileModuleResultDelivery>()
            val component = testProfileComponent(resource, commandResultSink = deliveries::add)
            val firstRequest = request(
                component,
                ProfileModuleCommand.ConfirmLegacyReset,
                sourceRevision = 80L + index * 2L,
            )

            assertIs<ProfileCommandIngressResult.Accepted>(
                component.acceptTestCommand(
                    firstRequest,
                    causalScope = 120L + index,
                    causalDepth = 0,
                ),
            )

            assertEquals(1, resource.writes.size)
            assertEquals(0, resource.purgeCount)
            assertEquals(1, deliveries.size)
            when (firstResult) {
                is ProfileV4WriteResult.Rejected -> assertEquals(
                    ProfileModuleResult.ResetWriteRejected(firstResult.reason),
                    deliveries.single().result,
                )
                is ProfileV4WriteResult.ResourceFailure -> assertEquals(
                    ProfileModuleResult.ResetWriteResourceFailure(firstResult.reason),
                    deliveries.single().result,
                )
                is ProfileV4WriteResult.OutcomeUnknown -> assertEquals(
                    ProfileModuleResult.ResetWriteOutcomeUnknown(firstResult.reason),
                    deliveries.single().result,
                )
                is ProfileV4WriteResult.Written -> error("Not a failure case")
            }
            assertIs<ProfileResetStatus.ConfirmationRequired>(
                component.query(ProfileQuery.GetPersistenceStatus).reset,
            )
            repeat(3) { component.query(ProfileQuery.GetPersistenceStatus) }
            assertEquals(1, resource.writes.size, "write failure must not retry automatically")
            assertEquals(1, deliveries.size, "write failure must not fabricate another result")

            val retryRequest = request(
                component,
                ProfileModuleCommand.ConfirmLegacyReset,
                sourceRevision = firstRequest.semanticHandle.sourceRevision + 1L,
            )
            assertFalse(firstRequest.semanticHandle == retryRequest.semanticHandle)
            assertIs<ProfileCommandIngressResult.Accepted>(
                component.acceptTestCommand(
                    retryRequest,
                    causalScope = 130L + index,
                    causalDepth = 0,
                ),
            )

            assertEquals(
                listOf(ProfileRevision(2L), ProfileRevision(4L)),
                resource.writes.map { snapshot -> snapshot.revision },
            )
            assertEquals(1, resource.purgeCount)
            assertEquals(2, deliveries.size)
            assertEquals(firstRequest.semanticHandle, deliveries.first().commandSource.semanticHandle)
            assertEquals(retryRequest.semanticHandle, deliveries.last().commandSource.semanticHandle)
            assertEquals(ProfileModuleResult.ResetCompleted, deliveries.last().result)
            assertEquals(
                ProfileBootstrapStatus.Ready,
                component.query(ProfileQuery.GetPersistenceStatus).bootstrap,
            )
        }
    }

    @Test
    fun purgeFailureTaxonomyEntersNeedsAttentionWithoutAutomaticRetry() {
        listOf<ProfileLegacyPurgeResult>(
            ProfileLegacyPurgeResult.Partial(
                ProfileLegacyKeys(progressV2 = true, matter = false),
            ),
            ProfileLegacyPurgeResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
            ProfileLegacyPurgeResult.OutcomeUnknown(
                remaining = ProfileLegacyKeys(progressV2 = true, matter = false),
                unknown = ProfileLegacyKeys(progressV2 = false, matter = true),
                reason = ProfilePurgeOutcomeUnknownReason.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
            ),
        ).forEachIndexed { index, purgeResult ->
            val resource = RecordingProfileResource(
                ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.ALL),
            ).apply { purgeBehavior = { purgeResult } }
            val component = testProfileComponent(resource)

            assertIs<ProfileCommandIngressResult.Accepted>(
                component.acceptTestCommand(
                    request(
                        component,
                        ProfileModuleCommand.ConfirmLegacyReset,
                        sourceRevision = 50L + index,
                    ),
                    causalScope = 100L + index,
                    causalDepth = 0,
                ),
            )
            val attention = assertIs<ProfileResetStatus.NeedsAttention>(
                component.query(ProfileQuery.GetPersistenceStatus).reset,
            )
            assertEquals(purgeResult, attention.result)
            assertEquals(1, resource.purgeCount)
            repeat(3) { component.query(ProfileQuery.GetPersistenceStatus) }
            assertEquals(1, resource.purgeCount)
        }
    }

    @Test
    fun oneExplicitRetryCommandCausesExactlyOnePurgeAttempt() {
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
        val deliveries = mutableListOf<ProfileModuleResultDelivery>()
        val component = testProfileComponent(resource, commandResultSink = deliveries::add)

        assertIs<ProfileResetStatus.NeedsAttention>(
            component.query(ProfileQuery.GetPersistenceStatus).reset,
        )
        repeat(2) { component.query(ProfileQuery.GetPersistenceStatus) }
        assertEquals(0, resource.purgeCount)

        val acceptance = assertIs<ProfileCommandIngressResult.Accepted>(
            component.acceptTestCommand(
                request(component, ProfileModuleCommand.RetryLegacyPurge, sourceRevision = 70L),
                causalScope = 111L,
                causalDepth = 0,
            ),
        )

        assertEquals(ProfileRevision(42L), acceptance.targetRevision)
        assertTrue(resource.writes.isEmpty())
        assertEquals(1, resource.purgeCount)
        assertEquals(ProfileModuleResult.ResetCompleted, deliveries.single().result)
        assertEquals(ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY, deliveries.single().effectiveProtocolIdentity)
        assertProfileQueries(loaded, component, ProfileRevision(43L))
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
    val result: ProfileBootstrapResourceResult,
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

private class RecordingPersistenceCapability : ProfilePersistenceCapability {
    var v4: String? = null
    var legacyProgressV2: String? = null
    var legacyMatter: String? = null
    var readV4Count: Int = 0
    var writeV4Count: Int = 0
    var removeLegacyProgressV2Count: Int = 0
    var removeLegacyMatterCount: Int = 0

    override fun readV4(): ProfilePersistenceReadResult {
        readV4Count += 1
        return ProfilePersistenceReadResult.Observed(v4)
    }

    override fun writeV4(payload: String): ProfilePersistenceMutationResult {
        writeV4Count += 1
        v4 = payload
        return ProfilePersistenceMutationResult.COMPLETED
    }

    override fun readLegacyProgressV2(): ProfilePersistenceReadResult =
        ProfilePersistenceReadResult.Observed(legacyProgressV2)

    override fun readLegacyMatter(): ProfilePersistenceReadResult =
        ProfilePersistenceReadResult.Observed(legacyMatter)

    override fun removeLegacyProgressV2(): ProfilePersistenceMutationResult {
        removeLegacyProgressV2Count += 1
        legacyProgressV2 = null
        return ProfilePersistenceMutationResult.COMPLETED
    }

    override fun removeLegacyMatter(): ProfilePersistenceMutationResult {
        removeLegacyMatterCount += 1
        legacyMatter = null
        return ProfilePersistenceMutationResult.COMPLETED
    }
}
