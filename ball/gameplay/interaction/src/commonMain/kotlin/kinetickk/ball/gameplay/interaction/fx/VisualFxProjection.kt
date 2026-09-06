// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.gameplay.nucleus.model.formatDamageNumber
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage

data class ParticleProjection(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float,
    val colorIndex: Int,
    val size: Float,
)

data class MotionEchoProjection(
    val x: Float,
    val y: Float,
    val life: Float,
    val maxLife: Float,
    val intensity: Float,
)

data class ShockwaveProjection(
    val x: Float,
    val y: Float,
    val life: Float,
    val maxLife: Float,
    val maxRadius: Float,
    val colorIndex: Int,
)

data class DamageNumberProjection(
    val x: Float,
    val y: Float,
    val amount: Long,
    val critical: Boolean,
    val life: Float,
    val compactAmount: String = formatDamageNumber(amount, DamageNumberFormat.COMPACT),
    val fullAmount: String = formatDamageNumber(amount, DamageNumberFormat.FULL),
    val russianCompactAmount: String = compactAmount.russianDamageNumber(),
) {
    fun formattedAmount(format: DamageNumberFormat, language: AppLanguage = AppLanguage.English): String = when (format) {
        DamageNumberFormat.COMPACT -> when (language) {
            AppLanguage.English -> compactAmount
            AppLanguage.Russian -> russianCompactAmount
        }
        DamageNumberFormat.FULL -> fullAmount
    }
}

/** Translate the already rounded number; general UI formatting truncates and has different bounds. */
internal fun String.russianDamageNumber(): String {
    val suffix = when {
        endsWith("Qa") -> "Qa" to " квадрлн"
        endsWith("Qi") -> "Qi" to " квинтлн"
        endsWith("K") -> "K" to " тыс."
        endsWith("M") -> "M" to " млн"
        endsWith("B") -> "B" to " млрд"
        endsWith("T") -> "T" to " трлн"
        else -> return this
    }
    return removeSuffix(suffix.first).replace('.', ',') + suffix.second
}

data class WeaponArcProjection(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val life: Float,
)

data class BuildNotificationProjection(val title: String, val details: ImmutableList<String>, val life: Float)

/** Immutable Interaction-owned visual snapshot attached after the stamped Game read. */
data class VisualFxProjection(
    val particles: ImmutableList<ParticleProjection>,
    val motionEchoes: ImmutableList<MotionEchoProjection>,
    val shockwaves: ImmutableList<ShockwaveProjection>,
    val damageNumbers: ImmutableList<DamageNumberProjection>,
    val weaponArcs: ImmutableList<WeaponArcProjection>,
    val buildNotifications: ImmutableList<BuildNotificationProjection> = immutableListOf(),
) {
    companion object {
        val EMPTY = VisualFxProjection(
            particles = immutableListOf(),
            motionEchoes = immutableListOf(),
            shockwaves = immutableListOf(),
            damageNumbers = immutableListOf(),
            weaponArcs = immutableListOf(),
        )
    }
}
