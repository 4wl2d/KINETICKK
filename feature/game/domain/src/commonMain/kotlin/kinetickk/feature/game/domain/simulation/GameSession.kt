// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

import kinetickk.feature.game.domain.model.*
import kinetickk.feature.game.domain.protocol.SoundCue
import kinetickk.feature.game.domain.protocol.VisualFxCue
import kotlin.math.max


internal fun MutableGameState.startRun() {
    screen = UiScreen.GAME
    phase = GamePhase.RUNNING
    rebirthConfirmationArmed = false
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
    relicProcCounts.fill(0)
    agonyMutationCounts.fill(0)
    delayedRelicHits.clear()
    slipstreamRelayTime = 0f
    borrowedMomentTime = 0f
    brakepointCharge = 0f
    itemStacks.fill(0)
    familyStacks.fill(0)
    totem = null
    nextEntityId = 1
    spawnClock = 0.2f
    nextEliteAt = rebirthProfile.eliteInterval(38f)
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
    elapsed = 0f
    heat = 0f
    overheated = false
    overheatHoldTime = 0f
    dashPhaseTime = 0f
    level = 1
    data = 0
    dataFraction = 0f
    nextLevelData = 18
    pendingLevelChoices = 0
    keys = 0
    kills = 0
    combo = 0
    comboTime = 0f
    runMatter = 0L
    matterFraction = 0f
    bankedThisRun = false
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
    rerollsRemaining = 1 + metaLevel(MetaUpgradeId.DATA_ARCHIVE) / 4 + rebirthProfile.bonusRerolls
    overdriveCharge = 0f
    overdriveTime = 0f
    weaponLevel = 1
    resetRunStats()
    weapon = startingWeapon
    resetWeaponRuntime()
    emitSound(SoundCue.UI_CLICK)
    repeat(rebirthProfile.openingEnemyCount) {
        val openingType = if (rebirthLevel == 0) {
            EnemyType.DRIFTER
        } else {
            enemyTypeForElapsed(threatElapsed, gameplayRandom.nextFloat())
        }
        spawnEnemy(openingType)
    }
}

internal fun MutableGameState.resetRunStats() {
    mass = 1f
    damageMultiplier = 1f + metaLevel(MetaUpgradeId.KINETIC_AMPLIFIER) * 0.05f
    weaponPower = 1f + metaLevel(MetaUpgradeId.ARMORY_LICENSE) * 0.04f * unlockedWeaponSet.size
    coolingRate = 19f * (1f + metaLevel(MetaUpgradeId.CRYO_VENTS) * 0.05f)
    magnetStrength = 4.65f * (1f + metaLevel(MetaUpgradeId.MAGNETIC_RESONANCE) * 0.04f)
    dashImpulse = 590f * (1f + metaLevel(MetaUpgradeId.DASH_CAPACITOR) * 0.05f)
    dashHeatCost = 36f
    maxHp = 100f + metaLevel(MetaUpgradeId.CORE_INTEGRITY) * 10f + rebirthProfile.playerIntegrityBonus
    hp = maxHp
    regenPerSecond = 0f
    critChance = 0.05f
    critMultiplier = 1.5f
    pickupRadius = 150f
    luck = 0f
    dataGain = 1f + metaLevel(MetaUpgradeId.DATA_ARCHIVE) * 0.05f
    matterGain = (1f + metaLevel(MetaUpgradeId.SALVAGE_PROTOCOL) * 0.05f) *
        rebirthProfile.matterGainMultiplier
    attackSpeed = 1f
    maxShield = 0f
    shield = 0f
    damageReduction = 0f
    comboWindow = 2.8f
    overdriveGain = 1f
    dragCoefficient = 0.29f
    polarityStability = 1f
    shieldRechargeDelay = 0f
    timeSinceDamage = 0f
    hurtCooldown = 0f
}

internal fun MutableGameState.returnToMenu() {
    if (phase != GamePhase.MENU) bankRunMatter()
    phase = GamePhase.MENU
    screen = UiScreen.GAME
    keyboardBrakeActive = false
    secondaryBrakeActive = false
    touchBrakeActive = false
    updateBraking()
    dashBufferTime = 0f
    accumulator = 0f
}

internal fun MutableGameState.togglePause() {
    if (screen != UiScreen.GAME) return
    if (phase == GamePhase.RUNNING) dashBufferTime = 0f
    phase = when (phase) {
        GamePhase.RUNNING -> GamePhase.PAUSED
        GamePhase.PAUSED -> GamePhase.RUNNING
        else -> phase
    }
    updateBraking()
    accumulator = 0f
}

internal fun MutableGameState.handleEscape() {
    if (screen != UiScreen.GAME) {
        closeOverlay()
    } else {
        togglePause()
    }
}

internal fun MutableGameState.openSettings() {
    settingsPage = 0
    openOverlay(UiScreen.SETTINGS)
}
internal fun MutableGameState.openLab() = openOverlay(UiScreen.LAB)
internal fun MutableGameState.openArmory() = openOverlay(UiScreen.ARMORY)
internal fun MutableGameState.openRebirth() {
    rebirthConfirmationArmed = false
    openOverlay(UiScreen.REBIRTH)
}
internal fun MutableGameState.openCodex() = openOverlay(UiScreen.CODEX)

internal fun MutableGameState.openOverlay(target: UiScreen) {
    if (phase == GamePhase.CHOICE) return
    if (phase in listOf(GamePhase.GAME_OVER, GamePhase.VICTORY) && target != UiScreen.REBIRTH) return
    overlayReturnPhase = if (phase == GamePhase.RUNNING) GamePhase.PAUSED else phase
    if (phase == GamePhase.RUNNING) {
        phase = GamePhase.PAUSED
        dashBufferTime = 0f
    }
    screen = target
    updateBraking()
    emitSound(SoundCue.UI_CLICK)
}

