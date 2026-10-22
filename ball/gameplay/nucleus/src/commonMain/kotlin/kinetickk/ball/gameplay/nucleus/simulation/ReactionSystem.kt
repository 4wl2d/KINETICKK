// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Fires a reaction when [incoming] lands on an enemy that already holds at least two stacks of another
 * element. The older element with the most stacks pairs (ties by element order). Bounded by per-enemy and
 * per-pair cooldowns, the generation cap and the reaction token budget.
 */
internal fun MutableGameState.tryReact(enemy: Enemy, incoming: StatusElement, incomingGeneration: Int) {
    if (!enemy.alive) return
    if (enemy.status[StatusCell.REACTION_READY_AT] > elapsed) return
    // A cascade needs a token regardless of its partner; avoid searching when none can be admitted.
    if (incomingGeneration >= 1 && reactionTokens < 1f) return
    var partner: StatusElement? = null
    var partnerStacks = 1
    for (element in StatusElement.entries) {
        if (element == incoming) continue
        val count = stacks(enemy, element)
        if (count > partnerStacks) {
            partner = element
            partnerStacks = count
        }
    }
    val older = partner ?: return
    if (partnerStacks < 2) return
    val reaction = ReactionId.between(incoming, older) ?: return
    val generation = max(incomingGeneration, generation(enemy, older))
    if (generation >= 2) return
    // Each pair keeps its own cooldown, so alternating two reactions cannot bypass it.
    if (enemy.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] > elapsed) return
    if (!admitReaction(generation)) return
    react(enemy, reaction, older, incoming, partnerStacks, generation, scale = 1f)
}

/**
 * Sets off the reaction between the two elements [enemy] holds most of (the older one needs at least two
 * stacks, the next one at least one; ties by element order), whatever the per-enemy reaction cooldown says.
 * The pair cooldown, the token budget, the generation cap and a shifting Architect still refuse it.
 * [scale] multiplies the magnitude up to [MAX_FORCED_REACTION_SCALE]. True when the reaction fired.
 */
internal fun MutableGameState.forceReaction(enemy: Enemy, generation: Int, scale: Float = 1f): Boolean {
    if (!enemy.alive || isShifting(enemy)) return false
    var older: StatusElement? = null
    var olderStacks = 1
    for (element in StatusElement.entries) {
        val count = stacks(enemy, element)
        if (count > olderStacks) {
            older = element
            olderStacks = count
        }
    }
    val first = older ?: return false
    var incoming: StatusElement? = null
    var incomingStacks = 0
    for (element in StatusElement.entries) {
        if (element == first) continue
        val count = stacks(enemy, element)
        if (count > incomingStacks) {
            incoming = element
            incomingStacks = count
        }
    }
    val second = incoming ?: return false
    val reaction = ReactionId.between(second, first) ?: return false
    val resolved = max(generation, max(generation(enemy, first), generation(enemy, second)))
    if (resolved >= 2) return false
    if (enemy.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] > elapsed) return false
    if (!admitReaction(resolved)) return false
    react(enemy, reaction, first, second, olderStacks, resolved, scale.coerceIn(0f, MAX_FORCED_REACTION_SCALE))
    return true
}

/**
 * Replays [reaction] on [enemy] as a cascade (generation 1, so it needs a reaction token) at nominal two
 * plus two stacks: no stack is consumed, no cooldown is written, nothing is discovered, no build residue
 * lands and no REACTION clause fires. [scale] multiplies the magnitude up to [MAX_FORCED_REACTION_SCALE].
 */
internal fun MutableGameState.echoReaction(enemy: Enemy, reaction: ReactionId, scale: Float = 1f): Boolean {
    if (!enemy.alive || isShifting(enemy)) return false
    if (!admitReaction(ECHO_GENERATION)) return false
    resolveReaction(enemy, reaction, ECHO_STACKS, ECHO_STACKS, reaction.first, reaction.second, ECHO_GENERATION,
        scale = scale.coerceIn(0f, MAX_FORCED_REACTION_SCALE), echo = true)
    return true
}

