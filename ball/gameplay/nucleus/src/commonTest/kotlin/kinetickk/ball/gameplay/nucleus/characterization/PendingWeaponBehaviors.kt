// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.characterization

import kinetickk.ball.content.api.WeaponId

/**
 * Weapons typed in content whose behavior has not landed yet: they equip safely and idle. Tests that
 * sweep every weapon assert these stay idle, so each one must leave this set the moment it acts.
 */
internal val PendingWeaponBehaviors: Set<WeaponId> = setOf(
    WeaponId.GYRE_DISC, WeaponId.SCATTER_BLOOM, WeaponId.TETHER_ARC, WeaponId.SENTRY_GRID,
    WeaponId.HUNTER_SALVO, WeaponId.CONCUSSION_ENGINE, WeaponId.GLACIER_BELL, WeaponId.EVENT_LENS,
    WeaponId.SOLAR_SWEEP, WeaponId.AEGIS_SHARDS, WeaponId.HOLLOW_RIFT, WeaponId.RESONANCE_DRUM,
)
