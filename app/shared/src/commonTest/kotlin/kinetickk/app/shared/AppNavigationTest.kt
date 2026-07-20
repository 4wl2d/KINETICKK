// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppNavigationTest {
    @Test
    fun overlayDuringRunningRunPausesAndBackReturnsToGameplay() {
        val navigator = AppNavigator().apply { showGameplay() }

        val opened = navigator.openOverlay(AppDestination.Lab, AppGameplayPhase.RUNNING)

        assertTrue(opened.pauseGameplay)
        assertEquals(listOf(AppDestination.Gameplay, AppDestination.Lab), opened.backStack.entries)
        assertEquals(listOf(AppDestination.Gameplay), navigator.back().backStack.entries)
    }

    @Test
    fun replacingOverlayNeverCreatesFeatureToFeatureBackStack() {
        val navigator = AppNavigator()
        navigator.openOverlay(AppDestination.Settings, AppGameplayPhase.IDLE)

        val replaced = navigator.openOverlay(AppDestination.Codex, AppGameplayPhase.IDLE)

        assertEquals(listOf(AppDestination.Home, AppDestination.Codex), replaced.backStack.entries)
        assertEquals(listOf(AppDestination.Home), navigator.back().backStack.entries)
    }

    @Test
    fun choicesRejectAllOverlaysAndResultsOnlyAllowRebirth() {
        val navigator = AppNavigator().apply { showGameplay() }

        listOf(
            AppDestination.Settings,
            AppDestination.Lab,
            AppDestination.Armory,
            AppDestination.Rebirth,
            AppDestination.Codex,
        ).forEach { destination ->
            val choice = navigator.openOverlay(destination, AppGameplayPhase.CHOICE)
            assertEquals(AppDestination.Gameplay, choice.backStack.active)
        }

        val settings = navigator.openOverlay(AppDestination.Settings, AppGameplayPhase.VICTORY)
        assertEquals(AppDestination.Gameplay, settings.backStack.active)
        val rebirth = navigator.openOverlay(AppDestination.Rebirth, AppGameplayPhase.VICTORY)
        assertEquals(AppDestination.Rebirth, rebirth.backStack.active)
        assertFalse(rebirth.pauseGameplay)
    }

    @Test
    fun routeMatrixIsExhaustiveForEveryGameplayPhaseAndOverlay() {
        val overlays = listOf(
            AppDestination.Settings,
            AppDestination.Lab,
            AppDestination.Armory,
            AppDestination.Rebirth,
            AppDestination.Codex,
        )

        AppGameplayPhase.entries.forEach { phase ->
            overlays.forEach { destination ->
                val navigator = AppNavigator().apply { showGameplay() }

                val transition = navigator.openOverlay(destination, phase)
                val expectedOpen = when (phase) {
                    AppGameplayPhase.CHOICE -> false
                    AppGameplayPhase.GAME_OVER,
                    AppGameplayPhase.VICTORY,
                    -> destination == AppDestination.Rebirth
                    AppGameplayPhase.IDLE,
                    AppGameplayPhase.RUNNING,
                    AppGameplayPhase.PAUSED,
                    -> true
                }

                assertEquals(
                    expectedOpen,
                    transition.backStack.overlay == destination,
                    "phase=$phase destination=$destination",
                )
                assertEquals(
                    expectedOpen && phase == AppGameplayPhase.RUNNING,
                    transition.pauseGameplay,
                    "phase=$phase destination=$destination",
                )
            }
        }
    }

    @Test
    fun keysMapToEveryGlobalShortcut() {
        val mappings = listOf(
            Key.S to AppShortcut.SETTINGS,
            Key.L to AppShortcut.LAB,
            Key.A to AppShortcut.ARMORY,
            Key.B to AppShortcut.REBIRTH,
            Key.C to AppShortcut.CODEX,
            Key.M to AppShortcut.MUTE,
            Key.Escape to AppShortcut.BACK,
            Key.Enter to AppShortcut.ENTER,
        )

        mappings.forEach { (key, expected) ->
            assertEquals(expected, key.toAppShortcut())
        }
        assertNull(Key.Z.toAppShortcut())
    }
}
