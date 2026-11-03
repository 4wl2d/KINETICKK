// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.nucleus.render.DungeonProjection
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * A dungeon's floor beyond its plate: the template's floor (deck plates, ice lattice, resonance rings, roots, grates,
 * sanctum tiles) in neutral lines with its accent inlay, the dark band where floor meets wall, a light pool per room
 * tinted by the room's mood (the element's hue while dormant, threat while sealed, warm bone once cleared) and the
 * runes along the walls in three groups that pulse out of step, lit for good once their room is cleared. Shapes come
 * from the content template, cached per template, rotation and kind in DungeonFloorPaths; nothing allocates per frame
 * once they are built.
 */

/** Ambient occlusion where floor meets wall: two ink strokes on the room outlines (half under the slabs). */
private const val OCCLUSION_WIDE = 150f
private const val OCCLUSION_NEAR = 64f

/** The room light pool spans this share of the room's shorter side. */
private const val ROOM_POOL_SHARE = 0.72f

/** Runes sit this far inside a wall line, this far apart, and are this large (world units). */
internal const val RUNE_INSET = 50f
internal const val RUNE_SPACING = 150f
internal const val RUNE_SIZE = 7f

/**
 * The template's floor detail, room lights and wall runes of [structure] centred at [center], over its floor plate and
 * under its walls: the cleared rooms' runes burn steady, the sealed room's pulse in threat.
 */
internal fun DrawScope.drawDungeonFloor(
    template: StructureTemplate,
    structure: StructureProjection,
    dungeon: DungeonProjection?,
    center: Offset,
    time: Float,
    roles: KkRolePalette,
) {
    val rotation = structure.rotation
    val style = template.id.dungeonStyle
    val accent = structureAccent(template, roles)
    translate(center.x, center.y) {
        drawPath(DungeonFloorPaths.detail(template, rotation), kkMix(Kk.Ink4, accent, 0.22f), alpha = 0.55f, style = kkStroke(1.5f))
        val inlay = DungeonFloorPaths.inlay(template, rotation)
        if (style.floor == DungeonFloor.GRATE) {
            // The furnace channels glow: a wide soft stroke under a hot thin one.
            drawPath(inlay, accent, alpha = 0.07f + 0.03f * sin(time * 1.3f), style = kkStroke(26f))
            drawPath(inlay, kkMix(accent, Kk.Bone, 0.35f), alpha = 0.42f, style = kkStroke(3f))
        } else {
            drawPath(inlay, accent, alpha = 0.20f, style = kkStroke(2f))
        }
        val rooms = StructurePaths.floor(template, rotation)
        drawPath(rooms, Kk.Ink, alpha = 0.22f, style = kkStroke(OCCLUSION_WIDE))
        drawPath(rooms, Kk.Ink, alpha = 0.30f, style = kkStroke(OCCLUSION_NEAR))
    }
    for (room in template.rooms.indices) {
        val spec = template.rooms[room]
        val mood = roomMood(structure, dungeon, room)
        val light = roomLight(mood, accent, roles)
        val breath = lightBreath(style, mood, time, room * 0.37f)
        val peak = when (mood) {
            RoomMood.DORMANT -> 0.035f + 0.015f * breath
            RoomMood.SEALED -> 0.07f + 0.06f * breath
            RoomMood.GUARDIAN -> 0.12f + 0.08f * breath
            RoomMood.CLEARED -> 0.09f
        }
        val shorter = min(spec.maxX - spec.minX, spec.maxY - spec.minY)
        // Light stays inside its room: the walls hold it.
        clipRect(center.x + roomLeft(spec, rotation), center.y + roomTop(spec, rotation), center.x + roomRight(spec, rotation),
            center.y + roomBottom(spec, rotation)) {
            drawLightPool(roomCenter(center, spec, rotation), shorter * ROOM_POOL_SHARE, light, peak)
            if (mood == RoomMood.SEALED || mood == RoomMood.GUARDIAN) drawRoomAlarm(spec, center, rotation, light, breath)
        }
    }
    drawRunes(template, structure, dungeon, center, time, roles, accent)
}

/** A sealed room's alarm: its walls' feet glow in threat on the alarm's beat (drawn inside its room's clip). */
private fun DrawScope.drawRoomAlarm(spec: StructureRoom, center: Offset, rotation: Int, light: Color, beat: Float) {
    val left = center.x + roomLeft(spec, rotation)
    val top = center.y + roomTop(spec, rotation)
    val size = Size(center.x + roomRight(spec, rotation) - left, center.y + roomBottom(spec, rotation) - top)
    drawRect(light, Offset(left, top), size, alpha = 0.05f + 0.07f * beat, style = kkStroke(ALARM_WIDE))
    drawRect(light, Offset(left, top), size, alpha = 0.08f + 0.12f * beat, style = kkStroke(ALARM_NEAR))
}

