// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy

/*
 * World obstacle queries shared by spawning, line of sight, character movement and enemy slams.
 * The world holds no obstacles yet, so every query answers "nothing in the way" and slams deal no
 * damage. Callers can depend on these contracts now; the obstacle field fills them in later.
 */

/** True when a circle of [radius] at ([x], [y]) would overlap an obstacle, so a spawn there must move. */
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
internal fun MutableGameState.isSpawnPointBlocked(x: Float, y: Float, radius: Float): Boolean = false

/**
 * True when a circle of [radius] swept from ([x0], [y0]) to ([x1], [y1]) would touch an obstacle;
 * blinks stop short of it and hostile sight lines and shots are blocked by it.
 */
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
internal fun MutableGameState.obstacleBlocksSegment(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Boolean = false

/**
 * Applies slam damage to [enemy] after it struck an obstacle or another enemy at [normalSpeed] along
 * the contact normal, and fires the SLAM trigger with it as the target. No-op while the world has no
 * obstacles.
 */
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
internal fun MutableGameState.applySlamDamage(enemy: Enemy, normalSpeed: Float) {
}
