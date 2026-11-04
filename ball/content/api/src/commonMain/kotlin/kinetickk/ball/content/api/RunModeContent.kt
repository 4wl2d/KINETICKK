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

/**
 * Core Freeze (Core Stasis, anti-AFK, every run mode and layer). The Core's filtered speed is an exponential average of
 * its scalar speed (time constant [speedFilterSeconds], the HUD's u/s). Below [freezeSpeed] the freeze grows by
 * `dt / freezeSeconds`; at or above [thawSpeed] it thaws by `dt / thawSeconds`; between them it holds, so hovering at
 * the threshold or jiggling under it never helps. A full freeze ends the run at once ([CoreStasisNotices.FROZEN]),
 * past shields, invulnerability and revivals; Easy mode shatters the ice instead and starts over. The freeze holds
 * (neither grows nor thaws) while control is unavailable: [runStartHoldSeconds] after a run starts,
 * [choiceHoldSeconds] after a choice closes, [layerSwitchHoldSeconds] after the world layer changes, and while the
 * host takes no input. [enabled] = false turns the system off (no field moves; harnesses that script slow movement);
 * [applyEffects] = false measures without any effect (probes). Seconds are simulation seconds.
 */
data class CoreStasisTuning(
    val enabled: Boolean = true,
    val applyEffects: Boolean = true,
    val freezeSpeed: Float = 100f,
    val thawSpeed: Float = 125f,
    val speedFilterSeconds: Float = 0.30f,
    val freezeSeconds: Float = 4.2f,
    val thawSeconds: Float = 3f,
    val runStartHoldSeconds: Float = 1.5f,
    val choiceHoldSeconds: Float = 0.6f,
    val layerSwitchHoldSeconds: Float = 1f,
) {
    init {
        require(freezeSpeed.isFinite() && freezeSpeed in 20f..1_000f)
        require(thawSpeed.isFinite() && thawSpeed >= freezeSpeed && thawSpeed <= 1_500f)
        require(speedFilterSeconds.isFinite() && speedFilterSeconds in 0.02f..5f)
        require(freezeSeconds.isFinite() && freezeSeconds in 0.5f..60f)
        require(thawSeconds.isFinite() && thawSeconds in 0.5f..60f)
        require(runStartHoldSeconds.isFinite() && runStartHoldSeconds in 0f..10f)
        require(choiceHoldSeconds.isFinite() && choiceHoldSeconds in 0f..10f)
        require(layerSwitchHoldSeconds.isFinite() && layerSwitchHoldSeconds in 0f..10f)
    }

    companion object {
        /** The system off: no state moves, every run is identical to a tree without it. */
        val DISABLED: CoreStasisTuning = CoreStasisTuning(enabled = false)

        /** Shipped thresholds without any effect: the freeze runs for probes and diagnostics only. */
        val MEASURE_ONLY: CoreStasisTuning = CoreStasisTuning(applyEffects = false)
    }
}

/** Every message string Core Freeze writes; the content localization test iterates [all]. */
object CoreStasisNotices {
    /** Run-end reason of a Core that froze solid. */
    const val FROZEN: String = "CORE FROZEN"
    val all: List<String> = listOf(FROZEN)
}
