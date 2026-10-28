// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.SiteReward
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.CircuitBeacons
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The opt-in site pilot of the site gates (SiteEngagementGateTest, BountyGateTest; spec §14.3) and of the SITES
 * calibration probe. It composes the input-only NoPoi bot: every 0.02 s input frame the bot decides first (its skills,
 * its threat-dodge Dash, its choices), and while no site of the arm's family is offered or running the bot's pointer and
 * Brake stand. Once a window offers the arm's family the pilot flies into that site and plays it, asking the production
 * inverse of the flight law (steeringAimFor) for the acceleration toward the velocity it wants, with the bot's avoidance
 * of every other optional site, of obstacles and (where the arm dodges) of hostile shots; the site it plays is the only
 * one it does not steer around, and it avoids nothing the gates measure.
 *
 * Like a player, and like the bot, the pilot's cursor swings around the Core rather than jumping across it and stays in
 * the area where steering keeps polarity, never on the Core (on a narrow phone screen a fast Core's lag reaches that
 * area's edge, where a cursor held just ahead of it would sweep the singularity through it). Above the speed at which a
 * rock starts to hurt it reads obstacles farther ahead and slows below that speed with a solid body on its course
 * (decisions B2). Its own Dashes keep a human cadence (none while a Dash phase runs), leave only once the cursor points
 * their way and never drive the Core into a solid body at a hurting speed.
 */

/** The family of window sites (or the sigil) an arm engages; every other optional site stays avoided. */
internal enum class SiteArm(val kinds: Set<SiteKind>) {
    RACE(setOf(SiteKind.GATE_RACE)),
    BRAKELESS(setOf(SiteKind.BRAKELESS_TRIAL)),
    RAM(setOf(SiteKind.RAM_TRIAL)),
    UNTOUCHED(setOf(SiteKind.UNTOUCHED_TRIAL)),
    VELOCITY(setOf(SiteKind.VELOCITY_TRIAL)),
    SLALOM(setOf(SiteKind.SLALOM_TRIAL)),
    BOUNTY(setOf(SiteKind.BOUNTY_ARENA)),

    /** The value baseline: the three anomalies of 0.5.0. */
    ANOMALY(SiteKind.entries.filter { it.family == SiteFamily.ANOMALY }.toSet()),

    /**
     * The shipped window weights, every window played: the first-drawn site of each window (side 0; side 1 when a rift
     * claimed side 0), whatever its family.
     */
    MIXED(SiteKind.entries.filter { it.family in WindowPlayFamilies }.toSet()),

    /** Far travel before the cutoff, the Cartographer's sigil and its duel (shipped window weights, all avoided). */
    SIGIL(setOf(SiteKind.FAR_SIGIL)),
    ;

    /**
     * The content the arm plays: the shipped policy with only the arm's kinds and the anomalies taking window weight,
     * the arm's kinds [ARM_TARGET_WEIGHT] each, so every window the family is eligible for offers it (the first draw in
     * all but a fraction of a percent of windows, else almost always the second). The anomaly arm offers anomaly pairs;
     * the sigil arm plays the shipped weights.
     */
    fun content(base: GameplayContentSnapshot = canonicalGameplayContent): GameplayContentSnapshot = when (this) {
        ANOMALY -> base.withAnomalyWindowsOnly()
        MIXED, SIGIL -> base
        else -> base.withWindowWeights(kinds.associateWith { ARM_TARGET_WEIGHT } + ANOMALY.kinds.associateWith { 1f })
    }

    companion object {
        /** The window weight of an arm's kinds against the anomalies' 1 each. */
        const val ARM_TARGET_WEIGHT: Float = 1_000f
    }
}

/** The window families the pilot can play (rifts stay avoided). */
private val WindowPlayFamilies: Set<SiteFamily> = setOf(SiteFamily.ANOMALY, SiteFamily.RACE, SiteFamily.CHALLENGE, SiteFamily.BOUNTY)

/** What an engagement ended in, read from the site's own run notice (or the sigil's defeat flag). */
internal enum class SiteOutcome(val notice: SiteNotice?) {
    GOLD(SiteNotice.RACE_GOLD),
    SILVER(SiteNotice.RACE_SILVER),
    BRONZE(SiteNotice.RACE_BRONZE),
    FINISHED(SiteNotice.RACE_FINISHED),
    LOST(SiteNotice.RACE_LOST),
    COMPLETED(SiteNotice.CHALLENGE_COMPLETE),
    FAILED(SiteNotice.CHALLENGE_FAILED),
    CLAIMED(SiteNotice.BOUNTY_CLAIMED),
    ABANDONED(SiteNotice.BOUNTY_ABANDONED),
    RESOLVED(SiteNotice.ANOMALY_RESOLVED),
    ANOMALY_LOST(SiteNotice.ANOMALY_LOST),
    BROKEN(SiteNotice.SIGIL_BROKEN),
    FADED(SiteNotice.SIGIL_FADES),

    /** The run ended (the Core fell) while the site ran. */
    DIED(null),

    /** The Architect's arrival cleared the running site. */
    CUT(null),
    ;

    /** True for an outcome that paid the site's reward. */
    val success: Boolean get() = this == GOLD || this == SILVER || this == BRONZE || this == FINISHED || this == COMPLETED ||
        this == CLAIMED || this == RESOLVED || this == BROKEN

    /** The race medal: 3 gold, 2 silver, 1 bronze, 0 otherwise. */
    val medal: Int get() = when (this) {
        GOLD -> 3
        SILVER -> 2
        BRONZE -> 1
        else -> 0
    }
}

/** One engagement of a site run: its kind, its outcome, when it started and how long it ran (simulated seconds). */
internal data class SiteEngagement(
    val kind: SiteKind,
    val outcome: SiteOutcome,
    val startedAt: Float,
    val seconds: Float,
    /** The reward's value in the S-G4 units ([siteRewardValue]), 0 when nothing was paid. */
    val value: Double,
    /** The run message the frame that ended it left. */
    val notice: String = "",
    /**
     * False when a later notice of the same frame covered the site's own and the outcome was read from what the site
     * paid instead ([endedOutcome]).
     */
    val read: Boolean = true,
)

/** One site run: its seed and end, its engagements in order and whether it ever opted into an optional site. */
internal data class SiteRun(
    val seed: Int,
    val phase: GamePhase,
    val seconds: Float,
    val engagements: List<SiteEngagement>,
    val touched: Boolean,
    /** Windows that offered the arm's family. */
    val offers: Int,
) {
    val won: Boolean get() = phase == GamePhase.VICTORY

    /** The NoPoi rule: a victory that never opted into an optional site. */
    val noPoiWin: Boolean get() = won && !touched
}

/** How a site run is set up: its viewport, starting profile (null: the fresh tier-0 Orb of the NoPoi gates) and end. */
internal data class SiteRunSetup(
    val width: Float = 1_280f,
    val height: Float = 800f,
    val profile: GameplayProfileSnapshot? = null,
    val base: GameplayContentSnapshot = canonicalGameplayContent,
    /** Stop once the last window can no longer run (a measurement that needs no run end). */
    val stopAfterWindows: Boolean = false,
)

/**
 * The S-G4 value of a reward bundle: directed reward 1.0, plain item offer 0.75, rarity-floor offer 1.1, Key 0.6, Relic
 * pickup 0.9, Matter 0.01 each, repair 0.1, Data 0.02 (spec §14.3); heals, Overdrive and rerolls count nothing.
 */
internal fun siteRewardValue(reward: SiteReward): Double =
    (if (reward.directed != null) 1.0 else 0.0) + reward.itemOffers * 0.75 + (if (reward.itemOfferFloor != null) 1.1 else 0.0) +
        reward.keys * 0.6 + reward.relics * 0.9 + reward.matter * 0.01 + reward.repairs * 0.1 + reward.data * 0.02

/**
 * The value an engagement paid: the race's medal reward, a site's own reward, and for a bounty also its boss's kill
 * (a Key, a Relic pickup, its Data and its Matter before the combo multiplier); nothing for a failure.
 */
internal fun MutableGameState.engagementValue(kind: SiteKind, outcome: SiteOutcome): Double {
    val sites = content.sites
    return when (outcome) {
        SiteOutcome.GOLD -> siteRewardValue(sites.race.gold)
        SiteOutcome.SILVER -> siteRewardValue(sites.race.silver)
        SiteOutcome.BRONZE -> siteRewardValue(sites.race.bronze)
        SiteOutcome.FINISHED -> siteRewardValue(sites.race.finish)
        SiteOutcome.COMPLETED, SiteOutcome.RESOLVED -> siteRewardValue(sites.definition(kind).reward)
        SiteOutcome.CLAIMED -> {
            val boss = sites.bounty.bossType.profile
            siteRewardValue(sites.definition(kind).reward) + 0.6 + 0.9 + boss.dataDrops * 0.02 +
                boss.matterReward * content.tempo.matterRewardMultiplier * 0.01
        }
        SiteOutcome.BROKEN -> siteRewardValue(sites.secrets.secretReward)
        else -> 0.0
    }
}

/** The engagement an arm tracks: the running window site, or the sigil's duel for the sigil arm. */
private fun MutableGameState.trackedEngagement(arm: SiteArm): SiteState? =
    if (arm == SiteArm.SIGIL) secretSigil?.takeIf { it.active } else activeWindowSite()

/** Directed rewards the run holds: queued, or open on the choice screen (no choice resolves inside a frame). */
private fun MutableGameState.directedHoldings(): Int = pendingDirectedRewards.size + if (directedReward != null) 1 else 0

/**
 * What the frame before an engagement ended looked like: the trial as last seen, when, and the directed rewards and
 * Elite kills held then (read only when a later notice covered the site's own).
 */
private class FrameBefore(val trial: SiteState, val at: Float, val holdings: Int, val eliteKills: Int)

/**
 * The outcome of the engagement [before] showed, ended in the frame just played: the site's own notice (the sigil:
 * its defeat flag). When a later notice of the same frame covered it, what the site paid decides: a directed reward
 * for an anomaly or a challenge, the boss's fall for a bounty, and for a race its last gate in time with the medal
 * its clock earned ([raceMedal]).
 */
private fun MutableGameState.endedOutcome(before: FrameBefore): Pair<SiteOutcome, Boolean> {
    val kind = before.trial.kind
    if (kind == SiteKind.FAR_SIGIL) {
        return (if (hasSecret(RunSecretFlag.CARTOGRAPHER_DEFEATED)) SiteOutcome.BROKEN else SiteOutcome.FADED) to true
    }
    SiteOutcome.entries.firstOrNull { it.notice?.english == message }?.let { return it to true }
    if (bossSpawned || elapsed >= content.tempo.bossAtSeconds) return SiteOutcome.CUT to true
    val paidDirected = directedHoldings() > before.holdings
    val outcome = when (kind.family) {
        SiteFamily.ANOMALY -> if (paidDirected) SiteOutcome.RESOLVED else SiteOutcome.ANOMALY_LOST
        SiteFamily.CHALLENGE -> if (paidDirected) SiteOutcome.COMPLETED else SiteOutcome.FAILED
        SiteFamily.BOUNTY -> if (before.trial.count >= 1 || eliteKills > before.eliteKills) SiteOutcome.CLAIMED else SiteOutcome.ABANDONED
        SiteFamily.RACE -> {
            val trial = before.trial
            val last = trial.course.size - 1
            val gate = trial.course[last]
            val passed = elapsed - before.at
            if (trial.step == last && trial.remaining - passed > 0f &&
                length(coreX - trial.x - gate.dx, coreY - trial.y - gate.dy) < RACE_LEASH * 0.5f
            ) {
                when (raceMedal(trial.copy(clock = trial.clock + passed))) {
                    3 -> SiteOutcome.GOLD
                    2 -> SiteOutcome.SILVER
                    1 -> SiteOutcome.BRONZE
                    else -> SiteOutcome.FINISHED
                }
            } else {
                SiteOutcome.LOST
            }
        }
        else -> SiteOutcome.CUT
    }
    return outcome to false
}

/**
 * Flies one site run of [arm] on [seed]: the fresh tier-0 Orb of the NoPoi gates (or [SiteRunSetup.profile]), 0.02 s
 * input frames until the run ends or 900 s, sound and visual cues taken every frame, on seeds fixed by definition.
 */
internal fun flySiteRun(seed: Int, arm: SiteArm, setup: SiteRunSetup = SiteRunSetup(), tuning: SitePilotTuning = SitePilotTuning()): SiteRun {
    val content = arm.content(setup.base)
    val state = if (setup.profile == null) {
        MutableGameState(content, seed = seed)
    } else {
        MutableGameState(content, seed = seed, bootstrapProgress = setup.profile)
    }
    state.unlockWeaponsForTesting(ReferenceArmory)
    state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
    state.resize(setup.width, setup.height)
    state.startRun()
    val pilot = SitePilot(arm, tuning = tuning)
    val windows = content.sites.windows
    val windowsEnd = windows.lastOfferAt + windows.offerLifetime + windows.trialDuration
    val engagements = ArrayList<SiteEngagement>()
    val offeredWindows = HashSet<Int>()
    var running: SiteState? = null
    var startedAt = 0f
    var touched = false
    while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
        val before = running?.let { FrameBefore(it, state.elapsed, state.directedHoldings(), state.eliteKills) }
        if (state.phase == GamePhase.CHOICE) {
            pilot.choose(state)
        } else {
            pilot.control(state)
            state.update(0.02f)
        }
        for (site in state.sites) if (!site.active && site.kind in arm.kinds) offeredWindows += site.offerId
        val now = state.trackedEngagement(arm)
        if (before != null && (now == null || now.id != before.trial.id)) {
            val (outcome, read) = state.endedOutcome(before)
            val kind = before.trial.kind
            engagements += SiteEngagement(
                kind, outcome, startedAt, state.elapsed - startedAt, state.engagementValue(kind, outcome), state.message, read,
            )
            running = null
        }
        if (now != null && running == null && now.kind in arm.kinds) {
            running = now
            startedAt = state.elapsed
        } else if (now != null && running != null) {
            running = now
        }
        touched = touched || state.optionalSitesTouched()
        state.takeSoundCues()
        state.takeVisualFxCues()
        if (setup.stopAfterWindows && running == null && arm.windowsDone(state, windowsEnd)) break
    }
    running?.let { site ->
        val outcome = if (state.phase == GamePhase.GAME_OVER) SiteOutcome.DIED else SiteOutcome.CUT
        engagements += SiteEngagement(site.kind, outcome, startedAt, state.elapsed - startedAt, 0.0, state.message)
    }
    return SiteRun(seed, state.phase, state.elapsed, engagements, touched, offeredWindows.size)
}

