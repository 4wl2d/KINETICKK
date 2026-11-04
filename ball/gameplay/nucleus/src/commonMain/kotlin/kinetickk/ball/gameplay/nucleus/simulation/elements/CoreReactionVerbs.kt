// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Core verbs read outside the reaction itself.

/**
 * Spall on a direct hit: the mark is cashed and the target flies away from the Core (Flash Freeze: it
 * freezes through the normal Freeze path). Immovable frames lose the mark without moving.
 */
internal fun MutableGameState.cashSpall(enemy: Enemy) {
    if (!enemy.alive || !consumeMark(enemy, MarkKind.SPALL) || !hasVerb(ReactionId.THERMAL_SHOCK)) return
    if (keystoneOn(ReactionId.THERMAL_SHOCK)) {
        if (!isFrozen(enemy) && enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] <= elapsed) freeze(enemy, FLASH_FREEZE_SECONDS)
    } else {
        hurl(coreX, coreY, enemy, tunedForce(ReactionId.THERMAL_SHOCK, reactionValue(ReactionId.THERMAL_SHOCK, SPALL_THROW_VALUE)))
    }
    onMarkCashed(enemy, ReactionId.THERMAL_SHOCK, null)
}

/**
 * Ablaze on a Core contact: [enemy] takes Thermal; with Firewalker a contact on an enemy close to its
 * Ignite threshold sets it off.
 */
internal fun MutableGameState.onAblazeContact(enemy: Enemy) {
    if (ablazeUntil <= elapsed || !enemy.alive || !hasVerb(ReactionId.WILDFIRE)) return
    applyStatus(enemy, StatusElement.THERMAL, reactionValue(ReactionId.WILDFIRE, ABLAZE_STACKS_VALUE).toInt(), 1)
    if (keystoneOn(ReactionId.WILDFIRE) && enemy.alive &&
        stacks(enemy, StatusElement.THERMAL) >= breakThreshold(StatusElement.THERMAL, enemy) - FIREWALKER_IGNITE_MARGIN
    ) {
        forceElementBreak(enemy, StatusElement.THERMAL)
    }
}

/** Glass Charge: an Embrittled ordinary or Elite enemy hit fast enough cannot stop the Core. */
internal fun MutableGameState.piercesEmbrittled(enemy: Enemy, coreImpactSpeed: Float): Boolean =
    hasVerb(ReactionId.EMBRITTLE) && !enemy.isBossClass && enemy.status[StatusCell.EMBRITTLED_UNTIL] > elapsed &&
        coreImpactSpeed >= reactionValue(ReactionId.EMBRITTLE, GLASS_CHARGE_SPEED_VALUE)

/**
 * Glass Burst: a pierced Embrittled enemy shatters into [GLASS_BURST_SHARDS] Cryo shards along the Core's
 * heading for [GLASS_BURST_SHARE] of the impact, and loses its Embrittle.
 */
internal fun MutableGameState.glassBurst(enemy: Enemy, impact: Float) {
    if (!keystoneOn(ReactionId.EMBRITTLE)) return
    enemy.status[StatusCell.EMBRITTLED_UNTIL] = 0f
    val heading = if (speed > 1f) atan2(velocityY, velocityX) else atan2(lastAimDirectionY, lastAimDirectionX)
    val x = enemy.x
    val y = enemy.y
    val link = LinkStyleCode.reaction(ReactionId.EMBRITTLE)
    val spacing = GLASS_BURST_SPACING_DEGREES * RADIANS_PER_DEGREE
    repeat(GLASS_BURST_SHARDS) { shard ->
        val angle = heading + (shard - GLASS_BURST_SHARDS / 2) * spacing
        val endX = x + cos(angle) * GLASS_BURST_REACH
        val endY = y + sin(angle) * GLASS_BURST_REACH
        addRelicArc(x, y, endX, endY, link)
        for (index in enemies.indices) {
            val target = enemies[index]
            if (target.id == enemy.id || !target.alive) continue
            if (segmentCircleIntersects(x, y, endX, endY, target.x, target.y, target.radius + 6f)) {
                dealStatusDamage(target, impact * GLASS_BURST_SHARE, StatusElement.CRYO, ignoresAffinity = true)
            }
        }
    }
}

/** Repulsor: an enemy Magnetize threw charges whatever it slams. */
internal fun MutableGameState.repulsorCharges(thrown: Enemy): Boolean =
    keystoneOn(ReactionId.MAGNETIZE) &&
        thrown.status[StatusCell.LAST_REACTION].toInt() == ReactionId.MAGNETIZE.ordinal + 1 &&
        elapsed - thrown.status[StatusCell.LAST_REACTION_AT] <= content.obstacles.thrownSeconds

