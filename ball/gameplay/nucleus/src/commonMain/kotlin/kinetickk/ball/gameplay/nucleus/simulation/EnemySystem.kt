// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


internal fun MutableGameState.updateEnemies(delta: Float) {
    val attackRate = hostileAttackRate()
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        enemy.previousX = enemy.x
        enemy.previousY = enemy.y
        if (enemy.flash.toRawBits() != 0) enemy.flash = max(0f, enemy.flash - delta * 5f)
        if (enemy.contactCooldown.toRawBits() != 0) {
            enemy.contactCooldown = max(0f, enemy.contactCooldown - delta)
        }
        if (enemy.weaponCooldown.toRawBits() != 0) {
            enemy.weaponCooldown = max(0f, enemy.weaponCooldown - delta)
        }
        if (!enemy.alive) continue
        // Frozen enemies stop and never attack; Chill and stasis slow both movement and attack cadence.
        val motion = enemyMotionScale(enemy)
        if (motion <= 0f) {
            enemy.vx = 0f
            enemy.vy = 0f
            continue
        }
        if (!canAct(enemy)) {
            // Stunned or stopped: no steering and no attacks; momentum bleeds off.
            val damping = exp(-6f * delta)
            enemy.vx *= damping
            enemy.vy *= damping
            enemy.x += enemy.vx * delta * motion
            enemy.y += enemy.vy * delta * motion
            continue
        }
        val dx = coreX - enemy.x
        val dy = coreY - enemy.y
        val distance = max(1f, length(dx, dy))
        val cadence = delta * motion * attackRate * affixCadence(enemy)
        // A site that owns this enemy (the Golden Drifter) moves it instead of its kind's behaviour.
        if (updateSiteOwnedEnemy(enemy, delta)) continue
        when (enemy.type) {
            EnemyType.DRIFTER -> steerEnemy(enemy, dx / distance * 78f, dy / distance * 78f, 2.1f, delta)
            // 0.6.0 kinds: one dispatch each; a kind whose behaviour has not landed yet steers like a Drifter.
            EnemyType.COMET -> updateComet(enemy, dx, dy, distance, delta)
            EnemyType.PYLON -> updatePylon(enemy, dx, dy, distance)
            EnemyType.SENTRY -> updateSentry(enemy, dx, dy, distance, delta)
            EnemyType.CRAWLER -> updateCrawler(enemy, dx, dy, distance, delta)
            EnemyType.SERAPH -> updateSeraph(enemy, dx, dy, distance, cadence, delta)
            EnemyType.CHOIR -> updateChoir(enemy, dx, dy, distance, cadence, delta)
            EnemyType.IMP -> updateImp(enemy, dx, dy, distance, delta)
            EnemyType.HOUND -> updateHound(enemy, dx, dy, distance, cadence, delta)
            EnemyType.CIPHER -> updateCipher(enemy, dx, dy, distance, delta)
            EnemyType.MIRROR -> updateMirror(enemy, dx, dy, cadence, delta)
            EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN,
            EnemyType.THRONE, EnemyType.PYRE_HERALD, EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT,
            -> updatePendingKind(enemy, dx, dy, distance, delta)
            EnemyType.SHOOTER -> {
                val desired = if (distance > 340f) 78f else if (distance < 250f) -72f else 0f
                val tangent = if (enemy.id % 2 == 0) 1f else -1f
                steerEnemy(
                    enemy,
                    dx / distance * desired - dy / distance * 62f * tangent,
                    dy / distance * desired + dx / distance * 62f * tangent,
                    2.4f,
                    delta,
                )
                enemy.actionTimer -= cadence
                if (enemy.actionTimer <= 0f && distance < 760f) {
                    enemy.actionTimer = 1.7f
                    if (canAttack(enemy)) {
                        fireSpread(
                            enemy.x, enemy.y, atan2(dy, dx),
                            if (content.tempo.denseAttacks(threatElapsed)) 3 else 1, 0.14f, 220f, shotDamage(enemy),
                        )
                    }
                }
            }
            EnemyType.CHARGER -> updateCharger(enemy, dx, dy, distance, cadence, delta)
            EnemyType.INTERCEPTOR -> {
                val leadTime = clamp(distance / 650f, 0.28f, 0.85f)
                val leadX = coreX + velocityX * leadTime - enemy.x
                val leadY = coreY + velocityY * leadTime - enemy.y
                val leadDistance = max(1f, length(leadX, leadY))
                val interceptSpeed = min(360f, 145f + speed * 0.13f)
                steerEnemy(
                    enemy,
                    leadX / leadDistance * interceptSpeed,
                    leadY / leadDistance * interceptSpeed,
                    4.2f,
                    delta,
                )
                enemy.actionTimer -= cadence
                if (enemy.actionTimer <= 0f && distance < 720f) {
                    enemy.actionTimer = 2.4f
                    if (canAttack(enemy)) {
                        val interceptBoost = 175f * rebirthProfile.enemySpeedMultiplier
                        enemy.vx += leadX / leadDistance * interceptBoost
                        enemy.vy += leadY / leadDistance * interceptBoost
                    }
                }
            }
            EnemyType.WEAVER -> {
                val weave = sin(elapsed * 2.7f + enemy.id * 0.91f)
                val tangentX = -dy / distance
                val tangentY = dx / distance
                steerEnemy(
                    enemy,
                    dx / distance * 92f + tangentX * weave * 175f,
                    dy / distance * 92f + tangentY * weave * 175f,
                    2.8f,
                    delta,
                )
                enemy.actionTimer -= cadence
                if (enemy.actionTimer <= 0f && distance < 700f) {
                    enemy.actionTimer = 1.85f
                    if (canAttack(enemy)) {
                        fireProjectileWall(
                            enemy.x,
                            enemy.y,
                            atan2(dy, dx),
                            if (content.tempo.denseAttacks(threatElapsed)) 5 else 3,
                            34f,
                            245f,
                            shotDamage(enemy),
                        )
                    }
                }
            }
            EnemyType.WARDEN -> {
                val desired = if (distance > 330f) 64f else if (distance < 250f) -58f else 0f
                val tangent = if (enemy.id % 2 == 0) 1f else -1f
                steerEnemy(
                    enemy,
                    dx / distance * desired - dy / distance * 28f * tangent,
                    dy / distance * desired + dx / distance * 28f * tangent,
                    1.55f,
                    delta,
                )
                if (distance < 440f && dashPhaseTime <= 0f && canAttack(enemy)) {
                    // Ion charge disrupts the well.
                    val disruption = max(0.2f, 1f - 0.12f * stacks(enemy, StatusElement.ION))
                    val gravity = (38f + (1f - distance / 440f) * 270f) * disruption
                    velocityX -= dx / distance * gravity * delta
                    velocityY -= dy / distance * gravity * delta
                }
                enemy.actionTimer -= cadence
                if (enemy.actionTimer <= 0f && distance < 780f) {
                    enemy.actionTimer = 2.65f
                    if (canAttack(enemy)) fireRadial(enemy.x, enemy.y, 8, 132f, elapsed * 0.35f, shotDamage(enemy))
                }
            }
            EnemyType.SPLITTER -> {
                val sway = sin(elapsed * 1.6f + enemy.id) * 44f
                steerEnemy(
                    enemy,
                    dx / distance * 58f - dy / distance * sway,
                    dy / distance * 58f + dx / distance * sway,
                    1.35f,
                    delta,
                )
            }
            EnemyType.ELITE -> {
                val tangent = if (enemy.id % 2 == 0) 1f else -1f
                steerEnemy(enemy, dx / distance * 52f - dy / distance * 45f * tangent, dy / distance * 52f + dx / distance * 45f * tangent, 1.3f, delta)
                enemy.actionTimer -= cadence
                if (enemy.actionTimer <= 0f) {
                    enemy.actionTimer = 1.18f
                    if (canAttack(enemy)) fireRadial(enemy.x, enemy.y, 10, 165f, elapsed * 0.2f, shotDamage(enemy))
                }
            }
            EnemyType.ARCHITECT -> updateArchitect(enemy, dx, dy, distance, delta * motion)
            EnemyType.SWARMLING -> updateSwarmling(enemy, dx, dy, distance, delta)
            EnemyType.BOMBER -> updateBomber(enemy, dx, dy, distance, delta)
            EnemyType.LANCER -> updateLancer(enemy, dx, dy, distance, delta)
            EnemyType.SNIPER -> updateSniper(enemy, dx, dy, distance, delta)
            EnemyType.AEGIS -> updateAegis(enemy, dx, dy, distance, delta)
            EnemyType.MINELAYER -> updateMinelayer(enemy, dx, dy, distance, cadence, delta)
            EnemyType.PHANTOM -> updatePhantom(enemy, dx, dy, distance, delta)
            EnemyType.BROODMOTHER -> updateBroodmother(enemy, dx, dy, distance, cadence, delta)
            EnemyType.REFLECTOR -> updateReflector(enemy, dx, dy, distance, delta)
            EnemyType.HERALD -> updateHerald(enemy, dx, dy, distance, cadence, delta)
        }
        enemy.x += enemy.vx * delta * motion
        enemy.y += enemy.vy * delta * motion
    }
    resolveEnemyObstacles()
    // Detonated Bombers leave without a kill.
    enemies.removeMatchingStable { enemy -> enemy.dead }
    val leashDistance = max(screenWidth, screenHeight) * 1.15f + 360f
    val leashSquared = leashDistance * leashDistance
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (leashExempt(enemy)) continue
        if (distanceSquared(enemy.x, enemy.y, coreX, coreY) > leashSquared) relocateLeashedEnemy(enemy)
    }
    updateEncounters()
}

