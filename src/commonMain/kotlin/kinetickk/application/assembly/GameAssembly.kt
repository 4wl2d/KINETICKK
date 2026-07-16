// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.assembly

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.features.audio.AudioFeatureBall
import kinetickk.features.audio.nucleus.protocol.AudioCue
import kinetickk.features.audio.nucleus.protocol.AudioPlaybackSettings
import kinetickk.features.game.GameDispatchResult
import kinetickk.features.game.GameFeatureBall
import kinetickk.features.game.GameRunStartExecution
import kinetickk.features.game.interaction.fx.InteractionFxReducer
import kinetickk.features.game.nucleus.CoreShape
import kinetickk.features.game.nucleus.DamageNumberFormat
import kinetickk.features.game.nucleus.DamageNumberSize
import kinetickk.features.game.nucleus.GameSettings
import kinetickk.features.game.nucleus.MetaUpgradeId
import kinetickk.features.game.nucleus.ParticleDensity
import kinetickk.features.game.nucleus.RebirthDirective
import kinetickk.features.game.nucleus.RebirthProfile
import kinetickk.features.game.nucleus.GameBootstrapSnapshot
import kinetickk.features.game.nucleus.WeaponId
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.game.nucleus.projection.VisualFxProjection
import kinetickk.features.game.nucleus.protocol.CommandRequest
import kinetickk.features.game.nucleus.protocol.GameCommand
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameOutputKind
import kinetickk.features.game.nucleus.protocol.GameProfileReplica
import kinetickk.features.game.nucleus.protocol.GameQuery
import kinetickk.features.game.nucleus.protocol.GameQueryResult
import kinetickk.features.game.nucleus.protocol.ProfileChange
import kinetickk.features.game.nucleus.protocol.SettingsChange
import kinetickk.features.game.nucleus.protocol.SoundCue
import kinetickk.features.game.nucleus.protocol.VisualFxCue
import kinetickk.features.profile.ProfileDispatchResult
import kinetickk.features.profile.ProfileFeatureBall
import kinetickk.features.profile.nucleus.domain.CoreShape as ProfileCoreShape
import kinetickk.features.profile.nucleus.domain.MetaUpgradeId as ProfileMetaUpgradeId
import kinetickk.features.profile.nucleus.domain.RebirthDirective as ProfileRebirthDirective
import kinetickk.features.profile.nucleus.domain.RebirthProfile as ProfileRebirthProfile
import kinetickk.features.profile.nucleus.domain.WeaponId as ProfileWeaponId
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeContract
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileCausalScope
import kinetickk.features.profile.nucleus.protocol.ProfileCommandSource
import kinetickk.features.profile.nucleus.protocol.ProfileIntent
import kinetickk.features.profile.nucleus.protocol.ProfileQuery
import kinetickk.features.profile.nucleus.protocol.ProfileQueryResult
import kinetickk.features.profile.nucleus.state.PlayerProfileValues
import kinetickk.features.settings.SettingsDispatchResult
import kinetickk.features.settings.SettingsFeatureBall
import kinetickk.features.settings.nucleus.domain.DamageNumberFormat as SettingsDamageNumberFormat
import kinetickk.features.settings.nucleus.domain.DamageNumberSize as SettingsDamageNumberSize
import kinetickk.features.settings.nucleus.domain.ParticleDensity as SettingsParticleDensity
import kinetickk.features.settings.nucleus.domain.SettingsAdjustmentDirection
import kinetickk.features.settings.nucleus.domain.SettingsValues
import kinetickk.features.settings.nucleus.protocol.SettingsIntent
import kinetickk.features.settings.nucleus.protocol.SettingsQuery
import kinetickk.features.settings.nucleus.protocol.SettingsQueryResult
import kinetickk.flows.persistence.ProgressPersistenceFlowBall
import kinetickk.flows.persistence.ProgressCaptureProvenance
import kinetickk.flows.persistence.ProgressPersistenceSchema
import kinetickk.flows.persistence.model.PersistedProgress
import kinetickk.flows.persistence.model.PersistedSettings
import kinetickk.flows.persistence.resources.ProgressLoadRejection
import kinetickk.flows.persistence.resources.ProgressLoadResult
import kinetickk.flows.persistence.resources.ProgressResourceFailure
import kinetickk.flows.persistence.resources.ProgressStore
import kinetickk.flows.persistence.resources.createPlatformProgressStore
import kinetickk.flows.persistence.resources.quarantineBootstrapProgress
import kinetickk.flows.rebirth.RebirthCommittedCommand
import kinetickk.flows.rebirth.RebirthDispatchResult
import kinetickk.flows.rebirth.RebirthFlowBall
import kinetickk.flows.rebirth.nucleus.protocol.RebirthCausalScope
import kinetickk.flows.rebirth.nucleus.protocol.RebirthModuleCommand
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartCommandSource
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartContract
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartModuleCommand
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet

sealed interface BootstrapProgressStatus {
    data object Loaded : BootstrapProgressStatus
    data object NotFound : BootstrapProgressStatus
    data class Rejected(val reason: ProgressLoadRejection) : BootstrapProgressStatus
    data class OutcomeUnknown(val reason: ProgressResourceFailure) : BootstrapProgressStatus
}

/**
 * Explicit static composition root for the local multi-Ball application.
 *
 * Assembly is the only route table. It maps source-owned Game commands into target-owned
 * Settings/Profile/Audio protocols, publishes refreshed replicas back to Game, and captures the
 * two permanent authorities into the existing combined persistence record.
 */
