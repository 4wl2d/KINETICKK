// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.foundation.diagnostics.DIAGNOSTICS_ENABLED
import kinetickk.ball.profile.api.FRAME_RATE_CAP_OPTIONS
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.REFRESH_RATE_AUTO
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplaySupport
import kinetickk.ball.profile.interaction.settings.api.SettingsLiveWindow
import kinetickk.ball.profile.interaction.settings.api.appliedInterfaceScalePercent
import kinetickk.ball.profile.interaction.settings.api.offeredInterfaceScales
import kotlin.math.abs

/** Every Settings row; entries are appended (tests and tags address rows by name, never by ordinal). */
internal enum class SettingsRow {
    LANGUAGE,
    SFX,
    MUSIC,
    MASTER_VOLUME,
    SIMULATION_SPEED,
    TEXT_SIZE,
    SCREEN_SHAKE,
    PARTICLES,
    DAMAGE_NUMBERS,
    DAMAGE_NUMBER_SIZE,
    DAMAGE_NUMBER_FORMAT,
    DAMAGE_COLOR_THRESHOLDS,
    RUN_STATISTICS_SIDE,
    COLOR_VISION,
    MINIMAP,
    PAUSE_ON_FOCUS_LOSS,
    WINDOW_MODE,
    WINDOW_SIZE,
    FULLSCREEN,
    VSYNC,
    FRAME_RATE_CAP,
    REFRESH_RATE,
    IMMERSIVE,
    INTERFACE_SCALE,
    SHOW_FPS,
    REACTION_EFFECTS,
    BIND_DASH,
    BIND_BRAKE,
    BIND_SKILL_1,
    BIND_SKILL_2,
    BIND_SKILL_3,
    BIND_APEX,
    BIND_PAUSE,
    BIND_CHOICE_1,
    BIND_CHOICE_2,
    BIND_CHOICE_3,
    BIND_CHOICE_4,
    BIND_REROLL,
    BIND_RESTART,
    BIND_PERFORMANCE,
    RESET_BINDINGS,
}

/**
 * Row controls: segmented choice, toggle, slider with −/+ steppers, one key cell ([KEYBIND]), a
 * value without a control ([READOUT]) and a two-press button ([ACTION]).
 */
internal enum class SettingsControl { SEGMENTED, TOGGLE, SLIDER, KEYBIND, READOUT, ACTION }

/** The declared control; [SettingsPlatform.control] gives the one shown on this platform. */
internal val SettingsRow.control: SettingsControl
    get() = when (this) {
        SettingsRow.LANGUAGE,
        SettingsRow.SIMULATION_SPEED,
        SettingsRow.PARTICLES,
        SettingsRow.DAMAGE_NUMBER_SIZE,
        SettingsRow.DAMAGE_NUMBER_FORMAT,
        SettingsRow.RUN_STATISTICS_SIDE,
        SettingsRow.COLOR_VISION,
        SettingsRow.WINDOW_MODE,
        SettingsRow.REFRESH_RATE,
        SettingsRow.INTERFACE_SCALE,
        SettingsRow.REACTION_EFFECTS,
        -> SettingsControl.SEGMENTED
        SettingsRow.SFX,
        SettingsRow.MUSIC,
        SettingsRow.SCREEN_SHAKE,
        SettingsRow.DAMAGE_NUMBERS,
        SettingsRow.MINIMAP,
        SettingsRow.PAUSE_ON_FOCUS_LOSS,
        SettingsRow.FULLSCREEN,
        SettingsRow.VSYNC,
        SettingsRow.IMMERSIVE,
        SettingsRow.SHOW_FPS,
        -> SettingsControl.TOGGLE
        SettingsRow.MASTER_VOLUME,
        SettingsRow.TEXT_SIZE,
        SettingsRow.DAMAGE_COLOR_THRESHOLDS,
        SettingsRow.WINDOW_SIZE,
        SettingsRow.FRAME_RATE_CAP,
        -> SettingsControl.SLIDER
        SettingsRow.BIND_DASH,
        SettingsRow.BIND_BRAKE,
        SettingsRow.BIND_SKILL_1,
        SettingsRow.BIND_SKILL_2,
        SettingsRow.BIND_SKILL_3,
        SettingsRow.BIND_APEX,
        SettingsRow.BIND_PAUSE,
        SettingsRow.BIND_CHOICE_1,
        SettingsRow.BIND_CHOICE_2,
        SettingsRow.BIND_CHOICE_3,
        SettingsRow.BIND_CHOICE_4,
        SettingsRow.BIND_REROLL,
        SettingsRow.BIND_RESTART,
        SettingsRow.BIND_PERFORMANCE,
        -> SettingsControl.KEYBIND
        SettingsRow.RESET_BINDINGS -> SettingsControl.ACTION
    }

