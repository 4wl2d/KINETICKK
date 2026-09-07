// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.foundation.dispatch.InlineReply
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileProgress
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.gameplay.api.GameplayRunExited

import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.ball.gameplay.api.GameplaySettingsApplied
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameComponentTest {
    @Test
    fun createdComponentCapturesContentWithoutPublishingSimulation() {
        val component = component()

        val status = component.query(GameplayQuery.GetRunStatus)
        val render = component.renderSnapshot()
        assertEquals(GameplayRevision.ZERO, status.revision)
        assertEquals(GameplayRunPhase.CREATED, status.phase)
        assertFalse(status.progressPending)
        assertNull(render.renderModel)
        assertSame(SyntheticGameplayContent, component.stateSnapshot().content)
        assertNull(component.query(GameplayQuery.GetActiveWeapon).weapon)
        assertTrue(component.query(GameplayQuery.GetBuildSummary).itemStacks.isEmpty())
        assertTrue(component.visualFxSnapshot().particles.isEmpty())
    }

    @Test
    fun renderSnapshotIsBuiltOncePerCommittedRevision() {
        val component = component()
        val created = component.renderSnapshot()
        assertSame(created, component.renderSnapshot())

        component.start()
        val started = component.renderSnapshot()
        assertNotSame(created, started)
        assertSame(started, component.renderSnapshot())

        val acceptance = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f)),
        )
        val advanced = component.renderSnapshot()
        assertEquals(acceptance.revision, advanced.revision)
        assertNotSame(started, advanced)
        assertSame(advanced, component.renderSnapshot())
    }

    @Test
    fun renderModelIsReusedWhenOnlyTheStampedRevisionChanges() {
        val component = component()
        component.start()
        val started = component.renderSnapshot()

        val acceptance = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.UserGestureObserved),
        )
        val gesture = component.renderSnapshot()

        assertEquals(acceptance.revision, gesture.revision)
        assertNotSame(started, gesture)
        assertSame(started.renderModel, gesture.renderModel)
        assertSame(gesture, component.renderSnapshot())

        val beforeDashModel = gesture.renderModel
        assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.DashRequested),
        )
        assertSame(beforeDashModel, component.renderSnapshot().renderModel)
    }

    @Test
    fun dashCachedModelMatchesAnIndependentFreshProjectionExactly() {
        val component = component()
        component.start()
        val beforeModel = checkNotNull(component.renderSnapshot().renderModel)

        assertTrue(beforeModel.enemies.isNotEmpty())
        val acceptance = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.DashRequested),
        )
        val cachedSnapshot = component.renderSnapshot()
        val cachedModel = checkNotNull(cachedSnapshot.renderModel)

        // Project the committed state independently, bypassing GameComponent's cache.
        val freshSnapshot = GameplayNucleus.renderSnapshot(component.stateSnapshot())
        val freshModel = checkNotNull(freshSnapshot.renderModel)

        assertEquals(acceptance.revision, cachedSnapshot.revision)
        assertEquals(cachedSnapshot.instanceId, freshSnapshot.instanceId)
        assertEquals(cachedSnapshot.revision, freshSnapshot.revision)
        assertSame(beforeModel, cachedModel)
        assertNotSame(cachedModel, freshModel)
        assertSame(freshModel.content, cachedModel.content)
        assertEquals(freshModel.exactRenderWitness(), cachedModel.exactRenderWitness())
    }

    @Test
    fun scalarInputPublicationStructurallySharesUnchangedCollections() {
        val component = component()
        component.start()
        val before = checkNotNull(component.renderSnapshot().renderModel)

        assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.PointerMoved.fromValidated(1_000f, 300f)),
        )
        val after = checkNotNull(component.renderSnapshot().renderModel)

        assertNotSame(before, after)
        assertSame(before.enemies, after.enemies)
        assertSame(before.projectiles, after.projectiles)
        assertSame(before.pickups, after.pickups)
        assertSame(before.trail, after.trail)
        assertSame(before.weaponNodes, after.weaponNodes)
        assertSame(before.weaponOrbitals, after.weaponOrbitals)
        assertSame(before.choices, after.choices)
        assertEquals(1_000f, after.pointerX)
        assertEquals(300f, after.pointerY)
    }

    @Test
    fun rejectedLocalRootPublishesNothingAndLeavesTheNextDispatchAdmissible() {
        val component = component()
        component.start()
        val beforeState = component.stateSnapshot()
        val beforeRender = component.renderSnapshot()

        val rejection = assertIs<GameplayAcceptance.Rejected>(
            component.accept(GameplayInteractionPulse.PointerMoved.fromValidated(-1f, 0f)),
        )

        assertEquals(beforeState.instanceId, rejection.instanceId)
        assertEquals(beforeState.revision, rejection.observedRevision)
        assertEquals(
            kinetickk.ball.gameplay.api.GameplayRejection.PointerOutsideViewport(
                kinetickk.ball.gameplay.api.GameplayPointerAxis.HORIZONTAL,
            ),
            rejection.reason,
        )
        assertSame(beforeState, component.stateSnapshot())
        assertSame(beforeRender, component.renderSnapshot())

        val accepted = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.UserGestureObserved),
        )
        assertEquals(beforeState.revision.value + 1L, accepted.revision.value)
        assertEquals(accepted.revision, component.stateSnapshot().revision)
        assertEquals(accepted.revision, component.renderSnapshot().revision)
    }

    @Test
    fun localProfileCompletionDrainsAfterLaterAudioFaultInExactCausalOrder() {
        val events = mutableListOf<String>()
        val profile = TestProfilePort(events::add)
        profile.snapshot = resilientLocalDispatchProfile()
        val audio = RecordingGameplayAudioExecutor(eventSink = events::add)
        val component = component(profile, audio, content = LocalDispatchGameplayContent)
        component.start()
        component.advanceUntilItemChoice()
        profile.calls.clear()
        profile.results.clear()
        profile.replies.clear()
        audio.frames.clear()
        events.clear()
        audio.throwOnAdvance = true
        val beforeRevision = component.stateSnapshot().revision

        assertFailsWith<AudioResourceFault> {
            component.accept(GameplayInteractionPulse.ChoiceSelected.fromValidated(0))
        }

        assertEquals(1, profile.calls.size)
        assertEquals(1, profile.results.size)
        assertFailsWith<IllegalStateException> { profile.replies.single().checkAvailable() }
        assertEquals(listOf("profile", "audio"), events)
        assertFalse(component.query(GameplayQuery.GetRunStatus).progressPending)
        assertEquals(beforeRevision.value + 2L, component.stateSnapshot().revision.value)
        assertEquals(component.stateSnapshot().revision, component.renderSnapshot().revision)
    }

    @Test
    fun localProfileDeliverThenThrowStillExecutesAudioAndDrainsItsCompletion() {
        val events = mutableListOf<String>()
        val profile = TestProfilePort(events::add)
        profile.snapshot = resilientLocalDispatchProfile()
        val audio = RecordingGameplayAudioExecutor(eventSink = events::add)
        val component = component(profile, audio, content = LocalDispatchGameplayContent)
        component.start()
        component.advanceUntilItemChoice()
        profile.calls.clear()
        profile.results.clear()
        profile.replies.clear()
        audio.frames.clear()
        events.clear()
        profile.mode = ProfileMode.DeliverThenThrow
        val beforeRevision = component.stateSnapshot().revision

        assertFailsWith<ProfileInvocationFault> {
            component.accept(GameplayInteractionPulse.ChoiceSelected.fromValidated(0))
        }

        assertEquals(1, profile.calls.size)
        assertEquals(1, profile.results.size)
        assertFailsWith<IllegalStateException> { profile.replies.single().checkAvailable() }
        assertEquals(listOf("profile", "audio"), events)
        assertEquals(1, audio.frames.size)
        assertFalse(component.query(GameplayQuery.GetRunStatus).progressPending)
        assertEquals(beforeRevision.value + 2L, component.stateSnapshot().revision.value)
        assertEquals(component.stateSnapshot().revision, component.renderSnapshot().revision)
    }

    @Test
    fun startReadsProfileAtBoundaryThenDeliversCanonicalResultAfterPublication() {
        val profile = TestProfilePort()
        val component = component(profile)
        val caller = GameplayCommandTestCaller<GameplayRunStarted>()
        caller.call { reply ->
            component.startRun(reply)
            assertEquals(GameplayRunPhase.RUNNING, component.query(GameplayQuery.GetRunStatus).phase)
            assertSame(SyntheticGameplayContent, component.renderSnapshot().renderModel!!.content)
            assertTrue(caller.applied.isEmpty())
        }
        val result = caller.applied.single()
        assertEquals(GameplayRevision(1), result.revision)
        assertEquals(1, profile.bootstrapReadCount)
        assertEquals(component.instanceId.runId, result.runId)
        assertEquals(component.renderSnapshot().revision, result.revision)
    }

    @Test
    fun wrongTargetAndUnavailableBootstrapRefuseBeforePublication() {
        val closed = GameplayCommandTestCaller<GameplayRunStarted>()
        closed.call(component()::startRun)
        val profile = TestProfilePort()
        val component = component(profile)
        assertFailsWith<IllegalStateException> { component.startRun(checkNotNull(closed.lastReply)) }
        assertEquals(GameplayRevision.ZERO, component.stateSnapshot().revision)
        assertEquals(0, profile.bootstrapReadCount)
        profile.bootstrapResult = ProfileRunBootstrapResult.Unavailable(
            ProfileBootstrapStatus.Blocked(ProfileBootstrapBlockReason.ResourceFailure(
                ProfileReadFailure.PROVIDER_READ_FAILED,
            )),
        )
        val caller = GameplayCommandTestCaller<GameplayRunStarted>()
        caller.call(component::startRun)
        val decision = assertIs<GameplayRefusal.DecisionRejected>(caller.refused.single())
        assertEquals(kinetickk.ball.gameplay.api.GameplayRejection.ProfileBootstrapUnavailable, decision.reason)
        assertEquals(GameplayRevision.ZERO, component.stateSnapshot().revision)
        assertTrue(caller.applied.isEmpty())
    }

    @Test
    fun invalidTrustedProfileContextIsAStateDecisionRejection() {
        val profile = TestProfilePort().apply {
            snapshot = snapshot.copy(
                preferences = PlayerPreferences(masterVolume = Float.NaN),
            )
        }
        val component = component(profile)

        val caller = GameplayCommandTestCaller<GameplayRunStarted>()
        caller.call(component::startRun)
        val decision = assertIs<GameplayRefusal.DecisionRejected>(caller.refused.single())
        assertIs<kinetickk.ball.gameplay.api.GameplayRejection.InvalidStartConfiguration>(
            decision.reason,
        )
        assertEquals(GameplayRevision.ZERO, component.stateSnapshot().revision)
        assertNull(component.stateSnapshot().engine)
    }

    @Test
    fun invalidPreferencesContextIsAStateDecisionRejection() {
        val profile = TestProfilePort()
        val component = component(profile)
        component.start()
        val before = component.stateSnapshot()
        profile.snapshot = profile.snapshot.copy(
            preferences = PlayerPreferences(masterVolume = Float.NaN),
        )

        val caller = GameplayCommandTestCaller<GameplaySettingsApplied>()
        caller.call { component.applyPreferences(profile.snapshot.preferences, it) }

        assertEquals(
            GameplayRefusal.DecisionRejected(
                kinetickk.ball.gameplay.api.GameplayRejection.InvalidPreferencesProjection,
            ),
            caller.refused.single(),
        )
        assertEquals(before, component.stateSnapshot())
    }

    @Test
    fun applyPreferencesUsesCapturedSourceValueAtTheTargetBoundary() {
        val profile = TestProfilePort()
        val component = component(profile)
        component.start()
        profile.snapshot = profile.snapshot.copy(
            preferences = PlayerPreferences(masterVolume = 0.4f),
        )
        val captured = profile.snapshot.preferences
        profile.snapshot = profile.snapshot.copy(preferences = PlayerPreferences(masterVolume = 0.8f))

        val caller = GameplayCommandTestCaller<GameplaySettingsApplied>()
        caller.call { component.applyPreferences(captured, it) }

        assertEquals(0, profile.preferencesReadCount)
        assertEquals(0.4f, component.renderSnapshot().renderModel!!.settings.masterVolume)
        assertEquals(component.instanceId.runId, caller.applied.single().runId)
        assertEquals(component.renderSnapshot().revision, caller.applied.single().revision)
    }

    @Test
    fun closedSettingsScopeIsRejectedBeforeAnotherStateAndRenderPublication() {
        val component = component()
        component.start()
        val caller = GameplayCommandTestCaller<GameplaySettingsApplied>()
        caller.call { component.applyPreferences(PlayerPreferences(masterVolume = 0.4f), it) }
        val beforeState = component.stateSnapshot()
        val beforeRender = component.renderSnapshot()

        assertFailsWith<IllegalStateException> {
            component.applyPreferences(PlayerPreferences(masterVolume = 0.6f), caller.lastReply)
        }
        assertFailsWith<IllegalStateException> { caller.lastReply.accepted(caller.applied.single()) }

        assertSame(beforeState, component.stateSnapshot())
        assertSame(beforeRender, component.renderSnapshot())
    }

    @Test
    fun settingsRefusalBeforeRunStartPreservesTheCreatedRun() {
        val component = component()
        val beforeState = component.stateSnapshot()
        val beforeRender = component.renderSnapshot()
        val caller = GameplayCommandTestCaller<GameplaySettingsApplied>()

        caller.call { component.applyPreferences(PlayerPreferences(), it) }

        assertEquals(
            GameplayRefusal.DecisionRejected(kinetickk.ball.gameplay.api.GameplayRejection.NotStarted),
            caller.refused.single(),
        )
        assertTrue(caller.applied.isEmpty())
        assertSame(beforeState, component.stateSnapshot())
        assertSame(beforeRender, component.renderSnapshot())
    }

    @Test
    fun acceptedProfileProgressPreservesScopeAndCompletesExitNonReentrantly() {
        val profile = TestProfilePort()
        val component = component(profile)
        component.start()
        component.advanceUntilMatter()
        profile.calls.clear()
        val before = component.stateSnapshot().revision
        val caller = GameplayCommandTestCaller<GameplayRunExited>()
        profile.onProgress = {
            assertEquals(GameplayRunPhase.EXITED, component.query(GameplayQuery.GetRunStatus).phase)
            assertTrue(component.query(GameplayQuery.GetRunStatus).progressPending)
            assertTrue(caller.applied.isEmpty())
        }
        caller.call { reply ->
            component.exitRun(reply)
            assertTrue(caller.applied.isEmpty())
            assertFalse(component.query(GameplayQuery.GetRunStatus).progressPending)
        }
        val result = caller.applied.single()
        assertEquals(GameplayExitProgressResult.Applied, result.progress)
        assertEquals(component.instanceId.runId, result.runId)
        assertEquals(before.value + 2, result.revision.value)
        assertEquals(result.revision, component.stateSnapshot().revision)
        assertEquals(1, profile.calls.size)
    }

    @Test
    fun verifiedProfilePreacceptRefusalUsesCallerOwnedControlCarrier() {
        val profile = TestProfilePort().apply { mode = ProfileMode.Refuse }
        val component = component(profile)
        component.start()
        component.advanceUntilMatter()
        val result = component.exit()
        assertEquals(GameplayExitProgressResult.NotApplied, result.progress)
        assertEquals(GameplayRunPhase.EXITED, component.query(GameplayQuery.GetRunStatus).phase)
        assertFalse(component.query(GameplayQuery.GetRunStatus).progressPending)
    }

    @Test
    fun previousProfileScopeCannotCompleteTheCurrentProgress() {
        val profile = TestProfilePort()
        val previous = component(profile)
        previous.start()
        previous.advanceUntilMatter()
        previous.exit()
        val current = component(profile)
        current.start()
        current.advanceUntilMatter()
        profile.mode = ProfileMode.UsePreviousReply
        assertFailsWith<IllegalStateException> { current.exit() }
        assertTrue(current.query(GameplayQuery.GetRunStatus).progressPending)
    }

    @Test
    fun duplicateProfileReplyKeepsTheFirstAcceptedProgress() {
        val profile = TestProfilePort().apply { mode = ProfileMode.Duplicate }
        val component = component(profile)
        component.start()
        component.advanceUntilMatter()
        val caller = GameplayCommandTestCaller<GameplayRunExited>()
        assertFailsWith<IllegalStateException> { caller.call(component::exitRun) }
        assertEquals(GameplayExitProgressResult.Applied, caller.applied.single().progress)
        assertEquals(component.stateSnapshot().revision, caller.applied.single().revision)
        assertFalse(component.query(GameplayQuery.GetRunStatus).progressPending)
        assertEquals(1, profile.results.size)
    }

    @Test
    fun validProfileResultIsDrainedBeforePostDeliveryInvocationFaultIsRethrown() {
        val profile = TestProfilePort().apply { mode = ProfileMode.DeliverThenThrow }
        val component = component(profile)
        component.start()
        component.advanceUntilMatter()
        val caller = GameplayCommandTestCaller<GameplayRunExited>()
        assertFailsWith<ProfileInvocationFault> { caller.call(component::exitRun) }
        assertFalse(component.query(GameplayQuery.GetRunStatus).progressPending)
        val status = component.query(GameplayQuery.GetRunStatus)
        val state = component.stateSnapshot()
        val render = component.renderSnapshot()
        assertEquals(status.revision, state.revision)
        assertEquals(state.revision, render.revision)
        assertSame(render, component.renderSnapshot())
        assertEquals(GameplayExitProgressResult.Applied, caller.applied.single().progress)
    }

    @Test
    fun profileInvocationFaultBeforeResultPreservesPendingRouteWithoutFakeCarrier() {
        val profile = TestProfilePort().apply { mode = ProfileMode.ThrowBeforeResult }
        val component = component(profile)
        component.start()
        component.advanceUntilMatter()
        val caller = GameplayCommandTestCaller<GameplayRunExited>()
        assertFailsWith<ProfileInvocationFault> { caller.call(component::exitRun) }
        assertTrue(component.query(GameplayQuery.GetRunStatus).progressPending)
        assertTrue(caller.applied.isEmpty())
        assertTrue(caller.refused.isEmpty())
    }

    @Test
    fun featureAcceptsOneActiveRunAndRefusesEveryFirstNPlusOneReplacement() {
        val profile = TestProfilePort()
        val feature = gameplayFeature(profile)
        val run0 = assertIs<GameComponent>(feature.createRun(RunId(0)))

        assertSame(run0, feature.activeRun())
        assertFailsWith<IllegalStateException> {
            feature.createRun(RunId(1))
        }
        assertSame(run0, feature.activeRun())

        run0.start()
        assertFailsWith<IllegalStateException> {
            feature.createRun(RunId(1))
        }
        run0.exit()
        assertEquals(GameplayRunPhase.EXITED, run0.query(GameplayQuery.GetRunStatus).phase)
        assertFailsWith<IllegalArgumentException> {
            feature.createRun(RunId(0))
        }
        val run1 = feature.createRun(RunId(1))
        assertSame(run1, feature.activeRun())

        val pendingProfile = TestProfilePort().apply { mode = ProfileMode.ThrowBeforeResult }
        val pendingFeature = gameplayFeature(pendingProfile)
        val pendingRun = assertIs<GameComponent>(
            pendingFeature.createRun(RunId(0)),
        )
        pendingRun.start()
        pendingRun.advanceUntilMatter()
        assertFailsWith<ProfileInvocationFault> {
            pendingRun.exit()
        }
        assertTrue(pendingRun.query(GameplayQuery.GetRunStatus).progressPending)
        assertFailsWith<IllegalStateException> {
            pendingFeature.createRun(RunId(1))
        }
        assertSame(pendingRun, pendingFeature.activeRun())
    }

    @Test
    fun featureReusesTheExactCapturedContentAndFrozenSeedAcrossTerminalReplacement() {
        assertEquals(731_991, DEFAULT_GAMEPLAY_SEED)
        val profile = TestProfilePort()
        val feature = gameplayFeature(profile)
        val run0 = assertIs<GameComponent>(feature.createRun(RunId(0)))
        assertSame(SyntheticGameplayContent, run0.stateSnapshot().content)
        run0.start()
        val run0Render = run0.renderSnapshot().renderModel!!
        assertSame(SyntheticGameplayContent, run0Render.content)
        val run0StartFacts = listOf(
            run0Render.coreX,
            run0Render.coreY,
            run0Render.velocityX,
            run0Render.velocityY,
            run0Render.weapon,
            run0Render.morningstarAngle,
            run0Render.enemies,
            run0Render.choices,
        )
        run0.exit()

        val run1 = assertIs<GameComponent>(feature.createRun(RunId(1)))
        assertSame(SyntheticGameplayContent, run1.stateSnapshot().content)
        run1.start()
        val run1Render = run1.renderSnapshot().renderModel!!
        assertSame(SyntheticGameplayContent, run1Render.content)
        assertEquals(
            run0StartFacts,
            listOf(
                run1Render.coreX,
                run1Render.coreY,
                run1Render.velocityX,
                run1Render.velocityY,
                run1Render.weapon,
                run1Render.morningstarAngle,
                run1Render.enemies,
                run1Render.choices,
            ),
        )
    }

    @Test
    fun busyCallRefusalIsTypedAndPublishesNothing() {
        val profile = TestProfilePort()
        val component = component(profile)
        component.start()
        component.advanceUntilMatter()
        val busy = GameplayCommandTestCaller<GameplayRunExited>()
        profile.onProgress = {
            val before = component.stateSnapshot()
            val render = component.renderSnapshot()
            busy.call(component::exitRun)
            assertEquals(GameplayRefusal.Busy, busy.refused.single())
            assertSame(before, component.stateSnapshot())
            assertSame(render, component.renderSnapshot())
        }
        component.exit()
        assertTrue(busy.applied.isEmpty())
    }

    @Test
    fun audioFaultsPropagateAfterAcceptedFramesCommitAndDrainExactResults() {
        val unlockAudio = RecordingGameplayAudioExecutor()
        val unlockComponent = component(
            audio = unlockAudio,
        )
        val unlockStart = unlockComponent.start()
        unlockAudio.throwOnUnlock = true

        assertFailsWith<AudioResourceFault> {
            unlockComponent.accept(GameplayInteractionPulse.UserGestureObserved)
        }
        val started = unlockComponent.query(GameplayQuery.GetRunStatus)
        assertEquals(GameplayRevision(2), started.revision)
        assertEquals(GameplayRunPhase.RUNNING, started.phase)
        assertEquals(1, unlockAudio.unlockCount)
        assertEquals(GameplayRevision(1), unlockStart.revision)
        assertEquals(started.revision, unlockComponent.stateSnapshot().revision)
        assertEquals(started.revision, unlockComponent.renderSnapshot().revision)
        assertSame(unlockComponent.renderSnapshot(), unlockComponent.renderSnapshot())

        val frameAudio = RecordingGameplayAudioExecutor()
        val frameComponent = component(
            audio = frameAudio,
        )
        val frameStart = frameComponent.start()
        frameAudio.throwOnAdvance = true

        assertFailsWith<AudioResourceFault> {
            frameComponent.accept(GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f))
        }
        val advanced = frameComponent.query(GameplayQuery.GetRunStatus)
        assertEquals(GameplayRevision(2), advanced.revision)
        assertEquals(GameplayRunPhase.RUNNING, advanced.phase)
        assertEquals(1, frameAudio.frames.size)
        assertEquals(GameplayRevision(1), frameStart.revision)
        assertEquals(advanced.revision, frameComponent.stateSnapshot().revision)
        assertEquals(advanced.revision, frameComponent.renderSnapshot().revision)
        assertSame(frameComponent.renderSnapshot(), frameComponent.renderSnapshot())
    }

    @Test
    fun completionDequeAndStaticBoundsRefuseNPlusOneWithoutTruncation() {
        val completions = gameplayCompletionDeque<Int>()
        repeat(8) { value -> assertTrue(completions.tryAddLast(value)) }
        assertFalse(completions.tryAddLast(8))
        assertEquals((0 until 8).toList(), List(8) { completions.removeFirstOrNull() })

        requireGameplayProfileOutputFanoutBound(1)
        assertFailsWith<IllegalStateException> { requireGameplayProfileOutputFanoutBound(2) }
        requireGameplayCompletionCapacity(1, 1)
        assertFailsWith<IllegalStateException> { requireGameplayCompletionCapacity(0, 1) }

        assertTrue(
            hasGameplayRevisionCapacity(
                GameplayRevision(Long.MAX_VALUE - 1),
                requiredRevisions = 1L,
            ),
        )
        assertFalse(
            hasGameplayRevisionCapacity(
                GameplayRevision(Long.MAX_VALUE),
                requiredRevisions = 1L,
            ),
        )
        assertTrue(
            hasGameplayRevisionCapacity(
                GameplayRevision(Long.MAX_VALUE - 2),
                requiredRevisions = 2L,
            ),
        )
        assertFalse(
            hasGameplayRevisionCapacity(
                GameplayRevision(Long.MAX_VALUE - 1),
                requiredRevisions = 2L,
            ),
        )
    }
}

