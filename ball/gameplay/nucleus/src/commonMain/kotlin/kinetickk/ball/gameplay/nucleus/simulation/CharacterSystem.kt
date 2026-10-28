// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_DART
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_LIFE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_PREVIOUS_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_PREVIOUS_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_READY_AT
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_STRIDE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_TARGET_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_TARGET_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_AGE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_DETONATE_AT
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_STRIDE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_X0
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_X1
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_Y0
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_Y1
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The twelve character mechanics. Every one reads accepted movement, combat and pickups inside the fixed
 * step; none draws randomness from the gameplay or reward streams (the Hive uses a stateless hash).
 */

// ---------------------------------------------------------------------------------------------
// Step lifecycle.

/** Ages the one-step event flags at the start of each fixed step (see [parriedThisStep], [blinkThisStep]). */
internal fun MutableGameState.ageCharacterStepFlags() {
    val ability = characterRuntime
    if (ability.parryFlagSteps > 0) ability.parryFlagSteps--
    if (ability.blinkFlagSteps > 0) ability.blinkFlagSteps--
}

/** Applies the character's base traits; called by the run reset before the Matrix and items. */
internal fun MutableGameState.applyCharacterTraits() {
    val traits = content.coreShape(coreShape).traits
    mass *= traits.massMultiplier
    maxHp += traits.integrityBonus
    dragCoefficient *= traits.dragMultiplier
    dashHeatCost *= traits.dashHeatMultiplier
    critChance += traits.critChanceBonus
    critMultiplier += traits.critDamageBonus
    pickupRadius += traits.pickupRadiusBonus
    refreshCoreRadius()
}

/** Core radius from the trait multiplier and, for the Star, Stellar Mass. */
internal fun MutableGameState.refreshCoreRadius() {
    val stacks = if (coreShape == CoreShape.STAR) characterRuntime.stellarMass else 0
    coreRadius = MutableGameState.CORE_RADIUS * content.coreShape(coreShape).traits.radiusMultiplier *
        (1f + STAR_RADIUS_PER_STACK * stacks)
}

internal fun MutableGameState.updateCharacterRuntime(delta: Float) {
    val ability = characterRuntime
    val currentSpeed = speed
    val moved = length(coreX - previousCoreX, coreY - previousCoreY)
    val heading = atan2(smoothedVelocityY, smoothedVelocityX)
    val turn = atan2(sin(heading - ability.previousHeading), cos(heading - ability.previousHeading))
    val brakingStarted = braking && !ability.wasBraking
    if (ability.parryWindow.toRawBits() != 0) ability.parryWindow = max(0f, ability.parryWindow - delta)
    if (ability.parryCooldown.toRawBits() != 0) ability.parryCooldown = max(0f, ability.parryCooldown - delta)
    // Hypercube holds the lattice as its cage: its nodes do not expire while it runs.
    if (ability.latticeTime.toRawBits() != 0 && !hypercubeRunning()) {
        ability.latticeTime = max(0f, ability.latticeTime - delta)
        if (ability.latticeTime <= 0f) ability.latticeCount = 0
    }
    if (ability.haloBurstFlash.toRawBits() != 0) ability.haloBurstFlash = max(0f, ability.haloBurstFlash - delta * FLASH_DECAY)
    if (ability.novaFlash.toRawBits() != 0) ability.novaFlash = max(0f, ability.novaFlash - delta * FLASH_DECAY)
    if (ability.polarityFlash.toRawBits() != 0) ability.polarityFlash = max(0f, ability.polarityFlash - delta * FLASH_DECAY)
    if (ability.edge > 0 && elapsed >= ability.edgeUntil) ability.edge = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.characterMarkTime > 0f) enemy.characterMarkTime = max(0f, enemy.characterMarkTime - delta)
    }
    when (coreShape) {
        CoreShape.ORB -> updateOrbit(ability, turn, moved, currentSpeed)
        CoreShape.PRISM -> updateFacets(ability, delta)
        CoreShape.SHARD -> ability.charge = if (elapsed >= ability.punctureReadyAt) 1f else 0f
        CoreShape.RING -> updateHalo(ability, delta, currentSpeed, moved)
        CoreShape.DIAMOND -> {
            if (brakingStarted && ability.previousSpeed >= PARRY_MIN_SPEED && moved > 0f && ability.parryCooldown <= 0f) {
                ability.parryWindow = parryWindowSeconds()
                ability.parryCooldown = PARRY_COOLDOWN_SECONDS
            }
            ability.charge = if (ability.parryWindow > 0f) 1f else max(0f, 1f - ability.parryCooldown / PARRY_COOLDOWN_SECONDS)
        }
        CoreShape.TESSERACT -> updateLattice(ability)
        CoreShape.STAR -> {
            // Hypernova: a nova comes on every Apex cooldown, with whatever Mass the Star holds.
            if (ability.stellarMass >= CharacterRuntime.MAX_STELLAR_MASS || apexActive()) supernova()
            ability.charge = ability.stellarMass / CharacterRuntime.MAX_STELLAR_MASS.toFloat()
        }
        CoreShape.SPIRAL -> updateEcho(ability, delta)
        CoreShape.DIPOLE -> updatePolarity(ability, delta)
        CoreShape.PENTAGON -> {
            if (ability.beatStreak > 0 && elapsed > (ability.streakBeat + 2) * BEAT_PERIOD + BEAT_WINDOW) ability.beatStreak = 0
            ability.charge = ability.beatStreak / CharacterRuntime.MAX_BEAT_STREAK.toFloat()
        }
        CoreShape.CRESCENT -> updateEclipse(ability, delta)
        CoreShape.HEXAGON -> updateHive(ability, delta)
    }
    if (apexUnlocked) updateApexForm(delta)
    ability.previousSpeed = currentSpeed
    ability.previousHeading = heading
    ability.wasBraking = braking
}

// ---------------------------------------------------------------------------------------------
// Dash, impact, hit, kill, pickup and turn hooks.

internal fun MutableGameState.beginCharacterDash() {
    val ability = characterRuntime
    ability.dashSequence++
    ability.hasDashOrigin = true
    ability.dashOriginX = coreX
    ability.dashOriginY = coreY
    ability.dashRecorded = false
}

/**
 * The Dash phase ran out. What belongs to that Dash (the Tesseract's origin, the Pentagon's beat) ends with
 * it, so a later phase that no Dash started (Gravity Surfer's ricochet) never revives it.
 */
internal fun MutableGameState.endCharacterDash() {
    characterRuntime.hasDashOrigin = false
    characterRuntime.dashOnBeat = false
}

/** A Dash's first contact with [enemy]: counts the Dash hit and marks it for the Shard. */
internal fun MutableGameState.onCharacterDashContact(enemy: Enemy) {
    if (dashPhaseTime <= 0f || enemy.lastCharacterDash == characterRuntime.dashSequence || enemy.dead || enemy.hp <= 0f) return
    enemy.lastCharacterDash = characterRuntime.dashSequence
    pendingDashHits++
    if (coreShape == CoreShape.SHARD) enemy.characterMarkTime = MARK_SECONDS
}

/**
 * An impact or a qualifying weapon hit on [enemy]: releases Orbit, and opens Shard marks unless the
 * hit is a [dash] impact.
 */
internal fun MutableGameState.onCharacterPrimaryHit(enemy: Enemy, dash: Boolean = false) {
    val ability = characterRuntime
    when (coreShape) {
        CoreShape.ORB -> if (ability.charge >= 1f) releaseOrbit(coreX, coreY, enemy)
        CoreShape.SHARD -> if (!dash && punctureReady(enemy) && consumeMark(enemy)) puncture(enemy, 1f)
        else -> Unit
    }
}

/** Releases a charged Orbit ring around ([x], [y]); the [struck] enemy carries the release's element. */
private fun MutableGameState.releaseOrbit(x: Float, y: Float, struck: Enemy?) {
    val ability = characterRuntime
    ability.charge = 0f
    ability.turnArc = 0f
    ability.turnDistance = 0f
    val damage = ORBIT_DAMAGE * abilityPower()
    val dominant = build.dominant
    // During Perihelion a release reaches boss-class enemies only when the base rules would have charged it too, and
    // only such a release spends the base rules' charge (their arc and distance start again, as a base release's do).
    val perihelion = apexActive()
    val reachesBosses = !perihelion || skillRuntime.apex(ApexCell.AUX) >= 1f
    if (perihelion && reachesBosses) {
        skillRuntime.setApex(ApexCell.AUX, 0f)
        skillRuntime.setApex(ApexCell.BASE_ARC, 0f)
        skillRuntime.setApex(ApexCell.BASE_DISTANCE, 0f)
    }
    for (index in enemies.indices) {
        val target = enemies[index]
        if (!target.alive || distanceSquared(x, y, target.x, target.y) > square(ORBIT_RADIUS + target.radius)) continue
        if (target.isBossClass && !reachesBosses) continue
        dealCharacterDamage(target, damage)
    }
    if (dominant != null && struck != null && struck.alive) applyStatus(struck, dominant, ORBIT_STACKS, 0)
    shockwave(x, y, 0.35f, ORBIT_RADIUS, 2)
    if (apexActive()) captureHostileShots()
}

/** Perihelion: a release captures the hostile bolts around the Core into the held store (at most the bolt budget). */
private fun MutableGameState.captureHostileShots() {
    val runtime = skillRuntime
    val capacity = apexDefinition.boltBudget.toFloat()
    val reach = apexValue(PERIHELION_CAPTURE_RADIUS)
    for (index in projectiles.indices) {
        if (runtime.apex(ApexCell.HELD) >= capacity || runtime.apex(ApexCell.BOLTS_LEFT) < 1f) return
        val shot = projectiles[index]
        if (!shot.hostile || distanceSquared(coreX, coreY, shot.x, shot.y) > square(reach + shot.radius)) continue
        if (eraseApexShot(shot, shot.x, shot.y)) runtime.setApex(ApexCell.HELD, runtime.apex(ApexCell.HELD) + 1f)
    }
}

/**
 * A skill cast from [slot] centred on ([anchorX], [anchorY]) whose first target was [primary]. The
 * Circle releases a charged Orbit there, the Tesseract records a lattice node at the Core, the Dipole flips at
 * once, an on-beat Pentagon cast counts as an on-beat action and takes back a share of its cooldown, the
 * Crescent's Blink recharges faster and the Hexagon's drones dart at the target. The Square (shorter cooldown),
 * Triangle (skill hits marked), Ring (wider self skills), Diamond (parries refund), Star (skill kills) and Spiral
 * (the ghost repeats self skills) act where those facts are read.
 */
