// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.STRUCTURE_OUTSIDE
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.interaction.localization.StructureHudText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.ball.gameplay.nucleus.render.StructureRenderProjection
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.TextResource
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.d
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkShear
import kinetickk.foundation.design.kkStroke
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Structures and dungeons in the world layer: room floor plates, wall slabs and doors, the sealed room's brackets, the
 * final room's plates and the scrim outside the dungeon the Core is inside; their entrances as edge markers and on the
 * minimap; on the HUD the dungeon trial panel's label, rules, rewards and sealed-room cell, and the vignette. Ink dominates, bone reads as solid, HATCH as locked, threat only for a live garrison and volt only for the
 * player's next way; no arrow, numeral or text marks a structure. Room and wall shapes come from the run's content
 * template, cached per template, rotation and kind in StructurePaths (never from projection floats, so a rebase keeps
 * them); positions, doors and progress come from the projection. Allocation-free per frame once the paths are cached.
 */

/** A structure is drawn while its footprint, its thickest slab and this margin meet the view (world units). */
private const val VIEW_MARGIN = 40f

/** The thickest slab content accepts (a pillar's half thickness is at most this). */
private const val SLAB_REACH = 80f

/** The floor plate: a hair lighter than the backdrop's ink. */
private val FloorColor = kkMix(Kk.Ink, Kk.Ink1, 0.6f)

/** The scrim's ink at full depth, and the depth inside the footprint at which it is full (world units). */
private const val SCRIM_ALPHA = 0.42f
private const val INTERIOR_FADE = 240f

/** How far the scrim reaches around the dungeon (world units): past any view. */
private const val SCRIM_REACH = 40_000f

/** The sealed room's corner brackets: arm length and inset from the room corner (world units), stroke (dp). */
private const val BRACKET_ARM = 90f
private const val BRACKET_INSET = 36f
private const val BRACKET_STROKE = 3f

/** The Doodle's planet around its one room (the radius of its ring of chords). */
private const val DOODLE_PLANET_RADIUS = 672f

/** The stacked plates promising the final room's reward (the Totem's plates, 66 units wide). */
private const val PROMISE_SCALE = 2.2f

/** An edge marker stands in for an entrance this far off the view at least (world units), like the Totem's. */
private const val ENTRANCE_MARK_MARGIN = 60f

/** No room: the Core is not inside the structure, or the structure is not the dungeon. */
private const val NO_ROOM = -2

/** Room floor plates of the structures in view, under the obstacles: cleared rooms lighter, the Doodle's planet. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawStructureFloors(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val structures = engine.structures.structures
    if (structures.isEmpty()) return
    for (index in structures.indices) {
        val structure = structures[index]
        val template = engine.content.structures.template(structure.template)
        val center = world(engine, structure.x, structure.y, shakeX, shakeY)
        if (!structureInView(center, structure)) continue
        val rotation = structure.rotation
        val rooms = template.rooms
        if (template.id == StructureTemplateId.ARCHITECTS_DOODLE) {
            val room = rooms[template.finalRoom]
            drawCircle(Kk.Bone.copy(alpha = 0.06f), DOODLE_PLANET_RADIUS, roomCenter(center, room, rotation))
        }
        // Rooms without walls (the Doodle's) have no plate: the planet is their floor.
        if (!template.roomWalls) continue
        translate(center.x, center.y) { drawPath(StructurePaths.floor(template, rotation), FloorColor) }
        val cleared = structure.clearedRooms
        for (room in rooms.indices) {
            if (cleared and (1 shl room) == 0) continue
            val spec = rooms[room]
            val left = center.x + roomLeft(spec, rotation)
            val top = center.y + roomTop(spec, rotation)
            drawRect(Kk.Bone.copy(alpha = 0.025f), Offset(left, top),
                Size(center.x + roomRight(spec, rotation) - left, center.y + roomBottom(spec, rotation) - top))
        }
    }
}

/**
 * Wall slabs and doors of the structures in view, right after their floors: each slab a bone-rimmed ink capsule with
 * a darker spine, open doors as two bone dashes (volt for the player's next way), sealed doors as hatched slabs edged
 * in threat while their room's garrison is alive, the sealed room's pulsing threat brackets and the final room's
 * plates until it is cleared (none for a secret template).
 */
