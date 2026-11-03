// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.interaction.*
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.*
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.TotemChestProjection
import kinetickk.ball.gameplay.nucleus.render.TrailPointProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Totem chests in the world and on the HUD: an openable chest (a Key held) shows the `you` key block and its charge
 * pips, a locked one the bone lock over hollow plates; a re-arming chest dims its ring, a new one grows in; the dungeon's
 * promise plates show the rolled chest; off screen only the nearest chest gets an edge marker (muted when locked); the
 * minimap pins it filled or hollow; and the Key chip keeps showing with a lock while a chest waits without a Key.
 */
class WorldTotemChestRenderingTest {
    private val roles = KkRolePalette.Default

    @Test
    fun anOpenableChestShowsTheKeyBlockAndALockedOneTheLock() {
        val open = render(scene(keys = 1, chest(charges = 3)))
        val locked = render(scene(keys = 0, chest(charges = 3)))
        // The middle plate holds the chest's centre, its key (or lock) glyph in the middle and the block around it.
        assertTrue(near(open.at(-28f, 0f), roles.you), "an openable chest fills its key block in volt (${open.at(-28f, 0f)})")
        assertFalse(near(locked.at(-28f, 0f), roles.you), "a locked chest has no volt block")
        assertTrue((-10..10).any { x -> (-10..10).any { y -> luminance(locked.at(x.toFloat(), y.toFloat())) > 0.5f } },
            "a locked chest shows the bone lock")
        // Three pips in a row under the plates, in the chest's accent.
        assertEquals(3, pipRuns(open, roles.you), "a pip per charge")
        assertEquals(1, pipRuns(render(scene(keys = 1, chest(charges = 1))), roles.you))
        assertEquals(2, pipRuns(render(scene(keys = 1, chest(charges = 2))), roles.you))
    }

    @Test
    fun aReArmingChestDimsItsRingAndANewOneGrowsIn() {
        val armed = render(scene(keys = 1, chest(charges = 2, armedAt = 0f)))
        val rearming = render(scene(keys = 1, chest(charges = 2, armedAt = 99f)))
        val ringArmed = ringLight(armed)
        val ringRearming = ringLight(rearming)
        assertTrue(ringArmed > ringRearming + 0.02f, "the ring dims while the chest re-arms ($ringArmed vs $ringRearming)")
        // Half-way through its entrance: the plates are smaller, so the pips end closer to the centre.
        val grown = lowestMark(armed)
        val young = lowestMark(render(scene(keys = 1, chest(charges = 2, bornAt = ELAPSED - 0.3f, armedAt = 0f))))
        assertTrue(young < grown - 10, "a chest still materialising draws smaller (lowest mark $young vs $grown)")
    }

    @Test
    fun promisePlatesShowTheRolledChestAndNoneWithoutOne() {
        val template = base.content.structures.template(StructureTemplateId.SPIRE)
        val room = template.rooms[template.finalRoom]
        val localX = (room.minX + room.maxX) / 2f
        val localY = (room.minY + room.maxY) / 2f
        // The Spire placed so its final room's centre is the view's centre.
        fun spire(charges: Int) = StructureFixture(template, CX - localX, CY - localY, chestCharges = charges)
        val withChest = render(base.with("structures" to structureProjection(spire(3)), "keys" to 0))
        val without = render(base.with("structures" to structureProjection(spire(0)), "keys" to 0))
        val accent = roles.element(template.element!!.ordinal)
        assertTrue(near(withChest.at(-28f, 0f), accent), "a rolled chest fills the promise's key block (${withChest.at(-28f, 0f)})")
        assertFalse(near(without.at(-28f, 0f), accent), "no chest: the promise is outlined")
        assertEquals(3, pipRuns(withChest, accent), "a pip per rolled charge")
        assertEquals(0, pipRuns(without, accent), "no pips without a chest")
    }

    @Test
    fun onlyTheNearestOffScreenChestGetsAMarkerAndALockedOneIsMuted() {
        val chests = totemChests(-3_000f + CX to CY, 2_000f + CX to CY + 500f, 5_000f + CX to CY)
        val open = base.with("totemChests" to chests, "keys" to 2)
        assertEquals(listOf(EdgeMarkerIcon.TOTEM), markers(open))
        val distance = WorldOverlayScratch.markers.distance[0]
        // The Core rests 600 below the view's centre.
        assertEquals(kotlin.math.hypot(2_000f, 100f), distance, 1f, "the nearest chest is marked")
        assertEquals(listOf(EdgeMarkerIcon.TOTEM_LOCKED), markers(open.with("keys" to 0)))
        assertEquals(Kk.Mute, edgeMarkerColor(EdgeMarkerIcon.TOTEM_LOCKED, roles))
        assertEquals(edgeMarkerColorGroup(EdgeMarkerIcon.CACHE), edgeMarkerColorGroup(EdgeMarkerIcon.TOTEM_LOCKED))
        // A chest in view gets no marker.
        assertEquals(emptyList(), markers(base.with("totemChests" to totemChests(CX + 100f to CY), "keys" to 1)))
    }

