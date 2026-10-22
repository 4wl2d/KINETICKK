// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileInstanceId
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.isWithinRebirthBounds
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableSet

/** Complete immutable snapshot owned and published by the Profile acceptor. */
data class ProfileState(
    val instanceId: ProfileInstanceId,
    val revision: ProfileRevision,
    val profile: PlayerProfile,
    val policy: ProfilePolicySnapshot,
    val bootstrap: ProfileBootstrapStatus,
    val persistence: ProfilePersistenceStatus,
) {
    init {
        require(instanceId == LOCAL_PROFILE_INSTANCE_ID) { "Only the local Profile instance is supported" }
    }

    companion object {
        fun initial(
            policy: ProfilePolicySnapshot,
            snapshotReadResult: ProfileSnapshotReadResult,
        ): ProfileState = constructInitialProfileState(policy, snapshotReadResult)
    }
}

private fun constructInitialProfileState(
    policy: ProfilePolicySnapshot,
    result: ProfileSnapshotReadResult,
): ProfileState {
    val default = defaultPlayerProfile(policy)

    fun state(
        revision: ProfileRevision,
        profile: PlayerProfile = default,
        bootstrap: ProfileBootstrapStatus,
        persistence: ProfilePersistenceStatus,
    ): ProfileState = ProfileState(
        instanceId = LOCAL_PROFILE_INSTANCE_ID,
        revision = revision,
        profile = profile,
        policy = policy,
        bootstrap = bootstrap,
        persistence = persistence,
    )

    return when (result) {
        is ProfileSnapshotReadResult.ResourceFailure -> state(
            revision = ProfileRevision(1L),
            bootstrap = ProfileBootstrapStatus.Blocked(
                ProfileBootstrapBlockReason.ResourceFailure(result.reason),
            ),
            persistence = ProfilePersistenceStatus.NotAttempted,
        )
        is ProfileSnapshotReadResult.Rejected -> state(
            revision = ProfileRevision(1L),
            bootstrap = ProfileBootstrapStatus.Ready,
            persistence = ProfilePersistenceStatus.NotAttempted,
        )
        is ProfileSnapshotReadResult.Observed -> {
            val snapshot = result.snapshot
            val loaded = snapshot?.profile?.withCatalogDiscoveries(policy)?.withMatrixPolicy(policy)
                ?.withUpgradePolicy(policy)
            when {
                snapshot == null || loaded == null -> state(
                    revision = ProfileRevision(1L),
                    bootstrap = ProfileBootstrapStatus.Ready,
                    persistence = ProfilePersistenceStatus.NotAttempted,
                )
                snapshot.revision.value > MAX_LOADABLE_SNAPSHOT_REVISION ||
                    !isPolicyCompatibleAtConstruction(loaded, policy) -> state(
                    revision = ProfileRevision(1L),
                    bootstrap = ProfileBootstrapStatus.Ready,
                    persistence = ProfilePersistenceStatus.NotAttempted,
                )
                else -> state(
                    revision = ProfileRevision(snapshot.revision.value + 1L),
                    profile = loaded,
                    bootstrap = ProfileBootstrapStatus.Ready,
                    persistence = ProfilePersistenceStatus.Persisted(snapshot.revision),
                )
            }
        }
    }
}

/**
 * Item discoveries are catalog indices: an id the captured catalog no longer contains is a retired
 * entry, not a corrupt economy. Loading forgets such ids (and their unseen markers) and keeps every
 * other fact; the next accepted write persists the filtered collection.
 */
private fun PlayerProfile.withCatalogDiscoveries(policy: ProfilePolicySnapshot): PlayerProfile {
    val current = collection
    if (current.discoveredItemIds.all(policy::containsItem) && current.newItemIds.all(policy::containsItem)) {
        return this
    }
    return copy(
        collection = current.copy(
            discoveredItemIds = current.discoveredItemIds.filter(policy::containsItem).toImmutableSet(),
            newItemIds = current.newItemIds.filter(policy::containsItem).toImmutableSet(),
        ),
    )
}

/**
 * Matrix ranks above a node's current maximum (a rebalance lowered it) are clamped on load and
 * their price is refunded; prerequisites and Rebirth gates are purchase rules only, so a profile
 * that owns a node whose gate moved keeps it. The next accepted write persists the clamped ranks.
 */
