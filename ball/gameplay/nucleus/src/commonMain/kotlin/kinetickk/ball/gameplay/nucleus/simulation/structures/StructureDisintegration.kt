// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.math.hypot

/*
 * Disintegration: a cleared structure, or one abandoned after a fight, leaves the structure window in the step it is
 * spent, so its walls stop colliding at once; presentation shatters it from a StructureDisintegrated cue (never read
 * back). The Architect's collapse and a full spent set shatter every windowed structure the same way. A structure the
 * window does not hold (a layer switch already rebuilt it for the other layer) leaves silently. Only finishes,
 * abandons and collapses allocate here.
 */

/**
 * Retires the just spent structure [structureId]: it disintegrates, and when that spend filled the spent set every
 * other windowed structure goes too (the full set counts them all as spent).
 */
internal fun MutableGameState.retireSpentStructure(structureId: Long) {
    disintegrateStructure(structureId)
    if (!spentStructureSetFull()) return
    disintegrateWindowStructures()
    rebuildStructureField()
}

/** Removes the windowed structure [structureId] and shatters it (cue, shockwave, burst, rumble); a no-op otherwise. */
internal fun MutableGameState.disintegrateStructure(structureId: Long) {
    val structure = removeStructureFromWindow(structureId) ?: return
    val template = content.structures.template(structure.template)
    val colour = template.element?.let(FxColorCode::elementTone) ?: 1
    emitStructureDisintegrated(structure)
    shockwave(structure.x, structure.y, 0.9f, hypot(structure.halfWidth, structure.halfHeight) + 120f, colour)
    burst(structure.x, structure.y, 36, colour)
    emitSound(GameplayAudioCue.STRUCTURE_COLLAPSE)
}

/**
 * Every windowed structure bursts at its centre and shatters, with one rumble when any stood. The caller rebuilds the
 * window afterwards (the Architect's collapse key, or the full spent set), which leaves it empty.
 */
internal fun MutableGameState.disintegrateWindowStructures() {
    val standing = structures.structureField.structures
    for (index in standing.indices) {
        val structure = standing[index]
        shockwave(structure.x, structure.y, 0.6f, 300f, 3)
        burst(structure.x, structure.y, 24, 3)
        emitStructureDisintegrated(structure)
    }
    if (standing.isNotEmpty()) emitSound(GameplayAudioCue.STRUCTURE_COLLAPSE)
}

private fun MutableGameState.emitStructureDisintegrated(structure: PlacedStructure) {
    emitVisualFx(VisualFxCue.StructureDisintegrated(structure.template, structure.rotation, structure.x, structure.y))
}