internal fun DrawScope.drawStructureWalls(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val projection = engine.structures
    val structures = projection.structures
    if (structures.isEmpty()) return
    val walls = projection.walls
    val dungeon = projection.dungeon
    for (index in structures.indices) {
        val structure = structures[index]
        val template = engine.content.structures.template(structure.template)
        val center = world(engine, structure.x, structure.y, shakeX, shakeY)
        if (!structureInView(center, structure)) continue
        val rotation = structure.rotation
        val slabs = StructurePaths.walls(template, rotation)
        translate(center.x, center.y) {
            // The stroke's outer half is the rim; the fill covers its inner half and the seams where slabs meet.
            drawPath(slabs, Kk.Bone, style = kkStroke(2f))
            drawPath(slabs, Kk.Ink3)
        }
        val current = dungeon != null && dungeon.structureId == structure.id
        val coreRoom = if (current) {
            roomAt(template, rotation, engine.renderCoreX - structure.x, engine.renderCoreY - structure.y)
        } else {
            NO_ROOM
        }
        val engaged = structure.engagedRoom
        for (wallIndex in structure.wallStart until structure.wallEnd) {
            val wall = walls[wallIndex]
            val a = world(engine, wall.ax, wall.ay, shakeX, shakeY)
            val b = world(engine, wall.bx, wall.by, shakeX, shakeY)
            when (wall.kind) {
                WallKind.SOLID -> drawWallSpine(a, b, wall.halfThickness)
                WallKind.DOOR_OPEN -> {
                    val next = current && leadsOnward(template, wall.door, structure.clearedRooms, coreRoom)
                    drawOpenDoor(a, b, if (next) roles.you.copy(alpha = 0.8f) else Kk.Bone.copy(alpha = 0.35f))
                }
                WallKind.DOOR_SEALED -> {
                    val live = engaged >= 0 && doorTouches(template, wall.door, engaged)
                    drawSealedDoor(a, b, wall.halfThickness, if (live) roles.threat else Kk.Bone.copy(alpha = 0.6f))
                }
            }
        }
        if (engaged >= 0 && engaged < template.rooms.size) drawRoomBrackets(center, template.rooms[engaged], rotation, engine.renderElapsed, roles)
        if (!template.secret && structure.clearedRooms and (1 shl template.finalRoom) == 0) {
            drawTotemPlates(roomCenter(center, template.rooms[template.finalRoom], rotation), PROMISE_SCALE, Kk.Bone,
                keyBlock = false, keyIcon = false)
        }
    }
}

/** The ink scrim outside the dungeon's rooms while the Core is in one of them, over the Core and under the singularity. */
internal fun DrawScope.drawStructureScrim(engine: GameplayRenderModel, shakeX: Float, shakeY: Float) {
    val amount = structureInteriorAmount(engine)
    if (amount <= 0.01f) return
    val structure = dungeonStructure(engine.structures) ?: return
    val template = engine.content.structures.template(structure.template)
    val center = world(engine, structure.x, structure.y, shakeX, shakeY)
    translate(center.x, center.y) {
        drawPath(StructurePaths.scrim(template, structure.rotation), Kk.Ink, alpha = SCRIM_ALPHA * amount)
    }
}

/**
 * How far the drawn Core is inside the dungeon (0..1): 0 without a dungeon, for a secret template or while the Core is
 * in none of its rooms (no scrim, no vignette: a Core outside the walls in a notch of an L-shaped footprint is in open
 * space), else its depth inside the footprint over [INTERIOR_FADE], so the scrim fades in on the way in and out on the
 * way out without any presentation memory. Outer doors lie on the footprint's edge and rooms meet edge to edge, so the
 * room test never cuts the fade short on a way the Core can fly.
 */
internal fun structureInteriorAmount(engine: GameplayRenderModel): Float {
    val dungeon = engine.structures.dungeon ?: return 0f
    if (!dungeon.panel) return 0f
    val structure = dungeonStructure(engine.structures) ?: return 0f
    val dx = engine.renderCoreX - structure.x
    val dy = engine.renderCoreY - structure.y
    val depth = min(structure.halfWidth - abs(dx), structure.halfHeight - abs(dy))
    if (depth <= 0f) return 0f
    val template = engine.content.structures.template(structure.template)
    if (roomAt(template, structure.rotation, dx, dy) == STRUCTURE_OUTSIDE) return 0f
    return (depth / INTERIOR_FADE).coerceIn(0f, 1f)
}

