// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.gameplay.nucleus.render.formatDamageNumber
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.foundation.collections.ImmutableList

/*
 * The live entries of [InteractionFxReducer]: mutable presentation memory it advances in place each step and copies
 * into the published [VisualFxProjection] lists. Only the reducer creates and changes them.
 */

/**
 * A live build toast, published as a [BuildNotificationProjection]. Its title, kind and lifetime never
 * change; [life] and [age] advance in place each step, and a merge replaces [details] (an immutable
 * list) and restarts [life].
 */
internal class BuildNotice(val title: String, var details: ImmutableList<String>, var life: Float, val kind: BuildNoticeKind) {
    val maxLife: Float = kind.lifeSeconds
    var age: Float = maxLife - life
}

internal data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val colorIndex: Int,
    val size: Float,
)

internal data class MotionEcho(
    val x: Float,
    val y: Float,
    var life: Float,
    val maxLife: Float,
    val intensity: Float,
)

internal data class Shockwave(
    val x: Float,
    val y: Float,
    var life: Float,
    val maxLife: Float,
    val maxRadius: Float,
    val colorIndex: Int,
)

internal data class DamageNumber(
    var x: Float,
    var y: Float,
    val amount: Long,
    val critical: Boolean,
    var life: Float = InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS,
    val compactAmount: String = formatDamageNumber(amount, DamageNumberFormat.COMPACT),
    val fullAmount: String = formatDamageNumber(amount, DamageNumberFormat.FULL),
    val russianCompactAmount: String = compactAmount.russianDamageNumber(),
    val driftX: Float = 0f,
    val driftY: Float = -1f,
    val tone: Int = 0,
)

internal data class ElementBurst(
    var x: Float,
    var y: Float,
    val primary: Int,
    val secondary: Int,
    val reaction: Int,
    var radius: Float,
    var life: Float,
    val maxLife: Float,
    var weight: Int,
    var direction: Float,
    var flags: Int,
    val seed: Int,
)

internal data class SkillBurst(
    val skill: Int,
    val phase: Int,
    var x: Float,
    var y: Float,
    val dirX: Float,
    val dirY: Float,
    val radius: Float,
    val rank: Int,
    val flags: Int,
    val seed: Int,
    var life: Float,
    val maxLife: Float,
)

internal data class ApexBurst(
    val shape: Int,
    var x: Float,
    var y: Float,
    val seed: Int,
    var life: Float,
    val maxLife: Float,
)

internal data class ReactionStamp(
    var x: Float,
    var y: Float,
    val reaction: Int,
    var life: Float,
    val maxLife: Float,
)

internal data class WeaponArc(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    var life: Float,
    val style: Int,
    val maxLife: Float,
)
