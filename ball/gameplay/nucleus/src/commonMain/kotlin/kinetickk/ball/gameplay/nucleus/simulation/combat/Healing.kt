// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Healing and shields: one path, so conversions and blocks apply everywhere.

internal enum class HealSource { REGENERATION, REPAIR, ITEM, LEVEL, VERDANT, RELIC, SKILL }

internal fun MutableGameState.heal(requested: Float, source: HealSource) {
    if (requested <= 0f) return
    val amount = requested * entropyTideHealing()
    if (build.liability(RelicId.VERDANT_ROT) && source != HealSource.VERDANT) return
    if (build.upside(RelicId.AEGIS_INVERSION) || build.liability(RelicId.AEGIS_INVERSION)) {
        if (build.liability(RelicId.GLASS_COMET)) {
            if (build.pairing(RelicPairingId.SHATTERED_AEGIS)) glassCharge = min(90f, glassCharge + amount)
            return
        }
        addShield(amount)
        return
    }
    val missing = maxHp - hp
    if (amount <= missing) {
        hp += amount
        return
    }
    hp = maxHp
    if (build.overhealOverfill > 0f) addShield(amount - missing, overfill = true)
}

internal fun MutableGameState.addShield(amount: Float, overfill: Boolean = false) {
    if (amount <= 0f || build.liability(RelicId.GLASS_COMET)) return
    val capacity = shieldCapacity()
    val ceiling = if (overfill || build.overhealOverfill > 0f) capacity * max(1f, build.overhealOverfill) else capacity
    shield = min(ceiling, shield + amount)
    if (shield >= capacity * 0.5f) shieldBreakArmed = true
}
