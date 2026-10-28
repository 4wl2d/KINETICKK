// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.NEAR_OBSTACLE_MAX_DISTANCE
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.bodyRadius
import kinetickk.ball.content.api.gateAngle
import kinetickk.ball.content.api.gateHalfSpan
import kinetickk.ball.content.api.obstacleSeedUnit
import kinetickk.ball.content.api.phaseShardSolid
import kinetickk.ball.content.api.pick
import kinetickk.ball.content.api.ventErupting
import kinetickk.ball.content.api.ventEruptionEnd
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Obstacle kinds. The content owns each kind's properties; every obstacle query here and in ObstacleSystem reads them
 * instead of naming kinds. Generation rolls the newer kinds on asteroid candidates from a stream of their own, so the
 * layout (positions, ids, candidate radii, gaps) never moves. After the Core's collisions a per-step pass measures
 * the Core's proximity to solid obstacles (NearObstacle, Matrix Near Miss), applies the field kinds to the Core
 * (gravity knots pull or push it, and leaving a knot after a hard turn slings it on; crossing an accelerator gate
 * boosts it; riding an erupting thermal vent heats and lifts it; coming near a derelict mine arms it), then to nearby
 * enemies (knots pull them, plumes scorch them, thrown ones set mines off), and finally detonates the mines whose fuse
 * ran out (a bounded number per step). Breakable kinds go for good: a crystal shatters into shards and Data when the
 * Core or a slammed enemy hits it hard enough, and a spent mine is gone; their ids join the destroyed memory.
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

/** Field obstacles farther than this from the Core move no enemy (bounds the enemy field pass). */
internal const val ENEMY_FIELD_ACTIVITY_RANGE: Float = 2_400f

/**
 * Runs after the Core's collisions in every running step: measures the gap NearObstacle reads and grants Matrix Near
 * Miss when the Core did not touch anything ([collided] false), applies the field kinds to the Core, settles the
 * gravity-knot sling, applies the field kinds to enemies, then detonates the mines whose fuse ran out.
 */
