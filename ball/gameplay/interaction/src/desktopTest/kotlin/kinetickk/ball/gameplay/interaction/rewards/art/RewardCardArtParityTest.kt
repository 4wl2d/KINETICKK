// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.interaction.rewards.shippedRewardContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The card art lookup against the tables keyed by canonical English item name that chose every scene:
 * for every item of the shipped catalog, [rewardCardArt] finds the scene its name chose, and
 * [usesFamilyArt] holds exactly for the items whose name borrowed a family scene. The name tables
 * below are the reference; they change only together with the art tables.
 */
class RewardCardArtParityTest {
    private val items = shippedRewardContent.items

    @Test
    fun everyShippedItemFindsTheSceneItsNameChose() {
        items.forEach { item -> assertEquals(artForName(item), rewardCardArt(item), "${item.id} ${item.name}") }
    }

    @Test
    fun familyArtFollowsTheNameTables() {
        items.forEach { item ->
            assertEquals(artForName(item) != null && item.name !in BespokeByName, usesFamilyArt(item), "${item.id} ${item.name}")
        }
    }

    @Test
    fun everyNameInTheReferenceTablesIsAShippedEpicOrLegendary() {
        val shipped = items.associateBy { it.name }
        (BespokeByName.keys + FamilyByName.keys).forEach { name ->
            val item = shipped[name]
            assertTrue(item != null && item.rarity >= ItemRarity.EPIC, "$name is a shipped epic or legendary item")
        }
        assertEquals(RewardCardArt.entries.toSet(), BespokeByName.values.toSet(), "every scene has the item it was drawn for")
    }

    /** The lookup as the name tables defined it: bespoke, then (epic and legendary only) family, then by kind. */
    private fun artForName(item: ItemDefinition): RewardCardArt? {
        BespokeByName[item.name]?.let { return it }
        if (item.rarity.rank < ItemRarity.EPIC.rank) return null
        FamilyByName[item.name]?.let { return it }
        return if (item.rarity == ItemRarity.LEGENDARY) RewardCardArt.RESONANCE_FORK else when (item.tags.firstOrNull()) {
            BuildTag.RAM -> RewardCardArt.TUNGSTEN_HEART
            BuildTag.DRIVE -> RewardCardArt.REDLINE_GOVERNOR
            BuildTag.ANCHOR -> RewardCardArt.ARREST_ENGINE
            BuildTag.ARSENAL -> RewardCardArt.OVERCHARGED_CAPACITOR
            BuildTag.PRECISION -> RewardCardArt.GLASS_EDGE
            BuildTag.BULWARK -> RewardCardArt.LAST_STAND
            BuildTag.OVERCLOCK -> RewardCardArt.REDLINE_REACTOR
            BuildTag.CHAIN -> RewardCardArt.DOMINO_PROTOCOL
            BuildTag.FORTUNE -> RewardCardArt.GAMBLERS_LEDGER
            null -> RewardCardArt.PRISMATIC_CORE
        }
    }