/**
 * One NoPoi run of the fresh tier-0 Orb on [seed] with the shipped site weights, flown like NoPoiPlaythroughTest's
 * population but in the site arms' viewport: the S-G4 baseline of runs that never opt into a site.
 */
internal fun flyNoPoiRun(
    seed: Int,
    base: GameplayContentSnapshot = canonicalGameplayContent,
    width: Float = 1_280f,
    height: Float = 800f,
): SiteRun {
    val state = MutableGameState(base, seed = seed)
    state.unlockWeaponsForTesting(ReferenceArmory)
    state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
    state.resize(width, height)
    state.startRun()
    val bot = NoPoiBot()
    var touched = false
    while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
        if (state.phase == GamePhase.CHOICE) {
            bot.choose(state)
        } else {
            bot.control(state)
            state.update(0.02f)
        }
        touched = touched || state.optionalSitesTouched()
        state.takeSoundCues()
        state.takeVisualFxCues()
    }
    return SiteRun(seed, state.phase, state.elapsed, emptyList(), touched, 0)
}

/** True once nothing more of this arm can start: the last window's trial is over, or the sigil's chance has passed. */
private fun SiteArm.windowsDone(state: MutableGameState, windowsEnd: Float): Boolean =
    if (this == SiteArm.SIGIL) {
        state.hasSecret(RunSecretFlag.CARTOGRAPHER_SUMMONED) && state.secretSigil == null || state.elapsed >= state.sigilCutoffAt()
    } else {
        state.elapsed >= windowsEnd
    }