internal fun MutableGameState.onCharacterSkillCast(slot: Int, anchorX: Float, anchorY: Float, primary: Enemy?) {
    val ability = characterRuntime
    when (coreShape) {
        CoreShape.ORB -> if (ability.charge >= 1f) releaseOrbit(anchorX, anchorY, primary)
        CoreShape.TESSERACT -> recordLatticeNode(coreX, coreY)
        CoreShape.DIPOLE -> flipPolarity()
        CoreShape.PENTAGON -> if (isOnBeat()) {
            onBeatAction()
            refundSkill(slot, skillRuntime[slot, SkillCell.LAST_COOLDOWN] * PENTAGON_SKILL_REFUND)
        }
        CoreShape.CRESCENT -> advanceBlinkRecharge(CRESCENT_SKILL_BLINK_SECONDS)
        CoreShape.HEXAGON -> if (primary != null) dronesDartAt(primary)
        CoreShape.PRISM, CoreShape.SHARD, CoreShape.RING, CoreShape.DIAMOND, CoreShape.STAR, CoreShape.SPIRAL -> Unit
    }
}

/**
 * Damage multiplier the character adds to an impact on [enemy] (null for none in particular) on its way in: all
 * facets intact (Monolith raises it), Stellar Mass, an on-beat impact and the beat streak (Crescendo holds it
 * full). An Apex never raises it on a boss-class enemy.
 */
internal fun MutableGameState.characterImpactMultiplier(enemy: Enemy? = null): Float = when (coreShape) {
    CoreShape.PRISM -> if (characterRuntime.facets < CharacterRuntime.MAX_FACETS) 1f
        else if (amplifies(enemy)) apexValue(MONOLITH_IMPACT_BONUS) else FACET_IMPACT_BONUS
    CoreShape.STAR -> 1f + STAR_IMPACT_PER_STACK * characterRuntime.stellarMass
    CoreShape.PENTAGON -> {
        val streak = if (amplifies(enemy)) CharacterRuntime.MAX_BEAT_STREAK else characterRuntime.beatStreak
        (if (isOnBeat()) BEAT_IMPACT_MULTIPLIER else 1f) * (1f + STREAK_IMPACT_PER_STACK * streak)
    }
    else -> 1f
}

/**
 * Damage multiplier on direct hits and ability damage: the Diamond's Edge, held full against ordinary enemies
 * ([enemy]; null for none in particular) while Mirror Stance runs.
 */
internal fun MutableGameState.characterDamageMultiplier(enemy: Enemy? = null): Float =
    if (coreShape == CoreShape.DIAMOND) 1f + EDGE_DAMAGE_PER_STACK * effectiveEdge(enemy) else 1f

/** The Diamond's Edge against [enemy]: Mirror Stance locks it at full on ordinary enemies. */
private fun MutableGameState.effectiveEdge(enemy: Enemy?): Int =
    if (amplifies(enemy)) CharacterRuntime.MAX_EDGE else characterRuntime.edge

/** The running Apex amplifies the mechanic on [enemy]; never on a boss-class enemy or none in particular. */
private fun MutableGameState.amplifies(enemy: Enemy?): Boolean = enemy != null && apexAmplifies(enemy)

// ---------------------------------------------------------------------------------------------
// Refit signatures. Each mechanic reads its one upgradable constant through an accessor that applies the
// selected character's Refit level with the content step ([kinetickk.ball.content.api.CharacterUpgradeSignature]);
// the render mapper reads the same accessors.

/** Orbit: the turn arc that charges the next impact (Refit shortens it by a share per level). */
internal fun MutableGameState.orbitArc(): Float = refitSignature(CoreShape.ORB, ORBIT_ARC, share = true)

/** Facets: seconds to regrow one facet (Refit shortens them by a share per level). */
internal fun MutableGameState.facetRegenSeconds(): Float =
    refitSignature(CoreShape.PRISM, FACET_REGEN_SECONDS, share = true)

/** Puncture: heat vented by a puncture. */
internal fun MutableGameState.punctureHeatRefund(): Float = refitSignature(CoreShape.SHARD, PUNCTURE_HEAT_REFUND)

/** Halo: the radius the ring gains at full speed over its minimum. */
internal fun MutableGameState.haloExtraRadius(): Float = refitSignature(CoreShape.RING, HALO_EXTRA_RADIUS)

/** Parry: seconds the parry window stays open. */
internal fun MutableGameState.parryWindowSeconds(): Float = refitSignature(CoreShape.DIAMOND, PARRY_WINDOW_SECONDS)

/** Lattice: seconds an open lattice lasts after its newest node. */
internal fun MutableGameState.latticeLifeSeconds(): Float = refitSignature(CoreShape.TESSERACT, LATTICE_LIFE_SECONDS)

/** Nova: the Supernova radius before Stellar Mass. */
internal fun MutableGameState.supernovaBaseRadius(): Float = refitSignature(CoreShape.STAR, SUPERNOVA_RADIUS)

/** Echo: seconds between two hostile shots the ghost swallows. */
internal fun MutableGameState.ghostAbsorbInterval(): Float = refitSignature(CoreShape.SPIRAL, GHOST_ABSORB_INTERVAL)

/** Polarity: the Attract field's radius. */
internal fun MutableGameState.attractRadius(): Float = refitSignature(CoreShape.DIPOLE, ATTRACT_RADIUS)

/** Polarity: the Repel field's radius (the signature's secondary step). */
internal fun MutableGameState.repelRadius(): Float =
    refitSignature(CoreShape.DIPOLE, REPEL_RADIUS, secondary = true)

/** Pulse: the on-beat burst's radius. */
internal fun MutableGameState.beatBurstRadius(): Float = refitSignature(CoreShape.PENTAGON, BEAT_BURST_RADIUS)

/** Eclipse: seconds the base rules take to recharge one Blink (Total Eclipse reads [blinkRechargeSeconds]). */
internal fun MutableGameState.baseBlinkRechargeSeconds(): Float =
    refitSignature(CoreShape.CRESCENT, BLINK_RECHARGE_SECONDS)

/** Hive: seconds a drone lives without a Data refresh. */
internal fun MutableGameState.droneLifeSeconds(): Float = refitSignature(CoreShape.HEXAGON, DRONE_LIFE_SECONDS)

/**
 * [base] moved by the Refit step of [shape] times the run's Refit level: added, or as a [share] of [base]. Another
 * character, or level 0, reads [base] itself. A step never takes a value below [REFIT_SIGNATURE_FLOOR_SHARE] of
 * [base] (the Blink recharge stays at or above 1.1 s, a facet regrows in at least 3 s).
 */
private fun MutableGameState.refitSignature(
    shape: CoreShape,
    base: Float,
    share: Boolean = false,
    secondary: Boolean = false,
): Float {
    val level = characterUpgradeLevel
    if (level == 0 || coreShape != shape) return base
    val signature = content.coreShape(shape).upgradeSignature
    val step = (if (secondary) signature.secondaryPerLevel else signature.perLevel) * level
    return max(base * REFIT_SIGNATURE_FLOOR_SHARE, if (share) base * (1f + step) else base + step)
}

/** Mass multiplier from Stellar Mass. */
internal fun MutableGameState.characterMassMultiplier(): Float =
    if (coreShape == CoreShape.STAR) 1f + STAR_MASS_PER_STACK * characterRuntime.stellarMass else 1f

/** Full Edge turns the next impact on [enemy] into a critical hit; the caller spends it with [spendEdgeCritical]. */
internal fun MutableGameState.edgeCriticalReady(enemy: Enemy? = null): Boolean =
    coreShape == CoreShape.DIAMOND && effectiveEdge(enemy) >= CharacterRuntime.MAX_EDGE

/** Spends full Edge after a critical impact on [enemy]; the Edge Mirror Stance locks is not spent. */
internal fun MutableGameState.spendEdgeCritical(enemy: Enemy? = null) {
    if (!amplifies(enemy)) characterRuntime.edge = 0
}

/**
 * After an impact on [enemy] dealt [result] at [impactSpeed] with [multiplier] from the character:
 * heavy impacts feed Facets and Stellar Mass, on-beat impacts pulse, and the multiplier's share counts
 * as ability output.
 */
internal fun MutableGameState.onCharacterImpact(enemy: Enemy, result: DamageResult, impactSpeed: Float, multiplier: Float) {
    val ability = characterRuntime
    val heavy = result.amount >= enemy.maxHp * HEAVY_IMPACT_SHARE && impactSpeed >= HEAVY_IMPACT_SPEED
    when (coreShape) {
        CoreShape.PRISM -> if (heavy) cutFacetTimer()
        CoreShape.STAR -> {
            if (multiplier > 1f) ability.abilityDamage += impactShare(enemy, result, multiplier)
            if (heavy && enemy.alive) gainStellarMass(2)
        }
        CoreShape.DIPOLE -> if (ability.polarity == Polarity.REPEL && enemy.alive) {
            // REPEL throws what the Core strikes away from it at the impact speed.
            val knockback = knockbackTaken(enemy)
            val dx = enemy.x - coreX
            val dy = enemy.y - coreY
            val distance = length(dx, dy)
            if (knockback > 0f && distance >= 1f) {
                // Magnetar throws ordinary enemies harder.
                val magnetar = if (apexAmplifies(enemy)) apexValue(MAGNETAR_THROW_SCALE) else 1f
                val throwSpeed = impactSpeed * REPEL_THROW_SHARE * knockback * magnetar
                enemy.vx += dx / distance * throwSpeed
                enemy.vy += dy / distance * throwSpeed
                if (!enemy.isBossClass) enemy.characterMarkTime = THROW_SECONDS
            }
        }
        CoreShape.PENTAGON -> {
            if (multiplier > 1f) ability.abilityDamage += impactShare(enemy, result, multiplier)
            // Crescendo puts every impact on the beat; only a real beat raises the streak or pulses boss-class enemies.
            val realBeat = isOnBeat()
            if (realBeat || apexActive()) {
                if (realBeat) onBeatAction()
                val beat = currentBeatIndex()
                if (ability.pulseBeat != beat) {
                    ability.pulseBeat = beat
                    val burst = BEAT_BURST_DAMAGE * abilityPower()
                    val burstRadius = beatBurstRadius()
                    for (index in enemies.indices) {
                        val target = enemies[index]
                        if (!realBeat && target.isBossClass) continue
                        if (target.alive && distanceSquared(coreX, coreY, target.x, target.y) <= square(burstRadius + target.radius)) {
                            dealCharacterDamage(target, burst)
                        }
                    }
                    shockwave(coreX, coreY, 0.25f, burstRadius, 2)
                }
            }
        }
        else -> Unit
    }
}