/**
 * False while [enemy] is disabled (stunned or stopped): it neither moves by itself nor attacks. The
 * Architect and the Herald are immune to stop and stun, so a disable written on them never holds.
 */
internal fun MutableGameState.canAct(enemy: Enemy): Boolean =
    enemy.isBossClass || elapsed >= enemy.disabledUntil

/** False while [enemy] cannot act or is silenced; every hostile attack branch checks it before firing. */
internal fun MutableGameState.canAttack(enemy: Enemy): Boolean = canAct(enemy) && elapsed >= enemy.silencedUntil

internal fun MutableGameState.steerEnemy(enemy: Enemy, desiredX: Float, desiredY: Float, agility: Float, delta: Float) {
    if (isThrownByPolarity(enemy)) return
    val factor = 1f - exp(-agility * delta)
    enemy.vx = lerp(enemy.vx, desiredX * rebirthProfile.enemySpeedMultiplier * affixSpeedFactor(enemy), factor)
    enemy.vy = lerp(enemy.vy, desiredY * rebirthProfile.enemySpeedMultiplier * affixSpeedFactor(enemy), factor)
}

internal fun MutableGameState.updateProjectiles(delta: Float) {
    // Hostile shots follow the world layer's time; the player's own shots never do.
    val hostileStep = delta * layerTimeScale(null)
    for (projectileIndex in projectiles.indices) {
        val projectile = projectiles[projectileIndex]
        val step = if (projectile.hostile) hostileStep else delta
        projectile.previousX = projectile.x
        projectile.previousY = projectile.y
        projectile.x += projectile.vx * step
        projectile.y += projectile.vy * step
        projectile.life -= step
        // A Quasar shot running out of life ends with its form's burst before it leaves the list.
        if (projectile.life <= 0f && projectile.sourceWeapon == kinetickk.ball.content.api.WeaponId.QUASAR_CANNON &&
            projectile.life > -delta
        ) quasarShotEnded(projectile)
    }
    projectiles.removeMatchingStable { projectile ->
        projectile.life <= 0f ||
            distanceSquared(projectile.x, projectile.y, coreX, coreY) > 1_600f * 1_600f
    }
    while (projectiles.size > MutableGameState.MAX_PROJECTILES) projectiles.removeAt(0)
}