/**
 * The pilot's own gains: what it asks the law for while it plays a site. They are the pilot's competence, calibrated
 * only on the SITES window (plan §8.2, i = 2,201..2,300), never on gate seeds; the gates' thresholds never move with
 * them.
 */
internal data class SitePilotTuning(
    /** Cursor reach whose steady speed the pilot flies toward an offered site. */
    val approachReach: Float = 0.45f,
    /** Race: cursor reach whose steady speed the pilot asks for between gates (spec: 0.55). */
    val raceReach: Float = 0.55f,
    /** Race: within this distance of a gate the heading blends toward the gate after it (spec: 350 u). */
    val raceBlendReach: Float = 350f,
    /** Race: the next gate's weight in that blend at the gate itself. */
    val raceBlend: Float = 0.6f,
    /** Race: Dash only when the flight is within this angle of the gate (radians; spec 15°) and farther than [raceDashReach]. */
    val raceDashAngle: Float = (15.0 * PI / 180.0).toFloat(),
    val raceDashReach: Float = 600f,
    /** Brakeless: the orbit's share of the ring and its speed (spec: 0.6, 600 u/s). */
    val brakelessShare: Float = 0.6f,
    val brakelessSpeed: Float = 600f,
    /** Ram: chase speed and the reach inside which the pilot Dashes at a marked foe (spec: 220 u). */
    val ramSpeed: Float = 650f,
    val ramDashReach: Float = 220f,
    /** Untouched: the orbit's share of the arena and its speed (spec: 0.5). */
    val untouchedShare: Float = 0.5f,
    val untouchedSpeed: Float = 460f,
    /** Velocity: the circle's radius and speed (spec: 1,100 u at 1,700 u/s). */
    val velocityRadius: Float = 1_100f,
    val velocitySpeed: Float = 1_700f,
    /** Slalom: the waypoint's lateral reach beyond its pole (spec: 120 u) and the speed toward it. */
    val slalomMargin: Float = 120f,
    val slalomSpeed: Float = 520f,
    /** Slalom: the waypoint lies this far short of the pole's line until the Core is out on its side, then past it. */
    val slalomLead: Float = 90f,
    /** Bounty and sigil duels: the pull back toward the centre starts this far out (spec: 800 u; the duel's 1,800 u). */
    val bountyReturn: Float = 800f,
    val duelReturn: Float = 1_800f,
    /** Anomalies: the speed through circuit beacons and the orbit's speed in its band (spec band 155 u). */
    val beaconSpeed: Float = 520f,
    val orbitBand: Float = 155f,
    val orbitSpeed: Float = 300f,
    /** Sigil: the second the far flight starts and the cursor reach it flies at. */
    val farFlightAt: Float = 300f,
    val farFlightReach: Float = 0.6f,
)

