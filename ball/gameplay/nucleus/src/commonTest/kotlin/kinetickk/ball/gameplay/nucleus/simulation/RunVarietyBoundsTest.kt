// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ChampionPolicy
import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.content.api.EliteAffixPolicy
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.WorldEventPolicy
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.WorldEventPlan
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.EventStrikeProjection
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The run-variety bounds at their last accepted and first rejected value (variety.md §11.6): what content accepts and
 * what a run then does at that bound.
 */
class RunVarietyBoundsTest {
    private val content = canonicalGameplayContent

    private fun withAffixes(affixes: EliteAffixPolicy): GameplayContentSnapshot =
        content.copy(variety = content.variety.copy(affixes = affixes))

    private fun arena(content: GameplayContentSnapshot = this.content, rebirth: Int = 0): MutableGameState =
        buildTestState(rebirth = rebirth, content = content).apply {
            elapsed = 400f
            clearObstaclesForTesting()
        }

    private fun MutableGameState.affixedElite(affix: EliteAffix, hp: Float = 1_000f): Enemy =
        addEnemyForTesting(coreX + 400f, coreY, hp = hp, radius = 26f, type = EnemyType.ELITE).also { it.affix = affix }

    @Test
    fun linkedAffixTethersThreeAndNeverAFourth() {
        EliteAffixPolicy(linkCount = 3)
        assertFailsWith<IllegalArgumentException> { EliteAffixPolicy(linkCount = 4) }
        for (links in 1..3) {
            val state = arena(withAffixes(content.variety.affixes.copy(linkCount = links)))
            val elite = state.affixedElite(EliteAffix.LINKED)
            // Four eligible allies in tether range, nearest first.
            val allies = (1..4).map { step -> state.addEnemyForTesting(elite.x + 40f * step, elite.y + 30f) }
            state.updateEnemies(MutableGameState.FIXED_STEP)
            assertEquals(allies.take(links).map { it.id }, allies.filter { state.linkedTo(it) == elite.id }.map { it.id }, "$links links")
        }
    }

    @Test
    fun splittingAffixSpawnsThreeFragmentsInsideTheCap() {
        EliteAffixPolicy(splitFragments = 3)
        assertFailsWith<IllegalArgumentException> { EliteAffixPolicy(splitFragments = 4) }

        /** Fragments of a Splitting Elite killed with [room] free places under the Splitter's cap ([dead] corpses too). */
        fun fragments(room: Int, dead: Int = 0, splitFragments: Int = 3): Int {
            val state = arena(withAffixes(content.variety.affixes.copy(splitFragments = splitFragments)))
            val cap = state.rebirthProfile.enemyCap(90)
            repeat(cap - room) { index -> state.addEnemyForTesting(state.coreX - 600f, state.coreY + index) }
            repeat(dead) { index -> state.addEnemyForTesting(state.coreX - 800f, state.coreY + index).dead = true }
            val elite = state.affixedElite(EliteAffix.SPLITTING, hp = 1f)
            state.dealHit(elite, 10f, HitSource.WEAPON, canCrit = false)
            state.sweepKills()
            return state.enemies.count { it.id > elite.id }
        }
        assertEquals(3, fragments(room = 4))
        assertEquals(3, fragments(room = 3))
        assertEquals(2, fragments(room = 2))
        assertEquals(0, fragments(room = 0))
        assertEquals(1, fragments(room = 4, splitFragments = 1))
        // The hard cap counts every entry: with one place left only one fragment fits, and none at the cap.
        val hardCap = content.rebirth.maxActiveEnemies
        val cap = arena().rebirthProfile.enemyCap(90)
        assertEquals(1, fragments(room = 4, dead = hardCap - (cap - 4) - 2))
        assertEquals(0, fragments(room = 4, dead = hardCap - (cap - 4) - 1))
    }

    @Test
    fun championChanceAboveTenPercentIsRejected() {
        val bound = ChampionPolicy(baseChance = 0.1f, chancePerTier = 0f)
        assertFailsWith<IllegalArgumentException> { ChampionPolicy(baseChance = 0.1f.nextUp(), chancePerTier = 0f) }
        assertFailsWith<IllegalArgumentException> { ChampionPolicy(baseChance = 0.04f, chancePerTier = 0.0061f) }
        // A run at the bound sees one champion in ten eligible spawns at the top tier, and no more.
        val state = arena(content.copy(variety = content.variety.copy(champions = bound)), rebirth = 10)
        var champions = 0
        repeat(SAMPLE) { if (state.spawnedEnemy(EnemyType.SHOOTER, 0f, 0f).champion) champions++ }
        assertEquals(0.1f, state.championChance())
        assertEquals(0.1f, champions / SAMPLE.toFloat(), 0.01f)
    }

    @Test
    fun worldEventPlansAcceptThreeAndRejectFour() {
        val three = WorldEventPolicy(minPerRun = ContentBounds.MAX_WORLD_EVENTS_PER_RUN, maxPerRun = ContentBounds.MAX_WORLD_EVENTS_PER_RUN)
        assertFailsWith<IllegalArgumentException> { WorldEventPolicy(maxPerRun = ContentBounds.MAX_WORLD_EVENTS_PER_RUN + 1) }
        assertFailsWith<IllegalArgumentException> {
            WorldEventPolicy(minPerRun = ContentBounds.MAX_WORLD_EVENTS_PER_RUN + 1, maxPerRun = ContentBounds.MAX_WORLD_EVENTS_PER_RUN + 1)
        }
        // At the bound every run plans exactly three events, one after another.
        for (seed in 1..200) {
            val plans = planWorldEvents(seed, three)
            assertEquals(ContentBounds.MAX_WORLD_EVENTS_PER_RUN, plans.size, "Seed $seed")
            for (index in 1 until plans.size) assertTrue(plans[index - 1].endsAt < plans[index].telegraphAt)
        }
        // The shipped policy plans the minimum or one more, never beyond its maximum.
        val shipped = content.variety.worldEvents
        assertEquals(setOf(shipped.minPerRun, shipped.maxPerRun), (1..200).map { planWorldEvents(it, shipped).size }.toSet())
    }

    @Test
    fun eventStrikesHoldFourAndSkipTheFifth() {
        /** The strikes armed after one meteor pulse that finds [armed] strikes already waiting. */
        fun strikesAfterAPulse(armed: Int): Int {
            val state = arena()
            val telegraphAt = 10f
            val startsAt = telegraphAt + content.variety.worldEvents.telegraphSeconds
            state.worldEventPlans = immutableListOf(WorldEventPlan(WorldEventKind.METEOR_SHOWER, telegraphAt, startsAt, startsAt + 30f))
            state.elapsed = startsAt + 5f
            state.updateWorldEvents(MutableGameState.FIXED_STEP)
            val waiting = List(armed) { index -> EventStrikeProjection(100f * index, 0f, 95f, state.elapsed, state.elapsed + 1f) }
            state.worldEvent = checkNotNull(state.worldEvent).copy(strikes = waiting.toImmutableList())
            state.worldEventNextPulseAt = state.elapsed
            state.updateWorldEvents(MutableGameState.FIXED_STEP)
            return checkNotNull(state.worldEvent).strikes.size
        }
        assertEquals(ContentBounds.MAX_EVENT_STRIKES, strikesAfterAPulse(ContentBounds.MAX_EVENT_STRIKES - 1), "The fourth is armed")
        assertEquals(ContentBounds.MAX_EVENT_STRIKES, strikesAfterAPulse(ContentBounds.MAX_EVENT_STRIKES), "The fifth is skipped")
    }

    private companion object {
        const val SAMPLE = 10_000
    }
}
