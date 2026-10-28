// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.characterization

import kinetickk.ball.content.api.*
import kinetickk.ball.content.api.EnemyType

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class TotemEnemySystemsTest {
    @Test
    fun recalibrationDealsOnlyOwnedWeaponsAndALoneStarterOnlyAmplifies() {
        val owned = listOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR, WeaponId.GYRE_DISC)
        val engine = runningEngine(seed = 202, owned = owned)
        openTotemChoice(engine)
        engine.choose(engine.choices.indexOfFirst { it.totemAction == TotemAction.CHANGE_WEAPON })
        assertEquals(ChoiceType.WEAPON, engine.choiceType)
        assertEquals(setOf(WeaponId.MORNINGSTAR, WeaponId.GYRE_DISC), engine.choices.mapNotNull(ChoiceOption::weaponId).toSet())
        // Both candidates are already on the table: a reroll could only deal them again, so it is neither offered nor spent.
        val rerolls = engine.rerollsRemaining
        assertFalse(engine.choicesCanReroll)
        engine.rerollChoices()
        assertEquals(rerolls, engine.rerollsRemaining)

        // A fresh profile owns only the starter: the Totem can amplify it, never recalibrate into nothing.
        val starter = runningEngine(seed = 203, owned = emptyList())
        assertEquals(setOf(WeaponId.FLUX_WAKE), starter.unlockedWeapons)
        starter.activateTotemForTesting()
        starter.update(GameScenario.FIXED_STEP)
        assertEquals(ChoiceType.TOTEM, starter.choiceType)
        assertEquals(listOf(TotemAction.AMPLIFY_CURRENT), starter.choices.map { it.totemAction })
    }

    @Test
    fun aWeaponRerollNeedsMoreOwnedCandidatesThanOneOffer() {
        fun recalibrated(owned: List<WeaponId>): GameScenario = runningEngine(seed = 204, owned = owned).also { engine ->
            openTotemChoice(engine)
            engine.choose(engine.choices.indexOfFirst { it.totemAction == TotemAction.CHANGE_WEAPON })
            assertEquals(ChoiceType.WEAPON, engine.choiceType)
        }
        val threeOthers = listOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR, WeaponId.GYRE_DISC, WeaponId.ARC_COIL)

        // Exactly one offer's worth of owned weapons: the reroll is not offered and costs nothing.
        val exact = recalibrated(threeOthers)
        assertEquals(3, exact.choices.size)
        val choices = exact.choices
        val rerolls = exact.rerollsRemaining
        assertTrue(rerolls > 0)
        assertFalse(exact.choicesCanReroll)
        exact.rerollChoices()
        assertEquals(rerolls, exact.rerollsRemaining)
        assertEquals(choices, exact.choices)

        // One more owned weapon makes a different hand possible, so the reroll is offered and spent.
        val wider = recalibrated(threeOthers + WeaponId.NULL_LANCE)
        assertTrue(wider.choicesCanReroll)
        val before = wider.rerollsRemaining
        wider.rerollChoices()
        assertEquals(before - 1, wider.rerollsRemaining)
    }

    @Test
    fun totemAmplifyAndChangeFormATwoStageChoiceThatPreservesWeaponLevel() {
        val engine = runningEngine(seed = 201)
        val startingWeapon = engine.weapon
        // The Orb starts its signature Flux Wake one level up.
        val amplified = engine.weaponLevel + 1

        openTotemChoice(engine)

        assertEquals(0, engine.keys)
        assertNull(engine.totem)
        assertEquals(ChoiceType.TOTEM, engine.choiceType)
        assertEquals(
            setOf(TotemAction.AMPLIFY_CURRENT, TotemAction.CHANGE_WEAPON),
            engine.choices.mapNotNull(ChoiceOption::totemAction).toSet(),
        )

        val rerollsBeforeTotemChoice = engine.rerollsRemaining
        val totemChoicesBeforeReroll = engine.choices
        assertFalse(engine.choicesCanReroll)

        engine.rerollChoices()

        assertEquals(rerollsBeforeTotemChoice, engine.rerollsRemaining)
        assertEquals(totemChoicesBeforeReroll, engine.choices)

        val amplifyIndex = engine.choices.indexOfFirst {
            it.totemAction == TotemAction.AMPLIFY_CURRENT
        }
        engine.choose(amplifyIndex)

        assertEquals(GamePhase.RUNNING, engine.phase)
        assertEquals(startingWeapon, engine.weapon)
        assertEquals(amplified, engine.weaponLevel)
        assertTrue(engine.choices.isEmpty())

        openTotemChoice(engine)
        val changeIndex = engine.choices.indexOfFirst {
            it.totemAction == TotemAction.CHANGE_WEAPON
        }
        engine.choose(changeIndex)

        assertEquals(GamePhase.CHOICE, engine.phase)
        assertEquals(ChoiceType.WEAPON, engine.choiceType)
        assertEquals(startingWeapon, engine.weapon)
        assertEquals(amplified, engine.weaponLevel)
        assertEquals(3, engine.choices.size)
        assertEquals(3, engine.choices.mapNotNull(ChoiceOption::weaponId).toSet().size)
        assertTrue(engine.choices.all { it.weaponId != startingWeapon })
        assertTrue(engine.choicesCanReroll)

        val rerollsBeforeWeaponChoice = engine.rerollsRemaining
        engine.rerollChoices()
        assertEquals(rerollsBeforeWeaponChoice - 1, engine.rerollsRemaining)

        val selectedWeapon = requireNotNull(engine.choices.first().weaponId)
        engine.choose(0)

        assertEquals(GamePhase.RUNNING, engine.phase)
        assertEquals(selectedWeapon, engine.weapon)
        assertNotEquals(startingWeapon, engine.weapon)
        assertEquals(amplified, engine.weaponLevel)
        assertTrue(engine.choices.isEmpty())
    }

    @Test
    fun enemyKillsDoNotAdvanceWeaponLevelWithoutATotem() {
        val engine = runningEngine(seed = 202)
        engine.equipWeaponForTesting(WeaponId.ENTROPY_FIELD)
        val weaponLevelBeforeKills = engine.weaponLevel

        repeat(24) { index ->
            engine.addEnemyForTesting(
                x = 105f,
                y = (index - 12) * 4f,
                hp = 0.01f,
                radius = 10f,
            )
        }

        engine.update(GameScenario.FIXED_STEP)

        assertEquals(24, engine.kills)
        assertEquals(weaponLevelBeforeKills, engine.weaponLevel)
    }

    @Test
    fun interceptorSteersTowardTheCorePredictedTravelPath() {
        val engine = runningEngine(seed = 203)
        engine.setVelocityForTesting(800f, 0f)
        val interceptor = engine.addEnemyForTesting(
            x = 0f,
            y = -300f,
            type = EnemyType.INTERCEPTOR,
        )

        engine.update(GameScenario.FIXED_STEP)

        assertTrue(interceptor.vx > 100f, "Interceptor did not lead the Core's horizontal motion: ${interceptor.vx}")
        assertTrue(interceptor.vy > 0f, "Interceptor must still close the vertical gap: ${interceptor.vy}")
        assertTrue(interceptor.vx > interceptor.vy * 0.8f, "Interceptor steered at the current position instead of the predicted path")
    }

    @Test
    fun weaverFiresAParallelProjectileWall() {
        val engine = runningEngine(seed = 204)
        engine.addEnemyForTesting(
            x = 400f,
            y = 0f,
            type = EnemyType.WEAVER,
        )

        engine.update(GameScenario.FIXED_STEP)

        val wall = engine.projectiles.filter(Projectile::hostile)
        assertEquals(3, wall.size)
        val reference = wall.first()
        wall.forEach { projectile ->
            assertEquals(reference.vx, projectile.vx, 0.001f)
            assertEquals(reference.vy, projectile.vy, 0.001f)
            assertTrue(projectile.vx < -240f, "Weaver wall must travel toward the Core")
        }
        val orderedStarts = wall.map(Projectile::previousY).sorted()
        assertEquals(34f, orderedStarts[1] - orderedStarts[0], 0.01f)
        assertEquals(34f, orderedStarts[2] - orderedStarts[1], 0.01f)
    }

    @Test
    fun wardenPullsAStationaryCoreInsideItsGravityRadius() {
        val engine = runningEngine(seed = 205)
        engine.addEnemyForTesting(
            x = 300f,
            y = 0f,
            type = EnemyType.WARDEN,
        )

        assertEquals(0f, engine.velocityX)
        engine.update(GameScenario.FIXED_STEP)

        assertTrue(engine.velocityX > 0.8f, "Warden did not pull the Core toward itself: ${engine.velocityX}")
        assertTrue(abs(engine.velocityY) < 0.01f)
    }

    @Test
    fun destroyedSplitterCreatesTwoSmallOutwardMovingFragments() {
        val engine = runningEngine(seed = 206)
        engine.equipWeaponForTesting(WeaponId.ENTROPY_FIELD)
        val splitter = engine.addEnemyForTesting(
            x = 120f,
            y = 0f,
            hp = 0.01f,
            radius = 27f,
            type = EnemyType.SPLITTER,
        )

        engine.update(GameScenario.FIXED_STEP)

        assertEquals(1, engine.kills)
        assertTrue(splitter !in engine.enemies)
        assertEquals(2, engine.enemies.size)
        engine.enemies.forEach { fragment ->
            assertEquals(EnemyType.DRIFTER, fragment.type)
            assertEquals(12f, fragment.radius)
            val offsetX = fragment.x - splitter.x
            val offsetY = fragment.y - splitter.y
            val speed = sqrt(fragment.vx * fragment.vx + fragment.vy * fragment.vy)
            assertEquals(13f, sqrt(offsetX * offsetX + offsetY * offsetY), 0.01f)
            assertEquals(175f, speed, 0.01f)
            assertTrue(offsetX * fragment.vx + offsetY * fragment.vy > 0f)
        }
    }

    @Test
    fun splitterFragmentsAcceptTheDynamicEnemyCapAndRejectTheNextCandidate() {
        val content = canonicalGameplayContent.copy(
            rebirth = canonicalGameplayContent.rebirth.copy(maxActiveEnemies = 3),
        )
        val engine = MutableGameState(content = content, seed = 207, initialMatter = 0).also { state ->
            state.startRun()
            state.enemies.clear()
        }
        val splitter = engine.addEnemyForTesting(0f, 0f, type = EnemyType.SPLITTER).also { enemy ->
            enemy.dead = true
        }
        val retained = engine.addEnemyForTesting(40f, 0f)
        val retainedIds = engine.enemies.map(Enemy::id)

        engine.spawnSplitterFragments(splitter)

        assertEquals(content.rebirth.maxActiveEnemies, engine.enemies.size)
        assertEquals(retainedIds, engine.enemies.take(retainedIds.size).map(Enemy::id))
        assertTrue(retained in engine.enemies)
        assertEquals(1, engine.enemies.count { enemy -> enemy.id !in retainedIds })
    }

    @Test
    fun enemyTypeGatesOpenAtExactThreatTimes() {
        val engine = gameScenario(seed = 3, initialMatter = 0)
        val gates = listOf(
            EnemyType.SHOOTER to 38f,
            EnemyType.SWARMLING to 75f,
            EnemyType.INTERCEPTOR to 90f,
            EnemyType.COMET to 120f,
            EnemyType.CHARGER to 150f,
            EnemyType.WEAVER to 150f,
            EnemyType.BOMBER to 200f,
            EnemyType.SPLITTER to 240f,
            EnemyType.WARDEN to 240f,
            EnemyType.LANCER to 240f,
            EnemyType.PYLON to 270f,
            EnemyType.SNIPER to 330f,
            EnemyType.AEGIS to 390f,
            EnemyType.MINELAYER to 450f,
        )
        gates.forEach { (type, gate) ->
            assertEquals(0f, engine.weightAt(gate - 0.001f, type), "$type before its gate")
            assertTrue(engine.weightAt(gate, type) > 0f, "$type at its gate")
            // The weight ramps in over a minute after the gate.
            assertTrue(engine.weightAt(gate, type) < engine.weightAt(gate + 60f, type), "$type ramps in")
            assertEquals(engine.content.enemy(type).weight, engine.weightAt(gate + 60f, type), "$type full weight")
        }
        assertTrue(engine.weightAt(0f, EnemyType.DRIFTER) > 0f)
        // Rebirth-gated kinds never appear at tier 0, however late; scheduled kinds never take ordinary weight.
        listOf(EnemyType.PHANTOM, EnemyType.BROODMOTHER, EnemyType.REFLECTOR, EnemyType.ELITE, EnemyType.HERALD, EnemyType.ARCHITECT)
            .forEach { type -> assertEquals(0f, engine.weightAt(700f, type), "$type at tier 0") }
        // Habitat-only kinds take no open-space weight, and owner-spawned mini and secret bosses no ordinary weight.
        listOf(
            EnemyType.SENTRY, EnemyType.CRAWLER, EnemyType.SERAPH, EnemyType.CHOIR, EnemyType.IMP, EnemyType.HOUND,
            EnemyType.CIPHER, EnemyType.MIRROR, EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE,
            EnemyType.PYRE_HERALD, EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT,
        ).forEach { type -> assertEquals(0f, engine.weightAt(700f, type), "$type in open space") }
    }

    @Test
    fun enemyTypePicksAreDeterministicPerSeedAndRespectRebirthGates() {
        fun picks(seed: Int, tier: Int): List<EnemyType> {
            val engine = gameScenario(seed = seed, initialMatter = 0, initialRebirthLevel = tier)
            engine.elapsed = 700f
            return List(600) { engine.pickEnemyType() }
        }
        assertEquals(picks(seed = 11, tier = 0), picks(seed = 11, tier = 0))
        val tierZero = picks(seed = 11, tier = 0).toSet()
        assertTrue(EnemyType.PHANTOM !in tierZero && EnemyType.BROODMOTHER !in tierZero && EnemyType.REFLECTOR !in tierZero)
        assertTrue(tierZero.containsAll(listOf(EnemyType.DRIFTER, EnemyType.SWARMLING, EnemyType.SNIPER, EnemyType.MINELAYER)))
        assertTrue(tierZero.containsAll(listOf(EnemyType.COMET, EnemyType.PYLON)), "the open-space newcomers join tier 0: $tierZero")
        val tierFour = picks(seed = 11, tier = 4).toSet()
        assertTrue(tierFour.containsAll(listOf(EnemyType.PHANTOM, EnemyType.BROODMOTHER, EnemyType.REFLECTOR)))
        assertTrue(EnemyType.HERALD !in tierFour && EnemyType.ELITE !in tierFour)
    }

    private fun GameScenario.weightAt(seconds: Float, type: EnemyType): Float {
        elapsed = seconds
        return typeWeight(content.enemy(type), weakOnly = false, recency = false)
    }

    private fun openTotemChoice(engine: GameScenario) {
        engine.activateTotemForTesting()
        engine.update(GameScenario.FIXED_STEP)
        assertEquals(GamePhase.CHOICE, engine.phase)
        assertEquals(ChoiceType.TOTEM, engine.choiceType)
        assertEquals(2, engine.choices.size)
    }

    private fun runningEngine(seed: Int, owned: List<WeaponId> = WeaponId.entries): GameScenario =
        gameScenario(seed = seed, initialMatter = 0).also { engine ->
            engine.unlockWeaponsForTesting(owned)
            engine.resize(1_280f, 720f)
            engine.startRun()
            engine.enemies.clear()
            engine.projectiles.clear()
            engine.updatePointer(640f, 360f)
        }
}
