// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GamePhase
import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandAdmission
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandRef
import kinetickk.ball.gameplay.api.GameplayCommandRefRejection
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayConfigurationRejection
import kinetickk.ball.gameplay.api.GameplayControlPulse
import kinetickk.ball.gameplay.api.GameplayExitProfileOutcome
import kinetickk.ball.gameplay.api.GameplayInputField
import kinetickk.ball.gameplay.api.GameplayInputReason
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayProfileResultRejection
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
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
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource as ProfileSource
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
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
    fun createdRunHasNoCapturedSimulationAndAllQueriesAreStamped() {
        val state = GameplayState.initial(RunId(14))

        assertEquals(GameplayRevision.ZERO, state.revision)
        assertEquals(GameplayRunPhase.CREATED, state.phase)
        assertNull(state.content)
        assertNull(state.engine)
        assertNull(GameplayNucleus.query(state, GameplayQuery.GetRender).renderModel)
        assertEquals(
            GameplayRunPhase.CREATED,
            GameplayNucleus.query(state, GameplayQuery.GetRunStatus).phase,
        )
        assertFalse(GameplayNucleus.query(state, GameplayQuery.GetRunStatus).profileCommandPending)
        assertNull(GameplayNucleus.query(state, GameplayQuery.GetActiveWeapon).weapon)
        assertTrue(GameplayNucleus.query(state, GameplayQuery.GetCodexStacks).itemStacks.isEmpty())
        listOf(
            GameplayNucleus.query(state, GameplayQuery.GetRender),
            GameplayNucleus.query(state, GameplayQuery.GetRunStatus),
            GameplayNucleus.query(state, GameplayQuery.GetActiveWeapon),
            GameplayNucleus.query(state, GameplayQuery.GetCodexStacks),
        ).forEach { projection ->
            assertEquals(state.instanceId, projection.instanceId)
            assertEquals(GameplayRevision.ZERO, projection.revision)
        }
    }

    @Test
    fun acceptedStartCapturesContentAndSeedInsideRevisionOne() {
        val initial = GameplayState.initial(RunId(2))
        val configuration = validConfiguration(seed = 91_337)
        val frame = start(initial, configuration)

        assertEquals(GameplayRevision(1), frame.nextState.revision)
        assertEquals(GameplayRunPhase.RUNNING, frame.nextState.phase)
        assertSame(configuration.content, frame.nextState.content)
        assertSame(configuration.content, frame.nextState.engine!!.model.content)
        assertSame(configuration.content, frame.renderProjection.renderModel!!.content)
        assertEquals(KINETICKK_CONTENT_VERSION, frame.renderProjection.renderModel!!.content.version)
        assertEquals(frame.nextState.instanceId, frame.renderProjection.instanceId)
        assertEquals(frame.nextState.revision, frame.renderProjection.revision)
        assertEquals(42L, frame.renderProjection.renderModel!!.totalMatter)
        assertEquals(1, frame.outputs.size)
        val completion = assertIs<GameplayOutput.CompleteCommand>(frame.outputs.single())
        assertEquals(GameplayCommandOutcome.RunStarted, completion.result.outcome)
        assertNull(initial.engine)
        assertEquals(GameplayRevision.ZERO, initial.revision)
    }

    @Test
    fun sameSeedAndPulseTraceProducesTheSameImmutableProjection() {
        var first = start(GameplayState.initial(RunId(1)), validConfiguration(seed = 91_337)).nextState
        var second = start(GameplayState.initial(RunId(2)), validConfiguration(seed = 91_337)).nextState
        val trace = listOf(
            GameplayInteractionPulse.ViewportChanged(1_280f, 720f, 1.5f),
            GameplayInteractionPulse.PointerMoved(1_100f, 240f),
            GameplayInteractionPulse.FrameElapsed(0.1f),
            GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, true),
            GameplayInteractionPulse.DashRequested,
            GameplayInteractionPulse.FrameElapsed(0.1f),
            GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, false),
        )

        trace.forEach { pulse ->
            first = accepted(GameplayNucleus.decide(first, pulse)).nextState
            second = accepted(GameplayNucleus.decide(second, pulse)).nextState
        }

        assertEquals(renderFacts(first), renderFacts(second))
    }

    @Test
    fun sessionCommandsRequireExactSourceTargetAdmissionAndPulse() {
        val state = GameplayState.initial(RunId(5))
        val pulse = GameplaySessionPulse.StartRun(validConfiguration())

        assertRejection(
            GameplayNucleus.decide(state, pulse),
            GameplayRejection.InvalidCommandRef(GameplayCommandRefRejection.WRONG_SOURCE_KIND),
        )

        val exact = command(state, pulse, sourceRevision = 8, ordinal = 3)
        val wrongTargetRef = exact.ref.copy(
            targetInstance = kinetickk.ball.gameplay.api.GameplayInstanceId(RunId(6)),
        )
        assertRejection(
            GameplayNucleus.decide(
                state,
                pulse,
                GameplayContext(
                    GameplayCommand(wrongTargetRef, pulse),
                    GameplayCommandAdmission(wrongTargetRef),
                ),
            ),
            GameplayRejection.InvalidCommandRef(GameplayCommandRefRejection.WRONG_TARGET),
        )

        listOf(
            exact.ref.copy(sourceRevision = 9),
            exact.ref.copy(ordinal = 4),
        ).forEach { mismatchedAdmissionRef ->
            assertRejection(
                GameplayNucleus.decide(
                    state,
                    pulse,
                    GameplayContext(exact, GameplayCommandAdmission(mismatchedAdmissionRef)),
                ),
                GameplayRejection.InvalidCommandRef(GameplayCommandRefRejection.ADMISSION_MISMATCH),
            )
        }
        assertRejection(
            GameplayNucleus.decide(
                state,
                pulse,
                GameplayContext(
                    GameplayCommand(exact.ref, GameplaySessionPulse.ExitRun),
                    GameplayCommandAdmission(exact.ref),
                ),
            ),
            GameplayRejection.InvalidCommandRef(GameplayCommandRefRejection.ADMISSION_MISMATCH),
        )
        assertRejection(
            GameplayNucleus.decide(
                state,
                GameplayInteractionPulse.DashRequested,
                GameplayContext(exact, GameplayCommandAdmission(exact.ref)),
            ),
            GameplayRejection.InvalidCommandRef(GameplayCommandRefRejection.WRONG_SOURCE_KIND),
        )
    }

    @Test
    fun everyConfigurationRejectionIsClosedAndLeavesCreatedStateUnchanged() {
        val state = GameplayState.initial(RunId(9))
        val valid = validConfiguration()
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

        configurations.forEachIndexed { index, (configuration, reason) ->
            val pulse = GameplaySessionPulse.StartRun(configuration)
            val command = command(state, pulse, ordinal = index)
            assertRejection(
                GameplayNucleus.decide(
                    state,
                    pulse,
                    GameplayContext(command, GameplayCommandAdmission(command.ref)),
                ),
                GameplayRejection.InvalidConfiguration(reason),
            )
            assertNull(state.engine)
            assertEquals(GameplayRevision.ZERO, state.revision)
        }
    }

    @Test
    fun everyInputFieldAndReasonIsRejectedWithoutAFrame() {
        val state = start(GameplayState.initial(RunId(10)), validConfiguration()).nextState
        val cases = listOf(
            Triple(GameplayInteractionPulse.FrameElapsed(Float.NaN), GameplayInputField.FRAME_DELTA_SECONDS, GameplayInputReason.NON_FINITE),
            Triple(GameplayInteractionPulse.FrameElapsed(-0.001f), GameplayInputField.FRAME_DELTA_SECONDS, GameplayInputReason.BELOW_MINIMUM),
            Triple(GameplayInteractionPulse.FrameElapsed(1.001f), GameplayInputField.FRAME_DELTA_SECONDS, GameplayInputReason.ABOVE_MAXIMUM),
            Triple(GameplayInteractionPulse.ViewportChanged(0f, 720f, 1f), GameplayInputField.VIEWPORT_WIDTH, GameplayInputReason.BELOW_MINIMUM),
            Triple(GameplayInteractionPulse.ViewportChanged(1_280f, 0f, 1f), GameplayInputField.VIEWPORT_HEIGHT, GameplayInputReason.BELOW_MINIMUM),
            Triple(GameplayInteractionPulse.ViewportChanged(1_280f, 720f, 0.49f), GameplayInputField.DENSITY, GameplayInputReason.BELOW_MINIMUM),
            Triple(GameplayInteractionPulse.PointerMoved(-1f, 20f), GameplayInputField.POINTER_X, GameplayInputReason.BELOW_MINIMUM),
            Triple(GameplayInteractionPulse.PointerMoved(20f, 721f), GameplayInputField.POINTER_Y, GameplayInputReason.ABOVE_MAXIMUM),
            Triple(GameplayInteractionPulse.ChoiceSelected(-1), GameplayInputField.CHOICE_INDEX, GameplayInputReason.BELOW_MINIMUM),
            Triple(GameplayInteractionPulse.ChoiceSelected(4), GameplayInputField.CHOICE_INDEX, GameplayInputReason.ABOVE_MAXIMUM),
        )

        cases.forEach { (pulse, field, reason) ->
            assertRejection(
                GameplayNucleus.decide(state, pulse),
                GameplayRejection.InvalidInput(field, reason),
            )
        }
        assertEquals(GameplayRevision(1), state.revision)
    }

    @Test
    fun legacySessionDuplicatesAreAcceptedSameSemanticState() {
        val state = start(GameplayState.initial(RunId(11)), validConfiguration()).nextState
        listOf(
            GameplayInteractionPulse.PauseForOverlay,
            GameplayInteractionPulse.ExitRunRequested,
            GameplayInteractionPulse.PreferencesChanged(PlayerPreferences(masterVolume = 0.25f)),
        ).forEach { pulse ->
            val frame = accepted(GameplayNucleus.decide(state, pulse))
            assertEquals(GameplayRevision(state.revision.value + 1), frame.nextState.revision)
            assertEquals(state.phase, frame.nextState.phase)
            assertSame(state.engine, frame.nextState.engine)
            assertSame(state.content, frame.nextState.content)
            assertTrue(frame.outputs.isEmpty())
        }
    }

    @Test
    fun lifecycleAndSessionOperationMatrixIsClosed() {
        val created = GameplayState.initial(RunId(12))
        assertRejection(GameplayNucleus.decide(created, GameplayInteractionPulse.DashRequested), GameplayRejection.NotStarted)
        assertSessionRejection(created, GameplaySessionPulse.PauseForOverlay, GameplayRejection.NotStarted)
        assertSessionRejection(created, GameplaySessionPulse.ApplyPreferences(PlayerPreferences()), GameplayRejection.NotStarted)
        assertSessionRejection(created, GameplaySessionPulse.ExitRun, GameplayRejection.NotStarted)

        val running = start(created, validConfiguration()).nextState
        val paused = session(running, GameplaySessionPulse.PauseForOverlay).nextState
        assertEquals(GameplayRunPhase.PAUSED, paused.phase)
        assertSessionRejection(paused, GameplaySessionPulse.PauseForOverlay, GameplayRejection.PauseUnavailable)
        val resumed = accepted(GameplayNucleus.decide(paused, GameplayInteractionPulse.PauseToggled)).nextState
        assertEquals(GameplayRunPhase.RUNNING, resumed.phase)

        val phases = listOf(
            GameplayRunPhase.RUNNING to GamePhase.RUNNING,
            GameplayRunPhase.PAUSED to GamePhase.PAUSED,
            GameplayRunPhase.CHOICE to GamePhase.CHOICE,
            GameplayRunPhase.GAME_OVER to GamePhase.GAME_OVER,
            GameplayRunPhase.VICTORY to GamePhase.VICTORY,
        )
        phases.forEach { (runPhase, renderPhase) ->
            val state = running.withPhase(runPhase, renderPhase)
            if (runPhase != GameplayRunPhase.RUNNING) {
                assertSessionRejection(
                    state,
                    GameplaySessionPulse.PauseForOverlay,
                    GameplayRejection.PauseUnavailable,
                )
            }
            val applied = session(
                state,
                GameplaySessionPulse.ApplyPreferences(PlayerPreferences(masterVolume = 0.4f)),
            )
            assertEquals(runPhase, applied.nextState.phase)
            assertEquals(0.4f, applied.nextState.engine!!.model.settings.masterVolume)
            val exited = session(state, GameplaySessionPulse.ExitRun)
            assertEquals(GameplayRunPhase.EXITED, exited.nextState.phase)
            assertIs<GameplayOutput.CompleteCommand>(exited.outputs.last())
            assertSessionRejection(
                state,
                GameplaySessionPulse.StartRun(validConfiguration()),
                GameplayRejection.AlreadyStarted,
            )
        }

        val exited = session(running, GameplaySessionPulse.ExitRun).nextState
        assertRejection(GameplayNucleus.decide(exited, GameplayInteractionPulse.DashRequested), GameplayRejection.RunExited)
        assertSessionRejection(exited, GameplaySessionPulse.ExitRun, GameplayRejection.RunExited)
        assertSessionRejection(
            exited,
            GameplaySessionPulse.StartRun(validConfiguration()),
            GameplayRejection.RunExited,
        )
        assertSessionRejection(
            running,
            GameplaySessionPulse.ApplyPreferences(PlayerPreferences(textScale = Float.NaN)),
            GameplayRejection.InvalidConfiguration(
                GameplayConfigurationRejection.INVALID_PREFERENCES,
            ),
        )
    }

    @Test
    fun exitWithoutProgressCompletesImmediately() {
        val state = start(GameplayState.initial(RunId(20)), validConfiguration()).nextState
        val frame = session(state, GameplaySessionPulse.ExitRun)

        assertEquals(GameplayRunPhase.EXITED, frame.nextState.phase)
        assertNull(frame.nextState.pendingProfileCommand)
        val completion = assertIs<GameplayOutput.CompleteCommand>(frame.outputs.single())
        assertEquals(
            GameplayCommandOutcome.RunExited(GameplayExitProfileOutcome.NoProgress),
            completion.result.outcome,
        )
    }

    @Test
    fun exitWithProgressDefersCompletionAndBuildsExactProfileCommand() {
        val state = startedWithPendingProgress(runId = 21, bankedMatter = 9)
        val exitCommand = command(state, GameplaySessionPulse.ExitRun, sourceRevision = 44, ordinal = 6)
        val frame = accepted(
            GameplayNucleus.decide(
                state,
                GameplaySessionPulse.ExitRun,
                GameplayContext(exitCommand, GameplayCommandAdmission(exitCommand.ref)),
            ),
        )

        assertEquals(GameplayRunPhase.EXITED, frame.nextState.phase)
        assertEquals(1, frame.outputs.size)
        val sent = assertIs<GameplayOutput.SendProfileCommand>(frame.outputs.single())
        assertEquals(ProfileSource.GameplayRun(21), sent.command.ref.sourceInstance)
        assertEquals(LOCAL_PROFILE_INSTANCE_ID, sent.command.ref.targetInstance)
        assertEquals(frame.nextState.revision.value, sent.command.ref.sourceRevision)
        assertEquals(0, sent.command.ref.ordinal)
        val pending = frame.nextState.pendingProfileCommand!!
        assertEquals(sent.command, pending.command)
        assertEquals(exitCommand.ref, pending.exitCompletion)
        assertEquals(1, frame.nextState.nextProfileCommandOrdinal)
        assertTrue(frame.outputs.none { it is GameplayOutput.CompleteCommand })
    }

    @Test
    fun acceptedAndPreacceptRejectedProfileCompletionsAreExactlyCorrelated() {
        val exiting = session(startedWithPendingProgress(22, 7), GameplaySessionPulse.ExitRun).nextState
        val pendingRef = exiting.pendingProfileCommand!!.command.ref

        val applied = accepted(
            GameplayNucleus.decide(
                exiting,
                GameplayControlPulse.ProfileCommandCompleted(
                    ProfileCommandResult.Accepted(
                        pendingRef,
                        ProfileRevision(18),
                        ProfileCommandOutcome.GameplayProgressApplied,
                    ),
                ),
            ),
        )
        assertNull(applied.nextState.pendingProfileCommand)
        assertEquals(exiting.revision.value + 1, applied.nextState.revision.value)
        val appliedCompletion = assertIs<GameplayOutput.CompleteCommand>(applied.outputs.single())
        assertEquals(
            GameplayCommandOutcome.RunExited(GameplayExitProfileOutcome.ProgressApplied),
            appliedCompletion.result.outcome,
        )

        val rejectedBeforeAcceptance = ProfileAcceptance.Rejected(
            LOCAL_PROFILE_INSTANCE_ID,
            ProfileRevision(19),
            ProfileRejection.NoChange,
        )
        val rejected = accepted(
            GameplayNucleus.decide(
                exiting,
                GameplayControlPulse.ProfileCommandRejectedBeforeAcceptance(
                    pendingRef,
                    rejectedBeforeAcceptance,
                ),
            ),
        )
        val rejectedCompletion = assertIs<GameplayOutput.CompleteCommand>(rejected.outputs.single())
        assertEquals(
            GameplayCommandOutcome.RunExited(
                GameplayExitProfileOutcome.ProgressRejected(
                    ProfileRevision(19),
                    ProfileRejection.NoChange,
                ),
            ),
            rejectedCompletion.result.outcome,
        )
    }

    @Test
    fun missingWrongAndMismatchedProfileReturnsRejectWithoutClearingPending() {
        val running = start(GameplayState.initial(RunId(23)), validConfiguration()).nextState
        val arbitraryRef = kinetickk.ball.profile.api.ProfileCommandRef(
            ProfileSource.GameplayRun(23),
            LOCAL_PROFILE_INSTANCE_ID,
            running.revision.value,
            0,
        )
        assertRejection(
            GameplayNucleus.decide(
                running,
                GameplayControlPulse.ProfileCommandCompleted(
                    ProfileCommandResult.Accepted(
                        arbitraryRef,
                        ProfileRevision(2),
                        ProfileCommandOutcome.GameplayProgressApplied,
                    ),
                ),
            ),
            GameplayRejection.UnexpectedProfileResult(
                GameplayProfileResultRejection.NO_COMMAND_PENDING,
            ),
        )

        val exiting = session(startedWithPendingProgress(23, 6), GameplaySessionPulse.ExitRun).nextState
        val pending = exiting.pendingProfileCommand!!
        listOf(
            pending.command.ref.copy(sourceInstance = ProfileSource.GameplayRun(999)),
            pending.command.ref.copy(sourceRevision = pending.command.ref.sourceRevision + 1),
            pending.command.ref.copy(ordinal = pending.command.ref.ordinal + 1),
        ).forEach { wrongRef ->
            assertRejection(
                GameplayNucleus.decide(
                    exiting,
                    GameplayControlPulse.ProfileCommandCompleted(
                        ProfileCommandResult.Accepted(
                            wrongRef,
                            ProfileRevision(3),
                            ProfileCommandOutcome.GameplayProgressApplied,
                        ),
                    ),
                ),
                GameplayRejection.UnexpectedProfileResult(
                    GameplayProfileResultRejection.COMMAND_REF_MISMATCH,
                ),
            )
        }
        assertRejection(
            GameplayNucleus.decide(
                exiting,
                GameplayControlPulse.ProfileCommandCompleted(
                    ProfileCommandResult.Accepted(
                        pending.command.ref,
                        ProfileRevision(3),
                        ProfileCommandOutcome.PreferencesChanged(PlayerPreferences()),
                    ),
                ),
            ),
            GameplayRejection.UnexpectedProfileResult(
                GameplayProfileResultRejection.OUTCOME_MISMATCH,
            ),
        )
        assertSame(pending, exiting.pendingProfileCommand)
    }

    @Test
    fun atMostOneProfileCommandCanBePending() {
        val state = start(GameplayState.initial(RunId(24)), validConfiguration()).nextState
        val firstCandidate = state.engine!!.model.copyForReduction().apply { pendingBankedMatter = 3 }
        val withProgress = state.copy(engine = EngineState(firstCandidate))
        val first = accepted(
            GameplayNucleus.decide(withProgress, GameplayInteractionPulse.FrameElapsed(0f)),
        ).nextState
        assertTrue(first.pendingProfileCommand != null)

        val secondCandidate = first.engine!!.model.copyForReduction().apply { pendingBankedMatter = 4 }
        val secondAttemptState = first.copy(engine = EngineState(secondCandidate))
        assertRejection(
            GameplayNucleus.decide(secondAttemptState, GameplayInteractionPulse.FrameElapsed(0f)),
            GameplayRejection.ProfileCommandPending,
        )
        assertSame(first.pendingProfileCommand, secondAttemptState.pendingProfileCommand)
    }

    @Test
    fun outputsKeepFxProfileAudioOrderAndBound() {
        val state = start(GameplayState.initial(RunId(25)), validConfiguration()).nextState
        val candidate = state.engine!!.model.copyForReduction().apply {
            pendingBankedMatter = 9
            emitVisualFx(VisualFxCue.ShockwaveAdded(1f, 2f, 0.3f, 40f, 2))
            emitSound(GameplayAudioCue.DASH)
        }
        val frame = accepted(
            GameplayNucleus.decide(
                state.copy(engine = EngineState(candidate)),
                GameplayInteractionPulse.FrameElapsed(0f),
            ),
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
    fun acceptedFrameAllowsThreeSemanticOutputsButRejectsFourth() {
        val state = GameplayState.initial(RunId(26))
        val render = GameplayNucleus.query(state, GameplayQuery.GetRender)
        GameplayAcceptedFrame(
            state,
            render,
            immutableListOf(
                GameplayOutput.EnsureAudioUnlocked,
                GameplayOutput.EnsureAudioUnlocked,
                GameplayOutput.EnsureAudioUnlocked,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            GameplayAcceptedFrame(
                state,
                render,
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
                state.copy(revision = GameplayRevision(1)),
                render,
                immutableListOf(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayAcceptedFrame(
                state,
                render,
                immutableListOf(
                    GameplayOutput.AdvanceAudio(0f, immutableListOf()),
                    GameplayOutput.EmitVisualFx(immutableListOf()),
                ),
            )
        }
        val commandRef = GameplayCommandRef(
            GameplayCommandSource.LocalSession,
            state.instanceId,
            sourceRevision = 0,
            ordinal = 0,
        )
        assertFailsWith<IllegalArgumentException> {
            GameplayAcceptedFrame(
                state,
                render,
                immutableListOf(
                    GameplayOutput.CompleteCommand(
                        GameplayCommandResult.Accepted(
                            commandRef,
                            GameplayRevision.ZERO,
                            GameplayCommandOutcome.RunStarted,
                        ),
                    ),
                    GameplayOutput.EnsureAudioUnlocked,
                ),
            )
        }
    }

    @Test
    fun stateRejectsLifecycleAndSimulationPhaseDriftExceptForExited() {
        val running = start(GameplayState.initial(RunId(29)), validConfiguration()).nextState

        assertFailsWith<IllegalArgumentException> {
            running.copy(phase = GameplayRunPhase.PAUSED)
        }
        running.copy(phase = GameplayRunPhase.EXITED)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun retainedQueriesOwnImmutableCollectionsAndDoNotChangeLater() {
        val state = start(GameplayState.initial(RunId(27)), validConfiguration()).nextState
        val retainedRender = GameplayNucleus.query(state, GameplayQuery.GetRender).renderModel!!
        val retainedStacks = GameplayNucleus.query(state, GameplayQuery.GetCodexStacks).itemStacks
        val retainedCoreX = retainedRender.coreX
        val advanced = accepted(
            GameplayNucleus.decide(state, GameplayInteractionPulse.FrameElapsed(0.1f)),
        ).nextState

        assertIs<ImmutableList<*>>(retainedRender.enemies)
        assertFalse((retainedRender.enemies as Any) is MutableList<*>)
        assertFailsWith<ClassCastException> {
            (retainedStacks as Any) as MutableList<Int>
        }
        assertEquals(retainedCoreX, retainedRender.coreX)
        assertNotEquals(state.revision, advanced.revision)
        assertSame(state.content, advanced.content)
        assertSame(state.content, GameplayNucleus.query(advanced, GameplayQuery.GetRender).renderModel!!.content)
    }

    @Test
    fun revisionExhaustionRejectsWithoutReducing() {
        val state = start(GameplayState.initial(RunId(28)), validConfiguration()).nextState.copy(
            revision = GameplayRevision(Long.MAX_VALUE),
        )

        assertRejection(
            GameplayNucleus.decide(state, GameplayInteractionPulse.FrameElapsed(0.1f)),
            GameplayRejection.RevisionExhausted,
        )
    }

    private fun start(
        state: GameplayState,
        configuration: RunConfiguration,
    ): GameplayAcceptedFrame = session(state, GameplaySessionPulse.StartRun(configuration))

    private fun session(
        state: GameplayState,
        pulse: GameplaySessionPulse,
    ): GameplayAcceptedFrame {
        val command = command(state, pulse)
        return accepted(
            GameplayNucleus.decide(
                state,
                pulse,
                GameplayContext(command, GameplayCommandAdmission(command.ref)),
            ),
        )
    }

    private fun assertSessionRejection(
        state: GameplayState,
        pulse: GameplaySessionPulse,
        reason: GameplayRejection,
    ) {
        val command = command(state, pulse)
        assertRejection(
            GameplayNucleus.decide(
                state,
                pulse,
                GameplayContext(command, GameplayCommandAdmission(command.ref)),
            ),
            reason,
        )
    }

    private fun command(
        state: GameplayState,
        pulse: GameplaySessionPulse,
        sourceRevision: Long = state.revision.value,
        ordinal: Int = 0,
    ): GameplayCommand = GameplayCommand(
        GameplayCommandRef(
            GameplayCommandSource.LocalSession,
            state.instanceId,
            sourceRevision,
            ordinal,
        ),
        pulse,
    )

    private fun startedWithPendingProgress(
        runId: Long,
        bankedMatter: Long,
    ): GameplayState {
        val state = start(GameplayState.initial(RunId(runId)), validConfiguration()).nextState
        val candidate = state.engine!!.model.copyForReduction().apply {
            runMatter = bankedMatter
        }
        return state.copy(engine = EngineState(candidate))
    }

    private fun GameplayState.withPhase(
        runPhase: GameplayRunPhase,
        renderPhase: GamePhase,
    ): GameplayState {
        val candidate = engine!!.model.copyForReduction().apply { phase = renderPhase }
        return copy(phase = runPhase, engine = EngineState(candidate))
    }
}

private fun validConfiguration(seed: Int = 731_991): RunConfiguration = RunConfiguration(
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
    val revision: Long,
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
    GameplayNucleus.query(state, GameplayQuery.GetRender).renderModel!!.let { model ->
        RenderFacts(
            state.revision.value,
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
