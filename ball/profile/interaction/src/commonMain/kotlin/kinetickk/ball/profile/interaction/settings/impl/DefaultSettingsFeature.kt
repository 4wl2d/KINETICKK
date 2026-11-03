// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplayPort
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.KkFramePacer
import kinetickk.foundation.design.LocalKkFrameRateCap
import kinetickk.foundation.design.LocalKkInterfaceScale
import kinetickk.foundation.design.toBindableKey
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.resource.audio.api.AudioService
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

class DefaultSettingsFeature(
    private val profilePort: ProfilePort,
    audioService: AudioService,
    private val display: SettingsDisplayPort = SettingsDisplayPort.None,
) : SettingsFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)

    // The view a window opened again for a display change (V-Sync, Borderless) comes back to: the
    // new window's composition starts without the previous rememberSaveable registry.
    private var retained: SettingsRetainedView? = null

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
        val restored = retained?.takeIf { it.routeToken == routeToken }
        var pageValue by rememberSaveable(routeToken) { mutableIntStateOf(restored?.page ?: 0) }
        var groupIndexValue by rememberSaveable(routeToken) { mutableIntStateOf(restored?.group ?: SettingsGroup.GAME.ordinal) }
        var infoValue by remember(routeToken) { mutableStateOf<SettingsRow?>(null) }
        var hoverValue by remember { mutableStateOf<SettingsTarget?>(null) }
        var focusValue by remember { mutableStateOf<SettingsTarget?>(null) }
        var activeRowValue by remember(routeToken) { mutableStateOf(restored?.activeRow) }
        var previewTimeValue by remember { mutableFloatStateOf(0f) }
        var previewWakeValue by remember { mutableIntStateOf(0) }
        // Key capture and the two-press reset (Interaction-ephemeral).
        var captureValue by remember(routeToken) { mutableStateOf<GameAction?>(null) }
        var swappedValue by remember(routeToken) { mutableStateOf<GameAction?>(null) }
        var swappedAtValue by remember(routeToken) { mutableFloatStateOf(SETTINGS_NEVER) }
        var rejectAtValue by remember(routeToken) { mutableFloatStateOf(SETTINGS_NEVER) }
        var resetArmedValue by remember(routeToken) { mutableStateOf(false) }
        // The live region's words, cleared after a while. Words equal to those it still says are cleared
        // first, so a second refused key is a new change of the region and is spoken again.
        var noticeValue by remember(routeToken) { mutableStateOf<SettingsNotice?>(null) }
        var noticeSequence by remember(routeToken) { mutableIntStateOf(0) }
        var announcementValue by remember(routeToken) { mutableStateOf<SettingsAnnouncement?>(null) }
        LaunchedEffect(noticeValue) {
            val notice = noticeValue ?: return@LaunchedEffect
            if (announcementValue == notice.announcement) {
                announcementValue = null
                delay(SETTINGS_ANNOUNCE_GAP_MS)
            }
            announcementValue = notice.announcement
            delay(SETTINGS_ANNOUNCE_MS)
            announcementValue = null
        }
        // The key held when a capture began (Enter or Space on the cell): its repeats are not a choice.
        val heldKey = remember { arrayOfNulls<Key>(1) }
        val captureIgnoredKey = remember { arrayOfNulls<Key>(1) }
        // The key that ended a capture: its repeats and release still belong to that press, so they
        // reach no shortcut (holding Escape does not also close Settings, a new S does not toggle it).
        val releaseKey = remember { arrayOfNulls<Key>(1) }
        val capturing = captureValue != null
        val support = remember(display, routeToken, viewportValue) { display.support() }
        // Settings is drawn at the scaled density; the offered scales follow the window's unscaled size.
        val screenScale = LocalKkInterfaceScale.current
        val unscaledDensity = density / screenScale
        val platform = SettingsPlatform(
            support = support,
            interfaceScales = settingsInterfaceScales(
                viewportValue.width / unscaledDensity, viewportValue.height / unscaledDensity, support.touchPrimary,
            ),
            screenScale = screenScale,
            live = display.liveWindow(),
            browserFullscreen = display.browserFullscreen,
        )
        SideEffect(routeToken, groupIndexValue, pageValue, activeRowValue) {
            retained = SettingsRetainedView(routeToken, groupIndexValue, pageValue, activeRowValue)
        }
        val requestedGroup = SettingsGroup.entries[groupIndexValue]
        val group = if (requestedGroup in platform.groups) requestedGroup else platform.groups.first()
        LaunchedEffect(pageValue) { focusRequester.requestFocus() }
        // The preview animates while the player interacts and settles shortly after, so an idle
        // Settings screen stops requesting frames; a key cell waiting for a key keeps it running.
        val frameRateCap = LocalKkFrameRateCap.current
        LaunchedEffect(previewWakeValue, capturing, frameRateCap) {
            val pacer = KkFramePacer(frameRateCap)
            var last = pacer.awaitFrame()
            val end = last + PREVIEW_AWAKE_NANOS
            while (last < end || capturing) {
                val now = pacer.awaitFrame()
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
                    is SettingsEffect.Announce -> {
                        noticeSequence += 1
                        noticeValue = SettingsNotice(effect.announcement, noticeSequence)
                    }
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
                SettingsAnnouncer(announcementValue, language)
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

/** One announcement; [sequence] lets the same words announce again. */
private data class SettingsNotice(val announcement: SettingsAnnouncement, val sequence: Int)

/** Announcements clear after this much presentation time (the Workshop's 4 s). */
private const val SETTINGS_ANNOUNCE_MS = 4_000L

/** How long the region stays empty before it repeats the words it said (longer than a screen reader's poll). */
private const val SETTINGS_ANNOUNCE_GAP_MS = 200L

/** What Settings shows for one route, kept by the feature instance for a window opened again. */
internal class SettingsRetainedView(val routeToken: Long, val group: Int, val page: Int, val activeRow: SettingsRow?)
