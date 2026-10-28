// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.DimensionProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * The opt-in pilot of the dimension gates and the harness that flies one visit. The reference NoPoi bot plays the run
 * (choices, threat Dashes, skills, the Brake, every optional site it skips) and the pilot takes the pointer only for
 * what a dimension asks of a player: the rift it was sent through, then the visit's objective as the HUD shows it
 * (the next course ring, the nearest ember, the objective boss, the exit rift). It reads the visit through the render
 * projection a player sees and inverts its wanted acceleration through the production steering inverse, so it follows
 * the flight law the run uses. It steers around obstacles, hostile shots and every skipped optional site as the bot
 * does, and around nothing a dimension gate measures.
 */

/** How fast the pilot flies at a rift it was sent through (a gentle cruise: the rift is 560 u ahead). */
private const val RIFT_APPROACH_SPEED = 650f

/** Spec §12.3: an Ascent flies its rings at this share of the pace, a Nadir step never below the other share. */
private const val ASCENT_PACE_SHARE = 1.2f
private const val NADIR_PACE_SHARE = 1.15f

/** The pilot's cruise to an exit rift outside the Nadir. */
private const val EXIT_SPEED = 650f

/** The NoPoi bot's pursuit gain on the velocity error, its tether bounds and its cursor turn per 20 ms input. */
private const val VELOCITY_GAIN = 5f
private const val MIN_TETHER = 65f
private const val MAX_TETHER = 400f
private const val CURSOR_TURN = 0.45f

/** The NoPoi bot's boss engagement: its orbit radius and speed with the Flux Wake, else with an arc weapon. */
private const val WAKE_BOSS_RADIUS = 100f
private const val WAKE_BOSS_SPEED = 350f
private const val ARC_BOSS_RADIUS = 300f
private const val ARC_BOSS_SPEED = 460f

/** The NoPoi bot's hunt orbit around its nearest target with an arc weapon (others fly through their target). */
private const val ARC_HUNT_RADIUS = 280f

/** The pilot brakes only when it runs this far above the speed it wants, and lets go again under the second share. */
private const val BRAKE_ABOVE_SHARE = 1.6f
private const val BRAKE_RELEASE_SHARE = 1.25f

/** The NoPoi bot's berths: optional sites from the Core 0.35 s ahead, obstacles 0.3 s ahead, and the hostile-shot dodge. */
private const val SITE_CLEARANCE_MARGIN = 295f
private const val SOLID_CLEARANCE_MARGIN = 110f
private const val HAZARD_CLEARANCE_MARGIN = 40f

/** A Dash the bot asked for is kept only when no solid body lies this long along the pilot's Dash line. */
private const val DASH_LOOKAHEAD_SECONDS = 0.3f

/**
 * The NoPoi bot's Blink rule: with Blink Drive a Dash may teleport up to [BLINK_LANDING_REACH] along the pointer, so it
 * is kept only when that line passes at least [SITE_GUARD_MARGIN] outside every skipped optional site's radius.
 */
private const val BLINK_LANDING_REACH = 320f
private const val SITE_GUARD_MARGIN = 85f

/**
 * Flies a run with [bot] and, while [target]'s rift is offered and once inside it, the visit. [takeRefuse] takes the
 * Refuse card of a Pact offer instead of the first card.
 */
