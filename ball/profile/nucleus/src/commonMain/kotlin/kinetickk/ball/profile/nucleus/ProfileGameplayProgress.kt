// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.rank
import kinetickk.ball.profile.api.rankWithin
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.max

internal fun ProfileNucleus.applyGameplayProgress(
    state: ProfileState,
    update: kinetickk.ball.profile.api.GameplayProgressUpdate,
): ProfileDecision {
    validateGameplayProgress(state, update)?.let {
        return rejected(ProfileRejection.InvalidGameplayProgress(it))
    }

    val economy = if (update.bankedMatter == 0L) {
        state.profile.economy
    } else {
        PlayerEconomy(
            matter = saturatedAdd(state.profile.economy.matter, update.bankedMatter),
            lifetimeMatter = saturatedAdd(state.profile.economy.lifetimeMatter, update.bankedMatter),
        )
    }
    val discoveries = state.profile.collection.discoveredItemIds.toMutableSet().apply {
        addAll(update.discoveredItemIds)
    }
    val rebirth = update.clearedRebirthLevel?.let { cleared ->
        // Clearing the final tier makes the victorious character Ascendant.
        val victor = update.architectDefeatedWith
        val ascended = victor?.takeIf { cleared >= ContentBounds.MAX_REBIRTH_LEVEL }
        val progress = state.profile.rebirthProgress
        progress.copy(
            highestCleared = max(progress.highestCleared, cleared),
            ascendedCharacters = if (ascended == null) progress.ascendedCharacters
            else (progress.ascendedCharacters + ascended).toImmutableSet(),
            // The victorious character keeps its highest clear; a lower tier replayed leaves it.
            characterClears = if (victor == null) progress.characterClears
            else progress.characterClears.mapIndexed { index, best ->
                if (index == victor.ordinal) max(best, cleared) else best
            }.toImmutableList(),
        )
    } ?: state.profile.rebirthProgress
    val previousAchievements = state.profile.characterAchievements
    val achievements = CharacterAchievementProgress(
        eliteKills = saturatedAdd(previousAchievements.eliteKills, update.eliteKills.toLong()),
        dashHits = saturatedAdd(previousAchievements.dashHits, update.dashHits.toLong()),
        completedOrbits = saturatedAdd(previousAchievements.completedOrbits, update.completedOrbits.toLong()),
        architectVictories = saturatedAdd(previousAchievements.architectVictories, if (update.architectDefeatedWith != null) 1L else 0L),
        victoriousCharacters = (previousAchievements.victoriousCharacters + listOfNotNull(update.architectDefeatedWith)).toImmutableSet(),
        totalKills = saturatedAdd(previousAchievements.totalKills, update.totalKills.toLong()),
        ricochets = saturatedAdd(previousAchievements.ricochets, update.ricochets.toLong()),
        dataPickups = saturatedAdd(previousAchievements.dataPickups, update.dataPickups.toLong()),
    )
    val collection = state.profile.collection
    // A resolved Heirloom offer spends the remembered Relics before a victory in the same
    // update records the Relics bound at that victory.
    val heirlooms = update.victoryRelicIds
        ?: if (update.heirloomResolved) immutableListOf() else collection.heirloomRelicIds
    val next = state.profile.copy(
        characterAchievements = achievements,
        economy = economy,
        collection = collection.copy(
            discoveredItemIds = discoveries.toImmutableSet(),
            newItemIds = (collection.newItemIds +
                (update.discoveredItemIds - collection.discoveredItemIds)).toImmutableSet(),
            discoveredRelicIds = (collection.discoveredRelicIds + update.discoveredRelicIds).toImmutableSet(),
            newRelicIds = (collection.newRelicIds +
                (update.discoveredRelicIds - collection.discoveredRelicIds)).toImmutableSet(),
            discoveredReactionIds = (collection.discoveredReactionIds + update.discoveredReactionIds).toImmutableSet(),
            discoveredPairingIds = (collection.discoveredPairingIds + update.discoveredPairingIds).toImmutableSet(),
            discoveredWorldEntries = (collection.discoveredWorldEntries + update.discoveredWorldEntries).toImmutableSet(),
            heirloomRelicIds = heirlooms,
            // Oldest first; only the newest MAX_RECENT_ITEM_IDS acquisitions are remembered.
            recentItemIds = if (update.acquiredItemIds.isEmpty()) {
                collection.recentItemIds
            } else {
                (collection.recentItemIds + update.acquiredItemIds).takeLast(MAX_RECENT_ITEM_IDS).toImmutableList()
            },
        ),
        rebirthProgress = rebirth,
    )
    if (next == state.profile) return rejected(ProfileRejection.NoChange)
    return acceptedMutation(
        state = state,
        nextProfile = next,
        commandOutput = ProfileOutput.ProgressApplied(ProfileProgressApplied(state.revision.next())),
    )
}

private fun validateGameplayProgress(
    state: ProfileState,
    update: kinetickk.ball.profile.api.GameplayProgressUpdate,
): ProfileGameplayProgressRejection? {
    if (update.eliteKills < 0 || update.dashHits < 0 || update.completedOrbits < 0 ||
        update.totalKills < 0 || update.ricochets < 0 || update.dataPickups < 0
    ) {
        return ProfileGameplayProgressRejection.NegativeAchievementProgress
    }
    update.architectDefeatedWith?.let { character ->
        if (!isCoreShapeUnlocked(state.profile, state.policy.coreShape(character))) {
            return ProfileGameplayProgressRejection.VictoryCharacterLocked
        }
    }
    if (update.bankedMatter < 0L) return ProfileGameplayProgressRejection.NegativeBankedMatter
    if (update.discoveredItemIds.size > state.policy.itemCount) {
        return ProfileGameplayProgressRejection.TooManyDiscoveries
    }
    update.discoveredItemIds.firstOrNull { !state.policy.containsItem(it) }?.let {
        return ProfileGameplayProgressRejection.UnknownItem(it)
    }
    if (update.acquiredItemIds.size > MAX_RECENT_ITEM_IDS) {
        return ProfileGameplayProgressRejection.TooManyAcquiredItems
    }
    update.acquiredItemIds.firstOrNull { !state.policy.containsItem(it) }?.let {
        return ProfileGameplayProgressRejection.UnknownItem(it)
    }
    update.clearedRebirthLevel?.let { level ->
        if (level < state.policy.rebirth.minimumLevel) {
            return ProfileGameplayProgressRejection.ClearedLevelBelowMinimum(level)
        }
        // The run's tier was selected under the rank at its start; the live selection may have
        // moved since, so the clear is checked against the rank, never against `level`.
        if (level > state.profile.rebirthProgress.rankWithin(state.policy.rebirth.minimumLevel, state.policy.rebirth.maximumLevel)) {
            return ProfileGameplayProgressRejection.ClearedLevelAboveRank(level)
        }
    }
    update.victoryRelicIds?.let { relics ->
        if (update.clearedRebirthLevel == null) {
            return ProfileGameplayProgressRejection.VictoryRelicsWithoutClear
        }
        if (relics.size > MAX_HEIRLOOM_RELICS) return ProfileGameplayProgressRejection.TooManyVictoryRelics
        relics.firstDuplicateOrNull()?.let { return ProfileGameplayProgressRejection.DuplicateVictoryRelic(it) }
    }
    return null
}

private fun <T> List<T>.firstDuplicateOrNull(): T? {
    val seen = HashSet<T>(size)
    return firstOrNull { !seen.add(it) }
}
