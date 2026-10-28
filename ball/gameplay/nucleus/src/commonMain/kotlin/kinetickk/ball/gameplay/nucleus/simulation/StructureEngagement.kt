// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kotlin.math.hypot

/*
 * Structures and dungeons as an engagement, read by the sites, the director, the habitat picks and the pilots. The
 * dungeon itself runs in DungeonSystem.kt.
 */

/** True while a dungeon room is sealed. */
internal fun MutableGameState.structureEngagementActive(): Boolean = (dungeon?.engagedRoom ?: -1) >= 0

/** True once the run entered or spent a structure. */
internal fun MutableGameState.structureTouched(): Boolean = dungeon != null || spentStructureIds.isNotEmpty()

/** True while the Core is shut in a sealed room (ambient picks then use the STRUCTURE habitat). */
internal fun MutableGameState.structureInteriorActive(): Boolean = structureEngagementActive()

/**
 * Appends one optional-site mark per structure of the window, spent ones included (their walls still hurt): its
 * centre and the circumradius of its footprint. Returns the new mark count, at most [MAX_OPTIONAL_SITE_MARKS].
 */
internal fun MutableGameState.appendStructureMarks(out: FloatArray, count: Int): Int {
    var written = count
    val structures = structureField.structures
    for (index in structures.indices) {
        if (written >= MAX_OPTIONAL_SITE_MARKS) return written
        val structure = structures[index]
        val base = written * 3
        out[base] = structure.x
        out[base + 1] = structure.y
        out[base + 2] = hypot(structure.halfWidth, structure.halfHeight)
        written++
    }
    return written
}

/** The trial panel of an engaged dungeon, or null. */
internal fun MutableGameState.structureTrialProjection(): TrialProjection? = null

/** A world layer switch collapses the dungeon silently. */
internal fun MutableGameState.onStructureLayerChanged() = collapseDungeonForLayerChange()

/** The director's spawn-rate scale: slower while a room is sealed (outside spawns would only pile against its walls). */
internal fun MutableGameState.structureDirectorScale(): Float =
    if (structureEngagementActive()) content.structures.directorRateWhileSealed else 1f