/** The share of an impact the character's [multiplier] added, up to the integrity the hit removed. */
private fun impactShare(enemy: Enemy, result: DamageResult, multiplier: Float): Double {
    val removed = min(result.amount, result.amount + enemy.hp.coerceAtMost(0f))
    return (removed * (1f - 1f / multiplier)).toDouble().coerceAtLeast(0.0)
}

/** A resolved kill: Elite kills restore a facet, kills gather Stellar Mass. */
internal fun MutableGameState.onCharacterKill(enemy: Enemy, eliteReward: Boolean) {
    val lastSource = enemy.status[StatusCell.LAST_SOURCE].toInt() - 1
    val impactKill = lastSource == HitSource.IMPACT.ordinal || lastSource == HitSource.DASH.ordinal
    val ability = characterRuntime
    when (coreShape) {
        CoreShape.PRISM -> {
            if (eliteReward && ability.facets < CharacterRuntime.MAX_FACETS) ability.facets++
            if (impactKill) cutFacetTimer()
        }
        CoreShape.STAR -> {
            // A skill kill gathers one more; Hypernova gathers more from every kill.
            val skillKill = lastSource == HitSource.SKILL.ordinal
            val gathered = if (apexActive()) apexValue(HYPERNOVA_KILL_MASS).toInt() + (if (impactKill) 1 else 0)
                else if (impactKill) 2 else 1
            gainStellarMass(gathered + (if (skillKill) STAR_SKILL_KILL_MASS else 0))
            if (impactKill && ability.stellarMass >= SUPERNOVA_EARLY_STACKS) supernova()
        }
        else -> Unit
    }
}

/** Real damage reached the shield or integrity: the Star sheds a quarter of its mass (nothing during Hypernova). */
internal fun MutableGameState.onCharacterDamaged() {
    if (coreShape != CoreShape.STAR || apexActive()) return
    val ability = characterRuntime
    if (ability.stellarMass <= 0) return
    ability.stellarMass -= (ability.stellarMass + 3) / 4
    refreshCoreRadius()
}

/** A Data pickup: the Hive may build a drone and refreshes the ones it has. */
internal fun MutableGameState.onCharacterDataPickup() {
    if (coreShape != CoreShape.HEXAGON) return
    val ability = characterRuntime
    val droneLife = droneLifeSeconds()
    for (index in 0 until ability.droneCount) ability.drones[index * DRONE_STRIDE + DRONE_LIFE] = droneLife
    if (ability.droneCount >= CharacterRuntime.MAX_DRONES) return
    ability.droneRolls++
    val roll = ((characterHash(pickupsCollected + DRONE_HASH_SALT) ushr 40) and 0xFFFFL).toInt()
    if (roll >= (DRONE_CHANCE * 65_536f).toInt() && ability.droneRolls < DRONE_GUARANTEE) return
    ability.droneRolls = 0
    addDrone(droneLife)
}

/** Builds a drone at the Core that lives [life] seconds (the caller keeps the hive below its bound). */
private fun MutableGameState.addDrone(life: Float) {
    val ability = characterRuntime
    val base = ability.droneCount * DRONE_STRIDE
    val drones = ability.drones
    drones[base + DRONE_X] = coreX
    drones[base + DRONE_Y] = coreY
    drones[base + DRONE_PREVIOUS_X] = coreX
    drones[base + DRONE_PREVIOUS_Y] = coreY
    drones[base + DRONE_LIFE] = life
    drones[base + DRONE_READY_AT] = elapsed + DRONE_FIRST_DART_SECONDS
    drones[base + DRONE_DART] = 0f
    drones[base + DRONE_TARGET_X] = coreX
    drones[base + DRONE_TARGET_Y] = coreY
    ability.droneCount++
}

/** A real TURN edge: the Dipole flips polarity (at most every two seconds). */
internal fun MutableGameState.onCharacterTurn() {
    if (coreShape != CoreShape.DIPOLE) return
    if (elapsed < characterRuntime.polarityFlipReadyAt) return
    flipPolarity()
}

/** Flips the Dipole's polarity now and, unless Magnetar's own clock flips it ([restartGate] false), restarts its flip gate. */
private fun MutableGameState.flipPolarity(restartGate: Boolean = true) {
    val ability = characterRuntime
    ability.polarity = if (ability.polarity == Polarity.ATTRACT) Polarity.REPEL else Polarity.ATTRACT
    if (restartGate) ability.polarityFlipReadyAt = elapsed + POLARITY_FLIP_INTERVAL
    ability.polarityFlash = 1f
    val reach = (if (ability.polarity == Polarity.ATTRACT) attractRadius() else repelRadius()) * magnetarReachScale()
    shockwave(coreX, coreY, 0.3f, reach, if (apexActive()) FxColorCode.APEX_GOLD else 2)
}

/** Magnetar widens both polarity fields on ordinary enemies. */
private fun MutableGameState.magnetarReachScale(): Float = if (apexActive()) apexValue(MAGNETAR_REACH_SCALE) else 1f

/**
 * Prism facets: an incoming hit that facets may cancel spends one and is negated completely. Every hit
 * pauses the regeneration; losing the last facet pushes nearby enemies away. Returns true when negated.
 */
internal fun MutableGameState.spendFacet(amount: Float): Boolean {
    if (coreShape != CoreShape.PRISM) return false
    val ability = characterRuntime
    if (ability.facets <= 0) return false
    ability.facets--
    ability.damagePrevented += amount.toDouble()
    hurtCooldown = 0.14f
    shockwave(coreX, coreY, 0.22f, coreRadius * 3f, 2)
    if (apexActive()) fireRefraction()
    if (ability.facets == 0) {
        pushEnemies(coreX, coreY, FACET_BREAK_RADIUS, FACET_BREAK_PUSH, inward = false)
        shockwave(coreX, coreY, 0.35f, FACET_BREAK_RADIUS, 2)
    }
    return true
}

/**
 * Monolith: a negated hit fires a refraction back, at the boss while strikes remain (within reach, not shifting),
 * else at the nearest ordinary enemy; the activation's bolt budget counts the refractions.
 */
private fun MutableGameState.fireRefraction() {
    val runtime = skillRuntime
    val left = runtime.apex(ApexCell.BOLTS_LEFT)
    if (left < 1f) return
    val target = apexShotTarget(coreX, coreY, APEX_SHOT_RANGE) ?: return
    runtime.setApex(ApexCell.BOLTS_LEFT, left - 1f)
    fireApexShot(coreX, coreY, target, apexValue(MONOLITH_REFRACTION_DAMAGE) * abilityPower())
}

/** True while Monolith runs: its facets take obstacle collisions as well as hits. */
internal fun MutableGameState.monolithTakesCollisions(): Boolean = coreShape == CoreShape.PRISM && apexActive()

/** Seconds per regrown facet: Monolith regrows them fast. */
internal fun MutableGameState.facetRegrowthSeconds(): Float =
    if (apexActive()) apexValue(MONOLITH_REGROWTH_SECONDS) else facetRegenSeconds()

/** Any incoming hit pauses facet regeneration. */
internal fun MutableGameState.pauseFacetRegeneration() {
    if (coreShape == CoreShape.PRISM) characterRuntime.facetPauseUntil = elapsed + FACET_PAUSE_SECONDS
}

private fun MutableGameState.cutFacetTimer() {
    val ability = characterRuntime
    if (ability.facets >= CharacterRuntime.MAX_FACETS || elapsed < ability.facetCutReadyAt) return
    ability.facetCutReadyAt = elapsed + FACET_CUT_INTERVAL
    ability.facetTimer += FACET_CUT_SECONDS
    val regen = facetRegenSeconds()
    if (ability.facetTimer >= regen) {
        ability.facetTimer -= regen
        ability.facets++
    }
}

/**
 * Diamond parry: an open window negates a hit approaching from ([x], [y]) with velocity ([vx], [vy]),
 * returns a hostile projectile as a shot and grants Edge. [damage] is what the hit would have dealt.
 */
internal fun MutableGameState.tryCharacterParry(
    x: Float, y: Float, vx: Float, vy: Float,
    threatenedCoreX: Float = coreX, threatenedCoreY: Float = coreY,
    damage: Float = 0f,
    projectile: Boolean = false,
): Boolean {
    val ability = characterRuntime
    if (coreShape != CoreShape.DIAMOND) return false
    // Mirror Stance: while braking, hits are parried (no window, speed or cooldown) until its parries run out.
    val mirror = apexActive() && braking && skillRuntime.apex(ApexCell.BOLTS_LEFT) >= 1f
    if (ability.parryWindow <= 0f && !mirror) return false
    // Collision callers pass the beginning of the swept segment: an incoming projectile
    // may already be beyond the core at the end of the same fixed step.
    if (vx * (threatenedCoreX - x) + vy * (threatenedCoreY - y) <= 0f) return false
    if (!mirror) ability.parryWindow = 0f
    ability.parryFlagSteps = 2
    ability.damagePrevented += damage.toDouble()
    ability.edge = min(CharacterRuntime.MAX_EDGE, ability.edge + 1)
    ability.edgeUntil = elapsed + EDGE_SECONDS
    if (mirror) {
        skillRuntime.setApex(ApexCell.BOLTS_LEFT, skillRuntime.apex(ApexCell.BOLTS_LEFT) - 1f)
        apexShotTarget(coreX, coreY, APEX_SHOT_RANGE)?.let { enemy ->
            val edge = 1f + EDGE_DAMAGE_PER_STACK * apexValue(MIRROR_LOCKED_EDGE)
            fireApexShot(coreX, coreY, enemy, apexValue(MIRROR_SHOT_DAMAGE) * abilityPower() * edge)
        }
    } else if (projectile) {
        nearestOtherEnemy(coreX, coreY, -1, PARRY_RETURN_RANGE)?.let { enemy ->
            fireRelicProjectile(coreX, coreY, enemy, PARRY_SHOT_DAMAGE * abilityPower() * characterDamageMultiplier())
        }
    }
    shockwave(coreX, coreY, 0.25f, 110f, 2)
    // A parry recharges every skill.
    refundAllSkills(DIAMOND_PARRY_SKILL_REFUND)
    return true
}

