// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileLoadRejection
import kinetickk.ball.profile.api.ProfileLoadResult
import kinetickk.ball.profile.api.RebirthProgress

/** Bounds and defensively recopies a profile supplied by an untrusted resource. */
fun quarantineBootstrapProfile(
    profile: PlayerProfile,
    policy: ProfilePolicySnapshot,
): ProfileLoadResult {
    if (
        profile.loadout.unlockedWeapons.size > policy.weapons.size ||
        profile.labProgress.ranks.size > policy.metaUpgrades.size ||
        profile.collection.discoveredItemIds.size > policy.itemCount
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
    val rebirthLevel = profile.rebirthProgress.level.coerceIn(
        policy.rebirth.minimumLevel,
        policy.rebirth.maximumLevel,
    )
    val acceptedWeapons = policy.weapons.mapTo(mutableSetOf()) { definition -> definition.id }
    val unlockedWeapons = profile.loadout.unlockedWeapons
        .filterTo(mutableSetOf()) { it in acceptedWeapons }
        .apply { add(policy.weapons.first().id) }
    val normalizedMetaLevels = List(policy.metaUpgrades.size) { index ->
        profile.labProgress.ranks.getOrNull(index)
            ?.coerceIn(0, policy.metaUpgrades[index].maxRanks)
            ?: 0
    }
    val discoveries = profile.collection.discoveredItemIds
        .filterTo(mutableSetOf(), policy::containsItem)

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
