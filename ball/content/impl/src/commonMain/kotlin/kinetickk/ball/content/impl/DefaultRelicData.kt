// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildCatalog
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPairingKind
import kinetickk.ball.content.api.RelicValue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

private fun relic(
    id: RelicId,
    name: String,
    aspect: RelicAspect,
    keys: List<RelicKey>,
    attuned: List<BuildTag>,
    upside: String,
    liability: String,
    vararg values: RelicValue,
    minimumRebirth: Int = 0,
    meldable: Boolean = true,
) = RelicDefinition(
    id = id,
    name = name,
    aspect = aspect,
    keys = keys.toImmutableList(),
    attunedTags = attuned.toImmutableList(),
    upside = upside,
    liability = liability,
    values = values.toList().toImmutableList(),
    minimumRebirth = minimumRebirth,
    meldable = meldable,
)

internal fun defaultRelics(): ImmutableList<RelicDefinition> = listOf(
    relic(
        RelicId.PERPETUAL_VECTOR, "Perpetual Vector", RelicAspect.KINETIC,
        listOf(RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.RAM),
        "Drag falls by 85 %. Every 400 u/s above 600, up to 2,200, adds {0} % damage.",
        "Brake grips half as hard, and impacts below 600 u/s deal 30 % less.",
        RelicValue(12f, 3f),
    ),
    relic(
        RelicId.BLINK_DRIVE, "Blink Drive", RelicAspect.KINETIC,
        listOf(RelicKey.MOTION, RelicKey.CRITICAL), listOf(BuildTag.DRIVE, BuildTag.PRECISION),
        "Dash becomes a Blink: you jump up to {1} u along your aim, stopping short of the singularity, " +
            "and strike every enemy on the line for {0} % impact damage. Blinks cost 35 % less heat.",
        "After a Blink you keep only 35 % of your speed.",
        RelicValue(110f, 30f), RelicValue(260f),
    ),
    relic(
        RelicId.GYRE_ENGINE, "Gyre Engine", RelicAspect.KINETIC,
        listOf(RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.CHAIN),
        "Every real turn releases a shock ring for {0} % impact damage and 2 stacks of your dominant element.",
        "Polarity no longer recovers on its own or while braking; only turns restore it, and less than before.",
        RelicValue(55f, 20f),
    ),
    relic(
        RelicId.GLASS_COMET, "Glass Comet", RelicAspect.KINETIC,
        listOf(RelicKey.FRAGILE, RelicKey.CRITICAL), listOf(BuildTag.PRECISION, BuildTag.RAM),
        "All your damage +{0} %. Impact kills above 900 u/s grant a quarter second of invulnerability, " +
            "at most once every 1.5 seconds.",
        "You take double damage, and shields and damage reduction no longer protect you.",
        RelicValue(100f, 25f),
    ),
    relic(
        RelicId.EXECUTIONERS_PRISM, "Executioner's Prism", RelicAspect.KINETIC,
        listOf(RelicKey.CRITICAL), listOf(BuildTag.PRECISION),
        "Critical hits are no longer random. Every 1 % critical chance, up to 50 %, becomes {0} % critical " +
            "damage. Hits that can crit always crit against enemies below 35 % integrity, Frozen or Brittle enemies and " +
            "enemies whose status is three quarters of the way to its Break.",
        "Hits that do not crit deal 20 % less.",
        RelicValue(3f, 0.5f),
    ),
    relic(
        RelicId.ECHO_CHAMBER, "Echo Chamber", RelicAspect.KINETIC,
        listOf(RelicKey.ECHO), listOf(BuildTag.ARSENAL),
        "Weapon hits echo 0.35 seconds later for {0} % damage and apply their on-hit effects again.",
        "Impact damage −50 %.",
        RelicValue(50f, 10f),
    ),
    relic(
        RelicId.FURNACE_COVENANT, "Furnace Covenant", RelicAspect.THERMAL,
        listOf(RelicKey.HEAT), listOf(BuildTag.OVERCLOCK),
        "Overheat never locks your Dash. Every 10 heat above 40 adds {0} % damage, and above 70 heat your " +
            "hits apply Thermal.",
        "Cooling −55 %. Above 80 heat you lose 3 integrity per second, and heat past full burns integrity instead.",
        RelicValue(8f, 2f),
    ),
    relic(
        RelicId.CINDER_CROWN, "Cinder Crown", RelicAspect.THERMAL,
        listOf(RelicKey.HEAT, RelicKey.SPREAD), listOf(BuildTag.OVERCLOCK, BuildTag.CHAIN),
        "Thermal stacks no longer burn over time. They bank {0} % of their damage and Ignite at 5 stacks, " +
            "releasing the bank and spreading 2 Thermal.",
        "Thermal fades twice as fast, and Ignites near you burn 4 % of your integrity.",
        RelicValue(160f, 30f),
    ),
    relic(
        RelicId.OVERDRIVE_CASCADE, "Overdrive Cascade", RelicAspect.THERMAL,
        listOf(RelicKey.HEAT), listOf(BuildTag.OVERCLOCK, BuildTag.ARSENAL),
        "Overdrive gain +{0} %. Each kill during Overdrive extends it, up to 2.5 extra seconds.",
        "Outside Overdrive all your damage −30 %.",
        RelicValue(60f, 20f),
    ),
    relic(
        RelicId.ABSOLUTE_ZERO, "Absolute Zero", RelicAspect.CRYO,
        listOf(RelicKey.COLD, RelicKey.CRITICAL), listOf(BuildTag.RAM, BuildTag.PRECISION),
        "Impacts apply {0} Cryo and Dash chills everything around you. Frozen enemies shatter on the next " +
            "direct hit for {1} % damage, chilling their neighbors.",
        "Half of the Thermal you apply turns to Cryo and the rest is lost. Overdrive gain −40 %.",
        RelicValue(3f), RelicValue(250f, 50f),
    ),
    relic(
        RelicId.STASIS_ANCHOR, "Stasis Anchor", RelicAspect.CRYO,
        listOf(RelicKey.STILLNESS, RelicKey.COLD), listOf(BuildTag.ANCHOR, BuildTag.ARSENAL),
        "While braking, enemies near you are slowed by {0} % and hostile bullets near you are caught, up to " +
            "24. Release Brake to fire them as your own. Stasis holds for 3 seconds and refills when you let go.",
        "Weapon activation −30 % while you are not braking.",
        RelicValue(45f, 10f),
    ),
    relic(
        RelicId.STORMHEART_CAPACITOR, "Stormheart Capacitor", RelicAspect.ION,
        listOf(RelicKey.CHARGE, RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.RAM),
        "Above 700 u/s the Core charges in 4 seconds. The next impact discharges into five enemies for " +
            "{0} % impact damage and 3 Ion each.",
        "Taking a hit while half charged or more discharges into you for 10 % of your integrity.",
        RelicValue(200f, 40f),
    ),
    relic(
        RelicId.AEGIS_INVERSION, "Aegis Inversion", RelicAspect.ION,
        listOf(RelicKey.CHARGE, RelicKey.LIFE), listOf(BuildTag.BULWARK),
        "All healing becomes shield, and shield capacity grows by 60 % of your maximum integrity. A breaking " +
            "shield releases a nova worth {0} % of the damage it absorbed.",
        "Integrity can never be restored, and shields no longer recharge on their own.",
        RelicValue(150f, 50f),
    ),
    relic(
        RelicId.NEUTRON_HEART, "Neutron Heart", RelicAspect.GRAVITIC,
        listOf(RelicKey.GRAVITY, RelicKey.STILLNESS), listOf(BuildTag.RAM, BuildTag.ANCHOR),
        "Mass +{0} %, and enemies barely move you. Slow collisions hurt you only half as much.",
        "Dash impulse −35 % and magnetism −20 %.",
        RelicValue(80f, 30f),
    ),
    relic(
        RelicId.EVENT_HORIZON, "Event Horizon", RelicAspect.GRAVITIC,
        listOf(RelicKey.GRAVITY), listOf(BuildTag.ANCHOR, BuildTag.RAM),
        "Enemies within 280 u are dragged toward you. Impacts against enemies carrying Gravitic deal {0} % more, and " +
            "your Collapses implode toward you.",
        "Contact damage taken ×1.5 and pickup radius halved.",
        RelicValue(40f, 20f),
    ),
    relic(
        RelicId.VERDANT_ROT, "Verdant Rot", RelicAspect.ENTROPY,
        listOf(RelicKey.DECAY, RelicKey.LIFE), listOf(BuildTag.BULWARK),
        "Impacts apply 2 Entropy. Every Entropy stack within 320 u heals you {0} per second, up to 3.5.",
        "All other healing is disabled, and every Entropy stack close to you makes you take 1.5 % more damage.",
        RelicValue(0.25f, 0.05f),
    ),
    relic(
        RelicId.PANDEMIC_CODEX, "Pandemic Codex", RelicAspect.ENTROPY,
        listOf(RelicKey.DECAY, RelicKey.SPREAD), listOf(BuildTag.CHAIN),
        "Statuses are contagious: when an enemy dies, {0} % of each of its statuses jumps to its two nearest " +
            "neighbors.",
        "Enemies carrying a status move 20 % faster.",
        RelicValue(60f, 10f),
    ),
    relic(
        RelicId.AGONY_SCEPTER, "Agony Scepter", RelicAspect.SOVEREIGN,
        listOf(RelicKey.ECHO), listOf(BuildTag.ARSENAL),
        "Your weapon takes its Forbidden form. Every rank deepens the mutation.",
        "Weapon activations add heat: 6 per shot, or 5 per second for continuous weapons.",
        minimumRebirth = 1,
    ),
    relic(
        RelicId.HOLLOW_CROWN, "Hollow Crown", RelicAspect.SOVEREIGN,
        listOf(RelicKey.VOID), listOf(BuildTag.FORTUNE),
        "Each empty Relic slot grants {0} % damage and 8 % activation speed.",
        "It never ranks up, every other bound Relic is 25 % weaker, and each empty slot makes you take 10 % " +
            "more damage.",
        RelicValue(35f),
        minimumRebirth = 3,
        meldable = false,
    ),
    relic(
        RelicId.OUROBOROS_SEAL, "Ouroboros Seal", RelicAspect.SOVEREIGN,
        listOf(RelicKey.LIFE, RelicKey.VOID), listOf(BuildTag.BULWARK, BuildTag.FORTUNE),
        "Once per run, lethal damage instead restores your integrity, releases a nova for {0} damage and " +
            "grants two seconds of invulnerability. Then the seal is spent but keeps its slot.",
        "Data gain −25 % while the seal is bound. The singularity still kills, and a spent seal locks its " +
            "slot for two minutes.",
        RelicValue(120f, 60f),
        minimumRebirth = 5,
    ),
    relic(
        RelicId.THUNDER_CHOIR, "Thunder Choir", RelicAspect.SONIC,
        listOf(RelicKey.ECHO, RelicKey.SHIELD), listOf(BuildTag.RAM, BuildTag.ANCHOR),
        "Shatter also chains to the {0} nearest Resonant enemies within 260 for {1} % of its damage; your knockback +20 %.",
        "You take +15 % knockback from hits.",
        RelicValue(2f, 1f), RelicValue(60f, 20f),
    ),
    relic(
        RelicId.NULL_COVENANT, "Null Covenant", RelicAspect.VOID,
        listOf(RelicKey.VOID, RelicKey.SHIELD), listOf(BuildTag.PRECISION, BuildTag.CHAIN),
        "Unmake fires immediately and remembers {0} % more; Hollow stacks never decay on Elites.",
        "Maximum integrity −10 %.",
        RelicValue(40f, 20f),
    ),
).toImmutableList().also(::validateRelics)

