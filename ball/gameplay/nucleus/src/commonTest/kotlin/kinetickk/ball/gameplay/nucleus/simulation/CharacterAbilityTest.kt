// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.*
import kotlin.test.*

class CharacterAbilityTest {
    /** A started run of [shape] with no enemies or shots, so only the scripted ones act. */
    private fun game(shape: CoreShape) = MutableGameState(canonicalGameplayContent).apply {
        coreShape = shape
        startRun()
        enemies.clear()
        projectiles.clear()
        runGrace = 10f
    }

    private fun MutableGameState.step(delta: Float = MutableGameState.FIXED_STEP) {
        previousCoreX = coreX
        previousCoreY = coreY
        elapsed += delta
        ageCharacterStepFlags()
        updateCharacterRuntime(delta)
    }

    private fun MutableGameState.moveAt(vx: Float, vy: Float, delta: Float = MutableGameState.FIXED_STEP) {
        velocityX = vx
        velocityY = vy
        smoothedVelocityX = vx
        smoothedVelocityY = vy
        previousCoreX = coreX
        previousCoreY = coreY
        coreX += vx * delta
        coreY += vy * delta
        elapsed += delta
        ageCharacterStepFlags()
        updateCharacterRuntime(delta)
    }

    private fun MutableGameState.hostileShotAt(fromX: Float, damage: Float = 10f) = Projectile(
        x = coreX + fromX, y = coreY, vx = -fromX * 30f, vy = 0f, radius = 5f, life = 1f, hostile = true, damage = damage,
    ).also { shot ->
        shot.previousX = coreX + fromX
        shot.previousY = coreY
        projectiles += shot
    }

    @Test fun reductionForkCopiesScalarsAndSharesBuffersUntilTheFirstChangedWrite() {
        val source = game(CoreShape.SPIRAL)
        repeat(10) { source.moveAt(400f, 0f) }
        val committedPath = source.characterRuntime.ghostPath.toList()
        val fork = source.copyForReduction()
        assertNotSame(source.characterRuntime, fork.characterRuntime)
        assertTrue(fork.characterRuntime.ghostPath.sharesStorageWith(source.characterRuntime.ghostPath))
        assertTrue(fork.characterRuntime.drones.sharesStorageWith(source.characterRuntime.drones))
        repeat(5) { fork.moveAt(400f, 0f) }
        assertFalse(fork.characterRuntime.ghostPath.sharesStorageWith(source.characterRuntime.ghostPath))
        assertEquals(committedPath, source.characterRuntime.ghostPath.toList())
        assertEquals(10, source.characterRuntime.ghostSamples)
        assertEquals(15, fork.characterRuntime.ghostSamples)
        // A scalar-input sibling shares the committed runtime: it never runs a step.
        assertSame(source.characterRuntime, source.copyForScalarInputReduction().characterRuntime)
    }

    @Test fun theEchoBufferIsWrittenInPlaceOnceAStepOwnsIt() {
        val game = game(CoreShape.SPIRAL)
        game.moveAt(300f, 0f)
        val owned = game.characterRuntime.ghostPath
        val probe = owned.fork()
        repeat(300) { game.moveAt(300f, 0f) }
        // The candidate keeps writing its own storage; the probe still sees the old samples.
        assertSame(owned, game.characterRuntime.ghostPath)
        assertFalse(probe.sharesStorageWith(owned))
    }

    @Test fun traitsApplyWhenTheRunStarts() {
        val prism = game(CoreShape.PRISM)
        assertEquals(1.25f, prism.mass, 1e-5f)
        assertEquals(120f, prism.maxHp)
        assertEquals(0.29f * 1.15f, prism.dragCoefficient, 1e-5f)
        val shard = game(CoreShape.SHARD)
        assertEquals(85f, shard.maxHp)
        assertEquals(0.13f, shard.critChance, 1e-5f)
        assertEquals(MutableGameState.CORE_RADIUS * 0.9f, shard.coreRadius, 1e-4f)
        assertEquals(36f * 0.85f, shard.dashHeatCost, 1e-4f)
        assertEquals(1.7f, game(CoreShape.DIAMOND).critMultiplier, 1e-5f)
        assertEquals(210f, game(CoreShape.HEXAGON).pickupRadius)
        assertEquals(MutableGameState.CORE_RADIUS, game(CoreShape.ORB).coreRadius)
    }

