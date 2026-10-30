// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Wall
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Structure walls in the shared world passes. Walls block exactly what asteroids block, through the same rules:
 * - the Core sweeps against walls in its obstacle pass (the earliest contact across circles and capsules wins, a
 *   circle on a tie), rests just off the wall and meets the solid-impact response keyed by its structure, so every
 *   wall of one structure shares one hit cooldown; a last guard pushes it out of any wall;
 * - enemies are pushed out, slide along walls and slam into them when thrown; a dungeon garrison stays in its room;
 * - player and hostile shots, and the weapon entities that stop at rocks, stop at walls;
 * - spawns, sight lines, blinks and landings see walls; placements land outside structure footprints.
 * Solid walls and sealed doors block; open doors let everything through. The window, the geometry and the broad
 * phase live in StructureFieldSystem.kt and StructureQueries.kt.
 */

/**
 * Earliest wall a circle of [radius] swept from ([x0], [y0]) to ([x1], [y1]) enters strictly before the fraction
 * [before]: the wall index in the high 32 bits and the entry fraction's float bits in the low 32 bits, or −1.
 */
internal fun MutableGameState.earliestWallEntry(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float, before: Float): Long {
    if (structureField.structures.isEmpty()) return -1L
    var bestIndex = -1
    var bestT = before
    val reach = length(x1 - x0, y1 - y0) * 0.5f + radius
    forEachSolidWallNear((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, reach) { index, wall ->
        val t = sweepCapsuleEntry(x0, y0, x1, y1, wall.ax, wall.ay, wall.bx, wall.by, wall.halfThickness + radius)
        if (t >= 0f && t < bestT) {
            bestT = t
            bestIndex = index
        }
    }
    return if (bestIndex < 0) -1L else packWallEntry(bestIndex, bestT)
}

/**
 * The Core, resting at ([contactX], [contactY]) where it touched [wall], answers the contact: it moves to its rest
 * point just off the wall along the contact normal and meets the solid-impact response of the wall's structure, band
 * damage scaled by the policy's wall damage scale.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.respondToWall(index: Int, wall: Wall, contactX: Float, contactY: Float) {
    val along = segmentNearest(contactX, contactY, wall.ax, wall.ay, wall.bx, wall.by)
    val nearestX = wall.ax + (wall.bx - wall.ax) * along
    val nearestY = wall.ay + (wall.by - wall.ay) * along
    val normal = wallNormal(wall, contactX - nearestX, contactY - nearestY, -velocityX, -velocityY)
    val surface = wall.halfThickness + coreRadius + WALL_CONTACT_SEPARATION
    coreX = nearestX + normal.x * surface
    coreY = nearestY + normal.y * surface
    resolveSolidImpact(structureSurfaceId(wall.id), normal.x, normal.y, coreX, coreY, content.structures.wallDamageScale)
}

/**
 * Last guard after the Core's bounces: the Core never rests inside a wall. A Core found inside one moves to its rest
 * point off it; still moving inward, it meets the solid-impact response (which hurts only at damage speed), and any
 * inward motion left is removed.
 */
internal fun MutableGameState.pushCoreOutOfWalls() {
    if (structureField.structures.isEmpty()) return
    val damageScale = content.structures.wallDamageScale
    forEachSolidWallNear(coreX, coreY, coreRadius) { _, wall ->
        val along = segmentNearest(coreX, coreY, wall.ax, wall.ay, wall.bx, wall.by)
        val nearestX = wall.ax + (wall.bx - wall.ax) * along
        val nearestY = wall.ay + (wall.by - wall.ay) * along
        val offsetX = coreX - nearestX
        val offsetY = coreY - nearestY
        val surface = wall.halfThickness + coreRadius
        if (offsetX * offsetX + offsetY * offsetY >= surface * surface) return@forEachSolidWallNear
        val normal = wallNormal(wall, offsetX, offsetY, -velocityX, -velocityY)
        val nx = normal.x
        val ny = normal.y
        coreX = nearestX + nx * (surface + WALL_CONTACT_SEPARATION)
        coreY = nearestY + ny * (surface + WALL_CONTACT_SEPARATION)
        if (phase == GamePhase.RUNNING && velocityX * nx + velocityY * ny < 0f) {
            resolveSolidImpact(structureSurfaceId(wall.id), nx, ny, coreX, coreY, damageScale)
        }
        val inward = -(velocityX * nx + velocityY * ny)
        if (inward > 0f) {
            velocityX += inward * nx
            velocityY += inward * ny
        }
    }
}

/**
 * After enemies move: pushes each live enemy out of any wall (its inward velocity is removed, and a hard contact
 * slams it), turns self-propelled enemies along a wall before they reach it, then holds every enemy a sealed room's
 * encounter owns inside that room. A phasing Phantom passes walls as it passes rocks.
 */
internal fun MutableGameState.resolveEnemyWalls() {
    if (structureField.structures.isEmpty()) return
    val range = content.obstacles.avoidanceRange
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || isPhasedPhantom(enemy)) continue
        // The broad phase adds each wall's half thickness once; avoidance scales the combined radii.
        val reach = enemy.radius * range + STRUCTURE_WALL_OVERHANG * (range - 1f)
        forEachSolidWallNear(enemy.x, enemy.y, reach) { _, wall -> resolveEnemyWall(enemy, wall) }
    }
    clampGarrisonToRoom()
}

