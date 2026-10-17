// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal const val STASIS_RADIUS: Float = 240f
internal const val STASIS_MAX_BANK: Int = 24
internal const val EVENT_HORIZON_RADIUS: Float = 280f
internal const val VERDANT_RADIUS: Float = 320f
internal const val VERDANT_CAP: Float = 3.5f
internal const val OUROBOROS_LOCK_SECONDS: Float = 120f
internal const val GLASS_INVULNERABILITY_SECONDS: Float = 0.25f
internal const val GLASS_INVULNERABILITY_COOLDOWN: Float = 1.5f
internal const val OVERDRIVE_BURNOUT_SECONDS: Float = 3f
internal const val OVERDRIVE_EXTENSION_CAP: Float = 2.5f
internal const val GYRE_ECHO_DELAY: Float = 0.25f

/** The upside value [index] of a bound relic at its rank, scaled by harmony, pairings and Hollow Crown. */
internal fun MutableGameState.relicValue(id: RelicId, index: Int): Float =
    content.relic(id).value(index, relicRank(id)) * build.relicUpside(id)

internal fun MutableGameState.stasisActive(): Boolean =
    build.upside(RelicId.STASIS_ANCHOR) && braking && stasisLockedUntil <= elapsed && stasisMeter > 0f &&
        !(build.pairing(RelicPairingId.CONTRADICTION) && speed >= 400f)

internal fun MutableGameState.stasisRadius(): Float = STASIS_RADIUS

internal fun MutableGameState.stasisSlow(): Float {
    var slow = relicValue(RelicId.STASIS_ANCHOR, 0) / 100f
    if (build.pairing(RelicPairingId.STILL_WATERS)) slow += 0.1f
    return slow.coerceIn(0f, 0.85f)
}

/** Every Overdrive gain goes through here: additive bonuses capped, Burnout blocks gain. */
internal fun MutableGameState.gainOverdrive(amount: Float) {
    if (amount <= 0f || overdriveTime > 0f || overdriveBurnoutUntil > elapsed) return
    overdriveCharge = min(125f, overdriveCharge + amount * overdriveGainFactor())
}

internal fun MutableGameState.overdriveGainFactor(): Float {
    var bonus = overdriveGain - 1f
    if (tagTier(BuildTag.OVERCLOCK) >= 2) bonus += 0.3f
    if (build.upside(RelicId.OVERDRIVE_CASCADE)) bonus += relicValue(RelicId.OVERDRIVE_CASCADE, 0) / 100f
    var factor = (1f + bonus).coerceIn(0f, content.build.policy.overdriveGainCap)
    if (build.liability(RelicId.ABSOLUTE_ZERO)) factor *= 0.6f
    return factor
}

// ---------------------------------------------------------------------------------------------
// Per-step rule runtime.

