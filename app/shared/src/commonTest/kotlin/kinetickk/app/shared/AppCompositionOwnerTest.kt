// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.ball.content.api.ContentCatalog
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileResetReason
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileResourceFailure
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.api.CodexOutput
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GameplayCodexStacksProjection
import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandAdmission
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayExitProfileOutcome
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRenderProjection
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.api.RunId
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.nucleus.AppDestination
import kinetickk.ball.profile.interaction.lab.api.LabFeature
import kinetickk.ball.profile.interaction.lab.api.LabOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.flow.session.interaction.reset.api.ResetModalOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppCompositionOwnerTest {
    @Test
    fun ownerCapturesContentOnceAndReusesTheGameplaySnapshotForEveryRun() {
        val shell = testShell()

        assertEquals(1, shell.contentCatalog.profilePolicyCalls)
        assertEquals(1, shell.contentCatalog.gameplayContentCalls)
        assertEquals(1, shell.contentCatalog.uiCatalogCalls)

        shell.owner.startNewRun()
        shell.gameplay.phase = GameplayRunPhase.GAME_OVER
        shell.owner.handleGameplayOutput(GameplayInteractionOutput.RestartRun)

        assertEquals(2, shell.gameplay.starts.size)
        assertEquals(listOf(RunId(0L), RunId(1L)), shell.gameplay.createdRunIds)
        shell.gameplay.starts.forEach { configuration ->
            assertSame(shell.contentCatalog.gameplaySnapshot, configuration.content)
        }
        assertEquals(1, shell.contentCatalog.profilePolicyCalls)
        assertEquals(1, shell.contentCatalog.gameplayContentCalls)
        assertEquals(1, shell.contentCatalog.uiCatalogCalls)
    }

    @Test
    fun openingOverlayPausesRunAndBackKeepsSameSessionPaused() {
        val shell = testShell()
        shell.owner.startNewRun()

        assertTrue(shell.owner.handleShortcut(AppShortcut.LAB))
        assertEquals(1, shell.gameplay.pauseCalls)
        assertEquals(GameplayRunPhase.PAUSED, shell.gameplay.phase)
        assertEquals(
            listOf(AppDestination.Gameplay, AppDestination.Lab),
            shell.owner.backStack.entries,
        )

        assertTrue(shell.owner.handleShortcut(AppShortcut.BACK))
        assertEquals(listOf(AppDestination.Gameplay), shell.owner.backStack.entries)
        assertEquals(GameplayRunPhase.PAUSED, shell.gameplay.phase)
        assertEquals(1, shell.gameplay.starts.size)
    }

    @Test
    fun settingsPreferencesReachActiveRunOnlyWhenSettingsCloses() {
        val shell = testShell()
        shell.owner.startNewRun()
        shell.owner.handleShortcut(AppShortcut.SETTINGS)
        val changed = shell.store.profile.preferences.copy(
            simulationSpeed = 1.75f,
            textScale = 1.5f,
            soundEnabled = false,
        )
        shell.store.setProfile(shell.store.profile.copy(preferences = changed))

        assertTrue(shell.gameplay.appliedPreferences.isEmpty())

        shell.owner.handleSettingsOutput(SettingsOutput.Back)

        assertEquals(listOf(changed), shell.gameplay.appliedPreferences)
        assertEquals(listOf(AppDestination.Gameplay), shell.owner.backStack.entries)
    }

    @Test
    fun replacingSettingsOverlayAlsoFinalizesPersistedPreferences() {
        val shell = testShell()
        shell.owner.startNewRun()
        shell.owner.handleShortcut(AppShortcut.SETTINGS)
        val changed = shell.store.preferences.copy(textScale = 1.75f)
        shell.store.setProfile(shell.store.profile.copy(preferences = changed))

        shell.owner.handleShortcut(AppShortcut.LAB)

        assertEquals(listOf(changed), shell.gameplay.appliedPreferences)
        assertEquals(AppDestination.Lab, shell.owner.backStack.overlay)
    }

    @Test
    fun labAndArmoryChangesApplyToNextRunWithoutMutatingActiveRun() {
        val shell = testShell(
            profile = PlayerProfile(
                economy = PlayerEconomy(matter = 500L, lifetimeMatter = 900L),
            ),
        )
        shell.owner.startNewRun()
        val activeConfiguration = shell.gameplay.starts.single()
        val nextProfile = shell.store.profile.copy(
            loadout = PlayerLoadout(
                coreShape = CoreShape.PRISM,
                selectedWeapon = WeaponId.MORNINGSTAR,
                unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR).toImmutableSet(),
            ),
            labProgress = LabProgress(immutableListOf(2, 1, 0, 0, 0, 0, 0, 0)),
        )

        shell.owner.handleShortcut(AppShortcut.LAB)
        shell.store.setProfile(nextProfile)
        shell.owner.handleLabOutput(LabOutput.Back)
        shell.owner.handleShortcut(AppShortcut.ARMORY)
        shell.owner.handleArmoryOutput(ArmoryOutput.Back)

        assertEquals(listOf(activeConfiguration), shell.gameplay.starts)
        assertTrue(shell.gameplay.appliedPreferences.isEmpty())

        shell.gameplay.phase = GameplayRunPhase.GAME_OVER
        shell.owner.handleGameplayOutput(GameplayInteractionOutput.RestartRun)

        assertEquals(2, shell.gameplay.starts.size)
        assertEquals(CoreShape.PRISM, shell.gameplay.starts.last().profile.loadout.coreShape)
        assertEquals(
            WeaponId.MORNINGSTAR,
            shell.gameplay.starts.last().profile.loadout.selectedWeapon,
        )
        assertEquals(
            nextProfile.labProgress.ranks,
            shell.gameplay.starts.last().profile.labProgress.ranks,
        )
    }

    @Test
    fun codexSnapshotContainsCurrentRunStacksOnlyDuringGameplay() {
        val shell = testShell()
        shell.gameplay.nextItemStacks = immutableListOf(2, 0, 5)

        assertEquals(CodexRunStacks(), shell.owner.currentRunStacks())

        shell.owner.startNewRun()

        assertEquals(
            CodexRunStacks(immutableListOf(2, 0, 5)),
            shell.owner.currentRunStacks(),
        )
    }

    @Test
    fun completedRebirthStartsFreshRunFromUpdatedProfile() {
        val shell = testShell()
        shell.owner.handleShortcut(AppShortcut.REBIRTH)
        val advanced = RebirthProgress(level = 1, highestCleared = 0)
        val nextProfile = shell.store.profile.copy(
            economy = PlayerEconomy(matter = 0L, lifetimeMatter = 1_500L),
            rebirthProgress = advanced,
        )
        shell.store.setProfile(nextProfile)

        shell.owner.handleRebirthOutput(RebirthOutput.CycleAdvanced(advanced))

        assertEquals(listOf(AppDestination.Gameplay), shell.owner.backStack.entries)
        assertEquals(1, shell.gameplay.starts.size)
        assertEquals(1, shell.gameplay.starts.single().profile.rebirthProgress.level)
        assertEquals(0L, shell.gameplay.starts.single().profile.economy.matter)
        assertEquals(1_500L, shell.gameplay.starts.single().profile.economy.lifetimeMatter)
    }

    @Test
    fun workflowParticipantCallsKeepTheirCurrentCausalOrder() {
        val runShell = testShell()

        assertEquals(
            listOf(
                workflowEvent("profile.runBootstrap", AppDestination.Home),
                workflowEvent("gameplay.start", AppDestination.Home),
                workflowEvent("start.returned", AppDestination.Gameplay),
            ),
            runShell.workflow.capture("start.returned", runShell.owner::startNewRun),
        )

        assertEquals(
            listOf(
                workflowEvent("profile.runBootstrap", AppDestination.Gameplay),
                workflowEvent("gameplay.status", AppDestination.Gameplay),
                workflowEvent("gameplay.start", AppDestination.Gameplay),
                workflowEvent("restart.returned", AppDestination.Gameplay),
            ),
            runShell.workflow.capture("restart.returned") {
                runShell.gameplay.phase = GameplayRunPhase.GAME_OVER
                runShell.owner.handleGameplayOutput(GameplayInteractionOutput.RestartRun)
            },
        )

        assertEquals(
            listOf(
                workflowEvent("gameplay.status", AppDestination.Gameplay),
                workflowEvent("gameplay.pause", AppDestination.Gameplay),
                workflowEvent("overlay.returned", AppDestination.Gameplay, AppDestination.Lab),
            ),
            runShell.workflow.capture("overlay.returned") {
                runShell.owner.handleShortcut(AppShortcut.LAB)
            },
        )

        val settingsShell = testShell()
        settingsShell.owner.startNewRun()
        settingsShell.owner.handleShortcut(AppShortcut.SETTINGS)

        assertEquals(
            listOf(
                workflowEvent(
                    "profile.preferences",
                    AppDestination.Gameplay,
                    AppDestination.Settings,
                ),
                workflowEvent(
                    "gameplay.applyPreferences",
                    AppDestination.Gameplay,
                    AppDestination.Settings,
                ),
                workflowEvent(
                    "profile.preferences",
                    AppDestination.Gameplay,
                    AppDestination.Settings,
                ),
                workflowEvent(
                    "audio.updatePreferences",
                    AppDestination.Gameplay,
                    AppDestination.Settings,
                ),
                workflowEvent("settings.returned", AppDestination.Gameplay),
            ),
            settingsShell.workflow.capture("settings.returned") {
                settingsShell.owner.handleSettingsOutput(SettingsOutput.Back)
            },
        )

        val rebirthShell = testShell()
        rebirthShell.owner.handleShortcut(AppShortcut.REBIRTH)
        val advanced = RebirthProgress(level = 1, highestCleared = 0)

        assertEquals(
            listOf(
                workflowEvent("profile.runBootstrap", AppDestination.Home, AppDestination.Rebirth),
                workflowEvent("gameplay.start", AppDestination.Home, AppDestination.Rebirth),
                workflowEvent("rebirth.returned", AppDestination.Gameplay),
            ),
            rebirthShell.workflow.capture("rebirth.returned") {
                rebirthShell.owner.handleRebirthOutput(RebirthOutput.CycleAdvanced(advanced))
            },
        )

        val exitShell = testShell()
        exitShell.owner.startNewRun()

        assertEquals(
            listOf(
                workflowEvent("gameplay.exit", AppDestination.Gameplay),
                workflowEvent("exit.returned", AppDestination.Home),
            ),
            exitShell.workflow.capture("exit.returned") {
                exitShell.owner.handleGameplayOutput(GameplayInteractionOutput.ExitToHome)
            },
        )
    }

    @Test
    fun globalShortcutRoutingCoversEveryOverlayAndBaseAction() {
        val expectedDestinations = listOf(
            AppShortcut.SETTINGS to AppDestination.Settings,
            AppShortcut.LAB to AppDestination.Lab,
            AppShortcut.ARMORY to AppDestination.Armory,
            AppShortcut.REBIRTH to AppDestination.Rebirth,
            AppShortcut.CODEX to AppDestination.Codex,
        )

        expectedDestinations.forEach { (shortcut, destination) ->
            val shell = testShell()
            assertTrue(shell.owner.handleShortcut(shortcut))
            assertEquals(destination, shell.owner.backStack.overlay)
        }

        val homeShell = testShell()
        assertFalse(homeShell.owner.handleShortcut(AppShortcut.BACK))
        assertTrue(homeShell.owner.handleShortcut(AppShortcut.ENTER))
        assertEquals(AppDestination.Gameplay, homeShell.owner.backStack.base)
        assertFalse(homeShell.owner.handleShortcut(AppShortcut.ENTER))

        val overlayShell = testShell()
        overlayShell.owner.handleShortcut(AppShortcut.SETTINGS)
        assertTrue(overlayShell.owner.handleShortcut(AppShortcut.ENTER))
        assertEquals(AppDestination.Home, overlayShell.owner.backStack.active)

        val muteShell = testShell()
        assertTrue(muteShell.owner.handleShortcut(AppShortcut.MUTE))
        assertFalse(muteShell.store.preferences.soundEnabled)
        assertFalse(muteShell.store.preferences.musicEnabled)
        assertTrue(muteShell.gameplay.appliedPreferences.isEmpty())
    }

    @Test
    fun shellOwnsAudioPreferencesShortcutToneAndCloseLifecycle() {
        val initialPreferences = PlayerPreferences(masterVolume = 0.4f)
        val shell = testShell(profile = PlayerProfile(preferences = initialPreferences))

        assertEquals(listOf(initialPreferences.toExpectedAudioPreferences()), shell.audio.preferencesUpdates)

        val updatedPreferences = initialPreferences.copy(masterVolume = 0.25f)
        shell.store.setProfile(shell.store.profile.copy(preferences = updatedPreferences))
        shell.owner.handleShortcut(AppShortcut.MUTE)

        val mutedPreferences = updatedPreferences.copy(soundEnabled = false, musicEnabled = false)
        assertEquals(mutedPreferences.toExpectedAudioPreferences(), shell.audio.preferencesUpdates.last())
        assertEquals(
            0f to listOf(ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE)),
            shell.audio.advances.last(),
        )

        shell.owner.close()
        assertEquals(1, shell.audio.closeCalls)
    }

    @Test
    fun resetConfirmationBlocksNormalRoutesAndCancelDoesNothingUntilExplicitConfirmation() {
        val obsoletePreferences = PlayerPreferences(
            soundEnabled = false,
            musicEnabled = false,
            masterVolume = 0.25f,
        )
        val shell = testShell(
            profile = PlayerProfile(
                preferences = obsoletePreferences,
                economy = PlayerEconomy(matter = 99L),
            ),
        )
        shell.store.requireLegacyReset()

        assertFalse(shell.owner.handleShortcut(AppShortcut.ENTER))
        assertFalse(shell.owner.handleShortcut(AppShortcut.LAB))
        assertTrue(shell.gameplay.starts.isEmpty())
        assertEquals(AppDestination.Home, shell.owner.backStack.active)

        val resetBeforeCancel = shell.store.resetStatus
        shell.owner.handleResetModalOutput(ResetModalOutput.Cancel)

        assertEquals(resetBeforeCancel, shell.store.resetStatus)
        assertTrue(shell.store.acceptedPulses.isEmpty())

        shell.owner.handleResetModalOutput(ResetModalOutput.ConfirmDelete)

        assertEquals(
            listOf<ProfilePulse.Business>(ProfilePulse.ConfirmLegacyReset),
            shell.store.acceptedPulses,
        )
        assertEquals(PlayerProfile(), shell.store.profile)
        assertEquals(
            listOf(
                obsoletePreferences.toExpectedAudioPreferences(),
                PlayerPreferences().toExpectedAudioPreferences(),
            ),
            shell.audio.preferencesUpdates,
        )
        assertTrue(shell.owner.handleShortcut(AppShortcut.ENTER))
        assertEquals(1, shell.gameplay.starts.size)
    }

    @Test
    fun purgeNeedsAttentionAllowsOnlyExplicitRetry() {
        val shell = testShell()
        shell.store.requirePurgeRetry()

        shell.owner.handleResetModalOutput(ResetModalOutput.ConfirmDelete)
        assertTrue(shell.store.acceptedPulses.isEmpty())
        assertFalse(shell.owner.handleShortcut(AppShortcut.ENTER))

        shell.owner.handleResetModalOutput(ResetModalOutput.RetryPurge)

        assertEquals(
            listOf<ProfilePulse.Business>(ProfilePulse.RetryLegacyPurge),
            shell.store.acceptedPulses,
        )
        assertTrue(shell.owner.handleShortcut(AppShortcut.ENTER))
    }

    @Test
    fun unknownBootstrapCannotTriggerResetOrEnterTheApplication() {
        val shell = testShell()
        shell.store.blockBootstrapRead()

        shell.owner.handleResetModalOutput(ResetModalOutput.ConfirmDelete)
        shell.owner.handleResetModalOutput(ResetModalOutput.RetryPurge)

        assertTrue(shell.store.acceptedPulses.isEmpty())
        assertFalse(shell.owner.handleShortcut(AppShortcut.ENTER))
        assertFalse(shell.owner.handleShortcut(AppShortcut.MUTE))
        assertTrue(shell.gameplay.starts.isEmpty())
    }

    @Test
    fun gameplayRejectionBeforeAcceptanceUsesTheRetainedCarrierAndDoesNotNavigate() {
        val shell = testShell()
        shell.gameplay.nextCommandRejection = GameplayRejection.AlreadyStarted

        shell.owner.startNewRun()

        assertEquals(AppDestination.Home, shell.owner.backStack.base)
        assertTrue(shell.gameplay.starts.isEmpty())
        val failure = assertIs<AppGameplayWorkflowFailure.RejectedBeforeAcceptance>(
            shell.owner.gameplayWorkflowFailure,
        )
        assertEquals(RunId(0L), failure.commandRef.targetInstance.runId)
        assertEquals(failure.commandRef.targetInstance, failure.rejection.instanceId)
        assertEquals(GameplayRejection.AlreadyStarted, failure.rejection.reason)
    }

    @Test
    fun startRetriesTheRetainedCreatedRunAfterRejectionBeforeAcceptance() {
        val shell = testShell()
        shell.gameplay.nextCommandRejection = GameplayRejection.AlreadyStarted

        shell.owner.startNewRun()
        shell.owner.startNewRun()

        assertEquals(listOf(RunId(0L)), shell.gameplay.createdRunIds)
        assertEquals(1, shell.gameplay.starts.size)
        assertEquals(AppDestination.Gameplay, shell.owner.backStack.base)
        assertEquals(null, shell.owner.gameplayWorkflowFailure)
    }

    @Test
    fun gameplayAcceptanceMarkerMustRetainTheExactTargetIdentity() {
        val shell = testShell()
        shell.gameplay.nextCommandRejection = GameplayRejection.AlreadyStarted
        shell.gameplay.nextAcceptanceInstance =
            kinetickk.ball.gameplay.api.GameplayInstanceId(RunId(999L))

        assertFailsWith<IllegalStateException> { shell.owner.startNewRun() }

        assertEquals(AppDestination.Home, shell.owner.backStack.base)
        assertTrue(shell.gameplay.starts.isEmpty())
        assertEquals(null, shell.owner.gameplayWorkflowFailure)
    }

    @Test
    fun rejectedProfileProgressKeepsTheGameplayRouteAndRecordsAClosedFailure() {
        val shell = testShell()
        shell.owner.startNewRun()
        shell.gameplay.exitProfileOutcome = GameplayExitProfileOutcome.ProgressRejected(
            observedRevision = ProfileRevision(9L),
            reason = kinetickk.ball.profile.api.ProfileRejection.NoChange,
        )

        shell.owner.handleGameplayOutput(GameplayInteractionOutput.ExitToHome)

        assertEquals(AppDestination.Gameplay, shell.owner.backStack.base)
        assertIs<AppGameplayWorkflowFailure.ExitProgressRejected>(
            shell.owner.gameplayWorkflowFailure,
        )
    }
}

