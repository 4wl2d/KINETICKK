// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.formatMultiplier
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.REFRESH_RATE_AUTO
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsRenderModel
import kotlin.math.abs
import kotlin.math.roundToInt

internal sealed interface SettingsAction {
    data class SelectGroup(val group: SettingsGroup) : SettingsAction
    data class SelectLanguage(val language: AppLanguage) : SettingsAction
    /** Direct choice of a segmented row's [option] (see [optionCount]). */
    data class Select(val row: SettingsRow, val option: Int) : SettingsAction
    data class SetMasterVolume(val percent: Int) : SettingsAction
    /** Toggles a toggle row, or steps a slider row by one step in [direction] (−1 or 1). */
    data class Adjust(val row: SettingsRow, val direction: Int) : SettingsAction
    /** Opens the row's (!) explanation, or closes it when it is already open. */
    data class ToggleInfo(val row: SettingsRow) : SettingsAction
    /** A press on the open explanation's slip: closes it and changes no preference. */
    data object CloseInfo : SettingsAction
    data class PageSelected(val page: Int) : SettingsAction
    data object Back : SettingsAction
    /** A press, tap, or Enter/Space on [action]'s key cell: the next key pressed becomes its key. */
    data class BeginCapture(val action: GameAction) : SettingsAction
    /** A bindable [key] pressed while capturing, at Settings time [at] seconds. */
    data class CaptureKey(val key: BindableKey, val at: Float) : SettingsAction
    /** A key that can never be bound (Tab, Enter, Backspace, Meta, unknown) pressed while capturing. */
    data class CaptureRejected(val at: Float) : SettingsAction
    /** Escape, or a press anywhere but the capturing cell: capture ends and nothing changes. */
    data object CancelCapture : SettingsAction
    /** First press of Reset keys: arms the confirmation. */
    data object ArmReset : SettingsAction
    /** Second press of Reset keys while armed: every key returns to its default. */
    data object ConfirmReset : SettingsAction
}

internal data class SettingsState(
    val model: SettingsRenderModel,
    val page: Int,
    val group: SettingsGroup = SettingsGroup.GAME,
    /** Row whose (!) explanation was opened by a press; hover and keyboard focus open it transiently. */
    val info: SettingsRow? = null,
    val platform: SettingsPlatform = SettingsPlatform.DEFAULT,
    /** The action whose key cell waits for a key, or null. */
    val capture: GameAction? = null,
    /** The action that lost its key to the last binding (it took the old key), or null. */
    val swapped: GameAction? = null,
    /** Settings time of the last swap, in seconds. */
    val swappedAt: Float = SETTINGS_NEVER,
    /** Settings time of the last refused key, in seconds. */
    val rejectAt: Float = SETTINGS_NEVER,
    /** Reset keys was pressed once and waits for its confirming press. */
    val resetArmed: Boolean = false,
)

/** What the Settings live region announces; the feature words it in the current language. */
internal sealed interface SettingsAnnouncement {
    /** [action] now uses [key]; [moved] (when not null) took [movedTo], the key [action] had. */
    data class KeyBound(
        val action: GameAction,
        val key: BindableKey,
        val moved: GameAction? = null,
        val movedTo: BindableKey? = null,
    ) : SettingsAnnouncement

    data object KeyUnavailable : SettingsAnnouncement
    data object KeysReset : SettingsAnnouncement
}

internal sealed interface SettingsEffect {
    data class AdjustPreference(
        val adjustment: ProfilePreferenceAdjustment,
    ) : SettingsEffect

    data class PlayAudio(val cue: ProfileAudioCue) : SettingsEffect
    data class Emit(val output: SettingsOutput) : SettingsEffect
    /** Enter or leave browser fullscreen; run synchronously inside the input handler that pressed it. */
    data class SetBrowserFullscreen(val on: Boolean) : SettingsEffect
    /** Apply the stored display preferences again over an OS-driven window change. */
    data object ReapplyDisplay : SettingsEffect
    data class Announce(val announcement: SettingsAnnouncement) : SettingsEffect
}

internal data class SettingsReduction(
    val state: SettingsState,
    val effects: List<SettingsEffect> = emptyList(),
)

internal object SettingsReducer {
    fun reduce(state: SettingsState, action: SettingsAction): SettingsReduction {
        if (state.capture != null) return reduceCapture(state, state.capture, action)
        val reduction = reduceIdle(state, action)
        // Any other action disarms a pending reset.
        val keepsReset = action == SettingsAction.ArmReset || action == SettingsAction.ConfirmReset
        return if (keepsReset || !reduction.state.resetArmed) {
            reduction
        } else {
            reduction.copy(state = reduction.state.copy(resetArmed = false))
        }
    }