/** Dash heat and impulse multipliers of the current Dash; Pentagon rewards an on-beat Dash. */
internal fun MutableGameState.onCharacterDashDeparture(): Boolean {
    if (coreShape != CoreShape.PENTAGON) {
        characterRuntime.dashOnBeat = false
        return false
    }
    // Crescendo: every Dash leaves on the beat; only a real beat raises the streak.
    val realBeat = isOnBeat()
    val onBeat = realBeat || apexActive()
    characterRuntime.dashOnBeat = onBeat
    if (realBeat) onBeatAction()
    return onBeat
}

/** The character's multiplier on the Dash impulse: an on-beat Pentagon Dash pushes [BEAT_DASH_IMPULSE] harder. */
internal fun MutableGameState.characterDashImpulseMultiplier(onBeat: Boolean): Float =
    if (onBeat && coreShape == CoreShape.PENTAGON) BEAT_DASH_IMPULSE else 1f

/** The largest [characterDashImpulseMultiplier] this character's Dash can reach. */
internal fun MutableGameState.maxCharacterDashImpulseMultiplier(): Float = characterDashImpulseMultiplier(onBeat = true)

/** Crescent Dashes need a Blink charge instead of heat. */
internal fun MutableGameState.characterDashReady(): Boolean? =
    if (coreShape == CoreShape.CRESCENT) characterRuntime.blinkCharges > 0 else null

// ---------------------------------------------------------------------------------------------
// Character runtime API: the read side other systems (signature weapon twists) may consult.

/** Intact Prism facets (0 for other characters). */
internal val MutableGameState.intactFacets: Int
    get() = if (coreShape == CoreShape.PRISM) characterRuntime.facets else 0

/** Current Halo radius (0 for other characters). */
internal val MutableGameState.haloRadius: Float
    get() = if (coreShape == CoreShape.RING) characterRuntime.haloRadius else 0f

/** True while a Shard mark is open on [enemy]. */
internal fun MutableGameState.isMarked(enemy: Enemy): Boolean =
    coreShape == CoreShape.SHARD && enemy.characterMarkTime > 0f

/** Opens (removes) a Shard mark; true when one was open. */
internal fun MutableGameState.consumeMark(enemy: Enemy): Boolean {
    if (!isMarked(enemy)) return false
    enemy.characterMarkTime = 0f
    return true
}

/** Opens a Shard mark on [enemy] (a Scatter Bloom pellet's Puncture mark); nothing for other characters. */
internal fun MutableGameState.markForPuncture(enemy: Enemy) {
    if (coreShape == CoreShape.SHARD && enemy.alive) enemy.characterMarkTime = MARK_SECONDS
}

/**
 * Spends an open Shard mark on [enemy] for a Puncture of [multiplier] times its damage when Puncture is
 * ready (the Null Lance signature's double Puncture); true when it struck.
 */
internal fun MutableGameState.tryPuncture(enemy: Enemy, multiplier: Float): Boolean {
    if (!punctureReady(enemy) || !consumeMark(enemy)) return false
    puncture(enemy, multiplier)
    return true
}

/**
 * Puncture is ready for [enemy]. While Vivisect runs a boss-class enemy still waits the base interval after the
 * last puncture of any enemy, so the faster cadence never reaches it.
 */
private fun MutableGameState.punctureReady(enemy: Enemy): Boolean {
    if (elapsed < characterRuntime.punctureReadyAt) return false
    return !enemy.isBossClass || !apexActive() || elapsed >= skillRuntime.apex(ApexCell.BOSS_BASE_READY_AT)
}

/**
 * A Puncture of [multiplier] times its damage on [enemy] (its mark already spent). While Vivisect runs, punctures
 * come at the Apex interval on ordinary enemies and spread the mark to the next one; on a boss-class enemy the
 * first punctures of the activation are Apex strikes (full hit cap), at the base interval.
 */
private fun MutableGameState.puncture(enemy: Enemy, multiplier: Float) {
    val ability = characterRuntime
    val vivisect = apexActive()
    val damage = PUNCTURE_DAMAGE * multiplier * abilityPower()
    ability.punctureReadyAt = elapsed + if (vivisect && !enemy.isBossClass) apexValue(VIVISECT_INTERVAL) else PUNCTURE_INTERVAL
    if (vivisect) skillRuntime.setApex(ApexCell.BOSS_BASE_READY_AT, elapsed + PUNCTURE_INTERVAL)
    if (!(vivisect && enemy.isBossClass && apexStrike(enemy) > 0f)) {
        dealCharacterDamage(enemy, damage)
    }
    heat = max(0f, heat - punctureHeatRefund())
    shockwave(enemy.x, enemy.y, 0.3f, 65f, 2)
    if (vivisect) spreadPunctureMark(enemy)
}

/** Vivisect: a puncture opens a mark on the nearest unmarked ordinary enemy within reach of [from]. */
private fun MutableGameState.spreadPunctureMark(from: Enemy) {
    val reach = apexValue(VIVISECT_SPREAD_RADIUS)
    var nearest: Enemy? = null
    var nearestDistance = reach * reach
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy === from || !enemy.alive || enemy.isBossClass || enemy.characterMarkTime > 0f || isShifting(enemy)) continue
        val distance = distanceSquared(from.x, from.y, enemy.x, enemy.y)
        if (distance <= nearestDistance) {
            nearest = enemy
            nearestDistance = distance
        }
    }
    val target = nearest ?: return
    target.characterMarkTime = MARK_SECONDS
    addRelicArc(from.x, from.y, target.x, target.y, LinkStyleCode.apex(CoreShape.SHARD))
}

/** True from a parry until the end of the next fixed step, so weapon updates of that step can react. */
internal val MutableGameState.parriedThisStep: Boolean get() = characterRuntime.parryFlagSteps > 0

/** True for the rest of the fixed step in which the Core blinked (the Crescent's Blink or Blink Drive's). */
internal val MutableGameState.blinkThisStep: Boolean get() = characterRuntime.blinkFlagSteps > 0
internal val MutableGameState.blinkFromX: Float get() = characterRuntime.blinkFromX
internal val MutableGameState.blinkFromY: Float get() = characterRuntime.blinkFromY
internal val MutableGameState.blinkToX: Float get() = characterRuntime.blinkToX
internal val MutableGameState.blinkToY: Float get() = characterRuntime.blinkToY

/** Live Hive drones and their positions. */
internal val MutableGameState.droneCount: Int get() = if (coreShape == CoreShape.HEXAGON) characterRuntime.droneCount else 0
internal fun MutableGameState.droneX(index: Int): Float = characterRuntime.droneValue(index, DRONE_X)
internal fun MutableGameState.droneY(index: Int): Float = characterRuntime.droneValue(index, DRONE_Y)

/** Star stacks (0 for other characters). */
internal val MutableGameState.stellarMass: Int get() = if (coreShape == CoreShape.STAR) characterRuntime.stellarMass else 0

/** True once the Spiral's echo ghost has a full second of path (and the step before it) to replay. */
internal val MutableGameState.ghostActive: Boolean
    get() = coreShape == CoreShape.SPIRAL && characterRuntime.ghostSamples >= CharacterRuntime.GHOST_SAMPLES
internal val MutableGameState.ghostX: Float get() = characterRuntime.ghostSampleX(CharacterRuntime.GHOST_DELAY_STEPS)
internal val MutableGameState.ghostY: Float get() = characterRuntime.ghostSampleY(CharacterRuntime.GHOST_DELAY_STEPS)

/**
 * The ghost's position one step earlier. A replayed teleport follows the teleport rule: the previous
 * position is the arrival point, so the ghost jumps the gap without travelling (or striking) along it.
 */
internal val MutableGameState.previousGhostX: Float get() = characterRuntime.ghostSampleX(previousGhostStepsAgo())
internal val MutableGameState.previousGhostY: Float get() = characterRuntime.ghostSampleY(previousGhostStepsAgo())

private fun MutableGameState.previousGhostStepsAgo(): Int {
    val delay = CharacterRuntime.GHOST_DELAY_STEPS
    return if (characterRuntime.ghostSampleTeleported(delay)) delay else delay + 1
}

/** Ghost speed over its last fixed step (0 until it is active, and on a replayed teleport). */
internal val MutableGameState.ghostSpeed: Float
    get() = if (!ghostActive) 0f else length(ghostX - previousGhostX, ghostY - previousGhostY) / MutableGameState.FIXED_STEP

/** Live Tesseract lattice nodes. */
internal val MutableGameState.latticeNodeCount: Int get() = if (coreShape == CoreShape.TESSERACT) characterRuntime.latticeCount else 0
internal fun MutableGameState.latticeNodeX(index: Int): Float = characterRuntime.latticeX(index)
internal fun MutableGameState.latticeNodeY(index: Int): Float = characterRuntime.latticeY(index)

/** Dipole polarity. */
internal val MutableGameState.polarity: Polarity get() = characterRuntime.polarity

/** True while a REPEL throw carries [enemy]: it flies on its momentum instead of steering (never a boss). */
internal fun MutableGameState.isThrownByPolarity(enemy: Enemy): Boolean =
    coreShape == CoreShape.DIPOLE && enemy.characterMarkTime > 0f && !enemy.isBossClass

/** True when Orbit is fully charged and the next impact or weapon hit releases it. */
internal val MutableGameState.orbitCharged: Boolean get() = coreShape == CoreShape.ORB && characterRuntime.charge >= 1f

/** Seconds from the current time to the nearest Pentagon beat. */
internal fun MutableGameState.beatOffset(): Float {
    val phase = elapsed % BEAT_PERIOD
    return min(phase, BEAT_PERIOD - phase)
}

internal fun MutableGameState.currentBeatIndex(): Int = (elapsed / BEAT_PERIOD).roundToInt()

/** True within the on-beat window of a Pentagon beat (never for other characters, never before the first beat). */
internal fun MutableGameState.isOnBeat(): Boolean =
    coreShape == CoreShape.PENTAGON && elapsed >= BEAT_PERIOD - BEAT_WINDOW && beatOffset() <= BEAT_WINDOW

/** Weapon activation multiplier from the character: on-beat activations of the Pentagon. `cooldown()` applies it. */
internal fun MutableGameState.characterActivationMultiplier(): Float = if (isOnBeat()) BEAT_ACTIVATION_MULTIPLIER else 1f

// ---------------------------------------------------------------------------------------------
// Mechanics.

