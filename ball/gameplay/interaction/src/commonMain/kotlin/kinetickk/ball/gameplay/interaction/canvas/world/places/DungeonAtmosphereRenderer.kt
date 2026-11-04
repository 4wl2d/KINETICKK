// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStrokeWidth
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.positiveModulo
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*

/*
 * A dungeon's air and its threshold: motes drifting in each room in view in the template's manner (station sparks
 * falling, vault frost, spire rings rising, labyrinth spores, foundry embers, reliquary motes orbiting the room's
 * centre), and at the entrance of a structure still to fight a threat light spilling out of its outer door between two
 * pulsing brackets. Motes are world-anchored and stateless (a hash per room and mote, the run's render time), secondary
 * (FxPool.DUNGEON, off above the crowd limit and at the low particle setting); the threshold always draws.
 */

/** One mote per this many square units of room, between these counts. */
private const val MOTE_AREA = 26_000f
private const val MIN_MOTES = 6
private const val MAX_MOTES = 22

/** The threshold's light reaches this far out of the outer door; its brackets stand this far beside it (world units). */
private const val THRESHOLD_POOL = 330f
private const val THRESHOLD_BRACKET = 26f

/** Motes of every room in view of every dungeon in view, after the walls; the thresholds of structures still to fight. */
internal fun DrawScope.drawDungeonAtmosphere(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val structures = engine.structures.structures
    if (structures.isEmpty()) return
    val density = moteDensity(engine.settings.particleDensity)
    val motes = density > 0f && FxFrame.crowd <= FxFrame.CROWD_SECONDARY_OFF
    val time = engine.renderElapsed
    for (index in structures.indices) {
        val structure = structures[index]
        val template = engine.content.structures.template(structure.template)
        if (!template.dungeon) continue
        val center = world(engine, structure.x, structure.y, shakeX, shakeY)
        drawThreshold(template, structure, engine, center, time, roles)
        if (!motes) continue
        val accent = structureAccent(template, roles)
        for (room in template.rooms.indices) {
            drawRoomMotes(template, structure, room, center, time, density, accent, roles, engine)
        }
    }
}

/** The particle setting's share of motes. */
private fun moteDensity(density: ParticleDensity): Float = when (density) {
    ParticleDensity.LOW -> 0f
    ParticleDensity.NORMAL -> 0.75f
    ParticleDensity.HIGH -> 1f
}

/** The motes of [room] when its rect meets the view and the pool has room for them. */
private fun DrawScope.drawRoomMotes(
    template: StructureTemplate,
    structure: StructureProjection,
    room: Int,
    center: Offset,
    time: Float,
    density: Float,
    accent: Color,
    roles: KkRolePalette,
    engine: GameplayRenderModel,
) {
    val spec = template.rooms[room]
    val rotation = structure.rotation
    val left = center.x + roomLeft(spec, rotation)
    val top = center.y + roomTop(spec, rotation)
    val right = center.x + roomRight(spec, rotation)
    val bottom = center.y + roomBottom(spec, rotation)
    if (right < 0f || bottom < 0f || left > size.width || top > size.height) return
    val width = right - left
    val height = bottom - top
    val count = ((width * height / MOTE_AREA).toInt().coerceIn(MIN_MOTES, MAX_MOTES) * density).toInt()
    if (count <= 0 || !FxDrawBudget.take(FxPool.DUNGEON, count, signature = false)) return
    val style = template.id.dungeonStyle
    val mood = roomMood(structure, engine.structures.dungeon, room)
    val color = if (mood == RoomMood.SEALED || mood == RoomMood.GUARDIAN) kkMix(roles.threat, accent, 0.5f) else kkMix(accent, Kk.Bone, 0.45f)
    val seed = (structure.id xor (structure.id ushr 32)).toInt() * 31 + room
    val inset = template.wallHalfThickness + 12f
    for (mote in 0 until count) {
        val u = fxHash(seed, mote * 4)
        val v = fxHash(seed, mote * 4 + 1)
        val pace = 0.6f + 0.8f * fxHash(seed, mote * 4 + 2)
        val phase = fxHash(seed, mote * 4 + 3)
        val twinkle = 0.5f + 0.5f * sin(time * (1.3f + pace) + phase * 6.283f)
        val spanX = width - 2f * inset
        val spanY = height - 2f * inset
        var x: Float
        var y: Float
        when (style.motes) {
            DungeonMotes.ORBITS -> {
                // Orbiting the room's centre: slow, at their own radius, never faster than a turn per 24 s.
                val radius = min(spanX, spanY) * (0.12f + 0.36f * u)
                val angle = phase * 6.283f + time * 0.26f * pace * (if (mote % 2 == 0) 1f else -1f)
                x = (left + right) * 0.5f + cos(angle) * radius
                y = (top + bottom) * 0.5f + sin(angle) * radius
            }
            else -> {
                val sway = sin(time * 0.7f * pace + phase * 6.283f) * 14f
                x = left + inset + positiveModulo(u * spanX + moteDriftX(style.motes) * pace * time + sway, spanX)
                y = top + inset + positiveModulo(v * spanY + moteDriftY(style.motes) * pace * time, spanY)
            }
        }
        drawMote(style.motes, x, y, pace, twinkle, color, time, phase)
    }
}

/** World units per second a template's motes drift across and down (before their own pace; rising is negative). */
private fun moteDriftX(motes: DungeonMotes): Float = when (motes) {
    DungeonMotes.SPARKS -> 4f
    DungeonMotes.FROST -> 9f
    DungeonMotes.SPORES -> 3f
    DungeonMotes.EMBERS -> 5f
    DungeonMotes.NOTES, DungeonMotes.ORBITS -> 0f
}

