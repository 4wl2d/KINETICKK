// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/**
 * The dimensions' interface copy: the trial panel's label and reward names, and the dimension reward overlay (its
 * headings, the card bands and the Pact's price lines). Layer, objective and boon names stay content keys. Sentence
 * case (the text styles apply display casing), no separators or arrows; Russian keeps `%` attached to its number.
 */
internal enum class DimensionHudText(
    override val english: String,
    override val russian: String,
) : TextResource {
    // The trial panel during a visit.
    Objective("Objective", "Цель"),
    RewardBlessing("Blessing", "Благословение"),
    RewardPact("Pact", "Сделка"),
    RewardLegendaryItem("Legendary item", "Легендарный предмет"),

    // The dimension reward overlay.
    BlessingHeading("Choose a blessing", "Выберите благословение"),
    PactHeading("Strike a pact", "Заключите сделку"),
    ArchiveHeading("Archive trove", "Сокровищница Архива"),
    BoonBlessing("Blessing", "Благословение"),
    BoonPact("Pact", "Сделка"),
    BoonAspect("Dungeon aspect", "Аспект подземелья"),
    EnemyIntegrityUp("Enemy integrity +{0}%", "Прочность врагов +{0}%"),
    EnemySpeedUp("Enemy speed +{0}%", "Скорость врагов +{0}%"),
    DamageTakenUp("Damage taken +{0}%", "Получаемый урон +{0}%"),
    RefuseEffect("Keep the run as it is and salvage +{0} matter", "Оставить забег как есть и получить +{0} материи"),
}