private fun MutableGameState.updateOrbit(ability: CharacterRuntime, turn: Float, moved: Float, currentSpeed: Float) {
    // Perihelion: Orbit charges on half the arc, over a shorter distance and at a lower speed.
    val perihelion = apexActive()
    val minimumSpeed = if (perihelion) PERIHELION_MIN_SPEED else ORBIT_MIN_SPEED
    if (!braking && currentSpeed >= minimumSpeed && ability.previousSpeed >= minimumSpeed && moved > 0f) {
        val sameDirection = ability.turnArc == 0f || turn * ability.turnArc >= 0f
        val arc = if (sameDirection) ability.turnArc + turn else turn
        val distance = if (sameDirection) ability.turnDistance + moved else moved
        val requiredArc = if (perihelion) apexValue(PERIHELION_ARC) else orbitArc()
        val requiredDistance = if (perihelion) PERIHELION_MIN_DISTANCE else ORBIT_MIN_DISTANCE
        val earned = if (abs(arc) >= requiredArc && distance >= requiredDistance) abs(turn) / PI.toFloat() else 0f
        ability.turnArc = arc.coerceIn(-TAU, TAU)
        ability.turnDistance = min(2_000f, distance)
        ability.charge = min(1f, ability.charge + earned)
    } else {
        ability.turnArc = 0f
        ability.turnDistance = 0f
    }
    if (perihelion) {
        updateBaseOrbitCharge(ability, turn, moved, currentSpeed)
        // Against boss-class enemies Perihelion keeps the base cadence: once the base rules would have charged Orbit,
        // it is charged, so the next hit releases it where a base release would land.
        if (skillRuntime.apex(ApexCell.AUX) >= 1f) ability.charge = 1f
    }
}

/**
 * During Perihelion, the charge the base rules would have earned ([ApexCell.AUX]; only a release it completes reaches
 * boss-class enemies), on the base arc, distance and speed and on its own arc ([ApexCell.BASE_ARC],
 * [ApexCell.BASE_DISTANCE]): a release that skips bosses leaves it running, as no base release happened.
 */
private fun MutableGameState.updateBaseOrbitCharge(ability: CharacterRuntime, turn: Float, moved: Float, currentSpeed: Float) {
    val runtime = skillRuntime
    if (!braking && currentSpeed >= ORBIT_MIN_SPEED && ability.previousSpeed >= ORBIT_MIN_SPEED && moved > 0f) {
        val previousArc = runtime.apex(ApexCell.BASE_ARC)
        val sameDirection = previousArc == 0f || turn * previousArc >= 0f
        val arc = if (sameDirection) previousArc + turn else turn
        val distance = if (sameDirection) runtime.apex(ApexCell.BASE_DISTANCE) + moved else moved
        val earned = if (abs(arc) >= orbitArc() && distance >= ORBIT_MIN_DISTANCE) abs(turn) / PI.toFloat() else 0f
        runtime.setApex(ApexCell.AUX, min(1f, runtime.apex(ApexCell.AUX) + earned))
        runtime.setApex(ApexCell.BASE_ARC, arc.coerceIn(-TAU, TAU))
        runtime.setApex(ApexCell.BASE_DISTANCE, min(2_000f, distance))
    } else {
        runtime.setApex(ApexCell.BASE_ARC, 0f)
        runtime.setApex(ApexCell.BASE_DISTANCE, 0f)
    }
}

private fun MutableGameState.updateFacets(ability: CharacterRuntime, delta: Float) {
    // Monolith: facets regrow fast and a hit no longer pauses them.
    val monolith = apexActive()
    val regrowth = facetRegrowthSeconds()
    if (ability.facets < CharacterRuntime.MAX_FACETS) {
        if (monolith || elapsed >= ability.facetPauseUntil) ability.facetTimer += delta
        if (ability.facetTimer >= regrowth) {
            ability.facetTimer -= regrowth
            ability.facets++
        }
    }
    if (ability.facets >= CharacterRuntime.MAX_FACETS) ability.facetTimer = 0f
    ability.charge = ability.facets / CharacterRuntime.MAX_FACETS.toFloat()
}

private fun MutableGameState.updateHalo(ability: CharacterRuntime, delta: Float, currentSpeed: Float, moved: Float) {
    val corona = apexActive()
    val extraRadius = haloExtraRadius()
    val target = CharacterRuntime.HALO_MIN_RADIUS + clamp(currentSpeed / HALO_SPEED_SPAN, 0f, 1f) * extraRadius
    if (ability.previousSpeed < HALO_BURST_SPEED && currentSpeed >= HALO_BURST_SPEED && elapsed >= ability.haloBurstReadyAt) {
        // The ring snaps out to the burst radius and strikes every enemy it crosses on the way.
        val inner = ability.haloRadius
        val damage = HALO_BURST_DAMAGE * abilityPower()
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive) continue
            val distance = length(enemy.x - coreX, enemy.y - coreY)
            if (distance + enemy.radius >= inner && distance - enemy.radius <= HALO_BURST_RADIUS) dealCharacterDamage(enemy, damage)
        }
        ability.haloRadius = HALO_BURST_RADIUS
        ability.haloBurstReadyAt = elapsed + HALO_BURST_COOLDOWN
        ability.haloBurstFlash = 1f
        shockwave(coreX, coreY, 0.35f, HALO_BURST_RADIUS, 2)
    } else if (corona) {
        // Corona holds the widest ring, never narrower than the one Refit already widened.
        ability.haloRadius = max(apexValue(CORONA_HALO_RADIUS), CharacterRuntime.HALO_MIN_RADIUS + extraRadius)
    } else {
        ability.haloRadius += (target - ability.haloRadius) * min(1f, delta * HALO_EASE)
    }
    val radius = ability.haloRadius
    ability.charge = ((radius - CharacterRuntime.HALO_MIN_RADIUS) / extraRadius).coerceIn(0f, 1f)
    if (moved <= 0.01f || currentSpeed < HALO_MIN_SPEED) return
    val power = delta * abilityPower()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val distance = length(enemy.x - coreX, enemy.y - coreY)
        if (distance >= max(36f, radius - HALO_BAND) && distance <= radius + HALO_BAND + enemy.radius) {
            // Corona's band burns ordinary enemies hotter; boss-class enemies keep the base burn.
            dealCharacterDamage(enemy, (if (corona && !enemy.isBossClass) apexValue(CORONA_BAND_DPS) else HALO_BAND_DPS) * power)
        }
    }
}