internal fun MutableGameState.updateObstacleKinds(collided: Boolean) {
    updateObstacleProximity(collided)
    val heldBy = updateCoreObstacleFields()
    updateSling(heldBy)
    // A BOOST clause can level up and open a choice: nothing else acts once it is open.
    if (phase != GamePhase.RUNNING) return
    updateEnemyObstacleFields()
    detonateDueMines()
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

/**
 * One lookup around the Core for the field kinds within [ObstacleKindTuning.maximumInfluence]: every gravity knot
 * whose field holds the Core pulls it toward its centre (a Repulsor pushes it away), strongest at the knot's surface
 * and fading linearly to nothing at the field's edge; an accelerator gate the Core's step crossed boosts it; an
 * erupting thermal vent whose plume holds the Core heats and lifts it; a dormant derelict mine within its arming reach
 * of the Core arms. Returns the id of the nearest knot whose field holds the Core, or [NO_OBSTACLE_ID].
 */
private fun MutableGameState.updateCoreObstacleFields(): Long {
    val kinds = content.obstacles.kinds
    var heldBy = NO_OBSTACLE_ID
    var heldDistanceSquared = Float.POSITIVE_INFINITY
    forEachObstacleNear(coreX, coreY, kinds.maximumInfluence + coreRadius) { index, obstacle ->
        when (obstacle.kind) {
            ObstacleKind.GRAVITY_WELL -> {
                val centreDistanceSquared = distanceSquared(coreX, coreY, obstacle.x, obstacle.y)
                if (centreDistanceSquared >= kinds.wellInfluence * kinds.wellInfluence) return@forEachObstacleNear
                if (centreDistanceSquared < heldDistanceSquared) {
                    heldDistanceSquared = centreDistanceSquared
                    heldBy = obstacle.id
                }
                val acceleration = wellPullAt(obstacle, coreX, coreY, coreRadius, kinds.wellCorePull, kinds)
                velocityX += acceleration.x * MutableGameState.FIXED_STEP
                velocityY += acceleration.y * MutableGameState.FIXED_STEP
            }
            ObstacleKind.ACCEL_GATE -> boostThroughGate(index, obstacle, kinds)
            ObstacleKind.THERMAL_VENT -> rideVent(index, obstacle, kinds)
            ObstacleKind.DERELICT_MINE -> armMineNearCore(index, obstacle, kinds)
            ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE, ObstacleKind.CRYSTAL, ObstacleKind.PHASE_SHARD,
            -> Unit
        }
    }
    return heldBy
}

/**
 * True when the step from ([x0], [y0]) to ([x1], [y1]) crosses the line of an accelerator gate centred at ([cx], [cy])
 * whose normal points along [angle]: it changes sides of the line between the posts ([halfSpan] either side of the
 * centre) travelling within the alignment of the normal (|cos| at least [alignment]), in either direction. A step
 * that only reaches the line, or stays on it, does not cross it; the next step leaving it does.
 */
internal fun accelGateCrossed(
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    cx: Float,
    cy: Float,
    angle: Float,
    halfSpan: Float,
    alignment: Float,
): Boolean {
    val fx = cos(angle)
    val fy = sin(angle)
    val s0 = (x0 - cx) * fx + (y0 - cy) * fy
    val s1 = (x1 - cx) * fx + (y1 - cy) * fy
    val crossed = (s0 < 0f && s1 >= 0f) || (s0 > 0f && s1 <= 0f)
    if (!crossed) return false
    val t = s0 / (s0 - s1)
    val px = x0 + (x1 - x0) * t - cx
    val py = y0 + (y1 - y0) * t - cy
    if (abs(-px * fy + py * fx) > halfSpan) return false
    val dx = x1 - x0
    val dy = y1 - y0
    val travelled = sqrt(dx * dx + dy * dy)
    return travelled > 1e-3f && abs((dx * fx + dy * fy) / travelled) >= alignment
}

/**
 * An accelerator gate: when the Core's step crossed it and the gate is ready, the gate rests for
 * [ObstacleKindTuning.gateCooldownSeconds] and boosts the Core by [ObstacleKindTuning.gateImpulse] (BOOST with the
 * gate's kind). Nothing else acts once a boost opened a choice.
 */
private fun MutableGameState.boostThroughGate(index: Int, gate: Obstacle, kinds: ObstacleKindTuning) {
    if (gate.ricochetReadyAt > elapsed || phase != GamePhase.RUNNING) return
    val halfSpan = kinds.gateHalfSpan(gate.radius)
    val angle = gateAngle(gate.shapeSeed)
    if (!accelGateCrossed(previousCoreX, previousCoreY, coreX, coreY, gate.x, gate.y, angle, halfSpan, kinds.gateAlignment)) return
    obstacleField = obstacleField.replaced(index, gate.copy(ricochetReadyAt = elapsed + kinds.gateCooldownSeconds))
    applyObstacleBoost(kinds.gateImpulse, ObstacleKind.ACCEL_GATE)
    shockwave(gate.x, gate.y, GATE_RING_SECONDS, halfSpan * GATE_RING_SPAN, YOU_TONE)
    burstBehindCore(GATE_BURST, YOU_TONE)
    emitSound(GameplayAudioCue.DASH)
}

/**
 * A thermal vent: while it erupts, a Core inside its plume (the vent's body plus [ObstacleKindTuning.ventPlumeMargin])
 * rides it once per eruption: [ObstacleKindTuning.ventHeat] heat (OVERHEAT at full) and an updraft of
 * [ObstacleKindTuning.ventImpulse] along the Core's travel, outward from the vent while the Core rests (BOOST with the
 * vent's kind). The vent remembers the end of the eruption that lifted the Core. Nothing else acts once a boost opened
 * a choice.
 */
private fun MutableGameState.rideVent(index: Int, vent: Obstacle, kinds: ObstacleKindTuning) {
    if (vent.ricochetReadyAt > elapsed || phase != GamePhase.RUNNING) return
    if (!kinds.ventErupting(elapsed, vent.shapeSeed)) return
    val plume = vent.radius + kinds.ventPlumeMargin
    val dx = coreX - vent.x
    val dy = coreY - vent.y
    val reach = plume + coreRadius
    if (dx * dx + dy * dy >= reach * reach) return
    obstacleField = obstacleField.replaced(index, vent.copy(ricochetReadyAt = kinds.ventEruptionEnd(elapsed, vent.shapeSeed)))
    val before = heat
    heat = min(MutableGameState.MAX_HEAT, heat + kinds.ventHeat)
    applyOverheatEdge(before)
    val distance = sqrt(dx * dx + dy * dy)
    val outwardX = if (distance > 0.001f) dx / distance else lastAimDirectionX
    val outwardY = if (distance > 0.001f) dy / distance else lastAimDirectionY
    applyObstacleBoost(kinds.ventImpulse, ObstacleKind.THERMAL_VENT, outwardX, outwardY)
    val thermal = FxColorCode.elementTone(StatusElement.THERMAL)
    shockwave(vent.x, vent.y, VENT_RING_SECONDS, plume, thermal)
    burstBehindCore(VENT_BURST, thermal)
    emitSound(GameplayAudioCue.DASH)
}

/** Sparks thrown back from the Core against its travel (along the aim's reverse while it rests). */
private fun MutableGameState.burstBehindCore(count: Int, colorIndex: Int) {
    val current = speed
    val directionX = if (current > 1f) velocityX / current else lastAimDirectionX
    val directionY = if (current > 1f) velocityY / current else lastAimDirectionY
    directionalBurst(coreX, coreY, count, colorIndex, -directionX, -directionY)
}

/** The volt (you) effect tone: a gate's boost is the player's. */
private const val YOU_TONE: Int = 0
private const val GATE_RING_SECONDS: Float = 0.25f
private const val GATE_RING_SPAN: Float = 1.3f
private const val GATE_BURST: Int = 10
private const val VENT_RING_SECONDS: Float = 0.35f
private const val VENT_BURST: Int = 12

/**
 * The acceleration a gravity knot's field gives a body of [bodyRadius] at ([x], [y]): [surfacePull] toward the knot
 * where the body touches its core, falling linearly to 0 at [ObstacleKindTuning.wellInfluence] from its centre; away
 * from the knot for a Repulsor. Zero outside the field and at the knot's exact centre.
 */
private fun wellPullAt(
    knot: Obstacle,
    x: Float,
    y: Float,
    bodyRadius: Float,
    surfacePull: Float,
    kinds: ObstacleKindTuning,
): SteeringVector {
    val dx = knot.x - x
    val dy = knot.y - y
    val distance = length(dx, dy)
    if (distance >= kinds.wellInfluence || distance <= 0.001f) return steeringVector(0f, 0f)
    val surface = knot.radius + bodyRadius
    val falloff = ((kinds.wellInfluence - distance) / max(1f, kinds.wellInfluence - surface)).coerceIn(0f, 1f)
    val pull = surfacePull * falloff * (if (knot.repels) -1f else 1f) / distance
    return steeringVector(dx * pull, dy * pull)
}

/**
 * The gravity-knot sling. Entering a knot's field ([heldBy]) records the Core's heading; leaving it (or passing into
 * another knot's field) after a net heading change of at least [ObstacleKindTuning.slingTurnRadians] at
 * [ObstacleKindTuning.slingMinimumSpeed] or faster throws the Core on by [ObstacleKindTuning.slingImpulse] (BOOST
 * with the knot's kind). A knot that left the window meanwhile settles nothing.
 */
private fun MutableGameState.updateSling(heldBy: Long) {
    val previous = slingWellId
    if (previous == heldBy) return
    if (previous != NO_OBSTACLE_ID && obstacleInWindow(previous)) {
        val kinds = content.obstacles.kinds
        val current = speed
        if (current >= kinds.slingMinimumSpeed && angleBetween(coreHeading(), slingEntryHeading) >= kinds.slingTurnRadians) {
            applyObstacleBoost(kinds.slingImpulse, ObstacleKind.GRAVITY_WELL)
            shockwave(coreX, coreY, SLING_RING_SECONDS, SLING_RING_RADIUS, FxColorCode.elementTone(StatusElement.GRAVITIC))
            emitSound(GameplayAudioCue.DASH)
        }
    }
    slingWellId = heldBy
    slingEntryHeading = if (heldBy == NO_OBSTACLE_ID) 0f else coreHeading()
}

private const val SLING_RING_SECONDS: Float = 0.3f
private const val SLING_RING_RADIUS: Float = 140f

/** The Core's travel heading; 0 while it barely moves. */
private fun MutableGameState.coreHeading(): Float = if (speed < 1f) 0f else atan2(velocityY, velocityX)

/** True while an obstacle with [id] is in the current window. */
private fun MutableGameState.obstacleInWindow(id: Long): Boolean {
    val obstacles = obstacleField.obstacles
    for (index in obstacles.indices) if (obstacles[index].id == id) return true
    return false
}

/**
 * Speeds the Core along its travel (along ([restDirectionX], [restDirectionY]) while it rests, its aim unless the
 * caller names another) by [impulse] (times the build's boost power), never past
 * [ObstacleKindTuning.boostLethalShare] of the lethal collision speed and never slowing it, then fires BOOST with
 * [kind]. BOOST fires even when the cap absorbs the whole impulse, so builds stay consistent at speed. Gates, slings
 * and vents share it.
 */
internal fun MutableGameState.applyObstacleBoost(
    impulse: Float,
    kind: ObstacleKind,
    restDirectionX: Float = lastAimDirectionX,
    restDirectionY: Float = lastAimDirectionY,
) {
    val cap = obstacleLethalSpeed() * content.obstacles.kinds.boostLethalShare
    val current = speed
    val target = max(current, min(current + impulse * build.boostPowerScale, cap))
    if (target > current) {
        val directionX = if (current > 1f) velocityX / current else restDirectionX
        val directionY = if (current > 1f) velocityY / current else restDirectionY
        velocityX = directionX * target
        velocityY = directionY * target
    }
    fireClauses(EffectTrigger.BOOST, null, impactPower(target), obstacle = kind)
}

/**
 * The field kinds' effect on enemies: every field obstacle (gates aside) within [ENEMY_FIELD_ACTIVITY_RANGE] of the
 * Core acts on the live, tangible enemies inside its reach. A gravity knot pulls them toward its core (a Repulsor
 * pushes) by [ObstacleKindTuning.wellEnemyPull] times their knockback intake and the build's knot pull; the obstacle
 * pass then slams those it drags in hard enough. An erupting vent's plume gives each enemy it touches one Thermal
 * stack every [ObstacleKindTuning.ventStatusInterval]. A thrown enemy touching a derelict mine's body sets it off on
 * the quick fuse.
 */
private fun MutableGameState.updateEnemyObstacleFields() {
    val obstacles = obstacleField.obstacles
    if (obstacles.isEmpty() || enemies.isEmpty()) return
    val kinds = content.obstacles.kinds
    val range = ENEMY_FIELD_ACTIVITY_RANGE * ENEMY_FIELD_ACTIVITY_RANGE
    // Plumes scorch on the steps that start a new status interval (one tick per interval for every plume).
    val interval = kinds.ventStatusInterval
    val statusTick = floor(elapsed / interval) != floor((elapsed - MutableGameState.FIXED_STEP) / interval)
    for (index in obstacles.indices) {
        val obstacle = obstacles[index]
        if (!obstacle.kind.field || distanceSquared(coreX, coreY, obstacle.x, obstacle.y) > range) continue
        when (obstacle.kind) {
            ObstacleKind.GRAVITY_WELL -> pullEnemiesIntoKnot(obstacle, kinds)
            ObstacleKind.THERMAL_VENT -> if (statusTick) scorchEnemiesInPlume(obstacle, kinds)
            ObstacleKind.DERELICT_MINE -> fuseMineUnderThrownEnemies(index, obstacle)
            // Gates never touch enemies.
            ObstacleKind.ACCEL_GATE,
            ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE, ObstacleKind.CRYSTAL, ObstacleKind.PHASE_SHARD,
            -> Unit
        }
    }
}

private fun MutableGameState.pullEnemiesIntoKnot(knot: Obstacle, kinds: ObstacleKindTuning) {
    val reach = kinds.wellInfluence * kinds.wellInfluence
    val surfacePull = kinds.wellEnemyPull * build.wellPullScale
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || isPhasedPhantom(enemy) || distanceSquared(enemy.x, enemy.y, knot.x, knot.y) >= reach) continue
        val intake = knockbackTaken(enemy)
        if (intake <= 0f) continue
        val acceleration = wellPullAt(knot, enemy.x, enemy.y, enemy.radius, surfacePull * intake, kinds)
        enemy.vx += acceleration.x * MutableGameState.FIXED_STEP
        enemy.vy += acceleration.y * MutableGameState.FIXED_STEP
    }
}

