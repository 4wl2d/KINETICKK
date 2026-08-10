// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kinetickk.resource.audio.api.AudioCue
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.impl.DefaultAudioService
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileMutationResult
import kinetickk.ball.profile.api.ProfileStore
import kinetickk.ball.profile.impl.createPlatformProfileStore
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.armory.impl.DefaultArmoryFeature
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.api.CodexOutput
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.codex.impl.DefaultCodexFeature
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.gameplay.api.GameplayOutput
import kinetickk.ball.gameplay.api.GameplayUiPhase
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.home.impl.DefaultHomeFeature
import kinetickk.flow.session.nucleus.AppBackStack
import kinetickk.flow.session.nucleus.AppDestination
import kinetickk.flow.session.nucleus.AppGameplayPhase
import kinetickk.flow.session.nucleus.AppNavigator
import kinetickk.ball.profile.interaction.lab.api.LabFeature
import kinetickk.ball.profile.interaction.lab.api.LabOutput
import kinetickk.ball.profile.interaction.lab.impl.DefaultLabFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.rebirth.impl.DefaultRebirthFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.interaction.settings.impl.DefaultSettingsFeature

/** The single UI entry point used by Desktop and Web hosts. */
@Composable
fun KinetickkApp() {
    val ownerValue = remember { AppCompositionOwner() }
    DisposableEffect(ownerValue) {
        onDispose(ownerValue::close)
    }
    ownerValue.Content()
}

