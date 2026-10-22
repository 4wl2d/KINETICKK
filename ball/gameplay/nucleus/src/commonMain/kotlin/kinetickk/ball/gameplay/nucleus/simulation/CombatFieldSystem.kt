// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Admits a lingering field and returns its table index, or -1 when it is refused. Admission is
 * deterministic: a skill owns one field per slot ([owner]) and a recast overwrites it; a kind at its
 * [CombatFieldKind.cap] overwrites its oldest field in place; otherwise reactions and items share
 * `maximumFields − maximumSkillFields` records and a field past that pool is refused. Overwriting in
 * place keeps every other index stable, so a field may be admitted while a pass walks the table.
 */
internal fun MutableGameState.addField(
    kind: CombatFieldKind,
    source: FieldSource,
    owner: Int,
    x: Float,
    y: Float,
    radius: Float,
    seconds: Float,
    damagePerSecond: Float,
    element: StatusElement? = null,
    stacksPerTick: Int = 0,
    generation: Int = 1,
    pull: Float = 0f,
    tickClock: Float = 0f,
    x2: Float = x,
    y2: Float = y,
    anchorId: Int = -1,
    flags: Int = 0,
    power: Float = 0f,
): Int {
    val table = combatFieldTable
    val policy = content.build.policy
    val index = if (source == FieldSource.SKILL) {
        val own = table.skillSlotIndex(owner)
        when {
            own >= 0 -> own
            table.countFrom(FieldSource.SKILL) >= policy.maximumSkillFields -> return -1
            else -> table.append()
        }
    } else if (kind.cap > 0 && table.countOf(kind) >= kind.cap) {
        table.oldestOf(kind)
    } else {
        if (table.count - table.countFrom(FieldSource.SKILL) >= policy.maximumFields - policy.maximumSkillFields) return -1
        table.append()
    }
    table[index, FieldCell.KIND] = kind.ordinal.toFloat()
    table[index, FieldCell.SOURCE] = source.ordinal.toFloat()
    table[index, FieldCell.OWNER] = owner.toFloat()
    table[index, FieldCell.X] = x
    table[index, FieldCell.Y] = y
    table[index, FieldCell.X2] = x2
    table[index, FieldCell.Y2] = y2
    table[index, FieldCell.RADIUS] = radius
    table[index, FieldCell.DPS] = damagePerSecond
    table[index, FieldCell.ELEMENT] = (element?.ordinal ?: -1).toFloat()
    table[index, FieldCell.STACKS] = stacksPerTick.toFloat()
    table[index, FieldCell.GENERATION] = generation.toFloat()
    table[index, FieldCell.PULL] = pull
    table[index, FieldCell.CREATED_AT] = elapsed
    table[index, FieldCell.EXPIRES_AT] = elapsed + seconds
    table[index, FieldCell.REMAINING] = seconds
    table[index, FieldCell.TICK_CLOCK] = tickClock
    table[index, FieldCell.CHARGE] = 0f
    table[index, FieldCell.ANCHOR_ID] = anchorId.toFloat()
    table[index, FieldCell.FLAGS] = flags.toFloat()
    table[index, FieldCell.POWER] = power
    table.stamp(index)
    return index
}

/** Grants the field at [index] [seconds] more life (growth); its presented expiry moves with it. */
internal fun MutableGameState.extendField(index: Int, seconds: Float) {
    val table = combatFieldTable
    table[index, FieldCell.REMAINING] = table[index, FieldCell.REMAINING] + seconds
    table[index, FieldCell.EXPIRES_AT] = table[index, FieldCell.EXPIRES_AT] + seconds
    table.touch()
}

/**
 * Advances every field admitted before this pass, in the documented order: anchored positions, then per field
 * the enemy pass (pull, damping) and the ticks that came due, then life; then the shot pass (the newest
 * [MAX_SHOT_EATING_FIELDS] shot-eating fields), the shot bend (attractors), the pickup pass (the newest
 * [MAX_PICKUP_FIELDS] stars), the expiry branches (implosion, pop) and compaction, and last the Core pass. A
 * field admitted or overwritten during the pass (a reaction set off by a tick) starts on the next step, and a
 * field a tick replaced keeps its replacement's life. Allocation-free.
 */
