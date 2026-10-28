// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.content.api.SignalPolicy
import kinetickk.ball.content.api.SignalTuning
import kinetickk.ball.gameplay.nucleus.model.BuffSource
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.SignalPrompt
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableListAppending
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Event signals (variety.md §7): opt-in prompts between the point-of-interest windows. Which slots a run uses and which
 * kinds it meets are stateless hashes of its seed (no stream gains a draw, no plan is retained): three of the five slots,
 * three distinct kinds by weight. A signal lives at most one at a time; touching it queues its prompt (at most two wait),
 * which opens after every other pending choice. Each option pays through an existing channel: Matter, rerolls, stats,
 * buffs, heals, a Key, a Relic offer or an item offer with a rarity floor. Gamble outcomes are hashes of the run seed,
 * the signal's index and the option.
 */

/** Hash keys under [SIGNAL_SALT]: slot ranks are keyed by the slot itself, then the k-th kind draw and each slot's side. */
private const val KIND_KEY: Long = 32L
private const val SIDE_KEY: Long = 48L

/** Gamble rolls under [GAMBLE_SALT] are keyed `signalIndex × GAMBLE_KEYS_PER_SIGNAL + slot` (0 reroll, 1 matter, 2 integrity). */
private const val GAMBLE_KEYS_PER_SIGNAL: Long = 4L

/** The notice a signal raises when it appears, by [SignalKind] ordinal (English content keys the feed localizes). */
internal val SIGNAL_DETECTED_NOTICES: List<String> = SignalKind.entries.map { "SIGNAL DETECTED // " + it.displayName.uppercase() }
internal const val SIGNAL_LOST: String = "SIGNAL LOST"
internal const val WAGER_WON: String = "WAGER WON"
internal const val WAGER_LOST: String = "WAGER LOST"
internal const val TRADE_COMPLETE: String = "TRADE COMPLETE"
internal const val BLESSING_RECEIVED: String = "BLESSING RECEIVED"
internal const val CURSE_BOUND: String = "CURSE BOUND"
internal const val SALVAGE_COMPLETE: String = "SALVAGE COMPLETE"

internal const val SIGNAL_DETECTED_SECONDS: Float = 2.2f
internal const val SIGNAL_LOST_SECONDS: Float = 1.4f
internal const val SIGNAL_RESULT_SECONDS: Float = 1.6f

/** A prompt's three cards by [SignalKind] ordinal, in [SignalOption] order; [SignalOption.IGNORE] follows them. */
private val SIGNAL_OPTIONS_BY_KIND: List<List<SignalOption>> =
    SignalKind.entries.map { kind -> SignalOption.entries.filter { it.kind == kind } }

/** The run's slot [slot] opens this many seconds into the run. */
internal fun SignalPolicy.slotAt(slot: Int): Float = firstAtSeconds + slot * intervalSeconds

/**
 * True when [slot] is one of the run's signal slots: the `perRun` slots whose hashes under [SIGNAL_SALT] are lowest
 * (a tie goes to the earlier slot). A pure function of the seed; no slot is drawn from a stream.
 */
internal fun signalSlotChosen(runSeed: Int, policy: SignalPolicy, slot: Int): Boolean {
    if (!policy.enabled || slot < 0 || slot >= policy.slots) return false
    val own = runHash(runSeed, SIGNAL_SALT, slot.toLong())
    var lower = 0
    for (other in 0 until policy.slots) {
        if (other == slot) continue
        val hash = runHash(runSeed, SIGNAL_SALT, other.toLong())
        if (hash < own || (hash == own && other < slot)) lower++
    }
    return lower < policy.perRun
}

/** The run's signal slots in time order (empty when signals are off). */
internal fun plannedSignalSlots(runSeed: Int, policy: SignalPolicy): List<Int> =
    (0 until policy.slots).filter { signalSlotChosen(runSeed, policy, it) }

/** The run's signal kinds in spawn order: `perRun` distinct kinds drawn by weight (empty when signals are off). */
internal fun plannedSignalKinds(runSeed: Int, policy: SignalPolicy): List<SignalKind> {
    if (!policy.enabled) return emptyList()
    // The policy keeps at least perRun kinds weighted, so a draw always finds one.
    val drawn = drawDistinctByWeight(policy.weights, policy.perRun) { draw -> hashUnit(runHash(runSeed, SIGNAL_SALT, KIND_KEY + draw)) }
    return List(drawn.size) { SignalKind.entries[drawn[it]] }
}

