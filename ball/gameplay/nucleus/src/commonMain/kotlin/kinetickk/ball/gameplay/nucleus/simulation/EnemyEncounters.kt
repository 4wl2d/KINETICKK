// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyNotices
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Encounter spawns for the lanes that own a place (sites, structure rooms, dimensions): a tagged group of enemies kept
 * inside its area, outside the ordinary cap and the leash, ended by its owner, by abandonment or by the Architect.
 * Spawning draws nothing from any run stream: kinds come from the owner's own roll and placement is geometric.
 */

internal const val MAX_ACTIVE_ENCOUNTERS: Int = 4
internal const val MAX_ENCOUNTER_MEMBERS: Int = 12
/** Live tagged enemies at once, every encounter together; reserved inside the hard enemy cap. */
internal const val MAX_ENCOUNTER_RESERVE: Int = 24
internal const val DEFAULT_ENCOUNTER_ABANDON_DISTANCE: Float = 2_400f
/** No secret boss is summoned later than this before the Architect. */
internal const val SECRET_BOSS_LAST_SUMMON_LEAD: Float = 90f
internal const val MAX_BOSS_ADDS_LIVE: Int = 4
/** An ember bursts as wide as a Volatile Remains burst. */
internal const val EMBER_RADIUS: Float = VOLATILE_BURST_RADIUS

private const val ENCOUNTER_NUDGE_ATTEMPTS: Int = 6
private const val ENCOUNTER_NUDGE_TURN: Float = 0.35f
private const val ENCOUNTER_NUDGE_DISTANCE: Float = 60f
private const val ENCOUNTER_GOLDEN_ANGLE: Float = 2.399963f
private const val BOSS_ADD_SPACING: Float = 50f

internal enum class EncounterOwner { SITE, ROOM, DIMENSION }
internal enum class EncounterPlacement { RING, CLUSTER }

/** Tag = owner kind in the top byte (1..3), the owner's own id in the low 56 bits; never 0. */
internal fun encounterTag(owner: EncounterOwner, id: Long): Long =
    ((owner.ordinal + 1).toLong() shl 56) or (id and 0x00FF_FFFF_FFFF_FFFFL)

internal fun encounterOwner(tag: Long): EncounterOwner? =
    EncounterOwner.entries.getOrNull(((tag ushr 56) and 0xFF).toInt() - 1)

internal class EncounterRequest(
    val tag: Long,
    /** Run-local centre. */
    val centerX: Float,
    val centerY: Float,
    /** Members stay inside (areaRadius − own radius). */
    val areaRadius: Float,
    val members: List<EnemyType>,
    val placement: EncounterPlacement = EncounterPlacement.RING,
    val ringRadius: Float = areaRadius * 0.55f,
    /** × the kind's spawn integrity. */
    val integrityShare: Float = 1f,
    val abandonDistance: Float = DEFAULT_ENCOUNTER_ABANDON_DISTANCE,
    /** Free hard-cap room by evicting the oldest untagged ordinary enemies. */
    val evictOrdinary: Boolean = false,
    /** A boss member sets the arrival banner. */
    val announce: Boolean = true,
    /** Run-local (x, y) per member in member order; when set it replaces the RING or CLUSTER geometry. */
    val positions: FloatArray? = null,
) {
    init {
        require(tag != 0L && encounterOwner(tag) != null) { "Encounter tags come from encounterTag" }
        require(areaRadius in 120f..2_400f) { "Encounter area radius must be in 120..2400" }
        require(members.size in 1..MAX_ENCOUNTER_MEMBERS) { "An encounter has 1..$MAX_ENCOUNTER_MEMBERS members" }
        require(ringRadius in 0f..areaRadius) { "Ring radius must lie inside the area" }
        require(integrityShare in 0.1f..2f) { "Integrity share must be in 0.1..2" }
        require(abandonDistance in areaRadius..6_000f) { "Abandon distance must be in areaRadius..6000" }
        require(positions == null || positions.size == members.size * 2) { "Positions hold one (x, y) per member" }
    }
}

