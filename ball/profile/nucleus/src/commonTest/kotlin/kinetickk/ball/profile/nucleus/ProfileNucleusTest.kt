// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandIssuerProvenance
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandPulse
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSemanticHandle
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.ProfileWriteFailure
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileNucleusTest {
    @Test
    fun defaultProfileUsesOnlyCapturedPolicyDefaults() {
        val policy = TestProfilePolicy.copy(
            coreShapes = TestProfilePolicy.coreShapes.reversed().toImmutableList(),
            weapons = TestProfilePolicy.weapons.reversed().toImmutableList(),
        )

        val profile = readyState(policy = policy).profile

        assertEquals(policy.coreShapes.first().id, profile.loadout.coreShape)
        assertEquals(policy.weapons.first().id, profile.loadout.selectedWeapon)
        assertEquals(setOf(policy.weapons.first().id), profile.loadout.unlockedWeapons)
        assertEquals(policy.metaUpgrades.size, profile.labProgress.ranks.size)
        assertEquals(policy.rebirth.minimumLevel, profile.rebirthProgress.level)
    }

    @Test
    fun everyLocalIntentIsDeterministicAndEmitsExactlyOneCurrentSnapshot() {
        val state = readyState(
            profile = defaultPlayerProfile(TestProfilePolicy).copy(
                economy = PlayerEconomy(matter = 10_000L, lifetimeMatter = 10_000L),
            ),
        )
        val intents: List<ProfilePulse.Business> = listOf(
            ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
            ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
        )

        intents.forEach { intent ->
            val pulse = ProfileNucleusPulse.Intent(intent)
            val first = ProfileNucleus.decide(state, pulse)
            assertEquals(first, ProfileNucleus.decide(state, pulse))
            val frame = first.acceptedFrame()
            val persist = assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single())
            assertEquals(frame.nextState.profile, persist.snapshot.profile)
            assertEquals(frame.nextState.revision, persist.snapshot.revision)
            assertIs<ProfilePersistenceStatus.Pending>(frame.nextState.persistence)
        }
    }

    @Test
    fun preferencesOwnStepSemanticsAndMuteIsATargetOwnedCommand() {
        val state = readyState()
        val adjustments = listOf(
            ProfilePreferenceAdjustment.ToggleSoundEffects,
            ProfilePreferenceAdjustment.ToggleMusic,
            ProfilePreferenceAdjustment.StepMasterVolume(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.StepSimulationSpeed(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.StepTextScale(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.ToggleScreenShake,
            ProfilePreferenceAdjustment.StepParticleDensity(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.ToggleDamageNumbers,
            ProfilePreferenceAdjustment.StepDamageNumberSize(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.StepDamageNumberFormat(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.StepDamageNumberTierThreshold(PreferenceAdjustmentDirection.INCREASE),
        )
        adjustments.forEach { adjustment ->
            ProfileNucleus.decide(
                state,
                ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(adjustment)),
            ).acceptedFrame()
        }

        val muted = decideCommand(state, ProfileModuleCommand.ToggleMute)
            .acceptedFrame().nextState.profile.preferences
        assertFalse(muted.soundEnabled)
        assertFalse(muted.musicEnabled)
    }

    @Test
    fun businessRejectionsPublishNoAcceptedFrame() {
        val state = readyState()
        assertEquals(
            ProfileRejection.InsufficientMatter,
            ProfileNucleus.decide(
                state,
                ProfileNucleusPulse.Intent(
                    ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
                ),
            ).rejection(),
        )
        assertEquals(
            ProfileRejection.CoreShapeLocked,
            decideCommand(state, ProfileModuleCommand.SelectCoreShape(CoreShape.PRISM)).rejection(),
        )
        assertEquals(
            ProfileRejection.RebirthLevelNotCleared,
            decideCommand(state, ProfileModuleCommand.AdvanceRebirth).rejection(),
        )
    }

    @Test
    fun gameplayProgressValidationUsesClosedRejectionReasons() {
        val state = readyState()
        val cases = listOf(
            GameplayProgressUpdate(bankedMatter = -1L) to
                ProfileGameplayProgressRejection.NegativeBankedMatter,
            GameplayProgressUpdate(discoveredItemIds = (0..TestProfilePolicy.itemCount).toSet()) to
                ProfileGameplayProgressRejection.TooManyDiscoveries,
            GameplayProgressUpdate(discoveredItemIds = setOf(-1)) to
                ProfileGameplayProgressRejection.UnknownItem(-1),
            GameplayProgressUpdate(clearedRebirthLevel = -1) to
                ProfileGameplayProgressRejection.ClearedLevelBelowMinimum(-1),
            GameplayProgressUpdate(clearedRebirthLevel = 1) to
                ProfileGameplayProgressRejection.ClearedLevelAboveCurrent(1),
        )

        cases.forEach { (update, expected) ->
            assertEquals(
                ProfileRejection.InvalidGameplayProgress(expected),
                decideCommand(
                    state,
                    ProfileModuleCommand.ApplyGameplayProgress(update),
                ).rejection(),
            )
        }
    }

    @Test
    fun targetOwnedCommandOrdersPersistenceBeforeCorrelatedCompletion() {
        val pulse = sessionPulse(ProfileModuleCommand.ToggleMute)
        val frame = ProfileNucleus.decide(
            readyState(),
            ProfileNucleusPulse.ModuleCommand(pulse),
        ).acceptedFrame()

        assertIs<ProfileOutput.PersistSnapshot>(frame.outputs[0])
        val completion = assertIs<ProfileOutput.CompleteCommand>(frame.outputs[1]).result
        assertEquals(pulse.commandSource.semanticHandle, completion.semanticHandle)
        assertEquals(pulse.commandSource, completion.commandSource)
        assertEquals(1, completion.sourceOrdinal)
        assertIs<ProfileModuleResult.PreferencesChanged>(completion.result)
    }

    @Test
    fun bootstrapTreatsMissingRejectedAndIncompatibleSnapshotsAsFreshDefaults() {
        val default = defaultPlayerProfile(TestProfilePolicy)
        val missing = ProfileState.initial(
            TestProfilePolicy,
            ProfileSnapshotReadResult.Observed(null),
        )
        val rejected = ProfileState.initial(
            TestProfilePolicy,
            ProfileSnapshotReadResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON),
        )
        val incompatible = constructedProfile(
            default.copy(economy = PlayerEconomy(matter = 1L, lifetimeMatter = 0L)),
        )
        val exhausted = listOf(
            Long.MAX_VALUE - 2L,
            Long.MAX_VALUE - 1L,
            Long.MAX_VALUE,
        ).map { revision -> constructedProfile(default, revision) }

        (listOf(missing, rejected, incompatible) + exhausted).forEach { state ->
            assertEquals(ProfileBootstrapStatus.Ready, state.bootstrap)
            assertEquals(default, state.profile)
            assertEquals(ProfilePersistenceStatus.NotAttempted, state.persistence)
            assertEquals(ProfileRevision(1L), state.revision)
        }
    }

    @Test
    fun bootstrapLoadsCompatibleCurrentSnapshotAndRestoresItsRevision() {
        val profile = defaultPlayerProfile(TestProfilePolicy).copy(
            economy = PlayerEconomy(matter = 55L, lifetimeMatter = 80L),
        )

        val loaded = constructedProfile(profile, revision = 17L)

        assertEquals(ProfileRevision(18L), loaded.revision)
        assertEquals(profile, loaded.profile)
        assertEquals(ProfilePersistenceStatus.Persisted(ProfileRevision(17L)), loaded.persistence)
        assertEquals(ProfileBootstrapStatus.Ready, loaded.bootstrap)
    }

    @Test
    fun providerReadFailureRemainsBlockedAndUnavailable() {
        val failed = ProfileState.initial(
            TestProfilePolicy,
            ProfileSnapshotReadResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
        )

        val blocked = assertIs<ProfileBootstrapStatus.Blocked>(failed.bootstrap)
        assertEquals(
            ProfileBootstrapBlockReason.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
            blocked.reason,
        )
        assertEquals(
            ProfileRejection.BootstrapNotReady,
            decideCommand(failed, ProfileModuleCommand.ToggleMute).rejection(),
        )
        assertIs<ProfileRunBootstrapResult.Unavailable>(
            ProfileNucleus.query(failed, ProfileQuery.GetRunBootstrap).result,
        )
    }

    @Test
    fun acceptedResourceEffectStagesOneFactBeforePersistenceCompletion() {
        val mutation = ProfileNucleus.decide(
            readyState(),
            ProfileNucleusPulse.Intent(
                ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ),
        ).acceptedFrame()
        val persist = assertIs<ProfileOutput.PersistSnapshot>(mutation.outputs.single())

        val unknown = ProfileNucleus.decide(
            mutation.nextState,
            ProfileNucleusPulse.WriteCompleted(
                persist.effectRef,
                ProfileWriteResult.OutcomeUnknown(
                    ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
            ),
        ).acceptedFrame()
        assertEquals(mutation.nextState.profile, unknown.nextState.profile)
        assertIs<ProfilePersistenceStatus.OutcomeUnknown>(unknown.nextState.persistence)
        assertTrue(unknown.outputs.isEmpty())

        val failed = ProfileNucleus.decide(
            mutation.nextState,
            ProfileNucleusPulse.WriteCompleted(
                persist.effectRef,
                ProfileWriteResult.ResourceFailure(
                    ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
                ),
            ),
        ).acceptedFrame()
        assertIs<ProfilePersistenceStatus.ResourceFailure>(failed.nextState.persistence)

        assertFailsWith<IllegalStateException> {
            ProfileNucleus.decide(
                mutation.nextState,
                ProfileNucleusPulse.WriteCompleted(
                    ProfileEffectRef(persist.effectRef.sourceRevision, persist.effectRef.ordinal + 1),
                    ProfileWriteResult.Written(persist.snapshot.revision),
                ),
            )
        }
    }

    @Test
    fun allQueriesReturnOneRevisionTaggedImmutableProjection() {
        val state = readyState(
            profile = defaultPlayerProfile(TestProfilePolicy).copy(
                economy = PlayerEconomy(100L, 200L),
                collection = PlayerCollection(setOf(2, 7)),
                rebirthProgress = RebirthProgress(0, 0),
            ),
        )
        val projections = listOf(
            ProfileNucleus.query(state, ProfileQuery.GetRunBootstrap),
            ProfileNucleus.query(state, ProfileQuery.GetPreferences),
            ProfileNucleus.query(state, ProfileQuery.GetHomeProgress),
            ProfileNucleus.query(state, ProfileQuery.GetLabProgress),
            ProfileNucleus.query(state, ProfileQuery.GetLoadout),
            ProfileNucleus.query(state, ProfileQuery.GetCollection),
            ProfileNucleus.query(state, ProfileQuery.GetRebirthProgress),
            ProfileNucleus.query(state, ProfileQuery.GetPersistenceStatus),
        )
        projections.forEach { projection ->
            assertEquals(state.instanceId, projection.instanceId)
            assertEquals(state.revision, projection.revision)
        }
        assertIs<ProfileRunBootstrapResult.Ready>(
            assertIs<RunBootstrapProjection>(projections[0]).result,
        )
        assertEquals(state.profile.preferences, assertIs<PreferencesProjection>(projections[1]).preferences)
        assertTrue(assertIs<HomeProgressProjection>(projections[2]).canAdvanceRebirth)
        assertEquals(state.profile.labProgress, assertIs<LabProgressProjection>(projections[3]).snapshot.progress)
        assertEquals(state.profile.loadout, assertIs<LoadoutProjection>(projections[4]).snapshot.loadout)
        assertEquals(state.profile.collection, assertIs<CollectionProjection>(projections[5]).collection)
        assertTrue(assertIs<RebirthProgressProjection>(projections[6]).canAdvance)
        assertEquals(state.persistence, assertIs<PersistenceStatusProjection>(projections[7]).persistence)
    }

    private fun readyState(
        profile: PlayerProfile? = null,
        policy: kinetickk.ball.content.api.ProfilePolicySnapshot = TestProfilePolicy,
    ): ProfileState {
        val initial = ProfileState.initial(policy, ProfileSnapshotReadResult.Observed(null))
        return if (profile == null) initial else initial.copy(profile = profile)
    }

    private fun constructedProfile(
        profile: PlayerProfile,
        revision: Long = 0L,
    ): ProfileState = ProfileState.initial(
        TestProfilePolicy,
        ProfileSnapshotReadResult.Observed(
            ProfileSnapshot(ProfileRevision(revision), profile),
        ),
    )

    private fun decideCommand(
        state: ProfileState,
        command: ProfileModuleCommand,
    ): ProfileDecision = ProfileNucleus.decide(
        state,
        ProfileNucleusPulse.ModuleCommand(
            if (command is ProfileModuleCommand.ApplyGameplayProgress) {
                gameplayPulse(command)
            } else {
                sessionPulse(command)
            },
        ),
    )

    private fun sessionPulse(command: ProfileModuleCommand): ProfileModuleCommandPulse = modulePulse(
        command = command,
        source = ProfileCommandSource.LocalSession,
        identity = when (command) {
            is ProfileModuleCommand.SelectCoreShape -> ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE
            ProfileModuleCommand.ToggleMute -> ProfileEffectiveProtocolIdentity.SESSION_MUTE
            ProfileModuleCommand.AdvanceRebirth -> ProfileEffectiveProtocolIdentity.SESSION_REBIRTH
            is ProfileModuleCommand.ApplyGameplayProgress -> error("Gameplay progress is not a session mapping")
        },
        issuer = ProfileCommandIssuerProvenance.LOCAL_SESSION_STATIC_BINDING,
    )

    private fun gameplayPulse(
        command: ProfileModuleCommand.ApplyGameplayProgress,
    ): ProfileModuleCommandPulse = modulePulse(
        command = command,
        source = ProfileCommandSource.GameplayRun(8L),
        identity = ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
        issuer = ProfileCommandIssuerProvenance.GAMEPLAY_RUN_STATIC_BINDING,
    )

    private fun modulePulse(
        command: ProfileModuleCommand,
        source: ProfileCommandSource,
        identity: ProfileEffectiveProtocolIdentity,
        issuer: ProfileCommandIssuerProvenance,
    ): ProfileModuleCommandPulse {
        val handle = ProfileSemanticHandle(source, sourceRevision = 4L, sourceOrdinal = 1)
        return ProfileModuleCommandPulse(
            commandSource = ProfileCommandSourceToken(
                semanticHandle = handle,
                targetInstance = readyState().instanceId,
                causalScope = 30L,
                causalDepth = 2,
            ),
            effectiveProtocolIdentity = identity,
            command = command,
            issuerProvenance = issuer,
        )
    }
}

private fun ProfileDecision.acceptedFrame(): ProfileAcceptedFrame =
    assertIs<ProfileDecision.Accepted>(this).frame

private fun ProfileDecision.rejection(): ProfileRejection =
    assertIs<ProfileDecision.Rejected>(this).reason
