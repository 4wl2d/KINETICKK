// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GamePhase
import kinetickk.ball.gameplay.api.GameplayCodexStacksProjection
import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandAdmission
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayControlPulse
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRenderProjection
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.fx.InteractionFxReducer
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.MAX_GAMEPLAY_OUTPUTS_PER_DECISION
import kinetickk.ball.gameplay.nucleus.GameplayAcceptedFrame
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineDispatchGuard

/** Sole owner, acceptor, publisher, and ordered-output dispatcher for one GameplayRun. */
internal class GameComponent private constructor(
    initialState: GameplayState,
    private val profilePort: ProfilePort,
    private val audioExecutor: GameplayAudioExecutor,
    private val commandResultSink: (GameplayCommandResult.Accepted) -> Unit,
) : GameplayInteractionPort {
    private val dispatchGuard = InlineDispatchGuard()
    private val completions =
        BoundedCompletionDeque<GameplayWorkItem>(GAMEPLAY_COMPLETION_CAPACITY)
    private var committedState: GameplayState = initialState
    private var interactionFxReducer: InteractionFxReducer? = null
    private var profileDispatchSourceDepth: Int? = null

    override val instanceId
        get() = committedState.instanceId

    override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance =
        requireNotNull(dispatchRoot(pulse, GameplayContext.Local, reportAcceptance = true))

    override fun accept(
        command: GameplayCommand,
        admission: GameplayCommandAdmission,
    ): GameplayAcceptance = requireNotNull(
        dispatchRoot(
            pulse = command.pulse,
            context = GameplayContext(command, admission),
            reportAcceptance = true,
        ),
    )

    override fun query(query: GameplayQuery.GetRender): GameplayRenderProjection =
        GameplayNucleus.query(committedState, query)

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayNucleus.query(committedState, query)

    override fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayNucleus.query(committedState, query)

    override fun query(query: GameplayQuery.GetCodexStacks): GameplayCodexStacksProjection =
        GameplayNucleus.query(committedState, query)

    override fun visualFxSnapshot(): VisualFxProjection =
        interactionFxReducer?.snapshot() ?: VisualFxProjection.EMPTY

    internal fun receiveProfileCommandResult(result: ProfileCommandResult.Accepted) {
        check(dispatchGuard.isDispatching) {
            "Inline Profile completion arrived outside its Gameplay causal scope"
        }
        val pending = checkNotNull(committedState.pendingProfileCommand) {
            "Profile result arrived without a pending Gameplay command"
        }
        check(result.commandRef == pending.command.ref) {
            "Profile result command correlation mismatch"
        }
        val sourceDepth = checkNotNull(profileDispatchSourceDepth) {
            "Profile result arrived outside Profile output dispatch"
        }
        enqueueCompletion(
            GameplayControlPulse.ProfileCommandCompleted(result),
            sourceDepth + PROFILE_ACCEPTED_COMPLETION_DEPTH,
        )
    }

    internal fun stateSnapshot(): GameplayState = committedState

    private fun dispatchRoot(
        pulse: kinetickk.ball.gameplay.api.GameplayPulse,
        context: GameplayContext,
        reportAcceptance: Boolean,
    ): GameplayAcceptance? = dispatchGuard.dispatch {
        check(completions.isEmpty) { "Gameplay completion deque leaked across dispatches" }
        check(completions.tryAddLast(GameplayWorkItem(pulse, context, causalDepth = 0)))

        var rootAcceptance: GameplayAcceptance? = null
        var root = true
        var deferredFault: Throwable? = null
        while (!completions.isEmpty) {
            val item = checkNotNull(completions.removeFirstOrNull())
            val before = committedState
            when (val decision = GameplayNucleus.decide(before, item.pulse, item.context)) {
                is GameplayDecision.Rejected -> {
                    check(root && reportAcceptance) {
                        "A trusted Gameplay completion was rejected: ${decision.reason}"
                    }
                    rootAcceptance = GameplayAcceptance.Rejected(
                        instanceId = before.instanceId,
                        observedRevision = before.revision,
                        reason = decision.reason,
                    )
                }
                is GameplayDecision.Accepted -> {
                    preflight(before, item, decision.frame)
                    committedState = decision.frame.nextState
                    initializeInteractionFxIfStarted(before, item)
                    if (root && reportAcceptance) {
                        rootAcceptance = GameplayAcceptance.Accepted(
                            instanceId = committedState.instanceId,
                            revision = committedState.revision,
                        )
                    }
                    decision.frame.outputs.forEach { output ->
                        try {
                            execute(output, item.causalDepth)
                        } catch (failure: Throwable) {
                            if (deferredFault == null) deferredFault = failure
                        }
                    }
                }
            }
            root = false
        }

        deferredFault?.let { throw it }
        if (reportAcceptance) checkNotNull(rootAcceptance) else null
    }

    private fun preflight(
        before: GameplayState,
        item: GameplayWorkItem,
        frame: GameplayAcceptedFrame,
    ) {
        val next = frame.nextState
        check(next.instanceId == before.instanceId) { "Gameplay instance identity changed" }
        check(before.revision.value < Long.MAX_VALUE)
        check(next.revision.value == before.revision.value + 1L) {
            "Gameplay revision must advance exactly once"
        }
        if (before.content != null) {
            check(next.content === before.content) { "Captured Gameplay content identity changed" }
        }
        if (item.pulse is GameplaySessionPulse.StartRun) {
            check(next.content === item.pulse.configuration.content) {
                "Gameplay did not capture the accepted Content snapshot"
            }
        }
        check(frame.renderProjection.instanceId == next.instanceId)
        check(frame.renderProjection.revision == next.revision)
        check(
            (next.phase == GameplayRunPhase.CREATED) ==
                (frame.renderProjection.renderModel == null),
        ) { "Gameplay render projection does not match lifecycle state" }
        frame.renderProjection.renderModel?.let { render ->
            check(render.content === next.content) { "Render projection changed captured Content" }
            val expectedRenderPhase = when (next.phase) {
                GameplayRunPhase.CREATED -> error("Created GameplayRun cannot expose a render model")
                GameplayRunPhase.RUNNING -> GamePhase.RUNNING
                GameplayRunPhase.PAUSED -> GamePhase.PAUSED
                GameplayRunPhase.CHOICE -> GamePhase.CHOICE
                GameplayRunPhase.GAME_OVER -> GamePhase.GAME_OVER
                GameplayRunPhase.VICTORY -> GamePhase.VICTORY
                GameplayRunPhase.EXITED -> GamePhase.PAUSED
            }
            check(render.phase == expectedRenderPhase) {
                "Gameplay run phase and render phase diverged"
            }
        }

        check(frame.outputs.size <= MAX_GAMEPLAY_OUTPUTS_PER_DECISION) {
            "Gameplay output limit exceeded"
        }
        check(frame.outputs.zipWithNext().all { (left, right) ->
            left.dispatchOrder <= right.dispatchOrder
        }) { "Gameplay outputs are not in FX -> Profile -> Audio -> result order" }

        val profileOutputs = frame.outputs.filterIsInstance<GameplayOutput.SendProfileCommand>()
        check(profileOutputs.size <= 1) {
            "A Gameplay decision may issue at most one Profile command"
        }
        if (profileOutputs.isNotEmpty()) {
            check(before.pendingProfileCommand == null) {
                "Gameplay cannot issue a second Profile command while one is pending"
            }
            check(item.causalDepth + PROFILE_ACCEPTED_COMPLETION_DEPTH < MAX_GAMEPLAY_CAUSAL_DEPTH) {
                "Gameplay causal depth exhausted before Profile dispatch"
            }
            check(completions.remainingCapacity >= 1) {
                "Gameplay completion capacity exhausted before acceptance"
            }
            val pending = checkNotNull(next.pendingProfileCommand)
            check(profileOutputs.single().command == pending.command)
            check(pending.command.ref.sourceRevision == next.revision.value)
            check(
                pending.command.ref.sourceInstance ==
                    ProfileCommandSource.GameplayRun(next.instanceId.runId.value),
            )
        } else if (before.pendingProfileCommand == null) {
            check(next.pendingProfileCommand == null) {
                "Gameplay retained a Profile command without emitting it"
            }
        }

        frame.outputs.forEachIndexed { index, output ->
            when (output) {
                is GameplayOutput.CompleteCommand -> {
                    check(output.result.targetRevision == next.revision)
                    check(output.result.commandRef.targetInstance == next.instanceId)
                    check(index == frame.outputs.lastIndex) {
                        "Gameplay command completion must be the final output"
                    }
                }
                is GameplayOutput.SendProfileCommand -> {
                    check(output.command.ref.targetInstance == profilePort.instanceId)
                }
                is GameplayOutput.AdvanceAudio,
                is GameplayOutput.EmitVisualFx,
                GameplayOutput.EnsureAudioUnlocked,
                -> Unit
            }
        }
    }

    private fun initializeInteractionFxIfStarted(
        before: GameplayState,
        item: GameplayWorkItem,
    ) {
        if (before.phase != GameplayRunPhase.CREATED) return
        val start = item.pulse as? GameplaySessionPulse.StartRun ?: return
        check(interactionFxReducer == null)
        interactionFxReducer = InteractionFxReducer(start.configuration.seed)
    }

    private fun execute(output: GameplayOutput, sourceDepth: Int) {
        when (output) {
            is GameplayOutput.EmitVisualFx ->
                checkNotNull(interactionFxReducer).apply(output.cues)
            is GameplayOutput.SendProfileCommand -> executeProfileCommand(output, sourceDepth)
            is GameplayOutput.AdvanceAudio -> runCatching {
                audioExecutor.advance(output.realDeltaSeconds, output.cues)
            }
            GameplayOutput.EnsureAudioUnlocked -> runCatching {
                audioExecutor.ensureUnlocked()
            }
            is GameplayOutput.CompleteCommand -> commandResultSink(output.result)
        }
    }

    private fun executeProfileCommand(
        output: GameplayOutput.SendProfileCommand,
        sourceDepth: Int,
    ) {
        check(profileDispatchSourceDepth == null)
        profileDispatchSourceDepth = sourceDepth
        val acceptance = try {
            profilePort.accept(
                output.command,
                ProfileCommandAdmission(output.command.ref),
            )
        } finally {
            profileDispatchSourceDepth = null
        }
        when (acceptance) {
            is ProfileAcceptance.Accepted -> {
                check(completions.size > 0) {
                    "Accepted inline Profile command returned without its reserved result"
                }
            }
            is ProfileAcceptance.Rejected -> {
                check(acceptance.instanceId == output.command.ref.targetInstance)
                enqueueCompletion(
                    GameplayControlPulse.ProfileCommandRejectedBeforeAcceptance(
                        commandRef = output.command.ref,
                        rejection = acceptance,
                    ),
                    sourceDepth + PROFILE_REJECTED_COMPLETION_DEPTH,
                )
            }
        }
    }

    private fun enqueueCompletion(
        pulse: GameplayControlPulse,
        causalDepth: Int,
    ) {
        check(causalDepth < MAX_GAMEPLAY_CAUSAL_DEPTH)
        check(
            completions.tryAddLast(
                GameplayWorkItem(pulse, GameplayContext.Local, causalDepth),
            ),
        ) { "Pre-reserved Gameplay completion could not be retained" }
    }

    companion object {
        fun create(
            runId: RunId,
            profilePort: ProfilePort,
            audioExecutor: GameplayAudioExecutor,
            commandResultSink: (GameplayCommandResult.Accepted) -> Unit,
        ): GameComponent = GameComponent(
            initialState = GameplayState.initial(runId),
            profilePort = profilePort,
            audioExecutor = audioExecutor,
            commandResultSink = commandResultSink,
        )
    }
}

private data class GameplayWorkItem(
    val pulse: kinetickk.ball.gameplay.api.GameplayPulse,
    val context: GameplayContext,
    val causalDepth: Int,
)

private val GameplayOutput.dispatchOrder: Int
    get() = when (this) {
        is GameplayOutput.EmitVisualFx -> 0
        is GameplayOutput.SendProfileCommand -> 1
        is GameplayOutput.AdvanceAudio,
        GameplayOutput.EnsureAudioUnlocked,
        -> 2
        is GameplayOutput.CompleteCommand -> 3
    }

private const val GAMEPLAY_COMPLETION_CAPACITY: Int = 8
private const val MAX_GAMEPLAY_CAUSAL_DEPTH: Int = 8
private const val PROFILE_REJECTED_COMPLETION_DEPTH: Int = 1
private const val PROFILE_ACCEPTED_COMPLETION_DEPTH: Int = 2
