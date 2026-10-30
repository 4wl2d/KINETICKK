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
    "Sonic" to "Звук",
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
    "Resonance" to "Резонанс",
    "Hollow" to "Опустошение",
    "Shatter" to "Раскол",
    "Unmake" to "Развоплощение",
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
    "Each stack makes the target take more knockback and more impact damage." to
        "Каждый слой усиливает отбрасывание цели и урон от столкновений по ней.",
    "Shatter: a shockwave throws nearby enemies away and hurts them." to
        "Раскол: ударная волна отшвыривает ближайших врагов и ранит их.",
    "Each stack weakens the target's attacks and makes it remember a share of the damage it takes." to
        "Каждый слой ослабляет атаки цели и заставляет её запоминать часть полученного урона.",
    "Unmake: a moment later the target suffers the remembered damage again, amplified." to
        "Развоплощение: мгновение спустя цель снова получает запомненный урон, причём усиленный.",

    // Reactions, their verbs and cues, and their keystone forms.
    "Thermal Shock" to "Термошок",
    "Fire meets ice: a violent blast leaves the target Brittle, and your next hit sends it flying." to
        "Огонь встречает лёд: мощный взрыв делает цель хрупкой, а следующий удар отправляет её в полёт.",
    "Spall" to "Откол",
    "Hit it into the pack" to "Отбей в толпу",
    "Flash Freeze" to "Мгновенная заморозка",
    "No throw; the next direct hit Freezes the target 0.8 s through the normal Freeze path; damage ×0.8." to
        "Без броска; следующий прямой удар замораживает цель на 0,8 с обычной Заморозкой; урон ×0,8.",
    "Plasma Burst" to "Плазменный взрыв",
    "Heat ionizes into an explosion that sets everything burning and leaves a plasma pool: inside it your " +
        "weapon fires faster and Dashes cost half the heat." to
        "Жар ионизируется во взрыв, поджигающий всё вокруг, и оставляет плазменную лужу: в ней оружие стреляет " +
        "быстрее, а рывки стоят вдвое меньше жара.",
    "Plasma Pool" to "Плазменная лужа",
    "Fight in the pool" to "Бейся в луже",
    "Arc Plasma" to "Дуговая плазма",
    "No pool; the burst repeats once on up to 2 Charged enemies within 220 at 50 % with 1 Thermal." to
        "Без лужи; взрыв один раз повторяется на 2 заряженных врагах в радиусе 220 с силой 50 % и 1 слоем Термо.",
    "Fusion" to "Синтез",
    "The target becomes a small star that drags in enemies and Data, then collapses and hurls them apart." to
        "Цель становится маленькой звездой: она затягивает врагов и Данные, а затем схлопывается и разбрасывает их.",
    "Accretion" to "Аккреция",
    "Feed the star" to "Корми звезду",
    "Dwarf Star" to "Белый карлик",
    "Lasts 3.2 s, pull 440, damage per second ×0.5; no implosion." to
        "Живёт 3,2 с, тяга 440, урон в секунду ×0,5; без схлопывания.",
    "Wildfire" to "Дикий огонь",
    "Rot catches fire and leaps to nearby enemies; for a moment everything you ram catches fire too." to
        "Гниль вспыхивает и перекидывается на ближайших врагов; ненадолго загорается и всё, во что вы врезаетесь.",
    "Ablaze" to "Объятый пламенем",
    "Dive and spread" to "Ныряй и поджигай",
    "Firewalker" to "Огнеходец",
    "Ablaze 6 s at any distance; an Ablaze contact on an enemy at Ignite threshold − 2 Scorch or more Ignites it; no spread." to
        "Пламя 6 с на любой дистанции; удар по врагу, которому до Воспламенения не больше 2 слоёв Ожога, поджигает его; без распространения.",
    "Superconduct" to "Сверхпроводимость",
    "Cold lightning races through three enemies and chills them; near you it strips the drag from your Core " +
        "and cools it." to
        "Холодная молния проносится через трёх врагов и остужает их; рядом с вами она снимает с Ядра сопротивление " +
        "и охлаждает его.",
    "Frictionless" to "Без трения",
    "Glide" to "Скользи",
    "Zero Resistance" to "Нулевое сопротивление",
    "6 links; a link on a Frozen enemy deals ×1.5 and consumes Brittle; no Frictionless." to
        "6 звеньев; звено по замёрзшему врагу бьёт ×1,5 и снимает хрупкость; без скольжения.",
    "Glacier Crush" to "Ледолом",
    "Gravity shatters the ice into a fan of shards that flies away from you and knocks enemies back." to
        "Гравитация раскалывает лёд веером осколков: они летят прочь от вас и отбрасывают врагов.",
    "Ice Fan" to "Ледяной веер",
    "Aim with your approach" to "Целься заходом",
    "Aimed Avalanche" to "Направленная лавина",
    "The fan follows your aim from the target; shards Freeze enemies with 4 or more Chill." to
        "Веер летит от цели по вашему прицелу; осколки замораживают врагов с 4 и более слоями Стужи.",
    "Embrittle" to "Охрупчивание",
    "Frozen rot: the target takes far more damage, freezes sooner and cannot stop your charge." to
        "Мёрзлая гниль: цель получает намного больше урона, быстрее замерзает и не может остановить ваш разгон.",
    "Glass Charge" to "Стеклянный таран",
    "Plow through" to "Прорывайся",
    "Glass Burst" to "Стеклянный взрыв",
    "A pierced Embrittled enemy shatters: 3 Cryo shards for 0.5 × the impact, Embrittle consumed; Embrittle lasts 2 s." to
        "Пронзённый охрупчённый враг раскалывается на 3 осколка Крио с силой 0,5 × столкновения, охрупчивание снимается; оно длится 2 с.",
    "Magnetize" to "Намагничивание",
    "The target turns magnetic: it yanks nearby enemies together and keeps pulling them and your shots for " +
        "three seconds." to
        "Цель намагничивается: она стягивает ближайших врагов и ещё три секунды притягивает их и ваши снаряды.",
    "Attractor" to "Аттрактор",
    "Shots home in" to "Снаряды летят к цели",
    "Repulsor" to "Отталкиватель",
    "No yank, no attractor; the target is hurled away from the Core at 900 × knockback and each enemy it slams takes 1 Charge." to
        "Без притяжения и аттрактора; цель отбрасывает от Ядра со скоростью 900 × отбрасывание, и каждый, в кого она врезается, получает Заряд.",
    "Feedback" to "Обратный ток",
    "Current tears through decayed armor, arcs onward spreading Entropy and kicks your weapon into its next shot." to
        "Ток прорывается сквозь истлевшую броню, бьёт дальше, распространяя Энтропию, и подгоняет ваше оружие.",
    "Overcurrent" to "Сверхток",
    "Faster weapon" to "Оружие быстрее",
    "Short Circuit" to "Короткое замыкание",
    "Refreshes actives instead: 1.0 s to every active, once per 1 s; no weapon refresh." to
        "Ускоряет активные эффекты вместо оружия: 1 с каждому, не чаще раза в 1 с.",
    "Rot Well" to "Гнилой колодец",
    "A rotting well opens on the target, pulling enemies in, slowing and decaying them; every death inside " +
        "widens it." to
        "На цели открывается гнилой колодец: он затягивает, замедляет и разлагает врагов, а каждая смерть внутри " +
        "расширяет его.",
    "Compost" to "Перегной",
    "Farm inside" to "Добивай внутри",
    "Plague Pit" to "Чумная яма",
    "Lasts 4 s; a death inside passes all its Decay to every enemy inside; no growth." to
        "Живёт 4 с; смерть внутри передаёт весь Распад всем врагам внутри; без расширения.",
    "Thunderclap" to "Громовой раскат",
    "Heat and sound hurl enemies away; Dash right on the clap to ride the blast for free." to
        "Жар и звук отшвыривают врагов; сделайте рывок точно на хлопок, чтобы бесплатно оседлать взрыв.",
    "Blast Jump" to "Взрывной прыжок",
    "Dash on the clap" to "Рывок на хлопок",
    "Rocket Clap" to "Реактивный хлопок",
    "No Dash needed: a clap within 220 launches the Core away at 560; no free Dash." to
        "Рывок не нужен: хлопок ближе 220 сам отбрасывает Ядро со скоростью 560; без бесплатного рывка.",
    "Shatterpoint" to "Точка раскола",
    "The frozen ring of sound splits into ice shards; near you they circle the Core, striking enemies and " +
        "breaking hostile shots." to
        "Застывший звон раскалывается на ледяные осколки; рядом с вами они кружат вокруг Ядра, бьют врагов и " +
        "разбивают вражеские снаряды.",
    "Shard Guard" to "Осколочный страж",
    "Get close" to "Иди вплотную",
    "Crystal Choir" to "Хрустальный хор",
    "No guard; 7 radial shards, each forces Shatter on enemies with 5 or more Resonance." to
        "Без стража; 7 осколков веером, каждый вызывает Раскол у врагов с 5 и более слоями Резонанса.",
    "Resonant Arc" to "Резонансная дуга",
    "Lightning rides the resonance through four enemies; near you, every sharp turn throws another arc." to
        "Молния бежит по резонансу через четырёх врагов; рядом с вами каждый резкий поворот выпускает новую дугу.",
    "Arc Weave" to "Плетение молний",
    "Turn to arc" to "Поворот — дуга",
    "Standing Wave" to "Стоячая волна",
    "The weave lasts 6 s at any distance; turn arcs have 2 links at 0.35 × the reaction's strength." to
        "Плетение длится 6 с на любой дистанции; дуги поворотов из 2 звеньев с силой 0,35 × реакции.",
    "Seismic" to "Сейсмический удар",
    "A quake crushes nearby enemies and stuns the ordinary ones; nearby rocks echo it." to
        "Толчок сминает ближайших врагов и оглушает обычных; ближние скалы повторяют его.",
    "Rock Echo" to "Эхо скал",
    "Fight by rocks" to "Бейся у скал",
    "Aftershock" to "Афтершок",
    "A second quake at the same spot 0.6 s later at 60 %; no rock echo." to
        "Повторный толчок на том же месте через 0,6 с с силой 60 %; без эха скал.",
    "Fracture" to "Трещина",
    "The vibration cracks rot open, spreads Decay and leaves enemies Cracked: release the Brake to make them burst." to
        "Вибрация вскрывает гниль, разносит Распад и оставляет врагов надломленными: отпустите тормоз, и они лопнут.",
    "Breakpoint" to "Точка излома",
    "Release the Brake" to "Отпусти тормоз",
    "Crevasse" to "Расщелина",
    "No Brake detonation; hit enemies are Suppressed 4 s: no aspect immunity, resistance or weakness, and armor plates crack; never on bosses." to
        "Без подрыва; задетые враги 4 с подавлены: без стихийной неуязвимости, стойкости и слабости, броня трескается; не действует на боссов.",
    "Silence" to "Безмолвие",
    "Sound falls into the void: nearby enemies take damage and a zone of silence keeps them from attacking." to
        "Звук проваливается в пустоту: ближайшие враги получают урон, а зона тишины не даёт им атаковать.",
    "Hush" to "Затишье",
    "Hold the quiet" to "Держи тишину",
    "Dead Air" to "Мёртвый эфир",
    "The hush follows the Core (radius 140, 3 s) and spends up to 20 hostile shots entering it; no damage when it forms." to
        "Тишина следует за Ядром (радиус 140, 3 с) и гасит до 20 влетающих в неё вражеских снарядов; без урона при появлении.",
    "Blackfire" to "Чёрное пламя",
    "A black flame burns the target through every resistance; when a burning enemy dies, the flame leaps on and " +
        "your actives recharge." to
        "Чёрное пламя жжёт цель сквозь любую стойкость; когда горящий враг гибнет, пламя перекидывается дальше, а " +
        "активные эффекты перезаряжаются.",
    "Flame Leap" to "Прыжок пламени",
    "Burn, then kill" to "Подожги и добей",
    "Hellfire" to "Адское пламя",
    "Damage per second ×1.5; the flame never leaps; no refund for actives." to
        "Урон в секунду ×1,5; пламя не перекидывается; активные эффекты не перезаряжаются.",
    "Stasis" to "Стазис",
    "Time stops around the target: enemies halt, hostile shots vanish, and inside the bubble your actives " +
        "recharge faster." to
        "Время вокруг цели замирает: враги застывают, вражеские снаряды исчезают, а внутри пузыря активные эффекты " +
        "перезаряжаются быстрее.",
    "Time Bubble" to "Пузырь времени",
    "Shelter inside" to "Укройся внутри",
    "Rewind" to "Перемотка",
    "Shots inside are returned as your shots (30 % of their damage, nearest enemy within 600, up to 12); no haste for actives." to
        "Снаряды внутри возвращаются вашими (30 % их урона, к ближайшему врагу в радиусе 600, до 12); без ускорения активных эффектов.",
    "Annihilate" to "Аннигиляция",
    "The void strikes the target and the toughest enemy nearby; your next Dash toward that enemy blinks you to it." to
        "Пустота бьёт по цели и по самому прочному врагу поблизости; следующий рывок в его сторону переносит вас к нему.",
    "Void Anchor" to "Якорь пустоты",
    "Dash to the doomed" to "Рывок к цели",
    "Event Strike" to "Удар горизонта",
    "Anchor blink-strikes always crit and refund their Dash heat; the anchor lasts 1.5 s." to
        "Удар через якорь всегда критический и возвращает жар рывка; якорь держится 1,5 с.",
    "Null Well" to "Нуль-колодец",
    "A well of nothing pulls enemies in, strips a share of their integrity and swallows hostile shots." to
        "Колодец небытия затягивает врагов, отнимает часть их прочности и поглощает вражеские снаряды.",
    "Maw" to "Пасть",
    "Park it in the fire" to "Подставь под огонь",
    "Hungry Void" to "Голодная пустота",
    "The well follows the Core (radius 120, 2.4 s) and stops pulling enemies; it still eats shots." to
        "Колодец следует за Ядром (радиус 120, 2,4 с) и больше не тянет врагов; снаряды он по-прежнему ест.",
    "Oblivion" to "Забвение",
    "Rot meets the void: a weakened enemy is erased, its Data rushes to you and your actives recharge; anything " +
        "sturdier takes a heavy burst." to
        "Гниль встречает пустоту: ослабленный враг стирается, его Данные летят к вам, а активные эффекты " +
        "перезаряжаются; кто покрепче, получает мощный удар.",
    "Erasure" to "Стирание",
    "Erase for Data" to "Стирай ради Данных",
    "Last Rites" to "Последний обряд",
    "Execute lines 16 % (Elites 9 %), never bosses; no Data latch, no refund for actives." to
        "Порог стирания 16 % (элита 9 %), никогда не боссы; без притяжения Данных и перезарядки активных эффектов.",

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
    "Every fourth weapon hit adds 1 stack of the weapon's element or your dominant element, and Breaks deal 25 % more damage." to
        "Каждое четвёртое попадание оружия добавляет 1 слой стихии оружия или вашей основной стихии, а пробои наносят на 25 % больше урона.",
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
    "Active recharge" to "Перезарядка активных эффектов",
    "Active power" to "Сила активных эффектов",

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
    "On active cast" to "При применении активного эффекта",
    "On active hit" to "При попадании активным эффектом",
    "On Apex" to "При Апексе",
    "On entering a field" to "При входе в поле",
    "On erasing a shot" to "При стирании снаряда",
    "On cashing a mark" to "При срабатывании метки",
    "On breaking an obstacle" to "При разрушении препятствия",
    "On boost" to "При разгоне",
    "On graze" to "При пролёте впритирку",
)
