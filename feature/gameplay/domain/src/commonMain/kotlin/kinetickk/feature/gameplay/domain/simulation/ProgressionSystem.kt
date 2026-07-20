// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.simulation

import kinetickk.core.content.*

import kinetickk.feature.gameplay.domain.model.*
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.collections.toImmutableSet
import kinetickk.core.profile.api.GameplayProgressUpdate
import kinetickk.feature.gameplay.domain.protocol.VisualFxCue
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min


internal fun MutableGameState.openItemChoice() {
    activeChoiceType = ChoiceType.ITEM
    buildItemChoices()
    dashBufferTime = 0f
    phase = GamePhase.CHOICE
}

internal fun MutableGameState.buildItemChoices() {
    val lifetimeUnlock = (1L + lifetimeMatter / 40L).coerceAtMost(80L).toInt()
    val catalogLevel = max(level, lifetimeUnlock)
    val unlocked = ItemCatalog.all.filter { it.unlockLevel <= catalogLevel }
    val eligible = unlocked.filter { itemStacks[it.id] < it.maxStacks }
    val selected = mutableListOf<ItemDefinition>()
    repeat(3) {
        val preferred = eligible.filter { candidate -> selected.none { it.id == candidate.id } }
        val available = preferred.ifEmpty { unlocked.filter { candidate -> selected.none { it.id == candidate.id } } }
        if (available.isEmpty()) return@repeat
        val rarity = rollRarity()
        val rarityPool = available.filter { it.rarity == rarity }.ifEmpty { available }
        selected += rarityPool[gameplayRandom.nextInt(rarityPool.size)]
    }
    choices = selected.map {
        ChoiceOption(
            type = ChoiceType.ITEM,
            title = it.name,
            description = compactItemDescription(it),
            tag = it.rarity.displayLabel.uppercase(),
            itemId = it.id,
        )
    }
}

internal fun MutableGameState.rollRarity(): ItemRarity {
    val roll = gameplayRandom.nextFloat() - luck.coerceIn(0f, 2f) * 0.08f
    return when {
        roll < 0.03f -> ItemRarity.LEGENDARY
        roll < 0.13f -> ItemRarity.EPIC
        roll < 0.34f -> ItemRarity.RARE
        roll < 0.67f -> ItemRarity.UNCOMMON
        else -> ItemRarity.COMMON
    }
}

internal fun MutableGameState.openTotemChoice() {
    activeChoiceType = ChoiceType.TOTEM
    val nextLevel = weaponLevel + 1
    val nextMastery = WeaponMastery.forLevel(nextLevel)
    val masteryTag = if (nextMastery != currentWeaponMastery) {
        nextMastery.displayLabel.uppercase()
    } else {
        "LEVEL $nextLevel"
    }
    choices = listOf(
        ChoiceOption(
            type = ChoiceType.TOTEM,
            title = "Amplify ${currentWeaponDefinition.name}",
            description = "Advance the current system from level $weaponLevel to $nextLevel immediately.",
            tag = masteryTag,
            weaponId = weapon,
            totemAction = TotemAction.AMPLIFY_CURRENT,
        ),
        ChoiceOption(
            type = ChoiceType.TOTEM,
            title = "Change weapon",
            description = "Recalibrate the Totem, then choose one of three different weapon systems.",
            tag = "RECALIBRATE",
            totemAction = TotemAction.CHANGE_WEAPON,
        ),
    )
    dashBufferTime = 0f
    phase = GamePhase.CHOICE
}

internal fun MutableGameState.openWeaponChoice() {
    activeChoiceType = ChoiceType.WEAPON
    buildWeaponChoices()
    dashBufferTime = 0f
    phase = GamePhase.CHOICE
}

internal fun MutableGameState.buildWeaponChoices() {
    val pool = WeaponCatalog.all.filter { it.id != weapon }.shuffled(gameplayRandom).take(3)
    choices = pool.map {
        ChoiceOption(
            type = ChoiceType.WEAPON,
            title = it.name,
            description = it.description,
            tag = it.tags.first(),
            weaponId = it.id,
        )
    }
}

internal fun MutableGameState.openRelicChoice() {
    activeChoiceType = ChoiceType.RELIC
    buildRelicChoices()
    dashBufferTime = 0f
    phase = GamePhase.CHOICE
}

