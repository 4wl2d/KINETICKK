// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableListOfSize
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.collections.mapToImmutableList
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*


internal fun MutableGameState.toRenderModel(
    reusableCollections: GameplayRenderModel? = null,
    identitySource: MutableGameState? = null,
): GameplayRenderModel {
    require(identitySource == null || identitySource.content === content) {
        "Identity-based Gameplay projection reuse requires matching content"
    }
    require(
        reusableCollections == null ||
            identitySource == null ||
            reusableCollections.content === identitySource.content,
    ) {
        "Reusable Gameplay projection does not match its identity source"
    }
    return GameplayRenderModel(
        content = content,
        phase = phase,
        settings = settings,
        rebirthLevel = rebirthLevel,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        coreX = coreX,
        coreY = coreY,
        velocityX = velocityX,
        velocityY = velocityY,
        cameraX = cameraX,
        cameraY = cameraY,
        pointerX = pointerX,
        pointerY = pointerY,
        pointerActive = pointerActive,
        braking = braking,
        elapsed = elapsed,
        heat = heat,
        overheated = overheated && !build.upside(kinetickk.ball.content.api.RelicId.FURNACE_COVENANT),
        dashPhaseTime = dashPhaseTime,
        hp = hp,
        maxHp = maxHp,
        shield = shield,
        maxShield = shieldCapacity(),
        level = level,
        data = data,
        nextLevelData = nextLevelData,
        keys = keys,
        kills = kills,
        runStatistics = runStatisticsProjection(reusableCollections?.runStatistics),
        combo = combo,
        comboTime = comboTime,
        runMatter = runMatter,
        totalMatter = totalMatter,
        lastImpact = lastImpact,
        lastImpactTime = lastImpactTime,
        damageFlash = damageFlash,
        runGrace = runGrace,
        screenShake = screenShake,
        message = message,
        messageTime = messageTime,
        mass = mass,
        damageMultiplier = damageMultiplier,
        weaponPower = weaponPower,
        effectiveWeaponPower = effectiveWeaponPower(),
        rewardPreviews = if (phase == GamePhase.CHOICE && identitySource?.phase == GamePhase.CHOICE &&
            choices === identitySource.choices && reusableCollections != null &&
            buildStats() == identitySource.buildStats() &&
            equippedRelics === identitySource.equippedRelics && weapon == identitySource.weapon && elapsed == identitySource.elapsed &&
            itemStacks.sharesStorageWith(identitySource.itemStacks) && build === identitySource.build &&
            discoveredPairingMask == identitySource.discoveredPairingMask &&
            discoveredReactionMask == identitySource.discoveredReactionMask
        ) reusableCollections.rewardPreviews else rewardPreviews(),
        coolingRate = coolingRate,
        magnetStrength = magnetStrength,
        dashImpulse = dashImpulse,
        dashHeatCost = effectiveDashHeatCost(),
        regenPerSecond = regenPerSecond,
        critChance = critChance,
        critMultiplier = critMultiplier,
        pickupRadius = effectivePickupRadius(),
        luck = effectiveLuck(),
        dataGain = dataGain,
        matterGain = matterGain,
        attackSpeed = attackSpeed,
        damageReduction = damageReduction,
        comboWindow = effectiveComboWindow(),
        overdriveGain = overdriveGain,
        dragCoefficient = dragCoefficient,
        polarityStability = polarityStability,
        weapon = weapon,
        weaponLevel = weaponLevel,
        overdriveCharge = overdriveCharge,
        overdriveTime = overdriveTime,
        rerollsRemaining = rerollsRemaining,
        acquiredItemCount = acquiredItemCount,
        recentItem = recentItem,
        equippedRelics = equippedRelics.reuseIfIdentical(
            identitySource?.equippedRelics,
            reusableCollections?.equippedRelics,
        ) ?: equippedRelics.reuseIfContentEqual(reusableCollections?.equippedRelics)
            ?: equippedRelics.toImmutableList(),
        morningstarAngle = morningstarAngle,
        morningstarX = morningstarX,
        morningstarY = morningstarY,
        weaponBeamTime = weaponBeamTime,
        weaponBeamStartX = weaponBeamStartX,
        weaponBeamStartY = weaponBeamStartY,
        weaponBeamEndX = weaponBeamEndX,
        weaponBeamEndY = weaponBeamEndY,
        totem = totem?.let { value ->
            reusableCollections?.totem?.takeIf { previous ->
                value === identitySource?.totem ||
                    (value.x.sameBitsAs(previous.x) &&
                        value.y.sameBitsAs(previous.y) &&
                        value.pulse.sameBitsAs(previous.pulse))
            } ?: TotemProjection(value.x, value.y, value.pulse)
        },
        coreShape = coreShape,
        enemies = enemies.reuseIfIdentical(identitySource?.enemies, reusableCollections?.enemies)
            ?: enemies.reuseEmptyProjection(reusableCollections?.enemies)
            ?: enemies.reuseProjectionIfContentEqual(reusableCollections?.enemies) { value, previous ->
                value.id == previous.id &&
                    value.type == previous.type &&
                    value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.vx.sameBitsAs(previous.vx) &&
                    value.vy.sameBitsAs(previous.vy) &&
                    value.hp.sameBitsAs(previous.hp) &&
                    value.maxHp.sameBitsAs(previous.maxHp) &&
                    value.radius.sameBitsAs(previous.radius) &&
                    value.actionTimer.sameBitsAs(previous.actionTimer) &&
                    value.flash.sameBitsAs(previous.flash) &&
                    value.contactCooldown.sameBitsAs(previous.contactCooldown) &&
                    value.weaponCooldown.sameBitsAs(previous.weaponCooldown) &&
                    value.previousX.sameBitsAs(previous.previousX) &&
                    value.previousY.sameBitsAs(previous.previousY) &&
                    value.dead == previous.dead &&
                    statusBits(value) == previous.statusBits &&
                    affinityBits(value) == previous.affinityBits &&
                    value.mode == previous.behavior &&
                    behaviorRemaining(value).sameBitsAs(previous.behaviorRemaining) &&
                    value.facing.sameBitsAs(previous.facing) &&
                    linkedTo(value) == previous.linkedTo &&
                    shielded(value) == previous.shielded
            }
            ?: enemies.mapToImmutableList { value ->
                EnemyProjection(
                    id = value.id,
                    type = value.type,
                    x = value.x,
                    y = value.y,
                    vx = value.vx,
                    vy = value.vy,
                    hp = value.hp,
                    maxHp = value.maxHp,
                    radius = value.radius,
                    actionTimer = value.actionTimer,
                    flash = value.flash,
                    contactCooldown = value.contactCooldown,
                    weaponCooldown = value.weaponCooldown,
                    previousX = value.previousX,
                    previousY = value.previousY,
                    dead = value.dead,
                    statusBits = statusBits(value),
                    affinityBits = affinityBits(value),
                    behavior = value.mode,
                    behaviorRemaining = behaviorRemaining(value),
                    facing = value.facing,
                    linkedTo = linkedTo(value),
                    shielded = shielded(value),
                )
            },
        projectiles = projectiles.reuseEmptyProjection(reusableCollections?.projectiles)
            ?: projectiles.reuseIfIdentical(
                identitySource?.projectiles,
                reusableCollections?.projectiles,
            )
            ?: projectiles.reuseProjectionIfContentEqual(
                reusableCollections?.projectiles,
            ) { value, previous ->
                value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.vx.sameBitsAs(previous.vx) &&
                    value.vy.sameBitsAs(previous.vy) &&
                    value.radius.sameBitsAs(previous.radius) &&
                    value.life.sameBitsAs(previous.life) &&
                    value.hostile == previous.hostile &&
                    value.damage.sameBitsAs(previous.damage) &&
                    value.pierce == previous.pierce &&
                    value.colorIndex == previous.colorIndex &&
                    value.sourceWeapon == previous.sourceWeapon &&
                    value.previousX.sameBitsAs(previous.previousX) &&
                    value.previousY.sameBitsAs(previous.previousY) &&
                    value.kind == previous.kind
            }
            ?: projectiles.mapToImmutableList { value ->
                ProjectileProjection(
                    x = value.x,
                    y = value.y,
                    vx = value.vx,
                    vy = value.vy,
                    radius = value.radius,
                    life = value.life,
                    hostile = value.hostile,
                    damage = value.damage,
                    pierce = value.pierce,
                    colorIndex = value.colorIndex,
                    sourceWeapon = value.sourceWeapon,
                    previousX = value.previousX,
                    previousY = value.previousY,
                    kind = value.kind,
                )
            },
        pickups = pickups.reuseEmptyProjection(reusableCollections?.pickups)
            ?: pickups.reuseIfIdentical(identitySource?.pickups, reusableCollections?.pickups)
            ?: pickups.reuseProjectionIfContentEqual(reusableCollections?.pickups) { value, previous ->
                value.type == previous.type &&
                    value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.vx.sameBitsAs(previous.vx) &&
                    value.vy.sameBitsAs(previous.vy) &&
                    value.life.sameBitsAs(previous.life) &&
                    value.previousX.sameBitsAs(previous.previousX) &&
                    value.previousY.sameBitsAs(previous.previousY)
            }
            ?: pickups.mapToImmutableList { value ->
                PickupProjection(
                    value.type,
                    value.x,
                    value.y,
                    value.vx,
                    value.vy,
                    value.life,
                    value.previousX,
                    value.previousY,
                )
            },
        trail = trail.reuseEmptyProjection(reusableCollections?.trail)
            ?: trail.reuseIfIdentical(identitySource?.trail, reusableCollections?.trail)
            ?: trail.reuseProjectionIfContentEqual(reusableCollections?.trail) { value, previous ->
                value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.age.sameBitsAs(previous.age)
            }
            ?: trail.mapToImmutableList { value -> TrailPointProjection(value.x, value.y, value.age) },
        weaponNodes = weaponNodes.reuseEmptyProjection(reusableCollections?.weaponNodes)
            ?: weaponNodes.reuseIfIdentical(
                identitySource?.weaponNodes,
                reusableCollections?.weaponNodes,
            )
            ?: weaponNodes.reuseProjectionIfContentEqual(
                reusableCollections?.weaponNodes,
            ) { value, previous ->
                value.type == previous.type &&
                    value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.life.sameBitsAs(previous.life) &&
                    value.maxLife.sameBitsAs(previous.maxLife) &&
                    value.radius.sameBitsAs(previous.radius)
            }
            ?: weaponNodes.mapToImmutableList { value ->
                WeaponNodeProjection(value.type, value.x, value.y, value.life, value.maxLife, value.radius)
            },
        weaponOrbitals = weaponOrbitals.reuseEmptyProjection(reusableCollections?.weaponOrbitals)
            ?: weaponOrbitals.reuseIfIdentical(
                identitySource?.weaponOrbitals,
                reusableCollections?.weaponOrbitals,
            )
            ?: weaponOrbitals.reuseProjectionIfContentEqual(
                reusableCollections?.weaponOrbitals,
            ) { value, previous ->
                value.index == previous.index &&
                    value.x.sameBitsAs(previous.x) &&
                    value.y.sameBitsAs(previous.y) &&
                    value.radius.sameBitsAs(previous.radius) &&
                    value.previousX.sameBitsAs(previous.previousX) &&
                    value.previousY.sameBitsAs(previous.previousY)
            }
            ?: weaponOrbitals.mapToImmutableList { value ->
                WeaponOrbitalProjection(value.index, value.x, value.y, value.radius, value.previousX, value.previousY)
            },
        choices = choices.reuseIfIdentical(identitySource?.choices, reusableCollections?.choices)
            ?: choices.reuseIfContentEqual(reusableCollections?.choices)
            ?: choices.toImmutableList(),
        choiceType = activeChoiceType,
        pendingRelicChoiceCount = pendingRelicChoices,
        directedChoice = directedReward != null,
        characterAbility = characterAbilityProjection(
            reusable = reusableCollections?.characterAbility,
            identityRuntime = identitySource?.characterRuntime,
        ),
        pointsOfInterest = pointsOfInterestProjection(
            reusable = reusableCollections?.pointsOfInterest,
            identitySource = identitySource,
        ),
        itemStacks = itemStacks.reuseIfStorageShared(
            identitySource?.itemStacks,
            reusableCollections?.itemStacks,
        ) ?: itemStacks.reuseIfContentEqual(reusableCollections?.itemStacks)
            ?: itemStacks.toImmutableList(),
        discoveredRelicMask = discoveredRelicMask,
        discoveredItemIds = discoveredItemIds.reuseIfStorageShared(
            identitySource?.discoveredItemIds,
            reusableCollections?.discoveredItemIds,
        ) ?: discoveredItemIds.reuseIfContentEqual(reusableCollections?.discoveredItemIds)
            ?: discoveredItemIds.toImmutableSet(),
        relicRanks = relicRanks.reuseIfStorageShared(
            identitySource?.relicRanks,
            reusableCollections?.relicRanks,
        ) ?: relicRanks.reuseIfContentEqual(reusableCollections?.relicRanks)
            ?: relicRanks.toImmutableList(),
        build = reusableCollections?.build?.takeIf { previous ->
            identitySource != null && build === identitySource.build && severedRelics.isEmpty() &&
                discoveredPairingMask == identitySource.discoveredPairingMask &&
                previous.stormCharge.sameBitsAs(stormCharge) &&
                previous.stasisMeter.sameBitsAs(stasisMeter / MutableGameState.STASIS_SECONDS) &&
                previous.stasisBank == stasisBank &&
                previous.ouroborosSpent == ouroborosSpent &&
                previous.invulnerable == (invulnerableUntil > elapsed) &&
                previous.overdriveBurnout == (overdriveBurnoutUntil > elapsed)
        } ?: buildProjection(),
        discoveredReactionMask = discoveredReactionMask,
        discoveredPairingMask = discoveredPairingMask,
        coreRadius = coreRadius,
        abilityPower = abilityPower(),
        interpolationAlpha = renderInterpolationAlpha(),
        previousCoreX = previousCoreX,
        previousCoreY = previousCoreY,
        previousCameraX = previousCameraX,
        previousCameraY = previousCameraY,
        previousMorningstarX = previousMorningstarX,
        previousMorningstarY = previousMorningstarY,
        viewportDensity = viewportDensity,
    )
}

