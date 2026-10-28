// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import java.util.IdentityHashMap
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every reaction's verb changes how a fight plays (combat spec §10): the same scripted 60 s arena runs with
 * the verb on and with the reaction's verb values removed, and the verb's play metric moves by at least 10 %
 * (Hush: hostile shots fired fall by at least 10 %). Each arena is a full fixed-step run of a Null Lance Orb
 * that cannot die, with the reaction set off on a ready enemy at a fixed cadence. The scripted player follows
 * the verb's cue the same way in both runs: it fights inside the latest pool or well, lets the Core glide,
 * holds position while stars and erasures work at range, meets a pack from its near side, dashes on the clap
 * and at the anchor, and releases the Brake; otherwise it whirls through a crowd after a wandering point. Where the
 * cue needs the Core on a point (the pool or well, the held spot, the wandering point the Magnetize lances home in
 * around) the player follows the point through the flight law's inverse. The Seismic arena keeps its rocks; there
 * the player stays below the dash-safe speed near them, so no impact is lethal. Every other arena is obstacle-free.
 */
class ReactionPlayStyleGateTest {
    @Test
    fun everyReactionVerbMovesItsPlayMetricByAtLeastTenPercent() {
        val failures = ArrayList<String>()
        for (reaction in ReactionId.entries) {
            val on = arena(reaction, verbs = true)
            val off = arena(reaction, verbs = false)
            val lower = reaction == ReactionId.SILENCE
            val ratio = if (off.metric > 0.0) on.metric / off.metric else Double.POSITIVE_INFINITY
            val passed = if (lower) on.metric <= off.metric * 0.9 else on.metric >= off.metric * 1.1 && on.metric > off.metric
            println("PLAYSTYLE ${reaction.name} ${metricName(reaction)} on=${"%.2f".format(on.metric)} off=${"%.2f".format(off.metric)} " +
                "ratio=${"%.3f".format(ratio)} reactions=${on.reactions}/${off.reactions} ${if (passed) "PASS" else "FAIL"}")
            assertTrue(on.reactions >= 20 && off.reactions >= 20, "$reaction fired too rarely to measure (${on.reactions}/${off.reactions})")
            if (!passed) failures += "$reaction ${metricName(reaction)} on=${on.metric} off=${off.metric}"
        }
        assertTrue(failures.isEmpty(), "Verbs that did not move their play metric: $failures")
    }

    private class Outcome(val metric: Double, val reactions: Int)

    private fun metricName(reaction: ReactionId): String = when (reaction) {
        ReactionId.THERMAL_SHOCK -> "slams"
        ReactionId.PLASMA_BURST, ReactionId.FEEDBACK -> "lances-fired"
        ReactionId.FUSION, ReactionId.OBLIVION -> "data-collected"
        ReactionId.WILDFIRE -> "contact-thermal-stacks"
        ReactionId.SUPERCONDUCT -> "mean-speed"
        ReactionId.GLACIER_CRUSH -> "shard-hits"
        ReactionId.EMBRITTLE -> "pierced-contacts"
        ReactionId.MAGNETIZE -> "lance-hits"
        ReactionId.ROT_WELL -> "enemy-seconds-in-wells"
        ReactionId.THUNDERCLAP -> "distance-after-clap-dashes"
        ReactionId.SHATTERPOINT, ReactionId.STASIS, ReactionId.NULL_WELL -> "shots-erased"
        ReactionId.RESONANT_ARC -> "turn-arcs"
        ReactionId.SEISMIC -> "stun-seconds-near-rocks"
        ReactionId.FRACTURE -> "detonations"
        ReactionId.SILENCE -> "hostile-shots-fired"
        ReactionId.BLACKFIRE -> "blackfire-enemy-seconds"
        ReactionId.ANNIHILATE -> "distance-closed"
    }

