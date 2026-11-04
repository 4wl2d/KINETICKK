// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kotlin.math.cos
import kotlin.math.sin
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

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
