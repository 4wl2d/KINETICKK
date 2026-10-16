// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.foundation.design.Kk
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Epic signature illustrations (static, faint, behind the text), part A: the nine Kinetic epics,
// drawn in the card hue (bone) as machined objects.
//
// Each is composed on the 300 × 450 board card in board dp ([EpicABoard]) and layered like a solid
// thing: an ink knockout under its silhouette (it hides the scanlines and the watermark behind it),
// faint metal fills, then the line work, all in one layer faded out toward the text by a soft
// ellipse ([fadedArt]). Geometry lives in the card's art cache, so a steady card draws without
// allocating.

/** Ink under an object's silhouette: what lies behind it dims, so the object reads as solid. */
private const val Knockout = 0.85f

/** Brush slot of the fade mask (each illustration uses the slots below it). */
private const val MaskSlot = RewardArtCache.SLOTS - 1

/** Rect slot of the fade layer's bounds. */
private const val LayerSlot = 0

private const val Deg = (PI / 180.0).toFloat()

/**
 * The design space of these illustrations: the 300 × 450 board card in board dp, anchored at the
 * card's bottom centre. A phone card shows the same composition a little smaller, so the art keeps
 * to the lower body there too. Set by [fit] at the start of each illustration (draw thread only).
 */
private object EpicABoard {
    /** Px per board dp. */
    var s = 1f
        private set
    private var left = 0f
    private var top = 0f

    /** A hairline and a line in px, never thinner than a crisp device line. */
    var hair = 1f
        private set
    var line = 1.5f
        private set

    /**
     * Fits the board to [frame]. [solidTop] is the board y where the illustration's fade begins: when
     * the card's text leaves less room than that, the art shrinks into the bottom strip (on a phone so
     * far that it never rises past the last text row, on the board to no less than [MIN_SHRINK]).
     */
    fun fit(frame: RewardArtFrame, solidTop: Float): EpicABoard {
        val bounds = frame.bounds
        s = if (frame.compact) min(bounds.width / 300f * 0.92f, bounds.height / 450f * 1.1f) else min(bounds.width / 300f, bounds.height / 450f)
        val room = bounds.bottom - frame.textFloor
        val needed = (450f - solidTop) * s
        if (needed > room && room > 0f) s = max(s * (if (frame.compact) MIN_SHRINK_PHONE else MIN_SHRINK), s * room / needed)
        left = bounds.left + (bounds.width - 300f * s) * 0.5f
        top = bounds.bottom - 450f * s
        hair = max(s, frame.dp(0.75f))
        line = max(1.5f * s, frame.dp(1f))
        return this
    }

    private const val MIN_SHRINK = 0.62f
    private const val MIN_SHRINK_PHONE = 0.5f

    fun x(value: Float): Float = left + value * s
    fun y(value: Float): Float = top + value * s
    fun l(value: Float): Float = value * s
    fun at(px: Float, py: Float): Offset = Offset(x(px), y(py))
}

/** Layer paint of [fadedArt] (created on first draw). */
private val EpicALayer = Paint()

private val Clear = Color.Transparent

/**
 * Draws [content] in one layer and fades it by an ellipse centred at board ([cx], [cy]) with radii
 * ([rx], [ry]): opaque to [inner] of the radius, gone at the rim, with a soft shoulder between.
 */
private inline fun DrawScope.fadedArt(frame: RewardArtFrame, cx: Float, cy: Float, rx: Float, ry: Float, inner: Float, content: DrawScope.() -> Unit) {
    val b = EpicABoard
    val px = b.x(cx)
    val py = b.y(cy)
    val k = ry / rx
    val face = frame.face
    val canvas = drawContext.canvas
    // The layer covers only the fade ellipse's box within the face: the art is gone outside it.
    val layer = frame.cache.rect(LayerSlot) {
        Rect(max(face.left, px - b.l(rx)), max(face.top, py - b.l(ry)), min(face.right, px + b.l(rx)), min(face.bottom, py + b.l(ry)))
    }
    canvas.saveLayer(layer, EpicALayer)
    content()
    val mask = frame.cache.brush(MaskSlot) {
        Brush.radialGradient(
            0f to Color.Black, inner to Color.Black, inner + (1f - inner) * 0.5f to Color.Black.copy(alpha = 0.5f), 1f to Clear,
            center = Offset(px, py), radius = b.l(rx),
        )
    }
    scale(1f, k, Offset(px, py)) {
        val top = py + (layer.top - py) / k
        val bottom = py + (layer.bottom - py) / k
        drawRect(mask, Offset(layer.left, top), Size(layer.width, bottom - top), blendMode = BlendMode.DstIn)
    }
    canvas.restore()
}

// Board-space drawing helpers (paths are built once per frame geometry, draws are allocation-free).

private fun boardRect(left: Float, top: Float, right: Float, bottom: Float): Rect =
    Rect(EpicABoard.x(left), EpicABoard.y(top), EpicABoard.x(right), EpicABoard.y(bottom))

