// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.CombatFieldTable
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState.Companion.BREAK_BURST
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState.Companion.REACTION_BURST

/**
 * Lingering combat fields, the field kinds the Core stood in, and the reaction and break token budgets. Written by the
 * elements domain.
 */
internal class ElementRunState(source: ElementRunState?) {
    /** Lingering fields; a deep copy forks the table. */
    internal val combatFieldTable: CombatFieldTable = source?.combatFieldTable?.fork() ?: CombatFieldTable()
    /** Field kinds the Core stood in at the last field pass, one bit per [CombatFieldKind] ordinal. */
    internal var coreFieldMask: Int = source?.coreFieldMask ?: 0
    /** When entering a field may next count as a field entry. */
    internal var fieldEnterReadyAt: Float = source?.fieldEnterReadyAt ?: 0f
    internal var reactionTokens: Float = source?.reactionTokens ?: REACTION_BURST
    internal var breakTokens: Float = source?.breakTokens ?: BREAK_BURST

    /** A new run has no field and full reaction and break budgets. */
    fun reset() {
        combatFieldTable.clear()
        coreFieldMask = 0
        fieldEnterReadyAt = 0f
        reactionTokens = REACTION_BURST
        breakTokens = BREAK_BURST
    }
}
