// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.protocol.BoundedVisualFxCueAccumulator
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue

/**
 * Sound and visual cues emitted since the owner last drained them, materialised lazily (null while empty) so a deep
 * copy only copies retained output. Written through [emitSound] and [emitVisualFx]; drained by the tick.
 */
internal class CueOutbox(source: CueOutbox?) {
    internal var soundCueStorage: MutableList<GameplayAudioCue>? =
        source?.soundCueStorage?.let { cues -> ArrayList(cues) }
    internal var visualFxCueStorage: BoundedVisualFxCueAccumulator? =
        source?.visualFxCueStorage?.copy()
}