internal fun MutableGameState.updateRelicRuntime(delta: Float) {
    for (index in relicCooldowns.indices) {
        val cooldown = relicCooldowns[index]
        if (cooldown.toRawBits() != 0) relicCooldowns[index] = max(0f, cooldown - delta)
    }
    if (severedRelics.isNotEmpty() && severedRelics.any { it.until <= elapsed }) {
        severedRelics = severedRelics.filter { it.until > elapsed }
        recompileBuild()
    }
    if (glassCharge > 0f && !build.pairing(RelicPairingId.SHATTERED_AEGIS)) glassCharge = 0f
    sinceTurn += delta
    expireBuffs()
    if (build.upside(RelicId.STORMHEART_CAPACITOR) && speed > 700f && stormCharge < 1f) {
        val rate = if (build.pairing(RelicPairingId.LIGHTNING_ROD) && speed > 1_400f) 1.5f else 1f
        stormCharge = min(1f, stormCharge + delta / 4f * rate)
    }
    if (build.upside(RelicId.STASIS_ANCHOR)) updateStasis(delta)
    if (build.upside(RelicId.EVENT_HORIZON)) {
        val radius = EVENT_HORIZON_RADIUS * if (build.pairing(RelicPairingId.BLACK_STAR)) 1.4f else 1f
        for (index in enemies.indices) {
            val enemy = enemies[index]
            val knockback = knockbackTaken(enemy)
            if (!enemy.alive || knockback <= 0f) continue
            val dx = coreX - enemy.x
            val dy = coreY - enemy.y
            val distance = length(dx, dy)
            if (distance < 1f || distance > radius) continue
            val pull = 260f * (1f - distance / radius * 0.6f) * knockback
            enemy.vx += dx / distance * pull * delta
            enemy.vy += dy / distance * pull * delta
        }
    }
    if (build.upside(RelicId.VERDANT_ROT)) {
        var nearby = 0
        val radiusSquared = square(VERDANT_RADIUS)
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.alive && distanceSquared(enemy.x, enemy.y, coreX, coreY) <= radiusSquared) {
                nearby += stacks(enemy, StatusElement.ENTROPY)
            }
        }
        if (nearby > 0) heal(min(VERDANT_CAP, nearby * relicValue(RelicId.VERDANT_ROT, 0)) * delta, HealSource.VERDANT)
    }
    if (build.liability(RelicId.FURNACE_COVENANT) && heat >= 80f) {
        loseIntegrity(3f * delta)
    }
    if (build.liability(RelicId.AGONY_SCEPTER) && weapon.isContinuous()) {
        val before = heat
        heat = min(MutableGameState.MAX_HEAT, heat + 5f * delta)
        applyOverheatEdge(before)
    }
    if (build.regenerationToDecay > 0f && regenPerSecond > 0f) {
        necroticClock += delta
        if (necroticClock >= 1f) {
            necroticClock -= 1f
            val count = max(1, (build.regenerationToDecay * regenPerSecond).toInt())
            applyStatusAround(coreX, coreY, 220f, StatusElement.ENTROPY, count.toFloat(), generation = 0)
        }
    }
    if (gyreEchoAt >= 0f && elapsed >= gyreEchoAt) {
        gyreEchoAt = -1f
        if (build.pairing(RelicPairingId.ENDLESS_GYRE)) gyreRing(220f, gyreEchoDamage)
    }
    updateEchoHits(delta)
}

private fun MutableGameState.updateStasis(delta: Float) {
    if (stasisActive()) {
        stasisMeter = max(0f, stasisMeter - delta)
        if (stasisMeter <= 0f) stasisLockedUntil = elapsed + 4f
        val radiusSquared = square(stasisRadius())
        projectiles.removeMatchingStable { projectile ->
            val caught = projectile.hostile && !projectile.spent && stasisBank < STASIS_MAX_BANK &&
                distanceSquared(projectile.x, projectile.y, coreX, coreY) <= radiusSquared
            if (caught) stasisBank++
            caught
        }
        if (build.pairing(RelicPairingId.STILL_WATERS)) {
            relicCounters[RelicId.STASIS_ANCHOR.ordinal]++
            if (relicCounters[RelicId.STASIS_ANCHOR.ordinal] % 48 == 0) {
                applyStatusAround(coreX, coreY, stasisRadius(), StatusElement.CRYO, 1f, generation = 1)
            }
        }
    } else if (!braking) {
        stasisMeter = min(MutableGameState.STASIS_SECONDS, stasisMeter + delta)
    }
}

private fun MutableGameState.releaseStasis() {
    val count = stasisBank
    stasisBank = 0
    if (count <= 0) return
    val damage = 20f * runScale() * build.relicUpside(RelicId.STASIS_ANCHOR)
    repeat(count) { shot ->
        val target = nearestOtherEnemy(coreX, coreY, -1, 900f)
        val angle = if (target != null) atan2(target.y - coreY, target.x - coreX) + (shot - count / 2) * 0.06f
        else shot * TAU / count
        addProjectile(Projectile(
            x = coreX, y = coreY, vx = cos(angle) * 820f, vy = sin(angle) * 820f, radius = 5f, life = 2f,
            hostile = false, damage = damage, pierce = 1, colorIndex = 1, sourceWeapon = null,
            statusElement = if (build.pairing(RelicPairingId.STILL_WATERS)) StatusElement.CRYO.ordinal else -1,
        ))
    }
    shockwave(coreX, coreY, 0.3f, stasisRadius(), 1)
}

