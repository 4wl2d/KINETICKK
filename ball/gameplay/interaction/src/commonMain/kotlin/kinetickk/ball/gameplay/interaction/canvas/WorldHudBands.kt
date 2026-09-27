// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.layout.runningHudMargin
import kotlin.math.max
import kotlin.math.min

/**
 * Screen regions the running HUD occupies, in px, so world labels and edge markers stay clear of
 * it. Follows the HUD layout per mode (HudRenderer, the trial panel layout and the running
 * controls) with a small padding. Recomputed only when the viewport, text size, trial or boss
 * state changes; draw-thread confined.
 */
internal class WorldHudKeepOut {
    var count = 0
        private set

    /** Incremented whenever the regions are recomputed. */
    var revision = 0
        private set
    private val rects = FloatArray(MAX_RECTS * 4)
    private val trialPanel = HudTrialPanelLayout()
    private var keyWidth = Float.NaN
    private var keyHeight = Float.NaN
    private var keyDensity = Float.NaN
    private var keyTextScale = Float.NaN
    private var keyTrial = false
    private var keyBoss = false

    fun update(width: Float, height: Float, density: Float, textScale: Float, trialActive: Boolean, bossPresent: Boolean): WorldHudKeepOut {
        if (width == keyWidth && height == keyHeight && density == keyDensity && textScale == keyTextScale &&
            trialActive == keyTrial && bossPresent == keyBoss
        ) return this
        keyWidth = width
        keyHeight = height
        keyDensity = density
        keyTextScale = textScale
        keyTrial = trialActive
        keyBoss = bossPresent
        revision++
        count = 0
        val scale = density.coerceAtLeast(1f)
        val mode = gameplayLayoutMode(width, height, scale)
        val margin = runningHudMargin(width, height, scale)
        val unit = if (mode == GameplayLayoutMode.REGULAR) regularHudUnit(width, scale) else scale
        val centerX = width * 0.5f
        when (mode) {
            GameplayLayoutMode.REGULAR -> {
                add(0f, 0f, width, 92f * unit) // level badge and data bar, timer, chips, pause
                if (bossPresent) add(centerX - 380f * unit, 0f, centerX + 380f * unit, 134f * unit)
                val chainTop = max(76f * unit, min(160f * unit, height * 0.2f))
                add(width - margin - 200f * unit, chainTop - 8f * unit, width, chainTop + 84f * unit)
                add(0f, height - 136f * unit, margin + 360f * unit, height) // integrity, shield, dash, heat
                add(centerX - 220f * unit, height - 136f * unit, centerX + 220f * unit, height) // speed
            }
            GameplayLayoutMode.COMPACT_LANDSCAPE -> {
                add(0f, 0f, width, 58f * unit) // badge, matter, timer, chain, buttons
                if (bossPresent) add(centerX - 170f * unit, 0f, centerX + 170f * unit, 80f * unit)
                add(width - margin - 180f * unit, 0f, width, 124f * unit) // relics and weapon
                add(0f, height - 84f * unit, margin + 230f * unit, height) // integrity cluster
                add(centerX - 150f * unit, height - 84f * unit, centerX + 140f * unit, height) // speed
            }
            GameplayLayoutMode.COMPACT_PORTRAIT -> {
                add(0f, 0f, width, 146f * unit) // status inset, badge, timer, chips, chain
                add(0f, height - 252f * unit, width, height) // loadout, integrity, speed, touch buttons
            }
        }
        if (trialActive) {
            val panel = trialPanel.update(width, height, scale, textScale)
            add(panel.left - 8f * unit, panel.top - 8f * unit, panel.right + 8f * unit, panel.bottom + 8f * unit)
        }
        var controlsLeft = Float.POSITIVE_INFINITY
        var controlsTop = Float.POSITIVE_INFINITY
        forEachRunningControlBounds(width, height, scale) { target, left, top, _, _ ->
            if (target == RunningControlTarget.DASH || target == RunningControlTarget.BRAKE) {
                controlsLeft = min(controlsLeft, left)
                controlsTop = min(controlsTop, top)
            }
        }
        if (controlsLeft.isFinite()) add(controlsLeft - 8f * unit, controlsTop - 8f * unit, width, height)
        return this
    }

