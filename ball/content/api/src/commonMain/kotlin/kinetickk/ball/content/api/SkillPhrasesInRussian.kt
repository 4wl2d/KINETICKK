// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/** Active skills: names, summaries and rank III texts. */
internal val SkillPhrasesInRussian: List<Pair<String, String>> = listOf(
    "Solar Flare" to "Солнечная вспышка",
    "Vents heat into a flare; the more heat, the more Scorch." to
        "Сбрасывает жар вспышкой: чем больше жара, тем сильнее Ожог.",
    "Enemies within two Scorch of Ignite ignite at once." to
        "Враги, которым до Воспламенения не хватает двух слоёв Ожога, сразу воспламеняются.",
    "Meteor" to "Метеор",
    "Call a meteor to the pointer; be inside to ride the blast." to
        "Вызовите метеор к указателю; окажитесь внутри, чтобы оседлать взрыв.",
    "Riding the blast also sets the Core ablaze for 3 s." to "Оседлав взрыв, Ядро ещё и пылает 3 с.",
    "Frost Line" to "Линия инея",
    "Raises a line of frost that slows enemies, stops hostile bolts and slams thrown enemies." to
        "Ставит линию инея: она замедляет врагов, останавливает снаряды и впечатывает отброшенных.",
    "Enemies on the line are embrittled while they stay on it." to "Враги на линии охрупчены, пока остаются на ней.",
    "Frost Snap" to "Морозный щелчок",
    "A freezing pulse that shatters Frozen and Brittle enemies and coils your next Dash." to
        "Морозный импульс раскалывает замёрзших и хрупких врагов и заряжает следующий рывок.",
    "Enemies within two Chill of Freeze freeze at once." to
        "Враги, которым до Заморозки не хватает двух слоёв Стужи, сразу замерзают.",
    "Arc Hook" to "Дуговой крюк",
    "Grapple the enemy nearest your pointer and fly to it; the impact counts as a Dash hit." to
        "Цепляет врага у указателя и бросает вас к нему; удар считается ударом рывком.",
    "If the target dies within 1.5 s, the hook is ready again 2.5 s after the cast." to
        "Если цель погибает за 1,5 с, крюк снова готов через 2,5 с после применения.",
    "Storm Coil" to "Грозовая катушка",
    "Plant a lightning turret at the pointer." to "Ставит у указателя молниевую турель.",
    "Zaps force Overload on enemies within two Charge of it." to
        "Удары турели вызывают Разряд у врагов, которым до него не хватает двух слоёв Заряда.",
    "Gravity Well" to "Гравитационный колодец",
    "Opens a well at the pointer that pulls enemies and Data in." to
        "Открывает у указателя колодец, который затягивает врагов и Данные.",
    "When the well closes, enemies inside with three or more Mass collapse." to
        "Когда колодец закрывается, враги внутри с тремя и более слоями Массы получают Коллапс.",
    "Mass Driver" to "Масс-драйвер",
    "Your Core turns heavy: fast impacts throw enemies and add Mass." to
        "Ядро тяжелеет: быстрые удары отшвыривают врагов и добавляют Массу.",
    "Slams while it runs add 1 Mass to both enemies." to
        "Пока навык действует, столкновения врагов добавляют обоим 1 слой Массы.",
    "Decay Spores" to "Споры распада",
    "Seed a crowd with Decay; the seeded spread rot and drop Data when they die." to
        "Засевает толпу Распадом; заражённые при гибели разносят гниль и роняют Данные.",
    "Each seeded death refunds 0.5 s to every skill, up to 3 s per cast." to
        "Каждая гибель заражённого возвращает всем навыкам 0,5 с, но не больше 3 с за применение.",
    "Entropic Tide" to "Энтропийный прилив",
    "Draws in all Decay around you: it bursts as damage and mends the Core." to
        "Вбирает весь Распад вокруг: он взрывается уроном и чинит Ядро.",
    "Healing past full integrity becomes shield." to "Лечение сверх полной прочности становится щитом.",
    "Sonic Boom" to "Звуковой удар",
    "A cone of sound blasts enemies away and erases shots; the recoil hops you back." to
        "Звуковой конус отшвыривает врагов и гасит снаряды; отдача отбрасывает вас назад.",
    "Thrown enemies ring for 0.8 s; a slam into one forces Shatter." to
        "Отброшенные враги звенят 0,8 с; удар о такого врага вызывает Раскол.",
    "Resonator" to "Резонатор",
    "Plants a resonator that pulses on the beat and replays reactions near it." to
        "Ставит резонатор: он пульсирует в такт и повторяет реакции рядом.",
    "Replays up to five reactions at 60% strength." to "Повторяет до пяти реакций с силой 60 %.",
    "Void Step" to "Шаг в пустоту",
    "Step through the void toward the pointer, untouchable for a moment; a rift detonates where you were." to
        "Шаг сквозь пустоту к указателю: на миг вы неуязвимы, а позади взрывается разлом.",
    "Enemies along the path turn Brittle and take 1 Hollow." to
        "Враги на пути становятся хрупкими и получают 1 слой Опустошения.",
    "Null Shroud" to "Нуль-покров",
    "Swallow incoming fire, then spit it back." to "Поглотите вражеский огонь и верните его.",
    "Returned shots pierce one enemy." to "Возвращённые снаряды пробивают одного врага.",
    "Slingshot" to "Праща",
    "Swing your momentum onto your aim; counts as a sharp turn." to
        "Разворачивает инерцию на прицел; считается резким поворотом.",
    "Impacts during the swing keep your momentum." to "Столкновения во время разворота не гасят вашу скорость.",
    "Full Stop" to "Полный стоп",
    "Stop dead: your speed becomes a shockwave that breaks near-full statuses." to
        "Резкая остановка: ваша скорость становится ударной волной, пробивающей почти полные статусы.",
    "Also counts as a Brake release." to "Также считается отпусканием тормоза.",
    "Catalyze" to "Катализ",
    "Every enemy near the pointer carrying two elements reacts at once." to
        "Каждый враг у указателя с двумя стихиями сразу вступает в реакцию.",
    "Forces up to ten reactions at 130% strength." to "Вызывает до десяти реакций с силой 130 %.",
    "Dual Infusion" to "Двойная инфузия",
    "Your weapon sets up reactions: hits apply your two elements." to
        "Оружие готовит реакции: попадания накладывают обе ваши стихии.",
    "Core impacts apply both elements too." to "Столкновения Ядра тоже накладывают обе стихии.",
)

