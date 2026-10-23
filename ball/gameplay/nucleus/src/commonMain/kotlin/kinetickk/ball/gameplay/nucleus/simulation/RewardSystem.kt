// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation


import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


internal fun MutableGameState.onEnemyKilled(enemy: Enemy) {
    if (enemy.dead) return
    // The Architect's fall ends the run before its kill rewards resolve, so Data from a kill clause
    // that completes a level cannot open a choice in place of the victory.
    val victory = enemy.type == EnemyType.ARCHITECT && phase == GamePhase.RUNNING
    if (victory) phase = GamePhase.VICTORY
    recordPointDefenderKilled(enemy)
    // The Herald pays out like an Elite: a Key, a Relic and an Elite kill.
    val eliteReward = enemy.isElite || enemy.isMiniBoss
    if (eliteReward) pendingEliteKills++
    enemy.dead = true
    kills++
    pendingTotalKills++
    // Elite Bounty queues one more item offer for every Elite reward (the Herald included); it opens
    // with the next pending choice.
    if (eliteReward && matrix.eliteBounty) pendingLevelChoices++
    combo++
    bestCombo = maxOf(bestCombo, combo)
    if (eliteReward) eliteKills++
    comboTime = effectiveComboWindow()
    onBuildKill(enemy)
    onCharacterKill(enemy, eliteReward)
    val profile = enemy.type.profile
    val comboMultiplier = 1f + min(combo, 50) * 0.01f + velocityTier * 0.15f
    grantMatter(profile.matterReward * comboMultiplier * content.tempo.matterRewardMultiplier)
    val dataCount = profile.dataDrops
    gainOverdrive(dataCount * 1.8f)
    // Erasure: an executed victim's Data rushes to the Core (the scatter draws stay the same).
    val latched = erasureLatchId == enemy.id && latchedVacuumActive()
    if (erasureLatchId == enemy.id) erasureLatchId = -1
    repeat(dataCount) {
        val angle = gameplayRandom.nextFloat() * TAU
        val pickupSpeed = 28f + gameplayRandom.nextFloat() * 70f
        addPickup(Pickup(PickupType.DATA, enemy.x, enemy.y, cos(angle) * pickupSpeed, sin(angle) * pickupSpeed, attracted = latched))
    }
    if (eliteReward) {
        addPickup(Pickup(PickupType.KEY, enemy.x, enemy.y))
        addPickup(Pickup(PickupType.RELIC, enemy.x + 15f, enemy.y - 8f))
    }
    if (enemy.type != EnemyType.ARCHITECT && gameplayRandom.nextFloat() < 0.035f + effectiveLuck() * 0.02f) {
        addPickup(Pickup(PickupType.REPAIR, enemy.x, enemy.y))
    }
    // A Splitter crushed by Mass or a fresh Collapse leaves no fragments.
    if (enemy.type == EnemyType.SPLITTER && !splitterCrushed(enemy)) spawnSplitterFragments(enemy)
    queueVolatileRemains(enemy)
    shockwave(
        enemy.x,
        enemy.y,
        if (enemy.type == EnemyType.ARCHITECT || enemy.isMiniBoss) 0.8f else 0.3f,
        enemy.radius * if (enemy.type == EnemyType.ARCHITECT) 4.5f else 2.7f,
        if (eliteReward) 3 else 1,
    )
    burst(enemy.x, enemy.y, if (enemy.type == EnemyType.ARCHITECT) 60 else if (enemy.isMiniBoss) 40 else 12, if (eliteReward) 3 else 1)
    emitSound(GameplayAudioCue.ENEMY_DESTROYED)
    if (victory) {
        message = "ARCHITECT DISMANTLED"
        messageTime = 10f
        pendingClearedRebirthLevel = rebirthLevel
        pendingArchitectDefeatedWith = coreShape
        pendingVictoryRelics = equippedRelics.map { it.id }.distinct().take(MAX_HEIRLOOM_RELICS)
        bankRunMatter()
        emitSound(GameplayAudioCue.VICTORY)
    }
}

internal fun MutableGameState.spawnSplitterFragments(enemy: Enemy) {
    val difficulty = content.tempo.enemyHealthMultiplier(elapsed)
    val fragmentLimit = rebirthProfile.enemyCap(90)
    val fragmentCount = min(2, max(0, fragmentLimit - enemies.count { !it.dead }))
    repeat(fragmentCount) { index ->
        if (enemies.size >= content.rebirth.maxActiveEnemies) return@repeat
        val angle = gameplayRandom.nextFloat() * TAU + index * TAU * 0.5f
        val fragmentHp = rebirthProfile.enemyHealth(SPLITTER_FRAGMENT_HEALTH * difficulty) * matrix.enemyIntegrityMultiplier
        val fragmentSpeed = 175f * rebirthProfile.enemySpeedMultiplier
        enemies += Enemy(
            id = nextEntityId++,
            type = EnemyType.DRIFTER,
            x = enemy.x + cos(angle) * 13f,
            y = enemy.y + sin(angle) * 13f,
            vx = cos(angle) * fragmentSpeed,
            vy = sin(angle) * fragmentSpeed,
            hp = fragmentHp,
            maxHp = fragmentHp,
            radius = SPLITTER_FRAGMENT_RADIUS,
            actionTimer = 0.2f,
        )
    }
}

private fun MutableGameState.splitterCrushed(enemy: Enemy): Boolean =
    stacks(enemy, StatusElement.GRAVITIC) >= 2 ||
        enemy.status[StatusCell.REARM_AT + StatusElement.GRAVITIC.ordinal] > elapsed
