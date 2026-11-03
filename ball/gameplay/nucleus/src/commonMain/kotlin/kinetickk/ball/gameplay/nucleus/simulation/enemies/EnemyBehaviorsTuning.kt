// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.enemies

internal const val CHARGER_STRIKE_SECONDS: Float = 0.55f
internal const val BOMBER_PRIME_DISTANCE: Float = 110f
internal const val BOMBER_PRIME_SECONDS: Float = 0.6f
internal const val BOMBER_BLAST_RADIUS: Float = 120f
internal const val BOMBER_BLAST_DAMAGE: Float = 18f
/** A Core impact at least this fast destroys an unprimed Bomber outright, without a blast. */
internal const val BOMBER_DEFUSE_SPEED: Float = 450f
internal const val LANCER_TELEGRAPH_SECONDS: Float = 0.7f
internal const val LANCER_DISTANCE: Float = 900f
internal const val LANCER_SPEED: Float = 1_100f
internal const val LANCER_DAMAGE: Float = 16f
internal const val LANCER_RECOVERY_SECONDS: Float = 1.2f
internal const val LANCER_STUN_SECONDS: Float = 1f
/** Pause after a recovery before the next telegraph, at the base attack rate. */
internal const val LANCER_COOLDOWN_SECONDS: Float = 2.6f
internal const val LANCER_ENGAGE_RANGE: Float = 650f
internal const val SNIPER_MIN_RANGE: Float = 600f
internal const val SNIPER_MAX_RANGE: Float = 820f
internal const val SNIPER_AIM_SECONDS: Float = 1.1f
internal const val SNIPER_SHOT_SPEED: Float = 900f
internal const val AEGIS_LINK_RANGE: Float = 220f
internal const val AEGIS_MAX_LINKS: Int = 3
/** Linked allies take this share less damage. */
internal const val AEGIS_LINK_REDUCTION: Float = 0.6f
internal const val MINE_ARM_SECONDS: Float = 0.8f
internal const val MINE_LIFE_SECONDS: Float = 8f
internal const val MINE_TRIGGER_RADIUS: Float = 60f
internal const val MINE_BLAST_RADIUS: Float = 70f
internal const val MINE_RADIUS: Float = 9f
internal const val MINE_DROP_SECONDS: Float = 3f
/** Minelayers never drop a mine closer than this to the Core, and stop while this many mines are live. */
internal const val MINE_MINIMUM_CORE_DISTANCE: Float = 260f
internal const val MAX_LIVE_MINES: Int = 10
internal const val PHANTOM_PHASE_SECONDS: Float = 1.4f
internal const val PHANTOM_REAPPEAR_DISTANCE: Float = 260f
internal const val PHANTOM_WINDUP_SECONDS: Float = 0.35f
internal const val PHANTOM_STRIKE_SPEED: Float = 520f
internal const val PHANTOM_STRIKE_SECONDS: Float = 0.5f
internal const val PHANTOM_STRIKE_DAMAGE: Float = 14f
internal const val BROOD_SIZE: Int = 3
internal const val BROOD_SECONDS: Float = 4f
internal const val REFLECTOR_ARC: Float = 60f * (kotlin.math.PI / 180.0).toFloat()
internal const val REFLECTOR_TURN_RATE: Float = 1.1f
internal const val REFLECTOR_SHOT_SPEED: Float = 600f
internal const val REFLECTOR_REFLECT_SECONDS: Float = 0.3f
internal const val HERALD_SHIELD_SECONDS: Float = 3f
internal const val HERALD_SHIELD_INTERVAL_SECONDS: Float = 12f
internal const val HERALD_FIRST_SHIELD_SECONDS: Float = 6f
