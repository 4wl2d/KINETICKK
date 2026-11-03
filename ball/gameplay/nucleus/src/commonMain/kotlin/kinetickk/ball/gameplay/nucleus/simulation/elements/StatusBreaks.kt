// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Breaks.

internal fun MutableGameState.tryBreak(enemy: Enemy, element: StatusElement, forced: Boolean = false) {
    if (isShifting(enemy)) return
    val index = element.ordinal
    val count = enemy.status[StatusCell.STACKS + index].toInt()
    if (count <= 0) return
    if (!forced && count < breakThreshold(element, enemy)) return
    if (enemy.status[StatusCell.REARM_AT + index] > elapsed) return
    if (element == StatusElement.CRYO && (isFrozen(enemy) || enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] > elapsed)) return
    val generation = enemy.status[StatusCell.GENERATION + index].toInt()
    if (!admitBreak(generation)) return
    enemy.status[StatusCell.STACKS + index] = 0f
    enemy.status[StatusCell.REARM_AT + index] = elapsed + BREAK_REARM_SECONDS
    resolveBreak(enemy, element, count, generation)
}

/** Forces [element]'s Break on [enemy] now if it holds any stacks (a weapon form's Freeze, Overload or Shatter). */
internal fun MutableGameState.forceElementBreak(enemy: Enemy, element: StatusElement) {
    tryBreak(enemy, element, forced = true)
}

/** Forces every status on [enemy] that is at least [fraction] of its threshold to Break (ANCHOR III). */
internal fun MutableGameState.forceBreaks(enemy: Enemy, fraction: Float) {
    for (element in StatusElement.entries) {
        val count = stacks(enemy, element)
        if (count > 0 && count >= breakThreshold(element, enemy) * fraction) tryBreak(enemy, element, forced = true)
    }
}

