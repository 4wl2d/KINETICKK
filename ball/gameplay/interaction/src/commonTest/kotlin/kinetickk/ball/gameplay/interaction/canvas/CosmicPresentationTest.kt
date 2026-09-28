// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.interaction.localization.WorldRedesignText
import kinetickk.ball.gameplay.nucleus.model.DamageNumberTier
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRebirthTiers
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.positiveModulo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Pure presentation rules of the redesigned world layer. */
class CosmicPresentationTest {
    private val remapped = KkRolePalette(
        you = Color(0xFF010203), threat = Color(0xFF040506), heat = Color(0xFF070809),
        shield = Color(0xFF0A0B0C), pol = Color(0xFF0D0E0F),
    )

    @Test
    fun arenaThemeTintsOnlyGridAndHalftoneByRebirthTier() {
        assertEquals(arenaTheme(0), arenaTheme(-3), "Tiers below 0 read as the neutral arena")
        assertEquals(arenaTheme(KkRebirthTiers.MaxTier), arenaTheme(40))
        assertEquals(Kk.Bone.copy(alpha = arenaTheme(0).grid.alpha), arenaTheme(0).grid, "Tier 0 keeps the board's bone grid")
        for (tier in 1 until KkRebirthTiers.MaxTier) {
            val tint = KkRebirthTiers.color(tier)
            listOf(arenaTheme(tier).grid, arenaTheme(tier).halftone).forEach { color ->
                assertEquals(tint.copy(alpha = color.alpha), color, "Tier $tier uses its own theme color")
                assertTrue(color.alpha in 0.02f..0.12f, "Tier $tier stays a light tint")
            }
        }
        val horizon = arenaTheme(KkRebirthTiers.MaxTier)
        assertEquals(Color.White.copy(alpha = horizon.grid.alpha), horizon.grid, "Tier 10 is black and white")
        assertEquals((0..KkRebirthTiers.MaxTier).map { arenaTheme(it) }.toSet().size, KkRebirthTiers.MaxTier + 1)
    }

    @Test
    fun worldGridStaysAnchoredToTheWorld() {
        listOf(0f, 13.5f, -977f, 40_021f).forEach { camera ->
            val offset = worldGridOffset(camera, 1440f, 0f)
            assertTrue(offset >= 0f && offset < WORLD_GRID_SPACING)
            assertEquals(offset, worldGridOffset(camera + WORLD_GRID_SPACING * 3f, 1440f, 0f), 0.01f)
            assertEquals(positiveModulo(offset - 10f, WORLD_GRID_SPACING), worldGridOffset(camera + 10f, 1440f, 0f), 0.01f)
        }
    }

    @Test
    fun enemiesReadAsDistinctBoardShapesWithoutADart() {
        val basic = listOf(EnemyType.DRIFTER, EnemyType.SHOOTER, EnemyType.CHARGER, EnemyType.INTERCEPTOR, EnemyType.WEAVER)
        assertEquals(basic.size, basic.map(::enemySilhouette).toSet().size, "Ordinary enemies have distinct silhouettes")
        assertEquals(EnemySilhouette.OCTAGON, enemySilhouette(EnemyType.ELITE))
        assertTrue(enemySilhouette(EnemyType.SPLITTER) !in basic.map(::enemySilhouette))
        // Hard rule 3: the dart silhouette becomes the pentagon M12 3 21 10 17.5 21h-11L3 10Z.
        assertEquals(
            listOf(12f, 3f, 21f, 10f, 17.5f, 21f, 6.5f, 21f, 3f, 10f),
            EnemySilhouette.PENTAGON.contours.single().toList(),
        )
        assertEquals(listOf(EnemyType.SHOOTER), EnemyType.entries.filter(::enemyHasCoreDot))
        EnemySilhouette.entries.forEach { shape ->
            shape.contours.forEach { contour ->
                assertTrue(contour.size >= 6 && contour.size % 2 == 0)
                assertTrue(contour.all { it in 0f..24f }, "$shape stays on the 24 grid")
            }
        }
    }

