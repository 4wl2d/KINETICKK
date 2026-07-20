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
import kinetickk.core.profile.api.CollectionCapability
import kinetickk.core.profile.api.GameplayProgressUpdate
import kinetickk.core.profile.api.LabProfileSnapshot
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.LoadoutProfileSnapshot
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
import kinetickk.core.profile.api.ProfileResourceFailure
import kinetickk.core.profile.api.ProfileStore
import kinetickk.core.profile.api.RebirthProfileSnapshot
import kinetickk.core.profile.api.RebirthProgress
import kotlin.math.max

/** Single owner of the complete profile and all cross-slice transactions. */
class DefaultProfileStore(
    private val resource: ProfileResource,
) : ProfileStore {
    private val providerAccepted: Boolean = runCatching {
        resource.providerId == ProfileProviderId.PLATFORM_LOCAL
    }.getOrDefault(false)

    override val providerId: ProfileProviderId = ProfileProviderId.PLATFORM_LOCAL

    override val bootstrapResult: ProfileLoadResult

    private var currentProfile: PlayerProfile

    init {
        val rawLoadResult = if (providerAccepted) {
            runCatching(resource::load).getOrElse {
                ProfileLoadResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_READ_FAILED)
            }
        } else {
            ProfileLoadResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_READ_FAILED)
        }
        bootstrapResult = when (rawLoadResult) {
            is ProfileLoadResult.Loaded -> quarantineBootstrapProfile(rawLoadResult.profile)
            ProfileLoadResult.NotFound -> ProfileLoadResult.NotFound
            is ProfileLoadResult.Rejected -> rawLoadResult
            is ProfileLoadResult.OutcomeUnknown -> rawLoadResult
        }
        currentProfile = (bootstrapResult as? ProfileLoadResult.Loaded)?.profile ?: PlayerProfile()
    }

    override fun profileSnapshot(): PlayerProfile = currentProfile

    override fun preferences(): PlayerPreferences = currentProfile.preferences

    override fun labSnapshot(): LabProfileSnapshot = LabProfileSnapshot(
        economy = currentProfile.economy,
        progress = currentProfile.labProgress,
    )

    override fun loadoutSnapshot(): LoadoutProfileSnapshot = LoadoutProfileSnapshot(
        economy = currentProfile.economy,
        loadout = currentProfile.loadout,
    )

    override fun collectionSnapshot(): PlayerCollection = currentProfile.collection

    override fun rebirthSnapshot(): RebirthProfileSnapshot =
        RebirthProfileSnapshot(currentProfile.rebirthProgress)

    override fun replaceProfile(profile: PlayerProfile): ProfilePersistResult {
        val quarantined = quarantineBootstrapProfile(profile)
        if (quarantined !is ProfileLoadResult.Loaded) {
            return ProfilePersistResult.OutcomeUnknown(ProfileResourceFailure.ENCODING_FAILED)
        }
        currentProfile = quarantined.profile
        return persistCurrent()
    }

    override fun updatePreferences(preferences: PlayerPreferences): ProfileMutationResult {
        if (
            !preferences.masterVolume.isFinite() ||
            !preferences.simulationSpeed.isFinite() ||
            !preferences.textScale.isFinite()
        ) {
            return rejected(ProfileMutationRejection.INVALID_GAMEPLAY_PROGRESS)
        }
        val normalized = preferences.normalized()
        if (normalized == currentProfile.preferences) return rejected(ProfileMutationRejection.NO_CHANGE)
        return commit(currentProfile.copy(preferences = normalized))
    }

    override fun purchaseMetaUpgrade(id: MetaUpgradeId): ProfileMutationResult {
        val definition = MetaUpgradeCatalog.byId(id)
        val currentRank = currentProfile.labProgress.rank(id)
        if (currentRank >= definition.maxRanks) return rejected(ProfileMutationRejection.MAX_RANK_REACHED)
        val cost = definition.cost(currentRank).toLong()
        if (currentProfile.economy.matter < cost) return rejected(ProfileMutationRejection.INSUFFICIENT_MATTER)

        val ranks = currentProfile.labProgress.ranks.toMutableList()
        ranks[id.ordinal] = currentRank + 1
        return commit(
            currentProfile.copy(
                economy = currentProfile.economy.copy(matter = currentProfile.economy.matter - cost),
                labProgress = LabProgress(ranks),
            ),
        )
    }

    override fun selectCoreShape(shape: CoreShape): ProfileMutationResult {
        if (currentProfile.economy.lifetimeMatter < shape.unlockLifetimeMatter()) {
            return rejected(ProfileMutationRejection.CORE_SHAPE_LOCKED)
        }
        if (shape == currentProfile.loadout.coreShape) return rejected(ProfileMutationRejection.NO_CHANGE)
        return commit(
            currentProfile.copy(loadout = currentProfile.loadout.copy(coreShape = shape)),
        )
    }

    override fun purchaseOrEquipWeapon(id: WeaponId): ProfileMutationResult {
        val unlocked = currentProfile.loadout.unlockedWeapons.toMutableSet()
        var economy = currentProfile.economy
        if (id !in unlocked) {
            val cost = WeaponCatalog.byId(id).permanentUnlockCost.toLong()
            if (economy.matter < cost) return rejected(ProfileMutationRejection.INSUFFICIENT_MATTER)
            economy = economy.copy(matter = economy.matter - cost)
            unlocked += id
        } else if (id == currentProfile.loadout.selectedWeapon) {
            return rejected(ProfileMutationRejection.NO_CHANGE)
        }
        return commit(
            currentProfile.copy(
                economy = economy,
                loadout = PlayerLoadout(
                    coreShape = currentProfile.loadout.coreShape,
                    selectedWeapon = id,
                    unlockedWeapons = unlocked,
                ),
            ),
        )
    }

    override fun advanceRebirth(): ProfileMutationResult {
        val progress = currentProfile.rebirthProgress
        if (
            progress.level >= RebirthProgression.MAX_LEVEL ||
            progress.highestCleared < progress.level
        ) {
            return rejected(ProfileMutationRejection.REBIRTH_UNAVAILABLE)
        }
        return commit(
            currentProfile.copy(
                rebirthProgress = progress.copy(level = progress.level + 1),
            ),
        )
    }

    override fun applyGameplayProgress(update: GameplayProgressUpdate): ProfileMutationResult {
        val clearedLevel = update.clearedRebirthLevel
        if (
            update.bankedMatter < 0L ||
            update.discoveredItemIds.any { it !in 0 until ItemCatalog.ITEM_COUNT } ||
            clearedLevel != null && clearedLevel !in 0..currentProfile.rebirthProgress.level
        ) {
            return rejected(ProfileMutationRejection.INVALID_GAMEPLAY_PROGRESS)
        }

        val economy = if (update.bankedMatter == 0L) {
            currentProfile.economy
        } else {
            PlayerEconomy(
                matter = saturatedAdd(currentProfile.economy.matter, update.bankedMatter),
                lifetimeMatter = saturatedAdd(currentProfile.economy.lifetimeMatter, update.bankedMatter),
            )
        }
        val discoveries = currentProfile.collection.discoveredItemIds.toMutableSet().apply {
            addAll(update.discoveredItemIds)
        }
        val rebirthProgress = if (clearedLevel == null) {
            currentProfile.rebirthProgress
        } else {
            currentProfile.rebirthProgress.copy(
                highestCleared = max(currentProfile.rebirthProgress.highestCleared, clearedLevel),
            )
        }
        val next = currentProfile.copy(
            economy = economy,
            collection = PlayerCollection(discoveries),
            rebirthProgress = rebirthProgress,
        )
        if (next == currentProfile) return rejected(ProfileMutationRejection.NO_CHANGE)
        return commit(next)
    }

    private fun commit(profile: PlayerProfile): ProfileMutationResult {
        currentProfile = profile
        return ProfileMutationResult.Applied(persistCurrent())
    }

    private fun rejected(reason: ProfileMutationRejection): ProfileMutationResult =
        ProfileMutationResult.Rejected(reason)

    private fun persistCurrent(): ProfilePersistResult {
        if (!providerAccepted) {
            return ProfilePersistResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED)
        }
        return runCatching { resource.persist(currentProfile) }.getOrElse {
            ProfilePersistResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED)
        }
    }
}

private fun CoreShape.unlockLifetimeMatter(): Long = when (this) {
    CoreShape.ORB -> 0L
    CoreShape.PRISM -> 25L
    CoreShape.SHARD -> 90L
}

private fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
