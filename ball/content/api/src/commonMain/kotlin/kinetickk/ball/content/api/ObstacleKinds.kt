// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * World obstacle kinds, in generation and render order (append-only: ordinals feed fingerprints).
 * Every kind is bounded by one circle of radius at most [ObstacleTuning.asteroidMaximumRadius] around its centre.
 */
enum class ObstacleKind(
    /** The Core and enemies collide with its body circle (a phased kind only while materialised). */
    val solid: Boolean,
    /** Solid only for part of a fixed cycle ([phaseShardSolid]). */
    val phased: Boolean,
    /** Spends shots and stops lens, shard and missile entities on its body (a phased kind only while materialised). */
    val stopsShots: Boolean,
    /** A hard Core contact uses the asteroid damage band and lethal speed. */
    val hurtsOnImpact: Boolean,
    /** Near Miss and GRAZE count skimming its body. */
    val grazeable: Boolean,
    /** Seismic's Rock Echo may repeat a quake from it. */
    val echoes: Boolean,
    /** Breaks for good: removed and its id kept in the destroyed memory. */
    val breakable: Boolean,
    /** Has an area effect the field pass resolves (pull, crossing, plume, arming). */
    val field: Boolean,
) {
    ASTEROID(solid = true, phased = false, stopsShots = true, hurtsOnImpact = true, grazeable = true, echoes = true, breakable = false, field = false),
    BUMPER(solid = true, phased = false, stopsShots = false, hurtsOnImpact = false, grazeable = false, echoes = true, breakable = false, field = false),
    UNSTABLE(solid = true, phased = false, stopsShots = true, hurtsOnImpact = true, grazeable = true, echoes = true, breakable = true, field = false),
    GRAVITY_WELL(solid = true, phased = false, stopsShots = true, hurtsOnImpact = true, grazeable = true, echoes = true, breakable = false, field = true),
    ACCEL_GATE(solid = false, phased = false, stopsShots = false, hurtsOnImpact = false, grazeable = false, echoes = false, breakable = false, field = true),
    CRYSTAL(solid = true, phased = false, stopsShots = true, hurtsOnImpact = false, grazeable = true, echoes = true, breakable = true, field = false),
    DERELICT_MINE(solid = false, phased = false, stopsShots = false, hurtsOnImpact = false, grazeable = false, echoes = false, breakable = true, field = true),
    PHASE_SHARD(solid = true, phased = true, stopsShots = true, hurtsOnImpact = true, grazeable = true, echoes = false, breakable = false, field = false),
    THERMAL_VENT(solid = false, phased = false, stopsShots = false, hurtsOnImpact = false, grazeable = false, echoes = false, breakable = false, field = true),
}

/** Farthest surface gap a [EffectCondition.NearObstacle] may name. */
const val NEAR_OBSTACLE_MAX_DISTANCE: Float = 320f

/**
 * Shares of ASTEROID candidates that become each new kind (the rest stay asteroids), plus layer-wide density and
 * bumper multipliers. A share whose kind is ineligible for the candidate's radius leaves it an asteroid.
 */
data class ObstacleMix(
    val gravityWell: Float = 0f,
    val accelGate: Float = 0f,
    val crystal: Float = 0f,
    val derelictMine: Float = 0f,
    val phaseShard: Float = 0f,
    val thermalVent: Float = 0f,
    /** Share of gravity knots that push (Repulsor) instead of pull (Mass Knot). */
    val repelShare: Float = 0.5f,
    /** Multiplies the chunk mean after Rebirth density and Fractured Space (1 = today). */
    val densityScale: Float = 1f,
    /** Multiplies the bumper share (1 = today). */
    val bumperScale: Float = 1f,
) {
    val total: Float get() = gravityWell + accelGate + crystal + derelictMine + phaseShard + thermalVent

    fun share(kind: ObstacleKind): Float = when (kind) {
        ObstacleKind.GRAVITY_WELL -> gravityWell
        ObstacleKind.ACCEL_GATE -> accelGate
        ObstacleKind.CRYSTAL -> crystal
        ObstacleKind.DERELICT_MINE -> derelictMine
        ObstacleKind.PHASE_SHARD -> phaseShard
        ObstacleKind.THERMAL_VENT -> thermalVent
        ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE -> 0f
    }

    init {
        listOf(gravityWell, accelGate, crystal, derelictMine, phaseShard, thermalVent).forEach {
            require(it.isFinite() && it in 0f..1f) { "Obstacle variant shares must be in 0..1" }
        }
        require(total <= 1f) { "Obstacle variant shares must sum to at most 1" }
        require(repelShare in 0f..1f) { "Repulsor share must be in 0..1" }
        require(densityScale.isFinite() && densityScale in 0.25f..2f) { "Obstacle density scale must be in 0.25..2" }
        require(bumperScale.isFinite() && bumperScale in 0f..2f) { "Bumper scale must be in 0..2" }
    }
}

