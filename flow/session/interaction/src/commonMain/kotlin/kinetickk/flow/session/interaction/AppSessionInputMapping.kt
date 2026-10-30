// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.ui.input.key.Key
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.interaction.workshop.api.WorkshopOutput
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.interaction.codex.api.CodexOutput
import kinetickk.flow.session.interaction.home.api.HomeOutput

internal fun Key.toSessionShortcut(): SessionShortcut? = when (this) {
    Key.S -> SessionShortcut.SETTINGS
    Key.W -> SessionShortcut.WORKSHOP
    Key.B -> SessionShortcut.REBIRTH
    Key.C, Key.I -> SessionShortcut.CODEX
    Key.M -> SessionShortcut.MUTE
    Key.Escape -> SessionShortcut.BACK
    Key.Enter -> SessionShortcut.ENTER
    else -> null
}

/**
 * Presentation aliases that open the Workshop on a tab: L the Core Matrix, A the weapons and, over
 * the Home base only, H the characters. Session still decides whether the Workshop opens (and
 * whether a character may be selected); the tab is the shell's request to the Workshop.
 */
internal fun Key.toWorkshopAlias(base: AppDestination): WorkshopTab? = when (this) {
    Key.L -> WorkshopTab.MATRIX
    Key.A -> WorkshopTab.WEAPONS
    Key.H -> if (base == AppDestination.Home) WorkshopTab.CHARACTERS else null
    else -> null
}

/** E over the Home base toggles Baby mode (Session decides; it accepts only a bare Home). Free in a run. */
internal fun Key.isRunModeKey(base: AppDestination): Boolean = this == Key.E && base == AppDestination.Home

/** The other run mode: what the Baby mode toggle and its E key ask Session for. */
internal fun RunMode.toggled(): RunMode = if (this == RunMode.EASY) RunMode.STANDARD else RunMode.EASY

/** True for a key the shell maps: a session shortcut, a Workshop alias or the run-mode key over [base]. */
internal fun Key.isShellKey(base: AppDestination): Boolean =
    toSessionShortcut() != null || toWorkshopAlias(base) != null || isRunModeKey(base)

/** Null for [HomeOutput.Quit]: closing the game is the host's, not a Session transition. */
internal fun HomeOutput.toSessionPulse(): SessionInteractionPulse? = when (this) {
    HomeOutput.OpenWorkshop -> SessionInteractionPulse.OpenOverlay(AppDestination.Workshop)
    HomeOutput.StartRun -> SessionInteractionPulse.StartRunRequested
    HomeOutput.OpenSettings -> SessionInteractionPulse.OpenOverlay(AppDestination.Settings)
    HomeOutput.OpenRebirth -> SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth)
    HomeOutput.OpenCodex -> SessionInteractionPulse.OpenOverlay(AppDestination.Codex)
    HomeOutput.Quit -> null
    is HomeOutput.SelectRunMode -> SessionInteractionPulse.SelectRunModeRequested(mode)
}

internal fun GameplayInteractionOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    GameplayInteractionOutput.OpenCodex -> SessionInteractionPulse.OpenOverlay(AppDestination.Codex)
    GameplayInteractionOutput.OpenSettings ->
        SessionInteractionPulse.OpenOverlay(AppDestination.Settings)
    // The pause menu's Workshop: Session opens it over the paused run on the tab shown last.
    GameplayInteractionOutput.OpenWorkshop ->
        SessionInteractionPulse.OpenOverlay(AppDestination.Workshop)
    GameplayInteractionOutput.OpenRebirth ->
        SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth)
    GameplayInteractionOutput.ExitToHome -> SessionInteractionPulse.ExitRunRequested
    GameplayInteractionOutput.RestartRun -> SessionInteractionPulse.RestartRunRequested
}

internal fun SettingsOutput.toSessionPulse(): SessionInteractionPulse? = when (this) {
    is SettingsOutput.LanguageChanged -> null
    SettingsOutput.Back -> SessionInteractionPulse.CloseOverlay
}

internal fun WorkshopOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    WorkshopOutput.Back -> SessionInteractionPulse.CloseOverlay
    is WorkshopOutput.SelectCharacter -> SessionInteractionPulse.SelectCoreShapeRequested(shape)
}

internal fun RebirthOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    RebirthOutput.Back -> SessionInteractionPulse.CloseOverlay
    is RebirthOutput.ArmRequested -> SessionInteractionPulse.RebirthRequested(tier)
    is RebirthOutput.ConfirmRequested -> SessionInteractionPulse.RebirthRequested(tier)
}

internal fun CodexOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    CodexOutput.Back -> SessionInteractionPulse.CloseOverlay
}
