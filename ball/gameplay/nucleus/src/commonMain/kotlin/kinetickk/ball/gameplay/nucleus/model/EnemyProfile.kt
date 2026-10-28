// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.EnemyType

/**
 * Static per-type combat data. [aspect] is an element ordinal or -1; Elites, the Herald and the Architect
 * carry their aspect as instance state instead ([instanceAspect]), assigned with this table's [aspectImmune].
 * [impactTaken] scales impact and dash hits, [weaponTaken] weapon hits, element-less procs and character
 * abilities; the Architect's frame starts from them and hardens each phase, and a guarded Reflector meets
 * hits with [guardedImpactTaken] and [guardedWeaponTaken]. [knockback] scales every player-caused
 * displacement; [victimFactor] scales every %max-integrity term. [shotDamage] is the base of each hostile
 * projectile the type fires (before hostile run growth); [dataDrops] and [matterReward] are its kill rewards.
 * [rank] mirrors the content roster. [escortable] kinds may serve as an Elite or Herald aspect escort; [phasesOut]
 * kinds are intangible while shifting; [slamTaken] scales slam damage; [directionalGuard] kinds decide their guard by
 * the hit direction; the kinetic frame hardens by [frameStep] per phase over the first [frameSteps] phases.
 */
internal class EnemyProfile(
    val baseHealth: Float,
    val radius: Float,
    val spawnActionTimer: Float,
    val impactTaken: Float,
    val weaponTaken: Float,
    val aspect: Int,
    val aspectImmune: Boolean,
    val instanceAspect: Boolean,
    val knockback: Float,
    val victimFactor: Float,
    val shotDamage: Float = 0f,
    val dataDrops: Int = 1,
    val matterReward: Float = 1f,
    val guardedImpactTaken: Float = impactTaken,
    val guardedWeaponTaken: Float = weaponTaken,
    val rank: EnemyRank = EnemyRank.ORDINARY,
    val escortable: Boolean = true,
    val phasesOut: Boolean = false,
    val slamTaken: Float = 1f,
    val directionalGuard: Boolean = false,
    val frameStep: Float = 0f,
    val frameSteps: Int = 0,
)