/** Walks [ObstacleKind] order; the first share whose cumulative sum exceeds [roll] wins if eligible, else ASTEROID. */
fun ObstacleMix.pick(roll: Float, candidateRadius: Float, kinds: ObstacleKindTuning): ObstacleKind {
    var cumulative = 0f
    for (kind in ObstacleKind.entries) {
        val share = share(kind)
        if (share <= 0f) continue
        cumulative += share
        if (roll < cumulative) return if (kinds.eligible(kind, candidateRadius)) kind else ObstacleKind.ASTEROID
    }
    return ObstacleKind.ASTEROID
}

/**
 * Every number of the obstacle kinds: one [ObstacleMix] per world layer and the mechanics of each new kind. A layer
 * whose mix is `ObstacleMix()` keeps the 0.5.0 field of asteroids and bumpers (the "without new kinds" content arm).
 */
data class ObstacleKindTuning(
    /**
     * Realspace: 0.45 of asteroid candidates rolled, 0.359 after eligibility, so 29.5 % of all candidates become new
     * kinds; asteroids stay 52.5 % and bumpers 18 %.
     */
    val space: ObstacleMix = ObstacleMix(
        gravityWell = 0.09f, accelGate = 0.09f, crystal = 0.11f, derelictMine = 0.05f, phaseShard = 0.06f, thermalVent = 0.05f,
    ),
    /** Heaven: gates, crystals and lifting Repulsors among more bumpers, a little sparser, no mines or vents. */
    val zenith: ObstacleMix = ObstacleMix(
        gravityWell = 0.06f, accelGate = 0.22f, crystal = 0.22f, phaseShard = 0.10f,
        repelShare = 0.7f, densityScale = 0.9f, bumperScale = 1.3f,
    ),
    /** Hell: dense vents and mines with pulling knots and few bumpers. */
    val nadir: ObstacleMix = ObstacleMix(
        gravityWell = 0.10f, accelGate = 0.04f, crystal = 0.04f, derelictMine = 0.14f, phaseShard = 0.04f, thermalVent = 0.24f,
        repelShare = 0.25f, densityScale = 1.15f, bumperScale = 0.6f,
    ),
    /** The Architect's memory: glitching phase shards. */
    val archive: ObstacleMix = ObstacleMix(
        gravityWell = 0.10f, accelGate = 0.08f, crystal = 0.08f, derelictMine = 0.06f, phaseShard = 0.24f, thermalVent = 0.04f,
        bumperScale = 0.8f,
    ),
    /** Gravity knots only replace mid and large rocks, so their field sits in open space. */
    val wellMinimumCandidate: Float = 70f,
    /** A gate needs an opening wide enough for the Core between its posts. */
    val gateMinimumCandidate: Float = 70f,
    /** Brittle crystals read small or medium. */
    val crystalMaximumCandidate: Float = 110f,
    val ventMinimumCandidate: Float = 60f,
    /** The solid knot at a gravity well's centre. */
    val wellCoreRadius: Float = 34f,
    /** Field radius of a gravity well. */
    val wellInfluence: Float = 260f,
    /** Core acceleration at the knot's surface, falling linearly to 0 at the field edge. */
    val wellCorePull: Float = 900f,
    /** Enemy acceleration at the knot's surface (times the enemy's knockback intake). */
    val wellEnemyPull: Float = 1_200f,
    /** Net heading change inside a well's field that counts as a sling. */
    val slingTurnRadians: Float = 1.5708f,
    val slingMinimumSpeed: Float = 600f,
    val slingImpulse: Float = 220f,
    val gateMaximumRadius: Float = 130f,
    val gatePostRadius: Float = 12f,
    val gateImpulse: Float = 380f,
    /** Least |cos| between the Core's travel and the gate normal that counts as a crossing. */
    val gateAlignment: Float = 0.5f,
    val gateCooldownSeconds: Float = 0.8f,
    /** Every boost (gate, sling, vent) leaves the Core at most this share of the lethal collision speed. */
    val boostLethalShare: Float = 0.9f,
    /** Share of its speed the Core keeps through a shatter. */
    val crystalSpeedRetention: Float = 0.88f,
    val crystalShards: Int = 6,
    val crystalShardDamage: Float = 14f,
    /** Data pickups a crystal drops. */
    val crystalData: Int = 2,
    val mineClusterMaximumRadius: Float = 64f,
    /** Arming reach beyond the mine cluster's body. */
    val mineArmMargin: Float = 110f,
    val mineFuseSeconds: Float = 0.6f,
    /** Fuse after a thrown enemy, a player shot or a neighbouring blast. */
    val mineQuickFuseSeconds: Float = 0.1f,
    val mineBlastRadius: Float = 160f,
    /** Blast damage to the Core as a share of maximum integrity. */
    val mineBlastShare: Float = 0.10f,
    val mineBlastEnemyDamage: Float = 60f,
    /** Mines detonating in one step; the next one waits a step. */
    val maximumMineDetonationsPerStep: Int = 4,
    val phasePeriodSeconds: Float = 3.2f,
    val phaseSolidShare: Float = 0.6f,
    /** Materialising look before a phase shard turns solid. */
    val phaseTelegraphSeconds: Float = 0.45f,
    val ventPeriodSeconds: Float = 4f,
    val ventEruptSeconds: Float = 1f,
    val ventTelegraphSeconds: Float = 0.6f,
    val ventMaximumRadius: Float = 110f,
    /** Plume radius beyond the vent's body. */
    val ventPlumeMargin: Float = 40f,
    val ventHeat: Float = 30f,
    /** The updraft's impulse. */
    val ventImpulse: Float = 160f,
    /** One Thermal stack per enemy per this many seconds inside an erupting plume. */
    val ventStatusInterval: Float = 0.25f,
    val grazeDistance: Float = 40f,
    val grazeMinimumSpeed: Float = 900f,
    val grazeCooldownSeconds: Float = 0.5f,
    /** At least every area reach of the kinds; the Core field query reach. */
    val maximumInfluence: Float = 320f,
) {
    init {
        require(wellCoreRadius >= 1f && wellCoreRadius <= wellMinimumCandidate) { "A knot's core must fit its candidates" }
        require(wellInfluence > wellCoreRadius + 16f && wellInfluence <= maximumInfluence) {
            "A well's field must reach past its core and stay within the maximum influence"
        }
        require(gatePostRadius > 0f && 2f * gatePostRadius < gateMinimumCandidate) { "Gate posts must leave an opening" }
        require(gateMaximumRadius >= gateMinimumCandidate) { "Gates must fit their smallest candidate" }
        require(mineClusterMaximumRadius + mineArmMargin <= maximumInfluence) { "A mine's arming reach must stay within the maximum influence" }
        require(mineBlastRadius > 0f && mineBlastRadius <= maximumInfluence) { "A mine blast must stay within the maximum influence" }
        require(mineQuickFuseSeconds > 0f && mineQuickFuseSeconds < mineFuseSeconds) { "A quick fuse must be shorter than the fuse" }
        require(mineBlastShare > 0f && mineBlastShare <= 0.5f) { "A mine blast takes at most half of the integrity" }
        require(maximumMineDetonationsPerStep in 1..8) { "Mine detonations per step must be in 1..8" }
        require(ventMaximumRadius >= ventMinimumCandidate) { "Vents must fit their smallest candidate" }
        require(ventMaximumRadius + ventPlumeMargin <= maximumInfluence) { "A plume must stay within the maximum influence" }
        require(ventEruptSeconds > 0f && ventEruptSeconds < ventPeriodSeconds) { "A vent erupts for part of its period" }
        require(ventTelegraphSeconds in 0f..(ventPeriodSeconds - ventEruptSeconds)) { "A vent telegraph must fit its quiet time" }
        require(ventHeat in 0f..100f) { "Vent heat must be in 0..100" }
        require(ventStatusInterval > 0f) { "Vent status interval must be positive" }
        require(phasePeriodSeconds > 0f) { "Phase period must be positive" }
        require(phaseSolidShare > 0f && phaseSolidShare < 1f) { "A phase shard is solid for part of its period" }
        require(phaseTelegraphSeconds in 0f..(phasePeriodSeconds * (1f - phaseSolidShare))) { "A phase telegraph must fit its ghost time" }
        require(gateAlignment in 0f..1f) { "Gate alignment must be in 0..1" }
        require(boostLethalShare > 0f && boostLethalShare < 1f) { "A boost must stay below the lethal speed" }
        require(slingTurnRadians > 0f && slingTurnRadians <= PI.toFloat()) { "A sling turn must be in (0, pi]" }
        listOf(
            wellCorePull, wellEnemyPull, slingMinimumSpeed, slingImpulse, gateImpulse, gateCooldownSeconds, crystalShardDamage,
            mineFuseSeconds, mineBlastEnemyDamage, ventImpulse, grazeMinimumSpeed, wellMinimumCandidate, gateMinimumCandidate,
            crystalMaximumCandidate, ventMinimumCandidate, mineArmMargin, ventPlumeMargin, mineClusterMaximumRadius,
        ).forEach { require(it.isFinite() && it >= 0f) { "Obstacle kind impulses, pulls, speeds and cooldowns must be finite and non-negative" } }
        require(crystalSpeedRetention > 0f && crystalSpeedRetention <= 1f) { "Crystal speed retention must be in (0, 1]" }
        require(crystalShards in 0..ContentBounds.MAX_SHARDS) { "Crystal shards must be in 0..${ContentBounds.MAX_SHARDS}" }
        require(crystalData in 0..4) { "Crystal Data must be in 0..4" }
        require(grazeDistance > 0f && grazeDistance <= NEAR_OBSTACLE_MAX_DISTANCE) { "Graze distance must be in (0, $NEAR_OBSTACLE_MAX_DISTANCE]" }
        require(grazeCooldownSeconds >= 0.1f) { "Graze cooldown must be at least 0.1 s" }
        require(maximumInfluence >= largestReach && maximumInfluence <= ContentBounds.MAX_OBSTACLE_INFLUENCE) {
            "Maximum influence must cover every reach and stay within ${ContentBounds.MAX_OBSTACLE_INFLUENCE}"
        }
    }

    /** The farthest area reach of any kind: well field, mine arming, blast and plume. */
    val largestReach: Float
        get() = max(max(wellInfluence, mineClusterMaximumRadius + mineArmMargin), max(mineBlastRadius, ventMaximumRadius + ventPlumeMargin))
}

