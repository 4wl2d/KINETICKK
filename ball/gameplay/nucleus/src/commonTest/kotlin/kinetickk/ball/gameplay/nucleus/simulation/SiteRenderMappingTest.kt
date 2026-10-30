// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.model.AmbientSiteWindow
import kinetickk.ball.gameplay.nucleus.model.SitePoint
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.TrialClock
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.ball.gameplay.nucleus.render.TrialSource
import kinetickk.ball.gameplay.nucleus.render.TrialValue
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The sites and the trial panel as Gameplay publishes them: one panel per engagement, every family's facts. */
class SiteRenderMappingTest {
    private fun game() = MutableGameState(canonicalGameplayContent, seed = 4_242).apply {
        startRun()
        enemies.clear()
        elapsed = 100f
    }

    private fun MutableGameState.running(site: SiteState): TrialProjection {
        sites = listOf(site)
        return assertNotNull(toRenderModel().trial, "${site.kind} runs a trial")
    }

    private fun active(kind: SiteKind, remaining: Float = 14f) =
        SiteState(id = 6L, kind = kind, x = 300f, y = -200f, expiresAt = 120f, active = true, remaining = remaining)

    @Test
    fun anomalyTrialsKeepTheirPanelFacts() {
        val game = game()
        val policy = game.content.sites
        val circuit = game.running(active(SiteKind.RESONANT_CIRCUIT, remaining = 17.5f).copy(step = 1))
        assertEquals(SiteFamily.ANOMALY, circuit.family)
        assertEquals(policy.definition(SiteKind.RESONANT_CIRCUIT).name, circuit.title)
        assertEquals(policy.definition(SiteKind.RESONANT_CIRCUIT).instruction, circuit.rules)
        assertEquals(TrialClock.COUNTDOWN, circuit.clock)
        assertEquals(17.5f, circuit.seconds)
        assertEquals(3, circuit.segments)
        assertEquals(1f / 3f, circuit.progress)
        assertEquals(TrialValue.COUNT to (1f to 3f), circuit.value to (circuit.done to circuit.required))
        assertEquals(TrialReward.WEAPON, circuit.reward)
        assertEquals(TrialSource.SITE, circuit.source)

        val vault = game.running(active(SiteKind.SEALED_ANOMALY).copy(count = 2, foeIds = immutableListOf(7, 8, 9)))
        assertEquals(3, vault.segments)
        assertEquals(2f / 3f, vault.progress)
        assertEquals(TrialValue.COUNT to (2f to 3f), vault.value to (vault.done to vault.required))
        assertEquals(TrialReward.RELIC, vault.reward)

        val orbit = game.running(active(SiteKind.COLLAPSING_ORBIT).copy(meter = 5.2f))
        assertEquals(8, orbit.segments)
        assertEquals(5.2f / policy.windows.orbitRequiredSeconds, orbit.progress)
        assertEquals(TrialValue.SECONDS to (5.2f to 8f), orbit.value to (orbit.done to orbit.required))
        assertEquals(TrialReward.ITEM_AND_REPAIR, orbit.reward)
        // The world's orbit arc reads the same progress as the panel.
        assertEquals(orbit.progress, game.toRenderModel().sites.single().progress)
    }

    @Test
    fun aRaceCountsItsGatesAndNamesTheBestMedalItsClockStillAllows() {
        val game = game()
        val race = active(SiteKind.GATE_RACE, remaining = 9f).copy(
            step = 2, par = 5f, limit = 12f,
            course = List(6) { SitePoint(200f * it, -100f * it) }.toImmutableList(),
        )
        // Par 5 s: gold to 5 s, silver to 6.5 s, bronze to 8.75 s (the shipped 1.0 / 1.3 / 1.75 factors).
        val medals = listOf(4f, 5f, 6.4f, 8.7f, 8.76f, 11f).map { clock -> game.running(race.copy(clock = clock)).medal }
        assertEquals(listOf(3, 3, 2, 1, 0, 0), medals)
        val trial = game.running(race.copy(clock = 6.3f))
        assertEquals(SiteFamily.RACE, trial.family)
        assertEquals(TrialClock.ELAPSED_TENTHS to 6.3f, trial.clock to trial.seconds)
        assertEquals(6, trial.segments)
        assertEquals(TrialValue.COUNT to (2f to 6f), trial.value to (trial.done to trial.required))
        assertEquals(2f / 6f, trial.progress)
        assertEquals(TrialReward.MEDAL, trial.reward)
        val projected = game.toRenderModel().sites.single()
        assertEquals(2, projected.medal)
        assertEquals(6.3f, projected.clock)
        assertEquals(9f, projected.remaining, "a race counts down its limit")
        assertEquals(race.course.map { it.dx to it.dy }, projected.course.map { it.dx to it.dy })
    }

