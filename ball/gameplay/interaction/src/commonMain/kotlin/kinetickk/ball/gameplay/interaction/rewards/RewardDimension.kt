// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.DimensionRewardKind
import kinetickk.ball.content.api.ModifierUnit
import kinetickk.ball.content.api.RunBoonKind
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.overlayGrouped
import kinetickk.ball.gameplay.interaction.canvas.elementFill
import kinetickk.ball.gameplay.interaction.canvas.elementIcon
import kinetickk.ball.gameplay.interaction.localization.DimensionHudText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkCardMaterial
import kinetickk.foundation.design.KkCardOrnament
import kinetickk.foundation.design.KkCardStyle
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.math.roundToInt

// The dimension reward overlay: the Zenith's Blessing cards, the Nadir's Pact cards and their Refuse card, and the
// headings of every dimension reward (the Archive's trove keeps the ordinary item cards). Cards carry facts only: the
// boon's own gains and costs, the nucleus preview of the stats it leaves and the Matter the Refuse card salvages.

/** The heading of an open dimension reward (by the layer's reward), or null when the open choice is no dimension reward. */
internal fun GameplayRenderModel.rewardDimensionHeading(language: AppLanguage): String? {
    val layer = dimension.rewardLayer ?: return null
    return when (content.dimensions.definition(layer).reward.kind) {
        DimensionRewardKind.BLESSING_OFFER -> language.text(DimensionHudText.BlessingHeading)
        DimensionRewardKind.PACT_OFFER -> language.text(DimensionHudText.PactHeading)
        DimensionRewardKind.ARCHIVE_TROVE -> language.text(DimensionHudText.ArchiveHeading)
        DimensionRewardKind.NONE -> null
    }
}

/**
 * A boon card: the boon's name, its kind on the band, its gains as stat lines (the nucleus preview's before and after
 * when it has one, else the gains themselves) and a Pact's price as cost lines in the relic-liability block (a stat it
 * lowers, or tougher, faster or harder-hitting enemies), in the kind's hue: the Blessing's shield, the Pact's heat (never
 * gold, the Rebirth's). The Refuse card reads as a decline and names the Matter it salvages.
 */
internal fun GameplayRenderModel.rewardBoonCard(choice: ChoiceOption, index: Int, language: AppLanguage): RewardCardPresentation {
    val boon = choice.boonId?.let(content::boon)
    if (boon == null) {
        val band = language.text(DimensionHudText.BoonPact)
        return RewardCardPresentation(
            choice = choice,
            accent = Kk.Mute2,
            tag = band,
            descriptions = listOf(language.text(DimensionHudText.RefuseEffect, overlayGrouped((choice.salvageMatter ?: 0).toLong(), language))),
            operation = "",
            title = choice.title.localizedContent(language),
            icon = KkIcon.SYSTEM_MATTER,
            bandStart = band,
        )
    }
    val band = language.text(when (boon.kind) {
        RunBoonKind.BLESSING -> DimensionHudText.BoonBlessing
        RunBoonKind.PACT -> DimensionHudText.BoonPact
        RunBoonKind.ASPECT -> DimensionHudText.BoonAspect
    })
    val element = if (boon.kind == RunBoonKind.ASPECT) content.structures.templates
        .firstOrNull { template -> template.rewards.any { it.boonId == boon.id } }?.element else null
    val preview = rewardPreviews.getOrNull(index)?.takeIf { choices.getOrNull(index) == choice }
    // The preview shows every stat the boon moves; the ones a Pact lowers read as its price below instead.
    val changes = preview?.changes?.map { it.presentation(language) }?.filter { it.improved }
        ?: boon.gains.mapNotNull { (it as? RunModifierEffect.Stat)?.let { stat -> boonStatLine(stat, language) } }
    return RewardCardPresentation(
        choice = choice,
        accent = Kk.Bone2,
        tag = band,
        descriptions = if (boon.kind == RunBoonKind.ASPECT) listOf(boon.description.localizedContent(language)) else emptyList(),
        operation = "",
        title = boon.name.localizedContent(language),
        changes = changes,
        liabilities = boon.costs.flatMap { boonPriceLines(it, language) },
        forecasts = preview?.forecasts.orEmpty(),
        rank = BOON_CARD_RANK,
        icon = when (boon.kind) {
            RunBoonKind.BLESSING -> KkIcon.FORMS_RING
            RunBoonKind.PACT -> KkIcon.SYSTEM_HEAT
            RunBoonKind.ASPECT -> element?.let { elementIcon(it.ordinal) } ?: KkIcon.FORMS_RING
        },
        elements = listOfNotNull(element),
        bandStart = band,
        boonKind = boon.kind,
    )
}

