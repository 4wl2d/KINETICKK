// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.SkillDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.SkillTargeting
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.SkillCueCodes
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Active skills in the Z, X and C slots. The slots fill from in-run drafts ([SkillLoadout]); their timing and
 * per-cast state live in [SkillRuntime]. Every cast and every refund goes through this file: a cast spends its
 * slot's cooldown, and every cooldown loop (Catalysis, character refunds, reaction verbs) draws on one refund
 * bank, so no combination more than doubles a skill's cast rate and no skill recasts sooner than
 * [MIN_SKILL_RECAST_SECONDS]. No gameplay or spawn randomness is drawn here.
 */

// ---------------------------------------------------------------------------------------------
// Step.

/**
 * Casts the buffered slots that can cast now (Z, X, C order), then resolves pending effects: Meteor landings,
 * Void Step rifts, Null Shroud's swallow and release, Arc Hook's kill window and the Spiral ghost's repeats.
 * Runs after the heat update and before the Core moves, so movement skills act in the same step. Allocation-free.
 */
internal fun MutableGameState.updateSkills(delta: Float) {
    val loadout = skillLoadout
    if (loadout.ownedCount == 0) {
        skillBufferMask = 0
        skillBufferTime = 0f
        return
    }
    if (skillBufferMask != 0) {
        skillBufferTime = max(0f, skillBufferTime - delta)
        if (skillBufferTime <= 0f) {
            skillBufferMask = 0
        } else {
            for (slot in 0 until SkillRuntime.SLOTS) {
                val bit = 1 shl slot
                if (skillBufferMask and bit == 0) continue
                if (castSkill(slot)) skillBufferMask = skillBufferMask and bit.inv()
                if (phase != kinetickk.ball.gameplay.nucleus.render.GamePhase.RUNNING) return
            }
            if (skillBufferMask == 0) skillBufferTime = 0f
        }
    }
    for (slot in 0 until SkillRuntime.SLOTS) {
        val id = loadout.skill(slot) ?: continue
        updatePendingSkillEffect(slot, id)
        if (phase != kinetickk.ball.gameplay.nucleus.render.GamePhase.RUNNING) return
    }
}

private fun MutableGameState.updatePendingSkillEffect(slot: Int, id: SkillId) {
    val runtime = skillRuntime
    val pendingAt = runtime[slot, SkillCell.PENDING_AT]
    when (id) {
        SkillId.METEOR -> if (pendingAt >= 0f && elapsed >= pendingAt) {
            runtime[slot, SkillCell.PENDING_AT] = -1f
            withSkillEffect(slot) { meteorLanding(slot) }
        }
        SkillId.VOID_STEP -> if (pendingAt >= 0f && elapsed >= pendingAt) {
            runtime[slot, SkillCell.PENDING_AT] = -1f
            withSkillEffect(slot) { voidRift(slot) }
        }
        SkillId.NULL_SHROUD -> if (pendingAt >= 0f) {
            if (elapsed < runtime[slot, SkillCell.ACTIVE_UNTIL]) {
                swallowShots(slot)
            } else {
                runtime[slot, SkillCell.PENDING_AT] = -1f
                releaseShroud(slot)
            }
        }
        SkillId.ARC_HOOK -> {
            val window = runtime[slot, SkillCell.AUX_UNTIL]
            if (window > 0f && (elapsed >= window || enemyById(runtime[slot, SkillCell.TARGET_ID].toInt())?.alive != true)) {
                // Rank III: a hooked target that dies within the window makes the hook ready 2.5 s after the cast.
                if (elapsed < window) {
                    val ready = runtime[slot, SkillCell.CAST_AT] + MIN_SKILL_RECAST_SECONDS
                    if (runtime[slot, SkillCell.READY_AT] > ready) runtime[slot, SkillCell.READY_AT] = ready
                }
                runtime[slot, SkillCell.AUX_UNTIL] = 0f
            }
        }
        else -> Unit
    }
    val ghostAt = runtime[slot, SkillCell.GHOST_AT]
    if (ghostAt >= 0f && elapsed >= ghostAt) {
        runtime[slot, SkillCell.GHOST_AT] = -1f
        withSkillEffect(slot) { ghostRepeat(slot, id) }
    }
}

// ---------------------------------------------------------------------------------------------
// Casting.

/**
 * Casts [slot] when it can: a filled slot whose cooldown ran out and, for a NEAREST skill, a target. A refused
 * cast spends nothing and leaves its request buffered; the slot shows why (cooling or no target). On a cast the
 * effect resolves, the cooldown starts and the character, cue and sound follow. True when the slot cast.
 */
internal fun MutableGameState.castSkill(slot: Int): Boolean {
    val id = skillLoadout.skill(slot) ?: return false
    val runtime = skillRuntime
    if (elapsed < runtime[slot, SkillCell.READY_AT]) {
        refuseSkill(slot, SkillSlotProjection.REFUSAL_COOLING)
        return false
    }
    val definition = content.skill(id)
    val rank = skillLoadout.rank(slot)
    val values = definition.rank(rank).values
    val pointerX = cameraX + pointerX - screenWidth * 0.5f
    val pointerY = cameraY + pointerY - screenHeight * 0.5f
    val toPointer = length(pointerX - coreX, pointerY - coreY)
    val aimX = if (toPointer > AIM_DEADZONE) (pointerX - coreX) / toPointer else lastAimDirectionX
    val aimY = if (toPointer > AIM_DEADZONE) (pointerY - coreY) / toPointer else lastAimDirectionY
    var anchorX = coreX
    var anchorY = coreY
    var target: Enemy? = null
    when (definition.targeting) {
        SkillTargeting.SELF, SkillTargeting.AIM -> Unit
        SkillTargeting.AREA -> if (toPointer > AIM_DEADZONE) {
            val reach = min(toPointer, definition.reach)
            anchorX = coreX + aimX * reach
            anchorY = coreY + aimY * reach
        }
        SkillTargeting.NEAREST -> {
            target = nearestSkillTarget(pointerX, pointerY, values[ARC_HOOK_REACH])
            if (target == null) {
                refuseSkill(slot, SkillSlotProjection.REFUSAL_NO_TARGET)
                return false
            }
            anchorX = target.x
            anchorY = target.y
        }
    }
    // Measured before the cast: a Prism with every facet up casts at a shorter cooldown.
    val prismDiscount = coreShape == CoreShape.PRISM && characterRuntime.facets >= CharacterRuntime.MAX_FACETS
    var radius = 0f
    val primaryId = withSkillEffect(slot) {
        radius = resolveSkillCast(slot, id, rank, values, aimX, aimY, anchorX, anchorY, target)
    }
    val primary = enemyById(primaryId) ?: target
    var cooldown = definition.rank(rank).cooldownSeconds * max(MIN_SKILL_COOLDOWN_SCALE, build.skillCooldownScale)
    if (prismDiscount) cooldown *= PRISM_SKILL_COOLDOWN
    cooldown = max(MIN_SKILL_RECAST_SECONDS, cooldown)
    runtime[slot, SkillCell.LAST_COOLDOWN] = cooldown
    runtime[slot, SkillCell.READY_AT] = elapsed + cooldown
    runtime[slot, SkillCell.REFUNDED] = 0f
    runtime[slot, SkillCell.CAST_AT] = elapsed
    runtime[slot, SkillCell.CAST_COUNT] = runtime[slot, SkillCell.CAST_COUNT] + 1f
    runtime[slot, SkillCell.REFUSAL] = SkillSlotProjection.REFUSAL_NONE.toFloat()
    onCharacterSkillCast(slot, anchorX, anchorY, primary)
    emitVisualFx(VisualFxCue.SkillCast(id, anchorX, anchorY, aimX, aimY, radius, rank))
    emitSound(GameplayAudioCue.SKILL)
    return true
}

/** Shows why a buffered cast waits, once per buffered request. */
private fun MutableGameState.refuseSkill(slot: Int, refusal: Int) {
    val runtime = skillRuntime
    if (runtime[slot, SkillCell.REFUSAL].toInt() == refusal &&
        runtime[slot, SkillCell.REFUSED_AT] > elapsed - MutableGameState.SKILL_INPUT_BUFFER_SECONDS
    ) return
    runtime[slot, SkillCell.REFUSAL] = refusal.toFloat()
    runtime[slot, SkillCell.REFUSED_AT] = elapsed
}

/**
 * Runs [effect] as part of [slot]'s cast: damage it deals counts once per boss-class enemy, the first
 * [MAX_SKILL_HITS_PER_CAST] enemies it damages are its skill hits, and the first enemy it touches is its
 * primary target, whose id it returns (-1 for none). The cast scratch is idle again afterwards.
 */
private inline fun MutableGameState.withSkillEffect(slot: Int, effect: () -> Unit): Int {
    castSlot = slot
    castHits = 0
    castPrimaryId = -1
    castBossHitFirst = -1
    castBossHitSecond = -1
    try {
        effect()
        return castPrimaryId
    } finally {
        castSlot = -1
        castHits = 0
        castPrimaryId = -1
        castBossHitFirst = -1
        castBossHitSecond = -1
    }
}

/**
 * The enemy nearest the pointer at ([pointerX], [pointerY]) among live, non-shifting enemies within [reach] of the
 * Core and at least [HOOK_SINGULARITY_GAP] from the singularity; ties keep scan order.
 */
internal fun MutableGameState.nearestSkillTarget(pointerX: Float, pointerY: Float, reach: Float): Enemy? {
    var best: Enemy? = null
    var bestDistance = Float.POSITIVE_INFINITY
    val reachSquared = reach * reach
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy)) continue
        if (distanceSquared(coreX, coreY, enemy.x, enemy.y) > reachSquared) continue
        val fromPointer = distanceSquared(pointerX, pointerY, enemy.x, enemy.y)
        if (fromPointer < square(HOOK_SINGULARITY_GAP)) continue
        if (fromPointer < bestDistance) {
            best = enemy
            bestDistance = fromPointer
        }
    }
    return best
}

