// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState

/** Every retained Core Stasis field by its bits, for [canonicalStateFingerprint]. */
internal fun coreStasisStateFingerprint(state: MutableGameState): Long {
    val stasis = state.coreStasis
    var signature = CORE_STASIS_SEED
    signature = stasisMix(signature, stasis.anchorX.toRawBits())
    signature = stasisMix(signature, stasis.anchorY.toRawBits())
    signature = stasisMix(signature, stasis.speedEma.toRawBits())
    signature = stasisMix(signature, stasis.stillSeconds.toRawBits())
    signature = stasisMix(signature, stasis.frozenSeconds.toRawBits())
    return stasisMix(signature, stasis.fractureClock.toRawBits())
}

/** The projected stasis level and fracture flag. */
internal fun coreStasisRenderFingerprint(render: GameplayRenderModel): Long =
    stasisMix(stasisMix(CORE_STASIS_SEED, render.coreStasis.toRawBits()), if (render.coreStasisFracturing) 1 else 0)

private fun stasisMix(current: Long, value: Int): Long = (current xor value.toLong()) * -7046029254386353131L

private const val CORE_STASIS_SEED = 0x5747_A515L
