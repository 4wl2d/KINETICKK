// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import kinetickk.flow.session.api.SessionResetLifecycle
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.reset.api.ResetModalFeature
import kinetickk.flow.session.interaction.reset.api.ResetModalMode
import kinetickk.flow.session.interaction.reset.api.ResetModalRenderModel

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
    resetModalFeature: ResetModalFeature,
) {
    val focusRequester = remember(sessionPort) { FocusRequester() }
    var shellValue by remember(sessionPort) {
        mutableStateOf(sessionPort.query(AppSessionQuery.GetShell))
    }

    fun dispatch(pulse: SessionInteractionPulse): Boolean {
        val accepted = sessionPort.accept(pulse) is SessionAcceptance.Accepted
        shellValue = sessionPort.query(AppSessionQuery.GetShell)
        return accepted
    }

    SideEffect(sessionPort) {
        focusRequester.requestFocus()
    }

    val resetModalModel = shellValue.resetLifecycle.toResetModalRenderModelOrNull()
    val normalInputEnabled = shellValue.normalInputEnabled
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
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
                onOutput = { output -> dispatch(output.toSessionPulse()) },
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

        if (resetModalModel != null) {
            resetModalFeature.Content(resetModalModel) { output ->
                dispatch(output.toSessionPulse())
            }
        }
    }
}

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
    CodexRunStacks(
        gameplayPresentation.activePresentation()
            ?.query(GameplayQuery.GetCodexStacks)
            ?.itemStacks
            ?: kinetickk.foundation.collections.immutableListOf(),
    )
} else {
    CodexRunStacks()
}

internal fun SessionResetLifecycle.toResetModalRenderModelOrNull(): ResetModalRenderModel? =
    when (this) {
        SessionResetLifecycle.READY -> null
        SessionResetLifecycle.CONFIRMATION_REQUIRED ->
            ResetModalRenderModel(ResetModalMode.CONFIRMATION_REQUIRED)
        SessionResetLifecycle.RESET_IN_PROGRESS ->
            ResetModalRenderModel(ResetModalMode.RESET_IN_PROGRESS)
        SessionResetLifecycle.PURGE_NEEDS_ATTENTION ->
            ResetModalRenderModel(ResetModalMode.PURGE_NEEDS_ATTENTION)
        SessionResetLifecycle.BOOTSTRAP_UNAVAILABLE ->
            ResetModalRenderModel(ResetModalMode.BOOTSTRAP_UNAVAILABLE)
    }