private fun component(
    profile: TestProfilePort = TestProfilePort(),
    audio: GameplayAudioExecutor = RecordingGameplayAudioExecutor(),
    content: kinetickk.ball.content.api.GameplayContentSnapshot = SyntheticGameplayContent,
): GameComponent {
    val component = GameComponent.create(
        runId = RunId(31),
        content = content,
        profilePort = profile,
        audioExecutor = audio,
        profileProgress = profile,
        seed = DEFAULT_GAMEPLAY_SEED,
    )
    return component
}

private fun GameComponent.exit(): GameplayRunExited =
    GameplayCommandTestCaller<GameplayRunExited>().also { it.call(this::exitRun) }.applied.single()

private fun GameComponent.start(): GameplayRunStarted =
    GameplayCommandTestCaller<GameplayRunStarted>().also { it.call(this::startRun) }.applied.single()

private fun GameComponent.advanceUntilMatter() {
    repeat(1_200) { frameIndex ->
        val render = checkNotNull(renderSnapshot().renderModel)
        if (render.runMatter > 0L) return
        check(query(GameplayQuery.GetRunStatus).phase == GameplayRunPhase.RUNNING) {
            "Run stopped before earning progress at frame $frameIndex"
        }
        assertIs<GameplayAcceptance.Accepted>(
            accept(GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f)),
        )
    }
    error("Run earned no progress within the deterministic frame budget")
}

