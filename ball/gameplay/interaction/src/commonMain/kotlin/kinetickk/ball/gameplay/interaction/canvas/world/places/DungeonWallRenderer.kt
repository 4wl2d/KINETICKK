// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.render.DungeonProjection
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStrokeWidth
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.kkFillTilePx
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.positiveModulo
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/*
 * A dungeon's walls with depth: every slab casts a shadow down and right onto the floor, shows its lit top and
 * a darker face along its lower edge (both inside the collision outline, so nothing drawn reaches past a wall), keeps
 * a bone rim with a light top edge and a seam along its crest. Sconces burn at the rooms' corners and wall middles in
 * the room's mood. Doors: an open doorway is a lit threshold (volt for the player's next way); a sealed one slams its
 * two panels shut from the jambs when its room seals, flashes, then holds a crackling threat barrier while the room's
 * garrison lives; a locked one is hatched bone. Allocation-free: shapes are the cached slab paths and per-wall rects.
 */

/** The slab's face band (inside its outline) and its shadow's offset (world units). */
private const val FACE_DEPTH = 14f
private const val SHADOW_X = 11f
private const val SHADOW_Y = 18f

/** A sconce sits this far inside a wall line; its pool spans this far. */
private const val SCONCE_INSET = 34f
private const val SCONCE_POOL = 140f

/** Doors slam shut over this long after the seal, then flash for [SLAM_FLASH_SECONDS]. */
private const val SLAM_SECONDS = 0.22f
private const val SLAM_FLASH_SECONDS = 0.35f

/** The cached [slabs] of a structure at [center] with shadow, face, lit top, rim and top highlight. */
internal fun DrawScope.drawDungeonSlabs(slabs: Path, center: Offset, accent: Color) {
    translate(center.x + SHADOW_X, center.y + SHADOW_Y) { drawPath(slabs, Kk.Ink, alpha = 0.5f) }
    translate(center.x, center.y) {
        // The rim's outer half shows; the face fills the outline and the lit top covers all but its lower band.
        drawPath(slabs, kkMix(Kk.Bone, accent, 0.3f), style = kkStroke(KkStrokeWidth.Icon))
        clipPath(slabs) {
            drawPath(slabs, kkMix(Kk.Ink1, accent, 0.10f))
            translate(0f, -FACE_DEPTH) {
                drawPath(slabs, Kk.Bone, alpha = 0.16f, style = kkStroke(KkStrokeWidth.Icon))
                drawPath(slabs, kkMix(Kk.Ink3, accent, 0.06f))
            }
        }
    }
}

/** The crest seam of a solid piece from [a] to [b] (a pillar's cap ring), lifted onto the lit top. */
internal fun DrawScope.drawSlabCrest(a: Offset, b: Offset, halfThickness: Float, accent: Color) {
    val color = kkMix(Kk.Ink4, accent, 0.18f)
    val lift = FACE_DEPTH * 0.5f
    if (a.x == b.x && a.y == b.y) {
        drawCircle(color, halfThickness * 0.45f, Offset(a.x, a.y - lift), alpha = 0.8f, style = kkStroke(KkStrokeWidth.Icon))
    } else {
        drawLine(color, Offset(a.x, a.y - lift), Offset(b.x, b.y - lift), (halfThickness * 0.3f).coerceAtLeast(2f), alpha = 0.8f)
    }
}

/**
 * The sconces of every room of [structure]: the inner corners and the middles of sides with no door at their middle,
 * each a pool on the floor and a bright nub, breathing out of step in the room's mood.
 */