internal class AppCompositionOwner(
    private val profileStore: ProfileStore = createPlatformProfileStore(),
    private val audioService: AudioService = DefaultAudioService(),
    private val gameplayFeature: GameplayFeature = DefaultGameplayFeature(profileStore, audioService),
    private val homeFeature: HomeFeature = DefaultHomeFeature(
        loadoutCapability = profileStore,
        collectionCapability = profileStore,
        rebirthCapability = profileStore,
        preferencesReader = profileStore,
    ),
    private val settingsFeature: SettingsFeature = DefaultSettingsFeature(profileStore),
    private val labFeature: LabFeature = DefaultLabFeature(profileStore, profileStore),
    private val armoryFeature: ArmoryFeature = DefaultArmoryFeature(profileStore, profileStore),
    private val rebirthFeature: RebirthFeature = DefaultRebirthFeature(profileStore, profileStore),
    private val codexFeature: CodexFeature = DefaultCodexFeature(profileStore, profileStore),
) {
    private val navigator = AppNavigator()

    val backStack: AppBackStack
        get() = navigator.backStack

    init {
        syncAudioPreferences()
    }

    @Composable
    fun Content() {
        val focusRequester = remember(this) { FocusRequester() }
        var backStackValue by remember(this) { mutableStateOf(backStack) }

        fun refreshShell() {
            backStackValue = backStack
        }

        LaunchedEffect(this) {
            focusRequester.requestFocus()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    audioService.ensureUnlocked()
                    val shortcut = event.key.toAppShortcut() ?: return@onPreviewKeyEvent false
                    handleShortcut(shortcut).also { handled ->
                        if (handled) refreshShell()
                    }
                }
                .focusable(),
        ) {
            when (backStackValue.base) {
                AppDestination.Home -> homeFeature.Content(
                    inputEnabled = backStackValue.overlay == null,
                    onOutput = { output ->
                        handleHomeOutput(output)
                        refreshShell()
                    },
                )
                AppDestination.Gameplay -> gameplayFeature.Content(
                    inputEnabled = backStackValue.overlay == null,
                    onOutput = { output ->
                        handleGameplayOutput(output)
                        refreshShell()
                    },
                )
                else -> error("Only Home and Gameplay may be base destinations")
            }

            when (backStackValue.overlay) {
                null -> Unit
                AppDestination.Settings -> settingsFeature.Content(
                    routeToken = backStackValue.routeToken,
                    onOutput = { output ->
                        handleSettingsOutput(output)
                        refreshShell()
                    },
                )
                AppDestination.Lab -> labFeature.Content(
                    routeToken = backStackValue.routeToken,
                    onOutput = { output ->
                        handleLabOutput(output)
                        refreshShell()
                    },
                )
                AppDestination.Armory -> armoryFeature.Content(
                    activeRunWeapon = activeGameplayWeapon(),
                    onOutput = { output ->
                        handleArmoryOutput(output)
                        refreshShell()
                    },
                )
                AppDestination.Rebirth -> rebirthFeature.Content(
                    routeToken = backStackValue.routeToken,
                    eligible = isRebirthRouteEligible(),
                    onOutput = { output ->
                        handleRebirthOutput(output)
                        refreshShell()
                    },
                )
                AppDestination.Codex -> codexFeature.Content(
                    runStacks = currentRunStacks(),
                    onOutput = { output ->
                        handleCodexOutput(output)
                        refreshShell()
                    },
                )
                AppDestination.Home,
                AppDestination.Gameplay,
                -> error("Base destinations cannot be overlays")
            }
        }
    }

    fun close() {
        audioService.close()
    }

    internal fun handleShortcut(shortcut: AppShortcut): Boolean = when (shortcut) {
        AppShortcut.SETTINGS -> openOverlay(AppDestination.Settings)
        AppShortcut.LAB -> openOverlay(AppDestination.Lab)
        AppShortcut.ARMORY -> openOverlay(AppDestination.Armory)
        AppShortcut.REBIRTH -> openOverlay(AppDestination.Rebirth)
        AppShortcut.CODEX -> openOverlay(AppDestination.Codex)
        AppShortcut.MUTE -> {
            toggleMute()
            true
        }
        AppShortcut.BACK -> if (backStack.overlay != null) {
            closeOverlay()
            true
        } else {
            false
        }
        AppShortcut.ENTER -> when {
            backStack.overlay != null -> {
                closeOverlay()
                true
            }
            backStack.base == AppDestination.Home -> {
                startNewRun()
                true
            }
            else -> false
        }
    }

    internal fun handleHomeOutput(output: HomeOutput) {
        when (output) {
            HomeOutput.StartRun -> startNewRun()
            HomeOutput.OpenSettings -> openOverlay(AppDestination.Settings)
            HomeOutput.OpenLab -> openOverlay(AppDestination.Lab)
            HomeOutput.OpenArmory -> openOverlay(AppDestination.Armory)
            HomeOutput.OpenRebirth -> openOverlay(AppDestination.Rebirth)
            HomeOutput.OpenCodex -> openOverlay(AppDestination.Codex)
            is HomeOutput.Cue -> playCue(output.cue)
        }
    }

    internal fun handleGameplayOutput(output: GameplayOutput) {
        when (output) {
            GameplayOutput.OpenSettings -> openOverlay(AppDestination.Settings)
            GameplayOutput.OpenRebirth -> openOverlay(AppDestination.Rebirth)
            GameplayOutput.ExitToHome -> navigator.showHome()
            GameplayOutput.RestartRun -> startNewRun()
        }
    }

    internal fun handleSettingsOutput(output: SettingsOutput) {
        when (output) {
            SettingsOutput.Back -> closeOverlay()
            is SettingsOutput.Cue -> {
                syncAudioPreferences()
                playCue(output.cue)
            }
        }
    }

    internal fun handleLabOutput(output: LabOutput) {
        when (output) {
            LabOutput.Back -> closeOverlay()
            is LabOutput.Cue -> playCue(output.cue)
        }
    }

    internal fun handleArmoryOutput(output: ArmoryOutput) {
        when (output) {
            ArmoryOutput.Back -> closeOverlay()
            is ArmoryOutput.Cue -> playCue(output.cue)
        }
    }

    internal fun handleRebirthOutput(output: RebirthOutput) {
        when (output) {
            RebirthOutput.Back -> closeOverlay()
            is RebirthOutput.Cue -> playCue(output.cue)
            is RebirthOutput.CycleAdvanced -> startNewRun()
        }
    }

    internal fun handleCodexOutput(output: CodexOutput) {
        when (output) {
            CodexOutput.Back -> closeOverlay()
            is CodexOutput.Cue -> playCue(output.cue)
        }
    }

    internal fun openOverlay(destination: AppDestination): Boolean {
        val before = backStack
        val transition = navigator.openOverlay(destination, gameplayPhase())
        val changed = transition.backStack != before
        if (changed && before.overlay == AppDestination.Settings && destination != AppDestination.Settings) {
            applyPersistedSettings()
        }
        if (transition.pauseGameplay) gameplayFeature.pauseForOverlay()
        return changed
    }

    internal fun closeOverlay() {
        val closing = backStack.overlay
        navigator.back()
        if (closing == AppDestination.Settings) {
            applyPersistedSettings()
        }
    }

    internal fun startNewRun() {
        gameplayFeature.start(profileStore.profileSnapshot().toRunConfiguration())
        navigator.showGameplay()
    }

    private fun gameplayPhase(): AppGameplayPhase = when (gameplayFeature.uiModel().phase) {
        GameplayUiPhase.IDLE -> AppGameplayPhase.IDLE
        GameplayUiPhase.RUNNING -> AppGameplayPhase.RUNNING
        GameplayUiPhase.PAUSED -> AppGameplayPhase.PAUSED
        GameplayUiPhase.CHOICE -> AppGameplayPhase.CHOICE
        GameplayUiPhase.GAME_OVER -> AppGameplayPhase.GAME_OVER
        GameplayUiPhase.VICTORY -> AppGameplayPhase.VICTORY
    }

    private fun activeGameplayWeapon() = if (backStack.base == AppDestination.Gameplay) {
        gameplayFeature.uiModel().activeWeapon
    } else {
        null
    }

    internal fun currentRunStacks(): CodexRunStacks = if (backStack.base == AppDestination.Gameplay) {
        CodexRunStacks(gameplayFeature.uiModel().itemStacks)
    } else {
        CodexRunStacks()
    }

    private fun isRebirthRouteEligible(): Boolean {
        val routeEligible = backStack.base == AppDestination.Home ||
            gameplayFeature.uiModel().phase == GameplayUiPhase.VICTORY
        return routeEligible
    }

    private fun toggleMute() {
        val current = profileStore.preferences()
        val enable = !current.soundEnabled && !current.musicEnabled
        val result = profileStore.updatePreferences(
            current.copy(soundEnabled = enable, musicEnabled = enable),
        )
        if (result is ProfileMutationResult.Applied) {
            gameplayFeature.applyPreferences(profileStore.preferences())
        }
        syncAudioPreferences()
        playCue(AudioCue.UI_CLICK)
    }

    private fun playCue(cue: AudioCue) {
        audioService.advance(0f, listOf(cue))
    }

    private fun applyPersistedSettings() {
        gameplayFeature.applyPreferences(profileStore.preferences())
        syncAudioPreferences()
    }

    private fun syncAudioPreferences() {
        audioService.updatePreferences(profileStore.preferences().toAudioPreferences())
    }
}