    private fun arena(reaction: ReactionId, verbs: Boolean): Outcome {
        val state = buildTestState(seed = 6_060, content = if (verbs) canonicalGameplayContent else contentWithoutReactionVerbs,
            weapon = WeaponId.NULL_LANCE)
        state.maxHp = 1_000_000f
        state.hp = state.maxHp
        // No level or anomaly choice interrupts the arena.
        state.nextLevelData = Int.MAX_VALUE
        state.quietArenaForTesting()
        val crowd = crowdFor(reaction)
        val step = MutableGameState.FIXED_STEP
        var spawned = 0
        var reactions = 0
        var metric = 0.0
        var nextTriggerAt = 0f
        var nextHoverAt = 0f
        var hoverX = 0f
        var hoverY = 0f
        var lastReactionX = 0f
        var lastReactionY = 0f
        val seenProjectiles = IdentityHashMap<Projectile, Int>()
        val slamReadyAt = IdentityHashMap<Enemy, Float>()
        val contactCooldown = IdentityHashMap<Enemy, Float>()
        val thermal = IdentityHashMap<Enemy, Int>()
        var dashAt = -1f
        var dashFromX = 0f
        var dashFromY = 0f
        var measuringUntil = -1f
        var anchorTarget: Enemy? = null
        var anchorDistance = 0f
        val start = state.elapsed
        while (state.elapsed < start + ARENA_SECONDS) {
            val t = state.elapsed - start
            state.runGrace = Float.MAX_VALUE
            if (reaction == ReactionId.SEISMIC) state.placeObstaclesForTesting(*ROCKS) else state.clearObstaclesForTesting()
            while (state.enemies.count { it.alive } < CROWD) {
                val angle = spawned * 2.4f
                val enemy = state.addEnemyForTesting(crowd.centerX + cos(angle) * crowd.radiusX, crowd.centerY + sin(angle) * crowd.radiusY,
                    hp = crowd.hp, type = crowd.type(spawned))
                enemy.hp = crowd.hp * crowd.hpShare
                spawned++
            }
            // A standing pack holds its ground (Spall's: all but what the player throws).
            if (crowd.holdsStill) {
                state.enemies.forEach { if (!crowd.releasesThrown || it.thrownUntil <= state.elapsed) { it.vx = 0f; it.vy = 0f } }
            }
            state.pickupRadius = crowd.magnetRadius ?: state.pickupRadius
            if (t >= nextHoverAt) {
                nextHoverAt = t + HOVER_SECONDS
                hoverX = lastReactionX
                hoverY = lastReactionY
            }
            aim(state, reaction, t, hoverX, hoverY)
            if (reaction == ReactionId.FRACTURE) state.braking = t % 1.2f < 0.45f
            if (reaction == ReactionId.SEISMIC) state.braking = tooFastNearRocks(state)
            if (state.elapsed >= nextTriggerAt) {
                nextTriggerAt = state.elapsed + crowd.triggerSeconds
                val target = readyTarget(state, reaction, crowd)
                if (target != null) {
                    val hpBefore = if (reaction == ReactionId.GLACIER_CRUSH) state.enemies.map { it.hp } else emptyList()
                    if (reaction == ReactionId.ANNIHILATE) anchorTarget = state.toughestOtherEnemy(target, 300f)
                    val clapNear = distanceSquared(target.x, target.y, state.coreX, state.coreY) <= 260f * 260f
                    lastReactionX = target.x
                    lastReactionY = target.y
                    state.setOff(reaction, target)
                    reactions++
                    if (reaction == ReactionId.GLACIER_CRUSH) {
                        metric += state.enemies.indices.count { index ->
                            index < hpBefore.size && state.enemies[index] !== target && state.enemies[index].hp < hpBefore[index]
                        }
                    }
                    if (reaction == ReactionId.THUNDERCLAP && clapNear && state.buildDashReady()) dashAt = state.elapsed
                    if (reaction == ReactionId.ANNIHILATE && anchorTarget?.alive == true && state.buildDashReady()) {
                        val anchor = anchorTarget!!
                        state.aimAt(anchor.x + (anchor.x - state.coreX), anchor.y + (anchor.y - state.coreY))
                        anchorDistance = length(anchor.x - state.coreX, anchor.y - state.coreY)
                        dashAt = state.elapsed
                    }
                }
            }
            if (dashAt == state.elapsed) {
                dashFromX = state.coreX
                dashFromY = state.coreY
                state.performDash()
                measuringUntil = state.elapsed + if (reaction == ReactionId.ANNIHILATE) 0.1f else 0.5f
            }
            val speedBefore = state.speed
            for (enemy in state.enemies) {
                slamReadyAt[enemy] = enemy.slamReadyAt
                contactCooldown[enemy] = enemy.contactCooldown
                thermal[enemy] = enemy.stacksOf(StatusElement.THERMAL)
            }
            // What the reaction itself showed is not the step's play.
            state.takeVisualFxCues()
            state.simulateStep(step)
            if (state.phase != GamePhase.RUNNING) error("$reaction arena left the running phase: ${state.message}")
            val cues = state.takeVisualFxCues()
            state.takeSoundCues()
            metric += when (reaction) {
                ReactionId.THERMAL_SHOCK -> state.enemies.count { (slamReadyAt[it] ?: it.slamReadyAt) < it.slamReadyAt }.toDouble()
                ReactionId.PLASMA_BURST, ReactionId.FEEDBACK -> newProjectiles(state, seenProjectiles, hostile = false).toDouble()
                ReactionId.FUSION, ReactionId.OBLIVION, ReactionId.GLACIER_CRUSH, ReactionId.THUNDERCLAP, ReactionId.ANNIHILATE -> 0.0
                ReactionId.WILDFIRE -> state.enemies.sumOf { enemy ->
                    if (enemy.contactCooldown > (contactCooldown[enemy] ?: enemy.contactCooldown)) {
                        (enemy.stacksOf(StatusElement.THERMAL) - (thermal[enemy] ?: 0)).coerceAtLeast(0)
                    } else 0
                }.toDouble()
                ReactionId.SUPERCONDUCT -> state.speed * step / ARENA_SECONDS.toDouble()
                ReactionId.EMBRITTLE -> state.enemies.count { enemy ->
                    enemy.contactCooldown > (contactCooldown[enemy] ?: enemy.contactCooldown) &&
                        enemy.status[StatusCell.EMBRITTLED_UNTIL] > state.elapsed && state.speed >= speedBefore * 0.95f && speedBefore >= 500f
                }.toDouble()
                ReactionId.MAGNETIZE -> lanceHits(state, seenProjectiles).toDouble()
                ReactionId.ROT_WELL -> enemySecondsInWells(state) * step.toDouble()
                ReactionId.SHATTERPOINT, ReactionId.STASIS, ReactionId.NULL_WELL -> state.shotErasedDispatches.toDouble()
                ReactionId.RESONANT_ARC -> cues.count {
                    it is VisualFxCue.WeaponArcAdded && it.style == LinkStyleCode.reaction(ReactionId.RESONANT_ARC)
                }.toDouble()
                ReactionId.SEISMIC -> state.enemies.count { enemy ->
                    enemy.alive && enemy.disabledUntil > state.elapsed && ROCKS.any { rock ->
                        distanceSquared(rock.x, rock.y, enemy.x, enemy.y) <= (rock.radius + 150f) * (rock.radius + 150f)
                    }
                } * step.toDouble()
                ReactionId.FRACTURE -> cues.count {
                    it is VisualFxCue.ShockwaveAdded && it.maxRadius == CRACKED_SHOCKWAVE_RADIUS &&
                        it.colorIndex == FxColorCode.elementTone(StatusElement.ENTROPY)
                }.toDouble()
                ReactionId.SILENCE -> newProjectiles(state, seenProjectiles, hostile = true).toDouble()
                ReactionId.BLACKFIRE -> state.enemies.count { it.alive && it.status[StatusCell.BLACKFIRE_UNTIL] > state.elapsed } * step.toDouble()
            }
            if (measuringUntil >= 0f && state.elapsed >= measuringUntil) {
                measuringUntil = -1f
                if (reaction == ReactionId.THUNDERCLAP) metric += length(state.coreX - dashFromX, state.coreY - dashFromY)
                if (reaction == ReactionId.ANNIHILATE) {
                    val anchor = anchorTarget
                    if (anchor != null && anchor.alive) metric += anchorDistance - length(anchor.x - state.coreX, anchor.y - state.coreY)
                }
            }
        }
        if (reaction == ReactionId.FUSION || reaction == ReactionId.OBLIVION) metric = state.data.toDouble()
        return Outcome(metric, reactions)
    }