/** Skill power: the run scale times the build's skill bonus. Every non-kinetic skill number scales with it. */
internal fun MutableGameState.skillPower(): Float = runScale() * (1f + build.skillPower)

/** Kinetic skills scale off impact power, which already carries the run growth: only the build bonus applies. */
private fun MutableGameState.kineticSkillScale(): Float = 1f + build.skillPower

/** The Ring's Halo widens its own self-centred skills (×1 at the smallest Halo, at most ×1.5). */
private fun MutableGameState.selfSkillRadiusScale(): Float =
    if (coreShape == CoreShape.RING) (haloRadius / RING_SKILL_HALO).coerceIn(1f, RING_SKILL_RADIUS_CAP) else 1f

/**
 * Skill damage of the running cast on [enemy]: through [dealSkillDamage], once per boss-class enemy per cast.
 * The first [MAX_SKILL_HITS_PER_CAST] enemies it damages count as the cast's skill hits.
 */
private fun MutableGameState.skillHit(enemy: Enemy, amount: Float, element: StatusElement?, kinetic: Boolean = false): Boolean {
    if (!enemy.alive || amount <= 0f) return false
    if (enemy.isBossClass) {
        if (castBossHitFirst == enemy.id || castBossHitSecond == enemy.id) return false
        if (castBossHitFirst < 0) castBossHitFirst = enemy.id else castBossHitSecond = enemy.id
    }
    val integrity = enemy.hp
    val result = dealSkillDamage(enemy, amount, element, kinetic)
    if (result.amount <= 0f) return false
    recordSkillDamage(castSlot, min(result.amount, integrity.coerceAtLeast(0f)))
    noteCastTarget(enemy)
    if (castHits < MAX_SKILL_HITS_PER_CAST) {
        castHits++
        onSkillHit(enemy)
    }
    return true
}

/** Skill field damage of [slot] (a tick, a zap, a returned shot): one capped instance, no skill hit. */
private fun MutableGameState.skillFieldHit(slot: Int, enemy: Enemy, amount: Float, element: StatusElement?) {
    if (!enemy.alive || amount <= 0f) return
    val integrity = enemy.hp
    val result = dealSkillDamage(enemy, amount, element)
    if (result.amount > 0f) recordSkillDamage(slot, min(result.amount, integrity.coerceAtLeast(0f)))
}

/** Counts [dealt] skill damage for the run and for [slot] (when known). */
private fun MutableGameState.recordSkillDamage(slot: Int, dealt: Float) {
    val runtime = skillRuntime
    runtime.skillDamage += dealt.toDouble()
    if (slot >= 0) runtime[slot, SkillCell.DAMAGE] = runtime[slot, SkillCell.DAMAGE] + dealt
}

private fun MutableGameState.noteCastTarget(enemy: Enemy) {
    if (castSlot >= 0 && castPrimaryId < 0) castPrimaryId = enemy.id
}

/** A skill hit on [enemy]: the Triangle marks it for Puncture. */
private fun MutableGameState.onSkillHit(enemy: Enemy) {
    if (enemy.alive) markForPuncture(enemy)
}

// ---------------------------------------------------------------------------------------------
// Effects. Values follow the per-skill order of `defaultSkillDefinitions`.

