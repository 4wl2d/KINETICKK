// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/** Immutable build vocabulary shared by gameplay and every catalog reader. */
data class BuildCatalog(
    val elements: ImmutableList<ElementDefinition>,
    val reactions: ImmutableList<ReactionDefinition>,
    val resonances: ImmutableList<TagResonanceDefinition>,
    val affinityTiers: ImmutableList<AffinityTierDefinition>,
    val pairings: ImmutableList<RelicPairingDefinition>,
    val policy: BuildPolicy = BuildPolicy(),
) {
    init {
        require(elements.map { it.element } == StatusElement.entries) { "StatusElement definitions follow StatusElement order" }
        require(reactions.map { it.id } == ReactionId.entries) { "Reaction definitions follow ReactionId order" }
        require(resonances.map { it.tag } == BuildTag.entries) { "Resonance definitions follow BuildTag order" }
        require(affinityTiers.size == ContentBounds.RESONANCE_TIERS)
        require(pairings.size <= ContentBounds.MAX_RELIC_PAIRINGS) { "Relic pairings size ${pairings.size} exceeds ${ContentBounds.MAX_RELIC_PAIRINGS}" }
        require(pairings.map { it.id } == RelicPairingId.entries) { "Pairings follow RelicPairingId order" }
        require(pairings.indices.all { i ->
            (0 until i).none { j -> pairings[j].joins(pairings[i].first, pairings[i].second) }
        }) { "Each pair of Relics has at most one named pairing" }
    }

    fun element(element: StatusElement): ElementDefinition = elements[element.ordinal]

    fun reaction(id: ReactionId): ReactionDefinition = reactions[id.ordinal]

    fun resonance(tag: BuildTag): TagResonanceDefinition = resonances[tag.ordinal]

    fun pairing(a: RelicId, b: RelicId): RelicPairingDefinition? = pairings.firstOrNull { it.joins(a, b) }

    fun pairing(id: RelicPairingId): RelicPairingDefinition = pairings[id.ordinal]
}

fun defaultBuildCatalog(): BuildCatalog = DefaultBuildCatalog