private fun Path.moveB(x: Float, y: Float) = moveTo(EpicABoard.x(x), EpicABoard.y(y))
private fun Path.lineB(x: Float, y: Float) = lineTo(EpicABoard.x(x), EpicABoard.y(y))
private fun Path.quadB(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(EpicABoard.x(cx), EpicABoard.y(cy), EpicABoard.x(x), EpicABoard.y(y))
private fun Path.ovalB(cx: Float, cy: Float, rx: Float, ry: Float) = addOval(boardRect(cx - rx, cy - ry, cx + rx, cy + ry))
private fun Path.arcB(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float, move: Boolean) =
    arcTo(boardRect(cx - rx, cy - ry, cx + rx, cy + ry), start, sweep, move)

/** A segment from ([x0], [y0]) to ([x1], [y1]) as its own contour. */
private fun Path.segB(x0: Float, y0: Float, x1: Float, y1: Float) {
    moveB(x0, y0)
    lineB(x1, y1)
}

/** The ring sector between radii [r0] and [r1] of a circle at ([cx], [cy]). */
private fun Path.annulusB(cx: Float, cy: Float, r0: Float, r1: Float, start: Float, sweep: Float) {
    arcB(cx, cy, r1, r1, start, sweep, true)
    arcB(cx, cy, r0, r0, start + sweep, -sweep, false)
    close()
}

/** The visible side band of an upright cylinder: its top ellipse at [top], its bottom at [bottom]. */
private fun Path.cylinderSide(cx: Float, rx: Float, ry: Float, top: Float, bottom: Float) {
    moveB(cx - rx, top)
    lineB(cx - rx, bottom)
    arcB(cx, bottom, rx, ry, 180f, -180f, false)
    lineB(cx + rx, top)
    arcB(cx, top, rx, ry, 0f, 180f, false)
    close()
}

/** The cylinder's outline below its top face, and a groove round the middle of its side. */
private fun Path.cylinderEdges(cx: Float, rx: Float, ry: Float, top: Float, bottom: Float, groove: Boolean) {
    moveB(cx - rx, top)
    lineB(cx - rx, bottom)
    arcB(cx, bottom, rx, ry, 180f, -180f, false)
    lineB(cx + rx, top)
    if (groove) arcB(cx, (top + bottom) * 0.5f, rx, ry, 180f, -180f, true)
}

private fun polarX(cx: Float, r: Float, degrees: Float): Float = cx + r * cos(degrees * Deg)
private fun polarY(cy: Float, r: Float, degrees: Float): Float = cy + r * sin(degrees * Deg)

/** [alpha] kept within the art's ceiling for the hue (ink knockouts may be dense). */
private fun artAlpha(color: Color, alpha: Float): Float = if (color == Kk.Ink) alpha else min(alpha, RewardArtAlpha.HIGH)

private fun DrawScope.ovalB(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float, style: DrawStyle = Fill) {
    val b = EpicABoard
    drawOval(color, Offset(b.x(cx - rx), b.y(cy - ry)), Size(b.l(2f * rx), b.l(2f * ry)), artAlpha(color, alpha), style)
}

private fun DrawScope.ovalB(brush: Brush, cx: Float, cy: Float, rx: Float, ry: Float) {
    val b = EpicABoard
    drawOval(brush, Offset(b.x(cx - rx), b.y(cy - ry)), Size(b.l(2f * rx), b.l(2f * ry)))
}

private fun DrawScope.arcB(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float, alpha: Float, style: DrawStyle) {
    val b = EpicABoard
    drawArc(color, start, sweep, false, Offset(b.x(cx - rx), b.y(cy - ry)), Size(b.l(2f * rx), b.l(2f * ry)), artAlpha(color, alpha), style)
}

private fun DrawScope.lineB(color: Color, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, alpha: Float, cap: StrokeCap = StrokeCap.Butt) {
    drawLine(color, EpicABoard.at(x0, y0), EpicABoard.at(x1, y1), width, cap, alpha = artAlpha(color, alpha))
}

/** A soft round glow of [hue] at board ([cx], [cy]), squashed to [squash] of its height. */
private fun DrawScope.glowB(brush: Brush, cx: Float, cy: Float, radius: Float, squash: Float) {
    val center = EpicABoard.at(cx, cy)
    scale(1f, squash, center) { drawCircle(brush, EpicABoard.l(radius), center) }
}

private fun glowBrush(hue: Color, cx: Float, cy: Float, radius: Float, alpha: Float): Brush =
    Brush.radialGradient(0f to hue.copy(alpha = min(alpha, RewardArtAlpha.HIGH)), 0.45f to hue.copy(alpha = min(alpha, RewardArtAlpha.HIGH) * 0.4f), 1f to hue.copy(alpha = 0f),
        center = EpicABoard.at(cx, cy), radius = EpicABoard.l(radius))

/** Turned metal across [left]..[right]: a lit flank, a dark core and a thin rim light. */
private fun metalAcross(hue: Color, left: Float, right: Float, gain: Float = 1f): Brush = Brush.horizontalGradient(
    0f to hue.copy(alpha = 0.04f * gain), 0.2f to hue.copy(alpha = 0.16f * gain), 0.45f to hue.copy(alpha = 0.06f * gain),
    0.85f to hue.copy(alpha = 0.025f * gain), 1f to hue.copy(alpha = 0.08f * gain),
    startX = EpicABoard.x(left), endX = EpicABoard.x(right),
)

/** Light falling from ([x0], [y0]) at [from] to ([x1], [y1]) at [to]. */
private fun lightBrush(hue: Color, x0: Float, y0: Float, x1: Float, y1: Float, from: Float, to: Float): Brush =
    Brush.linearGradient(0f to hue.copy(alpha = from), 1f to hue.copy(alpha = to), start = EpicABoard.at(x0, y0), end = EpicABoard.at(x1, y1))

// Tungsten Heart.

private const val TungstenX = 186f
private const val TungstenTilt = 0.34f
private val TungstenRx = floatArrayOf(100f, 82f, 64f)
private val TungstenTop = floatArrayOf(400f, 377f, 356f)
private val TungstenDepth = floatArrayOf(25f, 23f, 21f)
private const val TungstenCollarRx = 14f
private const val TungstenCollarTop = 339f

/**
 * Tungsten Heart: three heavy weight plates stacked on a collar, seen from above at the lower right,
 * cropped by the card as if too heavy for it: turned metal flanks, a raised rim and grooved sides,
 * grip slots and a faint warm heart at the hub.
 */
internal fun DrawScope.drawTungstenHeartArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 418f - 150f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val glint = cache.stroke(b.l(2.2f), StrokeCap.Round)
    val grip = cache.stroke(b.l(3.6f), StrokeCap.Round)
    val x = TungstenX
    fadedArt(frame, x, 418f, 186f, 150f, 0.5f) {
        for (plate in 0 until 3) {
            val rx = TungstenRx[plate]
            val ry = rx * TungstenTilt
            val top = TungstenTop[plate]
            val bottom = top + TungstenDepth[plate]
            drawPath(cache.path(plate * 3) { cylinderSide(x, rx, ry, top, bottom); ovalB(x, top, rx, ry) }, Kk.Ink, alpha = Knockout)
            drawPath(cache.path(plate * 3 + 1) { cylinderSide(x, rx, ry, top, bottom) }, cache.brush(plate) { metalAcross(hue, x - rx, x + rx, 1.3f) })
            drawPath(cache.path(plate * 3 + 2) { cylinderEdges(x, rx, ry, top, bottom, groove = true) }, hue, alpha = 0.15f, style = hair)
            ovalB(cache.brush(3 + plate) { lightBrush(hue, x - rx, top - ry, x + rx * 0.6f, top + ry, 0.16f, 0.03f) }, x, top, rx, ry)
            ovalB(hue, x, top, rx, ry, 0.24f, line)
            ovalB(hue, x, top, rx * 0.84f, ry * 0.84f, 0.12f, hair)
            arcB(hue, x, top, rx, ry, 112f, 52f, 0.22f, glint)
            lineB(hue, x - rx * 0.6f, top + ry * 0.8f + 2f, x - rx * 0.6f, bottom + ry * 0.8f - 2f, b.l(1.6f), 0.16f)
        }
        // The top plate's face: a recess, two grip slots and the hub with its warm heart.
        val rx = TungstenRx[2]
        val ry = rx * TungstenTilt
        val top = TungstenTop[2]
        ovalB(Kk.Ink, x, top, rx * 0.62f, ry * 0.62f, 0.5f)
        ovalB(hue, x, top, rx * 0.62f, ry * 0.62f, 0.1f, hair)
        arcB(Kk.Ink, x, top, rx * 0.73f, ry * 0.73f, 158f, 44f, 0.85f, grip)
        arcB(Kk.Ink, x, top, rx * 0.73f, ry * 0.73f, 338f, 44f, 0.85f, grip)
        arcB(hue, x, top, rx * 0.73f, ry * 0.73f, 162f, 36f, 0.12f, hair)
        arcB(hue, x, top, rx * 0.73f, ry * 0.73f, 342f, 36f, 0.12f, hair)
        glowB(cache.brush(6) { glowBrush(hue, x, top, 40f, 0.2f) }, x, top, 40f, TungstenTilt)
        ovalB(hue, x, top, 24f, 24f * TungstenTilt, 0.16f, hair)
        // The collar through the stack, its bore dark.
        val cr = TungstenCollarRx
        val cry = cr * TungstenTilt
        val collarBottom = top
        drawPath(cache.path(9) { cylinderSide(x, cr, cry, TungstenCollarTop, collarBottom); ovalB(x, TungstenCollarTop, cr, cry) }, Kk.Ink, alpha = Knockout)
        drawPath(cache.path(10) { cylinderSide(x, cr, cry, TungstenCollarTop, collarBottom) }, cache.brush(7) { metalAcross(hue, x - cr, x + cr, 1.3f) })
        drawPath(cache.path(11) { cylinderEdges(x, cr, cry, TungstenCollarTop, collarBottom, groove = false) }, hue, alpha = 0.18f, style = hair)
        ovalB(hue, x, TungstenCollarTop, cr, cry, 0.14f)
        ovalB(Kk.Ink, x, TungstenCollarTop, cr * 0.5f, cry * 0.5f, 0.9f)
        ovalB(hue, x, TungstenCollarTop, cr, cry, 0.26f, hair)
    }
}

// Redline Governor.

private const val GovernorX = 150f
private const val GovernorY = 426f
private const val GovernorStart = 196f
private const val GovernorEnd = 344f
private const val GovernorRed = 312f
private const val GovernorNeedle = 328f
private const val GovernorSteps = 32

/** Ticks of the gauge: minor ([major] false) or major ones outside the redline, or all inside it ([red]). */
private fun Path.governorTicks(major: Boolean, red: Boolean) {
    for (index in 0..GovernorSteps) {
        val angle = GovernorStart + index * (GovernorEnd - GovernorStart) / GovernorSteps
        val isMajor = index % 4 == 0
        val inRed = angle >= GovernorRed - 0.01f
        if (inRed != red || (!red && isMajor != major)) continue
        val inner = if (isMajor) 105f else 113f
        segB(polarX(GovernorX, inner, angle), polarY(GovernorY, inner, angle), polarX(GovernorX, 122f, angle), polarY(GovernorY, 122f, angle))
    }
}

/**
 * Redline Governor: a tachometer arc rising from the bottom of the card, the needle held in the
 * redline zone (a bright band and dense ticks in the card hue, never the threat colour) with a
 * governor stop pin where the redline starts and a sweep trail behind the needle.
 */
