// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfileRefusal

import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayConfigurationRejection
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPointerAxis
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.copyForReduction
import kinetickk.ball.gameplay.nucleus.simulation.emitSound
import kinetickk.ball.gameplay.nucleus.simulation.emitVisualFx
import kinetickk.ball.gameplay.nucleus.simulation.takeSoundCues
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameplayNucleusTest {
    @Test
    fun createdRunCapturesContentAndExposesOnlyStampedNarrowQueries() {
        val state = initial(14)

        assertEquals(GameplayRevision.ZERO, state.revision)
        assertEquals(GameplayRunPhase.CREATED, state.phase)
        assertSame(canonicalGameplayContent, state.content)
        assertNull(state.engine)
        assertNull(GameplayNucleus.renderSnapshot(state).renderModel)
        val status = GameplayNucleus.query(state, GameplayQuery.GetRunStatus)
        val weapon = GameplayNucleus.query(state, GameplayQuery.GetActiveWeapon)
        val codex = GameplayNucleus.query(state, GameplayQuery.GetBuildSummary)
        assertEquals(GameplayRunPhase.CREATED, status.phase)
        assertFalse(status.progressPending)
        assertNull(weapon.weapon)
        assertTrue(codex.itemStacks.isEmpty())
        listOf(status, weapon, codex).forEach { projection ->
            assertEquals(state.instanceId, projection.instanceId)
            assertEquals(GameplayRevision.ZERO, projection.revision)
        }
    }

    @Test
    fun acceptedStartUsesTrustedContextAndCompletesWithCanonicalSource() {
        val initial = initial(2)
        val inputs = validStartInputs(seed = 91_337)
        val frame = accepted(
            GameplayNucleus.decide(
                initial,
                GameplayNucleusPulse.StartRun,
                GameplayContext(start = GameplayStartContext.Ready(inputs)),
            ),
        )

        assertEquals(GameplayRevision(1), frame.nextState.revision)
        assertEquals(GameplayRunPhase.RUNNING, frame.nextState.phase)
        assertSame(inputs.content, frame.nextState.content)
        assertSame(inputs.content, frame.nextState.engine!!.model.content)
        val renderModel = checkNotNull(GameplayNucleus.renderSnapshot(frame.nextState).renderModel)
        assertSame(inputs.content, renderModel.content)
        assertEquals(KINETICKK_CONTENT_VERSION, renderModel.content.version)
        val completion = assertIs<GameplayOutput.RunStarted>(frame.outputs.single())
        assertEquals(initial.instanceId.runId, completion.result.runId)
        assertEquals(frame.nextState.revision, completion.result.revision)
        assertNull(initial.engine)
        assertEquals(GameplayRevision.ZERO, initial.revision)
    }

    @Test
    fun startRequiresAlreadyValidatedTrustedInputs() {
        val state = initial(3)

        assertFailsWith<IllegalStateException> {
            GameplayNucleus.decide(
                state,
                GameplayNucleusPulse.StartRun,
                GameplayContext.Empty,
            )
        }
        assertEquals(GameplayRevision.ZERO, state.revision)
        assertNull(state.engine)
    }

    @Test
    fun sameSeedAndIntentTraceProducesTheSameImmutableRenderFacts() {
        var first = start(initial(1), seed = 91_337).nextState
        var second = start(initial(2), seed = 91_337).nextState
        val trace = listOf(
            GameplayInteractionPulse.ViewportChanged.fromValidated(1_280f, 720f, 1.5f),
            GameplayInteractionPulse.PointerMoved.fromValidated(1_100f, 240f),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f),
            GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, true),
            GameplayInteractionPulse.DashRequested,
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f),
            GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, false),
        )

        trace.forEach { intent ->
            first = interaction(first, intent).nextState
            second = interaction(second, intent).nextState
        }

        assertEquals(renderFacts(first), renderFacts(second))
    }

    @Test
    fun configurationValidatorCoversEveryClosedBoundaryReason() {
        val valid = validStartInputs()
        val configurations = listOf(
            valid.copy(
                profile = valid.profile.copy(
                    preferences = PlayerPreferences(masterVolume = Float.NaN),
                ),
            ) to GameplayConfigurationRejection.INVALID_PREFERENCES,
            valid.copy(
                content = valid.content.copy(
                    weapons = valid.content.weapons.filter { it.id != WeaponId.FLUX_WAKE }
                        .toImmutableList(),
                ),
            ) to GameplayConfigurationRejection.STARTING_WEAPON_MISSING,
            valid.copy(
                content = valid.content.copy(
                    weapons = valid.content.weapons.filter { it.id != WeaponId.MORNINGSTAR }
                        .toImmutableList(),
                ),
                profile = valid.profile.copy(
                    loadout = PlayerLoadout(
                        selectedWeapon = WeaponId.FLUX_WAKE,
                        unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
                    ),
                ),
            ) to GameplayConfigurationRejection.STARTING_WEAPON_MISSING,
            valid.copy(
                profile = valid.profile.copy(
                    loadout = PlayerLoadout(
                        selectedWeapon = WeaponId.MORNINGSTAR,
                        unlockedWeapons = setOf(WeaponId.FLUX_WAKE),
                    ),
                ),
            ) to GameplayConfigurationRejection.STARTING_WEAPON_LOCKED,
            valid.copy(
                profile = valid.profile.copy(labProgress = LabProgress(emptyList())),
            ) to GameplayConfigurationRejection.META_RANK_COUNT_MISMATCH,
            valid.copy(
                profile = valid.profile.copy(
                    labProgress = LabProgress(
                        valid.profile.labProgress.ranks.mapIndexed { index, rank ->
                            if (index == 0) valid.content.metaUpgrades[0].maxRanks + 1 else rank
                        },
                    ),
                ),
            ) to GameplayConfigurationRejection.META_RANK_OUT_OF_RANGE,
            valid.copy(
                profile = valid.profile.copy(collection = PlayerCollection(setOf(999))),
            ) to GameplayConfigurationRejection.UNKNOWN_DISCOVERED_ITEM,
            valid.copy(
                profile = valid.profile.copy(
                    rebirthProgress = RebirthProgress(valid.content.rebirth.maximumLevel + 1),
                ),
            ) to GameplayConfigurationRejection.REBIRTH_LEVEL_OUT_OF_RANGE,
            valid.copy(
                profile = valid.profile.copy(economy = PlayerEconomy(-1, -1)),
            ) to GameplayConfigurationRejection.NEGATIVE_MATTER,
            valid.copy(
                profile = valid.profile.copy(economy = PlayerEconomy(2, 1)),
            ) to GameplayConfigurationRejection.LIFETIME_MATTER_BELOW_CURRENT,
        )

        configurations.forEach { (inputs, expected) ->
            assertEquals(expected, GameplayNucleus.validateStartInputs(inputs))
        }
        assertNull(GameplayNucleus.validateStartInputs(valid))
    }

    @Test
    fun pointerViewportMembershipAcceptsInclusiveEdgesAndRejectsAllFourAdjacentCoordinates() {
        val running = start(initial(10)).nextState
        val width = 1_280f
        val height = 720f
        val withViewport = interaction(
            running,
            GameplayInteractionPulse.ViewportChanged.fromValidated(width, height, 1f),
        ).nextState

        listOf(
            GameplayInteractionPulse.PointerMoved.fromValidated(0f, 20f),
            GameplayInteractionPulse.PointerMoved.fromValidated(width, 20f),
            GameplayInteractionPulse.PointerMoved.fromValidated(20f, 0f),
            GameplayInteractionPulse.PointerMoved.fromValidated(20f, height),
        ).forEach { pointer -> interaction(withViewport, pointer) }

        val firstBelowZero = -Float.MIN_VALUE
        val firstAboveWidth = Float.fromBits(width.toBits() + 1)
        val firstAboveHeight = Float.fromBits(height.toBits() + 1)
        listOf(
            GameplayInteractionPulse.PointerMoved.fromValidated(firstBelowZero, 20f) to
                GameplayPointerAxis.HORIZONTAL,
            GameplayInteractionPulse.PointerMoved.fromValidated(firstAboveWidth, 20f) to
                GameplayPointerAxis.HORIZONTAL,
            GameplayInteractionPulse.PointerMoved.fromValidated(20f, firstBelowZero) to
                GameplayPointerAxis.VERTICAL,
            GameplayInteractionPulse.PointerMoved.fromValidated(20f, firstAboveHeight) to
                GameplayPointerAxis.VERTICAL,
        ).forEach { (pointer, axis) ->
            assertIntentRejection(
                withViewport,
                pointer,
                GameplayRejection.PointerOutsideViewport(axis),
            )
        }
    }

    @Test
    fun lifecycleAndModuleOperationMatrixIsClosed() {
        val created = initial(12)
        assertIntentRejection(created, GameplayInteractionPulse.DashRequested, GameplayRejection.NotStarted)
        assertRejection(GameplayNucleus.decide(created, GameplayNucleusPulse.PauseForOverlay), GameplayRejection.NotStarted)
        assertRejection(
            GameplayNucleus.decide(created, GameplayNucleusPulse.ApplyPreferences(PlayerPreferences())),
            GameplayRejection.NotStarted,
        )
        assertRejection(GameplayNucleus.decide(created, GameplayNucleusPulse.ExitRun), GameplayRejection.NotStarted)

        val running = start(created).nextState
        val paused = accepted(GameplayNucleus.decide(running, GameplayNucleusPulse.PauseForOverlay)).nextState
        assertEquals(GameplayRunPhase.PAUSED, paused.phase)
        assertRejection(GameplayNucleus.decide(paused, GameplayNucleusPulse.PauseForOverlay), GameplayRejection.PauseUnavailable)
        val resumed = interaction(paused, GameplayInteractionPulse.PauseToggled).nextState
        assertEquals(GameplayRunPhase.RUNNING, resumed.phase)

        val applied = assertIs<GameplayDecision.Accepted>(GameplayNucleus.decide(
            resumed,
            GameplayNucleusPulse.ApplyPreferences(PlayerPreferences(masterVolume = 0.4f)),
        )).frame
        assertEquals(0.4f, applied.nextState.engine!!.model.settings.masterVolume)
        assertRejection(GameplayNucleus.decide(running, GameplayNucleusPulse.StartRun,
            GameplayContext(start = GameplayStartContext.Ready(validStartInputs()))), GameplayRejection.AlreadyStarted)

        val exited = exit(running).nextState
        assertEquals(GameplayRunPhase.EXITED, exited.phase)
        assertIntentRejection(exited, GameplayInteractionPulse.DashRequested, GameplayRejection.RunExited)
        assertRejection(GameplayNucleus.decide(exited, GameplayNucleusPulse.ExitRun), GameplayRejection.RunExited)
        assertRejection(GameplayNucleus.decide(exited, GameplayNucleusPulse.StartRun,
            GameplayContext(start = GameplayStartContext.Ready(validStartInputs()))), GameplayRejection.RunExited)
    }

    @Test
    fun exitWithoutProgressCompletesImmediately() {
        val state = start(initial(20)).nextState
        val frame = exit(state)

        assertEquals(GameplayRunPhase.EXITED, frame.nextState.phase)
        assertFalse(frame.nextState.progressPending)
        val completion = assertIs<GameplayOutput.RunExited>(frame.outputs.single())
        assertEquals(
            GameplayExitProgressResult.NoProgress,
            completion.result.progress,
        )
    }

    @Test
    fun exitWithProgressDefersCompletionAndBuildsExactProfileRequest() {
        val state = startedWithProgress(runId = 21, bankedMatter = 9)
        val frame = exit(state)
        assertEquals(GameplayRunPhase.EXITED, frame.nextState.phase)
        val sent = assertIs<GameplayOutput.SendProfileCommand>(frame.outputs.single())
        assertEquals(9L, sent.update.bankedMatter)
        assertTrue(frame.nextState.progressPending)
        assertTrue(frame.outputs.none { it is GameplayOutput.RunExited })
    }

    @Test
    fun acceptedAndPreacceptProfileCarriersCompleteTheReservedExit() {
        val exiting = exit(startedWithProgress(22, 7)).nextState
        val acceptedFrame = accepted(GameplayNucleus.decide(
            exiting,
            GameplayNucleusPulse.ProgressApplied(ProfileProgressApplied(ProfileRevision(18))),
        ))
        assertFalse(acceptedFrame.nextState.progressPending)
        assertEquals(GameplayExitProgressResult.Applied,
            assertIs<GameplayOutput.RunExited>(acceptedFrame.outputs.single()).result.progress)
        val refusedFrame = accepted(GameplayNucleus.decide(
            exiting,
            GameplayNucleusPulse.ProgressRefused(ProfileRefusal.Busy),
        ))
        assertFalse(refusedFrame.nextState.progressPending)
        assertEquals(GameplayExitProgressResult.NotApplied,
            assertIs<GameplayOutput.RunExited>(refusedFrame.outputs.single()).result.progress)
    }

    @Test
    fun atMostOneProfileCommandCanBePending() {
        val state = start(initial(24)).nextState
        val firstCandidate = state.engine!!.model.copyForReduction().apply { pendingBankedMatter = 3 }
        val first = interaction(
            state.copy(engine = EngineState(firstCandidate)),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0f),
        ).nextState
        assertTrue(first.progressPending)

        val stateOnlyFrame = interaction(
            first,
            GameplayInteractionPulse.PointerMoved.fromValidated(900f, 360f),
        )
        assertTrue(stateOnlyFrame.outputs.isEmpty())
        assertTrue(stateOnlyFrame.nextState.progressPending)

        val secondCandidate = first.engine!!.model.copyForReduction().apply { pendingBankedMatter = 4 }
        assertIntentRejection(
            first.copy(engine = EngineState(secondCandidate)),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0f),
            GameplayRejection.ProgressPending,
        )
    }

    @Test
    fun outputsKeepFxProfileAudioOrderAndBound() {
        val state = start(initial(25)).nextState
        val candidate = state.engine!!.model.copyForReduction().apply {
            pendingBankedMatter = 9
            emitVisualFx(VisualFxCue.ShockwaveAdded(1f, 2f, 0.3f, 40f, 2))
            emitSound(GameplayAudioCue.DASH)
        }
        val frame = interaction(
            state.copy(engine = EngineState(candidate)),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0f),
        )

        assertEquals(MAX_GAMEPLAY_OUTPUTS_PER_DECISION, frame.outputs.size)
        assertIs<GameplayOutput.EmitVisualFx>(frame.outputs[0])
        assertIs<GameplayOutput.SendProfileCommand>(frame.outputs[1])
        assertIs<GameplayOutput.AdvanceAudio>(frame.outputs[2])
    }

    @Test
    fun audioCueAccumulatorRetainsFirstThirtyTwoAndDropsThirtyThird() {
        val state = MutableGameState(canonicalGameplayContent, seed = 1)
        repeat(MutableGameState.MAX_GAMEPLAY_SOUND_CUES + 1) { index ->
            state.emitSound(
                if (index == MutableGameState.MAX_GAMEPLAY_SOUND_CUES) {
                    GameplayAudioCue.VICTORY
                } else {
                    GameplayAudioCue.DASH
                },
            )
        }

        val cues = state.takeSoundCues()
        assertEquals(MutableGameState.MAX_GAMEPLAY_SOUND_CUES, cues.size)
        assertTrue(cues.all { it == GameplayAudioCue.DASH })
    }

    @Test
    fun acceptedFrameEnforcesOutputBoundOrderAndFinalCompletion() {
        val state = initial(26)
        GameplayAcceptedFrame(
            state,
            immutableListOf(
                GameplayOutput.EnsureAudioUnlocked,
                GameplayOutput.EnsureAudioUnlocked,
                GameplayOutput.EnsureAudioUnlocked,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            GameplayAcceptedFrame(
                state,
                immutableListOf(
                    GameplayOutput.EnsureAudioUnlocked,
                    GameplayOutput.EnsureAudioUnlocked,
                    GameplayOutput.EnsureAudioUnlocked,
                    GameplayOutput.EnsureAudioUnlocked,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayAcceptedFrame(
                state,
                immutableListOf(
                    GameplayOutput.AdvanceAudio(0f, immutableListOf()),
                    GameplayOutput.EmitVisualFx(immutableListOf()),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayAcceptedFrame(
                state,
                immutableListOf(
                    GameplayOutput.RunStarted(GameplayRunStarted(state.instanceId.runId, state.revision)),
                    GameplayOutput.EnsureAudioUnlocked,
                ),
            )
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun retainedRenderAndQueryCollectionsStayImmutable() {
        val state = start(initial(27)).nextState
        val retainedRender = GameplayNucleus.renderSnapshot(state).renderModel!!
        val retainedStacks = GameplayNucleus.query(state, GameplayQuery.GetBuildSummary).itemStacks
        val retainedCoreX = retainedRender.coreX
        val advanced = interaction(
            state,
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f),
        ).nextState

        assertIs<ImmutableList<*>>(retainedRender.enemies)
        assertFalse((retainedRender.enemies as Any) is MutableList<*>)
        assertFailsWith<ClassCastException> { (retainedStacks as Any) as MutableList<Int> }
        assertEquals(retainedCoreX, retainedRender.coreX)
        assertNotEquals(state.revision, advanced.revision)
        assertSame(state.content, advanced.content)
        assertSame(state.content, GameplayNucleus.renderSnapshot(advanced).renderModel!!.content)
    }

    @Test
    fun reusableRenderSnapshotMustMatchItsExactCommittedSource() {
        val firstSource = start(initial(271)).nextState
        val firstSnapshot = GameplayNucleus.renderSnapshot(firstSource)
        val otherSource = start(initial(272)).nextState
        val otherNext = interaction(
            otherSource,
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.01f),
        ).nextState

        assertFailsWith<IllegalArgumentException> {
            GameplayNucleus.renderSnapshot(
                state = otherNext,
                reusableState = otherSource,
                reusableSnapshot = firstSnapshot,
            )
        }

        val pausedBranch = interaction(
            firstSource,
            GameplayInteractionPulse.PauseToggled,
        ).nextState
        val runningBranch = interaction(
            firstSource,
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.01f),
        ).nextState
        val runningNext = interaction(
            runningBranch,
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.01f),
        ).nextState
        assertEquals(pausedBranch.instanceId, runningBranch.instanceId)
        assertEquals(pausedBranch.revision, runningBranch.revision)
        assertFailsWith<IllegalArgumentException> {
            GameplayNucleus.renderSnapshot(
                state = runningNext,
                reusableState = runningBranch,
                reusableSnapshot = GameplayNucleus.renderSnapshot(pausedBranch),
            )
        }

        val firstNext = interaction(
            firstSource,
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.01f),
        ).nextState
        val firstFollowing = interaction(
            firstNext,
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.01f),
        ).nextState
        assertFailsWith<IllegalArgumentException> {
            GameplayNucleus.renderSnapshot(
                state = firstFollowing,
                reusableState = firstNext,
                reusableSnapshot = firstSnapshot,
            )
        }
    }

    private fun initial(runId: Long): GameplayState =
        GameplayState.initial(RunId(runId), canonicalGameplayContent)

    private fun start(state: GameplayState, seed: Int = 731_991): GameplayAcceptedFrame {
        return accepted(
            GameplayNucleus.decide(
                state,
                GameplayNucleusPulse.StartRun,
                GameplayContext(start = GameplayStartContext.Ready(validStartInputs(seed))),
            ),
        )
    }

    private fun exit(state: GameplayState): GameplayAcceptedFrame =
        accepted(GameplayNucleus.decide(state, GameplayNucleusPulse.ExitRun))

    private fun interaction(
        state: GameplayState,
        intent: GameplayInteractionPulse,
    ): GameplayAcceptedFrame = accepted(
        GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(intent)),
    )

    private fun assertIntentRejection(
        state: GameplayState,
        intent: GameplayInteractionPulse,
        expected: GameplayRejection,
    ) = assertRejection(
        GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(intent)),
        expected,
    )

    private fun startedWithProgress(runId: Long, bankedMatter: Long): GameplayState {
        val state = start(initial(runId)).nextState
        val candidate = state.engine!!.model.copyForReduction().apply { runMatter = bankedMatter }
        return state.copy(engine = EngineState(candidate))
    }
}

