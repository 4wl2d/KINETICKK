// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.gameplay.nucleus.render.TrialClock
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.ball.gameplay.nucleus.render.TrialSource
import kinetickk.ball.gameplay.nucleus.render.TrialValue
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

/**
 * The trial panel of the dungeon the Core holds, or null without one or for a secret template (the Doodle shows no
 * panel): the template's name, one segment per room that counts (garrisoned rooms and the final room) with the cleared
 * ones filled and counted, no clock and the template's rewards. A dungeon is no site: its family is a placeholder the
 * panel never reads (it names, draws and explains a dungeon by its source), so no content instruction is carried
 * either — the panel's rules for a dungeon are UI text.
 */
internal fun MutableGameState.structureTrialProjection(): TrialProjection? {
    val held = dungeon ?: return null
    val template = content.structures.template(held.template)
    if (template.secret) return null
    val counted = template.roomsToClearMask
    val rooms = counted.countOneBits()
    val cleared = (held.clearedRooms and counted).countOneBits()
    return TrialProjection(
        family = SiteFamily.CACHE,
        kind = null,
        title = template.name,
        rules = "",
        clock = TrialClock.NONE,
        seconds = 0f,
        segments = rooms,
        progress = cleared.toFloat() / rooms,
        value = TrialValue.COUNT,
        done = cleared.toFloat(),
        required = rooms.toFloat(),
        reward = TrialReward.STRUCTURE_REWARDS,
        source = TrialSource.STRUCTURE,
        structure = held.template,
    )
}

/** A world layer switch collapses the dungeon silently. */
internal fun MutableGameState.onStructureLayerChanged() = collapseDungeonForLayerChange()

/** The director's spawn-rate scale: slower while a room is sealed (outside spawns would only pile against its walls). */
internal fun MutableGameState.structureDirectorScale(): Float =
    if (structureEngagementActive()) content.structures.directorRateWhileSealed else 1f
