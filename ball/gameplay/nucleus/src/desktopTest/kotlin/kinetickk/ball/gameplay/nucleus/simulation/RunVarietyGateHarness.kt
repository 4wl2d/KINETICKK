// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.gameplay.nucleus.model.EngagementSecretMask
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Shared harness of the run-variety gates (variety.md §11.2-§11.5) and their calibration probe. Whole runs play like
 * NoPoiPlaythroughTest: a fresh profile with the reference armory, simulation speed 1, 0.02 s steps until the run ends
 * or 900 s, cues taken every step. The omen arms force their omen right after the start (direct-state runs never arm
 * the offer, so the base arm is No omen); the signal arm flies [SignalSeeker]. The director harness flies the director
 * alone. Seeds are fixed by definition and never picked by outcome; magnitudes are calibrated only on the VARIETY window
 * (plan §8.2, i = 1,601..1,900) or the spec's metric seeds, never on the gate seeds.
 */

/** The run-variety metric seeds (spec §11.2, §11.4): `i * 7_919 + 101` for i = 1..[count]. */
internal fun varietyMetricSeeds(count: Int): List<Int> = (1..count).map { it * 7_919 + 101 }

/** Seeds `i * 7_919 + 13` for i in [first]..[last]; VARIETY calibrates on 1,601..1,900 and never on the gate seeds. */
internal fun varietyWindowSeeds(first: Int, last: Int): List<Int> {
    require(last < 1_001 || first > 1_300) { "calibration never runs on the gate seeds (1,001..1,300)" }
    return (first..last).map { it * 7_919 + 13 }
}

/** One fresh run as the run-variety gates read it. */
internal data class VarietyRun(
    val seed: Int,
    val phase: GamePhase,
    val seconds: Float,
    /** Run Matter at the end: what the run banks. */
    val matter: Long,
    /** The level at 600 s, or at the end of a run that ended before. */
    val levelAt600: Int,
    val signalsSpawned: Int,
    val signalsEngaged: Int,
    /** The card the seeker took at each signal prompt, in order. */
    val picks: List<SignalOption>,
    /** The kinds of the signals the run spawned, in order. */
    val signalKinds: List<SignalKind>,
    /** The run opted into an optional site that is not an event signal. */
    val touchedSite: Boolean,
) {
    val won: Boolean get() = phase == GamePhase.VICTORY
}

/**
 * One fresh run on [seed] with [content], [omen] forced at the start (null: No omen) and the NoPoi bot, or the
 * [SignalSeeker] when [seek]; the run stops at its end, at 900 s, or at [until].
 */
internal fun flyVarietyRun(
    seed: Int,
    content: GameplayContentSnapshot = canonicalGameplayContent,
    omen: OmenId? = null,
    seek: Boolean = false,
    until: Float = 900f,
    observe: (MutableGameState) -> Unit = {},
): VarietyRun {
    val state = MutableGameState(content, seed = seed)
    state.unlockWeaponsForTesting(ReferenceArmory)
    state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
    state.startRun()
    state.forceRunOmenForTesting(omen)
    val bot = NoPoiBot()
    val seeker = if (seek) SignalSeeker(bot) else null
    var levelAt600 = -1
    var touchedSite = false
    val kinds = ArrayList<SignalKind>(3)
    var spawned = 0
    while (state.elapsed < min(900f, until) && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
        if (state.phase == GamePhase.CHOICE) {
            if (seeker != null) seeker.choose(state) else bot.choose(state)
        } else {
            if (seeker != null) seeker.control(state) else bot.control(state)
            state.update(0.02f)
        }
        if (state.signalsSpawned != spawned) {
            spawned = state.signalsSpawned
            state.eventSignals.firstOrNull()?.let { kinds += it.kind }
        }
        if (levelAt600 < 0 && state.elapsed >= 600f) levelAt600 = state.level
        touchedSite = touchedSite || state.optionalSitesTouchedOtherThanSignals()
        observe(state)
        state.takeSoundCues()
        state.takeVisualFxCues()
    }
    return VarietyRun(
        seed, state.phase, state.elapsed, state.runMatter, if (levelAt600 < 0) state.level else levelAt600, state.signalsSpawned,
        state.signalsEngaged, seeker?.picks.orEmpty().toList(), kinds, touchedSite,
    )
}