internal class DimensionPilot(
    val target: WorldLayer,
    private val bot: NoPoiBot = NoPoiBot(),
    private val takeRefuse: Boolean = false,
) {
    /**
     * Set by the harness once the pilot's rift is due: only then does it fly to a rift (a rift the game offers on its own
     * stays a skipped site), and until the rift opens it fights whatever boss-class enemy would keep it locked.
     */
    var riftDue = false

    private var view: DimensionProjection? = null
    private val marks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)
    private var steering = false
    private var cursorAngle = 0f
    private var braking = false

    // The goal of this input: a point to fly through (orbit 0) or to circle, the wanted speed, and whether the point is
    // the rift the pilot was sent through (its optional-site mark is then no berth).
    private var goalX = 0f
    private var goalY = 0f
    private var goalSpeed = 0f
    private var goalOrbit = 0f
    private var goalIsRift = false

    fun choose(s: MutableGameState) {
        if (takeRefuse && s.choiceType == ChoiceType.BOON) {
            val refuse = s.choices.indexOfFirst { it.boonId == null }
            if (refuse >= 0) {
                s.choose(refuse)
                return
            }
        }
        bot.choose(s)
    }

    fun control(s: MutableGameState) {
        val dashBefore = s.dashBufferTime
        bot.control(s)
        val projection = s.dimensionProjection(view, null).also { view = it }
        if (pickGoal(s, projection)) steer(s, dashBefore) else if (steering) handBack(s, dashBefore)
    }

    /**
     * Gives the pointer back to the bot at the cursor's turn rate: the bot keeps its own cursor while the pilot steers,
     * and a jump between the two could swing the singularity across the Core (a swept contact ends the run).
     */
    private fun handBack(s: MutableGameState, dashBefore: Float) {
        val offsetX = s.coreX - s.cameraX
        val offsetY = s.coreY - s.cameraY
        val tetherX = s.pointerX - s.screenWidth * 0.5f - offsetX
        val tetherY = s.pointerY - s.screenHeight * 0.5f - offsetY
        val turn = atan2(sin(atan2(tetherY, tetherX) - cursorAngle), cos(atan2(tetherY, tetherX) - cursorAngle))
        if (abs(turn) <= CURSOR_TURN) {
            steering = false
            return
        }
        cursorAngle += turn.coerceIn(-CURSOR_TURN, CURSOR_TURN)
        val tether = length(tetherX, tetherY).coerceAtLeast(MIN_TETHER)
        s.updatePointer(s.screenWidth * 0.5f + offsetX + cos(cursorAngle) * tether, s.screenHeight * 0.5f + offsetY + sin(cursorAngle) * tether)
        if (s.dashBufferTime > dashBefore && !dashAllowed(s, cos(cursorAngle), sin(cursorAngle), skipGoalRift = false)) {
            s.dashBufferTime = dashBefore
        }
    }

    /** Sets the goal of this input; false leaves the bot's input standing. */
    private fun pickGoal(s: MutableGameState, view: DimensionProjection): Boolean {
        if (view.layer == WorldLayer.BASE) return pickRift(s, view)
        val pace = view.pace
        val nadir = view.layer == WorldLayer.NADIR
        val floor = if (nadir) pace * NADIR_PACE_SHARE else 0f
        val exit = view.exitPortal
        if (view.completed && exit != null) return goal(exit.x, exit.y, max(EXIT_SPEED, floor))
        when (view.objective) {
            DimensionObjective.ASCENT -> {
                val ring = view.halos.firstOrNull { it.id == view.nextHaloId }
                if (ring != null) return goal(ring.x, ring.y, max(pace * ASCENT_PACE_SHARE, floor))
            }
            DimensionObjective.HARVEST -> {
                val speed = max(pace * NADIR_PACE_SHARE, floor)
                var best = Float.POSITIVE_INFINITY
                for (ember in view.embers) {
                    val distance = length(ember.x - s.coreX, ember.y - s.coreY)
                    if (distance / speed >= ember.remaining || distance >= best) continue
                    best = distance
                    goalX = ember.x
                    goalY = ember.y
                }
                if (best.isFinite()) return goal(goalX, goalY, speed)
            }
            DimensionObjective.JUDGEMENT, DimensionObjective.PYRE, DimensionObjective.ANTECEDENT -> {
                val boss = s.enemies.firstOrNull { it.id == view.bossId && it.alive }
                if (boss != null) return engage(s, boss, floor)
            }
            null -> Unit
        }
        return nadir && hunt(s, floor)
    }

    /**
     * Once its rift is due and while it was not entered: the rift when it is offered and open to the Core, else the
     * boss-class enemy that keeps it locked.
     */
    private fun pickRift(s: MutableGameState, view: DimensionProjection): Boolean {
        if (!riftDue || s.dimensionRun.visitedMask and target.bit != 0) return false
        val rift = if (target == WorldLayer.ARCHIVE) {
            view.glitchPortal?.let { it.x to it.y }
        } else {
            s.sites.firstOrNull { it.kind.riftTarget() == target }?.let { it.x to it.y }
        }
        if (rift != null && view.lockedRifts and target.bit == 0) return goal(rift.first, rift.second, RIFT_APPROACH_SPEED, rift = true)
        return lockerEngaged(s)
    }

    /** A boss-class enemy keeps a rift locked (entering would delete it): the pilot fights it as the bot fights a boss. */
    private fun lockerEngaged(s: MutableGameState): Boolean {
        val locker = s.enemies.firstOrNull { it.alive && it.isBossClass } ?: return false
        return engage(s, locker, 0f)
    }

    /** The bot's boss engagement on [boss]: a tight orbit with the Flux Wake, a wider one with an arc weapon. */
    private fun engage(s: MutableGameState, boss: Enemy, floor: Float): Boolean {
        val wake = s.weapon == WeaponId.FLUX_WAKE
        return goal(
            boss.x, boss.y,
            max(if (wake) WAKE_BOSS_SPEED else ARC_BOSS_SPEED, floor),
            orbit = if (wake) WAKE_BOSS_RADIUS else ARC_BOSS_RADIUS,
        )
    }

    /** The Nadir with nothing to fly to: the bot's nearest target (an Elite counts 250 u nearer), kept at [speed]. */
    private fun hunt(s: MutableGameState, speed: Float): Boolean {
        val prey = s.enemies.filter { it.alive }.minByOrNull {
            length(it.x - s.coreX, it.y - s.coreY) - if (it.type == EnemyType.ELITE) 250f else 0f
        }
        if (prey != null) {
            val arc = s.weapon in listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.PRISM_RELAY)
            return goal(prey.x + prey.vx * 0.3f, prey.y + prey.vy * 0.3f, speed, orbit = if (arc) ARC_HUNT_RADIUS else 0f)
        }
        val heading = s.travelHeading()
        return goal(s.coreX + cos(heading) * 1_000f, s.coreY + sin(heading) * 1_000f, speed)
    }

    private fun goal(x: Float, y: Float, speed: Float, orbit: Float = 0f, rift: Boolean = false): Boolean {
        goalX = x
        goalY = y
        goalSpeed = speed
        goalOrbit = orbit
        goalIsRift = rift
        return true
    }

    /** The bot's pursuit toward the goal with its berths, inverted through the production steering law. */
    private fun steer(s: MutableGameState, dashBefore: Float) {
        val toX = goalX - s.coreX
        val toY = goalY - s.coreY
        val distance = max(1f, length(toX, toY))
        var desiredX: Float
        var desiredY: Float
        if (goalOrbit > 0f) {
            // The bot's orbit: around the goal at the wanted speed, drawn back toward the orbit's radius.
            val nx = -toX / distance
            val ny = -toY / distance
            val radial = ((goalOrbit - distance) * 2.4f).coerceIn(-480f, 480f)
            desiredX = -ny * goalSpeed + nx * radial
            desiredY = nx * goalSpeed + ny * radial
        } else {
            desiredX = toX / distance * goalSpeed
            desiredY = toY / distance * goalSpeed
        }
        val siteCount = s.optionalSiteMarks(marks)
        for (mark in 0 until siteCount) {
            val base = mark * 3
            if (goalIsRift && marks[base] == goalX && marks[base + 1] == goalY) continue
            val px = s.coreX + s.velocityX * 0.35f - marks[base]
            val py = s.coreY + s.velocityY * 0.35f - marks[base + 1]
            var clearance = marks[base + 2] + SITE_CLEARANCE_MARGIN
            // A rift inside another skipped site's berth (a glitch rift beside a structure) would hold the Core where the
            // berth balances the approach: the berth then reaches only to the rift.
            if (goalIsRift) clearance = min(clearance, length(goalX - marks[base], goalY - marks[base + 1]))
            val pd = max(1f, length(px, py))
            if (pd < clearance) {
                desiredX += px / pd * (clearance - pd) * 8f
                desiredY += py / pd * (clearance - pd) * 8f
            }
        }
        for (obstacle in s.obstacleField.obstacles) {
            val reach = s.obstacleHazardReach(obstacle)
            if (reach <= 0f) continue
            val px = s.coreX + s.velocityX * 0.3f - obstacle.x
            val py = s.coreY + s.velocityY * 0.3f - obstacle.y
            val clearance = reach + s.coreRadius + if (obstacle.kind.solid) SOLID_CLEARANCE_MARGIN else HAZARD_CLEARANCE_MARGIN
            val pd = max(1f, length(px, py))
            if (pd < clearance) {
                desiredX += px / pd * (clearance - pd) * 6f
                desiredY += py / pd * (clearance - pd) * 6f
            }
        }
        for (shot in s.projectiles) if (shot.hostile) {
            val px = shot.x - s.coreX
            val py = shot.y - s.coreY
            val vx = shot.vx - s.velocityX
            val vy = shot.vy - s.velocityY
            val time = -(px * vx + py * vy) / max(1f, vx * vx + vy * vy)
            if (time in 0f..0.45f && length(px + vx * time, py + vy * time) < 80f) {
                val shotSpeed = max(1f, length(vx, vy))
                val side = if (px * -vy + py * vx > 0f) -1f else 1f
                desiredX += -vy / shotSpeed * side * 380f
                desiredY += vx / shotSpeed * side * 380f
            }
        }
        // The Brake only reins in a Core far above the wanted speed; the rest of the time it would bleed the pace.
        if (s.speed > goalSpeed * BRAKE_ABOVE_SHARE) braking = true
        if (s.speed < goalSpeed * BRAKE_RELEASE_SHARE) braking = false
        s.setBrake(braking)
        val drag = s.steeringDrag()
        val aim = s.steeringAimFor(
            (desiredX - s.velocityX) * VELOCITY_GAIN + s.velocityX * drag,
            (desiredY - s.velocityY) * VELOCITY_GAIN + s.velocityY * drag,
        )
        val offsetX = s.coreX - s.cameraX
        val offsetY = s.coreY - s.cameraY
        // Taking over, the cursor starts where the bot left it.
        if (!steering) {
            cursorAngle = atan2(s.pointerY - s.screenHeight * 0.5f - offsetY, s.pointerX - s.screenWidth * 0.5f - offsetX)
            steering = true
        }
        val turn = atan2(sin(aim.heading - cursorAngle), cos(aim.heading - cursorAngle))
        cursorAngle += turn.coerceIn(-CURSOR_TURN, CURSOR_TURN)
        val ux = cos(cursorAngle)
        val uy = sin(cursorAngle)
        // The bot's cursor area: the pointer stays where ordinary steering keeps its polarity.
        val halfWidth = s.screenWidth * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val halfHeight = s.screenHeight * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val availableX = if (ux > 0f) (halfWidth - offsetX) / ux else if (ux < 0f) (-halfWidth - offsetX) / ux else 10_000f
        val availableY = if (uy > 0f) (halfHeight - offsetY) / uy else if (uy < 0f) (-halfHeight - offsetY) / uy else 10_000f
        val tether = min(aim.distance.coerceIn(MIN_TETHER, MAX_TETHER), min(availableX, availableY)).coerceAtLeast(MIN_TETHER)
        s.updatePointer(s.screenWidth * 0.5f + offsetX + ux * tether, s.screenHeight * 0.5f + offsetY + uy * tether)
        // A threat Dash the bot asked for now flies along this pointer: dropped when it would ram a solid body at speed or
        // Blink onto a skipped site.
        if (s.dashBufferTime > dashBefore && !dashAllowed(s, ux, uy, skipGoalRift = goalIsRift)) s.dashBufferTime = dashBefore
    }

    /**
     * The bot's two Dash rules applied along the pilot's pointer (ux, uy): no solid body on the Dash line at the lethal
     * speed, and with Blink Drive no Blink landing on a skipped optional site (the rift the pilot flies to is no skipped
     * site when [skipGoalRift]). The bot checks both along its own cursor, which the pilot has replaced.
     */
    private fun dashAllowed(s: MutableGameState, ux: Float, uy: Float, skipGoalRift: Boolean): Boolean {
        if (!dashLineClear(s, ux, uy)) return false
        if (s.relicRank(RelicId.BLINK_DRIVE) <= 0) return true
        val endX = s.coreX + ux * BLINK_LANDING_REACH
        val endY = s.coreY + uy * BLINK_LANDING_REACH
        val count = s.optionalSiteMarks(marks)
        for (mark in 0 until count) {
            val base = mark * 3
            if (skipGoalRift && marks[base] == goalX && marks[base + 1] == goalY) continue
            if (segmentCircleIntersects(s.coreX, s.coreY, endX, endY, marks[base], marks[base + 1], marks[base + 2] + SITE_GUARD_MARGIN)) {
                return false
            }
        }
        return true
    }

    /** False when a Dash along (ux, uy) reaches the lethal obstacle speed with a solid body on its line. */
    private fun dashLineClear(s: MutableGameState, ux: Float, uy: Float): Boolean {
        val dashSpeed = length(s.velocityX + ux * s.effectiveDashImpulse(), s.velocityY + uy * s.effectiveDashImpulse())
        if (dashSpeed < s.obstacleLethalSpeed()) return true
        val endX = s.coreX + ux * dashSpeed * DASH_LOOKAHEAD_SECONDS
        val endY = s.coreY + uy * dashSpeed * DASH_LOOKAHEAD_SECONDS
        for (obstacle in s.obstacleField.obstacles) {
            if (!obstacle.kind.solid) continue
            if (segmentCircleIntersects(s.coreX, s.coreY, endX, endY, obstacle.x, obstacle.y, obstacle.radius + s.coreRadius)) return false
        }
        return true
    }
}

