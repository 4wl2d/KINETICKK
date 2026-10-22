// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/**
 * A character's level-cap ability. It runs for [durationSeconds], then recharges over
 * [cooldownSeconds]; Data credited after the cap shortens the recharge, never below
 * [minimumCooldownSeconds]. Against boss-class enemies it spends at most [bossStrikes] strikes and
 * erases at most [boltBudget] hostile bolts per activation. [values] are read by index in the order
 * [defaultApexDefinition] documents for the shape.
 */
data class ApexDefinition(
    val name: String,
    val description: String,
    val durationSeconds: Float,
    val cooldownSeconds: Float = DEFAULT_APEX_COOLDOWN_SECONDS,
    val minimumCooldownSeconds: Float = DEFAULT_APEX_MINIMUM_COOLDOWN_SECONDS,
    val bossStrikes: Int = DEFAULT_APEX_BOSS_STRIKES,
    val boltBudget: Int,
    val values: ImmutableList<Float>,
) {
    init {
        require(name.isNotBlank() && description.isNotBlank()) { "An Apex needs a name and a description" }
        require(cooldownSeconds.isFinite() && cooldownSeconds > 0f) { "Apex cooldown must be finite and positive" }
        require(durationSeconds.isFinite() && durationSeconds > 0f && durationSeconds < cooldownSeconds) {
            "Apex duration must be positive and shorter than its cooldown"
        }
        require(minimumCooldownSeconds.isFinite() && minimumCooldownSeconds in durationSeconds..cooldownSeconds) {
            "Apex minimum cooldown must lie between its duration and its cooldown"
        }
        require(bossStrikes in 0..ContentBounds.MAX_APEX_STRIKES) {
            "Apex boss strikes $bossStrikes exceed ${ContentBounds.MAX_APEX_STRIKES}"
        }
        require(boltBudget in 0..ContentBounds.MAX_APEX_BOLTS) {
            "Apex bolt budget $boltBudget exceeds ${ContentBounds.MAX_APEX_BOLTS}"
        }
        require(values.size <= ContentBounds.MAX_APEX_VALUES) {
            "Apex values size ${values.size} exceeds ${ContentBounds.MAX_APEX_VALUES}"
        }
        require(values.all(Float::isFinite)) { "Apex values must be finite" }
    }

    companion object {
        const val DEFAULT_APEX_COOLDOWN_SECONDS: Float = 60f
        const val DEFAULT_APEX_MINIMUM_COOLDOWN_SECONDS: Float = 30f
        const val DEFAULT_APEX_BOSS_STRIKES: Int = 6
    }
}

/**
 * The shipped Apex of each character. Values, in order:
 * - ORB Perihelion: Orbit charge arc rad, capture radius, held shot damage, release interval s
 * - PRISM Monolith: facet regrowth s, full-facet impact multiplier, refraction damage
 * - SHARD Vivisect: puncture interval s, mark spread radius, boss strike scale, free Dash gap s
 * - RING Corona: locked halo radius, band burn per second, burst interval s, burst damage
 * - DIAMOND Mirror Stance: returned shot damage, locked Edge
 * - TESSERACT Hypercube: cage half-side, inside speed factor, pulse damage, pulse damage per node
 * - STAR Hypernova: Stellar Mass on cast, nova cooldown s, Mass per kill, Mass at the end
 * - SPIRAL Twin Echo: second ghost delay s, strike impact scale, absorb interval s, strike speed
 * - DIPOLE Magnetar: polarity flip interval s, reach scale, Repel throw scale, returned shot damage
 * - PENTAGON Crescendo: beats, beat burst damage, beat burst radius, bolts erased per beat
 * - CRESCENT Total Eclipse: Blink recharge s, cut radius, scar pull speed, scar detonation scale
 * - HEXAGON Swarm Queen: dart interval s, dart damage, Data latch range, drone life s
 */
fun defaultApexDefinition(shape: CoreShape): ApexDefinition = when (shape) {
    CoreShape.ORB -> apex(
        "Perihelion",
        "For 10 s Orbit charges on half the arc and every release captures hostile shots; when it ends, they fly back.",
        10f, 24, 0.349f, 210f, 45f, 0.1f,
    )
    CoreShape.PRISM -> apex(
        "Monolith",
        "Restores every facet; for 8 s facets regrow fast and each blocked hit fires a refraction back.",
        8f, 16, 2f, 1.4f, 90f,
    )
    CoreShape.SHARD -> apex(
        "Vivisect",
        "For 8 s Dashes cost no heat, punctures come faster and every puncture marks the next enemy.",
        8f, 0, 0.12f, 260f, 1.5f, 0.35f,
    )
    CoreShape.RING -> apex(
        "Corona",
        "For 10 s the halo holds its widest ring, burns three times hotter and burns away shots that cross it.",
        10f, 32, 165f, 36f, 1.5f, 32f,
    )
    CoreShape.DIAMOND -> apex(
        "Mirror Stance",
        "For 6 s, while you brake, every hit is parried and returned at full Edge.",
        6f, 24, 65f, 5f,
    )
    CoreShape.TESSERACT -> apex(
        "Hypercube",
        "Folds your lattice into a cage for 8 s: shots crossing it vanish, enemies inside slow and its edges pulse.",
        8f, 30, 220f, 0.5f, 7f, 3.5f,
    )
    CoreShape.STAR -> apex(
        "Hypernova",
        "Bursts into a full Supernova at once; for 8 s novas come twice as fast, hits shed no mass and novas erase shots.",
        8f, 48, 24f, 1f, 3f, 12f,
    )
    CoreShape.SPIRAL -> apex(
        "Twin Echo",
        "For 10 s a second ghost follows half a second behind; both strike and swallow shots three times as often.",
        10f, 30, 0.5f, 0.32f, 0.5f, 300f,
    )
    CoreShape.DIPOLE -> apex(
        "Magnetar",
        "For 8 s polarity flips every second, its reach grows and incoming shots are flung back.",
        8f, 20, 1f, 1.4f, 1.5f, 30f,
    )
    CoreShape.PENTAGON -> apex(
        "Crescendo",
        "For twelve beats every move lands on the beat, the streak stays full and each beat erases nearby shots.",
        9f, 72, 12f, 40f, 140f, 6f,
    )
    CoreShape.CRESCENT -> apex(
        "Total Eclipse",
        "For 8 s Blink recharges almost at once, each Blink cuts through shots and scars pull enemies in.",
        8f, 40, 0.5f, 40f, 300f, 1.6f,
    )
    CoreShape.HEXAGON -> apex(
        "Swarm Queen",
        "Fills the hive; for 10 s drones dart three times as often, intercept shots and pull Data to you.",
        10f, 32, 0.35f, 18f, 600f, 12f,
    )
}

private fun apex(
    name: String,
    description: String,
    durationSeconds: Float,
    boltBudget: Int,
    vararg values: Float,
): ApexDefinition = ApexDefinition(
    name = name,
    description = description,
    durationSeconds = durationSeconds,
    boltBudget = boltBudget,
    values = values.toList().toImmutableList(),
)