private fun MutableGameState.updateEchoHits(delta: Float) {
    if (echoHits.isEmpty()) return
    var retained = 0
    for (index in echoHits.indices) {
        val echo = echoHits[index]
        echo.delay -= delta
        if (echo.delay > 0f) {
            if (retained != index) echoHits[retained] = echo
            retained++
            continue
        }
        val target = enemies.firstOrNull { it.id == echo.enemyId && it.alive } ?: continue
        val result = dealHit(target, echo.damage, HitSource.ECHO, canCrit = false)
        if (echo.appliesDominant && target.alive) build.dominant?.let { applyStatus(target, it, 1, 1) }
        if (result.amount > 0f) burst(target.x, target.y, 4, 2)
    }
    while (echoHits.size > retained) echoHits.removeAt(echoHits.lastIndex)
}

/** Thunder Choir's liability: the Core's recoil from its own collisions grows while it is bound or lingering. */
internal fun MutableGameState.impactRecoilScale(): Float =
    if (build.liability(RelicId.THUNDER_CHOIR)) 1f + THUNDER_CHOIR_RECOIL else 1f

/**
 * Null Covenant's liability: maximum integrity −10 % while it is bound or lingering. The cut stays 10 % of
 * the whole ceiling (the lowered one plus the cut), so later raises pay their share too, and it is returned
 * in full when the liability ends; integrity above the lowered ceiling is lost, and returning the ceiling
 * heals nothing.
 */
internal fun MutableGameState.reconcileIntegrityLiability() {
    val active = build.liability(RelicId.NULL_COVENANT)
    if (!active && integrityLiabilityCut == 0f) return
    val ceiling = maxHp + integrityLiabilityCut
    val cut = if (active) ceiling * NULL_COVENANT_INTEGRITY_CUT else 0f
    integrityLiabilityCut = cut
    maxHp = ceiling - cut
    hp = min(hp, maxHp)
}

/**
 * The ceiling has just moved from [before] by [nominal]: Null Covenant's liability takes its share of the
 * new ceiling, and the change that actually reached the ceiling is returned (exactly [nominal] without it).
 */
internal fun MutableGameState.ceilingChangeAfterLiability(before: Float, nominal: Float): Float {
    if (integrityLiabilityCut == 0f) return nominal
    reconcileIntegrityLiability()
    return maxHp - before
}

/** Integrity loss that is not an incoming hit (no shields, no triggers). */
internal fun MutableGameState.loseIntegrity(amount: Float) {
    if (amount <= 0f || invulnerableUntil > elapsed) return
    hp -= amount
    damageTaken += amount.toDouble()
    if (hp <= 0f) resolveLethal()
}

// ---------------------------------------------------------------------------------------------
// Hooks.

internal fun MutableGameState.relicOnDirectHit(enemy: Enemy, result: DamageResult, source: HitSource) {
    if (!enemy.alive && source != HitSource.IMPACT) return
    val impact = source == HitSource.IMPACT || source == HitSource.DASH
    if (impact && build.upside(RelicId.ABSOLUTE_ZERO) && enemy.alive) {
        applyStatus(enemy, StatusElement.CRYO, content.relic(RelicId.ABSOLUTE_ZERO).value(0, 1).toInt(), 0)
    }
    if (impact && build.upside(RelicId.VERDANT_ROT) && enemy.alive) applyStatus(enemy, StatusElement.ENTROPY, 2, 0)
    if (impact && build.pairing(RelicPairingId.BLACK_STAR) && enemy.alive) applyStatus(enemy, StatusElement.GRAVITIC, 1, 0)
    if (build.upside(RelicId.FURNACE_COVENANT) && heat >= 70f && enemy.alive && source != HitSource.ECHO) {
        if (relicCooldowns[RelicId.FURNACE_COVENANT.ordinal] <= 0f) {
            relicCooldowns[RelicId.FURNACE_COVENANT.ordinal] = 0.15f
            applyStatus(enemy, StatusElement.THERMAL, 1, 0)
        }
    }
    if (source == HitSource.IMPACT && build.upside(RelicId.STORMHEART_CAPACITOR) && stormCharge >= 1f) {
        stormCharge = 0f
        val damage = impactPower(speed) * relicValue(RelicId.STORMHEART_CAPACITOR, 0) / 100f
        chain(enemy.x, enemy.y, enemy.id, 5, 420f) { target ->
            dealProcDamage(target, damage)
            if (target.alive) applyStatus(target, StatusElement.ION, 3, 0)
        }
        if (build.pairing(RelicPairingId.LIGHTNING_ROD)) {
            velocityX *= 1.1f
            velocityY *= 1.1f
        }
        emitSound(GameplayAudioCue.OVERDRIVE)
    }
}

