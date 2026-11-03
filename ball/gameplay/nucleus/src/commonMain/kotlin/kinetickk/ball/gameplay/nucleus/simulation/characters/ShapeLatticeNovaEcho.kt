// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun MutableGameState.updateLattice(ability: CharacterRuntime) {
    if (dashPhaseTime > 0f && ability.hasDashOrigin && !ability.dashRecorded &&
        distanceSquared(ability.dashOriginX, ability.dashOriginY, coreX, coreY) >= square(LATTICE_RECORD_DISTANCE)
    ) {
        ability.dashRecorded = true
        recordLatticeNode(ability.dashOriginX, ability.dashOriginY)
    }
    ability.charge = ability.latticeCount / CharacterRuntime.MAX_LATTICE_NODES.toFloat()
}

/**
 * Records a lattice node at ([x], [y]): close to the first node of a three-node lattice it closes and collapses
 * the lattice; a node too close to another is not recorded; the last free node collapses it.
 */
internal fun MutableGameState.recordLatticeNode(x: Float, y: Float) {
    // Hypercube holds the lattice as its cage: nothing is recorded or collapses while it runs.
    if (hypercubeRunning()) return
    val ability = characterRuntime
    val count = ability.latticeCount
    if (count >= 3 && distanceSquared(x, y, ability.latticeX(0), ability.latticeY(0)) < square(LATTICE_CLOSE_DISTANCE)) {
        collapseLattice(count)
        return
    }
    for (index in 0 until count) {
        if (distanceSquared(x, y, ability.latticeX(index), ability.latticeY(index)) < square(LATTICE_SPACING)) return
    }
    ability.latticeNodes[count * 2] = x
    ability.latticeNodes[count * 2 + 1] = y
    ability.latticeCount = count + 1
    if (ability.latticeCount >= CharacterRuntime.MAX_LATTICE_NODES) collapseLattice(ability.latticeCount)
    else ability.latticeTime = latticeLifeSeconds()
}

/** Strikes every enemy touching a lattice edge; enemies inside the closed polygon take half again. */
private fun MutableGameState.collapseLattice(count: Int) {
    val ability = characterRuntime
    ability.latticeCount = 0
    ability.latticeTime = 0f
    if (count < 2) return
    val damage = (LATTICE_BASE_DAMAGE + count * LATTICE_NODE_DAMAGE) * abilityPower()
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        var touches = false
        for (index in 0 until count) {
            val next = (index + 1) % count
            if (segmentCircleIntersects(ability.latticeX(index), ability.latticeY(index), ability.latticeX(next), ability.latticeY(next),
                    enemy.x, enemy.y, enemy.radius)
            ) {
                touches = true
                break
            }
        }
        val inside = count >= 3 && insideLattice(ability, count, enemy.x, enemy.y)
        if (touches || inside) dealCharacterDamage(enemy, damage * if (inside) LATTICE_INSIDE_MULTIPLIER else 1f)
    }
    for (index in 0 until count) {
        val next = (index + 1) % count
        addWeaponArc(ability.latticeX(index), ability.latticeY(index), ability.latticeX(next), ability.latticeY(next), 0.35f)
    }
}

/** Even-odd point-in-polygon test over the first [count] lattice nodes. */
internal fun insideLattice(ability: CharacterRuntime, count: Int, x: Float, y: Float): Boolean {
    var inside = false
    var previous = count - 1
    for (index in 0 until count) {
        val ax = ability.latticeX(index)
        val ay = ability.latticeY(index)
        val bx = ability.latticeX(previous)
        val by = ability.latticeY(previous)
        if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside
        previous = index
    }
    return inside
}

internal fun MutableGameState.gainStellarMass(stacks: Int) {
    val ability = characterRuntime
    val next = min(CharacterRuntime.MAX_STELLAR_MASS, ability.stellarMass + stacks)
    if (next == ability.stellarMass) return
    ability.stellarMass = next
    refreshCoreRadius()
}

/**
 * Consumes all Stellar Mass into a nova whose radius, damage and element stacks grow with it. During Hypernova
 * a nova comes at the Apex interval whatever the Mass, erases the hostile bolts it covers and strikes the boss-class
 * enemies in Apex reach; one the base gate would not have allowed deals them nothing else.
 */
