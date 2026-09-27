// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/** Settings copy added by the redesign: Color vision, row explanations and preview labels. */
internal enum class SettingsRedesignText(override val english: String, override val russian: String) : TextResource {
    Back("Back", "Назад"),
    ColorVision("Color vision", "Цветовое зрение"),
    ColorVisionDefault("Default", "Обычное"),
    ColorVisionProtan("Protan", "Протан"),
    ColorVisionDeutan("Deutan", "Дейтан"),
    ColorVisionTritan("Tritan", "Тритан"),
    ColorVisionMono("Mono", "Моно"),
    AboutColorVision(
        "Remaps you, threat, heat, shield and polarity to pairs you can tell apart. Mono adds hatching to every threat.",
        "Перекрашивает вас, угрозы, нагрев, щит и полярность в пары цветов, которые легко различить. Моно добавляет штриховку на каждую угрозу.",
    ),
    AboutLanguage("Changes the language of every menu and of the run.", "Меняет язык всех меню и забега."),
    AboutSimulationSpeed(
        "Scales the whole simulation, run clock included.",
        "Ускоряет или замедляет всю симуляцию вместе с часами забега.",
    ),
    AboutSfx("Sound effects in runs and menus.", "Звуковые эффекты в забеге и в меню."),
    AboutMusic("Background music.", "Фоновая музыка."),
    AboutMasterVolume(
        "Overall level of sound effects and music. M mutes both anywhere.",
        "Общая громкость эффектов и музыки. M выключает их в любой момент.",
    ),
    AboutTextSize("Scales the text of menus and of the run.", "Масштаб текста в меню и в забеге."),
    AboutScreenShake("Camera shake on impacts and blasts.", "Тряска камеры при ударах и взрывах."),
    AboutParticles(
        "How many decorative particles, stars and fragments are drawn.",
        "Сколько декоративных частиц, звёзд и осколков рисуется.",
    ),
    AboutDamageNumbers("Damage you deal pops up as numbers.", "Нанесённый урон всплывает числами."),
    AboutDamageNumberSize(
        "Base size of damage numbers. Stronger hits still draw larger.",
        "Базовый размер чисел урона. Сильные удары всё равно крупнее.",
    ),
    AboutDamageNumberFormat(
        "Compact shortens large numbers. Full shows every digit.",
        "Кратко сокращает большие числа. Полно показывает все цифры.",
    ),
    AboutDamageColorTiers(
        "Damage numbers change color at this value, at four times it and at twenty times it.",
        "Числа урона меняют цвет на этом значении, на вчетверо большем и на вдвадцатеро большем.",
    ),
    AboutRunStatisticsSide(
        "Where the run statistics sit on the run report.",
        "С какой стороны отчёта о забеге стоит статистика.",
    ),
    RoleYou("You", "Вы"),
    RoleThreat("Threat", "Угроза"),
    RoleHeat("Heat", "Нагрев"),
    RoleShield("Shield", "Щит"),
    RolePolarity("Polarity", "Полярность"),
    MasterShort("Master", "Общая"),
    Page("Page {0} of {1}", "Страница {0} из {1}"),
}
