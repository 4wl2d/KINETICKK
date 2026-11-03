// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.max
import kotlin.math.min

/**
 * Animated meter fills (`.meter.striped` threat/ink, `.meter.striped-v` you/dark, the running Apex in
 * the preset's gold over its seam).
 */
enum class KkMeterStripes { NONE, THREAT, YOU, GOLD }

/**
 * Meter (`.meter`), leaning −12°. [value] and [ghost] are fractions 0..1 of the full meter.
 *
 * - [segments] > 0 splits the meter into that many cells with 3 dp gaps (`.meter.seg`; e.g.
 *   integrity with one cell per 20 points).
 * - [ghost] draws the hit ghost from [value] to [value] + [ghost] in [ghostColor]; the caller
 *   drains it over [KkTime.GhostDrain] ms.
 * - [stripes] replaces the fill with moving diagonal stripes (overheat = THREAT, overdrive
 *   active = YOU, a running Apex = GOLD); [time] in seconds animates them.
 * - [fillBrush] (a cached brush: pattern tile or [kkFoilBrush]) replaces a plain fill, anchored at
 *   the meter's left edge; [fillAlpha] fades a plain or brush fill.
 * - [edge] draws the 3 dp value edge in [edgeColor].
 * - [threat] marks the fill as a threat so MONO adds the hatch.
 * - [fromEnd] anchors the fill at the meter's right edge (a bar that drains from the left): the
 *   value spans the right [value] share and the ghost sits just left of it.
 *
 * Allocation-free (cached stripe tiles, no paths).
 */
fun DrawScope.drawKkMeter(
    bounds: Rect,
    value: Float,
    color: Color,
    roles: KkRolePalette,
    background: Color = Color(0x1AF1F0E8),
    segments: Int = 0,
    ghost: Float = 0f,
    ghostColor: Color = roles.threat,
    stripes: KkMeterStripes = KkMeterStripes.NONE,
    time: Float = 0f,
    edge: Boolean = false,
    threat: Boolean = false,
    fillBrush: Brush? = null,
    edgeColor: Color = Kk.Bone,
    fillAlpha: Float = 1f,
    fromEnd: Boolean = false,
) {
    val v = value.coerceIn(0f, 1f)
    val reach = (v + ghost.coerceAtLeast(0f)).coerceAtMost(1f)
    // From the left: the fill ends at valueX and the ghost runs on to ghostX. From the end: the fill starts at valueX and
    // the ghost runs back to ghostX, left of it.
    val valueX = if (fromEnd) bounds.right - bounds.width * v else bounds.left + bounds.width * v
    val ghostX = if (fromEnd) bounds.right - bounds.width * reach else bounds.left + bounds.width * reach
    withTransform({ kkShear(bounds.center.y) }) {
        if (segments <= 0) {
            drawMeterSpan(bounds, bounds.left, bounds.right, valueX, ghostX, color, background, ghostColor, stripes, roles, time, threat,
                fillBrush, fillAlpha, fromEnd)
        } else {
            val pitch = bounds.width / segments
            val gap = d(3f).coerceAtMost(pitch * 0.5f)
            for (index in 0 until segments) {
                val left = bounds.left + index * pitch
                drawMeterSpan(bounds, left, left + pitch - gap, valueX, ghostX, color, background, ghostColor, stripes, roles, time, threat,
                    fillBrush, fillAlpha, fromEnd)
            }
        }
        if (edge && v > 0f) drawRect(edgeColor, Offset(if (fromEnd) valueX else valueX - d(3f), bounds.top), Size(d(3f), bounds.height))
    }
}