internal enum class AppShortcut {
    SETTINGS,
    LAB,
    ARMORY,
    REBIRTH,
    CODEX,
    MUTE,
    BACK,
    ENTER,
}

internal fun Key.toAppShortcut(): AppShortcut? = when (this) {
    Key.S -> AppShortcut.SETTINGS
    Key.L -> AppShortcut.LAB
    Key.A -> AppShortcut.ARMORY
    Key.B -> AppShortcut.REBIRTH
    Key.C -> AppShortcut.CODEX
    Key.M -> AppShortcut.MUTE
    Key.Escape -> AppShortcut.BACK
    Key.Enter -> AppShortcut.ENTER
    else -> null
}

internal fun kinetickk.ball.profile.api.PlayerProfile.toRunConfiguration(): RunConfiguration =
    RunConfiguration(
        preferences = preferences,
        coreShape = loadout.coreShape,
        startingWeapon = loadout.selectedWeapon,
        unlockedWeapons = loadout.unlockedWeapons,
        metaRanks = labProgress.ranks,
        knownItemIds = collection.discoveredItemIds,
        rebirthLevel = rebirthProgress.level,
        matterAtStart = economy.matter,
        lifetimeMatterAtStart = economy.lifetimeMatter,
    )

private fun PlayerPreferences.toAudioPreferences(): AudioPreferences = AudioPreferences(
    soundEnabled = soundEnabled,
    musicEnabled = musicEnabled,
    masterVolume = masterVolume,
)
