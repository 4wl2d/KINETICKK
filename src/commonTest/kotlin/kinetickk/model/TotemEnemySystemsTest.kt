package kinetickk.model

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TotemEnemySystemsTest {
    @Test
    fun totemAmplifyAndChangeFormATwoStageChoiceThatPreservesWeaponLevel() {
        val engine = runningEngine(seed = 201)
        val startingWeapon = engine.weapon

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
        assertEquals(2, engine.weaponLevel)
        assertTrue(engine.choices.isEmpty())

        openTotemChoice(engine)
        val changeIndex = engine.choices.indexOfFirst {
            it.totemAction == TotemAction.CHANGE_WEAPON
        }
        engine.choose(changeIndex)

        assertEquals(GamePhase.CHOICE, engine.phase)
        assertEquals(ChoiceType.WEAPON, engine.choiceType)
        assertEquals(startingWeapon, engine.weapon)
        assertEquals(2, engine.weaponLevel)
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
        assertEquals(2, engine.weaponLevel)
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

        engine.update(GameEngine.FIXED_STEP)

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

        engine.update(GameEngine.FIXED_STEP)

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

        engine.update(GameEngine.FIXED_STEP)

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
        engine.update(GameEngine.FIXED_STEP)

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

        engine.update(GameEngine.FIXED_STEP)

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
    fun enemyTypeTiersChangeAtExactElapsedBoundaries() {
        assertEquals(EnemyType.DRIFTER, enemyTypeForElapsed(37.999f, 0.999f))
        assertEquals(EnemyType.SHOOTER, enemyTypeForElapsed(38f, 0.999f))
        assertEquals(EnemyType.SHOOTER, enemyTypeForElapsed(89.999f, 0.999f))
        assertEquals(EnemyType.INTERCEPTOR, enemyTypeForElapsed(90f, 0.999f))
        assertEquals(EnemyType.INTERCEPTOR, enemyTypeForElapsed(149.999f, 0.999f))
        assertEquals(EnemyType.WEAVER, enemyTypeForElapsed(150f, 0.999f))
        assertEquals(EnemyType.WEAVER, enemyTypeForElapsed(239.999f, 0.999f))
        assertEquals(EnemyType.WARDEN, enemyTypeForElapsed(240f, 0.999f))
        assertEquals(EnemyType.WARDEN, enemyTypeForElapsed(359.999f, 0.999f))
        assertEquals(EnemyType.WARDEN, enemyTypeForElapsed(360f, 0.999f))
    }

    @Test
    fun enemyTypeTierRollThresholdsAreDeterministic() {
        assertTier(
            elapsed = 38f,
            0f to EnemyType.DRIFTER,
            0.699f to EnemyType.DRIFTER,
            0.70f to EnemyType.SHOOTER,
        )
        assertTier(
            elapsed = 90f,
            0.499f to EnemyType.DRIFTER,
            0.50f to EnemyType.SHOOTER,
            0.779f to EnemyType.SHOOTER,
            0.78f to EnemyType.INTERCEPTOR,
        )
        assertTier(
            elapsed = 150f,
            0.339f to EnemyType.DRIFTER,
            0.34f to EnemyType.SHOOTER,
            0.579f to EnemyType.SHOOTER,
            0.58f to EnemyType.CHARGER,
            0.759f to EnemyType.CHARGER,
            0.76f to EnemyType.INTERCEPTOR,
            0.919f to EnemyType.INTERCEPTOR,
            0.92f to EnemyType.WEAVER,
        )
        assertTier(
            elapsed = 240f,
            0.259f to EnemyType.DRIFTER,
            0.26f to EnemyType.SHOOTER,
            0.459f to EnemyType.SHOOTER,
            0.46f to EnemyType.CHARGER,
            0.619f to EnemyType.CHARGER,
            0.62f to EnemyType.INTERCEPTOR,
            0.769f to EnemyType.INTERCEPTOR,
            0.77f to EnemyType.WEAVER,
            0.869f to EnemyType.WEAVER,
            0.87f to EnemyType.SPLITTER,
            0.949f to EnemyType.SPLITTER,
            0.95f to EnemyType.WARDEN,
        )
        assertTier(
            elapsed = 360f,
            0.219f to EnemyType.DRIFTER,
            0.22f to EnemyType.SHOOTER,
            0.389f to EnemyType.SHOOTER,
            0.39f to EnemyType.CHARGER,
            0.529f to EnemyType.CHARGER,
            0.53f to EnemyType.INTERCEPTOR,
            0.669f to EnemyType.INTERCEPTOR,
            0.67f to EnemyType.WEAVER,
            0.789f to EnemyType.WEAVER,
            0.79f to EnemyType.SPLITTER,
            0.909f to EnemyType.SPLITTER,
            0.91f to EnemyType.WARDEN,
        )
    }

    private fun openTotemChoice(engine: GameEngine) {
        engine.activateTotemForTesting()
        engine.update(GameEngine.FIXED_STEP)
        assertEquals(GamePhase.CHOICE, engine.phase)
        assertEquals(ChoiceType.TOTEM, engine.choiceType)
        assertEquals(2, engine.choices.size)
    }

    private fun assertTier(elapsed: Float, vararg expected: Pair<Float, EnemyType>) {
        expected.forEach { (roll, type) ->
            assertEquals(type, enemyTypeForElapsed(elapsed, roll), "elapsed=$elapsed roll=$roll")
        }
    }

    private fun runningEngine(seed: Int): GameEngine = GameEngine(seed = seed, initialMatter = 0).also { engine ->
        engine.resize(1_280f, 720f)
        engine.startRun()
        engine.enemies.clear()
        engine.projectiles.clear()
        engine.updatePointer(640f, 360f)
    }
}
