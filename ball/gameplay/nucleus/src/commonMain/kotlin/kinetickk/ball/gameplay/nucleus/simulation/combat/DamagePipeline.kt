// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Where damage comes from. Direct sources roll crits and fire on-hit clauses; the rest never do. Ordinals
 * are stored (plus one) in [StatusCell.LAST_SOURCE], so sources are only appended.
 */
internal enum class HitSource(val direct: Boolean) {
    IMPACT(true),
    WEAPON(true),
    DASH(true),
    ECHO(true),
    PROC(false),
    STATUS(false),
    /** Skill damage: never crits, fires no on-hit clause; its kills fire KILL clauses with no source. */
    SKILL(false),
}

// ---------------------------------------------------------------------------------------------
// Damage pipeline.

/** Σ item and resonance amplification for a direct hit; the part above the soft cap counts half. */
private fun MutableGameState.itemAmplification(enemy: Enemy, source: HitSource): Float {
    var amp = buffAmount(BuffStat.DAMAGE) + whileAmount(BuffStat.DAMAGE, enemy, source)
    when (source) {
        HitSource.IMPACT, HitSource.DASH -> {
            amp += buffAmount(BuffStat.IMPACT) + whileAmount(BuffStat.IMPACT, enemy, source)
            if (tagTier(BuildTag.RAM) >= 1) amp += 0.15f
            if (tagTier(BuildTag.RAM) >= 3 && speed >= 1_200f) amp += 0.25f
        }
        HitSource.WEAPON, HitSource.ECHO -> {
            amp += buffAmount(BuffStat.WEAPON) + whileAmount(BuffStat.WEAPON, enemy, source)
            if (tagTier(BuildTag.ARSENAL) >= 2) amp += 0.20f
        }
        HitSource.PROC, HitSource.STATUS, HitSource.SKILL -> Unit
    }
    val versus = build.damageVersus
    for (index in versus.indices) {
        if (holds(versus[index].condition, enemy, source, null)) amp += versus[index].amount
    }
    if (build.comboDamagePer > 0f) amp += min(build.comboDamageCap, combo * build.comboDamagePer)
    val cap = content.build.policy.amplificationSoftCap
    return if (amp > cap) cap + (amp - cap) * 0.5f else amp
}

internal const val BRITTLE_VULNERABILITY: Float = 0.6f

/** Relic "+damage" terms share one soft-capped surge: 1 + 3B / (3 + B), ceiling ×4. */
internal fun MutableGameState.relicSurge(): Float {
    var bonus = 0f
    if (build.upside(RelicId.GLASS_COMET)) {
        bonus += content.relic(RelicId.GLASS_COMET).value(0, relicRank(RelicId.GLASS_COMET)) / 100f *
            build.relicUpside(RelicId.GLASS_COMET)
    }
    if (build.upside(RelicId.FURNACE_COVENANT)) {
        val steps = ((heat - 40f) / 10f).coerceIn(0f, 6f)
        var furnace = steps * content.relic(RelicId.FURNACE_COVENANT).value(0, relicRank(RelicId.FURNACE_COVENANT)) / 100f *
            build.relicUpside(RelicId.FURNACE_COVENANT)
        if (build.pairing(RelicPairingId.THERMAL_PARADOX)) furnace *= 0.5f
        if (build.pairing(RelicPairingId.MELTDOWN) && overdriveTime > 0f) furnace *= 1.5f
        bonus += furnace
    }
    if (build.upside(RelicId.PERPETUAL_VECTOR)) {
        val steps = ((min(speed, 2_200f) - 600f) / 400f).coerceAtLeast(0f)
        bonus += steps * content.relic(RelicId.PERPETUAL_VECTOR).value(0, relicRank(RelicId.PERPETUAL_VECTOR)) / 100f *
            build.relicUpside(RelicId.PERPETUAL_VECTOR)
    }
    if (build.upside(RelicId.HOLLOW_CROWN)) {
        bonus += emptyRelicSlots() * content.relic(RelicId.HOLLOW_CROWN).value(0, 1) / 100f *
            build.relicUpside(RelicId.HOLLOW_CROWN)
    }
    if (glassCharge > 0f) bonus += glassCharge / 300f
    return if (bonus <= 0f) 1f else 1f + 3f * bonus / (3f + bonus)
}

