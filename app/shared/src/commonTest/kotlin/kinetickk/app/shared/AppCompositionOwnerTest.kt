// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import kinetickk.resource.audio.api.AudioCue
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileLoadResult
import kinetickk.ball.profile.api.ProfileMutationRejection
import kinetickk.ball.profile.api.ProfileMutationResult
import kinetickk.ball.profile.api.ProfilePersistResult
import kinetickk.ball.profile.api.ProfileProviderId
import kinetickk.ball.profile.api.ProfileStore
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.api.CodexOutput
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.gameplay.api.GameplayOutput
import kinetickk.ball.gameplay.api.GameplayUiModel
import kinetickk.ball.gameplay.api.GameplayUiPhase
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.nucleus.AppDestination
import kinetickk.ball.profile.interaction.lab.api.LabFeature
import kinetickk.ball.profile.interaction.lab.api.LabOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppCompositionOwnerTest {
    @Test
    fun openingOverlayPausesRunAndBackKeepsSameSessionPaused() {
        val shell = testShell()
        shell.owner.startNewRun()

        assertTrue(shell.owner.handleShortcut(AppShortcut.LAB))
        assertEquals(1, shell.gameplay.pauseCalls)
        assertEquals(GameplayUiPhase.PAUSED, shell.gameplay.uiModel().phase)
        assertEquals(
            listOf(AppDestination.Gameplay, AppDestination.Lab),
            shell.owner.backStack.entries,
        )

        assertTrue(shell.owner.handleShortcut(AppShortcut.BACK))
        assertEquals(listOf(AppDestination.Gameplay), shell.owner.backStack.entries)
        assertEquals(GameplayUiPhase.PAUSED, shell.gameplay.uiModel().phase)
        assertEquals(1, shell.gameplay.starts.size)
        assertEquals(0, shell.gameplay.togglePauseCalls)
    }

    @Test
    fun settingsPreferencesReachActiveRunOnlyWhenSettingsCloses() {
        val shell = testShell()
        shell.owner.startNewRun()
        shell.owner.handleShortcut(AppShortcut.SETTINGS)
        val changed = shell.store.profileSnapshot().preferences.copy(
            simulationSpeed = 1.75f,
            textScale = 1.5f,
            soundEnabled = false,
        )
        shell.store.setProfile(shell.store.profileSnapshot().copy(preferences = changed))

        shell.owner.handleSettingsOutput(SettingsOutput.Cue(AudioCue.UI_CLICK))
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
        val changed = shell.store.preferences().copy(textScale = 1.75f)
        shell.store.setProfile(shell.store.profileSnapshot().copy(preferences = changed))

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
        val nextProfile = shell.store.profileSnapshot().copy(
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

        shell.owner.handleGameplayOutput(GameplayOutput.RestartRun)

        assertEquals(2, shell.gameplay.starts.size)
        assertEquals(CoreShape.PRISM, shell.gameplay.starts.last().coreShape)
        assertEquals(WeaponId.MORNINGSTAR, shell.gameplay.starts.last().startingWeapon)
        assertEquals(nextProfile.labProgress.ranks, shell.gameplay.starts.last().metaRanks)
    }

    @Test
    fun codexSnapshotContainsCurrentRunStacksOnlyDuringGameplay() {
        val shell = testShell()
        shell.gameplay.model = GameplayUiModel(
            phase = GameplayUiPhase.RUNNING,
            activeWeapon = WeaponId.FLUX_WAKE,
            itemStacks = immutableListOf(2, 0, 5),
        )

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
        val nextProfile = shell.store.profileSnapshot().copy(
            economy = PlayerEconomy(matter = 0L, lifetimeMatter = 1_500L),
            rebirthProgress = advanced,
        )
        shell.store.setProfile(nextProfile)

        shell.owner.handleRebirthOutput(RebirthOutput.CycleAdvanced(advanced))

        assertEquals(listOf(AppDestination.Gameplay), shell.owner.backStack.entries)
        assertEquals(1, shell.gameplay.starts.size)
        assertEquals(1, shell.gameplay.starts.single().rebirthLevel)
        assertEquals(0L, shell.gameplay.starts.single().matterAtStart)
        assertEquals(1_500L, shell.gameplay.starts.single().lifetimeMatterAtStart)
    }

    @Test
    fun workflowParticipantCallsKeepTheirCurrentCausalOrder() {
        val runShell = testShell()

        assertEquals(
            listOf(
                workflowEvent("profile.snapshot", AppDestination.Home),
                workflowEvent("gameplay.start", AppDestination.Home),
                workflowEvent("start.returned", AppDestination.Gameplay),
            ),
            runShell.workflow.capture("start.returned", runShell.owner::startNewRun),
        )

        assertEquals(
            listOf(
                workflowEvent("profile.snapshot", AppDestination.Gameplay),
                workflowEvent("gameplay.start", AppDestination.Gameplay),
                workflowEvent("restart.returned", AppDestination.Gameplay),
            ),
            runShell.workflow.capture("restart.returned") {
                runShell.owner.handleGameplayOutput(GameplayOutput.RestartRun)
            },
        )

        assertEquals(
            listOf(
                workflowEvent("gameplay.uiModel", AppDestination.Gameplay),
                workflowEvent("gameplay.pause", AppDestination.Gameplay, AppDestination.Lab),
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
                workflowEvent("profile.preferences", AppDestination.Gameplay),
                workflowEvent("gameplay.applyPreferences", AppDestination.Gameplay),
                workflowEvent("profile.preferences", AppDestination.Gameplay),
                workflowEvent("audio.updatePreferences", AppDestination.Gameplay),
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
                workflowEvent("profile.snapshot", AppDestination.Home, AppDestination.Rebirth),
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
            listOf(workflowEvent("exit.returned", AppDestination.Home)),
            exitShell.workflow.capture("exit.returned") {
                exitShell.owner.handleGameplayOutput(GameplayOutput.ExitToHome)
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
        assertFalse(muteShell.store.preferences().soundEnabled)
        assertFalse(muteShell.store.preferences().musicEnabled)
        assertEquals(
            listOf(muteShell.store.preferences()),
            muteShell.gameplay.appliedPreferences,
        )
    }

    @Test
    fun shellOwnsAudioPreferencesUiCuesAndCloseLifecycle() {
        val initialPreferences = PlayerPreferences(masterVolume = 0.4f)
        val shell = testShell(profile = PlayerProfile(preferences = initialPreferences))

        assertEquals(listOf(initialPreferences.toExpectedAudioPreferences()), shell.audio.preferencesUpdates)

        val updatedPreferences = initialPreferences.copy(soundEnabled = false, masterVolume = 0.25f)
        shell.store.setProfile(shell.store.profileSnapshot().copy(preferences = updatedPreferences))
        shell.owner.handleSettingsOutput(SettingsOutput.Cue(AudioCue.UI_CLICK))

        assertEquals(updatedPreferences.toExpectedAudioPreferences(), shell.audio.preferencesUpdates.last())
        assertEquals(0f to listOf(AudioCue.UI_CLICK), shell.audio.advances.last())

        shell.owner.close()
        assertEquals(1, shell.audio.closeCalls)
    }
}

private data class TestShell(
    val owner: AppCompositionOwner,
    val store: FakeProfileStore,
    val gameplay: FakeGameplayFeature,
    val audio: FakeAudioService,
    val workflow: WorkflowRecorder,
)

private fun testShell(
    profile: PlayerProfile = PlayerProfile(),
): TestShell {
    val workflow = WorkflowRecorder()
    val store = FakeProfileStore(profile, workflow)
    val audio = FakeAudioService(workflow)
    val gameplay = FakeGameplayFeature(workflow)
    val owner = AppCompositionOwner(
        profileStore = store,
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
    return TestShell(owner, store, gameplay, audio, workflow)
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
    var model: GameplayUiModel = GameplayUiModel()
    val starts = mutableListOf<RunConfiguration>()
    val appliedPreferences = mutableListOf<PlayerPreferences>()
    var pauseCalls = 0
    var togglePauseCalls = 0

    override fun start(configuration: RunConfiguration) {
        workflow.record("gameplay.start")
        starts += configuration
        model = model.copy(
            phase = GameplayUiPhase.RUNNING,
            activeWeapon = configuration.startingWeapon,
        )
    }

    override fun applyPreferences(preferences: PlayerPreferences) {
        workflow.record("gameplay.applyPreferences")
        appliedPreferences += preferences
    }

    override fun pauseForOverlay(): Boolean {
        workflow.record("gameplay.pause")
        pauseCalls += 1
        if (model.phase != GameplayUiPhase.RUNNING) return false
        model = model.copy(phase = GameplayUiPhase.PAUSED)
        return true
    }

    override fun togglePause() {
        togglePauseCalls += 1
        model = model.copy(
            phase = if (model.phase == GameplayUiPhase.PAUSED) {
                GameplayUiPhase.RUNNING
            } else {
                GameplayUiPhase.PAUSED
            },
        )
    }

    override fun uiModel(): GameplayUiModel {
        workflow.record("gameplay.uiModel")
        return model
    }

    @Composable
    override fun Content(inputEnabled: Boolean, onOutput: (GameplayOutput) -> Unit) = Unit
}

private class FakeProfileStore(
    initialProfile: PlayerProfile,
    private val workflow: WorkflowRecorder,
) : ProfileStore {
    private var profile = initialProfile

    override val providerId = ProfileProviderId.PLATFORM_LOCAL
    override val bootstrapResult: ProfileLoadResult = ProfileLoadResult.Loaded(initialProfile)

    fun setProfile(value: PlayerProfile) {
        profile = value
    }

    override fun profileSnapshot(): PlayerProfile {
        workflow.record("profile.snapshot")
        return profile
    }

    override fun preferences(): PlayerPreferences {
        workflow.record("profile.preferences")
        return profile.preferences
    }

    override fun updatePreferences(preferences: PlayerPreferences): ProfileMutationResult =
        applied(profile.copy(preferences = preferences))

    override fun labSnapshot(): LabProfileSnapshot = LabProfileSnapshot(
        economy = profile.economy,
        progress = profile.labProgress,
    )

    override fun purchaseMetaUpgrade(id: MetaUpgradeId): ProfileMutationResult = rejected()

    override fun loadoutSnapshot(): LoadoutProfileSnapshot = LoadoutProfileSnapshot(
        economy = profile.economy,
        loadout = profile.loadout,
    )

    override fun selectCoreShape(shape: CoreShape): ProfileMutationResult = rejected()

    override fun purchaseOrEquipWeapon(id: WeaponId): ProfileMutationResult = rejected()

    override fun collectionSnapshot(): PlayerCollection = profile.collection

    override fun rebirthSnapshot(): RebirthProfileSnapshot =
        RebirthProfileSnapshot(profile.rebirthProgress)

    override fun advanceRebirth(): ProfileMutationResult = rejected()

    override fun applyGameplayProgress(update: GameplayProgressUpdate): ProfileMutationResult {
        workflow.record("profile.applyGameplayProgress")
        return rejected()
    }

    override fun replaceProfile(profile: PlayerProfile): ProfilePersistResult {
        this.profile = profile
        return ProfilePersistResult.Persisted
    }

    private fun applied(value: PlayerProfile): ProfileMutationResult {
        profile = value
        return ProfileMutationResult.Applied(ProfilePersistResult.Persisted)
    }

    private fun rejected(): ProfileMutationResult =
        ProfileMutationResult.Rejected(ProfileMutationRejection.NO_CHANGE)
}

private class FakeAudioService(
    private val workflow: WorkflowRecorder,
) : AudioService {
    val preferencesUpdates = mutableListOf<AudioPreferences>()
    val advances = mutableListOf<Pair<Float, List<AudioCue>>>()
    var unlockCalls = 0
    var closeCalls = 0

    override fun updatePreferences(preferences: AudioPreferences) {
        workflow.record("audio.updatePreferences")
        preferencesUpdates += preferences
    }

    override fun advance(realDeltaSeconds: Float, cues: List<AudioCue>) {
        advances += realDeltaSeconds to cues.toList()
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