internal fun MutableGameState.buildRelicChoices() {
    val selected = mutableListOf<RelicDefinition>()
    repeat(3) {
        val excluded = selected.mapTo(mutableSetOf()) { it.id }
        val sovereignChance = (0.08f + luck.coerceIn(0f, 2f) * 0.01f).coerceAtMost(0.10f)
        val preferredAspect = if (gameplayRandom.nextFloat() < sovereignChance) {
            RelicAspect.SOVEREIGN
        } else {
            RelicAspect.entries[gameplayRandom.nextInt(RelicAspect.entries.size - 1)]
        }
        val preferred = RelicCatalog.all.filter { it.aspect == preferredAspect && it.id !in excluded }
        val available = preferred.ifEmpty { RelicCatalog.all.filter { it.id !in excluded } }
        selected += available[gameplayRandom.nextInt(available.size)]
    }
    choices = buildList {
        selected.forEach { relic ->
            val currentRank = relicRank(relic.id)
            add(
                ChoiceOption(
                    type = ChoiceType.RELIC,
                    title = relic.name,
                    description = if (currentRank > 0) {
                        if (currentRank < RelicCatalog.MAX_RANK) {
                            "Merge the duplicate resonance and advance rank $currentRank to ${currentRank + 1}."
                        } else {
                            "This resonance is already rank ${RelicCatalog.MAX_RANK}; selecting it salvages Kinetic Matter."
                        }
                    } else {
                        relic.description
                    },
                    tag = relic.aspect.displayLabel.uppercase(),
                    relicId = relic.id,
                    relicAction = RelicChoiceAction.ACQUIRE,
                ),
            )
        }
        if (equippedRelics.size >= RelicCatalog.MAX_SLOTS) {
            add(
                ChoiceOption(
                    type = ChoiceType.RELIC,
                    title = "Meld resonance",
                    description = "Collapse this offering into one of the four bound Relics and raise its rank.",
                    tag = "MELD",
                    relicAction = RelicChoiceAction.MELD,
                ),
            )
        }
    }
}

internal fun MutableGameState.openRelicBindChoice() {
    activeChoiceType = ChoiceType.RELIC_BIND
    val action = pendingRelicBindAction ?: return
    val incoming = pendingBindingRelic
    choices = equippedRelics.mapIndexed { index, equipped ->
        val current = RelicCatalog.byId(equipped.id)
        when (action) {
            RelicChoiceAction.REPLACE -> {
                val replacement = RelicCatalog.byId(incoming ?: return)
                ChoiceOption(
                    type = ChoiceType.RELIC_BIND,
                    title = "Replace ${current.name}",
                    description = "Break slot ${index + 1} and bind ${replacement.name} at rank 1.",
                    tag = "SLOT ${index + 1} // REPLACE",
                    relicId = replacement.id,
                    relicAction = RelicChoiceAction.REPLACE,
                    relicSlot = index,
                )
            }
            RelicChoiceAction.MELD_TARGET -> ChoiceOption(
                type = ChoiceType.RELIC_BIND,
                title = "Meld ${current.name}",
                description = if (equipped.rank < RelicCatalog.MAX_RANK) {
                    "Collapse the offering into slot ${index + 1} and advance rank ${equipped.rank} to ${equipped.rank + 1}."
                } else {
                    "Slot ${index + 1} is already rank ${RelicCatalog.MAX_RANK}; salvage the excess resonance."
                },
                tag = "SLOT ${index + 1} // RANK ${equipped.rank}",
                relicId = equipped.id,
                relicAction = RelicChoiceAction.MELD_TARGET,
                relicSlot = index,
            )
            RelicChoiceAction.ACQUIRE, RelicChoiceAction.MELD -> return
        }
    }
    dashBufferTime = 0f
    phase = GamePhase.CHOICE
}

internal fun MutableGameState.acquireRelic(id: RelicId) {
    val currentIndex = equippedRelics.indexOfFirst { it.id == id }
    if (currentIndex >= 0) {
        val current = equippedRelics[currentIndex]
        if (current.rank >= RelicCatalog.MAX_RANK) {
            grantMatter(8f)
            message = RelicCatalog.byId(id).name.uppercase() + " // RESONANCE SALVAGED"
        } else {
            val updated = equippedRelics.toMutableList()
            updated[currentIndex] = current.copy(rank = current.rank + 1)
            equippedRelics = updated.toList()
            relicRanks[id.ordinal] = current.rank + 1
            message = RelicCatalog.byId(id).name.uppercase() + " // RANK " + (current.rank + 1)
        }
        messageTime = 1.7f
        return
    }
    if (equippedRelics.size >= RelicCatalog.MAX_SLOTS) return
    equippedRelics = equippedRelics + EquippedRelic(id, 1)
    relicRanks[id.ordinal] = 1
    message = RelicCatalog.byId(id).name.uppercase() + " // BOUND"
    messageTime = 1.7f
}