/** Resolves [id]'s effect for [slot] at [rank]; returns the radius its cue shows. */
private fun MutableGameState.resolveSkillCast(
    slot: Int,
    id: SkillId,
    rank: Int,
    values: List<Float>,
    aimX: Float,
    aimY: Float,
    anchorX: Float,
    anchorY: Float,
    target: Enemy?,
): Float {
    val runtime = skillRuntime
    val rankThree = rank >= ContentBounds.MAX_SKILL_RANK
    return when (id) {
        SkillId.SOLAR_FLARE -> {
            val vented = min(heat, values[SOLAR_VENT_CAP])
            heat -= vented
            val radius = values[SOLAR_RADIUS] * selfSkillRadiusScale()
            val damage = (values[SOLAR_DAMAGE] + values[SOLAR_DAMAGE_PER_HEAT] * vented) * skillPower()
            val scorch = min(SOLAR_SCORCH_CAP, values[SOLAR_SCORCH].toInt() + floor(vented / SOLAR_HEAT_PER_SCORCH).toInt())
            forEachEnemyWithin(coreX, coreY, radius) { enemy ->
                skillHit(enemy, damage, StatusElement.THERMAL)
                if (enemy.alive) applyStatus(enemy, StatusElement.THERMAL, scorch, 0)
                if (rankThree && enemy.alive &&
                    stacks(enemy, StatusElement.THERMAL) >= breakThreshold(StatusElement.THERMAL, enemy) - SKILL_BREAK_MARGIN
                ) forceElementBreak(enemy, StatusElement.THERMAL)
            }
            shockwave(coreX, coreY, 0.35f, radius, FxColorCode.elementTone(StatusElement.THERMAL))
            prepareGhost(slot, damage, radius)
            radius
        }
        SkillId.METEOR -> {
            runtime[slot, SkillCell.ANCHOR_X] = anchorX
            runtime[slot, SkillCell.ANCHOR_Y] = anchorY
            runtime[slot, SkillCell.PENDING_AT] = elapsed + values[METEOR_DELAY]
            values[METEOR_RADIUS]
        }
        SkillId.FROST_LINE -> {
            val half = values[FROST_LINE_LENGTH] * 0.5f
            val centreX = coreX + aimX * FROST_LINE_AHEAD
            val centreY = coreY + aimY * FROST_LINE_AHEAD
            // Perpendicular to the aim.
            addField(
                CombatFieldKind.FROST_LINE, FieldSource.SKILL, slot, centreX - aimY * half, centreY + aimX * half,
                radius = values[FROST_LINE_HALF_WIDTH], seconds = values[FROST_LINE_SECONDS], damagePerSecond = 0f,
                element = StatusElement.CRYO, stacksPerTick = values[FROST_LINE_CHILL].toInt(), generation = 0,
                x2 = centreX + aimY * half, y2 = centreY - aimX * half,
                flags = if (rankThree) FieldFlags.RANK_III else 0, power = values[FROST_LINE_BOLTS],
            )
            half
        }
        SkillId.FROST_SNAP -> {
            val radius = values[FROST_SNAP_RADIUS] * selfSkillRadiusScale()
            val shatter = impactPower(max(speed, FROST_SNAP_MIN_SPEED)) * values[FROST_SNAP_SHATTER] * kineticSkillScale()
            forEachEnemyWithin(coreX, coreY, radius) { enemy ->
                val brittle = enemy.status[StatusCell.BRITTLE] > 0f
                if (isFrozen(enemy) || brittle) {
                    if (brittle) enemy.status[StatusCell.BRITTLE] = 0f
                    skillHit(enemy, shatter, null, kinetic = true)
                }
                if (enemy.alive) applyStatus(enemy, StatusElement.CRYO, values[FROST_SNAP_CHILL].toInt(), 0)
                if (rankThree && enemy.alive &&
                    stacks(enemy, StatusElement.CRYO) >= breakThreshold(StatusElement.CRYO, enemy) - SKILL_BREAK_MARGIN
                ) forceElementBreak(enemy, StatusElement.CRYO)
            }
            runtime[slot, SkillCell.AUX_UNTIL] = elapsed + values[FROST_SNAP_COILED_SECONDS]
            shockwave(coreX, coreY, 0.3f, radius, FxColorCode.elementTone(StatusElement.CRYO))
            prepareGhost(slot, shatter, radius)
            radius
        }
        SkillId.ARC_HOOK -> {
            val hooked = target ?: return 0f
            applyStatus(hooked, StatusElement.ION, values[ARC_HOOK_CHARGE].toInt(), 0)
            noteCastTarget(hooked)
            val dx = hooked.x - coreX
            val dy = hooked.y - coreY
            val distance = max(1f, length(dx, dy))
            val dirX = dx / distance
            val dirY = dy / distance
            val along = velocityX * dirX + velocityY * dirY
            val hookSpeed = max(values[ARC_HOOK_SPEED], along)
            velocityX = dirX * hookSpeed
            velocityY = dirY * hookSpeed
            runtime[slot, SkillCell.TARGET_ID] = hooked.id.toFloat()
            runtime[slot, SkillCell.TARGET_UNTIL] = elapsed + values[ARC_HOOK_WINDOW]
            runtime[slot, SkillCell.AUX_UNTIL] = if (rankThree) elapsed + ARC_HOOK_KILL_WINDOW else 0f
            addRelicArc(coreX, coreY, hooked.x, hooked.y, LinkStyleCode.skill(SkillId.ARC_HOOK))
            distance
        }
        SkillId.STORM_COIL -> {
            addField(
                CombatFieldKind.STORM_COIL, FieldSource.SKILL, slot, anchorX, anchorY, radius = values[STORM_COIL_RADIUS],
                seconds = values[STORM_COIL_SECONDS], damagePerSecond = 0f, element = StatusElement.ION,
                stacksPerTick = values[STORM_COIL_TARGETS].toInt(), generation = 0,
                flags = if (rankThree) FieldFlags.RANK_III else 0, power = values[STORM_COIL_DAMAGE] * skillPower(),
            )
            values[STORM_COIL_RADIUS]
        }
        SkillId.GRAVITY_WELL -> {
            addField(
                CombatFieldKind.GRAVITY_WELL, FieldSource.SKILL, slot, anchorX, anchorY, radius = values[GRAVITY_WELL_RADIUS],
                seconds = values[GRAVITY_WELL_SECONDS], damagePerSecond = values[GRAVITY_WELL_DPS] * skillPower(),
                element = StatusElement.GRAVITIC, stacksPerTick = 1, generation = 0, pull = values[GRAVITY_WELL_PULL],
                flags = if (rankThree) FieldFlags.RANK_III else 0, power = values[GRAVITY_WELL_DATA_RANGE],
            )
            values[GRAVITY_WELL_RADIUS]
        }
        SkillId.MASS_DRIVER -> {
            val seconds = values[MASS_DRIVER_SECONDS]
            runtime[slot, SkillCell.ACTIVE_UNTIL] = elapsed + seconds
            addBuff(BuffSource.skill(id), kinetickk.ball.content.api.BuffStat.DAMAGE_TAKEN, -values[MASS_DRIVER_GUARD], seconds, 1)
            shockwave(coreX, coreY, 0.3f, coreRadius * 4f, FxColorCode.elementTone(StatusElement.GRAVITIC))
            0f
        }
        SkillId.DECAY_SPORES -> {
            val radius = values[SPORES_RADIUS]
            runtime[slot, SkillCell.STORED] = 0f
            runtime[slot, SkillCell.AUX_COUNT] = 0f
            forEachEnemyWithin(anchorX, anchorY, radius) { enemy ->
                applyStatus(enemy, StatusElement.ENTROPY, values[SPORES_DECAY].toInt(), 0)
                if (enemy.alive) {
                    setMark(enemy, MarkKind.SPORED, values[SPORES_SECONDS])
                    noteCastTarget(enemy)
                }
            }
            shockwave(anchorX, anchorY, 0.35f, radius, FxColorCode.elementTone(StatusElement.ENTROPY))
            radius
        }
        SkillId.ENTROPIC_TIDE -> {
            val radius = values[TIDE_RADIUS] * selfSkillRadiusScale()
            val scale = values[TIDE_SCALE] * kineticSkillScale()
            var consumed = 0
            forEachEnemyWithin(coreX, coreY, radius) { enemy ->
                val decay = stacks(enemy, StatusElement.ENTROPY)
                if (decay <= 0) return@forEachEnemyWithin
                val amount = detonateDecayAmount(enemy, StatusElement.ENTROPY) * scale
                enemy.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 0f
                consumed += decay
                skillHit(enemy, amount, StatusElement.ENTROPY)
            }
            if (consumed > 0) {
                val mended = min(consumed, values[TIDE_HEAL_CAP].toInt()) * values[TIDE_HEAL_PER_STACK]
                val missing = maxHp - hp
                heal(mended, HealSource.SKILL)
                // Rank III: what the Core could not take mends the shield.
                if (rankThree && mended > missing) addShield(mended - max(0f, missing))
                heat = max(0f, heat - consumed * TIDE_HEAT_PER_STACK)
            }
            shockwave(coreX, coreY, 0.4f, radius, FxColorCode.elementTone(StatusElement.ENTROPY))
            prepareGhost(slot, scale, radius)
            radius
        }
        SkillId.SONIC_BOOM -> {
            val range = values[BOOM_RANGE]
            val damage = values[BOOM_DAMAGE] * skillPower()
            val cone = cos(BOOM_HALF_ANGLE_DEGREES * RADIANS_PER_DEGREE)
            forEachEnemyWithin(coreX, coreY, range) { enemy ->
                if (!inCone(enemy.x, enemy.y, aimX, aimY, cone)) return@forEachEnemyWithin
                skillHit(enemy, damage, StatusElement.SONIC)
                if (!enemy.alive) return@forEachEnemyWithin
                applyStatus(enemy, StatusElement.SONIC, values[BOOM_RESONANCE].toInt(), 0)
                if (!enemy.alive) return@forEachEnemyWithin
                hurl(coreX, coreY, enemy, values[BOOM_PUSH])
                if (rankThree && !enemy.isBossClass) setMark(enemy, MarkKind.BOOMED, BOOMED_SECONDS)
            }
            var erased = 0
            val cap = values[BOOM_BOLTS].toInt()
            for (index in projectiles.indices) {
                if (erased >= cap) break
                val shot = projectiles[index]
                if (!shot.hostile || shot.spent || shot.kind != ProjectileKind.BOLT) continue
                if (distanceSquared(coreX, coreY, shot.x, shot.y) > range * range || !inCone(shot.x, shot.y, aimX, aimY, cone)) continue
                if (eraseHostileShot(shot, EraseCause.SKILL, shot.x, shot.y)) erased++
            }
            applyCoreImpulse(-aimX, -aimY, values[BOOM_RECOIL], gated = false)
            range
        }
        SkillId.RESONATOR -> {
            // The tick clock starts on the beat grid, so pulses land on global beats.
            val nextBeat = (floor(elapsed / BEAT_PERIOD) + 1f) * BEAT_PERIOD
            addField(
                CombatFieldKind.ECHO_RING, FieldSource.SKILL, slot, anchorX, anchorY, radius = values[RESONATOR_RADIUS],
                seconds = values[RESONATOR_SECONDS], damagePerSecond = 0f, element = StatusElement.SONIC,
                stacksPerTick = values[RESONATOR_ECHOES].toInt(), generation = 0,
                tickClock = BEAT_PERIOD - (nextBeat - elapsed), power = values[RESONATOR_SCALE],
                flags = if (rankThree) FieldFlags.RANK_III else 0,
            )
            values[RESONATOR_RADIUS]
        }
        SkillId.VOID_STEP -> voidStep(slot, values, aimX, aimY, rankThree)
        SkillId.NULL_SHROUD -> {
            runtime[slot, SkillCell.ACTIVE_UNTIL] = elapsed + values[SHROUD_SECONDS]
            runtime[slot, SkillCell.PENDING_AT] = elapsed + values[SHROUD_SECONDS]
            runtime[slot, SkillCell.STORED] = 0f
            swallowShots(slot)
            coreRadius + SHROUD_REACH
        }
        SkillId.SLINGSHOT -> {
            val swung = (speed * values[SLING_SCALE]).coerceIn(values[SLING_MIN_SPEED], values[SLING_MAX_SPEED])
            velocityX = aimX * swung
            velocityY = aimY * swung
            runtime[slot, SkillCell.ACTIVE_UNTIL] = elapsed + values[SLING_WINDOW]
            fireTurnEdge()
            0f
        }
        SkillId.FULL_STOP -> {
            val impact = impactPower(speed) * values[FULL_STOP_SCALE] * kineticSkillScale()
            velocityX = 0f
            velocityY = 0f
            val radius = values[FULL_STOP_RADIUS] * selfSkillRadiusScale()
            forEachEnemyWithin(coreX, coreY, radius) { enemy ->
                skillHit(enemy, impact, null, kinetic = true)
                if (enemy.alive) forceBreaks(enemy, values[FULL_STOP_BREAK])
            }
            shockwave(coreX, coreY, 0.35f, radius, 3)
            prepareGhost(slot, impact, radius)
            // Rank III: the stop counts as a Brake release.
            if (rankThree) onBrakeRelease()
            radius
        }
        SkillId.CATALYZE -> {
            catalyzeAround(anchorX, anchorY, values[CATALYZE_RADIUS], values[CATALYZE_TARGETS].toInt(), values[CATALYZE_SCALE],
                values[CATALYZE_FALLBACK].toInt())
            values[CATALYZE_RADIUS]
        }
        SkillId.DUAL_INFUSION -> {
            runtime[slot, SkillCell.ACTIVE_UNTIL] = elapsed + values[INFUSION_SECONDS]
            runtime[slot, SkillCell.STORED] = 0f
            runtime[slot, SkillCell.AUX_COUNT] = 0f
            runtime[slot, SkillCell.AUX_AT] = elapsed
            0f
        }
    }
}

/** Visits every live enemy whose body reaches within [radius] of ([x], [y]), in scan order. */
private inline fun MutableGameState.forEachEnemyWithin(x: Float, y: Float, radius: Float, action: (Enemy) -> Unit) {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        action(enemy)
    }
}

/** True when ([x], [y]) lies within the cone of half-angle cosine [cone] around the aim from the Core. */
private fun MutableGameState.inCone(x: Float, y: Float, aimX: Float, aimY: Float, cone: Float): Boolean {
    val dx = x - coreX
    val dy = y - coreY
    val distance = length(dx, dy)
    if (distance < 1f) return true
    return (dx * aimX + dy * aimY) / distance >= cone
}

/** Meteor lands at its anchor: Thermal damage, Scorch and a throw; a Core inside rides the blast away. */
private fun MutableGameState.meteorLanding(slot: Int) {
    val runtime = skillRuntime
    val rank = skillLoadout.rank(slot)
    val values = content.skill(SkillId.METEOR).rank(rank).values
    val x = runtime[slot, SkillCell.ANCHOR_X]
    val y = runtime[slot, SkillCell.ANCHOR_Y]
    val radius = values[METEOR_RADIUS]
    val damage = values[METEOR_DAMAGE] * skillPower()
    forEachEnemyWithin(x, y, radius) { enemy ->
        skillHit(enemy, damage, StatusElement.THERMAL)
        if (enemy.alive) applyStatus(enemy, StatusElement.THERMAL, values[METEOR_SCORCH].toInt(), 0)
        if (enemy.alive) hurl(x, y, enemy, values[METEOR_HURL])
    }
    var flags = 0
    val fromCentre = length(coreX - x, coreY - y)
    if (fromCentre <= radius + coreRadius) {
        flags = SkillCueCodes.FLAG_CORE_INSIDE
        var dirX: Float
        var dirY: Float
        if (fromCentre >= 1f) {
            dirX = (coreX - x) / fromCentre
            dirY = (coreY - y) / fromCentre
        } else {
            // At the very centre the blast throws the Core away from the singularity.
            val singularityX = cameraX + pointerX - screenWidth * 0.5f
            val singularityY = cameraY + pointerY - screenHeight * 0.5f
            val away = length(coreX - singularityX, coreY - singularityY)
            dirX = if (away >= 1f) (coreX - singularityX) / away else -lastAimDirectionX
            dirY = if (away >= 1f) (coreY - singularityY) / away else -lastAimDirectionY
        }
        applyCoreImpulse(dirX, dirY, values[METEOR_BOOST], gated = false)
        val total = speed
        if (total > METEOR_MAX_SPEED) {
            velocityX *= METEOR_MAX_SPEED / total
            velocityY *= METEOR_MAX_SPEED / total
        }
        if (rank >= ContentBounds.MAX_SKILL_RANK) ablazeUntil = max(ablazeUntil, elapsed + METEOR_ABLAZE_SECONDS)
    }
    shockwave(x, y, 0.45f, radius, FxColorCode.elementTone(StatusElement.THERMAL))
    emitVisualFx(VisualFxCue.SkillCast(SkillId.METEOR, x, y, 0f, 0f, radius, rank, SkillCueCodes.PHASE_IMPACT, flags))
}

