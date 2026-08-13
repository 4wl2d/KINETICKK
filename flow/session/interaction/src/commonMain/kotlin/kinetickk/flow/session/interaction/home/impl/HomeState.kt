// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.flow.session.interaction.audio.SessionAudioCue
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.foundation.collections.ImmutableList

internal sealed interface HomeAction {
    data class SelectCoreShape(val shape: CoreShape) : HomeAction
    data object StartRun : HomeAction
    data object OpenLab : HomeAction
    data object OpenArmory : HomeAction
    data object OpenRebirth : HomeAction
    data object OpenCodex : HomeAction
    data object OpenSettings : HomeAction
}

internal sealed interface HomeEffect {
    data class PlayAudio(val cue: SessionAudioCue) : HomeEffect
    data class Emit(val output: HomeOutput) : HomeEffect
}

internal data class HomeReduction(
    val effects: List<HomeEffect>,
)

internal class HomeReducer(
    private val coreShapes: ImmutableList<CoreShapeDefinition>,
    private val itemCount: Int,
    private val weaponCount: Int,
    private val rebirthPolicy: RebirthPolicySnapshot,
) {
    fun uiModel(projection: HomeProgressProjection): HomeUiModel = HomeUiModel(
        coreShape = projection.loadout.coreShape,
        totalMatter = projection.economy.matter,
        lifetimeMatter = projection.economy.lifetimeMatter,
        discoveredItemCount = projection.collection.discoveredItemIds.size,
        unlockedWeaponCount = projection.loadout.unlockedWeapons.size,
        rebirthLevel = projection.rebirthProgress.level,
        rebirthProfile = rebirthPolicy.profile(projection.rebirthProgress.level),
        canRebirth = projection.canAdvanceRebirth,
        coreShapes = coreShapes,
        itemCount = itemCount,
        weaponCount = weaponCount,
    )

    fun reduce(action: HomeAction): HomeReduction = when (action) {
        is HomeAction.SelectCoreShape -> HomeReduction(
            effects = listOf(
                HomeEffect.Emit(HomeOutput.SelectCoreShape(action.shape)),
                HomeEffect.PlayAudio(SessionAudioCue.UI_CLICK),
            ),
        )
        HomeAction.StartRun -> navigate(HomeOutput.StartRun)
        HomeAction.OpenLab -> navigate(HomeOutput.OpenLab)
        HomeAction.OpenArmory -> navigate(HomeOutput.OpenArmory)
        HomeAction.OpenRebirth -> navigate(HomeOutput.OpenRebirth)
        HomeAction.OpenCodex -> navigate(HomeOutput.OpenCodex)
        HomeAction.OpenSettings -> navigate(HomeOutput.OpenSettings)
    }

    private fun navigate(output: HomeOutput): HomeReduction = HomeReduction(
        effects = listOf(
            HomeEffect.PlayAudio(SessionAudioCue.UI_CLICK),
            HomeEffect.Emit(output),
        ),
    )
}

internal data class HomeViewport(
    val width: Float,
    val height: Float,
    val density: Float,
)

internal fun resolveHomePress(viewport: HomeViewport, x: Float, y: Float): HomeAction? {
    val hit = homeLayoutGeometry(viewport.width, viewport.height, viewport.density)
        .actions
        .firstOrNull { action ->
            x in action.bounds.left..action.bounds.right && y in action.bounds.top..action.bounds.bottom
        }
    return hit?.target?.toHomeAction()
}