    /**
     * While a key cell waits for a key, only the capture actions apply. Any other press is
     * consumed: it ends the capture and does nothing else (a press on the capturing cell keeps it).
     */
    private fun reduceCapture(state: SettingsState, capture: GameAction, action: SettingsAction): SettingsReduction =
        when (action) {
            is SettingsAction.CaptureKey -> {
                val bindings = state.model.preferences.keyBindings
                val previous = bindings.key(capture)
                if (previous == action.key) {
                    SettingsReduction(state.copy(capture = null))
                } else {
                    val holder = bindings.action(action.key)
                    SettingsReduction(
                        state = state.copy(capture = null, swapped = holder, swappedAt = if (holder != null) action.at else state.swappedAt),
                        effects = listOf(
                            SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetKeyBinding(capture, action.key)),
                            SettingsEffect.PlayAudio(ProfileAudioCue.PURCHASE),
                            SettingsEffect.Announce(
                                SettingsAnnouncement.KeyBound(capture, action.key, holder, previous.takeIf { holder != null }),
                            ),
                        ),
                    )
                }
            }
            is SettingsAction.CaptureRejected -> SettingsReduction(
                state = state.copy(rejectAt = action.at),
                effects = listOf(
                    SettingsEffect.PlayAudio(ProfileAudioCue.REFUSED),
                    SettingsEffect.Announce(SettingsAnnouncement.KeyUnavailable),
                ),
            )
            is SettingsAction.BeginCapture -> if (action.action == capture) {
                SettingsReduction(state)
            } else {
                SettingsReduction(state.copy(capture = null))
            }
            else -> SettingsReduction(state.copy(capture = null))
        }

    private fun reduceIdle(state: SettingsState, action: SettingsAction): SettingsReduction = when (action) {
        is SettingsAction.SelectGroup -> if (state.group == action.group || action.group !in state.platform.groups) {
            SettingsReduction(state)
        } else {
            SettingsReduction(
                state = state.copy(group = action.group, page = 0, info = null),
                effects = listOf(SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
            )
        }
        is SettingsAction.SelectLanguage -> if (state.model.preferences.language == action.language) {
            SettingsReduction(state)
        } else {
            SettingsReduction(state, listOf(
                SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetLanguage(action.language)),
                SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
            ))
        }
        is SettingsAction.Select -> select(state, action.row, action.option)
        is SettingsAction.SetMasterVolume -> if (action.percent !in 0..100 ||
            state.model.preferences.masterVolume == action.percent / 100f
        ) {
            SettingsReduction(state)
        } else {
            SettingsReduction(state, listOf(
                SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetMasterVolume(action.percent)),
            ))
        }
        is SettingsAction.Adjust -> {
            val effect = if (action.direction != -1 && action.direction != 1 || !state.platform.offers(action.row)) {
                null
            } else {
                adjustEffect(state, action.row, action.direction)
            }
            if (effect == null) {
                SettingsReduction(state)
            } else {
                SettingsReduction(state, listOf(effect, SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)))
            }
        }
        is SettingsAction.ToggleInfo -> SettingsReduction(
            state.copy(info = if (state.info == action.row) null else action.row),
        )
        SettingsAction.CloseInfo -> SettingsReduction(state.copy(info = null))
        is SettingsAction.PageSelected -> SettingsReduction(
            state = state.copy(page = action.page.coerceAtLeast(0), info = null),
            effects = listOf(SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
        )
        SettingsAction.Back -> SettingsReduction(
            state = state,
            effects = listOf(
                SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                SettingsEffect.Emit(SettingsOutput.Back),
            ),
        )
        is SettingsAction.BeginCapture -> if (!state.platform.offers(action.action.bindRow)) {
            SettingsReduction(state)
        } else {
            SettingsReduction(
                state = state.copy(capture = action.action, swapped = null, resetArmed = false),
                effects = listOf(SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
            )
        }
        // Capture actions outside a capture are stale (the key arrived after it ended): inert.
        is SettingsAction.CaptureKey, is SettingsAction.CaptureRejected, SettingsAction.CancelCapture -> SettingsReduction(state)
        SettingsAction.ArmReset -> if (state.resetArmed || !state.resetAvailable) {
            SettingsReduction(state)
        } else {
            SettingsReduction(state.copy(resetArmed = true), listOf(SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)))
        }
        SettingsAction.ConfirmReset -> if (!state.resetArmed || !state.resetAvailable) {
            SettingsReduction(state.copy(resetArmed = false))
        } else {
            SettingsReduction(
                state = state.copy(resetArmed = false, swapped = null),
                effects = listOf(
                    SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.ResetKeyBindings),
                    SettingsEffect.PlayAudio(ProfileAudioCue.PURCHASE),
                    SettingsEffect.Announce(SettingsAnnouncement.KeysReset),
                ),
            )
        }
    }

    private fun select(state: SettingsState, row: SettingsRow, option: Int): SettingsReduction {
        val preferences = state.model.preferences
        val platform = state.platform
        if (row.control != SettingsControl.SEGMENTED || !platform.offers(row) ||
            option !in 0 until row.optionCount(platform)
        ) {
            return SettingsReduction(state)
        }
        if (option == row.selectedOption(preferences, platform)) {
            // The stored mode while the system shows another one (e.g. the OS left fullscreen): apply it again.
            return if (row == SettingsRow.WINDOW_MODE && platform.liveModeOverride(preferences) != null) {
                SettingsReduction(state, listOf(SettingsEffect.ReapplyDisplay, SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK)))
            } else {
                SettingsReduction(state)
            }
        }
        return SettingsReduction(state, listOf(
            SettingsEffect.AdjustPreference(row.choiceAdjustment(option, platform)),
            SettingsEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
        ))
    }

    /** The effect of a toggle press or a stepper press, or null when the press changes nothing. */
    private fun adjustEffect(state: SettingsState, row: SettingsRow, direction: Int): SettingsEffect? {
        val preferences = state.model.preferences
        val platform = state.platform
        return when (row) {
            SettingsRow.FULLSCREEN -> SettingsEffect.SetBrowserFullscreen(!platform.browserFullscreen)
            SettingsRow.WINDOW_SIZE -> {
                val next = settingsWindowSizeStep(preferences, platform, direction) ?: return null
                if (next == preferences.windowSize) {
                    SettingsEffect.ReapplyDisplay
                } else {
                    SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetWindowSize(next))
                }
            }
            SettingsRow.FRAME_RATE_CAP -> settingsFrameRateCapStep(preferences, direction)?.let {
                SettingsEffect.AdjustPreference(ProfilePreferenceAdjustment.SetFrameRateCap(it))
            }
            else -> row.toAdjustment(direction)?.let(SettingsEffect::AdjustPreference)
        }
    }
}

