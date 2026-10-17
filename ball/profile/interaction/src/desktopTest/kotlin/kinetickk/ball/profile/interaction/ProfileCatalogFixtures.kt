// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import kinetickk.ball.content.api.BuildPolicy
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicValue
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponScaling
import kinetickk.ball.content.api.WeaponCadence.CONTACT
import kinetickk.ball.content.api.WeaponCadence.CONTINUOUS
import kinetickk.ball.content.api.WeaponCadence.DISCRETE
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.defaultMatrixNodes
import kinetickk.ball.content.api.defaultRebirthProfile
import kinetickk.ball.profile.interaction.rebirth.api.RebirthBuildModel
import kinetickk.ball.profile.interaction.rebirth.impl.RebirthBuildContent
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/*
 * The game's shipped copy for text-fit tests: mirrors `defaultWeapons()`
 * in ball/content/impl DefaultCatalogData.kt and DefaultRebirthData.kt, and the Rebirth-gated
 * Relics and items with a few Heirloom candidates from DefaultRelicData.kt and DefaultItemData.kt
 * (names, aspects, rarities, tags, elements and gates as shipped; the interaction module
 * cannot depend on the content implementation). Russian text comes from the content API's
 * phrase table through `localizedContent`, exactly as on screen.
 */

internal val CatalogWeapons: ImmutableList<WeaponDefinition> =
    listOf(
        catalogWeapon(
            WeaponId.FLUX_WAKE, "Flux Wake",
            "High-speed movement leaves a damaging trail that lingers behind the Core.",
            WeaponFamily.MOMENTUM, StatusElement.THERMAL, CONTINUOUS, BuildTag.DRIVE, BuildTag.OVERCLOCK,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.ORB, cost = 0,
            forms = listOf("Trail width doubles", "The trail lasts 4 s", "Ignite comes one stack sooner in the trail"),
            signature = "A trail laid while turning deals 50% more damage and lasts 50% longer.",
        ),
        catalogWeapon(
            WeaponId.MORNINGSTAR, "Morningstar",
            "A heavy orbital mass converts velocity and mass into crushing contact damage.",
            WeaponFamily.ORBITAL, null, CONTACT, BuildTag.RAM, BuildTag.ANCHOR,
            RelicKey.GRAVITY, WeaponScaling.MASS, CoreShape.PRISM, cost = 25,
            forms = listOf("Orbit radius +15%", "A second, smaller star joins the orbit", "Double knockback that slams enemies"),
            signature = "+10% damage and size for each intact facet.",
        ),
        catalogWeapon(
            WeaponId.PHASE_LATTICE, "Phase Lattice",
            "A pulsing gravity ring damages enemies held near its unstable perimeter.",
            WeaponFamily.AURA, StatusElement.CRYO, CONTINUOUS, BuildTag.BULWARK, BuildTag.ANCHOR,
            RelicKey.STILLNESS, WeaponScaling.SHIELD, CoreShape.RING, cost = 55,
            forms = listOf("A wider ring", "An outer ring joins the lattice", "Chill needs one stack less inside the ring"),
            signature = "The ring locks to the Halo radius.",
        ),
        catalogWeapon(
            WeaponId.NULL_LANCE, "Null Lance",
            "Momentum periodically projects a piercing lance along the Core's travel vector.",
            WeaponFamily.PROJECTILE, null, DISCRETE, BuildTag.DRIVE, BuildTag.PRECISION,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.SHARD, cost = 95,
            forms = listOf("Lance pierce +3", "A rear lance fires behind the Core", "Lances crit statused or marked enemies"),
            signature = "A lance hitting a marked enemy deals double Puncture and keeps its pierce.",
        ),
        catalogWeapon(
            WeaponId.GRAVITY_MINES, "Gravity Mines",
            "Braking plants implosive mines that pull enemies inward before detonating.",
            WeaponFamily.TRAP, StatusElement.GRAVITIC, DISCRETE, BuildTag.ANCHOR, BuildTag.CHAIN,
            RelicKey.STILLNESS, WeaponScaling.BRAKE, CoreShape.DIAMOND, cost = 145,
            forms = listOf("Mine radius +30%", "Up to 12 mines that detonate in a chain", "Mines collapse into a well for 1.2 s"),
            signature = "A parry drops a mine that detonates at once for double damage.",
        ),
        catalogWeapon(
            WeaponId.ION_SWARM, "Ion Swarm",
            "Autonomous ion motes orbit the Core, seek targets, and accelerate with attack speed.",
            WeaponFamily.ORBITAL, StatusElement.ION, DISCRETE, BuildTag.ARSENAL, BuildTag.CHAIN,
            RelicKey.CHARGE, WeaponScaling.ATTACK_SPEED, CoreShape.HEXAGON, cost = 215,
            forms = listOf("One more ion mote", "Mote shots pierce one more enemy", "Mote fire rate +25%"),
            signature = "Up to three drones fire with the swarm.",
        ),
        catalogWeapon(
            WeaponId.RIFT_BLADES, "Rift Blades",
            "Paired blades tear outward through enemies and return across their original paths.",
            WeaponFamily.ORBITAL, StatusElement.VOID, CONTACT, BuildTag.PRECISION, BuildTag.RAM,
            RelicKey.CRITICAL, WeaponScaling.CRIT, CoreShape.STAR, cost = 305,
            forms = listOf("One more blade", "Blade length +30%", "Critical hits launch rift slashes"),
            signature = "Blade length +2% for each Stellar Mass stack.",
        ),
        catalogWeapon(
            WeaponId.ARC_COIL, "Arc Coil",
            "Stored kinetic charge erupts as lightning that chains between nearby enemies.",
            WeaponFamily.CHAIN, StatusElement.ION, DISCRETE, BuildTag.ARSENAL, BuildTag.CHAIN,
            RelicKey.CHARGE, WeaponScaling.STATUS, CoreShape.PENTAGON, cost = 430,
            forms = listOf("One more chain target", "Chain range +100", "One more target and an Overload on the last hop"),
            signature = "On-beat activations chain to two more targets.",
        ),
        catalogWeapon(
            WeaponId.QUASAR_CANNON, "Quasar Cannon",
            "A slow-charging cannon compresses momentum into a colossal piercing projectile.",
            WeaponFamily.PROJECTILE, StatusElement.SONIC, DISCRETE, BuildTag.ARSENAL, BuildTag.OVERCLOCK,
            RelicKey.HEAT, WeaponScaling.HEAT, CoreShape.STAR, cost = 610,
            forms = listOf("Shot size +10%", "Side shots fire with every blast", "A sonic burst where the shot ends"),
            signature = "Shot size grows with Stellar Mass.",
        ),
        catalogWeapon(
            WeaponId.ENTROPY_FIELD, "Entropy Field",
            "A widening decay field slows hostile motion and deals escalating damage over time.",
            WeaponFamily.AURA, StatusElement.ENTROPY, CONTINUOUS, BuildTag.BULWARK, BuildTag.CHAIN,
            RelicKey.DECAY, WeaponScaling.STATUS, CoreShape.SPIRAL, cost = 860,
            forms = listOf("A wider field", "Executes enemies below 6% integrity", "Ruptures spread to nearby enemies"),
            signature = "The echo ghost carries a field at half strength.",
        ),
        catalogWeapon(
            WeaponId.SINGULARITY_SPEAR, "Singularity Spear",
            "Every overdrive cycle forges a boss-piercing spear from condensed kinetic matter.",
            WeaponFamily.BEAM, StatusElement.VOID, DISCRETE, BuildTag.OVERCLOCK, BuildTag.PRECISION,
            RelicKey.VOID, WeaponScaling.OVERDRIVE, CoreShape.CRESCENT, cost = 1_200,
            forms = listOf("A longer spear beam", "A cross beam fires with every spear", "The beam leaves a void scar"),
            signature = "Each Blink fires a spear along its path, once every 2 s.",
        ),
        catalogWeapon(
            WeaponId.PRISM_RELAY, "Prism Relay",
            "Launches a seeking light shard that refracts between targets; mastery adds ricochets and twin relays.",
            WeaponFamily.CHAIN, null, DISCRETE, BuildTag.PRECISION, BuildTag.FORTUNE,
            RelicKey.SPREAD, WeaponScaling.LUCK, CoreShape.TESSERACT, cost = 1_650,
            forms = listOf("One more relay bounce", "A twin relay fires alongside", "Relay bounces burst"),
            signature = "Relays bounce between lattice nodes.",
        ),
        catalogWeapon(
            WeaponId.GYRE_DISC, "Gyre Disc",
            "Throws a spinning disc along the aim that flies out and returns, cutting every enemy on both passes.",
            WeaponFamily.MOMENTUM, null, DISCRETE, BuildTag.DRIVE, BuildTag.PRECISION,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.ORB, cost = 120,
            forms = listOf("A larger disc", "Two discs fly at once", "Discs ricochet off obstacles and return for 50% more damage"),
            signature = "+25% damage while Orbit is charged.",
        ),
        catalogWeapon(
            WeaponId.SCATTER_BLOOM, "Scatter Bloom",
            "Every Dash fires a wide cone of pellets ahead of the Core; a small volley also fires on its own between Dashes.",
            WeaponFamily.PROJECTILE, null, DISCRETE, BuildTag.DRIVE, BuildTag.RAM,
            RelicKey.MOTION, WeaponScaling.DASH, CoreShape.SHARD, cost = 260,
            forms = listOf("Two more pellets", "Pellets pierce one more enemy", "Point-blank pellets keep their pierce"),
            signature = "Pellets leave Puncture marks.",
        ),
        catalogWeapon(
            WeaponId.TETHER_ARC, "Tether Arc",
            "An ion beam locks onto the nearest enemy, ramps its damage while held and jumps to a new target on a kill.",
            WeaponFamily.BEAM, StatusElement.ION, CONTINUOUS, BuildTag.ARSENAL, BuildTag.PRECISION,
            RelicKey.CHARGE, WeaponScaling.ATTACK_SPEED, CoreShape.DIPOLE, cost = 900, rebirth = 2,
            forms = listOf("Tether range +80", "A second tether at 75% strength", "The ramp reaches eight times and a kill Overloads"),
            signature = "Attract pulls the target in; Repel throws it into slams.",
        ),
        catalogWeapon(
            WeaponId.SENTRY_GRID, "Sentry Grid",
            "Deploys up to three sentries that hold their ground and fire bursting shots at enemies in range until they expire.",
            WeaponFamily.TRAP, null, DISCRETE, BuildTag.ANCHOR, BuildTag.ARSENAL,
            RelicKey.STILLNESS, WeaponScaling.ATTACK_SPEED, CoreShape.TESSERACT, cost = 380,
            forms = listOf("One more sentry", "Sentry shots pierce one more enemy", "Sentries explode when they expire"),
            signature = "Sentries deploy on lattice nodes.",
        ),
        catalogWeapon(
            WeaponId.HUNTER_SALVO, "Hunter Salvo",
            "Launches a salvo of homing missiles that burst on impact and spread Decay.",
            WeaponFamily.PROJECTILE, StatusElement.ENTROPY, DISCRETE, BuildTag.CHAIN, BuildTag.ARSENAL,
            RelicKey.SPREAD, WeaponScaling.COMBO, CoreShape.HEXAGON, cost = 1_300, rebirth = 4,
            forms = listOf("One more missile", "Missile burst +20%", "Missiles split on a kill"),
            signature = "Up to three drones each launch a missile.",
        ),
        catalogWeapon(
            WeaponId.CONCUSSION_ENGINE, "Concussion Engine",
            "Every hard impact releases a shockwave whose radius and damage grow with the Core's speed.",
            WeaponFamily.PULSE, StatusElement.SONIC, DISCRETE, BuildTag.RAM, BuildTag.BULWARK,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.SPIRAL, cost = 520,
            forms = listOf("A wider shockwave", "Shockwave knockback +50%", "+4 shield for each enemy the wave hits"),
            signature = "Ghost contacts also release shockwaves.",
        ),
        catalogWeapon(
            WeaponId.GLACIER_BELL, "Glacier Bell",
            "Releasing a held brake tolls a freezing nova around the Core; a smaller pulse rings out on its own otherwise.",
            WeaponFamily.PULSE, StatusElement.CRYO, DISCRETE, BuildTag.ANCHOR, BuildTag.BULWARK,
            RelicKey.COLD, WeaponScaling.BRAKE, CoreShape.DIAMOND, cost = 700, rebirth = 1,
            forms = listOf("A wider nova", "Enemies freeze at half Chill", "A second toll after 0.5 s"),
            signature = "A parry tolls the bell at once.",
        ),
        catalogWeapon(
            WeaponId.EVENT_LENS, "Event Lens",
            "Fires a lens toward the aim that collapses into a gravity well, pulling enemies in and crushing them.",
            WeaponFamily.TRAP, StatusElement.GRAVITIC, DISCRETE, BuildTag.ANCHOR, BuildTag.CHAIN,
            RelicKey.GRAVITY, WeaponScaling.MASS, CoreShape.DIPOLE, cost = 1_100, rebirth = 3,
            forms = listOf("A wider well", "Two lenses fly at once", "The well implodes as it closes"),
            signature = "Attract doubles the pull; Repel turns the implosion into a throw.",
        ),
        catalogWeapon(
            WeaponId.SOLAR_SWEEP, "Solar Sweep",
            "Two opposite solar beams rotate around the Core, burning everything they sweep across.",
            WeaponFamily.BEAM, StatusElement.THERMAL, CONTINUOUS, BuildTag.OVERCLOCK, BuildTag.PRECISION,
            RelicKey.HEAT, WeaponScaling.HEAT, CoreShape.RING, cost = 1_500, rebirth = 5,
            forms = listOf("Beam length +120", "Three beams sweep at once", "Beams leave burning arcs"),
            signature = "The beams start from the Halo radius.",
        ),
        catalogWeapon(
            WeaponId.AEGIS_SHARDS, "Aegis Shards",
            "Raises shield capacity and launches orbiting shards that pierce through nearby enemies; a broken shield fires them all at once.",
            WeaponFamily.ORBITAL, null, DISCRETE, BuildTag.BULWARK, BuildTag.ARSENAL,
            RelicKey.SHIELD, WeaponScaling.SHIELD, CoreShape.PRISM, cost = 1_800, rebirth = 6,
            forms = listOf("One more shard", "Shards pierce one more enemy", "A broken shield restores 25% shield, once every 60 s"),
            signature = "Each intact facet adds a shard, up to two.",
        ),
        catalogWeapon(
            WeaponId.HOLLOW_RIFT, "Hollow Rift",
            "Each Dash tears a rift along its path that detonates moments later; fast flight leaves smaller rifts behind.",
            WeaponFamily.TRAP, StatusElement.VOID, DISCRETE, BuildTag.DRIVE, BuildTag.CHAIN,
            RelicKey.VOID, WeaponScaling.DASH, CoreShape.CRESCENT, cost = 2_100, rebirth = 7,
            forms = listOf("Wider rifts", "Rifts detonate twice", "Rifts pull enemies in before they burst"),
            signature = "Blink scars become full rifts.",
        ),
        catalogWeapon(
            WeaponId.RESONANCE_DRUM, "Resonance Drum",
            "Beats a radial pulse around the Core that hurls enemies outward and leaves them Resonant.",
            WeaponFamily.PULSE, StatusElement.SONIC, DISCRETE, BuildTag.ANCHOR, BuildTag.RAM,
            RelicKey.ECHO, WeaponScaling.MASS, CoreShape.PENTAGON, cost = 2_600, rebirth = 9,
            forms = listOf("A wider drum pulse", "Every beat strikes twice", "Pulses Shatter Resonant enemies"),
            signature = "Pulses that land on the beat deal 50% more damage.",
        ),
    ).toImmutableList()

