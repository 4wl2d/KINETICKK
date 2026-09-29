// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.ball.content.api.CharacterUnlockRequirement
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgress
import kinetickk.flow.session.interaction.audio.SessionAudioCue
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

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
    private val weapons: ImmutableList<WeaponDefinition> = immutableListOf(),
    private val relicCount: Int = 0,
    private val metaUpgrades: ImmutableList<MetaUpgradeDefinition> = immutableListOf(),
) {
    /** Maps the authoritative Home projection (and, when read, Lab ranks) into render facts. */
    fun uiModel(projection: HomeProgressProjection, lab: LabProgress? = null): HomeUiModel = HomeUiModel(
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
        labRanks = lab?.let { progress ->
            metaUpgrades.sumOf { upgrade -> progress.rank(upgrade.id).coerceIn(0, upgrade.maxRanks) }
        } ?: 0,
        labMaxRanks = if (lab == null) 0 else metaUpgrades.sumOf { it.maxRanks },
        labMaxedUpgrades = lab?.let { progress ->
            metaUpgrades.count { upgrade -> progress.rank(upgrade.id) >= upgrade.maxRanks }
        } ?: 0,
        labUpgradeCount = if (lab == null) 0 else metaUpgrades.size,
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

/** Cumulative progress toward a form's unlock goal, as counted by the Profile achievements. */
internal fun coreShapeUnlockProgress(
    shape: CoreShapeDefinition,
    achievements: CharacterAchievementProgress,
): Long = when (shape.unlockRequirement) {
    CharacterUnlockRequirement.AVAILABLE -> 1L
    CharacterUnlockRequirement.ELITE_KILLS -> achievements.eliteKills
    CharacterUnlockRequirement.DASH_HITS -> achievements.dashHits
    CharacterUnlockRequirement.COMPLETED_ORBITS -> achievements.completedOrbits
    CharacterUnlockRequirement.ARCHITECT_VICTORIES -> achievements.architectVictories
    CharacterUnlockRequirement.DISTINCT_CHARACTER_VICTORIES -> achievements.victoriousCharacters.size.toLong()
}.coerceAtMost(shape.unlockTarget.toLong())

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
