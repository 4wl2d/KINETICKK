// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.StructurePolicy
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.TrailPointProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkMix
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Structures in the world layer, drawn by the real world renderer: slab outlines that hug the collision capsules, floor
 * plates, hatched sealed doors edged in threat, the sealed room's threat brackets and the volt dashes of the next way at
 * densities 1 and 2, a scrim that darkens outside the dungeon's rooms and never inside them, and paths built once.
 */
class WorldStructureRenderingTest {
    private val roles = KkRolePalette.Default
    private val templates = StructurePolicy().templates

    @Test
    fun slabOutlinesKeepEveryCornerOnTheCollisionCapsuleAndEveryEdgeJustInsideIt() {
        val corners = FloatArray(SLAB_OUTLINE_CORNERS * 2)
        val sag = cos(PI / 8).toFloat()
        var checked = 0
        for (template in templates) for (rotation in 0 until 4) for (wall in template.walls) {
            if (wall.isDoor) continue
            val ax = rotateQuarterX(wall.ax, wall.ay, rotation)
            val ay = rotateQuarterY(wall.ax, wall.ay, rotation)
            val bx = rotateQuarterX(wall.bx, wall.by, rotation)
            val by = rotateQuarterY(wall.bx, wall.by, rotation)
            val h = wall.halfThickness
            slabOutline(ax, ay, bx, by, h, corners)
            for (corner in 0 until SLAB_OUTLINE_CORNERS) {
                val distance = segmentDistance(corners[corner * 2], corners[corner * 2 + 1], ax, ay, bx, by)
                assertTrue(distance in (h - 2f)..(h + 0.01f), "${template.name} q$rotation corner $corner at $distance of $h")
                // Every edge runs inside the capsule, by at most the half-octagon's sag.
                val next = (corner + 1) % SLAB_OUTLINE_CORNERS
                val midX = (corners[corner * 2] + corners[next * 2]) / 2f
                val midY = (corners[corner * 2 + 1] + corners[next * 2 + 1]) / 2f
                val middle = segmentDistance(midX, midY, ax, ay, bx, by)
                assertTrue(middle in (h * sag - 0.01f)..(h + 0.01f), "${template.name} q$rotation edge $corner at $middle of $h")
            }
            checked++
        }
        assertTrue(checked > 400, "every solid wall of every template in every rotation ($checked)")
    }

    @Test
    fun cachedShapesCoverTheirTemplateTurnedByItsRotation() {
        for (template in templates) for (rotation in 0 until 4) {
            val turned = rotation and 1 == 1
            val halfWidth = if (turned) template.halfHeight else template.halfWidth
            val halfHeight = if (turned) template.halfWidth else template.halfHeight
            val slabs = StructurePaths.walls(template, rotation).getBounds()
            val where = "${template.name} q$rotation"
            // The slabs reach the footprint's wall lines and at most a slab past them.
            assertTrue(slabs.left <= -halfWidth + 0.01f && slabs.right >= halfWidth - 0.01f, "$where slabs $slabs")
            assertTrue(slabs.left >= -halfWidth - 80f && slabs.right <= halfWidth + 80f && slabs.top >= -halfHeight - 80f &&
                slabs.bottom <= halfHeight + 80f, "$where slabs $slabs")
            // The floor is the rooms, turned.
            val floor = StructurePaths.floor(template, rotation).getBounds()
            val left = template.rooms.minOf { minOf(rotateQuarterX(it.minX, it.minY, rotation), rotateQuarterX(it.maxX, it.maxY, rotation)) }
            val top = template.rooms.minOf { minOf(rotateQuarterY(it.minX, it.minY, rotation), rotateQuarterY(it.maxX, it.maxY, rotation)) }
            assertEquals(left, floor.left, 0.01f, where)
            assertEquals(top, floor.top, 0.01f, where)
            assertEquals(PathFillType.EvenOdd, StructurePaths.scrim(template, rotation).fillType, where)
            assertEquals(PathFillType.NonZero, StructurePaths.floor(template, rotation).fillType, where)
        }
    }

