// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

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

internal fun MutableGameState.resolveWeaponDamage(delta: Float) {
    val power = effectiveWeaponPower()
    val agonyRank = agonyPower()
    when (weapon) {
        WeaponId.FLUX_WAKE -> resolveFluxWake(delta, power, agonyRank)
        WeaponId.MORNINGSTAR -> resolveMorningstar(power, agonyRank)
        WeaponId.PHASE_LATTICE -> resolvePhaseLattice(delta, power, agonyRank)
        WeaponId.RIFT_BLADES -> resolveRiftBlades(power)
        WeaponId.ENTROPY_FIELD -> resolveEntropyField(delta, power, agonyRank)
        WeaponId.NULL_LANCE, WeaponId.GRAVITY_MINES, WeaponId.ION_SWARM, WeaponId.ARC_COIL, WeaponId.QUASAR_CANNON,
        WeaponId.SINGULARITY_SPEAR, WeaponId.PRISM_RELAY, WeaponId.GYRE_DISC, WeaponId.SCATTER_BLOOM, WeaponId.TETHER_ARC,
        WeaponId.SENTRY_GRID, WeaponId.HUNTER_SALVO, WeaponId.CONCUSSION_ENGINE, WeaponId.GLACIER_BELL, WeaponId.EVENT_LENS,
        WeaponId.SOLAR_SWEEP, WeaponId.AEGIS_SHARDS, WeaponId.HOLLOW_RIFT, WeaponId.RESONANCE_DRUM -> Unit
    }
    resolveWeaponEntityHits(delta)
}

/**
 * Flux Wake: [FLUX_DPS] per enemy touching a live trail point (no speed term: it scales with speed), Scorch every
 * [FLUX_SCORCH_INTERVAL]. The trail's heat spreads thin over a crowd: with more than [FLUX_FULL_HEAT_ENEMIES]
 * enemies in it at once, they share that many enemies' worth.
 */
private fun MutableGameState.resolveFluxWake(delta: Float, power: Float, agonyRank: Int) {
    val width = FLUX_WIDTH * (if (hasMasteryForm(1)) FLUX_WIDTH_FORM else 1f) + agonyRank * 4f
    val earlyIgnite = hasMasteryForm(3)
    var burning = 0
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (enemy.alive && fluxTrailScale(enemy, width) > 0f) burning++
    }
    val spread = if (burning > FLUX_FULL_HEAT_ENEMIES) FLUX_FULL_HEAT_ENEMIES.toFloat() / burning else 1f
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        val scale = fluxTrailScale(enemy, width)
        if (scale > 0f) {
            val landed = dealWeaponDamage(enemy, FLUX_DPS * delta * power * scale * spread * (1f + agonyRank * 0.10f),
                cadence = WeaponHitCadence.CONTINUOUS).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.THERMAL, FLUX_SCORCH_INTERVAL, extraBeforeBreak = earlyIgnite)
        }
    }
}

/** The strongest live trail point [enemy] touches (a turning trail's is larger), or 0. */
private fun MutableGameState.fluxTrailScale(enemy: Enemy, width: Float): Float {
    var scale = 0f
    for (trailIndex in trail.indices) {
        val point = trail[trailIndex]
        if (point.age < point.life && point.scale > scale &&
            distanceSquared(point.x, point.y, enemy.x, enemy.y) < square(enemy.radius + width)
        ) {
            scale = point.scale
        }
    }
    return scale
}

