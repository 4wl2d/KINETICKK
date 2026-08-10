// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GameplayCodexStacksProjection
import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandAdmission
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRenderProjection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.foundation.collections.immutableListOf

internal class AppSessionTestRig(
    val profile: FakeSessionProfilePort = FakeSessionProfilePort(),
    val gameplay: FakeSessionGameplayFeature = FakeSessionGameplayFeature(),
    gameplayContent: GameplayContentSnapshot = sessionGameplayContentFixture(),
) {
    val audioPreferences = mutableListOf<PlayerPreferences>()
    val effectEvents = mutableListOf<String>()
    var muteFeedbackCount: Int = 0
    var rebirthAcceptedFeedbackCount: Int = 0

    val component: DefaultAppSessionComponent = createAppSessionComponent(
        gameplayContent = gameplayContent,
        profilePort = profile,
        gameplayFeature = gameplay,
        updateAudioPreferences = { preferences ->
            effectEvents += "audio"
            audioPreferences += preferences
        },
        playMuteFeedback = {
            effectEvents += "mute"
            muteFeedbackCount += 1
        },
        playRebirthAcceptedFeedback = {
            effectEvents += "rebirth"
            rebirthAcceptedFeedbackCount += 1
        },
    ).let { it as DefaultAppSessionComponent }.also { component ->
        profile.resultSink = component::receiveProfileCommandResult
    }
}

