// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/** A Blessing is a clean upside (Zenith); a Pact pairs a big upside with a lasting price (Nadir). */
enum class RunBoonKind { BLESSING, PACT }

/**
 * One dimension reward card. [gains] then [costs] fold through the run-modifier vocabulary for the rest of the run.
 * Ids are stable: Blessings take 32..47 and Pacts 48..63.
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
) {
    init {
        require(name.isNotBlank() && description.isNotBlank()) { "Boon texts must not be blank" }
        when (kind) {
            RunBoonKind.BLESSING -> require(id in BLESSING_IDS) { "Blessing ids are 32..47" }
            RunBoonKind.PACT -> require(id in PACT_IDS) { "Pact ids are 48..63" }
        }
        require(gains.size in 1..ContentBounds.MAX_RUN_MODIFIER_EFFECTS) {
            "A boon grants 1..${ContentBounds.MAX_RUN_MODIFIER_EFFECTS} effects"
        }
        when (kind) {
            RunBoonKind.BLESSING -> require(costs.isEmpty()) { "A Blessing costs nothing" }
            RunBoonKind.PACT -> require(costs.size in 1..ContentBounds.MAX_RUN_MODIFIER_EFFECTS) {
                "A Pact costs 1..${ContentBounds.MAX_RUN_MODIFIER_EFFECTS} effects"
            }
        }
    }

    companion object {
        val BLESSING_IDS: IntRange = 32..47
        val PACT_IDS: IntRange = 48..63
    }
}

private fun stat(effect: ItemEffect, amount: Float): RunModifierEffect = RunModifierEffect.Stat(effect, amount)

/** The shipped Blessings (32..41) and Pacts (48..57). */
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
).toImmutableList()