    @Test fun everyCharacterHasABuildTag() {
        assertEquals(BuildTag.RAM, CoreShape.STAR.characterTag())
        assertEquals(BuildTag.BULWARK, CoreShape.SPIRAL.characterTag())
        assertEquals(BuildTag.CHAIN, CoreShape.DIPOLE.characterTag())
        assertEquals(BuildTag.OVERCLOCK, CoreShape.PENTAGON.characterTag())
        assertEquals(BuildTag.PRECISION, CoreShape.CRESCENT.characterTag())
        assertEquals(BuildTag.ARSENAL, CoreShape.HEXAGON.characterTag())
    }

    @Test fun orbitNeedsARealArcAndTheNextImpactOrWeaponHitReleasesItOnce() {
        val game = game(CoreShape.ORB)
        repeat(900) { index ->
            val angle = index * 0.01f
            game.moveAt(cos(angle) * 400f, sin(angle) * 400f, 0.01f)
        }
        assertTrue(game.orbitCharged)
        val near = game.addEnemyForTesting(game.coreX + 60f, game.coreY)
        val far = game.addEnemyForTesting(game.coreX + 400f, game.coreY)
        val power = game.abilityPower()
        game.onCharacterPrimaryHit(near)
        assertEquals(near.maxHp - ORBIT_DAMAGE * power, near.hp, 0.01f)
        assertEquals(far.maxHp, far.hp)
        val after = near.hp
        game.onCharacterPrimaryHit(near)
        assertEquals(after, near.hp)
        assertFalse(game.orbitCharged)
        // A weapon hit releases it too.
        game.characterRuntime.charge = 1f
        game.dealWeaponDamage(near, 1f)
        assertEquals(after - 1f - ORBIT_DAMAGE * power, near.hp, 0.05f)
        // So does a Dash impact.
        game.characterRuntime.charge = 1f
        val beforeDash = near.hp
        game.onCharacterPrimaryHit(near, dash = true)
        assertEquals(beforeDash - ORBIT_DAMAGE * power, near.hp, 0.05f)
        assertFalse(game.orbitCharged)
    }

    @Test fun facetsNegateOneHitPerHurtWindowRegenerateAndBreakOutward() {
        val game = game(CoreShape.PRISM)
        assertEquals(4, game.intactFacets)
        val maxHp = game.maxHp
        repeat(4) { index ->
            game.hurtCooldown = 0f
            game.takeDamage(20f, kind = IncomingDamage.PROJECTILE)
            assertEquals(maxHp, game.hp, "facet ${index + 1} negates the hit")
            // A second hit inside the same hurt window is ignored, not charged another facet.
            game.takeDamage(20f, kind = IncomingDamage.PROJECTILE)
            assertEquals(3 - index, game.intactFacets)
        }
        assertEquals(80.0, game.characterRuntime.damagePrevented, 1e-3)
        game.hurtCooldown = 0f
        game.takeDamage(20f, kind = IncomingDamage.PROJECTILE)
        assertTrue(game.hp < maxHp)
        // The timer waits two seconds after the last hit, then needs six.
        repeat((1.9f / MutableGameState.FIXED_STEP).toInt()) { game.step() }
        assertEquals(0f, game.characterRuntime.facetTimer)
        repeat((6.2f / MutableGameState.FIXED_STEP).toInt()) { game.step() }
        assertEquals(1, game.intactFacets)
        // An Elite kill restores one at once.
        val elite = game.addEnemyForTesting(500f, 500f, hp = 1f, type = EnemyType.ELITE)
        game.onEnemyKilled(elite)
        assertEquals(2, game.intactFacets)
    }

    @Test fun losingTheLastFacetPushesEnemiesAway() {
        val game = game(CoreShape.PRISM)
        game.characterRuntime.facets = 1
        val enemy = game.addEnemyForTesting(game.coreX + 100f, game.coreY)
        game.takeDamage(10f, kind = IncomingDamage.CONTACT)
        assertEquals(0, game.intactFacets)
        assertTrue(enemy.vx > 0f, "the break pushes outward")
    }

