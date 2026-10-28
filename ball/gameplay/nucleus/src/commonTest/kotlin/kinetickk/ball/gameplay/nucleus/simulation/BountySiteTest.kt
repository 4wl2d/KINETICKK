// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.aspectElement
import kinetickk.ball.gameplay.nucleus.model.aspectIsImmune
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.windowSiteId
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bounty arena: entering it releases the policy's mini-boss ahead of the Core as an encounter the site owns, drawn
 * from no run stream; the bounty runs untimed until the boss falls (claimed: 30 Matter and the bounty's discovery on top
 * of the boss's own drops) or the Core leaves the arena past 1,300 u from its centre (abandoned: the boss leaves without
 * a kill and nothing is granted). The Architect's arrival removes the boss the same way.
 */
class BountySiteTest {
    private val policy = canonicalGameplayContent.sites.bounty
    private val exit = policy.arenaRadius + policy.exitMargin

    private fun game(seed: Int = 52_211) = MutableGameState(canonicalGameplayContent, seed).apply {
        startRun()
        enemies.clear()
        projectiles.clear()
        pickups.clear()
        clearObstaclesForTesting()
        elapsed = 170f
    }

    /** Enters the bounty of window [offerId]'s [side] at ([x], [y]) from the Core at ([fromX], [fromY]). */
    private fun MutableGameState.enterBounty(
        x: Float = 400f,
        y: Float = 300f,
        fromX: Float = 0f,
        fromY: Float = 0f,
        offerId: Int = 1,
        side: Int = 0,
    ) {
        previousCoreX = fromX
        previousCoreY = fromY
        coreX = fromX
        coreY = fromY
        activateWindowSite(SiteState(windowSiteId(offerId, side), SiteKind.BOUNTY_ARENA, x, y, 205f))
    }

    /** One site step with the Core held at ([x], [y]). */
    private fun MutableGameState.hold(x: Float, y: Float, delta: Float = 0.1f) {
        previousCoreX = x
        previousCoreY = y
        coreX = x
        coreY = y
        updateSites(delta)
    }

    private fun MutableGameState.boss(): Enemy = enemies.single { it.id == sites.single().foeIds.single() }

    private fun MutableGameState.matter(): Double = runMatter + matterFraction.toDouble()

    private fun MutableGameState.discoveredBounty(): Boolean = worldDiscoveryMask and WorldDiscovery.BOUNTY.bit != 0

    @Test
    fun theBountyReleasesItsMiniBossAheadOfTheCoreAsAnEncounterFromNoStream() {
        val game = game()
        val reward = game.rewardRandom.snapshot()
        val spawn = game.spawnRandom.snapshot()
        val gameplay = game.gameplayRandom.snapshot()
        val siteStream = game.siteRandom.snapshot()
        game.enterBounty(x = 400f, y = 300f)
        assertEquals(reward, game.rewardRandom.snapshot())
        assertEquals(spawn, game.spawnRandom.snapshot())
        assertEquals(gameplay, game.gameplayRandom.snapshot())
        assertEquals(siteStream, game.siteRandom.snapshot())

        val bounty = game.sites.single()
        assertTrue(bounty.active)
        assertEquals(0f, bounty.remaining, "the bounty is untimed")
        assertEquals(0f, bounty.clock)
        assertEquals(0, bounty.count)
        assertEquals(game.content.sites.definition(SiteKind.BOUNTY_ARENA).instruction, game.message)
        assertTrue(game.engagementActive())

        // One boss, 300 u beyond the centre on the line from the Core (the unit (0.8, 0.6) here).
        val boss = game.enemies.single()
        assertEquals(listOf(boss.id), bounty.foeIds)
        assertEquals(EnemyType.WARDEN_PRIME, boss.type)
        assertTrue(boss.isMiniBoss)
        assertEquals(400f + 0.8f * policy.bossSpawnOffset, boss.x, 1e-3f)
        assertEquals(300f + 0.6f * policy.bossSpawnOffset, boss.y, 1e-3f)
        val integrity = game.spawnIntegrity(EnemyType.WARDEN_PRIME) * policy.bossIntegrityShare
        assertEquals(integrity, boss.hp, integrity * 1e-5f)
        assertEquals(integrity, boss.maxHp, integrity * 1e-5f)
        // The boss keeps its own profile's aspect (no hashed aspect) and owes no escorts.
        assertEquals(StatusElement.GRAVITIC.ordinal, boss.aspectElement)
        assertTrue(boss.aspectIsImmune)
        assertEquals(0, boss.escortsPending)

        val tag = encounterTag(EncounterOwner.SITE, windowSiteId(1, 0))
        assertEquals(tag, boss.ownerTag)
        val encounter = assertNotNull(game.encounter(tag))
        assertEquals(400f, encounter.centerX)
        assertEquals(300f, encounter.centerY)
        assertEquals(policy.arenaRadius, encounter.areaRadius)
        assertEquals(exit, encounter.abandonDistance)
        assertEquals(boss.id, encounter.bossId)
        assertTrue(game.isPlaceBound(boss))
        assertTrue(game.leashExempt(boss))
    }