/**
 * How far the accumulated real time has advanced past the last simulated state, in fixed steps.
 * Only a running simulation accumulates time; every other phase presents its exact state, and so
 * does a run (re)entering RUNNING until its first step makes the previous positions current.
 */
internal fun MutableGameState.renderInterpolationAlpha(): Float =
    if (phase == GamePhase.RUNNING && interpolationPrimed) {
        (accumulator / MutableGameState.FIXED_STEP).coerceIn(0f, 1f)
    } else {
        1f
    }

/** Run totals change rarely; an unchanged set keeps the previous immutable projection. */
private fun MutableGameState.runStatisticsProjection(reusable: RunStatistics?): RunStatistics {
    if (reusable != null &&
        reusable.damageDealt.toRawBits() == damageDealt.toRawBits() &&
        reusable.damageTaken.toRawBits() == damageTaken.toRawBits() &&
        reusable.damageAbsorbed.toRawBits() == damageAbsorbed.toRawBits() &&
        reusable.dataCollected == dataCollected &&
        reusable.pickupsCollected == pickupsCollected &&
        reusable.keysCollected == keysCollected &&
        reusable.eliteKills == eliteKills &&
        reusable.bestCombo == bestCombo
    ) {
        return reusable
    }
    return RunStatistics(
        damageDealt = damageDealt,
        damageTaken = damageTaken,
        damageAbsorbed = damageAbsorbed,
        dataCollected = dataCollected,
        pickupsCollected = pickupsCollected,
        keysCollected = keysCollected,
        eliteKills = eliteKills,
        bestCombo = bestCombo,
    )
}

