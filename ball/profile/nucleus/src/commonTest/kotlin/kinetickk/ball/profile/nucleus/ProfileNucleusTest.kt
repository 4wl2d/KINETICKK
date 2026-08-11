// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandIssuerProvenance
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandPulse
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfilePurgeOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSemanticHandle
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteFailure
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

        val profile = readyState(policy = policy).profile

        assertEquals(policy.coreShapes.first().id, profile.loadout.coreShape)
        assertEquals(policy.weapons.first().id, profile.loadout.selectedWeapon)
        assertEquals(setOf(policy.weapons.first().id), profile.loadout.unlockedWeapons)
        assertEquals(policy.metaUpgrades.size, profile.labProgress.ranks.size)
        assertEquals(policy.rebirth.minimumLevel, profile.rebirthProgress.level)
    }

    @Test
    fun everyLocalIntentIsDeterministicAndEmitsExactlyOneV4Snapshot() {
        val rich = readyState(
            profile = defaultPlayerProfile(TestProfilePolicy).copy(
                economy = PlayerEconomy(matter = 10_000L, lifetimeMatter = 10_000L),
            ),
        )
        val intents: List<ProfilePulse.Business> = listOf(
            ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
            ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
        )
        val inputProfile = rich.profile

        intents.forEach { intent ->
            val pulse = ProfileNucleusPulse.Intent(intent)
            val first = ProfileNucleus.decide(rich, pulse)
            val second = ProfileNucleus.decide(rich, pulse)
            assertEquals(first, second, intent.toString())
            val frame = first.acceptedFrame()
            assertEquals(ProfileRevision(rich.revision.value + 1L), frame.nextState.revision)
            val persist = assertIs<ProfileOutput.PersistV4Snapshot>(frame.outputs.single())
            assertEquals(frame.nextState.profile, persist.snapshot.profile)
            assertEquals(frame.nextState.revision, persist.snapshot.revision)
            assertEquals(TestProfilePolicy.version, persist.snapshot.contentVersion)
            assertIs<ProfilePersistenceStatus.Pending>(frame.nextState.persistence)
            assertEquals(inputProfile, rich.profile, "input State must remain immutable")
        }
    }

    @Test
    fun preferencesOwnCurrentStepSemanticsAndMuteRemainsATargetOwnedCommand() {
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
            ProfileNucleus.decide(
                state,
                ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(adjustment)),
            ).acceptedFrame()
        }

        val muted = ProfileNucleus.decide(
            state,
            ProfileNucleusPulse.ModuleCommand(sessionPulse(ProfileModuleCommand.ToggleMute)),
        ).acceptedFrame().nextState.profile.preferences
        assertFalse(muted.soundEnabled)
        assertFalse(muted.musicEnabled)

        val minimum = state.copy(
            profile = state.profile.copy(
                preferences = state.profile.preferences.copy(masterVolume = 0f),
            ),
        )
        assertEquals(
            ProfileRejection.NoChange,
            ProfileNucleus.decide(
                minimum,
                ProfileNucleusPulse.Intent(
                    ProfilePulse.AdjustPreference(
                        ProfilePreferenceAdjustment.StepMasterVolume(
                            PreferenceAdjustmentDirection.DECREASE,
                        ),
                    ),
                ),
            ).rejection(),
        )
    }

    @Test
    fun localAndModuleBusinessRejectionsPublishNoAcceptedFrame() {
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
            ProfileRejection.NoChange,
            ProfileNucleus.decide(
                state,
                ProfileNucleusPulse.Intent(
                    ProfilePulse.PurchaseOrEquipWeapon(WeaponId.FLUX_WAKE),
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
    fun gameplayDiscoveryIngressAcceptsItemCountAndRejectsFirstNPlusOne() {
        val state = readyState()
        val exact = (0 until TestProfilePolicy.itemCount).toSet()

        val accepted = decideCommand(
            state,
            ProfileModuleCommand.ApplyGameplayProgress(
                GameplayProgressUpdate(discoveredItemIds = exact),
            ),
        ).acceptedFrame()
        assertEquals(exact, accepted.nextState.profile.collection.discoveredItemIds)

        val overflow = (0..TestProfilePolicy.itemCount).toSet()
        assertEquals(
            ProfileRejection.InvalidGameplayProgress(
                ProfileGameplayProgressRejection.TooManyDiscoveries,
            ),
            decideCommand(
                state,
                ProfileModuleCommand.ApplyGameplayProgress(
                    GameplayProgressUpdate(discoveredItemIds = overflow),
                ),
            ).rejection(),
        )
    }

    @Test
    fun targetOwnedCommandOrdersPersistBeforeExactlyCorrelatedCompletion() {
        val state = readyState()
        val pulse = sessionPulse(ProfileModuleCommand.ToggleMute)
        val frame = ProfileNucleus.decide(
            state,
            ProfileNucleusPulse.ModuleCommand(pulse),
        ).acceptedFrame()

        assertEquals(2, frame.outputs.size)
        assertIs<ProfileOutput.PersistV4Snapshot>(frame.outputs[0])
        val completion = assertIs<ProfileOutput.CompleteCommand>(frame.outputs[1]).result
        assertEquals(pulse.commandSource.semanticHandle, completion.semanticHandle)
        assertEquals(pulse.commandSource, completion.commandSource)
        assertEquals(1, completion.sourceOrdinal)
        assertIs<ProfileModuleResult.PreferencesChanged>(completion.result)
    }

    @Test
    fun profileAcceptedFrameAcceptsTwoAndRejectsFirstNPlusOneOutput() {
        val state = readyState()
        val frame = decideCommand(state, ProfileModuleCommand.ToggleMute).acceptedFrame()

        ProfileAcceptedFrame(frame.nextState, frame.outputs)
        assertFailsWith<IllegalArgumentException> {
            ProfileAcceptedFrame(
                frame.nextState,
                (frame.outputs + frame.outputs.first()).toImmutableList(),
            )
        }
    }

    @Test
    fun sessionCoreSelectionPreservesStateSemanticsAndClosedRejections() {
        val unlocked = readyState(
            profile = defaultPlayerProfile(TestProfilePolicy).copy(
                economy = PlayerEconomy(matter = 7L, lifetimeMatter = 25L),
            ),
        )
        val frame = decideCommand(
            unlocked,
            ProfileModuleCommand.SelectCoreShape(CoreShape.PRISM),
        ).acceptedFrame()

        assertEquals(CoreShape.PRISM, frame.nextState.profile.loadout.coreShape)
        assertEquals(unlocked.profile.economy, frame.nextState.profile.economy)
        val completion = assertIs<ProfileOutput.CompleteCommand>(frame.outputs[1]).result
        assertEquals(
            ProfileModuleResult.CoreShapeSelected(CoreShape.PRISM),
            completion.result,
        )

        assertEquals(
            ProfileRejection.NoChange,
            decideCommand(
                readyState(),
                ProfileModuleCommand.SelectCoreShape(CoreShape.ORB),
            ).rejection(),
        )
        assertEquals(
            ProfileRejection.CoreShapeLocked,
            decideCommand(
                readyState(),
                ProfileModuleCommand.SelectCoreShape(CoreShape.PRISM),
            ).rejection(),
        )
    }

    @Test
    fun constructionBootstrapDistinguishesMissingFromKnownReadFailure() {
        val missing = ProfileState.initial(
            TestProfilePolicy,
            ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.NONE),
        )
        assertEquals(ProfileBootstrapStatus.Ready, missing.bootstrap)
        assertEquals(ProfilePersistenceStatus.NotAttempted, missing.persistence)
        assertEquals(defaultPlayerProfile(TestProfilePolicy), missing.profile)

        val failed = ProfileState.initial(
            TestProfilePolicy,
            ProfileBootstrapResourceResult.ResourceFailure(
                ProfileReadFailure.PROVIDER_READ_FAILED,
            ),
        )
        val blocked = assertIs<ProfileBootstrapStatus.Blocked>(failed.bootstrap)
        assertEquals(
            ProfileBootstrapBlockReason.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
            blocked.reason,
        )
        assertIs<ProfileResetStatus.NotRequired>(failed.reset)
        assertEquals(
            ProfileRejection.BootstrapNotReady,
            ProfileNucleus.decide(
                failed,
                ProfileNucleusPulse.ModuleCommand(sessionPulse(ProfileModuleCommand.ToggleMute)),
            ).rejection(),
        )
    }

    @Test
    fun constructionBootstrapLoadsOnlyCompatibleV4AndRestoresItsRevision() {
        val profile = defaultPlayerProfile(TestProfilePolicy).copy(
            economy = PlayerEconomy(55L, 80L),
        )
        val loaded = constructedProfile(profile, revision = 17L)
        assertEquals(ProfileRevision(18L), loaded.revision)
        assertEquals(profile, loaded.profile)
        assertEquals(ProfilePersistenceStatus.Persisted(ProfileRevision(17L)), loaded.persistence)

        val incompatible = constructedProfile(
            profile = profile,
            revision = 17L,
            contentVersion = ContentVersion("other-content"),
        )
        assertIs<ProfileResetStatus.ConfirmationRequired>(incompatible.reset)
        assertNotEquals(profile, incompatible.profile)

        val malformed = ProfileState.initial(
            TestProfilePolicy,
            ProfileBootstrapResourceResult.Rejected(
                ProfileV4Rejection.MALFORMED_JSON,
                ProfileLegacyKeys.NONE,
            ),
        )
        assertIs<ProfileResetStatus.ConfirmationRequired>(malformed.reset)
    }

    @Test
    fun constructionBootstrapRetainsExactBoundsAndRejectsFirstOverflow() {
        val base = defaultPlayerProfile(TestProfilePolicy)
        val exactRanks = TestProfilePolicy.metaUpgrades.map { it.maxRanks }
        val exactDiscoveries = (0 until TestProfilePolicy.itemCount).toSet()
        val exact = base.copy(
            loadout = PlayerLoadout(
                coreShape = base.loadout.coreShape,
                selectedWeapon = base.loadout.selectedWeapon,
                unlockedWeapons = WeaponId.entries.toSet(),
            ),
            labProgress = LabProgress(exactRanks),
            collection = PlayerCollection(exactDiscoveries),
        )

        assertEquals(ProfileBootstrapStatus.Ready, constructedProfile(exact).bootstrap)
        listOf(
            base.copy(labProgress = LabProgress(exactRanks + 0)),
            base.copy(
                labProgress = LabProgress(
                    exactRanks.mapIndexed { index, rank -> if (index == 0) rank + 1 else rank },
                ),
            ),
            base.copy(collection = PlayerCollection((0..TestProfilePolicy.itemCount).toSet())),
        ).forEach(::assertConstructionIncompatible)
    }

    @Test
    fun constructionBootstrapPreferencesAcceptExactMaximaAndRejectOverflow() {
        val base = defaultPlayerProfile(TestProfilePolicy)
        val exact = PlayerPreferences(
            masterVolume = 1f,
            simulationSpeed = SIMULATION_SPEED_OPTIONS.last(),
            textScale = 1.75f,
            damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
        )
        assertEquals(exact, constructedProfile(base.copy(preferences = exact)).profile.preferences)

        listOf(
            exact.copy(masterVolume = Float.fromBits(1f.toBits() + 1)),
            exact.copy(simulationSpeed = Float.fromBits(SIMULATION_SPEED_OPTIONS.last().toBits() + 1)),
            exact.copy(textScale = Float.fromBits(1.75f.toBits() + 1)),
            exact.copy(simulationSpeed = Float.fromBits(1f.toBits() + 1)),
            exact.copy(damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first() + 1),
        ).forEach { preferences ->
            assertConstructionIncompatible(base.copy(preferences = preferences))
        }
    }

    @Test
    fun acceptedResourceEffectStagesOneFactBeforePersistenceCompletion() {
        val mutation = ProfileNucleus.decide(
            readyState(),
            ProfileNucleusPulse.Intent(
                ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ),
        ).acceptedFrame()
        val persist = assertIs<ProfileOutput.PersistV4Snapshot>(mutation.outputs.single())
        assertEquals(0, persist.effectRef.ordinal)
        val acceptedProfile = mutation.nextState.profile

        val unknown = ProfileNucleus.decide(
            mutation.nextState,
            ProfileNucleusPulse.V4WriteCompleted(
                persist.effectRef,
                ProfileV4WriteResult.OutcomeUnknown(
                    ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
            ),
        ).acceptedFrame()
        assertEquals(acceptedProfile, unknown.nextState.profile)
        assertIs<ProfilePersistenceStatus.OutcomeUnknown>(unknown.nextState.persistence)
        assertTrue(unknown.outputs.isEmpty())

        val failedBeforeExecution = ProfileNucleus.decide(
            mutation.nextState,
            ProfileNucleusPulse.V4WriteCompleted(
                persist.effectRef,
                ProfileV4WriteResult.ResourceFailure(
                    ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
                ),
            ),
        ).acceptedFrame()
        assertEquals(acceptedProfile, failedBeforeExecution.nextState.profile)
        assertIs<ProfilePersistenceStatus.ResourceFailure>(failedBeforeExecution.nextState.persistence)
        assertTrue(failedBeforeExecution.outputs.isEmpty())

        assertFailsWith<IllegalStateException> {
            ProfileNucleus.decide(
                mutation.nextState,
                ProfileNucleusPulse.V4WriteCompleted(
                    ProfileEffectRef(persist.effectRef.sourceRevision, persist.effectRef.ordinal + 1),
                    ProfileV4WriteResult.Written(persist.snapshot.revision),
                ),
            )
        }
    }

    @Test
    fun confirmedResetStagesWriteThenPurgeThenExactCompletion() {
        val blocked = legacyBlockedState(ProfileLegacyKeys.ALL)
        val command = sessionPulse(ProfileModuleCommand.ConfirmLegacyReset)
        val confirmed = ProfileNucleus.decide(
            blocked,
            ProfileNucleusPulse.ModuleCommand(command),
        ).acceptedFrame()
        val write = assertIs<ProfileOutput.PersistV4Snapshot>(confirmed.outputs.single())
        assertEquals(0, write.effectRef.ordinal)
        assertTrue(write.snapshot.legacyResetConfirmed)
        assertIs<ProfileResetStatus.WritingFreshV4>(confirmed.nextState.reset)

        val written = ProfileNucleus.decide(
            confirmed.nextState,
            ProfileNucleusPulse.V4WriteCompleted(
                write.effectRef,
                ProfileV4WriteResult.Written(write.snapshot.revision),
            ),
        ).acceptedFrame()
        val purge = assertIs<ProfileOutput.PurgeLegacy>(written.outputs.single())
        assertEquals(0, purge.effectRef.ordinal)
        assertIs<ProfileResetStatus.PurgingLegacy>(written.nextState.reset)

        val purged = ProfileNucleus.decide(
            written.nextState,
            ProfileNucleusPulse.LegacyPurgeCompleted(
                purge.effectRef,
                ProfileLegacyPurgeResult.Purged,
            ),
        ).acceptedFrame()
        val completion = assertIs<ProfileOutput.CompleteCommand>(purged.outputs.single()).result
        assertEquals(command.commandSource, completion.commandSource)
        assertEquals(0, completion.sourceOrdinal)
        assertEquals(ProfileModuleResult.ResetCompleted, completion.result)
        assertEquals(ProfileBootstrapStatus.Ready, purged.nextState.bootstrap)
    }

    @Test
    fun localPartialResultMustNotAutoRetryAndOneExplicitCommandIssuesOneAttempt() {
        val blocked = legacyBlockedState(ProfileLegacyKeys.ALL)
        val confirm = decideCommand(
            blocked,
            ProfileModuleCommand.ConfirmLegacyReset,
        ).acceptedFrame()
        val write = assertIs<ProfileOutput.PersistV4Snapshot>(confirm.outputs.single())
        val purging = ProfileNucleus.decide(
            confirm.nextState,
            ProfileNucleusPulse.V4WriteCompleted(
                write.effectRef,
                ProfileV4WriteResult.Written(write.snapshot.revision),
            ),
        ).acceptedFrame()
        val purge = assertIs<ProfileOutput.PurgeLegacy>(purging.outputs.single())
        val partial = ProfileNucleus.decide(
            purging.nextState,
            ProfileNucleusPulse.LegacyPurgeCompleted(
                purge.effectRef,
                ProfileLegacyPurgeResult.Partial(
                    ProfileLegacyKeys(progressV2 = false, matter = true),
                ),
            ),
        ).acceptedFrame()
        assertIs<ProfileResetStatus.NeedsAttention>(partial.nextState.reset)
        assertTrue(
            partial.outputs.none { it is ProfileOutput.PurgeLegacy },
            "local partial result must not auto-retry",
        )

        val retry = ProfileNucleus.decide(
            partial.nextState,
            ProfileNucleusPulse.ModuleCommand(
                sessionPulse(ProfileModuleCommand.RetryLegacyPurge, sourceRevision = 5L),
            ),
        ).acceptedFrame()
        assertIs<ProfileOutput.PurgeLegacy>(retry.outputs.single())
        assertIs<ProfileResetStatus.PurgingLegacy>(retry.nextState.reset)
    }

    @Test
    fun knownAndUnknownWriteOrPurgeFailuresStayBlockedWithoutRetry() {
        val blocked = legacyBlockedState(ProfileLegacyKeys.ALL)
        val failedConfirmation = decideCommand(
            blocked,
            ProfileModuleCommand.ConfirmLegacyReset,
        ).acceptedFrame()
        val failedWrite = assertIs<ProfileOutput.PersistV4Snapshot>(failedConfirmation.outputs.single())
        val failedBeforeExecution = ProfileNucleus.decide(
            failedConfirmation.nextState,
            ProfileNucleusPulse.V4WriteCompleted(
                failedWrite.effectRef,
                ProfileV4WriteResult.ResourceFailure(
                    ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
                ),
            ),
        ).acceptedFrame()
        assertIs<ProfilePersistenceStatus.ResourceFailure>(failedBeforeExecution.nextState.persistence)
        assertIs<ProfileModuleResult.ResetWriteResourceFailure>(
            assertIs<ProfileOutput.CompleteCommand>(failedBeforeExecution.outputs.single()).result.result,
        )

        val confirmed = decideCommand(
            blocked,
            ProfileModuleCommand.ConfirmLegacyReset,
        ).acceptedFrame()
        val write = assertIs<ProfileOutput.PersistV4Snapshot>(confirmed.outputs.single())
        val unknownWrite = ProfileNucleus.decide(
            confirmed.nextState,
            ProfileNucleusPulse.V4WriteCompleted(
                write.effectRef,
                ProfileV4WriteResult.OutcomeUnknown(
                    ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
            ),
        ).acceptedFrame()
        assertIs<ProfilePersistenceStatus.OutcomeUnknown>(unknownWrite.nextState.persistence)
        assertIs<ProfileModuleResult.ResetWriteOutcomeUnknown>(
            assertIs<ProfileOutput.CompleteCommand>(unknownWrite.outputs.single()).result.result,
        )

        listOf<ProfileLegacyPurgeResult>(
            ProfileLegacyPurgeResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED),
            ProfileLegacyPurgeResult.OutcomeUnknown(
                remaining = ProfileLegacyKeys(progressV2 = false, matter = true),
                unknown = ProfileLegacyKeys(progressV2 = true, matter = false),
                reason = ProfilePurgeOutcomeUnknownReason.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
            ),
        ).forEachIndexed { index, result ->
            val nextConfirm = decideCommand(
                blocked,
                ProfileModuleCommand.ConfirmLegacyReset,
                sourceRevision = 20L + index,
            ).acceptedFrame()
            val nextWrite = assertIs<ProfileOutput.PersistV4Snapshot>(nextConfirm.outputs.single())
            val nextPurging = ProfileNucleus.decide(
                nextConfirm.nextState,
                ProfileNucleusPulse.V4WriteCompleted(
                    nextWrite.effectRef,
                    ProfileV4WriteResult.Written(nextWrite.snapshot.revision),
                ),
            ).acceptedFrame()
            val nextPurge = assertIs<ProfileOutput.PurgeLegacy>(nextPurging.outputs.single())
            val attention = ProfileNucleus.decide(
                nextPurging.nextState,
                ProfileNucleusPulse.LegacyPurgeCompleted(nextPurge.effectRef, result),
            ).acceptedFrame()
            assertIs<ProfileResetStatus.NeedsAttention>(attention.nextState.reset)
            assertTrue(attention.outputs.none { it is ProfileOutput.PurgeLegacy })
            assertIs<ProfileModuleResult.ResetNeedsAttention>(
                assertIs<ProfileOutput.CompleteCommand>(attention.outputs.single()).result.result,
            )
        }
    }

    @Test
    fun resetWriteFailureRetryRequiresExplicitNewCommandAndFreshEffectRef() {
        listOf<ProfileV4WriteResult>(
            ProfileV4WriteResult.Rejected(ProfileV4Rejection.INCONSISTENT_PROFILE),
            ProfileV4WriteResult.ResourceFailure(
                ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
            ),
            ProfileV4WriteResult.OutcomeUnknown(
                ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        ).forEachIndexed { index, failure ->
            val firstPulse = sessionPulse(
                ProfileModuleCommand.ConfirmLegacyReset,
                sourceRevision = 40L + index * 2L,
            )
            val first = ProfileNucleus.decide(
                legacyBlockedState(ProfileLegacyKeys.ALL),
                ProfileNucleusPulse.ModuleCommand(firstPulse),
            ).acceptedFrame()
            val firstWrite = assertIs<ProfileOutput.PersistV4Snapshot>(first.outputs.single())

            val failed = ProfileNucleus.decide(
                first.nextState,
                ProfileNucleusPulse.V4WriteCompleted(firstWrite.effectRef, failure),
            ).acceptedFrame()

            assertIs<ProfileResetStatus.ConfirmationRequired>(failed.nextState.reset)
            assertTrue(failed.outputs.none { output -> output is ProfileOutput.PersistV4Snapshot })
            assertTrue(failed.outputs.none { output -> output is ProfileOutput.PurgeLegacy })
            val completion = assertIs<ProfileOutput.CompleteCommand>(failed.outputs.single())
            when (failure) {
                is ProfileV4WriteResult.Rejected -> assertEquals(
                    ProfileModuleResult.ResetWriteRejected(failure.reason),
                    completion.result.result,
                )
                is ProfileV4WriteResult.ResourceFailure -> assertEquals(
                    ProfileModuleResult.ResetWriteResourceFailure(failure.reason),
                    completion.result.result,
                )
                is ProfileV4WriteResult.OutcomeUnknown -> assertEquals(
                    ProfileModuleResult.ResetWriteOutcomeUnknown(failure.reason),
                    completion.result.result,
                )
                is ProfileV4WriteResult.Written -> error("Not a failure case")
            }

            val retryPulse = sessionPulse(
                ProfileModuleCommand.ConfirmLegacyReset,
                sourceRevision = firstPulse.commandSource.sourceRevision + 1L,
            )
            assertNotEquals(
                firstPulse.commandSource.semanticHandle,
                retryPulse.commandSource.semanticHandle,
            )
            val retry = ProfileNucleus.decide(
                failed.nextState,
                ProfileNucleusPulse.ModuleCommand(retryPulse),
            ).acceptedFrame()
            val retryWrite = assertIs<ProfileOutput.PersistV4Snapshot>(retry.outputs.single())
            assertNotEquals(firstWrite.effectRef, retryWrite.effectRef)
            assertEquals(retry.nextState.revision, retryWrite.effectRef.sourceRevision)
            assertEquals(retry.nextState.revision, retryWrite.snapshot.revision)
            assertEquals(0, retryWrite.effectRef.ordinal)
            val writing = assertIs<ProfileResetStatus.WritingFreshV4>(retry.nextState.reset)
            assertEquals(
                retryPulse.commandSource.semanticHandle,
                writing.completion.commandSource.semanticHandle,
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
        val initial = ProfileState.initial(
            policy,
            ProfileBootstrapResourceResult.Observed(null, ProfileLegacyKeys.NONE),
        )
        return if (profile == null) initial else initial.copy(profile = profile)
    }

    private fun constructedProfile(
        profile: PlayerProfile,
        revision: Long = 0L,
        contentVersion: ContentVersion = TestProfilePolicy.version,
    ): ProfileState = ProfileState.initial(
        TestProfilePolicy,
        ProfileBootstrapResourceResult.Observed(
            snapshot = ProfileV4Snapshot(
                contentVersion = contentVersion,
                revision = ProfileRevision(revision),
                legacyResetConfirmed = false,
                profile = profile,
            ),
            legacyKeys = ProfileLegacyKeys.NONE,
        ),
    )

    private fun assertConstructionIncompatible(profile: PlayerProfile) {
        val state = constructedProfile(profile)
        assertIs<ProfileResetStatus.ConfirmationRequired>(state.reset)
        assertEquals(defaultPlayerProfile(TestProfilePolicy), state.profile)
    }

    private fun legacyBlockedState(keys: ProfileLegacyKeys): ProfileState =
        ProfileState.initial(
            TestProfilePolicy,
            ProfileBootstrapResourceResult.Observed(null, keys),
        )

    private fun decideCommand(
        state: ProfileState,
        command: ProfileModuleCommand,
        sourceRevision: Long = 4L,
    ): ProfileDecision = ProfileNucleus.decide(
        state,
        ProfileNucleusPulse.ModuleCommand(
            if (command is ProfileModuleCommand.ApplyGameplayProgress) {
                gameplayPulse(command, sourceRevision)
            } else {
                sessionPulse(command, sourceRevision)
            },
        ),
    )

    private fun sessionPulse(
        command: ProfileModuleCommand,
        sourceRevision: Long = 4L,
    ): ProfileModuleCommandPulse = modulePulse(
        command = command,
        source = ProfileCommandSource.LocalSession,
        sourceRevision = sourceRevision,
        identity = when (command) {
            is ProfileModuleCommand.SelectCoreShape -> ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE
            ProfileModuleCommand.ToggleMute -> ProfileEffectiveProtocolIdentity.SESSION_MUTE
            ProfileModuleCommand.AdvanceRebirth -> ProfileEffectiveProtocolIdentity.SESSION_REBIRTH
            ProfileModuleCommand.ConfirmLegacyReset -> ProfileEffectiveProtocolIdentity.SESSION_RESET_CONFIRM
            ProfileModuleCommand.RetryLegacyPurge -> ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY
            is ProfileModuleCommand.ApplyGameplayProgress -> error("Gameplay progress is not a session mapping")
        },
        issuer = ProfileCommandIssuerProvenance.LOCAL_SESSION_STATIC_BINDING,
    )

    private fun gameplayPulse(
        command: ProfileModuleCommand.ApplyGameplayProgress,
        sourceRevision: Long,
    ): ProfileModuleCommandPulse = modulePulse(
        command = command,
        source = ProfileCommandSource.GameplayRun(8L),
        sourceRevision = sourceRevision,
        identity = ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
        issuer = ProfileCommandIssuerProvenance.GAMEPLAY_RUN_STATIC_BINDING,
    )

    private fun modulePulse(
        command: ProfileModuleCommand,
        source: ProfileCommandSource,
        sourceRevision: Long,
        identity: ProfileEffectiveProtocolIdentity,
        issuer: ProfileCommandIssuerProvenance,
    ): ProfileModuleCommandPulse {
        val handle = ProfileSemanticHandle(source, sourceRevision, sourceOrdinal = 1)
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
