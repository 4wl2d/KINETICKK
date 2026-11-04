// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.math.max

// ---------------------------------------------------------------------------------------------
// Refit signatures. Each mechanic reads its one upgradable constant through an accessor that applies the
// selected character's Refit level with the content step ([kinetickk.ball.content.api.CharacterUpgradeSignature]);
// the render mapper reads the same accessors.

/** Orbit: the turn arc that charges the next impact (Refit shortens it by a share per level). */
internal fun MutableGameState.orbitArc(): Float = refitSignature(CoreShape.ORB, ORBIT_ARC, share = true)

/** Facets: seconds to regrow one facet (Refit shortens them by a share per level). */
internal fun MutableGameState.facetRegenSeconds(): Float =
    refitSignature(CoreShape.PRISM, FACET_REGEN_SECONDS, share = true)

/** Puncture: heat vented by a puncture. */
internal fun MutableGameState.punctureHeatRefund(): Float = refitSignature(CoreShape.SHARD, PUNCTURE_HEAT_REFUND)

/** Halo: the radius the ring gains at full speed over its minimum. */
internal fun MutableGameState.haloExtraRadius(): Float = refitSignature(CoreShape.RING, HALO_EXTRA_RADIUS)

/** Parry: seconds the parry window stays open. */
internal fun MutableGameState.parryWindowSeconds(): Float = refitSignature(CoreShape.DIAMOND, PARRY_WINDOW_SECONDS)

/** Lattice: seconds an open lattice lasts after its newest node. */
internal fun MutableGameState.latticeLifeSeconds(): Float = refitSignature(CoreShape.TESSERACT, LATTICE_LIFE_SECONDS)

/** Nova: the Supernova radius before Stellar Mass. */
internal fun MutableGameState.supernovaBaseRadius(): Float = refitSignature(CoreShape.STAR, SUPERNOVA_RADIUS)

/** Echo: seconds between two hostile shots the ghost swallows. */
internal fun MutableGameState.ghostAbsorbInterval(): Float = refitSignature(CoreShape.SPIRAL, GHOST_ABSORB_INTERVAL)

/** Polarity: the Attract field's radius. */
internal fun MutableGameState.attractRadius(): Float = refitSignature(CoreShape.DIPOLE, ATTRACT_RADIUS)

/** Polarity: the Repel field's radius (the signature's secondary step). */
internal fun MutableGameState.repelRadius(): Float =
    refitSignature(CoreShape.DIPOLE, REPEL_RADIUS, secondary = true)

/** Pulse: the on-beat burst's radius. */
internal fun MutableGameState.beatBurstRadius(): Float = refitSignature(CoreShape.PENTAGON, BEAT_BURST_RADIUS)

/** Eclipse: seconds the base rules take to recharge one Blink (Total Eclipse reads [blinkRechargeSeconds]). */
internal fun MutableGameState.baseBlinkRechargeSeconds(): Float =
    refitSignature(CoreShape.CRESCENT, BLINK_RECHARGE_SECONDS)

/** Hive: seconds a drone lives without a Data refresh. */
internal fun MutableGameState.droneLifeSeconds(): Float = refitSignature(CoreShape.HEXAGON, DRONE_LIFE_SECONDS)

/**
 * [base] moved by the Refit step of [shape] times the run's Refit level: added, or as a [share] of [base]. Another
 * character, or level 0, reads [base] itself. A step never takes a value below [REFIT_SIGNATURE_FLOOR_SHARE] of
 * [base] (the Blink recharge stays at or above 1.1 s, a facet regrows in at least 3 s).
 */
private fun MutableGameState.refitSignature(
    shape: CoreShape,
    base: Float,
    share: Boolean = false,
    secondary: Boolean = false,
): Float {
    val level = characterUpgradeLevel
    if (level == 0 || coreShape != shape) return base
    val signature = content.coreShape(shape).upgradeSignature
    val step = (if (secondary) signature.secondaryPerLevel else signature.perLevel) * level
    return max(base * REFIT_SIGNATURE_FLOOR_SHARE, if (share) base * (1f + step) else base + step)
}