private data class TestShell(
    val owner: AppCompositionOwner,
    val contentCatalog: CountingContentCatalog,
    val store: FakeProfilePort,
    val gameplay: FakeGameplayFeature,
    val audio: FakeAudioService,
    val workflow: WorkflowRecorder,
)

private fun testShell(
    profile: PlayerProfile = PlayerProfile(),
): TestShell {
    val workflow = WorkflowRecorder()
    val store = FakeProfilePort(profile, workflow)
    val audio = FakeAudioService(workflow)
    val gameplay = FakeGameplayFeature(workflow)
    val contentCatalog = CountingContentCatalog()
    val owner = AppCompositionOwner(
        contentCatalog = contentCatalog,
        profilePort = store,
        audioService = audio,
        gameplayFeature = gameplay,
        homeFeature = FakeHomeFeature(),
        settingsFeature = FakeSettingsFeature(),
        labFeature = FakeLabFeature(),
        armoryFeature = FakeArmoryFeature(),
        rebirthFeature = FakeRebirthFeature(),
        codexFeature = FakeCodexFeature(),
    )
    workflow.bind(owner)
    return TestShell(owner, contentCatalog, store, gameplay, audio, workflow)
}

private class CountingContentCatalog(
    delegate: ContentCatalog = createContentCatalog(),
) : ContentCatalog {
    private val profilePolicySnapshot: ProfilePolicySnapshot = delegate.profilePolicy()
    val gameplaySnapshot: GameplayContentSnapshot = delegate.gameplayContent()
    private val uiCatalogSnapshot: UiCatalogSnapshot = delegate.uiCatalog()

    override val version = delegate.version

    var profilePolicyCalls: Int = 0
        private set
    var gameplayContentCalls: Int = 0
        private set
    var uiCatalogCalls: Int = 0
        private set

    override fun profilePolicy(): ProfilePolicySnapshot {
        profilePolicyCalls++
        return profilePolicySnapshot
    }

    override fun gameplayContent(): GameplayContentSnapshot {
        gameplayContentCalls++
        return gameplaySnapshot
    }

    override fun uiCatalog(): UiCatalogSnapshot {
        uiCatalogCalls++
        return uiCatalogSnapshot
    }
}

