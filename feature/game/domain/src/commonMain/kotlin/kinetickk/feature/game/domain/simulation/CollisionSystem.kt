// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

import kinetickk.feature.game.domain.model.*
import kinetickk.feature.game.domain.protocol.SoundCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


internal fun MutableGameState.resolveCollisions(delta: Float) {
    resolveEnemyCoreCollisions()
    if (phase != GamePhase.RUNNING) return
    resolveWeaponDamage(delta)
    if (phase != GamePhase.RUNNING) return
    resolveProjectileHits()
    if (phase != GamePhase.RUNNING) return
    resolvePickupCollection()
    val killed = enemies.filter { it.dead || it.hp <= 0f }
    killed.forEach(::onEnemyKilled)
    enemies.removeAll { it.dead || it.hp <= 0f }
}

internal fun MutableGameState.resolveEnemyCoreCollisions() {
    for (enemy in enemies) {
        if (phase != GamePhase.RUNNING) return
        val combined = MutableGameState.CORE_RADIUS + enemy.radius
        val sweptHit = segmentCircleIntersects(
            previousCoreX - enemy.previousX,
            previousCoreY - enemy.previousY,
            coreX - enemy.x,
            coreY - enemy.y,
            0f,
            0f,
            combined,
        )
        if (enemy.contactCooldown <= 0f && sweptHit) {
            val relativeX = velocityX - enemy.vx
            val relativeY = velocityY - enemy.vy
            val impactSpeed = length(relativeX, relativeY)
            val dx = coreX - enemy.x
            val dy = coreY - enemy.y
            val distance = max(1f, length(dx, dy))
            val coreImpactSpeed = kotlin.math.abs(velocityX * dx / distance + velocityY * dy / distance)
            val damage = max(
                6f,
                mass * softVelocity(coreImpactSpeed) * 0.115f * damageMultiplier *
                    rebirthProfile.playerPowerMultiplier,
            )
            damageEnemy(enemy, damage, canCrit = true, relicKillProcsEligible = true)
            lastImpact = damage
            lastImpactTime = 0.72f
            enemy.contactCooldown = 0.26f
            screenShake = min(15f, 3f + softVelocity(impactSpeed) * 0.018f)
            velocityX += dx / distance * min(220f, softVelocity(impactSpeed) * 0.28f)
            velocityY += dy / distance * min(220f, softVelocity(impactSpeed) * 0.28f)
            overdriveCharge = min(125f, overdriveCharge + min(12f, coreImpactSpeed / 100f) * overdriveGain)
            shockwave(enemy.x, enemy.y, 0.26f, min(150f, 55f + damage * 0.9f), 3)
            directionalBurst(enemy.x, enemy.y, 8, 3, dx / distance, dy / distance)
            emitSound(SoundCue.IMPACT)
            if (coreImpactSpeed < 190f && dashPhaseTime <= 0f) takeDamage(7f + enemy.radius * 0.18f)
        }
    }
}

internal fun MutableGameState.resolveWeaponDamage(delta: Float) {
    val power = effectiveWeaponPower()
    val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
    when (weapon) {
        WeaponId.FLUX_WAKE -> enemies.forEach { enemy ->
            val touching = trail.any { point ->
                point.age < 1.8f + agonyRank * 0.18f &&
                    distanceSquared(point.x, point.y, enemy.x, enemy.y) < square(enemy.radius + 20f + agonyRank * 4f)
            }
            if (touching) {
                dealWeaponDamage(
                    enemy,
                    (12f + softVelocity(speed) * 0.012f) * delta * power * (1f + agonyRank * 0.10f),
                    cadence = WeaponHitCadence.CONTINUOUS,
                )
            }
        }
        WeaponId.MORNINGSTAR -> enemies.forEach { enemy ->
            val hitRadius = enemy.radius + 24f
            val primaryHit = distanceSquared(morningstarX, morningstarY, enemy.x, enemy.y) < hitRadius * hitRadius
            val mirrorX = coreX * 2f - morningstarX
            val mirrorY = coreY * 2f - morningstarY
            val forbiddenHit = agonyRank > 0 && distanceSquared(mirrorX, mirrorY, enemy.x, enemy.y) < hitRadius * hitRadius
            if (enemy.weaponCooldown <= 0f && (primaryHit || forbiddenHit)) {
                val forbiddenScale = if (!primaryHit && forbiddenHit) 0.55f + 0.08f * agonyRank else 1f
                dealWeaponDamage(enemy, (38f + softVelocity(speed) * 0.1f) * mass * power * forbiddenScale, canCrit = true)
                enemy.weaponCooldown = 0.2f
                screenShake = max(screenShake, 5f)
            }
        }
        WeaponId.PHASE_LATTICE -> enemies.forEach { enemy ->
            val ring = distanceSquared(coreX, coreY, enemy.x, enemy.y)
            if (ring in square(82f)..square(138f)) dealWeaponDamage(enemy, 19f * delta * power, cadence = WeaponHitCadence.CONTINUOUS)
            if (agonyRank > 0 && ring in square(158f)..square(192f + agonyRank * 4f)) {
                dealWeaponDamage(enemy, (8f + 3f * agonyRank) * delta * power, cadence = WeaponHitCadence.CONTINUOUS)
            }
        }
        WeaponId.RIFT_BLADES -> enemies.forEach { enemy ->
            if (enemy.weaponCooldown <= 0f && weaponOrbitals.any {
                    distanceSquared(it.x, it.y, enemy.x, enemy.y) <= square(it.radius + enemy.radius)
                }
            ) {
                dealWeaponDamage(enemy, (31f + softVelocity(speed) * 0.025f) * power, canCrit = true)
                enemy.weaponCooldown = 0.17f
            }
        }
        WeaponId.ENTROPY_FIELD -> enemies.forEach { enemy ->
            val radius = 170f + weaponLevel * 5f + agonyRank * 18f
            if (distanceSquared(coreX, coreY, enemy.x, enemy.y) <= square(radius + enemy.radius)) {
                val collapse = agonyRank > 0 && enemy.hp <= enemy.maxHp * (0.06f + agonyRank * 0.015f)
                dealWeaponDamage(
                    enemy,
                    if (collapse) enemy.hp else 24f * delta * power * (1f + agonyRank * 0.08f),
                    cadence = WeaponHitCadence.CONTINUOUS,
                )
                enemy.vx *= exp(-0.5f * delta)
                enemy.vy *= exp(-0.5f * delta)
            }
        }
        else -> Unit
    }
}