internal fun DrawScope.drawRedlineGovernorArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 430f - 190f * 0.55f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val tick = cache.stroke(b.l(2.4f))
    val x = GovernorX
    val y = GovernorY
    fadedArt(frame, x, 430f, 210f, 190f, 0.55f) {
        ovalB(Kk.Ink, x, y, 142f, 142f, 0.7f)
        drawPath(cache.path(0) { annulusB(x, y, 30f, 100f, GovernorStart, GovernorNeedle - GovernorStart) }, cache.brush(0) {
            val s0 = GovernorStart / 360f
            val s1 = GovernorNeedle / 360f
            Brush.sweepGradient(
                0f to Clear, s0 to hue.copy(alpha = 0f), (s0 + s1) * 0.5f to hue.copy(alpha = 0.05f), s1 to hue.copy(alpha = RewardArtAlpha.HIGH),
                s1 + 0.002f to hue.copy(alpha = 0f), 1f to Clear, center = b.at(x, y),
            )
        })
        drawPath(cache.path(1) { annulusB(x, y, 102f, 124f, GovernorRed, GovernorEnd - GovernorRed) }, hue, alpha = 0.07f)
        drawPath(cache.path(2) { arcB(x, y, 134f, 134f, GovernorStart - 5f, GovernorEnd - GovernorStart + 10f, true) }, hue, alpha = RewardArtAlpha.HIGH, style = line)
        drawPath(cache.path(3) {
            arcB(x, y, 140f, 140f, GovernorStart - 3f, GovernorEnd - GovernorStart + 6f, true)
            arcB(x, y, 100f, 100f, GovernorStart, GovernorEnd - GovernorStart, true)
        }, hue, alpha = 0.1f, style = hair)
        drawPath(cache.path(4) { governorTicks(major = false, red = false) }, hue, alpha = 0.2f, style = hair)
        drawPath(cache.path(5) { governorTicks(major = true, red = false) }, hue, alpha = RewardArtAlpha.HIGH, style = tick)
        drawPath(cache.path(6) { governorTicks(major = true, red = true) }, hue, alpha = RewardArtAlpha.HIGH, style = tick)
        // The redline band outside the ticks, and the governor's stop pin where it starts.
        drawPath(cache.path(7) { annulusB(x, y, 125f, 131f, GovernorRed, GovernorEnd - GovernorRed) }, hue, alpha = RewardArtAlpha.HIGH)
        lineB(hue, polarX(x, 98f, GovernorRed), polarY(y, 98f, GovernorRed), polarX(x, 137f, GovernorRed), polarY(y, 137f, GovernorRed), b.l(3f), 0.3f)
        drawPath(cache.path(8) {
            for (index in 0..GovernorSteps step 4) {
                val angle = GovernorStart + index * (GovernorEnd - GovernorStart) / GovernorSteps
                ovalB(polarX(x, 94f, angle), polarY(y, 94f, angle), 1.6f, 1.6f)
            }
        }, hue, alpha = 0.16f)
        // The needle: a slim blade with a square tip and a counterweight, then the hub.
        val needle = cache.path(9) {
            val c = cos(GovernorNeedle * Deg)
            val s = sin(GovernorNeedle * Deg)
            moveB(x - 22f * c + 4.6f * s, y - 22f * s - 4.6f * c)
            lineB(x + 116f * c + 1.3f * s, y + 116f * s - 1.3f * c)
            lineB(x + 116f * c - 1.3f * s, y + 116f * s + 1.3f * c)
            lineB(x - 22f * c - 4.6f * s, y - 22f * s + 4.6f * c)
            close()
        }
        drawPath(needle, Kk.Ink, alpha = Knockout)
        drawPath(needle, hue, alpha = 0.3f)
        lineB(hue, x, y, polarX(x, 112f, GovernorNeedle), polarY(y, 112f, GovernorNeedle), b.hair, 0.3f)
        ovalB(Kk.Ink, x, y, 15f, 15f, 0.9f)
        ovalB(hue, x, y, 15f, 15f, 0.22f, line)
        ovalB(hue, x, y, 22f, 22f, 0.08f, hair)
        ovalB(hue, x, y, 5f, 5f, 0.26f)
    }
}

// Arrest Engine.

private const val ArrestX = 234f
private const val ArrestY = 354f
private const val ArrestR = 84f
private const val ArrestInner = 57f
private const val ArrestHat = 38f

/** The caliper body: an angular clamp over the disc's left edge (radius, angle pairs). */
private val ArrestCaliper = floatArrayOf(
    96f, 150f, 104f, 158f, 106f, 192f, 104f, 226f, 96f, 234f,
    54f, 230f, 52f, 211f, 52f, 173f, 54f, 154f,
)

/**
 * Arrest Engine: a cross-drilled, slotted brake disc cropped at the right edge, with an angular
 * caliper clamping its left side; the friction ring glows just past the pads.
 */
internal fun DrawScope.drawArrestEngineArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 372f - 140f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val slot = cache.stroke(b.l(2.4f), StrokeCap.Round)
    val edge = cache.stroke(b.line, join = StrokeJoin.Round)
    val x = ArrestX
    val y = ArrestY
    fadedArt(frame, 220f, 372f, 170f, 140f, 0.5f) {
        ovalB(Kk.Ink, x, y, ArrestR + 2f, ArrestR + 2f, Knockout)
        // The friction ring: machined metal, hottest just past the caliper.
        drawPath(cache.path(0) {
            fillType = PathFillType.EvenOdd
            ovalB(x, y, ArrestR, ArrestR)
            ovalB(x, y, ArrestInner, ArrestInner)
        }, cache.brush(0) {
            Brush.sweepGradient(
                0f to hue.copy(alpha = 0.07f), 0.12f to hue.copy(alpha = 0.11f), 0.25f to hue.copy(alpha = 0.05f), 0.4f to hue.copy(alpha = 0.09f),
                0.53f to hue.copy(alpha = 0.05f), 0.65f to hue.copy(alpha = RewardArtAlpha.HIGH), 0.76f to hue.copy(alpha = 0.12f), 0.88f to hue.copy(alpha = 0.05f),
                1f to hue.copy(alpha = 0.07f), center = b.at(x, y),
            )
        })
        drawPath(cache.path(1) {
            for (group in 0 until 20) {
                val base = group * 18f + 4f
                for (row in 0 until 3) {
                    val angle = base + row * 5f
                    val r = 63f + row * 7.5f
                    ovalB(polarX(x, r, angle), polarY(y, r, angle), 2.4f, 2.4f)
                }
            }
        }, Kk.Ink, alpha = 0.9f)
        drawPath(cache.path(2) {
            for (group in 0 until 10) {
                val angle = group * 36f + 13f
                segB(polarX(x, 61f, angle), polarY(y, 61f, angle), polarX(x, 82f, angle + 7f), polarY(y, 82f, angle + 7f))
            }
        }, Kk.Ink, alpha = 0.8f, style = slot)
        ovalB(hue, x, y, ArrestR, ArrestR, 0.22f, line)
        ovalB(hue, x, y, ArrestR - 4f, ArrestR - 4f, 0.08f, hair)
        ovalB(hue, x, y, ArrestInner, ArrestInner, 0.14f, hair)
        // The hat: lit face, five lug bolts, the bore.
        ovalB(cache.brush(1) { lightBrush(hue, x - ArrestHat, y - ArrestHat, x + ArrestHat, y + ArrestHat, 0.13f, 0.03f) }, x, y, ArrestHat, ArrestHat)
        ovalB(hue, x, y, ArrestHat, ArrestHat, 0.2f, line)
        ovalB(hue, x, y, 20f, 20f, 0.1f, hair)
        val lugs = cache.path(3) {
            for (lug in 0 until 5) {
                val angle = lug * 72f - 90f
                ovalB(polarX(x, 27f, angle), polarY(y, 27f, angle), 4.2f, 4.2f)
            }
        }
        drawPath(lugs, Kk.Ink, alpha = 0.85f)
        drawPath(lugs, hue, alpha = 0.18f, style = hair)
        ovalB(Kk.Ink, x, y, 12f, 12f, 0.9f)
        ovalB(hue, x, y, 12f, 12f, 0.2f, hair)
        // The caliper, clamping.
        val body = cache.path(4) {
            for (index in 0 until ArrestCaliper.size / 2) {
                val r = ArrestCaliper[index * 2]
                val angle = ArrestCaliper[index * 2 + 1]
                if (index == 0) moveB(polarX(x, r, angle), polarY(y, r, angle)) else lineB(polarX(x, r, angle), polarY(y, r, angle))
            }
            close()
        }
        drawPath(body, Kk.Ink, alpha = 0.92f)
        drawPath(body, cache.brush(2) { lightBrush(hue, x - 100f, y - 50f, x - 60f, y + 50f, 0.17f, 0.05f) })
        drawPath(body, hue, alpha = RewardArtAlpha.HIGH, style = edge)
        // The rotor's edge running through the caliper's slot, and the lit crown of the caliper.
        arcB(Kk.Ink, x, y, ArrestR, ArrestR, 157f, 70f, 0.9f, cache.stroke(b.l(3.4f)))
        arcB(hue, x, y, ArrestR + 2.5f, ArrestR + 2.5f, 157f, 70f, 0.18f, hair)
        drawPath(cache.path(7) {
            for (index in 0 until 5) {
                val r = ArrestCaliper[index * 2] - 2f
                val angle = ArrestCaliper[index * 2 + 1] + if (index == 0) 2f else if (index == 4) -2f else 0f
                if (index == 0) moveB(polarX(x, r, angle), polarY(y, r, angle)) else lineB(polarX(x, r, angle), polarY(y, r, angle))
            }
        }, hue, alpha = 0.3f, style = hair)
        drawPath(cache.path(5) {
            ovalB(polarX(x, 76f, 176f), polarY(y, 76f, 176f), 8f, 8f)
            ovalB(polarX(x, 76f, 208f), polarY(y, 76f, 208f), 8f, 8f)
            arcB(x, y, 62f, 62f, 160f, 66f, true)
            arcB(x, y, 98f, 98f, 162f, 60f, true)
        }, hue, alpha = 0.14f, style = hair)
        drawPath(cache.path(6) {
            ovalB(polarX(x, 88f, 160f), polarY(y, 88f, 160f), 3.4f, 3.4f)
            ovalB(polarX(x, 88f, 224f), polarY(y, 88f, 224f), 3.4f, 3.4f)
        }, hue, alpha = RewardArtAlpha.HIGH)
    }
}

