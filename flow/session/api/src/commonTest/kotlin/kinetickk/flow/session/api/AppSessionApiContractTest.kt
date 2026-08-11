// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.RunId
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppSessionApiContractTest {
    @Test
    fun singletonIdentityAndRouteTokenKeepIdentitySeparateFromRevision() {
        assertEquals(
            "kinetickk.local/AppSession/local-session",
            LOCAL_APP_SESSION_INSTANCE_ID.canonicalValue,
        )
        assertEquals(SessionRevision.ZERO, SessionRevision(0L))
        assertEquals(SessionRouteToken(9L), SessionRouteToken.from(SessionRevision(9L)))
        assertFailsWith<IllegalArgumentException> { SessionRevision(-1L) }
        assertFailsWith<IllegalArgumentException> { SessionRouteToken(-1L) }
    }

    @Test
    fun shellProjectionRetainsExactlySevenRoutesAndOnlySessionOwnedWorkflowState() {
        val destinations = listOf(
            AppDestination.Home,
            AppDestination.Gameplay,
            AppDestination.Settings,
            AppDestination.Lab,
            AppDestination.Armory,
            AppDestination.Rebirth,
            AppDestination.Codex,
        )
        assertEquals(7, destinations.size)
        assertEquals(2, destinations.count(AppDestination::isBaseDestination))
        assertEquals(5, destinations.count(AppDestination::isOverlayDestination))

        val projection = AppShellProjection(
            instanceId = LOCAL_APP_SESSION_INSTANCE_ID,
            revision = SessionRevision(12L),
            routeRevision = SessionRevision(9L),
            base = AppDestination.Gameplay,
            overlay = AppDestination.Rebirth,
            activeRunId = RunId(4L),
            rebirthEligible = true,
            pendingWorkflow = null,
            resetLifecycle = SessionResetLifecycle.READY,
            rebirthConfirmationArmed = true,
            workflowFailure = null,
        )

        assertEquals(SessionRouteToken(9L), projection.routeToken)
        assertEquals(AppDestination.Rebirth, projection.active)
        assertEquals(
            immutableListOf(AppDestination.Gameplay, AppDestination.Rebirth),
            projection.entries,
        )
        assertTrue(projection.rebirthEligible)
        assertTrue(projection.normalInputEnabled)
        assertFailsWith<IllegalArgumentException> { projection.copy(base = AppDestination.Settings) }
        assertFailsWith<IllegalArgumentException> { projection.copy(overlay = AppDestination.Home) }
        assertFailsWith<IllegalArgumentException> { projection.copy(activeRunId = null) }
    }

    @Test
    fun resetAndPendingWorkflowBothBlockNormalInput() {
        val ready = homeProjection()

        assertTrue(ready.normalInputEnabled)
        assertFalse(
            ready.copy(
                resetLifecycle = SessionResetLifecycle.CONFIRMATION_REQUIRED,
            ).normalInputEnabled,
        )
        assertFalse(
            ready.copy(
                pendingWorkflow = SessionWorkflowPhase.STARTING_RUN,
            ).normalInputEnabled,
        )
        assertTrue(ready.rebirthEligible)
    }

    @Test
    fun publicIntentInventoryContainsNoParticipantControlCarrier() {
        val interactions: List<SessionInteractionPulse> = listOf(
            SessionInteractionPulse.StartRunRequested,
            SessionInteractionPulse.RestartRunRequested,
            SessionInteractionPulse.ExitRunRequested,
            SessionInteractionPulse.OpenOverlay(AppDestination.Settings),
            SessionInteractionPulse.CloseOverlay,
            SessionInteractionPulse.ShortcutObserved(SessionShortcut.MUTE),
            SessionInteractionPulse.ToggleMuteRequested,
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            SessionInteractionPulse.RebirthRequested,
            SessionInteractionPulse.ResetCancelled,
            SessionInteractionPulse.ResetConfirmed,
            SessionInteractionPulse.ResetRetryRequested,
        )

        assertEquals(12, interactions.size)
        assertIs<SessionInteractionPulse.SelectCoreShapeRequested>(interactions[7])
        assertEquals(6, SessionWorkflowFailureCode.entries.size)
    }
}

private fun homeProjection(): AppShellProjection = AppShellProjection(
    instanceId = LOCAL_APP_SESSION_INSTANCE_ID,
    revision = SessionRevision.ZERO,
    routeRevision = SessionRevision.ZERO,
    base = AppDestination.Home,
    overlay = null,
    activeRunId = null,
    rebirthEligible = true,
    pendingWorkflow = null,
    resetLifecycle = SessionResetLifecycle.READY,
    rebirthConfirmationArmed = false,
    workflowFailure = null,
)
