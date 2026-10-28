// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Obstacle impact speeds and the solid-surface response: asteroids today, structure walls through the same entry.
 * The steady model the speeds scale lives in CoreSteering.kt.
 */

/** No surface: the Core has not hit anything that keeps a per-surface cooldown. */
internal const val NO_SURFACE_ID: Long = Long.MIN_VALUE

/**
 * The speed a collision counts at: its [normalSpeed] plus `scrapeShare` of the sliding [tangentialSpeed], so a fast
 * glance hurts, never above the Core's speed (the length of both parts).
 */
internal fun MutableGameState.obstacleImpactSpeed(normalSpeed: Float, tangentialSpeed: Float): Float =
    min(length(normalSpeed, tangentialSpeed), normalSpeed + content.obstacles.scrapeShare * tangentialSpeed)

/** Share of the way from tier 0 to the end of collision forgiveness. */
private fun MutableGameState.obstacleForgiveness(): Float =
    (rebirthLevel.toFloat() / content.obstacles.forgivingTiers).coerceIn(0f, 1f)

/** Impact speed at which an asteroid collision starts to hurt. */
internal fun MutableGameState.obstacleDamageStartSpeed(): Float {
    val tuning = content.obstacles
    return steadyCursorSpeed(tuning.damageStartReach) + tuning.forgivingStartBonus * (1f - obstacleForgiveness())
}

/**
 * Normal speed from which a hurting hit (or a bumper throw) counts as a ricochet: RICOCHET clauses, Ricochet Rig,
 * Gravity Surfer, the Dipole counter and the ambush pace. It lies at or above the damage start, so a ricochet always
 * hurts.
 */
internal fun MutableGameState.obstacleRicochetSpeed(): Float {
    val tuning = content.obstacles
    return steadyCursorSpeed(tuning.ricochetReach) + tuning.forgivingStartBonus * (1f - obstacleForgiveness())
}

/**
 * Summed lethal-speed bonus of the Core's state: Matrix nodes and the build's obstacle tuning (itself capped when the
 * build compiles). [obstacleLethalSpeed] holds the sum under the build's own ceiling.
 */
internal fun MutableGameState.obstacleLethalSpeedBonus(): Float = matrix.obstacleLethalSpeedBonus + build.obstacleLethalBonus

/**
 * Impact speed at which an asteroid collision is lethal. Bonuses raise it at most to this build's own straight
 * steady speed at [kinetickk.ball.content.api.ObstacleTuning.lethalCeilingReach], so holding the cursor at the
 * edge stays lethal for every Matrix state. A Dash adds its impulse on top of the cruise speed, so the lethal speed
 * never drops below the strongest Dash the character can make (an on-beat Pentagon Dash pushes harder) from a cruise
 * at [kinetickk.ball.content.api.ObstacleTuning.dashSafeReach] plus a margin: a Dash from a gentle cruise can hurt
 * but never kills. Only that floor may lie above the ceiling.
 */
internal fun MutableGameState.obstacleLethalSpeed(): Float {
    val tuning = content.obstacles
    val steadyLethal = steadyCursorSpeed(tuning.lethalReach)
    val ceiling = max(steadyLethal, buildSteadyCursorSpeed(tuning.lethalCeilingReach))
    val raised = min(steadyLethal + obstacleLethalSpeedBonus(), ceiling)
    val dashFloor = steadyCursorSpeed(tuning.dashSafeReach) +
        effectiveDashImpulse() * maxCharacterDashImpulseMultiplier() + tuning.dashLethalMargin
    return max(max(obstacleDamageStartSpeed() + 1f, raised), dashFloor)
}

/**
 * Share of maximum integrity a hit at [impact] costs before the Matrix guard and tier-0 forgiveness: the floor
 * `minimumDamageShare` at the damage start [start], growing with the band position to `damageShare` at [lethal].
 */
internal fun MutableGameState.obstacleBandShare(impact: Float, start: Float, lethal: Float): Float {
    val tuning = content.obstacles
    val band = ((impact - start) / (lethal - start)).coerceIn(0f, 1f)
    return tuning.minimumDamageShare + (tuning.damageShare - tuning.minimumDamageShare) * band.pow(tuning.damageExponent)
}