private fun MutableGameState.updateLattice(ability: CharacterRuntime) {
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
private fun MutableGameState.recordLatticeNode(x: Float, y: Float) {
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
private fun insideLattice(ability: CharacterRuntime, count: Int, x: Float, y: Float): Boolean {
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
private fun MutableGameState.supernova() {
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

private fun MutableGameState.updateEcho(ability: CharacterRuntime, delta: Float) {
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
private fun MutableGameState.updateSecondGhost() {
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

private fun MutableGameState.updatePolarity(ability: CharacterRuntime, delta: Float) {
    val attract = ability.polarity == Polarity.ATTRACT
    val radius = if (attract) attractRadius() else repelRadius()
    val acceleration = (if (attract) -ATTRACT_ACCELERATION else REPEL_ACCELERATION) * delta
    // Magnetar widens the field and throws harder, on ordinary enemies only.
    val magnetar = apexActive()
    val wideRadius = radius * magnetarReachScale()
    val wideAcceleration = if (attract || !magnetar) acceleration else acceleration * apexValue(MAGNETAR_THROW_SCALE)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val amplified = magnetar && !enemy.isBossClass
        val dx = enemy.x - coreX
        val dy = enemy.y - coreY
        val distance = length(dx, dy)
        if (distance < 1f || distance > (if (amplified) wideRadius else radius) + enemy.radius) continue
        val knockback = knockbackTaken(enemy)
        if (knockback <= 0f) continue
        val push = if (amplified) wideAcceleration else acceleration
        enemy.vx += dx / distance * push * knockback
        enemy.vy += dy / distance * push * knockback
        // ATTRACT only adds its pull to the enemy's own steering. REPEL throws what it pushes: the
        // enemy flies on its momentum (no steering) until THROW_SECONDS after the push carries it out
        // of the field, and what it strikes meanwhile is a slam. Bosses and mini-bosses are never thrown.
        if (!attract && !enemy.isBossClass) enemy.characterMarkTime = THROW_SECONDS
    }
    ability.charge = if (elapsed >= ability.polarityFlipReadyAt) 1f else
        1f - ((ability.polarityFlipReadyAt - elapsed) / POLARITY_FLIP_INTERVAL).coerceIn(0f, 1f)
    // Thrown enemies meeting obstacles slam in the obstacle pass (resolveEnemyObstacles), at their
    // normal speed and through the same applySlamDamage, so each contact is found once. Here they
    // slam into each other.
    // Bounded like the obstacle pass: at most MAX_POLARITY_THROWN_CHECKS_PER_STEP thrown enemies look for a
    // partner each step, and a pair is rejected on its squared distance before any root.
    var slams = 0
    var thrownVisited = 0
    for (index in enemies.indices) {
        if (slams >= MAX_SLAMS_PER_STEP || thrownVisited >= MAX_POLARITY_THROWN_CHECKS_PER_STEP) break
        val thrown = enemies[index]
        // A phased Phantom is intangible: nothing slams into it and it slams into nothing.
        if (!thrown.alive || thrown.characterMarkTime <= 0f || isPhasedPhantom(thrown)) continue
        thrownVisited++
        for (otherIndex in enemies.indices) {
            if (otherIndex == index) continue
            val other = enemies[otherIndex]
            if (!other.alive || isPhasedPhantom(other)) continue
            val dx = other.x - thrown.x
            val dy = other.y - thrown.y
            val reach = thrown.radius + other.radius
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared < 0.25f || distanceSquared > reach * reach) continue
            val distance = sqrt(distanceSquared)
            val closing = ((thrown.vx - other.vx) * dx + (thrown.vy - other.vy) * dy) / distance
            if (closing < SLAM_MIN_SPEED) continue
            thrown.characterMarkTime = 0f
            // The pair shares the closing momentum, so one contact slams once. An enemy struck hard
            // enough flies on as a throw of its own and may slam the next one (never a boss).
            val share = closing * 0.5f
            thrown.vx -= dx / distance * share
            thrown.vy -= dy / distance * share
            other.vx += dx / distance * share
            other.vy += dy / distance * share
            if (share >= THROW_MIN_SPEED && !other.isBossClass) other.characterMarkTime = THROW_SECONDS
            applySlamDamage(thrown, closing, polarity = true)
            applySlamDamage(other, closing, polarity = true)
            slams++
            break
        }
    }
}

/**
 * The Dipole's slam damage on [enemy] at [speed], dealt as ability damage; returns the amount the SLAM
 * trigger sees. [applySlamDamage] calls it for polarity slams, so a slam is one damage instance.
 */
internal fun MutableGameState.polaritySlam(enemy: Enemy, speed: Float): Float {
    val damage = (SLAM_BASE_DAMAGE + SLAM_DAMAGE_PER_SPEED * speed) * abilityPower()
    dealCharacterDamage(enemy, damage)
    return damage
}

/** A landed obstacle slam ends a REPEL throw, so the enemy steers again. */
internal fun MutableGameState.endPolarityThrow(enemy: Enemy) {
    if (isThrownByPolarity(enemy)) enemy.characterMarkTime = 0f
}

/** Records an on-beat action: the streak rises once per beat. */
internal fun MutableGameState.onBeatAction() {
    val ability = characterRuntime
    val beat = currentBeatIndex()
    if (ability.streakBeat == beat) return
    ability.streakBeat = beat
    ability.beatStreak = min(CharacterRuntime.MAX_BEAT_STREAK, ability.beatStreak + 1)
}

/** Advances the Crescent's Blink recharge by [seconds] (a charge completes as in the step). */
private fun MutableGameState.advanceBlinkRecharge(seconds: Float) {
    val ability = characterRuntime
    if (ability.blinkCharges >= CharacterRuntime.MAX_BLINK_CHARGES) return
    ability.blinkRecharge += seconds
    val period = blinkRechargeSeconds()
    if (ability.blinkRecharge >= period) {
        ability.blinkRecharge -= period
        ability.blinkCharges++
    }
    if (ability.blinkCharges >= CharacterRuntime.MAX_BLINK_CHARGES) ability.blinkRecharge = 0f
}

/** Every idle Hive drone darts at [target] now (each dart as in the Hive's own cadence). */
private fun MutableGameState.dronesDartAt(target: Enemy) {
    val ability = characterRuntime
    val drones = ability.drones
    for (drone in 0 until ability.droneCount) {
        if (!target.alive) return
        val base = drone * DRONE_STRIDE
        if (drones[base + DRONE_DART] > 0f) continue
        drones[base + DRONE_READY_AT] = elapsed + DRONE_INTERVAL
        drones[base + DRONE_TARGET_X] = target.x
        drones[base + DRONE_TARGET_Y] = target.y
        drones[base + DRONE_DART] = DRONE_DART_SECONDS
        val integrity = target.hp
        recordAbilityDamage(integrity, dealProcDamage(target, DRONE_DAMAGE * abilityPower()))
    }
}

/** Seconds per Blink charge: Total Eclipse recharges almost at once; otherwise the Refit-adjusted base. */
internal fun MutableGameState.blinkRechargeSeconds(): Float =
    if (apexActive()) apexValue(ECLIPSE_BLINK_RECHARGE) else baseBlinkRechargeSeconds()

private fun MutableGameState.updateEclipse(ability: CharacterRuntime, delta: Float) {
    val period = blinkRechargeSeconds()
    if (ability.blinkCharges < CharacterRuntime.MAX_BLINK_CHARGES) {
        ability.blinkRecharge += delta
        if (ability.blinkRecharge >= period) {
            ability.blinkRecharge -= period
            ability.blinkCharges++
        }
    }
    if (ability.blinkCharges >= CharacterRuntime.MAX_BLINK_CHARGES) ability.blinkRecharge = 0f
    ability.charge = (ability.blinkCharges + (ability.blinkRecharge / period).coerceAtMost(1f)) / CharacterRuntime.MAX_BLINK_CHARGES
    val eclipse = apexActive()
    var index = 0
    while (index < ability.scarCount) {
        val age = ability.scarValue(index, SCAR_AGE)
        if (age < 0f) {
            if (elapsed >= ability.scarValue(index, SCAR_DETONATE_AT)) detonateScar(index)
            else if (eclipse) pullTowardScar(index, delta)
            index++
        } else if (age + delta >= SCAR_FADE_SECONDS) {
            removeScar(index)
        } else {
            ability.scars[index * SCAR_STRIDE + SCAR_AGE] = age + delta
            index++
        }
    }
}

/**
 * Crescent's Dash: a Blink along the aim that stops short of the singularity and of obstacles, keeps
 * most of the speed, costs a charge instead of heat, counts enemies it crosses as Dash hits and leaves
 * a void scar. Blink Drive extends it and adds its strike and pairings.
 */
internal fun MutableGameState.crescentBlink(
    directionX: Float,
    directionY: Float,
    /** A reach that replaces the Blink's own (a Void Anchor's); NaN keeps it. */
    rangeOverride: Float = Float.NaN,
    /** Scales the Blink's reach (a Blast Jump). */
    rangeScale: Float = 1f,
) {
    val ability = characterRuntime
    ability.blinkCharges = max(0, ability.blinkCharges - 1)
    dashPhaseTime = DASH_PHASE_SECONDS
    val drive = build.upside(RelicId.BLINK_DRIVE)
    var range = BLINK_RANGE
    var strikeMass = 1f
    if (drive) {
        range *= BLINK_DRIVE_RANGE_MULTIPLIER
        if (build.pairing(RelicPairingId.HEAVY_BLINK)) {
            range *= 0.65f
            strikeMass = 2f
        }
    }
    if (!rangeOverride.isNaN()) range = rangeOverride
    range *= rangeScale
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    val ahead = (targetX - coreX) * directionX + (targetY - coreY) * directionY
    if (ahead > 0f) range = min(range, max(0f, length(targetX - coreX, targetY - coreY) - BLINK_SINGULARITY_GAP))
    range = blinkRangeClearOfObstacles(directionX, directionY, range)
    val startX = coreX
    val startY = coreY
    val endX = coreX + directionX * range
    val endY = coreY + directionY * range
    val impact = impactPower(max(speed, 600f))
    val strike = if (drive) impact * strikeMass * relicValue(RelicId.BLINK_DRIVE, 0) / 100f else 0f
    val assassin = drive && build.pairing(RelicPairingId.ASSASSINS_LINE)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy)) continue
        if (!segmentCircleIntersects(startX, startY, endX, endY, enemy.x, enemy.y, enemy.radius + coreRadius)) continue
        val newContact = enemy.lastCharacterDash != ability.dashSequence
        onCharacterDashContact(enemy)
        if (newContact) onDashHit(enemy, impact)
        if (drive && enemy.alive) {
            if (assassin) enemy.status[StatusCell.MARKED_UNTIL] = elapsed + 1f
            dealHit(enemy, strike, HitSource.DASH, canCrit = true)
            if (assassin) {
                enemy.status[StatusCell.MARKED_UNTIL] = 0f
                if (enemy.alive) enemy.status[StatusCell.BRITTLE] = 1f
            }
        }
    }
    if (drive) addRelicArc(startX, startY, endX, endY)
    if (apexActive()) cutShotsAlong(startX, startY, endX, endY)
    coreX = endX
    coreY = endY
    previousCoreX = endX
    previousCoreY = endY
    previousSingularityX = targetX
    previousSingularityY = targetY
    velocityX *= BLINK_SPEED_RETENTION
    velocityY *= BLINK_SPEED_RETENTION
    markBlink(startX, startY, endX, endY)
    if (ability.scarCount >= CharacterRuntime.MAX_SCARS) {
        // The oldest scar detonates early rather than being dropped.
        if (ability.scarValue(0, SCAR_AGE) < 0f) detonateScar(0)
        removeScar(0)
    }
    val base = ability.scarCount * SCAR_STRIDE
    ability.scars[base + SCAR_X0] = startX
    ability.scars[base + SCAR_Y0] = startY
    ability.scars[base + SCAR_X1] = endX
    ability.scars[base + SCAR_Y1] = endY
    ability.scars[base + SCAR_DETONATE_AT] = elapsed + SCAR_FUSE_SECONDS
    ability.scars[base + SCAR_AGE] = -1f
    ability.scarCount++
    shockwave(endX, endY, 0.25f, 110f, 0)
}

/**
 * [range] of a Blink from the Core along (directionX, directionY), shortened in [BLINK_OBSTACLE_STEP] steps
 * until the path clears every obstacle: a Blink stops short of a rock, never lands in or past it.
 */
internal fun MutableGameState.blinkRangeClearOfObstacles(directionX: Float, directionY: Float, range: Float): Float {
    var clear = range
    while (clear > 0f && obstacleBlocksSegment(coreX, coreY, coreX + directionX * clear, coreY + directionY * clear, coreRadius)) {
        clear = max(0f, clear - BLINK_OBSTACLE_STEP)
    }
    return clear
}

/** Records a Blink from (startX, startY) to (endX, endY) for the rest of the step (see [blinkThisStep]). */
internal fun MutableGameState.markBlink(startX: Float, startY: Float, endX: Float, endY: Float) {
    val ability = characterRuntime
    ability.blinkFlagSteps = 1
    ability.blinkFromX = startX
    ability.blinkFromY = startY
    ability.blinkToX = endX
    ability.blinkToY = endY
}

/** Total Eclipse: a Blink from (x0, y0) to (x1, y1) erases the hostile bolts along its path (bolt budget). */
private fun MutableGameState.cutShotsAlong(x0: Float, y0: Float, x1: Float, y1: Float) {
    val reach = apexValue(ECLIPSE_CUT_RADIUS)
    for (index in projectiles.indices) {
        if (skillRuntime.apex(ApexCell.BOLTS_LEFT) < 1f) return
        val shot = projectiles[index]
        if (shot.hostile && segmentCircleIntersects(x0, y0, x1, y1, shot.x, shot.y, reach + shot.radius)) eraseApexShot(shot, shot.x, shot.y)
    }
    addWeaponArc(x0, y0, x1, y1, 0.3f, LinkStyleCode.apex(CoreShape.CRESCENT))
}

/** Total Eclipse: an armed scar draws the ordinary enemies near it toward its line before it detonates. */
private fun MutableGameState.pullTowardScar(index: Int, delta: Float) {
    val ability = characterRuntime
    val x0 = ability.scarValue(index, SCAR_X0)
    val y0 = ability.scarValue(index, SCAR_Y0)
    val x1 = ability.scarValue(index, SCAR_X1)
    val y1 = ability.scarValue(index, SCAR_Y1)
    val segmentX = x1 - x0
    val segmentY = y1 - y0
    val lengthSquared = segmentX * segmentX + segmentY * segmentY
    val step = apexValue(ECLIPSE_PULL_SPEED) * delta
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || enemy.isBossClass || isShifting(enemy)) continue
        val along = if (lengthSquared > 0f) (((enemy.x - x0) * segmentX + (enemy.y - y0) * segmentY) / lengthSquared).coerceIn(0f, 1f) else 0f
        val dx = x0 + segmentX * along - enemy.x
        val dy = y0 + segmentY * along - enemy.y
        val distance = length(dx, dy)
        if (distance <= 1f || distance > ECLIPSE_PULL_REACH + enemy.radius) continue
        val move = min(distance, step * knockbackTaken(enemy))
        enemy.x += dx / distance * move
        enemy.y += dy / distance * move
    }
}

