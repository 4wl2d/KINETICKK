// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.BuffSource
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.FieldFlags
import kinetickk.ball.gameplay.nucleus.model.MarkKind
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The 21 reaction verbs and their 21 keystone forms (combat spec §3.3, §3.4): every test pins a number of
 * the behaviour, not only that something happened.
 */
class ReactionBehaviourTest {
    private val step = MutableGameState.FIXED_STEP

    // -----------------------------------------------------------------------------------------
    // Verbs.

    @Test
    fun spallMarksTheTargetAndItsNextDirectHitThrowsItAwayFromTheCore() {
        val state = buildTestState()
        val target = state.dummy(x = 300f)
        state.setOff(ReactionId.THERMAL_SHOCK, target)
        assertEquals(MarkKind.SPALL, state.markOf(target))
        assertEquals(state.elapsed + 3f, target.status[StatusCell.MARK_UNTIL], 1e-4f)

        val knockback = state.knockbackTaken(target)
        state.dealHit(target, 5f, HitSource.WEAPON, canCrit = false)
        assertNull(state.markOf(target), "The hit cashes the mark")
        assertEquals(620f * knockback, target.vx, 0.01f)
        assertEquals(0f, target.vy, 0.01f)
        assertTrue(target.thrownUntil > state.elapsed, "A thrown enemy can slam others")
        val thrown = target.vx
        state.dealHit(target, 5f, HitSource.WEAPON, canCrit = false)
        assertEquals(thrown, target.vx, "One mark, one throw")

        // The Architect never moves, but the hit still spends the mark.
        state.bossSpawned = true
        val boss = state.addEnemyForTesting(-400f, 0f, hp = 100_000f, radius = 74f, type = EnemyType.ARCHITECT)
        state.setMark(boss, MarkKind.SPALL, 3f)
        state.dealHit(boss, 5f, HitSource.WEAPON, canCrit = false)
        assertNull(state.markOf(boss))
        assertEquals(0f, boss.vx)
    }

    @Test
    fun plasmaPoolSpeedsTheWeaponAndHalvesDashHeatWhileTheCoreStandsInIt() {
        val state = buildTestState()
        val activation = state.buildActivationBonus()
        val heat = state.effectiveDashHeatCost()
        state.setOff(ReactionId.PLASMA_BURST, state.dummy(x = 60f))
        val pool = state.fieldRecords.single()
        assertEquals(CombatFieldKind.PLASMA, pool.kind)
        assertEquals(110f, pool.radius)
        assertEquals(2.5f, pool.remaining)
        assertEquals(0f, pool.damagePerSecond)

        state.updateCombatFields(step)
        assertTrue(state.coreInField(CombatFieldKind.PLASMA))
        assertEquals(activation + 0.25f, state.buildActivationBonus(), 1e-5f)
        assertEquals(heat * 0.5f, state.effectiveDashHeatCost(), 1e-4f)

        state.coreX = 400f
        state.updateCombatFields(step)
        assertEquals(activation, state.buildActivationBonus(), 1e-5f, "Outside the pool nothing changes")
        assertEquals(heat, state.effectiveDashHeatCost(), 1e-4f)
    }

    @Test
    fun accretionDrawsLooseDataToTheStarThenImplodesHurlingOccupantsAndLatchingPickups() {
        val state = buildTestState()
        state.setOff(ReactionId.FUSION, state.dummy(x = 600f))
        val drawn = Pickup(PickupType.DATA, 800f, 0f).also { state.pickups += it }
        val key = Pickup(PickupType.KEY, 650f, 0f).also { state.pickups += it }
        val far = Pickup(PickupType.DATA, 950f, 0f).also { state.pickups += it }
        val latched = Pickup(PickupType.REPAIR, 600f, 180f).also { state.pickups += it }
        val occupant = state.dummy(x = 680f)
        state.updateCombatFields(step)
        assertEquals(-500f, drawn.vx, 0.01f, "Data within 300 rushes to the star at 500 u/s")
        assertEquals(0f, key.vx, "Keys never move")
        assertEquals(0f, far.vx, "Data beyond 300 stays")

        var before = occupant.vx
        while (state.fieldRecords.isNotEmpty()) {
            before = occupant.vx
            latched.vx = 0f
            latched.vy = 0f
            state.elapsed += step
            state.updateCombatFields(step)
        }
        val knockback = state.knockbackTaken(occupant)
        // The last step still pulls inward (220 u/s²), then the implosion throws outward at 500.
        assertEquals((500f - 220f * step) * knockback, occupant.vx - before, 0.01f)
        assertTrue(latched.attracted, "Pickups within radius + 40 latch to the Core")
        assertFalse(drawn.attracted || far.attracted)
    }

    @Test
    fun ablazeSetsEveryRammedEnemyBurningAndWildfireSpreadsToThreeAtMost() {
        val state = buildTestState()
        val target = state.dummy(x = 300f)
        val neighbors = List(5) { index -> state.dummy(x = 360f, y = (index - 2) * 30f) }
        state.setOff(ReactionId.WILDFIRE, target)
        assertEquals(3, neighbors.count { it.stacksOf(StatusElement.THERMAL) == 2 }, "Three neighbors take the declared two Thermal")
        assertEquals(2, neighbors.count { it.stacksOf(StatusElement.THERMAL) == 0 })
        assertEquals(state.elapsed + 3f, state.ablazeUntil)

        val rammed = state.dummy(x = -300f)
        state.onAblazeContact(rammed)
        assertEquals(2, rammed.stacksOf(StatusElement.THERMAL))
        assertEquals(1, rammed.generationOf(StatusElement.THERMAL))

        val far = buildTestState()
        far.setOff(ReactionId.WILDFIRE, far.dummy(x = 900f))
        assertEquals(0f, far.ablazeUntil, "Beyond the link radius the Core is not set alight")
    }