/** What an [ItemRule.ObstacleTuning] changes. */
enum class ObstacleAspect { RESTITUTION, COLLISION_DAMAGE, LETHAL_SPEED, BOOST_POWER, WELL_PULL }

/** Stateless hash of [seed] and [salt] in [0, 1) (24 exact bits; Int wrap-around is identical on every platform). */
fun obstacleSeedUnit(seed: Int, salt: Int): Float {
    var mixed = seed * -0x61c88647 + salt * 0x27d4eb2d
    mixed = (mixed xor (mixed ushr 15)) * 0x2c1b3c6d
    mixed = (mixed xor (mixed ushr 12)) * 0x297a2d39
    mixed = mixed xor (mixed ushr 15)
    return (mixed ushr 8) / 16_777_216f
}

private const val PHASE_SALT = 61
private const val VENT_SALT = 67

private fun cycleSeconds(elapsed: Float, seed: Int, period: Float, salt: Int): Float {
    val shifted = elapsed + obstacleSeedUnit(seed, salt) * period
    return shifted - floor(shifted / period) * period
}

/** True while the phase shard with [seed] is materialised at [elapsed]. */
fun ObstacleKindTuning.phaseShardSolid(elapsed: Float, seed: Int): Boolean =
    cycleSeconds(elapsed, seed, phasePeriodSeconds, PHASE_SALT) < phasePeriodSeconds * phaseSolidShare

