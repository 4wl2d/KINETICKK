// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


internal fun MutableGameState.resolveCollisions(delta: Float) {
    resolveCollisionPasses(delta)
    // Shots a reaction spent during these passes (Stasis) leave the list only once no pass is walking it.
    projectiles.removeMatchingStable { projectile -> projectile.spent }
}

private fun MutableGameState.resolveCollisionPasses(delta: Float) {
    // Shots stopped by an asteroid are spent before they can hit anything beyond it.
    blockProjectilesAtObstacles()
    resolveEnemyCoreCollisions()
    if (phase != GamePhase.RUNNING) return
    resolveWeaponDamage(delta)
    if (phase != GamePhase.RUNNING) return
    resolveProjectileHits()
    if (phase != GamePhase.RUNNING) return
    // Kills resolve before pickups can open a choice, so an Architect falling in this step still wins.
    sweepKills()
    if (phase != GamePhase.RUNNING) return
    resolvePickupCollection()
}

/**
 * Resolves every enemy at zero integrity. Only enemies resolved by [onEnemyKilled] (marked dead) are
 * removed; anything a death effect, or a later pickup clause, brings to zero stays and resolves in the next
 * running step's sweep, so no kill (and no Architect victory) is ever dropped. Bounded: each enemy resolves
 * at most once.
 */
internal fun MutableGameState.sweepKills() {
    var killedCount = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.dead && enemy.hp <= 0f) killedCount++
    }
    if (killedCount == 0) return
    val killed = arrayOfNulls<Enemy>(killedCount)
    var killedIndex = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.dead && enemy.hp <= 0f) killed[killedIndex++] = enemy
    }
    // The Architect resolves first, so a self-inflicted death later in the same sweep cannot undo a victory.
    for (index in killed.indices) if (checkNotNull(killed[index]).type == EnemyType.ARCHITECT) onEnemyKilled(checkNotNull(killed[index]))
    for (index in killed.indices) if (checkNotNull(killed[index]).type != EnemyType.ARCHITECT) onEnemyKilled(checkNotNull(killed[index]))
    // Matter granted by kills resolved after the terminal bank in this sweep still counts.
    if (phase != GamePhase.RUNNING) bankRunMatter()
    var retainedCount = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.dead) {
            if (retainedCount != index) enemies[retainedCount] = enemy
            retainedCount++
        }
    }
    var index = enemies.lastIndex
    while (index >= retainedCount) {
        enemies.removeAt(index)
        index--
    }
}

internal fun MutableGameState.resolveEnemyCoreCollisions() {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (phase != GamePhase.RUNNING) return
        // A Phantom out of phase is not there to hit.
        if (enemy.type == EnemyType.PHANTOM && isShifting(enemy)) continue
        val combined = coreRadius + enemy.radius
        val sweptHit = segmentCircleIntersects(
            previousCoreX - enemy.previousX,
            previousCoreY - enemy.previousY,
            coreX - enemy.x,
            coreY - enemy.y,
            0f,
            0f,
            combined,
        )
        if (enemy.contactCooldown <= 0f && sweptHit && !enemy.dead && enemy.hp > 0f) {
            val relativeX = velocityX - enemy.vx
            val relativeY = velocityY - enemy.vy
            val impactSpeed = length(relativeX, relativeY)
            val dx = coreX - enemy.x
            val dy = coreY - enemy.y
            val distance = max(1f, length(dx, dy))
            val coreImpactSpeed = kotlin.math.abs(velocityX * dx / distance + velocityY * dy / distance)
            // Read before the hit: a strike or lance in progress hurts whatever the Core's speed.
            val strike = strikeDamage(enemy)
            val wouldHurt = dashPhaseTime <= 0f && (strike > 0f || coreImpactSpeed < 190f)
            // Only a contact that would hurt spends the parry window and grants Edge.
            val parried = wouldHurt && tryCharacterParry(
                enemy.previousX, enemy.previousY, enemy.vx, enemy.vy,
                previousCoreX, previousCoreY,
                damage = if (strike > 0f) strike else contactDamage(enemy),
            )
            val newDashContact = dashPhaseTime > 0f && enemy.lastCharacterDash != characterRuntime.dashSequence
            onCharacterDashContact(enemy)
            val characterMultiplier = characterImpactMultiplier()
            val damage = impactPower(coreImpactSpeed) * characterMultiplier
            val defusable = enemy.type == EnemyType.BOMBER && enemy.mode != EnemyBehavior.PRIMED
            val edgeCritical = edgeCriticalReady()
            val impact = dealHit(enemy, damage, HitSource.IMPACT, canCrit = true, forceCrit = edgeCritical)
            if (edgeCritical) spendEdgeCritical()
            onCharacterImpact(enemy, impact, coreImpactSpeed, characterMultiplier)
            // A fast impact destroys an unprimed Bomber outright, before it can prime, even through a tether.
            if (defusable && enemy.alive && coreImpactSpeed >= BOMBER_DEFUSE_SPEED) {
                executeEnemy(enemy, HitSource.IMPACT)
            }
            if (newDashContact) onDashHit(enemy, damage)
            onCharacterPrimaryHit(enemy, dash = dashPhaseTime > 0f)
            lastImpact = damage
            lastImpactTime = 0.72f
            enemy.contactCooldown = 0.26f
            screenShake = min(15f, 3f + softVelocity(impactSpeed) * 0.018f)
            val keepsMomentum = tagTier(BuildTag.RAM) >= 3 && speed >= 1_200f
            if (!keepsMomentum) {
                val recoil = min(220f, softVelocity(impactSpeed) * 0.28f) / max(1f, effectiveMass() / mass) * impactRecoilScale()
                velocityX += dx / distance * recoil
                velocityY += dy / distance * recoil
            }
            gainOverdrive(min(12f, coreImpactSpeed / 100f))
            shockwave(enemy.x, enemy.y, 0.26f, min(150f, 55f + damage * 0.9f), 3)
            directionalBurst(enemy.x, enemy.y, 8, 3, dx / distance, dy / distance)
            emitSound(GameplayAudioCue.IMPACT)
            if (!parried && dashPhaseTime <= 0f && (strike > 0f || coreImpactSpeed < 190f)) {
                val neutron = if (build.upside(RelicId.NEUTRON_HEART)) 0.5f else 1f
                val contact = if (strike > 0f) strike else contactDamage(enemy)
                takeDamage(contact * neutron, kind = IncomingDamage.CONTACT)
            }
        }
    }
}

