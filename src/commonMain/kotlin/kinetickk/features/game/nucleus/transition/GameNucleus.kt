// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus.transition

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.Decider
import kinetickk.application.runtime.Decision
import kinetickk.application.runtime.DecisionResult
import kinetickk.application.runtime.Rejected
import kinetickk.features.game.nucleus.protocol.BrakeSource
import kinetickk.features.game.nucleus.protocol.EffectRequest
import kinetickk.features.game.nucleus.protocol.GameDecisionContext
import kinetickk.features.game.nucleus.protocol.GameEffect
import kinetickk.features.game.nucleus.protocol.GameFact
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameOutputKind
import kinetickk.features.game.nucleus.protocol.GameProjectionPayload
import kinetickk.features.game.nucleus.protocol.GamePulse
import kinetickk.features.game.nucleus.protocol.GameRejection
import kinetickk.features.game.nucleus.protocol.OperationId
import kinetickk.features.game.nucleus.protocol.PersistenceStatus
import kinetickk.features.game.nucleus.protocol.ProgressProvider
import kinetickk.features.game.nucleus.protocol.ProjectionOutput
import kinetickk.features.game.nucleus.protocol.SemanticHandle
import kinetickk.features.game.nucleus.protocol.SemanticOutput
import kinetickk.features.game.nucleus.MutableGameState
import kinetickk.features.game.nucleus.StoredProgress
import kinetickk.features.game.nucleus.UiScreen
import kinetickk.features.game.nucleus.protocol.VisualFxCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

internal data class GameBallState(
    val model: MutableGameState,
    val persistenceGeneration: Long = 0L,
    val outstandingPersistence: SemanticHandle? = null,
    val persistenceStatus: PersistenceStatus = PersistenceStatus.NeverRequested,
    val transitionSteps: Int = 0,
)

internal fun initialGameBallState(
    seed: Int,
    bootstrapProgress: StoredProgress?,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
): GameBallState = GameBallState(
    model = MutableGameState(
        seed = seed,
        initialMatter = initialMatter,
        initialRebirthLevel = initialRebirthLevel,
        bootstrapProgress = bootstrapProgress,
    ),
)

internal class GameNucleus : Decider<GameBallState, GamePulse, GameDecisionContext, SemanticOutput> {
    override fun decide(
        state: GameBallState,
        pulse: GamePulse,
        context: GameDecisionContext,
    ): DecisionResult<GameBallState, SemanticOutput> {
        if (context.transitionArtifact != TRANSITION_ARTIFACT) {
            return Rejected(GameRejection.InvalidInput("transitionArtifact", "unsupported artifact"))
        }
        if (context.operationId.value == 0uL) {
            return Rejected(GameRejection.InvalidInput("operationId", "must be reserved"))
        }
        if (pulse is GameFact && pulse.handle.operationId != context.operationId) {
            return Rejected(GameRejection.InvalidInput("operationId", "must match Fact handle"))
        }
        return when (pulse) {
            is GameFact -> decideFact(state, pulse, context)
            is GameIntent -> decideIntent(state, pulse, context)
        }
    }

    private fun decideFact(
        state: GameBallState,
        fact: GameFact,
        context: GameDecisionContext,
    ): DecisionResult<GameBallState, SemanticOutput> {
        if (fact.provider != ProgressProvider.PLATFORM_LOCAL) {
            return Rejected(
                GameRejection.InvalidFactProvider(
                    received = fact.provider,
                    expected = ProgressProvider.PLATFORM_LOCAL,
                ),
            )
        }
        if (fact.handle != state.outstandingPersistence) {
            return Rejected(GameRejection.StaleFact(fact.handle, state.outstandingPersistence))
        }
        val status = when (fact) {
            is GameFact.ProgressPersisted -> PersistenceStatus.Persisted(fact.handle)
            is GameFact.ProgressPersistenceOutcomeUnknown -> PersistenceStatus.OutcomeUnknown(
                handle = fact.handle,
                reason = fact.reason,
            )
        }
        val outputs = GameOutputBuilder(context.operationId).apply {
            projection(
                localName = "persistence-completion",
                visualFxCues = immutableListOf(),
            )
        }
        return Accepted(
            Decision(
                nextState = state.copy(
                    outstandingPersistence = null,
                    persistenceStatus = status,
                    transitionSteps = 1,
                ),
                outputs = outputs.build(),
            ),
        )
    }

