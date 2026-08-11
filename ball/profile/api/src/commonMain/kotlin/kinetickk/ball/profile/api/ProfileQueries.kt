// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

sealed interface ProfileQuery {
    data object GetRunBootstrap : ProfileQuery
    data object GetPreferences : ProfileQuery
    data object GetHomeProgress : ProfileQuery
    data object GetLabProgress : ProfileQuery
    data object GetLoadout : ProfileQuery
    data object GetCollection : ProfileQuery
    data object GetRebirthProgress : ProfileQuery
    data object GetPersistenceStatus : ProfileQuery
}

sealed interface ProfileProjection {
    val instanceId: ProfileInstanceId
    val revision: ProfileRevision
}

data class LabProfileSnapshot(
    val economy: PlayerEconomy,
    val progress: LabProgress,
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
    val labProgress: LabProgress,
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
) : ProfileProjection

data class LabProgressProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val snapshot: LabProfileSnapshot,
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

data class PersistenceStatusProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val bootstrap: ProfileBootstrapStatus,
    val reset: ProfileResetStatus,
    val persistence: ProfilePersistenceStatus,
) : ProfileProjection

/** Query-only Profile surface used by Home and Codex presentation. */
interface ProfileReadPort {
    val instanceId: ProfileInstanceId

    fun query(query: ProfileQuery.GetPreferences): PreferencesProjection
    fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection
    fun query(query: ProfileQuery.GetCollection): CollectionProjection
}

/** Local Profile Interaction authority plus the complete read surface. */
interface ProfilePort : ProfileReadPort {

    fun accept(pulse: ProfilePulse.Business): ProfileAcceptance

    fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection
    fun query(query: ProfileQuery.GetLabProgress): LabProgressProjection
    fun query(query: ProfileQuery.GetLoadout): LoadoutProjection
    fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection
    fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection
}

/** Statically bound AppSession command route with only the reads its workflow consumes. */
interface SessionProfileRoute {
    val instanceId: ProfileInstanceId

    fun acceptFromSession(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): ProfileCommandIngressResult

    fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection
    fun query(query: ProfileQuery.GetPreferences): PreferencesProjection
    fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection
    fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection
}

/** Statically bound GameplayRun command route and its two admitted Profile reads. */
interface GameplayProfileRoute {
    val instanceId: ProfileInstanceId

    fun acceptFromGameplay(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): ProfileCommandIngressResult

    fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection
    fun query(query: ProfileQuery.GetPreferences): PreferencesProjection
}