/**
 * One step of the event signals: the live signal expires, is lost or is touched, then a due slot spawns the next one.
 * The Architect and any world layer but realspace drop the live signal silently and skip due slots. An engagement or a
 * live signal holds a due slot up to `trialDelaySeconds`; after that the slot is skipped. Allocation-free on every step
 * that changes nothing.
 */
internal fun MutableGameState.updateEventSignals() {
    val policy = content.variety.signals
    if (!policy.enabled) return
    updateLiveSignal(policy)
    updateSignalSlots(policy)
}

private fun MutableGameState.updateLiveSignal(policy: SignalPolicy) {
    if (eventSignals.isEmpty()) return
    val signal = eventSignals[0]
    if (bossSpawned || !inBaseLayer()) {
        eventSignals = immutableListOf()
        return
    }
    if (elapsed >= signal.expiresAt || distanceSquared(coreX, coreY, signal.x, signal.y) > policy.lostDistance * policy.lostDistance) {
        eventSignals = immutableListOf()
        message = SIGNAL_LOST
        messageTime = SIGNAL_LOST_SECONDS
        return
    }
    if (!segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, signal.x, signal.y, policy.touchRadius)) return
    // A full prompt queue refuses the touch as a whole: the signal stays where it is.
    if (pendingSignalPrompts.size >= ContentBounds.MAX_PENDING_SIGNAL_PROMPTS) return
    // At most one signal lives, and it is the latest spawned.
    pendingSignalPrompts = pendingSignalPrompts.toImmutableListAppending(SignalPrompt(signal.kind, signalsSpawned - 1))
    eventSignals = immutableListOf()
    signalsEngaged++
}

private fun MutableGameState.updateSignalSlots(policy: SignalPolicy) {
    while (nextSignalSlot < policy.slots) {
        val slot = nextSignalSlot
        val slotAt = policy.slotAt(slot)
        if (elapsed < slotAt) return
        if (elapsed >= slotAt + policy.lifetimeSeconds || signalsSpawned >= policy.perRun || bossSpawned || !inBaseLayer() ||
            !signalSlotChosen(runSeed, policy, slot)
        ) {
            nextSignalSlot++
            continue
        }
        if (engagementActive() || eventSignals.isNotEmpty()) {
            if (elapsed >= slotAt + policy.trialDelaySeconds) nextSignalSlot++
            return
        }
        spawnEventSignal(policy, slot, slotAt)
        nextSignalSlot++
        return
    }
}

/**
 * The run's next signal appears at `distance` from the Core, `sideAngle` to one side of its heading (the side a hash of
 * the slot), moved out from under obstacles like an anomaly. It keeps its slot's window: it is gone `lifetimeSeconds`
 * after the slot opened, however long the slot waited.
 */
private fun MutableGameState.spawnEventSignal(policy: SignalPolicy, slot: Int, slotAt: Float) {
    val kind = plannedSignalKinds(runSeed, policy)[signalsSpawned]
    val side = if (hashUnit(runHash(runSeed, SIGNAL_SALT, SIDE_KEY + slot)) < 0.5f) -1f else 1f
    val angle = movementAngle() + side * policy.sideAngle
    val (x, y) = clearPointOfInterestSite(coreX + cos(angle) * policy.distance, coreY + sin(angle) * policy.distance)
    eventSignals = immutableListOf(EventSignalProjection(nextEntityId++, kind, x, y, slotAt + policy.lifetimeSeconds))
    signalsSpawned++
    message = SIGNAL_DETECTED_NOTICES[kind.ordinal]
    messageTime = SIGNAL_DETECTED_SECONDS
}

/**
 * Opens the oldest waiting prompt: its kind's three cards in [SignalOption] order, then Ignore. The prompt stays at the
 * head of the queue while its cards are open (the run is in CHOICE, so nothing else can queue) and leaves it when a card
 * resolves; never rerolled.
 */
