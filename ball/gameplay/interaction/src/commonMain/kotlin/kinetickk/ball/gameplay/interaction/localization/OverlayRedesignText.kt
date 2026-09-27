// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/**
 * Copy for the redesigned in-run overlays (rewards, pause, run report). Sentence case: the text
 * styles apply display casing. No separators, arrows or index numbers.
 */
internal enum class OverlayRedesignText(
    override val english: String,
    override val russian: String,
) : TextResource {
    LevelLabel("Lvl", "Ур."),
    Take("Take", "Взять"),
    Reroll("Reroll", "Обновить"),
    Build("Build", "Сборка"),
    Codex("Codex", "Кодекс"),
    Rank("Rank {0}", "Ранг {0}"),
    RankLabel("Rank", "Ранг"),
    StackCount("Stack {0}/{1}", "Копии {0}/{1}"),
    FamilyLabel("{0} family", "Семейство «{0}»"),
    Relics("Relics", "Реликвии"),
    Weapon("Weapon", "Оружие"),
    Stats("Stats", "Показатели"),
    Interactions("Interactions", "Взаимодействия"),
    Offerings("Offerings", "Подношения"),
    UpgradeKind("Upgrade", "Улучшение"),
    ChangeKind("Change", "Смена"),
    NewKind("New", "Новое"),
    Equipped("Equipped", "Экипировано"),
    MasteryInfo(
        "Weapon level stays for the whole run, even when you change weapon. Mastery milestones: {0}.",
        "Уровень оружия сохраняется до конца забега, даже при смене оружия. Этапы мастерства: {0}.",
    ),
    Bind("Bind", "Связать"),
    Replace("Replace", "Заменить"),
    Meld("Meld", "Слить"),
    Salvage("Salvage", "Извлечь"),
    FreeSlot("Free slot", "Свободно"),
    RebirthTag("Rebirth {0}", "Перерождение {0}"),
    VictoryTitleTop("Architect", "Архитектор"),
    VictoryTitleBottom("Dismantled", "Разобран"),
    DefeatTitleTop("Core", "Ядро"),
    DefeatTitleBottom("Broken", "Разбито"),
    MatterBanked("Matter banked", "Материя в банк"),
    Bank("Bank", "Банк"),
    BankInfo(
        "Matter banks on victory, on death and when you quit a run.",
        "Материя попадает в банк при победе, при гибели и при выходе из забега.",
    ),
    Menu("Menu", "Меню"),
    RunTimeShort("Run time", "Время"),
    DestroyedShort("Destroyed", "Уничтожено"),
    ElitesShort("Elites", "Элита"),
    MatterShort("Matter", "Материя"),
    ChainShort("Best chain", "Лучшая серия"),
    DamageShort("Damage", "Урон"),
}