/** [resolveEnemyObstacles]' rule for one wall, along the capsule's contact normal. */
private fun MutableGameState.resolveEnemyWall(enemy: Enemy, wall: Wall) {
    val range = content.obstacles.avoidanceRange
    val along = segmentNearest(enemy.x, enemy.y, wall.ax, wall.ay, wall.bx, wall.by)
    val nearestX = wall.ax + (wall.bx - wall.ax) * along
    val nearestY = wall.ay + (wall.by - wall.ay) * along
    val offsetX = enemy.x - nearestX
    val offsetY = enemy.y - nearestY
    val combined = wall.halfThickness + enemy.radius
    val avoidRadius = combined * range
    val distanceSquared = offsetX * offsetX + offsetY * offsetY
    if (distanceSquared >= avoidRadius * avoidRadius) return
    val distance = sqrt(distanceSquared)
    val normal = wallNormal(wall, offsetX, offsetY, -enemy.vx, -enemy.vy)
    val nx = normal.x
    val ny = normal.y
    val inward = -(enemy.vx * nx + enemy.vy * ny)
    if (distance < combined) {
        enemy.x = nearestX + nx * combined
        enemy.y = nearestY + ny * combined
        if (inward > 0f) {
            enemy.vx += inward * nx
            enemy.vy += inward * ny
            if (applySlamDamage(enemy, inward)) endPolarityThrow(enemy)
        }
        return
    }
    // Slide: self-propelled enemies bend their approach along the wall; thrown ones and strikes do not.
    if (inward <= 0f || !canAct(enemy) || enemy.thrownUntil > elapsed || isThrownByPolarity(enemy) ||
        enemy.mode == EnemyBehavior.STRIKE
    ) return
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
}

/**
 * Each live enemy the engaged room's encounter owns (the garrison, a mini-boss's adds and fragments, which hold the
 * clear) is held inside that room (template space, inset by its radius, the wall half thickness and a small gap), so
 * none of them ends behind a wall out of the Core's reach. A clamped enemy's previous position follows: the move is a
 * teleport, not a step.
 */
