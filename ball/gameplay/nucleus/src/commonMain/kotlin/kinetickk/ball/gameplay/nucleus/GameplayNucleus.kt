// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GameplayCodexStacksProjection
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayConfigurationRejection
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandPulse
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayModuleResultOutput
import kinetickk.ball.gameplay.api.GameplayPointerAxis
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutputs
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReduction
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.reducer.initialEngineState
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.applyPreferences
import kinetickk.ball.gameplay.nucleus.simulation.copyForReduction
import kinetickk.ball.gameplay.nucleus.simulation.exitRun
import kinetickk.ball.gameplay.nucleus.simulation.pauseForOverlay
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.simulation.takeProgressUpdate
import kinetickk.ball.gameplay.nucleus.simulation.takeSoundCues
import kinetickk.ball.gameplay.nucleus.simulation.takeVisualFxCues
import kinetickk.ball.gameplay.nucleus.simulation.toRenderModel
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileSemanticHandle
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableListOfSize
import kinetickk.foundation.collections.toImmutableList

/** Pure, deterministic authority for GameplayRun decisions and projections. */
object GameplayNucleus {
    private val reducer = GameReducer()

    fun decide(
        state: GameplayState,
        pulse: GameplayNucleusPulse,
        context: GameplayContext = GameplayContext.Empty,
    ): GameplayDecision {
        return when (pulse) {
            is GameplayNucleusPulse.Intent -> decideInteraction(state, pulse.intent)
            is GameplayNucleusPulse.ModuleCommand -> decideModuleCommand(state, pulse.pulse, context)
            is GameplayNucleusPulse.ProfileModuleResultPulse -> decideProfileResult(state, pulse)
            is GameplayNucleusPulse.ProfileCommandRejectedBeforeAcceptance ->
                decideProfileRefusal(state, pulse)
        }
    }

    fun renderSnapshot(state: GameplayState): GameplayRenderSnapshot =
        GameplayRenderSnapshot(
            instanceId = state.instanceId,
            revision = state.revision,
            renderModel = state.engine?.model?.toRenderModel(),
            projectionSourceIdentity = state.engine?.renderProjectionIdentity,
        )

    /**
     * Builds a stamped immutable model with structural projection reuse from a verified committed
     * source/snapshot pair. Callers cannot accidentally mix a render snapshot with another source:
     * mismatched stamps, content, or engine nullability are rejected before mapping.
     */
    fun renderSnapshot(
        state: GameplayState,
        reusableState: GameplayState,
        reusableSnapshot: GameplayRenderSnapshot,
    ): GameplayRenderSnapshot {
        validateReusableRenderPair(state, reusableState, reusableSnapshot)
        val model = state.engine?.model
        val reusableSource = reusableState.engine?.model
        val reusableModel = reusableSnapshot.renderModel
        return GameplayRenderSnapshot(
            instanceId = state.instanceId,
            revision = state.revision,
            renderModel = model?.toRenderModel(
                reusableCollections = reusableModel,
                identitySource = reusableSource,
            ),
            projectionSourceIdentity = state.engine?.renderProjectionIdentity,
        )
    }

    /** Restamps a render-neutral transition without allocating another render model. */
    fun reuseRenderSnapshot(
        state: GameplayState,
        reusableState: GameplayState,
        reusableSnapshot: GameplayRenderSnapshot,
    ): GameplayRenderSnapshot {
        validateReusableRenderPair(state, reusableState, reusableSnapshot)
        require((state.engine == null) == (reusableSnapshot.renderModel == null)) {
            "Render-neutral transition changed Gameplay engine availability"
        }
        return GameplayRenderSnapshot(
            instanceId = state.instanceId,
            revision = state.revision,
            renderModel = reusableSnapshot.renderModel,
            projectionSourceIdentity = state.engine?.renderProjectionIdentity,
        )
    }