    @Test
    fun aCoreOnTheCentreFindsTheBossAheadAlongItsFlight() {
        val game = game()
        game.velocityX = 0f
        game.velocityY = 600f
        game.enterBounty(x = 0f, y = 0f, fromX = 0f, fromY = 0f)
        val boss = game.boss()
        assertEquals(0f, boss.x, 1e-3f)
        assertEquals(policy.bossSpawnOffset, boss.y, 1e-3f)
    }

    @Test
    fun atTheHardCapTheBountyEvictsTheOldestOrdinaryEnemyAndRefusesWithoutOne() {
        val cap = canonicalGameplayContent.rebirth.maxActiveEnemies
        val offer = SiteState(windowSiteId(1, 0), SiteKind.BOUNTY_ARENA, 400f, 0f, 205f)
        val other = SiteState(windowSiteId(1, 1), SiteKind.RESONANT_CIRCUIT, -400f, 0f, 205f)

        // One free slot: the boss takes it and nothing is evicted.
        val room = game()
        repeat(cap - 1) { room.addEnemyForTesting(-900f, 40f * it) }
        val roomIds = room.enemies.map { it.id }
        room.sites = listOf(offer, other)
        room.enterAt(offer)
        assertTrue(room.sites.single().active)
        assertEquals(cap, room.enemies.size)
        assertEquals(roomIds, room.enemies.map { it.id }.dropLast(1))

        // At the cap the oldest ordinary enemy makes room.
        val full = game()
        repeat(cap) { full.addEnemyForTesting(-900f, 40f * it) }
        val fullIds = full.enemies.map { it.id }
        full.sites = listOf(offer, other)
        full.enterAt(offer)
        assertTrue(full.sites.single().active, "the bounty starts at the hard cap")
        assertEquals(cap, full.enemies.size)
        assertEquals(fullIds.drop(1), full.enemies.map { it.id }.dropLast(1), "the oldest ordinary enemy left")
        assertEquals(EnemyType.WARDEN_PRIME, full.enemies.last().type)

        // At the cap with nothing evictable the bounty refuses and stays offered.
        val elites = game()
        repeat(cap) { elites.addEnemyForTesting(-900f, 40f * it, type = EnemyType.ELITE) }
        val eliteIds = elites.enemies.map { it.id }
        elites.sites = listOf(offer, other)
        elites.enterAt(offer)
        assertEquals(listOf(offer, other), elites.sites, "a refused bounty stays offered")
        assertEquals(eliteIds, elites.enemies.map { it.id }, "nothing spawned and nothing evicted")
        assertNull(elites.encounter(siteEncounterTag(offer)))
        assertFalse(elites.engagementActive())
    }

    /** One step that sweeps the Core into [offer]'s centre from 70 u before it. */
    private fun MutableGameState.enterAt(offer: SiteState) {
        previousCoreX = offer.x - 70f
        previousCoreY = offer.y
        coreX = offer.x
        coreY = offer.y
        updateSites(0.01f)
    }

