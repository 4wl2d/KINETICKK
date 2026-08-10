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

/** Complete closed Profile input inventory. Resource-result variants are impl-only entry points. */
sealed interface ProfilePulse {
    sealed interface Business : ProfilePulse
    sealed interface ResourceResult : ProfilePulse

    data class AdjustPreference(
        val adjustment: ProfilePreferenceAdjustment,
    ) : Business

    data object ToggleMute : Business

    data class PurchaseMetaUpgrade(
        val id: MetaUpgradeId,
    ) : Business

    data class SelectCoreShape(
        val shape: CoreShape,
    ) : Business

    data class PurchaseOrEquipWeapon(
        val id: WeaponId,
    ) : Business

    data object AdvanceRebirth : Business

    data class ApplyGameplayProgress(
        val update: GameplayProgressUpdate,
    ) : Business

    data object ConfirmLegacyReset : Business
    data object RetryLegacyPurge : Business

    data class BootstrapCompleted(
        val result: ProfileBootstrapResourceResult,
    ) : ResourceResult

    data class V4WriteCompleted(
        val effectRef: ProfileEffectRef,
        val result: ProfileV4WriteResult,
    ) : ResourceResult

    data class LegacyPurgeCompleted(
        val effectRef: ProfileEffectRef,
        val result: ProfileLegacyPurgeResult,
    ) : ResourceResult
}

/** A cross-Ball command wraps the same business Pulse used by local Interaction. */
data class ProfileCommand(
    val ref: ProfileCommandRef,
    val pulse: ProfilePulse.Business,
)

sealed interface ProfileGameplayProgressRejection {
    data object NegativeBankedMatter : ProfileGameplayProgressRejection
    data class UnknownItem(val itemId: Int) : ProfileGameplayProgressRejection
    data object TooManyDiscoveries : ProfileGameplayProgressRejection
    data class ClearedLevelBelowMinimum(val level: Int) : ProfileGameplayProgressRejection
    data class ClearedLevelAboveCurrent(val level: Int) : ProfileGameplayProgressRejection
}

enum class ProfileCommandRefRejection {
    WRONG_TARGET,
    WRONG_SOURCE_KIND,
    ADMISSION_MISMATCH,
}

enum class ProfileResourceResultRejection {
    BOOTSTRAP_ALREADY_RESOLVED,
    NO_EFFECT_PENDING,
    EFFECT_REF_MISMATCH,
    RESULT_KIND_MISMATCH,
    WRITTEN_REVISION_MISMATCH,
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

    data class InvalidCommandRef(
        val reason: ProfileCommandRefRejection,
    ) : ProfileRejection

    data class UnexpectedResourceResult(
        val reason: ProfileResourceResultRejection,
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

sealed interface ProfileCommandOutcome {
    data class PreferencesChanged(val preferences: PlayerPreferences) : ProfileCommandOutcome
    data class RebirthAdvanced(val progress: RebirthProgress) : ProfileCommandOutcome
    data object GameplayProgressApplied : ProfileCommandOutcome
    data object ResetCompleted : ProfileCommandOutcome
    data class ResetWriteRejected(val reason: ProfileV4Rejection) : ProfileCommandOutcome
    data class ResetWriteOutcomeUnknown(val reason: ProfileResourceFailure) : ProfileCommandOutcome
    data class ResetNeedsAttention(val status: ProfileResetStatus.NeedsAttention) : ProfileCommandOutcome
}

sealed interface ProfileCommandResult {
    val commandRef: ProfileCommandRef

    data class Accepted(
        override val commandRef: ProfileCommandRef,
        val targetRevision: ProfileRevision,
        val outcome: ProfileCommandOutcome,
    ) : ProfileCommandResult

    data class Rejected(
        override val commandRef: ProfileCommandRef,
        val observedRevision: ProfileRevision,
        val reason: ProfileRejection,
    ) : ProfileCommandResult
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
    data class ResourceOutcomeUnknown(val reason: ProfileResourceFailure) : ProfileBootstrapBlockReason
    data class ResetRequired(val reason: ProfileResetReason) : ProfileBootstrapBlockReason
    data object ResetInProgress : ProfileBootstrapBlockReason
    data class ResetNeedsAttention(val result: ProfileLegacyPurgeResult) : ProfileBootstrapBlockReason
}

sealed interface ProfileBootstrapStatus {
    data object AwaitingResource : ProfileBootstrapStatus
    data object Ready : ProfileBootstrapStatus
    data class Blocked(val reason: ProfileBootstrapBlockReason) : ProfileBootstrapStatus
}

sealed interface ProfileResetCompletion {
    data object Local : ProfileResetCompletion
    data class Command(val commandRef: ProfileCommandRef) : ProfileResetCompletion
}

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
        val reason: ProfileResourceFailure,
    ) : ProfilePersistenceStatus
}