/** The alarm's two glow strokes on a sealed room's outline (world units). */
private const val ALARM_WIDE = 120f
private const val ALARM_NEAR = 44f

/** The three rune groups: each room's runes in its mood, a group's phase a third of a turn from the next. */
private fun DrawScope.drawRunes(
    template: StructureTemplate,
    structure: StructureProjection,
    dungeon: DungeonProjection?,
    center: Offset,
    time: Float,
    roles: KkRolePalette,
    accent: Color,
) {
    val rotation = structure.rotation
    // One draw per group for the whole structure in the element's hue; a sealed or cleared room's runes burn over them.
    translate(center.x, center.y) {
        for (group in 0 until DungeonFloorPaths.RUNE_GROUPS) {
            val glow = 0.5f + 0.5f * sin(time * 1.1f + group * 2.1f)
            drawPath(DungeonFloorPaths.runes(template, rotation, group), accent, alpha = 0.10f + 0.12f * glow)
        }
    }
    for (room in template.rooms.indices) {
        val mood = roomMood(structure, dungeon, room)
        if (mood == RoomMood.DORMANT) continue
        val spec = template.rooms[room]
        val color = roomLight(mood, accent, roles)
        val alpha = if (mood == RoomMood.CLEARED) 0.62f else 0.35f + 0.45f * lightBreath(template.id.dungeonStyle, mood, time, 0f)
        drawRoomRunes(spec, template.wallHalfThickness, center, rotation, color, alpha)
    }
}

/**
 * The runes of one lit room drawn as single dots along its walls (no path: a room's runes are at most a few dozen
 * circles), so a cleared or sealed room's runes burn over the dormant groups.
 */
private fun DrawScope.drawRoomRunes(spec: StructureRoom, wall: Float, center: Offset, rotation: Int, color: Color, alpha: Float) {
    forEachRune(spec, wall) { x, y, _ ->
        drawCircle(color, RUNE_SIZE * 0.62f, Offset(center.x + rotateQuarterX(x, y, rotation), center.y + rotateQuarterY(x, y, rotation)), alpha)
    }
}

/**
 * Every rune point of [room] (template space): along each side [RUNE_INSET] inside the wall line, [RUNE_SPACING]
 * apart, starting half a spacing from a corner; [action] gets the point and the rune's index around the room.
 */
internal inline fun forEachRune(room: StructureRoom, wall: Float, action: (Float, Float, Int) -> Unit) {
    val inset = RUNE_INSET + wall
    val left = room.minX + inset
    val right = room.maxX - inset
    val top = room.minY + inset
    val bottom = room.maxY - inset
    var index = 0
    var x = left + RUNE_SPACING * 0.5f
    while (x < right) {
        action(x, top, index++)
        action(x, bottom, index++)
        x += RUNE_SPACING
    }
    var y = top + RUNE_SPACING * 0.5f
    while (y < bottom) {
        action(left, y, index++)
        action(right, y, index++)
        y += RUNE_SPACING
    }
}

/**
 * Dungeon floor shapes in object-local units (origin at the structure centre), built from the content template turned
 * by its rotation: the template's neutral floor lines, its accent inlay and its wall runes in [RUNE_GROUPS] groups.
 * Keyed by template, rotation and kind; a bounded round robin (a window holds at most nine structures) whose evicted
 * paths are reset and reused. Draw-thread confined.
 */
internal object DungeonFloorPaths {
    const val RUNE_GROUPS = 3
    private const val CAPACITY = 48
    private const val DETAIL = 0
    private const val INLAY = 1
    private const val RUNES = 2
    private val keys = IntArray(CAPACITY) { -1 }
    private val sources = arrayOfNulls<StructureTemplate>(CAPACITY)
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private var next = 0

    /** Paths built so far: a warm frame builds none. */
    var builds = 0
        private set

    fun detail(template: StructureTemplate, rotation: Int): Path = get(template, rotation, DETAIL)

    fun inlay(template: StructureTemplate, rotation: Int): Path = get(template, rotation, INLAY)

    fun runes(template: StructureTemplate, rotation: Int, group: Int): Path = get(template, rotation, RUNES + group)

