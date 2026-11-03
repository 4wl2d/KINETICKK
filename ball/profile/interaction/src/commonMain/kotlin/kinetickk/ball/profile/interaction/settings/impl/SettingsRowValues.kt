// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.graphics.Color
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.label
import kinetickk.foundation.design.formatCompact
import kinetickk.foundation.design.formatMultiplier
import kotlin.math.roundToInt

internal fun SettingsRow.label(language: AppLanguage): String = when (this) {
    SettingsRow.LANGUAGE -> language.text(ProfileText.Language)
    SettingsRow.SFX -> language.text(ProfileText.Sfx)
    SettingsRow.MUSIC -> language.text(ProfileText.Music)
    SettingsRow.MASTER_VOLUME -> language.text(ProfileText.MasterVolume)
    SettingsRow.SIMULATION_SPEED -> language.text(ProfileText.SimulationSpeed)
    SettingsRow.TEXT_SIZE -> language.text(ProfileText.TextSize)
    SettingsRow.SCREEN_SHAKE -> language.text(ProfileText.ScreenShake)
    SettingsRow.PARTICLES -> language.text(ProfileText.Particles)
    SettingsRow.DAMAGE_NUMBERS -> language.text(ProfileText.DamageNumbers)
    SettingsRow.DAMAGE_NUMBER_SIZE -> language.text(ProfileText.DamageNumberSize)
    SettingsRow.DAMAGE_NUMBER_FORMAT -> language.text(ProfileText.DamageNumberFormat)
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> language.text(ProfileText.DamageColorTiers)
    SettingsRow.RUN_STATISTICS_SIDE -> language.text(ProfileText.RunStatisticsSide)
    SettingsRow.COLOR_VISION -> language.text(SettingsRedesignText.ColorVision)
    SettingsRow.MINIMAP -> language.text(SettingsRedesignText.Minimap)
    SettingsRow.PAUSE_ON_FOCUS_LOSS -> language.text(SettingsRedesignText.PauseOnFocusLoss)
    SettingsRow.WINDOW_MODE -> language.text(SettingsRedesignText.WindowMode)
    SettingsRow.WINDOW_SIZE -> language.text(SettingsRedesignText.WindowSize)
    SettingsRow.FULLSCREEN -> language.text(SettingsRedesignText.Fullscreen)
    SettingsRow.VSYNC -> language.text(SettingsRedesignText.Vsync)
    SettingsRow.FRAME_RATE_CAP -> language.text(SettingsRedesignText.FrameRateCap)
    SettingsRow.REFRESH_RATE -> language.text(SettingsRedesignText.RefreshRate)
    SettingsRow.IMMERSIVE -> language.text(SettingsRedesignText.Immersive)
    SettingsRow.INTERFACE_SCALE -> language.text(SettingsRedesignText.InterfaceScale)
    SettingsRow.SHOW_FPS -> language.text(SettingsRedesignText.ShowFps)
    SettingsRow.REACTION_EFFECTS -> language.text(SettingsRedesignText.ReactionEffects)
    SettingsRow.RESET_BINDINGS -> language.text(SettingsRedesignText.ResetBindings)
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
    -> settingsActionName(checkNotNull(boundAction), language)
}

/** The name of a rebindable gameplay action ("Active 2" / «Активный эффект 2»). */
internal fun settingsActionName(action: GameAction, language: AppLanguage): String = when (action) {
    GameAction.DASH -> language.text(SettingsRedesignText.ActionDash)
    GameAction.BRAKE -> language.text(SettingsRedesignText.ActionBrake)
    GameAction.SKILL_1 -> language.text(SettingsRedesignText.ActionSkill, 1)
    GameAction.SKILL_2 -> language.text(SettingsRedesignText.ActionSkill, 2)
    GameAction.SKILL_3 -> language.text(SettingsRedesignText.ActionSkill, 3)
    GameAction.APEX -> language.text(SettingsRedesignText.ActionApex)
    GameAction.PAUSE -> language.text(SettingsRedesignText.ActionPause)
    GameAction.REROLL -> language.text(SettingsRedesignText.ActionReroll)
    GameAction.RESTART -> language.text(SettingsRedesignText.ActionRestart)
    GameAction.CHOICE_1 -> language.text(SettingsRedesignText.ActionChoice, 1)
    GameAction.CHOICE_2 -> language.text(SettingsRedesignText.ActionChoice, 2)
    GameAction.CHOICE_3 -> language.text(SettingsRedesignText.ActionChoice, 3)
    GameAction.CHOICE_4 -> language.text(SettingsRedesignText.ActionChoice, 4)
    GameAction.PERFORMANCE_HUD -> language.text(SettingsRedesignText.ActionPerformance)
}

