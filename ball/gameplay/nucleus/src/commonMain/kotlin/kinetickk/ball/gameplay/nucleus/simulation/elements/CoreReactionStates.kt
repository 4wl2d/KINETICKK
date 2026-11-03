// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Core's lingering reaction states each step: pending aftershocks strike, Shard Guard shards break on
 * the first enemy or hostile shot they touch, and lapsed states clear. One pass over enemies and shots while
 * a guard lives; allocation-free.
 */
internal fun MutableGameState.updateCoreReactionStates() {
    updateAftershocks()
    if (guardShards <= 0) return
    if (guardUntil <= elapsed || !hasVerb(ReactionId.SHATTERPOINT)) {
        guardShards = 0
        return
    }
    val count = guardShards
    val orbit = coreRadius + reactionValue(ReactionId.SHATTERPOINT, GUARD_ORBIT_VALUE)
    val damage = guardDamage
    var broken = 0
    for (index in enemies.indices) {
        if (guardShards <= 0) return
        val target = enemies[index]
        if (!target.alive || isShifting(target)) continue
        val shard = guardShardTouching(count, broken, orbit, target.x, target.y, target.radius + GUARD_SHARD_RADIUS)
        if (shard < 0) continue
        broken = broken or (1 shl shard)
        guardShards--
        dealStatusDamage(target, damage, StatusElement.CRYO, ignoresAffinity = true)
        if (target.alive) applyStatus(target, StatusElement.CRYO, 1, 1)
    }
    for (index in projectiles.indices) {
        if (guardShards <= 0) return
        val shot = projectiles[index]
        if (!shot.hostile || shot.spent || shot.kind != ProjectileKind.BOLT) continue
        val shard = guardShardTouching(count, broken, orbit, shot.x, shot.y, shot.radius + GUARD_SHARD_RADIUS)
        if (shard < 0 || !eraseHostileShot(shot, EraseCause.REACTION, shot.x, shot.y)) continue
        broken = broken or (1 shl shard)
        guardShards--
    }
}

/**
 * The first unbroken shard of a [count]-shard guard on the [orbit] around the Core within [reach] of ([x], [y]),
 * or -1. Shard i sits at angle `elapsed × GUARD_SPIN + i × TAU / count`.
 */
private fun MutableGameState.guardShardTouching(count: Int, broken: Int, orbit: Float, x: Float, y: Float, reach: Float): Int {
    val fromCore = length(x - coreX, y - coreY)
    if (kotlin.math.abs(fromCore - orbit) > reach) return -1
    for (shard in 0 until count) {
        if (broken and (1 shl shard) != 0) continue
        val angle = elapsed * GUARD_SPIN + shard * TAU / count
        if (distanceSquared(coreX + cos(angle) * orbit, coreY + sin(angle) * orbit, x, y) <= reach * reach) return shard
    }
    return -1
}

/** The world moved by ([shiftX], [shiftY]): pending aftershocks move with it. */
internal fun MutableGameState.rebaseCoreReactionStates(shiftX: Float, shiftY: Float) {
    aftershockX0 -= shiftX
    aftershockY0 -= shiftY
    aftershockX1 -= shiftX
    aftershockY1 -= shiftY
}

/**
 * A new run starts with no lingering Core state, anchor, latch, prime, aftershock, buffered skill input or active item
 * waiting for a slot.
 */
internal fun MutableGameState.resetCoreReactionState() {
    aftershockAt0 = -1f
    aftershockX0 = 0f
    aftershockY0 = 0f
    aftershockDamage0 = 0f
    aftershockAt1 = -1f
    aftershockX1 = 0f
    aftershockY1 = 0f
    aftershockDamage1 = 0f
    ablazeUntil = 0f
    frictionlessUntil = 0f
    blastJumpUntil = 0f
    weaveUntil = 0f
    weaveDamage = 0f
    weaveRange = 0f
    guardShards = 0
    guardUntil = 0f
    guardDamage = 0f
    voidAnchorId = -1
    voidAnchorUntil = 0f
    overcurrentReadyAt = 0f
    flameRefundReadyAt = 0f
    erasureLatchId = -1
    coreImpulseReadyAt = 0f
    primedDashUntil = 0f
    dashHeatBank = 0f
    phaseUntil = 0f
    shotErasedDispatches = 0
    markDispatches = 0
    shotsErased = 0L
    pendingActiveItemId = null
    pendingActiveReborn = false
    skillBufferMask = 0
    skillBufferTime = 0f
    apexBufferTime = 0f
}
