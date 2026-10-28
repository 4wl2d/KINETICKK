// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.NEAR_OBSTACLE_MAX_DISTANCE
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.bodyRadius
import kinetickk.ball.content.api.phaseShardSolid
import kinetickk.ball.content.api.pick
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kotlin.math.max

/*
 * Obstacle kinds. The content owns each kind's properties; every obstacle query here and in ObstacleSystem reads them
 * instead of naming kinds. Generation rolls the newer kinds on asteroid candidates from a stream of their own, so the
 * layout (positions, ids, candidate radii, gaps) never moves. After the Core's collisions a per-step pass measures
 * the Core's proximity to solid obstacles (NearObstacle, Matrix Near Miss).
 */

// ---------------------------------------------------------------------------------------------
// Generation.

/** The obstacle kind mix of the run's current world layer (density and bumper scales included). */
internal fun MutableGameState.currentObstacleMix(): ObstacleMix {
    val kinds = content.obstacles.kinds
    return when (worldLayer) {
        WorldLayer.BASE -> kinds.space
        WorldLayer.ZENITH -> kinds.zenith
        WorldLayer.NADIR -> kinds.nadir
        WorldLayer.ARCHIVE -> kinds.archive
    }
}

/** No obstacle (obstacle ids are non-negative). */
internal const val NO_OBSTACLE_ID: Long = -1L

/** Salts the variant stream apart from the layout stream of the same chunk. */
private const val OBSTACLE_VARIANT_SALT: Long = 0x0B57_AC1E_7A21_0002L

/** Draws every slot owns in the variant stream (kind, then sub-variant), so a slot's roll never depends on another's. */
private const val VARIANT_DRAWS_PER_SLOT: Int = 2

/**
 * The obstacle a placed candidate becomes: bumpers and Fractured-Space unstables stay what the layout made them, and
 * an asteroid candidate may become a newer kind by the current layer's [ObstacleMix]. The roll is stateless per
 * (chunk, slot): two draws per slot from the chunk's variant stream, which shares no draw with the layout stream.
 * A converted obstacle keeps the candidate's id, centre and shape seed and takes the kind's body radius, which never
 * exceeds the candidate's, so gaps and the origin keep-out still hold.
 */
internal fun MutableGameState.variantObstacle(
    chunkX: Int,
    chunkY: Int,
    slot: Int,
    baseKind: ObstacleKind,
    candidateRadius: Float,
    shapeSeed: Int,
    id: Long,
    x: Float,
    y: Float,
): Obstacle {
    val mix = currentObstacleMix()
    if (baseKind != ObstacleKind.ASTEROID || mix.total == 0f) {
        return Obstacle(id = id, kind = baseKind, x = x, y = y, radius = candidateRadius, shapeSeed = shapeSeed)
    }
    val kinds = content.obstacles.kinds
    val random = HashRandom(chunkSeed(runSeed, chunkX, chunkY, worldLayer) xor OBSTACLE_VARIANT_SALT)
    repeat(slot * VARIANT_DRAWS_PER_SLOT) { random.nextFloat() }
    val kindRoll = random.nextFloat()
    val subRoll = random.nextFloat()
    val kind = mix.pick(kindRoll, candidateRadius, kinds)
    return Obstacle(
        id = id,
        kind = kind,
        x = x,
        y = y,
        radius = kinds.bodyRadius(kind, candidateRadius),
        shapeSeed = shapeSeed,
        repels = kind == ObstacleKind.GRAVITY_WELL && subRoll < mix.repelShare,
    )
}

// ---------------------------------------------------------------------------------------------
// Property-driven queries.

/** True while the Core and enemies collide with this obstacle's body (a phase shard only while materialised). */
internal fun Obstacle.solidAt(elapsed: Float, kinds: ObstacleKindTuning): Boolean =
    kind.solid && (!kind.phased || kinds.phaseShardSolid(elapsed, shapeSeed))