    @Test
    fun challengesShowTheirRuleSecondsOrCountsAndTheirRings() {
        val game = game()
        val challenges = game.content.sites.challenges
        val brakeless = game.running(active(SiteKind.BRAKELESS_TRIAL).copy(meter = 6f, clock = 10f))
        assertEquals(SiteFamily.CHALLENGE, brakeless.family)
        assertEquals(TrialValue.SECONDS to (6f to challenges.brakelessRequired), brakeless.value to (brakeless.done to brakeless.required))
        assertEquals(ceil(challenges.brakelessRequired).toInt(), brakeless.segments, "one segment per required second")
        assertEquals(6f / challenges.brakelessRequired, brakeless.progress)
        assertEquals(TrialReward.ITEM_AND_REPAIR, brakeless.reward)
        assertEquals(15, brakeless.rewardMatter)
        // The ring shrinks with the clock: half way through its 20 s, half way from 900 to 450.
        assertEquals(675f, game.toRenderModel().sites.single().radius)

        val untouched = game.running(active(SiteKind.UNTOUCHED_TRIAL).copy(meter = 3f, aimX = 420f, aimY = -260f))
        assertEquals(3f / challenges.untouchedRequired, untouched.progress)
        assertEquals(TrialReward.RELIC, untouched.reward)
        assertEquals(15, untouched.rewardMatter)
        val aimed = game.toRenderModel().sites.single()
        assertEquals(120f to -60f, aimed.aimDx to aimed.aimDy, "the volley aims from the centre")
        assertEquals(challenges.untouchedRadius, aimed.radius)

        val velocity = game.running(active(SiteKind.VELOCITY_TRIAL).copy(meter = 4.5f))
        assertEquals(6, velocity.segments)
        assertEquals(TrialValue.SECONDS to (4.5f to 6f), velocity.value to (velocity.done to velocity.required))
        assertEquals(challenges.velocityZoneRadius, game.toRenderModel().sites.single().radius)
        assertEquals(15, velocity.rewardMatter)

        val ram = game.running(active(SiteKind.RAM_TRIAL).copy(count = 1, foeIds = (20 until 28).toList().toImmutableList()))
        assertEquals(challenges.ramRequired, ram.segments, "one segment per required Dash kill")
        assertEquals(TrialValue.COUNT to (1f to challenges.ramRequired.toFloat()), ram.value to (ram.done to ram.required))
        assertEquals(TrialReward.WEAPON, ram.reward)
        assertEquals(15, ram.rewardMatter)

        val slalom = game.running(active(SiteKind.SLALOM_TRIAL).copy(step = 4, sides = 0b101010))
        assertEquals(6, slalom.segments)
        assertEquals(TrialValue.COUNT to (4f to 6f), slalom.value to (slalom.done to slalom.required))
        assertEquals(0b101010, game.toRenderModel().sites.single().sides)
        assertEquals(15, slalom.rewardMatter)
        // Only the untouched trial aims; an offer has no ring.
        game.sites = listOf(active(SiteKind.UNTOUCHED_TRIAL).copy(active = false, aimX = 1f, aimY = 1f))
        val offered = game.toRenderModel().sites.single()
        assertEquals(0f to 0f, offered.radius to offered.aimDx)
    }

