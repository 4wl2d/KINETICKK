// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.render.DungeonProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkMix
import kinetickk.ball.gameplay.interaction.canvas.*

/*
 * The dungeon look shared by the dungeon renderers: each template's identity (what its floor is made of, what drifts
 * in its air, its light), the room state that tints its lights (dormant, sealed and fighting, the guardian's room,
 * cleared), the soft light pools every light casts (one cached radial brush per colour, drawn scaled), and the cached
 * draw state of the frame. Everything is presentation: positions and timestamps come from the render model.
 */

/** What a template's floor is made of. */
internal enum class DungeonFloor { DECK, ICE, RINGS, ROOTS, GRATE, SANCTUM }

/** What drifts in a template's rooms. */
internal enum class DungeonMotes { SPARKS, FROST, NOTES, SPORES, EMBERS, ORBITS }

/**
 * A template's identity: its floor, its motes, the grid its floor tiles follow (world units) and how its light flickers
 * ([flicker] 0 steady .. 1 failing). The element hue and its icon come from the template's content.
 */
internal enum class DungeonStyle(val floor: DungeonFloor, val motes: DungeonMotes, val tile: Float, val flicker: Float) {
    STATION(DungeonFloor.DECK, DungeonMotes.SPARKS, 140f, 0.8f),
    VAULT(DungeonFloor.ICE, DungeonMotes.FROST, 160f, 0.1f),
    SPIRE(DungeonFloor.RINGS, DungeonMotes.NOTES, 125f, 0.2f),
    LABYRINTH(DungeonFloor.ROOTS, DungeonMotes.SPORES, 175f, 0.35f),
    FOUNDRY(DungeonFloor.GRATE, DungeonMotes.EMBERS, 70f, 0.5f),
    RELIQUARY(DungeonFloor.SANCTUM, DungeonMotes.ORBITS, 200f, 0.15f),
}

internal val StructureTemplateId.dungeonStyle: DungeonStyle
    get() = when (this) {
        StructureTemplateId.DERELICT_STATION -> DungeonStyle.STATION
        StructureTemplateId.VAULT -> DungeonStyle.VAULT
        StructureTemplateId.SPIRE -> DungeonStyle.SPIRE
        StructureTemplateId.LABYRINTH -> DungeonStyle.LABYRINTH
        StructureTemplateId.FOUNDRY -> DungeonStyle.FOUNDRY
        StructureTemplateId.RELIQUARY, StructureTemplateId.ARCHITECTS_DOODLE -> DungeonStyle.RELIQUARY
    }

/** How a room reads right now: its lights follow it. */
internal enum class RoomMood { DORMANT, SEALED, GUARDIAN, CLEARED }

/** The mood of [room] of [structure]: sealed (the guardian's when its guardian lives), cleared, or dormant. */
internal fun roomMood(structure: StructureProjection, dungeon: DungeonProjection?, room: Int): RoomMood {
    if (structure.engagedRoom == room) {
        return if (dungeon != null && dungeon.structureId == structure.id && dungeon.guardianId >= 0) RoomMood.GUARDIAN else RoomMood.SEALED
    }
    return if (structure.clearedRooms and (1 shl room) != 0) RoomMood.CLEARED else RoomMood.DORMANT
}

/** The light of a room in [mood]: the element's hue while dormant, threat while sealed, warm bone once cleared. */
internal fun roomLight(mood: RoomMood, accent: Color, roles: KkRolePalette): Color = when (mood) {
    RoomMood.DORMANT -> kkMix(accent, Kk.Bone, 0.2f)
    RoomMood.SEALED -> kkMix(roles.threat, accent, 0.25f)
    RoomMood.GUARDIAN -> kkMix(roles.threat, Kk.Bone, 0.1f)
    RoomMood.CLEARED -> kkMix(Kk.Bone, accent, 0.35f)
}

/**
 * A light's slow breathing in 0..1 for the light at [phase] (0..1): a steady template breathes once per few seconds, a
 * failing one ([DungeonStyle.flicker]) adds a quick stutter; a sealed room's lights pulse with the alarm instead.
 */
internal fun lightBreath(style: DungeonStyle, mood: RoomMood, time: Float, phase: Float): Float {
    val turn = phase * TURN
    if (mood == RoomMood.SEALED || mood == RoomMood.GUARDIAN) return 0.5f + 0.5f * kotlin.math.sin(time * ALARM_RATE + turn)
    val breath = 0.5f + 0.5f * kotlin.math.sin(time * BREATH_RATE + turn)
    val stutter = kotlin.math.sin(time * STUTTER_RATE + turn * 3f)
    return (breath - style.flicker * 0.35f * (if (stutter > 0.92f) 1f else 0f)).coerceIn(0f, 1f)
}

/** The structure a dungeon projection holds, or null. */
internal fun heldStructure(engine: GameplayRenderModel): StructureProjection? {
    val projection = engine.structures
    val dungeon = projection.dungeon ?: return null
    val structures = projection.structures
    for (index in structures.indices) if (structures[index].id == dungeon.structureId) return structures[index]
    return null
}

/**
 * A soft pool of [color] light of [radius] at [center] with peak [alpha]: one cached unit radial brush per colour,
 * drawn scaled, so the frame allocates nothing once the colour was seen.
 */
internal fun DrawScope.drawLightPool(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (alpha <= 0.004f || radius <= 0f) return
    val brush = DungeonLightBrushes.of(color)
    withTransform({
        translate(center.x, center.y)
        scale(radius, radius, Offset.Zero)
    }) {
        drawCircle(brush, 1f, Offset.Zero, alpha = alpha.coerceAtMost(1f))
    }
}

/**
 * Unit radial light brushes (centre opaque, half strength at a third, clear at the rim) keyed by colour; a bounded
 * round robin that builds a brush only for a colour it does not hold. Draw-thread confined.
 */
internal object DungeonLightBrushes {
    private const val CAPACITY = 24
    private val keys = LongArray(CAPACITY)
    private val brushes = arrayOfNulls<Brush>(CAPACITY)
    private var next = 0

    /** Brushes built so far: a warm frame builds none. */
    var builds = 0
        private set

    fun of(color: Color): Brush {
        val key = color.copy(alpha = 1f).value.toLong()
        for (index in 0 until CAPACITY) {
            if (brushes[index] != null && keys[index] == key) return brushes[index]!!
        }
        val opaque = color.copy(alpha = 1f)
        val brush = Brush.radialGradient(
            0f to opaque, 0.32f to opaque.copy(alpha = 0.5f), 0.7f to opaque.copy(alpha = 0.12f), 1f to Color.Transparent,
            center = Offset.Zero, radius = 1f,
        )
        keys[next] = key
        brushes[next] = brush
        next = (next + 1) % CAPACITY
        builds++
        return brush
    }
}

/** True when [template] is a dungeon (not the Doodle's secret) and so wears the dungeon look. */
internal val StructureTemplate.dungeon: Boolean get() = !secret && roomWalls

private const val TURN: Float = CanvasMath.TAU

/** A dormant light breathes about once per four seconds; a sealed room's alarm about once per second. */
private const val BREATH_RATE: Float = 1.6f
private const val ALARM_RATE: Float = 6f
private const val STUTTER_RATE: Float = 23f
