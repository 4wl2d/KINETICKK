// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.RunMode
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
    fun shellProjectionRetainsExactlySixRoutesAndOnlySessionOwnedWorkflowState() {
        val destinations = listOf(
            AppDestination.Home,
            AppDestination.Gameplay,
            AppDestination.Settings,
            AppDestination.Workshop,
            AppDestination.Rebirth,
            AppDestination.Codex,
        )
        assertEquals(6, destinations.size)
        assertEquals(2, destinations.count(AppDestination::isBaseDestination))
        assertEquals(4, destinations.count(AppDestination::isOverlayDestination))

        val projection = AppShellProjection(
            instanceId = LOCAL_APP_SESSION_INSTANCE_ID,
            revision = SessionRevision(12L),
            routeRevision = SessionRevision(9L),
            base = AppDestination.Gameplay,
            overlay = AppDestination.Rebirth,
            activeRunId = RunId(4L),
            rebirthEligible = true,
            pendingWorkflow = null,
            lifecycle = SessionLifecycle.READY,
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
        // Characters change between runs: selection is offered over the Home base only, under any overlay.
        assertFalse(projection.characterSelectionAvailable)
        assertTrue(projection.copy(base = AppDestination.Home, overlay = AppDestination.Workshop, activeRunId = null)
            .characterSelectionAvailable)
        assertFailsWith<IllegalArgumentException> { projection.copy(base = AppDestination.Settings) }
        assertFailsWith<IllegalArgumentException> { projection.copy(base = AppDestination.Workshop) }
        assertFailsWith<IllegalArgumentException> { projection.copy(overlay = AppDestination.Home) }
        assertFailsWith<IllegalArgumentException> { projection.copy(activeRunId = null) }
    }

    @Test
    fun theShellOffersTheStandardRunModeUnlessSessionSaysOtherwise() {
        val home = homeProjection()

        assertEquals(RunMode.STANDARD, home.runMode)
        assertEquals(RunMode.EASY, home.copy(runMode = RunMode.EASY).runMode)
        assertEquals(home, home.copy(runMode = RunMode.STANDARD))
    }

    @Test
    fun unavailableBootstrapAndPendingWorkflowBothBlockNormalInput() {
        val ready = homeProjection()

        assertTrue(ready.normalInputEnabled)
        assertFalse(
            ready.copy(
                lifecycle = SessionLifecycle.BOOTSTRAP_UNAVAILABLE,
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
            SessionInteractionPulse.RebirthRequested(1),
            SessionInteractionPulse.SelectRunModeRequested(RunMode.EASY),
        )

        assertEquals(10, interactions.size)
        assertIs<SessionInteractionPulse.SelectCoreShapeRequested>(interactions[7])
        assertEquals(RunMode.EASY, assertIs<SessionInteractionPulse.SelectRunModeRequested>(interactions[9]).mode)
        assertEquals(
            setOf(
                SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED,
                SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED,
                SessionWorkflowFailureCode.EXIT_PROGRESS_NOT_APPLIED,
            ),
            SessionWorkflowFailureCode.entries.toSet(),
        )
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
    lifecycle = SessionLifecycle.READY,
    rebirthConfirmationArmed = false,
    workflowFailure = null,
)
