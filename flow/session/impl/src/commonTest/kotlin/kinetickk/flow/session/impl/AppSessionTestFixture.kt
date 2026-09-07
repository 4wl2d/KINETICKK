// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.gameplay.api.GameplayRunExited

import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplayRunPort
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.GameplaySettingsApplied
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.GameplayOverlayPaused
import kinetickk.ball.gameplay.interaction.GameplayRunHost
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.ProfileSettings
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.ProfileRebirth
import kinetickk.ball.profile.api.ProfileRebirthAdvanced
import kinetickk.ball.profile.api.ProfileLoadout
import kinetickk.ball.profile.api.ProfileCoreShapeSelected

internal class AppSessionTestRig(
    val profile: FakeProfileCapabilities = FakeProfileCapabilities(),
    val gameplay: FakeSessionGameplayHost = FakeSessionGameplayHost(),
) {
    val audioPreferences = mutableListOf<PlayerPreferences>()
    val effectEvents = mutableListOf<String>()
    var muteFeedbackCount: Int = 0
    var rebirthAcceptedFeedbackCount: Int = 0

    val component: DefaultAppSessionComponent = createAppSessionComponent(
        profilePort = profile,
        profileSettings = profile,
        profileLoadout = profile,
        profileRebirth = profile,
        gameplayRunHost = gameplay,
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
    ).let { it as DefaultAppSessionComponent }
}

internal class FakeProfileCapabilities(
    var profile: PlayerProfile = PlayerProfile(
        rebirthProgress = RebirthProgress(level = 0, highestCleared = 0),
    ),
) : ProfileReadPort, ProfileSettings, ProfileLoadout, ProfileRebirth {
    override val instanceId = kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID

    var revision: ProfileRevision = ProfileRevision(1L)
    var bootstrap: ProfileBootstrapStatus = ProfileBootstrapStatus.Ready
    var persistence: ProfilePersistenceStatus = ProfilePersistenceStatus.NotAttempted
    var projectionInstanceId = instanceId
    val queries = mutableListOf<String>()
    val muteCalls = mutableListOf<InlineReply<ProfileSettingsChanged, ProfileRefusal>>()
    var muteHandler: ((InlineReply<ProfileSettingsChanged, ProfileRefusal>) -> Unit)? = null
    var onMuteObserved: (() -> Unit)? = null
    val shapeCalls = mutableListOf<CoreShape>()
    val shapeReplies = mutableListOf<InlineReply<ProfileCoreShapeSelected, ProfileRefusal>>()
    var shapeHandler: ((CoreShape, InlineReply<ProfileCoreShapeSelected, ProfileRefusal>) -> Unit)? = null

    override fun selectCoreShape(shape: CoreShape, reply: InlineReply<ProfileCoreShapeSelected, ProfileRefusal>) {
        reply.checkAvailable()
        shapeCalls += shape
        shapeReplies += reply
        shapeHandler?.invoke(shape, reply) ?: completeShape(shape, reply)
    }

    fun completeShape(
        shape: CoreShape,
        reply: InlineReply<ProfileCoreShapeSelected, ProfileRefusal>,
        transform: (ProfileCoreShapeSelected) -> ProfileCoreShapeSelected = { it },
    ) {
        revision = ProfileRevision(revision.value + 1L)
        profile = profile.copy(loadout = profile.loadout.copy(coreShape = shape))
        reply.accepted(transform(ProfileCoreShapeSelected(revision, shape)))
    }

    val rebirthReplies = mutableListOf<InlineReply<ProfileRebirthAdvanced, ProfileRefusal>>()
    var rebirthHandler: ((InlineReply<ProfileRebirthAdvanced, ProfileRefusal>) -> Unit)? = null

    override fun advanceRebirth(reply: InlineReply<ProfileRebirthAdvanced, ProfileRefusal>) {
        reply.checkAvailable()
        rebirthReplies += reply
        rebirthHandler?.invoke(reply) ?: completeRebirth(reply)
    }

    fun completeRebirth(reply: InlineReply<ProfileRebirthAdvanced, ProfileRefusal>) {
        revision = ProfileRevision(revision.value + 1)
        profile = profile.copy(rebirthProgress = profile.rebirthProgress.copy(level = profile.rebirthProgress.level + 1))
        reply.accepted(ProfileRebirthAdvanced(revision, profile.rebirthProgress))
    }

    override fun toggleMute(reply: InlineReply<ProfileSettingsChanged, ProfileRefusal>) {
        reply.checkAvailable()
        muteCalls += reply
        onMuteObserved?.invoke()
        muteHandler?.invoke(reply) ?: completeMute(reply)
    }

    fun completeMute(
        reply: InlineReply<ProfileSettingsChanged, ProfileRefusal>,
        preferences: PlayerPreferences = profile.preferences.let { current ->
            val enable = !current.soundEnabled && !current.musicEnabled
            current.copy(soundEnabled = enable, musicEnabled = enable)
        },
    ) {
        revision = ProfileRevision(revision.value + 1L)
        profile = profile.copy(preferences = preferences)
        reply.accepted(ProfileSettingsChanged(revision, preferences))
    }

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection {
        queries += "runBootstrap"
        return RunBootstrapProjection(
            projectionInstanceId,
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
        return PreferencesProjection(projectionInstanceId, revision, profile.preferences)
    }

    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection {
        queries += "rebirthProgress"
        return RebirthProgressProjection(
            projectionInstanceId,
            revision,
            RebirthProfileSnapshot(profile.rebirthProgress),
            canAdvanceRebirth(),
        )
    }

    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection {
        queries += "persistenceStatus"
        return PersistenceStatusProjection(projectionInstanceId, revision, bootstrap, persistence)
    }

    override fun query(query: ProfileQuery.GetHomeProgress): kinetickk.ball.profile.api.HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetCollection): kinetickk.ball.profile.api.CollectionProjection = error("unused")
    override fun query(query: ProfileQuery.GetLabProgress): kinetickk.ball.profile.api.LabProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetLoadout): kinetickk.ball.profile.api.LoadoutProjection = error("unused")

    private fun canAdvanceRebirth(): Boolean =
        profile.rebirthProgress.highestCleared >= profile.rebirthProgress.level
}