/**
 * Edge markers of structure entrances off screen, after the dimension markers: the two nearest to the Core, never the
 * dungeon the Core is in (the window announces at most four).
 */
internal fun DrawScope.collectStructureEdgeMarkers(engine: GameplayRenderModel, batch: EdgeMarkerBatch) {
    val projection = engine.structures
    val signals = projection.signals
    if (signals.isEmpty()) return
    val dungeon = projection.dungeon
    var nearest = -1
    var nearestDistance = Float.MAX_VALUE
    var second = -1
    var secondDistance = Float.MAX_VALUE
    for (index in signals.indices) {
        val signal = signals[index]
        if (dungeon != null && dungeon.structureId == signal.id) continue
        if (isOnScreen(world(engine, signal.x, signal.y, 0f, 0f), ENTRANCE_MARK_MARGIN)) continue
        val dx = signal.x - engine.coreX
        val dy = signal.y - engine.coreY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < nearestDistance) {
            second = nearest
            secondDistance = nearestDistance
            nearest = index
            nearestDistance = distance
        } else if (distance < secondDistance) {
            second = index
            secondDistance = distance
        }
    }
    val scale = engine.worldScale
    if (nearest >= 0) {
        val target = world(engine, signals[nearest].x, signals[nearest].y, 0f, 0f)
        batch.add(target.x * scale, target.y * scale, nearestDistance, EdgeMarkerIcon.STRUCTURE)
    }
    if (second >= 0) {
        val target = world(engine, signals[second].x, signals[second].y, 0f, 0f)
        batch.add(target.x * scale, target.y * scale, secondDistance, EdgeMarkerIcon.STRUCTURE)
    }
}

/**
 * Walls, sealed doors and entrances on the minimap, inside its clip, after the obstacles: walls as faint bone lines,
 * sealed doors firmer (threat while their room's garrison is alive), open doors left out, and each entrance a hollow
 * bone square pinned to the rim when beyond the range.
 */
internal fun DrawScope.drawMinimapStructures(
    engine: GameplayRenderModel,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
    roles: KkRolePalette,
) {
    val projection = engine.structures
    val structures = projection.structures
    val signals = projection.signals
    if (structures.isEmpty() && signals.isEmpty()) return
    val coreX = engine.renderCoreX
    val coreY = engine.renderCoreY
    val walls = projection.walls
    val wallWidth = max(1f, dot * 0.6f)
    val doorWidth = max(1.5f, dot * 0.9f)
    for (index in structures.indices) {
        val structure = structures[index]
        val template = engine.content.structures.template(structure.template)
        val engaged = structure.engagedRoom
        for (wallIndex in structure.wallStart until structure.wallEnd) {
            val wall = walls[wallIndex]
            if (wall.kind == WallKind.DOOR_OPEN) continue
            val ax = centerX + (wall.ax - coreX) * scale
            val ay = centerY + (wall.ay - coreY) * scale
            val bx = centerX + (wall.bx - coreX) * scale
            val by = centerY + (wall.by - coreY) * scale
            // Clipped by the map's clip; only a wall wholly to one side of the square is skipped.
            if (max(ax, bx) < centerX - half || min(ax, bx) > centerX + half || max(ay, by) < centerY - half ||
                min(ay, by) > centerY + half
            ) {
                continue
            }
            if (wall.kind == WallKind.SOLID) {
                drawLine(Kk.Bone.copy(alpha = 0.45f), Offset(ax, ay), Offset(bx, by), wallWidth)
            } else {
                val live = engaged >= 0 && doorTouches(template, wall.door, engaged)
                drawLine(if (live) roles.threat.copy(alpha = 0.9f) else Kk.Bone.copy(alpha = 0.7f), Offset(ax, ay), Offset(bx, by), doorWidth)
            }
        }
    }
    val mark = dot * 2.4f
    for (index in signals.indices) {
        val signal = signals[index]
        val at = pinned(centerX, centerY, half, (signal.x - coreX) * scale, (signal.y - coreY) * scale, mark)
        drawRect(Kk.Bone, Offset(at.x - mark * 0.5f, at.y - mark * 0.5f), Size(mark, mark), style = kkStroke(1f))
    }
}

