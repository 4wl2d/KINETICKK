// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The habitat director: open space keeps the released weights; every other habitat weighs only its own roster. */
class HabitatDirectorTest {
    @Test
    fun openSpaceWeightsEqualTheReleasedFormulaForTheOldRoster() {
        val masks = listOf(
            0L to 0L,
            bits(EnemyType.DRIFTER, EnemyType.SHOOTER) to bits(EnemyType.SNIPER),
            0L to bits(EnemyType.LANCER, EnemyType.MINELAYER, EnemyType.REFLECTOR),
        )
        var positive = 0
        for (tier in TIERS) {
            val state = directorState(tier)
            for (seconds in SECONDS) {
                state.elapsed = seconds
                for ((recent, previous) in masks) {
                    state.recentEventTypes = recent
                    state.previousEventTypes = previous
                    for (row in RELEASED_ROSTER) {
                        val definition = state.content.enemy(row.type)
                        for (weakOnly in BOOLEANS) for (recency in BOOLEANS) {
                            val released = state.releasedWeight(row, weakOnly, recency)
                            val label = "${row.type} tier=$tier t=$seconds weak=$weakOnly recency=$recency masks=$recent/$previous"
                            val space = state.typeWeight(definition, weakOnly, recency, habitat = EnemyHabitat.SPACE, personality = false)
                            assertEquals(released.toRawBits(), space.toRawBits(), label)
                            val ambient = state.typeWeight(definition, weakOnly, recency, personality = false)
                            assertEquals(released.toRawBits(), ambient.toRawBits(), "$label (realspace ambient)")
                            if (released > 0f) positive++
                        }
                    }
                }
            }
        }
        assertTrue(positive > 1_000, "the comparison covers open kinds, not only zeros ($positive)")

        // Hunter Protocol (tier 3 and up) still opens the Sniper 90 s early.
        val tierZero = directorState(tier = 0).apply { elapsed = 270f }
        val tierFour = directorState(tier = 4).apply { elapsed = 270f }
        val sniper = canonicalGameplayContent.enemy(EnemyType.SNIPER)
        assertEquals(0f, tierZero.typeWeight(sniper, weakOnly = false, recency = false, personality = false))
        assertTrue(tierFour.typeWeight(sniper, weakOnly = false, recency = false, personality = false) > 0f)

        // Of the sixteen newcomers only the Comet and the Pylon may ever take open-space weight.
        for (tier in TIERS) {
            val state = directorState(tier)
            for (seconds in SECONDS) {
                state.elapsed = seconds
                state.content.enemies.filter { it.type.ordinal > EnemyType.HERALD.ordinal }
                    .filter { it.type != EnemyType.COMET && it.type != EnemyType.PYLON }
                    .forEach { definition ->
                        val weight = state.typeWeight(definition, weakOnly = false, recency = false, habitat = EnemyHabitat.SPACE)
                        assertEquals(0f, weight, "${definition.type} in open space, tier=$tier t=$seconds")
                    }
            }
        }
    }

    @Test
    fun dimensionsAndInteriorsWeighOnlyTheirOwnRosters() {
        for ((habitat, roster) in HABITAT_ROSTERS) {
            for (tier in TIERS) {
                val state = directorState(tier)
                for (seconds in SECONDS) {
                    state.elapsed = seconds
                    val weighted = state.weightedKinds(habitat)
                    assertTrue(roster.containsAll(weighted), "$habitat tier=$tier t=$seconds weighs ${weighted - roster}")
                    assertTrue(weighted.isNotEmpty(), "$habitat opens with an ordinary kind, tier=$tier t=$seconds")
                    weighted.forEach { type ->
                        assertTrue(state.content.enemy(type).livesIn(habitat), "$type lives in $habitat")
                        assertFalse(state.content.enemy(type).isScheduled, "$type is ordinary")
                    }
                }
            }
            // Once every gate is open the whole roster is in play.
            val late = directorState(tier = 10).apply { elapsed = 700f }
            assertEquals(roster.toSet(), late.weightedKinds(habitat), "$habitat at tier 10, 700 s")
        }
    }