    @Test
    fun frictionlessCutsTheCoreDragToAQuarterAndVentsTwelveHeatOnce() {
        fun glide(frictionless: Boolean): Float {
            val state = buildTestState()
            state.heat = 50f
            if (frictionless) {
                state.setOff(ReactionId.SUPERCONDUCT, state.dummy(x = 300f))
                assertEquals(38f, state.heat, 1e-4f)
                assertEquals(state.elapsed + 2.5f, state.frictionlessUntil)
            }
            state.setVelocityForTesting(800f, 0f)
            state.aimAt(600f, 0f)
            state.updateCore(step)
            return state.velocityX
        }
        val drag = buildTestState().dragCoefficient
        assertEquals(exp(0.75f * drag * step), glide(true) / glide(false), 1e-6f)
    }

    @Test
    fun iceFanFliesAwayFromTheCoreInTwentyFiveDegreeStepsAndKnocksWhatItHits() {
        val state = buildTestState()
        val target = state.dummy(x = 400f)
        val angles = (-2..2).map { it * 25f * PI.toFloat() / 180f }
        val struck = angles.map { angle -> state.dummy(x = 400f + cos(angle) * 150f, y = sin(angle) * 150f) }
        val behind = state.dummy(x = 250f)
        state.setOff(ReactionId.GLACIER_CRUSH, target)

        struck.forEachIndexed { index, enemy ->
            val knockback = state.knockbackTaken(enemy)
            assertEquals(1, enemy.stacksOf(StatusElement.CRYO), "shard $index")
            assertEquals(cos(angles[index]) * 300f * knockback, enemy.vx, 0.01f)
            assertEquals(sin(angles[index]) * 300f * knockback, enemy.vy, 0.01f)
        }
        assertEquals(0, behind.stacksOf(StatusElement.CRYO), "No shard flies back toward the Core")
        val cue = state.takeVisualFxCues().filterIsInstance<VisualFxCue.ReactionTriggered>().single()
        assertEquals(0f, cue.direction, 1e-6f)
    }

    @Test
    fun glassChargeKeepsTheCoreMovingThroughEmbrittledEnemiesAtFiveHundredOrMore() {
        fun ram(speed: Float, embrittled: Boolean, boss: Boolean = false): Float {
            val state = buildTestState()
            state.clearObstaclesForTesting()
            state.bossSpawned = boss
            val enemy = if (boss) {
                state.addEnemyForTesting(80f, 0f, hp = 1_000_000f, radius = 74f, type = EnemyType.ARCHITECT)
            } else {
                state.dummy(x = 20f)
            }
            if (embrittled) enemy.status[StatusCell.EMBRITTLED_UNTIL] = state.elapsed + 4f
            state.previousCoreX = -10f
            state.coreX = 0f
            state.setVelocityForTesting(speed, 0f)
            state.resolveEnemyCoreCollisions()
            return state.velocityX
        }
        assertEquals(600f, ram(600f, embrittled = true), "Pierced: no recoil")
        assertEquals(500f, ram(500f, embrittled = true))
        assertTrue(ram(600f, embrittled = false) < 600f, "An ordinary enemy still stops the Core")
        assertTrue(ram(450f, embrittled = true) < 450f, "Below 500 u/s the Core recoils")
        assertTrue(ram(600f, embrittled = true, boss = true) < 600f, "The Architect is never pierced")
    }

    @Test
    fun attractorFollowsItsAnchorAndBendsPlayerShotsAtFourRadiansPerSecondKeepingTheirSpeed() {
        val state = buildTestState()
        val target = state.dummy(x = 400f)
        state.setOff(ReactionId.MAGNETIZE, target)
        val field = state.fieldRecords.single()
        assertEquals(CombatFieldKind.ATTRACTOR, field.kind)
        assertEquals(target.id, field.anchorId)
        assertEquals(3f, field.remaining)
        assertEquals(260f, field.pull)
        assertEquals(200f, field.radius)

        target.x = 450f
        target.vx = 0f
        val shot = Projectile(x = 450f, y = 150f, vx = 600f, vy = 0f, radius = 4f, life = 2f, hostile = false, damage = 1f)
        val bolt = state.hostileBolt(450f, 150f).also { it.vx = 600f }
        state.projectiles += shot
        state.updateCombatFields(step)
        assertEquals(450f, state.fieldRecords.single().x, "The field rides on its anchor")
        assertEquals(-4f * step, atan2(shot.vy, shot.vx), 1e-5f)
        assertEquals(600f, length(shot.vx, shot.vy), 1e-3f)
        assertEquals(0f, bolt.vy, "Hostile shots are not bent")

        target.hp = 0f
        target.x = 900f
        state.updateCombatFields(step)
        assertEquals(450f, state.fieldRecords.single().x, "A dead anchor leaves the field where it was")
        assertEquals(-1, state.fieldRecords.single().anchorId)
    }

    @Test
    fun overcurrentKicksTheWeaponAtMostOncePerSecond() {
        val state = buildTestState()
        val key = BuffSource.reaction(ReactionId.FEEDBACK)
        state.setOff(ReactionId.FEEDBACK, state.dummy(x = 300f))
        val kick = state.buffs.single { it.sourceKey == key }
        assertEquals(BuffStat.WEAPON, kick.stat)
        assertEquals(state.elapsed + 0.35f, kick.expiresAt, 1e-4f)
        assertEquals(state.elapsed + 1f, state.overcurrentReadyAt)

        state.elapsed += 0.5f
        state.setOff(ReactionId.FEEDBACK, state.dummy(x = -300f))
        assertEquals(kick.expiresAt, state.buffs.single { it.sourceKey == key }.expiresAt, "Within a second nothing refreshes")
        state.elapsed += 0.6f
        state.setOff(ReactionId.FEEDBACK, state.dummy(x = 0f, y = 300f))
        assertEquals(state.elapsed + 0.35f, state.buffs.single { it.sourceKey == key }.expiresAt, 1e-4f)
    }

