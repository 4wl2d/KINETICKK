// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.api.GamePhase
import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GameplayCodexStacksProjection
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandRefRejection
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayConfigurationRejection
import kinetickk.ball.gameplay.api.GameplayControlPulse
import kinetickk.ball.gameplay.api.GameplayExitProfileOutcome
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayProfileResultRejection
import kinetickk.ball.gameplay.api.GameplayPulse
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRenderProjection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutput
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.reducer.initialEngineState
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.simulation.takeSoundCues
import kinetickk.ball.gameplay.nucleus.simulation.takeVisualFxCues
import kinetickk.ball.gameplay.nucleus.simulation.toRenderModel
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandRef
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/** Pure, deterministic authority for GameplayRun decisions and projections. */
object GameplayNucleus {
    fun decide(
        state: GameplayState,
        pulse: GameplayPulse,
        context: GameplayContext = GameplayContext.Local,
    ): GameplayDecision {
        validateContext(state, pulse, context)?.let { return rejected(it) }
        if (state.revision.value == Long.MAX_VALUE) {
            return rejected(GameplayRejection.RevisionExhausted)
        }

        return when (pulse) {
            is GameplayControlPulse -> decideControl(state, pulse)
            is GameplaySessionPulse -> decideSession(state, pulse, context.command!!.ref)
            is GameplayInteractionPulse -> decideInteraction(state, pulse)
        }
    }

