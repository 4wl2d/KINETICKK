// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.SkillRank
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.floor
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every rank value a shipped active effect lists stays within the Nucleus bound that its cast runs through, so no card,
 * Codex line or record promises more than the cast does. A value past its bound would run at the bound, as Catalyze's
 * calibrated ranks once did (11 and 14 targets against the 10-enemy scratch, reaction scale 1.4..1.82 against the 1.3
 * forced-reaction ceiling).
 */
class SkillRankBoundsTest {
    @Test
    fun noShippedRankValuePassesTheRuntimeBoundItRunsThrough() {
        val content = canonicalGameplayContent
        // An attuned cast may land any element, so a single application is bounded by the smallest stack cap.
        val stackCap = content.build.elements.minOf { it.stackCap }.toFloat()
        val bounds = listOf(
            atMost(SkillId.CATALYZE, "targets", MutableGameState.MAX_SKILL_SCRATCH.toFloat()) { it.values[CATALYZE_TARGETS] },
            atMost(SkillId.CATALYZE, "reaction scale", MAX_FORCED_REACTION_SCALE) { it.values[CATALYZE_SCALE] },
            atMost(SkillId.RESONATOR, "echo scale", MAX_FORCED_REACTION_SCALE) { it.values[RESONATOR_SCALE] },
            atMost(SkillId.STORM_COIL, "targets per zap", STORM_COIL_MAX_TARGETS.toFloat()) { it.values[STORM_COIL_TARGETS] },
            atMost(SkillId.ARC_HOOK, "chain links", MAX_CHAIN_TARGETS.toFloat()) { it.values[ARC_HOOK_LINKS] },
            atMost(SkillId.METEOR, "Core boost speed", MAX_CORE_IMPULSE_SPEED) { it.values[METEOR_BOOST] },
            atMost(SkillId.SONIC_BOOM, "recoil speed", MAX_CORE_IMPULSE_SPEED) { it.values[BOOM_RECOIL] },
            atMost(SkillId.SOLAR_FLARE, "vented heat cap", MutableGameState.MAX_HEAT) { it.values[SOLAR_VENT_CAP] },
            atMost(SkillId.SOLAR_FLARE, "Scorch at the full vent", SOLAR_SCORCH_CAP.toFloat()) {
                it.values[SOLAR_SCORCH] + floor(min(it.values[SOLAR_VENT_CAP], MutableGameState.MAX_HEAT) / SOLAR_HEAT_PER_SCORCH)
            },
            atMost(SkillId.MASS_DRIVER, "damage taken cut", 1f - MIN_DAMAGE_TAKEN_MULTIPLIER) { it.values[MASS_DRIVER_GUARD] },
            atLeast(SkillId.DUAL_INFUSION, "weapon hits per secondary stack", 1f) { it.values[INFUSION_HITS_PER_SECONDARY] },
            atMost(SkillId.METEOR, "Scorch", stackCap) { it.values[METEOR_SCORCH] },
            atMost(SkillId.FROST_LINE, "Chill per tick", stackCap) { it.values[FROST_LINE_CHILL] },
            atMost(SkillId.FROST_SNAP, "Chill", stackCap) { it.values[FROST_SNAP_CHILL] },
            atMost(SkillId.ARC_HOOK, "Charge", stackCap) { it.values[ARC_HOOK_CHARGE] },
            atMost(SkillId.DECAY_SPORES, "Decay", stackCap) { it.values[SPORES_DECAY] },
            atMost(SkillId.SONIC_BOOM, "Resonance", stackCap) { it.values[BOOM_RESONANCE] },
            atMost(SkillId.VOID_STEP, "Hollow", stackCap) { it.values[VOID_STEP_HOLLOW] },
            atMost(SkillId.CATALYZE, "fallback stacks", stackCap) { it.values[CATALYZE_FALLBACK] },
        ) + SkillId.entries.map { id -> atLeast(id, "cooldown", MIN_SKILL_RECAST_SECONDS) { it.cooldownSeconds } }

        val past = bounds.flatMap { bound ->
            content.skill(bound.effect).ranks.mapIndexedNotNull { index, rank ->
                val value = bound.read(rank)
                val within = if (bound.ceiling) value <= bound.limit else value >= bound.limit
                if (within) null
                else "${bound.effect} rank ${index + 1} ${bound.what} $value, bound ${if (bound.ceiling) "<=" else ">="} ${bound.limit}"
            }
        }
        assertEquals(emptyList(), past)
    }

    private class RankBound(
        val effect: SkillId,
        val what: String,
        val limit: Float,
        val ceiling: Boolean,
        val read: (SkillRank) -> Float,
    )

    private fun atMost(effect: SkillId, what: String, limit: Float, read: (SkillRank) -> Float) =
        RankBound(effect, what, limit, ceiling = true, read)

    private fun atLeast(effect: SkillId, what: String, limit: Float, read: (SkillRank) -> Float) =
        RankBound(effect, what, limit, ceiling = false, read)
}
