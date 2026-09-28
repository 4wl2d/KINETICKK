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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

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
    fun flightsPastTheOffersNeverFlipOrStrandTheirMarkers() {
        // Two offers spawned as the game spawns them (560 px from the Core, one radian either side
        // of its heading, a random one per flight), without the totem, with it near them and with
        // it far off, and the Core (the camera on it) flying off on 36 courses at a random 3 to 6 px
        // per frame; then the flights where the verifier caught a marker stranded, pushed and back,
        // or flashing right after it stopped being stranded. (The HUD is left undrawn: the world
        // renderer keeps the markers clear of its regions on its own.) Every frame: markers
        // spaced; none goes back near a spot it left more than [ABA_PX] behind within
        // [FLIGHT_RETURN_FRAMES] frames (the verifier's flights: [REPRO_RETURN_FRAMES]), also when a
        // marker arrives or leaves; two markers trade places
        // only toward their targets' order and keep it once the targets are clearly apart; and
        // none stays over [STRANDED_ERROR_DEGREES] off its target for [STRANDED_FRAMES] frames
        // while the same targets placed afresh would point it within [FRESH_ERROR_DEGREES].
        val failures = ArrayList<String>()
        var frames = 0
        val random = Random(20_260_928)
        val flights = ArrayList<FlightSpec>()
        for ((dpWidth, dpHeight) in sizes) for (language in AppLanguage.entries) for (totemMode in 0 until 3) {
            for (course in 0 until FLIGHT_COURSES) {
                flights += FlightSpec(dpWidth, dpHeight, language, totemMode, random.nextFloat() * 2f * PI.toFloat(), course,
                    3f + 3f * random.nextFloat())
            }
        }
        flights += VERIFIER_FLIGHTS
        for (flight in flights) {
            val offers = List(2) { side ->
                val angle = flight.heading + if (side == 0) -1f else 1f
                Offset(cos(angle), sin(angle)) * OFFER_DISTANCE to
                    PointOfInterestKind.entries[(flight.course + side) % PointOfInterestKind.entries.size]
            }
            val totem = when (flight.totemMode) {
                0 -> null
                1 -> Offset(900f, -2_600f)
                else -> Offset(cos(flight.heading + 1.03f), sin(flight.heading + 1.03f)) * 1_500f
            }
            val course = flight.heading + (flight.course * 360f / FLIGHT_COURSES + 5f) * PI.toFloat() / 180f
            // A curved flight circles a center to the side of the heading, from the origin.
            val side = flight.heading + flight.turn * PI.toFloat() / 2f
            val center = Offset(cos(side), sin(side)) * flight.radius
            val startAngle = atan2(-center.y, -center.x)
            val returnFrames = if (flight in VERIFIER_FLIGHTS) REPRO_RETURN_FRAMES else FLIGHT_RETURN_FRAMES
            val checks = FlightChecks("$flight", flight.width, flight.height, failures, returnFrames)
            startAfresh(flight.width, flight.height, 1f, flight.language)
            for (frame in 0 until FLIGHT_FRAMES) {
                val core = if (flight.radius > 0f) {
                    val angle = startAngle + flight.turn * flight.speed * frame / flight.radius
                    center + Offset(cos(angle), sin(angle)) * flight.radius
                } else {
                    Offset(cos(course), sin(course)) * (flight.speed * frame)
                }
                val boxes = draw(flight.width, flight.height, 1f, flight.language, 1.25f, false, offers, totem, core = core, hud = false)
                checks.check(frame, boxes)
                frames++
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} failure(s), first: ${failures.take(8).joinToString("\n")}")
        assertTrue(frames > 300_000, "the flights draw their frames ($frames)")
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
            val checks = FlightChecks("$language ${dpWidth}x$dpHeight weave=$weave", dpWidth, dpHeight, failures)
            startAfresh(dpWidth, dpHeight, 1f, language)
            for (frame in 0 until WEAVE_FRAMES) {
                val core = Offset(lineX + weave * sin(frame * 0.5f), 1_000f + 4f * frame)
                val boxes = draw(dpWidth, dpHeight, 1f, language, 1.25f, false, offers, totem = null, core = core, hud = false)
                checks.check(frame, boxes)
                frames++
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} flicker(s), first: ${failures.take(6).joinToString("\n")}")
        assertTrue(frames > 4_000, "the flights draw their frames ($frames)")
    }

    @Test
    fun gameScreenShakeNeverMovesAMarker() {
        // Offers and the totem from 30 px inside the screen edge to 400 px past it (offers spawn
        // 560 px from the Core), the screen shaken the way the renderer shakes it at its strongest
        // ([MAX_SCREEN_SHAKE_DP] along sin/cos of the run time): marks and markers are decided,
        // and markers aimed, in the unshaken view, so no mark turns into a marker or back and not
        // one marker moves.
        val failures = ArrayList<String>()
        var frames = 0
        for ((dpWidth, dpHeight) in sizes) for (density in listOf(1f, 3f)) for (language in AppLanguage.entries) {
            for (separation in listOf(4f, 12f)) for (beyond in listOf(-30f, 10f, 40f, 150f, 400f)) for (step in 0 until SHAKE_DIRECTIONS) {
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
                val settledMarks = WorldDrawProbe.rects(WorldDrawn.POINT_MARK).size
                val where = "$language ${dpWidth}x$dpHeight @$density direction=$angle separation=$separation beyond=$beyond"
                if (beyond >= 150f) assertEquals(3, settled.size, "$where: one marker per target")
                val strength = MAX_SCREEN_SHAKE_DP * density
                for (frame in 0 until GAME_SHAKE_FRAMES) {
                    val time = 3f + frame / 60f
                    draw(dpWidth, dpHeight, density, language, 1.25f, false, offers, targets[2],
                        shakeX = sin(time * 91f) * strength, shakeY = cos(time * 77f) * strength)
                    val now = markerCenters()
                    if (now != settled) failures += "$where shaken frame $frame: $settled -> $now"
                    val marks = WorldDrawProbe.rects(WorldDrawn.POINT_MARK).size
                    if (marks != settledMarks) failures += "$where shaken frame $frame: $settledMarks marks -> $marks"
                    frames++
                }
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} shaken frame(s) moved a marker, first: ${failures.take(6).joinToString("\n")}")
        assertTrue(frames > 40_000, "the shaken runs draw their frames ($frames)")
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
     * A flight: its screen, spawn heading (radians), course index (also picking the offers'
     * kinds), Core speed (px per frame), totem (0: none, 1: far, 2: near) and, for a curved
     * flight, the [radius] of the circle it turns on and the [turn] direction (1: clockwise).
     */
    private data class FlightSpec(
        val width: Float,
        val height: Float,
        val language: AppLanguage,
        val totemMode: Int,
        val heading: Float,
        val course: Int,
        val speed: Float,
        val radius: Float = 0f,
        val turn: Float = 0f,
    )

    /**
     * The checks every frame of one flight must pass (see [flightsPastTheOffersNeverFlipOrStrandTheirMarkers]),
     * each marker followed by its kind across frames where markers arrive or leave.
     */
    private inner class FlightChecks(
        val where: String,
        val width: Float,
        val height: Float,
        val failures: MutableList<String>,
        val returnFrames: Int = FLIGHT_RETURN_FRAMES,
    ) {
        private val spots = HashMap<EdgeMarkerIcon, ArrayList<Offset>>()
        private val stranded = HashMap<EdgeMarkerIcon, Int>()
        private var previous: Frame? = null

        fun check(frame: Int, boxes: List<Rect>) {
            val at = "$where frame $frame"
            spaced(boxes, 1f)?.let { failures += "$at: $it" }
            val now = frame(width, height)
            val fresh = memorylessCenters(width, height)
            spots.keys.retainAll(now.icons.toSet())
            stranded.keys.retainAll(now.icons.toSet())
            for (index in now.icons.indices) {
                val icon = requireNotNull(now.icons[index])
                val track = spots.getOrPut(icon) { ArrayList() }
                val center = now.centers[index]
                // Back within a third of how far it strayed (at most [RETURN_PX]) of a spot it held
                // 2 to [returnFrames] frames ago, after straying more than [ABA_PX]: a flicker.
                for (back in 2..minOf(track.size, returnFrames)) {
                    val earlier = track[track.size - back]
                    val gap = distance(center, earlier)
                    if (gap > RETURN_PX) continue
                    val strayed = (1 until back).maxOf { distance(track[track.size - it], earlier) }
                    if (strayed > ABA_PX && gap <= strayed / 3f) {
                        failures += "$at: $icon left $earlier by $strayed px and came back within $gap px after $back frames " +
                            "${track.takeLast(back) + center}"
                    }
                    break
                }
                track += center
                if (track.size > returnFrames) track.removeAt(0)
                val target = now.targets[index]
                val error = abs(turn(now, listOf(target, center), 0, 1))
                val freshError = abs(turn(now, listOf(target, fresh[index]), 0, 1))
                // With another target within the order margin, which of the two a fresh placement
                // puts on the shared spot is a coin toss: such frames neither count nor end a run.
                val coincident = now.targets.indices.any { it != index && abs(turn(now, now.targets, index, it)) < ORDER_HOLD_DEGREES }
                val run = when {
                    coincident -> stranded[icon] ?: 0
                    error > STRANDED_ERROR_DEGREES && freshError <= FRESH_ERROR_DEGREES -> (stranded[icon] ?: 0) + 1
                    else -> 0
                }
                stranded[icon] = run
                if (run == STRANDED_FRAMES) failures += "$at: $icon $error degrees off its target for $run frames at $center, " +
                    "while placed afresh it would point within $freshError degrees from ${fresh[index]}"
            }
            val before = previous
            for (first in now.icons.indices) for (second in first + 1 until now.icons.size) {
                val targets = turn(now, now.targets, first, second)
                if (abs(targets) > 90f) continue
                val markers = turn(now, now.centers, first, second)
                if (abs(targets) > ORDER_MARGIN_DEGREES && markers * targets <= 0f) {
                    failures += "$at: ${now.icons[first]}, ${now.icons[second]} at ${now.centers} out of their targets' order ($targets degrees apart)"
                }
                val a = before?.icons?.indexOf(now.icons[first]) ?: -1
                val b = before?.icons?.indexOf(now.icons[second]) ?: -1
                if (a < 0 || b < 0) continue
                val then = turn(requireNotNull(before), before.centers, a, b)
                if (then * markers < 0f && markers * targets < 0f) {
                    failures += "$at: ${now.icons[first]}, ${now.icons[second]} swapped ${before.centers} -> ${now.centers} against their " +
                        "targets' order ($targets degrees apart)"
                }
            }
            previous = now
        }
    }

    /** The last frame's markers placed afresh (a planner without memory), in drawing order. */
    private fun memorylessCenters(width: Float, height: Float): List<Offset> {
        val batch = WorldOverlayScratch.markers
        memoryless.prepare(WorldOverlayScratch.keepOut, width, height, 1f, WorldOverlayScratch.planner.markerTextWidth)
        memoryless.forget()
        afresh.clear()
        for (index in 0 until batch.count) afresh.add(batch.targetX[index], batch.targetY[index], batch.distance[index], requireNotNull(batch.icon[index]))
        memoryless.place(afresh)
        return List(afresh.count) { Offset(afresh.markerX[it], afresh.markerY[it]) }
    }

    private val memoryless = EdgeMarkerPlanner()
    private val afresh = EdgeMarkerBatch()

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
        spaced(boxes, density)?.let { fail("$where: $it") }
    }

    /** Null when every pair of [boxes] is at least the marker spacing apart along one axis; else the first pair that is not. */
    private fun spaced(boxes: List<Rect>, density: Float): String? {
        for (first in boxes.indices) for (second in first + 1 until boxes.size) {
            val a = boxes[first]
            val b = boxes[second]
            val gap = max(max(b.left - a.right, a.left - b.right), max(b.top - a.bottom, a.top - b.bottom))
            if (gap < EDGE_MARKER_SPACING_DP * density - 0.01f) return "markers $a and $b are $gap px apart"
        }
        return null
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

        /**
         * How many frames back a flight's marker must not go back near a spot it left: the eight
         * frames a return reads as a flicker in, and the four after them, where no burst of
         * returns may wait; in the verifier's flights, sixteen (one swapped back after 14).
         */
        const val FLIGHT_RETURN_FRAMES = 12
        const val REPRO_RETURN_FRAMES = 16

        const val SHAKE_DIRECTIONS = 72
        const val SETTLE_FRAMES = 6
        const val SHAKEN_FRAMES = 12

        /** Courses flown past the offers, and frames per flight. */
        const val FLIGHT_COURSES = 36
        const val FLIGHT_FRAMES = 500

        /** How far from the Core the game spawns its offers. */
        const val OFFER_DISTANCE = 560f

        /** A marker that strays this far and comes back (within a third of that, at most [RETURN_PX]) flickers. */
        const val ABA_PX = 12f
        const val RETURN_PX = 24f

        /**
         * A marker this many degrees off its target for [STRANDED_FRAMES] frames in a row, while
         * the same targets placed afresh would point it within [FRESH_ERROR_DEGREES], is stranded.
         */
        const val STRANDED_ERROR_DEGREES = 30f

        /** Targets this close keep their markers' last order (the planner's order hysteresis). */
        const val ORDER_HOLD_DEGREES = 1f
        const val FRESH_ERROR_DEGREES = 15f
        const val STRANDED_FRAMES = 60

        /** Targets this many degrees apart show their markers in their order (past the planner's order hysteresis). */
        const val ORDER_MARGIN_DEGREES = 2f

        /** Frames of the weaving flight. */
        const val WEAVE_FRAMES = 400

        /** Game-shaken frames per direction. */
        const val GAME_SHAKE_FRAMES = 20

        /**
         * The flights where the verifier caught f0d9800 stranding a marker (a sealed anomaly held
         * 30 to 59 degrees off at 844x390, a resonant circuit up to 44 degrees off at 390x844) and
         * pushing one away and back (the totem before a crossing, a circuit when the orbit's
         * marker arrived next to it), and 9d051c5 flashing a totem right after a stranding ended
         * (a curved flight; 510 -> 582 -> 492 px; 366 -> 228 -> 366 px).
         */
        val VERIFIER_FLIGHTS = listOf(
            FlightSpec(844f, 390f, AppLanguage.English, 1, 5.016365f, 19, 6f),
            FlightSpec(390f, 844f, AppLanguage.Russian, 0, 1.9322724f, 14, 3f),
            FlightSpec(390f, 844f, AppLanguage.Russian, 1, 4.498208f, 26, 6f),
            FlightSpec(390f, 844f, AppLanguage.English, 1, 0.18163367f, 29, 4.5f),
            // 9d051c5 stopping a stranding while two targets lay within the order margin, in their
            // held order: the pair traded back within a frame or a few once the targets parted.
            FlightSpec(844f, 390f, AppLanguage.Russian, 2, 7 * 45f * PI.toFloat() / 180f + 0.2f, 7, 4.5f, radius = 300f, turn = -1f),
            FlightSpec(844f, 390f, AppLanguage.English, 1, 2.495924f, 1, 4.5f),
            FlightSpec(390f, 844f, AppLanguage.Russian, 1, 5.6731505f, 11, 4.5f),
        )
    }
}