private fun MutableGameState.resolveBreak(enemy: Enemy, element: StatusElement, count: Int, generation: Int) {
    val definition = content.build.element(element)
    val power = definition.breakPower * build.breakPower(element)
    val potency = build.potency(element)
    // Damage dealt by the Break itself; the Freeze's duration and the spread stacks are not damage.
    val scale = runScale() * breakDamageScale()
    val outputGeneration = generation + 1
    when (element) {
        StatusElement.THERMAL -> {
            var damage = power * count * potency * scale
            val bank = enemy.status[StatusCell.CINDER_BANK]
            if (bank > 0f && build.upside(RelicId.CINDER_CROWN)) {
                damage += bank
                enemy.status[StatusCell.CINDER_BANK] = 0f
            }
            val radius = definition.breakRadius
            for (targetIndex in enemies.indices) {
                val target = enemies[targetIndex]
                if (target.alive && distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(radius + target.radius)) {
                    dealStatusDamage(target, damage, element)
                }
            }
            if (generation == 0) {
                if (build.upside(RelicId.CINDER_CROWN)) {
                    applyStatusAround(enemy.x, enemy.y, 110f, StatusElement.THERMAL, 2f, outputGeneration, enemy.id)
                }
                // The self-burn is the liability, so it lingers through severance; it is not an incoming hit.
                if (build.liability(RelicId.CINDER_CROWN) && cinderRecoilReadyAt <= elapsed && invulnerableUntil <= elapsed &&
                    distanceSquared(enemy.x, enemy.y, coreX, coreY) <= square(110f)
                ) {
                    cinderRecoilReadyAt = elapsed + 0.75f
                    loseIntegrity(maxHp * 0.04f)
                }
            }
            shockwave(enemy.x, enemy.y, 0.28f, radius, FxColorCode.elementTone(element))
        }
        StatusElement.CRYO -> {
            freeze(enemy, min(2f, power))
            if (build.pairing(RelicPairingId.EXTINGUISHED) && enemy.status[StatusCell.CINDER_BANK] > 0f) {
                dealStatusDamage(enemy, enemy.status[StatusCell.CINDER_BANK] * 0.6f, StatusElement.THERMAL)
                enemy.status[StatusCell.CINDER_BANK] = 0f
            }
            shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 2.4f, FxColorCode.elementTone(element))
        }
        StatusElement.ION -> {
            val damage = power * count * potency * scale
            chain(enemy.x, enemy.y, enemy.id, 3, definition.breakRadius, LinkStyleCode.elementBreak(element)) { target ->
                dealStatusDamage(target, damage, element)
                if (outputGeneration < 2) applyStatus(target, StatusElement.ION, 2, outputGeneration)
            }
            dealStatusDamage(enemy, damage, element)
        }
        StatusElement.GRAVITIC -> {
            val towardCore = build.upside(RelicId.EVENT_HORIZON)
            val centerX = if (towardCore) coreX else enemy.x
            val centerY = if (towardCore) coreY else enemy.y
            var pulled = 0
            for (targetIndex in enemies.indices) {
                val target = enemies[targetIndex]
                if (target.id == enemy.id || !target.alive) continue
                if (distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(definition.breakRadius + target.radius)) {
                    pulled++
                    val knockback = knockbackTaken(target)
                    if (knockback > 0f) {
                        val dx = centerX - target.x
                        val dy = centerY - target.y
                        val distance = max(1f, length(dx, dy))
                        target.vx += dx / distance * 520f * knockback
                        target.vy += dy / distance * 520f * knockback
                        markThrown(target)
                    }
                }
            }
            var damage = power * count * (1f + min(pulled, 5)) * potency * scale
            if (towardCore && build.pairing(RelicPairingId.BLACK_STAR)) damage *= 1.5f
            for (targetIndex in enemies.indices) {
                val target = enemies[targetIndex]
                if (target.alive && distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(definition.breakRadius + target.radius)) {
                    dealStatusDamage(target, damage, element)
                }
            }
            shockwave(enemy.x, enemy.y, 0.32f, definition.breakRadius, FxColorCode.elementTone(element))
        }
        StatusElement.ENTROPY -> {
            if (enemy.status[StatusCell.MAX_HP_PROC_READY_AT] <= elapsed) {
                enemy.status[StatusCell.MAX_HP_PROC_READY_AT] = elapsed + maxHpProcCooldown(enemy)
                dealStatusDamage(enemy, enemy.maxHp * victimFactor(enemy) * definition.breakPower * breakDamageScale(), element)
            }
            if (build.pairing(RelicPairingId.BLIGHT_GARDEN) && relicCooldowns[RelicId.VERDANT_ROT.ordinal] <= 0f &&
                distanceSquared(enemy.x, enemy.y, coreX, coreY) <= square(VERDANT_RADIUS)
            ) {
                relicCooldowns[RelicId.VERDANT_ROT.ordinal] = BLIGHT_GARDEN_HEAL_COOLDOWN
                heal(BLIGHT_GARDEN_HEAL, HealSource.VERDANT)
            }
            shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 2.2f, FxColorCode.elementTone(element))
        }
        StatusElement.SONIC -> {
            val damage = power * count * potency * scale
            val radius = definition.breakRadius
            for (targetIndex in enemies.indices) {
                val target = enemies[targetIndex]
                if (!target.alive || distanceSquared(enemy.x, enemy.y, target.x, target.y) > square(radius + target.radius)) continue
                dealStatusDamage(target, damage, element)
                throwFrom(enemy, target)
            }
            if (build.upside(RelicId.THUNDER_CHOIR)) thunderChoir(enemy, damage)
            shockwave(enemy.x, enemy.y, 0.34f, radius, FxColorCode.elementTone(element))
        }
        StatusElement.VOID -> {
            var remembered = enemy.status[StatusCell.HOLLOW_MEMORY]
            enemy.status[StatusCell.HOLLOW_MEMORY] = 0f
            remembered *= power * breakDamageScale()
            val cap = enemy.maxHp * UNMAKE_INTEGRITY_CAP * if (enemy.isBossClass) victimFactor(enemy) else 1f
            val damage = min(remembered, cap)
            if (damage > 0f) {
                if (build.upside(RelicId.NULL_COVENANT)) {
                    strikeUnmake(enemy, damage)
                } else {
                    enemy.status[StatusCell.UNMAKE_AT] = elapsed + UNMAKE_DELAY_SECONDS
                    enemy.status[StatusCell.UNMAKE_DAMAGE] = enemy.status[StatusCell.UNMAKE_DAMAGE] + damage
                }
            }
            shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 2.6f, FxColorCode.elementTone(element))
        }
    }
    val spread = build.breakSpread(element)
    if (spread > 0) applyStatusAround(enemy.x, enemy.y, build.breakSpreadRadius(element), element, spread.toFloat(), outputGeneration, enemy.id)
    if (affinityTier(element) >= 3) applyStatusAround(enemy.x, enemy.y, 130f, element, 2f, outputGeneration, enemy.id)
    recordStatusCue(VisualFxCue.StatusBroken(enemy.x, enemy.y, element.ordinal))
    if (generation == 0 || listens(EffectTrigger.BREAK)) onBreak(enemy, element)
}

/** Shatter's throw: away from the Shatter's center, or away from the Core for the shattered enemy. */
private fun MutableGameState.throwFrom(center: Enemy, target: Enemy) {
    val knockback = knockbackTaken(target)
    if (knockback <= 0f) return
    var dx = target.x - center.x
    var dy = target.y - center.y
    if (target.id == center.id || length(dx, dy) < 1f) {
        dx = target.x - coreX
        dy = target.y - coreY
    }
    val distance = max(1f, length(dx, dy))
    target.vx += dx / distance * SHATTER_THROW_SPEED * knockback
    target.vy += dy / distance * SHATTER_THROW_SPEED * knockback
    markThrown(target)
}

/**
 * Thunder Choir: the Shatter jumps to the nearest Resonant enemies within [THUNDER_CHOIR_RADIUS] of the
 * shattered one, nearest first, without scratch storage (each pick is the next one by distance, then id).
 */
