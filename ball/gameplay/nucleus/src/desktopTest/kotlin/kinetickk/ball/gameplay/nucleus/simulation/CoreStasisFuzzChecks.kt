// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreStasisNotices
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.flight.*

/**
 * Core Freeze ranges for [InvariantFuzzHarness] (STAT_RANGE): every field finite, the filtered speed non-negative, the
 * freeze within 0..1 (1 only once the run has ended), the hold within the longest window, and the shown freeze within
 * 0..1. Returns the first problem, or null.
 */
internal fun coreStasisRangeProblem(m: MutableGameState): String? {
    val stasis = m.coreStasis
    val tuning = m.content.coreStasis
    val fields = floatArrayOf(stasis.speedFilter, stasis.freeze, stasis.holdSeconds)
    val longestHold = maxOf(tuning.runStartHoldSeconds, tuning.choiceHoldSeconds, tuning.layerSwitchHoldSeconds)
    val level = m.coreStasisLevel()
    return when {
        fields.any { !it.isFinite() } -> "Core Freeze field not finite: ${fields.toList()}"
        stasis.speedFilter < 0f -> "Core Freeze filtered speed ${stasis.speedFilter}"
        stasis.freeze !in 0f..1f -> "Core Freeze freeze ${stasis.freeze}"
        stasis.freeze >= 1f && tuning.applyEffects && m.phase == GamePhase.RUNNING -> "a Core frozen solid still runs"
        stasis.holdSeconds !in 0f..longestHold -> "Core Freeze hold ${stasis.holdSeconds}"
        level !in 0f..1f -> "Core Freeze level $level"
        else -> null
    }
}

/** A Baby mode run that ended frozen (EASY_MODE): Easy mode shatters the ice instead. */
internal fun coreStasisEasyProblem(m: MutableGameState): String? =
    if (m.phase == GamePhase.GAME_OVER && m.message == CoreStasisNotices.FROZEN) "a Baby mode run froze to death" else null