/** The same runtime (a scalar-input sibling) or equal content keeps the previous projection. */
private fun MutableGameState.characterAbilityProjection(
    reusable: CharacterAbilityProjection?,
    identityRuntime: CharacterRuntime?,
): CharacterAbilityProjection {
    val runtime = characterRuntime
    if (reusable != null && (runtime === identityRuntime || reusable.matches(this, runtime))) return reusable
    val shape = coreShape
    return CharacterAbilityProjection(
        charge = runtime.charge,
        ringRadius = runtime.haloRadius,
        parryWindow = runtime.parryWindow,
        lattice = if (shape == CoreShape.TESSERACT) {
            immutableListOfSize(runtime.latticeCount) { CharacterLatticePoint(runtime.latticeX(it), runtime.latticeY(it)) }
        } else {
            immutableListOf()
        },
        facets = if (shape == CoreShape.PRISM) runtime.facets else 0,
        facetProgress = facetProgress(runtime),
        edge = if (shape == CoreShape.DIAMOND) runtime.edge else 0,
        stellarMass = if (shape == CoreShape.STAR) runtime.stellarMass else 0,
        novaFlash = runtime.novaFlash,
        haloBurstFlash = runtime.haloBurstFlash,
        polarity = if (shape == CoreShape.DIPOLE) runtime.polarity.ordinal else 0,
        polarityFlash = runtime.polarityFlash,
        beatPeriod = if (shape == CoreShape.PENTAGON) BEAT_PERIOD else 0f,
        beatWindow = if (shape == CoreShape.PENTAGON) BEAT_WINDOW else 0f,
        beatStreak = if (shape == CoreShape.PENTAGON) runtime.beatStreak else 0,
        blinkCharges = if (shape == CoreShape.CRESCENT) runtime.blinkCharges else 0,
        blinkRecharge = if (shape == CoreShape.CRESCENT) runtime.blinkRecharge / BLINK_RECHARGE_SECONDS else 0f,
        scars = if (shape == CoreShape.CRESCENT) {
            immutableListOfSize(runtime.scarCount) { index -> scarProjection(runtime, index) }
        } else {
            immutableListOf()
        },
        drones = if (shape == CoreShape.HEXAGON) {
            immutableListOfSize(runtime.droneCount) { index ->
                CharacterDroneProjection(
                    x = runtime.droneValue(index, CharacterRuntime.DRONE_X),
                    y = runtime.droneValue(index, CharacterRuntime.DRONE_Y),
                    previousX = runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_X),
                    previousY = runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_Y),
                    darting = runtime.droneValue(index, CharacterRuntime.DRONE_DART) > 0f,
                )
            }
        } else {
            immutableListOf()
        },
        ghostActive = ghostActive,
        ghostX = if (ghostActive) ghostX else 0f,
        ghostY = if (ghostActive) ghostY else 0f,
        previousGhostX = if (ghostActive) previousGhostX else 0f,
        previousGhostY = if (ghostActive) previousGhostY else 0f,
    )
}