// ---------------------------------------------------------------------------------------------
// HUD: the dungeon's trial panel pieces and the vignette.

/** The vignette is clear out to this share of the corner radius and this ink alpha at the screen's corners. */
private const val VIGNETTE_CLEAR = 0.55f
private const val VIGNETTE_ALPHA = 0.5f

/** The sealed room's meter cell pulses between these alphas on the brackets' beat (a cleared cell is solid). */
private const val SEGMENT_PULSE_MIN = 0.2f
private const val SEGMENT_PULSE_SPAN = 0.35f

/** Draw-thread scratch of the dungeon panel's texts. */
private object StructurePanelText {
    val rewards = HudKeyedText()
}

/** The dungeon panel's label: a dungeon names itself a structure, whatever placeholder family its trial carries. */
internal fun structureTrialLabel(): TextResource = StructureHudText.Structure

/** The rules behind the dungeon panel's (!) in [language]. */
internal fun structureTrialRules(language: AppLanguage): String = language.text(StructureHudText.Rules)

/**
 * The dungeon panel's reward row: its template's reward labels in [language] joined with " + " ("Relic + Rare item"),
 * built once per template and language so a steady panel builds no string.
 */
internal fun structureTrialRewards(engine: GameplayRenderModel, trial: TrialProjection, language: AppLanguage): String {
    val id = trial.structure ?: return language.text(HudRedesignText.RewardUnknown)
    val template = engine.content.structures.template(id)
    return StructurePanelText.rewards.of(template, language.ordinal.toLong()) {
        template.rewards.joinToString(" + ") { it.label.localizedContent(language) }
    }
}

/**
 * The sealed room's cell of the dungeon panel's meter ([meter], drawn with the trial's segments, the cleared rooms
 * filled from the left): while a room is sealed the first empty cell pulses in volt on the brackets' beat. A sealed
 * room always counts (only a garrisoned room seals), so that cell is its own.
 */
internal fun DrawScope.drawSealedRoomSegment(engine: GameplayRenderModel, trial: TrialProjection, meter: Rect, roles: KkRolePalette) {
    val dungeon = engine.structures.dungeon ?: return
    val segments = trial.segments
    val index = trial.done.roundToInt()
    if (dungeon.engagedRoom < 0 || segments <= 0 || index < 0 || index >= segments) return
    val pitch = meter.width / segments
    val gap = d(3f).coerceAtMost(pitch * 0.5f)
    val alpha = SEGMENT_PULSE_MIN + SEGMENT_PULSE_SPAN * (0.5f + 0.5f * sin(engine.renderElapsed * 6f))
    withTransform({ kkShear(meter.center.y) }) {
        drawRect(roles.you, Offset(meter.left + index * pitch, meter.top), Size(pitch - gap, meter.height), alpha)
    }
}

/**
 * The HUD vignette while the Core is inside a dungeon: ink closing in from the screen's edges (clear out to
 * [VIGNETTE_CLEAR] of the corner radius, [VIGNETTE_ALPHA] at the corners), as deep as the world's scrim, so both fade in
 * on the way in and out on the way out. None for a secret template or outside the dungeon's rooms.
 */
