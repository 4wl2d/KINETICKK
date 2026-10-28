// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ReactionAspect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kotlin.math.PI
import kotlin.math.atan2
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
 * skips the build residue, discovery and REACTION clauses. A reaction with content values also plays its
 * verb (the play-style behaviour); a keystone in the build switches the verb to the keystone form.
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
    // Verb values in the order BuildCatalog documents; without them the reaction keeps its base effect.
    val v = definition.values
    val verbs = v.isNotEmpty()
    val keystone = verbs && build.keystone(reaction)
    val near = nearCore(x, y)
    // Reaction Tuning: reach, how long its states last and how hard it moves things (fields tune in addField).
    val radius = tunedRadius(reaction, definition.radius)
    var flags = if (keystone) ReactionCueFlags.KEYSTONE else 0
    var direction = Float.NaN
    when (reaction) {
        ReactionId.THERMAL_SHOCK -> {
            dealStatusDamage(enemy, if (keystone) magnitude * FLASH_FREEZE_DAMAGE else magnitude, StatusElement.THERMAL,
                ignoresAffinity = true)
            enemy.status[StatusCell.BRITTLE] = 1f
            // Spall: the next direct hit throws the target (Flash Freeze: freezes it instead).
            if (verbs && enemy.alive) setMark(enemy, MarkKind.SPALL, tunedSeconds(reaction, v[0]))
        }
        ReactionId.PLASMA_BURST -> {
            plasmaBurst(x, y, radius, magnitude, definition.residueStacks, residueGeneration)
            if (keystone) {
                arcPlasma(enemy, x, y, radius, magnitude, residueGeneration)
            } else if (verbs) {
                addField(CombatFieldKind.PLASMA, FieldSource.REACTION, reaction.ordinal, x, y, radius = v[0], seconds = v[1],
                    damagePerSecond = 0f, generation = residueGeneration)
            }
        }
        ReactionId.FUSION -> addField(
            CombatFieldKind.FUSION, FieldSource.REACTION, reaction.ordinal, x, y, radius = definition.radius,
            seconds = if (keystone) DWARF_STAR_SECONDS else FUSION_SECONDS,
            damagePerSecond = if (keystone) magnitude * DWARF_STAR_DAMAGE else magnitude,
            element = StatusElement.THERMAL, stacksPerTick = 1, generation = residueGeneration,
            pull = if (keystone) DWARF_STAR_PULL else FUSION_PULL, flags = if (keystone) FieldFlags.KEYSTONE else 0,
        )
        ReactionId.WILDFIRE -> {
            dealStatusDamage(enemy, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
            if (!keystone) {
                var spread = 0
                val spreadLimit = if (verbs) v[0].toInt() else WILDFIRE_LEGACY_SPREAD
                for (index in enemies.indices) {
                    if (spread >= spreadLimit) break
                    val target = enemies[index]
                    if (target.id == enemy.id || !target.alive) continue
                    if (distanceSquared(x, y, target.x, target.y) <= square(radius + target.radius)) {
                        spread++
                        addRelicArc(x, y, target.x, target.y, link)
                        if (residueGeneration < 2) applyStatus(target, StatusElement.THERMAL, definition.residueStacks, residueGeneration)
                    }
                }
            }
            // Ablaze: near the Core (Firewalker: anywhere) every ram sets enemies burning.
            if (verbs && (near || keystone)) {
                ablazeUntil = max(ablazeUntil, elapsed + tunedSeconds(reaction, ablazeSeconds()))
                flags = flags or ReactionCueFlags.VERB
            }
        }
        ReactionId.SUPERCONDUCT -> {
            dealStatusDamage(enemy, magnitude, StatusElement.CRYO, ignoresAffinity = true)
            chain(x, y, enemy.id, if (keystone) ZERO_RESISTANCE_LINKS else SUPERCONDUCT_LINKS, radius, link) { target ->
                var damage = magnitude
                if (keystone && isFrozen(target)) {
                    damage *= ZERO_RESISTANCE_FROZEN
                    target.status[StatusCell.BRITTLE] = 0f
                }
                dealStatusDamage(target, damage, StatusElement.ION, ignoresAffinity = true)
                if (residueGeneration < 2) applyStatus(target, StatusElement.CRYO, definition.residueStacks, residueGeneration)
            }
            // Frictionless: near the Core the drag falls away and the Core vents heat once.
            if (verbs && !keystone && near) {
                frictionlessUntil = max(frictionlessUntil, elapsed + tunedSeconds(reaction, v[1]))
                heat = max(0f, heat - v[2])
                flags = flags or ReactionCueFlags.VERB
            }
        }
        ReactionId.GLACIER_CRUSH -> {
            // Ice Fan: the fan points away from the Core (Aimed Avalanche: along the aim); without a verb it
            // keeps its old spread around the target.
            val base = when {
                keystone -> atan2(lastAimDirectionY, lastAimDirectionX)
                verbs -> awayFromCoreAngle(x, y)
                else -> 0f
            }
            if (verbs) direction = base
            val spacing = if (verbs) v[0] * RADIANS_PER_DEGREE else 0f
            val reach = tunedRadius(reaction, GLACIER_REACH)
            val fanPush = if (verbs) tunedForce(reaction, v[1]) else 0f
            repeat(GLACIER_SHARDS) { shard ->
                val angle = if (verbs) base + (shard - GLACIER_SHARDS / 2) * spacing else shard * TAU / GLACIER_SHARDS + enemy.id * 0.37f
                val dirX = cos(angle)
                val dirY = sin(angle)
                val endX = x + dirX * reach
                val endY = y + dirY * reach
                addRelicArc(x, y, endX, endY, link)
                for (index in enemies.indices) {
                    val target = enemies[index]
                    if (target.id == enemy.id || !target.alive) continue
                    if (segmentCircleIntersects(x, y, endX, endY, target.x, target.y, target.radius + 6f)) {
                        dealStatusDamage(target, magnitude, StatusElement.CRYO, ignoresAffinity = true)
                        if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.CRYO, definition.residueStacks, residueGeneration)
                        if (verbs && target.alive) push(target, dirX, dirY, fanPush)
                        if (keystone && target.alive && stacks(target, StatusElement.CRYO) >= AVALANCHE_FREEZE_CHILL) {
                            forceElementBreak(target, StatusElement.CRYO)
                        }
                    }
                }
            }
            dealStatusDamage(enemy, magnitude, StatusElement.CRYO, ignoresAffinity = true)
        }
        ReactionId.EMBRITTLE -> {
            enemy.status[StatusCell.EMBRITTLED_UNTIL] = elapsed +
                tunedSeconds(reaction, if (keystone) GLASS_BURST_EMBRITTLE_SECONDS else EMBRITTLE_SECONDS)
        }
        ReactionId.MAGNETIZE -> {
            if (keystone) {
                // Repulsor: no yank; the target flies away from the Core and charges whatever it slams.
                hurl(coreX, coreY, enemy, tunedForce(reaction, REPULSOR_THROW_SPEED))
            } else {
                for (index in enemies.indices) {
                    val target = enemies[index]
                    if (target.id == enemy.id || !target.alive) continue
                    val dx = x - target.x
                    val dy = y - target.y
                    val distance = max(1f, length(dx, dy))
                    if (distance > radius + target.radius) continue
                    val knockback = knockbackTaken(target)
                    val yank = tunedForce(reaction, MAGNETIZE_YANK)
                    if (!knockbackAsDamage(target, knockback, yank)) {
                        target.vx += dx / distance * yank * knockback
                        target.vy += dy / distance * yank * knockback
                        markThrown(target)
                    }
                    dealStatusDamage(target, magnitude, StatusElement.GRAVITIC, ignoresAffinity = true)
                    if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.GRAVITIC, definition.residueStacks, residueGeneration)
                }
            }
            dealStatusDamage(enemy, magnitude, StatusElement.ION, ignoresAffinity = true)
            // Attractor: the target keeps pulling enemies and bending your shots for a while.
            if (verbs && !keystone && enemy.alive) {
                addField(CombatFieldKind.ATTRACTOR, FieldSource.REACTION, reaction.ordinal, x, y, radius = definition.radius,
                    seconds = v[0], damagePerSecond = 0f, generation = residueGeneration, pull = v[1], anchorId = enemy.id,
                    flags = FieldFlags.FOLLOWS_ANCHOR)
            }
        }
        ReactionId.FEEDBACK -> {
            if (enemy.status[StatusCell.MAX_HP_PROC_READY_AT] <= elapsed) {
                enemy.status[StatusCell.MAX_HP_PROC_READY_AT] = elapsed + maxHpProcCooldown(enemy)
                dealStatusDamage(enemy, enemy.maxHp * victimFactor(enemy) * definition.power, StatusElement.ION, ignoresAffinity = true)
            }
            val arc = 5f * (a + b) * potency * build.reactionPower * runScale()
            chain(x, y, enemy.id, 2, radius, link) { target ->
                dealStatusDamage(target, arc, StatusElement.ION, ignoresAffinity = true)
                if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.ENTROPY, definition.residueStacks, residueGeneration)
            }
            // Overcurrent: the weapon jumps to its next shot (Short Circuit: the skills recharge instead).
            if (verbs && overcurrentReadyAt <= elapsed) {
                overcurrentReadyAt = elapsed + v[1]
                if (keystone) refundAllSkills(SHORT_CIRCUIT_SECONDS) else refreshWeapon(v[0], BuffSource.reaction(reaction))
                flags = flags or ReactionCueFlags.VERB
            }
        }
        ReactionId.ROT_WELL -> addField(
            CombatFieldKind.ROT_WELL, FieldSource.REACTION, reaction.ordinal, x, y, radius = definition.radius,
            seconds = if (keystone) PLAGUE_PIT_SECONDS else ROT_WELL_SECONDS,
            damagePerSecond = magnitude, element = StatusElement.ENTROPY, stacksPerTick = 1,
            generation = residueGeneration, pull = ROT_WELL_PULL, flags = if (keystone) FieldFlags.KEYSTONE else 0,
        )
        ReactionId.THUNDERCLAP -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (!target.alive || distanceSquared(x, y, target.x, target.y) > square(radius + target.radius)) continue
                dealStatusDamage(target, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
                hurl(x, y, target, tunedForce(reaction, THUNDERCLAP_THROW_SPEED))
            }
            shockwave(x, y, 0.32f, radius, FxColorCode.elementTone(StatusElement.SONIC))
            val clapDistanceSquared = distanceSquared(x, y, coreX, coreY)
            if (keystone) {
                // Rocket Clap: a clap close by launches the Core away from it.
                if (clapDistanceSquared <= square(ROCKET_CLAP_REACH)) {
                    val angle = awayFromCoreAngle(x, y) + PI.toFloat()
                    if (applyCoreImpulse(cos(angle), sin(angle), ROCKET_CLAP_SPEED, gated = true)) {
                        flags = flags or ReactionCueFlags.VERB
                    }
                }
            } else if (verbs && clapDistanceSquared <= square(v[0])) {
                // Blast Jump: a Dash started within the window rides the blast.
                blastJumpUntil = elapsed + tunedSeconds(reaction, v[1])
                flags = flags or ReactionCueFlags.VERB
            }
        }
        ReactionId.SHATTERPOINT -> {
            if (verbs && !keystone && near) {
                // Shard Guard: the shards circle the Core instead of flying out.
                guardShards = if (guardUntil > elapsed) min(v[2].toInt(), guardShards + SHATTERPOINT_SHARDS) else min(v[2].toInt(), SHATTERPOINT_SHARDS)
                guardUntil = elapsed + tunedSeconds(reaction, v[0])
                guardDamage = magnitude
                flags = flags or ReactionCueFlags.VERB
            } else {
                val shards = if (keystone) CRYSTAL_CHOIR_SHARDS else SHATTERPOINT_SHARDS
                val offset = enemy.id * 0.53f + 0.3f
                if (verbs) direction = offset
                repeat(shards) { shard ->
                    val angle = shard * TAU / shards + offset
                    val endX = x + cos(angle) * tunedRadius(reaction, SHATTERPOINT_REACH)
                    val endY = y + sin(angle) * tunedRadius(reaction, SHATTERPOINT_REACH)
                    addRelicArc(x, y, endX, endY, link)
                    for (index in enemies.indices) {
                        val target = enemies[index]
                        if (target.id == enemy.id || !target.alive) continue
                        if (segmentCircleIntersects(x, y, endX, endY, target.x, target.y, target.radius + 6f)) {
                            dealStatusDamage(target, magnitude, StatusElement.CRYO, ignoresAffinity = true)
                            if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.CRYO, definition.residueStacks, residueGeneration)
                            if (keystone && target.alive && stacks(target, StatusElement.SONIC) >= CRYSTAL_CHOIR_RESONANCE) {
                                forceElementBreak(target, StatusElement.SONIC)
                            }
                        }
                    }
                }
            }
            dealStatusDamage(enemy, magnitude, StatusElement.SONIC, ignoresAffinity = true)
        }
        ReactionId.RESONANT_ARC -> {
            val resonance = if (older == StatusElement.SONIC) a else b
            val range = radius + RESONANT_ARC_RANGE_PER_STACK * resonance
            dealStatusDamage(enemy, magnitude, StatusElement.ION, ignoresAffinity = true)
            chain(x, y, enemy.id, RESONANT_ARC_LINKS, range, link) { target ->
                dealStatusDamage(target, magnitude, StatusElement.ION, ignoresAffinity = true)
            }
            // Arc Weave: near the Core (Standing Wave: anywhere) sharp turns throw more arcs.
            if (verbs && (near || keystone)) {
                weaveUntil = max(weaveUntil, elapsed + tunedSeconds(reaction, weaveSeconds()))
                weaveDamage = magnitude * if (keystone) STANDING_WAVE_SHARE else v[2]
                weaveRange = range
                flags = flags or ReactionCueFlags.VERB
            }
        }
        ReactionId.SEISMIC -> {
            quake(x, y, radius, magnitude)
            shockwave(x, y, 0.4f, radius, FxColorCode.quake(StatusElement.GRAVITIC))
            if (keystone) {
                queueAftershock(x, y, magnitude * AFTERSHOCK_SHARE)
            } else if (verbs && rockEcho(x, y, v[0].toInt(), tunedRadius(reaction, v[1]), tunedRadius(reaction, v[2]), magnitude * v[3]) > 0) {
                flags = flags or ReactionCueFlags.VERB
            }
        }
        ReactionId.FRACTURE -> for (index in enemies.indices) {
            val target = enemies[index]
            if (!target.alive || distanceSquared(x, y, target.x, target.y) > square(radius + target.radius)) continue
            dealStatusDamage(target, magnitude, StatusElement.ENTROPY, ignoresAffinity = true)
            if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.ENTROPY, definition.residueStacks, residueGeneration)
            if (!verbs || !target.alive) continue
            if (keystone) suppress(target) else setMark(target, MarkKind.CRACKED, tunedSeconds(reaction, v[0]))
        }
        ReactionId.SILENCE -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (!target.alive || isShifting(target) || distanceSquared(x, y, target.x, target.y) > square(radius + target.radius)) continue
                // Dead Air forms without damage.
                if (!keystone) dealStatusDamage(target, magnitude, StatusElement.VOID, ignoresAffinity = true)
                if (target.alive) silence(target)
            }
            if (keystone) {
                addField(CombatFieldKind.HUSH, FieldSource.REACTION, reaction.ordinal, coreX, coreY, radius = DEAD_AIR_RADIUS,
                    seconds = DEAD_AIR_SECONDS, damagePerSecond = 0f, generation = residueGeneration,
                    flags = FieldFlags.FOLLOWS_CORE or FieldFlags.KEYSTONE)
            } else if (verbs) {
                addField(CombatFieldKind.HUSH, FieldSource.REACTION, reaction.ordinal, x, y, radius = v[0], seconds = v[1],
                    damagePerSecond = 0f, generation = residueGeneration)
            }
        }
        ReactionId.BLACKFIRE -> {
            enemy.status[StatusCell.BLACKFIRE_UNTIL] = elapsed + tunedSeconds(reaction, BLACKFIRE_SECONDS)
            enemy.status[StatusCell.BLACKFIRE_DPS] = max(enemy.status[StatusCell.BLACKFIRE_DPS],
                if (keystone) magnitude * HELLFIRE_DAMAGE else magnitude)
        }
        ReactionId.STASIS -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (!target.alive || isShifting(target) || distanceSquared(x, y, target.x, target.y) > square(radius + target.radius)) continue
                dealStatusDamage(target, magnitude, StatusElement.CRYO, ignoresAffinity = true)
                if (target.alive && isOrdinary(target)) {
                    target.disabledUntil = max(target.disabledUntil, elapsed + STASIS_STOP_SECONDS)
                    target.vx = 0f
                    target.vy = 0f
                }
            }
            // Time Bubble: the stop lingers as a field (Rewind: one that returns the shots it catches).
            val bubble = if (verbs) {
                addField(CombatFieldKind.TIME_BUBBLE, FieldSource.REACTION, reaction.ordinal, x, y, radius = v[0], seconds = v[1],
                    damagePerSecond = 0f, generation = residueGeneration, flags = if (keystone) FieldFlags.KEYSTONE else 0)
            } else -1
            // Stasis can fire while projectile hits are being walked by index, so it only spends the shots;
            // the list keeps its shape until the collision pass or the next projectile update drops them.
            val reach = square(radius)
            for (index in projectiles.indices) {
                val shot = projectiles[index]
                if (distanceSquared(x, y, shot.x, shot.y) > reach) continue
                if (keystone && bubble >= 0) rewindShot(bubble, shot) else eraseHostileShot(shot, EraseCause.REACTION, shot.x, shot.y)
            }
            shockwave(x, y, 0.45f, radius, FxColorCode.elementTone(StatusElement.CRYO))
        }
        ReactionId.ANNIHILATE -> {
            dealStatusDamage(enemy, magnitude, StatusElement.VOID, ignoresAffinity = true)
            toughestOtherEnemy(enemy, radius)?.let { target ->
                addRelicArc(x, y, target.x, target.y, link)
                dealStatusDamage(target, magnitude, StatusElement.VOID, ignoresAffinity = true)
                // Void Anchor: the next Dash toward the toughest one blinks the Core to it.
                if (verbs && target.alive) {
                    voidAnchorId = target.id
                    voidAnchorUntil = elapsed + tunedSeconds(reaction, if (keystone) EVENT_STRIKE_ANCHOR_SECONDS else v[0])
                    flags = flags or ReactionCueFlags.VERB
                }
            }
        }
        ReactionId.NULL_WELL -> {
            val weight = definition.power * potency * build.reactionPower
            if (keystone) {
                addField(CombatFieldKind.NULL_WELL, FieldSource.REACTION, reaction.ordinal, coreX, coreY, radius = HUNGRY_VOID_RADIUS,
                    seconds = HUNGRY_VOID_SECONDS, damagePerSecond = weight, element = StatusElement.VOID, stacksPerTick = 0,
                    generation = residueGeneration, flags = FieldFlags.FOLLOWS_CORE or FieldFlags.KEYSTONE)
            } else {
                addField(CombatFieldKind.NULL_WELL, FieldSource.REACTION, reaction.ordinal, x, y, radius = definition.radius,
                    seconds = NULL_WELL_SECONDS, damagePerSecond = weight, element = StatusElement.VOID, stacksPerTick = 0,
                    generation = residueGeneration, pull = NULL_WELL_PULL)
            }
        }
        ReactionId.OBLIVION -> {
            val line = when {
                enemy.isBossClass -> 0f
                enemy.isElite -> if (keystone) LAST_RITES_ELITE_LINE else OBLIVION_ELITE_LINE
                else -> if (keystone) LAST_RITES_LINE else OBLIVION_LINE
            }
            if (enemy.hp < enemy.maxHp * line) {
                // Erasure: the victim's Data latches to the Core and the skills recharge (Last Rites: neither).
                if (verbs) {
                    if (!keystone) {
                        erasureLatchId = enemy.id
                        refundAllSkills(v[0])
                    }
                    flags = flags or ReactionCueFlags.VERB
                }
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
    if (generation >= 1) flags = flags or ReactionCueFlags.CASCADE
    if (echo) flags = flags or ReactionCueFlags.ECHO
    recordStatusCue(VisualFxCue.ReactionTriggered(x, y, reaction.ordinal, max(radius, enemy.radius * 2f), first,
        direction = direction, flags = flags))
    if (!echo) {
        onReaction(enemy, reaction)
        // Catalysis: the reaction recharges the skills of its elements.
        catalyze(older, incoming)
    }
}

/** Plasma Burst's blast: [magnitude] Thermal to everything within [radius] of ([x], [y]) plus [stacks] Thermal. */
private fun MutableGameState.plasmaBurst(x: Float, y: Float, radius: Float, magnitude: Float, stacks: Int, generation: Int) {
    for (index in enemies.indices) {
        val target = enemies[index]
        if (target.alive && distanceSquared(x, y, target.x, target.y) <= square(radius + target.radius)) {
            dealStatusDamage(target, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
            if (generation < 2 && target.alive) applyStatus(target, StatusElement.THERMAL, stacks, generation)
        }
    }
}

/** Arc Plasma: the burst repeats once around up to [ARC_PLASMA_REPEATS] Charged enemies near it, at half strength. */
private fun MutableGameState.arcPlasma(origin: Enemy, x: Float, y: Float, radius: Float, magnitude: Float, generation: Int) {
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
private fun MutableGameState.quake(x: Float, y: Float, radius: Float, damage: Float) {
    for (index in enemies.indices) {
        val target = enemies[index]
        if (!target.alive || isShifting(target) || distanceSquared(x, y, target.x, target.y) > square(radius + target.radius)) continue
        dealStatusDamage(target, damage, StatusElement.GRAVITIC, ignoresAffinity = true)
        if (target.alive && isOrdinary(target)) target.disabledUntil = max(target.disabledUntil, elapsed + SEISMIC_STUN_SECONDS)
    }
}

/**
 * Rock Echo: up to [rocks] echoing obstacles within [reach] of ([x], [y]) repeat the quake around their centres
 * ([radius], [damage]). Obstacles are visited in window-cell order and never changed. Returns the echoes.
 */
private fun MutableGameState.rockEcho(x: Float, y: Float, rocks: Int, reach: Float, radius: Float, damage: Float): Int {
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
private fun MutableGameState.queueAftershock(x: Float, y: Float, damage: Float) {
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
private fun MutableGameState.updateAftershocks() {
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
private fun MutableGameState.suppress(target: Enemy) {
    if (target.isBossClass) return
    setMark(target, MarkKind.SUPPRESSED, CREVASSE_SECONDS)
    if (target.armored) target.armorCracked = true
}

/** Pushes [target] along the unit vector ([dirX], [dirY]) at [speed], scaled by the knockback it takes. */
private fun MutableGameState.push(target: Enemy, dirX: Float, dirY: Float, speed: Float) {
    val knockback = knockbackTaken(target)
    if (knockbackAsDamage(target, knockback, speed)) return
    if (knockback <= 0f) return
    target.vx += dirX * speed * knockback
    target.vy += dirY * speed * knockback
    markThrown(target)
}

/** Heading from the Core to ([x], [y]); the last aim when the two (nearly) coincide. */
private fun MutableGameState.awayFromCoreAngle(x: Float, y: Float): Float {
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

/**
 * Indices of the Core-state durations in their reaction's content values: Wildfire's Ablaze, Superconduct's
 * Frictionless and Resonant Arc's Arc Weave seconds.
 */
internal const val ABLAZE_SECONDS_VALUE: Int = 1
internal const val FRICTIONLESS_SECONDS_VALUE: Int = 1
internal const val WEAVE_SECONDS_VALUE: Int = 0

// Indices of the other verb values read outside resolveReaction (BuildCatalog documents every index).
internal const val SPALL_THROW_VALUE: Int = 1
internal const val PLASMA_ACTIVATION_VALUE: Int = 2
internal const val PLASMA_DASH_HEAT_VALUE: Int = 3
internal const val ACCRETION_REACH_VALUE: Int = 0
internal const val ACCRETION_SPEED_VALUE: Int = 1
internal const val ACCRETION_THROW_VALUE: Int = 2
internal const val ABLAZE_STACKS_VALUE: Int = 2
internal const val FRICTIONLESS_DRAG_VALUE: Int = 0
internal const val GLASS_CHARGE_SPEED_VALUE: Int = 0
internal const val ATTRACTOR_PULL_VALUE: Int = 1
internal const val ATTRACTOR_SHOT_REACH_VALUE: Int = 2
internal const val ATTRACTOR_SHOT_TURN_VALUE: Int = 3
internal const val COMPOST_DAMPING_VALUE: Int = 0
internal const val COMPOST_GROWTH_RADIUS_VALUE: Int = 1
internal const val COMPOST_GROWTH_SECONDS_VALUE: Int = 2
internal const val COMPOST_GROWTHS_VALUE: Int = 3
internal const val BLAST_JUMP_IMPULSE_VALUE: Int = 2
internal const val GUARD_ORBIT_VALUE: Int = 1
internal const val WEAVE_LINKS_VALUE: Int = 1
internal const val CRACKED_REACH_VALUE: Int = 1
internal const val CRACKED_POWER_VALUE: Int = 2
internal const val CRACKED_DECAY_VALUE: Int = 3
internal const val HUSH_SILENCE_VALUE: Int = 2
internal const val FLAME_LEAP_REACH_VALUE: Int = 0
internal const val FLAME_LEAP_SHARE_VALUE: Int = 1
internal const val FLAME_LEAP_FLOOR_VALUE: Int = 2
internal const val FLAME_REFUND_VALUE: Int = 3
internal const val BUBBLE_SHOTS_VALUE: Int = 2
internal const val BUBBLE_HASTE_VALUE: Int = 3
internal const val VOID_ANCHOR_CONE_VALUE: Int = 1
internal const val VOID_ANCHOR_REACH_VALUE: Int = 2
internal const val MAW_SHOTS_VALUE: Int = 0
internal const val MAW_POP_VALUE: Int = 1
internal const val MAW_THROW_VALUE: Int = 2

/** Reactions within this distance of the Core feed its verbs (Ablaze, Frictionless, Shard Guard, Arc Weave). */
internal const val CORE_LINK_RADIUS: Float = 480f
internal const val RADIANS_PER_DEGREE: Float = (PI / 180.0).toFloat()

// Base shapes the verbs leave unchanged.
internal const val FUSION_SECONDS: Float = 1.6f
internal const val FUSION_PULL: Float = 220f
/** Wildfire's spread when the reaction has no verb values. */
internal const val WILDFIRE_LEGACY_SPREAD: Int = 4
internal const val SUPERCONDUCT_LINKS: Int = 3
internal const val GLACIER_SHARDS: Int = 5
internal const val GLACIER_REACH: Float = 220f
internal const val EMBRITTLE_SECONDS: Float = 4f
internal const val MAGNETIZE_YANK: Float = 640f
internal const val ROT_WELL_SECONDS: Float = 2f
internal const val ROT_WELL_PULL: Float = 180f
/** Compost never grows a well past this radius. */
internal const val COMPOST_MAX_RADIUS: Float = 200f
/** Accretion latches the pickups this far beyond the star's radius when it implodes. */
internal const val ACCRETION_LATCH_MARGIN: Float = 40f
/** A Time Bubble holds ordinary occupants this long per tick. */
internal const val TIME_BUBBLE_STOP_SECONDS: Float = 0.5f
/** Blast Jump: a Crescent's Blink reaches this much farther. */
internal const val BLAST_JUMP_BLINK_RANGE: Float = 1.4f
internal const val GUARD_SHARD_RADIUS: Float = 6f
/** Shard Guard shards turn this many radians per second around the Core. */
internal const val GUARD_SPIN: Float = 3f
/** Void Anchor: the Core lands this far short of the anchor's rim and never within the singularity gap. */
internal const val VOID_BLINK_CLEARANCE: Float = 8f
internal const val VOID_BLINK_SINGULARITY_GAP: Float = 60f
internal const val MAX_ROCK_ECHOES: Int = 3
internal const val MAX_CRACKED_DETONATIONS: Int = 12
internal const val CRACKED_SHOCKWAVE_SECONDS: Float = 0.25f
internal const val CRACKED_SHOCKWAVE_RADIUS: Float = 60f
internal const val FLAME_LEAP_MIN_SECONDS: Float = 1f
internal const val FLAME_REFUND_GATE_SECONDS: Float = 0.5f
/** At most this many of the newest shot-eating fields test hostile shots each step. */
internal const val MAX_SHOT_EATING_FIELDS: Int = 4
/** At most this many of the newest pickup-pulling fields move pickups each step. */
internal const val MAX_PICKUP_FIELDS: Int = 2

// Keystone forms (one per reaction).
internal const val FLASH_FREEZE_SECONDS: Float = 0.8f
internal const val FLASH_FREEZE_DAMAGE: Float = 0.8f
internal const val ARC_PLASMA_REACH: Float = 220f
internal const val ARC_PLASMA_SHARE: Float = 0.5f
internal const val ARC_PLASMA_STACKS: Int = 1
internal const val DWARF_STAR_SECONDS: Float = 3.2f
internal const val DWARF_STAR_PULL: Float = 440f
internal const val DWARF_STAR_DAMAGE: Float = 0.5f
internal const val FIREWALKER_SECONDS: Float = 6f
internal const val FIREWALKER_IGNITE_MARGIN: Int = 2
internal const val ZERO_RESISTANCE_LINKS: Int = 6
internal const val ZERO_RESISTANCE_FROZEN: Float = 1.5f
internal const val AVALANCHE_FREEZE_CHILL: Int = 4
internal const val GLASS_BURST_EMBRITTLE_SECONDS: Float = 2f
internal const val GLASS_BURST_SHARDS: Int = 3
internal const val GLASS_BURST_SHARE: Float = 0.5f
internal const val GLASS_BURST_REACH: Float = 160f
internal const val GLASS_BURST_SPACING_DEGREES: Float = 25f
internal const val REPULSOR_THROW_SPEED: Float = 900f
internal const val SHORT_CIRCUIT_SECONDS: Float = 1f
internal const val PLAGUE_PIT_SECONDS: Float = 4f
internal const val ROCKET_CLAP_REACH: Float = 220f
internal const val ROCKET_CLAP_SPEED: Float = 560f
internal const val CRYSTAL_CHOIR_SHARDS: Int = 7
internal const val CRYSTAL_CHOIR_RESONANCE: Int = 5
internal const val STANDING_WAVE_SECONDS: Float = 6f
internal const val STANDING_WAVE_LINKS: Int = 2
internal const val STANDING_WAVE_SHARE: Float = 0.35f
internal const val AFTERSHOCK_DELAY_SECONDS: Float = 0.6f
internal const val AFTERSHOCK_SHARE: Float = 0.6f
internal const val CREVASSE_SECONDS: Float = 4f
internal const val DEAD_AIR_RADIUS: Float = 140f
internal const val DEAD_AIR_SECONDS: Float = 3f
internal const val DEAD_AIR_SHOTS: Int = 20
internal const val HELLFIRE_DAMAGE: Float = 1.5f
internal const val REWIND_SHOTS: Int = 12
internal const val REWIND_SHARE: Float = 0.3f
internal const val REWIND_REACH: Float = 600f
internal const val EVENT_STRIKE_ANCHOR_SECONDS: Float = 1.5f
/** Event Strike: the anchor counts as marked for crits this long after the blink. */
internal const val EVENT_STRIKE_CRIT_SECONDS: Float = 0.5f
internal const val HUNGRY_VOID_RADIUS: Float = 120f
internal const val HUNGRY_VOID_SECONDS: Float = 2.4f
internal const val LAST_RITES_LINE: Float = 0.16f
internal const val LAST_RITES_ELITE_LINE: Float = 0.09f

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

/**
 * Knockback to Damage: a [speed] throw, push or pull on [target], which takes at most [IMMOVABLE_KNOCKBACK] of
 * knockback, deals proc damage instead (through the Architect's gate), at most once per enemy per
 * [KNOCKBACK_DAMAGE_GATE_SECONDS] (the slam gate). True when it did; a gated throw moves the enemy as usual.
 */
internal fun MutableGameState.knockbackAsDamage(target: Enemy, knockback: Float, speed: Float): Boolean {
    val amount = build.knockbackToDamage
    if (amount <= 0f || knockback > IMMOVABLE_KNOCKBACK || !(speed > 0f) || !target.alive || isShifting(target)) return false
    if (target.slamReadyAt > elapsed) return false
    target.slamReadyAt = elapsed + KNOCKBACK_DAMAGE_GATE_SECONDS
    dealProcDamage(target, amount * speed * KNOCKBACK_DAMAGE_SCALE * runScale(), rescale = false)
    return true
}

internal const val IMMOVABLE_KNOCKBACK: Float = 0.35f
internal const val KNOCKBACK_DAMAGE_SCALE: Float = 0.15f
internal const val KNOCKBACK_DAMAGE_GATE_SECONDS: Float = 0.5f

/** Ordinary enemies take stuns and stops; Elites and bosses shrug them off. */
private fun isOrdinary(enemy: Enemy): Boolean = !enemy.isEliteClass

/** Pushes [target] away from ([x], [y]) at [speed], scaled by the knockback it takes. */
internal fun MutableGameState.hurl(x: Float, y: Float, target: Enemy, speed: Float) {
    val knockback = knockbackTaken(target)
    if (knockbackAsDamage(target, knockback, speed)) return
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
