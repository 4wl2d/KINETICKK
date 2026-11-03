// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.look.UNKNOWN_PAIRING_NAME
import kinetickk.ball.gameplay.interaction.canvas.look.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.look.overlayLinkMultiplier
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.BuildForecast
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.ForecastKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.KkIcon
import kotlin.math.pow
import kinetickk.ball.gameplay.interaction.canvas.look.*

/** Forecast order on a card: suppression first, then reactions, tiers, affinity and relic links. */
private fun ForecastKind.priority(): Int = when (this) {
    ForecastKind.SUPPRESSED -> 0
    ForecastKind.REACTION -> 1
    ForecastKind.TAG_TIER -> 2
    ForecastKind.AFFINITY -> 3
    ForecastKind.CONCORD -> 4
    ForecastKind.DISCORD -> 5
    ForecastKind.DISSONANCE -> 6
    ForecastKind.HARMONY -> 7
}

/** Forecast chips of an item card, most important first; [suppressor] is the relic that voids it. */
internal fun GameplayRenderModel.rewardForecastChips(
    forecasts: List<BuildForecast>,
    suppressor: RelicId?,
    language: AppLanguage,
): List<RewardChip> = forecasts.sortedBy { it.kind.priority() }.mapNotNull { rewardForecastChip(it, suppressor, language) }.distinct()

private fun GameplayRenderModel.rewardForecastChip(forecast: BuildForecast, suppressor: RelicId?, language: AppLanguage): RewardChip? {
    val policy = content.build.policy
    return when (forecast.kind) {
        ForecastKind.SUPPRESSED -> RewardChip(RewardChipKind.SUPPRESSED, language.text(OverlayRedesignText.Suppressed), relic = suppressor,
            rune = suppressor?.let(content::relic))
        ForecastKind.REACTION -> forecast.reaction?.let { id ->
            RewardChip(
                RewardChipKind.REACTION,
                if (forecast.discovered) content.build.reaction(id).name.localizedContent(language) else UNKNOWN_PAIRING_NAME,
                elements = listOf(id.first, id.second),
                discovered = forecast.discovered,
            )
        }
        ForecastKind.TAG_TIER -> forecast.tag?.let { tag ->
            RewardChip(RewardChipKind.TAG_TIER, tag.displayLabel.localizedContent(language), tag = tag,
                before = forecast.before, after = forecast.after, pips = policy.resonanceThresholds.size)
        }
        ForecastKind.AFFINITY -> forecast.element?.let { element ->
            val thresholds = policy.affinityThresholds
            val goal = thresholds.firstOrNull { it >= forecast.after } ?: forecast.after
            RewardChip(RewardChipKind.AFFINITY, element.displayLabel.localizedContent(language) + " " + forecast.after + "/" + goal,
                elements = listOf(element), before = forecast.before, after = forecast.after,
                active = policy.tier(forecast.after, thresholds) > policy.tier(forecast.before, thresholds))
        }
        ForecastKind.CONCORD, ForecastKind.DISCORD, ForecastKind.HARMONY, ForecastKind.DISSONANCE ->
            rewardLinkChip(forecast.kind.linkKind(), forecast.pairing, forecast.discovered, 1, language)
    }
}

private fun ForecastKind.linkKind(): RelicLinkKind = when (this) {
    ForecastKind.DISCORD -> RelicLinkKind.DISCORD
    ForecastKind.HARMONY -> RelicLinkKind.HARMONY
    ForecastKind.DISSONANCE -> RelicLinkKind.DISSONANCE
    else -> RelicLinkKind.CONCORD
}

private fun RelicLinkKind.priority(): Int = when (this) {
    RelicLinkKind.CONCORD -> 0
    RelicLinkKind.DISCORD -> 1
    RelicLinkKind.DISSONANCE -> 2
    RelicLinkKind.HARMONY -> 3
}

/**
 * A relic link chip: a named pairing reads "+ Name" or "− Name" ("?" until met); [count] generic
 * links of one kind compound into one multiplier ("Harmony ×1.21").
 */
