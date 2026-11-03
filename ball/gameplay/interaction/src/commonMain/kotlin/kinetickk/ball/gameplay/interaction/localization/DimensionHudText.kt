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
    CourseBroken("Course broken", "Маршрут сорван"),

    // The plate's layer gauge: what the Core's speed does in the layer.
    Grace("Grace", "Благодать"),
    Heat("Heat", "Жар"),
    Desync("Desync", "Рассинхрон"),

    // Why a rift cannot be entered now (under the rift).
    LockedEngagement("Locked until the trial ends", "Закрыт до конца испытания"),
    LockedBoss("Locked while a boss lives", "Закрыт, пока жив босс"),
    LockedArchitect("Locked, the Architect is near", "Закрыт, близится Архитектор"),

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