/** Multiplicative liabilities on damage dealt; never diluted by the surge soft cap. */
private fun MutableGameState.damageLiability(source: HitSource, critical: Boolean): Float {
    var multiplier = 1f
    if (build.liability(RelicId.OVERDRIVE_CASCADE) && overdriveTime <= 0f) multiplier *= 0.7f
    if (source == HitSource.IMPACT || source == HitSource.DASH) {
        if (build.liability(RelicId.ECHO_CHAMBER)) multiplier *= 0.5f
        if (build.liability(RelicId.PERPETUAL_VECTOR) && speed < 600f) multiplier *= 0.7f
    }
    if (source.direct && !critical) {
        if (build.liability(RelicId.EXECUTIONERS_PRISM)) multiplier *= 0.8f
        if (build.nonCriticalDamage != 0f) multiplier *= (1f + build.nonCriticalDamage).coerceAtLeast(0.1f)
    }
    return multiplier
}

/**
 * Σ vulnerability on the target, capped by policy. [brittle] false leaves out Brittle's bonus for damage that
 * does not spend it (skill damage), so Brittle pays out once, on the direct hit that consumes it.
 */
internal fun MutableGameState.vulnerability(enemy: Enemy, source: HitSource, brittle: Boolean = true): Float {
    var vulnerable = stacks(enemy, StatusElement.ENTROPY) * content.build.element(StatusElement.ENTROPY).perStack *
        build.potency(StatusElement.ENTROPY)
    if (source == HitSource.IMPACT || source == HitSource.DASH) {
        val resonance = stacks(enemy, StatusElement.SONIC)
        if (resonance > 0) vulnerable += resonance * RESONANCE_IMPACT_PER_STACK * build.potency(StatusElement.SONIC)
        val gravitic = stacks(enemy, StatusElement.GRAVITIC)
        vulnerable += gravitic * content.build.element(StatusElement.GRAVITIC).perStack * build.potency(StatusElement.GRAVITIC)
        if (gravitic > 0 && build.upside(RelicId.EVENT_HORIZON)) {
            vulnerable += content.relic(RelicId.EVENT_HORIZON).value(0, relicRank(RelicId.EVENT_HORIZON)) / 100f *
                build.relicUpside(RelicId.EVENT_HORIZON)
        }
    }
    if (enemy.status[StatusCell.EMBRITTLED_UNTIL] > elapsed) {
        vulnerable += content.build.reaction(kinetickk.ball.content.api.ReactionId.EMBRITTLE).power
    }
    // Brittle strengthens the next direct hit inside the same cap; dealHit consumes it.
    if (brittle && source.direct && enemy.status[StatusCell.BRITTLE] > 0f) vulnerable += BRITTLE_VULNERABILITY
    return vulnerable.coerceIn(0f, content.build.policy.vulnerabilityCap)
}

private class CritOutcome(val critical: Boolean, val multiplier: Float)

private fun MutableGameState.resolveCrit(
    enemy: Enemy,
    source: HitSource,
    canCrit: Boolean,
    forceCrit: Boolean,
    critChanceBonus: Float = 0f,
): CritOutcome {
    if (!canCrit && !forceCrit) return CritOutcome(false, 1f)
    val executioner = build.upside(RelicId.EXECUTIONERS_PRISM)
    val chance = (effectiveCritChance() + critChanceBonus).coerceIn(0f, 0.75f)
    var multiplier = critMultiplier
    var forced = forceCrit
    val always = build.alwaysCrit
    for (index in always.indices) {
        if (holds(always[index], enemy, source, null)) {
            forced = true
            break
        }
    }
    if (enemy.status[StatusCell.MARKED_UNTIL] > elapsed) forced = true
    if (build.pairing(RelicPairingId.COLD_VERDICT) && stacks(enemy, StatusElement.CRYO) >= 4) forced = true
    if (build.guaranteedCritAfter > 0 && critMissStreak >= build.guaranteedCritAfter) forced = true
    val critical = if (executioner) {
        forced || belowExecutionLine(enemy, 0.35f, bossFinalPhaseOf(enemy), phased = encounterSpec(enemy) != null) ||
            isFrozen(enemy) || enemy.status[StatusCell.BRITTLE] > 0f ||
            statusNearBreak(enemy, 0.75f)
    } else {
        val effectiveChance = if (build.critConversionRate > 0f) min(chance, 0.3f) else chance
        forced || gameplayRandom.nextFloat() < effectiveChance
    }
    if (executioner) {
        multiplier += min(chance, 0.5f) * content.relic(RelicId.EXECUTIONERS_PRISM).value(0, relicRank(RelicId.EXECUTIONERS_PRISM)) *
            build.relicUpside(RelicId.EXECUTIONERS_PRISM)
    } else if (build.critConversionRate > 0f && chance > 0.3f) {
        multiplier += (chance - 0.3f) * build.critConversionRate
    }
    val versus = build.critDamageVersus
    for (index in versus.indices) {
        if (holds(versus[index].condition, enemy, source, null)) multiplier += versus[index].amount
    }
    if (tagTier(BuildTag.PRECISION) >= 3 && totalStacks(enemy) > 0) multiplier *= 1.4f
    critMissStreak = if (critical) 0 else critMissStreak + 1
    return CritOutcome(critical, if (critical) min(multiplier, content.build.policy.criticalMultiplierCap) else 1f)
}