internal fun MutableGameState.resolveProjectileHits() {
    val hostileIterator = projectiles.iterator()
    while (hostileIterator.hasNext()) {
        if (phase != GamePhase.RUNNING) break
        val projectile = hostileIterator.next()
        if (!projectile.hostile) continue
        val hitRadius = MutableGameState.CORE_RADIUS + projectile.radius
        val hit = segmentCircleIntersects(
            projectile.previousX - previousCoreX,
            projectile.previousY - previousCoreY,
            projectile.x - coreX,
            projectile.y - coreY,
            0f,
            0f,
            hitRadius,
        )
        if (hit) {
            hostileIterator.remove()
            if (dashPhaseTime <= 0f) {
                takeDamage(12f)
                screenShake = max(screenShake, 6f)
            } else {
                grantMatter(1f)
                burst(projectile.x, projectile.y, 5, 2)
            }
        }
    }

    val friendlyIterator = projectiles.iterator()
    while (friendlyIterator.hasNext()) {
        val projectile = friendlyIterator.next()
        if (projectile.hostile) continue
        var consumed = false
        for (enemy in enemies) {
            if (enemy.id in projectile.hitEnemyIds || enemy.dead) continue
            val hitRadius = projectile.radius + enemy.radius
            val hit = segmentCircleIntersects(
                projectile.previousX - enemy.previousX,
                projectile.previousY - enemy.previousY,
                projectile.x - enemy.x,
                projectile.y - enemy.y,
                0f,
                0f,
                hitRadius,
            )
            if (hit) {
                projectile.hitEnemyIds += enemy.id
                val sourceWeapon = projectile.sourceWeapon
                if (sourceWeapon != null) {
                    dealWeaponDamage(enemy, projectile.damage, canCrit = true, sourceWeapon = sourceWeapon)
                } else {
                    damageEnemy(enemy, projectile.damage, canCrit = true)
                }
                burst(enemy.x, enemy.y, 4, projectile.colorIndex)
                projectile.pierce--
                if (projectile.pierce < 0) {
                    consumed = true
                    break
                }
                if (projectile.sourceWeapon == WeaponId.PRISM_RELAY) {
                    redirectPrismRelay(projectile)
                    break
                }
            }
        }
        if (consumed) friendlyIterator.remove()
    }
}

internal fun MutableGameState.redirectPrismRelay(projectile: Projectile) {
    val target = enemies.asSequence()
        .filter { !it.dead && it.id !in projectile.hitEnemyIds }
        .filter { distanceSquared(projectile.x, projectile.y, it.x, it.y) <= square(720f) }
        .minByOrNull { distanceSquared(projectile.x, projectile.y, it.x, it.y) }
        ?: return
    val angle = atan2(target.y - projectile.y, target.x - projectile.x)
    val projectileSpeed = max(520f, length(projectile.vx, projectile.vy))
    projectile.vx = cos(angle) * projectileSpeed
    projectile.vy = sin(angle) * projectileSpeed
    projectile.previousX = projectile.x
    projectile.previousY = projectile.y
    projectile.life = max(projectile.life, 0.9f)
    addWeaponArc(projectile.x, projectile.y, target.x, target.y, 0.08f)
}

internal fun MutableGameState.resolvePickupCollection() {
    val iterator = pickups.iterator()
    while (iterator.hasNext()) {
        val pickup = iterator.next()
        if (segmentCircleIntersects(
                previousCoreX - pickup.previousX,
                previousCoreY - pickup.previousY,
                coreX - pickup.x,
                coreY - pickup.y,
                0f,
                0f,
                34f,
            )
        ) {
            iterator.remove()
            when (pickup.type) {
                PickupType.DATA -> gainData(dataGain)
                PickupType.KEY -> {
                    keys++
                    message = "ELITE KEY ACQUIRED"
                    messageTime = 1.4f
                }
                PickupType.REPAIR -> hp = min(maxHp, hp + 22f)
                PickupType.RELIC -> {
                    pendingRelicChoices++
                    message = "RELIC RESONANCE"
                    messageTime = 1.4f
                }
            }
            burst(pickup.x, pickup.y, 7, if (pickup.type == PickupType.KEY || pickup.type == PickupType.RELIC) 3 else 1)
            emitSound(SoundCue.PICKUP)
            openNextPendingChoice()
        }
    }
}

internal fun MutableGameState.gainData(amount: Float) {
    dataFraction += amount
    val whole = floor(dataFraction).toInt()
    if (whole <= 0) return
    dataFraction -= whole
    data += whole
    while (data >= nextLevelData) {
        data -= nextLevelData
        level++
        pendingLevelChoices++
        nextLevelData = 18 + level * 8 + (level * level) / 8
    }
    openNextPendingChoice()
}
