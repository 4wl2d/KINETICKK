// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.director

import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

/**
 * Weighted type pick over the eligible roster; Wardens past their cap become Weavers. The roll is a spawn-stream draw
 * unless the caller hands one in [0, 1) as [unitRoll].
 */
internal fun MutableGameState.pickEnemyType(
    weakOnly: Boolean = false,
    excludeSwarmling: Boolean = false,
    personality: Boolean = true,
    unitRoll: Float = Float.NaN,
): EnemyType {
    val roster = content.enemies
    var total = 0f
    for (index in roster.indices) {
        val definition = roster[index]
        if (excludeSwarmling && definition.type == EnemyType.SWARMLING) continue
        total += typeWeight(definition, weakOnly, personality = personality)
    }
    if (total <= 0f) return EnemyType.DRIFTER
    var roll = (if (unitRoll.isNaN()) spawnRandom.nextFloat() else unitRoll) * total
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
internal fun MutableGameState.withinWardenCap(type: EnemyType): EnemyType {
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
        if (enemy.isEliteClass || isPlaceBound(enemy)) continue
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
