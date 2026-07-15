// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.model

/** A non-elemental resonance carried by a Relic. */
enum class RelicAspect(val displayLabel: String) {
    VECTOR("Vector"),
    GRAVITIC("Gravitic"),
    ION("Ion"),
    RIFT("Rift"),
    PRISM("Prism"),
    ENTROPY("Entropy"),
    SOVEREIGN("Sovereign"),
}

enum class RelicId {
    KINETIC_FLYWHEEL,
    GHOST_VECTOR,
    OVERTAKE_PROTOCOL,
    SLIPSTREAM_RELAY,
    BRAKEPOINT_MEMORY,
    POLARITY_SLING,

    ORBITAL_NAIL,
    EVENTIDE_ANCHOR,
    PERIAPSIS_HOOK,
    CRUSH_DEPTH,
    MASS_ECHO,
    TIDAL_LOCK,

    VOLTAIC_FILAMENT,
    STATIC_CHORUS,
    ION_DEBT,
    CIRCUIT_BREAKER,
    RETURN_CIRCUIT,
    STORM_INDEX,

    ECHO_CHAMBER,
    PALIMPSEST_ROUND,
    SECOND_HAND,
    FRACTURE_GATE,
    SPLIT_HORIZON,
    BORROWED_MOMENT,

    GLASS_WITNESS,
    FRACTURE_LENS,
    SPECTRAL_FAN,
    HARDLIGHT_EDGE,
    CHROMA_FEEDBACK,
    MIRROR_CUT,

    HEAT_DEBT,
    SCAR_TISSUE,
    QUIETUS_BLOOM,
    DEVOURERS_TOLL,
    DOOM_CLOCK,
    LAST_LIGHT,

    AGONY_SCEPTER,
    CROWN_OF_FOUR_WINDS,
    MIRROR_OF_THE_HUNT,
    ENGINE_OF_PARADOX,
}

data class RelicDefinition(
    val id: RelicId,
    val name: String,
    val aspect: RelicAspect,
    val description: String,
    val rankEffect: String,
) {
    init {
        require(name.isNotBlank()) { "Relic name must not be blank" }
        require(description.isNotBlank()) { "Relic description must not be blank" }
        require(rankEffect.isNotBlank()) { "Relic rank effect must not be blank" }
    }

    val isSovereign: Boolean get() = aspect == RelicAspect.SOVEREIGN
}

data class EquippedRelic(
    val id: RelicId,
    val rank: Int,
) {
    init {
        require(rank in 1..RelicCatalog.MAX_RANK) { "Relic rank must be between 1 and ${RelicCatalog.MAX_RANK}" }
    }
}

object RelicCatalog {
    const val RELIC_COUNT: Int = 40
    const val MAX_SLOTS: Int = 4
    const val MAX_RANK: Int = 5

