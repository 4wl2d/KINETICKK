// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.profile.nucleus.projection

import kinetickk.features.profile.nucleus.domain.CoreShape
import kinetickk.features.profile.nucleus.domain.MetaUpgradeId
import kinetickk.features.profile.nucleus.domain.RebirthProfile
import kinetickk.features.profile.nucleus.domain.RebirthProgression
import kinetickk.features.profile.nucleus.domain.WeaponId
import kinetickk.features.profile.nucleus.protocol.RunSettlementId
import kinetickk.features.profile.nucleus.state.PlayerProfileValues
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet

data class ProfileProjection(
    val matter: Long,
    val lifetimeMatter: Long,
    val metaRanks: ImmutableList<Int>,
    val selectedWeapon: WeaponId,
    val unlockedWeapons: ImmutableSet<WeaponId>,
    val selectedCoreShape: CoreShape,
    val discoveredItemIds: ImmutableSet<Int>,
    val rebirthLevel: Int,
    val highestClearedRebirth: Int,
    val lastAppliedRunSettlementId: RunSettlementId?,
    val runConfiguration: RunConfiguration,
) {
    val canAdvanceRebirth: Boolean
        get() = rebirthLevel < RebirthProgression.MAX_LEVEL &&
            highestClearedRebirth >= rebirthLevel

    fun metaLevel(id: MetaUpgradeId): Int = metaRanks[id.ordinal]

    companion object {
        const val BALL_INSTANCE_ID = "kinetickk.local/Profile/local-player"
        const val PROTOCOL_VERSION = "1.0.0"
        const val STATE_SCHEMA_VERSION = 1
    }
}

/** Field-minimized, immutable input captured by Game for one run lifecycle. */
data class RunConfiguration(
    val selectedWeapon: WeaponId,
    val selectedCoreShape: CoreShape,
    val unlockedWeaponCount: Int,
    val metaRanks: ImmutableList<Int>,
    val lifetimeMatter: Long,
    val rebirthLevel: Int,
    val rebirthProfile: RebirthProfile,
    val nextRebirthProfile: RebirthProfile,
)

internal fun PlayerProfileValues.toProjection(
    lastAppliedRunSettlementId: RunSettlementId?,
): ProfileProjection = ProfileProjection(
    matter = matter,
    lifetimeMatter = lifetimeMatter,
    metaRanks = metaRanks,
    selectedWeapon = selectedWeapon,
    unlockedWeapons = unlockedWeapons,
    selectedCoreShape = selectedCoreShape,
    discoveredItemIds = discoveredItemIds,
    rebirthLevel = rebirthLevel,
    highestClearedRebirth = highestClearedRebirth,
    lastAppliedRunSettlementId = lastAppliedRunSettlementId,
    runConfiguration = toRunConfiguration(),
)

internal fun PlayerProfileValues.toRunConfiguration(): RunConfiguration = RunConfiguration(
    selectedWeapon = selectedWeapon,
    selectedCoreShape = selectedCoreShape,
    unlockedWeaponCount = unlockedWeapons.size,
    metaRanks = metaRanks,
    lifetimeMatter = lifetimeMatter,
    rebirthLevel = rebirthLevel,
    rebirthProfile = RebirthProgression.profile(rebirthLevel),
    nextRebirthProfile = RebirthProgression.profile(rebirthLevel + 1),
)
