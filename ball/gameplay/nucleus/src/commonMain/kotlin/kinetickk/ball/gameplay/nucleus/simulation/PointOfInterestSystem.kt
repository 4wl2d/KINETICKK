// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.*

internal fun MutableGameState.resetPointsOfInterest() {
    pointsOfInterest = emptyList()
    nextPointOfferIndex = 0
    pendingDirectedRewards = emptyList()
    directedReward = null
    selectedRewardFocus = null
}

internal fun MutableGameState.updatePointsOfInterest(delta: Float) {
    if (phase != GamePhase.RUNNING) return
    val policy = content.pointsOfInterest
    val bossAt = content.tempo.bossAtSeconds
    if (bossSpawned || elapsed >= bossAt) {
        pointsOfInterest = emptyList()
        return
    }
    // A missed scheduled window is consumed; leaving a point never rolls another offer.
    val scheduledAt = policy.firstOfferAt + nextPointOfferIndex * policy.offerInterval
    if (scheduledAt <= policy.lastOfferAt && elapsed >= scheduledAt) {
        val offerId = nextPointOfferIndex++
        if (pointsOfInterest.isEmpty() && elapsed < scheduledAt + policy.offerLifetime &&
            scheduledAt + policy.offerLifetime + policy.trialDuration < bossAt
        ) {
            val heading = if (speed >= 80f) atan2(velocityY, velocityX) else atan2(lastAimDirectionY, lastAimDirectionX)
            pointsOfInterest = List(2) { side ->
                val angle = heading + if (side == 0) -1.0f else 1.0f
                // Anomalies move out from under obstacles, so their ring and beacons stay reachable.
                val (pointX, pointY) = clearPointOfInterestSite(coreX + cos(angle) * 560f, coreY + sin(angle) * 560f)
                PointOfInterestState(
                    offerId, PointOfInterestKind.entries[(offerId + side) % PointOfInterestKind.entries.size],
                    pointX, pointY,
                    scheduledAt + policy.offerLifetime,
                )
            }
            message = "TWO ANOMALIES DETECTED // CHOOSE A COURSE"
            messageTime = 2.5f
        }
    }
    val active = pointsOfInterest.firstOrNull { it.active }
    if (active == null) {
        pointsOfInterest = pointsOfInterest.filter { elapsed < it.expiresAt }
        val selected = pointsOfInterest.firstOrNull {
            segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, it.x, it.y, 65f)
        } ?: return
        activatePointOfInterest(selected)
        return
    }
    var trial = active.copy(remaining = max(0f, active.remaining - delta))
    if (trial.remaining <= 0f || distanceSquared(coreX, coreY, trial.x, trial.y) > square(2_400f)) {
        pointsOfInterest = emptyList()
        message = "ANOMALY LOST"
        messageTime = 1.6f
        return
    }
    var completed = false
    when (trial.kind) {
        PointOfInterestKind.RESONANT_CIRCUIT -> {
            val beacon = trial.beacon(trial.nextBeacon)
            if (segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, beacon.x, beacon.y, 55f)) {
                trial = trial.copy(nextBeacon = trial.nextBeacon + 1)
                completed = trial.nextBeacon == 4
            }
        }
        PointOfInterestKind.SEALED_ANOMALY -> {
            completed = trial.defeatedDefenders == 3
            // Defenders remain around their vault, rather than following a fleeing player forever.
            for (enemy in enemies) if (enemy.id in trial.defenderIds && !enemy.dead) {
                val dx = enemy.x - trial.x
                val dy = enemy.y - trial.y
                val distance = length(dx, dy)
                if (distance > 290f) {
                    enemy.x = trial.x + dx / distance * 290f
                    enemy.y = trial.y + dy / distance * 290f
                    enemy.previousX = enemy.x
                    enemy.previousY = enemy.y
                }
            }
        }
        PointOfInterestKind.COLLAPSING_ORBIT -> {
            val distance = length(coreX - trial.x, coreY - trial.y)
            val moved = length(coreX - previousCoreX, coreY - previousCoreY)
            val earned = if (distance in 120f..190f && moved >= 80f * delta) delta else 0f
            trial = trial.copy(orbitSeconds = min(policy.orbitRequiredSeconds, trial.orbitSeconds + earned))
            completed = trial.orbitSeconds >= policy.orbitRequiredSeconds
            if (trial.warningRemaining > 0f) {
                val warning = max(0f, trial.warningRemaining - delta)
                trial = trial.copy(warningRemaining = warning)
                if (warning <= 0f) fireOrbitVolley(trial)
            } else {
                trial = trial.copy(volleyClock = trial.volleyClock - delta)
                if (trial.volleyClock <= 0f) {
                    trial = trial.copy(volleyClock = 2.2f, warningRemaining = 0.9f,
                        volleyAngle = atan2(coreY - trial.y, coreX - trial.x))
                }
            }
        }
    }
    if (completed) {
        check(pendingDirectedRewards.size < 6) { "At most six scheduled POI rewards per run" }
        pendingDirectedRewards = pendingDirectedRewards + policy.definition(trial.kind).reward
        if (trial.kind == PointOfInterestKind.COLLAPSING_ORBIT) pendingCompletedOrbits++
        pointsOfInterest = emptyList() // consume before any choice can open
        message = "ANOMALY RESOLVED"
        messageTime = 2f
    } else pointsOfInterest = listOf(trial)
}