/** One live encounter; the enemies lane writes it, owners read it. Positions rebase with the world. */
internal data class EncounterState(
    val tag: Long,
    val centerX: Float,
    val centerY: Float,
    val areaRadius: Float,
    val abandonDistance: Float,
    /** Members plus later adds and Splitter fragments. */
    val spawned: Int,
    /** Kills of tagged enemies; despawns never count. */
    val killed: Int,
    /** The mini or secret boss member's id, or −1. */
    val bossId: Int,
    val startedAt: Float,
) {
    fun shifted(shiftX: Float, shiftY: Float): EncounterState = copy(centerX = centerX - shiftX, centerY = centerY - shiftY)
}

private val EnemyType.encounterRank: EnemyRank get() = profile.rank

/** All members or none. Returns false, changing nothing, on any capacity refusal. */
internal fun MutableGameState.spawnEncounter(request: EncounterRequest): Boolean {
    val members = request.members
    require(members.none { it.encounterRank == EnemyRank.BOSS || it.encounterRank == EnemyRank.ELITE || it == EnemyType.HERALD }) {
        "Encounters never spawn the Architect, Elites or the Herald"
    }
    require(members.count { it.encounterRank == EnemyRank.MINI_BOSS || it.encounterRank == EnemyRank.SECRET_BOSS } <= 1) {
        "An encounter holds at most one boss"
    }
    if (phase != GamePhase.RUNNING || bossSpawned) return false
    if (encounterIndex(request.tag) >= 0) return false
    if (encounters.size >= MAX_ACTIVE_ENCOUNTERS) return false
    val secret = members.firstOrNull { it.encounterRank == EnemyRank.SECRET_BOSS }
    if (secret != null && (secretBossMask and (1L shl secret.ordinal)) != 0L) return false
    if (secret != null && elapsed > content.tempo.bossAtSeconds - SECRET_BOSS_LAST_SUMMON_LEAD) return false
    if (liveTaggedEnemies() + members.size > MAX_ENCOUNTER_RESERVE) return false
    val deficit = enemies.size + members.size - content.rebirth.maxActiveEnemies
    if (deficit > 0) {
        if (!request.evictOrdinary || evictableOrdinaryCount() < deficit) return false
        repeat(deficit) { evictOldestOrdinaryEnemy() }
    }
    val bossIndex = members.indexOfFirst {
        it.encounterRank == EnemyRank.MINI_BOSS || it.encounterRank == EnemyRank.SECRET_BOSS
    }
    val others = members.size - if (bossIndex >= 0) 1 else 0
    var bossId = -1
    var ringSlot = 0
    for (memberIndex in members.indices) {
        val type = members[memberIndex]
        val radius = type.profile.radius
        val positions = request.positions
        var x: Float
        var y: Float
        var ringAngle = Float.NaN
        when {
            positions != null -> {
                x = positions[memberIndex * 2]
                y = positions[memberIndex * 2 + 1]
            }
            memberIndex == bossIndex -> {
                x = request.centerX
                y = request.centerY
            }
            request.placement == EncounterPlacement.RING -> {
                ringAngle = TAU * ringSlot / others
                x = request.centerX + cos(ringAngle) * request.ringRadius
                y = request.centerY + sin(ringAngle) * request.ringRadius
            }
            else -> {
                val spread = 14f + radius * 1.4f
                val distance = spread * sqrt(ringSlot.toFloat())
                val angle = ringSlot * ENCOUNTER_GOLDEN_ANGLE
                x = request.centerX + cos(angle) * distance
                y = request.centerY + sin(angle) * distance
            }
        }
        if (memberIndex != bossIndex) ringSlot++
        var attempts = 0
        while (attempts < ENCOUNTER_NUDGE_ATTEMPTS && isSpawnPointBlocked(x, y, radius)) {
            if (!ringAngle.isNaN()) {
                ringAngle += ENCOUNTER_NUDGE_TURN
                x = request.centerX + cos(ringAngle) * request.ringRadius
                y = request.centerY + sin(ringAngle) * request.ringRadius
            } else {
                val dx = x - request.centerX
                val dy = y - request.centerY
                val distance = length(dx, dy)
                if (distance > 0.001f) {
                    x += dx / distance * ENCOUNTER_NUDGE_DISTANCE
                    y += dy / distance * ENCOUNTER_NUDGE_DISTANCE
                } else {
                    x += ENCOUNTER_NUDGE_DISTANCE
                }
            }
            attempts++
        }
        val limit = request.areaRadius - radius
        val dx = x - request.centerX
        val dy = y - request.centerY
        val distance = length(dx, dy)
        if (distance > limit && distance > 0.001f) {
            x = request.centerX + dx / distance * limit
            y = request.centerY + dy / distance * limit
        }
        val enemy = spawnedEnemy(type, x, y, ownerTag = request.tag, integrityShare = request.integrityShare)
        enemies += enemy
        if (memberIndex == bossIndex) bossId = enemy.id
    }
    encounters = (encounters + EncounterState(
        request.tag, request.centerX, request.centerY, request.areaRadius, request.abandonDistance,
        spawned = members.size, killed = 0, bossId = bossId, startedAt = elapsed,
    )).toImmutableList()
    if (secret != null) secretBossMask = secretBossMask or (1L shl secret.ordinal)
    if (request.announce && bossIndex >= 0) {
        val boss = members[bossIndex]
        val name = content.enemy(boss).name.uppercase()
        if (boss.encounterRank == EnemyRank.SECRET_BOSS) {
            message = EnemyNotices.SECRET_SIGNAL + " // " + name
            messageTime = 3f
        } else {
            message = name
            messageTime = 2.5f
        }
    }
    return true
}