/** A starting profile of the dimension gates, with the bot that plays it and the Relics bound at the start. */
internal enum class DimensionProfile(val label: String) {
    /** Gate A's fresh tier-0 profile: no Matrix rank, the Orb and the Flux Wake. */
    FRESH_TIER_0("fresh-t0"),

    /** Gate D's strong build at tier 10: every Matrix rank, the Pentagon with Arc Coil and four Relics bound. */
    STRONG_TIER_10("strong-t10"),

    /** Gate B's profile at tier 3: every Matrix rank and the Pentagon with its signature Arc Coil, no key harmony. */
    SIGNATURE_TIER_3("signature-t3");

    fun snapshot(content: GameplayContentSnapshot): GameplayProfileSnapshot = when (this) {
        FRESH_TIER_0 -> gateProfile(GateMatrix.NONE, tier = 0, content = content)
        STRONG_TIER_10 -> gateProfile(GateMatrix.FULL, tier = 10, CoreShape.PENTAGON, WeaponId.ARC_COIL, content = content)
        SIGNATURE_TIER_3 -> gateProfile(GateMatrix.FULL, tier = 3, CoreShape.PENTAGON, WeaponId.ARC_COIL, content = content)
    }

    val relics: List<RelicId>
        get() = if (this == STRONG_TIER_10) {
            listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN)
        } else {
            emptyList()
        }

    fun bot(): NoPoiBot = NoPoiBot(avoidWeaponHarmony = this == SIGNATURE_TIER_3)
}

