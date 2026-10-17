// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.profile.interaction.CatalogRebirthItems
import kinetickk.ball.profile.interaction.CatalogWeapons
import kinetickk.ball.profile.interaction.catalogRebirthModel
import kinetickk.ball.profile.interaction.rebirth.api.RebirthUnlock
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Rebirth screen's tier ladder with the shipped weapons (all 24 with their Rebirth gates), characters,
 * Matrix nodes and enemy roster: focusing each tier 1..10 lists exactly what that rank opens. Items and
 * Relics come from the fixture's gated subset here; the shipped catalog's item names per tier are pinned by
 * the content catalog's ladder test.
 */
class RebirthLadderUnlocksTest {
    @Test
    fun focusingEachTierListsExactlyTheContentItsRankOpens() {
        val weapons = mapOf(1 to "Glacier Bell", 2 to "Tether Arc", 3 to "Event Lens", 4 to "Hunter Salvo", 5 to "Solar Sweep",
            6 to "Aegis Shards", 7 to "Hollow Rift", 9 to "Resonance Drum")
        val characters = mapOf(1 to "Spiral", 3 to "Pentagon", 5 to "Crescent", 7 to "Hexagon")
        val relics = mapOf(1 to "Agony Scepter", 3 to "Hollow Crown", 5 to "Ouroboros Seal")
        val matrix = mapOf(
            1 to listOf("Overdrive Coil", "Second Wind", "Ricochet Rig"), 2 to listOf("Bastion Protocol", "Elite Bounty"),
            3 to listOf("Glass Engine", "Heirloom Vault"), 4 to listOf("Juggernaut", "Prospector"), 5 to listOf("Perpetual Drift"),
            6 to listOf("Reborn Arsenal"), 7 to listOf("Aegis Lattice"), 9 to listOf("Gravity Surfer"),
        )
        val enemies = mapOf(2 to listOf("Phantom", "Herald"), 3 to listOf("Broodmother"), 4 to listOf("Reflector"))
        for (tier in 1..10) {
            val shown = catalogRebirthModel(current = 0, focus = tier, highestCleared = tier - 1).focusUnlocks
            fun <T : RebirthUnlock> names(kind: Class<T>) = shown.filterIsInstance(kind).map { it.name }
            assertEquals(listOfNotNull(weapons[tier]), names(RebirthUnlock.Weapon::class.java), "weapons at tier $tier")
            assertEquals(listOfNotNull(characters[tier]), names(RebirthUnlock.Character::class.java), "characters at tier $tier")
            assertEquals(listOfNotNull(relics[tier]), names(RebirthUnlock.Relic::class.java), "Relics at tier $tier")
            assertEquals(CatalogRebirthItems.filter { it.minimumRebirth == tier }.map { it.name }, names(RebirthUnlock.Item::class.java),
                "items at tier $tier")
            assertEquals(matrix[tier].orEmpty(), names(RebirthUnlock.MatrixNode::class.java), "Matrix nodes at tier $tier")
            assertEquals(enemies[tier].orEmpty(), names(RebirthUnlock.Enemy::class.java), "enemies at tier $tier")
            assertEquals(shown.size, shown.count { it.tier == tier }, "only tier $tier content at tier $tier")
        }
        // The weapon column is the 24 weapons' own Rebirth gates.
        assertEquals(CatalogWeapons.filter { it.minimumRebirth > 0 }.associate { it.minimumRebirth to it.name }, weapons)
    }
}
