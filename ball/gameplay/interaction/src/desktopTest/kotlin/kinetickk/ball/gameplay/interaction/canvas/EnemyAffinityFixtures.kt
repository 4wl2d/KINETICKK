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
 * not. The 0.6.0 mini-bosses are immune to their own aspect; the Antecedent's adaptive aspect is unset until its
 * fight assigns one. The weakness is always the counter of the aspect.
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
        EnemyType.ELITE, EnemyType.HERALD -> eliteAspect
        EnemyType.ARCHITECT -> architectAspect
        EnemyType.SWARMLING, EnemyType.PHANTOM, EnemyType.REFLECTOR -> null
        EnemyType.BOMBER -> StatusElement.THERMAL
        EnemyType.LANCER -> StatusElement.ION
        EnemyType.SNIPER -> StatusElement.CRYO
        EnemyType.AEGIS, EnemyType.BROODMOTHER -> StatusElement.GRAVITIC
        EnemyType.MINELAYER -> StatusElement.ENTROPY
        EnemyType.COMET -> StatusElement.CRYO
        EnemyType.PYLON -> StatusElement.ION
        EnemyType.SENTRY, EnemyType.IMP, EnemyType.PYRE_HERALD -> StatusElement.THERMAL
        EnemyType.CRAWLER, EnemyType.BROOD_QUEEN -> StatusElement.ENTROPY
        EnemyType.SERAPH, EnemyType.CIPHER, EnemyType.THRONE -> StatusElement.VOID
        EnemyType.CHOIR -> StatusElement.SONIC
        EnemyType.WARDEN_PRIME -> StatusElement.GRAVITIC
        EnemyType.HOUND, EnemyType.MIRROR, EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT -> null
    }
    val armored = type in setOf(
        EnemyType.CHARGER, EnemyType.WARDEN, EnemyType.SPLITTER, EnemyType.ELITE, EnemyType.ARCHITECT,
        EnemyType.LANCER, EnemyType.AEGIS, EnemyType.BROODMOTHER, EnemyType.HERALD,
        EnemyType.CHOIR, EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE, EnemyType.PYRE_HERALD,
        EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT,
    )
    val immune = type in setOf(
        EnemyType.WARDEN, EnemyType.ELITE, EnemyType.HERALD,
        EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE, EnemyType.PYRE_HERALD,
    )
    val fragile = type in setOf(
        EnemyType.INTERCEPTOR, EnemyType.COMET, EnemyType.PYLON, EnemyType.SENTRY, EnemyType.SERAPH, EnemyType.IMP,
    )
    return EnemyAffinity.encode(
        aspect = aspect?.ordinal ?: EnemyAffinity.NONE,
        immune = immune,
        weakness = aspect?.let(::counterOf)?.ordinal ?: EnemyAffinity.NONE,
        armored = armored,
        fragile = fragile,
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