internal fun DrawScope.drawDungeonVignette(engine: GameplayRenderModel) {
    val amount = structureInteriorAmount(engine)
    if (amount <= 0.01f) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    // The critical vignette's geometry: a circle of the corner radius squashed vertically into the screen's ellipse.
    val brush = HudDrawCache.brush(HudBrush.DUNGEON, w, h, 0f, 0f, Kk.Ink) {
        Brush.radialGradient(
            0f to Color.Transparent, VIGNETTE_CLEAR to Color.Transparent, 1f to Kk.Ink.copy(alpha = VIGNETTE_ALPHA),
            center = Offset(w * 0.5f, h * 0.5f), radius = w * 0.5f / SQRT_HALF,
        )
    }
    scale(1f, h / w, Offset(w * 0.5f, h * 0.5f)) {
        drawRect(brush, Offset(0f, h * 0.5f - w * 0.5f), Size(w, w), amount)
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces.

/** Whether the footprint of [structure] centred at [center] (layer units), with slabs and margin, meets the view. */
private fun DrawScope.structureInView(center: Offset, structure: StructureProjection): Boolean {
    val reachX = structure.halfWidth + SLAB_REACH + VIEW_MARGIN
    val reachY = structure.halfHeight + SLAB_REACH + VIEW_MARGIN
    return center.x + reachX >= 0f && center.x - reachX <= size.width && center.y + reachY >= 0f && center.y - reachY <= size.height
}

/** The windowed structure the dungeon holds, or null. */
private fun dungeonStructure(projection: StructureRenderProjection): StructureProjection? {
    val dungeon = projection.dungeon ?: return null
    val structures = projection.structures
    for (index in structures.indices) if (structures[index].id == dungeon.structureId) return structures[index]
    return null
}

/** The spine of a solid slab along its centre line (a round boss for a pillar). */
private fun DrawScope.drawWallSpine(a: Offset, b: Offset, halfThickness: Float) {
    val color = Kk.Ink4.copy(alpha = 0.6f)
    if (a.x == b.x && a.y == b.y) {
        drawCircle(color, halfThickness * 0.45f, a)
    } else {
        drawLine(color, a, b, (halfThickness * 0.5f).roundToInt().toFloat())
    }
}

/** An open doorway: two dashes across its gap, at 20–42 % and 58–80 % of it. */
private fun DrawScope.drawOpenDoor(a: Offset, b: Offset, color: androidx.compose.ui.graphics.Color) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    drawLine(color, Offset(a.x + dx * 0.2f, a.y + dy * 0.2f), Offset(a.x + dx * 0.42f, a.y + dy * 0.42f), 3f)
    drawLine(color, Offset(a.x + dx * 0.58f, a.y + dy * 0.58f), Offset(a.x + dx * 0.8f, a.y + dy * 0.8f), 3f)
}

/** A sealed doorway: the door capsule's slab rect in ink, hatched in bone (locked), edged in [edge]. */
private fun DrawScope.drawSealedDoor(a: Offset, b: Offset, halfThickness: Float, edge: androidx.compose.ui.graphics.Color) {
    val topLeft = Offset(min(a.x, b.x) - halfThickness, min(a.y, b.y) - halfThickness)
    val size = Size(abs(b.x - a.x) + halfThickness * 2f, abs(b.y - a.y) + halfThickness * 2f)
    drawRect(Kk.Ink3, topLeft, size)
    drawRect(kkFillBrush(KkFillKind.HATCH, Kk.Bone.copy(alpha = 0.55f)), topLeft, size)
    drawRect(edge, topLeft, size, style = kkStroke(2f))
}

/** Four threat brackets in the corners of the sealed room, pulsing. */
private fun DrawScope.drawRoomBrackets(center: Offset, room: StructureRoom, rotation: Int, time: Float, roles: KkRolePalette) {
    val color = roles.threat.copy(alpha = (0.65f + 0.35f * sin(time * 6f)).coerceIn(0f, 1f))
    val left = center.x + roomLeft(room, rotation) + BRACKET_INSET
    val top = center.y + roomTop(room, rotation) + BRACKET_INSET
    val right = center.x + roomRight(room, rotation) - BRACKET_INSET
    val bottom = center.y + roomBottom(room, rotation) - BRACKET_INSET
    drawLine(color, Offset(left, top), Offset(left + BRACKET_ARM, top), BRACKET_STROKE)
    drawLine(color, Offset(left, top), Offset(left, top + BRACKET_ARM), BRACKET_STROKE)
    drawLine(color, Offset(right, top), Offset(right - BRACKET_ARM, top), BRACKET_STROKE)
    drawLine(color, Offset(right, top), Offset(right, top + BRACKET_ARM), BRACKET_STROKE)
    drawLine(color, Offset(left, bottom), Offset(left + BRACKET_ARM, bottom), BRACKET_STROKE)
    drawLine(color, Offset(left, bottom), Offset(left, bottom - BRACKET_ARM), BRACKET_STROKE)
    drawLine(color, Offset(right, bottom), Offset(right - BRACKET_ARM, bottom), BRACKET_STROKE)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - BRACKET_ARM), BRACKET_STROKE)
}

