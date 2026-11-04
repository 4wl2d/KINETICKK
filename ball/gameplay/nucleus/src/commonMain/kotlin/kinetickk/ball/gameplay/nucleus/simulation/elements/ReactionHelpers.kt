// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ReactionAspect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/** Plasma Burst's blast: [magnitude] Thermal to everything within [radius] of ([x], [y]) plus [stacks] Thermal. */
internal fun MutableGameState.plasmaBurst(x: Float, y: Float, radius: Float, magnitude: Float, stacks: Int, generation: Int) {
    for (index in enemies.indices) {
        val target = enemies[index]
        if (target.alive && distanceSquared(x, y, target.x, target.y) <= square(radius + target.radius)) {
            dealStatusDamage(target, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
            if (generation < 2 && target.alive) applyStatus(target, StatusElement.THERMAL, stacks, generation)
        }
    }
}

/** Arc Plasma: the burst repeats once around up to [ARC_PLASMA_REPEATS] Charged enemies near it, at half strength. */
internal fun MutableGameState.arcPlasma(origin: Enemy, x: Float, y: Float, radius: Float, magnitude: Float, generation: Int) {
    var firstX = Float.NaN
    var firstY = Float.NaN
    var secondX = Float.NaN
    var secondY = Float.NaN
    for (index in enemies.indices) {
        val target = enemies[index]
        if (target.id == origin.id || !target.alive || isShifting(target) || stacks(target, StatusElement.ION) <= 0) continue
        if (distanceSquared(x, y, target.x, target.y) > square(ARC_PLASMA_REACH)) continue
        if (firstX.isNaN()) {
            firstX = target.x
            firstY = target.y
        } else {
            secondX = target.x
            secondY = target.y
            break
        }
    }
    val link = LinkStyleCode.reaction(ReactionId.PLASMA_BURST)
    if (!firstX.isNaN()) {
        addRelicArc(x, y, firstX, firstY, link)
        plasmaBurst(firstX, firstY, radius, magnitude * ARC_PLASMA_SHARE, ARC_PLASMA_STACKS, generation)
    }
    if (!secondX.isNaN()) {
        addRelicArc(x, y, secondX, secondY, link)
        plasmaBurst(secondX, secondY, radius, magnitude * ARC_PLASMA_SHARE, ARC_PLASMA_STACKS, generation)
    }
}

/** Seismic's quake: [damage] Gravitic within [radius] of ([x], [y]); ordinary enemies are stunned. */
internal fun MutableGameState.quake(x: Float, y: Float, radius: Float, damage: Float) {
    for (index in enemies.indices) {
        val target = enemies[index]
        if (!target.alive || isShifting(target) || distanceSquared(x, y, target.x, target.y) > square(radius + target.radius)) continue
        dealStatusDamage(target, damage, StatusElement.GRAVITIC, ignoresAffinity = true)
        if (target.alive && target.isOrdinary) target.disabledUntil = max(target.disabledUntil, elapsed + SEISMIC_STUN_SECONDS)
    }
}

/**
 * Rock Echo: up to [rocks] echoing obstacles within [reach] of ([x], [y]) repeat the quake around their centres
 * ([radius], [damage]). Obstacles are visited in window-cell order and never changed. Returns the echoes.
 */
internal fun MutableGameState.rockEcho(x: Float, y: Float, rocks: Int, reach: Float, radius: Float, damage: Float): Int {
    val limit = min(rocks, MAX_ROCK_ECHOES)
    var found = 0
    var firstX = 0f
    var firstY = 0f
    var secondX = 0f
    var secondY = 0f
    var thirdX = 0f
    var thirdY = 0f
    forEachObstacleNear(x, y, reach) { _, obstacle ->
        if (found < limit && obstacle.kind.echoes && distanceSquared(x, y, obstacle.x, obstacle.y) <= square(reach + obstacle.radius)) {
            when (found) {
                0 -> { firstX = obstacle.x; firstY = obstacle.y }
                1 -> { secondX = obstacle.x; secondY = obstacle.y }
                else -> { thirdX = obstacle.x; thirdY = obstacle.y }
            }
            found++
        }
    }
    for (echo in 0 until found) {
        val echoX = when (echo) { 0 -> firstX; 1 -> secondX; else -> thirdX }
        val echoY = when (echo) { 0 -> firstY; 1 -> secondY; else -> thirdY }
        quake(echoX, echoY, radius, damage)
        shockwave(echoX, echoY, 0.35f, radius, FxColorCode.quake(StatusElement.GRAVITIC))
    }
    return found
}

/** Aftershock: queues a second quake at ([x], [y]) in [AFTERSHOCK_DELAY_SECONDS]; a third pending one is dropped. */
internal fun MutableGameState.queueAftershock(x: Float, y: Float, damage: Float) {
    val at = elapsed + AFTERSHOCK_DELAY_SECONDS
    if (aftershockAt0 < 0f) {
        aftershockAt0 = at
        aftershockX0 = x
        aftershockY0 = y
        aftershockDamage0 = damage
    } else if (aftershockAt1 < 0f) {
        aftershockAt1 = at
        aftershockX1 = x
        aftershockY1 = y
        aftershockDamage1 = damage
    }
}

/** Strikes the aftershocks that came due. */
internal fun MutableGameState.updateAftershocks() {
    if (aftershockAt0 < 0f && aftershockAt1 < 0f) return
    val radius = content.build.reaction(ReactionId.SEISMIC).radius
    if (aftershockAt0 >= 0f && aftershockAt0 <= elapsed) {
        aftershockAt0 = -1f
        quake(aftershockX0, aftershockY0, radius, aftershockDamage0)
        shockwave(aftershockX0, aftershockY0, 0.4f, radius * AFTERSHOCK_SHARE, FxColorCode.quake(StatusElement.GRAVITIC))
    }
    if (aftershockAt1 >= 0f && aftershockAt1 <= elapsed) {
        aftershockAt1 = -1f
        quake(aftershockX1, aftershockY1, radius, aftershockDamage1)
        shockwave(aftershockX1, aftershockY1, 0.4f, radius * AFTERSHOCK_SHARE, FxColorCode.quake(StatusElement.GRAVITIC))
    }
}

/** Crevasse: a non-boss target loses its aspect for a while and its armor plates crack. */
internal fun MutableGameState.suppress(target: Enemy) {
    if (target.isBossClass) return
    setMark(target, MarkKind.SUPPRESSED, CREVASSE_SECONDS)
    if (target.armored) target.armorCracked = true
}

/** Pushes [target] along the unit vector ([dirX], [dirY]) at [speed], scaled by the knockback it takes. */
internal fun MutableGameState.push(target: Enemy, dirX: Float, dirY: Float, speed: Float) {
    val knockback = knockbackTaken(target)
    if (knockbackAsDamage(target, knockback, speed)) return
    if (knockback <= 0f) return
    target.vx += dirX * speed * knockback
    target.vy += dirY * speed * knockback
    markThrown(target)
}

/** Heading from the Core to ([x], [y]); the last aim when the two (nearly) coincide. */
internal fun MutableGameState.awayFromCoreAngle(x: Float, y: Float): Float {
    val dx = x - coreX
    val dy = y - coreY
    return if (length(dx, dy) < 1f) atan2(lastAimDirectionY, lastAimDirectionX) else atan2(dy, dx)
}

/**
 * True when ([x], [y]) lies within [CORE_LINK_RADIUS] (plus the build's Core Link bonus) of the Core: such reactions
 * feed the Core's verbs.
 */
internal fun MutableGameState.nearCore(x: Float, y: Float): Boolean =
    distanceSquared(x, y, coreX, coreY) <= square(CORE_LINK_RADIUS + build.coreLinkBonus)

/** [reaction]'s [radius] with its Reaction Tuning, never past [MAX_TUNED_RADIUS] (untuned: unchanged). */
internal fun MutableGameState.tunedRadius(reaction: ReactionId, radius: Float): Float {
    val scale = build.reactionTuning(reaction, ReactionAspect.RADIUS)
    return if (scale == 1f) radius else min(MAX_TUNED_RADIUS, radius * scale)
}

/** How long a state [reaction] leaves lasts with its Reaction Tuning. */
internal fun MutableGameState.tunedSeconds(reaction: ReactionId, seconds: Float): Float =
    seconds * build.reactionTuning(reaction, ReactionAspect.DURATION)

/** How hard [reaction] moves enemies with its Reaction Tuning. */
internal fun MutableGameState.tunedForce(reaction: ReactionId, speed: Float): Float =
    speed * build.reactionTuning(reaction, ReactionAspect.FORCE)

/**
 * A mark was cashed on [enemy] (a Spall throw, a Cracked detonation, a Void Anchor blink, a ringing Shatter):
 * MARK_CONSUMED clauses fire, at most [MAX_MARK_DISPATCHES_PER_STEP] times per fixed step.
 */
internal fun MutableGameState.onMarkCashed(enemy: Enemy, reaction: ReactionId?, element: StatusElement?) {
    if (markDispatches >= MAX_MARK_DISPATCHES_PER_STEP || !listens(EffectTrigger.MARK_CONSUMED)) return
    markDispatches++
    fireClauses(EffectTrigger.MARK_CONSUMED, enemy, element = element ?: reaction?.first, reaction = reaction)
}

internal const val MAX_MARK_DISPATCHES_PER_STEP: Int = 4

/** The verb value at [index] of [reaction]; read only where the reaction has verb values. */
internal fun MutableGameState.reactionValue(reaction: ReactionId, index: Int): Float = content.build.reaction(reaction).values[index]

/** True when [reaction] carries verb values (its play-style behaviour is on). */
internal fun MutableGameState.hasVerb(reaction: ReactionId): Boolean = content.build.reaction(reaction).values.isNotEmpty()

/** The keystone form of [reaction] is on: the build switched it on and the reaction has its verb. */
internal fun MutableGameState.keystoneOn(reaction: ReactionId): Boolean = build.keystone(reaction) && hasVerb(reaction)

/** How long Ablaze lasts when Wildfire sets it (Firewalker longer). */
internal fun MutableGameState.ablazeSeconds(): Float =
    if (keystoneOn(ReactionId.WILDFIRE)) FIREWALKER_SECONDS else reactionValue(ReactionId.WILDFIRE, ABLAZE_SECONDS_VALUE)

/** How long Arc Weave lasts when Resonant Arc sets it (Standing Wave longer). */
internal fun MutableGameState.weaveSeconds(): Float =
    if (keystoneOn(ReactionId.RESONANT_ARC)) STANDING_WAVE_SECONDS else reactionValue(ReactionId.RESONANT_ARC, WEAVE_SECONDS_VALUE)
