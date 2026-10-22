// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

import kinetickk.ball.gameplay.api.RunId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

sealed interface AppSessionQuery {
    data object GetShell : AppSessionQuery
}

data class AppShellProjection(
    val instanceId: AppSessionInstanceId,
    val revision: SessionRevision,
    val routeRevision: SessionRevision,
    val base: AppDestination,
    val overlay: AppDestination?,
    val activeRunId: RunId?,
    val rebirthEligible: Boolean,
    val pendingWorkflow: SessionWorkflowPhase?,
    val lifecycle: SessionLifecycle,
    val rebirthConfirmationArmed: Boolean,
    val workflowFailure: SessionWorkflowFailureCode?,
) {
    init {
        require(base.isBaseDestination()) { "Only Home and Gameplay may be base destinations" }
        require(overlay == null || overlay.isOverlayDestination()) {
            "Only feature routes may be Session overlays"
        }
        require(base != AppDestination.Gameplay || activeRunId != null) {
            "Gameplay base requires an active GameplayRun"
        }
    }

    val routeToken: SessionRouteToken
        get() = SessionRouteToken.from(routeRevision)

    val active: AppDestination
        get() = overlay ?: base

    val entries: ImmutableList<AppDestination>
        get() = overlay?.let { immutableListOf(base, it) } ?: immutableListOf(base)

    val normalInputEnabled: Boolean
        get() = lifecycle == SessionLifecycle.READY && pendingWorkflow == null

    /**
     * Characters change between runs: the Workshop offers selection only over the Home base. Session
     * still decides every [SessionInteractionPulse.SelectCoreShapeRequested]; this only tells the UI.
     */
    val characterSelectionAvailable: Boolean
        get() = base == AppDestination.Home
}

/** Local Interaction/query facade. Participant command authority is bound separately by Impl. */
interface AppSessionPort {
    val instanceId: AppSessionInstanceId

    fun accept(pulse: SessionInteractionPulse): SessionAcceptance

    fun query(query: AppSessionQuery.GetShell): AppShellProjection
}
