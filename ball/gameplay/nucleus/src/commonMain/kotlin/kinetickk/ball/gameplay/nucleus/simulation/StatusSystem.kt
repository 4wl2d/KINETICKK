// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal const val FREEZE_IMMUNITY_SECONDS: Float = 2.5f
internal const val ELITE_FREEZE_IMMUNITY_SECONDS: Float = 5f
internal const val ARCHITECT_FREEZE_IMMUNITY_SECONDS: Float = 8f
internal const val BREAK_REARM_SECONDS: Float = 1f
internal const val ENTROPY_DAMAGE_PER_STACK: Float = 0.8f
internal const val DOT_PULSES_PER_SECOND: Float = 4f
internal const val MAX_STATUS_CUES_PER_STEP: Int = 6
internal const val BLIGHT_GARDEN_HEAL: Float = 4f
internal const val BLIGHT_GARDEN_HEAL_COOLDOWN: Float = 0.5f

/** Resonance (Sonic): each stack adds this fraction of impact and dash damage taken. */
internal const val RESONANCE_IMPACT_PER_STACK: Float = 0.03f
/** Hollow (Void): each stack remembers this fraction of the damage the enemy takes. */
internal const val HOLLOW_MEMORY_PER_STACK: Float = 0.02f
/** Hollow never cuts an enemy's damage output below this factor. */
internal const val HOLLOW_OUTPUT_FLOOR: Float = 0.5f
/** Delay between an Unmake and the strike that releases the remembered damage. */
internal const val UNMAKE_DELAY_SECONDS: Float = 0.35f
/** An Unmake takes at most this share of the victim's maximum integrity (weighted for bosses). */
internal const val UNMAKE_INTEGRITY_CAP: Float = 0.4f
/** Velocity a Shatter gives the enemies it throws, before their knockback factor. */
internal const val SHATTER_THROW_SPEED: Float = 680f
/** Thunder Choir: radius in which a Shatter looks for Resonant enemies. */
internal const val THUNDER_CHOIR_RADIUS: Float = 260f
/** Thunder Choir: bonus to the knockback you deal, and the extra recoil you take. */
internal const val THUNDER_CHOIR_KNOCKBACK: Float = 0.2f
internal const val THUNDER_CHOIR_RECOIL: Float = 0.15f
/** Null Covenant: share of maximum integrity the liability removes. */
internal const val NULL_COVENANT_INTEGRITY_CUT: Float = 0.1f

// ---------------------------------------------------------------------------------------------
// Reads.

internal fun stacks(enemy: Enemy, element: StatusElement): Int = enemy.status[StatusCell.STACKS + element.ordinal].toInt()

internal fun totalStacks(enemy: Enemy): Int {
    var total = 0
    for (index in 0 until StatusCell.ELEMENTS) total += enemy.status[StatusCell.STACKS + index].toInt()
    return total
}

/**
 * The Architect, the mini-bosses and the secret bosses: immune to executes, stops and stuns, only briefly silenced,
 * and Unmake meets their weighted integrity. Every element rule and [canAct] ask this one question.
 */
internal val Enemy.isBossClass: Boolean get() = isBoss || isMiniBoss || isSecretBoss

/** Elites and the bosses; Null Covenant keeps Hollow on them. */
internal val Enemy.isEliteClass: Boolean get() = isElite || isBossClass

/**
 * Displacement [enemy] takes from anything the player causes: its frame's knockback, raised by each
 * Resonance stack and by Thunder Choir. Frames that never move (knockback 0) stay immovable.
 */
internal fun MutableGameState.knockbackTaken(enemy: Enemy): Float {
    val frame = enemy.knockback
    if (frame <= 0f) return 0f
    var scale = 1f
    val resonance = stacks(enemy, StatusElement.SONIC)
    if (resonance > 0) scale += resonance * content.build.element(StatusElement.SONIC).perStack * build.potency(StatusElement.SONIC)
    if (build.upside(RelicId.THUNDER_CHOIR)) scale *= 1f + THUNDER_CHOIR_KNOCKBACK * build.relicUpside(RelicId.THUNDER_CHOIR)
    return frame * scale
}

