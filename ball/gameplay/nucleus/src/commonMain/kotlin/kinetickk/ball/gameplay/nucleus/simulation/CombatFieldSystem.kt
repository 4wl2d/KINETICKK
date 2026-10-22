// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*

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
 * Advances every field admitted before this pass: pull, then the ticks that came due, then life. A field
 * admitted or overwritten during the pass (a reaction set off by a tick) starts on the next step, and a
 * field a tick replaced keeps its replacement's life. Spent fields are compacted after the pass; then the
 * Core pass records which kinds the Core stands in. Allocation-free.
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
    for (index in 0 until snapshot) {
        val sequence = table.sequence(index)
        if (sequence >= passSequence) continue
        val kind = table.kind(index)
        val fieldX = table[index, FieldCell.X]
        val fieldY = table[index, FieldCell.Y]
        val radius = table[index, FieldCell.RADIUS]
        val pull = table[index, FieldCell.PULL]
        val damagePerSecond = table[index, FieldCell.DPS]
        val element = table.element(index)
        val stacksPerTick = table[index, FieldCell.STACKS].toInt()
        val generation = table[index, FieldCell.GENERATION].toInt()
        // Reaction fields ignore affinity like every reaction; item wells meet the target's aspect.
        val ignoresAffinity = table.source(index) == FieldSource.REACTION
        val remaining = table[index, FieldCell.REMAINING] - delta
        var clock = table[index, FieldCell.TICK_CLOCK] + delta
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
            }
        }
        // A tick may have set off a reaction that replaced this very field: the replacement keeps its own life.
        if (table.sequence(index) != sequence) continue
        table[index, FieldCell.REMAINING] = remaining
        table[index, FieldCell.TICK_CLOCK] = clock
        if (remaining <= 0f) expired = true
    }
    if (expired) table.removeExpired()
    updateCoreFieldMask()
}

/** Which field kinds the Core stands in (one bit per [CombatFieldKind] ordinal), with each field's inside flag. */
private fun MutableGameState.updateCoreFieldMask() {
    val table = combatFieldTable
    var mask = 0
    for (index in 0 until table.count) {
        val radius = table[index, FieldCell.RADIUS]
        val inside = distanceSquared(coreX, coreY, table[index, FieldCell.X], table[index, FieldCell.Y]) <= radius * radius
        if (inside) mask = mask or (1 shl table.kind(index).ordinal)
        table.setFlag(index, FieldFlags.CORE_INSIDE, inside)
    }
    coreFieldMask = mask
}

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
