// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.min
import kotlin.math.sqrt

internal fun MutableGameState.updatePolarity(ability: CharacterRuntime, delta: Float) {
    val attract = ability.polarity == Polarity.ATTRACT
    val radius = if (attract) attractRadius() else repelRadius()
    val acceleration = (if (attract) -ATTRACT_ACCELERATION else REPEL_ACCELERATION) * delta
    // Magnetar widens the field and throws harder, on ordinary enemies only.
    val magnetar = apexActive()
    val wideRadius = radius * magnetarReachScale()
    val wideAcceleration = if (attract || !magnetar) acceleration else acceleration * apexValue(MAGNETAR_THROW_SCALE)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val amplified = magnetar && !enemy.isBossClass
        val dx = enemy.x - coreX
        val dy = enemy.y - coreY
        val distance = length(dx, dy)
        if (distance < 1f || distance > (if (amplified) wideRadius else radius) + enemy.radius) continue
        val knockback = knockbackTaken(enemy)
        if (knockback <= 0f) continue
        val push = if (amplified) wideAcceleration else acceleration
        enemy.vx += dx / distance * push * knockback
        enemy.vy += dy / distance * push * knockback
        // ATTRACT only adds its pull to the enemy's own steering. REPEL throws what it pushes: the
        // enemy flies on its momentum (no steering) until THROW_SECONDS after the push carries it out
        // of the field, and what it strikes meanwhile is a slam. Bosses and mini-bosses are never thrown.
        if (!attract && !enemy.isBossClass) enemy.characterMarkTime = THROW_SECONDS
    }
    ability.charge = if (elapsed >= ability.polarityFlipReadyAt) 1f else
        1f - ((ability.polarityFlipReadyAt - elapsed) / POLARITY_FLIP_INTERVAL).coerceIn(0f, 1f)
    // Thrown enemies meeting obstacles slam in the obstacle pass (resolveEnemyObstacles), at their
    // normal speed and through the same applySlamDamage, so each contact is found once. Here they
    // slam into each other.
    // Bounded like the obstacle pass: at most MAX_POLARITY_THROWN_CHECKS_PER_STEP thrown enemies look for a
    // partner each step, and a pair is rejected on its squared distance before any root.
    var slams = 0
    var thrownVisited = 0
    for (index in enemies.indices) {
        if (slams >= MAX_SLAMS_PER_STEP || thrownVisited >= MAX_POLARITY_THROWN_CHECKS_PER_STEP) break
        val thrown = enemies[index]
        // A phased Phantom is intangible: nothing slams into it and it slams into nothing.
        if (!thrown.alive || thrown.characterMarkTime <= 0f || isPhasedPhantom(thrown)) continue
        thrownVisited++
        for (otherIndex in enemies.indices) {
            if (otherIndex == index) continue
            val other = enemies[otherIndex]
            if (!other.alive || isPhasedPhantom(other)) continue
            val dx = other.x - thrown.x
            val dy = other.y - thrown.y
            val reach = thrown.radius + other.radius
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared < 0.25f || distanceSquared > reach * reach) continue
            val distance = sqrt(distanceSquared)
            val closing = ((thrown.vx - other.vx) * dx + (thrown.vy - other.vy) * dy) / distance
            if (closing < SLAM_MIN_SPEED) continue
            thrown.characterMarkTime = 0f
            // The pair shares the closing momentum, so one contact slams once. An enemy struck hard
            // enough flies on as a throw of its own and may slam the next one (never a boss).
            val share = closing * 0.5f
            thrown.vx -= dx / distance * share
            thrown.vy -= dy / distance * share
            other.vx += dx / distance * share
            other.vy += dy / distance * share
            if (share >= THROW_MIN_SPEED && !other.isBossClass) other.characterMarkTime = THROW_SECONDS
            applySlamDamage(thrown, closing, polarity = true)
            applySlamDamage(other, closing, polarity = true)
            slams++
            break
        }
    }
}

/**
 * The Dipole's slam damage on [enemy] at [speed], dealt as ability damage; returns the amount the SLAM
 * trigger sees. [applySlamDamage] calls it for polarity slams, so a slam is one damage instance.
 */
internal fun MutableGameState.polaritySlam(enemy: Enemy, speed: Float): Float {
    val damage = (SLAM_BASE_DAMAGE + SLAM_DAMAGE_PER_SPEED * speed) * abilityPower()
    dealCharacterDamage(enemy, damage)
    return damage
}

/** A landed obstacle slam ends a REPEL throw, so the enemy steers again. */
internal fun MutableGameState.endPolarityThrow(enemy: Enemy) {
    if (isThrownByPolarity(enemy)) enemy.characterMarkTime = 0f
}

/** Records an on-beat action: the streak rises once per beat. */
internal fun MutableGameState.onBeatAction() {
    val ability = characterRuntime
    val beat = currentBeatIndex()
    if (ability.streakBeat == beat) return
    ability.streakBeat = beat
    ability.beatStreak = min(CharacterRuntime.MAX_BEAT_STREAK, ability.beatStreak + 1)
}
