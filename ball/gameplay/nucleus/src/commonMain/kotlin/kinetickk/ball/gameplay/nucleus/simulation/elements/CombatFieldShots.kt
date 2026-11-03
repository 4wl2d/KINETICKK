// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * True when the field at [index] spends hostile shots: a Maw or a Time Bubble (a reaction's or an item zone's) or
 * Dead Air with budget left, or a skill's Frost Line within its bolt cap.
 */
internal fun MutableGameState.eatsShots(index: Int, kind: CombatFieldKind): Boolean {
    val table = combatFieldTable
    val limit = when (table.source(index)) {
        FieldSource.REACTION, FieldSource.ITEM -> when (kind) {
            CombatFieldKind.NULL_WELL -> if (hasVerb(ReactionId.NULL_WELL)) reactionValue(ReactionId.NULL_WELL, MAW_SHOTS_VALUE) else 0f
            CombatFieldKind.TIME_BUBBLE -> if (hasVerb(ReactionId.STASIS)) reactionValue(ReactionId.STASIS, BUBBLE_SHOTS_VALUE) else 0f
            CombatFieldKind.HUSH -> if (table.hasFlag(index, FieldFlags.KEYSTONE)) DEAD_AIR_SHOTS.toFloat() else 0f
            else -> 0f
        }
        FieldSource.SKILL -> if (kind == CombatFieldKind.FROST_LINE) table[index, FieldCell.POWER] else 0f
    }
    return table[index, FieldCell.CHARGE] < limit
}

/**
 * The lowest sequence among the [limit] newest fields that [matches] accepts and that were admitted before
 * [passSequence]; fields at or above it take part in a capped pass. Int.MAX_VALUE when none.
 */
internal inline fun CombatFieldTable.newestFloor(passSequence: Int, limit: Int, matches: (Int) -> Boolean): Int {
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
internal fun MutableGameState.eatShots(passSequence: Int) {
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
            if (!fieldContains(index, shot.x, shot.y, 0f)) continue
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
    val table = combatFieldTable
    val cause = when (table.source(index)) {
        FieldSource.SKILL -> EraseCause.SKILL
        FieldSource.ITEM -> EraseCause.ITEM
        FieldSource.REACTION -> EraseCause.REACTION
    }
    if (!eraseHostileShot(shot, cause, shot.x, shot.y)) return false
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
internal fun MutableGameState.bendShots(passSequence: Int, delta: Float) {
    if (!hasVerb(ReactionId.MAGNETIZE)) return
    val table = combatFieldTable
    val reach = reactionValue(ReactionId.MAGNETIZE, ATTRACTOR_SHOT_REACH_VALUE)
    val maxTurn = reactionValue(ReactionId.MAGNETIZE, ATTRACTOR_SHOT_TURN_VALUE) * delta
    for (index in 0 until table.count) {
        if (table.sequence(index) >= passSequence || table.kind(index) != CombatFieldKind.ATTRACTOR) continue
        if (table.source(index) == FieldSource.SKILL) continue
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

private const val PI_F: Float = PI.toFloat()