/** The engaging pilot over a NoPoi bot (see the file comment). */
internal class SitePilot(
    val arm: SiteArm,
    private val bot: NoPoiBot = NoPoiBot(),
    private val tuning: SitePilotTuning = SitePilotTuning(),
) {
    /** Scratch for the optional-site marks, rewritten on every read. */
    private val marks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)
    private var wantX = 0f
    private var wantY = 0f
    private var orbitOwner = Long.MIN_VALUE
    private var orbitTurn = 1f
    private var dashWanted = false
    private var dashX = 0f
    private var dashY = 0f
    private var steered = false
    private var aimHeading = 0f
    private var aimDistance = 0f

    fun choose(s: MutableGameState) = bot.choose(s)

    fun control(s: MutableGameState) {
        val heldX = s.cameraX + s.pointerX - s.screenWidth * 0.5f - s.coreX
        val heldY = s.cameraY + s.pointerY - s.screenHeight * 0.5f - s.coreY
        val held = atan2(heldY, heldX)
        dashWanted = false
        steered = false
        bot.control(s)
        if (s.phase != GamePhase.RUNNING) return
        decide(s)
        if (!steered && !dashWanted) {
            // The bot steers alone: its cursor stands unless it would jump across the Core or sit on it.
            val tetherX = s.cameraX + s.pointerX - s.screenWidth * 0.5f - s.coreX
            val tetherY = s.cameraY + s.pointerY - s.screenHeight * 0.5f - s.coreY
            val turn = angleBetween(atan2(tetherY, tetherX), held)
            if (abs(turn) <= CURSOR_SWING && length(tetherX, tetherY) >= MIN_TETHER) return
            aimHeading = atan2(tetherY, tetherX)
            aimDistance = length(tetherX, tetherY)
        }
        // A Dash leaves along the cursor: the cursor turns to the Dash's direction first, and the Dash waits until it points there.
        if (dashWanted) {
            aimHeading = atan2(dashY, dashX)
            aimDistance = max(DASH_TETHER, length(heldX, heldY))
        }
        val placed = place(s, held)
        if (dashWanted && abs(angleBetween(placed, atan2(dashY, dashX))) <= DASH_AIM) s.requestDash()
    }

    private fun decide(s: MutableGameState) {
        val active = s.activeWindowSite()
        if (active != null) {
            if (active.kind in arm.kinds) play(s, active)
            return
        }
        if (arm == SiteArm.SIGIL) {
            sigil(s)
            return
        }
        if (s.engagementActive()) return
        for (site in s.sites) {
            if (site.active || site.kind !in arm.kinds || s.elapsed >= site.expiresAt) continue
            toward(s, site.x, site.y, s.steadyCursorSpeed(tuning.approachReach))
            steer(s, site, dodge = true, brake = null)
            return
        }
    }

    /**
     * Places the cursor for the aim ([aimHeading], [aimDistance]) and returns the heading it got. The cursor swings around
     * the Core at most [CURSOR_SWING] radians a frame from where it was held ([held]), as the NoPoi bot's does (a cursor
     * jumping across the Core would sweep the singularity through it), and it stays inside the area where ordinary
     * steering keeps polarity, at least [MIN_TETHER] from the Core and more ahead of a fast Core
     * ([FORWARD_MARGIN_SECONDS]). When the Core's lag already carries it to that area's edge (a fast Core on a narrow
     * phone screen), the nearest heading within the swing that keeps the tether is taken, then one within
     * [EMERGENCY_SWING] (still too short a jump for the singularity's sweep to touch the Core), and as a last resort the
     * screen centre's direction.
     */
    private fun place(s: MutableGameState, held: Float): Float {
        for (bound in floatArrayOf(CURSOR_SWING, EMERGENCY_SWING)) {
            val target = held + angleBetween(aimHeading, held).coerceIn(-bound, bound)
            var step = 0
            while (step * SWING_SEARCH_STEP <= 2f * bound) {
                val angle = target + ((step + 1) / 2) * SWING_SEARCH_STEP * if (step % 2 == 1) 1f else -1f
                step++
                if (abs(angleBetween(angle, held)) > bound + 1e-4f) continue
                val room = room(s, angle)
                // Ahead of a fast Core the singularity keeps a margin of the Core's closing speed, so a Core pinned near
                // the area's edge never runs onto a singularity held just ahead of it.
                val need = MIN_TETHER + max(0f, s.velocityX * cos(angle) + s.velocityY * sin(angle)) * FORWARD_MARGIN_SECONDS
                if (room >= need) {
                    hold(s, angle, aimDistance.coerceIn(need, room))
                    return angle
                }
            }
        }
        val centre = atan2(s.cameraY - s.coreY, s.cameraX - s.coreX)
        hold(s, centre, max(MIN_TETHER, min(aimDistance, room(s, centre))))
        return centre
    }

    /** How far from the Core along [heading] the pointer may sit inside the polarity area (negative when outside it). */
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

    /** Holds the singularity [distance] from the Core along [heading]. */
    private fun hold(s: MutableGameState, heading: Float, distance: Float) {
        s.updatePointer(
            s.screenWidth * 0.5f + s.coreX - s.cameraX + cos(heading) * distance,
            s.screenHeight * 0.5f + s.coreY - s.cameraY + sin(heading) * distance,
        )
    }

    private fun play(s: MutableGameState, trial: SiteState) {
        when (trial.kind) {
            SiteKind.GATE_RACE -> race(s, trial)
            SiteKind.BRAKELESS_TRIAL -> {
                orbit(s, trial.id, trial.x, trial.y, tuning.brakelessShare * s.content.sites.challenges.brakelessRing(trial.clock),
                    tuning.brakelessSpeed)
                steer(s, trial, dodge = false, brake = false, mayBrakeNearRocks = false)
            }
            SiteKind.RAM_TRIAL -> ram(s, trial)
            SiteKind.UNTOUCHED_TRIAL -> {
                orbit(s, trial.id, trial.x, trial.y, tuning.untouchedShare * s.content.sites.challenges.untouchedRadius,
                    tuning.untouchedSpeed)
                steer(s, trial, dodge = true, brake = null)
            }
            SiteKind.VELOCITY_TRIAL -> velocity(s, trial)
            SiteKind.SLALOM_TRIAL -> slalom(s, trial)
            SiteKind.BOUNTY_ARENA -> duel(s, trial, trial.foeIds.firstOrNull(), tuning.bountyReturn)
            SiteKind.RESONANT_CIRCUIT -> {
                val beacon = CircuitBeacons[trial.step.coerceAtMost(CircuitBeacons.size - 1)]
                toward(s, trial.x + beacon.dx, trial.y + beacon.dy, tuning.beaconSpeed)
                steer(s, trial, dodge = true, brake = false)
            }
            SiteKind.COLLAPSING_ORBIT -> {
                orbit(s, trial.id, trial.x, trial.y, tuning.orbitBand, tuning.orbitSpeed)
                steer(s, trial, dodge = true, brake = false)
            }
            SiteKind.SEALED_ANOMALY -> {
                val defender = nearestFoe(s, trial)
                if (defender != null) combat(s, defender.x + defender.vx * 0.3f, defender.y + defender.vy * 0.3f, boss = false)
                steer(s, trial, dodge = true, brake = null)
            }
            else -> Unit
        }
    }

    /**
     * Toward the next gate at the steady speed of the race reach, blending toward the gate after it within the blend
     * reach; a Dash when the flight lines up with the gate far enough from it.
     */
    private fun race(s: MutableGameState, trial: SiteState) {
        val gate = trial.course[trial.step]
        val gateX = trial.x + gate.dx
        val gateY = trial.y + gate.dy
        val distance = max(1f, length(gateX - s.coreX, gateY - s.coreY))
        var unitX = (gateX - s.coreX) / distance
        var unitY = (gateY - s.coreY) / distance
        if (trial.step + 1 < trial.course.size && distance < tuning.raceBlendReach) {
            val next = trial.course[trial.step + 1]
            val leg = max(1f, length(next.dx - gate.dx, next.dy - gate.dy))
            val weight = tuning.raceBlend * (1f - distance / tuning.raceBlendReach)
            unitX += (next.dx - gate.dx) / leg * weight
            unitY += (next.dy - gate.dy) / leg * weight
            val blended = max(0.001f, length(unitX, unitY))
            unitX /= blended
            unitY /= blended
        }
        val speed = s.steadyCursorSpeed(tuning.raceReach)
        wantX = unitX * speed
        wantY = unitY * speed
        steer(s, trial, dodge = false, brake = false)
        if (distance > tuning.raceDashReach && aligned(s, unitX, unitY, tuning.raceDashAngle)) dash(s, unitX, unitY)
    }

    /** Chases the nearest marked foe and Dashes at it within the Dash reach. */
    private fun ram(s: MutableGameState, trial: SiteState) {
        val foe = nearestFoe(s, trial)
        if (foe == null) {
            orbit(s, trial.id, trial.x, trial.y, s.content.sites.challenges.ramRingRadius, tuning.untouchedSpeed)
            steer(s, trial, dodge = true, brake = null)
            return
        }
        val targetX = foe.x + foe.vx * 0.15f
        val targetY = foe.y + foe.vy * 0.15f
        toward(s, targetX, targetY, tuning.ramSpeed)
        steer(s, trial, dodge = false, brake = false)
        val distance = length(targetX - s.coreX, targetY - s.coreY)
        if (distance < tuning.ramDashReach) {
            dash(s, (targetX - s.coreX) / max(1f, distance), (targetY - s.coreY) / max(1f, distance), maxHeat = RAM_DASH_HEAT)
        }
    }

    /** Circles the zone's centre at the circle's radius and speed; a Dash when the flight lines up below that speed. */
    private fun velocity(s: MutableGameState, trial: SiteState) {
        orbit(s, trial.id, trial.x, trial.y, tuning.velocityRadius, tuning.velocitySpeed)
        steer(s, trial, dodge = false, brake = false)
        val want = max(1f, length(wantX, wantY))
        if (s.speed < tuning.velocitySpeed && aligned(s, wantX / want, wantY / want, tuning.raceDashAngle)) {
            dash(s, wantX / want, wantY / want)
        }
    }

    /**
     * Toward the next pole's waypoint on its flagged side, beyond the pole by the slalom margin: short of the pole's line
     * until the Core is out on that side, then across the line there; a Brake sheds speed far above the slalom speed.
     */
    private fun slalom(s: MutableGameState, trial: SiteState) {
        val policy = s.content.sites.challenges
        val pole = trial.course[trial.step.coerceAtMost(trial.course.size - 1)]
        val side = if (trial.sides and (1 shl trial.step) != 0) 1f else -1f
        val unitX = cos(trial.axis)
        val unitY = sin(trial.axis)
        val normalX = -unitY
        val normalY = unitX
        val lateral = (s.coreX - trial.x) * normalX + (s.coreY - trial.y) * normalY
        val outside = side * lateral >= policy.slalomPoleOffset + s.coreRadius + SLALOM_SIDE_SLACK
        val lead = if (outside) tuning.slalomLead else -tuning.slalomLead
        val waypointX = trial.x + pole.dx + normalX * side * tuning.slalomMargin + unitX * lead
        val waypointY = trial.y + pole.dy + normalY * side * tuning.slalomMargin + unitY * lead
        toward(s, waypointX, waypointY, tuning.slalomSpeed)
        steer(s, trial, dodge = false, brake = s.speed > tuning.slalomSpeed * SLALOM_BRAKE_SHARE)
    }

    /**
     * The duel of a bounty or a sigil: the NoPoi bot's fight against the Architect turned on the duel's boss (orbiting it
     * at its weapon's range, ramming it with the Flux Wake), pulled back toward the centre beyond [returnReach].
     */
    private fun duel(s: MutableGameState, site: SiteState, bossId: Int?, returnReach: Float) {
        val boss = s.enemies.firstOrNull { it.id == bossId && it.alive }
        if (boss != null) combat(s, boss.x, boss.y, boss = true) else toward(s, site.x, site.y, tuning.untouchedSpeed)
        val outX = s.coreX - site.x
        val outY = s.coreY - site.y
        val out = length(outX, outY)
        if (out > returnReach) {
            val pull = ((out - returnReach) * 4f).coerceAtMost(700f)
            wantX -= outX / out * pull
            wantY -= outY / out * pull
        }
        steer(s, site, dodge = true, brake = null)
    }

    /**
     * Far travel: from [SitePilotTuning.farFlightAt] the pilot flies straight away from the spawn until the sigil appears,
     * then into it, then duels its boss.
     */
    private fun sigil(s: MutableGameState) {
        val sigil = s.secretSigil
        when {
            sigil != null && sigil.active -> duel(s, sigil, sigil.foeIds.firstOrNull(), tuning.duelReturn)
            sigil != null -> {
                if (s.engagementActive()) return
                toward(s, sigil.x, sigil.y, s.steadyCursorSpeed(tuning.approachReach))
                steer(s, sigil, dodge = true, brake = false)
            }
            !s.hasSecret(RunSecretFlag.FAR_TRAVEL) && s.elapsed >= tuning.farFlightAt -> {
                val absoluteX = s.absoluteX(s.coreX).toFloat()
                val absoluteY = s.absoluteY(s.coreY).toFloat()
                val out = length(absoluteX, absoluteY)
                val heading = if (out > 50f) atan2(absoluteY, absoluteX) else s.travelHeading()
                val speed = s.steadyCursorSpeed(tuning.farFlightReach)
                wantX = cos(heading) * speed
                wantY = sin(heading) * speed
                steer(s, null, dodge = true, brake = false)
            }
        }
    }

    /** The NoPoi bot's chase of a target: an orbit at its weapon's range, or a ram with the Flux Wake. */
    private fun combat(s: MutableGameState, targetX: Float, targetY: Float, boss: Boolean) {
        val radius = when {
            boss -> if (s.weapon == WeaponId.FLUX_WAKE) 100f else 300f
            s.weapon == WeaponId.ARC_COIL || s.weapon == WeaponId.ION_SWARM || s.weapon == WeaponId.PRISM_RELAY -> 280f
            else -> 0f
        }
        val orbitSpeed = if (boss && s.weapon == WeaponId.FLUX_WAKE) 350f else 460f
        val dx = s.coreX - targetX
        val dy = s.coreY - targetY
        val distance = max(1f, length(dx, dy))
        val nx = if (distance > 2f) dx / distance else 1f
        val ny = if (distance > 2f) dy / distance else 0f
        if (radius < 50f) {
            wantX = -nx * 650f
            wantY = -ny * 650f
        } else {
            val radial = ((radius - distance) * 2.4f).coerceIn(-480f, 480f)
            wantX = -ny * orbitSpeed + nx * radial
            wantY = nx * orbitSpeed + ny * radial
        }
    }

    private fun nearestFoe(s: MutableGameState, trial: SiteState) = s.enemies.filter { it.alive && it.id in trial.foeIds }
        .minByOrNull { length(it.x - s.coreX, it.y - s.coreY) }

    /** Desired velocity toward ([x], [y]) at [speed]. */
    private fun toward(s: MutableGameState, x: Float, y: Float, speed: Float) {
        val distance = max(1f, length(x - s.coreX, y - s.coreY))
        wantX = (x - s.coreX) / distance * speed
        wantY = (y - s.coreY) / distance * speed
    }

    /**
     * Desired velocity of an orbit around ([x], [y]) at [radius] and [speed]: tangential, in the sense the Core already
     * turns when the orbit starts, with the NoPoi bot's radial correction and the inward pull a circle at that speed
     * needs (fed forward through the steering gain, so the orbit does not settle outside its radius).
     */
    private fun orbit(s: MutableGameState, owner: Long, x: Float, y: Float, radius: Float, speed: Float) {
        val dx = s.coreX - x
        val dy = s.coreY - y
        val distance = max(1f, length(dx, dy))
        val nx = dx / distance
        val ny = dy / distance
        if (owner != orbitOwner) {
            orbitOwner = owner
            orbitTurn = if (nx * s.velocityY - ny * s.velocityX < 0f) -1f else 1f
        }
        val radial = ((radius - distance) * 2.4f).coerceIn(-480f, 480f) - speed * speed / (max(1f, radius) * STEERING_GAIN)
        wantX = -ny * orbitTurn * speed + nx * radial
        wantY = nx * orbitTurn * speed + ny * radial
    }

    /** True when the Core flies within [angle] of the unit direction ([unitX], [unitY]). */
    private fun aligned(s: MutableGameState, unitX: Float, unitY: Float, angle: Float): Boolean =
        s.speed > 1f && (s.velocityX * unitX + s.velocityY * unitY) / s.speed >= cos(angle)

    /**
     * Wants a Dash along ([unitX], [unitY]) at a human cadence (none while a Dash phase runs, nor above [maxHeat]) that
     * never drives the Core into a solid body at a hurting speed within 0.3 s of the Dash's travel; [control] turns the
     * cursor there and Dashes once it points there.
     */
    private fun dash(s: MutableGameState, unitX: Float, unitY: Float, maxHeat: Float = 60f) {
        if (!s.dashReady || s.heat >= maxHeat || s.dashPhaseTime > 0f) return
        val after = s.speed + s.effectiveDashImpulse()
        if (after >= s.obstacleDamageStartSpeed()) {
            val reach = after * 0.3f
            for (obstacle in s.obstacleField.obstacles) {
                if (!obstacle.kind.solid) continue
                val hazard = s.obstacleHazardReach(obstacle)
                if (hazard <= 0f) continue
                if (segmentCircleIntersects(s.coreX, s.coreY, s.coreX + unitX * reach, s.coreY + unitY * reach,
                        obstacle.x, obstacle.y, hazard + s.coreRadius + 40f)
                ) return
            }
        }
        dashWanted = true
        dashX = unitX
        dashY = unitY
    }

    /**
     * Steers for the desired velocity with the NoPoi bot's avoidance: every optional site but [playing] (from where
     * momentum carries the Core), solid bodies and hazards ahead, and hostile shots when [dodge]; the acceleration that
     * asks for goes through the production inverse, and the pointer stays where steering keeps polarity. [brake] sets
     * the Brake (null keeps the bot's).
     */
    private fun steer(s: MutableGameState, playing: SiteState?, dodge: Boolean, brake: Boolean?, mayBrakeNearRocks: Boolean = true) {
        var desiredX = wantX
        var desiredY = wantY
        val count = s.optionalSiteMarks(marks)
        for (mark in 0 until count) {
            val base = mark * 3
            if (playing != null && marks[base] == playing.x && marks[base + 1] == playing.y) continue
            val px = s.coreX + s.velocityX * 0.35f - marks[base]
            val py = s.coreY + s.velocityY * 0.35f - marks[base + 1]
            val clearance = marks[base + 2] + SITE_PILOT_CLEARANCE
            val distance = max(1f, length(px, py))
            if (distance < clearance) {
                desiredX += px / distance * (clearance - distance) * 8f
                desiredY += py / distance * (clearance - distance) * 8f
            }
        }
        // Above the speed at which a rock starts to hurt, the pilot looks farther ahead and, with a solid body on its
        // course, slows below that speed (B2: a competent pilot near rocks).
        val hurting = s.obstacleDamageStartSpeed()
        val fast = s.speed > hurting
        val lookahead = if (fast) FAST_LOOKAHEAD else 0.3f
        var rockAhead = false
        for (obstacle in s.obstacleField.obstacles) {
            val reach = s.obstacleHazardReach(obstacle)
            if (reach <= 0f) continue
            val px = s.coreX + s.velocityX * lookahead - obstacle.x
            val py = s.coreY + s.velocityY * lookahead - obstacle.y
            val clearance = reach + s.coreRadius + if (obstacle.kind.solid) 110f else 40f
            val distance = max(1f, length(px, py))
            if (distance < clearance) {
                desiredX += px / distance * (clearance - distance) * 6f
                desiredY += py / distance * (clearance - distance) * 6f
            }
            if (fast && obstacle.kind.solid && !rockAhead) {
                rockAhead = segmentCircleIntersects(
                    s.coreX, s.coreY, s.coreX + s.velocityX * GOVERNOR_SECONDS, s.coreY + s.velocityY * GOVERNOR_SECONDS,
                    obstacle.x, obstacle.y, reach + s.coreRadius + GOVERNOR_CLEARANCE,
                )
            }
        }
        if (rockAhead) {
            val desired = length(desiredX, desiredY)
            val cap = hurting * GOVERNOR_SHARE
            if (desired > cap) {
                desiredX *= cap / desired
                desiredY *= cap / desired
            }
        }
        if (dodge) for (shot in s.projectiles) if (shot.hostile) {
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
        if (brake != null) s.setBrake(brake || rockAhead && mayBrakeNearRocks && s.speed > hurting * GOVERNOR_BRAKE)
        val aim = s.steeringAimFor(
            (desiredX - s.velocityX) * STEERING_GAIN + s.velocityX * s.dragCoefficient,
            (desiredY - s.velocityY) * STEERING_GAIN + s.velocityY * s.dragCoefficient,
        )
        aimHeading = aim.heading
        aimDistance = aim.distance
        steered = true
    }
}

