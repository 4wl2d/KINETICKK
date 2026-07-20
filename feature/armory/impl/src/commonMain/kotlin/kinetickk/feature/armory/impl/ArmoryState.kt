// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.armory.impl

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.content.WeaponCatalog
import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.LoadoutCapability
import kinetickk.core.profile.api.ProfileMutationResult
import kinetickk.feature.armory.api.ArmoryRenderModel

internal const val ARMORY_PAGE_SIZE = 3

internal sealed interface ArmoryAction {
    data object Back : ArmoryAction
    data object PreviousPage : ArmoryAction
    data object NextPage : ArmoryAction
    data class SelectWeapon(val id: WeaponId) : ArmoryAction
}

internal data class ArmoryReduction(
    val page: Int,
    val close: Boolean = false,
    val profileChanged: Boolean = false,
    val feedbackCue: AudioCue? = null,
)

internal class ArmoryReducer(private val capability: LoadoutCapability) {
    val maxPage: Int
        get() = (WeaponCatalog.all.size - 1) / ARMORY_PAGE_SIZE

    fun renderModel(activeRunWeapon: WeaponId?): ArmoryRenderModel {
        val snapshot = capability.loadoutSnapshot()
        return ArmoryRenderModel(
            totalMatter = snapshot.economy.matter,
            selectedWeapon = snapshot.loadout.selectedWeapon,
            unlockedWeapons = snapshot.loadout.unlockedWeapons,
            activeRunWeapon = activeRunWeapon,
        )
    }

    fun reduce(page: Int, action: ArmoryAction): ArmoryReduction = when (action) {
        ArmoryAction.Back -> ArmoryReduction(
            page.coerceIn(0, maxPage),
            close = true,
            feedbackCue = AudioCue.UI_CLICK,
        )
        ArmoryAction.PreviousPage -> ArmoryReduction(
            (page.coerceIn(0, maxPage) - 1).coerceAtLeast(0),
            feedbackCue = AudioCue.UI_CLICK,
        )
        ArmoryAction.NextPage -> ArmoryReduction(
            (page.coerceIn(0, maxPage) + 1).coerceAtMost(maxPage),
            feedbackCue = AudioCue.UI_CLICK,
        )
        is ArmoryAction.SelectWeapon -> {
            val result = capability.purchaseOrEquipWeapon(action.id)
            val applied = result is ProfileMutationResult.Applied
            ArmoryReduction(
                page.coerceIn(0, maxPage),
                profileChanged = applied,
                feedbackCue = if (applied) AudioCue.PURCHASE else null,
            )
        }
    }
}

internal data class ArmoryViewport(val width: Float, val height: Float, val density: Float)

internal fun resolveArmoryPress(viewport: ArmoryViewport, page: Int, x: Float, y: Float): ArmoryAction? {
    val d = viewport.density
    val width = minOf(900f * d, viewport.width - 30f * d)
    val height = minOf(650f * d, viewport.height - 30f * d)
    val left = (viewport.width - width) * 0.5f
    val top = (viewport.height - height) * 0.5f
    val right = left + width
    val bottom = top + height
    val maxPage = (WeaponCatalog.all.size - 1) / ARMORY_PAGE_SIZE
    if (y > bottom - 55f * d) {
        return when {
            x < left + width * 0.45f -> ArmoryAction.Back
            x < right - 85f * d -> ArmoryAction.PreviousPage
            else -> ArmoryAction.NextPage
        }
    }
    val cardWidth = minOf(245f * d, (width - 80f * d) / 3f)
    val gap = 16f * d
    val startX = (viewport.width - (cardWidth * 3f + gap * 2f)) * 0.5f
    if (y !in top + 118f * d..bottom - 85f * d) return null
    repeat(3) { index ->
        val cardLeft = startX + index * (cardWidth + gap)
        if (x in cardLeft..cardLeft + cardWidth) {
            val id = WeaponCatalog.all.getOrNull(page.coerceIn(0, maxPage) * ARMORY_PAGE_SIZE + index)?.id
            return id?.let(ArmoryAction::SelectWeapon)
        }
    }
    return null
}