    @Test
    fun theKeyChipShowsALockWhileAChestWaitsWithoutAKey() {
        fun chipShown(model: GameplayRenderModel): Boolean {
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.English)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(1_440, 810)), Size(1_440f, 810f)) {
                drawHud(model, measurer, 1f)
            }
            return HudLayoutProbe.rect(HudBlock.KEY_CHIP) != null
        }
        val hud = hudTestModel()
        assertFalse(chipShown(hud.with("keys" to 0)), "no Key and no chest: no chip")
        assertTrue(chipShown(hud.with("keys" to 0, "totemChests" to totemChests(500f to 0f))), "a waiting chest keeps the chip")
        assertTrue(chipShown(hud.with("keys" to 2)))
    }

    // -----------------------------------------------------------------------------------------

    private fun chest(charges: Int, bornAt: Float = 0f, armedAt: Float = 0f) =
        TotemChestProjection(1L shl 8, CX, CY, charges, bornAt, armedAt)

    private fun scene(keys: Int, vararg chests: TotemChestProjection): GameplayRenderModel =
        base.with("keys" to keys, "totemChests" to immutableListOf(*chests))

    /** A quiet frame centred on (CX, CY), at a run time past any chest's entrance. */
    private val base: GameplayRenderModel = hudTestModel(WIDTH, HEIGHT).with(
        "coreX" to CX, "coreY" to CY + 600f, "previousCoreX" to CX, "previousCoreY" to CY + 600f,
        "cameraX" to CX, "cameraY" to CY, "previousCameraX" to CX, "previousCameraY" to CY,
        "velocityX" to 0f, "velocityY" to 0f, "pointerX" to 5f, "pointerY" to 5f, "elapsed" to ELAPSED, "interpolationAlpha" to 1f,
        "obstacles" to immutableListOf<ObstacleProjection>(), "enemies" to immutableListOf<EnemyProjection>(),
        "pickups" to immutableListOf<PickupProjection>(), "trail" to immutableListOf<TrailPointProjection>(),
        "sites" to immutableListOf<SiteProjection>(), "trial" to null,
    )

    private fun render(model: GameplayRenderModel): PixelMap {
        val bitmap = ImageBitmap(WIDTH.toInt(), HEIGHT.toInt())
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
            AppLanguage.English)
        val scaled = model.with("viewportDensity" to 1f, "uiScale" to 1f, "screenWidth" to WIDTH, "screenHeight" to HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH, HEIGHT)) {
            drawRect(Kk.Ink)
            drawWorld(scaled, VisualFxProjection.EMPTY, 0f, 0f, measurer)
        }
        return bitmap.toPixelMap()
    }

    private fun markers(model: GameplayRenderModel): List<EdgeMarkerIcon?> {
        render(model)
        val batch = WorldOverlayScratch.markers
        return (0 until batch.count).map { batch.icon[it] }
    }

    /** The pixel at ([x], [y]) world units from the view's centre (the world draws at scale 1 here). */
    private fun PixelMap.at(x: Float, y: Float): Color = this[(WIDTH * 0.5f + x).toInt(), (HEIGHT * 0.5f + y).toInt()]

    /** Runs of [color] along the pip row below a full-grown chest (between the plates' foot and 30 units lower). */
    private fun pipRuns(pixels: PixelMap, color: Color): Int {
        val y = pipRowY(pixels, color) ?: return 0
        var runs = 0
        var inside = false
        for (x in -40..40) {
            val hit = near(pixels.at(x.toFloat(), y), color)
            if (hit && !inside) runs++
            inside = hit
        }
        return runs
    }

    /** The first row below the plates (from 82 units down) holding [color] at the centre column, or null. */
    private fun pipRowY(pixels: PixelMap, color: Color = roles.you): Float? =
        (82..120).firstOrNull { y -> (-12..12).any { near(pixels.at(it.toFloat(), y.toFloat()), color) } }?.toFloat()

    /** The lowest row (world units below the centre) where anything is drawn over the ink, within 40 units of the axis. */
    private fun lowestMark(pixels: PixelMap): Int =
        (0..130).last { y -> (-40..40).any { luminance(pixels.at(it.toFloat(), y.toFloat())) > luminance(Kk.Ink) + 0.03f } }

    /** The brightest pixel on the ring's circle (contact radius 64) beside the plates (within 45° of the horizontal). */
    private fun ringLight(pixels: PixelMap): Float =
        ((-45..45 step 2) + (135..225 step 2)).maxOf { degrees ->
            val radians = Math.toRadians(degrees.toDouble())
            luminance(pixels.at((64.0 * kotlin.math.cos(radians)).toFloat(), (64.0 * kotlin.math.sin(radians)).toFloat()))
        }

    private fun near(actual: Color, expected: Color, tolerance: Float = 0.06f): Boolean =
        abs(actual.red - expected.red) < tolerance && abs(actual.green - expected.green) < tolerance &&
            abs(actual.blue - expected.blue) < tolerance

    private fun luminance(color: Color): Float = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private companion object {
        const val WIDTH = 800f
        const val HEIGHT = 800f
        const val CX = 40_000f
        const val CY = 40_000f
        const val ELAPSED = 30f
    }
}
