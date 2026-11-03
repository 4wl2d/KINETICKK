// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.actsOnSkills
import kinetickk.ball.gameplay.nucleus.simulation.*

/** Characters lean toward an archetype without locking the build. */
internal fun CoreShape.characterTag(): BuildTag = when (this) {
    CoreShape.ORB, CoreShape.TESSERACT -> BuildTag.DRIVE
    CoreShape.PRISM, CoreShape.DIAMOND -> BuildTag.ANCHOR
    CoreShape.SHARD, CoreShape.CRESCENT -> BuildTag.PRECISION
    CoreShape.RING, CoreShape.SPIRAL -> BuildTag.BULWARK
    CoreShape.STAR -> BuildTag.RAM
    CoreShape.DIPOLE -> BuildTag.CHAIN
    CoreShape.PENTAGON -> BuildTag.OVERCLOCK
    CoreShape.HEXAGON -> BuildTag.ARSENAL
}

internal fun MutableGameState.tagTier(tag: BuildTag): Int =
    content.build.policy.tier(build.tagPoints(tag), content.build.policy.resonanceThresholds)

internal fun MutableGameState.affinityTier(element: StatusElement): Int =
    content.build.policy.tier(build.affinity(element), content.build.policy.affinityThresholds)

/** A relic bind or rank-three upgrade in [MutableGameState.acquisitionOrder]; item ids stay non-negative. */
internal fun relicGain(id: RelicId): Int = -(1 + id.ordinal)

/** Records [id]'s affinity gain as the newest one; each relic appears at most once. */
internal fun MutableGameState.recordRelicGain(id: RelicId) {
    val entry = relicGain(id)
    acquisitionOrder = acquisitionOrder.filter { it != entry } + entry
}

/** Recompiles after an item, relic or severance change. */
internal fun MutableGameState.recompileBuild() {
    build = compileBuild()
    reconcileIntegrityLiability()
}

/** The build the run's items, Relics, character, Matrix and weapon compile to, with [keystoneMask] forms on top. */
internal fun MutableGameState.compileBuild(keystoneMask: Int = 0): CompiledBuild = CompiledBuild.compile(
    content = content,
    itemStacks = IntArray(itemStacks.size) { itemStacks[it] },
    rebornItems = IntArray(rebornItems.size) { rebornItems[it] },
    acquisitionOrder = acquisitionOrder,
    coreShape = coreShape,
    equipped = equippedRelics,
    severed = severedRelics.map { it.id },
    spentOuroboros = ouroborosSpent,
    statusPotencyBonus = matrix.statusPotencyBonus,
    reactionPowerBonus = matrix.reactionPowerBonus,
    weaponKey = currentWeaponDefinition.key,
    keystoneMask = keystoneMask,
    boonRules = runBoons.flatMap { content.boon(it)?.buildRules.orEmpty() },
)

/**
 * The share of [item]'s parts bound relic liabilities disable (1: every effect, so offers weight it 0 and cards strike
 * it). An active effect counts as one part no liability disables, so an active item stays offerable under any Relic.
 */