/** The gameplay action a key binding row edits; null for every other row. */
internal val SettingsRow.boundAction: GameAction?
    get() = when (this) {
        SettingsRow.BIND_DASH -> GameAction.DASH
        SettingsRow.BIND_BRAKE -> GameAction.BRAKE
        SettingsRow.BIND_SKILL_1 -> GameAction.SKILL_1
        SettingsRow.BIND_SKILL_2 -> GameAction.SKILL_2
        SettingsRow.BIND_SKILL_3 -> GameAction.SKILL_3
        SettingsRow.BIND_APEX -> GameAction.APEX
        SettingsRow.BIND_PAUSE -> GameAction.PAUSE
        SettingsRow.BIND_CHOICE_1 -> GameAction.CHOICE_1
        SettingsRow.BIND_CHOICE_2 -> GameAction.CHOICE_2
        SettingsRow.BIND_CHOICE_3 -> GameAction.CHOICE_3
        SettingsRow.BIND_CHOICE_4 -> GameAction.CHOICE_4
        SettingsRow.BIND_REROLL -> GameAction.REROLL
        SettingsRow.BIND_RESTART -> GameAction.RESTART
        SettingsRow.BIND_PERFORMANCE -> GameAction.PERFORMANCE_HUD
        else -> null
    }

/** The key binding row of [this] action. */
internal val GameAction.bindRow: SettingsRow
    get() = when (this) {
        GameAction.DASH -> SettingsRow.BIND_DASH
        GameAction.BRAKE -> SettingsRow.BIND_BRAKE
        GameAction.SKILL_1 -> SettingsRow.BIND_SKILL_1
        GameAction.SKILL_2 -> SettingsRow.BIND_SKILL_2
        GameAction.SKILL_3 -> SettingsRow.BIND_SKILL_3
        GameAction.APEX -> SettingsRow.BIND_APEX
        GameAction.PAUSE -> SettingsRow.BIND_PAUSE
        GameAction.REROLL -> SettingsRow.BIND_REROLL
        GameAction.RESTART -> SettingsRow.BIND_RESTART
        GameAction.CHOICE_1 -> SettingsRow.BIND_CHOICE_1
        GameAction.CHOICE_2 -> SettingsRow.BIND_CHOICE_2
        GameAction.CHOICE_3 -> SettingsRow.BIND_CHOICE_3
        GameAction.CHOICE_4 -> SettingsRow.BIND_CHOICE_4
        GameAction.PERFORMANCE_HUD -> SettingsRow.BIND_PERFORMANCE
    }

/** Stable test-tag segment of a row (`kinetickk.settings.<id>.…`). */
internal val SettingsRow.tagId: String
    get() = when (this) {
        SettingsRow.COLOR_VISION -> "colorvision"
        SettingsRow.PAUSE_ON_FOCUS_LOSS -> "pauseonfocusloss"
        SettingsRow.WINDOW_MODE -> "windowmode"
        SettingsRow.WINDOW_SIZE -> "windowsize"
        SettingsRow.FULLSCREEN -> "fullscreen"
        SettingsRow.VSYNC -> "vsync"
        SettingsRow.FRAME_RATE_CAP -> "framecap"
        SettingsRow.REFRESH_RATE -> "refreshrate"
        SettingsRow.IMMERSIVE -> "immersive"
        SettingsRow.INTERFACE_SCALE -> "interfacescale"
        SettingsRow.SHOW_FPS -> "showfps"
        SettingsRow.REACTION_EFFECTS -> "reactioneffects"
        SettingsRow.RESET_BINDINGS -> "resetbindings"
        else -> if (boundAction != null) "bind." + name.removePrefix("BIND_").lowercase() else name.lowercase()
    }

internal enum class SettingsGroup(val label: ProfileText, val rows: List<SettingsRow>) {
    GAME(ProfileText.SettingsGame, listOf(SettingsRow.LANGUAGE, SettingsRow.SIMULATION_SPEED, SettingsRow.PAUSE_ON_FOCUS_LOSS)),
    SOUND(ProfileText.SettingsSound, listOf(SettingsRow.SFX, SettingsRow.MUSIC, SettingsRow.MASTER_VOLUME)),
    DISPLAY(ProfileText.SettingsDisplay, listOf(
        SettingsRow.WINDOW_MODE, SettingsRow.WINDOW_SIZE, SettingsRow.FULLSCREEN, SettingsRow.VSYNC, SettingsRow.FRAME_RATE_CAP,
        SettingsRow.REFRESH_RATE, SettingsRow.IMMERSIVE, SettingsRow.INTERFACE_SCALE, SettingsRow.SHOW_FPS,
    )),
    GRAPHICS(ProfileText.SettingsGraphics, listOf(
        SettingsRow.COLOR_VISION, SettingsRow.SCREEN_SHAKE, SettingsRow.PARTICLES, SettingsRow.REACTION_EFFECTS,
        SettingsRow.DAMAGE_NUMBERS, SettingsRow.DAMAGE_NUMBER_SIZE, SettingsRow.DAMAGE_NUMBER_FORMAT,
        SettingsRow.DAMAGE_COLOR_THRESHOLDS,
    )),
    INTERFACE(ProfileText.SettingsInterface, listOf(SettingsRow.TEXT_SIZE, SettingsRow.RUN_STATISTICS_SIDE, SettingsRow.MINIMAP)),
    CONTROLS(ProfileText.SettingsControls, listOf(
        SettingsRow.BIND_DASH, SettingsRow.BIND_BRAKE, SettingsRow.BIND_SKILL_1, SettingsRow.BIND_SKILL_2,
        SettingsRow.BIND_SKILL_3, SettingsRow.BIND_APEX, SettingsRow.BIND_PAUSE, SettingsRow.BIND_CHOICE_1,
        SettingsRow.BIND_CHOICE_2, SettingsRow.BIND_CHOICE_3, SettingsRow.BIND_CHOICE_4, SettingsRow.BIND_REROLL,
        SettingsRow.BIND_RESTART, SettingsRow.BIND_PERFORMANCE, SettingsRow.RESET_BINDINGS,
    )),
}