/** A Dash waits until the cursor points within this angle (radians) of the Dash's direction. */
private const val DASH_AIM = 0.2f

/** The cursor's least distance from the Core while it aims a Dash. */
private const val DASH_TETHER = 140f

/** The Ram rite's Dashes may run hotter than the race's (one more Dash before the overheat line). */
private const val RAM_DASH_HEAT = 75f

/** The most the cursor swings around the Core in one 0.02 s frame (the NoPoi bot's bound). */
private const val CURSOR_SWING = 0.45f

/**
 * The widest swing a cursor pinned at the polarity area's edge may make: a jump of at most this angle at the least
 * tether keeps the singularity's swept chord 65 · cos(1.0) ≈ 35 dp from the Core, outside its 22 dp kill radius.
 */
private const val EMERGENCY_SWING = 2.0f

/** Headings tried around the aim, this far apart (radians). */
private const val SWING_SEARCH_STEP = 0.1f

/** Seconds ahead the pilot reads obstacles once it flies fast enough for a rock to hurt (the bot's 0.3 s below). */
private const val FAST_LOOKAHEAD = 0.45f

/** A fast Core with a solid body within this many seconds of its course, at this clearance, slows below the hurting speed. */
private const val GOVERNOR_SECONDS = 0.6f
private const val GOVERNOR_CLEARANCE = 60f

