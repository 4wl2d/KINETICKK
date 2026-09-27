// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkShutter
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalCrashDiagnostics
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.drawKkShutter
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileQuery
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.interaction.GameplayPresentation
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.lab.api.LabFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionPort
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.profile.api.ProfileUnavailableFeature

/** Renders the closed AppSession route projection and translates UI outputs into Session Pulses. */
@Composable
fun AppSessionContent(
    sessionPort: AppSessionPort,
    audioExecutor: SessionAudioExecutor,
    gameplayPresentation: GameplayPresentation,
    homeFeature: HomeFeature,
    settingsFeature: SettingsFeature,
    labFeature: LabFeature,
    armoryFeature: ArmoryFeature,
    rebirthFeature: RebirthFeature,
    codexFeature: CodexFeature,
    profileUnavailableFeature: ProfileUnavailableFeature,
    initialLanguage: AppLanguage = AppLanguage.English,
    profileReadPort: ProfileReadPort? = null,
    onLanguageChanged: (AppLanguage) -> Unit = {},
) {
    val diagnostics = LocalCrashDiagnostics.current
    var languageValue by remember(sessionPort, profileReadPort) {
        mutableStateOf(profileReadPort?.query(ProfileQuery.GetPreferences)?.preferences?.language ?: initialLanguage)
    }
    val focusRequester = remember(sessionPort) { FocusRequester() }
    var shellValue by remember(sessionPort) {
        mutableStateOf(sessionPort.query(AppSessionQuery.GetShell))
    }
    val observedShell = shellValue
    SideEffect(observedShell) {
        diagnostics.context("session.committed") { observedShell.toString() }
    }

    fun dispatch(pulse: SessionInteractionPulse): Boolean {
        val before = shellValue
        diagnostics.context("session.before-input") { before.toString() }
        diagnostics.event("session.input", pulse.toString())
        val accepted = sessionPort.accept(pulse) is SessionAcceptance.Accepted
        shellValue = sessionPort.query(AppSessionQuery.GetShell)
        val after = shellValue
        diagnostics.context("session.committed") { after.toString() }
        return accepted
    }

    SideEffect(sessionPort, shellValue.base, shellValue.overlay) {
        // Establish fallback routing after an overlay disposes its focused control.
        // Home can focus a semantic action after layout; Gameplay restores its own focus.
        if (shellValue.base == AppDestination.Home && shellValue.overlay == null) {
            focusRequester.requestFocus()
        }
    }

    SideEffect(languageValue, onLanguageChanged) { onLanguageChanged(languageValue) }

    val normalInputEnabled = shellValue.normalInputEnabled
    // Screen changes: the new destination composes and takes input at once; the previous frame
    // (kept for menus) stays on top until the shutter's ink slab covers the screen.
    val transition = remember(sessionPort) { SessionTransitionState() }
    val snapshot = rememberGraphicsLayer()
    SideEffect(shellValue.base, shellValue.overlay) {
        transition.show(shellValue.base, shellValue.overlay)
    }
    LaunchedEffect(transition.id) {
        if (transition.kind == SessionTransitionKind.NONE) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (transition.elapsedMs < transition.kind.durationMs) {
            withFrameNanos { frame -> transition.elapsedMs = (frame - start) / 1_000_000f }
        }
        transition.finish()
    }
    CompositionLocalProvider(LocalAppLanguage provides languageValue) {
        val roles = LocalKkRolePalette.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (transition.recording) {
                        snapshot.record { this@drawWithContent.drawContent() }
                        drawLayer(snapshot)
                        transition.recorded()
                    } else {
                        drawContent()
                    }
                    val elapsed = transition.elapsedMs
                    when (transition.kind) {
                        SessionTransitionKind.NONE -> Unit
                        SessionTransitionKind.SHUTTER -> {
                            if (elapsed < KkShutter.SWAP_MS) drawLayer(snapshot)
                            drawKkShutter(elapsed, roles)
                        }
                        SessionTransitionKind.CROSSFADE ->
                            drawRect(Kk.Ink, alpha = (1f - elapsed / SessionTransitionKind.CROSSFADE.durationMs).coerceIn(0f, 1f))
                    }
                }
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    // Codex owns text entry, slot activation and its two-step Escape.
                    if (shellValue.overlay == AppDestination.Codex) return@onPreviewKeyEvent false
                    // Settings owns slider keys and numeric editing (including Ctrl/Cmd+A).
                    if (shellValue.overlay == AppDestination.Settings) return@onPreviewKeyEvent false
                    // Let a focused semantic control own Enter. When focus remains on
                    // this root, the bubble handler below preserves the global shortcut.
                    if (event.key == Key.Enter) return@onPreviewKeyEvent false
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    audioExecutor.ensureUnlocked()
                    val shortcut = event.key.toSessionShortcut()
                        ?: return@onPreviewKeyEvent false
                    dispatch(SessionInteractionPulse.ShortcutObserved(shortcut))
                }
                .onKeyEvent { event ->
                    if (shellValue.overlay == AppDestination.Codex) return@onKeyEvent true
                    if (shellValue.overlay == AppDestination.Settings) {
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val shortcut = event.key.toSessionShortcut() ?: return@onKeyEvent false
                        audioExecutor.ensureUnlocked()
                        return@onKeyEvent dispatch(SessionInteractionPulse.ShortcutObserved(shortcut))
                    }
                    if (event.key != Key.Enter || event.type != KeyEventType.KeyDown) {
                        return@onKeyEvent false
                    }
                    audioExecutor.ensureUnlocked()
                    dispatch(SessionInteractionPulse.ShortcutObserved(SessionShortcut.ENTER))
                }
                .focusable(),
        ) {
            when (shellValue.base) {
                AppDestination.Home -> homeFeature.Content(
                    inputEnabled = normalInputEnabled && shellValue.overlay == null,
                    onOutput = { output -> dispatch(output.toSessionPulse()) },
                )
                AppDestination.Gameplay -> gameplayPresentation.Content(
                    inputEnabled = normalInputEnabled && shellValue.overlay == null,
                    onOutput = { output -> dispatch(output.toSessionPulse()) },
                )
                AppDestination.Settings,
                AppDestination.Lab,
                AppDestination.Armory,
                AppDestination.Rebirth,
                AppDestination.Codex,
                -> error("Only Home and Gameplay may be base destinations")
            }

            when (shellValue.overlay.takeIf { normalInputEnabled }) {
                null -> Unit
                AppDestination.Settings -> settingsFeature.Content(
                    routeToken = shellValue.routeToken.value,
                    onOutput = { output ->
                        when (output) {
                            is SettingsOutput.LanguageChanged -> languageValue = output.language
                            SettingsOutput.Back -> output.toSessionPulse()?.let { dispatch(it) }
                        }
                    },
                )
                AppDestination.Lab -> labFeature.Content(
                    routeToken = shellValue.routeToken.value,
                    onOutput = { output -> dispatch(output.toSessionPulse()) },
                )
                AppDestination.Armory -> armoryFeature.Content(
                    activeRunWeapon = activeGameplayWeapon(shellValue, gameplayPresentation),
                    onOutput = { output -> dispatch(output.toSessionPulse()) },
                )
                AppDestination.Rebirth -> rebirthFeature.Content(
                    routeToken = shellValue.routeToken.value,
                    eligible = shellValue.rebirthEligible,
                    confirmationArmed = shellValue.rebirthConfirmationArmed,
                    onOutput = { output -> dispatch(output.toSessionPulse()) },
                )
                AppDestination.Codex -> codexFeature.Content(
                    runStacks = currentRunStacks(shellValue, gameplayPresentation),
                    onOutput = { output -> dispatch(output.toSessionPulse()) },
                )
                AppDestination.Home,
                AppDestination.Gameplay,
                -> error("Base destinations cannot be overlays")
            }

            if (shellValue.lifecycle.showsProfileUnavailable()) {
                profileUnavailableFeature.Content()
            }
        }
    }
}