/** Character Apex abilities: names and descriptions. */
internal val ApexPhrasesInRussian: List<Pair<String, String>> = listOf(
    "Perihelion" to "Перигелий",
    "For 10 s Orbit charges on half the arc and every release captures hostile shots; when it ends, they fly back." to
        "10 с орбита заряжается за полдуги, каждый выпуск захватывает вражеские снаряды, а в конце они летят обратно.",
    "Monolith" to "Монолит",
    "Restores every facet; for 8 s facets regrow fast and each blocked hit fires a refraction back." to
        "Восстанавливает все грани; 8 с они быстро отрастают, а каждый погашенный удар выпускает ответный луч.",
    "Vivisect" to "Вивисекция",
    "For 8 s Dashes cost no heat, punctures come faster and every puncture marks the next enemy." to
        "8 с рывки не стоят жара, проколы чаще, а каждый прокол помечает следующего врага.",
    "Corona" to "Солнечный венец",
    "For 10 s the halo holds its widest ring, burns three times hotter and burns away shots that cross it." to
        "10 с ореол держит самое широкое кольцо, жжёт втрое сильнее и сжигает пересекающие его снаряды.",
    "Mirror Stance" to "Зеркальная стойка",
    "For 6 s, while you brake, the next four hits are parried and returned at full Edge." to
        "6 с, пока вы тормозите, следующие четыре удара парируются и возвращаются с полной остротой.",
    "Hypercube" to "Гиперкуб",
    "Folds your lattice into a cage for 8 s: shots crossing it vanish, enemies inside slow and its edges pulse." to
        "На 8 с сворачивает решётку в клетку: пересекающие её снаряды исчезают, враги внутри замедляются, а грани пульсируют.",
    "Hypernova" to "Гиперновая",
    "Bursts into a full Supernova at once; for 8 s novas come twice as fast, hits shed no mass and novas erase shots." to
        "Мгновенно взрывается полной сверхновой; 8 с взрывы вдвое чаще, урон не отнимает массу, а сверхновые стирают снаряды.",
    "Twin Echo" to "Двойное эхо",
    "For 10 s a second ghost follows half a second behind; both strike and swallow shots three times as often." to
        "10 с за вами в полсекунды позади следует второй призрак; оба бьют и поглощают снаряды втрое чаще.",
    "Magnetar" to "Магнетар",
    "For 8 s polarity flips every second, its reach grows and incoming shots are flung back." to
        "8 с полярность меняется каждую секунду, её радиус растёт, а летящие снаряды отбрасываются назад.",
    "Crescendo" to "Крещендо",
    "For twelve beats every move lands on the beat, the streak stays full and each beat erases nearby shots." to
        "Двенадцать тактов каждое движение попадает в такт, серия полна, а каждый такт стирает ближние снаряды.",
    "Total Eclipse" to "Полное затмение",
    "For 8 s Blink recharges almost at once, each Blink cuts through shots and scars pull enemies in." to
        "8 с скачок перезаряжается почти мгновенно, каждый скачок разрезает снаряды, а шрамы стягивают врагов.",
    "Swarm Queen" to "Королева роя",
    "Fills the hive; for 10 s drones dart three times as often, intercept shots and pull Data to you." to
        "Заполняет улей; 10 с дроны атакуют втрое чаще, перехватывают снаряды и притягивают к вам Данные.",
)
