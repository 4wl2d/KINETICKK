// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DimensionPolicy
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.RunBoonKind
import kinetickk.ball.content.api.RunTempoProfile
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldLayer
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Bootstrap cross-checks of the dimensions against the roster, the POI windows, the run tempo and the boons. */
class DimensionCatalogValidationTest {
    private val data = defaultContentBootstrapData()

    @Test
    fun theShippedCatalogPublishesItsDimensionsAndBoons() {
        val gameplay = createContentCatalogForTesting(data).gameplayContent()
        assertEquals(DimensionPolicy(), gameplay.dimensions)
        assertEquals(10, gameplay.boons.count { it.kind == RunBoonKind.BLESSING })
        assertEquals(10, gameplay.boons.count { it.kind == RunBoonKind.PACT })
    }

    @Test
    fun everyDimensionOpensWithANativeFromTheFirstSecondAtTierZero() {
        // One native opener is enough: a later Seraph leaves the Choir opening the Zenith.
        val lateSeraph = data.enemies.replace(EnemyType.SERAPH) { it.copy(timeGateSeconds = 10f) }
        createContentCatalogForTesting(data.copy(enemies = lateSeraph))
        // A Shooter opening the Zenith satisfies the roster's own rule, but the Zenith would open with a realspace visitor.
        val shooterOpens = lateSeraph.replace(EnemyType.SHOOTER) { it.copy(timeGateSeconds = 0f) }
        createContentCatalogForTesting(data.copy(enemies = shooterOpens))
        assertRejected(
            data.copy(enemies = shooterOpens.replace(EnemyType.CHOIR) { it.copy(timeGateSeconds = 0.5f) }),
            "ZENITH must open with a native",
        )
        // Tier counts too: the Drifter still opens the Archive, but neither of its natives does on tier 0.
        val tieredCipher = data.enemies.replace(EnemyType.CIPHER) { it.copy(minimumRebirth = 1) }
        createContentCatalogForTesting(data.copy(enemies = tieredCipher))
        assertRejected(
            data.copy(enemies = tieredCipher.replace(EnemyType.MIRROR) { it.copy(timeGateSeconds = 5f) }),
            "ARCHIVE must open with a native",
        )
        // A native that also lives in structures still opens its dimension.
        val choirInRooms = shooterOpens.replace(EnemyType.CHOIR) {
            it.copy(habitats = it.habitats or EnemyHabitat.STRUCTURE.bit, habitatWeights = it.habitatWeights.copy(structure = 2f))
        }
        createContentCatalogForTesting(data.copy(enemies = choirInRooms))
        // Visitors from open space keep their own gates and tiers (the Shooter arrives at 38 s in space and Zenith alike).
        val shooter = data.enemies[EnemyType.SHOOTER.ordinal]
        assertTrue(shooter.livesIn(EnemyHabitat.ZENITH) && shooter.timeGateSeconds > 0f)
        createContentCatalogForTesting(data.copy(enemies = data.enemies.replace(EnemyType.SHOOTER) { it.copy(minimumRebirth = 2) }))
    }

    @Test
    fun objectiveBossesKeepTheirRankAndLayer() {
        val secretThrone = data.enemies.replace(EnemyType.THRONE) { it.copy(rank = EnemyRank.SECRET_BOSS, revealedBy = WorldDiscovery.ZENITH) }
        assertRejected(data.copy(enemies = secretThrone), "JUDGEMENT boss THRONE must be a MINI_BOSS")
        val miniAntecedent = data.enemies.replace(EnemyType.ANTECEDENT) { it.copy(rank = EnemyRank.MINI_BOSS, revealedBy = null) }
        assertRejected(data.copy(enemies = miniAntecedent), "ANTECEDENT boss ANTECEDENT must be a SECRET_BOSS")
        val exiledHerald = data.enemies.replace(EnemyType.PYRE_HERALD) { it.copy(habitats = EnemyHabitat.ZENITH.bit) }
        assertRejected(data.copy(enemies = exiledHerald), "PYRE boss PYRE_HERALD must live in NADIR")
        // A boss may live in more habitats than its own layer.
        val wanderingThrone = data.enemies.replace(EnemyType.THRONE) { it.copy(habitats = it.habitats or EnemyHabitat.SPACE.bit) }
        createContentCatalogForTesting(data.copy(enemies = wanderingThrone))
    }