internal fun MutableGameState.openSignalPrompt() {
    val prompt = pendingSignalPrompts.firstOrNull() ?: return
    val options = SIGNAL_OPTIONS_BY_KIND[prompt.kind.ordinal]
    activeChoiceType = ChoiceType.SIGNAL
    choices = List(options.size + 1) { index -> signalCard(options.getOrElse(index) { SignalOption.IGNORE }, prompt.kind) }
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

private fun MutableGameState.signalCard(option: SignalOption, kind: SignalKind): ChoiceOption {
    val terms = signalTerms(option)
    return ChoiceOption(
        type = ChoiceType.SIGNAL,
        title = option.title,
        description = "",
        tag = if (option == SignalOption.IGNORE) "DECLINE" else kind.displayName.uppercase(),
        signalKind = kind,
        signalOption = option,
        signalCost = terms.cost,
        signalPayout = terms.payout,
        locked = terms.locked,
    )
}

/** What a card costs and pays now (Matter, integrity, rerolls or a stake, by option), and whether the run can take it. */
private class SignalTerms(val cost: Int, val payout: Int, val locked: Boolean)

private val FREE_TERMS = SignalTerms(cost = 0, payout = 0, locked = false)

private fun MutableGameState.signalTerms(option: SignalOption): SignalTerms {
    val tuning = content.variety.signals.tuning
    val unbanked = unbankedMatter()
    return when (option) {
        SignalOption.MERCHANT_RARE_OFFER -> matterPrice(tuning, tuning.merchantRareCost, payout = 0, unbanked)
        SignalOption.MERCHANT_EPIC_OFFER -> matterPrice(tuning, tuning.merchantEpicCost, payout = 0, unbanked)
        SignalOption.MERCHANT_REROLLS -> matterPrice(tuning, tuning.merchantRerollCost, tuning.merchantRerolls, unbanked)
        SignalOption.SHRINE_BLOOD -> integrityPrice(tuning.shrineBloodShare)
        SignalOption.SHRINE_WARD -> SignalTerms(
            tuning.shrineWardIntegrity.roundToInt(), tuning.shrineWardShield.roundToInt(), maxHp <= tuning.shrineWardMinimumIntegrity,
        )
        SignalOption.SHRINE_RELIC -> integrityPrice(tuning.shrineRelicShare)
        SignalOption.GAMBLE_REROLL -> SignalTerms(1, tuning.gambleRerollWin, rerollsRemaining < 1)
        SignalOption.GAMBLE_MATTER -> {
            val stake = matterStake(tuning, unbanked)
            SignalTerms(stake, (tuning.gambleMatterPayout * stake).roundToInt(), unbanked < tuning.gambleMatterMinimum)
        }
        SignalOption.GAMBLE_INTEGRITY -> integrityPrice(tuning.gambleIntegrityShare)
        SignalOption.DERELICT_REPAIR -> SignalTerms(0, (tuning.derelictRepairShare * maxHp).roundToInt(), locked = false)
        SignalOption.DERELICT_CARGO ->
            SignalTerms(0, (cargoMatter(tuning) * matterGain * layerMatterMultiplier()).roundToInt(), locked = false)
        SignalOption.CACHE_CRATE, SignalOption.CACHE_RELIQUARY, SignalOption.CACHE_IDOL, SignalOption.DERELICT_VAULT,
        SignalOption.IGNORE,
        -> FREE_TERMS
    }
}

/** Run Matter not banked yet: what a signal may spend or stake (banking happens only at the run's end). */
private fun MutableGameState.unbankedMatter(): Long = max(0L, runMatter - bankedRunMatter)

/**
 * The run's price factor `matterGainMultiplier × (priceBase + elapsed / priceRampSeconds)`: Matter prices follow the
 * Rebirth tier's Matter gain and grow through the run.
 */
private fun MutableGameState.priceFactor(tuning: SignalTuning): Float =
    rebirthProfile.matterGainMultiplier * (tuning.priceBase + elapsed / tuning.priceRampSeconds)

/** [base] Matter at the price factor, rounded to the nearest price step and never below one step. */
private fun MutableGameState.matterPrice(tuning: SignalTuning, base: Float, payout: Int, unbanked: Long): SignalTerms {
    val step = tuning.priceStep
    val cost = max(step, ((base * priceFactor(tuning) + step * 0.5f) / step).toInt() * step)
    return SignalTerms(cost, payout, unbanked < cost)
}

/** [share] of maximum integrity, paid from integrity: locked unless more than one integrity would remain. */
private fun MutableGameState.integrityPrice(share: Float): SignalTerms {
    val cost = share * maxHp
    return SignalTerms(cost.roundToInt(), 0, hp <= cost + 1f)
}

/** The Matter wager: a share of the unbanked Matter, never below the minimum stake. */
private fun matterStake(tuning: SignalTuning, unbanked: Long): Int {
    val share = (tuning.gambleMatterShare * unbanked.toFloat()).toLong()
    return max(tuning.gambleMatterMinimum.toLong(), share).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/**
 * The cargo's Matter before the run's Matter gain: the cargo base at the price factor's time ramp. [grantMatter] applies
 * the run's Matter gain, which already holds the Rebirth tier's multiplier.
 */
private fun MutableGameState.cargoMatter(tuning: SignalTuning): Float =
    tuning.derelictCargoMatter * (tuning.priceBase + elapsed / tuning.priceRampSeconds)

/**
 * Resolves card [option] of the open prompt and returns the cue the prompt closes with, or null when nothing closes it
 * here: a card the run cannot take now (the prompt stays open) or a payout that chains an item offer, which takes over
 * the overlay and closes it itself. A resolved card takes the prompt off the queue before it pays.
 */
internal fun MutableGameState.resolveSignalOption(option: ChoiceOption, before: BuildChangeSnapshot): GameplayAudioCue? {
    val chosen = option.signalOption ?: return null
    val prompt = pendingSignalPrompts.firstOrNull() ?: return null
    if (chosen.kind != null && chosen.kind != prompt.kind) return null
    val terms = signalTerms(chosen)
    if (terms.locked) return null
    pendingSignalPrompts = pendingSignalPrompts.subList(1, pendingSignalPrompts.size)
    val tuning = content.variety.signals.tuning
    when (chosen) {
        SignalOption.MERCHANT_RARE_OFFER -> {
            runMatter -= terms.cost
            return chainSignalOffer(TRADE_COMPLETE, ItemRarity.RARE, chosen, before)
        }
        SignalOption.MERCHANT_EPIC_OFFER -> {
            runMatter -= terms.cost
            return chainSignalOffer(TRADE_COMPLETE, ItemRarity.EPIC, chosen, before)
        }
        SignalOption.MERCHANT_REROLLS -> {
            runMatter -= terms.cost
            rerollsRemaining += tuning.merchantRerolls
            signalNotice(TRADE_COMPLETE)
        }
        SignalOption.SHRINE_BLOOD -> {
            hp -= tuning.shrineBloodShare * maxHp
            blessBlood(tuning)
            signalNotice(BLESSING_RECEIVED)
        }
        SignalOption.SHRINE_WARD -> {
            blessWard(tuning)
            signalNotice(BLESSING_RECEIVED)
        }
        SignalOption.SHRINE_RELIC -> {
            hp -= tuning.shrineRelicShare * maxHp
            pendingRelicChoices++
            signalNotice(BLESSING_RECEIVED)
        }
        SignalOption.GAMBLE_REROLL -> {
            val won = gambleUnit(prompt, 0) < tuning.gambleRerollChance
            rerollsRemaining = if (won) rerollsRemaining + tuning.gambleRerollWin else rerollsRemaining - 1
            signalNotice(if (won) WAGER_WON else WAGER_LOST)
        }
        SignalOption.GAMBLE_MATTER -> {
            val won = gambleUnit(prompt, 1) < tuning.gambleMatterChance
            // Raw Matter either way: a wager is not Matter gain.
            runMatter += if (won) (tuning.gambleMatterPayout * terms.cost).roundToInt() - terms.cost.toLong() else -terms.cost.toLong()
            signalNotice(if (won) WAGER_WON else WAGER_LOST)
        }
        SignalOption.GAMBLE_INTEGRITY -> {
            hp -= tuning.gambleIntegrityShare * maxHp
            if (gambleUnit(prompt, 2) < tuning.gambleIntegrityChance) return chainSignalOffer(WAGER_WON, ItemRarity.EPIC, chosen, before)
            signalNotice(WAGER_LOST)
        }
        SignalOption.CACHE_CRATE -> {
            bindSignalCurse(chosen, BuffStat.DAMAGE_TAKEN, tuning.crateCurse, tuning.crateCurseSeconds)
            return chainSignalOffer(CURSE_BOUND, ItemRarity.EPIC, chosen, before)
        }
        SignalOption.CACHE_RELIQUARY -> {
            bindSignalCurse(chosen, BuffStat.DAMAGE, -tuning.reliquaryCurse, tuning.reliquaryCurseSeconds)
            return chainSignalOffer(CURSE_BOUND, ItemRarity.LEGENDARY, chosen, before)
        }
        SignalOption.CACHE_IDOL -> {
            bindSignalCurse(chosen, BuffStat.COOLING, -tuning.idolCurse, tuning.idolCurseSeconds)
            pendingRelicChoices++
            signalNotice(CURSE_BOUND)
        }
        SignalOption.DERELICT_REPAIR -> {
            heal(tuning.derelictRepairShare * maxHp, HealSource.REPAIR)
            signalNotice(SALVAGE_COMPLETE)
        }
        SignalOption.DERELICT_CARGO -> {
            grantMatter(cargoMatter(tuning))
            signalNotice(SALVAGE_COMPLETE)
        }
        SignalOption.DERELICT_VAULT -> {
            addPickup(Pickup(PickupType.KEY, coreX, coreY))
            signalNotice(SALVAGE_COMPLETE)
        }
        SignalOption.IGNORE -> Unit
    }
    return GameplayAudioCue.UI_CLICK
}

/**
 * The deterministic build part of [option] on a reward-preview candidate: the Shrine blessings' stat changes. Gambles,
 * offers, curses and salvage preview nothing.
 */
internal fun MutableGameState.previewSignalOption(option: SignalOption) {
    val tuning = content.variety.signals.tuning
    when (option) {
        SignalOption.SHRINE_BLOOD -> blessBlood(tuning)
        SignalOption.SHRINE_WARD -> blessWard(tuning)
        else -> Unit
    }
}

private fun MutableGameState.blessBlood(tuning: SignalTuning) {
    applyStatDelta(ItemEffect.IMPACT_DAMAGE, tuning.shrineBloodImpact)
    applyStatDelta(ItemEffect.WEAPON_POWER, tuning.shrineBloodWeapon)
}

private fun MutableGameState.blessWard(tuning: SignalTuning) {
    applyStatDelta(ItemEffect.MAX_INTEGRITY, -tuning.shrineWardIntegrity)
    applyStatDelta(ItemEffect.SHIELD_CAPACITY, tuning.shrineWardShield)
    applyStatDelta(ItemEffect.DAMAGE_REDUCTION, tuning.shrineWardReduction)
}

/** A gamble's roll for [prompt]'s signal: a hash of the run seed, the signal's index and the option's [slot]. */
private fun MutableGameState.gambleUnit(prompt: SignalPrompt, slot: Int): Float =
    hashUnit(runHash(runSeed, GAMBLE_SALT, prompt.signalIndex * GAMBLE_KEYS_PER_SIGNAL + slot))

private fun MutableGameState.signalNotice(text: String) {
    message = text
    messageTime = SIGNAL_RESULT_SECONDS
}

/** A paid card that chains an item offer with a rarity [floor]: the notice and build change now, then the offer. */
private fun MutableGameState.chainSignalOffer(
    notice: String,
    floor: ItemRarity,
    option: SignalOption,
    before: BuildChangeSnapshot,
): GameplayAudioCue? {
    signalNotice(notice)
    emitBuildChange(before, option.title)
    emitSound(GameplayAudioCue.UI_CLICK)
    openItemOfferWithFloor(floor)
    return null
}

/**
 * A curse always lands: with every buff slot live, the oldest buff that is not a signal's own makes room first (at most
 * three signal curses exist, one per Cursed cache card).
 */
private fun MutableGameState.bindSignalCurse(option: SignalOption, stat: BuffStat, amount: Float, seconds: Float) {
    var live = 0
    var oldest = -1
    for (index in buffs.indices) {
        val buff = buffs[index]
        if (buff.expiresAt <= elapsed) continue
        live++
        if (oldest < 0 && !isSignalBuff(buff.sourceKey)) oldest = index
    }
    if (live >= MutableGameState.MAX_BUFFS && oldest >= 0) buffs = buffs.filterIndexed { index, _ -> index != oldest }
    addBuff(BuffSource.signal(option), stat, amount, seconds, 1)
}

private fun isSignalBuff(sourceKey: Int): Boolean =
    sourceKey <= BuffSource.signal(SignalOption.entries.first()) && sourceKey >= BuffSource.signal(SignalOption.entries.last())