internal fun MutableGameState.resolveWeaponDamage(delta: Float) {
    val power = effectiveWeaponPower()
    val agonyRank = agonyPower()
    when (weapon) {
        WeaponId.FLUX_WAKE -> for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            var touching = false
            for (trailIndex in trail.indices) {
                val point = trail[trailIndex]
                if (
                    point.age < 1.8f + agonyRank * 0.18f &&
                    distanceSquared(point.x, point.y, enemy.x, enemy.y) <
                    square(enemy.radius + 20f + agonyRank * 4f)
                ) {
                    touching = true
                    break
                }
            }
            if (touching) {
                dealWeaponDamage(
                    enemy,
                    (12f + softVelocity(speed) * 0.012f) * delta * power * (1f + agonyRank * 0.10f),
                    cadence = WeaponHitCadence.CONTINUOUS,
                )
            }
        }
        WeaponId.MORNINGSTAR -> for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            val hitRadius = enemy.radius + 24f
            val primaryHit = distanceSquared(morningstarX, morningstarY, enemy.x, enemy.y) < hitRadius * hitRadius
            val mirrorX = coreX * 2f - morningstarX
            val mirrorY = coreY * 2f - morningstarY
            val forbiddenHit = agonyRank > 0 && distanceSquared(mirrorX, mirrorY, enemy.x, enemy.y) < hitRadius * hitRadius
            if (enemy.weaponCooldown <= 0f && (primaryHit || forbiddenHit)) {
                val forbiddenScale = if (!primaryHit && forbiddenHit) 0.55f + 0.08f * agonyRank else 1f
                dealWeaponDamage(
                    enemy,
                    (38f + softVelocity(speed) * 0.1f) * effectiveMass() * power * forbiddenScale,
                    canCrit = true,
                    cadence = WeaponHitCadence.CONTACT,
                )
                enemy.weaponCooldown = 0.2f
                screenShake = max(screenShake, 5f)
            }
        }
        WeaponId.PHASE_LATTICE -> for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            val ring = distanceSquared(coreX, coreY, enemy.x, enemy.y)
            if (ring in square(82f)..square(138f)) dealWeaponDamage(enemy, 19f * delta * power, cadence = WeaponHitCadence.CONTINUOUS)
            if (agonyRank > 0 && ring in square(158f)..square(192f + agonyRank * 4f)) {
                dealWeaponDamage(enemy, (8f + 3f * agonyRank) * delta * power, cadence = WeaponHitCadence.CONTINUOUS)
            }
        }
        WeaponId.RIFT_BLADES -> for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            var orbitalHit = false
            if (enemy.weaponCooldown <= 0f) {
                for (orbitalIndex in weaponOrbitals.indices) {
                    val orbital = weaponOrbitals[orbitalIndex]
                    if (
                        distanceSquared(orbital.x, orbital.y, enemy.x, enemy.y) <=
                        square(orbital.radius + enemy.radius)
                    ) {
                        orbitalHit = true
                        break
                    }
                }
            }
            if (orbitalHit) {
                dealWeaponDamage(enemy, (31f + softVelocity(speed) * 0.025f) * power, canCrit = true, cadence = WeaponHitCadence.CONTACT)
                enemy.weaponCooldown = 0.17f
            }
        }
        WeaponId.ENTROPY_FIELD -> for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            val radius = 170f + weaponLevel * 5f + agonyRank * 18f
            if (distanceSquared(coreX, coreY, enemy.x, enemy.y) <= square(radius + enemy.radius)) {
                // Agony's collapse executes outright, past every multiplier and tether; bosses only take the field.
                val collapse = agonyRank > 0 && !enemy.isBoss && !enemy.isMiniBoss && enemy.hp <= enemy.maxHp * (0.06f + agonyRank * 0.015f)
                if (collapse) {
                    executeEnemy(enemy, HitSource.WEAPON)
                } else {
                    dealWeaponDamage(enemy, 24f * delta * power * (1f + agonyRank * 0.08f), cadence = WeaponHitCadence.CONTINUOUS)
                }
                val drag = exp(-0.5f * delta * enemy.knockback)
                enemy.vx *= drag
                enemy.vy *= drag
            }
        }
        else -> Unit
    }
}