/**
 * The row's (!) explanation; on key binding rows the key cell's accessibility hint. The refresh
 * rate explains a choice where [platform] offers one and the monitor readout elsewhere.
 */
internal fun SettingsRow.about(language: AppLanguage, platform: SettingsPlatform = SettingsPlatform.DEFAULT): String = when (this) {
    SettingsRow.LANGUAGE -> language.text(SettingsRedesignText.AboutLanguage)
    SettingsRow.SFX -> language.text(SettingsRedesignText.AboutSfx)
    SettingsRow.MUSIC -> language.text(SettingsRedesignText.AboutMusic)
    SettingsRow.MASTER_VOLUME -> language.text(SettingsRedesignText.AboutMasterVolume)
    SettingsRow.SIMULATION_SPEED -> language.text(SettingsRedesignText.AboutSimulationSpeed)
    SettingsRow.TEXT_SIZE -> language.text(SettingsRedesignText.AboutTextSize)
    SettingsRow.SCREEN_SHAKE -> language.text(SettingsRedesignText.AboutScreenShake)
    SettingsRow.PARTICLES -> language.text(SettingsRedesignText.AboutParticles)
    SettingsRow.DAMAGE_NUMBERS -> language.text(SettingsRedesignText.AboutDamageNumbers)
    SettingsRow.DAMAGE_NUMBER_SIZE -> language.text(SettingsRedesignText.AboutDamageNumberSize)
    SettingsRow.DAMAGE_NUMBER_FORMAT -> language.text(SettingsRedesignText.AboutDamageNumberFormat)
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> language.text(SettingsRedesignText.AboutDamageColorTiers)
    SettingsRow.RUN_STATISTICS_SIDE -> language.text(SettingsRedesignText.AboutRunStatisticsSide)
    SettingsRow.COLOR_VISION -> language.text(SettingsRedesignText.AboutColorVision)
    SettingsRow.MINIMAP -> language.text(SettingsRedesignText.AboutMinimap)
    SettingsRow.PAUSE_ON_FOCUS_LOSS -> language.text(SettingsRedesignText.AboutPauseOnFocusLoss)
    SettingsRow.WINDOW_MODE -> language.text(SettingsRedesignText.AboutWindowMode)
    SettingsRow.WINDOW_SIZE -> language.text(SettingsRedesignText.AboutWindowSize)
    SettingsRow.FULLSCREEN -> language.text(SettingsRedesignText.AboutFullscreen)
    SettingsRow.VSYNC -> language.text(SettingsRedesignText.AboutVsync)
    SettingsRow.FRAME_RATE_CAP -> language.text(SettingsRedesignText.AboutFrameRateCap)
    SettingsRow.REFRESH_RATE -> language.text(
        if (platform.control(this) == SettingsControl.READOUT) SettingsRedesignText.AboutRefreshRateDesktop
        else SettingsRedesignText.AboutRefreshRateAndroid,
    )
    SettingsRow.IMMERSIVE -> language.text(SettingsRedesignText.AboutImmersive)
    SettingsRow.INTERFACE_SCALE -> language.text(SettingsRedesignText.AboutInterfaceScale)
    SettingsRow.SHOW_FPS -> language.text(SettingsRedesignText.AboutShowFps)
    SettingsRow.REACTION_EFFECTS -> language.text(SettingsRedesignText.AboutReactionEffects)
    SettingsRow.RESET_BINDINGS -> language.text(SettingsRedesignText.AboutResetBindings)
    SettingsRow.BIND_DASH -> language.text(SettingsRedesignText.AboutBindDash)
    SettingsRow.BIND_BRAKE -> language.text(SettingsRedesignText.AboutBindBrake)
    SettingsRow.BIND_SKILL_1 -> language.text(SettingsRedesignText.AboutBindSkill, 1)
    SettingsRow.BIND_SKILL_2 -> language.text(SettingsRedesignText.AboutBindSkill, 2)
    SettingsRow.BIND_SKILL_3 -> language.text(SettingsRedesignText.AboutBindSkill, 3)
    SettingsRow.BIND_APEX -> language.text(SettingsRedesignText.AboutBindApex)
    SettingsRow.BIND_PAUSE -> language.text(SettingsRedesignText.AboutBindPause)
    SettingsRow.BIND_CHOICE_1 -> language.text(SettingsRedesignText.AboutBindChoice, 1)
    SettingsRow.BIND_CHOICE_2 -> language.text(SettingsRedesignText.AboutBindChoice, 2)
    SettingsRow.BIND_CHOICE_3 -> language.text(SettingsRedesignText.AboutBindChoice, 3)
    SettingsRow.BIND_CHOICE_4 -> language.text(SettingsRedesignText.AboutBindChoice, 4)
    SettingsRow.BIND_REROLL -> language.text(SettingsRedesignText.AboutBindReroll)
    SettingsRow.BIND_RESTART -> language.text(SettingsRedesignText.AboutBindRestart)
    SettingsRow.BIND_PERFORMANCE -> language.text(SettingsRedesignText.AboutBindPerformance)
}