private fun MutableGameState.facetProgress(runtime: CharacterRuntime): Float =
    if (coreShape == CoreShape.PRISM && runtime.facets < CharacterRuntime.MAX_FACETS) {
        (runtime.facetTimer / FACET_REGEN_SECONDS).coerceIn(0f, 1f)
    } else {
        0f
    }

private fun MutableGameState.scarProjection(runtime: CharacterRuntime, index: Int): CharacterScarProjection =
    CharacterScarProjection(
        x0 = runtime.scarValue(index, CharacterRuntime.SCAR_X0),
        y0 = runtime.scarValue(index, CharacterRuntime.SCAR_Y0),
        x1 = runtime.scarValue(index, CharacterRuntime.SCAR_X1),
        y1 = runtime.scarValue(index, CharacterRuntime.SCAR_Y1),
        fuse = scarFuse(runtime, index),
        fade = scarFade(runtime, index),
    )

/** Fuse progress of scar [index]: 0 when laid, 1 from its detonation on. */
private fun MutableGameState.scarFuse(runtime: CharacterRuntime, index: Int): Float {
    if (runtime.scarValue(index, CharacterRuntime.SCAR_AGE) >= 0f) return 1f
    return (1f - (runtime.scarValue(index, CharacterRuntime.SCAR_DETONATE_AT) - elapsed) / SCAR_FUSE_SECONDS).coerceIn(0f, 1f)
}