internal fun MutableGameState.supernova() {
    val ability = characterRuntime
    val stacks = ability.stellarMass
    if (stacks <= 0 && !apexActive() || elapsed < ability.supernovaReadyAt) return
    val radius = supernovaBaseRadius() + SUPERNOVA_RADIUS_PER_STACK * stacks
    val damage = (SUPERNOVA_DAMAGE + SUPERNOVA_DAMAGE_PER_STACK * stacks) * abilityPower()
    val dominant = build.dominant
    val elementStacks = stacks / SUPERNOVA_STACKS_PER_ELEMENT
    val hypernova = apexActive()
    val runtime = skillRuntime
    // The base gate: a nova at least the base cooldown after the last one it allowed reaches bosses as usual.
    val baseNova = !hypernova || elapsed >= runtime.apex(ApexCell.BOSS_BASE_READY_AT)
    if (baseNova) runtime.setApex(ApexCell.BOSS_BASE_READY_AT, elapsed + SUPERNOVA_COOLDOWN)
    ability.stellarMass = 0
    ability.supernovaReadyAt = elapsed + if (hypernova) apexValue(HYPERNOVA_COOLDOWN) else SUPERNOVA_COOLDOWN
    ability.novaFlash = 1f
    refreshCoreRadius()
    // Every Hypernova nova strikes the boss-class enemies in reach; one the base gate would not allow does nothing else.
    if (hypernova) apexStrikeBossesNear(coreX, coreY)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        if (enemy.isBossClass && !baseNova) continue
        dealCharacterDamage(enemy, damage)
        if (dominant != null && elementStacks > 0 && enemy.alive) applyStatus(enemy, dominant, elementStacks, 0)
    }
    if (hypernova) {
        for (index in projectiles.indices) {
            if (runtime.apex(ApexCell.BOLTS_LEFT) < 1f) break
            val shot = projectiles[index]
            if (shot.hostile && distanceSquared(coreX, coreY, shot.x, shot.y) <= square(radius + shot.radius)) eraseApexShot(shot, shot.x, shot.y)
        }
    }
    shockwave(coreX, coreY, 0.5f, radius, 3)
    screenShake = max(screenShake, 8f)
    emitSound(GameplayAudioCue.OVERDRIVE)
}

internal fun MutableGameState.updateEcho(ability: CharacterRuntime, delta: Float) {
    // A Blink is a teleport: the sample it reached is flagged and never replayed as motion. The flag is
    // explicit because the Core keeps moving after the Blink in the same step.
    val teleported = ability.ghostSamples > 0 && blinkThisStep
    ability.recordGhostSample(coreX, coreY, teleported)
    if (ability.ghostSamples < CharacterRuntime.GHOST_SAMPLES) {
        ability.charge = ability.ghostSamples / CharacterRuntime.GHOST_SAMPLES.toFloat()
        return
    }
    val x = ghostX
    val y = ghostY
    val fromX = previousGhostX
    val fromY = previousGhostY
    val speedNow = if (delta > 0f) length(x - fromX, y - fromY) / delta else 0f
    val reach = coreRadius
    // Twin Echo: the ghost swallows bolts three times as often while its bolt budget lasts.
    val twin = apexActive() && skillRuntime.apex(ApexCell.BOLTS_LEFT) >= 1f
    if (elapsed >= ability.ghostAbsorbReadyAt && ghostAbsorbs(x, y, fromX, fromY, if (twin) EraseCause.APEX else EraseCause.CHARACTER)) {
        ability.ghostAbsorbReadyAt = elapsed + if (twin) apexValue(TWIN_ECHO_ABSORB_INTERVAL) else ghostAbsorbInterval()
    }
    ability.charge = if (elapsed >= ability.ghostAbsorbReadyAt) 1f else 0.5f
    if (speedNow < GHOST_MIN_SPEED) return
    val damage = impactPower(speedNow) * GHOST_IMPACT_SHARE
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || enemy.characterMarkTime > 0f || isShifting(enemy)) continue
        if (segmentCircleIntersects(fromX - enemy.previousX, fromY - enemy.previousY, x - enemy.x, y - enemy.y, 0f, 0f,
                reach + enemy.radius)
        ) {
            enemy.characterMarkTime = GHOST_HIT_INTERVAL
            val integrity = enemy.hp
            recordAbilityDamage(integrity, dealProcDamage(enemy, damage))
            directionalBurst(enemy.x, enemy.y, 5, 2, x - fromX, y - fromY)
        }
    }
}

