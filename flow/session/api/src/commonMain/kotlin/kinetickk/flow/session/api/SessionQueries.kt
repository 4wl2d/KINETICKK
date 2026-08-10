// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

sealed interface AppSessionQuery {
    data object GetShell : AppSessionQuery
}

data class AppShellProjection(
    val instanceId: AppSessionInstanceId,
    val revision: SessionRevision,
    val base: AppDestination,
    val overlay: AppDestination?,
    val activeRunId: RunId?,
    /** Authoritative phase from the last Gameplay status/result observed by Session. */
    val gameplayPhase: GameplayRunPhase?,
    val pendingWorkflow: SessionWorkflowPhase?,
    val resetLifecycle: SessionResetLifecycle,
    val rebirthConfirmationArmed: Boolean,
    val workflowFailure: SessionWorkflowFailure?,
) {
    init {
        require(base.isBaseDestination()) { "Only Home and Gameplay may be base destinations" }
        require(overlay == null || overlay.isOverlayDestination()) {
            "Only feature routes may be Session overlays"
        }
        require((activeRunId == null) == (gameplayPhase == null)) {
            "An observed Gameplay phase must belong to the active RunId"
        }
        require(base != AppDestination.Gameplay || activeRunId != null) {
            "Gameplay base requires an active GameplayRun"
        }
    }

    val routeToken: SessionRouteToken
        get() = SessionRouteToken.from(revision)

    val active: AppDestination
        get() = overlay ?: base

    val entries: ImmutableList<AppDestination>
        get() = overlay?.let { immutableListOf(base, it) } ?: immutableListOf(base)

    val normalInputEnabled: Boolean
        get() = resetLifecycle == SessionResetLifecycle.READY && pendingWorkflow == null

    val rebirthEligible: Boolean
        get() = base == AppDestination.Home || gameplayPhase == GameplayRunPhase.VICTORY
}

interface AppSessionPort {
    val instanceId: AppSessionInstanceId

    fun accept(pulse: SessionInteractionPulse): SessionAcceptance

    fun query(query: AppSessionQuery.GetShell): AppShellProjection
}
