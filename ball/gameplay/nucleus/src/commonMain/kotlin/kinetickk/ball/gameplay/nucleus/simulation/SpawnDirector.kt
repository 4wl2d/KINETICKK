// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The spawn director replaces fixed-interval single spawns. A pacing phase sets how fast spawn budget
 * accrues; the director plans one event (kind, size and, for single-type events, the type), fires it once
 * the budget covers it and spends what it spawned. Every draw comes from spawnRandom.
 */

/** Pacing phases: CALM opens the run, then BUILD, SURGE and BREATHER loop. */
internal enum class SpawnDirectorPhase(val rate: Float, val minimumSeconds: Float, val maximumSeconds: Float) {
    CALM(0.6f, 10f, 16f),
    BUILD(1.0f, 18f, 30f),
    SURGE(1.8f, 8f, 14f),
    BREATHER(0.35f, 5f, 9f),
}

internal enum class SpawnEventKind { SINGLE, PACK, FLANK, SWARM, WALL, ENCIRCLE, AMBUSH }

/** An ambush waits for the Core to hold this share of the obstacle damage-start speed. */
internal const val AMBUSH_SPEED_FRACTION: Float = 0.75f
internal const val AMBUSH_HOLD_SECONDS: Float = 1.5f
internal const val AMBUSH_COOLDOWN_SECONDS: Float = 20f
internal const val ENCIRCLE_COOLDOWN_SECONDS: Float = 40f
internal const val WALL_COOLDOWN_SECONDS: Float = 30f
internal const val CAP_OVERFLOW_COOLDOWN_SECONDS: Float = 20f
internal const val DIRECTOR_CHECK_SECONDS: Float = 0.4f
/**
 * Overall budget scale: at 1 the director banks one enemy per tempo interval at phase rate 1. The tuning
 * knob for the no-POI reference bot at tier 0 (its win rate over 40 seeds stays at the 0.3.x level).
 */
internal const val DIRECTOR_RATE_SCALE: Float = 1f
/** An event never fires below this share of its planned size. */
internal const val MINIMUM_EVENT_SHARE: Float = 0.6f
/** A Swarmling fills this share of an ordinary cap slot and of the spawn budget. */
internal const val SWARMLING_CAP_COST: Float = 1f / 3f
internal const val MAX_SPAWN_BUDGET: Float = 14f
/** A type's weight ramps in over this long after its gate opens. */
internal const val TYPE_RAMP_SECONDS: Float = 60f
private const val MINIMUM_TYPE_RAMP: Float = 0.08f
/** Hunter Protocol opens the Sniper and Lancer gates this much earlier. */
internal const val HUNTER_PROTOCOL_GATE_SHIFT_SECONDS: Float = 90f
internal const val FIRST_HERALD_AT_SECONDS: Float = 360f
internal const val SECOND_HERALD_AT_SECONDS: Float = 540f
internal const val HERALD_ESCORTS: Int = 2
internal const val HARDENED_ELITE_ESCORTS: Int = 2
internal const val HARDENED_ELITE_INTEGRITY: Float = 1.5f
/** Overclocked Swarm: every hostile attack cadence runs this much faster. */
internal const val OVERCLOCKED_ATTACK_RATE: Float = 1.25f
private const val BEHIND_REROLL_SPEED: Float = 700f
private const val DEGREES: Float = (kotlin.math.PI / 180.0).toFloat()
private const val AHEAD_HALF_ANGLE: Float = 50f * DEGREES
private const val FLANK_OUTER_ANGLE: Float = 130f * DEGREES

internal fun MutableGameState.resetSpawnDirector() {
    directorPhase = SpawnDirectorPhase.CALM.ordinal
    directorPhaseUntil = phaseDuration(SpawnDirectorPhase.CALM)
    spawnBudget = 0f
    pendingEventKind = -1
    pendingEventSize = 0
    pendingEventType = -1
    recentEventTypes = 0L
    previousEventTypes = 0L
    ambushReadyAt = AMBUSH_COOLDOWN_SECONDS
    encircleReadyAt = 0f
    wallReadyAt = 0f
    capOverflowReadyAt = 0f
    ambushSpeedSeconds = 0f
    spawnEventCount = 0
    lastSpawnEventKind = -1
    lastSpawnEventPlanned = 0
    lastSpawnEventSpawned = 0
    heraldsSpawned = 0
    secretBossMask = 0L
    encounters = kinetickk.foundation.collections.immutableListOf()
}