internal fun MutableGameState.closeOverlay() {
    if (screen == UiScreen.GAME) return
    screen = UiScreen.GAME
    phase = overlayReturnPhase
    rebirthConfirmationArmed = false
    updateBraking()
    emitSound(SoundCue.UI_CLICK)
}

/** Arms the next threat tier first, then starts it on the second request. */
internal fun MutableGameState.requestRebirth(): Boolean {
    if (!canRebirth) return false
    if (!rebirthConfirmationArmed) {
        rebirthConfirmationArmed = true
        emitSound(SoundCue.UI_CLICK)
        return false
    }
    rebirthLevel++
    activeRebirthProfile = RebirthProgression.profile(rebirthLevel)
    upcomingRebirthProfile = RebirthProgression.profile(rebirthLevel + 1)
    rebirthConfirmationArmed = false
    persist()
    startRun()
    message = "REBIRTH $rebirthLevel // ${rebirthProfile.directive.displayName.uppercase()}"
    messageTime = 2.8f
    emitSound(SoundCue.PURCHASE)
    return true
}

internal fun MutableGameState.isCoreShapeUnlocked(shape: CoreShape): Boolean = lifetimeMatter >= when (shape) {
    CoreShape.ORB -> 0L
    CoreShape.PRISM -> 25L
    CoreShape.SHARD -> 90L
}

internal fun MutableGameState.setCoreShape(shape: CoreShape) {
    if (!isCoreShapeUnlocked(shape)) return
    coreShape = shape
    persist()
    emitSound(SoundCue.UI_CLICK)
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
    braking = phase == GamePhase.RUNNING && screen == UiScreen.GAME &&
        (keyboardBrakeActive || secondaryBrakeActive || touchBrakeActive)
}

internal fun MutableGameState.requestDash() {
    if (phase == GamePhase.RUNNING && screen == UiScreen.GAME) {
        dashBufferTime = MutableGameState.DASH_INPUT_BUFFER_SECONDS
    }
}

internal fun MutableGameState.handleEnter() {
    if (screen != UiScreen.GAME) {
        closeOverlay()
        return
    }
    when (phase) {
        GamePhase.MENU, GamePhase.GAME_OVER, GamePhase.VICTORY -> startRun()
        GamePhase.PAUSED -> togglePause()
        else -> Unit
    }
}

internal fun MutableGameState.toggleMute() {
    val enable = !settings.soundEnabled && !settings.musicEnabled
    settings = settings.copy(soundEnabled = enable, musicEnabled = enable)
    persist()
    emitSound(SoundCue.UI_CLICK)
}

internal fun MutableGameState.choose(index: Int) {
    if (phase != GamePhase.CHOICE || index !in choices.indices) return
    val option = choices[index]
    val sound = when (option.type) {
        ChoiceType.ITEM -> {
            val itemId = option.itemId ?: return
            acquireItem(itemId)
            SoundCue.LEVEL_UP
        }
        ChoiceType.TOTEM -> when (option.totemAction ?: return) {
            TotemAction.AMPLIFY_CURRENT -> {
                amplifyCurrentWeapon()
                SoundCue.WEAPON_ACQUIRED
            }
            TotemAction.CHANGE_WEAPON -> {
                openWeaponChoice()
                emitSound(SoundCue.UI_CLICK)
                return
            }
        }
        ChoiceType.WEAPON -> {
            val weaponId = option.weaponId ?: return
            equipRunWeapon(weaponId)
            SoundCue.WEAPON_ACQUIRED
        }
        ChoiceType.RELIC -> when (option.relicAction ?: return) {
            RelicChoiceAction.ACQUIRE -> {
                val relicId = option.relicId ?: return
                if (relicRank(relicId) > 0 || equippedRelics.size < RelicCatalog.MAX_SLOTS) {
                    acquireRelic(relicId)
                    SoundCue.WEAPON_ACQUIRED
                } else {
                    pendingBindingRelic = relicId
                    pendingRelicBindAction = RelicChoiceAction.REPLACE
                    openRelicBindChoice()
                    emitSound(SoundCue.UI_CLICK)
                    return
                }
            }
            RelicChoiceAction.MELD -> {
                pendingBindingRelic = null
                pendingRelicBindAction = RelicChoiceAction.MELD_TARGET
                openRelicBindChoice()
                emitSound(SoundCue.UI_CLICK)
                return
            }
            RelicChoiceAction.REPLACE, RelicChoiceAction.MELD_TARGET -> return
        }
        ChoiceType.RELIC_BIND -> {
            val slot = option.relicSlot ?: return
            when (option.relicAction ?: return) {
                RelicChoiceAction.REPLACE -> replaceRelic(slot, option.relicId ?: pendingBindingRelic ?: return)
                RelicChoiceAction.MELD_TARGET -> meldRelic(slot)
                RelicChoiceAction.ACQUIRE, RelicChoiceAction.MELD -> return
            }
            pendingBindingRelic = null
            pendingRelicBindAction = null
            SoundCue.WEAPON_ACQUIRED
        }
    }
    finishChoice(sound)
}

internal fun MutableGameState.finishChoice(sound: SoundCue) {
    choices = emptyList()
    phase = GamePhase.RUNNING
    runGrace = max(runGrace, 0.5f)
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
    emitSound(SoundCue.UI_CLICK)
}