internal fun MutableGameState.isFrozen(enemy: Enemy): Boolean = enemy.status[StatusCell.FROZEN_UNTIL] > elapsed

internal fun generation(enemy: Enemy, element: StatusElement): Int = enemy.status[StatusCell.GENERATION + element.ordinal].toInt()

/** Effective Break threshold for [element] on [enemy], respecting the content floor. */
internal fun MutableGameState.breakThreshold(element: StatusElement, enemy: Enemy? = null): Int {
    val definition = content.build.element(element)
    var threshold = definition.breakThreshold - build.thresholdCut(element)
    if (element == StatusElement.THERMAL && build.upside(RelicId.CINDER_CROWN)) threshold = min(threshold, 5)
    if (element == StatusElement.CRYO && enemy != null && enemy.status[StatusCell.EMBRITTLED_UNTIL] > elapsed) threshold -= 2
    return max(definition.breakFloor, threshold)
}

internal fun MutableGameState.statusNearBreak(enemy: Enemy, fraction: Float): Boolean {
    for (element in StatusElement.entries) {
        val count = stacks(enemy, element)
        if (count > 0 && count >= breakThreshold(element, enemy) * fraction) return true
    }
    return false
}

/**
 * Scale of [enemy]'s integration and attack cadence: zero while Frozen, slowed by Chill and stasis. A
 * stunned or stopped enemy neither steers nor counts down an attack, but still drifts at this scale while
 * its momentum bleeds off.
 */
internal fun MutableGameState.enemyMotionScale(enemy: Enemy): Float {
    if (isFrozen(enemy)) return 0f
    var scale = 1f
    val chill = stacks(enemy, StatusElement.CRYO)
    if (chill > 0) {
        val slow = min(0.63f, chill * content.build.element(StatusElement.CRYO).perStack * build.potency(StatusElement.CRYO))
        scale *= 1f - if (enemy.isBoss || enemy.isMiniBoss) slow * 0.5f else slow
    }
    if (stasisActive() && distanceSquared(enemy.x, enemy.y, coreX, coreY) <= square(stasisRadius())) {
        scale *= 1f - stasisSlow()
    }
    if (build.liability(RelicId.PANDEMIC_CODEX) && totalStacks(enemy) > 0) scale *= 1.2f
    // Hypercube's cage slows the ordinary enemies inside.
    scale *= hypercubeMotionScale(enemy)
    scale *= layerTimeScale(enemy)
    return scale
}

// ---------------------------------------------------------------------------------------------
// Application.

/**
 * Applies [count] stacks of [element] at [generation]. Conversions, caps, reactions and Breaks resolve
 * here. Outputs at generation two or above are dropped by the caller's rules, never here.
 */