/** The live encounter with [tag], or null when it is over (ended or lost). */
internal fun MutableGameState.encounter(tag: Long): EncounterState? {
    val index = encounterIndex(tag)
    return if (index >= 0) encounters[index] else null
}

/** Live members (alive, with the tag) of the encounter [tag]. */
internal fun MutableGameState.liveEncounterMembers(tag: Long): Int {
    if (tag == 0L) return 0
    var count = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.ownerTag == tag && enemy.alive) count++
    }
    return count
}

/** When the encounter [tag] started; NaN for 0 or an unknown tag. */
internal fun MutableGameState.encounterStartedAt(tag: Long): Float {
    if (tag == 0L) return Float.NaN
    return encounter(tag)?.startedAt ?: Float.NaN
}

/** Ends the bookkeeping. [despawn] removes the members without kills or rewards; otherwise they turn ambient (tag 0). */
internal fun MutableGameState.endEncounter(tag: Long, despawn: Boolean) {
    val index = encounterIndex(tag)
    if (index < 0) return
    encounters = encounters.filterIndexed { candidate, _ -> candidate != index }.toImmutableList()
    if (despawn) {
        enemies.removeMatchingStable { enemy -> enemy.ownerTag == tag }
    } else {
        for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            if (enemy.ownerTag == tag) enemy.ownerTag = 0L
        }
    }
}

internal fun MutableGameState.endAllEncounters(despawn: Boolean) {
    if (encounters.isEmpty()) return
    val current = encounters
    for (index in current.indices) endEncounter(current[index].tag, despawn)
}

/** Per step, at the end of updateEnemies: abandonment, then the area clamp. */
internal fun MutableGameState.updateEncounters() {
    if (encounters.isEmpty()) return
    val current = encounters
    for (index in current.indices) {
        val state = current[index]
        val dx = coreX - state.centerX
        val dy = coreY - state.centerY
        if (dx * dx + dy * dy <= state.abandonDistance * state.abandonDistance) continue
        var secretAlive = false
        for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            if (enemy.ownerTag == state.tag && enemy.isSecretBoss && enemy.alive) secretAlive = true
        }
        endEncounter(state.tag, despawn = true)
        if (secretAlive) {
            message = EnemyNotices.SECRET_SIGNAL_LOST
            messageTime = 2f
        }
    }
    if (encounters.isEmpty()) return
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (enemy.ownerTag == 0L || !enemy.alive) continue
        val stateIndex = encounterIndex(enemy.ownerTag)
        if (stateIndex < 0) continue
        val state = encounters[stateIndex]
        val limit = state.areaRadius - enemy.radius
        val dx = enemy.x - state.centerX
        val dy = enemy.y - state.centerY
        val distance = length(dx, dy)
        if (distance <= limit || distance <= 0.001f) continue
        val nx = dx / distance
        val ny = dy / distance
        enemy.x = state.centerX + nx * limit
        enemy.y = state.centerY + ny * limit
        val outward = enemy.vx * nx + enemy.vy * ny
        if (outward > 0f) {
            enemy.vx -= outward * nx
            enemy.vy -= outward * ny
        }
    }
}

