// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState

/** Every retained Core Freeze field by its bits, for [canonicalStateFingerprint]. */
internal fun coreStasisStateFingerprint(state: MutableGameState): Long {
    val stasis = state.coreStasis
    var signature = CORE_STASIS_SEED
    signature = stasisMix(signature, stasis.speedFilter.toRawBits())
    signature = stasisMix(signature, stasis.freeze.toRawBits())
    signature = stasisMix(signature, stasis.holdSeconds.toRawBits())
    return stasisMix(signature, if (stasis.inputHeld) 1 else 0)
}

/** The projected freeze and where it heads. */
internal fun coreStasisRenderFingerprint(render: GameplayRenderModel): Long =
    stasisMix(stasisMix(CORE_STASIS_SEED, render.coreStasis.toRawBits()), render.coreStasisMotion.ordinal)

private fun stasisMix(current: Long, value: Int): Long = (current xor value.toLong()) * -7046029254386353131L

private const val CORE_STASIS_SEED = 0x5747_A515L
