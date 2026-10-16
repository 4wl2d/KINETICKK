// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.design.Kk
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.BuildProjection
import kinetickk.ball.gameplay.nucleus.render.SeveredRelicProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
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
    fun pauseBuildOverviewShowsTheRelicsCostsResonanceReactionsItemsStatsAndTotals() {
        val relics = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1))
        val met = setOf(RelicPairingId.ENDLESS_GYRE)
        val build = BuildProjection(
            tagPoints = immutableListOf(4, 2, 0, 0, 0, 0, 0, 0, 0),
            tagTiers = immutableListOf(1, 0, 0, 0, 0, 0, 0, 0, 0),
            affinity = immutableListOf(3, 2, 0, 0, 0),
            affinityTiers = immutableListOf(1, 1, 0, 0, 0),
            dominant = StatusElement.THERMAL,
            relicUpsides = RelicId.entries.map {
                when (it) {
                    RelicId.PERPETUAL_VECTOR -> 1.1f
                    RelicId.GYRE_ENGINE -> 0.85f
                    else -> 1f
                }
            }.toImmutableList(),
            severed = immutableListOf(SeveredRelicProjection(RelicId.EVENT_HORIZON, 30f)),
            suppressedItemIds = immutableListOf(2),
            rebornItemIds = immutableListOf(3),
            reachableReactions = immutableListOf(ReactionId.THERMAL_SHOCK),
        )
        val stacks = listOf(1, 0, 1, 2, 0)
        fun overview(language: AppLanguage, pairings: Set<RelicPairingId> = met) = rewardFixtureModel(phase = GamePhase.PAUSED, relics = relics,
            discoveredPairings = pairings, build = build, itemStacks = stacks, language = language).pauseBuildOverview(language)
        val english = overview(AppLanguage.English)
        // The run's own weapon and its level (SPEC 7.4: weapon + level).
        assertEquals("Flux Wake", english.weaponName)
        assertEquals("Lvl 7", english.weaponLevelText)
        assertEquals(WeaponMastery.RESONANT, english.masteryTier)
        assertEquals("Relics 2/4", english.relicsLabel)
        assertEquals(4, english.relics.size)
        assertEquals(listOf("×1.1", "×0.85"), english.relics.filterNotNull().map { it.multiplier })
        assertEquals(listOf(PauseValueTone.YOU, PauseValueTone.THREAT), english.relics.filterNotNull().map { it.tone })
        assertEquals(listOf("Endless Gyre"), english.synergyNames, "a met named pairing gets its named bracket")
        assertEquals(listOf("?"), overview(AppLanguage.English, emptySet()).synergyNames, "an unmet pairing keeps its name hidden")
        // One cost line per liability, always listed; a replaced relic's cost lingers at the end.
        assertEquals(listOf(RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE, RelicId.EVENT_HORIZON), english.costs.map { it.definition.id })
        assertEquals(listOf(false, false, true), english.costs.map { it.lingering })
        assertTrue(english.costs.first().text.startsWith("Brake grips half as hard"))
        assertEquals(listOf(BuildTag.RAM, BuildTag.DRIVE), english.tags.map { it.tag }, "tags with points, most first")
        assertEquals(listOf("Ram", "Drive"), english.tags.map { it.label })
        assertEquals(listOf(3, 5, 7), english.tagThresholds)
        assertEquals(5, english.elements.size)
        assertEquals(listOf(true, false, false, false, false), english.elements.map { it.dominant })
        assertEquals(listOf(2, 4, 6), english.affinityThresholds)
        assertEquals(listOf("?"), english.reactions.map { it.name }, "an unmet reaction reads as a question mark")
        assertEquals(listOf(0, 2, 3), english.items.map { it.item.id })
        assertEquals(listOf(false, true, false), english.items.map { it.suppressed })
        assertEquals(listOf(false, false, true), english.items.map { it.reborn })
        assertEquals(listOf(1, 1, 2), english.items.map { it.stacks })
        assertEquals("Rebirth 3", english.rebirth)
        assertEquals(listOf("Impact power", "Weapon power", "Critical chance", "Shield"), english.stats.map { it.label })
        assertEquals("×1.64", english.stats.first().value)
        assertTrue(english.stats.first().highlight)
        assertEquals("8%", english.stats.single { it.label == "Critical chance" }.value)
        assertEquals(listOf("07:42", "612", "2", "+486", "×31", "184.3K"), english.run.map { it.value })
        val russian = overview(AppLanguage.Russian)
        assertEquals("Ур. 7", russian.weaponLevelText)
        assertEquals("×1,64", russian.stats.first().value)
        assertEquals("×1,1", russian.relics.first()?.multiplier)
        val shown = listOf(english, russian).flatMap { view ->
            listOf(view.heading, view.weaponName, view.weaponLevelText, view.form, view.rebirth, view.relicsLabel, view.costLabel,
                view.resonanceLabel, view.elementsLabel,
                view.reactionsLabel, view.itemsLabel, view.noItems, view.noReactions) +
                view.stats.flatMap { listOf(it.label, it.value) } + view.run.flatMap { listOf(it.label, it.value) } +
                view.synergyNames + view.costs.map { it.text } + view.tags.map { it.label } + view.elements.map { it.label } +
                view.reactions.map { it.name } + view.relics.filterNotNull().map { it.multiplier }
        }
        assertTrue(shown.none { text -> text.any { it in "·→←↑↓↵›‹▶◀◇§" } }, shown.toString())
        val latin = Regex("[A-Za-z]")
        val russianShown = listOf(russian.heading, russian.weaponName, russian.weaponLevelText, russian.costLabel, russian.resonanceLabel, russian.elementsLabel, russian.reactionsLabel,
            russian.itemsLabel, russian.noItems, russian.noReactions) + russian.stats.map { it.label } + russian.tags.map { it.label } +
            russian.elements.map { it.label }
        assertTrue(russianShown.none { latin.containsMatchIn(it) }, russianShown.toString())
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

    @Test
    fun thePausePanelReadsAgainstTheDimmedWorldInEveryLayout() {
        for (mode in GameplayLayoutMode.entries) {
            val panel = pausePanelColor(mode)
            assertEquals(1f, panel.alpha, "$mode panel is opaque")
            // The scrim over the darkest frame (ink) is what surrounds the panel.
            val scrim = pauseOverlayScrimColor(mode)
            val around = kinetickk.foundation.design.kkMix(Kk.Ink, scrim.copy(alpha = 1f), scrim.alpha)
            val lift = listOf(panel.red - around.red, panel.green - around.green, panel.blue - around.blue).minOrNull()!! * 255f
            assertTrue(lift >= if (mode == GameplayLayoutMode.REGULAR) 8f else 4f, "$mode panel is lighter than the scrim by $lift levels")
        }
    }
}