// Overcharged Capacitor.

private const val CapAxis = 372f
private const val CapPlateTop = 312f
private const val CapPlateBottom = 436f
private const val CapPlateDepth = 28f
private const val CapPlateRise = 18f
private val CapPlateX = floatArrayOf(74f, 190f)
private const val CapCoilLeft = 108f
private const val CapCoilTurns = 7
private const val CapCoilPitch = 10.6f
private const val CapCoilRadius = 24f
private const val CapCoilLean = 4f
private const val CapCoilSamples = 10

/** The coil's front ([front]) or back half-turns as polylines round the rod. */
private fun Path.capacitorCoil(front: Boolean) {
    for (turn in 0 until CapCoilTurns) {
        val from = turn * 2f * PI.toFloat() + if (front) 0f else PI.toFloat()
        for (sample in 0..CapCoilSamples) {
            val theta = from + sample * PI.toFloat() / CapCoilSamples
            val px = CapCoilLeft + CapCoilPitch * theta / (2f * PI.toFloat()) + CapCoilLean * sin(theta)
            val py = CapAxis - CapCoilRadius * cos(theta)
            if (sample == 0) moveB(px, py) else lineB(px, py)
        }
    }
}

private fun Path.capacitorPlate(a: Float) {
    moveB(a, CapPlateTop)
    lineB(a + CapPlateDepth, CapPlateTop - CapPlateRise)
    lineB(a + CapPlateDepth, CapPlateBottom - CapPlateRise)
    lineB(a, CapPlateBottom)
    close()
}

/**
 * Overcharged Capacitor: two charged plates seen at an angle with the field running straight
 * between them and fringing over their tops, a core rod through both and a tight copper-style coil
 * wound round it, glowing with more charge than it can hold.
 */
internal fun DrawScope.drawOverchargedCapacitorArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 372f - 118f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val wire = cache.stroke(b.l(1.8f), StrokeCap.Round, StrokeJoin.Round)
    val gap = cache.stroke(b.l(3.8f), StrokeCap.Round, StrokeJoin.Round)
    val mark = cache.stroke(b.l(1.7f))
    fadedArt(frame, 146f, 372f, 158f, 118f, 0.5f) {
        glowB(cache.brush(0) { glowBrush(hue, 146f, CapAxis, 70f, 0.24f) }, 146f, CapAxis, 70f, 1f)
        // The field: straight between the plates, fringing over and under them.
        drawPath(cache.path(0) {
            for (row in 0 until 3) {
                segB(104f, 320f + row * 11f, 188f, 320f + row * 11f)
                segB(104f, 402f + row * 11f, 188f, 402f + row * 11f)
            }
            moveB(96f, 296f)
            quadB(146f, 272f, 202f, 296f)
            moveB(90f, 302f)
            quadB(146f, 256f, 208f, 302f)
            moveB(98f, 432f)
            quadB(146f, 446f, 194f, 432f)
        }, hue, alpha = 0.15f, style = hair)
        drawPath(cache.path(1) { capacitorCoil(front = false) }, hue, alpha = 0.11f, style = hair)
        // The core rod with its terminals.
        val rod = cache.path(2) {
            addRect(boardRect(44f, CapAxis - 8f, 246f, CapAxis + 8f))
            ovalB(44f, CapAxis, 4f, 11f)
            ovalB(246f, CapAxis, 4f, 11f)
        }
        drawPath(rod, Kk.Ink, alpha = Knockout)
        drawPath(rod, cache.brush(1) { lightBrush(hue, 0f, CapAxis - 10f, 0f, CapAxis + 10f, 0.16f, 0.03f) })
        drawPath(cache.path(3) {
            segB(44f, CapAxis - 8f, 246f, CapAxis - 8f)
            segB(44f, CapAxis + 8f, 246f, CapAxis + 8f)
            ovalB(44f, CapAxis, 4f, 11f)
            ovalB(246f, CapAxis, 4f, 11f)
            ovalB(52f, CapAxis, 3f, 13f)
            ovalB(238f, CapAxis, 3f, 13f)
        }, hue, alpha = 0.18f, style = hair)
        val coil = cache.path(4) { capacitorCoil(front = true) }
        drawPath(coil, Kk.Ink, alpha = 0.8f, style = gap)
        drawPath(coil, hue, alpha = 0.3f, style = wire)
        // The plates, each a lit face and a bright leading edge.
        for (plate in 0 until 2) {
            val a = CapPlateX[plate]
            val face = cache.path(5 + plate) { capacitorPlate(a) }
            drawPath(face, Kk.Ink, alpha = Knockout)
            drawPath(face, cache.brush(2) { lightBrush(hue, 0f, CapPlateTop - CapPlateRise, 0f, CapPlateBottom, 0.17f, 0.03f) })
            drawPath(face, hue, alpha = 0.22f, style = line)
            lineB(hue, a, CapPlateTop, a, CapPlateBottom, b.l(2.6f), 0.2f)
            lineB(hue, a + CapPlateDepth, CapPlateTop - CapPlateRise, a, CapPlateTop, b.l(1.6f), 0.3f)
        }
        drawPath(cache.path(7) {
            for (plate in 0 until 2) {
                val a = CapPlateX[plate]
                for (rib in 1..3) {
                    val yy = CapPlateTop + rib * 31f
                    segB(a + 3f, yy - 2f, a + CapPlateDepth - 3f, yy - CapPlateRise + 2f)
                }
            }
        }, hue, alpha = 0.08f, style = hair)
        // The charge on the plates: plus marks down the left plate, minus marks down the right.
        drawPath(cache.path(8) {
            for (plate in 0 until 2) {
                val a = CapPlateX[plate]
                for (row in 0 until 5) {
                    val v = 0.14f + row * 0.18f
                    val cx = a + CapPlateDepth * 0.5f
                    val cy = CapPlateTop + (CapPlateBottom - CapPlateTop) * v - CapPlateRise * 0.5f
                    segB(cx - 3.5f, cy + 1.2f, cx + 3.5f, cy - 1.2f)
                    if (plate == 0) segB(cx, cy - 3.6f, cx, cy + 3.6f)
                }
            }
        }, hue, alpha = RewardArtAlpha.HIGH, style = mark)
        // More charge than it can hold: the light spills over the coil.
        glowB(cache.brush(3) { glowBrush(hue, 146f, CapAxis, 46f, 0.14f) }, 146f, CapAxis, 46f, 0.8f)
    }
}

// Glass Edge.

/** The blade's spine and edge as quadratic curves (board x, y of the start at the guard, control and end). */
private val GlassSpine = floatArrayOf(80f, 424f, 190f, 384f, 334f, 346f)
private val GlassEdge = floatArrayOf(52f, 392f, 150f, 330f, 334f, 300f)
private const val GlassFlat = 0.08f
private const val GlassRidge = 0.58f
private const val GlassBevel = 0.9f
private const val GlassFacets = 8
private const val GlassBreak = 0.62f

/** The guard at the blade's root: its centre and the blade's direction there (unit). */
private const val GlassGuardX = 66f
private const val GlassGuardY = 408f
private const val GlassDirX = 0.898f
private const val GlassDirY = -0.441f