private fun validStartInputs(seed: Int = 731_991): GameplayStartInputs = GameplayStartInputs(
    content = canonicalGameplayContent,
    profile = PlayerProfile(
        economy = PlayerEconomy(matter = 42, lifetimeMatter = 84),
    ).toGameplaySnapshot(),
    seed = seed,
)

private fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences,
    economy,
    loadout,
    labProgress,
    collection,
    rebirthProgress,
)

private fun accepted(decision: GameplayDecision): GameplayAcceptedFrame =
    assertIs<GameplayDecision.Accepted>(decision).frame

private fun assertRejection(decision: GameplayDecision, expected: GameplayRejection) {
    assertEquals(expected, assertIs<GameplayDecision.Rejected>(decision).reason)
}

private data class RenderFacts(
    val phase: GamePhase,
    val coreX: Float,
    val coreY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val elapsed: Float,
    val enemies: List<Any>,
    val projectiles: List<Any>,
    val trail: List<Any>,
)

private fun renderFacts(state: GameplayState): RenderFacts =
    GameplayNucleus.renderSnapshot(state).renderModel!!.let { model ->
        RenderFacts(
            model.phase,
            model.coreX,
            model.coreY,
            model.velocityX,
            model.velocityY,
            model.elapsed,
            model.enemies.toList(),
            model.projectiles.toList(),
            model.trail.toList(),
        )
    }
