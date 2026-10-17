// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Nested clause dispatch is bounded: a clause's own consequences may dispatch at most one more level. */
internal const val MAX_CLAUSE_DEPTH: Int = 2

/** Highest stack growth a %max-HP proc may take. */
internal const val MAX_HP_PROC_SCALE: Float = 1.25f

private fun EffectClause.appliesStatus(): Boolean {
    for (index in actions.indices) if (actions[index] is EffectAction.ApplyStatus) return true
    return false
}

/** Items may apply statuses at most this often per second at one stack (more with stack magnitude). */
internal const val ITEM_STATUS_RATE: Float = 6f

/**
 * Dispatches every owned clause listening to [trigger]. [target] is the enemy involved (hit, killed,
 * broken), [hit] the triggering damage, [element] the element of a Break and [reaction] the reaction
 * that fired (its two elements both count as involved). Clause consequences are proc damage, so they
 * never re-trigger hit or kill clauses.
 */
internal fun MutableGameState.fireClauses(
    trigger: EffectTrigger,
    target: Enemy? = null,
    hit: Float = 0f,
    source: HitSource? = null,
    element: StatusElement? = null,
    statusOnly: Boolean = false,
    reaction: ReactionId? = null,
) {
    val packed = build.clauses(trigger)
    if (packed.isEmpty() || clauseDepth >= MAX_CLAUSE_DEPTH) return
    clauseDepth++
    try {
        for (index in packed.indices) {
            val itemId = CompiledBuild.clauseItem(packed[index])
            val clauseIndex = CompiledBuild.clauseIndex(packed[index])
            val item = content.items[itemId]
            val clause = item.clauses[clauseIndex]
            val global = content.clauseIndex(itemId, clauseIndex)
            if (clauseReadyAt[global] > elapsed) continue
            var conditionsHold = true
            for (conditionIndex in clause.conditions.indices) {
                if (!holds(clause.conditions[conditionIndex], target, source, element, reaction)) {
                    conditionsHold = false
                    break
                }
            }
            if (!conditionsHold) continue
            val gate = clause.everyNthAt(effectiveLuck())
            // Echoes only repeat ungated status clauses; they never advance counters or start cooldowns.
            if (statusOnly && (gate > 1 || clause.cooldownSeconds > 0f || !clause.appliesStatus())) continue
            if (gate > 1) {
                val count = clauseCounters[global] + 1
                clauseCounters[global] = count % gate
                if (count % gate != 0) continue
            }
            if (clause.cooldownSeconds > 0f) clauseReadyAt[global] = elapsed + clause.cooldownSeconds
            val generation = if (trigger == EffectTrigger.BREAK || trigger == EffectTrigger.REACTION) 1 else 0
            runClause(itemId, clause, target, hit, generation, statusOnly)
        }
    } finally {
        clauseDepth--
    }
}

