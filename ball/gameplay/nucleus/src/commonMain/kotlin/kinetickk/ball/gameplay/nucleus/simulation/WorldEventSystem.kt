// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.WorldEventPolicy
import kinetickk.ball.gameplay.nucleus.model.BuffSource
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.WorldEventPlan
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.EventStrikeProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * The run variety's step: the run-start announcements, then ambient world events (variety.md §6) and event signals
 * (inert until they land). World events are planned once at run start from stateless hashes of the run seed (no stream
 * gains a draw): two or three per run, one at a time, telegraphed from 60 s and over by 660 s. Every value an event
 * rolls while it runs is a hash of the run seed and the event's pulse index.
 */

/** The run variety's single per-step hook: announcements, then the world event, then (later) signals. */
internal fun MutableGameState.updateRunVariety(delta: Float) {
    announceHostilePattern()
    announceKeynote()
    updateWorldEvents(delta)
}

/** A world layer switch ends the event in progress silently. */
internal fun MutableGameState.endWorldEventForLayerChange() {
    if (worldEvent != null) clearWorldEvent()
}

/** The banner each world event raises when its telegraph starts (English content keys the feed localizes). */
internal val WORLD_EVENT_BANNERS: List<String> = WorldEventKind.entries.map { it.displayName.uppercase() }

/** The notice an event's end raises. */
internal const val SKIES_CLEAR: String = "SKIES CLEAR"

/** How long a world event's banner and its end notice show. */
internal const val WORLD_EVENT_BANNER_SECONDS: Float = 2.5f
internal const val SKIES_CLEAR_SECONDS: Float = 1.6f

/** The gravity storm refreshes its projected wind (angle and strength) this often; its push is exact every step. */
internal const val GRAVITY_WIND_SAMPLE_SECONDS: Float = 0.25f

/** A data storm's kill bonus Data lands this far above the kill, spread sideways when there are several. */
private const val STORM_KILL_DATA_LIFT: Float = 16f
private const val STORM_KILL_DATA_SPREAD: Float = 12f

/** An ion strike's two bolts fall from this far above the zone, this far either side of its centre. */
private const val ION_BOLT_HEIGHT: Float = 420f
private const val ION_BOLT_SIDE: Float = 24f
private const val ION_BOLT_LIFE: Float = 0.2f

/** Hash keys under [WORLD_EVENT_SALT]: the count roll, the start roll of plan i, the k-th kind draw, pulse values. */
private const val COUNT_KEY: Long = 0L
private const val START_KEY: Long = 1L
private const val KIND_KEY: Long = 16L
private const val PULSE_KEY: Long = 64L

/**
 * The run's world events, planned from stateless hashes of [runSeed] (empty when the policy is off): `minPerRun`, plus
 * one with `extraEventChance`, at most `maxPerRun`; kinds drawn by weight without replacement; plan i inside the i-th
 * equal segment of the window, after its telegraph and before its duration and gap run out of the segment. So events
 * never overlap, never repeat a kind, telegraph no earlier than `firstAtSeconds` and end by `lastEndSeconds − gapSeconds`.
 */
internal fun planWorldEvents(runSeed: Int, policy: WorldEventPolicy): ImmutableList<WorldEventPlan> {
    if (!policy.enabled) return immutableListOf()
    val extra = if (hashUnit(runHash(runSeed, WORLD_EVENT_SALT, COUNT_KEY)) < policy.extraEventChance) 1 else 0
    val count = min(policy.minPerRun + extra, policy.maxPerRun)
    val kinds = drawWorldEventKinds(runSeed, policy, count)
    val segment = (policy.lastEndSeconds - policy.firstAtSeconds) / count
    return List(count) { index ->
        val kind = kinds[index]
        val duration = policy.durationOf(kind)
        val earliest = policy.firstAtSeconds + index * segment + policy.telegraphSeconds
        val latest = policy.firstAtSeconds + (index + 1) * segment - duration - policy.gapSeconds
        val startsAt = earliest + hashUnit(runHash(runSeed, WORLD_EVENT_SALT, START_KEY + index)) * (latest - earliest)
        WorldEventPlan(kind, telegraphAt = startsAt - policy.telegraphSeconds, startsAt = startsAt, endsAt = startsAt + duration)
    }.toImmutableList()
}

