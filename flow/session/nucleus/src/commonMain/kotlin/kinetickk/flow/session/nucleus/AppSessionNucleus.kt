// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.flow.session.api.AppShellProjection

/** Pure coordinator for the singleton AppSession Flow. */
object AppSessionNucleus {
    fun decide(
        state: AppSessionState,
        pulse: AppSessionNucleusPulse,
        context: AppSessionContext = AppSessionContext.Empty,
    ): AppSessionDecision = when (pulse) {
        is AppSessionNucleusPulse.Intent -> decideInteraction(state, pulse.intent, context)
        is AppSessionNucleusPulse.Result.ProfileRebirthTierSelected -> completeRebirthProfileResult(state, pulse.result, context)
        is AppSessionNucleusPulse.Refusal.ProfileRebirthRefused -> rejectRebirth(state)
        is AppSessionNucleusPulse.Result.GameplayRunExited -> completeExitResult(state, pulse.result)
        is AppSessionNucleusPulse.Refusal.GameplayExitRefused -> rejectExit(state, pulse)
        is AppSessionNucleusPulse.Result.ProfileSettingsChanged -> completeMuteProfileResult(state, pulse.result)
        is AppSessionNucleusPulse.Result.GameplaySettingsApplied -> completeGameplaySettings(state, pulse)
        is AppSessionNucleusPulse.Refusal.ProfileSettingsRefused -> rejectProfileSettings(state)
        is AppSessionNucleusPulse.Refusal.GameplaySettingsRefused -> rejectGameplaySettings(state, pulse)
        is AppSessionNucleusPulse.Result.ProfileCoreShapeSelected -> completeCoreShape(state, pulse.result)
        is AppSessionNucleusPulse.Refusal.ProfileCoreShapeRefused -> rejectCoreShape(state)
        is AppSessionNucleusPulse.Result.GameplayRunStarted -> completeStart(state, pulse)
        is AppSessionNucleusPulse.Result.GameplayOverlayPaused -> completePause(state, pulse)
        is AppSessionNucleusPulse.Refusal.GameplayStartRefused -> rejectStart(state, pulse)
        is AppSessionNucleusPulse.Refusal.GameplayPauseRefused -> rejectPause(state, pulse)
    }

    fun shell(state: AppSessionState): AppShellProjection = state.toShellProjection()
}
