// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPresentationPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplayRunPort
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.GameplaySettingsApplied
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.GameplayOverlayPaused
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.fx.InteractionFxReducer
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.MAX_GAMEPLAY_OUTPUTS_PER_DECISION
import kinetickk.ball.gameplay.nucleus.GameplayAcceptedFrame
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.foundation.dispatch.call
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileProgress
import kinetickk.ball.gameplay.api.GameplayRunExited

/** Sole owner, acceptor, atomic publisher, and ordered-output dispatcher for one GameplayRun. */
internal class GameComponent private constructor(
    initialState: GameplayState,
    private val profilePort: ProfileReadPort,
    private val profileProgress: ProfileProgress,
    private val audioExecutor: GameplayAudioExecutor,
    private val seed: Int,
) : GameplayRunPort, GameplayPresentationPort, GameplayInteractionPort {
    private val acceptance = InlineAcceptance(gameplayCompletionDeque<GameplayWorkItem>())

    private val localOutputItem = GameplayWorkItem.Local
    private var committedFrame: CommittedGameplayFrame = CommittedGameplayFrame(
        state = initialState,
        renderSnapshot = GameplayNucleus.renderSnapshot(initialState),
    )
    private val committedState: GameplayState
        get() = committedFrame.state
    private val committedRenderSnapshot: GameplayRenderSnapshot
        get() = committedFrame.renderSnapshot
    private var interactionFxReducer: InteractionFxReducer? = null

    override val instanceId
        get() = committedState.instanceId

    override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance =
        dispatchLocal(pulse)

    override fun applyPreferences(
        preferences: PlayerPreferences,
        reply: InlineReply<GameplaySettingsApplied, GameplayRefusal>,
    ) = dispatchDirect(reply) { GameplayWorkItem.Settings(GameplayNucleusPulse.ApplyPreferences(preferences), reply) }

    override fun startRun(reply: InlineReply<GameplayRunStarted, GameplayRefusal>) =
        dispatchDirect(reply) { GameplayWorkItem.Starting(trustedStartContext(), reply) }

    override fun pauseForOverlay(reply: InlineReply<GameplayOverlayPaused, GameplayRefusal>) =
        dispatchDirect(reply) { GameplayWorkItem.Pausing(reply) }

    override fun exitRun(reply: InlineReply<GameplayRunExited, GameplayRefusal>) =
        dispatchDirect(reply, MAX_EXIT_REVISIONS_PER_DISPATCH) { GameplayWorkItem.Exiting(reply) }

    private fun <Result : Any> dispatchDirect(
        reply: InlineReply<Result, GameplayRefusal>,
        requiredRevisions: Long = 1L,
        itemForAcceptedCall: () -> GameplayWorkItem.Direct<Result>,
    ) {
        reply.checkAvailable()
        if (acceptance.isDispatching || !acceptance.isEmpty) {
            reply.refused(GameplayRefusal.Busy)
            return
        }
        if (!hasGameplayRevisionCapacity(committedState.revision, requiredRevisions)) {
            reply.refused(GameplayRefusal.RevisionCapacityExhausted)
            return
        }
        acceptance.dispatch {
            val item = itemForAcceptedCall()
            when (val decision = GameplayNucleus.decide(committedState, item.pulse, item.context)) {
                is GameplayDecision.Rejected -> reply.refused(GameplayRefusal.DecisionRejected(decision.reason))
                is GameplayDecision.Accepted -> dispatchAccepted(decision.frame, item)
            }
        }
    }

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayNucleus.query(committedState, query)

    override fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayNucleus.query(committedState, query)

    override fun query(query: GameplayQuery.GetBuildSummary): GameplayBuildSummaryProjection =
        GameplayNucleus.query(committedState, query)

    override fun renderSnapshot(): GameplayRenderSnapshot = committedRenderSnapshot

    override fun visualFxSnapshot(): VisualFxProjection =
        interactionFxReducer?.snapshot() ?: VisualFxProjection.EMPTY

    internal fun stateSnapshot(): GameplayState = committedState

    private fun trustedStartContext(): GameplayContext {
        val projection = profilePort.query(ProfileQuery.GetRunBootstrap)
        check(projection.instanceId == profilePort.instanceId) {
            "Profile bootstrap projection came from the wrong instance"
        }
        return when (val result = projection.result) {
            is ProfileRunBootstrapResult.Ready -> GameplayContext(
                start = GameplayStartContext.Ready(
                    GameplayStartInputs(
                        content = committedState.content,
                        profile = result.snapshot,
                        seed = seed,
                    ),
                ),
            )
            is ProfileRunBootstrapResult.Unavailable -> GameplayContext(
                start = GameplayStartContext.ProfileUnavailable,
            )
        }
    }

    private fun dispatchLocal(pulse: GameplayInteractionPulse): GameplayAcceptance =
        acceptance.dispatch {
            check(committedState.revision.value <= Long.MAX_VALUE - MAX_LOCAL_REVISIONS_PER_DISPATCH) {
                "Gameplay local revision capacity exhausted before Intent construction"
            }
            val before = committedState
            when (val decision = GameplayNucleus.decide(
                before,
                GameplayNucleusPulse.Intent(pulse),
                GameplayContext.Empty,
            )) {
                is GameplayDecision.Rejected -> GameplayAcceptance.Rejected(
                    instanceId = before.instanceId,
                    observedRevision = before.revision,
                    reason = decision.reason,
                )
                is GameplayDecision.Accepted -> {
                    dispatchAccepted(
                        rootFrame = decision.frame,
                        rootItem = localOutputItem,
                        renderModelNeutralTransition = pulse === GameplayInteractionPulse.DashRequested,
                    )
                    GameplayAcceptance.Accepted(
                        instanceId = decision.frame.nextState.instanceId,
                        revision = decision.frame.nextState.revision,
                    )
                }
            }
        }

    /** Shared source, expanded at ingress to keep the input path free of extra call boundaries. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun acceptFrame(
        before: GameplayState,
        item: GameplayWorkItem,
        frame: GameplayAcceptedFrame,
        renderModelNeutralTransition: Boolean = false,
    ) {
        val renderSnapshot = preflight(
            before = before,
            renderModelNeutralTransition = renderModelNeutralTransition,
            item = item,
            frame = frame,
        )
        publish(frame.nextState, renderSnapshot)
        initializeInteractionFxIfStarted(before, item)
    }

    /** Inline the shared loop to retain the allocation-free local dispatch path. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun dispatchAccepted(
        rootFrame: GameplayAcceptedFrame,
        rootItem: GameplayWorkItem,
        renderModelNeutralTransition: Boolean = false,
    ) {
        acceptance.acceptAndDrain(
            rootItem = rootItem,
            rootFrame = rootFrame,
            outputs = { it.outputs },
            acceptFrame = { item, frame ->
                acceptFrame(
                    before = committedState,
                    item = item,
                    frame = frame,
                    renderModelNeutralTransition = renderModelNeutralTransition && item === rootItem,
                )
            },
            decideCompletion = { completion ->
                when (val decision = GameplayNucleus.decide(
                    committedState,
                    completion.pulse,
                    completion.context,
                )) {
                    is GameplayDecision.Accepted -> decision.frame
                    is GameplayDecision.Rejected ->
                        error("A trusted Gameplay completion was rejected: ${decision.reason}")
                }
            },
            execute = { output, item -> execute(output, item) },
        )
    }

    private fun preflight(
        before: GameplayState,
        renderModelNeutralTransition: Boolean,
        item: GameplayWorkItem,
        frame: GameplayAcceptedFrame,
    ): GameplayRenderSnapshot {
        val next = frame.nextState
        check(next.instanceId == before.instanceId) { "Gameplay instance identity changed" }
        check(next.content === before.content) { "Captured Gameplay content identity changed" }
        check(before.revision.value < Long.MAX_VALUE)
        check(next.revision.value == before.revision.value + 1L) {
            "Gameplay revision must advance exactly once"
        }
        if (item is GameplayWorkItem.Direct<*>) item.reply.checkAvailable()
        val renderSnapshot = if (
            next.engine === before.engine ||
            renderModelNeutralTransition
        ) {
            check(committedRenderSnapshot.instanceId == before.instanceId)
            check(committedRenderSnapshot.revision == before.revision)
            GameplayNucleus.reuseRenderSnapshot(
                state = next,
                reusableState = before,
                reusableSnapshot = committedRenderSnapshot,
            )
        } else {
            GameplayNucleus.renderSnapshot(
                state = next,
                reusableState = before,
                reusableSnapshot = committedRenderSnapshot,
            )
        }
        check(renderSnapshot.instanceId == next.instanceId)
        check(renderSnapshot.revision == next.revision)
        check((renderSnapshot.renderModel == null) == (next.engine == null))
        renderSnapshot.renderModel?.let { render ->
            check(render.content === next.content)
            val expectedRenderPhase = when (next.phase) {
                GameplayRunPhase.CREATED -> error("Created GameplayRun cannot expose a render model")
                GameplayRunPhase.RUNNING -> GamePhase.RUNNING
                GameplayRunPhase.PAUSED -> GamePhase.PAUSED
                GameplayRunPhase.CHOICE -> GamePhase.CHOICE
                GameplayRunPhase.GAME_OVER -> GamePhase.GAME_OVER
                GameplayRunPhase.VICTORY -> GamePhase.VICTORY
                GameplayRunPhase.EXITED -> GamePhase.PAUSED
            }
            check(render.phase == expectedRenderPhase)
        }
        check(frame.outputs.size <= MAX_GAMEPLAY_OUTPUTS_PER_DECISION)
        var profileOutput: GameplayOutput.SendProfileCommand? = null
        var profileOutputCount = 0
        var directResultCount = 0
        var outputIndex = 0
        var previousDispatchOrder = 0
        while (outputIndex < frame.outputs.size) {
            val output = frame.outputs[outputIndex]
            val dispatchOrder = output.dispatchOrder
            if (outputIndex > 0) {
                check(previousDispatchOrder <= dispatchOrder) {
                    "Gameplay outputs are not in FX -> Profile -> Audio -> result order"
                }
            }
            previousDispatchOrder = dispatchOrder
            if (output is GameplayOutput.SendProfileCommand) {
                profileOutputCount++
                if (profileOutput == null) {
                    profileOutput = output
                }
            }
            if (output is GameplayOutput.SettingsApplied || output is GameplayOutput.RunStarted ||
                output is GameplayOutput.OverlayPaused || output is GameplayOutput.RunExited
            ) directResultCount++
            outputIndex++
        }

        val expectsExitResult = item.exitReplyOrNull() != null && !next.progressPending
        val expectsImmediateResult = item is GameplayWorkItem.Direct<*> && item !is GameplayWorkItem.Exiting
        check(directResultCount == if (expectsImmediateResult || expectsExitResult) 1 else 0) {
            "Gameplay command must accept its one typed result"
        }

        requireGameplayProfileOutputFanoutBound(profileOutputCount)
        if (profileOutput != null) {
            check(!before.progressPending && next.progressPending)
            requireGameplayCompletionCapacity(acceptance.remainingCapacity, requiredCompletions = 1)
        } else if (!before.progressPending) {
            check(!next.progressPending)
        } else if (item is GameplayWorkItem.ProgressCompletion) {
            check(!next.progressPending)
        } else {
            check(next.progressPending)
        }

        outputIndex = 0
        while (outputIndex < frame.outputs.size) {
            val output = frame.outputs[outputIndex]
            when (output) {
                is GameplayOutput.RunExited -> {
                    checkNotNull(item.exitReplyOrNull()).checkAvailable()
                    check(output.result.runId == next.instanceId.runId && output.result.revision == next.revision)
                    check(next.phase == GameplayRunPhase.EXITED && !next.progressPending)
                    check(outputIndex == frame.outputs.lastIndex)
                }
                is GameplayOutput.SettingsApplied -> {
                    check(item is GameplayWorkItem.Settings)
                    check(output.result.runId == next.instanceId.runId)
                    check(output.result.revision == next.revision)
                    check(outputIndex == frame.outputs.lastIndex)
                }
                is GameplayOutput.RunStarted -> {
                    check(item is GameplayWorkItem.Starting)
                    check(output.result.runId == next.instanceId.runId && output.result.revision == next.revision)
                    check(next.phase == GameplayRunPhase.RUNNING)
                    check(outputIndex == frame.outputs.lastIndex)
                }
                is GameplayOutput.OverlayPaused -> {
                    check(item is GameplayWorkItem.Pausing)
                    check(output.result.runId == next.instanceId.runId && output.result.revision == next.revision)
                    check(next.phase == GameplayRunPhase.PAUSED)
                    check(outputIndex == frame.outputs.lastIndex)
                }
                is GameplayOutput.SendProfileCommand -> Unit
                is GameplayOutput.AdvanceAudio,
                is GameplayOutput.EmitVisualFx,
                GameplayOutput.EnsureAudioUnlocked,
                -> Unit
            }
            outputIndex++
        }
        return renderSnapshot
    }

    private fun publish(state: GameplayState, renderSnapshot: GameplayRenderSnapshot) {
        committedFrame = CommittedGameplayFrame(state, renderSnapshot)
    }

    private fun initializeInteractionFxIfStarted(
        before: GameplayState,
        item: GameplayWorkItem,
    ) {
        if (before.phase != GameplayRunPhase.CREATED) return
        if (item.pulse !== GameplayNucleusPulse.StartRun) return
        check(interactionFxReducer == null)
        interactionFxReducer = InteractionFxReducer(
            (checkNotNull(item.context.start) as GameplayStartContext.Ready).inputs.seed,
        )
    }

    private fun execute(output: GameplayOutput, item: GameplayWorkItem) {
        when (output) {
            is GameplayOutput.EmitVisualFx ->
                checkNotNull(interactionFxReducer).apply(output.cues)
            is GameplayOutput.SendProfileCommand -> executeProfileCommand(output, item)
            is GameplayOutput.AdvanceAudio ->
                audioExecutor.advance(output.realDeltaSeconds, output.cues)
            GameplayOutput.EnsureAudioUnlocked ->
                audioExecutor.ensureUnlocked()
            is GameplayOutput.RunExited -> checkNotNull(item.exitReplyOrNull()).accepted(output.result)
            is GameplayOutput.SettingsApplied -> when (item) {
                is GameplayWorkItem.Settings -> item.reply.accepted(output.result)
                else -> error("Gameplay settings result has no current typed call")
            }
            is GameplayOutput.RunStarted -> when (item) {
                is GameplayWorkItem.Starting -> item.reply.accepted(output.result)
                else -> error("Gameplay start result has no current typed call")
            }
            is GameplayOutput.OverlayPaused -> when (item) {
                is GameplayWorkItem.Pausing -> item.reply.accepted(output.result)
                else -> error("Gameplay pause result has no current typed call")
            }
        }
    }

    private fun executeProfileCommand(output: GameplayOutput.SendProfileCommand, item: GameplayWorkItem) {
        val exitReply = item.exitReplyOrNull()
        acceptance.call(
            invoke = { reply -> profileProgress.applyGameplayProgress(output.update, reply) },
            acceptedInput = { GameplayWorkItem.ProgressCompletion(GameplayNucleusPulse.ProgressApplied(it), exitReply) },
            refusedInput = { GameplayWorkItem.ProgressCompletion(GameplayNucleusPulse.ProgressRefused(it), exitReply) },
        )
    }

    companion object {
        fun create(
            runId: RunId,
            content: GameplayContentSnapshot,
            profilePort: ProfileReadPort,
            profileProgress: ProfileProgress,
            audioExecutor: GameplayAudioExecutor,
            seed: Int,
        ): GameComponent = GameComponent(
            initialState = GameplayState.initial(runId, content),
            profilePort = profilePort,
            audioExecutor = audioExecutor,
            profileProgress = profileProgress,
            seed = seed,
        )
    }
}

private data class CommittedGameplayFrame(
    val state: GameplayState,
    val renderSnapshot: GameplayRenderSnapshot,
)

internal fun <T> gameplayCompletionDeque(): BoundedCompletionDeque<T> =
    BoundedCompletionDeque(GAMEPLAY_COMPLETION_CAPACITY)

internal fun requireGameplayProfileOutputFanoutBound(profileCommandCount: Int) {
    check(profileCommandCount in 0..1) {
        "A Gameplay decision may issue at most one Profile command"
    }
}

internal fun requireGameplayCompletionCapacity(remainingCapacity: Int, requiredCompletions: Int) {
    check(requiredCompletions >= 0 && remainingCapacity >= requiredCompletions) {
        "Gameplay completion capacity exhausted before acceptance"
    }
}

internal fun hasGameplayRevisionCapacity(
    revision: GameplayRevision,
    requiredRevisions: Long,
): Boolean {
    require(requiredRevisions > 0L)
    return revision.value <= Long.MAX_VALUE - requiredRevisions
}

private sealed interface GameplayWorkItem {
    val pulse: GameplayNucleusPulse
    val context: GameplayContext

    sealed interface Direct<Result : Any> : GameplayWorkItem {
        val reply: InlineReply<Result, GameplayRefusal>
    }

    data object Local : GameplayWorkItem {
        override val pulse: GameplayNucleusPulse = GameplayNucleusPulse.Intent(GameplayInteractionPulse.UserGestureObserved)
        override val context: GameplayContext = GameplayContext.Empty
    }

    data class ProgressCompletion(
        override val pulse: GameplayNucleusPulse,
        val exitReply: InlineReply<GameplayRunExited, GameplayRefusal>?,
    ) : GameplayWorkItem {
        override val context: GameplayContext = GameplayContext.Empty
    }

    data class Exiting(
        override val reply: InlineReply<GameplayRunExited, GameplayRefusal>,
    ) : Direct<GameplayRunExited> {
        override val pulse: GameplayNucleusPulse = GameplayNucleusPulse.ExitRun
        override val context: GameplayContext = GameplayContext.Empty
    }

    data class Settings(
        override val pulse: GameplayNucleusPulse.ApplyPreferences,
        override val reply: InlineReply<GameplaySettingsApplied, GameplayRefusal>,
    ) : Direct<GameplaySettingsApplied> {
        override val context: GameplayContext = GameplayContext.Empty
    }

    data class Starting(
        override val context: GameplayContext,
        override val reply: InlineReply<GameplayRunStarted, GameplayRefusal>,
    ) : Direct<GameplayRunStarted> {
        override val pulse: GameplayNucleusPulse = GameplayNucleusPulse.StartRun
    }

    data class Pausing(
        override val reply: InlineReply<GameplayOverlayPaused, GameplayRefusal>,
    ) : Direct<GameplayOverlayPaused> {
        override val pulse: GameplayNucleusPulse = GameplayNucleusPulse.PauseForOverlay
        override val context: GameplayContext = GameplayContext.Empty
    }

}

private fun GameplayWorkItem.exitReplyOrNull(): InlineReply<GameplayRunExited, GameplayRefusal>? = when (this) {
    is GameplayWorkItem.Exiting -> reply
    is GameplayWorkItem.ProgressCompletion -> exitReply
    else -> null
}

private val GameplayOutput.dispatchOrder: Int
    get() = when (this) {
        is GameplayOutput.EmitVisualFx -> 0
        is GameplayOutput.SendProfileCommand -> 1
        is GameplayOutput.AdvanceAudio,
        GameplayOutput.EnsureAudioUnlocked,
        -> 2
        is GameplayOutput.RunExited, is GameplayOutput.SettingsApplied,
        is GameplayOutput.RunStarted, is GameplayOutput.OverlayPaused,
        -> 3
    }

private const val GAMEPLAY_COMPLETION_CAPACITY: Int = 8
private const val MAX_LOCAL_REVISIONS_PER_DISPATCH: Long = 2L
private const val MAX_EXIT_REVISIONS_PER_DISPATCH: Long = 2L
