// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.isAscendant
import kinetickk.ball.profile.api.rank
import kinetickk.flow.session.interaction.audio.SessionAudioCue
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

internal sealed interface HomeAction {
    data object StartRun : HomeAction
    /** The Workshop row: the Workshop on the tab it showed last. */
    data object OpenWorkshop : HomeAction
    data object OpenRebirth : HomeAction
    data object OpenCodex : HomeAction
    data object OpenSettings : HomeAction
    data object Quit : HomeAction
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
    private val upgrades: UpgradePolicy = UpgradePolicy(),
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
        // The Profile reads its own purchase rules for the stamp; Home never repeats them.
        workshopAffordable = projection.workshopAffordable,
        upgradeLevels = projection.upgradeLevels,
        upgradeLevelCap = upgrades.maxLevel * (coreShapes.size + weaponCount),
        matrixRanks = matrix?.let { progress ->
            matrixNodes.sumOf { node -> progress.rank(node.id).coerceIn(0, node.maxRanks) }
        } ?: 0,
        matrixMaxRanks = if (matrix == null) 0 else matrixNodes.sumOf { it.maxRanks },
        matrixOwnedNodes = matrix?.let { progress -> matrixNodes.count { node -> progress.rank(node.id) > 0 } } ?: 0,
        matrixNodeCount = if (matrix == null) 0 else matrixNodes.size,
        rebirthRank = projection.rebirthProgress.rank,
        ascendant = projection.rebirthProgress.isAscendant,
    )

    fun reduce(action: HomeAction): HomeReduction = when (action) {
        HomeAction.StartRun -> navigate(HomeOutput.StartRun)
        HomeAction.OpenWorkshop -> navigate(HomeOutput.OpenWorkshop)
        HomeAction.OpenRebirth -> navigate(HomeOutput.OpenRebirth)
        HomeAction.OpenCodex -> navigate(HomeOutput.OpenCodex)
        HomeAction.OpenSettings -> navigate(HomeOutput.OpenSettings)
        HomeAction.Quit -> navigate(HomeOutput.Quit)
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
    /** The host can close the game: Home lays out its Quit button. */
    val quitAvailable: Boolean = false,
)

internal fun resolveHomePress(viewport: HomeViewport, x: Float, y: Float): HomeAction? {
    val layout = homeLayoutGeometry(viewport.width, viewport.height, viewport.density, viewport.quitAvailable)
    val quit = layout.quit?.touch
    if (quit != null && x in quit.left..quit.right && y in quit.top..quit.bottom) return HomeAction.Quit
    val hit = layout
        .actions
        .firstOrNull { action ->
            x in action.bounds.left..action.bounds.right && y in action.bounds.top..action.bounds.bottom
        }
    return hit?.target?.toHomeAction()
}

/** Resolves a press on a drawn (!) info button (its expanded touch rect), if any. */
internal fun resolveHomeInfoPress(viewport: HomeViewport, x: Float, y: Float): HomeInfoTarget? =
    homeLayoutGeometry(viewport.width, viewport.height, viewport.density, viewport.quitAvailable)
        .infos
        .firstOrNull { info ->
            x in info.touch.left..info.touch.right && y in info.touch.top..info.touch.bottom
        }
        ?.target