internal fun MutableGameState.replaceRelic(slot: Int, id: RelicId) {
    if (slot !in equippedRelics.indices) return
    val replaced = equippedRelics[slot]
    clearRelicRuntime(replaced.id)
    val updated = equippedRelics.toMutableList()
    updated[slot] = EquippedRelic(id, 1)
    equippedRelics = updated.toList()
    relicRanks[replaced.id.ordinal] = 0
    relicRanks[id.ordinal] = 1
    message = RelicCatalog.byId(id).name.uppercase() + " // SLOT ${slot + 1} BOUND"
    messageTime = 1.7f
}

internal fun MutableGameState.meldRelic(slot: Int) {
    if (slot !in equippedRelics.indices) return
    val current = equippedRelics[slot]
    if (current.rank >= RelicCatalog.MAX_RANK) {
        grantMatter(8f)
        message = RelicCatalog.byId(current.id).name.uppercase() + " // RESONANCE SALVAGED"
    } else {
        val updated = equippedRelics.toMutableList()
        updated[slot] = current.copy(rank = current.rank + 1)
        equippedRelics = updated.toList()
        relicRanks[current.id.ordinal] = current.rank + 1
        message = RelicCatalog.byId(current.id).name.uppercase() + " // RANK " + (current.rank + 1)
    }
    messageTime = 1.7f
}

internal fun MutableGameState.clearRelicRuntime(id: RelicId) {
    val index = id.ordinal
    relicCooldowns[index] = 0f
    relicCounters[index] = 0
    when (id) {
        RelicId.SLIPSTREAM_RELAY -> slipstreamRelayTime = 0f
        RelicId.BRAKEPOINT_MEMORY -> brakepointCharge = 0f
        RelicId.BORROWED_MOMENT -> borrowedMomentTime = 0f
        else -> Unit
    }
    delayedRelicHits.removeAll { it.relicId == id }
    enemies.forEach { enemy ->
        enemy.relicCounters[index] = 0
        enemy.relicTimers[index] = 0f
        enemy.relicValues[index] = 0f
    }
}

internal fun MutableGameState.openNextPendingChoice() {
    if (phase != GamePhase.RUNNING) return
    when {
        pendingLevelChoices > 0 -> {
            pendingLevelChoices--
            openItemChoice()
        }
        pendingRelicChoices > 0 -> {
            pendingRelicChoices--
            openRelicChoice()
        }
    }
}

internal fun MutableGameState.amplifyCurrentWeapon() {
    weaponLevel++
    val reachedMastery = WeaponMastery.entries.firstOrNull { it.minimumLevel == weaponLevel }
    message = if (reachedMastery != null) {
        currentWeaponDefinition.name.uppercase() + " // " + reachedMastery.displayLabel.uppercase()
    } else {
        currentWeaponDefinition.name.uppercase() + " // LEVEL " + weaponLevel
    }
    messageTime = 1.5f
}

internal fun MutableGameState.acquireItem(itemId: Int) {
    val item = ItemCatalog.byId(itemId) ?: return
    if (itemStacks[itemId] >= item.maxStacks) {
        grantMatter((item.rarity.rank * 2).toFloat())
        message = item.name.uppercase() + " SALVAGED"
        messageTime = 1.4f
        return
    }
    itemStacks[itemId]++
    acquiredItemCount++
    recentItem = item
    applyItemModifier(item.primary)
    applyItemModifier(item.secondary)
    val family = item.id / 20
    familyStacks[family]++
    if (familyStacks[family] % 3 == 0) {
        weaponPower += 0.06f
        damageMultiplier += 0.04f
        message = item.family.uppercase() + " RESONANCE"
    } else {
        message = item.name.uppercase() + " ACQUIRED"
    }
    messageTime = 1.7f
    if (discoveredItemIds.add(itemId)) pendingDiscoveredItemIds += itemId
}