    private fun add(left: Float, top: Float, right: Float, bottom: Float) {
        if (count >= MAX_RECTS) return
        val base = count * 4
        rects[base] = left
        rects[base + 1] = top
        rects[base + 2] = right
        rects[base + 3] = bottom
        count++
    }

    /** Whether the box overlaps any HUD region. */
    fun intersects(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        for (index in 0 until count) {
            val base = index * 4
            if (left < rects[base + 2] && right > rects[base] && top < rects[base + 3] && bottom > rects[base + 1]) return true
        }
        return false
    }

    /** The regions as rects (tests). */
    fun rects(): List<Rect> = List(count) { Rect(rects[it * 4], rects[it * 4 + 1], rects[it * 4 + 2], rects[it * 4 + 3]) }

    private companion object {
        const val MAX_RECTS = 12
    }
}

/**
 * Places off-screen markers at the screen edge (`HUD-Elite.png`): on a ring inset from the edges,
 * pushed inward at most a tenth of the short side, and slid along the edge past HUD regions so a
 * marker never sits in the field or on the HUD. The table of clear ring positions is rebuilt only
 * when the keep-out, viewport or marker size changes.
 */
internal class EdgeMarkerPlanner {
    private val depths = FloatArray(MAX_SAMPLES)
    private var samples = 0
    private var spacing = 1f
    private var ringLeft = 0f
    private var ringTop = 0f
    private var ringRight = 0f
    private var ringBottom = 0f
    private var width = 0f
    private var height = 0f
    private var iconHalf = 0f
    private var gap = 0f
    private var textWidth = 0f
    private var halfHeight = 0f
    private var keepOut: WorldHudKeepOut? = null
    private var keyRevision = -1
    private var keyWidth = Float.NaN
    private var keyHeight = Float.NaN
    private var keyDensity = Float.NaN
    private var keyTextWidth = Float.NaN

    /** Rebuilds the clear-position table when [keepOut], the viewport or the marker width changed. */
    fun prepare(keepOut: WorldHudKeepOut, width: Float, height: Float, density: Float, textWidth: Float): EdgeMarkerPlanner {
        if (this.keepOut === keepOut && keyRevision == keepOut.revision && keyWidth == width &&
            keyHeight == height && keyDensity == density && keyTextWidth == textWidth
        ) return this
        this.keepOut = keepOut
        keyRevision = keepOut.revision
        keyWidth = width
        keyHeight = height
        keyDensity = density
        keyTextWidth = textWidth
        this.width = width
        this.height = height
        val unit = density.coerceAtLeast(1f)
        iconHalf = EDGE_ICON_DP * 0.5f * unit
        gap = EDGE_TEXT_GAP_DP * unit
        this.textWidth = textWidth
        halfHeight = EDGE_HALF_HEIGHT_DP * unit
        val inset = EDGE_INSET_DP * unit
        ringLeft = inset
        ringTop = inset
        ringRight = width - inset
        ringBottom = height - inset
        val ringWidth = max(0f, ringRight - ringLeft)
        val ringHeight = max(0f, ringBottom - ringTop)
        val perimeter = 2f * (ringWidth + ringHeight)
        spacing = max(EDGE_SAMPLE_DP * unit, perimeter / MAX_SAMPLES)
        samples = min(MAX_SAMPLES, (perimeter / spacing).toInt().coerceAtLeast(1))
        val maxDepth = maxEdgeDepth(width, height)
        val step = 3f * unit
        for (index in 0 until samples) {
            val s = index * spacing
            val px = ringX(s)
            val py = ringY(s)
            val nx = normalX(s)
            val ny = normalY(s)
            var depth = Float.NaN
            var k = 0f
            while (k <= maxDepth) {
                if (clearAt(px + nx * k, py + ny * k)) {
                    depth = k
                    break
                }
                k += step
            }
            depths[index] = depth
        }
        return this
    }

    /**
     * Marker position for an off-screen [target]: where the ray from the screen center meets the
     * edge ring, moved to the nearest clear ring position.
     */
    fun position(target: Offset): Offset {
        val s0 = rayParameter(target.x, target.y)
        if (samples <= 0) return Offset(ringX(s0), ringY(s0))
        val start = ((s0 / spacing) + 0.5f).toInt() % samples
        for (distance in 0..samples / 2) {
            val forward = (start + distance) % samples
            if (!depths[forward].isNaN()) return at(forward)
            val backward = ((start - distance) % samples + samples) % samples
            if (!depths[backward].isNaN()) return at(backward)
        }
        return Offset(ringX(s0), ringY(s0))
    }