    @Test
    fun compostDampsOrdinaryOccupantsAndGrowsTheWellThreeTimesAtMost() {
        val state = buildTestState()
        state.setOff(ReactionId.ROT_WELL, state.dummy(x = 400f))
        val drifter = state.dummy(x = 400f).also { it.vx = 100f }
        val elite = state.addEnemyForTesting(400f, 0f, hp = 1_000_000f, type = EnemyType.ELITE).also { it.vx = 100f }
        state.updateCombatFields(step)
        assertEquals(100f * exp(-2f * step), drifter.vx, 1e-3f)
        assertEquals(100f, elite.vx, "Elites are never damped")

        val life = state.fieldRecords.single().remaining
        repeat(4) { state.onStatusCarrierDied(state.dummy(x = 420f)) }
        val well = state.fieldRecords.single()
        assertEquals(200f, well.radius, "+20 per death, three growths")
        assertEquals(3f, well.charge)
        assertEquals(life + 1.5f, well.remaining, 1e-4f)
        state.onStatusCarrierDied(state.dummy(x = 900f))
        assertEquals(3f, state.fieldRecords.single().charge, "A death outside does not count")
    }

    @Test
    fun blastJumpRidesTheClapWithOnePointSixTimesTheImpulseForNoHeat() {
        fun dash(clap: Boolean, at: Float = 200f): Pair<Float, Float> {
            val state = buildTestState()
            state.clearObstaclesForTesting()
            state.aimAt(300f, 0f)
            if (clap) state.setOff(ReactionId.THUNDERCLAP, state.dummy(x = at))
            state.performDash()
            assertEquals(0f, state.blastJumpUntil, "A Dash spends the window")
            return state.velocityX to state.heat
        }
        val (plain, plainHeat) = dash(clap = false)
        val (jump, jumpHeat) = dash(clap = true)
        assertTrue(plainHeat > 0f)
        assertEquals(plain * 1.6f, jump, 0.01f)
        assertEquals(0f, jumpHeat)
        val (farClap, farHeat) = dash(clap = true, at = 300f)
        assertEquals(plain, farClap, 0.01f, "A clap beyond 260 opens no window")
        assertEquals(plainHeat, farHeat)
    }

    @Test
    fun shardGuardCirclesTheCoreWithAtMostSixShardsThatBreakOnTheFirstShotOrEnemy() {
        val state = buildTestState()
        state.setOff(ReactionId.SHATTERPOINT, state.dummy(x = 300f))
        assertEquals(5, state.guardShards)
        assertEquals(state.elapsed + 4f, state.guardUntil)
        state.setOff(ReactionId.SHATTERPOINT, state.dummy(x = -300f))
        assertEquals(6, state.guardShards, "A second guard tops up to six")

        val orbit = state.coreRadius + 74f
        fun slot(index: Int, count: Int): Pair<Float, Float> {
            val angle = state.elapsed * 3f + index * TAU / count
            return cos(angle) * orbit to sin(angle) * orbit
        }
        val (boltX, boltY) = slot(0, 6)
        val bolt = state.hostileBolt(boltX, boltY)
        state.updateCoreReactionStates()
        assertTrue(bolt.spent)
        assertEquals(5, state.guardShards)

        val (enemyX, enemyY) = slot(1, 5)
        val struck = state.dummy(x = enemyX, y = enemyY)
        state.updateCoreReactionStates()
        assertEquals(state.guardDamage, struck.maxHp - struck.hp, 0.01f)
        assertEquals(1, struck.stacksOf(StatusElement.CRYO))
        assertEquals(4, state.guardShards)

        state.elapsed = state.guardUntil + 0.1f
        state.updateCoreReactionStates()
        assertEquals(0, state.guardShards, "The guard lapses")
    }

    @Test
    fun arcWeaveThrowsAThreeLinkChainFromTheCoreOnEveryTurnAtHalfTheReaction() {
        val state = buildTestState()
        state.setOff(ReactionId.RESONANT_ARC, state.dummy(x = 300f, y = 300f))
        val magnitude = state.magnitude(ReactionId.RESONANT_ARC)
        assertEquals(state.elapsed + 3f, state.weaveUntil)
        assertEquals(0.5f * magnitude, state.weaveDamage, 1e-3f)
        assertEquals(220f + 25f, state.weaveRange, "Its own range: one consumed Resonance")

        val chained = List(4) { index -> state.dummy(x = 60f + index * 60f) }
        state.onTurnForReactions()
        assertEquals(3, chained.count { it.hp < it.maxHp })
        chained.take(3).forEach { assertEquals(state.weaveDamage, it.maxHp - it.hp, 0.01f) }
        assertEquals(chained[3].maxHp, chained[3].hp)
    }

    @Test
    fun rockEchoRepeatsTheQuakeAroundAtMostThreeNearbyRocks() {
        val state = buildTestState()
        state.placeObstaclesForTesting(testObstacle(300f, 200f, 40f), testObstacle(500f, -200f, 40f), testObstacle(300f, -200f, 40f),
            testObstacle(400f, 420f, 40f))
        val byRock = state.dummy(x = 300f, y = 270f)
        state.setOff(ReactionId.SEISMIC, state.dummy(x = 400f))
        val magnitude = state.magnitude(ReactionId.SEISMIC)
        assertEquals(0.5f * magnitude, byRock.maxHp - byRock.hp, 0.01f)
        assertEquals(state.elapsed + SEISMIC_STUN_SECONDS, byRock.disabledUntil, 1e-4f)
        val quakes = state.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>()
            .count { it.colorIndex == FxColorCode.quake(StatusElement.GRAVITIC) }
        assertEquals(1 + 3, quakes, "The quake and three echoes; the rock beyond 260 stays silent")

        val crowded = buildTestState()
        crowded.placeObstaclesForTesting(*Array(5) { index -> testObstacle(300f + index * 50f, 200f, 30f) })
        crowded.setOff(ReactionId.SEISMIC, crowded.dummy(x = 400f))
        assertEquals(1 + 3, crowded.takeVisualFxCues().filterIsInstance<VisualFxCue.ShockwaveAdded>()
            .count { it.colorIndex == FxColorCode.quake(StatusElement.GRAVITIC) }, "At most three rocks echo")
    }