private data class WorkflowEvent(
    val action: String,
    val backStack: List<AppDestination>,
)

private fun workflowEvent(action: String, vararg destinations: AppDestination): WorkflowEvent =
    WorkflowEvent(action, destinations.toList())

private class WorkflowRecorder {
    private var owner: AppCompositionOwner? = null
    private val events = mutableListOf<WorkflowEvent>()

    fun bind(owner: AppCompositionOwner) {
        this.owner = owner
    }

    fun capture(returnedAction: String, operation: () -> Unit): List<WorkflowEvent> {
        events.clear()
        operation()
        record(returnedAction)
        return events.toList()
    }

    fun record(action: String) {
        events += WorkflowEvent(action, owner?.backStack?.entries?.toList().orEmpty())
    }
}

private class FakeGameplayFeature(
    private val workflow: WorkflowRecorder,
) : GameplayFeature {
    val starts = mutableListOf<RunConfiguration>()
    val appliedPreferences = mutableListOf<PlayerPreferences>()
    val createdRunIds = mutableListOf<RunId>()
    val receivedProfileResults = mutableListOf<kinetickk.ball.profile.api.ProfileCommandResult.Accepted>()
    var pauseCalls = 0
    var nextItemStacks = immutableListOf<Int>()
    var nextCommandRejection: GameplayRejection? = null
    var nextAcceptanceInstance: kinetickk.ball.gameplay.api.GameplayInstanceId? = null
    var exitProfileOutcome: GameplayExitProfileOutcome = GameplayExitProfileOutcome.NoProgress
    private var activeRunValue: FakeGameplayRun? = null

    var phase: GameplayRunPhase
        get() = activeRunValue?.phase ?: GameplayRunPhase.CREATED
        set(value) {
            checkNotNull(activeRunValue).phase = value
        }

    override fun createRun(
        runId: RunId,
        commandResultSink: (GameplayCommandResult.Accepted) -> Unit,
    ): GameplayPort {
        createdRunIds += runId
        return FakeGameplayRun(
            runId = runId,
            workflow = workflow,
            commandResultSink = commandResultSink,
            initialItemStacks = nextItemStacks,
            onStart = starts::add,
            onPreferences = appliedPreferences::add,
            onPause = { pauseCalls += 1 },
            consumeCommandRejection = {
                nextCommandRejection.also { nextCommandRejection = null }
            },
            consumeAcceptanceInstance = {
                nextAcceptanceInstance.also { nextAcceptanceInstance = null }
            },
            exitProfileOutcome = { exitProfileOutcome },
        ).also { activeRunValue = it }
    }

    override fun activeRun(): GameplayPort? = activeRunValue

    override fun receiveProfileCommandResult(
        result: kinetickk.ball.profile.api.ProfileCommandResult.Accepted,
    ) {
        receivedProfileResults += result
        activeRunValue?.receiveProfileCommandResult(result)
    }

    @Composable
    override fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayInteractionOutput) -> Unit,
    ) = Unit
}