    @Test
    fun theAmbientDirectorPicksOnlyTheRosterOfTheLayerItStandsIn() {
        for (layer in WorldLayer.entries) {
            val state = directorState(tier = 4).apply {
                elapsed = 300f
                worldLayer = layer
            }
            assertEquals(layer.habitat, state.ambientHabitat())
            val roster = if (layer == WorldLayer.BASE) SPACE_ROSTER else HABITAT_ROSTERS.getValue(layer.habitat)
            val picks = List(600) { state.pickEnemyType() }.toSet()
            assertTrue(roster.containsAll(picks), "$layer picked ${picks - roster}")
            assertTrue(picks.size >= 2, "$layer picked only $picks")
            assertTrue(List(200) { state.pickEnemyType(weakOnly = true) }.all { it in roster }, "$layer weak picks")

            // Swarms need the Swarmling: realspace still rolls them, no dimension does.
            var swarms = 0
            repeat(400) {
                state.directorPhase = SpawnDirectorPhase.SURGE.ordinal
                state.pendingEventKind = -1
                state.planSpawnEvent()
                if (state.pendingEventKind == SpawnEventKind.SWARM.ordinal) swarms++
                val planned = state.pendingEventType
                if (planned >= 0) assertTrue(EnemyType.entries[planned] in roster, "$layer planned ${EnemyType.entries[planned]}")
            }
            if (layer == WorldLayer.BASE) assertTrue(swarms > 0, "realspace surges roll swarms") else assertEquals(0, swarms, "$layer")
        }
    }

    @Test
    fun habitatPicksFollowTheirWeightsDrawNothingAndIgnoreTheEventMemory() {
        val state = directorState(tier = 10).apply { elapsed = 700f }
        val spawn = state.spawnRandom.snapshot()
        val gameplay = state.gameplayRandom.snapshot()
        val reward = state.rewardRandom.snapshot()
        for (habitat in EnemyHabitat.entries) {
            val weights = state.content.enemies
                .associate { it.type to state.typeWeight(it, weakOnly = false, recency = false, habitat = habitat, personality = false) }
                .filterValues { it > 0f }
            val total = weights.values.sum()
            val picks = List(ROLLS) { index -> state.pickHabitatType(habitat, (index + 0.5f) / ROLLS) }
            assertEquals(picks, List(ROLLS) { index -> state.pickHabitatType(habitat, (index + 0.5f) / ROLLS) }, "$habitat per roll")
            val counts = picks.groupingBy { it }.eachCount()
            assertEquals(weights.keys, counts.keys, "$habitat picks every weighted kind and nothing else")
            weights.forEach { (type, weight) ->
                assertEquals(weight / total, counts.getValue(type).toFloat() / ROLLS, 2f / ROLLS, "$habitat share of $type")
            }
            // Out-of-range rolls clamp to the ends of the roll range.
            assertEquals(state.pickHabitatType(habitat, 0f), state.pickHabitatType(habitat, -3f), "$habitat below 0")
            assertEquals(state.pickHabitatType(habitat, 0.9999999f), state.pickHabitatType(habitat, 1f), "$habitat at 1")
            assertEquals(state.pickHabitatType(habitat, 0.9999999f), state.pickHabitatType(habitat, 7f), "$habitat above 1")

            // The halving of recently used kinds belongs to the ambient director; an owner's pick ignores it.
            state.recentEventTypes = -1L
            state.previousEventTypes = -1L
            assertEquals(picks, List(ROLLS) { index -> state.pickHabitatType(habitat, (index + 0.5f) / ROLLS) }, "$habitat recency")
            state.recentEventTypes = 0L
            state.previousEventTypes = 0L
        }
        assertEquals(spawn, state.spawnRandom.snapshot())
        assertEquals(gameplay, state.gameplayRandom.snapshot())
        assertEquals(reward, state.rewardRandom.snapshot())
    }

