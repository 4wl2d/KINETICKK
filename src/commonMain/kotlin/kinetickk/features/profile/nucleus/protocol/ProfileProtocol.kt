// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.profile.nucleus.protocol

import kinetickk.application.runtime.BusinessRejection
import kinetickk.features.profile.nucleus.domain.CoreShape
import kinetickk.features.profile.nucleus.domain.MetaUpgradeId
import kinetickk.features.profile.nucleus.domain.WeaponId
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kinetickk.features.profile.nucleus.projection.RunConfiguration
import kinetickk.features.profile.nucleus.read.ProfileReadResult
import kinetickk.foundation.collections.ImmutableList
import kotlin.jvm.JvmInline

@JvmInline
internal value class ProfileOperationId(val value: ULong)

@JvmInline
value class RunSettlementId(val value: ULong)

sealed interface ProfilePulse

/** Complete source identity of one command committed by another local Ball. */
data class ProfileCommandSource(
    val sourceBallInstanceId: String,
    val sourceCommitRevision: ULong,
    val sourceOrdinal: UInt,
    val sourceOperationId: ULong,
    val sourceOutputKind: String,
    val sourceLocalOrdinalOrName: String,
)

/** Stable owner and operation identity of the end-to-end causal budget. */
data class ProfileCausalScope(
    val ownerBallInstanceId: String,
    val operationId: ULong,
)

enum class ProfileModuleAuthority {
    LOCAL_PROFILE,
}

enum class ProfileModuleProtocolVersion {
    V1_0_0,
}

data class ProfileModuleResultProvenance(
    val authority: ProfileModuleAuthority,
    val protocolVersion: ProfileModuleProtocolVersion,
)

/** Target-owned reference to the exact Profile snapshot created by a module command. */
data class ProfileSnapshotReference(
    val profileBallInstanceId: String,
    val profileCommitRevision: ULong,
    val profileStateSchemaVersion: Int,
)

/** Target-owned command for the Rebirth Flow's request to advance permanent progression. */
data class ProfileAdvanceRebirthModuleCommand(
    val commandSource: ProfileCommandSource,
    val causalBudgetScope: ProfileCausalScope,
    val causalDepth: Int,
    val expectedLevel: Int,
) : ProfilePulse

enum class ProfileAdvanceRebirthRejectionReason {
    INVALID_COMMAND_SOURCE,
    INVALID_CAUSAL_CONTEXT,
    LEVEL_MISMATCH,
    CURRENT_LEVEL_NOT_CLEARED,
    MAXIMUM_LEVEL_REACHED,
    CONFLICTING_REDELIVERY,
    STALE_COMMAND_SOURCE,
    TARGET_DECISION_REJECTED,
    TARGET_ADMISSION_REJECTED,
}

sealed interface ProfileAdvanceRebirthModuleResult {
    val commandSource: ProfileCommandSource
    val causalBudgetScope: ProfileCausalScope
    val causalDepth: Int
    val provenance: ProfileModuleResultProvenance

    data class Advanced(
        override val commandSource: ProfileCommandSource,
        override val causalBudgetScope: ProfileCausalScope,
        override val causalDepth: Int,
        override val provenance: ProfileModuleResultProvenance,
        val newLevel: Int,
        val profileSnapshotReference: ProfileSnapshotReference,
    ) : ProfileAdvanceRebirthModuleResult

    data class Rejected(
        override val commandSource: ProfileCommandSource,
        override val causalBudgetScope: ProfileCausalScope,
        override val causalDepth: Int,
        override val provenance: ProfileModuleResultProvenance,
        val reason: ProfileAdvanceRebirthRejectionReason,
    ) : ProfileAdvanceRebirthModuleResult
}