private class FakeGameplayRun(
    runId: RunId,
    private val workflow: WorkflowRecorder,
    private val commandResultSink: (GameplayCommandResult.Accepted) -> Unit,
    initialItemStacks: kinetickk.foundation.collections.ImmutableList<Int>,
    private val onStart: (RunConfiguration) -> Unit,
    private val onPreferences: (PlayerPreferences) -> Unit,
    private val onPause: () -> Unit,
    private val consumeCommandRejection: () -> GameplayRejection?,
    private val consumeAcceptanceInstance:
        () -> kinetickk.ball.gameplay.api.GameplayInstanceId?,
    private val exitProfileOutcome: () -> GameplayExitProfileOutcome,
) : GameplayPort {
    override val instanceId = kinetickk.ball.gameplay.api.GameplayInstanceId(runId)
    private var revisionValue = GameplayRevision.ZERO
    var phase: GameplayRunPhase = GameplayRunPhase.CREATED
    private var activeWeaponValue: WeaponId? = null
    private var itemStacksValue = initialItemStacks

    override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
        revisionValue = GameplayRevision(revisionValue.value + 1L)
        if (pulse == GameplayInteractionPulse.PauseToggled) {
            phase = if (phase == GameplayRunPhase.PAUSED) {
                GameplayRunPhase.RUNNING
            } else {
                GameplayRunPhase.PAUSED
            }
        }
        return GameplayAcceptance.Accepted(instanceId, revisionValue)
    }

    override fun accept(
        command: GameplayCommand,
        admission: GameplayCommandAdmission,
    ): GameplayAcceptance {
        check(command.ref.targetInstance == instanceId)
        check(admission.commandRef == command.ref)
        val acceptanceInstance = consumeAcceptanceInstance() ?: instanceId
        consumeCommandRejection()?.let { rejection ->
            return GameplayAcceptance.Rejected(acceptanceInstance, revisionValue, rejection)
        }
        revisionValue = GameplayRevision(revisionValue.value + 1L)
        val outcome = when (val pulse = command.pulse) {
            is kinetickk.ball.gameplay.api.GameplaySessionPulse.StartRun -> {
                workflow.record("gameplay.start")
                onStart(pulse.configuration)
                activeWeaponValue = pulse.configuration.profile.loadout.selectedWeapon
                phase = GameplayRunPhase.RUNNING
                GameplayCommandOutcome.RunStarted
            }
            kinetickk.ball.gameplay.api.GameplaySessionPulse.PauseForOverlay -> {
                workflow.record("gameplay.pause")
                onPause()
                phase = GameplayRunPhase.PAUSED
                GameplayCommandOutcome.OverlayPaused
            }
            is kinetickk.ball.gameplay.api.GameplaySessionPulse.ApplyPreferences -> {
                workflow.record("gameplay.applyPreferences")
                onPreferences(pulse.preferences)
                GameplayCommandOutcome.PreferencesApplied(pulse.preferences)
            }
            kinetickk.ball.gameplay.api.GameplaySessionPulse.ExitRun -> {
                workflow.record("gameplay.exit")
                phase = GameplayRunPhase.EXITED
                GameplayCommandOutcome.RunExited(exitProfileOutcome())
            }
        }
        commandResultSink(
            GameplayCommandResult.Accepted(
                commandRef = command.ref,
                targetRevision = revisionValue,
                outcome = outcome,
            ),
        )
        return GameplayAcceptance.Accepted(acceptanceInstance, revisionValue)
    }

    override fun query(query: GameplayQuery.GetRender): GameplayRenderProjection =
        GameplayRenderProjection(instanceId, revisionValue, renderModel = null)

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection {
        workflow.record("gameplay.status")
        return GameplayRunStatusProjection(
            instanceId = instanceId,
            revision = revisionValue,
            phase = phase,
            profileCommandPending = false,
        )
    }

    override fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayActiveWeaponProjection(instanceId, revisionValue, activeWeaponValue)

    override fun query(query: GameplayQuery.GetCodexStacks): GameplayCodexStacksProjection =
        GameplayCodexStacksProjection(instanceId, revisionValue, itemStacksValue)

    fun receiveProfileCommandResult(
        result: kinetickk.ball.profile.api.ProfileCommandResult.Accepted,
    ) = Unit
}