/** True while some key differs from its default (the Reset keys button is enabled). */
internal val SettingsState.resetAvailable: Boolean
    get() = platform.offers(SettingsRow.RESET_BINDINGS) && !model.preferences.keyBindings.isDefault

internal fun PlayerPreferences.toRenderModel(): SettingsRenderModel = SettingsRenderModel(
    preferences = normalized(),
)

/** Number of choices of a segmented row on [platform]; 0 for every other control. */
internal fun SettingsRow.optionCount(platform: SettingsPlatform = SettingsPlatform.DEFAULT): Int = when (this) {
    SettingsRow.LANGUAGE -> AppLanguage.entries.size
    SettingsRow.SIMULATION_SPEED -> SIMULATION_SPEED_OPTIONS.size
    SettingsRow.PARTICLES -> ParticleDensity.entries.size
    SettingsRow.DAMAGE_NUMBER_SIZE -> DamageNumberSize.entries.size
    SettingsRow.DAMAGE_NUMBER_FORMAT -> DamageNumberFormat.entries.size
    SettingsRow.RUN_STATISTICS_SIDE -> 2
    SettingsRow.COLOR_VISION -> ColorVision.entries.size
    SettingsRow.WINDOW_MODE -> WindowMode.entries.size
    SettingsRow.REFRESH_RATE -> if (platform.support.refreshRatesHz.isEmpty()) 0 else platform.refreshChoices.size
    SettingsRow.INTERFACE_SCALE -> platform.interfaceScales.size
    SettingsRow.REACTION_EFFECTS -> ReactionEffectsLevel.entries.size
    else -> 0
}

