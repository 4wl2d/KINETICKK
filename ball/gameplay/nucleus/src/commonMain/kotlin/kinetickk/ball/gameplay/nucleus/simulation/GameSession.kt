// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.max


internal fun MutableGameState.startRun() {
    phase = GamePhase.RUNNING
    interpolationPrimed = false
    matrix = foldMatrix()
    characterRuntime = CharacterRuntime()
    enemies.clear()
    projectiles.clear()
    pickups.clear()
    trail.clear()
    emitVisualFx(VisualFxCue.ClearAll)
    weaponNodes.clear()
    weaponOrbitals.clear()
    choices = emptyList()
    activeChoiceType = ChoiceType.ITEM
    equippedRelics = emptyList()
    pendingRelicChoices = 0
    pendingBindingRelic = null
    pendingRelicBindAction = null
    relicRanks.fill(0)
    relicCooldowns.fill(0f)
    relicCounters.fill(0)
    agonyMutationCounts.fill(0)
    echoHits.clear()
    itemStacks.fill(0)
    rebornItems.fill(0)
    acquisitionOrder = emptyList()
    clauseReadyAt.fill(0f)
    clauseCounters.fill(0)
    statusAccumulators.fill(0f)
    itemStatusReadyAt.fill(0f)
    buffs = emptyList()
    combatFieldTable.clear()
    coreFieldMask = 0
    fieldEnterReadyAt = 0f
    resetCoreReactionState()
    reactionTokens = MutableGameState.REACTION_BURST
    breakTokens = MutableGameState.BREAK_BURST
    severedRelics = emptyList()
    ouroborosSpent = false
    ouroborosLockedUntil = 0f
    invulnerableUntil = 0f
    glassInvulnerabilityReadyAt = 0f
    bulwarkInvulnerabilityReadyAt = 0f
    stormCharge = 0f
    stasisMeter = MutableGameState.STASIS_SECONDS
    stasisBank = 0
    stasisLockedUntil = 0f
    sinceTurn = 0f
    gyreReadyAt = 0f
    gyreEchoAt = -1f
    gyreEchoDamage = 0f
    overdriveBurnoutUntil = 0f
    overdriveExtension = 0f
    overdriveKills = 0
    aegisAbsorbed = 0f
    aegisNovaReadyAt = 0f
    shieldBreakArmed = false
    shieldBreakReadyAt = 0f
    overheatArmed = true
    wasBraking = false
    brakeHoldTime = 0f
    brakeShedSpeed = 0f
    critMissStreak = 0
    freeDashPrimed = false
    freeDashReadyAt = 0f
    comboKillCounter = 0
    glassCharge = 0f
    integrityLiabilityCut = 0f
    cinderRecoilReadyAt = 0f
    offerPity = 0
    itemOffersOpened = 0
    secondWindSpent = false
    matrixFreeDashReadyAt = 0f
    dashHitThisDash = false
    clauseDepth = 0
    arsenalCounter = 0
    necroticClock = 0f
    heirloomOfferActive = false
    heirloomPending = heirloomCandidates.isNotEmpty() && rebirthRank >= 1
    volatileBursts = kinetickk.foundation.collections.immutableListOf()
    totem = null
    nextEntityId = 1
    spawnClock = 0.2f
    nextEliteAt = rebirthProfile.eliteInterval(content.tempo.firstEliteAtSeconds)
    elitesSpawned = 0
    resetSpawnDirector()
    bossSpawned = false
    accumulator = 0f
    coreX = 0f
    coreY = 0f
    previousCoreX = 0f
    previousCoreY = 0f
    velocityX = 0f
    velocityY = 0f
    cameraX = 0f
    cameraY = 0f
    previousCameraX = 0f
    previousCameraY = 0f
    pointerX = screenWidth * 0.76f
    pointerY = screenHeight * 0.5f
    previousSingularityX = pointerX - screenWidth * 0.5f
    previousSingularityY = pointerY - screenHeight * 0.5f
    lastAimDirectionX = 1f
    lastAimDirectionY = 0f
    pointerActive = true
    keyboardBrakeActive = false
    secondaryBrakeActive = false
    touchBrakeActive = false
    updateBraking()
    dashBufferTime = 0f
    saturationHeadingX = 1f
    saturationHeadingY = 0f
    smoothedVelocityX = 0f
    smoothedVelocityY = 0f
    turnHeadingEstablished = false
    turnHoldTime = 0f
    turnRecoveryCooldown = 0f
    turnDirection = 0
    elapsed = 0f
    heat = 0f
    overheated = false
    overheatHoldTime = 0f
    dashPhaseTime = 0f
    level = 1
    data = 0
    dataFraction = 0f
    nextLevelData = content.tempo.dataRequiredForLevel(1)
    pendingLevelChoices = 0
    keys = 0
    kills = 0
    damageDealt = 0.0
    damageTaken = 0.0
    damageAbsorbed = 0.0
    dataCollected = 0L
    pickupsCollected = 0L
    keysCollected = 0L
    eliteKills = 0
    bestCombo = 0
    combo = 0
    comboTime = 0f
    runMatter = 0L
    matterFraction = 0f
    bankedRunMatter = 0L
    lastImpact = 0f
    lastImpactTime = 0f
    damageFlash = 0f
    hurtCooldown = 0f
    runGrace = 1.35f
    screenShake = 0f
    message = "DRIFT PHASE"
    messageTime = 2.2f
    recentItem = null
    acquiredItemCount = 0
    rerollsRemaining = 1 + matrix.rerolls + rebirthProfile.bonusRerolls
    overdriveCharge = matrix.startingOverdrive
    overdriveTime = 0f
    weapon = startingWeapon
    // The signature character starts its weapon one level up.
    weaponLevel = 1 + matrix.startingWeaponLevels + signatureLevelBonus()
    resetRunStats()
    recompileBuild()
    // A new run starts with a new character runtime (Dash count 0), so the weapon's input tracking starts over too.
    weaponRuntime = WeaponRuntime()
    resetWeaponRuntime()
    resetPointsOfInterest()
    resetObstacles()
    emitSound(GameplayAudioCue.UI_CLICK)
    spawnOpeningPacks()
}