internal fun MutableGameState.updateCombatFields(delta: Float) {
    val table = combatFieldTable
    val snapshot = table.count
    if (snapshot == 0) {
        coreFieldMask = 0
        return
    }
    val passSequence = table.nextSequence
    var expired = false
    var shotEaters = 0
    var attractors = 0
    var stars = 0
    for (index in 0 until snapshot) {
        val sequence = table.sequence(index)
        if (sequence >= passSequence) continue
        val kind = table.kind(index)
        followAnchor(index, kind)
        val fieldX = table[index, FieldCell.X]
        val fieldY = table[index, FieldCell.Y]
        val radius = table[index, FieldCell.RADIUS]
        val pull = table[index, FieldCell.PULL]
        val damagePerSecond = table[index, FieldCell.DPS]
        val element = table.element(index)
        val stacksPerTick = table[index, FieldCell.STACKS].toInt()
        val generation = table[index, FieldCell.GENERATION].toInt()
        // Reaction fields ignore affinity like every reaction; item wells meet the target's aspect.
        val reactionField = table.source(index) == FieldSource.REACTION
        val ignoresAffinity = reactionField
        val remaining = table[index, FieldCell.REMAINING] - delta
        var clock = table[index, FieldCell.TICK_CLOCK] + delta
        if (pull != 0f) {
            for (enemyIndex in enemies.indices) {
                val target = enemies[enemyIndex]
                val knockback = knockbackTaken(target)
                if (!target.alive || knockback <= 0f) continue
                val dx = fieldX - target.x
                val dy = fieldY - target.y
                val distance = length(dx, dy)
                if (distance > radius + target.radius || distance < 1f) continue
                target.vx += dx / distance * pull * delta * knockback
                target.vy += dy / distance * pull * delta * knockback
            }
        }
        // Compost: a rot well slows the ordinary enemies inside it.
        if (kind == CombatFieldKind.ROT_WELL && reactionField && hasVerb(ReactionId.ROT_WELL)) {
            val damping = exp(-reactionValue(ReactionId.ROT_WELL, COMPOST_DAMPING_VALUE) * delta)
            for (enemyIndex in enemies.indices) {
                val target = enemies[enemyIndex]
                if (!target.alive || target.isEliteClass) continue
                if (distanceSquared(fieldX, fieldY, target.x, target.y) > square(radius + target.radius)) continue
                target.vx *= damping
                target.vy *= damping
            }
        }
        while (clock >= CombatField.TICK_SECONDS) {
            clock -= CombatField.TICK_SECONDS
            for (enemyIndex in enemies.indices) {
                val target = enemies[enemyIndex]
                if (!target.alive) continue
                if (distanceSquared(fieldX, fieldY, target.x, target.y) > square(radius + target.radius)) continue
                // A Null Well's rate is a share of the occupant's weighted maximum integrity per second.
                val perSecond = if (kind == CombatFieldKind.NULL_WELL) {
                    target.maxHp * victimFactor(target) * damagePerSecond
                } else {
                    damagePerSecond
                }
                dealStatusDamage(target, perSecond * CombatField.TICK_SECONDS, element, showNumber = false,
                    ignoresAffinity = ignoresAffinity)
                if (element != null && stacksPerTick > 0 && generation < 2 && target.alive) {
                    applyStatus(target, element, stacksPerTick, generation)
                }
                if (target.alive) tickOccupant(kind, target)
            }
        }
        // A tick may have set off a reaction that replaced this very field: the replacement keeps its own life.
        if (table.sequence(index) != sequence) continue
        table[index, FieldCell.REMAINING] = remaining
        table[index, FieldCell.TICK_CLOCK] = clock
        if (remaining <= 0f) expired = true
        if (eatsShots(index, kind)) shotEaters++
        if (kind == CombatFieldKind.ATTRACTOR && reactionField) attractors++
        if (kind == CombatFieldKind.FUSION && reactionField) stars++
    }
    if (shotEaters > 0) eatShots(passSequence)
    if (attractors > 0) bendShots(passSequence, delta)
    if (stars > 0) pullPickups(passSequence, delta)
    if (expired) {
        for (index in 0 until table.count) {
            if (table.sequence(index) < passSequence && table[index, FieldCell.REMAINING] <= 0f) onFieldExpired(index)
        }
        table.removeExpired()
    }
    updateCoreFieldMask(delta)
}