    /** Who the arena spawns, where, how tough, how often the reaction is set off and on whom. */
    private class Crowd(
        val type: (Int) -> EnemyType = { EnemyType.DRIFTER },
        val centerX: Float = 0f,
        val centerY: Float = 0f,
        val radiusX: Float = 380f,
        val radiusY: Float = 380f,
        val hp: Float = 300f,
        val hpShare: Float = 1f,
        val triggerSeconds: Float = 0.25f,
        val farthest: Boolean = false,
        val holdsStill: Boolean = false,
        val releasesThrown: Boolean = false,
        /** A weak magnet, so pickups left at range stay where they fell. */
        val magnetRadius: Float? = null,
    )

    private fun crowdFor(reaction: ReactionId): Crowd = when (reaction) {
        // Stars and erasures work on a ring of gunners beyond the magnet's reach.
        ReactionId.FUSION -> Crowd(type = { EnemyType.SHOOTER }, radiusX = 620f, radiusY = 620f, hp = 40f, farthest = true,
            magnetRadius = WEAK_MAGNET)
        ReactionId.OBLIVION -> Crowd(type = { EnemyType.SHOOTER }, radiusX = 620f, radiusY = 620f, hpShare = 0.11f, farthest = true,
            magnetRadius = WEAK_MAGNET)
        // A standing pack met from its near side: shards fly into it, thrown enemies slam into it.
        ReactionId.GLACIER_CRUSH -> Crowd(centerX = 560f, radiusX = 90f, radiusY = 140f, holdsStill = true)
        ReactionId.THERMAL_SHOCK -> Crowd(centerX = 560f, radiusX = 90f, radiusY = 140f, holdsStill = true, releasesThrown = true)
        // Kills inside the wells, a well at a time.
        ReactionId.ROT_WELL -> Crowd(hp = 60f, triggerSeconds = 1f)
        // A ring of gunners, quieted now and then.
        ReactionId.SILENCE -> Crowd(type = { EnemyType.SHOOTER }, radiusX = 450f, radiusY = 450f, triggerSeconds = 1f)
        // Burning enemies have to die for the flame to leap.
        ReactionId.BLACKFIRE -> Crowd(hp = 50f)
        ReactionId.SHATTERPOINT, ReactionId.STASIS, ReactionId.NULL_WELL ->
            Crowd(type = { index -> if (index % 2 == 0) EnemyType.SHOOTER else EnemyType.DRIFTER })
        else -> Crowd()
    }