internal class FakeSessionProfilePort(
    var profile: PlayerProfile = PlayerProfile(
        rebirthProgress = RebirthProgress(level = 0, highestCleared = 0),
    ),
) : ProfilePort {
    override val instanceId = kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID

    var revision: ProfileRevision = ProfileRevision(1L)
    var bootstrap: ProfileBootstrapStatus = ProfileBootstrapStatus.Ready
    var reset: ProfileResetStatus = ProfileResetStatus.NotRequired(false)
    var persistence: ProfilePersistenceStatus = ProfilePersistenceStatus.NotAttempted
    var resultSink: (ProfileCommandResult.Accepted) -> Unit = {}
    var commandHandler: ((ProfileCommand) -> ProfileAcceptance)? = null
    var onCommandObserved: ((ProfileCommand) -> Unit)? = null
    val commands = mutableListOf<ProfileCommand>()
    val queries = mutableListOf<String>()

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance =
        ProfileAcceptance.Rejected(instanceId, revision, ProfileRejection.NoChange)

    override fun accept(
        command: ProfileCommand,
        admission: ProfileCommandAdmission,
    ): ProfileAcceptance {
        check(command.ref == admission.commandRef)
        commands += command
        onCommandObserved?.invoke(command)
        return commandHandler?.invoke(command) ?: completeAutomatically(command)
    }

    fun complete(
        command: ProfileCommand,
        outcome: ProfileCommandOutcome,
    ): ProfileAcceptance.Accepted {
        revision = ProfileRevision(revision.value + 1L)
        applyOutcome(outcome)
        resultSink(ProfileCommandResult.Accepted(command.ref, revision, outcome))
        return ProfileAcceptance.Accepted(instanceId, revision)
    }

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection {
        queries += "runBootstrap"
        return RunBootstrapProjection(
            instanceId,
            revision,
            if (bootstrap == ProfileBootstrapStatus.Ready) {
                ProfileRunBootstrapResult.Ready(profile.toGameplaySnapshot())
            } else {
                ProfileRunBootstrapResult.Unavailable(bootstrap)
            },
        )
    }

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection {
        queries += "preferences"
        return PreferencesProjection(instanceId, revision, profile.preferences)
    }

    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection {
        queries += "homeProgress"
        return HomeProgressProjection(
            instanceId,
            revision,
            profile.economy,
            profile.loadout,
            profile.collection,
            profile.rebirthProgress,
            canAdvanceRebirth(),
        )
    }

    override fun query(query: ProfileQuery.GetLabProgress): LabProgressProjection {
        queries += "labProgress"
        return LabProgressProjection(
            instanceId,
            revision,
            LabProfileSnapshot(profile.economy, profile.labProgress),
        )
    }

    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection {
        queries += "loadout"
        return LoadoutProjection(
            instanceId,
            revision,
            LoadoutProfileSnapshot(profile.economy, profile.loadout),
        )
    }

    override fun query(query: ProfileQuery.GetCollection): CollectionProjection {
        queries += "collection"
        return CollectionProjection(instanceId, revision, profile.collection)
    }

    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection {
        queries += "rebirthProgress"
        return RebirthProgressProjection(
            instanceId,
            revision,
            RebirthProfileSnapshot(profile.rebirthProgress),
            canAdvanceRebirth(),
        )
    }

    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection {
        queries += "persistenceStatus"
        return PersistenceStatusProjection(
            instanceId,
            revision,
            bootstrap,
            reset,
            persistence,
        )
    }

    private fun completeAutomatically(command: ProfileCommand): ProfileAcceptance =
        when (val pulse = command.pulse) {
            ProfilePulse.ToggleMute -> {
                val current = profile.preferences
                val enabled = !current.soundEnabled && !current.musicEnabled
                complete(
                    command,
                    ProfileCommandOutcome.PreferencesChanged(
                        current.copy(soundEnabled = enabled, musicEnabled = enabled),
                    ),
                )
            }
            is ProfilePulse.SelectCoreShape -> complete(
                command,
                ProfileCommandOutcome.CoreShapeSelected(pulse.shape),
            )
            ProfilePulse.AdvanceRebirth -> {
                val next = profile.rebirthProgress.copy(level = profile.rebirthProgress.level + 1)
                complete(command, ProfileCommandOutcome.RebirthAdvanced(next))
            }
            ProfilePulse.ConfirmLegacyReset,
            ProfilePulse.RetryLegacyPurge,
            -> complete(command, ProfileCommandOutcome.ResetCompleted)
            is ProfilePulse.AdjustPreference -> complete(
                command,
                ProfileCommandOutcome.PreferencesChanged(profile.preferences),
            )
            is ProfilePulse.ApplyGameplayProgress,
            is ProfilePulse.PurchaseMetaUpgrade,
            is ProfilePulse.PurchaseOrEquipWeapon,
            -> error("Unexpected Session-owned Profile pulse: $pulse")
        }

    private fun applyOutcome(outcome: ProfileCommandOutcome) {
        when (outcome) {
            is ProfileCommandOutcome.CoreShapeSelected -> {
                profile = profile.copy(
                    loadout = profile.loadout.copy(coreShape = outcome.shape),
                )
            }
            is ProfileCommandOutcome.PreferencesChanged -> {
                profile = profile.copy(preferences = outcome.preferences)
            }
            is ProfileCommandOutcome.RebirthAdvanced -> {
                profile = profile.copy(rebirthProgress = outcome.progress)
            }
            ProfileCommandOutcome.ResetCompleted -> {
                profile = PlayerProfile()
                bootstrap = ProfileBootstrapStatus.Ready
                reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = true)
                persistence = ProfilePersistenceStatus.Persisted(revision)
            }
            is ProfileCommandOutcome.ResetNeedsAttention -> {
                reset = outcome.status
                bootstrap = ProfileBootstrapStatus.Blocked(
                    kinetickk.ball.profile.api.ProfileBootstrapBlockReason.ResetNeedsAttention(
                        outcome.status.result,
                    ),
                )
            }
            is ProfileCommandOutcome.ResetWriteOutcomeUnknown,
            is ProfileCommandOutcome.ResetWriteRejected,
            ProfileCommandOutcome.GameplayProgressApplied,
            -> Unit
        }
    }

    private fun canAdvanceRebirth(): Boolean =
        profile.rebirthProgress.highestCleared >= profile.rebirthProgress.level
}

internal class FakeSessionGameplayFeature : GameplayFeature {
    val createdRunIds = mutableListOf<RunId>()
    var onCreateRun: (RunId) -> Unit = {}
    var configureRun: (FakeSessionGameplayPort) -> Unit = {}
    private var active: FakeSessionGameplayPort? = null

    override fun createRun(
        runId: RunId,
        commandResultSink: (GameplayCommandResult.Accepted) -> Unit,
    ): GameplayPort {
        createdRunIds += runId
        onCreateRun(runId)
        return FakeSessionGameplayPort(runId, commandResultSink).also { run ->
            configureRun(run)
            active = run
        }
    }

    override fun activeRun(): GameplayPort? = active

    fun activeFakeRun(): FakeSessionGameplayPort? = active

    override fun receiveProfileCommandResult(result: ProfileCommandResult.Accepted) = Unit

    @Composable
    override fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayInteractionOutput) -> Unit,
    ) = Unit
}