/**
 * The No omen NoPoi runs on [PopulationSeeds], measured once per test JVM: the base of the omen arms and of the signal
 * gate.
 */
internal val VarietyBasePopulation: List<VarietyRun> by lazy { populationOf(PopulationSeeds) { flyVarietyRun(it) } }

/** The share of runs that won. */
internal fun List<VarietyRun>.winShare(): Double = shareOf { it.won }

/** The median run length (the upper middle of an even count). */
internal fun List<VarietyRun>.medianSeconds(): Double = map { it.seconds }.gateMedian().toDouble()

/** The median banked Matter (the upper middle of an even count). */
internal fun List<VarietyRun>.medianMatter(): Double = map { it.matter }.sorted()[size / 2].toDouble()

/** The mean level at 600 s. */
internal fun List<VarietyRun>.meanLevelAt600(): Double = sumOf { it.levelAt600.toDouble() } / size

/** Signals the runs touched over the signals they spawned (0 for none). */
internal fun List<VarietyRun>.reachShare(): Double =
    sumOf { it.signalsSpawned }.let { spawned -> if (spawned == 0) 0.0 else sumOf { it.signalsEngaged }.toDouble() / spawned }

/** [optionalSitesTouched] without the event-signal clause (a seeker engages signals by design). */
private fun MutableGameState.optionalSitesTouchedOtherThanSignals(): Boolean =
    activeWindowSite() != null || directedReward != null || pendingDirectedRewards.isNotEmpty() || openedSiteIds.isNotEmpty() ||
        (runSecretFlags and EngagementSecretMask) != 0 || secretSigil?.active == true || structureTouched() || dimensionTouched()

/**
 * The engaging pilot of the signal gate (spec §11.5): the NoPoi bot, which steers to each live event signal once no
 * engagement runs and takes Merchant's Rare offer when it can pay, Shrine's Blood blessing above 0.6 of its maximum
 * integrity, Gamble's reroll wager, the Cursed cache's Sealed crate, and Derelict's Patch below 0.7 of its maximum
 * integrity or its cargo otherwise; a card it cannot take, or a Shrine below the line, is Ignored. Every other choice is
 * the bot's. The approach asks the production inverse (steeringAimFor) for the acceleration toward the velocity it
 * wants, with the bot's avoidance of every other optional site, of solid bodies and hazards ahead and of hostile shots,
 * and slows below the speed at which a rock starts to hurt when one lies on its course.
 */