private fun GameComponent.advanceUntilItemChoice() {
    repeat(7_200) { frameIndex ->
        val render = checkNotNull(renderSnapshot().renderModel)
        if (
            render.phase == kinetickk.ball.gameplay.nucleus.render.GamePhase.CHOICE &&
            render.choiceType == ChoiceType.ITEM &&
            render.choices.isNotEmpty()
        ) {
            return
        }
        check(query(GameplayQuery.GetRunStatus).phase == GameplayRunPhase.RUNNING) {
            "Run stopped before its first item choice at frame $frameIndex: ${render.phase} / ${render.choiceType}"
        }
        val pickupTarget = render.pickups.firstOrNull()?.let { pickup -> pickup.x to pickup.y }
        val nearestEnemy = render.enemies.minByOrNull { enemy ->
                val dx = enemy.x - render.coreX
                val dy = enemy.y - render.coreY
                dx * dx + dy * dy
            }
        val target = pickupTarget ?: nearestEnemy?.let { enemy ->
            val awayX = render.coreX - enemy.x
            val awayY = render.coreY - enemy.y
            val length = kotlin.math.sqrt(awayX * awayX + awayY * awayY).coerceAtLeast(1f)
            render.coreX + awayX / length * 600f to
                render.coreY + awayY / length * 600f
        }
        if (target != null) {
            val pointerX = (target.first - render.cameraX + render.screenWidth * 0.5f)
                .coerceIn(0f, render.screenWidth)
            val pointerY = (target.second - render.cameraY + render.screenHeight * 0.5f)
                .coerceIn(0f, render.screenHeight)
            assertIs<GameplayAcceptance.Accepted>(
                accept(GameplayInteractionPulse.PointerMoved.fromValidated(pointerX, pointerY)),
            )
        }
        if (frameIndex % 15 == 0) {
            assertIs<GameplayAcceptance.Accepted>(
                accept(GameplayInteractionPulse.DashRequested),
            )
        }
        assertIs<GameplayAcceptance.Accepted>(
            accept(GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f)),
        )
    }
    error("Run produced no item choice within the deterministic frame budget")
}

