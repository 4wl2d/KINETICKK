// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.collections.mapToImmutableList
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
    require(identitySource == null || sources == null) {
        "A projection reuses by identity source or by captured sources, never both"
    }
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
        rewardPreviews = reusableRewardPreviews(reusableCollections, identitySource, sources) ?: rewardPreviews(),
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
                    shielded(value) == previous.shielded &&
                    value.armorIntact == previous.armored &&
                    controlBits(value) == previous.controlBits &&
                    value.charges == previous.charges
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
                    armored = value.armorIntact,
                    controlBits = controlBits(value),
                    charges = value.charges,
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
                    value.age.sameBitsAs(previous.age) &&
                    value.life.sameBitsAs(previous.life) &&
                    value.scale.sameBitsAs(previous.scale)
            }
            ?: trail.mapToImmutableList { value -> TrailPointProjection(value.x, value.y, value.age, value.life, value.scale) },
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
        directedChoice = directedReward != null || dimensionRewardOpen(),
        characterAbility = characterAbilityProjection(
            reusable = reusableCollections?.characterAbility,
            identityRuntime = identitySource?.characterRuntime,
        ),
        sites = siteProjections(
            reusable = reusableCollections?.sites,
            identitySource = identitySource,
        ),
        itemStacks = itemStacks.reuseIfContentEqual(reusableCollections?.itemStacks)
            ?: itemStacks.toImmutableList(),
        discoveredRelicMask = discoveredRelicMask,
        discoveredItemIds = discoveredItemIds.reuseIfContentEqual(reusableCollections?.discoveredItemIds)
            ?: discoveredItemIds.toImmutableSet(),
        relicRanks = relicRanks.reuseIfContentEqual(reusableCollections?.relicRanks)
            ?: relicRanks.toImmutableList(),
        build = buildProjection(
            previous = reusableCollections?.build,
            previousBuild = identitySource?.build ?: sources?.build,
            previousPairingMask = reusableCollections?.discoveredPairingMask,
            previousItemStacks = reusableCollections?.itemStacks,
        ),
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
        obstacles = obstacleProjection(reusableCollections?.obstacles, identitySource?.obstacleField),
        minimapRange = minimapRange(),
        weaponEntities = weaponEntityProjection(reusableCollections?.weaponEntities, identitySource?.weaponRuntime),
        // The same weapon runtime changes no synergy input.
        weaponSynergy = reusableCollections?.weaponSynergy?.takeIf { identitySource != null && weaponRuntime === identitySource.weaponRuntime }
            ?: weaponSynergyProjection(reusableCollections?.weaponSynergy),
        rebirthRank = rebirthRank,
        volatileBursts = volatileBursts,
        maxLevel = content.tempo.maximumLevel,
        skillBar = skillBarProjection(reusableCollections?.skillBar),
        apex = apexProjection(reusableCollections?.apex),
        combatFields = combatFieldProjection(reusableCollections?.combatFields, identitySource?.combatFieldTable),
        coreState = coreStateProjection(reusableCollections?.coreState),
        coreStraightness = coreStraightness,
        easyMode = easyMode,
        ambientSites = ambientSiteProjections(reusableCollections?.ambientSites, identitySource),
        trial = trialProjection(reusableCollections?.trial),
        secretFlags = runSecretFlags,
        goldenDrifterId = goldenDrifterId,
        structures = structureRenderProjection(reusableCollections?.structures, identitySource),
        dimension = dimensionProjection(reusableCollections?.dimension, identitySource),
        runBoons = runBoons,
        runOmen = runOmen,
        directorPersonality = directorPersonality,
        keynoteTag = keynoteTag,
        keynoteElement = keynoteElement,
        worldEvent = worldEvent,
        eventSignals = eventSignals,
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
