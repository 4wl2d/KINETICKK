// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyNotices
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnemyEncounterApiTest {
    private fun game() = MutableGameState(canonicalGameplayContent).apply {
        startRun()
        enemies.clear()
        quietArenaForTesting()
    }

    private fun request(
        id: Long,
        members: List<EnemyType>,
        centerX: Float = 600f,
        centerY: Float = 0f,
        evictOrdinary: Boolean = false,
        positions: FloatArray? = null,
        areaRadius: Float = 500f,
    ) = EncounterRequest(
        tag = encounterTag(EncounterOwner.SITE, id),
        centerX = centerX,
        centerY = centerY,
        areaRadius = areaRadius,
        members = members,
        evictOrdinary = evictOrdinary,
        positions = positions,
    )

    @Test
    fun anEncounterSpawnsAllItsMembersOrNoneAtTheHardCap() {
        val game = game()
        val cap = game.content.rebirth.maxActiveEnemies
        val elite = game.spawnedEnemy(EnemyType.ELITE, 2_000f, 0f).also { game.enemies += it }
        val ordinary = List(cap - 4) { game.addEnemyForTesting(1_500f, 200f + it) }
        // Three members fit the three free slots.
        assertTrue(game.spawnEncounter(request(1, List(3) { EnemyType.DRIFTER })))
        assertEquals(cap, game.enemies.size)
        // One more member than the room: refused, nothing changes.
        game.enemies.removeAt(game.enemies.lastIndex)
        game.enemies.removeAt(game.enemies.lastIndex)
        val before = game.enemies.map { it.id }
        assertFalse(game.spawnEncounter(request(2, List(3) { EnemyType.DRIFTER })))
        assertEquals(before, game.enemies.map { it.id })
        assertNull(game.encounter(encounterTag(EncounterOwner.SITE, 2)))
        // With eviction it frees exactly the missing slot from the oldest untagged ordinary enemy.
        assertTrue(game.spawnEncounter(request(2, List(3) { EnemyType.DRIFTER }, evictOrdinary = true)))
        assertEquals(cap, game.enemies.size)
        assertFalse(game.enemies.any { it.id == ordinary.first().id }, "the oldest ordinary enemy leaves")
        assertTrue(elite in game.enemies, "Elites stay")
        assertEquals(4, game.enemies.count { it.ownerTag != 0L }, "tagged members stay")
    }

    @Test
    fun fourEncountersRunAtOnceAndAnEncounterHoldsTwelveMembers() {
        val game = game()
        repeat(MAX_ACTIVE_ENCOUNTERS) { assertTrue(game.spawnEncounter(request(it.toLong(), listOf(EnemyType.DRIFTER)))) }
        assertFalse(game.spawnEncounter(request(9, listOf(EnemyType.DRIFTER))))
        assertEquals(MAX_ACTIVE_ENCOUNTERS, game.encounters.size)

        val members = List(MAX_ENCOUNTER_MEMBERS) { EnemyType.DRIFTER }
        assertEquals(MAX_ENCOUNTER_MEMBERS, request(1, members).members.size)
        assertFailsWith<IllegalArgumentException> { request(1, members + EnemyType.DRIFTER) }
        assertFailsWith<IllegalArgumentException> { request(1, emptyList()) }
    }

    @Test
    fun taggedEnemiesStayWithinTheReserve() {
        val game = game()
        assertTrue(game.spawnEncounter(request(1, List(12) { EnemyType.DRIFTER }, centerX = 600f)))
        assertTrue(game.spawnEncounter(request(2, List(12) { EnemyType.DRIFTER }, centerX = -600f)))
        assertEquals(MAX_ENCOUNTER_RESERVE, game.enemies.count { it.ownerTag != 0L })
        assertFalse(game.spawnEncounter(request(3, listOf(EnemyType.DRIFTER), centerY = 600f)))
        // A kill frees its reserve slot again.
        game.onEnemyKilled(game.enemies.first { it.ownerTag != 0L })
        game.enemies.removeMatchingStable { it.dead }
        assertTrue(game.spawnEncounter(request(3, listOf(EnemyType.DRIFTER), centerY = 600f)))
    }

    @Test
    fun requestBoundsAcceptTheirLimitsAndRejectTheNextValue() {
        assertEquals(120f, request(1, listOf(EnemyType.DRIFTER), areaRadius = 120f).areaRadius)
        assertEquals(2_400f, request(1, listOf(EnemyType.DRIFTER), areaRadius = 2_400f).areaRadius)
        assertFailsWith<IllegalArgumentException> { request(1, listOf(EnemyType.DRIFTER), areaRadius = 119f) }
        assertFailsWith<IllegalArgumentException> { request(1, listOf(EnemyType.DRIFTER), areaRadius = 2_401f) }
        assertFailsWith<IllegalArgumentException> {
            EncounterRequest(0L, 0f, 0f, 300f, listOf(EnemyType.DRIFTER))
        }
        assertFailsWith<IllegalArgumentException> {
            EncounterRequest(encounterTag(EncounterOwner.ROOM, 1), 0f, 0f, 300f, listOf(EnemyType.DRIFTER), integrityShare = 2.01f)
        }
        assertFailsWith<IllegalArgumentException> {
            EncounterRequest(encounterTag(EncounterOwner.ROOM, 1), 0f, 0f, 300f, listOf(EnemyType.DRIFTER), abandonDistance = 6_001f)
        }
        assertFailsWith<IllegalArgumentException> {
            request(1, listOf(EnemyType.DRIFTER, EnemyType.SHOOTER), positions = floatArrayOf(0f, 0f, 1f))
        }
        val game = game()
        assertFailsWith<IllegalArgumentException> { game.spawnEncounter(request(1, listOf(EnemyType.HERALD))) }
        assertFailsWith<IllegalArgumentException> { game.spawnEncounter(request(1, listOf(EnemyType.ELITE))) }
        assertFailsWith<IllegalArgumentException> {
            game.spawnEncounter(request(1, listOf(EnemyType.WARDEN_PRIME, EnemyType.THRONE)))
        }
    }

    @Test
    fun aDuplicateTagIsRefusedAndTagsKeepTheirOwner() {
        val game = game()
        assertTrue(game.spawnEncounter(request(1, listOf(EnemyType.DRIFTER))))
        assertFalse(game.spawnEncounter(request(1, listOf(EnemyType.SHOOTER), centerY = 700f)))
        EncounterOwner.entries.forEach { owner ->
            val tag = encounterTag(owner, 0x00F0_0000_0000_0010L)
            assertEquals(owner, encounterOwner(tag))
            assertTrue(tag != 0L)
        }
        assertNull(encounterOwner(0L))
    }

    @Test
    fun aSecretBossArrivesOncePerRunAndNeverLateBeforeTheArchitect() {
        val game = game()
        assertTrue(game.spawnEncounter(request(1, listOf(EnemyType.CARTOGRAPHER))))
        assertEquals(EnemyNotices.SECRET_SIGNAL + " // " + game.content.enemy(EnemyType.CARTOGRAPHER).name.uppercase(), game.message)
        game.endEncounter(encounterTag(EncounterOwner.SITE, 1), despawn = true)
        assertFalse(game.spawnEncounter(request(2, listOf(EnemyType.CARTOGRAPHER))), "once per run")

        val late = game()
        late.elapsed = late.content.tempo.bossAtSeconds - SECRET_BOSS_LAST_SUMMON_LEAD
        assertTrue(late.spawnEncounter(request(1, listOf(EnemyType.ANTECEDENT))))
        val later = game()
        later.elapsed = later.content.tempo.bossAtSeconds - SECRET_BOSS_LAST_SUMMON_LEAD + 0.01f
        assertFalse(later.spawnEncounter(request(1, listOf(EnemyType.ANTECEDENT))))
    }

    @Test
    fun membersAreLeashExemptClampedInsideTheirAreaAndOutsideTheOrdinaryCap() {
        val game = game()
        val tag = encounterTag(EncounterOwner.ROOM, 4)
        assertTrue(game.spawnEncounter(EncounterRequest(tag, 600f, 0f, 400f, listOf(EnemyType.WARDEN_PRIME, EnemyType.DRIFTER, EnemyType.DRIFTER))))
        val state = assertNotNull(game.encounter(tag))
        val boss = game.enemies.single { it.id == state.bossId }
        assertEquals(EnemyType.WARDEN_PRIME, boss.type)
        assertEquals(600f, boss.x)
        assertEquals(0f, game.ordinaryCapLoad())
        game.enemies.forEach { assertTrue(game.leashExempt(it)) }

        val drifter = game.enemies.first { it.type == EnemyType.DRIFTER }
        drifter.x = 600f + 2_000f
        drifter.vx = 300f
        game.updateEncounters()
        assertEquals(400f - drifter.radius, length(drifter.x - 600f, drifter.y), 1e-2f)
        assertEquals(0f, drifter.vx, 1e-3f, "the outward velocity goes")
    }

    @Test
    fun killsCountOnceAndADespawnNeverCounts() {
        val game = game()
        val tag = encounterTag(EncounterOwner.SITE, 7)
        assertTrue(game.spawnEncounter(request(7, List(3) { EnemyType.DRIFTER })))
        val member = game.enemies.first()
        game.onEnemyKilled(member)
        game.onEnemyKilled(member)
        assertEquals(1, assertNotNull(game.encounter(tag)).killed)
        assertEquals(2, game.liveEncounterMembers(tag))

        val pickups = game.pickups.size
        val kills = game.kills
        game.endEncounter(tag, despawn = true)
        assertTrue(game.enemies.none { it.ownerTag == tag && it.alive })
        assertNull(game.encounter(tag))
        assertEquals(kills, game.kills)
        assertEquals(pickups, game.pickups.size)
        assertTrue(game.encounterStartedAt(tag).isNaN())
    }

    @Test
    fun endingWithoutDespawnTurnsMembersAmbient() {
        val game = game()
        val tag = encounterTag(EncounterOwner.SITE, 8)
        assertTrue(game.spawnEncounter(request(8, List(2) { EnemyType.DRIFTER })))
        game.endEncounter(tag, despawn = false)
        assertEquals(2, game.enemies.size)
        assertTrue(game.enemies.all { it.ownerTag == 0L })
        assertEquals(2f, game.ordinaryCapLoad())
    }

    @Test
    fun leavingTheAbandonDistanceEndsTheEncounterAndASecretBossSignalsItsLoss() {
        val game = game()
        val tag = encounterTag(EncounterOwner.SITE, 2)
        assertTrue(game.spawnEncounter(request(2, listOf(EnemyType.CARTOGRAPHER, EnemyType.DRIFTER))))
        game.coreX = 600f + DEFAULT_ENCOUNTER_ABANDON_DISTANCE
        game.updateEncounters()
        assertNotNull(game.encounter(tag), "exactly at the distance it holds")
        game.coreX += 1f
        game.updateEncounters()
        assertNull(game.encounter(tag))
        assertTrue(game.enemies.isEmpty())
        assertEquals(EnemyNotices.SECRET_SIGNAL_LOST, game.message)
    }

    @Test
    fun theArchitectsArrivalEndsEveryEncounter() {
        val game = game()
        assertTrue(game.spawnEncounter(request(1, List(2) { EnemyType.DRIFTER })))
        game.elapsed = game.content.tempo.bossAtSeconds
        game.simulateStep(MutableGameState.FIXED_STEP)
        assertTrue(game.bossSpawned)
        assertTrue(game.encounters.isEmpty())
        assertTrue(game.enemies.none { it.ownerTag != 0L })
        assertFalse(game.spawnEncounter(request(2, listOf(EnemyType.DRIFTER))), "no encounter starts in the Architect fight")
    }

    @Test
    fun aRebaseShiftsEncounterCentres() {
        val game = game()
        val tag = encounterTag(EncounterOwner.DIMENSION, 1)
        assertTrue(game.spawnEncounter(EncounterRequest(tag, 260_000f, 0f, 400f, listOf(EnemyType.DRIFTER), abandonDistance = 6_000f)))
        game.coreX = 260_000f
        game.rebaseWorldIfNeeded()
        assertEquals(0f, assertNotNull(game.encounter(tag)).centerX)
    }

    @Test
    fun theEncounterSpawnDrawsNothingFromTheSpawnStream() {
        val game = game()
        val spawn = game.spawnRandom.snapshot()
        val gameplay = game.gameplayRandom.snapshot()
        val reward = game.rewardRandom.snapshot()
        assertTrue(game.spawnEncounter(request(1, listOf(EnemyType.BROOD_QUEEN, EnemyType.SHOOTER, EnemyType.DRIFTER))))
        assertEquals(EnemyType.DRIFTER, game.pickHabitatType(EnemyHabitat.SPACE, 0f))
        assertEquals(spawn, game.spawnRandom.snapshot())
        assertEquals(gameplay, game.gameplayRandom.snapshot())
        assertEquals(reward, game.rewardRandom.snapshot())
    }

    @Test
    fun positionsReplaceTheGeometryAndStayInsideTheArea() {
        val game = game()
        val positions = floatArrayOf(700f, 100f, 600f + 5_000f, 0f)
        assertTrue(game.spawnEncounter(request(3, listOf(EnemyType.DRIFTER, EnemyType.SHOOTER), positions = positions)))
        val (first, second) = game.enemies
        assertEquals(700f, first.x)
        assertEquals(100f, first.y)
        assertEquals(600f + 500f - second.radius, second.x, 1e-2f)
        assertEquals(EnemyType.SHOOTER.profile.radius, second.radius)
    }

    @Test
    fun habitatPicksKeepToTheirHabitatAndRoll() {
        val game = game()
        game.elapsed = 400f
        repeat(20) { step ->
            val roll = step / 20f
            val space = game.pickHabitatType(EnemyHabitat.SPACE, roll)
            assertTrue(game.content.enemy(space).weightIn(EnemyHabitat.SPACE) > 0f, "$space lives in SPACE")
            assertEquals(EnemyRank.ORDINARY, space.profile.rank)
            assertEquals(space, game.pickHabitatType(EnemyHabitat.SPACE, roll), "the same roll picks the same kind")
            val zenith = game.pickHabitatType(EnemyHabitat.ZENITH, roll)
            assertTrue(game.content.enemy(zenith).weightIn(EnemyHabitat.ZENITH) > 0f, "$zenith lives in ZENITH")
        }
    }

    @Test
    fun reductionCopiesKeepEncountersAndEnemyTagsIsolated() {
        val source = game()
        val tag = encounterTag(EncounterOwner.SITE, 5)
        assertTrue(source.spawnEncounter(request(5, List(2) { EnemyType.DRIFTER })))
        val candidate = source.copyForReduction()
        candidate.onEnemyKilled(candidate.enemies.first())
        candidate.endEncounter(tag, despawn = false)
        assertEquals(0, assertNotNull(source.encounter(tag)).killed)
        assertTrue(source.enemies.all { it.ownerTag == tag && !it.dead })
        assertEquals(GamePhase.RUNNING, source.phase)
    }
}
