// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/** Blessings and Pacts come from dimensions; an Aspect is the unique lasting reward of a dungeon trial. */
enum class RunBoonKind { BLESSING, PACT, ASPECT }

/**
 * One lasting run reward. [gains] then [costs] fold through the run-modifier vocabulary; [buildRules] join the build.
 * Ids are stable: Blessings take 32..47, Pacts 48..63 and dungeon Aspects 64..69.
 */
data class RunBoonDefinition(
    val id: Int,
    val kind: RunBoonKind,
    /** English content key. */
    val name: String,
    /** Flavor line; mechanical text is generated from the effects. */
    val description: String,
    val gains: ImmutableList<RunModifierEffect>,
    val costs: ImmutableList<RunModifierEffect> = immutableListOf(),
    /** Dungeon aspects use the same bounded elemental rules as items, without occupying an item slot. */
    val buildRules: ImmutableList<ItemRule> = immutableListOf(),
) {
    init {
        require(name.isNotBlank() && description.isNotBlank()) { "Boon texts must not be blank" }
        when (kind) {
            RunBoonKind.BLESSING -> require(id in BLESSING_IDS) { "Blessing ids are 32..47" }
            RunBoonKind.PACT -> require(id in PACT_IDS) { "Pact ids are 48..63" }
            RunBoonKind.ASPECT -> require(id in ASPECT_IDS) { "Dungeon aspect ids are 64..69" }
        }
        require(gains.size in 1..ContentBounds.MAX_RUN_MODIFIER_EFFECTS) {
            "A boon grants 1..${ContentBounds.MAX_RUN_MODIFIER_EFFECTS} effects"
        }
        when (kind) {
            RunBoonKind.BLESSING, RunBoonKind.ASPECT -> require(costs.isEmpty()) { "A Blessing or dungeon aspect costs nothing" }
            RunBoonKind.PACT -> require(costs.size in 1..ContentBounds.MAX_RUN_MODIFIER_EFFECTS) {
                "A Pact costs 1..${ContentBounds.MAX_RUN_MODIFIER_EFFECTS} effects"
            }
        }
        require(gains.all { it is RunModifierEffect.Stat && it.amount > 0f }) { "A boon gains only stat increases" }
        require(costs.all(::isBoonPrice)) { "A Pact's price is a stat decrease or a tougher, faster or harder-hitting enemy" }
        require(if (kind == RunBoonKind.ASPECT) buildRules.size in 1..3 else buildRules.isEmpty()) {
            "Only dungeon aspects carry one to three build rules"
        }
        require(buildRules.all { it is ItemRule.BreakSpread || it is ItemRule.StatusDuration ||
            it is ItemRule.BreakThreshold || it is ItemRule.SkillCooldown || it is ItemRule.CoreLinkRadius ||
            it is ItemRule.ReactionTuning || it is ItemRule.FieldTuning || it is ItemRule.ReactionKeystone }) {
            "Dungeon aspects use supported elemental build rules"
        }
    }

    companion object {
        val BLESSING_IDS: IntRange = 32..47
        val PACT_IDS: IntRange = 48..63
        val ASPECT_IDS: IntRange = 64..69
    }
}

/**
 * A lasting price taken mid-run: a stat decrease, or hostile integrity, speed or incoming damage raised (never
 * lowered) without touching the spawn or Elite rates. Rerolls, Matter scales, mutators and Keynote floors stay with
 * omens and signals: a mutator such as Fractured Space rebuilds the obstacle field, which a dimension reward must not do.
 */
private fun isBoonPrice(effect: RunModifierEffect): Boolean = when (effect) {
    is RunModifierEffect.Stat -> effect.amount < 0f
    is RunModifierEffect.HostileScale -> effect.spawnRate == 1f && effect.eliteRate == 1f &&
        effect.health >= 1f && effect.speed >= 1f && effect.incomingDamage >= 1f &&
        maxOf(effect.health, effect.speed, effect.incomingDamage) > 1f
    else -> false
}

private fun stat(effect: ItemEffect, amount: Float): RunModifierEffect = RunModifierEffect.Stat(effect, amount)