/** Screen-change styles (Motion board): the shutter, or a 150 ms fade from ink. */
internal enum class SessionTransitionKind(val durationMs: Float) {
    NONE(0f),
    SHUTTER(KkShutter.TOTAL_MS.toFloat()),
    CROSSFADE(150f),
}

/**
 * The in-run screen is not recorded every frame (it is the hot path), so leaving it fades the
 * next screen in; menus keep their last frame for the shutter to cover.
 */
internal fun sessionRecordsSnapshot(base: AppDestination, overlay: AppDestination?): Boolean =
    base != AppDestination.Gameplay || overlay != null

/** Chooses how the shell moves from the shown destinations to the next ones. */
internal fun sessionTransitionKind(
    previousBase: AppDestination?,
    previousOverlay: AppDestination?,
    nextBase: AppDestination,
    nextOverlay: AppDestination?,
    snapshotAvailable: Boolean,
): SessionTransitionKind = when {
    previousBase == null || (previousBase == nextBase && previousOverlay == nextOverlay) -> SessionTransitionKind.NONE
    snapshotAvailable -> SessionTransitionKind.SHUTTER
    else -> SessionTransitionKind.CROSSFADE
}

/**
 * Draw-side transition state. [recording] and the snapshot flags are read and written on the
 * draw/apply thread only; [kind], [elapsedMs] and [id] are snapshot state so the overlay redraws.
 */