class GameAssembly private constructor(
    private val game: GameFeatureBall,
    private val settings: SettingsFeatureBall,
    private val profile: ProfileFeatureBall,
    private val audio: AudioFeatureBall,
    private val progressPersistence: ProgressPersistenceFlowBall,
    private val rebirth: RebirthFlowBall,
    val bootstrapProgressStatus: BootstrapProgressStatus,
    private val interactionFxReducer: InteractionFxReducer,
) {
    private var observedSettingsRevision: ULong? = null
    private var observedProfileRevision: ULong? = null
    private val runSettlementRouter = RunSettlementRouter(
        execute = { command -> profile.execute(command) },
    )
    private val rebirthGameStartRouter = RebirthGameStartRouter(
        execute = { command ->
            observeDependencies()
            game.execute(command)
        },
    )

    fun dispatch(intent: GameIntent): GameDispatchResult {
        val resumeSettlement = runSettlementRouter.hasPending()
        val resumeRebirthCompletion = rebirth.hasRetainedCompletion()
        val resumeRebirthGameStart = rebirthGameStartRouter.hasPending()
        val preRootVisualFx = mutableListOf<VisualFxCue>()
        var permanentAuthorityChanged = false

        val resumedSettlement = resumeSettlement && resumeRetainedRunSettlement()
        if (runSettlementRouter.hasPending()) {
            return GameDispatchResult.AdmissionRejected(runSettlementRouter.backpressure())
        }
        if (resumedSettlement) {
            permanentAuthorityChanged = true
        }

        if (resumeRebirthCompletion) {
            rebirth.resumeRetainedCompletion()?.let { resumed ->
                permanentAuthorityChanged = routeRebirthDispatch(
                    dispatch = resumed,
                    followOnVisualFx = preRootVisualFx,
                    routeDepth = 1,
                ) || permanentAuthorityChanged
            }
        }
        if (rebirth.hasRetainedCompletion()) {
            if (permanentAuthorityChanged) {
                observeDependencies()
                persistCurrentAuthorities()
            }
            return GameDispatchResult.AdmissionRejected(
                AdmissionFailure.DeliveryBackpressure(
                    scope = REBIRTH_COMPLETION_SCOPE,
                    pending = 1,
                    capacity = 1,
                ),
            )
        }

        if (resumeRebirthGameStart) {
            rebirthGameStartRouter.resume()?.let { execution ->
                permanentAuthorityChanged = routeGameStartExecution(
                    execution = execution,
                    followOnVisualFx = preRootVisualFx,
                    routeDepth = 1,
                ) || permanentAuthorityChanged
            }
        }
        if (rebirthGameStartRouter.hasPending()) {
            if (permanentAuthorityChanged) {
                observeDependencies()
                persistCurrentAuthorities()
            }
            return GameDispatchResult.AdmissionRejected(rebirthGameStartRouter.backpressure())
        }
        if (rebirth.hasRetainedCompletion()) {
            if (permanentAuthorityChanged) {
                observeDependencies()
                persistCurrentAuthorities()
            }
            return GameDispatchResult.AdmissionRejected(
                AdmissionFailure.DeliveryBackpressure(
                    scope = REBIRTH_COMPLETION_SCOPE,
                    pending = 1,
                    capacity = 1,
                ),
            )
        }
        if (permanentAuthorityChanged) {
            observeDependencies()
            persistCurrentAuthorities()
        }

        val root = game.dispatch(intent)
        if (root !is GameDispatchResult.Committed) return root

        permanentAuthorityChanged = false
        val followOnVisualFx = preRootVisualFx
        root.commands.forEach { request ->
            permanentAuthorityChanged =
                route(
                    request = request,
                    sourceCommitRevision = root.sourceCommitRevision,
                    followOnVisualFx = followOnVisualFx,
                    routeDepth = 1,
                ) || permanentAuthorityChanged
        }
        observeDependencies()
        if (permanentAuthorityChanged) {
            persistCurrentAuthorities()
        }
        return root.copy(
            projectionRead = readGameProjection(),
            visualFxCues = (root.visualFxCues + followOnVisualFx).toImmutableList(),
        )
    }

    fun close() {
        audio.close()
    }

    internal fun applyVisualFx(cues: Iterable<VisualFxCue>) = interactionFxReducer.apply(cues)

    internal fun visualFxSnapshot(): VisualFxProjection = interactionFxReducer.snapshot()

    internal fun readCurrentGameProjection(): GameProjection = readGameProjection().payload

    internal fun readCurrentSettings(): SettingsValues = currentSettings()

    internal fun readCurrentProfile(): ProfileProjection = currentProfile()

    internal fun progressPersistenceStatus() = progressPersistence.status()

    internal fun rebirthStatus() = rebirth.status()

    internal fun runSettlementStatus(): RunSettlementRoutingStatus = runSettlementRouter.status()

    internal fun rebirthGameStartStatus(): RebirthGameStartRoutingStatus =
        rebirthGameStartRouter.status()

    private fun route(
        request: CommandRequest,
        sourceCommitRevision: ULong,
        followOnVisualFx: MutableList<VisualFxCue>,
        routeDepth: Int,
    ): Boolean {
        check(routeDepth <= MAX_STATIC_ROUTE_DEPTH) { "Static Assembly route depth exceeded" }
        return when (val command = request.payload) {
            is GameCommand.AdvanceAudio -> {
                val playbackSettings = currentSettings()
                audio.advance(
                    settings = AudioPlaybackSettings(
                        soundEnabled = playbackSettings.soundEnabled,
                        musicEnabled = playbackSettings.musicEnabled,
                        masterVolume = playbackSettings.masterVolume,
                    ),
                    realDeltaSeconds = command.realDeltaSeconds,
                    cues = command.cues.map(SoundCue::toAudioCue),
                )
                false
            }
            GameCommand.EnsureAudioUnlocked -> {
                audio.ensureUnlocked()
                false
            }
            is GameCommand.ChangeSettings ->
                settings.dispatch(command.change.toSettingsIntent()) is
                    SettingsDispatchResult.Committed
            is GameCommand.ChangeProfile -> {
                when (val change = command.change) {
                    is ProfileChange.ApplyRunOutcome -> routeRunSettlement(
                        command = request.toProfileRunOutcomeCommand(
                            sourceCommitRevision,
                            change,
                        ),
                    )
                    else -> profile.dispatch(change.toProfileIntent()) is
                        ProfileDispatchResult.Committed
                }
            }
            is GameCommand.BeginRebirth -> {
                routeRebirthStart(
                    command = request.toRebirthStartCommand(
                        sourceCommitRevision = sourceCommitRevision,
                        expectedLevel = command.expectedLevel,
                    ),
                    followOnVisualFx = followOnVisualFx,
                    routeDepth = routeDepth + 1,
                )
            }
        }
    }

    private fun routeRebirthStart(
        command: RebirthStartModuleCommand,
        followOnVisualFx: MutableList<VisualFxCue>,
        routeDepth: Int,
    ): Boolean {
        val start = rebirth.start(command)
        if (start !is RebirthDispatchResult.Committed) return false
        var profileChanged = false
        start.commands.forEach { command ->
            profileChanged = routeRebirthCommand(
                command = command,
                followOnVisualFx = followOnVisualFx,
                routeDepth = routeDepth,
            ) || profileChanged
        }
        return profileChanged
    }

    private fun routeRebirthCommand(
        command: RebirthCommittedCommand,
        followOnVisualFx: MutableList<VisualFxCue>,
        routeDepth: Int,
    ): Boolean {
        check(routeDepth <= MAX_STATIC_ROUTE_DEPTH) { "Static Assembly route depth exceeded" }
        return when (val payload = command.request.payload) {
            is RebirthModuleCommand.AdvanceProfileRebirth -> {
                val execution = profile.execute(payload.targetCommand)
                val followOnAuthorityChanged = routeRebirthDispatch(
                    dispatch = rebirth.accept(execution.moduleResult),
                    followOnVisualFx = followOnVisualFx,
                    routeDepth = routeDepth + 1,
                )
                execution.committed != null || followOnAuthorityChanged
            }
            is RebirthModuleCommand.StartGameRun -> {
                val execution = rebirthGameStartRouter.deliver(payload.targetCommand)
                    ?: return false
                routeGameStartExecution(
                    execution = execution,
                    followOnVisualFx = followOnVisualFx,
                    routeDepth = routeDepth,
                )
            }
        }
    }

    private fun routeGameStartExecution(
        execution: GameRunStartExecution,
        followOnVisualFx: MutableList<VisualFxCue>,
        routeDepth: Int,
    ): Boolean {
        check(routeDepth <= MAX_STATIC_ROUTE_DEPTH) { "Static Assembly route depth exceeded" }
        var permanentAuthorityChanged = false
        execution.committed?.let { startRun ->
            followOnVisualFx += startRun.visualFxCues
            startRun.commands.forEach { next ->
                permanentAuthorityChanged = route(
                    request = next,
                    sourceCommitRevision = startRun.sourceCommitRevision,
                    followOnVisualFx = followOnVisualFx,
                    routeDepth = routeDepth + 1,
                ) || permanentAuthorityChanged
            }
        }
        permanentAuthorityChanged = routeRebirthDispatch(
            dispatch = rebirth.accept(execution.moduleResult),
            followOnVisualFx = followOnVisualFx,
            routeDepth = routeDepth + 1,
        ) || permanentAuthorityChanged
        return permanentAuthorityChanged
    }

    private fun routeRebirthDispatch(
        dispatch: RebirthDispatchResult,
        followOnVisualFx: MutableList<VisualFxCue>,
        routeDepth: Int,
    ): Boolean {
        if (dispatch !is RebirthDispatchResult.Committed) return false
        var permanentAuthorityChanged = false
        dispatch.commands.forEach { command ->
            permanentAuthorityChanged = routeRebirthCommand(
                command = command,
                followOnVisualFx = followOnVisualFx,
                routeDepth = routeDepth,
            ) || permanentAuthorityChanged
        }
        return permanentAuthorityChanged
    }

    private fun routeRunSettlement(
        command: ProfileApplyRunOutcomeModuleCommand,
    ): Boolean = runSettlementRouter.deliver(command)

    private fun resumeRetainedRunSettlement(): Boolean = runSettlementRouter.resume()

    private fun observeDependencies() {
        val settingsRead =
            (settings.query(SettingsQuery.GetSettings) as SettingsQueryResult.Settings).value
        val profileRead =
            (profile.query(ProfileQuery.GetProfileProjection) as ProfileQueryResult.Projection).value
        if (
            observedSettingsRevision == settingsRead.consistencyStamp.commitRevision &&
            observedProfileRevision == profileRead.consistencyStamp.commitRevision
        ) {
            return
        }
        val observation = game.observeDependencies(
            settings = settingsRead.payload.toGameSettings(),
            profile = profileRead.payload.toGameReplica(),
            settingsSource = settingsRead.consistencyStamp,
            profileSource = profileRead.consistencyStamp,
        )
        if (observation is GameDispatchResult.Committed) {
            observedSettingsRevision = settingsRead.consistencyStamp.commitRevision
            observedProfileRevision = profileRead.consistencyStamp.commitRevision
        }
    }

    private fun readGameProjection() =
        (game.query(GameQuery.GetGameProjection) as GameQueryResult.Projection).value

    private fun currentSettings(): SettingsValues =
        (settings.query(SettingsQuery.GetSettings) as SettingsQueryResult.Settings).value.payload

    private fun currentProfile(): ProfileProjection =
        (profile.query(ProfileQuery.GetProfileProjection) as ProfileQueryResult.Projection).value.payload

    private fun persistCurrentAuthorities() {
        val settingsRead =
            (settings.query(SettingsQuery.GetSettings) as SettingsQueryResult.Settings).value
        val profileRead =
            (profile.query(ProfileQuery.GetProfileProjection) as ProfileQueryResult.Projection).value
        progressPersistence.persist(
            snapshot = combinedProgressSnapshot(settingsRead.payload, profileRead.payload),
            provenance = ProgressCaptureProvenance(
                profileSnapshot = profileRead.consistencyStamp,
                settingsSnapshot = settingsRead.consistencyStamp,
            ),
        )
    }

    private fun combinedProgressSnapshot(
        currentSettings: SettingsValues,
        currentProfile: ProfileProjection,
    ): PersistedProgress = PersistedProgress(
            matter = currentProfile.matter,
            lifetimeMatter = currentProfile.lifetimeMatter,
            coreShapeIndex = currentProfile.selectedCoreShape.toPersistenceCode(),
            selectedWeaponIndex = currentProfile.selectedWeapon.toPersistenceCode(),
            unlockedWeaponIndices = currentProfile.unlockedWeapons
                .mapTo(mutableSetOf(), ProfileWeaponId::toPersistenceCode),
            metaLevels = currentProfile.metaRanks,
            discoveredItemIds = currentProfile.discoveredItemIds,
            settings = currentSettings.toPersistedSettings(),
            rebirthLevel = currentProfile.rebirthLevel,
            highestClearedRebirth = currentProfile.highestClearedRebirth,
        )

    companion object {
        fun create(
            progressStore: ProgressStore = createPlatformProgressStore(),
            seed: Int = 731_991,
        ): GameAssembly = create(
            progressStore = progressStore,
            audioBall = AudioFeatureBall.create(),
            seed = seed,
        )

        internal fun create(
            progressStore: ProgressStore,
            audioBall: AudioFeatureBall,
            seed: Int = 731_991,
        ): GameAssembly {
            val rawLoadResult = runCatching(progressStore::load).getOrElse {
                ProgressLoadResult.OutcomeUnknown(ProgressResourceFailure.PROVIDER_READ_FAILED)
            }
            val loadResult = when (rawLoadResult) {
                is ProgressLoadResult.Loaded -> quarantineBootstrapProgress(rawLoadResult.progress)
                ProgressLoadResult.NotFound -> ProgressLoadResult.NotFound
                is ProgressLoadResult.Rejected -> rawLoadResult
                is ProgressLoadResult.OutcomeUnknown -> rawLoadResult
            }
            val bootstrap = (loadResult as? ProgressLoadResult.Loaded)?.progress
            val status = when (loadResult) {
                is ProgressLoadResult.Loaded -> BootstrapProgressStatus.Loaded
                ProgressLoadResult.NotFound -> BootstrapProgressStatus.NotFound
                is ProgressLoadResult.Rejected -> BootstrapProgressStatus.Rejected(loadResult.reason)
                is ProgressLoadResult.OutcomeUnknown -> BootstrapProgressStatus.OutcomeUnknown(loadResult.reason)
            }
            val assembly = GameAssembly(
                game = GameFeatureBall.create(
                    bootstrapProgress = bootstrap?.toGameBootstrapSnapshot(),
                    seed = seed,
                ),
                settings = SettingsFeatureBall(
                    bootstrap?.settings?.toSettingsValues() ?: SettingsValues(),
                ),
                profile = ProfileFeatureBall.create(bootstrap.toProfileValues()),
                audio = audioBall,
                progressPersistence = ProgressPersistenceFlowBall.create(progressStore),
                rebirth = RebirthFlowBall.create(),
                bootstrapProgressStatus = status,
                interactionFxReducer = InteractionFxReducer(seed),
            )
            assembly.observeDependencies()
            return assembly
        }

        private const val MAX_STATIC_ROUTE_DEPTH = 4
        private const val REBIRTH_COMPLETION_SCOPE =
            "kinetickk.local/GameAssembly/rebirth-completion"
    }
}

