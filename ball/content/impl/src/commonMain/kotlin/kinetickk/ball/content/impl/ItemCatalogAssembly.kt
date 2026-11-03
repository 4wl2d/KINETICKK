// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRule
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal fun defaultItems(): ImmutableList<ItemDefinition> =
    (FamilyDrafts + ThemeDrafts + MechanicDrafts + ActiveDrafts + ObstacleDrafts).mapIndexed { index, draft ->
        ItemDefinition(
            id = index,
            name = draft.name,
            description = draft.flavor,
            rarity = draft.rarity,
            tags = draft.tags.toImmutableList(),
            elements = draft.elements.toImmutableList(),
            stats = draft.stats.toImmutableList(),
            clauses = draft.clauses.toImmutableList(),
            rules = draft.rules.toImmutableList(),
            maxStacks = draft.stacks,
            unlockLevel = draft.unlock,
            minimumRebirth = draft.rebirth,
            risk = draft.risk,
            active = draft.active,
        )
    }.toImmutableList().also(::validateItems)

internal fun validateItems(items: List<ItemDefinition>) {
    check(items.size <= ContentBounds.MAX_ITEMS) { "Item catalog exceeds ${ContentBounds.MAX_ITEMS} items" }
    check(items.withIndex().all { (index, item) -> item.id == index }) { "Item ids must be contiguous from zero" }
    check(items.map { it.name }.toSet().size == items.size) { "Item names must be unique" }
    check(items.map { it.description }.toSet().size == items.size) { "Item flavor lines must be unique" }
    check(items.all { it.tags.isNotEmpty() || it.elements.isNotEmpty() || it.rules.any { rule -> rule is ItemRule.ReactionPower || rule is ItemRule.ReactionResidue } }) {
        "Every item belongs to an archetype, an element or the reaction family"
    }
    BuildTag.entries.forEach { tag ->
        check(items.count { tag in it.tags } >= 5) { "$tag needs at least five items to be draftable" }
    }
    StatusElement.entries.forEach { element ->
        check(items.count { element in it.elements } >= 5) { "$element needs at least five items" }
        check(items.any { element in it.elements && it.unlockLevel <= 2 }) { "$element needs an early enabler" }
    }
    validateExclusiveRules(items)
}

/**
 * A reaction has at most one keystone item, which declares both of the reaction's elements (the item itself is
 * Epic or Legendary by construction); at most one item grants skill charges and at most one attunes skills.
 */
internal fun validateExclusiveRules(items: List<ItemDefinition>) {
    val keystones = items.mapNotNull { item -> item.keystone?.let { it to item } }
    keystones.groupBy({ it.first }, { it.second.name }).forEach { (reaction, names) ->
        check(names.size == 1) { "$reaction has more than one keystone item: $names" }
    }
    keystones.forEach { (reaction, item) ->
        check(reaction.first in item.elements && reaction.second in item.elements) {
            "${item.name} switches $reaction to its keystone without declaring both of its elements"
        }
    }
    check(items.count { item -> item.rules.any { it is ItemRule.SkillCharges } } <= 1) { "At most one item grants skill charges" }
    check(items.count { item -> item.rules.any { it is ItemRule.SkillAttunement } } <= 1) { "At most one item attunes skills" }
}

/**
 * Progressive content validation: every explicit element an item touches is declared on the item (so
 * cards and affinity counts never lie), and no combination of threshold reductions can drop a Break
 * below two stacks.
 */
internal fun validateBuildReferences(items: List<ItemDefinition>, build: kinetickk.ball.content.api.BuildCatalog) {
    items.forEach { item ->
        val touched = buildSet {
            item.clauses.forEach { clause ->
                clause.actions.forEach { action ->
                    when (action) {
                        is EffectAction.ApplyStatus -> action.pick.element?.let(::add)
                        is EffectAction.Chain -> action.pick?.element?.let(::add)
                        is EffectAction.Shards -> action.pick?.element?.let(::add)
                        is EffectAction.Well -> action.pick?.element?.let(::add)
                        else -> Unit
                    }
                }
            }
            item.rules.forEach { rule ->
                when (rule) {
                    is ItemRule.ElementPotency -> add(rule.element)
                    is ItemRule.StatusDuration -> add(rule.element)
                    is ItemRule.BreakThreshold -> add(rule.element)
                    is ItemRule.BreakPower -> add(rule.element)
                    is ItemRule.BreakSpread -> add(rule.element)
                    is ItemRule.SkillAttunement -> rule.pick.element?.let(::add)
                    is ItemRule.SkillCooldown -> rule.element?.let(::add)
                    is ItemRule.SkillPower -> rule.element?.let(::add)
                    else -> Unit
                }
            }
        }
        require(item.elements.containsAll(touched)) { "${item.name} touches undeclared elements $touched" }
    }
    StatusElement.entries.forEach { element ->
        val reduction = items.sumOf { item ->
            item.rules.filterIsInstance<ItemRule.BreakThreshold>().filter { it.element == element }.sumOf { it.reduction }
        }
        // One affinity tier also lowers the threshold by one.
        require(build.element(element).breakThreshold - reduction - 1 >= 2) {
            "$element break threshold can fall below two stacks"
        }
    }
}