internal class SignalSeeker(private val bot: NoPoiBot = NoPoiBot()) {
    val picks = ArrayList<SignalOption>()
    private val marks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)

    fun choose(s: MutableGameState) {
        if (s.choiceType != ChoiceType.SIGNAL) return bot.choose(s)
        val option = pick(s)
        picks += option
        s.choose(s.choices.indexOfFirst { it.signalOption == option })
    }

    private fun pick(s: MutableGameState): SignalOption {
        val want = when (s.choices.firstNotNullOfOrNull { it.signalKind }) {
            SignalKind.MERCHANT -> SignalOption.MERCHANT_RARE_OFFER
            SignalKind.SHRINE -> if (s.hp > SHRINE_LINE * s.maxHp) SignalOption.SHRINE_BLOOD else SignalOption.IGNORE
            SignalKind.GAMBLE -> SignalOption.GAMBLE_REROLL
            SignalKind.CURSED_CACHE -> SignalOption.CACHE_CRATE
            SignalKind.DERELICT -> if (s.hp < PATCH_LINE * s.maxHp) SignalOption.DERELICT_REPAIR else SignalOption.DERELICT_CARGO
            null -> SignalOption.IGNORE
        }
        return if (s.choices.any { it.signalOption == want && !it.locked }) want else SignalOption.IGNORE
    }

    fun control(s: MutableGameState) {
        val held = atan2(s.cameraY + s.pointerY - s.screenHeight * 0.5f - s.coreY, s.cameraX + s.pointerX - s.screenWidth * 0.5f - s.coreX)
        bot.control(s)
        if (s.phase != GamePhase.RUNNING || s.engagementActive()) return
        val signal = s.eventSignals.firstOrNull() ?: return
        val distance = max(1f, length(signal.x - s.coreX, signal.y - s.coreY))
        val speed = s.steadyCursorSpeed(APPROACH_REACH)
        var desiredX = (signal.x - s.coreX) / distance * speed
        var desiredY = (signal.y - s.coreY) / distance * speed
        val count = s.optionalSiteMarks(marks)
        for (mark in 0 until count) {
            val base = mark * 3
            if (marks[base] == signal.x && marks[base + 1] == signal.y) continue
            val px = s.coreX + s.velocityX * 0.35f - marks[base]
            val py = s.coreY + s.velocityY * 0.35f - marks[base + 1]
            val clearance = marks[base + 2] + SITE_CLEARANCE
            val gap = max(1f, length(px, py))
            if (gap < clearance) {
                desiredX += px / gap * (clearance - gap) * 8f
                desiredY += py / gap * (clearance - gap) * 8f
            }
        }
        val hurting = s.obstacleDamageStartSpeed()
        val fast = s.speed > hurting
        val lookahead = if (fast) 0.45f else 0.3f
        var rockAhead = false
        for (obstacle in s.obstacleField.obstacles) {
            val reach = s.obstacleHazardReach(obstacle)
            if (reach <= 0f) continue
            val px = s.coreX + s.velocityX * lookahead - obstacle.x
            val py = s.coreY + s.velocityY * lookahead - obstacle.y
            val clearance = reach + s.coreRadius + if (obstacle.kind.solid) 110f else 40f
            val gap = max(1f, length(px, py))
            if (gap < clearance) {
                desiredX += px / gap * (clearance - gap) * 6f
                desiredY += py / gap * (clearance - gap) * 6f
            }
            if (fast && obstacle.kind.solid && !rockAhead) {
                rockAhead = segmentCircleIntersects(
                    s.coreX, s.coreY, s.coreX + s.velocityX * 0.6f, s.coreY + s.velocityY * 0.6f,
                    obstacle.x, obstacle.y, reach + s.coreRadius + 60f,
                )
            }
        }
        if (rockAhead) {
            val desired = length(desiredX, desiredY)
            val cap = hurting * 0.9f
            if (desired > cap) {
                desiredX *= cap / desired
                desiredY *= cap / desired
            }
        }
        for (shot in s.projectiles) if (shot.hostile) {
            val px = shot.x - s.coreX
            val py = shot.y - s.coreY
            val vx = shot.vx - s.velocityX
            val vy = shot.vy - s.velocityY
            val time = -(px * vx + py * vy) / max(1f, vx * vx + vy * vy)
            if (time in 0f..0.45f && length(px + vx * time, py + vy * time) < 80f) {
                val relative = max(1f, length(vx, vy))
                val side = if (px * -vy + py * vx > 0f) -1f else 1f
                desiredX += -vy / relative * side * 380f
                desiredY += vx / relative * side * 380f
            }
        }
        val aim = s.steeringAimFor(
            (desiredX - s.velocityX) * STEERING_GAIN + s.velocityX * s.dragCoefficient,
            (desiredY - s.velocityY) * STEERING_GAIN + s.velocityY * s.dragCoefficient,
        )
        place(s, held, aim.heading, aim.distance)
    }

    /**
     * Holds the pointer for the aim, swinging around the Core at most the bot's 0.45 rad a frame from [held] and staying
     * inside the area where steering keeps polarity, at least 65 dp from the Core (the SITES pilot's placement).
     */
    private fun place(s: MutableGameState, held: Float, heading: Float, distance: Float) {
        for (bound in floatArrayOf(CURSOR_SWING, EMERGENCY_SWING)) {
            val target = held + turn(heading, held).coerceIn(-bound, bound)
            var step = 0
            while (step * SWING_STEP <= 2f * bound) {
                val angle = target + ((step + 1) / 2) * SWING_STEP * if (step % 2 == 1) 1f else -1f
                step++
                if (abs(turn(angle, held)) > bound + 1e-4f) continue
                val room = room(s, angle)
                val need = MIN_TETHER + max(0f, s.velocityX * cos(angle) + s.velocityY * sin(angle)) * 0.06f
                if (room >= need) {
                    hold(s, angle, distance.coerceIn(need, room))
                    return
                }
            }
        }
        val centre = atan2(s.cameraY - s.coreY, s.cameraX - s.coreX)
        hold(s, centre, max(MIN_TETHER, min(distance, room(s, centre))))
    }

    private fun room(s: MutableGameState, heading: Float): Float {
        val unitX = cos(heading)
        val unitY = sin(heading)
        val offsetX = s.coreX - s.cameraX
        val offsetY = s.coreY - s.cameraY
        val halfWidth = s.screenWidth * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val halfHeight = s.screenHeight * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val availableX = if (unitX > 0f) (halfWidth - offsetX) / unitX else if (unitX < 0f) (-halfWidth - offsetX) / unitX else 10_000f
        val availableY = if (unitY > 0f) (halfHeight - offsetY) / unitY else if (unitY < 0f) (-halfHeight - offsetY) / unitY else 10_000f
        return min(availableX, availableY)
    }

    private fun hold(s: MutableGameState, heading: Float, distance: Float) {
        s.updatePointer(
            s.screenWidth * 0.5f + s.coreX - s.cameraX + cos(heading) * distance,
            s.screenHeight * 0.5f + s.coreY - s.cameraY + sin(heading) * distance,
        )
    }

    private fun turn(to: Float, from: Float): Float = atan2(sin(to - from), cos(to - from))

    private companion object {
        const val SHRINE_LINE = 0.6f
        const val PATCH_LINE = 0.7f
        /** The cursor reach whose steady speed the seeker flies toward a signal (the SITES pilot's approach). */
        const val APPROACH_REACH = 0.45f
        const val SITE_CLEARANCE = 295f
        const val STEERING_GAIN = 5f
        const val CURSOR_SWING = 0.45f
        const val EMERGENCY_SWING = 2.0f
        const val SWING_STEP = 0.1f
        const val MIN_TETHER = 65f
    }
}