private fun SettingsChange.toSettingsIntent(): SettingsIntent = when (this) {
    SettingsChange.ToggleMute -> SettingsIntent.MuteToggled
    SettingsChange.ToggleSound -> SettingsIntent.SoundToggled
    SettingsChange.ToggleMusic -> SettingsIntent.MusicToggled
    is SettingsChange.AdjustMasterVolume ->
        SettingsIntent.MasterVolumeAdjusted(direction.toSettingsDirection())
    is SettingsChange.AdjustSimulationSpeed ->
        SettingsIntent.SimulationSpeedAdjusted(direction.toSettingsDirection())
    is SettingsChange.AdjustTextScale ->
        SettingsIntent.TextScaleAdjusted(direction.toSettingsDirection())
    SettingsChange.ToggleScreenShake -> SettingsIntent.ScreenShakeToggled
    is SettingsChange.AdjustParticleDensity ->
        SettingsIntent.ParticleDensityAdjusted(direction.toSettingsDirection())
    SettingsChange.ToggleDamageNumbers -> SettingsIntent.DamageNumbersToggled
    is SettingsChange.AdjustDamageNumberSize ->
        SettingsIntent.DamageNumberSizeAdjusted(direction.toSettingsDirection())
    is SettingsChange.AdjustDamageNumberFormat ->
        SettingsIntent.DamageNumberFormatAdjusted(direction.toSettingsDirection())
    is SettingsChange.AdjustDamageNumberTierThreshold ->
        SettingsIntent.DamageNumberTierThresholdAdjusted(direction.toSettingsDirection())
}