    @Test
    fun breakpointDetonatesCrackedEnemiesNearTheCoreOnBrakeReleaseTwelveAtMost() {
        val state = buildTestState()
        val target = state.dummy(x = 200f)
        val neighbor = state.dummy(x = 260f)
        state.setOff(ReactionId.FRACTURE, target)
        assertEquals(MarkKind.CRACKED, state.markOf(neighbor))
        assertEquals(state.elapsed + 4f, neighbor.status[StatusCell.MARK_UNTIL], 1e-4f)
        val far = state.dummy(x = 600f).also { state.setMark(it, MarkKind.CRACKED, 4f) }

        val damage = 6f * state.content.build.reaction(ReactionId.FRACTURE).power * state.build.potency(StatusElement.ENTROPY) *
            state.build.reactionPower * state.runScale() * (1f + state.vulnerability(neighbor, HitSource.STATUS))
        val integrity = neighbor.hp
        val decay = neighbor.stacksOf(StatusElement.ENTROPY)
        state.braking = true
        state.updateBuildEdges(0.4f)
        state.braking = false
        state.updateBuildEdges(step)
        assertEquals(damage, integrity - neighbor.hp, 0.05f)
        assertEquals(decay + 2, neighbor.stacksOf(StatusElement.ENTROPY))
        assertNull(state.markOf(neighbor), "Detonated marks are spent")
        assertEquals(MarkKind.CRACKED, state.markOf(far), "Beyond 320 the mark waits")

        val crowd = buildTestState()
        repeat(14) { index -> crowd.dummy(x = 40f + index * 15f).also { crowd.setMark(it, MarkKind.CRACKED, 4f) } }
        crowd.detonateCracked()
        assertEquals(2, crowd.enemies.count { crowd.markIs(it, MarkKind.CRACKED) }, "Twelve detonations per release")
    }

    @Test
    fun hushSilencesEnemiesThatEnterItAndBossesOnlyThroughSilencesOwnRule() {
        val state = buildTestState()
        state.setOff(ReactionId.SILENCE, state.dummy(x = 400f))
        val hush = state.fieldRecords.single()
        assertEquals(CombatFieldKind.HUSH, hush.kind)
        assertEquals(170f, hush.radius)
        assertEquals(2.5f, hush.remaining)

        val late = state.dummy(x = 450f)
        state.bossSpawned = true
        val boss = state.addEnemyForTesting(400f, 100f, hp = 100_000f, radius = 74f, type = EnemyType.ARCHITECT)
        assertEquals(0f, late.silencedUntil)
        state.elapsed += 0.4f
        state.updateCombatFields(0.4f)
        assertEquals(state.elapsed + 0.5f, late.silencedUntil, 1e-4f)
        assertEquals(state.elapsed + SILENCE_BOSS_SECONDS, boss.silencedUntil, 1e-4f)
        val bossSilence = boss.silencedUntil
        repeat(3) {
            state.elapsed += 0.4f
            state.updateCombatFields(0.4f)
        }
        assertEquals(bossSilence, boss.silencedUntil, "The boss's grace holds through later ticks")
        assertEquals(state.elapsed + 0.5f, late.silencedUntil, 1e-4f)
    }

    @Test
    fun flameLeapPassesTheBurnOnAtSixtyPercentUntilItIsTooWeakToLeap() {
        val state = buildTestState()
        val burning = state.dummy(x = 300f)
        state.setOff(ReactionId.BLACKFIRE, burning)
        val rate = burning.status[StatusCell.BLACKFIRE_DPS]
        val next = state.dummy(x = 450f)
        val far = state.dummy(x = 300f, y = 400f)
        state.elapsed += 1f
        burning.hp = 0f
        state.onStatusCarrierDied(burning)
        assertEquals(state.elapsed + 2f, next.status[StatusCell.BLACKFIRE_UNTIL], 1e-4f)
        assertEquals(0.6f * rate, next.status[StatusCell.BLACKFIRE_DPS], 1e-3f)
        assertEquals(0f, far.status[StatusCell.BLACKFIRE_UNTIL])

        // A flame with little time left still leaps for a second; one below 8 × run scale does not.
        state.elapsed = next.status[StatusCell.BLACKFIRE_UNTIL] - 0.2f
        next.status[StatusCell.BLACKFIRE_DPS] = rate
        val third = state.dummy(x = 600f)
        state.onStatusCarrierDied(next)
        assertEquals(state.elapsed + 1f, third.status[StatusCell.BLACKFIRE_UNTIL], 1e-4f)
        val weak = state.dummy(x = -300f)
        weak.status[StatusCell.BLACKFIRE_UNTIL] = state.elapsed + 2f
        weak.status[StatusCell.BLACKFIRE_DPS] = 8f * state.runScale() / 0.6f * 0.99f
        val spared = state.dummy(x = -420f)
        state.onStatusCarrierDied(weak)
        assertEquals(0f, spared.status[StatusCell.BLACKFIRE_UNTIL])
    }

    @Test
    fun timeBubbleSpendsUpToTwentyFourShotsAndHoldsOrdinaryOccupantsEachTick() {
        val state = buildTestState()
        state.setOff(ReactionId.STASIS, state.dummy(x = 400f))
        val bubble = state.fieldRecords.single()
        assertEquals(CombatFieldKind.TIME_BUBBLE, bubble.kind)
        assertEquals(140f, bubble.radius)
        assertEquals(2f, bubble.remaining)
        val bolts = List(30) { index -> state.hostileBolt(380f + (index % 6) * 10f, (index / 6) * 10f - 20f) }
        state.updateCombatFields(step)
        assertEquals(24, bolts.count { it.spent })
        assertEquals(24f, state.fieldRecords.single().charge)
        state.updateCombatFields(step)
        assertEquals(24, bolts.count { it.spent }, "The bubble's budget is spent")

        val ordinary = state.dummy(x = 420f)
        val elite = state.addEnemyForTesting(420f, 0f, hp = 1_000_000f, type = EnemyType.ELITE)
        state.elapsed += 0.4f
        state.updateCombatFields(0.4f)
        assertEquals(state.elapsed + 0.5f, ordinary.disabledUntil, 1e-4f)
        assertEquals(0f, elite.disabledUntil)
    }

