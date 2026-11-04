// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.simulation.progression.*

/** Ends a running run as [reason]: the Core bursts in [burstColor] (an FxColorCode index; threat by default). */
internal fun MutableGameState.endRun(reason: String, burstColor: Int = 4) {
    if (phase != GamePhase.RUNNING) return
    phase = GamePhase.GAME_OVER
    message = reason
    messageTime = 10f
    keyboardBrakeActive = false
    secondaryBrakeActive = false
    touchBrakeActive = false
    updateBraking()
    dashBufferTime = 0f
    burst(coreX, coreY, 45, burstColor)
    bankRunMatter()
    emitSound(GameplayAudioCue.GAME_OVER)
}