/**
 * The single direct-hit path for impacts, weapons, dash strikes and echoes. Computes the full
 * pipeline, applies the damage, then dispatches on-hit clauses and relic rules once.
 */
internal fun MutableGameState.dealHit(
    enemy: Enemy,
    base: Float,
    source: HitSource,
    canCrit: Boolean = true,
    qualified: Boolean = true,
    sourceWeapon: WeaponId? = null,
    /** The Diamond's full Edge: this hit is critical whatever the roll. */
    forceCrit: Boolean = false,
    /** Extra crit chance for this hit (a CRIT-scaling weapon's own hits). */
    critChanceBonus: Float = 0f,
): DamageResult {
    if (base <= 0f || !enemy.alive) return DamageResult(0f, false)
    // A shifting Architect takes nothing, so no roll, Brittle or Freeze is spent on it.
    if (isShifting(enemy)) return DamageResult(0f, false)
    if (source == HitSource.ECHO) {
        // An echo repeats a share of an already resolved hit: no multiplier applies twice, no state is consumed.
        val echo = damageEnemy(enemy, base, critical = false, killEligible = false, source = source)
        if (echo.amount > 0f && qualified) onDirectHit(enemy, echo, source, sourceWeapon)
        return echo
    }
    val crit = resolveCrit(enemy, source, canCrit, forceCrit, critChanceBonus)
    var amount = base * (1f + itemAmplification(enemy, source)) * relicSurge() * crit.multiplier *
        (1f + vulnerability(enemy, source)) * damageLiability(source, crit.critical) * matrix.damageMultiplier *
        characterDamageMultiplier(enemy)
    if (enemy.status[StatusCell.BRITTLE] > 0f) enemy.status[StatusCell.BRITTLE] = 0f
    val shatter = build.upside(RelicId.ABSOLUTE_ZERO) && isFrozen(enemy)
    if (shatter) {
        amount *= content.relic(RelicId.ABSOLUTE_ZERO).value(1, relicRank(RelicId.ABSOLUTE_ZERO)) / 100f *
            build.relicUpside(RelicId.ABSOLUTE_ZERO) * if (build.pairing(RelicPairingId.COLD_VERDICT)) 1.5f else 1f
        thaw(enemy)
    }
    val result = dealReceived(enemy, amount, source, element = null,
        critical = crit.critical || shatter && build.pairing(RelicPairingId.COLD_VERDICT), killEligible = true)
    if (shatter) {
        shockwave(enemy.x, enemy.y, 0.3f, 120f, 1)
        applyStatusAround(enemy.x, enemy.y, 120f, StatusElement.CRYO, 2f, generation = 1, excludedId = enemy.id)
    }
    // Spall: the hit cashes Thermal Shock's mark.
    cashSpall(enemy)
    if (result.amount > 0f && qualified) {
        // The shattering hit's own clauses still count the target as Frozen.
        if (shatter) shatteringEnemyId = enemy.id
        try {
            onDirectHit(enemy, result, source, sourceWeapon)
        } finally {
            if (shatter) shatteringEnemyId = -1
        }
    }
    return result
}

/**
 * The equipped weapon's hits. CONTINUOUS and CONTACT hits scale with [continuousWeaponRate]; DISCRETE
 * ones already gained their rate through [cooldown]. The proc rule: only a hit whose [procKey] still
 * qualifies (the first enemy of a discrete activation, or a persistent source at most once per
 * [WeaponRuntime.SOURCE_INTERVAL]) fires WEAPON_HIT clauses, Echo Chamber, the ARSENAL counter and relic
 * on-hit rules, and counts toward a kinetic weapon's adopted element; every other hit deals damage only
 * (its element is applied by the weapon itself). The default key qualifies discrete hits always and a
 * continuous or contact weapon's main source on its interval.
 */
