// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.engine

import kinetickk.core.collections.ImmutableList
import kinetickk.core.profile.api.GameplayProfileSnapshot
import kinetickk.feature.gameplay.domain.renderModel.GameplayRenderModel
import kinetickk.feature.gameplay.domain.protocol.GameEffect
import kinetickk.feature.gameplay.domain.protocol.GameplayAction
import kinetickk.feature.gameplay.domain.protocol.GameRejection
import kinetickk.feature.gameplay.domain.reducer.EngineState
import kinetickk.feature.gameplay.domain.reducer.GameReducer
import kinetickk.feature.gameplay.domain.reducer.GameReductionResult
import kinetickk.feature.gameplay.domain.reducer.initialEngineState
import kinetickk.feature.gameplay.domain.simulation.toRenderModel

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