/** Base pull per unit of distance to the singularity, before items and the Matrix. */
internal const val BASE_MAGNET_STRENGTH: Float = 4.65f

/** Base exponential drag on the Core's velocity per second. */
internal const val BASE_DRAG_COEFFICIENT: Float = 0.29f

/** Character base stats, then the owned Core Matrix effects from content, then Rebirth. */
internal fun MutableGameState.resetRunStats() {
    mass = 1f
    damageMultiplier = 1f
    weaponPower = 1f
    coolingRate = 19f
    magnetStrength = BASE_MAGNET_STRENGTH
    dashImpulse = 590f
    dashHeatCost = 36f
    maxHp = 100f + rebirthProfile.playerIntegrityBonus
    regenPerSecond = 0f
    critChance = 0.05f
    critMultiplier = 1.5f
    pickupRadius = content.pickups.magnetRadius
    coreRadius = MutableGameState.CORE_RADIUS
    luck = 0f
    dataGain = 1f
    matterGain = 1f
    attackSpeed = 1f
    maxShield = 0f
    shield = 0f
    damageReduction = 0f
    comboWindow = 2.8f
    overdriveGain = 1f
    dragCoefficient = BASE_DRAG_COEFFICIENT
    applyCharacterTraits()
    applyMatrixStats()
    matterGain *= rebirthProfile.matterGainMultiplier
    dashHeatCost *= matrix.dashHeatMultiplier
    dragCoefficient *= matrix.dragMultiplier
    hp = maxHp
    polarityStability = 1f
    shieldRechargeDelay = 0f
    timeSinceDamage = 0f
    hurtCooldown = 0f
}

internal fun MutableGameState.togglePause() {
    if (phase == GamePhase.RUNNING) dashBufferTime = 0f
    phase = when (phase) {
        GamePhase.RUNNING -> GamePhase.PAUSED
        GamePhase.PAUSED -> GamePhase.RUNNING
        else -> phase
    }
    updateBraking()
    accumulator = 0f
    lastTransitionSteps = 0
    interpolationPrimed = false
}

internal fun MutableGameState.pauseForOverlay() {
    if (phase == GamePhase.RUNNING) {
        phase = GamePhase.PAUSED
        dashBufferTime = 0f
        accumulator = 0f
        lastTransitionSteps = 0
    }
    keyboardBrakeActive = false
    secondaryBrakeActive = false
    touchBrakeActive = false
    updateBraking()
}

internal fun MutableGameState.exitRun() {
    bankRunMatter()
    phase = GamePhase.PAUSED
    keyboardBrakeActive = false
    secondaryBrakeActive = false
    touchBrakeActive = false
    dashBufferTime = 0f
    accumulator = 0f
    lastTransitionSteps = 0
    updateBraking()
}

internal fun MutableGameState.applyPreferences(preferences: PlayerPreferences) {
    settings = preferences.normalized()
}

internal fun MutableGameState.updatePointer(x: Float, y: Float, active: Boolean = true) {
    pointerX = clamp(x, 0f, screenWidth)
    pointerY = clamp(y, 0f, screenHeight)
    pointerActive = active
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    val dx = targetX - coreX
    val dy = targetY - coreY
    val distance = length(dx, dy)
    if (distance > 24f) {
        lastAimDirectionX = dx / distance
        lastAimDirectionY = dy / distance
    }
}

internal fun MutableGameState.setBrake(active: Boolean) {
    keyboardBrakeActive = active
    updateBraking()
}

internal fun MutableGameState.setSecondaryBrake(active: Boolean) {
    secondaryBrakeActive = active
    updateBraking()
}

internal fun MutableGameState.setTouchBrake(active: Boolean) {
    touchBrakeActive = active
    updateBraking()
}

