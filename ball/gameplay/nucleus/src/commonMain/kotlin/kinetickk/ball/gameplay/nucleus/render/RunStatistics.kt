// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

/** Run-owned totals, independent of spent resources and drained profile progress outputs. */
data class RunStatistics(
    val damageDealt: Double = 0.0,
    val damageTaken: Double = 0.0,
    val damageAbsorbed: Double = 0.0,
    val dataCollected: Long = 0L,
    val pickupsCollected: Long = 0L,
    val keysCollected: Long = 0L,
    val eliteKills: Int = 0,
    val bestCombo: Int = 0,
)
