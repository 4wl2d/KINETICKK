// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.characterization

import kinetickk.ball.content.api.*
import kinetickk.ball.content.api.EnemyType

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class RelicSystemTest {
    @Test
    fun startingANewRunClearsTheRunRelicMatrix() {
        val engine = runningEngine(seed = 200)
        engine.acquireRelicForTesting(RelicId.PERPETUAL_VECTOR)
        engine.acquireRelicForTesting(RelicId.PERPETUAL_VECTOR)
        assertEquals(listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2)), engine.equippedRelics)

        engine.startRun()

        assertTrue(engine.equippedRelics.isEmpty())
        assertEquals(0, engine.relicRank(RelicId.PERPETUAL_VECTOR))
    }

    @Test
    fun relicMatrixStopsAtFourSlotsAndDuplicateRanksStopAtThree() {
        val engine = runningEngine(seed = 201)
        val installed = RelicId.entries.take(engine.content.relicPolicy.maxSlots)
        installed.forEach(engine::acquireRelicForTesting)

        val matrixBeforeOverflow = engine.equippedRelics
        engine.acquireRelicForTesting(RelicId.entries[engine.content.relicPolicy.maxSlots])

        assertEquals(engine.content.relicPolicy.maxSlots, engine.equippedRelics.size)
        assertEquals(matrixBeforeOverflow, engine.equippedRelics)

        val duplicate = installed.first()
        repeat(engine.content.relicPolicy.maxRank + 3) { engine.acquireRelicForTesting(duplicate) }

        assertEquals(engine.content.relicPolicy.maxRank, engine.relicRank(duplicate))
        assertEquals(engine.content.relicPolicy.maxSlots, engine.equippedRelics.size)
        assertEquals(1, engine.equippedRelics.count { it.id == duplicate })
    }

    /** Generic Meld stops at rank 2; rank 3 needs the relic itself offered again. */
    @Test
    fun meldRaisesToRankTwoSalvagesBeyondItAndDuplicatesReachRankThree() {
        val engine = runningEngine(seed = 202)
        val relic = RelicId.PERPETUAL_VECTOR
        val meldCeiling = 2
        engine.acquireRelicForTesting(relic)

        engine.meldRelic(slot = 0)

        assertEquals(meldCeiling, engine.relicRank(relic))
        assertFalse(engine.canMeld(relic))
        val exactRankState = engine.equippedRelics
        val matterBeforeOverflow = engine.runMatter

        engine.meldRelic(slot = 0)

        assertEquals(exactRankState, engine.equippedRelics)
        assertEquals(meldCeiling, engine.relicRank(relic))
        assertEquals(matterBeforeOverflow + 8L, engine.runMatter)

        // The relic itself offered again still reaches the policy rank, never beyond it.
        engine.acquireRelicForTesting(relic)
        assertEquals(engine.content.relicPolicy.maxRank, engine.relicRank(relic))
        engine.acquireRelicForTesting(relic)
        assertEquals(engine.content.relicPolicy.maxRank, engine.relicRank(relic))
    }

    @Test
    fun fullMatrixMeldRequiresAnExplicitTargetAndOnlyRanksThatSlot() {
        val engine = fullRelicEngine(seed = 202)
        val before = engine.equippedRelics

        engine.openRelicChoiceForTesting()
        val meldIndex = engine.choices.indexOfFirst { it.relicAction == RelicChoiceAction.MELD }
        assertTrue(meldIndex >= 0, "A full matrix did not offer explicit melding: ${engine.choices}")
        engine.choose(meldIndex)

        assertEquals(ChoiceType.RELIC_BIND, engine.choiceType)
        assertEquals(
            (0 until engine.content.relicPolicy.maxSlots).toList(),
            engine.choices.mapNotNull(ChoiceOption::relicSlot),
        )
        assertTrue(engine.choices.all { it.relicAction == RelicChoiceAction.MELD_TARGET })

        val targetSlot = 2
        engine.choose(engine.choices.indexOfFirst { it.relicSlot == targetSlot })

        val expected = before.toMutableList().apply {
            this[targetSlot] = this[targetSlot].copy(rank = this[targetSlot].rank + 1)
        }
        assertEquals(expected, engine.equippedRelics)
        assertEquals(GamePhase.RUNNING, engine.phase)
    }

    @Test
    fun fullMatrixReplacementRequiresAnExplicitTargetAndOnlyReplacesThatSlot() {
        val engine = fullRelicEngine(seed = 203)
        val before = engine.equippedRelics

        engine.openRelicChoiceForTesting()
        val incomingIndex = engine.choices.indexOfFirst { option ->
            option.relicAction == RelicChoiceAction.ACQUIRE &&
                option.relicId != null &&
                option.relicId !in before.map(EquippedRelic::id)
        }
        assertTrue(incomingIndex >= 0, "The deterministic offering did not contain a new Relic: ${engine.choices}")
        val incoming = requireNotNull(engine.choices[incomingIndex].relicId)
        engine.choose(incomingIndex)

        assertEquals(ChoiceType.RELIC_BIND, engine.choiceType)
        assertEquals(
            (0 until engine.content.relicPolicy.maxSlots).toList(),
            engine.choices.mapNotNull(ChoiceOption::relicSlot),
        )
        assertTrue(engine.choices.all { it.relicAction == RelicChoiceAction.REPLACE })

        val targetSlot = 1
        engine.choose(engine.choices.indexOfFirst { it.relicSlot == targetSlot })

        val expected = before.toMutableList().apply { this[targetSlot] = EquippedRelic(incoming, 1) }
        assertEquals(expected, engine.equippedRelics)
        assertEquals(GamePhase.RUNNING, engine.phase)
    }

    @Test
    fun relicRanksRemainBoundWhenTheRunWeaponChanges() {
        val engine = runningEngine(seed = 204)
        repeat(3) { engine.acquireRelicForTesting(RelicId.GYRE_ENGINE) }
        repeat(2) { engine.acquireRelicForTesting(RelicId.ECHO_CHAMBER) }
        val beforeSwap = engine.equippedRelics

        WeaponId.entries.drop(1).forEach { weapon ->
            engine.equipWeaponForTesting(weapon)
            assertEquals(beforeSwap, engine.equippedRelics, "Relics changed while equipping $weapon")
            assertEquals(3, engine.relicRank(RelicId.GYRE_ENGINE))
            assertEquals(2, engine.relicRank(RelicId.ECHO_CHAMBER))
        }
    }

    @Test
    fun eliteDeathDropsOneRelicResonanceAlongsideItsKey() {
        val engine = runningEngine(seed = 205)
        engine.enemies.clear()
        engine.pickups.clear()

        engine.killEnemyForTesting(EnemyType.ELITE)

        assertEquals(1, engine.kills)
        assertEquals(1, engine.pickups.count { it.type == PickupType.KEY })
        assertEquals(1, engine.pickups.count { it.type == PickupType.RELIC })
    }

    @Test
    fun lethalCoreImpactQualifiesForOnDeathRelics() {
        val engine = runningEngine(seed = 211)
        engine.enemies.clear()
        engine.acquireRelicForTesting(RelicId.GLASS_COMET)
        engine.updatePointer(1_280f, 360f)
        engine.addEnemyForTesting(x = 100f, y = 0f, hp = 1f, radius = 12f)
        engine.setVelocityForTesting(24_000f, 0f)

        engine.update(GameScenario.FIXED_STEP)

        assertEquals(1, engine.kills)
        // A fast impact kill is the Glass Comet's invulnerability edge, and it starts its cooldown.
        assertTrue(engine.invulnerableUntil > engine.elapsed)
        assertEquals(engine.elapsed + GLASS_INVULNERABILITY_COOLDOWN, engine.glassInvulnerabilityReadyAt, 0.0001f)
    }

    @Test
    fun collectingARelicThenQueuingAnItemChoiceDrainsBothRewards() {
        val engine = runningEngine(seed = 206)
        engine.enemies.clear()
        engine.dropRelicForTesting()

        engine.update(GameScenario.FIXED_STEP)

        assertEquals(GamePhase.CHOICE, engine.phase)
        assertEquals(ChoiceType.RELIC, engine.choiceType)
        assertEquals(0, engine.pendingRelicChoiceCount)
        assertTrue(engine.pickups.none { it.type == PickupType.RELIC })

        engine.grantDataForTesting(18f)
        assertEquals(ChoiceType.RELIC, engine.choiceType, "The active Relic reward was overwritten")

        chooseFirstCompletingOption(engine)
        // The level's item offer follows the Relic reward directly (no skill draft opens before it).
        assertEquals(ChoiceType.ITEM, engine.choiceType)
        chooseFirstCompletingOption(engine)

        assertEquals(GamePhase.RUNNING, engine.phase)
        assertEquals(1, engine.equippedRelics.size)
        assertEquals(1, engine.acquiredItemCount)
        assertTrue(engine.choices.isEmpty())
    }

    @Test
    fun queuedRelicRewardWaitsForAndThenFollowsAllPendingItemChoices() {
        val engine = runningEngine(seed = 207)
        engine.enemies.clear()
        engine.grantDataForTesting(100f)
        // The first queued item offer opens at once.
        assertEquals(ChoiceType.ITEM, engine.choiceType)

        engine.openRelicChoiceForTesting()
        assertEquals(ChoiceType.ITEM, engine.choiceType, "The active item offer was overwritten")
        assertEquals(1, engine.pendingRelicChoiceCount)

        val seen = mutableListOf<ChoiceType>()
        repeat(8) {
            if (engine.phase != GamePhase.CHOICE) return@repeat
            seen += engine.choiceType
            chooseFirstCompletingOption(engine)
        }

        assertEquals(GamePhase.RUNNING, engine.phase, "Queued choices were stranded: $seen")
        assertEquals(ChoiceType.ITEM, seen.first())
        assertEquals(3, seen.count { it == ChoiceType.ITEM })
        assertEquals(1, seen.count { it == ChoiceType.RELIC })
        assertEquals(3, engine.acquiredItemCount)
        assertEquals(1, engine.equippedRelics.size)
        assertEquals(0, engine.pendingRelicChoiceCount)
        assertTrue(engine.choices.isEmpty())
    }

    @Test
    fun everyRelicCanBeEquippedAndKeepsTheSimulationFinite() {
        RelicId.entries.forEach { relicId ->
            val engine = runningEngine(seed = 300 + relicId.ordinal)
            engine.enemies.clear()
            engine.projectiles.clear()
            engine.equipWeaponForTesting(WeaponId.entries[relicId.ordinal % WeaponId.entries.size])
            engine.acquireRelicForTesting(relicId)
            engine.updatePointer(1_280f, 360f)
            engine.setVelocityForTesting(760f, 90f)
            engine.addEnemyForTesting(110f, 0f, hp = 100_000f)
            engine.addEnemyForTesting(230f, 35f, hp = 100_000f)
            engine.addEnemyForTesting(360f, -45f, hp = 100_000f)
            engine.requestDash()

            repeat(90) { engine.update(GameScenario.FIXED_STEP) }

            assertEquals(
                listOf(EquippedRelic(relicId, 1)),
                engine.equippedRelics,
                "$relicId was not equipped",
            )
            assertFiniteState(engine, relicId)
        }
    }

    @Test
    fun continuousWeaponContactsAreRateLimitedAndRelicDamageDoesNotRecurse() {
        val engine = runningEngine(seed = 360)
        engine.enemies.clear()
        engine.equipWeaponForTesting(WeaponId.GRAVITY_MINES)
        engine.acquireRelicForTesting(RelicId.ECHO_CHAMBER)
        val primary = engine.addEnemyForTesting(90f, 0f, hp = 100_000f)

        repeat(100) {
            val damage = engine.triggerWeaponContactForTesting(primary, continuous = true)
            assertTrue(damage.isFinite() && damage > 0f)
        }

        assertEquals(
            1,
            engine.echoHitCountForTesting(),
            "Continuous contacts queued more than one Echo Chamber hit inside a qualification window",
        )

        // The echo lands once and never queues an echo of itself.
        val hpBeforeEcho = primary.hp
        repeat(60) { engine.updateRelicRuntime(GameScenario.FIXED_STEP) }
        assertTrue(primary.hp < hpBeforeEcho, "The queued echo never landed")
        assertEquals(0, engine.echoHitCountForTesting())
        assertTrue(primary.hp.isFinite())
    }

    @Test
    fun agonyScepterCreatesAWeaponSpecificRuntimeMutationForEveryWeapon() {
        // Weapons whose behavior has not landed carry no Forbidden form yet: they must not mutate at all.
        PendingWeaponBehaviors.forEach { weapon ->
            val idle = exerciseAgonyMutation(weapon, equipScepter = true)
            assertTrue(idle.all { it == 0 }, "$weapon now has an Agony form: drop it from PendingWeaponBehaviors")
        }
        val implemented = WeaponId.entries.filterNot { it in PendingWeaponBehaviors }
        val signatures = implemented.associateWith { weapon ->
            val baseline = exerciseAgonyMutation(weapon, equipScepter = false)
            assertTrue(baseline.all { it == 0 }, "$weapon reported an Agony mutation without Agony Scepter: $baseline")

            val augmented = exerciseAgonyMutation(weapon, equipScepter = true)
            assertTrue(
                augmented[weapon.ordinal] > 0,
                "Agony Scepter did not mutate $weapon at runtime: $augmented",
            )
            assertEquals(
                1,
                augmented.count { it > 0 },
                "$weapon activated another weapon's Agony mutation path: $augmented",
            )
            augmented.map { it > 0 }
        }

        assertEquals(
            implemented.size,
            signatures.values.toSet().size,
            "Agony Scepter mutation signatures were not weapon-specific: $signatures",
        )
    }

    private fun chooseFirstCompletingOption(engine: GameScenario) {
        val index = engine.choices.indexOfFirst { option ->
            option.type != ChoiceType.RELIC || option.relicAction == RelicChoiceAction.ACQUIRE
        }
        assertTrue(index >= 0, "No completing option was available for ${engine.choiceType}: ${engine.choices}")
        engine.choose(index)
        if (engine.choiceType == ChoiceType.RELIC_BIND && engine.phase == GamePhase.CHOICE) {
            engine.choose(0)
        }
    }

    private fun assertFiniteState(engine: GameScenario, relicId: RelicId) {
        val engineValues = listOf(
            engine.coreX,
            engine.coreY,
            engine.velocityX,
            engine.velocityY,
            engine.hp,
            engine.maxHp,
            engine.heat,
            engine.mass,
            engine.damageMultiplier,
            engine.weaponPower,
            engine.attackSpeed,
            engine.overdriveCharge,
        )
        assertTrue(engineValues.all(Float::isFinite), "$relicId produced non-finite engine state: $engineValues")
        engine.enemies.forEach { enemy ->
            val values = listOf(enemy.x, enemy.y, enemy.vx, enemy.vy, enemy.hp, enemy.flash)
            assertTrue(values.all(Float::isFinite), "$relicId produced non-finite enemy state: $values")
            assertTrue((0 until StatusCell.COUNT).all { enemy.status[it].isFinite() }, "$relicId produced a non-finite status cell")
        }
        engine.projectiles.forEach { projectile ->
            val values = listOf(projectile.x, projectile.y, projectile.vx, projectile.vy, projectile.life, projectile.damage)
            assertTrue(values.all(Float::isFinite), "$relicId produced non-finite projectile state: $values")
        }
    }

    private fun exerciseAgonyMutation(weapon: WeaponId, equipScepter: Boolean): List<Int> {
        val engine = runningEngine(seed = 400 + weapon.ordinal + if (equipScepter) 100 else 0)
        engine.enemies.clear()
        engine.projectiles.clear()
        engine.equipWeaponForTesting(weapon)
        if (equipScepter) engine.acquireRelicForTesting(RelicId.AGONY_SCEPTER)
        engine.updatePointer(1_280f, 360f)
        engine.setVelocityForTesting(760f, 0f)
        engine.addEnemyForTesting(180f, 0f, hp = 100_000f)
        engine.addEnemyForTesting(280f, 40f, hp = 100_000f)

        engine.update(GameScenario.FIXED_STEP)

        assertEquals(
            engine.agonyMutationCountsForTesting()[weapon.ordinal],
            engine.agonyMutationCountForTesting(weapon),
        )
        return engine.agonyMutationCountsForTesting()
    }

    private fun runningEngine(seed: Int): GameScenario = gameScenario(seed = seed, initialMatter = 0).apply {
        resize(1_280f, 720f)
        startRun()
    }

    private fun fullRelicEngine(seed: Int): GameScenario = runningEngine(seed).apply {
        RelicId.entries.take(content.relicPolicy.maxSlots).forEach(::acquireRelicForTesting)
    }
}