private fun quadAt(curve: FloatArray, axis: Int, t: Float): Float {
    val m = 1f - t
    return m * m * curve[axis] + 2f * m * t * curve[2 + axis] + t * t * curve[4 + axis]
}

/** Board x and y of the blade point at [t] along it (0 at the guard, 1 past the card) and [s] across it (0 spine, 1 edge). */
private fun glassX(t: Float, s: Float): Float = quadAt(GlassSpine, 0, t) + s * (quadAt(GlassEdge, 0, t) - quadAt(GlassSpine, 0, t))
private fun glassY(t: Float, s: Float): Float = quadAt(GlassSpine, 1, t) + s * (quadAt(GlassEdge, 1, t) - quadAt(GlassSpine, 1, t))

private fun Path.glassMove(t: Float, s: Float) = moveB(glassX(t, s), glassY(t, s))
private fun Path.glassLine(t: Float, s: Float) = lineB(glassX(t, s), glassY(t, s))

/** A run along the blade at [s] from [t0] to [t1], continuing the current contour unless [move]. */
private fun Path.glassRun(s: Float, t0: Float, t1: Float, move: Boolean = true, steps: Int = 24) {
    for (step in 0..steps) {
        val t = t0 + (t1 - t0) * step / steps
        if (step == 0 && move) glassMove(t, s) else glassLine(t, s)
    }
}

/** The band of the blade between [s0] and [s1], closed. */
private fun Path.glassBand(s0: Float, s1: Float) {
    glassRun(s0, 0f, 1.04f)
    glassRun(s1, 1.04f, 0f, move = false)
    close()
}

/** Where facet boundary [index] of the lower ([upper] false) or upper row meets its lower and upper side. */
private fun glassFacetT(frame: RewardArtFrame, index: Int, upper: Boolean, top: Boolean): Float {
    val base = index * 1.06f / GlassFacets + (frame.random(index * 2 + if (upper) 1 else 0) - 0.5f) * 0.35f / GlassFacets
    val shift = if (upper) 0.5f / GlassFacets + 0.045f else 0f
    return (base + shift + if (top) (if (upper) 0.025f else 0.045f) else 0f).coerceIn(0f, 1.04f)
}

/** The long facets of one row ([upper]) whose seeded shade class is [shade] (0..2). */
private fun Path.glassFacets(frame: RewardArtFrame, upper: Boolean, shade: Int) {
    val s0 = if (upper) GlassRidge else GlassFlat
    val s1 = if (upper) GlassBevel else GlassRidge
    for (index in -1 until GlassFacets) {
        val first = index.coerceAtLeast(0)
        if ((index + 1 + (if (upper) 1 else 0) + (frame.random(150 + index + if (upper) 20 else 0) * 2.2f).toInt()) % 3 != shade) continue
        val a0 = if (index < 0) 0f else glassFacetT(frame, first, upper, false)
        val a1 = if (index < 0) 0f else glassFacetT(frame, first, upper, true)
        val b0 = glassFacetT(frame, index + 1, upper, false)
        val b1 = glassFacetT(frame, index + 1, upper, true)
        glassMove(a0, s0)
        glassLine(b0, s0)
        glassLine(b1, s1)
        glassLine(a1, s1)
        close()
    }
}

/** The fracture across the blade at [GlassBreak], from the edge to the spine, with its branches. */
private fun Path.glassFracture() {
    val f = GlassBreak
    glassMove(f, 1f)
    glassLine(f - 0.014f, 0.8f)
    glassLine(f + 0.008f, 0.56f)
    glassLine(f - 0.01f, 0.3f)
    glassLine(f + 0.006f, 0f)
    glassMove(f - 0.014f, 0.8f)
    glassLine(f - 0.05f, 0.62f)
    glassLine(f - 0.085f, 0.46f)
    glassMove(f + 0.008f, 0.56f)
    glassLine(f + 0.045f, 0.42f)
    glassLine(f + 0.07f, 0.24f)
    glassMove(f - 0.01f, 0.3f)
    glassLine(f - 0.04f, 0.14f)
}

/** A point of the guard and grip frame: [along] the blade from the guard's centre, [across] it. */
private fun glassHiltX(along: Float, across: Float): Float = GlassGuardX + along * GlassDirX - across * GlassDirY
private fun glassHiltY(along: Float, across: Float): Float = GlassGuardY + along * GlassDirY + across * GlassDirX

private fun Path.glassHilt(along: Float, across: Float, first: Boolean) =
    if (first) moveB(glassHiltX(along, across), glassHiltY(along, across)) else lineB(glassHiltX(along, across), glassHiltY(along, across))

/** The guard: an angular bar across the blade's root, its ends cut. */
private fun Path.glassGuard() {
    glassHilt(-5f, -27f, true)
    glassHilt(-1f, -33f, false)
    glassHilt(5f, -31f, false)
    glassHilt(5f, 31f, false)
    glassHilt(-1f, 33f, false)
    glassHilt(-5f, 27f, false)
    close()
}

/**
 * Glass Edge: a glass sword rising from its guard at the lower left, the curved blade sweeping
 * across the face past the card's edge: two rows of long crystal facets, a keen bevelled edge with
 * a glint, and one fracture running clean through it, its broken faces catching the light.
 */
internal fun DrawScope.drawGlassEdgeArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 392f - 150f * 0.58f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line, join = StrokeJoin.Round)
    val keen = cache.stroke(b.l(1.7f), StrokeCap.Round)
    val crack = cache.stroke(b.hair, StrokeCap.Round, StrokeJoin.Round)
    val gap = cache.stroke(b.l(3f), StrokeCap.Round, StrokeJoin.Round)
    fadedArt(frame, 180f, 392f, 210f, 150f, 0.58f) {
        // The grip below the guard, wrapped.
        val grip = cache.path(13) {
            glassHilt(-4f, -6f, true)
            glassHilt(-40f, -6f, false)
            glassHilt(-40f, 6f, false)
            glassHilt(-4f, 6f, false)
            close()
        }
        drawPath(grip, Kk.Ink, alpha = 0.92f)
        drawPath(grip, hue, alpha = 0.05f)
        drawPath(grip, hue, alpha = 0.16f, style = hair)
        drawPath(cache.path(14) {
            for (wrap in 0 until 5) {
                val along = -10f - wrap * 6.5f
                glassHilt(along + 2f, -6f, true)
                glassHilt(along - 2f, 6f, false)
            }
        }, hue, alpha = 0.14f, style = hair)
        // The blade.
        val blade = cache.path(0) { glassBand(0f, 1f) }
        drawPath(blade, Kk.Ink, alpha = 0.9f)
        drawPath(blade, cache.brush(0) {
            Brush.linearGradient(0f to hue.copy(alpha = 0.03f), 0.6f to hue.copy(alpha = 0.07f), 1f to hue.copy(alpha = 0.03f),
                start = b.at(glassX(0.5f, 0f), glassY(0.5f, 0f)), end = b.at(glassX(0.5f, 1f), glassY(0.5f, 1f)))
        })
        for (row in 0 until 2) {
            val upper = row == 1
            drawPath(cache.path(1 + row * 3) { glassFacets(frame, upper, 0) }, hue, alpha = 0.03f)
            drawPath(cache.path(2 + row * 3) { glassFacets(frame, upper, 1) }, hue, alpha = 0.07f)
            drawPath(cache.path(3 + row * 3) { glassFacets(frame, upper, 2) }, hue, alpha = 0.125f)
        }
        drawPath(cache.path(7) {
            for (row in 0 until 2) {
                val upper = row == 1
                val s0 = if (upper) GlassRidge else GlassFlat
                val s1 = if (upper) GlassBevel else GlassRidge
                for (index in 0..GlassFacets) {
                    glassMove(glassFacetT(frame, index, upper, false), s0)
                    glassLine(glassFacetT(frame, index, upper, true), s1)
                }
            }
            glassRun(GlassFlat, 0f, 1.04f)
            glassRun(GlassRidge, 0f, 1.04f)
        }, hue, alpha = 0.08f, style = hair)
        // The edge bevel, bright toward the edge, and the spine.
        drawPath(cache.path(8) { glassBand(GlassBevel, 1f) }, cache.brush(1) {
            Brush.linearGradient(0f to hue.copy(alpha = 0.06f), 1f to hue.copy(alpha = 0.22f),
                start = b.at(glassX(0.55f, GlassBevel), glassY(0.55f, GlassBevel)), end = b.at(glassX(0.55f, 1f), glassY(0.55f, 1f)))
        })
        drawPath(cache.path(9) { glassRun(0f, 0f, 1.04f) }, hue, alpha = 0.18f, style = hair)
        drawPath(cache.path(10) { glassRun(GlassBevel, 0f, 1.04f) }, hue, alpha = 0.14f, style = hair)
        // The keen edge with its glint.
        drawPath(cache.path(11) { glassRun(1f, 0f, 1.04f) }, cache.brush(2) {
            Brush.linearGradient(
                0f to hue.copy(alpha = 0.1f), 0.3f to hue.copy(alpha = 0.2f), 0.46f to hue.copy(alpha = RewardArtAlpha.HIGH), 0.62f to hue.copy(alpha = 0.18f),
                1f to hue.copy(alpha = 0.08f),
                start = b.at(glassX(0f, 1f), glassY(0f, 1f)), end = b.at(glassX(1f, 1f), glassY(1f, 1f)),
            )
        }, style = keen)
        // The guard over the blade's root.
        val guard = cache.path(12) { glassGuard() }
        drawPath(guard, Kk.Ink, alpha = 0.94f)
        drawPath(guard, cache.brush(3) { lightBrush(hue, glassHiltX(0f, -33f), glassHiltY(0f, -33f), glassHiltX(0f, 33f), glassHiltY(0f, 33f), 0.18f, 0.05f) })
        drawPath(guard, hue, alpha = RewardArtAlpha.HIGH, style = line)
        // The fracture: a dark gap with its broken faces lit, and two flakes off the edge.
        val fracture = cache.path(15) { glassFracture() }
        drawPath(fracture, Kk.Ink, alpha = 0.85f, style = gap)
        drawPath(fracture, hue, alpha = RewardArtAlpha.HIGH, style = crack)
        drawPath(cache.path(16) {
            val f = GlassBreak
            glassMove(f + 0.02f, 1.18f)
            glassLine(f + 0.034f, 1.16f)
            glassLine(f + 0.036f, 1.27f)
            glassLine(f + 0.022f, 1.29f)
            close()
            glassMove(f + 0.055f, 1.4f)
            glassLine(f + 0.064f, 1.39f)
            glassLine(f + 0.066f, 1.47f)
            glassLine(f + 0.056f, 1.48f)
            close()
        }, hue, alpha = 0.22f)
    }
}