/** Index of the accepted choice of a segmented row (the listed speed nearest to the stored one). */
internal fun SettingsRow.selectedOption(
    preferences: PlayerPreferences,
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): Int = when (this) {
    SettingsRow.LANGUAGE -> preferences.language.ordinal
    SettingsRow.SIMULATION_SPEED -> SIMULATION_SPEED_OPTIONS.indices.minByOrNull { index ->
        abs(SIMULATION_SPEED_OPTIONS[index] - preferences.simulationSpeed)
    } ?: 0
    SettingsRow.PARTICLES -> preferences.particleDensity.ordinal
    SettingsRow.DAMAGE_NUMBER_SIZE -> preferences.damageNumberSize.ordinal
    SettingsRow.DAMAGE_NUMBER_FORMAT -> preferences.damageNumberFormat.ordinal
    SettingsRow.RUN_STATISTICS_SIDE -> if (preferences.runStatisticsOnLeft) 0 else 1
    SettingsRow.COLOR_VISION -> preferences.colorVision.ordinal
    SettingsRow.WINDOW_MODE -> preferences.windowMode.ordinal
    // A stored rate this display does not offer is applied as Auto.
    SettingsRow.REFRESH_RATE -> platform.refreshChoices.indexOf(preferences.refreshRateHz).coerceAtLeast(0)
    SettingsRow.INTERFACE_SCALE -> platform.interfaceScales.indexOf(platform.appliedInterfaceScale(preferences.interfaceScalePercent))
    SettingsRow.REACTION_EFFECTS -> preferences.reactionEffects.ordinal
    else -> -1
}

/** Visible label of a segmented choice (uppercase is applied by the text style). */
internal fun SettingsRow.optionLabel(
    option: Int,
    language: AppLanguage,
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): String = when (this) {
    SettingsRow.LANGUAGE -> AppLanguage.entries[option].nativeName
    SettingsRow.SIMULATION_SPEED -> formatMultiplier(SIMULATION_SPEED_OPTIONS[option], language).replace('x', '×')
    SettingsRow.PARTICLES -> language.text(when (ParticleDensity.entries[option]) {
        ParticleDensity.LOW -> ProfileText.Low
        ParticleDensity.NORMAL -> ProfileText.Normal
        ParticleDensity.HIGH -> ProfileText.High
    })
    SettingsRow.DAMAGE_NUMBER_SIZE -> language.text(damageNumberSizeText(DamageNumberSize.entries[option]))
    SettingsRow.DAMAGE_NUMBER_FORMAT -> language.text(when (DamageNumberFormat.entries[option]) {
        DamageNumberFormat.COMPACT -> ProfileText.Compact
        DamageNumberFormat.FULL -> ProfileText.Full
    })
    SettingsRow.RUN_STATISTICS_SIDE -> language.text(if (option == 0) ProfileText.LeftSide else ProfileText.RightSide)
    SettingsRow.COLOR_VISION -> language.text(colorVisionText(ColorVision.entries[option]))
    SettingsRow.WINDOW_MODE -> language.text(windowModeText(WindowMode.entries[option]))
    SettingsRow.REFRESH_RATE -> refreshRateText(platform.refreshChoices[option], language)
    SettingsRow.INTERFACE_SCALE -> "${platform.interfaceScales[option]}%"
    SettingsRow.REACTION_EFFECTS -> language.text(reactionEffectsText(ReactionEffectsLevel.entries[option]))
    else -> error("$this has no segmented choices")
}

/** Stable test-tag suffix of a segmented choice (`kinetickk.settings.<row>.<option>`). */
internal fun SettingsRow.optionId(option: Int, platform: SettingsPlatform = SettingsPlatform.DEFAULT): String = when (this) {
    SettingsRow.LANGUAGE -> AppLanguage.entries[option].code
    SettingsRow.SIMULATION_SPEED -> (SIMULATION_SPEED_OPTIONS[option] * 100f).roundToInt().toString()
    SettingsRow.PARTICLES -> ParticleDensity.entries[option].name.lowercase()
    SettingsRow.DAMAGE_NUMBER_SIZE -> DamageNumberSize.entries[option].name.lowercase()
    SettingsRow.DAMAGE_NUMBER_FORMAT -> DamageNumberFormat.entries[option].name.lowercase()
    SettingsRow.RUN_STATISTICS_SIDE -> if (option == 0) "left" else "right"
    SettingsRow.COLOR_VISION -> ColorVision.entries[option].name.lowercase()
    SettingsRow.WINDOW_MODE -> WindowMode.entries[option].name.lowercase()
    SettingsRow.REFRESH_RATE -> platform.refreshChoices[option].let { if (it == REFRESH_RATE_AUTO) "auto" else it.toString() }
    SettingsRow.INTERFACE_SCALE -> platform.interfaceScales[option].toString()
    SettingsRow.REACTION_EFFECTS -> ReactionEffectsLevel.entries[option].name.lowercase()
    else -> error("$this has no segmented choices")
}

