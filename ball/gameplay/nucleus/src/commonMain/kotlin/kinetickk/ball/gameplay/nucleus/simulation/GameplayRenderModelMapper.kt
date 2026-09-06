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
        overheated = overheated,
        dashPhaseTime = dashPhaseTime,
        hp = hp,
        maxHp = maxHp,
        shield = shield,
        maxShield = maxShield,
        level = level,
        data = data,
        nextLevelData = nextLevelData,
        keys = keys,
        kills = kills,
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
        coolingRate = coolingRate,
        magnetStrength = magnetStrength,
        dashImpulse = dashImpulse,
        dashHeatCost = dashHeatCost,
        regenPerSecond = regenPerSecond,
        critChance = critChance,
        critMultiplier = critMultiplier,
        pickupRadius = pickupRadius,
        luck = luck,
        dataGain = dataGain,
        matterGain = matterGain,
        attackSpeed = attackSpeed,
        damageReduction = damageReduction,
        comboWindow = comboWindow,
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
                    value.dead == previous.dead
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
                    value.radius.sameBitsAs(previous.radius)
            }
            ?: weaponOrbitals.mapToImmutableList { value ->
                WeaponOrbitalProjection(value.index, value.x, value.y, value.radius)
            },
        choices = choices.reuseIfIdentical(identitySource?.choices, reusableCollections?.choices)
            ?: choices.reuseIfContentEqual(reusableCollections?.choices)
            ?: choices.toImmutableList(),
        choiceType = activeChoiceType,
        pendingRelicChoiceCount = pendingRelicChoices,
        directedChoice = directedReward != null,
        characterAbility = CharacterAbilityProjection(characterRuntime.charge, characterRuntime.barrier,
            characterRuntime.ringRadius, characterRuntime.parryWindow,
            characterRuntime.lattice.map { CharacterLatticePoint(it.x, it.y) }.toImmutableList()),
        pointsOfInterest = pointsOfInterest.map { point ->
            PointOfInterestProjection(point.kind, content.pointsOfInterest.definition(point.kind).name,
                point.x, point.y, point.active,
                if (point.active) point.remaining else (point.expiresAt - elapsed).coerceAtLeast(0f),
                point.nextBeacon, when (point.kind) {
                    kinetickk.ball.content.api.PointOfInterestKind.RESONANT_CIRCUIT -> (point.nextBeacon - 1) / 3f
                    kinetickk.ball.content.api.PointOfInterestKind.SEALED_ANOMALY -> point.defeatedDefenders / 3f
                    kinetickk.ball.content.api.PointOfInterestKind.COLLAPSING_ORBIT -> point.orbitSeconds / content.pointsOfInterest.orbitRequiredSeconds
                }, point.defenderIds, point.warningRemaining, point.volleyAngle)
        }.toImmutableList(),
        itemStacks = itemStacks.reuseIfStorageShared(
            identitySource?.itemStacks,
            reusableCollections?.itemStacks,
        ) ?: itemStacks.reuseIfContentEqual(reusableCollections?.itemStacks)
            ?: itemStacks.toImmutableList(),
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
    )
}

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
