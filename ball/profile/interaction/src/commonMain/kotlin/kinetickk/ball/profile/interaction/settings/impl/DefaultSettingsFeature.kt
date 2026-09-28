// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.resource.audio.api.AudioService
import kotlin.math.roundToInt

class DefaultSettingsFeature(
    private val profilePort: ProfilePort,
    audioService: AudioService,
) : SettingsFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    @Composable
    override fun Content(
        routeToken: Long,
        onOutput: (SettingsOutput) -> Unit,
    ) {
        var renderModelValue by remember(profilePort, routeToken) {
            mutableStateOf(
                profilePort.query(ProfileQuery.GetPreferences).preferences.toRenderModel(),
            )
        }
        val focusRequester = remember { FocusRequester() }
        val density = LocalDensity.current.density
        var viewportValue by remember { mutableStateOf(IntSize.Zero) }
        var pageValue by rememberSaveable(routeToken) { mutableIntStateOf(0) }
        var groupIndexValue by rememberSaveable(routeToken) { mutableIntStateOf(SettingsGroup.GAME.ordinal) }
        var infoValue by remember(routeToken) { mutableStateOf<SettingsRow?>(null) }
        var hoverValue by remember { mutableStateOf<SettingsTarget?>(null) }
        var focusValue by remember { mutableStateOf<SettingsTarget?>(null) }
        var activeRowValue by remember(routeToken) { mutableStateOf<SettingsRow?>(null) }
        var previewTimeValue by remember { mutableFloatStateOf(0f) }
        var previewWakeValue by remember { mutableIntStateOf(0) }
        val group = SettingsGroup.entries[groupIndexValue]
        LaunchedEffect(pageValue) { focusRequester.requestFocus() }
        // The preview animates while the player interacts and settles shortly after, so an idle
        // Settings screen stops requesting frames.
        LaunchedEffect(previewWakeValue) {
            var last = withFrameNanos { it }
            val end = last + PREVIEW_AWAKE_NANOS
            while (last < end) {
                val now = withFrameNanos { it }
                previewTimeValue += ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
            }
        }

        val language = LocalAppLanguage.current
        val roles = LocalKkRolePalette.current
        val typography = rememberInterfaceTypography()
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        val width = viewportValue.width.toFloat()
        val height = viewportValue.height.toFloat()
        val mode = settingsLayoutMode(width, height, density)
        val textScale = renderModelValue.preferences.textScale
        val measurers = remember(composeTextMeasurer, typography, language, roles, textScale, mode) {
            settingsMeasurers(composeTextMeasurer, typography, language, roles, textScale, mode)
        }
        val layout = remember(measurers, width, height, density, group, pageValue) {
            if (width <= 0f || height <= 0f) null
            else settingsLayout(width, height, density, group, pageValue, language, measurers.metrics(mode))
        }
        val activeRow = activeRowValue?.takeIf { row -> layout?.row(row) != null } ?: layout?.rows?.firstOrNull()?.row
        val canvasCache = remember { SettingsCanvasCache() }

        fun wake() {
            previewWakeValue++
        }

        fun dispatch(action: SettingsAction) {
            val reduction = SettingsReducer.reduce(
                state = SettingsState(renderModelValue, pageValue, group, infoValue),
                action = action,
            )
            renderModelValue = reduction.state.model
            if (reduction.state.group != group || reduction.state.page != pageValue) activeRowValue = null
            pageValue = reduction.state.page
            groupIndexValue = reduction.state.group.ordinal
            infoValue = reduction.state.info
            reduction.effects.forEach { effect ->
                when (effect) {
                    is SettingsEffect.AdjustPreference -> {
                        profilePort.accept(ProfilePulse.AdjustPreference(effect.adjustment))
                        renderModelValue = profilePort
                            .query(ProfileQuery.GetPreferences)
                            .preferences
                            .toRenderModel()
                        audioExecutor.updatePreferences(renderModelValue.preferences)
                        onOutput(SettingsOutput.LanguageChanged(renderModelValue.preferences.language))
                    }
                    is SettingsEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is SettingsEffect.Emit -> onOutput(effect.output)
                }
            }
            wake()
        }

        fun focusChanged(target: SettingsTarget, focused: Boolean) {
            if (focused) {
                focusValue = target
                target.row?.let { activeRowValue = it }
                wake()
            } else if (focusValue == target) {
                focusValue = null
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onSizeChanged { viewportValue = it }
                .pointerInput(layout) {
                    val current = layout ?: return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val position = event.changes.firstOrNull()?.position ?: continue
                            when (event.type) {
                                PointerEventType.Exit -> hoverValue = null
                                PointerEventType.Move, PointerEventType.Enter -> {
                                    val target = current.targetAt(position.x, position.y)
                                    if (target != hoverValue) hoverValue = target
                                    val row = current.rows.firstOrNull { position.y >= it.bounds.top && position.y < it.bounds.bottom &&
                                        position.x >= it.bounds.left - 20f * density && position.x <= it.bounds.right }?.row
                                    if (row != null && row != activeRowValue) {
                                        activeRowValue = row
                                        wake()
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(layout) {
                        val current = layout ?: return@pointerInput
                        detectTapGestures { position ->
                            resolveSettingsPress(current, position.x, position.y)?.let(::dispatch)
                        }
                    },
            ) {
                val current = layout ?: return@Canvas
                drawSettings(
                    SettingsFrame(current, renderModelValue.preferences, activeRow, hoverValue, focusValue, infoValue, previewTimeValue),
                    measurers,
                    canvasCache,
                )
            }
            if (layout != null) {
                SettingsAccessibleControls(
                    layout = layout,
                    model = renderModelValue,
                    language = language,
                    onFocusChanged = ::focusChanged,
                    onActivate = { target -> dispatch(target.toAction()) },
                )
                layout.row(SettingsRow.MASTER_VOLUME)?.let { row ->
                    SettingsVolumeControl(
                        percent = (renderModelValue.preferences.masterVolume * 100f).roundToInt(),
                        routeToken = routeToken,
                        row = row,
                        measurers = measurers,
                        onPercentChange = { dispatch(SettingsAction.SetMasterVolume(it)) },
                        onStep = { dispatch(SettingsAction.Adjust(SettingsRow.MASTER_VOLUME, it)) },
                        onFocusChanged = ::focusChanged,
                        onEditingFinished = { focusRequester.requestFocus() },
                    )
                }
                settingsOpenInfo(focusValue, hoverValue, infoValue)?.let { open ->
                    val slip = remember(measurers, layout, open) { measurers.infoSlip(layout, open) }
                    if (slip != null) {
                        SettingsInfoSlipLayer(slip, settingsSlipTakesPresses(focusValue, hoverValue, infoValue)) {
                            dispatch(SettingsAction.CloseInfo)
                        }
                    }
                }
            }
        }
    }
}

private const val PREVIEW_AWAKE_NANOS = 2_400_000_000L

/**
 * The open (!) explanation, composed last so it sits above the canvas and the Compose volume
 * strip. When it [takesPresses], every press inside the slip lands on it: a tap meant to dismiss
 * it closes it and never reaches the control it covers.
 */
@Composable
private fun SettingsInfoSlipLayer(slip: SettingsInfoSlip, takesPresses: Boolean, onPress: () -> Unit) {
    val press by rememberUpdatedState(onPress)
    // PlacedBox puts the layer at the rounded top-left; the slip is drawn relative to it.
    val origin = Offset(slip.bounds.left.roundToInt().toFloat(), slip.bounds.top.roundToInt().toFloat())
    // Without pointer input the layer is no hit target, so presses reach the controls below.
    val presses = if (takesPresses) Modifier.pointerInput(slip) { detectTapGestures { press() } } else Modifier
    PlacedBox(slip.bounds, Modifier
        .testTag("kinetickk.settings.${slip.row.tagId}.slip")
        .then(presses)
        .drawBehind { drawSettingsInfoSlip(slip, origin) })
}

/**
 * Focusable semantic nodes over the canvas targets, in reading order. Pointer input stays with the
 * canvas and its resolver (these nodes add none); keyboard and accessibility actions reach the same
 * targets through [onActivate].
 */
@Composable
private fun SettingsAccessibleControls(
    layout: SettingsLayout,
    model: kinetickk.ball.profile.interaction.settings.api.SettingsRenderModel,
    language: AppLanguage,
    onFocusChanged: (SettingsTarget, Boolean) -> Unit,
    onActivate: (SettingsTarget) -> Unit,
) {
    val preferences = model.preferences
    val density = layout.density
    SettingsSemanticTarget(layout.back, "kinetickk.settings.back", language.text(SettingsRedesignText.Back), Role.Button,
        SettingsTarget.Back, onFocusChanged, onActivate)
    Box(Modifier.fillMaxSize().selectableGroup()) {
        layout.tabs.forEach { tab ->
            SettingsSemanticTarget(
                tab.bounds, "kinetickk.settings.group.${tab.group.name.lowercase()}", language.text(tab.group.label), Role.Tab,
                SettingsTarget.Tab(tab.group), onFocusChanged, onActivate, selected = layout.group == tab.group,
            )
        }
    }
    layout.rows.forEach { row ->
        val label = row.row.label(language)
        when (row.row.control) {
            SettingsControl.SEGMENTED -> Box(Modifier.fillMaxSize().selectableGroup()) {
                val selected = row.row.selectedOption(preferences)
                row.options.forEachIndexed { option, cell ->
                    val optionLabel = row.row.optionLabel(option, language)
                    SettingsSemanticTarget(
                        cell, "kinetickk.settings.${row.row.tagId}.${row.row.optionId(option)}",
                        optionLabel, Role.RadioButton, SettingsTarget.Option(row.row, option), onFocusChanged, onActivate,
                        selected = option == selected,
                    )
                }
            }
            SettingsControl.TOGGLE -> row.toggle?.let { toggle ->
                SettingsSemanticTarget(
                    toggle, "kinetickk.settings.${row.row.tagId}.toggle", label, Role.Switch,
                    SettingsTarget.Toggle(row.row), onFocusChanged, onActivate, toggled = row.row.isOn(preferences),
                )
            }
            SettingsControl.SLIDER -> if (row.row != SettingsRow.MASTER_VOLUME) {
                val value = settingValue(preferences, row.row, language)
                row.decrease?.let {
                    SettingsSemanticTarget(it, "kinetickk.settings.${row.row.tagId}.decrease", "$label −", Role.Button,
                        SettingsTarget.Step(row.row, -1), onFocusChanged, onActivate, state = value)
                }
                row.increase?.let {
                    SettingsSemanticTarget(it, "kinetickk.settings.${row.row.tagId}.increase", "$label +", Role.Button,
                        SettingsTarget.Step(row.row, 1), onFocusChanged, onActivate, state = value)
                }
            }
        }
        SettingsSemanticTarget(
            row.info.settingsTouch(density, 32f), "kinetickk.settings.${row.row.tagId}.info", row.row.about(language), Role.Button,
            SettingsTarget.Info(row.row), onFocusChanged, onActivate,
        )
    }
    layout.pages.forEachIndexed { index, pip ->
        SettingsSemanticTarget(
            pip.settingsTouch(density, 32f), "kinetickk.settings.page.$index",
            language.text(SettingsRedesignText.Page, index + 1, layout.pages.size), Role.Tab,
            SettingsTarget.Page(index), onFocusChanged, onActivate, selected = index == layout.page,
        )
    }
}

@Composable
private fun SettingsSemanticTarget(
    bounds: Rect,
    tag: String,
    description: String,
    role: Role,
    target: SettingsTarget,
    onFocusChanged: (SettingsTarget, Boolean) -> Unit,
    onActivate: (SettingsTarget) -> Unit,
    selected: Boolean? = null,
    toggled: Boolean? = null,
    state: String? = null,
) {
    PlacedBox(bounds, Modifier
        .testTag(tag)
        .semantics {
            contentDescription = description
            this.role = role
            if (selected != null) this.selected = selected
            if (toggled != null) toggleableState = ToggleableState(toggled)
            if (state != null) stateDescription = state
            onClick { onActivate(target); true }
        }
        .onFocusChanged { onFocusChanged(target, it.isFocused) }
        .onKeyEvent { event ->
            val activates = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
            if (activates && event.type == KeyEventType.KeyDown) onActivate(target)
            activates
        }
        .focusable())
}
