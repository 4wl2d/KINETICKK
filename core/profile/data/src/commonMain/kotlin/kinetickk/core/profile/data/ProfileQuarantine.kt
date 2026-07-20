// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.data

import kinetickk.core.content.ItemCatalog
import kinetickk.core.content.MetaUpgradeCatalog
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.content.RebirthProgression
import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.PlayerCollection
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.core.profile.api.PlayerLoadout
import kinetickk.core.profile.api.PlayerProfile
import kinetickk.core.profile.api.ProfileLoadRejection
import kinetickk.core.profile.api.ProfileLoadResult
import kinetickk.core.profile.api.RebirthProgress

/** Bounds and defensively recopies a profile supplied by an untrusted resource. */
fun quarantineBootstrapProfile(profile: PlayerProfile): ProfileLoadResult {
    if (
        profile.loadout.unlockedWeapons.size > WeaponId.entries.size ||
        profile.labProgress.ranks.size > MetaUpgradeId.entries.size ||
        profile.collection.discoveredItemIds.size > ItemCatalog.ITEM_COUNT
    ) {
        return ProfileLoadResult.Rejected(ProfileLoadRejection.BOOTSTRAP_COLLECTION_LIMIT_EXCEEDED)
    }

    val rawPreferences = profile.preferences
    if (
        !rawPreferences.masterVolume.isFinite() ||
        !rawPreferences.simulationSpeed.isFinite() ||
        !rawPreferences.textScale.isFinite()
    ) {
        return ProfileLoadResult.Rejected(ProfileLoadRejection.BOOTSTRAP_NON_FINITE_NUMBER)
    }

    val matter = profile.economy.matter.coerceAtLeast(0L)
    val rebirthLevel = profile.rebirthProgress.level.coerceIn(0, RebirthProgression.MAX_LEVEL)
    val unlockedWeapons = profile.loadout.unlockedWeapons
        .filterTo(mutableSetOf()) { it in WeaponId.entries }
        .apply { add(WeaponId.FLUX_WAKE) }
    val normalizedMetaLevels = List(MetaUpgradeId.entries.size) { index ->
        profile.labProgress.ranks.getOrNull(index)
            ?.coerceIn(0, MetaUpgradeCatalog.all[index].maxRanks)
            ?: 0
    }
    val discoveries = profile.collection.discoveredItemIds
        .filterTo(mutableSetOf()) { it in 0 until ItemCatalog.ITEM_COUNT }

    return ProfileLoadResult.Loaded(
        PlayerProfile(
            preferences = rawPreferences.normalized(),
            economy = PlayerEconomy(
                matter = matter,
                lifetimeMatter = profile.economy.lifetimeMatter.coerceAtLeast(matter),
            ),
            loadout = PlayerLoadout(
                coreShape = profile.loadout.coreShape,
                selectedWeapon = profile.loadout.selectedWeapon,
                unlockedWeapons = unlockedWeapons,
            ),
            labProgress = LabProgress(normalizedMetaLevels),
            collection = PlayerCollection(discoveries),
            rebirthProgress = RebirthProgress(
                level = rebirthLevel,
                highestCleared = profile.rebirthProgress.highestCleared.coerceIn(-1, rebirthLevel),
            ),
        ),
    )
}
