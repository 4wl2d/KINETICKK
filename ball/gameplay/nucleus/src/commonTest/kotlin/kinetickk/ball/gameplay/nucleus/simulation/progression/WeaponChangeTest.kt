// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One weapon change a run: the changed weapon keeps 60 % of the weapon level (rounded down, at least level 1), and once
 * the change is spent Totem chests and weapon sites offer Amplify alone.
 */
class WeaponChangeTest {
    @Test
    fun aChangedWeaponKeepsSixtyPercentOfTheLevelRoundedDownAndNeverBelowOne() {
        val tempo = canonicalGameplayContent.tempo
        assertEquals(1, tempo.weaponChangesPerRun)
        assertEquals(listOf(1, 1, 3, 6, 9, 12), listOf(1, 2, 5, 10, 15, 20).map(tempo::weaponLevelAfterChange))

        val run = armedRun(level = 11)
        run.openTotemChoice()
        run.choose(run.choices.indexOfFirst { it.totemAction == TotemAction.CHANGE_WEAPON })
        assertEquals(ChoiceType.WEAPON, run.choiceType)
        val weapon = run.choices.first().weaponId
        run.choose(0)

        assertEquals(weapon, run.weapon)
        assertEquals(6, run.weaponLevel)
        assertEquals(0, run.weaponChangesLeft())
        assertTrue(run.buildProjection().weaponChangesSpent)
    }

    @Test
    fun aSpentChangeLeavesTotemChestsAndWeaponSitesAmplifyAlone() {
        val run = armedRun(level = 5)
        run.openDirectedReward(DirectedReward.WEAPON)
        run.choose(0)
        val change = run.choices.indexOfFirst { it.type == ChoiceType.WEAPON }
        assertTrue(change >= 0, "an unspent run is offered a weapon site's change: ${run.choices}")
        run.choose(change)
        assertEquals(3, run.weaponLevel)
        assertEquals(GamePhase.RUNNING, run.phase)

        run.openTotemChoice()
        assertEquals(listOf(TotemAction.AMPLIFY_CURRENT), run.choices.map { it.totemAction })
        run.choose(0)
        assertEquals(4, run.weaponLevel, "Amplify stays")

        run.openDirectedReward(DirectedReward.WEAPON)
        assertEquals(listOf(TotemAction.AMPLIFY_CURRENT), run.choices.map { it.totemAction }, "no direction step, Amplify alone")
        assertFalse(run.choices.any { it.type == ChoiceType.WEAPON || it.rewardFocus != null })
    }

    @Test
    fun theWeaponCardPreviewsTheKeptLevel() {
        val run = armedRun(level = 10)
        run.openTotemChoice()
        run.choose(run.choices.indexOfFirst { it.totemAction == TotemAction.CHANGE_WEAPON })
        // Level 10 to 6 drops the level factor (1.72 to 1.40) and the mastery (+35 % to +20 %), a cut of 28 % that
        // outweighs the offered weapon's synergies: without the cut its power would rise above the Arc Coil's.
        val preview = run.rewardPreviews().first()
        val power = preview.changes.single { it.name == "Weapon power" }
        assertTrue(power.after < power.before, "$power")
    }

    /** A quiet run on the Arc Coil at weapon level [level] whose profile owns four more weapons of every direction. */
    private fun armedRun(level: Int): MutableGameState = buildTestState(seed = 91, weapon = WeaponId.ARC_COIL).apply {
        unlockWeaponsForTesting(listOf(WeaponId.ION_SWARM, WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR, WeaponId.ENTROPY_FIELD))
        weaponLevel = level
    }
}