internal fun colorVisionText(colorVision: ColorVision): SettingsRedesignText = when (colorVision) {
    ColorVision.DEFAULT -> SettingsRedesignText.ColorVisionDefault
    ColorVision.PROTAN -> SettingsRedesignText.ColorVisionProtan
    ColorVision.DEUTAN -> SettingsRedesignText.ColorVisionDeutan
    ColorVision.TRITAN -> SettingsRedesignText.ColorVisionTritan
    ColorVision.MONO -> SettingsRedesignText.ColorVisionMono
}

internal fun windowModeText(mode: WindowMode): SettingsRedesignText = when (mode) {
    WindowMode.WINDOWED -> SettingsRedesignText.WindowModeWindowed
    WindowMode.BORDERLESS -> SettingsRedesignText.WindowModeBorderless
    WindowMode.FULLSCREEN -> SettingsRedesignText.WindowModeFullscreen
}

internal fun reactionEffectsText(level: ReactionEffectsLevel): SettingsRedesignText = when (level) {
    ReactionEffectsLevel.FULL -> SettingsRedesignText.ReactionFull
    ReactionEffectsLevel.REDUCED -> SettingsRedesignText.ReactionReduced
    ReactionEffectsLevel.MINIMAL -> SettingsRedesignText.ReactionMinimal
}

/** "Auto" for [REFRESH_RATE_AUTO], otherwise "120 Hz" / «120 Гц». */
internal fun refreshRateText(hz: Int, language: AppLanguage): String =
    if (hz == REFRESH_RATE_AUTO) language.text(SettingsRedesignText.RefreshAuto) else language.text(SettingsRedesignText.RefreshValue, hz)

internal fun damageNumberSizeText(size: DamageNumberSize): ProfileText = when (size) {
    DamageNumberSize.SMALL -> ProfileText.Small
    DamageNumberSize.NORMAL -> ProfileText.Normal
    DamageNumberSize.LARGE -> ProfileText.Large
    DamageNumberSize.HUGE -> ProfileText.Huge
}

/**
 * The window size preset one stepper press in [direction] selects, or null when none is left.
 * Steps go through the offered presets by area from the stored preset, or from the live size
 * when the player resized the window ([SettingsPlatform.liveSizeOverride]). Only in windowed mode.
 */
internal fun settingsWindowSizeStep(
    preferences: PlayerPreferences,
    platform: SettingsPlatform,
    direction: Int,
): WindowSizePreset? {
    if (preferences.windowMode != WindowMode.WINDOWED) return null
    val live = platform.liveSizeOverride(preferences)
    val reference = live?.let { it.widthDp.toLong() * it.heightDp } ?: preferences.windowSize.area
    val offered = platform.support.windowSizes
    return if (direction > 0) {
        offered.filter { it.area > reference }.minByOrNull { it.area }
    } else {
        offered.filter { it.area < reference }.maxByOrNull { it.area }
    }
}

private val WindowSizePreset.area: Long get() = widthDp.toLong() * heightDp

/** Slider position 0..1 of the window size among the offered presets. */
internal fun settingsWindowSizePosition(preferences: PlayerPreferences, platform: SettingsPlatform): Float {
    val offered = platform.support.windowSizes.sortedBy { it.area }
    if (offered.size <= 1) return 1f
    val live = platform.liveSizeOverride(preferences)
    val reference = live?.let { it.widthDp.toLong() * it.heightDp } ?: preferences.windowSize.area
    val below = offered.indexOfLast { it.area <= reference }.coerceAtLeast(0)
    return below.toFloat() / offered.lastIndex
}

/** The frame-rate limit one stepper press in [direction] selects, or null at either end. */
internal fun settingsFrameRateCapStep(preferences: PlayerPreferences, direction: Int): Int? {
    val index = FRAME_RATE_CAP_STEPS.indexOf(preferences.frameRateCap)
    if (index < 0) return null
    return FRAME_RATE_CAP_STEPS.getOrNull(index + direction.coerceIn(-1, 1))
}

