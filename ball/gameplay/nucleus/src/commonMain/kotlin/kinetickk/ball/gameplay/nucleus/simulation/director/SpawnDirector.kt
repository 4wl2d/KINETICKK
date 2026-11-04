// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.director

import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.PACK_HUNTERS_PACK_SIZE_BONUS
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
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
    if (inBaseLayer() && elapsed >= nextEliteAt) {
        nextEliteAt += rebirthProfile.eliteInterval(content.tempo.eliteIntervalSeconds(elapsed)) * jitter(0.75f, 1.25f)
        if (spawnScheduled(EnemyType.ELITE)) {
            message = eliteSignalNotice()
            messageTime = 1.4f
        }
    }
    val heraldDue = when (heraldsSpawned) {
        0 -> runMutatorActive(RebirthMutator.HARDENED_ELITES) && elapsed >= FIRST_HERALD_AT_SECONDS
        1 -> runMutatorActive(RebirthMutator.OVERCLOCKED_SWARM) && elapsed >= SECOND_HERALD_AT_SECONDS
        else -> false
    }
    if (inBaseLayer() && heraldDue && content.enemy(EnemyType.HERALD).minimumRebirth <= rebirthLevel) {
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
 * Run when a room's seal or its end switches [ambientHabitat]: the planned event's fixed kind was picked under the
 * habitat before, so one with no weight in the habitat now gives way to a kind of this habitat, Swarmlings left out,
 * from a hash of the run seed and the event count. The event keeps its kind and size, and no stream is drawn.
 */
internal fun MutableGameState.keepPendingEventInHabitat() {
    val typeIndex = pendingEventType
    if (typeIndex < 0) return
    val habitat = ambientHabitat()
    if (content.enemy(EnemyType.entries[typeIndex]).weightIn(habitat) > 0f) return
    val roll = hashUnit(runHash(runSeed, EVENT_HABITAT_SALT, (spawnEventCount.toLong() shl 8) or habitat.ordinal.toLong()))
    pendingEventType = pickEnemyType(excludeSwarmling = true, unitRoll = roll).ordinal
}

/**
 * The tier's extra pack members, raised to Pack Hunters' when the run carries that mutator without the tier (an omen
 * adds it), so the mutator's larger packs come with its more frequent ones ([rollEventKind]).
 */
private fun MutableGameState.rebirthPackBonus(): Int =
    if (runMutatorActive(RebirthMutator.PACK_HUNTERS)) max(rebirthProfile.packSizeBonus, PACK_HUNTERS_PACK_SIZE_BONUS)
    else rebirthProfile.packSizeBonus

/**
 * Chooses the next event's kind and size; SINGLE and PACK also fix their type, promoting Swarmlings to a
 * SWARM. Every other kind leaves Swarmlings out, so an event's budget and cap unit always match its members.
 */
internal fun MutableGameState.planSpawnEvent() {
    var kind = rollEventKind()
    var type = -1
    var size = when (kind) {
        SpawnEventKind.SINGLE -> 1
        SpawnEventKind.PACK -> rangeInclusive(3, 6) + rebirthPackBonus() + personalityPackBonus()
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
    single *= personalityEventScale(SpawnEventKind.SINGLE)
    pack *= personalityEventScale(SpawnEventKind.PACK)
    flank *= personalityEventScale(SpawnEventKind.FLANK)
    swarm *= personalityEventScale(SpawnEventKind.SWARM)
    wall *= personalityEventScale(SpawnEventKind.WALL)
    encircle *= personalityEventScale(SpawnEventKind.ENCIRCLE)
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

internal fun typeBit(type: EnemyType): Long = 1L shl type.ordinal

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
internal fun MutableGameState.spawnMember(type: EnemyType, x: Float, y: Float, beyondView: Boolean = true) {
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
    if (structureEngagementActive()) EnemyHabitat.STRUCTURE else worldLayer.habitat

/**
 * Leaders may be left several screens behind during an ordinary retreat. Six thousand units is far travel to the
 * distant sites and structures: beyond it, their slow steering cannot bring them back into the fight in useful time.
 * Their leash also stays at least as far away as the ordinary viewport-scaled leash.
 */
internal const val FAR_LEADER_LEASH_DISTANCE: Float = 6_000f

/** Stateless leader relocation: returning a leader never shifts the ordinary director's spawn stream. */
private const val LEADER_LEASH_SALT: Long = 0x4C45_4144_4C45_4153L

/**
 * A leashed enemy is moved, integrity intact, back to spawn distance ahead or on a flank; the move is a teleport, so
 * its previous position follows. Roaming Elites and Heralds use a stable per-enemy hash; ordinary enemies keep their
 * existing spawn draws.
 */
internal fun MutableGameState.relocateLeashedEnemy(enemy: Enemy) {
    val leader = enemy.type == EnemyType.ELITE || enemy.type == EnemyType.HERALD
    val hash = if (leader) runHash(runSeed, LEADER_LEASH_SALT, enemy.id.toLong()) else 0L
    val offset = if (leader) {
        val side = if (hash and 1L == 0L) 1f else -1f
        side * (AHEAD_HALF_ANGLE + hashUnit(hash) * (FLANK_OUTER_ANGLE - AHEAD_HALF_ANGLE))
    } else sectorOffset(allowBehind = false)
    val rolled = movementAngle() + offset
    val overscanRoll = if (leader) hashUnit(splitMix64(hash)) else spawnRandom.nextFloat()
    // An offered site may turn the chosen angle. The diagonal keeps a leader outside the viewport at every angle,
    // while clearOfAnomalies checks the same distance that placement uses.
    val viewDistance = if (leader) length(screenWidth, screenHeight) * 0.5f else viewportEdgeDistance(rolled)
    val distance = viewDistance + 80f + overscanRoll * 180f
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
    if (leader) {
        // A rock nudge must not return a roaming leader to the sealed room it was ejected from.
        val outside = structureEjected(x, y, enemy.radius)
        x = outside.x
        y = outside.y
    }
    enemy.x = x
    enemy.y = y
    enemy.previousX = x
    enemy.previousY = y
    enemy.vx = 0f
    enemy.vy = 0f
    onLeashedEnemyRelocated(enemy)
}

/** Place-bound enemies and bosses stay; the roaming Elite and Herald instead have a far-travel leash. */
internal fun MutableGameState.leashExempt(enemy: Enemy): Boolean =
    (enemy.isEliteClass && enemy.type != EnemyType.ELITE && enemy.type != EnemyType.HERALD) || isPlaceBound(enemy)
