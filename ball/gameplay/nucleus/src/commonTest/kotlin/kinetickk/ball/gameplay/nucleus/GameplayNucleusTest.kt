// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayCommandIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayConfigurationRejection
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
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
import kinetickk.ball.gameplay.api.GameplaySemanticHandle
import kinetickk.ball.gameplay.api.RunId
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
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileResultSourceToken
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSemanticHandle
import kinetickk.ball.profile.api.ProfileTargetBoundaryProvenance
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
        val codex = GameplayNucleus.query(state, GameplayQuery.GetCodexStacks)
        assertEquals(GameplayRunPhase.CREATED, status.phase)
        assertFalse(status.profileCommandPending)
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
        val pulse = modulePulse(initial, GameplayModuleCommand.StartRun, sourceRevision = 8, ordinal = 3)
        val inputs = validStartInputs(seed = 91_337)
        val frame = accepted(
            GameplayNucleus.decide(
                initial,
                GameplayNucleusPulse.ModuleCommand(pulse),
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
        val completion = assertIs<GameplayOutput.CompleteCommand>(frame.outputs.single())
        assertEquals(pulse.commandSource, completion.result.commandSource)
        assertEquals(pulse.commandSource.semanticHandle, completion.result.semanticHandle)
        assertEquals(0, completion.result.sourceOrdinal)
        assertEquals(GameplayModuleResult.RunStarted, completion.result.result)
        assertNull(initial.engine)
        assertEquals(GameplayRevision.ZERO, initial.revision)
    }

    @Test
    fun startRequiresAlreadyValidatedTrustedInputs() {
        val state = initial(3)
        val pulse = modulePulse(state, GameplayModuleCommand.StartRun)

        assertFailsWith<IllegalStateException> {
            GameplayNucleus.decide(
                state,
                GameplayNucleusPulse.ModuleCommand(pulse),
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
        assertModuleRejection(created, GameplayModuleCommand.PauseForOverlay, GameplayRejection.NotStarted)
        assertModuleRejection(created, GameplayModuleCommand.ApplyPreferences, GameplayRejection.NotStarted)
        assertModuleRejection(created, GameplayModuleCommand.ExitRun, GameplayRejection.NotStarted)

        val running = start(created).nextState
        val paused = module(running, GameplayModuleCommand.PauseForOverlay).nextState
        assertEquals(GameplayRunPhase.PAUSED, paused.phase)
        assertModuleRejection(paused, GameplayModuleCommand.PauseForOverlay, GameplayRejection.PauseUnavailable)
        val resumed = interaction(paused, GameplayInteractionPulse.PauseToggled).nextState
        assertEquals(GameplayRunPhase.RUNNING, resumed.phase)

        val applied = module(
            resumed,
            GameplayModuleCommand.ApplyPreferences,
            GameplayContext(preferences = PlayerPreferences(masterVolume = 0.4f)),
        )
        assertEquals(0.4f, applied.nextState.engine!!.model.settings.masterVolume)
        assertModuleRejection(running, GameplayModuleCommand.StartRun, GameplayRejection.AlreadyStarted)

        val exited = module(running, GameplayModuleCommand.ExitRun).nextState
        assertEquals(GameplayRunPhase.EXITED, exited.phase)
        assertIntentRejection(exited, GameplayInteractionPulse.DashRequested, GameplayRejection.RunExited)
        assertModuleRejection(exited, GameplayModuleCommand.ExitRun, GameplayRejection.RunExited)
        assertModuleRejection(exited, GameplayModuleCommand.StartRun, GameplayRejection.RunExited)
    }

    @Test
    fun exitWithoutProgressCompletesImmediately() {
        val state = start(initial(20)).nextState
        val frame = module(state, GameplayModuleCommand.ExitRun)

        assertEquals(GameplayRunPhase.EXITED, frame.nextState.phase)
        assertNull(frame.nextState.pendingProfileCommand)
        val completion = assertIs<GameplayOutput.CompleteCommand>(frame.outputs.single())
        assertEquals(
            GameplayModuleResult.RunExited(GameplayExitProgressResult.NoProgress),
            completion.result.result,
        )
    }

    @Test
    fun exitWithProgressDefersCompletionAndBuildsExactProfileRequest() {
        val state = startedWithProgress(runId = 21, bankedMatter = 9)
        val pulse = modulePulse(
            state,
            GameplayModuleCommand.ExitRun,
            sourceRevision = 44,
            ordinal = 6,
        )
        val frame = accepted(
            GameplayNucleus.decide(
                state,
                GameplayNucleusPulse.ModuleCommand(pulse),
                GameplayContext.Empty,
            ),
        )

        assertEquals(GameplayRunPhase.EXITED, frame.nextState.phase)
        val sent = assertIs<GameplayOutput.SendProfileCommand>(frame.outputs.single())
        assertEquals(ProfileCommandSource.GameplayRun(21), sent.request.semanticHandle.sourceInstance)
        assertEquals(LOCAL_PROFILE_INSTANCE_ID, sent.request.targetInstance)
        assertEquals(frame.nextState.revision.value, sent.request.semanticHandle.sourceRevision)
        assertEquals(0, sent.request.sourceOrdinal)
        val pending = checkNotNull(frame.nextState.pendingProfileCommand)
        assertEquals(sent.request, pending.request)
        assertEquals(pulse.commandSource, pending.exitCompletion)
        assertTrue(frame.outputs.none { it is GameplayOutput.CompleteCommand })
    }

    @Test
    fun acceptedAndPreacceptProfileCarriersCompleteTheReservedExit() {
        val exiting = module(startedWithProgress(22, 7), GameplayModuleCommand.ExitRun).nextState
        val pending = checkNotNull(exiting.pendingProfileCommand)
        val commandSource = profileCommandSource(pending.request.semanticHandle)
        val acceptedFrame = accepted(
            GameplayNucleus.decide(
                exiting,
                GameplayNucleusPulse.ProfileModuleResultPulse(
                    commandSource = commandSource,
                    resultSource = ProfileResultSourceToken(
                        semanticHandle = pending.request.semanticHandle,
                        targetInstance = LOCAL_PROFILE_INSTANCE_ID,
                        targetRevision = ProfileRevision(18),
                        sourceOrdinal = 1,
                        causalScope = 17,
                        causalDepth = 2,
                    ),
                    effectiveProtocolIdentity = ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
                    result = ProfileModuleResult.GameplayProgressApplied,
                    issuerProvenance = ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING,
                ),
            ),
        )
        assertNull(acceptedFrame.nextState.pendingProfileCommand)
        assertEquals(
            GameplayModuleResult.RunExited(GameplayExitProgressResult.Applied),
            assertIs<GameplayOutput.CompleteCommand>(acceptedFrame.outputs.single()).result.result,
        )

        val refusedFrame = accepted(
            GameplayNucleus.decide(
                exiting,
                GameplayNucleusPulse.ProfileCommandRejectedBeforeAcceptance(
                    commandSource = commandSource,
                    effectiveProtocolIdentity = ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
                    boundaryResponse = ProfileCommandBoundaryResponse.DecisionRejected(
                        kinetickk.ball.profile.api.ProfileRejection.NoChange,
                    ),
                    targetBoundaryProvenance = ProfileTargetBoundaryProvenance(
                        LOCAL_PROFILE_INSTANCE_ID,
                        ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
                    ),
                ),
            ),
        )
        assertEquals(
            GameplayModuleResult.RunExited(GameplayExitProgressResult.NotApplied),
            assertIs<GameplayOutput.CompleteCommand>(refusedFrame.outputs.single()).result.result,
        )
    }

    @Test
    fun atMostOneProfileCommandCanBePending() {
        val state = start(initial(24)).nextState
        val firstCandidate = state.engine!!.model.copyForReduction().apply { pendingBankedMatter = 3 }
        val first = interaction(
            state.copy(engine = EngineState(firstCandidate)),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0f),
        ).nextState
        assertTrue(first.pendingProfileCommand != null)

        val secondCandidate = first.engine!!.model.copyForReduction().apply { pendingBankedMatter = 4 }
        assertIntentRejection(
            first.copy(engine = EngineState(secondCandidate)),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0f),
            GameplayRejection.ProfileCommandPending,
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
        val commandSource = modulePulse(state, GameplayModuleCommand.StartRun).commandSource
        assertFailsWith<IllegalArgumentException> {
            GameplayAcceptedFrame(
                state,
                immutableListOf(
                    GameplayOutput.CompleteCommand(
                        GameplayModuleResultOutput(
                            commandSource.semanticHandle,
                            0,
                            commandSource,
                            GameplayModuleResult.RunStarted,
                        ),
                    ),
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
        val retainedStacks = GameplayNucleus.query(state, GameplayQuery.GetCodexStacks).itemStacks
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

    private fun initial(runId: Long): GameplayState =
        GameplayState.initial(RunId(runId), canonicalGameplayContent)

    private fun start(state: GameplayState, seed: Int = 731_991): GameplayAcceptedFrame {
        val pulse = modulePulse(state, GameplayModuleCommand.StartRun)
        return accepted(
            GameplayNucleus.decide(
                state,
                GameplayNucleusPulse.ModuleCommand(pulse),
                GameplayContext(start = GameplayStartContext.Ready(validStartInputs(seed))),
            ),
        )
    }

    private fun module(
        state: GameplayState,
        command: GameplayModuleCommand,
        context: GameplayContext = GameplayContext.Empty,
    ): GameplayAcceptedFrame = accepted(
        GameplayNucleus.decide(
            state,
            GameplayNucleusPulse.ModuleCommand(modulePulse(state, command)),
            context,
        ),
    )

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

    private fun assertModuleRejection(
        state: GameplayState,
        command: GameplayModuleCommand,
        expected: GameplayRejection,
    ) {
        val context = when (command) {
            GameplayModuleCommand.StartRun -> GameplayContext(
                start = GameplayStartContext.Ready(validStartInputs()),
            )
            GameplayModuleCommand.ApplyPreferences -> GameplayContext(preferences = PlayerPreferences())
            GameplayModuleCommand.PauseForOverlay,
            GameplayModuleCommand.ExitRun,
            -> GameplayContext.Empty
        }
        assertRejection(
            GameplayNucleus.decide(
                state,
                GameplayNucleusPulse.ModuleCommand(modulePulse(state, command)),
                context,
            ),
            expected,
        )
    }

    private fun modulePulse(
        state: GameplayState,
        command: GameplayModuleCommand,
        sourceRevision: Long = state.revision.value,
        ordinal: Int = 0,
    ): GameplayModuleCommandPulse {
        val handle = GameplaySemanticHandle(GameplayCommandSource.LocalSession, sourceRevision, ordinal)
        return GameplayModuleCommandPulse(
            commandSource = GameplayCommandSourceToken(
                semanticHandle = handle,
                targetInstance = state.instanceId,
                causalScope = 17,
                causalDepth = 0,
            ),
            effectiveProtocolIdentity = when (command) {
                GameplayModuleCommand.StartRun -> GameplayEffectiveProtocolIdentity.SESSION_START
                GameplayModuleCommand.PauseForOverlay -> GameplayEffectiveProtocolIdentity.SESSION_PAUSE
                GameplayModuleCommand.ApplyPreferences -> GameplayEffectiveProtocolIdentity.SESSION_PREFERENCES
                GameplayModuleCommand.ExitRun -> GameplayEffectiveProtocolIdentity.SESSION_EXIT
            },
            command = command,
            issuerProvenance = GameplayCommandIssuerProvenance.LOCAL_SESSION_STATIC_BINDING,
        )
    }

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

private fun profileCommandSource(handle: ProfileSemanticHandle): ProfileCommandSourceToken =
    ProfileCommandSourceToken(
        semanticHandle = handle,
        targetInstance = LOCAL_PROFILE_INSTANCE_ID,
        causalScope = 17,
        causalDepth = 1,
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