    @Test fun facetsNeverCancelDamageThatIsNotCancellable() {
        assertTrue(IncomingDamage.entries.filter { it != IncomingDamage.SELF }.all { it.cancellableByFacets })
        val game = game(CoreShape.PRISM)
        game.takeDamage(10f, kind = IncomingDamage.SELF)
        assertEquals(4, game.intactFacets)
        assertTrue(game.hp < game.maxHp)
    }

    @Test fun allFourFacetsStrengthenImpactsAndHeavyImpactsCutTheTimer() {
        val game = game(CoreShape.PRISM)
        assertEquals(FACET_IMPACT_BONUS, game.characterImpactMultiplier())
        game.characterRuntime.facets = 2
        assertEquals(1f, game.characterImpactMultiplier())
        val enemy = game.addEnemyForTesting(0f, 0f, hp = 100f)
        game.onCharacterImpact(enemy, DamageResult(40f, false), impactSpeed = 600f, multiplier = 1f)
        assertEquals(1f, game.characterRuntime.facetTimer, 1e-5f)
        // At most once per 1.2 s.
        game.onCharacterImpact(enemy, DamageResult(40f, false), impactSpeed = 600f, multiplier = 1f)
        assertEquals(1f, game.characterRuntime.facetTimer, 1e-5f)
        // Too slow or too light does not count.
        game.elapsed += 2f
        game.onCharacterImpact(enemy, DamageResult(40f, false), impactSpeed = 300f, multiplier = 1f)
        game.onCharacterImpact(enemy, DamageResult(10f, false), impactSpeed = 900f, multiplier = 1f)
        assertEquals(1f, game.characterRuntime.facetTimer, 1e-5f)
    }

    @Test fun punctureMarksOnDashAndOpensOnALaterImpactOrWeaponHit() {
        val game = game(CoreShape.SHARD)
        val enemy = game.addEnemyForTesting(50f, 0f)
        game.beginCharacterDash(); game.dashPhaseTime = 0.24f
        game.onCharacterDashContact(enemy); game.onCharacterDashContact(enemy)
        assertEquals(1, game.pendingDashHits)
        assertEquals(enemy.maxHp, enemy.hp)
        assertTrue(game.isMarked(enemy))
        game.dashPhaseTime = 0f
        game.heat = 50f
        game.dealWeaponDamage(enemy, 1f)
        assertEquals(enemy.maxHp - 1f - PUNCTURE_DAMAGE, enemy.hp, 0.05f)
        assertEquals(50f - PUNCTURE_HEAT_REFUND, game.heat)
        assertFalse(game.isMarked(enemy))
        // A second mark inside 0.25 s waits, and a Dash impact never opens one.
        enemy.characterMarkTime = 3f
        game.onCharacterPrimaryHit(enemy)
        assertTrue(game.isMarked(enemy))
        game.elapsed += 0.3f
        game.onCharacterPrimaryHit(enemy, dash = true)
        assertTrue(game.isMarked(enemy))
        game.elapsed += 0.3f
        game.onCharacterPrimaryHit(enemy)
        assertFalse(game.isMarked(enemy))
    }

    @Test fun haloWidensWithSpeedBurnsItsBandAndBurstsWhenCrossingHighSpeed() {
        val game = game(CoreShape.RING)
        repeat(240) { game.moveAt(0f, 0f) }
        assertEquals(CharacterRuntime.HALO_MIN_RADIUS, game.haloRadius, 0.5f)
        repeat(240) { game.moveAt(600f, 0f) }
        assertEquals(CharacterRuntime.HALO_MIN_RADIUS + 55f, game.haloRadius, 1f)
        val rim = game.addEnemyForTesting(game.coreX, game.coreY + game.haloRadius)
        val center = game.addEnemyForTesting(game.coreX + 5f, game.coreY)
        game.moveAt(600f, 0f)
        assertTrue(rim.hp < rim.maxHp)
        assertEquals(center.maxHp, center.hp)
        // Accelerating through 1 200 sends the Halo Burst once per cooldown.
        val crossed = game.addEnemyForTesting(game.coreX + 200f, game.coreY)
        game.moveAt(1_250f, 0f)
        assertEquals(HALO_BURST_RADIUS, game.haloRadius)
        assertEquals(crossed.maxHp - HALO_BURST_DAMAGE, crossed.hp, 0.5f)
        val afterBurst = crossed.hp
        game.moveAt(1_100f, 0f)
        game.moveAt(1_250f, 0f)
        assertEquals(afterBurst, crossed.hp, 0.5f, "the burst is on cooldown")
        assertTrue(game.characterRuntime.haloBurstFlash > 0f)
    }

