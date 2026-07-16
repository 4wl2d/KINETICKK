// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus.transition

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.Decider
import kinetickk.application.runtime.Decision
import kinetickk.application.runtime.DecisionResult
import kinetickk.application.runtime.Rejected
import kinetickk.application.runtime.ConsistencyStamp
import kinetickk.features.game.nucleus.protocol.BrakeSource
import kinetickk.features.game.nucleus.protocol.CommandRequest
import kinetickk.features.game.nucleus.protocol.GameDecisionContext
import kinetickk.features.game.nucleus.protocol.GameCommand
import kinetickk.features.game.nucleus.protocol.GameFact
import kinetickk.features.game.nucleus.protocol.GameDependencyContract
import kinetickk.features.game.nucleus.protocol.GameDependencySource
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameOutputKind
import kinetickk.features.game.nucleus.protocol.GameProjectionPayload
import kinetickk.features.game.nucleus.protocol.GamePulse
import kinetickk.features.game.nucleus.protocol.GameRejection
import kinetickk.features.game.nucleus.protocol.GameRunStartContract
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleCommand
import kinetickk.features.game.nucleus.protocol.OperationId
import kinetickk.features.game.nucleus.protocol.ProjectionOutput
import kinetickk.features.game.nucleus.protocol.SemanticHandle
import kinetickk.features.game.nucleus.protocol.SemanticOutput
import kinetickk.features.game.nucleus.MutableGameState
import kinetickk.features.game.nucleus.GameBootstrapSnapshot
import kinetickk.features.game.nucleus.UiScreen
import kinetickk.features.game.nucleus.protocol.VisualFxCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

internal data class GameBallState(
    val model: MutableGameState,
    val settingsSource: ConsistencyStamp? = null,
    val profileSource: ConsistencyStamp? = null,
    val transitionSteps: Int = 0,
)