    private fun get(template: StructureTemplate, rotation: Int, kind: Int): Path {
        val key = (template.id.ordinal shl 8) or ((rotation and 3) shl 5) or kind
        for (index in 0 until CAPACITY) {
            if (keys[index] == key && sources[index] === template) return paths[index]!!
        }
        val path = paths[next]?.also { it.reset() } ?: Path()
        val turned = TurnedPath(path, rotation and 3)
        for (room in template.rooms.indices) {
            val spec = template.rooms[room]
            when (kind) {
                DETAIL -> buildDetail(turned, spec, template.id.dungeonStyle)
                INLAY -> buildInlay(turned, spec, template, room)
                else -> buildRunes(turned, spec, template.wallHalfThickness, kind - RUNES)
            }
        }
        keys[next] = key
        sources[next] = template
        paths[next] = path
        next = (next + 1) % CAPACITY
        builds++
        return path
    }

    /** The template's neutral floor lines in [room]: plates, a lattice, spokes, a tile grid or grates. */
    private fun buildDetail(path: TurnedPath, room: StructureRoom, style: DungeonStyle) {
        val tile = style.tile
        when (style.floor) {
            DungeonFloor.ICE -> lattice(path, room, tile)
            DungeonFloor.RINGS -> {
                val cx = (room.minX + room.maxX) * 0.5f
                val cy = (room.minY + room.maxY) * 0.5f
                val reach = min(room.maxX - room.minX, room.maxY - room.minY) * 0.46f
                for (spoke in 0 until 12) {
                    val angle = spoke * (CanvasMath.TAU / 12f)
                    path.line(cx + cos(angle) * tile * 0.6f, cy + sin(angle) * tile * 0.6f, cx + cos(angle) * reach, cy + sin(angle) * reach)
                }
            }
            DungeonFloor.GRATE -> {
                val alongX = room.maxX - room.minX >= room.maxY - room.minY
                if (alongX) stripes(path, room.minY, room.maxY, tile) { y -> path.line(room.minX, y, room.maxX, y) }
                else stripes(path, room.minX, room.maxX, tile) { x -> path.line(x, room.minY, x, room.maxY) }
            }
            DungeonFloor.DECK, DungeonFloor.ROOTS, DungeonFloor.SANCTUM -> {
                stripes(path, room.minX, room.maxX, tile) { x -> path.line(x, room.minY, x, room.maxY) }
                stripes(path, room.minY, room.maxY, tile) { y -> path.line(room.minX, y, room.maxX, y) }
            }
        }
    }

    /**
     * The accent inlay of room [index]: the station's corner deck markings, the vault's frost stars, the spire's
     * resonance rings, the labyrinth's roots, the foundry's two channels and the reliquary's rune circle.
     */
    private fun buildInlay(path: TurnedPath, room: StructureRoom, template: StructureTemplate, index: Int) {
        val cx = (room.minX + room.maxX) * 0.5f
        val cy = (room.minY + room.maxY) * 0.5f
        val width = room.maxX - room.minX
        val height = room.maxY - room.minY
        val shorter = min(width, height)
        val seed = template.id.ordinal * 131 + index * 17
        when (template.id.dungeonStyle.floor) {
            DungeonFloor.DECK -> {
                val inset = template.wallHalfThickness + 46f
                val arm = 70f
                for (corner in 0 until 4) {
                    val x = if (corner % 2 == 0) room.minX + inset else room.maxX - inset
                    val y = if (corner < 2) room.minY + inset else room.maxY - inset
                    val sx = if (corner % 2 == 0) 1f else -1f
                    val sy = if (corner < 2) 1f else -1f
                    path.line(x, y + sy * arm, x, y)
                    path.lineTo(x + sx * arm, y)
                }
            }
            DungeonFloor.ICE -> for (star in 0 until 3) {
                val x = room.minX + width * (0.2f + 0.6f * fxHash(seed, star * 2))
                val y = room.minY + height * (0.2f + 0.6f * fxHash(seed, star * 2 + 1))
                val reach = 26f + 22f * fxHash(seed, 9 + star)
                for (spoke in 0 until 3) {
                    val angle = spoke * (CanvasMath.TAU / 6f)
                    path.line(x - cos(angle) * reach, y - sin(angle) * reach, x + cos(angle) * reach, y + sin(angle) * reach)
                }
            }
            DungeonFloor.RINGS -> {
                var radius = template.id.dungeonStyle.tile
                while (radius < shorter * 0.47f) {
                    path.circle(cx, cy, radius)
                    radius += template.id.dungeonStyle.tile
                }
            }
            DungeonFloor.ROOTS -> for (root in 0 until 4) {
                // A root from a wall point toward the room's centre, bending as it goes.
                val side = root % 4
                var x = if (side == 1) room.maxX else if (side == 3) room.minX else room.minX + width * (0.25f + 0.5f * fxHash(seed, root))
                var y = if (side == 0) room.minY else if (side == 2) room.maxY else room.minY + height * (0.25f + 0.5f * fxHash(seed, root))
                var heading = kotlin.math.atan2(cy - y, cx - x)
                path.move(x, y)
                for (segment in 0 until 5) {
                    heading += (fxHash(seed, 20 + root * 8 + segment) - 0.5f) * 1.4f
                    val length = 50f + 50f * fxHash(seed, 60 + root * 8 + segment)
                    x = (x + cos(heading) * length).coerceIn(room.minX, room.maxX)
                    y = (y + sin(heading) * length).coerceIn(room.minY, room.maxY)
                    path.lineTo(x, y)
                }
            }
            DungeonFloor.GRATE -> {
                val alongX = width >= height
                for (channel in 1..2) {
                    if (alongX) {
                        val y = room.minY + height * channel / 3f
                        path.line(room.minX, y, room.maxX, y)
                    } else {
                        val x = room.minX + width * channel / 3f
                        path.line(x, room.minY, x, room.maxY)
                    }
                }
            }
            DungeonFloor.SANCTUM -> {
                val radius = shorter * 0.28f
                path.circle(cx, cy, radius)
                path.circle(cx, cy, radius * 0.72f)
                for (point in 0 until 8) {
                    val from = point * (CanvasMath.TAU / 8f)
                    val to = from + CanvasMath.TAU * 3f / 8f
                    path.line(cx + cos(from) * radius * 0.72f, cy + sin(from) * radius * 0.72f, cx + cos(to) * radius * 0.72f, cy + sin(to) * radius * 0.72f)
                }
            }
        }
    }