/**
 * Damage of an asteroid collision at [impact] inside the damage band: the band share of maximum integrity, Matrix
 * guard and tier-0 forgiveness applied.
 */
internal fun MutableGameState.obstacleBandDamage(impact: Float, start: Float, lethal: Float): Float {
    val tuning = content.obstacles
    val forgiving = tuning.forgivingDamageScale + (1f - tuning.forgivingDamageScale) * obstacleForgiveness()
    return maxHp * obstacleBandShare(impact, start, lethal) * matrix.obstacleDamageMultiplier * forgiving
}

/**
 * The Core meets a solid surface [surfaceId] along the outward normal ([nx], [ny]) at its rest point ([contactX],
 * [contactY]): it bounces (sliding below the slide speed) at the restitution plus the build's bonus (at most
 * [MAX_OBSTACLE_RESTITUTION]), and from the damage-start impact speed the hit hurts (band damage scaled by
 * [damageScale] and the build's obstacle damage scale) and costs part of the sliding speed, or kills. The cooldown
 * spaces hits on the same surface; another surface, or a lethal hit, lands at once. Only a hit whose normal speed
 * reaches the ricochet speed counts and pays out as a ricochet. HURT and RICOCHET carry the [obstacle] kind struck
 * (none for a structure wall).
 */
internal fun MutableGameState.resolveSolidImpact(
    surfaceId: Long,
    nx: Float,
    ny: Float,
    contactX: Float,
    contactY: Float,
    damageScale: Float = 1f,
    obstacle: ObstacleKind? = null,
) {
    val tuning = content.obstacles
    val normalSpeed = -(velocityX * nx + velocityY * ny)
    if (normalSpeed <= 0f) return
    val tangentX = velocityX + normalSpeed * nx
    val tangentY = velocityY + normalSpeed * ny
    val impact = obstacleImpactSpeed(normalSpeed, length(tangentX, tangentY))
    val restitution = ((matrix.ricochetRig?.restitution ?: tuning.restitution) + build.obstacleRestitutionBonus)
        .coerceIn(0f, MAX_OBSTACLE_RESTITUTION)
    val elasticity = if (normalSpeed < tuning.slideSpeed) 0f else restitution
    velocityX += (1f + elasticity) * normalSpeed * nx
    velocityY += (1f + elasticity) * normalSpeed * ny
    val start = obstacleDamageStartSpeed()
    if (impact < start) return
    val lethal = obstacleLethalSpeed()
    if (impact < lethal && surfaceId == obstacleHitSurfaceId && obstacleHitReadyAt > elapsed) return
    obstacleHitSurfaceId = surfaceId
    obstacleHitReadyAt = elapsed + tuning.hitCooldownSeconds
    val surfaceX = contactX - nx * coreRadius
    val surfaceY = contactY - ny * coreRadius
    screenShake = max(screenShake, min(16f, 6f + impact * 0.004f))
    shockwave(surfaceX, surfaceY, 0.3f, 120f, 4)
    directionalBurst(surfaceX, surfaceY, 10, 4, nx, ny)
    emitSound(GameplayAudioCue.IMPACT)
    val ricochet = normalSpeed >= obstacleRicochetSpeed()
    if (ricochet) pendingRicochets++
    if (impact >= lethal) {
        takeLethalObstacleHit(obstacle)
    } else {
        // Severity runs from the floor's share of the lethal share (0.11 shipped) to 1 at the lethal end of the band.
        val lost = tuning.scrapeFriction * obstacleBandShare(impact, start, lethal) / tuning.damageShare
        velocityX -= tangentX * lost
        velocityY -= tangentY * lost
        takeDamage(
            obstacleBandDamage(impact, start, lethal) * damageScale * build.obstacleDamageScale,
            kind = IncomingDamage.OBSTACLE,
            obstacle = obstacle,
        )
    }
    if (phase != GamePhase.RUNNING) return
    if (ricochet) onRicochet(surfaceX, surfaceY, normalSpeed, obstacle)
}

/** The most a bounce off a solid surface keeps of the normal speed, whatever the build adds to the restitution. */
internal const val MAX_OBSTACLE_RESTITUTION: Float = 0.95f