private fun Int.toSettingsDirection(): SettingsAdjustmentDirection =
    if (this < 0) SettingsAdjustmentDirection.DECREASE else SettingsAdjustmentDirection.INCREASE

private fun ProfileChange.toProfileIntent(): ProfileIntent = when (this) {
    is ProfileChange.PurchaseMetaUpgrade ->
        ProfileIntent.PurchaseMetaUpgrade(upgrade.toProfileMetaUpgradeId())
    is ProfileChange.PurchaseOrSelectWeapon ->
        ProfileIntent.PurchaseOrSelectWeapon(weapon.toProfileWeaponId())
    is ProfileChange.SelectCoreShape ->
        ProfileIntent.SelectCoreShape(shape.toProfileCoreShape())
    is ProfileChange.RecordItemDiscovery ->
        ProfileIntent.RecordItemDiscoveries(listOf(itemId).toImmutableList())
    is ProfileChange.ApplyRunOutcome -> error("Run outcomes use Profile's target-owned contract")
}

internal fun CommandRequest.toProfileRunOutcomeCommand(
    sourceCommitRevision: ULong,
    change: ProfileChange.ApplyRunOutcome,
): ProfileApplyRunOutcomeModuleCommand = ProfileApplyRunOutcomeModuleCommand(
    commandSource = ProfileCommandSource(
        sourceBallInstanceId = GameProjection.BALL_INSTANCE_ID,
        sourceCommitRevision = sourceCommitRevision,
        sourceOrdinal = sourceOrdinal,
        sourceOperationId = semanticHandle.operationId.value,
        sourceOutputKind = ProfileApplyRunOutcomeContract.SOURCE_OUTPUT_KIND,
        sourceLocalOrdinalOrName = semanticHandle.localOrdinalOrName,
    ),
    causalBudgetScope = ProfileCausalScope(
        ownerBallInstanceId = GameProjection.BALL_INSTANCE_ID,
        operationId = semanticHandle.operationId.value,
    ),
    causalDepth = ProfileApplyRunOutcomeContract.COMMAND_CAUSAL_DEPTH,
    matterEarned = change.matterEarned,
    clearedRebirthLevel = change.clearedRebirthLevel,
).also {
    check(semanticHandle.outputKind == kinetickk.features.game.nucleus.protocol.GameOutputKind.CHANGE_PROFILE) {
        "Run settlement must originate from Game's CHANGE_PROFILE output"
    }
}