private fun resilientLocalDispatchProfile(): GameplayProfileSnapshot = PlayerProfile(
    loadout = PlayerLoadout(
        selectedWeapon = WeaponId.PRISM_RELAY,
        unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.PRISM_RELAY),
    ),
    labProgress = LabProgress(List(MetaUpgradeId.entries.size) { 10 }),
).toGameplaySnapshot()

private val LocalDispatchGameplayContent by lazy {
    val rebirth = SyntheticGameplayContent.rebirth
    SyntheticGameplayContent.copy(
        // This fixture exercises dispatch ordering; a single pickup reaches its item-choice boundary.
        tempo = SyntheticGameplayContent.tempo.copy(dataPickupMultiplier = 20f),
        rebirth = rebirth.copy(
            profiles = rebirth.profiles.map { profile ->
                profile.copy(
                    openingEnemyCount = 1,
                    enemyCapMultiplier = 0.1f,
                    spawnRateMultiplier = 1f,
                    enemyHealthMultiplier = 0.01f,
                    enemySpeedMultiplier = 0.25f,
                    incomingDamageMultiplier = 0f,
                    playerPowerMultiplier = 20f,
                    maximumActiveEnemies = 8,
                    minimumSpawnIntervalSeconds = 0.2f,
                )
            }.toImmutableList(),
            minSpawnIntervalSeconds = 0.2f,
        ),
    )
}

