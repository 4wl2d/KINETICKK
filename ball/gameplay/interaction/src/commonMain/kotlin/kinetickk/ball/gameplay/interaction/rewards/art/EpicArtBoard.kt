// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.kkLuminance
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
internal const val Knockout = 0.85f

/** Brush slot of the fade mask (each illustration uses the slots below it). */
private const val MaskSlot = RewardArtCache.SLOTS - 1

/** Rect slot of the fade layer's bounds. */
private const val LayerSlot = 0

internal const val Deg = (PI / 180.0).toFloat()

/**
 * The design space of these illustrations: the 300 × 450 board card in board dp, anchored at the
 * card's bottom centre. A phone card shows the same composition a little smaller, so the art keeps
 * to the lower body there too. Set by [fit] at the start of each illustration (draw thread only).
 */
internal object EpicABoard {
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

internal val Clear = Color.Transparent

/**
 * Draws [content] in one layer and fades it by an ellipse centred at board ([cx], [cy]) with radii
 * ([rx], [ry]): opaque to [inner] of the radius, gone at the rim, with a soft shoulder between.
 */
internal inline fun DrawScope.fadedArt(frame: RewardArtFrame, cx: Float, cy: Float, rx: Float, ry: Float, inner: Float, content: DrawScope.() -> Unit) {
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

internal fun boardRect(left: Float, top: Float, right: Float, bottom: Float): Rect =
    Rect(EpicABoard.x(left), EpicABoard.y(top), EpicABoard.x(right), EpicABoard.y(bottom))

internal fun Path.moveB(x: Float, y: Float) = moveTo(EpicABoard.x(x), EpicABoard.y(y))
internal fun Path.lineB(x: Float, y: Float) = lineTo(EpicABoard.x(x), EpicABoard.y(y))
internal fun Path.quadB(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(EpicABoard.x(cx), EpicABoard.y(cy), EpicABoard.x(x), EpicABoard.y(y))
internal fun Path.ovalB(cx: Float, cy: Float, rx: Float, ry: Float) = addOval(boardRect(cx - rx, cy - ry, cx + rx, cy + ry))
internal fun Path.arcB(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float, move: Boolean) =
    arcTo(boardRect(cx - rx, cy - ry, cx + rx, cy + ry), start, sweep, move)

/** A segment from ([x0], [y0]) to ([x1], [y1]) as its own contour. */
internal fun Path.segB(x0: Float, y0: Float, x1: Float, y1: Float) {
    moveB(x0, y0)
    lineB(x1, y1)
}

/** The ring sector between radii [r0] and [r1] of a circle at ([cx], [cy]). */
internal fun Path.annulusB(cx: Float, cy: Float, r0: Float, r1: Float, start: Float, sweep: Float) {
    arcB(cx, cy, r1, r1, start, sweep, true)
    arcB(cx, cy, r0, r0, start + sweep, -sweep, false)
    close()
}

/** The visible side band of an upright cylinder: its top ellipse at [top], its bottom at [bottom]. */
internal fun Path.cylinderSide(cx: Float, rx: Float, ry: Float, top: Float, bottom: Float) {
    moveB(cx - rx, top)
    lineB(cx - rx, bottom)
    arcB(cx, bottom, rx, ry, 180f, -180f, false)
    lineB(cx + rx, top)
    arcB(cx, top, rx, ry, 0f, 180f, false)
    close()
}

/** The cylinder's outline below its top face, and a groove round the middle of its side. */
internal fun Path.cylinderEdges(cx: Float, rx: Float, ry: Float, top: Float, bottom: Float, groove: Boolean) {
    moveB(cx - rx, top)
    lineB(cx - rx, bottom)
    arcB(cx, bottom, rx, ry, 180f, -180f, false)
    lineB(cx + rx, top)
    if (groove) arcB(cx, (top + bottom) * 0.5f, rx, ry, 180f, -180f, true)
}

internal fun polarX(cx: Float, r: Float, degrees: Float): Float = cx + r * cos(degrees * Deg)
internal fun polarY(cy: Float, r: Float, degrees: Float): Float = cy + r * sin(degrees * Deg)

/** [alpha] kept within the art's ceiling for the hue (ink knockouts may be dense). */
private fun artAlpha(color: Color, alpha: Float): Float = if (color == Kk.Ink) alpha else min(alpha, RewardArtAlpha.HIGH)

internal fun DrawScope.ovalB(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float, style: DrawStyle = Fill) {
    val b = EpicABoard
    drawOval(color, Offset(b.x(cx - rx), b.y(cy - ry)), Size(b.l(2f * rx), b.l(2f * ry)), artAlpha(color, alpha), style)
}

internal fun DrawScope.ovalB(brush: Brush, cx: Float, cy: Float, rx: Float, ry: Float) {
    val b = EpicABoard
    drawOval(brush, Offset(b.x(cx - rx), b.y(cy - ry)), Size(b.l(2f * rx), b.l(2f * ry)))
}

internal fun DrawScope.arcB(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float, alpha: Float, style: DrawStyle) {
    val b = EpicABoard
    drawArc(color, start, sweep, false, Offset(b.x(cx - rx), b.y(cy - ry)), Size(b.l(2f * rx), b.l(2f * ry)), artAlpha(color, alpha), style)
}

internal fun DrawScope.lineB(color: Color, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, alpha: Float, cap: StrokeCap = StrokeCap.Butt) {
    drawLine(color, EpicABoard.at(x0, y0), EpicABoard.at(x1, y1), width, cap, alpha = artAlpha(color, alpha))
}

/** A soft round glow of [hue] at board ([cx], [cy]), squashed to [squash] of its height. */
internal fun DrawScope.glowB(brush: Brush, cx: Float, cy: Float, radius: Float, squash: Float) {
    val center = EpicABoard.at(cx, cy)
    scale(1f, squash, center) { drawCircle(brush, EpicABoard.l(radius), center) }
}

internal fun glowBrush(hue: Color, cx: Float, cy: Float, radius: Float, alpha: Float): Brush =
    Brush.radialGradient(0f to hue.copy(alpha = min(alpha, RewardArtAlpha.HIGH)), 0.45f to hue.copy(alpha = min(alpha, RewardArtAlpha.HIGH) * 0.4f), 1f to hue.copy(alpha = 0f),
        center = EpicABoard.at(cx, cy), radius = EpicABoard.l(radius))

/** Turned metal across [left]..[right]: a lit flank, a dark core and a thin rim light. */
internal fun metalAcross(hue: Color, left: Float, right: Float, gain: Float = 1f): Brush = Brush.horizontalGradient(
    0f to hue.copy(alpha = 0.04f * gain), 0.2f to hue.copy(alpha = 0.16f * gain), 0.45f to hue.copy(alpha = 0.06f * gain),
    0.85f to hue.copy(alpha = 0.025f * gain), 1f to hue.copy(alpha = 0.08f * gain),
    startX = EpicABoard.x(left), endX = EpicABoard.x(right),
)

/** Light falling from ([x0], [y0]) at [from] to ([x1], [y1]) at [to]. */
internal fun lightBrush(hue: Color, x0: Float, y0: Float, x1: Float, y1: Float, from: Float, to: Float): Brush =
    Brush.linearGradient(0f to hue.copy(alpha = from), 1f to hue.copy(alpha = to), start = EpicABoard.at(x0, y0), end = EpicABoard.at(x1, y1))

// Epic signature illustrations (static, faint, behind the text), part B.
//
// Every illustration is built around one spot in the lower face (its center and reach, from the
// frame's clear region), fades out radially through cached gradient brushes (no offscreen layer)
// and keeps all geometry in the frame's cache, so a steady card draws it without allocating.

// region shared

internal const val DEG = 0.017453292f
internal const val HALF_PI = 1.5707964f
internal const val SQRT3_HALF = 0.8660254f

/** The floats slot holding the art's spot (center x, y, reach and the hue's alpha gain). */
private const val SpotSlot = RewardArtCache.SLOTS - 1

/**
 * The spot an illustration is built around: at ([fx], [fy]) of the clear region, reaching [scale]
 * times the region's comfortable radius; [3] is an alpha gain that lifts dark hues and calms light
 * ones, so every element reads equally faint.
 */
internal fun RewardArtFrame.spot(fx: Float, fy: Float, scale: Float): FloatArray = cache.floats(SpotSlot, 4) { out ->
    val area = clear
    // A phone card's text runs lower: its art sits further right and down, a little larger.
    val x = if (compact) (fx + 0.1f).coerceAtMost(0.8f) else fx
    val y = if (compact) fy + 0.18f else fy
    out[0] = area.left + area.width * x
    out[1] = area.top + area.height * y
    out[2] = min(area.width * 0.5f, area.height * 0.78f) * scale * (if (compact) 1.18f else 1f)
    out[3] = (1.3f - 0.5f * kkLuminance(hue)).coerceIn(0.9f, 1.22f)
}

/** [base] lifted by the hue's [gain], never past the art's ceiling ([RewardArtAlpha.HIGH]). */
internal fun alphaOf(base: Float, gain: Float): Float = (base * gain).coerceAtMost(RewardArtAlpha.HIGH)

/** [color] at full strength out to [hold] of [radius] from the spot, clear at [radius]. */
internal fun fadeBrush(color: Color, cx: Float, cy: Float, radius: Float, hold: Float = 0.55f): Brush =
    Brush.radialGradient(0f to color, hold to color, 1f to color.copy(alpha = 0f), center = Offset(cx, cy), radius = radius)

/** A soft round glow of [color]: full at its center, clear at [radius]. */
internal fun glowBrush(color: Color, cx: Float, cy: Float, radius: Float): Brush =
    Brush.radialGradient(0f to color, 0.35f to color.copy(alpha = 0.55f), 1f to color.copy(alpha = 0f), center = Offset(cx, cy), radius = radius)

/** Moves (or draws) to the point [x] along and [y] across a local frame at ([ox], [oy]) turned to cos [c], sin [s]. */
internal fun Path.local(move: Boolean, ox: Float, oy: Float, c: Float, s: Float, x: Float, y: Float) {
    val px = ox + x * c - y * s
    val py = oy + x * s + y * c
    if (move) moveTo(px, py) else lineTo(px, py)
}

/** A quadratic curve through the control point ([x1], [y1]) to ([x2], [y2]), in the same local frame as [local]. */
internal fun Path.localQuad(ox: Float, oy: Float, c: Float, s: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
    quadraticTo(ox + x1 * c - y1 * s, oy + x1 * s + y1 * c, ox + x2 * c - y2 * s, oy + x2 * s + y2 * c)
}

/** A whole square turned 45° (never a cut spark). */
internal fun Path.diamond(x: Float, y: Float, half: Float) {
    moveTo(x, y - half)
    lineTo(x + half, y)
    lineTo(x, y + half)
    lineTo(x - half, y)
    close()
}

// endregion