/** Every hostile attack cadence: run growth of the attack rate, sped up by Overclocked Swarm. */
internal fun MutableGameState.hostileAttackRate(): Float =
    content.tempo.attackRateMultiplier(threatElapsed) *
        if (runMutatorActive(RebirthMutator.OVERCLOCKED_SWARM)) OVERCLOCKED_ATTACK_RATE else 1f

/** Damage of one hostile projectile [enemy] fires now. */
internal fun MutableGameState.shotDamage(enemy: Enemy): Float = hostileDamage(enemy, enemy.type.profile.shotDamage)

internal fun MutableGameState.spawnWave(delta: Float) {
    spawnPendingEscorts()
    // During the fight ordinary adds keep arriving, thinner and slower, only while a live Architect is engaged.
    val bossFight = bossSpawned
    if (bossFight) {
        val boss = liveArchitect() ?: return
        if (!architectEngaged(boss)) return
    }
    advanceDirectorPhase()
    if (speed > obstacleRicochetSpeed() * AMBUSH_SPEED_FRACTION) ambushSpeedSeconds += delta else ambushSpeedSeconds = 0f
    val phase = SpawnDirectorPhase.entries[directorPhase]
    // The Rebirth interval keeps the published minimum spawn interval as the fastest one-enemy cadence.
    var rate = phase.rate * DIRECTOR_RATE_SCALE / rebirthProfile.spawnInterval(content.tempo.spawnIntervalSeconds(elapsed)) *
        personalityRateScale(phase) * personalityBudgetScale() * layerSpawnRateMultiplier() * structureDirectorScale()
    if (bossFight) rate /= ARCHITECT_ADD_INTERVAL_SCALE
    spawnBudget = min(MAX_SPAWN_BUDGET, spawnBudget + delta * rate)
    spawnClock -= delta
    if (spawnClock <= 0f) {
        spawnClock = DIRECTOR_CHECK_SECONDS * jitter(0.55f, 1.45f)
        if (ambushReady()) planAmbush() else if (pendingEventKind < 0) planSpawnEvent()
        tryFireSpawnEvent(bossFight)
    }
    if (bossFight) return
    if (layerFeatures().elites && elapsed >= nextEliteAt) {
        nextEliteAt += rebirthProfile.eliteInterval(content.tempo.eliteIntervalSeconds(elapsed)) * jitter(0.75f, 1.25f)
        if (spawnScheduled(EnemyType.ELITE)) {
            message = "ELITE SIGNAL"
            messageTime = 1.4f
        }
    }
    val heraldDue = when (heraldsSpawned) {
        0 -> runMutatorActive(RebirthMutator.HARDENED_ELITES) && elapsed >= FIRST_HERALD_AT_SECONDS
        1 -> runMutatorActive(RebirthMutator.OVERCLOCKED_SWARM) && elapsed >= SECOND_HERALD_AT_SECONDS
        else -> false
    }
    if (layerFeatures().heralds && heraldDue && content.enemy(EnemyType.HERALD).minimumRebirth <= rebirthLevel) {
        heraldsSpawned++
        if (enemies.size >= content.rebirth.maxActiveEnemies) evictOldestOrdinaryEnemy()
        if (spawnScheduled(EnemyType.HERALD)) {
            message = "HERALD APPROACHES"
            messageTime = 2.5f
        }
    }
}

/** The run opening: the opening count split into two or three small packs of first-second types. */
internal fun MutableGameState.spawnOpeningPacks() {
    val total = rebirthProfile.openingEnemyCount
    if (total <= 0) return
    val packs = min(total, 2 + spawnRandom.nextInt(2))
    var remaining = total
    val heading = movementAngle()
    for (pack in 0 until packs) {
        val size = remaining / (packs - pack)
        remaining -= size
        val type = pickEnemyType(weakOnly = true, excludeSwarmling = true, personality = false)
        val angle = heading + sectorOffset()
        val distance = viewportEdgeDistance(angle) + 80f + spawnRandom.nextFloat() * 180f
        spawnCluster(type, size, coreX + cos(angle) * distance, coreY + sin(angle) * distance)
    }
}

private fun MutableGameState.advanceDirectorPhase() {
    if (elapsed < directorPhaseUntil) return
    val next = when (SpawnDirectorPhase.entries[directorPhase]) {
        SpawnDirectorPhase.CALM, SpawnDirectorPhase.BREATHER -> SpawnDirectorPhase.BUILD
        SpawnDirectorPhase.BUILD -> SpawnDirectorPhase.SURGE
        SpawnDirectorPhase.SURGE -> SpawnDirectorPhase.BREATHER
    }
    directorPhase = next.ordinal
    directorPhaseUntil = elapsed + phaseDuration(next)
    onDirectorPhaseStarted(next)
}

