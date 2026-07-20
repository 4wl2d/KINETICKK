// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.port.audio

import kinetickk.feature.game.domain.model.GameSettings
import kinetickk.feature.game.domain.protocol.SoundCue

/** Output port used by the game engine to advance audio without knowing a platform implementation. */
interface AudioResource {
    fun advance(settings: GameSettings, realDelta: Float, cues: List<SoundCue>)
    fun ensureUnlocked()
    fun close()
}
