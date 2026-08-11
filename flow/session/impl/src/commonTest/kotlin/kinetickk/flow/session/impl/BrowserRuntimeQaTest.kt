// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResetReason
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionResetLifecycle
import kinetickk.flow.session.api.SessionWorkflowFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Deterministic cross-platform flow QA; Wasm executes this class in isolated Chrome. */
class BrowserRuntimeQaTest {
    @Test
    fun resetModalLifecycleSupportsCancelConfirmAndExplicitRetry() {
        val resetReason = ProfileResetReason.LegacyDataDetected
        val profile = FakeSessionProfilePort().apply {
            bootstrap = ProfileBootstrapStatus.Blocked(
                ProfileBootstrapBlockReason.ResetRequired(resetReason),
            )
            reset = ProfileResetStatus.ConfirmationRequired(
                resetReason,
                ProfileLegacyKeys.ALL,
            )
        }
        val rig = AppSessionTestRig(profile = profile)
        assertEquals(
            SessionResetLifecycle.CONFIRMATION_REQUIRED,
            shell(rig).resetLifecycle,
        )
        assertFalse(shell(rig).normalInputEnabled)
        assertIs<SessionAcceptance.Rejected>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.ResetCancelled),
        )
        assertEquals(
            SessionResetLifecycle.CONFIRMATION_REQUIRED,
            shell(rig).resetLifecycle,
        )
        assertTrue(profile.commands.isEmpty())

        val purgeResult = ProfileLegacyPurgeResult.Partial(ProfileLegacyKeys.ALL)
        val needsAttention = ProfileResetStatus.NeedsAttention(
            legacyKeys = ProfileLegacyKeys.ALL,
            result = purgeResult,
        )
        profile.commandHandler = { command ->
            when (command.pulse) {
                ProfilePulse.ConfirmLegacyReset -> profile.complete(
                    command,
                    ProfileCommandOutcome.ResetNeedsAttention(needsAttention),
                )
                ProfilePulse.RetryLegacyPurge -> profile.complete(
                    command,
                    ProfileCommandOutcome.ResetCompleted,
                )
                else -> error("Unexpected reset QA command: ${command.pulse}")
            }
        }

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.ResetConfirmed),
        )
        assertEquals(
            SessionResetLifecycle.PURGE_NEEDS_ATTENTION,
            shell(rig).resetLifecycle,
        )
        assertIs<SessionWorkflowFailure.ResetNeedsAttention>(shell(rig).workflowFailure)

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.ResetRetryRequested),
        )
        assertEquals(SessionResetLifecycle.READY, shell(rig).resetLifecycle)
        assertNull(shell(rig).workflowFailure)
        assertTrue(shell(rig).normalInputEnabled)
        assertEquals(
            listOf(ProfilePulse.ConfirmLegacyReset, ProfilePulse.RetryLegacyPurge),
            profile.commands.map { it.pulse },
        )
    }

    @Test
    fun exactSevenRouteInventoryOpensAndClosesEveryOverlay() {
        val routeInventory = listOf(
            AppDestination.Home,
            AppDestination.Gameplay,
            AppDestination.Settings,
            AppDestination.Lab,
            AppDestination.Armory,
            AppDestination.Rebirth,
            AppDestination.Codex,
        )
        val overlays = routeInventory.drop(2)
        assertEquals(7, routeInventory.size)
        assertEquals(7, routeInventory.toSet().size)

        val rig = AppSessionTestRig()
        overlays.forEach { destination ->
            assertIs<SessionAcceptance.Accepted>(
                rig.component.accept(SessionInteractionPulse.OpenOverlay(destination)),
            )
            val opened = shell(rig)
            assertEquals(destination, opened.active)
            assertEquals(listOf(AppDestination.Home, destination), opened.entries)

            assertIs<SessionAcceptance.Accepted>(
                rig.component.accept(SessionInteractionPulse.CloseOverlay),
            )
            val closed = shell(rig)
            assertEquals(AppDestination.Home, closed.active)
            assertEquals(listOf<AppDestination>(AppDestination.Home), closed.entries)
        }

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )
        assertEquals(AppDestination.Gameplay, shell(rig).active)
        assertTrue(shell(rig).active in routeInventory)
    }

    @Test
    fun gameplayStartPauseSettingsRestartAndExitRunAsOneDeterministicWorkflow() {
        val rig = AppSessionTestRig()
        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )
        assertEquals(RunId(0L), shell(rig).activeRunId)
        assertEquals(GameplayRunPhase.RUNNING, shell(rig).gameplayPhase)

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(
                SessionInteractionPulse.OpenOverlay(AppDestination.Settings),
            ),
        )
        assertEquals(AppDestination.Settings, shell(rig).overlay)
        assertEquals(GameplayRunPhase.PAUSED, shell(rig).gameplayPhase)
        assertTrue(
            rig.gameplay.activeFakeRun()!!.commands.any {
                it.pulse == GameplaySessionPulse.PauseForOverlay
            },
        )

        val expectedPreferences = PlayerPreferences(
            soundEnabled = false,
            musicEnabled = false,
            textScale = 1.5f,
        )
        rig.profile.profile = rig.profile.profile.copy(preferences = expectedPreferences)
        rig.profile.revision = ProfileRevision(rig.profile.revision.value + 1L)
        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.CloseOverlay),
        )
        assertNull(shell(rig).overlay)
        assertEquals(
            GameplaySessionPulse.ApplyPreferences(expectedPreferences),
            rig.gameplay.activeFakeRun()!!.commands.last().pulse,
        )
        assertEquals(expectedPreferences, rig.audioPreferences.last())

        rig.gameplay.activeFakeRun()!!.phase = GameplayRunPhase.GAME_OVER
        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.RestartRunRequested),
        )
        assertEquals(listOf(RunId(0L), RunId(1L)), rig.gameplay.createdRunIds)
        assertEquals(RunId(1L), shell(rig).activeRunId)
        assertEquals(GameplayRunPhase.RUNNING, shell(rig).gameplayPhase)

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.ExitRunRequested),
        )
        assertEquals(AppDestination.Home, shell(rig).base)
        assertEquals(GameplayRunPhase.EXITED, shell(rig).gameplayPhase)
        assertNull(shell(rig).pendingWorkflow)
    }

    @Test
    fun gameplayAndProfilePreacceptRejectionsRecoverWithoutPersistentProviders() {
        val gameplayRig = AppSessionTestRig()
        var rejectFirstPause = true
        gameplayRig.gameplay.configureRun = { run ->
            run.commandHandler = { command ->
                when (val pulse = command.pulse) {
                    is GameplaySessionPulse.StartRun -> {
                        run.phase = GameplayRunPhase.RUNNING
                        run.complete(command, GameplayCommandOutcome.RunStarted)
                    }
                    GameplaySessionPulse.PauseForOverlay -> if (rejectFirstPause) {
                        rejectFirstPause = false
                        GameplayAcceptance.Rejected(
                            run.instanceId,
                            run.revision,
                            GameplayRejection.PauseUnavailable,
                        )
                    } else {
                        run.phase = GameplayRunPhase.PAUSED
                        run.complete(command, GameplayCommandOutcome.OverlayPaused)
                    }
                    is GameplaySessionPulse.ApplyPreferences,
                    GameplaySessionPulse.ExitRun,
                    -> error("Unexpected Gameplay QA command: $pulse")
                }
            }
        }
        assertIs<SessionAcceptance.Accepted>(
            gameplayRig.component.accept(SessionInteractionPulse.StartRunRequested),
        )
        assertIs<SessionAcceptance.Accepted>(
            gameplayRig.component.accept(
                SessionInteractionPulse.OpenOverlay(AppDestination.Lab),
            ),
        )
        assertNull(shell(gameplayRig).overlay)
        assertNull(shell(gameplayRig).pendingWorkflow)
        assertIs<SessionWorkflowFailure.GameplayCommandRejected>(
            shell(gameplayRig).workflowFailure,
        )

        assertIs<SessionAcceptance.Accepted>(
            gameplayRig.component.accept(
                SessionInteractionPulse.OpenOverlay(AppDestination.Lab),
            ),
        )
        assertEquals(AppDestination.Lab, shell(gameplayRig).overlay)
        assertNull(shell(gameplayRig).workflowFailure)

        val profileRig = AppSessionTestRig()
        var rejectFirstShape = true
        profileRig.profile.commandHandler = { command ->
            when (val pulse = command.pulse) {
                is ProfilePulse.SelectCoreShape -> if (rejectFirstShape) {
                    rejectFirstShape = false
                    ProfileAcceptance.Rejected(
                        profileRig.profile.instanceId,
                        profileRig.profile.revision,
                        ProfileRejection.CoreShapeLocked,
                    )
                } else {
                    profileRig.profile.complete(
                        command,
                        ProfileCommandOutcome.CoreShapeSelected(pulse.shape),
                    )
                }
                else -> error("Unexpected Profile QA command: $pulse")
            }
        }
        assertIs<SessionAcceptance.Accepted>(
            profileRig.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            ),
        )
        assertIs<SessionWorkflowFailure.ProfileCommandRejected>(
            shell(profileRig).workflowFailure,
        )
        assertEquals(CoreShape.ORB, profileRig.profile.profile.loadout.coreShape)

        assertIs<SessionAcceptance.Accepted>(
            profileRig.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            ),
        )
        assertEquals(CoreShape.PRISM, profileRig.profile.profile.loadout.coreShape)
        assertNull(shell(profileRig).workflowFailure)
    }
}

private fun shell(rig: AppSessionTestRig) = rig.component.query(AppSessionQuery.GetShell)
