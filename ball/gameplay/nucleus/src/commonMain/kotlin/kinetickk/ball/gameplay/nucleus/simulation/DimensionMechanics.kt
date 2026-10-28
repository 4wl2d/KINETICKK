// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kotlin.math.max
import kotlin.math.min

/*
 * What a layer does to the run while the Core is in it: the pace a dimension measures flight against, the Zenith's
 * Grace (lighter drag and mending halo rings), the Nadir's Heat (slow flight scorches the Core, Matter burns twice as
 * bright), the Archive's Desync (hostile time follows the Core's speed) and the director's rate and cap. Every value
 * comes from the current layer's content definition; realspace's definition changes nothing, so every function here
 * is the identity there.
 */

/**
 * The speed a dimension measures flight against: the steady speed of the pace reach along the viewport's long axis
 * with the base movement stats, never above the pace ceiling. It follows the production steady model, so a retuned
 * flight law moves it too.
 */
internal fun MutableGameState.dimensionPace(): Float {
    val policy = content.dimensions
    return min(policy.paceCeiling, steadyCursorSpeed(policy.paceReach))
}

/**
 * The current layer's change to the Core's drag: the Zenith's Grace multiplies it, but never below the layer's drag
 * floor, and never raises a drag already under that floor (Perpetual Vector, Superconduct).
 */
internal fun MutableGameState.layerDrag(drag: Float): Float {
    val physics = content.dimensions.definition(worldLayer).physics
    return max(drag * physics.dragMultiplier, min(drag, physics.dragFloor))
}

/** The current layer's scale of the director's spawn rate. */
internal fun MutableGameState.layerSpawnRateMultiplier(): Float =
    content.dimensions.definition(worldLayer).spawnRateMultiplier

/** The current layer's scale of the ordinary enemy cap. */
internal fun MutableGameState.layerOrdinaryCapMultiplier(): Float =
    content.dimensions.definition(worldLayer).ordinaryCapMultiplier

/**
 * World time scale for [enemy] (null: hostile projectiles) in the current layer. Under the Archive's Desync it is
 * the Core's speed over the pace, kept between the layer's bounds, so time never stops and runs normally at pace; a
 * boss-class enemy never runs slower than the boss floor. The fixed step and the run clock are untouched.
 */
internal fun MutableGameState.layerTimeScale(enemy: Enemy?): Float {
    val physics = content.dimensions.definition(worldLayer).physics
    if (physics.desyncMinimumScale == 1f && physics.desyncMaximumScale == 1f) return 1f
    val pace = dimensionPace()
    val scale = (if (pace > 0f) speed / pace else 1f).coerceIn(physics.desyncMinimumScale, physics.desyncMaximumScale)
    return if (enemy != null && enemy.isBossClass) max(scale, physics.desyncBossFloor) else scale
}

/** The current layer's scale of Matter earned (the Nadir doubles it). */
internal fun MutableGameState.layerMatterMultiplier(): Float =
    content.dimensions.definition(worldLayer).physics.matterMultiplier
