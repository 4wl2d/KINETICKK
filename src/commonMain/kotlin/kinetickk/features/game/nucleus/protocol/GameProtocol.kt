// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus.protocol

import kinetickk.application.runtime.BusinessRejection
import kinetickk.application.runtime.ConsistencyStamp
import kinetickk.features.game.nucleus.CoreShape
import kinetickk.features.game.nucleus.GameSettings
import kinetickk.features.game.nucleus.MetaUpgradeId
import kinetickk.features.game.nucleus.RebirthProfile
import kinetickk.features.game.nucleus.UiScreen
import kinetickk.features.game.nucleus.WeaponId
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.game.nucleus.read.ReadResult
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kotlin.jvm.JvmInline

sealed interface GamePulse

/**
 * Target-owned public contract for the Rebirth Flow's request to start one Game run.
 *
 * The source tuple is intentionally scalar instead of importing a Flow-owned envelope type. It
 * still carries the complete committed source identity required to correlate the returned result.
 */
data class GameRunStartCommandSource(
    val sourceBallInstanceId: String,
    val sourceCommitRevision: ULong,
    val sourceOrdinal: UInt,
    val sourceOperationId: ULong,
    val sourceOutputKind: String,
    val sourceLocalOrdinalOrName: String,
)

data class GameRunStartCausalScope(
    val ownerBallInstanceId: String,
    val operationId: ULong,
)

data class GameRunConfigurationReference(
    val profileBallInstanceId: String,
    val profileCommitRevision: ULong,
    val profileStateSchemaVersion: Int,
    val rebirthLevel: Int,
)

data class GameRunStartModuleCommand(
    val commandSource: GameRunStartCommandSource,
    val causalBudgetScope: GameRunStartCausalScope,
    val causalDepth: Int,
    val runConfigurationReference: GameRunConfigurationReference,
) : GamePulse

enum class GameRunStartAuthority {
    LOCAL_GAME,
}

enum class GameRunStartProtocolVersion {
    V1_0_0,
}

data class GameRunStartResultProvenance(
    val authority: GameRunStartAuthority,
    val protocolVersion: GameRunStartProtocolVersion,
)

enum class GameRunStartRejectionReason {
    INVALID_COMMAND_SOURCE,
    COMMAND_SOURCE_CONFLICT,
    STALE_COMMAND_SOURCE,
    INVALID_CAUSAL_CONTEXT,
    INVALID_RUN_CONFIGURATION_REFERENCE,
    PROFILE_REFERENCE_NOT_CURRENT,
    REBIRTH_LEVEL_MISMATCH,
    TARGET_DECISION_REJECTED,
    TARGET_ADMISSION_REJECTED,
}

sealed interface GameRunStartModuleResult {
    val commandSource: GameRunStartCommandSource
    val causalBudgetScope: GameRunStartCausalScope
    val causalDepth: Int
    val provenance: GameRunStartResultProvenance

    data class Started(
        override val commandSource: GameRunStartCommandSource,
        override val causalBudgetScope: GameRunStartCausalScope,
        override val causalDepth: Int,
        override val provenance: GameRunStartResultProvenance,
        val gameCommitRevision: ULong,
    ) : GameRunStartModuleResult

    data class Rejected(
        override val commandSource: GameRunStartCommandSource,
        override val causalBudgetScope: GameRunStartCausalScope,
        override val causalDepth: Int,
        override val provenance: GameRunStartResultProvenance,
        val reason: GameRunStartRejectionReason,
    ) : GameRunStartModuleResult
}

object GameRunStartContract {
    const val SOURCE_BALL_INSTANCE_ID = "kinetickk.local/RebirthFlow/local-player"
    const val CAUSAL_SCOPE_OWNER_BALL_INSTANCE_ID = "kinetickk.local/Game/local-player"
    const val SOURCE_OUTPUT_KIND = "GAME_START_RUN"
    const val SOURCE_LOCAL_ORDINAL_OR_NAME = "game-start-run"
    const val COMMAND_CAUSAL_DEPTH = 5
    const val RESULT_CAUSAL_DEPTH = 6

    val RESULT_PROVENANCE = GameRunStartResultProvenance(
        authority = GameRunStartAuthority.LOCAL_GAME,
        protocolVersion = GameRunStartProtocolVersion.V1_0_0,
    )
}

sealed interface GameQuery {
    data object GetGameProjection : GameQuery
}