internal fun CommandRequest.toRebirthStartCommand(
    sourceCommitRevision: ULong,
    expectedLevel: Int,
): RebirthStartModuleCommand = RebirthStartModuleCommand(
    commandSource = RebirthStartCommandSource(
        sourceBallInstanceId = GameProjection.BALL_INSTANCE_ID,
        sourceCommitRevision = sourceCommitRevision,
        sourceOrdinal = sourceOrdinal,
        sourceOperationId = semanticHandle.operationId.value,
        sourceOutputKind = semanticHandle.outputKind.name,
        sourceLocalOrdinalOrName = semanticHandle.localOrdinalOrName,
    ),
    causalBudgetScope = RebirthCausalScope(
        ownerBallInstanceId = GameProjection.BALL_INSTANCE_ID,
        operationId = semanticHandle.operationId.value,
    ),
    causalDepth = RebirthStartContract.COMMAND_CAUSAL_DEPTH,
    expectedLevel = expectedLevel,
).also {
    check(semanticHandle.outputKind == GameOutputKind.BEGIN_REBIRTH) {
        "Rebirth start must originate from Game's BEGIN_REBIRTH output"
    }
}

private fun PersistedProgress?.toProfileValues(): PlayerProfileValues {
    if (this == null) return PlayerProfileValues()
    val unlocked = unlockedWeaponIndices
        .mapNotNullTo(mutableSetOf(), Int::toProfileWeaponIdOrNull)
        .apply { add(ProfileWeaponId.FLUX_WAKE) }
    val selectedCandidate = selectedWeaponIndex.toProfileWeaponIdOrNull()
        ?: ProfileWeaponId.FLUX_WAKE
    val selected = selectedCandidate.takeIf { it in unlocked } ?: ProfileWeaponId.FLUX_WAKE
    val shapeCandidate = coreShapeIndex.toProfileCoreShapeOrNull() ?: ProfileCoreShape.ORB
    val shape = when (shapeCandidate) {
        ProfileCoreShape.ORB -> shapeCandidate
        ProfileCoreShape.PRISM -> shapeCandidate.takeIf { lifetimeMatter >= 25L }
        ProfileCoreShape.SHARD -> shapeCandidate.takeIf { lifetimeMatter >= 90L }
    } ?: ProfileCoreShape.ORB
    return PlayerProfileValues(
        matter = matter,
        lifetimeMatter = lifetimeMatter,
        metaRanks = metaLevels,
        selectedWeapon = selected,
        unlockedWeapons = unlocked,
        selectedCoreShape = shape,
        discoveredItemIds = discoveredItemIds,
        rebirthLevel = rebirthLevel,
        highestClearedRebirth = highestClearedRebirth,
    )
}

private fun PersistedProgress.toGameBootstrapSnapshot(): GameBootstrapSnapshot =
    GameBootstrapSnapshot(
        matter = matter,
        lifetimeMatter = lifetimeMatter,
        coreShapeIndex = coreShapeIndex,
        selectedWeaponIndex = selectedWeaponIndex,
        unlockedWeaponIndices = unlockedWeaponIndices,
        metaLevels = metaLevels,
        discoveredItemIds = discoveredItemIds,
        settings = settings.toSettingsValues().toGameSettings(),
        rebirthLevel = rebirthLevel,
        highestClearedRebirth = highestClearedRebirth,
    )

