// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
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
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.min

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
    val roll = ((splitMix64(pickupsCollected + DRONE_HASH_SALT) ushr 40) and 0xFFFFL).toInt()
    if (roll >= (DRONE_CHANCE * 65_536f).toInt() && ability.droneRolls < DRONE_GUARANTEE) return
    ability.droneRolls = 0
    addDrone(droneLife)
}

/** Builds a drone at the Core that lives [life] seconds (the caller keeps the hive below its bound). */
internal fun MutableGameState.addDrone(life: Float) {
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
internal fun MutableGameState.flipPolarity(restartGate: Boolean = true) {
    val ability = characterRuntime
    ability.polarity = if (ability.polarity == Polarity.ATTRACT) Polarity.REPEL else Polarity.ATTRACT
    if (restartGate) ability.polarityFlipReadyAt = elapsed + POLARITY_FLIP_INTERVAL
    ability.polarityFlash = 1f
    val reach = (if (ability.polarity == Polarity.ATTRACT) attractRadius() else repelRadius()) * magnetarReachScale()
    shockwave(coreX, coreY, 0.3f, reach, if (apexActive()) FxColorCode.APEX_GOLD else 2)
}

/** Magnetar widens both polarity fields on ordinary enemies. */
internal fun MutableGameState.magnetarReachScale(): Float = if (apexActive()) apexValue(MAGNETAR_REACH_SCALE) else 1f

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
            fireRelicProjectile(coreX, coreY, enemy, PARRY_SHOT_DAMAGE * abilityPower() * characterDamageMultiplier() * characterUpgradePower)
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
