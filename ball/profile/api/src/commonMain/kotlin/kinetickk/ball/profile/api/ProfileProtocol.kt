// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage

enum class PreferenceAdjustmentDirection {
    DECREASE,
    INCREASE,
}

/** Closed settings operation interpreted only by Profile Nucleus. */
sealed interface ProfilePreferenceAdjustment {
    data class SetLanguage(val language: AppLanguage) : ProfilePreferenceAdjustment
    data object ToggleSoundEffects : ProfilePreferenceAdjustment
    data object ToggleMusic : ProfilePreferenceAdjustment
    data class StepMasterVolume(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetMasterVolume(val percent: Int) : ProfilePreferenceAdjustment {
        init {
            require(percent in 0..100) { "Master volume must be between 0 and 100 percent" }
        }
    }
    data class StepSimulationSpeed(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class StepTextScale(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data object ToggleScreenShake : ProfilePreferenceAdjustment
    data object ToggleRunStatisticsSide : ProfilePreferenceAdjustment
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
    val eliteKills: Int = 0,
    val dashHits: Int = 0,
    val completedOrbits: Int = 0,
    val architectDefeatedWith: CoreShape? = null,
) {
    constructor(
        bankedMatter: Long = 0L,
        discoveredItemIds: Set<Int>,
        clearedRebirthLevel: Int? = null,
        eliteKills: Int = 0,
        dashHits: Int = 0,
        completedOrbits: Int = 0,
        architectDefeatedWith: CoreShape? = null,
    ) : this(
        bankedMatter, discoveredItemIds.toImmutableSet(), clearedRebirthLevel,
        eliteKills, dashHits, completedOrbits, architectDefeatedWith,
    )
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

sealed interface ProfileGameplayProgressRejection {
    data object NegativeAchievementProgress : ProfileGameplayProgressRejection
    data object VictoryCharacterLocked : ProfileGameplayProgressRejection
    data object NegativeBankedMatter : ProfileGameplayProgressRejection
    data class UnknownItem(val itemId: Int) : ProfileGameplayProgressRejection
    data object TooManyDiscoveries : ProfileGameplayProgressRejection
    data class ClearedLevelBelowMinimum(val level: Int) : ProfileGameplayProgressRejection
    data class ClearedLevelAboveCurrent(val level: Int) : ProfileGameplayProgressRejection
}

sealed interface ProfileRejection {
    data object BootstrapNotReady : ProfileRejection
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

sealed interface ProfileBootstrapBlockReason {
    data class ResourceFailure(val reason: ProfileReadFailure) : ProfileBootstrapBlockReason
}

sealed interface ProfileBootstrapStatus {
    data object Ready : ProfileBootstrapStatus
    data class Blocked(val reason: ProfileBootstrapBlockReason) : ProfileBootstrapStatus
}

sealed interface ProfilePersistenceStatus {
    data object NotAttempted : ProfilePersistenceStatus

    data class Pending(
        val effectRef: ProfileEffectRef,
        val snapshotRevision: ProfileRevision,
    ) : ProfilePersistenceStatus

    data class Persisted(
        val snapshotRevision: ProfileRevision,
    ) : ProfilePersistenceStatus

    data class Rejected(
        val snapshotRevision: ProfileRevision,
        val reason: ProfileSnapshotRejection,
    ) : ProfilePersistenceStatus

    data class ResourceFailure(
        val snapshotRevision: ProfileRevision,
        val reason: ProfileWriteFailure,
    ) : ProfilePersistenceStatus

    data class OutcomeUnknown(
        val snapshotRevision: ProfileRevision,
        val reason: ProfileWriteOutcomeUnknownReason,
    ) : ProfilePersistenceStatus
}