private fun ProfileProjection.toGameReplica(): GameProfileReplica = GameProfileReplica(
    matter = matter,
    lifetimeMatter = lifetimeMatter,
    coreShape = selectedCoreShape.toGameCoreShape(),
    selectedWeapon = selectedWeapon.toGameWeaponId(),
    unlockedWeapons = unlockedWeapons.map(ProfileWeaponId::toGameWeaponId).toImmutableSet(),
    metaRanks = metaRanks,
    discoveredItemIds = discoveredItemIds.toImmutableSet(),
    rebirthLevel = rebirthLevel,
    highestClearedRebirth = highestClearedRebirth,
    activeRebirthProfile = runConfiguration.rebirthProfile.toGameRebirthProfile(),
    nextRebirthProfile = runConfiguration.nextRebirthProfile.toGameRebirthProfile(),
)

private fun ProfileRebirthProfile.toGameRebirthProfile(): RebirthProfile = RebirthProfile(
    tier = tier,
    directive = directive.toGameRebirthDirective(),
    openingEnemyCount = openingEnemyCount,
    enemyCapMultiplier = enemyCapMultiplier,
    spawnRateMultiplier = spawnRateMultiplier,
    enemyHealthMultiplier = enemyHealthMultiplier,
    enemySpeedMultiplier = enemySpeedMultiplier,
    incomingDamageMultiplier = incomingDamageMultiplier,
    eliteRateMultiplier = eliteRateMultiplier,
    threatTimeOffsetSeconds = threatTimeOffsetSeconds,
    playerPowerMultiplier = playerPowerMultiplier,
    playerIntegrityBonus = playerIntegrityBonus,
    matterGainMultiplier = matterGainMultiplier,
    bonusRerolls = bonusRerolls,
)

private fun ProfileRebirthDirective.toGameRebirthDirective(): RebirthDirective = when (this) {
    ProfileRebirthDirective.BASELINE -> RebirthDirective.BASELINE
    ProfileRebirthDirective.SWARM -> RebirthDirective.SWARM
    ProfileRebirthDirective.FORTIFIED -> RebirthDirective.FORTIFIED
    ProfileRebirthDirective.OVERCLOCKED -> RebirthDirective.OVERCLOCKED
}

private fun SettingsValues.toGameSettings(): GameSettings = GameSettings(
    soundEnabled = soundEnabled,
    musicEnabled = musicEnabled,
    masterVolume = masterVolume,
    simulationSpeed = simulationSpeed,
    textScale = textScale,
    screenShake = screenShake,
    particleDensity = particleDensity.toGameParticleDensity(),
    damageNumbers = damageNumbers,
    damageNumberSize = damageNumberSize.toGameDamageNumberSize(),
    damageNumberFormat = damageNumberFormat.toGameDamageNumberFormat(),
    damageNumberTierThreshold = damageNumberTierThreshold,
)

internal fun SettingsValues.toPersistedSettings(): PersistedSettings = PersistedSettings(
    soundEnabled = soundEnabled,
    musicEnabled = musicEnabled,
    masterVolume = masterVolume,
    simulationSpeed = simulationSpeed,
    textScale = textScale,
    screenShake = screenShake,
    particleDensityCode = when (particleDensity) {
        SettingsParticleDensity.LOW ->
            ProgressPersistenceSchema.PARTICLE_DENSITY_LOW_CODE
        SettingsParticleDensity.NORMAL ->
            ProgressPersistenceSchema.PARTICLE_DENSITY_NORMAL_CODE
        SettingsParticleDensity.HIGH ->
            ProgressPersistenceSchema.PARTICLE_DENSITY_HIGH_CODE
    },
    damageNumbers = damageNumbers,
    damageNumberSizeCode = when (damageNumberSize) {
        SettingsDamageNumberSize.SMALL ->
            ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_SMALL_CODE
        SettingsDamageNumberSize.NORMAL ->
            ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_NORMAL_CODE
        SettingsDamageNumberSize.LARGE ->
            ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_LARGE_CODE
        SettingsDamageNumberSize.HUGE ->
            ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_HUGE_CODE
    },
    damageNumberFormatCode = when (damageNumberFormat) {
        SettingsDamageNumberFormat.COMPACT ->
            ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_COMPACT_CODE
        SettingsDamageNumberFormat.FULL ->
            ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_FULL_CODE
    },
    damageNumberTierThreshold = damageNumberTierThreshold,
)

internal fun PersistedSettings.toSettingsValues(): SettingsValues = SettingsValues(
    soundEnabled = soundEnabled,
    musicEnabled = musicEnabled,
    masterVolume = masterVolume,
    simulationSpeed = simulationSpeed,
    textScale = textScale,
    screenShake = screenShake,
    particleDensity = when (particleDensityCode) {
        ProgressPersistenceSchema.PARTICLE_DENSITY_LOW_CODE ->
            SettingsParticleDensity.LOW
        ProgressPersistenceSchema.PARTICLE_DENSITY_NORMAL_CODE ->
            SettingsParticleDensity.NORMAL
        ProgressPersistenceSchema.PARTICLE_DENSITY_HIGH_CODE ->
            SettingsParticleDensity.HIGH
        else -> SettingsParticleDensity.NORMAL
    },
    damageNumbers = damageNumbers,
    damageNumberSize = when (damageNumberSizeCode) {
        ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_SMALL_CODE ->
            SettingsDamageNumberSize.SMALL
        ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_NORMAL_CODE ->
            SettingsDamageNumberSize.NORMAL
        ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_LARGE_CODE ->
            SettingsDamageNumberSize.LARGE
        ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_HUGE_CODE ->
            SettingsDamageNumberSize.HUGE
        else -> SettingsDamageNumberSize.NORMAL
    },
    damageNumberFormat = when (damageNumberFormatCode) {
        ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_COMPACT_CODE ->
            SettingsDamageNumberFormat.COMPACT
        ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_FULL_CODE ->
            SettingsDamageNumberFormat.FULL
        else -> SettingsDamageNumberFormat.COMPACT
    },
    damageNumberTierThreshold = damageNumberTierThreshold,
).normalized()