    val all: List<RelicDefinition> = listOf(
        RelicDefinition(
            RelicId.KINETIC_FLYWHEEL,
            "Kinetic Flywheel",
            RelicAspect.VECTOR,
            "The weapon stores excess velocity as striking force instead of losing it to drag.",
            "+5% weapon damage above 500 u/s per rank; the bonus doubles above 1,600 u/s.",
        ),
        RelicDefinition(
            RelicId.GHOST_VECTOR,
            "Ghost Vector",
            RelicAspect.VECTOR,
            "Every dash leaves a cutting vector through enemies near the Core's departure point.",
            "+24 dash-cut damage and +14 effect radius per rank.",
        ),
        RelicDefinition(
            RelicId.OVERTAKE_PROTOCOL,
            "Overtake Protocol",
            RelicAspect.VECTOR,
            "The weapon predicts hostile motion and punishes enemies that refuse to slow down.",
            "+7% weapon damage per rank against enemies moving at 170 u/s or faster.",
        ),
        RelicDefinition(
            RelicId.SLIPSTREAM_RELAY,
            "Slipstream Relay",
            RelicAspect.VECTOR,
            "Kills made at high velocity feed a short activation surge into the next weapon cycle.",
            "+6% activation speed per rank for 3 seconds after a kill above 500 u/s.",
        ),
        RelicDefinition(
            RelicId.BRAKEPOINT_MEMORY,
            "Brakepoint Memory",
            RelicAspect.VECTOR,
            "Gravity Brake stores arrested momentum; the next weapon contact releases it all at once.",
            "Stores up to +18% next-hit damage per rank while braking.",
        ),
        RelicDefinition(
            RelicId.POLARITY_SLING,
            "Polarity Sling",
            RelicAspect.VECTOR,
            "A strained tether slings weapon force through the weakest point in the magnetic field.",
            "Up to +8% weapon damage per rank as Polarity falls toward zero.",
        ),

        RelicDefinition(
            RelicId.ORBITAL_NAIL,
            "Orbital Nail",
            RelicAspect.GRAVITIC,
            "Weapon contact nails a target to the Core's local gravity well.",
            "Pulls the target 24 u/s toward the Core per rank on each qualified hit.",
        ),
        RelicDefinition(
            RelicId.EVENTIDE_ANCHOR,
            "Eventide Anchor",
            RelicAspect.GRAVITIC,
            "Destroyed enemies collapse into brief anchors that drag their formation inward.",
            "+18 implosion damage and +18 radius per rank on kill.",
        ),
        RelicDefinition(
            RelicId.PERIAPSIS_HOOK,
            "Periapsis Hook",
            RelicAspect.GRAVITIC,
            "Distant targets fall harder through the weapon's curved approach.",
            "+8% weapon damage per rank beyond 300 units from the Core.",
        ),
        RelicDefinition(
            RelicId.CRUSH_DEPTH,
            "Crush Depth",
            RelicAspect.GRAVITIC,
            "Targets deep inside the Core's pressure field are compressed before impact.",
            "+6% weapon damage per rank within 155 units of the Core.",
        ),
        RelicDefinition(
            RelicId.MASS_ECHO,
            "Mass Echo",
            RelicAspect.GRAVITIC,
            "The weapon borrows the Core's mass and returns it as recoil through its victim.",
            "+4% damage per Core mass and stronger knockback per rank.",
        ),
        RelicDefinition(
            RelicId.TIDAL_LOCK,
            "Tidal Lock",
            RelicAspect.GRAVITIC,
            "Repeated contacts align the weapon and target into an increasingly violent orbit.",
            "+2% damage per rank and qualified hit, stacking five times per target.",
        ),

        RelicDefinition(
            RelicId.VOLTAIC_FILAMENT,
            "Voltaic Filament",
            RelicAspect.ION,
            "Weapon contact extrudes a live filament toward the nearest untouched enemy.",
            "Arcs 16% of the triggering damage per rank, with a 0.28-second gate.",
        ),
        RelicDefinition(
            RelicId.STATIC_CHORUS,
            "Static Chorus",
            RelicAspect.ION,
            "A rhythm of weapon contacts resolves into a many-voiced discharge.",
            "Every seventh qualified hit arcs to 1 additional target per rank.",
        ),
        RelicDefinition(
            RelicId.ION_DEBT,
            "Ion Debt",
            RelicAspect.ION,
            "Each contact leaves charge the target must eventually repay to everything nearby.",
            "Five qualified hits discharge 14 area damage per rank.",
        ),
        RelicDefinition(
            RelicId.CIRCUIT_BREAKER,
            "Circuit Breaker",
            RelicAspect.ION,
            "The first weapon contact interrupts a target's movement circuit and cracks its guard.",
            "+11 opening damage and 8% movement interruption per rank.",
        ),
        RelicDefinition(
            RelicId.RETURN_CIRCUIT,
            "Return Circuit",
            RelicAspect.ION,
            "When no nearby body can carry the current onward, it folds back through its first host.",
            "+9% weapon damage per rank against isolated targets.",
        ),
        RelicDefinition(
            RelicId.STORM_INDEX,
            "Storm Index",
            RelicAspect.ION,
            "At Surge velocity, the weapon indexes every fourth contact as a free lightning route.",
            "+10 arc damage and +45 acquisition range per rank.",
        ),

        RelicDefinition(
            RelicId.ECHO_CHAMBER,
            "Echo Chamber",
            RelicAspect.RIFT,
            "A portion of every strike is hidden outside time and returned after the target has moved.",
            "Repeats 12% of qualified-hit damage per rank after 0.45 seconds.",
        ),
        RelicDefinition(
            RelicId.PALIMPSEST_ROUND,
            "Palimpsest Round",
            RelicAspect.RIFT,
            "The weapon periodically writes its previous impact over the present one.",
            "Every seventh qualified hit repeats 20% of that damage per rank.",
        ),
        RelicDefinition(
            RelicId.SECOND_HAND,
            "Second Hand",
            RelicAspect.RIFT,
            "A kill steals a fraction of the next second and hands it to the weapon clock.",
            "Removes 0.08 seconds per rank from both weapon cooldowns on kill.",
        ),
        RelicDefinition(
            RelicId.FRACTURE_GATE,
            "Fracture Gate",
            RelicAspect.RIFT,
            "A repeating contact folds its target through the Core and ruptures its exit point.",
            "Every sixth qualified hit transposes the target and deals 12 rupture damage per rank.",
        ),
        RelicDefinition(
            RelicId.SPLIT_HORIZON,
            "Split Horizon",
            RelicAspect.RIFT,
            "A destroyed enemy leaves one half of its final strike hunting beyond the horizon.",
            "Kills launch a seeking shard for 18 damage per rank.",
        ),
        RelicDefinition(
            RelicId.BORROWED_MOMENT,
            "Borrowed Moment",
            RelicAspect.RIFT,
            "Damage to the Core is answered with a brief interval stolen from the attacker.",
            "+9% activation speed per rank for 2.5 seconds after taking damage.",
        ),

        RelicDefinition(
            RelicId.GLASS_WITNESS,
            "Glass Witness",
            RelicAspect.PRISM,
            "The first strike is remembered as a fault line that later hits can see and exploit.",
            "Exposes fresh targets to +7% weapon damage per rank for 3 seconds.",
        ),
        RelicDefinition(
            RelicId.FRACTURE_LENS,
            "Fracture Lens",
            RelicAspect.PRISM,
            "Existing wounds bend probability until the weapon finds the most brittle outcome.",
            "+2.5% critical chance per rank against injured targets.",
        ),
        RelicDefinition(
            RelicId.SPECTRAL_FAN,
            "Spectral Fan",
            RelicAspect.PRISM,
            "A regular cadence of impacts fans one strike into two pale refractions.",
            "Every sixth qualified hit refracts 14% damage per rank into two nearby targets.",
        ),
        RelicDefinition(
            RelicId.HARDLIGHT_EDGE,
            "Hardlight Edge",
            RelicAspect.PRISM,
            "Critical strikes condense into an edge too coherent to disperse on contact.",
            "+12% critical damage per rank.",
        ),
        RelicDefinition(
            RelicId.CHROMA_FEEDBACK,
            "Chroma Feedback",
            RelicAspect.PRISM,
            "The first color returned by each enemy is recycled into the Core's defenses.",
            "First contact restores 1.5 Shield—or 2 Overdrive—per rank.",
        ),
        RelicDefinition(
            RelicId.MIRROR_CUT,
            "Mirror Cut",
            RelicAspect.PRISM,
            "A critical wound appears again in the nearest body as if both occupied one reflection.",
            "Critical hits mirror 15% of their damage per rank to another target.",
        ),

        RelicDefinition(
            RelicId.HEAT_DEBT,
            "Heat Debt",
            RelicAspect.ENTROPY,
            "Weapon damage is not spent; it accumulates as an unstable debt inside the target.",
            "Qualified hits build debt that ruptures for 16 area damage per rank.",
        ),
        RelicDefinition(
            RelicId.SCAR_TISSUE,
            "Scar Tissue",
            RelicAspect.ENTROPY,
            "Each strike teaches the target's structure how to keep damaging itself.",
            "Applies 3 damage per second per rank for 3 seconds; repeated hits intensify it.",
        ),
        RelicDefinition(
            RelicId.QUIETUS_BLOOM,
            "Quietus Bloom",
            RelicAspect.ENTROPY,
            "A kill blossoms into a field that ages motion and matter around it.",
            "+10 decay damage, +10% slow, and +16 radius per rank on kill.",
        ),
        RelicDefinition(
            RelicId.DEVOURERS_TOLL,
            "Devourer's Toll",
            RelicAspect.ENTROPY,
            "Every death pays a small portion of its remaining possibility back to the Core.",
            "Every third kill restores 1.5 Integrity and Shield per rank.",
        ),
        RelicDefinition(
            RelicId.DOOM_CLOCK,
            "Doom Clock",
            RelicAspect.ENTROPY,
            "Elite systems and the Architect are forced to acknowledge how little time they have left.",
            "+8% damage per rank to Elites and the Architect, rising with run time.",
        ),
        RelicDefinition(
            RelicId.LAST_LIGHT,
            "Last Light",
            RelicAspect.ENTROPY,
            "Near fracture, the Core burns its final safe future to make the current one lethal.",
            "+12% weapon damage and +8% activation speed per rank below 35% Integrity.",
        ),

        RelicDefinition(
            RelicId.AGONY_SCEPTER,
            "Agony Scepter",
            RelicAspect.SOVEREIGN,
            "The Scepter discovers a forbidden extension unique to whichever weapon is synchronized.",
            "Strengthens the weapon-specific mutation per rank; all twelve weapons mutate differently.",
        ),
        RelicDefinition(
            RelicId.CROWN_OF_FOUR_WINDS,
            "Crown of Four Winds",
            RelicAspect.SOVEREIGN,
            "The Crown rewards a matrix whose four slots disagree without falling out of resonance.",
            "+4% damage and +3% activation speed per rank for each distinct non-Sovereign aspect.",
        ),
        RelicDefinition(
            RelicId.MIRROR_OF_THE_HUNT,
            "Mirror of the Hunt",
            RelicAspect.SOVEREIGN,
            "The first wound in every enemy is reflected into a second enemy across the Core.",
            "The reflected opening strike carries 20% of its damage per rank.",
        ),
        RelicDefinition(
            RelicId.ENGINE_OF_PARADOX,
            "Engine of Paradox",
            RelicAspect.SOVEREIGN,
            "Entering Overdrive rewinds the weapon and vents heat from a future that never happened.",
            "On Overdrive: reset cooldowns, vent 10 Heat, and gain 10% activation speed per rank.",
        ),
    ).also(::validateRelics)

