// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.engine

import kinetickk.foundation.collections.ImmutableList
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.gameplay.nucleus.renderModel.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.protocol.GameEffect
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAction
import kinetickk.ball.gameplay.nucleus.protocol.GameRejection
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.reducer.initialEngineState
import kinetickk.ball.gameplay.nucleus.simulation.toRenderModel

/** Immutable state exposed after an accepted intent. */
data class GameSnapshot(
    val revision: ULong,
    val renderModel: GameplayRenderModel,
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
        renderModel = initialState.model.toRenderModel(),
    )

    fun dispatch(intent: GameplayAction): GameDispatchResult {
        if (currentSnapshot.revision == ULong.MAX_VALUE) {
            return GameDispatchResult.Rejected(GameRejection.RevisionExhausted)
        }

        return when (val result = reducer.reduce(state, intent)) {
            is GameReductionResult.Rejected -> GameDispatchResult.Rejected(result.reason)
            is GameReductionResult.Accepted -> {
                state = result.value.state
                currentSnapshot = GameSnapshot(
                    revision = currentSnapshot.revision + 1uL,
                    renderModel = state.model.toRenderModel(),
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
            bootstrapProgress: GameplayProfileSnapshot?,
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
