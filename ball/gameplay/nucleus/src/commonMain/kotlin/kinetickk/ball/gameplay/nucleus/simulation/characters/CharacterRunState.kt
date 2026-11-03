// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
import kinetickk.ball.profile.api.GameplayProfileSnapshot

/**
 * The selected character: its shape, its runtime and its Refit level and power, fixed when a run starts. Written by
 * the characters domain and the run start.
 */
internal class CharacterRunState(source: CharacterRunState?, bootstrapProgress: GameplayProfileSnapshot?) {
    /** Mutable character state; a deep copy forks it (scalars and buffers copied). */
    internal var characterRuntime: CharacterRuntime = source?.characterRuntime?.fork() ?: CharacterRuntime()
    /** The selected character's Refit level, fixed when a run starts. */
    internal var characterUpgradeLevel: Int = source?.characterUpgradeLevel ?: 0
    /** Refit's character-mechanic damage multiplier, fixed when a run starts. */
    internal var characterUpgradePower: Float = source?.characterUpgradePower ?: 1f
    internal var coreShape: CoreShape = source?.coreShape
        ?: bootstrapProgress?.loadout?.coreShape ?: CoreShape.ORB
}
