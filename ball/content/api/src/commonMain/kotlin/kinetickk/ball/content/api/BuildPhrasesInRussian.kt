// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/**
 * Build vocabulary: elements, reactions, archetypes, affinity, Relic aspects and keys, offer focus,
 * timed stats and trigger labels. Terms stay consistent with the generated rules text.
 */
internal val BuildPhrasesInRussian: List<Pair<String, String>> = listOf(
    // Elements, their status labels and Breaks.
    "Kinetic" to "Кинетика",
    "Thermal" to "Термо",
    "Cryo" to "Крио",
    "Ion" to "Ион",
    "Gravitic" to "Гравитация",
    "Entropy" to "Энтропия",
    "Sovereign" to "Владычество",
    "Scorch" to "Ожог",
    "Chill" to "Стужа",
    "Charge" to "Заряд",
    "Mass" to "Масса",
    "Decay" to "Распад",
    "Ignite" to "Воспламенение",
    "Freeze" to "Заморозка",
    "Overload" to "Разряд",
    "Collapse" to "Коллапс",
    "Rupture" to "Разрыв",
    "Each stack burns the target every second." to "Каждый слой каждую секунду обжигает цель.",
    "Ignite: the stacks explode around the target." to "Воспламенение: слои взрываются вокруг цели.",
    "Each stack slows the target." to "Каждый слой замедляет цель.",
    "Freeze: the target stops and becomes Brittle; the next direct hit strikes harder. A thawed enemy resists the next Freeze for a while." to
        "Заморозка: цель застывает и становится хрупкой, следующий прямой удар бьёт сильнее. Оттаявший враг какое-то время сопротивляется новой Заморозке.",
    "Direct hits on a charged target arc to a nearby enemy." to "Прямые удары по заряженной цели перескакивают дугой на ближайшего врага.",
    "Overload: lightning chains through three enemies and charges them." to "Разряд: молния проходит цепью через трёх врагов и заряжает их.",
    "Each stack makes the target take more impact damage and drift toward other marked enemies." to
        "Каждый слой увеличивает урон от столкновений по цели и тянет её к другим отмеченным врагам.",
    "Collapse: nearby enemies are dragged into the target and crushed together." to
        "Коллапс: ближайших врагов затягивает к цели и сминает вместе с ней.",
    "Each stack makes the target take more damage from every source." to "Каждый слой увеличивает весь урон, который получает цель.",
    "Rupture: the target loses a share of its maximum integrity." to "Разрыв: цель теряет часть своей максимальной прочности.",

    // Reactions.
    "Thermal Shock" to "Термошок",
    "Fire meets ice: a violent single-target blast that leaves the target Brittle." to
        "Огонь встречает лёд: мощный взрыв по одной цели делает её хрупкой.",
    "Plasma Burst" to "Плазменный взрыв",
    "Heat ionizes into an explosion that sets everything it reaches burning." to
        "Жар ионизируется и взрывается, поджигая всё, до чего дотягивается.",
    "Fusion" to "Синтез",
    "The target becomes a small star that drags enemies in and burns them." to
        "Цель превращается в маленькую звезду, которая затягивает врагов и сжигает их.",
    "Wildfire" to "Дикий огонь",
    "Rot catches fire and leaps to nearby enemies as Thermal." to "Гниль вспыхивает и перекидывается на ближайших врагов слоями Термо.",
    "Superconduct" to "Сверхпроводимость",
    "Cold lightning races through three enemies and chills them." to "Холодная молния проносится через трёх врагов и остужает их.",
    "Glacier Crush" to "Ледолом",
    "Gravity shatters the ice into piercing shards." to "Гравитация раскалывает лёд на пронзающие осколки.",
    "Embrittle" to "Охрупчивание",
    "Frozen rot: the target takes far more damage and freezes sooner." to "Мёрзлая гниль: цель получает намного больше урона и быстрее замерзает.",
    "Magnetize" to "Намагничивание",
    "The target turns magnetic and yanks nearby enemies into a collision." to
        "Цель намагничивается и резко стягивает ближайших врагов в столкновение.",
    "Feedback" to "Обратный ток",
    "Current tears through decayed armor and arcs onward, spreading Entropy." to
        "Ток прорывается сквозь истлевшую броню и бьёт дальше, распространяя Энтропию.",
    "Rot Well" to "Гнилой колодец",
    "A rotting well opens on the target, pulling enemies in and decaying them." to
        "На цели открывается гнилой колодец: он затягивает врагов и разлагает их.",

    // Archetype tags and resonance tiers.
    "Ram" to "Таран",
    "Drive" to "Порыв",
    "Anchor" to "Якорь",
    "Arsenal" to "Арсенал",
    "Precision" to "Точность",
    "Bulwark" to "Бастион",
    "Overclock" to "Форсаж",
    "Chain" to "Цепь",
    "Fortune" to "Фортуна",
    "Collisions, mass and raw momentum." to "Столкновения, масса и чистый импульс.",
    "Impact damage +15 %." to "Урон от столкновений +15 %.",
    "Impacts above 900 u/s splash 35 % of their damage around the target." to
        "Столкновения на скорости выше 900 ед/с наносят 35 % своего урона вокруг цели.",
    "Above 1,200 u/s impacts keep your momentum and deal 25 % more." to
        "На скорости выше 1\u00A0200 ед/с столкновения сохраняют ваш импульс и наносят на 25 % больше урона.",
    "Speed, Dash and the line you carve." to "Скорость, рывок и линия, которую вы прочерчиваете.",
    "Dash heat −15 %." to "Нагрев рывка −15 %.",
    "Dash hits apply 2 stacks of your dominant element." to "Попадания рывком накладывают 2 слоя вашей основной стихии.",
    "The first Dash hit of each Dash refunds 60 % of its heat." to "Первое попадание каждого рывка возвращает 60 % его нагрева.",
    "Brake, control and the art of stopping." to "Торможение, контроль и искусство остановки.",
    "Brake cools 50 % faster." to "Торможение охлаждает на 50 % быстрее.",
    "Releasing Brake pushes enemies away and splashes 40 % impact damage." to
        "Отпущенный тормоз отталкивает врагов и наносит вокруг 40 % урона от столкновения.",
    "Releasing Brake breaks every status on nearby enemies that is at least 60 % full." to
        "Отпущенный тормоз вызывает пробой каждой стихии на ближайших врагах, заполненной хотя бы на 60 %.",
    "The weapon system and its cadence." to "Оружейная система и её ритм.",
    "Activation speed +10 %." to "Скорость активации +10 %.",
    "Weapon power +20 %." to "Мощность оружия +20 %.",
    "Every third weapon hit applies 1 stack of your dominant element." to
        "Каждое третье попадание оружия накладывает 1 слой вашей основной стихии.",
    "Critical hits and weak points." to "Критические удары и слабые места.",
    "Critical chance +6 %." to "Шанс критического удара +6 %.",
    "Critical hits deepen every status on the target by 1 stack." to "Критические удары добавляют по 1 слою каждой стихии на цели.",
    "Critical hits against enemies with a status deal 40 % more." to
        "Критические удары по врагам под действием стихии наносят на 40 % больше урона.",
    "Shields, integrity and recovery." to "Щиты, прочность и восстановление.",
    "Shield capacity +18." to "Ёмкость щита +18.",
    "Shields start recharging after 2.2 seconds instead of 4." to "Щиты начинают восстанавливаться через 2,2 секунды вместо 4.",
    "A breaking shield grants a brief invulnerability, once every 20 seconds." to
        "Разрушенный щит дарует краткую неуязвимость, не чаще раза в 20 секунд.",
    "Heat, cooling and Overdrive." to "Нагрев, охлаждение и перегрузка.",
    "Cooling +15 %." to "Охлаждение +15 %.",
    "Overdrive gain +30 %." to "Накопление перегрузки +30 %.",
    "Entering Overdrive vents all heat and adds 2 seconds." to "Вход в перегрузку сбрасывает весь нагрев и добавляет 2 секунды.",
    "Kills, combos and momentum between targets." to "Убийства, комбо и импульс между целями.",
    "Combo window +1 second." to "Окно комбо +1 секунда.",
    "Every twelfth combo kill bursts around the victim." to "Каждое двенадцатое убийство в комбо взрывается вокруг жертвы.",
    "A lapsing combo halves instead of resetting." to "Прерванное комбо уменьшается вдвое, а не сбрасывается.",
    "Data, Matter, luck and choice." to "Данные, материя, удача и выбор.",
    "Luck +0.2." to "Удача +0,2.",
    "One extra reroll every four levels." to "Один дополнительный переброс каждые четыре уровня.",
    "Reborn cards appear 10 % more often, even before the first Rebirth." to
        "Перерождённые карты появляются на 10 % чаще, даже до первого Перерождения.",

    // Element affinity tiers.
    "Statuses of this element are 25 % stronger." to "Эффекты этой стихии сильнее на 25 %.",
    "Its Break needs one stack less and hits 40 % harder." to "Её пробою нужно на один слой меньше, и он бьёт на 40 % сильнее.",
    "Overflow: its Breaks splash 2 stacks to nearby enemies." to "Перелив: её пробои переносят 2 слоя на ближайших врагов.",

    // Relic resonance keys. Motion and Shield share the general vocabulary.
    "Stillness" to "Покой",
    "Heat" to "Жар",
    "Cold" to "Холод",
    "Gravity" to "Тяготение",
    "Life" to "Жизнь",
    "Fragile" to "Хрупкость",
    "Echo" to "Эхо",
    "Critical" to "Критический",
    "Spread" to "Заражение",
    "Void" to "Пустота",

    // Timed stats that are not item effects.
    "Damage" to "Урон",
    "Damage taken" to "Получаемый урон",

    // Clause trigger labels.
    "On impact" to "При столкновении",
    "On weapon hit" to "При попадании оружия",
    "On critical hit" to "При критическом ударе",
    "On kill" to "При убийстве",
    "On dash" to "При рывке",
    "On dash hit" to "При попадании рывком",
    "On brake release" to "При отпускании тормоза",
    "On turn" to "При повороте",
    "When hit" to "При получении урона",
    "On shield break" to "При разрушении щита",
    "On Overdrive" to "При перегрузке",
    "On overheat" to "При перегреве",
    "On reaction" to "При реакции",
    "On break" to "При пробое",
    "On level up" to "При повышении уровня",
    "On data pickup" to "При сборе данных",
    "On ricochet" to "При рикошете",
    "On slam" to "При впечатывании врага",
)