private class FakeProfilePort(
    initialProfile: PlayerProfile,
    private val workflow: WorkflowRecorder,
) : ProfilePort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    var profile: PlayerProfile = initialProfile
        private set
    val preferences: PlayerPreferences
        get() = profile.preferences
    private var revision = ProfileRevision(1L)
    var bootstrapStatus: ProfileBootstrapStatus = ProfileBootstrapStatus.Ready
    var resetStatus: ProfileResetStatus = ProfileResetStatus.NotRequired(
        legacyResetConfirmed = false,
    )
    var persistenceStatus: ProfilePersistenceStatus = ProfilePersistenceStatus.NotAttempted
    val acceptedPulses = mutableListOf<ProfilePulse.Business>()

    fun setProfile(value: PlayerProfile) {
        profile = value
        revision = ProfileRevision(revision.value + 1L)
    }

    fun requireLegacyReset() {
        val reason = ProfileResetReason.LegacyDataDetected
        bootstrapStatus = ProfileBootstrapStatus.Blocked(
            ProfileBootstrapBlockReason.ResetRequired(reason),
        )
        resetStatus = ProfileResetStatus.ConfirmationRequired(reason, ProfileLegacyKeys.ALL)
    }

    fun requirePurgeRetry() {
        val result = ProfileLegacyPurgeResult.Partial(ProfileLegacyKeys.ALL)
        bootstrapStatus = ProfileBootstrapStatus.Blocked(
            ProfileBootstrapBlockReason.ResetNeedsAttention(result),
        )
        resetStatus = ProfileResetStatus.NeedsAttention(ProfileLegacyKeys.ALL, result)
    }

    fun blockBootstrapRead() {
        bootstrapStatus = ProfileBootstrapStatus.Blocked(
            ProfileBootstrapBlockReason.ResourceOutcomeUnknown(
                ProfileResourceFailure.PROVIDER_READ_FAILED,
            ),
        )
        resetStatus = ProfileResetStatus.NotRequired(legacyResetConfirmed = false)
    }

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        acceptedPulses += pulse
        if (
            bootstrapStatus != ProfileBootstrapStatus.Ready &&
            pulse != ProfilePulse.ConfirmLegacyReset &&
            pulse != ProfilePulse.RetryLegacyPurge
        ) {
            return ProfileAcceptance.Rejected(instanceId, revision, kinetickk.ball.profile.api.ProfileRejection.ResetRequired)
        }
        return when (pulse) {
            ProfilePulse.ToggleMute -> {
                val enable = !profile.preferences.soundEnabled && !profile.preferences.musicEnabled
                profile = profile.copy(
                    preferences = profile.preferences.copy(
                        soundEnabled = enable,
                        musicEnabled = enable,
                    ),
                )
                accepted()
            }
            ProfilePulse.ConfirmLegacyReset -> {
                profile = PlayerProfile()
                resetStatus = ProfileResetStatus.NotRequired(legacyResetConfirmed = true)
                bootstrapStatus = ProfileBootstrapStatus.Ready
                accepted()
            }
            ProfilePulse.RetryLegacyPurge -> {
                resetStatus = ProfileResetStatus.NotRequired(legacyResetConfirmed = true)
                bootstrapStatus = ProfileBootstrapStatus.Ready
                accepted()
            }
            is ProfilePulse.ApplyGameplayProgress -> {
                workflow.record("profile.applyGameplayProgress")
                rejected()
            }
            is ProfilePulse.AdjustPreference,
            ProfilePulse.AdvanceRebirth,
            is ProfilePulse.PurchaseMetaUpgrade,
            is ProfilePulse.PurchaseOrEquipWeapon,
            is ProfilePulse.SelectCoreShape,
            -> rejected()
        }
    }

    override fun accept(
        command: ProfileCommand,
        admission: ProfileCommandAdmission,
    ): ProfileAcceptance = error("not used")

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection {
        workflow.record("profile.runBootstrap")
        val result = if (
            bootstrapStatus == ProfileBootstrapStatus.Ready &&
            resetStatus is ProfileResetStatus.NotRequired
        ) {
            ProfileRunBootstrapResult.Ready(profile.toGameplaySnapshot())
        } else {
            ProfileRunBootstrapResult.Unavailable(bootstrapStatus)
        }
        return RunBootstrapProjection(instanceId, revision, result)
    }

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection {
        workflow.record("profile.preferences")
        return PreferencesProjection(instanceId, revision, profile.preferences)
    }

    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection =
        HomeProgressProjection(
            instanceId = instanceId,
            revision = revision,
            economy = profile.economy,
            loadout = profile.loadout,
            collection = profile.collection,
            rebirthProgress = profile.rebirthProgress,
            canAdvanceRebirth = profile.rebirthProgress.highestCleared >= profile.rebirthProgress.level,
        )

    override fun query(query: ProfileQuery.GetLabProgress): LabProgressProjection =
        LabProgressProjection(
            instanceId,
            revision,
            LabProfileSnapshot(profile.economy, profile.labProgress),
        )

    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection =
        LoadoutProjection(
            instanceId,
            revision,
            LoadoutProfileSnapshot(profile.economy, profile.loadout),
        )

    override fun query(query: ProfileQuery.GetCollection): CollectionProjection =
        CollectionProjection(instanceId, revision, profile.collection)

    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection =
        RebirthProgressProjection(
            instanceId,
            revision,
            RebirthProfileSnapshot(profile.rebirthProgress),
            canAdvance = profile.rebirthProgress.highestCleared >= profile.rebirthProgress.level,
        )

    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection =
        PersistenceStatusProjection(
            instanceId,
            revision,
            bootstrapStatus,
            resetStatus,
            persistenceStatus,
        )

    private fun accepted(): ProfileAcceptance.Accepted {
        revision = ProfileRevision(revision.value + 1L)
        return ProfileAcceptance.Accepted(instanceId, revision)
    }

    private fun rejected(): ProfileAcceptance.Rejected = ProfileAcceptance.Rejected(
        instanceId,
        revision,
        kinetickk.ball.profile.api.ProfileRejection.NoChange,
    )
}

