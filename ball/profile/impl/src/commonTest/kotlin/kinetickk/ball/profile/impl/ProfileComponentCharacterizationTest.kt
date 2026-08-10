// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Freezes the public Profile mutation behavior at the v4 acceptor boundary.
 *
 * Arbitrary Profile replacement and bootstrap normalization were intentionally removed: v4 input
 * is either policy-compatible or reset-gated. Every supported mutation below is observed through
 * typed queries and a complete Profile snapshot write.
 */
class ProfileComponentCharacterizationTest {
    @Test
    fun everyPreferenceAdjustmentAndMutePublishesThroughQueriesAndWritesOnce() {
        val resource = RecordingProfileResource()
        val component = testProfileComponent(resource)
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
            assertAcceptedAndWritten(component, resource, ProfilePulse.AdjustPreference(adjustment))
            assertEquals(
                resource.writes.last().profile.preferences,
                component.query(ProfileQuery.GetPreferences).preferences,
            )
        }
        assertAcceptedAndWritten(component, resource, ProfilePulse.ToggleMute)

        val preferences = component.query(ProfileQuery.GetPreferences).preferences
        assertTrue(preferences.soundEnabled)
        assertTrue(preferences.musicEnabled)
        assertEquals(0.66f, preferences.masterVolume)
        assertEquals(1.35f, preferences.simulationSpeed)
        assertEquals(1.26f, preferences.textScale)
        assertFalse(preferences.screenShake)
        assertEquals(ParticleDensity.HIGH, preferences.particleDensity)
        assertFalse(preferences.damageNumbers)
        assertEquals(DamageNumberSize.LARGE, preferences.damageNumberSize)
        assertEquals(DamageNumberFormat.FULL, preferences.damageNumberFormat)
        assertEquals(100, preferences.damageNumberTierThreshold)
        assertEquals(adjustments.size + 1, resource.writes.size)
    }

    @Test
    fun allMetaUpgradePurchasesUsePolicyOrderAndCurrentRankPrices() {
        val initialMatter = 1_000_000L
        val initial = testDefaultProfile().copy(
            economy = PlayerEconomy(initialMatter, initialMatter),
        )
        val resource = loadedResource(initial)
        val component = testProfileComponent(resource)
        var expectedMatter = initialMatter

        MetaUpgradeId.entries.forEach { id ->
            expectedMatter -= TestProfilePolicy.metaUpgrade(id).cost(level = 0)
            assertAcceptedAndWritten(component, resource, ProfilePulse.PurchaseMetaUpgrade(id))
            val lab = component.query(ProfileQuery.GetLabProgress).snapshot
            assertEquals(1, lab.progress.rank(id))
            assertEquals(expectedMatter, lab.economy.matter)
        }

        assertEquals(List(MetaUpgradeId.entries.size) { 1 }, queriedProfile(component).labProgress.ranks)
        assertEquals(MetaUpgradeId.entries.size, resource.writes.size)
    }

    @Test
    fun coreShapeSelectionUsesLifetimeUnlocksWithoutSpendingMatter() {
        val initial = testDefaultProfile().copy(
            economy = PlayerEconomy(matter = 70L, lifetimeMatter = 90L),
        )
        val resource = loadedResource(initial)
        val component = testProfileComponent(resource)

        assertAcceptedAndWritten(
            component,
            resource,
            ProfilePulse.SelectCoreShape(CoreShape.PRISM),
        )
        assertEquals(
            PlayerLoadout(coreShape = CoreShape.PRISM, unlockedWeapons = setOf(WeaponId.FLUX_WAKE)),
            component.query(ProfileQuery.GetLoadout).snapshot.loadout,
        )
        assertAcceptedAndWritten(
            component,
            resource,
            ProfilePulse.SelectCoreShape(CoreShape.SHARD),
        )

        val loadout = component.query(ProfileQuery.GetLoadout).snapshot
        assertEquals(CoreShape.SHARD, loadout.loadout.coreShape)
        assertEquals(initial.economy, loadout.economy)
        assertEquals(2, resource.writes.size)
    }

    @Test
    fun weaponPurchaseEquipAndReequipChargeOnlyTheFirstUnlock() {
        val initialMatter = 100_000L
        val initial = testDefaultProfile().copy(
            economy = PlayerEconomy(initialMatter, initialMatter),
        )
        val resource = loadedResource(initial)
        val component = testProfileComponent(resource)
        val weapon = WeaponId.MORNINGSTAR
        val expectedMatter = initialMatter - TestProfilePolicy.weapon(weapon).permanentUnlockCost

        assertAcceptedAndWritten(
            component,
            resource,
            ProfilePulse.PurchaseOrEquipWeapon(weapon),
        )
        var view = component.query(ProfileQuery.GetLoadout).snapshot
        assertEquals(expectedMatter, view.economy.matter)
        assertEquals(weapon, view.loadout.selectedWeapon)
        assertEquals(setOf(WeaponId.FLUX_WAKE, weapon), view.loadout.unlockedWeapons)

        assertAcceptedAndWritten(
            component,
            resource,
            ProfilePulse.PurchaseOrEquipWeapon(WeaponId.FLUX_WAKE),
        )
        assertAcceptedAndWritten(
            component,
            resource,
            ProfilePulse.PurchaseOrEquipWeapon(weapon),
        )

        view = component.query(ProfileQuery.GetLoadout).snapshot
        assertEquals(expectedMatter, view.economy.matter)
        assertEquals(weapon, view.loadout.selectedWeapon)
        assertEquals(3, resource.writes.size)
    }

    @Test
    fun gameplayBatchAndRebirthPreservePermanentSlicesAcrossTiers() {
        val representative = representativeProfile()
        val initial = representative.copy(
            economy = PlayerEconomy(matter = 5L, lifetimeMatter = 20L),
            loadout = representative.loadout.copy(coreShape = CoreShape.ORB),
            rebirthProgress = RebirthProgress(level = 2, highestCleared = 1),
        )
        val resource = loadedResource(initial)
        val component = testProfileComponent(resource)

        assertAcceptedAndWritten(
            component,
            resource,
            ProfilePulse.ApplyGameplayProgress(
                GameplayProgressUpdate(
                    bankedMatter = 7L,
                    discoveredItemIds = setOf(1, 399),
                    clearedRebirthLevel = 2,
                ),
            ),
        )

        val afterRun = queriedProfile(component)
        assertEquals(PlayerEconomy(12L, 27L), afterRun.economy)
        assertEquals(setOf(0, 1, 200, 399), afterRun.collection.discoveredItemIds)
        assertEquals(RebirthProgress(level = 2, highestCleared = 2), afterRun.rebirthProgress)
        assertTrue(component.query(ProfileQuery.GetRebirthProgress).canAdvance)

        assertAcceptedAndWritten(component, resource, ProfilePulse.AdvanceRebirth)

        val advanced = queriedProfile(component)
        assertEquals(afterRun.preferences, advanced.preferences)
        assertEquals(afterRun.economy, advanced.economy)
        assertEquals(afterRun.loadout, advanced.loadout)
        assertEquals(afterRun.labProgress, advanced.labProgress)
        assertEquals(afterRun.collection, advanced.collection)
        assertEquals(RebirthProgress(level = 3, highestCleared = 2), advanced.rebirthProgress)
        assertFalse(component.query(ProfileQuery.GetRebirthProgress).canAdvance)
        assertEquals(2, resource.writes.size)
    }

    @Test
    fun everyRejectionLeavesStateRevisionAndEffectsUntouched() {
        val maxedRanks = List(MetaUpgradeId.entries.size) { index ->
            if (index == MetaUpgradeId.CORE_INTEGRITY.ordinal) {
                TestProfilePolicy.metaUpgrade(MetaUpgradeId.CORE_INTEGRITY).maxRanks
            } else {
                0
            }
        }
        val cases = listOf(
            RejectionCase(
                profile = testDefaultProfile(),
                pulse = ProfilePulse.SelectCoreShape(CoreShape.ORB),
                reason = ProfileRejection.NoChange,
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                pulse = ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
                reason = ProfileRejection.InsufficientMatter,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(
                    economy = PlayerEconomy(10_000L, 10_000L),
                    labProgress = LabProgress(maxedRanks),
                ),
                pulse = ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
                reason = ProfileRejection.MetaUpgradeMaxRank,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(economy = PlayerEconomy(0L, 24L)),
                pulse = ProfilePulse.SelectCoreShape(CoreShape.PRISM),
                reason = ProfileRejection.CoreShapeLocked,
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                pulse = ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
                reason = ProfileRejection.InsufficientMatter,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(
                    rebirthProgress = RebirthProgress(level = 10, highestCleared = 10),
                ),
                pulse = ProfilePulse.AdvanceRebirth,
                reason = ProfileRejection.RebirthMaximumReached,
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                pulse = ProfilePulse.AdvanceRebirth,
                reason = ProfileRejection.RebirthLevelNotCleared,
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                pulse = ProfilePulse.ApplyGameplayProgress(
                    GameplayProgressUpdate(bankedMatter = -1L),
                ),
                reason = ProfileRejection.InvalidGameplayProgress(
                    ProfileGameplayProgressRejection.NegativeBankedMatter,
                ),
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                pulse = ProfilePulse.ApplyGameplayProgress(
                    GameplayProgressUpdate(discoveredItemIds = setOf(400)),
                ),
                reason = ProfileRejection.InvalidGameplayProgress(
                    ProfileGameplayProgressRejection.UnknownItem(400),
                ),
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                pulse = ProfilePulse.ApplyGameplayProgress(
                    GameplayProgressUpdate(clearedRebirthLevel = 1),
                ),
                reason = ProfileRejection.InvalidGameplayProgress(
                    ProfileGameplayProgressRejection.ClearedLevelAboveCurrent(1),
                ),
            ),
        )

        cases.forEach { case ->
            val resource = loadedResource(case.profile)
            val component = testProfileComponent(resource)
            val before = component.stateSnapshot()
            val beforeProfile = queriedProfile(component)
            val beforeWrites = resource.writes.size

            val rejection = assertIs<ProfileAcceptance.Rejected>(component.accept(case.pulse))

            assertEquals(case.reason, rejection.reason)
            assertEquals(before.revision, rejection.observedRevision)
            assertEquals(before, component.stateSnapshot())
            assertEquals(beforeProfile, queriedProfile(component))
            assertEquals(beforeWrites, resource.writes.size)
            assertEquals(0, resource.purgeCount)
        }
    }

    @Test
    fun capturedCustomPolicyControlsDefaultsItemsCostsUnlocksAndRebirthBounds() {
        val customPolicy = ProfilePolicySnapshot(
            version = ContentVersion("custom-policy"),
            itemCount = 2,
            coreShapes = TestProfilePolicy.coreShapes.map { definition ->
                CoreShapeDefinition(
                    definition.id,
                    when (definition.id) {
                        CoreShape.ORB -> 0L
                        CoreShape.PRISM -> 500L
                        CoreShape.SHARD -> 900L
                    },
                )
            }.toImmutableList(),
            weapons = TestProfilePolicy.weapons.map { definition ->
                if (definition.id == WeaponId.MORNINGSTAR) {
                    WeaponDefinition(
                        id = definition.id,
                        name = definition.name,
                        description = definition.description,
                        tags = definition.tags,
                        permanentUnlockCost = 7,
                    )
                } else {
                    definition
                }
            }.toImmutableList(),
            metaUpgrades = TestProfilePolicy.metaUpgrades,
            rebirth = testRebirthPolicy(minimumLevel = 2, maximumLevel = 3),
        )
        val resource = RecordingProfileResource()
        val component = testProfileComponent(resource, customPolicy)

        assertProfileQueries(testDefaultProfile(customPolicy), component, ProfileRevision(1L))
        assertEquals(2, component.query(ProfileQuery.GetRebirthProgress).snapshot.progress.level)

        assertAcceptedAndWritten(
            component,
            resource,
            ProfilePulse.ApplyGameplayProgress(
                GameplayProgressUpdate(bankedMatter = 7L, discoveredItemIds = setOf(0, 1)),
            ),
        )
        assertAcceptedAndWritten(
            component,
            resource,
            ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
        )
        assertEquals(0L, component.query(ProfileQuery.GetLoadout).snapshot.economy.matter)

        assertRejectedAtomically(
            component,
            resource,
            ProfilePulse.SelectCoreShape(CoreShape.PRISM),
            ProfileRejection.CoreShapeLocked,
        )
        assertRejectedAtomically(
            component,
            resource,
            ProfilePulse.ApplyGameplayProgress(
                GameplayProgressUpdate(discoveredItemIds = setOf(2)),
            ),
            ProfileRejection.InvalidGameplayProgress(ProfileGameplayProgressRejection.UnknownItem(2)),
        )
        assertAcceptedAndWritten(
            component,
            resource,
            ProfilePulse.ApplyGameplayProgress(GameplayProgressUpdate(clearedRebirthLevel = 2)),
        )
        assertAcceptedAndWritten(component, resource, ProfilePulse.AdvanceRebirth)
        assertEquals(3, component.query(ProfileQuery.GetRebirthProgress).snapshot.progress.level)
        assertRejectedAtomically(
            component,
            resource,
            ProfilePulse.AdvanceRebirth,
            ProfileRejection.RebirthMaximumReached,
        )
        assertEquals(4, resource.writes.size)
    }
}

