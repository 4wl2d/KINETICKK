// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Obstacle impact speeds and the solid-surface response: asteroids today, structure walls through the same entry.
 * The steady model the speeds scale lives in CoreSteering.kt.
 */

/** No surface: the Core has not hit anything that keeps a per-surface cooldown. */
internal const val NO_SURFACE_ID: Long = Long.MIN_VALUE

/** Share of the way from tier 0 to the end of collision forgiveness. */
private fun MutableGameState.obstacleForgiveness(): Float =
    (rebirthLevel.toFloat() / content.obstacles.forgivingTiers).coerceIn(0f, 1f)

/** Normal speed at which an asteroid collision starts to hurt. */
internal fun MutableGameState.obstacleDamageStartSpeed(): Float {
    val tuning = content.obstacles
    return steadyCursorSpeed(tuning.damageStartReach) + tuning.forgivingStartBonus * (1f - obstacleForgiveness())
}

/** Normal speed from which a hard contact counts as a ricochet (and ambush pacing measures speed). */
internal fun MutableGameState.obstacleRicochetSpeed(): Float = obstacleDamageStartSpeed()

/** Lethal-speed bonus of the Core's state (Matrix nodes today). */
internal fun MutableGameState.obstacleLethalSpeedBonus(): Float = matrix.obstacleLethalSpeedBonus

/**
 * Normal speed at which an asteroid collision is lethal (bonuses included). A Dash adds its impulse
 * on top of the cruise speed, so the lethal speed never drops below the strongest Dash the character can
 * make (an on-beat Pentagon Dash pushes harder) from a cruise at
 * [kinetickk.ball.content.api.ObstacleTuning.dashSafeReach] plus a margin: a Dash from a gentle cruise can
 * hurt but never kills.
 */
internal fun MutableGameState.obstacleLethalSpeed(): Float {
    val tuning = content.obstacles
    return max(
        max(obstacleDamageStartSpeed() + 1f, steadyCursorSpeed(tuning.lethalReach) + obstacleLethalSpeedBonus()),
        steadyCursorSpeed(tuning.dashSafeReach) + effectiveDashImpulse() * maxCharacterDashImpulseMultiplier() +
            tuning.dashLethalMargin,
    )
}

/**
 * Damage of an asteroid collision at normal speed [normalSpeed] inside the damage band: a share of
 * maximum integrity growing with the band position, Matrix guard and tier-0 forgiveness applied.
 */
internal fun MutableGameState.obstacleBandDamage(normalSpeed: Float, start: Float, lethal: Float): Float {
    val tuning = content.obstacles
    val band = ((normalSpeed - start) / (lethal - start)).coerceIn(0f, 1f)
    val forgiving = tuning.forgivingDamageScale + (1f - tuning.forgivingDamageScale) * obstacleForgiveness()
    return maxHp * tuning.damageShare * band.pow(tuning.damageExponent) * matrix.obstacleDamageMultiplier * forgiving
}

/**
 * The Core meets a solid surface [surfaceId] along the outward normal ([nx], [ny]) at ([contactX], [contactY]):
 * it bounces (sliding below the slide speed), and above the damage-start speed the hit hurts (band damage scaled by
 * [damageScale]) or kills, fires a ricochet and starts the collision cooldown. A lethal hit lands even right after a
 * band hit.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.resolveSolidImpact(
    surfaceId: Long,
    nx: Float,
    ny: Float,
    contactX: Float,
    contactY: Float,
    damageScale: Float = 1f,
) {
    val tuning = content.obstacles
    val normalSpeed = -(velocityX * nx + velocityY * ny)
    if (normalSpeed <= 0f) return
    val start = obstacleDamageStartSpeed()
    val restitution = (matrix.ricochetRig?.restitution ?: tuning.restitution).coerceIn(0f, 1f)
    val elasticity = if (normalSpeed < tuning.slideSpeed) 0f else restitution
    velocityX += (1f + elasticity) * normalSpeed * nx
    velocityY += (1f + elasticity) * normalSpeed * ny
    if (normalSpeed < start) return
    val lethal = obstacleLethalSpeed()
    // The cooldown spaces band damage; a lethal hit lands even right after a band hit.
    if (normalSpeed < lethal && obstacleHitReadyAt > elapsed) return
    obstacleHitReadyAt = elapsed + tuning.hitCooldownSeconds
    screenShake = max(screenShake, min(16f, 6f + normalSpeed * 0.004f))
    shockwave(contactX - nx * coreRadius, contactY - ny * coreRadius, 0.3f, 120f, 4)
    directionalBurst(contactX - nx * coreRadius, contactY - ny * coreRadius, 10, 4, nx, ny)
    emitSound(GameplayAudioCue.IMPACT)
    pendingRicochets++
    if (normalSpeed >= lethal) {
        takeLethalObstacleHit()
    } else {
        takeDamage(obstacleBandDamage(normalSpeed, start, lethal) * damageScale, kind = IncomingDamage.OBSTACLE)
    }
    if (phase != GamePhase.RUNNING) return
    onRicochet(contactX - nx * coreRadius, contactY - ny * coreRadius, normalSpeed)
}
