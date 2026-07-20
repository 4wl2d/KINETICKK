// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

import kinetickk.core.collections.toImmutableList
import kinetickk.core.collections.toImmutableSet
import kinetickk.feature.game.domain.model.*
import kinetickk.feature.game.domain.projection.EnemyProjection
import kinetickk.feature.game.domain.projection.GameProjection
import kinetickk.feature.game.domain.projection.PickupProjection
import kinetickk.feature.game.domain.projection.ProjectileProjection
import kinetickk.feature.game.domain.projection.TotemProjection
import kinetickk.feature.game.domain.projection.TrailPointProjection
import kinetickk.feature.game.domain.projection.WeaponNodeProjection
import kinetickk.feature.game.domain.projection.WeaponOrbitalProjection


internal fun MutableGameState.toProjection(): GameProjection = GameProjection(
    phase = phase,
    screen = screen,
    settings = settings,
    rebirthLevel = rebirthLevel,
    highestClearedRebirth = highestClearedRebirth,
    rebirthConfirmationArmed = rebirthConfirmationArmed,
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
    lifetimeMatter = lifetimeMatter,
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
    startingWeapon = startingWeapon,
    weaponLevel = weaponLevel,
    overdriveCharge = overdriveCharge,
    overdriveTime = overdriveTime,
    rerollsRemaining = rerollsRemaining,
    acquiredItemCount = acquiredItemCount,
    recentItem = recentItem,
    equippedRelics = equippedRelics.toImmutableList(),
    morningstarAngle = morningstarAngle,
    morningstarX = morningstarX,
    morningstarY = morningstarY,
    weaponBeamTime = weaponBeamTime,
    weaponBeamStartX = weaponBeamStartX,
    weaponBeamStartY = weaponBeamStartY,
    weaponBeamEndX = weaponBeamEndX,
    weaponBeamEndY = weaponBeamEndY,
    totem = totem?.let { value -> TotemProjection(value.x, value.y, value.pulse) },
    codexPage = codexPage,
    armoryPage = armoryPage,
    settingsPage = settingsPage,
    coreShape = coreShape,
    enemies = enemies.map { value ->
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
    }.toImmutableList(),
    projectiles = projectiles.map { value ->
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
    }.toImmutableList(),
    pickups = pickups.map { value ->
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
    }.toImmutableList(),
    trail = trail.map { value -> TrailPointProjection(value.x, value.y, value.age) }.toImmutableList(),
    weaponNodes = weaponNodes.map { value ->
        WeaponNodeProjection(value.type, value.x, value.y, value.life, value.maxLife, value.radius)
    }.toImmutableList(),
    weaponOrbitals = weaponOrbitals.map { value ->
        WeaponOrbitalProjection(value.index, value.x, value.y, value.radius)
    }.toImmutableList(),
    choices = choices.toImmutableList(),
    choiceType = activeChoiceType,
    pendingRelicChoiceCount = pendingRelicChoices,
    unlockedWeapons = unlockedWeaponView.toImmutableSet(),
    itemStacks = itemStacks.asIterable().toImmutableList(),
    discoveredItemIds = discoveredItemIds.toImmutableSet(),
    metaRanks = metaRanks.asIterable().toImmutableList(),
    relicRanks = relicRanks.asIterable().toImmutableList(),
)

internal fun MutableGameState.boundedCollectionSizes(): List<Int> = buildList {
    add(enemies.size)
    add(projectiles.size)
    add(pickups.size)
    add(trail.size)
    add(weaponNodes.size)
    add(weaponOrbitals.size)
    add(choices.size)
    add(equippedRelics.size)
    add(unlockedWeaponSet.size)
    add(discoveredItemIds.size)
    add(delayedRelicHits.size)
    add(itemStacks.size)
    add(familyStacks.size)
    add(metaRanks.size)
    add(relicRanks.size)
    add(relicCooldowns.size)
    add(relicCounters.size)
    add(relicProcCounts.size)
    add(agonyMutationCounts.size)
    projectiles.forEach { projectile -> add(projectile.hitEnemyIds.size) }
}

internal fun MutableGameState.domainCollectionLimits(): List<DomainCollectionLimit> = listOf(
    DomainCollectionLimit("enemies", enemies.size, MutableGameState.MAX_ENEMIES),
    DomainCollectionLimit("projectiles", projectiles.size, MutableGameState.MAX_PROJECTILES),
    DomainCollectionLimit("pickups", pickups.size, MutableGameState.MAX_PICKUPS),
    DomainCollectionLimit("trail", trail.size, MutableGameState.MAX_TRAIL_POINTS),
    DomainCollectionLimit("delayedRelicHits", delayedRelicHits.size, MutableGameState.MAX_DELAYED_RELIC_HITS),
)