/** One director-only run of the budget gate: what the director spawned in the measured window and its events. */
internal data class DirectorRun(
    val seed: Int,
    /** Cap units the ambient director spawned in [DIRECTOR_WINDOW]. */
    val capUnits: Double,
    /** Spawn integrity of those enemies. */
    val integrity: Double,
    /** Spawn events fired in the first [DIRECTOR_EVENT_SECONDS] seconds, and their distinct kinds. */
    val events: Int,
    val eventKinds: Set<SpawnEventKind>,
    /** Steps the run spent outside RUNNING (a choice would hold the director); a valid flight has none. */
    val heldSteps: Int,
)

/** The budget gate's measured window (spec §11.2: [60, 300] s) and the span its event checks read. */
internal val DIRECTOR_WINDOW: ClosedFloatingPointRange<Float> = 60f..300f
internal const val DIRECTOR_EVENT_SECONDS: Float = 240f

/**
 * The director alone on [seed] at tier 0 with [personality] forced (null: the plain director) on [content] (spec
 * §11.2): the Core flies a 600 u/s circle of 900 u and never takes damage, the world's other systems are quiet, an enemy
 * reaching the Core (|dx| + |dy| under 320 u, SpawnDirectorPacingTest's reach) is cleared like a kill without paying
 * one, and every enemy is removed [lifetime] seconds after it appears (checked each second), so the ordinary cap never
 * binds and every spawn is the budget's. Without the contact clearing the Core rams kills, levels up, and its level
 * choice holds the director for the rest of the run. Counts the ambient director's ordinary spawns (no encounter
 * member, Elite, Herald or boss class) in [DIRECTOR_WINDOW] by cap units and spawn integrity, and the spawn events of
 * the first [DIRECTOR_EVENT_SECONDS] seconds.
 */
