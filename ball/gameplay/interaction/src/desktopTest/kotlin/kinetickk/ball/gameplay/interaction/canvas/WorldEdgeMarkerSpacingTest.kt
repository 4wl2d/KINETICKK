// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.ball.gameplay.nucleus.render.TotemProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.localeList
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Several off-screen targets at once (two offers and the totem, as the game spawns them) drawn by
 * the real world renderer next to the real HUD (bundled fonts): no two edge markers of a frame
 * overlap, each keeps [EDGE_MARKER_SPACING_DP] from the others, and they keep the order of their
 * targets along the edge, so every distance stays legible and each still points its own way.
 * From frame to frame the markers stay put while the screen shakes and never flick back and forth
 * between two arrangements while their targets turn.
 */
class WorldEdgeMarkerSpacingTest {
    private val sizes = listOf(1_440f to 810f, 844f to 390f, 390f to 844f)

    @Test
    fun offersAboveTheViewDrawSeparateMarkers() {
        // The review scene: a sealed anomaly straight above the view, a resonant circuit and a
        // collapsing orbit 28 degrees to either side of it, with and without an elite alive.
        for ((dpWidth, dpHeight) in sizes) for (density in listOf(1f, 3f)) for (language in AppLanguage.entries) {
            for (textScale in listOf(1f, 1.25f, 1.75f)) for (boss in listOf(false, true)) {
                val targets = listOf(Offset(-1_400f, -2_600f), Offset(0f, -3_000f), Offset(1_400f, -2_600f))
                val kinds = listOf(PointOfInterestKind.RESONANT_CIRCUIT, PointOfInterestKind.SEALED_ANOMALY, PointOfInterestKind.COLLAPSING_ORBIT)
                val boxes = draw(dpWidth, dpHeight, density, language, textScale, boss, targets.zip(kinds), totem = null)
                val where = "$language ${dpWidth}x$dpHeight @$density text=$textScale boss=$boss"
                assertEquals(3, boxes.size, "$where: one marker per offer ($boxes)")
                assertSpaced(boxes, density, where)
                // Left to right as their targets are: the circuit, the anomaly, the orbit.
                assertTrue(boxes[0].center.x < boxes[1].center.x && boxes[1].center.x < boxes[2].center.x, "$where: order kept ($boxes)")
            }
        }
    }

