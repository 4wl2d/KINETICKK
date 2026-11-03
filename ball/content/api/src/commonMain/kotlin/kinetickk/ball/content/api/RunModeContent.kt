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
 * Core Stasis (anti-AFK, every run mode): a Core within [anchorRadius] of its leash anchor whose speed EMA (time
 * constant [speedEmaSeconds]) stays below [stillSpeed] gathers still seconds; leaving the radius drags the anchor
 * along, and a drag at [travelSpeed] or faster counts as travel. The still clock falls at [decayRate] times its growth
 * while the Core is not still and is capped at [fullStasisSeconds]. Past [graceSeconds] the stasis level ramps to 1
 * over [rampSeconds], slowing weapon activation by up to [maxWeaponSlow]; after [fractureDelaySeconds] at full stasis
 * the Core loses [fractureShare] of max integrity every [fractureTickSeconds] (never in Easy mode). [enabled] = false
 * turns the system off (no field moves); [applyEffects] = false measures without any effect (probes). Seconds are
 * active simulation seconds.
 */
data class CoreStasisTuning(
    val enabled: Boolean = true,
    val applyEffects: Boolean = true,
    val anchorRadius: Float = 240f,
    val stillSpeed: Float = 120f,
    /** A leash drag counts as travel only at this speed EMA or faster; a slower crawl stays still. */
    val travelSpeed: Float = 30f,
    val speedEmaSeconds: Float = 1.5f,
    val decayRate: Float = 4f,
    val graceSeconds: Float = 10f,
    val rampSeconds: Float = 12f,
    val maxWeaponSlow: Float = 0.65f,
    val fractureDelaySeconds: Float = 6f,
    val fractureShare: Float = 0.05f,
    val fractureTickSeconds: Float = 1.5f,
) {
    init {
        require(anchorRadius.isFinite() && anchorRadius in 60f..1_200f)
        require(stillSpeed.isFinite() && stillSpeed in 20f..1_000f)
        require(travelSpeed.isFinite() && travelSpeed >= 0f && travelSpeed < stillSpeed)
        require(speedEmaSeconds.isFinite() && speedEmaSeconds in 0.1f..10f)
        require(decayRate.isFinite() && decayRate in 1f..20f)
        require(graceSeconds.isFinite() && graceSeconds in 1f..120f)
        require(rampSeconds.isFinite() && rampSeconds in 0.5f..120f)
        require(maxWeaponSlow.isFinite() && maxWeaponSlow in 0f..0.9f)
        require(fractureDelaySeconds.isFinite() && fractureDelaySeconds in 0f..60f)
        require(fractureShare.isFinite() && fractureShare in 0f..0.5f)
        require(fractureTickSeconds.isFinite() && fractureTickSeconds in 0.1f..10f)
    }

    /** Seconds of stillness at which the stasis level reaches 1 (the still clock's cap). */
    val fullStasisSeconds: Float get() = graceSeconds + rampSeconds

    companion object {
        /** The system off: no state moves, every run is identical to a tree without it. */
        val DISABLED: CoreStasisTuning = CoreStasisTuning(enabled = false)

        /** Shipped thresholds without any effect: the still clock runs for probes and diagnostics only. */
        val MEASURE_ONLY: CoreStasisTuning = CoreStasisTuning(applyEffects = false)
    }
}

/** Every message string Core Stasis writes; the content localization test iterates [all]. */
object CoreStasisNotices {
    /** Run-end reason of a Core that fractured while frozen. */
    const val FROZEN: String = "CORE FROZEN"
    val all: List<String> = listOf(FROZEN)
}
