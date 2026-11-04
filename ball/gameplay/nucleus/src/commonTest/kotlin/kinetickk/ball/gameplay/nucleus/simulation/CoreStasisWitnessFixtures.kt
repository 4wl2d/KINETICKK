// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/** Non-default Core Freeze facts for the retained-state witnesses ([populateAdditionalRetainedState]). */
internal fun MutableGameState.populateCoreStasisRetainedState() {
    coreStasis.speedFilter = 133f
    coreStasis.freeze = 0.45f
    coreStasis.holdSeconds = 0.25f
    coreStasis.inputHeld = true
}

/** Every Core Freeze field by its bits, for [exactSimulationFingerprint]. */
internal fun ExactFingerprint.coreStasisFingerprint(state: MutableGameState) {
    val stasis = state.coreStasis
    float("coreStasis.speedFilter", stasis.speedFilter)
    float("coreStasis.freeze", stasis.freeze)
    float("coreStasis.holdSeconds", stasis.holdSeconds)
    value("coreStasis.inputHeld", stasis.inputHeld)
}