/** 0 while solid, else seconds until the phase shard materialises. */
fun ObstacleKindTuning.phaseShardSecondsToSolid(elapsed: Float, seed: Int): Float {
    val cycle = cycleSeconds(elapsed, seed, phasePeriodSeconds, PHASE_SALT)
    return if (cycle < phasePeriodSeconds * phaseSolidShare) 0f else phasePeriodSeconds - cycle
}

/** True while the thermal vent with [seed] erupts at [elapsed]. */
fun ObstacleKindTuning.ventErupting(elapsed: Float, seed: Int): Boolean =
    cycleSeconds(elapsed, seed, ventPeriodSeconds, VENT_SALT) < ventEruptSeconds

/** 0 while erupting, else seconds until the next eruption. */
fun ObstacleKindTuning.ventSecondsToEruption(elapsed: Float, seed: Int): Float {
    val cycle = cycleSeconds(elapsed, seed, ventPeriodSeconds, VENT_SALT)
    return if (cycle < ventEruptSeconds) 0f else ventPeriodSeconds - cycle
}

/** While the vent with [seed] erupts, the second its eruption ends (`elapsed + erupt − cycle`). */
fun ObstacleKindTuning.ventEruptionEnd(elapsed: Float, seed: Int): Float =
    elapsed + (ventEruptSeconds - cycleSeconds(elapsed, seed, ventPeriodSeconds, VENT_SALT))