/**
 * Void Step: the Core steps toward the aim (stopping short of the singularity and of obstacles), keeps most of
 * its speed and phases out; a rift arms where it stood. Rank III leaves the enemies on the path Brittle and Hollow.
 */
private fun MutableGameState.voidStep(slot: Int, values: List<Float>, aimX: Float, aimY: Float, rankThree: Boolean): Float {
    val runtime = skillRuntime
    val singularityX = cameraX + pointerX - screenWidth * 0.5f
    val singularityY = cameraY + pointerY - screenHeight * 0.5f
    var range = values[VOID_STEP_DISTANCE]
    val ahead = (singularityX - coreX) * aimX + (singularityY - coreY) * aimY
    if (ahead > 0f) range = min(range, max(0f, length(singularityX - coreX, singularityY - coreY) - VOID_STEP_SINGULARITY_GAP))
    range = blinkRangeClearOfObstacles(aimX, aimY, range)
    val startX = coreX
    val startY = coreY
    runtime[slot, SkillCell.ANCHOR_X] = startX
    runtime[slot, SkillCell.ANCHOR_Y] = startY
    runtime[slot, SkillCell.PENDING_AT] = elapsed + values[VOID_STEP_RIFT_DELAY]
    phaseUntil = max(phaseUntil, elapsed + values[VOID_STEP_PHASE])
    if (range <= 0f) return 0f
    val endX = coreX + aimX * range
    val endY = coreY + aimY * range
    if (rankThree) {
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive || isShifting(enemy)) continue
            if (!segmentCircleIntersects(startX, startY, endX, endY, enemy.x, enemy.y, enemy.radius + coreRadius)) continue
            enemy.status[StatusCell.BRITTLE] = 1f
            applyStatus(enemy, StatusElement.VOID, 1, 0)
            noteCastTarget(enemy)
        }
    }
    coreX = endX
    coreY = endY
    previousCoreX = endX
    previousCoreY = endY
    previousSingularityX = singularityX
    previousSingularityY = singularityY
    velocityX *= VOID_STEP_SPEED_RETENTION
    velocityY *= VOID_STEP_SPEED_RETENTION
    markBlink(startX, startY, endX, endY)
    return range
}

/** The Void Step rift detonates where the step began. */
private fun MutableGameState.voidRift(slot: Int) {
    val runtime = skillRuntime
    val rank = skillLoadout.rank(slot)
    val values = content.skill(SkillId.VOID_STEP).rank(rank).values
    val x = runtime[slot, SkillCell.ANCHOR_X]
    val y = runtime[slot, SkillCell.ANCHOR_Y]
    val radius = values[VOID_STEP_RIFT_RADIUS]
    val damage = values[VOID_STEP_RIFT_DAMAGE] * skillPower()
    forEachEnemyWithin(x, y, radius) { enemy ->
        skillHit(enemy, damage, StatusElement.VOID)
        if (enemy.alive) applyStatus(enemy, StatusElement.VOID, values[VOID_STEP_HOLLOW].toInt(), 0)
    }
    shockwave(x, y, 0.3f, radius, FxColorCode.elementTone(StatusElement.VOID))
    emitVisualFx(VisualFxCue.SkillCast(SkillId.VOID_STEP, x, y, 0f, 0f, radius, rank, SkillCueCodes.PHASE_IMPACT))
}

/** Null Shroud swallows the hostile bolts touching the Core's shroud, up to its store. */
private fun MutableGameState.swallowShots(slot: Int) {
    val runtime = skillRuntime
    val values = content.skill(SkillId.NULL_SHROUD).rank(skillLoadout.rank(slot)).values
    val cap = values[SHROUD_STORE].toInt()
    var stored = runtime[slot, SkillCell.STORED].toInt()
    if (stored >= cap) return
    val reach = coreRadius + SHROUD_REACH
    for (index in projectiles.indices) {
        if (stored >= cap) break
        val shot = projectiles[index]
        if (!shot.hostile || shot.spent || shot.kind != ProjectileKind.BOLT) continue
        if (distanceSquared(coreX, coreY, shot.x, shot.y) > square(reach + shot.radius)) continue
        if (eraseHostileShot(shot, EraseCause.SKILL, shot.x, shot.y)) stored++
    }
    runtime[slot, SkillCell.STORED] = stored.toFloat()
}

/** The shroud ends: every stored bolt flies back, round-robin over the nearest enemies within reach. */
private fun MutableGameState.releaseShroud(slot: Int) {
    val runtime = skillRuntime
    val rank = skillLoadout.rank(slot)
    val values = content.skill(SkillId.NULL_SHROUD).rank(rank).values
    val stored = runtime[slot, SkillCell.STORED].toInt()
    runtime[slot, SkillCell.STORED] = 0f
    if (stored <= 0) return
    val targets = pickNearestEnemies(coreX, coreY, values[SHROUD_RANGE], SHROUD_RELEASE_TARGETS)
    if (targets == 0) return
    val damage = values[SHROUD_DAMAGE] * skillPower()
    val pierce = if (rank >= ContentBounds.MAX_SKILL_RANK) 1 else 0
    for (shot in 0 until stored) {
        val target = enemyById(skillScratch[shot % targets]) ?: continue
        val angle = kotlin.math.atan2(target.y - coreY, target.x - coreX)
        addProjectile(Projectile(
            x = coreX, y = coreY, vx = cos(angle) * SHROUD_SHOT_SPEED, vy = sin(angle) * SHROUD_SHOT_SPEED,
            radius = 5f, life = 2f, hostile = false, damage = damage, pierce = pierce, colorIndex = 2,
            statusElement = StatusElement.VOID.ordinal, procKey = WeaponRuntime.PROC_SKILL,
        ))
    }
    emitVisualFx(VisualFxCue.SkillCast(SkillId.NULL_SHROUD, coreX, coreY, 0f, 0f, coreRadius + SHROUD_REACH, rank,
        SkillCueCodes.PHASE_IMPACT))
}

/** A Null Shroud shot struck [enemy]: element-less skill damage, then its Hollow rider. */
internal fun MutableGameState.skillShotHit(enemy: Enemy, damage: Float, statusElement: Int) {
    skillFieldHit(skillLoadout.slotOf(SkillId.NULL_SHROUD), enemy, damage, null)
    if (statusElement >= 0 && enemy.alive) applyStatus(enemy, StatusElement.entries[statusElement], 2, 1)
}

/**
 * Fills [skillScratch] with the ids of up to [count] live, non-shifting enemies nearest ([x], [y]) within
 * [range], nearest first; returns how many it found.
 */
private fun MutableGameState.pickNearestEnemies(x: Float, y: Float, range: Float, count: Int): Int {
    val limit = min(count, skillScratch.size)
    var picked = 0
    val rangeSquared = range * range
    while (picked < limit) {
        var best: Enemy? = null
        var bestDistance = rangeSquared
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive || isShifting(enemy)) continue
            var taken = false
            for (scan in 0 until picked) if (skillScratch[scan] == enemy.id) taken = true
            if (taken) continue
            val distance = distanceSquared(x, y, enemy.x, enemy.y)
            if (distance <= bestDistance && (best == null || distance < bestDistance)) {
                best = enemy
                bestDistance = distance
            }
        }
        val chosen = best ?: break
        skillScratch[picked++] = chosen.id
    }
    return picked
}

/**
 * Catalyze: up to [count] enemies nearest ([x], [y]) within [radius] react at once ([scale] magnitude); an enemy
 * with a single element takes [fallback] stacks of the build's partner element instead and may react on its own.
 */
private fun MutableGameState.catalyzeAround(x: Float, y: Float, radius: Float, count: Int, scale: Float, fallback: Int) {
    val picked = pickNearestEnemies(x, y, radius + MAX_ENEMY_RADIUS_SLACK, count)
    for (index in 0 until picked) {
        val enemy = enemyById(skillScratch[index]) ?: continue
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        var elements = 0
        var only: StatusElement? = null
        for (element in StatusElement.entries) {
            if (stacks(enemy, element) > 0) {
                elements++
                only = element
            }
        }
        if (elements >= 2) {
            if (forceReaction(enemy, 0, scale)) noteCastTarget(enemy)
        } else if (elements == 1 && only != null) {
            applyStatus(enemy, catalystPartner(only), fallback, 0)
            noteCastTarget(enemy)
        }
    }
    shockwave(x, y, 0.35f, radius, 2)
}

/** The element Catalyze adds to an enemy that holds only [element]: the build's secondary, else dominant, else Thermal/Cryo. */
private fun MutableGameState.catalystPartner(element: StatusElement): StatusElement {
    val secondary = build.secondary
    val dominant = build.dominant
    return when {
        secondary != null && secondary != element -> secondary
        dominant != null && dominant != element -> dominant
        element == StatusElement.THERMAL -> StatusElement.CRYO
        else -> StatusElement.THERMAL
    }
}