internal fun SettingsRow.isOn(preferences: PlayerPreferences, platform: SettingsPlatform = SettingsPlatform.DEFAULT): Boolean = when (this) {
    SettingsRow.SFX -> preferences.soundEnabled
    SettingsRow.MUSIC -> preferences.musicEnabled
    SettingsRow.SCREEN_SHAKE -> preferences.screenShake
    SettingsRow.DAMAGE_NUMBERS -> preferences.damageNumbers
    SettingsRow.MINIMAP -> preferences.minimapEnabled
    SettingsRow.PAUSE_ON_FOCUS_LOSS -> preferences.pauseOnFocusLoss
    SettingsRow.VSYNC -> preferences.vsync
    SettingsRow.IMMERSIVE -> preferences.immersive
    SettingsRow.SHOW_FPS -> preferences.showFps
    // Live browser state, never stored.
    SettingsRow.FULLSCREEN -> platform.browserFullscreen
    else -> false
}

/** False while the row's control is disabled: the window size outside windowed mode. */
internal fun SettingsRow.isEnabled(preferences: PlayerPreferences): Boolean =
    this != SettingsRow.WINDOW_SIZE || preferences.windowMode == WindowMode.WINDOWED

/** Whether Reset keys can be pressed: some key is not its default. */
internal fun settingsResetAvailable(preferences: PlayerPreferences, platform: SettingsPlatform): Boolean =
    platform.offers(SettingsRow.RESET_BINDINGS) && !preferences.keyBindings.isDefault

/** Slider position 0..1 of a slider row. */
internal fun SettingsRow.sliderValue(preferences: PlayerPreferences, platform: SettingsPlatform = SettingsPlatform.DEFAULT): Float = when (this) {
    SettingsRow.MASTER_VOLUME -> preferences.masterVolume
    SettingsRow.TEXT_SIZE -> (preferences.textScale - 1f) / 0.75f
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> thresholdIndex(preferences).toFloat() / DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.lastIndex
    SettingsRow.WINDOW_SIZE -> settingsWindowSizePosition(preferences, platform)
    SettingsRow.FRAME_RATE_CAP -> FRAME_RATE_CAP_STEPS.indexOf(preferences.frameRateCap).coerceAtLeast(0).toFloat() / FRAME_RATE_CAP_STEPS.lastIndex
    else -> 0f
}.coerceIn(0f, 1f)

/** Whether one step in [direction] still changes the value (the stepper greys out at the ends). */
internal fun SettingsRow.canStep(
    preferences: PlayerPreferences,
    direction: Int,
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): Boolean = when (this) {
    SettingsRow.WINDOW_SIZE -> settingsWindowSizeStep(preferences, platform, direction) != null
    SettingsRow.FRAME_RATE_CAP -> settingsFrameRateCapStep(preferences, direction) != null
    else -> {
        val value = sliderValue(preferences, platform)
        if (direction < 0) value > 0f else value < 1f
    }
}

/** Short value next to a slider: percent, the first damage color threshold, a window size or a frame limit. */
internal fun SettingsRow.sliderOutput(
    preferences: PlayerPreferences,
    language: AppLanguage,
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): String = when (this) {
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> formatCompact(preferences.damageNumberTierThreshold.toLong(), language)
    SettingsRow.WINDOW_SIZE -> platform.liveSizeOverride(preferences)?.let { settingsWindowSizeText(it.widthDp, it.heightDp, language) }
        ?: settingsWindowSizeText(preferences.windowSize.widthDp, preferences.windowSize.heightDp, language)
    SettingsRow.FRAME_RATE_CAP -> settingsFrameRateText(preferences.frameRateCap, language)
    else -> "${(sliderValueRaw(preferences) * 100f).roundToInt()}%"
}

/** "1600×900". */
internal fun settingsWindowSizeText(widthDp: Int, heightDp: Int, language: AppLanguage): String =
    language.text(SettingsRedesignText.WindowSizeValue, widthDp, heightDp)

/** "60 FPS" / «60 к/с», or "Unlimited" for 0. */
internal fun settingsFrameRateText(fps: Int, language: AppLanguage): String =
    if (fps <= 0) language.text(SettingsRedesignText.FrameRateUnlimited) else language.text(SettingsRedesignText.FrameRateValue, fps)