    /** Whether the marker (icon + distance, text toward the screen center) fits at ([x], [y]). */
    fun clearAt(x: Float, y: Float): Boolean {
        val left = markerLeft(x)
        val right = markerRight(x)
        val top = y - halfHeight
        val bottom = y + halfHeight
        if (left < 0f || right > width || top < 0f || bottom > height) return false
        return keepOut?.intersects(left, top, right, bottom) != true
    }

    fun markerLeft(x: Float): Float = if (x > width * 0.5f) x - iconHalf - gap - textWidth else x - iconHalf
    fun markerRight(x: Float): Float = if (x > width * 0.5f) x + iconHalf else x + iconHalf + gap + textWidth
    fun markerHalfHeight(): Float = halfHeight

    private fun at(index: Int): Offset {
        val s = index * spacing
        val depth = depths[index]
        return Offset(ringX(s) + normalX(s) * depth, ringY(s) + normalY(s) * depth)
    }

    private val ringWidth get() = max(0f, ringRight - ringLeft)
    private val ringHeight get() = max(0f, ringBottom - ringTop)

    // The ring runs clockwise from the top-left corner: top, right, bottom, left.
    private fun ringX(s: Float): Float {
        val w = ringWidth
        val h = ringHeight
        return when {
            s < w -> ringLeft + s
            s < w + h -> ringRight
            s < 2f * w + h -> ringRight - (s - w - h)
            else -> ringLeft
        }
    }

    private fun ringY(s: Float): Float {
        val w = ringWidth
        val h = ringHeight
        return when {
            s < w -> ringTop
            s < w + h -> ringTop + (s - w)
            s < 2f * w + h -> ringBottom
            else -> ringBottom - (s - 2f * w - h)
        }
    }

    private fun normalX(s: Float): Float {
        val w = ringWidth
        val h = ringHeight
        return when {
            s < w -> 0f
            s < w + h -> -1f
            s < 2f * w + h -> 0f
            else -> 1f
        }
    }

    private fun normalY(s: Float): Float {
        val w = ringWidth
        val h = ringHeight
        return when {
            s < w -> 1f
            s < w + h -> 0f
            s < 2f * w + h -> -1f
            else -> 0f
        }
    }

    /** Ring parameter where the ray from the screen center toward ([x], [y]) meets the ring. */
    private fun rayParameter(x: Float, y: Float): Float {
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val dx = x - centerX
        val dy = y - centerY
        val tx = when {
            dx > 0f -> (ringRight - centerX) / dx
            dx < 0f -> (ringLeft - centerX) / dx
            else -> Float.POSITIVE_INFINITY
        }
        val ty = when {
            dy > 0f -> (ringBottom - centerY) / dy
            dy < 0f -> (ringTop - centerY) / dy
            else -> Float.POSITIVE_INFINITY
        }
        val nearest = min(tx, ty)
        val t = if (nearest.isFinite()) nearest else 0f
        val hitX = (centerX + dx * t).coerceIn(ringLeft, ringRight)
        val hitY = (centerY + dy * t).coerceIn(ringTop, ringBottom)
        val w = ringWidth
        val h = ringHeight
        return when {
            tx <= ty && dx > 0f -> w + (hitY - ringTop)
            tx <= ty && dx < 0f -> 2f * w + h + (ringBottom - hitY)
            dy < 0f -> hitX - ringLeft
            else -> w + h + (ringRight - hitX)
        }
    }

    private companion object {
        const val MAX_SAMPLES = 1_024
    }
}

/** Edge markers sit this far from the screen edge (icon center) before any inward push. */
internal const val EDGE_INSET_DP = 24f
internal const val EDGE_ICON_DP = 18f
internal const val EDGE_TEXT_GAP_DP = 8f
internal const val EDGE_HALF_HEIGHT_DP = 11f
private const val EDGE_SAMPLE_DP = 6f

/** How far inward an edge marker may move to clear the HUD: a tenth of the short side. */
internal fun maxEdgeDepth(width: Float, height: Float): Float = 0.1f * min(width, height)