    @Test
    fun effectColorsReadThroughTheRolePalette() {
        assertEquals(remapped.you, fxColor(0, remapped), "Dash rings are the player's")
        assertEquals(remapped.you, fxColor(3, remapped), "Ram rings are the player's")
        assertEquals(remapped.threat, fxColor(4, remapped), "Damage taken is a threat")
        assertEquals(Kk.Bone, fxColor(1, remapped), "Kill shards are bone")
        listOf(-1, 2, 99).forEach { index -> assertTrue(fxColor(index, remapped) in setOf(Kk.Bone, Kk.Bone2)) }
    }

    @Test
    fun damageNumberTiersAreBoneYouHeatThreatAndGrow() {
        assertEquals(
            listOf(Kk.Bone, remapped.you, remapped.heat, remapped.threat),
            DamageNumberTier.entries.map { damageNumberColor(it, remapped) },
        )
        val scales = DamageNumberTier.entries.map(::damageNumberScale)
        assertEquals(scales.sorted(), scales)
        assertEquals(scales.size, scales.toSet().size)
        assertEquals(0.4f, damageNumberPop(0f, 0f), 0.001f)
        assertEquals(1.25f, damageNumberPop(90f, 0.15f), 0.01f)
        assertEquals(1f, damageNumberPop(300f, 0.5f), 0.001f)
        assertEquals(0f, damageNumberPop(600f, 1f), 0.001f, "Numbers shrink out at the end of the drift")
        (0..440 step 10).forEach { ms -> assertTrue(damageNumberPop(ms.toFloat(), ms / 600f) in 0.4f..1.2501f) }
    }

    @Test
    fun rarityUsesFoundationTokens() {
        ItemRarity.entries.forEach { rarity -> assertEquals(Kk.rarity(rarity.rank), rarityColor(rarity)) }
    }

    @Test
    fun ramImpactInvertsOnlyTheImpactFrame() {
        assertTrue(isRamImpactFrame(0.72f))
        assertTrue(isRamImpactFrame(0.72f - 1f / 120f))
        assertFalse(isRamImpactFrame(0.72f - 0.02f))
        assertFalse(isRamImpactFrame(0f))
    }

    @Test
    fun edgeMarkersHugTheEdgeAndStayClearOfTheHud() {
        val sizes = listOf(Triple(1_440f, 810f, 1f), Triple(844f, 390f, 1f), Triple(390f, 844f, 1f), Triple(2_532f, 1_170f, 3f))
        sizes.forEach { (width, height, density) ->
            listOf(false to false, true to false, true to true).forEach { (trial, boss) ->
                listOf(1f, 1.25f, 1.75f).forEach { textScale ->
                    val keepOut = WorldHudKeepOut().update(width, height, density, textScale, trial, boss)
                    assertTrue(keepOut.count >= 3, "the HUD regions are known at $width x $height")
                    val planner = EdgeMarkerPlanner().prepare(keepOut, width, height, density, 80f * density * textScale / 1.25f)
                    val center = Offset(width * 0.5f, height * 0.5f)
                    val reach = EDGE_INSET_DP * density + maxEdgeDepth(width, height) + 0.01f
                    for (step in 0 until 144) {
                        val angle = step * PI.toFloat() / 72f
                        val target = center + Offset(cos(angle), sin(angle)) * (width + height)
                        val marker = planner.position(target)
                        val where = "$width x $height trial=$trial boss=$boss text=$textScale step=$step at $marker"
                        val left = planner.markerLeft(marker.x)
                        val right = planner.markerRight(marker.x)
                        val half = planner.markerHalfHeight()
                        val box = Rect(left, marker.y - half, right, marker.y + half)
                        assertTrue(box.left >= 0f && box.right <= width && box.top >= 0f && box.bottom <= height, "on screen: $where")
                        assertFalse(keepOut.intersects(box.left, box.top, box.right, box.bottom), "clear of the HUD: $where")
                        assertTrue(outwardGap(box, keepOut.rects(), width, height) <= reach, "hugs an edge or the HUD along it, not the field: $where")
                        // The position is the only direction cue (no arrow): it points at the target.
                        assertTrue(angleBetween(marker - center, target - center) <= 30f, "points toward the target: $where")
                    }
                }
            }
            // A target straight to the right sits on the right edge at the screen's vertical center.
            val keepOut = WorldHudKeepOut().update(width, height, density, 1.25f, trialActive = false, bossPresent = false)
            val planner = EdgeMarkerPlanner().prepare(keepOut, width, height, density, 80f * density)
            val right = planner.position(Offset(width * 0.5f + 5_000f, height * 0.5f))
            assertEquals(width - EDGE_INSET_DP * density, right.x, 0.5f)
            assertEquals(height * 0.5f, right.y, 6f * density)
        }
    }