/** One run of a dimension gate: where it went, what the visit did and how it ended. */
internal data class DimensionVisitRun(
    val seed: Int,
    val target: WorldLayer,
    /** The objective the visit played (forced or rolled), or null without a visit. */
    val objective: DimensionObjective?,
    /** False when the run ended before its rift opened or the rift closed unentered. */
    val entered: Boolean,
    /** The second the rift opened (NaN when it never did). */
    val openedAt: Float,
    val enteredAt: Float,
    val completed: Boolean,
    /** Seconds from the entry to the completion (NaN when not completed). */
    val completionSeconds: Float,
    /** Integrity and shield lost inside the visit, and the part the Nadir's scorch took of it. */
    val visitDamage: Float,
    val scorchDamage: Float,
    /** The run's end: its phase and message, and the second it stopped. */
    val phase: GamePhase,
    val reason: String,
    val seconds: Float,
    /** Integrity share at the return (NaN without a return). */
    val integrityAtReturn: Float,
    /** The largest share of its integrity the objective boss lost during the visit (0 without one). */
    val bossLost: Float = 0f,
) {
    /** Alive when the harness stopped (a visit's run stops five seconds after the return). */
    val survived: Boolean get() = phase != GamePhase.GAME_OVER
    val won: Boolean get() = phase == GamePhase.VICTORY

    override fun toString(): String =
        "seed=$seed target=$target objective=$objective entered=$entered openedAt=${"%.2f".format(openedAt)} enteredAt=${"%.2f".format(enteredAt)} " +
            "completed=$completed completion=${"%.2f".format(completionSeconds)} damage=${"%.1f".format(visitDamage)} " +
            "scorch=${"%.1f".format(scorchDamage)} integrityAtReturn=${"%.3f".format(integrityAtReturn)} bossLost=${"%.3f".format(bossLost)} phase=$phase " +
            "reason=$reason seconds=${"%.2f".format(seconds)}"
}