    @Test
    fun duelsShowTheirFoesIntegrityWithoutAClockOrValue() {
        val game = game()
        val boss = game.spawnedEnemy(EnemyType.WARDEN, 600f, -200f).also { game.enemies += it }
        boss.hp = boss.maxHp * 0.7f
        val bounty = game.running(active(SiteKind.BOUNTY_ARENA, remaining = 25f).copy(foeIds = immutableListOf(boss.id), clock = 31f))
        assertEquals(SiteFamily.BOUNTY, bounty.family)
        assertEquals(TrialClock.NONE to 0f, bounty.clock to bounty.seconds)
        assertEquals(TrialValue.NONE, bounty.value)
        assertEquals(3, bounty.segments)
        assertEquals(0.3f, bounty.progress, 1e-5f)
        assertEquals(TrialReward.KEY_AND_RELIC, bounty.reward)
        val projected = game.toRenderModel().sites.single()
        assertEquals(0f, projected.remaining, "an untimed duel shows no countdown")
        assertEquals(game.content.sites.bounty.arenaRadius, projected.radius)
        // The kill hook's count completes it on the next step; the panel is full meanwhile.
        assertEquals(1f, game.running(game.sites.single().copy(count = 1)).progress)

        game.sites = emptyList()
        game.secretSigil = SiteState(id = 9_000L, kind = SiteKind.FAR_SIGIL, x = 0f, y = 900f, expiresAt = 630f, active = true,
            foeIds = immutableListOf(boss.id))
        val sigil = assertNotNull(game.toRenderModel().trial)
        assertEquals(SiteFamily.SECRET to SiteKind.FAR_SIGIL, sigil.family to sigil.kind)
        assertEquals(TrialReward.UNKNOWN, sigil.reward)
        assertEquals(0.3f, sigil.progress, 1e-5f)
    }

    @Test
    fun offersRunNoTrialAndAnEqualTrialKeepsItsInstance() {
        val game = game()
        game.sites = listOf(active(SiteKind.SEALED_ANOMALY).copy(active = false), active(SiteKind.GATE_RACE).copy(id = 7L, active = false))
        assertNull(game.toRenderModel().trial, "an offer is no trial")
        game.secretSigil = SiteState(id = 9_000L, kind = SiteKind.FAR_SIGIL, x = 0f, y = 900f, expiresAt = 630f)
        assertNull(game.toRenderModel().trial, "an untouched sigil is no trial")

        game.sites = listOf(active(SiteKind.COLLAPSING_ORBIT).copy(meter = 2f))
        val first = game.toRenderModel()
        val same = game.toRenderModel(reusableCollections = first)
        assertSame(first.trial, same.trial)
        game.sites = listOf(game.sites.single().copy(meter = 2.5f))
        val moved = game.toRenderModel(reusableCollections = first)
        assertNotSame(first.trial, moved.trial)
        assertEquals(2.5f, moved.trial!!.done)
    }

    @Test
    fun theSigilFollowsTheWindowSitesAndAnUnmovedCourseKeepsItsList() {
        val game = game()
        val course = List(7) { SitePoint(150f * it, 90f * it) }.toImmutableList()
        game.sites = listOf(active(SiteKind.GATE_RACE).copy(par = 6f, course = course))
        game.secretSigil = SiteState(id = 9_000L, kind = SiteKind.FAR_SIGIL, x = -40f, y = 1_100f, expiresAt = 630f)
        val first = game.toRenderModel()
        assertEquals(listOf(SiteKind.GATE_RACE, SiteKind.FAR_SIGIL), first.sites.map { it.kind })
        assertEquals(game.content.sites.definition(SiteKind.FAR_SIGIL).activationRadius, first.sites.last().activationRadius)
        // The race's clock runs: a fresh projection, the same course list.
        game.sites = listOf(game.sites.single().copy(clock = 1.5f))
        val next = game.toRenderModel(reusableCollections = first)
        assertNotSame(first.sites, next.sites)
        assertSame(first.sites.first().course, next.sites.first().course)
        assertEquals(1.5f, next.sites.first().clock)
        // A course of other marks is projected anew.
        game.sites = listOf(game.sites.single().copy(course = course.map { SitePoint(it.dx + 1f, it.dy) }.toImmutableList()))
        assertNotSame(first.sites.first().course, game.toRenderModel(reusableCollections = next).sites.first().course)
    }