private fun validateRelics(relics: List<RelicDefinition>) {
    check(relics.size <= ContentBounds.MAX_RELICS) { "Relic catalog exceeds ${ContentBounds.MAX_RELICS} relics" }
    check(relics.size == RelicId.entries.size) { "Relic catalog must define every RelicId" }
    check(relics.withIndex().all { (index, relic) -> relic.id.ordinal == index }) {
        "Relic catalog order must match RelicId order"
    }
    check(relics.map { it.name }.toSet().size == relics.size) { "Relic names must be unique" }
    check(relics.map { it.upside }.toSet().size == relics.size) { "Relic upsides must be unique" }
    check(relics.all { relic ->
        val placeholders = Placeholder.findAll(relic.upside).map { it.groupValues[1].toInt() }.toSet()
        placeholders == relic.values.indices.toSet()
    }) { "Every Relic value is referenced exactly by its upside text" }
    check(relics.count(RelicDefinition::isSovereign) in 1..4) { "Sovereign relics stay rare" }
    check(relics.filter(RelicDefinition::isSovereign).all { it.minimumRebirth > 0 }) {
        "Sovereign relics are unlocked by Rebirth"
    }
}

/** Every named pairing must agree with the public keys so players can predict it. */
internal fun validatePairings(relics: List<RelicDefinition>, build: BuildCatalog) {
    val byId = relics.associateBy { it.id }
    build.pairings.forEach { pairing ->
        val first = requireNotNull(byId[pairing.first]) { "${pairing.id} joins a Relic missing from the catalog" }
        val second = requireNotNull(byId[pairing.second]) { "${pairing.id} joins a Relic missing from the catalog" }
        when (pairing.kind) {
            RelicPairingKind.CONCORD -> require(first.harmonizes(second)) {
                "${pairing.id} must join Relics that share a key and have no opposed keys"
            }
            RelicPairingKind.DISCORD -> require(first.dissonates(second)) {
                "${pairing.id} must join opposed keys"
            }
        }
    }
}

private val Placeholder = Regex("\\{([0-9]+)\\}")