private fun GameplayRenderModel.rewardLinkChip(
    kind: RelicLinkKind,
    pairing: RelicPairingId?,
    discovered: Boolean,
    count: Int,
    language: AppLanguage,
): RewardChip {
    val policy = content.build.policy
    val name = pairing?.let { if (discovered) content.build.pairing(it).name.localizedContent(language) else UNKNOWN_PAIRING_NAME }
    return when (kind) {
        RelicLinkKind.CONCORD -> RewardChip(RewardChipKind.CONCORD, "+ " + (name ?: UNKNOWN_PAIRING_NAME), discovered = discovered)
        RelicLinkKind.DISCORD -> RewardChip(RewardChipKind.DISCORD, "− " + (name ?: UNKNOWN_PAIRING_NAME), discovered = discovered)
        RelicLinkKind.HARMONY -> RewardChip(RewardChipKind.HARMONY, language.text(OverlayRedesignText.Harmony) + " " +
            overlayLinkMultiplier(policy.harmonyScale.pow(count), language), after = count)
        RelicLinkKind.DISSONANCE -> RewardChip(RewardChipKind.DISSONANCE, language.text(OverlayRedesignText.Dissonance) + " " +
            overlayLinkMultiplier(policy.dissonanceScale.pow(count), language), after = count)
    }
}

/**
 * Forecast chips of a relic card: the links it forms (named pairings first, generic ones
 * compounded), then the affinity and tag tiers it adds. The nucleus forecasts lead; a choice
 * without them reads the links of its candidate matrix.
 */
internal fun GameplayRenderModel.rewardRelicForecastChips(
    forecasts: List<BuildForecast>,
    preview: RewardRelicPreview,
    language: AppLanguage,
): List<RewardChip> {
    class Link(val kind: RelicLinkKind, val pairing: RelicPairingId?, val discovered: Boolean)
    val fromForecasts = forecasts.filter { it.kind.priority() >= ForecastKind.CONCORD.priority() }
        .map { Link(it.kind.linkKind(), it.pairing, it.discovered) }
    val links = fromForecasts.ifEmpty { preview.addedLinks.map { Link(it.kind, it.pairing, it.discovered) } }
    val named = links.filter { it.pairing != null }.sortedBy { it.kind.priority() }
        .map { rewardLinkChip(it.kind, it.pairing, it.discovered, 1, language) }
    val generic = links.filter { it.pairing == null }.groupBy { it.kind }.entries.sortedBy { it.key.priority() }
        .map { (kind, group) -> rewardLinkChip(kind, null, true, group.size, language) }
    val rest = forecasts.filter { it.kind.priority() < ForecastKind.CONCORD.priority() }.sortedBy { it.kind.priority() }
        .mapNotNull { rewardForecastChip(it, null, language) }
    return (named + generic + rest).distinct()
}

internal fun RewardFocus.relicAspect(): RelicAspect? = RelicAspect.entries.firstOrNull { it.name == name }

/** What picking a focus leads to, by the directed reward's choice type (artifacts, relics, weapons). */
internal fun rewardFocusEffect(type: ChoiceType): OverlayRedesignText = when (type) {
    // The active slot prompt, omens, signal prompts and boons are never directed rewards; a focus never leads to one.
    ChoiceType.ITEM, ChoiceType.ACTIVE_SLOT, ChoiceType.OMEN, ChoiceType.SIGNAL, ChoiceType.BOON -> OverlayRedesignText.FocusArtifacts
    ChoiceType.RELIC, ChoiceType.RELIC_BIND -> OverlayRedesignText.FocusRelics
    ChoiceType.TOTEM, ChoiceType.WEAPON -> OverlayRedesignText.FocusWeapons
}

internal fun RewardFocus.overlayIcon(): KkIcon = relicAspect()?.overlayIcon() ?: when (this) {
    RewardFocus.MOTION -> KkIcon.SYSTEM_DASH
    RewardFocus.CONTROL -> KkIcon.SYSTEM_POLARITY
    RewardFocus.STRIKE -> KkIcon.SYSTEM_ELITE
    RewardFocus.OFFENSE -> KkIcon.SYSTEM_OVERDRIVE
    RewardFocus.DEFENSE -> KkIcon.SYSTEM_SHIELD
    RewardFocus.ECONOMY -> KkIcon.SYSTEM_MATTER
    else -> KkIcon.SYSTEM_DATA
}