internal fun MutableGameState.applyStatus(enemy: Enemy, element: StatusElement, count: Int, generation: Int) {
    if (count <= 0 || !enemy.alive) return
    if (generation >= 2) return
    if (isShifting(enemy)) return
    var landing = element
    var amount = count
    val converted = element == StatusElement.THERMAL && build.liability(RelicId.ABSOLUTE_ZERO)
    if (converted) {
        amount /= 2
        landing = StatusElement.CRYO
        if (amount <= 0) return
    }
    // A Suppressed enemy has no aspect: no immunity, resistance or weakness.
    val suppressed = markIs(enemy, MarkKind.SUPPRESSED)
    // An immune aspect refuses the landing element before any side effect.
    if (!suppressed && enemy.aspectIsImmune && enemy.aspectElement == landing.ordinal) {
        signalImmunity(enemy)
        return
    }
    if (converted && build.pairing(RelicPairingId.THERMAL_PARADOX)) heat = max(0f, heat - 3f * amount)
    val affinityCarry = when {
        suppressed -> -1
        landing.ordinal == enemy.aspectElement -> StatusCell.RESIST_CARRY
        landing.ordinal == enemy.weaknessElement -> StatusCell.WEAK_CARRY
        else -> -1
    }
    if (affinityCarry >= 0) {
        // Resisted stacks land at half strength, the weakness at one and a half; fractions carry.
        val scaled = enemy.status[affinityCarry] + amount * if (affinityCarry == StatusCell.RESIST_CARRY) 0.5f else 1.5f
        amount = scaled.toInt()
        enemy.status[affinityCarry] = scaled - amount
        if (amount <= 0) return
    }
    if (landing == StatusElement.CRYO && enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] > elapsed && !isFrozen(enemy)) {
        // Half strength while immune; an odd half stack carries to the next Cryo landing.
        val half = enemy.status[StatusCell.CRYO_IMMUNE_CARRY] + amount * 0.5f
        amount = half.toInt()
        enemy.status[StatusCell.CRYO_IMMUNE_CARRY] = half - amount
        if (amount <= 0) return
    }
    val definition = content.build.element(landing)
    val index = landing.ordinal
    val before = enemy.status[StatusCell.STACKS + index].toInt()
    val after = min(definition.stackCap, before + amount)
    enemy.status[StatusCell.STACKS + index] = after.toFloat()
    val generationCell = StatusCell.GENERATION + index
    enemy.status[generationCell] = if (before == 0) generation.toFloat() else max(enemy.status[generationCell], generation.toFloat())
    enemy.status[StatusCell.DECAY_AT + index] = elapsed + decaySeconds(landing)
    if (landing == StatusElement.CRYO && build.pairing(RelicPairingId.EXTINGUISHED)) {
        enemy.status[StatusCell.CINDER_BANK] += amount * 3f * runScale()
    }
    tryReact(enemy, landing, generation)
    if (enemy.alive) tryBreak(enemy, landing)
}

/**
 * Sets an Elite's or the Architect's aspect. An immune element loses its stacks, so nothing stale keeps
 * reacting or adding vulnerability; both affinity carries restart.
 */
internal fun assignAspect(enemy: Enemy, element: Int, immune: Boolean) {
    enemy.aspect = element
    enemy.aspectImmune = immune && element >= 0
    if (enemy.aspectImmune) {
        enemy.status[StatusCell.STACKS + element] = 0f
        enemy.status[StatusCell.GENERATION + element] = 0f
        enemy.status[StatusCell.DECAY_AT + element] = 0f
        forgetHollowWhenEmpty(enemy)
    }
    enemy.status[StatusCell.RESIST_CARRY] = 0f
    enemy.status[StatusCell.WEAK_CARRY] = 0f
}

/** Feedback when an immune aspect refuses stacks or damage, at most once per [IMMUNE_CUE_SECONDS] per enemy. */
internal fun MutableGameState.signalImmunity(enemy: Enemy) {
    if (enemy.status[StatusCell.IMMUNE_FX_READY_AT] > elapsed) return
    enemy.status[StatusCell.IMMUNE_FX_READY_AT] = elapsed + IMMUNE_CUE_SECONDS
    recordStatusCue(VisualFxCue.Burst(enemy.x, enemy.y, 3, IMMUNE_CUE_COLOR, settings.particleDensity))
}

internal const val IMMUNE_CUE_SECONDS: Float = 0.25f
private const val IMMUNE_CUE_COLOR: Int = 2

/** Applies stacks around a point, generation +1 rules handled by the caller passing [generation]. */
internal fun MutableGameState.applyStatusAround(
    x: Float,
    y: Float,
    radius: Float,
    element: StatusElement,
    count: Float,
    generation: Int,
    excludedId: Int = -1,
) {
    val whole = count.toInt().coerceAtLeast(1)
    if (generation >= 2) return
    for (index in enemies.indices) {
        val target = enemies[index]
        if (target.id == excludedId || !target.alive) continue
        if (distanceSquared(x, y, target.x, target.y) <= square(radius + target.radius)) applyStatus(target, element, whole, generation)
    }
}

