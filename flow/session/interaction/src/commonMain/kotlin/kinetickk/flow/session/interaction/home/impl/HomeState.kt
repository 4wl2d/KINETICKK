// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.flow.session.interaction.audio.SessionAudioCue
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

internal sealed interface HomeAction {
    data object OpenHangar : HomeAction
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
    private val weapons: ImmutableList<WeaponDefinition> = immutableListOf(),
    private val relicCount: Int = 0,
    private val matrixNodes: ImmutableList<MatrixNodeDefinition> = immutableListOf(),
) {
    /** Maps the authoritative Home projection (and, when read, the Matrix ranks) into render facts. */
    fun uiModel(projection: HomeProgressProjection, matrix: MatrixProgress? = null): HomeUiModel = HomeUiModel(
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
        unlockedCoreShapes = projection.unlockedCoreShapes,
        characterAchievements = projection.characterAchievements,
        discoveredRelicCount = projection.collection.discoveredRelicIds.size,
        relicCount = relicCount,
        startingWeaponName = weapons.firstOrNull { it.id == projection.loadout.selectedWeapon }?.name,
        weaponUnlockAffordable = weapons.any { weapon ->
            weapon.id !in projection.loadout.unlockedWeapons &&
                projection.economy.matter >= weapon.permanentUnlockCost.toLong()
        },
        matrixRanks = matrix?.let { progress ->
            matrixNodes.sumOf { node -> progress.rank(node.id).coerceIn(0, node.maxRanks) }
        } ?: 0,
        matrixMaxRanks = if (matrix == null) 0 else matrixNodes.sumOf { it.maxRanks },
        matrixOwnedNodes = matrix?.let { progress -> matrixNodes.count { node -> progress.rank(node.id) > 0 } } ?: 0,
        matrixNodeCount = if (matrix == null) 0 else matrixNodes.size,
    )

    fun reduce(action: HomeAction): HomeReduction = when (action) {
        HomeAction.OpenHangar -> navigate(HomeOutput.OpenHangar)
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

/** Resolves a press on a drawn (!) info button (its expanded touch rect), if any. */
internal fun resolveHomeInfoPress(viewport: HomeViewport, x: Float, y: Float): HomeInfoTarget? =
    homeLayoutGeometry(viewport.width, viewport.height, viewport.density)
        .infos
        .firstOrNull { info ->
            x in info.touch.left..info.touch.right && y in info.touch.top..info.touch.bottom
        }
        ?.target