private fun MutableGameState.phaseDuration(phase: SpawnDirectorPhase): Float =
    (phase.minimumSeconds + spawnRandom.nextFloat() * (phase.maximumSeconds - phase.minimumSeconds)) *
        personalityDurationScale(phase)

private fun MutableGameState.jitter(minimum: Float, maximum: Float): Float =
    minimum + spawnRandom.nextFloat() * (maximum - minimum)

private fun MutableGameState.rangeInclusive(minimum: Int, maximum: Int): Int =
    minimum + spawnRandom.nextInt(maximum - minimum + 1)

private fun MutableGameState.ambushReady(): Boolean =
    ambushSpeedSeconds >= AMBUSH_HOLD_SECONDS && elapsed >= ambushReadyAt &&
        pendingEventKind != SpawnEventKind.AMBUSH.ordinal

private fun MutableGameState.planAmbush() {
    val type = pickEnemyType(excludeSwarmling = true)
    pendingEventKind = SpawnEventKind.AMBUSH.ordinal
    pendingEventSize = rangeInclusive(3, 5)
    pendingEventType = type.ordinal
    ambushReadyAt = elapsed + AMBUSH_COOLDOWN_SECONDS * ambushCooldownScale()
}

/**
 * Chooses the next event's kind and size; SINGLE and PACK also fix their type, promoting Swarmlings to a
 * SWARM. Every other kind leaves Swarmlings out, so an event's budget and cap unit always match its members.
 */
internal fun MutableGameState.planSpawnEvent() {
    var kind = rollEventKind()
    var type = -1
    var size = when (kind) {
        SpawnEventKind.SINGLE -> 1
        SpawnEventKind.PACK -> rangeInclusive(3, 6) + rebirthProfile.packSizeBonus + personalityPackBonus()
        SpawnEventKind.FLANK -> 2 * rangeInclusive(2, 3)
        SpawnEventKind.SWARM -> rangeInclusive(6, 10)
        SpawnEventKind.WALL -> rangeInclusive(5, 7)
        SpawnEventKind.ENCIRCLE -> rangeInclusive(6, 10)
        SpawnEventKind.AMBUSH -> rangeInclusive(3, 5)
    }
    when (kind) {
        SpawnEventKind.SINGLE, SpawnEventKind.PACK -> {
            val picked = pickEnemyType()
            if (picked == EnemyType.SWARMLING) {
                kind = SpawnEventKind.SWARM
                size = rangeInclusive(6, 10)
            }
            type = picked.ordinal
        }
        SpawnEventKind.SWARM -> type = EnemyType.SWARMLING.ordinal
        SpawnEventKind.WALL, SpawnEventKind.AMBUSH -> type = pickEnemyType(excludeSwarmling = true).ordinal
        SpawnEventKind.FLANK, SpawnEventKind.ENCIRCLE -> Unit
    }
    pendingEventKind = kind.ordinal
    pendingEventSize = size
    pendingEventType = type
}

private fun MutableGameState.rollEventKind(): SpawnEventKind {
    val phase = SpawnDirectorPhase.entries[directorPhase]
    val swarmReady = typeWeight(content.enemy(EnemyType.SWARMLING), weakOnly = false, recency = false) > 0f
    val packWeight = if (runMutatorActive(RebirthMutator.PACK_HUNTERS)) 1.5f else 1f
    var single = 0f
    var pack = 0f
    var flank = 0f
    var swarm = 0f
    var wall = 0f
    var encircle = 0f
    when (phase) {
        SpawnDirectorPhase.CALM, SpawnDirectorPhase.BREATHER -> {
            single = 70f
            pack = 30f * packWeight
        }
        SpawnDirectorPhase.BUILD -> {
            single = 35f
            pack = 30f * packWeight
            flank = 15f
            swarm = if (swarmReady) 10f else 0f
            wall = if (elapsed >= wallReadyAt) 10f else 0f
        }
        SpawnDirectorPhase.SURGE -> {
            pack = 25f * packWeight
            swarm = if (swarmReady) 20f else 0f
            flank = 20f
            encircle = if (elapsed >= encircleReadyAt) 20f else 0f
            wall = if (elapsed >= wallReadyAt) 15f else 0f
        }
    }
    single *= personalityEventScale(SpawnEventKind.SINGLE.ordinal)
    pack *= personalityEventScale(SpawnEventKind.PACK.ordinal)
    flank *= personalityEventScale(SpawnEventKind.FLANK.ordinal)
    swarm *= personalityEventScale(SpawnEventKind.SWARM.ordinal)
    wall *= personalityEventScale(SpawnEventKind.WALL.ordinal)
    encircle *= personalityEventScale(SpawnEventKind.ENCIRCLE.ordinal)
    var roll = spawnRandom.nextFloat() * (single + pack + flank + swarm + wall + encircle)
    roll -= single
    if (roll < 0f) return SpawnEventKind.SINGLE
    roll -= pack
    if (roll < 0f) return SpawnEventKind.PACK
    roll -= flank
    if (roll < 0f) return SpawnEventKind.FLANK
    roll -= swarm
    if (roll < 0f) return SpawnEventKind.SWARM
    roll -= wall
    if (roll < 0f) return SpawnEventKind.WALL
    return if (encircle > 0f) SpawnEventKind.ENCIRCLE else SpawnEventKind.PACK
}