/** The share of the hurting speed the slowed pilot asks for, and the share above which it also Brakes (never in the brakeless trial). */
private const val GOVERNOR_SHARE = 0.9f
private const val GOVERNOR_BRAKE = 1.4f

/** The slalom's Core counts as out on its pole's side this far beyond what the pass needs. */
private const val SLALOM_SIDE_SLACK = 30f

/** The slalom Brakes above this share of its speed. */
private const val SLALOM_BRAKE_SHARE = 1.6f

/** Acceleration asked per unit of velocity error (the NoPoi bot's gain, per second). */
private const val STEERING_GAIN = 5f

/** Seconds of the Core's forward speed added to the least tether for a singularity held ahead of it. */
private const val FORWARD_MARGIN_SECONDS = 0.06f

/** The least tether the pilot holds (the NoPoi bot's 65 dp). */
private const val MIN_TETHER = 65f

/** The signed turn from [from] to [to], in −π..π. */
private fun angleBetween(to: Float, from: Float): Float = atan2(sin(to - from), cos(to - from))

/** How far beyond another optional site's radius the pilot keeps the momentum-projected Core (the bot's margin). */
private const val SITE_PILOT_CLEARANCE = 295f

/** Bootstrap standard errors: 20,000 resamples of the runs, RNG seed 20261027 (the calibration convention). */
internal const val SITE_BOOTSTRAP_RESAMPLES: Int = 20_000
internal const val SITE_BOOTSTRAP_SEED: Int = 20_261_027

