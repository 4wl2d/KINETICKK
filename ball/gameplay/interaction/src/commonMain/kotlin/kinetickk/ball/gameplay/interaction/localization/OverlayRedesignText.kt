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
    Relics("Relics", "Реликвии"),
    Weapon("Weapon", "Оружие"),
    Stats("Stats", "Показатели"),
    Interactions("Interactions", "Взаимодействия"),
    Offerings("Offerings", "Подношения"),
    UpgradeKind("Upgrade", "Улучшение"),
    ChangeKind("Change", "Смена"),
    NewKind("New", "Новое"),
    Equipped("Equipped", "Экипировано"),
    Signature("Signature", "Фирменное"),
    MasteryInfo(
        "Weapon level stays for the whole run, even when you change weapon. Mastery milestones: {0}.",
        "Уровень оружия сохраняется до конца забега, даже при смене оружия. Этапы мастерства: {0}.",
    ),
    // Effect lines that replace the game's step-by-step choice descriptions on offers.
    FocusArtifacts("Matching artifacts and a repair", "Подходящие артефакты и ремонт"),
    FocusRelics("Relics of this aspect", "Реликвии этого аспекта"),
    FocusWeapons("Upgrade or a matching weapon", "Улучшение или подходящее оружие"),
    ChangeWeaponEffect("Other weapon systems on offer", "Другие оружейные системы на выбор"),
    MeldEffect("Raises the rank of a bound relic", "Повышает ранг связанной реликвии"),
    SalvageEffect("Excess resonance becomes matter", "Избыток резонанса становится материей"),
    ScrollableReport("Scrollable run statistics", "Прокручиваемая статистика забега"),
    Bind("Bind", "Связать"),
    Replace("Replace", "Заменить"),
    Meld("Meld", "Слить"),
    Salvage("Salvage", "Извлечь"),
    FreeSlot("Free slot", "Свободно"),
    Decline("Decline", "Отказаться"),
    LeaveHollow("Leave hollow", "Оставить пустым"),
    LeaveHollowEffect("Keep the slot empty and salvage the resonance for +{0} matter", "Слот остаётся пустым, резонанс даёт +{0} материи"),
    LeaveHeirloom("Leave the heirloom", "Оставить наследие"),
    LeaveHeirloomEffect("Start without it and gain +{0} rerolls", "Начать без него и получить +{0} обновления"),
    Heirloom("Heirloom", "Наследие"),
    // Relic links: named pairings stay unknown until met; the generic key rule is always visible.
    Harmony("Harmony", "Гармония"),
    Dissonance("Dissonance", "Диссонанс"),
    HarmonyEffect("A shared key strengthens both upsides by {0}%", "Общий ключ усиливает оба преимущества на {0}%"),
    DissonanceEffect("Opposed keys weaken both upsides by {0}%", "Противоположные ключи ослабляют оба преимущества на {0}%"),
    UnknownConcordEffect("An unknown pairing amplifies these relics", "Неизвестное сочетание усиливает эти реликвии"),
    UnknownDiscordEffect("An unknown pairing interferes with these relics", "Неизвестное сочетание мешает этим реликвиям"),
    Reborn("Reborn", "Перерождённый"),
    Risk("Risk", "Риск"),
    // Reward card blocks: a relic's mandatory cost, offers disabled by bound relics, relic links.
    Cost("Cost", "Цена"),
    Suppressed("Suppressed", "Подавлено"),
    CostStays("Cost stays {0} s", "Цена держится {0} с"),
    Upside("Upside", "Преимущество"),
    Attuned("Attuned", "Созвучие"),
    RebirthTag("Rebirth {0}", "Перерождение {0}"),
    // The Victory report's Rebirth line: the rank this clear earns and what it opens, or Ascendant.
    RebornRankEarned("Reborn rank {0} earned", "Получен ранг перерождения {0}"),
    AscendantCleared("Tier 10 cleared, Ascendant", "Ступень 10 пройдена, Вознесённый"),
    RebirthUnlocked("Unlocked: {0}", "Открыто: {0}"),
    UnlockedItems("{0} items", "предметов: {0}"),
    UnlockedMatrixNodes("{0} Matrix nodes", "узлов Матрицы: {0}"),
    UnlockedEnemies("new enemies {0}", "новые враги: {0}"),
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