private fun SettingsRow.choiceAdjustment(option: Int, platform: SettingsPlatform): ProfilePreferenceAdjustment = when (this) {
    SettingsRow.LANGUAGE -> ProfilePreferenceAdjustment.SetLanguage(AppLanguage.entries[option])
    SettingsRow.SIMULATION_SPEED -> ProfilePreferenceAdjustment.SetSimulationSpeed(SIMULATION_SPEED_OPTIONS[option])
    SettingsRow.PARTICLES -> ProfilePreferenceAdjustment.SetParticleDensity(ParticleDensity.entries[option])
    SettingsRow.DAMAGE_NUMBER_SIZE -> ProfilePreferenceAdjustment.SetDamageNumberSize(DamageNumberSize.entries[option])
    SettingsRow.DAMAGE_NUMBER_FORMAT -> ProfilePreferenceAdjustment.SetDamageNumberFormat(DamageNumberFormat.entries[option])
    // Only reached for the side that is not selected, so one toggle lands exactly on it.
    SettingsRow.RUN_STATISTICS_SIDE -> ProfilePreferenceAdjustment.ToggleRunStatisticsSide
    SettingsRow.COLOR_VISION -> ProfilePreferenceAdjustment.SetColorVision(ColorVision.entries[option])
    SettingsRow.WINDOW_MODE -> ProfilePreferenceAdjustment.SetWindowMode(WindowMode.entries[option])
    SettingsRow.REFRESH_RATE -> ProfilePreferenceAdjustment.SetRefreshRate(platform.refreshChoices[option])
    SettingsRow.INTERFACE_SCALE -> ProfilePreferenceAdjustment.SetInterfaceScale(platform.interfaceScales[option])
    SettingsRow.REACTION_EFFECTS -> ProfilePreferenceAdjustment.SetReactionEffects(ReactionEffectsLevel.entries[option])
    else -> error("$this has no segmented choices")
}

/** The stepped or toggled adjustment of a row without platform-dependent steps; null when it has none. */
private fun SettingsRow.toAdjustment(direction: Int): ProfilePreferenceAdjustment? {
    val adjustmentDirection = if (direction < 0) {
        PreferenceAdjustmentDirection.DECREASE
    } else {
        PreferenceAdjustmentDirection.INCREASE
    }
    return when (this) {
        SettingsRow.SFX -> ProfilePreferenceAdjustment.ToggleSoundEffects
        SettingsRow.MUSIC -> ProfilePreferenceAdjustment.ToggleMusic
        SettingsRow.MASTER_VOLUME -> ProfilePreferenceAdjustment.StepMasterVolume(adjustmentDirection)
        SettingsRow.SIMULATION_SPEED -> ProfilePreferenceAdjustment.StepSimulationSpeed(adjustmentDirection)
        SettingsRow.TEXT_SIZE -> ProfilePreferenceAdjustment.StepTextScale(adjustmentDirection)
        SettingsRow.SCREEN_SHAKE -> ProfilePreferenceAdjustment.ToggleScreenShake
        SettingsRow.RUN_STATISTICS_SIDE -> ProfilePreferenceAdjustment.ToggleRunStatisticsSide
        SettingsRow.PARTICLES -> ProfilePreferenceAdjustment.StepParticleDensity(adjustmentDirection)
        SettingsRow.DAMAGE_NUMBERS -> ProfilePreferenceAdjustment.ToggleDamageNumbers
        SettingsRow.DAMAGE_NUMBER_SIZE -> ProfilePreferenceAdjustment.StepDamageNumberSize(adjustmentDirection)
        SettingsRow.DAMAGE_NUMBER_FORMAT -> ProfilePreferenceAdjustment.StepDamageNumberFormat(adjustmentDirection)
        SettingsRow.DAMAGE_COLOR_THRESHOLDS ->
            ProfilePreferenceAdjustment.StepDamageNumberTierThreshold(adjustmentDirection)
        SettingsRow.MINIMAP -> ProfilePreferenceAdjustment.ToggleMinimap
        SettingsRow.PAUSE_ON_FOCUS_LOSS -> ProfilePreferenceAdjustment.TogglePauseOnFocusLoss
        SettingsRow.VSYNC -> ProfilePreferenceAdjustment.ToggleVsync
        SettingsRow.IMMERSIVE -> ProfilePreferenceAdjustment.ToggleImmersive
        SettingsRow.SHOW_FPS -> ProfilePreferenceAdjustment.ToggleShowFps
        // Language and Color vision are chosen with typed selections; the other rows have no step.
        else -> null
    }
}
