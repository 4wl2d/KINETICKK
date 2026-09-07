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
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
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
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileNucleusTest {
    @Test
    fun statisticsSideIsOwnedByProfileAndIncludedInThePersistedSnapshot() {
        val state = readyState()
        val pulse = ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleRunStatisticsSide))
        val frame = ProfileNucleus.decide(state, pulse).acceptedFrame()
        assertTrue(frame.nextState.profile.preferences.runStatisticsOnLeft)
        assertFalse(state.profile.preferences.runStatisticsOnLeft)
        assertEquals(frame.nextState.profile, assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single()).snapshot.profile)
        val persist = assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single())
        val written = ProfileNucleus.decide(frame.nextState, ProfileNucleusPulse.WriteCompleted(
            persist.effectRef, ProfileWriteResult.Written(persist.snapshot.revision),
        )).acceptedFrame()
        val restored = ProfileNucleus.decide(written.nextState, pulse).acceptedFrame()
        assertFalse(restored.nextState.profile.preferences.runStatisticsOnLeft)
    }

    @Test
    fun exactVolumePreservesOtherPreferencesAndPersistsEveryAllowedPercent() {
        val state = readyState()
        for (percent in 0..100) {
            val pulse = ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(
                ProfilePreferenceAdjustment.SetMasterVolume(percent),
            ))
            val decision = ProfileNucleus.decide(state, pulse)
            assertEquals(decision, ProfileNucleus.decide(state, pulse))
            if (percent / 100f == state.profile.preferences.masterVolume) {
                assertEquals(ProfileRejection.NoChange, decision.rejection())
            } else {
                val frame = decision.acceptedFrame()
                val expected = state.profile.copy(preferences = state.profile.preferences.copy(masterVolume = percent / 100f))
                assertEquals(expected, frame.nextState.profile)
                assertEquals(expected, assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single()).snapshot.profile)
                assertEquals(percent / 100f, ProfileNucleus.query(frame.nextState, ProfileQuery.GetPreferences).preferences.masterVolume)
            }
        }
        assertEquals(readyState(), state)
        for (invalid in listOf(-1, 101, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { ProfilePreferenceAdjustment.SetMasterVolume(invalid) }
        }
    }

    @Test
    fun languageSelectionOwnsPreferenceAndSnapshotWithoutChangingOtherProfileFacts() {
        AppLanguage.entries.forEach { language ->
            val previousLanguage = AppLanguage.entries.first { it != language }
            val default = defaultPlayerProfile(TestProfilePolicy)
            val state = readyState(profile = default.copy(
                preferences = default.preferences.copy(language = previousLanguage),
            ))
            val pulse = ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(
                ProfilePreferenceAdjustment.SetLanguage(language),
            ))

            val decision = ProfileNucleus.decide(state, pulse)
            assertEquals(decision, ProfileNucleus.decide(state, pulse))
            val frame = decision.acceptedFrame()
            val expected = state.profile.copy(preferences = state.profile.preferences.copy(language = language))
            assertEquals(expected, frame.nextState.profile)
            assertEquals(expected, assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single()).snapshot.profile)
            assertEquals(language, ProfileNucleus.query(frame.nextState, ProfileQuery.GetPreferences).preferences.language)
            assertEquals(previousLanguage, state.profile.preferences.language)
        }
    }

    @Test
    fun selectingCurrentLanguageRejectsWithoutChangingStateOrPublishingSnapshot() {
        val state = readyState()
        val before = ProfileNucleus.query(state, ProfileQuery.GetPreferences)

        assertEquals(ProfileRejection.NoChange, ProfileNucleus.decide(
            state,
            ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(
                ProfilePreferenceAdjustment.SetLanguage(AppLanguage.Russian),
            )),
        ).rejection())
        assertEquals(before, ProfileNucleus.query(state, ProfileQuery.GetPreferences))
    }

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

        val muted = ProfileNucleus.decide(state, ProfileNucleusPulse.ToggleMute)
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
            ProfileNucleus.decide(state, ProfileNucleusPulse.SelectCoreShape(CoreShape.PRISM)).rejection(),
        )
        assertEquals(
            ProfileRejection.RebirthLevelNotCleared,
            ProfileNucleus.decide(state, ProfileNucleusPulse.AdvanceRebirth).rejection(),
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
                applyGameplayProgress(state, update).rejection(),
            )
        }
    }

    @Test
    fun targetOwnedCommandOrdersPersistenceBeforeCorrelatedCompletion() {
        val frame = ProfileNucleus.decide(
            readyState(),
            ProfileNucleusPulse.ToggleMute,
        ).acceptedFrame()

        assertEquals(2, frame.outputs.size)
        assertIs<ProfileOutput.PersistSnapshot>(frame.outputs[0])
        val completion = assertIs<ProfileOutput.SettingsChanged>(frame.outputs[1]).result
        assertEquals(frame.nextState.revision, completion.revision)
        assertEquals(frame.nextState.profile.preferences, completion.preferences)
        val overflowing = immutableListOf(frame.outputs[0], frame.outputs[1], frame.outputs[0])
        assertEquals(3, overflowing.size)
        assertFailsWith<IllegalArgumentException> { frame.copy(outputs = overflowing) }
    }

    @Test
    fun rebirthDecisionProducesTheAcceptedProgressAfterItsPersistenceRequest() {
        val profile = defaultPlayerProfile(TestProfilePolicy).copy(rebirthProgress = RebirthProgress(2, 2))
        val state = readyState(profile)
        val pulse = ProfileNucleusPulse.AdvanceRebirth
        val decision = ProfileNucleus.decide(state, pulse)
        assertEquals(decision, ProfileNucleus.decide(state, pulse))

        val frame = decision.acceptedFrame()
        assertEquals(2, frame.outputs.size)
        val persist = assertIs<ProfileOutput.PersistSnapshot>(frame.outputs[0])
        val result = assertIs<ProfileOutput.RebirthAdvanced>(frame.outputs[1]).result
        assertEquals(RebirthProgress(3, 2), result.progress)
        assertEquals(frame.nextState.revision, result.revision)
        assertEquals(frame.nextState.profile.rebirthProgress, result.progress)
        assertEquals(frame.nextState.profile, persist.snapshot.profile)
        assertEquals(profile.copy(rebirthProgress = result.progress), frame.nextState.profile)
        assertEquals(profile, state.profile)
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
            ProfileNucleus.decide(failed, ProfileNucleusPulse.ToggleMute).rejection(),
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

    @Test
    fun achievementThresholdsAccumulateAcrossRunsAndUnlockAllSixShapes() {
        var state = readyState()
        fun record(update: GameplayProgressUpdate) {
            val frame = applyGameplayProgress(state, update).acceptedFrame()
            // Every next attempt reconstructs Profile from the accepted persisted value.
            state = constructedProfile(frame.nextState.profile, frame.nextState.revision.value)
        }
        fun unlocked() = ProfileNucleus.query(state, ProfileQuery.GetHomeProgress).unlockedCoreShapes

        assertEquals(setOf(CoreShape.ORB), unlocked())
        record(GameplayProgressUpdate(eliteKills = 2, dashHits = 19))
        assertEquals(setOf(CoreShape.ORB), unlocked())
        record(GameplayProgressUpdate(eliteKills = 1, dashHits = 1, completedOrbits = 1))
        assertEquals(setOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD, CoreShape.RING), unlocked())
        record(GameplayProgressUpdate(architectDefeatedWith = CoreShape.ORB))
        assertTrue(CoreShape.DIAMOND in unlocked())
        record(GameplayProgressUpdate(architectDefeatedWith = CoreShape.ORB))
        record(GameplayProgressUpdate(architectDefeatedWith = CoreShape.PRISM))
        assertFalse(CoreShape.TESSERACT in unlocked())
        record(GameplayProgressUpdate(architectDefeatedWith = CoreShape.SHARD))
        assertEquals(CoreShape.entries.toSet(), unlocked())
        assertEquals(4, state.profile.characterAchievements.architectVictories)
        assertEquals(3, state.profile.characterAchievements.victoriousCharacters.size)
        assertEquals(0L, state.profile.economy.lifetimeMatter)
        repeat(3) { assertEquals(CoreShape.entries.toSet(), unlocked()) }
    }

    @Test
    fun malformedAchievementDeltaAndLockedCharacterVictoryPreserveProfile() {
        val state = readyState()
        listOf(
            GameplayProgressUpdate(eliteKills = -1),
            GameplayProgressUpdate(dashHits = -1),
            GameplayProgressUpdate(completedOrbits = -1),
        ).forEach { update ->
            assertEquals(
                ProfileRejection.InvalidGameplayProgress(ProfileGameplayProgressRejection.NegativeAchievementProgress),
                applyGameplayProgress(state, update).rejection(),
            )
        }
        assertEquals(
            ProfileRejection.InvalidGameplayProgress(ProfileGameplayProgressRejection.VictoryCharacterLocked),
            applyGameplayProgress(state,
                GameplayProgressUpdate(architectDefeatedWith = CoreShape.TESSERACT),
            ).rejection(),
        )
        assertEquals(defaultPlayerProfile(TestProfilePolicy), state.profile)
    }

    @Test
    fun achievementCountersSaturateWithoutOverflowAndTheDecisionIsDeterministic() {
        val state = readyState(profile = defaultPlayerProfile(TestProfilePolicy).copy(
            characterAchievements = kinetickk.ball.profile.api.CharacterAchievementProgress(
                eliteKills = Long.MAX_VALUE - 1,
                dashHits = Long.MAX_VALUE - 1,
                completedOrbits = Long.MAX_VALUE - 1,
            ),
        ))
        val update = GameplayProgressUpdate(
            eliteKills = Int.MAX_VALUE,
            dashHits = Int.MAX_VALUE,
            completedOrbits = Int.MAX_VALUE,
        )
        val first = applyGameplayProgress(state, update).acceptedFrame()
        val second = applyGameplayProgress(state, update).acceptedFrame()
        assertEquals(first, second)
        assertEquals(2, first.outputs.size)
        assertIs<ProfileOutput.PersistSnapshot>(first.outputs[0])
        val result = assertIs<ProfileOutput.ProgressApplied>(first.outputs[1]).result
        assertEquals(first.nextState.revision, result.revision)
        assertEquals(Long.MAX_VALUE, first.nextState.profile.characterAchievements.eliteKills)
        assertEquals(Long.MAX_VALUE, first.nextState.profile.characterAchievements.dashHits)
        assertEquals(Long.MAX_VALUE, first.nextState.profile.characterAchievements.completedOrbits)
        assertEquals(Long.MAX_VALUE - 1, state.profile.characterAchievements.eliteKills)
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

    private fun applyGameplayProgress(
        state: ProfileState,
        update: GameplayProgressUpdate,
    ): ProfileDecision = ProfileNucleus.decide(
        state,
        ProfileNucleusPulse.ApplyGameplayProgress(update),
    )
}

private fun ProfileDecision.acceptedFrame(): ProfileAcceptedFrame =
    assertIs<ProfileDecision.Accepted>(this).frame

private fun ProfileDecision.rejection(): ProfileRejection =
    assertIs<ProfileDecision.Rejected>(this).reason