/**
 * A scar detonates on what touches its line, with one Void stack. During Total Eclipse it hits ordinary enemies
 * harder; boss-class enemies take the first detonations of the activation as Apex strikes, and later ones only
 * at the base Blink cadence.
 */
private fun MutableGameState.detonateScar(index: Int) {
    val ability = characterRuntime
    val x0 = ability.scarValue(index, SCAR_X0)
    val y0 = ability.scarValue(index, SCAR_Y0)
    val x1 = ability.scarValue(index, SCAR_X1)
    val y1 = ability.scarValue(index, SCAR_Y1)
    ability.scars[index * SCAR_STRIDE + SCAR_AGE] = 0f
    val damage = SCAR_DAMAGE * abilityPower()
    val eclipse = apexActive()
    val scale = if (eclipse) apexValue(ECLIPSE_SCAR_SCALE) else 1f
    val runtime = skillRuntime
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        if (eclipse && enemy.isBossClass && segmentCircleIntersects(x0, y0, x1, y1, enemy.x, enemy.y, enemy.radius + APEX_BOSS_REACH) &&
            apexStrikeFrom((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, enemy) > 0f
        ) continue
        if (!segmentCircleIntersects(x0, y0, x1, y1, enemy.x, enemy.y, enemy.radius + SCAR_REACH)) continue
        if (eclipse && enemy.isBossClass) {
            if (elapsed < runtime.apex(ApexCell.BOSS_BASE_READY_AT)) continue
            runtime.setApex(ApexCell.BOSS_BASE_READY_AT, elapsed + baseBlinkRechargeSeconds())
            dealCharacterDamage(enemy, damage)
        } else {
            dealCharacterDamage(enemy, damage * scale)
        }
        if (enemy.alive) applyStatus(enemy, StatusElement.VOID, 1, 0)
    }
    addWeaponArc(x0, y0, x1, y1, 0.3f)
}

private fun MutableGameState.removeScar(index: Int) {
    val ability = characterRuntime
    val last = ability.scarCount - 1
    for (slot in index until last) {
        for (field in 0 until SCAR_STRIDE) {
            ability.scars[slot * SCAR_STRIDE + field] = ability.scars[(slot + 1) * SCAR_STRIDE + field]
        }
    }
    ability.scarCount = last
}