private fun ProfileCoreShape.toPersistenceCode(): Int = when (this) {
    ProfileCoreShape.ORB -> ProgressPersistenceSchema.CORE_SHAPE_ORB_CODE
    ProfileCoreShape.PRISM -> ProgressPersistenceSchema.CORE_SHAPE_PRISM_CODE
    ProfileCoreShape.SHARD -> ProgressPersistenceSchema.CORE_SHAPE_SHARD_CODE
}

private fun Int.toProfileCoreShapeOrNull(): ProfileCoreShape? = when (this) {
    ProgressPersistenceSchema.CORE_SHAPE_ORB_CODE -> ProfileCoreShape.ORB
    ProgressPersistenceSchema.CORE_SHAPE_PRISM_CODE -> ProfileCoreShape.PRISM
    ProgressPersistenceSchema.CORE_SHAPE_SHARD_CODE -> ProfileCoreShape.SHARD
    else -> null
}

private fun ProfileWeaponId.toPersistenceCode(): Int = when (this) {
    ProfileWeaponId.FLUX_WAKE -> ProgressPersistenceSchema.WEAPON_FLUX_WAKE_CODE
    ProfileWeaponId.MORNINGSTAR -> ProgressPersistenceSchema.WEAPON_MORNINGSTAR_CODE
    ProfileWeaponId.PHASE_LATTICE -> ProgressPersistenceSchema.WEAPON_PHASE_LATTICE_CODE
    ProfileWeaponId.NULL_LANCE -> ProgressPersistenceSchema.WEAPON_NULL_LANCE_CODE
    ProfileWeaponId.GRAVITY_MINES -> ProgressPersistenceSchema.WEAPON_GRAVITY_MINES_CODE
    ProfileWeaponId.ION_SWARM -> ProgressPersistenceSchema.WEAPON_ION_SWARM_CODE
    ProfileWeaponId.RIFT_BLADES -> ProgressPersistenceSchema.WEAPON_RIFT_BLADES_CODE
    ProfileWeaponId.ARC_COIL -> ProgressPersistenceSchema.WEAPON_ARC_COIL_CODE
    ProfileWeaponId.QUASAR_CANNON -> ProgressPersistenceSchema.WEAPON_QUASAR_CANNON_CODE
    ProfileWeaponId.ENTROPY_FIELD -> ProgressPersistenceSchema.WEAPON_ENTROPY_FIELD_CODE
    ProfileWeaponId.SINGULARITY_SPEAR -> ProgressPersistenceSchema.WEAPON_SINGULARITY_SPEAR_CODE
    ProfileWeaponId.PRISM_RELAY -> ProgressPersistenceSchema.WEAPON_PRISM_RELAY_CODE
}

private fun Int.toProfileWeaponIdOrNull(): ProfileWeaponId? = when (this) {
    ProgressPersistenceSchema.WEAPON_FLUX_WAKE_CODE -> ProfileWeaponId.FLUX_WAKE
    ProgressPersistenceSchema.WEAPON_MORNINGSTAR_CODE -> ProfileWeaponId.MORNINGSTAR
    ProgressPersistenceSchema.WEAPON_PHASE_LATTICE_CODE -> ProfileWeaponId.PHASE_LATTICE
    ProgressPersistenceSchema.WEAPON_NULL_LANCE_CODE -> ProfileWeaponId.NULL_LANCE
    ProgressPersistenceSchema.WEAPON_GRAVITY_MINES_CODE -> ProfileWeaponId.GRAVITY_MINES
    ProgressPersistenceSchema.WEAPON_ION_SWARM_CODE -> ProfileWeaponId.ION_SWARM
    ProgressPersistenceSchema.WEAPON_RIFT_BLADES_CODE -> ProfileWeaponId.RIFT_BLADES
    ProgressPersistenceSchema.WEAPON_ARC_COIL_CODE -> ProfileWeaponId.ARC_COIL
    ProgressPersistenceSchema.WEAPON_QUASAR_CANNON_CODE -> ProfileWeaponId.QUASAR_CANNON
    ProgressPersistenceSchema.WEAPON_ENTROPY_FIELD_CODE -> ProfileWeaponId.ENTROPY_FIELD
    ProgressPersistenceSchema.WEAPON_SINGULARITY_SPEAR_CODE -> ProfileWeaponId.SINGULARITY_SPEAR
    ProgressPersistenceSchema.WEAPON_PRISM_RELAY_CODE -> ProfileWeaponId.PRISM_RELAY
    else -> null
}

private fun SoundCue.toAudioCue(): AudioCue = when (this) {
    SoundCue.UI_CLICK -> AudioCue.UI_CLICK
    SoundCue.DASH -> AudioCue.DASH
    SoundCue.WEAPON_LIGHT -> AudioCue.WEAPON_LIGHT
    SoundCue.WEAPON_HEAVY -> AudioCue.WEAPON_HEAVY
    SoundCue.IMPACT -> AudioCue.IMPACT
    SoundCue.ENEMY_DESTROYED -> AudioCue.ENEMY_DESTROYED
    SoundCue.PICKUP -> AudioCue.PICKUP
    SoundCue.LEVEL_UP -> AudioCue.LEVEL_UP
    SoundCue.OVERHEAT -> AudioCue.OVERHEAT
    SoundCue.RECOVERED -> AudioCue.RECOVERED
    SoundCue.HURT -> AudioCue.HURT
    SoundCue.OVERDRIVE -> AudioCue.OVERDRIVE
    SoundCue.WEAPON_ACQUIRED -> AudioCue.WEAPON_ACQUIRED
    SoundCue.PURCHASE -> AudioCue.PURCHASE
    SoundCue.GAME_OVER -> AudioCue.GAME_OVER
    SoundCue.VICTORY -> AudioCue.VICTORY
}