/** Seconds a visit's run is flown after the return before it is read (spec §12.3). */
internal const val AFTER_RETURN_SECONDS = 5f

/**
 * Plays [profile] on [seed]: the NoPoi bot until [riftAt], when [target]'s rift opens 560 u ahead (a window rift for
 * the Zenith or the Nadir, the glitch rift for the Archive); the pilot flies through it and the visit, with
 * [objective] forced in place of the rolled one when given. The run stops [AFTER_RETURN_SECONDS] after the return, or
 * at [until] when [runToEnd] (a full run, as the outcome gate reads it). A null [target] plays the run with no rift.
 */
internal fun flyDimensionRun(
    seed: Int,
    profile: DimensionProfile,
    target: WorldLayer?,
    riftAt: Float,
    objective: DimensionObjective? = null,
    takeRefuse: Boolean = false,
    runToEnd: Boolean = false,
    until: Float = 900f,
    content: GameplayContentSnapshot = canonicalGameplayContent,
): DimensionVisitRun {
    val state = MutableGameState(content, seed = seed, bootstrapProgress = profile.snapshot(content))
    state.unlockWeaponsForTesting(ReferenceArmory)
    state.startRun()
    profile.relics.forEach(state::acquireRelicForTesting)
    // Without a target the pilot's rift never comes due, so the bot plays the whole run.
    val pilot = DimensionPilot(target ?: WorldLayer.ZENITH, profile.bot(), takeRefuse)
    var opened = false
    var openedAt = Float.NaN
    val latestRift = if (target == WorldLayer.ARCHIVE) {
        content.dimensions.latestGlitchRiftAt(content.tempo.bossAtSeconds)
    } else {
        content.tempo.bossAtSeconds - content.dimensions.exitMarginSeconds - content.dimensions.longestWindowVisitSeconds -
            content.sites.windows.offerLifetime
    }
    var enteredAt = Float.NaN
    var playedObjective: DimensionObjective? = null
    var completedAt = Float.NaN
    var returnedAt = Float.NaN
    var integrityAtReturn = Float.NaN
    var damage = 0f
    var scorch = 0f
    var bossLost = 0f
    var stopAt = until
    val physics = content.dimensions.definition(target ?: WorldLayer.BASE).physics
    while (state.elapsed < stopAt && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
        if (target != null && !opened && state.phase == GamePhase.RUNNING && state.elapsed >= riftAt) {
            pilot.riftDue = true
            // The rift opens at its time, or once nothing keeps it locked (a Herald the pilot then fights first).
            if (state.dimensionEntryOpen(target) && !state.engagementActive()) {
                state.openPortalForTesting(target, clear = true)
                opened = true
                openedAt = state.elapsed
            } else if (state.elapsed > latestRift) {
                // Never unlocked in time: a visit's run has nothing to measure, a whole run goes on without its rift.
                if (!runToEnd) break
                opened = true
            }
        }
        val inside = target != null && state.worldLayer == target
        val integrityBefore = state.hp + state.shield
        val slowBefore = state.dimensionSlowSeconds
        val clockBefore = state.dimensionScorchClock
        val elapsedBefore = state.elapsed
        if (state.phase == GamePhase.CHOICE) {
            pilot.choose(state)
        } else {
            pilot.control(state)
            state.update(0.02f)
        }
        if (target != null && enteredAt.isNaN() && state.worldLayer == target) {
            enteredAt = state.elapsed
            if (objective != null && state.dimensionRun.visit?.objective != objective) state.forceObjectiveForTesting(objective)
            playedObjective = state.dimensionRun.visit?.objective
        }
        if (inside || state.worldLayer == target) {
            val lost = max(0f, integrityBefore - state.hp - state.shield)
            damage += lost
            if (physics.scorchSharePerSecond > 0f) {
                val ticks = scorchTicks(slowBefore, clockBefore, state, state.elapsed - elapsedBefore, physics.scorchGraceSeconds, physics.scorchTickSeconds)
                val tick = state.maxHp * physics.scorchSharePerSecond * physics.scorchTickSeconds *
                    state.incomingDamageFactor * state.rebirthProfile.incomingDamageMultiplier
                scorch += min(lost, ticks * tick)
            }
            if (completedAt.isNaN() && state.dimensionRun.visit?.completed == true) completedAt = state.elapsed
            if (state.dimensionRun.visit?.objective?.boss != null) bossLost = max(bossLost, state.dimensionTrialProjection()?.progress ?: 0f)
        }
        if (!enteredAt.isNaN() && returnedAt.isNaN() && state.worldLayer == WorldLayer.BASE) {
            returnedAt = state.elapsed
            integrityAtReturn = state.hp / state.maxHp
            if (!runToEnd) stopAt = min(until, returnedAt + AFTER_RETURN_SECONDS)
        }
        // A rift that closed unentered ends a visit's run: nothing is left to measure.
        if (target != null && opened && enteredAt.isNaN() && !runToEnd && !riftOffered(state, target)) break
        state.takeSoundCues()
        state.takeVisualFxCues()
    }
    if (completedAt.isNaN() && state.dimensionRun.completedMask and (target?.bit ?: 0) != 0) completedAt = returnedAt
    return DimensionVisitRun(
        seed, target ?: WorldLayer.BASE, playedObjective, !enteredAt.isNaN(), openedAt, enteredAt, !completedAt.isNaN(),
        completedAt - enteredAt, damage, scorch, state.phase, state.message, state.elapsed, integrityAtReturn, bossLost,
    )
}