private fun moteDriftY(motes: DungeonMotes): Float = when (motes) {
    DungeonMotes.SPARKS -> 26f
    DungeonMotes.FROST -> 14f
    DungeonMotes.NOTES -> -22f
    DungeonMotes.SPORES -> -12f
    DungeonMotes.EMBERS -> -48f
    DungeonMotes.ORBITS -> 0f
}

/** One mote of [kind] at ([x], [y]) in [color], twinkling by [twinkle] (0..1). */
private fun DrawScope.drawMote(kind: DungeonMotes, x: Float, y: Float, pace: Float, twinkle: Float, color: Color, time: Float, phase: Float) {
    val at = Offset(x, y)
    when (kind) {
        DungeonMotes.SPARKS -> drawLine(color, at, Offset(x - 1.5f, y - 7f * pace), 1.6f, alpha = 0.25f + 0.55f * twinkle * twinkle)
        DungeonMotes.FROST -> drawCircle(Kk.Bone, 1.4f + pace, at, alpha = 0.18f + 0.32f * twinkle)
        DungeonMotes.NOTES -> {
            val swell = positiveModulo(time * 0.5f * pace + phase, 1f)
            drawCircle(color, 3f + 9f * swell, at, alpha = 0.4f * (1f - swell), style = kkStroke(KkStrokeWidth.Line))
        }
        DungeonMotes.SPORES -> drawCircle(color, 1.8f + 1.4f * pace, at, alpha = 0.2f + 0.35f * twinkle)
        DungeonMotes.EMBERS -> {
            val side = 2.2f + 1.6f * pace
            drawRect(kkMix(color, Kk.Bone, 0.3f * twinkle), Offset(x - side * 0.5f, y - side * 0.5f), Size(side, side), alpha = 0.35f + 0.55f * twinkle)
        }
        DungeonMotes.ORBITS -> drawCircle(color, 2f + pace, at, alpha = 0.3f + 0.45f * twinkle)
    }
}

/**
 * The threshold of a structure still to fight (no room of it cleared, none sealed): its outer door spills a threat
 * light outward and two threat brackets beside it pulse; brighter as the Core comes near. None once a fight began.
 */
private fun DrawScope.drawThreshold(
    template: StructureTemplate,
    structure: StructureProjection,
    engine: GameplayRenderModel,
    center: Offset,
    time: Float,
    roles: KkRolePalette,
) {
    if (structure.clearedRooms and template.roomsToClearMask != 0 || structure.engagedRoom >= 0) return
    val door = template.doors.getOrNull(template.entranceDoor) ?: return
    val rotation = structure.rotation
    val room = template.rooms[door.roomA]
    val doorX = if (door.axis == WallAxis.HORIZONTAL) door.center else door.line
    val doorY = if (door.axis == WallAxis.HORIZONTAL) door.line else door.center
    val outward = outwardSign(door.axis, door.line, room)
    val normalX = if (door.axis == WallAxis.VERTICAL) outward else 0f
    val normalY = if (door.axis == WallAxis.HORIZONTAL) outward else 0f
    val at = Offset(center.x + rotateQuarterX(doorX, doorY, rotation), center.y + rotateQuarterY(doorX, doorY, rotation))
    if (at.x < -THRESHOLD_POOL || at.y < -THRESHOLD_POOL || at.x > size.width + THRESHOLD_POOL || at.y > size.height + THRESHOLD_POOL) return
    val nx = rotateQuarterX(normalX, normalY, rotation)
    val ny = rotateQuarterY(normalX, normalY, rotation)
    val near = 1f - (hypot(engine.renderCoreX - structure.x - rotateQuarterX(doorX, doorY, rotation), engine.renderCoreY - structure.y -
        rotateQuarterY(doorX, doorY, rotation)) / NEAR_REACH).coerceIn(0f, 1f)
    val pulse = 0.5f + 0.5f * sin(time * (2.4f + 3f * near))
    val threat = roles.threat
    drawLightPool(Offset(at.x + nx * THRESHOLD_POOL * 0.35f, at.y + ny * THRESHOLD_POOL * 0.35f), THRESHOLD_POOL, threat, 0.05f + 0.07f * pulse + 0.08f * near)
    // Brackets at both jambs, opening outward.
    val half = door.width * 0.5f + THRESHOLD_BRACKET
    val tx = -ny
    val ty = nx
    val alpha = 0.35f + 0.45f * pulse * (0.4f + 0.6f * near)
    for (side in 0 until 2) {
        val sign = if (side == 0) 1f else -1f
        val jamb = Offset(at.x + tx * half * sign + nx * (template.wallHalfThickness + 10f), at.y + ty * half * sign + ny * (template.wallHalfThickness + 10f))
        val arm = 34f
        drawLine(threat, jamb, Offset(jamb.x + nx * arm, jamb.y + ny * arm), 3f, alpha = alpha)
        drawLine(threat, jamb, Offset(jamb.x - tx * arm * sign, jamb.y - ty * arm * sign), 3f, alpha = alpha)
    }
}

/** +1 when the outside of a door on [line] of [room] lies toward +axis, −1 otherwise. */
private fun outwardSign(axis: WallAxis, line: Float, room: StructureRoom): Float {
    val middle = if (axis == WallAxis.HORIZONTAL) (room.minY + room.maxY) * 0.5f else (room.minX + room.maxX) * 0.5f
    return if (line > middle) 1f else -1f
}

/** The threshold brightens over this last stretch of the Core's approach (world units). */
private const val NEAR_REACH = 1_400f
