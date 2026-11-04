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
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*

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

/** The same list, an empty one or equal enemies keep the previous projection. */
internal fun MutableGameState.enemyProjections(
    reusableCollections: GameplayRenderModel?,
    identitySource: MutableGameState?,
): ImmutableList<EnemyProjection> =
    enemies.reuseIfIdentical(identitySource?.enemies, reusableCollections?.enemies)
        ?: enemies.reuseEmptyProjection(reusableCollections?.enemies)
        ?: enemies.reuseProjectionIfContentEqual(reusableCollections?.enemies) { value, previous ->
            value.id == previous.id &&
                value.type == previous.type &&
                value.x.sameBitsAs(previous.x) &&
                value.y.sameBitsAs(previous.y) &&
                value.vx.sameBitsAs(previous.vx) &&
                value.vy.sameBitsAs(previous.vy) &&
                value.hp.sameBitsAs(previous.hp) &&
                value.maxHp.sameBitsAs(previous.maxHp) &&
                value.radius.sameBitsAs(previous.radius) &&
                value.actionTimer.sameBitsAs(previous.actionTimer) &&
                value.flash.sameBitsAs(previous.flash) &&
                value.contactCooldown.sameBitsAs(previous.contactCooldown) &&
                value.weaponCooldown.sameBitsAs(previous.weaponCooldown) &&
                value.previousX.sameBitsAs(previous.previousX) &&
                value.previousY.sameBitsAs(previous.previousY) &&
                value.dead == previous.dead &&
                statusBits(value) == previous.statusBits &&
                affinityBits(value) == previous.affinityBits &&
                value.mode == previous.behavior &&
                behaviorRemaining(value).sameBitsAs(previous.behaviorRemaining) &&
                value.facing.sameBitsAs(previous.facing) &&
                linkedTo(value) == previous.linkedTo &&
                shielded(value) == previous.shielded &&
                value.armorIntact == previous.armored &&
                controlBits(value) == previous.controlBits &&
                value.charges == previous.charges
        }
        ?: enemies.mapToImmutableList { value ->
            EnemyProjection(
                id = value.id,
                type = value.type,
                x = value.x,
                y = value.y,
                vx = value.vx,
                vy = value.vy,
                hp = value.hp,
                maxHp = value.maxHp,
                radius = value.radius,
                actionTimer = value.actionTimer,
                flash = value.flash,
                contactCooldown = value.contactCooldown,
                weaponCooldown = value.weaponCooldown,
                previousX = value.previousX,
                previousY = value.previousY,
                dead = value.dead,
                statusBits = statusBits(value),
                affinityBits = affinityBits(value),
                behavior = value.mode,
                behaviorRemaining = behaviorRemaining(value),
                facing = value.facing,
                linkedTo = linkedTo(value),
                shielded = shielded(value),
                armored = value.armorIntact,
                controlBits = controlBits(value),
                charges = value.charges,
            )
        }

/** An empty list, the same list or equal shots keep the previous projection. */
internal fun MutableGameState.projectileProjections(
    reusableCollections: GameplayRenderModel?,
    identitySource: MutableGameState?,
): ImmutableList<ProjectileProjection> =
    projectiles.reuseEmptyProjection(reusableCollections?.projectiles)
        ?: projectiles.reuseIfIdentical(
            identitySource?.projectiles,
            reusableCollections?.projectiles,
        )
        ?: projectiles.reuseProjectionIfContentEqual(
            reusableCollections?.projectiles,
        ) { value, previous ->
            value.x.sameBitsAs(previous.x) &&
                value.y.sameBitsAs(previous.y) &&
                value.vx.sameBitsAs(previous.vx) &&
                value.vy.sameBitsAs(previous.vy) &&
                value.radius.sameBitsAs(previous.radius) &&
                value.life.sameBitsAs(previous.life) &&
                value.hostile == previous.hostile &&
                value.damage.sameBitsAs(previous.damage) &&
                value.pierce == previous.pierce &&
                value.colorIndex == previous.colorIndex &&
                value.sourceWeapon == previous.sourceWeapon &&
                value.previousX.sameBitsAs(previous.previousX) &&
                value.previousY.sameBitsAs(previous.previousY) &&
                value.kind == previous.kind
        }
        ?: projectiles.mapToImmutableList { value ->
            ProjectileProjection(
                x = value.x,
                y = value.y,
                vx = value.vx,
                vy = value.vy,
                radius = value.radius,
                life = value.life,
                hostile = value.hostile,
                damage = value.damage,
                pierce = value.pierce,
                colorIndex = value.colorIndex,
                sourceWeapon = value.sourceWeapon,
                previousX = value.previousX,
                previousY = value.previousY,
                kind = value.kind,
            )
        }