internal fun MutableGameState.decaySeconds(element: StatusElement): Float {
    var seconds = content.build.element(element).decaySeconds * build.durationMultiplier(element)
    if (element == StatusElement.THERMAL && build.liability(RelicId.CINDER_CROWN)) seconds *= 0.5f
    return seconds
}

// ---------------------------------------------------------------------------------------------
// Breaks.

private fun MutableGameState.tryBreak(enemy: Enemy, element: StatusElement, forced: Boolean = false) {
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
            val spread = build.breakSpread(element)
            if (spread > 0) applyStatusAround(enemy.x, enemy.y, build.breakSpreadRadius(element), element, spread.toFloat(), outputGeneration, enemy.id)
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
private fun MutableGameState.strikeUnmake(enemy: Enemy, damage: Float) {
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
    enemy.isBoss -> 4f
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

// ---------------------------------------------------------------------------------------------
// Ticking.

internal fun MutableGameState.updateStatuses(delta: Float) {
    val policy = content.build.policy
    breakTokens = min(MutableGameState.BREAK_BURST, breakTokens + policy.breaksPerSecond * delta)
    reactionTokens = min(MutableGameState.REACTION_BURST, reactionTokens + policy.reactionsPerSecond * delta)
    val scale = runScale()
    var centroidX = 0f
    var centroidY = 0f
    var centroidWeight = 0f
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val gravitic = stacks(enemy, StatusElement.GRAVITIC)
        if (gravitic > 0) {
            centroidX += enemy.x * gravitic
            centroidY += enemy.y * gravitic
            centroidWeight += gravitic
        }
    }
    if (centroidWeight > 0f) {
        centroidX /= centroidWeight
        centroidY /= centroidWeight
    }
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        if (enemy.hitQualificationCooldown.toRawBits() != 0) {
            enemy.hitQualificationCooldown = max(0f, enemy.hitQualificationCooldown - delta)
        }
        val unmakeAt = enemy.status[StatusCell.UNMAKE_AT]
        if (unmakeAt > 0f && unmakeAt <= elapsed) {
            val damage = enemy.status[StatusCell.UNMAKE_DAMAGE]
            enemy.status[StatusCell.UNMAKE_AT] = 0f
            enemy.status[StatusCell.UNMAKE_DAMAGE] = 0f
            strikeUnmake(enemy, damage)
            if (!enemy.alive) continue
        }
        if (enemy.status[StatusCell.BLACKFIRE_UNTIL] > 0f) burnBlackfire(enemy, delta)
        if (!enemy.alive || totalStacks(enemy) == 0) continue
        for (element in StatusElement.entries) {
            val cell = StatusCell.STACKS + element.ordinal
            var count = enemy.status[cell].toInt()
            if (count <= 0) continue
            val decayCell = StatusCell.DECAY_AT + element.ordinal
            if (enemy.status[decayCell] <= elapsed) {
                if (element == StatusElement.VOID && enemy.isEliteClass && build.upside(RelicId.NULL_COVENANT)) {
                    // Null Covenant: Hollow never fades on Elites; the timer only rolls forward.
                    enemy.status[decayCell] = elapsed + decaySeconds(element)
                    continue
                }
                count--
                enemy.status[cell] = count.toFloat()
                enemy.status[decayCell] = elapsed + decaySeconds(element)
                if (element == StatusElement.VOID) forgetHollowWhenEmpty(enemy)
                if (count <= 0) continue
            }
        }
        // Damage-over-time pulses at 4 Hz, staggered by enemy id, without per-step damage numbers.
        val phase = (enemy.id % 30) / 30f
        val pulse = floor((elapsed + phase / DOT_PULSES_PER_SECOND) * DOT_PULSES_PER_SECOND) >
            floor((elapsed - delta + phase / DOT_PULSES_PER_SECOND) * DOT_PULSES_PER_SECOND)
        if (pulse) {
            val thermal = stacks(enemy, StatusElement.THERMAL)
            if (thermal > 0) {
                val damage = thermal * content.build.element(StatusElement.THERMAL).perStack *
                    build.potency(StatusElement.THERMAL) * scale / DOT_PULSES_PER_SECOND
                if (build.upside(RelicId.CINDER_CROWN)) {
                    enemy.status[StatusCell.CINDER_BANK] += damage *
                        content.relic(RelicId.CINDER_CROWN).value(0, relicRank(RelicId.CINDER_CROWN)) / 100f *
                        build.relicUpside(RelicId.CINDER_CROWN)
                } else {
                    dealStatusDamage(enemy, damage, StatusElement.THERMAL, showNumber = false)
                }
            }
            val entropy = stacks(enemy, StatusElement.ENTROPY)
            if (entropy > 0 && enemy.alive) {
                dealStatusDamage(enemy, entropy * ENTROPY_DAMAGE_PER_STACK * build.potency(StatusElement.ENTROPY) * scale /
                    DOT_PULSES_PER_SECOND, StatusElement.ENTROPY, showNumber = false)
            }
        }
        val gravitic = stacks(enemy, StatusElement.GRAVITIC)
        val knockback = knockbackTaken(enemy)
        if (gravitic > 0 && centroidWeight > gravitic && knockback > 0f && !isFrozen(enemy)) {
            val dx = centroidX - enemy.x
            val dy = centroidY - enemy.y
            val distance = length(dx, dy)
            if (distance > 1f) {
                val drift = min(40f, 6f * gravitic) * delta * knockback
                enemy.x += dx / distance * min(drift, distance)
                enemy.y += dy / distance * min(drift, distance)
            }
        }
    }
}

