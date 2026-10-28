// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ARCHITECT_ENCOUNTER
import kinetickk.ball.content.api.BossEncounterSpec
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.HabitatWeights
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.WorldDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EnemyRosterTest {
    @Test
    fun rosterFollowsTheEnemyTypeOrderAndPublishesOneSnapshotList() {
        val catalog = createContentCatalog()
        val gameplay = catalog.gameplayContent().enemies
        val ui = catalog.uiCatalog().enemies

        assertEquals(EnemyType.entries.toList(), gameplay.map { it.type })
        assertSame(gameplay, ui)
        assertEquals(35, gameplay.size)
        assertTrue(gameplay.size <= ContentBounds.MAX_ENEMY_TYPES)
        assertEquals(48, ContentBounds.MAX_ENEMY_TYPES)
    }

    @Test
    fun gatesWeightsAndThreatMatchTheRosterTable() {
        val roster = createContentCatalog().gameplayContent().enemies.associateBy { it.type }
        // type to (gate seconds, minimum Rebirth, SPACE weight, threat)
        val expected = mapOf(
            EnemyType.DRIFTER to Row(0f, 0, 22f, 1),
            EnemyType.SHOOTER to Row(38f, 0, 17f, 2),
            EnemyType.INTERCEPTOR to Row(90f, 0, 14f, 2),
            EnemyType.CHARGER to Row(150f, 0, 14f, 2),
            EnemyType.WEAVER to Row(150f, 0, 12f, 2),
            EnemyType.SPLITTER to Row(240f, 0, 12f, 3),
            EnemyType.WARDEN to Row(240f, 0, 9f, 3),
            EnemyType.ELITE to Row(38f, 0, 0f, 4),
            EnemyType.ARCHITECT to Row(720f, 0, 0f, 5),
            EnemyType.SWARMLING to Row(75f, 0, 10f, 1),
            EnemyType.BOMBER to Row(200f, 0, 8f, 3),
            EnemyType.LANCER to Row(240f, 0, 7f, 3),
            EnemyType.SNIPER to Row(330f, 0, 6f, 3),
            EnemyType.AEGIS to Row(390f, 0, 4f, 3),
            EnemyType.MINELAYER to Row(450f, 0, 5f, 3),
            EnemyType.PHANTOM to Row(300f, 2, 6f, 4),
            EnemyType.BROODMOTHER to Row(420f, 3, 3f, 4),
            EnemyType.REFLECTOR to Row(360f, 4, 5f, 4),
            EnemyType.HERALD to Row(360f, 2, 0f, 5),
            // The two new open-space kinds join the run at two and four and a half minutes.
            EnemyType.COMET to Row(120f, 0, 7f, 2),
            EnemyType.PYLON to Row(270f, 0, 4f, 3),
            EnemyType.SENTRY to Row(0f, 0, 0f, 3),
            EnemyType.CRAWLER to Row(0f, 0, 0f, 1),
            EnemyType.SERAPH to Row(0f, 0, 0f, 3),
            EnemyType.CHOIR to Row(0f, 0, 0f, 3),
            EnemyType.IMP to Row(0f, 0, 0f, 2),
            EnemyType.HOUND to Row(0f, 0, 0f, 2),
            EnemyType.CIPHER to Row(0f, 0, 0f, 3),
            EnemyType.MIRROR to Row(0f, 0, 0f, 3),
            EnemyType.WARDEN_PRIME to Row(45f, 0, 0f, 4),
            EnemyType.BROOD_QUEEN to Row(60f, 0, 0f, 4),
            EnemyType.THRONE to Row(165f, 0, 0f, 5),
            EnemyType.PYRE_HERALD to Row(165f, 0, 0f, 5),
            EnemyType.CARTOGRAPHER to Row(60f, 0, 0f, 5),
            EnemyType.ANTECEDENT to Row(300f, 0, 0f, 5),
        )
        assertEquals(EnemyType.entries.toSet(), expected.keys)
        expected.forEach { (type, row) ->
            val definition = roster.getValue(type)
            assertEquals(row, Row(definition.timeGateSeconds, definition.minimumRebirth, definition.weight, definition.threat), type.name)
        }
        // The Drifter's share decays to 40 % by six minutes, in open space and inside structures alike.
        val drifter = roster.getValue(EnemyType.DRIFTER)
        assertEquals(22f, drifter.baseWeight(0f))
        assertEquals(22f * 0.4f, drifter.baseWeight(360f), 1e-4f)
        assertEquals(22f * 0.4f, drifter.baseWeight(600f), 1e-4f)
        assertEquals(10f * 0.4f, drifter.baseWeight(360f, EnemyHabitat.STRUCTURE), 1e-4f)
    }

    @Test
    fun habitatsAndTheirWeightsMatchTheRosterTable() {
        val roster = createContentCatalog().gameplayContent().enemies.associateBy { it.type }
        val s = EnemyHabitat.SPACE
        val st = EnemyHabitat.STRUCTURE
        val z = EnemyHabitat.ZENITH
        val n = EnemyHabitat.NADIR
        val a = EnemyHabitat.ARCHIVE
        // type to (habitats, STRUCTURE, ZENITH, NADIR, ARCHIVE weights)
        val expected = mapOf(
            EnemyType.DRIFTER to Habitats(setOf(s, st, a), HabitatWeights(structure = 10f, archive = 4f)),
            EnemyType.SHOOTER to Habitats(setOf(s, st, z), HabitatWeights(structure = 8f, zenith = 6f)),
            EnemyType.CHARGER to Habitats(setOf(s, st, n), HabitatWeights(structure = 6f, nadir = 5f)),
            EnemyType.INTERCEPTOR to Habitats(setOf(s), HabitatWeights.NONE),
            EnemyType.WEAVER to Habitats(setOf(s, st, z), HabitatWeights(structure = 5f, zenith = 5f)),
            EnemyType.WARDEN to Habitats(setOf(s), HabitatWeights.NONE),
            EnemyType.SPLITTER to Habitats(setOf(s, st, n), HabitatWeights(structure = 5f, nadir = 3f)),
            EnemyType.ELITE to Habitats(EnemyHabitat.entries.toSet(), HabitatWeights.NONE),
            EnemyType.ARCHITECT to Habitats(setOf(s), HabitatWeights.NONE),
            EnemyType.SWARMLING to Habitats(setOf(s, st), HabitatWeights(structure = 6f)),
            EnemyType.BOMBER to Habitats(setOf(s, st, n), HabitatWeights(structure = 6f, nadir = 5f)),
            EnemyType.LANCER to Habitats(setOf(s, st), HabitatWeights(structure = 4f)),
            EnemyType.SNIPER to Habitats(setOf(s, z, a), HabitatWeights(zenith = 3f, archive = 4f)),
            EnemyType.AEGIS to Habitats(setOf(s, st, z), HabitatWeights(structure = 3f, zenith = 3f)),
            EnemyType.MINELAYER to Habitats(setOf(s, st, n), HabitatWeights(structure = 4f, nadir = 3f)),
            EnemyType.PHANTOM to Habitats(setOf(s, st, a), HabitatWeights(structure = 4f, archive = 5f)),
            EnemyType.BROODMOTHER to Habitats(setOf(s), HabitatWeights.NONE),
            EnemyType.REFLECTOR to Habitats(setOf(s, st, a), HabitatWeights(structure = 4f, archive = 3f)),
            EnemyType.HERALD to Habitats(setOf(s), HabitatWeights.NONE),
            EnemyType.COMET to Habitats(setOf(s), HabitatWeights.NONE),
            EnemyType.PYLON to Habitats(setOf(s, st), HabitatWeights(structure = 6f)),
            EnemyType.SENTRY to Habitats(setOf(st), HabitatWeights(structure = 10f)),
            EnemyType.CRAWLER to Habitats(setOf(st), HabitatWeights(structure = 12f)),
            EnemyType.SERAPH to Habitats(setOf(z), HabitatWeights(zenith = 12f)),
            EnemyType.CHOIR to Habitats(setOf(z), HabitatWeights(zenith = 9f)),
            EnemyType.IMP to Habitats(setOf(n), HabitatWeights(nadir = 14f)),
            EnemyType.HOUND to Habitats(setOf(n), HabitatWeights(nadir = 12f)),
            EnemyType.CIPHER to Habitats(setOf(a), HabitatWeights(archive = 12f)),
            EnemyType.MIRROR to Habitats(setOf(a), HabitatWeights(archive = 8f)),
            EnemyType.WARDEN_PRIME to Habitats(setOf(s, st), HabitatWeights.NONE),
            EnemyType.BROOD_QUEEN to Habitats(setOf(s, st), HabitatWeights.NONE),
            EnemyType.THRONE to Habitats(setOf(z), HabitatWeights.NONE),
            EnemyType.PYRE_HERALD to Habitats(setOf(n), HabitatWeights.NONE),
            EnemyType.CARTOGRAPHER to Habitats(setOf(s), HabitatWeights.NONE),
            EnemyType.ANTECEDENT to Habitats(setOf(a), HabitatWeights.NONE),
        )
        assertEquals(EnemyType.entries.toSet(), expected.keys)
        expected.forEach { (type, row) ->
            val definition = roster.getValue(type)
            assertEquals(row.habitats, EnemyHabitat.entries.filter(definition::livesIn).toSet(), type.name)
            assertEquals(row.weights, definition.habitatWeights, type.name)
        }
    }

    @Test
    fun ranksEncountersAndDiscoveriesMatchTheRosterTable() {
        val roster = createContentCatalog().gameplayContent().enemies

        fun ranked(rank: EnemyRank) = roster.filter { it.rank == rank }.map { it.type }
        assertEquals(listOf(EnemyType.ARCHITECT), ranked(EnemyRank.BOSS))
        assertEquals(listOf(EnemyType.ELITE), ranked(EnemyRank.ELITE))
        assertEquals(
            listOf(EnemyType.HERALD, EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE, EnemyType.PYRE_HERALD),
            ranked(EnemyRank.MINI_BOSS),
        )
        assertEquals(listOf(EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT), ranked(EnemyRank.SECRET_BOSS))
        assertEquals(listOf(EnemyType.ARCHITECT), roster.filter { it.isBoss }.map { it.type })
        assertEquals(listOf(EnemyType.ELITE), roster.filter { it.isElite }.map { it.type })
        assertEquals(ranked(EnemyRank.MINI_BOSS), roster.filter { it.isMiniBoss }.map { it.type })
        assertEquals(ranked(EnemyRank.SECRET_BOSS), roster.filter { it.isSecretBoss }.map { it.type })
        assertEquals(roster.filter { it.rank != EnemyRank.ORDINARY }.map { it.type }, roster.filter { it.isScheduled }.map { it.type })

        val byType = roster.associateBy { it.type }
        assertSame(ARCHITECT_ENCOUNTER, byType.getValue(EnemyType.ARCHITECT).encounter)
        assertNull(byType.getValue(EnemyType.HERALD).encounter, "The Herald keeps its own schedule")
        val mini = byType.getValue(EnemyType.WARDEN_PRIME).encounter
        assertEquals(BossEncounterSpec(3, 0.03f, 0.02f, 0.5f, 2f, enrageAttackRate = 1.3f, enrageAfterSeconds = 75f), mini)
        assertEquals(mini, byType.getValue(EnemyType.BROOD_QUEEN).encounter)
        assertEquals(mini, byType.getValue(EnemyType.PYRE_HERALD).encounter)
        assertEquals(mini?.copy(shiftSeconds = 2.5f), byType.getValue(EnemyType.THRONE).encounter)
        assertEquals(
            BossEncounterSpec(3, 0.015f, 0.02f, 0.5f, 4f, enrageAttackRate = 1.35f, enrageAfterSeconds = 120f),
            byType.getValue(EnemyType.CARTOGRAPHER).encounter,
        )
        assertEquals(
            BossEncounterSpec(4, 0.015f, 0.02f, 0.5f, 5f, enrageAttackRate = 1.35f, enrageAfterSeconds = 150f),
            byType.getValue(EnemyType.ANTECEDENT).encounter,
        )
        assertEquals(WorldDiscovery.CARTOGRAPHER, byType.getValue(EnemyType.CARTOGRAPHER).revealedBy)
        assertEquals(WorldDiscovery.ANTECEDENT, byType.getValue(EnemyType.ANTECEDENT).revealedBy)
        assertTrue(roster.filterNot { it.isSecretBoss }.all { it.revealedBy == null })
    }

    @Test
    fun bootstrapRejectsAMisorderedRosterAndASecondBoss() {
        val data = defaultContentBootstrapData()

        assertRejected(data.copy(enemies = data.enemies.reversed()), "EnemyType order")
        assertRejected(data.copy(enemies = data.enemies.dropLast(1)), "EnemyType order")
        val secondBoss = data.enemies.map {
            if (it.type == EnemyType.HERALD) it.copy(rank = EnemyRank.BOSS, encounter = ARCHITECT_ENCOUNTER) else it
        }
        assertRejected(data.copy(enemies = secondBoss), "only boss")
        val unweighted = data.enemies.map { if (it.type == EnemyType.SNIPER) it.copy(weight = 0f) else it }
        assertRejected(data.copy(enemies = unweighted), "spawn weight")
    }

    @Test
    fun bootstrapRejectsHabitatsWithoutWeightAndAHabitatWithoutAnOpeningKind() {
        val data = defaultContentBootstrapData()

        // A Seraph declared to live in open space carries no weight there.
        val misplaced = data.enemies.map { if (it.type == EnemyType.SERAPH) it.copy(habitats = EnemyHabitat.SPACE.bit) else it }
        assertRejected(data.copy(enemies = misplaced), "spawn weight")
        // A weight in a habitat the kind does not name is refused as well.
        val stray = data.enemies.map {
            if (it.type == EnemyType.INTERCEPTOR) it.copy(habitatWeights = HabitatWeights(nadir = 2f)) else it
        }
        assertRejected(data.copy(enemies = stray), "spawn weight")
        // Seraph and Choir are the Zenith's only kinds available at 0 s.
        val lateZenith = data.enemies.map {
            if (it.type == EnemyType.SERAPH || it.type == EnemyType.CHOIR) it.copy(timeGateSeconds = 10f) else it
        }
        assertRejected(data.copy(enemies = lateZenith), "every habitat")
        val zenithOpensWithChoir = data.enemies.map { if (it.type == EnemyType.SERAPH) it.copy(timeGateSeconds = 10f) else it }
        createContentCatalogForTesting(data.copy(enemies = zenithOpensWithChoir))
        val ordinaryHerald = data.enemies.map { if (it.type == EnemyType.HERALD) it.copy(rank = EnemyRank.ORDINARY, weight = 1f) else it }
        assertRejected(data.copy(enemies = ordinaryHerald), "stay a mini-boss")
        val twinSecrets = data.enemies.map {
            if (it.type == EnemyType.ANTECEDENT) it.copy(revealedBy = WorldDiscovery.CARTOGRAPHER) else it
        }
        assertRejected(data.copy(enemies = twinSecrets), "distinct discoveries")
    }

    @Test
    fun definitionsRejectOutOfRangeThreatAndScheduledWeight() {
        val drifter = defaultContentBootstrapData().enemies.first()
        assertFailsWith<IllegalArgumentException> { drifter.copy(threat = 0) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(threat = 6) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(minimumRebirth = ContentBounds.MAX_REBIRTH_LEVEL + 1) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(rank = EnemyRank.ELITE) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(lateWeightFraction = 1.5f) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(habitats = 0) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(habitats = EnemyHabitat.ALL + 1) }
        assertFailsWith<IllegalArgumentException> { HabitatWeights(zenith = -1f) }
        EnemyDefinition(EnemyType.DRIFTER, "Edge", "Bound check.", threat = 5, timeGateSeconds = 0f, minimumRebirth = 10, weight = 1f)
    }

    @Test
    fun definitionsTieEncountersAndDiscoveriesToTheirRank() {
        val roster = defaultContentBootstrapData().enemies.associateBy { it.type }
        val secret = roster.getValue(EnemyType.CARTOGRAPHER)
        assertFailsWith<IllegalArgumentException> { secret.copy(encounter = null) }
        assertFailsWith<IllegalArgumentException> { secret.copy(revealedBy = null) }
        val drifter = roster.getValue(EnemyType.DRIFTER)
        assertFailsWith<IllegalArgumentException> { drifter.copy(encounter = ARCHITECT_ENCOUNTER) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(revealedBy = WorldDiscovery.CARTOGRAPHER) }
        assertFailsWith<IllegalArgumentException> { roster.getValue(EnemyType.ARCHITECT).copy(encounter = null) }
        assertFailsWith<IllegalArgumentException> {
            roster.getValue(EnemyType.WARDEN_PRIME).copy(habitatWeights = HabitatWeights(structure = 1f))
        }
        // A mini-boss may carry an encounter spec or none (the Herald keeps its own schedule).
        roster.getValue(EnemyType.HERALD).copy(encounter = ARCHITECT_ENCOUNTER.copy(enragePhases = 0, enrageMutator = null))
        assertEquals(RebirthMutator.SINGULARITY, ARCHITECT_ENCOUNTER.enrageMutator)
    }

    private data class Row(val gate: Float, val rebirth: Int, val weight: Float, val threat: Int)

    private data class Habitats(val habitats: Set<EnemyHabitat>, val weights: HabitatWeights)

    private fun assertRejected(data: ContentBootstrapData, message: String) {
        val failure = assertFailsWith<IllegalArgumentException> { createContentCatalogForTesting(data) }
        assertTrue(failure.message.orEmpty().contains(message), "Expected '$message' in '${failure.message}'")
    }
}