private fun MetaUpgradeId.toProfileMetaUpgradeId(): ProfileMetaUpgradeId = when (this) {
    MetaUpgradeId.CORE_INTEGRITY -> ProfileMetaUpgradeId.CORE_INTEGRITY
    MetaUpgradeId.KINETIC_AMPLIFIER -> ProfileMetaUpgradeId.KINETIC_AMPLIFIER
    MetaUpgradeId.MAGNETIC_RESONANCE -> ProfileMetaUpgradeId.MAGNETIC_RESONANCE
    MetaUpgradeId.CRYO_VENTS -> ProfileMetaUpgradeId.CRYO_VENTS
    MetaUpgradeId.DASH_CAPACITOR -> ProfileMetaUpgradeId.DASH_CAPACITOR
    MetaUpgradeId.SALVAGE_PROTOCOL -> ProfileMetaUpgradeId.SALVAGE_PROTOCOL
    MetaUpgradeId.DATA_ARCHIVE -> ProfileMetaUpgradeId.DATA_ARCHIVE
    MetaUpgradeId.ARMORY_LICENSE -> ProfileMetaUpgradeId.ARMORY_LICENSE
}

private fun WeaponId.toProfileWeaponId(): ProfileWeaponId = when (this) {
    WeaponId.FLUX_WAKE -> ProfileWeaponId.FLUX_WAKE
    WeaponId.MORNINGSTAR -> ProfileWeaponId.MORNINGSTAR
    WeaponId.PHASE_LATTICE -> ProfileWeaponId.PHASE_LATTICE
    WeaponId.NULL_LANCE -> ProfileWeaponId.NULL_LANCE
    WeaponId.GRAVITY_MINES -> ProfileWeaponId.GRAVITY_MINES
    WeaponId.ION_SWARM -> ProfileWeaponId.ION_SWARM
    WeaponId.RIFT_BLADES -> ProfileWeaponId.RIFT_BLADES
    WeaponId.ARC_COIL -> ProfileWeaponId.ARC_COIL
    WeaponId.QUASAR_CANNON -> ProfileWeaponId.QUASAR_CANNON
    WeaponId.ENTROPY_FIELD -> ProfileWeaponId.ENTROPY_FIELD
    WeaponId.SINGULARITY_SPEAR -> ProfileWeaponId.SINGULARITY_SPEAR
    WeaponId.PRISM_RELAY -> ProfileWeaponId.PRISM_RELAY
}

private fun CoreShape.toProfileCoreShape(): ProfileCoreShape = when (this) {
    CoreShape.ORB -> ProfileCoreShape.ORB
    CoreShape.PRISM -> ProfileCoreShape.PRISM
    CoreShape.SHARD -> ProfileCoreShape.SHARD
}

private fun ProfileCoreShape.toGameCoreShape(): CoreShape = when (this) {
    ProfileCoreShape.ORB -> CoreShape.ORB
    ProfileCoreShape.PRISM -> CoreShape.PRISM
    ProfileCoreShape.SHARD -> CoreShape.SHARD
}

private fun ProfileWeaponId.toGameWeaponId(): WeaponId = when (this) {
    ProfileWeaponId.FLUX_WAKE -> WeaponId.FLUX_WAKE
    ProfileWeaponId.MORNINGSTAR -> WeaponId.MORNINGSTAR
    ProfileWeaponId.PHASE_LATTICE -> WeaponId.PHASE_LATTICE
    ProfileWeaponId.NULL_LANCE -> WeaponId.NULL_LANCE
    ProfileWeaponId.GRAVITY_MINES -> WeaponId.GRAVITY_MINES
    ProfileWeaponId.ION_SWARM -> WeaponId.ION_SWARM
    ProfileWeaponId.RIFT_BLADES -> WeaponId.RIFT_BLADES
    ProfileWeaponId.ARC_COIL -> WeaponId.ARC_COIL
    ProfileWeaponId.QUASAR_CANNON -> WeaponId.QUASAR_CANNON
    ProfileWeaponId.ENTROPY_FIELD -> WeaponId.ENTROPY_FIELD
    ProfileWeaponId.SINGULARITY_SPEAR -> WeaponId.SINGULARITY_SPEAR
    ProfileWeaponId.PRISM_RELAY -> WeaponId.PRISM_RELAY
}

private fun SettingsParticleDensity.toGameParticleDensity(): ParticleDensity = when (this) {
    SettingsParticleDensity.LOW -> ParticleDensity.LOW
    SettingsParticleDensity.NORMAL -> ParticleDensity.NORMAL
    SettingsParticleDensity.HIGH -> ParticleDensity.HIGH
}

private fun SettingsDamageNumberSize.toGameDamageNumberSize(): DamageNumberSize = when (this) {
    SettingsDamageNumberSize.SMALL -> DamageNumberSize.SMALL
    SettingsDamageNumberSize.NORMAL -> DamageNumberSize.NORMAL
    SettingsDamageNumberSize.LARGE -> DamageNumberSize.LARGE
    SettingsDamageNumberSize.HUGE -> DamageNumberSize.HUGE
}

private fun SettingsDamageNumberFormat.toGameDamageNumberFormat(): DamageNumberFormat = when (this) {
    SettingsDamageNumberFormat.COMPACT -> DamageNumberFormat.COMPACT
    SettingsDamageNumberFormat.FULL -> DamageNumberFormat.FULL
}
