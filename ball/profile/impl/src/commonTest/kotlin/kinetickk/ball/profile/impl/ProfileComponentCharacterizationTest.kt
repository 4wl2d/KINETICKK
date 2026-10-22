// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.ball.profile.api.ProfileRebirthTierSelected
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Freezes Profile behavior through its local and typed command capabilities. */
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
            assertAcceptedAndWritten(
                component,
                resource,
                ProfileTestOperation.Local(ProfilePulse.AdjustPreference(adjustment)),
            )
            assertEquals(
                resource.writes.last().profile.preferences,
                component.query(ProfileQuery.GetPreferences).preferences,
            )
        }
        assertAcceptedAndWritten(
            component,
            resource,
            ProfileTestOperation.ToggleMute,
        )

        val preferences = component.query(ProfileQuery.GetPreferences).preferences
        assertTrue(preferences.soundEnabled)
        assertTrue(preferences.musicEnabled)
        assertEquals(0.66f, preferences.masterVolume)
        assertEquals(1.15f, preferences.simulationSpeed)
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
    fun firstRowMatrixPurchasesUsePolicyPricesAndResetRefundsThemAll() {
        val initialMatter = 1_000_000L
        val initial = testDefaultProfile().copy(
            economy = PlayerEconomy(initialMatter, initialMatter),
        )
        val resource = loadedResource(initial)
        val component = testProfileComponent(resource)
        var expectedMatter = initialMatter
        val firstRow = TestProfilePolicy.matrixNodes.filter { it.row == 0 }

        firstRow.forEach { node ->
            expectedMatter -= node.cost(owned = 0)
            assertAcceptedAndWritten(
                component,
                resource,
                ProfileTestOperation.Local(ProfilePulse.PurchaseMatrixNode(node.id)),
            )
            val matrix = component.query(ProfileQuery.GetMatrixProgress).snapshot
            assertEquals(1, matrix.progress.rank(node.id))
            assertEquals(expectedMatter, matrix.economy.matter)
        }

        assertEquals(firstRow.size, queriedProfile(component).matrix.ownedNodeCount)
        assertEquals(firstRow.size, resource.writes.size)

        assertAcceptedAndWritten(component, resource, ProfileTestOperation.Local(ProfilePulse.ResetMatrix))
        val reset = component.query(ProfileQuery.GetMatrixProgress).snapshot
        assertEquals(MatrixProgress(), reset.progress)
        assertEquals(initialMatter, reset.economy.matter)
        assertEquals(firstRow.size + 1, resource.writes.size)
    }

    @Test
    fun coreShapeSelectionUsesAchievementsWithoutSpendingMatter() {
        val initial = testDefaultProfile().copy(
            economy = PlayerEconomy(matter = 70L, lifetimeMatter = 90L),
            characterAchievements = kinetickk.ball.profile.api.CharacterAchievementProgress(eliteKills = 3, dashHits = 20),
        )
        val resource = loadedResource(initial)
        val component = testProfileComponent(resource)

        assertAcceptedAndWritten(
            component,
            resource,
            ProfileTestOperation.SelectCoreShape(CoreShape.PRISM),
        )
        assertAcceptedAndWritten(
            component,
            resource,
            ProfileTestOperation.SelectCoreShape(CoreShape.SHARD),
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

        listOf(weapon, WeaponId.FLUX_WAKE, weapon).forEach { target ->
            assertAcceptedAndWritten(
                component,
                resource,
                ProfileTestOperation.Local(ProfilePulse.PurchaseOrEquipWeapon(target)),
            )
        }

        val view = component.query(ProfileQuery.GetLoadout).snapshot
        assertEquals(expectedMatter, view.economy.matter)
        assertEquals(weapon, view.loadout.selectedWeapon)
        assertEquals(setOf(WeaponId.FLUX_WAKE, weapon), view.loadout.unlockedWeapons)
        assertEquals(3, resource.writes.size)
    }

    @Test
    fun upgradePurchasesChargeDoublingPricesKeepLifetimeMatterAndWriteOncePerLevel() {
        val initial = testDefaultProfile().copy(economy = PlayerEconomy(1_000L, 4_000L))
        val resource = loadedResource(initial)
        val component = testProfileComponent(resource)

        listOf(
            ProfilePulse.PurchaseCharacterUpgrade(CoreShape.ORB),
            ProfilePulse.PurchaseCharacterUpgrade(CoreShape.ORB),
            ProfilePulse.PurchaseWeaponUpgrade(WeaponId.FLUX_WAKE),
        ).forEach { pulse ->
            assertAcceptedAndWritten(component, resource, ProfileTestOperation.Local(pulse))
        }

        val view = component.query(ProfileQuery.GetLoadout).snapshot
        // Refit 50 + 100 at Orb's base 50, Honing 35 at Flux Wake's base 35.
        assertEquals(PlayerEconomy(1_000L - 50L - 100L - 35L, 4_000L), view.economy)
        assertEquals(2, view.upgrades.characterLevel(CoreShape.ORB))
        assertEquals(1, view.upgrades.weaponLevel(WeaponId.FLUX_WAKE))
        assertEquals(initial.loadout, view.loadout)
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
            ProfileTestOperation.ApplyGameplayProgress(
                GameplayProgressUpdate(
                    bankedMatter = 7L,
                    discoveredItemIds = setOf(1, 79),
                    clearedRebirthLevel = 2,
                ),
            ),
        )
        val afterRun = queriedProfile(component)
        assertEquals(PlayerEconomy(12L, 27L), afterRun.economy)
        assertEquals(setOf(0, 1, 40, 79), afterRun.collection.discoveredItemIds)
        assertEquals(RebirthProgress(level = 2, highestCleared = 2), afterRun.rebirthProgress)

        assertAcceptedAndWritten(
            component,
            resource,
            ProfileTestOperation.SelectRebirthTier(3),
        )
        val advanced = queriedProfile(component)
        assertEquals(afterRun.preferences, advanced.preferences)
        assertEquals(afterRun.economy, advanced.economy)
        assertEquals(afterRun.loadout, advanced.loadout)
        assertEquals(afterRun.matrix, advanced.matrix)
        assertEquals(afterRun.collection, advanced.collection)
        assertEquals(RebirthProgress(level = 3, highestCleared = 2), advanced.rebirthProgress)
        assertEquals(2, resource.writes.size)
    }

    @Test
    fun everyRejectionLeavesStateRevisionAndEffectsUntouched() {
        val maxedRanks = List(MatrixNodeId.entries.size) { index ->
            if (index == MatrixNodeId.REINFORCED_HULL.ordinal) {
                TestProfilePolicy.matrixNode(MatrixNodeId.REINFORCED_HULL).maxRanks
            } else {
                0
            }
        }
        val cases = listOf(
            RejectionCase(
                profile = testDefaultProfile(),
                operation = ProfileTestOperation.SelectCoreShape(CoreShape.ORB),
                reason = ProfileRejection.NoChange,
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                operation = ProfileTestOperation.Local(
                    ProfilePulse.PurchaseMatrixNode(MatrixNodeId.REINFORCED_HULL),
                ),
                reason = ProfileRejection.InsufficientMatter,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(
                    economy = PlayerEconomy(10_000L, 10_000L),
                    matrix = MatrixProgress(maxedRanks),
                ),
                operation = ProfileTestOperation.Local(
                    ProfilePulse.PurchaseMatrixNode(MatrixNodeId.REINFORCED_HULL),
                ),
                reason = ProfileRejection.MatrixNodeMaxRank,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(economy = PlayerEconomy(10_000L, 10_000L)),
                operation = ProfileTestOperation.Local(
                    ProfilePulse.PurchaseMatrixNode(MatrixNodeId.NANOFORGE),
                ),
                reason = ProfileRejection.MatrixPrerequisiteMissing,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(
                    economy = PlayerEconomy(10_000L, 10_000L),
                    matrix = MatrixProgress(maxedRanks),
                ),
                operation = ProfileTestOperation.Local(
                    ProfilePulse.PurchaseMatrixNode(MatrixNodeId.SECOND_WIND),
                ),
                reason = ProfileRejection.MatrixRebirthLocked,
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                operation = ProfileTestOperation.Local(ProfilePulse.ResetMatrix),
                reason = ProfileRejection.NoChange,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(economy = PlayerEconomy(0L, 24L)),
                operation = ProfileTestOperation.SelectCoreShape(CoreShape.PRISM),
                reason = ProfileRejection.CoreShapeLocked,
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                operation = ProfileTestOperation.Local(
                    ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
                ),
                reason = ProfileRejection.InsufficientMatter,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(
                    rebirthProgress = RebirthProgress(level = 10, highestCleared = 10),
                ),
                operation = ProfileTestOperation.SelectRebirthTier(11),
                reason = ProfileRejection.RebirthTierLocked,
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                operation = ProfileTestOperation.SelectRebirthTier(1),
                reason = ProfileRejection.RebirthTierLocked,
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                operation = ProfileTestOperation.ApplyGameplayProgress(
                    GameplayProgressUpdate(bankedMatter = -1L),
                ),
                reason = ProfileRejection.InvalidGameplayProgress(
                    ProfileGameplayProgressRejection.NegativeBankedMatter,
                ),
            ),
            RejectionCase(
                profile = testDefaultProfile(),
                operation = ProfileTestOperation.ApplyGameplayProgress(GameplayProgressUpdate()),
                reason = ProfileRejection.NoChange,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(economy = PlayerEconomy(10_000L, 10_000L)),
                operation = ProfileTestOperation.Local(ProfilePulse.PurchaseCharacterUpgrade(CoreShape.PRISM)),
                reason = ProfileRejection.CoreShapeLocked,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(economy = PlayerEconomy(10_000L, 10_000L)),
                operation = ProfileTestOperation.Local(ProfilePulse.PurchaseWeaponUpgrade(WeaponId.MORNINGSTAR)),
                reason = ProfileRejection.WeaponNotOwned,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(
                    economy = PlayerEconomy(10_000L, 10_000L),
                    upgrades = PermanentUpgrades(
                        characterLevels = CoreShape.entries.map { if (it == CoreShape.ORB) 10 else 0 },
                        weaponLevels = List(WeaponId.entries.size) { 0 },
                    ),
                ),
                operation = ProfileTestOperation.Local(ProfilePulse.PurchaseCharacterUpgrade(CoreShape.ORB)),
                reason = ProfileRejection.UpgradeMaxLevel,
            ),
            RejectionCase(
                profile = testDefaultProfile().copy(economy = PlayerEconomy(34L, 34L)),
                operation = ProfileTestOperation.Local(ProfilePulse.PurchaseWeaponUpgrade(WeaponId.FLUX_WAKE)),
                reason = ProfileRejection.InsufficientMatter,
            ),
        )

        cases.forEach { case ->
            val resource = loadedResource(case.profile)
            val component = testProfileComponent(resource)
            val before = component.stateSnapshot()
            val beforeProfile = queriedProfile(component)

            assertEquals(case.reason, component.reject(case.operation))
            assertEquals(before, component.stateSnapshot())
            assertEquals(beforeProfile, queriedProfile(component))
            assertTrue(resource.writes.isEmpty())
        }
    }

    @Test
    fun capturedCustomPolicyControlsItemsCostsUnlocksAndRebirthBounds() {
        val customPolicy = ProfilePolicySnapshot(
            version = ContentVersion("custom-policy"),
            itemCount = 2,
            coreShapes = TestProfilePolicy.coreShapes.map { definition ->
                definition.copy(unlockTarget = if (definition.id == CoreShape.ORB) 0 else 500)
            }.toImmutableList(),
            weapons = TestProfilePolicy.weapons.map { definition ->
                if (definition.id == WeaponId.MORNINGSTAR) {
                    testWeapon(definition.id, cost = 7)
                } else {
                    definition
                }
            }.toImmutableList(),
            matrixNodes = TestProfilePolicy.matrixNodes,
            rebirth = testRebirthPolicy(minimumLevel = 2, maximumLevel = 3),
        )
        val resource = RecordingProfileResource()
        val component = testProfileComponent(resource, customPolicy)

        assertProfileQueries(testDefaultProfile(customPolicy), component, ProfileRevision(1L))
        assertAcceptedAndWritten(
            component,
            resource,
            ProfileTestOperation.ApplyGameplayProgress(
                GameplayProgressUpdate(bankedMatter = 7L, discoveredItemIds = setOf(0, 1)),
            ),
        )
        assertAcceptedAndWritten(
            component,
            resource,
            ProfileTestOperation.Local(
                ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
            ),
        )
        assertEquals(0L, component.query(ProfileQuery.GetLoadout).snapshot.economy.matter)

        assertRejectedAtomically(
            component,
            resource,
            ProfileTestOperation.SelectCoreShape(CoreShape.PRISM),
            ProfileRejection.CoreShapeLocked,
        )
        assertRejectedAtomically(
            component,
            resource,
            ProfileTestOperation.ApplyGameplayProgress(
                GameplayProgressUpdate(discoveredItemIds = setOf(2)),
            ),
            ProfileRejection.InvalidGameplayProgress(ProfileGameplayProgressRejection.UnknownItem(2)),
        )
        assertAcceptedAndWritten(
            component,
            resource,
            ProfileTestOperation.ApplyGameplayProgress(
                GameplayProgressUpdate(clearedRebirthLevel = 2),
            ),
        )
        assertAcceptedAndWritten(
            component,
            resource,
            ProfileTestOperation.SelectRebirthTier(3),
        )
        assertEquals(3, component.query(ProfileQuery.GetRebirthProgress).snapshot.progress.level)
        assertRejectedAtomically(
            component,
            resource,
            ProfileTestOperation.SelectRebirthTier(4),
            ProfileRejection.RebirthTierLocked,
        )
        // The custom policy's minimum is the floor of the selectable range.
        assertRejectedAtomically(
            component,
            resource,
            ProfileTestOperation.SelectRebirthTier(1),
            ProfileRejection.RebirthTierLocked,
        )
        assertEquals(4, resource.writes.size)
    }
}

