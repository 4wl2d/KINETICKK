// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.StructureField

/**
 * Run-long structure state: the structure window around the Core, the dungeon the Core is in, the spent structures
 * and the last announced entry. Written by the structures domain.
 */
internal class StructureRunState(source: StructureRunState?) {
    /** Structure walls, doors and signals of the window around the Core; an immutable value replaced on change. */
    internal var structureField: StructureField = source?.structureField ?: StructureField.EMPTY
    /** The structure the Core is inside (or was, within the leave radius), or null. Value-only. */
    internal var dungeon: DungeonState? = source?.dungeon
    /** Cleared or abandoned structures, oldest first, at most [ContentBounds.MAX_SPENT_STRUCTURES]; id or flag. */
    internal var spentStructureIds: List<Long> = source?.spentStructureIds ?: emptyList()
    /** Last structure whose entry banner was shown (re-entering does not repeat it). */
    internal var lastAnnouncedStructureId: Long = source?.lastAnnouncedStructureId ?: 0L
}