/**
 * Platform facts the rows read besides the Profile preferences: what this platform and window
 * offer ([support], the offered [interfaceScales]) and the live display state ([live] window,
 * [browserFullscreen]). Interaction-ephemeral; never written to Profile.
 */
internal data class SettingsPlatform(
    val support: SettingsDisplaySupport = SettingsDisplaySupport(),
    val interfaceScales: List<Int> = INTERFACE_SCALE_OPTIONS,
    val live: SettingsLiveWindow? = null,
    val browserFullscreen: Boolean = false,
    /** The interface scale the screen is already drawn at (the shell folds it into the density). */
    val screenScale: Float = 1f,
) {
    /** Whether [row] is shown here. */
    fun offers(row: SettingsRow): Boolean = when (row) {
        SettingsRow.BIND_PERFORMANCE -> DIAGNOSTICS_ENABLED && support.keyboard
        SettingsRow.WINDOW_MODE -> support.windowModes
        SettingsRow.WINDOW_SIZE -> support.windowModes && support.windowSizes.isNotEmpty()
        SettingsRow.FULLSCREEN -> support.browserFullscreen
        SettingsRow.VSYNC -> support.vsync
        SettingsRow.REFRESH_RATE -> support.refreshRatesHz.isNotEmpty() || support.refreshReadout
        SettingsRow.IMMERSIVE -> support.immersive
        SettingsRow.RESET_BINDINGS -> support.keyboard
        else -> row.boundAction == null || support.keyboard
    }

    /** The visible rows of [group], in order. */
    fun rows(group: SettingsGroup): List<SettingsRow> = group.rows.filter(::offers)

    /** The tabs shown: groups with at least one visible row. */
    val groups: List<SettingsGroup> get() = SettingsGroup.entries.filter { group -> group.rows.any(::offers) }

    /** The control [row] shows here: the refresh rate is a readout where it cannot be chosen. */
    fun control(row: SettingsRow): SettingsControl =
        if (row == SettingsRow.REFRESH_RATE && support.refreshRatesHz.isEmpty()) SettingsControl.READOUT else row.control

    /** Refresh choices: Auto, then the selectable rates. */
    val refreshChoices: List<Int> get() = listOf(REFRESH_RATE_AUTO) + support.refreshRatesHz

    /** The stored interface scale clamped to the offered options (presentation only, never written back). */
    fun appliedInterfaceScale(stored: Int): Int = appliedInterfaceScalePercent(stored, interfaceScales)

    /** The live window size when it differs from the stored preset by more than 1 dp in windowed mode, else null. */
    fun liveSizeOverride(preferences: PlayerPreferences): SettingsLiveWindow? {
        val window = live ?: return null
        if (window.mode != WindowMode.WINDOWED || preferences.windowMode != WindowMode.WINDOWED) return null
        val preset = preferences.windowSize
        val differs = abs(window.widthDp - preset.widthDp) > 1 || abs(window.heightDp - preset.heightDp) > 1
        return if (differs) window else null
    }

    /** The live window mode when it differs from the stored one, else null. */
    fun liveModeOverride(preferences: PlayerPreferences): WindowMode? =
        live?.mode?.takeIf { it != preferences.windowMode }

    companion object {
        val DEFAULT: SettingsPlatform = SettingsPlatform()
    }
}

/** Interface scales offered for a window of [widthDp] × [heightDp] (see [offeredInterfaceScales]). */
internal fun settingsInterfaceScales(widthDp: Float, heightDp: Float, touchPrimary: Boolean): List<Int> =
    offeredInterfaceScales(widthDp, heightDp, touchPrimary)

/** Frame-rate limits in stepper order: the listed limits ascending, then Unlimited (0). */
internal val FRAME_RATE_CAP_STEPS: List<Int> = FRAME_RATE_CAP_OPTIONS.filter { it > 0 } + 0

/** A Settings timestamp no animation is running from. */
internal const val SETTINGS_NEVER = -1_000_000f