/** Morningstar: (38 + 0.1 v) per contact (mass is its scaling, so no mass term); the forms add reach, a star and throws. */
private fun MutableGameState.resolveMorningstar(power: Float, agonyRank: Int) {
    val sizeScale = morningstarScale()
    val hitReach = MORNINGSTAR_REACH * sizeScale
    val damage = (MORNINGSTAR_DAMAGE + softVelocity(speed) * MORNINGSTAR_SPEED_SHARE) * power * sizeScale
    val secondStar = hasMasteryForm(2)
    val secondX = coreX - (morningstarX - coreX) * MORNINGSTAR_SECOND_ORBIT
    val secondY = coreY - (morningstarY - coreY) * MORNINGSTAR_SECOND_ORBIT
    val mirrorX = coreX * 2f - morningstarX
    val mirrorY = coreY * 2f - morningstarY
    val knockback = MORNINGSTAR_KNOCKBACK * if (hasMasteryForm(3)) 2f else 1f
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || enemy.weaponCooldown > 0f) continue
        val reach = enemy.radius + hitReach
        val primaryHit = distanceSquared(morningstarX, morningstarY, enemy.x, enemy.y) < reach * reach
        val secondReach = enemy.radius + hitReach * MORNINGSTAR_SECOND_SIZE
        val secondHit = secondStar && distanceSquared(secondX, secondY, enemy.x, enemy.y) < secondReach * secondReach
        val forbiddenHit = agonyRank > 0 && distanceSquared(mirrorX, mirrorY, enemy.x, enemy.y) < reach * reach
        if (!primaryHit && !secondHit && !forbiddenHit) continue
        val scale = when {
            primaryHit -> 1f
            secondHit -> MORNINGSTAR_SECOND_SIZE
            else -> 0.55f + 0.08f * agonyRank
        }
        val sourceX = if (primaryHit) morningstarX else if (secondHit) secondX else mirrorX
        val sourceY = if (primaryHit) morningstarY else if (secondHit) secondY else mirrorY
        dealWeaponDamage(enemy, damage * scale, canCrit = true, cadence = WeaponHitCadence.CONTACT,
            procKey = WeaponRuntime.sourceKey(if (primaryHit) 0 else 1))
        pushEnemy(enemy, sourceX, sourceY, knockback * scale)
        enemy.weaponCooldown = 0.2f
        screenShake = max(screenShake, 5f)
    }
}

/** Phase Lattice: a ring 26/s with Chill every 0.6 s; the Ring signature centres it on the Halo. */
private fun MutableGameState.resolvePhaseLattice(delta: Float, power: Float, agonyRank: Int) {
    val center = phaseLatticeCenter()
    val half = PHASE_LATTICE_HALF_WIDTH * if (hasMasteryForm(1)) 1.35f else 1f
    val inner = square(max(0f, center - half))
    val outer = square(center + half)
    val earlyFreeze = hasMasteryForm(3)
    val outerRing = hasMasteryForm(2)
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        val ring = distanceSquared(coreX, coreY, enemy.x, enemy.y)
        if (ring in inner..outer) {
            val landed = dealWeaponDamage(enemy, PHASE_LATTICE_DPS * delta * power, cadence = WeaponHitCadence.CONTINUOUS).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.CRYO, PHASE_LATTICE_CHILL_INTERVAL, extraBeforeBreak = earlyFreeze)
        } else if (outerRing && ring in square(center + PHASE_OUTER_OFFSET - 16f)..square(center + PHASE_OUTER_OFFSET + 16f)) {
            dealWeaponDamage(enemy, PHASE_OUTER_DPS * delta * power, cadence = WeaponHitCadence.CONTINUOUS, procKey = WeaponRuntime.sourceKey(1))
        }
        if (agonyRank > 0 && ring in square(158f)..square(192f + agonyRank * 4f)) {
            dealWeaponDamage(enemy, (8f + 3f * agonyRank) * delta * power, cadence = WeaponHitCadence.CONTINUOUS, procKey = WeaponRuntime.sourceKey(1))
        }
    }
}

/** Centre radius of Phase Lattice's ring: the Halo radius for the Ring, else 110. */
internal fun MutableGameState.phaseLatticeCenter(): Float =
    if (signatureActive() && haloRadius > 0f) haloRadius else PHASE_LATTICE_CENTER