    private fun decideIntent(
        state: GameBallState,
        intent: GameIntent,
        context: GameDecisionContext,
    ): DecisionResult<GameBallState, SemanticOutput> {
        validate(state, intent)?.let { return Rejected(it) }

        if (intent == GameIntent.UserGestureObserved) {
            val outputs = GameOutputBuilder(context.operationId).apply {
                projection("root", immutableListOf())
                effect(
                    kind = GameOutputKind.ENSURE_AUDIO_UNLOCKED,
                    localName = "audio-unlock",
                    payload = GameEffect.EnsureAudioUnlocked,
                )
            }
            return Accepted(
                Decision(
                    nextState = state.copy(transitionSteps = 1),
                    outputs = outputs.build(),
                ),
            )
        }

        val candidate = state.model.copyForDecision()
        applyIntent(candidate, intent)

        val outputs = GameOutputBuilder(context.operationId)
        outputs.projection(
            localName = "root",
            visualFxCues = candidate.takeVisualFxCues().toImmutableList(),
        )
        val persistenceSnapshot = candidate.takePersistenceRequest()
        var nextGeneration = state.persistenceGeneration
        var outstanding = state.outstandingPersistence
        var persistenceStatus = state.persistenceStatus
        if (persistenceSnapshot != null) {
            if (nextGeneration == Long.MAX_VALUE) {
                return Rejected(GameRejection.GenerationExhausted(nextGeneration))
            }
            nextGeneration++
            val handle = outputs.effect(
                kind = GameOutputKind.PERSIST_PROGRESS,
                localName = "generation-$nextGeneration",
                payload = GameEffect.PersistProgress(persistenceSnapshot),
            )
            outstanding = handle
            persistenceStatus = PersistenceStatus.Pending(handle)
        }

        val cues = candidate.takeSoundCues()
        if (intent is GameIntent.FrameElapsed || cues.isNotEmpty()) {
            outputs.effect(
                kind = GameOutputKind.ADVANCE_AUDIO,
                localName = "audio-advance",
                payload = GameEffect.AdvanceAudio(
                    settings = candidate.settings,
                    realDeltaSeconds = (intent as? GameIntent.FrameElapsed)?.realDeltaSeconds ?: 0f,
                    cues = cues.toImmutableList(),
                ),
            )
        }

        return Accepted(
            Decision(
                nextState = GameBallState(
                    model = candidate,
                    persistenceGeneration = nextGeneration,
                    outstandingPersistence = outstanding,
                    persistenceStatus = persistenceStatus,
                    transitionSteps = if (intent is GameIntent.FrameElapsed) {
                        candidate.lastTransitionSteps
                    } else {
                        1
                    },
                ),
                outputs = outputs.build(),
            ),
        )
    }

    private fun applyIntent(state: MutableGameState, intent: GameIntent) {
        when (intent) {
            is GameIntent.FrameElapsed -> state.update(intent.realDeltaSeconds)
            is GameIntent.ViewportChanged -> state.resize(intent.width, intent.height, intent.density)
            is GameIntent.PointerMoved -> state.updatePointer(intent.x, intent.y, intent.active)
            is GameIntent.PointerPressed -> state.pointerPressed(intent.x, intent.y)
            GameIntent.PointerReleased -> state.pointerReleased()
            is GameIntent.BrakeChanged -> when (intent.source) {
                BrakeSource.KEYBOARD -> state.setBrake(intent.active)
                BrakeSource.SECONDARY_POINTER -> state.setSecondaryBrake(intent.active)
            }
            GameIntent.DashRequested -> state.requestDash()
            GameIntent.PauseToggled -> state.togglePause()
            GameIntent.EscapeRequested -> state.handleEscape()
            is GameIntent.ScreenOpenRequested -> when (intent.screen) {
                UiScreen.SETTINGS -> state.openSettings()
                UiScreen.LAB -> state.openLab()
                UiScreen.ARMORY -> state.openArmory()
                UiScreen.REBIRTH -> state.openRebirth()
                UiScreen.CODEX -> state.openCodex()
                UiScreen.GAME -> state.closeOverlay()
            }
            GameIntent.MuteToggled -> state.toggleMute()
            is GameIntent.ChoiceSelected -> state.choose(intent.index)
            GameIntent.ChoicesRerolled -> state.rerollChoices()
            GameIntent.EnterPressed -> state.handleEnter()
            GameIntent.RunStartRequested -> state.startRun()
            GameIntent.ReturnToMenuRequested -> state.returnToMenu()
            GameIntent.RebirthRequested -> state.requestRebirth()
            is GameIntent.CoreShapeSelected -> state.setCoreShape(intent.shape)
            is GameIntent.MetaUpgradePurchaseRequested -> state.buyMetaUpgrade(intent.upgrade)
            is GameIntent.WeaponPurchaseOrEquipRequested -> state.buyOrEquipWeapon(intent.weapon)
            GameIntent.UserGestureObserved -> error("handled before transaction cloning")
        }
    }

