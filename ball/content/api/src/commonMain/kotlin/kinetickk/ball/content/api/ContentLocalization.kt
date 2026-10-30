// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.common.localization.AppLanguage

/** Pure presentation mapping. Content identities and mechanical values stay language-independent. */
fun String.localizedContent(language: AppLanguage): String = when (language) {
    AppLanguage.English -> this
    AppLanguage.Russian -> contentInRussian() ?: this
}

internal fun String.contentInRussian(): String? =
    RussianContent[this] ?: itemModifierInRussian() ?: dynamicContentInRussian()

/**
 * Every English-keyed source, in merge order. Kept as pair lists so a test can prove that no English
 * key maps to two different Russian values (a later entry would otherwise overwrite silently).
 */
internal val RussianContentSources: List<List<Pair<String, String>>> = listOf(
    ContentPhrasesInRussian,
    WeaponPhrasesInRussian,
    BuildPhrasesInRussian,
    RelicPhrasesInRussian,
    ItemPhrasesInRussian,
    SkillPhrasesInRussian,
    ApexPhrasesInRussian,
    SitePhrasesInRussian,
    TerrainPhrasesInRussian,
    RunVarietyPhrasesInRussian,
    StructurePhrasesInRussian,
    DimensionPhrasesInRussian,
)

private val RussianContent: Map<String, String> = buildMap {
    RussianContentSources.forEach { source -> source.forEach { (english, russian) -> put(english, russian) } }
    // Canvas headings are sometimes uppercased before reaching presentation mapping.
    toMap().forEach { (english, russian) -> put(english.uppercase(), russian.uppercase()) }
}

private val ModifierValue = Regex("^([+−-])([0-9]+(?:\\.[0-9]+)?)(%|/s|s)?$")

/** Translates the compact `+4.5% Impact damage` form; unknown labels fall back to English (null). */
internal fun String.itemModifierInRussian(): String? {
    val effect = ItemEffect.entries.firstOrNull { endsWith(" ${it.displayLabel}") } ?: return null
    val value = ModifierValue.matchEntire(removeSuffix(" ${effect.displayLabel}")) ?: return null
    val label = RussianContent[effect.displayLabel] ?: return null
    val sign = if (value.groupValues[1] == "+") "+" else "−"
    val amount = value.groupValues[2].replace('.', ',')
    val suffix = when (value.groupValues[3]) {
        "/s" -> "/с"
        "s" -> " с"
        else -> value.groupValues[3]
    }
    return "$label: $sign$amount$suffix"
}
