// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*

// ---------------------------------------------------------------------------------------------
// Hooks called by the simulation. Each derives its edge inside the fixed step.

/** After a direct hit landed: clauses, statuses from resonance, Ion arcs and relic rules. */
internal fun MutableGameState.onDirectHit(enemy: Enemy, result: DamageResult, source: HitSource, sourceWeapon: WeaponId?) {
    enemy.status[StatusCell.LAST_SOURCE] = (source.ordinal + 1).toFloat()
    // Derived damage measures the hit before the receiver's own multipliers, so armour never leaks to others,
    // and before the run damage multiplier (Matrix and Melds), which every proc, status and field applies once itself.
    val basis = result.unmitigated / runDamageMultiplier()
    when (source) {
        HitSource.IMPACT -> fireClauses(EffectTrigger.IMPACT, enemy, basis, source)
        HitSource.WEAPON -> {
            fireClauses(EffectTrigger.WEAPON_HIT, enemy, basis, source)
            onSkillWeaponHit(enemy)
            if (tagTier(BuildTag.ARSENAL) >= 3) {
                // A deterministic counter, not a roll: no draw from the crit and proc stream.
                arsenalCounter = (arsenalCounter + 1) % ARSENAL_INFUSION_EVERY
                if (arsenalCounter == 0) arsenalInfusion()?.let { infusion -> applyStatus(enemy, infusion, 1, 0) }
            }
            if (build.upside(kinetickk.ball.content.api.RelicId.ECHO_CHAMBER) && echoHits.size < MutableGameState.MAX_ECHO_HITS) {
                val share = content.relic(kinetickk.ball.content.api.RelicId.ECHO_CHAMBER)
                    .value(0, relicRank(kinetickk.ball.content.api.RelicId.ECHO_CHAMBER)) / 100f *
                    build.relicUpside(kinetickk.ball.content.api.RelicId.ECHO_CHAMBER)
                val forbidden = build.pairing(kinetickk.ball.content.api.RelicPairingId.FORBIDDEN_ECHO)
                echoHits += EchoHit(enemy.id, 0.35f, result.amount * share * if (forbidden) 1.2f else 1f, forbidden)
            }
        }
        HitSource.ECHO -> fireClauses(EffectTrigger.WEAPON_HIT, enemy, basis, source, statusOnly = true)
        HitSource.DASH, HitSource.PROC, HitSource.STATUS, HitSource.SKILL -> Unit
    }
    if (result.critical) {
        fireClauses(EffectTrigger.CRIT, enemy, basis, source)
        if (tagTier(BuildTag.PRECISION) >= 2) {
            for (element in StatusElement.entries) if (stacks(enemy, element) > 0 && enemy.alive) applyStatus(enemy, element, 1, 0)
        }
    }
    if (source == HitSource.IMPACT && tagTier(BuildTag.RAM) >= 2 && speed >= 900f) {
        areaDamage(enemy.x, enemy.y, 80f, basis * 0.35f, excludedId = enemy.id, rescale = false)
    }
    if (enemy.alive) arcCharge(enemy)
    if (source == HitSource.WEAPON && enemy.alive) onCharacterPrimaryHit(enemy)
    relicOnDirectHit(enemy, result, source)
}

/** ARSENAL III adds one stack on every this-many weapon hit (one in four). */
internal const val ARSENAL_INFUSION_EVERY: Int = 4

/** ARSENAL III: every Break deals this share more damage. */
internal const val ARSENAL_BREAK_BONUS: Float = 0.25f

/** The element ARSENAL III adds: the weapon's own element when it has one, else the dominant element. */
internal fun MutableGameState.arsenalInfusion(): StatusElement? = currentWeaponDefinition.element ?: build.dominant

/** Damage scale of every Break: ARSENAL III raises it, nothing else does. */
internal fun MutableGameState.breakDamageScale(): Float =
    if (tagTier(BuildTag.ARSENAL) >= 3) 1f + ARSENAL_BREAK_BONUS else 1f

