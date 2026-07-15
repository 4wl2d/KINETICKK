// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameSystemsTest {
    @Test
    fun defaultSimulationSpeedIsSlightlyAccelerated() {
        val engine = GameEngine(seed = 10, initialMatter = 0)

        assertEquals(1.15f, engine.settings.simulationSpeed)
    }

    @Test
    fun physicalVelocityCanExceedOldDashCapAndRemainsFinite() {
        val engine = runningEngine(seed = 11)
        engine.enemies.clear()
        engine.setVelocityForTesting(5_000f, 320f)

        engine.update(GameEngine.FIXED_STEP)

        assertTrue(engine.speed > 1_040f, "Physical velocity was unexpectedly capped at ${engine.speed}")
        assertTrue(engine.speed.isFinite())
        assertTrue(engine.velocityX.isFinite())
        assertTrue(engine.velocityY.isFinite())
        assertTrue(engine.coreX.isFinite())
        assertTrue(engine.coreY.isFinite())
    }

    @Test
    fun sweptCoreCollisionDamagesEnemyCrossedAtHighSpeed() {
        val engine = runningEngine(seed = 12)
        engine.enemies.clear()
        engine.updatePointer(1_280f, 360f)
        val enemy = engine.addEnemyForTesting(x = 100f, y = 0f, hp = 10_000f, radius = 12f)
        engine.setVelocityForTesting(24_000f, 0f)

        engine.update(GameEngine.FIXED_STEP)

        assertTrue(engine.coreX > enemy.x, "The Core must cross the enemy during this step")
        assertTrue(enemy.hp < enemy.maxHp, "Swept collision did not damage the crossed enemy")
        assertTrue(engine.lastImpact > 0f)
    }

    @Test
    fun everyCatalogItemCanBeAcquiredAndDiscovered() {
        val engine = GameEngine(seed = 13, initialMatter = 0)

        ItemCatalog.all.forEach { engine.acquireItemForTesting(it.id) }

        assertEquals(ItemCatalog.ITEM_COUNT, engine.acquiredItemCount)
        assertEquals(ItemCatalog.ITEM_COUNT, engine.discoveredItemCount)
        assertEquals(ItemCatalog.all.last(), engine.recentItem)
        ItemCatalog.all.forEach { item ->
            assertEquals(1, engine.itemStack(item.id), "Item ${item.id} was not acquired exactly once")
            assertTrue(engine.isItemDiscovered(item.id), "Item ${item.id} was not discovered")
        }
        assertTrue(engine.damageMultiplier.isFinite())
        assertTrue(engine.weaponPower.isFinite())
        assertTrue(engine.maxHp.isFinite())
    }

    @Test
    fun itemStacksStopAtMaximumAndThreeFamilyItemsTriggerResonance() {
        val engine = GameEngine(seed = 14, initialMatter = 0)
        val first = requireNotNull(ItemCatalog.byId(0))
        val second = requireNotNull(ItemCatalog.byId(1))
        val third = requireNotNull(ItemCatalog.byId(2))

        engine.acquireItemForTesting(first.id)
        engine.acquireItemForTesting(second.id)
        val damageBeforeResonance = engine.damageMultiplier
        val weaponPowerBeforeResonance = engine.weaponPower
        val massBeforeThirdItem = engine.mass

        engine.acquireItemForTesting(third.id)

        assertEquals(damageBeforeResonance + third.primary.amount + 0.04f, engine.damageMultiplier, 0.0001f)
        assertEquals(weaponPowerBeforeResonance + 0.06f, engine.weaponPower, 0.0001f)
        assertEquals(massBeforeThirdItem + third.secondary.amount, engine.mass, 0.0001f)
        assertEquals("IMPACT RESONANCE", engine.message)

        val acquisitionsBeforeRepeatedStacks = engine.acquiredItemCount
        repeat(third.maxStacks + 5) { engine.acquireItemForTesting(third.id) }

        assertEquals(third.maxStacks, engine.itemStack(third.id))
        assertEquals(
            acquisitionsBeforeRepeatedStacks + third.maxStacks - 1,
            engine.acquiredItemCount,
            "Attempts above maxStacks must not count as acquisitions",
        )
        assertEquals(3, engine.discoveredItemCount)
    }

    @Test
    fun overflowingDataQueuesAllChoicesAndRetainsRemainder() {
        val engine = runningEngine(seed = 15)
        engine.enemies.clear()

        engine.grantDataForTesting(100f)

        assertEquals(4, engine.level)
        assertEquals(5, engine.data)
        assertEquals(52, engine.nextLevelData)
        assertEquals(GamePhase.CHOICE, engine.phase)
        assertTrue(engine.choices.isNotEmpty())

        repeat(2) {
            engine.choose(0)
            assertEquals(GamePhase.CHOICE, engine.phase, "Queued level choice was not presented")
            assertEquals(5, engine.data, "Choosing an upgrade must not consume overflow Data")
        }

        engine.choose(0)
        assertEquals(GamePhase.RUNNING, engine.phase)
        assertEquals(3, engine.acquiredItemCount)
        assertEquals(5, engine.data)
        assertEquals(4, engine.level)

        engine.grantDataForTesting(1f)
        assertEquals(GamePhase.RUNNING, engine.phase)
        assertEquals(6, engine.data)
    }

    @Test
    fun everyWeaponCanBeEquippedAndProducesADistinctRuntimeSignature() {
        val signatures = WeaponId.entries.associateWith(::exerciseWeapon)

        assertEquals(WeaponId.entries.size, signatures.size)
        assertEquals(
            WeaponId.entries.size,
            signatures.values.toSet().size,
            "Weapon runtime signatures were not distinct: $signatures",
        )
    }

    @Test
    fun eachMetaPurchaseDeductsLongMatterAndAddsExactlyOneRank() {
        val engine = GameEngine(seed = 30, initialMatter = Int.MAX_VALUE)

        MetaUpgradeCatalog.all.forEach { upgrade ->
            val matterBefore = engine.totalMatter

            assertTrue(engine.buyMetaUpgrade(upgrade.id))

            assertEquals(matterBefore - upgrade.cost(0).toLong(), engine.totalMatter)
            assertEquals(1, engine.metaLevel(upgrade.id))
        }
        assertEquals(MetaUpgradeId.entries.size, MetaUpgradeId.entries.sumOf(engine::metaLevel))
    }

    @Test
    fun permanentWeaponPurchaseUnlocksEquipsAndDoesNotChargeTwice() {
        val engine = GameEngine(seed = 31, initialMatter = 10_000)
        val weapon = WeaponId.SINGULARITY_SPEAR
        val cost = WeaponCatalog.byId(weapon).permanentUnlockCost.toLong()
        val matterBefore = engine.totalMatter

        assertFalse(engine.isWeaponUnlocked(weapon))
        assertTrue(engine.buyOrEquipWeapon(weapon))
        assertTrue(engine.isWeaponUnlocked(weapon))
        assertEquals(weapon, engine.weapon)
        assertEquals(weapon, engine.startingWeapon)
        assertEquals(matterBefore - cost, engine.totalMatter)

        val matterAfterUnlock = engine.totalMatter
        assertTrue(engine.buyOrEquipWeapon(weapon))
        assertEquals(matterAfterUnlock, engine.totalMatter)

        engine.startRun()
        assertEquals(weapon, engine.weapon)

        val poorEngine = GameEngine(seed = 32, initialMatter = 0)
        assertFalse(poorEngine.buyOrEquipWeapon(WeaponId.MORNINGSTAR))
        assertFalse(poorEngine.isWeaponUnlocked(WeaponId.MORNINGSTAR))
    }

    @Test
    fun totemAmplificationAdvancesThroughVisibleMasteryMilestones() {
        val engine = runningEngine(seed = 33)
        engine.equipWeaponForTesting(WeaponId.PRISM_RELAY)

        assertEquals(WeaponMastery.CALIBRATED, engine.currentWeaponMastery)
        assertEquals(WeaponMastery.AMPLIFIED, engine.nextWeaponMastery)

        engine.amplifyWeaponForTesting(2)

        assertEquals(3, engine.weaponLevel)
        assertEquals(WeaponMastery.AMPLIFIED, engine.currentWeaponMastery)
        assertEquals(WeaponMastery.RESONANT, engine.nextWeaponMastery)
        assertEquals("PRISM RELAY // AMPLIFIED", engine.message)

        engine.amplifyWeaponForTesting(3)

        assertEquals(6, engine.weaponLevel)
        assertEquals(WeaponMastery.RESONANT, engine.currentWeaponMastery)
        assertEquals(WeaponMastery.ASCENDED, engine.nextWeaponMastery)

        engine.enemies.clear()
        engine.projectiles.clear()
        engine.addEnemyForTesting(250f, 0f)
        engine.update(GameEngine.FIXED_STEP)

        val relays = engine.projectiles.filter { !it.hostile && it.sourceWeapon == WeaponId.PRISM_RELAY }
        assertEquals(2, relays.size, "Resonant Prism Relay must fire a twin volley")
        assertTrue(relays.all { it.pierce == 4 }, "Resonant Prism Relay must gain additional ricochets")
    }

    @Test
    fun prismRelayRefractsFromItsFirstTargetIntoAnotherEnemy() {
        val engine = runningEngine(seed = 34)
        engine.enemies.clear()
        engine.projectiles.clear()
        engine.equipWeaponForTesting(WeaponId.PRISM_RELAY)
        val first = engine.addEnemyForTesting(140f, 0f)
        val second = engine.addEnemyForTesting(280f, 0f)

        repeat(50) { engine.update(GameEngine.FIXED_STEP) }

        assertTrue(first.hp < first.maxHp, "Prism Relay did not hit its initial target")
        assertTrue(second.hp < second.maxHp, "Prism Relay did not ricochet into the second target")
    }

    @Test
    fun dashTurnsThroughOpposingMomentum() {
        val engine = runningEngine(seed = 40)
        engine.enemies.clear()
        engine.updatePointer(1_280f, 360f)
        engine.setVelocityForTesting(-900f, 0f)

        engine.requestDash()
        engine.update(GameEngine.FIXED_STEP)

        assertTrue(engine.velocityX > 300f, "Dash did not regain steering authority: ${engine.velocityX}")
    }

    @Test
    fun dashUsesLastAimDirectionInsideAimDeadzone() {
        val engine = runningEngine(seed = 41)
        engine.enemies.clear()
        engine.updatePointer(1_280f, 360f)
        engine.updatePointer(640f, 360f)

        engine.requestDash()
        engine.update(GameEngine.FIXED_STEP)

        assertTrue(engine.velocityX > 500f)
        assertTrue(engine.heat > 30f)
    }

    @Test
    fun bufferedDashDoesNotSurvivePause() {
        val engine = runningEngine(seed = 42)
        engine.enemies.clear()

        engine.requestDash()
        engine.togglePause()
        engine.togglePause()
        engine.update(GameEngine.FIXED_STEP)

        assertEquals(0f, engine.heat)
        assertTrue(engine.speed < 100f)
    }

    @Test
    fun hudDashPressPreservesWorldAim() {
        val engine = runningEngine(seed = 43)
        engine.enemies.clear()
        engine.updatePointer(120f, 360f)

        engine.pointerPressed(1_280f - 82f, 720f - 88f)

        assertEquals(120f, engine.pointerX)
        assertEquals(360f, engine.pointerY)
        engine.update(GameEngine.FIXED_STEP)
        assertTrue(engine.velocityX < -500f, "HUD dash was redirected toward the button")
        engine.pointerReleased()
    }

    @Test
    fun brakeSourcesDoNotCancelEachOther() {
        val engine = runningEngine(seed = 44)
        engine.setBrake(true)
        engine.pointerPressed(1_280f - 190f, 720f - 67f)
        engine.pointerReleased()

        assertTrue(engine.braking, "Releasing touch brake cancelled held keyboard brake")
        engine.setBrake(false)
        assertFalse(engine.braking)
    }

    @Test
    fun brakeReleaseDuringPauseIsAppliedOnResume() {
        val engine = runningEngine(seed = 45)
        engine.setBrake(true)
        engine.togglePause()
        engine.setBrake(false)
        engine.togglePause()

        assertFalse(engine.braking)
    }

    @Test
    fun fastEnemyStillHurtsSlowCore() {
        val engine = runningEngine(seed = 46)
        engine.enemies.clear()
        engine.updatePointer(640f, 360f)
        val enemy = engine.addEnemyForTesting(70f, 0f, hp = 1_000f)
        enemy.vx = -12_000f
        val hpBefore = engine.hp

        engine.update(GameEngine.FIXED_STEP)

        assertTrue(engine.hp < hpBefore, "Enemy momentum was incorrectly treated as Core offense")
    }

    @Test
    fun simultaneousBulletsRespectShortHurtCooldown() {
        val engine = runningEngine(seed = 47)
        engine.enemies.clear()
        engine.updatePointer(640f, 360f)
        repeat(2) { engine.projectiles += Projectile(0f, 0f, 0f, 0f, 6f, 1f) }

        engine.update(GameEngine.FIXED_STEP)

        assertEquals(88f, engine.hp, 0.001f)
    }

    @Test
    fun openingEnemiesSpawnJustBeyondViewport() {
        val engine = runningEngine(seed = 48)

        engine.enemies.forEach { enemy ->
            assertTrue(
                kotlin.math.abs(enemy.x) > 640f || kotlin.math.abs(enemy.y) > 360f,
                "Opening enemy spawned inside viewport at ${enemy.x}, ${enemy.y}",
            )
            val distance = kotlin.math.sqrt(enemy.x * enemy.x + enemy.y * enemy.y)
            val directionX = kotlin.math.abs(enemy.x / distance)
            val directionY = kotlin.math.abs(enemy.y / distance)
            val edgeDistance = minOf(640f / maxOf(0.001f, directionX), 360f / maxOf(0.001f, directionY))
            val overscan = distance - edgeDistance
            assertTrue(overscan in 89f..241f, "Unexpected spawn overscan: $overscan")
        }
    }

    @Test
    fun farStragglersAreLeashedWithoutRewards() {
        val engine = runningEngine(seed = 49)
        engine.enemies.clear()
        val nearby = engine.addEnemyForTesting(220f, 0f)
        val farAway = engine.addEnemyForTesting(10_000f, 0f)
        val killsBefore = engine.kills

        engine.update(GameEngine.FIXED_STEP)

        assertTrue(nearby in engine.enemies)
        assertTrue(farAway !in engine.enemies)
        assertEquals(killsBefore, engine.kills)
    }

    @Test
    fun continuousDamageDoesNotHoldFullWhiteFlash() {
        val engine = runningEngine(seed = 50)
        engine.enemies.clear()
        engine.equipWeaponForTesting(WeaponId.ENTROPY_FIELD)
        val enemy = engine.addEnemyForTesting(120f, 0f)

        repeat(24) { engine.update(GameEngine.FIXED_STEP) }

        assertTrue(enemy.hp < enemy.maxHp)
        assertTrue(enemy.flash < 0.5f, "Continuous damage pinned a full hit flash: ${enemy.flash}")
    }

    @Test
    fun impactFeedbackExpires() {
        val engine = runningEngine(seed = 51)
        engine.enemies.clear()
        engine.updatePointer(1_280f, 360f)
        engine.addEnemyForTesting(100f, 0f, hp = 10_000f, radius = 12f)
        engine.setVelocityForTesting(24_000f, 0f)
        engine.update(GameEngine.FIXED_STEP)
        assertTrue(engine.lastImpactTime > 0f)

        engine.enemies.clear()
        repeat(100) { engine.update(GameEngine.FIXED_STEP) }

        assertEquals(0f, engine.lastImpactTime)
    }

    private fun exerciseWeapon(id: WeaponId): WeaponRuntimeSignature {
        val engine = runningEngine(seed = 100 + id.ordinal)
        engine.enemies.clear()
        engine.projectiles.clear()
        engine.equipWeaponForTesting(id)
        engine.updatePointer(1_280f, 360f)
        engine.setVelocityForTesting(620f, 0f)
        var observedEnemy: Enemy? = null

        when (id) {
            WeaponId.FLUX_WAKE -> {
                engine.setVelocityForTesting(900f, 0f)
                engine.update(0.05f)
                assertTrue(engine.trail.isNotEmpty())
            }
            WeaponId.MORNINGSTAR -> {
                engine.update(GameEngine.FIXED_STEP)
                observedEnemy = engine.addEnemyForTesting(engine.morningstarX, engine.morningstarY)
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(engine.morningstarAngle > 0f)
                assertTrue(observedEnemy.hp < observedEnemy.maxHp)
            }
            WeaponId.PHASE_LATTICE -> {
                observedEnemy = engine.addEnemyForTesting(110f, 0f)
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(observedEnemy.hp < observedEnemy.maxHp)
            }
            WeaponId.NULL_LANCE -> {
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(engine.projectiles.any { !it.hostile && it.sourceWeapon == id && it.pierce == 5 })
            }
            WeaponId.GRAVITY_MINES -> {
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(engine.weaponNodes.any { it.type == WeaponNodeType.GRAVITY_MINE })
            }
            WeaponId.ION_SWARM -> {
                observedEnemy = engine.addEnemyForTesting(250f, 0f)
                engine.update(GameEngine.FIXED_STEP)
                assertEquals(2, engine.weaponOrbitals.size)
                assertTrue(engine.projectiles.any { !it.hostile && it.sourceWeapon == id })
            }
            WeaponId.RIFT_BLADES -> {
                engine.update(GameEngine.FIXED_STEP)
                val orbital = engine.weaponOrbitals.first()
                observedEnemy = engine.addEnemyForTesting(orbital.x, orbital.y)
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(observedEnemy.hp < observedEnemy.maxHp)
            }
            WeaponId.ARC_COIL -> {
                observedEnemy = engine.addEnemyForTesting(200f, 0f)
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(engine.weaponArcs.isNotEmpty())
                assertTrue(observedEnemy.hp < observedEnemy.maxHp)
            }
            WeaponId.QUASAR_CANNON -> {
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(engine.projectiles.any { !it.hostile && it.sourceWeapon == id && it.pierce == 10 })
            }
            WeaponId.ENTROPY_FIELD -> {
                observedEnemy = engine.addEnemyForTesting(120f, 0f)
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(observedEnemy.hp < observedEnemy.maxHp)
            }
            WeaponId.SINGULARITY_SPEAR -> {
                observedEnemy = engine.addEnemyForTesting(300f, 0f)
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(engine.weaponBeamTime > 0f)
                assertTrue(observedEnemy.hp < observedEnemy.maxHp)
            }
            WeaponId.PRISM_RELAY -> {
                observedEnemy = engine.addEnemyForTesting(250f, 0f)
                engine.update(GameEngine.FIXED_STEP)
                assertTrue(engine.projectiles.any { !it.hostile && it.sourceWeapon == id && it.pierce == 2 })
            }
        }

        val friendlyProjectiles = engine.projectiles.filterNot(Projectile::hostile)
        val firstProjectile = friendlyProjectiles.firstOrNull()
        return WeaponRuntimeSignature(
            trailCount = engine.trail.size,
            orbitalCount = engine.weaponOrbitals.size,
            orbitalRadiusBits = engine.weaponOrbitals.firstOrNull()?.radius?.toBits() ?: 0,
            nodeCount = engine.weaponNodes.size,
            projectileCount = friendlyProjectiles.size,
            projectilePierce = firstProjectile?.pierce ?: -1,
            projectileRadiusBits = firstProjectile?.radius?.toBits() ?: 0,
            projectileSourceOrdinal = firstProjectile?.sourceWeapon?.ordinal ?: -1,
            arcCount = engine.weaponArcs.size,
            beamActive = engine.weaponBeamTime > 0f,
            morningstarMoved = engine.morningstarAngle > 0f,
            enemyDamageMilli = observedEnemy?.let { ((it.maxHp - it.hp) * 1_000f).toInt() } ?: 0,
        )
    }

    private fun runningEngine(seed: Int): GameEngine = GameEngine(seed = seed, initialMatter = 0).apply {
        resize(1_280f, 720f)
        startRun()
    }

    private data class WeaponRuntimeSignature(
        val trailCount: Int,
        val orbitalCount: Int,
        val orbitalRadiusBits: Int,
        val nodeCount: Int,
        val projectileCount: Int,
        val projectilePierce: Int,
        val projectileRadiusBits: Int,
        val projectileSourceOrdinal: Int,
        val arcCount: Int,
        val beamActive: Boolean,
        val morningstarMoved: Boolean,
        val enemyDamageMilli: Int,
    )
}