private sealed interface ProfileTestOperation {
    data object ToggleMute : ProfileTestOperation
    data class SelectRebirthTier(val tier: Int) : ProfileTestOperation
    data class SelectCoreShape(val shape: CoreShape) : ProfileTestOperation
    data class Local(val pulse: ProfilePulse.Business) : ProfileTestOperation
    data class ApplyGameplayProgress(val update: GameplayProgressUpdate) : ProfileTestOperation
}

private data class RejectionCase(
    val profile: PlayerProfile,
    val operation: ProfileTestOperation,
    val reason: ProfileRejection,
)

private fun loadedResource(
    profile: PlayerProfile,
    revision: Long = 10L,
): RecordingProfileResource = RecordingProfileResource(
    ProfileSnapshotReadResult.Observed(
        snapshot = profileSnapshot(profile = profile, revision = revision),
    ),
)

private fun assertAcceptedAndWritten(
    component: DefaultProfileComponent,
    resource: RecordingProfileResource,
    operation: ProfileTestOperation,
) {
    val beforeRevision = component.query(ProfileQuery.GetPreferences).revision
    val beforeWrites = resource.writes.size
    val acceptedRevision = when (operation) {
        ProfileTestOperation.ToggleMute -> ProfileCommandTestCaller<ProfileSettingsChanged>()
            .also { it.call(component::toggleMute) }.changed.single().revision
        is ProfileTestOperation.SelectRebirthTier -> ProfileCommandTestCaller<ProfileRebirthTierSelected>()
            .also { it.call { reply -> component.selectRebirthTier(operation.tier, reply) } }.changed.single().revision
        is ProfileTestOperation.SelectCoreShape -> ProfileCommandTestCaller<ProfileCoreShapeSelected>()
            .also { it.call { reply -> component.selectCoreShape(operation.shape, reply) } }.changed.single().revision
        is ProfileTestOperation.Local ->
            assertIs<ProfileAcceptance.Accepted>(component.accept(operation.pulse)).revision
        is ProfileTestOperation.ApplyGameplayProgress -> ProfileCommandTestCaller<ProfileProgressApplied>()
            .also { it.call { reply -> component.applyGameplayProgress(operation.update, reply) } }.changed.single().revision
    }

    assertEquals(ProfileRevision(beforeRevision.value + 1L), acceptedRevision)
    assertEquals(beforeWrites + 1, resource.writes.size)
    assertEquals(acceptedRevision, resource.writes.last().revision)
    assertEquals(
        ProfileRevision(beforeRevision.value + 2L),
        component.query(ProfileQuery.GetPreferences).revision,
    )
    assertProfileQueries(resource.writes.last().profile, component)
}