/** Share of an ordinary cap slot [type] fills. */
internal fun capCost(type: EnemyType): Float = if (type == EnemyType.SWARMLING) SWARMLING_CAP_COST else 1f

/**
 * The ordinary cap: Elites count, the boss class (the Architect, mini and secret bosses) and encounter members do not;
 * Swarmlings fill a third of a slot.
 */
internal fun MutableGameState.ordinaryCapLoad(): Float {
    var load = 0f
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.dead || enemy.isBossClass || enemy.ownerTag != 0L) continue
        load += capCost(enemy.type)
    }
    return load
}

internal fun MutableGameState.ordinaryEnemyCap(bossFight: Boolean = bossSpawned): Float {
    val cap = (min(content.rebirth.maxActiveEnemies, rebirthProfile.enemyCap(content.tempo.ordinaryEnemyCap(elapsed))) *
        layerOrdinaryCapMultiplier()).toInt()
    return if (bossFight) (cap * ARCHITECT_ADD_CAP_SHARE).toInt().toFloat() else cap.toFloat()
}

private fun MutableGameState.tryFireSpawnEvent(bossFight: Boolean) {
    val kindIndex = pendingEventKind
    if (kindIndex < 0) return
    val kind = SpawnEventKind.entries[kindIndex]
    val size = pendingEventSize
    val unit = if (pendingEventType >= 0) capCost(EnemyType.entries[pendingEventType]) else 1f
    val cost = size * unit
    if (spawnBudget < cost) return
    val room = ordinaryEnemyCap(bossFight) - ordinaryCapLoad()
    val minimumCount = ceil(size * MINIMUM_EVENT_SHARE).toInt()
    var count = size
    if (cost > room + 1e-4f) {
        val fit = floor((room + 1e-4f) / unit).toInt()
        if (fit >= minimumCount) {
            count = fit
        } else if (!bossFight && capOverflowReadyAt <= elapsed && enemies.size + size <= content.rebirth.maxActiveEnemies) {
            capOverflowReadyAt = elapsed + CAP_OVERFLOW_COOLDOWN_SECONDS
        } else {
            // Too little room for this event: plan a smaller one rather than cutting it below 60 %.
            if (kind != SpawnEventKind.SINGLE && room >= 1f) {
                pendingEventKind = SpawnEventKind.SINGLE.ordinal
                pendingEventSize = 1
                pendingEventType = pickEnemyType(excludeSwarmling = true).ordinal
            }
            return
        }
    }
    count = min(count, content.rebirth.maxActiveEnemies - enemies.size)
    if (count < minimumCount || count <= 0) return
    val typesUsed = spawnEvent(kind, count)
    spawnBudget = max(0f, spawnBudget - count * unit)
    when (kind) {
        SpawnEventKind.WALL -> wallReadyAt = elapsed + WALL_COOLDOWN_SECONDS * wallCooldownScale()
        SpawnEventKind.ENCIRCLE -> encircleReadyAt = elapsed + ENCIRCLE_COOLDOWN_SECONDS * encircleCooldownScale()
        else -> Unit
    }
    previousEventTypes = recentEventTypes
    recentEventTypes = typesUsed
    spawnEventCount++
    lastSpawnEventKind = kind.ordinal
    lastSpawnEventPlanned = size
    lastSpawnEventSpawned = count
    pendingEventKind = -1
    pendingEventSize = 0
    pendingEventType = -1
}

