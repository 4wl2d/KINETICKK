// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_LIFE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_STRIDE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_Y
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Apex forms (combat-final §5.3). [castApex] starts one, [updateApex] ends it; the step below runs what each form
// adds while it is active (and Perihelion's release after it). Against boss-class enemies every form keeps the base
// numbers and cadence and adds only Apex strikes; bolts are erased only within the activation's budget.

/** The character's Apex form begins (its activation cells are already set). */
internal fun MutableGameState.beginApexForm() {
    val ability = characterRuntime
    val runtime = skillRuntime
    when (coreShape) {
        // The base rules' Orbit charge, arc and distance carry into the activation.
        CoreShape.ORB -> {
            runtime.setApex(ApexCell.AUX, ability.charge)
            runtime.setApex(ApexCell.BASE_ARC, ability.turnArc)
            runtime.setApex(ApexCell.BASE_DISTANCE, ability.turnDistance)
        }
        CoreShape.PRISM -> {
            ability.facets = CharacterRuntime.MAX_FACETS
            ability.facetTimer = 0f
        }
        CoreShape.SHARD -> {
            // The last free Dash: none yet, and boss punctures follow the last puncture's base interval.
            runtime.setApex(ApexCell.AUX, Float.NEGATIVE_INFINITY)
            runtime.setApex(ApexCell.BOSS_BASE_READY_AT, ability.punctureReadyAt)
        }
        CoreShape.RING -> runtime.setApex(ApexCell.AUX, elapsed)
        CoreShape.DIAMOND -> Unit
        CoreShape.TESSERACT -> foldHypercube()
        CoreShape.STAR -> {
            // A full Supernova at once, past the base gate; a boss meets it only if the base gate allowed it.
            runtime.setApex(ApexCell.BOSS_BASE_READY_AT, ability.supernovaReadyAt)
            ability.stellarMass = min(CharacterRuntime.MAX_STELLAR_MASS, apexValue(HYPERNOVA_CAST_MASS).toInt())
            ability.supernovaReadyAt = elapsed
            supernova()
        }
        CoreShape.SPIRAL -> {
            runtime.setApex(ApexCell.AUX, 0f)
            runtime.setApex(ApexCell.HELD_RELEASE_AT, 0f)
        }
        CoreShape.DIPOLE -> runtime.setApex(ApexCell.AUX, elapsed + apexValue(MAGNETAR_FLIP_INTERVAL))
        CoreShape.PENTAGON -> runtime.setApex(ApexCell.AUX, beatOrdinal().toFloat())
        CoreShape.CRESCENT -> runtime.setApex(ApexCell.BOSS_BASE_READY_AT, 0f)
        CoreShape.HEXAGON -> {
            // Refreshed drones live the Apex's life, never shorter than the Refit-lengthened base life.
            val life = max(apexValue(SWARM_DRONE_LIFE), droneLifeSeconds())
            for (drone in 0 until ability.droneCount) ability.drones[drone * DRONE_STRIDE + DRONE_LIFE] = life
            while (ability.droneCount < CharacterRuntime.MAX_DRONES) addDrone(life)
            runtime.setApex(ApexCell.AUX, elapsed)
        }
    }
}

/** The character's Apex form ended. */
internal fun MutableGameState.endApexForm() {
    val ability = characterRuntime
    when (coreShape) {
        CoreShape.ORB -> if (skillRuntime.apex(ApexCell.HELD) >= 1f) {
            // Held shots fly back now, or half a second after a shifting boss returns (never more than 9 s away).
            var releaseAt = elapsed
            for (index in enemies.indices) {
                val enemy = enemies[index]
                if (enemy.isBossClass && enemy.alive && isShifting(enemy)) releaseAt = max(releaseAt, enemy.shiftUntil + PERIHELION_SHIFT_GRACE)
            }
            skillRuntime.setApex(ApexCell.HELD_RELEASE_AT, min(releaseAt, elapsed + PERIHELION_MAX_WAIT))
        }
        CoreShape.TESSERACT -> {
            // The cage unfolds: a folded square is cleared, a lattice the player built resumes its life.
            if (ability.latticeCount >= CharacterRuntime.MAX_LATTICE_NODES) {
                ability.latticeCount = 0
                ability.latticeTime = 0f
            } else if (ability.latticeCount > 0) {
                ability.latticeTime = latticeLifeSeconds()
            }
        }
        CoreShape.STAR -> {
            ability.stellarMass = min(CharacterRuntime.MAX_STELLAR_MASS, apexValue(HYPERNOVA_END_MASS).toInt())
            refreshCoreRadius()
        }
        CoreShape.PRISM, CoreShape.SHARD, CoreShape.RING, CoreShape.DIAMOND, CoreShape.SPIRAL, CoreShape.DIPOLE,
        CoreShape.PENTAGON, CoreShape.CRESCENT, CoreShape.HEXAGON -> Unit
    }
}

