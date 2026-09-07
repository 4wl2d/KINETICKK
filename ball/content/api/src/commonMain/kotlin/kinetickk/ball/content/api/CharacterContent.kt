// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

enum class CharacterUnlockRequirement {
    AVAILABLE,
    ELITE_KILLS,
    DASH_HITS,
    COMPLETED_ORBITS,
    ARCHITECT_VICTORIES,
    DISTINCT_CHARACTER_VICTORIES,
}

/** Content owns the character mechanic and achievement requirements; Profile evaluates access. */
data class CoreShapeDefinition(
    val id: CoreShape,
    val displayName: String = when (id) {
        CoreShape.ORB -> "Circle"
        CoreShape.PRISM -> "Square"
        CoreShape.SHARD -> "Triangle"
        CoreShape.RING -> "Ring"
        CoreShape.DIAMOND -> "Diamond"
        CoreShape.TESSERACT -> "Tesseract"
    },
    val mechanicDescription: String = when (id) {
        CoreShape.ORB -> "Follow a real arc to charge a circular wave released by the next primary hit."
        CoreShape.PRISM -> "Brake from speed to build a temporary barrier. Dash spends its charge on a ram."
        CoreShape.SHARD -> "Dash through enemies to mark them briefly; a subsequent hit opens each mark."
        CoreShape.RING -> "Movement expands a damaging ring. Brake contracts it, preserving a safe inner dead zone."
        CoreShape.DIAMOND -> "The opening of Brake can parry an approaching threat and release a counterattack."
        CoreShape.TESSERACT -> "Separated real Dash positions build a bounded lattice. Closing it or Brake collapses it."
    },
    val unlockRequirement: CharacterUnlockRequirement = when (id) {
        CoreShape.ORB -> CharacterUnlockRequirement.AVAILABLE
        CoreShape.PRISM -> CharacterUnlockRequirement.ELITE_KILLS
        CoreShape.SHARD -> CharacterUnlockRequirement.DASH_HITS
        CoreShape.RING -> CharacterUnlockRequirement.COMPLETED_ORBITS
        CoreShape.DIAMOND -> CharacterUnlockRequirement.ARCHITECT_VICTORIES
        CoreShape.TESSERACT -> CharacterUnlockRequirement.DISTINCT_CHARACTER_VICTORIES
    },
    val unlockTarget: Int = when (id) {
        CoreShape.ORB -> 0
        CoreShape.PRISM, CoreShape.TESSERACT -> 3
        CoreShape.SHARD -> 20
        CoreShape.RING, CoreShape.DIAMOND -> 1
    },
    val unlockDescription: String = when (id) {
        CoreShape.ORB -> "Available from the start"
        CoreShape.PRISM -> "Defeat 3 elites across runs"
        CoreShape.SHARD -> "Land 20 Dash hits across runs"
        CoreShape.RING -> "Complete a Collapsing Orbit"
        CoreShape.DIAMOND -> "Defeat the Architect"
        CoreShape.TESSERACT -> "Win with 3 different characters"
    },
) {
    init {
        require(displayName.isNotBlank() && mechanicDescription.isNotBlank() && unlockDescription.isNotBlank())
        require(unlockTarget >= 0)
        require(unlockRequirement != CharacterUnlockRequirement.AVAILABLE || unlockTarget == 0)
    }
}

fun defaultCoreShapeDefinitions(): ImmutableList<CoreShapeDefinition> =
    CoreShape.entries.map { CoreShapeDefinition(it) }.toImmutableList()
