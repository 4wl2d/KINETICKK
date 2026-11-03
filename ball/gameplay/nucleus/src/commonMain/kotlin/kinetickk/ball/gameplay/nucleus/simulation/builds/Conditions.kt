// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*

// ---------------------------------------------------------------------------------------------
// Buffs and conditional modifiers.

internal fun MutableGameState.buffAmount(stat: BuffStat): Float {
    var total = 0f
    for (index in buffs.indices) {
        val buff = buffs[index]
        if (buff.stat == stat && buff.expiresAt > elapsed) total += buff.amount
    }
    return total
}

internal fun MutableGameState.whileAmount(stat: BuffStat, target: Enemy?, source: HitSource? = null): Float {
    val modifiers = build.whileModifiers
    if (modifiers.isEmpty()) return 0f
    var total = 0f
    for (index in modifiers.indices) {
        val modifier = modifiers[index]
        if (modifier.stat == stat && holds(modifier.condition, target, source, null)) total += modifier.amount
    }
    return total
}

/** Evaluates a clause or rule condition against current state. Pure apart from reading state. */
internal fun MutableGameState.holds(
    condition: EffectCondition,
    target: Enemy?,
    source: HitSource?,
    element: StatusElement?,
    reaction: ReactionId? = null,
    skill: kinetickk.ball.content.api.SkillId? = null,
    obstacle: kinetickk.ball.content.api.ObstacleKind? = null,
): Boolean = when (condition) {
    is EffectCondition.SpeedAtLeast -> speed >= condition.speed
    is EffectCondition.SpeedBelow -> speed < condition.speed
    is EffectCondition.IntegrityBelow -> hp < maxHp * condition.fraction
    is EffectCondition.HeatAtLeast -> heat >= condition.heat
    EffectCondition.ShieldUp -> shield > 0f
    EffectCondition.OverdriveActive -> overdriveTime > 0f
    EffectCondition.Braking -> braking
    is EffectCondition.ComboAtLeast -> combo >= condition.combo
    is EffectCondition.TargetHas -> target != null && when (val element = condition.element) {
        null -> totalStacks(target) >= condition.stacks
        else -> stacks(target, element) >= condition.stacks
    }
    EffectCondition.TargetElite -> target != null && target.isEliteClass
    EffectCondition.TargetFrozen -> target != null && (isFrozen(target) || target.id == shatteringEnemyId)
    is EffectCondition.TargetBelow -> target != null && target.hp < target.maxHp * condition.fraction
    EffectCondition.SourceImpact -> source == HitSource.IMPACT || source == HitSource.DASH
    EffectCondition.SourceWeapon -> source == HitSource.WEAPON || source == HitSource.ECHO
    // A reaction (fired, or behind a field or mark) involves both of its elements; a Break, skill or other field
    // names its own.
    is EffectCondition.ElementIs -> reaction?.involves(condition.element) ?: (element == condition.element)
    is EffectCondition.ReactionIs -> reaction == condition.reaction
    is EffectCondition.SkillIs -> skill == condition.skill
    is EffectCondition.SkillTagged -> skill != null && condition.tag in content.skill(skill).tags
    EffectCondition.CoreInField -> coreFieldMask != 0
    EffectCondition.TargetMarked -> target != null && targetMarked(target)
    EffectCondition.TargetControlled -> target != null &&
        (isFrozen(target) || target.disabledUntil > elapsed || target.silencedUntil > elapsed)
    EffectCondition.TargetThrown -> target != null && (target.thrownUntil > elapsed || isThrownByPolarity(target))
    EffectCondition.ApexActive -> apexActive()
    is EffectCondition.SkillsCharging -> skillsCharging() >= condition.count
    is EffectCondition.WeaponFamilyIs -> currentWeaponDefinition.family == condition.family
    // The obstacle kind the dispatch carries, and the Core's measured gap to the nearest solid obstacle (+∞: none).
    is EffectCondition.ObstacleIs -> obstacle == condition.kind
    is EffectCondition.NearObstacle -> coreObstacleGap <= condition.distance
}

/** A live mark, Blackfire or Embrittle on [target], or [target] is the Void Anchor. */
internal fun MutableGameState.targetMarked(target: Enemy): Boolean =
    markOf(target) != null || target.status[StatusCell.BLACKFIRE_UNTIL] > elapsed ||
        target.status[StatusCell.EMBRITTLED_UNTIL] > elapsed || voidAnchorId == target.id && voidAnchorUntil > elapsed

/** Owned skills still recharging. */
internal fun MutableGameState.skillsCharging(): Int {
    var count = 0
    for (slot in 0 until SkillRuntime.SLOTS) {
        if (!skillLoadout.isEmpty(slot) && skillRuntime[slot, SkillCell.READY_AT] > elapsed) count++
    }
    return count
}
