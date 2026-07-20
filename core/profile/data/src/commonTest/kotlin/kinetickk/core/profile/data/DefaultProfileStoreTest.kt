// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.data

import kinetickk.core.content.CoreShape
import kinetickk.core.content.MetaUpgradeCatalog
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.content.WeaponCatalog
import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.GameplayProgressUpdate
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.PlayerCollection
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.core.profile.api.PlayerLoadout
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.core.profile.api.PlayerProfile
import kinetickk.core.profile.api.ProfileLoadResult
import kinetickk.core.profile.api.ProfileMutationRejection
import kinetickk.core.profile.api.ProfileMutationResult
import kinetickk.core.profile.api.ProfilePersistResult
import kinetickk.core.profile.api.ProfileProviderId
import kinetickk.core.profile.api.ProfileResource
import kinetickk.core.profile.api.RebirthProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultProfileStoreTest {
    @Test
    fun bootstrapQuarantineNormalizesEverySliceBeforeExposure() {
        val store = DefaultProfileStore(
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
        assertEquals(MetaUpgradeCatalog.byId(MetaUpgradeId.CORE_INTEGRITY).maxRanks, profile.labProgress.ranks.first())
        assertTrue(profile.collection.discoveredItemIds.isEmpty())
        assertEquals(RebirthProgress(10, 10), profile.rebirthProgress)
        assertEquals(profile.economy, store.profileSnapshot().economy)
    }

    @Test
    fun labAndLoadoutPurchasesUpdateFullProfileInSingleWrites() {
        val resource = RecordingResource(
            loaded = PlayerProfile(economy = PlayerEconomy(10_000L, 10_000L)),
        )
        val store = DefaultProfileStore(resource)

        assertIs<ProfileMutationResult.Applied>(
            store.purchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
        )
        assertIs<ProfileMutationResult.Applied>(
            store.purchaseOrEquipWeapon(WeaponId.SINGULARITY_SPEAR),
        )
        assertIs<ProfileMutationResult.Applied>(store.selectCoreShape(CoreShape.SHARD))

        val expectedMatter = 10_000L - MetaUpgradeCatalog.byId(MetaUpgradeId.CORE_INTEGRITY).cost(0) -
            WeaponCatalog.byId(WeaponId.SINGULARITY_SPEAR).permanentUnlockCost
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
        val store = DefaultProfileStore(resource)

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
        val store = DefaultProfileStore(resource)

        MetaUpgradeId.entries.forEach { id ->
            assertIs<ProfileMutationResult.Applied>(store.purchaseMetaUpgrade(id))
        }

        assertEquals(List(MetaUpgradeId.entries.size) { 1 }, store.profileSnapshot().labProgress.ranks)
        assertEquals(
            initialMatter - MetaUpgradeCatalog.all.sumOf { definition -> definition.cost(0).toLong() },
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
        val store = DefaultProfileStore(resource)
        val weapon = WeaponId.MORNINGSTAR

        assertIs<ProfileMutationResult.Applied>(store.purchaseOrEquipWeapon(weapon))
        val matterAfterUnlock = store.profileSnapshot().economy.matter
        assertEquals(
            initialMatter - WeaponCatalog.byId(weapon).permanentUnlockCost,
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
        val store = DefaultProfileStore(resource)

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
        val nonFinite = DefaultProfileStore(
            RecordingResource(loaded = PlayerProfile(preferences = PlayerPreferences(masterVolume = Float.NaN))),
        )
        val oversizedRanks = DefaultProfileStore(
            RecordingResource(
                loaded = PlayerProfile(labProgress = LabProgress(List(MetaUpgradeId.entries.size + 1) { 0 })),
            ),
        )
        val oversizedCollection = DefaultProfileStore(
            RecordingResource(
                loaded = PlayerProfile(collection = PlayerCollection((0..400).toSet())),
            ),
        )

        assertEquals(
            ProfileLoadResult.Rejected(
                kinetickk.core.profile.api.ProfileLoadRejection.BOOTSTRAP_NON_FINITE_NUMBER,
            ),
            nonFinite.bootstrapResult,
        )
        listOf(oversizedRanks, oversizedCollection).forEach { store ->
            assertEquals(
                ProfileLoadResult.Rejected(
                    kinetickk.core.profile.api.ProfileLoadRejection.BOOTSTRAP_COLLECTION_LIMIT_EXCEEDED,
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
        val store = DefaultProfileStore(resource)

        val result = assertIs<ProfileMutationResult.Rejected>(
            store.purchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
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
                kinetickk.core.profile.api.ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        )
        val store = DefaultProfileStore(resource)

        val result = assertIs<ProfileMutationResult.Applied>(
            store.updatePreferences(PlayerPreferences(masterVolume = 0.5f)),
        )

        assertIs<ProfilePersistResult.OutcomeUnknown>(result.persistence)
        assertEquals(0.5f, store.preferences().masterVolume)
    }
}

private class RecordingResource(
    loaded: PlayerProfile? = null,
    private val persistResult: ProfilePersistResult = ProfilePersistResult.Persisted,
) : ProfileResource {
    override val providerId: ProfileProviderId = ProfileProviderId.PLATFORM_LOCAL
    private val loadResult = loaded?.let(ProfileLoadResult::Loaded) ?: ProfileLoadResult.NotFound
    val persisted = mutableListOf<PlayerProfile>()

    override fun load(): ProfileLoadResult = loadResult

    override fun persist(profile: PlayerProfile): ProfilePersistResult {
        persisted += profile
        return persistResult
    }
}