/**
 * Places [count] members of the pending event; returns the bit mask of the types it spawned. Only SWARM
 * spawns Swarmlings; the Warden cap holds for every member, not only for the pick.
 */
private fun MutableGameState.spawnEvent(kind: SpawnEventKind, count: Int): Long {
    val heading = movementAngle()
    val fixedType = if (pendingEventType >= 0) EnemyType.entries[pendingEventType] else null
    return when (kind) {
        SpawnEventKind.SINGLE, SpawnEventKind.PACK, SpawnEventKind.SWARM -> {
            val type = fixedType ?: pickEnemyType(excludeSwarmling = true)
            val rolled = heading + sectorOffset()
            val distance = viewportEdgeDistance(rolled) + 80f + spawnRandom.nextFloat() * 180f
            val angle = clearOfAnomalies(rolled, distance)
            spawnCluster(type, count, coreX + cos(angle) * distance, coreY + sin(angle) * distance)
        }
        SpawnEventKind.AMBUSH -> {
            val type = fixedType ?: pickEnemyType(excludeSwarmling = true)
            val angle = heading + (spawnRandom.nextFloat() * 2f - 1f) * 25f * DEGREES
            val distance = viewportEdgeDistance(angle) + 80f + spawnRandom.nextFloat() * 80f
            spawnCluster(type, count, coreX + cos(angle) * distance, coreY + sin(angle) * distance)
        }
        SpawnEventKind.FLANK -> {
            val first = (count + 1) / 2
            var mask = 0L
            for (side in 0 until 2) {
                val type = pickEnemyType(excludeSwarmling = true)
                val sign = if (side == 0) 1f else -1f
                val rolled = heading + sign * (70f + spawnRandom.nextFloat() * 40f) * DEGREES
                val distance = viewportEdgeDistance(rolled) + 80f + spawnRandom.nextFloat() * 180f
                val angle = clearOfAnomalies(rolled, distance)
                val members = if (side == 0) first else count - first
                mask = mask or spawnCluster(type, members, coreX + cos(angle) * distance, coreY + sin(angle) * distance)
            }
            mask
        }
        SpawnEventKind.WALL -> {
            val type = fixedType ?: pickEnemyType(excludeSwarmling = true)
            val distance = viewportEdgeDistance(heading) + 120f + spawnRandom.nextFloat() * 80f
            val centerX = coreX + cos(heading) * distance
            val centerY = coreY + sin(heading) * distance
            val tangentX = -sin(heading)
            val tangentY = cos(heading)
            val spacing = 64f + type.profile.radius * 1.6f
            var mask = 0L
            for (index in 0 until count) {
                val lateral = (index - (count - 1) * 0.5f) * spacing
                val member = withinWardenCap(type)
                spawnMember(member, centerX + tangentX * lateral, centerY + tangentY * lateral)
                mask = mask or typeBit(member)
            }
            mask
        }
        SpawnEventKind.ENCIRCLE -> {
            val first = pickEnemyType(weakOnly = true, excludeSwarmling = true)
            val second = pickEnemyType(weakOnly = true, excludeSwarmling = true)
            val radius = sqrt(screenWidth * screenWidth + screenHeight * screenHeight) * 0.5f + 90f
            val phase = spawnRandom.nextFloat() * TAU
            for (index in 0 until count) {
                val angle = phase + index * TAU / count
                spawnMember(if (index % 2 == 0) first else second, coreX + cos(angle) * radius, coreY + sin(angle) * radius)
            }
            typeBit(first) or typeBit(second)
        }
    }
}

private fun typeBit(type: EnemyType): Long = 1L shl type.ordinal

/**
 * Members on a golden-angle spiral around the anchor, so a pack reads as one group without overlapping;
 * members past the Warden cap take the Warden's stand-in. Returns the bit mask of the types spawned.
 */
private fun MutableGameState.spawnCluster(type: EnemyType, count: Int, anchorX: Float, anchorY: Float): Long {
    val spread = 14f + type.profile.radius * 1.4f
    var mask = 0L
    for (index in 0 until count) {
        val radius = spread * sqrt(index.toFloat())
        val angle = index * GOLDEN_ANGLE
        val member = withinWardenCap(type)
        spawnMember(member, anchorX + cos(angle) * radius, anchorY + sin(angle) * radius)
        mask = mask or typeBit(member)
    }
    return mask
}

private const val GOLDEN_ANGLE: Float = 2.399963f

/**
 * Adds one enemy at least [MINIMUM_SPAWN_OVERSCAN] beyond the viewport edge, nudged outward from the Core
 * while the point overlaps an obstacle.
 */
