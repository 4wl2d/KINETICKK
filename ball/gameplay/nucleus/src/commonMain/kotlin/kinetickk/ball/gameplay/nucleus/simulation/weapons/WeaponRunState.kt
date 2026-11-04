// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.model.RunIntArray
import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime

/**
 * The equipped weapon's level, runtime, clocks and Agony mutations, the Flux trail cursor, the Morningstar and the
 * beam. Written by the weapons domain (equipping by progression); positions follow a rebase.
 */
internal class WeaponRunState(content: GameplayContentSnapshot, source: WeaponRunState?) {
    internal var weaponLevel: Int = source?.weaponLevel ?: 1
    /** Owned state of the equipped weapon (entities, proc gates, element cadences, scaling inputs); a deep copy forks it. */
    internal var weaponRuntime: WeaponRuntime = source?.weaponRuntime?.fork() ?: WeaponRuntime()
    internal var weaponClock: Float = source?.weaponClock ?: 0f
    internal var weaponSecondaryClock: Float = source?.weaponSecondaryClock ?: 0f
    internal val agonyMutationCounts: RunIntArray = source?.agonyMutationCounts?.copy()
        ?: RunIntArray(IntArray(content.weapons.size))
    internal var trailLastX: Float = source?.trailLastX ?: 0f
    internal var trailLastY: Float = source?.trailLastY ?: 0f
    internal var trailDistanceCarry: Float = source?.trailDistanceCarry ?: 0f
    internal var morningstarAngle: Float = source?.morningstarAngle ?: 0f
    internal var morningstarX: Float = source?.morningstarX ?: 0f
    internal var morningstarY: Float = source?.morningstarY ?: 0f
    /** Morningstar position at the start of the last fixed step (render interpolation only). */
    internal var previousMorningstarX: Float = source?.previousMorningstarX ?: 0f
    internal var previousMorningstarY: Float = source?.previousMorningstarY ?: 0f
    internal var weaponBeamTime: Float = source?.weaponBeamTime ?: 0f
    internal var weaponBeamStartX: Float = source?.weaponBeamStartX ?: 0f
    internal var weaponBeamStartY: Float = source?.weaponBeamStartY ?: 0f
    internal var weaponBeamEndX: Float = source?.weaponBeamEndX ?: 0f
    internal var weaponBeamEndY: Float = source?.weaponBeamEndY ?: 0f
}
