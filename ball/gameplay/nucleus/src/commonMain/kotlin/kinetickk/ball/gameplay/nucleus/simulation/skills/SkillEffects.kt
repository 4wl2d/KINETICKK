// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.SkillCueCodes
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Effects. Values follow the per-skill order of `defaultSkillDefinitions`.

/** Resolves [id]'s effect for [slot] at [rank]; returns the radius its cue shows. */
internal fun MutableGameState.resolveSkillCast(
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
        SkillId.SOLAR_FLARE -> castSolarFlare(slot, id, values, rankThree)
        SkillId.METEOR -> {
            runtime[slot, SkillCell.ANCHOR_X] = anchorX
            runtime[slot, SkillCell.ANCHOR_Y] = anchorY
            runtime[slot, SkillCell.PENDING_AT] = elapsed + values[METEOR_DELAY]
            values[METEOR_RADIUS]
        }
        SkillId.FROST_LINE -> castFrostLine(slot, values, aimX, aimY, rankThree)
        SkillId.FROST_SNAP -> castFrostSnap(slot, id, values, runtime, rankThree)
        SkillId.ARC_HOOK -> castArcHook(slot, values, target, runtime, rankThree)
        SkillId.STORM_COIL -> {
            addField(
                CombatFieldKind.STORM_COIL, FieldSource.SKILL, slot, anchorX, anchorY, radius = values[STORM_COIL_RADIUS],
                seconds = values[STORM_COIL_SECONDS], damagePerSecond = 0f, element = attuned(StatusElement.ION),
                stacksPerTick = values[STORM_COIL_TARGETS].toInt(), generation = 0,
                flags = if (rankThree) FieldFlags.RANK_III else 0, power = values[STORM_COIL_DAMAGE] * skillPower(id),
            )
            values[STORM_COIL_RADIUS]
        }
        SkillId.GRAVITY_WELL -> {
            addField(
                CombatFieldKind.GRAVITY_WELL, FieldSource.SKILL, slot, anchorX, anchorY, radius = values[GRAVITY_WELL_RADIUS],
                seconds = values[GRAVITY_WELL_SECONDS], damagePerSecond = values[GRAVITY_WELL_DPS] * skillPower(id),
                element = attuned(StatusElement.GRAVITIC), stacksPerTick = 1, generation = 0, pull = values[GRAVITY_WELL_PULL],
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
        SkillId.DECAY_SPORES -> castDecaySpores(slot, values, anchorX, anchorY, runtime)
        SkillId.ENTROPIC_TIDE -> castEntropicTide(slot, id, values, rankThree)
        SkillId.SONIC_BOOM -> castSonicBoom(id, values, aimX, aimY, rankThree)
        SkillId.RESONATOR -> {
            // The tick clock starts on the beat grid, so pulses land on global beats.
            val nextBeat = (floor(elapsed / BEAT_PERIOD) + 1f) * BEAT_PERIOD
            addField(
                CombatFieldKind.ECHO_RING, FieldSource.SKILL, slot, anchorX, anchorY, radius = values[RESONATOR_RADIUS],
                seconds = values[RESONATOR_SECONDS], damagePerSecond = 0f, element = attuned(StatusElement.SONIC),
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
        SkillId.SLINGSHOT -> castSlingshot(slot, values, aimX, aimY, runtime)
        SkillId.FULL_STOP -> castFullStop(slot, id, values, rankThree)
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

private fun MutableGameState.castSolarFlare(slot: Int, id: SkillId, values: List<Float>, rankThree: Boolean): Float {
    val vented = min(heat, values[SOLAR_VENT_CAP])
    heat -= vented
    val radius = values[SOLAR_RADIUS] * selfSkillRadiusScale()
    val damage = (values[SOLAR_DAMAGE] + values[SOLAR_DAMAGE_PER_HEAT] * vented) * skillPower(id)
    val scorch = min(SOLAR_SCORCH_CAP, values[SOLAR_SCORCH].toInt() + floor(vented / SOLAR_HEAT_PER_SCORCH).toInt())
    val element = attuned(StatusElement.THERMAL)
    forEachEnemyWithin(coreX, coreY, radius) { enemy ->
        skillHit(enemy, damage, element)
        if (enemy.alive) applyStatus(enemy, element, scorch, 0)
        if (rankThree && enemy.alive &&
            stacks(enemy, element) >= breakThreshold(element, enemy) - SKILL_BREAK_MARGIN
        ) forceElementBreak(enemy, element)
    }
    shockwave(coreX, coreY, 0.35f, radius, FxColorCode.elementTone(StatusElement.THERMAL))
    prepareGhost(slot, damage, radius)
    return radius
}

private fun MutableGameState.castFrostLine(slot: Int, values: List<Float>, aimX: Float, aimY: Float, rankThree: Boolean): Float {
    val half = values[FROST_LINE_LENGTH] * 0.5f
    val centreX = coreX + aimX * FROST_LINE_AHEAD
    val centreY = coreY + aimY * FROST_LINE_AHEAD
    // Perpendicular to the aim.
    addField(
        CombatFieldKind.FROST_LINE, FieldSource.SKILL, slot, centreX - aimY * half, centreY + aimX * half,
        radius = values[FROST_LINE_HALF_WIDTH], seconds = values[FROST_LINE_SECONDS], damagePerSecond = 0f,
        element = attuned(StatusElement.CRYO), stacksPerTick = values[FROST_LINE_CHILL].toInt(), generation = 0,
        x2 = centreX + aimY * half, y2 = centreY - aimX * half,
        flags = if (rankThree) FieldFlags.RANK_III else 0, power = values[FROST_LINE_BOLTS],
    )
    return half
}

private fun MutableGameState.castFrostSnap(
    slot: Int, id: SkillId, values: List<Float>, runtime: SkillRuntime, rankThree: Boolean,
): Float {
    val radius = values[FROST_SNAP_RADIUS] * selfSkillRadiusScale()
    val shatter = impactPower(max(speed, FROST_SNAP_MIN_SPEED)) * values[FROST_SNAP_SHATTER] * kineticSkillScale(id)
    val element = attuned(StatusElement.CRYO)
    forEachEnemyWithin(coreX, coreY, radius) { enemy ->
        val brittle = enemy.status[StatusCell.BRITTLE] > 0f
        if (isFrozen(enemy) || brittle) {
            if (brittle) enemy.status[StatusCell.BRITTLE] = 0f
            skillHit(enemy, shatter, null, kinetic = true)
        }
        if (enemy.alive) applyStatus(enemy, element, values[FROST_SNAP_CHILL].toInt(), 0)
        if (rankThree && enemy.alive &&
            stacks(enemy, element) >= breakThreshold(element, enemy) - SKILL_BREAK_MARGIN
        ) forceElementBreak(enemy, element)
    }
    runtime[slot, SkillCell.AUX_UNTIL] = elapsed + values[FROST_SNAP_COILED_SECONDS]
    shockwave(coreX, coreY, 0.3f, radius, FxColorCode.elementTone(StatusElement.CRYO))
    prepareGhost(slot, shatter, radius)
    return radius
}

private fun MutableGameState.castArcHook(
    slot: Int, values: List<Float>, target: Enemy?, runtime: SkillRuntime, rankThree: Boolean,
): Float {
    val hooked = target ?: return 0f
    applyStatus(hooked, attuned(StatusElement.ION), values[ARC_HOOK_CHARGE].toInt(), 0)
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
    return distance
}

private fun MutableGameState.castDecaySpores(
    slot: Int, values: List<Float>, anchorX: Float, anchorY: Float, runtime: SkillRuntime,
): Float {
    val radius = values[SPORES_RADIUS]
    runtime[slot, SkillCell.STORED] = 0f
    runtime[slot, SkillCell.AUX_COUNT] = 0f
    val element = attuned(StatusElement.ENTROPY)
    forEachEnemyWithin(anchorX, anchorY, radius) { enemy ->
        applyStatus(enemy, element, values[SPORES_DECAY].toInt(), 0)
        if (enemy.alive) {
            setMark(enemy, MarkKind.SPORED, values[SPORES_SECONDS], cast = true)
            noteCastTarget(enemy)
        }
    }
    shockwave(anchorX, anchorY, 0.35f, radius, FxColorCode.elementTone(StatusElement.ENTROPY))
    return radius
}

private fun MutableGameState.castEntropicTide(slot: Int, id: SkillId, values: List<Float>, rankThree: Boolean): Float {
    val radius = values[TIDE_RADIUS] * selfSkillRadiusScale()
    val scale = values[TIDE_SCALE] * kineticSkillScale(id)
    var consumed = 0
    forEachEnemyWithin(coreX, coreY, radius) { enemy ->
        val decay = stacks(enemy, StatusElement.ENTROPY)
        if (decay <= 0) return@forEachEnemyWithin
        val amount = detonateDecayAmount(enemy, StatusElement.ENTROPY) * scale
        enemy.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 0f
        consumed += decay
        skillHit(enemy, amount, attuned(StatusElement.ENTROPY))
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
    return radius
}

private fun MutableGameState.castSonicBoom(id: SkillId, values: List<Float>, aimX: Float, aimY: Float, rankThree: Boolean): Float {
    val range = values[BOOM_RANGE]
    val damage = values[BOOM_DAMAGE] * skillPower(id)
    val cone = cos(BOOM_HALF_ANGLE_DEGREES * RADIANS_PER_DEGREE)
    val element = attuned(StatusElement.SONIC)
    forEachEnemyWithin(coreX, coreY, range) { enemy ->
        if (!inCone(enemy.x, enemy.y, aimX, aimY, cone)) return@forEachEnemyWithin
        skillHit(enemy, damage, element)
        if (!enemy.alive) return@forEachEnemyWithin
        applyStatus(enemy, element, values[BOOM_RESONANCE].toInt(), 0)
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
    return range
}

private fun MutableGameState.castSlingshot(slot: Int, values: List<Float>, aimX: Float, aimY: Float, runtime: SkillRuntime): Float {
    var swung = (speed * values[SLING_SCALE]).coerceIn(values[SLING_MIN_SPEED], values[SLING_MAX_SPEED])
    // The swing never flings the Core into a rock: it slows to what keeps the next moments' path clear.
    swung = min(swung, blinkRangeClearOfObstacles(aimX, aimY, swung * SLING_CLEAR_SECONDS) / SLING_CLEAR_SECONDS)
    velocityX = aimX * swung
    velocityY = aimY * swung
    runtime[slot, SkillCell.ACTIVE_UNTIL] = elapsed + values[SLING_WINDOW]
    fireTurnEdge()
    return 0f
}

private fun MutableGameState.castFullStop(slot: Int, id: SkillId, values: List<Float>, rankThree: Boolean): Float {
    val impact = impactPower(speed) * values[FULL_STOP_SCALE] * kineticSkillScale(id)
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
    return radius
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
internal fun MutableGameState.meteorLanding(slot: Int) {
    val runtime = skillRuntime
    val rank = skillLoadout.rank(slot)
    val values = content.skill(SkillId.METEOR).rank(rank).values
    val x = runtime[slot, SkillCell.ANCHOR_X]
    val y = runtime[slot, SkillCell.ANCHOR_Y]
    val radius = values[METEOR_RADIUS]
    val damage = values[METEOR_DAMAGE] * skillPower(SkillId.METEOR)
    val element = attuned(StatusElement.THERMAL)
    forEachEnemyWithin(x, y, radius) { enemy ->
        skillHit(enemy, damage, element)
        if (enemy.alive) applyStatus(enemy, element, values[METEOR_SCORCH].toInt(), 0)
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
    // The step lands clear of rocks, so the speed it keeps does not carry the Core straight into one.
    while (range > 0f && !landingClearOfObstacles(coreX + aimX * range, coreY + aimY * range, coreRadius + VOID_STEP_OBSTACLE_GAP)) {
        range = max(0f, range - VOID_STEP_OBSTACLE_STEP)
    }
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
            applyStatus(enemy, attuned(StatusElement.VOID), 1, 0)
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

/**
 * True when an obstacle comes within [HOOK_OBSTACLE_CLEARANCE] of the hook's line to [target] or of the
 * [HOOK_OVERSHOOT] the Core carries past it, so the hook would fling the Core into the rock.
 */
internal fun MutableGameState.hookLineBlocked(target: Enemy): Boolean {
    val dx = target.x - coreX
    val dy = target.y - coreY
    val distance = max(1f, length(dx, dy))
    val endX = target.x + dx / distance * HOOK_OVERSHOOT
    val endY = target.y + dy / distance * HOOK_OVERSHOOT
    return obstacleBlocksSegment(coreX, coreY, endX, endY, coreRadius + HOOK_OBSTACLE_CLEARANCE)
}

/** True when no obstacle surface lies within [clearance] of ([x], [y]). */
private fun MutableGameState.landingClearOfObstacles(x: Float, y: Float, clearance: Float): Boolean {
    forEachObstacleNear(x, y, clearance) { _, obstacle ->
        if (distanceSquared(x, y, obstacle.x, obstacle.y) < square(obstacle.radius + clearance)) return false
    }
    return wallsClearOf(x, y, clearance)
}

/** The Void Step rift detonates where the step began. */
internal fun MutableGameState.voidRift(slot: Int) {
    val runtime = skillRuntime
    val rank = skillLoadout.rank(slot)
    val values = content.skill(SkillId.VOID_STEP).rank(rank).values
    val x = runtime[slot, SkillCell.ANCHOR_X]
    val y = runtime[slot, SkillCell.ANCHOR_Y]
    val radius = values[VOID_STEP_RIFT_RADIUS]
    val damage = values[VOID_STEP_RIFT_DAMAGE] * skillPower(SkillId.VOID_STEP)
    val element = attuned(StatusElement.VOID)
    forEachEnemyWithin(x, y, radius) { enemy ->
        skillHit(enemy, damage, element)
        if (enemy.alive) applyStatus(enemy, element, values[VOID_STEP_HOLLOW].toInt(), 0)
    }
    shockwave(x, y, 0.3f, radius, FxColorCode.elementTone(StatusElement.VOID))
    emitVisualFx(VisualFxCue.SkillCast(SkillId.VOID_STEP, x, y, 0f, 0f, radius, rank, SkillCueCodes.PHASE_IMPACT))
}

/** Null Shroud swallows the hostile bolts touching the Core's shroud, up to its store. */
internal fun MutableGameState.swallowShots(slot: Int) {
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
internal fun MutableGameState.releaseShroud(slot: Int) {
    val runtime = skillRuntime
    val rank = skillLoadout.rank(slot)
    val values = content.skill(SkillId.NULL_SHROUD).rank(rank).values
    val stored = runtime[slot, SkillCell.STORED].toInt()
    runtime[slot, SkillCell.STORED] = 0f
    if (stored <= 0) return
    val targets = pickNearestEnemies(coreX, coreY, values[SHROUD_RANGE], SHROUD_RELEASE_TARGETS)
    if (targets == 0) return
    val damage = values[SHROUD_DAMAGE] * skillPower(SkillId.NULL_SHROUD)
    val pierce = if (rank >= ContentBounds.MAX_SKILL_RANK) 1 else 0
    for (shot in 0 until stored) {
        val target = enemyById(skillScratch[shot % targets]) ?: continue
        val angle = kotlin.math.atan2(target.y - coreY, target.x - coreX)
        addProjectile(Projectile(
            x = coreX, y = coreY, vx = cos(angle) * SHROUD_SHOT_SPEED, vy = sin(angle) * SHROUD_SHOT_SPEED,
            radius = 5f, life = 2f, hostile = false, damage = damage, pierce = pierce, colorIndex = 2,
            statusElement = attuned(StatusElement.VOID).ordinal, procKey = WeaponRuntime.PROC_SKILL,
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

internal fun MutableGameState.ghostRepeat(slot: Int, id: SkillId) {
    if (!ghostActive) return
    val runtime = skillRuntime
    val x = ghostX
    val y = ghostY
    val radius = runtime[slot, SkillCell.GHOST_RADIUS]
    val stored = runtime[slot, SkillCell.GHOST_DAMAGE]
    forEachEnemyWithin(x, y, radius) { enemy ->
        when (id) {
            SkillId.SOLAR_FLARE -> skillHit(enemy, stored * GHOST_REPEAT_SHARE, attuned(StatusElement.THERMAL))
            SkillId.FROST_SNAP -> if (isFrozen(enemy) || enemy.status[StatusCell.BRITTLE] > 0f) {
                skillHit(enemy, stored * GHOST_REPEAT_SHARE, null, kinetic = true)
            }
            SkillId.FULL_STOP -> skillHit(enemy, stored * GHOST_REPEAT_SHARE, null, kinetic = true)
            // The Tide's repeat reads the Decay now on the enemy and consumes none of it.
            SkillId.ENTROPIC_TIDE -> skillHit(enemy, detonateDecayAmount(enemy, StatusElement.ENTROPY) * stored * GHOST_REPEAT_SHARE,
                attuned(StatusElement.ENTROPY))
            else -> Unit
        }
    }
    shockwave(x, y, 0.25f, radius, 2)
}