/** The Spiral's ghost repeats a self-centred damage skill where it stands, at a share of the damage, nothing else. */
private fun MutableGameState.prepareGhost(slot: Int, damage: Float, radius: Float) {
    if (coreShape != CoreShape.SPIRAL) return
    val runtime = skillRuntime
    runtime[slot, SkillCell.GHOST_AT] = elapsed + GHOST_REPEAT_DELAY
    runtime[slot, SkillCell.GHOST_DAMAGE] = damage
    runtime[slot, SkillCell.GHOST_RADIUS] = radius
}

private fun MutableGameState.ghostRepeat(slot: Int, id: SkillId) {
    if (!ghostActive) return
    val runtime = skillRuntime
    val x = ghostX
    val y = ghostY
    val radius = runtime[slot, SkillCell.GHOST_RADIUS]
    val stored = runtime[slot, SkillCell.GHOST_DAMAGE]
    forEachEnemyWithin(x, y, radius) { enemy ->
        when (id) {
            SkillId.SOLAR_FLARE -> skillHit(enemy, stored * GHOST_REPEAT_SHARE, StatusElement.THERMAL)
            SkillId.FROST_SNAP -> if (isFrozen(enemy) || enemy.status[StatusCell.BRITTLE] > 0f) {
                skillHit(enemy, stored * GHOST_REPEAT_SHARE, null, kinetic = true)
            }
            SkillId.FULL_STOP -> skillHit(enemy, stored * GHOST_REPEAT_SHARE, null, kinetic = true)
            // The Tide's repeat reads the Decay now on the enemy and consumes none of it.
            SkillId.ENTROPIC_TIDE -> skillHit(enemy, detonateDecayAmount(enemy, StatusElement.ENTROPY) * stored * GHOST_REPEAT_SHARE,
                StatusElement.ENTROPY)
            else -> Unit
        }
    }
    shockwave(x, y, 0.25f, radius, 2)
}

// ---------------------------------------------------------------------------------------------
// Skill fields (kind branches the field pass calls).

/** Seconds between two ticks of a field of [kind]: Storm Coil zaps every half second, a Resonator on the beat. */
internal fun tickPeriod(kind: CombatFieldKind): Float = when (kind) {
    CombatFieldKind.STORM_COIL -> STORM_COIL_ZAP_SECONDS
    CombatFieldKind.ECHO_RING -> BEAT_PERIOD
    else -> CombatField.TICK_SECONDS
}

/** True when ([x], [y]) is within [extra] of the field at [index] (a Frost Line measures to its segment). */
internal fun MutableGameState.fieldContains(index: Int, x: Float, y: Float, extra: Float): Boolean {
    val table = combatFieldTable
    val reach = table[index, FieldCell.RADIUS] + extra
    return if (table.kind(index) == CombatFieldKind.FROST_LINE) {
        pointToSegmentDistanceSquared(x, y, table[index, FieldCell.X], table[index, FieldCell.Y],
            table[index, FieldCell.X2], table[index, FieldCell.Y2]) <= reach * reach
    } else {
        distanceSquared(table[index, FieldCell.X], table[index, FieldCell.Y], x, y) <= reach * reach
    }
}

/**
 * Every step a Frost Line slows the ordinary enemies on it, and a thrown enemy that crosses it slams into it
 * and takes Chill.
 */
internal fun MutableGameState.frostLineStep(index: Int, delta: Float) {
    val table = combatFieldTable
    val x0 = table[index, FieldCell.X]
    val y0 = table[index, FieldCell.Y]
    val x1 = table[index, FieldCell.X2]
    val y1 = table[index, FieldCell.Y2]
    val damping = exp(-FROST_LINE_DAMPING * delta)
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || isShifting(enemy)) continue
        val thrown = enemy.thrownUntil > elapsed || isThrownByPolarity(enemy)
        if (thrown && segmentsCross(enemy.previousX, enemy.previousY, enemy.x, enemy.y, x0, y0, x1, y1)) {
            if (applySlamDamage(enemy, length(enemy.vx, enemy.vy)) && enemy.alive) {
                applyStatus(enemy, StatusElement.CRYO, FROST_LINE_SLAM_CHILL, 0)
            }
        }
        if (!enemy.alive || enemy.isEliteClass || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        enemy.vx *= damping
        enemy.vy *= damping
    }
}

/** A Frost Line tick: Chill on the line (rank III: Embrittled while it stays). */
internal fun MutableGameState.frostLineTick(index: Int) {
    val table = combatFieldTable
    val chill = table[index, FieldCell.STACKS].toInt()
    val rankThree = table.hasFlag(index, FieldFlags.RANK_III)
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        applyStatus(enemy, StatusElement.CRYO, chill, 0)
        if (rankThree && enemy.alive) {
            enemy.status[StatusCell.EMBRITTLED_UNTIL] = max(enemy.status[StatusCell.EMBRITTLED_UNTIL], elapsed + FROST_LINE_EMBRITTLE_SECONDS)
        }
    }
}

/**
 * A Storm Coil zap: the nearest enemies inside (at most its target count, three by construction) take Ion
 * damage and one Charge; rank III forces Overload on an enemy close to it.
 */
internal fun MutableGameState.stormCoilZap(index: Int) {
    val table = combatFieldTable
    val x = table[index, FieldCell.X]
    val y = table[index, FieldCell.Y]
    val radius = table[index, FieldCell.RADIUS]
    val count = table[index, FieldCell.STACKS].toInt().coerceIn(0, STORM_COIL_MAX_TARGETS)
    val damage = table[index, FieldCell.POWER]
    val rankThree = table.hasFlag(index, FieldFlags.RANK_III)
    var firstId = -1
    var secondId = -1
    var thirdId = -1
    var firstDistance = Float.POSITIVE_INFINITY
    var secondDistance = Float.POSITIVE_INFINITY
    var thirdDistance = Float.POSITIVE_INFINITY
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || isShifting(enemy)) continue
        val distance = distanceSquared(x, y, enemy.x, enemy.y)
        if (distance > square(radius + enemy.radius)) continue
        when {
            distance < firstDistance -> {
                thirdId = secondId; thirdDistance = secondDistance
                secondId = firstId; secondDistance = firstDistance
                firstId = enemy.id; firstDistance = distance
            }
            distance < secondDistance -> {
                thirdId = secondId; thirdDistance = secondDistance
                secondId = enemy.id; secondDistance = distance
            }
            distance < thirdDistance -> {
                thirdId = enemy.id; thirdDistance = distance
            }
        }
    }
    val link = LinkStyleCode.skill(SkillId.STORM_COIL)
    for (pick in 0 until count) {
        val id = when (pick) { 0 -> firstId; 1 -> secondId; else -> thirdId }
        val enemy = enemyById(id) ?: break
        if (!enemy.alive) continue
        addRelicArc(x, y, enemy.x, enemy.y, link)
        skillFieldHit(table.owner(index), enemy, damage, StatusElement.ION)
        if (enemy.alive) applyStatus(enemy, StatusElement.ION, 1, 0)
        if (rankThree && enemy.alive &&
            stacks(enemy, StatusElement.ION) >= breakThreshold(StatusElement.ION, enemy) - SKILL_BREAK_MARGIN
        ) forceElementBreak(enemy, StatusElement.ION)
    }
}

/**
 * A Resonator pulse on the beat: one Resonance to every enemy inside, then the latest reaction inside (within
 * the last beat) echoes once on its enemy, while the resonator has echoes left.
 */
internal fun MutableGameState.resonatorPulse(index: Int) {
    val table = combatFieldTable
    var latest: Enemy? = null
    var latestAt = elapsed - BEAT_PERIOD
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        applyStatus(enemy, StatusElement.SONIC, 1, 0)
    }
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        val at = enemy.status[StatusCell.LAST_REACTION_AT]
        if (enemy.status[StatusCell.LAST_REACTION] > 0f && at >= latestAt && (latest == null || at > latestAt)) {
            latest = enemy
            latestAt = at
        }
    }
    val echoTarget = latest ?: return
    if (table[index, FieldCell.CHARGE] >= table[index, FieldCell.STACKS]) return
    val reaction = kinetickk.ball.content.api.ReactionId.entries.getOrNull(echoTarget.status[StatusCell.LAST_REACTION].toInt() - 1)
        ?: return
    if (echoReaction(echoTarget, reaction, table[index, FieldCell.POWER])) {
        table[index, FieldCell.CHARGE] = table[index, FieldCell.CHARGE] + 1f
        table.touch()
        addRelicArc(table[index, FieldCell.X], table[index, FieldCell.Y], echoTarget.x, echoTarget.y,
            LinkStyleCode.skill(SkillId.RESONATOR))
    }
}

/** A tick of the skill field at [index]'s own damage (a Gravity Well's): skill damage, never a skill hit. */
internal fun MutableGameState.skillFieldTickDamage(index: Int, target: Enemy, amount: Float, element: StatusElement?) {
    skillFieldHit(combatFieldTable.owner(index), target, amount, element)
}

/** A Gravity Well closes: loose Data and repairs inside latch to the Core; rank III collapses the heavy enemies. */
internal fun MutableGameState.gravityWellClosed(index: Int) {
    val table = combatFieldTable
    val x = table[index, FieldCell.X]
    val y = table[index, FieldCell.Y]
    val radius = table[index, FieldCell.RADIUS]
    val latch = latchedVacuumActive()
    for (pickupIndex in pickups.indices) {
        val pickup = pickups[pickupIndex]
        if (!pickup.expires || pickup.attracted) continue
        if (distanceSquared(x, y, pickup.x, pickup.y) <= radius * radius) pickup.attracted = latch
    }
    if (!table.hasFlag(index, FieldFlags.RANK_III)) return
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        if (stacks(enemy, StatusElement.GRAVITIC) >= GRAVITY_WELL_COLLAPSE_MASS) forceElementBreak(enemy, StatusElement.GRAVITIC)
    }
}

