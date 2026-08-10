// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.foundation.collections.ImmutableList

internal const val ARMORY_PAGE_SIZE = 3

internal sealed interface ArmoryAction {
    data object Back : ArmoryAction
    data object PreviousPage : ArmoryAction
    data object NextPage : ArmoryAction
    data class SelectWeapon(val id: WeaponId) : ArmoryAction
}

internal data class ArmoryReduction(
    val page: Int,
    val effects: List<ArmoryEffect> = emptyList(),
)

internal sealed interface ArmoryEffect {
    data class PurchaseOrEquipWeapon(val id: WeaponId) : ArmoryEffect
    data class PlayAudio(val cue: ProfileAudioCue) : ArmoryEffect
    data class Emit(val output: ArmoryOutput) : ArmoryEffect
}

internal class ArmoryReducer(
    private val weapons: ImmutableList<WeaponDefinition>,
) {
    val maxPage: Int
        get() = (weapons.size - 1) / ARMORY_PAGE_SIZE

    fun renderModel(
        snapshot: LoadoutProfileSnapshot,
        activeRunWeapon: WeaponId?,
    ): ArmoryRenderModel = ArmoryRenderModel(
        totalMatter = snapshot.economy.matter,
        selectedWeapon = snapshot.loadout.selectedWeapon,
        unlockedWeapons = snapshot.loadout.unlockedWeapons,
        activeRunWeapon = activeRunWeapon,
    )

    fun reduce(page: Int, action: ArmoryAction): ArmoryReduction = when (action) {
        ArmoryAction.Back -> ArmoryReduction(
            page.coerceIn(0, maxPage),
            effects = listOf(
                ArmoryEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                ArmoryEffect.Emit(ArmoryOutput.Back),
            ),
        )
        ArmoryAction.PreviousPage -> ArmoryReduction(
            (page.coerceIn(0, maxPage) - 1).coerceAtLeast(0),
            effects = listOf(ArmoryEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
        )
        ArmoryAction.NextPage -> ArmoryReduction(
            (page.coerceIn(0, maxPage) + 1).coerceAtMost(maxPage),
            effects = listOf(ArmoryEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
        )
        is ArmoryAction.SelectWeapon -> ArmoryReduction(
            page.coerceIn(0, maxPage),
            effects = listOf(ArmoryEffect.PurchaseOrEquipWeapon(action.id)),
        )
    }
}

internal data class ArmoryViewport(val width: Float, val height: Float, val density: Float)

internal fun resolveArmoryPress(
    viewport: ArmoryViewport,
    weapons: ImmutableList<WeaponDefinition>,
    page: Int,
    x: Float,
    y: Float,
): ArmoryAction? {
    val d = viewport.density
    val width = minOf(900f * d, viewport.width - 30f * d)
    val height = minOf(650f * d, viewport.height - 30f * d)
    val left = (viewport.width - width) * 0.5f
    val top = (viewport.height - height) * 0.5f
    val right = left + width
    val bottom = top + height
    val maxPage = (weapons.size - 1) / ARMORY_PAGE_SIZE
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
            val id = weapons.getOrNull(page.coerceIn(0, maxPage) * ARMORY_PAGE_SIZE + index)?.id
            return id?.let(ArmoryAction::SelectWeapon)
        }
    }
    return null
}