    @Test
    fun edgeMarkersPointTowardTargetsAboveAndBelowALandscapePhone() {
        // The phone's top row and bottom clusters cover those edges: the marker sits just inside
        // them, in the target's direction, not on a side edge.
        val width = 844f
        val height = 390f
        val center = Offset(width * 0.5f, height * 0.5f)
        listOf(false to false, true to false, false to true, true to true).forEach { (trial, boss) ->
            listOf(1f, 1.25f, 1.75f).forEach { textScale ->
                val keepOut = WorldHudKeepOut().update(width, height, 1f, textScale, trial, boss)
                val planner = EdgeMarkerPlanner().prepare(keepOut, width, height, 1f, 70f * textScale / 1.25f)
                listOf(Offset(width * 0.5f + 40f, -900f), Offset(width * 0.5f + 40f, height + 900f), Offset(width * 0.5f - 60f, -900f)).forEach { target ->
                    val marker = planner.position(target)
                    val error = angleBetween(marker - center, target - center)
                    assertTrue(error <= 30f, "trial=$trial boss=$boss text=$textScale: marker $marker is $error degrees off the target $target")
                }
            }
        }
    }

    /** Degrees between two directions. */
    private fun angleBetween(a: Offset, b: Offset): Float {
        val cosine = (a.x * b.x + a.y * b.y) / (a.getDistance() * b.getDistance())
        return kotlin.math.acos(cosine.coerceIn(-1f, 1f)) * 180f / PI.toFloat()
    }

    /**
     * Smallest gap between [box] and what lies outward of it in some direction: the screen edge or
     * a HUD region overlapping it across that direction.
     */
    private fun outwardGap(box: Rect, regions: List<Rect>, width: Float, height: Float): Float {
        var gap = minOf(box.left, box.top, width - box.right, height - box.bottom)
        regions.forEach { region ->
            val acrossX = region.left < box.right && region.right > box.left
            val acrossY = region.top < box.bottom && region.bottom > box.top
            if (acrossX && region.bottom <= box.top) gap = minOf(gap, box.top - region.bottom)
            if (acrossX && region.top >= box.bottom) gap = minOf(gap, region.top - box.bottom)
            if (acrossY && region.right <= box.left) gap = minOf(gap, box.left - region.right)
            if (acrossY && region.left >= box.right) gap = minOf(gap, region.left - box.right)
        }
        return gap
    }

    @Test
    fun pointMarksNeverSitHalfOffScreen() {
        // Damage numbers and labels are kept inside the screen along each axis.
        assertEquals(46f, keepInside(10f, 40f, 40f, 390f, 6f))
        assertEquals(344f, keepInside(380f, 40f, 40f, 390f, 6f))
        assertEquals(200f, keepInside(200f, 40f, 60f, 390f, 6f))
        assertEquals(195f, keepInside(0f, 300f, 300f, 390f, 6f), "a label wider than the screen is centered")
    }

