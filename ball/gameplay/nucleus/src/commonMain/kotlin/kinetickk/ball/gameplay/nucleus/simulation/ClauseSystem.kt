// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.ImpulseDirection
import kinetickk.ball.content.api.MarkPick
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.SkillPick
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.ZoneKind
import kinetickk.ball.content.api.WeaponCadence
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
 * broken), [hit] the triggering damage, [element] the element of a Break (or of the skill or field involved)
 * and [reaction] the reaction that fired, made the field or left the mark (a fired reaction's two elements
 * both count as involved); [skill] is the skill that cast or hit. Effects start at ([originX], [originY]),
 * or at the target (the Core without one) while those are NaN. Clause consequences are proc damage, so they
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
    skill: SkillId? = null,
    originX: Float = Float.NaN,
    originY: Float = Float.NaN,
    obstacle: kinetickk.ball.content.api.ObstacleKind? = null,
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
                if (!holds(clause.conditions[conditionIndex], target, source, element, reaction, skill, obstacle)) {
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
            val generation = if (trigger == EffectTrigger.BREAK || trigger == EffectTrigger.REACTION ||
                trigger == EffectTrigger.SHOT_ERASED
            ) 1 else 0
            runClause(itemId, clause, target, hit, generation, statusOnly, skill, originX, originY)
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
    skill: SkillId?,
    fromX: Float,
    fromY: Float,
) {
    val magnitude = build.magnitude(itemId)
    val bonus = build.bonus(itemId)
    // Damage scale per basis: %max-HP procs cap their stack growth and ignore Reborn and attunement.
    fun scale(basis: DamageBasis) = if (basis == DamageBasis.VICTIM_MAX_HP) min(MAX_HP_PROC_SCALE, magnitude) else magnitude * bonus
    val originX = if (fromX.isNaN()) target?.x ?: coreX else fromX
    val originY = if (fromY.isNaN()) target?.y ?: coreY else fromY
    // Marks share the item's status rate: open when the clause began, so a status and a mark in one clause both land.
    val statusRateOpen = itemStatusReadyAt[itemId] <= elapsed
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
            is EffectAction.Buff -> addBuff(BuffSource.item(itemId), action.stat, action.amount * magnitude * bonus, action.seconds, action.maxStacks)
            is EffectAction.ExtendCombo -> if (combo > 0) comboTime = min(effectiveComboWindow() * 2f, comboTime + action.seconds * magnitude)
            is EffectAction.RefreshWeapon -> refreshWeapon(action.seconds * magnitude, BuffSource.item(itemId))
            // A level-up's dividend joins the level loop that fired it rather than starting a second one inside it.
            is EffectAction.GainData ->
                if (clause.trigger == EffectTrigger.LEVEL_UP) creditData(action.amount * magnitude * bonus)
                else gainData(action.amount * magnitude * bonus)
            is EffectAction.Well -> {
                // Field ticks apply the relic surge live, so a FLAT well is measured without it.
                val measured = if (action.basis == DamageBasis.FLAT) runScale() else basisAmount(action.basis, hit, target)
                val dps = measured * action.scale * scale(action.basis)
                val element = action.pick?.let(::resolvePick)
                addField(
                    CombatFieldKind.DARK_STAR, FieldSource.ITEM, itemId, originX, originY, radius = action.radius,
                    seconds = action.seconds, damagePerSecond = dps, element = element,
                    stacksPerTick = if (element != null) 1 else 0, generation = 1, pull = 240f,
                )
            }
            is EffectAction.Detonate -> if (target != null && target.alive && !isShifting(target)) detonate(target, action, magnitude * bonus)
            is EffectAction.RefundSkills -> refundSkillsFor(action.pick, action.seconds * magnitude, skill, clause.trigger)
            is EffectAction.ChargeApex -> chargeApex(action.seconds * magnitude)
            is EffectAction.ForceReaction -> forceReactionsAround(target, originX, originY, action.radius, action.maxTargets)
            is EffectAction.Zone -> openZone(itemId, action, originX, originY, target)
            is EffectAction.EraseShots -> eraseShotsAround(originX, originY, action.radius, action.maximum)
            is EffectAction.CoreImpulse -> launchCore(action.direction, action.speed * magnitude, target)
            is EffectAction.ApplyMark -> if (statusRateOpen && target != null && target.alive && !isShifting(target)) {
                itemStatusReadyAt[itemId] = max(itemStatusReadyAt[itemId], elapsed + 1f / (ITEM_STATUS_RATE * max(1f, magnitude)))
                applyItemMark(target, action.mark, action.seconds)
            }
            is EffectAction.Throw -> if (target != null && target.alive && !isShifting(target)) {
                hurl(coreX, coreY, target, action.speed * magnitude)
            }
            EffectAction.PrimeDash -> primedDashUntil = max(primedDashUntil, elapsed + PRIME_DASH_SECONDS)
        }
    }
}

