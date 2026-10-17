// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType

/**
 * [kinetickk.ball.gameplay.nucleus.render.EnemyProjection.affinityBits] an enemy of [type] carries in
 * a run, after the nucleus profile table: Drifters are neutral, the Elite's [eliteAspect] is immune,
 * and the Architect resists [architectAspect] (none by default) in [architectPhase], [shifting] or
 * not. The weakness is always the counter of the aspect.
 */
internal fun runAffinity(
    type: EnemyType,
    eliteAspect: StatusElement = StatusElement.ION,
    architectAspect: StatusElement? = null,
    architectPhase: Int = 0,
    shifting: Boolean = false,
): Int {
    val aspect: StatusElement? = when (type) {
        EnemyType.DRIFTER -> null
        EnemyType.SHOOTER -> StatusElement.ION
        EnemyType.CHARGER, EnemyType.INTERCEPTOR -> StatusElement.THERMAL
        EnemyType.WEAVER -> StatusElement.CRYO
        EnemyType.WARDEN -> StatusElement.GRAVITIC
        EnemyType.SPLITTER -> StatusElement.ENTROPY
        EnemyType.ELITE -> eliteAspect
        EnemyType.ARCHITECT -> architectAspect
    }
    val armored = type in setOf(EnemyType.CHARGER, EnemyType.WARDEN, EnemyType.SPLITTER, EnemyType.ELITE, EnemyType.ARCHITECT)
    return EnemyAffinity.encode(
        aspect = aspect?.ordinal ?: EnemyAffinity.NONE,
        immune = type == EnemyType.WARDEN || type == EnemyType.ELITE,
        weakness = aspect?.let(::counterOf)?.ordinal ?: EnemyAffinity.NONE,
        armored = armored,
        fragile = type == EnemyType.INTERCEPTOR,
        shifting = type == EnemyType.ARCHITECT && shifting,
        phase = if (type == EnemyType.ARCHITECT) architectPhase else 0,
    )
}

/**
 * A tracked boss's affinity for HUD fixtures: the Elite immune to Ion, the Architect shifting into its
 * second phase hardened against Thermal (so weak to Cryo).
 */
internal fun bossAffinity(type: EnemyType): Int =
    runAffinity(type, architectAspect = StatusElement.THERMAL, architectPhase = 1, shifting = true)

/** Thermal and Cryo counter each other, Sonic and Void counter each other; Ion, Entropy and Gravitic form a cycle. */
internal fun counterOf(element: StatusElement): StatusElement = when (element) {
    StatusElement.THERMAL -> StatusElement.CRYO
    StatusElement.CRYO -> StatusElement.THERMAL
    StatusElement.ION -> StatusElement.ENTROPY
    StatusElement.ENTROPY -> StatusElement.GRAVITIC
    StatusElement.GRAVITIC -> StatusElement.ION
    StatusElement.SONIC -> StatusElement.VOID
    StatusElement.VOID -> StatusElement.SONIC
}
