// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/**
 * Copy of the run-variety overlays: the omen offer and its run conditions, the event signal cards and the pause
 * Conditions. Content names (omens, personalities, signal kinds, world events) stay content keys; this is the copy the
 * interaction words around them. Sentence case, no separators or arrows; Russian keeps `%` attached and seconds «с».
 * A number a card shows from the nucleus (a price, a stake, a payout) is an argument; the numbers written into the
 * copy are the shipped signal tuning's.
 */
internal enum class RunVarietyText(
    override val english: String,
    override val russian: String,
) : TextResource {
    ChooseOmen("Choose an omen", "Выберите знамение"),
    OmenBand("Omen", "Знамение"),
    NoOmen("No omen", "Без знамения"),
    NoOmenText("Start the run as it is", "Начать забег как есть"),
    ConditionPattern("Pattern", "Тактика"),
    ConditionKeynote("Keynote", "Лейтмотив"),
    ConditionOmen("Omen", "Знамение"),
    ConditionEvent("Event", "Событие"),
    Conditions("Conditions", "Условия"),
    OfferAtLeast("An item offer of {0} or better", "Предложение предметов: «{0}» и выше"),
    OfferLegendary("A Legendary item offer", "Предложение легендарных предметов"),
    PriceMatter("{0} matter", "{0} материи"),
    CostIntegrity("Costs {0} integrity", "Стоит {0} прочности"),
    MerchantRareTitle("Rare offer", "Редкое предложение"),
    MerchantEpicTitle("Epic offer", "Эпическое предложение"),
    MerchantRerollsTitle("Reroll bundle", "Набор обновлений"),
    MerchantRerollsText("+{0} rerolls", "+{0} обновления"),
    ShrineBloodTitle("Blood blessing", "Кровное благословение"),
    ShrineBloodText("+10% impact damage and +10% weapon power for the rest of the run", "+10% урона от столкновений и +10% силы оружия до конца забега"),
    ShrineWardTitle("Ward blessing", "Охранное благословение"),
    ShrineWardText("+25 shield capacity and +4% damage reduction", "+25 к ёмкости щита и +4% к снижению урона"),
    ShrineWardCost("−20 maximum integrity", "−20 к максимальной прочности"),
    ShrineRelicTitle("Relic rite", "Обряд реликвии"),
    RelicOffer("A Relic offer", "Предложение реликвии"),
    GambleRerollTitle("Wager a reroll", "Поставить обновление"),
    GambleRerollText("Even odds to win three rerolls", "Равные шансы выиграть три обновления"),
    GambleRerollStake("Stake: 1 reroll", "Ставка: 1 обновление"),
    GambleMatterTitle("Wager matter", "Поставить материю"),
    GambleMatterText("45% odds to win {0} matter", "Шанс 45% выиграть {0} материи"),
    GambleMatterStake("Stake: {0} matter", "Ставка: {0} материи"),
    GambleIntegrityTitle("Wager integrity", "Поставить прочность"),
    GambleIntegrityText("Even odds of an Epic offer", "Равные шансы на эпическое предложение"),
    GambleIntegrityStake("Stake: {0} integrity", "Ставка: {0} прочности"),
    CacheCrateTitle("Sealed crate", "Запечатанный ящик"),
    CacheCrateCurse("Curse: +25% damage taken for 150 s", "Проклятие: +25% получаемого урона на 150 с"),
    CacheReliquaryTitle("Black reliquary", "Чёрный реликварий"),
    CacheReliquaryCurse("Curse: −20% hit damage for 240 s", "Проклятие: −20% урона от попаданий на 240 с"),
    CacheIdolTitle("Bound idol", "Скованный идол"),
    CacheIdolCurse("Curse: −40% cooling for 150 s", "Проклятие: −40% охлаждения на 150 с"),
    DerelictRepairTitle("Patch the hull", "Залатать корпус"),
    DerelictRepairText("Restore {0} integrity", "Восстановить {0} прочности"),
    DerelictCargoTitle("Strip the cargo", "Забрать груз"),
    DerelictCargoText("+{0} matter", "+{0} материи"),
    DerelictVaultTitle("Pry the vault", "Вскрыть сейф"),
    DerelictVaultText("Drops a Key", "Оставляет ключ"),
    Ignore("Ignore", "Пройти мимо"),
    IgnoreText("Leave the signal behind", "Оставить сигнал позади"),
}
