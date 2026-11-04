// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.nucleus.model.ItemDeck
import kinetickk.ball.gameplay.nucleus.model.ItemHistoryWeights
import kinetickk.ball.gameplay.nucleus.model.RunIntArray
import kinetickk.ball.gameplay.nucleus.model.RunMutableSet
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState.Companion.DECK_SEED_SALT
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.rank

/**
 * What the run captured from the Profile and the run options when it was created: the Rebirth profile, owned weapons,
 * Matrix ranks, Refit and Honing levels, the deck seed, the omen offer, the draft deck and the recent-item weights. Written by the progression domain (the deck by its deal, the Rebirth profile by a run modifier); the rest
 * never changes during a run.
 */
internal class RunProfileCapture(
    content: GameplayContentSnapshot,
    source: RunProfileCapture?,
    seed: Int,
    initialRebirthLevel: Int,
    bootstrapProgress: GameplayProfileSnapshot?,
    runMode: RunMode,
    offerRunOmen: Boolean,
) {
    internal var activeRebirthProfile: RebirthProfile = source?.activeRebirthProfile
        ?: content.rebirth.profile(
            bootstrapProgress?.rebirthProgress?.level ?: initialRebirthLevel,
        )
    internal val unlockedWeaponSet: RunMutableSet<WeaponId> = source?.unlockedWeaponSet?.copy()
        ?: RunMutableSet(mutableSetOf<WeaponId>().apply {
            addAll(bootstrapProgress?.loadout?.unlockedWeapons.orEmpty())
            add(WeaponId.FLUX_WAKE)
        })
    internal var unlockedWeaponView: Set<WeaponId> = source?.unlockedWeaponView
        ?: unlockedWeaponSet.toSet()
    /** Owned Core Matrix ranks by content node index, captured from the profile at bootstrap. */
    internal val matrixRanks: RunIntArray = source?.matrixRanks?.copy()
        ?: RunIntArray(IntArray(content.matrixNodes.size) { index ->
            val definition = content.matrixNodes[index]
            (bootstrapProgress?.matrix?.rank(definition.id) ?: 0).coerceIn(0, definition.maxRanks)
        })
    /** Bought Refit levels by [CoreShape.ordinal], captured from the profile at bootstrap; a run never writes them. */
    internal val characterUpgradeLevels: RunIntArray = source?.characterUpgradeLevels?.copy()
        ?: RunIntArray(IntArray(CoreShape.entries.size) { index ->
            (bootstrapProgress?.upgrades?.characterLevels?.getOrElse(index) { 0 } ?: 0)
                .coerceIn(0, content.upgrades.maxLevel)
        })
    /** Bought Honing levels by [WeaponId.ordinal], captured from the profile at bootstrap; a run never writes them. */
    internal val weaponUpgradeLevels: RunIntArray = source?.weaponUpgradeLevels?.copy()
        ?: RunIntArray(IntArray(WeaponId.entries.size) { index ->
            (bootstrapProgress?.upgrades?.weaponLevels?.getOrElse(index) { 0 } ?: 0)
                .coerceIn(0, content.upgrades.maxLevel)
        })
    /** The draft deck stream's seed ([DECK_SEED_SALT]); the deck is dealt from it when the run starts. */
    internal val deckSeed: Int = source?.deckSeed ?: (seed xor DECK_SEED_SALT)
    /** The production start path offers a run omen beside the Heirloom; fixed when the run is created. */
    internal val offersRunOmen: Boolean = source?.offersRunOmen ?: offerRunOmen
    /** The run's draft deck (immutable, replaced when a refill deals); every item until the run deals one. */
    internal var itemDeck: ItemDeck = source?.itemDeck ?: ItemDeck.EVERYTHING
    /** Down-weights from the Profile's recent acquisitions, fixed for the run. */
    internal val itemHistory: ItemHistoryWeights = source?.itemHistory ?: ItemHistoryWeights.of(
        bootstrapProgress?.recentItemIds.orEmpty(), content.items.size, MAX_RECENT_ITEM_IDS,
        content.build.policy.draftDeck.historyPenalty, content.build.policy.draftDeck.historyFloor,
    )
}
