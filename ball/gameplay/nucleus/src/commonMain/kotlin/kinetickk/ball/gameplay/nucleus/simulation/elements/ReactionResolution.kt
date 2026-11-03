// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
                // Rocket Clap: a clap close by launches the Core away from it, never back against its travel: a push
                // that would brake the Core is mirrored along its travel, so a clap ahead carries it on.
                if (clapDistanceSquared <= square(ROCKET_CLAP_REACH)) {
                    val angle = awayFromCoreAngle(x, y) + PI.toFloat()
                    var launchX = cos(angle)
                    var launchY = sin(angle)
                    val travel = speed
                    if (travel > 1f) {
                        val alongX = velocityX / travel
                        val alongY = velocityY / travel
                        val against = launchX * alongX + launchY * alongY
                        if (against < 0f) {
                            launchX -= 2f * against * alongX
                            launchY -= 2f * against * alongY
                        }
                    }
                    if (applyCoreImpulse(launchX, launchY, ROCKET_CLAP_SPEED, gated = true)) {
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
                if (target.alive && target.isOrdinary) {
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
    val announced = first && announcesDiscoveries
    if (first) {
        discoveredReactionMask = discoveredReactionMask or bit
        pendingDiscoveredReactionMask = pendingDiscoveredReactionMask or bit
    }
    if (announced) {
        emitVisualFx(VisualFxCue.BuildChanged("REACTION DISCOVERED", kinetickk.foundation.collections.immutableListOf("+ " + definition.name)))
    }
    if (generation >= 1) flags = flags or ReactionCueFlags.CASCADE
    if (echo) flags = flags or ReactionCueFlags.ECHO
    recordStatusCue(VisualFxCue.ReactionTriggered(x, y, reaction.ordinal, max(radius, enemy.radius * 2f), announced,
        direction = direction, flags = flags))
    if (!echo) {
        onReaction(enemy, reaction)
        // Catalysis: the reaction recharges the skills of its elements.
        catalyze(older, incoming)
    }
}