/** The ghost moving from ([fromX], [fromY]) to ([x], [y]) swallows the first hostile bolt it crosses; true when it did. */
private fun MutableGameState.ghostAbsorbs(x: Float, y: Float, fromX: Float, fromY: Float, cause: EraseCause): Boolean {
    val reach = coreRadius
    for (index in projectiles.indices) {
        val shot = projectiles[index]
        if (!shot.hostile || shot.spent || shot.kind != kinetickk.ball.gameplay.nucleus.render.ProjectileKind.BOLT) continue
        if (segmentCircleIntersects(shot.previousX - fromX, shot.previousY - fromY, shot.x - x, shot.y - y, 0f, 0f, reach + shot.radius)) {
            // Spent, not removed: the collision pass removes spent shots once no pass walks the list.
            if (!eraseHostileShot(shot, cause, x, y)) continue
            characterRuntime.damagePrevented += shot.damage.toDouble()
            return true
        }
    }
    return false
}

/** Twin Echo's second ghost: the path [TWIN_ECHO_DELAY_STEPS] behind the Core, once the path is that long. */
internal val MutableGameState.secondGhostActive: Boolean
    get() = coreShape == CoreShape.SPIRAL && apexActive() && characterRuntime.ghostSamples > secondGhostDelaySteps() + 1
internal val MutableGameState.secondGhostX: Float get() = characterRuntime.ghostSampleX(secondGhostDelaySteps())
internal val MutableGameState.secondGhostY: Float get() = characterRuntime.ghostSampleY(secondGhostDelaySteps())

private fun MutableGameState.secondGhostDelaySteps(): Int =
    (apexValue(TWIN_ECHO_DELAY) / MutableGameState.FIXED_STEP).roundToInt().coerceIn(1, CharacterRuntime.GHOST_DELAY_STEPS - 1)

/**
 * Twin Echo: the second ghost strikes what it crosses at speed (ordinary enemies share the first ghost's
 * per-enemy interval; boss-class enemies in Apex reach take only Apex strikes, on their own interval) and swallows
 * a bolt on the Apex interval within the bolt budget.
 */
internal fun MutableGameState.updateSecondGhost() {
    if (!secondGhostActive) return
    val ability = characterRuntime
    val runtime = skillRuntime
    val delay = secondGhostDelaySteps()
    val x = ability.ghostSampleX(delay)
    val y = ability.ghostSampleY(delay)
    val previous = if (ability.ghostSampleTeleported(delay)) delay else delay + 1
    val fromX = ability.ghostSampleX(previous)
    val fromY = ability.ghostSampleY(previous)
    if (elapsed >= runtime.apex(ApexCell.HELD_RELEASE_AT) && runtime.apex(ApexCell.BOLTS_LEFT) >= 1f &&
        ghostAbsorbs(x, y, fromX, fromY, EraseCause.APEX)
    ) runtime.setApex(ApexCell.HELD_RELEASE_AT, elapsed + apexValue(TWIN_ECHO_ABSORB_INTERVAL))
    val speedNow = length(x - fromX, y - fromY) / MutableGameState.FIXED_STEP
    if (speedNow < apexValue(TWIN_ECHO_STRIKE_SPEED)) return
    val damage = impactPower(speedNow) * apexValue(TWIN_ECHO_STRIKE_SCALE)
    val reach = coreRadius
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy)) continue
        if (enemy.isBossClass) {
            if (elapsed < runtime.apex(ApexCell.AUX) || distanceSquared(x, y, enemy.x, enemy.y) > square(APEX_BOSS_REACH + enemy.radius)) continue
            if (apexStrikeFrom(x, y, enemy) > 0f) runtime.setApex(ApexCell.AUX, elapsed + GHOST_HIT_INTERVAL)
            continue
        }
        if (!segmentCircleIntersects(fromX - enemy.previousX, fromY - enemy.previousY, x - enemy.x, y - enemy.y, 0f, 0f,
                reach + enemy.radius)
        ) continue
        if (enemy.characterMarkTime > 0f) continue
        enemy.characterMarkTime = GHOST_HIT_INTERVAL
        val integrity = enemy.hp
        recordAbilityDamage(integrity, dealProcDamage(enemy, damage))
        directionalBurst(enemy.x, enemy.y, 5, FxColorCode.APEX_GOLD, x - fromX, y - fromY)
    }
}
