// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_HUD_TOP_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_PANEL_ROW_DP
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.kkStroke
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Where the minimap sits (px), shared by the renderer and the world keep-out ([WorldHudKeepOut]) so
 * edge markers and world labels stay off it: the left column under the level badge on desktop and
 * landscape phones, the right edge under the boss row on portrait phones; under the trial panel
 * while a trial runs. It shrinks to fit above the bottom clusters and is left out when even the
 * smallest size does not fit.
 */
internal object HudMinimapGeometry {
    /** Side of the minimap in reference dp: desktop and phones. */
    const val REGULAR_SIDE_DP: Float = 132f
    const val COMPACT_SIDE_DP: Float = 104f
    private const val MINIMUM_SIDE_DP: Float = 56f

    /**
     * Writes the square into [out] (left, top, right, bottom) for [frame]; [trial] is the laid-out trial
     * panel while one runs. Returns false when it has no room.
     */
    fun place(frame: HudFrame, trial: HudTrialPanelLayout?, out: FloatArray): Boolean {
        val gap = frame.u(if (frame.regular) 10f else 6f)
        var side = frame.u(if (frame.regular) REGULAR_SIDE_DP else COMPACT_SIDE_DP)
        val top = when {
            trial != null -> trial.bottom + gap
            frame.mode == GameplayLayoutMode.REGULAR -> frame.u(118f)
            frame.mode == GameplayLayoutMode.COMPACT_LANDSCAPE -> frame.u(48f)
            else -> frame.u(PORTRAIT_HUD_TOP_DP + PORTRAIT_PANEL_ROW_DP)
        }
        val limit = frame.bottomClustersTop - gap
        if (top + side > limit) side = limit - top
        if (side < frame.u(MINIMUM_SIDE_DP)) return false
        val left = if (frame.portrait) frame.width - frame.margin - side else frame.margin
        out[0] = left
        out[1] = top
        out[2] = left + side
        out[3] = top + side
        return true
    }
}

/** Draw-thread scratch of the minimap (rewritten every frame, no allocation). */
private object MinimapScratch {
    val rect = FloatArray(4)
    val trial = HudTrialPanelLayout()
}

/**
 * The run minimap (`HudBlock.MINIMAP`): north-up and centred on the Core, out to
 * [GameplayRenderModel.minimapRange] world units. Obstacles (bumpers as rings, unstable rocks hot),
 * enemies as dots, Elites as aspect-coloured diamonds, Heralds and the Architect large, anomalies,
 * the Totem, Keys and Relic resonances (pinned to the rim when beyond it) and the Core's heading.
 * Hidden by the Minimap setting.
 */