    fun query(state: GameplayState, query: GameplayQuery.GetRender): GameplayRenderProjection =
        renderProjection(state)

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
            itemStacks = state.engine?.model?.itemStacks?.asIterable()?.toImmutableList()
                ?: immutableListOf(),
        )

    private fun decideSession(
        state: GameplayState,
        pulse: GameplaySessionPulse,
        commandRef: kinetickk.ball.gameplay.api.GameplayCommandRef,
    ): GameplayDecision = when (pulse) {
        is GameplaySessionPulse.StartRun -> startRun(state, pulse.configuration, commandRef)
        GameplaySessionPulse.PauseForOverlay -> pauseForOverlay(state, commandRef)
        is GameplaySessionPulse.ApplyPreferences -> applyPreferences(state, pulse, commandRef)
        GameplaySessionPulse.ExitRun -> exitRun(state, commandRef)
    }

    private fun startRun(
        state: GameplayState,
        configuration: RunConfiguration,
        commandRef: kinetickk.ball.gameplay.api.GameplayCommandRef,
    ): GameplayDecision {
        when (state.phase) {
            GameplayRunPhase.CREATED -> Unit
            GameplayRunPhase.EXITED -> return rejected(GameplayRejection.RunExited)
            else -> return rejected(GameplayRejection.AlreadyStarted)
        }
        validateConfiguration(configuration)?.let {
            return rejected(GameplayRejection.InvalidConfiguration(it))
        }

        val engine = initialEngineState(
            content = configuration.content,
            seed = configuration.seed,
            bootstrapProgress = configuration.profile,
        )
        engine.model.startRun()
        // Start captures RNG/content and preserves the old initialized simulation, but its
        // initialization-only cues were never externally observable and remain drained here.
        engine.model.takeSoundCues()
        engine.model.takeVisualFxCues()

        val revision = state.revision.next()
        val nextState = GameplayState(
            instanceId = state.instanceId,
            revision = revision,
            phase = GameplayRunPhase.RUNNING,
            content = configuration.content,
            engine = engine,
            pendingProfileCommand = null,
            nextProfileCommandOrdinal = state.nextProfileCommandOrdinal,
        )
        return accepted(
            nextState,
            immutableListOf(
                GameplayOutput.CompleteCommand(
                    kinetickk.ball.gameplay.api.GameplayCommandResult.Accepted(
                        commandRef = commandRef,
                        targetRevision = revision,
                        outcome = GameplayCommandOutcome.RunStarted,
                    ),
                ),
            ),
        )
    }

    private fun pauseForOverlay(
        state: GameplayState,
        commandRef: kinetickk.ball.gameplay.api.GameplayCommandRef,
    ): GameplayDecision {
        lifecycleGate(state)?.let { return rejected(it) }
        if (state.phase != GameplayRunPhase.RUNNING) {
            return rejected(GameplayRejection.PauseUnavailable)
        }
        val reduction = reduce(state.engine!!, GameplayInteractionPulse.PauseForOverlay)
            ?: return rejected(GameplayRejection.PauseUnavailable)
        val revision = state.revision.next()
        val nextState = state.copy(
            revision = revision,
            phase = reduction.state.model.phase.toRunPhase(),
            engine = reduction.state,
        )
        val mapped = reduction.outputs.toGameplayOutputs(nextState, exitCompletion = null)
            ?: return rejected(GameplayRejection.RevisionExhausted)
        if (mapped.pending != null) return rejected(GameplayRejection.ProfileCommandPending)
        return accepted(
            nextState,
            mapped.outputs +
                GameplayOutput.CompleteCommand(
                    kinetickk.ball.gameplay.api.GameplayCommandResult.Accepted(
                        commandRef,
                        revision,
                        GameplayCommandOutcome.OverlayPaused,
                    ),
                ),
        )
    }

    private fun applyPreferences(
        state: GameplayState,
        pulse: GameplaySessionPulse.ApplyPreferences,
        commandRef: kinetickk.ball.gameplay.api.GameplayCommandRef,
    ): GameplayDecision {
        lifecycleGate(state)?.let { return rejected(it) }
        if (!isValidPreferences(pulse.preferences)) {
            return rejected(
                GameplayRejection.InvalidConfiguration(
                    GameplayConfigurationRejection.INVALID_PREFERENCES,
                ),
            )
        }
        val reduction = reduce(
            state.engine!!,
            GameplayInteractionPulse.PreferencesChanged(pulse.preferences),
        ) ?: return rejected(
            GameplayRejection.InvalidConfiguration(
                GameplayConfigurationRejection.INVALID_PREFERENCES,
            ),
        )
        val revision = state.revision.next()
        val nextState = state.copy(revision = revision, engine = reduction.state)
        val mapped = reduction.outputs.toGameplayOutputs(nextState, exitCompletion = null)
            ?: return rejected(GameplayRejection.RevisionExhausted)
        if (mapped.pending != null) return rejected(GameplayRejection.ProfileCommandPending)
        return accepted(
            nextState,
            mapped.outputs +
                GameplayOutput.CompleteCommand(
                    kinetickk.ball.gameplay.api.GameplayCommandResult.Accepted(
                        commandRef,
                        revision,
                        GameplayCommandOutcome.PreferencesApplied(reduction.state.model.settings),
                    ),
                ),
        )
    }

    private fun exitRun(
        state: GameplayState,
        commandRef: kinetickk.ball.gameplay.api.GameplayCommandRef,
    ): GameplayDecision {
        lifecycleGate(state)?.let { return rejected(it) }
        if (state.pendingProfileCommand != null) {
            return rejected(GameplayRejection.ProfileCommandPending)
        }
        val result = GameReducer().reduce(state.engine!!, GameplayInteractionPulse.ExitRunRequested)
        if (result is GameReductionResult.Rejected) return rejected(result.reason)
        result as GameReductionResult.Accepted

        val revision = state.revision.next()
        val prepared = state.copy(
            revision = revision,
            phase = GameplayRunPhase.EXITED,
            engine = result.value.state,
        )
        val mapped = result.value.outputs.toGameplayOutputs(prepared, commandRef)
            ?: return rejected(GameplayRejection.RevisionExhausted)
        val pending = mapped.pending
        val nextState = prepared.copy(
            pendingProfileCommand = pending,
            nextProfileCommandOrdinal = if (pending == null) {
                state.nextProfileCommandOrdinal
            } else {
                state.nextProfileCommandOrdinal.nextOrdinal()
            },
        )
        val outputs = if (pending == null) {
            mapped.outputs + GameplayOutput.CompleteCommand(
                kinetickk.ball.gameplay.api.GameplayCommandResult.Accepted(
                    commandRef,
                    revision,
                    GameplayCommandOutcome.RunExited(GameplayExitProfileOutcome.NoProgress),
                ),
            )
        } else {
            mapped.outputs
        }
        return accepted(nextState, outputs)
    }

    private fun decideInteraction(
        state: GameplayState,
        pulse: GameplayInteractionPulse,
    ): GameplayDecision {
        lifecycleGate(state)?.let { return rejected(it) }

        // Session commands are now the sole lifecycle/configuration writers. These variants stay
        // in the closed Interaction inventory only until old UI hosts finish migrating.
        if (
            pulse == GameplayInteractionPulse.PauseForOverlay ||
            pulse == GameplayInteractionPulse.ExitRunRequested ||
            pulse is GameplayInteractionPulse.PreferencesChanged
        ) {
            return accepted(state.copy(revision = state.revision.next()), immutableListOf())
        }

        val result = GameReducer().reduce(state.engine!!, pulse)
        return when (result) {
            is GameReductionResult.Rejected -> rejected(result.reason)
            is GameReductionResult.Accepted -> {
                val revision = state.revision.next()
                val prepared = state.copy(
                    revision = revision,
                    phase = result.value.state.model.phase.toRunPhase(),
                    engine = result.value.state,
                )
                val mapped = result.value.outputs.toGameplayOutputs(prepared, exitCompletion = null)
                    ?: return rejected(GameplayRejection.RevisionExhausted)
                if (mapped.pending != null && state.pendingProfileCommand != null) {
                    return rejected(GameplayRejection.ProfileCommandPending)
                }
                val nextState = prepared.copy(
                    pendingProfileCommand = mapped.pending ?: state.pendingProfileCommand,
                    nextProfileCommandOrdinal = if (mapped.pending == null) {
                        state.nextProfileCommandOrdinal
                    } else {
                        state.nextProfileCommandOrdinal.nextOrdinal()
                    },
                )
                accepted(nextState, mapped.outputs)
            }
        }
    }

    private fun decideControl(
        state: GameplayState,
        pulse: GameplayControlPulse,
    ): GameplayDecision {
        val pending = state.pendingProfileCommand
            ?: return rejected(
                GameplayRejection.UnexpectedProfileResult(
                    GameplayProfileResultRejection.NO_COMMAND_PENDING,
                ),
            )
        val commandRef = when (pulse) {
            is GameplayControlPulse.ProfileCommandCompleted -> pulse.result.commandRef
            is GameplayControlPulse.ProfileCommandRejectedBeforeAcceptance -> pulse.commandRef
        }
        if (commandRef != pending.command.ref) {
            return rejected(
                GameplayRejection.UnexpectedProfileResult(
                    GameplayProfileResultRejection.COMMAND_REF_MISMATCH,
                ),
            )
        }

        val profileOutcome = when (pulse) {
            is GameplayControlPulse.ProfileCommandCompleted -> {
                if (pulse.result.outcome != ProfileCommandOutcome.GameplayProgressApplied) {
                    return rejected(
                        GameplayRejection.UnexpectedProfileResult(
                            GameplayProfileResultRejection.OUTCOME_MISMATCH,
                        ),
                    )
                }
                GameplayExitProfileOutcome.ProgressApplied
            }
            is GameplayControlPulse.ProfileCommandRejectedBeforeAcceptance -> {
                if (pulse.rejection.instanceId != pending.command.ref.targetInstance) {
                    return rejected(
                        GameplayRejection.UnexpectedProfileResult(
                            GameplayProfileResultRejection.TARGET_INSTANCE_MISMATCH,
                        ),
                    )
                }
                GameplayExitProfileOutcome.ProgressRejected(
                    observedRevision = pulse.rejection.observedRevision,
                    reason = pulse.rejection.reason,
                )
            }
        }

        val revision = state.revision.next()
        val nextState = state.copy(revision = revision, pendingProfileCommand = null)
        val outputs = pending.exitCompletion?.let { completion ->
            immutableListOf(
                GameplayOutput.CompleteCommand(
                    kinetickk.ball.gameplay.api.GameplayCommandResult.Accepted(
                        completion,
                        revision,
                        GameplayCommandOutcome.RunExited(profileOutcome),
                    ),
                ),
            )
        } ?: immutableListOf()
        return accepted(nextState, outputs)
    }

    private fun validateContext(
        state: GameplayState,
        pulse: GameplayPulse,
        context: GameplayContext,
    ): GameplayRejection? {
        val command = context.command
        val admission = context.admission
        if (pulse is GameplaySessionPulse) {
            if (command == null && admission == null) {
                return GameplayRejection.InvalidCommandRef(
                    GameplayCommandRefRejection.WRONG_SOURCE_KIND,
                )
            }
            if (
                command == null ||
                admission == null ||
                command.pulse != pulse ||
                command.ref != admission.commandRef
            ) {
                return GameplayRejection.InvalidCommandRef(
                    GameplayCommandRefRejection.ADMISSION_MISMATCH,
                )
            }
            if (command.ref.targetInstance != state.instanceId) {
                return GameplayRejection.InvalidCommandRef(GameplayCommandRefRejection.WRONG_TARGET)
            }
            if (command.ref.sourceInstance != GameplayCommandSource.LocalSession) {
                return GameplayRejection.InvalidCommandRef(
                    GameplayCommandRefRejection.WRONG_SOURCE_KIND,
                )
            }
            return null
        }
        if (command != null || admission != null) {
            return GameplayRejection.InvalidCommandRef(GameplayCommandRefRejection.WRONG_SOURCE_KIND)
        }
        return null
    }

    private fun lifecycleGate(state: GameplayState): GameplayRejection? = when (state.phase) {
        GameplayRunPhase.CREATED -> GameplayRejection.NotStarted
        GameplayRunPhase.EXITED -> GameplayRejection.RunExited
        else -> null
    }

    private fun validateConfiguration(
        configuration: RunConfiguration,
    ): GameplayConfigurationRejection? {
        val profile = configuration.profile
        val content = configuration.content
        if (!isValidPreferences(profile.preferences)) {
            return GameplayConfigurationRejection.INVALID_PREFERENCES
        }
        val contentWeapons = content.weapons.map { it.id }
        if (
            profile.loadout.selectedWeapon !in contentWeapons ||
            profile.loadout.unlockedWeapons.any { it !in contentWeapons }
        ) {
            return GameplayConfigurationRejection.STARTING_WEAPON_MISSING
        }
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
            profile.rebirthProgress.level !in
            content.rebirth.minimumLevel..content.rebirth.maximumLevel ||
            profile.rebirthProgress.highestCleared !in -1..profile.rebirthProgress.level
        ) {
            return GameplayConfigurationRejection.REBIRTH_LEVEL_OUT_OF_RANGE
        }
        if (profile.economy.matter < 0L) {
            return GameplayConfigurationRejection.NEGATIVE_MATTER
        }
        if (profile.economy.lifetimeMatter < profile.economy.matter) {
            return GameplayConfigurationRejection.LIFETIME_MATTER_BELOW_CURRENT
        }
        return null
    }

    private fun isValidPreferences(preferences: kinetickk.ball.profile.api.PlayerPreferences): Boolean =
        preferences.masterVolume.isFinite() &&
            preferences.simulationSpeed.isFinite() &&
            preferences.textScale.isFinite() &&
            preferences == preferences.normalized() &&
            preferences.simulationSpeed in MutableGameState.SIMULATION_SPEEDS &&
            preferences.damageNumberTierThreshold in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS

    private fun reduce(
        engine: EngineState,
        pulse: GameplayInteractionPulse,
    ): kinetickk.ball.gameplay.nucleus.reducer.GameReduction? =
        when (val result = GameReducer().reduce(engine, pulse)) {
            is GameReductionResult.Accepted -> result.value
            is GameReductionResult.Rejected -> null
        }

    private data class MappedOutputs(
        val outputs: ImmutableList<GameplayOutput>,
        val pending: PendingProfileCommand?,
    )

    private fun ImmutableList<SimulationOutput>.toGameplayOutputs(
        nextState: GameplayState,
        exitCompletion: kinetickk.ball.gameplay.api.GameplayCommandRef?,
    ): MappedOutputs? {
        if (
            nextState.nextProfileCommandOrdinal == Int.MAX_VALUE &&
            any { it is SimulationOutput.PublishProgress }
        ) {
            return null
        }
        var pending: PendingProfileCommand? = null
        val mapped = buildList {
            this@toGameplayOutputs.forEach { output ->
                when (output) {
                    is SimulationOutput.EmitVisualFx -> add(GameplayOutput.EmitVisualFx(output.cues))
                    is SimulationOutput.PublishProgress -> {
                        check(pending == null) { "A Gameplay decision may emit at most one Profile command" }
                        val ref = ProfileCommandRef(
                            sourceInstance = ProfileCommandSource.GameplayRun(
                                nextState.instanceId.runId.value,
                            ),
                            targetInstance = LOCAL_PROFILE_INSTANCE_ID,
                            sourceRevision = nextState.revision.value,
                            ordinal = nextState.nextProfileCommandOrdinal,
                        )
                        val command = ProfileCommand(ref, ProfilePulse.ApplyGameplayProgress(output.update))
                        pending = PendingProfileCommand(command, exitCompletion)
                        add(GameplayOutput.SendProfileCommand(command))
                    }
                    is SimulationOutput.AdvanceAudio -> add(
                        GameplayOutput.AdvanceAudio(output.realDeltaSeconds, output.cues),
                    )
                    SimulationOutput.EnsureAudioUnlocked -> add(GameplayOutput.EnsureAudioUnlocked)
                }
            }
        }.toImmutableList()
        return MappedOutputs(mapped, pending)
    }

    private operator fun ImmutableList<GameplayOutput>.plus(
        output: GameplayOutput,
    ): ImmutableList<GameplayOutput> = (this.asIterable() + output).toImmutableList()

    private fun accepted(
        nextState: GameplayState,
        outputs: ImmutableList<GameplayOutput>,
    ): GameplayDecision.Accepted {
        check(outputs.size <= MAX_GAMEPLAY_OUTPUTS_PER_DECISION) {
            "Gameplay semantic output bound exceeded: ${outputs.size}"
        }
        val completionIndex = outputs.indexOfFirst { it is GameplayOutput.CompleteCommand }
        check(completionIndex < 0 || completionIndex == outputs.lastIndex) {
            "Gameplay command completion must be the final semantic output"
        }
        return GameplayDecision.Accepted(
            GameplayAcceptedFrame(
                nextState = nextState,
                renderProjection = renderProjection(nextState),
                outputs = outputs,
            ),
        )
    }

    private fun rejected(reason: GameplayRejection): GameplayDecision.Rejected =
        GameplayDecision.Rejected(reason)

    private fun renderProjection(state: GameplayState): GameplayRenderProjection =
        GameplayRenderProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            renderModel = state.engine?.model?.toRenderModel(),
        )

    private fun GamePhase.toRunPhase(): GameplayRunPhase = when (this) {
        GamePhase.RUNNING -> GameplayRunPhase.RUNNING
        GamePhase.PAUSED -> GameplayRunPhase.PAUSED
        GamePhase.CHOICE -> GameplayRunPhase.CHOICE
        GamePhase.GAME_OVER -> GameplayRunPhase.GAME_OVER
        GamePhase.VICTORY -> GameplayRunPhase.VICTORY
    }

    private fun GameplayRevision.next(): GameplayRevision = GameplayRevision(value + 1L)

    private fun Int.nextOrdinal(): Int {
        check(this < Int.MAX_VALUE) { "Gameplay Profile-command ordinal exhausted" }
        return this + 1
    }
}