object ProfileAdvanceRebirthContract {
    const val SOURCE_BALL_INSTANCE_ID = "kinetickk.local/RebirthFlow/local-player"
    const val CAUSAL_SCOPE_OWNER_BALL_INSTANCE_ID = "kinetickk.local/Game/local-player"
    const val SOURCE_OUTPUT_KIND = "PROFILE_ADVANCE_REBIRTH"
    const val SOURCE_LOCAL_ORDINAL_OR_NAME = "profile-advance-rebirth"
    const val COMMAND_CAUSAL_DEPTH = 3
    const val RESULT_CAUSAL_DEPTH = 4

    val RESULT_PROVENANCE = ProfileModuleResultProvenance(
        authority = ProfileModuleAuthority.LOCAL_PROFILE,
        protocolVersion = ProfileModuleProtocolVersion.V1_0_0,
    )
}

/** Target-owned command for Game's request to settle one completed run. */
data class ProfileApplyRunOutcomeModuleCommand(
    val commandSource: ProfileCommandSource,
    val causalBudgetScope: ProfileCausalScope,
    val causalDepth: Int,
    val matterEarned: Long,
    val clearedRebirthLevel: Int?,
) : ProfilePulse

enum class ProfileApplyRunOutcomeRejectionReason {
    INVALID_COMMAND_SOURCE,
    INVALID_CAUSAL_CONTEXT,
    INVALID_RUN_MATTER,
    INVALID_CLEARED_REBIRTH_LEVEL,
    CONFLICTING_RUN_SETTLEMENT,
    STALE_RUN_SETTLEMENT,
    CONFLICTING_REDELIVERY,
    STALE_COMMAND_SOURCE,
    TARGET_DECISION_REJECTED,
    TARGET_ADMISSION_REJECTED,
}

sealed interface ProfileApplyRunOutcomeModuleResult {
    val commandSource: ProfileCommandSource
    val causalBudgetScope: ProfileCausalScope
    val causalDepth: Int
    val provenance: ProfileModuleResultProvenance

    data class Applied(
        override val commandSource: ProfileCommandSource,
        override val causalBudgetScope: ProfileCausalScope,
        override val causalDepth: Int,
        override val provenance: ProfileModuleResultProvenance,
        val settlementId: RunSettlementId,
        val profileSnapshotReference: ProfileSnapshotReference,
        val resultingMatter: Long,
        val resultingLifetimeMatter: Long,
        val highestClearedRebirth: Int,
    ) : ProfileApplyRunOutcomeModuleResult

    data class Rejected(
        override val commandSource: ProfileCommandSource,
        override val causalBudgetScope: ProfileCausalScope,
        override val causalDepth: Int,
        override val provenance: ProfileModuleResultProvenance,
        val reason: ProfileApplyRunOutcomeRejectionReason,
    ) : ProfileApplyRunOutcomeModuleResult
}

object ProfileApplyRunOutcomeContract {
    const val SOURCE_BALL_INSTANCE_ID = "kinetickk.local/Game/local-player"
    const val SOURCE_OUTPUT_KIND = "CHANGE_PROFILE"
    const val SOURCE_LOCAL_ORDINAL_PREFIX = "authority-"
    const val COMMAND_CAUSAL_DEPTH = 2
    const val RESULT_CAUSAL_DEPTH = 3

    val RESULT_PROVENANCE = ProfileModuleResultProvenance(
        authority = ProfileModuleAuthority.LOCAL_PROFILE,
        protocolVersion = ProfileModuleProtocolVersion.V1_0_0,
    )
}

sealed interface ProfileIntent : ProfilePulse {
    data class PurchaseMetaUpgrade(val upgrade: MetaUpgradeId) : ProfileIntent
    data class PurchaseOrSelectWeapon(val weapon: WeaponId) : ProfileIntent
    data class SelectCoreShape(val shape: CoreShape) : ProfileIntent

    data class RecordItemDiscoveries(
        val itemIds: ImmutableList<Int>,
    ) : ProfileIntent

