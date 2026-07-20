// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.data.progress

import kinetickk.feature.game.domain.model.CoreShape
import kinetickk.feature.game.domain.model.ItemCatalog
import kinetickk.feature.game.domain.model.MetaUpgradeCatalog
import kinetickk.feature.game.domain.model.MetaUpgradeId
import kinetickk.feature.game.domain.model.RebirthProgression
import kinetickk.feature.game.domain.model.StoredProgress
import kinetickk.feature.game.domain.model.WeaponId
import kinetickk.feature.game.domain.port.progress.*

/**
 * Second quarantine for a Resource-supplied bootstrap value.
 *
 * A custom [ProgressStore] is still an untrusted capability at Assembly. This function bounds its
 * already-materialized collections, rejects non-finite numeric settings, and constructs one new
 * normalized snapshot before revision zero. The raw object is never retained by the game engine.
 */
fun quarantineBootstrapProgress(progress: StoredProgress): ProgressLoadResult {
    if (
        progress.unlockedWeaponIndices.size > WeaponId.entries.size ||
        progress.metaLevels.size > MetaUpgradeId.entries.size ||
        progress.discoveredItemIds.size > ItemCatalog.ITEM_COUNT
    ) {
        return ProgressLoadResult.Rejected(
            ProgressLoadRejection.BOOTSTRAP_COLLECTION_LIMIT_EXCEEDED,
        )
    }

    val rawSettings = progress.settings
    if (
        !rawSettings.masterVolume.isFinite() ||
        !rawSettings.simulationSpeed.isFinite() ||
        !rawSettings.textScale.isFinite()
    ) {
        return ProgressLoadResult.Rejected(ProgressLoadRejection.BOOTSTRAP_NON_FINITE_NUMBER)
    }

    val matter = progress.matter.coerceAtLeast(0L)
    val rebirthLevel = progress.rebirthLevel.coerceIn(0, RebirthProgression.MAX_LEVEL)
    val unlockedWeapons = progress.unlockedWeaponIndices
        .filterTo(mutableSetOf()) { it in WeaponId.entries.indices }
        .apply { add(WeaponId.FLUX_WAKE.ordinal) }
    val normalizedMetaLevels = List(MetaUpgradeId.entries.size) { index ->
        progress.metaLevels.getOrNull(index)
            ?.coerceIn(0, MetaUpgradeCatalog.all[index].maxRanks)
            ?: 0
    }
    val discoveries = progress.discoveredItemIds
        .filterTo(mutableSetOf()) { it in 0 until ItemCatalog.ITEM_COUNT }

    return ProgressLoadResult.Loaded(
        StoredProgress(
            matter = matter,
            lifetimeMatter = progress.lifetimeMatter.coerceAtLeast(matter),
            coreShapeIndex = progress.coreShapeIndex.coerceIn(0, CoreShape.entries.lastIndex),
            selectedWeaponIndex = progress.selectedWeaponIndex.coerceIn(0, WeaponId.entries.lastIndex),
            unlockedWeaponIndices = unlockedWeapons,
            metaLevels = normalizedMetaLevels,
            discoveredItemIds = discoveries,
            settings = rawSettings.normalized(),
            rebirthLevel = rebirthLevel,
            highestClearedRebirth = progress.highestClearedRebirth.coerceIn(-1, rebirthLevel),
        ),
    )
}
