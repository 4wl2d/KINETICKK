// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.world

import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*

// ---------------------------------------------------------------------------------------------
// Queries.

/** Clear space a spawn keeps from an obstacle surface. */
internal const val SPAWN_OBSTACLE_CLEARANCE: Float = 24f

/**
 * True when a circle of [radius] at ([x], [y]) would overlap an obstacle's body or its hazard reach (a mine's arming
 * reach, a vent's plume), so a spawn there must move.
 */
internal fun MutableGameState.isSpawnPointBlocked(x: Float, y: Float, radius: Float): Boolean {
    val reach = radius + SPAWN_OBSTACLE_CLEARANCE
    forEachObstacleNear(x, y, reach + content.obstacles.kinds.hazardMargin()) { _, obstacle ->
        val keepOut = max(obstacle.radius, obstacleHazardReach(obstacle)) + reach
        if (distanceSquared(x, y, obstacle.x, obstacle.y) < square(keepOut)) return true
    }
    return wallBlocksPoint(x, y, radius)
}

/**
 * True when a circle of [radius] swept from ([x0], [y0]) to ([x1], [y1]) would touch a solid obstacle;
 * blinks stop short of it and hostile sight lines and shots are blocked by it.
 */
internal fun MutableGameState.obstacleBlocksSegment(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Boolean {
    val centerX = (x0 + x1) * 0.5f
    val centerY = (y0 + y1) * 0.5f
    val reach = length(x1 - x0, y1 - y0) * 0.5f + radius
    val kinds = content.obstacles.kinds
    forEachObstacleNear(centerX, centerY, reach) { _, obstacle ->
        if (obstacle.solidAt(elapsed, kinds) &&
            sweepEntry(x0, y0, x1, y1, obstacle.x, obstacle.y, obstacle.radius + radius) >= 0f
        ) return true
    }
    return wallBlocksSegment(x0, y0, x1, y1, radius)
}

/**
 * Applies slam damage to [enemy] after it struck an obstacle or another enemy at [normalSpeed] along
 * the contact normal, and fires the SLAM trigger with it as the target. A [polarity] slam (an enemy the
 * Dipole threw, or the one it struck) takes the Dipole's slam speed and damage instead of the obstacle
 * slam's. Both rules share the enemy's slam cooldown, so one contact deals one slam and fires SLAM once,
 * whichever pass meets it first. Below the slam speed, within the cooldown, or on a shifting enemy (which no
 * damage reaches), nothing happens. An obstacle slam meets the enemy's slam frame. [obstacle] is the kind struck, for
 * SLAM conditions that name one. Returns true when the slam landed.
 */
internal fun MutableGameState.applySlamDamage(
    enemy: Enemy,
    normalSpeed: Float,
    polarity: Boolean = isThrownByPolarity(enemy),
    obstacle: kinetickk.ball.content.api.ObstacleKind? = null,
): Boolean {
    val tuning = content.obstacles
    val slamSpeed = if (polarity) SLAM_MIN_SPEED else tuning.slamSpeed
    if (normalSpeed < slamSpeed || !enemy.alive || enemy.slamReadyAt > elapsed || isShifting(enemy)) return false
    enemy.slamReadyAt = elapsed + tuning.slamCooldownSeconds
    val damage = if (polarity) {
        polaritySlam(enemy, normalSpeed)
    } else {
        val amount = (normalSpeed - tuning.slamDamageOffset) * tuning.slamDamagePerSpeed * runScale() *
            enemy.type.profile.slamTaken
        dealProcDamage(enemy, amount)
        amount
    }
    burst(enemy.x, enemy.y, 6, 3)
    fireClauses(EffectTrigger.SLAM, enemy, damage, obstacle = obstacle)
    onSkillSlam(enemy)
    return true
}

/** Marks [enemy] as thrown by the player, so hitting another enemy hard enough slams both. */
internal fun MutableGameState.markThrown(enemy: Enemy) {
    enemy.thrownUntil = elapsed + content.obstacles.thrownSeconds
}

/**
 * Visits every obstacle whose window cell lies within [reach] (plus the largest obstacle radius) of
 * ([x], [y]). Walks only the chunk cells the query touches; allocation-free.
 */
internal inline fun MutableGameState.forEachObstacleNear(
    x: Float,
    y: Float,
    reach: Float,
    action: (index: Int, obstacle: Obstacle) -> Unit,
) {
    val field = obstacleField
    if (field.size == 0) return
    val tuning = content.obstacles
    val chunk = tuning.chunkSize.toDouble()
    val margin = (reach + max(tuning.asteroidMaximumRadius, tuning.bumperRadius)).toDouble()
    val absoluteX = worldOriginX + x
    val absoluteY = worldOriginY + y
    val firstColumn = max(0, floor((absoluteX - margin) / chunk).toInt() - field.minimumChunkX)
    val lastColumn = min(field.span - 1, floor((absoluteX + margin) / chunk).toInt() - field.minimumChunkX)
    val firstRow = max(0, floor((absoluteY - margin) / chunk).toInt() - field.minimumChunkY)
    val lastRow = min(field.span - 1, floor((absoluteY + margin) / chunk).toInt() - field.minimumChunkY)
    if (firstColumn > lastColumn || firstRow > lastRow) return
    val obstacles = field.obstacles
    for (row in firstRow..lastRow) {
        for (column in firstColumn..lastColumn) {
            val cell = row * field.span + column
            for (index in field.cellBegin(cell) until field.cellEnd(cell)) action(index, obstacles[index])
        }
    }
}

/**
 * Fraction (0..1) of the segment ([x0], [y0]) → ([x1], [y1]) at which a point first comes within
 * [radius] of ([cx], [cy]); 0 when it starts inside, −1 when it never does.
 */
internal fun sweepEntry(x0: Float, y0: Float, x1: Float, y1: Float, cx: Float, cy: Float, radius: Float): Float {
    val fx = (x0 - cx).toDouble()
    val fy = (y0 - cy).toDouble()
    val r = radius.toDouble()
    val c = fx * fx + fy * fy - r * r
    if (c <= 0.0) return 0f
    val dx = (x1 - x0).toDouble()
    val dy = (y1 - y0).toDouble()
    val a = dx * dx + dy * dy
    if (a == 0.0) return -1f
    val b = 2.0 * (fx * dx + fy * dy)
    if (b >= 0.0) return -1f
    val discriminant = b * b - 4.0 * a * c
    if (discriminant < 0.0) return -1f
    val t = (-b - sqrt(discriminant)) / (2.0 * a)
    return if (t in 0.0..1.0) t.toFloat() else -1f
}

/**
 * Fraction (0..1) of a shot's or weapon entity's step ([x0], [y0]) → ([x1], [y1]) at which a circle of [radius]
 * runs into an obstacle of [obstacleRadius] at ([cx], [cy]), or −1. A circle that starts overlapping it is stopped
 * only when it moves inward: a disc or shot wider than the Core, launched from a Core resting on a rock, flies free
 * when thrown away from it.
 */
internal fun obstacleStepEntry(x0: Float, y0: Float, x1: Float, y1: Float, cx: Float, cy: Float, obstacleRadius: Float, radius: Float): Float {
    val reach = obstacleRadius + radius
    val fx = x0 - cx
    val fy = y0 - cy
    if (fx * fx + fy * fy <= reach * reach) return if (fx * (x1 - x0) + fy * (y1 - y0) < 0f) 0f else -1f
    return sweepEntry(x0, y0, x1, y1, cx, cy, reach)
}
