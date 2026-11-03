// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.foundation.collections.toImmutableSet

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.foundation.collections.toImmutableList
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.EnemyNotices
import kinetickk.ball.content.api.lifetimeMatterOfferLevel

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.sites.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

internal const val MAX_GENERATED_REWARD_CHOICES: Int = 4

/** A Totem choice's weapon change offers up to three other owned weapon systems; Gambler's Ledger only widens item offers. */
internal const val WEAPON_OFFER_CHOICES: Int = 3

internal fun MutableGameState.openItemChoice() {
    activeChoiceType = ChoiceType.ITEM
    itemOffersOpened++
    buildItemChoices()
    if (choices.isEmpty()) {
        grantMatter(8f)
        message = "CATALOG COMPLETE // MATTER SALVAGED"
        messageTime = 1.5f
        return
    }
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

internal fun MutableGameState.openTotemChoice() {
    activeChoiceType = ChoiceType.TOTEM
    val nextLevel = weaponLevel + 1
    val nextMastery = content.weaponMasteryForLevel(nextLevel)
    val masteryTag = if (nextMastery != currentWeaponMastery) {
        nextMastery.displayLabel.uppercase()
    } else {
        "LEVEL $nextLevel"
    }
    // Recalibration needs another owned weapon to offer; a profile with only the starter amplifies.
    val canRecalibrate = weaponOfferCandidates().isNotEmpty()
    choices = listOfNotNull(
        amplifyOption("Advance the current system from level $weaponLevel to $nextLevel immediately.", masteryTag),
        ChoiceOption(
            type = ChoiceType.TOTEM,
            title = "Change weapon",
            description = "Recalibrate the Totem, then choose another weapon system you own.",
            tag = "RECALIBRATE",
            totemAction = TotemAction.CHANGE_WEAPON,
        ).takeIf { canRecalibrate },
    )
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

internal fun MutableGameState.openWeaponChoice() {
    activeChoiceType = ChoiceType.WEAPON
    buildWeaponChoices()
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

/** Weapons a Totem or point of interest may offer: owned by the profile and not the one equipped. */
internal fun MutableGameState.weaponOfferCandidates(): List<WeaponDefinition> =
    content.weapons.filter { it.id != weapon && it.id in unlockedWeapons }

/**
 * Offer weight of an owned weapon: the run's signature character triples it, each point in its two tags
 * adds a quarter (at most two) and a bound Relic carrying its key adds a half.
 */
internal fun MutableGameState.weaponOfferWeight(definition: WeaponDefinition): Float =
    1f + (if (signatureActive(definition)) 2f else 0f) + min(2f, 0.25f * weaponTagPoints(definition)) +
        if (harmonizingRelic(definition) != null) 0.5f else 0f

/** Up to [count] distinct weapons of [pool], drawn by [weaponOfferWeight] from the reward stream. */
internal fun MutableGameState.weightedWeaponPicks(pool: List<WeaponDefinition>, count: Int): List<WeaponDefinition> {
    val remaining = pool.toMutableList()
    val picks = ArrayList<WeaponDefinition>(min(count, pool.size))
    while (picks.size < count && remaining.isNotEmpty()) {
        var total = 0f
        for (definition in remaining) total += weaponOfferWeight(definition)
        var roll = rewardRandom.nextFloat() * total
        var chosen = remaining.lastIndex
        for (index in remaining.indices) {
            roll -= weaponOfferWeight(remaining[index])
            if (roll < 0f) {
                chosen = index
                break
            }
        }
        picks += remaining.removeAt(chosen)
    }
    return picks
}

internal fun MutableGameState.buildWeaponChoices() {
    val pool = weightedWeaponPicks(weaponOfferCandidates(), WEAPON_OFFER_CHOICES)
    choices = pool.map {
        ChoiceOption(
            type = ChoiceType.WEAPON,
            title = it.name,
            description = it.description,
            tag = it.tags.first().displayLabel.uppercase(),
            weaponId = it.id,
        )
    }
}

/**
 * Opens the next waiting choice in queue order: the run omen, the Heirloom, plain level offers, directed rewards,
 * rarity-floor offers, Relic choices, dimension rewards, then signal prompts. Level offers chain into the next of
 * the later kinds.
 */
internal fun MutableGameState.openNextPendingChoice() {
    if (phase != GamePhase.RUNNING) return
    if (omenPending) {
        openOmenChoice()
        if (phase != GamePhase.RUNNING) return
    }
    if (heirloomPending) {
        openHeirloomChoice()
        if (phase != GamePhase.RUNNING) return
    }
    while (phase == GamePhase.RUNNING && pendingLevelChoices > 0) {
        pendingLevelChoices--
        openItemChoice()
    }
    if (phase != GamePhase.RUNNING) return
    when {
        pendingDirectedRewards.isNotEmpty() -> {
            val reward = pendingDirectedRewards.first()
            pendingDirectedRewards = pendingDirectedRewards.drop(1)
            openDirectedReward(reward)
        }
        pendingFloorOffers.isNotEmpty() -> openNextFloorOffer()
        pendingRelicChoices > 0 -> {
            pendingRelicChoices--
            openRelicChoice()
        }
        hasPendingDimensionReward() -> openPendingDimensionReward()
        pendingSignalPrompts.isNotEmpty() -> openSignalPrompt()
    }
}

/** Opens the oldest waiting rarity-floor item offer. */
private fun MutableGameState.openNextFloorOffer() {
    val floor = pendingFloorOffers.first()
    pendingFloorOffers = pendingFloorOffers.drop(1)
    openItemOfferWithFloor(floor)
}

/** True once the current weapon is at [kinetickk.ball.content.api.RunTempoProfile.maximumWeaponLevel]: nothing amplifies it. */
internal fun MutableGameState.weaponAtMaximumLevel(): Boolean = weaponLevel >= content.tempo.maximumWeaponLevel

/**
 * One more level of the current weapon, announced as `[title] // LEVEL n` (or the mastery it reaches). Every source of
 * weapon levels comes through here, so none passes the maximum level: there it changes nothing and returns false.
 */
internal fun MutableGameState.amplifyCurrentWeapon(title: String = currentWeaponDefinition.name.uppercase()): Boolean {
    if (weaponAtMaximumLevel()) return false
    weaponLevel++
    val reachedMastery = content.weaponMasteries.firstOrNull { it.minimumLevel == weaponLevel }
    message = if (reachedMastery != null) {
        title + " // " + reachedMastery.displayLabel.uppercase()
    } else {
        title + " // LEVEL " + weaponLevel
    }
    messageTime = 1.5f
    return true
}

/**
 * The Amplify card of a Totem or directed WEAPON choice: [description] and [tag] below the maximum level; at it the
 * card says it salvages the amplification for Matter ([takeAmplify]).
 */
internal fun MutableGameState.amplifyOption(description: String, tag: String): ChoiceOption = ChoiceOption(
    type = ChoiceType.TOTEM,
    title = "Amplify ${currentWeaponDefinition.name}",
    description = if (weaponAtMaximumLevel()) WEAPON_PEAK_DESCRIPTION else description,
    tag = if (weaponAtMaximumLevel()) WEAPON_PEAK_TAG else tag,
    weaponId = weapon,
    totemAction = TotemAction.AMPLIFY_CURRENT,
)

/** A taken Amplify card: one weapon level, or at the maximum level [WEAPON_PEAK_SALVAGE_MATTER] base Matter instead. */
internal fun MutableGameState.takeAmplify() {
    if (amplifyCurrentWeapon()) return
    grantMatter(WEAPON_PEAK_SALVAGE_MATTER)
    message = WEAPON_PEAK_SALVAGED
    messageTime = 1.5f
}

/** Base Matter an Amplify card salvages when the weapon is already at its maximum level. */
internal const val WEAPON_PEAK_SALVAGE_MATTER: Float = 12f
private const val WEAPON_PEAK_DESCRIPTION = "This weapon is at its maximum level; selecting it salvages Kinetic Matter."
private const val WEAPON_PEAK_TAG = "MAX LEVEL"
private const val WEAPON_PEAK_SALVAGED = "WEAPON AT MAXIMUM // MATTER SALVAGED"

/**
 * Elite resonance: an Elite-class kill (the kills that drop a Key) amplifies the current weapon by the tempo's
 * resonance levels at once (0: off), up to the maximum weapon level. It draws from no stream.
 */
internal fun MutableGameState.resonateEliteKill() {
    val levels = content.tempo.eliteResonanceLevels
    var amplified = false
    repeat(levels) { if (amplifyCurrentWeapon(title = EnemyNotices.ELITE_RESONANCE)) amplified = true }
    if (amplified) emitSound(GameplayAudioCue.WEAPON_ACQUIRED)
}

/**
 * Takes one stack of [itemId]. A maxed item, or an unowned active item while every slot holds another active, is
 * salvaged for Matter instead (an offer asks which slot to replace first: [openActiveSlotChoice]); an active item's
 * first stack binds its effect into the lowest empty slot and each later stack ranks it ([stackActiveEffect]).
 */
internal fun MutableGameState.acquireItem(itemId: Int, reborn: Boolean = false) {
    val item = content.item(itemId) ?: return
    val before = itemStacks[itemId]
    if (before >= item.maxStacks || before == 0 && needsActiveSlot(item)) {
        salvageItem(item)
        return
    }
    stackItem(item, reborn)
    val tiersBefore = BuildTag.entries.map { tagTier(it) }
    item.active?.let { stackActiveEffect(item, before + 1) }
    recompileBuild()
    val reachedTier = BuildTag.entries.firstOrNull { tagTier(it) > tiersBefore[it.ordinal] }
    message = when {
        reachedTier != null -> reachedTier.displayLabel.uppercase() + " RESONANCE"
        item.isActive && before > 0 -> item.name.uppercase() + " // RANK " + (before + 1)
        else -> item.name.uppercase() + " ACQUIRED"
    }
    messageTime = 1.7f
    if (discoveredItemIds.add(itemId)) pendingDiscoveredItemIds += itemId
}

/** True when [item] is an unowned active item and every slot holds another active: taking it replaces one. */
internal fun MutableGameState.needsActiveSlot(item: ItemDefinition): Boolean =
    item.isActive && itemStacks[item.id] == 0 && skillLoadout.lowestEmptySlot() < 0

/** The maxed-item salvage: Matter by rarity ([salvageMatter]) and nothing else. */
private fun MutableGameState.salvageItem(item: ItemDefinition) {
    grantMatter(salvageMatter(item).toFloat())
    message = item.name.uppercase() + " SALVAGED"
    messageTime = 1.4f
}

/** The Matter a salvaged item grants before the run's Matter gain: rarity rank × 2. */
private fun salvageMatter(item: ItemDefinition): Int = item.rarity.rank * 2

/**
 * One more stack of [item] counted for the run (history, first acquisition, Reborn roll, its stats' marginal share and
 * one-shot grants) without the slot rules; the caller recompiles. Reward previews use it to forecast an active item that
 * still needs a slot.
 */
internal fun MutableGameState.stackItem(item: ItemDefinition, reborn: Boolean) {
    val itemId = item.id
    val before = itemStacks[itemId]
    itemStacks[itemId] = before + 1
    if (reborn) rebornItems[itemId] = 1
    if (before == 0) {
        acquisitionOrder = acquisitionOrder + itemId
        pendingAcquiredItemIds = (pendingAcquiredItemIds + itemId).takeLast(MAX_RECENT_ITEM_IDS)
    }
    acquiredItemCount++
    recentItem = item
    val policy = content.build.policy
    // Each stack adds its marginal share of the diminishing stack magnitude.
    val marginal = policy.stackMagnitude(before + 1) - policy.stackMagnitude(before)
    val rebornScale = if (rebornItems[itemId] != 0) policy.rebornMagnitude else 1f
    item.stats.forEach { stat -> applyStatDelta(stat.effect, stat.amount * marginal * rebornScale) }
    item.rules.forEach { rule -> if (rule is ItemRule.BonusRerolls) rerollsRemaining += rule.count }
}

/**
 * Asks which held active the pending incoming active item ([MutableGameState.pendingActiveItemId]) replaces: one card
 * per slot, then a card keeping the loadout (the incoming item is salvaged). Cards carry facts only (slot, held item,
 * rank, incoming item, the keep card's salvage Matter); presentation words them. Never rerolled.
 */
internal fun MutableGameState.openActiveSlotChoice() {
    val incoming = pendingActiveItemId ?: return
    activeChoiceType = ChoiceType.ACTIVE_SLOT
    choices = (0 until SkillRuntime.SLOTS).mapNotNull { slot ->
        val effect = skillLoadout.skill(slot) ?: return@mapNotNull null
        val held = content.activeItem(effect) ?: return@mapNotNull null
        ChoiceOption(
            type = ChoiceType.ACTIVE_SLOT,
            title = held.name,
            description = "",
            tag = "",
            itemId = held.id,
            skillId = effect,
            skillRank = skillLoadout.rank(slot),
            activeSlot = slot,
            incomingItemId = incoming,
        )
    } + ChoiceOption(
        type = ChoiceType.ACTIVE_SLOT,
        title = content.items[incoming].name,
        description = "",
        tag = "",
        incomingItemId = incoming,
        salvageMatter = salvageMatter(content.items[incoming]),
    )
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

/**
 * Replaces the active item in [slot] with [incomingId]: the held item leaves the run entirely (no stacks, no Reborn
 * flag, out of the gain order, its passive gone on the recompile) and everything its effect still has running ends;
 * discovery and history stay. The gauges are kept within the capacities the change leaves, then [incomingId] binds that
 * slot at rank I, ready at once. The held item may be offered again later.
 */
internal fun MutableGameState.replaceActiveItem(slot: Int, incomingId: Int, reborn: Boolean) {
    val effect = skillLoadout.skill(slot) ?: return
    val held = content.activeItem(effect) ?: return
    releaseSkillSlot(slot)
    itemStacks[held.id] = 0
    rebornItems[held.id] = 0
    statusAccumulators[held.id] = 0f
    itemStatusReadyAt[held.id] = 0f
    acquisitionOrder = acquisitionOrder.filter { it != held.id }
    recompileBuild()
    shield = min(shield, shieldCapacity() * max(1f, build.overhealOverfill))
    hp = min(hp, maxHp)
    acquireItem(incomingId, reborn)
}

/** The keep card: the loadout stays and the incoming active item is salvaged like a maxed item. */
internal fun MutableGameState.keepActiveLoadout(incomingId: Int) {
    content.item(incomingId)?.let { salvageItem(it) }
}

/** Applies a signed passive stat change. Capped stats use incremental diminishing returns. */
internal fun MutableGameState.applyStatDelta(effect: ItemEffect, amount: Float) {
    when (effect) {
        ItemEffect.IMPACT_DAMAGE -> damageMultiplier = max(0.1f, damageMultiplier + amount)
        ItemEffect.WEAPON_POWER -> weaponPower = max(0.1f, weaponPower + amount)
        ItemEffect.MASS -> mass = max(0.2f, mass + amount)
        ItemEffect.MAGNETISM -> magnetStrength *= max(0.2f, 1f + amount)
        ItemEffect.COOLING -> coolingRate *= max(0.2f, 1f + amount)
        ItemEffect.MAX_INTEGRITY -> {
            val ceiling = maxHp
            maxHp = max(10f, maxHp + amount)
            val gained = ceilingChangeAfterLiability(ceiling, amount)
            // Aegis Inversion (bound or lingering) never restores integrity, only raises the ceiling.
            val sealed = build.liability(kinetickk.ball.content.api.RelicId.AEGIS_INVERSION)
            hp = if (gained > 0f && !sealed) hp + gained else min(hp, maxHp)
        }
        ItemEffect.REGEN -> regenPerSecond = max(0f, regenPerSecond + amount)
        ItemEffect.DASH_POWER -> dashImpulse *= max(0.2f, 1f + amount)
        ItemEffect.DASH_EFFICIENCY -> dashHeatCost = max(12f, dashHeatCost * (1f - amount.coerceAtMost(0.25f)))
        ItemEffect.CRIT_CHANCE -> critChance = (critChance + amount * (1f - critChance / 0.75f)).coerceIn(0f, 0.75f)
        ItemEffect.CRIT_DAMAGE -> critMultiplier = max(1f, critMultiplier + amount)
        ItemEffect.PICKUP_RADIUS -> pickupRadius = max(content.pickups.minimumMagnetRadius, pickupRadius + amount)
        ItemEffect.LUCK -> luck = max(0f, luck + amount)
        ItemEffect.DATA_GAIN -> dataGain = max(0.1f, dataGain + amount)
        ItemEffect.MATTER_GAIN -> matterGain = max(0.1f, matterGain + amount)
        ItemEffect.ATTACK_SPEED -> attackSpeed = max(0.2f, attackSpeed + amount)
        ItemEffect.SHIELD_CAPACITY -> {
            maxShield = max(0f, maxShield + amount)
            if (amount > 0f) shield += amount
        }
        ItemEffect.DAMAGE_REDUCTION -> damageReduction = (damageReduction + amount * (1f - damageReduction / 0.65f)).coerceIn(0f, 0.65f)
        ItemEffect.COMBO_WINDOW -> comboWindow = max(0.5f, comboWindow + amount)
        ItemEffect.OVERDRIVE_GAIN -> overdriveGain = max(0.1f, overdriveGain + amount)
        // The flight law's grip and sling stats; the law reads them clamped.
        ItemEffect.GRIP -> gripStrength += amount
        ItemEffect.SLING -> slingStrength += amount
    }
}

internal fun MutableGameState.itemStack(itemId: Int): Int = itemStacks.getOrElse(itemId) { 0 }
internal fun MutableGameState.isItemDiscovered(itemId: Int): Boolean = itemId in discoveredItemIds
internal fun MutableGameState.equipRunWeapon(id: WeaponId) {
    weapon = id
    resetWeaponRuntime()
    // Relic key harmony follows the equipped weapon.
    recompileBuild()
    message = content.weapon(id).name.uppercase() + " SYNCHRONIZED"
    messageTime = 1.8f
}

internal fun MutableGameState.resetWeaponRuntime() {
    weaponRuntime.reset()
    trail.clear()
    weaponNodes.clear()
    weaponOrbitals.clear()
    emitVisualFx(VisualFxCue.ClearWeaponArcs)
    projectiles.removeMatchingStable { projectile -> !projectile.hostile }
    trailLastX = coreX
    trailLastY = coreY
    trailDistanceCarry = 0f
    morningstarAngle = 0f
    morningstarX = coreX + 105f
    morningstarY = coreY
    previousMorningstarX = morningstarX
    previousMorningstarY = morningstarY
    weaponClock = 0f
    weaponSecondaryClock = 0f
    weaponBeamTime = 0f
}

internal fun MutableGameState.grantMatter(base: Float) {
    matterFraction += base * matterGain * layerMatterMultiplier()
    val whole = floor(matterFraction).toLong()
    if (whole > 0L) {
        runMatter = saturatedAdd(runMatter, whole)
        matterFraction -= whole.toFloat()
    }
}

/** Banks run Matter not banked yet; idempotent, and Matter granted after a terminal bank still counts. */
internal fun MutableGameState.bankRunMatter() {
    achievementFlushDue = true
    val fresh = runMatter - bankedRunMatter
    if (fresh <= 0L) return
    bankedRunMatter = runMatter
    if (easyMode) return
    totalMatter = saturatedAdd(totalMatter, fresh)
    lifetimeMatter = saturatedAdd(lifetimeMatter, fresh)
    pendingBankedMatter = saturatedAdd(pendingBankedMatter, fresh)
}

internal fun MutableGameState.takeProgressUpdate(): GameplayProgressUpdate? {
    if (easyMode) { achievementFlushDue = false; clearPendingProgress(); return null }
    val flushCounters = achievementFlushDue
    achievementFlushDue = false
    if (!hasUrgentProgress() && !(flushCounters && hasPendingAchievementCounters())) return null
    val update = GameplayProgressUpdate(
        bankedMatter = pendingBankedMatter,
        discoveredItemIds = pendingDiscoveredItemIds.toImmutableSet(),
        discoveredRelicIds = RelicId.entries.filter { pendingDiscoveredRelicMask and (1L shl it.ordinal) != 0L }.toImmutableSet(),
        clearedRebirthLevel = pendingClearedRebirthLevel,
        eliteKills = pendingEliteKills, dashHits = pendingDashHits,
        completedOrbits = pendingCompletedOrbits, architectDefeatedWith = pendingArchitectDefeatedWith,
        discoveredReactionIds = ReactionId.entries.filter { pendingDiscoveredReactionMask and (1 shl it.ordinal) != 0 }.toImmutableSet(),
        discoveredPairingIds = RelicPairingId.entries.filter { pendingDiscoveredPairingMask and (1 shl it.ordinal) != 0 }.toImmutableSet(),
        victoryRelicIds = pendingVictoryRelics?.toImmutableList(),
        heirloomResolved = pendingHeirloomResolved,
        totalKills = pendingTotalKills,
        ricochets = pendingRicochets,
        dataPickups = pendingDataPickups,
        acquiredItemIds = pendingAcquiredItemIds.toImmutableList(),
        discoveredWorldEntries = WorldDiscovery.entries.filter { pendingWorldDiscoveryMask and it.bit != 0 }.toImmutableSet(),
    )
    clearPendingProgress()
    return update
}

/** Drops every pending Profile fact. Every new outbox field must be cleared here (Baby mode relies on it). */
internal fun MutableGameState.clearPendingProgress() {
    pendingBankedMatter = 0L
    pendingDiscoveredItemIds.clear()
    pendingDiscoveredRelicMask = 0L
    pendingClearedRebirthLevel = null
    pendingEliteKills = 0
    pendingDashHits = 0
    pendingCompletedOrbits = 0
    pendingArchitectDefeatedWith = null
    pendingDiscoveredReactionMask = 0
    pendingDiscoveredPairingMask = 0
    pendingVictoryRelics = null
    pendingHeirloomResolved = false
    pendingTotalKills = 0
    pendingRicochets = 0
    pendingDataPickups = 0
    pendingAcquiredItemIds = emptyList()
    pendingWorldDiscoveryMask = 0
}

/** Progress that is published by the next reduction. */
internal fun MutableGameState.hasUrgentProgress(): Boolean =
    pendingBankedMatter != 0L ||
        pendingDiscoveredItemIds.isNotEmpty() || pendingDiscoveredRelicMask != 0L ||
        pendingDiscoveredReactionMask != 0 || pendingDiscoveredPairingMask != 0 ||
        pendingVictoryRelics != null || pendingHeirloomResolved ||
        pendingClearedRebirthLevel != null || pendingEliteKills != 0 || pendingDashHits != 0 ||
        pendingCompletedOrbits != 0 || pendingArchitectDefeatedWith != null || pendingWorldDiscoveryMask != 0

/** Non-urgent progress: counters and first acquisitions ride along with the next update or the run-end flush. */
internal fun MutableGameState.hasPendingAchievementCounters(): Boolean =
    pendingTotalKills != 0 || pendingRicochets != 0 || pendingDataPickups != 0 || pendingAcquiredItemIds.isNotEmpty()

/** True when [takeProgressUpdate] would publish an update now. */
internal fun MutableGameState.hasPublishableProgress(): Boolean =
    !easyMode && (hasUrgentProgress() || achievementFlushDue && hasPendingAchievementCounters())