/** [count] distinct kinds, each drawn by weight among the kinds not drawn yet; zero-weight kinds are never drawn. */
private fun drawWorldEventKinds(runSeed: Int, policy: WorldEventPolicy, count: Int): List<WorldEventKind> {
    val kinds = WorldEventKind.entries
    val drawn = BooleanArray(kinds.size)
    return List(count) { draw ->
        var total = 0f
        for (index in kinds.indices) if (!drawn[index]) total += policy.weights[index]
        var roll = hashUnit(runHash(runSeed, WORLD_EVENT_SALT, KIND_KEY + draw)) * total
        var chosen = -1
        for (index in kinds.indices) {
            val weight = policy.weights[index]
            if (drawn[index] || weight <= 0f) continue
            chosen = index
            if (roll < weight) break
            roll -= weight
        }
        // The policy keeps at least maxPerRun kinds weighted, so a draw always finds one.
        drawn[chosen] = true
        kinds[chosen]
    }
}

/** How long an event of [kind] stays active after its telegraph. */
internal fun WorldEventPolicy.durationOf(kind: WorldEventKind): Float = when (kind) {
    WorldEventKind.METEOR_SHOWER -> meteor.durationSeconds
    WorldEventKind.DATA_STORM -> dataStorm.durationSeconds
    WorldEventKind.GRAVITY_STORM -> gravity.durationSeconds
    WorldEventKind.ION_STORM -> ion.durationSeconds
    WorldEventKind.SOLAR_FLARE -> solar.durationSeconds
}

/** Seconds between an active event's pulses (strikes, Data, buffs; the gravity storm's projected wind samples). */
private fun WorldEventPolicy.pulseSecondsOf(kind: WorldEventKind): Float = when (kind) {
    WorldEventKind.METEOR_SHOWER -> meteor.intervalSeconds
    WorldEventKind.DATA_STORM -> dataStorm.intervalSeconds
    WorldEventKind.GRAVITY_STORM -> GRAVITY_WIND_SAMPLE_SECONDS
    WorldEventKind.ION_STORM -> ion.intervalSeconds
    WorldEventKind.SOLAR_FLARE -> solar.pulseSeconds
}

/**
 * One step of the world events. The Architect's arrival and any world layer but realspace end the event in progress
 * silently and consume every plan whose telegraph passes meanwhile. Otherwise the next plan starts at its telegraph
 * (with its banner), the event ends at its end (SKIES CLEAR), due strikes land in list order, and an active event runs
 * its kind's step and at most one pulse.
 */
internal fun MutableGameState.updateWorldEvents(delta: Float) {
    if (bossSpawned || !inBaseLayer()) {
        if (worldEvent != null) clearWorldEvent()
        consumePassedWorldEventPlans()
        return
    }
    if (worldEvent == null) {
        val plan = worldEventPlans.getOrNull(nextWorldEvent) ?: return
        if (elapsed < plan.telegraphAt) return
        if (elapsed >= plan.endsAt) {
            // A plan whose whole event already passed is consumed silently.
            nextWorldEvent++
            return
        }
        startWorldEvent(plan)
    }
    val event = worldEvent ?: return
    if (elapsed >= event.endsAt) {
        clearWorldEvent()
        message = SKIES_CLEAR
        messageTime = SKIES_CLEAR_SECONDS
        return
    }
    if (event.strikes.isNotEmpty()) {
        landDueStrikes(event)
        if (phase != GamePhase.RUNNING) return
    }
    if (elapsed < event.startsAt) return
    val policy = content.variety.worldEvents
    if (event.kind == WorldEventKind.GRAVITY_STORM) blowGravityWind(event, policy, delta)
    if (event.kind == WorldEventKind.SOLAR_FLARE) sweepSolarFlare(event, policy)
    if (elapsed >= worldEventNextPulseAt) {
        // Landing strikes replaced the projection: the pulse reads the current one.
        pulseWorldEvent(worldEvent ?: return, policy)
        worldEventPulse++
        // Pulse n is due at startsAt + n × interval (no accumulated drift); at most one fires per step.
        worldEventNextPulseAt = event.startsAt + worldEventPulse * policy.pulseSecondsOf(event.kind)
    }
}

