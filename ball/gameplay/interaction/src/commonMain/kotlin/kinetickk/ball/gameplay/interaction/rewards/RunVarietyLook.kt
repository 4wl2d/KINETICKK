// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.KkIcon

/** The Keynote's tag and element names ("Ram Thermal"), or what the run has of them; null without a Keynote. */
internal fun GameplayRenderModel.rewardKeynoteLabel(language: AppLanguage): String? {
    val parts = listOfNotNull(keynoteTag?.displayLabel?.localizedContent(language), keynoteElement?.displayLabel?.localizedContent(language))
    return if (parts.isEmpty()) null else parts.joinToString(" ")
}

/**
 * The director pattern's sigil: a system mark per personality, never a form (the characters'), a tag (the Keynote's)
 * or an element (the hues' channel).
 */
internal fun DirectorPersonalityId.sigil(): KkIcon = when (this) {
    DirectorPersonalityId.TIDE -> KkIcon.SYSTEM_CYCLE_CLOCK
    DirectorPersonalityId.HUNTER_CELL -> KkIcon.SYSTEM_POLARITY
    DirectorPersonalityId.SIEGE -> KkIcon.SYSTEM_DISMANTLED
    DirectorPersonalityId.AMBUSHERS -> KkIcon.SYSTEM_ANOMALY
    DirectorPersonalityId.ELEMENTAL_STORM -> KkIcon.SYSTEM_HEAT
    DirectorPersonalityId.LONG_CALM -> KkIcon.SYSTEM_BRAKE
}

/** An omen's card mark; the Amplified Keynote takes the run's Keynote tag. */
internal fun OmenId.icon(keynote: BuildTag?): KkIcon = when (this) {
    OmenId.GLASS_SUN -> KkIcon.SYSTEM_OVERDRIVE
    OmenId.IRON_TIDE -> KkIcon.SYSTEM_MATTER
    OmenId.HUNGRY_DARK -> KkIcon.SYSTEM_DATA
    OmenId.CROWDED_SKY -> KkIcon.SYSTEM_REROLL
    OmenId.QUICKENED_HOST -> KkIcon.SYSTEM_DASH
    OmenId.RICH_VEINS -> KkIcon.SYSTEM_MATTER
    OmenId.AMPLIFIED_KEYNOTE -> keynote?.overlayIcon() ?: KkIcon.SYSTEM_DATA
    OmenId.VOLATILE_PACT -> KkIcon.SYSTEM_HEAT
    OmenId.FAULT_LINE -> KkIcon.TAG_RAM
    OmenId.ASCETIC_PATH -> KkIcon.SYSTEM_REROLL
    OmenId.CROWNED_QUARRY -> KkIcon.SYSTEM_ELITE
    OmenId.STILL_WATER -> KkIcon.SYSTEM_SHIELD
}

/** A signal option's card mark. */
internal fun SignalOption.icon(): KkIcon = when (this) {
    SignalOption.MERCHANT_RARE_OFFER, SignalOption.MERCHANT_EPIC_OFFER -> KkIcon.SYSTEM_MATTER
    SignalOption.MERCHANT_REROLLS -> KkIcon.SYSTEM_REROLL
    SignalOption.SHRINE_BLOOD -> KkIcon.SYSTEM_INTEGRITY
    SignalOption.SHRINE_WARD -> KkIcon.SYSTEM_SHIELD
    SignalOption.SHRINE_RELIC -> KkIcon.ASPECT_SOVEREIGN
    SignalOption.GAMBLE_REROLL -> KkIcon.SYSTEM_REROLL
    SignalOption.GAMBLE_MATTER -> KkIcon.SYSTEM_MATTER
    SignalOption.GAMBLE_INTEGRITY -> KkIcon.SYSTEM_INTEGRITY
    // A cursed offer can be taken: the warning diamond marks its curse (the padlock means a locked card).
    SignalOption.CACHE_CRATE, SignalOption.CACHE_RELIQUARY, SignalOption.CACHE_IDOL -> KkIcon.SYSTEM_ANOMALY
    SignalOption.DERELICT_REPAIR -> KkIcon.SYSTEM_INTEGRITY
    SignalOption.DERELICT_CARGO -> KkIcon.SYSTEM_MATTER
    SignalOption.DERELICT_VAULT -> KkIcon.SYSTEM_KEY
    SignalOption.IGNORE -> KkIcon.SYSTEM_DASH
}
