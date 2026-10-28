// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.OmenDefinition
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.math.min

/*
 * Run omens: a run started on the production path offers three omens and No omen at the end of its first fixed step,
 * before the Heirloom. The offer draws from its own local stream (run seed xor [OMEN_SEED_SALT]), discarded once the
 * cards are dealt, so no existing stream moves and No omen leaves the run as it would have been. An accepted omen folds
 * its gains, then its costs, through [applyRunModifier]. Omens are never rerolled.
 */

/** The No omen card's title and text (English content keys). */
internal const val NO_OMEN_TITLE: String = "No omen"
internal const val NO_OMEN_TEXT: String = "Start the run as it is."

/**
 * True when [omen] can be offered this run: omens are on, the Rebirth rank reaches its minimum, no cost adds a mutator
 * the tier already runs, and a Keynote floor gain has a Keynote to fill.
 */
internal fun MutableGameState.omenEligible(omen: OmenDefinition): Boolean =
    content.variety.omens.enabled &&
        omen.minimumRank <= rebirthRank &&
        omen.costs.none { it is RunModifierEffect.Mutator && rebirthProfile.has(it.mutator) } &&
        (omen.gains.none { it is RunModifierEffect.KeynoteFloor } || keynoteTag != null)

/**
 * Opens the omen offer: up to the policy's offer count of eligible omens, drawn by weight without replacement from the
 * local omen stream, then the No omen card. An empty pool opens nothing and the run goes on.
 */
internal fun MutableGameState.openOmenChoice() {
    omenPending = false
    val policy = content.variety.omens
    val pool = policy.definitions.filter { omenEligible(it) }
    if (pool.isEmpty()) return
    val offered = drawOmens(pool, min(policy.offers, pool.size), CloneableXorWowRandom(runSeed xor OMEN_SEED_SALT))
    activeChoiceType = ChoiceType.OMEN
    choices = offered.map { omen ->
        ChoiceOption(type = ChoiceType.OMEN, title = omen.name, description = omen.upside, tag = "OMEN", omenId = omen.id)
    } + ChoiceOption(type = ChoiceType.OMEN, title = NO_OMEN_TITLE, description = NO_OMEN_TEXT, tag = "DECLINE")
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

/**
 * [count] omens of [pool] by weight without replacement, the draft deck's loop: each draw rolls over the untaken
 * weights in pool order. Float sums run in a fixed order, so every platform deals the same cards.
 */
private fun drawOmens(pool: List<OmenDefinition>, count: Int, random: CloneableXorWowRandom): List<OmenDefinition> {
    val taken = BooleanArray(pool.size)
    val offered = ArrayList<OmenDefinition>(count)
    repeat(count) {
        var total = 0f
        for (index in pool.indices) if (!taken[index]) total += pool[index].weight
        var roll = random.nextFloat() * total
        var pick = -1
        for (index in pool.indices) {
            if (taken[index]) continue
            pick = index
            roll -= pool[index].weight
            if (roll <= 0f) break
        }
        taken[pick] = true
        offered += pool[pick]
    }
    return offered
}

/**
 * Accepts omen [id] for the rest of the run: its gains, then its costs, through [applyRunModifier]. The first Elite is
 * rescheduled from the (possibly scaled) profile while it is still ahead, as the run start scheduled it. [preview]
 * (reward previews, on a candidate copy) rebuilds no world and announces nothing.
 */
internal fun MutableGameState.acceptRunOmen(id: OmenId, preview: Boolean = false) {
    val omen = content.variety.omens.definitions[id.ordinal]
    runOmen = id
    applyRunModifiers(omen.gains, preview)
    applyRunModifiers(omen.costs, preview)
    if (elapsed < nextEliteAt) nextEliteAt = rebirthProfile.eliteInterval(content.tempo.firstEliteAtSeconds)
    if (!preview) {
        message = "OMEN // " + omen.name.uppercase()
        messageTime = 2.2f
    }
}

/**
 * Re-applies the accepted omen's stat and Matter-gain effects after [resetRunStats] rebuilt the run's stats; a run
 * without an omen is unchanged. Rerolls, hostile scales, mutators and Keynote floors live outside the reset stats.
 */
internal fun MutableGameState.applyOmenStats() {
    val omen = content.variety.omens.definitions[(runOmen ?: return).ordinal]
    applyStatEffects(omen.gains)
    applyStatEffects(omen.costs)
}

private fun MutableGameState.applyRunModifiers(effects: ImmutableList<RunModifierEffect>, preview: Boolean) {
    for (index in effects.indices) applyRunModifier(effects[index], preview)
}

private fun MutableGameState.applyStatEffects(effects: ImmutableList<RunModifierEffect>) {
    for (index in effects.indices) {
        val effect = effects[index]
        if (effect is RunModifierEffect.Stat || effect is RunModifierEffect.MatterGainScale) applyRunModifier(effect)
    }
}
