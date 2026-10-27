// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.CharacterUnlockRequirement
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.HangarShapeProgress
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.highestClearedWith
import kinetickk.ball.profile.api.rank
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet

/** The Profile counter a character's unlock requirement compares against its target. */
internal fun characterUnlockProgress(profile: PlayerProfile, definition: CoreShapeDefinition): Long {
    val progress = profile.characterAchievements
    return when (definition.unlockRequirement) {
        CharacterUnlockRequirement.AVAILABLE -> 0L
        CharacterUnlockRequirement.ELITE_KILLS -> progress.eliteKills
        CharacterUnlockRequirement.DASH_HITS -> progress.dashHits
        CharacterUnlockRequirement.COMPLETED_ORBITS -> progress.completedOrbits
        CharacterUnlockRequirement.ARCHITECT_VICTORIES -> progress.architectVictories
        CharacterUnlockRequirement.DISTINCT_CHARACTER_VICTORIES -> progress.victoriousCharacters.size.toLong()
        CharacterUnlockRequirement.TOTAL_KILLS -> progress.totalKills
        CharacterUnlockRequirement.RICOCHETS -> progress.ricochets
        CharacterUnlockRequirement.DATA_PICKUPS -> progress.dataPickups
        CharacterUnlockRequirement.REBIRTH_RANK -> profile.rebirthProgress.rank.toLong()
    }
}

internal fun isCoreShapeUnlocked(profile: PlayerProfile, definition: CoreShapeDefinition): Boolean =
    characterUnlockProgress(profile, definition) >= definition.unlockTarget

internal fun unlockedCoreShapes(profile: PlayerProfile, policy: ProfilePolicySnapshot): ImmutableSet<CoreShape> =
    policy.coreShapes.filter { isCoreShapeUnlocked(profile, it) }.map { it.id }.toImmutableSet()

/** Per-character unlock state in stable order; progress is capped at the target. */
internal fun hangarShapeProgress(profile: PlayerProfile, policy: ProfilePolicySnapshot): ImmutableList<HangarShapeProgress> =
    policy.coreShapes.map { definition ->
        val target = definition.unlockTarget.toLong()
        val progress = characterUnlockProgress(profile, definition)
        HangarShapeProgress(
            shape = definition.id,
            unlocked = progress >= target,
            progress = progress.coerceIn(0L, target),
            target = target,
            victorious = definition.id in profile.characterAchievements.victoriousCharacters,
            highestCleared = profile.rebirthProgress.highestClearedWith(definition.id),
        )
    }.toImmutableList()
