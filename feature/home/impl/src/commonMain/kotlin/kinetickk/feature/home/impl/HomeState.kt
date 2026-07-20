// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.home.impl

import kinetickk.core.content.CoreShape
import kinetickk.core.content.RebirthProgression
import kinetickk.core.profile.api.CollectionCapability
import kinetickk.core.profile.api.LoadoutCapability
import kinetickk.core.profile.api.ProfileMutationResult
import kinetickk.core.profile.api.RebirthCapability
import kinetickk.feature.home.api.HomeOutput
import kinetickk.feature.home.api.HomeUiModel

internal sealed interface HomeAction {
    data class SelectCoreShape(val shape: CoreShape) : HomeAction
    data object StartRun : HomeAction
    data object OpenLab : HomeAction
    data object OpenArmory : HomeAction
    data object OpenRebirth : HomeAction
    data object OpenCodex : HomeAction
    data object OpenSettings : HomeAction
}

internal class HomeReducer(
    private val loadoutCapability: LoadoutCapability,
    private val collectionCapability: CollectionCapability,
    private val rebirthCapability: RebirthCapability,
) {
    fun uiModel(): HomeUiModel {
        val loadout = loadoutCapability.loadoutSnapshot()
        val collection = collectionCapability.collectionSnapshot()
        val rebirth = rebirthCapability.rebirthSnapshot().progress
        return HomeUiModel(
            coreShape = loadout.loadout.coreShape,
            totalMatter = loadout.economy.matter,
            lifetimeMatter = loadout.economy.lifetimeMatter,
            discoveredItemCount = collection.discoveredItemIds.size,
            unlockedWeaponCount = loadout.loadout.unlockedWeapons.size,
            rebirthLevel = rebirth.level,
            rebirthProfile = RebirthProgression.profile(rebirth.level),
            canRebirth = rebirth.level < RebirthProgression.MAX_LEVEL &&
                rebirth.highestCleared >= rebirth.level,
        )
    }

    fun reduce(action: HomeAction): HomeOutput? = when (action) {
        is HomeAction.SelectCoreShape -> when (loadoutCapability.selectCoreShape(action.shape)) {
            is ProfileMutationResult.Applied -> null
            is ProfileMutationResult.Rejected -> null
        }
        HomeAction.StartRun -> HomeOutput.StartRun
        HomeAction.OpenLab -> HomeOutput.OpenLab
        HomeAction.OpenArmory -> HomeOutput.OpenArmory
        HomeAction.OpenRebirth -> HomeOutput.OpenRebirth
        HomeAction.OpenCodex -> HomeOutput.OpenCodex
        HomeAction.OpenSettings -> HomeOutput.OpenSettings
    }
}

internal data class HomeViewport(
    val width: Float,
    val height: Float,
    val density: Float,
)

internal fun resolveHomePress(viewport: HomeViewport, x: Float, y: Float): HomeAction? {
    val d = viewport.density
    val cardY = viewport.height * 0.62f
    if (y in cardY - 55f * d..cardY + 55f * d) {
        val center = viewport.width * 0.5f
        when {
            x in center - 190f * d..center - 70f * d -> return HomeAction.SelectCoreShape(CoreShape.ORB)
            x in center - 60f * d..center + 60f * d -> return HomeAction.SelectCoreShape(CoreShape.PRISM)
            x in center + 70f * d..center + 190f * d -> return HomeAction.SelectCoreShape(CoreShape.SHARD)
        }
    }

    val buttonY = viewport.height * 0.78f
    if (
        x in viewport.width * 0.5f - 150f * d..viewport.width * 0.5f + 150f * d &&
        y in buttonY - 31f * d..buttonY + 31f * d
    ) {
        return HomeAction.StartRun
    }

    val secondaryY = viewport.height * 0.9f
    if (y !in secondaryY - 20f * d..secondaryY + 20f * d) return null
    val spacing = minOf(132f * d, viewport.width * 0.19f)
    val start = viewport.width * 0.5f - spacing * 2f
    val index = ((x - start) / spacing).toInt().let { floor ->
        val fraction = (x - start) / spacing - floor
        if (fraction >= 0.5f) floor + 1 else floor
    }
    val itemCenter = start + index * spacing
    if (index !in 0..4 || x !in itemCenter - spacing * 0.44f..itemCenter + spacing * 0.44f) return null
    return when (index) {
        0 -> HomeAction.OpenLab
        1 -> HomeAction.OpenArmory
        2 -> HomeAction.OpenRebirth
        3 -> HomeAction.OpenCodex
        else -> HomeAction.OpenSettings
    }
}