    @Test
    fun voidAnchorBlinksADashAimedAtTheToughestEnemyAndIsSpent() {
        val state = buildTestState()
        state.clearObstaclesForTesting()
        val target = state.dummy(x = 200f)
        val tough = state.dummy(x = 400f, hp = 2_000_000f)
        state.setOff(ReactionId.ANNIHILATE, target)
        assertEquals(tough.id, state.voidAnchorId)
        assertEquals(state.elapsed + 3f, state.voidAnchorUntil)

        state.aimAt(700f, 40f)
        state.performDash()
        assertEquals(400f - tough.radius - state.coreRadius - 8f, state.coreX, 0.01f)
        assertEquals(0f, state.coreY, 0.01f)
        assertEquals(-1, state.voidAnchorId)
        assertTrue(state.velocityX > 0f && kotlin.math.abs(state.velocityY) < 0.01f, "The Dash drives on into the anchor")

        val wide = buildTestState()
        wide.clearObstaclesForTesting()
        val anchor = wide.dummy(x = 400f, hp = 2_000_000f)
        wide.setOff(ReactionId.ANNIHILATE, wide.dummy(x = 200f))
        wide.aimAt(400f, 400f)
        wide.performDash()
        assertEquals(0f, wide.coreX, "A Dash 45° off the anchor does not blink")
        assertEquals(anchor.id, wide.voidAnchorId)
    }

    @Test
    fun mawEatsUpToTwentyShotsAndPopsOnlyWhenFedFive() {
        fun lastStepThrow(shots: Int): Pair<Float, Float> {
            val state = buildTestState()
            state.setOff(ReactionId.NULL_WELL, state.dummy(x = 400f))
            val bolts = List(shots) { index -> state.hostileBolt(380f + (index % 5) * 10f, (index / 5) * 10f - 20f) }
            val occupant = state.dummy(x = 480f)
            state.updateCombatFields(step)
            assertEquals(minOf(20, shots), bolts.count { it.spent })
            var before = occupant.vx
            while (state.fieldRecords.isNotEmpty()) {
                before = occupant.vx
                state.elapsed += step
                state.updateCombatFields(step)
            }
            return (occupant.vx - before) to state.knockbackTaken(occupant)
        }
        val (fed, knockback) = lastStepThrow(25)
        assertEquals((400f - 240f * step) * knockback, fed, 0.01f)
        val (hungry, _) = lastStepThrow(4)
        assertEquals(-240f * step * knockback, hungry, 0.01f, "Four shots are not enough to pop")
    }

    @Test
    fun erasureLatchesTheExecutedVictimsDataWithoutAnExtraRandomDraw() {
        fun execute(verbs: Boolean): Pair<List<Boolean>, Any> {
            val state = buildTestState(content = if (verbs) canonicalContent() else contentWithoutReactionVerbs)
            val victim = state.dummy(x = 300f, hp = 1_000f).also { it.hp = 100f }
            state.setOff(ReactionId.OBLIVION, victim)
            assertFalse(victim.alive)
            assertEquals(if (verbs) victim.id else -1, state.erasureLatchId)
            state.sweepKills()
            assertEquals(-1, state.erasureLatchId)
            return state.pickups.filter { it.type == PickupType.DATA }.map { it.attracted } to state.gameplayRandom.snapshot()
        }
        val (latched, random) = execute(verbs = true)
        val (scattered, plainRandom) = execute(verbs = false)
        assertTrue(latched.isNotEmpty() && latched.all { it })
        assertTrue(scattered.none { it })
        assertEquals(plainRandom, random)
    }

    // -----------------------------------------------------------------------------------------
    // Keystones.

    @Test
    fun flashFreezeFreezesOnTheNextHitInsteadOfThrowingAtEightyPercentDamage() {
        val plain = buildTestState()
        val plainTarget = plain.dummy(x = 300f)
        plain.setOff(ReactionId.THERMAL_SHOCK, plainTarget)
        val state = buildTestState().apply { useKeystones(ReactionId.THERMAL_SHOCK) }
        val target = state.dummy(x = 300f)
        state.setOff(ReactionId.THERMAL_SHOCK, target)
        assertEquals((plainTarget.maxHp - plainTarget.hp) * 0.8f, target.maxHp - target.hp, 0.01f)
        assertTrue(state.takeVisualFxCues().filterIsInstance<VisualFxCue.ReactionTriggered>().single().flags and ReactionCueFlags.KEYSTONE != 0)

        state.dealHit(target, 5f, HitSource.WEAPON, canCrit = false)
        assertEquals(state.elapsed + 0.8f, target.status[StatusCell.FROZEN_UNTIL], 1e-4f)
        assertEquals(0f, target.vx, "No throw")
    }

    @Test
    fun arcPlasmaRepeatsTheBurstOnTwoChargedEnemiesAtHalfStrengthWithoutAPool() {
        val state = buildTestState().apply { useKeystones(ReactionId.PLASMA_BURST) }
        val target = state.dummy(x = 300f)
        val charged = listOf(state.dummy(x = 500f), state.dummy(x = 300f, y = 200f), state.dummy(x = 100f))
        charged.forEach { it.stack(StatusElement.ION, 1) }
        state.setOff(ReactionId.PLASMA_BURST, target)
        val magnitude = state.magnitude(ReactionId.PLASMA_BURST)
        assertTrue(state.fieldRecords.none { it.kind == CombatFieldKind.PLASMA }, "No pool")
        charged.take(2).forEach { repeat ->
            assertEquals(0.5f * magnitude, repeat.maxHp - repeat.hp, 0.01f)
            assertEquals(1, repeat.stacksOf(StatusElement.THERMAL))
        }
        assertEquals(charged[2].maxHp, charged[2].hp, "Two repeats at most")
    }

