// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

/**
 * Lingering effects reactions leave on the Core (absolute run times), Seismic's pending aftershocks and the reaction
 * dispatch counters. Written by the elements domain.
 */
internal class CoreReactionState(source: CoreReactionState?) {
    // Core reaction state: lingering effects reactions leave on the Core (absolute run times).
    internal var ablazeUntil: Float = source?.ablazeUntil ?: 0f
    internal var frictionlessUntil: Float = source?.frictionlessUntil ?: 0f
    internal var blastJumpUntil: Float = source?.blastJumpUntil ?: 0f
    internal var weaveUntil: Float = source?.weaveUntil ?: 0f
    internal var weaveDamage: Float = source?.weaveDamage ?: 0f
    internal var weaveRange: Float = source?.weaveRange ?: 0f
    /** Ice shards orbiting the Core (at most [MAX_GUARD_SHARDS]), until when and what each strikes for. */
    internal var guardShards: Int = source?.guardShards ?: 0
    internal var guardUntil: Float = source?.guardUntil ?: 0f
    internal var guardDamage: Float = source?.guardDamage ?: 0f
    /** The one Void Anchor (enemy id, -1 for none) and when it lapses. */
    internal var voidAnchorId: Int = source?.voidAnchorId ?: -1
    internal var voidAnchorUntil: Float = source?.voidAnchorUntil ?: 0f
    internal var overcurrentReadyAt: Float = source?.overcurrentReadyAt ?: 0f
    internal var flameRefundReadyAt: Float = source?.flameRefundReadyAt ?: 0f
    /** The enemy whose execute is under way, so its Data latches to the Core; -1 between executes. */
    internal var erasureLatchId: Int = source?.erasureLatchId ?: -1
    /** When a reaction or item may next push the Core. */
    internal var coreImpulseReadyAt: Float = source?.coreImpulseReadyAt ?: 0f
    internal var primedDashUntil: Float = source?.primedDashUntil ?: 0f
    /** Dash heat spent toward the next Dash-heat-to-skills refund (below its step of 10). */
    internal var dashHeatBank: Float = source?.dashHeatBank ?: 0f
    internal var phaseUntil: Float = source?.phaseUntil ?: 0f
    /** Seismic's keystone keeps two pending aftershocks: when each strikes (negative when free), where and how hard. */
    internal var aftershockAt0: Float = source?.aftershockAt0 ?: -1f
    internal var aftershockX0: Float = source?.aftershockX0 ?: 0f
    internal var aftershockY0: Float = source?.aftershockY0 ?: 0f
    internal var aftershockDamage0: Float = source?.aftershockDamage0 ?: 0f
    internal var aftershockAt1: Float = source?.aftershockAt1 ?: -1f
    internal var aftershockX1: Float = source?.aftershockX1 ?: 0f
    internal var aftershockY1: Float = source?.aftershockY1 ?: 0f
    internal var aftershockDamage1: Float = source?.aftershockDamage1 ?: 0f
    /** Erased bolts and cashed marks announced within the current fixed step; zero between steps. */
    internal var shotErasedDispatches: Int = source?.shotErasedDispatches ?: 0
    internal var markDispatches: Int = source?.markDispatches ?: 0
    /** Hostile bolts erased this run (a run statistic). */
    internal var shotsErased: Long = source?.shotsErased ?: 0L
}