internal fun MutableGameState.estimatedStateBytes(): Long =
    8_192L +
        enemies.size * 512L +
        projectiles.size * 256L +
        pickups.size * 96L +
        trail.size * 32L +
        weaponNodes.size * 56L +
        weaponOrbitals.size * 40L +
        choices.size * 512L +
        discoveredItemIds.size * 16L +
        delayedRelicHits.size * 48L +
        projectiles.sumOf { projectile -> projectile.hitEnemyIds.size.toLong() * 16L }

/**
 * Produces an isolated reducer candidate. Committed instances are never mutated again;
 * all mutation happens on this private copy during reduction.
 */
internal fun MutableGameState.copyForReduction(): MutableGameState {
    val target = MutableGameState(
        seed = 0,
        initialMatter = 0,
        initialRebirthLevel = rebirthLevel,
    )

    target.gameplayRandom = gameplayRandom.copy()
    target.activeRebirthProfile = activeRebirthProfile
    target.upcomingRebirthProfile = upcomingRebirthProfile
    target.unlockedWeaponSet.clear()
    target.unlockedWeaponSet.addAll(unlockedWeaponSet)
    target.unlockedWeaponView = target.unlockedWeaponSet.toSet()
    metaRanks.copyInto(target.metaRanks)
    target.discoveredItemIds.clear()
    target.discoveredItemIds.addAll(discoveredItemIds)
    itemStacks.copyInto(target.itemStacks)
    familyStacks.copyInto(target.familyStacks)
    target.soundCues.clear()
    target.soundCues.addAll(soundCues)
    target.visualFxCues = visualFxCues.copy()
    target.persistenceRequested = persistenceRequested

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
    relicRanks.copyInto(target.relicRanks)
    relicCooldowns.copyInto(target.relicCooldowns)
    relicCounters.copyInto(target.relicCounters)
    relicProcCounts.copyInto(target.relicProcCounts)
    target.delayedRelicHits.clear()
    target.delayedRelicHits.addAll(delayedRelicHits.map(DelayedRelicHit::copy))
    agonyMutationCounts.copyInto(target.agonyMutationCounts)
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
    target.overlayReturnPhase = overlayReturnPhase
    target.activeChoiceType = activeChoiceType

    target.phase = phase
    target.screen = screen
    target.settings = settings
    target.rebirthLevel = rebirthLevel
    target.highestClearedRebirth = highestClearedRebirth
    target.rebirthConfirmationArmed = rebirthConfirmationArmed
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
    target.equippedRelics = equippedRelics.toList()
    target.morningstarAngle = morningstarAngle
    target.morningstarX = morningstarX
    target.morningstarY = morningstarY
    target.weaponBeamTime = weaponBeamTime
    target.weaponBeamStartX = weaponBeamStartX
    target.weaponBeamStartY = weaponBeamStartY
    target.weaponBeamEndX = weaponBeamEndX
    target.weaponBeamEndY = weaponBeamEndY
    target.totem = totem?.copy()
    target.codexPage = codexPage
    target.armoryPage = armoryPage
    target.settingsPage = settingsPage
    target.coreShape = coreShape

    target.enemies.clear()
    target.enemies.addAll(enemies.map { enemy ->
        enemy.copy(
            relicCounters = enemy.relicCounters.copyOf(),
            relicTimers = enemy.relicTimers.copyOf(),
            relicValues = enemy.relicValues.copyOf(),
        )
    })
    target.projectiles.clear()
    target.projectiles.addAll(projectiles.map { projectile ->
        projectile.copy(hitEnemyIds = projectile.hitEnemyIds.toMutableSet())
    })
    target.pickups.clear()
    target.pickups.addAll(pickups.map(Pickup::copy))
    target.trail.clear()
    target.trail.addAll(trail.map(TrailPoint::copy))
    target.weaponNodes.clear()
    target.weaponNodes.addAll(weaponNodes.map(WeaponNode::copy))
    target.weaponOrbitals.clear()
    target.weaponOrbitals.addAll(weaponOrbitals.map(WeaponOrbital::copy))
    target.choices = choices.toList()
    return target
}