/**
 * Pickups drift from where they dropped until they enter the magnet radius; there they latch and home on
 * the Core at `max(vacuumSpeed, coreSpeed × factor)` with no damping until collected. Event Horizon's
 * liability turns the latch off: pickups are then only pulled while inside the radius, weaker at its rim.
 * Keys and Relic resonances never expire, and a latched pickup does not age while it homes.
 */
internal fun MutableGameState.updatePickups(delta: Float) {
    val radius = effectivePickupRadius()
    val latching = latchedVacuumActive()
    val tuning = content.pickups
    val homingSpeed = max(tuning.vacuumSpeed, speed * tuning.vacuumCoreSpeedFactor)
    val drag = exp(-1.8f * delta)
    for (pickupIndex in pickups.indices) {
        val pickup = pickups[pickupIndex]
        pickup.previousX = pickup.x
        pickup.previousY = pickup.y
        val dx = coreX - pickup.x
        val dy = coreY - pickup.y
        val distance = max(1f, length(dx, dy))
        if (latching && !pickup.attracted && distance < radius) pickup.attracted = true
        val homing = latching && pickup.attracted
        // A latched pickup is on its way in and never fades before it arrives.
        if (pickup.expires && !homing) pickup.life -= delta
        if (homing) {
            pickup.vx = dx / distance * homingSpeed
            pickup.vy = dy / distance * homingSpeed
        } else {
            if (!latching && distance < radius) {
                val pull = (radius - distance) * 4.8f
                pickup.vx += dx / distance * pull * delta
                pickup.vy += dy / distance * pull * delta
            }
            pickup.vx *= drag
            pickup.vy *= drag
        }
        pickup.x += pickup.vx * delta
        pickup.y += pickup.vy * delta
    }
    pickups.removeMatchingStable { pickup -> pickup.expires && pickup.life <= 0f }
    while (pickups.size > MutableGameState.MAX_PICKUPS) {
        val oldest = oldestEvictablePickup()
        pickups.removeAt(if (oldest >= 0) oldest else 0)
    }
}

