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
    // Values per reaction, in index order (the gameplay rules read them by index):
    // Thermal Shock: Spall seconds, throw speed. Plasma Burst: pool radius, seconds, activation bonus, Dash heat
    // factor. Fusion: pickup reach, pickup speed, implosion throw. Wildfire: spread targets, Ablaze seconds, Thermal
    // per contact. Superconduct: drag factor, seconds, heat vented. Glacier Crush: shard spacing (degrees), knock
    // speed. Embrittle: pierce speed. Magnetize: attractor seconds, pull, shot reach, shot turn (rad/s). Feedback:
    // weapon refresh seconds, gate seconds. Rot Well: damping, growth radius, growth seconds, growths. Thunderclap:
    // reach, window seconds, impulse factor. Shatterpoint: guard seconds, orbit gap, shard cap. Resonant Arc: weave
    // seconds, links, damage share. Seismic: rocks, reach, echo radius, echo share. Fracture: Cracked seconds,
    // release reach, detonation power, Decay. Silence: hush radius, seconds, silence per tick. Blackfire: leap reach,
    // leap share, leap floor, skill refund. Stasis: bubble radius, seconds, shots, skill haste. Annihilate: anchor
    // seconds, aim cone (degrees), blink reach. Null Well: shots eaten, pop threshold, pop speed. Oblivion: refund.
    reactions = immutableListOf(
        ReactionDefinition(ReactionId.THERMAL_SHOCK, "Thermal Shock",
            "Fire meets ice: a violent blast leaves the target Brittle, and your next hit sends it flying.",
            power = 10f, radius = 0f, values = immutableListOf(3f, 620f),
            verb = "Spall", cue = "Hit it into the pack", keystone = "Flash Freeze",
            keystoneDescription = "No throw; the next direct hit Freezes the target 0.8 s through the normal Freeze path; damage ×0.8."),
        ReactionDefinition(ReactionId.PLASMA_BURST, "Plasma Burst",
            "Heat ionizes into an explosion that sets everything burning and leaves a plasma pool: inside it your " +
                "weapon fires faster and Dashes cost half the heat.",
            power = 5f, radius = 110f, residue = StatusElement.THERMAL, residueStacks = 2,
            values = immutableListOf(110f, 2.5f, 0.25f, 0.5f),
            verb = "Plasma Pool", cue = "Fight in the pool", keystone = "Arc Plasma",
            keystoneDescription = "No pool; the burst repeats once on up to 2 Charged enemies within 220 at 50 % with 1 Thermal."),
        ReactionDefinition(ReactionId.FUSION, "Fusion",
            "The target becomes a small star that drags in enemies and Data, then collapses and hurls them apart.",
            power = 3f, radius = 150f, residue = StatusElement.THERMAL, residueStacks = 1,
            values = immutableListOf(300f, 500f, 500f),
            verb = "Accretion", cue = "Feed the star", keystone = "Dwarf Star",
            keystoneDescription = "Lasts 3.2 s, pull 440, damage per second ×0.5; no implosion."),
        ReactionDefinition(ReactionId.WILDFIRE, "Wildfire",
            "Rot catches fire and leaps to nearby enemies; for a moment everything you ram catches fire too.",
            power = 4f, radius = 160f, residue = StatusElement.THERMAL, residueStacks = 2,
            values = immutableListOf(3f, 3f, 2f),
            verb = "Ablaze", cue = "Dive and spread", keystone = "Firewalker",
            keystoneDescription = "Ablaze 6 s at any distance; an Ablaze contact on an enemy at Ignite threshold − 2 Scorch or more Ignites it; no spread."),
        ReactionDefinition(ReactionId.SUPERCONDUCT, "Superconduct",
            "Cold lightning races through three enemies and chills them; near you it strips the drag from your Core " +
                "and cools it.",
            power = 6f, radius = 320f, residue = StatusElement.CRYO, residueStacks = 2,
            values = immutableListOf(0.25f, 2.5f, 12f),
            verb = "Frictionless", cue = "Glide", keystone = "Zero Resistance",
            keystoneDescription = "6 links; a link on a Frozen enemy deals ×1.5 and consumes Brittle; no Frictionless."),
        ReactionDefinition(ReactionId.GLACIER_CRUSH, "Glacier Crush",
            "Gravity shatters the ice into a fan of shards that flies away from you and knocks enemies back.",
            power = 4f, radius = 0f, residue = StatusElement.CRYO, residueStacks = 1,
            values = immutableListOf(25f, 300f),
            verb = "Ice Fan", cue = "Aim with your approach", keystone = "Aimed Avalanche",
            keystoneDescription = "The fan follows your aim from the target; shards Freeze enemies with 4 or more Chill."),
        ReactionDefinition(ReactionId.EMBRITTLE, "Embrittle",
            "Frozen rot: the target takes far more damage, freezes sooner and cannot stop your charge.",
            power = 0.35f, radius = 0f, values = immutableListOf(500f),
            verb = "Glass Charge", cue = "Plow through", keystone = "Glass Burst",
            keystoneDescription = "A pierced Embrittled enemy shatters: 3 Cryo shards for 0.5 × the impact, Embrittle consumed; Embrittle lasts 2 s."),
        ReactionDefinition(ReactionId.MAGNETIZE, "Magnetize",
            "The target turns magnetic: it yanks nearby enemies together and keeps pulling them and your shots for " +
                "three seconds.",
            power = 4f, radius = 200f, residue = StatusElement.GRAVITIC, residueStacks = 1,
            values = immutableListOf(3f, 260f, 220f, 4f),
            verb = "Attractor", cue = "Shots home in", keystone = "Repulsor",
            keystoneDescription = "No yank, no attractor; the target is hurled away from the Core at 900 × knockback and each enemy it slams takes 1 Charge."),
        ReactionDefinition(ReactionId.FEEDBACK, "Feedback",
            "Current tears through decayed armor, arcs onward spreading Entropy and kicks your weapon into its next shot.",
            power = 0.05f, radius = 260f, residue = StatusElement.ENTROPY, residueStacks = 2,
            values = immutableListOf(0.35f, 1f),
            verb = "Overcurrent", cue = "Faster weapon", keystone = "Short Circuit",
            keystoneDescription = "Refreshes skills instead: 1.0 s to every skill, once per 1 s; no weapon refresh."),
        ReactionDefinition(ReactionId.ROT_WELL, "Rot Well",
            "A rotting well opens on the target, pulling enemies in, slowing and decaying them; every death inside " +
                "widens it.",
            power = 3f, radius = 140f, residue = StatusElement.ENTROPY, residueStacks = 1,
            values = immutableListOf(2f, 20f, 0.5f, 3f),
            verb = "Compost", cue = "Farm inside", keystone = "Plague Pit",
            keystoneDescription = "Lasts 4 s; a death inside passes all its Decay to every enemy inside; no growth."),
        ReactionDefinition(ReactionId.THUNDERCLAP, "Thunderclap",
            "Heat and sound hurl enemies away; Dash right on the clap to ride the blast for free.",
            power = 6f, radius = 150f, values = immutableListOf(260f, 0.6f, 1.6f),
            verb = "Blast Jump", cue = "Dash on the clap", keystone = "Rocket Clap",
            keystoneDescription = "No Dash needed: a clap within 220 launches the Core away at 560; no free Dash."),
        ReactionDefinition(ReactionId.SHATTERPOINT, "Shatterpoint",
            "The frozen ring of sound splits into ice shards; near you they circle the Core, striking enemies and " +
                "breaking hostile shots.",
            power = 4f, radius = 0f, residue = StatusElement.CRYO, residueStacks = 1,
            values = immutableListOf(4f, 74f, 6f),
            verb = "Shard Guard", cue = "Get close", keystone = "Crystal Choir",
            keystoneDescription = "No guard; 7 radial shards, each forces Shatter on enemies with 5 or more Resonance."),
        ReactionDefinition(ReactionId.RESONANT_ARC, "Resonant Arc",
            "Lightning rides the resonance through four enemies; near you, every sharp turn throws another arc.",
            power = 6f, radius = 220f, values = immutableListOf(3f, 3f, 0.5f),
            verb = "Arc Weave", cue = "Turn to arc", keystone = "Standing Wave",
            keystoneDescription = "The weave lasts 6 s at any distance; turn arcs have 2 links at 0.35 × the reaction's strength."),
        ReactionDefinition(ReactionId.SEISMIC, "Seismic",
            "A quake crushes nearby enemies and stuns the ordinary ones; nearby rocks echo it.",
            power = 3.5f, radius = 180f, values = immutableListOf(3f, 260f, 90f, 0.5f),
            verb = "Rock Echo", cue = "Fight by rocks", keystone = "Aftershock",
            keystoneDescription = "A second quake at the same spot 0.6 s later at 60 %; no rock echo."),
        ReactionDefinition(ReactionId.FRACTURE, "Fracture",
            "The vibration cracks rot open, spreads Decay and leaves enemies Cracked: release the Brake to make them burst.",
            power = 2.5f, radius = 120f, residue = StatusElement.ENTROPY, residueStacks = 3,
            values = immutableListOf(4f, 320f, 6f, 2f),
            verb = "Breakpoint", cue = "Release the Brake", keystone = "Crevasse",
            keystoneDescription = "No Brake detonation; hit enemies are Suppressed 4 s: no aspect immunity, resistance or weakness, and armor plates crack; never on bosses."),
        ReactionDefinition(ReactionId.SILENCE, "Silence",
            "Sound falls into the void: nearby enemies take damage and a zone of silence keeps them from attacking.",
            power = 4f, radius = 170f, values = immutableListOf(170f, 2.5f, 0.5f),
            verb = "Hush", cue = "Hold the quiet", keystone = "Dead Air",
            keystoneDescription = "The hush follows the Core (radius 140, 3 s) and spends up to 20 hostile shots entering it; no damage when it forms."),
        ReactionDefinition(ReactionId.BLACKFIRE, "Blackfire",
            "A black flame burns the target through every resistance; when a burning enemy dies, the flame leaps on and " +
                "your skills recharge.",
            power = 5f, radius = 0f, values = immutableListOf(200f, 0.6f, 8f, 1f),
            verb = "Flame Leap", cue = "Burn, then kill", keystone = "Hellfire",
            keystoneDescription = "Damage per second ×1.5; the flame never leaps; no skill refund."),
        ReactionDefinition(ReactionId.STASIS, "Stasis",
            "Time stops around the target: enemies halt, hostile shots vanish, and inside the bubble your skills " +
                "recharge faster.",
            power = 3f, radius = 140f, values = immutableListOf(140f, 2f, 24f, 1.5f),
            verb = "Time Bubble", cue = "Shelter inside", keystone = "Rewind",
            keystoneDescription = "Shots inside are returned as your shots (30 % of their damage, nearest enemy within 600, up to 12); no skill haste."),
        ReactionDefinition(ReactionId.ANNIHILATE, "Annihilate",
            "The void strikes the target and the toughest enemy nearby; your next Dash toward that enemy blinks you to it.",
            power = 8f, radius = 300f, values = immutableListOf(3f, 30f, 520f),
            verb = "Void Anchor", cue = "Dash to the doomed", keystone = "Event Strike",
            keystoneDescription = "Anchor blink-strikes always crit and refund their Dash heat; the anchor lasts 1.5 s."),
        ReactionDefinition(ReactionId.NULL_WELL, "Null Well",
            "A well of nothing pulls enemies in, strips a share of their integrity and swallows hostile shots.",
            power = 0.04f, radius = 150f, values = immutableListOf(20f, 5f, 400f),
            verb = "Maw", cue = "Park it in the fire", keystone = "Hungry Void",
            keystoneDescription = "The well follows the Core (radius 120, 2.4 s) and stops pulling enemies; it still eats shots."),
        ReactionDefinition(ReactionId.OBLIVION, "Oblivion",
            "Rot meets the void: a weakened enemy is erased, its Data rushes to you and your skills recharge; anything " +
                "sturdier takes a heavy burst.",
            power = 6f, radius = 0f, values = immutableListOf(1.5f),
            verb = "Erasure", cue = "Erase for Data", keystone = "Last Rites",
            keystoneDescription = "Execute lines 16 % (Elites 9 %), never bosses; no Data latch, no skill refund."),
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
