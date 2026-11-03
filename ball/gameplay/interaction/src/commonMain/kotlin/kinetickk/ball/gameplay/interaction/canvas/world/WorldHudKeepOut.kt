// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.SKILL_KEY_CAP_REACH_DP
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.layout.*

/**
 * Screen regions the running HUD occupies, in px, so world labels and edge markers stay clear of
 * it. The top-center column (clock, elite / Architect block), the chain, the trial panel and the
 * running controls come from the HUD's own geometry ([HudTopGeometry], [HudFrame],
 * [HudTrialPanelLayout], [forEachRunningControlBounds]) at the current text size, so the regions
 * follow the HUD; the remaining clusters use their board reserves. Recomputed only when the
 * viewport, text size, trial or boss state changes; draw-thread confined.
 */
internal class WorldHudKeepOut {
    var count = 0
        private set

    /** Incremented whenever the regions are recomputed. */
    var revision = 0
        private set
    private val rects = FloatArray(MAX_RECTS * 4)
    private val trialPanel = HudTrialPanelLayout()
    private val frame = HudFrame()
    private var keyWidth = Float.NaN
    private var keyHeight = Float.NaN
    private var keyDensity = Float.NaN
    private var keyTextScale = Float.NaN
    private var keyTrial = false
    private var keyBoss = false
    private var keyMinimap = false
    private val minimap = FloatArray(4)

    fun update(
        width: Float,
        height: Float,
        density: Float,
        textScale: Float,
        trialActive: Boolean,
        bossPresent: Boolean,
        minimapShown: Boolean = true,
    ): WorldHudKeepOut {
        if (width == keyWidth && height == keyHeight && density == keyDensity && textScale == keyTextScale &&
            trialActive == keyTrial && bossPresent == keyBoss && minimapShown == keyMinimap
        ) return this
        keyWidth = width
        keyHeight = height
        keyDensity = density
        keyTextScale = textScale
        keyTrial = trialActive
        keyBoss = bossPresent
        keyMinimap = minimapShown
        revision++
        count = 0
        val scale = density.coerceAtLeast(1f)
        val frame = frame.update(width, height, scale)
        val mode = frame.mode
        val margin = frame.margin
        val unit = frame.unit
        val pad = 8f * unit
        val centerX = width * 0.5f
        var controlsLeft = Float.POSITIVE_INFINITY
        var controlsTop = Float.POSITIVE_INFINITY
        var skillsLeft = Float.POSITIVE_INFINITY
        var skillsTop = Float.POSITIVE_INFINITY
        var skillsRight = Float.NEGATIVE_INFINITY
        var skillsBottom = Float.NEGATIVE_INFINITY
        var headerBottom = 0f
        // Every control that may appear (the skill row and the Apex badge included), so the regions
        // do not move when the first active item binds a slot or the Apex unlocks.
        forEachRunningControlBounds(width, height, scale, RunningControlMask.ALL) { target, left, top, right, bottom ->
            when (target) {
                RunningControlTarget.DASH, RunningControlTarget.BRAKE -> {
                    controlsLeft = min(controlsLeft, left)
                    controlsTop = min(controlsTop, top)
                }
                RunningControlTarget.SKILL_1, RunningControlTarget.SKILL_2, RunningControlTarget.SKILL_3 -> {
                    skillsLeft = min(skillsLeft, left)
                    skillsTop = min(skillsTop, top)
                    skillsRight = max(skillsRight, right)
                    skillsBottom = max(skillsBottom, bottom)
                }
                // Pause and performance in the top row, the Apex badge at the top-left.
                RunningControlTarget.PAUSE, RunningControlTarget.PERFORMANCE, RunningControlTarget.APEX ->
                    headerBottom = max(headerBottom, bottom)
            }
        }
        // REGULAR skill squares carry their key caps below them.
        if (mode == GameplayLayoutMode.REGULAR && skillsBottom.isFinite()) skillsBottom += SKILL_KEY_CAP_REACH_DP * unit
        val clockBottom = HudTopGeometry.clockTop(frame) + HudTopGeometry.clockHeight(frame)
        when (mode) {
            GameplayLayoutMode.REGULAR -> {
                // Level badge (a display numeral, 38 board px) and its Data bar; chips; clock; pause.
                val badgeBottom = 26f * unit + 38f * unit + 14f * unit
                add(0f, 0f, width, max(max(badgeBottom, clockBottom), max(60f * unit, headerBottom)) + pad)
                val chainTop = frame.chainTop
                val chainBottom = chainTop + 60f * frame.textFactor * COND_LINE_HEIGHT_EM * scale + 10f * unit
                add(width - margin - 200f * unit, chainTop - pad, width, chainBottom + pad)
                add(0f, frame.bottomClustersTop, margin + 360f * unit, height) // integrity, shield, dash, heat
                add(centerX - 220f * unit, frame.bottomClustersTop, centerX + 220f * unit, height) // speed
            }
            GameplayLayoutMode.COMPACT_LANDSCAPE -> {
                // Badge, matter, clock, chain (a display numeral with its bar), performance and pause.
                val chainBottom = 30f * unit + 26f * COND_LINE_HEIGHT_EM * scale * 0.5f + 5f * unit
                add(0f, 0f, width, max(max(clockBottom, chainBottom), headerBottom) + 4f * unit)
                // Relics (with stacked synergy brackets) and the weapon slot with its level under it.
                val loadoutBottom = 62f * unit + 34f * unit + 4f * unit + 9f * scale * hudUiScale(textScale)
                add(width - margin - 180f * unit, 0f, width, loadoutBottom + pad)
                add(0f, frame.bottomClustersTop, margin + 230f * unit, height) // integrity cluster
                add(centerX - 150f * unit, frame.bottomClustersTop, centerX + 140f * unit, height) // speed
            }
            GameplayLayoutMode.COMPACT_PORTRAIT -> {
                // Status inset, badge, clock, chips and chain: the rows above the reserved boss row.
                add(0f, 0f, width, HudTopGeometry.bossTop(frame))
                add(0f, frame.bottomClustersTop, width, height) // loadout, integrity, speed, touch buttons
            }
        }
        if (bossPresent) {
            // The elite / Architect block under the clock (its own row on portrait phones), with the
            // name at the current text size; whichever boss it is, both shapes are covered.
            val half = max(HudTopGeometry.bossBarWidth(frame, wide = true), HudTopGeometry.bossBarWidth(frame, wide = false)) * 0.5f
            val bottom = max(HudTopGeometry.bossBottom(frame, true, textScale), HudTopGeometry.bossBottom(frame, false, textScale))
            add(max(0f, centerX - half - pad), 0f, min(width, centerX + half + pad), bottom + pad)
        }
        if (trialActive) {
            val panel = trialPanel.update(width, height, scale, textScale)
            add(panel.left - pad, panel.top - pad, panel.right + pad, panel.bottom + pad)
        }
        if (minimapShown && HudMinimapGeometry.place(frame, if (trialActive) trialPanel else null, minimap)) {
            add(minimap[0] - pad, minimap[1] - pad, minimap[2] + pad, minimap[3] + pad)
        }
        if (controlsLeft.isFinite()) add(controlsLeft - pad, controlsTop - pad, width, height)
        if (skillsLeft.isFinite()) add(skillsLeft - pad, skillsTop - pad, skillsRight + pad, skillsBottom + pad)
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
