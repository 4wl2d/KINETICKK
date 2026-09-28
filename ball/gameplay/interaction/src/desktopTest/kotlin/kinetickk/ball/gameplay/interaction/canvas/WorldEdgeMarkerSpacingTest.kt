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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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
 * between two arrangements while their targets turn, or while the Core flies past two offers that
 * point almost the same way.
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

    @Test
    fun flightsPastTheOffersNeverFlipTheirMarkers() {
        // Two offers spawned as the game spawns them (560 px from the Core, one radian either side
        // of its heading, here the +x axis), with and without the totem, and the Core (the camera
        // on it) flying off on 36 courses at 3 and at 6 px per frame (the HUD is left undrawn: the
        // world renderer keeps the markers clear of the HUD's regions on its own). Flying along
        // the line through both offers, their directions stay within a hair of each other: the
        // markers still never swap back and forth. A marker never returns to a spot it just left
        // more than [ABA_PX] away, two markers trade places only toward their targets' true
        // order, and targets clearly apart keep their order.
        val failures = ArrayList<String>()
        var frames = 0
        for ((dpWidth, dpHeight) in sizes) for (language in AppLanguage.entries) for (totem in listOf(null, Offset(900f, -2_600f))) {
            for (course in 0 until FLIGHT_COURSES) for (speed in listOf(3f, 6f)) {
                val degrees = course * 360f / FLIGHT_COURSES + 5f
                val radians = degrees * PI.toFloat() / 180f
                val offers = listOf(-1f, 1f).map { side -> Offset(cos(side), sin(side)) * OFFER_DISTANCE }
                    .zip(listOf(PointOfInterestKind.SEALED_ANOMALY, PointOfInterestKind.COLLAPSING_ORBIT))
                val where = "$language ${dpWidth}x$dpHeight totem=$totem course=$degrees speed=$speed"
                val history = ArrayList<Frame>()
                startAfresh(dpWidth, dpHeight, 1f, language)
                for (frame in 0 until FLIGHT_FRAMES) {
                    val core = Offset(cos(radians), sin(radians)) * (speed * frame)
                    val boxes = draw(dpWidth, dpHeight, 1f, language, 1.25f, false, offers, totem, core = core, hud = false)
                    assertSpaced(boxes, 1f, "$where frame $frame")
                    val now = frame(dpWidth, dpHeight)
                    if (history.isNotEmpty() && history.last().icons != now.icons) history.clear() // a marker came or went
                    history += now
                    checkFlight(history, "$where frame $frame", failures)
                    frames++
                }
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} flicker(s), first: ${failures.take(6).joinToString("\n")}")
        assertTrue(frames > 500_000, "the flights draw their frames ($frames)")
    }

    @Test
    fun aCoreWeavingAlongTheOffersLineKeepsTheirMarkersInPlace() {
        // The Core flies away from both offers along the line through them, weaving a few px
        // either side of it: seen from the Core the offers point the same way to within a tenth
        // of a degree and trade their order with every weave. Their markers keep their order,
        // and neither returns to a spot it left more than [ABA_PX] away.
        val failures = ArrayList<String>()
        var frames = 0
        val offers = listOf(-1f, 1f).map { side -> Offset(cos(side), sin(side)) * OFFER_DISTANCE }
            .zip(listOf(PointOfInterestKind.SEALED_ANOMALY, PointOfInterestKind.COLLAPSING_ORBIT))
        val lineX = offers[0].first.x
        for ((dpWidth, dpHeight) in sizes) for (language in AppLanguage.entries) for (weave in listOf(3f, 8f)) {
            val where = "$language ${dpWidth}x$dpHeight weave=$weave"
            val history = ArrayList<Frame>()
            startAfresh(dpWidth, dpHeight, 1f, language)
            for (frame in 0 until WEAVE_FRAMES) {
                val core = Offset(lineX + weave * sin(frame * 0.5f), 1_000f + 4f * frame)
                val boxes = draw(dpWidth, dpHeight, 1f, language, 1.25f, false, offers, totem = null, core = core, hud = false)
                assertSpaced(boxes, 1f, "$where frame $frame")
                val now = frame(dpWidth, dpHeight)
                if (history.isNotEmpty() && history.last().icons != now.icons) history.clear()
                history += now
                checkFlight(history, "$where frame $frame", failures)
                frames++
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} flicker(s), first: ${failures.take(6).joinToString("\n")}")
        assertTrue(frames > 4_000, "the flights draw their frames ($frames)")
    }

    @Test
    fun gameScreenShakeNeverMovesAMarker() {
        // Offers and the totem 150 to 400 px past the screen edge (offers spawn 560 px from the
        // Core), the screen shaken the way the renderer shakes it at its strongest
        // ([MAX_SCREEN_SHAKE_DP] along sin/cos of the run time): markers are HUD overlays aimed
        // from the unshaken view, so not one of them moves.
        val failures = ArrayList<String>()
        var frames = 0
        for ((dpWidth, dpHeight) in sizes) for (density in listOf(1f, 3f)) for (language in AppLanguage.entries) {
            for (separation in listOf(4f, 12f)) for (beyond in listOf(150f, 400f)) for (step in 0 until SHAKE_DIRECTIONS) {
                if (density > 1f && (language != AppLanguage.Russian || step % 3 != 0)) continue
                val angle = step * 360f / SHAKE_DIRECTIONS
                val targets = listOf(angle - separation, angle, angle + separation).map { degrees ->
                    val radians = degrees * PI.toFloat() / 180f
                    // [beyond] px past the edge the ray crosses (a grown screen's edge).
                    val reach = edgeDistance(dpWidth * density + 2f * beyond, dpHeight * density + 2f * beyond, degrees)
                    Offset(cos(radians), sin(radians)) * reach
                }
                val offers = listOf(targets[0] to PointOfInterestKind.SEALED_ANOMALY, targets[1] to PointOfInterestKind.COLLAPSING_ORBIT)
                startAfresh(dpWidth, dpHeight, density, language)
                repeat(SETTLE_FRAMES) { draw(dpWidth, dpHeight, density, language, 1.25f, false, offers, targets[2]) }
                val settled = markerCenters()
                val where = "$language ${dpWidth}x$dpHeight @$density direction=$angle separation=$separation beyond=$beyond"
                assertEquals(3, settled.size, "$where: one marker per target")
                val strength = MAX_SCREEN_SHAKE_DP * density
                for (frame in 0 until GAME_SHAKE_FRAMES) {
                    val time = 3f + frame / 60f
                    draw(dpWidth, dpHeight, density, language, 1.25f, false, offers, targets[2],
                        shakeX = sin(time * 91f) * strength, shakeY = cos(time * 77f) * strength)
                    val now = markerCenters()
                    if (now != settled) failures += "$where shaken frame $frame: $settled -> $now"
                    frames++
                }
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} shaken frame(s) moved a marker, first: ${failures.take(6).joinToString("\n")}")
        assertTrue(frames > 20_000, "the shaken runs draw their frames ($frames)")
    }

    @Test
    fun aFrameWithoutMarkersStartsTheNextOnesAfresh() {
        // The totem off screen, then back in view for a frame (no marker at all), then off screen
        // again 5 degrees further round (along a clear stretch of edge, more ring samples than a
        // marker follows in a frame): its marker is where a lone marker for that target goes, not
        // dragged toward the spot it held before the gap. Without the gap it is dragged, so the
        // memory is what the gap clears.
        val turns = mapOf((1_440f to 810f) to -100f, (844f to 390f) to 185f, (390f to 844f) to -85f)
        for ((dpWidth, dpHeight) in sizes) {
            val from = turns.getValue(dpWidth to dpHeight)
            val before = totemAt(from)
            val after = totemAt(from + 5f)
            val where = "${dpWidth}x$dpHeight"
            startAfresh(dpWidth, dpHeight, 1f, AppLanguage.English)
            draw(dpWidth, dpHeight, 1f, AppLanguage.English, 1.25f, false, emptyList(), before)
            draw(dpWidth, dpHeight, 1f, AppLanguage.English, 1.25f, false, emptyList(), after)
            val followed = markerCenters().single()
            val fresh = WorldOverlayScratch.planner.position(screenOf(after, dpWidth, dpHeight))
            assertTrue(distance(followed, fresh) > 0.5f, "$where: without a gap the marker follows its last spot ($followed, alone $fresh)")
            draw(dpWidth, dpHeight, 1f, AppLanguage.English, 1.25f, false, emptyList(), before)
            draw(dpWidth, dpHeight, 1f, AppLanguage.English, 1.25f, false, emptyList(), totem = null)
            assertEquals(0, WorldOverlayScratch.markers.count, "$where: no marker in the gap")
            draw(dpWidth, dpHeight, 1f, AppLanguage.English, 1.25f, false, emptyList(), after)
            assertEquals(fresh, markerCenters().single(), "$where: after the gap the marker is placed afresh")
        }
    }

    /** Draws a frame without edge markers, which makes the next frame's markers start afresh. */
    private fun startAfresh(dpWidth: Float, dpHeight: Float, density: Float, language: AppLanguage) {
        draw(dpWidth, dpHeight, density, language, 1.25f, false, emptyList(), totem = null)
        assertEquals(0, WorldOverlayScratch.markers.count, "a frame without off-screen targets")
    }

    /** The totem [DISTANCE] away at [degrees] (clockwise from the right, screen y down). */
    private fun totemAt(degrees: Float): Offset {
        val radians = degrees * PI.toFloat() / 180f
        return Offset(cos(radians), sin(radians)) * DISTANCE
    }

    /** Screen position of world point [at] with the camera on the world origin. */
    private fun screenOf(at: Offset, width: Float, height: Float): Offset = Offset(width * 0.5f + at.x, height * 0.5f + at.y)

    /** Distance from the center of a [width] x [height] view to its edge along [degrees]. */
    private fun edgeDistance(width: Float, height: Float, degrees: Float): Float {
        val radians = degrees * PI.toFloat() / 180f
        val alongX = abs(cos(radians))
        val alongY = abs(sin(radians))
        val toSide = if (alongX > 1e-4f) width * 0.5f / alongX else Float.POSITIVE_INFINITY
        val toTopOrBottom = if (alongY > 1e-4f) height * 0.5f / alongY else Float.POSITIVE_INFINITY
        return min(toSide, toTopOrBottom)
    }

    /** One frame's edge markers: kinds, centers and their targets (screen px), in drawing order. */
    private class Frame(val icons: List<EdgeMarkerIcon?>, val centers: List<Offset>, val targets: List<Offset>, val width: Float, val height: Float)

    private fun frame(width: Float, height: Float): Frame {
        val batch = WorldOverlayScratch.markers
        return Frame(List(batch.count) { batch.icon[it] }, markerCenters(), List(batch.count) { Offset(batch.targetX[it], batch.targetY[it]) },
            width, height)
    }

    /**
     * Checks the newest frame of [history] (frames with the same markers) against the ones before
     * it: no marker returns within 3 px of a spot it held in the last [RETURN_FRAMES] frames after
     * straying more than [ABA_PX] from it; a pair of markers whose targets lie within a quarter
     * turn of each other changes its order around the screen center only into its targets'
     * order, and keeps that order while the targets are over [ORDER_MARGIN_DEGREES] apart.
     */
    private fun checkFlight(history: List<Frame>, where: String, failures: MutableList<String>) {
        val now = history.last()
        for (marker in now.centers.indices) {
            for (back in 2..minOf(history.size - 1, RETURN_FRAMES)) {
                val earlier = history[history.size - 1 - back].centers[marker]
                if (distance(now.centers[marker], earlier) > 3f) continue
                val strayed = (1 until back).maxOf { distance(history[history.size - 1 - it].centers[marker], earlier) }
                if (strayed > ABA_PX) failures += "$where: marker $marker left $earlier by $strayed px and came back " +
                    history.subList(history.size - 1 - back, history.size).map { it.centers[marker] }
                break
            }
        }
        val previous = history.getOrNull(history.size - 2)
        for (first in now.centers.indices) for (second in first + 1 until now.centers.size) {
            val targets = turn(now, now.targets, first, second)
            if (abs(targets) > 90f) continue
            val markers = turn(now, now.centers, first, second)
            if (abs(targets) > ORDER_MARGIN_DEGREES && markers * targets <= 0f) {
                failures += "$where: markers $first, $second at ${now.centers} out of their targets' order ($targets degrees apart)"
            }
            if (previous == null) continue
            val before = turn(previous, previous.centers, first, second)
            if (before * markers < 0f && markers * targets < 0f) {
                failures += "$where: markers $first, $second swapped ${previous.centers} -> ${now.centers} against their targets' order " +
                    "($targets degrees apart)"
            }
        }
    }

    /** Degrees from [points] [first] to [second], seen from the screen center, the short way round. */
    private fun turn(frame: Frame, points: List<Offset>, first: Int, second: Int): Float {
        fun direction(point: Offset) = atan2(point.y - frame.height * 0.5f, point.x - frame.width * 0.5f) * 180f / PI.toFloat()
        var turn = direction(points[second]) - direction(points[first])
        while (turn < -180f) turn += 360f
        while (turn > 180f) turn -= 360f
        return turn
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
     * Draws the world and (with [hud]) the HUD with the camera on the Core at [core] (the world
     * origin by default), the [offers] (world positions) and an optional [totem], the world shaken
     * by ([shakeX], [shakeY]); returns the edge-marker boxes in drawing order (offers first, then
     * the totem).
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
        core: Offset = Offset.Zero,
        hud: Boolean = true,
    ): List<Rect> {
        val width = dpWidth * density
        val height = dpHeight * density
        val measurer = measurers.getOrPut(Triple(density, language, textScale)) {
            CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr, cacheSize = 64),
                textScale, language, HudTestFonts.typography.copy(localeList = language.localeList()))
        }
        val model = base(dpWidth, dpHeight).with(
            "screenWidth" to width, "screenHeight" to height, "uiScale" to density,
            "coreX" to core.x, "coreY" to core.y, "cameraX" to core.x, "cameraY" to core.y,
            "totem" to totem?.let { TotemProjection(it.x, it.y, 1f) },
            "enemies" to (if (boss) listOf(EnemyProjection(4, EnemyType.ELITE, 0f, -9_000f, 0f, 0f, 500f, 1_000f, 38f, 0f, 0f, 0f, 0f, 0f, 0f,
                false)) else emptyList()).toImmutableList(),
            "pointsOfInterest" to offers.map { (at, kind) ->
                PointOfInterestProjection(kind, kind.name, at.x, at.y, false, 20f, 0, 0f, immutableListOf(), 0f, 0f)
            }.toImmutableList(),
        )
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(width, height)) {
            drawWorld(model, VisualFxProjection.EMPTY, shakeX, shakeY, measurer)
            if (hud) drawHud(model, measurer, 1f)
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

        /** Courses flown past the offers, and frames per flight. */
        const val FLIGHT_COURSES = 36
        const val FLIGHT_FRAMES = 600

        /** How far from the Core the game spawns its offers. */
        const val OFFER_DISTANCE = 560f

        /** A marker that strays this far and comes back flickers. */
        const val ABA_PX = 12f

        /** Targets this many degrees apart show their markers in their order (past the planner's order hysteresis). */
        const val ORDER_MARGIN_DEGREES = 2f

        /** Frames of the weaving flight. */
        const val WEAVE_FRAMES = 400

        /** Game-shaken frames per direction. */
        const val GAME_SHAKE_FRAMES = 30
    }
}
