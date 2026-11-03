// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/** Non-default Core Stasis facts for the retained-state witnesses ([populateAdditionalRetainedState]). */
internal fun MutableGameState.populateCoreStasisRetainedState() {
    coreStasis.anchorX = 12.5f
    coreStasis.anchorY = -7.25f
    coreStasis.speedEma = 133f
    coreStasis.stillSeconds = 11.5f
    coreStasis.frozenSeconds = 2.25f
    coreStasis.fractureClock = 0.75f
}

/** Every Core Stasis field by its bits, for [exactSimulationFingerprint]. */
internal fun ExactFingerprint.coreStasisFingerprint(state: MutableGameState) {
    val stasis = state.coreStasis
    float("coreStasis.anchorX", stasis.anchorX)
    float("coreStasis.anchorY", stasis.anchorY)
    float("coreStasis.speedEma", stasis.speedEma)
    float("coreStasis.stillSeconds", stasis.stillSeconds)
    float("coreStasis.frozenSeconds", stasis.frozenSeconds)
    float("coreStasis.fractureClock", stasis.fractureClock)
}