/** True while [target]'s rift is still offered in realspace. */
private fun riftOffered(state: MutableGameState, target: WorldLayer): Boolean =
    if (target == WorldLayer.ARCHIVE) state.dimensionRun.glitchPortal != null
    else state.sites.any { it.kind.riftTarget() == target }

/**
 * The scorch ticks of one input frame, read from the visit's slow and scorch clocks before ([slowBefore],
 * [clockBefore]) and after it: the clock runs only past the grace and gives back one tick length per tick.
 */
private fun scorchTicks(
    slowBefore: Float,
    clockBefore: Float,
    after: MutableGameState,
    frame: Float,
    grace: Float,
    tick: Float,
): Int {
    val slowAfter = after.dimensionSlowSeconds
    if (slowAfter <= grace) return 0
    val continued = slowAfter >= slowBefore
    val start = if (continued && slowBefore > grace) clockBefore else 0f
    val pastGrace = if (continued) min(frame, slowAfter - grace) else slowAfter - grace
    return ((start + pastGrace - after.dimensionScorchClock) / tick).roundToInt().coerceAtLeast(0)
}

/** Bootstrap standard errors of the gates and probes: 20,000 resamples with RNG seed 20261027 (policy.md procedure). */
internal const val DIMENSION_BOOTSTRAP_RESAMPLES = 20_000
internal const val DIMENSION_BOOTSTRAP_SEED = 20_261_027