/** Data and repairs fade; Keys and Relic resonances wait until they are collected. */
internal val Pickup.expires: Boolean get() = type == PickupType.DATA || type == PickupType.REPAIR

/** Index of the oldest pickup the cap may evict (Data first, then repairs), or -1. */
internal fun MutableGameState.oldestEvictablePickup(): Int {
    var repair = -1
    for (index in pickups.indices) {
        val type = pickups[index].type
        if (type == PickupType.DATA) return index
        if (repair < 0 && type == PickupType.REPAIR) repair = index
    }
    return repair
}

/** Event Horizon's liability disables the latched vacuum and the level-up vacuum. */
internal fun MutableGameState.latchedVacuumActive(): Boolean =
    !build.liability(kinetickk.ball.content.api.RelicId.EVENT_HORIZON)

/**
 * A level-up, and the end of a choice, latch every Data pickup inside the viewport onto the Core
 * (unless Event Horizon's liability holds).
 */
internal fun MutableGameState.vacuumOnScreenData() {
    if (!latchedVacuumActive() || pickups.isEmpty()) return
    val halfWidth = screenWidth * 0.5f
    val halfHeight = screenHeight * 0.5f
    for (index in pickups.indices) {
        val pickup = pickups[index]
        if (pickup.type != PickupType.DATA || pickup.attracted) continue
        if (kotlin.math.abs(pickup.x - cameraX) <= halfWidth && kotlin.math.abs(pickup.y - cameraY) <= halfHeight) {
            pickup.attracted = true
        }
    }
}