private fun SettingsRow.sliderValueRaw(preferences: PlayerPreferences): Float = when (this) {
    SettingsRow.MASTER_VOLUME -> preferences.masterVolume
    SettingsRow.TEXT_SIZE -> preferences.textScale
    else -> 0f
}

internal fun settingsThresholdOutputSamples(language: AppLanguage): List<String> =
    DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.map { formatCompact(it.toLong(), language) }

private fun thresholdIndex(preferences: PlayerPreferences): Int =
    DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices.minByOrNull { index ->
        kotlin.math.abs(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[index].toLong() - preferences.damageNumberTierThreshold)
    } ?: 0

private fun onOff(on: Boolean, language: AppLanguage): String = language.text(if (on) ProfileText.On else ProfileText.Off)

/** Spoken value of a row (state descriptions); matches what the row shows. */
internal fun settingValue(
    preferences: PlayerPreferences,
    row: SettingsRow,
    language: AppLanguage = preferences.language,
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): String = when (row) {
    SettingsRow.LANGUAGE -> preferences.language.nativeName
    SettingsRow.SFX -> onOff(preferences.soundEnabled, language)
    SettingsRow.MUSIC -> onOff(preferences.musicEnabled, language)
    SettingsRow.MASTER_VOLUME -> "${(preferences.masterVolume * 100f).roundToInt()}%"
    SettingsRow.SIMULATION_SPEED -> formatMultiplier(preferences.simulationSpeed, language)
    SettingsRow.TEXT_SIZE -> "${(preferences.textScale * 100f).roundToInt()}%"
    SettingsRow.RUN_STATISTICS_SIDE -> language.text(
        if (preferences.runStatisticsOnLeft) ProfileText.LeftSide else ProfileText.RightSide,
    )
    SettingsRow.SCREEN_SHAKE -> onOff(preferences.screenShake, language)
    SettingsRow.PARTICLES, SettingsRow.DAMAGE_NUMBER_SIZE, SettingsRow.DAMAGE_NUMBER_FORMAT, SettingsRow.COLOR_VISION,
    SettingsRow.WINDOW_MODE, SettingsRow.INTERFACE_SCALE, SettingsRow.REACTION_EFFECTS,
    -> row.optionLabel(row.selectedOption(preferences, platform), language, platform)
    SettingsRow.DAMAGE_NUMBERS -> onOff(preferences.damageNumbers, language)
    SettingsRow.MINIMAP -> onOff(preferences.minimapEnabled, language)
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
        val first = preferences.damageNumberTierThreshold.toLong()
        val second = first * DAMAGE_NUMBER_POWERFUL_MULTIPLIER
        val third = first * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER
        "${formatCompact(first, language)}/${formatCompact(second, language)}/${formatCompact(third, language)}"
    }
    SettingsRow.PAUSE_ON_FOCUS_LOSS, SettingsRow.VSYNC, SettingsRow.IMMERSIVE, SettingsRow.SHOW_FPS, SettingsRow.FULLSCREEN ->
        onOff(row.isOn(preferences, platform), language)
    SettingsRow.WINDOW_SIZE, SettingsRow.FRAME_RATE_CAP -> row.sliderOutput(preferences, language, platform)
    SettingsRow.REFRESH_RATE -> if (platform.control(row) == SettingsControl.READOUT) {
        val hz = platform.support.monitorRefreshHz
        if (hz > 0) refreshRateText(hz, language) else language.text(SettingsRedesignText.RefreshUnknown)
    } else {
        row.optionLabel(row.selectedOption(preferences, platform), language, platform)
    }
    SettingsRow.RESET_BINDINGS -> language.text(SettingsRedesignText.ResetBindingsButton)
    else -> preferences.keyBindings.key(checkNotNull(row.boundAction)).label(language)
}

/** Damage number color tiers start at the threshold, four times it and twenty times it. */
internal const val DAMAGE_NUMBER_POWERFUL_MULTIPLIER = 4L
internal const val DAMAGE_NUMBER_DEVASTATING_MULTIPLIER = 20L

/** Damage number sample in the preview, formatted like the run formats it. */
internal fun settingsDamageSample(amount: Long, format: DamageNumberFormat, language: AppLanguage): String = when (format) {
    DamageNumberFormat.COMPACT -> formatCompact(amount, language)
    DamageNumberFormat.FULL -> amount.toString()
}

/** Tier color of a sample: bone, then you, heat and threat as the magnitude climbs. */
internal fun settingsDamageColor(amount: Long, threshold: Int, roles: KkRolePalette): Color = when {
    amount >= threshold * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER -> roles.threat
    amount >= threshold * DAMAGE_NUMBER_POWERFUL_MULTIPLIER -> roles.heat
    amount >= threshold -> roles.you
    else -> Kk.Bone
}