    private fun validateReusableRenderPair(
        state: GameplayState,
        reusableState: GameplayState,
        reusableSnapshot: GameplayRenderSnapshot,
    ) {
        val model = state.engine?.model
        val reusableSource = reusableState.engine?.model
        val reusableModel = reusableSnapshot.renderModel
        require(state.instanceId == reusableState.instanceId) {
            "Reusable Gameplay render source is not this instance's predecessor"
        }
        require(reusableState.revision.value < Long.MAX_VALUE) {
            "Reusable Gameplay render source revision cannot advance"
        }
        require(state.revision.value == reusableState.revision.value + 1L) {
            "Reusable Gameplay render source is not the immediately preceding revision"
        }
        require(reusableSnapshot.instanceId == reusableState.instanceId) {
            "Reusable Gameplay render snapshot belongs to another instance"
        }
        require(reusableSnapshot.revision == reusableState.revision) {
            "Reusable Gameplay render snapshot has a mismatched source revision"
        }
        require(reusableState.content === state.content) {
            "Reusable Gameplay render source belongs to different content"
        }
        require((reusableSource == null) == (reusableModel == null)) {
            "Reusable Gameplay render source/model nullability mismatch"
        }
        require(
            reusableSnapshot.projectionSourceIdentity ===
                reusableState.engine?.renderProjectionIdentity,
        ) {
            "Reusable Gameplay render snapshot was not projected from its claimed source"
        }
        require(
            (reusableModel == null) == (reusableSnapshot.projectionSourceIdentity == null),
        ) {
            "Reusable Gameplay render provenance/model nullability mismatch"
        }
        if (reusableModel != null) {
            require(reusableModel.content === reusableState.content) {
                "Reusable Gameplay render model belongs to different content"
            }
        }
        if (model != null) {
            require(model.content === state.content) {
                "Gameplay render source belongs to different content"
            }
        }
    }

    fun query(state: GameplayState, query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayRunStatusProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            phase = state.phase,
            profileCommandPending = state.pendingProfileCommand != null,
        )

    fun query(state: GameplayState, query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayActiveWeaponProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            weapon = state.engine?.model?.weapon,
        )