private fun MutableGameState.spawnMember(type: EnemyType, x: Float, y: Float, beyondView: Boolean = true) {
    if (enemies.size >= content.rebirth.maxActiveEnemies) return
    var spawnX = x
    var spawnY = y
    val outwardX = x - coreX
    val outwardY = y - coreY
    val outward = length(outwardX, outwardY)
    val minimum = viewportEdgeDistance(kotlin.math.atan2(outwardY, outwardX)) + MINIMUM_SPAWN_OVERSCAN
    if (beyondView && outward < minimum) {
        val scale = if (outward > 1f) minimum / outward else 1f
        spawnX = if (outward > 1f) coreX + outwardX * scale else coreX + minimum
        spawnY = if (outward > 1f) coreY + outwardY * scale else coreY
    }
    val radius = type.profile.radius
    val ejected = structureEjected(spawnX, spawnY, radius)
    spawnX = ejected.x
    spawnY = ejected.y
    var attempts = 0
    while (attempts < SPAWN_NUDGE_ATTEMPTS && isSpawnPointBlocked(spawnX, spawnY, radius)) {
        val dx = spawnX - coreX
        val dy = spawnY - coreY
        val distance = max(1f, length(dx, dy))
        spawnX += dx / distance * SPAWN_NUDGE_DISTANCE
        spawnY += dy / distance * SPAWN_NUDGE_DISTANCE
        attempts++
    }
    enemies += spawnedEnemy(type, spawnX, spawnY)
}

/** No spawn lands closer than this beyond the viewport edge, so nothing appears inside the view. */
internal const val MINIMUM_SPAWN_OVERSCAN: Float = 40f
private const val SPAWN_NUDGE_ATTEMPTS: Int = 6
private const val SPAWN_NUDGE_DISTANCE: Float = 60f

/**
 * Direction of a spawn relative to the heading: ahead within 50 degrees 35 %, the flanks 45 %, behind
 * 20 % (behind becomes a flank while the Core is fast), uniform inside the sector.
 */
internal fun MutableGameState.sectorOffset(allowBehind: Boolean = true): Float {
    val roll = spawnRandom.nextFloat()
    val side = if (spawnRandom.nextFloat() < 0.5f) 1f else -1f
    val within = spawnRandom.nextFloat()
    val behind = allowBehind && roll >= 0.8f && speed <= BEHIND_REROLL_SPEED
    return when {
        roll < 0.35f -> (within * 2f - 1f) * AHEAD_HALF_ANGLE
        behind -> side * (FLANK_OUTER_ANGLE + within * (TAU * 0.5f - FLANK_OUTER_ANGLE))
        else -> side * (AHEAD_HALF_ANGLE + within * (FLANK_OUTER_ANGLE - AHEAD_HALF_ANGLE))
    }
}

/**
 * Turns a spawn direction away from offered anomalies: an anchor never lands within
 * [POI_SPAWN_CLEARANCE] of a point the player has not entered, so packs do not gather on its circle.
 */
internal fun MutableGameState.clearOfAnomalies(angle: Float, distance: Float): Float {
    if (sites.isEmpty()) return angle
    var candidate = angle
    for (attempt in 0 until 4) {
        val x = coreX + cos(candidate) * distance
        val y = coreY + sin(candidate) * distance
        var blocked = false
        for (index in sites.indices) {
            val point = sites[index]
            if (point.active) continue
            val dx = x - point.x
            val dy = y - point.y
            if (dx * dx + dy * dy < POI_SPAWN_CLEARANCE * POI_SPAWN_CLEARANCE) blocked = true
        }
        if (!blocked) return candidate
        candidate = angle + ANOMALY_TURN * (if (attempt % 2 == 0) (attempt / 2 + 1) else -(attempt / 2 + 1))
    }
    return candidate
}

internal const val POI_SPAWN_CLEARANCE: Float = 650f
private const val ANOMALY_TURN: Float = 45f * DEGREES

/** Distance from the Core to the viewport edge along [angle]. */
internal fun MutableGameState.viewportEdgeDistance(angle: Float): Float {
    val toVertical = screenWidth * 0.5f / max(0.001f, abs(cos(angle)))
    val toHorizontal = screenHeight * 0.5f / max(0.001f, abs(sin(angle)))
    return min(toVertical, toHorizontal)
}

/** The habitat ambient picks use: a structure interior, else the world layer's own. */
internal fun MutableGameState.ambientHabitat(): EnemyHabitat =
    if (structureInteriorActive()) EnemyHabitat.STRUCTURE else worldLayer.habitat

