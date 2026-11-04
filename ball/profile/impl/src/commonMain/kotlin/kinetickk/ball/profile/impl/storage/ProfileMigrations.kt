// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl.storage

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.ProfileSnapshotRejection

/**
 * Validates the epoch and the raw list bounds of either catalog generation before any id is mapped.
 * Returns true for a save of the retired catalog, whose discoveries are dropped unparsed: its Relic
 * names no longer exist and its item indices point into a different catalog.
 */
internal fun PlayerCollectionDto.isFromRetiredCatalog(): Boolean {
    val epoch = itemCatalogEpoch
    rejectUnless(
        epoch == null || epoch in 0..ContentBounds.COLLECTION_GENERATION,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    val retired = epoch == null || epoch < ContentBounds.COLLECTION_GENERATION
    val maxItems = if (retired) ContentBounds.LEGACY_MAX_ITEMS else ContentBounds.MAX_ITEMS
    val maxRelics = if (retired) ContentBounds.LEGACY_MAX_RELICS else ContentBounds.MAX_RELICS
    rejectUnless(
        discoveredItemIds.size <= maxItems && newItemIds.size <= maxItems &&
            discoveredRelicIds.size <= maxRelics && newRelicIds.size <= maxRelics &&
            discoveredReactionIds.size <= ReactionId.entries.size &&
            discoveredPairingIds.size <= RelicPairingId.entries.size &&
            heirloomRelicIds.size <= MAX_HEIRLOOM_RELICS &&
            recentItemIds.size <= MAX_RECENT_ITEM_IDS,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    // Only bits of defined discoveries; a sign or higher bit names none.
    rejectUnless(
        worldDiscoveryMask and WorldDiscovery.VALID_MASK.inv() == 0,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    // The retired codec never wrote these fields, so a save carrying them without an epoch was not
    // produced by any version.
    rejectUnless(
        !retired || discoveredReactionIds.isEmpty() && discoveredPairingIds.isEmpty() && heirloomRelicIds.isEmpty() &&
            recentItemIds.isEmpty() && worldDiscoveryMask == 0,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    return retired
}

/**
 * The retired Lab, frozen as it shipped: eight upgrades priced `baseCost × rank²` for each rank.
 * A legacy save must list exactly these wire ids in sorted order with ranks in 0..maxRanks; its
 * refund is the full price of every owned rank, so veterans keep their whole spend.
 */
internal object RetiredLabRefund {
    private class LegacyUpgrade(val wireId: String, val maxRanks: Int, val baseCost: Long)

    private val upgrades: List<LegacyUpgrade> = listOf(
        LegacyUpgrade("ARMORY_LICENSE", maxRanks = 12, baseCost = 45),
        LegacyUpgrade("CORE_INTEGRITY", maxRanks = 10, baseCost = 18),
        LegacyUpgrade("CRYO_VENTS", maxRanks = 8, baseCost = 26),
        LegacyUpgrade("DASH_CAPACITOR", maxRanks = 8, baseCost = 30),
        LegacyUpgrade("DATA_ARCHIVE", maxRanks = 10, baseCost = 38),
        LegacyUpgrade("KINETIC_AMPLIFIER", maxRanks = 10, baseCost = 22),
        LegacyUpgrade("MAGNETIC_RESONANCE", maxRanks = 8, baseCost = 24),
        LegacyUpgrade("SALVAGE_PROTOCOL", maxRanks = 10, baseCost = 34),
    )

    fun refund(lab: LabProgressDto): Long {
        rejectUnless(
            lab.ranks.map(MetaUpgradeRankDto::id) == upgrades.map(LegacyUpgrade::wireId),
            ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
        )
        var total = 0L
        lab.ranks.forEachIndexed { index, record ->
            val upgrade = upgrades[index]
            rejectUnless(record.rank in 0..upgrade.maxRanks, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
            for (rank in 1..record.rank) total += upgrade.baseCost * rank * rank
        }
        return total
    }
}
