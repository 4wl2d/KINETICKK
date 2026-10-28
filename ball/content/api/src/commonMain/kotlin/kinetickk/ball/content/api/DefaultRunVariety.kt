// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/** The shipped omens in [OmenId] order. */
fun defaultOmenDefinitions(): ImmutableList<OmenDefinition> = listOf(
    OmenDefinition(
        OmenId.GLASS_SUN, "Glass Sun", "+25% impact damage and +20% weapon power.", "−30 maximum integrity.",
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.IMPACT_DAMAGE, 0.25f), RunModifierEffect.Stat(ItemEffect.WEAPON_POWER, 0.20f)),
        costs = immutableListOf(RunModifierEffect.Stat(ItemEffect.MAX_INTEGRITY, -30f)),
    ),
    OmenDefinition(
        OmenId.IRON_TIDE, "Iron Tide", "+30% Matter gain and one more reroll.", "Enemies carry 12% more integrity.",
        gains = immutableListOf(RunModifierEffect.MatterGainScale(1.30f), RunModifierEffect.Rerolls(1)),
        costs = immutableListOf(RunModifierEffect.HostileScale(health = 1.12f)),
    ),
    OmenDefinition(
        OmenId.HUNGRY_DARK, "Hungry Dark", "+20% Data gain.", RebirthMutator.BLACKOUT.description,
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.DATA_GAIN, 0.20f)),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.BLACKOUT)),
    ),
    OmenDefinition(
        OmenId.CROWDED_SKY, "Crowded Sky", "+0.5 luck and one more reroll.", RebirthMutator.PACK_HUNTERS.description,
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.LUCK, 0.5f), RunModifierEffect.Rerolls(1)),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.PACK_HUNTERS)),
    ),
    OmenDefinition(
        OmenId.QUICKENED_HOST, "Quickened Host", "+20% Dash power and +25% cooling.",
        "Enemies move 12% faster and arrive 10% more often.",
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.DASH_POWER, 0.20f), RunModifierEffect.Stat(ItemEffect.COOLING, 0.25f)),
        costs = immutableListOf(RunModifierEffect.HostileScale(speed = 1.12f, spawnRate = 1.10f)),
    ),
    OmenDefinition(
        OmenId.RICH_VEINS, "Rich Veins", "+50% Matter gain.", "Enemy attacks deal 15% more damage.",
        gains = immutableListOf(RunModifierEffect.MatterGainScale(1.50f)),
        costs = immutableListOf(RunModifierEffect.HostileScale(incomingDamage = 1.15f)),
    ),
    OmenDefinition(
        OmenId.AMPLIFIED_KEYNOTE, "Amplified Keynote", "The run's keynote tag and element fill the deck.",
        "One reroll fewer; Elites arrive 15% more often.",
        gains = immutableListOf(RunModifierEffect.KeynoteFloor(0.95f)),
        costs = immutableListOf(RunModifierEffect.Rerolls(-1), RunModifierEffect.HostileScale(eliteRate = 1.15f)),
    ),
    OmenDefinition(
        OmenId.VOLATILE_PACT, "Volatile Pact", "+8% critical chance, +40% critical damage and +15 shield capacity.",
        RebirthMutator.VOLATILE_REMAINS.description,
        gains = immutableListOf(
            RunModifierEffect.Stat(ItemEffect.CRIT_CHANCE, 0.08f), RunModifierEffect.Stat(ItemEffect.CRIT_DAMAGE, 0.40f),
            RunModifierEffect.Stat(ItemEffect.SHIELD_CAPACITY, 15f),
        ),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.VOLATILE_REMAINS)),
    ),
    OmenDefinition(
        OmenId.FAULT_LINE, "Fault Line", "+20% mass and +15% impact damage.", RebirthMutator.FRACTURED_SPACE.description,
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.MASS, 0.20f), RunModifierEffect.Stat(ItemEffect.IMPACT_DAMAGE, 0.15f)),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.FRACTURED_SPACE)),
    ),
    OmenDefinition(
        OmenId.ASCETIC_PATH, "Ascetic Path", "Two more rerolls and +1 luck.", "−40% Matter gain.",
        gains = immutableListOf(RunModifierEffect.Rerolls(2), RunModifierEffect.Stat(ItemEffect.LUCK, 1f)),
        costs = immutableListOf(RunModifierEffect.MatterGainScale(0.60f)),
    ),
    OmenDefinition(
        OmenId.CROWNED_QUARRY, "Crowned Quarry", "Elites arrive 30% more often, each with a Key and a Relic.",
        RebirthMutator.HARDENED_ELITES.description,
        gains = immutableListOf(RunModifierEffect.HostileScale(eliteRate = 1.30f)),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.HARDENED_ELITES)),
    ),
    OmenDefinition(
        OmenId.STILL_WATER, "Still Water", "+5 shield capacity and +6% damage reduction.",
        "−15% impact damage and −15% weapon power.",
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.SHIELD_CAPACITY, 5f), RunModifierEffect.Stat(ItemEffect.DAMAGE_REDUCTION, 0.06f)),
        costs = immutableListOf(RunModifierEffect.Stat(ItemEffect.IMPACT_DAMAGE, -0.15f), RunModifierEffect.Stat(ItemEffect.WEAPON_POWER, -0.15f)),
    ),
).toImmutableList()

