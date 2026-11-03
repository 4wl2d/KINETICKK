// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The run layer's weapon level cap (docs/balance/progression.md): no source of weapon levels (Elite resonance, a Totem
 * chest or directed WEAPON reward's Amplify, the automatic amplification) takes the weapon past
 * [kinetickk.ball.content.api.RunTempoProfile.maximumWeaponLevel]; an Amplify card offered there says it salvages Matter
 * and does.
 */
class WeaponLevelCapTest {
    @Test
    fun eliteResonanceStopsAtTheMaximumWeaponLevel() {
        val run = buildTestState(seed = 81)
        val cap = run.content.tempo.maximumWeaponLevel
        run.weaponLevel = cap - 1
        run.killEnemyForTesting(EnemyType.ELITE, x = run.coreX + 400f)
        assertEquals(cap, run.weaponLevel)
        run.takeSoundCues()
        run.message = ""
        run.killEnemyForTesting(EnemyType.ELITE, x = run.coreX + 400f)
        assertEquals(cap, run.weaponLevel, "a resonance at the cap adds no level")
        assertFalse(GameplayAudioCue.WEAPON_ACQUIRED in run.takeSoundCues(), "nor the weapon sound")
        assertFalse(run.message.startsWith("ELITE RESONANCE"), "nor an announcement")
        assertFalse(run.amplifyCurrentWeapon(), "every source goes through the capped amplification")
        assertEquals(cap, run.weaponLevel)
    }

    @Test
    fun anAmplifyCardAtTheMaximumLevelSalvagesMatterInsteadOfALevel() {
        val run = buildTestState(seed = 82)
        val cap = run.content.tempo.maximumWeaponLevel
        run.weaponLevel = cap
        run.openTotemChoice()
        assertEquals(GamePhase.CHOICE, run.phase)
        val amplify = run.choices.single { it.totemAction == TotemAction.AMPLIFY_CURRENT }
        assertEquals(ChoiceType.TOTEM, amplify.type)
        assertEquals("This weapon is at its maximum level; selecting it salvages Kinetic Matter.", amplify.description)
        assertEquals("MAX LEVEL", amplify.tag)
        assertNotEquals(amplify.description, amplify.description.localizedContent(AppLanguage.Russian), "the card reads in Russian")
        assertNotEquals(amplify.tag, amplify.tag.localizedContent(AppLanguage.Russian))

        val matter = run.runMatter + run.matterFraction
        run.choose(run.choices.indexOf(amplify))
        assertEquals(cap, run.weaponLevel)
        assertEquals(WEAPON_PEAK_SALVAGE_MATTER * run.matterGain, run.runMatter + run.matterFraction - matter, 1e-3f)
        assertEquals("WEAPON AT MAXIMUM // MATTER SALVAGED", run.message)
        assertNotEquals(run.message, run.message.localizedContent(AppLanguage.Russian))
    }

    @Test
    fun belowTheMaximumTheAmplifyCardStillAdvancesOneLevel() {
        val run = buildTestState(seed = 83)
        val cap = run.content.tempo.maximumWeaponLevel
        run.weaponLevel = cap - 1
        run.openTotemChoice()
        val amplify = run.choices.single { it.totemAction == TotemAction.AMPLIFY_CURRENT }
        assertEquals("Advance the current system from level ${cap - 1} to $cap immediately.", amplify.description)
        val matter = floor(run.runMatter + run.matterFraction)
        run.choose(run.choices.indexOf(amplify))
        assertEquals(cap, run.weaponLevel)
        assertTrue(floor(run.runMatter + run.matterFraction) == matter, "an amplification salvages nothing")
    }
}
