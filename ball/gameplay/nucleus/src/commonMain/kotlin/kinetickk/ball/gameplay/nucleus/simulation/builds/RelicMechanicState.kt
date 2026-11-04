// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState.Companion.STASIS_SECONDS

/**
 * Retained timers, charges and counters of relic mechanics (and the Matrix's free Dash). Written by the build
 * domain's relic rules.
 */
internal class RelicMechanicState(source: RelicMechanicState?) {
    internal var glassInvulnerabilityReadyAt: Float = source?.glassInvulnerabilityReadyAt ?: 0f
    internal var bulwarkInvulnerabilityReadyAt: Float = source?.bulwarkInvulnerabilityReadyAt ?: 0f
    internal var stormCharge: Float = source?.stormCharge ?: 0f
    internal var stasisMeter: Float = source?.stasisMeter ?: STASIS_SECONDS
    internal var stasisBank: Int = source?.stasisBank ?: 0
    internal var stasisLockedUntil: Float = source?.stasisLockedUntil ?: 0f
    internal var sinceTurn: Float = source?.sinceTurn ?: 0f
    internal var gyreReadyAt: Float = source?.gyreReadyAt ?: 0f
    /** Endless Gyre's delayed second ring: when it fires (negative when none is pending) and its damage. */
    internal var gyreEchoAt: Float = source?.gyreEchoAt ?: -1f
    internal var gyreEchoDamage: Float = source?.gyreEchoDamage ?: 0f
    internal var overdriveExtension: Float = source?.overdriveExtension ?: 0f
    internal var overdriveKills: Int = source?.overdriveKills ?: 0
    internal var aegisAbsorbed: Float = source?.aegisAbsorbed ?: 0f
    internal var aegisNovaReadyAt: Float = source?.aegisNovaReadyAt ?: 0f
    internal var shieldBreakArmed: Boolean = source?.shieldBreakArmed ?: false
    internal var shieldBreakReadyAt: Float = source?.shieldBreakReadyAt ?: 0f
    internal var glassCharge: Float = source?.glassCharge ?: 0f
    /** Maximum integrity Null Covenant's liability currently withholds (0 when none). */
    internal var integrityLiabilityCut: Float = source?.integrityLiabilityCut ?: 0f
    internal var cinderRecoilReadyAt: Float = source?.cinderRecoilReadyAt ?: 0f
    internal var secondWindSpent: Boolean = source?.secondWindSpent ?: false
    /** When the next heat-free Matrix Dash is ready. */
    internal var matrixFreeDashReadyAt: Float = source?.matrixFreeDashReadyAt ?: 0f
    internal var necroticClock: Float = source?.necroticClock ?: 0f

    /** A new run: every timer, charge and counter at its initial value. */
    fun reset() {
        glassInvulnerabilityReadyAt = 0f
        bulwarkInvulnerabilityReadyAt = 0f
        stormCharge = 0f
        stasisMeter = STASIS_SECONDS
        stasisBank = 0
        stasisLockedUntil = 0f
        sinceTurn = 0f
        gyreReadyAt = 0f
        gyreEchoAt = -1f
        gyreEchoDamage = 0f
        overdriveExtension = 0f
        overdriveKills = 0
        aegisAbsorbed = 0f
        aegisNovaReadyAt = 0f
        shieldBreakArmed = false
        shieldBreakReadyAt = 0f
        glassCharge = 0f
        integrityLiabilityCut = 0f
        cinderRecoilReadyAt = 0f
        secondWindSpent = false
        matrixFreeDashReadyAt = 0f
        necroticClock = 0f
    }
}
