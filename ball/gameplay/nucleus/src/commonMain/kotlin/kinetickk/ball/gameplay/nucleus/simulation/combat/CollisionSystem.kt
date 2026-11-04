// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*


internal fun MutableGameState.resolveCollisions(delta: Float) {
    resolveCollisionPasses(delta)
    // Shots a reaction spent during these passes (Stasis) leave the list only once no pass is walking it.
    projectiles.removeMatchingStable { projectile -> projectile.spent }
}

private fun MutableGameState.resolveCollisionPasses(delta: Float) {
    // Shots stopped by an asteroid are spent before they can hit anything beyond it.
    blockProjectilesAtObstacles()
    blockProjectilesAtWalls()
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
    // Matter granted by kills resolved after the terminal bank in this sweep still counts. A choice a kill opened is not
    // the run's end: banking then would leave signals pricing only the Matter earned after it.
    if (phase == GamePhase.GAME_OVER || phase == GamePhase.VICTORY) bankRunMatter()
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
        if (!(enemy.contactCooldown <= 0f) || enemy.dead || !(enemy.hp > 0f)) continue
        // A Phantom out of phase is not there to hit.
        if (isPhasedPhantom(enemy)) continue
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
        if (sweptHit) {
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
            val characterMultiplier = characterImpactMultiplier(enemy)
            val damage = impactPower(coreImpactSpeed) * characterMultiplier
            val defusable = enemy.type == EnemyType.BOMBER && enemy.mode != EnemyBehavior.PRIMED
            val edgeCritical = edgeCriticalReady(enemy)
            val impact = dealHit(enemy, damage, HitSource.IMPACT, canCrit = true, forceCrit = edgeCritical)
            if (edgeCritical) spendEdgeCritical(enemy)
            // Ablaze: while the Core burns, every contact sets the enemy burning.
            onAblazeContact(enemy)
            onSkillCoreImpact(enemy, coreImpactSpeed, damage, dashContact = newDashContact)
            onCharacterImpact(enemy, impact, coreImpactSpeed, characterMultiplier)
            onWeaponImpact(coreImpactSpeed, damage)
            onEnemyCoreImpact(enemy, coreImpactSpeed)
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
            // Glass Charge: an Embrittled enemy cannot stop a fast Core (Glass Burst: it shatters as you pass).
            val pierced = piercesEmbrittled(enemy, coreImpactSpeed)
            if (pierced) glassBurst(enemy, damage)
            val keepsMomentum = tagTier(BuildTag.RAM) >= 3 && speed >= 1_200f || pierced || slingshotKeepsMomentum()
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

internal fun MutableGameState.resolveProjectileHits() {
    val needsLiveEnemyIds = resolveHostileProjectileHits()
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
    resolveFriendlyProjectileHits(liveEnemyIds, liveEnemyCount)
}

/** Hostile shots against the Core (parry, damage or a Dash phase); returns whether a friendly shot keeps enemy hits to prune. */
private fun MutableGameState.resolveHostileProjectileHits(): Boolean {
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
    return needsLiveEnemyIds
}

/** Friendly shots against enemies, pruning hit histories to [liveEnemyIds] (the first [liveEnemyCount], sorted) when given. */
private fun MutableGameState.resolveFriendlyProjectileHits(liveEnemyIds: IntArray?, liveEnemyCount: Int) {
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
            if (enemy.dead || isShifting(enemy)) continue
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
            // A hit history may span many enemies. Only geometrically overlapping pairs need
            // its lookup; the broad phase rejects the far more numerous misses cheaply.
            if (hit && !projectile.hasRecordedEnemyHit(enemy.id)) {
                // An Apex shot with no strike left passes through boss-class enemies without spending itself.
                if (projectile.procKey == WeaponRuntime.PROC_APEX && apexShotPassesThrough(enemy)) continue
                if (!projectile.tryRecordEnemyHit(enemy.id)) continue
                // A directional guard (the Reflector's front, a Throne's arcs) meets shots by where they come from, not
                // where the Core is.
                val shieldBefore = enemy.guarded
                val directional = enemy.type.profile.directionalGuard
                val guardFaces = directional && shieldFaces(enemy, projectile.previousX, projectile.previousY)
                if (directional) enemy.guarded = guardFaces
                val sourceWeapon = projectile.sourceWeapon
                if (sourceWeapon != null) {
                    weaponShotHit(projectile, sourceWeapon, enemy)
                } else if (projectile.procKey == WeaponRuntime.PROC_SKILL) {
                    skillShotHit(enemy, projectile.damage, projectile.statusElement)
                } else if (projectile.procKey == WeaponRuntime.PROC_APEX) {
                    apexShotHit(enemy, projectile.damage, projectile.statusElement)
                } else {
                    // Relic shots deal element-less damage; a status element is a rider applied after it.
                    dealProcDamage(enemy, projectile.damage)
                    if (projectile.statusElement >= 0 && enemy.alive) {
                        applyStatus(enemy, kinetickk.ball.content.api.StatusElement.entries[projectile.statusElement], 2, 1)
                    }
                }
                if (directional) enemy.guarded = shieldBefore
                burst(enemy.x, enemy.y, 4, projectile.colorIndex)
                if (guardFaces) {
                    // The guard stops the shot; only the Reflector may send one back.
                    if (enemy.type == EnemyType.REFLECTOR) reflectShot(enemy)
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
            if (projectile.sourceWeapon == WeaponId.QUASAR_CANNON) quasarShotEnded(projectile)
            projectiles.removeAt(friendlyIndex)
        } else {
            friendlyIndex++
        }
    }
}

internal fun MutableGameState.redirectPrismRelay(projectile: Projectile) {
    prismRelayBounced(projectile)
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
                    dataPickupRechargesSkills()
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