/** A follower field moves with the Core; an attractor with its living anchor (a lost anchor leaves it in place). */
private fun MutableGameState.followAnchor(index: Int, kind: CombatFieldKind) {
    val table = combatFieldTable
    if (table.hasFlag(index, FieldFlags.FOLLOWS_CORE)) {
        moveField(index, coreX, coreY)
        return
    }
    if (kind != CombatFieldKind.ATTRACTOR) return
    val anchorId = table[index, FieldCell.ANCHOR_ID].toInt()
    if (anchorId < 0) return
    for (enemyIndex in enemies.indices) {
        val anchor = enemies[enemyIndex]
        if (anchor.id != anchorId) continue
        if (anchor.alive) {
            moveField(index, anchor.x, anchor.y)
            return
        }
        break
    }
    table[index, FieldCell.ANCHOR_ID] = -1f
    table.setFlag(index, FieldFlags.FOLLOWS_ANCHOR, false)
    table.touch()
}

private fun MutableGameState.moveField(index: Int, x: Float, y: Float) {
    val table = combatFieldTable
    if (table[index, FieldCell.X] == x && table[index, FieldCell.Y] == y) return
    table[index, FieldCell.X] = x
    table[index, FieldCell.Y] = y
    table[index, FieldCell.X2] = x
    table[index, FieldCell.Y2] = y
    table.touch()
}

/** A tick's kind-specific hold on [target]: a Hush silences, a Time Bubble stops ordinary enemies. */
private fun MutableGameState.tickOccupant(kind: CombatFieldKind, target: Enemy) {
    when (kind) {
        CombatFieldKind.HUSH -> if (hasVerb(ReactionId.SILENCE) && !isShifting(target)) {
            // Bosses only through Silence's own rule, so chained hushes still leave them attack windows.
            if (target.isBossClass) {
                silence(target)
            } else {
                target.silencedUntil = max(target.silencedUntil, elapsed + reactionValue(ReactionId.SILENCE, HUSH_SILENCE_VALUE))
            }
        }
        CombatFieldKind.TIME_BUBBLE -> if (!target.isEliteClass && !isShifting(target)) {
            target.disabledUntil = max(target.disabledUntil, elapsed + TIME_BUBBLE_STOP_SECONDS)
        }
        else -> Unit
    }
}

/** True when the field at [index] spends hostile shots: a Maw, a Time Bubble or Dead Air with budget left. */
private fun MutableGameState.eatsShots(index: Int, kind: CombatFieldKind): Boolean {
    val table = combatFieldTable
    if (table.source(index) != FieldSource.REACTION) return false
    val limit = when (kind) {
        CombatFieldKind.NULL_WELL -> if (hasVerb(ReactionId.NULL_WELL)) reactionValue(ReactionId.NULL_WELL, MAW_SHOTS_VALUE) else 0f
        CombatFieldKind.TIME_BUBBLE -> if (hasVerb(ReactionId.STASIS)) reactionValue(ReactionId.STASIS, BUBBLE_SHOTS_VALUE) else 0f
        CombatFieldKind.HUSH -> if (table.hasFlag(index, FieldFlags.KEYSTONE)) DEAD_AIR_SHOTS.toFloat() else 0f
        else -> 0f
    }
    return table[index, FieldCell.CHARGE] < limit
}

