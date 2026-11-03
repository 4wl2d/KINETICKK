// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.nucleus.render.ChoiceType

/**
 * The open choice's type, rerolls left, offer pity and count, the open offer's rarity floor and the last acquired item.
 * Written by the progression domain.
 */
internal class OfferRunState(source: OfferRunState?) {
    internal var activeChoiceType: ChoiceType = source?.activeChoiceType ?: ChoiceType.ITEM
    internal var rerollsRemaining: Int = source?.rerollsRemaining ?: 1
    internal var offerPity: Int = source?.offerPity ?: 0
    /** Item offers opened this run; the Matrix widens every n-th one. */
    internal var itemOffersOpened: Int = source?.itemOffersOpened ?: 0
    /** The open item offer's rarity floor (null: none); rerolls keep it, finishing the choice clears it. */
    internal var offerRarityFloor: ItemRarity? = source?.offerRarityFloor
    internal var acquiredItemCount: Int = source?.acquiredItemCount ?: 0
    internal var recentItem: ItemDefinition? = source?.recentItem

    /**
     * A new run opens item offers, has opened none, has no pity and no acquisition, and starts with [rerolls] rerolls.
     * The rarity floor is cleared with the run variety.
     */
    fun reset(rerolls: Int) {
        activeChoiceType = ChoiceType.ITEM
        offerPity = 0
        itemOffersOpened = 0
        recentItem = null
        acquiredItemCount = 0
        rerollsRemaining = rerolls
    }
}