internal fun DrawScope.drawDungeonSconces(
    template: StructureTemplate,
    structure: StructureProjection,
    dungeon: DungeonProjection?,
    center: Offset,
    time: Float,
    roles: KkRolePalette,
) {
    val accent = structureAccent(template, roles)
    val style = template.id.dungeonStyle
    val rotation = structure.rotation
    val inset = template.wallHalfThickness + SCONCE_INSET
    for (room in template.rooms.indices) {
        val spec = template.rooms[room]
        val left = center.x + roomLeft(spec, rotation)
        val top = center.y + roomTop(spec, rotation)
        val right = center.x + roomRight(spec, rotation)
        val bottom = center.y + roomBottom(spec, rotation)
        if (right < -SCONCE_POOL || bottom < -SCONCE_POOL || left > size.width + SCONCE_POOL || top > size.height + SCONCE_POOL) continue
        val mood = roomMood(structure, dungeon, room)
        val light = roomLight(mood, accent, roles)
        // A sconce lights its own room only: the walls hold its pool.
        clipRect(left, top, right, bottom) {
            for (slot in 0 until SCONCE_SLOTS) {
                if (!sconceAt(template, spec, slot, inset)) continue
                val at = Offset(center.x + rotateQuarterX(sconceX, sconceY, rotation), center.y + rotateQuarterY(sconceX, sconceY, rotation))
                val breath = lightBreath(style, mood, time, (room * 8 + slot) * 0.13f)
                drawLightPool(at, SCONCE_POOL, light, 0.07f + 0.08f * breath)
                drawCircle(light, 9f, at, alpha = 0.25f + 0.25f * breath, style = kkStroke(KkStrokeWidth.Line2))
                drawCircle(kkMix(light, Kk.Bone, 0.5f), 4.5f, at, alpha = 0.7f + 0.3f * breath)
            }
        }
    }
}

private const val SCONCE_SLOTS = 8

/** The last sconce point [sconceAt] found (template space); draw-thread scratch. */
private var sconceX = 0f
private var sconceY = 0f

/** The sconce point of slot [slot] of [room], written to [sconceX]/[sconceY]; false when the slot holds none. */
private fun sconceAt(template: StructureTemplate, room: StructureRoom, slot: Int, inset: Float): Boolean {
    val left = room.minX + inset
    val right = room.maxX - inset
    val top = room.minY + inset
    val bottom = room.maxY - inset
    val midX = (room.minX + room.maxX) * 0.5f
    val midY = (room.minY + room.maxY) * 0.5f
    when (slot) {
        0 -> { sconceX = left; sconceY = top }
        1 -> { sconceX = right; sconceY = top }
        2 -> { sconceX = right; sconceY = bottom }
        3 -> { sconceX = left; sconceY = bottom }
        else -> {
            // A side middle only on a long side, and never in a doorway.
            val side = slot - 4
            val horizontal = side % 2 == 0
            val length = if (horizontal) room.maxX - room.minX else room.maxY - room.minY
            if (length < MIDDLE_SCONCE_SIDE) return false
            val line = when (side) {
                0 -> room.minY
                1 -> room.maxX
                2 -> room.maxY
                else -> room.minX
            }
            val along = if (horizontal) midX else midY
            for (index in template.doors.indices) {
                val door = template.doors[index]
                val axis = if (horizontal) WallAxis.HORIZONTAL else WallAxis.VERTICAL
                if (door.axis == axis && door.line == line && abs(door.center - along) < door.width * 0.5f + SCONCE_INSET) return false
            }
            when (side) {
                0 -> { sconceX = midX; sconceY = top }
                1 -> { sconceX = right; sconceY = midY }
                2 -> { sconceX = midX; sconceY = bottom }
                else -> { sconceX = left; sconceY = midY }
            }
        }
    }
    return true
}

/** Sides shorter than this keep their corners' sconces only. */
private const val MIDDLE_SCONCE_SIDE = 900f

/**
 * An open doorway from [a] to [b]: a threshold light spilling both ways and two dashes across its gap, at 20–42 % and
 * 58–80 % of it, in [color] (volt for the player's next way).
 */
