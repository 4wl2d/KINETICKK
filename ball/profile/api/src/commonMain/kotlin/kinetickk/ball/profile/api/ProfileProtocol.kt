// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
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
    /** Direct choice of one listed speed (segmented control); unlisted speeds are not representable. */
    data class SetSimulationSpeed(val speed: Float) : ProfilePreferenceAdjustment {
        init {
            require(speed in SIMULATION_SPEED_OPTIONS) { "Simulation speed must be one of the listed options" }
        }
    }
    data class StepTextScale(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data object ToggleScreenShake : ProfilePreferenceAdjustment
    data object ToggleRunStatisticsSide : ProfilePreferenceAdjustment
    data class StepParticleDensity(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetParticleDensity(val density: ParticleDensity) : ProfilePreferenceAdjustment
    data object ToggleDamageNumbers : ProfilePreferenceAdjustment
    data class StepDamageNumberSize(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetDamageNumberSize(val size: DamageNumberSize) : ProfilePreferenceAdjustment
    data class StepDamageNumberFormat(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetDamageNumberFormat(val format: DamageNumberFormat) : ProfilePreferenceAdjustment
    data class StepDamageNumberTierThreshold(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetColorVision(val colorVision: ColorVision) : ProfilePreferenceAdjustment
    data object ToggleMinimap : ProfilePreferenceAdjustment
}

data class GameplayProgressUpdate(
    val bankedMatter: Long = 0L,
    val discoveredItemIds: ImmutableSet<Int> = immutableSetOf(),
    val clearedRebirthLevel: Int? = null,
    val eliteKills: Int = 0,
    val dashHits: Int = 0,
    val completedOrbits: Int = 0,
    val architectDefeatedWith: CoreShape? = null,
    val discoveredRelicIds: ImmutableSet<RelicId> = immutableSetOf(),
    val discoveredReactionIds: ImmutableSet<ReactionId> = immutableSetOf(),
    val discoveredPairingIds: ImmutableSet<RelicPairingId> = immutableSetOf(),
    /**
     * Relics bound at the Architect victory in slot order. Profile accepts it only together with
     * [clearedRebirthLevel] and replaces the remembered Heirlooms with it.
     */
    val victoryRelicIds: ImmutableList<RelicId>? = null,
    /** The run resolved its Heirloom offer (taken or declined), so the remembered Heirlooms are spent. */
    val heirloomResolved: Boolean = false,
    /** Enemies destroyed since the last update. */
    val totalKills: Int = 0,
    /** Hard obstacle bounces since the last update. */
    val ricochets: Int = 0,
    /** Data pickups collected since the last update. */
    val dataPickups: Int = 0,
) {
    constructor(
        bankedMatter: Long = 0L,
        discoveredItemIds: Set<Int>,
        clearedRebirthLevel: Int? = null,
        eliteKills: Int = 0,
        dashHits: Int = 0,
        completedOrbits: Int = 0,
        architectDefeatedWith: CoreShape? = null,
        discoveredRelicIds: Set<RelicId> = emptySet(),
        discoveredReactionIds: Set<ReactionId> = emptySet(),
        discoveredPairingIds: Set<RelicPairingId> = emptySet(),
        victoryRelicIds: List<RelicId>? = null,
        heirloomResolved: Boolean = false,
        totalKills: Int = 0,
        ricochets: Int = 0,
        dataPickups: Int = 0,
    ) : this(
        bankedMatter, discoveredItemIds.toImmutableSet(), clearedRebirthLevel,
        eliteKills, dashHits, completedOrbits, architectDefeatedWith, discoveredRelicIds.toImmutableSet(),
        discoveredReactionIds.toImmutableSet(), discoveredPairingIds.toImmutableSet(),
        victoryRelicIds?.toImmutableList(), heirloomResolved, totalKills, ricochets, dataPickups,
    )

    /** True when the update carries no fact for Profile to apply, so no command needs to be sent. */
    val isEmpty: Boolean
        get() = bankedMatter == 0L && discoveredItemIds.isEmpty() && clearedRebirthLevel == null &&
            eliteKills == 0 && dashHits == 0 && completedOrbits == 0 && architectDefeatedWith == null &&
            discoveredRelicIds.isEmpty() && discoveredReactionIds.isEmpty() && discoveredPairingIds.isEmpty() &&
            victoryRelicIds == null && !heirloomResolved && totalKills == 0 && ricochets == 0 && dataPickups == 0
}

sealed interface CollectionEntry {
    data class Item(val id: Int) : CollectionEntry
    data class Relic(val id: RelicId) : CollectionEntry
}

/** Narrow Profile capability used by the collection's presentation. */
fun interface ProfileCollectionVisits {
    fun markViewed(entry: CollectionEntry): ProfileAcceptance
}

/** Closed local Interaction intent inventory. */
sealed interface ProfilePulse {
    sealed interface Business : ProfilePulse

    data class AdjustPreference(
        val adjustment: ProfilePreferenceAdjustment,
    ) : Business

    /** Buys the next rank of a Core Matrix node. */
    data class PurchaseMatrixNode(
        val id: MatrixNodeId,
    ) : Business

    /** Refunds every owned Core Matrix rank (a free respec). */
    data object ResetMatrix : Business

    data class PurchaseOrEquipWeapon(
        val id: WeaponId,
    ) : Business

    data class MarkCollectionEntryViewed(val entry: CollectionEntry) : Business

    /** Chooses the character for the next run from the Hangar; it must be unlocked. */
    data class SelectCoreShape(val shape: CoreShape) : Business
}

sealed interface ProfileGameplayProgressRejection {
    data object NegativeAchievementProgress : ProfileGameplayProgressRejection
    data object VictoryCharacterLocked : ProfileGameplayProgressRejection
    data object NegativeBankedMatter : ProfileGameplayProgressRejection
    data class UnknownItem(val itemId: Int) : ProfileGameplayProgressRejection
    data object TooManyDiscoveries : ProfileGameplayProgressRejection
    data class ClearedLevelBelowMinimum(val level: Int) : ProfileGameplayProgressRejection
    data class ClearedLevelAboveCurrent(val level: Int) : ProfileGameplayProgressRejection
    /** Victory Relics describe an Architect victory, so they require a cleared Rebirth level. */
    data object VictoryRelicsWithoutClear : ProfileGameplayProgressRejection
    data object TooManyVictoryRelics : ProfileGameplayProgressRejection
    data class DuplicateVictoryRelic(val relicId: RelicId) : ProfileGameplayProgressRejection
}

sealed interface ProfileRejection {
    data object BootstrapNotReady : ProfileRejection
    data object NoChange : ProfileRejection
    data object InsufficientMatter : ProfileRejection
    data object MatrixNodeMaxRank : ProfileRejection
    /** None of the node's prerequisites is owned. */
    data object MatrixPrerequisiteMissing : ProfileRejection
    /** The node needs a higher Rebirth rank. */
    data object MatrixRebirthLocked : ProfileRejection
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
