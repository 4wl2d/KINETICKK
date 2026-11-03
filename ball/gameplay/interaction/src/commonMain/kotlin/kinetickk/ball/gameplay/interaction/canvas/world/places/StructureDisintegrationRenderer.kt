// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.fx.StructureDisintegrationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.*
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * A spent structure shattering over its disintegration (the FX reducer's StructureDisintegrationProjection, never read
 * back): its floor plates fade, its slabs flash with a brightening rim, then every solid wall cracks into up to four
 * shards that drift outward from the centre, spin and fade. Floors and slabs come from the cached StructurePaths; the
 * shards are lines computed from the template walls with an integer hash, so a frame allocates nothing and builds no
 * path. Shards take FxPool.RUBBLE operations and stop for the frame when it refuses; the fading floor and the flash
 * always draw. The shockwave and burst come from the simulation's own cues.
 */

/** The flash of the slabs lasts this share of the disintegration; shards start at [SHARD_START]. */
private const val CRACK_END = 0.18f
private const val SHARD_START = 0.10f

/** The farthest a shard drifts (world units), used to keep a shattering structure drawn while any shard is in view. */
private const val SHARD_MAX_DRIFT = 240f
private const val SLAB_REACH = 80f
private const val VIEW_MARGIN = 40f

/** A wall is cut into one shard per this many units, at most [MAX_SHARDS_PER_WALL], with an 8 % gap between them. */
private const val SHARD_LENGTH = 120f
private const val MAX_SHARDS_PER_WALL = 4
private const val SHARD_GAP = 0.08f

/** Draws every live disintegration in view, after the standing structures' walls and before the obstacles. */
internal fun DrawScope.drawStructureDisintegrations(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val disintegrations = visualFx.disintegrations
    if (disintegrations.isEmpty()) return
    for (index in disintegrations.indices) {
        val disintegration = disintegrations[index]
        val template = engine.content.structures.template(disintegration.template)
        val center = world(engine, disintegration.x, disintegration.y, shakeX, shakeY)
        if (!isOnScreen(center, template.circumradius + SLAB_REACH + SHARD_MAX_DRIFT + VIEW_MARGIN)) continue
        val progress = (1f - disintegration.life / disintegration.maxLife).coerceIn(0f, 1f)
        val accent = structureAccent(template, roles)
        drawFadingFloor(template, disintegration.rotation, center, progress, roles, accent)
        if (progress < CRACK_END) drawCrackingSlabs(template, disintegration.rotation, center, progress, accent)
        if (progress >= SHARD_START) drawShards(template, disintegration, center, progress, accent)
    }
}

/** The floor plates (or the Doodle's planet) fading with the square of the time left. */
private fun DrawScope.drawFadingFloor(
    template: StructureTemplate,
    rotation: Int,
    center: Offset,
    progress: Float,
    roles: KkRolePalette,
    accent: androidx.compose.ui.graphics.Color,
) {
    val fade = (1f - progress) * (1f - progress)
    if (fade <= 0.001f) return
    if (template.roomWalls) {
        translate(center.x, center.y) { drawPath(StructurePaths.floor(template, rotation), structureFloorColor(template, roles), alpha = fade) }
        return
    }
    val room = template.rooms[template.finalRoom]
    val localX = (room.minX + room.maxX) * 0.5f
    val localY = (room.minY + room.maxY) * 0.5f
    val planet = Offset(center.x + rotateQuarterX(localX, localY, rotation), center.y + rotateQuarterY(localX, localY, rotation))
    drawCircle(accent.copy(alpha = 0.06f * fade), DOODLE_PLANET_RADIUS, planet)
}

/** The cached slabs in ink with a rim brightening from bone toward the accent: the structure flashes before it cracks. */
private fun DrawScope.drawCrackingSlabs(
    template: StructureTemplate,
    rotation: Int,
    center: Offset,
    progress: Float,
    accent: androidx.compose.ui.graphics.Color,
) {
    val slabs = StructurePaths.walls(template, rotation)
    val crack = progress / CRACK_END
    translate(center.x, center.y) {
        drawPath(slabs, Kk.Ink3)
        drawPath(slabs, kkMix(Kk.Bone, accent, 0.5f), style = kkStroke(2f + 2f * crack))
    }
}

/**
 * The solid walls as shards: each wall cut into pieces that drift outward along the direction from the centre to their
 * midpoint (the wall's normal for a piece at the centre), slide, spin about their midpoint, shrink and fade; a bright
 * rim lingers on each for the first half. Stops at the first piece [FxPool.RUBBLE] refuses.
 */
private fun DrawScope.drawShards(
    template: StructureTemplate,
    disintegration: StructureDisintegrationProjection,
    center: Offset,
    progress: Float,
    accent: androidx.compose.ui.graphics.Color,
) {
    val q = ((progress - SHARD_START) / (1f - SHARD_START)).coerceIn(0f, 1f)
    val ease = 1f - (1f - q) * (1f - q)
    val fade = (1f - q).pow(1.5f)
    val rim = kkMix(Kk.Bone, accent, 0.3f)
    val rotation = disintegration.rotation
    val walls = template.walls
    for (wallIndex in walls.indices) {
        val wall = walls[wallIndex]
        if (wall.isDoor) continue
        val ax = rotateQuarterX(wall.ax, wall.ay, rotation)
        val ay = rotateQuarterY(wall.ax, wall.ay, rotation)
        val bx = rotateQuarterX(wall.bx, wall.by, rotation)
        val by = rotateQuarterY(wall.bx, wall.by, rotation)
        val length = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
        val pieces = ceil(length / SHARD_LENGTH).toInt().coerceIn(1, MAX_SHARDS_PER_WALL)
        // A pillar (zero length) keeps the x axis as its direction.
        val ux = if (length > 0.001f) (bx - ax) / length else 1f
        val uy = if (length > 0.001f) (by - ay) / length else 0f
        val width = 2f * wall.halfThickness * (1f - 0.4f * q)
        for (piece in 0 until pieces) {
            if (!FxDrawBudget.take(FxPool.RUBBLE, 1, signature = false)) return
            val seed = disintegration.seed xor (wallIndex * 31 + piece)
            val start = (piece + SHARD_GAP * 0.5f) / pieces
            val end = (piece + 1 - SHARD_GAP * 0.5f) / pieces
            val midX = ax + (bx - ax) * (start + end) * 0.5f
            val midY = ay + (by - ay) * (start + end) * 0.5f
            val half = length * (end - start) * 0.5f * (1f - 0.35f * q)
            val distance = sqrt(midX * midX + midY * midY)
            val dirX = if (distance > 1f) midX / distance else -uy
            val dirY = if (distance > 1f) midY / distance else ux
            val drift = ease * (70f + 160f * fxHash(seed, 1))
            val slide = ease * (fxHash(seed, 2) - 0.5f) * 60f
            val spin = ease * (fxHash(seed, 3) - 0.5f) * 1.4f
            val cx = center.x + midX + dirX * drift + ux * slide
            val cy = center.y + midY + dirY * drift + uy * slide
            val tx = ux * cos(spin) - uy * sin(spin)
            val ty = ux * sin(spin) + uy * cos(spin)
            val a = Offset(cx - tx * half, cy - ty * half)
            val b = Offset(cx + tx * half, cy + ty * half)
            drawLine(Kk.Ink3.copy(alpha = fade), a, b, strokeWidth = max(1f, width), cap = StrokeCap.Butt)
            if (q < 0.5f) drawLine(rim.copy(alpha = 1f - 2f * q), a, b, strokeWidth = 1.5f, cap = StrokeCap.Butt)
        }
    }
}