/** The event of [plan] begins its telegraph: its projection, pulse clock and banner. */
private fun MutableGameState.startWorldEvent(plan: WorldEventPlan) {
    worldEvent = WorldEventProjection(
        kind = plan.kind,
        telegraphAt = plan.telegraphAt,
        startsAt = plan.startsAt,
        endsAt = plan.endsAt,
        windAngle = if (plan.kind == WorldEventKind.GRAVITY_STORM) gravityWindBase() else 0f,
    )
    worldEventPulse = 0
    worldEventNextPulseAt = plan.startsAt
    message = WORLD_EVENT_BANNERS[plan.kind.ordinal]
    messageTime = WORLD_EVENT_BANNER_SECONDS
}

/** The event in progress is over: its plan is consumed and its strikes go with it. */
private fun MutableGameState.clearWorldEvent() {
    worldEvent = null
    nextWorldEvent++
    worldEventPulse = 0
    worldEventNextPulseAt = 0f
}

private fun MutableGameState.consumePassedWorldEventPlans() {
    while (true) {
        val plan = worldEventPlans.getOrNull(nextWorldEvent) ?: return
        if (elapsed < plan.telegraphAt) return
        nextWorldEvent++
    }
}

/** A value in [0, 1) for slot [slot] (0..3) of the current pulse. */
private fun MutableGameState.pulseUnit(slot: Int): Float =
    hashUnit(runHash(runSeed, WORLD_EVENT_SALT, PULSE_KEY + worldEventPulse * 4L + slot))

/** The Core's heading, which meteors and Data storms fall around. */
private fun MutableGameState.eventHeading(): Float = movementAngle()

private fun MutableGameState.pulseWorldEvent(event: WorldEventProjection, policy: WorldEventPolicy) {
    when (event.kind) {
        WorldEventKind.METEOR_SHOWER -> {
            val meteor = policy.meteor
            val angle = eventHeading() + (pulseUnit(0) - 0.5f) * meteor.angleSpread
            val distance = meteor.minDistance + pulseUnit(1) * meteor.distanceSpread
            armStrike(event, coreX + cos(angle) * distance, coreY + sin(angle) * distance, meteor.radius, meteor.armSeconds)
        }
        WorldEventKind.DATA_STORM -> {
            val storm = policy.dataStorm
            val angle = eventHeading() + (pulseUnit(0) - 0.5f) * storm.angleSpread
            val distance = storm.minDistance + pulseUnit(1) * (storm.maxDistance - storm.minDistance)
            addPickup(Pickup(PickupType.DATA, coreX + cos(angle) * distance, coreY + sin(angle) * distance))
        }
        WorldEventKind.GRAVITY_STORM -> worldEvent = event.copy(
            windAngle = gravityWindAngle(event, policy),
            windStrength = gravityWindRamp(event, policy),
        )
        WorldEventKind.ION_STORM -> {
            val ion = policy.ion
            val angle = pulseUnit(0) * TAU
            val distance = ion.minDistance + pulseUnit(1) * (ion.maxDistance - ion.minDistance)
            armStrike(event, coreX + cos(angle) * distance, coreY + sin(angle) * distance, ion.radius, ion.armSeconds)
        }
        WorldEventKind.SOLAR_FLARE -> flareSolar()
    }
}

/**
 * Telegraphs a strike at ([x], [y]) that lands [armSeconds] from now. Skipped when [ContentBounds.MAX_EVENT_STRIKES] are
 * already armed, or when it would land after the event ends (a telegraphed strike always lands).
 */
private fun MutableGameState.armStrike(event: WorldEventProjection, x: Float, y: Float, radius: Float, armSeconds: Float) {
    if (event.strikes.size >= ContentBounds.MAX_EVENT_STRIKES || elapsed + armSeconds > event.endsAt) return
    worldEvent = event.copy(
        strikes = (event.strikes + EventStrikeProjection(x, y, radius, armedAt = elapsed, strikesAt = elapsed + armSeconds))
            .toImmutableList(),
    )
}

