// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameplayPresentationPolicyTest {
    @Test
    fun runningHudAndPerformanceAreOnlyVisibleDuringActivePlay() {
        assertTrue(shouldDrawRunningPresentation(GamePhase.RUNNING))
        assertFalse(shouldDrawRunningPresentation(GamePhase.PAUSED))
        assertFalse(shouldDrawRunningPresentation(GamePhase.CHOICE))
        assertFalse(shouldDrawRunningPresentation(GamePhase.GAME_OVER))
        assertFalse(shouldDrawRunningPresentation(GamePhase.VICTORY))
    }

    @Test
    fun statusOverlaysSeparateMenusFromPlayAcrossViewportClasses() {
        for (mode in GameplayLayoutMode.entries) {
            assertTrue(pauseOverlayScrimColor(mode).alpha >= 0.9f)
            assertTrue(terminalOverlayScrimColor(mode).alpha >= pauseOverlayScrimColor(mode).alpha)
        }
        assertEquals(pauseOverlayScrimColor(GameplayLayoutMode.COMPACT_LANDSCAPE),
            pauseOverlayScrimColor(GameplayLayoutMode.COMPACT_PORTRAIT))
        assertEquals(terminalOverlayScrimColor(GameplayLayoutMode.COMPACT_LANDSCAPE),
            terminalOverlayScrimColor(GameplayLayoutMode.COMPACT_PORTRAIT))
    }
}