/** The shipped Blessings (32..41), Pacts (48..57) and dungeon Aspects (64..69). */
fun defaultRunBoons(): ImmutableList<RunBoonDefinition> = listOf(
    RunBoonDefinition(
        32, RunBoonKind.BLESSING, "Halo of Mending", "Light remembers your shape.",
        immutableListOf(stat(ItemEffect.MAX_INTEGRITY, 25f), stat(ItemEffect.REGEN, 0.8f)),
    ),
    RunBoonDefinition(
        33, RunBoonKind.BLESSING, "Seraph Wings", "Borrowed feathers, honest speed.",
        immutableListOf(stat(ItemEffect.DASH_POWER, 0.25f), stat(ItemEffect.DASH_EFFICIENCY, 0.20f)),
    ),
    RunBoonDefinition(
        34, RunBoonKind.BLESSING, "Choir Resonance", "The hymn keeps your rhythm.",
        immutableListOf(stat(ItemEffect.ATTACK_SPEED, 0.20f), stat(ItemEffect.WEAPON_POWER, 0.10f)),
    ),
    RunBoonDefinition(
        35, RunBoonKind.BLESSING, "Radiant Mass", "Strike like a falling star.",
        immutableListOf(stat(ItemEffect.IMPACT_DAMAGE, 0.30f), stat(ItemEffect.MASS, 0.15f)),
    ),
    RunBoonDefinition(
        36, RunBoonKind.BLESSING, "Aureole", "A crown of light turns blows aside.",
        immutableListOf(stat(ItemEffect.SHIELD_CAPACITY, 30f), stat(ItemEffect.DAMAGE_REDUCTION, 0.06f)),
    ),
    RunBoonDefinition(
        37, RunBoonKind.BLESSING, "Grace of Motion", "Momentum, blessed.",
        immutableListOf(stat(ItemEffect.OVERDRIVE_GAIN, 0.35f), stat(ItemEffect.COOLING, 0.20f)),
    ),
    RunBoonDefinition(
        38, RunBoonKind.BLESSING, "Clear Sight", "See the weak seam before it opens.",
        immutableListOf(stat(ItemEffect.CRIT_CHANCE, 0.08f), stat(ItemEffect.CRIT_DAMAGE, 0.30f)),
    ),
    RunBoonDefinition(
        39, RunBoonKind.BLESSING, "Providence", "Fortune favours the faithful.",
        immutableListOf(stat(ItemEffect.LUCK, 1f), stat(ItemEffect.DATA_GAIN, 0.15f)),
    ),
    RunBoonDefinition(
        40, RunBoonKind.BLESSING, "Ascendant Pull", "The heavens lean toward you.",
        immutableListOf(stat(ItemEffect.MAGNETISM, 0.15f), stat(ItemEffect.PICKUP_RADIUS, 60f)),
    ),
    RunBoonDefinition(
        41, RunBoonKind.BLESSING, "Steadfast Light", "It does not go out.",
        immutableListOf(stat(ItemEffect.MAX_INTEGRITY, 30f), stat(ItemEffect.COMBO_WINDOW, 0.6f)),
    ),
    RunBoonDefinition(
        48, RunBoonKind.PACT, "Cinder Pact", "Burn brighter, burn shorter.",
        immutableListOf(stat(ItemEffect.IMPACT_DAMAGE, 0.45f), stat(ItemEffect.WEAPON_POWER, 0.20f)),
        immutableListOf(stat(ItemEffect.MAX_INTEGRITY, -20f)),
    ),
    RunBoonDefinition(
        49, RunBoonKind.PACT, "Furnace Pact", "The forge never rests; neither will you.",
        immutableListOf(stat(ItemEffect.ATTACK_SPEED, 0.35f)),
        immutableListOf(stat(ItemEffect.COOLING, -0.25f)),
    ),
    RunBoonDefinition(
        50, RunBoonKind.PACT, "Hunger Pact", "Take more. They grow fat on it too.",
        immutableListOf(stat(ItemEffect.DATA_GAIN, 0.35f), stat(ItemEffect.MATTER_GAIN, 0.30f)),
        immutableListOf(RunModifierEffect.HostileScale(health = 1.12f)),
    ),
    RunBoonDefinition(
        51, RunBoonKind.PACT, "Wrath Pact", "Every wound is a lesson in rage.",
        immutableListOf(stat(ItemEffect.CRIT_CHANCE, 0.12f), stat(ItemEffect.CRIT_DAMAGE, 0.50f)),
        immutableListOf(RunModifierEffect.HostileScale(incomingDamage = 1.15f)),
    ),
    RunBoonDefinition(
        52, RunBoonKind.PACT, "Hound Pact", "Run with the pack, leave the scraps.",
        immutableListOf(stat(ItemEffect.DASH_POWER, 0.40f), stat(ItemEffect.OVERDRIVE_GAIN, 0.40f)),
        immutableListOf(stat(ItemEffect.PICKUP_RADIUS, -60f)),
    ),
    RunBoonDefinition(
        53, RunBoonKind.PACT, "Ash Pact", "A shell of ash over a smaller heart.",
        immutableListOf(stat(ItemEffect.SHIELD_CAPACITY, 60f)),
        immutableListOf(stat(ItemEffect.MAX_INTEGRITY, -25f)),
    ),
    RunBoonDefinition(
        54, RunBoonKind.PACT, "Ember Pact", "Feed the fire; it feeds them too.",
        immutableListOf(stat(ItemEffect.WEAPON_POWER, 0.35f)),
        immutableListOf(RunModifierEffect.HostileScale(speed = 1.08f)),
    ),
    RunBoonDefinition(
        55, RunBoonKind.PACT, "Brimstone Pact", "Heavy as sin, slow to answer.",
        immutableListOf(stat(ItemEffect.MASS, 0.30f), stat(ItemEffect.IMPACT_DAMAGE, 0.30f)),
        immutableListOf(stat(ItemEffect.MAGNETISM, -0.12f)),
    ),
    RunBoonDefinition(
        56, RunBoonKind.PACT, "Greed Pact", "Luck has a price, and it is you.",
        immutableListOf(stat(ItemEffect.LUCK, 2f)),
        immutableListOf(stat(ItemEffect.MAX_INTEGRITY, -15f), RunModifierEffect.HostileScale(incomingDamage = 1.08f)),
    ),
    RunBoonDefinition(
        57, RunBoonKind.PACT, "Pyre Pact", "Burn the map to light the road.",
        immutableListOf(stat(ItemEffect.OVERDRIVE_GAIN, 0.60f), stat(ItemEffect.COOLING, 0.25f)),
        immutableListOf(stat(ItemEffect.DATA_GAIN, -0.15f)),
    ),
    RunBoonDefinition(
        64, RunBoonKind.ASPECT, dungeonAspectName(64),
        "Attack speed +10%. Ion skill cooldowns are 20% shorter. Superconduct becomes Zero Resistance, chaining through frozen enemies.",
        immutableListOf(stat(ItemEffect.ATTACK_SPEED, 0.10f)),
        buildRules = immutableListOf(ItemRule.SkillCooldown(StatusElement.ION, 0.20f), ItemRule.ReactionKeystone(ReactionId.SUPERCONDUCT)),
    ),
    RunBoonDefinition(
        65, RunBoonKind.ASPECT, dungeonAspectName(65),
        "Shield capacity +20. Chill lasts 50% longer. Freeze spreads one Chill stack to nearby enemies within 180 u.",
        immutableListOf(stat(ItemEffect.SHIELD_CAPACITY, 20f)),
        buildRules = immutableListOf(ItemRule.StatusDuration(StatusElement.CRYO, 1.5f), ItemRule.BreakSpread(StatusElement.CRYO, 1, 180f)),
    ),
    RunBoonDefinition(
        66, RunBoonKind.ASPECT, dungeonAspectName(66),
        "Weapon power +10%. Reactions feed the Core from 160 u farther. Shatterpoint pushes enemies 30% harder.",
        immutableListOf(stat(ItemEffect.WEAPON_POWER, 0.10f)),
        buildRules = immutableListOf(ItemRule.CoreLinkRadius(160f), ItemRule.ReactionTuning(ReactionId.SHATTERPOINT, ReactionAspect.FORCE, 0.30f)),
    ),
    RunBoonDefinition(
        67, RunBoonKind.ASPECT, dungeonAspectName(67),
        "Pickup reach +60 u. Rupture needs one fewer Decay stack and spreads two Decay stacks within 220 u.",
        immutableListOf(stat(ItemEffect.PICKUP_RADIUS, 60f)),
        buildRules = immutableListOf(ItemRule.BreakThreshold(StatusElement.ENTROPY, 1), ItemRule.BreakSpread(StatusElement.ENTROPY, 2, 220f)),
    ),
    RunBoonDefinition(
        68, RunBoonKind.ASPECT, dungeonAspectName(68),
        "Cooling +20%. Ignite spreads one Scorch stack within 200 u. Wildfire becomes Firewalker, igniting rams from any range.",
        immutableListOf(stat(ItemEffect.COOLING, 0.20f)),
        buildRules = immutableListOf(ItemRule.BreakSpread(StatusElement.THERMAL, 1, 200f), ItemRule.ReactionKeystone(ReactionId.WILDFIRE)),
    ),
    RunBoonDefinition(
        69, RunBoonKind.ASPECT, dungeonAspectName(69),
        "Mass +10%. Your fields last 30% longer. Fusion becomes Dwarf Star, pulling harder and burning longer.",
        immutableListOf(stat(ItemEffect.MASS, 0.10f)),
        buildRules = immutableListOf(ItemRule.FieldTuning(FieldAspect.DURATION, 0.30f), ItemRule.ReactionKeystone(ReactionId.FUSION)),
    ),
).toImmutableList()

/** Stable content names shared by the reward preview and the owned boon. */
fun dungeonAspectName(id: Int): String = when (id) {
    64 -> "Induction Coil"
    65 -> "Rime Heart"
    66 -> "Harmonic Crown"
    67 -> "Mycelial Compass"
    68 -> "Furnace Heart"
    69 -> "Singularity Seal"
    else -> error("Unknown dungeon aspect $id")
}
