// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.data

import kinetickk.core.content.CoreShape
import kinetickk.core.content.ItemCatalog
import kinetickk.core.content.MetaUpgradeCatalog
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.content.RebirthProgression
import kinetickk.core.content.WeaponCatalog
import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.GameplayProgressUpdate
import kinetickk.core.profile.api.LabProfileSnapshot
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.LoadoutProfileSnapshot
import kinetickk.core.profile.api.PlayerCollection
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.core.profile.api.PlayerLoadout
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.core.profile.api.PlayerProfile
import kinetickk.core.profile.api.ProfileLoadRejection
import kinetickk.core.profile.api.ProfileLoadResult
import kinetickk.core.profile.api.ProfileMutationRejection
import kinetickk.core.profile.api.ProfileMutationResult
import kinetickk.core.profile.api.ProfilePersistResult
import kinetickk.core.profile.api.ProfileProviderId
import kinetickk.core.profile.api.ProfileResource
import kinetickk.core.profile.api.ProfileResourceFailure
import kinetickk.core.profile.api.ProfileStore
import kinetickk.core.profile.api.RebirthProfileSnapshot
import kinetickk.core.profile.api.RebirthProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Records the public ProfileStore behavior at the migration baseline.
 *
 * Catalog formulas and codec details have their own tests; this suite focuses on mutation
 * acceptance, rejection atomicity, published query snapshots, and persistence ordering. The
 * documented v4 reset and removal of arbitrary production replacement remain intentional deltas.
 */
class ProfileStoreCharacterizationTest {
    @Test
    fun bootstrapOutcomesSelectTheObservableInitialProfileWithoutWriting() {
        val loadedProfile = representativeProfile()
        val loadedResource = CharacterizationProfileResource(
            loadResult = ProfileLoadResult.Loaded(loadedProfile),
        )
        val loadedStore = DefaultProfileStore(loadedResource)

        assertEquals(ProfileProviderId.PLATFORM_LOCAL, loadedStore.providerId)
        assertEquals(ProfileLoadResult.Loaded(loadedProfile), loadedStore.bootstrapResult)
        assertProfileViews(loadedProfile, loadedStore)
        assertEquals(1, loadedResource.loadAttempts)
        assertTrue(loadedResource.persistenceAttempts.isEmpty())

        val unavailableResults = listOf(
            ProfileLoadResult.NotFound,
            ProfileLoadResult.Rejected(ProfileLoadRejection.MALFORMED_PAYLOAD),
            ProfileLoadResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_READ_FAILED),
        )
        unavailableResults.forEach { loadResult ->
            val resource = CharacterizationProfileResource(loadResult = loadResult)
            val store = DefaultProfileStore(resource)

            assertEquals(loadResult, store.bootstrapResult)
            assertProfileViews(PlayerProfile(), store)
            assertEquals(1, resource.loadAttempts)
            assertTrue(resource.persistenceAttempts.isEmpty())
        }

