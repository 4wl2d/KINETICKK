// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.CircuitBeacons
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.model.lerp
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * World obstacles. The field is generated per chunk from a hash of the run seed and the absolute
 * chunk, so the same run always meets the same rocks, and the window of chunks around the Core is
 * regenerated only when the Core changes chunk. Absolute positions are run-local positions plus the
 * accumulated rebase origin.
 *
 * Collisions:
 * - the Core sweeps against obstacles right after it moves; asteroids hurt above the damage-start
 *   speed and kill above the lethal speed, bumpers throw it back, unstable asteroids explode;
 * - enemies are pushed out, slide around obstacles and slam into them when thrown;
 * - asteroids stop player and hostile shots.
 */

// ---------------------------------------------------------------------------------------------
// Queries.

/** Clear space a spawn keeps from an obstacle surface. */
internal const val SPAWN_OBSTACLE_CLEARANCE: Float = 24f

/** True when a circle of [radius] at ([x], [y]) would overlap an obstacle, so a spawn there must move. */
internal fun MutableGameState.isSpawnPointBlocked(x: Float, y: Float, radius: Float): Boolean {
    val reach = radius + SPAWN_OBSTACLE_CLEARANCE
    forEachObstacleNear(x, y, reach) { _, obstacle ->
        if (distanceSquared(x, y, obstacle.x, obstacle.y) < square(obstacle.radius + reach)) return true
    }
    return wallBlocksPoint(x, y, radius)
}

/**
 * True when a circle of [radius] swept from ([x0], [y0]) to ([x1], [y1]) would touch an obstacle;
 * blinks stop short of it and hostile sight lines and shots are blocked by it.
 */
