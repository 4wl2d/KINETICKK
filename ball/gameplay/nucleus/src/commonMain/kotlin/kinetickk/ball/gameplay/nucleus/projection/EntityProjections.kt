// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.mapToImmutableList
import kinetickk.foundation.collections.ImmutableList
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*

/**
 * Control and mark bits of [enemy] ([EnemyControlBits]): stopped (never a boss), silenced, thrown, burning
 * with Blackfire, an Unmake pending, its live mark, the Void Anchor and an attractor's anchor.
 */
internal fun MutableGameState.controlBits(enemy: Enemy): Int {
    var bits = 0
    if (enemy.disabledUntil > elapsed && !enemy.isBossClass) bits = bits or EnemyControlBits.STOPPED
    if (enemy.silencedUntil > elapsed) bits = bits or EnemyControlBits.SILENCED
    if (enemy.thrownUntil > elapsed) bits = bits or EnemyControlBits.THROWN
    if (enemy.status[StatusCell.BLACKFIRE_UNTIL] > elapsed) bits = bits or EnemyControlBits.BLACKFIRE
    if (enemy.status[StatusCell.UNMAKE_AT] > 0f) bits = bits or EnemyControlBits.UNMAKE_PENDING
    val mark = markOf(enemy)
    if (mark != null) bits = bits or ((mark.ordinal + 1) shl EnemyControlBits.MARK_SHIFT)
    if (voidAnchorId == enemy.id && voidAnchorUntil > elapsed) bits = bits or EnemyControlBits.VOID_ANCHOR
    if (anchorsAttractor(enemy.id)) bits = bits or EnemyControlBits.ATTRACTOR_ANCHOR
    if (enemy.isBossClass && apexActive() && apexStrikesLeft > 0) bits = bits or EnemyControlBits.APEX_STRIKE_TARGET
    return bits
}

private fun MutableGameState.anchorsAttractor(enemyId: Int): Boolean {
    val table = combatFieldTable
    for (index in 0 until table.count) {
        if (table.kind(index) == CombatFieldKind.ATTRACTOR && table[index, FieldCell.ANCHOR_ID].toInt() == enemyId) return true
    }
    return false
}

/** The same runtime or equal entities keep the previous projection. */
internal fun MutableGameState.weaponEntityProjection(
    reusable: ImmutableList<WeaponEntityProjection>?,
    identityRuntime: WeaponRuntime?,
): ImmutableList<WeaponEntityProjection> {
    val entities = weaponRuntime.entities
    if (reusable != null) {
        if (weaponRuntime === identityRuntime) return reusable
        if (entities.isEmpty() && reusable.isEmpty()) return reusable
        if (entities.reuseProjectionIfContentEqual(reusable) { value, previous -> previous.matches(value) } != null) return reusable
    }
    if (entities.isEmpty()) return immutableListOf()
    return entities.mapToImmutableList { value ->
        WeaponEntityProjection(
            kind = value.kind,
            x = value.x,
            y = value.y,
            previousX = value.previousX,
            previousY = value.previousY,
            endX = value.endX,
            endY = value.endY,
            previousEndX = value.previousEndX,
            previousEndY = value.previousEndY,
            angle = value.angle,
            previousAngle = value.previousAngle,
            radius = value.radius,
            extent = value.extent,
            life = value.life,
            maxLife = value.maxLife,
            phase = value.phase,
        )
    }
}

private fun WeaponEntityProjection.matches(entity: WeaponEntity): Boolean =
    kind == entity.kind &&
        x.sameBitsAs(entity.x) &&
        y.sameBitsAs(entity.y) &&
        previousX.sameBitsAs(entity.previousX) &&
        previousY.sameBitsAs(entity.previousY) &&
        endX.sameBitsAs(entity.endX) &&
        endY.sameBitsAs(entity.endY) &&
        previousEndX.sameBitsAs(entity.previousEndX) &&
        previousEndY.sameBitsAs(entity.previousEndY) &&
        angle.sameBitsAs(entity.angle) &&
        previousAngle.sameBitsAs(entity.previousAngle) &&
        radius.sameBitsAs(entity.radius) &&
        extent.sameBitsAs(entity.extent) &&
        life.sameBitsAs(entity.life) &&
        maxLife.sameBitsAs(entity.maxLife) &&
        phase == entity.phase

/** The window changes only when the Core changes chunk (or a rock shatters); otherwise it is reused. */
internal fun MutableGameState.obstacleProjection(
    reusable: ImmutableList<ObstacleProjection>?,
    identityField: ObstacleField?,
): ImmutableList<ObstacleProjection> {
    val field = obstacleField
    if (reusable != null) {
        if (field === identityField) return reusable
        if (field.obstacles.reuseProjectionIfContentEqual(reusable) { value, previous -> previous.matches(value) } != null) {
            return reusable
        }
    }
    return field.obstacles.mapToImmutableList { value ->
        ObstacleProjection(value.id, value.kind, value.x, value.y, value.radius, value.shapeSeed, value.fuseAt, value.repels)
    }
}

private fun ObstacleProjection.matches(obstacle: Obstacle): Boolean =
    id == obstacle.id &&
        kind == obstacle.kind &&
        x.sameBitsAs(obstacle.x) &&
        y.sameBitsAs(obstacle.y) &&
        radius.sameBitsAs(obstacle.radius) &&
        shapeSeed == obstacle.shapeSeed &&
        fuseAt.sameBitsAs(obstacle.fuseAt) &&
        repels == obstacle.repels