private fun assertRejectedAtomically(
    component: DefaultProfileComponent,
    resource: RecordingProfileResource,
    operation: ProfileTestOperation,
    reason: ProfileRejection,
) {
    val before = component.stateSnapshot()
    val beforeWrites = resource.writes.size
    assertEquals(reason, component.reject(operation))
    assertEquals(before, component.stateSnapshot())
    assertEquals(beforeWrites, resource.writes.size)
}

private fun DefaultProfileComponent.reject(operation: ProfileTestOperation): ProfileRejection =
    when (operation) {
        ProfileTestOperation.ToggleMute -> assertIs<kinetickk.ball.profile.api.ProfileRefusal.DecisionRejected>(
            ProfileCommandTestCaller<ProfileSettingsChanged>().also { it.call(this::toggleMute) }.refused.single(),
        ).reason
        is ProfileTestOperation.SelectRebirthTier -> assertIs<kinetickk.ball.profile.api.ProfileRefusal.DecisionRejected>(
            ProfileCommandTestCaller<ProfileRebirthTierSelected>()
                .also { it.call { reply -> selectRebirthTier(operation.tier, reply) } }.refused.single(),
        ).reason
        is ProfileTestOperation.SelectCoreShape -> assertIs<kinetickk.ball.profile.api.ProfileRefusal.DecisionRejected>(
            ProfileCommandTestCaller<ProfileCoreShapeSelected>()
                .also { it.call { reply -> selectCoreShape(operation.shape, reply) } }.refused.single(),
        ).reason
        is ProfileTestOperation.Local ->
            assertIs<ProfileAcceptance.Rejected>(accept(operation.pulse)).reason
        is ProfileTestOperation.ApplyGameplayProgress -> assertIs<kinetickk.ball.profile.api.ProfileRefusal.DecisionRejected>(
            ProfileCommandTestCaller<ProfileProgressApplied>()
                .also { it.call { reply -> applyGameplayProgress(operation.update, reply) } }.refused.single(),
        ).reason
    }
