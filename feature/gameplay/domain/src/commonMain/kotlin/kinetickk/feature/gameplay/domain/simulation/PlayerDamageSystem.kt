// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.simulation

import kinetickk.core.content.*

import kinetickk.feature.gameplay.domain.model.*
import kinetickk.core.audio.api.AudioCue
import kotlin.math.min


internal fun MutableGameState.takeDamage(rawAmount: Float) {
    if (hurtCooldown > 0f) return
    var amount = rawAmount * rebirthProfile.incomingDamageMultiplier *
        (1f - damageReduction.coerceIn(0f, 0.65f))
    if (shield > 0f) {
        val absorbed = min(shield, amount)
        shield -= absorbed
        amount -= absorbed
    }
    if (amount > 0f) hp -= amount
    if (amount > 0f && relicRank(RelicId.BORROWED_MOMENT) > 0) {
        borrowedMomentTime = 2.5f
        relicProcCounts[RelicId.BORROWED_MOMENT.ordinal]++
    }
    timeSinceDamage = 0f
    shieldRechargeDelay = 4f
    hurtCooldown = 0.14f
    damageFlash = 1f
    shockwave(coreX, coreY, 0.3f, 90f, 4)
    burst(coreX, coreY, 8, 4)
    emitSound(AudioCue.HURT)
    if (hp <= 0f) endRun("CORE FRACTURED")
}