private fun MutableGameState.runClause(
    itemId: Int,
    clause: EffectClause,
    target: Enemy?,
    hit: Float,
    generation: Int,
    statusOnly: Boolean,
) {
    val magnitude = build.magnitude(itemId)
    val bonus = build.bonus(itemId)
    // Damage scale per basis: %max-HP procs cap their stack growth and ignore Reborn and attunement.
    fun scale(basis: DamageBasis) = if (basis == DamageBasis.VICTIM_MAX_HP) min(MAX_HP_PROC_SCALE, magnitude) else magnitude * bonus
    val originX = target?.x ?: coreX
    val originY = target?.y ?: coreY
    for (actionIndex in clause.actions.indices) {
        val action = clause.actions[actionIndex]
        if (statusOnly && action !is EffectAction.ApplyStatus) continue
        when (action) {
            is EffectAction.ApplyStatus -> {
                val element = resolvePick(action.pick)
                if (element == null) {
                    if (action.pick == ElementPick.DOMINANT && target != null && target.alive) {
                        dealProcDamage(target, hit * 0.2f * magnitude, rescale = false)
                    }
                    continue
                }
                val whole = wholeStatusStacks(itemId, action.stacks * magnitude)
                if (whole <= 0) continue
                if (action.radius > 0f) {
                    applyStatusAround(originX, originY, action.radius, element, whole.toFloat(), generation)
                } else if (target != null) {
                    applyStatus(target, element, whole, generation)
                }
            }
            is EffectAction.Burst -> {
                val damage = basisAmount(action.basis, hit, target) * action.scale * scale(action.basis)
                val shared = action.basis == DamageBasis.VICTIM_MAX_HP
                areaDamage(originX, originY, action.radius, damage,
                    excludedId = if (shared) target?.id ?: -1 else -1,
                    rescale = action.basis != DamageBasis.HIT, shareCapped = shared)
            }
            is EffectAction.Strike -> {
                // A shifting Architect takes nothing, so its %max-integrity cooldown is not spent either.
                if (target == null || !target.alive || isShifting(target)) continue
                if (action.basis == DamageBasis.VICTIM_MAX_HP) {
                    if (target.status[StatusCell.MAX_HP_PROC_READY_AT] > elapsed) continue
                    target.status[StatusCell.MAX_HP_PROC_READY_AT] = elapsed + maxHpProcCooldown(target)
                }
                val damage = basisAmount(action.basis, hit, target) * action.scale * scale(action.basis)
                dealProcDamage(target, damage, rescale = action.basis != DamageBasis.HIT)
            }
            is EffectAction.Chain -> {
                val damage = basisAmount(action.basis, hit, target) * action.scale * scale(action.basis)
                val element = action.pick?.let(::resolvePick)
                val stacksPerLink = if (element != null) wholeStatusStacks(itemId, action.stacks * magnitude) else 0
                chain(originX, originY, target?.id ?: -1, action.count, action.range) { link ->
                    dealProcDamage(link, damage, rescale = action.basis != DamageBasis.HIT, element = element,
                        shareCapped = action.basis == DamageBasis.VICTIM_MAX_HP)
                    if (element != null && stacksPerLink > 0 && link.alive) applyStatus(link, element, stacksPerLink, generation)
                }
            }
            is EffectAction.Shards -> {
                val damage = basisAmount(action.basis, hit, target) * action.scale * scale(action.basis)
                val element = action.pick?.let(::resolvePick)
                fireShards(originX, originY, action.count, damage, element, target?.id ?: -1, generation,
                    shareCapped = action.basis == DamageBasis.VICTIM_MAX_HP)
            }
            is EffectAction.Pull -> pushEnemies(originX, originY, action.radius, action.strength * magnitude, inward = true)
            is EffectAction.Push -> pushEnemies(originX, originY, action.radius, action.strength * magnitude, inward = false)
            is EffectAction.Heal -> heal(action.amount * magnitude * bonus, HealSource.ITEM)
            is EffectAction.Shield -> addShield(action.amount * magnitude * bonus)
            is EffectAction.Vent -> heat = max(0f, heat - action.heat * magnitude)
            is EffectAction.Overdrive -> gainOverdrive(action.charge * magnitude * bonus)
            is EffectAction.Buff -> addBuff(itemId, action.stat, action.amount * magnitude * bonus, action.seconds, action.maxStacks)
            is EffectAction.ExtendCombo -> if (combo > 0) comboTime = min(effectiveComboWindow() * 2f, comboTime + action.seconds * magnitude)
            is EffectAction.RefreshWeapon -> refreshWeapon(action.seconds * magnitude, itemId)
            is EffectAction.GainData -> gainData(action.amount * magnitude * bonus)
            is EffectAction.Well -> {
                // Field ticks apply the relic surge live, so a FLAT well is measured without it.
                val measured = if (action.basis == DamageBasis.FLAT) runScale() else basisAmount(action.basis, hit, target)
                val dps = measured * action.scale * scale(action.basis)
                val element = action.pick?.let(::resolvePick)
                addField(CombatField(
                    kind = CombatFieldKind.DARK_STAR, remaining = action.seconds, x = originX, y = originY,
                    radius = action.radius, damagePerSecond = dps, element = element,
                    stacksPerTick = if (element != null) 1 else 0, generation = 1, pull = 240f,
                ))
            }
            is EffectAction.Detonate -> if (target != null && target.alive && !isShifting(target)) detonate(target, action, magnitude * bonus)
        }
    }
}