/**
 * Blackfire: burns at its per-second rate in the 4 Hz damage pulses until it ends, ignoring the
 * target's aspect (resistance and immunity alike).
 */
private fun MutableGameState.burnBlackfire(enemy: Enemy, delta: Float) {
    if (enemy.status[StatusCell.BLACKFIRE_UNTIL] <= elapsed) {
        enemy.status[StatusCell.BLACKFIRE_UNTIL] = 0f
        enemy.status[StatusCell.BLACKFIRE_DPS] = 0f
        return
    }
    val phase = (enemy.id % 30) / 30f
    val pulse = floor((elapsed + phase / DOT_PULSES_PER_SECOND) * DOT_PULSES_PER_SECOND) >
        floor((elapsed - delta + phase / DOT_PULSES_PER_SECOND) * DOT_PULSES_PER_SECOND)
    if (pulse) {
        dealStatusDamage(enemy, enemy.status[StatusCell.BLACKFIRE_DPS] / DOT_PULSES_PER_SECOND, StatusElement.THERMAL,
            showNumber = false, ignoresAffinity = true)
    }
}

/**
 * Freezes [enemy] the way a Freeze Break does: ordinary enemies and Elites for [seconds], the Architect for half
 * a second, the Herald not at all (its Break still rearms and leaves it Brittle). The target stops, turns Brittle
 * and resists the next Freeze for a while.
 */
internal fun MutableGameState.freeze(enemy: Enemy, seconds: Float) {
    val immunity = when {
        enemy.isBoss -> ARCHITECT_FREEZE_IMMUNITY_SECONDS
        enemy.isElite -> ELITE_FREEZE_IMMUNITY_SECONDS
        else -> FREEZE_IMMUNITY_SECONDS
    }
    val duration = if (enemy.isMiniBoss) 0f else if (enemy.isBoss) 0.5f else seconds
    enemy.status[StatusCell.FROZEN_UNTIL] = elapsed + duration
    enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] = elapsed + duration + immunity
    enemy.status[StatusCell.REARM_AT + StatusElement.CRYO.ordinal] = elapsed + duration + BREAK_REARM_SECONDS
    enemy.status[StatusCell.BRITTLE] = 1f
    enemy.status[StatusCell.CRYO_IMMUNE_CARRY] = 0f
    enemy.vx = 0f
    enemy.vy = 0f
}