internal fun flyDirector(
    seed: Int,
    personality: DirectorPersonalityId?,
    content: GameplayContentSnapshot = canonicalGameplayContent,
    lifetime: Float = ENEMY_LIFETIME,
    /** Receives `type@x,y@t` of every enemy the first [DIRECTOR_EVENT_SECONDS] seconds bring, in arrival order. */
    timeline: MutableList<String>? = null,
): DirectorRun {
    val state = MutableGameState(content, seed = seed, initialMatter = 0).apply {
        resize(1_280f, 720f)
        startRun()
        forcePersonalityForTesting(personality)
        invulnerableUntil = Float.MAX_VALUE
        runGrace = Float.MAX_VALUE
    }
    val bornAt = HashMap<Int, Float>()
    var capUnits = 0.0
    var integrity = 0.0
    var events = 0
    var seen = state.spawnEventCount
    val kinds = HashSet<SpawnEventKind>()
    var nextSweep = 1f
    var angle = 0f
    var held = 0
    while (state.elapsed < DIRECTOR_WINDOW.endInclusive) {
        angle += CIRCLE_SPEED / CIRCLE_RADIUS * MutableGameState.FIXED_STEP
        state.coreX = cos(angle) * CIRCLE_RADIUS
        state.coreY = sin(angle) * CIRCLE_RADIUS
        state.velocityX = -sin(angle) * CIRCLE_SPEED
        state.velocityY = cos(angle) * CIRCLE_SPEED
        state.updatePointer(640f - sin(angle) * 260f, 360f + cos(angle) * 260f)
        state.simulateStep(MutableGameState.FIXED_STEP)
        if (state.phase != GamePhase.RUNNING) held++
        for (enemy in state.enemies) {
            if (bornAt.containsKey(enemy.id)) continue
            bornAt[enemy.id] = state.elapsed
            if (timeline != null && state.elapsed <= DIRECTOR_EVENT_SECONDS) {
                timeline += "${enemy.type}@${enemy.x.toInt()},${enemy.y.toInt()}@${(state.elapsed * 50f).toInt()}"
            }
            if (enemy.ownerTag != 0L || enemy.isBossClass || enemy.type == EnemyType.ELITE || enemy.type == EnemyType.HERALD) continue
            if (state.elapsed !in DIRECTOR_WINDOW) continue
            capUnits += capCost(enemy.type)
            integrity += enemy.maxHp
        }
        if (state.spawnEventCount != seen) {
            seen = state.spawnEventCount
            if (state.elapsed <= DIRECTOR_EVENT_SECONDS) {
                events++
                kinds += SpawnEventKind.entries[state.lastSpawnEventKind]
            }
        }
        state.enemies.removeAll { enemy -> abs(enemy.x - state.coreX) + abs(enemy.y - state.coreY) < CONTACT_REACH && !enemy.isMiniBoss }
        if (state.elapsed >= nextSweep) {
            nextSweep += 1f
            state.enemies.removeAll { enemy -> !enemy.isMiniBoss && state.elapsed - (bornAt[enemy.id] ?: state.elapsed) > lifetime }
        }
        state.pickups.clear()
        state.quietArenaForTesting()
        state.takeVisualFxCues()
        state.takeSoundCues()
    }
    return DirectorRun(seed, capUnits, integrity, events, kinds, held)
}

private const val CIRCLE_SPEED = 600f
private const val CIRCLE_RADIUS = 900f
private const val ENEMY_LIFETIME = 6f

/** An enemy this close to the Core (|dx| + |dy|) has reached it (SpawnDirectorPacingTest's reach). */
private const val CONTACT_REACH = 320f