private fun MutableGameState.scorchEnemiesInPlume(vent: Obstacle, kinds: ObstacleKindTuning) {
    if (!kinds.ventErupting(elapsed, vent.shapeSeed)) return
    val plume = vent.radius + kinds.ventPlumeMargin
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || isPhasedPhantom(enemy)) continue
        val reach = plume + enemy.radius
        if (distanceSquared(enemy.x, enemy.y, vent.x, vent.y) >= reach * reach) continue
        applyStatus(enemy, StatusElement.THERMAL, 1, generation = 0)
    }
}

private fun MutableGameState.fuseMineUnderThrownEnemies(index: Int, mine: Obstacle) {
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || isPhasedPhantom(enemy)) continue
        if (enemy.thrownUntil <= elapsed && !isThrownByPolarity(enemy)) continue
        val reach = mine.radius + enemy.radius
        if (distanceSquared(enemy.x, enemy.y, mine.x, mine.y) >= reach * reach) continue
        quickFuseMine(index)
        return
    }
}

// ---------------------------------------------------------------------------------------------
// Breakable kinds.

/**
 * Remembers that the obstacle with [id] broke for good, so regenerating its chunk skips it. The memory keeps the most
 * recent [ContentBounds.MAX_DESTROYED_OBSTACLES] ids.
 */