/** Consumes the reacting stacks, writes the cooldowns and the last-reaction cells, then resolves. */
private fun MutableGameState.react(
    enemy: Enemy,
    reaction: ReactionId,
    older: StatusElement,
    incoming: StatusElement,
    a: Int,
    generation: Int,
    scale: Float,
) {
    val b = min(3, stacks(enemy, incoming))
    enemy.status[StatusCell.STACKS + older.ordinal] = 0f
    enemy.status[StatusCell.STACKS + incoming.ordinal] = (stacks(enemy, incoming) - b).toFloat()
    if (older == StatusElement.VOID || incoming == StatusElement.VOID) forgetHollowWhenEmpty(enemy)
    enemy.status[StatusCell.REACTION_READY_AT] = elapsed + content.build.policy.reactionCooldownSeconds
    enemy.status[StatusCell.LAST_REACTION] = (reaction.ordinal + 1).toFloat()
    enemy.status[StatusCell.LAST_REACTION_AT] = elapsed
    enemy.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] =
        elapsed + content.build.policy.reactionPairCooldownSeconds
    resolveReaction(enemy, reaction, a, b, older, incoming, generation, scale)
}

/**
 * The reaction's effect for [a] older and [b] incoming stacks. [scale] multiplies the magnitude; an [echo]
 * skips the build residue, discovery and REACTION clauses.
 */