    @Test
    fun dwarfStarLastsLongerPullsHarderBurnsHalfAndNeverImplodes() {
        val state = buildTestState().apply { useKeystones(ReactionId.FUSION) }
        state.setOff(ReactionId.FUSION, state.dummy(x = 600f))
        val magnitude = state.magnitude(ReactionId.FUSION)
        val star = state.fieldRecords.single()
        assertEquals(3.2f, star.remaining)
        assertEquals(440f, star.pull)
        assertEquals(magnitude * 0.5f, star.damagePerSecond, 1e-3f)
        assertTrue(star.flags and FieldFlags.KEYSTONE != 0)
        val occupant = state.dummy(x = 680f)
        var before = occupant.vx
        while (state.fieldRecords.isNotEmpty()) {
            before = occupant.vx
            state.elapsed += step
            state.updateCombatFields(step)
        }
        assertEquals(-440f * step * state.knockbackTaken(occupant), occupant.vx - before, 0.01f, "No implosion")
    }

    @Test
    fun firewalkerBurnsTheCoreAnywhereForSixSecondsAndIgnitesNearlyFullEnemiesWithoutSpreading() {
        val state = buildTestState().apply { useKeystones(ReactionId.WILDFIRE) }
        val neighbor = state.dummy(x = 960f)
        state.setOff(ReactionId.WILDFIRE, state.dummy(x = 900f))
        assertEquals(state.elapsed + 6f, state.ablazeUntil)
        assertEquals(0, neighbor.stacksOf(StatusElement.THERMAL), "No spread")

        val threshold = state.breakThreshold(StatusElement.THERMAL)
        val primed = state.dummy(x = -300f).also { it.stack(StatusElement.THERMAL, threshold - 4) }
        state.onAblazeContact(primed)
        assertEquals(0, primed.stacksOf(StatusElement.THERMAL), "Two short of the threshold after the contact: it Ignites")
        assertTrue(primed.status[StatusCell.REARM_AT + StatusElement.THERMAL.ordinal] > state.elapsed)
        val cold = state.dummy(x = 0f, y = -300f)
        state.onAblazeContact(cold)
        assertEquals(2, cold.stacksOf(StatusElement.THERMAL))
    }

    @Test
    fun zeroResistanceChainsSixLinksHitsFrozenLinksHarderAndLeavesTheCoreAlone() {
        val state = buildTestState().apply { useKeystones(ReactionId.SUPERCONDUCT) }
        val links = List(7) { index -> state.dummy(x = 300f + (index + 1) * 40f) }
        state.freeze(links[0], 2f)
        state.setOff(ReactionId.SUPERCONDUCT, state.dummy(x = 300f))
        val magnitude = state.magnitude(ReactionId.SUPERCONDUCT)
        assertEquals(6, links.count { it.hp < it.maxHp })
        assertEquals(magnitude * 1.5f, links[0].maxHp - links[0].hp, 0.01f)
        assertEquals(0f, links[0].status[StatusCell.BRITTLE])
        assertEquals(magnitude, links[1].maxHp - links[1].hp, 0.01f)
        assertEquals(0f, state.frictionlessUntil)
    }

    @Test
    fun aimedAvalancheFollowsTheAimAndFreezesEnemiesWithFourChill() {
        val state = buildTestState().apply { useKeystones(ReactionId.GLACIER_CRUSH) }
        state.lastAimDirectionX = 0f
        state.lastAimDirectionY = 1f
        val chilled = state.dummy(x = 400f, y = 150f).also { it.stack(StatusElement.CRYO, 3) }
        state.setOff(ReactionId.GLACIER_CRUSH, state.dummy(x = 400f))
        val cue = state.takeVisualFxCues().filterIsInstance<VisualFxCue.ReactionTriggered>().single()
        assertEquals(PI.toFloat() / 2f, cue.direction, 1e-5f)
        assertTrue(state.isFrozen(chilled), "Three Chill plus the shard's residue reach four")
    }

    @Test
    fun glassBurstShattersAPiercedEmbrittledEnemyIntoThreeShardsAndEndsItsEmbrittle() {
        val state = buildTestState().apply { useKeystones(ReactionId.EMBRITTLE) }
        state.clearObstaclesForTesting()
        val target = state.dummy(x = 20f)
        state.setOff(ReactionId.EMBRITTLE, target)
        assertEquals(state.elapsed + 2f, target.status[StatusCell.EMBRITTLED_UNTIL])
        val behind = state.dummy(x = 140f)
        state.previousCoreX = -10f
        state.coreX = 0f
        state.setVelocityForTesting(600f, 0f)
        val impact = state.impactPower(600f) * state.characterImpactMultiplier()
        state.resolveEnemyCoreCollisions()
        assertEquals(600f, state.velocityX)
        assertEquals(0f, target.status[StatusCell.EMBRITTLED_UNTIL])
        assertEquals(impact * 0.5f, behind.maxHp - behind.hp, 0.05f)
    }

    @Test
    fun repulsorHurlsTheTargetAwayWithoutAnAttractorAndItsSlamsCharge() {
        val state = buildTestState().apply { useKeystones(ReactionId.MAGNETIZE) }
        val bystander = state.dummy(x = 380f)
        val target = state.dummy(x = 300f)
        state.setOff(ReactionId.MAGNETIZE, target)
        assertEquals(900f * state.knockbackTaken(target), target.vx, 0.01f)
        assertEquals(0f, bystander.vx, "No yank")
        assertTrue(state.fieldRecords.isEmpty(), "No attractor")
        assertTrue(state.repulsorCharges(target))
        assertFalse(state.repulsorCharges(bystander))

        state.clearObstaclesForTesting()
        val struck = state.dummy(x = target.x + 30f)
        state.resolveEnemyObstacles()
        assertTrue(struck.slamReadyAt > state.elapsed, "The thrown target slams into it")
        assertEquals(1, struck.stacksOf(StatusElement.ION), "and charges it")
    }