/** An empty list, the same list or equal pickups keep the previous projection. */
internal fun MutableGameState.pickupProjections(
    reusableCollections: GameplayRenderModel?,
    identitySource: MutableGameState?,
): ImmutableList<PickupProjection> =
    pickups.reuseEmptyProjection(reusableCollections?.pickups)
        ?: pickups.reuseIfIdentical(identitySource?.pickups, reusableCollections?.pickups)
        ?: pickups.reuseProjectionIfContentEqual(reusableCollections?.pickups) { value, previous ->
            value.type == previous.type &&
                value.x.sameBitsAs(previous.x) &&
                value.y.sameBitsAs(previous.y) &&
                value.vx.sameBitsAs(previous.vx) &&
                value.vy.sameBitsAs(previous.vy) &&
                value.life.sameBitsAs(previous.life) &&
                value.previousX.sameBitsAs(previous.previousX) &&
                value.previousY.sameBitsAs(previous.previousY)
        }
        ?: pickups.mapToImmutableList { value ->
            PickupProjection(
                value.type,
                value.x,
                value.y,
                value.vx,
                value.vy,
                value.life,
                value.previousX,
                value.previousY,
            )
        }

/** An empty trail, the same list or equal points keep the previous projection. */
internal fun MutableGameState.trailProjections(
    reusableCollections: GameplayRenderModel?,
    identitySource: MutableGameState?,
): ImmutableList<TrailPointProjection> =
    trail.reuseEmptyProjection(reusableCollections?.trail)
        ?: trail.reuseIfIdentical(identitySource?.trail, reusableCollections?.trail)
        ?: trail.reuseProjectionIfContentEqual(reusableCollections?.trail) { value, previous ->
            value.x.sameBitsAs(previous.x) &&
                value.y.sameBitsAs(previous.y) &&
                value.age.sameBitsAs(previous.age) &&
                value.life.sameBitsAs(previous.life) &&
                value.scale.sameBitsAs(previous.scale)
        }
        ?: trail.mapToImmutableList { value -> TrailPointProjection(value.x, value.y, value.age, value.life, value.scale) }

/** An empty list, the same list or equal nodes keep the previous projection. */
internal fun MutableGameState.weaponNodeProjections(
    reusableCollections: GameplayRenderModel?,
    identitySource: MutableGameState?,
): ImmutableList<WeaponNodeProjection> =
    weaponNodes.reuseEmptyProjection(reusableCollections?.weaponNodes)
        ?: weaponNodes.reuseIfIdentical(
            identitySource?.weaponNodes,
            reusableCollections?.weaponNodes,
        )
        ?: weaponNodes.reuseProjectionIfContentEqual(
            reusableCollections?.weaponNodes,
        ) { value, previous ->
            value.type == previous.type &&
                value.x.sameBitsAs(previous.x) &&
                value.y.sameBitsAs(previous.y) &&
                value.life.sameBitsAs(previous.life) &&
                value.maxLife.sameBitsAs(previous.maxLife) &&
                value.radius.sameBitsAs(previous.radius)
        }
        ?: weaponNodes.mapToImmutableList { value ->
            WeaponNodeProjection(value.type, value.x, value.y, value.life, value.maxLife, value.radius)
        }

/** An empty list, the same list or equal orbitals keep the previous projection. */
internal fun MutableGameState.weaponOrbitalProjections(
    reusableCollections: GameplayRenderModel?,
    identitySource: MutableGameState?,
): ImmutableList<WeaponOrbitalProjection> =
    weaponOrbitals.reuseEmptyProjection(reusableCollections?.weaponOrbitals)
        ?: weaponOrbitals.reuseIfIdentical(
            identitySource?.weaponOrbitals,
            reusableCollections?.weaponOrbitals,
        )
        ?: weaponOrbitals.reuseProjectionIfContentEqual(
            reusableCollections?.weaponOrbitals,
        ) { value, previous ->
            value.index == previous.index &&
                value.x.sameBitsAs(previous.x) &&
                value.y.sameBitsAs(previous.y) &&
                value.radius.sameBitsAs(previous.radius) &&
                value.previousX.sameBitsAs(previous.previousX) &&
                value.previousY.sameBitsAs(previous.previousY)
        }
        ?: weaponOrbitals.mapToImmutableList { value ->
            WeaponOrbitalProjection(value.index, value.x, value.y, value.radius, value.previousX, value.previousY)
        }