    @Test
    fun aWeakHabitatPickWidensBeforeADrifterAndAThreatLeadOpensLaterKinds() {
        val state = directorState(tier = 0).apply { elapsed = 0f }
        val rolls = List(40) { (it + 0.5f) / 40 }
        // At 0 s no weak Zenith kind is open (its Shooter opens at 38 s): a weak pick takes the open Zenith kinds.
        assertEquals(setOf(EnemyType.SERAPH, EnemyType.CHOIR), rolls.map { state.pickHabitatType(EnemyHabitat.ZENITH, it, weakOnly = true) }.toSet())
        state.elapsed = 400f
        assertEquals(setOf(EnemyType.SHOOTER, EnemyType.WEAVER), rolls.map { state.pickHabitatType(EnemyHabitat.ZENITH, it, weakOnly = true) }.toSet())

        // A pick for a later threat time (a garrison's gate lead) opens the kinds of that time.
        state.elapsed = 0f
        assertEquals(setOf(EnemyType.IMP, EnemyType.HOUND), rolls.map { state.pickHabitatType(EnemyHabitat.NADIR, it) }.toSet())
        assertEquals(
            setOf(EnemyType.IMP, EnemyType.HOUND, EnemyType.CHARGER, EnemyType.SPLITTER, EnemyType.BOMBER),
            rolls.map { state.pickHabitatType(EnemyHabitat.NADIR, it, threatSeconds = 400f) }.toSet(),
        )

        // A habitat with nothing open at all still yields an enemy: a Drifter.
        val emptyZenith = canonicalGameplayContent.copy(
            enemies = canonicalGameplayContent.enemies.map { it.copy(habitatWeights = it.habitatWeights.copy(zenith = 0f)) }.toImmutableList(),
        )
        val empty = directorState(tier = 0, content = emptyZenith).apply { elapsed = 400f }
        assertEquals(setOf(EnemyType.DRIFTER), rolls.map { empty.pickHabitatType(EnemyHabitat.ZENITH, it) }.toSet())
    }

    @Test
    fun everyKindMarksItsOwnBitInTheEventMemory() {
        val masks = EnemyType.entries.map { type ->
            val state = directorState(tier = 0)
            state.pendingEventKind = SpawnEventKind.SINGLE.ordinal
            state.pendingEventSize = 1
            state.pendingEventType = type.ordinal
            state.spawnBudget = MAX_SPAWN_BUDGET
            state.spawnClock = 0f
            state.ambushReadyAt = Float.MAX_VALUE
            state.spawnWave(MutableGameState.FIXED_STEP)
            assertEquals(1, state.spawnEventCount, "$type fired")
            type to state.recentEventTypes
        }
        masks.forEach { (type, mask) -> assertNotEquals(0L, mask, "$type marks the event memory") }
        for (first in masks.indices) for (second in first + 1 until masks.size) {
            val (a, maskA) = masks[first]
            val (b, maskB) = masks[second]
            assertEquals(0L, maskA and maskB, "$a and $b share an event bit")
        }

        // A kind's mark halves that kind alone, in whichever habitat it is weighed.
        val state = directorState(tier = 10).apply { elapsed = 700f }
        for (habitat in EnemyHabitat.entries) {
            val weighted = state.weightedKinds(habitat)
            for ((marked, mask) in masks.filter { it.first in weighted }) {
                for ((recent, previous) in listOf(mask to 0L, 0L to mask)) {
                    state.recentEventTypes = recent
                    state.previousEventTypes = previous
                    weighted.forEach { type ->
                        val definition = state.content.enemy(type)
                        val fresh = state.typeWeight(definition, weakOnly = false, recency = false, habitat = habitat, personality = false)
                        val remembered = state.typeWeight(definition, weakOnly = false, recency = true, habitat = habitat, personality = false)
                        assertEquals(if (type == marked) fresh * 0.5f else fresh, remembered, "$habitat $type after $marked")
                    }
                }
            }
        }
    }