// Last Stand.

private const val StandGround = 428f
private const val StandLean = 6f
private const val StandCut = 12f
private val StandX = floatArrayOf(32f, 76f, 123f, 177f, 221f)
private val StandWidth = floatArrayOf(48f, 48f, 54f, 48f, 48f)
private val StandHeight = floatArrayOf(96f, 106f, 120f, 106f, 96f)

/** Back to front: the outer shields, the inner ones, the cracked centre last. */
private val StandOrder = intArrayOf(0, 4, 1, 3, 2)

private fun standX(index: Int, u: Float, v: Float): Float = StandX[index] + u + StandLean * v / StandHeight[index]
private fun standY(v: Float): Float = StandGround - v

/** Shield [index]'s outline inset by [inset] (local u across, v up); the centre one has its top corner broken off. */
private fun Path.standShield(index: Int, inset: Float) {
    val w = StandWidth[index]
    val h = StandHeight[index]
    val c = StandCut
    val d = inset
    fun at(u: Float, v: Float, first: Boolean = false) = if (first) moveB(standX(index, u, v), standY(v)) else lineB(standX(index, u, v), standY(v))
    at(d + 3f, d, first = true)
    at(w - d - 3f, d)
    at(w - d, d + 3f)
    if (index == 2 && d == 0f) {
        at(w, h - c - 12f)
        at(w - 7f, h - c - 6f)
        at(w - 5f, h - c + 1f)
        at(w - 13f, h - 5f)
        at(w - c - 10f, h)
    } else {
        at(w - d, h - c - d * 0.4f)
        at(w - c - d * 0.4f, h - d)
    }
    at(c + d * 0.4f, h - d)
    at(d, h - c - d * 0.4f)
    at(d, d + 3f)
    close()
}

/**
 * Last Stand: a wall of five tower shields standing on the ground line, the tall one in the
 * middle in front, struck, cracked from a dent and missing a corner, still standing.
 */
internal fun DrawScope.drawLastStandArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 410f - 140f * 0.55f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line, join = StrokeJoin.Round)
    val crack = cache.stroke(b.l(1.3f), StrokeCap.Round, StrokeJoin.Round)
    val light = cache.brush(0) { lightBrush(hue, 0f, StandGround - 120f, 0f, StandGround, 0.12f, 0.025f) }
    val struck = cache.brush(1) { lightBrush(hue, 0f, StandGround - 120f, 0f, StandGround, 0.18f, 0.035f) }
    fadedArt(frame, 150f, 410f, 186f, 140f, 0.55f) {
        drawRect(Kk.Ink, b.at(24f, StandGround), Size(b.l(252f), b.l(12f)), alpha = 0.5f)
        lineB(hue, 22f, StandGround, 278f, StandGround, b.hair, 0.14f)
        for (order in 0 until 5) {
            val index = StandOrder[order]
            val shield = cache.path(index * 3) { standShield(index, 0f) }
            drawPath(shield, Kk.Ink, alpha = 0.92f)
            val outer = index == 0 || index == 4
            if (index == 2) drawPath(shield, struck) else drawPath(shield, light, alpha = if (outer) 0.6f else 0.85f)
            drawPath(shield, hue, alpha = if (index == 2) RewardArtAlpha.HIGH else if (outer) 0.15f else 0.19f, style = line)
            drawPath(cache.path(index * 3 + 1) {
                standShield(index, 5f)
                val w = StandWidth[index]
                val h = StandHeight[index]
                segB(standX(index, w * 0.5f, 9f), standY(9f), standX(index, w * 0.5f, h - 9f), standY(h - 9f))
                segB(standX(index, 5f, h * 0.62f), standY(h * 0.62f), standX(index, w - 5f, h * 0.62f), standY(h * 0.62f))
                for (corner in 0 until 4) {
                    val u = if (corner % 2 == 0) 9f else w - 9f
                    val v = if (corner < 2) 11f else h - 15f
                    ovalB(standX(index, u, v), standY(v), 1.7f, 1.7f)
                }
            }, hue, alpha = 0.12f, style = hair)
        }
        // The strike on the centre shield: a dent and cracks running out of it.
        val impactU = 23f
        val impactV = 70f
        val ix = standX(2, impactU, impactV)
        val iy = standY(impactV)
        clipPath(cache.path(2 * 3) { standShield(2, 0f) }) {
            val cracks = cache.path(15) {
                for (ray in 0 until 7) {
                    var angle = ray * 51f + 14f + (frame.random(300 + ray) - 0.5f) * 24f
                    var px = ix
                    var py = iy
                    moveB(px, py)
                    for (step in 0 until 3) {
                        val length = (9f + step * 8f) * (0.8f + frame.random(320 + ray * 3 + step) * 0.6f)
                        angle += (frame.random(360 + ray * 3 + step) - 0.5f) * 34f
                        px += length * cos(angle * Deg)
                        py += length * sin(angle * Deg)
                        lineB(px, py)
                        if (step == 1 && ray % 3 == 0) {
                            val side = angle + 38f
                            lineB(px + 12f * cos(side * Deg), py + 12f * sin(side * Deg))
                            moveB(px, py)
                        }
                    }
                }
            }
            drawPath(cracks, Kk.Ink, alpha = 0.8f, style = cache.stroke(b.l(3f), StrokeCap.Round, StrokeJoin.Round))
            drawPath(cracks, hue, alpha = RewardArtAlpha.HIGH, style = crack)
        }
        ovalB(Kk.Ink, ix, iy, 5f, 5f, 0.7f)
        ovalB(hue, ix, iy, 5f, 5f, 0.2f, hair)
        ovalB(hue, ix, iy, 9.5f, 9.5f, 0.1f, hair)
        // The broken-off corner at the foot of the wall.
        drawPath(cache.path(16) {
            moveB(182f, StandGround - 1f)
            lineB(189f, StandGround - 7f)
            lineB(195f, StandGround - 4f)
            lineB(193f, StandGround - 1f)
            close()
            moveB(199f, StandGround - 1f)
            lineB(203f, StandGround - 4f)
            lineB(207f, StandGround - 1f)
            close()
        }, hue, alpha = 0.18f)
    }
}

// Redline Reactor.

