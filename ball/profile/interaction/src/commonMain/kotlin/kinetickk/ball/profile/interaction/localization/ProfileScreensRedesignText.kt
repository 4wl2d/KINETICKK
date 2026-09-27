// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/**
 * Copy for the redesigned Armory, Lab and Rebirth screens and their shared header. Casing is
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

    LabInfo(
        "All Forms share Lab upgrades. Ranks apply at the start of every run and are kept through death, quitting and every Rebirth.",
        "Улучшения лаборатории общие для всех форм. Ранги действуют с начала каждого забега и сохраняются после гибели, выхода и перерождения.",
    ),
    UpgradesList("Upgrades", "Улучшения"),
    Now("Now", "Сейчас"),
    NextRank("Next rank", "След. ранг"),
    RankOf("Rank {0}/{1}", "Ранг {0}/{1}"),
    BuyRank("Buy rank", "Купить ранг"),
    NoValue("—", "—"),

    TierTag("Tier {0}", "Ступень {0}"),
    TierLadder("Rebirth tiers {0} to {1}, current {2}", "Ступени перерождения с {0} по {1}, текущая {2}"),
    Advance("Advance", "Перейти"),
    Confirm("Confirm", "Подтвердить"),
    Locked("Locked", "Закрыто"),
    MaxTier("Max", "Максимум"),
    RebirthKeeps(
        "Your run build resets. Matter, Lab upgrades, unlocked weapons, discoveries and settings stay.",
        "Сборка забега сбрасывается. Материя, улучшения лаборатории, открытое оружие, открытия и настройки остаются.",
    ),
    RebirthLocked(
        "Defeat the Architect on tier {0} to advance.",
        "Победите Архитектора на ступени {0}, чтобы перейти дальше.",
    ),
    RebirthMaximum("Every Rebirth tier is cleared.", "Все ступени перерождения пройдены."),
    AdvanceLabel("Rebirth", "Перерождение"),
}
