// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/** Cumulative achievement a character unlocks with; persisted counters live in the Profile. Append only. */
enum class CharacterUnlockRequirement {
    AVAILABLE,
    ELITE_KILLS,
    DASH_HITS,
    COMPLETED_ORBITS,
    ARCHITECT_VICTORIES,
    DISTINCT_CHARACTER_VICTORIES,
    TOTAL_KILLS,
    RICOCHETS,
    DATA_PICKUPS,
    /** Compared against the Reborn rank (highest cleared tier plus one, at most ten). */
    REBIRTH_RANK,
}

/** What feeds a character's mechanic; the Hangar shows it as a label. */
enum class CharacterTriggerKind(val displayLabel: String) {
    MOVEMENT("Movement"),
    KILLS("Kills"),
    PICKUPS("Pickups"),
    RHYTHM("Rhythm"),
    DASH("Dash"),
    BRAKE("Brake"),
    GEOMETRY("Geometry"),
}

/** Base stat changes a character applies when a run starts; neutral values change nothing. */
data class CharacterTraits(
    val massMultiplier: Float = 1f,
    val integrityBonus: Int = 0,
    val dragMultiplier: Float = 1f,
    val dashHeatMultiplier: Float = 1f,
    val radiusMultiplier: Float = 1f,
    val critChanceBonus: Float = 0f,
    val critDamageBonus: Float = 0f,
    val pickupRadiusBonus: Float = 0f,
) {
    init {
        require(massMultiplier in 0.5f..2f && dragMultiplier in 0.5f..2f && dashHeatMultiplier in 0.5f..2f)
        require(radiusMultiplier in 0.5f..2f && integrityBonus in -50..50)
        require(critChanceBonus in 0f..0.5f && critDamageBonus in 0f..1f && pickupRadiusBonus in 0f..300f)
    }
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
        CoreShape.STAR -> "Star"
        CoreShape.SPIRAL -> "Spiral"
        CoreShape.DIPOLE -> "Dipole"
        CoreShape.PENTAGON -> "Pentagon"
        CoreShape.CRESCENT -> "Crescent"
        CoreShape.HEXAGON -> "Hexagon"
    },
    val mechanicDescription: String = when (id) {
        CoreShape.ORB -> "Arc in one direction to charge Orbit. When it is full, your next impact or weapon hit " +
            "releases a ring wave and brands the struck enemy with your dominant element."
        CoreShape.PRISM -> "Four facets each negate one hit and regenerate over time. Elite kills and heavy impacts " +
            "restore them faster; while all four hold, your impacts hit harder."
        CoreShape.SHARD -> "Dash through enemies to mark them. A later impact or weapon hit punctures the mark for " +
            "heavy damage and vents heat."
        CoreShape.RING -> "A halo widens with your speed and burns enemies on its band. Accelerating through high " +
            "speed sends out a Halo Burst."
        CoreShape.DIAMOND -> "Start braking from speed to open a brief parry. A parry negates the hit, returns shots " +
            "and builds Edge; full Edge makes your next impact critical."
        CoreShape.TESSERACT -> "Separated Dash origins become lattice nodes. Closing the loop or placing the fourth " +
            "node collapses it along its edges."
        CoreShape.STAR -> "Kills gather Stellar Mass that strengthens impacts and grows the Core. Full mass, or an " +
            "impact kill past half, bursts into a Supernova; taking damage sheds mass."
        CoreShape.SPIRAL -> "An echo ghost retraces your path one second behind, striking the enemies it passes at " +
            "speed and absorbing a shot now and then."
        CoreShape.DIPOLE -> "Every real turn flips polarity. Attract pulls enemies toward the Core; Repel throws " +
            "them into obstacles and each other."
        CoreShape.PENTAGON -> "Act on the visible beat. On-beat Dashes cost less heat, on-beat impacts pulse and " +
            "weapons activate faster; keeping time builds a streak."
        CoreShape.CRESCENT -> "Dash becomes a two-charge Blink that keeps most of your speed and leaves a void scar " +
            "that detonates."
        CoreShape.HEXAGON -> "Data pickups build a hive of up to four drones that orbit the Core and dart at " +
            "nearby enemies."
    },
    val unlockRequirement: CharacterUnlockRequirement = when (id) {
        CoreShape.ORB -> CharacterUnlockRequirement.AVAILABLE
        CoreShape.PRISM -> CharacterUnlockRequirement.ELITE_KILLS
        CoreShape.SHARD -> CharacterUnlockRequirement.DASH_HITS
        CoreShape.RING -> CharacterUnlockRequirement.COMPLETED_ORBITS
        CoreShape.DIAMOND -> CharacterUnlockRequirement.ARCHITECT_VICTORIES
        CoreShape.TESSERACT -> CharacterUnlockRequirement.DISTINCT_CHARACTER_VICTORIES
        CoreShape.STAR -> CharacterUnlockRequirement.TOTAL_KILLS
        CoreShape.DIPOLE -> CharacterUnlockRequirement.RICOCHETS
        CoreShape.SPIRAL, CoreShape.PENTAGON, CoreShape.CRESCENT, CoreShape.HEXAGON ->
            CharacterUnlockRequirement.REBIRTH_RANK
    },
    val unlockTarget: Int = when (id) {
        CoreShape.ORB -> 0
        CoreShape.PRISM, CoreShape.TESSERACT -> 3
        CoreShape.SHARD -> 20
        CoreShape.RING, CoreShape.DIAMOND -> 1
        CoreShape.STAR -> 1_500
        CoreShape.SPIRAL -> 1
        CoreShape.DIPOLE -> 40
        CoreShape.PENTAGON -> 3
        CoreShape.CRESCENT -> 5
        CoreShape.HEXAGON -> 7
    },
    val unlockDescription: String = when (id) {
        CoreShape.ORB -> "Available from the start"
        CoreShape.PRISM -> "Defeat 3 elites across runs"
        CoreShape.SHARD -> "Land 20 Dash hits across runs"
        CoreShape.RING -> "Complete a Collapsing Orbit"
        CoreShape.DIAMOND -> "Defeat the Architect"
        CoreShape.TESSERACT -> "Win with 3 different characters"
        CoreShape.STAR -> "Destroy 1500 enemies across runs"
        CoreShape.SPIRAL -> "Reach Reborn rank 1"
        CoreShape.DIPOLE -> "Ricochet off obstacles 40 times"
        CoreShape.PENTAGON -> "Reach Reborn rank 3"
        CoreShape.CRESCENT -> "Reach Reborn rank 5"
        CoreShape.HEXAGON -> "Reach Reborn rank 7"
    },
    val mechanicName: String = when (id) {
        CoreShape.ORB -> "Orbit"
        CoreShape.PRISM -> "Facets"
        CoreShape.SHARD -> "Puncture"
        CoreShape.RING -> "Halo"
        CoreShape.DIAMOND -> "Parry"
        CoreShape.TESSERACT -> "Lattice"
        CoreShape.STAR -> "Nova"
        CoreShape.SPIRAL -> "Echo"
        CoreShape.DIPOLE -> "Polarity"
        CoreShape.PENTAGON -> "Pulse"
        CoreShape.CRESCENT -> "Eclipse"
        CoreShape.HEXAGON -> "Hive"
    },
    val triggerKind: CharacterTriggerKind = when (id) {
        CoreShape.ORB, CoreShape.RING, CoreShape.SPIRAL, CoreShape.DIPOLE -> CharacterTriggerKind.MOVEMENT
        CoreShape.PRISM, CoreShape.STAR -> CharacterTriggerKind.KILLS
        CoreShape.SHARD, CoreShape.CRESCENT -> CharacterTriggerKind.DASH
        CoreShape.DIAMOND -> CharacterTriggerKind.BRAKE
        CoreShape.TESSERACT -> CharacterTriggerKind.GEOMETRY
        CoreShape.PENTAGON -> CharacterTriggerKind.RHYTHM
        CoreShape.HEXAGON -> CharacterTriggerKind.PICKUPS
    },
    val traits: CharacterTraits = when (id) {
        CoreShape.ORB, CoreShape.PENTAGON -> CharacterTraits()
        CoreShape.PRISM -> CharacterTraits(massMultiplier = 1.25f, dragMultiplier = 1.15f, integrityBonus = 20)
        CoreShape.SHARD -> CharacterTraits(critChanceBonus = 0.08f, integrityBonus = -15, radiusMultiplier = 0.9f,
            dashHeatMultiplier = 0.85f)
        CoreShape.RING -> CharacterTraits(dragMultiplier = 0.95f)
        CoreShape.DIAMOND -> CharacterTraits(critDamageBonus = 0.2f)
        CoreShape.TESSERACT -> CharacterTraits(dashHeatMultiplier = 0.9f)
        CoreShape.STAR -> CharacterTraits(integrityBonus = 10)
        CoreShape.SPIRAL -> CharacterTraits(dragMultiplier = 0.92f)
        CoreShape.DIPOLE -> CharacterTraits(massMultiplier = 1.1f)
        CoreShape.CRESCENT -> CharacterTraits(integrityBonus = -10, radiusMultiplier = 0.95f)
        CoreShape.HEXAGON -> CharacterTraits(pickupRadiusBonus = 60f, integrityBonus = -5)
    },
    /** 1 (straightforward) to 3 (demanding). */
    val difficulty: Int = when (id) {
        CoreShape.ORB, CoreShape.PRISM, CoreShape.RING, CoreShape.STAR, CoreShape.HEXAGON -> 1
        CoreShape.SHARD, CoreShape.SPIRAL, CoreShape.DIPOLE, CoreShape.CRESCENT -> 2
        CoreShape.DIAMOND, CoreShape.TESSERACT, CoreShape.PENTAGON -> 3
    },
) {
    init {
        require(displayName.isNotBlank() && mechanicDescription.isNotBlank() && unlockDescription.isNotBlank())
        require(mechanicName.isNotBlank())
        require(unlockTarget >= 0)
        require(unlockRequirement != CharacterUnlockRequirement.AVAILABLE || unlockTarget == 0)
        require(difficulty in 1..3)
    }
}

fun defaultCoreShapeDefinitions(): ImmutableList<CoreShapeDefinition> =
    CoreShape.entries.map { CoreShapeDefinition(it) }.toImmutableList()
