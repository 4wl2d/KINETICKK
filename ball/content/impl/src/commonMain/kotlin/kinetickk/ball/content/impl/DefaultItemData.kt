// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRarity.COMMON
import kinetickk.ball.content.api.ItemRarity.EPIC
import kinetickk.ball.content.api.ItemRarity.LEGENDARY
import kinetickk.ball.content.api.ItemRarity.RARE
import kinetickk.ball.content.api.ItemRarity.UNCOMMON
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatDelta
import kinetickk.foundation.collections.toImmutableList

// Compact authoring vocabulary. Every value is validated by the api constructors.
internal fun stat(effect: ItemEffect, amount: Float) = StatDelta(effect, amount)

internal fun on(
    trigger: EffectTrigger,
    vararg actions: EffectAction,
    given: List<EffectCondition> = emptyList(),
    cooldown: Float = 0f,
    every: Int = 1,
) = EffectClause(trigger, given.toImmutableList(), actions.toList().toImmutableList(), cooldown, every)

internal fun apply(element: StatusElement, stacks: Int, radius: Float = 0f) =
    EffectAction.ApplyStatus(ElementPick.of(element), stacks, radius)

internal fun applyPick(pick: ElementPick, stacks: Int, radius: Float = 0f) =
    EffectAction.ApplyStatus(pick, stacks, radius)

internal class ItemDraft(
    val name: String,
    val flavor: String,
    val rarity: ItemRarity,
    val tags: List<BuildTag>,
    val elements: List<StatusElement> = emptyList(),
    val stats: List<StatDelta> = emptyList(),
    val clauses: List<EffectClause> = emptyList(),
    val rules: List<ItemRule> = emptyList(),
    val stacks: Int = when (rarity) {
        COMMON -> 3
        UNCOMMON, RARE -> 2
        EPIC, LEGENDARY -> 1
    },
    val unlock: Int = 1,
    val rebirth: Int = 0,
    val risk: Boolean = false,
    val active: SkillId? = null,
)

/** A reaction clause condition naming both elements: it holds only for the reaction between them. */
internal fun pair(first: StatusElement, second: StatusElement): List<EffectCondition> =
    listOf(EffectCondition.ElementIs(first), EffectCondition.ElementIs(second))