private enum class ProfileMode { Accept, Refuse, UsePreviousReply, Duplicate, DeliverThenThrow, ThrowBeforeResult }

private class TestProfilePort(private val eventSink: (String) -> Unit = {}) : ProfileReadPort, ProfileProgress {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    var snapshot: GameplayProfileSnapshot = PlayerProfile().toGameplaySnapshot()
    var bootstrapResult: ProfileRunBootstrapResult = ProfileRunBootstrapResult.Ready(snapshot)
    var mode: ProfileMode = ProfileMode.Accept
    var onProgress: (() -> Unit)? = null
    val calls = mutableListOf<GameplayProgressUpdate>()
    val replies = mutableListOf<InlineReply<ProfileProgressApplied, ProfileRefusal>>()
    val results = mutableListOf<ProfileProgressApplied>()
    var bootstrapReadCount: Int = 0
    var preferencesReadCount: Int = 0
    private var revision = ProfileRevision(10)

    override fun applyGameplayProgress(update: GameplayProgressUpdate, reply: InlineReply<ProfileProgressApplied, ProfileRefusal>) {
        reply.checkAvailable()
        val previous = replies.lastOrNull()
        calls += update
        replies += reply
        eventSink("profile")
        onProgress?.invoke()
        when (mode) {
            ProfileMode.ThrowBeforeResult -> throw ProfileInvocationFault()
            ProfileMode.Refuse -> reply.refused(ProfileRefusal.DecisionRejected(ProfileRejection.NoChange))
            ProfileMode.UsePreviousReply -> checkNotNull(previous).accepted(ProfileProgressApplied(revision))
            else -> {
                revision = ProfileRevision(revision.value + 1)
                val result = ProfileProgressApplied(revision)
                results += result
                reply.accepted(result)
                if (mode == ProfileMode.DeliverThenThrow) throw ProfileInvocationFault()
                if (mode == ProfileMode.Duplicate) reply.accepted(ProfileProgressApplied(ProfileRevision(revision.value + 1)))
            }
        }
    }