private fun MutableGameState.clampGarrisonToRoom() {
    val dungeon = dungeon ?: return
    val roomIndex = dungeon.engagedRoom
    if (roomIndex < 0) return
    val template = content.structures.template(dungeon.template)
    if (roomIndex >= template.rooms.size) return
    val room = template.rooms[roomIndex]
    val tag = roomEncounterTag(dungeon.structureId, roomIndex)
    val inverse = (4 - dungeon.rotation) and 3
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || enemy.ownerTag != tag) continue
        val inset = enemy.radius + template.wallHalfThickness + GARRISON_WALL_GAP
        val dx = enemy.x - dungeon.x
        val dy = enemy.y - dungeon.y
        val localX = rotateQuarterX(dx, dy, inverse)
        val localY = rotateQuarterY(dx, dy, inverse)
        val clampedX = clampIntoSpan(localX, room.minX + inset, room.maxX - inset)
        val clampedY = clampIntoSpan(localY, room.minY + inset, room.maxY - inset)
        if (clampedX == localX && clampedY == localY) continue
        enemy.x = dungeon.x + rotateQuarterX(clampedX, clampedY, dungeon.rotation)
        enemy.y = dungeon.y + rotateQuarterY(clampedX, clampedY, dungeon.rotation)
        enemy.previousX = enemy.x
        enemy.previousY = enemy.y
    }
}

/** [value] held inside [low]..[high]; the middle when the span is empty. */
private fun clampIntoSpan(value: Float, low: Float, high: Float): Float =
    if (low > high) (low + high) * 0.5f else value.coerceIn(low, high)

/** True when a circle of [radius] at ([x], [y]) would come within the spawn clearance of a solid or sealed wall. */
internal fun MutableGameState.wallBlocksPoint(x: Float, y: Float, radius: Float): Boolean {
    if (structureField.structures.isEmpty()) return false
    val reach = radius + SPAWN_OBSTACLE_CLEARANCE
    forEachSolidWallNear(x, y, reach) { _, wall ->
        if (wallDistanceSquared(x, y, wall) < square(wall.halfThickness + reach)) return true
    }
    return false
}