internal fun MutableGameState.updateBraking() {
    braking = phase == GamePhase.RUNNING &&
        (keyboardBrakeActive || secondaryBrakeActive || touchBrakeActive)
}

internal fun MutableGameState.requestDash() {
    if (phase == GamePhase.RUNNING) {
        dashBufferTime = MutableGameState.DASH_INPUT_BUFFER_SECONDS
    }
}

internal fun MutableGameState.choose(index: Int) {
    if (phase != GamePhase.CHOICE || index !in choices.indices) return
    val option = choices[index]
    option.rewardFocus?.let { selectRewardFocus(it); return }
    val before = captureBuildChange()
    val sound = when (option.type) {
        ChoiceType.ITEM -> {
            val itemId = option.itemId ?: return
            acquireItem(itemId, option.reborn)
            if (directedReward == null) resolveItemOfferPity(content.item(itemId))
            GameplayAudioCue.LEVEL_UP
        }
        ChoiceType.TOTEM -> when (option.totemAction ?: return) {
            TotemAction.AMPLIFY_CURRENT -> {
                amplifyCurrentWeapon()
                GameplayAudioCue.WEAPON_ACQUIRED
            }
            TotemAction.CHANGE_WEAPON -> {
                openWeaponChoice()
                emitSound(GameplayAudioCue.UI_CLICK)
                return
            }
        }
        ChoiceType.WEAPON -> {
            val weaponId = option.weaponId ?: return
            equipRunWeapon(weaponId)
            GameplayAudioCue.WEAPON_ACQUIRED
        }
        ChoiceType.RELIC -> when (option.relicAction ?: return) {
            RelicChoiceAction.DECLINE -> {
                declineRelic(option.heirloom)
                heirloomOfferActive = false
                GameplayAudioCue.UI_CLICK
            }
            RelicChoiceAction.ACQUIRE -> {
                val relicId = option.relicId ?: return
                // Measured while the offer is still open: Heirloom Vault extras it could not show become ranks.
                val heirloomRanks = if (option.heirloom) heirloomRankBonus() else 0
                if (option.heirloom) {
                    pendingHeirloomResolved = true
                    heirloomCandidates = emptyList()
                    heirloomOfferActive = false
                }
                if (relicRank(relicId) > 0 || equippedRelics.size < content.relicPolicy.maxSlots) {
                    acquireRelic(relicId)
                    // Each extra rank arrives as if the Relic were offered again (a maxed one is salvaged).
                    repeat(heirloomRanks) { acquireRelic(relicId) }
                    GameplayAudioCue.WEAPON_ACQUIRED
                } else {
                    pendingBindingRelic = relicId
                    pendingRelicBindAction = RelicChoiceAction.REPLACE
                    openRelicBindChoice()
                    emitSound(GameplayAudioCue.UI_CLICK)
                    return
                }
            }
            RelicChoiceAction.MELD -> {
                pendingBindingRelic = null
                pendingRelicBindAction = RelicChoiceAction.MELD_TARGET
                openRelicBindChoice()
                emitSound(GameplayAudioCue.UI_CLICK)
                return
            }
            RelicChoiceAction.REPLACE, RelicChoiceAction.MELD_TARGET -> return
        }
        ChoiceType.RELIC_BIND -> {
            val slot = option.relicSlot ?: return
            when (option.relicAction ?: return) {
                RelicChoiceAction.REPLACE -> replaceRelic(slot, option.relicId ?: pendingBindingRelic ?: return)
                RelicChoiceAction.MELD_TARGET -> meldRelic(slot)
                RelicChoiceAction.ACQUIRE, RelicChoiceAction.MELD, RelicChoiceAction.DECLINE -> return
            }
            pendingBindingRelic = null
            pendingRelicBindAction = null
            GameplayAudioCue.WEAPON_ACQUIRED
        }
    }
    emitBuildChange(before, option.title)
    finishChoice(sound)
}

internal fun MutableGameState.finishChoice(sound: GameplayAudioCue) {
    if (directedReward == kinetickk.ball.content.api.DirectedReward.ITEM_AND_REPAIR) {
        heal(25f, HealSource.REPAIR)
    }
    directedReward = null
    selectedRewardFocus = null
    choices = emptyList()
    phase = GamePhase.RUNNING
    interpolationPrimed = false
    runGrace = max(runGrace, 0.5f)
    vacuumOnScreenData()
    emitSound(sound)
    openNextPendingChoice()
}

internal fun MutableGameState.rerollChoices() {
    if (!choicesCanReroll) return
    rerollsRemaining--
    when (activeChoiceType) {
        ChoiceType.ITEM -> buildItemChoices()
        ChoiceType.WEAPON -> buildWeaponChoices()
        ChoiceType.RELIC -> buildRelicChoices()
        ChoiceType.TOTEM, ChoiceType.RELIC_BIND -> return
    }
    emitSound(GameplayAudioCue.UI_CLICK)
}
