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
}