/** Seconds since scar [index] detonated (0 before). */
private fun scarFade(runtime: CharacterRuntime, index: Int): Float =
    runtime.scarValue(index, CharacterRuntime.SCAR_AGE).coerceAtLeast(0f)

/** Compares every projected field bit for bit against what [characterAbilityProjection] would build now. */
private fun CharacterAbilityProjection.matches(state: MutableGameState, runtime: CharacterRuntime): Boolean {
    val shape = state.coreShape
    if (!charge.sameBitsAs(runtime.charge) || !ringRadius.sameBitsAs(runtime.haloRadius) ||
        !parryWindow.sameBitsAs(runtime.parryWindow) ||
        facets != (if (shape == CoreShape.PRISM) runtime.facets else 0) ||
        !facetProgress.sameBitsAs(state.facetProgress(runtime)) ||
        edge != (if (shape == CoreShape.DIAMOND) runtime.edge else 0) ||
        stellarMass != (if (shape == CoreShape.STAR) runtime.stellarMass else 0) ||
        !novaFlash.sameBitsAs(runtime.novaFlash) || !haloBurstFlash.sameBitsAs(runtime.haloBurstFlash) ||
        polarity != (if (shape == CoreShape.DIPOLE) runtime.polarity.ordinal else 0) ||
        !polarityFlash.sameBitsAs(runtime.polarityFlash) ||
        !beatPeriod.sameBitsAs(if (shape == CoreShape.PENTAGON) BEAT_PERIOD else 0f) ||
        !beatWindow.sameBitsAs(if (shape == CoreShape.PENTAGON) BEAT_WINDOW else 0f) ||
        beatStreak != (if (shape == CoreShape.PENTAGON) runtime.beatStreak else 0) ||
        blinkCharges != (if (shape == CoreShape.CRESCENT) runtime.blinkCharges else 0) ||
        !blinkRecharge.sameBitsAs(if (shape == CoreShape.CRESCENT) runtime.blinkRecharge / BLINK_RECHARGE_SECONDS else 0f) ||
        ghostActive != state.ghostActive
    ) {
        return false
    }
    if (ghostActive && (!ghostX.sameBitsAs(state.ghostX) || !ghostY.sameBitsAs(state.ghostY) ||
            !previousGhostX.sameBitsAs(state.previousGhostX) || !previousGhostY.sameBitsAs(state.previousGhostY))
    ) {
        return false
    }
    val latticeCount = if (shape == CoreShape.TESSERACT) runtime.latticeCount else 0
    if (lattice.size != latticeCount) return false
    for (index in lattice.indices) {
        val point = lattice[index]
        if (!point.x.sameBitsAs(runtime.latticeX(index)) || !point.y.sameBitsAs(runtime.latticeY(index))) return false
    }
    val scarCount = if (shape == CoreShape.CRESCENT) runtime.scarCount else 0
    if (scars.size != scarCount) return false
    for (index in scars.indices) {
        val scar = scars[index]
        // Field by field from the runtime: the check builds no projection.
        if (!scar.x0.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_X0)) ||
            !scar.y0.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_Y0)) ||
            !scar.x1.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_X1)) ||
            !scar.y1.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_Y1)) ||
            !scar.fuse.sameBitsAs(state.scarFuse(runtime, index)) || !scar.fade.sameBitsAs(scarFade(runtime, index))
        ) {
            return false
        }
    }
    val droneCount = if (shape == CoreShape.HEXAGON) runtime.droneCount else 0
    if (drones.size != droneCount) return false
    for (index in drones.indices) {
        val drone = drones[index]
        if (!drone.x.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_X)) ||
            !drone.y.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_Y)) ||
            !drone.previousX.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_X)) ||
            !drone.previousY.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_Y)) ||
            drone.darting != (runtime.droneValue(index, CharacterRuntime.DRONE_DART) > 0f)
        ) {
            return false
        }
    }
    return true
}

