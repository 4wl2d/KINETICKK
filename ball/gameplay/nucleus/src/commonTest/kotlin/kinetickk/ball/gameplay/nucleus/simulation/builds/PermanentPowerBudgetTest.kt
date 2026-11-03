// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Design target P7 of docs/balance/progression.md: every permanent family is perceptible at the run start of the Orb with
 * Arc Coil. Maxed Honing multiplies the weapon's own hits by at least 1.8, maxed Refit the character's damage by at least
 * 1.6, and a full Core Matrix against none multiplies weapon damage per second (hit power, the all-damage multiplier,
 * the expected critical factor and the activation rate) and effective integrity (integrity plus shield, over the damage
 * that gets through reduction) by at least 1.8 each.
 */
class PermanentPowerBudgetTest {
    @Test
    fun maxedHoningAndRefitArePerceptible() {
        val plain = start()
        val maxed = start(refit = MAX, honing = MAX)
        val honing = maxed.effectiveWeaponPower() / plain.effectiveWeaponPower()
        val refit = maxed.dealCharacterDamage(maxed.addEnemyForTesting(400f, 0f, hp = 100_000f), 50f).amount /
            plain.dealCharacterDamage(plain.addEnemyForTesting(400f, 0f, hp = 100_000f), 50f).amount
        println("PERMANENT_BUDGET honing=${honing}x refit=${refit}x integrity=${maxed.maxHp / plain.maxHp}x")
        assertTrue(honing >= 1.8f, "maxed Honing multiplies the weapon's hits by $honing")
        assertTrue(refit >= 1.6f, "maxed Refit multiplies character damage by $refit")
    }

    @Test
    fun aFullMatrixAtLeastNearlyDoublesOffenseAndEffectiveIntegrity() {
        val none = start()
        val full = start(fullMatrix = true)
        val offense = full.weaponDamagePerSecond() / none.weaponDamagePerSecond()
        val integrity = full.effectiveIntegrity() / none.effectiveIntegrity()
        println("PERMANENT_BUDGET matrix offense=${offense}x effectiveIntegrity=${integrity}x " +
            "(hit ${full.effectiveWeaponPower() * full.matrix.damageMultiplier / none.effectiveWeaponPower()}x)")
        assertTrue(offense >= 1.8f, "a full Matrix multiplies weapon damage per second by $offense")
        assertTrue(integrity >= 1.8f, "a full Matrix multiplies effective integrity by $integrity")
    }

    private fun MutableGameState.weaponDamagePerSecond(): Float =
        effectiveWeaponPower() * matrix.damageMultiplier * (1f + effectiveCritChance() * (critMultiplier - 1f)) / cooldown(1f)

    private fun MutableGameState.effectiveIntegrity(): Float = (maxHp + shieldCapacity()) / (1f - effectiveDamageReduction())

    private fun start(fullMatrix: Boolean = false, refit: Int = 0, honing: Int = 0): MutableGameState {
        val content = canonicalGameplayContent
        return MutableGameState(
            content,
            bootstrapProgress = GameplayProfileSnapshot(
                preferences = PlayerPreferences(),
                economy = PlayerEconomy(),
                loadout = PlayerLoadout(CoreShape.ORB, WeaponId.ARC_COIL, setOf(WeaponId.FLUX_WAKE, WeaponId.ARC_COIL)),
                matrix = MatrixProgress(content.matrixNodes.map { if (fullMatrix) it.maxRanks else 0 }),
                collection = PlayerCollection(),
                rebirthProgress = RebirthProgress(),
                upgrades = PermanentUpgrades(
                    List(CoreShape.entries.size) { if (it == CoreShape.ORB.ordinal) refit else 0 },
                    List(WeaponId.entries.size) { if (WeaponId.entries[it] == WeaponId.ARC_COIL) honing else 0 },
                ),
            ),
        ).apply {
            startRun()
            enemies.clear()
            projectiles.clear()
        }
    }

    private companion object {
        val MAX: Int = canonicalGameplayContent.upgrades.maxLevel
    }
}
