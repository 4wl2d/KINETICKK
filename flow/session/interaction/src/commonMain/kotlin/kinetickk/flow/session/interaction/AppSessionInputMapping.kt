// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.ui.input.key.Key
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.lab.api.LabOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.interaction.codex.api.CodexOutput
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.reset.api.ResetModalOutput

internal fun Key.toSessionShortcut(): SessionShortcut? = when (this) {
    Key.S -> SessionShortcut.SETTINGS
    Key.L -> SessionShortcut.LAB
    Key.A -> SessionShortcut.ARMORY
    Key.B -> SessionShortcut.REBIRTH
    Key.C -> SessionShortcut.CODEX
    Key.M -> SessionShortcut.MUTE
    Key.Escape -> SessionShortcut.BACK
    Key.Enter -> SessionShortcut.ENTER
    else -> null
}

internal fun HomeOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    is HomeOutput.SelectCoreShape -> SessionInteractionPulse.SelectCoreShapeRequested(shape)
    HomeOutput.StartRun -> SessionInteractionPulse.StartRunRequested
    HomeOutput.OpenSettings -> SessionInteractionPulse.OpenOverlay(AppDestination.Settings)
    HomeOutput.OpenLab -> SessionInteractionPulse.OpenOverlay(AppDestination.Lab)
    HomeOutput.OpenArmory -> SessionInteractionPulse.OpenOverlay(AppDestination.Armory)
    HomeOutput.OpenRebirth -> SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth)
    HomeOutput.OpenCodex -> SessionInteractionPulse.OpenOverlay(AppDestination.Codex)
}

internal fun GameplayInteractionOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    GameplayInteractionOutput.OpenSettings ->
        SessionInteractionPulse.OpenOverlay(AppDestination.Settings)
    GameplayInteractionOutput.OpenRebirth ->
        SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth)
    GameplayInteractionOutput.ExitToHome -> SessionInteractionPulse.ExitRunRequested
    GameplayInteractionOutput.RestartRun -> SessionInteractionPulse.RestartRunRequested
}

internal fun SettingsOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    SettingsOutput.Back -> SessionInteractionPulse.CloseOverlay
}

internal fun LabOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    LabOutput.Back -> SessionInteractionPulse.CloseOverlay
}

internal fun ArmoryOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    ArmoryOutput.Back -> SessionInteractionPulse.CloseOverlay
}

internal fun RebirthOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    RebirthOutput.Back -> SessionInteractionPulse.CloseOverlay
    RebirthOutput.ArmRequested,
    RebirthOutput.ConfirmRequested,
    -> SessionInteractionPulse.RebirthRequested
}

internal fun CodexOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    CodexOutput.Back -> SessionInteractionPulse.CloseOverlay
}

internal fun ResetModalOutput.toSessionPulse(): SessionInteractionPulse = when (this) {
    ResetModalOutput.Cancel -> SessionInteractionPulse.ResetCancelled
    ResetModalOutput.ConfirmDelete -> SessionInteractionPulse.ResetConfirmed
    ResetModalOutput.RetryPurge -> SessionInteractionPulse.ResetRetryRequested
}
