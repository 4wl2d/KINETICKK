// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.mapToImmutableList

/**
 * The structure disintegrations of [InteractionFxReducer]: one per [VisualFxCue.StructureDisintegrated], at most
 * [InteractionFxLimits.MAX_DISINTEGRATIONS] (the oldest leaves first), each living
 * [InteractionFxLimits.DISINTEGRATION_SECONDS]. Their shards mark the world, so a rebase moves them; a clear ends them.
 * Every change reports whether the published list changes; [published] rebuilds the list only after one.
 */
internal class StructureDisintegrationTrack {
    private val entries = mutableListOf<Disintegration>()
    private var dirty = false
    private var cached: ImmutableList<StructureDisintegrationProjection> = immutableListOf()

    val isEmpty: Boolean get() = entries.isEmpty()

    /** Starts the disintegration of [cue] with the presentation-only [seed] of its shards. */
    fun add(cue: VisualFxCue.StructureDisintegrated, seed: Int): Boolean {
        val life = InteractionFxLimits.DISINTEGRATION_SECONDS
        entries += Disintegration(cue.template, cue.rotation, cue.x, cue.y, life, life, seed)
        while (entries.size > InteractionFxLimits.MAX_DISINTEGRATIONS) entries.removeAt(0)
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
        }
        dirty = true
        return true
    }

    /** Ages every disintegration by [delta] and drops the finished ones. */
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
    fun published(): ImmutableList<StructureDisintegrationProjection> {
        if (!dirty) return cached
        cached = if (entries.isEmpty()) EMPTY else entries.mapToImmutableList { value ->
            StructureDisintegrationProjection(value.template, value.rotation, value.x, value.y, value.life, value.maxLife, value.seed)
        }
        dirty = false
        return cached
    }

    private class Disintegration(
        val template: StructureTemplateId,
        val rotation: Int,
        var x: Float,
        var y: Float,
        var life: Float,
        val maxLife: Float,
        val seed: Int,
    )

    private companion object {
        val EMPTY: ImmutableList<StructureDisintegrationProjection> = immutableListOf()
    }
}