    @Test
    fun theBossStaysInsideItsArenaAndOutOfTheLeashAndTheEviction() {
        val game = game()
        game.enterBounty(x = 400f, y = 300f)
        val boss = game.boss()
        boss.x = 400f + 2_000f
        boss.y = 300f
        game.updateEncounters()
        assertEquals(policy.arenaRadius - boss.radius, length(boss.x - 400f, boss.y - 300f), 1e-2f)
        // Neither the leash nor an eviction ever takes the bounty's boss.
        assertTrue(game.leashExempt(boss))
        game.evictOldestOrdinaryEnemy()
        assertEquals(listOf(boss.id), game.enemies.map { it.id })
    }

    @Test
    fun theBountyIsUntimedAndItsClockCountsUp() {
        val game = game()
        game.enterBounty()
        game.hold(400f, 300f, delta = 60f)
        game.hold(400f, 300f, delta = 0.5f)
        val bounty = game.sites.single()
        assertTrue(bounty.active)
        assertEquals(60.5f, bounty.clock, 1e-4f)
        assertEquals(0f, bounty.remaining)
    }

    @Test
    fun killingTheBossClaimsThirtyMatterOnTopOfItsDropsAndDiscoversTheBounty() {
        val game = game()
        game.enterBounty()
        val boss = game.boss()
        val tag = boss.ownerTag
        // Two guards the boss called in share its encounter.
        assertEquals(2, game.spawnBossAdds(boss, EnemyType.WARDEN, 2, 0.5f, 2))
        val guards = game.enemies.filter { it.id != boss.id }.map { it.id }
        val eliteKills = game.pendingEliteKills

        game.onEnemyKilled(boss)
        assertEquals(eliteKills + 1, game.pendingEliteKills, "the boss pays an Elite kill")
        assertEquals(1, game.pickups.count { it.type == PickupType.KEY })
        assertEquals(1, game.pickups.count { it.type == PickupType.RELIC })
        assertEquals(1, game.sites.single().count)
        assertFalse(game.discoveredBounty(), "the bounty is claimed at its next step")

        val matter = game.matter()
        game.hold(400f, 300f)
        assertTrue(game.sites.isEmpty())
        assertEquals(SiteNotice.BOUNTY_CLAIMED.english, game.message)
        assertEquals(matter + 30.0 * game.matterGain, game.matter(), 1e-3)
        assertTrue(game.discoveredBounty())
        assertTrue(game.pendingWorldDiscoveryMask and WorldDiscovery.BOUNTY.bit != 0)
        assertTrue(game.pendingDirectedRewards.isEmpty(), "the bounty keeps the directed queue free")
        assertNull(game.encounter(tag))
        assertEquals(guards, game.enemies.filter { it.alive }.map { it.id }, "the guards stay")
        assertTrue(game.enemies.filter { it.alive }.all { it.ownerTag == 0L && !game.isPlaceBound(it) }, "as ordinary enemies")
        assertFalse(game.engagementActive())
    }

    @Test
    fun aBossThatFellWithTheCoreInsideIsClaimedEvenIfTheCoreLeavesAtOnce() {
        // The kill lands after the site step that saw the Core inside; the next step claims it wherever the Core is.
        val game = game()
        game.enterBounty(x = 400f, y = 300f)
        game.hold(400f, 300f + exit)
        game.onEnemyKilled(game.boss())
        game.hold(400f, 300f + exit + 200f)
        assertEquals(SiteNotice.BOUNTY_CLAIMED.english, game.message)
        assertTrue(game.discoveredBounty())
    }

