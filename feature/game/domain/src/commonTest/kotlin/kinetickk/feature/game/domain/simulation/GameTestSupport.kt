// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

import kinetickk.feature.game.domain.model.DelayedRelicHit
import kinetickk.feature.game.domain.model.Enemy
import kinetickk.feature.game.domain.model.EnemyType
import kinetickk.feature.game.domain.model.GamePhase
import kinetickk.feature.game.domain.model.Pickup
import kinetickk.feature.game.domain.model.PickupType
import kinetickk.feature.game.domain.model.Projectile
import kinetickk.feature.game.domain.model.RelicId
import kinetickk.feature.game.domain.model.Totem
import kinetickk.feature.game.domain.model.TrailPoint
import kinetickk.feature.game.domain.model.WeaponHitCadence
import kinetickk.feature.game.domain.model.WeaponId
import kotlin.math.max

internal fun MutableGameState.setVelocityForTesting(x: Float, y: Float) {
    velocityX = x
    velocityY = y
}

internal fun MutableGameState.addEnemyForTesting(
    x: Float,
    y: Float,
    hp: Float = 1_000f,
    radius: Float = 17f,
    type: EnemyType = EnemyType.DRIFTER,
): Enemy {
    val enemy = Enemy(nextEntityId++, type, x, y, hp = hp, maxHp = hp, radius = radius)
    enemies += enemy
    return enemy
}

internal fun MutableGameState.activateTotemForTesting(x: Float = coreX, y: Float = coreY) {
    keys++
    totem = Totem(x, y)
}

internal fun MutableGameState.equipWeaponForTesting(id: WeaponId) = equipRunWeapon(id)

internal fun MutableGameState.acquireItemForTesting(id: Int) = acquireItem(id)

internal fun MutableGameState.acquireRelicForTesting(id: RelicId) = acquireRelic(id)

internal fun MutableGameState.openRelicChoiceForTesting() {
    pendingRelicChoices++
    openNextPendingChoice()
}

internal fun MutableGameState.dropRelicForTesting(x: Float = coreX, y: Float = coreY) {
    pickups += Pickup(PickupType.RELIC, x, y)
}

internal fun MutableGameState.addProjectileForTesting() {
    projectiles += Projectile(coreX, coreY, 0f, 0f, 1f, 1f)
}

internal fun MutableGameState.addTrailPointForTesting() {
    trail += TrailPoint(coreX, coreY, 0f)
}

internal fun MutableGameState.addDelayedRelicHitForTesting() {
    delayedRelicHits += DelayedRelicHit(RelicId.ECHO_CHAMBER, 0, 1f, 1f)
}

internal fun MutableGameState.killEnemyForTesting(
    type: EnemyType,
    x: Float = coreX,
    y: Float = coreY,
) {
    val enemy = addEnemyForTesting(x, y, hp = 1f, type = type)
    damageEnemy(enemy, 2f, relicKillProcsEligible = true)
    onEnemyKilled(enemy)
    enemies.remove(enemy)
}

internal fun MutableGameState.triggerWeaponContactForTesting(target: Enemy, continuous: Boolean = false): Float =
    dealWeaponDamage(
        target,
        baseAmount = 20f,
        cadence = if (continuous) WeaponHitCadence.CONTINUOUS else WeaponHitCadence.DISCRETE,
    ).amount

internal fun MutableGameState.damageEnemyForTesting(target: Enemy, amount: Float): Float =
    damageEnemy(target, amount).amount

internal fun MutableGameState.relicProcCountForTesting(id: RelicId): Int = relicProcCounts[id.ordinal]

internal fun MutableGameState.agonyMutationCountForTesting(id: WeaponId): Int = agonyMutationCounts[id.ordinal]

internal fun MutableGameState.agonyMutationCountsForTesting(): List<Int> = agonyMutationCounts.toList()

internal fun MutableGameState.delayedRelicHitCountForTesting(): Int = delayedRelicHits.size

internal fun MutableGameState.grantDataForTesting(amount: Float) = gainData(amount)

internal fun MutableGameState.amplifyWeaponForTesting(levels: Int = 1) {
    repeat(max(0, levels)) { amplifyCurrentWeapon() }
}

internal fun MutableGameState.markCurrentRebirthClearedForTesting() {
    highestClearedRebirth = max(highestClearedRebirth, rebirthLevel)
    phase = GamePhase.VICTORY
}
