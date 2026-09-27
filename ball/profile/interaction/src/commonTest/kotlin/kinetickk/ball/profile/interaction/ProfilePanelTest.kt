// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfilePanelTest {
    @Test
    fun framesUseTheModeReferenceBoards() {
        val board = profileFrame(1440f, 810f, 1f)
        assertEquals(ProfileLayoutMode.REGULAR, board.mode)
        assertEquals(1f, board.k)
        assertEquals(56f, board.left)
        assertEquals(84f, board.headerHeight)

        val desktop = profileFrame(1000f, 700f, 1f)
        assertEquals(ProfileLayoutMode.REGULAR, desktop.mode)
        assertEquals(1000f / 1440f, desktop.k, 0.0001f)

        // Density keeps the mode logical: a 2x phone is still a phone.
        assertEquals(ProfileLayoutMode.COMPACT_LANDSCAPE, profileFrame(1688f, 780f, 2f).mode)
        assertEquals(ProfileLayoutMode.COMPACT_PORTRAIT, profileFrame(390f, 844f, 1f).mode)
        assertEquals(ProfileLayoutMode.COMPACT_LANDSCAPE, profileFrame(720f, 360f, 1f).mode)
        assertEquals(ProfileLayoutMode.COMPACT_PORTRAIT, profileFrame(800f, 900f, 1f).mode)

        // Wide windows center the scaled board composition.
        val wide = profileFrame(2400f, 810f, 1f)
        assertEquals(480f, wide.originX, 0.01f)
        assertEquals(480f + 56f, wide.left, 0.01f)
    }

    @Test
    fun headerBackSlotSitsInTheHeader() {
        for ((width, height) in listOf(1440f to 810f, 844f to 390f, 390f to 844f)) {
            val frame = profileFrame(width, height, 1f)
            val back = profileHeaderBackRect(frame, 76f)
            assertEquals(frame.left, back.left)
            assertEquals(76f, back.width)
            assertTrue(back.top >= 0f && back.bottom <= frame.headerHeight, "$width x $height")
        }
    }

    @Test
    fun matterUsesDigitGroupingThenTheCompactForm() {
        assertEquals("0", formatMatter(0L, AppLanguage.English))
        assertEquals("999", formatMatter(999L, AppLanguage.English))
        assertEquals("1,284", formatMatter(1_284L, AppLanguage.English))
        assertEquals("12 050", formatMatter(12_050L, AppLanguage.Russian))
        assertEquals("0", formatMatter(-5L, AppLanguage.English))
        assertTrue(formatMatter(1_500_000L, AppLanguage.English).length <= 6)
    }
}