internal class SessionTransitionState {
    private var shownBase: AppDestination? = null
    private var shownOverlay: AppDestination? = null
    private var hasSnapshot = false
    var kind by mutableStateOf(SessionTransitionKind.NONE)
        private set
    var elapsedMs by mutableFloatStateOf(0f)
    var id by mutableIntStateOf(0)
        private set

    /** True while the shown screen's frames are recorded (menus, not during a shutter). */
    val recording: Boolean
        get() = kind != SessionTransitionKind.SHUTTER && shownBase?.let { sessionRecordsSnapshot(it, shownOverlay) } == true

    fun show(base: AppDestination, overlay: AppDestination?) {
        if (base == shownBase && overlay == shownOverlay) return
        val previousBase = shownBase
        // A change during a running shutter has no fresh frame of the shown screen: fade instead.
        val snapshotAvailable = previousBase != null && sessionRecordsSnapshot(previousBase, shownOverlay) &&
            hasSnapshot && kind != SessionTransitionKind.SHUTTER
        val next = sessionTransitionKind(previousBase, shownOverlay, base, overlay, snapshotAvailable)
        shownBase = base
        shownOverlay = overlay
        // The snapshot holds the previous screen until the shutter swaps it out.
        hasSnapshot = next == SessionTransitionKind.SHUTTER
        if (next != SessionTransitionKind.NONE) {
            kind = next
            elapsedMs = 0f
            id++
        }
    }

    fun recorded() {
        hasSnapshot = true
    }

    fun finish() {
        kind = SessionTransitionKind.NONE
    }
}

internal fun SessionLifecycle.showsProfileUnavailable(): Boolean =
    this == SessionLifecycle.BOOTSTRAP_UNAVAILABLE

internal fun activeGameplayWeapon(
    shell: AppShellProjection,
    gameplayPresentation: GameplayPresentation,
) = if (shell.base == AppDestination.Gameplay) {
    gameplayPresentation.activePresentation()?.query(GameplayQuery.GetActiveWeapon)?.weapon
} else {
    null
}

internal fun currentRunStacks(
    shell: AppShellProjection,
    gameplayPresentation: GameplayPresentation,
): CodexRunStacks = if (shell.base == AppDestination.Gameplay) {
    val build = gameplayPresentation.activePresentation()?.query(GameplayQuery.GetBuildSummary)
    CodexRunStacks(
        itemStacks = build?.itemStacks ?: kinetickk.foundation.collections.immutableListOf(),
        build = build,
    )
} else {
    CodexRunStacks()
}