/**
 * The lowest sequence among the [limit] newest fields that [matches] accepts and that were admitted before
 * [passSequence]; fields at or above it take part in a capped pass. Int.MAX_VALUE when none.
 */
private inline fun CombatFieldTable.newestFloor(passSequence: Int, limit: Int, matches: (Int) -> Boolean): Int {
    var floor = Int.MAX_VALUE
    var ceiling = passSequence
    repeat(limit) {
        var best = -1
        for (index in 0 until count) {
            val sequence = sequence(index)
            if (sequence < ceiling && sequence > best && matches(index)) best = sequence
        }
        if (best < 0) return floor
        floor = best
        ceiling = best
    }
    return floor
}

/** Shot pass: hostile bolts inside the newest shot-eating fields are spent (each field up to its budget). */
private fun MutableGameState.eatShots(passSequence: Int) {
    val table = combatFieldTable
    val floor = table.newestFloor(passSequence, MAX_SHOT_EATING_FIELDS) { eatsShots(it, table.kind(it)) }
    for (shotIndex in projectiles.indices) {
        val shot = projectiles[shotIndex]
        if (!shot.hostile || shot.spent || shot.kind != ProjectileKind.BOLT) continue
        for (index in 0 until table.count) {
            val sequence = table.sequence(index)
            if (sequence < floor || sequence >= passSequence) continue
            val kind = table.kind(index)
            if (!eatsShots(index, kind)) continue
            val radius = table[index, FieldCell.RADIUS]
            if (distanceSquared(table[index, FieldCell.X], table[index, FieldCell.Y], shot.x, shot.y) > radius * radius) continue
            val caught = if (kind == CombatFieldKind.TIME_BUBBLE && table.hasFlag(index, FieldFlags.KEYSTONE)) {
                rewindShot(index, shot)
            } else {
                eatShot(index, shot)
            }
            if (caught) break
        }
    }
}

/** Spends [shot] into the field at [index], counting it against the field's budget. */
private fun MutableGameState.eatShot(index: Int, shot: Projectile): Boolean {
    if (!eraseHostileShot(shot, EraseCause.REACTION, shot.x, shot.y)) return false
    val table = combatFieldTable
    table[index, FieldCell.CHARGE] = table[index, FieldCell.CHARGE] + 1f
    table.touch()
    return true
}

/**
 * Rewind: the bubble at [index] spends hostile [shot] like any bubble, and its first [REWIND_SHOTS] fly back at
 * the nearest enemy within [REWIND_REACH] as player shots of [REWIND_SHARE] of their damage.
 */
internal fun MutableGameState.rewindShot(index: Int, shot: Projectile): Boolean {
    val returns = combatFieldTable[index, FieldCell.CHARGE] < REWIND_SHOTS
    val x = shot.x
    val y = shot.y
    val damage = shot.damage
    if (!eatShot(index, shot)) return false
    if (returns) nearestEnemy(x, y, REWIND_REACH)?.let { target -> fireRelicProjectile(x, y, target, damage * REWIND_SHARE) }
    return true
}

/** Attractors bend player shots near them toward their centre, keeping each shot's speed. */
private fun MutableGameState.bendShots(passSequence: Int, delta: Float) {
    if (!hasVerb(ReactionId.MAGNETIZE)) return
    val table = combatFieldTable
    val reach = reactionValue(ReactionId.MAGNETIZE, ATTRACTOR_SHOT_REACH_VALUE)
    val maxTurn = reactionValue(ReactionId.MAGNETIZE, ATTRACTOR_SHOT_TURN_VALUE) * delta
    for (index in 0 until table.count) {
        if (table.sequence(index) >= passSequence || table.kind(index) != CombatFieldKind.ATTRACTOR) continue
        if (table.source(index) != FieldSource.REACTION) continue
        val fieldX = table[index, FieldCell.X]
        val fieldY = table[index, FieldCell.Y]
        for (shotIndex in projectiles.indices) {
            val shot = projectiles[shotIndex]
            if (shot.hostile || shot.spent) continue
            val dx = fieldX - shot.x
            val dy = fieldY - shot.y
            if (dx * dx + dy * dy > reach * reach || dx * dx + dy * dy < 1f) continue
            val speed = length(shot.vx, shot.vy)
            if (speed < 1f) continue
            val heading = atan2(shot.vy, shot.vx)
            var turn = atan2(dy, dx) - heading
            while (turn > PI_F) turn -= TAU
            while (turn < -PI_F) turn += TAU
            val bent = heading + turn.coerceIn(-maxTurn, maxTurn)
            shot.vx = cos(bent) * speed
            shot.vy = sin(bent) * speed
        }
    }
}