    @Test
    fun cometPylonAndSentryNeverEscortEvenWhereTheDirectorPicksThem() {
        // Open the Comet's and the Pylon's gates and give the Sentry a test-only open-space weight.
        val open = canonicalGameplayContent.copy(
            enemies = canonicalGameplayContent.enemies.map { definition ->
                when (definition.type) {
                    EnemyType.COMET, EnemyType.PYLON -> definition.copy(timeGateSeconds = 0f)
                    EnemyType.SENTRY -> definition.copy(weight = 10f)
                    else -> definition
                }
            }.toImmutableList(),
        )
        val state = directorState(tier = 0, content = open).apply { elapsed = 700f }
        val kin = mapOf(
            EnemyType.COMET to setOf(EnemyType.WEAVER, EnemyType.SNIPER),
            EnemyType.PYLON to setOf(EnemyType.SHOOTER, EnemyType.LANCER),
            EnemyType.SENTRY to setOf(EnemyType.CHARGER, EnemyType.INTERCEPTOR, EnemyType.BOMBER),
        )
        kin.forEach { (type, escorts) ->
            assertTrue(state.typeWeight(state.content.enemy(type), weakOnly = false, recency = false) > 0f, "$type is pickable here")
            val aspect = type.profile.aspect
            val called = List(12) { index -> state.aspectEscortType(aspect, index) }.toSet()
            assertEquals(escorts, called, "${StatusElement.entries[aspect]} escorts")
        }
    }

    private class ReleasedKind(
        val type: EnemyType,
        val threat: Int,
        val gate: Float,
        val minimumRebirth: Int,
        val weight: Float,
        val scheduled: Boolean = false,
        val lateFraction: Float = 1f,
        val lateAt: Float = 0f,
    )

    /** The released (0.5.0) ordinary weight of [row]: the formula and the roster numbers of that release. */
    private fun MutableGameState.releasedWeight(row: ReleasedKind, weakOnly: Boolean, recency: Boolean): Float {
        if (row.scheduled || row.minimumRebirth > rebirthLevel) return 0f
        if (weakOnly && row.threat > 2) return 0f
        val hunter = rebirthProfile.has(RebirthMutator.HUNTER_PROTOCOL) &&
            (row.type == EnemyType.SNIPER || row.type == EnemyType.LANCER)
        val gate = max(0f, row.gate - if (hunter) 90f else 0f)
        val threat = threatElapsed
        if (threat < gate) return 0f
        val ramp = ((threat - gate) / 60f).coerceIn(0.08f, 1f)
        val base = if (row.lateAt <= 0f || row.lateFraction >= 1f) {
            row.weight
        } else {
            row.weight * (1f - (1f - row.lateFraction) * (threat / row.lateAt).coerceIn(0f, 1f))
        }
        var weight = base * ramp
        if (recency && ((recentEventTypes or previousEventTypes) and (1L shl row.type.ordinal)) != 0L) weight *= 0.5f
        return weight
    }

    private fun MutableGameState.weightedKinds(habitat: EnemyHabitat): Set<EnemyType> =
        content.enemies.filter { typeWeight(it, weakOnly = false, recency = false, habitat = habitat, personality = false) > 0f }
            .map { it.type }.toSet()

    private fun bits(vararg types: EnemyType): Long = types.fold(0L) { mask, type -> mask or (1L shl type.ordinal) }

    private fun directorState(tier: Int, content: GameplayContentSnapshot = canonicalGameplayContent): MutableGameState =
        MutableGameState(content, seed = 59, initialMatter = 0, initialRebirthLevel = tier).apply {
            resize(1_280f, 720f)
            startRun()
            enemies.clear()
            quietArenaForTesting()
        }