internal fun MutableGameState.recordDestroyedObstacle(id: Long) {
    val destroyed = destroyedObstacleIds + id
    destroyedObstacleIds = if (destroyed.size > ContentBounds.MAX_DESTROYED_OBSTACLES) {
        destroyed.subList(destroyed.size - ContentBounds.MAX_DESTROYED_OBSTACLES, destroyed.size).toList()
    } else destroyed
}

/**
 * The crystal at [index] shatters for good: it leaves the window and joins the destroyed memory, throws
 * [ObstacleKindTuning.crystalShards] Cryo shard lines through nearby enemies, drops [ObstacleKindTuning.crystalData]
 * Data and fires SHATTER with the crystal's kind from its centre, as hard as [impact]. A Core that broke it ([byCore])
 * keeps [ObstacleKindTuning.crystalSpeedRetention] of its speed and flies on; a crystal never hurts it.
 */
internal fun MutableGameState.shatterCrystal(index: Int, crystal: Obstacle, impact: Float, byCore: Boolean) {
    val kinds = content.obstacles.kinds
    obstacleField = obstacleField.removed(index)
    recordDestroyedObstacle(crystal.id)
    if (byCore) {
        velocityX *= kinds.crystalSpeedRetention
        velocityY *= kinds.crystalSpeedRetention
    }
    fireShards(crystal.x, crystal.y, kinds.crystalShards, kinds.crystalShardDamage * runScale(), StatusElement.CRYO, -1, 0, false)
    for (data in 0 until kinds.crystalData) {
        val angle = obstacleSeedUnit(crystal.shapeSeed, CRYSTAL_DATA_SALT + data) * TAU
        addPickup(Pickup(PickupType.DATA, crystal.x, crystal.y, cos(angle) * CRYSTAL_DATA_SPEED, sin(angle) * CRYSTAL_DATA_SPEED))
    }
    val cryo = FxColorCode.elementTone(StatusElement.CRYO)
    burst(crystal.x, crystal.y, CRYSTAL_BURST, cryo)
    shockwave(crystal.x, crystal.y, CRYSTAL_RING_SECONDS, crystal.radius * CRYSTAL_RING_SPAN, cryo)
    emitSound(GameplayAudioCue.IMPACT)
    screenShake = max(screenShake, CRYSTAL_SHAKE)
    if (phase != GamePhase.RUNNING) return
    fireClauses(
        EffectTrigger.SHATTER, null, impactPower(impact), obstacle = ObstacleKind.CRYSTAL,
        originX = crystal.x, originY = crystal.y,
    )
}

