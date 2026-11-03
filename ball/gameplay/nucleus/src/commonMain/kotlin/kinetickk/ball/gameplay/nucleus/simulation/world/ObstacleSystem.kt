// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.world

import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.gameplay.nucleus.model.CircuitBeacons
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kotlin.math.max
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*

/*
 * World obstacles. The field is generated per chunk from a hash of the run seed and the absolute
 * chunk, so the same run always meets the same rocks, and the window of chunks around the Core is
 * regenerated only when the Core changes chunk. Absolute positions are run-local positions plus the
 * accumulated rebase origin.
 *
 * Collisions (every query reads the kind's properties, see ObstacleKindSystem.kt):
 * - the Core sweeps against solid obstacles right after it moves; asteroids hurt above the damage-start
 *   speed and kill above the lethal speed, bumpers throw it back, unstable asteroids explode and crystals
 *   shatter;
 * - enemies are pushed out of solid obstacles, slide around them and slam into them when thrown (a slam
 *   can shatter a crystal);
 * - asteroids and the other shot-stopping kinds stop player and hostile shots; a player shot sets off a
 *   derelict mine it runs into.
 */

// ---------------------------------------------------------------------------------------------
// Collision speeds and the solid response live in ObstacleImpact.kt (the steady model in CoreSteering.kt).

/** World distance from the Core to the minimap edge: widened by large viewports, halved by Blackout. */
internal fun MutableGameState.minimapRange(): Float {
    val tuning = content.obstacles
    val halfDiagonal = length(screenWidth, screenHeight) * 0.5f
    val range = max(tuning.minimapRange, tuning.minimapViewportFactor * halfDiagonal)
    return if (runMutatorActive(RebirthMutator.BLACKOUT)) range * tuning.blackoutMinimapScale else range
}

// ---------------------------------------------------------------------------------------------
// Shots.

/**
 * Shot-stopping obstacles (asteroids, and a phase shard only while materialised) stop player and hostile shots: a
 * shot whose step crosses one is spent (removed after the collision passes). Bumpers and the kinds with no solid
 * body let shots through; a player shot running into a derelict mine sets it off on the quick fuse. Mine
 * projectiles never move.
 */
internal fun MutableGameState.blockProjectilesAtObstacles() {
    if (obstacleField.size == 0) return
    val kinds = content.obstacles.kinds
    for (projectileIndex in projectiles.indices) {
        val projectile = projectiles[projectileIndex]
        if (projectile.spent || projectile.kind == ProjectileKind.MINE) continue
        val x0 = projectile.previousX
        val y0 = projectile.previousY
        val x1 = projectile.x
        val y1 = projectile.y
        val reach = length(x1 - x0, y1 - y0) * 0.5f + projectile.radius
        forEachObstacleNear((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, reach) { index, obstacle ->
            if (!projectile.hostile) fuseMineCrossedByShot(index, obstacle, x0, y0, x1, y1, projectile.radius)
            if (!obstacle.stopsShotsAt(elapsed, kinds)) return@forEachObstacleNear
            val t = obstacleStepEntry(x0, y0, x1, y1, obstacle.x, obstacle.y, obstacle.radius, projectile.radius)
            if (t >= 0f) {
                projectile.life = 0f
                burst(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, 3, if (projectile.hostile) 4 else projectile.colorIndex)
                return@forEachObstacleNear
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// World placement.

/**
 * Moves a site at ([x], [y]) outward from the Core in [step]s until a circle of [radius] there is clear
 * of obstacles (at most [attempts] moves), then keeps it outside structure footprints. The bounded search may still
 * meet an obstacle, but never places the site inside a room. Used for rare placements only.
 */
internal fun MutableGameState.nudgedClearOfObstacles(
    x: Float,
    y: Float,
    radius: Float,
    step: Float = 80f,
    attempts: Int = 10,
): Pair<Float, Float> {
    val ejected = structureEjected(x, y, radius)
    var siteX = ejected.x
    var siteY = ejected.y
    var moves = 0
    while (moves < attempts && isSpawnPointBlocked(siteX, siteY, radius)) {
        val dx = siteX - coreX
        val dy = siteY - coreY
        val distance = max(1f, length(dx, dy))
        siteX += dx / distance * step
        siteY += dy / distance * step
        moves++
    }
    val final = structureEjected(siteX, siteY, radius)
    return final.x to final.y
}

/** Room an anomaly needs: its orbit ring around the centre and the two circuit beacons. */
private const val POINT_SITE_RADIUS: Float = 210f
private const val POINT_BEACON_CLEARANCE: Float = 72f
private const val POINT_SITE_STEP: Float = 90f
private const val POINT_SITE_ATTEMPTS: Int = 12

/** True when an obstacle overlaps an anomaly at ([x], [y]), its ring or its beacons. */
internal fun MutableGameState.pointSiteBlocked(x: Float, y: Float): Boolean =
    isSpawnPointBlocked(x, y, POINT_SITE_RADIUS) ||
        isSpawnPointBlocked(x + CircuitBeacons[0].dx, y + CircuitBeacons[0].dy, POINT_BEACON_CLEARANCE) ||
        isSpawnPointBlocked(x + CircuitBeacons[1].dx, y + CircuitBeacons[1].dy, POINT_BEACON_CLEARANCE)

/** Room a structure keeps clear around an anomaly: its ring and both beacons. */
private const val POINT_SITE_EJECT_RADIUS: Float = 320f

/** Searches outward for obstacle clearance (bounded), then keeps the anomaly outside structure footprints. */
internal fun MutableGameState.clearPointOfInterestSite(x: Float, y: Float): Pair<Float, Float> {
    val ejected = structureEjected(x, y, POINT_SITE_EJECT_RADIUS)
    var siteX = ejected.x
    var siteY = ejected.y
    var moves = 0
    while (moves < POINT_SITE_ATTEMPTS && pointSiteBlocked(siteX, siteY)) {
        val dx = siteX - coreX
        val dy = siteY - coreY
        val distance = max(1f, length(dx, dy))
        siteX += dx / distance * POINT_SITE_STEP
        siteY += dy / distance * POINT_SITE_STEP
        moves++
    }
    val final = structureEjected(siteX, siteY, POINT_SITE_EJECT_RADIUS)
    return final.x to final.y
}
