// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

/**
 * Run statistics for the report: kills, damage dealt, taken and absorbed, collections, Elite kills and the best combo.
 */
internal class RunStatsState(source: RunStatsState?) {
    internal var kills: Int = source?.kills ?: 0
    internal var damageDealt: Double = source?.damageDealt ?: 0.0
    internal var damageTaken: Double = source?.damageTaken ?: 0.0
    internal var damageAbsorbed: Double = source?.damageAbsorbed ?: 0.0
    internal var dataCollected: Long = source?.dataCollected ?: 0L
    internal var pickupsCollected: Long = source?.pickupsCollected ?: 0L
    internal var keysCollected: Long = source?.keysCollected ?: 0L
    internal var eliteKills: Int = source?.eliteKills ?: 0
    internal var bestCombo: Int = source?.bestCombo ?: 0

    /** A new run has counted nothing. */
    fun reset() {
        kills = 0
        damageDealt = 0.0
        damageTaken = 0.0
        damageAbsorbed = 0.0
        dataCollected = 0L
        pickupsCollected = 0L
        keysCollected = 0L
        eliteKills = 0
        bestCombo = 0
    }
}