/** Breakpoint: a Brake release detonates the Cracked enemies near the Core (at most [MAX_CRACKED_DETONATIONS]). */
internal fun MutableGameState.detonateCracked() {
    if (!hasVerb(ReactionId.FRACTURE) || keystoneOn(ReactionId.FRACTURE)) return
    val definition = content.build.reaction(ReactionId.FRACTURE)
    val reach = definition.values[CRACKED_REACH_VALUE]
    val damage = definition.values[CRACKED_POWER_VALUE] * definition.power * build.potency(StatusElement.ENTROPY) *
        build.reactionPower * runScale()
    val decay = definition.values[CRACKED_DECAY_VALUE].toInt()
    var detonated = 0
    for (index in enemies.indices) {
        if (detonated >= MAX_CRACKED_DETONATIONS) break
        val target = enemies[index]
        if (!target.alive || !markIs(target, MarkKind.CRACKED)) continue
        if (distanceSquared(coreX, coreY, target.x, target.y) > square(reach + target.radius)) continue
        consumeMark(target, MarkKind.CRACKED)
        detonated++
        val x = target.x
        val y = target.y
        dealStatusDamage(target, damage, StatusElement.ENTROPY, ignoresAffinity = true)
        if (target.alive) applyStatus(target, StatusElement.ENTROPY, decay, 1)
        shockwave(x, y, CRACKED_SHOCKWAVE_SECONDS, CRACKED_SHOCKWAVE_RADIUS, FxColorCode.elementTone(StatusElement.ENTROPY))
        onMarkCashed(target, ReactionId.FRACTURE, null)
    }
}

/** Arc Weave: while it lasts, every TURN edge throws a chain from the Core. */
internal fun MutableGameState.onTurnForReactions() {
    if (weaveUntil <= elapsed || !hasVerb(ReactionId.RESONANT_ARC)) return
    val links = if (keystoneOn(ReactionId.RESONANT_ARC)) STANDING_WAVE_LINKS else reactionValue(ReactionId.RESONANT_ARC, WEAVE_LINKS_VALUE).toInt()
    val damage = weaveDamage
    chain(coreX, coreY, -1, links, weaveRange, LinkStyleCode.reaction(ReactionId.RESONANT_ARC)) { target ->
        dealStatusDamage(target, damage, StatusElement.ION, ignoresAffinity = true)
    }
}

/** The live Void Anchor, or null (none, lapsed, dead or shifting). */
internal fun MutableGameState.voidAnchor(): Enemy? {
    if (voidAnchorId < 0 || voidAnchorUntil <= elapsed) return null
    for (index in enemies.indices) {
        val candidate = enemies[index]
        if (candidate.id == voidAnchorId) return if (candidate.alive && !isShifting(candidate)) candidate else null
    }
    return null
}

/**
 * Flame Leap and the field deaths when [enemy] dies: a burning enemy passes its black flame to the nearest other
 * enemy (weaker each leap, until it is too weak to leap) and recharges the skills; a death inside a Rot Well
 * grows it (Plague Pit: passes its Decay to everyone inside).
 */
internal fun MutableGameState.onReactionCarrierDied(enemy: Enemy) {
    if (enemy.status[StatusCell.BLACKFIRE_UNTIL] > elapsed && hasVerb(ReactionId.BLACKFIRE) && !keystoneOn(ReactionId.BLACKFIRE)) {
        val values = content.build.reaction(ReactionId.BLACKFIRE).values
        val dps = enemy.status[StatusCell.BLACKFIRE_DPS] * values[FLAME_LEAP_SHARE_VALUE]
        if (dps >= values[FLAME_LEAP_FLOOR_VALUE] * runScale()) {
            nearestOtherEnemy(enemy.x, enemy.y, enemy.id, values[FLAME_LEAP_REACH_VALUE])?.let { target ->
                val seconds = max(FLAME_LEAP_MIN_SECONDS, enemy.status[StatusCell.BLACKFIRE_UNTIL] - elapsed)
                target.status[StatusCell.BLACKFIRE_UNTIL] = max(target.status[StatusCell.BLACKFIRE_UNTIL], elapsed + seconds)
                target.status[StatusCell.BLACKFIRE_DPS] = max(target.status[StatusCell.BLACKFIRE_DPS], dps)
                addRelicArc(enemy.x, enemy.y, target.x, target.y, LinkStyleCode.reaction(ReactionId.BLACKFIRE))
            }
        }
        if (flameRefundReadyAt <= elapsed && refundAllSkills(values[FLAME_REFUND_VALUE]) > 0f) {
            flameRefundReadyAt = elapsed + FLAME_REFUND_GATE_SECONDS
        }
    }
    if (hasVerb(ReactionId.ROT_WELL)) onRotWellDeath(enemy)
}