private val DRIFTER_PROFILE = EnemyProfile(28f, 17f, 0.2f, 1f, 1f, -1, false, false, 1f, 1f, dataDrops = 1)
private val SHOOTER_PROFILE = EnemyProfile(60f, 20f, 0.7f, 1f, 1f, StatusElement.ION.ordinal, false, false, 1f, 1f, shotDamage = 10f, dataDrops = 2)
private val CHARGER_PROFILE = EnemyProfile(82f, 23f, 1.2f, 0.6f, 1f, StatusElement.THERMAL.ordinal, false, false, 0f, 0.7f, dataDrops = 3)
private val INTERCEPTOR_PROFILE = EnemyProfile(68f, 18f, 1.4f, 1.25f, 0.85f, StatusElement.THERMAL.ordinal, false, false, 1f, 1f, dataDrops = 3)
private val WEAVER_PROFILE = EnemyProfile(74f, 21f, 0.9f, 1f, 1f, StatusElement.CRYO.ordinal, false, false, 1f, 1f, shotDamage = 9f, dataDrops = 3)
private val WARDEN_PROFILE = EnemyProfile(176f, 29f, 1.3f, 0.7f, 0.85f, StatusElement.GRAVITIC.ordinal, true, false, 0f, 0.5f, shotDamage = 8f, dataDrops = 5)
private val SPLITTER_PROFILE = EnemyProfile(120f, 27f, 0.4f, 0.8f, 1f, StatusElement.ENTROPY.ordinal, false, false, 1f, 0.7f, dataDrops = 4)
private val ELITE_PROFILE = EnemyProfile(
    360f, 38f, 0.5f, 0.85f, 0.85f, -1, true, true, 0.35f, 0.15f, shotDamage = 12f, dataDrops = 8, matterReward = 4f,
    rank = EnemyRank.ELITE,
)
private val ARCHITECT_PROFILE = EnemyProfile(
    1_960f, 74f, 1.2f, 0.8f, 0.8f, -1, false, true, 0f, 0.04f, shotDamage = 14f, dataDrops = 20, matterReward = 30f,
    rank = EnemyRank.BOSS, frameStep = 0.05f, frameSteps = 2,
)
private val SWARMLING_PROFILE = EnemyProfile(9f, 10f, 0.2f, 1f, 1f, -1, false, false, 1.3f, 1f, dataDrops = 1, matterReward = 0.3f)
private val BOMBER_PROFILE = EnemyProfile(40f, 16f, 0.5f, 1f, 1f, StatusElement.THERMAL.ordinal, false, false, 1.1f, 1f, dataDrops = 2)
private val LANCER_PROFILE = EnemyProfile(90f, 22f, 1.0f, 0.8f, 1f, StatusElement.ION.ordinal, false, false, 0.4f, 0.8f, dataDrops = 3)
private val SNIPER_PROFILE = EnemyProfile(55f, 19f, 1.5f, 1f, 1f, StatusElement.CRYO.ordinal, false, false, 1f, 1f, shotDamage = 22f, dataDrops = 3)
private val AEGIS_PROFILE = EnemyProfile(140f, 26f, 1.0f, 0.9f, 0.9f, StatusElement.GRAVITIC.ordinal, false, false, 0.8f, 0.7f, dataDrops = 4, matterReward = 2f)
private val MINELAYER_PROFILE = EnemyProfile(100f, 24f, 1.2f, 1f, 1f, StatusElement.ENTROPY.ordinal, false, false, 0.9f, 0.8f, shotDamage = 14f, dataDrops = 3)
private val PHANTOM_PROFILE = EnemyProfile(70f, 20f, 1.6f, 1f, 1f, -1, false, false, 1f, 1f, dataDrops = 3, phasesOut = true)
private val BROODMOTHER_PROFILE = EnemyProfile(420f, 34f, 2.0f, 0.8f, 0.9f, StatusElement.GRAVITIC.ordinal, false, false, 0.3f, 0.5f, dataDrops = 6, matterReward = 2f)
private val REFLECTOR_PROFILE = EnemyProfile(
    220f, 30f, 1.0f, 1f, 1f, -1, false, false, 0.5f, 0.6f, shotDamage = 10f, dataDrops = 5, matterReward = 2f,
    guardedImpactTaken = 0.5f, guardedWeaponTaken = 0.25f, directionalGuard = true,
)
private val HERALD_PROFILE = EnemyProfile(
    2_400f, 52f, 1.0f, 0.85f, 0.85f, -1, true, true, 0.2f, 0.15f, shotDamage = 13f, dataDrops = 20, matterReward = 12f,
    rank = EnemyRank.MINI_BOSS,
)

