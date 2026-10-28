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
 *
 * [OBSTACLE] is an asteroid collision or blast: it scales with `1 + damagePerTier × tier` instead of the
 * Rebirth incoming-damage multiplier, ignores damage reduction and damage-taken modifiers, is absorbed
 * by shields, and neither uses nor grants the hit i-frames (collisions have their own cooldown).
 * [cancellableByFacets] is false for it: Prism facets cancel it only while Monolith runs (the Prism's Apex).
 */
internal enum class IncomingDamage(
    /** Prism facets may negate an incoming hit of this kind; obstacle collisions set false (Monolith aside). */
    val cancellableByFacets: Boolean = true,
) {
    HIT,
    CONTACT,
    PROJECTILE,
    SELF,
    OBSTACLE(cancellableByFacets = false),
}

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
 * Effects on the attacking enemy that scale its damage output: Hollow (Void) multiplies
 * `1 - 0.03 * hollowStacks` (scaled by Void potency, floored at [HOLLOW_OUTPUT_FLOOR]).
 */
internal fun MutableGameState.hostileOutputFactor(enemy: Enemy?): Float = hollowOutputFactor(enemy)

/** Hostile damage grows by this fraction of run progress; see [hostileDamage]. */
internal const val HOSTILE_RUN_GROWTH: Float = 0.5f

/** Damage-taken buffs and liabilities together never bring the multiplier on a hit below this. */
internal const val MIN_DAMAGE_TAKEN_MULTIPLIER: Float = 0.1f

internal fun MutableGameState.takeDamage(
    rawAmount: Float,
    ignoreReduction: Boolean = false,
    kind: IncomingDamage = IncomingDamage.HIT,
    obstacle: kinetickk.ball.content.api.ObstacleKind? = null,
    lethalReason: String = "CORE FRACTURED",
) {
    if (kind == IncomingDamage.OBSTACLE) {
        takeObstacleDamage(rawAmount, obstacle)
        return
    }
    // Void Step's phase: contacts and shots pass through the Core (the shots are still spent).
    if ((kind == IncomingDamage.CONTACT || kind == IncomingDamage.PROJECTILE) && phaseUntil > elapsed) return
    // Self-inflicted damage neither uses nor grants the hit i-frames, so real hits are never dropped behind it.
    val incoming = kind != IncomingDamage.SELF
    if ((incoming && hurtCooldown > 0f) || invulnerableUntil > elapsed) return
    var amount = rawAmount * rebirthProfile.incomingDamageMultiplier
    if (kind == IncomingDamage.CONTACT) amount *= matrix.contactDamageMultiplier
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
    amount *= taken.coerceAtLeast(MIN_DAMAGE_TAKEN_MULTIPLIER)
    if (build.liability(RelicId.GLASS_COMET)) {
        amount *= 2f
        glassCharge = 0f
    } else if (!ignoreReduction) {
        amount *= 1f - effectiveDamageReduction()
    }
    if (incoming) pauseFacetRegeneration()
    // A facet negates the whole instance; the hurt window it opens keeps it to one per window.
    if (incoming && kind.cancellableByFacets && spendFacet(amount)) return
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
    if (incoming) onCharacterDamaged()
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
    shieldRechargeDelay = (if (tagTier(BuildTag.BULWARK) >= 2) 2.2f else 4f) * matrix.shieldRegenDelayMultiplier
    if (incoming) hurtCooldown = 0.14f
    damageFlash = 1f
    shockwave(coreX, coreY, 0.3f, 90f, 4)
    burst(coreX, coreY, 8, 4)
    emitSound(GameplayAudioCue.HURT)
    if (shieldBefore > 0f && shield <= 0f) {
        if (incoming) onShieldBroken() else aegisAbsorbed = 0f
    }
    if (incoming) fireClauses(EffectTrigger.HURT, null, rawAmount)
    if (hp <= 0f) resolveLethal(lethalReason)
}

/** Run end message of a collision that leaves the Core at zero integrity. */
internal const val OBSTACLE_COLLISION_REASON: String = "OBSTACLE COLLISION"

/**
 * See [IncomingDamage.OBSTACLE]. Glass Comet's liability still holds: collisions deal double damage and
 * shields do not absorb them.
 */
private fun MutableGameState.takeObstacleDamage(rawAmount: Float, obstacle: kinetickk.ball.content.api.ObstacleKind? = null) {
    if (rawAmount <= 0f || invulnerableUntil > elapsed) return
    var amount = rawAmount * (1f + content.obstacles.damagePerTier * rebirthLevel)
    // Monolith: while it runs, a facet also takes an obstacle collision (and fires its refraction).
    if (monolithTakesCollisions() && spendFacet(amount)) return
    val glass = build.liability(RelicId.GLASS_COMET)
    if (glass) {
        amount *= 2f
        glassCharge = 0f
    }
    val shieldBefore = shield
    if (shield > 0f && !glass) {
        val absorbed = min(shield, amount)
        absorbShield(absorbed, incoming = true)
        amount -= absorbed
    }
    if (amount > 0f) {
        damageTaken += min(amount, hp.coerceAtLeast(0f)).toDouble()
        hp -= amount
    }
    noteObstacleHurt()
    if (shieldBefore > 0f && shield <= 0f) onShieldBroken()
    fireClauses(EffectTrigger.HURT, null, rawAmount, obstacle = obstacle)
    if (hp <= 0f) resolveLethal(OBSTACLE_COLLISION_REASON)
}

/**
 * A collision at or above the lethal speed: integrity drops to zero past shields, then only Second Wind
 * or the Ouroboros Seal may answer it. Only the invulnerability a revive granted protects (the Core may
 * still be flying into the same rock); Glass Comet's and Bulwark's windows do not.
 */
internal fun MutableGameState.takeLethalObstacleHit() {
    if (reviveInvulnerableUntil > elapsed) return
    damageTaken += hp.coerceAtLeast(0f).toDouble()
    hp = 0f
    noteObstacleHurt()
    resolveLethal(OBSTACLE_COLLISION_REASON)
}

private fun MutableGameState.noteObstacleHurt() {
    // A collision is a hit to the character too: it pauses facet regeneration and costs Stellar Mass,
    // but no facet ever cancels it.
    pauseFacetRegeneration()
    onCharacterDamaged()
    timeSinceDamage = 0f
    shieldRechargeDelay = (if (tagTier(BuildTag.BULWARK) >= 2) 2.2f else 4f) * matrix.shieldRegenDelayMultiplier
    damageFlash = 1f
    shockwave(coreX, coreY, 0.3f, 90f, 4)
    burst(coreX, coreY, 8, 4)
    emitSound(GameplayAudioCue.HURT)
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
