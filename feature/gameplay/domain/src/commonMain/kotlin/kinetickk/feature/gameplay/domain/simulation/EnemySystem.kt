// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.simulation

import kinetickk.feature.gameplay.domain.model.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


internal fun MutableGameState.updateEnemies(delta: Float) {
    enemies.forEach { enemy ->
        enemy.previousX = enemy.x
        enemy.previousY = enemy.y
        enemy.flash = max(0f, enemy.flash - delta * 5f)
        enemy.contactCooldown = max(0f, enemy.contactCooldown - delta)
        enemy.weaponCooldown = max(0f, enemy.weaponCooldown - delta)
        val dx = coreX - enemy.x
        val dy = coreY - enemy.y
        val distance = max(1f, length(dx, dy))
        when (enemy.type) {
            EnemyType.DRIFTER -> steerEnemy(enemy, dx / distance * 78f, dy / distance * 78f, 2.1f, delta)
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
                enemy.actionTimer -= delta
                if (enemy.actionTimer <= 0f && distance < 760f) {
                    enemy.actionTimer = max(0.62f, 1.7f - threatElapsed / 900f)
                    fireSpread(enemy.x, enemy.y, atan2(dy, dx), if (threatElapsed > 420f) 3 else 1, 0.14f, 220f)
                }
            }
            EnemyType.CHARGER -> {
                enemy.actionTimer -= delta
                if (enemy.actionTimer <= -0.45f) {
                    enemy.actionTimer = 2.15f
                    enemy.vx = dx / distance * 390f * rebirthProfile.enemySpeedMultiplier
                    enemy.vy = dy / distance * 390f * rebirthProfile.enemySpeedMultiplier
                } else if (enemy.actionTimer < 0f) {
                    enemy.vx *= exp(-6f * delta)
                    enemy.vy *= exp(-6f * delta)
                } else {
                    steerEnemy(enemy, dx / distance * 48f, dy / distance * 48f, 0.7f, delta)
                }
            }
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
                enemy.actionTimer -= delta
                if (enemy.actionTimer <= 0f && distance < 720f) {
                    enemy.actionTimer = 2.4f
                    val interceptBoost = 175f * rebirthProfile.enemySpeedMultiplier
                    enemy.vx += leadX / leadDistance * interceptBoost
                    enemy.vy += leadY / leadDistance * interceptBoost
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
                enemy.actionTimer -= delta
                if (enemy.actionTimer <= 0f && distance < 700f) {
                    enemy.actionTimer = 1.85f
                    fireProjectileWall(
                        enemy.x,
                        enemy.y,
                        atan2(dy, dx),
                        if (threatElapsed > 420f) 5 else 3,
                        34f,
                        245f,
                    )
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
                if (distance < 440f && dashPhaseTime <= 0f) {
                    val gravity = 38f + (1f - distance / 440f) * 270f
                    velocityX -= dx / distance * gravity * delta
                    velocityY -= dy / distance * gravity * delta
                }
                enemy.actionTimer -= delta
                if (enemy.actionTimer <= 0f && distance < 780f) {
                    enemy.actionTimer = 2.65f
                    fireRadial(enemy.x, enemy.y, 8, 132f, elapsed * 0.35f)
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
                enemy.actionTimer -= delta
                if (enemy.actionTimer <= 0f) {
                    enemy.actionTimer = 1.18f
                    fireRadial(enemy.x, enemy.y, 10, 165f, elapsed * 0.2f)
                }
            }
            EnemyType.ARCHITECT -> updateArchitect(enemy, dx, dy, distance, delta)
        }
        enemy.x += enemy.vx * delta
        enemy.y += enemy.vy * delta
    }
    val leashDistance = max(screenWidth, screenHeight) * 1.15f + 360f
    val leashSquared = leashDistance * leashDistance
    enemies.removeAll { enemy ->
        enemy.type != EnemyType.ELITE &&
            enemy.type != EnemyType.ARCHITECT &&
            distanceSquared(enemy.x, enemy.y, coreX, coreY) > leashSquared
    }
}

internal fun MutableGameState.updateArchitect(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    val orbit = elapsed * 0.23f
    val targetX = coreX + cos(orbit) * 380f
    val targetY = coreY + sin(orbit) * 380f
    steerEnemy(enemy, (targetX - enemy.x) * 0.6f, (targetY - enemy.y) * 0.6f, 1.2f, delta)
    enemy.actionTimer -= delta
    if (enemy.actionTimer <= 0f) {
        enemy.actionTimer = if (enemy.hp < enemy.maxHp * 0.5f) 0.5f else 0.78f
        fireSpread(enemy.x, enemy.y, atan2(dy, dx), 7, 0.18f, 250f)
        fireRadial(enemy.x, enemy.y, if (enemy.hp < enemy.maxHp * 0.5f) 18 else 12, 135f, elapsed)
    }
    if (distance < 180f) {
        enemy.vx -= dx / distance * 80f * delta
        enemy.vy -= dy / distance * 80f * delta
    }
}

internal fun MutableGameState.steerEnemy(enemy: Enemy, desiredX: Float, desiredY: Float, agility: Float, delta: Float) {
    val factor = 1f - exp(-agility * delta)
    enemy.vx = lerp(enemy.vx, desiredX * rebirthProfile.enemySpeedMultiplier, factor)
    enemy.vy = lerp(enemy.vy, desiredY * rebirthProfile.enemySpeedMultiplier, factor)
}

internal fun MutableGameState.updateProjectiles(delta: Float) {
    projectiles.forEach {
        it.previousX = it.x
        it.previousY = it.y
        it.x += it.vx * delta
        it.y += it.vy * delta
        it.life -= delta
    }
    projectiles.removeAll {
        it.life <= 0f || distanceSquared(it.x, it.y, coreX, coreY) > 1_600f * 1_600f
    }
    while (projectiles.size > MutableGameState.MAX_PROJECTILES) projectiles.removeAt(0)
}

internal fun MutableGameState.updatePickups(delta: Float) {
    pickups.forEach { pickup ->
        pickup.previousX = pickup.x
        pickup.previousY = pickup.y
        pickup.life -= delta
        val dx = coreX - pickup.x
        val dy = coreY - pickup.y
        val distance = max(1f, length(dx, dy))
        if (distance < pickupRadius) {
            val pull = (pickupRadius - distance) * 4.8f
            pickup.vx += dx / distance * pull * delta
            pickup.vy += dy / distance * pull * delta
        }
        pickup.vx *= exp(-1.8f * delta)
        pickup.vy *= exp(-1.8f * delta)
        pickup.x += pickup.vx * delta
        pickup.y += pickup.vy * delta
    }
    pickups.removeAll { it.life <= 0f }
    while (pickups.size > MutableGameState.MAX_PICKUPS) pickups.removeAt(0)
}

internal fun MutableGameState.updateTotem(delta: Float) {
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
        totem = Totem(coreX + cos(angle) * distance, coreY + sin(angle) * distance)
        message = "TOTEM DETECTED"
        messageTime = 1.5f
    }
}