    @Test fun parryNegatesReturnsShotsBuildsEdgeAndFullEdgeForcesTheNextImpactCritical() {
        val game = game(CoreShape.DIAMOND)
        game.characterRuntime.previousSpeed = 400f
        game.braking = true
        game.moveAt(300f, 0f)
        assertTrue(game.characterRuntime.parryWindow > 0f)
        assertFalse(game.tryCharacterParry(20f, 0f, 300f, 0f))
        val target = game.addEnemyForTesting(300f, 0f)
        assertTrue(game.tryCharacterParry(20f, 0f, -300f, 0f, damage = 12f, projectile = true))
        assertTrue(game.projectiles.any { !it.hostile }, "a parried shot returns")
        assertEquals(1, game.characterRuntime.edge)
        assertEquals(12.0, game.characterRuntime.damagePrevented, 1e-4)
        assertTrue(game.parriedThisStep)
        game.ageCharacterStepFlags()
        assertTrue(game.parriedThisStep, "observable through the next step")
        game.ageCharacterStepFlags()
        assertFalse(game.parriedThisStep)
        assertFalse(game.tryCharacterParry(20f, 0f, -300f, 0f))
        assertEquals(1f + EDGE_DAMAGE_PER_STACK, game.characterDamageMultiplier())
        game.characterRuntime.edge = CharacterRuntime.MAX_EDGE
        game.characterRuntime.edgeUntil = game.elapsed + 5f
        assertTrue(game.edgeCriticalReady())
        game.critChance = 0f
        val result = game.dealHit(target, 10f, HitSource.IMPACT, canCrit = true, forceCrit = game.edgeCriticalReady())
        assertTrue(result.critical)
        game.spendEdgeCritical()
        assertEquals(0, game.characterRuntime.edge)
        // Edge expires after six seconds.
        game.characterRuntime.edge = 2
        game.characterRuntime.edgeUntil = game.elapsed + 0.01f
        game.step(0.02f)
        assertEquals(0, game.characterRuntime.edge)
    }

    @Test fun latticeNeedsSeparatedDashesBrakeNoLongerCollapsesAndTheFourthNodeHitsInsideHarder() {
        val game = game(CoreShape.TESSERACT)
        val corners = listOf(0f to 0f, 400f to 0f, 400f to 400f, 0f to 400f)
        val inside = game.addEnemyForTesting(200f, 200f)
        // Edges strike what touches them: radius 17, 12 outside the first edge.
        val edge = game.addEnemyForTesting(200f, -12f)
        val clear = game.addEnemyForTesting(200f, -30f)
        corners.forEachIndexed { index, (x, y) ->
            game.coreX = x; game.coreY = y
            game.beginCharacterDash()
            game.dashPhaseTime = 0.24f
            game.coreX = x + 130f
            game.step(0.01f)
            if (index == 1) {
                // Brake no longer collapses the lattice.
                game.braking = true
                game.step(0.01f)
                game.braking = false
                assertEquals(2, game.latticeNodeCount)
            }
        }
        assertEquals(0, game.latticeNodeCount)
        val damage = LATTICE_BASE_DAMAGE + 4 * LATTICE_NODE_DAMAGE
        assertEquals(edge.maxHp - damage, edge.hp, 0.05f)
        assertEquals(clear.maxHp, clear.hp)
        assertEquals(inside.maxHp - damage * LATTICE_INSIDE_MULTIPLIER, inside.hp, 0.05f)
    }

