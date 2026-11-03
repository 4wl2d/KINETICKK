// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.model.ActiveBuff
import kinetickk.ball.gameplay.nucleus.model.RunFloatArray
import kinetickk.ball.gameplay.nucleus.model.RunIntArray

/**
 * The build's run-long inventory and clause state: owned and Reborn item stacks, the acquisition order, clause timers
 * and counters, status accumulators, active buffs and the clause and build-hook counters. Written by the build domain
 * and item acquisition.
 */
internal class BuildRunState(content: GameplayContentSnapshot, source: BuildRunState?) {
    internal val itemStacks: RunIntArray = source?.itemStacks?.copy()
        ?: RunIntArray(IntArray(content.items.size))
    internal val rebornItems: RunIntArray = source?.rebornItems?.copy()
        ?: RunIntArray(IntArray(content.items.size))
    /**
     * Build gains in order: item ids at first acquisition (a replaced active item's id leaves it) and relic binds or
     * rank-three upgrades as [relicGain] entries. Decides affinity ties. Immutable value, replaced on change.
     */
    internal var acquisitionOrder: List<Int> = source?.acquisitionOrder ?: emptyList()
    internal val clauseReadyAt: RunFloatArray = source?.clauseReadyAt?.copy()
        ?: RunFloatArray(FloatArray(content.totalClauseCount))
    internal val clauseCounters: RunIntArray = source?.clauseCounters?.copy()
        ?: RunIntArray(IntArray(content.totalClauseCount))
    /** Fractional status stacks per item, so stack magnitudes apply deterministically without RNG. */
    internal val statusAccumulators: RunFloatArray = source?.statusAccumulators?.copy()
        ?: RunFloatArray(FloatArray(content.items.size))
    /** Earliest time each item may apply statuses again (per-item rate bound). */
    internal val itemStatusReadyAt: RunFloatArray = source?.itemStatusReadyAt?.copy()
        ?: RunFloatArray(FloatArray(content.items.size))
    internal var buffs: List<ActiveBuff> = source?.buffs ?: emptyList()
    internal var clauseDepth: Int = source?.clauseDepth ?: 0
    internal var comboKillCounter: Int = source?.comboKillCounter ?: 0
    internal var arsenalCounter: Int = source?.arsenalCounter ?: 0

    /** A new run owns no item and no buff; every clause timer, counter and status accumulator starts at zero. */
    fun reset() {
        itemStacks.fill(0)
        rebornItems.fill(0)
        acquisitionOrder = emptyList()
        clauseReadyAt.fill(0f)
        clauseCounters.fill(0)
        statusAccumulators.fill(0f)
        itemStatusReadyAt.fill(0f)
        buffs = emptyList()
        comboKillCounter = 0
        clauseDepth = 0
        arsenalCounter = 0
    }
}