private fun PlayerProfile.toGameplaySnapshot() = kinetickk.ball.profile.api.GameplayProfileSnapshot(
    preferences = preferences,
    economy = economy,
    loadout = loadout,
    labProgress = labProgress,
    collection = collection,
    rebirthProgress = rebirthProgress,
)

private class FakeAudioService(
    private val workflow: WorkflowRecorder,
) : AudioService {
    val preferencesUpdates = mutableListOf<AudioPreferences>()
    val advances = mutableListOf<Pair<Float, List<ToneRequest>>>()
    var unlockCalls = 0
    var closeCalls = 0

    override fun updatePreferences(preferences: AudioPreferences) {
        workflow.record("audio.updatePreferences")
        preferencesUpdates += preferences
    }

    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) {
        advances += realDeltaSeconds to requests.toList()
    }

    override fun ensureUnlocked() {
        unlockCalls++
    }

    override fun close() {
        closeCalls++
    }
}

private fun PlayerPreferences.toExpectedAudioPreferences(): AudioPreferences = AudioPreferences(
    soundEnabled = soundEnabled,
    musicEnabled = musicEnabled,
    masterVolume = masterVolume,
)

private class FakeHomeFeature : HomeFeature {
    @Composable
    override fun Content(inputEnabled: Boolean, onOutput: (HomeOutput) -> Unit) = Unit
}

private class FakeSettingsFeature : SettingsFeature {
    @Composable
    override fun Content(routeToken: Int, onOutput: (SettingsOutput) -> Unit) = Unit
}

private class FakeLabFeature : LabFeature {
    @Composable
    override fun Content(routeToken: Int, onOutput: (LabOutput) -> Unit) = Unit
}

private class FakeArmoryFeature : ArmoryFeature {
    @Composable
    override fun Content(activeRunWeapon: WeaponId?, onOutput: (ArmoryOutput) -> Unit) = Unit
}

private class FakeRebirthFeature : RebirthFeature {
    @Composable
    override fun Content(
        routeToken: Int,
        eligible: Boolean,
        onOutput: (RebirthOutput) -> Unit,
    ) = Unit
}

private class FakeCodexFeature : CodexFeature {
    @Composable
    override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) = Unit
}