    @Test fun stellarMassGrowsTheCoreAndSupernovaConsumesIt() {
        val game = game(CoreShape.STAR)
        val far = game.addEnemyForTesting(5_000f, 5_000f, hp = 1f)
        game.onEnemyKilled(far)
        assertEquals(1, game.stellarMass)
        assertEquals(MutableGameState.CORE_RADIUS * 1.015f, game.coreRadius, 1e-4f)
        assertEquals(1.02f, game.effectiveMass(), 1e-5f)
        assertEquals(1.03f, game.characterImpactMultiplier(), 1e-5f)
        // Taking damage sheds a quarter, rounded up.
        game.characterRuntime.stellarMass = 8
        game.takeDamage(5f)
        assertEquals(6, game.stellarMass)
        // An impact kill past half fires the Supernova.
        game.characterRuntime.stellarMass = 12
        val victim = game.addEnemyForTesting(game.coreX + 100f, game.coreY, hp = 2_000f)
        val killed = game.addEnemyForTesting(game.coreX + 30f, game.coreY, hp = 1f)
        killed.status[StatusCell.LAST_SOURCE] = (HitSource.IMPACT.ordinal + 1).toFloat()
        game.onEnemyKilled(killed)
        assertEquals(0, game.stellarMass)
        assertEquals(victim.maxHp - (SUPERNOVA_DAMAGE + SUPERNOVA_DAMAGE_PER_STACK * 14), victim.hp, 0.1f)
        assertEquals(MutableGameState.CORE_RADIUS, game.coreRadius)
        // Full mass fires on its own once the cooldown allows.
        game.characterRuntime.stellarMass = CharacterRuntime.MAX_STELLAR_MASS
        game.step()
        assertEquals(CharacterRuntime.MAX_STELLAR_MASS, game.stellarMass, "two-second cooldown")
        game.elapsed += 2f
        game.step()
        assertEquals(0, game.stellarMass)
    }

    @Test fun theEchoGhostRetracesThePathOneSecondBehindStrikesAtSpeedAndAbsorbsAShot() {
        val game = game(CoreShape.SPIRAL)
        repeat(CharacterRuntime.GHOST_DELAY_STEPS + 1) { game.moveAt(600f, 0f) }
        assertFalse(game.ghostActive)
        game.moveAt(600f, 0f)
        assertTrue(game.ghostActive)
        assertEquals(game.coreX - 600f, game.ghostX, 0.1f)
        assertEquals(600f, game.ghostSpeed, 1f)
        val enemy = game.addEnemyForTesting(game.ghostX + 5f, game.ghostY)
        game.moveAt(600f, 0f)
        val first = enemy.maxHp - enemy.hp
        assertTrue(first > 0f, "the ghost strikes what it passes")
        assertEquals(first.toDouble(), game.characterRuntime.abilityDamage, 0.01)
        // 0.4 s per enemy.
        enemy.x = game.ghostX + 5f; enemy.previousX = enemy.x
        game.moveAt(600f, 0f)
        assertEquals(first, enemy.maxHp - enemy.hp, 0.01f)
        // It absorbs one hostile shot, then waits 1.5 s.
        val shot = Projectile(x = game.ghostX, y = game.ghostY + 2f, vx = 0f, vy = -100f, radius = 5f, life = 1f, hostile = true,
            damage = 9f).apply { previousX = x; previousY = y + 10f }
        val second = Projectile(x = game.ghostX + 20f, y = game.ghostY + 2f, vx = 0f, vy = -100f, radius = 5f, life = 1f,
            hostile = true, damage = 9f).apply { previousX = x; previousY = y + 10f }
        game.projectiles += shot
        game.moveAt(0f, 0f)
        assertTrue(shot.spent)
        game.projectiles += second
        second.x = game.ghostX; second.previousX = game.ghostX
        game.moveAt(0f, 0f)
        assertFalse(second.spent)
    }

