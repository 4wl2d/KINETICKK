// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.simulation.progression.*

internal fun MutableGameState.endRun(reason: String) {
    if (phase != GamePhase.RUNNING) return
    phase = GamePhase.GAME_OVER
    message = reason
    messageTime = 10f
    keyboardBrakeActive = false
    secondaryBrakeActive = false
    touchBrakeActive = false
    updateBraking()
    dashBufferTime = 0f
    burst(coreX, coreY, 45, 4)
    bankRunMatter()
    emitSound(GameplayAudioCue.GAME_OVER)
}