    private fun validate(
        state: GameBallState,
        intent: GameIntent,
    ): GameRejection.InvalidInput? = when (intent) {
        is GameIntent.FrameElapsed -> bounded(
            field = "realDeltaSeconds",
            value = intent.realDeltaSeconds,
            minimum = MIN_FRAME_DELTA_SECONDS,
            maximum = MAX_FRAME_DELTA_SECONDS,
        )
        is GameIntent.ViewportChanged ->
            bounded("width", intent.width, MIN_VIEWPORT_DIMENSION, MAX_VIEWPORT_DIMENSION)
                ?: bounded("height", intent.height, MIN_VIEWPORT_DIMENSION, MAX_VIEWPORT_DIMENSION)
                ?: bounded("density", intent.density, MIN_DENSITY, MAX_DENSITY)
        is GameIntent.PointerMoved ->
            bounded("x", intent.x, 0f, state.model.screenWidth)
                ?: bounded("y", intent.y, 0f, state.model.screenHeight)
        is GameIntent.PointerPressed ->
            bounded("x", intent.x, 0f, state.model.screenWidth)
                ?: bounded("y", intent.y, 0f, state.model.screenHeight)
        is GameIntent.ChoiceSelected -> if (intent.index in 0..3) {
            null
        } else {
            GameRejection.InvalidInput("index", "must be in 0..3")
        }
        else -> null
    }

    private fun bounded(
        field: String,
        value: Float,
        minimum: Float,
        maximum: Float,
    ): GameRejection.InvalidInput? = when {
        !value.isFinite() -> GameRejection.InvalidInput(field, "must be finite")
        value < minimum || value > maximum ->
            GameRejection.InvalidInput(field, "must be in [$minimum, $maximum]")
        else -> null
    }

    private companion object {
        const val TRANSITION_ARTIFACT = "game-v1"
        const val MIN_FRAME_DELTA_SECONDS = 0f
        const val MAX_FRAME_DELTA_SECONDS = 1f
        const val MIN_VIEWPORT_DIMENSION = 1f
        const val MAX_VIEWPORT_DIMENSION = 32_768f
        const val MIN_DENSITY = 0.5f
        const val MAX_DENSITY = 8f
    }
}

/**
 * The only Game output construction path. It assigns canonical contiguous source ordinals while
 * the runtime still independently verifies the complete envelope batch before acceptance.
 */
private class GameOutputBuilder(
    private val operationId: OperationId,
) {
    private val outputs = mutableListOf<SemanticOutput>()

    fun projection(
        localName: String,
        visualFxCues: ImmutableList<VisualFxCue>,
    ) {
        outputs += ProjectionOutput(
            semanticHandle = SemanticHandle(
                operationId = operationId,
                outputKind = GameOutputKind.GAME_PROJECTION_CHANGED,
                localOrdinalOrName = localName,
            ),
            sourceOrdinal = outputs.size.toUInt(),
            payload = GameProjectionPayload.GameProjectionChanged(visualFxCues),
        )
    }

    fun effect(
        kind: GameOutputKind,
        localName: String,
        payload: GameEffect,
    ): SemanticHandle {
        val handle = SemanticHandle(
            operationId = operationId,
            outputKind = kind,
            localOrdinalOrName = localName,
        )
        outputs += EffectRequest(
            semanticHandle = handle,
            sourceOrdinal = outputs.size.toUInt(),
            payload = payload,
        )
        return handle
    }

    fun build(): ImmutableList<SemanticOutput> = outputs.toImmutableList()
}
