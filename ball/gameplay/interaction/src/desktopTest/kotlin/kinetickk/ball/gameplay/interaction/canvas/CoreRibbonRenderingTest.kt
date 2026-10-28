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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.interaction.fx.CoreRibbonProjection
import kinetickk.ball.gameplay.interaction.fx.ribbonLength
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Core ribbon drawn over ink at density 1, the Core at the screen centre: it follows the recorded path (not the
 * velocity), never bridges a teleport, keeps its rails off at low particle density, ends inside its length, carries the
 * Dash ghosts on the path, cools on straights and turns pastel (never the cursor's green) in Baby mode.
 */
class CoreRibbonRenderingTest {
    private val cx = WIDTH / 2
    private val cy = HEIGHT / 2

    @Test
    fun theRibbonFollowsTheCurvedPathNotTheVelocity() {
        // Moving +x at 900 u/s; the path runs 120 u back along −x, then turns 90° and runs 200 u along +y.
        val path = line(0f, 0f, -120f, 0f, 12) + line(-120f, 0f, -120f, 200f, 20)
        val pixels = render(model(), ribbon(path))
        assertTrue(pixels.share(cx - 120, cy + 60, ROLES.you) > 0.1f, "the turned leg carries the ribbon")
        // Today's wedge ran 334 u straight back along −v: the ribbon leaves that line at the turn.
        for (x in cx - 330..cx - 140) for (y in cy - 6..cy + 6) {
            assertEquals(Kk.Ink, pixels[x, y], "nothing lies on the velocity line past the turn at ($x, $y)")
        }
    }

    @Test
    fun noSegmentCrossesATeleport() {
        // The fourth sample starts a chain; the older samples lie 400 u away, below.
        val near = line(0f, 0f, -40f, 0f, 4)
        val far = line(-40f, 400f, -200f, 400f, 16)
        val pixels = render(model(), ribbon(near + far, startAt = near.size - 1))
        assertTrue(pixels.share(cx - 20, cy, ROLES.you) > 0.2f, "the chain at the Core draws")
        for (y in cy + 20..cy + 380) for (x in cx - 44..cx - 36) {
            assertEquals(Kk.Ink, pixels[x, y], "no segment bridges the jump at ($x, $y)")
        }
        for (x in cx - 200..cx - 40) assertEquals(Kk.Ink, pixels[x, cy + 400], "the older chain is not drawn at ($x, ${cy + 400})")
    }

    @Test
    fun lowParticlesDrawTheRibbonWithoutRails() {
        val ribbon = ribbon(line(0f, 0f, -400f, 0f, 40))
        val normal = render(model(), ribbon)
        val low = render(model(particles = ParticleDensity.LOW), ribbon)
        for (side in listOf(-1, 1)) {
            val rows = if (side < 0) (cy - 15)..(cy - 11) else (cy + 11)..(cy + 15)
            assertTrue(rows.maxOf { normal.share(cx - 60, it, Kk.Bone) } > 0.15f, "a bone rail runs 13 u to the side ($side)")
            rows.forEach { assertEquals(Kk.Ink, low[cx - 60, it], "low particle density draws no rail (row $it)") }
        }
        assertTrue(low.share(cx - 60, cy, ROLES.you) > 0.2f, "the ribbon itself still draws at low density")
        // The rails stop after 70 % of the length; the ribbon goes on.
        val length = ribbonLength(900f)
        val past = (cx - (length * 0.7f).toInt() - 4)
        (cy + 10..cy + 16).forEach { assertEquals(Kk.Ink, normal[past, it], "no rail past 70 % of the length (row $it)") }
    }

    @Test
    fun theRibbonEndsBluntAndInsideItsLength() {
        for (speed in listOf(300f, 900f, 2_000f)) {
            val length = ribbonLength(speed)
            val pixels = render(model(speedX = speed), ribbon(line(0f, 0f, -760f, 0f, 64)))
            val end = cx - (length + 2f).toInt()
            for (x in 0 until end) for (y in cy - 16..cy + 16) {
                assertEquals(Kk.Ink, pixels[x, y], "at $speed u/s nothing draws past $length u ($x, $y)")
            }
            val late = cx - (length * 0.8f).toInt()
            assertTrue(pixels.share(late, cy, ROLES.you) > 0.05f, "at $speed u/s the ribbon still shows at 80 % of its length")
        }
    }

    @Test
    fun dashGhostsFollowThePath() {
        // A Dash bends the path: 60 u back along −x, then along +y. The first ghost sits 84 u along the path, at (−60, 24),
        // where the old afterimage sat 84 u back along the velocity, at (−84, 0).
        val ribbon = ribbon(line(0f, 0f, -60f, 0f, 6) + line(-60f, 0f, -60f, 200f, 20), dashNewest = 26)
        val dash = render(model(dash = 0.24f, particles = ParticleDensity.LOW), ribbon)
        assertTrue((cx - 80..cx - 76).maxOf { dash.share(it, cy + 24, ROLES.you) } > 0.3f, "the first ghost's ring passes 18 u left of (−60, 24)")
        for (x in cx - 86..cx - 82) for (y in cy - 20..cy - 16) {
            assertEquals(Kk.Ink, dash[x, y], "no ghost along the velocity line ($x, $y)")
        }
        val cruise = render(model(particles = ParticleDensity.LOW), ribbon)
        assertEquals(Kk.Ink, cruise[cx - 78, cy + 24], "no ghost outside a Dash")
        val fading = render(model(dash = 0.06f, particles = ParticleDensity.LOW), ribbon)
        val full = (cx - 80..cx - 76).maxOf { dash.share(it, cy + 24, ROLES.you) }
        val quarter = (cx - 80..cx - 76).maxOf { fading.share(it, cy + 24, ROLES.you) }
        assertTrue(abs(quarter / full - 0.25f) < 0.06f, "the ghosts fade with the Dash phase ($quarter of $full)")
    }

    @Test
    fun aStraightCourseCoolsTheRibbon() {
        val ribbon = ribbon(line(0f, 0f, -400f, 0f, 40))
        val carving = render(model(straightness = 0f), ribbon).share(cx - 45, cy, ROLES.you)
        val straight = render(model(straightness = 1f), ribbon).share(cx - 45, cy, ROLES.you)
        assertTrue(carving > 0.3f, "a carving Core's ribbon glows ($carving)")
        assertTrue(abs(straight / carving - 0.7f) < 0.03f, "a fully straight course keeps 70 % of the light ($straight of $carving)")
    }

    @Test
    fun anEasyRibbonIsPastelVoltWithLilacRails() {
        val ribbon = ribbon(line(0f, 0f, -400f, 0f, 40))
        val pixels = render(model(easy = true), ribbon)
        assertTrue(pixels.share(cx - 45, cy, easyPastelYou(ROLES)) > 0.3f, "Baby mode draws the ribbon in pastel volt")
        assertEquals(0f, pixels.share(cx - 45, cy, ROLES.you), "not in the plain volt")
        assertEquals(0f, pixels.share(cx - 45, cy, ROLES.soft), "never in the harmless cursor's green")
        assertTrue((cy + 11..cy + 15).maxOf { pixels.share(cx - 60, it, easyPastelLilac(ROLES)) } > 0.15f, "pastel lilac rails")
    }

    @Test
    fun aSlowOrFinishedRunDrawsNoRibbon() {
        val ribbon = ribbon(line(0f, 0f, -400f, 0f, 40))
        for (model in listOf(model(speedX = 25f), model().with("phase" to GamePhase.GAME_OVER))) {
            val pixels = render(model, ribbon)
            for (x in cx - 200..cx) for (y in cy - 16..cy + 16) assertEquals(Kk.Ink, pixels[x, y], "nothing at ($x, $y)")
        }
        val empty = render(model(), CoreRibbonProjection.EMPTY)
        assertEquals(Kk.Ink, empty[cx - 45, cy], "no samples, no ribbon")
    }

    private fun model(
        speedX: Float = 900f,
        dash: Float = 0f,
        straightness: Float = 0f,
        easy: Boolean = false,
        particles: ParticleDensity = ParticleDensity.NORMAL,
    ): GameplayRenderModel {
        val base = hudTestModel(WIDTH.toFloat(), HEIGHT.toFloat())
        return base.with(
            "phase" to GamePhase.RUNNING, "coreX" to CORE, "coreY" to CORE, "previousCoreX" to CORE, "previousCoreY" to CORE,
            "cameraX" to CORE, "cameraY" to CORE, "previousCameraX" to CORE, "previousCameraY" to CORE,
            "velocityX" to speedX, "velocityY" to 0f, "dashPhaseTime" to dash, "overdriveTime" to 0f, "coreStraightness" to straightness,
            "easyMode" to easy, "settings" to base.settings.copy(particleDensity = particles),
        )
    }

    /** Points every (length / [count]) from ([fromX], [fromY]) (exclusive) to ([toX], [toY]) (inclusive), Core-relative. */
    private fun line(fromX: Float, fromY: Float, toX: Float, toY: Float, count: Int): List<Pair<Float, Float>> =
        (1..count).map { step -> fromX + (toX - fromX) * step / count to fromY + (toY - fromY) * step / count }

    /** Core-relative [points] (newest first), age 0, the oldest (or [startAt]) starting the chain, the newest [dashNewest] dashed. */
    private fun ribbon(points: List<Pair<Float, Float>>, startAt: Int = points.lastIndex, dashNewest: Int = 0): CoreRibbonProjection =
        CoreRibbonProjection.of(
            FloatArray(points.size * 2) { CORE + if (it % 2 == 0) points[it / 2].first else points[it / 2].second },
            FloatArray(points.size),
            IntArray(points.size) { (if (it == startAt) CoreRibbonProjection.START else 0) or (if (it < dashNewest) CoreRibbonProjection.DASH else 0) },
        )

    private fun render(model: GameplayRenderModel, ribbon: CoreRibbonProjection): PixelMap {
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawRect(Kk.Ink)
            drawCoreRibbon(model, ribbon, 0f, 0f, ROLES)
        }
        return bitmap.toPixelMap()
    }

    /**
     * How much of [tone] lies over ink at ([x], [y]): the shared share of every channel [tone] moves away from ink, or 0
     * when the pixel is not a mix of ink and [tone] alone (another hue, or barely touched).
     */
    private fun PixelMap.share(x: Int, y: Int, tone: Color): Float {
        val pixel = this[x, y]
        val shares = listOf(
            Triple(pixel.red, Kk.Ink.red, tone.red),
            Triple(pixel.green, Kk.Ink.green, tone.green),
            Triple(pixel.blue, Kk.Ink.blue, tone.blue),
        ).filter { (_, ink, target) -> abs(target - ink) > 0.1f }.map { (value, ink, target) -> (value - ink) / (target - ink) }
        val low = shares.min()
        val high = shares.max()
        return if (low < 0.02f || high - low > max(0.05f, high * 0.12f)) 0f else shares.average().toFloat()
    }

    private companion object {
        const val WIDTH = 1_600
        const val HEIGHT = 1_000
        const val CORE = 1_000f
        val ROLES = KkRolePalette.Default
    }
}

/**
 * A recorded Core ribbon on a circle of [radius] around ([cx], [cy]) in world units, newest first: the newest sample at
 * [startAngle] (radians, screen axes), the others spread evenly over a further [sweep] radians, aged evenly from 0 to
 * [oldestAge] s; the oldest starts the chain and the newest [dashNewest] were recorded in a Dash.
 */
internal fun curvedRibbon(
    cx: Float,
    cy: Float,
    radius: Float,
    startAngle: Float,
    sweep: Float,
    samples: Int,
    dashNewest: Int = 0,
    oldestAge: Float = 0.4f,
): CoreRibbonProjection = CoreRibbonProjection.of(
    FloatArray(samples * 2) { coordinate ->
        val angle = startAngle + sweep * (coordinate / 2) / (samples - 1)
        if (coordinate % 2 == 0) cx + cos(angle) * radius else cy + sin(angle) * radius
    },
    FloatArray(samples) { oldestAge * it / (samples - 1) },
    IntArray(samples) { index ->
        (if (index == samples - 1) CoreRibbonProjection.START else 0) or (if (index < dashNewest) CoreRibbonProjection.DASH else 0)
    },
)
