// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.max
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*

// ---------------------------------------------------------------------------------------------
// Weapon shots (player projectiles of the equipped weapon).

/**
 * A weapon shot meets [enemy]: its forms and signature twists, the hit itself (proc key from the shot)
 * and the weapon's own element. Runs inside the projectile pass; the pass removes spent shots.
 */
internal fun MutableGameState.weaponShotHit(projectile: Projectile, sourceWeapon: WeaponId, enemy: Enemy) {
    val equipped = sourceWeapon == weapon
    var forceCrit = false
    when (sourceWeapon) {
        WeaponId.NULL_LANCE -> if (equipped) {
            // Third form: statused or marked enemies take a crit.
            if (hasMasteryForm(3) && (totalStacks(enemy) > 0 || isMarked(enemy) || enemy.status[StatusCell.MARKED_UNTIL] > elapsed)) {
                forceCrit = true
            }
            // Shard signature: a lance on a mark strikes a double Puncture and keeps its pierce.
            if (signatureActive() && tryPuncture(enemy, 2f)) projectile.pierce++
        }
        // Third form: a point-blank pellet tears through without spending its pierce.
        WeaponId.SCATTER_BLOOM -> if (equipped && hasMasteryForm(3) &&
            distanceSquared(coreX, coreY, enemy.x, enemy.y) <= square(SCATTER_POINT_BLANK + enemy.radius)
        ) projectile.pierce++
        else -> Unit
    }
    if (!enemy.alive) return
    val result = dealWeaponDamage(enemy, projectile.damage, canCrit = true, sourceWeapon = sourceWeapon, procKey = projectile.procKey,
        forceCrit = forceCrit)
    if (!equipped || result.amount <= 0f) return
    when (sourceWeapon) {
        WeaponId.ION_SWARM -> {
            val runtime = weaponRuntime
            runtime.elementHits = (runtime.elementHits + 1) % ION_SWARM_CHARGE_EVERY
            if (runtime.elementHits == 0) applyWeaponStatus(enemy, StatusElement.ION, 1)
        }
        WeaponId.QUASAR_CANNON -> {
            applyWeaponStatus(enemy, StatusElement.SONIC, 2)
            val speed = max(1f, length(projectile.vx, projectile.vy))
            val knockback = QUASAR_KNOCKBACK * knockbackTaken(enemy)
            if (enemy.alive && knockback > 0f) {
                enemy.vx += projectile.vx / speed * knockback
                enemy.vy += projectile.vy / speed * knockback
                markThrown(enemy)
            }
        }
        WeaponId.SCATTER_BLOOM -> if (signatureActive()) markForPuncture(enemy)
        WeaponId.SENTRY_GRID -> sentrySplash(projectile, enemy)
        else -> Unit
    }
}

/**
 * A sentry shot bursts where it strikes: every other enemy within [SENTRY_SPLASH_RADIUS] of the struck one takes
 * [SENTRY_SPLASH_SHARE] of the shot (it may crit, but fires no on-hit effects). A crowd feeds the grid; a lone
 * target takes only the shot.
 */
private fun MutableGameState.sentrySplash(projectile: Projectile, struck: Enemy) {
    val damage = projectile.damage * SENTRY_SPLASH_SHARE
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy === struck || !enemy.alive || isShifting(enemy)) continue
        if (distanceSquared(struck.x, struck.y, enemy.x, enemy.y) > square(SENTRY_SPLASH_RADIUS + enemy.radius)) continue
        dealWeaponDamage(enemy, damage, canCrit = true, sourceWeapon = WeaponId.SENTRY_GRID, procKey = WeaponRuntime.PROC_NEVER)
    }
    // A small ring shows the burst's reach.
    shockwave(struck.x, struck.y, 0.2f, SENTRY_SPLASH_RADIUS, projectile.colorIndex)
}

/** Quasar Cannon's third form: a shot that ends (spent or out of life) bursts into a sonic wave. */
internal fun MutableGameState.quasarShotEnded(projectile: Projectile) {
    if (weapon != WeaponId.QUASAR_CANNON || !hasMasteryForm(3) || projectile.hostile) return
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(projectile.x, projectile.y, enemy.x, enemy.y) > square(QUASAR_BURST_RADIUS + enemy.radius)) continue
        val landed = dealWeaponDamage(enemy, projectile.damage * QUASAR_BURST_SHARE, procKey = WeaponRuntime.PROC_NEVER).amount > 0f
        if (landed) applyWeaponStatus(enemy, StatusElement.SONIC, 1)
    }
    shockwave(projectile.x, projectile.y, 0.3f, QUASAR_BURST_RADIUS, 0)
}

/**
 * A Prism Relay shard bounced off an enemy: the third form bursts there, and the Tesseract signature
 * runs the bounce through the lattice (every enemy on a lattice edge is cut).
 */
internal fun MutableGameState.prismRelayBounced(projectile: Projectile) {
    if (weapon != WeaponId.PRISM_RELAY) return
    if (hasMasteryForm(3)) {
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive || projectile.hasRecordedEnemyHit(enemy.id)) continue
            if (distanceSquared(projectile.x, projectile.y, enemy.x, enemy.y) <= square(PRISM_BURST_RADIUS + enemy.radius)) {
                dealWeaponDamage(enemy, projectile.damage * PRISM_BURST_SHARE, procKey = WeaponRuntime.PROC_NEVER)
            }
        }
        shockwave(projectile.x, projectile.y, 0.2f, PRISM_BURST_RADIUS, 0)
    }
    val nodes = latticeNodeCount
    if (signatureActive() && nodes >= 2) {
        for (node in 0 until nodes) {
            val next = (node + 1) % nodes
            if (nodes == 2 && node == 1) break
            val x0 = latticeNodeX(node)
            val y0 = latticeNodeY(node)
            val x1 = latticeNodeX(next)
            val y1 = latticeNodeY(next)
            for (index in enemies.indices) {
                val enemy = enemies[index]
                if (enemy.alive && segmentCircleIntersects(x0, y0, x1, y1, enemy.x, enemy.y, enemy.radius + 8f)) {
                    dealWeaponDamage(enemy, projectile.damage * PRISM_LATTICE_SHARE, procKey = WeaponRuntime.PROC_NEVER)
                }
            }
            addWeaponArc(x0, y0, x1, y1, 0.12f)
        }
    }
}
