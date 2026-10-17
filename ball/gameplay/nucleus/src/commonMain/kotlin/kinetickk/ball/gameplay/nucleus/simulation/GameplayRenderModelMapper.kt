// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

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
                    affinityBits(value) == previous.affinityBits
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
                    value.previousY.sameBitsAs(previous.previousY)
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
 * Only a running simulation accumulates time; every other phase presents its exact state.
 */
internal fun MutableGameState.renderInterpolationAlpha(): Float =
    if (phase == GamePhase.RUNNING) (accumulator / MutableGameState.FIXED_STEP).coerceIn(0f, 1f) else 1f

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

/** The immutable runtime value is replaced on change, so identity or equal content keeps the projection. */
private fun MutableGameState.characterAbilityProjection(
    reusable: CharacterAbilityProjection?,
    identityRuntime: CharacterRuntime?,
): CharacterAbilityProjection {
    val runtime = characterRuntime
    if (reusable != null && (runtime === identityRuntime || reusable.matches(runtime))) return reusable
    return CharacterAbilityProjection(runtime.charge, runtime.barrier, runtime.ringRadius, runtime.parryWindow,
        runtime.lattice.map { CharacterLatticePoint(it.x, it.y) }.toImmutableList())
}

private fun CharacterAbilityProjection.matches(runtime: CharacterRuntime): Boolean {
    if (!charge.sameBitsAs(runtime.charge) || !barrier.sameBitsAs(runtime.barrier) ||
        !ringRadius.sameBitsAs(runtime.ringRadius) || !parryWindow.sameBitsAs(runtime.parryWindow) ||
        lattice.size != runtime.lattice.size
    ) {
        return false
    }
    for (index in lattice.indices) {
        val point = lattice[index]
        val source = runtime.lattice[index]
        if (!point.x.sameBitsAs(source.x) || !point.y.sameBitsAs(source.y)) return false
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
