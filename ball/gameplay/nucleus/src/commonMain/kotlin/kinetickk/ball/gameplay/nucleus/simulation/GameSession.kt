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
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.director.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.sites.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

/**
 * Starts a fresh run on this state. The holders reset their own groups ([PendingChoiceState.reset] and the others); the
 * order of the calls between them is the order every run-start draw, id and cue depends on.
 */
internal fun MutableGameState.startRun() {
    phase = GamePhase.RUNNING
    matrix = foldMatrix()
    drawItemDeck()
    characterRuntime = CharacterRuntime()
    skillRuntime = SkillRuntime()
    skillLoadout = SkillLoadout.EMPTY
    enemies.clear()
    projectiles.clear()
    pickups.clear()
    trail.clear()
    emitVisualFx(VisualFxCue.ClearAll)
    weaponNodes.clear()
    weaponOrbitals.clear()
    choices = emptyList()
    equippedRelics = emptyList()
    pendingChoice.reset()
    relicRun.reset()
    agonyMutationCounts.fill(0)
    buildRun.reset()
    elementRun.reset()
    resetCoreReactionState()
    invulnerableUntil = 0f
    relicMechanics.reset()
    overdriveBurnoutUntil = 0f
    overheatArmed = true
    wasBraking = false
    brakeHoldTime = 0f
    brakeShedSpeed = 0f
    critMissStreak = 0
    freeDashPrimed = false
    freeDashReadyAt = 0f
    dashHitThisDash = false
    heirloomPending = !easyMode && heirloomCandidates.isNotEmpty() && rebirthRank >= 1
    omenPending = offersRunOmen && content.variety.omens.enabled
    volatileBursts = kinetickk.foundation.collections.immutableListOf()
    nextEntityId = 1
    spawnClock = 0.2f
    nextEliteAt = rebirthProfile.eliteInterval(content.tempo.firstEliteAtSeconds)
    elitesSpawned = 0
    resetRunVariety()
    startRunVariety()
    resetSpawnDirector()
    bossSpawned = false
    frameState.reset()
    placeCoreForRunStart()
    keyboardBrakeActive = false
    secondaryBrakeActive = false
    touchBrakeActive = false
    updateBraking()
    dashBufferTime = 0f
    resetCoreMotionMemory()
    elapsed = 0f
    heat = 0f
    overheated = false
    overheatHoldTime = 0f
    dashPhaseTime = 0f
    runEconomy.reset(firstLevelData = dataRequiredForLevel(1))
    runStats.reset()
    combo = 0
    comboTime = 0f
    bankedRunMatter = 0L
    hurtCooldown = 0f
    runGrace = 1.35f
    offerRun.reset(rerolls = 1 + matrix.rerolls + rebirthProfile.bonusRerolls)
    overdriveCharge = matrix.startingOverdrive
    overdriveTime = 0f
    weapon = startingWeapon
    // The signature character starts its weapon one level up.
    weaponLevel = 1 + matrix.startingWeaponLevels + signatureLevelBonus()
    // Refit of the selected character is a run constant; Honing is read by the current weapon.
    characterUpgradeLevel = characterUpgradeLevels[coreShape.ordinal]
    characterUpgradePower = 1f + content.upgrades.characterPowerPerLevel * characterUpgradeLevel
    resetRunStats()
    resetDimensions()
    recompileBuild()
    // A new run starts with a new character runtime (Dash count 0), so the weapon's input tracking starts over too.
    weaponRuntime = WeaponRuntime()
    resetWeaponRuntime()
    resetSites()
    resetStructures()
    resetObstacles()
    emitSound(GameplayAudioCue.UI_CLICK)
    spawnOpeningPacks()
}

/** The Core at rest at the origin under the camera, the pointer ahead of it to the right, aiming right. */
private fun MutableGameState.placeCoreForRunStart() {
    coreX = 0f
    coreY = 0f
    previousCoreX = 0f
    previousCoreY = 0f
    velocityX = 0f
    velocityY = 0f
    cameraX = 0f
    cameraY = 0f
    pointerX = screenWidth * 0.76f
    pointerY = screenHeight * 0.5f
    previousSingularityX = pointerX - screenWidth * 0.5f
    previousSingularityY = pointerY - screenHeight * 0.5f
    lastAimDirectionX = 1f
    lastAimDirectionY = 0f
    pointerActive = true
}

/** Base pull per unit of distance to the singularity, before items and the Matrix. */
internal const val BASE_MAGNET_STRENGTH: Float = 4.65f

/** Base exponential drag on the Core's velocity per second. */
internal const val BASE_DRAG_COEFFICIENT: Float = 0.29f