internal fun DrawScope.drawDungeonOpenDoor(a: Offset, b: Offset, color: Color, next: Boolean, time: Float) {
    val mid = Offset((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
    val span = abs(b.x - a.x) + abs(b.y - a.y)
    if (next) drawLightPool(mid, span * 0.55f, color, 0.10f + 0.05f * sin(time * 3f))
    val dx = b.x - a.x
    val dy = b.y - a.y
    drawLine(color, Offset(a.x + dx * 0.2f, a.y + dy * 0.2f), Offset(a.x + dx * 0.42f, a.y + dy * 0.42f), 3f)
    drawLine(color, Offset(a.x + dx * 0.58f, a.y + dy * 0.58f), Offset(a.x + dx * 0.8f, a.y + dy * 0.8f), 3f)
}

/**
 * A sealed doorway from [a] to [b]: the capsule's slab rect. Live (its room's garrison fights): the two panels slam in
 * from the jambs over [SLAM_SECONDS] from [sealedAt], flash, then a threat barrier crackles across it; locked: bone
 * hatch with a bone edge.
 */
internal fun DrawScope.drawDungeonSealedDoor(
    a: Offset,
    b: Offset,
    halfThickness: Float,
    live: Boolean,
    sealedAt: Float,
    time: Float,
    roles: KkRolePalette,
) {
    val left = min(a.x, b.x) - halfThickness
    val top = min(a.y, b.y) - halfThickness
    val width = abs(b.x - a.x) + halfThickness * 2f
    val height = abs(b.y - a.y) + halfThickness * 2f
    if (!live) {
        drawRect(Kk.Ink3, Offset(left, top), Size(width, height))
        drawRect(kkFillBrush(KkFillKind.HATCH, Kk.Bone.copy(alpha = 0.55f)), Offset(left, top), Size(width, height))
        drawRect(Kk.Bone.copy(alpha = 0.6f), Offset(left, top), Size(width, height), style = kkStroke(KkStrokeWidth.Icon))
        return
    }
    val threat = roles.threat
    val since = if (sealedAt >= 0f) time - sealedAt else Float.MAX_VALUE
    val closing = (since / SLAM_SECONDS).coerceIn(0f, 1f)
    val shut = KkEase.Snap.transform(closing)
    val horizontal = width >= height
    if (closing < 1f) {
        // Two panels sliding in from the jambs, the gap between them closing.
        val half = (if (horizontal) width else height) * 0.5f * shut
        for (panel in 0 until 2) {
            val x = if (horizontal && panel == 1) left + width - half else left
            val y = if (!horizontal && panel == 1) top + height - half else top
            val panelSize = if (horizontal) Size(half, height) else Size(width, half)
            drawRect(Kk.Ink3, Offset(x, y), panelSize)
            drawRect(kkFillBrush(KkFillKind.HATCH, Kk.Bone.copy(alpha = 0.4f)), Offset(x, y), panelSize)
            drawRect(threat, Offset(x, y), panelSize, style = kkStroke(KkStrokeWidth.Icon))
        }
        return
    }
    drawRect(Kk.Ink3, Offset(left, top), Size(width, height))
    drawRect(kkFillBrush(KkFillKind.HATCH, Kk.Bone.copy(alpha = 0.4f)), Offset(left, top), Size(width, height))
    drawBarrierStripes(left, top, width, height, threat, time)
    val pulse = 0.5f + 0.5f * sin(time * 6f)
    drawRect(threat, Offset(left - 5f, top - 5f), Size(width + 10f, height + 10f), alpha = 0.10f + 0.14f * pulse, style = kkStroke(10f))
    drawRect(threat, Offset(left, top), Size(width, height), style = kkStroke(KkStrokeWidth.Icon))
    // The barrier's seam flickers along the doorway.
    val flicker = 0.45f + 0.55f * sin(time * 31f + left * 0.01f).coerceAtLeast(0f)
    val seam = kkMix(threat, Kk.Bone, 0.55f)
    if (horizontal) drawLine(seam, Offset(left, top + height * 0.5f), Offset(left + width, top + height * 0.5f), 2f, alpha = flicker)
    else drawLine(seam, Offset(left + width * 0.5f, top), Offset(left + width * 0.5f, top + height), 2f, alpha = flicker)
    val flash = 1f - ((since - SLAM_SECONDS) / SLAM_FLASH_SECONDS).coerceIn(0f, 1f)
    if (flash > 0f) drawRect(kkMix(threat, Kk.Bone, 0.6f), Offset(left - 6f, top - 6f), Size(width + 12f, height + 12f), alpha = 0.7f * flash)
}

/** The barrier's threat stripes over the door rect, scrolling one tile per loop of the stripes fill. */
private fun DrawScope.drawBarrierStripes(left: Float, top: Float, width: Float, height: Float, threat: Color, time: Float) {
    val kind = KkFillKind.STRIPES
    val brush = kkFillBrush(kind, threat.copy(alpha = 0.28f))
    val shift = positiveModulo(time / kind.loopSeconds, 1f) * kkFillTilePx(kind)
    clipRect(left, top, left + width, top + height) {
        translate(shift, 0f) { drawRect(brush, Offset(left - shift, top), Size(width, height)) }
    }
}
