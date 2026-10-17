// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.characterization

import kinetickk.ball.content.api.WeaponId

/**
 * Weapons typed in content whose behavior has not landed yet: they equip safely and idle. Tests that
 * sweep every weapon assert these stay idle, so each one must leave this set the moment it acts.
 */
internal val PendingWeaponBehaviors: Set<WeaponId> = emptySet()