internal fun MutableGameState.onBuildKill(enemy: Enemy) {
    onStatusCarrierDied(enemy)
    if (!enemy.killClausesEligible) return
    val source = when (enemy.status[StatusCell.LAST_SOURCE].toInt() - 1) {
        HitSource.IMPACT.ordinal -> HitSource.IMPACT
        HitSource.DASH.ordinal -> HitSource.DASH
        HitSource.WEAPON.ordinal -> HitSource.WEAPON
        else -> null
    }
    fireClauses(EffectTrigger.KILL, enemy, 0f, source)
    if (tagTier(BuildTag.CHAIN) >= 2) {
        comboKillCounter++
        if (comboKillCounter >= 12) {
            comboKillCounter = 0
            areaDamage(enemy.x, enemy.y, 140f, enemy.maxHp * victimFactor(enemy) * 0.6f, excludedId = enemy.id, rescale = false,
                shareCapped = true)
        }
    }
    relicOnKill(enemy, source)
}

internal fun MutableGameState.onBreak(enemy: Enemy, element: StatusElement) {
    fireClauses(EffectTrigger.BREAK, enemy, 0f, HitSource.STATUS, element)
    onWeaponBreak(enemy, element)
}

internal fun MutableGameState.onReaction(enemy: Enemy, reaction: ReactionId) {
    if (!listens(EffectTrigger.REACTION)) return
    fireClauses(EffectTrigger.REACTION, enemy, 0f, HitSource.STATUS, reaction.first, reaction = reaction)
}

/** First contact with an enemy during a Dash. */
internal fun MutableGameState.onDashHit(enemy: Enemy, impact: Float) {
    fireClauses(EffectTrigger.DASH_HIT, enemy, impact, HitSource.DASH)
    if (tagTier(BuildTag.DRIVE) >= 2 && enemy.alive) {
        val dominant = build.dominant
        if (dominant != null) applyStatus(enemy, dominant, 2, 0) else dealProcDamage(enemy, impact * 0.2f, rescale = false)
    }
    if (tagTier(BuildTag.DRIVE) >= 3 && !dashHitThisDash) heat = max(0f, heat - effectiveDashHeatCost() * 0.6f)
    dashHitThisDash = true
}

/** Every level hardens the Core a little (content-owned amounts), so survival no longer depends on stat-only items. */
internal fun MutableGameState.onLevelUp() {
    val ceiling = maxHp
    val levelIntegrity = content.tempo.levelIntegrity
    maxHp += levelIntegrity
    val gained = ceilingChangeAfterLiability(ceiling, levelIntegrity)
    if (build.liability(kinetickk.ball.content.api.RelicId.AEGIS_INVERSION)) hp = minOf(hp, maxHp) else hp += gained
    heal(content.tempo.levelRepair, HealSource.LEVEL)
    fireClauses(EffectTrigger.LEVEL_UP)
    if (tagTier(BuildTag.FORTUNE) >= 2 && level % 4 == 0) rerollsRemaining++
}

/**
 * Ouroboros Seal's liability and Data-to-skills' cost; applied to every Data gain, pickups and item clauses alike.
 */
internal fun MutableGameState.dataGainLiability(): Float =
    (if (build.liability(kinetickk.ball.content.api.RelicId.OUROBOROS_SEAL)) 0.75f else 1f) *
        if (build.dataToSkills > 0f) kinetickk.ball.content.api.DATA_CONVERSION_GAIN else 1f

/** Agony Scepter's mutation strength: ranks 1, 2 and 3 map to the Forbidden forms 1, 3 and 5. */
internal fun MutableGameState.agonyPower(): Int =
    if (build.upside(kinetickk.ball.content.api.RelicId.AGONY_SCEPTER)) relicRank(kinetickk.ball.content.api.RelicId.AGONY_SCEPTER) * 2 - 1 else 0

/** Agony Scepter's liability for discrete weapon activations. */
internal fun MutableGameState.onWeaponActivation() {
    if (!build.liability(kinetickk.ball.content.api.RelicId.AGONY_SCEPTER)) return
    val before = heat
    heat = min(MutableGameState.MAX_HEAT, heat + 6f)
    applyOverheatEdge(before)
}
