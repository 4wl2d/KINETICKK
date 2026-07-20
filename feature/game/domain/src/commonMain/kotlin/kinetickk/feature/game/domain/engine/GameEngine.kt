// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.engine

import kinetickk.core.collections.ImmutableList
import kinetickk.feature.game.domain.model.StoredProgress
import kinetickk.feature.game.domain.projection.GameProjection
import kinetickk.feature.game.domain.protocol.GameEffect
import kinetickk.feature.game.domain.protocol.GameIntent
import kinetickk.feature.game.domain.protocol.GameRejection
import kinetickk.feature.game.domain.reducer.EngineState
import kinetickk.feature.game.domain.reducer.GameReducer
import kinetickk.feature.game.domain.reducer.GameReductionResult
import kinetickk.feature.game.domain.reducer.initialEngineState
import kinetickk.feature.game.domain.simulation.toProjection

/** Immutable state exposed after an accepted intent. */
data class GameSnapshot(
    val revision: ULong,
    val projection: GameProjection,
)

sealed interface GameDispatchResult {
    data class Committed(
        val snapshot: GameSnapshot,
        val effects: ImmutableList<GameEffect>,
    ) : GameDispatchResult

    data class Rejected(val reason: GameRejection) : GameDispatchResult
}

/**
 * Synchronous unidirectional game engine: Intent -> Reducer -> State + Effects.
 *
 * The engine commits state before returning effects. It never executes platform resources.
 */
class GameEngine private constructor(
    initialState: EngineState,
) {
    private val reducer = GameReducer()
    private var state = initialState
    private var currentSnapshot = GameSnapshot(
        revision = 0uL,
        projection = initialState.model.toProjection(),
    )

    fun dispatch(intent: GameIntent): GameDispatchResult {
        if (currentSnapshot.revision == ULong.MAX_VALUE) {
            return GameDispatchResult.Rejected(GameRejection.RevisionExhausted)
        }

        return when (val result = reducer.reduce(state, intent)) {
            is GameReductionResult.Rejected -> GameDispatchResult.Rejected(result.reason)
            is GameReductionResult.Accepted -> {
                state = result.value.state
                currentSnapshot = GameSnapshot(
                    revision = currentSnapshot.revision + 1uL,
                    projection = state.model.toProjection(),
                )
                GameDispatchResult.Committed(
                    snapshot = currentSnapshot,
                    effects = result.value.effects,
                )
            }
        }
    }

    fun snapshot(): GameSnapshot = currentSnapshot

    companion object {
        fun create(
            bootstrapProgress: StoredProgress?,
            seed: Int = 731_991,
            initialMatter: Int? = null,
            initialRebirthLevel: Int = 0,
        ): GameEngine = GameEngine(
            initialState = initialEngineState(
                seed = seed,
                bootstrapProgress = bootstrapProgress,
                initialMatter = initialMatter,
                initialRebirthLevel = initialRebirthLevel,
            ),
        )
    }
}
