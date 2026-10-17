// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
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

// ---------------------------------------------------------------------------------------------
// Reads.

internal fun stacks(enemy: Enemy, element: StatusElement): Int = enemy.status[StatusCell.STACKS + element.ordinal].toInt()

internal fun totalStacks(enemy: Enemy): Int {
    var total = 0
    for (index in 0 until 5) total += enemy.status[StatusCell.STACKS + index].toInt()
    return total
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

/** Movement scale applied to the enemy's integration and attack cadence. */
internal fun MutableGameState.enemySpeedScale(enemy: Enemy): Float {
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
    // An immune aspect refuses the landing element before any side effect.
    if (enemy.aspectIsImmune && enemy.aspectElement == landing.ordinal) {
        signalImmunity(enemy)
        return
    }
    if (converted && build.pairing(RelicPairingId.THERMAL_PARADOX)) heat = max(0f, heat - 3f * amount)
    val affinityCarry = when (landing.ordinal) {
        enemy.aspectElement -> StatusCell.RESIST_CARRY
        enemy.weaknessElement -> StatusCell.WEAK_CARRY
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
    val scale = runScale()
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
            shockwave(enemy.x, enemy.y, 0.28f, radius, 4)
        }
        StatusElement.CRYO -> {
            val immunity = when {
                enemy.isBoss -> ARCHITECT_FREEZE_IMMUNITY_SECONDS
                enemy.isElite -> ELITE_FREEZE_IMMUNITY_SECONDS
                else -> FREEZE_IMMUNITY_SECONDS
            }
            // The Herald never freezes; its Break still rearms and leaves it Brittle.
            val duration = if (enemy.isMiniBoss) 0f else if (enemy.isBoss) 0.5f else min(2f, power)
            enemy.status[StatusCell.FROZEN_UNTIL] = elapsed + duration
            enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] = elapsed + duration + immunity
            enemy.status[StatusCell.REARM_AT + element.ordinal] = elapsed + duration + BREAK_REARM_SECONDS
            enemy.status[StatusCell.BRITTLE] = 1f
            enemy.status[StatusCell.CRYO_IMMUNE_CARRY] = 0f
            enemy.vx = 0f
            enemy.vy = 0f
            if (build.pairing(RelicPairingId.EXTINGUISHED) && enemy.status[StatusCell.CINDER_BANK] > 0f) {
                dealStatusDamage(enemy, enemy.status[StatusCell.CINDER_BANK] * 0.6f, StatusElement.THERMAL)
                enemy.status[StatusCell.CINDER_BANK] = 0f
            }
            shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 2.4f, 1)
        }
        StatusElement.ION -> {
            val damage = power * count * potency * scale
            chain(enemy.x, enemy.y, enemy.id, 3, definition.breakRadius) { target ->
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
                    val knockback = target.knockback
                    if (knockback > 0f) {
                        val dx = centerX - target.x
                        val dy = centerY - target.y
                        val distance = max(1f, length(dx, dy))
                        target.vx += dx / distance * 520f * knockback
                        target.vy += dy / distance * 520f * knockback
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
            shockwave(enemy.x, enemy.y, 0.32f, definition.breakRadius, 3)
        }
        StatusElement.ENTROPY -> {
            if (enemy.status[StatusCell.MAX_HP_PROC_READY_AT] <= elapsed) {
                enemy.status[StatusCell.MAX_HP_PROC_READY_AT] = elapsed + maxHpProcCooldown(enemy)
                dealStatusDamage(enemy, enemy.maxHp * victimFactor(enemy) * definition.breakPower, element)
            }
            if (build.pairing(RelicPairingId.BLIGHT_GARDEN) && relicCooldowns[RelicId.VERDANT_ROT.ordinal] <= 0f &&
                distanceSquared(enemy.x, enemy.y, coreX, coreY) <= square(VERDANT_RADIUS)
            ) {
                relicCooldowns[RelicId.VERDANT_ROT.ordinal] = BLIGHT_GARDEN_HEAL_COOLDOWN
                heal(BLIGHT_GARDEN_HEAL, HealSource.VERDANT)
            }
            shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 2.2f, 4)
        }
    }
    if (affinityTier(element) >= 3) applyStatusAround(enemy.x, enemy.y, 130f, element, 2f, outputGeneration, enemy.id)
    recordStatusCue(VisualFxCue.StatusBroken(enemy.x, enemy.y, element.ordinal))
    if (generation == 0 || listens(EffectTrigger.BREAK)) onBreak(enemy, element)
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
        if (totalStacks(enemy) == 0) continue
        for (element in StatusElement.entries) {
            val cell = StatusCell.STACKS + element.ordinal
            var count = enemy.status[cell].toInt()
            if (count <= 0) continue
            val decayCell = StatusCell.DECAY_AT + element.ordinal
            if (enemy.status[decayCell] <= elapsed) {
                count--
                enemy.status[cell] = count.toFloat()
                enemy.status[decayCell] = elapsed + decaySeconds(element)
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
        val knockback = enemy.knockback
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

/** Contagion and banked fire on death. Runs for every death, eligible or not. */
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

/** Packed per-enemy status for presentation: five 4-bit stack counts, then state flags. */
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
    )
}

internal const val STATUS_FROZEN_BIT: Int = 1 shl 20
internal const val STATUS_BRITTLE_BIT: Int = 1 shl 21
internal const val STATUS_EMBRITTLED_BIT: Int = 1 shl 22
internal const val STATUS_PRIMED_BIT: Int = 1 shl 23

internal val MutableGameState.anchorBreaksOnRelease: Boolean get() = tagTier(BuildTag.ANCHOR) >= 3
