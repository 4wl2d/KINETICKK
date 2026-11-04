// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.world

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kotlin.math.max
import kotlin.math.sqrt
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*

// ---------------------------------------------------------------------------------------------
// Enemies.

/**
 * After enemies move: pushes each out of any solid obstacle (its inward velocity is removed, and a hard
 * contact slams it unless the obstacle phases), turns self-propelled enemies along the surface before they
 * reach it, shatters the first crystal a landed slam struck this step (the others wait for their next
 * contact), then lets thrown enemies slam into each other (bounded per step).
 */
internal fun MutableGameState.resolveEnemyObstacles() {
    val tuning = content.obstacles
    if (obstacleField.size > 0) {
        // The lookup adds the largest obstacle radius once; avoidance scales the combined radii.
        val largest = max(tuning.asteroidMaximumRadius, tuning.bumperRadius)
        val kinds = tuning.kinds
        var breakId = NO_OBSTACLE_ID
        var breakSpeed = 0f
        for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            if (!enemy.alive || isPhasedPhantom(enemy)) continue
            val avoidReach = (enemy.radius + largest) * tuning.avoidanceRange - largest
            forEachObstacleNear(enemy.x, enemy.y, avoidReach) { _, obstacle ->
                if (!obstacle.solidAt(elapsed, kinds)) return@forEachObstacleNear
                val slam = resolveEnemyObstacle(enemy, obstacle)
                if (slam > 0f && breakId == NO_OBSTACLE_ID && obstacle.kind == ObstacleKind.CRYSTAL) {
                    breakId = obstacle.id
                    breakSpeed = slam
                }
            }
        }
        if (breakId != NO_OBSTACLE_ID) shatterCrystalStruckByEnemy(breakId, breakSpeed)
    }
    resolveEnemyWalls()
    resolveThrownEnemySlams()
}

/** Pushes [enemy] out of [obstacle] or bends its approach; returns the speed of a slam that landed, else 0. */
private fun MutableGameState.resolveEnemyObstacle(enemy: Enemy, obstacle: Obstacle): Float {
    val tuning = content.obstacles
    val combined = obstacle.radius + enemy.radius
    val dx = enemy.x - obstacle.x
    val dy = enemy.y - obstacle.y
    val distanceSquared = dx * dx + dy * dy
    val avoidRadius = combined * tuning.avoidanceRange
    if (distanceSquared >= avoidRadius * avoidRadius) return 0f
    val distance = sqrt(distanceSquared)
    val nx = if (distance > 0.001f) dx / distance else 1f
    val ny = if (distance > 0.001f) dy / distance else 0f
    val inward = -(enemy.vx * nx + enemy.vy * ny)
    if (distance < combined) {
        enemy.x = obstacle.x + nx * combined
        enemy.y = obstacle.y + ny * combined
        if (inward > 0f) {
            enemy.vx += inward * nx
            enemy.vy += inward * ny
            if (!obstacle.kind.phased && applySlamDamage(enemy, inward, obstacle = obstacle.kind)) {
                endPolarityThrow(enemy)
                return inward
            }
        }
        return 0f
    }
    // Slide: self-propelled enemies bend their approach along the surface; thrown ones and strikes do not.
    if (inward <= 0f || !canAct(enemy) || enemy.thrownUntil > elapsed || isThrownByPolarity(enemy) ||
        enemy.mode == EnemyBehavior.STRIKE
    ) return 0f
    val closeness = (1f - (distance - combined) / (avoidRadius - combined)).coerceIn(0f, 1f)
    val removed = inward * closeness
    var tangentX = -ny
    var tangentY = nx
    if (enemy.vx * tangentX + enemy.vy * tangentY < 0f) {
        tangentX = -tangentX
        tangentY = -tangentY
    }
    enemy.vx += removed * nx + removed * tangentX
    enemy.vy += removed * ny + removed * tangentY
    return 0f
}

/** Thrown enemies hitting others at the slam speed slam both; at most a few per step. */
private fun MutableGameState.resolveThrownEnemySlams() {
    val tuning = content.obstacles
    var slams = 0
    var thrownVisited = 0
    for (thrownIndex in enemies.indices) {
        if (slams >= tuning.maximumEnemySlamsPerStep || thrownVisited >= MAX_THROWN_CHECKS_PER_STEP) return
        val thrown = enemies[thrownIndex]
        if (thrown.thrownUntil <= elapsed || !thrown.alive || isPhasedPhantom(thrown)) continue
        thrownVisited++
        for (otherIndex in enemies.indices) {
            if (otherIndex == thrownIndex) continue
            val other = enemies[otherIndex]
            if (!other.alive) continue
            val dx = thrown.x - other.x
            val dy = thrown.y - other.y
            val combined = thrown.radius + other.radius
            val distanceSquared = dx * dx + dy * dy
            // The overlap test rejects almost every pair, so it runs before the phase lookup.
            if (distanceSquared >= combined * combined || isPhasedPhantom(other)) continue
            val distance = sqrt(distanceSquared)
            val nx = if (distance > 0.001f) dx / distance else 1f
            val ny = if (distance > 0.001f) dy / distance else 0f
            val closing = -((thrown.vx - other.vx) * nx + (thrown.vy - other.vy) * ny)
            if (closing < tuning.slamSpeed) continue
            // The two meet head-on: the closing speed is shared out and both take the slam.
            thrown.vx += closing * 0.5f * nx
            thrown.vy += closing * 0.5f * ny
            other.vx -= closing * 0.5f * nx
            other.vy -= closing * 0.5f * ny
            applySlamDamage(thrown, closing)
            applySlamDamage(other, closing)
            // Repulsor: an enemy Magnetize hurled charges whatever it slams.
            if (other.alive && repulsorCharges(thrown)) applyStatus(other, kinetickk.ball.content.api.StatusElement.ION, 1, 1)
            slams++
            if (slams >= tuning.maximumEnemySlamsPerStep) return
        }
    }
}

private const val MAX_THROWN_CHECKS_PER_STEP: Int = 24