/** Each step of an unlocked Apex: the running form's own effects and Perihelion's held release. */
internal fun MutableGameState.updateApexForm(delta: Float) {
    if (coreShape == CoreShape.ORB) releaseHeldShots()
    if (!apexActive()) return
    when (coreShape) {
        CoreShape.RING -> updateCorona()
        CoreShape.TESSERACT -> updateHypercube()
        CoreShape.SPIRAL -> updateSecondGhost()
        CoreShape.DIPOLE -> updateMagnetar()
        CoreShape.PENTAGON -> updateCrescendo()
        CoreShape.HEXAGON -> updateSwarmQueen()
        CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD, CoreShape.DIAMOND, CoreShape.STAR, CoreShape.CRESCENT -> Unit
    }
}

/**
 * Perihelion's release: after the activation, one held shot per interval flies at the boss while strikes remain
 * (each a full-cap strike; a boss that starts shifting meanwhile is waited for until half a second past its shift),
 * else at the nearest ordinary enemy within reach with one dominant stack. With nothing in reach the shots wait.
 */
private fun MutableGameState.releaseHeldShots() {
    val runtime = skillRuntime
    val held = runtime.apex(ApexCell.HELD)
    val releaseAt = runtime.apex(ApexCell.HELD_RELEASE_AT)
    if (held < 1f || apexActive() || elapsed < releaseAt) return
    // A boss that starts shifting mid-release is waited for, as at the end of the activation, while strikes remain.
    if (runtime.apex(ApexCell.STRIKES_LEFT) >= 1f) {
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.isBossClass && enemy.alive && isShifting(enemy)) {
                runtime.setApex(ApexCell.HELD_RELEASE_AT, enemy.shiftUntil + PERIHELION_SHIFT_GRACE)
                return
            }
        }
    }
    val target = apexShotTarget(coreX, coreY, PERIHELION_RELEASE_RANGE) ?: return
    runtime.setApex(ApexCell.HELD, held - 1f)
    // On the release schedule (a shot that waited for a target restarts it), so fixed steps add no drift.
    val interval = apexValue(PERIHELION_RELEASE_INTERVAL)
    runtime.setApex(ApexCell.HELD_RELEASE_AT, max(releaseAt + interval, elapsed + interval - MutableGameState.FIXED_STEP))
    fireApexShot(coreX, coreY, target, apexValue(PERIHELION_HELD_DAMAGE) * abilityPower(),
        if (target.isBossClass) null else build.dominant)
}

/**
 * Corona: hostile bolts crossing the locked band are erased, and an Apex Halo Burst fires on its own interval
 * whatever the speed: ordinary enemies in its reach take its damage, boss-class enemies in Apex reach only a strike.
 */
private fun MutableGameState.updateCorona() {
    val runtime = skillRuntime
    val radius = characterRuntime.haloRadius
    for (index in projectiles.indices) {
        if (runtime.apex(ApexCell.BOLTS_LEFT) < 1f) break
        val shot = projectiles[index]
        if (!shot.hostile || shot.spent) continue
        val now = length(shot.x - coreX, shot.y - coreY)
        val before = length(shot.previousX - previousCoreX, shot.previousY - previousCoreY)
        val inBand = now >= radius - HALO_BAND - shot.radius && now <= radius + HALO_BAND + shot.radius
        val crossed = (now - radius) * (before - radius) < 0f
        if (inBand || crossed) eraseApexShot(shot, shot.x, shot.y)
    }
    if (elapsed < runtime.apex(ApexCell.AUX)) return
    runtime.setApex(ApexCell.AUX, elapsed + apexValue(CORONA_BURST_INTERVAL))
    val damage = apexValue(CORONA_BURST_DAMAGE) * abilityPower()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || enemy.isBossClass || distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(HALO_BURST_RADIUS + enemy.radius)) continue
        dealCharacterDamage(enemy, damage)
    }
    apexStrikeBossesNear(coreX, coreY)
    characterRuntime.haloBurstFlash = 1f
    shockwave(coreX, coreY, 0.35f, HALO_BURST_RADIUS, FxColorCode.APEX_GOLD)
}

/** True while Hypercube holds the lattice as its cage. */
internal fun MutableGameState.hypercubeRunning(): Boolean = coreShape == CoreShape.TESSERACT && apexActive()

