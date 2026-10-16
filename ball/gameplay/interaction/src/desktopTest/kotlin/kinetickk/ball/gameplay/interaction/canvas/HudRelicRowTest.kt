// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.interaction.fx.BuildNoticeKind
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureRelics
import kinetickk.ball.gameplay.nucleus.render.BuildProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SeveredRelicProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the HUD's relic row draws for relic links (link bars between neighbours, stacked brackets
 * otherwise, nothing for a shared key's harmony) and where the ghosts of replaced relics go.
 */
class HudRelicRowTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)

    @Test
    fun interleavedLinksDrawSeparateBracketsThatNeverMeet() {
        sizes.forEach { (w, h) ->
            // Contradiction (0, 2) interleaves Thermal Paradox (1, 3); in the second row Lightning Rod (0, 3)
            // spans Endless Gyre (0, 2) and a dissonance (1, 3).
            listOf(
                listOf(RelicId.PERPETUAL_VECTOR, RelicId.FURNACE_COVENANT, RelicId.STASIS_ANCHOR, RelicId.ABSOLUTE_ZERO) to 2,
                listOf(RelicId.PERPETUAL_VECTOR, RelicId.NEUTRON_HEART, RelicId.GYRE_ENGINE, RelicId.STORMHEART_CAPACITOR) to 3,
            ).forEach { (relics, expected) ->
                val marks = drawRelics(w, h, relics)
                val brackets = marks.filter { it.bracket }
                val where = "$w x $h $relics"
                assertEquals(expected, brackets.size, "$where: $brackets")
                brackets.forEachIndexed { index, a ->
                    brackets.drop(index + 1).forEach { b ->
                        if (a.from <= b.to && b.from <= a.to) {
                            assertFalse(a.rect.overlaps(b.rect), "$where: brackets ${a.from}-${a.to} ${a.rect} and ${b.from}-${b.to} ${b.rect} meet")
                        }
                    }
                }
                // Brackets stay above the diamonds' row and on screen.
                brackets.forEach { assertTrue(it.rect.top >= 0f && it.rect.right <= w, "$where: ${it.rect}") }
            }
        }
    }

    @Test
    fun linkedRelicsTwoSlotsApartGetABracketAndNoLinkBar() {
        sizes.forEach { (w, h) ->
            // Endless Gyre in slots 0 and 2 with an unrelated relic between them.
            val marks = drawRelics(w, h, listOf(RelicId.PERPETUAL_VECTOR, RelicId.ECHO_CHAMBER, RelicId.GYRE_ENGINE))
            assertEquals(listOf(Triple(true, 0, 2)), marks.map { Triple(it.bracket, it.from, it.to) }, "$w x $h")
            // Linked neighbours are joined by a link bar instead.
            val neighbours = drawRelics(w, h, listOf(RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE))
            assertEquals(listOf(Triple(false, 0, 1)), neighbours.map { Triple(it.bracket, it.from, it.to) }, "$w x $h")
            // A shared key's harmony gets neither: four Motion relics would stack six brackets.
            assertEquals(emptyList(), drawRelics(w, h, listOf(RelicId.GYRE_ENGINE, RelicId.ECHO_CHAMBER, RelicId.STORMHEART_CAPACITOR)), "$w x $h")
            assertEquals(emptyList(), drawRelics(w, h, listOf(RelicId.GYRE_ENGINE, RelicId.STORMHEART_CAPACITOR)), "$w x $h")
            // The bracket spans the two diamonds' centers, above them.
            val bracket = marks.single().rect
            val bar = neighbours.single().rect
            assertTrue(bracket.bottom <= bar.top + 1f, "$w x $h: bracket $bracket is not above the row $bar")
        }
    }

    /**
     * The ghosts of replaced relics whose cost lingers stay clear of the row's gauges (Stormheart's
     * charge ring in the first slot), of the feed's plates and of the controls, on screen, at every
     * text size: left of the row on desktop and portrait, under it on landscape phones.
     */
    @Test
    fun severedRelicGhostsStayClearOfTheGaugesTheFeedAndTheControls() {
        val severed = immutableListOf(SeveredRelicProjection(RelicId.HOLLOW_CROWN, 20f), SeveredRelicProjection(RelicId.EVENT_HORIZON, 8f))
        val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(
            BuildNotificationProjection("REACTION DISCOVERED", immutableListOf("+ Thermal Shock"), 5f, BuildNoticeKind.DISCOVERY),
        ))
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            for (textScale in listOf(1f, 1.25f, 1.75f)) {
                val relics = listOf(RelicId.STORMHEART_CAPACITOR, RelicId.STASIS_ANCHOR, RelicId.GLASS_COMET, RelicId.OUROBOROS_SEAL)
                val model = relicModel(w, h, relics).with(
                    "build" to BuildProjection(severed = severed, stormCharge = 0.6f, stasisMeter = 0.5f, invulnerable = true),
                    "message" to "DRIFT PHASE", "messageTime" to 1.5f,
                )
                val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), textScale,
                    language, HudTestFonts.typography)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(w, h)), Size(w.toFloat(), h.toFloat())) {
                    drawHud(model, measurer, 1f)
                    drawHudFeed(model, fx, measurer, 1f, null)
                }
                val where = "$language $w x $h x$textScale"
                val ghosts = assertNotNull(HudLayoutProbe.rect(HudBlock.SEVERED), where)
                val row = assertNotNull(HudLayoutProbe.rect(HudBlock.RELIC_ROW), where)
                assertFalse(ghosts.overlaps(row), "$where: ghosts $ghosts meet the row's gauges $row")
                HudLayoutProbe.rect(HudBlock.FEED)?.let { feed -> assertFalse(ghosts.overlaps(feed), "$where: ghosts $ghosts meet the feed $feed") }
                runningControlBounds(w.toFloat(), h.toFloat(), 1f).forEach { control ->
                    assertFalse(ghosts.overlaps(control.bounds), "$where: ghosts $ghosts meet ${control.target} ${control.bounds}")
                }
                assertTrue(ghosts.left >= 0f && ghosts.top >= 0f && ghosts.right <= w && ghosts.bottom <= h, "$where: $ghosts off screen")
                // The loadout's block holds its ghosts, so the HUD's layout checks see them.
                val loadout = assertNotNull(HudLayoutProbe.rect(HudBlock.LOADOUT), where)
                assertTrue(loadout.left <= ghosts.left && loadout.bottom >= ghosts.bottom, "$where: loadout $loadout leaves out $ghosts")
            }
        }
    }

    private fun relicModel(width: Int, height: Int, relics: List<RelicId>): GameplayRenderModel {
        val base = hudTestModel(width.toFloat(), height.toFloat())
        // The shipped relic keys decide every link.
        val content = base.content.copy(relics = rewardFixtureRelics.toImmutableList())
        return base.with(
            "content" to content,
            "equippedRelics" to relics.map { EquippedRelic(it, 1) }.toImmutableList(),
        )
    }

    private fun drawRelics(width: Int, height: Int, relics: List<RelicId>): List<HudLinkMark> {
        val model = relicModel(width, height, relics)
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English, HudTestFonts.typography)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(width, height)), Size(width.toFloat(), height.toFloat())) {
            drawHud(model, measurer, 1f)
        }
        return HudLayoutProbe.links()
    }
}
