// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.gameplay.nucleus.render.formatDamageNumber
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.mapToImmutableList

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

/** Drops the oldest entries of [values] until at most [maximum] remain. */
internal fun <Element> trimFront(values: MutableList<Element>, maximum: Int) {
    while (values.size > maximum) values.removeAt(0)
}

/** Visits each value once, allowing its update and stable compaction in the same pass. */
internal inline fun <Element> retainInPlace(
    values: MutableList<Element>,
    isAlive: (Element) -> Boolean,
) {
    var retainedCount = 0
    for (index in values.indices) {
        val value = values[index]
        if (isAlive(value)) {
            if (retainedCount != index) values[retainedCount] = value
            retainedCount++
        }
    }
    var index = values.lastIndex
    while (index >= retainedCount) {
        values.removeAt(index)
        index--
    }
}

// The published copies of the live entries ([InteractionFxReducer.snapshot]).

internal fun List<BuildNotice>.toNotificationProjections(): ImmutableList<BuildNotificationProjection> = mapToImmutableList { value ->
    BuildNotificationProjection(value.title, value.details, value.life, value.kind, value.maxLife, value.age)
}

internal fun List<Particle>.toParticleProjections(): ImmutableList<ParticleProjection> = mapToImmutableList { value ->
    ParticleProjection(
        value.x,
        value.y,
        value.vx,
        value.vy,
        value.life,
        value.maxLife,
        value.colorIndex,
        value.size,
    )
}

internal fun List<MotionEcho>.toMotionEchoProjections(): ImmutableList<MotionEchoProjection> = mapToImmutableList { value ->
    MotionEchoProjection(value.x, value.y, value.life, value.maxLife, value.intensity)
}

internal fun List<Shockwave>.toShockwaveProjections(): ImmutableList<ShockwaveProjection> = mapToImmutableList { value ->
    ShockwaveProjection(
        value.x,
        value.y,
        value.life,
        value.maxLife,
        value.maxRadius,
        value.colorIndex,
    )
}

internal fun List<DamageNumber>.toDamageNumberProjections(): ImmutableList<DamageNumberProjection> = mapToImmutableList { value ->
    DamageNumberProjection(
        x = value.x,
        y = value.y,
        amount = value.amount,
        critical = value.critical,
        life = value.life,
        compactAmount = value.compactAmount,
        fullAmount = value.fullAmount,
        russianCompactAmount = value.russianCompactAmount,
        driftX = value.driftX,
        driftY = value.driftY,
        tone = value.tone,
    )
}

internal fun List<WeaponArc>.toWeaponArcProjections(): ImmutableList<WeaponArcProjection> = mapToImmutableList { value ->
    WeaponArcProjection(value.fromX, value.fromY, value.toX, value.toY, value.life, value.style, value.maxLife)
}

internal fun List<ElementBurst>.toElementBurstProjections(): ImmutableList<ElementBurstProjection> = mapToImmutableList { value ->
    ElementBurstProjection(value.x, value.y, value.primary, value.secondary, value.reaction, value.radius, value.life,
        value.maxLife, value.weight, value.direction, value.flags, value.seed)
}

internal fun List<SkillBurst>.toSkillBurstProjections(): ImmutableList<SkillBurstProjection> = mapToImmutableList { value ->
    SkillBurstProjection(value.skill, value.phase, value.x, value.y, value.dirX, value.dirY, value.radius, value.rank,
        value.flags, value.seed, value.life, value.maxLife)
}

internal fun List<ApexBurst>.toApexBurstProjections(): ImmutableList<ApexBurstProjection> = mapToImmutableList { value ->
    ApexBurstProjection(value.shape, value.x, value.y, value.seed, value.life, value.maxLife)
}

internal fun List<ReactionStamp>.toReactionStampProjections(): ImmutableList<ReactionStampProjection> = mapToImmutableList { value ->
    ReactionStampProjection(value.x, value.y, value.reaction, value.life, value.maxLife)
}