/** Lands every due strike in list order after one list replacement drops them. */
private fun MutableGameState.landDueStrikes(event: WorldEventProjection) {
    val strikes = event.strikes
    var due = 0
    for (index in strikes.indices) if (strikes[index].strikesAt <= elapsed) due++
    if (due == 0) return
    worldEvent = event.copy(strikes = strikes.filter { it.strikesAt > elapsed }.toImmutableList())
    val policy = content.variety.worldEvents
    for (index in strikes.indices) {
        val strike = strikes[index]
        if (strike.strikesAt > elapsed) continue
        when (event.kind) {
            WorldEventKind.METEOR_SHOWER -> landMeteor(strike, policy)
            WorldEventKind.ION_STORM -> landIonStrike(strike, policy)
            WorldEventKind.DATA_STORM, WorldEventKind.GRAVITY_STORM, WorldEventKind.SOLAR_FLARE -> Unit
        }
    }
}

/**
 * A meteor lands: a hostile blast on a Core inside its ring (a Dash phase or an open parry cancels it), damage in
 * Drifter integrities to every enemy inside, a push out of the ring for those that are not boss class, and one Data at
 * the centre. The damage has no source, so it fires no kill clause.
 */
private fun MutableGameState.landMeteor(strike: EventStrikeProjection, policy: WorldEventPolicy) {
    val meteor = policy.meteor
    shockwave(strike.x, strike.y, 0.5f, strike.radius * 1.6f, 3)
    burst(strike.x, strike.y, 14, 3)
    val reach = strike.radius + coreRadius
    if (phase == GamePhase.RUNNING && distanceSquared(strike.x, strike.y, coreX, coreY) <= reach * reach) {
        hurtByHostileBlast(strike.x, strike.y, hostileDamage(null, meteor.coreDamage), IncomingDamage.HIT)
    }
    val damage = meteor.enemyDamageScale * spawnIntegrity(EnemyType.DRIFTER)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || !insideStrike(enemy, strike)) continue
        damageEnemy(enemy, damage, source = null)
        if (!enemy.isBossClass) pushOutOfStrike(enemy, strike, meteor.pushSpeed)
    }
    addPickup(Pickup(PickupType.DATA, strike.x, strike.y))
}

/**
 * An ion strike lands: two bolts fall on the zone, every enemy inside takes its Ion stacks and damage in Drifter
 * integrities (no source), and a Core within the strike's Core radius takes a hostile blast unless it is dashing.
 */
private fun MutableGameState.landIonStrike(strike: EventStrikeProjection, policy: WorldEventPolicy) {
    val ion = policy.ion
    val style = LinkStyleCode.elementBreak(StatusElement.ION)
    addWeaponArc(strike.x - ION_BOLT_SIDE, strike.y - ION_BOLT_HEIGHT, strike.x, strike.y, ION_BOLT_LIFE, style)
    addWeaponArc(strike.x + ION_BOLT_SIDE, strike.y - ION_BOLT_HEIGHT, strike.x, strike.y, ION_BOLT_LIFE, style)
    applyStatusAround(strike.x, strike.y, strike.radius, StatusElement.ION, ion.ionStacks.toFloat(), generation = 0)
    val damage = ion.enemyDamageScale * spawnIntegrity(EnemyType.DRIFTER)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && insideStrike(enemy, strike)) damageEnemy(enemy, damage, source = null)
    }
    if (phase == GamePhase.RUNNING && distanceSquared(strike.x, strike.y, coreX, coreY) <= ion.coreRadius * ion.coreRadius) {
        hurtByHostileBlast(strike.x, strike.y, hostileDamage(null, ion.coreDamage), IncomingDamage.HIT)
    }
}

private fun insideStrike(enemy: Enemy, strike: EventStrikeProjection): Boolean {
    val reach = strike.radius + enemy.radius
    return distanceSquared(strike.x, strike.y, enemy.x, enemy.y) <= reach * reach
}

/** Raises [enemy]'s outward speed from the strike's centre to at least [speed], keeping its sideways motion. */
private fun pushOutOfStrike(enemy: Enemy, strike: EventStrikeProjection, speed: Float) {
    val dx = enemy.x - strike.x
    val dy = enemy.y - strike.y
    val distance = length(dx, dy)
    val nx = if (distance > 0f) dx / distance else 1f
    val ny = if (distance > 0f) dy / distance else 0f
    val outward = enemy.vx * nx + enemy.vy * ny
    if (outward >= speed) return
    enemy.vx += nx * (speed - outward)
    enemy.vy += ny * (speed - outward)
}

