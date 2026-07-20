// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.api

import kinetickk.core.collections.ImmutableSet
import kinetickk.core.collections.immutableSetOf
import kinetickk.core.collections.toImmutableSet
import kinetickk.core.content.CoreShape
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.content.WeaponId

data class LabProfileSnapshot(
    val economy: PlayerEconomy,
    val progress: LabProgress,
)

data class LoadoutProfileSnapshot(
    val economy: PlayerEconomy,
    val loadout: PlayerLoadout,
)

data class RebirthProfileSnapshot(
    val progress: RebirthProgress,
)

data class GameplayProfileSnapshot(
    val preferences: PlayerPreferences,
    val economy: PlayerEconomy,
    val loadout: PlayerLoadout,
    val labProgress: LabProgress,
    val collection: PlayerCollection,
    val rebirthProgress: RebirthProgress,
)

data class GameplayProgressUpdate(
    val bankedMatter: Long = 0L,
    val discoveredItemIds: ImmutableSet<Int> = immutableSetOf(),
    val clearedRebirthLevel: Int? = null,
) {
    constructor(
        bankedMatter: Long = 0L,
        discoveredItemIds: Set<Int>,
        clearedRebirthLevel: Int? = null,
    ) : this(bankedMatter, discoveredItemIds.toImmutableSet(), clearedRebirthLevel)
}

enum class ProfileMutationRejection {
    NO_CHANGE,
    INSUFFICIENT_MATTER,
    MAX_RANK_REACHED,
    CORE_SHAPE_LOCKED,
    INVALID_GAMEPLAY_PROGRESS,
    REBIRTH_UNAVAILABLE,
}

sealed interface ProfileMutationResult {
    data class Applied(
        val persistence: ProfilePersistResult,
    ) : ProfileMutationResult

    data class Rejected(
        val reason: ProfileMutationRejection,
    ) : ProfileMutationResult
}

interface PlayerProfileProvider {
    fun profileSnapshot(): PlayerProfile
}

interface PreferencesReader {
    fun preferences(): PlayerPreferences
}

interface SettingsProfileCapability : PreferencesReader {
    fun updatePreferences(preferences: PlayerPreferences): ProfileMutationResult
}

interface LabPurchaseCapability {
    fun labSnapshot(): LabProfileSnapshot
    fun purchaseMetaUpgrade(id: MetaUpgradeId): ProfileMutationResult
}

interface LoadoutCapability {
    fun loadoutSnapshot(): LoadoutProfileSnapshot
    fun selectCoreShape(shape: CoreShape): ProfileMutationResult
    fun purchaseOrEquipWeapon(id: WeaponId): ProfileMutationResult
}

interface CollectionCapability {
    fun collectionSnapshot(): PlayerCollection
}

interface RebirthCapability {
    fun rebirthSnapshot(): RebirthProfileSnapshot
    fun advanceRebirth(): ProfileMutationResult
}

interface GameplayProgressCapability {
    fun applyGameplayProgress(update: GameplayProgressUpdate): ProfileMutationResult
}

/** Full composition-root surface. Feature implementations receive only one of its narrow parents. */
interface ProfileStore :
    PlayerProfileProvider,
    SettingsProfileCapability,
    LabPurchaseCapability,
    LoadoutCapability,
    CollectionCapability,
    RebirthCapability,
    GameplayProgressCapability {
    val providerId: ProfileProviderId
    val bootstrapResult: ProfileLoadResult
    fun replaceProfile(profile: PlayerProfile): ProfilePersistResult
}
