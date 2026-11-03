// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreStasisNotices
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.flight.*

/**
 * Core Stasis ranges for [InvariantFuzzHarness] (STAT_RANGE): every field finite, the still clock within 0..full
 * stasis, the frozen and fracture clocks within their periods, the level within 0..1, and no fracture in the Nadir.
 * Returns the first problem, or null.
 */
internal fun coreStasisRangeProblem(m: MutableGameState): String? {
    val stasis = m.coreStasis
    val tuning = m.content.coreStasis
    val fields = floatArrayOf(stasis.anchorX, stasis.anchorY, stasis.speedEma, stasis.stillSeconds, stasis.frozenSeconds, stasis.fractureClock)
    val level = m.coreStasisLevel()
    return when {
        fields.any { !it.isFinite() } -> "Core Stasis field not finite: ${fields.toList()}"
        stasis.stillSeconds !in 0f..tuning.fullStasisSeconds -> "Core Stasis still clock ${stasis.stillSeconds}"
        stasis.frozenSeconds !in 0f..tuning.fractureDelaySeconds -> "Core Stasis frozen clock ${stasis.frozenSeconds}"
        stasis.fractureClock < 0f || stasis.fractureClock >= tuning.fractureTickSeconds -> "Core Stasis fracture clock ${stasis.fractureClock}"
        level !in 0f..1f -> "Core Stasis level $level"
        m.worldLayer == WorldLayer.NADIR && m.coreStasisFracturing() -> "the Core fractures in the Nadir"
        else -> null
    }
}

/** A Baby mode run that ended frozen (EASY_MODE): Easy mode never fractures. */
internal fun coreStasisEasyProblem(m: MutableGameState): String? =
    if (m.phase == GamePhase.GAME_OVER && m.message == CoreStasisNotices.FROZEN) "a Baby mode run froze to death" else null