/**
 * Kill hook of onEnemyKilled: a killed Imp (any owner) leaves an ember; a secret boss (any owner) pays the secret-boss
 * reward; a tagged kill counts once toward its encounter.
 */
internal fun MutableGameState.onEncounterEnemyKilled(enemy: Enemy) {
    if (enemy.type == EnemyType.IMP) queueEmber(enemy.x, enemy.y, IMP_KILL_EMBER_SECONDS)
    if (enemy.isSecretBoss) paySecretBossDefeat(enemy)
    if (enemy.ownerTag == 0L) return
    val index = encounterIndex(enemy.ownerTag)
    if (index < 0) return
    val state = encounters[index]
    encounters = replacedEncounter(index, state.copy(killed = state.killed + 1))
}

/**
 * Spawns up to [count] adds of [type] around [boss] (ring at boss radius + type radius + 50, angle i·TAU/count + run
 * time, nudged, held on the boss's side of a wall). A tagged boss's adds take its tag (leash-exempt, clamped,
 * despawned with the encounter, counted in spawned), at most [maxLive] live adds of that type, within
 * [MAX_ENCOUNTER_RESERVE]; an untagged boss's adds fit the free ordinary room. Always within the hard cap. Returns how
 * many spawned. Draws nothing.
 */
internal fun MutableGameState.spawnBossAdds(boss: Enemy, type: EnemyType, count: Int, integrityShare: Float, maxLive: Int): Int {
    require(type.encounterRank == EnemyRank.ORDINARY) { "Boss adds are ordinary kinds" }
    if (count <= 0) return 0
    val tag = boss.ownerTag
    var allowed = count
    if (tag != 0L) {
        var live = 0
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.ownerTag == tag && enemy.type == type && enemy.alive) live++
        }
        allowed = min(allowed, maxLive - live)
        allowed = min(allowed, MAX_ENCOUNTER_RESERVE - liveTaggedEnemies())
    } else {
        val room = ordinaryEnemyCap() - ordinaryCapLoad()
        allowed = min(allowed, (room / capCost(type)).toInt())
    }
    allowed = min(allowed, content.rebirth.maxActiveEnemies - enemies.size)
    if (allowed <= 0) return 0
    val radius = type.profile.radius
    val distance = boss.radius + radius + BOSS_ADD_SPACING
    for (index in 0 until allowed) {
        val angle = index * TAU / count + elapsed
        var x = boss.x + cos(angle) * distance
        var y = boss.y + sin(angle) * distance
        var attempts = 0
        while (attempts < ENCOUNTER_NUDGE_ATTEMPTS && isSpawnPointBlocked(x, y, radius)) {
            x += cos(angle) * ENCOUNTER_NUDGE_DISTANCE
            y += sin(angle) * ENCOUNTER_NUDGE_DISTANCE
            attempts++
        }
        // Neither the ring nor the nudge carries an add through a wall: it comes back along its line to the boss's side.
        val wall = earliestWallEntry(boss.x, boss.y, x, y, radius, before = 2f)
        if (wall >= 0L) {
            val share = Float.fromBits(wall.toInt())
            x = boss.x + (x - boss.x) * share
            y = boss.y + (y - boss.y) * share
        }
        enemies += spawnedEnemy(type, x, y, ownerTag = tag, integrityShare = integrityShare)
    }
    if (tag != 0L) {
        val stateIndex = encounterIndex(tag)
        if (stateIndex >= 0) {
            val state = encounters[stateIndex]
            encounters = replacedEncounter(stateIndex, state.copy(spawned = state.spawned + allowed))
        }
    }
    return allowed
}