internal fun MutableGameState.resolveProjectileHits() {
    var needsLiveEnemyIds = false
    var hostileIndex = 0
    var hostileRemaining = projectiles.size
    while (hostileRemaining > 0) {
        val projectile = projectiles[hostileIndex]
        hostileRemaining--
        if (!projectile.hostile) {
            needsLiveEnemyIds = needsLiveEnemyIds || projectile.hasRecordedEnemyHits
            hostileIndex++
            continue
        }
        if (phase != GamePhase.RUNNING) {
            var scanIndex = hostileIndex + 1
            repeat(hostileRemaining) {
                val remainingProjectile = projectiles[scanIndex++]
                if (!remainingProjectile.hostile && remainingProjectile.hasRecordedEnemyHits) {
                    needsLiveEnemyIds = true
                }
            }
            break
        }
        if (projectile.spent) {
            hostileIndex++
            continue
        }
        if (projectile.kind == ProjectileKind.MINE) {
            if (resolveMine(projectile)) projectiles.removeAt(hostileIndex) else hostileIndex++
            continue
        }
        val hitRadius = coreRadius + projectile.radius
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
            projectiles.removeAt(hostileIndex)
            if (tryCharacterParry(
                    projectile.previousX, projectile.previousY, projectile.vx, projectile.vy,
                    previousCoreX, previousCoreY,
                    damage = if (dashPhaseTime <= 0f) projectile.damage else 0f,
                    projectile = true,
                )
            ) {
                burst(projectile.x, projectile.y, 6, 2)
            } else if (dashPhaseTime <= 0f) {
                // The shot carries the damage its source computed when it fired.
                takeDamage(projectile.damage, kind = IncomingDamage.PROJECTILE)
                screenShake = max(screenShake, 6f)
            } else {
                // Shots phased by an on-beat Pentagon Dash pay no Matter.
                if (!characterRuntime.dashOnBeat) grantMatter(1f)
                burst(projectile.x, projectile.y, 5, 2)
            }
        } else {
            hostileIndex++
        }
    }

    var liveEnemyIds: IntArray? = null
    var liveEnemyCount = 0
    if (needsLiveEnemyIds) {
        val ids = IntArray(enemies.size)
        var idsAreSorted = true
        for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            if (enemy.dead) continue
            if (liveEnemyCount > 0 && ids[liveEnemyCount - 1] > enemy.id) {
                idsAreSorted = false
            }
            ids[liveEnemyCount++] = enemy.id
        }
        if (!idsAreSorted) ids.sort(0, liveEnemyCount)
        liveEnemyIds = ids
    }
    var friendlyIndex = 0
    var friendlyRemaining = projectiles.size
    while (friendlyRemaining > 0) {
        val projectile = projectiles[friendlyIndex]
        friendlyRemaining--
        if (projectile.hostile || projectile.spent) {
            friendlyIndex++
            continue
        }
        if (liveEnemyIds != null && projectile.hasRecordedEnemyHits) {
            projectile.retainLiveEnemyHits(liveEnemyIds, liveEnemyCount)
        }
        var consumed = false
        for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            // Shots pass through a shifting Architect without spending pierce.
            if (enemy.dead || isShifting(enemy) || projectile.hasRecordedEnemyHit(enemy.id)) continue
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
                if (!projectile.tryRecordEnemyHit(enemy.id)) continue
                // A Reflector's front shield meets shots by where they come from, not where the Core is.
                val shieldBefore = enemy.guarded
                val shieldFaces = reflectorShieldFaces(enemy, projectile.previousX, projectile.previousY)
                if (enemy.type == EnemyType.REFLECTOR) enemy.guarded = shieldFaces
                val sourceWeapon = projectile.sourceWeapon
                if (sourceWeapon != null) {
                    dealWeaponDamage(enemy, projectile.damage, canCrit = true, sourceWeapon = sourceWeapon)
                } else {
                    // Relic shots deal element-less damage; a status element is a rider applied after it.
                    dealProcDamage(enemy, projectile.damage)
                    if (projectile.statusElement >= 0 && enemy.alive) {
                        applyStatus(enemy, kinetickk.ball.content.api.StatusElement.entries[projectile.statusElement], 2, 1)
                    }
                }
                if (enemy.type == EnemyType.REFLECTOR) enemy.guarded = shieldBefore
                burst(enemy.x, enemy.y, 4, projectile.colorIndex)
                if (shieldFaces) {
                    // The shield stops the shot and may send one back.
                    reflectShot(enemy)
                    consumed = true
                    break
                }
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
        if (consumed) {
            projectiles.removeAt(friendlyIndex)
        } else {
            friendlyIndex++
        }
    }
}