/** The gravity storm's wind angle at its start, a stateless hash of the run seed. */
private fun MutableGameState.gravityWindBase(): Float = hashUnit(runHash(runSeed, WORLD_EVENT_SALT, PULSE_KEY)) * TAU

/** The wind turns at the storm's turn rate from its start angle. */
private fun MutableGameState.gravityWindAngle(event: WorldEventProjection, policy: WorldEventPolicy): Float =
    gravityWindBase() + policy.gravity.turnRate * (elapsed - event.startsAt)

/** The wind's 0..1 strength: smoothstep ramps over the storm's first and last ramp seconds, full between. */
private fun MutableGameState.gravityWindRamp(event: WorldEventProjection, policy: WorldEventPolicy): Float {
    val ramp = policy.gravity.rampSeconds
    if (ramp <= 0f) return 1f
    val rise = clamp((elapsed - event.startsAt) / ramp, 0f, 1f)
    val fall = clamp((event.endsAt - elapsed) / ramp, 0f, 1f)
    val share = min(rise, fall)
    return share * share * (3f - 2f * share)
}

/** The wind pushes the Core and every live enemy that is not boss class along its current angle. */
private fun MutableGameState.blowGravityWind(event: WorldEventProjection, policy: WorldEventPolicy, delta: Float) {
    val gravity = policy.gravity
    val ramp = gravityWindRamp(event, policy)
    if (ramp <= 0f) return
    val angle = gravityWindAngle(event, policy)
    val x = cos(angle) * ramp * delta
    val y = sin(angle) * ramp * delta
    velocityX += x * gravity.coreAcceleration
    velocityY += y * gravity.coreAcceleration
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || enemy.isBossClass) continue
        enemy.vx += x * gravity.enemyAcceleration
        enemy.vy += y * gravity.enemyAcceleration
    }
}

/**
 * A solar flare pulse: the flare's cooling penalty and weapon bonus, refreshed for its buff seconds under the world
 * event's buff source (one stack each, so pulses only renew them).
 */
private fun MutableGameState.flareSolar() {
    val solar = content.variety.worldEvents.solar
    val source = BuffSource.worldEvent(WorldEventKind.SOLAR_FLARE)
    addBuff(source, BuffStat.COOLING, -solar.coolingPenalty, solar.buffSeconds, 1)
    addBuff(source, BuffStat.WEAPON, solar.weaponBonus, solar.buffSeconds, 1)
}

/**
 * A solar flare's sweeps, due at its start and every sweep interval after it: each sweep records its start and angle
 * (the band's look) and heats every enemy near the Core.
 */
private fun MutableGameState.sweepSolarFlare(event: WorldEventProjection, policy: WorldEventPolicy) {
    val solar = policy.solar
    val nextSweep = if (event.sweepStartedAt == Float.NEGATIVE_INFINITY) event.startsAt else event.sweepStartedAt + solar.sweepIntervalSeconds
    if (elapsed < nextSweep) return
    worldEvent = event.copy(sweepStartedAt = nextSweep, sweepAngle = pulseUnit(3) * TAU)
    if (solar.sweepStacks > 0) {
        applyStatusAround(coreX, coreY, solar.sweepRadius, StatusElement.THERMAL, solar.sweepStacks.toFloat(), generation = 0)
    }
}

/** A data storm's kill bonus: every kill while the storm is active leaves its bonus Data just above it. */
internal fun MutableGameState.dropDataStormBonus(enemy: Enemy) {
    val event = worldEvent ?: return
    if (event.kind != WorldEventKind.DATA_STORM || elapsed < event.startsAt) return
    val count = content.variety.worldEvents.dataStorm.killBonusData
    for (index in 0 until count) {
        val offset = (index - (count - 1) * 0.5f) * STORM_KILL_DATA_SPREAD
        addPickup(Pickup(PickupType.DATA, enemy.x + offset, enemy.y - STORM_KILL_DATA_LIFT))
    }
}