private fun DrawScope.drawMeterSpan(
    bounds: Rect,
    left: Float,
    right: Float,
    valueX: Float,
    ghostX: Float,
    color: Color,
    background: Color,
    ghostColor: Color,
    stripes: KkMeterStripes,
    roles: KkRolePalette,
    time: Float,
    threat: Boolean,
    fillBrush: Brush?,
    fillAlpha: Float,
    fromEnd: Boolean = false,
) {
    if (right <= left) return
    val height = bounds.height
    if (background.alpha > 0f) drawRect(background, Offset(left, bounds.top), Size(right - left, height))
    val fillLeft = if (fromEnd) max(left, valueX) else left
    val fillRight = if (fromEnd) right else min(right, valueX)
    if (fillRight > fillLeft) {
        when (stripes) {
            KkMeterStripes.NONE -> if (fillBrush != null) {
                // Anchored at the meter's left edge, so the brush does not depend on where the meter sits.
                translate(bounds.left, 0f) {
                    drawRect(fillBrush, Offset(fillLeft - bounds.left, bounds.top), Size(fillRight - fillLeft, height), fillAlpha)
                }
            } else {
                drawRect(color, Offset(fillLeft, bounds.top), Size(fillRight - fillLeft, height), fillAlpha)
            }
            KkMeterStripes.THREAT, KkMeterStripes.YOU, KkMeterStripes.GOLD -> kkDrawStripes(
                fillLeft, bounds.top, fillRight, bounds.bottom, meterStripeColor(stripes, roles), meterStripeBackground(stripes, roles),
                time, KkFillKind.METER_STRIPES,
            )
        }
        if (threat && roles.hatchThreats) {
            drawRect(kkFillBrush(KkFillKind.THREAT_HATCH, Kk.Ink), Offset(fillLeft, bounds.top), Size(fillRight - fillLeft, height))
        }
    }
    val ghostLeft = if (fromEnd) max(left, ghostX) else max(left, valueX)
    val ghostRight = if (fromEnd) min(right, valueX) else min(right, ghostX)
    if (ghostRight > ghostLeft) drawRect(ghostColor, Offset(ghostLeft, bounds.top), Size(ghostRight - ghostLeft, height))
}

/** The bright stripe of a striped meter fill ([KkMeterStripes.NONE] has none: its plain colour applies). */
internal fun meterStripeColor(stripes: KkMeterStripes, roles: KkRolePalette): Color = when (stripes) {
    KkMeterStripes.THREAT -> roles.threat
    KkMeterStripes.YOU, KkMeterStripes.NONE -> roles.you
    KkMeterStripes.GOLD -> roles.gold.base
}

/** The dark stripe between the bright ones. */
internal fun meterStripeBackground(stripes: KkMeterStripes, roles: KkRolePalette): Color = when (stripes) {
    KkMeterStripes.THREAT -> kkMix(roles.threat, Kk.Ink, 0.45f)
    KkMeterStripes.YOU, KkMeterStripes.NONE -> kkMix(roles.you, Kk.Ink, 0.35f)
    KkMeterStripes.GOLD -> roles.gold.seam
}

/**
 * Moving meter stripes ([stripes], [KkFillKind.METER_STRIPES]) filling the plain rect from [left] to
 * [right] (no lean): lines and frames that show a striped meter state. Allocation-free.
 */
fun DrawScope.drawKkMeterStripes(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    stripes: KkMeterStripes,
    roles: KkRolePalette,
    time: Float,
) {
    kkDrawStripes(left, top, right, bottom, meterStripeColor(stripes, roles), meterStripeBackground(stripes, roles), time,
        KkFillKind.METER_STRIPES)
}

/**
 * Tick ladder (HUD velocity, `.vtick`): [count] sheared ticks with 2 dp gaps inside [bounds].
 * Ticks below [lit] are bone, from [hotFrom] `you`, from [maxFrom] threat; unlit ticks ink-4.
 * Allocation-free.
 */
fun DrawScope.drawKkTickLadder(
    bounds: Rect,
    roles: KkRolePalette,
    count: Int,
    lit: Int,
    hotFrom: Int = count,
    maxFrom: Int = count,
) {
    if (count <= 0) return
    val gap = d(2f)
    val pitch = (bounds.width + gap) / count
    withTransform({ kkShear(bounds.center.y) }) {
        for (index in 0 until count) {
            val color = when {
                index >= lit -> Kk.Ink4
                index >= maxFrom -> roles.threat
                index >= hotFrom -> roles.you
                else -> Kk.Bone
            }
            drawRect(color, Offset(bounds.left + index * pitch, bounds.top), Size(pitch - gap, bounds.height))
        }
    }
}
