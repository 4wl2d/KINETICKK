// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** World discoveries: known for the whole run, published once through the Profile progress update. */
class WorldDiscoveryTest {
    /** A started run whose Profile already knows [known]; the start's own progress is taken first. */
    private fun game(known: Set<WorldDiscovery> = emptySet()) = MutableGameState(
        canonicalGameplayContent,
        bootstrapProgress = GameplayProfileSnapshot(
            preferences = PlayerPreferences(),
            economy = PlayerEconomy(),
            loadout = PlayerLoadout(),
            matrix = MatrixProgress(),
            collection = PlayerCollection(discoveredItemIds = emptySet(), discoveredWorldEntries = known),
            rebirthProgress = RebirthProgress(),
        ),
    ).apply {
        startRun()
        takeProgressUpdate()
    }

    private fun mask(vararg ids: WorldDiscovery): Int = ids.fold(0) { mask, id -> mask or id.bit }

    @Test
    fun discoverWorldIsIdempotentAndSetsThePendingAndKnownMasks() {
        val game = game()
        assertEquals(0, game.worldDiscoveryMask)
        assertFalse(game.hasUrgentProgress())

        game.discoverWorld(WorldDiscovery.GATE_RACE)
        assertEquals(mask(WorldDiscovery.GATE_RACE), game.worldDiscoveryMask)
        assertEquals(mask(WorldDiscovery.GATE_RACE), game.pendingWorldDiscoveryMask)
        assertTrue(game.hasUrgentProgress(), "a discovery is published by the next reduction")
        assertTrue(game.hasPublishableProgress())

        game.discoverWorld(WorldDiscovery.GATE_RACE)
        assertEquals(mask(WorldDiscovery.GATE_RACE), game.worldDiscoveryMask)
        assertEquals(mask(WorldDiscovery.GATE_RACE), game.pendingWorldDiscoveryMask)

        game.discoverWorld(WorldDiscovery.QUIET_SIGNAL)
        assertEquals(mask(WorldDiscovery.GATE_RACE, WorldDiscovery.QUIET_SIGNAL), game.worldDiscoveryMask)
        assertEquals(mask(WorldDiscovery.GATE_RACE, WorldDiscovery.QUIET_SIGNAL), game.pendingWorldDiscoveryMask)
    }

    @Test
    fun theNextProgressUpdateCarriesTheDiscoveriesAndClearsPending() {
        val game = game()
        game.discoverWorld(WorldDiscovery.TRIALS)
        game.discoverWorld(WorldDiscovery.ANTECEDENT)

        val update = assertNotNull(game.takeProgressUpdate())
        assertEquals(setOf(WorldDiscovery.TRIALS, WorldDiscovery.ANTECEDENT), update.discoveredWorldEntries)
        assertFalse(update.isEmpty)
        assertEquals(0, game.pendingWorldDiscoveryMask)
        assertEquals(mask(WorldDiscovery.TRIALS, WorldDiscovery.ANTECEDENT), game.worldDiscoveryMask, "the run still knows them")
        assertFalse(game.hasUrgentProgress())
        assertNull(game.takeProgressUpdate(), "nothing is published twice")

        // Found again in the same run: still known, never republished.
        game.discoverWorld(WorldDiscovery.TRIALS)
        assertEquals(0, game.pendingWorldDiscoveryMask)
        assertNull(game.takeProgressUpdate())

        // A later first find rides the next update alone.
        game.discoverWorld(WorldDiscovery.DRIFT_CACHE)
        assertEquals(setOf(WorldDiscovery.DRIFT_CACHE), assertNotNull(game.takeProgressUpdate()).discoveredWorldEntries)
    }

    @Test
    fun aDiscoveryKnownFromTheProfileNeverRepublishes() {
        val known = setOf(WorldDiscovery.GATE_RACE, WorldDiscovery.ZENITH, WorldDiscovery.CARTOGRAPHER)
        val game = game(known)
        assertEquals(mask(*known.toTypedArray()), game.worldDiscoveryMask, "the run starts knowing the Profile's discoveries")
        assertEquals(0, game.pendingWorldDiscoveryMask)

        known.forEach(game::discoverWorld)
        assertEquals(0, game.pendingWorldDiscoveryMask)
        assertFalse(game.hasUrgentProgress())
        assertNull(game.takeProgressUpdate())

        game.discoverWorld(WorldDiscovery.TRIALS)
        assertEquals(setOf(WorldDiscovery.TRIALS), assertNotNull(game.takeProgressUpdate()).discoveredWorldEntries)
        assertEquals(mask(*(known + WorldDiscovery.TRIALS).toTypedArray()), game.worldDiscoveryMask)

        // A restart keeps what the run knows; a new state from the same Profile starts from the Profile again.
        game.startRun()
        assertEquals(mask(*(known + WorldDiscovery.TRIALS).toTypedArray()), game.worldDiscoveryMask)
        assertEquals(mask(*known.toTypedArray()), game(known).worldDiscoveryMask)
    }

    @Test
    fun droppingPendingProgressDropsUnpublishedDiscoveriesAndKeepsWhatTheRunKnows() {
        // The Baby mode path drops every pending Profile fact instead of publishing it.
        val game = game()
        game.discoverWorld(WorldDiscovery.SPIN_CYCLE)
        game.clearPendingProgress()
        assertEquals(0, game.pendingWorldDiscoveryMask)
        assertEquals(mask(WorldDiscovery.SPIN_CYCLE), game.worldDiscoveryMask)
        assertFalse(game.hasUrgentProgress())
        assertNull(game.takeProgressUpdate())
    }

    @Test
    fun aReductionCandidateKeepsItsDiscoveriesApartFromItsSource() {
        val source = game()
        source.discoverWorld(WorldDiscovery.GATE_RACE)
        val candidate = source.copyForReduction()
        candidate.discoverWorld(WorldDiscovery.VAULT)
        assertEquals(setOf(WorldDiscovery.GATE_RACE, WorldDiscovery.VAULT), assertNotNull(candidate.takeProgressUpdate()).discoveredWorldEntries)

        assertEquals(mask(WorldDiscovery.GATE_RACE), source.worldDiscoveryMask)
        assertEquals(mask(WorldDiscovery.GATE_RACE), source.pendingWorldDiscoveryMask)
        assertEquals(setOf(WorldDiscovery.GATE_RACE), assertNotNull(source.takeProgressUpdate()).discoveredWorldEntries)
    }
}