    fun byId(id: RelicId): RelicDefinition = all[id.ordinal]
}

private fun validateRelics(relics: List<RelicDefinition>) {
    check(RelicId.entries.size == RelicCatalog.RELIC_COUNT) { "RelicId must contain exactly 40 relics" }
    check(relics.size == RelicCatalog.RELIC_COUNT) { "Relic catalog must contain exactly 40 relics" }
    check(relics.withIndex().all { (index, relic) -> relic.id.ordinal == index }) {
        "Relic catalog order must match RelicId order"
    }
    check(relics.map { it.name }.toSet().size == relics.size) { "Relic names must be unique" }
    check(relics.map { it.description }.toSet().size == relics.size) { "Relic descriptions must be unique" }
    check(relics.map { it.rankEffect }.toSet().size == relics.size) { "Relic rank effects must be unique" }
    val standardCounts = relics.filterNot(RelicDefinition::isSovereign).groupingBy(RelicDefinition::aspect).eachCount()
    RelicAspect.entries.filter { it != RelicAspect.SOVEREIGN }.forEach { aspect ->
        check(standardCounts[aspect] == 6) { "$aspect must contain exactly six relics" }
    }
    check(relics.count(RelicDefinition::isSovereign) == 4) { "Relic catalog must contain exactly four Sovereign relics" }
}