    private fun aim(state: MutableGameState, reaction: ReactionId, t: Float, hoverX: Float, hoverY: Float) {
        when (reaction) {
            ReactionId.PLASMA_BURST, ReactionId.ROT_WELL ->
                follow(state, hoverX + 40f * cos(3f * t), hoverY + 40f * sin(3f * t), -120f * sin(3f * t), 120f * cos(3f * t))
            ReactionId.SUPERCONDUCT -> {
                val speed = state.speed
                if (length(state.coreX, state.coreY) > 350f || speed < 50f) {
                    state.aimAt(0f, 0f)
                } else {
                    state.aimAt(state.coreX + state.velocityX / speed * 40f, state.coreY + state.velocityY / speed * 40f)
                }
            }
            ReactionId.FUSION, ReactionId.OBLIVION, ReactionId.SILENCE ->
                follow(state, 40f * cos(2f * t), 40f * sin(2f * t), -80f * sin(2f * t), 80f * cos(2f * t))
            ReactionId.GLACIER_CRUSH, ReactionId.THERMAL_SHOCK -> state.aimAt(200f + 60f * cos(1.3f * t), 120f * sin(0.9f * t))
            ReactionId.MAGNETIZE ->
                follow(state, 220f * cos(1.7f * t), 220f * sin(1.1f * t), -374f * sin(1.7f * t), 242f * cos(1.1f * t))
            else -> state.aimAt(220f * cos(1.7f * t), 220f * sin(1.1f * t))
        }
    }

    /**
     * Flies the Core after the point ([x], [y]) moving at ([vx], [vy]) through the production inverse of the flight
     * law, the pointer kept on the screen. Under the 0.6.0 law a pointer laid on the point no longer holds a spot:
     * grip and the slingshot carry the Core round it at speed, far from a pool, a star or its own homing shots.
     */
    private fun follow(state: MutableGameState, x: Float, y: Float, vx: Float, vy: Float) {
        val aim = state.steeringAimFor(
            (x - state.coreX) * FOLLOW_POSITION_GAIN + (vx - state.velocityX) * FOLLOW_VELOCITY_GAIN,
            (y - state.coreY) * FOLLOW_POSITION_GAIN + (vy - state.velocityY) * FOLLOW_VELOCITY_GAIN,
        )
        val distance = aim.distance.coerceIn(FOLLOW_MIN_TETHER, FOLLOW_MAX_TETHER)
        state.updatePointer(
            state.coreX - state.cameraX + state.screenWidth * 0.5f + cos(aim.heading) * distance,
            state.coreY - state.cameraY + state.screenHeight * 0.5f + sin(aim.heading) * distance,
        )
    }