// 0.6.0 kinds. No run spawns them yet: Comet and Pylon wait behind their disabled gates, the rest live outside SPACE
// or arrive only through encounters.
private val COMET_PROFILE = EnemyProfile(
    30f, 14f, 1.0f, 1.5f, 0.7f, StatusElement.CRYO.ordinal, false, false, 1.2f, 1f, dataDrops = 4, matterReward = 1.2f,
    escortable = false,
)
private val PYLON_PROFILE = EnemyProfile(
    150f, 22f, 1.6f, 1.6f, 0.5f, StatusElement.ION.ordinal, false, false, 0f, 0.6f, shotDamage = 9f, dataDrops = 4,
    matterReward = 1.5f, escortable = false, slamTaken = 3.2f,
)
private val SENTRY_PROFILE = EnemyProfile(
    110f, 20f, 1.2f, 1.3f, 0.8f, StatusElement.THERMAL.ordinal, false, false, 0f, 0.7f, shotDamage = 10f, dataDrops = 3,
    matterReward = 1.2f, escortable = false,
)
private val CRAWLER_PROFILE = EnemyProfile(
    16f, 12f, 0.2f, 1f, 1f, StatusElement.ENTROPY.ordinal, false, false, 1.2f, 1f, dataDrops = 1, matterReward = 0.4f,
)
private val SERAPH_PROFILE = EnemyProfile(
    90f, 21f, 1.0f, 1.15f, 0.9f, StatusElement.VOID.ordinal, false, false, 0.8f, 0.8f, dataDrops = 4, matterReward = 1.5f,
)
private val CHOIR_PROFILE = EnemyProfile(
    130f, 25f, 1.4f, 0.9f, 1f, StatusElement.SONIC.ordinal, false, false, 0.6f, 0.7f, shotDamage = 9f, dataDrops = 4,
    matterReward = 1.5f,
)
private val IMP_PROFILE = EnemyProfile(
    34f, 15f, 0.5f, 1.1f, 1f, StatusElement.THERMAL.ordinal, false, false, 1.1f, 1f, dataDrops = 2, matterReward = 1f,
)
private val HOUND_PROFILE = EnemyProfile(44f, 16f, 0.8f, 1f, 1f, -1, false, false, 1f, 1f, dataDrops = 2, matterReward = 1f)
private val CIPHER_PROFILE = EnemyProfile(
    70f, 19f, 1.2f, 1f, 1f, StatusElement.VOID.ordinal, false, false, 1f, 1f, shotDamage = 11f, dataDrops = 4,
    matterReward = 1.5f, phasesOut = true,
)
private val MIRROR_PROFILE = EnemyProfile(
    85f, 20f, 1.0f, 1f, 1f, -1, false, false, 0.7f, 0.8f, shotDamage = 10f, dataDrops = 4, matterReward = 1.5f,
)
private val WARDEN_PRIME_PROFILE = EnemyProfile(
    1_800f, 44f, 1.0f, 0.8f, 0.85f, StatusElement.GRAVITIC.ordinal, true, false, 0.15f, 0.15f, shotDamage = 11f,
    dataDrops = 18, matterReward = 10f, rank = EnemyRank.MINI_BOSS,
)
private val BROOD_QUEEN_PROFILE = EnemyProfile(
    2_000f, 46f, 1.5f, 0.9f, 0.9f, StatusElement.ENTROPY.ordinal, true, false, 0.2f, 0.15f, shotDamage = 10f,
    dataDrops = 18, matterReward = 10f, rank = EnemyRank.MINI_BOSS,
)
private val THRONE_PROFILE = EnemyProfile(
    2_200f, 48f, 1.2f, 0.9f, 0.6f, StatusElement.VOID.ordinal, true, false, 0f, 0.15f, shotDamage = 12f,
    dataDrops = 18, matterReward = 10f, guardedImpactTaken = 0.1f, guardedWeaponTaken = 0.15f,
    rank = EnemyRank.MINI_BOSS, directionalGuard = true,
)
private val PYRE_HERALD_PROFILE = EnemyProfile(
    2_100f, 50f, 1.0f, 0.85f, 0.85f, StatusElement.THERMAL.ordinal, true, false, 0.2f, 0.15f, shotDamage = 12f,
    dataDrops = 18, matterReward = 10f, rank = EnemyRank.MINI_BOSS,
)
private val CARTOGRAPHER_PROFILE = EnemyProfile(
    3_200f, 54f, 1.5f, 0.85f, 0.85f, -1, false, false, 0f, 0.04f, shotDamage = 12f, dataDrops = 30, matterReward = 40f,
    rank = EnemyRank.SECRET_BOSS, phasesOut = true,
)
/** The Antecedent's aspect is adaptive instance state and never immune. */
private val ANTECEDENT_PROFILE = EnemyProfile(
    4_000f, 58f, 1.5f, 0.8f, 0.8f, -1, false, true, 0f, 0.04f, shotDamage = 13f, dataDrops = 30, matterReward = 50f,
    guardedImpactTaken = 0.15f, guardedWeaponTaken = 0.2f, rank = EnemyRank.SECRET_BOSS, directionalGuard = true,
    frameStep = 0.04f, frameSteps = 3,
)