    private companion object {
        /** Each bespoke scene by the canonical English name of the item it was drawn for. */
        val BespokeByName: Map<String, RewardCardArt> = mapOf(
            "Tungsten Heart" to RewardCardArt.TUNGSTEN_HEART, "Redline Governor" to RewardCardArt.REDLINE_GOVERNOR,
            "Arrest Engine" to RewardCardArt.ARREST_ENGINE, "Overcharged Capacitor" to RewardCardArt.OVERCHARGED_CAPACITOR,
            "Glass Edge" to RewardCardArt.GLASS_EDGE, "Last Stand" to RewardCardArt.LAST_STAND,
            "Redline Reactor" to RewardCardArt.REDLINE_REACTOR, "Domino Protocol" to RewardCardArt.DOMINO_PROTOCOL,
            "Gambler's Ledger" to RewardCardArt.GAMBLERS_LEDGER, "Phoenix Coil" to RewardCardArt.PHOENIX_COIL,
            "Shatterglass" to RewardCardArt.SHATTERGLASS, "Storm Lattice" to RewardCardArt.STORM_LATTICE,
            "Dark Star" to RewardCardArt.DARK_STAR, "Necrotic Exchange" to RewardCardArt.NECROTIC_EXCHANGE,
            "Deadeye Reticle" to RewardCardArt.DEADEYE_RETICLE, "Prismatic Core" to RewardCardArt.PRISMATIC_CORE,
            "Volatile Compound" to RewardCardArt.VOLATILE_COMPOUND, "Solar Choir" to RewardCardArt.SOLAR_CHOIR,
            "Glacial Heart" to RewardCardArt.GLACIAL_HEART, "Resonance Fork" to RewardCardArt.RESONANCE_FORK,
        )

        /** The family scene each later epic and legendary borrows, by canonical English name. */
        val FamilyByName: Map<String, RewardCardArt> = mapOf(
            "Pile-Up Protocol" to RewardCardArt.DOMINO_PROTOCOL, "Gravity Breech" to RewardCardArt.DARK_STAR,
            "Frozen Moment" to RewardCardArt.SHATTERGLASS, "Annihilator Coil" to RewardCardArt.STORM_LATTICE,
            "Singularity Seed" to RewardCardArt.DARK_STAR, "Stillness Sight" to RewardCardArt.SHATTERGLASS,
            "Avalanche Protocol" to RewardCardArt.DARK_STAR, "Comet Drive" to RewardCardArt.TUNGSTEN_HEART,
            "Siege Ram" to RewardCardArt.ARREST_ENGINE, "Nitro Injector" to RewardCardArt.REDLINE_REACTOR,
            "Hard Stop" to RewardCardArt.REDLINE_GOVERNOR, "Usurer's Contract" to RewardCardArt.LAST_STAND,
            "Feeding Frenzy" to RewardCardArt.NECROTIC_EXCHANGE, "Cathedral Bell" to RewardCardArt.RESONANCE_FORK,
            "Nothing Left" to RewardCardArt.RESONANCE_FORK, "Perfect Carom" to RewardCardArt.GLACIAL_HEART,
            "Pyre Magazine" to RewardCardArt.SOLAR_CHOIR, "Last Word" to RewardCardArt.RESONANCE_FORK,
            "Cornucopia Core" to RewardCardArt.SOLAR_CHOIR, "Twin Chamber" to RewardCardArt.STORM_LATTICE,
            "Alchemical Core" to RewardCardArt.PRISMATIC_CORE, "Overture" to RewardCardArt.OVERCHARGED_CAPACITOR,
            "Encore Protocol" to RewardCardArt.VOLATILE_COMPOUND, "Quench Seal" to RewardCardArt.SHATTERGLASS,
            "Arc Furnace" to RewardCardArt.STORM_LATTICE, "Degenerate Matter" to RewardCardArt.DARK_STAR,
            "Ember Treads" to RewardCardArt.TUNGSTEN_HEART, "Cryogenic Bus" to RewardCardArt.STORM_LATTICE,
            "Glacier Scope" to RewardCardArt.SHATTERGLASS, "Glassblower's Ram" to RewardCardArt.TUNGSTEN_HEART,
            "Like Poles" to RewardCardArt.DARK_STAR, "Shunt Resistor" to RewardCardArt.OVERCHARGED_CAPACITOR,
            "Grave Mulch" to RewardCardArt.NECROTIC_EXCHANGE, "Blast Rider" to RewardCardArt.REDLINE_GOVERNOR,
            "Choir Prism" to RewardCardArt.SHATTERGLASS, "Wavefront Rudder" to RewardCardArt.REDLINE_GOVERNOR,
            "Fault Hammer" to RewardCardArt.ARREST_ENGINE, "Sapper's Wedge" to RewardCardArt.GLASS_EDGE,
            "Anechoic Shell" to RewardCardArt.LAST_STAND, "Brazier of Ash" to RewardCardArt.PHOENIX_COIL,
            "Hourglass Relay" to RewardCardArt.GLACIAL_HEART, "Horizon Knife" to RewardCardArt.RESONANCE_FORK,
            "Starving Lantern" to RewardCardArt.DARK_STAR, "Requiem Engine" to RewardCardArt.RESONANCE_FORK,
            "Gravity Assist" to RewardCardArt.ARREST_ENGINE, "Furnace Liturgy" to RewardCardArt.PHOENIX_COIL,
            "Kinetic Rosary" to RewardCardArt.DEADEYE_RETICLE, "Siphon Script" to RewardCardArt.NECROTIC_EXCHANGE,
            "Hazard Pay" to RewardCardArt.DOMINO_PROTOCOL, "Philosopher's Engine" to RewardCardArt.SOLAR_CHOIR,
            "Summit Crown" to RewardCardArt.SOLAR_CHOIR, "Meridian Engine" to RewardCardArt.GLACIAL_HEART,
            "Resonator" to RewardCardArt.REDLINE_REACTOR, "Entropic Tide" to RewardCardArt.DARK_STAR,
            "Catalyze" to RewardCardArt.STORM_LATTICE, "Dual Infusion" to RewardCardArt.VOLATILE_COMPOUND,
            "Horizon Surfer" to RewardCardArt.DARK_STAR, "Updraft Kiln" to RewardCardArt.PHOENIX_COIL,
        )
    }
}