    @Test
    fun floorsSlabsSealedDoorsBracketsAndTheNextWayDrawAtEveryDensity() {
        for (density in listOf(1f, 2f)) {
            val sealed = render(sealedScene(), density)
            val where = "@$density"
            // Floors: the sealed hall's plate, the cleared base lighter, nothing outside the footprint.
            assertTrue(near(sealed.at(300f, 450f, density), FloorColor, 0.004f), "hall floor $where: ${sealed.at(300f, 450f, density)}")
            assertTrue(sealed.at(300f, 800f, density).red > FloorColor.red + 0.01f, "the cleared base is lighter $where")
            assertTrue(near(sealed.at(560f, 450f, density), Kk.Ink, 0.004f), "outside the footprint $where")
            // The fin's slab: a darker spine, the ink body and a bone rim.
            assertTrue(near(sealed.at(0f, 300f, density), kkMix(Kk.Ink3, Kk.Ink4, 0.6f), 0.012f), "fin spine $where")
            assertTrue(near(sealed.at(0f, 315f, density), Kk.Ink3, 0.006f), "fin body $where")
            assertTrue((320 until 330).maxOf { luminance(sealed.at(0f, it.toFloat(), density)) } > 0.6f, "fin rim $where")
            // The sealed hall's lower door: a hatched ink slab edged in threat.
            val hatch = (-400 until -100 step 2).flatMap { x -> (582 until 619 step 2).map { y -> sealed.at(x.toFloat(), y.toFloat(), density) } }
            assertTrue(hatch.count { luminance(it) > luminance(Kk.Ink3) + 0.15f } > 20, "hatch lines $where")
            assertTrue(hatch.count { near(it, Kk.Ink3, 0.02f) } > 20, "the slab between the hatch lines $where")
            assertEquals(roles.threat, nearest(sealed.at(-250f, 576f, density)), "threat door edge $where")
            // The sealed hall's brackets, at their full pulse.
            assertEquals(roles.threat, nearest(sealed.at(-420f, 36f, density)), "top-left bracket $where")
            assertEquals(roles.threat, nearest(sealed.at(420f, 564f, density)), "bottom-right bracket $where")
            assertFalse(nearest(sealed.at(196f, 0f, density)) == roles.you, "a sealed door is no next way $where")

            // The hall cleared: its upper door leads on in volt, its lower door (between cleared rooms) in faint bone.
            val onward = render(onwardScene(), density)
            assertEquals(roles.you, nearest(onward.at(196f, 0f, density)), "the next way's first dash $where")
            assertEquals(roles.you, nearest(onward.at(304f, 0f, density)), "the next way's second dash $where")
            assertFalse(nearest(onward.at(250f, 0f, density)) == roles.you, "the gap between the dashes $where")
            val back = onward.at(-303f, 600f, density)
            assertFalse(nearest(back) == roles.you, "the door back is no next way $where")
            assertTrue(luminance(back) > luminance(Kk.Ink) + 0.15f, "the door back draws its bone dash $where")
            assertFalse(nearest(onward.at(-420f, 36f, density)) == roles.threat, "no brackets once the hall is clear $where")
            // From the base (cleared), the cleared hall's door onward is still the next way.
            val fromBase = render(onwardScene().with("coreX" to SX - 200f, "coreY" to SY + 800f), density)
            assertEquals(roles.you, nearest(fromBase.at(196f, 0f, density)), "a cleared room leads on $where")
        }
    }