    override fun query(query: ProfileQuery.GetHomeProgress): kinetickk.ball.profile.api.HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetCollection): kinetickk.ball.profile.api.CollectionProjection = error("unused")
    override fun query(query: ProfileQuery.GetLabProgress): kinetickk.ball.profile.api.LabProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetLoadout): kinetickk.ball.profile.api.LoadoutProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): kinetickk.ball.profile.api.RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): kinetickk.ball.profile.api.PersistenceStatusProjection = error("unused")

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection {
        bootstrapReadCount++
        val result = if (bootstrapResult is ProfileRunBootstrapResult.Ready) {
            ProfileRunBootstrapResult.Ready(snapshot)
        } else {
            bootstrapResult
        }
        return RunBootstrapProjection(instanceId, revision, result)
    }

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection {
        preferencesReadCount++
        return PreferencesProjection(instanceId, revision, snapshot.preferences)
    }

}

private fun GameplayRenderModel.exactRenderWitness(): List<Any?> = listOf(
    phase,
    settings,
    rebirthLevel,
    screenWidth.toRawBits(),
    screenHeight.toRawBits(),
    uiScale.toRawBits(),
    coreX.toRawBits(),
    coreY.toRawBits(),
    velocityX.toRawBits(),
    velocityY.toRawBits(),
    cameraX.toRawBits(),
    cameraY.toRawBits(),
    pointerX.toRawBits(),
    pointerY.toRawBits(),
    pointerActive,
    braking,
    elapsed.toRawBits(),
    heat.toRawBits(),
    overheated,
    dashPhaseTime.toRawBits(),
    hp.toRawBits(),
    maxHp.toRawBits(),
    shield.toRawBits(),
    maxShield.toRawBits(),
    level,
    data,
    nextLevelData,
    keys,
    kills,
    combo,
    comboTime.toRawBits(),
    runMatter,
    totalMatter,
    lastImpact.toRawBits(),
    lastImpactTime.toRawBits(),
    damageFlash.toRawBits(),
    runGrace.toRawBits(),
    screenShake.toRawBits(),
    message,
    messageTime.toRawBits(),
    mass.toRawBits(),
    damageMultiplier.toRawBits(),
    weaponPower.toRawBits(),
    coolingRate.toRawBits(),
    magnetStrength.toRawBits(),
    dashImpulse.toRawBits(),
    dashHeatCost.toRawBits(),
    regenPerSecond.toRawBits(),
    critChance.toRawBits(),
    critMultiplier.toRawBits(),
    pickupRadius.toRawBits(),
    luck.toRawBits(),
    dataGain.toRawBits(),
    matterGain.toRawBits(),
    attackSpeed.toRawBits(),
    damageReduction.toRawBits(),
    comboWindow.toRawBits(),
    overdriveGain.toRawBits(),
    dragCoefficient.toRawBits(),
    polarityStability.toRawBits(),
    weapon,
    weaponLevel,
    overdriveCharge.toRawBits(),
    overdriveTime.toRawBits(),
    rerollsRemaining,
    acquiredItemCount,
    recentItem,
    equippedRelics.toList(),
    morningstarAngle.toRawBits(),
    morningstarX.toRawBits(),
    morningstarY.toRawBits(),
    weaponBeamTime.toRawBits(),
    weaponBeamStartX.toRawBits(),
    weaponBeamStartY.toRawBits(),
    weaponBeamEndX.toRawBits(),
    weaponBeamEndY.toRawBits(),
    totem,
    coreShape,
    enemies.toList(),
    projectiles.toList(),
    pickups.toList(),
    trail.toList(),
    weaponNodes.toList(),
    weaponOrbitals.toList(),
    choices.toList(),
    choiceType,
    pendingRelicChoiceCount,
    itemStacksSnapshot.toList(),
    discoveredItemCount,
    kinetickk.ball.content.api.RelicId.entries.map(::relicRank),
)

private fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences,
    economy,
    loadout,
    labProgress,
    collection,
    rebirthProgress,
)

private fun gameplayFeature(profile: TestProfilePort): DefaultGameplayFeature =
    DefaultGameplayFeature(
        gameplayContent = SyntheticGameplayContent,
        profilePort = profile,
        profileProgress = profile,
        audioService = NoOpAudioService,
    )

private class RecordingGameplayAudioExecutor(
    var throwOnAdvance: Boolean = false,
    var throwOnUnlock: Boolean = false,
    private val eventSink: (String) -> Unit = {},
) : GameplayAudioExecutor {
    val frames = mutableListOf<Pair<Float, List<GameplayAudioCue>>>()
    var unlockCount = 0

    override fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>) {
        frames += realDeltaSeconds to cues.toList()
        eventSink("audio")
        if (throwOnAdvance) throw AudioResourceFault()
    }

    override fun ensureUnlocked() {
        unlockCount++
        if (throwOnUnlock) throw AudioResourceFault()
    }
}

private class AudioResourceFault : RuntimeException()
private class ProfileInvocationFault : RuntimeException()

private object NoOpAudioService : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