private fun loadedResource(
    profile: PlayerProfile,
    revision: Long = 10L,
): RecordingProfileResource = RecordingProfileResource(
    ProfileBootstrapResourceResult.Observed(
        snapshot = v4Snapshot(profile = profile, revision = revision),
        legacyKeys = ProfileLegacyKeys.NONE,
    ),
)

private fun assertAcceptedAndWritten(
    component: DefaultProfileComponent,
    resource: RecordingProfileResource,
    pulse: ProfilePulse.Business,
) {
    val beforeRevision = component.query(ProfileQuery.GetPreferences).revision
    val beforeWrites = resource.writes.size

    val acceptance = assertIs<ProfileAcceptance.Accepted>(component.accept(pulse))

    assertEquals(ProfileRevision(beforeRevision.value + 1L), acceptance.revision)
    assertEquals(beforeWrites + 1, resource.writes.size)
    assertEquals(acceptance.revision, resource.writes.last().revision)
    assertEquals(
        ProfileRevision(beforeRevision.value + 2L),
        component.query(ProfileQuery.GetPreferences).revision,
    )
    assertProfileQueries(resource.writes.last().profile, component)
}

private fun assertRejectedAtomically(
    component: DefaultProfileComponent,
    resource: RecordingProfileResource,
    pulse: ProfilePulse.Business,
    reason: ProfileRejection,
) {
    val before = component.stateSnapshot()
    val beforeWrites = resource.writes.size
    val rejection = assertIs<ProfileAcceptance.Rejected>(component.accept(pulse))
    assertEquals(reason, rejection.reason)
    assertEquals(before, component.stateSnapshot())
    assertEquals(beforeWrites, resource.writes.size)
}

private data class RejectionCase(
    val profile: PlayerProfile,
    val pulse: ProfilePulse.Business,
    val reason: ProfileRejection,
)