// ---------------------------------------------------------------------------------------------
// Hooks other systems call.

/** Mass multiplier while Mass Driver runs. */
internal fun MutableGameState.skillMassMultiplier(): Float {
    val slot = activeSkillSlot(SkillId.MASS_DRIVER)
    return if (slot < 0) 1f else skillValues(slot)[MASS_DRIVER_MASS]
}

/** The slot holding [id] while its effect runs (ACTIVE_UNTIL ahead), or -1. */
private fun MutableGameState.activeSkillSlot(id: SkillId): Int {
    val slot = skillLoadout.slotOf(id)
    return if (slot >= 0 && skillRuntime[slot, SkillCell.ACTIVE_UNTIL] > elapsed) slot else -1
}

private fun MutableGameState.skillValues(slot: Int): List<Float> {
    val id = checkNotNull(skillLoadout.skill(slot)) { "slot $slot is empty" }
    return content.skill(id).rank(skillLoadout.rank(slot)).values
}

/**
 * A Core impact on [enemy] at [coreImpactSpeed] dealing [damage]: Arc Hook's window turns the first one into a
 * Dash hit with arcs, Mass Driver throws, Slingshot hurls and rank III Dual Infusion infuses.
 */
internal fun MutableGameState.onSkillCoreImpact(enemy: Enemy, coreImpactSpeed: Float, damage: Float, dashContact: Boolean) {
    val loadout = skillLoadout
    if (loadout.ownedCount == 0) return
    val runtime = skillRuntime
    val hookSlot = loadout.slotOf(SkillId.ARC_HOOK)
    if (hookSlot >= 0 && runtime[hookSlot, SkillCell.TARGET_UNTIL] > elapsed) {
        runtime[hookSlot, SkillCell.TARGET_UNTIL] = 0f
        if (!dashContact) onDashHit(enemy, damage)
        markForPuncture(enemy)
        val values = skillValues(hookSlot)
        val linkDamage = values[ARC_HOOK_LINK_DAMAGE] * skillPower()
        withSkillEffect(hookSlot) {
            chain(enemy.x, enemy.y, enemy.id, values[ARC_HOOK_LINKS].toInt(), ARC_HOOK_LINK_RANGE, LinkStyleCode.skill(SkillId.ARC_HOOK)) { link ->
                skillHit(link, linkDamage, StatusElement.ION)
                if (link.alive) applyStatus(link, StatusElement.ION, 1, 0)
            }
        }
    }
    if (!enemy.alive) return
    val driver = activeSkillSlot(SkillId.MASS_DRIVER)
    if (driver >= 0) {
        val values = skillValues(driver)
        if (coreImpactSpeed >= values[MASS_DRIVER_THRESHOLD]) {
            hurl(coreX, coreY, enemy, values[MASS_DRIVER_THROW])
            applyStatus(enemy, StatusElement.GRAVITIC, MASS_DRIVER_IMPACT_MASS, 0)
        }
    }
    if (!enemy.alive) return
    val sling = activeSkillSlot(SkillId.SLINGSHOT)
    if (sling >= 0) hurl(coreX, coreY, enemy, skillValues(sling)[SLING_HURL])
    val infusion = activeSkillSlot(SkillId.DUAL_INFUSION)
    if (infusion >= 0 && loadout.rank(infusion) >= ContentBounds.MAX_SKILL_RANK && enemy.alive) {
        infuse(infusion, enemy, infusionDominant())
        if (enemy.alive) infuse(infusion, enemy, infusionSecondary())
    }
}

/** Rank III Slingshot: impacts during the swing keep the Core's momentum. */
internal fun MutableGameState.slingshotKeepsMomentum(): Boolean {
    val slot = activeSkillSlot(SkillId.SLINGSHOT)
    return slot >= 0 && skillLoadout.rank(slot) >= ContentBounds.MAX_SKILL_RANK
}

/** A weapon hit on [enemy] while Dual Infusion runs: the dominant element, and every n-th hit the secondary. */
internal fun MutableGameState.onSkillWeaponHit(enemy: Enemy) {
    if (skillLoadout.ownedCount == 0) return
    val slot = activeSkillSlot(SkillId.DUAL_INFUSION)
    if (slot < 0 || !enemy.alive) return
    val runtime = skillRuntime
    val hits = runtime[slot, SkillCell.STORED] + 1f
    runtime[slot, SkillCell.STORED] = hits
    infuse(slot, enemy, infusionDominant())
    val every = skillValues(slot)[INFUSION_HITS_PER_SECONDARY].toInt().coerceAtLeast(1)
    if (enemy.alive && hits.toInt() % every == 0) infuse(slot, enemy, infusionSecondary())
}

private fun MutableGameState.infusionDominant(): StatusElement = build.dominant ?: StatusElement.THERMAL

private fun MutableGameState.infusionSecondary(): StatusElement = build.secondary ?: StatusElement.CRYO

/** One infused stack, at most the per-second cap of [slot]'s Dual Infusion. */
private fun MutableGameState.infuse(slot: Int, enemy: Enemy, element: StatusElement) {
    val runtime = skillRuntime
    if (elapsed - runtime[slot, SkillCell.AUX_AT] >= 1f) {
        runtime[slot, SkillCell.AUX_AT] = elapsed
        runtime[slot, SkillCell.AUX_COUNT] = 0f
    }
    val applied = runtime[slot, SkillCell.AUX_COUNT]
    if (applied >= skillValues(slot)[INFUSION_STACK_CAP]) return
    runtime[slot, SkillCell.AUX_COUNT] = applied + 1f
    applyStatus(enemy, element, 1, 0)
}

/** A landed slam on [enemy]: a ringing (BOOMED) enemy Shatters, and rank III Mass Driver adds Mass. */
internal fun MutableGameState.onSkillSlam(enemy: Enemy) {
    if (!enemy.alive) return
    if (consumeMark(enemy, MarkKind.BOOMED)) forceElementBreak(enemy, StatusElement.SONIC)
    val driver = activeSkillSlot(SkillId.MASS_DRIVER)
    if (driver >= 0 && skillLoadout.rank(driver) >= ContentBounds.MAX_SKILL_RANK && enemy.alive) {
        applyStatus(enemy, StatusElement.GRAVITIC, 1, 0)
    }
}

/** True while a Frost Snap has coiled the next Dash. */
internal fun MutableGameState.dashCoiled(): Boolean {
    val slot = skillLoadout.slotOf(SkillId.FROST_SNAP)
    return slot >= 0 && skillRuntime[slot, SkillCell.AUX_UNTIL] > elapsed
}

/** Spends a coiled Dash and returns its push scale (1 when none is coiled). */
internal fun MutableGameState.consumeCoiledDash(): Float {
    if (!dashCoiled()) return 1f
    val slot = skillLoadout.slotOf(SkillId.FROST_SNAP)
    skillRuntime[slot, SkillCell.AUX_UNTIL] = 0f
    return skillValues(slot)[FROST_SNAP_COILED_PUSH]
}

/** Bolts Null Shroud holds right now (0 when it is not running). */
internal fun MutableGameState.shroudStored(): Int {
    val slot = activeSkillSlot(SkillId.NULL_SHROUD)
    return if (slot < 0) 0 else skillRuntime[slot, SkillCell.STORED].toInt()
}

/**
 * A death: a Spored enemy spreads Decay to its neighbours and drops Data (within its cast's budget); rank III
 * Decay Spores refunds every skill for it.
 */