/** A gain as a stat line: the stat's name and its signed amount in the stat's unit. */
private fun boonStatLine(stat: RunModifierEffect.Stat, language: AppLanguage): RewardStatPresentation =
    RewardStatPresentation(stat.effect.displayLabel.localizedContent(language), "", boonAmount(stat, language), stat.amount > 0f)

/** A stat amount signed in its unit ("+25", "+20%", "−0.6 s"). */
private fun boonAmount(stat: RunModifierEffect.Stat, language: AppLanguage): String {
    val (shown, unit) = when (stat.effect.unit) {
        ModifierUnit.PERCENT -> stat.amount * 100f to "%"
        ModifierUnit.PER_SECOND -> stat.amount to "/s"
        ModifierUnit.SECONDS -> stat.amount to "s"
        ModifierUnit.FLAT -> stat.amount to ""
    }
    val number = rewardNumber(shown, unit, language)
    return if (stat.amount > 0f) "+$number" else number
}

/**
 * A Pact's price as cost lines: a lowered stat ("Max integrity −20"), and each hostile scale above 1 as its own line
 * ("Enemy integrity +12%", "Enemy speed +8%", "Damage taken +15%").
 */
internal fun boonPriceLines(cost: RunModifierEffect, language: AppLanguage): List<String> = when (cost) {
    is RunModifierEffect.Stat -> listOf(cost.effect.displayLabel.localizedContent(language) + " " + boonAmount(cost, language))
    is RunModifierEffect.HostileScale -> buildList {
        fun percent(scale: Float) = ((scale - 1f) * 100f).roundToInt()
        if (cost.health > 1f) add(language.text(DimensionHudText.EnemyIntegrityUp, percent(cost.health)))
        if (cost.speed > 1f) add(language.text(DimensionHudText.EnemySpeedUp, percent(cost.speed)))
        if (cost.incomingDamage > 1f) add(language.text(DimensionHudText.DamageTakenUp, percent(cost.incomingDamage)))
    }
    // A boon's price is a lowered stat or a hostile scale only (content validation); nothing else is worded here.
    else -> emptyList()
}

/** A boon kind's hue under the colour-vision preset: the Blessing's shield light, the Pact's heat. */
internal fun RunBoonKind.hue(roles: KkRolePalette): Color = when (this) {
    RunBoonKind.BLESSING -> roles.shield
    RunBoonKind.PACT -> roles.heat
    RunBoonKind.ASPECT -> roles.kinetic
}

/**
 * The face of a boon card: the rare chrome frame, the kind's hue, a Blessing's concentric halo rings with a shield's
 * double rim, a Pact's vent bars with the vent grille, and the boon's mark as the watermark.
 */
internal fun rewardBoonCardLook(presentation: RewardCardPresentation, roles: KkRolePalette): RewardCardLook {
    val kind = presentation.boonKind ?: RunBoonKind.BLESSING
    val hue = presentation.accentColor(roles)
    val element = presentation.elements.firstOrNull()
    val style = KkCardStyle(
        material = KkCardMaterial.CHROME,
        hue = hue,
        pattern = when (kind) {
            RunBoonKind.BLESSING -> KkFillKind.RINGS
            RunBoonKind.PACT -> KkFillKind.BARS
            RunBoonKind.ASPECT -> element?.let { elementFill(it.ordinal) } ?: KkFillKind.RINGS
        },
        ornament = when (kind) {
            RunBoonKind.BLESSING -> KkCardOrnament.DOUBLE_RIM
            RunBoonKind.PACT -> KkCardOrnament.VENTS
            RunBoonKind.ASPECT -> KkCardOrnament.RETICLE
        },
        watermark = presentation.icon,
        seed = BOON_SEED + abs(presentation.choice.boonId ?: 0),
        patternBoost = patternBoost(hue, roles),
        gold = roles.gold,
        spectrum = roles.spectrum,
    )
    return RewardCardLook(style, null, hue, hue)
}

/** A boon card's rank: the rare one, about two Rare items' worth (its frame is the chrome, never the legendary gold). */
private const val BOON_CARD_RANK = 3

/** The card seed's kind offset for boons, after the item, relic, weapon and other seeds. */
private const val BOON_SEED = 0x5000
