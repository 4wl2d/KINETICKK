// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.design.Kk
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureModel
import kinetickk.foundation.common.localization.AppLanguage
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
            // Pause and rewards dim the frozen world with an ink scrim (SPEC: ink 80–85 %, no blur);
            // the run report covers it completely.
            for (scrim in listOf(pauseOverlayScrimColor(mode), choiceOverlayScrimColor(mode))) {
                assertTrue(scrim.alpha >= 0.8f, "$mode scrim hides the world")
                assertEquals(Kk.Ink.copy(alpha = scrim.alpha), scrim, "$mode scrim is ink")
            }
            assertEquals(1f, terminalOverlayScrimColor(mode).alpha)
            assertTrue(terminalOverlayScrimColor(mode).alpha >= pauseOverlayScrimColor(mode).alpha)
        }
        assertEquals(pauseOverlayScrimColor(GameplayLayoutMode.COMPACT_LANDSCAPE),
            pauseOverlayScrimColor(GameplayLayoutMode.COMPACT_PORTRAIT))
        assertEquals(terminalOverlayScrimColor(GameplayLayoutMode.COMPACT_LANDSCAPE),
            terminalOverlayScrimColor(GameplayLayoutMode.COMPACT_PORTRAIT))
    }

    @Test
    fun pauseBuildOverviewShowsTheRunsOwnWeaponRelicsStatsAndTotals() {
        val relics = listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, 2), EquippedRelic(RelicId.GHOST_VECTOR, 1))
        val english = rewardFixtureModel(phase = GamePhase.PAUSED, relics = relics).pauseBuildOverview(AppLanguage.English)
        assertEquals("Flux Wake", english.weaponName)
        assertEquals("Lvl 7", english.weaponLevelText)
        assertEquals(WeaponMastery.RESONANT, english.masteryTier)
        assertEquals("Relics 2/4", english.relicsLabel)
        assertEquals(listOf("Vector maneuver"), english.synergyNames)
        assertEquals(4, english.relics.size)
        assertEquals("Rebirth 3", english.rebirth)
        assertEquals(10, english.stats.size)
        assertEquals("×1.64", english.stats.first().value)
        assertTrue(english.stats.first().highlight)
        assertEquals("8%", english.stats.single { it.label == "Critical chance" }.value)
        assertEquals(listOf("07:42", "612", "2", "+486", "×31", "184.3K"), english.run.map { it.value })
        val russian = rewardFixtureModel(phase = GamePhase.PAUSED, relics = relics, language = AppLanguage.Russian)
            .pauseBuildOverview(AppLanguage.Russian)
        assertEquals("Ур. 7", russian.weaponLevelText)
        assertEquals("×1,64", russian.stats.first().value)
        val shown = listOf(english, russian).flatMap { overview ->
            listOf(overview.heading, overview.form, overview.rebirth, overview.weaponName, overview.mastery, overview.relicsLabel) +
                overview.stats.flatMap { listOf(it.label, it.value) } + overview.run.flatMap { listOf(it.label, it.value) } +
                overview.synergyNames + overview.synergyDescriptions
        }
        assertTrue(shown.none { text -> text.any { it in "·→←↑↓↵›‹▶◀◇§" } }, shown.toString())
    }

    @Test
    fun overlayNumbersGroupAndCompactPerLanguage() {
        assertEquals("1,412", overlayGrouped(1_412L, AppLanguage.English))
        assertEquals("1\u202F412", overlayGrouped(1_412L, AppLanguage.Russian))
        assertEquals("999", overlayGrouped(999L, AppLanguage.English))
        assertEquals("31,260", overlayCompact(31_260L, AppLanguage.English))
        assertEquals("612K", overlayCompact(612_000L, AppLanguage.English))
        assertEquals("×1.2", overlayMultiplier(1.2f, AppLanguage.English))
        assertEquals("×1", overlayMultiplier(1f, AppLanguage.English))
        assertEquals("23,2", overlayDecimal(23.2f, AppLanguage.Russian))
        assertEquals("8%", overlayPercent(0.08f, AppLanguage.English))
    }
}