/**
 * An enemy's slam at [slamSpeed] broke the crystal with [id] this step (one break per step: further crystals wait for
 * their next contact). Nothing happens when it is no longer in the window.
 */
internal fun MutableGameState.shatterCrystalStruckByEnemy(id: Long, slamSpeed: Float) {
    val obstacles = obstacleField.obstacles
    for (index in obstacles.indices) {
        val obstacle = obstacles[index]
        if (obstacle.id != id) continue
        shatterCrystal(index, obstacle, slamSpeed, byCore = false)
        return
    }
}

/** Salts of the Data a crystal drops: one direction per pickup from its shape seed. */
private const val CRYSTAL_DATA_SALT: Int = 80
private const val CRYSTAL_DATA_SPEED: Float = 160f
private const val CRYSTAL_BURST: Int = 18
private const val CRYSTAL_RING_SECONDS: Float = 0.3f
private const val CRYSTAL_RING_SPAN: Float = 2.2f
private const val CRYSTAL_SHAKE: Float = 6f

/**
 * A dormant derelict mine arms once the Core comes within its arming reach (its body plus
 * [ObstacleKindTuning.mineArmMargin]): it blows [ObstacleKindTuning.mineFuseSeconds] later, telegraphed by a threat
 * ring that reaches the blast's edge as it goes off.
 */