/** Hypercube's cage: the lattice when it has three nodes or more, else a square around the Core in the node slots. */
private fun MutableGameState.foldHypercube() {
    val ability = characterRuntime
    if (ability.latticeCount < 3) {
        val half = apexValue(HYPERCUBE_HALF_SIDE)
        val nodes = ability.latticeNodes
        nodes[0] = coreX - half
        nodes[1] = coreY - half
        nodes[2] = coreX + half
        nodes[3] = coreY - half
        nodes[4] = coreX + half
        nodes[5] = coreY + half
        nodes[6] = coreX - half
        nodes[7] = coreY + half
        ability.latticeCount = CharacterRuntime.MAX_LATTICE_NODES
    }
    skillRuntime.setApex(ApexCell.AUX, elapsed + HYPERCUBE_PULSE_SECONDS)
}

/**
 * Hypercube: bolts crossing a cage edge are erased (bolt budget); every pulse interval the edges strike what
 * touches them, harder inside, and boss-class enemies in Apex reach of the Core take only a strike.
 */
private fun MutableGameState.updateHypercube() {
    val ability = characterRuntime
    val count = ability.latticeCount
    if (count < 2) return
    val runtime = skillRuntime
    for (index in projectiles.indices) {
        if (runtime.apex(ApexCell.BOLTS_LEFT) < 1f) break
        val shot = projectiles[index]
        if (!shot.hostile || shot.spent) continue
        for (edge in 0 until count) {
            val next = (edge + 1) % count
            if (segmentsCross(shot.previousX, shot.previousY, shot.x, shot.y,
                    ability.latticeX(edge), ability.latticeY(edge), ability.latticeX(next), ability.latticeY(next))
            ) {
                eraseApexShot(shot, shot.x, shot.y)
                break
            }
        }
    }
    if (elapsed < runtime.apex(ApexCell.AUX)) return
    runtime.setApex(ApexCell.AUX, elapsed + HYPERCUBE_PULSE_SECONDS)
    val damage = (apexValue(HYPERCUBE_PULSE_DAMAGE) + apexValue(HYPERCUBE_PULSE_PER_NODE) * count) * abilityPower()
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || enemy.isBossClass) continue
        var touches = false
        for (edge in 0 until count) {
            val next = (edge + 1) % count
            if (segmentCircleIntersects(ability.latticeX(edge), ability.latticeY(edge), ability.latticeX(next), ability.latticeY(next),
                    enemy.x, enemy.y, enemy.radius + HYPERCUBE_EDGE_REACH)
            ) {
                touches = true
                break
            }
        }
        val inside = count >= 3 && insideLattice(ability, count, enemy.x, enemy.y)
        if (!touches && !inside) continue
        dealCharacterDamage(enemy, damage * if (inside) LATTICE_INSIDE_MULTIPLIER else 1f)
    }
    // The pulse runs through the Core the cage is folded from: boss-class enemies in reach of it take a strike.
    apexStrikeBossesNear(coreX, coreY)
    for (edge in 0 until count) {
        val next = (edge + 1) % count
        addWeaponArc(ability.latticeX(edge), ability.latticeY(edge), ability.latticeX(next), ability.latticeY(next), 0.2f,
            LinkStyleCode.apex(CoreShape.TESSERACT))
    }
}

/** Hypercube slows the ordinary enemies inside its cage (their motion scale). */
internal fun MutableGameState.hypercubeMotionScale(enemy: Enemy): Float {
    if (!hypercubeRunning() || enemy.isEliteClass) return 1f
    val ability = characterRuntime
    val count = ability.latticeCount
    return if (count >= 3 && insideLattice(ability, count, enemy.x, enemy.y)) apexValue(HYPERCUBE_INSIDE_SPEED) else 1f
}

/**
 * Magnetar: polarity flips on its own clock (turns still flip on theirs); in Repel the hostile bolts closing on
 * the Core within the wide field are erased and fired back along their reflected path as Apex shots.
 */
private fun MutableGameState.updateMagnetar() {
    val runtime = skillRuntime
    if (elapsed >= runtime.apex(ApexCell.AUX)) {
        runtime.setApex(ApexCell.AUX, elapsed + apexValue(MAGNETAR_FLIP_INTERVAL))
        flipPolarity(restartGate = false)
    }
    if (characterRuntime.polarity != Polarity.REPEL) return
    val reach = repelRadius() * magnetarReachScale()
    val damage = apexValue(MAGNETAR_SHOT_DAMAGE) * abilityPower()
    // Re-fired shots are appended; only the bolts present at the start of the pass are looked at.
    val count = projectiles.size
    for (index in 0 until count) {
        if (runtime.apex(ApexCell.BOLTS_LEFT) < 1f) return
        val shot = projectiles[index]
        if (!shot.hostile || shot.spent) continue
        val dx = shot.x - coreX
        val dy = shot.y - coreY
        val distance = length(dx, dy)
        if (distance < 1f || distance > reach + shot.radius) continue
        val nx = dx / distance
        val ny = dy / distance
        val closing = shot.vx * nx + shot.vy * ny
        if (closing >= 0f) continue
        val vx = shot.vx
        val vy = shot.vy
        if (!eraseApexShot(shot, shot.x, shot.y)) continue
        // Reflect the velocity off the field's normal at the bolt.
        val rx = vx - 2f * closing * nx
        val ry = vy - 2f * closing * ny
        val speed = length(rx, ry)
        if (speed > 0f) fireApexShotAlong(shot.x, shot.y, rx / speed, ry / speed, damage)
    }
}