/** Stable one-pass compaction without iterator or stdlib predicate-wrapper allocation. */
internal inline fun <Element> MutableList<Element>.removeMatchingStable(
    remove: (Element) -> Boolean,
) {
    var retainedCount = 0
    for (index in indices) {
        val value = this[index]
        if (!remove(value)) {
            if (retainedCount != index) this[retainedCount] = value
            retainedCount++
        }
    }
    var index = lastIndex
    while (index >= retainedCount) {
        removeAt(index)
        index--
    }
}

internal fun MutableGameState.updateTotem(delta: Float) {
    if (phase != GamePhase.RUNNING) return
    val activeTotem = totem
    if (activeTotem != null) {
        activeTotem.pulse = (activeTotem.pulse + delta * 2.5f) % TAU
        if (segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, activeTotem.x, activeTotem.y, 45f) && keys > 0) {
            keys--
            totem = null
            openTotemChoice()
        }
    } else if (keys > 0 && phase == GamePhase.RUNNING) {
        val angle = gameplayRandom.nextFloat() * TAU
        val distance = 520f + gameplayRandom.nextFloat() * 180f
        val (totemX, totemY) = nudgedClearOfObstacles(coreX + cos(angle) * distance, coreY + sin(angle) * distance, TOTEM_CLEARANCE)
        totem = Totem(totemX, totemY)
        message = "TOTEM DETECTED"
        messageTime = 1.5f
    }
}

/** Room a Totem keeps from obstacles, so the Core can always reach it. */
private const val TOTEM_CLEARANCE: Float = 70f

/** Spawns a scheduled or escort enemy of [type] at spawn distance; false at the hard cap. */
internal fun MutableGameState.spawnEnemy(type: EnemyType): Boolean = spawnScheduled(type)

/**
 * A new [type] at ([x], [y]) with its profile and current spawn integrity (scaled by [integrityShare]), owned by the
 * encounter [ownerTag] (0: the ambient director). Elites and Heralds roll an aspect from the spawn stream, so only call
 * this for an enemy that is added.
 */
internal fun MutableGameState.spawnedEnemy(
    type: EnemyType,
    x: Float,
    y: Float,
    ownerTag: Long = 0L,
    integrityShare: Float = 1f,
): Enemy {
    val profile = type.profile
    var integrity = spawnIntegrity(type)
    val hardened = type == EnemyType.ELITE && runMutatorActive(RebirthMutator.HARDENED_ELITES)
    if (hardened) integrity *= HARDENED_ELITE_INTEGRITY
    integrity *= integrityShare
    val enemy = Enemy(
        id = nextEntityId++,
        type = type,
        x = x,
        y = y,
        hp = integrity,
        maxHp = integrity,
        radius = profile.radius,
        actionTimer = profile.spawnActionTimer,
    )
    if (type == EnemyType.ELITE || type == EnemyType.HERALD) {
        assignAspect(enemy, rollAspect(), immune = profile.aspectImmune)
        if (type == EnemyType.ELITE) elitesSpawned++
        if (hardened) enemy.escortsPending = HARDENED_ELITE_ESCORTS
        if (type == EnemyType.HERALD) enemy.escortsPending = HERALD_ESCORTS
    }
    enemy.ownerTag = ownerTag
    rollVanguardArmor(enemy)
    rollEliteVariety(enemy)
    initializeBehavior(enemy)
    return enemy
}

/** Spawn integrity of [type] at the current run time; the Architect follows the same curve. */
internal fun MutableGameState.spawnIntegrity(type: EnemyType): Float =
    rebirthProfile.enemyHealth(type.profile.baseHealth * content.tempo.enemyHealthMultiplier(elapsed)) *
        matrix.enemyIntegrityMultiplier
