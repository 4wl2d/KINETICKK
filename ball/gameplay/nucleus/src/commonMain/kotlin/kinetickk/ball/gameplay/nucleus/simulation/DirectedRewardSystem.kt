// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.gameplay.nucleus.render.*

/*
 * Directed rewards: a resolved window site queues one; its choice first offers directions, then three matching offers.
 */

internal fun MutableGameState.openDirectedReward(reward: DirectedReward) {
    directedReward = reward
    selectedRewardFocus = null
    activeChoiceType = when (reward) {
        DirectedReward.WEAPON -> ChoiceType.TOTEM
        DirectedReward.RELIC -> ChoiceType.RELIC
        DirectedReward.ITEM_AND_REPAIR -> ChoiceType.ITEM
    }
    val focuses = when (reward) {
        // Only directions with an owned weapon behind them; a direction with none would offer just Amplify.
        DirectedReward.WEAPON -> WeaponFocuses.filter { focus -> weaponOfferCandidates().any { weaponRewardFocus(it) == focus } }
        DirectedReward.RELIC -> RelicFocuses.shuffled(rewardRandom).take(3)
        DirectedReward.ITEM_AND_REPAIR -> listOf(RewardFocus.OFFENSE, RewardFocus.DEFENSE, RewardFocus.ECONOMY)
    }
    choices = if (focuses.isEmpty()) {
        // No other owned weapon (a starter-only profile): skip the direction step and offer Amplify alone.
        directedWeaponChoices(emptyList())
    } else {
        focuses.map { ChoiceOption(activeChoiceType, it.label, "Choose a direction, then one of three matching offers.",
            "FOCUS", rewardFocus = it) }
    }
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

/** The weapon reward's offer: Amplify the current weapon, then each of [pool] as a change. */
private fun MutableGameState.directedWeaponChoices(pool: List<WeaponDefinition>): List<ChoiceOption> =
    listOf(ChoiceOption(ChoiceType.TOTEM, "Amplify ${currentWeaponDefinition.name}",
        "Raise current weapon mastery by one level.", "AMPLIFY", weaponId = weapon,
        totemAction = TotemAction.AMPLIFY_CURRENT)) + pool.map {
        ChoiceOption(ChoiceType.WEAPON, it.name, it.description, "CHANGE", weaponId = it.id)
    }

internal fun MutableGameState.selectRewardFocus(focus: RewardFocus) {
    val reward = directedReward ?: return
    selectedRewardFocus = focus
    when (reward) {
        DirectedReward.RELIC -> {
            val aspect = RelicAspect.valueOf(focus.name)
            val pool = offerableRelics()
            val matching = pool.filter { it.aspect == aspect }.shuffled(rewardRandom).take(3)
            // Small aspects are topped up with the build's best attuned relics instead of leaving dead cards.
            val filler = pool.filter { it !in matching && it.aspect != RelicAspect.SOVEREIGN }
                .sortedByDescending { relic -> relic.attunedTags.sumOf { build.tagPoints(it) } }
                .take(3 - matching.size)
            choices = (matching + filler).map {
                ChoiceOption(ChoiceType.RELIC, it.name, it.upside, it.aspect.displayLabel,
                    relicId = it.id, relicAction = RelicChoiceAction.ACQUIRE)
            } + relicOfferTailCard()
        }
        DirectedReward.ITEM_AND_REPAIR -> {
            // A uniform shuffle of the focus pool, no weights. Actives must meet their offer rules first, and the cards
            // take the first three of the shuffle with at most one active (later actives are skipped, never redrawn).
            refillItemDeck()
            val pool = deckPool(content.items.filter {
                it.minimumRebirth <= rebirthRank && hasUsefulItemEffect(it) && activeOfferable(it) && itemRewardFocus(it) == focus
            }, 3)
            val picks = ArrayList<ItemDefinition>(3)
            for (item in pool.shuffled(rewardRandom)) {
                if (picks.size == 3) break
                if (item.isActive && picks.any { it.isActive }) continue
                picks += item
            }
            choices = picks.map { itemCard(it, it.rarity.displayLabel, reborn = rewardRandom.nextFloat() < rebornChance()) }
        }
        DirectedReward.WEAPON -> {
            choices = directedWeaponChoices(
                weightedWeaponPicks(weaponOfferCandidates().filter { weaponRewardFocus(it) == focus }, 2),
            )
        }
    }
    // The finite catalog can be exhausted; salvage is an honest terminal outcome, never a dead choice.
    if (choices.isEmpty()) {
        grantMatter(8f)
        finishChoice(kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue.LEVEL_UP)
    }
}

/** Weapon directions, in display order; [weaponRewardFocus] maps every weapon to one of them. */
private val WeaponFocuses: List<RewardFocus> = listOf(RewardFocus.MOTION, RewardFocus.CONTROL, RewardFocus.STRIKE)

/** Relic directions: Kinetic and one per element, named after their [RelicAspect]. */
private val RelicFocuses: List<RewardFocus> = listOf(
    RewardFocus.KINETIC, RewardFocus.THERMAL, RewardFocus.CRYO, RewardFocus.ION, RewardFocus.GRAVITIC,
    RewardFocus.ENTROPY, RewardFocus.SONIC, RewardFocus.VOID,
)

internal fun itemRewardFocus(item: ItemDefinition): RewardFocus = when {
    BuildTag.FORTUNE in item.tags -> RewardFocus.ECONOMY
    BuildTag.BULWARK in item.tags || BuildTag.ANCHOR in item.tags -> RewardFocus.DEFENSE
    else -> RewardFocus.OFFENSE
}

/** A weapon's POI direction follows its first build tag: movement, control or striking power. */
internal fun weaponRewardFocus(weapon: WeaponDefinition): RewardFocus = when (weapon.tags.first()) {
    BuildTag.RAM, BuildTag.DRIVE, BuildTag.OVERCLOCK -> RewardFocus.MOTION
    BuildTag.ANCHOR, BuildTag.BULWARK, BuildTag.CHAIN -> RewardFocus.CONTROL
    BuildTag.ARSENAL, BuildTag.PRECISION, BuildTag.FORTUNE -> RewardFocus.STRIKE
}