/** Fractional stacks accumulate per item; the per-item rate bound applies here. Returns whole stacks. */
private fun MutableGameState.wholeStatusStacks(itemId: Int, amount: Float): Int {
    if (itemStatusReadyAt[itemId] > elapsed) return 0
    val magnitude = max(1f, build.magnitude(itemId))
    itemStatusReadyAt[itemId] = elapsed + 1f / (ITEM_STATUS_RATE * magnitude)
    val total = statusAccumulators[itemId] + amount
    val whole = floor(total).toInt()
    statusAccumulators[itemId] = total - whole
    return whole
}

internal fun MutableGameState.resolvePick(pick: ElementPick): StatusElement? = pick.element ?: when (pick) {
    ElementPick.DOMINANT -> build.dominant
    ElementPick.SECONDARY -> build.secondary
    else -> null
}

private fun MutableGameState.fireShards(
    x: Float,
    y: Float,
    count: Int,
    damage: Float,
    element: StatusElement?,
    excludedId: Int,
    generation: Int,
    shareCapped: Boolean,
) {
    repeat(count) { shard ->
        val angle = shard * TAU / count + elapsed * 1.7f
        val endX = x + cos(angle) * 240f
        val endY = y + sin(angle) * 240f
        addRelicArc(x, y, endX, endY)
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.id == excludedId || !enemy.alive) continue
            if (segmentCircleIntersects(x, y, endX, endY, enemy.x, enemy.y, enemy.radius + 6f)) {
                dealProcDamage(enemy, damage, rescale = false, element = element, shareCapped = shareCapped)
                if (element != null && enemy.alive) applyStatus(enemy, element, 1, generation)
            }
        }
    }
}

/** Damage over time becomes burst: consumes the listed statuses for their remaining damage at once. */
private fun MutableGameState.detonate(target: Enemy, action: EffectAction.Detonate, scale: Float) {
    if (target.status[StatusCell.DETONATE_READY_AT] > elapsed) return
    var total = 0f
    var element: StatusElement? = null
    for (index in action.elements.indices) {
        val listed = action.elements[index]
        val count = stacks(target, listed)
        if (count <= 0) continue
        val perStack = when (listed) {
            StatusElement.THERMAL -> content.build.element(StatusElement.THERMAL).perStack
            StatusElement.ENTROPY -> ENTROPY_DAMAGE_PER_STACK
            else -> 0f
        }
        if (perStack == 0f) continue
        // n stacks decaying one at a time deal perStack × n(n+1)/2 × decay seconds of damage.
        total += perStack * build.potency(listed) * runScale() * count * (count + 1) * 0.5f * decaySeconds(listed)
        target.status[StatusCell.STACKS + listed.ordinal] = 0f
        if (element == null || count * 2 >= breakThreshold(listed, target)) element = listed
    }
    if (total <= 0f) return
    target.status[StatusCell.DETONATE_READY_AT] = elapsed + 2f
    dealStatusDamage(target, total * action.scale * scale, element)
    shockwave(target.x, target.y, 0.3f, target.radius * 2.6f, 4)
}

private fun MutableGameState.addBuff(itemId: Int, stat: kinetickk.ball.content.api.BuffStat, amount: Float, seconds: Float, maxStacks: Int) {
    val existing = buffs.indexOfFirst { it.sourceItem == itemId && it.stat == stat }
    val expires = elapsed + seconds
    buffs = if (existing >= 0) {
        buffs.mapIndexed { index, buff ->
            if (index == existing) buff.copy(stacks = min(maxStacks, buff.stacks + 1), expiresAt = expires, amountPerStack = amount) else buff
        }
    } else {
        val live = buffs.filter { it.expiresAt > elapsed }
        if (live.size >= MutableGameState.MAX_BUFFS) live else live + ActiveBuff(itemId, stat, amount, 1, expires)
    }
}

internal fun MutableGameState.expireBuffs() {
    if (buffs.isEmpty()) return
    var anyExpired = false
    for (index in buffs.indices) if (buffs[index].expiresAt <= elapsed) anyExpired = true
    if (anyExpired) buffs = buffs.filter { it.expiresAt > elapsed }
}