    @Test
    fun everyOfferedBoonKindHoldsAFullHand() {
        val blessings = data.boons.filter { it.kind == RunBoonKind.BLESSING }
        val pacts = data.boons.filter { it.kind == RunBoonKind.PACT }
        createContentCatalogForTesting(data.copy(boons = blessings.take(3) + pacts.take(3)))
        assertRejected(data.copy(boons = blessings.take(2) + pacts), "ZENITH deals 3 blessing cards")
        assertRejected(data.copy(boons = blessings + pacts.take(2)), "NADIR deals 3 pact cards")
        // The hand follows the policy: a Zenith dealing two cards needs only two Blessings.
        val zenith = data.dimensions.definition(WorldLayer.ZENITH)
        val twoCardZenith = data.dimensions.copy(
            definitions = data.dimensions.definitions.map {
                if (it.layer == WorldLayer.ZENITH) zenith.copy(reward = zenith.reward.copy(cardsOnSuccess = 2)) else it
            }.toImmutableList(),
        )
        createContentCatalogForTesting(data.copy(dimensions = twoCardZenith, boons = blessings.take(2) + pacts))
        // The catalog holds at most the bound, with unique ids.
        val full = (32..47).map { blessings.first().copy(id = it) } + (48..63).map { pacts.first().copy(id = it) }
        assertEquals(ContentBounds.MAX_BOON_DEFINITIONS, full.size)
        createContentCatalogForTesting(data.copy(boons = full))
        assertRejected(data.copy(boons = full + full.first()), "exceeds bootstrap bound")
        assertRejected(data.copy(boons = data.boons + data.boons.last()), "Boon ids must be unique")
    }

    @Test
    fun aRiftWindowMustEndItsVisitBeforeTheBossAndTheArchiveMustStayReachable() {
        // Window 4 (525 s) closes at 565 s, 20 s before 720 - 75 - 60; window 5 (645 s) can never host a rift.
        createContentCatalogForTesting(data.copy(dimensions = data.dimensions.copy(firstPortalWindow = 4)))
        assertRejected(data.copy(dimensions = data.dimensions.copy(firstPortalWindow = 5)), "No POI window can host a rift")
        // An earlier boss: window 1 (165 s, closing 205 s) still fits at 340 s, not at 339 s.
        createContentCatalogForTesting(data.copy(tempo = RunTempoProfile(bossAtSeconds = 340f)))
        assertRejected(data.copy(tempo = RunTempoProfile(bossAtSeconds = 339f)), "No POI window can host a rift")
        // From window 0 the Archive binds first: its glitch rift (100 s visit, 30 s offer, 60 s margin) must fit after 45 s.
        val fromTheFirstWindow = data.dimensions.copy(firstPortalWindow = 0)
        createContentCatalogForTesting(data.copy(dimensions = fromTheFirstWindow, tempo = RunTempoProfile(bossAtSeconds = 235f)))
        assertRejected(
            data.copy(dimensions = fromTheFirstWindow, tempo = RunTempoProfile(bossAtSeconds = 234f)),
            "glitch rift could not appear",
        )
        // A disabled policy opens no rift, so no schedule binds it.
        createContentCatalogForTesting(data.copy(dimensions = data.dimensions.copy(enabled = false), tempo = RunTempoProfile(bossAtSeconds = 200f)))
    }

    private fun List<EnemyDefinition>.replace(type: EnemyType, change: (EnemyDefinition) -> EnemyDefinition): List<EnemyDefinition> =
        map { if (it.type == type) change(it) else it }

    private fun assertRejected(data: ContentBootstrapData, expectedMessage: String) {
        val failure = assertFailsWith<IllegalArgumentException> { createContentCatalogForTesting(data) }
        assertTrue(failure.message.orEmpty().contains(expectedMessage), "Expected '$expectedMessage' in '${failure.message}'")
    }
}
