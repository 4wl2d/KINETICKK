// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.max
import kotlin.math.min

/**
 * Where damage came from: relics treat contact and bullets differently, and [SELF] (recoil, liabilities)
 * neither uses nor grants hit i-frames, HURT clauses or shield-break rewards.
 */
internal enum class IncomingDamage { HIT, CONTACT, PROJECTILE, SELF }

/**
 * Damage of a hostile attack from [enemy] (null for sourceless hazards), evaluated when the attack is
 * created: a projectile is fired or a contact resolves. It applies the run growth `1 + 0.5p` and the
 * per-enemy [hostileOutputFactor]. The Rebirth incoming-damage multiplier is not part of it, because
 * [takeDamage] applies that multiplier to every amount it receives; applying it here as well would
 * count it twice.
 */
internal fun MutableGameState.hostileDamage(enemy: Enemy?, base: Float): Float =
    base * (1f + HOSTILE_RUN_GROWTH * runProgress) * hostileOutputFactor(enemy)

/**
 * Hook for effects on the attacking enemy that scale its damage output. It is 1 until the Hollow
 * status lands, which multiplies `1 - 0.03 * hollowStacks` in here.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.hostileOutputFactor(enemy: Enemy?): Float = 1f

/** Hostile damage grows by this fraction of run progress; see [hostileDamage]. */
internal const val HOSTILE_RUN_GROWTH: Float = 0.5f

internal fun MutableGameState.takeDamage(
    rawAmount: Float,
    ignoreReduction: Boolean = false,
    kind: IncomingDamage = IncomingDamage.HIT,
) {
    // Self-inflicted damage neither uses nor grants the hit i-frames, so real hits are never dropped behind it.
    val incoming = kind != IncomingDamage.SELF
    if ((incoming && hurtCooldown > 0f) || invulnerableUntil > elapsed) return
    var amount = rawAmount * rebirthProfile.incomingDamageMultiplier
    var taken = 1f + buffAmount(BuffStat.DAMAGE_TAKEN) + whileAmount(BuffStat.DAMAGE_TAKEN, null)
    if (build.liability(RelicId.HOLLOW_CROWN)) taken += 0.1f * emptyRelicSlots()
    if (build.liability(RelicId.VERDANT_ROT)) {
        var close = 0
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.alive && distanceSquared(enemy.x, enemy.y, coreX, coreY) <= square(160f)) close += stacks(enemy, StatusElement.ENTROPY)
        }
        taken += min(0.3f, close * 0.015f)
    }
    if (kind == IncomingDamage.CONTACT && build.liability(RelicId.EVENT_HORIZON)) taken += 0.5f
    if (kind == IncomingDamage.PROJECTILE && build.pairing(RelicPairingId.BLACK_STAR)) taken += 0.5f
    amount *= taken.coerceAtLeast(0.1f)
    if (build.liability(RelicId.GLASS_COMET)) {
        amount *= 2f
        glassCharge = 0f
    } else if (!ignoreReduction) {
        amount *= 1f - effectiveDamageReduction()
    }
    if (!build.liability(RelicId.GLASS_COMET) && characterRuntime.barrier > 0f) {
        val absorbed = min(characterRuntime.barrier, amount)
        characterRuntime = characterRuntime.copy(barrier = characterRuntime.barrier - absorbed)
        damageAbsorbed += absorbed.toDouble()
        amount -= absorbed
    }
    val shieldBefore = shield
    if (shield > 0f && !build.liability(RelicId.GLASS_COMET)) {
        val absorbed = min(shield, amount)
        absorbShield(absorbed, incoming)
        amount -= absorbed
    }
    if (amount > 0f) {
        damageTaken += min(amount, hp.coerceAtLeast(0f)).toDouble()
        hp -= amount
    }
    if (incoming && build.liability(RelicId.STORMHEART_CAPACITOR) && stormCharge >= 0.5f) {
        stormCharge = 0f
        var discharge = maxHp * 0.1f
        if (build.pairing(RelicPairingId.FARADAY_CAGE) && shield > 0f) {
            val absorbed = min(shield, discharge)
            absorbShield(absorbed, incoming = true)
            discharge -= absorbed
        }
        if (discharge > 0f) {
            damageTaken += min(discharge, hp.coerceAtLeast(0f)).toDouble()
            hp -= discharge
        }
    }
    timeSinceDamage = 0f
    shieldRechargeDelay = if (tagTier(BuildTag.BULWARK) >= 2) 2.2f else 4f
    if (incoming) hurtCooldown = 0.14f
    damageFlash = 1f
    shockwave(coreX, coreY, 0.3f, 90f, 4)
    burst(coreX, coreY, 8, 4)
    emitSound(GameplayAudioCue.HURT)
    if (shieldBefore > 0f && shield <= 0f) {
        if (incoming) onShieldBroken() else aegisAbsorbed = 0f
    }
    if (incoming) fireClauses(EffectTrigger.HURT, null, rawAmount)
    if (hp <= 0f) resolveLethal()
}

/** Takes [absorbed] from the shield; only incoming hits under Aegis Inversion feed its nova. */
private fun MutableGameState.absorbShield(absorbed: Float, incoming: Boolean) {
    shield -= absorbed
    damageAbsorbed += absorbed.toDouble()
    if (incoming && build.upside(RelicId.AEGIS_INVERSION)) aegisAbsorbed += absorbed
}

/** A shield break counts only if the shield had reached half capacity since the last one. */
private fun MutableGameState.onShieldBroken() {
    // Every emptying ends that shield's absorbed total, whether or not the break qualifies.
    val absorbed = aegisAbsorbed
    aegisAbsorbed = 0f
    if (!shieldBreakArmed || shieldBreakReadyAt > elapsed) return
    shieldBreakArmed = false
    shieldBreakReadyAt = elapsed + 3f
    fireClauses(EffectTrigger.SHIELD_BREAK, null, absorbed)
    relicOnShieldBreak(absorbed)
    if (tagTier(BuildTag.BULWARK) >= 3 && bulwarkInvulnerabilityReadyAt <= elapsed) {
        bulwarkInvulnerabilityReadyAt = elapsed + 20f
        invulnerableUntil = max(invulnerableUntil, elapsed + 0.8f)
    }
}
