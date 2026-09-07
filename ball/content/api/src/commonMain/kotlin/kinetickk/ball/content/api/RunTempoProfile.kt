// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/** Content-owned starting values; seconds always mean active simulation time at 1x. */
data class FatigueTuning(
    val velocitySmoothingSeconds: Float = 0.15f,
    val turnDegrees: Float = 40f,
    val minimumTurnSpeed: Float = 200f,
    val turnHoldSeconds: Float = 0.20f,
    val turnRecovery: Float = 0.40f,
    val turnCooldownSeconds: Float = 0.75f,
    val maximumBrakeRecoverySpeed: Float = 250f,
    val brakeRecoveryPerSecond: Float = 0.80f,
    val normalRecoveryPerSecond: Float = 0.30f,
    /** Normalized center-to-edge reach; 0.8 leaves the outer 10% of each screen axis strained. */
    val edgeStrainStart: Float = 0.80f,
    val edgeDrainPerSecond: Float = 0.40f,
) {
    init {
        require(velocitySmoothingSeconds.isFinite() && velocitySmoothingSeconds > 0f)
        require(turnDegrees.isFinite() && turnDegrees in 1f..179f)
        require(minimumTurnSpeed.isFinite() && minimumTurnSpeed > 0f)
        require(turnHoldSeconds.isFinite() && turnHoldSeconds > 0f)
        require(turnRecovery.isFinite() && turnRecovery in 0f..1f)
        require(turnCooldownSeconds.isFinite() && turnCooldownSeconds >= turnHoldSeconds)
        require(maximumBrakeRecoverySpeed.isFinite() && maximumBrakeRecoverySpeed > 0f)
        require(brakeRecoveryPerSecond.isFinite() && brakeRecoveryPerSecond > 0f)
        require(normalRecoveryPerSecond.isFinite() && normalRecoveryPerSecond > 0f)
        require(edgeStrainStart.isFinite() && edgeStrainStart > 0f && edgeStrainStart < 1f)
        require(edgeDrainPerSecond.isFinite() && edgeDrainPerSecond > 0f)
    }
}

/** One immutable source for encounter pressure, build growth and the displayed boss clock. */
data class RunTempoProfile(
    val bossAtSeconds: Float = 12f * 60f,
    val firstEliteAtSeconds: Float = 38f,
    val dataPickupMultiplier: Float = 1.60f,
    val matterRewardMultiplier: Float = 1.40f,
    val weaponAmplificationEveryLevels: Int = 2,
    val maximumAutomaticWeaponLevel: Int = 10,
    val fatigue: FatigueTuning = FatigueTuning(),
) {
    init {
        require(bossAtSeconds.isFinite() && bossAtSeconds > 0f)
        require(firstEliteAtSeconds.isFinite() && firstEliteAtSeconds in 0f..bossAtSeconds)
        require(dataPickupMultiplier.isFinite() && dataPickupMultiplier > 0f)
        require(matterRewardMultiplier.isFinite() && matterRewardMultiplier > 0f)
        require(weaponAmplificationEveryLevels > 0)
        require(maximumAutomaticWeaponLevel in 1..100)
    }

    fun progress(elapsedSeconds: Float): Float = (elapsedSeconds / bossAtSeconds).coerceIn(0f, 1f)

    // Fractions preserve encounter/build milestones when tuning the boss's arrival.
    fun encounterStage(elapsedSeconds: Float): Int = when {
        progress(elapsedSeconds) < 38f / 720f -> 0
        progress(elapsedSeconds) < 90f / 720f -> 1
        progress(elapsedSeconds) < 150f / 720f -> 2
        progress(elapsedSeconds) < 240f / 720f -> 3
        progress(elapsedSeconds) < 360f / 720f -> 4
        else -> 5
    }

    fun ordinaryEnemyCap(elapsedSeconds: Float): Int = (14 + (progress(elapsedSeconds) * 60f).toInt()).coerceAtMost(90)
    fun spawnIntervalSeconds(elapsedSeconds: Float): Float = (0.84f - progress(elapsedSeconds) * 0.64f).coerceAtLeast(0.20f)
    fun eliteIntervalSeconds(elapsedSeconds: Float): Float = (68f - progress(elapsedSeconds) * 20f).coerceAtLeast(48f)
    fun enemyHealthMultiplier(elapsedSeconds: Float): Float = 1f + progress(elapsedSeconds) * 2.55f
    fun attackRateMultiplier(elapsedSeconds: Float): Float = 1f + progress(elapsedSeconds) * 0.40f
    fun denseAttacks(elapsedSeconds: Float): Boolean = progress(elapsedSeconds) >= 0.58f
    fun dataRequiredForLevel(level: Int): Int = if (level <= 1) 18 else 18 + level * 8 + level * level / 8
    fun grantsWeaponAmplification(level: Int): Boolean = level > 1 && level % weaponAmplificationEveryLevels == 0
}
