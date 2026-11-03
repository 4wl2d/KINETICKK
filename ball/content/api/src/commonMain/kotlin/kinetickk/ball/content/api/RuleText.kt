// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage

/**
 * The item's passive mechanics as display lines: stats, then one TRIGGER and one EFFECT line per
 * clause, then rules; every cost line follows the rest in declaration order. An active item's effect
 * is not among them: cards and the Codex present it from its [SkillDefinition] (its summary, its
 * [rankLines] at the rank the item reaches and its rank III text).
 */
fun ItemDefinition.ruleLines(
    language: AppLanguage,
    context: RuleTextContext = RuleTextContext(),
): ImmutableList<RuleLine> {
    val writer = RuleWriter(language, context)
    val lines = ArrayList<RuleLine>(stats.size + clauses.size * 2 + rules.size * 2)
    stats.forEach { lines += writer.stat(it) }
    clauses.forEach { clause ->
        lines += writer.trigger(clause)
        lines += writer.effect(clause)
    }
    rules.forEach { lines += writer.rule(it) }
    val (costs, rest) = lines.partition { it.kind == RuleLineKind.COST }
    return (rest + costs).toImmutableList()
}

/** The upside in [language] with its `{n}` values at [rank], using the language's decimal separator. */
fun RelicDefinition.upsideText(language: AppLanguage, rank: Int): String =
    RelicValuePlaceholder.replace(upside.localizedContent(language)) { match ->
        match.groupValues[1].toIntOrNull()?.let(values::getOrNull)
            ?.let { value -> language.number(value.at(rank).toDouble(), RELIC_DECIMALS) }
            ?: match.value
    }

/** The mandatory cost in [language]; liabilities never scale with rank. */
fun RelicDefinition.liabilityText(language: AppLanguage): String = liability.localizedContent(language)

private val RelicValuePlaceholder = Regex("\\{([0-9]+)\\}")
private const val RELIC_DECIMALS = 2

/** Critical chance kept by [ConversionKind.CRIT_CHANCE_TO_DAMAGE]. */
internal const val CRIT_CHANCE_CONVERSION_CAP = 0.3

/** Cooling kept by [ConversionKind.DASH_HEAT_TO_SKILLS]. */
const val DASH_HEAT_CONVERSION_COOLING: Float = 0.8f

/** Data gain kept by [ConversionKind.DATA_TO_SKILLS]. */
const val DATA_CONVERSION_GAIN: Float = 0.8f

/** Skill cooldown scale of [ConversionKind.SKILLS_COST_HEAT]. */
const val SKILL_HEAT_CONVERSION_COOLDOWN: Float = 0.6f
