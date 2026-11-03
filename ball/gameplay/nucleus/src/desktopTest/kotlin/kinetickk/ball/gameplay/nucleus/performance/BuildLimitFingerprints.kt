// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState

/** The run's build limits (Relic claims, Melds and their damage multiplier, weapon changes), for [canonicalStateFingerprint]. */
internal fun buildLimitsStateFingerprint(state: MutableGameState): Long {
    var signature = BUILD_LIMITS_SEED
    signature = limitsMix(signature, state.relicRun.relicClaims)
    signature = limitsMix(signature, state.relicRun.meldedSlots)
    signature = limitsMix(signature, state.relicRun.meldDamageMultiplier.toRawBits())
    return limitsMix(signature, state.offerRun.weaponChanges)
}

private fun limitsMix(current: Long, value: Int): Long = (current xor value.toLong()) * -7046029254386353131L

private const val BUILD_LIMITS_SEED = 0x0B1D_4A11L
