// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/**
 * Relic names, upside templates (the `{n}` placeholders stay identical to the English source),
 * liabilities and named pairings.
 */
internal val RelicPhrasesInRussian: List<Pair<String, String>> = listOf(
    "Perpetual Vector" to "Вечный вектор",
    "Drag falls by 85 %. Every 400 u/s above 600, up to 2,200, adds {0} % damage." to
        "Сопротивление снижается на 85 %. Каждые 400 ед/с сверх 600, вплоть до 2\u00A0200, добавляют {0} % урона.",
    "Brake grips half as hard, and impacts below 600 u/s deal 30 % less." to
        "Тормоз держит вдвое слабее, а столкновения на скорости ниже 600 ед/с наносят на 30 % меньше урона.",

    "Blink Drive" to "Привод скачка",
    "Dash becomes a Blink: you jump up to {1} u along your aim, stopping short of the singularity, " +
        "and strike every enemy on the line for {0} % impact damage. Blinks cost 35 % less heat." to
        "Рывок становится Скачком: вы переноситесь до {1} ед по направлению прицела, не долетая до " +
        "сингулярности, и бьёте каждого врага на линии на {0} % урона от столкновения. Скачки дают на 35 % меньше нагрева.",
    "After a Blink you keep only 35 % of your speed." to "После Скачка у вас остаётся лишь 35 % скорости.",

    "Gyre Engine" to "Вихревой двигатель",
    "Every real turn releases a shock ring for {0} % impact damage and 2 stacks of your dominant element." to
        "Каждый настоящий поворот выпускает ударное кольцо: {0} % урона от столкновения и 2 слоя вашей основной стихии.",
    "Polarity no longer recovers on its own or while braking; only turns restore it, and less than before." to
        "Полярность больше не восстанавливается сама и при торможении: её возвращают только повороты, и слабее, чем прежде.",

    "Glass Comet" to "Стеклянная комета",
    "All your damage +{0} %. Impact kills above 900 u/s grant a quarter second of invulnerability, " +
        "at most once every 1.5 seconds." to
        "Весь ваш урон +{0} %. Убийства столкновением на скорости выше 900 ед/с дают четверть секунды " +
        "неуязвимости, не чаще раза в 1,5 секунды.",
    "You take double damage, and shields and damage reduction no longer protect you." to
        "Вы получаете двойной урон, а щиты и снижение урона больше вас не защищают.",

    "Executioner's Prism" to "Призма палача",
    "Critical hits are no longer random. Every 1 % critical chance, up to 50 %, becomes {0} % critical " +
        "damage. Hits that can crit always crit against enemies below 35 % integrity, Frozen or Brittle enemies and " +
        "enemies whose status is three quarters of the way to its Break." to
        "Критические удары больше не случайны. Каждый 1 % шанса критического удара, вплоть до 50 %, " +
        "превращается в {0} % критического урона. Удары, которые могут быть критическими, всегда критические по врагам с прочностью ниже 35 %, " +
        "по замороженным и хрупким врагам и по врагам, чья стихия на три четверти заполнена до пробоя.",
    "Hits that do not crit deal 20 % less." to "Некритические удары наносят на 20 % меньше урона.",

    "Echo Chamber" to "Камера эха",
    "Weapon hits echo 0.35 seconds later for {0} % damage and apply their on-hit effects again." to
        "Попадания оружия повторяются эхом через 0,35 секунды с {0} % урона и снова применяют свои эффекты при попадании.",
    "Impact damage −50 %." to "Урон от столкновений −50 %.",

    "Furnace Covenant" to "Завет горнила",
    "Overheat never locks your Dash. Every 10 heat above 40 adds {0} % damage, and above 70 heat your " +
        "hits apply Thermal." to
        "Перегрев больше не блокирует рывок. Каждые 10 единиц нагрева сверх 40 добавляют {0} % урона, " +
        "а при нагреве выше 70 ваши удары накладывают Термо.",
    "Cooling −55 %. Above 80 heat you lose 3 integrity per second, and heat past full burns integrity instead." to
        "Охлаждение −55 %. При нагреве выше 80 вы теряете 3 прочности в секунду, а нагрев сверх предела сжигает прочность.",

    "Cinder Crown" to "Пепельная корона",
    "Thermal stacks no longer burn over time. They bank {0} % of their damage and Ignite at 5 stacks, " +
        "releasing the bank and spreading 2 Thermal." to
        "Слои Термо больше не жгут постепенно. Они копят {0} % своего урона и вызывают Воспламенение " +
        "на 5 слоях, высвобождая накопленное и распространяя 2 слоя Термо.",
    "Thermal fades twice as fast, and Ignites near you burn 4 % of your integrity." to
        "Термо рассеивается вдвое быстрее, а Воспламенения рядом с вами сжигают 4 % вашей прочности.",

    "Overdrive Cascade" to "Каскад перегрузки",
    "Overdrive gain +{0} %. Each kill during Overdrive extends it, up to 2.5 extra seconds." to
        "Накопление перегрузки +{0} %. Каждое убийство во время перегрузки продлевает её, вплоть до 2,5 дополнительной секунды.",
    "Outside Overdrive all your damage −30 %." to "Вне перегрузки весь ваш урон −30 %.",

    "Absolute Zero" to "Абсолютный ноль",
    "Impacts apply {0} Cryo and Dash chills everything around you. Frozen enemies shatter on the next " +
        "direct hit for {1} % damage, chilling their neighbors." to
        "Столкновения накладывают {0} слоя Крио, а рывок остужает всё вокруг вас. Замороженные враги " +
        "раскалываются от следующего прямого удара, нанося {1} % урона и остужая соседей.",
    "Half of the Thermal you apply turns to Cryo and the rest is lost. Overdrive gain −40 %." to
        "Половина накладываемого вами Термо превращается в Крио, остальное теряется. Накопление перегрузки −40 %.",

    "Stasis Anchor" to "Якорь стазиса",
    "While braking, enemies near you are slowed by {0} % and hostile bullets near you are caught, up to " +
        "24. Release Brake to fire them as your own. Stasis holds for 3 seconds and refills when you let go." to
        "Во время торможения враги рядом с вами замедляются на {0} %, а вражеские снаряды рядом застывают, " +
        "до 24 штук. Отпущенный тормоз выпускает их как ваши. Стазис держится 3 секунды и восполняется, " +
        "когда вы отпускаете тормоз.",
    "Weapon activation −30 % while you are not braking." to "Скорость активации оружия −30 %, пока вы не тормозите.",

    "Stormheart Capacitor" to "Грозовой конденсатор",
    "Above 700 u/s the Core charges in 4 seconds. The next impact discharges into five enemies for " +
        "{0} % impact damage and 3 Ion each." to
        "На скорости выше 700 ед/с Ядро заряжается за 4 секунды. Следующее столкновение выпускает заряд " +
        "в пять врагов: каждому {0} % урона от столкновения и 3 слоя Иона.",
    "Taking a hit while half charged or more discharges into you for 10 % of your integrity." to
        "Если вас ранят при заряде от половины, заряд бьёт по вам на 10 % вашей прочности.",

    "Aegis Inversion" to "Инверсия эгиды",
    "All healing becomes shield, and shield capacity grows by 60 % of your maximum integrity. A breaking " +
        "shield releases a nova worth {0} % of the damage it absorbed." to
        "Всё лечение становится щитом, а ёмкость щита растёт на 60 % вашей максимальной прочности. " +
        "Разрушаясь, щит выпускает вспышку силой {0} % поглощённого урона.",
    "Integrity can never be restored, and shields no longer recharge on their own." to
        "Прочность больше нельзя восстановить, а щиты перестают перезаряжаться сами.",

    "Neutron Heart" to "Нейтронное сердце",
    "Mass +{0} %, and enemies barely move you. Slow collisions hurt you only half as much." to
        "Масса +{0} %, враги почти не сдвигают вас. Медленные столкновения ранят вас вдвое слабее.",
    "Dash impulse −35 % and magnetism −20 %." to "Импульс рывка −35 %, магнетизм −20 %.",

    "Event Horizon" to "Горизонт событий",
    "Enemies within 280 u are dragged toward you. Impacts against enemies carrying Gravitic deal {0} % more, and " +
        "your Collapses implode toward you." to
        "Врагов в пределах 280 ед затягивает к вам. Столкновения с врагами под действием Гравитации наносят " +
        "на {0} % больше урона, а ваши Коллапсы схлопываются в вашу сторону.",
    "Contact damage taken ×1.5 and pickup radius halved." to "Урон от контакта с врагами ×1,5, радиус сбора уменьшен вдвое.",

    "Verdant Rot" to "Цветущая гниль",
    "Impacts apply 2 Entropy. Every Entropy stack within 320 u heals you {0} per second, up to 3.5." to
        "Столкновения накладывают 2 слоя Энтропии. Каждый слой Энтропии в пределах 320 ед лечит вас " +
        "на {0} в секунду, вплоть до 3,5.",
    "All other healing is disabled, and every Entropy stack close to you makes you take 1.5 % more damage." to
        "Любое другое лечение отключено, а каждый слой Энтропии вблизи вас увеличивает получаемый урон на 1,5 %.",

    "Pandemic Codex" to "Кодекс пандемии",
    "Statuses are contagious: when an enemy dies, {0} % of each of its statuses jumps to its two nearest " +
        "neighbors." to
        "Стихии заразны: когда враг погибает, {0} % каждой его стихии перескакивает на двух ближайших соседей.",
    "Enemies carrying a status move 20 % faster." to "Враги под действием стихии двигаются на 20 % быстрее.",

    "Agony Scepter" to "Скипетр агонии",
    "Your weapon takes its Forbidden form. Every rank deepens the mutation." to
        "Ваше оружие принимает Запретную форму. Каждый ранг углубляет мутацию.",
    "Weapon activations add heat: 6 per shot, or 5 per second for continuous weapons." to
        "Активации оружия добавляют нагрев: 6 за выстрел или 5 в секунду у непрерывного оружия.",

    "Hollow Crown" to "Полая корона",
    "Each empty Relic slot grants {0} % damage and 8 % activation speed." to
        "Каждая пустая ячейка реликвий даёт {0} % урона и 8 % скорости активации.",
    "It never ranks up, every other bound Relic is 25 % weaker, and each empty slot makes you take 10 % " +
        "more damage." to
        "Она никогда не повышает ранг, все остальные привязанные реликвии слабее на 25 %, а каждая пустая " +
        "ячейка увеличивает получаемый урон на 10 %.",

    "Ouroboros Seal" to "Печать Уробороса",
    "Once per run, lethal damage instead restores your integrity, releases a nova for {0} damage and " +
        "grants two seconds of invulnerability. Then the seal is spent but keeps its slot." to
        "Раз за забег смертельный урон вместо гибели восстанавливает вашу прочность, выпускает вспышку " +
        "на {0} урона и даёт две секунды неуязвимости. После этого печать истощается, но сохраняет ячейку.",
    "Data gain −25 % while the seal is bound. The singularity still kills, and a spent seal locks its " +
        "slot for two minutes." to
        "Получение данных −25 %, пока печать привязана. Сингулярность по-прежнему убивает, а истощённая " +
        "печать блокирует свою ячейку на две минуты.",

    // Named relic-to-relic pairings.
    "Endless Gyre" to "Бесконечный вихрь",
    "Above 1,200 u/s every turn fires a second, wider ring at 60 % damage a moment later." to
        "На скорости выше 1\u00A0200 ед/с каждый поворот мгновение спустя выпускает второе, более широкое кольцо с 60 % урона.",
    "Assassin's Line" to "Линия убийцы",
    "Blink strikes always crit and leave their targets Brittle." to "Удары Скачка всегда критические и делают цели хрупкими.",
    "Meltdown" to "Расплав",
    "Overdrive starts at full heat, and the heat bonus counts half again during Overdrive." to
        "Перегрузка начинается при полном нагреве, а во время неё бонус от нагрева увеличивается в полтора раза.",
    "Cold Verdict" to "Холодный приговор",
    "Shatters always crit, and hits on enemies with 4 or more Cryo always crit." to
        "Раскалывания всегда критические, как и удары по врагам, на которых от 4 слоёв Крио.",
    "Lightning Rod" to "Громоотвод",
    "Above 1,400 u/s the capacitor charges 50 % faster, and a discharge throws you forward." to
        "На скорости выше 1\u00A0400 ед/с конденсатор заряжается на 50 % быстрее, а выпущенный заряд бросает вас вперёд.",
    "Black Star" to "Чёрная звезда",
    "Collapses at the Core strike 50 % harder, but hostile bullets hit you 50 % harder." to
        "Коллапсы у Ядра бьют на 50 % сильнее, но и вражеские снаряды ранят вас на 50 % сильнее.",
    "Blight Garden" to "Сад порчи",
    "Contagion passes 1 extra Entropy, and every Rupture near you heals 4." to
        "Заражение передаёт на 1 слой Энтропии больше, а каждый Разрыв рядом с вами лечит на 4.",
    "Firestorm" to "Огненный шторм",
    "A dying carrier detonates half of its bank around it." to "Погибающий носитель взрывает вокруг себя половину накопленного.",
    "Forbidden Echo" to "Запретное эхо",
    "Echoes strike 20 % harder and apply 1 stack of your dominant element." to
        "Эхо бьёт на 20 % сильнее и накладывает 1 слой вашей основной стихии.",
    "Rot Ward" to "Гнилостный оберег",
    "The rot feeds the inverted shield, and every nova applies 3 Entropy." to
        "Гниль питает инвертированный щит, а каждая вспышка накладывает 3 слоя Энтропии.",
    "Still Waters" to "Тихий омут",
    "Released bullets apply 2 Cryo, and stasis slows 10 % more." to
        "Выпущенные снаряды накладывают 2 слоя Крио, а стазис замедляет на 10 % сильнее.",
    "Fold Vector" to "Свёрнутый вектор",
    "Blinks keep 55 % of your speed, but lose their heat discount." to "Скачки сохраняют 55 % вашей скорости, но теряют скидку на нагрев.",
    "Hollow Vow" to "Пустая клятва",
    "The seal fills your shield and restores 30 % of your integrity." to "Печать заполняет ваш щит и восстанавливает 30 % прочности.",
    "Faraday Cage" to "Клетка Фарадея",
    "A self-discharge strikes your shield first, and every nova applies 3 Ion." to
        "Заряд, бьющий по вам, сначала попадает в щит, а каждая вспышка накладывает 3 слоя Иона.",
    "Thermal Paradox" to "Тепловой парадокс",
    "The furnace's heat bonus is halved, but every converted Thermal stack vents 3 heat." to
        "Бонус горнила от нагрева уменьшен вдвое, но каждый преобразованный слой Термо снижает нагрев на 3.",
    "Extinguished" to "Угасание",
    "The crown banks Cryo instead, and every Freeze releases the bank at 60 %." to
        "Корона копит Крио вместо Термо, а каждая Заморозка высвобождает 60 % накопленного.",
    "Shattered Aegis" to "Разбитая эгида",
    "Glass ignores shields: inverted healing becomes 1 % damage per 3 points instead, lost when hit." to
        "Стекло не признаёт щитов: инвертированное лечение вместо этого даёт 1 % урона за каждые 3 единицы " +
        "и пропадает при получении удара.",
    "Contradiction" to "Противоречие",
    "Stasis only holds while you move slower than 400 u/s." to "Стазис держится, только пока вы движетесь медленнее 400 ед/с.",
    "Heavy Blink" to "Тяжёлый скачок",
    "Blinks travel 35 % less far, but their strikes use twice your mass." to
        "Скачки переносят на 35 % ближе, но их удары используют вдвое большую массу.",
    "Fragile Rebirth" to "Хрупкое возрождение",
    "The seal restores only half your integrity, but its nova deals double damage." to
        "Печать восстанавливает лишь половину прочности, но её вспышка наносит двойной урон.",
)