    @Test
    fun theScrimDarkensOutsideTheDungeonsRoomsAndNeverInsideThem() {
        for (density in listOf(1f, 2f)) {
            val scene = sealedScene()
            val inside = render(scene, density)
            val held = requireNotNull(scene.structures.dungeon)
            // The same frame without a panel (a secret template's rule): no scrim at all.
            val plain = render(scene.with("structures" to scene.structures.copy(dungeon = held.copy(panel = false))), density)
            val where = "@$density"
            // The outer rim of the hall's west wall lies outside every room; the fin's rim and the Core inside the hall.
            assertTrue(luminance(inside.at(-524.5f, 450f, density)) < luminance(plain.at(-524.5f, 450f, density)) - 0.2f, "outer rim $where")
            assertEquals(plain.at(0f, 324.5f, density).toArgb(), inside.at(0f, 324.5f, density).toArgb(), "fin rim $where")
            assertEquals(plain.at(-200f, 450f, density).toArgb(), inside.at(-200f, 450f, density).toArgb(), "Core $where")
            assertEquals(plain.at(300f, 800f, density).toArgb(), inside.at(300f, 800f, density).toArgb(), "cleared base $where")
            // Fading in: a Core 60 units inside the footprint draws a quarter of the scrim.
            val edge = scene.with("coreX" to SX - 500f + 60f, "coreY" to SY + 450f)
            assertEquals(0.25f, structureInteriorAmount(edge), 0.001f)
            assertEquals(0f, structureInteriorAmount(scene.with("coreX" to SX - 560f)), "outside the footprint")
            assertEquals(1f, structureInteriorAmount(scene))
        }
    }

    /**
     * The Derelict Station's airlock and hall leave a notch open to space north of the airlock, inside the footprint: a
     * Core flying there is outside the walls, so neither the scrim nor the vignette covers it, in any rotation, while a
     * Core in the hall at the same footprint depth is under the full scrim.
     */
    @Test
    fun aCoreOutsideTheWallsInAFootprintNotchIsNeverScrimmed() {
        val template = base.content.structures.template(StructureTemplateId.DERELICT_STATION)
        for (rotation in 0..3) {
            val station = StructureFixture(template, SX, SY, rotation = rotation)
            val notch = notchScene(station, -700f, -450f)
            assertEquals(0f, structureInteriorAmount(notch), "the notch north of the airlock @r$rotation")
            assertEquals(1f, structureInteriorAmount(notchScene(station, -300f, -450f)), "the hall at the same depth @r$rotation")
        }
        val station = StructureFixture(template, SX, SY)
        val notch = notchScene(station, -700f, -450f)
        val held = requireNotNull(notch.structures.dungeon)
        val plain = notch.with("structures" to notch.structures.copy(dungeon = held.copy(panel = false)))
        for (density in listOf(1f, 2f)) {
            val core = render(notch, density).screenCentre(density)
            assertEquals(render(plain, density).screenCentre(density).toArgb(), core.toArgb(), "the Core in the notch is not inked @$density")
        }
    }

    @Test
    fun aWarmFrameBuildsNoPath() {
        val scene = sealedScene()
        render(scene, 1f)
        val built = StructurePaths.builds
        render(scene, 1f)
        render(onwardScene(), 1f)
        assertEquals(built, StructurePaths.builds, "the floor, slab and scrim paths come from the cache")
    }

    // -----------------------------------------------------------------------------------------
    // Scenes: a Spire (four stacked halls, x −500..500) at (SX, SY), the view centred 300 below its centre.

    private fun spire(clearedRooms: Int, engagedRoom: Int) = StructureFixture(
        base.content.structures.template(StructureTemplateId.SPIRE), SX, SY, clearedRooms = clearedRooms, engagedRoom = engagedRoom,
    )

    /** The lower hall sealed around the Core (the base cleared): threat doors and brackets, the scrim at full depth. */
    private fun sealedScene(): GameplayRenderModel {
        val spire = spire(clearedRooms = 0b0001, engagedRoom = 1)
        return base.with("structures" to structureProjection(spire, dungeon = spire, garrisonAlive = 5, garrisonTotal = 5))
    }

    /** The base and the lower hall cleared, the Core still in the hall: its upper door is the next way. */
    private fun onwardScene(): GameplayRenderModel {
        val spire = spire(clearedRooms = 0b0011, engagedRoom = -1)
        return base.with("structures" to structureProjection(spire, dungeon = spire))
    }