    @Test
    fun nearbyTargetsInEveryDirectionKeepTheirMarkersApart() {
        var frames = 0
        for ((dpWidth, dpHeight) in sizes) for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.75f)) {
            for (boss in listOf(false, true)) for (separation in listOf(4f, 12f, 28f)) for (step in 0 until DIRECTIONS) {
                // Two offers and the totem, [separation] degrees apart around the direction.
                val angle = step * 360f / DIRECTIONS
                val directions = listOf(angle - separation, angle, angle + separation)
                val targets = directions.map { degrees ->
                    val radians = degrees * PI.toFloat() / 180f
                    Offset(cos(radians), sin(radians)) * DISTANCE
                }
                val offers = listOf(targets[0] to PointOfInterestKind.SEALED_ANOMALY, targets[1] to PointOfInterestKind.COLLAPSING_ORBIT)
                val boxes = draw(dpWidth, dpHeight, 1f, language, textScale, boss, offers, totem = targets[2])
                val where = "$language ${dpWidth}x$dpHeight text=$textScale boss=$boss direction=$angle separation=$separation"
                assertEquals(3, boxes.size, "$where: one marker per target ($boxes)")
                assertSpaced(boxes, 1f, where)
                val keepOut = WorldOverlayScratch.keepOut
                boxes.forEach { box ->
                    assertTrue(box.left >= 0f && box.top >= 0f && box.right <= dpWidth && box.bottom <= dpHeight, "$where: $box on screen")
                    assertFalse(keepOut.intersects(box.left, box.top, box.right, box.bottom), "$where: $box clear of the HUD")
                }
                frames++
            }
        }
        assertTrue(frames > 1_000, "the sweep draws its frames ($frames)")
    }

    @Test
    fun turningTargetsNeverSendAMarkerBackAndForth() {
        // The view turns past two offers and the totem in quarter-degree steps, as a moving Core
        // turns them: a marker may jump to a new spot (across a HUD block, or when its run
        // rearranges), but never back to the spot it just left, and the markers keep their
        // targets' order around the screen center in every frame.
        var frames = 0
        for ((dpWidth, dpHeight) in sizes) for (language in AppLanguage.entries) for (textScale in listOf(1.25f, 1.75f)) {
            for (boss in listOf(false, true)) for (separation in listOf(4f, 12f)) {
                val history = ArrayList<List<Offset>>()
                for (step in 0 until SWEEP_STEPS) {
                    val angle = step * 360f / SWEEP_STEPS
                    val boxes = drawAround(dpWidth, dpHeight, language, textScale, boss, angle, separation)
                    val where = "$language ${dpWidth}x$dpHeight text=$textScale boss=$boss direction=$angle separation=$separation"
                    assertEquals(3, boxes.size, "$where: one marker per target ($boxes)")
                    assertSpaced(boxes, 1f, where)
                    val centers = markerCenters()
                    assertInTargetOrder(centers, dpWidth, dpHeight, angle, where)
                    history += centers
                    assertNoReturn(history, where)
                    frames++
                }
            }
        }
        assertTrue(frames > 10_000, "the sweep draws its frames ($frames)")
    }

    @Test
    fun screenShakeLeavesCrowdedMarkersInPlace() {
        // The markers settle at a direction; then the screen shakes 3 px either way (the targets
        // with it) and not one marker moves, in any direction.
        for ((dpWidth, dpHeight) in sizes) for (language in AppLanguage.entries) for (boss in listOf(false, true)) {
            for (separation in listOf(4f, 12f)) for (step in 0 until SHAKE_DIRECTIONS) {
                val angle = step * 360f / SHAKE_DIRECTIONS
                repeat(SETTLE_FRAMES) { drawAround(dpWidth, dpHeight, language, 1.25f, boss, angle, separation) }
                val settled = markerCenters()
                for (frame in 0 until SHAKEN_FRAMES) {
                    val shakeX = if (frame % 2 == 0) 3f else -3f
                    val shakeY = if (frame % 3 == 0) 2f else -2f
                    drawAround(dpWidth, dpHeight, language, 1.25f, boss, angle, separation, shakeX, shakeY)
                    val where = "$language ${dpWidth}x$dpHeight boss=$boss direction=$angle separation=$separation shaken frame $frame"
                    assertEquals(settled, markerCenters(), "$where: the markers stay where they settled")
                }
            }
        }
    }

    /**
     * Draws two offers and the totem [separation] degrees apart around [angle] (degrees clockwise
     * from the right, screen y down), [DISTANCE] from the Core, with the screen shaken by
     * ([shakeX], [shakeY]); returns the edge-marker boxes.
     */
    private fun drawAround(
        dpWidth: Float,
        dpHeight: Float,
        language: AppLanguage,
        textScale: Float,
        boss: Boolean,
        angle: Float,
        separation: Float,
        shakeX: Float = 0f,
        shakeY: Float = 0f,
    ): List<Rect> {
        val targets = listOf(angle - separation, angle, angle + separation).map { degrees ->
            val radians = degrees * PI.toFloat() / 180f
            Offset(cos(radians), sin(radians)) * DISTANCE
        }
        val offers = listOf(targets[0] to PointOfInterestKind.SEALED_ANOMALY, targets[1] to PointOfInterestKind.COLLAPSING_ORBIT)
        return draw(dpWidth, dpHeight, 1f, language, textScale, boss, offers, totem = targets[2], shakeX, shakeY)
    }

    /** Centers of the edge markers drawn in the last frame, in drawing order (offers, then the totem). */
    private fun markerCenters(): List<Offset> {
        val batch = WorldOverlayScratch.markers
        return List(batch.count) { Offset(batch.markerX[it], batch.markerY[it]) }
    }

    /**
     * The markers of targets at increasing directions around [angle] (drawing order) point, from
     * the screen center, in increasing directions too.
     */
    private fun assertInTargetOrder(centers: List<Offset>, width: Float, height: Float, angle: Float, where: String) {
        val turns = centers.map { center ->
            var turn = atan2(center.y - height * 0.5f, center.x - width * 0.5f) * 180f / PI.toFloat() - angle
            while (turn < -180f) turn += 360f
            while (turn > 180f) turn -= 360f
            turn
        }
        assertTrue(turns.zipWithNext().all { (first, second) -> first < second }, "$where: markers $centers out of their targets' order")
    }

    /**
     * When a marker of the newest frame in [history] jumped more than [JUMP_PX] from where it was
     * the frame before, it did not return to a spot it held within the [RETURN_FRAMES] before that.
     */
    private fun assertNoReturn(history: List<List<Offset>>, where: String) {
        if (history.size < 3) return
        val now = history.last()
        val before = history[history.size - 2]
        for (marker in now.indices) {
            if (distance(now[marker], before[marker]) <= JUMP_PX) continue
            for (back in 3..minOf(history.size, RETURN_FRAMES + 2)) {
                val earlier = history[history.size - back][marker]
                assertTrue(distance(now[marker], earlier) > 3f,
                    "$where: marker $marker jumped from ${before[marker]} back to $earlier, where it was ${back - 1} frames ago")
            }
        }
    }

    private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

    /** Every pair of [boxes] is at least the marker spacing apart along one axis. */
    private fun assertSpaced(boxes: List<Rect>, density: Float, where: String) {
        for (first in boxes.indices) for (second in first + 1 until boxes.size) {
            val a = boxes[first]
            val b = boxes[second]
            val gap = max(max(b.left - a.right, a.left - b.right), max(b.top - a.bottom, a.top - b.bottom))
            assertTrue(gap >= EDGE_MARKER_SPACING_DP * density - 0.01f, "$where: markers $a and $b are $gap px apart")
        }
    }

    /**
     * Draws the world and the HUD with the camera on the Core at the world origin, the [offers]
     * (screen offsets from the view center to world positions) and an optional [totem], the world
     * shaken by ([shakeX], [shakeY]); returns the edge-marker boxes in drawing order (offers first,
     * then the totem).
     */
    private fun draw(
        dpWidth: Float,
        dpHeight: Float,
        density: Float,
        language: AppLanguage,
        textScale: Float,
        boss: Boolean,
        offers: List<Pair<Offset, PointOfInterestKind>>,
        totem: Offset?,
        shakeX: Float = 0f,
        shakeY: Float = 0f,
    ): List<Rect> {
        val width = dpWidth * density
        val height = dpHeight * density
        val measurer = measurers.getOrPut(Triple(density, language, textScale)) {
            CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr, cacheSize = 64),
                textScale, language, HudTestFonts.typography.copy(localeList = language.localeList()))
        }
        val model = base(dpWidth, dpHeight).with(
            "screenWidth" to width, "screenHeight" to height, "uiScale" to density,
            "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f,
            "totem" to totem?.let { TotemProjection(it.x, it.y, 1f) },
            "enemies" to (if (boss) listOf(EnemyProjection(4, EnemyType.ELITE, 0f, -9_000f, 0f, 0f, 500f, 1_000f, 38f, 0f, 0f, 0f, 0f, 0f, 0f,
                false)) else emptyList()).toImmutableList(),
            "pointsOfInterest" to offers.map { (at, kind) ->
                PointOfInterestProjection(kind, kind.name, at.x, at.y, false, 20f, 0, 0f, immutableListOf(), 0f, 0f)
            }.toImmutableList(),
        )
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(width, height)) {
            drawWorld(model, VisualFxProjection.EMPTY, shakeX, shakeY, measurer)
            drawHud(model, measurer, 1f)
        }
        return WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
    }

    private val models = HashMap<Pair<Float, Float>, GameplayRenderModel>()
    private fun base(width: Float, height: Float): GameplayRenderModel = models.getOrPut(width to height) { hudTestModel(width, height) }

    private val measurers = HashMap<Triple<Float, AppLanguage, Float>, CanvasTextMeasurer>()

    // The size passed to draw sets the screen; a 1 px bitmap keeps the sweep fast.
    private val canvas = Canvas(ImageBitmap(1, 1))

    private companion object {
        const val DIRECTIONS = 36
        const val DISTANCE = 3_000f

        /** Quarter-degree steps around the view. */
        const val SWEEP_STEPS = 1_440

        /** A marker move this long is a jump to another spot (a few ring samples at least). */
        const val JUMP_PX = 30f

        /** How many frames back a jumping marker must not return to. */
        const val RETURN_FRAMES = 8

        const val SHAKE_DIRECTIONS = 72
        const val SETTLE_FRAMES = 6
        const val SHAKEN_FRAMES = 12
    }
}
