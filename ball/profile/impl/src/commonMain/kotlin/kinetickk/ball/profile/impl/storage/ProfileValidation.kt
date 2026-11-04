// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl.storage

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.FRAME_RATE_CAP_OPTIONS
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.REFRESH_RATE_AUTO
import kinetickk.ball.profile.api.REFRESH_RATE_RANGE
import kinetickk.ball.profile.api.isWithinRebirthBounds
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kotlin.math.roundToInt

internal fun validateProfile(profile: PlayerProfile) {
    val achievements = profile.characterAchievements
    rejectUnless(
        achievements.eliteKills >= 0L && achievements.dashHits >= 0L &&
            achievements.completedOrbits >= 0L && achievements.architectVictories >= 0L &&
            achievements.totalKills >= 0L && achievements.ricochets >= 0L && achievements.dataPickups >= 0L &&
            achievements.victoriousCharacters.size <= CoreShape.entries.size &&
            achievements.victoriousCharacters.size.toLong() <= achievements.architectVictories,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    val preferences = profile.preferences
    rejectUnless(
        preferences.masterVolume.isFinite() && preferences.masterVolume in 0f..1f &&
            preferences.simulationSpeed.isFinite() && preferences.simulationSpeed in 0.75f..2f &&
            preferences.simulationSpeed in SIMULATION_SPEED_OPTIONS &&
            preferences.textScale.isFinite() && preferences.textScale in 1f..1.75f &&
            preferences.damageNumberTierThreshold in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS &&
            preferences.frameRateCap in FRAME_RATE_CAP_OPTIONS &&
            (preferences.refreshRateHz == REFRESH_RATE_AUTO || preferences.refreshRateHz in REFRESH_RATE_RANGE) &&
            preferences.interfaceScalePercent in INTERFACE_SCALE_OPTIONS,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.economy.matter >= 0L &&
            profile.economy.lifetimeMatter >= profile.economy.matter,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )

    rejectUnless(
        profile.loadout.unlockedWeapons.isNotEmpty() &&
            profile.loadout.unlockedWeapons.size <= ContentBounds.MAX_WEAPONS,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.loadout.selectedWeapon in profile.loadout.unlockedWeapons,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )

    rejectUnless(
        profile.matrix.ranks.size == MatrixNodeId.entries.size &&
            profile.matrix.ranks.size <= ContentBounds.MAX_MATRIX_NODES,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    rejectUnless(
        profile.matrix.ranks.all { it in 0..ContentBounds.MAX_MATRIX_NODE_RANKS },
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )

    rejectUnless(
        profile.collection.discoveredItemIds.size <= ContentBounds.MAX_ITEMS &&
            profile.collection.discoveredItemIds.all { it >= 0 },
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.collection.discoveredItemIds.containsAll(profile.collection.newItemIds) &&
            profile.collection.discoveredRelicIds.containsAll(profile.collection.newRelicIds) &&
            profile.collection.discoveredRelicIds.size <= ContentBounds.MAX_RELICS,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    val heirlooms = profile.collection.heirloomRelicIds
    rejectUnless(heirlooms.size <= MAX_HEIRLOOM_RELICS, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    rejectUnless(heirlooms.toSet().size == heirlooms.size, ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
    val recent = profile.collection.recentItemIds
    rejectUnless(
        recent.size <= MAX_RECENT_ITEM_IDS && recent.all { it in 0 until ContentBounds.MAX_ITEMS },
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.rebirthProgress.isWithinRebirthBounds(ContentBounds.MIN_REBIRTH_LEVEL, ContentBounds.MAX_REBIRTH_LEVEL),
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    // An Ascendant clear, and every recorded clear, is an Architect victory with that character.
    rejectUnless(
        achievements.victoriousCharacters.containsAll(profile.rebirthProgress.ascendedCharacters) &&
            CoreShape.entries.all { profile.rebirthProgress.characterClears[it.ordinal] < 0 || it in achievements.victoriousCharacters },
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    val upgrades = profile.upgrades
    rejectUnless(
        upgrades.characterLevels.size == CoreShape.entries.size && upgrades.weaponLevels.size == WeaponId.entries.size,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    rejectUnless(
        upgrades.characterLevels.all { it in 0..ContentBounds.MAX_UPGRADE_LEVEL } &&
            upgrades.weaponLevels.all { it in 0..ContentBounds.MAX_UPGRADE_LEVEL },
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
}

internal fun <T : Comparable<T>> List<T>.isStrictlySorted(): Boolean {
    for (index in 1 until size) {
        if (this[index - 1] >= this[index]) return false
    }
    return true
}

internal fun Float.toPercent(): Int {
    rejectUnless(isFinite(), ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    return (this * 100f).roundToInt()
}

internal fun String.parseCanonicalNonNegativeLong(): Long {
    if (isEmpty() || this != "0" && (first() == '0' || any { it !in '0'..'9' })) {
        reject(ProfileSnapshotRejection.INVALID_DECIMAL)
    }
    if (this == "0") return 0L
    if (any { it !in '0'..'9' }) reject(ProfileSnapshotRejection.INVALID_DECIMAL)
    val value = toLongOrNull() ?: reject(ProfileSnapshotRejection.INVALID_DECIMAL)
    rejectUnless(value >= 0L && value.toString() == this, ProfileSnapshotRejection.INVALID_DECIMAL)
    return value
}
