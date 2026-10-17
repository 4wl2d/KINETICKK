// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.WeaponMastery

/** The same bounded counts drive spawning and reward previews. */
internal fun MutableGameState.weaponOrbitalCount(): Int {
    val rank = agonyPower()
    // Orbitals grow through the first mastery form and Agony Scepter, not every level (the level is in the power).
    return minOf(8, 2 + if (rank > 0) 1 + rank / 2 else 0)
}

/** Ion Swarm orbitals: the shared count, plus one at its first form. */
internal fun MutableGameState.ionOrbitalCount(): Int =
    minOf(MutableGameState.MAX_WEAPON_ORBITALS, weaponOrbitalCount() + if (hasMasteryForm(1)) 1 else 0)

/** Rift Blades: the shared count, plus one at its first form. */
internal fun MutableGameState.riftBladeCount(): Int =
    minOf(MutableGameState.MAX_WEAPON_ORBITALS, weaponOrbitalCount() + if (hasMasteryForm(1)) 1 else 0)

/**
 * Arc Coil targets: two, a third at its first form, a fourth at its third; a Pentagon's on-beat
 * activation chains two more (at most [MAX_ARC_COIL_TARGETS]).
 */
internal fun MutableGameState.arcCoilTargetCount(): Int =
    minOf(
        MAX_ARC_COIL_TARGETS,
        2 + (if (hasMasteryForm(1)) 1 else 0) + (if (hasMasteryForm(3)) 1 else 0) +
            if (signatureActive() && isOnBeat()) 2 else 0,
    )

internal fun MutableGameState.prismRelayCount(): Int =
    (if (currentWeaponMastery >= WeaponMastery.RESONANT) 2 else 1) + if (agonyPower() > 0) 1 else 0

internal fun MutableGameState.prismRelayBounces(): Int = when (currentWeaponMastery) {
    WeaponMastery.CALIBRATED -> 2
    WeaponMastery.AMPLIFIED -> 3
    WeaponMastery.RESONANT -> 3
    WeaponMastery.ASCENDED -> 4
} + agonyPower()
