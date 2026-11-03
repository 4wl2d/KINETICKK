// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.director

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.simulation.enemies.EncounterState
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * Spawn pacing: the spawn clock, Elite and Herald schedules, the director's phase and budget, the planned and last
 * events, live encounter groups and the event cooldowns. Written by the director domain.
 */
internal class SpawnDirectorState(content: GameplayContentSnapshot, source: SpawnDirectorState?) {
    internal var spawnClock: Float = source?.spawnClock ?: 0f
    internal var nextEliteAt: Float = source?.nextEliteAt ?: content.tempo.firstEliteAtSeconds
    /** Elites spawned this run. */
    internal var elitesSpawned: Int = source?.elitesSpawned ?: 0
    /** Heralds spawned this run; each scheduled Herald arrives once. */
    internal var heraldsSpawned: Int = source?.heraldsSpawned ?: 0
    /** Spawn director pacing phase ([SpawnDirectorPhase] ordinal) and the run time it ends. */
    internal var directorPhase: Int = source?.directorPhase ?: 0
    internal var directorPhaseUntil: Float = source?.directorPhaseUntil ?: 0f
    /** Enemies' worth of spawn budget banked by the director; events spend it. */
    internal var spawnBudget: Float = source?.spawnBudget ?: 0f
    /** The planned next event ([SpawnEventKind] ordinal, or -1), its size and its single enemy type (-1 when mixed). */
    internal var pendingEventKind: Int = source?.pendingEventKind ?: -1
    internal var pendingEventSize: Int = source?.pendingEventSize ?: 0
    internal var pendingEventType: Int = source?.pendingEventType ?: -1
    /** Enemy types (bit per ordinal) used by the last and the previous event; they spawn at half weight. */
    internal var recentEventTypes: Long = source?.recentEventTypes ?: 0L
    internal var previousEventTypes: Long = source?.previousEventTypes ?: 0L
    /** Secret boss kinds spawned this run (bit per EnemyType ordinal); each arrives at most once. */
    internal var secretBossMask: Long = source?.secretBossMask ?: 0L
    /** Live encounter groups, at most MAX_ACTIVE_ENCOUNTERS; value-only, replaced on change. */
    internal var encounters: ImmutableList<EncounterState> = source?.encounters ?: immutableListOf()
    internal var ambushReadyAt: Float = source?.ambushReadyAt ?: 0f
    internal var encircleReadyAt: Float = source?.encircleReadyAt ?: 0f
    internal var wallReadyAt: Float = source?.wallReadyAt ?: 0f
    /** When an event may next exceed the ordinary cap by its own size. */
    internal var capOverflowReadyAt: Float = source?.capOverflowReadyAt ?: 0f
    /** Seconds the Core has kept ambush speed without a break. */
    internal var ambushSpeedSeconds: Float = source?.ambushSpeedSeconds ?: 0f
    /** Events fired this run and the kind, planned size and spawned count of the last one. */
    internal var spawnEventCount: Int = source?.spawnEventCount ?: 0
    internal var lastSpawnEventKind: Int = source?.lastSpawnEventKind ?: -1
    internal var lastSpawnEventPlanned: Int = source?.lastSpawnEventPlanned ?: 0
    internal var lastSpawnEventSpawned: Int = source?.lastSpawnEventSpawned ?: 0
}
