// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/**
 * Baby mode rules (RunMode.EASY). Incoming hostile and obstacle damage is multiplied by [incomingDamageMultiplier]. A
 * singularity contact puts the Core [boopClearance] from the cursor, turns the inward speed into [boopRestitution] of it
 * outward, leaves at least [boopMinimumOutwardSpeed] outward and never more speed than before the contact (or that minimum).
 * An obstacle hit at or above the lethal speed deals [lethalObstacleDamageShare] of max integrity instead of breaking the
 * Core, and no obstacle damage takes integrity below [obstacleIntegrityFloor].
 */
data class EasyModeTuning(
    val incomingDamageMultiplier: Float = 0.5f,
    val boopClearance: Float = 44f,
    val boopRestitution: Float = 0.6f,
    val boopMinimumOutwardSpeed: Float = 280f,
    val lethalObstacleDamageShare: Float = 0.5f,
    val obstacleIntegrityFloor: Float = 1f,
) {
    init {
        require(incomingDamageMultiplier.isFinite() && incomingDamageMultiplier in 0.1f..1f)
        require(boopClearance.isFinite() && boopClearance in MIN_BOOP_CLEARANCE..MAX_BOOP_CLEARANCE)
        require(boopRestitution.isFinite() && boopRestitution in 0f..1f)
        require(boopMinimumOutwardSpeed.isFinite() && boopMinimumOutwardSpeed in 0f..900f)
        require(lethalObstacleDamageShare.isFinite() && lethalObstacleDamageShare in 0f..1f)
        require(obstacleIntegrityFloor.isFinite() && obstacleIntegrityFloor in 1f..10f)
    }

    companion object {
        /** Just outside the 22-unit kill radius, so the next swept test starts outside it. */
        const val MIN_BOOP_CLEARANCE: Float = 23f
        const val MAX_BOOP_CLEARANCE: Float = 120f
    }
}