/** Rift Blades: (31 + 0.025 v) per contact, Hollow on every third hit; at the third form crits slash on. */
private fun MutableGameState.resolveRiftBlades(power: Float) {
    val runtime = weaponRuntime
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || enemy.weaponCooldown > 0f) continue
        var orbitalHit = false
        for (orbitalIndex in weaponOrbitals.indices) {
            val orbital = weaponOrbitals[orbitalIndex]
            if (distanceSquared(orbital.x, orbital.y, enemy.x, enemy.y) <= square(orbital.radius + enemy.radius)) {
                orbitalHit = true
                break
            }
        }
        if (!orbitalHit) continue
        val damage = (RIFT_BLADE_DAMAGE + softVelocity(speed) * RIFT_BLADE_SPEED_SHARE) * power
        val result = dealWeaponDamage(enemy, damage, canCrit = true, cadence = WeaponHitCadence.CONTACT)
        enemy.weaponCooldown = 0.17f
        if (result.amount > 0f) {
            runtime.elementHits = (runtime.elementHits + 1) % RIFT_BLADE_HOLLOW_EVERY
            if (runtime.elementHits == 0) applyWeaponStatus(enemy, StatusElement.VOID, 1)
        }
        if (result.critical && hasMasteryForm(3)) riftSlash(enemy, damage * RIFT_SLASH_SHARE)
    }
}

/** A rift slash: a cut from the struck enemy outward from the Core. */
private fun MutableGameState.riftSlash(origin: Enemy, damage: Float) {
    val dx = origin.x - coreX
    val dy = origin.y - coreY
    val distance = max(1f, length(dx, dy))
    val endX = origin.x + dx / distance * RIFT_SLASH_LENGTH
    val endY = origin.y + dy / distance * RIFT_SLASH_LENGTH
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy === origin || !enemy.alive) continue
        if (segmentCircleIntersects(origin.x, origin.y, endX, endY, enemy.x, enemy.y, enemy.radius + 10f)) {
            dealWeaponDamage(enemy, damage, procKey = WeaponRuntime.PROC_NEVER)
        }
    }
    addWeaponArc(origin.x, origin.y, endX, endY, 0.16f)
}

/** Entropy Field: 28/s with drag and Decay every 0.8 s; the forms widen it, execute and spread Ruptures. */
private fun MutableGameState.resolveEntropyField(delta: Float, power: Float, agonyRank: Int) {
    val radius = entropyFieldRadius()
    val executeLine = max(if (hasMasteryForm(2)) ENTROPY_EXECUTE else 0f, if (agonyRank > 0) 0.06f + agonyRank * 0.015f else 0f)
    entropyField(coreX, coreY, radius, power * (1f + agonyRank * 0.08f), executeLine, delta, WeaponRuntime.sourceKey(0))
    // Spiral signature: the echo ghost carries a half-strength field.
    if (signatureActive() && ghostActive) {
        entropyField(ghostX, ghostY, radius * ENTROPY_GHOST_RADIUS, power * 0.5f, 0f, delta, WeaponRuntime.sourceKey(1))
    }
}

private fun MutableGameState.entropyField(x: Float, y: Float, radius: Float, power: Float, executeLine: Float, delta: Float, procKey: Int) {
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        // The collapse executes outright, past every multiplier and tether; bosses only take the field.
        if (executeLine > 0f && !enemy.isBossClass && enemy.hp <= enemy.maxHp * executeLine) {
            executeEnemy(enemy, HitSource.WEAPON)
        } else {
            val landed = dealWeaponDamage(enemy, ENTROPY_DPS * delta * power, cadence = WeaponHitCadence.CONTINUOUS, procKey = procKey).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.ENTROPY, ENTROPY_DECAY_INTERVAL)
        }
        val drag = exp(-0.5f * delta * enemy.knockback)
        enemy.vx *= drag
        enemy.vy *= drag
    }
}

/** Entropy Field radius: 170 + 8 per level, +35 % at the first form, +18 per Agony power. */
internal fun MutableGameState.entropyFieldRadius(): Float =
    (170f + weaponLevel * 8f) * (if (hasMasteryForm(1)) 1.35f else 1f) + agonyPower() * 18f

/** Entropy Field's third form: a Rupture inside the field spreads Decay around it. */
internal fun MutableGameState.onWeaponBreak(enemy: Enemy, element: StatusElement) {
    if (weapon != WeaponId.ENTROPY_FIELD || element != StatusElement.ENTROPY || !hasMasteryForm(3)) return
    val radius = entropyFieldRadius()
    if (distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(radius + enemy.radius)) return
    applyStatusAround(enemy.x, enemy.y, ENTROPY_SPREAD_RADIUS, StatusElement.ENTROPY, 2f, generation = 1, excludedId = enemy.id)
}