    @Test fun polarityFlipsOnRealTurnsAttractsAndRepelsIntoSlams() {
        val game = game(CoreShape.DIPOLE)
        assertEquals(Polarity.ATTRACT, game.polarity)
        val pulled = game.addEnemyForTesting(game.coreX + 200f, game.coreY)
        game.step()
        assertTrue(pulled.vx < 0f, "attract pulls toward the Core")
        // What the field moves flies on its momentum: steering leaves it alone while it is thrown.
        assertTrue(game.isThrownByPolarity(pulled))
        val pulledVelocity = pulled.vx
        game.steerEnemy(pulled, 78f, 0f, 2.1f, MutableGameState.FIXED_STEP)
        assertEquals(pulledVelocity, pulled.vx)
        game.onCharacterTurn()
        assertEquals(Polarity.REPEL, game.polarity)
        game.onCharacterTurn()
        assertEquals(Polarity.REPEL, game.polarity, "flips are at least two seconds apart")
        game.elapsed += 2f
        game.onCharacterTurn()
        assertEquals(Polarity.ATTRACT, game.polarity)
        game.elapsed += 2f
        game.onCharacterTurn()
        // A repelled enemy thrown into another slams.
        game.enemies.clear()
        val thrown = game.addEnemyForTesting(game.coreX + 100f, game.coreY)
        val struck = game.addEnemyForTesting(game.coreX + 120f, game.coreY)
        game.step()
        assertTrue(thrown.characterMarkTime > 0f)
        thrown.vx = 500f; struck.vx = 0f
        game.step()
        val slam = (SLAM_BASE_DAMAGE + SLAM_DAMAGE_PER_SPEED * 500f) * game.abilityPower()
        assertEquals(thrown.maxHp - slam, thrown.hp, 0.5f)
        assertEquals(struck.maxHp - slam, struck.hp, 0.5f)
        // The pair shared the closing momentum, so the contact does not slam again.
        game.step()
        assertEquals(thrown.maxHp - slam, thrown.hp, 0.5f)
        // Repel throws what the Core strikes, at the impact speed.
        game.enemies.clear()
        val rammed = game.addEnemyForTesting(game.coreX + 30f, game.coreY)
        game.onCharacterImpact(rammed, DamageResult(10f, false), impactSpeed = 600f, multiplier = 1f)
        assertEquals(600f * game.knockbackTaken(rammed), rammed.vx, 0.01f)
        assertTrue(game.isThrownByPolarity(rammed))
    }

    @Test fun pentagonRewardsOnBeatDashesImpactsAndActivations() {
        val game = game(CoreShape.PENTAGON)
        game.elapsed = BEAT_PERIOD * 4f + 0.05f
        assertTrue(game.isOnBeat())
        assertEquals(BEAT_ACTIVATION_MULTIPLIER, game.characterActivationMultiplier())
        val onBeatCooldown = game.cooldown(1f)
        game.elapsed = BEAT_PERIOD * 4f + 0.3f
        assertFalse(game.isOnBeat())
        assertEquals(onBeatCooldown * BEAT_ACTIVATION_MULTIPLIER, game.cooldown(1f), 1e-4f)
        // An on-beat Dash costs 60 % of the heat and pushes harder.
        game.elapsed = BEAT_PERIOD * 5f
        game.heat = 0f; game.velocityX = 0f; game.velocityY = 0f
        game.pointerX = game.screenWidth; game.pointerY = game.screenHeight * 0.5f
        game.performDash()
        assertEquals(game.effectiveDashHeatCost() * BEAT_DASH_HEAT, game.heat, 1e-3f)
        assertEquals(game.effectiveDashImpulse() * BEAT_DASH_IMPULSE, game.velocityX, 1f)
        assertEquals(1, game.characterRuntime.beatStreak)
        // A shot phased by the on-beat Dash pays no Matter.
        val matter = game.runMatter
        game.hostileShotAt(30f)
        game.resolveProjectileHits()
        assertEquals(matter, game.runMatter)
        // An on-beat impact pulses once per beat and raises the streak on the next beat.
        game.elapsed = BEAT_PERIOD * 6f
        val bystander = game.addEnemyForTesting(game.coreX + 50f, game.coreY)
        val struck = game.addEnemyForTesting(game.coreX + 10f, game.coreY)
        game.onCharacterImpact(struck, DamageResult(10f, false), 500f, game.characterImpactMultiplier())
        assertTrue(bystander.hp < bystander.maxHp)
        assertEquals(2, game.characterRuntime.beatStreak)
        // Two silent beats end the streak.
        game.elapsed = BEAT_PERIOD * 8f + 0.2f
        game.step(0f)
        assertEquals(0, game.characterRuntime.beatStreak)
    }

