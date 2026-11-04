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
import androidx.compose.runtime.MutableIntState
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
import androidx.compose.ui.input.pointer.PointerInputScope
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
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplaySupport
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsRenderModel
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
        val restored = retained?.takeIf { it.routeToken == routeToken }
        val opening = remember(profilePort, routeToken) {
            SettingsOpening(profilePort.query(ProfileQuery.GetPreferences).preferences.toRenderModel(), restored?.activeRow)
        }
        val screen = remember { SettingsScreen() }
        val focusRequester = remember { FocusRequester() }
        val density = LocalDensity.current.density
        val page = rememberSaveable(routeToken) { mutableIntStateOf(restored?.page ?: 0) }
        val groupIndex = rememberSaveable(routeToken) { mutableIntStateOf(restored?.group ?: SettingsGroup.GAME.ordinal) }
        LaunchedEffect(opening.notice) { opening.notice?.let { opening.announce(it) } }
        val capturing = opening.capture != null
        val support = remember(display, routeToken, screen.viewport) { display.support() }
        val platform = settingsPlatform(support, screen.viewport, density, LocalKkInterfaceScale.current)
        SideEffect(routeToken, groupIndex.intValue, page.intValue, opening.activeRow) {
            retained = SettingsRetainedView(routeToken, groupIndex.intValue, page.intValue, opening.activeRow)
        }
        val requestedGroup = SettingsGroup.entries[groupIndex.intValue]
        val group = if (requestedGroup in platform.groups) requestedGroup else platform.groups.first()
        LaunchedEffect(page.intValue) { focusRequester.requestFocus() }
        // The preview animates while the player interacts and settles shortly after, so an idle
        // Settings screen stops requesting frames; a key cell waiting for a key keeps it running.
        val frameRateCap = LocalKkFrameRateCap.current
        LaunchedEffect(screen.previewWake, capturing, frameRateCap) { screen.runPreview(frameRateCap, capturing) }

        val language = LocalAppLanguage.current
        val roles = LocalKkRolePalette.current
        val typography = rememberInterfaceTypography()
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        val width = screen.viewport.width.toFloat()
        val height = screen.viewport.height.toFloat()
        val mode = settingsLayoutMode(width, height, density)
        val textScale = opening.renderModel.preferences.textScale
        val measurers = remember(composeTextMeasurer, typography, language, roles, textScale, mode) {
            settingsMeasurers(composeTextMeasurer, typography, language, roles, textScale, mode)
        }
        val layout = remember(measurers, width, height, density, group, page.intValue, platform) {
            if (width <= 0f || height <= 0f) null
            else settingsLayout(width, height, density, group, page.intValue, language, measurers.metrics(mode), platform)
        }
        val activeRow = opening.activeRow?.takeIf { row -> layout?.row(row) != null } ?: layout?.rows?.firstOrNull()?.row
        val canvasCache = remember { SettingsCanvasCache() }
        val input = SettingsInput(opening, screen, page, groupIndex, group, platform, onOutput)

        Box(
            Modifier
                .fillMaxSize()
                .semantics { paneTitle = language.text(ProfileText.SettingsTitle) }
                .onPreviewKeyEvent(input::previewKey)
                .focusRequester(focusRequester)
                .focusable()
                .onSizeChanged { screen.viewport = it }
                .pointerInput(layout) {
                    val current = layout ?: return@pointerInput
                    input.trackPointer(this, current, density)
                },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(layout) {
                        val current = layout ?: return@pointerInput
                        detectTapGestures { position ->
                            // While a key cell waits, a press anywhere else only ends the capture.
                            (resolveSettingsPress(current, position.x, position.y, opening.resetArmed)
                                ?: SettingsAction.CancelCapture.takeIf { opening.capture != null })?.let(input::dispatch)
                        }
                    },
            ) {
                val current = layout ?: return@Canvas
                drawSettings(
                    SettingsFrame(
                        current, opening.renderModel.preferences, activeRow, screen.hover, screen.focus, opening.info, screen.previewTime,
                        opening.capture, opening.swapped, opening.swappedAt, opening.rejectAt, opening.resetArmed,
                    ),
                    measurers,
                    canvasCache,
                )
            }
            if (layout != null) {
                SettingsAccessibleControls(
                    layout = layout,
                    model = opening.renderModel,
                    language = language,
                    capture = opening.capture,
                    resetArmed = opening.resetArmed,
                    onFocusChanged = input::focusChanged,
                    onActivate = { target -> input.dispatch(target.toAction(opening.resetArmed)) },
                )
                SettingsAnnouncer(opening.announcement, language)
                layout.row(SettingsRow.MASTER_VOLUME)?.let { row ->
                    SettingsVolumeControl(
                        percent = (opening.renderModel.preferences.masterVolume * 100f).roundToInt(),
                        routeToken = routeToken,
                        row = row,
                        measurers = measurers,
                        onPercentChange = { input.dispatch(SettingsAction.SetMasterVolume(it)) },
                        onStep = { input.dispatch(SettingsAction.Adjust(SettingsRow.MASTER_VOLUME, it)) },
                        onFocusChanged = input::focusChanged,
                        onEditingFinished = { focusRequester.requestFocus() },
                    )
                }
                settingsOpenInfo(screen.focus, screen.hover, opening.info)?.let { open ->
                    val slip = remember(measurers, layout, open) { measurers.infoSlip(layout, open) }
                    if (slip != null) {
                        SettingsInfoSlipLayer(slip, settingsSlipTakesPresses(screen.focus, screen.hover, opening.info)) {
                            input.dispatch(SettingsAction.CloseInfo)
                        }
                    }
                }
            }
        }
    }

    /** The platform Settings offers on: [support] and the interface scales that fit the window's unscaled size. */
    private fun settingsPlatform(support: SettingsDisplaySupport, viewport: IntSize, density: Float, screenScale: Float): SettingsPlatform {
        // Settings is drawn at the scaled density; the offered scales follow the window's unscaled size.
        val unscaledDensity = density / screenScale
        return SettingsPlatform(
            support = support,
            interfaceScales = settingsInterfaceScales(
                viewport.width / unscaledDensity, viewport.height / unscaledDensity, support.touchPrimary,
            ),
            screenScale = screenScale,
            live = display.liveWindow(),
            browserFullscreen = display.browserFullscreen,
        )
    }

    /**
     * The Settings handlers for one composition: they read and write [opening], [screen] and the
     * saved [page] and [groupIndex], and reduce against this composition's [group] and [platform].
     * They run outside composition (presses, keys, pointer moves and focus changes).
     */
    private inner class SettingsInput(
        val opening: SettingsOpening,
        val screen: SettingsScreen,
        val page: MutableIntState,
        val groupIndex: MutableIntState,
        val group: SettingsGroup,
        val platform: SettingsPlatform,
        val onOutput: (SettingsOutput) -> Unit,
    ) {
        fun wake() {
            screen.previewWake++
        }

        fun dispatch(action: SettingsAction) {
            val reduction = SettingsReducer.reduce(
                state = SettingsState(
                    opening.renderModel, page.intValue, group, opening.info, platform,
                    opening.capture, opening.swapped, opening.swappedAt, opening.rejectAt, opening.resetArmed,
                ),
                action = action,
            )
            val next = reduction.state
            opening.renderModel = next.model
            if (next.group != group || next.page != page.intValue) opening.activeRow = null
            page.intValue = next.page
            groupIndex.intValue = next.group.ordinal
            opening.info = next.info
            if (next.capture != null && opening.capture == null) screen.captureIgnoredKey = screen.heldKey
            opening.capture = next.capture
            opening.swapped = next.swapped
            opening.swappedAt = next.swappedAt
            opening.rejectAt = next.rejectAt
            opening.resetArmed = next.resetArmed
            reduction.effects.forEach(::runEffect)
            wake()
        }

        private fun runEffect(effect: SettingsEffect) {
            when (effect) {
                is SettingsEffect.AdjustPreference -> {
                    profilePort.accept(ProfilePulse.AdjustPreference(effect.adjustment))
                    opening.renderModel = profilePort
                        .query(ProfileQuery.GetPreferences)
                        .preferences
                        .toRenderModel()
                    audioExecutor.updatePreferences(opening.renderModel.preferences)
                    onOutput(SettingsOutput.LanguageChanged(opening.renderModel.preferences.language))
                }
                is SettingsEffect.PlayAudio -> audioExecutor.play(effect.cue)
                is SettingsEffect.Emit -> onOutput(effect.output)
                // Synchronously, inside the press that asked for it (browsers require a user gesture).
                is SettingsEffect.SetBrowserFullscreen -> display.setBrowserFullscreen(effect.on)
                SettingsEffect.ReapplyDisplay -> display.reapply()
                is SettingsEffect.Announce -> {
                    opening.noticeSequence += 1
                    opening.notice = SettingsNotice(effect.announcement, opening.noticeSequence)
                }
            }
        }

        /** Every key event while a key cell waits for a key: consumed, so no shortcut or focus move sees it. */
        fun captureKey(event: KeyEvent) {
            val ignored = screen.captureIgnoredKey
            if (event.type == KeyEventType.KeyUp) {
                if (event.key == ignored) screen.captureIgnoredKey = null
                return
            }
            if (event.type != KeyEventType.KeyDown || event.key == ignored) return
            val key = event.key.toBindableKey()
            dispatch(when {
                event.key == Key.Escape -> SettingsAction.CancelCapture
                key != null -> SettingsAction.CaptureKey(key, screen.previewTime)
                else -> SettingsAction.CaptureRejected(screen.previewTime)
            })
            if (opening.capture == null) screen.releaseKey = event.key
        }

        /** Every key event before the focused control sees it: a capture takes it, and so does the rest of the press that ended one. */
        fun previewKey(event: KeyEvent): Boolean {
            when (event.type) {
                KeyEventType.KeyDown -> {
                    screen.heldKey = event.key
                    if (screen.releaseKey != event.key) screen.releaseKey = null
                }
                KeyEventType.KeyUp -> if (screen.heldKey == event.key) screen.heldKey = null
            }
            return when {
                opening.capture != null -> {
                    captureKey(event)
                    true
                }
                event.key == screen.releaseKey -> {
                    if (event.type == KeyEventType.KeyUp) screen.releaseKey = null
                    true
                }
                else -> false
            }
        }

        fun focusChanged(target: SettingsTarget, focused: Boolean) {
            if (focused) {
                screen.focus = target
                target.row?.let { opening.activeRow = it }
                wake()
            } else if (screen.focus == target) {
                screen.focus = null
            }
        }

        /** Hover follows the pointer over [layout], and so does the active row (its band reaches 20 dp left of the row). */
        suspend fun trackPointer(scope: PointerInputScope, layout: SettingsLayout, density: Float) {
            scope.awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val position = event.changes.firstOrNull()?.position ?: continue
                    when (event.type) {
                        PointerEventType.Exit -> screen.hover = null
                        PointerEventType.Move, PointerEventType.Enter -> {
                            val target = layout.targetAt(position.x, position.y)
                            if (target != screen.hover) screen.hover = target
                            val row = layout.rows.firstOrNull { position.y >= it.bounds.top && position.y < it.bounds.bottom &&
                                position.x >= it.bounds.left - 20f * density && position.x <= it.bounds.right }?.row
                            if (row != null && row != opening.activeRow) {
                                opening.activeRow = row
                                wake()
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * One opening of Settings (its route token): the preferences shown, the open (!) and the active
     * row, key capture and the two-press reset (Interaction-ephemeral), and the live region's words.
     */
    private class SettingsOpening(model: SettingsRenderModel, activeRow: SettingsRow?) {
        var renderModel by mutableStateOf(model)
        var info by mutableStateOf<SettingsRow?>(null)
        var activeRow by mutableStateOf(activeRow)
        var capture by mutableStateOf<GameAction?>(null)
        var swapped by mutableStateOf<GameAction?>(null)
        var swappedAt by mutableFloatStateOf(SETTINGS_NEVER)
        var rejectAt by mutableFloatStateOf(SETTINGS_NEVER)
        var resetArmed by mutableStateOf(false)
        // The live region's words, cleared after a while. Words equal to those it still says are cleared
        // first, so a second refused key is a new change of the region and is spoken again.
        var notice by mutableStateOf<SettingsNotice?>(null)
        var noticeSequence by mutableIntStateOf(0)
        var announcement by mutableStateOf<SettingsAnnouncement?>(null)

        suspend fun announce(notice: SettingsNotice) {
            if (announcement == notice.announcement) {
                announcement = null
                delay(SETTINGS_ANNOUNCE_GAP_MS)
            }
            announcement = notice.announcement
            delay(SETTINGS_ANNOUNCE_MS)
            announcement = null
        }
    }

    /**
     * Settings state that outlives an opening: the viewport, hover and keyboard focus, the preview
     * clock and what wakes it, and the keys of the press in flight.
     */
    private class SettingsScreen {
        var viewport by mutableStateOf(IntSize.Zero)
        var hover by mutableStateOf<SettingsTarget?>(null)
        var focus by mutableStateOf<SettingsTarget?>(null)
        var previewTime by mutableFloatStateOf(0f)
        var previewWake by mutableIntStateOf(0)
        // The key held when a capture began (Enter or Space on the cell): its repeats are not a choice.
        var heldKey: Key? = null
        var captureIgnoredKey: Key? = null
        // The key that ended a capture: its repeats and release still belong to that press, so they
        // reach no shortcut (holding Escape does not also close Settings, a new S does not toggle it).
        var releaseKey: Key? = null

        /** Advances [previewTime] frame by frame, for a while after a wake and as long as a key cell is [capturing]. */
        suspend fun runPreview(frameRateCap: Int, capturing: Boolean) {
            val pacer = KkFramePacer(frameRateCap)
            var last = pacer.awaitFrame()
            val end = last + PREVIEW_AWAKE_NANOS
            while (last < end || capturing) {
                val now = pacer.awaitFrame()
                previewTime += ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
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