private fun MutableGameState.armMineNearCore(index: Int, mine: Obstacle, kinds: ObstacleKindTuning) {
    if (mine.fuseAt > 0f || phase != GamePhase.RUNNING) return
    val reach = mine.radius + kinds.mineArmMargin + coreRadius
    if (distanceSquared(coreX, coreY, mine.x, mine.y) >= reach * reach) return
    obstacleField = obstacleField.replaced(index, mine.copy(fuseAt = elapsed + kinds.mineFuseSeconds))
    shockwave(mine.x, mine.y, kinds.mineFuseSeconds, kinds.mineBlastRadius, THREAT_TONE)
}

/**
 * Sets the mine at [index] off on the quick fuse ([ObstacleKindTuning.mineQuickFuseSeconds] from now), arming it if it
 * was dormant; a fuse that already ends sooner stays.
 */
private fun MutableGameState.quickFuseMine(index: Int) {
    val mine = obstacleField.obstacles[index]
    val quick = elapsed + content.obstacles.kinds.mineQuickFuseSeconds
    if (mine.fuseAt > 0f && mine.fuseAt <= quick) return
    obstacleField = obstacleField.replaced(index, mine.copy(fuseAt = quick))
}

/**
 * A player shot whose step from ([x0], [y0]) to ([x1], [y1]) runs into the body of the derelict mine at [index] sets
 * it off on the quick fuse; the shot flies on (a mine stops nothing). Other kinds are left alone.
 */
