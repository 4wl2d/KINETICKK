// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.localizedContent
import kinetickk.foundation.common.localization.AppLanguage

internal fun String.rewardLabel(language: AppLanguage): String =
    if (language == AppLanguage.Russian) RewardLabelsInRussian[this] ?: localizedContent(language) else this

private val RewardLabelsInRussian = mapOf(
    "Drones" to "Дроны",
    "Blades" to "Клинки",
    "Chain targets" to "Цели цепи",
    "Projectiles" to "Снаряды",
    "Ricochets" to "Рикошеты",
    "Damage · speed ≥500" to "Урон · скорость ≥500",
    "Damage · speed ≥1600" to "Урон · скорость ≥1600",
    "Damage · dash" to "Урон рывка",
    "Radius · dash" to "Радиус рывка",
    "Damage · enemy speed ≥170" to "Урон · скорость врага ≥170",
    "Activation · fast kill, 3s" to "Активация · быстрое убийство, 3 с",
    "Next hit · braking, max" to "Следующий удар · тормоз, макс.",
    "Damage · zero polarity" to "Урон · нулевая полярность",
    "Pull · hit" to "Притяжение при попадании",
    "Damage · kill" to "Урон при убийстве",
    "Radius · kill" to "Радиус при убийстве",
    "Damage · distance >300" to "Урон · дистанция >300",
    "Damage · distance ≤155" to "Урон · дистанция ≤155",
    "Damage · current mass" to "Урон от текущей массы",
    "Knockback · hit" to "Отбрасывание при попадании",
    "Damage · per hit, max 5" to "Урон за попадание, до 5",
    "Arc · hit, every 0.28s" to "Дуга при попадании · 0,28 с",
    "Arc targets · every 7 hits" to "Цели дуги · каждые 7 ударов",
    "Damage · every 5 hits" to "Урон · каждые 5 ударов",
    "Discharge radius" to "Радиус разряда",
    "Damage · first hit" to "Урон первого попадания",
    "Slow · first hit" to "Замедление · первый удар",
    "Damage · isolated target" to "Урон одиночной цели",
    "Arc · every 4 fast hits" to "Дуга · 4 удара на скорости",
    "Arc range" to "Дальность дуги",
    "Repeat damage · after 0.45s" to "Повтор урона через 0,45 с",
    "Repeat damage · every 7 hits" to "Повтор урона · 7 ударов",
    "Cooldown removed · kill" to "Сброс перезарядки · убийство",
    "Rupture · every 6 hits" to "Разрыв · каждые 6 ударов",
    "Seeking shard · kill" to "Самонаводящийся осколок · убийство",
    "Activation · hurt, 2.5s" to "Активация · ранение, 2,5 с",
    "Damage · exposed, 3s" to "Урон по уязвимой цели · 3 с",
    "Critical chance · injured target" to "Шанс крита · раненая цель",
    "2 refractions · every 6 hits" to "2 отражения · 6 ударов",
    "Critical damage bonus" to "Бонус критического урона",
    "Shield · first hit" to "Щит за первый удар",
    "Overdrive · shield full" to "Перегрузка · щит полон",
    "Mirrored damage · critical" to "Отражение критического урона",
    "Rupture · every 5 hits" to "Разрыв · каждые 5 ударов",
    "Stored damage · hit" to "Накопление урона за удар",
    "Decay · 3s, max 5 stacks" to "Распад · 3 с, до 5 копий",
    "Slow · kill" to "Замедление при убийстве",
    "Integrity + shield · every 3 kills" to "Прочность и щит · 3 убийства",
    "Damage · elite / boss, now" to "Урон элите / боссу сейчас",
    "Damage · integrity ≤35%" to "Урон · прочность ≤35%",
    "Activation · integrity ≤35%" to "Активация · прочность ≤35%",
    "Weapon mutation rank" to "Ранг мутации оружия",
    "Damage · matrix aspects" to "Урон от аспектов матрицы",
    "Mirrored damage · first hit" to "Отражение первого удара",
    "Activation · overdrive" to "Активация при перегрузке",
)
