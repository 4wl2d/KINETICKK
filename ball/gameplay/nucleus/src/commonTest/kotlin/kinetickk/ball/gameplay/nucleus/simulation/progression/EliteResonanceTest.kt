// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Elite resonance: every Elite-class kill (Elite, Herald, mini-bosses, secret bosses: the kills that drop a Key)
 * amplifies the current weapon at once by the tempo's resonance levels, announced like an amplification and with the
 * weapon sound; ordinary kills and the Architect never resonate; the knob sets the levels (0 turns it off) and the
 * resonance draws from no run stream.
 */
class EliteResonanceTest {
    @Test
    fun everyEliteClassKillAmplifiesTheCurrentWeaponOnce() {
        for (type in listOf(EnemyType.ELITE, EnemyType.HERALD, EnemyType.WARDEN_PRIME, EnemyType.CARTOGRAPHER)) {
            val run = buildTestState(seed = 71)
            val level = run.weaponLevel
            run.takeSoundCues()
            run.killEnemyForTesting(type, x = run.coreX + 400f)
            assertEquals(level + 1, run.weaponLevel, "$type resonates once")
            assertTrue(GameplayAudioCue.WEAPON_ACQUIRED in run.takeSoundCues(), "$type plays the weapon sound")
            assertTrue(run.pickups.any { it.type == PickupType.KEY }, "$type still drops a Key")
            assertTrue(run.pickups.any { it.type == PickupType.RELIC }, "$type still drops a Relic")
        }
        val elite = buildTestState(seed = 71)
        elite.weaponLevel = 1
        elite.killEnemyForTesting(EnemyType.ELITE, x = elite.coreX + 400f)
        assertEquals("ELITE RESONANCE // LEVEL 2", elite.message)
        // At a milestone the mastery reached is announced instead of the level.
        elite.weaponLevel = WeaponMastery.AMPLIFIED.minimumLevel - 1
        elite.killEnemyForTesting(EnemyType.ELITE, x = elite.coreX + 400f)
        assertEquals("ELITE RESONANCE // AMPLIFIED", elite.message)
    }

    @Test
    fun ordinaryKillsAndTheArchitectNeverResonate() {
        val run = buildTestState(seed = 72)
        val level = run.weaponLevel
        repeat(6) { run.killEnemyForTesting(EnemyType.DRIFTER, x = run.coreX + 400f) }
        run.killEnemyForTesting(EnemyType.SHOOTER, x = run.coreX + 400f)
        assertEquals(level, run.weaponLevel)
        val architect = buildTestState(seed = 72)
        val before = architect.weaponLevel
        architect.killEnemyForTesting(EnemyType.ARCHITECT, x = architect.coreX + 400f)
        assertEquals(before, architect.weaponLevel, "the Architect is no Elite-class kill")
    }

    @Test
    fun theKnobSetsTheLevelsAndZeroTurnsResonanceOff() {
        for (levels in listOf(0, 1, 3)) {
            val run = buildTestState(seed = 73, content = resonance(levels))
            val level = run.weaponLevel
            run.takeSoundCues()
            run.killEnemyForTesting(EnemyType.ELITE, x = run.coreX + 400f)
            assertEquals(level + levels, run.weaponLevel, "$levels resonance levels")
            assertEquals(levels > 0, GameplayAudioCue.WEAPON_ACQUIRED in run.takeSoundCues())
        }
        val off = buildTestState(seed = 73, content = resonance(0))
        off.message = ""
        off.killEnemyForTesting(EnemyType.ELITE, x = off.coreX + 400f)
        assertFalse(off.message.startsWith("ELITE RESONANCE"), "no announcement while it is off")
    }

    @Test
    fun resonanceDrawsFromNoRunStream() {
        val on = buildTestState(seed = 74, content = resonance(1))
        val off = buildTestState(seed = 74, content = resonance(0))
        assertEquals(on.streams(), off.streams())
        on.killEnemyForTesting(EnemyType.ELITE, x = on.coreX + 400f)
        off.killEnemyForTesting(EnemyType.ELITE, x = off.coreX + 400f)
        assertEquals(off.streams(), on.streams(), "the resonance changes no stream position")
        assertEquals(off.weaponLevel + 1, on.weaponLevel)
    }

    private fun resonance(levels: Int): GameplayContentSnapshot =
        canonicalGameplayContent.copy(tempo = canonicalGameplayContent.tempo.copy(eliteResonanceLevels = levels))
}
