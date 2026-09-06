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
    RussianContent[this] ?: generatedItemDescriptionInRussian() ?: itemModifierInRussian() ?:
        dynamicContentInRussian()

private val ItemFamilies = mapOf(
    "Impact" to "Удар", "Arsenal" to "Арсенал", "Density" to "Плотность",
    "Polarity" to "Полярность", "Cryogenic" to "Криогеника", "Integrity" to "Прочность",
    "Renewal" to "Восстановление", "Vector" to "Вектор", "Efficiency" to "Эффективность",
    "Precision" to "Точность", "Ruin" to "Разрушение", "Collection" to "Сбор",
    "Fortune" to "Удача", "Archive" to "Архив", "Salvage" to "Утилизация",
    "Tempo" to "Темп", "Shield" to "Щит", "Bulwark" to "Бастион",
    "Combo" to "Комбо", "Overdrive" to "Перегрузка",
)

private val ItemNouns = mapOf(
    "Ram" to "Таран", "Dynamo" to "Динамо", "Ballast" to "Балласт", "Compass" to "Компас",
    "Vent" to "Радиатор", "Lattice" to "Решётка", "Seed" to "Семя", "Thruster" to "Ускоритель",
    "Reclaimer" to "Переработчик", "Lens" to "Линза", "Crucible" to "Тигель",
    "Harvester" to "Сборщик", "Die" to "Кость", "Codex" to "Кодекс", "Siphon" to "Сифон",
    "Metronome" to "Метроном", "Aegis" to "Эгида", "Dampener" to "Демпфер",
    "Relay" to "Реле", "Reactor" to "Реактор",
)

private val ItemComponents = mapOf(
    "Cinder" to "Искра", "Neon" to "Неон", "Gravitic" to "Гравитация",
    "Lodestar" to "Путеводная звезда", "Rime" to "Иней", "Bastion" to "Оплот",
    "Verdant" to "Росток", "Comet" to "Комета", "Frugal" to "Бережливость",
    "Hawkeye" to "Соколиный глаз", "Cataclysm" to "Катаклизм", "Trawler" to "Трал",
    "Serendipity" to "Счастливый случай", "Mnemonic" to "Память", "Alchemical" to "Алхимия",
    "Pulse" to "Импульс", "Prismatic" to "Преломление", "Adamant" to "Адамант",
    "Echo" to "Эхо", "Nova" to "Новая звезда",
)

private val GeneratedItemNames = buildMap {
    ItemNouns.forEach { (noun, russianNoun) ->
        ItemComponents.forEach { (component, russianComponent) ->
            put("$component $noun", "$russianNoun «$russianComponent»")
        }
    }
}

private val RussianContent = buildMap {
    putAll(ContentPhrasesInRussian)
    putAll(RelicPhrasesInRussian)
    putAll(ItemFamilies)
    putAll(GeneratedItemNames)
    // Canvas headings are sometimes uppercased before reaching presentation mapping.
    toMap().forEach { (english, russian) -> put(english.uppercase(), russian.uppercase()) }
}

// This grammar is the exact format emitted by DefaultCatalogData's generated item descriptions.
// Every semantic token is checked against the owner's finite catalog before translating it.
private val GeneratedItemDescription = Regex(
    "^(.+) binds the (.+) family to a (.+) component: (.+) and (.+) per stack \\(max ([0-9]+)\\)\\.$",
)
private val ModifierValue = Regex("^\\+([0-9]+(?:\\.[0-9]+)?)(%|/s|s)?$")

private fun String.generatedItemDescriptionInRussian(): String? {
    val match = GeneratedItemDescription.matchEntire(this) ?: return null
    val (name, family, component, primary, secondary, stacks) = match.destructured
    val translatedName = GeneratedItemNames[name] ?: return null
    val translatedFamily = ItemFamilies[family] ?: return null
    val translatedComponent = ItemComponents[component] ?: return null
    val translatedPrimary = primary.itemModifierInRussian() ?: return null
    val translatedSecondary = secondary.itemModifierInRussian() ?: return null
    return "$translatedName объединяет семейство «$translatedFamily» и компонент «$translatedComponent»: " +
        "$translatedPrimary и $translatedSecondary за копию (макс. $stacks)."
}

private fun String.itemModifierInRussian(): String? {
    val effect = ItemEffect.entries.firstOrNull { endsWith(" ${it.displayLabel}") } ?: return null
    val value = ModifierValue.matchEntire(removeSuffix(" ${effect.displayLabel}")) ?: return null
    val amount = value.groupValues[1].replace('.', ',')
    val suffix = when (value.groupValues[2]) {
        "/s" -> "/с"
        "s" -> " с"
        else -> value.groupValues[2]
    }
    return "${ContentPhrasesInRussian.getValue(effect.displayLabel)}: +$amount$suffix"
}