private const val ReactorX = 176f
private const val ReactorY = 398f
private const val ReactorRods = 8
private const val ReactorRodOrbit = 50f
private const val ReactorRod = 9f
private const val ReactorVents = 12

/**
 * Redline Reactor: a reactor core seen from straight above, as a schematic of its vessel: the heavy
 * containment ring with coolant vents cut into it, vent ticks round it and the hot redline arc, an
 * inner shield ring with its dashed track, a ring of eight fuel rods seen end-on round the bright
 * core disc, and control spokes between the rods. Round and flat, never a stack.
 */
internal fun DrawScope.drawRedlineReactorArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 404f - 150f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val hot = cache.stroke(b.l(3.4f), StrokeCap.Round)
    val vent = cache.stroke(b.l(4.2f), StrokeCap.Round)
    val x = ReactorX
    val y = ReactorY
    fadedArt(frame, x, 404f, 190f, 150f, 0.5f) {
        ovalB(Kk.Ink, x, y, 114f, 114f, Knockout)
        // Containment: a heavy ring lit from the upper left, its rims, coolant vents cut into it.
        drawPath(cache.path(0) {
            fillType = PathFillType.EvenOdd
            ovalB(x, y, 104f, 104f)
            ovalB(x, y, 90f, 90f)
        }, cache.brush(0) { lightBrush(hue, x - 104f, y - 104f, x + 72f, y + 72f, 0.16f, 0.03f) })
        ovalB(hue, x, y, 104f, 104f, 0.2f, line)
        ovalB(hue, x, y, 90f, 90f, 0.12f, hair)
        for (slot in 0 until ReactorVents) arcB(Kk.Ink, x, y, 97f, 97f, slot * 30f + 8f, 14f, 0.9f, vent)
        drawPath(cache.path(1) {
            for (index in 0 until 48) {
                val a = index * 7.5f
                val outer = if (index % 4 == 0) 117f else 111f
                segB(polarX(x, 107f, a), polarY(y, 107f, a), polarX(x, outer, a), polarY(y, outer, a))
            }
        }, hue, alpha = 0.14f, style = hair)
        // The redline: the hot sector of the containment ring, upper right.
        arcB(hue, x, y, 104f, 104f, 290f, 58f, RewardArtAlpha.HIGH, hot)
        // The shield ring and its dashed inner track.
        ovalB(hue, x, y, 74f, 74f, 0.16f, line)
        drawPath(cache.path(2) { for (index in 0 until 36) arcB(x, y, 67f, 67f, index * 10f, 5f, true) }, hue, alpha = 0.12f, style = hair)
        // Control spokes between the rods.
        drawPath(cache.path(3) {
            for (index in 0 until ReactorRods) {
                val a = index * 45f + 22.5f
                segB(polarX(x, 30f, a), polarY(y, 30f, a), polarX(x, 66f, a), polarY(y, 66f, a))
            }
        }, hue, alpha = 0.1f, style = hair)
        // The fuel rods end-on: dark wells, a lit rim and a hot pellet in each.
        val rods = cache.path(4) {
            for (index in 0 until ReactorRods) {
                val a = index * 45f
                ovalB(polarX(x, ReactorRodOrbit, a), polarY(y, ReactorRodOrbit, a), ReactorRod, ReactorRod)
            }
        }
        drawPath(rods, Kk.Ink, alpha = 0.9f)
        drawPath(rods, hue, alpha = 0.2f, style = line)
        drawPath(cache.path(5) {
            for (index in 0 until ReactorRods) {
                val a = index * 45f
                ovalB(polarX(x, ReactorRodOrbit, a), polarY(y, ReactorRodOrbit, a), 3.4f, 3.4f)
            }
        }, hue, alpha = 0.2f)
        // The core: a bright disc in its own glow.
        glowB(cache.brush(1) { glowBrush(hue, x, y, 62f, 0.22f) }, x, y, 62f, 1f)
        ovalB(hue, x, y, 24f, 24f, 0.14f)
        ovalB(hue, x, y, 24f, 24f, 0.22f, line)
        ovalB(hue, x, y, 11f, 11f, 0.22f)
    }
}

// Domino Protocol.

private const val DominoGround = 422f
private const val DominoW = 26f
private const val DominoH = 54f
private const val DominoDepthX = 6f
private const val DominoDepthY = -5f
private const val DominoTiles = 6
private val DominoAngles = floatArrayOf(64f, 47f, 31f, 16f, 0f, 0f)

/** Pips of each tile: the falling one shows two and two, the five more each show five over another value. */
private val DominoPips = intArrayOf(2, 2, 5, 1, 5, 3, 5, 4, 5, 6, 5, 2)

/** Pivot x (each tile turns about its bottom right corner) of every tile, resting on its neighbour. */
private fun dominoPivots(into: FloatArray) {
    var pivot = 0f
    into[0] = 0f
    for (index in 0 until DominoTiles - 1) {
        val a = DominoAngles[index] * Deg
        val next = DominoAngles[index + 1] * Deg
        pivot = if (DominoAngles[index] == 0f) {
            pivot + DominoW + 16f
        } else {
            val tipX = pivot + DominoH * sin(a)
            val tipY = DominoGround - DominoH * cos(a)
            val along = (DominoGround - DominoW * sin(next) - tipY) / cos(next)
            tipX + DominoW * cos(next) - along * sin(next)
        }
        into[index + 1] = pivot
    }
    // Centre the row: from the first tile's lifted back corner to the last tile's depth.
    val left = into[0] - DominoW * cos(DominoAngles[0] * Deg)
    val right = into[DominoTiles - 1] + DominoDepthX
    val shift = 150f - (left + right) * 0.5f
    for (index in 0 until DominoTiles) into[index] += shift
}

/** Board point of tile [index] at local ([u] across from the back edge, [v] up from the base). */
private fun dominoX(pivots: FloatArray, index: Int, u: Float, v: Float): Float {
    val a = DominoAngles[index] * Deg
    return pivots[index] + (u - DominoW) * cos(a) + v * sin(a)
}

private fun dominoY(index: Int, u: Float, v: Float): Float {
    val a = DominoAngles[index] * Deg
    return DominoGround + (u - DominoW) * sin(a) - v * cos(a)
}

private fun Path.dominoFace(pivots: FloatArray, index: Int, dx: Float, dy: Float) {
    moveB(dominoX(pivots, index, 0f, 0f) + dx, dominoY(index, 0f, 0f) + dy)
    lineB(dominoX(pivots, index, DominoW, 0f) + dx, dominoY(index, DominoW, 0f) + dy)
    lineB(dominoX(pivots, index, DominoW, DominoH) + dx, dominoY(index, DominoW, DominoH) + dy)
    lineB(dominoX(pivots, index, 0f, DominoH) + dx, dominoY(index, 0f, DominoH) + dy)
    close()
}

/** The tile's whole box (its face and the face moved back by the depth) as one outline. */
private fun Path.dominoBody(pivots: FloatArray, index: Int) {
    val xs = FloatArray(8)
    val ys = FloatArray(8)
    for (corner in 0 until 4) {
        val u = if (corner == 1 || corner == 2) DominoW else 0f
        val v = if (corner >= 2) DominoH else 0f
        xs[corner] = dominoX(pivots, index, u, v)
        ys[corner] = dominoY(index, u, v)
        xs[corner + 4] = xs[corner] + DominoDepthX
        ys[corner + 4] = ys[corner] + DominoDepthY
    }
    hullB(xs, ys)
}

/** The convex hull of the board points as one closed contour (gift wrapping, at build time only). */
private fun Path.hullB(xs: FloatArray, ys: FloatArray) {
    val count = xs.size
    var start = 0
    for (index in 1 until count) if (xs[index] < xs[start] || (xs[index] == xs[start] && ys[index] < ys[start])) start = index
    var current = start
    var steps = 0
    do {
        if (steps == 0) moveB(xs[current], ys[current]) else lineB(xs[current], ys[current])
        var next = (current + 1) % count
        for (index in 0 until count) {
            val cross = (xs[next] - xs[current]) * (ys[index] - ys[current]) - (ys[next] - ys[current]) * (xs[index] - xs[current])
            if (cross < 0f) next = index
        }
        current = next
        steps++
    } while (current != start && steps <= count)
    close()
}

private val PipU = floatArrayOf(0.28f, 0.5f, 0.72f)
private val PipV = floatArrayOf(0.25f, 0.5f, 0.75f)

/** Grid cells (column, row) of the pips of [value] on one half of a tile. */
private fun pipCells(value: Int): Int = when (value) {
    1 -> 0b000_010_000
    2 -> 0b100_000_001
    3 -> 0b100_010_001
    4 -> 0b101_000_101
    5 -> 0b101_010_101
    else -> 0b101_101_101
}