    @Test
    fun aDuelsProgressFollowsItsFoeEvenWhenTheSitesAreShared() {
        val game = game()
        val boss = game.spawnedEnemy(EnemyType.WARDEN, 600f, -200f).also { game.enemies += it }
        game.sites = listOf(active(SiteKind.BOUNTY_ARENA).copy(foeIds = immutableListOf(boss.id)))
        val before = game.toRenderModel()
        assertEquals(0f, before.sites.single().progress)
        // A sibling with the same sites list and clock but a wounded boss: the identity shortcut must not keep the
        // previous duel progress.
        val sibling = game.copyForReduction()
        sibling.enemies.single { it.id == boss.id }.hp = boss.maxHp * 0.4f
        val after: GameplayRenderModel = sibling.toRenderModel(reusableCollections = before, identitySource = game)
        assertEquals(0.6f, after.sites.single().progress, 1e-5f)
        assertEquals(0.6f, after.trial!!.progress, 1e-5f)
        assertEquals(sibling.toRenderModel().sites, after.sites)
    }

    @Test
    fun ambientSitesProjectOnlyOnceSettledWithTheirRevealAndKeepTheirList() {
        val game = game()
        val policy = game.content.sites
        fun ambient(kind: SiteKind, cellX: Int, x: Float, settled: Boolean) =
            SiteState(ambientSiteId(kind, cellX, 0), kind, x, 400f, Float.POSITIVE_INFINITY, settled = settled)
        val cache = ambient(SiteKind.DRIFT_CACHE, 1, 900f, settled = true)
        val quiet = ambient(SiteKind.QUIET_SIGNAL, 2, -700f, settled = true)
        game.ambientSites = AmbientSiteWindow(0, 0, immutableListOf(cache, ambient(SiteKind.DRIFT_CACHE, 3, 5_000f, settled = false), quiet))

        val first = game.toRenderModel()
        assertEquals(listOf(SiteKind.DRIFT_CACHE, SiteKind.QUIET_SIGNAL), first.ambientSites.map { it.kind }, "an unsettled site is not shown")
        val (cacheMark, quietMark) = first.ambientSites
        assertEquals(900f to 400f, cacheMark.x to cacheMark.y)
        assertEquals(policy.definition(SiteKind.DRIFT_CACHE).name, cacheMark.name)
        assertEquals(policy.definition(SiteKind.DRIFT_CACHE).activationRadius, cacheMark.activationRadius)
        assertEquals(1_800f, cacheMark.reveal)
        assertEquals(3_000f, quietMark.reveal)
        assertEquals(policy.definition(SiteKind.QUIET_SIGNAL).activationRadius, quietMark.activationRadius)
        assertEquals(false to -1, cacheMark.active to cacheMark.step)
        assertNull(first.trial, "an ambient site runs no trial")

        // The same window, or a window with the same settled sites, keeps the published list.
        assertSame(first.ambientSites, game.toRenderModel(reusableCollections = first, identitySource = game).ambientSites)
        game.ambientSites = game.ambientSites.copy(sites = game.ambientSites.sites.toList().toImmutableList())
        assertSame(first.ambientSites, game.toRenderModel(reusableCollections = first).ambientSites)
        // A site that settles or opens publishes a new list.
        game.ambientSites = AmbientSiteWindow(0, 0, immutableListOf(cache))
        val opened = game.toRenderModel(reusableCollections = first)
        assertNotSame(first.ambientSites, opened.ambientSites)
        assertEquals(listOf(cacheMark), opened.ambientSites.toList())
        game.ambientSites = AmbientSiteWindow.EMPTY
        assertEquals(0, game.toRenderModel(reusableCollections = opened).ambientSites.size)
    }
}
