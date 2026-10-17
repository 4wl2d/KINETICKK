// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.*
import kinetickk.ball.profile.api.*
import kinetickk.foundation.collections.*
import kinetickk.flow.session.interaction.TEST_ITEM_COUNT
import kinetickk.flow.session.interaction.TestCoreShapes
import kinetickk.flow.session.interaction.TestRebirthPolicy
import kinetickk.flow.session.interaction.testItems

internal fun codexTestCatalog(count: Int = TEST_ITEM_COUNT): UiCatalogSnapshot = UiCatalogSnapshot(
    version = ContentVersion("test-v1"), items = testItems(count),
    weapons = WeaponId.entries.map { WeaponDefinition(it, it.name, "Full weapon description for ${it.name}", listOf("test"), 10) }.toImmutableList(),
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    relics = testRelics, coreShapes = TestCoreShapes, rebirth = TestRebirthPolicy, relicPolicy = RelicPolicy(4, 3),
)

/**
 * The shipped relics' identities, aspects, keys and attunement (so pairings and key rules read as
 * in the game) with plain upside and cost text; one ranked value each.
 */
internal val testRelics: ImmutableList<RelicDefinition> = listOf(
    TestRelic(RelicId.PERPETUAL_VECTOR, "Perpetual Vector", RelicAspect.KINETIC, listOf(RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.RAM)),
    TestRelic(RelicId.BLINK_DRIVE, "Blink Drive", RelicAspect.KINETIC, listOf(RelicKey.MOTION, RelicKey.CRITICAL), listOf(BuildTag.DRIVE, BuildTag.PRECISION)),
    TestRelic(RelicId.GYRE_ENGINE, "Gyre Engine", RelicAspect.KINETIC, listOf(RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.CHAIN)),
    TestRelic(RelicId.GLASS_COMET, "Glass Comet", RelicAspect.KINETIC, listOf(RelicKey.FRAGILE, RelicKey.CRITICAL), listOf(BuildTag.PRECISION, BuildTag.RAM)),
    TestRelic(RelicId.EXECUTIONERS_PRISM, "Executioner's Prism", RelicAspect.KINETIC, listOf(RelicKey.CRITICAL), listOf(BuildTag.PRECISION)),
    TestRelic(RelicId.ECHO_CHAMBER, "Echo Chamber", RelicAspect.KINETIC, listOf(RelicKey.ECHO), listOf(BuildTag.ARSENAL)),
    TestRelic(RelicId.FURNACE_COVENANT, "Furnace Covenant", RelicAspect.THERMAL, listOf(RelicKey.HEAT), listOf(BuildTag.OVERCLOCK)),
    TestRelic(RelicId.CINDER_CROWN, "Cinder Crown", RelicAspect.THERMAL, listOf(RelicKey.HEAT, RelicKey.SPREAD), listOf(BuildTag.OVERCLOCK, BuildTag.CHAIN)),
    TestRelic(RelicId.OVERDRIVE_CASCADE, "Overdrive Cascade", RelicAspect.THERMAL, listOf(RelicKey.HEAT), listOf(BuildTag.OVERCLOCK, BuildTag.ARSENAL)),
    TestRelic(RelicId.ABSOLUTE_ZERO, "Absolute Zero", RelicAspect.CRYO, listOf(RelicKey.COLD, RelicKey.CRITICAL), listOf(BuildTag.RAM, BuildTag.PRECISION)),
    TestRelic(RelicId.STASIS_ANCHOR, "Stasis Anchor", RelicAspect.CRYO, listOf(RelicKey.STILLNESS, RelicKey.COLD), listOf(BuildTag.ANCHOR, BuildTag.ARSENAL)),
    TestRelic(RelicId.STORMHEART_CAPACITOR, "Stormheart Capacitor", RelicAspect.ION, listOf(RelicKey.CHARGE, RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.RAM)),
    TestRelic(RelicId.AEGIS_INVERSION, "Aegis Inversion", RelicAspect.ION, listOf(RelicKey.CHARGE, RelicKey.LIFE), listOf(BuildTag.BULWARK)),
    TestRelic(RelicId.NEUTRON_HEART, "Neutron Heart", RelicAspect.GRAVITIC, listOf(RelicKey.GRAVITY, RelicKey.STILLNESS), listOf(BuildTag.RAM, BuildTag.ANCHOR)),
    TestRelic(RelicId.EVENT_HORIZON, "Event Horizon", RelicAspect.GRAVITIC, listOf(RelicKey.GRAVITY), listOf(BuildTag.ANCHOR, BuildTag.RAM)),
    TestRelic(RelicId.VERDANT_ROT, "Verdant Rot", RelicAspect.ENTROPY, listOf(RelicKey.DECAY, RelicKey.LIFE), listOf(BuildTag.BULWARK)),
    TestRelic(RelicId.PANDEMIC_CODEX, "Pandemic Codex", RelicAspect.ENTROPY, listOf(RelicKey.DECAY, RelicKey.SPREAD), listOf(BuildTag.CHAIN)),
    TestRelic(RelicId.AGONY_SCEPTER, "Agony Scepter", RelicAspect.SOVEREIGN, listOf(RelicKey.ECHO), listOf(BuildTag.ARSENAL), minimumRebirth = 1),
    TestRelic(RelicId.HOLLOW_CROWN, "Hollow Crown", RelicAspect.SOVEREIGN, listOf(RelicKey.VOID), listOf(BuildTag.FORTUNE), minimumRebirth = 3, meldable = false),
    TestRelic(RelicId.OUROBOROS_SEAL, "Ouroboros Seal", RelicAspect.SOVEREIGN, listOf(RelicKey.LIFE, RelicKey.VOID), listOf(BuildTag.BULWARK, BuildTag.FORTUNE), minimumRebirth = 5),
).toImmutableList()

@Suppress("FunctionName")
private fun TestRelic(
    id: RelicId,
    name: String,
    aspect: RelicAspect,
    keys: List<RelicKey>,
    attuned: List<BuildTag>,
    minimumRebirth: Int = 0,
    meldable: Boolean = true,
) = RelicDefinition(id, name, aspect, keys.toImmutableList(), attuned.toImmutableList(),
    "Full relic upside for $name grants {0} % more.", "Full relic cost for $name.",
    immutableListOf(RelicValue(20f, 10f)), minimumRebirth, meldable)

internal fun codexTestProgress(discovered: Set<Int> = setOf(2, TEST_ITEM_COUNT - 1)): HomeProgressProjection = HomeProgressProjection(
    LOCAL_PROFILE_INSTANCE_ID, ProfileRevision.ZERO, PlayerEconomy(), PlayerLoadout(), PlayerCollection(discovered), RebirthProgress(), false,
)