    @Test fun crescentBlinksOnChargesKeepsSpeedStopsShortOfTheSingularityAndScarsThePath() {
        val game = game(CoreShape.CRESCENT)
        game.heat = 0f
        game.velocityX = 500f; game.velocityY = 0f
        game.pointerX = game.screenWidth; game.pointerY = game.screenHeight * 0.5f
        val crossed = game.addEnemyForTesting(game.coreX + 120f, game.coreY)
        val startX = game.coreX
        game.performDash()
        assertEquals(startX + BLINK_RANGE, game.coreX, 0.01f)
        assertEquals(game.coreX, game.previousCoreX)
        assertEquals(500f * BLINK_SPEED_RETENTION, game.velocityX, 0.01f)
        assertEquals(0f, game.heat, "Blink costs no heat")
        assertEquals(1, game.characterRuntime.blinkCharges)
        assertEquals(1, game.pendingDashHits, "crossing an enemy is a Dash hit")
        assertTrue(game.blinkThisStep)
        assertEquals(startX, game.blinkFromX)
        assertEquals(1, game.characterRuntime.scarCount)
        // The scar detonates after 0.4 s with Hollow.
        repeat((0.45f / MutableGameState.FIXED_STEP).toInt()) { game.step() }
        assertEquals(crossed.maxHp - SCAR_DAMAGE, crossed.hp, 0.05f)
        assertTrue(stacks(crossed, StatusElement.VOID) >= 1)
        // The singularity stops it 60 short.
        game.coreX = 0f; game.coreY = 0f; game.cameraX = 0f; game.cameraY = 0f
        game.pointerX = game.screenWidth * 0.5f + 100f
        game.performDash()
        assertEquals(40f, game.coreX, 0.01f)
        assertEquals(0, game.characterRuntime.blinkCharges)
        assertFalse(game.buildDashReady())
        repeat((BLINK_RECHARGE_SECONDS / MutableGameState.FIXED_STEP).toInt() + 2) { game.step() }
        assertEquals(1, game.characterRuntime.blinkCharges)
        assertTrue(game.buildDashReady())
    }

    @Test fun hiveBuildsDronesFromDataPickupsAndTheyDartAtNearbyEnemies() {
        val game = game(CoreShape.HEXAGON)
        repeat(4) {
            game.pickupsCollected++
            game.onCharacterDataPickup()
        }
        assertTrue(game.droneCount >= 1, "every fourth pickup is guaranteed")
        repeat(40) {
            game.pickupsCollected++
            game.onCharacterDataPickup()
        }
        assertEquals(CharacterRuntime.MAX_DRONES, game.droneCount)
        val enemy = game.addEnemyForTesting(game.coreX + 150f, game.coreY)
        repeat(60) { game.step() }
        assertTrue(enemy.hp < enemy.maxHp)
        assertTrue(game.characterRuntime.abilityDamage > 0.0)
        for (index in 0 until game.droneCount) {
            assertTrue(length(game.droneX(index) - game.coreX, game.droneY(index) - game.coreY) <= DRONE_ORBIT_RADIUS + 200f)
        }
        // Drones expire after twelve seconds unless a pickup refreshes them.
        repeat((12.5f / MutableGameState.FIXED_STEP).toInt()) { game.step() }
        assertEquals(0, game.droneCount)
    }

    @Test fun everyCharacterStepsAndForksWithoutSharingMutableState() {
        for (shape in CoreShape.entries) {
            val game = game(shape)
            game.addEnemyForTesting(80f, 0f)
            repeat(200) { index -> game.moveAt(cos(index * 0.03f) * 700f, sin(index * 0.03f) * 700f) }
            val before = game.characterRuntime.toString()
            val fork = game.copyForReduction()
            repeat(30) { fork.moveAt(800f, 0f) }
            assertEquals(before, game.characterRuntime.toString(), "$shape fork leaked into its source")
            assertEquals(GamePhase.RUNNING, fork.phase)
        }
    }
}