    /** [station] held as the dungeon, the Core and the view at its template point ([x], [y]). */
    private fun notchScene(station: StructureFixture, x: Float, y: Float): GameplayRenderModel {
        val (coreX, coreY) = station.at(x, y)
        return base.with(
            "structures" to structureProjection(station, dungeon = station),
            "coreX" to coreX, "coreY" to coreY, "previousCoreX" to coreX, "previousCoreY" to coreY,
            "cameraX" to coreX, "cameraY" to coreY, "previousCameraX" to coreX, "previousCameraY" to coreY,
        )
    }

    /** One run's model (its content holds the templates the renderer caches its shapes by). */
    private val base: GameplayRenderModel = hudTestModel(WIDTH, HEIGHT).with(
        "coreX" to SX - 200f, "coreY" to SY + 450f, "previousCoreX" to SX - 200f, "previousCoreY" to SY + 450f,
        "cameraX" to SX, "cameraY" to SY + 300f, "previousCameraX" to SX, "previousCameraY" to SY + 300f,
        "velocityX" to 0f, "velocityY" to 0f, "pointerX" to 5f, "pointerY" to 5f,
        // The brackets' pulse at its peak.
        "elapsed" to (PI / 12).toFloat(),
        "obstacles" to immutableListOf<ObstacleProjection>(), "enemies" to immutableListOf<EnemyProjection>(),
        "pickups" to immutableListOf<PickupProjection>(), "trail" to immutableListOf<TrailPointProjection>(),
        "sites" to immutableListOf<SiteProjection>(), "totem" to null, "trial" to null,
    )

    private fun render(model: GameplayRenderModel, density: Float): PixelMap {
        val width = (WIDTH * density).toInt()
        val height = (HEIGHT * density).toInt()
        val bitmap = ImageBitmap(width, height)
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr), 1f,
            AppLanguage.English)
        val scaled = model.with("viewportDensity" to density, "uiScale" to density, "screenWidth" to WIDTH, "screenHeight" to HEIGHT)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            drawWorld(scaled, VisualFxProjection.EMPTY, 0f, 0f, measurer)
        }
        return bitmap.toPixelMap()
    }

    /** The pixel of the Spire's template point ([x], [y]). */
    private fun PixelMap.at(x: Float, y: Float, density: Float): Color =
        this[((WIDTH * 0.5f + x) * density).toInt(), ((HEIGHT * 0.5f + y - 300f) * density).toInt()]

    /** The pixel at the screen's centre (the Core when the view is centred on it). */
    private fun PixelMap.screenCentre(density: Float): Color = this[(WIDTH * 0.5f * density).toInt(), (HEIGHT * 0.5f * density).toInt()]

    private fun near(actual: Color, expected: Color, tolerance: Float): Boolean =
        abs(actual.red - expected.red) < tolerance && abs(actual.green - expected.green) < tolerance &&
            abs(actual.blue - expected.blue) < tolerance

    /** The nearest of the scene's colors (the role hues, bone and its alpha mixes over ink, the inks) to [actual] (RGB distance). */
    private fun nearest(actual: Color): Color {
        val palette = listOf(roles.threat, roles.you, Kk.Bone, Kk.Ink, Kk.Ink3, Kk.Ink4, FloorColor) +
            (1..6).map { kkMix(Kk.Ink, Kk.Bone, it / 7f) }
        return palette.minBy { (it.red - actual.red).let { r -> r * r } + (it.green - actual.green).let { g -> g * g } +
            (it.blue - actual.blue).let { b -> b * b } }
    }

    private fun luminance(color: Color): Float = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private fun segmentDistance(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared <= 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        val px = ax + dx * t - x
        val py = ay + dy * t - y
        return sqrt(px * px + py * py)
    }

    private companion object {
        const val WIDTH = 1_200f
        const val HEIGHT = 1_400f
        const val SX = 40_000f
        const val SY = 40_000f

        /** The floor plate's ink (the renderer's own mix). */
        val FloorColor = structureFloorColor(StructurePolicy().template(StructureTemplateId.SPIRE), KkRolePalette.Default)
    }
}