internal fun MutableGameState.dealWeaponDamage(
    enemy: Enemy,
    baseAmount: Float,
    canCrit: Boolean = false,
    cadence: WeaponHitCadence = WeaponHitCadence.DISCRETE,
    sourceWeapon: WeaponId = weapon,
    procKey: Int = if (cadence == WeaponHitCadence.DISCRETE) WeaponRuntime.PROC_ALWAYS else WeaponRuntime.sourceKey(0),
    forceCrit: Boolean = false,
): DamageResult {
    if (baseAmount <= 0f || !enemy.alive) return DamageResult(0f, false)
    val definition = content.weapon(sourceWeapon)
    var base = if (cadence == WeaponHitCadence.DISCRETE) baseAmount else baseAmount * continuousWeaponRate()
    if (definition.scaling == kinetickk.ball.content.api.WeaponScaling.STATUS && totalStacks(enemy) > 0) base *= STATUS_SCALING_BONUS
    val critBonus = if (definition.scaling == kinetickk.ball.content.api.WeaponScaling.CRIT) CRIT_SCALING_CHANCE else 0f
    val runtime = weaponRuntime
    val qualified = runtime.canQualify(procKey, elapsed)
    val result = dealHit(enemy, base, HitSource.WEAPON, canCrit = canCrit, qualified = qualified, sourceWeapon = sourceWeapon,
        forceCrit = forceCrit, critChanceBonus = critBonus)
    // Character hooks (Orbit release, Puncture) are no on-hit build effect: a contact hit always reaches them, and a
    // continuous weapon reaches them once per enemy every CONTINUOUS_HOOK_SECONDS, as every weapon hit used to qualify.
    if (!qualified && result.amount > 0f && enemy.alive) {
        when (cadence) {
            WeaponHitCadence.CONTACT -> onCharacterPrimaryHit(enemy)
            WeaponHitCadence.CONTINUOUS -> if (enemy.weaponCooldown <= 0f) {
                enemy.weaponCooldown = CONTINUOUS_HOOK_SECONDS
                onCharacterPrimaryHit(enemy)
            }
            WeaponHitCadence.DISCRETE -> Unit
        }
    }
    if (qualified && result.amount > 0f) {
        runtime.spendQualification(procKey, elapsed)
        if (definition.element == null) {
            runtime.infusionHits = (runtime.infusionHits + 1) % KINETIC_INFUSION_EVERY
            if (runtime.infusionHits == 0 && enemy.alive) weaponInfusionElement(definition)?.let { applyStatus(enemy, it, 1, 0) }
        }
    }
    return result
}

/** A continuous weapon's per-enemy interval for character hooks (Orbit release, Puncture). */
internal const val CONTINUOUS_HOOK_SECONDS: Float = 0.22f

/**
 * Item and relic proc damage. [amount] is already measured against its basis; [rescale] adds target
 * vulnerability and liabilities. The Matrix damage multiplier always applies here, once: hit bases are
 * measured without it. An element-picked proc carries its [element]. A [shareCapped] kill proc
 * takes at most [KILL_PROC_SHARE_CAP] of the target's weighted integrity. Proc kills never fire kill clauses.
 */
internal fun MutableGameState.dealProcDamage(
    enemy: Enemy,
    amount: Float,
    rescale: Boolean = true,
    tone: Int = 0,
    element: StatusElement? = null,
    shareCapped: Boolean = false,
): DamageResult {
    if (amount <= 0f || !enemy.alive) return DamageResult(0f, false)
    if (isShifting(enemy)) return DamageResult(0f, false)
    var scaled = (if (rescale) amount * (1f + vulnerability(enemy, HitSource.PROC)) * damageLiability(HitSource.PROC, false) else amount) *
        matrix.damageMultiplier
    if (shareCapped) scaled = min(scaled, KILL_PROC_SHARE_CAP * enemy.maxHp * victimFactor(enemy))
    return dealReceived(enemy, scaled, HitSource.PROC, element, critical = false, killEligible = false, tone = tone)
}

/**
 * Status, Break and reaction damage: never crits; scales with surge and vulnerability. Reaction damage
 * passes [ignoresAffinity]: no enemy is immune to both elements of a pair.
 */
