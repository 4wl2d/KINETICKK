// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.audio.api.AudioPreferences
import kinetickk.core.audio.api.AudioService
import kinetickk.core.collections.immutableListOf
import kinetickk.core.collections.toImmutableSet
import kinetickk.core.content.CoreShape
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.GameplayProgressUpdate
import kinetickk.core.profile.api.LabProfileSnapshot
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.LoadoutProfileSnapshot
import kinetickk.core.profile.api.PlayerCollection
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.core.profile.api.PlayerLoadout
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.core.profile.api.PlayerProfile
import kinetickk.core.profile.api.ProfileLoadResult
import kinetickk.core.profile.api.ProfileMutationRejection
import kinetickk.core.profile.api.ProfileMutationResult
import kinetickk.core.profile.api.ProfilePersistResult
import kinetickk.core.profile.api.ProfileProviderId
import kinetickk.core.profile.api.ProfileStore
import kinetickk.core.profile.api.RebirthProfileSnapshot
import kinetickk.core.profile.api.RebirthProgress
import kinetickk.feature.armory.api.ArmoryFeature
import kinetickk.feature.armory.api.ArmoryOutput
import kinetickk.feature.codex.api.CodexFeature
import kinetickk.feature.codex.api.CodexOutput
import kinetickk.feature.codex.api.CodexRunStacks
import kinetickk.feature.gameplay.api.GameplayFeature
import kinetickk.feature.gameplay.api.GameplayOutput
import kinetickk.feature.gameplay.api.GameplayUiModel
import kinetickk.feature.gameplay.api.GameplayUiPhase
import kinetickk.feature.gameplay.api.RunConfiguration
import kinetickk.feature.home.api.HomeFeature
import kinetickk.feature.home.api.HomeOutput
import kinetickk.feature.lab.api.LabFeature
import kinetickk.feature.lab.api.LabOutput
import kinetickk.feature.rebirth.api.RebirthFeature
import kinetickk.feature.rebirth.api.RebirthOutput
import kinetickk.feature.settings.api.SettingsFeature
import kinetickk.feature.settings.api.SettingsOutput
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
    fun shellOwnsAudioPreferencesCuesUnlockAndCloseLifecycle() {
        val initialPreferences = PlayerPreferences(masterVolume = 0.4f)
        val shell = testShell(profile = PlayerProfile(preferences = initialPreferences))

        assertEquals(listOf(initialPreferences.toExpectedAudioPreferences()), shell.audio.preferencesUpdates)

        shell.owner.handleGameplayOutput(GameplayOutput.UserGestureObserved)
        shell.owner.handleGameplayOutput(
            GameplayOutput.AudioFrame(
                realDeltaSeconds = 0.016f,
                cues = immutableListOf(AudioCue.DASH),
            ),
        )
        assertEquals(1, shell.audio.unlockCalls)
        assertEquals(0.016f to listOf(AudioCue.DASH), shell.audio.advances.last())

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
)

private fun testShell(
    profile: PlayerProfile = PlayerProfile(),
    gameplay: FakeGameplayFeature = FakeGameplayFeature(),
): TestShell {
    val store = FakeProfileStore(profile)
    val audio = FakeAudioService()
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
    return TestShell(owner, store, gameplay, audio)
}

private class FakeGameplayFeature : GameplayFeature {
    var model: GameplayUiModel = GameplayUiModel()
    val starts = mutableListOf<RunConfiguration>()
    val appliedPreferences = mutableListOf<PlayerPreferences>()
    var pauseCalls = 0
    var togglePauseCalls = 0

    override fun start(configuration: RunConfiguration) {
        starts += configuration
        model = model.copy(
            phase = GameplayUiPhase.RUNNING,
            activeWeapon = configuration.startingWeapon,
        )
    }

    override fun applyPreferences(preferences: PlayerPreferences) {
        appliedPreferences += preferences
    }

    override fun pauseForOverlay(): Boolean {
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

    override fun uiModel(): GameplayUiModel = model

    @Composable
    override fun Content(inputEnabled: Boolean, onOutput: (GameplayOutput) -> Unit) = Unit
}

private class FakeProfileStore(initialProfile: PlayerProfile) : ProfileStore {
    private var profile = initialProfile

    override val providerId = ProfileProviderId.PLATFORM_LOCAL
    override val bootstrapResult: ProfileLoadResult = ProfileLoadResult.Loaded(initialProfile)

    fun setProfile(value: PlayerProfile) {
        profile = value
    }

    override fun profileSnapshot(): PlayerProfile = profile

    override fun preferences(): PlayerPreferences = profile.preferences

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

    override fun applyGameplayProgress(update: GameplayProgressUpdate): ProfileMutationResult = rejected()

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

private class FakeAudioService : AudioService {
    val preferencesUpdates = mutableListOf<AudioPreferences>()
    val advances = mutableListOf<Pair<Float, List<AudioCue>>>()
    var unlockCalls = 0
    var closeCalls = 0

    override fun updatePreferences(preferences: AudioPreferences) {
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