internal fun initialGameBallState(
    seed: Int,
    bootstrapProgress: GameBootstrapSnapshot?,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
): GameBallState = GameBallState(
    model = MutableGameState(
        seed = seed,
        initialMatter = initialMatter,
        initialRebirthLevel = initialRebirthLevel,
        bootstrapProgress = bootstrapProgress,
        externalAuthorities = true,
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
        return when (pulse) {
            is GameFact -> decideFact(state, pulse, context)
            is GameIntent -> decideIntent(state, pulse, context)
            is GameRunStartModuleCommand -> decideRunStartModuleCommand(state, pulse, context)
        }
    }

    private fun decideRunStartModuleCommand(
        state: GameBallState,
        command: GameRunStartModuleCommand,
        context: GameDecisionContext,
    ): DecisionResult<GameBallState, SemanticOutput> {
        validateRunStartModuleCommand(state, command, context)?.let { return Rejected(it) }

        val candidate = state.model.copyForDecision()
        candidate.startRun()
        val outputs = GameOutputBuilder(context.operationId)
        outputs.projection(
            localName = "root",
            visualFxCues = candidate.takeVisualFxCues().toImmutableList(),
        )
        candidate.takeAuthorityCommands().forEachIndexed { index, authorityCommand ->
            outputs.command(
                kind = authorityCommand.outputKind(),
                localName = "authority-$index",
                payload = authorityCommand,
            )
        }
        val cues = candidate.takeSoundCues()
        if (cues.isNotEmpty()) {
            outputs.command(
                kind = GameOutputKind.ADVANCE_AUDIO,
                localName = "audio-advance",
                payload = GameCommand.AdvanceAudio(
                    realDeltaSeconds = 0f,
                    cues = cues.toImmutableList(),
                ),
            )
        }
        return Accepted(
            Decision(
                nextState = state.copy(model = candidate, transitionSteps = 1),
                outputs = outputs.build(),
            ),
        )
    }

    private fun validateRunStartModuleCommand(
        state: GameBallState,
        command: GameRunStartModuleCommand,
        context: GameDecisionContext,
    ): GameRejection? {
        val source = command.commandSource
        if (source.sourceBallInstanceId != GameRunStartContract.SOURCE_BALL_INSTANCE_ID) {
            return GameRejection.InvalidRunStartCommandSource(
                field = "sourceBallInstanceId",
                reason = "unsupported source authority",
            )
        }
        if (source.sourceCommitRevision == 0uL) {
            return GameRejection.InvalidRunStartCommandSource(
                field = "sourceCommitRevision",
                reason = "must identify an accepted source commit",
            )
        }
        if (source.sourceOrdinal != 0u) {
            return GameRejection.InvalidRunStartCommandSource(
                field = "sourceOrdinal",
                reason = "must identify the single Game-start output",
            )
        }
        if (source.sourceOperationId == 0uL) {
            return GameRejection.InvalidRunStartCommandSource(
                field = "sourceOperationId",
                reason = "must identify a reserved source operation",
            )
        }
        if (source.sourceOutputKind != GameRunStartContract.SOURCE_OUTPUT_KIND) {
            return GameRejection.InvalidRunStartCommandSource(
                field = "sourceOutputKind",
                reason = "unsupported output kind",
            )
        }
        if (source.sourceLocalOrdinalOrName != GameRunStartContract.SOURCE_LOCAL_ORDINAL_OR_NAME) {
            return GameRejection.InvalidRunStartCommandSource(
                field = "sourceLocalOrdinalOrName",
                reason = "unsupported semantic handle name",
            )
        }

        val scope = command.causalBudgetScope
        if (
            scope.ownerBallInstanceId !=
            GameRunStartContract.CAUSAL_SCOPE_OWNER_BALL_INSTANCE_ID ||
            scope.operationId != source.sourceOperationId
        ) {
            return GameRejection.InvalidRunStartCausalContext(
                field = "causalBudgetScope",
                reason = "must preserve the initiating Game operation scope",
            )
        }
        if (
            context.causalBudgetScopeOwnerBallInstanceId != scope.ownerBallInstanceId ||
            context.causalBudgetScope.value != scope.operationId
        ) {
            return GameRejection.InvalidRunStartCausalContext(
                field = "decisionContext.causalBudgetScope",
                reason = "must match the imported command scope",
            )
        }
        if (
            command.causalDepth != GameRunStartContract.COMMAND_CAUSAL_DEPTH ||
            context.causalDepth != GameRunStartContract.COMMAND_CAUSAL_DEPTH
        ) {
            return GameRejection.InvalidRunStartCausalContext(
                field = "causalDepth",
                reason = "must be ${GameRunStartContract.COMMAND_CAUSAL_DEPTH}",
            )
        }

        val reference = command.runConfigurationReference
        if (
            reference.profileBallInstanceId != GameDependencyContract.PROFILE_BALL_INSTANCE_ID ||
            reference.profileStateSchemaVersion !=
            GameDependencyContract.PROFILE_STATE_SCHEMA_VERSION ||
            reference.profileCommitRevision == 0uL ||
            reference.rebirthLevel < 0
        ) {
            return GameRejection.InvalidRunConfigurationReference(
                field = "runConfigurationReference",
                reason = "must identify a supported accepted Profile snapshot",
            )
        }
        val expectedProfileSource = state.profileSource
        if (
            expectedProfileSource == null ||
            reference.profileBallInstanceId != expectedProfileSource.ballInstanceId ||
            reference.profileCommitRevision != expectedProfileSource.commitRevision ||
            reference.profileStateSchemaVersion != expectedProfileSource.stateSchemaVersion
        ) {
            return GameRejection.ProfileReferenceNotCurrent(
                received = reference,
                expected = expectedProfileSource,
            )
        }
        if (reference.rebirthLevel != state.model.rebirthLevel) {
            return GameRejection.RunStartRebirthLevelMismatch(
                received = reference.rebirthLevel,
                expected = state.model.rebirthLevel,
            )
        }
        return null
    }

    private fun decideFact(
        state: GameBallState,
        fact: GameFact,
        context: GameDecisionContext,
    ): DecisionResult<GameBallState, SemanticOutput> {
        validateDependencyObservation(state, fact)?.let { return Rejected(it) }
        val candidate = state.model.copyForDecision()
        when (fact) {
            is GameFact.DependenciesObserved -> candidate.observeDependencies(
                observedSettings = fact.settings,
                profile = fact.profile,
            )
        }
        val outputs = GameOutputBuilder(context.operationId).apply {
            projection(localName = "dependencies-observed", visualFxCues = immutableListOf())
        }
        return Accepted(
            Decision(
                nextState = state.copy(
                    model = candidate,
                    settingsSource = fact.settingsSource,
                    profileSource = fact.profileSource,
                    transitionSteps = 1,
                ),
                outputs = outputs.build(),
            ),
        )
    }

    private fun validateDependencyObservation(
        state: GameBallState,
        fact: GameFact,
    ): GameRejection? = when (fact) {
        is GameFact.DependenciesObserved ->
            validateDependencySource(
                dependency = GameDependencySource.SETTINGS,
                received = fact.settingsSource,
                lastAccepted = state.settingsSource,
                expectedBallInstanceId = GameDependencyContract.SETTINGS_BALL_INSTANCE_ID,
                expectedStateSchemaVersion = GameDependencyContract.SETTINGS_STATE_SCHEMA_VERSION,
            ) ?: validateDependencySource(
                dependency = GameDependencySource.PROFILE,
                received = fact.profileSource,
                lastAccepted = state.profileSource,
                expectedBallInstanceId = GameDependencyContract.PROFILE_BALL_INSTANCE_ID,
                expectedStateSchemaVersion = GameDependencyContract.PROFILE_STATE_SCHEMA_VERSION,
            )
    }

    private fun validateDependencySource(
        dependency: GameDependencySource,
        received: ConsistencyStamp,
        lastAccepted: ConsistencyStamp?,
        expectedBallInstanceId: String,
        expectedStateSchemaVersion: Int,
    ): GameRejection? {
        if (
            received.ballInstanceId != expectedBallInstanceId ||
            received.stateSchemaVersion != expectedStateSchemaVersion
        ) {
            return GameRejection.InvalidDependencySource(
                dependency = dependency,
                received = received,
                expectedBallInstanceId = expectedBallInstanceId,
                expectedStateSchemaVersion = expectedStateSchemaVersion,
            )
        }
        if (
            lastAccepted != null &&
            received.commitRevision < lastAccepted.commitRevision
        ) {
            return GameRejection.StaleDependencySource(
                dependency = dependency,
                receivedCommitRevision = received.commitRevision,
                lastAcceptedCommitRevision = lastAccepted.commitRevision,
            )
        }
        return null
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
                command(
                    kind = GameOutputKind.ENSURE_AUDIO_UNLOCKED,
                    localName = "audio-unlock",
                    payload = GameCommand.EnsureAudioUnlocked,
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
        candidate.takeAuthorityCommands().forEachIndexed { index, command ->
            outputs.command(
                kind = command.outputKind(),
                localName = "authority-$index",
                payload = command,
            )
        }

        val cues = candidate.takeSoundCues()
        if (intent is GameIntent.FrameElapsed || cues.isNotEmpty()) {
            outputs.command(
                kind = GameOutputKind.ADVANCE_AUDIO,
                localName = "audio-advance",
                payload = GameCommand.AdvanceAudio(
                    realDeltaSeconds = (intent as? GameIntent.FrameElapsed)?.realDeltaSeconds ?: 0f,
                    cues = cues.toImmutableList(),
                ),
            )
        }

        return Accepted(
            Decision(
                nextState = GameBallState(
                    model = candidate,
                    settingsSource = state.settingsSource,
                    profileSource = state.profileSource,
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
        const val TRANSITION_ARTIFACT = "game-v2"
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

    fun command(
        kind: GameOutputKind,
        localName: String,
        payload: GameCommand,
    ): SemanticHandle {
        val handle = SemanticHandle(
            operationId = operationId,
            outputKind = kind,
            localOrdinalOrName = localName,
        )
        outputs += CommandRequest(
            semanticHandle = handle,
            sourceOrdinal = outputs.size.toUInt(),
            payload = payload,
        )
        return handle
    }

    fun build(): ImmutableList<SemanticOutput> = outputs.toImmutableList()
}

private fun GameCommand.outputKind(): GameOutputKind = when (this) {
    is GameCommand.AdvanceAudio -> GameOutputKind.ADVANCE_AUDIO
    GameCommand.EnsureAudioUnlocked -> GameOutputKind.ENSURE_AUDIO_UNLOCKED
    is GameCommand.ChangeSettings -> GameOutputKind.CHANGE_SETTINGS
    is GameCommand.ChangeProfile -> GameOutputKind.CHANGE_PROFILE
    is GameCommand.BeginRebirth -> GameOutputKind.BEGIN_REBIRTH
}
