// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.mapToImmutableList

/**
 * The dungeon moments of [InteractionFxReducer]: one per [VisualFxCue.DungeonMoment], at most
 * [InteractionFxLimits.MAX_DUNGEON_MOMENTS] (the oldest leaves first), each living [dungeonMomentSeconds] of its kind.
 * They mark the world, so a rebase moves them; a clear ends them. Every change reports whether the published list
 * changes; [published] rebuilds the list only after one.
 */
internal class DungeonMomentTrack {
    private val entries = mutableListOf<Moment>()
    private var dirty = false
    private var cached: ImmutableList<DungeonMomentProjection> = EMPTY

    val isEmpty: Boolean get() = entries.isEmpty()

    /** Starts the moment of [cue] with the presentation-only [seed] of its sparks. */
    fun add(cue: VisualFxCue.DungeonMoment, seed: Int): Boolean {
        val life = dungeonMomentSeconds(cue.kind)
        entries += Moment(cue.kind, cue.template, cue.x, cue.y, cue.halfWidth, cue.halfHeight, cue.focusX, cue.focusY, life, life, seed)
        while (entries.size > InteractionFxLimits.MAX_DUNGEON_MOMENTS) entries.removeAt(0)
        dirty = true
        return true
    }

    fun clear(): Boolean {
        if (entries.isEmpty()) return false
        entries.clear()
        dirty = true
        return true
    }

    fun rebase(shiftX: Float, shiftY: Float): Boolean {
        if (entries.isEmpty()) return false
        for (index in entries.indices) {
            val entry = entries[index]
            entry.x -= shiftX
            entry.y -= shiftY
            entry.focusX -= shiftX
            entry.focusY -= shiftY
        }
        dirty = true
        return true
    }

    /** Ages every moment by [delta] and drops the finished ones. */
    fun advance(delta: Float): Boolean {
        if (entries.isEmpty()) return false
        var kept = 0
        for (index in entries.indices) {
            val entry = entries[index]
            entry.life -= delta
            if (entry.life > 0f) entries[kept++] = entry
        }
        while (entries.size > kept) entries.removeAt(entries.lastIndex)
        dirty = true
        return true
    }

    /** The published list: rebuilt only after a change, so a quiet frame reuses the previous instance. */
    fun published(): ImmutableList<DungeonMomentProjection> {
        if (!dirty) return cached
        cached = if (entries.isEmpty()) EMPTY else entries.mapToImmutableList { value ->
            DungeonMomentProjection(
                value.kind, value.template, value.x, value.y, value.halfWidth, value.halfHeight, value.focusX, value.focusY,
                value.life, value.maxLife, value.seed,
            )
        }
        dirty = false
        return cached
    }

    private class Moment(
        val kind: DungeonMomentKind,
        val template: StructureTemplateId,
        var x: Float,
        var y: Float,
        val halfWidth: Float,
        val halfHeight: Float,
        var focusX: Float,
        var focusY: Float,
        var life: Float,
        val maxLife: Float,
        val seed: Int,
    )

    private companion object {
        val EMPTY: ImmutableList<DungeonMomentProjection> = immutableListOf()
    }
}

/**
 * How long a dungeon moment plays: the doors' slam and barrier ignition, a wave's breach flare, the guardian's
 * entrance, a room's light burst and rune ignition, and the whole structure's clear.
 */
internal fun dungeonMomentSeconds(kind: DungeonMomentKind): Float = when (kind) {
    DungeonMomentKind.SEALED -> 1f
    DungeonMomentKind.WAVE -> 0.8f
    DungeonMomentKind.GUARDIAN -> 1.6f
    DungeonMomentKind.ROOM_CLEARED -> 1.5f
    DungeonMomentKind.STRUCTURE_CLEARED -> 2.2f
}

/**
 * The structure looks of [InteractionFxReducer], cleared, rebased and aged together: structures shattering and dungeon
 * moments.
 */
internal class StructureFxTracks {
    val disintegrations = StructureDisintegrationTrack()
    val moments = DungeonMomentTrack()

    val isEmpty: Boolean get() = disintegrations.isEmpty && moments.isEmpty

    fun clear(): Boolean {
        val shards = disintegrations.clear()
        return moments.clear() || shards
    }

    fun rebase(shiftX: Float, shiftY: Float): Boolean {
        val shards = disintegrations.rebase(shiftX, shiftY)
        return moments.rebase(shiftX, shiftY) || shards
    }

    fun advance(delta: Float): Boolean {
        val shards = disintegrations.advance(delta)
        return moments.advance(delta) || shards
    }
}
