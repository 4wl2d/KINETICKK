// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.simulation.BASE_DRAG_COEFFICIENT
import kinetickk.ball.gameplay.nucleus.simulation.BASE_MAGNET_STRENGTH

/**
 * The Core's build stats: mass, damage and weapon power, cooling, magnet, Dash, regeneration, crits, pickup radius,
 * luck, gains, attack speed, damage reduction, combo window, Overdrive gain and the flight law's drag, grip and sling.
 * Set by the run's stat reset, then changed by stat deltas and character traits.
 */
internal class CoreStatsState(content: GameplayContentSnapshot, source: CoreStatsState?) {
    internal var mass: Float = source?.mass ?: 1f
    internal var damageMultiplier: Float = source?.damageMultiplier ?: 1f
    internal var weaponPower: Float = source?.weaponPower ?: 1f
    internal var coolingRate: Float = source?.coolingRate ?: 19f
    internal var magnetStrength: Float = source?.magnetStrength ?: 4.65f
    internal var dashImpulse: Float = source?.dashImpulse ?: 590f
    internal var dashHeatCost: Float = source?.dashHeatCost ?: 36f
    internal var regenPerSecond: Float = source?.regenPerSecond ?: 0f
    internal var critChance: Float = source?.critChance ?: 0.05f
    internal var critMultiplier: Float = source?.critMultiplier ?: 1.5f
    internal var pickupRadius: Float = source?.pickupRadius ?: content.pickups.magnetRadius
    internal var luck: Float = source?.luck ?: 0f
    internal var dataGain: Float = source?.dataGain ?: 1f
    internal var matterGain: Float = source?.matterGain ?: 1f
    internal var attackSpeed: Float = source?.attackSpeed ?: 1f
    internal var damageReduction: Float = source?.damageReduction ?: 0f
    internal var comboWindow: Float = source?.comboWindow ?: 2.8f
    internal var overdriveGain: Float = source?.overdriveGain ?: 1f
    internal var dragCoefficient: Float = source?.dragCoefficient ?: 0.29f
    /** The flight law's grip and sling build stats (raw; clamped where the law reads them). */
    internal var gripStrength: Float = source?.gripStrength ?: 1f
    internal var slingStrength: Float = source?.slingStrength ?: 1f

    /** The character base stats before traits, the Matrix and Rebirth; [pickupRadius] is the content's magnet radius. */
    fun reset(pickupRadius: Float) {
        mass = 1f
        damageMultiplier = 1f
        weaponPower = 1f
        coolingRate = 19f
        magnetStrength = BASE_MAGNET_STRENGTH
        dashImpulse = 590f
        dashHeatCost = 36f
        regenPerSecond = 0f
        critChance = 0.05f
        critMultiplier = 1.5f
        this.pickupRadius = pickupRadius
        luck = 0f
        dataGain = 1f
        matterGain = 1f
        attackSpeed = 1f
        damageReduction = 0f
        comboWindow = 2.8f
        overdriveGain = 1f
        dragCoefficient = BASE_DRAG_COEFFICIENT
        gripStrength = 1f
        slingStrength = 1f
    }
}
