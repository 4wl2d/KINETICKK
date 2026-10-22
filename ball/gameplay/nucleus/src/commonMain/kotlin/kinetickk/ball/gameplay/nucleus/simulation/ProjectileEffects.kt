// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation


import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
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
    emitSound(GameplayAudioCue.GAME_OVER)
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
    procKey: Int = WeaponRuntime.PROC_ALWAYS,
    life: Float = 4f,
) {
    addProjectile(Projectile(
        x = x,
        y = y,
        vx = cos(angle) * projectileSpeed,
        vy = sin(angle) * projectileSpeed,
        radius = radius,
        life = life,
        hostile = false,
        damage = damage,
        pierce = pierce,
        colorIndex = colorIndex,
        sourceWeapon = weapon,
        procKey = procKey,
    ))
}

/** Aimed hostile fan; [damage] comes from [hostileDamage] when the volley is fired. */
internal fun MutableGameState.fireSpread(
    x: Float,
    y: Float,
    angle: Float,
    count: Int,
    spacing: Float,
    projectileSpeed: Float,
    damage: Float,
) {
    val scaledSpeed = projectileSpeed * rebirthProfile.enemySpeedMultiplier
    val offset = (count - 1) * spacing * 0.5f
    repeat(count) { index ->
        val shotAngle = angle - offset + index * spacing
        addProjectile(Projectile(x, y, cos(shotAngle) * scaledSpeed, sin(shotAngle) * scaledSpeed, 5f, 6f, damage = damage))
    }
}

internal fun MutableGameState.fireProjectileWall(
    x: Float,
    y: Float,
    angle: Float,
    count: Int,
    spacing: Float,
    projectileSpeed: Float,
    damage: Float,
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
            damage = damage,
        ))
    }
}

internal fun MutableGameState.fireRadial(
    x: Float,
    y: Float,
    count: Int,
    projectileSpeed: Float,
    rotation: Float,
    damage: Float,
    life: Float = 8f,
) {
    val scaledSpeed = projectileSpeed * rebirthProfile.enemySpeedMultiplier
    repeat(count) { index ->
        val angle = rotation + index * TAU / count
        addProjectile(Projectile(x, y, cos(angle) * scaledSpeed, sin(angle) * scaledSpeed, 5f, life, damage = damage))
    }
}

internal fun MutableGameState.addProjectile(projectile: Projectile) {
    if (projectiles.size < MutableGameState.MAX_PROJECTILES) projectiles += projectile
}

/** At the cap a new Data or repair is dropped; a Key or Relic resonance evicts the oldest Data instead. */
internal fun MutableGameState.addPickup(pickup: Pickup) {
    if (pickup.type == PickupType.DATA) pickup.life = blackoutDataLife(pickup.life)
    if (pickups.size >= MutableGameState.MAX_PICKUPS) {
        if (pickup.expires) return
        val oldest = oldestEvictablePickup()
        if (oldest < 0) return
        pickups.removeAt(oldest)
    }
    pickups += pickup
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
    style: Int = LinkStyleCode.WEAPON,
) {
    emitVisualFx(
        VisualFxCue.WeaponArcAdded(
            fromX = fromX,
            fromY = fromY,
            toX = toX,
            toY = toY,
            life = life,
            style = style,
        ),
    )
}

/** What spent a hostile bolt. */
internal enum class EraseCause { REACTION, SKILL, ITEM, CHARACTER, APEX }

/**
 * Spends hostile bolt [shot] (its life drops to 0 and the list keeps its shape, so any pass may call this
 * while it walks the projectiles). Friendly, already spent and MINE shots are refused; an Apex erasure spends
 * one bolt of the running activation's budget and is refused once the budget is empty. The first
 * [MAX_SHOT_ERASURES_SHOWN_PER_STEP] erasures of a fixed step show a burst at ([x], [y]) (Apex gold for the
 * Apex's) and fire SHOT_ERASED clauses there. True when the shot was spent.
 */