    @Test
    fun shortCircuitSkipsTheWeaponKick() {
        val state = buildTestState().apply { useKeystones(ReactionId.FEEDBACK) }
        state.setOff(ReactionId.FEEDBACK, state.dummy(x = 300f))
        assertTrue(state.buffs.none { it.sourceKey == BuffSource.reaction(ReactionId.FEEDBACK) })
        assertEquals(state.elapsed + 1f, state.overcurrentReadyAt, "The once-per-second gate still holds")
    }

    @Test
    fun plaguePitLastsFourSecondsAndPassesAVictimsDecayToEveryoneInside() {
        val state = buildTestState().apply { useKeystones(ReactionId.ROT_WELL) }
        state.setOff(ReactionId.ROT_WELL, state.dummy(x = 400f))
        assertEquals(4f, state.fieldRecords.single().remaining)
        val inside = List(2) { index -> state.dummy(x = 380f + index * 40f) }
        val outside = state.dummy(x = 700f)
        val victim = state.dummy(x = 400f).also { it.stack(StatusElement.ENTROPY, 5) }
        state.onStatusCarrierDied(victim)
        inside.forEach { assertEquals(5, it.stacksOf(StatusElement.ENTROPY)) }
        assertEquals(0, outside.stacksOf(StatusElement.ENTROPY))
        assertEquals(140f, state.fieldRecords.single().radius, "No growth")
    }

    @Test
    fun rocketClapLaunchesTheCoreAwayAtFiveHundredSixtyGatedHalfASecond() {
        val state = buildTestState().apply { useKeystones(ReactionId.THUNDERCLAP) }
        state.setOff(ReactionId.THUNDERCLAP, state.dummy(x = 200f))
        assertEquals(-560f, state.velocityX, 0.01f)
        assertEquals(0f, state.blastJumpUntil)
        state.setOff(ReactionId.THUNDERCLAP, state.dummy(x = 0f, y = 200f))
        assertEquals(0f, state.velocityY, 0.01f, "A second clap within 0.5 s does not push")
        state.elapsed += 0.5f
        state.setOff(ReactionId.THUNDERCLAP, state.dummy(x = 0f, y = -150f))
        assertEquals(560f, state.velocityY, 0.01f)
    }

    @Test
    fun crystalChoirFiresSevenShardsAnywhereAndShattersResonantEnemies() {
        val state = buildTestState().apply { useKeystones(ReactionId.SHATTERPOINT) }
        val target = state.dummy(x = 300f)
        val offset = target.id * 0.53f + 0.3f
        val resonant = state.dummy(x = 300f + cos(offset) * 150f, y = sin(offset) * 150f).also {
            it.stack(StatusElement.SONIC, 5)
            // Its own reaction cooldown keeps the shard's Chill from setting off a second Shatterpoint.
            it.status[StatusCell.REACTION_READY_AT] = state.elapsed + 10f
        }
        state.setOff(ReactionId.SHATTERPOINT, target)
        assertEquals(0, state.guardShards, "No guard, even near the Core")
        assertEquals(0, resonant.stacksOf(StatusElement.SONIC), "Its Resonance broke into a Shatter")
        val arcs = state.takeVisualFxCues().filterIsInstance<VisualFxCue.WeaponArcAdded>()
        assertEquals(7, arcs.size)
    }

    @Test
    fun standingWaveWeavesForSixSecondsAnywhereWithTwoLinksAtThirtyFivePercent() {
        val state = buildTestState().apply { useKeystones(ReactionId.RESONANT_ARC) }
        state.setOff(ReactionId.RESONANT_ARC, state.dummy(x = 900f))
        val magnitude = state.magnitude(ReactionId.RESONANT_ARC)
        assertEquals(state.elapsed + 6f, state.weaveUntil)
        assertEquals(0.35f * magnitude, state.weaveDamage, 1e-3f)
        val chained = List(3) { index -> state.dummy(x = 60f + index * 60f) }
        state.onTurnForReactions()
        assertEquals(2, chained.count { it.hp < it.maxHp })
    }

    @Test
    fun aftershockQuakesAgainAtSixtyPercentAfterSixTenthsOfASecondWithoutRockEchoes() {
        val state = buildTestState().apply { useKeystones(ReactionId.SEISMIC) }
        state.placeObstaclesForTesting(testObstacle(300f, 200f, 40f))
        val target = state.dummy(x = 400f)
        val neighbor = state.dummy(x = 500f, hp = 10_000f)
        val byRock = state.dummy(x = 300f, y = 270f)
        state.setOff(ReactionId.SEISMIC, target)
        val magnitude = state.magnitude(ReactionId.SEISMIC)
        val first = neighbor.maxHp - neighbor.hp
        assertEquals(magnitude, first, 0.01f)
        assertEquals(byRock.maxHp, byRock.hp, "No rock echo")
        state.elapsed += 0.59f
        state.updateCoreReactionStates()
        assertEquals(first, neighbor.maxHp - neighbor.hp, 0.01f)
        state.elapsed += 0.02f
        state.updateCoreReactionStates()
        assertEquals(first + 0.6f * magnitude, neighbor.maxHp - neighbor.hp, 0.01f)

        // Two pending slots: a third aftershock in the window is dropped.
        val busy = buildTestState().apply { useKeystones(ReactionId.SEISMIC) }
        repeat(3) { index -> busy.setOff(ReactionId.SEISMIC, busy.dummy(x = 300f, y = index * 400f - 400f)) }
        assertTrue(busy.aftershockAt0 > 0f && busy.aftershockAt1 > 0f)
        assertEquals(-400f, busy.aftershockY0)
        assertEquals(0f, busy.aftershockY1)
    }

