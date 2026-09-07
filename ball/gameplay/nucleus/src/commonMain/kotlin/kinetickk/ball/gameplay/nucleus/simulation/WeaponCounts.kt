// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponMastery

/** The same bounded counts drive spawning and reward previews. */
internal fun MutableGameState.weaponOrbitalCount(): Int {
    val rank = relicRank(RelicId.AGONY_SCEPTER)
    return minOf(8, 2 + (weaponLevel - 1) / 3 + if (rank > 0) 1 + rank / 2 else 0)
}

internal fun MutableGameState.arcCoilTargetCount(): Int = minOf(MAX_ARC_COIL_TARGETS, 3 + weaponLevel / 3)

internal fun MutableGameState.prismRelayCount(): Int =
    (if (currentWeaponMastery >= WeaponMastery.RESONANT) 2 else 1) + if (relicRank(RelicId.AGONY_SCEPTER) > 0) 1 else 0

internal fun MutableGameState.prismRelayBounces(): Int = when (currentWeaponMastery) {
    WeaponMastery.CALIBRATED -> 2
    WeaponMastery.AMPLIFIED -> 3
    WeaponMastery.RESONANT -> 4
    WeaponMastery.ASCENDED -> 6
} + relicRank(RelicId.AGONY_SCEPTER)
