// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.WeaponId
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
                PointOfInterestState(
                    offerId, PointOfInterestKind.entries[(offerId + side) % PointOfInterestKind.entries.size],
                    coreX + cos(angle) * 560f, coreY + sin(angle) * 560f,
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
        val integrity = rebirthProfile.enemyHealth(100f * (1f + elapsed / content.tempo.bossAtSeconds))
        val enemy = Enemy(
            id = nextEntityId++, type = EnemyType.WARDEN,
            x = point.x + cos(angle) * 130f, y = point.y + sin(angle) * 130f,
            hp = integrity, maxHp = integrity, radius = 26f, actionTimer = 0.8f + index * 0.4f,
            relicCounters = IntArray(content.relics.size), relicTimers = FloatArray(content.relics.size),
            relicValues = FloatArray(content.relics.size),
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
    repeat(3) { lane ->
        val lateral = (lane - 1) * 60f
        addProjectile(Projectile(point.x - dx * 320f - dy * lateral,
            point.y - dy * 320f + dx * lateral, dx * 460f, dy * 460f,
            radius = 7f, life = 1.5f, hostile = true, damage = 12f))
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
        DirectedReward.WEAPON -> listOf(RewardFocus.MOTION, RewardFocus.CONTROL, RewardFocus.STRIKE)
        DirectedReward.RELIC -> listOf(RewardFocus.VECTOR, RewardFocus.GRAVITIC, RewardFocus.ION,
            RewardFocus.RIFT, RewardFocus.PRISM, RewardFocus.ENTROPY).shuffled(gameplayRandom).take(3)
        DirectedReward.ITEM_AND_REPAIR -> listOf(RewardFocus.OFFENSE, RewardFocus.DEFENSE, RewardFocus.ECONOMY)
    }
    choices = focuses.map { ChoiceOption(activeChoiceType, it.label, "Choose a direction, then one of three matching offers.",
        "FOCUS", rewardFocus = it) }
    dashBufferTime = 0f
    phase = GamePhase.CHOICE
}

internal fun MutableGameState.selectRewardFocus(focus: RewardFocus) {
    val reward = directedReward ?: return
    selectedRewardFocus = focus
    when (reward) {
        DirectedReward.RELIC -> {
            val aspect = RelicAspect.valueOf(focus.name)
            choices = content.relics.filter { it.aspect == aspect && relicRank(it.id) < content.relicPolicy.maxRank }
                .shuffled(gameplayRandom).take(3).map {
                    ChoiceOption(ChoiceType.RELIC, it.name, it.description, it.aspect.displayLabel,
                        relicId = it.id, relicAction = RelicChoiceAction.ACQUIRE)
                }
        }
        DirectedReward.ITEM_AND_REPAIR -> {
            val pool = content.items.filter { itemStacks[it.id] < it.maxStacks && hasUsefulItemEffect(it) && itemRewardFocus(it) == focus }
            choices = pool.shuffled(gameplayRandom).take(3).map {
                ChoiceOption(ChoiceType.ITEM, it.name, compactItemDescription(it), it.rarity.displayLabel, itemId = it.id)
            }
        }
        DirectedReward.WEAPON -> {
            val pool = content.weapons.filter { it.id != weapon && weaponRewardFocus(it.id) == focus }
                .shuffled(gameplayRandom).take(2)
            choices = listOf(ChoiceOption(ChoiceType.TOTEM, "Amplify ${currentWeaponDefinition.name}",
                "Raise current weapon mastery by one level.", "AMPLIFY", weaponId = weapon,
                totemAction = TotemAction.AMPLIFY_CURRENT)) + pool.map {
                ChoiceOption(ChoiceType.WEAPON, it.name, it.description, "CHANGE", weaponId = it.id)
            }
        }
    }
    // The finite catalog can be exhausted; salvage is an honest terminal outcome, never a dead choice.
    if (choices.isEmpty()) {
        grantMatter(8f)
        finishChoice(kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue.LEVEL_UP)
    }
}

internal fun itemRewardFocus(item: ItemDefinition): RewardFocus = when (item.primary.effect) {
    ItemEffect.MAX_INTEGRITY, ItemEffect.REGEN, ItemEffect.SHIELD_CAPACITY, ItemEffect.DAMAGE_REDUCTION,
    ItemEffect.COOLING, ItemEffect.DASH_EFFICIENCY -> RewardFocus.DEFENSE
    ItemEffect.DATA_GAIN, ItemEffect.MATTER_GAIN, ItemEffect.PICKUP_RADIUS, ItemEffect.LUCK -> RewardFocus.ECONOMY
    else -> RewardFocus.OFFENSE
}

internal fun weaponRewardFocus(weapon: WeaponId): RewardFocus = when (weapon) {
    WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR, WeaponId.RIFT_BLADES, WeaponId.PHASE_LATTICE -> RewardFocus.MOTION
    WeaponId.GRAVITY_MINES, WeaponId.ION_SWARM, WeaponId.ENTROPY_FIELD, WeaponId.ARC_COIL -> RewardFocus.CONTROL
    WeaponId.NULL_LANCE, WeaponId.QUASAR_CANNON, WeaponId.SINGULARITY_SPEAR, WeaponId.PRISM_RELAY -> RewardFocus.STRIKE
}
