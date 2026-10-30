// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.FIGHTS_PER_CASE
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.FRAME
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.arena
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.fights
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.fightsOf
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex"): the owner asked that the level-42 ability strongly
 * affect the final fight. This class holds its contract; [ApexArchitectCastTest] holds the cast rule,
 * [ApexArchitectOffenceTest], [ApexArchitectPrismDefenceTest] and [ApexArchitectDiamondDefenceTest] the benefit bands,
 * and [ApexArchitectFightHarness] plays the fights of all of them.
 *
 * The contract holds in every Apex arm of the first three runs per case: each strike stays within the boss's hit cap,
 * an activation spends at most its strikes and its bolt budget, the boss's integrity never moves while it shifts,
 * every shift starts at a phase gate and lasts exactly 8 s, its escorts arrive, and the Apex costs the Architect at
 * most 10 % of the volleys its own timer schedules: a volley counts against it when the boss could attack and the
 * shared projectile pool was at the volley budget only because of live Apex shots (a Silence and a pool full of other
 * shots hold volleys back with or without an Apex). In a scripted arena where the Architect is the only enemy, the
 * character's own damage to it during an activation (strikes included) is never below the same script's without the
 * Apex. The contract keeps its fixed seeds: on the 0.6.0 tree no clause broke in the 312 population fights of the
 * cast rule.
 */
class ApexArchitectFightTest {
    @Test
    fun everyApexKeepsTheArchitectContractAtTierZeroAndTen() {
        val broken = mutableListOf<String>()
        for (shape in CoreShape.entries) for (tier in listOf(0, 10)) {
            if (fightsOf(shape, tier).size < FIGHTS_PER_CASE) broken += "$shape tier $tier reached too few Architects"
        }
        for ((key, pair) in fights) {
            val (shape, tier, seed) = key
            val label = "$shape tier $tier seed $seed"
            val (apex, plain) = pair
            println("APEX-FIGHT $shape tier=$tier seed=$seed ${apex.line()} | without ${plain.line()}")
            if (apex.strikeExcess > 1e-3f) broken += "$label strike over the cap by ${apex.strikeExcess}"
            if (apex.maxStrikes > apex.strikeBudget) broken += "$label spent ${apex.maxStrikes} strikes"
            if (apex.maxErasures > apex.boltBudget || apex.minBoltsLeft < 0f) broken += "$label erased ${apex.maxErasures}"
            if (apex.shiftingLoss > 0f) broken += "$label boss lost ${apex.shiftingLoss} while shifting"
            // A shift starts inside a frame (up to three fixed steps) at a phase gate and ends 8 s later.
            val earliest = ARCHITECT_SHIFT_SECONDS - FRAME - MutableGameState.FIXED_STEP
            if (apex.shiftLengths.any { it !in earliest..ARCHITECT_SHIFT_SECONDS + 1e-3f }) {
                broken += "$label shifts end ${apex.shiftLengths} s after their frame"
            }
            if (apex.shiftEscorts.any { it < 2 }) broken += "$label escorts ${apex.shiftEscorts}"
            if (apex.apexVolleyShare < 0.9f) broken += "$label fired ${apex.apexVolleyShare} of the volleys its Apex could cost " +
                "(${apex.apexMissedVolleys} held back by Apex shots, ${apex.volleyShare} of all scheduled)"
        }
        assertTrue(broken.isEmpty(), "Apex contract: $broken")
    }

    @Test
    fun anActivationNeverLowersTheCharactersDamageToTheArchitect() {
        val lowered = mutableListOf<String>()
        for (tier in listOf(0, 10)) for (shape in CoreShape.entries) {
            val apex = arena(shape, tier, apex = true)
            val plain = arena(shape, tier, apex = false)
            println("APEX-ARENA $shape tier=$tier mechanic=${"%.1f".format(apex.mechanic)} (strikes ${"%.1f".format(apex.strikes)}, " +
                "base ${"%.1f".format(apex.mechanic - apex.strikes)}) without=${"%.1f".format(plain.mechanic)}")
            if (apex.mechanic < plain.mechanic) lowered += "$shape tier $tier ${apex.mechanic} below ${plain.mechanic}"
        }
        assertTrue(lowered.isEmpty(), "an Apex lowered the damage its character deals the Architect: $lowered")
    }
}
