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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplayPort
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.label
import kinetickk.foundation.design.toBindableKey
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.resource.audio.api.AudioService
import kotlin.math.roundToInt

class DefaultSettingsFeature(
    private val profilePort: ProfilePort,
    audioService: AudioService,
    private val display: SettingsDisplayPort = SettingsDisplayPort.None,
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
        // Key capture and the two-press reset (Interaction-ephemeral).
        var captureValue by remember(routeToken) { mutableStateOf<GameAction?>(null) }
        var swappedValue by remember(routeToken) { mutableStateOf<GameAction?>(null) }
        var swappedAtValue by remember(routeToken) { mutableFloatStateOf(SETTINGS_NEVER) }
        var rejectAtValue by remember(routeToken) { mutableFloatStateOf(SETTINGS_NEVER) }
        var resetArmedValue by remember(routeToken) { mutableStateOf(false) }
        var announcementValue by remember(routeToken) { mutableStateOf<SettingsAnnouncement?>(null) }
        // The key held when a capture began (Enter or Space on the cell): its repeats are not a choice.
        val heldKey = remember { arrayOfNulls<Key>(1) }
        val captureIgnoredKey = remember { arrayOfNulls<Key>(1) }
        // The key that ended a capture: its repeats and release still belong to that press, so they
        // reach no shortcut (holding Escape does not also close Settings, a new S does not toggle it).
        val releaseKey = remember { arrayOfNulls<Key>(1) }
        val capturing = captureValue != null
        val support = remember(display, routeToken, viewportValue) { display.support() }
        val platform = SettingsPlatform(
            support = support,
            interfaceScales = settingsInterfaceScales(viewportValue.width / density, viewportValue.height / density, support.touchPrimary),
            live = display.liveWindow(),
            browserFullscreen = display.browserFullscreen,
        )
        val requestedGroup = SettingsGroup.entries[groupIndexValue]
        val group = if (requestedGroup in platform.groups) requestedGroup else platform.groups.first()
        LaunchedEffect(pageValue) { focusRequester.requestFocus() }
        // The preview animates while the player interacts and settles shortly after, so an idle
        // Settings screen stops requesting frames; a key cell waiting for a key keeps it running.
        LaunchedEffect(previewWakeValue, capturing) {
            var last = withFrameNanos { it }
            val end = last + PREVIEW_AWAKE_NANOS
            while (last < end || capturing) {
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
        val layout = remember(measurers, width, height, density, group, pageValue, platform) {
            if (width <= 0f || height <= 0f) null
            else settingsLayout(width, height, density, group, pageValue, language, measurers.metrics(mode), platform)
        }
        val activeRow = activeRowValue?.takeIf { row -> layout?.row(row) != null } ?: layout?.rows?.firstOrNull()?.row
        val canvasCache = remember { SettingsCanvasCache() }

        fun wake() {
            previewWakeValue++
        }

        fun dispatch(action: SettingsAction) {
            val reduction = SettingsReducer.reduce(
                state = SettingsState(
                    renderModelValue, pageValue, group, infoValue, platform,
                    captureValue, swappedValue, swappedAtValue, rejectAtValue, resetArmedValue,
                ),
                action = action,
            )
            val next = reduction.state
            renderModelValue = next.model
            if (next.group != group || next.page != pageValue) activeRowValue = null
            pageValue = next.page
            groupIndexValue = next.group.ordinal
            infoValue = next.info
            if (next.capture != null && captureValue == null) captureIgnoredKey[0] = heldKey[0]
            captureValue = next.capture
            swappedValue = next.swapped
            swappedAtValue = next.swappedAt
            rejectAtValue = next.rejectAt
            resetArmedValue = next.resetArmed
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
                    // Synchronously, inside the press that asked for it (browsers require a user gesture).
                    is SettingsEffect.SetBrowserFullscreen -> display.setBrowserFullscreen(effect.on)
                    SettingsEffect.ReapplyDisplay -> display.reapply()
                    is SettingsEffect.Announce -> announcementValue = effect.announcement
                }
            }
            wake()
        }

        /** Every key event while a key cell waits for a key: consumed, so no shortcut or focus move sees it. */
        fun captureKey(event: KeyEvent) {
            val ignored = captureIgnoredKey[0]
            if (event.type == KeyEventType.KeyUp) {
                if (event.key == ignored) captureIgnoredKey[0] = null
                return
            }
            if (event.type != KeyEventType.KeyDown || event.key == ignored) return
            val key = event.key.toBindableKey()
            dispatch(when {
                event.key == Key.Escape -> SettingsAction.CancelCapture
                key != null -> SettingsAction.CaptureKey(key, previewTimeValue)
                else -> SettingsAction.CaptureRejected(previewTimeValue)
            })
            if (captureValue == null) releaseKey[0] = event.key
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
                .semantics { paneTitle = language.text(ProfileText.SettingsTitle) }
                .onPreviewKeyEvent { event ->
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            heldKey[0] = event.key
                            if (releaseKey[0] != event.key) releaseKey[0] = null
                        }
                        KeyEventType.KeyUp -> if (heldKey[0] == event.key) heldKey[0] = null
                    }
                    when {
                        captureValue != null -> {
                            captureKey(event)
                            true
                        }
                        event.key == releaseKey[0] -> {
                            if (event.type == KeyEventType.KeyUp) releaseKey[0] = null
                            true
                        }
                        else -> false
                    }
                }
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
                            // While a key cell waits, a press anywhere else only ends the capture.
                            (resolveSettingsPress(current, position.x, position.y, resetArmedValue)
                                ?: SettingsAction.CancelCapture.takeIf { captureValue != null })?.let(::dispatch)
                        }
                    },
            ) {
                val current = layout ?: return@Canvas
                drawSettings(
                    SettingsFrame(
                        current, renderModelValue.preferences, activeRow, hoverValue, focusValue, infoValue, previewTimeValue,
                        captureValue, swappedValue, swappedAtValue, rejectAtValue, resetArmedValue,
                    ),
                    measurers,
                    canvasCache,
                )
            }
            if (layout != null) {
                SettingsAccessibleControls(
                    layout = layout,
                    model = renderModelValue,
                    language = language,
                    capture = captureValue,
                    resetArmed = resetArmedValue,
                    onFocusChanged = ::focusChanged,
                    onActivate = { target -> dispatch(target.toAction(resetArmedValue)) },
                )
                announcementValue?.let { announcement -> SettingsAnnouncer(announcement, language) }
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
 * The Settings live region (`kinetickk.settings.announce`): a key bound or moved and a reset are
 * polite; a key that cannot be bound is assertive.
 */
@Composable
private fun SettingsAnnouncer(announcement: SettingsAnnouncement, language: AppLanguage) {
    val text = settingsAnnouncementText(announcement, language)
    val assertive = announcement == SettingsAnnouncement.KeyUnavailable
    Box(Modifier.testTag("kinetickk.settings.announce").semantics {
        liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
        contentDescription = text
    })
}

/** The words of an announcement: "Dash set to F" (plus "Skill 1 moved to Space" after a swap). */
internal fun settingsAnnouncementText(announcement: SettingsAnnouncement, language: AppLanguage): String = when (announcement) {
    is SettingsAnnouncement.KeyBound -> {
        val set = language.text(SettingsRedesignText.KeySetTo, settingsActionName(announcement.action, language), announcement.key.label(language))
        val moved = announcement.moved
        val movedTo = announcement.movedTo
        if (moved == null || movedTo == null) set
        else "$set. " + language.text(SettingsRedesignText.KeyMovedTo, settingsActionName(moved, language), movedTo.label(language))
    }
    SettingsAnnouncement.KeyUnavailable -> language.text(SettingsRedesignText.KeyUnavailable)
    SettingsAnnouncement.KeysReset -> language.text(SettingsRedesignText.KeysReset)
}

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
    capture: GameAction?,
    resetArmed: Boolean,
    onFocusChanged: (SettingsTarget, Boolean) -> Unit,
    onActivate: (SettingsTarget) -> Unit,
) {
    val preferences = model.preferences
    val platform = layout.platform
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
        val tag = "kinetickk.settings.${row.row.tagId}"
        when (row.control) {
            SettingsControl.SEGMENTED -> Box(Modifier.fillMaxSize().selectableGroup()) {
                val selected = row.row.selectedOption(preferences, platform)
                row.options.forEachIndexed { option, cell ->
                    val optionLabel = row.row.optionLabel(option, language, platform)
                    SettingsSemanticTarget(
                        cell, "$tag.${row.row.optionId(option, platform)}",
                        optionLabel, Role.RadioButton, SettingsTarget.Option(row.row, option), onFocusChanged, onActivate,
                        selected = option == selected,
                    )
                }
            }
            SettingsControl.TOGGLE -> row.toggle?.let { toggle ->
                SettingsSemanticTarget(
                    toggle, "$tag.toggle", label, Role.Switch,
                    SettingsTarget.Toggle(row.row), onFocusChanged, onActivate, toggled = row.row.isOn(preferences, platform),
                )
            }
            SettingsControl.SLIDER -> if (row.row != SettingsRow.MASTER_VOLUME) {
                val value = settingValue(preferences, row.row, language, platform)
                val enabled = row.row.isEnabled(preferences)
                row.decrease?.let {
                    SettingsSemanticTarget(it, "$tag.decrease", "$label −", Role.Button,
                        SettingsTarget.Step(row.row, -1), onFocusChanged, onActivate, state = value, enabled = enabled)
                }
                row.increase?.let {
                    SettingsSemanticTarget(it, "$tag.increase", "$label +", Role.Button,
                        SettingsTarget.Step(row.row, 1), onFocusChanged, onActivate, state = value, enabled = enabled)
                }
            }
            SettingsControl.KEYBIND -> {
                val action = checkNotNull(row.row.boundAction)
                row.key?.let { cell ->
                    val key = preferences.keyBindings.key(action).label(language)
                    SettingsSemanticTarget(
                        cell, "$tag.key", language.text(SettingsRedesignText.KeyCell, label, key), Role.Button,
                        SettingsTarget.Key(action), onFocusChanged, onActivate,
                        state = if (capture == action) language.text(SettingsRedesignText.WaitingForKey) else null,
                        actionLabel = row.row.about(language, platform),
                    )
                }
            }
            // A readout has no control; its (!) below carries the value as state.
            SettingsControl.READOUT -> Unit
            SettingsControl.ACTION -> row.action?.let { button ->
                SettingsSemanticTarget(
                    button, "$tag.action",
                    language.text(if (resetArmed) SettingsRedesignText.ResetBindingsConfirm else SettingsRedesignText.ResetBindingsButton),
                    Role.Button, SettingsTarget.Reset, onFocusChanged, onActivate,
                    enabled = settingsResetAvailable(preferences, platform),
                )
            }
        }
        if (row.hasInfo) {
            SettingsSemanticTarget(
                row.info.settingsTouch(density, 32f), "$tag.info", row.row.about(language, platform), Role.Button,
                SettingsTarget.Info(row.row), onFocusChanged, onActivate,
                state = if (row.control == SettingsControl.READOUT) settingValue(preferences, row.row, language, platform) else null,
            )
        }
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
    enabled: Boolean = true,
    actionLabel: String? = null,
) {
    PlacedBox(bounds, Modifier
        .testTag(tag)
        .semantics {
            contentDescription = description
            this.role = role
            if (selected != null) this.selected = selected
            if (toggled != null) toggleableState = ToggleableState(toggled)
            if (state != null) stateDescription = state
            if (!enabled) disabled()
            onClick(label = actionLabel) { onActivate(target); true }
        }
        .onFocusChanged { onFocusChanged(target, it.isFocused) }
        .onKeyEvent { event ->
            val activates = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
            if (activates && event.type == KeyEventType.KeyDown) onActivate(target)
            activates
        }
        .focusable())
}