private fun Path.dominoPips(pivots: FloatArray) {
    for (index in 0 until DominoTiles) {
        for (half in 0 until 2) {
            val cells = pipCells(DominoPips[index * 2 + half])
            for (cell in 0 until 9) {
                if (cells and (1 shl (8 - cell)) == 0) continue
                val u = DominoW * PipU[cell % 3]
                val v = DominoH * 0.5f * (half + PipV[2 - cell / 3])
                ovalB(dominoX(pivots, index, u, v), dominoY(index, u, v), 2.3f, 2.3f)
            }
        }
    }
}

/**
 * Domino Protocol: six tiles on a ground line, the first one falling onto the next and the fall
 * running down the row, each resting on its neighbour, the first tile's swing traced as a dashed
 * arc: the falling one shows two and two, the five more each show five.
 */
internal fun DrawScope.drawDominoProtocolArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 404f - 128f * 0.55f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line, join = StrokeJoin.Round)
    val dash = cache.stroke(b.l(1.8f), StrokeCap.Round)
    val pivots = cache.floats(0, DominoTiles) { dominoPivots(it) }
    fadedArt(frame, 150f, 404f, 186f, 128f, 0.55f) {
        drawRect(Kk.Ink, b.at(20f, DominoGround), Size(b.l(260f), b.l(10f)), alpha = 0.5f)
        lineB(hue, 22f, DominoGround, 278f, DominoGround, b.hair, 0.14f)
        drawPath(cache.path(0) {
            val pivot = pivots[0]
            for (index in 0 until 7) {
                val start = 270f + index * 9f
                arcB(pivot, DominoGround, DominoH, DominoH, start, 5f, true)
            }
        }, hue, alpha = 0.2f, style = dash)
        for (index in 0 until DominoTiles) {
            val body = cache.path(1 + index * 2) { dominoBody(pivots, index) }
            val face = cache.path(2 + index * 2) { dominoFace(pivots, index, 0f, 0f) }
            drawPath(body, Kk.Ink, alpha = 0.92f)
            drawPath(body, hue, alpha = 0.05f)
            drawPath(face, Kk.Ink, alpha = 0.9f)
            drawPath(face, cache.brush(index) {
                lightBrush(hue, dominoX(pivots, index, 0f, DominoH), dominoY(index, 0f, DominoH), dominoX(pivots, index, DominoW, 0f), dominoY(index, DominoW, 0f),
                    if (index == 0) 0.18f else 0.14f, 0.035f)
            })
            drawPath(face, hue, alpha = if (index == 0) 0.3f else 0.22f, style = line)
        }
        drawPath(cache.path(14) {
            for (index in 0 until DominoTiles) {
                segB(dominoX(pivots, index, 4f, DominoH * 0.5f), dominoY(index, 4f, DominoH * 0.5f),
                    dominoX(pivots, index, DominoW - 4f, DominoH * 0.5f), dominoY(index, DominoW - 4f, DominoH * 0.5f))
            }
        }, hue, alpha = 0.16f, style = hair)
        drawPath(cache.path(15) { dominoPips(pivots) }, hue, alpha = 0.3f)
    }
}

// Gambler's Ledger.

private const val LedgerPivotX = 150f
private const val LedgerPivotY = 486f
private const val LedgerCardW = 54f
private const val LedgerCardH = 80f
private const val LedgerCardLean = 4f
private const val LedgerCards = 5
private val LedgerAngles = floatArrayOf(-33f, -17f, -1f, 15f, 33f)
private val LedgerReach = floatArrayOf(112f, 112f, 112f, 112f, 128f)

/** Board x and y of the point ([u], [v]) (from the card's centre, v down) of fanned card [index]. */
private fun ledgerX(index: Int, u: Float, v: Float): Float {
    val a = LedgerAngles[index] * Deg
    val cx = LedgerPivotX + LedgerReach[index] * sin(a)
    val lean = LedgerCardLean * (0.5f - v / LedgerCardH)
    return cx + (u + lean) * cos(a) - v * sin(a)
}

private fun ledgerY(index: Int, u: Float, v: Float): Float {
    val a = LedgerAngles[index] * Deg
    val cy = LedgerPivotY - LedgerReach[index] * cos(a)
    val lean = LedgerCardLean * (0.5f - v / LedgerCardH)
    return cy + (u + lean) * sin(a) + v * cos(a)
}

private fun Path.ledgerQuad(index: Int, u0: Float, v0: Float, u1: Float, v1: Float) {
    moveB(ledgerX(index, u0, v0), ledgerY(index, u0, v0))
    lineB(ledgerX(index, u1, v0), ledgerY(index, u1, v0))
    lineB(ledgerX(index, u1, v1), ledgerY(index, u1, v1))
    lineB(ledgerX(index, u0, v1), ledgerY(index, u0, v1))
    close()
}

/** A diamond (a square turned on its corner) of half-diagonal [r] at ([u], [v]) on card [index]. */
private fun Path.ledgerDiamond(index: Int, u: Float, v: Float, r: Float) {
    moveB(ledgerX(index, u, v - r * 1.2f), ledgerY(index, u, v - r * 1.2f))
    lineB(ledgerX(index, u + r, v), ledgerY(index, u + r, v))
    lineB(ledgerX(index, u, v + r * 1.2f), ledgerY(index, u, v + r * 1.2f))
    lineB(ledgerX(index, u - r, v), ledgerY(index, u - r, v))
    close()
}

/**
 * Gambler's Ledger: a fanned hand of five little reward cards (leaning like the card itself, each
 * with its band and a diamond) over a ruled ledger with a margin and an amount column, the last
 * card drawn out of the hand: one more on the table.
 */
internal fun DrawScope.drawGamblersLedgerArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 404f - 140f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line, join = StrokeJoin.Round)
    val ink = cache.stroke(b.l(2f), StrokeCap.Round)
    val w = LedgerCardW * 0.5f
    val h = LedgerCardH * 0.5f
    fadedArt(frame, 150f, 404f, 196f, 140f, 0.5f) {
        // The ledger: ruled lines, a double margin rule and the amount column, entries in ink.
        drawPath(cache.path(0) {
            for (row in 0 until 11) segB(40f, 310f + row * 12.2f, 262f, 310f + row * 12.2f)
        }, hue, alpha = 0.14f, style = hair)
        drawPath(cache.path(1) {
            segB(68f, 298f, 68f, 442f)
            segB(72f, 298f, 72f, 442f)
        }, hue, alpha = 0.2f, style = hair)
        drawPath(cache.path(2) {
            for (row in 0 until 10) {
                val y = 316f + row * 12.2f - 4f
                val text = 18f + frame.random(500 + row) * 70f
                if (frame.random(520 + row) > 0.25f) segB(80f, y, 80f + text, y)
                val amount = 8f + frame.random(540 + row) * 22f
                segB(256f - amount, y, 256f, y)
            }
        }, hue, alpha = 0.12f, style = ink)
        for (index in 0 until LedgerCards) {
            val card = cache.path(3 + index * 3) { ledgerQuad(index, -w, -h, w, h) }
            val drawn = index == LedgerCards - 1
            drawPath(card, Kk.Ink, alpha = 0.94f)
            drawPath(card, cache.brush(index) {
                lightBrush(hue, ledgerX(index, -w, -h), ledgerY(index, -w, -h), ledgerX(index, w, h), ledgerY(index, w, h), if (drawn) 0.18f else 0.12f, 0.03f)
            })
            drawPath(cache.path(4 + index * 3) { ledgerQuad(index, -w, -h, w, -h + 12f) }, hue, alpha = if (drawn) 0.2f else 0.12f)
            drawPath(card, hue, alpha = if (drawn) RewardArtAlpha.HIGH else 0.18f, style = line)
            drawPath(cache.path(5 + index * 3) {
                ledgerQuad(index, -w + 5f, -h + 17f, w - 5f, h - 5f)
                ledgerDiamond(index, 0f, 6f, 9f)
                ledgerDiamond(index, -w + 10f, -h + 23f, 2.6f)
                ledgerDiamond(index, w - 10f, h - 11f, 2.6f)
            }, hue, alpha = if (drawn) 0.22f else 0.14f, style = hair)
        }
        // The drawn card's diamond, lit.
        drawPath(cache.path(18) { ledgerDiamond(LedgerCards - 1, 0f, 6f, 6f) }, hue, alpha = RewardArtAlpha.HIGH)
    }
}
