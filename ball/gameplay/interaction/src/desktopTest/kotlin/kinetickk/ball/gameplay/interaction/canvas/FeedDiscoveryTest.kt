// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.fx.BuildNoticeKind
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Discovery, Break and tier toasts as the feed draws them: the nucleus's English titles read in the
 * player's language, a resonance tier line loses its numeral to lit pips (its plate keeps room for
 * them), Russian toasts carry no Latin letters, and toasts of higher rank sit above lower ones.
 */
class FeedDiscoveryTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)

    @Test
    fun discoveryTitlesAndTierLinesReadInThePlayersLanguage() {
        val expected = mapOf(
            AppLanguage.English to listOf("REACTION DISCOVERED", "+ THERMAL SHOCK"),
            AppLanguage.Russian to listOf("РЕАКЦИЯ ОТКРЫТА", "+ ТЕРМОШОК"),
        )
        for ((language, lines) in expected) sizes.forEach { (w, h) ->
            val drawn = draw(w, h, language, BuildNotificationProjection("REACTION DISCOVERED", immutableListOf("+ Thermal Shock"), 5f,
                BuildNoticeKind.DISCOVERY))
            assertEquals(lines, drawn, "$language $w x $h")
        }
        val pairing = draw(1_440, 810, AppLanguage.Russian, BuildNotificationProjection("PAIRING DISCOVERED", immutableListOf("+ Black Star"), 5f,
            BuildNoticeKind.DISCOVERY))
        assertEquals("СОЧЕТАНИЕ ОТКРЫТО", pairing.first())
        val breaks = draw(1_440, 810, AppLanguage.Russian, BuildNotificationProjection("BREAK", immutableListOf("+ Ignite", "+ Freeze"), 3f,
            BuildNoticeKind.BREAK))
        assertEquals(listOf("ПРОБОЙ", "+ ВОСПЛАМЕНЕНИЕ", "+ ЗАМОРОЗКА"), breaks)
    }

    @Test
    fun tierLinesShowLitPipsInsteadOfNumerals() {
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            val toast = BuildNotificationProjection("Cinder Ram", immutableListOf("+ Ram II", "+ Thermal III"), 5f)
            val drawn = draw(w, h, language, toast)
            val context = "$language $w x $h $drawn"
            assertEquals(3, drawn.size, context)
            drawn.drop(1).forEach { line ->
                assertFalse(line.endsWith(" I") || line.endsWith(" II") || line.endsWith(" III") || line.any { it.isDigit() },
                    "$context: the tier reads as pips, not a numeral")
            }
            if (language == AppLanguage.Russian) {
                assertEquals(listOf("+ ТАРАН", "+ ТЕРМО"), drawn.drop(1), context)
                assertTrue(drawn.drop(1).none { line -> line.any { it in 'A'..'Z' || it in 'a'..'z' } }, context)
            }
            // The plate keeps room for the three pips after the widest tier line.
            val plate = FeedProbe.plateBox((0 until FeedProbe.plateCount).first { !FeedProbe.isBanner(it) })
            val widest = (1 until FeedProbe.lineCount).maxOf { line ->
                val layout = FeedProbe.lineLayout(line)
                FeedProbe.lineBox(line).left + (0 until layout.lineCount).maxOf { layout.getLineRight(it) }
            }
            assertTrue(plate.right - widest >= 24f, "$context: pips fit between the text ($widest) and the plate edge (${plate.right})")
        }
    }

    @Test
    fun higherRankedToastsSitAboveLowerOnes() {
        // The reducer keeps toasts by rank, lowest first: an acquisition, a first Break, a discovery.
        val notices = immutableListOf(
            BuildNotificationProjection("Cinder Ram", immutableListOf("Impact damage +0.05"), 5f),
            BuildNotificationProjection("BREAK", immutableListOf("+ Overload"), 3f, BuildNoticeKind.BREAK),
            BuildNotificationProjection("REACTION DISCOVERED", immutableListOf("+ Fusion"), 5f, BuildNoticeKind.DISCOVERY),
        )
        val model = hudTestModel(1_440f, 810f).with("messageTime" to 0f)
        drawFeed(1_440, 810, AppLanguage.English, 1.25f) { measurer ->
            drawHudFeed(model, VisualFxProjection.EMPTY.copy(buildNotifications = notices), measurer, 1f, null)
        }
        val titles = (0 until FeedProbe.lineCount).filter { line -> (0 until line).none { FeedProbe.linePlate(it) == FeedProbe.linePlate(line) } }
            .map { FeedProbe.lineLayout(it).layoutInput.text.text }
        assertEquals(listOf("REACTION DISCOVERED", "BREAK", "CINDER RAM"), titles, "the discovery leads, the acquisition trails")
    }

    /** Draws one toast and returns the texts of its plate, title first (no-break spaces as spaces). */
    private fun draw(width: Int, height: Int, language: AppLanguage, toast: BuildNotificationProjection): List<String> {
        val model = hudTestModel(width.toFloat(), height.toFloat()).with("messageTime" to 0f)
        drawFeed(width, height, language, 1.25f) { measurer ->
            drawHudFeed(model, VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(toast)), measurer, 1f, null)
        }
        val plate = (0 until FeedProbe.plateCount).first { !FeedProbe.isBanner(it) }
        assertFeedLinesFit("$language $width x $height ${toast.title}", width, height)
        return (0 until FeedProbe.lineCount).filter { FeedProbe.linePlate(it) == plate }
            .map { FeedProbe.lineLayout(it).layoutInput.text.text.replace(' ', ' ') }
    }
}
