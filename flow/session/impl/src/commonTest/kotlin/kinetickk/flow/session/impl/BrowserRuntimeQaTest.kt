// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Deterministic cross-platform flow QA; Wasm executes this class in an isolated browser. */
class BrowserRuntimeQaTest {
    @Test
    fun exactSevenRouteInventoryOpensAndClosesEveryOverlay() {
        val inventory = listOf(
            AppDestination.Home,
            AppDestination.Gameplay,
            AppDestination.Settings,
            AppDestination.Lab,
            AppDestination.Armory,
            AppDestination.Rebirth,
            AppDestination.Codex,
        )
        assertEquals(7, inventory.size)
        assertEquals(7, inventory.toSet().size)
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
}

private fun shell(rig: AppSessionTestRig) = rig.component.query(AppSessionQuery.GetShell)