/**
 * Points of interest change a few times per second at most: the same list at the same time, or
 * projections that already carry every derived value, keep the previous immutable list.
 */
private fun MutableGameState.pointsOfInterestProjection(
    reusable: ImmutableList<PointOfInterestProjection>?,
    identitySource: MutableGameState?,
): ImmutableList<PointOfInterestProjection> {
    if (reusable != null) {
        if (pointsOfInterest === identitySource?.pointsOfInterest && elapsed.sameBitsAs(identitySource.elapsed)) return reusable
        if (reusable.size == pointsOfInterest.size) {
            var same = true
            for (index in pointsOfInterest.indices) {
                if (!pointProjectionMatches(reusable[index], pointsOfInterest[index])) {
                    same = false
                    break
                }
            }
            if (same) return reusable
        }
    }
    return pointsOfInterest.mapToImmutableList { point ->
        PointOfInterestProjection(point.kind, content.pointsOfInterest.definition(point.kind).name,
            point.x, point.y, point.active, pointRemaining(point), point.nextBeacon, pointProgress(point),
            point.defenderIds, point.warningRemaining, point.volleyAngle)
    }
}

private fun MutableGameState.pointRemaining(point: PointOfInterestState): Float =
    if (point.active) point.remaining else (point.expiresAt - elapsed).coerceAtLeast(0f)