    private companion object {
        val TIERS = listOf(0, 4, 10)
        val SECONDS = listOf(0f, 38f, 120f, 270f, 360f, 450f, 700f)
        val BOOLEANS = listOf(false, true)
        const val ROLLS = 2_000

        /** The 0.5.0 roster: threat, gate, Rebirth tier, open-space weight and late decay of the nineteen old kinds. */
        val RELEASED_ROSTER = listOf(
            ReleasedKind(EnemyType.DRIFTER, 1, 0f, 0, 22f, lateFraction = 0.4f, lateAt = 360f),
            ReleasedKind(EnemyType.SHOOTER, 2, 38f, 0, 17f),
            ReleasedKind(EnemyType.CHARGER, 2, 150f, 0, 14f),
            ReleasedKind(EnemyType.INTERCEPTOR, 2, 90f, 0, 14f),
            ReleasedKind(EnemyType.WEAVER, 2, 150f, 0, 12f),
            ReleasedKind(EnemyType.WARDEN, 3, 240f, 0, 9f),
            ReleasedKind(EnemyType.SPLITTER, 3, 240f, 0, 12f),
            ReleasedKind(EnemyType.ELITE, 4, 38f, 0, 0f, scheduled = true),
            ReleasedKind(EnemyType.ARCHITECT, 5, 720f, 0, 0f, scheduled = true),
            ReleasedKind(EnemyType.SWARMLING, 1, 75f, 0, 10f),
            ReleasedKind(EnemyType.BOMBER, 3, 200f, 0, 8f),
            ReleasedKind(EnemyType.LANCER, 3, 240f, 0, 7f),
            ReleasedKind(EnemyType.SNIPER, 3, 330f, 0, 6f),
            ReleasedKind(EnemyType.AEGIS, 3, 390f, 0, 4f),
            ReleasedKind(EnemyType.MINELAYER, 3, 450f, 0, 5f),
            ReleasedKind(EnemyType.PHANTOM, 4, 300f, 2, 6f),
            ReleasedKind(EnemyType.BROODMOTHER, 4, 420f, 3, 3f),
            ReleasedKind(EnemyType.REFLECTOR, 4, 360f, 4, 5f),
            ReleasedKind(EnemyType.HERALD, 5, 360f, 2, 0f, scheduled = true),
        )

        val SPACE_ROSTER = setOf(
            EnemyType.DRIFTER, EnemyType.SHOOTER, EnemyType.CHARGER, EnemyType.INTERCEPTOR, EnemyType.WEAVER,
            EnemyType.WARDEN, EnemyType.SPLITTER, EnemyType.SWARMLING, EnemyType.BOMBER, EnemyType.LANCER,
            EnemyType.SNIPER, EnemyType.AEGIS, EnemyType.MINELAYER, EnemyType.PHANTOM, EnemyType.BROODMOTHER,
            EnemyType.REFLECTOR, EnemyType.COMET, EnemyType.PYLON,
        )

        /** The habitat table of the 0.6.0 roster, outside open space. */
        val HABITAT_ROSTERS = mapOf(
            EnemyHabitat.STRUCTURE to setOf(
                EnemyType.DRIFTER, EnemyType.SHOOTER, EnemyType.CHARGER, EnemyType.WEAVER, EnemyType.SPLITTER,
                EnemyType.SWARMLING, EnemyType.BOMBER, EnemyType.LANCER, EnemyType.AEGIS, EnemyType.MINELAYER,
                EnemyType.PHANTOM, EnemyType.REFLECTOR, EnemyType.PYLON, EnemyType.SENTRY, EnemyType.CRAWLER,
            ),
            EnemyHabitat.ZENITH to setOf(
                EnemyType.SHOOTER, EnemyType.WEAVER, EnemyType.SNIPER, EnemyType.AEGIS, EnemyType.SERAPH, EnemyType.CHOIR,
            ),
            EnemyHabitat.NADIR to setOf(
                EnemyType.CHARGER, EnemyType.SPLITTER, EnemyType.BOMBER, EnemyType.MINELAYER, EnemyType.IMP, EnemyType.HOUND,
            ),
            EnemyHabitat.ARCHIVE to setOf(
                EnemyType.DRIFTER, EnemyType.SNIPER, EnemyType.PHANTOM, EnemyType.REFLECTOR, EnemyType.CIPHER, EnemyType.MIRROR,
            ),
        )
    }
}