internal fun MutableGameState.applyItemModifier(modifier: ItemModifier) {
    when (modifier.effect) {
        ItemEffect.IMPACT_DAMAGE -> damageMultiplier += modifier.amount
        ItemEffect.WEAPON_POWER -> weaponPower += modifier.amount
        ItemEffect.MASS -> mass += modifier.amount
        ItemEffect.MAGNETISM -> magnetStrength *= 1f + modifier.amount
        ItemEffect.COOLING -> coolingRate *= 1f + modifier.amount
        ItemEffect.MAX_INTEGRITY -> {
            maxHp += modifier.amount
            hp += modifier.amount
        }
        ItemEffect.REGEN -> regenPerSecond += modifier.amount
        ItemEffect.DASH_POWER -> dashImpulse *= 1f + modifier.amount
        ItemEffect.DASH_EFFICIENCY -> dashHeatCost = max(12f, dashHeatCost * (1f - modifier.amount.coerceAtMost(0.25f)))
        ItemEffect.CRIT_CHANCE -> critChance = min(0.75f, critChance + modifier.amount)
        ItemEffect.CRIT_DAMAGE -> critMultiplier += modifier.amount
        ItemEffect.PICKUP_RADIUS -> pickupRadius += modifier.amount
        ItemEffect.LUCK -> luck += modifier.amount
        ItemEffect.DATA_GAIN -> dataGain += modifier.amount
        ItemEffect.MATTER_GAIN -> matterGain += modifier.amount
        ItemEffect.ATTACK_SPEED -> attackSpeed += modifier.amount
        ItemEffect.SHIELD_CAPACITY -> {
            maxShield += modifier.amount
            shield += modifier.amount
        }
        ItemEffect.DAMAGE_REDUCTION -> damageReduction = min(0.65f, damageReduction + modifier.amount)
        ItemEffect.COMBO_WINDOW -> comboWindow += modifier.amount
        ItemEffect.OVERDRIVE_GAIN -> overdriveGain += modifier.amount
    }
}

internal fun MutableGameState.itemStack(itemId: Int): Int = itemStacks.getOrElse(itemId) { 0 }
internal fun MutableGameState.isItemDiscovered(itemId: Int): Boolean = itemId in discoveredItemIds
internal fun MutableGameState.metaLevel(id: MetaUpgradeId): Int = metaRanks[id.ordinal]
internal fun MutableGameState.equipRunWeapon(id: WeaponId) {
    weapon = id
    resetWeaponRuntime()
    message = WeaponCatalog.byId(id).name.uppercase() + " SYNCHRONIZED"
    messageTime = 1.8f
}

internal fun MutableGameState.resetWeaponRuntime() {
    trail.clear()
    weaponNodes.clear()
    weaponOrbitals.clear()
    emitVisualFx(VisualFxCue.ClearWeaponArcs)
    projectiles.removeAll { !it.hostile }
    trailLastX = coreX
    trailLastY = coreY
    trailDistanceCarry = 0f
    morningstarAngle = 0f
    morningstarX = coreX + 105f
    morningstarY = coreY
    weaponClock = 0f
    weaponSecondaryClock = 0f
    weaponBeamTime = 0f
}

internal fun MutableGameState.grantMatter(base: Float) {
    matterFraction += base * matterGain
    val whole = floor(matterFraction).toLong()
    if (whole > 0L) {
        runMatter = saturatedAdd(runMatter, whole)
        matterFraction -= whole.toFloat()
    }
}

internal fun MutableGameState.bankRunMatter() {
    if (bankedThisRun || runMatter <= 0L) return
    bankedThisRun = true
    totalMatter = saturatedAdd(totalMatter, runMatter)
    lifetimeMatter = saturatedAdd(lifetimeMatter, runMatter)
    pendingBankedMatter = saturatedAdd(pendingBankedMatter, runMatter)
}

internal fun MutableGameState.takeProgressUpdate(): GameplayProgressUpdate? {
    if (
        pendingBankedMatter == 0L &&
        pendingDiscoveredItemIds.isEmpty() &&
        pendingClearedRebirthLevel == null
    ) {
        return null
    }
    val update = GameplayProgressUpdate(
        bankedMatter = pendingBankedMatter,
        discoveredItemIds = pendingDiscoveredItemIds.toImmutableSet(),
        clearedRebirthLevel = pendingClearedRebirthLevel,
    )
    pendingBankedMatter = 0L
    pendingDiscoveredItemIds.clear()
    pendingClearedRebirthLevel = null
    return update
}

internal fun MutableGameState.takeSoundCues(): List<AudioCue> {
    if (soundCues.isEmpty()) return emptyList()
    val result = soundCues.toList()
    soundCues.clear()
    return result
}

internal fun MutableGameState.takeVisualFxCues(): List<VisualFxCue> = visualFxCues.drain()

internal fun MutableGameState.emitSound(cue: AudioCue) {
    if (soundCues.size < 32) soundCues += cue
}

internal fun MutableGameState.emitVisualFx(cue: VisualFxCue) {
    visualFxCues.record(cue)
}