internal fun MutableGameState.dealStatusDamage(
    enemy: Enemy,
    amount: Float,
    element: StatusElement?,
    showNumber: Boolean = true,
    ignoresAffinity: Boolean = false,
): DamageResult {
    if (amount <= 0f || !enemy.alive) return DamageResult(0f, false)
    if (isShifting(enemy)) return DamageResult(0f, false)
    val scaled = amount * relicSurge() * (1f + vulnerability(enemy, HitSource.STATUS)) *
        damageLiability(HitSource.STATUS, false) * matrix.damageMultiplier
    val tone = element?.let { it.ordinal + 1 } ?: 0
    if (ignoresAffinity) {
        return damageEnemy(enemy, scaled, false, killEligible = true, tone = tone, showNumber = showNumber, source = HitSource.STATUS)
    }
    return dealReceived(enemy, scaled, HitSource.STATUS, element, critical = false, killEligible = true, tone = tone,
        showNumber = showNumber)
}

/**
 * Skill damage: never crits; scales with the Relic surge, the target's vulnerability (impact terms for a
 * [kinetic] skill; never Brittle, which only the direct hit that spends it takes), damage liabilities and the
 * Matrix multiplier. An element-tagged skill is refused only
 * by an IMMUNE aspect; a kinetic one meets the impact frame, an element-less one the weapon frame. Kills
 * fire KILL clauses (the source maps to none); the Architect's gate applies in [damageEnemy].
 */
internal fun MutableGameState.dealSkillDamage(
    enemy: Enemy,
    amount: Float,
    element: StatusElement?,
    kinetic: Boolean = false,
): DamageResult {
    if (amount <= 0f || !enemy.alive) return DamageResult(0f, false)
    if (isShifting(enemy)) return DamageResult(0f, false)
    val scaled = amount * relicSurge() *
        (1f + vulnerability(enemy, if (kinetic) HitSource.IMPACT else HitSource.SKILL, brittle = false)) *
        damageLiability(HitSource.SKILL, false) * matrix.damageMultiplier
    val received = when {
        element != null -> if (aspectRefuses(enemy, element)) 0f else 1f
        kinetic -> enemy.impactTaken
        else -> enemy.weaponTaken * armorWeaponFactor(enemy, HitSource.SKILL)
    }
    if (received <= 0f) {
        signalImmunity(enemy)
        return DamageResult(0f, false)
    }
    val tone = element?.let { it.ordinal + 1 } ?: 0
    return damageEnemy(enemy, scaled * received, critical = false, killEligible = true, tone = tone,
        source = HitSource.SKILL, unmitigated = scaled)
}

/**
 * Character abilities bypass the build pipeline but still meet the receiver's kinetic frame. Refit
 * ([MutableGameState.characterUpgradePower]) always applies; the Edge applies unless the amount already carries
 * it ([edge] false: Apex strikes and shots); the dealt amount counts as the run's ability output.
 */
internal fun MutableGameState.dealCharacterDamage(enemy: Enemy, amount: Float, edge: Boolean = true): DamageResult {
    if (amount <= 0f || !enemy.alive) return DamageResult(0f, false)
    val scaled = amount * matrix.damageMultiplier * characterUpgradePower *
        if (edge) characterDamageMultiplier(enemy) else 1f
    val integrity = enemy.hp
    val result = damageEnemy(enemy, scaled * enemy.weaponTaken, unmitigated = scaled)
    recordAbilityDamage(integrity, result)
    return result
}

/** Counts a character ability's damage as run output, up to the integrity the target had left. */
internal fun MutableGameState.recordAbilityDamage(integrityBefore: Float, result: DamageResult) {
    characterRuntime.abilityDamage += min(result.amount, integrityBefore.coerceAtLeast(0f)).toDouble()
}

/** Final share of a victim's weighted integrity a kill proc may take from any one other enemy. */
internal const val KILL_PROC_SHARE_CAP: Float = 0.4f

/**
 * The receiving enemy's own multiplier, applied once per damage instance: element-tagged damage is
 * refused only by an IMMUNE aspect; element-less damage meets the kinetic frame of its source.
 */
internal fun MutableGameState.receivedScale(enemy: Enemy, source: HitSource, element: StatusElement?): Float = when {
    element != null -> if (aspectRefuses(enemy, element)) 0f else 1f
    source == HitSource.IMPACT || source == HitSource.DASH -> enemy.impactTaken
    else -> enemy.weaponTaken * armorWeaponFactor(enemy, source)
}