    @Test
    fun leavingTheArenaPastItsExitRemovesTheBossAndGrantsNothing() {
        val game = game()
        game.enterBounty(x = 400f, y = 300f)
        val boss = game.boss()
        game.spawnBossAdds(boss, EnemyType.WARDEN, 2, 0.5f, 2)
        val kills = game.kills
        val matter = game.matter()

        // Exactly the exit keeps the bounty, and the encounter keeps its members.
        game.hold(400f, 300f + exit)
        game.updateEncounters()
        assertTrue(game.sites.single().active)
        assertEquals(3, game.enemies.size)

        game.hold(400f, 300f + exit + 1f)
        assertTrue(game.sites.isEmpty())
        assertEquals(SiteNotice.BOUNTY_ABANDONED.english, game.message)
        assertTrue(game.enemies.isEmpty(), "the boss and its guards leave")
        assertEquals(kills, game.kills, "without a kill")
        assertTrue(game.pickups.isEmpty())
        assertEquals(matter, game.matter(), 1e-6)
        assertFalse(game.discoveredBounty())
        assertNull(game.encounter(boss.ownerTag))
        assertFalse(game.engagementActive())
    }

    @Test
    fun aBossGoneWithoutAKillAbandonsTheBounty() {
        val game = game()
        game.enterBounty()
        val tag = game.boss().ownerTag
        game.enemies.clear()
        game.hold(400f, 300f)
        assertTrue(game.sites.isEmpty())
        assertEquals(SiteNotice.BOUNTY_ABANDONED.english, game.message)
        assertFalse(game.discoveredBounty())
        assertNull(game.encounter(tag))
    }

    @Test
    fun theArchitectsArrivalRemovesTheBossWithoutAKill() {
        val game = game()
        game.enterBounty()
        val boss = game.boss()
        game.spawnBossAdds(boss, EnemyType.WARDEN, 2, 0.5f, 2)
        val kills = game.kills
        game.elapsed = game.content.tempo.bossAtSeconds
        game.hold(400f, 300f, delta = 0.01f)
        assertTrue(game.sites.isEmpty())
        assertTrue(game.enemies.isEmpty())
        assertEquals(kills, game.kills)
        assertNull(game.encounter(boss.ownerTag))
        assertNotEquals(SiteNotice.BOUNTY_CLAIMED.english, game.message)
        assertFalse(game.discoveredBounty())
    }

    @Test
    fun aRebaseShiftsTheArenaAndItsBoss() {
        val game = game()
        game.enterBounty(x = 300_000f, y = 20f, fromX = 299_600f, fromY = 20f)
        val boss = game.boss()
        assertEquals(300_000f + policy.bossSpawnOffset, boss.x, 1e-1f)
        game.coreX = 300_000f
        game.coreY = 0f
        game.rebaseWorldIfNeeded()
        val shifted = game.sites.single()
        assertEquals(0f, shifted.x)
        assertEquals(20f, shifted.y)
        assertEquals(policy.bossSpawnOffset, boss.x, 1e-1f)
        val encounter = assertNotNull(game.encounter(boss.ownerTag))
        assertEquals(0f, encounter.centerX)
        assertEquals(20f, encounter.centerY)
        game.hold(0f, 20f + exit)
        assertTrue(game.sites.single().active, "the exit follows the shifted centre")
    }

    @Test
    fun windowsOfferTheBountyFrom165To525AndEnteringItStartsTheOnlyEngagement() {
        assertEquals(1f, canonicalGameplayContent.sites.definition(SiteKind.BOUNTY_ARENA).windowWeight)
        val offeredAt = sortedSetOf<Int>()
        var entered = false
        for (seed in 1..300) {
            val game = game(seed)
            for (index in 0..5) {
                val pair = game.offerWindowForTesting(index)
                val bounty = pair.firstOrNull { it.kind.family == SiteFamily.BOUNTY } ?: continue
                offeredAt += index
                if (entered) continue
                game.velocityX = 500f
                game.enterAt(bounty)
                val running = game.sites.single()
                assertTrue(running.active)
                assertEquals(bounty.id, running.id)
                assertTrue(game.engagementActive())
                assertEquals(EnemyType.WARDEN_PRIME, game.boss().type)
                entered = true
                break
            }
        }
        assertTrue(entered)
        // 165, 285, 405 and 525 s: never at 45 s (before 160 s) or 645 s (its 90 s reserve would reach the Architect).
        assertEquals(sortedSetOf(1, 2, 3, 4), offeredAt)
    }
}
