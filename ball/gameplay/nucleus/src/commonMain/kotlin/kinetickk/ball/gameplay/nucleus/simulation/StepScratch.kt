// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState.Companion.MAX_CHAIN_SCRATCH

/**
 * Per-step scratch of the combat and elements domains: the status cue budget, bounded chain targeting and the
 * enemy a hit is shattering. Each field is back at its initial value between fixed steps; a deep copy keeps only
 * [statusCuesThisStep] and starts the rest fresh.
 */
internal class StepScratch(source: StepScratch?) {
    /** Visual cue budget for statuses and reactions within the current fixed step. */
    internal var statusCuesThisStep: Int = source?.statusCuesThisStep ?: 0
    /** Candidate-private scratch for bounded chain targeting; never read across steps. */
    internal val chainScratch: IntArray = IntArray(MAX_CHAIN_SCRATCH)
    /** Nesting level of [chainScratch] use within the current step; zero between steps. */
    internal var chainDepth: Int = 0
    /** The enemy whose Freeze the current hit is shattering, so that hit's clauses still see it Frozen; -1 between hits. */
    internal var shatteringEnemyId: Int = -1
}