/** Gate normal angle: the seed's low 16 bits (invertible, so tests can build a gate at a chosen angle). */
fun gateAngle(shapeSeed: Int): Float = (shapeSeed and 0xFFFF) / 65_536f * 6.2831855f

/** From an accelerator gate's centre to each post's centre, for a gate body of [bodyRadius] (the posts sit inside it). */
fun ObstacleKindTuning.gateHalfSpan(bodyRadius: Float): Float = max(0f, bodyRadius - gatePostRadius)

/** True when a candidate of [candidateRadius] may become [kind]. */
fun ObstacleKindTuning.eligible(kind: ObstacleKind, candidateRadius: Float): Boolean = when (kind) {
    ObstacleKind.GRAVITY_WELL -> candidateRadius >= wellMinimumCandidate
    ObstacleKind.ACCEL_GATE -> candidateRadius >= gateMinimumCandidate
    ObstacleKind.CRYSTAL -> candidateRadius <= crystalMaximumCandidate
    ObstacleKind.THERMAL_VENT -> candidateRadius >= ventMinimumCandidate
    ObstacleKind.DERELICT_MINE, ObstacleKind.PHASE_SHARD -> true
    ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE -> false
}

/** The body radius a candidate of [candidateRadius] takes as [kind]; never larger than the candidate. */
fun ObstacleKindTuning.bodyRadius(kind: ObstacleKind, candidateRadius: Float): Float = when (kind) {
    ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE, ObstacleKind.CRYSTAL, ObstacleKind.PHASE_SHARD -> candidateRadius
    ObstacleKind.GRAVITY_WELL -> wellCoreRadius
    ObstacleKind.ACCEL_GATE -> min(candidateRadius, gateMaximumRadius)
    ObstacleKind.DERELICT_MINE -> min(candidateRadius, mineClusterMaximumRadius)
    ObstacleKind.THERMAL_VENT -> min(candidateRadius, ventMaximumRadius)
}