    data class ApplyRunOutcome(
        val settlementId: RunSettlementId,
        val matterEarned: Long,
        val clearedRebirthLevel: Int? = null,
    ) : ProfileIntent

    data class AdvanceRebirth(
        val expectedLevel: Int,
    ) : ProfileIntent
}

sealed interface ProfileQuery {
    data object GetProfileProjection : ProfileQuery
    data object GetRunConfiguration : ProfileQuery
}

sealed interface ProfileQueryResult {
    data class Projection(val value: ProfileReadResult<ProfileProjection>) : ProfileQueryResult
    data class Configuration(val value: ProfileReadResult<RunConfiguration>) : ProfileQueryResult
}

internal data class ProfileDecisionContext(
    val operationId: ProfileOperationId,
    val causalBudgetScopeOperationId: ULong = operationId.value,
    val causalBudgetScopeOwnerBallInstanceId: String = ProfileProjection.BALL_INSTANCE_ID,
    val transitionArtifact: String = "profile-v1",
    val causalDepth: Int = 1,
    val retryCount: Int = 0,
)

/** Empty for protocol 1.0.0; persistence and inter-Ball routing are composed later. */
internal sealed interface ProfileSemanticOutput

sealed interface ProfileRejection : BusinessRejection {
    data class InvalidContext(val field: String, val reason: String) : ProfileRejection
    data class InvalidModuleCommandSource(val field: String, val reason: String) : ProfileRejection
    data class InvalidModuleCommandCausalContext(
        val field: String,
        val reason: String,
    ) : ProfileRejection

    data class InvalidDiscoveryId(val itemId: Int) : ProfileRejection
    data class InsufficientMatter(val required: Long, val available: Long) : ProfileRejection
    data class MetaUpgradeMaxed(val upgrade: MetaUpgradeId) : ProfileRejection
    data class CoreShapeLocked(
        val shape: CoreShape,
        val requiredLifetimeMatter: Long,
        val actualLifetimeMatter: Long,
    ) : ProfileRejection

    data class InvalidRunSettlementId(val settlementId: RunSettlementId) : ProfileRejection
    data class InvalidRunMatter(val matterEarned: Long) : ProfileRejection
    data class InvalidClearedRebirthLevel(
        val received: Int,
        val currentRebirthLevel: Int,
    ) : ProfileRejection

    data class ConflictingRunSettlement(val settlementId: RunSettlementId) : ProfileRejection
    data class StaleRunSettlement(
        val received: RunSettlementId,
        val latest: RunSettlementId,
    ) : ProfileRejection

    data class RebirthLevelMismatch(val expected: Int, val actual: Int) : ProfileRejection
    data class RebirthNotCleared(val level: Int, val highestCleared: Int) : ProfileRejection
    data class RebirthMaximumReached(val level: Int) : ProfileRejection
}

internal fun ProfileAdvanceRebirthModuleCommand.envelopeViolation(): ProfileRejection? =
    validateCommandSource(
        source = commandSource,
        expectedBallInstanceId = ProfileAdvanceRebirthContract.SOURCE_BALL_INSTANCE_ID,
        expectedOutputKind = ProfileAdvanceRebirthContract.SOURCE_OUTPUT_KIND,
        expectedSourceOrdinal = 0u,
        expectedLocalOrdinalOrName = ProfileAdvanceRebirthContract.SOURCE_LOCAL_ORDINAL_OR_NAME,
    ) ?: validateCausalEnvelope(
        source = commandSource,
        scope = causalBudgetScope,
        causalDepth = causalDepth,
        expectedDepth = ProfileAdvanceRebirthContract.COMMAND_CAUSAL_DEPTH,
        expectedScopeOwnerBallInstanceId =
        ProfileAdvanceRebirthContract.CAUSAL_SCOPE_OWNER_BALL_INSTANCE_ID,
    )