internal fun MutableGameState.eraseHostileShot(shot: Projectile, cause: EraseCause, x: Float, y: Float): Boolean {
    if (!shot.hostile || shot.spent || shot.kind != ProjectileKind.BOLT) return false
    if (cause == EraseCause.APEX) {
        val left = skillRuntime.apex(ApexCell.BOLTS_LEFT)
        if (left < 1f) return false
        skillRuntime.setApex(ApexCell.BOLTS_LEFT, left - 1f)
    }
    shot.life = 0f
    shotsErased++
    if (shotErasedDispatches < MAX_SHOT_ERASURES_SHOWN_PER_STEP) {
        shotErasedDispatches++
        burst(x, y, 5, if (cause == EraseCause.APEX) FxColorCode.APEX_GOLD else 2)
        // The same bound caps SHOT_ERASED: at most four dispatches per fixed step.
        if (listens(kinetickk.ball.content.api.EffectTrigger.SHOT_ERASED)) {
            fireClauses(kinetickk.ball.content.api.EffectTrigger.SHOT_ERASED, originX = x, originY = y)
        }
    }
    return true
}

internal const val MAX_SHOT_ERASURES_SHOWN_PER_STEP: Int = 4

/** Nearest live enemy within [range]; a shifting Architect is never a target. */
internal fun MutableGameState.nearestEnemy(x: Float, y: Float, range: Float): Enemy? {
    var nearest: Enemy? = null
    var nearestDistanceSquared = range * range
    for (index in enemies.indices) {
        val candidate = enemies[index]
        if (candidate.dead || !(candidate.hp > 0f) || isShifting(candidate)) continue
        val candidateDistanceSquared = distanceSquared(x, y, candidate.x, candidate.y)
        if (
            candidateDistanceSquared <= nearestDistanceSquared &&
            (nearest == null || candidateDistanceSquared < nearestDistanceSquared)
        ) {
            nearest = candidate
            nearestDistanceSquared = candidateDistanceSquared
        }
    }
    return nearest
}

internal fun MutableGameState.movementAngle(): Float {
    if (speed > 20f) return atan2(velocityY, velocityX)
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    return atan2(targetY - coreY, targetX - coreX)
}

/**
 * Power of the equipped weapon's own hits: item weapon power, Rebirth, level, generic mastery, run growth
 * (1 + 1.6p plus the Matrix bonus), Overdrive and the synergies ([weaponSynergyPower]: tag resonance,
 * signature, Relic key harmony, scaling stat). An OVERDRIVE-scaling weapon takes Overdrive through its
 * scaling only, so no input counts twice.
 */
internal fun MutableGameState.effectiveWeaponPower(): Float =
    weaponPower *
        rebirthProfile.playerPowerMultiplier *
        (1f + (weaponLevel - 1) * 0.08f) *
        (1f + currentWeaponMastery.damageBonus) *
        weaponRunGrowth() *
        weaponSynergyPower() *
        weaponOverdriveFactor()

/**
 * Power of character abilities and weapon-based item effects. It shares the weapon's level, mastery and
 * Overdrive factors but grows with [kinetickk.ball.content.api.RunTempoProfile.abilityRunGrowth], so
 * equipped-weapon bonuses applied only to [effectiveWeaponPower] never leak into abilities.
 */
internal fun MutableGameState.abilityPower(): Float =
    weaponPower *
        rebirthProfile.playerPowerMultiplier *
        (1f + (weaponLevel - 1) * 0.08f) *
        (1f + currentWeaponMastery.damageBonus) *
        content.tempo.abilityRunGrowth(elapsed) *
        if (overdriveTime > 0f) 1.45f else 1f

internal fun MutableGameState.cooldown(base: Float): Float =
    base / (
        attackSpeed *
            (1f + currentWeaponMastery.activationSpeedBonus) *
            buildActivationBonus() *
            characterActivationMultiplier() *
            (if (overdriveTime > 0f) 1.35f else 1f)
        ).coerceAtLeast(0.2f)
