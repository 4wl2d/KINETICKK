// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kotlin.math.max
import kotlin.math.min

/**
 * Fires a reaction when [incoming] lands on an enemy that already holds at least two stacks of another
 * element. The older element with the most stacks pairs (ties by element order). Bounded by per-enemy and
 * per-pair cooldowns, the generation cap and the reaction token budget.
 */
internal fun MutableGameState.tryReact(enemy: Enemy, incoming: StatusElement, incomingGeneration: Int) {
    if (!enemy.alive) return
    if (enemy.status[StatusCell.REACTION_READY_AT] > elapsed) return
    // A cascade needs a token regardless of its partner; avoid searching when none can be admitted.
    if (incomingGeneration >= 1 && reactionTokens < 1f) return
    var partner: StatusElement? = null
    var partnerStacks = 1
    for (element in StatusElement.entries) {
        if (element == incoming) continue
        val count = stacks(enemy, element)
        if (count > partnerStacks) {
            partner = element
            partnerStacks = count
        }
    }
    val older = partner ?: return
    if (partnerStacks < 2) return
    val reaction = ReactionId.between(incoming, older) ?: return
    val generation = max(incomingGeneration, generation(enemy, older))
    if (generation >= 2) return
    // Each pair keeps its own cooldown, so alternating two reactions cannot bypass it.
    if (enemy.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] > elapsed) return
    if (!admitReaction(generation)) return
    react(enemy, reaction, older, incoming, partnerStacks, generation, scale = 1f)
}

/**
 * Sets off the reaction between the two elements [enemy] holds most of (the older one needs at least two
 * stacks, the next one at least one; ties by element order), whatever the per-enemy reaction cooldown says.
 * The pair cooldown, the token budget, the generation cap and a shifting Architect still refuse it.
 * [scale] multiplies the magnitude up to [MAX_FORCED_REACTION_SCALE]. True when the reaction fired.
 */
internal fun MutableGameState.forceReaction(enemy: Enemy, generation: Int, scale: Float = 1f): Boolean {
    if (!enemy.alive || isShifting(enemy)) return false
    var older: StatusElement? = null
    var olderStacks = 1
    for (element in StatusElement.entries) {
        val count = stacks(enemy, element)
        if (count > olderStacks) {
            older = element
            olderStacks = count
        }
    }
    val first = older ?: return false
    var incoming: StatusElement? = null
    var incomingStacks = 0
    for (element in StatusElement.entries) {
        if (element == first) continue
        val count = stacks(enemy, element)
        if (count > incomingStacks) {
            incoming = element
            incomingStacks = count
        }
    }
    val second = incoming ?: return false
    val reaction = ReactionId.between(second, first) ?: return false
    val resolved = max(generation, max(generation(enemy, first), generation(enemy, second)))
    if (resolved >= 2) return false
    if (enemy.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] > elapsed) return false
    if (!admitReaction(resolved)) return false
    react(enemy, reaction, first, second, olderStacks, resolved, scale.coerceIn(0f, MAX_FORCED_REACTION_SCALE))
    return true
}

/**
 * Replays [reaction] on [enemy] as a cascade (generation 1, so it needs a reaction token) at nominal two
 * plus two stacks: no stack is consumed, no cooldown is written, nothing is discovered, no build residue
 * lands and no REACTION clause fires. [scale] multiplies the magnitude up to [MAX_FORCED_REACTION_SCALE].
 */
internal fun MutableGameState.echoReaction(enemy: Enemy, reaction: ReactionId, scale: Float = 1f): Boolean {
    if (!enemy.alive || isShifting(enemy)) return false
    if (!admitReaction(ECHO_GENERATION)) return false
    resolveReaction(enemy, reaction, ECHO_STACKS, ECHO_STACKS, reaction.first, reaction.second, ECHO_GENERATION,
        scale = scale.coerceIn(0f, MAX_FORCED_REACTION_SCALE), echo = true)
    return true
}

/** Consumes the reacting stacks, writes the cooldowns and the last-reaction cells, then resolves. */
private fun MutableGameState.react(
    enemy: Enemy,
    reaction: ReactionId,
    older: StatusElement,
    incoming: StatusElement,
    a: Int,
    generation: Int,
    scale: Float,
) {
    val b = min(3, stacks(enemy, incoming))
    enemy.status[StatusCell.STACKS + older.ordinal] = 0f
    enemy.status[StatusCell.STACKS + incoming.ordinal] = (stacks(enemy, incoming) - b).toFloat()
    if (older == StatusElement.VOID || incoming == StatusElement.VOID) forgetHollowWhenEmpty(enemy)
    enemy.status[StatusCell.REACTION_READY_AT] = elapsed + content.build.policy.reactionCooldownSeconds
    enemy.status[StatusCell.LAST_REACTION] = (reaction.ordinal + 1).toFloat()
    enemy.status[StatusCell.LAST_REACTION_AT] = elapsed
    enemy.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] =
        elapsed + content.build.policy.reactionPairCooldownSeconds
    resolveReaction(enemy, reaction, a, b, older, incoming, generation, scale)
}