private fun MutableGameState.thunderChoir(origin: Enemy, shatterDamage: Float) {
    // The link count is the rank's own number; harmony and dissonance scale only the damage share.
    val links = content.relic(RelicId.THUNDER_CHOIR).value(0, relicRank(RelicId.THUNDER_CHOIR)).toInt()
        .coerceIn(0, MAX_CHAIN_TARGETS)
    val share = relicValue(RelicId.THUNDER_CHOIR, 1) / 100f
    val rangeSquared = square(THUNDER_CHOIR_RADIUS)
    var lastDistance = -1f
    var lastId = Int.MIN_VALUE
    repeat(links) {
        var picked: Enemy? = null
        var pickedDistance = Float.MAX_VALUE
        for (index in enemies.indices) {
            val candidate = enemies[index]
            if (candidate.id == origin.id || !candidate.alive || isShifting(candidate)) continue
            if (stacks(candidate, StatusElement.SONIC) <= 0) continue
            val distance = distanceSquared(origin.x, origin.y, candidate.x, candidate.y)
            if (distance > rangeSquared) continue
            val after = distance > lastDistance || (distance == lastDistance && candidate.id > lastId)
            if (!after) continue
            if (distance < pickedDistance || (distance == pickedDistance && candidate.id < (picked?.id ?: Int.MAX_VALUE))) {
                picked = candidate
                pickedDistance = distance
            }
        }
        val target = picked ?: return
        lastDistance = pickedDistance
        lastId = target.id
        addRelicArc(origin.x, origin.y, target.x, target.y)
        dealStatusDamage(target, shatterDamage * share, StatusElement.SONIC)
    }
}

/** Releases an Unmake: the remembered damage strikes as Void status damage. */
internal fun MutableGameState.strikeUnmake(enemy: Enemy, damage: Float) {
    dealStatusDamage(enemy, damage, StatusElement.VOID)
    burst(enemy.x, enemy.y, 6, 2)
}

/**
 * Memory belongs to Hollow: once the last Void stack is gone (decayed, spent by a reaction or refused by an
 * immune aspect), nothing is remembered. Every writer that can empty the Void stacks calls this after it.
 */
internal fun forgetHollowWhenEmpty(enemy: Enemy) {
    if (stacks(enemy, StatusElement.VOID) <= 0) enemy.status[StatusCell.HOLLOW_MEMORY] = 0f
}

/**
 * Hollow (Void) memory: while the enemy carries Hollow it remembers a share of every damage it takes,
 * bounded by its maximum integrity. Called by the single enemy-integrity writer. Like every base derived
 * from a hit, the memory is measured without the Core Matrix damage multiplier, which the Unmake strike
 * applies once as a damage instance of its own.
 */
internal fun MutableGameState.rememberHollowDamage(enemy: Enemy, dealt: Float) {
    val hollow = stacks(enemy, StatusElement.VOID)
    if (hollow <= 0 || dealt <= 0f) return
    val applied = dealt / matrix.damageMultiplier
    var share = hollow * HOLLOW_MEMORY_PER_STACK * build.potency(StatusElement.VOID)
    if (build.upside(RelicId.NULL_COVENANT)) share *= 1f + relicValue(RelicId.NULL_COVENANT, 0) / 100f
    enemy.status[StatusCell.HOLLOW_MEMORY] = min(enemy.maxHp, enemy.status[StatusCell.HOLLOW_MEMORY] + applied * share)
}

/**
 * Hollow (Void) on the attacking enemy: each stack cuts its damage output, never below
 * [HOLLOW_OUTPUT_FLOOR]. 1 for sourceless hazards and enemies without Hollow.
 */
internal fun MutableGameState.hollowOutputFactor(enemy: Enemy?): Float {
    if (enemy == null) return 1f
    val hollow = stacks(enemy, StatusElement.VOID)
    if (hollow <= 0) return 1f
    val cut = hollow * content.build.element(StatusElement.VOID).perStack * build.potency(StatusElement.VOID)
    return (1f - cut).coerceIn(HOLLOW_OUTPUT_FLOOR, 1f)
}

internal fun maxHpProcCooldown(enemy: Enemy): Float = when {
    enemy.isBoss || enemy.isSecretBoss -> 4f
    enemy.isElite || enemy.isMiniBoss -> 2f
    else -> 1f
}

private fun MutableGameState.admitBreak(generation: Int): Boolean {
    // Generation 0 is always admitted but still spends, never below zero, so later cascades recover quickly.
    if (generation == 0) {
        breakTokens = max(0f, breakTokens - 1f)
        return true
    }
    if (breakTokens < 1f) return false
    breakTokens -= 1f
    return true
}

internal fun MutableGameState.admitReaction(generation: Int): Boolean {
    if (generation == 0) {
        reactionTokens = max(0f, reactionTokens - 1f)
        return true
    }
    if (reactionTokens < 1f) return false
    reactionTokens -= 1f
    return true
}

internal fun MutableGameState.recordStatusCue(cue: VisualFxCue) {
    if (statusCuesThisStep < MAX_STATUS_CUES_PER_STEP) {
        statusCuesThisStep++
        emitVisualFx(cue)
    }
}