/** The shipped director personalities in [DirectorPersonalityId] order. */
fun defaultDirectorPersonalities(): ImmutableList<DirectorPersonalityDefinition> = listOf(
    DirectorPersonalityDefinition(
        DirectorPersonalityId.TIDE, "Tidewave", "Swarms and packs roll in on long surges, then the field goes quiet.",
        phaseDuration = PhaseScales(build = 0.85f, surge = 1.3f, breather = 1.5f),
        eventWeights = EventKindScales(single = 0.5f, pack = 1.4f, flank = 1.0f, swarm = 1.8f, wall = 0.7f, encircle = 1.2f),
        encircleCooldownScale = 0.85f, packSizeBonus = 1,
        typeBias = immutableListOf(
            EnemyWeightBias(EnemyType.SWARMLING, 1.8f), EnemyWeightBias(EnemyType.DRIFTER, 1.3f),
            EnemyWeightBias(EnemyType.BOMBER, 1.3f), EnemyWeightBias(EnemyType.SNIPER, 0.6f),
        ),
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.HUNTER_CELL, "Hunter Cell", "Ranged hunters lead, with walls and flanks to pin you down.",
        budgetScale = 0.97f,
        eventWeights = EventKindScales(single = 0.8f, pack = 0.9f, flank = 1.3f, swarm = 0.7f, wall = 1.5f, encircle = 1.0f),
        wallCooldownScale = 0.8f,
        typeBias = immutableListOf(
            EnemyWeightBias(EnemyType.SHOOTER, 1.6f), EnemyWeightBias(EnemyType.WEAVER, 1.6f), EnemyWeightBias(EnemyType.SNIPER, 1.6f),
            EnemyWeightBias(EnemyType.LANCER, 1.6f), EnemyWeightBias(EnemyType.MINELAYER, 1.6f),
            EnemyWeightBias(EnemyType.CHARGER, 0.75f), EnemyWeightBias(EnemyType.INTERCEPTOR, 0.75f),
        ),
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.SIEGE, "Siege", "Heavy wardens, aegises and reflectors grind forward in long builds.",
        phaseDuration = PhaseScales(build = 1.25f, surge = 0.9f),
        eventWeights = EventKindScales(single = 1.2f, pack = 1.0f, flank = 0.8f, swarm = 0.5f, wall = 1.6f, encircle = 0.6f),
        wallCooldownScale = 0.8f,
        typeBias = immutableListOf(
            EnemyWeightBias(EnemyType.WARDEN, 1.6f), EnemyWeightBias(EnemyType.AEGIS, 1.8f), EnemyWeightBias(EnemyType.REFLECTOR, 1.6f),
            EnemyWeightBias(EnemyType.BROODMOTHER, 1.6f), EnemyWeightBias(EnemyType.SPLITTER, 1.4f),
            EnemyWeightBias(EnemyType.SWARMLING, 0.5f), EnemyWeightBias(EnemyType.DRIFTER, 0.8f),
        ),
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.AMBUSHERS, "Ambushers", "Fast strikers spring ambushes and close in from the flanks.",
        eventWeights = EventKindScales(single = 0.7f, pack = 1.0f, flank = 1.6f, swarm = 1.0f, wall = 0.8f, encircle = 1.5f),
        ambushCooldownScale = 0.6f, encircleCooldownScale = 0.75f,
        typeBias = immutableListOf(
            EnemyWeightBias(EnemyType.INTERCEPTOR, 1.5f), EnemyWeightBias(EnemyType.CHARGER, 1.5f), EnemyWeightBias(EnemyType.PHANTOM, 1.6f),
            EnemyWeightBias(EnemyType.LANCER, 1.2f), EnemyWeightBias(EnemyType.WARDEN, 0.7f),
        ),
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.ELEMENTAL_STORM, "Elemental Storm", "Enemies of one aspect flood the field, and Elites share it.",
        featuresAspect = true, aspectWeight = 2.2f,
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.LONG_CALM, "Long Calm", "Long lulls broken by fierce surges.",
        budgetScale = 0.95f,
        phaseRate = PhaseScales(surge = 1.3f),
        phaseDuration = PhaseScales(calm = 1.6f, breather = 1.6f),
        eventWeights = EventKindScales(single = 1.0f, pack = 1.2f, flank = 1.0f, swarm = 1.2f, wall = 1.0f, encircle = 1.2f),
    ),
).toImmutableList()

/** Every run notice the run-variety systems emit (the uppercase forms come from the uppercase mirror). */
val RunVarietyNotices: List<String> = buildList {
    defaultDirectorPersonalities().forEach { add("HOSTILE PATTERN // ${it.name.uppercase()}") }
    add("KEYNOTE")
    add("ONSLAUGHT")
    defaultOmenDefinitions().forEach { add("OMEN // ${it.name.uppercase()}") }
    EliteAffix.entries.forEach { add(it.signalNotice) }
    WorldEventKind.entries.forEach { add(it.displayName.uppercase()) }
    add("SKIES CLEAR")
    SignalKind.entries.forEach { add("SIGNAL DETECTED // ${it.displayName.uppercase()}") }
    add("SIGNAL LOST")
    addAll(listOf("WAGER WON", "WAGER LOST", "TRADE COMPLETE", "BLESSING RECEIVED", "CURSE BOUND", "SALVAGE COMPLETE"))
}
