// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/** Session copy added by the redesign: short labels, (!) explanations and the clean legal line. */
internal enum class SessionRedesignText(
    override val english: String,
    override val russian: String,
) : TextResource {
    MATTER("Matter", "Материя"),
    FORM("Form", "Форма"),
    STARTING_WEAPON("Starting weapon", "Стартовое оружие"),
    MAXED("Maxed", "На максимуме"),
    DIRECTIVE("Directive", "Директива"),
    STATUS("Status", "Статус"),
    READY("Ready", "Готово"),
    UNLOCKABLE("Unlockable", "Можно открыть"),
    LANGUAGE("Language", "Язык"),
    TEXT_SIZE("Text size", "Размер текста"),

    LAB_INFO(
        "Permanent upgrades bought with Matter. Every form shares them, and they stay after every rebirth.",
        "Постоянные улучшения за материю. Они действуют на все формы и сохраняются после перерождения.",
    ),
    ARMORY_INFO(
        "Unlock starting weapons with Matter. Every form can use every weapon.",
        "Открывайте стартовое оружие за материю. Любая форма может использовать любое оружие.",
    ),
    REBIRTH_INFO(
        "Defeat the Architect to open a harder tier. Your run build resets. Matter, upgrades, unlocked weapons, discoveries and settings are kept.",
        "Победите Архитектора, чтобы открыть более сложный уровень. Сборка забега сбрасывается. Материя, улучшения, открытое оружие, находки и настройки сохраняются.",
    ),
    CODEX_INFO(
        "Everything you have found in runs: items, weapons, relics, forms and synergies.",
        "Всё, что вы нашли в забегах: предметы, оружие, реликвии, формы и синергии.",
    ),
    SETTINGS_INFO(
        "Game, sound, graphics and interface options. Changes apply immediately.",
        "Параметры игры, звука, графики и интерфейса. Изменения применяются сразу.",
    ),

    VERSION("v0.2.0", "v0.2.0"),
    COPYRIGHT("© 2026 Vladislav Tomilov", "© 2026 Владислав Томилов"),
    LICENSE("GPL v3+", "GPL v3+"),
    SOURCE("github.com/4wl2d/KINETICKK", "github.com/4wl2d/KINETICKK"),

    BACK("Back", "Назад"),
    CLOSE("Close", "Закрыть"),
    SEARCH("Search", "Поиск"),
    RARITY("Rarity", "Редкость"),
    STATS("Stats", "Характеристики"),
    MAX_STACKS("Max {0}", "Макс. {0}"),
}