internal fun MutableGameState.relicOnKill(enemy: Enemy, source: HitSource?) {
    if (source == HitSource.IMPACT && build.upside(RelicId.GLASS_COMET) && speed > 900f && glassInvulnerabilityReadyAt <= elapsed) {
        glassInvulnerabilityReadyAt = elapsed + GLASS_INVULNERABILITY_COOLDOWN
        invulnerableUntil = max(invulnerableUntil, elapsed + GLASS_INVULNERABILITY_SECONDS)
    }
    if (build.upside(RelicId.OVERDRIVE_CASCADE) && overdriveTime > 0f && overdriveExtension < OVERDRIVE_EXTENSION_CAP) {
        var step = 0.5f
        repeat(min(overdriveKills, 40)) { step *= 0.8f }
        val granted = min(step, OVERDRIVE_EXTENSION_CAP - overdriveExtension)
        overdriveKills++
        overdriveExtension += granted
        overdriveTime += granted
    }
}

/** Dash departure: resolves Blink and dash-time rules. Returns true when the Dash became a Blink. */
internal fun MutableGameState.relicOnDash(directionX: Float, directionY: Float): Boolean {
    relicOnDashDeparture()
    if (!build.upside(RelicId.BLINK_DRIVE)) return false
    var range = relicValue(RelicId.BLINK_DRIVE, 1)
    var strikeMass = 1f
    if (build.pairing(RelicPairingId.HEAVY_BLINK)) {
        range *= 0.65f
        strikeMass = 2f
    }
    // Stop short of the singularity so a Blink can never cross it.
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    val toSingularity = length(targetX - coreX, targetY - coreY)
    val along = targetX - coreX
    val ahead = (along * directionX + (targetY - coreY) * directionY)
    if (ahead > 0f) range = min(range, max(0f, toSingularity - 60f))
    if (range < 30f) return false
    val startX = coreX
    val startY = coreY
    val endX = coreX + directionX * range
    val endY = coreY + directionY * range
    val strike = impactPower(max(speed, 600f)) * strikeMass * relicValue(RelicId.BLINK_DRIVE, 0) / 100f
    val assassin = build.pairing(RelicPairingId.ASSASSINS_LINE)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        if (segmentCircleIntersects(startX, startY, endX, endY, enemy.x, enemy.y, enemy.radius + coreRadius)) {
            // The mark forces the crit of this strike only; dealHit dispatches relic on-hit rules once.
            if (assassin) enemy.status[StatusCell.MARKED_UNTIL] = elapsed + 1f
            dealHit(enemy, strike, HitSource.DASH, canCrit = true)
            if (assassin) {
                enemy.status[StatusCell.MARKED_UNTIL] = 0f
                if (enemy.alive) enemy.status[StatusCell.BRITTLE] = 1f
            }
        }
    }
    addRelicArc(startX, startY, endX, endY)
    coreX = endX
    coreY = endY
    previousCoreX = endX
    previousCoreY = endY
    previousSingularityX = targetX
    previousSingularityY = targetY
    val keep = if (build.pairing(RelicPairingId.FOLD_VECTOR)) 0.55f else 0.35f
    velocityX *= keep
    velocityY *= keep
    shockwave(endX, endY, 0.25f, 120f, 0)
    return true
}

/** Relic rules of every Dash departure, Blink or not. */
internal fun MutableGameState.relicOnDashDeparture() {
    if (build.upside(RelicId.ABSOLUTE_ZERO)) applyStatusAround(coreX, coreY, 140f, StatusElement.CRYO, 3f, generation = 0)
}

internal fun MutableGameState.relicOnBrakeRelease() {
    if (build.upside(RelicId.STASIS_ANCHOR)) releaseStasis()
}