/** PrimeDash: the next Dash within this long costs no heat. */
internal const val PRIME_DASH_SECONDS: Float = 3f

/** ChargeApex before the maximum level refunds this share of its seconds to every skill. */
internal const val CHARGE_APEX_SKILL_SHARE: Float = 0.25f

/**
 * RefundSkills: [seconds] through the refund bank to [pick]'s skills. A SKILL_CAST clause never refunds the slot
 * that just cast; CAST falls back to the slowest skill when no skill dispatched the clause.
 */
private fun MutableGameState.refundSkillsFor(pick: SkillPick, seconds: Float, skill: SkillId?, trigger: EffectTrigger) {
    if (skillLoadout.ownedCount == 0) return
    val dispatching = skill?.let(skillLoadout::slotOf) ?: -1
    val excluded = if (trigger == EffectTrigger.SKILL_CAST) dispatching else -1
    when (pick) {
        SkillPick.ALL -> for (slot in 0 until SkillRuntime.SLOTS) if (slot != excluded) refundSkill(slot, seconds)
        SkillPick.CAST ->
            if (dispatching >= 0 && dispatching != excluded) refundSkill(dispatching, seconds)
            else slowestSkillSlot(excluded).let { if (it >= 0) refundSkill(it, seconds) }
        SkillPick.LONGEST -> slowestSkillSlot(excluded).let { if (it >= 0) refundSkill(it, seconds) }
    }
}

/** The recharging slot with the most cooldown left (slot order on ties), other than [excluded]; -1 when none. */
internal fun MutableGameState.slowestSkillSlot(excluded: Int = -1): Int {
    var best = -1
    var bestLeft = 0f
    for (slot in 0 until SkillRuntime.SLOTS) {
        if (slot == excluded || skillLoadout.isEmpty(slot)) continue
        val left = skillRuntime[slot, SkillCell.READY_AT] - elapsed
        if (left > bestLeft) {
            best = slot
            bestLeft = left
        }
    }
    return best
}

/** ChargeApex: once the Apex is unlocked its cooldown shrinks by [seconds]; before that every skill by a quarter of it. */
internal fun MutableGameState.chargeApex(seconds: Float) {
    if (apexUnlocked) refundApex(seconds) else refundAllSkills(seconds * CHARGE_APEX_SKILL_SHARE)
}

/**
 * ForceReaction: sets off the reaction on [target], then on the enemies nearest ([x], [y]) within [radius] until
 * [maxTargets] were tried; each is a cascade (generation 1, so it spends a reaction token). Allocation-free.
 */
private fun MutableGameState.forceReactionsAround(target: Enemy?, x: Float, y: Float, radius: Float, maxTargets: Int) {
    var tried = 0
    var firstId = -1
    var secondId = -1
    var thirdId = -1
    if (target != null && target.alive) {
        forceReaction(target, 1)
        firstId = target.id
        tried = 1
    }
    while (tried < maxTargets) {
        var best: Enemy? = null
        var bestDistance = Float.POSITIVE_INFINITY
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive || isShifting(enemy) || enemy.id == firstId || enemy.id == secondId || enemy.id == thirdId) continue
            val distance = distanceSquared(x, y, enemy.x, enemy.y)
            if (distance > square(radius + enemy.radius) || distance >= bestDistance) continue
            best = enemy
            bestDistance = distance
        }
        val next = best ?: break
        forceReaction(next, 1)
        when (tried) {
            0 -> firstId = next.id
            1 -> secondId = next.id
            else -> thirdId = next.id
        }
        tried++
    }
}

/**
 * Zone: an item field of the zone's kind with no damage of its own at ([x], [y]); an attractor rides [target]
 * when there is one. Its behaviour (pool, hush, bubble, pull, maw) is its reaction's verb, read from content.
 */