/**
 * An ember: a delayed Volatile-Remains-style burst at ([x], [y]) after [delay] seconds (radius [EMBER_RADIUS], base
 * damage 6, a Core at 400 u/s or faster escapes), detonated by updateVolatileRemains with or without the R4 mutator.
 * None is queued while [MutableGameState.MAX_VOLATILE_BURSTS] bursts wait. Draws nothing.
 */
internal fun MutableGameState.queueEmber(x: Float, y: Float, delay: Float) {
    if (volatileBursts.size >= MutableGameState.MAX_VOLATILE_BURSTS) return
    volatileBursts = (volatileBursts + VolatileBurstProjection(
        x = x,
        y = y,
        radius = EMBER_RADIUS,
        armedAt = elapsed,
        detonatesAt = elapsed + delay,
    )).toImmutableList()
}

/**
 * Weighted kind pick for an encounter in [habitat] from a caller-owned [roll] in [0, 1) (the owner's own stream or
 * hash): ordinary kinds only, the run's time (at [threatSeconds]) and Rebirth gates apply, no recency and no director
 * personality. Draws nothing.
 */
internal fun MutableGameState.pickHabitatType(
    habitat: EnemyHabitat,
    roll: Float,
    weakOnly: Boolean = false,
    threatSeconds: Float = threatElapsed,
): EnemyType {
    var weak = weakOnly
    var total = habitatWeightTotal(habitat, weak, threatSeconds)
    if (total <= 0f && weak) {
        weak = false
        total = habitatWeightTotal(habitat, weak, threatSeconds)
    }
    if (total <= 0f) return EnemyType.DRIFTER
    var remaining = roll.coerceIn(0f, 0.9999999f) * total
    var picked = EnemyType.DRIFTER
    val roster = content.enemies
    for (index in roster.indices) {
        val definition = roster[index]
        val weight = typeWeight(definition, weak, recency = false, habitat = habitat, threatSeconds = threatSeconds, personality = false)
        if (weight <= 0f) continue
        picked = definition.type
        remaining -= weight
        if (remaining < 0f) break
    }
    return picked
}

private fun MutableGameState.habitatWeightTotal(habitat: EnemyHabitat, weakOnly: Boolean, threatSeconds: Float): Float {
    var total = 0f
    val roster = content.enemies
    for (index in roster.indices) {
        total += typeWeight(roster[index], weakOnly, recency = false, habitat = habitat, threatSeconds = threatSeconds, personality = false)
    }
    return total
}

private fun MutableGameState.encounterIndex(tag: Long): Int {
    val current = encounters
    for (index in current.indices) if (current[index].tag == tag) return index
    return -1
}

private fun MutableGameState.replacedEncounter(index: Int, state: EncounterState): ImmutableList<EncounterState> =
    encounters.mapIndexed { candidate, existing -> if (candidate == index) state else existing }.toImmutableList()

/** Live enemies any encounter owns. */
private fun MutableGameState.liveTaggedEnemies(): Int {
    var count = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.ownerTag != 0L && enemy.alive) count++
    }
    return count
}

/** Enemies [evictOldestOrdinaryEnemy] may remove: untagged, not place-bound, neither Elite nor boss class. */
private fun MutableGameState.evictableOrdinaryCount(): Int {
    var count = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.isBossClass && !enemy.isElite && !isPlaceBound(enemy)) count++
    }
    return count
}

/** A tagged enemy spawned outside the encounter's own spawn (a Splitter fragment) counts toward its spawned total. */
internal fun MutableGameState.recordEncounterSpawn(tag: Long) {
    if (tag == 0L) return
    val index = encounterIndex(tag)
    if (index < 0) return
    val state = encounters[index]
    encounters = replacedEncounter(index, state.copy(spawned = state.spawned + 1))
}