        val throwingResource = CharacterizationProfileResource(
            loadFailure = IllegalStateException("read failed"),
        )
        val throwingStore = DefaultProfileStore(throwingResource)
        assertEquals(
            ProfileLoadResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_READ_FAILED),
            throwingStore.bootstrapResult,
        )
        assertProfileViews(PlayerProfile(), throwingStore)
        assertEquals(1, throwingResource.loadAttempts)
        assertTrue(throwingResource.persistenceAttempts.isEmpty())
    }

    @Test
    fun replaceProfileQuarantinesPublishesAndPersistsWhileInvalidReplacementIsAtomic() {
        val replacement = PlayerProfile(
            preferences = PlayerPreferences(
                soundEnabled = false,
                musicEnabled = false,
                masterVolume = 2f,
                simulationSpeed = 0.5f,
                textScale = 0.5f,
            ),
            economy = PlayerEconomy(matter = -5L, lifetimeMatter = 3L),
            loadout = PlayerLoadout(unlockedWeapons = emptySet()),
            labProgress = LabProgress(listOf(Int.MAX_VALUE)),
            collection = PlayerCollection(setOf(-1, 399, ItemCatalog.ITEM_COUNT)),
            rebirthProgress = RebirthProgress(level = 99, highestCleared = 99),
        )
        val expected = PlayerProfile(
            preferences = replacement.preferences.normalized(),
            economy = PlayerEconomy(matter = 0L, lifetimeMatter = 3L),
            loadout = PlayerLoadout(unlockedWeapons = setOf(WeaponId.FLUX_WAKE)),
            labProgress = LabProgress(
                List(MetaUpgradeId.entries.size) { index ->
                    if (index == MetaUpgradeId.CORE_INTEGRITY.ordinal) {
                        MetaUpgradeCatalog.byId(MetaUpgradeId.CORE_INTEGRITY).maxRanks
                    } else {
                        0
                    }
                },
            ),
            collection = PlayerCollection(setOf(399)),
            rebirthProgress = RebirthProgress(
                level = RebirthProgression.MAX_LEVEL,
                highestCleared = RebirthProgression.MAX_LEVEL,
            ),
        )
        lateinit var store: DefaultProfileStore
        val resource = CharacterizationProfileResource(
            beforePersist = { snapshot -> assertProfileViews(snapshot, store) },
        )
        store = DefaultProfileStore(resource)

        assertEquals(ProfilePersistResult.Persisted, store.replaceProfile(replacement))
        assertProfileViews(expected, store)
        assertEquals(listOf(expected), resource.persistenceAttempts)
        assertEquals(ProfileLoadResult.NotFound, store.bootstrapResult)

        val invalid = expected.copy(
            preferences = expected.preferences.copy(masterVolume = Float.NaN),
        )
        assertEquals(
            ProfilePersistResult.OutcomeUnknown(ProfileResourceFailure.ENCODING_FAILED),
            store.replaceProfile(invalid),
        )
        assertProfileViews(expected, store)
        assertEquals(listOf(expected), resource.persistenceAttempts)
    }

    @Test
    fun preferencesNormalizeAndMuteBothChannelsWhileNoChangeAndNonFiniteValuesReject() {
        val initial = representativeProfile()
        val resource = CharacterizationProfileResource(ProfileLoadResult.Loaded(initial))
        val store = DefaultProfileStore(resource)
        val requested = initial.preferences.copy(
            soundEnabled = false,
            musicEnabled = false,
            masterVolume = 4f,
            simulationSpeed = 0.25f,
            textScale = 3f,
        )
        val expected = initial.copy(preferences = requested.normalized())

        assertEquals(
            ProfileMutationResult.Applied(ProfilePersistResult.Persisted),
            store.updatePreferences(requested),
        )
        assertProfileViews(expected, store)
        assertEquals(listOf(expected), resource.persistenceAttempts)

        assertRejectedWithoutPublication(
            result = store.updatePreferences(requested),
            reason = ProfileMutationRejection.NO_CHANGE,
            expectedProfile = expected,
            store = store,
            resource = resource,
            expectedPersistenceAttempts = 1,
        )

        listOf(
            expected.preferences.copy(masterVolume = Float.NaN),
            expected.preferences.copy(simulationSpeed = Float.POSITIVE_INFINITY),
            expected.preferences.copy(textScale = Float.NEGATIVE_INFINITY),
        ).forEach { invalid ->
            assertRejectedWithoutPublication(
                result = store.updatePreferences(invalid),
                reason = ProfileMutationRejection.INVALID_GAMEPLAY_PROGRESS,
                expectedProfile = expected,
                store = store,
                resource = resource,
                expectedPersistenceAttempts = 1,
            )
        }
    }

    @Test
    fun labPurchaseUsesTheCurrentRankPriceAndRejectsInsufficientOrMaxedProfilesAtomically() {
        val id = MetaUpgradeId.CORE_INTEGRITY
        val cost = MetaUpgradeCatalog.byId(id).cost(0).toLong()
        val initial = representativeProfile().copy(
            economy = PlayerEconomy(matter = cost, lifetimeMatter = 500L),
            labProgress = LabProgress(),
        )
        val resource = CharacterizationProfileResource(ProfileLoadResult.Loaded(initial))
        val store = DefaultProfileStore(resource)
        val expectedRanks = initial.labProgress.ranks.toMutableList().apply {
            this[id.ordinal] = 1
        }
        val expected = initial.copy(
            economy = initial.economy.copy(matter = 0L),
            labProgress = LabProgress(expectedRanks),
        )

        assertEquals(
            ProfileMutationResult.Applied(ProfilePersistResult.Persisted),
            store.purchaseMetaUpgrade(id),
        )
        assertProfileViews(expected, store)
        assertEquals(listOf(expected), resource.persistenceAttempts)

        val insufficient = initial.copy(economy = PlayerEconomy(cost - 1L, 500L))
        val insufficientResource = CharacterizationProfileResource(ProfileLoadResult.Loaded(insufficient))
        val insufficientStore = DefaultProfileStore(insufficientResource)
        assertRejectedWithoutPublication(
            result = insufficientStore.purchaseMetaUpgrade(id),
            reason = ProfileMutationRejection.INSUFFICIENT_MATTER,
            expectedProfile = insufficient,
            store = insufficientStore,
            resource = insufficientResource,
        )

        val maxRanks = List(MetaUpgradeId.entries.size) { index ->
            if (index == id.ordinal) MetaUpgradeCatalog.byId(id).maxRanks else 0
        }
        val maxed = initial.copy(labProgress = LabProgress(maxRanks))
        val maxedResource = CharacterizationProfileResource(ProfileLoadResult.Loaded(maxed))
        val maxedStore = DefaultProfileStore(maxedResource)
        assertRejectedWithoutPublication(
            result = maxedStore.purchaseMetaUpgrade(id),
            reason = ProfileMutationRejection.MAX_RANK_REACHED,
            expectedProfile = maxed,
            store = maxedStore,
            resource = maxedResource,
        )
    }

    @Test
    fun coreShapeSelectionUsesLifetimeMatterBoundariesAndRejectsLockedOrSelectedShapes() {
        val prismReady = representativeProfile().copy(
            economy = PlayerEconomy(matter = 0L, lifetimeMatter = 25L),
            loadout = PlayerLoadout(),
        )
        val resource = CharacterizationProfileResource(ProfileLoadResult.Loaded(prismReady))
        val store = DefaultProfileStore(resource)
        val prismSelected = prismReady.copy(
            loadout = prismReady.loadout.copy(coreShape = CoreShape.PRISM),
        )

        assertEquals(
            ProfileMutationResult.Applied(ProfilePersistResult.Persisted),
            store.selectCoreShape(CoreShape.PRISM),
        )
        assertProfileViews(prismSelected, store)
        assertEquals(listOf(prismSelected), resource.persistenceAttempts)
        assertRejectedWithoutPublication(
            result = store.selectCoreShape(CoreShape.PRISM),
            reason = ProfileMutationRejection.NO_CHANGE,
            expectedProfile = prismSelected,
            store = store,
            resource = resource,
            expectedPersistenceAttempts = 1,
        )

        val shardLocked = prismReady.copy(
            economy = PlayerEconomy(matter = 0L, lifetimeMatter = 89L),
        )
        val lockedResource = CharacterizationProfileResource(ProfileLoadResult.Loaded(shardLocked))
        val lockedStore = DefaultProfileStore(lockedResource)
        assertRejectedWithoutPublication(
            result = lockedStore.selectCoreShape(CoreShape.SHARD),
            reason = ProfileMutationRejection.CORE_SHAPE_LOCKED,
            expectedProfile = shardLocked,
            store = lockedStore,
            resource = lockedResource,
        )

        val shardReady = shardLocked.copy(economy = shardLocked.economy.copy(lifetimeMatter = 90L))
        val shardResource = CharacterizationProfileResource(ProfileLoadResult.Loaded(shardReady))
        val shardStore = DefaultProfileStore(shardResource)
        val shardSelected = shardReady.copy(
            loadout = shardReady.loadout.copy(coreShape = CoreShape.SHARD),
        )
        assertEquals(
            ProfileMutationResult.Applied(ProfilePersistResult.Persisted),
            shardStore.selectCoreShape(CoreShape.SHARD),
        )
        assertProfileViews(shardSelected, shardStore)
        assertEquals(listOf(shardSelected), shardResource.persistenceAttempts)
    }

    @Test
    fun weaponMutationDistinguishesUnlockEquipAndAlreadySelectedPaths() {
        val weapon = WeaponId.MORNINGSTAR
        val cost = WeaponCatalog.byId(weapon).permanentUnlockCost.toLong()
        val initial = representativeProfile().copy(
            economy = PlayerEconomy(matter = cost, lifetimeMatter = 500L),
            loadout = PlayerLoadout(),
        )
        val resource = CharacterizationProfileResource(ProfileLoadResult.Loaded(initial))
        val store = DefaultProfileStore(resource)
        val purchased = initial.copy(
            economy = initial.economy.copy(matter = 0L),
            loadout = PlayerLoadout(
                selectedWeapon = weapon,
                unlockedWeapons = setOf(WeaponId.FLUX_WAKE, weapon),
            ),
        )

        assertEquals(
            ProfileMutationResult.Applied(ProfilePersistResult.Persisted),
            store.purchaseOrEquipWeapon(weapon),
        )
        assertProfileViews(purchased, store)
        assertEquals(listOf(purchased), resource.persistenceAttempts)
        assertRejectedWithoutPublication(
            result = store.purchaseOrEquipWeapon(weapon),
            reason = ProfileMutationRejection.NO_CHANGE,
            expectedProfile = purchased,
            store = store,
            resource = resource,
            expectedPersistenceAttempts = 1,
        )

        val fluxEquipped = purchased.copy(
            loadout = purchased.loadout.copy(selectedWeapon = WeaponId.FLUX_WAKE),
        )
        assertEquals(
            ProfileMutationResult.Applied(ProfilePersistResult.Persisted),
            store.purchaseOrEquipWeapon(WeaponId.FLUX_WAKE),
        )
        assertProfileViews(fluxEquipped, store)
        assertEquals(fluxEquipped, resource.persistenceAttempts.last())

        val reequipped = fluxEquipped.copy(loadout = fluxEquipped.loadout.copy(selectedWeapon = weapon))
        assertEquals(
            ProfileMutationResult.Applied(ProfilePersistResult.Persisted),
            store.purchaseOrEquipWeapon(weapon),
        )
        assertProfileViews(reequipped, store)
        assertEquals(0L, reequipped.economy.matter)
        assertEquals(3, resource.persistenceAttempts.size)
        assertEquals(reequipped, resource.persistenceAttempts.last())

        val insufficient = initial.copy(economy = PlayerEconomy(cost - 1L, 500L))
        val insufficientResource = CharacterizationProfileResource(ProfileLoadResult.Loaded(insufficient))
        val insufficientStore = DefaultProfileStore(insufficientResource)
        assertRejectedWithoutPublication(
            result = insufficientStore.purchaseOrEquipWeapon(weapon),
            reason = ProfileMutationRejection.INSUFFICIENT_MATTER,
            expectedProfile = insufficient,
            store = insufficientStore,
            resource = insufficientResource,
        )
    }

    @Test
    fun rebirthAdvancesOnlyAfterTheCurrentLevelWasClearedAndStopsAtTheMaximum() {
        val unavailable = representativeProfile().copy(
            rebirthProgress = RebirthProgress(level = 0, highestCleared = -1),
        )
        val unavailableResource = CharacterizationProfileResource(ProfileLoadResult.Loaded(unavailable))
        val unavailableStore = DefaultProfileStore(unavailableResource)
        assertRejectedWithoutPublication(
            result = unavailableStore.advanceRebirth(),
            reason = ProfileMutationRejection.REBIRTH_UNAVAILABLE,
            expectedProfile = unavailable,
            store = unavailableStore,
            resource = unavailableResource,
        )

        val ready = unavailable.copy(rebirthProgress = RebirthProgress(level = 0, highestCleared = 0))
        val resource = CharacterizationProfileResource(ProfileLoadResult.Loaded(ready))
        val store = DefaultProfileStore(resource)
        val advanced = ready.copy(rebirthProgress = RebirthProgress(level = 1, highestCleared = 0))
        assertEquals(
            ProfileMutationResult.Applied(ProfilePersistResult.Persisted),
            store.advanceRebirth(),
        )
        assertProfileViews(advanced, store)
        assertEquals(listOf(advanced), resource.persistenceAttempts)
        assertRejectedWithoutPublication(
            result = store.advanceRebirth(),
            reason = ProfileMutationRejection.REBIRTH_UNAVAILABLE,
            expectedProfile = advanced,
            store = store,
            resource = resource,
            expectedPersistenceAttempts = 1,
        )

        val maximum = ready.copy(
            rebirthProgress = RebirthProgress(
                level = RebirthProgression.MAX_LEVEL,
                highestCleared = RebirthProgression.MAX_LEVEL,
            ),
        )
        val maximumResource = CharacterizationProfileResource(ProfileLoadResult.Loaded(maximum))
        val maximumStore = DefaultProfileStore(maximumResource)
        assertRejectedWithoutPublication(
            result = maximumStore.advanceRebirth(),
            reason = ProfileMutationRejection.REBIRTH_UNAVAILABLE,
            expectedProfile = maximum,
            store = maximumStore,
            resource = maximumResource,
        )
    }

    @Test
    fun gameplayProgressMergesAtomicallySaturatesEconomyAndRejectsEveryInvalidBoundary() {
        val initial = representativeProfile().copy(
            economy = PlayerEconomy(Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L),
            collection = PlayerCollection(setOf(1)),
            rebirthProgress = RebirthProgress(level = 2, highestCleared = 1),
        )
        val resource = CharacterizationProfileResource(ProfileLoadResult.Loaded(initial))
        val store = DefaultProfileStore(resource)
        val expected = initial.copy(
            economy = PlayerEconomy(Long.MAX_VALUE, Long.MAX_VALUE),
            collection = PlayerCollection(setOf(1, ItemCatalog.ITEM_COUNT - 1)),
            rebirthProgress = RebirthProgress(level = 2, highestCleared = 2),
        )

        assertEquals(
            ProfileMutationResult.Applied(ProfilePersistResult.Persisted),
            store.applyGameplayProgress(
                GameplayProgressUpdate(
                    bankedMatter = 10L,
                    discoveredItemIds = setOf(1, ItemCatalog.ITEM_COUNT - 1),
                    clearedRebirthLevel = 2,
                ),
            ),
        )
        assertProfileViews(expected, store)
        assertEquals(listOf(expected), resource.persistenceAttempts)

        assertRejectedWithoutPublication(
            result = store.applyGameplayProgress(
                GameplayProgressUpdate(
                    discoveredItemIds = setOf(1),
                    clearedRebirthLevel = 1,
                ),
            ),
            reason = ProfileMutationRejection.NO_CHANGE,
            expectedProfile = expected,
            store = store,
            resource = resource,
            expectedPersistenceAttempts = 1,
        )

        listOf(
            GameplayProgressUpdate(bankedMatter = -1L),
            GameplayProgressUpdate(discoveredItemIds = setOf(-1)),
            GameplayProgressUpdate(discoveredItemIds = setOf(ItemCatalog.ITEM_COUNT)),
            GameplayProgressUpdate(clearedRebirthLevel = -1),
            GameplayProgressUpdate(clearedRebirthLevel = 3),
        ).forEach { invalid ->
            assertRejectedWithoutPublication(
                result = store.applyGameplayProgress(invalid),
                reason = ProfileMutationRejection.INVALID_GAMEPLAY_PROGRESS,
                expectedProfile = expected,
                store = store,
                resource = resource,
                expectedPersistenceAttempts = 1,
            )
        }
    }

    @Test
    fun persistenceExceptionsBecomeOutcomeUnknownAfterPublicationWithoutRetryOrRollback() {
        lateinit var store: DefaultProfileStore
        val resource = CharacterizationProfileResource(
            persistFailure = IllegalStateException("write failed"),
            beforePersist = { snapshot -> assertProfileViews(snapshot, store) },
        )
        store = DefaultProfileStore(resource)
        val muted = PlayerProfile(
            preferences = PlayerPreferences(soundEnabled = false, musicEnabled = false),
        )

        assertEquals(
            ProfileMutationResult.Applied(
                ProfilePersistResult.OutcomeUnknown(
                    ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
            ),
            store.updatePreferences(muted.preferences),
        )
        assertProfileViews(muted, store)
        assertEquals(listOf(muted), resource.persistenceAttempts)

        val replacement = representativeProfile()
        assertEquals(
            ProfilePersistResult.OutcomeUnknown(
                ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
            store.replaceProfile(replacement),
        )
        assertProfileViews(replacement, store)
        assertEquals(listOf(muted, replacement), resource.persistenceAttempts)

        assertRejectedWithoutPublication(
            result = store.updatePreferences(replacement.preferences),
            reason = ProfileMutationRejection.NO_CHANGE,
            expectedProfile = replacement,
            store = store,
            resource = resource,
            expectedPersistenceAttempts = 2,
        )
    }
}

private fun representativeProfile(): PlayerProfile = PlayerProfile(
    preferences = PlayerPreferences(
        masterVolume = 0.4f,
        simulationSpeed = 1.25f,
        textScale = 1.5f,
    ),
    economy = PlayerEconomy(matter = 2_000L, lifetimeMatter = 4_000L),
    loadout = PlayerLoadout(
        coreShape = CoreShape.PRISM,
        selectedWeapon = WeaponId.MORNINGSTAR,
        unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
    ),
    labProgress = LabProgress(List(MetaUpgradeId.entries.size) { index -> index % 2 }),
    collection = PlayerCollection(setOf(0, 199, ItemCatalog.ITEM_COUNT - 1)),
    rebirthProgress = RebirthProgress(level = 2, highestCleared = 1),
)

private fun assertProfileViews(expected: PlayerProfile, store: ProfileStore) {
    assertEquals(expected, store.profileSnapshot())
    assertEquals(expected.preferences, store.preferences())
    assertEquals(
        LabProfileSnapshot(expected.economy, expected.labProgress),
        store.labSnapshot(),
    )
    assertEquals(
        LoadoutProfileSnapshot(expected.economy, expected.loadout),
        store.loadoutSnapshot(),
    )
    assertEquals(expected.collection, store.collectionSnapshot())
    assertEquals(RebirthProfileSnapshot(expected.rebirthProgress), store.rebirthSnapshot())
}

private fun assertRejectedWithoutPublication(
    result: ProfileMutationResult,
    reason: ProfileMutationRejection,
    expectedProfile: PlayerProfile,
    store: ProfileStore,
    resource: CharacterizationProfileResource,
    expectedPersistenceAttempts: Int = 0,
) {
    assertEquals(ProfileMutationResult.Rejected(reason), result)
    assertProfileViews(expectedProfile, store)
    assertEquals(expectedPersistenceAttempts, resource.persistenceAttempts.size)
}

private class CharacterizationProfileResource(
    private val loadResult: ProfileLoadResult = ProfileLoadResult.NotFound,
    private val loadFailure: Throwable? = null,
    private val persistResult: ProfilePersistResult = ProfilePersistResult.Persisted,
    private val persistFailure: Throwable? = null,
    private val beforePersist: (PlayerProfile) -> Unit = {},
) : ProfileResource {
    override val providerId: ProfileProviderId = ProfileProviderId.PLATFORM_LOCAL

    var loadAttempts: Int = 0
        private set

    val persistenceAttempts = mutableListOf<PlayerProfile>()

    override fun load(): ProfileLoadResult {
        loadAttempts += 1
        loadFailure?.let { throw it }
        return loadResult
    }

    override fun persist(profile: PlayerProfile): ProfilePersistResult {
        persistenceAttempts += profile
        beforePersist(profile)
        persistFailure?.let { throw it }
        return persistResult
    }
}