sealed interface GameQueryResult {
    data class Projection(val value: ReadResult<GameProjection>) : GameQueryResult
}

sealed interface GameIntent : GamePulse {
    data class FrameElapsed(val realDeltaSeconds: Float) : GameIntent
    data class ViewportChanged(val width: Float, val height: Float, val density: Float) : GameIntent
    data class PointerMoved(val x: Float, val y: Float, val active: Boolean = true) : GameIntent
    data class PointerPressed(val x: Float, val y: Float) : GameIntent
    data object PointerReleased : GameIntent
    data class BrakeChanged(val source: BrakeSource, val active: Boolean) : GameIntent
    data object DashRequested : GameIntent
    data object PauseToggled : GameIntent
    data object EscapeRequested : GameIntent
    data class ScreenOpenRequested(val screen: UiScreen) : GameIntent
    data object MuteToggled : GameIntent
    data class ChoiceSelected(val index: Int) : GameIntent
    data object ChoicesRerolled : GameIntent
    data object EnterPressed : GameIntent
    data object RunStartRequested : GameIntent
    data object ReturnToMenuRequested : GameIntent
    data object RebirthRequested : GameIntent
    data class CoreShapeSelected(val shape: CoreShape) : GameIntent
    data class MetaUpgradePurchaseRequested(val upgrade: MetaUpgradeId) : GameIntent
    data class WeaponPurchaseOrEquipRequested(val weapon: WeaponId) : GameIntent
    data object UserGestureObserved : GameIntent
}

enum class BrakeSource { KEYBOARD, SECONDARY_POINTER }

enum class SoundCue {
    UI_CLICK,
    DASH,
    WEAPON_LIGHT,
    WEAPON_HEAVY,
    IMPACT,
    ENEMY_DESTROYED,
    PICKUP,
    LEVEL_UP,
    OVERHEAT,
    RECOVERED,
    HURT,
    OVERDRIVE,
    WEAPON_ACQUIRED,
    PURCHASE,
    GAME_OVER,
    VICTORY,
}

/** Trusted, field-minimized replicas observed from the owning Settings and Profile Balls. */
data class GameProfileReplica(
    val matter: Long,
    val lifetimeMatter: Long,
    val coreShape: CoreShape,
    val selectedWeapon: WeaponId,
    val unlockedWeapons: ImmutableSet<WeaponId>,
    val metaRanks: ImmutableList<Int>,
    val discoveredItemIds: ImmutableSet<Int>,
    val rebirthLevel: Int,
    val highestClearedRebirth: Int,
    val activeRebirthProfile: RebirthProfile,
    val nextRebirthProfile: RebirthProfile,
)

/** Closed identities of the two authority snapshots captured by Game. */
enum class GameDependencySource {
    SETTINGS,
    PROFILE,
}

object GameDependencyContract {
    const val SETTINGS_BALL_INSTANCE_ID = "kinetickk.local/Settings/local-player"
    const val SETTINGS_STATE_SCHEMA_VERSION = 1
    const val PROFILE_BALL_INSTANCE_ID = "kinetickk.local/Profile/local-player"
    const val PROFILE_STATE_SCHEMA_VERSION = 1
}

sealed interface GameFact : GamePulse {
    data class DependenciesObserved(
        val settings: GameSettings,
        val profile: GameProfileReplica,
        val settingsSource: ConsistencyStamp = ConsistencyStamp(
            ballInstanceId = GameDependencyContract.SETTINGS_BALL_INSTANCE_ID,
            commitRevision = 0uL,
            stateSchemaVersion = GameDependencyContract.SETTINGS_STATE_SCHEMA_VERSION,
        ),
        val profileSource: ConsistencyStamp = ConsistencyStamp(
            ballInstanceId = GameDependencyContract.PROFILE_BALL_INSTANCE_ID,
            commitRevision = 0uL,
            stateSchemaVersion = GameDependencyContract.PROFILE_STATE_SCHEMA_VERSION,
        ),
    ) : GameFact
}

/** A trusted semantic operation identity reserved before the Nucleus evaluates a Pulse. */
@JvmInline
value class OperationId(val value: ULong)

/** The closed output-kind names owned by Game protocol 2.0.0. */
enum class GameOutputKind {
    GAME_PROJECTION_CHANGED,
    ADVANCE_AUDIO,
    ENSURE_AUDIO_UNLOCKED,
    CHANGE_SETTINGS,
    CHANGE_PROFILE,
    BEGIN_REBIRTH,
}

