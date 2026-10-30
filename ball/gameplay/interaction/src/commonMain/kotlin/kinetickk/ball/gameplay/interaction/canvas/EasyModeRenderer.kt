// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkPacifierSigilPaths
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Baby mode (the easy run mode) in the run: pastel accents, the diaper on the Core, the pacifier wings on the cursor and
// the pacifier plate in the Rebirth strip's slot. Procedural and cached: a steady frame allocates nothing.

/** Pastel mint of Baby mode accents (the world's halftone). */
internal fun easyPastelMint(roles: KkRolePalette): Color = kkMix(roles.soft, Kk.Bone, 0.45f)

/** Pastel lilac of Baby mode accents. */
internal fun easyPastelLilac(roles: KkRolePalette): Color = kkMix(roles.pol, Kk.Bone, 0.5f)

/** Pastel volt of the Baby mode ribbon: still "you", never the cursor's soft green. */
internal fun easyPastelYou(roles: KkRolePalette): Color = kkMix(roles.you, Kk.Bone, 0.35f)

/**
 * The diaper on the Core at [center]: a band over the lower part of a circle of 1.12 Core radii (a circular segment
 * below 0.18 of that radius, ink outlined), two lilac side tabs and a safety pin on the right tab. Object-local and
 * never rotated, so it stays a diaper while the form spins. [alpha] 0.5 while the Core is phased, like the form.
 */
internal fun DrawScope.drawDiaper(engine: GameplayRenderModel, center: Offset, roles: KkRolePalette, alpha: Float) {
    val shapes = EasyModeShapes.of(engine.coreRadius * DIAPER_RADIUS_RATIO)
    translate(center.x, center.y) {
        drawPath(shapes.band, kkMix(Kk.Bone, roles.soft, 0.25f), alpha = alpha)
        drawPath(shapes.band, Kk.Ink, alpha = alpha, style = kkStroke(1.5f))
        drawPath(shapes.tabs, easyPastelLilac(roles), alpha = alpha)
        drawPath(shapes.pin, Kk.Bone2, alpha = alpha)
        drawPath(shapes.pin, Kk.Ink, alpha = alpha, style = kkStroke(1f))
    }
}

/**
 * The pacifier guard of the Baby mode cursor: two arcs of 60 degrees left and right of [center] at radius 19, outside
 * the ring (13.5 with its 1.5 half stroke) so whatever the cursor sits on stays visible inside it.
 */
internal fun DrawScope.drawPacifierWings(center: Offset, tone: Color) {
    val topLeft = Offset(center.x - PACIFIER_WING_RADIUS, center.y - PACIFIER_WING_RADIUS)
    val size = Size(PACIFIER_WING_RADIUS * 2f, PACIFIER_WING_RADIUS * 2f)
    val stroke = kkStroke(4f)
    drawArc(tone.copy(alpha = 0.8f), 150f, 60f, false, topLeft, size, style = stroke)
    drawArc(tone.copy(alpha = 0.8f), -30f, 60f, false, topLeft, size, style = stroke)
}

/**
 * The Baby mode plate in the Rebirth strip's slot: an ink disc of [radius] at ([cx], [cy]) ringed in the soft role
 * with the pacifier sigil inside. No text (the reduced HUD carries none).
 */
internal fun DrawScope.drawEasyStripPlate(roles: KkRolePalette, cx: Float, cy: Float, radius: Float, frame: HudFrame) {
    val center = Offset(cx, cy)
    drawCircle(Kk.Ink2, radius, center)
    drawCircle(roles.soft, radius - frame.u(0.75f), center, style = kkStroke(frame.u(1.5f)))
    translate(cx, cy) { drawPath(KkPacifierSigilPaths.of(radius * 0.62f), roles.soft) }
}

/** The diaper's radius in Core radii. */
private const val DIAPER_RADIUS_RATIO = 1.12f

/** The cursor's pacifier wings: their radius (the ring is 13.5, the dashed outer ring 27.25). */
private const val PACIFIER_WING_RADIUS = 19f

/** Origin-centred diaper paths cached by radius (4 slots, round robin: one Core radius at a time in a run). */
private object EasyModeShapes {
    private const val CAPACITY = 4
    private val keys = IntArray(CAPACITY)
    private val slots = arrayOfNulls<DiaperPaths>(CAPACITY)
    private var next = 0

    fun of(radius: Float): DiaperPaths {
        val key = radius.toRawBits()
        for (slot in 0 until CAPACITY) {
            val paths = slots[slot]
            if (paths != null && keys[slot] == key) return paths
        }
        val paths = slots[next] ?: DiaperPaths().also { slots[next] = it }
        paths.build(radius)
        keys[next] = key
        next = (next + 1) % CAPACITY
        return paths
    }
}

/** The diaper of one radius: the band, the two tabs and the safety pin, centred on the Core. */
private class DiaperPaths {
    val band = Path()
    val tabs = Path()
    val pin = Path()

    fun build(r: Float) {
        band.rewind()
        // The segment below y = 0.18 r: twelve steps along the circle from 10.4 to 169.6 degrees through the bottom
        // (y grows downward), closed by the chord.
        for (step in 0..BAND_STEPS) {
            val angle = (BAND_FROM + (BAND_TO - BAND_FROM) * step / BAND_STEPS) * DEG_TO_RAD
            val x = cos(angle) * r
            val y = sin(angle) * r
            if (step == 0) band.moveTo(x, y) else band.lineTo(x, y)
        }
        band.close()
        tabs.rewind()
        for (side in intArrayOf(-1, 1)) {
            val cx = side * 0.82f * r
            val cy = 0.30f * r
            val halfWidth = 0.17f * r
            val halfHeight = 0.12f * r
            tabs.moveTo(cx - halfWidth, cy - halfHeight)
            tabs.lineTo(cx + halfWidth, cy - halfHeight)
            tabs.lineTo(cx + halfWidth, cy + halfHeight)
            tabs.lineTo(cx - halfWidth, cy + halfHeight)
            tabs.close()
        }
        pin.rewind()
        // The head: an octagon of 0.10 r at (0.46 r, 0.46 r).
        val headX = 0.46f * r
        val headY = 0.46f * r
        for (index in 0 until 8) {
            val angle = (index * 45f + 22.5f) * DEG_TO_RAD
            val x = headX + cos(angle) * 0.10f * r
            val y = headY + sin(angle) * 0.10f * r
            if (index == 0) pin.moveTo(x, y) else pin.lineTo(x, y)
        }
        pin.close()
        // The bar: an elongated hexagon from 0.48 r to 0.98 r, 0.14 r tall, level with the head.
        val left = 0.48f * r
        val right = 0.98f * r
        val half = 0.07f * r
        pin.moveTo(left, headY)
        pin.lineTo(left + half, headY - half)
        pin.lineTo(right - half, headY - half)
        pin.lineTo(right, headY)
        pin.lineTo(right - half, headY + half)
        pin.lineTo(left + half, headY + half)
        pin.close()
    }

    private companion object {
        const val BAND_STEPS = 12
        const val BAND_FROM = 10.4f
        const val BAND_TO = 169.6f
        const val DEG_TO_RAD = (PI / 180.0).toFloat()
    }
}