internal fun MutableGameState.resolveReaction(
    enemy: Enemy,
    reaction: ReactionId,
    a: Int,
    b: Int,
    older: StatusElement,
    incoming: StatusElement,
    generation: Int,
    scale: Float = 1f,
    echo: Boolean = false,
) {
    val definition = content.build.reaction(reaction)
    val potency = (build.potency(older) + build.potency(incoming)) * 0.5f
    val magnitude = definition.power * (a + b) * potency * build.reactionPower * runScale() * scale
    val link = LinkStyleCode.reaction(reaction)
    val residueGeneration = generation + 1
    val x = enemy.x
    val y = enemy.y
    when (reaction) {
        ReactionId.THERMAL_SHOCK -> {
            dealStatusDamage(enemy, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
            enemy.status[StatusCell.BRITTLE] = 1f
        }
        ReactionId.PLASMA_BURST -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (target.alive && distanceSquared(x, y, target.x, target.y) <= square(definition.radius + target.radius)) {
                    dealStatusDamage(target, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
                    if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.THERMAL, definition.residueStacks, residueGeneration)
                }
            }
        }
        ReactionId.FUSION -> addField(
            CombatFieldKind.FUSION, FieldSource.REACTION, reaction.ordinal, x, y, radius = definition.radius, seconds = 1.6f,
            damagePerSecond = magnitude, element = StatusElement.THERMAL, stacksPerTick = 1,
            generation = residueGeneration, pull = 220f,
        )
        ReactionId.WILDFIRE -> {
            dealStatusDamage(enemy, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
            var spread = 0
            val spreadStacks = a / 2 + 1
            for (index in enemies.indices) {
                if (spread >= 4) break
                val target = enemies[index]
                if (target.id == enemy.id || !target.alive) continue
                if (distanceSquared(x, y, target.x, target.y) <= square(definition.radius + target.radius)) {
                    spread++
                    addRelicArc(x, y, target.x, target.y, link)
                    if (residueGeneration < 2) applyStatus(target, StatusElement.THERMAL, spreadStacks, residueGeneration)
                }
            }
        }
        ReactionId.SUPERCONDUCT -> {
            dealStatusDamage(enemy, magnitude, StatusElement.CRYO, ignoresAffinity = true)
            chain(x, y, enemy.id, 3, definition.radius, link) { target ->
                dealStatusDamage(target, magnitude, StatusElement.ION, ignoresAffinity = true)
                if (residueGeneration < 2) applyStatus(target, StatusElement.CRYO, definition.residueStacks, residueGeneration)
            }
        }
        ReactionId.GLACIER_CRUSH -> {
            repeat(5) { shard ->
                val angle = shard * TAU / 5f + enemy.id * 0.37f
                val endX = x + cos(angle) * 220f
                val endY = y + sin(angle) * 220f
                addRelicArc(x, y, endX, endY, link)
                for (index in enemies.indices) {
                    val target = enemies[index]
                    if (target.id == enemy.id || !target.alive) continue
                    if (segmentCircleIntersects(x, y, endX, endY, target.x, target.y, target.radius + 6f)) {
                        dealStatusDamage(target, magnitude, StatusElement.CRYO, ignoresAffinity = true)
                        if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.CRYO, definition.residueStacks, residueGeneration)
                    }
                }
            }
            dealStatusDamage(enemy, magnitude, StatusElement.CRYO, ignoresAffinity = true)
        }
        ReactionId.EMBRITTLE -> {
            enemy.status[StatusCell.EMBRITTLED_UNTIL] = elapsed + 4f
        }
        ReactionId.MAGNETIZE -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (target.id == enemy.id || !target.alive) continue
                val dx = x - target.x
                val dy = y - target.y
                val distance = max(1f, length(dx, dy))
                if (distance > definition.radius + target.radius) continue
                val knockback = knockbackTaken(target)
                target.vx += dx / distance * 640f * knockback
                target.vy += dy / distance * 640f * knockback
                markThrown(target)
                dealStatusDamage(target, magnitude, StatusElement.GRAVITIC, ignoresAffinity = true)
                if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.GRAVITIC, definition.residueStacks, residueGeneration)
            }
            dealStatusDamage(enemy, magnitude, StatusElement.ION, ignoresAffinity = true)
        }
        ReactionId.FEEDBACK -> {
            if (enemy.status[StatusCell.MAX_HP_PROC_READY_AT] <= elapsed) {
                enemy.status[StatusCell.MAX_HP_PROC_READY_AT] = elapsed + maxHpProcCooldown(enemy)
                dealStatusDamage(enemy, enemy.maxHp * victimFactor(enemy) * definition.power, StatusElement.ION, ignoresAffinity = true)
            }
            val arc = 5f * (a + b) * potency * build.reactionPower * runScale()
            chain(x, y, enemy.id, 2, definition.radius, link) { target ->
                dealStatusDamage(target, arc, StatusElement.ION, ignoresAffinity = true)
                if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.ENTROPY, definition.residueStacks, residueGeneration)
            }
        }
        ReactionId.ROT_WELL -> addField(
            CombatFieldKind.ROT_WELL, FieldSource.REACTION, reaction.ordinal, x, y, radius = definition.radius, seconds = 2f,
            damagePerSecond = magnitude, element = StatusElement.ENTROPY, stacksPerTick = 1,
            generation = residueGeneration, pull = 180f,
        )
        ReactionId.THUNDERCLAP -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (!target.alive || distanceSquared(x, y, target.x, target.y) > square(definition.radius + target.radius)) continue
                dealStatusDamage(target, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
                hurl(x, y, target, THUNDERCLAP_THROW_SPEED)
            }
            shockwave(x, y, 0.32f, definition.radius, FxColorCode.elementTone(StatusElement.SONIC))
        }
        ReactionId.SHATTERPOINT -> {
            repeat(SHATTERPOINT_SHARDS) { shard ->
                val angle = shard * TAU / SHATTERPOINT_SHARDS + enemy.id * 0.53f + 0.3f
                val endX = x + cos(angle) * SHATTERPOINT_REACH
                val endY = y + sin(angle) * SHATTERPOINT_REACH
                addRelicArc(x, y, endX, endY, link)
                for (index in enemies.indices) {
                    val target = enemies[index]
                    if (target.id == enemy.id || !target.alive) continue
                    if (segmentCircleIntersects(x, y, endX, endY, target.x, target.y, target.radius + 6f)) {
                        dealStatusDamage(target, magnitude, StatusElement.CRYO, ignoresAffinity = true)
                        if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.CRYO, definition.residueStacks, residueGeneration)
                    }
                }
            }
            dealStatusDamage(enemy, magnitude, StatusElement.SONIC, ignoresAffinity = true)
        }
        ReactionId.RESONANT_ARC -> {
            val resonance = if (older == StatusElement.SONIC) a else b
            val range = definition.radius + RESONANT_ARC_RANGE_PER_STACK * resonance
            dealStatusDamage(enemy, magnitude, StatusElement.ION, ignoresAffinity = true)
            chain(x, y, enemy.id, RESONANT_ARC_LINKS, range, link) { target ->
                dealStatusDamage(target, magnitude, StatusElement.ION, ignoresAffinity = true)
            }
        }
        ReactionId.SEISMIC -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (!target.alive || isShifting(target) || distanceSquared(x, y, target.x, target.y) > square(definition.radius + target.radius)) continue
                dealStatusDamage(target, magnitude, StatusElement.GRAVITIC, ignoresAffinity = true)
                if (target.alive && isOrdinary(target)) target.disabledUntil = max(target.disabledUntil, elapsed + SEISMIC_STUN_SECONDS)
            }
            shockwave(x, y, 0.4f, definition.radius, FxColorCode.quake(StatusElement.GRAVITIC))
        }
        ReactionId.FRACTURE -> for (index in enemies.indices) {
            val target = enemies[index]
            if (!target.alive || distanceSquared(x, y, target.x, target.y) > square(definition.radius + target.radius)) continue
            dealStatusDamage(target, magnitude, StatusElement.ENTROPY, ignoresAffinity = true)
            if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.ENTROPY, definition.residueStacks, residueGeneration)
        }
        ReactionId.SILENCE -> for (index in enemies.indices) {
            val target = enemies[index]
            if (!target.alive || isShifting(target) || distanceSquared(x, y, target.x, target.y) > square(definition.radius + target.radius)) continue
            dealStatusDamage(target, magnitude, StatusElement.VOID, ignoresAffinity = true)
            if (target.alive) silence(target)
        }
        ReactionId.BLACKFIRE -> {
            enemy.status[StatusCell.BLACKFIRE_UNTIL] = elapsed + BLACKFIRE_SECONDS
            enemy.status[StatusCell.BLACKFIRE_DPS] = max(enemy.status[StatusCell.BLACKFIRE_DPS], magnitude)
        }
        ReactionId.STASIS -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (!target.alive || isShifting(target) || distanceSquared(x, y, target.x, target.y) > square(definition.radius + target.radius)) continue
                dealStatusDamage(target, magnitude, StatusElement.CRYO, ignoresAffinity = true)
                if (target.alive && isOrdinary(target)) {
                    target.disabledUntil = max(target.disabledUntil, elapsed + STASIS_STOP_SECONDS)
                    target.vx = 0f
                    target.vy = 0f
                }
            }
            // Stasis can fire while projectile hits are being walked by index, so it only spends the shots;
            // the list keeps its shape until the collision pass or the next projectile update drops them.
            val reach = square(definition.radius)
            for (index in projectiles.indices) {
                val shot = projectiles[index]
                if (distanceSquared(x, y, shot.x, shot.y) <= reach) eraseHostileShot(shot, EraseCause.REACTION, shot.x, shot.y)
            }
            shockwave(x, y, 0.45f, definition.radius, FxColorCode.elementTone(StatusElement.CRYO))
        }
        ReactionId.ANNIHILATE -> {
            dealStatusDamage(enemy, magnitude, StatusElement.VOID, ignoresAffinity = true)
            toughestOtherEnemy(enemy, definition.radius)?.let { target ->
                addRelicArc(x, y, target.x, target.y, link)
                dealStatusDamage(target, magnitude, StatusElement.VOID, ignoresAffinity = true)
            }
        }
        ReactionId.NULL_WELL -> addField(
            CombatFieldKind.NULL_WELL, FieldSource.REACTION, reaction.ordinal, x, y, radius = definition.radius,
            seconds = NULL_WELL_SECONDS, damagePerSecond = definition.power * potency * build.reactionPower,
            element = StatusElement.VOID, stacksPerTick = 0, generation = residueGeneration, pull = NULL_WELL_PULL,
        )
        ReactionId.OBLIVION -> {
            val line = when {
                enemy.isBossClass -> 0f
                enemy.isElite -> OBLIVION_ELITE_LINE
                else -> OBLIVION_LINE
            }
            if (enemy.hp < enemy.maxHp * line) {
                // An execute: an Aegis tether cannot soften it (bosses never reach here, their line is 0).
                executeEnemy(enemy, HitSource.STATUS, tone = StatusElement.VOID.ordinal + 1)
            } else {
                dealStatusDamage(enemy, magnitude, StatusElement.ENTROPY, ignoresAffinity = true)
            }
        }
    }
    if (!echo && build.reactionResidue > 0 && enemy.alive && residueGeneration < 2) {
        applyStatus(enemy, incoming, build.reactionResidue, residueGeneration)
    }
    if (build.reactionRecoil > 0f && distanceSquared(x, y, coreX, coreY) <= square(build.reactionRecoilRadius)) {
        takeDamage(build.reactionRecoil * selfDamageScale(), ignoreReduction = false, kind = IncomingDamage.SELF)
    }
    val bit = 1 shl reaction.ordinal
    val first = !echo && discoveredReactionMask and bit == 0
    if (first) {
        discoveredReactionMask = discoveredReactionMask or bit
        pendingDiscoveredReactionMask = pendingDiscoveredReactionMask or bit
        emitVisualFx(VisualFxCue.BuildChanged("REACTION DISCOVERED", kinetickk.foundation.collections.immutableListOf("+ " + definition.name)))
    }
    val flags = (if (generation >= 1) ReactionCueFlags.CASCADE else 0) or (if (echo) ReactionCueFlags.ECHO else 0)
    recordStatusCue(VisualFxCue.ReactionTriggered(x, y, reaction.ordinal, max(definition.radius, enemy.radius * 2f), first,
        flags = flags))
    if (!echo) onReaction(enemy, reaction)
}