/** Whether door [door] of [template] opens onto [room]. */
private fun doorTouches(template: StructureTemplate, door: Int, room: Int): Boolean {
    if (door < 0 || door >= template.doors.size) return false
    val spec = template.doors[door]
    return spec.roomA == room || spec.roomB == room
}

/**
 * Whether open door [door] is the player's next way: it joins a room reached (cleared, or the one the Core is in;
 * the outside while the Core is in no room) to a room that still counts (a garrison or the final room) and is not clear.
 */
private fun leadsOnward(template: StructureTemplate, door: Int, cleared: Int, coreRoom: Int): Boolean {
    if (door < 0 || door >= template.doors.size) return false
    val spec = template.doors[door]
    val counted = template.roomsToClearMask
    return (reached(spec.roomA, cleared, coreRoom) && pending(spec.roomB, cleared, counted)) ||
        (reached(spec.roomB, cleared, coreRoom) && pending(spec.roomA, cleared, counted))
}

private fun reached(room: Int, cleared: Int, coreRoom: Int): Boolean =
    if (room == STRUCTURE_OUTSIDE) coreRoom == STRUCTURE_OUTSIDE else room == coreRoom || cleared and (1 shl room) != 0

private fun pending(room: Int, cleared: Int, counted: Int): Boolean =
    room != STRUCTURE_OUTSIDE && counted and (1 shl room) != 0 && cleared and (1 shl room) == 0

/** The room of [template] turned by [rotation] that holds the offset ([dx], [dy]) from its centre, or [STRUCTURE_OUTSIDE]. */
private fun roomAt(template: StructureTemplate, rotation: Int, dx: Float, dy: Float): Int {
    val inverse = (4 - rotation) and 3
    val x = rotateQuarterX(dx, dy, inverse)
    val y = rotateQuarterY(dx, dy, inverse)
    val rooms = template.rooms
    for (index in rooms.indices) {
        val room = rooms[index]
        if (x >= room.minX && x <= room.maxX && y >= room.minY && y <= room.maxY) return index
    }
    return STRUCTURE_OUTSIDE
}

/** The centre of [room] turned by [rotation], as a point from the structure centre [center]. */
private fun roomCenter(center: Offset, room: StructureRoom, rotation: Int): Offset {
    val x = (room.minX + room.maxX) * 0.5f
    val y = (room.minY + room.maxY) * 0.5f
    return Offset(center.x + rotateQuarterX(x, y, rotation), center.y + rotateQuarterY(x, y, rotation))
}

// The bounds of a room turned by a quarter rotation (quarter turns keep rooms axis-aligned).
private fun roomLeft(room: StructureRoom, rotation: Int): Float = when (rotation and 3) {
    0 -> room.minX
    1 -> -room.maxY
    2 -> -room.maxX
    else -> room.minY
}

private fun roomTop(room: StructureRoom, rotation: Int): Float = when (rotation and 3) {
    0 -> room.minY
    1 -> room.minX
    2 -> -room.maxY
    else -> -room.maxX
}

private fun roomRight(room: StructureRoom, rotation: Int): Float = when (rotation and 3) {
    0 -> room.maxX
    1 -> -room.minY
    2 -> -room.minX
    else -> room.maxY
}

private fun roomBottom(room: StructureRoom, rotation: Int): Float = when (rotation and 3) {
    0 -> room.maxY
    1 -> room.maxX
    2 -> -room.minY
    else -> -room.minX
}

// ---------------------------------------------------------------------------------------------
// Cached shapes.

/** Corners of a slab outline ([slabOutline]). */
internal const val SLAB_OUTLINE_CORNERS = 10

private const val SQRT_HALF = 0.70710677f

/**
 * Writes the outline of the capsule ([ax], [ay]) → ([bx], [by]) with [halfThickness] into [out] as
 * [SLAB_OUTLINE_CORNERS] (x, y) pairs: both sides, then half-octagon caps whose corners lie on the cap circles, so every
 * corner is [halfThickness] from the segment and every edge stays inside the collision capsule by at most
 * 0.076 × [halfThickness]. A zero-length capsule (a pillar) is an octagon.
 */