internal class FakeSessionGameplayPort(
    runId: RunId,
    private val resultSink: (GameplayCommandResult.Accepted) -> Unit,
) : GameplayPort {
    override val instanceId = kinetickk.ball.gameplay.api.GameplayInstanceId(runId)

    var revision: GameplayRevision = GameplayRevision.ZERO
    var phase: GameplayRunPhase = GameplayRunPhase.CREATED
    var profileCommandPending: Boolean = false
    var commandHandler: ((GameplayCommand) -> GameplayAcceptance)? = null
    var onCommandObserved: ((GameplayCommand) -> Unit)? = null
    val commands = mutableListOf<GameplayCommand>()

    override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance =
        GameplayAcceptance.Accepted(instanceId, revision)

    override fun accept(
        command: GameplayCommand,
        admission: GameplayCommandAdmission,
    ): GameplayAcceptance {
        check(command.ref == admission.commandRef)
        commands += command
        onCommandObserved?.invoke(command)
        return commandHandler?.invoke(command) ?: completeAutomatically(command)
    }

    fun complete(
        command: GameplayCommand,
        outcome: GameplayCommandOutcome,
    ): GameplayAcceptance.Accepted {
        revision = GameplayRevision(revision.value + 1L)
        resultSink(GameplayCommandResult.Accepted(command.ref, revision, outcome))
        return GameplayAcceptance.Accepted(instanceId, revision)
    }

    override fun query(query: GameplayQuery.GetRender): GameplayRenderProjection =
        GameplayRenderProjection(instanceId, revision, renderModel = null)

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayRunStatusProjection(instanceId, revision, phase, profileCommandPending)

    override fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayActiveWeaponProjection(instanceId, revision, weapon = null)

    override fun query(query: GameplayQuery.GetCodexStacks): GameplayCodexStacksProjection =
        GameplayCodexStacksProjection(instanceId, revision, immutableListOf())

    private fun completeAutomatically(command: GameplayCommand): GameplayAcceptance =
        when (val pulse = command.pulse) {
            is GameplaySessionPulse.StartRun -> {
                phase = GameplayRunPhase.RUNNING
                complete(command, GameplayCommandOutcome.RunStarted)
            }
            GameplaySessionPulse.PauseForOverlay -> {
                phase = GameplayRunPhase.PAUSED
                complete(command, GameplayCommandOutcome.OverlayPaused)
            }
            is GameplaySessionPulse.ApplyPreferences -> complete(
                command,
                GameplayCommandOutcome.PreferencesApplied(pulse.preferences),
            )
            GameplaySessionPulse.ExitRun -> {
                phase = GameplayRunPhase.EXITED
                complete(
                    command,
                    GameplayCommandOutcome.RunExited(
                        kinetickk.ball.gameplay.api.GameplayExitProfileOutcome.NoProgress,
                    ),
                )
            }
        }
}

internal fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences = preferences,
    economy = economy,
    loadout = loadout,
    labProgress = labProgress,
    collection = collection,
    rebirthProgress = rebirthProgress,
)

internal fun sessionGameplayContentFixture(): GameplayContentSnapshot {
    val rebirth = RebirthProfile(
        tier = 0,
        directive = RebirthDirective.BASELINE,
        openingEnemyCount = 1,
        enemyCapMultiplier = 1f,
        spawnRateMultiplier = 1f,
        enemyHealthMultiplier = 1f,
        enemySpeedMultiplier = 1f,
        incomingDamageMultiplier = 1f,
        eliteRateMultiplier = 1f,
        threatTimeOffsetSeconds = 0f,
        playerPowerMultiplier = 1f,
        playerIntegrityBonus = 0f,
        matterGainMultiplier = 1f,
        bonusRerolls = 0,
        maximumActiveEnemies = 1,
        minimumSpawnIntervalSeconds = 1f,
        minimumEliteIntervalSeconds = 1f,
    )
    return GameplayContentSnapshot(
        version = ContentVersion("session-impl-test"),
        items = immutableListOf(),
        weapons = immutableListOf(),
        weaponMasteries = immutableListOf(),
        metaUpgrades = immutableListOf(),
        relics = immutableListOf(),
        rebirth = RebirthPolicySnapshot(
            minimumLevel = 0,
            maximumLevel = 0,
            profiles = immutableListOf(rebirth),
            maxActiveEnemies = 1,
            minSpawnIntervalSeconds = 1f,
            minEliteIntervalSeconds = 1f,
        ),
        relicPolicy = RelicPolicy(maxSlots = 1, maxRank = 1),
    )
}
