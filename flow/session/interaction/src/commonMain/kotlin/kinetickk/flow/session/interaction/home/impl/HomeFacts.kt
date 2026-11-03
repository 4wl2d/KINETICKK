// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.localizedContent
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.KkIcon

/** Facts card content: the item's label, one big value, facts from real data and its (!) text. */
internal class HomeFacts(
    val label: String,
    val big: String,
    val facts: List<Pair<String, String>>,
    val info: String,
    val sub: String?,
    val stamp: String?,
)

/** The stamp menu row [target] carries when the game's state calls for it (see [homeFacts]). */
internal fun homeMenuStampText(target: HomeLayoutTarget, language: AppLanguage): String? = when (target) {
    HomeLayoutTarget.WORKSHOP -> language.text(SessionRedesignText.AFFORDABLE)
    HomeLayoutTarget.REBIRTH -> language.text(SessionRedesignText.READY)
    else -> null
}

internal fun homeFacts(model: HomeUiModel, target: HomeLayoutTarget, language: AppLanguage, textScale: Float): HomeFacts {
    val label = language.text(HomeMenuLabels[HomeMenuTargets.indexOf(target)])
    val matter = homeNumber(model.totalMatter, language)
    val weapon = model.startingWeaponName?.localizedContent(language)
    val tierProfile = model.rebirthProfile
    return when (target) {
        HomeLayoutTarget.WORKSHOP -> {
            val completion = homeWorkshopCompletion(model)
            HomeFacts(
                label,
                language.text(SessionRedesignText.PERCENT, completion),
                buildList {
                    add(language.text(SessionRedesignText.WEAPONS_FACT) to "${model.unlockedWeaponCount}/${model.weaponCount}")
                    if (model.matrixMaxRanks > 0) {
                        add(language.text(SessionRedesignText.RANKS) to "${model.matrixRanks}/${model.matrixMaxRanks}")
                    }
                    if (model.upgradeLevelCap > 0) {
                        add(language.text(SessionRedesignText.UPGRADES) to "${model.upgradeLevels}/${model.upgradeLevelCap}")
                    }
                },
                language.text(SessionRedesignText.WORKSHOP_INFO),
                language.text(SessionRedesignText.PERCENT, completion),
                if (model.workshopAffordable) homeMenuStampText(target, language) else null,
            )
        }
        HomeLayoutTarget.REBIRTH -> HomeFacts(
            label,
            model.rebirthLevel.toString(),
            listOf(
                language.text(SessionRedesignText.TIER) to tierProfile.displayName.localizedContent(language),
                language.text(SessionRedesignText.RANK) to
                    if (model.ascendant) language.text(SessionRedesignText.ASCENDANT) else model.rebirthRank.toString(),
                language.text(SessionRedesignText.STATUS) to
                    language.text(if (model.canRebirth) SessionRedesignText.NEW_TIER else SessionRedesignText.SELECTED),
            ),
            language.text(SessionRedesignText.REBIRTH_INFO),
            null,
            if (model.canRebirth) homeMenuStampText(target, language) else null,
        )
        HomeLayoutTarget.CODEX -> HomeFacts(
            label,
            model.discoveredItemCount.toString(),
            buildList {
                add(language.text(SessionText.ITEMS_TITLE) to "${model.discoveredItemCount}/${model.itemCount}")
                if (model.relicCount > 0) add(language.text(SessionText.RELICS_TITLE) to "${model.discoveredRelicCount}/${model.relicCount}")
                add(language.text(SessionText.FORMS_TITLE) to "${model.unlockedCoreShapes.size}/${model.coreShapes.size}")
            },
            language.text(SessionRedesignText.CODEX_INFO),
            "${model.discoveredItemCount}/${model.itemCount}",
            null,
        )
        HomeLayoutTarget.SETTINGS -> HomeFacts(
            label,
            language.code.uppercase(),
            listOf(
                language.text(SessionRedesignText.LANGUAGE) to language.nativeName,
                language.text(SessionRedesignText.TEXT_SIZE) to "${(textScale * 100f + 0.5f).toInt()}%",
            ),
            language.text(SessionRedesignText.SETTINGS_INFO),
            null,
            null,
        )
        else -> HomeFacts(
            label,
            model.rebirthLevel.toString(),
            buildList {
                add(language.text(SessionRedesignText.TIER) to tierProfile.displayName.localizedContent(language))
                add(language.text(SessionRedesignText.FORM) to model.coreShape(model.coreShape).displayName.localizedContent(language))
            },
            rebirthTierDescription(tierProfile).localizedContent(language),
            language.text(SessionText.REBIRTH_LEVEL, model.rebirthLevel),
            null,
        )
    }
}

/**
 * How much of the Workshop is bought, in whole percent (rounded down): owned weapons, Matrix ranks and
 * Refit / Honing levels over every weapon, rank and level it sells. Unread parts count as nothing.
 */
internal fun homeWorkshopCompletion(model: HomeUiModel): Int {
    val total = model.weaponCount.toLong() + model.matrixMaxRanks + model.upgradeLevelCap
    if (total <= 0L) return 0
    val owned = model.unlockedWeaponCount.toLong() + model.matrixRanks + model.upgradeLevels
    return (owned.coerceIn(0L, total) * 100L / total).toInt()
}

/** What the selected tier adds: its newest mutator's rule, or the baseline cycle's line at tier 0. */
internal fun rebirthTierDescription(profile: kinetickk.ball.content.api.RebirthProfile): String =
    profile.newestMutator?.description ?: BASELINE_TIER_DESCRIPTION

private const val BASELINE_TIER_DESCRIPTION = "The original Architect cycle."

/** Grouped integer (1,284 / 1 284) for chips and facts. */
internal fun homeNumber(value: Long, language: AppLanguage): String {
    val digits = value.coerceAtLeast(0L).toString()
    val separator = if (language == AppLanguage.Russian) ' ' else ','
    return buildString(digits.length + digits.length / 3) {
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(separator)
            append(char)
        }
    }
}

/** The (!) text of the form panel: the current character's mechanic name and what it does. */
internal fun homeFormInfo(model: HomeUiModel, language: AppLanguage): String {
    val definition = model.coreShape(model.coreShape)
    return definition.mechanicName.localizedContent(language) + "\n" + definition.mechanicDescription.localizedContent(language)
}

/** The form icon of a character: [KkIcon.Forms] follows the character order. */
internal fun CoreShape.formIcon(): KkIcon = KkIcon.Forms[ordinal]
