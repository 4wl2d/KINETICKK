// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/** Session copy added by the redesign: short labels, (!) explanations and the version line. */
internal enum class SessionRedesignText(
    override val english: String,
    override val russian: String,
) : TextResource {
    MATTER("Matter", "Материя"),
    FORM("Form", "Форма"),
    MAXED("Maxed", "На максимуме"),
    RANKS("Ranks", "Ранги"),
    DIRECTIVE("Directive", "Директива"),
    TIER("Tier", "Ступень"),
    RANK("Rank", "Ранг"),
    ASCENDANT("Ascendant", "Вознесённый"),
    NEW_TIER("New tier", "Новая ступень"),
    SELECTED("Selected", "Выбрана"),
    STATUS("Status", "Статус"),
    READY("Ready", "Готово"),
    AFFORDABLE("Affordable", "Доступно"),
    UPGRADES("Upgrades", "Улучшения"),
    WEAPONS_FACT("Weapons", "Оружие"),
    PERCENT("{0}%", "{0}%"),
    LANGUAGE("Language", "Язык"),
    TEXT_SIZE("Text size", "Размер текста"),

    WORKSHOP_INFO(
        "Spend Matter on characters, weapons and the Core Matrix. Everything bought here stays after every rebirth.",
        "Тратьте материю на персонажей, оружие и Матрицу Ядра. Всё купленное здесь сохраняется после перерождения.",
    ),
    REBIRTH_INFO(
        "Defeat the Architect to earn the next, harder tier and a Reborn rank; any earned tier can be chosen for the next run. Matter, Matrix nodes, unlocked weapons, discoveries and settings are kept.",
        "Победите Архитектора, чтобы открыть следующую, более сложную ступень и ранг перерождения; для забега можно выбрать любую открытую ступень. Материя, узлы матрицы, открытое оружие, находки и настройки сохраняются.",
    ),
    CODEX_INFO(
        "Everything you have found in runs: items, weapons, relics, forms, reactions and relic pairings.",
        "Всё, что вы нашли в забегах: предметы, оружие, реликвии, формы, реакции и сочетания реликвий.",
    ),
    SETTINGS_INFO(
        "Game, sound, graphics and interface options. Changes apply immediately.",
        "Параметры игры, звука, графики и интерфейса. Изменения применяются сразу.",
    ),

    VERSION("v0.5.0", "v0.5.0"),

    BACK("Back", "Назад"),
    CLOSE("Close", "Закрыть"),
    SEARCH("Search", "Поиск"),
    RARITY("Rarity", "Редкость"),
    STATS("Stats", "Характеристики"),
    MAX_STACKS("Max {0}", "Макс. {0}"),
}