internal fun ProfileApplyRunOutcomeModuleCommand.envelopeViolation(): ProfileRejection? {
    val source = commandSource
    if (source.sourceOrdinal == 0u) {
        return ProfileRejection.InvalidModuleCommandSource(
            field = "sourceOrdinal",
            reason = "must follow Game's projection output",
        )
    }
    val expectedLocalName = ProfileApplyRunOutcomeContract.SOURCE_LOCAL_ORDINAL_PREFIX +
        (source.sourceOrdinal - 1u).toString()
    return validateCommandSource(
        source = source,
        expectedBallInstanceId = ProfileApplyRunOutcomeContract.SOURCE_BALL_INSTANCE_ID,
        expectedOutputKind = ProfileApplyRunOutcomeContract.SOURCE_OUTPUT_KIND,
        expectedSourceOrdinal = null,
        expectedLocalOrdinalOrName = expectedLocalName,
    ) ?: validateCausalEnvelope(
        source = source,
        scope = causalBudgetScope,
        causalDepth = causalDepth,
        expectedDepth = ProfileApplyRunOutcomeContract.COMMAND_CAUSAL_DEPTH,
        expectedScopeOwnerBallInstanceId =
        ProfileApplyRunOutcomeContract.SOURCE_BALL_INSTANCE_ID,
    )
}

private fun validateCommandSource(
    source: ProfileCommandSource,
    expectedBallInstanceId: String,
    expectedOutputKind: String,
    expectedSourceOrdinal: UInt?,
    expectedLocalOrdinalOrName: String,
): ProfileRejection? {
    if (source.sourceBallInstanceId != expectedBallInstanceId) {
        return ProfileRejection.InvalidModuleCommandSource(
            field = "sourceBallInstanceId",
            reason = "must identify the declared source Ball",
        )
    }
    if (source.sourceCommitRevision == 0uL) {
        return ProfileRejection.InvalidModuleCommandSource(
            field = "sourceCommitRevision",
            reason = "must identify an accepted source frame",
        )
    }
    if (expectedSourceOrdinal != null && source.sourceOrdinal != expectedSourceOrdinal) {
        return ProfileRejection.InvalidModuleCommandSource(
            field = "sourceOrdinal",
            reason = "must be $expectedSourceOrdinal",
        )
    }
    if (source.sourceOperationId == 0uL) {
        return ProfileRejection.InvalidModuleCommandSource(
            field = "sourceOperationId",
            reason = "must identify the source operation",
        )
    }
    if (source.sourceOutputKind != expectedOutputKind) {
        return ProfileRejection.InvalidModuleCommandSource(
            field = "sourceOutputKind",
            reason = "must be $expectedOutputKind",
        )
    }
    if (source.sourceLocalOrdinalOrName != expectedLocalOrdinalOrName) {
        return ProfileRejection.InvalidModuleCommandSource(
            field = "sourceLocalOrdinalOrName",
            reason = "must be $expectedLocalOrdinalOrName",
        )
    }
    return null
}

private fun validateCausalEnvelope(
    source: ProfileCommandSource,
    scope: ProfileCausalScope,
    causalDepth: Int,
    expectedDepth: Int,
    expectedScopeOwnerBallInstanceId: String,
): ProfileRejection? {
    if (scope.ownerBallInstanceId != expectedScopeOwnerBallInstanceId) {
        return ProfileRejection.InvalidModuleCommandCausalContext(
            field = "causalBudgetScope.ownerBallInstanceId",
            reason = "must preserve the initiating Ball",
        )
    }
    if (scope.operationId != source.sourceOperationId) {
        return ProfileRejection.InvalidModuleCommandCausalContext(
            field = "causalBudgetScope.operationId",
            reason = "must match the source operation",
        )
    }
    if (causalDepth != expectedDepth) {
        return ProfileRejection.InvalidModuleCommandCausalContext(
            field = "causalDepth",
            reason = "must be $expectedDepth",
        )
    }
    return null
}