/** A refund returns at most 40 % of the remaining cooldown; continuous weapons get a short surge instead. */
private fun MutableGameState.refreshWeapon(seconds: Float, itemId: Int) {
    if (weapon.isContinuous()) {
        addBuff(itemId, kinetickk.ball.content.api.BuffStat.WEAPON, 0.4f, max(0.18f, seconds), 1)
        return
    }
    val refund = min(seconds, max(0f, weaponClock) * 0.4f)
    weaponClock -= refund
    weaponSecondaryClock -= min(seconds, max(0f, weaponSecondaryClock) * 0.4f)
}

internal fun WeaponId.isContinuous(): Boolean =
    this == WeaponId.FLUX_WAKE || this == WeaponId.PHASE_LATTICE || this == WeaponId.ENTROPY_FIELD

// ---------------------------------------------------------------------------------------------
// Hooks called by the simulation. Each derives its edge inside the fixed step.

/** After a direct hit landed: clauses, statuses from resonance, Ion arcs and relic rules. */
internal fun MutableGameState.onDirectHit(enemy: Enemy, result: DamageResult, source: HitSource, sourceWeapon: WeaponId?) {
    enemy.status[StatusCell.LAST_SOURCE] = (source.ordinal + 1).toFloat()
    // Derived damage measures the hit before the receiver's own multipliers, so armour never leaks to others,
    // and before the Matrix damage multiplier, which every proc, status and field applies once itself.
    val basis = result.unmitigated / matrix.damageMultiplier
    when (source) {
        HitSource.IMPACT -> fireClauses(EffectTrigger.IMPACT, enemy, basis, source)
        HitSource.WEAPON -> {
            fireClauses(EffectTrigger.WEAPON_HIT, enemy, basis, source)
            if (tagTier(BuildTag.ARSENAL) >= 3) {
                // A deterministic counter, not a roll: no draw from the crit and proc stream.
                arsenalCounter = (arsenalCounter + 1) % ARSENAL_INFUSION_EVERY
                if (arsenalCounter == 0) {
                    val infusion = arsenalInfusion()
                    if (infusion != null) applyStatus(enemy, infusion, 1, 0) else dealProcDamage(enemy, basis * 0.2f, rescale = false)
                }
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
        HitSource.DASH, HitSource.PROC, HitSource.STATUS -> Unit
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
    relicOnDirectHit(enemy, result, source)
}

/** ARSENAL III adds one stack on every this-many weapon hit (one in four). */
internal const val ARSENAL_INFUSION_EVERY: Int = 4

/** ARSENAL III: every Break deals this share more damage. */
internal const val ARSENAL_BREAK_BONUS: Float = 0.25f

/**
 * The element ARSENAL III adds: the weapon's own element when it has one, else the dominant element.
 * The shipped weapons deal elementless Kinetic damage, so today this is the dominant element.
 */
internal fun MutableGameState.arsenalInfusion(): StatusElement? = build.dominant

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

/** Every level hardens the Core a little, so survival no longer depends on stat-only items. */
internal const val LEVEL_INTEGRITY: Float = 2f
internal const val LEVEL_REPAIR: Float = 6f

internal fun MutableGameState.onLevelUp() {
    val ceiling = maxHp
    maxHp += LEVEL_INTEGRITY
    val gained = ceilingChangeAfterLiability(ceiling, LEVEL_INTEGRITY)
    if (build.liability(kinetickk.ball.content.api.RelicId.AEGIS_INVERSION)) hp = minOf(hp, maxHp) else hp += gained
    heal(LEVEL_REPAIR, HealSource.LEVEL)
    fireClauses(EffectTrigger.LEVEL_UP)
    if (tagTier(BuildTag.FORTUNE) >= 2 && level % 4 == 0) rerollsRemaining++
}

/** Ouroboros Seal's liability; applied to every Data gain, pickups and item clauses alike. */
internal fun MutableGameState.dataGainLiability(): Float =
    if (build.liability(kinetickk.ball.content.api.RelicId.OUROBOROS_SEAL)) 0.75f else 1f

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