/** Character base stats, then the owned Core Matrix effects from content, Refit integrity, then Rebirth. */
internal fun MutableGameState.resetRunStats() {
    coreStats.reset(pickupRadius = content.pickups.magnetRadius)
    maxHp = 100f + rebirthProfile.playerIntegrityBonus
    coreRadius = MutableGameState.CORE_RADIUS
    maxShield = 0f
    shield = 0f
    applyCharacterTraits()
    applyMatrixStats()
    maxHp += content.upgrades.characterIntegrityPerLevel * characterUpgradeLevel
    matterGain *= rebirthProfile.matterGainMultiplier
    applyOmenStats()
    dashHeatCost *= matrix.dashHeatMultiplier
    dragCoefficient *= matrix.dragMultiplier
    hp = maxHp
    polarityStability = 1f
    shieldRechargeDelay = 0f
    timeSinceDamage = 0f
    hurtCooldown = 0f
}

internal fun MutableGameState.togglePause() {
    if (phase == GamePhase.RUNNING) clearInputBuffers()
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
        clearInputBuffers()
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
    clearInputBuffers()
    accumulator = 0f
    lastTransitionSteps = 0
    updateBraking()
}

/** Drops buffered Dash, skill and Apex requests, so none fires after a pause, an exit or a reward choice. */
internal fun MutableGameState.clearInputBuffers() {
    dashBufferTime = 0f
    skillBufferMask = 0
    skillBufferTime = 0f
    apexBufferTime = 0f
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

/**
 * Buffers a cast of skill [slot] for [MutableGameState.SKILL_INPUT_BUFFER_SECONDS]; the next fixed steps cast it
 * once it can. It writes only the two buffer fields; the skill runtime changes when a step casts. An empty slot or a
 * run that is not RUNNING buffers nothing.
 */
internal fun MutableGameState.requestSkill(slot: Int) {
    if (phase != GamePhase.RUNNING || slot !in 0 until SkillRuntime.SLOTS || skillLoadout.isEmpty(slot)) return
    skillBufferMask = skillBufferMask or (1 shl slot)
    skillBufferTime = MutableGameState.SKILL_INPUT_BUFFER_SECONDS
}

/**
 * Buffers an Apex cast for [MutableGameState.SKILL_INPUT_BUFFER_SECONDS]; the next fixed steps cast it once the
 * Apex is ready. A scalar only; a locked Apex or a run that is not RUNNING buffers nothing.
 */
internal fun MutableGameState.requestApex() {
    if (phase != GamePhase.RUNNING || !apexUnlocked) return
    apexBufferTime = MutableGameState.SKILL_INPUT_BUFFER_SECONDS
}

internal fun MutableGameState.choose(index: Int) {
    if (phase != GamePhase.CHOICE || index !in choices.indices) return
    val option = choices[index]
    option.rewardFocus?.let { selectRewardFocus(it); return }
    val before = captureBuildChange()
    val sound = when (option.type) {
        ChoiceType.ITEM -> {
            val itemId = option.itemId ?: return
            val item = content.item(itemId) ?: return
            // The offer resolves when its card is taken, even when the active item still needs a slot.
            if (directedReward == null && !dimensionRewardOpen()) resolveItemOfferPity(item)
            if (needsActiveSlot(item)) {
                pendingActiveItemId = itemId
                pendingActiveReborn = option.reborn
                openActiveSlotChoice()
                emitSound(GameplayAudioCue.UI_CLICK)
                return
            }
            acquireItem(itemId, option.reborn)
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
            changeRunWeapon(weaponId)
            GameplayAudioCue.WEAPON_ACQUIRED
        }
        ChoiceType.RELIC -> chooseRelicCard(option) ?: return
        ChoiceType.ACTIVE_SLOT -> {
            val incoming = pendingActiveItemId ?: return
            val reborn = pendingActiveReborn
            pendingActiveItemId = null
            pendingActiveReborn = false
            val slot = option.activeSlot
            if (slot != null) replaceActiveItem(slot, incoming, reborn) else keepActiveLoadout(incoming)
            emitBuildChange(before, content.items[incoming].name)
            finishChoice(if (slot != null) GameplayAudioCue.LEVEL_UP else GameplayAudioCue.UI_CLICK)
            return
        }
        // An omen card accepts its omen; No omen changes nothing.
        ChoiceType.OMEN -> {
            option.omenId?.let { acceptRunOmen(it) }
            GameplayAudioCue.UI_CLICK
        }
        // A signal card pays through its channel; a locked card or a chained item offer leaves without finishing here.
        ChoiceType.SIGNAL -> resolveSignalOption(option, before) ?: return
        // A boon card takes its Blessing or Pact; the Refuse card salvages.
        ChoiceType.BOON -> takeBoonCard(option)
        ChoiceType.RELIC_BIND -> {
            if (option.relicAction != RelicChoiceAction.REPLACE) return
            replaceRelic(option.relicSlot ?: return, option.relicId ?: pendingBindingRelic ?: return)
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
    offerRarityFloor = null
    dimensionRewardLayer = null
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
        ChoiceType.TOTEM, ChoiceType.RELIC_BIND, ChoiceType.ACTIVE_SLOT,
        ChoiceType.OMEN, ChoiceType.SIGNAL, ChoiceType.BOON,
        -> return
    }
    emitSound(GameplayAudioCue.UI_CLICK)
}
