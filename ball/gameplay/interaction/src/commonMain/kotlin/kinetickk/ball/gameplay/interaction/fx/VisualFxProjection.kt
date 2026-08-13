// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.gameplay.nucleus.model.formatDamageNumber
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

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
) {
    fun formattedAmount(format: DamageNumberFormat): String = when (format) {
        DamageNumberFormat.COMPACT -> compactAmount
        DamageNumberFormat.FULL -> fullAmount
    }
}

data class WeaponArcProjection(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val life: Float,
)

/** Immutable Interaction-owned visual snapshot attached after the stamped Game read. */
data class VisualFxProjection(
    val particles: ImmutableList<ParticleProjection>,
    val motionEchoes: ImmutableList<MotionEchoProjection>,
    val shockwaves: ImmutableList<ShockwaveProjection>,
    val damageNumbers: ImmutableList<DamageNumberProjection>,
    val weaponArcs: ImmutableList<WeaponArcProjection>,
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