internal fun MutableGameState.itemSuppressedShare(item: ItemDefinition): Float {
    var total = if (item.isActive) 1 else 0
    var suppressed = 0
    fun count(disabled: Boolean) {
        total++
        if (disabled) suppressed++
    }
    val verdant = build.liability(RelicId.VERDANT_ROT)
    val glass = build.liability(RelicId.GLASS_COMET)
    val frozenOut = build.liability(RelicId.ABSOLUTE_ZERO)
    item.stats.forEach { stat ->
        count(
            (verdant && stat.effect == ItemEffect.REGEN && build.regenerationToDecay == 0f) ||
                (glass && (stat.effect == ItemEffect.SHIELD_CAPACITY || stat.effect == ItemEffect.DAMAGE_REDUCTION)),
        )
    }
    item.clauses.forEach { clause ->
        clause.actions.forEach { action ->
            count(
                (verdant && action is EffectAction.Heal) ||
                    (glass && action is EffectAction.Shield) ||
                    (glass && clause.trigger == EffectTrigger.SHIELD_BREAK),
            )
        }
        clause.conditions.forEach { condition ->
            if (frozenOut && condition is EffectCondition.ElementIs && condition.element == StatusElement.THERMAL) count(true)
            // No Thermal lands, so no Thermal reaction fires, leaves a field or a mark.
            if (frozenOut && condition is EffectCondition.ReactionIs && condition.reaction.involves(StatusElement.THERMAL)) count(true)
        }
    }
    item.rules.forEach { rule ->
        count(
            frozenOut && when (rule) {
                is ItemRule.ElementPotency -> rule.element == StatusElement.THERMAL
                is ItemRule.StatusDuration -> rule.element == StatusElement.THERMAL
                is ItemRule.BreakThreshold -> rule.element == StatusElement.THERMAL
                is ItemRule.BreakPower -> rule.element == StatusElement.THERMAL
                is ItemRule.BreakSpread -> rule.element == StatusElement.THERMAL
                is ItemRule.ReactionKeystone -> rule.reaction.involves(StatusElement.THERMAL)
                is ItemRule.ReactionTuning -> rule.reaction?.involves(StatusElement.THERMAL) == true
                else -> false
            } || glass && rule is ItemRule.Convert && rule.kind == ConversionKind.OVERHEAL_TO_SHIELD,
        )
    }
    return if (total == 0) 0f else suppressed.toFloat() / total
}

/**
 * The share of [item]'s parts this run cannot use: skill parts (skill triggers and conditions, skill refunds,
 * skill buffs, skill rules, Catalysis and the skill conversions) while no active item is owned, and parts gated on a
 * weapon family other than the equipped one. Kept apart from [itemSuppressedShare], which Relics cause; Apex parts
 * are never dormant (each refunds skills before the Apex unlocks), and neither is an active effect, which counts as one
 * part an active item can always use.
 */
internal fun MutableGameState.itemDormantShare(item: ItemDefinition): Float {
    val noSkills = skillLoadout.ownedCount == 0
    val family = currentWeaponDefinition.family
    fun conditionDormant(condition: EffectCondition): Boolean = when (condition) {
        is EffectCondition.SkillIs, is EffectCondition.SkillTagged, is EffectCondition.SkillsCharging -> noSkills
        is EffectCondition.WeaponFamilyIs -> condition.family != family
        else -> false
    }
    var total = if (item.isActive) 1 else 0
    var dormant = 0
    fun count(isDormant: Boolean) {
        total++
        if (isDormant) dormant++
    }
    total += item.stats.size
    item.clauses.forEach { clause ->
        val clauseDormant = noSkills && (clause.trigger == EffectTrigger.SKILL_CAST || clause.trigger == EffectTrigger.SKILL_HIT) ||
            clause.conditions.any(::conditionDormant)
        clause.actions.forEach { action ->
            count(
                clauseDormant || noSkills && (action is EffectAction.RefundSkills ||
                    action is EffectAction.Buff && action.stat.isSkillStat),
            )
        }
    }
    item.rules.forEach { rule ->
        count(
            when (rule) {
                is ItemRule.WhileModifier -> conditionDormant(rule.condition) || noSkills && rule.stat.isSkillStat
                is ItemRule.SkillCooldown, is ItemRule.SkillPower, ItemRule.SkillCharges, is ItemRule.SkillAttunement,
                is ItemRule.CatalysisRate,
                -> noSkills
                is ItemRule.Convert -> noSkills && rule.kind.actsOnSkills
                else -> false
            },
        )
    }
    return if (total == 0 || dormant == 0) 0f else dormant.toFloat() / total
}

private val BuffStat.isSkillStat: Boolean get() = actsOnSkills