/**
 * Ordinary spawn weight of [definition] in [habitat] at [threatSeconds]: zero before its time or Rebirth gate, ramping
 * in over [TYPE_RAMP_SECONDS], halved when the last two events used it ([recency]), then scaled by the director
 * personality and the onslaught theme ([personality]).
 */
internal fun MutableGameState.typeWeight(
    definition: EnemyDefinition,
    weakOnly: Boolean,
    recency: Boolean = true,
    habitat: EnemyHabitat = ambientHabitat(),
    threatSeconds: Float = threatElapsed,
    personality: Boolean = true,
): Float {
    if (definition.isScheduled || definition.minimumRebirth > rebirthLevel) return 0f
    if (weakOnly && definition.threat > 2) return 0f
    val type = definition.type
    val hunterShift = if (
        runMutatorActive(RebirthMutator.HUNTER_PROTOCOL) && (type == EnemyType.SNIPER || type == EnemyType.LANCER)
    ) HUNTER_PROTOCOL_GATE_SHIFT_SECONDS else 0f
    val gate = max(0f, definition.timeGateSeconds - hunterShift)
    val threat = threatSeconds
    if (threat < gate) return 0f
    val ramp = clamp((threat - gate) / TYPE_RAMP_SECONDS, MINIMUM_TYPE_RAMP, 1f)
    var weight = definition.baseWeight(threat, habitat) * ramp
    if (weight <= 0f) return 0f
    if (recency && ((recentEventTypes or previousEventTypes) and typeBit(type)) != 0L) weight *= 0.5f
    if (personality) weight *= personalityTypeMultiplier(type) * surgeThemeMultiplier(type)
    return weight
}

/** Weighted type pick over the eligible roster; Wardens past their cap become Weavers. */
internal fun MutableGameState.pickEnemyType(
    weakOnly: Boolean = false,
    excludeSwarmling: Boolean = false,
    personality: Boolean = true,
): EnemyType {
    val roster = content.enemies
    var total = 0f
    for (index in roster.indices) {
        val definition = roster[index]
        if (excludeSwarmling && definition.type == EnemyType.SWARMLING) continue
        total += typeWeight(definition, weakOnly, personality = personality)
    }
    if (total <= 0f) return EnemyType.DRIFTER
    var roll = spawnRandom.nextFloat() * total
    var picked = EnemyType.DRIFTER
    for (index in roster.indices) {
        val definition = roster[index]
        if (excludeSwarmling && definition.type == EnemyType.SWARMLING) continue
        val weight = typeWeight(definition, weakOnly, personality = personality)
        if (weight <= 0f) continue
        picked = definition.type
        roll -= weight
        if (roll < 0f) break
    }
    return withinWardenCap(picked)
}

/** Live Wardens the director allows at once: two, plus one per three Rebirth tiers, at most six. */
internal fun MutableGameState.wardenCap(): Int = 2 + min(4, rebirthLevel / 3)

/** [type], or its stand-in (a Weaver once eligible, otherwise a Drifter) while it is a Warden and the cap is full. */
private fun MutableGameState.withinWardenCap(type: EnemyType): EnemyType {
    if (type != EnemyType.WARDEN || countLive(EnemyType.WARDEN) < wardenCap()) return type
    return if (typeWeight(content.enemy(EnemyType.WEAVER), weakOnly = false, recency = false) > 0f) EnemyType.WEAVER else EnemyType.DRIFTER
}

private fun MutableGameState.countLive(type: EnemyType): Int {
    var count = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.dead && enemy.type == type) count++
    }
    return count
}

/** Spawns a scheduled enemy (Elite, Herald, Architect, escorts of the Architect) at spawn distance. */
internal fun MutableGameState.spawnScheduled(type: EnemyType): Boolean {
    if (enemies.size >= content.rebirth.maxActiveEnemies) return false
    val rolled = movementAngle() + sectorOffset(allowBehind = type != EnemyType.HERALD)
    val distance = viewportEdgeDistance(rolled) + 80f + spawnRandom.nextFloat() * 180f
    val angle = clearOfAnomalies(rolled, distance)
    spawnMember(type, coreX + cos(angle) * distance, coreY + sin(angle) * distance)
    return true
}

/**
 * The terminal and mini-bosses take priority over the oldest ordinary enemy at the hard cap; place-bound enemies (an
 * encounter's members, an active trial's defenders) stay, so the engagement can still be completed.
 */