private fun MutableGameState.updateHive(ability: CharacterRuntime, delta: Float) {
    var index = 0
    while (index < ability.droneCount) {
        val base = index * DRONE_STRIDE
        val life = ability.drones[base + DRONE_LIFE] - delta
        if (life <= 0f) {
            val last = ability.droneCount - 1
            for (field in 0 until DRONE_STRIDE) ability.drones[base + field] = ability.drones[last * DRONE_STRIDE + field]
            ability.droneCount = last
            continue
        }
        ability.drones[base + DRONE_LIFE] = life
        index++
    }
    val count = ability.droneCount
    ability.charge = count / CharacterRuntime.MAX_DRONES.toFloat()
    for (drone in 0 until count) {
        val base = drone * DRONE_STRIDE
        val drones = ability.drones
        drones[base + DRONE_PREVIOUS_X] = drones[base + DRONE_X]
        drones[base + DRONE_PREVIOUS_Y] = drones[base + DRONE_Y]
        val angle = elapsed * DRONE_ORBIT_SPEED + drone * TAU / count
        val orbitX = coreX + cos(angle) * DRONE_ORBIT_RADIUS
        val orbitY = coreY + sin(angle) * DRONE_ORBIT_RADIUS
        var dart = drones[base + DRONE_DART]
        if (dart <= 0f && elapsed >= drones[base + DRONE_READY_AT]) {
            // Swarm Queen: while strikes remain the drones reach for a boss-class enemy in Apex reach first.
            val target = (if (apexActive()) apexBossInReach(coreX, coreY) else null) ?: nearestEnemy(orbitX, orbitY, DRONE_RANGE)
            if (target != null) {
                // Swarm Queen: ordinary enemies take faster, harder darts; a boss-class target keeps the base
                // interval, and the first darts of the activation at it are Apex strikes.
                val queen = apexAmplifies(target)
                drones[base + DRONE_READY_AT] = elapsed + if (queen) apexValue(SWARM_DART_INTERVAL) else DRONE_INTERVAL
                drones[base + DRONE_TARGET_X] = target.x
                drones[base + DRONE_TARGET_Y] = target.y
                dart = DRONE_DART_SECONDS
                val integrity = target.hp
                val strike = target.isBossClass && apexActive() && apexStrike(target) > 0f
                if (!strike) {
                    val damage = (if (queen) apexValue(SWARM_DART_DAMAGE) else DRONE_DAMAGE) * abilityPower()
                    recordAbilityDamage(integrity, dealProcDamage(target, damage))
                }
                if (!target.alive && elapsed >= ability.droneHealReadyAt) {
                    ability.droneHealReadyAt = elapsed + DRONE_HEAL_INTERVAL
                    heal(DRONE_KILL_HEAL, HealSource.ITEM)
                }
            }
        }
        if (dart > 0f) {
            dart = max(0f, dart - delta)
            val progress = 1f - dart / DRONE_DART_SECONDS
            val reach = 1f - abs(progress * 2f - 1f)
            drones[base + DRONE_X] = lerp(orbitX, drones[base + DRONE_TARGET_X], reach)
            drones[base + DRONE_Y] = lerp(orbitY, drones[base + DRONE_TARGET_Y], reach)
        } else {
            drones[base + DRONE_X] = orbitX
            drones[base + DRONE_Y] = orbitY
        }
        drones[base + DRONE_DART] = dart
    }
}

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
private fun MutableGameState.updateApexForm(delta: Float) {
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
private fun MutableGameState.hypercubeRunning(): Boolean = coreShape == CoreShape.TESSERACT && apexActive()

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

/** Shifts every stored character position after a world rebase. */
internal fun MutableGameState.rebaseCharacterRuntime(shiftX: Float, shiftY: Float) {
    val ability = characterRuntime
    ability.dashOriginX -= shiftX
    ability.dashOriginY -= shiftY
    ability.blinkFromX -= shiftX
    ability.blinkFromY -= shiftY
    ability.blinkToX -= shiftX
    ability.blinkToY -= shiftY
    for (index in 0 until ability.latticeCount) {
        ability.latticeNodes[index * 2] = ability.latticeNodes[index * 2] - shiftX
        ability.latticeNodes[index * 2 + 1] = ability.latticeNodes[index * 2 + 1] - shiftY
    }
    if (ability.ghostSamples > 0) {
        val path = ability.ghostPath
        for (index in 0 until CharacterRuntime.GHOST_SAMPLES) {
            val base = index * CharacterRuntime.GHOST_STRIDE
            path[base + CharacterRuntime.GHOST_X] = path[base + CharacterRuntime.GHOST_X] - shiftX
            path[base + CharacterRuntime.GHOST_Y] = path[base + CharacterRuntime.GHOST_Y] - shiftY
        }
    }
    val drones = ability.drones
    for (index in 0 until ability.droneCount) {
        val base = index * DRONE_STRIDE
        drones[base + DRONE_X] = drones[base + DRONE_X] - shiftX
        drones[base + DRONE_Y] = drones[base + DRONE_Y] - shiftY
        drones[base + DRONE_PREVIOUS_X] = drones[base + DRONE_PREVIOUS_X] - shiftX
        drones[base + DRONE_PREVIOUS_Y] = drones[base + DRONE_PREVIOUS_Y] - shiftY
        drones[base + DRONE_TARGET_X] = drones[base + DRONE_TARGET_X] - shiftX
        drones[base + DRONE_TARGET_Y] = drones[base + DRONE_TARGET_Y] - shiftY
    }
    for (index in 0 until ability.scarCount) {
        val base = index * SCAR_STRIDE
        ability.scars[base + SCAR_X0] = ability.scars[base + SCAR_X0] - shiftX
        ability.scars[base + SCAR_Y0] = ability.scars[base + SCAR_Y0] - shiftY
        ability.scars[base + SCAR_X1] = ability.scars[base + SCAR_X1] - shiftX
        ability.scars[base + SCAR_Y1] = ability.scars[base + SCAR_Y1] - shiftY
    }
}

/** SplitMix64 finalizer: a stateless hash for the Hive's drone roll, so no gameplay stream is drawn. */
private fun characterHash(value: Long): Long {
    var z = value + -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}

internal const val DASH_PHASE_SECONDS: Float = 0.24f

/** Apex value indices (the per-shape order of `defaultApexDefinition`) and the forms' own numbers. */
private const val PERIHELION_ARC = 0
private const val PERIHELION_CAPTURE_RADIUS = 1
private const val PERIHELION_HELD_DAMAGE = 2
private const val PERIHELION_RELEASE_INTERVAL = 3
private const val PERIHELION_MIN_DISTANCE: Float = 50f
private const val PERIHELION_MIN_SPEED: Float = 150f
private const val PERIHELION_SHIFT_GRACE: Float = 0.5f
private const val PERIHELION_MAX_WAIT: Float = 9f
private const val PERIHELION_RELEASE_RANGE: Float = 600f
private const val MONOLITH_REGROWTH_SECONDS = 0
private const val MONOLITH_IMPACT_BONUS = 1
private const val MONOLITH_REFRACTION_DAMAGE = 2
private const val VIVISECT_INTERVAL = 0
private const val VIVISECT_SPREAD_RADIUS = 1
private const val VIVISECT_FREE_DASH_GAP = 2
private const val CORONA_HALO_RADIUS = 0
private const val CORONA_BAND_DPS = 1
private const val CORONA_BURST_INTERVAL = 2
private const val CORONA_BURST_DAMAGE = 3
private const val MIRROR_SHOT_DAMAGE = 0
private const val MIRROR_LOCKED_EDGE = 1
private const val HYPERCUBE_HALF_SIDE = 0
private const val HYPERCUBE_INSIDE_SPEED = 1
private const val HYPERCUBE_PULSE_DAMAGE = 2
private const val HYPERCUBE_PULSE_PER_NODE = 3
private const val HYPERCUBE_PULSE_SECONDS: Float = 0.6f
private const val HYPERCUBE_EDGE_REACH: Float = 24f
private const val HYPERNOVA_CAST_MASS = 0
private const val HYPERNOVA_COOLDOWN = 1
private const val HYPERNOVA_KILL_MASS = 2
private const val HYPERNOVA_END_MASS = 3
private const val TWIN_ECHO_DELAY = 0
private const val TWIN_ECHO_STRIKE_SCALE = 1
private const val TWIN_ECHO_ABSORB_INTERVAL = 2
private const val TWIN_ECHO_STRIKE_SPEED = 3
private const val MAGNETAR_FLIP_INTERVAL = 0
private const val MAGNETAR_REACH_SCALE = 1
private const val MAGNETAR_THROW_SCALE = 2
private const val MAGNETAR_SHOT_DAMAGE = 3
private const val CRESCENDO_BURST_DAMAGE = 1
private const val CRESCENDO_BURST_RADIUS = 2
private const val CRESCENDO_BOLTS_PER_BEAT = 3
private const val ECLIPSE_BLINK_RECHARGE = 0
private const val ECLIPSE_CUT_RADIUS = 1
private const val ECLIPSE_PULL_SPEED = 2
private const val ECLIPSE_SCAR_SCALE = 3
private const val ECLIPSE_PULL_REACH: Float = 100f
private const val SWARM_DART_INTERVAL = 0
private const val SWARM_DART_DAMAGE = 1
private const val SWARM_DATA_RANGE = 2
private const val SWARM_DRONE_LIFE = 3
private const val SWARM_INTERCEPT_SECONDS: Float = 0.6f
private const val SWARM_INTERCEPT_REACH: Float = 70f
/** Character × skill interactions. */
internal const val PENTAGON_SKILL_REFUND: Float = 0.2f
internal const val CRESCENT_SKILL_BLINK_SECONDS: Float = 0.6f
internal const val DIAMOND_PARRY_SKILL_REFUND: Float = 1f
internal const val STAR_SKILL_KILL_MASS: Int = 1
private const val FLASH_DECAY: Float = 2.5f
/** No Refit signature moves its constant below this share of the base ([refitSignature]). */
private const val REFIT_SIGNATURE_FLOOR_SHARE: Float = 0.5f

// Ability damage is calibrated by CharacterBalanceTest: averaged over its seeded 90 s fights (packs at
// random bearings, content bolt damage, exact and jittered timing) every offensive character's ability
// output stays within 40 % of their median (Orbit 42, Puncture 180, Halo 12/s and 16, Lattice 7 + 3.5
// per node, slams 120 + 0.25 per u/s from 100 u/s, Pulse burst 40, scars 45, drones 9). The 0.6.0 flight law
// (grip carves tighter, faster arcs) lifted Orbit and cost the Star and the Dipole their pack meetings: Orbit
// 70 -> 42, the early Supernova from 12 -> 6 Stellar Mass, Repel 420 -> 700 with throws of 0.6 -> 1.5 s.

private const val ORBIT_ARC: Float = 0.698f
private const val ORBIT_MIN_DISTANCE: Float = 100f
private const val ORBIT_MIN_SPEED: Float = 200f
internal const val ORBIT_RADIUS: Float = 210f
internal const val ORBIT_DAMAGE: Float = 42f
private const val ORBIT_STACKS: Int = 2

internal const val FACET_REGEN_SECONDS: Float = 6f
internal const val FACET_PAUSE_SECONDS: Float = 2f
private const val FACET_CUT_SECONDS: Float = 1f
private const val FACET_CUT_INTERVAL: Float = 1.2f
internal const val FACET_IMPACT_BONUS: Float = 1.2f
private const val FACET_BREAK_RADIUS: Float = 180f
private const val FACET_BREAK_PUSH: Float = 520f
private const val HEAVY_IMPACT_SHARE: Float = 0.25f
private const val HEAVY_IMPACT_SPEED: Float = 450f

private const val MARK_SECONDS: Float = 3f
internal const val PUNCTURE_DAMAGE: Float = 180f
internal const val PUNCTURE_HEAT_REFUND: Float = 12f
private const val PUNCTURE_INTERVAL: Float = 0.25f

private const val HALO_SPEED_SPAN: Float = 1_200f
internal const val HALO_EXTRA_RADIUS: Float = 110f
private const val HALO_BAND: Float = 18f
internal const val HALO_BAND_DPS: Float = 12f
private const val HALO_MIN_SPEED: Float = 40f
private const val HALO_EASE: Float = 6f
internal const val HALO_BURST_SPEED: Float = 1_200f
internal const val HALO_BURST_RADIUS: Float = 260f
internal const val HALO_BURST_DAMAGE: Float = 16f
private const val HALO_BURST_COOLDOWN: Float = 1.2f

private const val PARRY_MIN_SPEED: Float = 200f
internal const val PARRY_WINDOW_SECONDS: Float = 0.18f
internal const val PARRY_COOLDOWN_SECONDS: Float = 1.2f
private const val PARRY_RETURN_RANGE: Float = 800f
internal const val PARRY_SHOT_DAMAGE: Float = 65f
internal const val EDGE_DAMAGE_PER_STACK: Float = 0.08f
private const val EDGE_SECONDS: Float = 6f

private const val LATTICE_RECORD_DISTANCE: Float = 120f
private const val LATTICE_CLOSE_DISTANCE: Float = 130f
private const val LATTICE_SPACING: Float = 150f
private const val LATTICE_LIFE_SECONDS: Float = 7f
internal const val LATTICE_BASE_DAMAGE: Float = 7f
internal const val LATTICE_NODE_DAMAGE: Float = 3.5f
internal const val LATTICE_INSIDE_MULTIPLIER: Float = 1.5f

private const val STAR_IMPACT_PER_STACK: Float = 0.03f
private const val STAR_RADIUS_PER_STACK: Float = 0.015f
private const val STAR_MASS_PER_STACK: Float = 0.02f
private const val SUPERNOVA_EARLY_STACKS: Int = 6
private const val SUPERNOVA_RADIUS: Float = 160f
private const val SUPERNOVA_RADIUS_PER_STACK: Float = 8f
internal const val SUPERNOVA_DAMAGE: Float = 20f
internal const val SUPERNOVA_DAMAGE_PER_STACK: Float = 9f
private const val SUPERNOVA_STACKS_PER_ELEMENT: Int = 4
private const val SUPERNOVA_COOLDOWN: Float = 2f

private const val GHOST_MIN_SPEED: Float = 300f
internal const val GHOST_IMPACT_SHARE: Float = 0.32f
private const val GHOST_HIT_INTERVAL: Float = 0.4f
private const val GHOST_ABSORB_INTERVAL: Float = 1.5f

private const val POLARITY_FLIP_INTERVAL: Float = 2f
internal const val ATTRACT_RADIUS: Float = 300f
private const val ATTRACT_ACCELERATION: Float = 260f
internal const val REPEL_RADIUS: Float = 220f
private const val REPEL_ACCELERATION: Float = 700f
internal const val THROW_SECONDS: Float = 1.5f
private const val REPEL_THROW_SHARE: Float = 1f
internal const val SLAM_MIN_SPEED: Float = 100f
/** A struck enemy that gains this speed from a slam is thrown on (as fast as a slam needs). */
internal const val THROW_MIN_SPEED: Float = SLAM_MIN_SPEED
internal const val SLAM_BASE_DAMAGE: Float = 120f
internal const val SLAM_DAMAGE_PER_SPEED: Float = 0.25f
private const val MAX_SLAMS_PER_STEP: Int = 8

/** Thrown enemies that look for a slam partner in one step (the obstacle pass uses the same bound). */
private const val MAX_POLARITY_THROWN_CHECKS_PER_STEP: Int = 24

internal const val BEAT_PERIOD: Float = 0.75f
internal const val BEAT_WINDOW: Float = 0.11f
/** An on-beat Dash costs this share of the usual heat (40 % less) and pushes [BEAT_DASH_IMPULSE] harder. */
internal const val BEAT_DASH_HEAT: Float = 0.6f
internal const val BEAT_DASH_IMPULSE: Float = 1.2f
private const val BEAT_IMPACT_MULTIPLIER: Float = 1.6f
private const val BEAT_BURST_RADIUS: Float = 80f
internal const val BEAT_BURST_DAMAGE: Float = 40f
internal const val BEAT_ACTIVATION_MULTIPLIER: Float = 1.4f
private const val STREAK_IMPACT_PER_STACK: Float = 0.06f

internal const val BLINK_RANGE: Float = 240f
private const val BLINK_SINGULARITY_GAP: Float = 60f
private const val BLINK_OBSTACLE_STEP: Float = 20f
internal const val BLINK_SPEED_RETENTION: Float = 0.8f
internal const val BLINK_RECHARGE_SECONDS: Float = 2.2f
private const val BLINK_DRIVE_RANGE_MULTIPLIER: Float = 1.4f
internal const val SCAR_FUSE_SECONDS: Float = 0.4f
private const val SCAR_FADE_SECONDS: Float = 0.3f
internal const val SCAR_DAMAGE: Float = 45f
private const val SCAR_REACH: Float = 40f

private const val DRONE_CHANCE: Float = 0.25f
private const val DRONE_GUARANTEE: Int = 4
private const val DRONE_HASH_SALT: Long = 0x4E1F_C0DEL
private const val DRONE_LIFE_SECONDS: Float = 12f
private const val DRONE_FIRST_DART_SECONDS: Float = 0.3f
internal const val DRONE_ORBIT_RADIUS: Float = 70f
private const val DRONE_ORBIT_SPEED: Float = 2.4f
private const val DRONE_RANGE: Float = 260f
internal const val DRONE_DAMAGE: Float = 9f
private const val DRONE_INTERVAL: Float = 1f
private const val DRONE_DART_SECONDS: Float = 0.2f
private const val DRONE_KILL_HEAL: Float = 0.5f
private const val DRONE_HEAL_INTERVAL: Float = 1f
