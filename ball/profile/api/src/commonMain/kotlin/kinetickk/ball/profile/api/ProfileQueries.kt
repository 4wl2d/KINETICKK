// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf

sealed interface ProfileQuery {
    data object GetRunBootstrap : ProfileQuery
    data object GetPreferences : ProfileQuery
    data object GetHomeProgress : ProfileQuery
    data object GetMatrixProgress : ProfileQuery
    data object GetLoadout : ProfileQuery
    data object GetCollection : ProfileQuery
    data object GetRebirthProgress : ProfileQuery
    data object GetPersistenceStatus : ProfileQuery
    /** Every character's unlock state for the Hangar. */
    data object GetHangar : ProfileQuery
}

sealed interface ProfileProjection {
    val instanceId: ProfileInstanceId
    val revision: ProfileRevision
}

data class MatrixProfileSnapshot(
    val economy: PlayerEconomy,
    val progress: MatrixProgress,
    /** Rebirth rank the Matrix gates compare against. */
    val rebirthRank: Int = 0,
)

data class LoadoutProfileSnapshot(
    val economy: PlayerEconomy,
    val loadout: PlayerLoadout,
)

data class RebirthProfileSnapshot(
    val progress: RebirthProgress,
)

data class GameplayProfileSnapshot(
    val preferences: PlayerPreferences,
    val economy: PlayerEconomy,
    val loadout: PlayerLoadout,
    val matrix: MatrixProgress,
    val collection: PlayerCollection,
    val rebirthProgress: RebirthProgress,
)

sealed interface ProfileRunBootstrapResult {
    data class Ready(val snapshot: GameplayProfileSnapshot) : ProfileRunBootstrapResult
    data class Unavailable(val status: ProfileBootstrapStatus) : ProfileRunBootstrapResult
}

data class RunBootstrapProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val result: ProfileRunBootstrapResult,
) : ProfileProjection

data class PreferencesProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val preferences: PlayerPreferences,
) : ProfileProjection

data class HomeProgressProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val economy: PlayerEconomy,
    val loadout: PlayerLoadout,
    val collection: PlayerCollection,
    val rebirthProgress: RebirthProgress,
    val canAdvanceRebirth: Boolean,
    val characterAchievements: CharacterAchievementProgress = CharacterAchievementProgress(),
    val unlockedCoreShapes: ImmutableSet<CoreShape> = immutableSetOf(CoreShape.ORB),
    /** Every character's unlock progress in stable order (the Hangar query's [HangarProjection.shapes]). */
    val characterProgress: ImmutableList<HangarShapeProgress> = immutableListOf(),
) : ProfileProjection

data class MatrixProgressProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val snapshot: MatrixProfileSnapshot,
) : ProfileProjection

data class LoadoutProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val snapshot: LoadoutProfileSnapshot,
) : ProfileProjection

data class CollectionProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val collection: PlayerCollection,
) : ProfileProjection

data class RebirthProgressProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val snapshot: RebirthProfileSnapshot,
    val canAdvance: Boolean,
) : ProfileProjection

/** Unlock progress of one character: [progress] of [target], capped at the target for display. */
data class HangarShapeProgress(
    val shape: CoreShape,
    val unlocked: Boolean,
    val progress: Long,
    val target: Long,
)

data class HangarProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    /** One entry per character in stable [CoreShape] order. */
    val shapes: ImmutableList<HangarShapeProgress>,
    val selected: CoreShape,
    /** Rebirth rank the Reborn-rank unlocks compare against. */
    val rebirthRank: Int = 0,
) : ProfileProjection

data class PersistenceStatusProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val bootstrap: ProfileBootstrapStatus,
    val persistence: ProfilePersistenceStatus,
) : ProfileProjection

/** Read capability of the application-lifetime Profile. */
interface ProfileReadPort {
    val instanceId: ProfileInstanceId

    fun query(query: ProfileQuery.GetPreferences): PreferencesProjection
    fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection
    fun query(query: ProfileQuery.GetCollection): CollectionProjection
    fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection
    fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection
    fun query(query: ProfileQuery.GetLoadout): LoadoutProjection
    fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection
    fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection
    fun query(query: ProfileQuery.GetHangar): HangarProjection
}

/** Local Profile interactions with their read capability. */
interface ProfilePort : ProfileReadPort {
    fun accept(pulse: ProfilePulse.Business): ProfileAcceptance
}
