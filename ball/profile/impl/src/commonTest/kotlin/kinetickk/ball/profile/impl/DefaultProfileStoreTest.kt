// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileLoadResult
import kinetickk.ball.profile.api.ProfileMutationRejection
import kinetickk.ball.profile.api.ProfileMutationResult
import kinetickk.ball.profile.api.ProfilePersistResult
import kinetickk.ball.profile.api.ProfileProviderId
import kinetickk.ball.profile.api.ProfileResource
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultProfileStoreTest {
    @Test
    fun bootstrapQuarantineNormalizesEverySliceBeforeExposure() {
        val store = testProfileStore(
            RecordingResource(
                loaded = PlayerProfile(
                    preferences = PlayerPreferences(masterVolume = -2f, simulationSpeed = 99f),
                    economy = PlayerEconomy(-10L, -20L),
                    loadout = PlayerLoadout(unlockedWeapons = emptySet()),
                    labProgress = LabProgress(listOf(Int.MAX_VALUE)),
                    collection = PlayerCollection(setOf(-1, 400)),
                    rebirthProgress = RebirthProgress(Int.MAX_VALUE, Int.MAX_VALUE),
                ),
            ),
        )

        assertIs<ProfileLoadResult.Loaded>(store.bootstrapResult)
        val profile = store.profileSnapshot()
        assertEquals(PlayerEconomy(), profile.economy)
        assertEquals(0f, profile.preferences.masterVolume)
        assertEquals(2f, profile.preferences.simulationSpeed)
        assertEquals(setOf(WeaponId.FLUX_WAKE), profile.loadout.unlockedWeapons)
        assertEquals(TestProfilePolicy.metaUpgrade(MetaUpgradeId.CORE_INTEGRITY).maxRanks, profile.labProgress.ranks.first())
        assertTrue(profile.collection.discoveredItemIds.isEmpty())
        assertEquals(RebirthProgress(10, 10), profile.rebirthProgress)
        assertEquals(profile.economy, store.profileSnapshot().economy)
    }

    @Test
    fun labAndLoadoutPurchasesUpdateFullProfileInSingleWrites() {
        val resource = RecordingResource(
            loaded = PlayerProfile(economy = PlayerEconomy(10_000L, 10_000L)),
        )
        val store = testProfileStore(resource)

        assertIs<ProfileMutationResult.Applied>(
            store.purchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
        )
        assertIs<ProfileMutationResult.Applied>(
            store.purchaseOrEquipWeapon(WeaponId.SINGULARITY_SPEAR),
        )
        assertIs<ProfileMutationResult.Applied>(store.selectCoreShape(CoreShape.SHARD))

        val expectedMatter = 10_000L - TestProfilePolicy.metaUpgrade(MetaUpgradeId.CORE_INTEGRITY).cost(0) -
            TestProfilePolicy.weapon(WeaponId.SINGULARITY_SPEAR).permanentUnlockCost
        assertEquals(expectedMatter, store.profileSnapshot().economy.matter)
        assertEquals(1, resource.persisted[0].labProgress.rank(MetaUpgradeId.CORE_INTEGRITY))
        assertEquals(WeaponId.SINGULARITY_SPEAR, resource.persisted[1].loadout.selectedWeapon)
        assertEquals(CoreShape.SHARD, store.profileSnapshot().loadout.coreShape)
        assertEquals(3, resource.persisted.size)
        assertEquals(store.profileSnapshot(), resource.persisted.last())
    }

    @Test
    fun gameplayBatchAtomicallyBanksDiscoversAndClearsCurrentRebirth() {
        val resource = RecordingResource(
            loaded = PlayerProfile(
                economy = PlayerEconomy(5L, 20L),
                rebirthProgress = RebirthProgress(2, 1),
            ),
        )
        val store = testProfileStore(resource)

        assertIs<ProfileMutationResult.Applied>(
            store.applyGameplayProgress(
                GameplayProgressUpdate(
                    bankedMatter = 7L,
                    discoveredItemIds = setOf(0, 399),
                    clearedRebirthLevel = 2,
                ),
            ),
        )

        val profile = store.profileSnapshot()
        assertEquals(PlayerEconomy(12L, 27L), profile.economy)
        assertEquals(setOf(0, 399), profile.collection.discoveredItemIds)
        assertEquals(RebirthProgress(2, 2), profile.rebirthProgress)
        assertEquals(listOf(profile), resource.persisted)
    }

    @Test
    fun allEightMetaUpgradesPersistInCatalogOrder() {
        val initialMatter = 1_000_000L
        val resource = RecordingResource(
            loaded = PlayerProfile(economy = PlayerEconomy(initialMatter, initialMatter)),
        )
        val store = testProfileStore(resource)

        MetaUpgradeId.entries.forEach { id ->
            assertIs<ProfileMutationResult.Applied>(store.purchaseMetaUpgrade(id))
        }

        assertEquals(List(MetaUpgradeId.entries.size) { 1 }, store.profileSnapshot().labProgress.ranks)
        assertEquals(
            initialMatter - TestProfilePolicy.metaUpgrades.sumOf { definition -> definition.cost(0).toLong() },
            store.profileSnapshot().economy.matter,
        )
        assertEquals(MetaUpgradeId.entries.size, resource.persisted.size)
    }

    @Test
    fun unlockedWeaponCanBeReequippedWithoutAnotherCharge() {
        val initialMatter = 100_000L
        val resource = RecordingResource(
            loaded = PlayerProfile(economy = PlayerEconomy(initialMatter, initialMatter)),
        )
        val store = testProfileStore(resource)
        val weapon = WeaponId.MORNINGSTAR

        assertIs<ProfileMutationResult.Applied>(store.purchaseOrEquipWeapon(weapon))
        val matterAfterUnlock = store.profileSnapshot().economy.matter
        assertEquals(
            initialMatter - TestProfilePolicy.weapon(weapon).permanentUnlockCost,
            matterAfterUnlock,
        )
        val alreadySelected = assertIs<ProfileMutationResult.Rejected>(store.purchaseOrEquipWeapon(weapon))
        assertEquals(ProfileMutationRejection.NO_CHANGE, alreadySelected.reason)
        assertIs<ProfileMutationResult.Applied>(store.purchaseOrEquipWeapon(WeaponId.FLUX_WAKE))
        assertIs<ProfileMutationResult.Applied>(store.purchaseOrEquipWeapon(weapon))

        assertEquals(matterAfterUnlock, store.profileSnapshot().economy.matter)
        assertEquals(weapon, store.profileSnapshot().loadout.selectedWeapon)
        assertEquals(3, resource.persisted.size)
    }

    @Test
    fun rebirthPreservesEveryPermanentSliceAcrossMultipleTiers() {
        val initial = PlayerProfile(
            preferences = PlayerPreferences(masterVolume = 0.4f, textScale = 1.5f),
            economy = PlayerEconomy(4_000L, 9_000L),
            loadout = PlayerLoadout(
                coreShape = CoreShape.SHARD,
                selectedWeapon = WeaponId.MORNINGSTAR,
                unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
            ),
            labProgress = LabProgress(List(MetaUpgradeId.entries.size) { 1 }),
            collection = PlayerCollection(setOf(0, 199, 399)),
            rebirthProgress = RebirthProgress(level = 0, highestCleared = 0),
        )
        val resource = RecordingResource(loaded = initial)
        val store = testProfileStore(resource)

        assertIs<ProfileMutationResult.Applied>(store.advanceRebirth())
        assertIs<ProfileMutationResult.Applied>(
            store.applyGameplayProgress(GameplayProgressUpdate(clearedRebirthLevel = 1)),
        )
        assertIs<ProfileMutationResult.Applied>(store.advanceRebirth())

        val advanced = store.profileSnapshot()
        assertEquals(initial.preferences, advanced.preferences)
        assertEquals(initial.economy, advanced.economy)
        assertEquals(initial.loadout, advanced.loadout)
        assertEquals(initial.labProgress, advanced.labProgress)
        assertEquals(initial.collection, advanced.collection)
        assertEquals(RebirthProgress(level = 2, highestCleared = 1), advanced.rebirthProgress)
        assertEquals(3, resource.persisted.size)
    }

    @Test
    fun quarantineRejectsNonFiniteAndOversizedBootstrapPayloads() {
        val nonFinite = testProfileStore(
            RecordingResource(loaded = PlayerProfile(preferences = PlayerPreferences(masterVolume = Float.NaN))),
        )
        val oversizedRanks = testProfileStore(
            RecordingResource(
                loaded = PlayerProfile(labProgress = LabProgress(List(MetaUpgradeId.entries.size + 1) { 0 })),
            ),
        )
        val oversizedCollection = testProfileStore(
            RecordingResource(
                loaded = PlayerProfile(collection = PlayerCollection((0..400).toSet())),
            ),
        )

        assertEquals(
            ProfileLoadResult.Rejected(
                kinetickk.ball.profile.api.ProfileLoadRejection.BOOTSTRAP_NON_FINITE_NUMBER,
            ),
            nonFinite.bootstrapResult,
        )
        listOf(oversizedRanks, oversizedCollection).forEach { store ->
            assertEquals(
                ProfileLoadResult.Rejected(
                    kinetickk.ball.profile.api.ProfileLoadRejection.BOOTSTRAP_COLLECTION_LIMIT_EXCEEDED,
                ),
                store.bootstrapResult,
            )
            assertEquals(PlayerProfile(), store.profileSnapshot())
        }
    }

    @Test
    fun rejectedMutationDoesNotWriteOrPartiallyChangeAnotherSlice() {
        val original = PlayerProfile(economy = PlayerEconomy(0L, 0L))
        val resource = RecordingResource(loaded = original)
        val store = testProfileStore(resource)

        val result = assertIs<ProfileMutationResult.Rejected>(
            store.purchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
        )

        assertEquals(ProfileMutationRejection.INSUFFICIENT_MATTER, result.reason)
        assertEquals(original, store.profileSnapshot())
        assertTrue(resource.persisted.isEmpty())
    }

    @Test
    fun rejectedLabPurchaseDoesNotPublishOrPersist() {
        val original = PlayerProfile(economy = PlayerEconomy(0L, 0L))
        val resource = RecordingResource(loaded = original)
        val store = testProfileStore(resource)

        val result = assertIs<ProfileMutationResult.Rejected>(
            store.purchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
        )

        assertEquals(ProfileMutationRejection.INSUFFICIENT_MATTER, result.reason)
        assertEquals(original, store.profileSnapshot())
        assertTrue(resource.persisted.isEmpty())
    }

    @Test
    fun persistenceUncertaintyDoesNotRollbackCommittedProfile() {
        val resource = RecordingResource(
            loaded = PlayerProfile(),
            persistResult = ProfilePersistResult.OutcomeUnknown(
                kinetickk.ball.profile.api.ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        )
        val store = testProfileStore(resource)

        val result = assertIs<ProfileMutationResult.Applied>(
            store.updatePreferences(PlayerPreferences(masterVolume = 0.5f)),
        )

        assertIs<ProfilePersistResult.OutcomeUnknown>(result.persistence)
        assertEquals(0.5f, store.preferences().masterVolume)
    }

    @Test
    fun persistenceUncertaintyDoesNotRollbackAcceptedLabPurchase() {
        val initialMatter = 1_000L
        val resource = RecordingResource(
            loaded = PlayerProfile(economy = PlayerEconomy(initialMatter, initialMatter)),
            persistResult = ProfilePersistResult.OutcomeUnknown(
                kinetickk.ball.profile.api.ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        )
        val store = testProfileStore(resource)

        val result = assertIs<ProfileMutationResult.Applied>(
            store.purchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
        )

        assertIs<ProfilePersistResult.OutcomeUnknown>(result.persistence)
        assertEquals(1, store.labSnapshot().progress.rank(MetaUpgradeId.CORE_INTEGRITY))
        assertEquals(
            initialMatter - TestProfilePolicy.metaUpgrade(MetaUpgradeId.CORE_INTEGRITY).cost(0),
            store.labSnapshot().economy.matter,
        )
    }

    @Test
    fun acceptedProfileStateIsVisibleBeforeItsPersistenceEffectRuns() {
        lateinit var store: DefaultProfileStore
        var effectObserved = false
        val resource = RecordingResource(
            loaded = PlayerProfile(economy = PlayerEconomy(1_000L, 1_000L)),
            onPersist = { acceptedSnapshot ->
                effectObserved = true
                assertEquals(acceptedSnapshot, store.profileSnapshot())
            },
        )
        store = testProfileStore(resource)

        assertIs<ProfileMutationResult.Applied>(
            store.purchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
        )

        assertTrue(effectObserved)
    }

    @Test
    fun mutationsUseTheCapturedNonDefaultPolicy() {
        val customRebirth = TestProfilePolicy.rebirth.copy(
            minimumLevel = 2,
            maximumLevel = 3,
            profiles = TestProfilePolicy.rebirth.profiles.drop(2).take(2).toImmutableList(),
        )
        val customPolicy = TestProfilePolicy.copy(
            itemCount = 2,
            coreShapes = TestProfilePolicy.coreShapes.map { definition ->
                if (definition.id == CoreShape.PRISM) {
                    definition.copy(unlockLifetimeMatter = 500L)
                } else {
                    definition
                }
            }.toImmutableList(),
            weapons = TestProfilePolicy.weapons.map { definition ->
                if (definition.id == WeaponId.MORNINGSTAR) {
                    kinetickk.ball.content.api.WeaponDefinition(
                        definition.id,
                        definition.name,
                        definition.description,
                        definition.tags,
                        permanentUnlockCost = 7,
                    )
                } else {
                    definition
                }
            }.toImmutableList(),
            rebirth = customRebirth,
        )
        val resource = RecordingResource(
            loaded = PlayerProfile(
                economy = PlayerEconomy(matter = 20L, lifetimeMatter = 100L),
                rebirthProgress = RebirthProgress(level = 2, highestCleared = 2),
            ),
        )
        val store = DefaultProfileStore(resource, customPolicy)

        assertEquals(
            ProfileMutationRejection.CORE_SHAPE_LOCKED,
            assertIs<ProfileMutationResult.Rejected>(store.selectCoreShape(CoreShape.PRISM)).reason,
        )
        assertIs<ProfileMutationResult.Applied>(store.purchaseOrEquipWeapon(WeaponId.MORNINGSTAR))
        assertEquals(13L, store.profileSnapshot().economy.matter)
        assertEquals(
            ProfileMutationRejection.INVALID_GAMEPLAY_PROGRESS,
            assertIs<ProfileMutationResult.Rejected>(
                store.applyGameplayProgress(GameplayProgressUpdate(discoveredItemIds = setOf(2))),
            ).reason,
        )
        assertIs<ProfileMutationResult.Applied>(store.advanceRebirth())
        assertEquals(
            ProfileMutationRejection.REBIRTH_UNAVAILABLE,
            assertIs<ProfileMutationResult.Rejected>(store.advanceRebirth()).reason,
        )
    }
}

private class RecordingResource(
    loaded: PlayerProfile? = null,
    private val persistResult: ProfilePersistResult = ProfilePersistResult.Persisted,
    private val onPersist: (PlayerProfile) -> Unit = {},
) : ProfileResource {
    override val providerId: ProfileProviderId = ProfileProviderId.PLATFORM_LOCAL
    private val loadResult = loaded?.let(ProfileLoadResult::Loaded) ?: ProfileLoadResult.NotFound
    val persisted = mutableListOf<PlayerProfile>()

    override fun load(): ProfileLoadResult = loadResult

    override fun persist(profile: PlayerProfile): ProfilePersistResult {
        onPersist(profile)
        persisted += profile
        return persistResult
    }
}
