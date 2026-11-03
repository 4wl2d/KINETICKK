// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*


/**
 * Projects this state. [reusableCollections] is the previous model: a list, value or sub-projection whose content
 * equals the current one keeps its previous instance.
 *
 * Two reuse rules, one per path:
 * - The pure path passes the previous, never-mutated state as [identitySource]: a field holding the same reference
 *   as there proves an unchanged value.
 * - The tick path mutates one state in place, so it passes no [identitySource]: a reference compared against the
 *   same mutated object would hand out a stale projection. Only values that are replaced, never mutated, may be
 *   captured instead ([ProjectionSources], given as [sources]); every other value (mutable lists, runtimes, storage
 *   arrays) reuses by content equality only.
 */
internal fun MutableGameState.toRenderModel(
    reusableCollections: GameplayRenderModel? = null,
    identitySource: MutableGameState? = null,
    sources: ProjectionSources? = null,
): GameplayRenderModel {
    requireReuseSources(reusableCollections, identitySource, sources)
    return GameplayRenderModel(
        content = content, phase = phase, settings = settings, rebirthLevel = rebirthLevel,
        screenWidth = screenWidth, screenHeight = screenHeight, uiScale = uiScale,
        coreX = coreX, coreY = coreY, velocityX = velocityX, velocityY = velocityY,
        cameraX = cameraX, cameraY = cameraY, pointerX = pointerX, pointerY = pointerY, pointerActive = pointerActive,
        braking = braking, elapsed = elapsed, heat = heat,
        overheated = overheated && !build.upside(kinetickk.ball.content.api.RelicId.FURNACE_COVENANT),
        dashPhaseTime = dashPhaseTime, hp = hp, maxHp = maxHp, shield = shield, maxShield = shieldCapacity(),
        level = level, data = data, nextLevelData = nextLevelData, keys = keys, kills = kills,
        runStatistics = runStatisticsProjection(reusableCollections?.runStatistics),
        combo = combo, comboTime = comboTime, runMatter = runMatter, totalMatter = totalMatter,
        lastImpact = lastImpact, lastImpactTime = lastImpactTime, damageFlash = damageFlash, runGrace = runGrace,
        screenShake = screenShake, message = message, messageTime = messageTime,
        mass = mass, damageMultiplier = damageMultiplier, weaponPower = weaponPower,
        effectiveWeaponPower = effectiveWeaponPower(),
        rewardPreviews = reusableRewardPreviews(reusableCollections, identitySource, sources) ?: rewardPreviews(),
        coolingRate = coolingRate, magnetStrength = magnetStrength, dashImpulse = dashImpulse,
        dashHeatCost = effectiveDashHeatCost(), regenPerSecond = regenPerSecond,
        critChance = critChance, critMultiplier = critMultiplier, pickupRadius = effectivePickupRadius(),
        luck = effectiveLuck(), dataGain = dataGain, matterGain = matterGain, attackSpeed = attackSpeed,
        damageReduction = damageReduction, comboWindow = effectiveComboWindow(), overdriveGain = overdriveGain,
        dragCoefficient = dragCoefficient, polarityStability = polarityStability,
        weapon = weapon, weaponLevel = weaponLevel, overdriveCharge = overdriveCharge, overdriveTime = overdriveTime,
        rerollsRemaining = rerollsRemaining, acquiredItemCount = acquiredItemCount, recentItem = recentItem,
        equippedRelics = equippedRelicProjection(reusableCollections, identitySource),
        morningstarAngle = morningstarAngle, morningstarX = morningstarX, morningstarY = morningstarY,
        weaponBeamTime = weaponBeamTime, weaponBeamStartX = weaponBeamStartX, weaponBeamStartY = weaponBeamStartY,
        weaponBeamEndX = weaponBeamEndX, weaponBeamEndY = weaponBeamEndY,
        totem = totemProjection(reusableCollections, identitySource),
        totemChests = totemChestProjections(reusableCollections?.totemChests, identitySource),
        coreShape = coreShape,
        enemies = enemyProjections(reusableCollections, identitySource),
        projectiles = projectileProjections(reusableCollections, identitySource),
        pickups = pickupProjections(reusableCollections, identitySource),
        trail = trailProjections(reusableCollections, identitySource),
        weaponNodes = weaponNodeProjections(reusableCollections, identitySource),
        weaponOrbitals = weaponOrbitalProjections(reusableCollections, identitySource),
        choices = choiceProjection(reusableCollections, identitySource),
        choiceType = activeChoiceType, pendingRelicChoiceCount = pendingRelicChoices,
        directedChoice = directedReward != null || dimensionRewardOpen(),
        characterAbility = characterAbilityProjection(
            reusable = reusableCollections?.characterAbility,
            identityRuntime = identitySource?.characterRuntime,
        ),
        sites = siteProjections(reusable = reusableCollections?.sites, identitySource = identitySource),
        itemStacks = itemStacks.reuseIfContentEqual(reusableCollections?.itemStacks) ?: itemStacks.toImmutableList(),
        discoveredRelicMask = discoveredRelicMask,
        discoveredItemIds = discoveredItemIds.reuseIfContentEqual(reusableCollections?.discoveredItemIds)
            ?: discoveredItemIds.toImmutableSet(),
        relicRanks = relicRanks.reuseIfContentEqual(reusableCollections?.relicRanks) ?: relicRanks.toImmutableList(),
        build = buildProjection(
            previous = reusableCollections?.build,
            previousBuild = identitySource?.build ?: sources?.build,
            previousPairingMask = reusableCollections?.discoveredPairingMask,
            previousItemStacks = reusableCollections?.itemStacks,
        ),
        discoveredReactionMask = discoveredReactionMask, discoveredPairingMask = discoveredPairingMask,
        coreRadius = coreRadius, abilityPower = abilityPower(), interpolationAlpha = renderInterpolationAlpha(),
        previousCoreX = previousCoreX, previousCoreY = previousCoreY,
        previousCameraX = previousCameraX, previousCameraY = previousCameraY,
        previousMorningstarX = previousMorningstarX, previousMorningstarY = previousMorningstarY,
        viewportDensity = viewportDensity,
        obstacles = obstacleProjection(reusableCollections?.obstacles, identitySource?.obstacleField),
        minimapRange = minimapRange(),
        weaponEntities = weaponEntityProjection(reusableCollections?.weaponEntities, identitySource?.weaponRuntime),
        // The same weapon runtime changes no synergy input.
        weaponSynergy = reusableCollections?.weaponSynergy?.takeIf { identitySource != null && weaponRuntime === identitySource.weaponRuntime }
            ?: weaponSynergyProjection(reusableCollections?.weaponSynergy),
        rebirthRank = rebirthRank, volatileBursts = volatileBursts, maxLevel = content.tempo.maximumLevel,
        skillBar = skillBarProjection(reusableCollections?.skillBar),
        apex = apexProjection(reusableCollections?.apex),
        combatFields = combatFieldProjection(reusableCollections?.combatFields, identitySource?.combatFieldTable),
        coreState = coreStateProjection(reusableCollections?.coreState),
        coreStraightness = coreStraightness, easyMode = easyMode,
        ambientSites = ambientSiteProjections(reusableCollections?.ambientSites, identitySource),
        trial = trialProjection(reusableCollections?.trial),
        secretFlags = runSecretFlags, goldenDrifterId = goldenDrifterId,
        structures = structureRenderProjection(reusableCollections?.structures, identitySource),
        dimension = dimensionProjection(reusableCollections?.dimension, identitySource),
        runBoons = runBoons, runOmen = runOmen, directorPersonality = directorPersonality,
        keynoteTag = keynoteTag, keynoteElement = keynoteElement, worldEvent = worldEvent, eventSignals = eventSignals,
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
