// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/**
 * Every hostile kind. Runs and profiles never persist an enemy kind, but the order is the roster order
 * of [defaultEnemyDefinitions] and of the Codex, so entries are append-only.
 */
enum class EnemyType {
    DRIFTER,
    SHOOTER,
    CHARGER,
    INTERCEPTOR,
    WEAVER,
    WARDEN,
    SPLITTER,
    ELITE,
    ARCHITECT,
    SWARMLING,
    BOMBER,
    LANCER,
    SNIPER,
    AEGIS,
    MINELAYER,
    PHANTOM,
    BROODMOTHER,
    REFLECTOR,
    HERALD,
}

/**
 * Roster data of one [EnemyType]: what the Codex shows and when the spawn director may pick it.
 * [timeGateSeconds] is compared with the run's threat time and [minimumRebirth] with the selected
 * Rebirth tier. A zero [weight] keeps the kind out of ordinary spawn picks (scheduled kinds). An
 * ordinary kind's weight can fall linearly to [lateWeightFraction] of itself by [lateWeightAtSeconds].
 * [isBoss] marks the run's final boss and [isMiniBoss] the scheduled mini-boss; [isElite] the
 * scheduled Elite.
 */
data class EnemyDefinition(
    val type: EnemyType,
    val name: String,
    val description: String,
    val threat: Int,
    val timeGateSeconds: Float,
    val minimumRebirth: Int,
    val weight: Float,
    val isElite: Boolean = false,
    val isBoss: Boolean = false,
    val isMiniBoss: Boolean = false,
    val lateWeightFraction: Float = 1f,
    val lateWeightAtSeconds: Float = 0f,
) {
    init {
        require(name.isNotBlank()) { "Enemy name must not be blank" }
        require(description.isNotBlank()) { "Enemy description must not be blank" }
        require(threat in MIN_ENEMY_THREAT..MAX_ENEMY_THREAT) { "Enemy threat must be in $MIN_ENEMY_THREAT..$MAX_ENEMY_THREAT" }
        require(timeGateSeconds.isFinite() && timeGateSeconds >= 0f) { "Enemy time gate must be finite and non-negative" }
        require(minimumRebirth in ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL) {
            "Enemy minimum Rebirth must be a valid tier"
        }
        require(weight.isFinite() && weight >= 0f) { "Enemy weight must be finite and non-negative" }
        require(lateWeightFraction.isFinite() && lateWeightFraction in 0f..1f) { "Late weight fraction must be in 0..1" }
        require(lateWeightAtSeconds.isFinite() && lateWeightAtSeconds >= 0f) { "Late weight time must be non-negative" }
        require(!(isBoss && isMiniBoss)) { "An enemy is either the boss or a mini-boss" }
        require(!((isBoss || isMiniBoss || isElite) && weight > 0f)) { "Scheduled enemies never take ordinary spawn weight" }
    }

    /** True for kinds the director never picks for ordinary events. */
    val isScheduled: Boolean get() = isElite || isBoss || isMiniBoss

    /** Ordinary spawn weight before the director's ramp-in and repetition rules. */
    fun baseWeight(elapsedSeconds: Float): Float {
        if (lateWeightAtSeconds <= 0f || lateWeightFraction >= 1f) return weight
        val progress = (elapsedSeconds / lateWeightAtSeconds).coerceIn(0f, 1f)
        return weight * (1f - (1f - lateWeightFraction) * progress)
    }
}

const val MIN_ENEMY_THREAT: Int = 1
const val MAX_ENEMY_THREAT: Int = 5

