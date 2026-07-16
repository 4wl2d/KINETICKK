// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.persistence.resources

import kinetickk.flows.persistence.ProgressPersistenceSchema
import kinetickk.flows.persistence.model.PersistedProgress

/**
 * Second quarantine for a Resource-supplied bootstrap value.
 *
 * A custom [ProgressStore] is still an untrusted capability at Assembly. This function bounds its
 * already-materialized collections, rejects non-finite numeric settings, and constructs one new
 * normalized snapshot before revision zero. The raw object is never retained by the Game Ball.
 */
internal fun quarantineBootstrapProgress(progress: PersistedProgress): ProgressLoadResult {
    if (
        progress.unlockedWeaponIndices.size > ProgressPersistenceSchema.WEAPON_CODE_COUNT ||
        progress.metaLevels.size > ProgressPersistenceSchema.META_UPGRADE_CODE_COUNT ||
        progress.discoveredItemIds.size > ProgressPersistenceSchema.ITEM_ID_COUNT
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
    val rebirthLevel = progress.rebirthLevel.coerceIn(
        0,
        ProgressPersistenceSchema.MAX_REBIRTH_LEVEL,
    )
    val unlockedWeapons = progress.unlockedWeaponIndices
        .filterTo(mutableSetOf(), ProgressPersistenceSchema::isSupportedWeaponCode)
        .apply { add(ProgressPersistenceSchema.BASELINE_WEAPON_CODE) }
    val selectedWeapon = progress.selectedWeaponIndex
        .takeIf(ProgressPersistenceSchema::isSupportedWeaponCode)
        ?.takeIf(unlockedWeapons::contains)
        ?: ProgressPersistenceSchema.BASELINE_WEAPON_CODE
    val normalizedMetaLevels = List(ProgressPersistenceSchema.META_UPGRADE_CODE_COUNT) { index ->
        progress.metaLevels.getOrNull(index)
            ?.coerceIn(0, requireNotNull(ProgressPersistenceSchema.maxMetaUpgradeRank(index)))
            ?: 0
    }
    val discoveries = progress.discoveredItemIds
        .filterTo(mutableSetOf(), ProgressPersistenceSchema::isSupportedItemId)

    return ProgressLoadResult.Loaded(
        PersistedProgress(
            matter = matter,
            lifetimeMatter = progress.lifetimeMatter.coerceAtLeast(matter),
            coreShapeIndex = progress.coreShapeIndex.coerceIn(
                0,
                ProgressPersistenceSchema.CORE_SHAPE_CODE_COUNT - 1,
            ),
            selectedWeaponIndex = selectedWeapon,
            unlockedWeaponIndices = unlockedWeapons,
            metaLevels = normalizedMetaLevels,
            discoveredItemIds = discoveries,
            settings = rawSettings.normalized(),
            rebirthLevel = rebirthLevel,
            highestClearedRebirth = progress.highestClearedRebirth.coerceIn(-1, rebirthLevel),
        ),
    )
}
