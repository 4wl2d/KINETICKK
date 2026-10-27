// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/** Settings copy added by the redesign: Color vision, row explanations and preview labels. */
internal enum class SettingsRedesignText(override val english: String, override val russian: String) : TextResource {
    Back("Back", "Назад"),
    ColorVision("Color vision", "Цветовое зрение"),
    ColorVisionDefault("Default", "Обычное"),
    ColorVisionProtan("Protan", "Протан"),
    ColorVisionDeutan("Deutan", "Дейтан"),
    ColorVisionTritan("Tritan", "Тритан"),
    ColorVisionMono("Mono", "Моно"),
    AboutColorVision(
        "Remaps you, threat, heat, shield and polarity to pairs you can tell apart. Mono adds hatching to every threat.",
        "Перекрашивает вас, угрозы, нагрев, щит и полярность в пары цветов, которые легко различить. В режиме «Моно» все угрозы дополнительно заштрихованы.",
    ),
    AboutLanguage("Changes the language of every menu and of the run.", "Меняет язык всех меню и забега."),
    AboutSimulationSpeed(
        "Scales the whole simulation, run clock included.",
        "Ускоряет или замедляет всю симуляцию вместе с часами забега.",
    ),
    AboutSfx("Sound effects in runs and menus.", "Звуковые эффекты в забеге и в меню."),
    AboutMusic("Background music.", "Фоновая музыка."),
    AboutMasterVolume(
        "Overall level of sound effects and music. M mutes both anywhere.",
        "Общая громкость эффектов и музыки. M выключает их в любой момент.",
    ),
    AboutTextSize("Scales the text of menus and of the run.", "Масштаб текста в меню и в забеге."),
    AboutScreenShake("Camera shake on impacts and blasts.", "Тряска камеры при ударах и взрывах."),
    AboutParticles(
        "How many decorative particles, stars and fragments are drawn.",
        "Сколько декоративных частиц, звёзд и осколков рисуется.",
    ),
    AboutDamageNumbers("Damage you deal pops up as numbers.", "Нанесённый урон всплывает числами."),
    AboutDamageNumberSize(
        "Base size of damage numbers. Stronger hits still draw larger.",
        "Базовый размер чисел урона. Сильные удары всё равно крупнее.",
    ),
    AboutDamageNumberFormat(
        "Compact shortens large numbers. Full shows every digit.",
        "Кратко сокращает большие числа. Полно показывает все цифры.",
    ),
    AboutDamageColorTiers(
        "Damage numbers change color at this value, at four times it and at twenty times it.",
        "Числа урона меняют цвет на этом значении, на вчетверо большем и на вдвадцатеро большем.",
    ),
    Minimap("Minimap", "Миникарта"),
    AboutMinimap(
        "Shows nearby obstacles, enemies and anomalies in a corner of the run.",
        "Показывает в углу экрана ближайшие препятствия, врагов и аномалии.",
    ),
    AboutRunStatisticsSide(
        "Where the run statistics sit on the run report.",
        "С какой стороны отчёта о забеге стоит статистика.",
    ),
    RoleYou("You", "Вы"),
    RoleThreat("Threat", "Угроза"),
    RoleHeat("Heat", "Нагрев"),
    RoleShield("Shield", "Щит"),
    RolePolarity("Polarity", "Полярность"),
    MasterShort("Master", "Общая"),
    Page("Page {0} of {1}", "Страница {0} из {1}"),