    /** Diamond-shaped runes of [group] along [room]'s walls. */
    private fun buildRunes(path: TurnedPath, room: StructureRoom, wall: Float, group: Int) {
        forEachRune(room, wall) { x, y, index ->
            if (index % RUNE_GROUPS != group) return@forEachRune
            path.move(x, y - RUNE_SIZE)
            path.lineTo(x + RUNE_SIZE, y)
            path.lineTo(x, y + RUNE_SIZE)
            path.lineTo(x - RUNE_SIZE, y)
            path.close()
        }
    }

    /** Diagonal lines a [tile] apart both ways, cut to the room. */
    private fun lattice(path: TurnedPath, room: StructureRoom, tile: Float) {
        val width = room.maxX - room.minX
        val height = room.maxY - room.minY
        var offset = tile * 0.5f - height
        while (offset < width) {
            // x - minX = offset + (y - minY): rising to the right; and its mirror.
            clipDiagonal(path, room, offset, rising = true)
            clipDiagonal(path, room, offset, rising = false)
            offset += tile
        }
    }

    private fun clipDiagonal(path: TurnedPath, room: StructureRoom, offset: Float, rising: Boolean) {
        val height = room.maxY - room.minY
        val width = room.maxX - room.minX
        val startY = maxOf(0f, -offset)
        val endY = minOf(height, width - offset)
        if (endY <= startY) return
        val x0 = room.minX + offset + startY
        val x1 = room.minX + offset + endY
        if (rising) path.line(x0, room.minY + startY, x1, room.minY + endY)
        else path.line(x0, room.maxY - startY, x1, room.maxY - endY)
    }

    private inline fun stripes(path: TurnedPath, low: Float, high: Float, tile: Float, line: (Float) -> Unit) {
        var at = low + tile
        while (at < high - tile * 0.5f) {
            line(at)
            at += tile
        }
    }
}

/** A path builder in template space that writes every point turned by a quarter [rotation]. */
private class TurnedPath(private val path: Path, private val rotation: Int) {
    fun move(x: Float, y: Float) = path.moveTo(rotateQuarterX(x, y, rotation), rotateQuarterY(x, y, rotation))

    fun lineTo(x: Float, y: Float) = path.lineTo(rotateQuarterX(x, y, rotation), rotateQuarterY(x, y, rotation))

    fun line(ax: Float, ay: Float, bx: Float, by: Float) {
        move(ax, ay)
        lineTo(bx, by)
    }

    fun close() = path.close()

    fun circle(x: Float, y: Float, radius: Float) {
        val cx = rotateQuarterX(x, y, rotation)
        val cy = rotateQuarterY(x, y, rotation)
        path.addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
    }
}