internal val EnemyType.profile: EnemyProfile
    get() = when (this) {
        EnemyType.DRIFTER -> DRIFTER_PROFILE
        EnemyType.SHOOTER -> SHOOTER_PROFILE
        EnemyType.CHARGER -> CHARGER_PROFILE
        EnemyType.INTERCEPTOR -> INTERCEPTOR_PROFILE
        EnemyType.WEAVER -> WEAVER_PROFILE
        EnemyType.WARDEN -> WARDEN_PROFILE
        EnemyType.SPLITTER -> SPLITTER_PROFILE
        EnemyType.ELITE -> ELITE_PROFILE
        EnemyType.ARCHITECT -> ARCHITECT_PROFILE
        EnemyType.SWARMLING -> SWARMLING_PROFILE
        EnemyType.BOMBER -> BOMBER_PROFILE
        EnemyType.LANCER -> LANCER_PROFILE
        EnemyType.SNIPER -> SNIPER_PROFILE
        EnemyType.AEGIS -> AEGIS_PROFILE
        EnemyType.MINELAYER -> MINELAYER_PROFILE
        EnemyType.PHANTOM -> PHANTOM_PROFILE
        EnemyType.BROODMOTHER -> BROODMOTHER_PROFILE
        EnemyType.REFLECTOR -> REFLECTOR_PROFILE
        EnemyType.HERALD -> HERALD_PROFILE
        EnemyType.COMET -> COMET_PROFILE
        EnemyType.PYLON -> PYLON_PROFILE
        EnemyType.SENTRY -> SENTRY_PROFILE
        EnemyType.CRAWLER -> CRAWLER_PROFILE
        EnemyType.SERAPH -> SERAPH_PROFILE
        EnemyType.CHOIR -> CHOIR_PROFILE
        EnemyType.IMP -> IMP_PROFILE
        EnemyType.HOUND -> HOUND_PROFILE
        EnemyType.CIPHER -> CIPHER_PROFILE
        EnemyType.MIRROR -> MIRROR_PROFILE
        EnemyType.WARDEN_PRIME -> WARDEN_PRIME_PROFILE
        EnemyType.BROOD_QUEEN -> BROOD_QUEEN_PROFILE
        EnemyType.THRONE -> THRONE_PROFILE
        EnemyType.PYRE_HERALD -> PYRE_HERALD_PROFILE
        EnemyType.CARTOGRAPHER -> CARTOGRAPHER_PROFILE
        EnemyType.ANTECEDENT -> ANTECEDENT_PROFILE
    }

/** Splitter fragments are Drifters with their own base integrity and radius. */
internal const val SPLITTER_FRAGMENT_HEALTH: Float = 21f
internal const val SPLITTER_FRAGMENT_RADIUS: Float = 12f

/** Sealed-Anomaly defenders are Wardens at this share of the Warden integrity formula. */
internal const val POINT_DEFENDER_HEALTH_SHARE: Float = 0.4f

/**
 * The one counter rule: Thermal and Cryo counter each other, Sonic and Void counter each other; Ion,
 * Entropy and Gravitic form a cycle. Takes and returns element ordinals, -1 for none.
 */
internal fun counterElement(element: Int): Int = when (element) {
    StatusElement.THERMAL.ordinal -> StatusElement.CRYO.ordinal
    StatusElement.CRYO.ordinal -> StatusElement.THERMAL.ordinal
    StatusElement.ION.ordinal -> StatusElement.ENTROPY.ordinal
    StatusElement.ENTROPY.ordinal -> StatusElement.GRAVITIC.ordinal
    StatusElement.GRAVITIC.ordinal -> StatusElement.ION.ordinal
    StatusElement.SONIC.ordinal -> StatusElement.VOID.ordinal
    StatusElement.VOID.ordinal -> StatusElement.SONIC.ordinal
    else -> -1
}

/** Element ordinal of the enemy's aspect, or -1 when neutral. */
internal val Enemy.aspectElement: Int
    get() {
        val profile = type.profile
        return if (profile.instanceAspect) aspect else profile.aspect
    }

/** True when the aspect refuses its element entirely instead of resisting it. */
internal val Enemy.aspectIsImmune: Boolean
    get() {
        val profile = type.profile
        return aspectElement >= 0 && if (profile.instanceAspect) aspectImmune else profile.aspectImmune
    }

/** Element ordinal the enemy is weak to, always the counter of its aspect, or -1. */
internal val Enemy.weaknessElement: Int get() = counterElement(aspectElement)

/**
 * A boss frame hardens phase by phase from its profile value: each phase after the first, up to the profile's
 * [EnemyProfile.frameSteps], takes [EnemyProfile.frameStep] less of every kinetic hit, whatever the build.
 */
private fun Enemy.kineticTaken(profileTaken: Float): Float {
    val profile = type.profile
    return profileTaken - profile.frameStep * phase.coerceIn(0, profile.frameSteps)
}

internal val Enemy.impactTaken: Float
    get() = if (guarded) type.profile.guardedImpactTaken else kineticTaken(type.profile.impactTaken)

internal val Enemy.weaponTaken: Float
    get() = if (guarded) type.profile.guardedWeaponTaken else kineticTaken(type.profile.weaponTaken)

internal val Enemy.knockback: Float get() = type.profile.knockback