private fun PlayerProfile.withMatrixPolicy(policy: ProfilePolicySnapshot): PlayerProfile {
    if (matrix.ranks.size != MatrixNodeId.entries.size) return this
    var refund = 0L
    val ranks = matrix.ranks.mapIndexed { index, rank ->
        val definition = policy.matrixNodes.firstOrNull { it.id.ordinal == index } ?: return@mapIndexed rank
        if (rank <= definition.maxRanks) return@mapIndexed rank
        refund = saturatedAdd(refund, definition.spent(rank) - definition.spent(definition.maxRanks))
        definition.maxRanks
    }
    if (refund == 0L && ranks == matrix.ranks) return this
    val matter = saturatedAdd(economy.matter, refund)
    return copy(
        economy = PlayerEconomy(matter = matter, lifetimeMatter = maxOf(economy.lifetimeMatter, matter)),
        matrix = MatrixProgress(ranks),
    )
}

/**
 * Loading advances once, and one complete mutation advances for acceptance and write completion.
 * Snapshots above this boundary cannot support that smallest useful current-schema lifecycle.
 */
private const val MAX_LOADABLE_SNAPSHOT_REVISION: Long = Long.MAX_VALUE - 3L

private fun isPolicyCompatibleAtConstruction(
    profile: PlayerProfile,
    policy: ProfilePolicySnapshot,
): Boolean {
    val preferences = profile.preferences
    if (
        !preferences.masterVolume.isFinite() ||
        !preferences.simulationSpeed.isFinite() ||
        !preferences.textScale.isFinite() ||
        preferences != preferences.normalized() ||
        preferences.simulationSpeed !in kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS ||
        preferences.damageNumberTierThreshold !in
            kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
    ) return false
    if (profile.economy.matter < 0L || profile.economy.lifetimeMatter < profile.economy.matter) {
        return false
    }
    val achievements = profile.characterAchievements
    if (achievements.eliteKills < 0L || achievements.dashHits < 0L ||
        achievements.completedOrbits < 0L || achievements.architectVictories < 0L ||
        achievements.totalKills < 0L || achievements.ricochets < 0L || achievements.dataPickups < 0L ||
        achievements.victoriousCharacters.size > achievements.architectVictories
    ) return false
    val allowedShapes = policy.coreShapes.map { it.id }
    if (achievements.victoriousCharacters.any { it !in allowedShapes }) return false
    val allowedWeapons = policy.weapons.map { it.id }
    if (
        profile.loadout.coreShape !in allowedShapes ||
        !isCoreShapeUnlocked(profile, policy.coreShape(profile.loadout.coreShape)) ||
        profile.loadout.selectedWeapon !in allowedWeapons ||
        profile.loadout.selectedWeapon !in profile.loadout.unlockedWeapons ||
        policy.weapons.first().id !in profile.loadout.unlockedWeapons ||
        profile.loadout.unlockedWeapons.isEmpty() ||
        profile.loadout.unlockedWeapons.size > policy.weapons.size ||
        profile.loadout.unlockedWeapons.size > kinetickk.ball.content.api.ContentBounds.MAX_WEAPONS ||
        profile.loadout.unlockedWeapons.any { it !in allowedWeapons }
    ) return false
    val matrix = profile.matrix
    if (matrix.ranks.size != MatrixNodeId.entries.size || matrix.ranks.any { it < 0 }) return false
    policy.matrixNodes.forEach { definition ->
        if (matrix.rank(definition.id) > definition.maxRanks) return false
    }
    if (!profile.upgrades.isWithin(policy.upgrades)) return false
    val heirlooms = profile.collection.heirloomRelicIds
    if (
        profile.collection.discoveredItemIds.size > policy.itemCount ||
        profile.collection.discoveredItemIds.any { !policy.containsItem(it) } ||
        !profile.collection.discoveredItemIds.containsAll(profile.collection.newItemIds) ||
        !profile.collection.discoveredRelicIds.containsAll(profile.collection.newRelicIds) ||
        profile.collection.discoveredRelicIds.size > kinetickk.ball.content.api.ContentBounds.MAX_RELICS ||
        heirlooms.size > MAX_HEIRLOOM_RELICS || heirlooms.toSet().size != heirlooms.size
    ) return false
    if (!profile.rebirthProgress.isWithinRebirthBounds(policy.rebirth.minimumLevel, policy.rebirth.maximumLevel)) {
        return false
    }
    if (!achievements.victoriousCharacters.containsAll(profile.rebirthProgress.ascendedCharacters)) return false
    return true
}

internal fun defaultPlayerProfile(policy: ProfilePolicySnapshot): PlayerProfile {
    val defaultWeapon = policy.weapons.first().id
    return PlayerProfile(
        loadout = PlayerLoadout(
            coreShape = policy.coreShapes.first().id,
            selectedWeapon = defaultWeapon,
            unlockedWeapons = immutableSetOf(defaultWeapon),
        ),
        matrix = MatrixProgress(),
        rebirthProgress = RebirthProgress(
            level = policy.rebirth.minimumLevel,
            highestCleared = -1,
        ),
    )
}