/** [metric] over [runs] and its bootstrap standard error (runs resampled whole, so a run's engagements stay together). */
internal fun <T> bootstrap(runs: List<T>, metric: (List<T>) -> Double): Pair<Double, Double> {
    val random = Random(SITE_BOOTSTRAP_SEED)
    val values = DoubleArray(SITE_BOOTSTRAP_RESAMPLES)
    val sample = ArrayList<T>(runs.size)
    for (resample in 0 until SITE_BOOTSTRAP_RESAMPLES) {
        sample.clear()
        repeat(runs.size) { sample += runs[random.nextInt(runs.size)] }
        values[resample] = metric(sample)
    }
    val mean = values.average()
    val se = sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    return metric(runs) to se
}

/** "name=value(SE se)" with four decimals. */
internal fun <T> bootstrapLine(name: String, runs: List<T>, metric: (List<T>) -> Double): String {
    val (value, se) = bootstrap(runs, metric)
    return "$name=${"%.4f".format(value)}(SE ${"%.4f".format(se)})"
}

/** Engagements of [runs] that ended in a readable result (DIED counts as a failure; a CUT one never ran its course). */
internal fun List<SiteRun>.judged(kind: Set<SiteKind>): List<SiteEngagement> =
    flatMap { run -> run.engagements.filter { it.kind in kind && it.outcome != SiteOutcome.CUT } }

/** The share of [engagements] matching [predicate] (0 when there are none). */
internal fun List<SiteEngagement>.shareWhere(predicate: (SiteEngagement) -> Boolean): Double =
    if (isEmpty()) 0.0 else count(predicate).toDouble() / size

/** Mean value per engagement (0 when there are none). */
internal fun List<SiteEngagement>.meanValue(): Double = if (isEmpty()) 0.0 else sumOf { it.value } / size

/** Seeds of the calibration window `i * 7_919 + 13` for i in [first]..[last] (plan §8.2; SITES: 2,201..2,300). */
internal fun siteWindowSeeds(first: Int, last: Int): List<Int> = (first..last).map { it * 7_919 + 13 }