private val DefaultBuildCatalog = BuildCatalog(
    elements = immutableListOf(
        ElementDefinition(
            StatusElement.THERMAL,
            "Each stack burns the target every second.",
            "Ignite: the stacks explode around the target.",
            breakThreshold = 8, decaySeconds = 1.6f, perStack = 3f, breakPower = 7f, breakRadius = 90f, breakFloor = 5,
        ),
        ElementDefinition(
            StatusElement.CRYO,
            "Each stack slows the target.",
            "Freeze: the target stops and becomes Brittle; the next direct hit strikes harder. A thawed enemy resists the next Freeze for a while.",
            breakThreshold = 6, decaySeconds = 1.8f, perStack = 0.07f, breakPower = 1.4f, breakRadius = 0f, breakFloor = 4,
        ),
        ElementDefinition(
            StatusElement.ION,
            "Direct hits on a charged target arc to a nearby enemy.",
            "Overload: lightning chains through three enemies and charges them.",
            breakThreshold = 7, decaySeconds = 2.4f, perStack = 4f, breakPower = 9f, breakRadius = 300f, breakFloor = 4,
        ),
        ElementDefinition(
            StatusElement.GRAVITIC,
            "Each stack makes the target take more impact damage and drift toward other marked enemies.",
            "Collapse: nearby enemies are dragged into the target and crushed together.",
            breakThreshold = 6, decaySeconds = 2.2f, perStack = 0.04f, breakPower = 8f, breakRadius = 150f, breakFloor = 4,
        ),
        ElementDefinition(
            StatusElement.ENTROPY,
            "Each stack makes the target take more damage from every source.",
            "Rupture: the target loses a share of its maximum integrity.",
            breakThreshold = 10, decaySeconds = 3f, perStack = 0.03f, breakPower = 0.10f, breakRadius = 0f, breakFloor = 7,
        ),
        ElementDefinition(
            StatusElement.SONIC,
            "Each stack makes the target take more knockback and more impact damage.",
            "Shatter: a shockwave throws nearby enemies away and hurts them.",
            breakThreshold = 7, decaySeconds = 3.5f, perStack = 0.04f, breakPower = 6f, breakRadius = 160f, breakFloor = 4,
        ),
        ElementDefinition(
            StatusElement.VOID,
            "Each stack weakens the target's attacks and makes it remember a share of the damage it takes.",
            "Unmake: a moment later the target suffers the remembered damage again, amplified.",
            breakThreshold = 8, decaySeconds = 3f, perStack = 0.03f, breakPower = 1.6f, breakRadius = 0f, breakFloor = 5,
        ),
    ),
    reactions = immutableListOf(
        ReactionDefinition(ReactionId.THERMAL_SHOCK, "Thermal Shock",
            "Fire meets ice: a violent single-target blast that leaves the target Brittle.", power = 12f, radius = 0f),
        ReactionDefinition(ReactionId.PLASMA_BURST, "Plasma Burst",
            "Heat ionizes into an explosion that sets everything it reaches burning.", power = 6f, radius = 110f,
            residue = StatusElement.THERMAL, residueStacks = 2),
        ReactionDefinition(ReactionId.FUSION, "Fusion",
            "The target becomes a small star that drags enemies in and burns them.", power = 3f, radius = 150f,
            residue = StatusElement.THERMAL, residueStacks = 1),
        ReactionDefinition(ReactionId.WILDFIRE, "Wildfire",
            "Rot catches fire and leaps to nearby enemies as Thermal.", power = 5f, radius = 160f,
            residue = StatusElement.THERMAL, residueStacks = 2),
        ReactionDefinition(ReactionId.SUPERCONDUCT, "Superconduct",
            "Cold lightning races through three enemies and chills them.", power = 7f, radius = 320f,
            residue = StatusElement.CRYO, residueStacks = 2),
        ReactionDefinition(ReactionId.GLACIER_CRUSH, "Glacier Crush",
            "Gravity shatters the ice into piercing shards.", power = 4f, radius = 0f,
            residue = StatusElement.CRYO, residueStacks = 1),
        ReactionDefinition(ReactionId.EMBRITTLE, "Embrittle",
            "Frozen rot: the target takes far more damage and freezes sooner.", power = 0.35f, radius = 0f),
        ReactionDefinition(ReactionId.MAGNETIZE, "Magnetize",
            "The target turns magnetic and yanks nearby enemies into a collision.", power = 5f, radius = 200f,
            residue = StatusElement.GRAVITIC, residueStacks = 1),
        ReactionDefinition(ReactionId.FEEDBACK, "Feedback",
            "Current tears through decayed armor and arcs onward, spreading Entropy.", power = 0.05f, radius = 260f,
            residue = StatusElement.ENTROPY, residueStacks = 2),
        ReactionDefinition(ReactionId.ROT_WELL, "Rot Well",
            "A rotting well opens on the target, pulling enemies in and decaying them.", power = 3f, radius = 140f,
            residue = StatusElement.ENTROPY, residueStacks = 1),
        ReactionDefinition(ReactionId.THUNDERCLAP, "Thunderclap",
            "Heat and sound burst outward in a blast that hurls enemies away.", power = 6f, radius = 150f),
        ReactionDefinition(ReactionId.SHATTERPOINT, "Shatterpoint",
            "The frozen ring of sound splits into five ice shards that chill what they pierce.", power = 4f, radius = 0f,
            residue = StatusElement.CRYO, residueStacks = 1),
        ReactionDefinition(ReactionId.RESONANT_ARC, "Resonant Arc",
            "Lightning rides the resonance through four enemies; more Resonance reaches farther.", power = 6f, radius = 220f),
        ReactionDefinition(ReactionId.SEISMIC, "Seismic",
            "A quake crushes nearby enemies and briefly stuns the ordinary ones.", power = 4f, radius = 180f),
        ReactionDefinition(ReactionId.FRACTURE, "Fracture",
            "The vibration cracks rot open and spreads Decay to everything close.", power = 3f, radius = 120f,
            residue = StatusElement.ENTROPY, residueStacks = 3),
        ReactionDefinition(ReactionId.SILENCE, "Silence",
            "Sound falls into the void: nearby enemies take damage and cannot attack for two seconds.", power = 4f, radius = 170f),
        ReactionDefinition(ReactionId.BLACKFIRE, "Blackfire",
            "A black flame burns the target for three seconds, ignoring every resistance and immunity.", power = 5f, radius = 0f),
        ReactionDefinition(ReactionId.STASIS, "Stasis",
            "Time stops around the target: ordinary enemies halt in place and hostile shots vanish.", power = 3f, radius = 140f),
        ReactionDefinition(ReactionId.ANNIHILATE, "Annihilate",
            "The void strikes the target and the toughest enemy nearby at once.", power = 9f, radius = 300f),
        ReactionDefinition(ReactionId.NULL_WELL, "Null Well",
            "A well of nothing pulls enemies in and strips a share of their maximum integrity.", power = 0.04f, radius = 150f),
        ReactionDefinition(ReactionId.OBLIVION, "Oblivion",
            "Rot meets the void: a weakened enemy is erased outright, anything sturdier takes a heavy burst.", power = 6f,
            radius = 0f),
    ),
    resonances = immutableListOf(
        TagResonanceDefinition(BuildTag.RAM, "Collisions, mass and raw momentum.", immutableListOf(
            "Impact damage +15 %.",
            "Impacts above 900 u/s splash 35 % of their damage around the target.",
            "Above 1,200 u/s impacts keep your momentum and deal 25 % more.",
        )),
        TagResonanceDefinition(BuildTag.DRIVE, "Speed, Dash and the line you carve.", immutableListOf(
            "Dash heat −15 %.",
            "Dash hits apply 2 stacks of your dominant element.",
            "The first Dash hit of each Dash refunds 60 % of its heat.",
        )),
        TagResonanceDefinition(BuildTag.ANCHOR, "Brake, control and the art of stopping.", immutableListOf(
            "Brake cools 50 % faster.",
            "Releasing Brake pushes enemies away and splashes 40 % impact damage.",
            "Releasing Brake breaks every status on nearby enemies that is at least 60 % full.",
        )),
        TagResonanceDefinition(BuildTag.ARSENAL, "The weapon system and its cadence.", immutableListOf(
            "Activation speed +10 %.",
            "Weapon power +20 %.",
            "Every fourth weapon hit adds 1 stack of the weapon's element or your dominant element, and Breaks deal 25 % more damage.",
        )),
        TagResonanceDefinition(BuildTag.PRECISION, "Critical hits and weak points.", immutableListOf(
            "Critical chance +6 %.",
            "Critical hits deepen every status on the target by 1 stack.",
            "Critical hits against enemies with a status deal 40 % more.",
        )),
        TagResonanceDefinition(BuildTag.BULWARK, "Shields, integrity and recovery.", immutableListOf(
            "Shield capacity +18.",
            "Shields start recharging after 2.2 seconds instead of 4.",
            "A breaking shield grants a brief invulnerability, once every 20 seconds.",
        )),
        TagResonanceDefinition(BuildTag.OVERCLOCK, "Heat, cooling and Overdrive.", immutableListOf(
            "Cooling +15 %.",
            "Overdrive gain +30 %.",
            "Entering Overdrive vents all heat and adds 2 seconds.",
        )),
        TagResonanceDefinition(BuildTag.CHAIN, "Kills, combos and momentum between targets.", immutableListOf(
            "Combo window +1 second.",
            "Every twelfth combo kill bursts around the victim.",
            "A lapsing combo halves instead of resetting.",
        )),
        TagResonanceDefinition(BuildTag.FORTUNE, "Data, Matter, luck and choice.", immutableListOf(
            "Luck +0.2.",
            "One extra reroll every four levels.",
            "Reborn cards appear 10 % more often, even before the first Rebirth.",
        )),
    ),
    affinityTiers = immutableListOf(
        AffinityTierDefinition("Statuses of this element are 25 % stronger."),
        AffinityTierDefinition("Its Break needs one stack less and hits 40 % harder."),
        AffinityTierDefinition("Overflow: its Breaks splash 2 stacks to nearby enemies."),
    ),
    pairings = immutableListOf(
        pairing(RelicPairingId.ENDLESS_GYRE, RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE, RelicPairingKind.CONCORD,
            "Endless Gyre", "Above 1,200 u/s every turn fires a second, wider ring at 60 % damage a moment later."),
        pairing(RelicPairingId.ASSASSINS_LINE, RelicId.BLINK_DRIVE, RelicId.EXECUTIONERS_PRISM, RelicPairingKind.CONCORD,
            "Assassin's Line", "Blink strikes always crit and leave their targets Brittle."),
        pairing(RelicPairingId.MELTDOWN, RelicId.FURNACE_COVENANT, RelicId.OVERDRIVE_CASCADE, RelicPairingKind.CONCORD,
            "Meltdown", "Overdrive starts at full heat, and the heat bonus counts half again during Overdrive."),
        pairing(RelicPairingId.COLD_VERDICT, RelicId.ABSOLUTE_ZERO, RelicId.EXECUTIONERS_PRISM, RelicPairingKind.CONCORD,
            "Cold Verdict", "Shatters always crit, and hits on enemies with 4 or more Cryo always crit."),
        pairing(RelicPairingId.LIGHTNING_ROD, RelicId.STORMHEART_CAPACITOR, RelicId.PERPETUAL_VECTOR, RelicPairingKind.CONCORD,
            "Lightning Rod", "Above 1,400 u/s the capacitor charges 50 % faster, and a discharge throws you forward."),
        pairing(RelicPairingId.BLACK_STAR, RelicId.EVENT_HORIZON, RelicId.NEUTRON_HEART, RelicPairingKind.CONCORD,
            "Black Star", "Collapses at the Core strike 50 % harder, but hostile bullets hit you 50 % harder."),
        pairing(RelicPairingId.BLIGHT_GARDEN, RelicId.VERDANT_ROT, RelicId.PANDEMIC_CODEX, RelicPairingKind.CONCORD,
            "Blight Garden", "Contagion passes 1 extra Entropy, and every Rupture near you heals 4."),
        pairing(RelicPairingId.FIRESTORM, RelicId.CINDER_CROWN, RelicId.PANDEMIC_CODEX, RelicPairingKind.CONCORD,
            "Firestorm", "A dying carrier detonates half of its bank around it."),
        pairing(RelicPairingId.FORBIDDEN_ECHO, RelicId.ECHO_CHAMBER, RelicId.AGONY_SCEPTER, RelicPairingKind.CONCORD,
            "Forbidden Echo", "Echoes strike 20 % harder and apply 1 stack of your dominant element."),
        pairing(RelicPairingId.ROT_WARD, RelicId.AEGIS_INVERSION, RelicId.VERDANT_ROT, RelicPairingKind.CONCORD,
            "Rot Ward", "The rot feeds the inverted shield, and every nova applies 3 Entropy."),
        pairing(RelicPairingId.STILL_WATERS, RelicId.STASIS_ANCHOR, RelicId.ABSOLUTE_ZERO, RelicPairingKind.CONCORD,
            "Still Waters", "Released bullets apply 2 Cryo, and stasis slows 10 % more."),
        pairing(RelicPairingId.FOLD_VECTOR, RelicId.BLINK_DRIVE, RelicId.PERPETUAL_VECTOR, RelicPairingKind.CONCORD,
            "Fold Vector", "Blinks keep 55 % of your speed, but lose their heat discount."),
        pairing(RelicPairingId.HOLLOW_VOW, RelicId.AEGIS_INVERSION, RelicId.OUROBOROS_SEAL, RelicPairingKind.CONCORD,
            "Hollow Vow", "The seal fills your shield and restores 30 % of your integrity."),
        pairing(RelicPairingId.FARADAY_CAGE, RelicId.STORMHEART_CAPACITOR, RelicId.AEGIS_INVERSION, RelicPairingKind.CONCORD,
            "Faraday Cage", "A self-discharge strikes your shield first, and every nova applies 3 Ion."),
        pairing(RelicPairingId.THERMAL_PARADOX, RelicId.FURNACE_COVENANT, RelicId.ABSOLUTE_ZERO, RelicPairingKind.DISCORD,
            "Thermal Paradox", "The furnace's heat bonus is halved, but every converted Thermal stack vents 3 heat."),
        pairing(RelicPairingId.EXTINGUISHED, RelicId.CINDER_CROWN, RelicId.ABSOLUTE_ZERO, RelicPairingKind.DISCORD,
            "Extinguished", "The crown banks Cryo instead, and every Freeze releases the bank at 60 %."),
        pairing(RelicPairingId.SHATTERED_AEGIS, RelicId.GLASS_COMET, RelicId.AEGIS_INVERSION, RelicPairingKind.DISCORD,
            "Shattered Aegis", "Glass ignores shields: inverted healing becomes 1 % damage per 3 points instead, lost when hit."),
        pairing(RelicPairingId.CONTRADICTION, RelicId.PERPETUAL_VECTOR, RelicId.STASIS_ANCHOR, RelicPairingKind.DISCORD,
            "Contradiction", "Stasis only holds while you move slower than 400 u/s."),
        pairing(RelicPairingId.HEAVY_BLINK, RelicId.BLINK_DRIVE, RelicId.NEUTRON_HEART, RelicPairingKind.DISCORD,
            "Heavy Blink", "Blinks travel 35 % less far, but their strikes use twice your mass."),
        pairing(RelicPairingId.FRAGILE_REBIRTH, RelicId.GLASS_COMET, RelicId.OUROBOROS_SEAL, RelicPairingKind.DISCORD,
            "Fragile Rebirth", "The seal restores only half your integrity, but its nova deals double damage."),
    ),
)

private fun pairing(
    id: RelicPairingId,
    first: RelicId,
    second: RelicId,
    kind: RelicPairingKind,
    name: String,
    description: String,
) = RelicPairingDefinition(id, first, second, kind, name, description)

/** Every element, in ordinal order, as an immutable list (allocation-free for hot paths). */
val AllStatusElements: ImmutableList<StatusElement> = StatusElement.entries.toImmutableList()