private fun catalogWeapon(
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


/** The shipped Core Matrix: its data lives in the content API, so the fixture is the real catalog. */
internal val CatalogMatrixNodes: ImmutableList<MatrixNodeDefinition> = defaultMatrixNodes()

internal fun catalogRebirthProfile(tier: Int): RebirthProfile = defaultRebirthProfile(tier)

private fun catalogRelic(
    id: RelicId,
    name: String,
    aspect: RelicAspect,
    key: RelicKey,
    gate: Int = 0,
    upside: String = "$name upside",
    values: List<RelicValue> = emptyList(),
) = RelicDefinition(
    id = id,
    name = name,
    aspect = aspect,
    keys = immutableListOf(key),
    attunedTags = immutableListOf(),
    upside = upside,
    liability = "$name liability",
    values = values.toImmutableList(),
    minimumRebirth = gate,
)

/**
 * The three Rebirth-gated Sovereign relics (with their shipped rule rewrites, which the build table
 * shows) and four Heirloom candidates (long names in both languages).
 */
internal val CatalogRebirthRelics: ImmutableList<RelicDefinition> = listOf(
    catalogRelic(RelicId.EXECUTIONERS_PRISM, "Executioner's Prism", RelicAspect.KINETIC, RelicKey.CRITICAL),
    catalogRelic(RelicId.FURNACE_COVENANT, "Furnace Covenant", RelicAspect.THERMAL, RelicKey.HEAT),
    catalogRelic(RelicId.ABSOLUTE_ZERO, "Absolute Zero", RelicAspect.CRYO, RelicKey.COLD),
    catalogRelic(RelicId.STORMHEART_CAPACITOR, "Stormheart Capacitor", RelicAspect.ION, RelicKey.CHARGE),
    catalogRelic(RelicId.AGONY_SCEPTER, "Agony Scepter", RelicAspect.SOVEREIGN, RelicKey.ECHO, gate = 1,
        upside = "Your weapon takes its Forbidden form. Every rank deepens the mutation."),
    catalogRelic(RelicId.HOLLOW_CROWN, "Hollow Crown", RelicAspect.SOVEREIGN, RelicKey.VOID, gate = 3,
        upside = "Each empty Relic slot grants {0} % damage and 8 % activation speed.", values = listOf(RelicValue(35f))),
    catalogRelic(RelicId.OUROBOROS_SEAL, "Ouroboros Seal", RelicAspect.SOVEREIGN, RelicKey.LIFE, gate = 5,
        upside = "Once per run, lethal damage instead restores your integrity, releases a nova for {0} damage and " +
            "grants two seconds of invulnerability. Then the seal is spent but keeps its slot.",
        values = listOf(RelicValue(120f, 60f))),
).toImmutableList()

private fun catalogItem(
    id: Int,
    name: String,
    description: String,
    tags: List<BuildTag>,
    elements: List<StatusElement>,
    rule: ItemRule,
    gate: Int,
) = ItemDefinition(
    id = id,
    name = name,
    description = description,
    rarity = ItemRarity.LEGENDARY,
    tags = tags.toImmutableList(),
    elements = elements.toImmutableList(),
    rules = immutableListOf(rule),
    maxStacks = 1,
    unlockLevel = 16,
    minimumRebirth = gate,
)

/** The three Rebirth-gated legendaries with their shipped descriptions. */
internal val CatalogRebirthItems: ImmutableList<ItemDefinition> = listOf(
    catalogItem(0, "Solar Choir", "When one fire sings, the others answer.", listOf(BuildTag.CHAIN), listOf(StatusElement.THERMAL),
        ItemRule.BreakSpread(StatusElement.THERMAL, 3, 140f), gate = 2),
    catalogItem(1, "Glacial Heart", "Collide with the frozen and the whole field cracks.", listOf(BuildTag.RAM),
        listOf(StatusElement.CRYO), ItemRule.BreakPower(StatusElement.CRYO, 1f), gate = 2),
    catalogItem(2, "Resonance Fork", "A reaction that rings on after it ends.", emptyList(), emptyList(),
        ItemRule.ReactionResidue(2), gate = 4),
).toImmutableList()

/** Heirlooms of a victory with four bound relics, in slot order. */
internal val CatalogHeirlooms: List<RelicId> =
    listOf(RelicId.STORMHEART_CAPACITOR, RelicId.EXECUTIONERS_PRISM, RelicId.FURNACE_COVENANT, RelicId.ABSOLUTE_ZERO)

/** The ladder's catalog content: the gated Relics and items plus the shipped weapons, characters, Matrix and roster. */
internal val CatalogRebirthContent: RebirthBuildContent = RebirthBuildContent(CatalogRebirthRelics, CatalogRebirthItems, BuildPolicy(),
    CatalogWeapons, kinetickk.ball.content.api.defaultCoreShapeDefinitions(), CatalogMatrixNodes,
    kinetickk.ball.content.api.defaultEnemyDefinitions())

/** The screen's build model between the selected [current] and the focused [target] tier with the shipped Reborn odds. */
internal fun catalogRebirthBuild(current: Int, target: Int, heirlooms: List<RelicId> = CatalogHeirlooms): RebirthBuildModel =
    CatalogRebirthContent.model(current, target, heirlooms)

/** The ladder selected at [current] (after clearing [highestCleared]) and focused on [focus], with catalog content. */
internal fun catalogRebirthModel(
    current: Int,
    focus: Int,
    highestCleared: Int = current,
    eligible: Boolean = true,
    heirlooms: List<RelicId> = CatalogHeirlooms,
): kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel = kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel(
    current = catalogRebirthProfile(current),
    focus = catalogRebirthProfile(focus),
    highestCleared = highestCleared,
    eligible = eligible,
    matter = 1_924L,
    minimumTier = 0,
    maximumTier = 10,
    build = catalogRebirthBuild(current, focus, heirlooms),
)
