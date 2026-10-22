// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.ui.input.key.Key
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.interaction.workshop.api.WorkshopOutput
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSessionInputMappingTest {
    @Test
    fun globalKeysMapToTheClosedSessionShortcutInventory() {
        val expected = listOf(
            Key.S to SessionShortcut.SETTINGS,
            Key.W to SessionShortcut.WORKSHOP,
            Key.B to SessionShortcut.REBIRTH,
            Key.C to SessionShortcut.CODEX,
            Key.I to SessionShortcut.CODEX,
            Key.M to SessionShortcut.MUTE,
            Key.Escape to SessionShortcut.BACK,
            Key.Enter to SessionShortcut.ENTER,
        )

        expected.forEach { (key, shortcut) ->
            assertEquals(shortcut, key.toSessionShortcut())
        }
        assertEquals(SessionShortcut.entries.toSet(), expected.map { it.second }.toSet())
        // The old screen keys are Workshop tab aliases now, and the skill keys stay free.
        listOf(Key.L, Key.A, Key.H, Key.Z, Key.X, Key.V).forEach { assertNull(it.toSessionShortcut(), "$it") }
    }

    @Test
    fun aliasKeysOpenTheWorkshopOnATabAndHOnlyOverHome() {
        for (base in listOf(AppDestination.Home, AppDestination.Gameplay)) {
            assertEquals(WorkshopTab.MATRIX, Key.L.toWorkshopAlias(base))
            assertEquals(WorkshopTab.WEAPONS, Key.A.toWorkshopAlias(base))
            listOf(Key.W, Key.S, Key.C, Key.Z).forEach { assertNull(it.toWorkshopAlias(base), "$it over $base") }
        }
        // Characters change between runs: H is a Home key only.
        assertEquals(WorkshopTab.CHARACTERS, Key.H.toWorkshopAlias(AppDestination.Home))
        assertNull(Key.H.toWorkshopAlias(AppDestination.Gameplay))
        assertTrue(Key.H.isShellKey(AppDestination.Home))
        assertFalse(Key.H.isShellKey(AppDestination.Gameplay))
        assertFalse(Key.Z.isShellKey(AppDestination.Home))
    }

    @Test
    fun screenOutputsMapExhaustivelyToSessionOwnedPulses() {
        assertEquals(
            SessionInteractionPulse.StartRunRequested,
            HomeOutput.StartRun.toSessionPulse(),
        )
        assertEquals(
            SessionInteractionPulse.OpenOverlay(AppDestination.Codex),
            HomeOutput.OpenCodex.toSessionPulse(),
        )
        // The Home entries into the Workshop name their tab; the shell sends the tab with the overlay.
        for ((output, tab) in listOf(
            HomeOutput.OpenHangar to WorkshopTab.CHARACTERS,
            HomeOutput.OpenArmory to WorkshopTab.WEAPONS,
            HomeOutput.OpenLab to WorkshopTab.MATRIX,
        )) {
            assertEquals(SessionInteractionPulse.OpenOverlay(AppDestination.Workshop), output.toSessionPulse())
            assertEquals(tab, output.workshopTab())
        }
        assertNull(HomeOutput.OpenCodex.workshopTab())
        assertEquals(SessionInteractionPulse.CloseOverlay, WorkshopOutput.Back.toSessionPulse())
        // Character selection goes to Session, which decides whether it is between runs.
        assertEquals(
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            WorkshopOutput.SelectCharacter(CoreShape.PRISM).toSessionPulse(),
        )
        assertEquals(
            SessionInteractionPulse.ExitRunRequested,
            GameplayInteractionOutput.ExitToHome.toSessionPulse(),
        )
        assertEquals(
            SessionInteractionPulse.RestartRunRequested,
            GameplayInteractionOutput.RestartRun.toSessionPulse(),
        )
        assertEquals(
            SessionInteractionPulse.RebirthRequested(3),
            RebirthOutput.ArmRequested(3).toSessionPulse(),
        )
        assertEquals(
            SessionInteractionPulse.RebirthRequested(0),
            RebirthOutput.ConfirmRequested(0).toSessionPulse(),
        )
    }

    @Test
    fun onlyUnavailableBootstrapShowsTheBlockingProfileOverlay() {
        assertEquals(false, SessionLifecycle.READY.showsProfileUnavailable())
        assertEquals(true, SessionLifecycle.BOOTSTRAP_UNAVAILABLE.showsProfileUnavailable())
    }
}
