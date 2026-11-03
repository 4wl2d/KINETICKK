// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.world

import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.NEAR_OBSTACLE_MAX_DISTANCE
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.bodyRadius
import kinetickk.ball.content.api.gateAngle
import kinetickk.ball.content.api.gateHalfSpan
import kinetickk.ball.content.api.ventErupting
import kinetickk.ball.content.api.ventEruptionEnd
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// The step.

/** Field obstacles farther than this from the Core move no enemy (bounds the enemy field pass). */
internal const val ENEMY_FIELD_ACTIVITY_RANGE: Float = 2_400f

/**
 * Runs after the Core's collisions in every running step: measures the gap NearObstacle reads and grants Matrix Near
 * Miss and GRAZE when the Core did not touch anything ([collided] false), applies the field kinds to the Core, settles
 * the gravity-knot sling, applies the field kinds to enemies, then detonates the mines whose fuse ran out.
 */
internal fun MutableGameState.updateObstacleKinds(collided: Boolean) {
    updateObstacleProximity(collided)
    // A GRAZE clause can level up and open a choice: nothing else acts once it is open.
    if (phase != GamePhase.RUNNING) return
    val heldBy = updateCoreObstacleFields()
    updateSling(heldBy)
    // A BOOST clause can level up and open a choice: nothing else acts once it is open.
    if (phase != GamePhase.RUNNING) return
    updateEnemyObstacleFields()
    detonateDueMines()
}

/**
 * One lookup around the Core: [MutableGameState.coreObstacleGap] is the gap to the nearest solid surface within
 * [NEAR_OBSTACLE_MAX_DISTANCE] (0 while touching, +∞ when none). When the Core skims the nearest grazeable solid
 * surface fast without touching anything, Matrix Near Miss fires by its own numbers, and GRAZE (with that obstacle's
 * kind) fires within [ObstacleKindTuning.grazeDistance] at [ObstacleKindTuning.grazeMinimumSpeed] or faster, at most
 * once per [ObstacleKindTuning.grazeCooldownSeconds], throwing sparks off the surface toward the Core.
 */
private fun MutableGameState.updateObstacleProximity(collided: Boolean) {
    val kinds = content.obstacles.kinds
    val nearMiss = matrix.nearMiss
    var solidGap = Float.POSITIVE_INFINITY
    var grazeGap = Float.POSITIVE_INFINITY
    var grazed: Obstacle? = null
    val reach = max(NEAR_OBSTACLE_MAX_DISTANCE, nearMiss?.distance ?: 0f) + coreRadius
    forEachObstacleNear(coreX, coreY, reach) { _, obstacle ->
        if (!obstacle.solidAt(elapsed, kinds)) return@forEachObstacleNear
        val gap = length(coreX - obstacle.x, coreY - obstacle.y) - obstacle.radius - coreRadius
        if (gap < solidGap) solidGap = gap
        if (obstacle.kind.grazeable && gap > 0f && gap < grazeGap) {
            grazeGap = gap
            grazed = obstacle
        }
    }
    coreObstacleGap = if (solidGap <= NEAR_OBSTACLE_MAX_DISTANCE) max(0f, solidGap) else Float.POSITIVE_INFINITY
    if (collided) return
    if (nearMiss != null && speed >= nearMiss.minSpeed && nearMissReadyAt <= elapsed && grazeGap <= nearMiss.distance) {
        nearMissReadyAt = elapsed + nearMiss.cooldown
        gainOverdrive(nearMiss.overdrive)
    }
    val surface = grazed ?: return
    if (speed < kinds.grazeMinimumSpeed || grazeReadyAt > elapsed || grazeGap > kinds.grazeDistance) return
    grazeReadyAt = elapsed + kinds.grazeCooldownSeconds
    // The spark point: the obstacle's surface on the line to the Core.
    val toCore = grazeGap + surface.radius + coreRadius
    burst(
        surface.x + (coreX - surface.x) / toCore * surface.radius,
        surface.y + (coreY - surface.y) / toCore * surface.radius,
        GRAZE_SPARKS,
        GRAZE_TONE,
    )
    fireClauses(EffectTrigger.GRAZE, null, impactPower(speed), obstacle = surface.kind)
}

private const val GRAZE_SPARKS: Int = 4
private const val GRAZE_TONE: Int = 2

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
    if (previous != NO_OBSTACLE_ID && obstacleIndexOf(previous) >= 0) {
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

/** Index of the obstacle with [id] in the current window, or -1 when it is not there. */
internal fun MutableGameState.obstacleIndexOf(id: Long): Int {
    val obstacles = obstacleField.obstacles
    for (index in obstacles.indices) if (obstacles[index].id == id) return index
    return -1
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
 * the quick fuse. Only the window's field obstacles are walked, and the mines only while some live enemy is thrown.
 */
private fun MutableGameState.updateEnemyObstacleFields() {
    val field = obstacleField
    if (field.fieldCount == 0 || enemies.isEmpty()) return
    val obstacles = field.obstacles
    val kinds = content.obstacles.kinds
    val range = ENEMY_FIELD_ACTIVITY_RANGE * ENEMY_FIELD_ACTIVITY_RANGE
    // Plumes scorch on the steps that start a new status interval (one tick per interval for every plume).
    val interval = kinds.ventStatusInterval
    val statusTick = floor(elapsed / interval) != floor((elapsed - MutableGameState.FIXED_STEP) / interval)
    // Asked at the first mine in range, and again after a plume scorched (a status can throw or kill); a knot only
    // changes velocities and a fuse only the window, so the answer holds between them.
    var thrownKnown = false
    var anyThrown = false
    for (n in 0 until field.fieldCount) {
        val index = field.fieldIndex(n)
        val obstacle = obstacles[index]
        if (distanceSquared(coreX, coreY, obstacle.x, obstacle.y) > range) continue
        when (obstacle.kind) {
            ObstacleKind.GRAVITY_WELL -> pullEnemiesIntoKnot(obstacle, kinds)
            ObstacleKind.THERMAL_VENT -> if (statusTick) {
                scorchEnemiesInPlume(obstacle, kinds)
                thrownKnown = false
            }
            ObstacleKind.DERELICT_MINE -> {
                if (!thrownKnown) {
                    anyThrown = anyLiveThrownEnemy()
                    thrownKnown = true
                }
                if (anyThrown) fuseMineUnderThrownEnemies(index, obstacle)
            }
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
        if (!enemy.alive || distanceSquared(enemy.x, enemy.y, knot.x, knot.y) >= reach || isPhasedPhantom(enemy)) continue
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
        if (!enemy.alive) continue
        val reach = plume + enemy.radius
        if (distanceSquared(enemy.x, enemy.y, vent.x, vent.y) >= reach * reach || isPhasedPhantom(enemy)) continue
        applyStatus(enemy, StatusElement.THERMAL, 1, generation = 0)
    }
}

/** Thrown by an impact, or by the Dipole's polarity. */
private fun MutableGameState.isThrownEnemy(enemy: Enemy): Boolean =
    enemy.thrownUntil > elapsed || isThrownByPolarity(enemy)

private fun MutableGameState.anyLiveThrownEnemy(): Boolean {
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (enemy.alive && isThrownEnemy(enemy)) return true
    }
    return false
}

private fun MutableGameState.fuseMineUnderThrownEnemies(index: Int, mine: Obstacle) {
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || !isThrownEnemy(enemy)) continue
        val reach = mine.radius + enemy.radius
        if (distanceSquared(enemy.x, enemy.y, mine.x, mine.y) >= reach * reach || isPhasedPhantom(enemy)) continue
        quickFuseMine(index)
        return
    }
}