/** The shipped roster in [EnemyType] order. */
fun defaultEnemyDefinitions(): ImmutableList<EnemyDefinition> = listOf(
    EnemyDefinition(
        EnemyType.DRIFTER, "Drifter", "Drifts straight at the Core. Weak alone, dangerous in numbers.",
        threat = 1, timeGateSeconds = 0f, minimumRebirth = 0, weight = 22f,
        lateWeightFraction = 0.4f, lateWeightAtSeconds = 360f,
    ),
    EnemyDefinition(
        EnemyType.SHOOTER, "Shooter", "Holds its distance and fires aimed bolts.",
        threat = 2, timeGateSeconds = 38f, minimumRebirth = 0, weight = 17f,
    ),
    EnemyDefinition(
        EnemyType.CHARGER, "Charger", "Creeps closer, winds up and dashes through the Core.",
        threat = 2, timeGateSeconds = 150f, minimumRebirth = 0, weight = 14f,
    ),
    EnemyDefinition(
        EnemyType.INTERCEPTOR, "Interceptor", "Leads your movement and boosts to cut you off.",
        threat = 2, timeGateSeconds = 90f, minimumRebirth = 0, weight = 14f,
    ),
    EnemyDefinition(
        EnemyType.WEAVER, "Weaver", "Weaves from side to side and fires walls of bolts.",
        threat = 2, timeGateSeconds = 150f, minimumRebirth = 0, weight = 12f,
    ),
    EnemyDefinition(
        EnemyType.WARDEN, "Warden", "Keeps its range, pulls the Core into its well and fires radial bursts.",
        threat = 3, timeGateSeconds = 240f, minimumRebirth = 0, weight = 9f,
    ),
    EnemyDefinition(
        EnemyType.SPLITTER, "Splitter", "Sways forward and breaks into two fragments when destroyed.",
        threat = 3, timeGateSeconds = 240f, minimumRebirth = 0, weight = 12f,
    ),
    EnemyDefinition(
        EnemyType.ELITE, "Elite", "Carries an elemental aspect, fires radial volleys and drops a Key and a Relic.",
        threat = 4, timeGateSeconds = 38f, minimumRebirth = 0, weight = 0f, isElite = true,
    ),
    EnemyDefinition(
        EnemyType.ARCHITECT, "Architect", "The final boss. It shifts between phases and hardens against your build.",
        threat = 5, timeGateSeconds = 720f, minimumRebirth = 0, weight = 0f, isBoss = true,
    ),
    EnemyDefinition(
        EnemyType.SWARMLING, "Swarmling", "Arrives in packs of six to ten and flocks around the Core.",
        threat = 1, timeGateSeconds = 75f, minimumRebirth = 0, weight = 10f,
    ),
    EnemyDefinition(
        EnemyType.BOMBER, "Bomber", "Accelerates at the Core, primes and explodes. A fast impact destroys it before it primes.",
        threat = 3, timeGateSeconds = 200f, minimumRebirth = 0, weight = 8f,
    ),
    EnemyDefinition(
        EnemyType.LANCER, "Lancer", "Stops, marks a line and lances along it. Lancing into an obstacle stuns it.",
        threat = 3, timeGateSeconds = 240f, minimumRebirth = 0, weight = 7f,
    ),
    EnemyDefinition(
        EnemyType.SNIPER, "Sniper", "Keeps far away, shows its sight line and fires one heavy shot.",
        threat = 3, timeGateSeconds = 330f, minimumRebirth = 0, weight = 6f,
    ),
    EnemyDefinition(
        EnemyType.AEGIS, "Aegis", "Tethers up to three allies, who take far less damage, and flees the Core.",
        threat = 3, timeGateSeconds = 390f, minimumRebirth = 0, weight = 4f,
    ),
    EnemyDefinition(
        EnemyType.MINELAYER, "Minelayer", "Circles at mid range and drops mines that arm and explode.",
        threat = 3, timeGateSeconds = 450f, minimumRebirth = 0, weight = 5f,
    ),
    EnemyDefinition(
        EnemyType.PHANTOM, "Phantom", "Phases out of reach, reappears near the Core and strikes.",
        threat = 4, timeGateSeconds = 300f, minimumRebirth = 2, weight = 6f,
    ),
    EnemyDefinition(
        EnemyType.BROODMOTHER, "Broodmother", "Slow and massive, it births Swarmlings every few seconds.",
        threat = 4, timeGateSeconds = 420f, minimumRebirth = 3, weight = 3f,
    ),
    EnemyDefinition(
        EnemyType.REFLECTOR, "Reflector", "A front shield blunts impacts and returns shots. Strike it from behind.",
        threat = 4, timeGateSeconds = 360f, minimumRebirth = 4, weight = 5f,
    ),
    EnemyDefinition(
        EnemyType.HERALD, "Herald", "A mini-boss with spiral volleys, aspect escorts and a shield only impacts can break.",
        threat = 5, timeGateSeconds = 360f, minimumRebirth = 2, weight = 0f, isMiniBoss = true,
    ),
).toImmutableList()