private fun MutableGameState.openZone(itemId: Int, zone: EffectAction.Zone, x: Float, y: Float, target: Enemy?) {
    val anchor = if (zone.kind == ZoneKind.ATTRACTOR && target != null && target.alive) target else null
    val kind = when (zone.kind) {
        ZoneKind.PLASMA -> CombatFieldKind.PLASMA
        ZoneKind.HUSH -> CombatFieldKind.HUSH
        ZoneKind.TIME_BUBBLE -> CombatFieldKind.TIME_BUBBLE
        ZoneKind.ATTRACTOR -> CombatFieldKind.ATTRACTOR
        ZoneKind.MAW -> CombatFieldKind.NULL_WELL
    }
    val pull = when (zone.kind) {
        ZoneKind.ATTRACTOR -> if (hasVerb(ReactionId.MAGNETIZE)) reactionValue(ReactionId.MAGNETIZE, ATTRACTOR_PULL_VALUE) else 0f
        ZoneKind.MAW -> NULL_WELL_PULL
        else -> 0f
    }
    addField(
        kind, FieldSource.ITEM, itemId, anchor?.x ?: x, anchor?.y ?: y, radius = zone.radius, seconds = zone.seconds,
        damagePerSecond = 0f, generation = 1, pull = pull, anchorId = anchor?.id ?: -1,
        flags = if (anchor != null) FieldFlags.FOLLOWS_ANCHOR else 0,
    )
}

/**
 * EraseShots: erases up to [maximum] live hostile bolts nearest ([x], [y]) within [radius], nearest first (an item
 * erasure). Allocation-free: one scan per bolt.
 */
private fun MutableGameState.eraseShotsAround(x: Float, y: Float, radius: Float, maximum: Int) {
    var erased = 0
    while (erased < maximum) {
        var best: Projectile? = null
        var bestDistance = radius * radius
        for (index in projectiles.indices) {
            val shot = projectiles[index]
            if (!shot.hostile || shot.spent || shot.kind != kinetickk.ball.gameplay.nucleus.render.ProjectileKind.BOLT) continue
            val distance = distanceSquared(x, y, shot.x, shot.y)
            if (distance > bestDistance) continue
            if (best == null || distance < bestDistance) {
                best = shot
                bestDistance = distance
            }
        }
        val chosen = best ?: break
        if (eraseHostileShot(chosen, EraseCause.ITEM, chosen.x, chosen.y)) erased++ else break
    }
}

/** CoreImpulse: a gated launch of the Core at [speed] toward the pointer, away from [target] or along its motion. */
private fun MutableGameState.launchCore(direction: ImpulseDirection, speed: Float, target: Enemy?) {
    var dirX = lastAimDirectionX
    var dirY = lastAimDirectionY
    when (direction) {
        ImpulseDirection.POINTER -> {
            val dx = cameraX + pointerX - screenWidth * 0.5f - coreX
            val dy = cameraY + pointerY - screenHeight * 0.5f - coreY
            val distance = length(dx, dy)
            if (distance > AIM_DEADZONE) {
                dirX = dx / distance
                dirY = dy / distance
            }
        }
        ImpulseDirection.AWAY_FROM_TARGET -> if (target != null) {
            val dx = coreX - target.x
            val dy = coreY - target.y
            val distance = length(dx, dy)
            if (distance >= 1f) {
                dirX = dx / distance
                dirY = dy / distance
            }
        }
        ImpulseDirection.FORWARD -> if (this.speed > 1f) {
            dirX = velocityX / this.speed
            dirY = velocityY / this.speed
        }
    }
    applyCoreImpulse(dirX, dirY, speed, gated = true)
}