internal fun slabOutline(ax: Float, ay: Float, bx: Float, by: Float, halfThickness: Float, out: FloatArray) {
    var ux = bx - ax
    var uy = by - ay
    val length = sqrt(ux * ux + uy * uy)
    if (length > 1e-4f) {
        ux /= length
        uy /= length
    } else {
        ux = 1f
        uy = 0f
    }
    val nx = -uy
    val ny = ux
    val h = halfThickness
    val d = halfThickness * SQRT_HALF
    out[0] = ax + nx * h
    out[1] = ay + ny * h
    out[2] = bx + nx * h
    out[3] = by + ny * h
    out[4] = bx + (ux + nx) * d
    out[5] = by + (uy + ny) * d
    out[6] = bx + ux * h
    out[7] = by + uy * h
    out[8] = bx + (ux - nx) * d
    out[9] = by + (uy - ny) * d
    out[10] = bx - nx * h
    out[11] = by - ny * h
    out[12] = ax - nx * h
    out[13] = ay - ny * h
    out[14] = ax - (ux + nx) * d
    out[15] = ay - (uy + ny) * d
    out[16] = ax - ux * h
    out[17] = ay - uy * h
    out[18] = ax - (ux - nx) * d
    out[19] = ay - (uy - ny) * d
}

/**
 * Structure shapes in object-local units (origin at the structure centre), built from the content template turned by
 * its rotation: the floor (room rects), the solid slabs and the scrim (a rect past any view with the rooms cut out,
 * even-odd). Keyed by template, rotation and kind; a bounded round robin (a window holds at most nine structures, three
 * kinds each) whose evicted paths are reset and reused. Draw-thread confined.
 */
internal object StructurePaths {
    const val CAPACITY = 32
    private const val FLOOR = 0
    private const val WALLS = 1
    private const val SCRIM = 2
    private val keys = IntArray(CAPACITY) { -1 }
    private val sources = arrayOfNulls<StructureTemplate>(CAPACITY)
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private val corners = FloatArray(SLAB_OUTLINE_CORNERS * 2)
    private var next = 0

    /** Paths built so far: a warm frame builds none. */
    var builds = 0
        private set

    fun floor(template: StructureTemplate, rotation: Int): Path = get(template, rotation, FLOOR)

    fun walls(template: StructureTemplate, rotation: Int): Path = get(template, rotation, WALLS)

    fun scrim(template: StructureTemplate, rotation: Int): Path = get(template, rotation, SCRIM)

    private fun get(template: StructureTemplate, rotation: Int, kind: Int): Path {
        val key = (template.id.ordinal shl 8) or ((rotation and 3) shl 4) or kind
        for (index in 0 until CAPACITY) {
            if (keys[index] == key && sources[index] === template) return paths[index]!!
        }
        val path = paths[next]?.also { it.reset() } ?: Path()
        when (kind) {
            FLOOR -> buildRooms(path, template, rotation)
            WALLS -> buildSlabs(path, template, rotation)
            else -> {
                path.addRect(Rect(-SCRIM_REACH, -SCRIM_REACH, SCRIM_REACH, SCRIM_REACH))
                buildRooms(path, template, rotation)
            }
        }
        path.fillType = if (kind == SCRIM) PathFillType.EvenOdd else PathFillType.NonZero
        keys[next] = key
        sources[next] = template
        paths[next] = path
        next = (next + 1) % CAPACITY
        builds++
        return path
    }

    private fun buildRooms(path: Path, template: StructureTemplate, rotation: Int) {
        val rooms = template.rooms
        for (index in rooms.indices) {
            val room = rooms[index]
            path.addRect(Rect(roomLeft(room, rotation), roomTop(room, rotation), roomRight(room, rotation), roomBottom(room, rotation)))
        }
    }

    private fun buildSlabs(path: Path, template: StructureTemplate, rotation: Int) {
        val walls = template.walls
        for (index in walls.indices) {
            val wall = walls[index]
            if (wall.isDoor) continue
            slabOutline(
                rotateQuarterX(wall.ax, wall.ay, rotation), rotateQuarterY(wall.ax, wall.ay, rotation),
                rotateQuarterX(wall.bx, wall.by, rotation), rotateQuarterY(wall.bx, wall.by, rotation),
                wall.halfThickness, corners,
            )
            path.moveTo(corners[0], corners[1])
            for (corner in 1 until SLAB_OUTLINE_CORNERS) path.lineTo(corners[corner * 2], corners[corner * 2 + 1])
            path.close()
        }
    }
}