/** Accretion: the newest stars pull loose Data and repairs toward their centre. */
private fun MutableGameState.pullPickups(passSequence: Int, delta: Float) {
    if (!hasVerb(ReactionId.FUSION) || pickups.isEmpty()) return
    val table = combatFieldTable
    val reach = reactionValue(ReactionId.FUSION, ACCRETION_REACH_VALUE)
    val pace = reactionValue(ReactionId.FUSION, ACCRETION_SPEED_VALUE)
    val floor = table.newestFloor(passSequence, MAX_PICKUP_FIELDS) {
        table.kind(it) == CombatFieldKind.FUSION && table.source(it) == FieldSource.REACTION
    }
    for (index in 0 until table.count) {
        val sequence = table.sequence(index)
        if (sequence < floor || sequence >= passSequence || table.kind(index) != CombatFieldKind.FUSION) continue
        if (table.source(index) != FieldSource.REACTION) continue
        val fieldX = table[index, FieldCell.X]
        val fieldY = table[index, FieldCell.Y]
        for (pickupIndex in pickups.indices) {
            val pickup = pickups[pickupIndex]
            if (!pickup.expires || pickup.attracted) continue
            val dx = fieldX - pickup.x
            val dy = fieldY - pickup.y
            val distance = length(dx, dy)
            if (distance > reach || distance < 1f) continue
            val speed = min(pace, distance / delta)
            pickup.vx = dx / distance * speed
            pickup.vy = dy / distance * speed
        }
    }
}

/** A field's last moment: a star implodes (Accretion), a fed Maw pops. */
private fun MutableGameState.onFieldExpired(index: Int) {
    val table = combatFieldTable
    if (table.source(index) != FieldSource.REACTION || table.hasFlag(index, FieldFlags.KEYSTONE) && table.kind(index) == CombatFieldKind.FUSION) return
    val x = table[index, FieldCell.X]
    val y = table[index, FieldCell.Y]
    val radius = table[index, FieldCell.RADIUS]
    when (table.kind(index)) {
        CombatFieldKind.FUSION -> if (hasVerb(ReactionId.FUSION)) {
            hurlOccupants(x, y, radius, reactionValue(ReactionId.FUSION, ACCRETION_THROW_VALUE))
            val latch = latchedVacuumActive()
            for (pickupIndex in pickups.indices) {
                val pickup = pickups[pickupIndex]
                if (!pickup.expires || pickup.attracted) continue
                if (distanceSquared(x, y, pickup.x, pickup.y) <= square(radius + ACCRETION_LATCH_MARGIN)) pickup.attracted = latch
            }
        }
        CombatFieldKind.NULL_WELL -> if (hasVerb(ReactionId.NULL_WELL) &&
            table[index, FieldCell.CHARGE] >= reactionValue(ReactionId.NULL_WELL, MAW_POP_VALUE)
        ) {
            hurlOccupants(x, y, radius, reactionValue(ReactionId.NULL_WELL, MAW_THROW_VALUE))
            shockwave(x, y, 0.3f, radius, FxColorCode.elementTone(StatusElement.VOID))
        }
        else -> Unit
    }
}