internal fun MutableGameState.obstacleBlocksSegment(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Boolean {
    val centerX = (x0 + x1) * 0.5f
    val centerY = (y0 + y1) * 0.5f
    val reach = length(x1 - x0, y1 - y0) * 0.5f + radius
    forEachObstacleNear(centerX, centerY, reach) { _, obstacle ->
        if (sweepEntry(x0, y0, x1, y1, obstacle.x, obstacle.y, obstacle.radius + radius) >= 0f) return true
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

private fun square(value: Float): Float = value * value

// ---------------------------------------------------------------------------------------------
// Generation.

/** A new run starts at the absolute origin with a fresh window and no destroyed asteroids. */
internal fun MutableGameState.resetObstacles() {
    worldOriginX = 0.0
    worldOriginY = 0.0
    obstacleField = ObstacleField.EMPTY
    destroyedObstacleIds = emptyList()
    obstacleHitReadyAt = 0f
    obstacleHitSurfaceId = NO_SURFACE_ID
    nearMissReadyAt = 0f
    reviveInvulnerableUntil = 0f
    coreObstacleGap = Float.POSITIVE_INFINITY
    grazeReadyAt = 0f
    slingWellId = NO_OBSTACLE_ID
    slingEntryHeading = 0f
    worldLayer = WorldLayer.BASE
    worldKeepOut = null
    refreshObstacleWindow()
}

/** Absolute chunk index of an absolute coordinate. */
internal fun MutableGameState.absoluteChunk(absolute: Double): Int =
    floor(absolute / content.obstacles.chunkSize).toInt()

/** Regenerates the obstacle window when the Core entered another chunk (or no window exists yet). */
internal fun MutableGameState.refreshObstacleWindow() {
    val tuning = content.obstacles
    val chunkX = absoluteChunk(worldOriginX + coreX)
    val chunkY = absoluteChunk(worldOriginY + coreY)
    val field = obstacleField
    if (field.span == tuning.windowChunks && field.centerChunkX == chunkX && field.centerChunkY == chunkY) return
    val span = tuning.windowChunks
    val half = span / 2
    val cells = ArrayList<List<Obstacle>>(span * span)
    for (row in 0 until span) {
        for (column in 0 until span) {
            val cx = chunkX - half + column
            val cy = chunkY - half + row
            // Chunks still inside the window keep their obstacles (and any bumper cooldowns).
            cells += field.chunkObstacles(cx, cy)?.toList() ?: generateChunk(cx, cy)
        }
    }
    val next = ObstacleField.of(chunkX, chunkY, span, cells)
    check(next.size <= ContentBounds.MAX_OBSTACLES) { "The obstacle window exceeded ${ContentBounds.MAX_OBSTACLES}" }
    obstacleField = next
    refreshStructureField(chunkX, chunkY)
}

/**
 * The obstacles of one absolute chunk: a binomial count over the chunk's slots (mean scaled by Rebirth
 * density and Fractured Space), kinds and radii from the tuning, centres anywhere in the chunk (so no
 * obstacle-free corridor runs along chunk borders), a full gap from each other, none reaching into the
 * safe radius around the run origin. A candidate too close to a candidate of an earlier neighbour
 * chunk (lower row, or the same row to the left) yields to it, which keeps the gap across borders
 * while every chunk still depends only on its own hash and its neighbours'. Destroyed unstable
 * asteroids stay gone; they still count as candidates, so the rest of the field never moves.
 */
internal fun MutableGameState.generateChunk(chunkX: Int, chunkY: Int): List<Obstacle> {
    val own = chunkCandidates(chunkX, chunkY)
    if (own.isEmpty()) return emptyList()
    val tuning = content.obstacles
    val chunk = tuning.chunkSize
    val neighbours = Array(EARLIER_NEIGHBOURS.size / 2) { neighbour ->
        chunkCandidates(chunkX + EARLIER_NEIGHBOURS[neighbour * 2], chunkY + EARLIER_NEIGHBOURS[neighbour * 2 + 1])
    }
    val result = ArrayList<Obstacle>(own.size)
    for (index in own.indices) {
        val candidate = own[index]
        if (yieldsToEarlierNeighbour(neighbours, candidate)) continue
        val id = obstacleId(chunkX, chunkY, candidate.slot, worldLayer)
        if (id in destroyedObstacleIds) continue
        if (structureSuppresses(chunkX, chunkY, candidate.offsetX, candidate.offsetY, candidate.radius)) continue
        result += Obstacle(
            id = id,
            kind = candidate.kind,
            x = (chunkX.toDouble() * chunk + candidate.offsetX - worldOriginX).toFloat(),
            y = (chunkY.toDouble() * chunk + candidate.offsetY - worldOriginY).toFloat(),
            radius = candidate.radius,
            shapeSeed = candidate.shapeSeed,
        )
    }
    return result
}

/** One placed obstacle of a chunk before cross-border resolution; offsets are within the chunk. */
private class ObstacleCandidate(
    val slot: Int,
    val kind: ObstacleKind,
    val radius: Float,
    val shapeSeed: Int,
    val offsetX: Float,
    val offsetY: Float,
)

/** [neighbours] holds the candidates of each [EARLIER_NEIGHBOURS] chunk, in that order. */
private fun MutableGameState.yieldsToEarlierNeighbour(
    neighbours: Array<List<ObstacleCandidate>>,
    candidate: ObstacleCandidate,
): Boolean {
    val tuning = content.obstacles
    val chunk = tuning.chunkSize
    for (neighbour in neighbours.indices) {
        val dx = EARLIER_NEIGHBOURS[neighbour * 2]
        val dy = EARLIER_NEIGHBOURS[neighbour * 2 + 1]
        val others = neighbours[neighbour]
        for (index in others.indices) {
            val other = others[index]
            val separationX = dx * chunk + other.offsetX - candidate.offsetX
            val separationY = dy * chunk + other.offsetY - candidate.offsetY
            val gap = other.radius + candidate.radius + tuning.minimumGap
            if (separationX * separationX + separationY * separationY < gap * gap) return true
        }
    }
    return false
}

/** Neighbour chunks generated "before" a chunk, as (dx, dy) pairs: the row below and the left one. */
private val EARLIER_NEIGHBOURS: IntArray = intArrayOf(-1, -1, 0, -1, 1, -1, -1, 0)

/** The chunk's own placements in the current world layer: deterministic from its hash, gap kept inside the chunk. */
private fun MutableGameState.chunkCandidates(chunkX: Int, chunkY: Int): List<ObstacleCandidate> {
    val tuning = content.obstacles
    val fractured = runMutatorActive(RebirthMutator.FRACTURED_SPACE)
    val mix = currentObstacleMix()
    val mean = tuning.meanPerChunk * rebirthProfile.obstacleDensityMultiplier *
        (if (fractured) tuning.fracturedDensityMultiplier else 1f) * mix.densityScale
    val slots = tuning.maximumPerChunk
    if (slots == 0) return emptyList()
    val chance = (mean / slots).coerceIn(0f, 1f)
    val random = HashRandom(chunkSeed(runSeed, chunkX, chunkY, worldLayer))
    var count = 0
    repeat(slots) { if (random.nextFloat() < chance) count++ }
    if (count == 0) return emptyList()
    val bumperShare = (if (fractured) tuning.fracturedBumperShare else tuning.bumperShare) * mix.bumperScale
    val chunk = tuning.chunkSize
    val baseX = chunkX.toDouble() * chunk
    val baseY = chunkY.toDouble() * chunk
    val placed = ArrayList<ObstacleCandidate>(count)
    for (slot in 0 until count) {
        val bumper = random.nextFloat() < bumperShare
        val unstable = random.nextFloat() < tuning.fracturedUnstableShare
        val size = random.nextFloat()
        val shapeSeed = random.nextInt()
        val kind = when {
            bumper -> ObstacleKind.BUMPER
            fractured && unstable -> ObstacleKind.UNSTABLE
            else -> ObstacleKind.ASTEROID
        }
        val radius = if (bumper) tuning.bumperRadius else lerp(tuning.asteroidMinimumRadius, tuning.asteroidMaximumRadius, size)
        for (attempt in 0 until PLACEMENT_ATTEMPTS) {
            val offsetX = random.nextFloat() * chunk
            val offsetY = random.nextFloat() * chunk
            val absoluteX = baseX + offsetX
            val absoluteY = baseY + offsetY
            if (sqrt(absoluteX * absoluteX + absoluteY * absoluteY) - radius < tuning.safeRadius) continue
            var clear = true
            for (index in placed.indices) {
                val other = placed[index]
                val gap = other.radius + radius + tuning.minimumGap
                if (distanceSquared(offsetX, offsetY, other.offsetX, other.offsetY) < gap * gap) {
                    clear = false
                    break
                }
            }
            if (!clear) continue
            placed += ObstacleCandidate(slot, kind, radius, shapeSeed, offsetX, offsetY)
            break
        }
    }
    return placed
}

private const val PLACEMENT_ATTEMPTS: Int = 8

/**
 * Stable obstacle id: absolute chunk (28 bits per axis), slot (4 bits) and the world layer in bits 60-62
 * (0 in realspace, so realspace ids are the pre-layer ones).
 */
internal fun obstacleId(chunkX: Int, chunkY: Int, slot: Int, layer: WorldLayer = WorldLayer.BASE): Long =
    ((layer.ordinal.toLong() and 0x7) shl 60) or ((chunkX.toLong() and CHUNK_ID_MASK) shl 32) or
        ((chunkY.toLong() and CHUNK_ID_MASK) shl 4) or (slot.toLong() and 0xF)

private const val CHUNK_ID_MASK: Long = 0x0FFF_FFFF

/** Stateless hash of the run seed, the layer and the absolute chunk, so generation order never matters. */
internal fun chunkSeed(runSeed: Int, chunkX: Int, chunkY: Int, layer: WorldLayer = WorldLayer.BASE): Long =
    worldHash(runSeed, layer, chunkX, chunkY, OBSTACLE_SEED_SALT)

private const val OBSTACLE_SEED_SALT: Long = 0x0B57_AC1E_5EED_0001L

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
// The Core.

/**
 * Runs right after the Core moves: keeps the window current, resolves the swept Core path against
 * obstacles (bounces, damage, ricochets, explosions) and grants Near Miss.
 */
internal fun MutableGameState.updateObstacles() {
    refreshObstacleWindow()
    val collided = resolveCoreObstacleCollisions()
    if (phase != GamePhase.RUNNING) return
    if (!collided) checkNearMiss()
}

private const val MAX_CORE_BOUNCES: Int = 3
private const val CONTACT_SEPARATION: Float = 0.05f

/** Returns true when the Core touched an obstacle this step. */
private fun MutableGameState.resolveCoreObstacleCollisions(): Boolean {
    if (obstacleField.size == 0 && structureField.structures.isEmpty()) return false
    var startX = previousCoreX
    var startY = previousCoreY
    var endX = coreX
    var endY = coreY
    var collided = false
    val delta = MutableGameState.FIXED_STEP
    for (bounce in 0 until MAX_CORE_BOUNCES) {
        var hitIndex = -1
        var hitT = 2f
        val reach = length(endX - startX, endY - startY) * 0.5f + coreRadius
        val segmentStartX = startX
        val segmentStartY = startY
        val segmentEndX = endX
        val segmentEndY = endY
        forEachObstacleNear((startX + endX) * 0.5f, (startY + endY) * 0.5f, reach) { index, obstacle ->
            val t = sweepEntry(
                segmentStartX, segmentStartY, segmentEndX, segmentEndY,
                obstacle.x, obstacle.y, obstacle.radius + coreRadius,
            )
            if (t >= 0f && t < hitT) {
                hitT = t
                hitIndex = index
            }
        }
        // Structure walls sweep in the same pass; a circle wins a tie.
        var wallIndex = -1
        val wallHit = earliestWallEntry(segmentStartX, segmentStartY, segmentEndX, segmentEndY, coreRadius, hitT)
        if (wallHit >= 0L) {
            wallIndex = (wallHit ushr 32).toInt()
            hitT = Float.fromBits(wallHit.toInt())
            hitIndex = -1
        }
        if (hitIndex < 0 && wallIndex < 0) break
        collided = true
        val contactX = startX + (endX - startX) * hitT
        val contactY = startY + (endY - startY) * hitT
        if (wallIndex >= 0) {
            // The wall response leaves the Core at its rest point off the wall; the sweep goes on from there.
            coreX = contactX
            coreY = contactY
            respondToWall(wallIndex, structureField.walls[wallIndex], contactX, contactY)
            startX = coreX
            startY = coreY
        } else {
            val obstacle = obstacleField.obstacles[hitIndex]
            var normalX = contactX - obstacle.x
            var normalY = contactY - obstacle.y
            val normalLength = length(normalX, normalY)
            if (normalLength < 0.001f) {
                val currentSpeed = speed
                normalX = if (currentSpeed > 0.001f) -velocityX / currentSpeed else 1f
                normalY = if (currentSpeed > 0.001f) -velocityY / currentSpeed else 0f
            } else {
                normalX /= normalLength
                normalY /= normalLength
            }
            val surface = obstacle.radius + coreRadius + CONTACT_SEPARATION
            val restX = obstacle.x + normalX * surface
            val restY = obstacle.y + normalY * surface
            val passedThrough = respondToObstacle(hitIndex, obstacle, normalX, normalY, restX, restY)
            if (passedThrough) {
                // The rock shattered: the Core keeps its line from the contact point.
                startX = contactX
                startY = contactY
            } else {
                startX = restX
                startY = restY
            }
        }
        val remaining = (1f - hitT) * delta
        endX = startX + velocityX * remaining
        endY = startY + velocityY * remaining
        if (phase != GamePhase.RUNNING) break
    }
    coreX = endX
    coreY = endY
    if (collided) {
        pushCoreOutOfObstacles()
        pushCoreOutOfWalls()
    }
    return collided
}

/** Last guard after the bounces: the Core never rests inside an obstacle. */
private fun MutableGameState.pushCoreOutOfObstacles() {
    forEachObstacleNear(coreX, coreY, coreRadius) { _, obstacle ->
        val combined = obstacle.radius + coreRadius
        val dx = coreX - obstacle.x
        val dy = coreY - obstacle.y
        val distance = length(dx, dy)
        if (distance < combined) {
            val nx = if (distance > 0.001f) dx / distance else 1f
            val ny = if (distance > 0.001f) dy / distance else 0f
            coreX = obstacle.x + nx * (combined + CONTACT_SEPARATION)
            coreY = obstacle.y + ny * (combined + CONTACT_SEPARATION)
            val inward = -(velocityX * nx + velocityY * ny)
            if (inward > 0f) {
                velocityX += inward * nx
                velocityY += inward * ny
            }
        }
    }
}

/**
 * Velocity response, damage and triggers of the Core meeting [obstacle] along the outward normal
 * ([nx], [ny]) at ([contactX], [contactY]), dispatched by kind: bumpers throw, an unstable asteroid hit at
 * the damage-start impact speed bursts, every other obstacle is a solid surface. Returns true when the
 * obstacle shattered and the Core passes on without bouncing.
 */
private fun MutableGameState.respondToObstacle(
    index: Int,
    obstacle: Obstacle,
    nx: Float,
    ny: Float,
    contactX: Float,
    contactY: Float,
): Boolean {
    val normalSpeed = -(velocityX * nx + velocityY * ny)
    if (normalSpeed <= 0f) return false
    if (obstacle.kind == ObstacleKind.BUMPER) {
        bounceOffBumper(index, obstacle, nx, ny, normalSpeed, contactX, contactY)
        return false
    }
    if (obstacle.kind == ObstacleKind.UNSTABLE) {
        val impact = obstacleImpactSpeed(normalSpeed, length(velocityX + normalSpeed * nx, velocityY + normalSpeed * ny))
        if (impact >= obstacleDamageStartSpeed()) {
            explodeUnstable(index, obstacle, impact)
            return true
        }
    }
    resolveSolidImpact(obstacle.id, nx, ny, contactX, contactY)
    return false
}

/** Ricochet payoffs: Ricochet Rig's burst, Gravity Surfer's dash phase and RICOCHET clauses. */
internal fun MutableGameState.onRicochet(x: Float, y: Float, normalSpeed: Float) {
    val impact = impactPower(normalSpeed)
    matrix.ricochetRig?.let { rig -> if (rig.burstRadius > 0f) areaDamage(x, y, rig.burstRadius, impact) }
    if (matrix.ricochetPhaseSeconds > 0f) dashPhaseTime = max(dashPhaseTime, matrix.ricochetPhaseSeconds)
    fireClauses(EffectTrigger.RICOCHET, null, impact)
}

/**
 * Elastic throw: the Core leaves at 1.05 × its speed, held to the bumper band and below the lethal
 * collision speed (on a small viewport that speed can lie inside the band); never hurts. Like an asteroid
 * bounce it is a ricochet only at the ricochet speed or faster, at most once per cooldown per bumper.
 */
private fun MutableGameState.bounceOffBumper(
    index: Int,
    obstacle: Obstacle,
    nx: Float,
    ny: Float,
    normalSpeed: Float,
    contactX: Float,
    contactY: Float,
) {
    val tuning = content.obstacles
    val incoming = speed
    velocityX += 2f * normalSpeed * nx
    velocityY += 2f * normalSpeed * ny
    val current = speed
    val directionX = if (current > 0.001f) velocityX / current else nx
    val directionY = if (current > 0.001f) velocityY / current else ny
    val band = max(tuning.bumperMinimumExitSpeed, min(tuning.bumperGain * incoming, tuning.bumperMaximumExitSpeed))
    val exit = min(band, obstacleLethalSpeed() * tuning.bumperLethalShare)
    velocityX = directionX * exit
    velocityY = directionY * exit
    shockwave(obstacle.x, obstacle.y, 0.25f, obstacle.radius * 2.4f, 2)
    emitSound(GameplayAudioCue.IMPACT)
    screenShake = max(screenShake, 4f)
    if (normalSpeed < obstacleRicochetSpeed() || obstacle.ricochetReadyAt > elapsed) return
    obstacleField = obstacleField.replaced(index, obstacle.copy(ricochetReadyAt = elapsed + tuning.bumperRicochetCooldownSeconds))
    pendingRicochets++
    onRicochet(contactX - nx * coreRadius, contactY - ny * coreRadius, normalSpeed)
}

/**
 * An unstable asteroid hit at the damage-start impact speed bursts: it hurts the Core a little and enemies around
 * it, as hard as the [impact] speed.
 */
private fun MutableGameState.explodeUnstable(index: Int, obstacle: Obstacle, impact: Float) {
    val tuning = content.obstacles
    obstacleField = obstacleField.removed(index)
    val destroyed = destroyedObstacleIds + obstacle.id
    destroyedObstacleIds = if (destroyed.size > ContentBounds.MAX_DESTROYED_OBSTACLES) {
        destroyed.subList(destroyed.size - ContentBounds.MAX_DESTROYED_OBSTACLES, destroyed.size).toList()
    } else destroyed
    areaDamage(obstacle.x, obstacle.y, tuning.unstableBlastRadius, impactPower(impact))
    burst(obstacle.x, obstacle.y, 24, 4)
    screenShake = max(screenShake, 12f)
    emitSound(GameplayAudioCue.IMPACT)
    if (distanceSquared(coreX, coreY, obstacle.x, obstacle.y) <= square(tuning.unstableBlastRadius + coreRadius + obstacle.radius)) {
        takeDamage(tuning.unstableBlastDamage, kind = IncomingDamage.OBSTACLE)
    }
}

/** Near Miss: skimming past an asteroid fast enough, without touching it, grants Overdrive. */
private fun MutableGameState.checkNearMiss() {
    val nearMiss = matrix.nearMiss ?: return
    if (speed < nearMiss.minSpeed || nearMissReadyAt > elapsed) return
    val reach = coreRadius + nearMiss.distance
    forEachObstacleNear(coreX, coreY, reach) { _, obstacle ->
        if (obstacle.kind == ObstacleKind.BUMPER) return@forEachObstacleNear
        val gap = length(coreX - obstacle.x, coreY - obstacle.y) - obstacle.radius - coreRadius
        if (gap > 0f && gap <= nearMiss.distance) {
            nearMissReadyAt = elapsed + nearMiss.cooldown
            gainOverdrive(nearMiss.overdrive)
            return
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Enemies.

/**
 * After enemies move: pushes each out of any obstacle (its inward velocity is removed, and a hard
 * contact slams it), turns self-propelled enemies along the surface before they reach it, then lets
 * thrown enemies slam into each other (bounded per step).
 */
internal fun MutableGameState.resolveEnemyObstacles() {
    val tuning = content.obstacles
    if (obstacleField.size > 0) {
        // The lookup adds the largest obstacle radius once; avoidance scales the combined radii.
        val largest = max(tuning.asteroidMaximumRadius, tuning.bumperRadius)
        for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            if (!enemy.alive || isPhasedPhantom(enemy)) continue
            val avoidReach = (enemy.radius + largest) * tuning.avoidanceRange - largest
            forEachObstacleNear(enemy.x, enemy.y, avoidReach) { _, obstacle ->
                resolveEnemyObstacle(enemy, obstacle)
            }
        }
    }
    resolveEnemyWalls()
    resolveThrownEnemySlams()
}

private fun MutableGameState.resolveEnemyObstacle(enemy: Enemy, obstacle: Obstacle) {
    val tuning = content.obstacles
    val combined = obstacle.radius + enemy.radius
    val dx = enemy.x - obstacle.x
    val dy = enemy.y - obstacle.y
    val distanceSquared = dx * dx + dy * dy
    val avoidRadius = combined * tuning.avoidanceRange
    if (distanceSquared >= avoidRadius * avoidRadius) return
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
            if (applySlamDamage(enemy, inward)) endPolarityThrow(enemy)
        }
        return
    }
    // Slide: self-propelled enemies bend their approach along the surface; thrown ones and strikes do not.
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
            if (!other.alive || isPhasedPhantom(other)) continue
            val dx = thrown.x - other.x
            val dy = thrown.y - other.y
            val combined = thrown.radius + other.radius
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared >= combined * combined) continue
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

// ---------------------------------------------------------------------------------------------
// Shots.

/**
 * Asteroids stop player and hostile shots: a shot whose step crosses one is spent (removed after the
 * collision passes). Bumpers let shots through; mines never move.
 */
internal fun MutableGameState.blockProjectilesAtObstacles() {
    if (obstacleField.size == 0) return
    for (projectileIndex in projectiles.indices) {
        val projectile = projectiles[projectileIndex]
        if (projectile.spent || projectile.kind == ProjectileKind.MINE) continue
        val x0 = projectile.previousX
        val y0 = projectile.previousY
        val x1 = projectile.x
        val y1 = projectile.y
        val reach = length(x1 - x0, y1 - y0) * 0.5f + projectile.radius
        forEachObstacleNear((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, reach) { _, obstacle ->
            if (obstacle.kind == ObstacleKind.BUMPER) return@forEachObstacleNear
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
 * of obstacles (at most [attempts] moves); returns the site. Used for rare placements only.
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
    return siteX to siteY
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

/** Moves an anomaly outward from the Core until its site is clear (bounded). */
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
    return siteX to siteY
}
