// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/** In-run HUD labels added by the redesign; casing is applied by the text style. */
internal enum class HudRedesignText(
    override val english: String,
    override val russian: String,
) : TextResource {
    LevelLabel("Lvl", "Ур."),
    WeaponLevel("Lvl {0}", "Ур. {0}"),
    Overheat("Overheat", "Перегрев"),
    ArchitectTitle("The Architect", "Архитектор"),
    AnomalyTrial("Anomaly trial", "Испытание аномалии"),
    TrialSeconds("{0} / {1} s", "{0} / {1} с"),
    RewardWeapon("Weapon", "Оружие"),
    RewardRelic("Relic", "Реликвия"),
    RewardItemAndRepair("Item + repair", "Предмет + ремонт"),
    RaceTrial("Gate race", "Гонка"),
    ChallengeTrial("Challenge", "Вызов"),
    BountyTrial("Bounty", "Охота"),
    SigilTrial("Sigil", "Печать"),
    RewardItem("Item", "Предмет"),
    RewardKeyAndRelic("Key + relic", "Ключ + реликвия"),
    RewardMatter("Matter", "Материя"),
    RewardGold("Gold", "Золото"),
    RewardSilver("Silver", "Серебро"),
    RewardBronze("Bronze", "Бронза"),
    RewardNoMedal("No medal", "Без медали"),
    RewardUnknown("Unknown", "Неизвестно"),
    TrialCount("{0} / {1}", "{0} / {1}"),
    RaceSeconds("{0} s", "{0} с"),

    // Feed toast titles for build discoveries (the nucleus sends the English key).
    ReactionDiscovered("Reaction discovered", "Реакция открыта"),
    PairingDiscovered("Pairing discovered", "Сочетание открыто"),
    IncompatibilityDiscovered("Incompatibility found", "Найдена несовместимость"),
    BreakNotice("Break", "Пробой"),

    // Pause build view sections.
    Resonance("Resonance", "Резонанс"),
    Elements("Elements", "Стихии"),
    Reactions("Reactions", "Реакции"),
    Items("Items", "Предметы"),
    ImpactPower("Impact power", "Сила удара"),
    WeaponPower("Weapon power", "Мощь оружия"),
    CriticalChance("Critical chance", "Шанс крита"),
    ShieldCapacity("Shield", "Щит"),
    UnknownReaction("?", "?"),
    NoReactions("No element pair yet", "Пока нет пары стихий"),
    NoItems("No items yet", "Пока нет предметов"),
    HollowSlot("Hollow slot", "Пустая ячейка"),
    WeaponSynergy("Weapon synergy", "Синергия оружия"),
    SynergyTags("Tags +{0}%", "Теги +{0}%"),
    SynergySignature("Signature {0}", "Фирменное {0}"),
    SynergyHarmony("Harmony: {0}", "Гармония: {0}"),
    SynergyElement("Element: {0}", "Стихия: {0}"),
    SynergyScaling("{0} {1}", "{0} {1}"),
    SynergyForms("Form {0}/3", "Форма {0}/3"),
    NoSynergy("No synergy yet", "Синергии пока нет"),

    // Run report build recap.
    TopTags("Archetype", "Архетип"),
    DominantElement("Dominant {0}", "Основа: {0}"),
    ReactionsThisRun("Reactions this run", "Реакции за забег"),
}