/** The index of the Pentagon beat the current moment belongs to (the beat at or before it). */
private fun MutableGameState.beatOrdinal(): Int = floor(elapsed / BEAT_PERIOD).toInt()

/**
 * Crescendo: on each new beat an on-beat burst strikes ordinary enemies nearby (boss-class enemies in Apex reach only
 * take a strike) and erases the hostile bolts around the Core, a few per beat within the bolt budget.
 */
private fun MutableGameState.updateCrescendo() {
    val runtime = skillRuntime
    val beat = beatOrdinal()
    if (beat <= runtime.apex(ApexCell.AUX).toInt()) return
    runtime.setApex(ApexCell.AUX, beat.toFloat())
    val radius = apexValue(CRESCENDO_BURST_RADIUS)
    val damage = apexValue(CRESCENDO_BURST_DAMAGE) * abilityPower()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || enemy.isBossClass || distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        dealCharacterDamage(enemy, damage)
    }
    apexStrikeBossesNear(coreX, coreY)
    var erased = 0
    val perBeat = apexValue(CRESCENDO_BOLTS_PER_BEAT).toInt()
    for (index in projectiles.indices) {
        if (erased >= perBeat || runtime.apex(ApexCell.BOLTS_LEFT) < 1f) break
        val shot = projectiles[index]
        if (shot.hostile && distanceSquared(coreX, coreY, shot.x, shot.y) <= square(radius + shot.radius) &&
            eraseApexShot(shot, shot.x, shot.y)
        ) erased++
    }
    shockwave(coreX, coreY, 0.25f, radius, FxColorCode.APEX_GOLD)
}

/**
 * Swarm Queen: every intercept interval each drone erases one hostile bolt near it (bolt budget), and loose Data
 * in reach latches onto the Core (Event Horizon's liability keeps the latch off).
 */
private fun MutableGameState.updateSwarmQueen() {
    val ability = characterRuntime
    val runtime = skillRuntime
    if (elapsed >= runtime.apex(ApexCell.AUX)) {
        runtime.setApex(ApexCell.AUX, elapsed + SWARM_INTERCEPT_SECONDS)
        for (drone in 0 until ability.droneCount) {
            if (runtime.apex(ApexCell.BOLTS_LEFT) < 1f) break
            val x = ability.droneValue(drone, DRONE_X)
            val y = ability.droneValue(drone, DRONE_Y)
            for (index in projectiles.indices) {
                val shot = projectiles[index]
                if (shot.hostile && distanceSquared(x, y, shot.x, shot.y) <= square(SWARM_INTERCEPT_REACH + shot.radius) &&
                    eraseApexShot(shot, x, y)
                ) break
            }
        }
    }
    if (!latchedVacuumActive()) return
    val reach = square(apexValue(SWARM_DATA_RANGE))
    for (index in pickups.indices) {
        val pickup = pickups[index]
        if (pickup.type == kinetickk.ball.gameplay.nucleus.render.PickupType.DATA && !pickup.attracted &&
            distanceSquared(coreX, coreY, pickup.x, pickup.y) <= reach
        ) pickup.attracted = true
    }
}

/** Vivisect: a Dash at least the free gap after the last free one costs no heat. */
internal fun MutableGameState.apexFreeDashReady(): Boolean =
    coreShape == CoreShape.SHARD && apexActive() && elapsed >= skillRuntime.apex(ApexCell.AUX) + apexValue(VIVISECT_FREE_DASH_GAP)

/** A Dash during Vivisect cuts into the boss-class enemies in reach of the Core: Apex strikes. */
internal fun MutableGameState.vivisectDash() {
    if (coreShape == CoreShape.SHARD && apexActive()) apexStrikeBossesNear(coreX, coreY)
}

/** Spends Vivisect's free Dash; true when this Dash costs no heat. */
internal fun MutableGameState.spendApexFreeDash(): Boolean {
    if (!apexFreeDashReady()) return false
    skillRuntime.setApex(ApexCell.AUX, elapsed)
    return true
}
