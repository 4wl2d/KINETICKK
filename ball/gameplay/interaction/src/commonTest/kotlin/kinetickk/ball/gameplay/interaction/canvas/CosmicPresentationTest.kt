// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
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
    fun edgeMarkersStayOnScreenAndClearOfTheHudCorners() {
        listOf(1440f to 810f, 844f to 390f, 390f to 844f).forEach { (width, height) ->
            val center = Offset(width * 0.5f, height * 0.5f)
            for (step in 0 until 72) {
                val angle = step * PI.toFloat() / 36f
                val target = center + Offset(cos(angle), sin(angle)) * (width + height)
                val marker = edgeMarkerPosition(target, width, height, 1f)
                assertTrue(marker.x in 24f..width - 24f, "x inside the screen at $width x $height, step $step")
                assertTrue(marker.y >= height * 0.16f && marker.y >= 80f, "clear of the top HUD row")
                assertTrue(marker.y <= if (height > width) height * 0.66f else height - 110f, "clear of the bottom HUD clusters")
                val onSide = marker.x <= 24.01f || marker.x >= width - 24.01f
                if (!onSide) assertTrue(marker.x in width * 0.26f..width * 0.74f, "top and bottom markers avoid the corners")
            }
            // A target straight to the right stays at the vertical center of the right edge.
            assertEquals(Offset(width - 24f, height * 0.5f), edgeMarkerPosition(center + Offset(5_000f, 0f), width, height, 1f))
        }
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
        assertEquals("340m", WorldStrings.distance(344f, AppLanguage.English))
        assertEquals("340 м", WorldStrings.distance(336f, AppLanguage.Russian))
        assertTrue(WorldStrings.distance(344f, AppLanguage.English) === WorldStrings.distance(341f, AppLanguage.English))
        assertEquals("0:14", WorldStrings.timer(13.2f))
        assertEquals("1:05", WorldStrings.timer(65f))
        assertEquals("0:00", WorldStrings.timer(-2f))
        assertTrue(WorldStrings.timer(30.5f) === WorldStrings.timer(30.1f))
    }
}