    @Test
    fun crevasseSuppressesAspectsForFourSecondsAndCracksArmorButNeverReachesBosses() {
        val state = buildTestState().apply { useKeystones(ReactionId.FRACTURE) }
        val immune = state.addEnemyForTesting(260f, 0f, hp = 1_000_000f, type = EnemyType.ELITE).also {
            assignAspect(it, StatusElement.ENTROPY.ordinal, immune = true)
            it.armored = true
        }
        state.bossSpawned = true
        val boss = state.addEnemyForTesting(150f, 200f, hp = 100_000f, radius = 74f, type = EnemyType.ARCHITECT)
        state.setOff(ReactionId.FRACTURE, state.dummy(x = 200f))
        assertEquals(MarkKind.SUPPRESSED, state.markOf(immune))
        assertEquals(state.elapsed + 4f, immune.status[StatusCell.MARK_UNTIL], 1e-4f)
        assertTrue(immune.armorCracked)
        assertNull(state.markOf(boss))
        assertEquals(0, immune.stacksOf(StatusElement.ENTROPY), "Its immunity refused Fracture's own residue")
        state.applyStatus(immune, StatusElement.ENTROPY, 2, generation = 0)
        assertEquals(2, immune.stacksOf(StatusElement.ENTROPY), "No immunity while Suppressed")
        state.elapsed += 4.1f
        state.applyStatus(immune, StatusElement.ENTROPY, 2, generation = 0)
        assertEquals(2, immune.stacksOf(StatusElement.ENTROPY), "Immune again once it lapses")
    }

    @Test
    fun deadAirFollowsTheCoreWithoutDamageAndSpendsTwentyShots() {
        val state = buildTestState().apply { useKeystones(ReactionId.SILENCE) }
        val target = state.dummy(x = 300f)
        state.setOff(ReactionId.SILENCE, target)
        assertEquals(target.maxHp, target.hp, "No damage when it forms")
        assertTrue(target.silencedUntil > state.elapsed)
        val hush = state.fieldRecords.single()
        assertEquals(140f, hush.radius)
        assertEquals(3f, hush.remaining)
        assertTrue(hush.flags and FieldFlags.FOLLOWS_CORE != 0)
        state.coreX = 50f
        val bolts = List(25) { index -> state.hostileBolt(50f + (index % 5) * 10f, (index / 5) * 10f - 20f) }
        state.updateCombatFields(step)
        assertEquals(50f, state.fieldRecords.single().x)
        assertEquals(20, bolts.count { it.spent })
    }

    @Test
    fun hellfireBurnsHalfAgainHarderAndNeverLeaps() {
        val state = buildTestState().apply { useKeystones(ReactionId.BLACKFIRE) }
        val burning = state.dummy(x = 300f)
        state.setOff(ReactionId.BLACKFIRE, burning)
        assertEquals(state.magnitude(ReactionId.BLACKFIRE) * 1.5f, burning.status[StatusCell.BLACKFIRE_DPS], 1e-3f)
        val next = state.dummy(x = 400f)
        state.onStatusCarrierDied(burning)
        assertEquals(0f, next.status[StatusCell.BLACKFIRE_UNTIL])
    }

    @Test
    fun rewindReturnsCaughtShotsAsPlayerShotsAtThirtyPercentUpToTwelve() {
        val state = buildTestState().apply { useKeystones(ReactionId.STASIS) }
        state.setOff(ReactionId.STASIS, state.dummy(x = 400f, hp = 1_000_000f))
        val bolts = List(30) { index -> state.hostileBolt(380f + (index % 6) * 10f, (index / 6) * 10f - 20f, damage = 20f) }
        state.updateCombatFields(step)
        val returned = state.projectiles.filter { !it.hostile }
        assertEquals(12, returned.size)
        assertTrue(returned.all { it.damage == 6f })
        assertEquals(24, bolts.count { it.spent }, "Past the twelfth the bubble only spends shots, up to its 24")
    }

    @Test
    fun eventStrikeAnchorsForOneAndAHalfSecondsAndItsBlinkStrikeCritsForNoHeat() {
        val state = buildTestState().apply { useKeystones(ReactionId.ANNIHILATE) }
        state.clearObstaclesForTesting()
        val tough = state.dummy(x = 400f, hp = 2_000_000f)
        state.setOff(ReactionId.ANNIHILATE, state.dummy(x = 200f))
        assertEquals(state.elapsed + 1.5f, state.voidAnchorUntil)
        state.aimAt(700f, 0f)
        state.performDash()
        assertEquals(0f, state.heat)
        assertTrue(tough.status[StatusCell.MARKED_UNTIL] > state.elapsed, "The strike crits")
    }

    @Test
    fun hungryVoidFollowsTheCoreWithoutPullingAndStillEatsShots() {
        val state = buildTestState().apply { useKeystones(ReactionId.NULL_WELL) }
        state.setOff(ReactionId.NULL_WELL, state.dummy(x = 300f))
        val well = state.fieldRecords.single()
        assertEquals(120f, well.radius)
        assertEquals(2.4f, well.remaining)
        assertEquals(0f, well.pull)
        assertEquals(0f, well.x)
        val occupant = state.dummy(x = 80f)
        val bolt = state.hostileBolt(-40f, 0f)
        state.updateCombatFields(step)
        assertEquals(0f, occupant.vx)
        assertTrue(bolt.spent)
    }

    @Test
    fun lastRitesExecutesUpToSixteenPercentWithoutLatchingData() {
        val state = buildTestState().apply { useKeystones(ReactionId.OBLIVION) }
        val victim = state.dummy(x = 300f, hp = 1_000f).also { it.hp = 150f }
        state.setOff(ReactionId.OBLIVION, victim)
        assertFalse(victim.alive, "15 % is below the Last Rites line")
        assertEquals(-1, state.erasureLatchId)
        val sturdy = state.dummy(x = -300f, hp = 1_000f).also { it.hp = 170f }
        state.setOff(ReactionId.OBLIVION, sturdy)
        assertTrue(sturdy.alive)
    }

    private fun canonicalContent() = kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
}

/** Reaction strength for [a] older and [b] incoming stacks: power × (a + b) × mean potency × reaction power × run scale. */
internal fun MutableGameState.magnitude(reaction: ReactionId, a: Int = 3, b: Int = 1): Float =
    content.build.reaction(reaction).power * (a + b) * (build.potency(reaction.first) + build.potency(reaction.second)) * 0.5f *
        build.reactionPower * runScale()

private fun Enemy.stack(element: StatusElement, count: Int) {
    status[StatusCell.STACKS + element.ordinal] = count.toFloat()
}
