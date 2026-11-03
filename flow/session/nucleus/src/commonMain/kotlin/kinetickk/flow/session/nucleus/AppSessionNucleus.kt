// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.AppShellProjection

/** Pure coordinator for the singleton AppSession Flow. */
object AppSessionNucleus {
    fun decide(
        state: AppSessionState,
        pulse: AppSessionNucleusPulse,
        context: AppSessionContext = AppSessionContext.Empty,
    ): AppSessionDecision = when (pulse) {
        is AppSessionNucleusPulse.Intent -> decideInteraction(state, pulse.intent, context)
        is ProfileRebirthTierSelectedPulse -> completeRebirthProfileResult(state, pulse.result, context)
        is ProfileRebirthRefusedPulse -> rejectRebirth(state)
        is GameplayRunExitedPulse -> completeExitResult(state, pulse.result)
        is GameplayExitRefusedPulse -> rejectExit(state, pulse)
        is ProfileSettingsChangedPulse -> completeMuteProfileResult(state, pulse.result)
        is GameplaySettingsAppliedPulse -> completeGameplaySettings(state, pulse)
        is ProfileSettingsRefusedPulse -> rejectProfileSettings(state)
        is GameplaySettingsRefusedPulse -> rejectGameplaySettings(state, pulse)
        is ProfileCoreShapeSelectedPulse -> completeCoreShape(state, pulse.result)
        is ProfileCoreShapeRefusedPulse -> rejectCoreShape(state)
        is GameplayRunStartedPulse -> completeStart(state, pulse)
        is GameplayOverlayPausedPulse -> completePause(state, pulse)
        is GameplayStartRefusedPulse -> rejectStart(state, pulse)
        is GameplayPauseRefusedPulse -> rejectPause(state, pulse)
    }

    fun query(
        state: AppSessionState,
        query: AppSessionQuery.GetShell,
    ): AppShellProjection = when (query) {
        AppSessionQuery.GetShell -> state.toShellProjection()
    }
}