/** True while this obstacle spends shots and stops weapon entities on its body (a phase shard only while materialised). */
internal fun Obstacle.stopsShotsAt(elapsed: Float, kinds: ObstacleKindTuning): Boolean =
    kind.stopsShots && (!kind.phased || kinds.phaseShardSolid(elapsed, shapeSeed))

/**
 * True when the Core's sweep starting at ([startX], [startY]) may hit [obstacle]: its body is solid now, except a
 * phase shard the segment starts inside, which materialised around the Core; the Core drifts out of it unharmed.
 */
internal fun MutableGameState.coreMayHit(obstacle: Obstacle, startX: Float, startY: Float): Boolean {
    if (!obstacle.solidAt(elapsed, content.obstacles.kinds)) return false
    if (!obstacle.kind.phased) return true
    val reach = obstacle.radius + coreRadius
    return distanceSquared(startX, startY, obstacle.x, obstacle.y) > reach * reach
}

/**
 * How far from its centre [obstacle] can affect the Core: its body, a mine's arming reach or a vent's plume; 0 for a
 * harmless accelerator gate. Spawns and sites keep clear of it.
 */
internal fun MutableGameState.obstacleHazardReach(obstacle: Obstacle): Float = when (obstacle.kind) {
    ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE, ObstacleKind.CRYSTAL, ObstacleKind.PHASE_SHARD,
    ObstacleKind.GRAVITY_WELL -> obstacle.radius
    ObstacleKind.DERELICT_MINE -> obstacle.radius + content.obstacles.kinds.mineArmMargin
    ObstacleKind.THERMAL_VENT -> obstacle.radius + content.obstacles.kinds.ventPlumeMargin
    ObstacleKind.ACCEL_GATE -> 0f
}

/** The farthest any kind's hazard reach lies beyond its body; a query by hazard reach widens its lookup by it. */
internal fun ObstacleKindTuning.hazardMargin(): Float = max(mineArmMargin, ventPlumeMargin)

// ---------------------------------------------------------------------------------------------
// The step.

/**
 * Runs after the Core's collisions in every running step: measures the gap NearObstacle reads and grants Matrix Near
 * Miss when the Core did not touch anything ([collided] false).
 */
internal fun MutableGameState.updateObstacleKinds(collided: Boolean) {
    updateObstacleProximity(collided)
}

/**
 * One lookup around the Core: [MutableGameState.coreObstacleGap] is the gap to the nearest solid surface within
 * [NEAR_OBSTACLE_MAX_DISTANCE] (0 while touching, +∞ when none), and Near Miss fires when the Core skims a grazeable
 * solid surface fast without touching it.
 */
private fun MutableGameState.updateObstacleProximity(collided: Boolean) {
    val kinds = content.obstacles.kinds
    val nearMiss = matrix.nearMiss
    var solidGap = Float.POSITIVE_INFINITY
    var grazeGap = Float.POSITIVE_INFINITY
    val reach = max(NEAR_OBSTACLE_MAX_DISTANCE, nearMiss?.distance ?: 0f) + coreRadius
    forEachObstacleNear(coreX, coreY, reach) { _, obstacle ->
        if (!obstacle.solidAt(elapsed, kinds)) return@forEachObstacleNear
        val gap = length(coreX - obstacle.x, coreY - obstacle.y) - obstacle.radius - coreRadius
        if (gap < solidGap) solidGap = gap
        if (obstacle.kind.grazeable && gap > 0f && gap < grazeGap) grazeGap = gap
    }
    coreObstacleGap = if (solidGap <= NEAR_OBSTACLE_MAX_DISTANCE) max(0f, solidGap) else Float.POSITIVE_INFINITY
    if (collided || nearMiss == null) return
    if (speed >= nearMiss.minSpeed && nearMissReadyAt <= elapsed && grazeGap <= nearMiss.distance) {
        nearMissReadyAt = elapsed + nearMiss.cooldown
        gainOverdrive(nearMiss.overdrive)
    }
}
