// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.settings.nucleus.protocol

import kinetickk.application.runtime.BusinessRejection
import kinetickk.features.settings.nucleus.domain.SettingsAdjustmentDirection
import kinetickk.features.settings.nucleus.domain.SettingsValues
import kinetickk.features.settings.nucleus.read.SettingsReadResult
import kotlin.jvm.JvmInline

object SettingsProtocol {
    const val VERSION = "1.0.0"
    const val STATE_SCHEMA_VERSION = 1
    const val TRANSITION_ARTIFACT = "settings-v1"
    const val BALL_INSTANCE_ID = "kinetickk.local/Settings/local-player"
}

sealed interface SettingsIntent {
    data object MuteToggled : SettingsIntent
    data object SoundToggled : SettingsIntent
    data object MusicToggled : SettingsIntent
    data class MasterVolumeAdjusted(val direction: SettingsAdjustmentDirection) : SettingsIntent
    data class SimulationSpeedAdjusted(val direction: SettingsAdjustmentDirection) : SettingsIntent
    data class TextScaleAdjusted(val direction: SettingsAdjustmentDirection) : SettingsIntent
    data object ScreenShakeToggled : SettingsIntent
    data class ParticleDensityAdjusted(val direction: SettingsAdjustmentDirection) : SettingsIntent
    data object DamageNumbersToggled : SettingsIntent
    data class DamageNumberSizeAdjusted(val direction: SettingsAdjustmentDirection) : SettingsIntent
    data class DamageNumberFormatAdjusted(val direction: SettingsAdjustmentDirection) : SettingsIntent
    data class DamageNumberTierThresholdAdjusted(
        val direction: SettingsAdjustmentDirection,
    ) : SettingsIntent
}

sealed interface SettingsQuery {
    data object GetSettings : SettingsQuery
}

sealed interface SettingsQueryResult {
    data class Settings(
        val value: SettingsReadResult<SettingsValues>,
    ) : SettingsQueryResult
}

@JvmInline
value class SettingsOperationId(val value: ULong)

enum class SettingsOutputKind { SETTINGS_CHANGED }

data class SettingsSemanticHandle(
    val operationId: SettingsOperationId,
    val outputKind: SettingsOutputKind,
    val localOrdinalOrName: String,
)

data class SettingsProjectionOutput(
    val semanticHandle: SettingsSemanticHandle,
    val sourceOrdinal: UInt,
    val payload: SettingsProjectionPayload,
)

sealed interface SettingsProjectionPayload {
    data class SettingsChanged(
        val settings: SettingsValues,
    ) : SettingsProjectionPayload
}

internal data class SettingsDecisionContext(
    val operationId: SettingsOperationId,
    val causalBudgetScope: SettingsOperationId = operationId,
    val transitionArtifact: String = SettingsProtocol.TRANSITION_ARTIFACT,
    val causalDepth: Int = 1,
    val retryCount: Int = 0,
)

sealed interface SettingsRejection : BusinessRejection {
    data class InvalidContext(
        val field: String,
        val reason: String,
    ) : SettingsRejection
}
