// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.ui.input.key.Key
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AppSessionInputMappingTest {
    @Test
    fun globalKeysMapToTheClosedSessionShortcutInventory() {
        val expected = listOf(
            Key.S to SessionShortcut.SETTINGS,
            Key.L to SessionShortcut.LAB,
            Key.A to SessionShortcut.ARMORY,
            Key.B to SessionShortcut.REBIRTH,
            Key.C to SessionShortcut.CODEX,
            Key.M to SessionShortcut.MUTE,
            Key.Escape to SessionShortcut.BACK,
            Key.Enter to SessionShortcut.ENTER,
        )

        expected.forEach { (key, shortcut) ->
            assertEquals(shortcut, key.toSessionShortcut())
        }
        assertNull(Key.Z.toSessionShortcut())
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
        assertEquals(
            CoreShape.PRISM,
            assertIs<SessionInteractionPulse.SelectCoreShapeRequested>(
                HomeOutput.SelectCoreShape(CoreShape.PRISM).toSessionPulse(),
            ).shape,
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
            SessionInteractionPulse.RebirthRequested,
            RebirthOutput.ArmRequested.toSessionPulse(),
        )
        assertEquals(
            SessionInteractionPulse.RebirthRequested,
            RebirthOutput.ConfirmRequested.toSessionPulse(),
        )
    }

    @Test
    fun onlyUnavailableBootstrapShowsTheBlockingProfileOverlay() {
        assertEquals(false, SessionLifecycle.READY.showsProfileUnavailable())
        assertEquals(true, SessionLifecycle.BOOTSTRAP_UNAVAILABLE.showsProfileUnavailable())
    }
}
