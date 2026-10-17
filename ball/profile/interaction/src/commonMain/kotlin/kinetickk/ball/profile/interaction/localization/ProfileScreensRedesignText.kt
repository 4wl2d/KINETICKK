// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/**
 * Copy for the redesigned Armory, Matrix and Rebirth screens and their shared header. Casing is
 * applied by the text style, so the strings stay in sentence case.
 */
internal enum class ProfileScreensRedesignText(
    override val english: String,
    override val russian: String,
) : TextResource {
    Back("Back", "Назад"),
    Matter("Matter", "Материя"),
    CountOf("{0}/{1}", "{0}/{1}"),
    PageStep("Page {0} of {1}", "Страница {0} из {1}"),

    ArmoryInfo(
        "Unlock a weapon with Matter to start runs with it. Every Core can use every weapon.",
        "Откройте оружие за материю, чтобы начинать с ним забеги. Любое ядро может использовать любое оружие.",
    ),
    WeaponsList("Weapons", "Оружие"),
    Starter("Starter", "Стартовое"),
    Owned("Owned", "Открыто"),
    ActiveRun("In this run", "В этом забеге"),
    CostMatter("{0} Matter", "{0} материи"),
    Unlock("Unlock", "Открыть"),
    SetAsStarter("Set as starter", "Сделать стартовым"),
    NeedMore("Need {0} more matter", "Не хватает {0} материи"),
    Mastery("Mastery", "Мастерство"),
    MasteryInfo(
        "Mastery climbs with the weapon level during a run. Each milestone adds damage and activation speed, and it resets with the run build.",
        "Мастерство растёт вместе с уровнем оружия в забеге. Каждая ступень добавляет урон и скорость активации и сбрасывается вместе со сборкой.",
    ),
    MasteryLevel("Lvl {0}", "Ур. {0}"),
    MasteryBase("Base", "База"),

    MatrixInfo(
        "The Core Matrix holds permanent upgrades bought with Matter. Every Form shares them and they apply from the start of every run. A node opens once any node joined to it from above is owned; Reset returns all the Matter for free.",
        "Матрица Ядра хранит постоянные улучшения за материю. Они общие для всех форм и действуют с начала каждого забега. Узел открывается, когда куплен любой связанный с ним узел выше; сброс бесплатно возвращает всю материю.",
    ),
    MatrixNodes("Matrix nodes", "Узлы матрицы"),
    RankOf("Rank {0}/{1}", "Ранг {0}/{1}"),
    BuyRank("Buy rank", "Купить ранг"),
    NoValue("—", "—"),
    Reset("Reset", "Сброс"),
    ResetRefund("Reset refunds {0} Matter", "Сброс вернёт {0} материи"),
    Keystone("Keystone", "Ключевой узел"),
    HangarInfo(
        "Choose the character for your next run. Each form has its own mechanic and traits; new forms unlock with achievements and Reborn ranks.",
        "Выберите персонажа для следующего забега. У каждой формы своя механика и особенности; новые формы открываются достижениями и рангами перерождения.",
    ),
    HangarRoster("Characters", "Персонажи"),
    HangarSelect("Select", "Выбрать"),
    HangarSelected("Selected", "Выбран"),
    HangarAvailable("Available", "Доступен"),
    HangarLocked("Locked", "Закрыт"),
    HangarMass("Mass", "Масса"),
    HangarIntegrity("Integrity", "Прочность"),
    HangarHandling("Handling", "Управление"),
    HangarHeat("Heat", "Нагрев"),
    HangarDifficulty("Difficulty", "Сложность"),
    HangarSignature("Signature", "Фирменное"),
    HangarSignaturePending("Signature weapons arrive later", "Фирменное оружие появится позже"),
    HangarUnlockedBy("Unlocked: {0}", "Открыт: {0}"),
    HangarProgress("{0}: {1}/{2}", "{0}: {1}/{2}"),
    RebornLock("Reborn {0}", "Перерождение {0}"),
    RebornLockDetail("Reach Rebirth rank {0} to open this node.", "Узел откроется на ранге перерождения {0}."),
    PrerequisiteLock("Locked", "Закрыто"),
    RequiresNode("Requires {0}.", "Нужен узел «{0}»."),
    RequiresEitherNode("Requires {0} or {1}.", "Нужен узел «{0}» или «{1}»."),
    NodeAvailable("Available", "Доступно"),
    NodeMaxed("Every rank owned.", "Все ранги куплены."),

    TierTag("Tier {0}", "Ступень {0}"),
    TierLadder("Rebirth tiers {0} to {1}, current {2}", "Ступени перерождения с {0} по {1}, текущая {2}"),
    Advance("Advance", "Перейти"),
    Confirm("Confirm", "Подтвердить"),
    Locked("Locked", "Закрыто"),
    MaxTier("Max", "Максимум"),
    RebirthKeeps(
        "Your run build resets. Matter, Matrix nodes, unlocked weapons, discoveries and settings stay.",
        "Сборка забега сбрасывается. Материя, узлы матрицы, открытое оружие, открытия и настройки остаются.",
    ),
    RebirthLocked(
        "Defeat the Architect on tier {0} to advance.",
        "Победите Архитектора на ступени {0}, чтобы перейти дальше.",
    ),
    RebirthMaximum("Every Rebirth tier is cleared.", "Все ступени перерождения пройдены."),
    AdvanceLabel("Rebirth", "Перерождение"),
    BuildUnlocks("Build unlocks", "Новое для сборки"),
    RebornCards("Reborn cards", "Перерождённые карты"),
    PercentValue("{0}%", "{0}%"),
    UnlockRelic("Relic", "Реликвия"),
    UnlockItem("Item", "Предмет"),
    Heirlooms("Heirlooms", "Наследие"),
    RebirthBuildInfo(
        "Reborn cards are stronger copies of items, and their tags count twice. Relics bound when the Architect falls become Heirlooms: from tier 1 on, the next run opens with a choice of them.",
        "Перерождённые карты сильнее обычных предметов, а их метки считаются дважды. Реликвии, привязанные при победе над Архитектором, становятся наследием: со ступени 1 следующий забег начинается с выбора из них.",
    ),
    BuildSummary("{0}. {1} {2}, next {3}.", "{0}. {1} {2}, далее {3}."),
}