internal fun MutableGameState.fuseMineCrossedByShot(
    index: Int,
    obstacle: Obstacle,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    shotRadius: Float,
) {
    if (obstacle.kind != ObstacleKind.DERELICT_MINE) return
    if (obstacleStepEntry(x0, y0, x1, y1, obstacle.x, obstacle.y, obstacle.radius, shotRadius) < 0f) return
    quickFuseMine(index)
}

/**
 * Detonates the mines whose fuse ran out, at most [ObstacleKindTuning.maximumMineDetonationsPerStep] per step (the
 * rest wait for the next step). The window is walked from its last index down, so a removal only shifts indices
 * already visited.
 */
private fun MutableGameState.detonateDueMines() {
    val kinds = content.obstacles.kinds
    var detonations = 0
    var index = obstacleField.size - 1
    while (index >= 0) {
        if (detonations >= kinds.maximumMineDetonationsPerStep || phase != GamePhase.RUNNING) return
        val obstacle = obstacleField.obstacles[index]
        if (obstacle.kind == ObstacleKind.DERELICT_MINE && obstacle.fuseAt > 0f && obstacle.fuseAt <= elapsed) {
            detonateMine(index, obstacle, kinds)
            detonations++
        }
        index--
    }
}

/**
 * The mine at [index] goes off for good: it leaves the window and joins the destroyed memory, deals
 * [ObstacleKindTuning.mineBlastEnemyDamage] (with run growth) to every enemy within [ObstacleKindTuning.mineBlastRadius],
 * sets off the mines its blast reaches on the quick fuse, costs a Core inside the blast
 * [ObstacleKindTuning.mineBlastShare] of its maximum integrity as obstacle damage (a lethal blast ends the run as an
 * obstacle collision), and fires SHATTER with the mine's kind from its centre.
 */
private fun MutableGameState.detonateMine(index: Int, mine: Obstacle, kinds: ObstacleKindTuning) {
    obstacleField = obstacleField.removed(index)
    recordDestroyedObstacle(mine.id)
    val enemyDamage = kinds.mineBlastEnemyDamage * runScale()
    areaDamage(mine.x, mine.y, kinds.mineBlastRadius, enemyDamage)
    forEachObstacleNear(mine.x, mine.y, kinds.mineBlastRadius) { neighbourIndex, neighbour ->
        if (neighbour.kind != ObstacleKind.DERELICT_MINE) return@forEachObstacleNear
        val reach = kinds.mineBlastRadius + neighbour.radius
        if (distanceSquared(mine.x, mine.y, neighbour.x, neighbour.y) <= reach * reach) quickFuseMine(neighbourIndex)
    }
    val distance = length(coreX - mine.x, coreY - mine.y)
    if (distance <= kinds.mineBlastRadius + coreRadius) {
        takeDamage(
            maxHp * kinds.mineBlastShare * build.obstacleDamageScale,
            kind = IncomingDamage.OBSTACLE,
            obstacle = ObstacleKind.DERELICT_MINE,
        )
    }
    burst(mine.x, mine.y, MINE_BURST, THREAT_TONE)
    shockwave(mine.x, mine.y, MINE_RING_SECONDS, kinds.mineBlastRadius, THREAT_TONE)
    emitSound(GameplayAudioCue.IMPACT)
    screenShake = max(screenShake, MINE_SHAKE * (1f - distance / MINE_SHAKE_RANGE).coerceIn(0f, 1f))
    if (phase != GamePhase.RUNNING) return
    fireClauses(
        EffectTrigger.SHATTER, null, enemyDamage, obstacle = ObstacleKind.DERELICT_MINE,
        originX = mine.x, originY = mine.y,
    )
}

/** The threat effect tone: a mine is a danger. */
private const val THREAT_TONE: Int = 4
private const val MINE_BURST: Int = 22
private const val MINE_RING_SECONDS: Float = 0.35f
private const val MINE_SHAKE: Float = 10f

/** A blast this far from the Core or farther shakes nothing. */
private const val MINE_SHAKE_RANGE: Float = 900f