internal fun MutableGameState.evictOldestOrdinaryEnemy() {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.isBossClass || enemy.isElite || isPlaceBound(enemy)) continue
        enemies.removeAt(index)
        return
    }
}

/**
 * Calls in escorts that Elites (Hardened Elites) and Heralds still owe: ordinary types eligible now that
 * share the leader's aspect and are not immune to it, placed around the leader, within the hard cap.
 */
internal fun MutableGameState.spawnPendingEscorts() {
    val leaders = enemies.size
    for (index in 0 until leaders) {
        val leader = enemies[index]
        if (leader.escortsPending <= 0 || leader.isBoss) continue
        val owed = leader.escortsPending
        leader.escortsPending = 0
        if (!leader.alive) continue
        for (escort in 0 until owed) {
            val type = aspectEscortType(leader.aspect, escort)
            val angle = escort * TAU / owed + elapsed
            val distance = leader.radius + type.profile.radius + 46f
            spawnMember(type, leader.x + cos(angle) * distance, leader.y + sin(angle) * distance, beyondView = false)
        }
    }
}

/**
 * The [index]th eligible escortable ordinary type sharing [aspect] without immunity, in roster order. An aspect no
 * ordinary type carries takes its eligible [aspectStandIn]; Drifters otherwise.
 */
internal fun MutableGameState.aspectEscortType(aspect: Int, index: Int): EnemyType {
    val roster = content.enemies
    var matches = 0
    for (rosterIndex in roster.indices) {
        val definition = roster[rosterIndex]
        val profile = definition.type.profile
        if (profile.aspect != aspect || aspect < 0 || profile.aspectImmune || !profile.escortable) continue
        if (typeWeight(definition, weakOnly = false, recency = false) > 0f) matches++
    }
    if (matches == 0) {
        val standIn = aspectStandIn(aspect) ?: return EnemyType.DRIFTER
        return if (typeWeight(content.enemy(standIn), weakOnly = false, recency = false) > 0f) standIn else EnemyType.DRIFTER
    }
    var target = index % matches
    for (rosterIndex in roster.indices) {
        val definition = roster[rosterIndex]
        val profile = definition.type.profile
        if (profile.aspect != aspect || profile.aspectImmune || !profile.escortable) continue
        if (typeWeight(definition, weakOnly = false, recency = false) <= 0f) continue
        if (target == 0) return definition.type
        target--
    }
    return EnemyType.DRIFTER
}

/**
 * The ordinary type that escorts in place of kin for an aspect no ordinary type carries (Sonic, Void), at
 * the Architect and for Elite and Herald escorts alike; null when ordinary types carry [aspect].
 */
internal fun aspectStandIn(aspect: Int): EnemyType? = when (aspect) {
    StatusElement.SONIC.ordinal -> EnemyType.CHARGER
    StatusElement.VOID.ordinal -> EnemyType.WEAVER
    else -> null
}

/** Uniform element ordinal for a new Elite's or Herald's aspect. */
internal fun MutableGameState.rollAspect(): Int = spawnRandom.nextInt(StatusElement.entries.size)

/**
 * A leashed ordinary enemy is moved, integrity intact, back to spawn distance ahead or on a flank; the
 * move is a teleport, so its previous position follows.
 */
internal fun MutableGameState.relocateLeashedEnemy(enemy: Enemy) {
    val rolled = movementAngle() + sectorOffset(allowBehind = false)
    val distance = viewportEdgeDistance(rolled) + 80f + spawnRandom.nextFloat() * 180f
    val angle = clearOfAnomalies(rolled, distance)
    val ejected = structureEjected(coreX + cos(angle) * distance, coreY + sin(angle) * distance, enemy.radius)
    var x = ejected.x
    var y = ejected.y
    var attempts = 0
    while (attempts < SPAWN_NUDGE_ATTEMPTS && isSpawnPointBlocked(x, y, enemy.radius)) {
        x += cos(angle) * SPAWN_NUDGE_DISTANCE
        y += sin(angle) * SPAWN_NUDGE_DISTANCE
        attempts++
    }
    enemy.x = x
    enemy.y = y
    enemy.previousX = x
    enemy.previousY = y
    enemy.vx = 0f
    enemy.vy = 0f
    onLeashedEnemyRelocated(enemy)
}

/** True when [enemy] stays wherever it is: scheduled leaders, the boss class and place-bound enemies. */
internal fun MutableGameState.leashExempt(enemy: Enemy): Boolean =
    enemy.isElite || enemy.isBossClass || isPlaceBound(enemy)