internal fun MutableGameState.redirectPrismRelay(projectile: Projectile) {
    val maximumDistanceSquared = square(720f)
    var target: Enemy? = null
    var targetDistanceSquared = maximumDistanceSquared
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (enemy.dead || isShifting(enemy) || projectile.hasRecordedEnemyHit(enemy.id)) continue
        val distanceSquared = distanceSquared(projectile.x, projectile.y, enemy.x, enemy.y)
        if (
            distanceSquared < targetDistanceSquared ||
            (target == null && distanceSquared == targetDistanceSquared)
        ) {
            target = enemy
            targetDistanceSquared = distanceSquared
        }
    }
    val selectedTarget = target ?: return
    val angle = atan2(selectedTarget.y - projectile.y, selectedTarget.x - projectile.x)
    val projectileSpeed = max(520f, length(projectile.vx, projectile.vy))
    projectile.vx = cos(angle) * projectileSpeed
    projectile.vy = sin(angle) * projectileSpeed
    projectile.previousX = projectile.x
    projectile.previousY = projectile.y
    projectile.life = max(projectile.life, 0.9f)
    addWeaponArc(projectile.x, projectile.y, selectedTarget.x, selectedTarget.y, 0.08f)
}

internal fun MutableGameState.resolvePickupCollection() {
    var pickupIndex = 0
    var pickupsRemaining = pickups.size
    while (pickupsRemaining > 0) {
        val pickup = pickups[pickupIndex]
        pickupsRemaining--
        if (segmentCircleIntersects(
                previousCoreX - pickup.previousX,
                previousCoreY - pickup.previousY,
                coreX - pickup.x,
                coreY - pickup.y,
                0f,
                0f,
                content.pickups.collectionRadius,
            )
        ) {
            pickups.removeAt(pickupIndex)
            pickupsCollected++
            when (pickup.type) {
                PickupType.DATA -> {
                    pendingDataPickups++
                    onCharacterDataPickup()
                    gainData(dataGain * content.tempo.dataPickupMultiplier * (1f + matrix.pickupValueBonus))
                    fireClauses(EffectTrigger.PICKUP)
                }
                PickupType.KEY -> {
                    keys++
                    keysCollected++
                    message = "ELITE KEY ACQUIRED"
                    messageTime = 1.4f
                }
                PickupType.REPAIR -> heal(22f, HealSource.REPAIR)
                PickupType.RELIC -> {
                    pendingRelicChoices++
                    message = "RELIC RESONANCE"
                    messageTime = 1.4f
                }
            }
            burst(pickup.x, pickup.y, 7, if (pickup.type == PickupType.KEY || pickup.type == PickupType.RELIC) 3 else 1)
            emitSound(GameplayAudioCue.PICKUP)
            openNextPendingChoice()
        } else {
            pickupIndex++
        }
    }
}

internal fun MutableGameState.gainData(rawAmount: Float) {
    val amount = rawAmount * dataGainLiability()
    dataFraction += amount
    val whole = floor(dataFraction).toInt()
    if (whole <= 0) return
    dataFraction -= whole
    data += whole
    dataCollected += whole.toLong()
    val levelBefore = level
    while (data >= nextLevelData) {
        data -= nextLevelData
        level++
        pendingLevelChoices++
        onLevelUp()
        nextLevelData = content.tempo.dataRequiredForLevel(level)
        if (content.tempo.grantsWeaponAmplification(level) &&
            weaponLevel < content.tempo.maximumAutomaticWeaponLevel + matrix.startingWeaponLevels
        ) {
            val before = captureBuildChange()
            amplifyCurrentWeapon()
            emitBuildChange(before, currentWeaponDefinition.name + " mastery advanced")
        }
    }
    if (level > levelBefore) vacuumOnScreenData()
    openNextPendingChoice()
}