/** Ends a Freeze early (a shatter) while keeping the post-thaw immunity. */
internal fun MutableGameState.thaw(enemy: Enemy) {
    if (!isFrozen(enemy)) return
    val immunity = when {
        enemy.isBoss -> ARCHITECT_FREEZE_IMMUNITY_SECONDS
        enemy.isElite -> ELITE_FREEZE_IMMUNITY_SECONDS
        else -> FREEZE_IMMUNITY_SECONDS
    }
    enemy.status[StatusCell.FROZEN_UNTIL] = elapsed
    enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] = elapsed + immunity
    enemy.status[StatusCell.REARM_AT + StatusElement.CRYO.ordinal] = elapsed + BREAK_REARM_SECONDS
}

/** Ion arcs from direct hits on charged targets; bounded by a per-enemy cooldown. */
internal fun MutableGameState.arcCharge(enemy: Enemy) {
    val charge = stacks(enemy, StatusElement.ION)
    if (charge <= 0 || enemy.status[StatusCell.ARC_READY_AT] > elapsed) return
    enemy.status[StatusCell.ARC_READY_AT] = elapsed + 0.35f
    nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 220f)?.let { target ->
        addRelicArc(enemy.x, enemy.y, target.x, target.y)
        dealStatusDamage(target, charge * content.build.element(StatusElement.ION).perStack *
            build.potency(StatusElement.ION) * runScale(), StatusElement.ION)
    }
}

/** Contagion, banked fire and the reaction verbs that read deaths. Runs for every death, eligible or not. */
internal fun MutableGameState.onStatusCarrierDied(enemy: Enemy) {
    if (build.upside(RelicId.PANDEMIC_CODEX) && totalStacks(enemy) > 0) {
        val share = min(0.8f, content.relic(RelicId.PANDEMIC_CODEX).value(0, relicRank(RelicId.PANDEMIC_CODEX)) / 100f *
            build.relicUpside(RelicId.PANDEMIC_CODEX))
        var targets = 0
        var lastId = enemy.id
        while (targets < 2) {
            val target = nearestOtherEnemyExcluding(enemy, lastId, 300f) ?: break
            lastId = target.id
            targets++
            for (element in StatusElement.entries) {
                val count = stacks(enemy, element)
                if (count <= 0) continue
                val gen = generation(enemy, element) + 1
                var passed = floor(count * share).toInt()
                if (element == StatusElement.ENTROPY && build.pairing(RelicPairingId.BLIGHT_GARDEN)) passed += 1
                if (passed > 0 && gen < 2) applyStatus(target, element, passed, gen)
            }
            addRelicArc(enemy.x, enemy.y, target.x, target.y)
        }
    }
    val bank = enemy.status[StatusCell.CINDER_BANK]
    if (bank > 0f && build.pairing(RelicPairingId.FIRESTORM)) {
        for (index in enemies.indices) {
            val target = enemies[index]
            if (target.id != enemy.id && target.alive && distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(110f + target.radius)) {
                dealStatusDamage(target, bank * 0.5f, StatusElement.THERMAL)
            }
        }
        shockwave(enemy.x, enemy.y, 0.3f, 110f, 4)
    }
    onReactionCarrierDied(enemy)
    onSkillCarrierDied(enemy)
}

private fun MutableGameState.nearestOtherEnemyExcluding(origin: Enemy, alsoExcluded: Int, range: Float): Enemy? {
    var nearest: Enemy? = null
    var best = range * range
    for (index in enemies.indices) {
        val candidate = enemies[index]
        if (candidate.id == origin.id || candidate.id == alsoExcluded || !candidate.alive || isShifting(candidate)) continue
        val d = distanceSquared(origin.x, origin.y, candidate.x, candidate.y)
        if (d <= best && (nearest == null || d < best)) {
            nearest = candidate
            best = d
        }
    }
    return nearest
}