internal fun MutableGameState.spawnWave(delta: Float) {
    if (bossSpawned) return
    spawnClock -= delta
    val baseMaxEnemies = min(90, 14 + floor(elapsed / 20f).toInt())
    val maxEnemies = min(MutableGameState.MAX_ENEMIES, rebirthProfile.enemyCap(baseMaxEnemies))
    if (spawnClock <= 0f && enemies.size < maxEnemies) {
        val baseInterval = max(0.13f, 0.84f - elapsed / 1_700f)
        spawnClock = rebirthProfile.spawnInterval(baseInterval)
        val rolledType = enemyTypeForElapsed(threatElapsed, gameplayRandom.nextFloat())
        val maxWardens = 2 + min(4, rebirthLevel / 3)
        val type = if (rolledType == EnemyType.WARDEN && enemies.count { it.type == EnemyType.WARDEN } >= maxWardens) {
            EnemyType.WEAVER
        } else {
            rolledType
        }
        spawnEnemy(type)
    }
    if (elapsed >= nextEliteAt) {
        nextEliteAt += rebirthProfile.eliteInterval(max(48f, 86f - elapsed / 25f))
        if (spawnEnemy(EnemyType.ELITE)) {
            message = "ELITE SIGNAL"
            messageTime = 1.4f
        }
    }
}

internal fun MutableGameState.spawnEnemy(type: EnemyType): Boolean {
    if (enemies.size >= MutableGameState.MAX_ENEMIES) return false
    val useForwardCorridor = type != EnemyType.ELITE &&
        type != EnemyType.ARCHITECT &&
        speed > 500f &&
        gameplayRandom.nextFloat() < 0.82f
    val angle = if (useForwardCorridor) {
        movementAngle()
    } else {
        gameplayRandom.nextFloat() * TAU
    }
    val directionX = cos(angle)
    val directionY = sin(angle)
    val distanceToVerticalEdge = screenWidth * 0.5f / max(0.001f, kotlin.math.abs(directionX))
    val distanceToHorizontalEdge = screenHeight * 0.5f / max(0.001f, kotlin.math.abs(directionY))
    val viewportEdgeDistance = min(distanceToVerticalEdge, distanceToHorizontalEdge)
    val distance = viewportEdgeDistance + 90f + gameplayRandom.nextFloat() * 150f
    val lateralOffset = if (useForwardCorridor) (gameplayRandom.nextFloat() - 0.5f) * 140f else 0f
    val x = coreX + directionX * distance - directionY * lateralOffset
    val y = coreY + directionY * distance + directionX * lateralOffset
    val difficulty = 1f + elapsed / 470f
    val stats = when (type) {
        EnemyType.DRIFTER -> Triple(rebirthProfile.enemyHealth(30f * difficulty), 17f, 0.2f)
        EnemyType.SHOOTER -> Triple(rebirthProfile.enemyHealth(46f * difficulty), 20f, 0.7f)
        EnemyType.CHARGER -> Triple(rebirthProfile.enemyHealth(68f * difficulty), 23f, 1.2f)
        EnemyType.INTERCEPTOR -> Triple(rebirthProfile.enemyHealth(58f * difficulty), 18f, 1.4f)
        EnemyType.WEAVER -> Triple(rebirthProfile.enemyHealth(74f * difficulty), 21f, 0.9f)
        EnemyType.WARDEN -> Triple(rebirthProfile.enemyHealth(145f * difficulty), 29f, 1.3f)
        EnemyType.SPLITTER -> Triple(rebirthProfile.enemyHealth(118f * difficulty), 27f, 0.4f)
        EnemyType.ELITE -> Triple(rebirthProfile.enemyHealth(340f * difficulty), 38f, 0.5f)
        EnemyType.ARCHITECT -> Triple(rebirthProfile.enemyHealth(5_400f), 74f, 1.2f)
    }
    enemies += Enemy(
        id = nextEntityId++,
        type = type,
        x = x,
        y = y,
        hp = stats.first,
        maxHp = stats.first,
        radius = stats.second,
        actionTimer = stats.third,
    )
    return true
}
