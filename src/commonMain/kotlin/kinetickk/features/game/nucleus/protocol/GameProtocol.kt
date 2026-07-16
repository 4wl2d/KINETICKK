// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus.protocol

import kinetickk.application.runtime.BusinessRejection
import kinetickk.features.game.nucleus.CoreShape
import kinetickk.features.game.nucleus.GameSettings
import kinetickk.features.game.nucleus.MetaUpgradeId
import kinetickk.features.game.nucleus.StoredProgress
import kinetickk.features.game.nucleus.UiScreen
import kinetickk.features.game.nucleus.WeaponId
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.game.nucleus.read.ReadResult
import kinetickk.foundation.collections.ImmutableList
import kotlin.jvm.JvmInline

sealed interface GamePulse

sealed interface GameQuery {
    data object GetGameProjection : GameQuery
    data object GetPersistenceStatus : GameQuery
}

sealed interface GameQueryResult {
    data class Projection(val value: ReadResult<GameProjection>) : GameQueryResult
    data class Persistence(val value: ReadResult<PersistenceStatus>) : GameQueryResult
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

sealed interface GameFact : GamePulse {
    val handle: SemanticHandle
    val provider: ProgressProvider

    data class ProgressPersisted(
        override val handle: SemanticHandle,
        override val provider: ProgressProvider,
    ) : GameFact

    data class ProgressPersistenceOutcomeUnknown(
        override val handle: SemanticHandle,
        override val provider: ProgressProvider,
        val reason: ProgressPersistenceUnknownReason,
    ) : GameFact
}

enum class ProgressProvider { PLATFORM_LOCAL }

/** A trusted semantic operation identity reserved before the Nucleus evaluates a Pulse. */
@JvmInline
value class OperationId(val value: ULong)

/** The closed output-kind names owned by Game protocol 1.0.0. */
enum class GameOutputKind {
    GAME_PROJECTION_CHANGED,
    ADVANCE_AUDIO,
    ENSURE_AUDIO_UNLOCKED,
    PERSIST_PROGRESS,
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

data class EffectRequest(
    override val semanticHandle: SemanticHandle,
    override val sourceOrdinal: UInt,
    val payload: GameEffect,
) : SemanticOutput

sealed interface GameProjectionPayload {
    data class GameProjectionChanged(
        val visualFxCues: ImmutableList<VisualFxCue>,
    ) : GameProjectionPayload
}

sealed interface GameEffect {
    data class AdvanceAudio(
        val settings: GameSettings,
        val realDeltaSeconds: Float,
        val cues: ImmutableList<SoundCue>,
    ) : GameEffect

    data object EnsureAudioUnlocked : GameEffect

    data class PersistProgress(
        val snapshot: StoredProgress,
    ) : GameEffect
}

data class GameDecisionContext(
    val operationId: OperationId,
    val causalBudgetScope: OperationId = operationId,
    val transitionArtifact: String = "game-v1",
    val causalDepth: Int = 1,
    val retryCount: Int = 0,
)

sealed interface GameRejection : BusinessRejection {
    data class InvalidInput(val field: String, val reason: String) : GameRejection
    data class StaleFact(val received: SemanticHandle, val expected: SemanticHandle?) : GameRejection
    data class InvalidFactProvider(
        val received: ProgressProvider,
        val expected: ProgressProvider,
    ) : GameRejection
    data class GenerationExhausted(val lastGeneration: Long) : GameRejection
}

enum class ProgressPersistenceUnknownReason {
    PROVIDER_READ_FAILED,
    ENCODING_FAILED,
    PAYLOAD_LIMIT_EXCEEDED,
    PROVIDER_WRITE_MAY_HAVE_EXECUTED,
}

sealed interface PersistenceStatus {
    data object NeverRequested : PersistenceStatus
    data class Pending(val handle: SemanticHandle) : PersistenceStatus
    data class Persisted(val handle: SemanticHandle) : PersistenceStatus
    data class OutcomeUnknown(
        val handle: SemanticHandle,
        val reason: ProgressPersistenceUnknownReason,
    ) : PersistenceStatus
}