private fun MutableGameState.pointProgress(point: PointOfInterestState): Float = when (point.kind) {
    kinetickk.ball.content.api.PointOfInterestKind.RESONANT_CIRCUIT -> (point.nextBeacon - 1) / 3f
    kinetickk.ball.content.api.PointOfInterestKind.SEALED_ANOMALY -> point.defeatedDefenders / 3f
    kinetickk.ball.content.api.PointOfInterestKind.COLLAPSING_ORBIT ->
        point.orbitSeconds / content.pointsOfInterest.orbitRequiredSeconds
}

private fun MutableGameState.pointProjectionMatches(
    projection: PointOfInterestProjection,
    point: PointOfInterestState,
): Boolean =
    projection.kind == point.kind &&
        projection.x.sameBitsAs(point.x) &&
        projection.y.sameBitsAs(point.y) &&
        projection.active == point.active &&
        projection.remaining.sameBitsAs(pointRemaining(point)) &&
        projection.nextBeacon == point.nextBeacon &&
        projection.progress.sameBitsAs(pointProgress(point)) &&
        (projection.defenderIds === point.defenderIds || projection.defenderIds == point.defenderIds) &&
        projection.warningRemaining.sameBitsAs(point.warningRemaining) &&
        projection.volleyAngle.sameBitsAs(point.volleyAngle)

private fun <Source : Any, Projection> Source.reuseIfIdentical(
    previousSource: Source?,
    previousProjection: Projection?,
): Projection? = previousProjection?.takeIf { this === previousSource }

private fun <Source, Projection> List<Source>.reuseEmptyProjection(
    previous: ImmutableList<Projection>?,
): ImmutableList<Projection>? =
    previous?.takeIf { isEmpty() && it.isEmpty() }

private inline fun <Source, Projection> List<Source>.reuseProjectionIfContentEqual(
    previous: ImmutableList<Projection>?,
    sameProjection: (Source, Projection) -> Boolean,
): ImmutableList<Projection>? {
    if (previous == null || size != previous.size) return null
    var index = 0
    while (index < size) {
        if (!sameProjection(this[index], previous[index])) return null
        index++
    }
    return previous
}

private fun Float.sameBitsAs(other: Float): Boolean = toRawBits() == other.toRawBits()

private fun <Element> List<Element>.reuseIfContentEqual(
    previous: ImmutableList<Element>?,
): ImmutableList<Element>? {
    if (previous == null || size != previous.size) return null
    var index = 0
    while (index < size) {
        if (this[index] != previous[index]) return null
        index++
    }
    return previous
}

private fun CopyOnWriteIntArray.reuseIfStorageShared(
    previousSource: CopyOnWriteIntArray?,
    previousProjection: ImmutableList<Int>?,
): ImmutableList<Int>? =
    previousProjection?.takeIf { previousSource != null && sharesStorageWith(previousSource) }

private fun <Element> CopyOnWriteMutableSet<Element>.reuseIfStorageShared(
    previousSource: CopyOnWriteMutableSet<Element>?,
    previousProjection: ImmutableSet<Element>?,
): ImmutableSet<Element>? =
    previousProjection?.takeIf { previousSource != null && sharesStorageWith(previousSource) }