/** Throws every movable enemy within [radius] of ([x], [y]) outward at [speed]. */
private fun MutableGameState.hurlOccupants(x: Float, y: Float, radius: Float, speed: Float) {
    for (enemyIndex in enemies.indices) {
        val target = enemies[enemyIndex]
        if (!target.alive || distanceSquared(x, y, target.x, target.y) > square(radius + target.radius)) continue
        hurl(x, y, target, speed)
    }
}

/**
 * A death inside a reaction's Rot Well: Compost grows the well (radius, life, at most its growths); a
 * Plague Pit passes the victim's Decay to every enemy inside instead.
 */
internal fun MutableGameState.onRotWellDeath(enemy: Enemy) {
    val table = combatFieldTable
    val snapshot = table.count
    for (index in 0 until snapshot) {
        if (table.kind(index) != CombatFieldKind.ROT_WELL || table.source(index) != FieldSource.REACTION) continue
        val x = table[index, FieldCell.X]
        val y = table[index, FieldCell.Y]
        val radius = table[index, FieldCell.RADIUS]
        if (distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        if (table.hasFlag(index, FieldFlags.KEYSTONE)) {
            val decay = stacks(enemy, StatusElement.ENTROPY)
            if (decay <= 0) continue
            for (enemyIndex in enemies.indices) {
                val target = enemies[enemyIndex]
                if (target.id == enemy.id || !target.alive) continue
                if (distanceSquared(x, y, target.x, target.y) <= square(radius + target.radius)) {
                    applyStatus(target, StatusElement.ENTROPY, decay, 1)
                }
            }
            continue
        }
        val growths = table[index, FieldCell.CHARGE]
        if (growths >= reactionValue(ReactionId.ROT_WELL, COMPOST_GROWTHS_VALUE)) continue
        table[index, FieldCell.CHARGE] = growths + 1f
        table[index, FieldCell.RADIUS] = min(COMPOST_MAX_RADIUS, radius + reactionValue(ReactionId.ROT_WELL, COMPOST_GROWTH_RADIUS_VALUE))
        extendField(index, reactionValue(ReactionId.ROT_WELL, COMPOST_GROWTH_SECONDS_VALUE))
    }
}

/**
 * Which field kinds the Core stands in (one bit per [CombatFieldKind] ordinal), with each field's inside flag.
 * Standing in a reaction's Time Bubble hastens the skills.
 */
private fun MutableGameState.updateCoreFieldMask(delta: Float) {
    val table = combatFieldTable
    var mask = 0
    var haste = false
    for (index in 0 until table.count) {
        val radius = table[index, FieldCell.RADIUS]
        val inside = distanceSquared(coreX, coreY, table[index, FieldCell.X], table[index, FieldCell.Y]) <= radius * radius
        if (inside) {
            mask = mask or (1 shl table.kind(index).ordinal)
            if (table.kind(index) == CombatFieldKind.TIME_BUBBLE && table.source(index) == FieldSource.REACTION &&
                !table.hasFlag(index, FieldFlags.KEYSTONE)
            ) haste = true
        }
        table.setFlag(index, FieldFlags.CORE_INSIDE, inside)
    }
    coreFieldMask = mask
    if (haste && hasVerb(ReactionId.STASIS)) refundAllSkills((reactionValue(ReactionId.STASIS, BUBBLE_HASTE_VALUE) - 1f) * delta)
}

/** True while the Core stands in a field of [kind]. */
internal fun MutableGameState.coreInField(kind: CombatFieldKind): Boolean = coreFieldMask and (1 shl kind.ordinal) != 0

private const val PI_F: Float = PI.toFloat()

internal fun MutableGameState.rebaseCombatFields(shiftX: Float, shiftY: Float) {
    val table = combatFieldTable
    if (table.count == 0) return
    for (index in 0 until table.count) {
        table[index, FieldCell.X] = table[index, FieldCell.X] - shiftX
        table[index, FieldCell.Y] = table[index, FieldCell.Y] - shiftY
        table[index, FieldCell.X2] = table[index, FieldCell.X2] - shiftX
        table[index, FieldCell.Y2] = table[index, FieldCell.Y2] - shiftY
    }
    table.touch()
}