/**
 * Marks [enemy] with [kind] for [seconds]; [cast] records that a skill cast wrote it. An enemy carries one mark:
 * a newer mark replaces an older one, whatever its kind or writer.
 */
internal fun MutableGameState.setMark(enemy: Enemy, kind: MarkKind, seconds: Float, cast: Boolean = false) {
    enemy.status[StatusCell.MARK] = (kind.ordinal + 1).toFloat()
    enemy.status[StatusCell.MARK_UNTIL] = elapsed + seconds
    enemy.status[StatusCell.MARK_CAST] = if (cast) 1f else 0f
}

/** The live mark on [enemy], or null (none, or lapsed). */
internal fun MutableGameState.markOf(enemy: Enemy): MarkKind? {
    if (enemy.status[StatusCell.MARK_UNTIL] <= elapsed) return null
    return MarkKind.entries.getOrNull(enemy.status[StatusCell.MARK].toInt() - 1)
}

internal fun MutableGameState.markIs(enemy: Enemy, kind: MarkKind): Boolean = markOf(enemy) == kind

/** Cashes a live [kind] mark: clears it and returns true; any other mark stays. */
internal fun MutableGameState.consumeMark(enemy: Enemy, kind: MarkKind): Boolean {
    if (!markIs(enemy, kind)) return false
    enemy.status[StatusCell.MARK] = 0f
    enemy.status[StatusCell.MARK_UNTIL] = 0f
    enemy.status[StatusCell.MARK_CAST] = 0f
    return true
}

/**
 * Packed per-enemy status for presentation: [StatusCell.ELEMENTS] 4-bit stack counts in element order
 * (bits 0..27), then the Frozen, Brittle, Embrittled and primed flags in bits 28..31.
 */
internal fun MutableGameState.statusBits(enemy: Enemy): Int {
    var bits = 0
    for (element in StatusElement.entries) {
        bits = bits or (min(15, stacks(enemy, element)) shl (element.ordinal * 4))
    }
    if (isFrozen(enemy)) bits = bits or STATUS_FROZEN_BIT
    if (enemy.status[StatusCell.BRITTLE] > 0f) bits = bits or STATUS_BRITTLE_BIT
    if (enemy.status[StatusCell.EMBRITTLED_UNTIL] > elapsed) bits = bits or STATUS_EMBRITTLED_BIT
    if (primed(enemy)) bits = bits or STATUS_PRIMED_BIT
    return bits
}

private fun MutableGameState.primed(enemy: Enemy): Boolean {
    for (element in StatusElement.entries) {
        val count = stacks(enemy, element)
        if (count > 0 && count == breakThreshold(element, enemy) - 1) return true
    }
    return false
}

/** Packed affinity for presentation; see [EnemyAffinity]. The shift flag depends on the run time. */
internal fun MutableGameState.affinityBits(enemy: Enemy): Int {
    val impact = enemy.impactTaken
    return EnemyAffinity.encode(
        aspect = enemy.aspectElement,
        immune = enemy.aspectIsImmune,
        weakness = enemy.weaknessElement,
        armored = impact < 1f,
        fragile = impact > 1f,
        shifting = isShifting(enemy),
        phase = if (enemy.isBoss || enemy.isMiniBoss) enemy.phase else 0,
        affix = enemy.affix?.ordinal ?: EnemyAffinity.NONE,
        champion = enemy.champion,
    )
}

internal const val STATUS_STACK_BITS: Int = 4 * StatusCell.ELEMENTS
internal const val STATUS_FROZEN_BIT: Int = 1 shl STATUS_STACK_BITS
internal const val STATUS_BRITTLE_BIT: Int = 1 shl (STATUS_STACK_BITS + 1)
internal const val STATUS_EMBRITTLED_BIT: Int = 1 shl (STATUS_STACK_BITS + 2)
internal const val STATUS_PRIMED_BIT: Int = 1 shl (STATUS_STACK_BITS + 3)

internal val MutableGameState.anchorBreaksOnRelease: Boolean get() = tagTier(BuildTag.ANCHOR) >= 3