    fun query(state: GameplayState, query: GameplayQuery.GetCodexStacks): GameplayCodexStacksProjection =
        GameplayCodexStacksProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            itemStacks = state.engine?.model?.itemStacks?.toImmutableList()
                ?: immutableListOf(),
        )

    private fun decideModuleCommand(
        state: GameplayState,
        pulse: GameplayModuleCommandPulse,
        context: GameplayContext,
    ): GameplayDecision = when (val command = pulse.command) {
        GameplayModuleCommand.StartRun -> startRun(
            state = state,
            commandSource = pulse.commandSource,
            start = checkNotNull(context.start) { "Trusted Gameplay start Context was not supplied" },
        )
        GameplayModuleCommand.PauseForOverlay -> pauseForOverlay(state, pulse.commandSource)
        GameplayModuleCommand.ApplyPreferences -> applyPreferences(
            state,
            pulse.commandSource,
            checkNotNull(context.preferences) { "Trusted preferences read was not supplied" },
        )
        GameplayModuleCommand.ExitRun -> exitRun(state, pulse.commandSource)
    }

    private fun startRun(
        state: GameplayState,
        commandSource: GameplayCommandSourceToken,
        start: GameplayStartContext,
    ): GameplayDecision {
        when (state.phase) {
            GameplayRunPhase.CREATED -> Unit
            GameplayRunPhase.EXITED -> return rejected(GameplayRejection.RunExited)
            else -> return rejected(GameplayRejection.AlreadyStarted)
        }
        if (start == GameplayStartContext.ProfileUnavailable) {
            return rejected(GameplayRejection.ProfileBootstrapUnavailable)
        }
        val startInputs = (start as GameplayStartContext.Ready).inputs
        check(startInputs.content === state.content) { "Gameplay start changed captured Content" }
        validateStartInputs(startInputs)?.let { reason ->
            return rejected(GameplayRejection.InvalidStartConfiguration(reason))
        }

        val engine = initialEngineState(
            content = state.content,
            seed = startInputs.seed,
            bootstrapProgress = startInputs.profile,
        )
        engine.model.startRun()
        engine.model.takeSoundCues()
        engine.model.takeVisualFxCues()

        val revision = state.revision.next()
        val nextState = state.copy(
            revision = revision,
            phase = GameplayRunPhase.RUNNING,
            engine = engine,
        )
        return accepted(
            nextState,
            immutableListOf(
                completion(commandSource, sourceOrdinal = 0, GameplayModuleResult.RunStarted),
            ),
        )
    }

    private fun pauseForOverlay(
        state: GameplayState,
        commandSource: GameplayCommandSourceToken,
    ): GameplayDecision {
        lifecycleGate(state)?.let { return rejected(it) }
        if (state.phase != GameplayRunPhase.RUNNING) {
            return rejected(GameplayRejection.PauseUnavailable)
        }
        val reduction = reduceTrusted(state.engine!!) { it.pauseForOverlay() }
        val revision = state.revision.next()
        val prepared = state.copy(
            revision = revision,
            phase = reduction.state.model.phase.toRunPhase(),
            engine = reduction.state,
        )
        val outputs = reduction.outputs.toGameplayOutputs(
            instanceId = prepared.instanceId,
            revision = prepared.revision,
            trailingOutput = completion(
                commandSource,
                sourceOrdinal = reduction.outputs.size,
                result = GameplayModuleResult.OverlayPaused,
            ),
        )
        check(reduction.outputs.progressUpdate == null) {
            "Pause unexpectedly emitted Profile progress"
        }
        return accepted(prepared, outputs)
    }

    private fun applyPreferences(
        state: GameplayState,
        commandSource: GameplayCommandSourceToken,
        preferences: PlayerPreferences,
    ): GameplayDecision {
        lifecycleGate(state)?.let { return rejected(it) }
        if (!isValidPreferencesProjection(preferences)) {
            return rejected(GameplayRejection.InvalidPreferencesProjection)
        }
        val reduction = reduceTrusted(state.engine!!) { it.applyPreferences(preferences) }
        val revision = state.revision.next()
        val prepared = state.copy(revision = revision, engine = reduction.state)
        val outputs = reduction.outputs.toGameplayOutputs(
            instanceId = prepared.instanceId,
            revision = prepared.revision,
            trailingOutput = completion(
                commandSource,
                sourceOrdinal = reduction.outputs.size,
                result = GameplayModuleResult.PreferencesApplied,
            ),
        )
        check(reduction.outputs.progressUpdate == null) {
            "Preferences unexpectedly emitted Profile progress"
        }
        return accepted(prepared, outputs)
    }

    private fun exitRun(
        state: GameplayState,
        commandSource: GameplayCommandSourceToken,
    ): GameplayDecision {
        lifecycleGate(state)?.let { return rejected(it) }
        if (state.pendingProfileCommand != null) {
            return rejected(GameplayRejection.ProfileCommandPending)
        }
        val reduction = reduceTrusted(state.engine!!) { it.exitRun() }
        val revision = state.revision.next()
        val profileProgressPending = reduction.outputs.progressUpdate != null
        val outputs = reduction.outputs.toGameplayOutputs(
            instanceId = state.instanceId,
            revision = revision,
            trailingOutput = if (profileProgressPending) {
                null
            } else {
                completion(
                    commandSource,
                    sourceOrdinal = reduction.outputs.size,
                    result = GameplayModuleResult.RunExited(GameplayExitProgressResult.NoProgress),
                )
            },
        )
        val pending = reduction.outputs.toPendingProfileCommand(outputs, commandSource)
        val nextState = state.copy(
            revision = revision,
            phase = GameplayRunPhase.EXITED,
            engine = reduction.state,
            pendingProfileCommand = pending,
        )
        return accepted(nextState, outputs)
    }

    private fun decideInteraction(
        state: GameplayState,
        pulse: GameplayInteractionPulse,
    ): GameplayDecision {
        lifecycleGate(state)?.let { return rejected(it) }
        return when (val result = reducer.reduce(state.engine!!, pulse)) {
            is GameReductionResult.Rejected -> rejected(result.reason)
            is GameReductionResult.Accepted -> {
                val revision = state.revision.next()
                val outputs = result.outputs.toGameplayOutputs(
                    instanceId = state.instanceId,
                    revision = revision,
                )
                val pending = result.outputs.toPendingProfileCommand(
                    mappedOutputs = outputs,
                    exitCompletion = null,
                )
                if (pending != null && state.pendingProfileCommand != null) {
                    return rejected(GameplayRejection.ProfileCommandPending)
                }
                val nextState = state.copy(
                    revision = revision,
                    phase = result.state.model.phase.toRunPhase(),
                    engine = result.state,
                    pendingProfileCommand = pending ?: state.pendingProfileCommand,
                )
                accepted(
                    nextState,
                    outputs,
                )
            }
        }
    }

    private fun decideProfileResult(
        state: GameplayState,
        pulse: GameplayNucleusPulse.ProfileModuleResultPulse,
    ): GameplayDecision {
        val pending = checkNotNull(state.pendingProfileCommand)
        check(pulse.commandSource.semanticHandle == pending.request.semanticHandle)
        check(pulse.result == ProfileModuleResult.GameplayProgressApplied)
        return completeProfileProgress(state, pending, GameplayExitProgressResult.Applied)
    }

    private fun decideProfileRefusal(
        state: GameplayState,
        pulse: GameplayNucleusPulse.ProfileCommandRejectedBeforeAcceptance,
    ): GameplayDecision {
        val pending = checkNotNull(state.pendingProfileCommand)
        check(pulse.commandSource.semanticHandle == pending.request.semanticHandle)
        return completeProfileProgress(state, pending, GameplayExitProgressResult.NotApplied)
    }

    private fun completeProfileProgress(
        state: GameplayState,
        pending: PendingProfileCommand,
        progressResult: GameplayExitProgressResult,
    ): GameplayDecision {
        val revision = state.revision.next()
        val nextState = state.copy(revision = revision, pendingProfileCommand = null)
        val outputs = pending.exitCompletion?.let { commandSource ->
            immutableListOf(
                completion(
                    commandSource,
                    sourceOrdinal = 0,
                    result = GameplayModuleResult.RunExited(progressResult),
                ),
            )
        } ?: immutableListOf()
        return accepted(nextState, outputs)
    }

    private fun lifecycleGate(state: GameplayState): GameplayRejection? = when (state.phase) {
        GameplayRunPhase.CREATED -> GameplayRejection.NotStarted
        GameplayRunPhase.EXITED -> GameplayRejection.RunExited
        else -> null
    }

    fun validateStartInputs(
        inputs: GameplayStartInputs,
    ): GameplayConfigurationRejection? {
        val profile = inputs.profile
        val content = inputs.content
        if (!isValidPreferencesProjection(profile.preferences)) {
            return GameplayConfigurationRejection.INVALID_PREFERENCES
        }
        val contentWeapons = content.weapons.map { it.id }
        if (
            profile.loadout.selectedWeapon !in contentWeapons ||
            profile.loadout.unlockedWeapons.any { it !in contentWeapons }
        ) return GameplayConfigurationRejection.STARTING_WEAPON_MISSING
        if (profile.loadout.selectedWeapon !in profile.loadout.unlockedWeapons) {
            return GameplayConfigurationRejection.STARTING_WEAPON_LOCKED
        }
        if (profile.labProgress.ranks.size != content.metaUpgrades.size) {
            return GameplayConfigurationRejection.META_RANK_COUNT_MISMATCH
        }
        content.metaUpgrades.forEachIndexed { index, definition ->
            if (profile.labProgress.ranks[index] !in 0..definition.maxRanks) {
                return GameplayConfigurationRejection.META_RANK_OUT_OF_RANGE
            }
        }
        if (profile.collection.discoveredItemIds.any { content.item(it) == null }) {
            return GameplayConfigurationRejection.UNKNOWN_DISCOVERED_ITEM
        }
        if (
            profile.rebirthProgress.level !in content.rebirth.minimumLevel..content.rebirth.maximumLevel ||
            profile.rebirthProgress.highestCleared !in -1..profile.rebirthProgress.level
        ) return GameplayConfigurationRejection.REBIRTH_LEVEL_OUT_OF_RANGE
        if (profile.economy.matter < 0L) return GameplayConfigurationRejection.NEGATIVE_MATTER
        if (profile.economy.lifetimeMatter < profile.economy.matter) {
            return GameplayConfigurationRejection.LIFETIME_MATTER_BELOW_CURRENT
        }
        return null
    }

    fun isValidPreferencesProjection(preferences: PlayerPreferences): Boolean =
        preferences.masterVolume.isFinite() &&
            preferences.simulationSpeed.isFinite() &&
            preferences.textScale.isFinite() &&
            preferences == preferences.normalized() &&
            preferences.simulationSpeed in SIMULATION_SPEED_OPTIONS &&
            preferences.damageNumberTierThreshold in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS

    private fun reduceTrusted(
        engine: EngineState,
        mutation: (kinetickk.ball.gameplay.nucleus.simulation.MutableGameState) -> Unit,
    ): GameReduction {
        val candidate = engine.model.copyForReduction()
        mutation(candidate)
        val visualFxCues = candidate.takeVisualFxCues()
        val progressUpdate = candidate.takeProgressUpdate()
        val soundCues = candidate.takeSoundCues()
        val outputs = SimulationOutputs.create(
            visualFxCues = visualFxCues,
            progressUpdate = progressUpdate,
            advanceAudio = soundCues.isNotEmpty(),
            audioCues = soundCues,
        )
        return GameReduction(EngineState(candidate), outputs)
    }

    private fun SimulationOutputs.toGameplayOutputs(
        instanceId: GameplayInstanceId,
        revision: GameplayRevision,
        trailingOutput: GameplayOutput? = null,
    ): ImmutableList<GameplayOutput> {
        val sourceOutputCount = size
        val mappedOutputCount = sourceOutputCount + if (trailingOutput == null) 0 else 1
        return immutableListOfSize(mappedOutputCount) { sourceOrdinal ->
            if (sourceOrdinal == sourceOutputCount) {
                checkNotNull(trailingOutput)
            } else {
                toGameplayOutputAt(instanceId, revision, sourceOrdinal)
            }
        }
    }

    private fun SimulationOutputs.toGameplayOutputAt(
        instanceId: GameplayInstanceId,
        revision: GameplayRevision,
        sourceOrdinal: Int,
    ): GameplayOutput {
        var remainingIndex = sourceOrdinal
        visualFxCuesOrNull?.let { cues ->
            if (remainingIndex == 0) return GameplayOutput.EmitVisualFx(cues)
            remainingIndex--
        }
        progressUpdate?.let { update ->
            if (remainingIndex == 0) {
                return profileOutput(instanceId, revision, sourceOrdinal, update)
            }
            remainingIndex--
        }
        audioCuesOrNull?.let { cues ->
            if (remainingIndex == 0) {
                return GameplayOutput.AdvanceAudio(audioRealDeltaSeconds, cues)
            }
            remainingIndex--
        }
        if (ensuresAudioUnlocked && remainingIndex == 0) {
            return GameplayOutput.EnsureAudioUnlocked
        }
        error("Canonical Simulation output index was not resolved")
    }

    private fun profileOutput(
        instanceId: GameplayInstanceId,
        revision: GameplayRevision,
        sourceOrdinal: Int,
        update: GameplayProgressUpdate,
    ): GameplayOutput.SendProfileCommand {
        val handle = ProfileSemanticHandle(
            sourceInstance = ProfileCommandSource.GameplayRun(instanceId.runId.value),
            sourceRevision = revision.value,
            sourceOrdinal = sourceOrdinal,
        )
        val request = ProfileModuleCommandRequest(
            semanticHandle = handle,
            sourceOrdinal = sourceOrdinal,
            targetInstance = LOCAL_PROFILE_INSTANCE_ID,
            command = ProfileModuleCommand.ApplyGameplayProgress(update),
        )
        return GameplayOutput.SendProfileCommand(request)
    }

    private fun SimulationOutputs.toPendingProfileCommand(
        mappedOutputs: ImmutableList<GameplayOutput>,
        exitCompletion: GameplayCommandSourceToken?,
    ): PendingProfileCommand? {
        if (progressUpdate == null) return null
        val profileOutputIndex = if (visualFxCuesOrNull == null) 0 else 1
        val profileOutput = mappedOutputs[profileOutputIndex] as GameplayOutput.SendProfileCommand
        return PendingProfileCommand(profileOutput.request, exitCompletion)
    }

    private fun completion(
        commandSource: GameplayCommandSourceToken,
        sourceOrdinal: Int,
        result: GameplayModuleResult,
    ): GameplayOutput.CompleteCommand = GameplayOutput.CompleteCommand(
        GameplayModuleResultOutput(
            semanticHandle = commandSource.semanticHandle,
            sourceOrdinal = sourceOrdinal,
            commandSource = commandSource,
            result = result,
        ),
    )

    private fun accepted(
        nextState: GameplayState,
        outputs: ImmutableList<GameplayOutput>,
    ): GameplayDecision.Accepted = GameplayDecision.Accepted(
        GameplayAcceptedFrame(
            nextState = nextState,
            outputs = outputs,
        ),
    )

    private fun rejected(reason: GameplayRejection): GameplayDecision.Rejected =
        GameplayDecision.Rejected(reason)

    private fun GamePhase.toRunPhase(): GameplayRunPhase = when (this) {
        GamePhase.RUNNING -> GameplayRunPhase.RUNNING
        GamePhase.PAUSED -> GameplayRunPhase.PAUSED
        GamePhase.CHOICE -> GameplayRunPhase.CHOICE
        GamePhase.GAME_OVER -> GameplayRunPhase.GAME_OVER
        GamePhase.VICTORY -> GameplayRunPhase.VICTORY
    }

    private fun GameplayRevision.next(): GameplayRevision = GameplayRevision(value + 1L)
}
