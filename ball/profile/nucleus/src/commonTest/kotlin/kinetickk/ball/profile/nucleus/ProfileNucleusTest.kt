// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.toImmutableList
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
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileResourceFailure
import kinetickk.ball.profile.api.ProfileResourceResultRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProfileNucleusTest {
    @Test
    fun defaultProfileUsesOnlyTheCapturedPolicyDefaults() {
        val policy = TestProfilePolicy.copy(
            coreShapes = TestProfilePolicy.coreShapes.reversed().toImmutableList(),
            weapons = TestProfilePolicy.weapons.reversed().toImmutableList(),
        )

        val profile = ProfileState.initial(policy).profile

        assertEquals(policy.coreShapes.first().id, profile.loadout.coreShape)
        assertEquals(policy.weapons.first().id, profile.loadout.selectedWeapon)
        assertEquals(setOf(policy.weapons.first().id), profile.loadout.unlockedWeapons)
        assertEquals(policy.metaUpgrades.size, profile.labProgress.ranks.size)
        assertEquals(policy.rebirth.minimumLevel, profile.rebirthProgress.level)
    }

    @Test
    fun everyOrdinaryBusinessPulseIsDeterministicAndEmitsExactlyOneV4Snapshot() {
        val rich = readyState(
            profile = defaultPlayerProfile(TestProfilePolicy).copy(
                economy = PlayerEconomy(matter = 10_000L, lifetimeMatter = 10_000L),
                rebirthProgress = RebirthProgress(level = 0, highestCleared = 0),
            ),
        )
        val pulses = listOf(
            ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ProfilePulse.ToggleMute,
            ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
            ProfilePulse.SelectCoreShape(CoreShape.PRISM),
            ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
            ProfilePulse.AdvanceRebirth,
            ProfilePulse.ApplyGameplayProgress(
                GameplayProgressUpdate(bankedMatter = 5L, discoveredItemIds = setOf(3)),
            ),
        )
        val inputProfile = rich.profile

        pulses.forEach { pulse ->
            val first = ProfileNucleus.decide(rich, pulse)
            val second = ProfileNucleus.decide(rich, pulse)
            assertEquals(first, second, pulse.toString())
            val frame = first.acceptedFrame()
            assertEquals(ProfileRevision(rich.revision.value + 1L), frame.nextState.revision)
            assertEquals(1, frame.outputs.size)
            val persist = assertIs<ProfileOutput.PersistV4Snapshot>(frame.outputs.single())
            assertEquals(frame.nextState.profile, persist.snapshot.profile)
            assertEquals(frame.nextState.revision, persist.snapshot.revision)
            assertEquals(TestProfilePolicy.version, persist.snapshot.contentVersion)
            assertIs<ProfilePersistenceStatus.Pending>(frame.nextState.persistence)
            assertTrue(frame.outputs.size <= MAX_PROFILE_OUTPUTS_PER_DECISION)
            assertEquals(inputProfile, rich.profile, "input State must remain immutable")
        }
    }

    @Test
    fun preferenceAdjustmentOwnsCurrentStepAndMuteSemantics() {
        val state = readyState()
        val adjustmentKinds = listOf(
            ProfilePreferenceAdjustment.ToggleSoundEffects,
            ProfilePreferenceAdjustment.ToggleMusic,
            ProfilePreferenceAdjustment.StepMasterVolume(PreferenceAdjustmentDirection.DECREASE),
            ProfilePreferenceAdjustment.StepSimulationSpeed(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.StepTextScale(PreferenceAdjustmentDirection.DECREASE),
            ProfilePreferenceAdjustment.ToggleScreenShake,
            ProfilePreferenceAdjustment.StepParticleDensity(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.ToggleDamageNumbers,
            ProfilePreferenceAdjustment.StepDamageNumberSize(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.StepDamageNumberFormat(PreferenceAdjustmentDirection.INCREASE),
            ProfilePreferenceAdjustment.StepDamageNumberTierThreshold(PreferenceAdjustmentDirection.INCREASE),
        )
        adjustmentKinds.forEach { adjustment ->
            ProfileNucleus.decide(state, ProfilePulse.AdjustPreference(adjustment)).acceptedFrame()
        }

        val muted = ProfileNucleus.decide(state, ProfilePulse.ToggleMute).acceptedFrame()
            .nextState.profile.preferences
        assertFalse(muted.soundEnabled)
        assertFalse(muted.musicEnabled)
        val reenabledState = state.copy(profile = state.profile.copy(preferences = muted))
        val enabled = ProfileNucleus.decide(reenabledState, ProfilePulse.ToggleMute).acceptedFrame()
            .nextState.profile.preferences
        assertTrue(enabled.soundEnabled)
        assertTrue(enabled.musicEnabled)

        val minimum = state.copy(
            profile = state.profile.copy(
                preferences = state.profile.preferences.copy(masterVolume = 0f),
            ),
        )
        assertEquals(
            ProfileRejection.NoChange,
            ProfileNucleus.decide(
                minimum,
                ProfilePulse.AdjustPreference(
                    ProfilePreferenceAdjustment.StepMasterVolume(PreferenceAdjustmentDirection.DECREASE),
                ),
            ).rejection(),
        )
    }

    @Test
    fun businessRejectionsPublishNoFrameOrOutput() {
        val state = readyState()
        assertEquals(
            ProfileRejection.InsufficientMatter,
            ProfileNucleus.decide(
                state,
                ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
            ).rejection(),
        )
        assertEquals(
            ProfileRejection.CoreShapeLocked,
            ProfileNucleus.decide(state, ProfilePulse.SelectCoreShape(CoreShape.PRISM)).rejection(),
        )
        assertEquals(
            ProfileRejection.NoChange,
            ProfileNucleus.decide(state, ProfilePulse.PurchaseOrEquipWeapon(WeaponId.FLUX_WAKE)).rejection(),
        )
        assertEquals(
            ProfileRejection.RebirthLevelNotCleared,
            ProfileNucleus.decide(state, ProfilePulse.AdvanceRebirth).rejection(),
        )
        val invalidProgress = ProfileNucleus.decide(
            state,
            ProfilePulse.ApplyGameplayProgress(GameplayProgressUpdate(bankedMatter = -1L)),
        ).rejection()
        assertIs<ProfileRejection.InvalidGameplayProgress>(invalidProgress)
        assertEquals(state.revision, ProfileRevision(1L))
    }

    @Test
    fun gameplayProgressAndTerminalPolicyRejectionsUseClosedReasons() {
        val state = readyState()
        val gameplayCases = listOf(
            GameplayProgressUpdate(bankedMatter = -1L) to
                kinetickk.ball.profile.api.ProfileGameplayProgressRejection.NegativeBankedMatter,
            GameplayProgressUpdate(discoveredItemIds = (0..TestProfilePolicy.itemCount).toSet()) to
                kinetickk.ball.profile.api.ProfileGameplayProgressRejection.TooManyDiscoveries,
            GameplayProgressUpdate(discoveredItemIds = setOf(-1)) to
                kinetickk.ball.profile.api.ProfileGameplayProgressRejection.UnknownItem(-1),
            GameplayProgressUpdate(clearedRebirthLevel = -1) to
                kinetickk.ball.profile.api.ProfileGameplayProgressRejection.ClearedLevelBelowMinimum(-1),
            GameplayProgressUpdate(clearedRebirthLevel = 1) to
                kinetickk.ball.profile.api.ProfileGameplayProgressRejection.ClearedLevelAboveCurrent(1),
        )
        gameplayCases.forEach { (update, expected) ->
            assertEquals(
                ProfileRejection.InvalidGameplayProgress(expected),
                ProfileNucleus.decide(
                    state,
                    ProfilePulse.ApplyGameplayProgress(update),
                ).rejection(),
            )
        }

        val maxRanks = List(MetaUpgradeId.entries.size) { index ->
            TestProfilePolicy.metaUpgrade(MetaUpgradeId.entries[index]).maxRanks
        }
        assertEquals(
            ProfileRejection.MetaUpgradeMaxRank,
            ProfileNucleus.decide(
                state.copy(profile = state.profile.copy(labProgress = kinetickk.ball.profile.api.LabProgress(maxRanks))),
                ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
            ).rejection(),
        )
        val maximumRebirth = state.copy(
            profile = state.profile.copy(
                rebirthProgress = RebirthProgress(
                    TestProfilePolicy.rebirth.maximumLevel,
                    TestProfilePolicy.rebirth.maximumLevel,
                ),
            ),
        )
        assertEquals(
            ProfileRejection.RebirthMaximumReached,
            ProfileNucleus.decide(maximumRebirth, ProfilePulse.AdvanceRebirth).rejection(),
        )
    }

    @Test
    fun commandsEnforceClosedSourceKindsAndOrderPersistBeforeCompletion() {
        val state = readyState()
        val pulse = ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleMusic)
        val command = sessionCommand(pulse)
        val context = ProfileContext(command, ProfileCommandAdmission(command.ref))
        val frame = ProfileNucleus.decide(state, pulse, context).acceptedFrame()

        assertEquals(2, frame.outputs.size)
        assertIs<ProfileOutput.PersistV4Snapshot>(frame.outputs[0])
        val completion = assertIs<ProfileOutput.CompleteCommand>(frame.outputs[1]).result
        assertEquals(command.ref, completion.commandRef)
        assertEquals(frame.nextState.revision, completion.targetRevision)
        assertIs<ProfileCommandOutcome.PreferencesChanged>(completion.outcome)

        val purchase = ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY)
        val forbidden = sessionCommand(purchase)
        assertEquals(
            ProfileRejection.InvalidCommandRef(ProfileCommandRefRejection.WRONG_SOURCE_KIND),
            ProfileNucleus.decide(
                state,
                purchase,
                ProfileContext(forbidden, ProfileCommandAdmission(forbidden.ref)),
            ).rejection(),
        )

        val progress = ProfilePulse.ApplyGameplayProgress(GameplayProgressUpdate(bankedMatter = 1L))
        val gameplay = gameplayCommand(progress)
        assertEquals(
            2,
            ProfileNucleus.decide(
                state,
                progress,
                ProfileContext(gameplay, ProfileCommandAdmission(gameplay.ref)),
            ).acceptedFrame().outputs.size,
        )

        assertEquals(
            ProfileRejection.InvalidCommandRef(ProfileCommandRefRejection.ADMISSION_MISMATCH),
            ProfileNucleus.decide(
                state,
                pulse,
                ProfileContext(command, ProfileCommandAdmission(command.ref.copy(ordinal = 8))),
            ).rejection(),
        )
    }

    @Test
    fun bootstrapMissingIsReadyWithoutWriteWhileUnknownIsNondestructivelyBlocked() {
        val initial = ProfileState.initial(TestProfilePolicy)
        val missing = ProfileNucleus.decide(
            initial,
            ProfilePulse.BootstrapCompleted(
                ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.NONE),
            ),
        ).acceptedFrame()
        assertEquals(ProfileBootstrapStatus.Ready, missing.nextState.bootstrap)
        assertEquals(ProfilePersistenceStatus.NotAttempted, missing.nextState.persistence)
        assertTrue(missing.outputs.isEmpty())
        assertEquals(defaultPlayerProfile(TestProfilePolicy), missing.nextState.profile)

        val unknown = ProfileNucleus.decide(
            initial,
            ProfilePulse.BootstrapCompleted(
                ProfileBootstrapResourceResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_READ_FAILED),
            ),
        ).acceptedFrame()
        val blocked = assertIs<ProfileBootstrapStatus.Blocked>(unknown.nextState.bootstrap)
        assertIs<ProfileBootstrapBlockReason.ResourceOutcomeUnknown>(blocked.reason)
        assertIs<ProfileResetStatus.NotRequired>(unknown.nextState.reset)
        assertTrue(unknown.outputs.isEmpty())
        assertEquals(
            ProfileRejection.BootstrapNotReady,
            ProfileNucleus.decide(unknown.nextState, ProfilePulse.ToggleMute).rejection(),
        )
    }

    @Test
    fun bootstrapLoadsOnlyCompatibleV4AndRestoresItsRevision() {
        val profile = defaultPlayerProfile(TestProfilePolicy).copy(
            economy = PlayerEconomy(55L, 80L),
        )
        val loaded = ProfileNucleus.decide(
            ProfileState.initial(TestProfilePolicy),
            ProfilePulse.BootstrapCompleted(
                ProfileBootstrapResourceResult.Observed(
                    snapshot = ProfileV4Snapshot(
                        contentVersion = TestProfilePolicy.version,
                        revision = ProfileRevision(17L),
                        legacyResetConfirmed = false,
                        profile = profile,
                    ),
                    legacyKeys = ProfileLegacyKeys.NONE,
                ),
            ),
        ).acceptedFrame()
        assertEquals(ProfileRevision(18L), loaded.nextState.revision)
        assertEquals(profile, loaded.nextState.profile)
        assertEquals(ProfilePersistenceStatus.Persisted(ProfileRevision(17L)), loaded.nextState.persistence)

        val incompatible = ProfileNucleus.decide(
            ProfileState.initial(TestProfilePolicy),
            ProfilePulse.BootstrapCompleted(
                ProfileBootstrapResourceResult.Observed(
                    snapshot = ProfileV4Snapshot(
                        contentVersion = ContentVersion("other-content"),
                        revision = ProfileRevision(17L),
                        legacyResetConfirmed = false,
                        profile = profile,
                    ),
                    legacyKeys = ProfileLegacyKeys.NONE,
                ),
            ),
        ).acceptedFrame()
        assertIs<ProfileResetStatus.ConfirmationRequired>(incompatible.nextState.reset)
        assertNotEquals(profile, incompatible.nextState.profile)
        assertTrue(incompatible.outputs.isEmpty())

        val malformed = ProfileNucleus.decide(
            ProfileState.initial(TestProfilePolicy),
            ProfilePulse.BootstrapCompleted(
                ProfileBootstrapResourceResult.Rejected(
                    ProfileV4Rejection.MALFORMED_JSON,
                    ProfileLegacyKeys.NONE,
                ),
            ),
        ).acceptedFrame()
        assertIs<ProfileResetStatus.ConfirmationRequired>(malformed.nextState.reset)
        assertTrue(malformed.outputs.isEmpty())
    }

    @Test
    fun mutationWriteFactsAdvanceStatusWithoutRollbackAndRejectWrongCorrelation() {
        val mutation = ProfileNucleus.decide(readyState(), ProfilePulse.ToggleMute).acceptedFrame()
        val persist = assertIs<ProfileOutput.PersistV4Snapshot>(mutation.outputs.single())
        val acceptedProfile = mutation.nextState.profile

        val unknown = ProfileNucleus.decide(
            mutation.nextState,
            ProfilePulse.V4WriteCompleted(
                persist.effectRef,
                ProfileV4WriteResult.OutcomeUnknown(
                    ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
            ),
        ).acceptedFrame()
        assertEquals(acceptedProfile, unknown.nextState.profile)
        assertIs<ProfilePersistenceStatus.OutcomeUnknown>(unknown.nextState.persistence)
        assertTrue(unknown.outputs.isEmpty())

        assertEquals(
            ProfileRejection.UnexpectedResourceResult(
                ProfileResourceResultRejection.EFFECT_REF_MISMATCH,
            ),
            ProfileNucleus.decide(
                mutation.nextState,
                ProfilePulse.V4WriteCompleted(
                    ProfileEffectRef(persist.effectRef.sourceRevision, persist.effectRef.ordinal + 1),
                    ProfileV4WriteResult.Written(persist.snapshot.revision),
                ),
            ).rejection(),
        )
        assertEquals(
            ProfileRejection.UnexpectedResourceResult(
                ProfileResourceResultRejection.RESULT_KIND_MISMATCH,
            ),
            ProfileNucleus.decide(
                mutation.nextState,
                ProfilePulse.V4WriteCompleted(
                    persist.effectRef,
                    ProfileV4WriteResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_READ_FAILED),
                ),
            ).rejection(),
        )
    }

    @Test
    fun confirmedResetWritesDefaultBeforePurgeAndCompletesOnlyAfterPurge() {
        val keys = ProfileLegacyKeys(progressV2 = true, matter = true)
        val blocked = legacyBlockedState(keys)
        val command = sessionCommand(ProfilePulse.ConfirmLegacyReset)
        val confirmed = ProfileNucleus.decide(
            blocked,
            ProfilePulse.ConfirmLegacyReset,
            ProfileContext(command, ProfileCommandAdmission(command.ref)),
        ).acceptedFrame()
        val write = assertIs<ProfileOutput.PersistV4Snapshot>(confirmed.outputs.single())
        assertTrue(write.snapshot.legacyResetConfirmed)
        assertEquals(defaultPlayerProfile(TestProfilePolicy), write.snapshot.profile)
        assertIs<ProfileResetStatus.WritingFreshV4>(confirmed.nextState.reset)

        val written = ProfileNucleus.decide(
            confirmed.nextState,
            ProfilePulse.V4WriteCompleted(
                write.effectRef,
                ProfileV4WriteResult.Written(write.snapshot.revision),
            ),
        ).acceptedFrame()
        val purge = assertIs<ProfileOutput.PurgeLegacy>(written.outputs.single())
        assertIs<ProfileResetStatus.PurgingLegacy>(written.nextState.reset)

        val purged = ProfileNucleus.decide(
            written.nextState,
            ProfilePulse.LegacyPurgeCompleted(purge.effectRef, ProfileLegacyPurgeResult.Purged),
        ).acceptedFrame()
        assertEquals(ProfileBootstrapStatus.Ready, purged.nextState.bootstrap)
        assertEquals(ProfileResetStatus.NotRequired(legacyResetConfirmed = true), purged.nextState.reset)
        val completion = assertIs<ProfileOutput.CompleteCommand>(purged.outputs.single()).result
        assertEquals(ProfileCommandOutcome.ResetCompleted, completion.outcome)
        assertEquals(command.ref, completion.commandRef)
    }

    @Test
    fun resetWriteFailureNeverPurgesAndPartialPurgeRequiresExplicitRetry() {
        val keys = ProfileLegacyKeys(progressV2 = true, matter = true)
        val blocked = legacyBlockedState(keys)
        val confirm = ProfileNucleus.decide(blocked, ProfilePulse.ConfirmLegacyReset).acceptedFrame()
        val write = assertIs<ProfileOutput.PersistV4Snapshot>(confirm.outputs.single())
        val writeFailed = ProfileNucleus.decide(
            confirm.nextState,
            ProfilePulse.V4WriteCompleted(
                write.effectRef,
                ProfileV4WriteResult.Rejected(ProfileV4Rejection.NON_CANONICAL_PAYLOAD),
            ),
        ).acceptedFrame()
        assertIs<ProfileResetStatus.ConfirmationRequired>(writeFailed.nextState.reset)
        assertIs<ProfilePersistenceStatus.Rejected>(writeFailed.nextState.persistence)
        assertTrue(writeFailed.outputs.isEmpty())

        val reconfirm = ProfileNucleus.decide(
            writeFailed.nextState,
            ProfilePulse.ConfirmLegacyReset,
        ).acceptedFrame()
        val retryWrite = assertIs<ProfileOutput.PersistV4Snapshot>(reconfirm.outputs.single())
        val purgeFrame = ProfileNucleus.decide(
            reconfirm.nextState,
            ProfilePulse.V4WriteCompleted(
                retryWrite.effectRef,
                ProfileV4WriteResult.Written(retryWrite.snapshot.revision),
            ),
        ).acceptedFrame()
        val purge = assertIs<ProfileOutput.PurgeLegacy>(purgeFrame.outputs.single())
        val partial = ProfileNucleus.decide(
            purgeFrame.nextState,
            ProfilePulse.LegacyPurgeCompleted(
                purge.effectRef,
                ProfileLegacyPurgeResult.Partial(ProfileLegacyKeys(progressV2 = false, matter = true)),
            ),
        ).acceptedFrame()
        assertIs<ProfileResetStatus.NeedsAttention>(partial.nextState.reset)
        assertTrue(partial.outputs.isEmpty(), "local partial result must not auto-retry")

        val retry = ProfileNucleus.decide(partial.nextState, ProfilePulse.RetryLegacyPurge).acceptedFrame()
        assertIs<ProfileOutput.PurgeLegacy>(retry.outputs.single())
        assertIs<ProfileResetStatus.PurgingLegacy>(retry.nextState.reset)
    }

    @Test
    fun resetUnknownWriteAndPurgeOutcomesRemainBlockedWithoutRollbackOrAutoRetry() {
        val keys = ProfileLegacyKeys.ALL
        val blocked = legacyBlockedState(keys)
        val command = sessionCommand(ProfilePulse.ConfirmLegacyReset)
        val confirmed = ProfileNucleus.decide(
            blocked,
            ProfilePulse.ConfirmLegacyReset,
            ProfileContext(command, ProfileCommandAdmission(command.ref)),
        ).acceptedFrame()
        val write = assertIs<ProfileOutput.PersistV4Snapshot>(confirmed.outputs.single())
        val unknownWrite = ProfileNucleus.decide(
            confirmed.nextState,
            ProfilePulse.V4WriteCompleted(
                write.effectRef,
                ProfileV4WriteResult.OutcomeUnknown(
                    ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
            ),
        ).acceptedFrame()
        assertEquals(write.snapshot.profile, unknownWrite.nextState.profile)
        assertIs<ProfileResetStatus.ConfirmationRequired>(unknownWrite.nextState.reset)
        assertIs<ProfilePersistenceStatus.OutcomeUnknown>(unknownWrite.nextState.persistence)
        assertIs<ProfileCommandOutcome.ResetWriteOutcomeUnknown>(
            assertIs<ProfileOutput.CompleteCommand>(unknownWrite.outputs.single()).result.outcome,
        )

        val localConfirm = ProfileNucleus.decide(blocked, ProfilePulse.ConfirmLegacyReset).acceptedFrame()
        val localWrite = assertIs<ProfileOutput.PersistV4Snapshot>(localConfirm.outputs.single())
        val purging = ProfileNucleus.decide(
            localConfirm.nextState,
            ProfilePulse.V4WriteCompleted(
                localWrite.effectRef,
                ProfileV4WriteResult.Written(localWrite.snapshot.revision),
            ),
        ).acceptedFrame()
        val purge = assertIs<ProfileOutput.PurgeLegacy>(purging.outputs.single())
        listOf(
            ProfileResourceFailure.PROVIDER_READ_FAILED,
            ProfileResourceFailure.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
        ).forEach { reason ->
            val unknownPurge = ProfileNucleus.decide(
                purging.nextState,
                ProfilePulse.LegacyPurgeCompleted(
                    purge.effectRef,
                    ProfileLegacyPurgeResult.OutcomeUnknown(
                        remaining = ProfileLegacyKeys(progressV2 = false, matter = true),
                        unknown = ProfileLegacyKeys(progressV2 = true, matter = false),
                        reason = reason,
                    ),
                ),
            ).acceptedFrame()
            assertIs<ProfileResetStatus.NeedsAttention>(unknownPurge.nextState.reset)
            assertTrue(unknownPurge.outputs.isEmpty())
            assertEquals(
                ProfileRejection.ResetRequired,
                ProfileNucleus.decide(unknownPurge.nextState, ProfilePulse.ToggleMute).rejection(),
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
        profile: PlayerProfile = defaultPlayerProfile(TestProfilePolicy),
    ): ProfileState = ProfileState.initial(TestProfilePolicy).copy(
        revision = ProfileRevision(1L),
        profile = profile,
        bootstrap = ProfileBootstrapStatus.Ready,
        reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = false),
    )

    private fun legacyBlockedState(keys: ProfileLegacyKeys): ProfileState =
        ProfileNucleus.decide(
            ProfileState.initial(TestProfilePolicy),
            ProfilePulse.BootstrapCompleted(ProfileBootstrapResourceResult.Observed(null, keys)),
        ).acceptedFrame().nextState

    private fun sessionCommand(pulse: ProfilePulse.Business): ProfileCommand {
        val ref = ProfileCommandRef(
            sourceInstance = ProfileCommandSource.LocalSession,
            targetInstance = ProfileState.initial(TestProfilePolicy).instanceId,
            sourceRevision = 4L,
            ordinal = 1,
        )
        return ProfileCommand(ref, pulse)
    }

    private fun gameplayCommand(pulse: ProfilePulse.Business): ProfileCommand {
        val ref = ProfileCommandRef(
            sourceInstance = ProfileCommandSource.GameplayRun(8L),
            targetInstance = ProfileState.initial(TestProfilePolicy).instanceId,
            sourceRevision = 11L,
            ordinal = 2,
        )
        return ProfileCommand(ref, pulse)
    }
}

private fun ProfileDecision.acceptedFrame(): ProfileAcceptedFrame =
    assertIs<ProfileDecision.Accepted>(this).frame

private fun ProfileDecision.rejection(): ProfileRejection =
    assertIs<ProfileDecision.Rejected>(this).reason