    @Test
    fun noWorldMarkUsesNumeralsOrArrowheads() {
        // Hard rule 1: circuit beacons show their place as 1–3 pips, never as digits.
        assertEquals(listOf(1, 2, 3), (0..2).map(::beaconPips))
        // Hard rule 3: kill shards are point-symmetric slivers (every vertex has its mirror), so no
        // orientation reads as an arrowhead, and they tumble instead of facing their travel.
        val points = ShardOutline.toList().chunked(2)
        assertEquals(4, points.size)
        points.forEach { (x, y) ->
            assertTrue(points.any { (ox, oy) -> kotlin.math.abs(ox + x) < 1e-6f && kotlin.math.abs(oy + y) < 1e-6f }, "mirror of ($x, $y)")
        }
        val heading = 0.7f
        val facing = heading * 57.29578f
        (0 until 7).forEach { index ->
            val offset = ((shardTumble(heading, index, 1f) - facing) % 180f + 180f) % 180f
            assertTrue(offset > 20f && offset < 160f, "shard $index is not aligned with its travel ($offset)")
        }
        assertEquals(340L, roundedDistance(344f))
        assertEquals(350L, roundedDistance(345f))
        assertEquals(9_990L, roundedDistance(1e9f))
        assertEquals(0L, roundedDistance(-5f))
    }

    @Test
    fun worldStringsAreLocalizedAndCached() {
        WorldRedesignText.entries.forEach { resource ->
            assertTrue(resource.english.isNotBlank() && resource.russian.isNotBlank())
            assertNotEquals(resource.english, resource.russian)
            listOf(resource.english, resource.russian).forEach { text ->
                listOf("·", "→", "←", "§").forEach { forbidden -> assertFalse(forbidden in text) }
            }
        }
        assertEquals("M", WorldStrings.distanceSuffix(AppLanguage.English))
        assertEquals(" М", WorldStrings.distanceSuffix(AppLanguage.Russian))
        assertTrue(WorldStrings.distanceSuffix(AppLanguage.English) === WorldStrings.distanceSuffix(AppLanguage.English))
        assertEquals("0:14", WorldStrings.timer(13.2f))
        assertEquals("1:05", WorldStrings.timer(65f))
        assertEquals("0:00", WorldStrings.timer(-2f))
        assertTrue(WorldStrings.timer(30.5f) === WorldStrings.timer(30.1f))
    }

    @Test
    fun orbitTimerNeverEntersTheHudTopRowOrBottomClusters() {
        // Desktop: a ring centered low enough keeps its label above it, clear of the timer row.
        assertEquals(588f, orbitTimerBaseline(800f, 20f, 1_440f, 1_080f, 1f))
        // Phone landscape: the ring reaches the top row, so the label moves below the ring.
        val below = orbitTimerBaseline(195f, 20f, 844f, 390f, 1f)
        assertTrue(below.isNaN() || below - 20f >= hudSafeTop(390f, 1f))
        // A ring centered above the screen: the "below" label would land under the clock; skipped.
        assertTrue(orbitTimerBaseline(-200f, 20f, 844f, 390f, 1f).isNaN())
        // A label that fits neither band is skipped instead of covering the HUD.
        assertTrue(orbitTimerBaseline(200f, 20f, 844f, 390f, 1f).isNaN())
        // Other HUD regions (trial panel) push the label to the other side or skip it.
        assertEquals(732f, orbitTimerBaseline(500f, 20f, 1_440f, 1_080f, 1f) { top, _ -> top > 600f })
        assertTrue(orbitTimerBaseline(800f, 20f, 1_440f, 1_080f, 1f) { _, _ -> false }.isNaN())
        listOf(Triple(1_440f, 810f, 1f), Triple(844f, 390f, 1f), Triple(390f, 844f, 1f)).forEach { (w, h, d) ->
            for (centerY in -210..(h.toInt() + 210) step 5) {
                val baseline = orbitTimerBaseline(centerY.toFloat(), 20f, w, h, d)
                if (!baseline.isNaN()) {
                    assertTrue(baseline - 20f >= hudSafeTop(h, d), "top row at $w x $h, y=$centerY")
                    assertTrue(baseline <= hudSafeBottom(w, h, d), "bottom clusters at $w x $h, y=$centerY")
                }
            }
        }
    }
}