data class SemanticHandle(
    val operationId: OperationId,
    val outputKind: GameOutputKind,
    val localOrdinalOrName: String,
)

/** Canonical closed SemanticOutput family for the Game Ball. */
sealed interface SemanticOutput {
    val semanticHandle: SemanticHandle
    val sourceOrdinal: UInt
}

data class ProjectionOutput(
    override val semanticHandle: SemanticHandle,
    override val sourceOrdinal: UInt,
    val payload: GameProjectionPayload,
) : SemanticOutput

data class CommandRequest(
    override val semanticHandle: SemanticHandle,
    override val sourceOrdinal: UInt,
    val payload: GameCommand,
) : SemanticOutput

sealed interface GameProjectionPayload {
    data class GameProjectionChanged(
        val visualFxCues: ImmutableList<VisualFxCue>,
    ) : GameProjectionPayload
}

sealed interface GameCommand {
    data class AdvanceAudio(
        val realDeltaSeconds: Float,
        val cues: ImmutableList<SoundCue>,
    ) : GameCommand

    data object EnsureAudioUnlocked : GameCommand

    data class ChangeSettings(val change: SettingsChange) : GameCommand
    data class ChangeProfile(val change: ProfileChange) : GameCommand
    data class BeginRebirth(val expectedLevel: Int) : GameCommand
}

/** Source-owned request vocabulary; Assembly maps it explicitly to Settings protocol Intents. */
sealed interface SettingsChange {
    data object ToggleMute : SettingsChange
    data object ToggleSound : SettingsChange
    data object ToggleMusic : SettingsChange
    data class AdjustMasterVolume(val direction: Int) : SettingsChange
    data class AdjustSimulationSpeed(val direction: Int) : SettingsChange
    data class AdjustTextScale(val direction: Int) : SettingsChange
    data object ToggleScreenShake : SettingsChange
    data class AdjustParticleDensity(val direction: Int) : SettingsChange
    data object ToggleDamageNumbers : SettingsChange
    data class AdjustDamageNumberSize(val direction: Int) : SettingsChange
    data class AdjustDamageNumberFormat(val direction: Int) : SettingsChange
    data class AdjustDamageNumberTierThreshold(val direction: Int) : SettingsChange
}

/** Source-owned request vocabulary; Assembly maps it explicitly to Profile protocol Intents. */
sealed interface ProfileChange {
    data class PurchaseMetaUpgrade(val upgrade: MetaUpgradeId) : ProfileChange
    data class PurchaseOrSelectWeapon(val weapon: WeaponId) : ProfileChange
    data class SelectCoreShape(val shape: CoreShape) : ProfileChange
    data class RecordItemDiscovery(val itemId: Int) : ProfileChange
    data class ApplyRunOutcome(
        val matterEarned: Long,
        val clearedRebirthLevel: Int?,
    ) : ProfileChange
}

data class GameDecisionContext(
    val operationId: OperationId,
    val causalBudgetScope: OperationId = operationId,
    val causalBudgetScopeOwnerBallInstanceId: String = GameProjection.BALL_INSTANCE_ID,
    val transitionArtifact: String = "game-v2",
    val causalDepth: Int = 1,
    val retryCount: Int = 0,
)

sealed interface GameRejection : BusinessRejection {
    data class InvalidInput(val field: String, val reason: String) : GameRejection

    data class InvalidDependencySource(
        val dependency: GameDependencySource,
        val received: ConsistencyStamp,
        val expectedBallInstanceId: String,
        val expectedStateSchemaVersion: Int,
    ) : GameRejection

    data class StaleDependencySource(
        val dependency: GameDependencySource,
        val receivedCommitRevision: ULong,
        val lastAcceptedCommitRevision: ULong,
    ) : GameRejection

    data class InvalidRunStartCommandSource(
        val field: String,
        val reason: String,
    ) : GameRejection

    data class InvalidRunStartCausalContext(
        val field: String,
        val reason: String,
    ) : GameRejection

    data class InvalidRunConfigurationReference(
        val field: String,
        val reason: String,
    ) : GameRejection

    data class ProfileReferenceNotCurrent(
        val received: GameRunConfigurationReference,
        val expected: ConsistencyStamp?,
    ) : GameRejection

    data class RunStartRebirthLevelMismatch(
        val received: Int,
        val expected: Int,
    ) : GameRejection
}
