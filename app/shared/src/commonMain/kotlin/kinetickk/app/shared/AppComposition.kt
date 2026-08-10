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
import kinetickk.ball.content.api.ContentCatalog
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.impl.DefaultAudioService
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.impl.createPlatformProfileComponent
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.armory.impl.DefaultArmoryFeature
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.api.CodexOutput
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.codex.impl.DefaultCodexFeature
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandAdmission
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandRef
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayExitProfileOutcome
import kinetickk.ball.gameplay.api.GameplayPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
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
import kinetickk.flow.session.interaction.reset.api.ResetModalFeature
import kinetickk.flow.session.interaction.reset.api.ResetModalMode
import kinetickk.flow.session.interaction.reset.api.ResetModalOutput
import kinetickk.flow.session.interaction.reset.api.ResetModalRenderModel
import kinetickk.flow.session.interaction.reset.impl.DefaultResetModalFeature

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
    private val contentCatalog: ContentCatalog = createContentCatalog(),
    profilePort: ProfilePort? = null,
    audioService: AudioService? = null,
    gameplayFeature: GameplayFeature? = null,
    homeFeature: HomeFeature? = null,
    settingsFeature: SettingsFeature? = null,
    labFeature: LabFeature? = null,
    armoryFeature: ArmoryFeature? = null,
    rebirthFeature: RebirthFeature? = null,
    codexFeature: CodexFeature? = null,
    resetModalFeature: ResetModalFeature? = null,
) {
    private val profilePolicy = contentCatalog.profilePolicy()
    private val gameplayContent = contentCatalog.gameplayContent()
    private val uiCatalog = contentCatalog.uiCatalog()

    private var gameplayResultBinding: GameplayFeature? = null
    private val profilePort: ProfilePort = profilePort ?: createPlatformProfileComponent(
        policy = profilePolicy,
        commandResultSink = { result ->
            checkNotNull(gameplayResultBinding) {
                "Gameplay result binding must exist before Profile can complete a command"
            }.receiveProfileCommandResult(result)
        },
    )
    private val audioService: AudioService = audioService ?: DefaultAudioService()
    private val sessionAudioExecutor = SessionAudioExecutor(this.audioService)
    private val gameplayFeature: GameplayFeature = gameplayFeature ?: DefaultGameplayFeature(
        this.profilePort,
        this.audioService,
    )
    private val homeFeature: HomeFeature = homeFeature ?: DefaultHomeFeature(
        profilePort = this.profilePort,
        uiCatalog = uiCatalog,
        audioService = this.audioService,
    )
    private val settingsFeature: SettingsFeature = settingsFeature ?: DefaultSettingsFeature(
        this.profilePort,
        this.audioService,
    )
    private val labFeature: LabFeature = labFeature ?: DefaultLabFeature(
        profilePort = this.profilePort,
        metaUpgrades = uiCatalog.metaUpgrades,
        audioService = this.audioService,
    )
    private val armoryFeature: ArmoryFeature = armoryFeature ?: DefaultArmoryFeature(
        profilePort = this.profilePort,
        weapons = uiCatalog.weapons,
        weaponMasteries = uiCatalog.weaponMasteries,
        audioService = this.audioService,
    )
    private val rebirthFeature: RebirthFeature = rebirthFeature ?: DefaultRebirthFeature(
        profilePort = this.profilePort,
        rebirthPolicy = uiCatalog.rebirth,
        audioService = this.audioService,
    )
    private val codexFeature: CodexFeature = codexFeature ?: DefaultCodexFeature(
        profilePort = this.profilePort,
        uiCatalog = uiCatalog,
        audioService = this.audioService,
    )
    private val resetModalFeature: ResetModalFeature = resetModalFeature ?: DefaultResetModalFeature(
        this.audioService,
    )

    private val navigator = AppNavigator()
    private var nextRunIdValue: Long? = 0L
    private var sessionRevisionValue: Long = 0L
    private var nextGameplayCommandOrdinalValue: Int? = 0
    private var pendingGameplayCommandRefValue: GameplayCommandRef? = null
    private var pendingGameplayResultHandlerValue:
        ((GameplayCommandResult.Accepted) -> Unit)? = null

    internal var gameplayWorkflowFailure: AppGameplayWorkflowFailure? = null
        private set

    val backStack: AppBackStack
        get() = navigator.backStack

    init {
        gameplayResultBinding = this.gameplayFeature
        syncAudioPreferences()
    }

    @Composable
    fun Content() {
        val focusRequester = remember(this) { FocusRequester() }
        var backStackValue by remember(this) { mutableStateOf(backStack) }
        var persistenceStatusValue by remember(this) {
            mutableStateOf(profilePort.query(ProfileQuery.GetPersistenceStatus))
        }

        fun refreshShell() {
            backStackValue = backStack
            persistenceStatusValue = profilePort.query(ProfileQuery.GetPersistenceStatus)
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
            val resetModalModel = persistenceStatusValue.toResetModalRenderModelOrNull()
            val normalInputEnabled = resetModalModel == null
            when (backStackValue.base) {
                AppDestination.Home -> homeFeature.Content(
                    inputEnabled = normalInputEnabled && backStackValue.overlay == null,
                    onOutput = { output ->
                        handleHomeOutput(output)
                        refreshShell()
                    },
                )
                AppDestination.Gameplay -> gameplayFeature.Content(
                    inputEnabled = normalInputEnabled && backStackValue.overlay == null,
                    onOutput = { output ->
                        handleGameplayOutput(output)
                        refreshShell()
                    },
                )
                else -> error("Only Home and Gameplay may be base destinations")
            }

            when (backStackValue.overlay.takeIf { normalInputEnabled }) {
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

            if (resetModalModel != null) {
                resetModalFeature.Content(resetModalModel) { output ->
                    handleResetModalOutput(output)
                    refreshShell()
                }
            }
        }
    }

    fun close() {
        audioService.close()
    }

    internal fun handleShortcut(shortcut: AppShortcut): Boolean = handleReadyShortcut(shortcut)

    private fun handleReadyShortcut(shortcut: AppShortcut): Boolean {
        if (currentResetModalModel() != null) return false
        return when (shortcut) {
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
    }

    internal fun handleHomeOutput(output: HomeOutput) {
        when (output) {
            HomeOutput.StartRun -> startNewRun()
            HomeOutput.OpenSettings -> openOverlay(AppDestination.Settings)
            HomeOutput.OpenLab -> openOverlay(AppDestination.Lab)
            HomeOutput.OpenArmory -> openOverlay(AppDestination.Armory)
            HomeOutput.OpenRebirth -> openOverlay(AppDestination.Rebirth)
            HomeOutput.OpenCodex -> openOverlay(AppDestination.Codex)
        }
    }

    internal fun handleGameplayOutput(output: GameplayInteractionOutput) {
        when (output) {
            GameplayInteractionOutput.OpenSettings -> openOverlay(AppDestination.Settings)
            GameplayInteractionOutput.OpenRebirth -> openOverlay(AppDestination.Rebirth)
            GameplayInteractionOutput.ExitToHome -> exitRun()
            GameplayInteractionOutput.RestartRun -> startNewRun()
        }
    }

    internal fun handleSettingsOutput(output: SettingsOutput) {
        when (output) {
            SettingsOutput.Back -> closeOverlay()
        }
    }

    internal fun handleLabOutput(output: LabOutput) {
        when (output) {
            LabOutput.Back -> closeOverlay()
        }
    }

    internal fun handleArmoryOutput(output: ArmoryOutput) {
        when (output) {
            ArmoryOutput.Back -> closeOverlay()
        }
    }

    internal fun handleRebirthOutput(output: RebirthOutput) {
        when (output) {
            RebirthOutput.Back -> closeOverlay()
            is RebirthOutput.CycleAdvanced -> startNewRun()
        }
    }

    internal fun handleCodexOutput(output: CodexOutput) {
        when (output) {
            CodexOutput.Back -> closeOverlay()
        }
    }

    internal fun handleResetModalOutput(output: ResetModalOutput) {
        val mode = currentResetModalModel()?.mode ?: return
        when (output) {
            ResetModalOutput.Cancel -> Unit
            ResetModalOutput.ConfirmDelete -> if (mode == ResetModalMode.CONFIRMATION_REQUIRED) {
                if (profilePort.accept(ProfilePulse.ConfirmLegacyReset) is ProfileAcceptance.Accepted) {
                    syncAudioPreferences()
                }
            }
            ResetModalOutput.RetryPurge -> if (mode == ResetModalMode.PURGE_NEEDS_ATTENTION) {
                profilePort.accept(ProfilePulse.RetryLegacyPurge)
            }
        }
    }

    internal fun openOverlay(destination: AppDestination): Boolean {
        val before = backStack
        val phase = gameplayPhase()
        if (before.base == AppDestination.Gameplay && phase == AppGameplayPhase.RUNNING) {
            var changed = false
            val run = gameplayFeature.activeRun() ?: return false
            dispatchGameplayCommand(run, GameplaySessionPulse.PauseForOverlay) { result ->
                if (result.outcome == GameplayCommandOutcome.OverlayPaused) {
                    val transition = navigator.openOverlay(
                        destination,
                        AppGameplayPhase.PAUSED,
                    )
                    changed = transition.backStack != before
                    if (
                        changed &&
                        before.overlay == AppDestination.Settings &&
                        destination != AppDestination.Settings
                    ) {
                        applyPersistedSettings()
                    }
                } else {
                    gameplayWorkflowFailure =
                        AppGameplayWorkflowFailure.UnexpectedResult(result)
                }
            }
            return changed
        }

        if (before.overlay == AppDestination.Settings && destination != AppDestination.Settings) {
            var replaced = false
            applyPersistedSettings {
                replaced = navigator.openOverlay(destination, phase).backStack != before
            }
            return replaced
        }
        val transition = navigator.openOverlay(destination, phase)
        val changed = transition.backStack != before
        return changed
    }

    internal fun closeOverlay() {
        val closing = backStack.overlay
        if (closing == AppDestination.Settings) {
            applyPersistedSettings { navigator.back() }
        } else {
            navigator.back()
        }
    }

    internal fun startNewRun() {
        val bootstrap = profilePort.query(ProfileQuery.GetRunBootstrap).result
        if (bootstrap !is ProfileRunBootstrapResult.Ready) return
        val retainedRun = gameplayFeature.activeRun()
            ?.takeIf { run ->
                val status = run.query(GameplayQuery.GetRunStatus)
                status.phase == GameplayRunPhase.CREATED && !status.profileCommandPending
            }
        val run = retainedRun ?: gameplayFeature.createRun(
            runId = allocateRunId(),
            commandResultSink = ::receiveGameplayCommandResult,
        )
        dispatchGameplayCommand(
            target = run,
            pulse = GameplaySessionPulse.StartRun(
                bootstrap.snapshot.toRunConfiguration(gameplayContent),
            ),
        ) { result ->
            if (result.outcome == GameplayCommandOutcome.RunStarted) {
                navigator.showGameplay()
            } else {
                gameplayWorkflowFailure =
                    AppGameplayWorkflowFailure.UnexpectedResult(result)
            }
        }
    }

    private fun gameplayPhase(): AppGameplayPhase {
        val run = gameplayFeature.activeRun() ?: return AppGameplayPhase.IDLE
        return when (run.query(GameplayQuery.GetRunStatus).phase) {
            GameplayRunPhase.CREATED,
            GameplayRunPhase.EXITED,
            -> AppGameplayPhase.IDLE
            GameplayRunPhase.RUNNING -> AppGameplayPhase.RUNNING
            GameplayRunPhase.PAUSED -> AppGameplayPhase.PAUSED
            GameplayRunPhase.CHOICE -> AppGameplayPhase.CHOICE
            GameplayRunPhase.GAME_OVER -> AppGameplayPhase.GAME_OVER
            GameplayRunPhase.VICTORY -> AppGameplayPhase.VICTORY
        }
    }

    private fun activeGameplayWeapon() = if (backStack.base == AppDestination.Gameplay) {
        gameplayFeature.activeRun()?.query(GameplayQuery.GetActiveWeapon)?.weapon
    } else {
        null
    }

    internal fun currentRunStacks(): CodexRunStacks = if (backStack.base == AppDestination.Gameplay) {
        CodexRunStacks(
            gameplayFeature.activeRun()
                ?.query(GameplayQuery.GetCodexStacks)
                ?.itemStacks
                ?: kinetickk.foundation.collections.immutableListOf(),
        )
    } else {
        CodexRunStacks()
    }

    private fun isRebirthRouteEligible(): Boolean {
        val routeEligible = backStack.base == AppDestination.Home ||
            gameplayFeature.activeRun()?.query(GameplayQuery.GetRunStatus)?.phase ==
            GameplayRunPhase.VICTORY
        return routeEligible
    }

    private fun toggleMute() {
        val result = profilePort.accept(ProfilePulse.ToggleMute)
        if (result is ProfileAcceptance.Accepted) {
            applyPreferencesToGameplay(currentPreferences())
        }
        syncAudioPreferences()
        sessionAudioExecutor.playUiClick()
    }

    private fun applyPersistedSettings(onApplied: () -> Unit = {}) {
        val preferences = currentPreferences()
        val run = gameplayFeature.activeRun()
        if (run == null) {
            syncAudioPreferences()
            onApplied()
            return
        }
        dispatchGameplayCommand(
            target = run,
            pulse = GameplaySessionPulse.ApplyPreferences(preferences),
        ) { result ->
            if (result.outcome is GameplayCommandOutcome.PreferencesApplied) {
                syncAudioPreferences()
                onApplied()
            } else {
                gameplayWorkflowFailure =
                    AppGameplayWorkflowFailure.UnexpectedResult(result)
            }
        }
    }

    private fun applyPreferencesToGameplay(preferences: PlayerPreferences) {
        val run = gameplayFeature.activeRun() ?: return
        dispatchGameplayCommand(
            target = run,
            pulse = GameplaySessionPulse.ApplyPreferences(preferences),
        ) { result ->
            if (result.outcome !is GameplayCommandOutcome.PreferencesApplied) {
                gameplayWorkflowFailure =
                    AppGameplayWorkflowFailure.UnexpectedResult(result)
            }
        }
    }

    private fun exitRun() {
        val run = gameplayFeature.activeRun() ?: return
        dispatchGameplayCommand(run, GameplaySessionPulse.ExitRun) { result ->
            when (val outcome = result.outcome) {
                is GameplayCommandOutcome.RunExited -> when (val profile = outcome.profile) {
                    GameplayExitProfileOutcome.NoProgress,
                    GameplayExitProfileOutcome.ProgressApplied,
                    -> navigator.showHome()
                    is GameplayExitProfileOutcome.ProgressRejected -> {
                        gameplayWorkflowFailure =
                            AppGameplayWorkflowFailure.ExitProgressRejected(profile)
                    }
                }
                else -> {
                    gameplayWorkflowFailure =
                        AppGameplayWorkflowFailure.UnexpectedResult(result)
                }
            }
        }
    }

    private fun dispatchGameplayCommand(
        target: GameplayPort,
        pulse: GameplaySessionPulse,
        onResult: (GameplayCommandResult.Accepted) -> Unit,
    ): GameplayAcceptance {
        check(pendingGameplayCommandRefValue == null) {
            "Only one temporary AppSession Gameplay command may be pending"
        }
        check(sessionRevisionValue < Long.MAX_VALUE) {
            "Temporary AppSession revision is exhausted"
        }
        val ordinal = checkNotNull(nextGameplayCommandOrdinalValue) {
            "Temporary AppSession Gameplay command ordinal is exhausted"
        }
        val sourceRevision = sessionRevisionValue + 1L
        val commandRef = GameplayCommandRef(
            sourceInstance = GameplayCommandSource.LocalSession,
            targetInstance = target.instanceId,
            sourceRevision = sourceRevision,
            ordinal = ordinal,
        )
        val command = GameplayCommand(commandRef, pulse)

        pendingGameplayCommandRefValue = commandRef
        pendingGameplayResultHandlerValue = onResult
        sessionRevisionValue = sourceRevision
        nextGameplayCommandOrdinalValue = if (ordinal == Int.MAX_VALUE) null else ordinal + 1
        gameplayWorkflowFailure = null

        val acceptance = target.accept(command, GameplayCommandAdmission(commandRef))
        check(acceptance.instanceId == commandRef.targetInstance) {
            "Gameplay acceptance marker target identity mismatch"
        }
        when (acceptance) {
            is GameplayAcceptance.Rejected -> {
                check(pendingGameplayCommandRefValue == commandRef) {
                    "Gameplay cannot both complete and reject one command before acceptance"
                }
                clearPendingGameplayCommand()
                gameplayWorkflowFailure = AppGameplayWorkflowFailure.RejectedBeforeAcceptance(
                    commandRef = commandRef,
                    rejection = acceptance,
                )
            }
            is GameplayAcceptance.Accepted -> {
                check(pendingGameplayCommandRefValue == null) {
                    "Accepted synchronous Gameplay command returned without its reserved result"
                }
            }
        }
        return acceptance
    }

    private fun receiveGameplayCommandResult(result: GameplayCommandResult.Accepted) {
        val commandRef = checkNotNull(pendingGameplayCommandRefValue) {
            "Gameplay result arrived without a pending temporary AppSession command"
        }
        check(result.commandRef == commandRef) {
            "Gameplay result command correlation mismatch"
        }
        val handler = checkNotNull(pendingGameplayResultHandlerValue)
        clearPendingGameplayCommand()
        handler(result)
    }

    private fun clearPendingGameplayCommand() {
        pendingGameplayCommandRefValue = null
        pendingGameplayResultHandlerValue = null
    }

    private fun allocateRunId(): RunId {
        val value = checkNotNull(nextRunIdValue) { "Gameplay RunId space is exhausted" }
        nextRunIdValue = if (value == Long.MAX_VALUE) null else value + 1L
        return RunId(value)
    }

    private fun syncAudioPreferences() {
        audioService.updatePreferences(currentPreferences().toAudioPreferences())
    }

    private fun currentPreferences(): PlayerPreferences =
        profilePort.query(ProfileQuery.GetPreferences).preferences

    private fun currentResetModalModel(): ResetModalRenderModel? =
        profilePort.query(ProfileQuery.GetPersistenceStatus).toResetModalRenderModelOrNull()
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

internal sealed interface AppGameplayWorkflowFailure {
    data class RejectedBeforeAcceptance(
        val commandRef: GameplayCommandRef,
        val rejection: GameplayAcceptance.Rejected,
    ) : AppGameplayWorkflowFailure

    data class UnexpectedResult(
        val result: GameplayCommandResult.Accepted,
    ) : AppGameplayWorkflowFailure

    data class ExitProgressRejected(
        val outcome: GameplayExitProfileOutcome.ProgressRejected,
    ) : AppGameplayWorkflowFailure
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

internal fun GameplayProfileSnapshot.toRunConfiguration(
    content: GameplayContentSnapshot,
): RunConfiguration =
    RunConfiguration(
        content = content,
        profile = this,
    )

private fun PlayerPreferences.toAudioPreferences(): AudioPreferences = AudioPreferences(
    soundEnabled = soundEnabled,
    musicEnabled = musicEnabled,
    masterVolume = masterVolume,
)

private fun PersistenceStatusProjection.toResetModalRenderModelOrNull(): ResetModalRenderModel? =
    when (reset) {
        is ProfileResetStatus.ConfirmationRequired -> ResetModalRenderModel(
            ResetModalMode.CONFIRMATION_REQUIRED,
        )
        is ProfileResetStatus.WritingFreshV4,
        is ProfileResetStatus.PurgingLegacy,
        -> ResetModalRenderModel(ResetModalMode.RESET_IN_PROGRESS)
        is ProfileResetStatus.NeedsAttention -> ResetModalRenderModel(
            ResetModalMode.PURGE_NEEDS_ATTENTION,
        )
        is ProfileResetStatus.NotRequired -> when (bootstrap) {
            ProfileBootstrapStatus.Ready -> null
            ProfileBootstrapStatus.AwaitingResource,
            is ProfileBootstrapStatus.Blocked,
            -> ResetModalRenderModel(ResetModalMode.BOOTSTRAP_UNAVAILABLE)
        }
    }