/** [metric] over [runs] and its bootstrap standard error. */
internal fun <T> bootstrapped(runs: List<T>, metric: (List<T>) -> Double): Pair<Double, Double> {
    if (runs.isEmpty()) return Double.NaN to Double.NaN
    val random = Random(DIMENSION_BOOTSTRAP_SEED)
    val values = DoubleArray(DIMENSION_BOOTSTRAP_RESAMPLES) {
        metric(List(runs.size) { runs[random.nextInt(runs.size)] })
    }
    val finite = values.filter { it.isFinite() }
    val mean = finite.average()
    val se = kotlin.math.sqrt(finite.sumOf { (it - mean) * (it - mean) } / max(1, finite.size - 1))
    return metric(runs) to se
}

/** "value(SE se)" with four decimals. */
internal fun <T> describe(runs: List<T>, metric: (List<T>) -> Double): String {
    val (value, se) = bootstrapped(runs, metric)
    return "${"%.4f".format(value)}(SE ${"%.4f".format(se)})"
}

/** The median of the finite values (NaN for none); the upper middle of an even count. */
internal fun List<Float>.finiteMedian(): Double {
    val finite = filter { it.isFinite() }.sorted()
    return if (finite.isEmpty()) Double.NaN else finite[finite.size / 2].toDouble()
}