internal fun MutableGameState.onSkillCarrierDied(enemy: Enemy) {
    if (!consumeMark(enemy, MarkKind.SPORED)) return
    for (index in enemies.indices) {
        val target = enemies[index]
        if (target.id == enemy.id || !target.alive) continue
        if (distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(SPORES_SPREAD_RADIUS + target.radius)) {
            applyStatus(target, StatusElement.ENTROPY, SPORES_SPREAD_DECAY, 1)
        }
    }
    val slot = skillLoadout.slotOf(SkillId.DECAY_SPORES)
    if (slot < 0) return
    val runtime = skillRuntime
    val values = skillValues(slot)
    val dropped = runtime[slot, SkillCell.STORED]
    if (dropped < values[SPORES_DATA_CAP]) {
        runtime[slot, SkillCell.STORED] = dropped + 1f
        // A golden-angle scatter from the enemy id: no draw from any random stream.
        val angle = enemy.id * SPORES_SCATTER_ANGLE
        addPickup(Pickup(PickupType.DATA, enemy.x, enemy.y, cos(angle) * SPORES_DATA_SPEED, sin(angle) * SPORES_DATA_SPEED))
    }
    if (skillLoadout.rank(slot) >= ContentBounds.MAX_SKILL_RANK) {
        val refunded = runtime[slot, SkillCell.AUX_COUNT]
        if (refunded < SPORES_REFUND_CAP) {
            runtime[slot, SkillCell.AUX_COUNT] = refunded + refundAllSkills(min(SPORES_REFUND_SECONDS, SPORES_REFUND_CAP - refunded))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Refund bank.

/**
 * Takes up to [seconds] off [slot]'s cooldown. The bank bounds it: an empty or ready slot takes nothing, a cast
 * takes back at most half its cooldown in total, and no refund brings the slot below [MIN_SKILL_RECAST_SECONDS]
 * after its cast. Returns the seconds refunded.
 */
internal fun MutableGameState.refundSkill(slot: Int, seconds: Float): Float {
    if (!(seconds > 0f) || skillLoadout.isEmpty(slot)) return 0f
    val runtime = skillRuntime
    val readyAt = runtime[slot, SkillCell.READY_AT]
    if (readyAt <= elapsed) return 0f
    val refunded = runtime[slot, SkillCell.REFUNDED]
    val room = min(
        MAX_REFUND_SHARE * runtime[slot, SkillCell.LAST_COOLDOWN] - refunded,
        readyAt - max(elapsed, runtime[slot, SkillCell.CAST_AT] + MIN_SKILL_RECAST_SECONDS),
    )
    val refund = min(seconds, room)
    if (!(refund > 0f)) return 0f
    runtime[slot, SkillCell.READY_AT] = readyAt - refund
    runtime[slot, SkillCell.REFUNDED] = refunded + refund
    return refund
}

/** Refunds [seconds] to every owned skill through the bank; returns the seconds refunded in total. */
internal fun MutableGameState.refundAllSkills(seconds: Float): Float {
    if (skillLoadout.ownedCount == 0) return 0f
    var total = 0f
    for (slot in 0 until SkillRuntime.SLOTS) total += refundSkill(slot, seconds)
    return total
}

/**
 * Catalysis: a reaction between [older] and [incoming] recharges each owned skill of either element and, less,
 * each element-less skill. Echoes never call it.
 */
internal fun MutableGameState.catalyze(older: StatusElement, incoming: StatusElement) {
    val loadout = skillLoadout
    if (loadout.ownedCount == 0) return
    for (slot in 0 until SkillRuntime.SLOTS) {
        val id = loadout.skill(slot) ?: continue
        val element = content.skill(id).element
        val seconds = when {
            element == null -> CATALYSIS_ELEMENTLESS_SECONDS
            element == older || element == incoming -> CATALYSIS_SECONDS
            else -> continue
        }
        refundSkill(slot, seconds * build.catalysisRate)
    }
}

// ---------------------------------------------------------------------------------------------
// Slots, drafts and rank-ups.

/** Fills the lowest empty slot with [id] at rank I, ready at once. */
internal fun MutableGameState.acquireSkill(id: SkillId) {
    val slot = skillLoadout.lowestEmptySlot()
    if (slot < 0 || skillLoadout.owns(id)) return
    skillLoadout = skillLoadout.with(slot, id, 1)
    resetSkillSlot(slot)
    recordSkillGain(id)
    recompileBuild()
    message = content.skill(id).name.uppercase() + " ACQUIRED"
    messageTime = 1.7f
}

/** Raises [id] a rank (up to III); the skill is ready at once. */
internal fun MutableGameState.rankUpSkill(id: SkillId) {
    val slot = skillLoadout.slotOf(id)
    if (slot < 0) return
    val rank = skillLoadout.rank(slot)
    if (rank >= ContentBounds.MAX_SKILL_RANK) return
    skillLoadout = skillLoadout.with(slot, id, rank + 1)
    val runtime = skillRuntime
    runtime[slot, SkillCell.RANKED_AT_LEVEL] = level.toFloat()
    runtime[slot, SkillCell.READY_AT] = elapsed
    runtime[slot, SkillCell.REFUNDED] = 0f
    // Rank III adds a second element point: a newer gain for affinity ties.
    if (rank + 1 >= ContentBounds.MAX_SKILL_RANK) recordSkillGain(id)
    recompileBuild()
    message = content.skill(id).name.uppercase() + " // RANK " + (rank + 1)
    messageTime = 1.7f
}

private fun MutableGameState.resetSkillSlot(slot: Int) {
    val runtime = skillRuntime
    for (cell in 0 until SkillCell.STRIDE) runtime[slot, cell] = 0f
    runtime[slot, SkillCell.TARGET_ID] = -1f
    runtime[slot, SkillCell.PENDING_AT] = -1f
    runtime[slot, SkillCell.GHOST_AT] = -1f
    runtime[slot, SkillCell.REFUSED_AT] = -1f
    runtime[slot, SkillCell.READY_AT] = elapsed
    runtime[slot, SkillCell.RANKED_AT_LEVEL] = level.toFloat()
}

/** The slot a rank-up card raises now: the lowest rank among skills held or ranked at least the gap ago (slot order on ties), or -1. */
internal fun MutableGameState.rankUpCandidateSlot(): Int {
    val loadout = skillLoadout
    val gap = content.tempo.skillRankGapLevels
    var best = -1
    for (slot in 0 until SkillRuntime.SLOTS) {
        if (loadout.isEmpty(slot)) continue
        val rank = loadout.rank(slot)
        if (rank >= ContentBounds.MAX_SKILL_RANK) continue
        if (level - skillRuntime[slot, SkillCell.RANKED_AT_LEVEL].toInt() < gap) continue
        if (best < 0 || rank < loadout.rank(best)) best = slot
    }
    return best
}

/** The rank-up card for [slot]'s skill (the rank it reaches). */
internal fun MutableGameState.rankUpCard(slot: Int): kinetickk.ball.gameplay.nucleus.render.ChoiceOption {
    val id = checkNotNull(skillLoadout.skill(slot))
    val definition = content.skill(id)
    val next = skillLoadout.rank(slot) + 1
    return kinetickk.ball.gameplay.nucleus.render.ChoiceOption(
        type = kinetickk.ball.gameplay.nucleus.render.ChoiceType.SKILL,
        title = definition.name,
        description = if (next >= ContentBounds.MAX_SKILL_RANK) definition.rankThree else definition.summary,
        tag = "RANK $next",
        skillId = id,
        skillRank = next,
    )
}

/** Characters' draft lanes: three skills each that play into the character's mechanic. */
internal fun CoreShape.synergySkills(): List<SkillId> = when (this) {
    CoreShape.ORB -> listOf(SkillId.SLINGSHOT, SkillId.ARC_HOOK, SkillId.CATALYZE)
    CoreShape.PRISM -> listOf(SkillId.FULL_STOP, SkillId.FROST_SNAP, SkillId.MASS_DRIVER)
    CoreShape.SHARD -> listOf(SkillId.VOID_STEP, SkillId.ARC_HOOK, SkillId.SONIC_BOOM)
    CoreShape.RING -> listOf(SkillId.SOLAR_FLARE, SkillId.FROST_LINE, SkillId.ENTROPIC_TIDE)
    CoreShape.DIAMOND -> listOf(SkillId.FROST_SNAP, SkillId.FULL_STOP, SkillId.NULL_SHROUD)
    CoreShape.TESSERACT -> listOf(SkillId.VOID_STEP, SkillId.CATALYZE, SkillId.METEOR)
    CoreShape.STAR -> listOf(SkillId.MASS_DRIVER, SkillId.SOLAR_FLARE, SkillId.DECAY_SPORES)
    CoreShape.SPIRAL -> listOf(SkillId.FROST_LINE, SkillId.RESONATOR, SkillId.SLINGSHOT)
    CoreShape.DIPOLE -> listOf(SkillId.GRAVITY_WELL, SkillId.SONIC_BOOM, SkillId.SLINGSHOT)
    CoreShape.PENTAGON -> listOf(SkillId.SONIC_BOOM, SkillId.RESONATOR, SkillId.STORM_COIL)
    CoreShape.CRESCENT -> listOf(SkillId.VOID_STEP, SkillId.ARC_HOOK, SkillId.NULL_SHROUD)
    CoreShape.HEXAGON -> listOf(SkillId.DECAY_SPORES, SkillId.GRAVITY_WELL, SkillId.STORM_COIL)
}

/** True when [definition] may appear in a draft now: not owned, and Catalyze only once two elements are in play. */
private fun MutableGameState.draftable(definition: SkillDefinition): Boolean =
    !skillLoadout.owns(definition.id) &&
        (definition.id != SkillId.CATALYZE || StatusElement.entries.count { build.affinity(it) >= 1 } >= CATALYZE_MIN_ELEMENTS)

/** Aligned-lane weight: element fit and tags the build already has points in. */
private fun MutableGameState.alignedSkillWeight(definition: SkillDefinition): Float {
    var weight = 1f
    val element = definition.element
    if (element != null && element == build.dominant) weight += 1f
    if (element != null && element == build.secondary) weight += 0.6f
    definition.tags.forEach { tag -> if (build.tagPoints(tag) >= 1) weight += 0.4f }
    return weight
}

/**
 * The skill draft: an aligned card (weighted by fit), a character card (uniform over the character's lane, else
 * aligned) and a wild card (uniform over the rest). Each card draws exactly one [MutableGameState.rewardRandom]
 * float, whatever the pool, so a draft always costs three draws.
 */
internal fun MutableGameState.buildSkillDraft() {
    val skills = content.skills
    val cards = ArrayList<SkillDefinition>(DRAFT_CARDS)
    fun available(definition: SkillDefinition): Boolean = draftable(definition) && cards.none { it.id == definition.id }
    fun fallback(): List<SkillDefinition> = skills.filter { !skillLoadout.owns(it.id) && cards.none { card -> card.id == it.id } }

    val alignedRoll = rewardRandom.nextFloat()
    val alignedPool = skills.filter(::available).ifEmpty { fallback() }
    weightedSkillPick(alignedPool, alignedRoll)?.let(cards::add)

    val characterRoll = rewardRandom.nextFloat()
    val lane = coreShape.synergySkills().map(content::skill).filter(::available)
    if (lane.isNotEmpty()) {
        cards += lane[(characterRoll * lane.size).toInt().coerceIn(0, lane.lastIndex)]
    } else {
        weightedSkillPick(skills.filter(::available).ifEmpty { fallback() }, characterRoll)?.let(cards::add)
    }

    val wildRoll = rewardRandom.nextFloat()
    val wild = skills.filter(::available).ifEmpty { fallback() }
    if (wild.isNotEmpty()) cards += wild[(wildRoll * wild.size).toInt().coerceIn(0, wild.lastIndex)]

    choices = cards.map { definition ->
        kinetickk.ball.gameplay.nucleus.render.ChoiceOption(
            type = kinetickk.ball.gameplay.nucleus.render.ChoiceType.SKILL,
            title = definition.name,
            description = definition.summary,
            tag = "RANK 1",
            skillId = definition.id,
            skillRank = 1,
        )
    }
}

private fun MutableGameState.weightedSkillPick(pool: List<SkillDefinition>, roll: Float): SkillDefinition? {
    if (pool.isEmpty()) return null
    var total = 0f
    for (definition in pool) total += alignedSkillWeight(definition)
    var remaining = roll * total
    for (definition in pool) {
        remaining -= alignedSkillWeight(definition)
        if (remaining < 0f) return definition
    }
    return pool.last()
}

/** Opens the next skill draft (heirloom first, then drafts, then the level's item offer). */
internal fun MutableGameState.openSkillDraft() {
    if (skillLoadout.lowestEmptySlot() < 0) return
    activeChoiceType = kinetickk.ball.gameplay.nucleus.render.ChoiceType.SKILL
    buildSkillDraft()
    if (choices.isEmpty()) return
    clearInputBuffers()
    phase = kinetickk.ball.gameplay.nucleus.render.GamePhase.CHOICE
}

/**
 * Puts the rank-up [card] in place of an item offer's experimental card (index 1), whose item was drawn and is
 * discarded so the reward stream does not shift; a short offer gains the card at its end.
 */
internal fun MutableGameState.placeRankUpCard(card: kinetickk.ball.gameplay.nucleus.render.ChoiceOption) {
    choices = when {
        choices.size >= 2 -> choices.toMutableList().also { it[1] = card }
        choices.size < MutableGameState.MAX_CHOICES -> choices + card
        else -> choices
    }
}

// ---------------------------------------------------------------------------------------------
// Constants (nucleus rules; per-rank numbers are content values).

/** No skill recasts sooner than this after its cast, whatever refunds it. */
internal const val MIN_SKILL_RECAST_SECONDS: Float = 2.5f
/** The static cooldown scale (rules and conversions) never goes below this. */
internal const val MIN_SKILL_COOLDOWN_SCALE: Float = 0.5f
/** At most this share of a cast's cooldown comes back through refunds, so no combination more than doubles a cast rate. */
internal const val MAX_REFUND_SHARE: Float = 0.5f
/** The first enemies a cast damages are its skill hits. */
internal const val MAX_SKILL_HITS_PER_CAST: Int = 4
internal const val CATALYSIS_SECONDS: Float = 0.4f
internal const val CATALYSIS_ELEMENTLESS_SECONDS: Float = 0.2f
internal const val PRISM_SKILL_COOLDOWN: Float = 0.85f
internal const val RING_SKILL_HALO: Float = 110f
internal const val RING_SKILL_RADIUS_CAP: Float = 1.5f
internal const val GHOST_REPEAT_DELAY: Float = 1f
internal const val GHOST_REPEAT_SHARE: Float = 0.35f
/** Pointer distance below which a cast keeps the last aim and an area cast lands on the Core. */
internal const val AIM_DEADZONE: Float = 24f
internal const val HOOK_SINGULARITY_GAP: Float = 90f
internal const val SKILL_BREAK_MARGIN: Int = 2
internal const val DRAFT_CARDS: Int = 3
internal const val CATALYZE_MIN_ELEMENTS: Int = 2
private const val MAX_ENEMY_RADIUS_SLACK: Float = 80f

// Value indices, per skill.
private const val SOLAR_VENT_CAP = 0
private const val SOLAR_RADIUS = 1
private const val SOLAR_DAMAGE = 2
private const val SOLAR_DAMAGE_PER_HEAT = 3
private const val SOLAR_SCORCH = 4
private const val SOLAR_SCORCH_CAP = 6
private const val SOLAR_HEAT_PER_SCORCH = 15f

private const val METEOR_RADIUS = 0
private const val METEOR_DAMAGE = 1
private const val METEOR_SCORCH = 2
private const val METEOR_BOOST = 3
private const val METEOR_DELAY = 4
private const val METEOR_HURL = 5
internal const val METEOR_MAX_SPEED: Float = 1_800f
private const val METEOR_ABLAZE_SECONDS: Float = 3f

private const val FROST_LINE_LENGTH = 0
private const val FROST_LINE_SECONDS = 1
private const val FROST_LINE_BOLTS = 2
private const val FROST_LINE_CHILL = 3
private const val FROST_LINE_HALF_WIDTH = 4
internal const val FROST_LINE_AHEAD: Float = 160f
private const val FROST_LINE_DAMPING: Float = 4f
private const val FROST_LINE_SLAM_CHILL: Int = 2
private const val FROST_LINE_EMBRITTLE_SECONDS: Float = 1f

private const val FROST_SNAP_RADIUS = 0
private const val FROST_SNAP_CHILL = 1
private const val FROST_SNAP_SHATTER = 2
private const val FROST_SNAP_COILED_PUSH = 3
private const val FROST_SNAP_COILED_SECONDS = 4
private const val FROST_SNAP_MIN_SPEED: Float = 600f

private const val ARC_HOOK_REACH = 0
private const val ARC_HOOK_CHARGE = 1
private const val ARC_HOOK_SPEED = 2
private const val ARC_HOOK_LINKS = 3
private const val ARC_HOOK_LINK_DAMAGE = 4
private const val ARC_HOOK_WINDOW = 5
private const val ARC_HOOK_LINK_RANGE: Float = 260f
private const val ARC_HOOK_KILL_WINDOW: Float = 1.5f

private const val STORM_COIL_RADIUS = 0
private const val STORM_COIL_SECONDS = 1
private const val STORM_COIL_TARGETS = 2
private const val STORM_COIL_DAMAGE = 3
internal const val STORM_COIL_ZAP_SECONDS: Float = 0.5f
private const val STORM_COIL_MAX_TARGETS: Int = 3

private const val GRAVITY_WELL_RADIUS = 0
private const val GRAVITY_WELL_SECONDS = 1
private const val GRAVITY_WELL_PULL = 2
private const val GRAVITY_WELL_DPS = 3
private const val GRAVITY_WELL_DATA_RANGE = 4
internal const val GRAVITY_WELL_PICKUP_SPEED: Float = 400f
private const val GRAVITY_WELL_COLLAPSE_MASS: Int = 3

private const val MASS_DRIVER_SECONDS = 0
private const val MASS_DRIVER_MASS = 1
private const val MASS_DRIVER_GUARD = 2
private const val MASS_DRIVER_THROW = 3
private const val MASS_DRIVER_THRESHOLD = 4
private const val MASS_DRIVER_IMPACT_MASS: Int = 2

private const val SPORES_RADIUS = 0
private const val SPORES_DECAY = 1
private const val SPORES_SECONDS = 2
private const val SPORES_DATA_CAP = 3
private const val SPORES_SPREAD_RADIUS: Float = 130f
private const val SPORES_SPREAD_DECAY: Int = 2
private const val SPORES_DATA_SPEED: Float = 60f
private const val SPORES_SCATTER_ANGLE: Float = 2.39996f
private const val SPORES_REFUND_SECONDS: Float = 0.5f
private const val SPORES_REFUND_CAP: Float = 3f

private const val TIDE_RADIUS = 0
private const val TIDE_SCALE = 1
private const val TIDE_HEAL_PER_STACK = 2
private const val TIDE_HEAL_CAP = 3
private const val TIDE_HEAT_PER_STACK: Float = 1f

private const val BOOM_RANGE = 0
private const val BOOM_DAMAGE = 1
private const val BOOM_RESONANCE = 2
private const val BOOM_BOLTS = 3
private const val BOOM_PUSH = 4
private const val BOOM_RECOIL = 5
private const val BOOM_HALF_ANGLE_DEGREES: Float = 35f
private const val BOOMED_SECONDS: Float = 0.8f

private const val RESONATOR_RADIUS = 0
private const val RESONATOR_SECONDS = 1
private const val RESONATOR_SCALE = 2
private const val RESONATOR_ECHOES = 3

private const val VOID_STEP_DISTANCE = 0
private const val VOID_STEP_RIFT_DAMAGE = 1
private const val VOID_STEP_HOLLOW = 2
private const val VOID_STEP_RIFT_RADIUS = 3
private const val VOID_STEP_PHASE = 4
private const val VOID_STEP_RIFT_DELAY = 5
private const val VOID_STEP_SINGULARITY_GAP: Float = 60f
private const val VOID_STEP_SPEED_RETENTION: Float = 0.8f

private const val SHROUD_SECONDS = 0
private const val SHROUD_STORE = 1
private const val SHROUD_DAMAGE = 2
private const val SHROUD_RANGE = 3
internal const val SHROUD_REACH: Float = 44f
private const val SHROUD_RELEASE_TARGETS: Int = 4
private const val SHROUD_SHOT_SPEED: Float = 720f

private const val SLING_SCALE = 0
private const val SLING_MIN_SPEED = 1
private const val SLING_MAX_SPEED = 2
private const val SLING_WINDOW = 3
private const val SLING_HURL = 4

private const val FULL_STOP_RADIUS = 0
private const val FULL_STOP_SCALE = 1
private const val FULL_STOP_BREAK = 2

private const val CATALYZE_RADIUS = 0
private const val CATALYZE_TARGETS = 1
private const val CATALYZE_SCALE = 2
private const val CATALYZE_FALLBACK = 3

private const val INFUSION_SECONDS = 0
private const val INFUSION_HITS_PER_SECONDARY = 1
private const val INFUSION_STACK_CAP = 2
