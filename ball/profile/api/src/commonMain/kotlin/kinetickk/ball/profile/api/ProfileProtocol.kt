// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableSet

enum class PreferenceAdjustmentDirection {
    DECREASE,
    INCREASE,
}

/** Closed settings operation interpreted only by Profile Nucleus. */
sealed interface ProfilePreferenceAdjustment {
    data object ToggleSoundEffects : ProfilePreferenceAdjustment
    data object ToggleMusic : ProfilePreferenceAdjustment
    data class StepMasterVolume(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class StepSimulationSpeed(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class StepTextScale(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data object ToggleScreenShake : ProfilePreferenceAdjustment
    data class StepParticleDensity(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data object ToggleDamageNumbers : ProfilePreferenceAdjustment
    data class StepDamageNumberSize(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class StepDamageNumberFormat(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class StepDamageNumberTierThreshold(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
}

data class GameplayProgressUpdate(
    val bankedMatter: Long = 0L,
    val discoveredItemIds: ImmutableSet<Int> = immutableSetOf(),
    val clearedRebirthLevel: Int? = null,
) {
    constructor(
        bankedMatter: Long = 0L,
        discoveredItemIds: Set<Int>,
        clearedRebirthLevel: Int? = null,
    ) : this(bankedMatter, discoveredItemIds.toImmutableSet(), clearedRebirthLevel)
}

/** Closed local Interaction intent inventory. */
sealed interface ProfilePulse {
    sealed interface Business : ProfilePulse

    data class AdjustPreference(
        val adjustment: ProfilePreferenceAdjustment,
    ) : Business

    data class PurchaseMetaUpgrade(
        val id: MetaUpgradeId,
    ) : Business

    data class PurchaseOrEquipWeapon(
        val id: WeaponId,
    ) : Business
}

/** Target-owned Profile ModuleCommand payloads. They are not aliases of local Interaction intents. */
sealed interface ProfileModuleCommand {
    data class SelectCoreShape(val shape: CoreShape) : ProfileModuleCommand
    data object ToggleMute : ProfileModuleCommand
    data object AdvanceRebirth : ProfileModuleCommand
    data object ConfirmLegacyReset : ProfileModuleCommand
    data object RetryLegacyPurge : ProfileModuleCommand
    data class ApplyGameplayProgress(val update: GameplayProgressUpdate) : ProfileModuleCommand
}

/** Canonical accepted-source ModuleCommandRequest retained in a caller frame. */
data class ProfileModuleCommandRequest(
    val semanticHandle: ProfileSemanticHandle,
    val sourceOrdinal: Int,
    val targetInstance: ProfileInstanceId,
    val command: ProfileModuleCommand,
) {
    init {
        require(sourceOrdinal == semanticHandle.sourceOrdinal) {
            "Profile command request ordinal must match its semantic handle"
        }
    }
}

/** Canonical target Nucleus command input, constructed only by the trusted binding boundary. */
data class ProfileModuleCommandPulse(
    val commandSource: ProfileCommandSourceToken,
    val effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
    val command: ProfileModuleCommand,
    val issuerProvenance: ProfileCommandIssuerProvenance,
)

sealed interface ProfileGameplayProgressRejection {
    data object NegativeBankedMatter : ProfileGameplayProgressRejection
    data class UnknownItem(val itemId: Int) : ProfileGameplayProgressRejection
    data object TooManyDiscoveries : ProfileGameplayProgressRejection
    data class ClearedLevelBelowMinimum(val level: Int) : ProfileGameplayProgressRejection
    data class ClearedLevelAboveCurrent(val level: Int) : ProfileGameplayProgressRejection
}

enum class ProfileCommandValidationFailureReason {
    WRONG_TARGET,
    WRONG_SOURCE_KIND,
}

sealed interface ProfileRejection {
    data object BootstrapNotReady : ProfileRejection
    data object ResetRequired : ProfileRejection
    data object ResetInProgress : ProfileRejection
    data object NoChange : ProfileRejection
    data object InsufficientMatter : ProfileRejection
    data object MetaUpgradeMaxRank : ProfileRejection
    data object CoreShapeLocked : ProfileRejection
    data object RebirthMaximumReached : ProfileRejection
    data object RebirthLevelNotCleared : ProfileRejection

    data class InvalidGameplayProgress(
        val reason: ProfileGameplayProgressRejection,
    ) : ProfileRejection

}

sealed interface ProfileAcceptance {
    val instanceId: ProfileInstanceId

    data class Accepted(
        override val instanceId: ProfileInstanceId,
        val revision: ProfileRevision,
    ) : ProfileAcceptance

    data class Rejected(
        override val instanceId: ProfileInstanceId,
        val observedRevision: ProfileRevision,
        val reason: ProfileRejection,
    ) : ProfileAcceptance
}

/** Target-owned ModuleResult payload family for the six Profile mappings. */
sealed interface ProfileModuleResult {
    data class PreferencesChanged(val preferences: PlayerPreferences) : ProfileModuleResult
    data class CoreShapeSelected(val shape: CoreShape) : ProfileModuleResult
    data class RebirthAdvanced(val progress: RebirthProgress) : ProfileModuleResult
    data object GameplayProgressApplied : ProfileModuleResult
    data object ResetCompleted : ProfileModuleResult
    data class ResetWriteRejected(val reason: ProfileV4Rejection) : ProfileModuleResult
    data class ResetWriteOutcomeUnknown(
        val reason: ProfileWriteOutcomeUnknownReason,
    ) : ProfileModuleResult
    data class ResetNeedsAttention(val status: ProfileResetStatus.NeedsAttention) : ProfileModuleResult
}

/** Canonical target output, created only inside an accepted Profile Decision. */
data class ProfileModuleResultOutput(
    val semanticHandle: ProfileSemanticHandle,
    val sourceOrdinal: Int,
    val commandSource: ProfileCommandSourceToken,
    val result: ProfileModuleResult,
) {
    init {
        require(semanticHandle == commandSource.semanticHandle) {
            "Profile result output must preserve the command semantic handle"
        }
    }
}

/** Full accepted-frame evidence transported by the statically bound Profile result route. */
data class ProfileModuleResultDelivery(
    val commandSource: ProfileCommandSourceToken,
    val resultSource: ProfileResultSourceToken,
    val effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
    val result: ProfileModuleResult,
    val issuerProvenance: ProfileResultIssuerProvenance,
)

sealed interface ProfileCommandAdmissionFailureReason {
    data class CausalBudgetExceeded(
        val causalScope: Long,
        val limit: Int,
    ) : ProfileCommandAdmissionFailureReason

    data object CompletionCapacityExhausted : ProfileCommandAdmissionFailureReason
    data object RevisionCapacityExhausted : ProfileCommandAdmissionFailureReason
}

sealed interface ProfileCommandBoundaryResponse {
    data class ValidationFailure(
        val reason: ProfileCommandValidationFailureReason,
    ) : ProfileCommandBoundaryResponse

    data class AdmissionFailure(
        val reason: ProfileCommandAdmissionFailureReason,
    ) : ProfileCommandBoundaryResponse

    data class DecisionRejected(
        val reason: ProfileRejection,
    ) : ProfileCommandBoundaryResponse
}

/** Verified target-ingress refusal evidence; the caller owns its ControlPulse carrier wrapper. */
data class ProfileCommandRefusalEvidence(
    val commandSource: ProfileCommandSourceToken,
    val effectiveProtocolIdentity: ProfileEffectiveProtocolIdentity,
    val boundaryResponse: ProfileCommandBoundaryResponse,
    val targetBoundaryProvenance: ProfileTargetBoundaryProvenance,
)

sealed interface ProfileCommandIngressResult {
    data class Accepted(
        val targetInstance: ProfileInstanceId,
        val targetRevision: ProfileRevision,
    ) : ProfileCommandIngressResult

    data class RejectedBeforeAcceptance(
        val refusal: ProfileCommandRefusalEvidence,
    ) : ProfileCommandIngressResult
}

sealed interface ProfileResetReason {
    data object LegacyDataDetected : ProfileResetReason
    data class InvalidV4(val reason: ProfileV4Rejection) : ProfileResetReason
    data class ContentVersionMismatch(
        val expected: ContentVersion,
        val observed: ContentVersion,
    ) : ProfileResetReason
    data object IncompatibleProfile : ProfileResetReason
}

sealed interface ProfileBootstrapBlockReason {
    data class ResourceFailure(val reason: ProfileReadFailure) : ProfileBootstrapBlockReason
    data class ResetRequired(val reason: ProfileResetReason) : ProfileBootstrapBlockReason
    data object ResetInProgress : ProfileBootstrapBlockReason
    data class ResetNeedsAttention(val result: ProfileLegacyPurgeResult) : ProfileBootstrapBlockReason
}

sealed interface ProfileBootstrapStatus {
    data object Ready : ProfileBootstrapStatus
    data class Blocked(val reason: ProfileBootstrapBlockReason) : ProfileBootstrapStatus
}

data class ProfileResetCompletion(
    val commandSource: ProfileCommandSourceToken,
)

sealed interface ProfileResetStatus {
    data class NotRequired(
        val legacyResetConfirmed: Boolean,
    ) : ProfileResetStatus

    data class ConfirmationRequired(
        val reason: ProfileResetReason,
        val legacyKeys: ProfileLegacyKeys,
    ) : ProfileResetStatus

    data class WritingFreshV4(
        val completion: ProfileResetCompletion,
        val reason: ProfileResetReason,
        val effectRef: ProfileEffectRef,
        val legacyKeys: ProfileLegacyKeys,
    ) : ProfileResetStatus

    data class PurgingLegacy(
        val completion: ProfileResetCompletion,
        val effectRef: ProfileEffectRef,
        val legacyKeys: ProfileLegacyKeys,
    ) : ProfileResetStatus

    data class NeedsAttention(
        val legacyKeys: ProfileLegacyKeys,
        val result: ProfileLegacyPurgeResult,
    ) : ProfileResetStatus
}

enum class ProfileV4WritePurpose {
    MUTATION,
    RESET_DEFAULT,
}

sealed interface ProfilePersistenceStatus {
    data object NotAttempted : ProfilePersistenceStatus

    data class Pending(
        val effectRef: ProfileEffectRef,
        val snapshotRevision: ProfileRevision,
        val purpose: ProfileV4WritePurpose,
    ) : ProfilePersistenceStatus

    data class Persisted(
        val snapshotRevision: ProfileRevision,
    ) : ProfilePersistenceStatus

    data class Rejected(
        val snapshotRevision: ProfileRevision,
        val reason: ProfileV4Rejection,
    ) : ProfilePersistenceStatus

    data class OutcomeUnknown(
        val snapshotRevision: ProfileRevision,
        val reason: ProfileWriteOutcomeUnknownReason,
    ) : ProfilePersistenceStatus
}
