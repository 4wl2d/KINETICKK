// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
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
import kinetickk.ball.profile.api.isAscendant
import kinetickk.ball.profile.api.rank
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
    fun theMinimapToggleIsOwnedByProfileAndPersisted() {
        val state = readyState()
        assertTrue(state.profile.preferences.minimapEnabled)
        val pulse = ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleMinimap))
        val frame = ProfileNucleus.decide(state, pulse).acceptedFrame()
        assertFalse(frame.nextState.profile.preferences.minimapEnabled)
        assertTrue(state.profile.preferences.minimapEnabled, "the committed state is untouched")
        assertEquals(
            frame.nextState.profile.copy(),
            assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single()).snapshot.profile,
        )
        assertEquals(state.profile.preferences.copy(minimapEnabled = false), frame.nextState.profile.preferences)
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
    fun colorVisionSelectionOwnsPreferenceAndSnapshotAndCurrentChoiceIsNoChange() {
        ColorVision.entries.forEach { target ->
            ColorVision.entries.forEach { previous ->
                val default = defaultPlayerProfile(TestProfilePolicy)
                val state = readyState(profile = default.copy(
                    preferences = default.preferences.copy(colorVision = previous),
                ))
                val pulse = ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(
                    ProfilePreferenceAdjustment.SetColorVision(target),
                ))
                val decision = ProfileNucleus.decide(state, pulse)
                assertEquals(decision, ProfileNucleus.decide(state, pulse))
                if (target == previous) {
                    assertEquals(ProfileRejection.NoChange, decision.rejection())
                } else {
                    val frame = decision.acceptedFrame()
                    val expected = state.profile.copy(preferences = state.profile.preferences.copy(colorVision = target))
                    assertEquals(expected, frame.nextState.profile)
                    assertEquals(expected, assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single()).snapshot.profile)
                    assertEquals(target, ProfileNucleus.query(frame.nextState, ProfileQuery.GetPreferences).preferences.colorVision)
                }
                assertEquals(previous, state.profile.preferences.colorVision)
            }
        }
        assertEquals(ColorVision.DEFAULT, defaultPlayerProfile(TestProfilePolicy).preferences.colorVision)
    }

    @Test
    fun segmentedPreferencesAcceptEveryDirectChoiceAndRejectTheCurrentOne() {
        val state = readyState()
        val current = state.profile.preferences
        val choices = SIMULATION_SPEED_OPTIONS.map { speed ->
            ProfilePreferenceAdjustment.SetSimulationSpeed(speed) to current.copy(simulationSpeed = speed)
        } + ParticleDensity.entries.map { density ->
            ProfilePreferenceAdjustment.SetParticleDensity(density) to current.copy(particleDensity = density)
        } + DamageNumberSize.entries.map { size ->
            ProfilePreferenceAdjustment.SetDamageNumberSize(size) to current.copy(damageNumberSize = size)
        } + DamageNumberFormat.entries.map { format ->
            ProfilePreferenceAdjustment.SetDamageNumberFormat(format) to current.copy(damageNumberFormat = format)
        }
        choices.forEach { (adjustment, expected) ->
            val decision = ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(adjustment)))
            if (expected == current) {
                assertEquals(ProfileRejection.NoChange, decision.rejection(), adjustment.toString())
            } else {
                val frame = decision.acceptedFrame()
                assertEquals(expected, frame.nextState.profile.preferences, adjustment.toString())
                assertEquals(expected, assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.single()).snapshot.profile.preferences)
            }
        }
        assertEquals(readyState(), state)
        for (unlisted in listOf(0.5f, 1.1f, Float.fromBits(1f.toBits() + 1), 2.5f, Float.NaN)) {
            assertFailsWith<IllegalArgumentException> { ProfilePreferenceAdjustment.SetSimulationSpeed(unlisted) }
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
        assertEquals(MatrixProgress(), profile.matrix)
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
            ProfilePulse.PurchaseMatrixNode(MatrixNodeId.REINFORCED_HULL),
            ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
            ProfilePulse.PurchaseCharacterUpgrade(CoreShape.ORB),
            ProfilePulse.PurchaseWeaponUpgrade(WeaponId.FLUX_WAKE),
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
    fun droppingToTierZeroAfterRankFiveKeepsRankFiveWeaponsCharactersAndMatrixNodes() {
        val rich = PlayerEconomy(matter = 100_000L, lifetimeMatter = 100_000L)
        val ranked = readyState(defaultPlayerProfile(TestProfilePolicy).copy(economy = rich,
            rebirthProgress = RebirthProgress(level = 5, highestCleared = 4)))
        val dropped = ProfileNucleus.decide(ranked, ProfileNucleusPulse.SelectRebirthTier(0)).acceptedFrame().nextState
            .let { readyState(it.profile) }
        assertEquals(RebirthProgress(level = 0, highestCleared = 4), dropped.profile.rebirthProgress)
        // Rank 5 content stays open at tier 0: the Solar Sweep (Reborn 5) sells, the Crescent (Reborn 5) and the
        // Pentagon (Reborn 3) are unlocked, and a Reborn 5 Matrix node can be bought.
        val solar = TestProfilePolicy.weapon(WeaponId.SOLAR_SWEEP)
        assertEquals(5, solar.minimumRebirth)
        val bought = ProfileNucleus.decide(dropped, ProfileNucleusPulse.Intent(ProfilePulse.PurchaseOrEquipWeapon(solar.id)))
            .acceptedFrame().nextState.profile
        assertTrue(solar.id in bought.loadout.unlockedWeapons)
        val unlocked = ProfileNucleus.query(dropped, ProfileQuery.GetHomeProgress).unlockedCoreShapes
        assertTrue(CoreShape.CRESCENT in unlocked && CoreShape.PENTAGON in unlocked, "$unlocked")
        assertFalse(CoreShape.HEXAGON in unlocked, "the Hexagon needs rank 7")
        val crescent = ProfileNucleus.decide(dropped, ProfileNucleusPulse.SelectCoreShape(CoreShape.CRESCENT)).acceptedFrame()
        assertEquals(CoreShape.CRESCENT, crescent.nextState.profile.loadout.coreShape)
        assertEquals(5, ProfileNucleus.query(dropped, ProfileQuery.GetMatrixProgress).snapshot.rebirthRank)
        assertEquals(5, ProfileNucleus.query(dropped, ProfileQuery.GetLoadout).snapshot.rebirthRank)
    }

    @Test
    fun aWeaponRebirthGateOpensAtExactlyItsRankBeforeMatterIsChecked() {
        val gated = TestProfilePolicy.weapon(WeaponId.TETHER_ARC)
        assertEquals(2, gated.minimumRebirth)
        fun purchase(state: ProfileState) =
            ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.PurchaseOrEquipWeapon(gated.id)))
        fun rich(highestCleared: Int) = readyState(
            profile = defaultPlayerProfile(TestProfilePolicy).copy(
                economy = PlayerEconomy(matter = 10_000L, lifetimeMatter = 10_000L),
                rebirthProgress = RebirthProgress(level = 0, highestCleared = highestCleared),
            ),
        )

        // Rank = highest cleared tier + 1: one clear is rank 1, one below the gate.
        val rankOne = rich(highestCleared = 0)
        assertEquals(ProfileRejection.WeaponRebirthLocked, purchase(rankOne).rejection())
        // The gate comes before the price: a poor rank-one profile still hears about the gate.
        val poor = readyState(profile = rankOne.profile.copy(economy = PlayerEconomy()))
        assertEquals(ProfileRejection.WeaponRebirthLocked, purchase(poor).rejection())

        val rankTwo = rich(highestCleared = 1)
        val bought = purchase(rankTwo).acceptedFrame().nextState.profile
        assertEquals(gated.id, bought.loadout.selectedWeapon)
        assertTrue(gated.id in bought.loadout.unlockedWeapons)
        assertEquals(10_000L - gated.permanentUnlockCost, bought.economy.matter)

        // Owning it already: equipping never re-checks the gate.
        val owned = readyState(
            profile = rankOne.profile.copy(
                loadout = PlayerLoadout(
                    coreShape = rankOne.profile.loadout.coreShape,
                    unlockedWeapons = rankOne.profile.loadout.unlockedWeapons + gated.id,
                ),
            ),
        )
        assertEquals(gated.id, purchase(owned).acceptedFrame().nextState.profile.loadout.selectedWeapon)
    }

    @Test
    fun upgradePriceDoublesAndSaturates() {
        // Ten Refit levels of a base-55 character cost 55, 110, … 28,160: 56,265 in total.
        var state = readyState(defaultPlayerProfile(TestProfilePolicy).copy(
            economy = PlayerEconomy(matter = 56_265L, lifetimeMatter = 60_000L),
        ))
        val paid = (0 until TestProfilePolicy.upgrades.maxLevel).map { level ->
            val before = state.profile.economy.matter
            state = ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.PurchaseCharacterUpgrade(CoreShape.ORB)))
                .acceptedFrame().persisted()
            assertEquals(level + 1, state.profile.upgrades.characterLevel(CoreShape.ORB))
            before - state.profile.economy.matter
        }
        assertEquals((0 until 10).map { 55L shl it }, paid)
        assertEquals(PlayerEconomy(matter = 0L, lifetimeMatter = 60_000L), state.profile.economy)

        // The price function the decision charges saturates instead of overflowing.
        val policy = TestProfilePolicy.upgrades
        assertEquals(1L shl 62, policy.nextCost(base = 1, level = 62))
        assertEquals(Long.MAX_VALUE, policy.nextCost(base = 2, level = 62))
        assertEquals(Long.MAX_VALUE, policy.nextCost(base = 1, level = 63))
        assertEquals(Long.MAX_VALUE, policy.nextCost(base = 10_000, level = 200))
    }

    @Test
    fun businessRejectionsPublishNoAcceptedFrame() {
        val state = readyState()
        assertEquals(
            ProfileRejection.InsufficientMatter,
            ProfileNucleus.decide(
                state,
                ProfileNucleusPulse.Intent(
                    ProfilePulse.PurchaseMatrixNode(MatrixNodeId.REINFORCED_HULL),
                ),
            ).rejection(),
        )
        assertEquals(
            ProfileRejection.CoreShapeLocked,
            ProfileNucleus.decide(state, ProfileNucleusPulse.SelectCoreShape(CoreShape.PRISM)).rejection(),
        )
        assertEquals(
            ProfileRejection.RebirthTierLocked,
            ProfileNucleus.decide(state, ProfileNucleusPulse.SelectRebirthTier(1)).rejection(),
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
            GameplayProgressUpdate(discoveredItemIds = emptySet(), acquiredItemIds = List(MAX_RECENT_ITEM_IDS + 1) { 0 }) to
                ProfileGameplayProgressRejection.TooManyAcquiredItems,
            GameplayProgressUpdate(discoveredItemIds = emptySet(), acquiredItemIds = listOf(0, TestProfilePolicy.itemCount)) to
                ProfileGameplayProgressRejection.UnknownItem(TestProfilePolicy.itemCount),
            GameplayProgressUpdate(clearedRebirthLevel = -1) to
                ProfileGameplayProgressRejection.ClearedLevelBelowMinimum(-1),
            GameplayProgressUpdate(clearedRebirthLevel = 1) to
                ProfileGameplayProgressRejection.ClearedLevelAboveRank(1),
            GameplayProgressUpdate(discoveredItemIds = emptySet(), victoryRelicIds = listOf(RelicId.GLASS_COMET)) to
                ProfileGameplayProgressRejection.VictoryRelicsWithoutClear,
            GameplayProgressUpdate(
                discoveredItemIds = emptySet(),
                clearedRebirthLevel = 0,
                victoryRelicIds = RelicId.entries.take(MAX_HEIRLOOM_RELICS + 1),
            ) to ProfileGameplayProgressRejection.TooManyVictoryRelics,
            GameplayProgressUpdate(
                discoveredItemIds = emptySet(),
                clearedRebirthLevel = 0,
                victoryRelicIds = listOf(RelicId.GLASS_COMET, RelicId.BLINK_DRIVE, RelicId.GLASS_COMET),
            ) to ProfileGameplayProgressRejection.DuplicateVictoryRelic(RelicId.GLASS_COMET),
        )

        cases.forEach { (update, expected) ->
            assertEquals(
                ProfileRejection.InvalidGameplayProgress(expected),
                applyGameplayProgress(state, update).rejection(),
            )
        }
    }

    @Test
    fun victoryRecordsHeirloomsInSlotOrderAndResolvingTheOfferSpendsThem() {
        val state = readyState()
        val slots = RelicId.entries.reversed().take(MAX_HEIRLOOM_RELICS)
        val victory = GameplayProgressUpdate(
            discoveredItemIds = emptySet(),
            clearedRebirthLevel = 0,
            architectDefeatedWith = CoreShape.ORB,
            victoryRelicIds = slots,
        )
        val won = applyGameplayProgress(state, victory).acceptedFrame()
        assertEquals(slots, won.nextState.profile.collection.heirloomRelicIds)
        assertEquals(won.nextState.profile, assertIs<ProfileOutput.PersistSnapshot>(won.outputs[0]).snapshot.profile)
        val bootstrap = assertIs<ProfileRunBootstrapResult.Ready>(
            ProfileNucleus.query(won.nextState, ProfileQuery.GetRunBootstrap).result,
        )
        assertEquals(slots, bootstrap.snapshot.collection.heirloomRelicIds)
        assertTrue(state.profile.collection.heirloomRelicIds.isEmpty())

        // The next run resolves its Heirloom offer; spending is idempotent once nothing is left.
        val resolved = applyGameplayProgress(won.persisted(), GameplayProgressUpdate(heirloomResolved = true)).acceptedFrame()
        assertTrue(resolved.nextState.profile.collection.heirloomRelicIds.isEmpty())
        assertEquals(won.nextState.profile.collection.copy(heirloomRelicIds = immutableListOf()), resolved.nextState.profile.collection)
        assertEquals(
            ProfileRejection.NoChange,
            applyGameplayProgress(resolved.persisted(), GameplayProgressUpdate(heirloomResolved = true)).rejection(),
        )

        // Spending the old Heirlooms and winning again in one update keeps only the new victory.
        val again = listOf(RelicId.HOLLOW_CROWN)
        val replaced = applyGameplayProgress(won.persisted(), GameplayProgressUpdate(
            discoveredItemIds = emptySet(),
            clearedRebirthLevel = 0,
            victoryRelicIds = again,
            heirloomResolved = true,
        )).acceptedFrame()
        assertEquals(again, replaced.nextState.profile.collection.heirloomRelicIds)
        // A victory without bound Relics leaves no Heirloom behind.
        val bare = applyGameplayProgress(won.persisted(), GameplayProgressUpdate(
            discoveredItemIds = emptySet(),
            clearedRebirthLevel = 0,
            victoryRelicIds = emptyList(),
        )).acceptedFrame()
        assertTrue(bare.nextState.profile.collection.heirloomRelicIds.isEmpty())
        // Progress without a victory keeps the remembered Heirlooms untouched.
        val banked = applyGameplayProgress(won.persisted(), GameplayProgressUpdate(bankedMatter = 5L)).acceptedFrame()
        assertEquals(slots, banked.nextState.profile.collection.heirloomRelicIds)
    }

    @Test
    fun reactionAndPairingDiscoveriesMergeIdempotentlyWithoutTouchingOtherFacts() {
        val state = readyState()
        val first = applyGameplayProgress(state, GameplayProgressUpdate(
            discoveredItemIds = emptySet(),
            discoveredReactionIds = setOf(ReactionId.FUSION, ReactionId.EMBRITTLE),
            discoveredPairingIds = setOf(RelicPairingId.MELTDOWN),
        )).acceptedFrame()
        val second = applyGameplayProgress(first.persisted(), GameplayProgressUpdate(
            discoveredItemIds = emptySet(),
            discoveredReactionIds = setOf(ReactionId.EMBRITTLE, ReactionId.ROT_WELL),
            discoveredPairingIds = setOf(RelicPairingId.CONTRADICTION),
        )).acceptedFrame()
        val collection = second.nextState.profile.collection
        assertEquals(setOf(ReactionId.FUSION, ReactionId.EMBRITTLE, ReactionId.ROT_WELL), collection.discoveredReactionIds)
        assertEquals(setOf(RelicPairingId.MELTDOWN, RelicPairingId.CONTRADICTION), collection.discoveredPairingIds)
        assertEquals(
            state.profile.copy(collection = state.profile.collection.copy(
                discoveredReactionIds = collection.discoveredReactionIds,
                discoveredPairingIds = collection.discoveredPairingIds,
            )),
            second.nextState.profile,
        )
        assertEquals(
            ProfileRejection.NoChange,
            applyGameplayProgress(second.persisted(), GameplayProgressUpdate(
                discoveredItemIds = emptySet(),
                discoveredReactionIds = setOf(ReactionId.FUSION),
                discoveredPairingIds = setOf(RelicPairingId.MELTDOWN),
            )).rejection(),
        )
        val everything = applyGameplayProgress(state, GameplayProgressUpdate(
            discoveredItemIds = emptySet(),
            discoveredReactionIds = ReactionId.entries.toSet(),
            discoveredPairingIds = RelicPairingId.entries.toSet(),
        )).acceptedFrame().nextState.profile.collection
        assertEquals(ReactionId.entries.toSet(), everything.discoveredReactionIds)
        assertEquals(RelicPairingId.entries.toSet(), everything.discoveredPairingIds)
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
        val pulse = ProfileNucleusPulse.SelectRebirthTier(3)
        val decision = ProfileNucleus.decide(state, pulse)
        assertEquals(decision, ProfileNucleus.decide(state, pulse))

        val frame = decision.acceptedFrame()
        assertEquals(2, frame.outputs.size)
        val persist = assertIs<ProfileOutput.PersistSnapshot>(frame.outputs[0])
        val result = assertIs<ProfileOutput.RebirthTierSelected>(frame.outputs[1]).result
        assertEquals(RebirthProgress(3, 2), result.progress)
        assertEquals(frame.nextState.revision, result.revision)
        assertEquals(frame.nextState.profile.rebirthProgress, result.progress)
        assertEquals(frame.nextState.profile, persist.snapshot.profile)
        assertEquals(profile.copy(rebirthProgress = result.progress), frame.nextState.profile)
        assertEquals(profile, state.profile)
    }

    @Test
    fun anyTierFromTheMinimumToTheRankCanBeSelectedAndNothingAbove() {
        val profile = defaultPlayerProfile(TestProfilePolicy).copy(
            economy = PlayerEconomy(matter = 77L, lifetimeMatter = 90L),
            rebirthProgress = RebirthProgress(level = 5, highestCleared = 4),
        )
        val state = readyState(profile)
        // Dropping back to tier 0 keeps every other slice and the highest clear (the rank stays 5).
        (0..5).forEach { tier ->
            val next = ProfileNucleus.decide(state, ProfileNucleusPulse.SelectRebirthTier(tier)).acceptedFrame().nextState
            assertEquals(RebirthProgress(level = tier, highestCleared = 4), next.profile.rebirthProgress, "tier $tier")
            assertEquals(profile.copy(rebirthProgress = next.profile.rebirthProgress), next.profile)
            assertEquals(5, next.profile.rebirthProgress.rank)
        }
        listOf(6, 10, -1).forEach { tier ->
            assertEquals(ProfileRejection.RebirthTierLocked,
                ProfileNucleus.decide(state, ProfileNucleusPulse.SelectRebirthTier(tier)).rejection(), "tier $tier")
        }
        // A fresh profile may select only tier 0; reselecting it confirms the tier and still publishes.
        val fresh = readyState()
        val again = ProfileNucleus.decide(fresh, ProfileNucleusPulse.SelectRebirthTier(0)).acceptedFrame()
        assertEquals(RebirthProgress(0, -1), assertIs<ProfileOutput.RebirthTierSelected>(again.outputs[1]).result.progress)
        assertEquals(ProfileRejection.RebirthTierLocked,
            ProfileNucleus.decide(fresh, ProfileNucleusPulse.SelectRebirthTier(1)).rejection())
        // Ascendant profiles may select every tier up to the last one.
        val ascendant = readyState(defaultPlayerProfile(TestProfilePolicy).copy(rebirthProgress = RebirthProgress(3, 10)))
        assertEquals(10, ProfileNucleus.decide(ascendant, ProfileNucleusPulse.SelectRebirthTier(10)).acceptedFrame()
            .nextState.profile.rebirthProgress.level)
    }

    @Test
    fun aNewTierWaitsOnlyWhileTheRankIsUnclearedAndNotSelected() {
        fun waiting(progress: RebirthProgress): Pair<Boolean, Boolean> {
            val state = readyState(defaultPlayerProfile(TestProfilePolicy).copy(rebirthProgress = progress))
            return ProfileNucleus.query(state, ProfileQuery.GetHomeProgress).canAdvanceRebirth to
                ProfileNucleus.query(state, ProfileQuery.GetRebirthProgress).canAdvance
        }
        assertEquals(true to true, waiting(RebirthProgress(level = 0, highestCleared = 4)))
        assertEquals(true to true, waiting(RebirthProgress(level = 4, highestCleared = 4)))
        assertEquals(false to false, waiting(RebirthProgress(level = 5, highestCleared = 4)))
        assertEquals(false to false, waiting(RebirthProgress()))
        // Every tier is cleared: replaying a lower one leaves no new tier waiting.
        assertEquals(false to false, waiting(RebirthProgress(level = 3, highestCleared = 10)))
        assertEquals(false to false, waiting(RebirthProgress(level = 10, highestCleared = 10)))
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

        val heirlooms = listOf(
            RelicId.entries.take(MAX_HEIRLOOM_RELICS + 1),
            listOf(RelicId.GLASS_COMET, RelicId.GLASS_COMET),
        ).map { relics ->
            constructedProfile(default.copy(collection = PlayerCollection(emptySet(), heirloomRelicIds = relics)))
        }

        (listOf(missing, rejected, incompatible) + exhausted + heirlooms).forEach { state ->
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
    fun bootstrapForgetsRetiredItemDiscoveriesWithoutResettingTheEconomy() {
        val catalogEnd = TestProfilePolicy.itemCount
        val profile = defaultPlayerProfile(TestProfilePolicy).copy(
            economy = PlayerEconomy(matter = 640L, lifetimeMatter = 9_000L),
            matrix = MatrixProgress(MatrixNodeId.entries.map { if (it.ordinal < 3) 1 else 0 }),
            rebirthProgress = RebirthProgress(3, 2),
            collection = PlayerCollection(
                discoveredItemIds = setOf(1, catalogEnd - 1, catalogEnd, 399),
                newItemIds = setOf(1, catalogEnd, 399),
                discoveredRelicIds = setOf(RelicId.GYRE_ENGINE),
                discoveredReactionIds = setOf(ReactionId.SUPERCONDUCT),
                heirloomRelicIds = listOf(RelicId.GYRE_ENGINE),
            ),
        )

        val loaded = constructedProfile(profile, revision = 41L)

        assertEquals(ProfileRevision(42L), loaded.revision)
        assertEquals(ProfilePersistenceStatus.Persisted(ProfileRevision(41L)), loaded.persistence)
        assertEquals(
            profile.copy(collection = profile.collection.copy(
                discoveredItemIds = kinetickk.foundation.collections.immutableSetOf(1, catalogEnd - 1),
                newItemIds = kinetickk.foundation.collections.immutableSetOf(1),
            )),
            loaded.profile,
        )
        // The filtered collection is what the next accepted write persists.
        val written = ProfileNucleus.decide(
            loaded,
            ProfileNucleusPulse.Intent(ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleScreenShake)),
        ).acceptedFrame()
        assertEquals(
            setOf(1, catalogEnd - 1),
            assertIs<ProfileOutput.PersistSnapshot>(written.outputs.single()).snapshot.profile.collection.discoveredItemIds,
        )
        // An in-catalog unseen marker without its discovery is still an inconsistent save.
        val inconsistent = constructedProfile(profile.copy(
            collection = PlayerCollection(setOf(1), newItemIds = setOf(2)),
        ))
        assertEquals(defaultPlayerProfile(TestProfilePolicy), inconsistent.profile)
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
            ProfileNucleus.query(state, ProfileQuery.GetMatrixProgress),
            ProfileNucleus.query(state, ProfileQuery.GetLoadout),
            ProfileNucleus.query(state, ProfileQuery.GetCollection),
            ProfileNucleus.query(state, ProfileQuery.GetRebirthProgress),
            ProfileNucleus.query(state, ProfileQuery.GetPersistenceStatus),
            ProfileNucleus.query(state, ProfileQuery.GetHangar),
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
        assertEquals(state.profile.matrix, assertIs<MatrixProgressProjection>(projections[3]).snapshot.progress)
        assertEquals(1, assertIs<MatrixProgressProjection>(projections[3]).snapshot.rebirthRank)
        assertEquals(state.profile.loadout, assertIs<LoadoutProjection>(projections[4]).snapshot.loadout)
        // The Armory reads the weapon unlock gates' rank from the loadout projection.
        assertEquals(1, assertIs<LoadoutProjection>(projections[4]).snapshot.rebirthRank)
        assertEquals(state.profile.collection, assertIs<CollectionProjection>(projections[5]).collection)
        assertTrue(assertIs<RebirthProgressProjection>(projections[6]).canAdvance)
        assertEquals(state.persistence, assertIs<PersistenceStatusProjection>(projections[7]).persistence)
    }

    @Test
    fun achievementThresholdsAccumulateAcrossRunsAndUnlockAllTwelveShapes() {
        var state = readyState()
        fun record(update: GameplayProgressUpdate) {
            val frame = applyGameplayProgress(state, update).acceptedFrame()
            // Every next attempt reconstructs Profile from the accepted persisted value.
            state = constructedProfile(frame.nextState.profile, frame.nextState.revision.value)
        }
        fun unlocked() = ProfileNucleus.query(state, ProfileQuery.GetHomeProgress).unlockedCoreShapes
        fun rank(highestCleared: Int) {
            state = constructedProfile(
                state.profile.copy(rebirthProgress = RebirthProgress(highestCleared, highestCleared)),
                state.revision.value,
            )
        }

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
        assertTrue(CoreShape.TESSERACT in unlocked())
        // Cumulative counters: one short, then exactly the target.
        record(GameplayProgressUpdate(totalKills = 1_499, ricochets = 39))
        assertFalse(CoreShape.STAR in unlocked())
        assertFalse(CoreShape.DIPOLE in unlocked())
        record(GameplayProgressUpdate(totalKills = 1, ricochets = 1))
        assertTrue(CoreShape.STAR in unlocked())
        assertTrue(CoreShape.DIPOLE in unlocked())
        // Reborn rank gates: rank = highest cleared tier + 1.
        rank(0)
        assertTrue(CoreShape.SPIRAL in unlocked())
        assertFalse(CoreShape.PENTAGON in unlocked())
        rank(1)
        assertFalse(CoreShape.PENTAGON in unlocked())
        rank(2)
        assertTrue(CoreShape.PENTAGON in unlocked())
        rank(4)
        assertTrue(CoreShape.CRESCENT in unlocked())
        assertFalse(CoreShape.HEXAGON in unlocked())
        rank(6)
        assertEquals(CoreShape.entries.toSet(), unlocked())
        assertEquals(4, state.profile.characterAchievements.architectVictories)
        assertEquals(3, state.profile.characterAchievements.victoriousCharacters.size)
        assertEquals(0L, state.profile.economy.lifetimeMatter)
        repeat(3) { assertEquals(CoreShape.entries.toSet(), unlocked()) }
    }

    @Test
    fun hangarQueryReportsProgressAndTheLocalPulseSelectsOnlyUnlockedShapes() {
        val state = readyState(profile = defaultPlayerProfile(TestProfilePolicy).copy(
            characterAchievements = kinetickk.ball.profile.api.CharacterAchievementProgress(eliteKills = 5, totalKills = 700),
            rebirthProgress = RebirthProgress(0, 0),
        ))
        val hangar = ProfileNucleus.query(state, ProfileQuery.GetHangar)
        assertEquals(state.revision, hangar.revision)
        assertEquals(CoreShape.entries.toList(), hangar.shapes.map { it.shape })
        assertEquals(CoreShape.ORB, hangar.selected)
        assertEquals(1, hangar.rebirthRank)
        val prism = hangar.shapes.first { it.shape == CoreShape.PRISM }
        assertTrue(prism.unlocked)
        assertEquals(3L, prism.progress, "progress is capped at the target")
        val star = hangar.shapes.first { it.shape == CoreShape.STAR }
        assertEquals(false to (700L to 1_500L), star.unlocked to (star.progress to star.target))
        assertTrue(hangar.shapes.first { it.shape == CoreShape.SPIRAL }.unlocked)
        assertFalse(hangar.shapes.first { it.shape == CoreShape.HEXAGON }.unlocked)

        val selected = ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.SelectCoreShape(CoreShape.SPIRAL)))
            .acceptedFrame()
        assertEquals(CoreShape.SPIRAL, selected.nextState.profile.loadout.coreShape)
        assertEquals(
            ProfileRejection.CoreShapeLocked,
            ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.SelectCoreShape(CoreShape.STAR))).rejection(),
        )
        assertEquals(
            ProfileRejection.NoChange,
            ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.SelectCoreShape(CoreShape.ORB))).rejection(),
        )
        assertEquals(CoreShape.ORB, state.profile.loadout.coreShape)
    }

    @Test
    fun malformedAchievementDeltaAndLockedCharacterVictoryPreserveProfile() {
        val state = readyState()
        listOf(
            GameplayProgressUpdate(eliteKills = -1),
            GameplayProgressUpdate(dashHits = -1),
            GameplayProgressUpdate(completedOrbits = -1),
            GameplayProgressUpdate(totalKills = -1),
            GameplayProgressUpdate(ricochets = -1),
            GameplayProgressUpdate(dataPickups = -1),
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
                totalKills = Long.MAX_VALUE - 1,
                ricochets = Long.MAX_VALUE - 1,
                dataPickups = Long.MAX_VALUE - 1,
            ),
        ))
        val update = GameplayProgressUpdate(
            eliteKills = Int.MAX_VALUE,
            dashHits = Int.MAX_VALUE,
            completedOrbits = Int.MAX_VALUE,
            totalKills = Int.MAX_VALUE,
            ricochets = Int.MAX_VALUE,
            dataPickups = Int.MAX_VALUE,
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
        assertEquals(Long.MAX_VALUE, first.nextState.profile.characterAchievements.totalKills)
        assertEquals(Long.MAX_VALUE, first.nextState.profile.characterAchievements.ricochets)
        assertEquals(Long.MAX_VALUE, first.nextState.profile.characterAchievements.dataPickups)
        assertEquals(Long.MAX_VALUE - 1, state.profile.characterAchievements.eliteKills)
    }

    @Test
    fun aLoweredTierSurvivesConstructionAndAClearIsCheckedAgainstTheRank() {
        val lowered = PlayerProfile(rebirthProgress = RebirthProgress(level = 0, highestCleared = 4))
        val constructed = constructedProfile(lowered, revision = 7L)
        assertEquals(lowered.rebirthProgress, constructed.profile.rebirthProgress)
        // A tier above the rank is not a valid save and resets at construction.
        val aboveRank = constructedProfile(PlayerProfile(rebirthProgress = RebirthProgress(level = 6, highestCleared = 4)), revision = 7L)
        assertEquals(RebirthProgress(), aboveRank.profile.rebirthProgress)

        // The run's tier may differ from the live selection: a clear up to the rank is accepted
        // whatever `level` is now, and the first tier above the rank is rejected.
        val atRank = assertIs<ProfileDecision.Accepted>(applyGameplayProgress(constructed, GameplayProgressUpdate(clearedRebirthLevel = 5)))
        assertEquals(RebirthProgress(level = 0, highestCleared = 5), atRank.frame.nextState.profile.rebirthProgress)
        assertEquals(
            ProfileRejection.InvalidGameplayProgress(ProfileGameplayProgressRejection.ClearedLevelAboveRank(6)),
            assertIs<ProfileDecision.Rejected>(applyGameplayProgress(constructed, GameplayProgressUpdate(clearedRebirthLevel = 6))).reason,
        )
        // A lower clear never lowers the record.
        val lower = assertIs<ProfileDecision.Accepted>(
            applyGameplayProgress(constructed, GameplayProgressUpdate(bankedMatter = 1L, clearedRebirthLevel = 2)),
        )
        assertEquals(4, lower.frame.nextState.profile.rebirthProgress.highestCleared)
    }

    @Test
    fun aFinalTierClearMakesTheVictoriousCharacterAscendant() {
        val nine = readyState(PlayerProfile(rebirthProgress = RebirthProgress(level = 9, highestCleared = 8)))
        val clearedNine = applyGameplayProgress(nine,
            GameplayProgressUpdate(clearedRebirthLevel = 9, architectDefeatedWith = CoreShape.ORB)).acceptedFrame().nextState
        assertEquals(9, clearedNine.profile.rebirthProgress.highestCleared)
        assertTrue(clearedNine.profile.rebirthProgress.ascendedCharacters.isEmpty())

        val ten = readyState(PlayerProfile(rebirthProgress = RebirthProgress(level = 10, highestCleared = 9)))
        val ascended = applyGameplayProgress(ten,
            GameplayProgressUpdate(clearedRebirthLevel = 10, architectDefeatedWith = CoreShape.ORB)).acceptedFrame().nextState
        assertEquals(RebirthProgress(10, 10, kinetickk.foundation.collections.immutableSetOf(CoreShape.ORB)),
            ascended.profile.rebirthProgress)
        assertTrue(ascended.profile.rebirthProgress.isAscendant)
        // A later tier-10 clear with the same character changes nothing in the set; the record never drops.
        val again = applyGameplayProgress(ascended.copy(persistence = ten.persistence),
            GameplayProgressUpdate(bankedMatter = 1L, clearedRebirthLevel = 10, architectDefeatedWith = CoreShape.ORB))
            .acceptedFrame().nextState
        assertEquals(setOf(CoreShape.ORB), again.profile.rebirthProgress.ascendedCharacters)
        // An Ascendant set the victories do not back is not a valid save.
        val forged = constructedProfile(PlayerProfile(rebirthProgress = RebirthProgress(10, 10,
            kinetickk.foundation.collections.immutableSetOf(CoreShape.ORB))))
        assertTrue(forged.profile.rebirthProgress.ascendedCharacters.isEmpty())
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

/** Completes the frame's write so the next decision starts from a settled State. */
private fun ProfileAcceptedFrame.persisted(): ProfileState {
    val persist = assertIs<ProfileOutput.PersistSnapshot>(outputs.first())
    return ProfileNucleus.decide(
        nextState,
        ProfileNucleusPulse.WriteCompleted(persist.effectRef, ProfileWriteResult.Written(persist.snapshot.revision)),
    ).acceptedFrame().nextState
}

private fun ProfileDecision.acceptedFrame(): ProfileAcceptedFrame =
    assertIs<ProfileDecision.Accepted>(this).frame

private fun ProfileDecision.rejection(): ProfileRejection =
    assertIs<ProfileDecision.Rejected>(this).reason
