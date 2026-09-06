// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.CharacterUnlockRequirement
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.toImmutableSet

internal fun isCoreShapeUnlocked(profile: PlayerProfile, definition: CoreShapeDefinition): Boolean {
    val progress = profile.characterAchievements
    val count = when (definition.unlockRequirement) {
        CharacterUnlockRequirement.AVAILABLE -> 0L
        CharacterUnlockRequirement.ELITE_KILLS -> progress.eliteKills
        CharacterUnlockRequirement.DASH_HITS -> progress.dashHits
        CharacterUnlockRequirement.COMPLETED_ORBITS -> progress.completedOrbits
        CharacterUnlockRequirement.ARCHITECT_VICTORIES -> progress.architectVictories
        CharacterUnlockRequirement.DISTINCT_CHARACTER_VICTORIES -> progress.victoriousCharacters.size.toLong()
    }
    return count >= definition.unlockTarget
}

internal fun unlockedCoreShapes(profile: PlayerProfile, policy: ProfilePolicySnapshot): ImmutableSet<CoreShape> =
    policy.coreShapes.filter { isCoreShapeUnlocked(profile, it) }.map { it.id }.toImmutableSet()
