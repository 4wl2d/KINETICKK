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
    data class SelectCoreShape(val shape: CoreShape) : HomeEffect
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
                HomeEffect.SelectCoreShape(action.shape),
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
