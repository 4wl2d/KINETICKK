// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reference bot's Relic picks: it binds a Relic its orbiting Arc Coil build can use, melds rather than bind one whose
 * rule never fires for it, and plays the Hollow Crown as the hollow build it is (the Crown with empty slots, Melds after).
 */
class NoPoiRelicPickTest {
    @Test
    fun itMeldsRatherThanBindARelicItsBuildCannotUse() {
        val run = offer(RelicId.STASIS_ANCHOR, RelicId.THUNDER_CHOIR)
        NoPoiBot().choose(run)
        assertEquals(1, run.relicRun.meldedSlots, "it never brakes on purpose and applies no Sonic")
        assertEquals(emptyList(), run.equippedRelics)
    }

    @Test
    fun itBindsARelicItsWeaponBuildUses() {
        val run = offer(RelicId.ECHO_CHAMBER)
        NoPoiBot().choose(run)
        assertEquals(1, run.relicRank(RelicId.ECHO_CHAMBER), "weapon echoes pay for an Arc Coil build")
        assertEquals(0, run.relicRun.meldedSlots)
    }

    @Test
    fun withTheHollowCrownBoundItMeldsTheOtherSlots() {
        val run = offer(RelicId.ECHO_CHAMBER, bound = listOf(RelicId.HOLLOW_CROWN))
        NoPoiBot().choose(run)
        assertEquals(1, run.relicRun.meldedSlots, "a bound Relic would fill a slot the Crown counts")
        assertEquals(0, run.relicRank(RelicId.ECHO_CHAMBER))
    }

    @Test
    fun itTakesTheHollowCrownOnlyWhileItsSlotsAreEmpty() {
        val early = offer(RelicId.HOLLOW_CROWN, bound = listOf(RelicId.ECHO_CHAMBER))
        NoPoiBot().choose(early)
        assertEquals(1, early.relicRank(RelicId.HOLLOW_CROWN))
        val late = offer(RelicId.HOLLOW_CROWN, bound = listOf(RelicId.ECHO_CHAMBER, RelicId.AGONY_SCEPTER))
        NoPoiBot().choose(late)
        assertEquals(0, late.relicRank(RelicId.HOLLOW_CROWN), "with two Relics bound the Crown would pay for one slot")
        assertEquals(1, late.relicRun.meldedSlots)
    }

    /** A Rebirth 3 run with [bound] claimed and an open Relic offer of [relics] plus the Meld card. */
    private fun offer(vararg relics: RelicId, bound: List<RelicId> = emptyList()): MutableGameState {
        val run = buildTestState(rebirth = 3)
        bound.forEach(run::claimRelicForTesting)
        run.openRelicChoiceForTesting()
        run.choices = relics.map { ChoiceOption(ChoiceType.RELIC, "", "", "", relicId = it, relicAction = RelicChoiceAction.ACQUIRE) } +
            run.relicOfferTailCard()
        return run
    }
}
