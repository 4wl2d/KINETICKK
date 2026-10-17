// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponCadence.CONTACT
import kinetickk.ball.content.api.WeaponCadence.CONTINUOUS
import kinetickk.ball.content.api.WeaponCadence.DISCRETE
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponScaling
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/**
 * The 24 weapons in [WeaponId] order. Each [CoreShape] is the signature of exactly two weapons; the
 * original twelve keep their Matter costs, and the twelve additions rise in cost with their Rebirth gate.
 */
internal fun defaultWeapons(): ImmutableList<WeaponDefinition> =
    listOf(
        weapon(
            WeaponId.FLUX_WAKE, "Flux Wake",
            "High-speed movement leaves a damaging trail that lingers behind the Core.",
            WeaponFamily.MOMENTUM, StatusElement.THERMAL, CONTINUOUS, BuildTag.DRIVE, BuildTag.OVERCLOCK,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.ORB, cost = 0,
            forms = listOf("Trail width +40%", "The trail lasts 2.4 s", "Ignite comes one stack sooner in the trail"),
            signature = "A trail laid while turning deals 50% more damage and lasts 50% longer.",
        ),
        weapon(
            WeaponId.MORNINGSTAR, "Morningstar",
            "A heavy orbital mass converts velocity and mass into crushing contact damage.",
            WeaponFamily.ORBITAL, null, CONTACT, BuildTag.RAM, BuildTag.ANCHOR,
            RelicKey.GRAVITY, WeaponScaling.MASS, CoreShape.PRISM, cost = 25,
            forms = listOf("Orbit radius +15%", "A second, smaller star joins the orbit", "Double knockback that slams enemies"),
            signature = "+10% damage and size for each intact facet.",
        ),
        weapon(
            WeaponId.PHASE_LATTICE, "Phase Lattice",
            "A pulsing gravity ring damages enemies held near its unstable perimeter.",
            WeaponFamily.AURA, StatusElement.CRYO, CONTINUOUS, BuildTag.BULWARK, BuildTag.ANCHOR,
            RelicKey.STILLNESS, WeaponScaling.SHIELD, CoreShape.RING, cost = 55,
            forms = listOf("A wider ring", "An outer ring joins the lattice", "Chill needs one stack less inside the ring"),
            signature = "The ring locks to the Halo radius.",
        ),
        weapon(
            WeaponId.NULL_LANCE, "Null Lance",
            "Momentum periodically projects a piercing lance along the Core's travel vector.",
            WeaponFamily.PROJECTILE, null, DISCRETE, BuildTag.DRIVE, BuildTag.PRECISION,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.SHARD, cost = 95,
            forms = listOf("Lance pierce +3", "A rear lance fires behind the Core", "Lances crit statused or marked enemies"),
            signature = "A lance hitting a marked enemy deals double Puncture and keeps its pierce.",
        ),
        weapon(
            WeaponId.GRAVITY_MINES, "Gravity Mines",
            "Braking plants implosive mines that pull enemies inward before detonating.",
            WeaponFamily.TRAP, StatusElement.GRAVITIC, DISCRETE, BuildTag.ANCHOR, BuildTag.CHAIN,
            RelicKey.STILLNESS, WeaponScaling.BRAKE, CoreShape.DIAMOND, cost = 145,
            forms = listOf("Mine radius +20%", "Up to 12 mines that detonate in a chain", "Mines collapse into a well for 1.2 s"),
            signature = "A parry drops a mine that detonates at once for double damage.",
        ),
        weapon(
            WeaponId.ION_SWARM, "Ion Swarm",
            "Autonomous ion motes orbit the Core, seek targets, and accelerate with attack speed.",
            WeaponFamily.ORBITAL, StatusElement.ION, DISCRETE, BuildTag.ARSENAL, BuildTag.CHAIN,
            RelicKey.CHARGE, WeaponScaling.ATTACK_SPEED, CoreShape.HEXAGON, cost = 215,
            forms = listOf("One more ion mote", "Mote shots pierce one enemy", "Mote fire rate +30%"),
            signature = "Up to three drones fire with the swarm.",
        ),
        weapon(
            WeaponId.RIFT_BLADES, "Rift Blades",
            "Paired blades tear outward through enemies and return across their original paths.",
            WeaponFamily.ORBITAL, StatusElement.VOID, CONTACT, BuildTag.PRECISION, BuildTag.RAM,
            RelicKey.CRITICAL, WeaponScaling.CRIT, CoreShape.STAR, cost = 305,
            forms = listOf("One more blade", "Blade length +30%", "Critical hits launch rift slashes"),
            signature = "Blade length +2% for each Stellar Mass stack.",
        ),
        weapon(
            WeaponId.ARC_COIL, "Arc Coil",
            "Stored kinetic charge erupts as lightning that chains between nearby enemies.",
            WeaponFamily.CHAIN, StatusElement.ION, DISCRETE, BuildTag.ARSENAL, BuildTag.CHAIN,
            RelicKey.CHARGE, WeaponScaling.STATUS, CoreShape.PENTAGON, cost = 430,
            forms = listOf("One more chain target", "Chain range +100", "One more target and an Overload on the last hop"),
            signature = "On-beat activations chain to two more targets.",
        ),
        weapon(
            WeaponId.QUASAR_CANNON, "Quasar Cannon",
            "A slow-charging cannon compresses momentum into a colossal piercing projectile.",
            WeaponFamily.PROJECTILE, StatusElement.SONIC, DISCRETE, BuildTag.ARSENAL, BuildTag.OVERCLOCK,
            RelicKey.HEAT, WeaponScaling.HEAT, CoreShape.STAR, cost = 610,
            forms = listOf("Shot size +15%", "Side shots fire with every blast", "A sonic burst where the shot ends"),
            signature = "Shot size grows with Stellar Mass.",
        ),
        weapon(
            WeaponId.ENTROPY_FIELD, "Entropy Field",
            "A widening decay field slows hostile motion and deals escalating damage over time.",
            WeaponFamily.AURA, StatusElement.ENTROPY, CONTINUOUS, BuildTag.BULWARK, BuildTag.CHAIN,
            RelicKey.DECAY, WeaponScaling.STATUS, CoreShape.SPIRAL, cost = 860,
            forms = listOf("A wider field", "Executes enemies below 6% integrity", "Ruptures spread to nearby enemies"),
            signature = "The echo ghost carries a field at half strength.",
        ),
        weapon(
            WeaponId.SINGULARITY_SPEAR, "Singularity Spear",
            "Every overdrive cycle forges a boss-piercing spear from condensed kinetic matter.",
            WeaponFamily.BEAM, StatusElement.VOID, DISCRETE, BuildTag.OVERCLOCK, BuildTag.PRECISION,
            RelicKey.VOID, WeaponScaling.OVERDRIVE, CoreShape.CRESCENT, cost = 1_200,
            forms = listOf("A longer spear beam", "A cross beam fires with every spear", "The beam leaves a void scar"),
            signature = "Each Blink fires a spear along its path, once every 2 s.",
        ),
        weapon(
            WeaponId.PRISM_RELAY, "Prism Relay",
            "Launches a seeking light shard that refracts between targets; mastery adds ricochets and twin relays.",
            WeaponFamily.CHAIN, null, DISCRETE, BuildTag.PRECISION, BuildTag.FORTUNE,
            RelicKey.SPREAD, WeaponScaling.LUCK, CoreShape.TESSERACT, cost = 1_650,
            forms = listOf("One more relay bounce", "A twin relay fires alongside", "Relay bounces burst"),
            signature = "Relays bounce between lattice nodes.",
        ),
        weapon(
            WeaponId.GYRE_DISC, "Gyre Disc",
            "Throws a spinning disc along the aim that flies out and returns, cutting every enemy on both passes.",
            WeaponFamily.MOMENTUM, null, DISCRETE, BuildTag.DRIVE, BuildTag.PRECISION,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.ORB, cost = 120,
            forms = listOf("A larger disc", "Two discs fly at once", "Discs ricochet off obstacles and return for 50% more damage"),
            signature = "+25% damage while Orbit is charged.",
        ),
        weapon(
            WeaponId.SCATTER_BLOOM, "Scatter Bloom",
            "Every Dash fires a wide cone of pellets ahead of the Core; a small volley also fires on its own between Dashes.",
            WeaponFamily.PROJECTILE, null, DISCRETE, BuildTag.DRIVE, BuildTag.RAM,
            RelicKey.MOTION, WeaponScaling.DASH, CoreShape.SHARD, cost = 260,
            forms = listOf("Two more pellets", "Pellets pierce one enemy", "Point-blank pellets deal double damage"),
            signature = "Pellets leave Puncture marks.",
        ),
        weapon(
            WeaponId.TETHER_ARC, "Tether Arc",
            "An ion beam locks onto the nearest enemy, ramps its damage while held and jumps to a new target on a kill.",
            WeaponFamily.BEAM, StatusElement.ION, CONTINUOUS, BuildTag.ARSENAL, BuildTag.PRECISION,
            RelicKey.CHARGE, WeaponScaling.ATTACK_SPEED, CoreShape.DIPOLE, cost = 900, rebirth = 2,
            forms = listOf("Tether range +80", "A second tether at half strength", "The ramp reaches four times and a kill Overloads"),
            signature = "Attract pulls the target in; Repel throws it into slams.",
        ),
        weapon(
            WeaponId.SENTRY_GRID, "Sentry Grid",
            "Deploys up to three sentries that hold their ground and fire at enemies in range until they expire.",
            WeaponFamily.TRAP, null, DISCRETE, BuildTag.ANCHOR, BuildTag.ARSENAL,
            RelicKey.STILLNESS, WeaponScaling.ATTACK_SPEED, CoreShape.TESSERACT, cost = 380,
            forms = listOf("One more sentry", "Sentry shots pierce", "Sentries explode when they expire"),
            signature = "Sentries deploy on lattice nodes.",
        ),
        weapon(
            WeaponId.HUNTER_SALVO, "Hunter Salvo",
            "Launches a salvo of homing missiles that burst on impact and spread Decay.",
            WeaponFamily.PROJECTILE, StatusElement.ENTROPY, DISCRETE, BuildTag.CHAIN, BuildTag.ARSENAL,
            RelicKey.SPREAD, WeaponScaling.COMBO, CoreShape.HEXAGON, cost = 1_300, rebirth = 4,
            forms = listOf("One more missile", "Missile burst +40%", "Missiles split on a kill"),
            signature = "Up to three drones each launch a missile.",
        ),
        weapon(
            WeaponId.CONCUSSION_ENGINE, "Concussion Engine",
            "Every hard impact releases a shockwave whose radius and damage grow with the Core's speed.",
            WeaponFamily.PULSE, StatusElement.SONIC, DISCRETE, BuildTag.RAM, BuildTag.BULWARK,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.SPIRAL, cost = 520,
            forms = listOf("A wider shockwave", "Shockwave knockback +50%", "+4 shield for each enemy the wave hits"),
            signature = "Ghost contacts also release shockwaves.",
        ),
        weapon(
            WeaponId.GLACIER_BELL, "Glacier Bell",
            "Releasing a held brake tolls a freezing nova around the Core; a smaller pulse rings out on its own otherwise.",
            WeaponFamily.PULSE, StatusElement.CRYO, DISCRETE, BuildTag.ANCHOR, BuildTag.BULWARK,
            RelicKey.COLD, WeaponScaling.BRAKE, CoreShape.DIAMOND, cost = 700, rebirth = 1,
            forms = listOf("A wider nova", "Enemies freeze at half Chill", "A second toll after 0.5 s"),
            signature = "A parry tolls the bell at once.",
        ),
        weapon(
            WeaponId.EVENT_LENS, "Event Lens",
            "Fires a lens toward the aim that collapses into a gravity well, pulling enemies in and crushing them.",
            WeaponFamily.TRAP, StatusElement.GRAVITIC, DISCRETE, BuildTag.ANCHOR, BuildTag.CHAIN,
            RelicKey.GRAVITY, WeaponScaling.MASS, CoreShape.DIPOLE, cost = 1_100, rebirth = 3,
            forms = listOf("A wider well", "Two lenses fly at once", "The well implodes for 120 damage"),
            signature = "Attract doubles the pull; Repel turns the implosion into a throw.",
        ),
        weapon(
            WeaponId.SOLAR_SWEEP, "Solar Sweep",
            "Two opposite solar beams rotate around the Core, burning everything they sweep across.",
            WeaponFamily.BEAM, StatusElement.THERMAL, CONTINUOUS, BuildTag.OVERCLOCK, BuildTag.PRECISION,
            RelicKey.HEAT, WeaponScaling.HEAT, CoreShape.RING, cost = 1_500, rebirth = 5,
            forms = listOf("Beam length +60", "Three beams sweep at once", "Beams leave burning arcs"),
            signature = "The beams start from the Halo radius.",
        ),
        weapon(
            WeaponId.AEGIS_SHARDS, "Aegis Shards",
            "Raises shield capacity and launches orbiting shards at nearby enemies; a broken shield fires them all at once.",
            WeaponFamily.ORBITAL, null, DISCRETE, BuildTag.BULWARK, BuildTag.ARSENAL,
            RelicKey.SHIELD, WeaponScaling.SHIELD, CoreShape.PRISM, cost = 1_800, rebirth = 6,
            forms = listOf("One more shard", "Shards pierce", "A broken shield restores 25% shield, once every 60 s"),
            signature = "Each intact facet adds a shard, up to two.",
        ),
        weapon(
            WeaponId.HOLLOW_RIFT, "Hollow Rift",
            "Each Dash tears a rift along its path that detonates moments later; fast flight leaves smaller rifts behind.",
            WeaponFamily.TRAP, StatusElement.VOID, DISCRETE, BuildTag.DRIVE, BuildTag.CHAIN,
            RelicKey.VOID, WeaponScaling.DASH, CoreShape.CRESCENT, cost = 2_100, rebirth = 7,
            forms = listOf("Wider rifts", "Rifts detonate twice", "Rifts pull enemies in before they burst"),
            signature = "Blink scars become full rifts.",
        ),
        weapon(
            WeaponId.RESONANCE_DRUM, "Resonance Drum",
            "Beats a radial pulse around the Core that hurls enemies outward and leaves them Resonant.",
            WeaponFamily.PULSE, StatusElement.SONIC, DISCRETE, BuildTag.ANCHOR, BuildTag.RAM,
            RelicKey.ECHO, WeaponScaling.MASS, CoreShape.PENTAGON, cost = 2_600, rebirth = 9,
            forms = listOf("A wider drum pulse", "Every beat strikes twice", "Pulses Shatter Resonant enemies"),
            signature = "Pulses that land on the beat deal 50% more damage.",
        ),
    ).toImmutableList().also(::validateWeapons)