internal fun DrawScope.drawMinimap(engine: GameplayRenderModel, measurer: TextMeasurer, frame: HudFrame) {
    if (!engine.settings.minimapEnabled) return
    var trialActive = false
    val points = engine.pointsOfInterest
    for (index in points.indices) if (points[index].active) trialActive = true
    val trial = if (trialActive) MinimapScratch.trial.update(size.width, size.height, density, measurer.scale) else null
    val rect = MinimapScratch.rect
    if (!HudMinimapGeometry.place(frame, trial, rect)) return
    val left = rect[0]
    val top = rect[1]
    val side = rect[2] - left
    HudLayoutProbe.record(HudBlock.MINIMAP, left, top, rect[2], rect[3])
    val roles = measurer.roles
    val corner = frame.u(6f)
    drawRoundRect(Kk.Ink.copy(alpha = 0.74f), Offset(left, top), Size(side, side), CornerRadius(corner, corner))
    val half = side * 0.5f
    val centerX = left + half
    val centerY = top + half
    val scale = half / engine.minimapRange.coerceAtLeast(1f)
    val coreX = engine.renderCoreX
    val coreY = engine.renderCoreY
    val dot = frame.u(1.6f)
    clipRect(left, top, left + side, top + side) {
        // Range rings at a third and two thirds.
        drawCircle(Kk.Bone.copy(alpha = 0.06f), half * 0.66f, Offset(centerX, centerY), style = kkStroke(1f))
        drawCircle(Kk.Bone.copy(alpha = 0.05f), half * 0.33f, Offset(centerX, centerY), style = kkStroke(1f))
        val obstacles = engine.obstacles
        for (index in obstacles.indices) {
            val obstacle = obstacles[index]
            val x = centerX + (obstacle.x - coreX) * scale
            val y = centerY + (obstacle.y - coreY) * scale
            val radius = max(dot * 0.9f, obstacle.radius * scale)
            if (x < left - radius || x > left + side + radius || y < top - radius || y > top + side + radius) continue
            when (obstacle.kind) {
                ObstacleKind.ASTEROID -> drawCircle(Kk.Bone.copy(alpha = 0.32f), radius, Offset(x, y))
                ObstacleKind.UNSTABLE -> drawCircle(roles.heat.copy(alpha = 0.7f), radius, Offset(x, y))
                ObstacleKind.BUMPER -> drawCircle(roles.pol.copy(alpha = 0.8f), radius, Offset(x, y), style = kkStroke(max(1f, dot * 0.6f)))
            }
        }
        val pickups = engine.pickups
        for (index in pickups.indices) {
            val pickup = pickups[index]
            if (pickup.type != PickupType.KEY && pickup.type != PickupType.RELIC) continue
            val color = if (pickup.type == PickupType.KEY) roles.you else Kk.RLegend
            drawPinnedMark(centerX, centerY, half, (pickup.x - coreX) * scale, (pickup.y - coreY) * scale, dot * 1.6f, color, filled = true)
        }
        for (index in points.indices) {
            val point = points[index]
            drawPinnedMark(centerX, centerY, half, (point.x - coreX) * scale, (point.y - coreY) * scale, dot * 2.2f, roles.you,
                filled = point.active)
        }
        engine.totem?.let { totem ->
            drawPinnedMark(centerX, centerY, half, (totem.x - coreX) * scale, (totem.y - coreY) * scale, dot * 2f, roles.you, filled = true)
        }
        val enemies = engine.enemies
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.dead) continue
            val dx = (enemy.x - coreX) * scale
            val dy = (enemy.y - coreY) * scale
            when (enemy.type) {
                EnemyType.HERALD, EnemyType.ARCHITECT -> {
                    val (x, y) = pinned(centerX, centerY, half, dx, dy, dot * 3f)
                    drawCircle(roles.threat, dot * 3f, Offset(x, y))
                    drawCircle(Kk.Bone.copy(alpha = 0.8f), dot * 3f, Offset(x, y), style = kkStroke(1f))
                }
                EnemyType.ELITE -> {
                    val (x, y) = pinned(centerX, centerY, half, dx, dy, dot * 2.4f)
                    val aspect = EnemyAffinity.aspect(enemy.affinityBits)
                    val color = if (aspect in roles.elements.indices) roles.elements[aspect] else roles.threat
                    val reach = dot * 2.4f
                    rotate(45f, Offset(x, y)) {
                        drawRect(color, Offset(x - reach * 0.7f, y - reach * 0.7f), Size(reach * 1.4f, reach * 1.4f))
                    }
                }
                else -> {
                    val x = centerX + dx
                    val y = centerY + dy
                    if (abs(dx) > half || abs(dy) > half) continue
                    drawCircle(roles.threat.copy(alpha = 0.85f), dot, Offset(x, y))
                }
            }
        }
        // The Core and its heading.
        val speed = sqrt(engine.velocityX * engine.velocityX + engine.velocityY * engine.velocityY)
        if (speed > 20f) {
            val length = frame.u(10f) * (0.4f + 0.6f * speedVisualRatio(speed))
            drawLine(
                roles.you.copy(alpha = 0.9f),
                Offset(centerX, centerY),
                Offset(centerX + engine.velocityX / speed * length, centerY + engine.velocityY / speed * length),
                max(1f, dot),
            )
        }
        drawCircle(roles.you, dot * 1.7f, Offset(centerX, centerY))
    }
    drawRoundRect(Kk.Bone.copy(alpha = 0.22f), Offset(left, top), Size(side, side), CornerRadius(corner, corner), style = kkStroke(1f))
}

/**
 * A mark at ([dx], [dy]) from the centre, pinned inside the rim when it lies beyond the range, so the
 * direction of an objective is always on the map.
 */
private fun DrawScope.drawPinnedMark(
    centerX: Float,
    centerY: Float,
    half: Float,
    dx: Float,
    dy: Float,
    size: Float,
    color: Color,
    filled: Boolean,
) {
    val (x, y) = pinned(centerX, centerY, half, dx, dy, size)
    if (filled) {
        drawRect(color, Offset(x - size * 0.5f, y - size * 0.5f), Size(size, size))
    } else {
        drawRect(color, Offset(x - size * 0.5f, y - size * 0.5f), Size(size, size), style = kkStroke(1f))
    }
}

/** ([dx], [dy]) from the centre, scaled back onto the square inset by [inset] when it lies beyond it. */
private fun pinned(centerX: Float, centerY: Float, half: Float, dx: Float, dy: Float, inset: Float): Offset {
    val limit = max(1f, half - inset)
    val reach = max(abs(dx), abs(dy))
    if (reach <= limit) return Offset(centerX + dx, centerY + dy)
    val factor = limit / reach
    return Offset(centerX + dx * factor, centerY + dy * factor)
}