/** True when a circle of [radius] swept from ([x0], [y0]) to ([x1], [y1]) would touch a solid or sealed wall. */
internal fun MutableGameState.wallBlocksSegment(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Boolean {
    if (structureField.structures.isEmpty()) return false
    val reach = length(x1 - x0, y1 - y0) * 0.5f + radius
    forEachSolidWallNear((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, reach) { _, wall ->
        if (sweepCapsuleEntry(x0, y0, x1, y1, wall.ax, wall.ay, wall.bx, wall.by, wall.halfThickness + radius) >= 0f) return true
    }
    return false
}

/**
 * A circle of [radius] at ([x], [y]) moved just outside every structure footprint inflated by [radius] plus a margin,
 * along the nearest side; at most two passes (two inflated footprints may touch). A sealed room is no exception: a
 * director spawn, a leash relocation or a placement aimed into it lands outside the structure (its garrison and a
 * mini-boss's adds are placed without ejection).
 */
internal fun MutableGameState.structureEjected(x: Float, y: Float, radius: Float): SteeringVector {
    val structures = structureField.structures
    if (structures.isEmpty()) return steeringVector(x, y)
    val margin = radius + STRUCTURE_EJECT_MARGIN
    var px = x
    var py = y
    for (pass in 0 until MAX_EJECT_PASSES) {
        var moved = false
        for (index in structures.indices) {
            val structure = structures[index]
            val dx = px - structure.x
            val dy = py - structure.y
            val reachX = structure.halfWidth + margin
            val reachY = structure.halfHeight + margin
            if (abs(dx) >= reachX || abs(dy) >= reachY) continue
            if (reachX - abs(dx) <= reachY - abs(dy)) {
                px = structure.x + if (dx >= 0f) reachX + EJECT_SEPARATION else -(reachX + EJECT_SEPARATION)
            } else {
                py = structure.y + if (dy >= 0f) reachY + EJECT_SEPARATION else -(reachY + EJECT_SEPARATION)
            }
            moved = true
        }
        if (!moved) break
    }
    return steeringVector(px, py)
}

/** True when a circle of [radius] at ([x], [y]) meets a structure footprint inflated by the margin [structureEjected] keeps. */
internal fun MutableGameState.structureFootprintBlocks(x: Float, y: Float, radius: Float): Boolean {
    val structures = structureField.structures
    val margin = radius + STRUCTURE_EJECT_MARGIN
    for (index in structures.indices) {
        val structure = structures[index]
        val reachX = structure.halfWidth + margin
        val reachY = structure.halfHeight + margin
        if (abs(x - structure.x) < reachX && abs(y - structure.y) < reachY) return true
    }
    return false
}

/**
 * A circle of [radius] at ([x], [y]) drawn back along its line from ([centerX], [centerY]) to just before that line
 * first meets a structure footprint inflated by [radius] plus the margin [structureEjected] keeps, when the point lies
 * in one. A footprint the centre stands closer to than that is inflated by half the centre's gap instead, and one that
 * holds the centre is passed over. Unlike [structureEjected] it never moves a point away from the centre, so a course
 * mark keeps its bound. A point outside every footprint stays.
 */
internal fun MutableGameState.structureClippedToward(
    centerX: Float,
    centerY: Float,
    x: Float,
    y: Float,
    radius: Float,
): SteeringVector {
    val structures = structureField.structures
    val margin = radius + STRUCTURE_EJECT_MARGIN
    val dx = x - centerX
    val dy = y - centerY
    var inside = false
    var entry = 1f
    for (index in structures.indices) {
        val structure = structures[index]
        val fromX = centerX - structure.x
        val fromY = centerY - structure.y
        val gap = max(abs(fromX) - structure.halfWidth, abs(fromY) - structure.halfHeight)
        if (gap <= 0f) continue
        val inflate = min(margin, gap * 0.5f)
        val reachX = structure.halfWidth + inflate
        val reachY = structure.halfHeight + inflate
        if (abs(x - structure.x) < reachX && abs(y - structure.y) < reachY) inside = true
        entry = min(entry, boxEntry(fromX, fromY, dx, dy, reachX, reachY))
    }
    if (!inside) return steeringVector(x, y)
    val share = max(0f, entry - EJECT_SEPARATION / length(dx, dy))
    return steeringVector(centerX + dx * share, centerY + dy * share)
}

/**
 * The fraction of the segment from ([fromX], [fromY]) along ([dx], [dy]) at which it enters the open box |x| < [reachX],
 * |y| < [reachY] it starts outside of, or 1 when it never does.
 */
private fun boxEntry(fromX: Float, fromY: Float, dx: Float, dy: Float, reachX: Float, reachY: Float): Float {
    var enter = 0f
    var leave = 1f
    if (dx == 0f) {
        if (abs(fromX) >= reachX) return 1f
    } else {
        val a = (-reachX - fromX) / dx
        val b = (reachX - fromX) / dx
        enter = max(enter, min(a, b))
        leave = min(leave, max(a, b))
    }
    if (dy == 0f) {
        if (abs(fromY) >= reachY) return 1f
    } else {
        val a = (-reachY - fromY) / dy
        val b = (reachY - fromY) / dy
        enter = max(enter, min(a, b))
        leave = min(leave, max(a, b))
    }
    return if (enter < leave) enter else 1f
}

/** Shots whose step crosses a solid or sealed wall are spent where they meet it; mines never move. */
internal fun MutableGameState.blockProjectilesAtWalls() {
    if (structureField.structures.isEmpty()) return
    for (projectileIndex in projectiles.indices) {
        val projectile = projectiles[projectileIndex]
        if (projectile.spent || projectile.kind == ProjectileKind.MINE) continue
        val x0 = projectile.previousX
        val y0 = projectile.previousY
        val x1 = projectile.x
        val y1 = projectile.y
        val entry = earliestWallStep(x0, y0, x1, y1, projectile.radius)
        if (entry < 0L) continue
        val t = Float.fromBits(entry.toInt())
        projectile.life = 0f
        burst(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, 3, if (projectile.hostile) 4 else projectile.colorIndex)
    }
}

/**
 * Index of the first solid or sealed wall a circle of [radius] runs into on its way from ([x0], [y0]) to ([x1],
 * [y1]), or −1. A circle already touching a wall is stopped only when it moves into it.
 */
internal fun MutableGameState.wallOnPath(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Int {
    if (structureField.structures.isEmpty()) return -1
    val entry = earliestWallStep(x0, y0, x1, y1, radius)
    return if (entry < 0L) -1 else (entry ushr 32).toInt()
}

/** True when ([x], [y]) keeps at least [clearance] from every solid or sealed wall surface. */
internal fun MutableGameState.wallsClearOf(x: Float, y: Float, clearance: Float): Boolean {
    if (structureField.structures.isEmpty()) return true
    forEachSolidWallNear(x, y, clearance) { _, wall ->
        if (wallDistanceSquared(x, y, wall) < square(wall.halfThickness + clearance)) return false
    }
    return true
}

/** Earliest [capsuleStepEntry] over the solid and sealed walls, packed like [earliestWallEntry], or −1. */
private fun MutableGameState.earliestWallStep(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Long {
    var bestIndex = -1
    var bestT = 2f
    val reach = length(x1 - x0, y1 - y0) * 0.5f + radius
    forEachSolidWallNear((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, reach) { index, wall ->
        val t = capsuleStepEntry(x0, y0, x1, y1, wall.ax, wall.ay, wall.bx, wall.by, wall.halfThickness, radius)
        if (t >= 0f && t < bestT) {
            bestT = t
            bestIndex = index
        }
    }
    return if (bestIndex < 0) -1L else packWallEntry(bestIndex, bestT)
}

private fun packWallEntry(index: Int, t: Float): Long = (index.toLong() shl 32) or (t.toRawBits().toLong() and 0xFFFF_FFFFL)

/** Squared distance from ([x], [y]) to the centre line of [wall]. */
private fun wallDistanceSquared(x: Float, y: Float, wall: Wall): Float {
    val along = segmentNearest(x, y, wall.ax, wall.ay, wall.bx, wall.by)
    val dx = x - (wall.ax + (wall.bx - wall.ax) * along)
    val dy = y - (wall.ay + (wall.by - wall.ay) * along)
    return dx * dx + dy * dy
}

/**
 * Outward unit normal of [wall] at a point ([offsetX], [offsetY]) from its nearest centre-line point. On the centre
 * line itself it turns toward ([backX], [backY]), where the body came from: the segment's perpendicular on that side,
 * or that direction itself for a pillar; +x when nothing tells.
 */
private fun wallNormal(wall: Wall, offsetX: Float, offsetY: Float, backX: Float, backY: Float): SteeringVector {
    val distance = length(offsetX, offsetY)
    if (distance >= MIN_CONTACT_NORMAL) return steeringVector(offsetX / distance, offsetY / distance)
    val segmentX = wall.bx - wall.ax
    val segmentY = wall.by - wall.ay
    val segment = length(segmentX, segmentY)
    if (segment > 0f) {
        val nx = -segmentY / segment
        val ny = segmentX / segment
        return if (nx * backX + ny * backY < 0f) steeringVector(-nx, -ny) else steeringVector(nx, ny)
    }
    val back = length(backX, backY)
    return if (back >= MIN_CONTACT_NORMAL) steeringVector(backX / back, backY / back) else steeringVector(1f, 0f)
}

private fun square(value: Float): Float = value * value

/** The Core rests this far off a wall surface, like off a rock. */
private const val WALL_CONTACT_SEPARATION: Float = 0.05f

/** Below this distance from a wall's centre line the contact normal falls back to the motion. */
private const val MIN_CONTACT_NORMAL: Float = 0.001f

/** Room a placement keeps from a structure footprint beyond its own radius. */
private const val STRUCTURE_EJECT_MARGIN: Float = 64f

/** An ejected point lands this far past the inflated footprint, clear of float rounding. */
private const val EJECT_SEPARATION: Float = 0.5f

private const val MAX_EJECT_PASSES: Int = 2

/** Gap a garrison member keeps from its room's walls. */
private const val GARRISON_WALL_GAP: Float = 4f
