// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

import kinetickk.feature.game.domain.model.*
import kinetickk.feature.game.domain.protocol.SoundCue
import kinetickk.feature.game.domain.protocol.VisualFxCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin


internal fun MutableGameState.endRun(reason: String) {
    if (phase != GamePhase.RUNNING) return
    phase = GamePhase.GAME_OVER
    message = reason
    messageTime = 10f
    keyboardBrakeActive = false
    secondaryBrakeActive = false
    touchBrakeActive = false
    updateBraking()
    dashBufferTime = 0f
    burst(coreX, coreY, 45, 4)
    bankRunMatter()
    emitSound(SoundCue.GAME_OVER)
}

internal fun MutableGameState.firePlayerProjectile(
    x: Float,
    y: Float,
    angle: Float,
    projectileSpeed: Float,
    damage: Float,
    pierce: Int,
    radius: Float,
    colorIndex: Int,
) {
    addProjectile(Projectile(
        x = x,
        y = y,
        vx = cos(angle) * projectileSpeed,
        vy = sin(angle) * projectileSpeed,
        radius = radius,
        life = 4f,
        hostile = false,
        damage = damage,
        pierce = pierce,
        colorIndex = colorIndex,
        sourceWeapon = weapon,
    ))
}

internal fun MutableGameState.fireSpread(x: Float, y: Float, angle: Float, count: Int, spacing: Float, projectileSpeed: Float) {
    val scaledSpeed = projectileSpeed * rebirthProfile.enemySpeedMultiplier
    val offset = (count - 1) * spacing * 0.5f
    repeat(count) { index ->
        val shotAngle = angle - offset + index * spacing
        addProjectile(Projectile(x, y, cos(shotAngle) * scaledSpeed, sin(shotAngle) * scaledSpeed, 5f, 6f))
    }
}

internal fun MutableGameState.fireProjectileWall(
    x: Float,
    y: Float,
    angle: Float,
    count: Int,
    spacing: Float,
    projectileSpeed: Float,
) {
    val scaledSpeed = projectileSpeed * rebirthProfile.enemySpeedMultiplier
    val directionX = cos(angle)
    val directionY = sin(angle)
    val tangentX = -directionY
    val tangentY = directionX
    val offset = (count - 1) * spacing * 0.5f
    repeat(count) { index ->
        val lateral = index * spacing - offset
        addProjectile(Projectile(
            x = x + tangentX * lateral,
            y = y + tangentY * lateral,
            vx = directionX * scaledSpeed,
            vy = directionY * scaledSpeed,
            radius = 5f,
            life = 6f,
        ))
    }
}

internal fun MutableGameState.fireRadial(x: Float, y: Float, count: Int, projectileSpeed: Float, rotation: Float) {
    val scaledSpeed = projectileSpeed * rebirthProfile.enemySpeedMultiplier
    repeat(count) { index ->
        val angle = rotation + index * TAU / count
        addProjectile(Projectile(x, y, cos(angle) * scaledSpeed, sin(angle) * scaledSpeed, 5f, 8f))
    }
}

internal fun MutableGameState.addProjectile(projectile: Projectile) {
    if (projectiles.size < MutableGameState.MAX_PROJECTILES) projectiles += projectile
}

internal fun MutableGameState.addPickup(pickup: Pickup) {
    if (pickups.size < MutableGameState.MAX_PICKUPS) pickups += pickup
}

internal fun MutableGameState.burst(x: Float, y: Float, requestedCount: Int, colorIndex: Int) {
    emitVisualFx(
        VisualFxCue.Burst(
            x = x,
            y = y,
            requestedCount = requestedCount,
            colorIndex = colorIndex,
            density = settings.particleDensity,
        ),
    )
}

internal fun MutableGameState.directionalBurst(
    x: Float,
    y: Float,
    requestedCount: Int,
    colorIndex: Int,
    directionX: Float,
    directionY: Float,
) {
    emitVisualFx(
        VisualFxCue.DirectionalBurst(
            x = x,
            y = y,
            requestedCount = requestedCount,
            colorIndex = colorIndex,
            directionX = directionX,
            directionY = directionY,
            density = settings.particleDensity,
        ),
    )
}

internal fun MutableGameState.shockwave(x: Float, y: Float, life: Float, maxRadius: Float, colorIndex: Int) {
    emitVisualFx(
        VisualFxCue.ShockwaveAdded(
            x = x,
            y = y,
            life = life,
            maxRadius = maxRadius,
            colorIndex = colorIndex,
        ),
    )
}

internal fun MutableGameState.addWeaponArc(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    life: Float = 0.14f,
) {
    emitVisualFx(
        VisualFxCue.WeaponArcAdded(
            fromX = fromX,
            fromY = fromY,
            toX = toX,
            toY = toY,
            life = life,
        ),
    )
}

internal fun MutableGameState.nearestEnemy(x: Float, y: Float, range: Float): Enemy? = enemies
    .asSequence()
    .filter { !it.dead && it.hp > 0f && distanceSquared(x, y, it.x, it.y) <= range * range }
    .minByOrNull { distanceSquared(x, y, it.x, it.y) }

internal fun MutableGameState.movementAngle(): Float {
    if (speed > 20f) return atan2(velocityY, velocityX)
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    return atan2(targetY - coreY, targetX - coreX)
}

internal fun MutableGameState.effectiveWeaponPower(): Float =
    weaponPower *
        rebirthProfile.playerPowerMultiplier *
        (1f + (weaponLevel - 1) * 0.08f) *
        (1f + currentWeaponMastery.damageBonus) *
        if (overdriveTime > 0f) 1.45f else 1f

internal fun MutableGameState.cooldown(base: Float): Float =
    base / (
        attackSpeed *
            (1f + currentWeaponMastery.activationSpeedBonus) *
            (1f + relicActivationSpeedBonus()) *
            if (overdriveTime > 0f) 1.35f else 1f
        ).coerceAtLeast(0.2f)

internal fun MutableGameState.relicActivationSpeedBonus(): Float {
    var bonus = 0f
    if (slipstreamRelayTime > 0f) bonus += 0.06f * relicRank(RelicId.SLIPSTREAM_RELAY)
    if (borrowedMomentTime > 0f) bonus += 0.09f * relicRank(RelicId.BORROWED_MOMENT)
    if (hp <= maxHp * 0.35f) bonus += 0.08f * relicRank(RelicId.LAST_LIGHT)
    val crownRank = relicRank(RelicId.CROWN_OF_FOUR_WINDS)
    if (crownRank > 0) bonus += 0.03f * crownRank * distinctRelicAspectCount()
    if (overdriveTime > 0f) bonus += 0.10f * relicRank(RelicId.ENGINE_OF_PARADOX)
    return bonus
}

internal fun MutableGameState.compactItemDescription(item: ItemDefinition): String =
    formatModifier(item.primary) + "  //  " + formatModifier(item.secondary)

internal fun MutableGameState.formatModifier(modifier: ItemModifier): String {
    val amount = if (modifier.effect.unit == ModifierUnit.PERCENT) modifier.amount * 100f else modifier.amount
    val rounded = (amount * 10f).toInt() / 10f
    val suffix = when (modifier.effect.unit) {
        ModifierUnit.PERCENT -> "%"
        ModifierUnit.PER_SECOND -> "/s"
        ModifierUnit.SECONDS -> "s"
        ModifierUnit.FLAT -> ""
    }
    return "+" + rounded + suffix + " " + modifier.effect.displayLabel
}
