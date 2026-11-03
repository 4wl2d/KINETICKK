// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.isAscendant
import kinetickk.ball.profile.api.rank

fun ProfileNucleus.query(state: ProfileState, query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection =
    RunBootstrapProjection(
        instanceId = state.instanceId,
        revision = state.revision,
        result = if (state.bootstrap == ProfileBootstrapStatus.Ready) {
            ProfileRunBootstrapResult.Ready(state.profile.toGameplaySnapshot())
        } else {
            ProfileRunBootstrapResult.Unavailable(state.bootstrap)
        },
    )

fun ProfileNucleus.query(state: ProfileState, query: ProfileQuery.GetPreferences): PreferencesProjection =
    PreferencesProjection(state.instanceId, state.revision, state.profile.preferences)

fun ProfileNucleus.query(state: ProfileState, query: ProfileQuery.GetHomeProgress): HomeProgressProjection =
    HomeProgressProjection(
        instanceId = state.instanceId,
        revision = state.revision,
        economy = state.profile.economy,
        loadout = state.profile.loadout,
        collection = state.profile.collection,
        rebirthProgress = state.profile.rebirthProgress,
        canAdvanceRebirth = canAdvanceRebirth(state),
        characterAchievements = state.profile.characterAchievements,
        unlockedCoreShapes = unlockedCoreShapes(state.profile, state.policy),
        characterProgress = hangarShapeProgress(state.profile, state.policy),
        workshopAffordable = isWorkshopAffordable(state.profile, state.policy),
        upgradeLevels = state.profile.upgrades.totalLevels,
    )

fun ProfileNucleus.query(state: ProfileState, query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection =
    MatrixProgressProjection(
        instanceId = state.instanceId,
        revision = state.revision,
        snapshot = MatrixProfileSnapshot(
            economy = state.profile.economy,
            progress = state.profile.matrix,
            rebirthRank = state.profile.rebirthProgress.rank,
        ),
    )

fun ProfileNucleus.query(state: ProfileState, query: ProfileQuery.GetLoadout): LoadoutProjection =
    LoadoutProjection(
        instanceId = state.instanceId,
        revision = state.revision,
        snapshot = LoadoutProfileSnapshot(
            economy = state.profile.economy,
            loadout = state.profile.loadout,
            rebirthRank = state.profile.rebirthProgress.rank,
            upgrades = state.profile.upgrades,
        ),
    )

fun ProfileNucleus.query(state: ProfileState, query: ProfileQuery.GetCollection): CollectionProjection =
    CollectionProjection(state.instanceId, state.revision, state.profile.collection)

fun ProfileNucleus.query(state: ProfileState, query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection =
    RebirthProgressProjection(
        instanceId = state.instanceId,
        revision = state.revision,
        snapshot = RebirthProfileSnapshot(state.profile.rebirthProgress),
        canAdvance = canAdvanceRebirth(state),
    )

fun ProfileNucleus.query(state: ProfileState, query: ProfileQuery.GetHangar): HangarProjection =
    HangarProjection(
        instanceId = state.instanceId,
        revision = state.revision,
        shapes = hangarShapeProgress(state.profile, state.policy),
        selected = state.profile.loadout.coreShape,
        rebirthRank = state.profile.rebirthProgress.rank,
        ascendedCharacters = state.profile.rebirthProgress.ascendedCharacters,
        ascendant = state.profile.rebirthProgress.isAscendant,
    )

fun ProfileNucleus.query(state: ProfileState, query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection =
    PersistenceStatusProjection(
        instanceId = state.instanceId,
        revision = state.revision,
        bootstrap = state.bootstrap,
        persistence = state.persistence,
    )
