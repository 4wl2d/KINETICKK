// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.length
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Site-owned enemies: an enemy a site moves itself instead of its kind's behaviour. The Golden Drifter is an ordinary
 * Drifter that flees the Core, jinking from side to side and speeding up while it flies.
 */

/** Sideways share of the Golden Drifter's jink, its rate (rad/s) and its phase step per enemy id. */
private const val GOLDEN_JINK: Float = 0.45f
private const val GOLDEN_JINK_RATE: Float = 2.3f
private const val GOLDEN_JINK_PHASE: Float = 0.7f

/** How quickly the Golden Drifter turns onto its flight. */
private const val GOLDEN_AGILITY: Float = 3.5f

/**
 * Moves [enemy] when a site owns it and returns true, so its kind's behaviour (and the loop's own integration) is
 * skipped this step: the Golden Drifter steers and moves here.
 */
internal fun MutableGameState.updateSiteOwnedEnemy(enemy: Enemy, delta: Float): Boolean {
    if (enemy.id != goldenDrifterId) return false
    fleeGoldenDrifter(enemy, delta)
    return true
}

/**
 * The Golden Drifter flees straight away from the Core with a sideways jink, at the policy's base speed rising by its
 * ramp for every second it has flown, never past the maximum; then it moves at its motion scale.
 */
private fun MutableGameState.fleeGoldenDrifter(enemy: Enemy, delta: Float) {
    val policy = content.sites.secrets
    val awayX = enemy.x - coreX
    val awayY = enemy.y - coreY
    val distance = max(1f, length(awayX, awayY))
    val directionX = awayX / distance
    val directionY = awayY / distance
    val jink = GOLDEN_JINK * sin(elapsed * GOLDEN_JINK_RATE + enemy.id * GOLDEN_JINK_PHASE)
    val flown = max(0f, elapsed - (goldenDrifterUntil - policy.goldenLifetime))
    val fleeSpeed = min(policy.goldenMaxSpeed, policy.goldenBaseSpeed + policy.goldenSpeedRamp * flown)
    steerEnemy(
        enemy,
        (directionX - directionY * jink) * fleeSpeed,
        (directionY + directionX * jink) * fleeSpeed,
        GOLDEN_AGILITY,
        delta,
    )
    val motion = enemyMotionScale(enemy)
    enemy.x += enemy.vx * delta * motion
    enemy.y += enemy.vy * delta * motion
}
