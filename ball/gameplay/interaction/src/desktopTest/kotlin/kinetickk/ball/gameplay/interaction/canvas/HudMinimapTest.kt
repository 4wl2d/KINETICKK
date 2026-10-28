// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.interaction.fx.BuildNoticeKind
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.localeList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The minimap (`HudBlock.MINIMAP`): its own corner of the HUD in every layout, inside the world's
 * keep-out, gone when the setting is off, north-up around the Core at the published range.
 */
class HudMinimapTest {
    private val viewports = listOf(1_440f to 810f, 1_280f to 800f, 844f to 390f, 390f to 844f, 412f to 915f, 360f to 640f)
    private val others = listOf(
        HudBlock.CLOCK, HudBlock.MATTER_CHIP, HudBlock.KEY_CHIP, HudBlock.CHAIN, HudBlock.BOSS, HudBlock.BADGE,
        HudBlock.INTEGRITY, HudBlock.SPEED, HudBlock.LOADOUT, HudBlock.TRIAL_PANEL, HudBlock.POLARITY_LABEL,
        HudBlock.DASH_LABEL, HudBlock.BRAKE_LABEL, HudBlock.RELIC_ROW, HudBlock.FEED, HudBlock.REBIRTH,
    )
    private val feed = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(
        BuildNotificationProjection("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04", "+ Ram II"), 4f),
        BuildNotificationProjection("REACTION DISCOVERED", immutableListOf("+ Thermal Shock"), 5f, BuildNoticeKind.DISCOVERY),
    ))

    @Test
    fun theMinimapOverlapsNoOtherHudBlockInAnyLayout() {
        val orbit = SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f, true, 12f, -1, 0.4f,
            immutableListOf(), 0f, 0f)
        for ((dpWidth, dpHeight) in viewports) for (density in listOf(1f, 2.625f)) for (textScale in listOf(1f, 1.25f, 1.75f)) {
            // A slow Core, a high-tier three-digit speed and a top-tier four-digit one (the widest, most stretched number).
            for (boss in listOf(null, EnemyType.ELITE, EnemyType.ARCHITECT)) for (trial in listOf(false, true)) for (speed in listOf(0f, 980f, 2_400f)) {
                val width = dpWidth * density
                val height = dpHeight * density
                val model = hudTestModel(dpWidth, dpHeight).with(
                    "screenWidth" to width, "screenHeight" to height, "uiScale" to density,
                    "keys" to 12, "runMatter" to 9_876_543L, "combo" to 128, "comboTime" to 1f, "comboWindow" to 2.8f, *levelOverrides(),
                    // Tier 10: the Rebirth strip with every mutator sigil.
                    "rebirthLevel" to 10,
                    "enemies" to (if (boss == null) emptyList() else listOf(EnemyProjection(4, boss, 0f, 0f, 0f, 0f, 500f, 1_000f, 60f,
                        0f, 0f, 0f, 0f, 0f, 0f, false, affinityBits = bossAffinity(boss)))).toImmutableList(),
                    "sites" to (if (trial) listOf(orbit) else emptyList()).toImmutableList(),
                    "velocityX" to speed, "velocityY" to 0f,
                )
                drawHud(model, width, height, density, textScale)
                val where = "${dpWidth}x$dpHeight @$density x$textScale $boss trial=$trial speed=$speed"
                val minimap = assertNotNull(HudLayoutProbe.rect(HudBlock.MINIMAP), "$where: no minimap")
                assertTrue(minimap.left >= 0f && minimap.right <= width && minimap.top >= 0f && minimap.bottom <= height, "$where: $minimap")
                for (block in others) {
                    val rect = HudLayoutProbe.rect(block) ?: continue
                    assertFalse(rect.overlaps(minimap), "$where: the minimap $minimap overlaps $block $rect")
                }
                forEachRunningControlBounds(width, height, density, RunningControlMask.ALL) { target, left, top, right, bottom ->
                    assertFalse(Rect(left, top, right, bottom).overlaps(minimap), "$where: the minimap $minimap overlaps $target")
                }
                val keepOut = WorldHudKeepOut().update(width, height, density, textScale, trial, boss != null)
                assertTrue(keepOut.rects().any { it.contains(minimap.topLeft) && it.contains(minimap.bottomRight) },
                    "$where: the keep-out misses the minimap")
            }
        }
    }

    @Test
    fun turningTheMinimapOffHidesItAndFreesItsKeepOut() {
        val hidden = hudTestModel().with("settings" to PlayerPreferences(language = AppLanguage.English, minimapEnabled = false))
        drawHud(hidden, 1_440f, 810f, 1f, 1.25f)
        assertNull(HudLayoutProbe.rect(HudBlock.MINIMAP))
        val shown = WorldHudKeepOut().update(1_440f, 810f, 1f, 1.25f, false, false, minimapShown = true)
        val shownRects = shown.rects()
        val freed = WorldHudKeepOut().update(1_440f, 810f, 1f, 1.25f, false, false, minimapShown = false).rects()
        assertEquals(shownRects.size - 1, freed.size)
        // The keep-out a frame uses follows the setting.
        val engineKeepOut = WorldHudKeepOut()
        assertEquals(shownRects.size, engineKeepOut.update(1_440f, 810f, 1f, 1.25f, false, false, true).count)
        assertEquals(freed.size, engineKeepOut.update(1_440f, 810f, 1f, 1.25f, false, false, false).count)
    }

    @Test
    fun marksSitNorthUpAroundTheCoreAtThePublishedRange() {
        val roles = KkRolePalette.Default
        val base = hudTestModel().with("velocityX" to 0f, "velocityY" to 0f)
        val range = base.minimapRange
        // Unstable rocks (heat) half the range to the east and half the range to the south.
        val model = base.with(
            "obstacles" to immutableListOf(
                ObstacleProjection(1L, ObstacleKind.UNSTABLE, base.coreX + range * 0.5f, base.coreY, 260f, 3),
                ObstacleProjection(2L, ObstacleKind.UNSTABLE, base.coreX, base.coreY + range * 0.5f, 260f, 5),
            ),
        )
        val pixels = render(model, 1_440, 810)
        val minimap = assertNotNull(HudLayoutProbe.rect(HudBlock.MINIMAP))
        val half = minimap.width * 0.5f
        val center = minimap.center
        assertTrue(tinted(pixels.at(center.x + half * 0.5f, center.y), roles.heat), "east of the Core is east on the map")
        assertTrue(tinted(pixels.at(center.x, center.y + half * 0.5f), roles.heat), "south of the Core is down on the map")
        assertFalse(tinted(pixels.at(center.x - half * 0.5f, center.y), roles.heat), "nothing mirrored to the west")
        assertFalse(tinted(pixels.at(center.x, center.y - half * 0.5f), roles.heat), "nothing mirrored to the north")
        // A quarter of the range: the same rocks fall off the map.
        val shorter = render(model.with("minimapRange" to range * 0.25f), 1_440, 810)
        assertFalse(tinted(shorter.at(center.x + half * 0.5f, center.y), roles.heat), "a rock past the range is not drawn")
    }

    @Test
    fun gravityKnotsMarkTheMinimapInGraviticWithTheirField() {
        val gravitic = KkRolePalette.Default.element(3)
        val base = hudTestModel().with("velocityX" to 0f, "velocityY" to 0f)
        // A short range, so the knot's dot is several pixels wide and its field a ring well clear of it.
        val range = base.minimapRange * 0.25f
        val kinds = base.content.obstacles.kinds
        val knot = ObstacleProjection(1L, ObstacleKind.GRAVITY_WELL, base.coreX + range * 0.5f, base.coreY, kinds.wellCoreRadius, 3)
        val empty = render(base.with("minimapRange" to range, "obstacles" to immutableListOf<ObstacleProjection>()), 1_440, 810)
        val pixels = render(base.with("minimapRange" to range, "obstacles" to immutableListOf(knot)), 1_440, 810)
        val minimap = assertNotNull(HudLayoutProbe.rect(HudBlock.MINIMAP))
        val half = minimap.width * 0.5f
        val scale = half / range
        val dotX = minimap.center.x + half * 0.5f
        val dotY = minimap.center.y
        assertTrue(tinted(pixels.at(dotX, dotY), gravitic), "a knot is a Gravitic dot")
        val field = kinds.wellInfluence * scale
        assertTrue(field < half * 0.5f, "the field ring stays on the map: $field")
        assertTrue(pixels.at(dotX, dotY - field) != empty.at(dotX, dotY - field), "its field's edge is a ring around it")
        assertEquals(empty.at(dotX, dotY - field * 0.6f), pixels.at(dotX, dotY - field * 0.6f), "open between the dot and the ring")
    }

    private fun drawHud(model: GameplayRenderModel, width: Float, height: Float, density: Float, textScale: Float) {
        val measurer = measurer(density, textScale)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(width, height)) {
            drawHud(model, measurer, 1f)
            // The feed records its block after the HUD; it docks clear of the minimap too.
            drawHudFeed(model, feed, measurer, 1f, null)
        }
    }

    private fun render(model: GameplayRenderModel, width: Int, height: Int): androidx.compose.ui.graphics.PixelMap {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            drawHud(model, measurer(1f, 1.25f), 1f)
        }
        return bitmap.toPixelMap()
    }

    private fun androidx.compose.ui.graphics.PixelMap.at(x: Float, y: Float) = this[x.toInt(), y.toInt()]

    /** Whether [actual] lies more than half way from the ink background toward [expected]. */
    private fun tinted(actual: androidx.compose.ui.graphics.Color, expected: androidx.compose.ui.graphics.Color): Boolean {
        val base = Kk.Ink
        val ex = expected.red - base.red
        val ey = expected.green - base.green
        val ez = expected.blue - base.blue
        val along = ((actual.red - base.red) * ex + (actual.green - base.green) * ey + (actual.blue - base.blue) * ez) /
            (ex * ex + ey * ey + ez * ez)
        return along > 0.5f
    }

    private fun measurer(density: Float, textScale: Float) = CanvasTextMeasurer(
        TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr), textScale, AppLanguage.English,
        HudTestFonts.typography.copy(localeList = AppLanguage.English.localeList()),
    )
}