internal class FakeSessionGameplayHost : GameplayRunHost {
    val createdRunIds = mutableListOf<RunId>()
    var onCreateRun: (RunId) -> Unit = {}
    var configureRun: (FakeGameplayRunPort) -> Unit = {}
    private var active: FakeGameplayRunPort? = null

    override fun createRun(
        runId: RunId,
    ): GameplayRunPort {
        createdRunIds += runId
        onCreateRun(runId)
        return FakeGameplayRunPort(runId).also { run ->
            configureRun(run)
            active = run
        }
    }

    override fun activeRun(): GameplayRunPort? = active

    fun activeFakeRun(): FakeGameplayRunPort? = active
}

internal class FakeGameplayRunPort(
    runId: RunId,
) : GameplayRunPort {
    override val instanceId = GameplayInstanceId(runId)

    var revision: GameplayRevision = GameplayRevision.ZERO
    var phase: GameplayRunPhase = GameplayRunPhase.CREATED
    var progressPending: Boolean = false
    var statusInstanceId: GameplayInstanceId = instanceId
    val settingsCalls = mutableListOf<PlayerPreferences>()
    val settingsReplies = mutableListOf<InlineReply<GameplaySettingsApplied, GameplayRefusal>>()
    var settingsHandler: ((PlayerPreferences, InlineReply<GameplaySettingsApplied, GameplayRefusal>) -> Unit)? = null
    var onSettingsObserved: ((PlayerPreferences) -> Unit)? = null
    val startReplies = mutableListOf<InlineReply<GameplayRunStarted, GameplayRefusal>>()
    val pauseReplies = mutableListOf<InlineReply<GameplayOverlayPaused, GameplayRefusal>>()
    var startHandler: ((InlineReply<GameplayRunStarted, GameplayRefusal>) -> Unit)? = null
    var pauseHandler: ((InlineReply<GameplayOverlayPaused, GameplayRefusal>) -> Unit)? = null
    var onStartObserved: (() -> Unit)? = null
    var onPauseObserved: (() -> Unit)? = null

    override fun startRun(reply: InlineReply<GameplayRunStarted, GameplayRefusal>) {
        reply.checkAvailable()
        startReplies += reply
        onStartObserved?.invoke()
        startHandler?.invoke(reply) ?: completeStart(reply)
    }

    override fun pauseForOverlay(reply: InlineReply<GameplayOverlayPaused, GameplayRefusal>) {
        reply.checkAvailable()
        pauseReplies += reply
        onPauseObserved?.invoke()
        pauseHandler?.invoke(reply) ?: completePause(reply)
    }

    fun completeStart(reply: InlineReply<GameplayRunStarted, GameplayRefusal>) {
        revision = GameplayRevision(revision.value + 1L)
        phase = GameplayRunPhase.RUNNING
        reply.accepted(GameplayRunStarted(instanceId.runId, revision))
    }

    fun completePause(reply: InlineReply<GameplayOverlayPaused, GameplayRefusal>) {
        revision = GameplayRevision(revision.value + 1L)
        phase = GameplayRunPhase.PAUSED
        reply.accepted(GameplayOverlayPaused(instanceId.runId, revision))
    }

    override fun applyPreferences(
        preferences: PlayerPreferences,
        reply: InlineReply<GameplaySettingsApplied, GameplayRefusal>,
    ) {
        reply.checkAvailable()
        settingsCalls += preferences
        settingsReplies += reply
        onSettingsObserved?.invoke(preferences)
        settingsHandler?.invoke(preferences, reply) ?: completeSettings(reply)
    }

    fun completeSettings(reply: InlineReply<GameplaySettingsApplied, GameplayRefusal>) {
        revision = GameplayRevision(revision.value + 1L)
        reply.accepted(GameplaySettingsApplied(instanceId.runId, revision))
    }

    val exitReplies = mutableListOf<InlineReply<GameplayRunExited, GameplayRefusal>>()
    var exitHandler: ((InlineReply<GameplayRunExited, GameplayRefusal>) -> Unit)? = null

    override fun exitRun(reply: InlineReply<GameplayRunExited, GameplayRefusal>) {
        reply.checkAvailable()
        exitReplies += reply
        exitHandler?.invoke(reply) ?: completeExit(reply)
    }

    fun completeExit(
        reply: InlineReply<GameplayRunExited, GameplayRefusal>,
        progress: GameplayExitProgressResult = GameplayExitProgressResult.NoProgress,
    ) {
        revision = GameplayRevision(revision.value + if (progress === GameplayExitProgressResult.NoProgress) 1 else 2)
        phase = GameplayRunPhase.EXITED
        reply.accepted(GameplayRunExited(instanceId.runId, revision, progress))
    }

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayRunStatusProjection(statusInstanceId, revision, phase, progressPending)


}

internal fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences = preferences,
    economy = economy,
    loadout = loadout,
    labProgress = labProgress,
    collection = collection,
    rebirthProgress = rebirthProgress,
)
