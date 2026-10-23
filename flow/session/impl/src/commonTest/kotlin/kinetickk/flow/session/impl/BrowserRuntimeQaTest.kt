// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Deterministic cross-platform flow QA; Wasm executes this class in an isolated browser. */
class BrowserRuntimeQaTest {
    @Test
    fun exactSixRouteInventoryOpensAndClosesEveryOverlay() {
        val inventory = listOf(
            AppDestination.Home,
            AppDestination.Gameplay,
            AppDestination.Settings,
            AppDestination.Workshop,
            AppDestination.Rebirth,
            AppDestination.Codex,
        )
        assertEquals(6, inventory.size)
        assertEquals(6, inventory.toSet().size)
        val rig = AppSessionTestRig()

        inventory.drop(2).forEach { destination ->
            assertIs<SessionAcceptance.Accepted>(
                rig.component.accept(SessionInteractionPulse.OpenOverlay(destination)),
            )
            assertEquals(destination, shell(rig).active)
            assertIs<SessionAcceptance.Accepted>(
                rig.component.accept(SessionInteractionPulse.CloseOverlay),
            )
            assertEquals(AppDestination.Home, shell(rig).active)
        }

        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        assertEquals(AppDestination.Gameplay, shell(rig).active)
    }

    @Test
    fun startPauseSettingsCloseAndExitStayDeterministic() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        assertEquals(AppDestination.Gameplay, shell(rig).base)

        rig.component.accept(SessionInteractionPulse.OpenOverlay(AppDestination.Settings))
        assertEquals(AppDestination.Settings, shell(rig).overlay)
        assertEquals(GameplayRunPhase.PAUSED, rig.component.stateSnapshot().gameplayPhase)

        rig.component.accept(SessionInteractionPulse.CloseOverlay)
        assertNull(shell(rig).overlay)
        assertNull(shell(rig).pendingWorkflow)

        rig.component.accept(SessionInteractionPulse.ExitRunRequested)
        assertEquals(AppDestination.Home, shell(rig).base)
        assertNull(shell(rig).pendingWorkflow)
    }

    @Test
    fun characterSelectionUnderTheWorkshopRunsTheProfileCommandOnlyBetweenRuns() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.OpenOverlay(AppDestination.Workshop))
        assertTrue(shell(rig).characterSelectionAvailable)
        assertIs<SessionAcceptance.Accepted>(rig.component.accept(SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM)))
        // The participant command ran inline; the Workshop stays open on the same route.
        assertEquals(listOf(CoreShape.PRISM), rig.profile.shapeCalls)
        assertEquals(AppDestination.Workshop, shell(rig).overlay)
        assertNull(shell(rig).pendingWorkflow)

        // During a run the Workshop opens (pausing it), but characters do not change.
        rig.component.accept(SessionInteractionPulse.CloseOverlay)
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        rig.component.accept(SessionInteractionPulse.OpenOverlay(AppDestination.Workshop))
        assertEquals(AppDestination.Workshop, shell(rig).overlay)
        assertEquals(GameplayRunPhase.PAUSED, rig.component.stateSnapshot().gameplayPhase)
        assertFalse(shell(rig).characterSelectionAvailable)
        val refused = assertIs<SessionAcceptance.Rejected>(
            rig.component.accept(SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.SHARD)),
        )
        assertEquals(SessionRejection.CoreShapeSelectionUnavailable, refused.reason)
        assertEquals(listOf(CoreShape.PRISM), rig.profile.shapeCalls)
    }
}

private fun shell(rig: AppSessionTestRig) = rig.component.query(AppSessionQuery.GetShell)