internal fun MutableGameState.relicOnTurn() {
    sinceTurn = 0f
    if (!build.upside(RelicId.GYRE_ENGINE) || gyreReadyAt > elapsed) return
    gyreReadyAt = elapsed + 1f
    val damage = impactPower(speed) * relicValue(RelicId.GYRE_ENGINE, 0) / 100f
    gyreRing(150f, damage)
    if (build.pairing(RelicPairingId.ENDLESS_GYRE) && speed > 1_200f) {
        // The 1 s ring cooldown keeps at most one echo pending.
        gyreEchoAt = elapsed + GYRE_ECHO_DELAY
        gyreEchoDamage = damage * 0.6f
    }
}

private fun MutableGameState.gyreRing(radius: Float, damage: Float) {
    val element = resolvePick(ElementPick.DOMINANT)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        // The ring deals impact damage; the dominant element is a rider, never the damage's element.
        dealProcDamage(enemy, if (element == null) damage * 1.2f else damage)
        if (element != null && enemy.alive) applyStatus(enemy, element, 2, 0)
    }
    shockwave(coreX, coreY, 0.3f, radius, 0)
}

/**
 * Ouroboros only answers integrity deaths; the singularity still ends the run directly. [reason] is the
 * run end message when nothing answers.
 */
internal fun MutableGameState.resolveLethal(reason: String = "CORE FRACTURED") {
    if (hp > 0f) return
    if (trySecondWind()) {
        reviveInvulnerableUntil = invulnerableUntil
        emitSound(GameplayAudioCue.RECOVERED)
        return
    }
    if (build.upside(RelicId.OUROBOROS_SEAL) && !ouroborosSpent) {
        ouroborosSpent = true
        ouroborosLockedUntil = elapsed + OUROBOROS_LOCK_SECONDS
        val fragile = build.pairing(RelicPairingId.FRAGILE_REBIRTH)
        hp = if (fragile) maxHp * 0.5f else maxHp
        if (build.pairing(RelicPairingId.HOLLOW_VOW)) {
            hp = max(1f, maxHp * 0.3f)
            shield = shieldCapacity()
        }
        val nova = relicValue(RelicId.OUROBOROS_SEAL, 0) * runScale() * if (fragile) 2f else 1f
        areaDamage(coreX, coreY, 400f, nova, rescale = false)
        invulnerableUntil = elapsed + 2f * build.relicUpside(RelicId.OUROBOROS_SEAL)
        reviveInvulnerableUntil = invulnerableUntil
        runGrace = max(runGrace, 2f)
        message = "OUROBOROS // REBORN"
        messageTime = 2.4f
        emitSound(GameplayAudioCue.RECOVERED)
        recompileBuild()
        return
    }
    endRun(reason)
}

internal fun MutableGameState.relicOnShieldBreak(absorbed: Float) {
    if (build.upside(RelicId.AEGIS_INVERSION) && aegisNovaReadyAt <= elapsed && absorbed > 0f) {
        aegisNovaReadyAt = elapsed + 3f
        val nova = absorbed * relicValue(RelicId.AEGIS_INVERSION, 0) / 100f * kotlin.math.sqrt(runScale())
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive || distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(220f + enemy.radius)) continue
            dealProcDamage(enemy, nova)
            if (enemy.alive && build.pairing(RelicPairingId.ROT_WARD)) applyStatus(enemy, StatusElement.ENTROPY, 3, 0)
            if (enemy.alive && build.pairing(RelicPairingId.FARADAY_CAGE)) applyStatus(enemy, StatusElement.ION, 3, 0)
        }
        shockwave(coreX, coreY, 0.35f, 220f, 1)
    }
}

/** Records discovery of every named pairing whose two relics are bound. */
internal fun MutableGameState.discoverBoundPairings() {
    val mask = build.pairingMask
    val fresh = mask and discoveredPairingMask.inv()
    if (fresh == 0) return
    discoveredPairingMask = discoveredPairingMask or fresh
    pendingDiscoveredPairingMask = pendingDiscoveredPairingMask or fresh
    val names = content.build.pairings.filter { fresh and (1 shl it.id.ordinal) != 0 }.map {
        (if (it.kind == kinetickk.ball.content.api.RelicPairingKind.CONCORD) "+ " else "− ") + it.name
    }
    emitVisualFx(VisualFxCue.BuildChanged("PAIRING DISCOVERED", names.toImmutableList()))
}