internal fun MutableGameState.activatePointOfInterest(point: PointOfInterestState) {
    if (point.kind == PointOfInterestKind.SEALED_ANOMALY && enemies.size + 3 > content.rebirth.maxActiveEnemies) return
    val ids = mutableListOf<Int>()
    if (point.kind == PointOfInterestKind.SEALED_ANOMALY) repeat(3) { index ->
        val angle = TAU * index / 3f
        val integrity = spawnIntegrity(EnemyType.WARDEN) * POINT_DEFENDER_HEALTH_SHARE
        val enemy = Enemy(
            id = nextEntityId++, type = EnemyType.WARDEN,
            x = point.x + cos(angle) * 130f, y = point.y + sin(angle) * 130f,
            hp = integrity, maxHp = integrity, radius = 26f, actionTimer = 0.8f + index * 0.4f,
        )
        enemies += enemy
        ids += enemy.id
    }
    pointsOfInterest = listOf(point.copy(active = true, remaining = content.pointsOfInterest.trialDuration,
        defenderIds = ids.toImmutableList()))
    message = content.pointsOfInterest.definition(point.kind).instruction
    messageTime = 4f
}

internal fun MutableGameState.recordPointDefenderKilled(enemy: Enemy) {
    val trial = pointsOfInterest.singleOrNull()?.takeIf { it.active && enemy.id in it.defenderIds } ?: return
    // Called once by onEnemyKilled, whose dead guard also protects this counter.
    pointsOfInterest = listOf(trial.copy(defeatedDefenders = min(3, trial.defeatedDefenders + 1)))
}

private fun MutableGameState.fireOrbitVolley(point: PointOfInterestState) {
    val dx = cos(point.volleyAngle)
    val dy = sin(point.volleyAngle)
    val damage = hostileDamage(null, 12f)
    repeat(3) { lane ->
        val lateral = (lane - 1) * 60f
        addProjectile(Projectile(point.x - dx * 320f - dy * lateral,
            point.y - dy * 320f + dx * lateral, dx * 460f, dy * 460f,
            radius = 7f, life = 1.5f, hostile = true, damage = damage))
    }
}

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
    dashBufferTime = 0f
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
            val pool = content.items.filter {
                it.minimumRebirth <= rebirthLevel && hasUsefulItemEffect(it) && itemRewardFocus(it) == focus
            }
            choices = pool.shuffled(rewardRandom).take(3).map {
                ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel, itemId = it.id,
                    reborn = rewardRandom.nextFloat() < rebornChance())
            }
        }
        DirectedReward.WEAPON -> {
            choices = directedWeaponChoices(
                weaponOfferCandidates().filter { weaponRewardFocus(it) == focus }.shuffled(rewardRandom).take(2),
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