    /**
     * The Seismic arena keeps its rocks, since its verb works near them, so its player flies as a competent player
     * does: it brakes while the Core is faster than the dash-safe speed with a rock within [ROCK_LOOKAHEAD_SECONDS]
     * of travel, and no impact there is lethal.
     */
    private fun tooFastNearRocks(state: MutableGameState): Boolean {
        val speed = state.speed
        if (speed <= state.steadyCursorSpeed(state.content.obstacles.dashSafeReach)) return false
        return ROCKS.any { rock ->
            length(rock.x - state.coreX, rock.y - state.coreY) - rock.radius - state.coreRadius <= speed * ROCK_LOOKAHEAD_SECONDS
        }
    }

    /**
     * The enemy ready to react to [reaction] (its own and the pair's cooldowns): the nearest to the Core, or the
     * farthest within reach where the verb works at range.
     */
    private fun readyTarget(state: MutableGameState, reaction: ReactionId, crowd: Crowd): Enemy? {
        var best: Enemy? = null
        var bestDistance = if (crowd.farthest) -1f else REACH * REACH
        for (enemy in state.enemies) {
            if (!enemy.alive || state.isShifting(enemy)) continue
            if (enemy.status[StatusCell.REACTION_READY_AT] > state.elapsed) continue
            if (enemy.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] > state.elapsed) continue
            val distance = distanceSquared(enemy.x, enemy.y, state.coreX, state.coreY)
            if (distance > REACH * REACH) continue
            if (if (crowd.farthest) distance > bestDistance else distance < bestDistance) {
                best = enemy
                bestDistance = distance
            }
        }
        return best
    }

    /** Player or hostile shots first seen this step. */
    private fun newProjectiles(state: MutableGameState, seen: IdentityHashMap<Projectile, Int>, hostile: Boolean): Int {
        var fresh = 0
        for (shot in state.projectiles) {
            if (shot.hostile != hostile || seen.containsKey(shot)) continue
            seen[shot] = shot.pierce
            fresh++
        }
        return fresh
    }

    /** Lance hits this step: spent pierce on lances still flying plus the hit that consumed a lance. */
    private fun lanceHits(state: MutableGameState, tracked: IdentityHashMap<Projectile, Int>): Int {
        var hits = 0
        val live = IdentityHashMap<Projectile, Boolean>()
        for (shot in state.projectiles) {
            if (shot.hostile || shot.sourceWeapon != WeaponId.NULL_LANCE) continue
            live[shot] = true
            val before = tracked[shot]
            if (before != null) hits += (before - shot.pierce).coerceAtLeast(0)
            tracked[shot] = shot.pierce
        }
        val gone = tracked.keys.filter { it !in live }
        for (shot in gone) {
            // A lance leaves the list early only when a hit spends its last pierce.
            if (shot.life > 0f) hits++
            tracked.remove(shot)
        }
        return hits
    }

    private fun enemySecondsInWells(state: MutableGameState): Int {
        val wells = state.fieldRecords.filter { it.kind == CombatFieldKind.ROT_WELL }
        return state.enemies.count { enemy ->
            enemy.alive && wells.any { well ->
                distanceSquared(well.x, well.y, enemy.x, enemy.y) <= (well.radius + enemy.radius) * (well.radius + enemy.radius)
            }
        }
    }

    private companion object {
        const val ARENA_SECONDS = 60f
        const val CROWD = 14
        const val REACH = 700f
        /** The scripted player moves its hover point (Plasma, Rot Well) to the latest reaction this often. */
        const val HOVER_SECONDS = 2f
        /** The content's magnet floor. */
        const val WEAK_MAGNET = 120f
        /** The following player's gains on the position and velocity error and its tether (the balance pilots' values). */
        const val FOLLOW_POSITION_GAIN = 6f
        const val FOLLOW_VELOCITY_GAIN = 4f
        const val FOLLOW_MIN_TETHER = 65f
        const val FOLLOW_MAX_TETHER = 380f
        /** How far ahead (in seconds of travel) the Seismic arena's player looks for a rock. */
        const val ROCK_LOOKAHEAD_SECONDS = 0.3f
        val ROCKS = arrayOf(
            testObstacle(260f, 0f, 50f), testObstacle(-260f, 0f, 50f), testObstacle(0f, 260f, 50f), testObstacle(0f, -260f, 50f),
            testObstacle(180f, 180f, 40f), testObstacle(-180f, -180f, 40f),
        )
    }
}