/**
 * Indices of the Core-state durations in their reaction's content values: Wildfire's Ablaze, Superconduct's
 * Frictionless and Resonant Arc's Arc Weave seconds.
 */
internal const val ABLAZE_SECONDS_VALUE: Int = 1
internal const val FRICTIONLESS_SECONDS_VALUE: Int = 1
internal const val WEAVE_SECONDS_VALUE: Int = 0

/** Forced and echoed reactions scale their magnitude at most this much. */
internal const val MAX_FORCED_REACTION_SCALE: Float = 1.3f
/** An echo replays a reaction as a cascade at nominal two plus two stacks. */
internal const val ECHO_GENERATION: Int = 1
internal const val ECHO_STACKS: Int = 2

internal const val THUNDERCLAP_THROW_SPEED: Float = 760f
internal const val SHATTERPOINT_SHARDS: Int = 5
internal const val SHATTERPOINT_REACH: Float = 240f
internal const val RESONANT_ARC_LINKS: Int = 4
internal const val RESONANT_ARC_RANGE_PER_STACK: Float = 25f
internal const val SEISMIC_STUN_SECONDS: Float = 0.6f
internal const val SILENCE_SECONDS: Float = 2f
/** Bosses fall silent briefly, so chained Silences cannot keep the Architect from ever attacking. */
internal const val SILENCE_BOSS_SECONDS: Float = 0.75f
/** After a Silence ends, a boss ignores further Silences for this long, whichever enemy sets them off. */
internal const val SILENCE_BOSS_GRACE_SECONDS: Float = 2f
internal const val BLACKFIRE_SECONDS: Float = 3f
internal const val STASIS_STOP_SECONDS: Float = 1f
internal const val NULL_WELL_SECONDS: Float = 1.6f
internal const val NULL_WELL_PULL: Float = 240f
internal const val OBLIVION_LINE: Float = 0.12f
internal const val OBLIVION_ELITE_LINE: Float = 0.06f