internal const val FLUX_DPS: Float = 22f
internal const val FLUX_WIDTH: Float = 20f
internal const val FLUX_WIDTH_FORM: Float = 2f
internal const val FLUX_SCORCH_INTERVAL: Float = 2f
/** Enemies the Flux Wake trail burns at full heat at once; a larger crowd in it shares that heat. */
internal const val FLUX_FULL_HEAT_ENEMIES: Int = 6
internal const val MORNINGSTAR_DAMAGE: Float = 13.8f
internal const val MORNINGSTAR_REACH: Float = 24f
internal const val MORNINGSTAR_SPEED_SHARE: Float = 0.030f
internal const val MORNINGSTAR_KNOCKBACK: Float = 160f
internal const val MORNINGSTAR_SECOND_ORBIT: Float = 0.72f
internal const val MORNINGSTAR_SECOND_SIZE: Float = 0.45f
internal const val PHASE_LATTICE_CENTER: Float = 110f
internal const val PHASE_LATTICE_HALF_WIDTH: Float = 42f
internal const val PHASE_LATTICE_DPS: Float = 55f
internal const val PHASE_LATTICE_CHILL_INTERVAL: Float = 0.6f
internal const val PHASE_OUTER_OFFSET: Float = 100f
internal const val PHASE_OUTER_DPS: Float = 30f
internal const val RIFT_BLADE_DAMAGE: Float = 18f
internal const val RIFT_BLADE_SPEED_SHARE: Float = 0.018f
internal const val RIFT_BLADE_HOLLOW_EVERY: Int = 3
internal const val RIFT_SLASH_SHARE: Float = 0.5f
internal const val RIFT_SLASH_LENGTH: Float = 160f
internal const val ENTROPY_DPS: Float = 11f
internal const val ENTROPY_DECAY_INTERVAL: Float = 1.6f
internal const val ENTROPY_EXECUTE: Float = 0.06f
internal const val ENTROPY_GHOST_RADIUS: Float = 0.7f
internal const val ENTROPY_SPREAD_RADIUS: Float = 140f

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

/** Data needed to go from run level [level] to the next: the content table scaled by the run's Rebirth tier. */
internal fun MutableGameState.dataRequiredForLevel(level: Int): Int =
    content.tempo.dataRequiredForLevel(level, rebirthProfile)

internal fun MutableGameState.gainData(rawAmount: Float) {
    if (creditData(rawAmount) <= 0) return
    val levelBefore = level
    val maximumLevel = content.tempo.maximumLevel
    while (level < maximumLevel && data >= nextLevelData) {
        data -= nextLevelData
        level++
        pendingLevelChoices++
        onLevelUp()
        nextLevelData = dataRequiredForLevel(level)
        if (content.tempo.grantsWeaponAmplification(level) &&
            weaponLevel < content.tempo.maximumAutomaticWeaponLevel + matrix.startingWeaponLevels + signatureLevelBonus()
        ) {
            val before = captureBuildChange()
            amplifyCurrentWeapon()
            emitBuildChange(before, currentWeaponDefinition.name + " mastery advanced")
        }
        if (level == maximumLevel) {
            // The capped level holds no Data: the leftover would only charge an Apex that unlocks ready.
            data = 0
            unlockApex()
        }
    }
    if (level > levelBefore) vacuumOnScreenData()
    openNextPendingChoice()
}

/**
 * Adds Data without levelling and returns the whole points added; the fraction carries over. A LEVEL_UP
 * clause's Data arrives this way, so the level loop in [gainData] that fired it takes the Data up against the
 * settled requirement and never re-enters itself. At the run's maximum level every whole Data still counts as
 * collected but charges the Apex instead and adds nothing (0 is returned, so [gainData] never loops).
 */
internal fun MutableGameState.creditData(rawAmount: Float): Int {
    dataFraction += rawAmount * dataGainLiability()
    val whole = floor(dataFraction).toInt()
    if (whole <= 0) return 0
    dataFraction -= whole
    dataCollected += whole.toLong()
    if (level >= content.tempo.maximumLevel) {
        chargeApexWithData(whole)
        return 0
    }
    data += whole
    return whole
}