    // DISPLAY
    WindowMode("Window mode", "Режим окна"),
    WindowModeWindowed("Windowed", "Окно"),
    WindowModeBorderless("Borderless", "Без рамки"),
    WindowModeFullscreen("Fullscreen", "Полный экран"),
    AboutWindowMode(
        "Windowed keeps the title bar. Borderless fills the screen without one. Fullscreen gives the whole display to the game.",
        "В режиме «Окно» есть заголовок. «Без рамки» заполняет экран без заголовка. «Полный экран» отдаёт игре весь дисплей.",
    ),
    WindowSize("Window size", "Размер окна"),
    WindowSizeValue("{0}×{1}", "{0}×{1}"),
    AboutWindowSize(
        "Size of the game window in windowed mode. Sizes larger than this screen are not offered.",
        "Размер окна игры в оконном режиме. Размеры больше этого экрана не предлагаются.",
    ),
    Fullscreen("Fullscreen", "Полный экран"),
    AboutFullscreen(
        "Fills the screen with the game. The browser's Esc key returns to the page.",
        "Игра занимает весь экран. Клавиша Esc в браузере возвращает обычный вид.",
    ),
    Vsync("V-Sync", "Вертикальная синхронизация"),
    AboutVsync(
        "Waits for the display before showing each frame: no tearing, and the frame rate stays at or below the refresh rate. Changing it reopens the window; your run is kept.",
        "Ждёт дисплей перед показом каждого кадра: без разрывов изображения, а частота кадров не выше частоты экрана. При изменении окно открывается заново, забег сохраняется.",
    ),
    FrameRateCap("Frame rate limit", "Ограничение кадров"),
    FrameRateValue("{0} FPS", "{0} к/с"),
    FrameRateUnlimited("Unlimited", "Без лимита"),
    AboutFrameRateCap(
        "The most frames the game draws per second. Lower limits save power; the game plays the same at any frame rate.",
        "Сколько кадров в секунду игра рисует не больше. Низкий предел экономит энергию; игра идёт одинаково при любой частоте кадров.",
    ),
    RefreshRate("Refresh rate", "Частота обновления"),
    RefreshAuto("Auto", "Авто"),
    RefreshValue("{0} Hz", "{0} Гц"),
    RefreshUnknown("—", "—"),
    AboutRefreshRateAndroid(
        "How often the screen redraws. Auto picks the highest rate the screen supports.",
        "Как часто обновляется экран. «Авто» выбирает наибольшую поддерживаемую частоту.",
    ),
    AboutRefreshRateDesktop(
        "Your monitor's current refresh rate. It is set in the system display settings.",
        "Текущая частота обновления монитора. Она задаётся в настройках дисплея системы.",
    ),
    Immersive("Hide system bars", "Скрыть системные панели"),
    AboutImmersive(
        "Hides the status and navigation bars. A swipe from the screen edge shows them for a moment.",
        "Скрывает строку состояния и панель навигации. Свайп от края экрана ненадолго показывает их.",
    ),
    InterfaceScale("Interface scale", "Масштаб интерфейса"),
    AboutInterfaceScale(
        "Scales menus and the run's controls and readouts. How much of the arena you see does not change.",
        "Масштабирует меню, кнопки и показатели забега. Видимая часть арены не меняется.",
    ),
    ShowFps("Show FPS", "Показывать FPS"),
    AboutShowFps(
        "Shows the frame rate in a corner of the run. Pressing it opens the full performance panel.",
        "Показывает частоту кадров в углу экрана во время забега. Нажатие открывает подробную панель производительности.",
    ),
    PauseOnFocusLoss("Pause when unfocused", "Пауза без фокуса"),
    AboutPauseOnFocusLoss(
        "Pauses the run when the game window loses focus or the app goes to the background.",
        "Ставит забег на паузу, когда окно игры теряет фокус или приложение уходит в фон.",
    ),
    ReactionEffects("Reaction effects", "Эффекты реакций"),
    ReactionFull("Full", "Полные"),
    ReactionReduced("Reduced", "Меньше"),
    ReactionMinimal("Minimal", "Минимум"),
    AboutReactionEffects(
        "How much of each elemental reaction is animated. Every reaction stays recognizable at every level.",
        "Насколько подробно анимируются реакции стихий. Каждая реакция остаётся узнаваемой на любом уровне.",
    ),
    PreviewPlay("Play", "Играть"),

    // CONTROLS
    ActionDash("Dash", "Рывок"),
    ActionBrake("Brake", "Тормоз"),
    ActionSkill("Active {0}", "Активный эффект {0}"),
    ActionApex("Apex", "Апекс"),
    ActionPause("Pause", "Пауза"),
    ActionChoice("Choice {0}", "Выбор {0}"),
    ActionReroll("Reroll", "Обновить"),
    ActionRestart("Restart", "Заново"),
    ActionPerformance("Performance panel", "Производительность"),
    AboutBindDash("Spends heat for a burst of speed.", "Тратит нагрев на резкое ускорение."),
    AboutBindBrake("Hold to slow the Core down.", "Удерживайте, чтобы замедлить Ядро."),
    AboutBindSkill("Casts the active effect in slot {0}.", "Применяет активный эффект из ячейки {0}."),
    AboutBindApex("Casts your character's Apex once it is ready.", "Применяет Апекс персонажа, когда он готов."),
    AboutBindPause("Pauses and resumes the run. Esc always works too.", "Ставит забег на паузу и снимает её. Esc работает всегда."),
    AboutBindReroll("Redraws the offered cards.", "Заново раскладывает предложенные карты."),
    AboutBindRestart("Starts a new run after the run ends.", "Начинает новый забег после окончания."),
    AboutBindChoice("Takes card {0} of the offer.", "Берёт карту {0} из предложения."),
    AboutBindPerformance(
        "Shows or hides the full performance panel.",
        "Показывает или скрывает подробную панель производительности.",
    ),
    ResetBindings("Reset keys", "Сбросить клавиши"),
    ResetBindingsButton("Reset", "Сбросить"),
    ResetBindingsConfirm("Confirm", "Подтвердить"),
    AboutResetBindings(
        "Choose an action, then press its new key. Esc cancels. A key that is already taken swaps places. Reset returns every key to its default.",
        "Выберите действие и нажмите новую клавишу. Esc отменяет выбор. Если клавиша занята, действия меняются клавишами. Сброс возвращает все клавиши по умолчанию.",
    ),
    KeyCell("{0}: {1}", "{0}: {1}"),
    WaitingForKey("Waiting for a key", "Ожидание клавиши"),
    Swapped("Swapped", "Поменяно"),
    KeyUnavailable("Key not available", "Эта клавиша недоступна"),
    KeysReset("Keys reset", "Клавиши сброшены"),
    KeySetTo("{0} set to {1}", "{0}: {1}"),
    KeyMovedTo("{0} moved to {1}", "{0}: перенесено на {1}"),
}