private fun weapon(
    id: WeaponId,
    name: String,
    description: String,
    family: WeaponFamily,
    element: StatusElement?,
    cadence: WeaponCadence,
    firstTag: BuildTag,
    secondTag: BuildTag,
    key: RelicKey,
    scaling: WeaponScaling,
    signatureShape: CoreShape,
    cost: Int,
    rebirth: Int = 0,
    forms: List<String>,
    signature: String,
): WeaponDefinition = WeaponDefinition(
    id = id,
    name = name,
    description = description,
    family = family,
    element = element,
    tags = listOf(firstTag, secondTag),
    key = key,
    signature = signatureShape,
    scaling = scaling,
    cadence = cadence,
    permanentUnlockCost = cost,
    minimumRebirth = rebirth,
    masteryForms = forms,
    signatureText = signature,
)

private fun validateWeapons(weapons: List<WeaponDefinition>) {
    check(WeaponId.entries.size == ContentBounds.MAX_WEAPONS) { "WeaponId must contain exactly 24 weapons" }
    check(weapons.size == ContentBounds.MAX_WEAPONS) { "Weapon catalog must contain exactly 24 weapons" }
    check(weapons.withIndex().all { (index, weapon) -> weapon.id.ordinal == index }) {
        "Weapon catalog order must match WeaponId order"
    }
    check(weapons.map { it.id }.toSet().size == weapons.size) { "Weapon ids must be unique" }
    check(weapons.map { it.name }.toSet().size == weapons.size) { "Weapon names must be unique" }
    check(weapons.all { it.tags.toSet().size == WeaponDefinition.WEAPON_TAG_COUNT }) {
        "Every weapon carries exactly two distinct build tags"
    }
    check(CoreShape.entries.all { shape -> weapons.count { it.signature == shape } == SIGNATURE_WEAPONS_PER_SHAPE }) {
        "Every character is the signature of exactly two weapons"
    }
}

private const val SIGNATURE_WEAPONS_PER_SHAPE = 2
