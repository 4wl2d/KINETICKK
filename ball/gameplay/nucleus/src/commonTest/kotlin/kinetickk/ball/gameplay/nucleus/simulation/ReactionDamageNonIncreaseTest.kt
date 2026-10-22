// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The verbs change how reactions play, never how hard they hit: for four older and three incoming stacks
 * at potency 1 every reaction's immediate damage to its target, its Blackfire burn and its field's damage
 * rate stay at or below the 0.4.0 reaction (its old power, no verb).
 */
class ReactionDamageNonIncreaseTest {
    @Test
    fun noReactionHitsHarderThanItDidBeforeItsVerb() {
        ReactionId.entries.forEach { reaction ->
            val now = strike(reaction, canonicalGameplayContent)
            val before = strike(reaction, legacyReactions)
            assertTrue(now.target <= before.target + 1e-3f, "$reaction target damage ${now.target} > ${before.target}")
            assertTrue(now.burn <= before.burn + 1e-3f, "$reaction burn ${now.burn} > ${before.burn}")
            assertTrue(now.fieldRate <= before.fieldRate + 1e-3f, "$reaction field rate ${now.fieldRate} > ${before.fieldRate}")
        }
    }

    private class Strike(val target: Float, val burn: Float, val fieldRate: Float)

    private fun strike(reaction: ReactionId, content: kinetickk.ball.content.api.GameplayContentSnapshot): Strike {
        val state = buildTestState(content = content)
        val target = state.dummy(x = 900f)
        state.resolveReaction(target, reaction, a = 4, b = 3, older = reaction.first, incoming = reaction.second, generation = 0)
        return Strike(
            target = target.maxHp - target.hp,
            burn = target.status[StatusCell.BLACKFIRE_DPS],
            fieldRate = state.fieldRecords.maxOfOrNull { it.damagePerSecond } ?: 0f,
        )
    }

    private companion object {
        /** 0.4.0's reaction powers where they changed; every other reaction kept its power. */
        val legacyPowers = mapOf(
            ReactionId.THERMAL_SHOCK to 12f, ReactionId.PLASMA_BURST to 6f, ReactionId.WILDFIRE to 5f,
            ReactionId.SUPERCONDUCT to 7f, ReactionId.MAGNETIZE to 5f, ReactionId.SEISMIC to 4f,
            ReactionId.FRACTURE to 3f, ReactionId.ANNIHILATE to 9f,
        )

        val legacyReactions = canonicalGameplayContent.copy(
            build = canonicalGameplayContent.build.copy(
                reactions = canonicalGameplayContent.build.reactions.map { definition ->
                    definition.copy(power = legacyPowers[definition.id] ?: definition.power, values = immutableListOf())
                }.toImmutableList(),
            ),
        )
    }
}