/** ApplyMark: writes [mark] on [target] for [seconds]; Embrittled is the Embrittle reaction's state. */
private fun MutableGameState.applyItemMark(target: Enemy, mark: MarkPick, seconds: Float) {
    when (mark) {
        MarkPick.SPALL -> setMark(target, MarkKind.SPALL, seconds)
        MarkPick.CRACKED -> setMark(target, MarkKind.CRACKED, seconds)
        MarkPick.SPORED -> setMark(target, MarkKind.SPORED, seconds)
        MarkPick.EMBRITTLED ->
            target.status[StatusCell.EMBRITTLED_UNTIL] = max(target.status[StatusCell.EMBRITTLED_UNTIL], elapsed + seconds)
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

internal fun MutableGameState.fireShards(
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
        if (count <= 0 || decayPerStack(listed) == 0f) continue
        total += detonateDecayAmount(target, listed)
        target.status[StatusCell.STACKS + listed.ordinal] = 0f
        if (element == null || count * 2 >= breakThreshold(listed, target)) element = listed
    }
    if (total <= 0f) return
    target.status[StatusCell.DETONATE_READY_AT] = elapsed + 2f
    dealStatusDamage(target, total * action.scale * scale, element)
    shockwave(target.x, target.y, 0.3f, target.radius * 2.6f, 4)
}

/**
 * The damage [target]'s [element] stacks still hold as they decay one at a time: perStack × n(n+1)/2 × decay
 * seconds (Thermal and Entropy decay into damage; other elements hold none). Reads, never consumes.
 */
internal fun MutableGameState.detonateDecayAmount(target: Enemy, element: StatusElement): Float {
    val count = stacks(target, element)
    val perStack = decayPerStack(element)
    if (count <= 0 || perStack == 0f) return 0f
    return perStack * build.potency(element) * runScale() * count * (count + 1) * 0.5f * decaySeconds(element)
}

private fun MutableGameState.decayPerStack(element: StatusElement): Float = when (element) {
    StatusElement.THERMAL -> content.build.element(StatusElement.THERMAL).perStack
    StatusElement.ENTROPY -> ENTROPY_DAMAGE_PER_STACK
    else -> 0f
}

/** Grants or restacks the [sourceKey]'s ([BuffSource]) [stat] buff; at most [MutableGameState.MAX_BUFFS] live. */
internal fun MutableGameState.addBuff(sourceKey: Int, stat: kinetickk.ball.content.api.BuffStat, amount: Float, seconds: Float, maxStacks: Int) {
    val existing = buffs.indexOfFirst { it.sourceKey == sourceKey && it.stat == stat }
    val expires = elapsed + seconds
    buffs = if (existing >= 0) {
        buffs.mapIndexed { index, buff ->
            if (index == existing) buff.copy(stacks = min(maxStacks, buff.stacks + 1), expiresAt = expires, amountPerStack = amount) else buff
        }
    } else {
        val live = buffs.filter { it.expiresAt > elapsed }
        if (live.size >= MutableGameState.MAX_BUFFS) live else live + ActiveBuff(sourceKey, stat, amount, 1, expires)
    }
}

internal fun MutableGameState.expireBuffs() {
    if (buffs.isEmpty()) return
    var anyExpired = false
    for (index in buffs.indices) if (buffs[index].expiresAt <= elapsed) anyExpired = true
    if (anyExpired) buffs = buffs.filter { it.expiresAt > elapsed }
}

/**
 * A refund returns at most 40 % of each remaining cooldown: the weapon clocks and the weapon's own timers (Scatter
 * Bloom's volley, Glacier Bell's toll and pulse, Hollow Rift's passive rift, Concussion Engine's shockwave).
 * Continuous and contact weapons have no cooldown to advance: they get a short surge instead.
 */
internal fun MutableGameState.refreshWeapon(seconds: Float, sourceKey: Int) {
    if (weapon.isContinuous() || currentWeaponDefinition.cadence == WeaponCadence.CONTACT) {
        addBuff(sourceKey, kinetickk.ball.content.api.BuffStat.WEAPON, 0.4f, max(0.18f, seconds), 1)
        return
    }
    weaponClock -= cooldownRefund(seconds, weaponClock)
    weaponSecondaryClock -= cooldownRefund(seconds, weaponSecondaryClock)
    val runtime = weaponRuntime
    runtime.passiveClock -= cooldownRefund(seconds, runtime.passiveClock)
    runtime.readyAt -= cooldownRefund(seconds, runtime.readyAt - elapsed)
}

private fun cooldownRefund(seconds: Float, remaining: Float): Float = min(seconds, max(0f, remaining) * 0.4f)

/** True for the CONTINUOUS cadence of the weapon roster (damage per second instead of activations). */
internal fun WeaponId.isContinuous(): Boolean = when (this) {
    WeaponId.FLUX_WAKE, WeaponId.PHASE_LATTICE, WeaponId.ENTROPY_FIELD, WeaponId.TETHER_ARC, WeaponId.SOLAR_SWEEP -> true
    WeaponId.MORNINGSTAR, WeaponId.NULL_LANCE, WeaponId.GRAVITY_MINES, WeaponId.ION_SWARM, WeaponId.RIFT_BLADES,
    WeaponId.ARC_COIL, WeaponId.QUASAR_CANNON, WeaponId.SINGULARITY_SPEAR, WeaponId.PRISM_RELAY, WeaponId.GYRE_DISC,
    WeaponId.SCATTER_BLOOM, WeaponId.SENTRY_GRID, WeaponId.HUNTER_SALVO, WeaponId.CONCUSSION_ENGINE,
    WeaponId.GLACIER_BELL, WeaponId.EVENT_LENS, WeaponId.AEGIS_SHARDS, WeaponId.HOLLOW_RIFT, WeaponId.RESONANCE_DRUM,
    -> false
}

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