/** An immune aspect refuses its own element, unless the enemy is Suppressed (Crevasse). */
internal fun MutableGameState.aspectRefuses(enemy: Enemy, element: StatusElement): Boolean =
    enemy.aspectIsImmune && enemy.aspectElement == element.ordinal && !markIs(enemy, MarkKind.SUPPRESSED)

private fun MutableGameState.dealReceived(
    enemy: Enemy,
    unmitigated: Float,
    source: HitSource,
    element: StatusElement?,
    critical: Boolean,
    killEligible: Boolean,
    tone: Int = 0,
    showNumber: Boolean = true,
): DamageResult {
    val scale = receivedScale(enemy, source, element)
    if (scale <= 0f) {
        signalImmunity(enemy)
        return DamageResult(0f, false)
    }
    return damageEnemy(enemy, unmitigated * scale, critical, killEligible, tone, showNumber, source, unmitigated)
}

/** Measures a clause basis for a triggering context. */
internal fun MutableGameState.basisAmount(basis: DamageBasis, hit: Float, victim: Enemy?): Float = when (basis) {
    DamageBasis.HIT -> hit
    DamageBasis.IMPACT -> impactPower(speed)
    DamageBasis.BRAKE -> impactPower(max(brakeShedSpeed, 200f)) * sqrt(runScale())
    DamageBasis.WEAPON -> 40f * abilityPower()
    DamageBasis.FLAT -> runScale() * relicSurge()
    DamageBasis.VICTIM_MAX_HP -> if (victim == null) 0f else victim.maxHp * victimFactor(victim)
    DamageBasis.SHIELD -> shieldCapacity() * sqrt(runScale())
}

/** Share of every %max-integrity term (item procs, Rupture, Feedback) an enemy takes. */
internal fun victimFactor(enemy: Enemy): Float = enemy.type.profile.victimFactor

/**
 * The low-level applicator and the single enemy-integrity writer: gates every boss with an encounter spec, records
 * damage, flashes and publishes a bounded damage number. [unmitigated] is reported back for clause bases.
 */
internal fun MutableGameState.damageEnemy(
    enemy: Enemy,
    amount: Float,
    critical: Boolean = false,
    killEligible: Boolean = false,
    tone: Int = 0,
    showNumber: Boolean = true,
    source: HitSource? = null,
    unmitigated: Float = amount,
    execute: Boolean = false,
): DamageResult {
    if (amount <= 0f || !enemy.alive) return DamageResult(0f, false)
    // Herald shields and Aegis tethers guard the receiver; a phased Phantom takes nothing at all.
    val guarded = guardedDamage(enemy, amount, source, execute)
    val spec = encounterSpec(enemy)
    val applied = when {
        spec != null -> gateBossDamage(enemy, spec, guarded)
        isShifting(enemy) -> 0f
        else -> guarded
    }
    if (applied <= 0f) return DamageResult(0f, false)
    val removed = minOf(applied, enemy.hp).toDouble()
    damageDealt += removed
    if (skillCausedHit(source)) skillRuntime.skillCausedDamage += removed
    rememberHollowDamage(enemy, applied)
    enemy.hp -= applied
    crackArmor(enemy, source)
    if (spec != null) {
        enemy.lastDamagedAt = elapsed
        onBossDamaged(enemy, spec)
    }
    if (enemy.hp <= 0f) {
        // Kill rules read the source of the killing blow, never an earlier hit.
        enemy.killClausesEligible = killEligible
        enemy.status[StatusCell.LAST_SOURCE] = (source?.ordinal?.plus(1) ?: 0).toFloat()
    }
    enemy.flash = max(enemy.flash, if (applied >= 5f) 1f else 0.16f)
    if (showNumber && settings.damageNumbers && applied >= 5f) {
        emitVisualFx(
            VisualFxCue.DamageNumberAdded(
                x = enemy.x,
                y = enemy.y - enemy.radius,
                amount = applied.roundToLong(),
                critical = critical,
                tone = tone,
            ),
        )
    }
    return DamageResult(applied, critical, unmitigated)
}

/**
 * Destroys [enemy] outright with its remaining integrity: an Aegis tether cannot soften an execute, while
 * the Architect's gate and a phased Phantom's untargetability still apply. Callers keep bosses out.
 */
internal fun MutableGameState.executeEnemy(enemy: Enemy, source: HitSource, tone: Int = 0): DamageResult =
    damageEnemy(enemy, enemy.hp, killEligible = true, tone = tone, source = source, execute = true)
