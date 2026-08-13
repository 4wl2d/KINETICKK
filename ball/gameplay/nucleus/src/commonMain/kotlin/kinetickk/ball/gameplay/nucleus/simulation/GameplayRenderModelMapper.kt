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

/**
 * Produces an isolated reducer candidate. Committed instances are never mutated again;
 * all mutation happens on this private copy during reduction.
 */
internal fun MutableGameState.copyForReduction(): MutableGameState =
    copyForReduction(shareStableStorage = false)

internal fun MutableGameState.copyForScalarInputReduction(): MutableGameState =
    copyForReduction(shareStableStorage = true)

private fun MutableGameState.copyForReduction(
    shareStableStorage: Boolean,
): MutableGameState {
    val target = MutableGameState(
        content = content,
        seed = 0,
        initialMatter = 0,
        initialRebirthLevel = rebirthLevel,
        reductionSource = this,
        shareStableReductionStorage = shareStableStorage,
    )

    target.nextEntityId = nextEntityId
    target.spawnClock = spawnClock
    target.nextEliteAt = nextEliteAt
    target.dashBufferTime = dashBufferTime
    target.bossSpawned = bossSpawned
    target.keyboardBrakeActive = keyboardBrakeActive
    target.secondaryBrakeActive = secondaryBrakeActive
    target.touchBrakeActive = touchBrakeActive
    target.uiScale = uiScale
    target.accumulator = accumulator
    target.lastTransitionSteps = lastTransitionSteps
    target.previousCoreX = previousCoreX
    target.previousCoreY = previousCoreY
    target.previousSingularityX = previousSingularityX
    target.previousSingularityY = previousSingularityY
    target.trailLastX = trailLastX
    target.trailLastY = trailLastY
    target.trailDistanceCarry = trailDistanceCarry
    target.weaponClock = weaponClock
    target.weaponSecondaryClock = weaponSecondaryClock
    target.pendingLevelChoices = pendingLevelChoices
    target.pendingRelicChoices = pendingRelicChoices
    target.pendingBindingRelic = pendingBindingRelic
    target.pendingRelicBindAction = pendingRelicBindAction
    target.slipstreamRelayTime = slipstreamRelayTime
    target.borrowedMomentTime = borrowedMomentTime
    target.brakepointCharge = brakepointCharge
    target.dataFraction = dataFraction
    target.matterFraction = matterFraction
    target.shieldRechargeDelay = shieldRechargeDelay
    target.overheatHoldTime = overheatHoldTime
    target.saturationHeadingX = saturationHeadingX
    target.saturationHeadingY = saturationHeadingY
    target.timeSinceDamage = timeSinceDamage
    target.hurtCooldown = hurtCooldown
    target.lastAimDirectionX = lastAimDirectionX
    target.lastAimDirectionY = lastAimDirectionY
    target.bankedThisRun = bankedThisRun
    target.activeChoiceType = activeChoiceType

    target.phase = phase
    target.settings = settings
    target.rebirthLevel = rebirthLevel
    target.screenWidth = screenWidth
    target.screenHeight = screenHeight
    target.coreX = coreX
    target.coreY = coreY
    target.velocityX = velocityX
    target.velocityY = velocityY
    target.cameraX = cameraX
    target.cameraY = cameraY
    target.pointerX = pointerX
    target.pointerY = pointerY
    target.pointerActive = pointerActive
    target.braking = braking
    target.elapsed = elapsed
    target.heat = heat
    target.overheated = overheated
    target.dashPhaseTime = dashPhaseTime
    target.hp = hp
    target.maxHp = maxHp
    target.shield = shield
    target.maxShield = maxShield
    target.level = level
    target.data = data
    target.nextLevelData = nextLevelData
    target.keys = keys
    target.kills = kills
    target.combo = combo
    target.comboTime = comboTime
    target.runMatter = runMatter
    target.totalMatter = totalMatter
    target.lifetimeMatter = lifetimeMatter
    target.lastImpact = lastImpact
    target.lastImpactTime = lastImpactTime
    target.damageFlash = damageFlash
    target.runGrace = runGrace
    target.screenShake = screenShake
    target.message = message
    target.messageTime = messageTime
    target.mass = mass
    target.damageMultiplier = damageMultiplier
    target.weaponPower = weaponPower
    target.coolingRate = coolingRate
    target.magnetStrength = magnetStrength
    target.dashImpulse = dashImpulse
    target.dashHeatCost = dashHeatCost
    target.regenPerSecond = regenPerSecond
    target.critChance = critChance
    target.critMultiplier = critMultiplier
    target.pickupRadius = pickupRadius
    target.luck = luck
    target.dataGain = dataGain
    target.matterGain = matterGain
    target.attackSpeed = attackSpeed
    target.damageReduction = damageReduction
    target.comboWindow = comboWindow
    target.overdriveGain = overdriveGain
    target.dragCoefficient = dragCoefficient
    target.polarityStability = polarityStability
    target.weapon = weapon
    target.startingWeapon = startingWeapon
    target.weaponLevel = weaponLevel
    target.overdriveCharge = overdriveCharge
    target.overdriveTime = overdriveTime
    target.rerollsRemaining = rerollsRemaining
    target.acquiredItemCount = acquiredItemCount
    target.recentItem = recentItem
    target.equippedRelics = equippedRelics
    target.morningstarAngle = morningstarAngle
    target.morningstarX = morningstarX
    target.morningstarY = morningstarY
    target.weaponBeamTime = weaponBeamTime
    target.weaponBeamStartX = weaponBeamStartX
    target.weaponBeamStartY = weaponBeamStartY
    target.weaponBeamEndX = weaponBeamEndX
    target.weaponBeamEndY = weaponBeamEndY
    target.totem = if (shareStableStorage) totem else totem?.copy()
    target.coreShape = coreShape

    target.choices = choices
    return target
}
