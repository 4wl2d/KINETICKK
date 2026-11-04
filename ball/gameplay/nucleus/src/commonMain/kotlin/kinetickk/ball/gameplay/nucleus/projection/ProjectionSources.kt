// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.gameplay.api.BuildStatSummary
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.builds.CompiledBuild
import kinetickk.ball.gameplay.nucleus.simulation.builds.buildStats
import kinetickk.foundation.collections.ImmutableList

/**
 * The state values the tick path's last projection read that are replaced, never mutated, so the same reference
 * proves the same value: the compiled build (only `recompileBuild` assigns it), the offered choices and the equipped
 * relics (every writer assigns a new read-only list). Mutable lists, runtimes and storage arrays are never captured;
 * they reuse by content equality. A render snapshot carries this capture as its projection provenance.
 */
internal class ProjectionSources(
    /** The engine the snapshot was projected from ([kinetickk.ball.gameplay.nucleus.reducer.EngineState.renderProjectionIdentity]). */
    val engineIdentity: Any,
    val build: CompiledBuild,
    val choices: List<ChoiceOption>,
    val equippedRelics: List<EquippedRelic>,
    /** The stat rows the reward previews compare; read only while a choice is open, null otherwise. */
    val previewStats: ImmutableList<BuildStatSummary>?,
)

/** The capture of this state after its projection; an unchanged [previous] capture is kept without allocating. */
internal fun MutableGameState.captureProjectionSources(
    engineIdentity: Any,
    previous: ProjectionSources?,
): ProjectionSources {
    val previewStats = if (phase == GamePhase.CHOICE) buildStats(withContributions = false) else null
    if (previous != null && previous.engineIdentity === engineIdentity && previous.build === build &&
        previous.choices === choices && previous.equippedRelics === equippedRelics &&
        previous.previewStats == previewStats
    ) {
        return previous
    }
    return ProjectionSources(engineIdentity, build, choices, equippedRelics, previewStats)
}

/** The provenance a snapshot claims: its capture's engine, or the engine identity it carries directly. */
internal fun projectionProvenance(projectionSourceIdentity: Any?): Any? =
    (projectionSourceIdentity as? ProjectionSources)?.engineIdentity ?: projectionSourceIdentity