/**
 * Silence stops [target]'s attacks. A boss falls silent for [SILENCE_BOSS_SECONDS] and is then out of reach
 * for [SILENCE_BOSS_GRACE_SECONDS]: per-enemy reaction cooldowns cannot stop several reactors from chaining
 * Silences, so the lockout belongs to the boss.
 */
internal fun MutableGameState.silence(target: Enemy, seconds: Float = SILENCE_SECONDS) {
    if (!target.isBossClass) {
        target.silencedUntil = max(target.silencedUntil, elapsed + seconds)
        return
    }
    if (target.silencedUntil > 0f && elapsed < target.silencedUntil + SILENCE_BOSS_GRACE_SECONDS) return
    target.silencedUntil = elapsed + SILENCE_BOSS_SECONDS
}

/** Ordinary enemies take stuns and stops; Elites and bosses shrug them off. */
private fun isOrdinary(enemy: Enemy): Boolean = !enemy.isEliteClass

/** Pushes [target] away from ([x], [y]) at [speed], scaled by the knockback it takes. */
internal fun MutableGameState.hurl(x: Float, y: Float, target: Enemy, speed: Float) {
    val knockback = knockbackTaken(target)
    if (knockback <= 0f) return
    var dx = target.x - x
    var dy = target.y - y
    if (length(dx, dy) < 1f) {
        dx = target.x - coreX
        dy = target.y - coreY
    }
    val distance = max(1f, length(dx, dy))
    target.vx += dx / distance * speed * knockback
    target.vy += dy / distance * speed * knockback
    markThrown(target)
}

/** The other living enemy with the most integrity within [range] of [origin] (ties by id), or null. */
internal fun MutableGameState.toughestOtherEnemy(origin: Enemy, range: Float): Enemy? {
    var best: Enemy? = null
    val rangeSquared = range * range
    for (index in enemies.indices) {
        val candidate = enemies[index]
        if (candidate.id == origin.id || !candidate.alive || isShifting(candidate)) continue
        if (distanceSquared(origin.x, origin.y, candidate.x, candidate.y) > rangeSquared) continue
        if (best == null || candidate.hp > best.hp) best = candidate
    }
    return best
}

/** A new run starts with no lingering Core state, anchor, latch, prime or buffered skill input. */
internal fun MutableGameState.resetCoreReactionState() {
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
    phaseUntil = 0f
    shotErasedDispatches = 0
    markDispatches = 0
    pendingSkillDrafts = 0
    skillBufferMask = 0
    skillBufferTime = 0f
    apexBufferTime = 0f
}
