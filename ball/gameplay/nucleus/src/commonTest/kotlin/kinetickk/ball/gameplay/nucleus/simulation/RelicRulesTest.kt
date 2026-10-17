// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One focused rule rewrite and liability per relic, plus the slot rules around them. */
class RelicRulesTest {
    @Test
    fun perpetualVectorTurnsCappedSpeedIntoDamageButWeakensSlowImpacts() {
        val state = buildTestState(RelicId.PERPETUAL_VECTOR)
        state.setVelocityForTesting(1_400f, 0f)
        assertEquals(surge(0.24f), state.relicSurge(), 0.0001f)
        state.setVelocityForTesting(3_000f, 0f)
        assertEquals(surge(0.48f), state.relicSurge(), 0.0001f, "Speed counts only up to 2,200")

        state.setVelocityForTesting(0f, 0f)
        val slow = state.dealHit(state.dummy(), 100f, HitSource.IMPACT, canCrit = false).amount
        assertEquals(70f, slow, 0.01f)
    }

    @Test
    fun blinkDriveStrikesTheLineOnceAndNeverCrossesTheSingularity() {
        val state = buildTestState(RelicId.BLINK_DRIVE, RelicId.VERDANT_ROT)
        assertEquals(state.dashHeatCost * 0.65f, state.effectiveDashHeatCost(), 0.0001f)
        val onLine = state.dummy(x = 130f)
        val offLine = state.dummy(x = 130f, y = 200f)
        state.updatePointer(640f + 600f, 360f)
        state.setVelocityForTesting(1_000f, 0f)

        assertTrue(state.relicOnDash(1f, 0f))
        assertEquals(260f, state.coreX, 0.01f)
        assertEquals(350f, state.velocityX, 0.01f, "A Blink keeps only 35 % of the speed")
        assertTrue(onLine.hp < onLine.maxHp)
        assertEquals(offLine.maxHp, offLine.hp)
        // Relic on-hit rules apply once per Blink strike: Verdant Rot's two Entropy, not four.
        assertEquals(2, onLine.stacksOf(StatusElement.ENTROPY))

        val short = buildTestState(RelicId.BLINK_DRIVE)
        short.updatePointer(640f + 100f, 360f)
        assertTrue(short.relicOnDash(1f, 0f))
        assertEquals(40f, short.coreX, 0.01f, "The Blink stops 60 u short of the singularity")

        val blocked = buildTestState(RelicId.BLINK_DRIVE)
        blocked.updatePointer(640f + 80f, 360f)
        assertFalse(blocked.relicOnDash(1f, 0f), "Too close to the singularity: an ordinary Dash")
        assertEquals(0f, blocked.coreX)
    }

    @Test
    fun gyreEngineRingsOnTurnsWithTheDominantElementButPolarityOnlyRecoversByTurning() {
        val state = buildTestState(RelicId.GYRE_ENGINE).apply { own("Igniter") }
        val near = state.dummy(x = 100f)
        state.relicOnTurn()
        assertTrue(near.hp < near.maxHp)
        assertEquals(2, near.stacksOf(StatusElement.THERMAL))
        val afterFirst = near.hp
        state.elapsed = 0.5f
        state.relicOnTurn()
        assertEquals(afterFirst, near.hp, "The ring rests for a second")

        state.polarityStability = 0.5f
        state.updatePolarityStability(0.5f)
        assertEquals(0.5f, state.polarityStability, "Polarity no longer recovers on its own")
        val free = buildTestState().apply { polarityStability = 0.5f }
        free.updatePolarityStability(0.5f)
        assertTrue(free.polarityStability > 0.5f)
    }

    @Test
    fun glassCometGrantsCooldownBoundInvulnerabilityAndRemovesEveryDefense() {
        val state = buildTestState(RelicId.GLASS_COMET)
        assertEquals(surge(1f), state.relicSurge(), 0.0001f)
        state.setVelocityForTesting(1_000f, 0f)
        state.relicOnKill(state.dummy(), HitSource.IMPACT)
        assertEquals(GLASS_INVULNERABILITY_SECONDS, state.invulnerableUntil, 0.0001f)

        state.elapsed = 1f
        state.relicOnKill(state.dummy(), HitSource.IMPACT)
        assertEquals(GLASS_INVULNERABILITY_SECONDS, state.invulnerableUntil, 0.0001f, "1.5 s cooldown")
        state.elapsed = 1.5f
        state.relicOnKill(state.dummy(), HitSource.WEAPON)
        assertEquals(GLASS_INVULNERABILITY_SECONDS, state.invulnerableUntil, 0.0001f, "Only impact kills count")
        state.relicOnKill(state.dummy(), HitSource.IMPACT)
        assertEquals(1.5f + GLASS_INVULNERABILITY_SECONDS, state.invulnerableUntil, 0.0001f)

        val fragile = buildTestState(RelicId.GLASS_COMET).apply {
            maxShield = 50f
            shield = 50f
            damageReduction = 0.5f
        }
        assertEquals(0f, fragile.shieldCapacity())
        fragile.takeDamage(10f, kind = IncomingDamage.PROJECTILE)
        assertEquals(fragile.maxHp - 20f, fragile.hp, 0.001f)
    }

    @Test
    fun executionersPrismMakesCritsDeterministicAndWeakensEveryOtherHit() {
        val state = buildTestState(RelicId.EXECUTIONERS_PRISM)
        val healthy = state.dummy()
        val plain = state.dealHit(healthy, 100f, HitSource.WEAPON)
        assertFalse(plain.critical)
        assertEquals(80f, plain.amount, 0.01f)

        val wounded = state.dummy().apply { hp = maxHp * 0.3f }
        val execution = state.dealHit(wounded, 100f, HitSource.WEAPON)
        assertTrue(execution.critical)
        assertEquals(100f * (1.5f + 0.05f * 3f), execution.amount, 0.01f)

        val nearBreak = state.dummy().apply { status[StatusCell.STACKS + StatusElement.ION.ordinal] = 6f }
        assertTrue(state.dealHit(nearBreak, 100f, HitSource.WEAPON).critical)
        val frozen = state.dummy().apply { status[StatusCell.FROZEN_UNTIL] = 5f }
        assertTrue(state.dealHit(frozen, 100f, HitSource.WEAPON).critical)

        // Critical chance converts only up to 50 %.
        state.critChance = 0.75f
        assertEquals(100f * (1.5f + 0.5f * 3f), state.dealHit(wounded, 100f, HitSource.WEAPON).amount, 0.01f)
    }

    @Test
    fun echoChamberRepeatsWeaponHitsLaterButHalvesImpacts() {
        val state = buildTestState(RelicId.ECHO_CHAMBER)
        val target = state.dummy()
        val hit = state.dealWeaponDamage(target, 100f).amount
        val echo = state.echoHits.single()
        assertEquals(hit * 0.5f, echo.damage, 0.01f)
        assertEquals(0.35f, echo.delay, 0.0001f)

        val hpBeforeEcho = target.hp
        repeat(60) { state.updateRelicRuntime(MutableGameState.FIXED_STEP) }
        assertTrue(state.echoHits.isEmpty())
        assertEquals(hpBeforeEcho - hit * 0.5f, target.hp, 0.05f)

        assertEquals(50f, state.dealHit(state.dummy(), 100f, HitSource.IMPACT, canCrit = false).amount, 0.01f)
    }

    @Test
    fun furnaceCovenantKeepsDashOnlineAndBurnsIntegrityForExcessHeat() {
        val state = buildTestState(RelicId.FURNACE_COVENANT)
        state.heat = MutableGameState.MAX_HEAT
        state.overheated = true
        assertTrue(state.buildDashReady())
        assertEquals(surge(0.48f), state.relicSurge(), 0.0001f)
        state.heat = 40f
        assertEquals(1f, state.relicSurge())

        state.heat = 75f
        val target = state.dummy()
        state.dealHit(target, 10f, HitSource.WEAPON, canCrit = false)
        state.dealHit(target, 10f, HitSource.WEAPON, canCrit = false)
        assertEquals(1, target.stacksOf(StatusElement.THERMAL), "Above 70 heat hits apply Thermal, at most every 0.15 s")

        state.heat = 90f
        state.updateRelicRuntime(1f)
        assertEquals(state.maxHp - 3f, state.hp, 0.001f)
        assertEquals(state.coolingRate * 0.45f, state.effectiveCooling(), 0.001f)

        state.heat = 90f
        val beforeDash = state.hp
        state.performDash()
        assertEquals(MutableGameState.MAX_HEAT, state.heat)
        assertEquals(beforeDash - (90f + state.effectiveDashHeatCost() - MutableGameState.MAX_HEAT) * 0.5f, state.hp, 0.001f)
    }

    @Test
    fun cinderCrownBanksThermalForAnEarlyIgniteThatBurnsNearbyIntegrity() {
        val state = buildTestState(RelicId.CINDER_CROWN)
        assertEquals(5, state.breakThreshold(StatusElement.THERMAL))
        val target = state.dummy(x = 50f)
        val neighbor = state.dummy(x = 120f)
        state.applyStatus(target, StatusElement.THERMAL, 4, generation = 0)
        repeat(120) {
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateStatuses(MutableGameState.FIXED_STEP)
        }
        assertEquals(target.maxHp, target.hp, "Thermal no longer burns over time")
        val bank = target.status[StatusCell.CINDER_BANK]
        assertTrue(bank > 0f)

        state.applyStatus(target, StatusElement.THERMAL, 5, generation = 0)
        assertEquals(0f, target.status[StatusCell.CINDER_BANK], "Ignite releases the bank")
        assertTrue(target.maxHp - target.hp > bank)
        assertEquals(2, neighbor.stacksOf(StatusElement.THERMAL), "A direct Ignite spreads two Thermal")
        assertEquals(state.maxHp * 0.96f, state.hp, 0.001f, "An Ignite next to the Core burns 4 % integrity")
    }

    @Test
    fun overdriveCascadeExtendsOverdriveGeometricallyAndWeakensDamageOutsideIt() {
        val state = buildTestState(RelicId.OVERDRIVE_CASCADE)
        state.gainOverdrive(10f)
        assertEquals(16f, state.overdriveCharge, 0.001f)

        state.overdriveTime = 7f
        val victim = state.dummy()
        state.relicOnKill(victim, HitSource.WEAPON)
        assertEquals(7.5f, state.overdriveTime, 0.0001f)
        state.relicOnKill(victim, HitSource.WEAPON)
        assertEquals(7.9f, state.overdriveTime, 0.0001f)
        repeat(200) { state.relicOnKill(victim, HitSource.WEAPON) }
        assertTrue(state.overdriveExtension <= OVERDRIVE_EXTENSION_CAP)
        assertTrue(state.overdriveExtension > OVERDRIVE_EXTENSION_CAP - 0.01f)

        state.overdriveTime = 0f
        assertEquals(70f, state.dealHit(state.dummy(), 100f, HitSource.WEAPON, canCrit = false).amount, 0.01f)
    }

    @Test
    fun overdriveBurnoutBlocksChargeForThreeSecondsAfterItEnds() {
        val state = buildTestState()
        state.overdriveTime = 0.001f
        state.elapsed = 5f
        state.updateOverdrive(0.01f)
        assertEquals(0f, state.overdriveTime)
        assertEquals(5f + OVERDRIVE_BURNOUT_SECONDS, state.overdriveBurnoutUntil, 0.0001f)
        state.gainOverdrive(50f)
        assertEquals(0f, state.overdriveCharge)
        state.elapsed = 8.01f
        state.gainOverdrive(50f)
        assertEquals(50f, state.overdriveCharge, 0.001f)
    }

    @Test
    fun absoluteZeroChillsImpactsAndShattersFrozenTargetsButSmothersThermal() {
        val state = buildTestState(RelicId.ABSOLUTE_ZERO)
        val struck = state.dummy()
        state.dealHit(struck, 10f, HitSource.IMPACT, canCrit = false)
        assertEquals(3, struck.stacksOf(StatusElement.CRYO))

        val frozen = state.dummy(x = 800f).apply { status[StatusCell.FROZEN_UNTIL] = 2f }
        val bystander = state.dummy(x = 860f)
        val shatter = state.dealHit(frozen, 100f, HitSource.WEAPON, canCrit = false)
        assertEquals(250f, shatter.amount, 0.01f)
        assertFalse(state.isFrozen(frozen), "A shatter thaws the target")
        assertTrue(frozen.status[StatusCell.FREEZE_IMMUNE_UNTIL] > state.elapsed)
        assertEquals(2, bystander.stacksOf(StatusElement.CRYO))
        assertEquals(1, bystander.generationOf(StatusElement.CRYO))

        val burning = state.dummy(x = -800f)
        state.applyStatus(burning, StatusElement.THERMAL, 5, generation = 0)
        assertEquals(0, burning.stacksOf(StatusElement.THERMAL))
        assertEquals(2, burning.stacksOf(StatusElement.CRYO), "Half the Thermal becomes Cryo, the rest is lost")
        assertEquals(0.6f, state.overdriveGainFactor(), 0.0001f)

        val dash = state.dummy(x = 100f)
        state.relicOnDash(1f, 0f)
        assertEquals(3, dash.stacksOf(StatusElement.CRYO))
    }

    @Test
    fun stasisAnchorCatchesAtMostTwentyFourBulletsAndReleasesThemAsShards() {
        val state = buildTestState(RelicId.STASIS_ANCHOR)
        repeat(30) { index ->
            state.projectiles += Projectile(x = 50f + index, y = 0f, vx = 0f, vy = 0f, radius = 4f, life = 5f, hostile = true)
        }
        state.setBrake(true)
        assertEquals(1f, state.buildActivationBonus(), 0.0001f)
        state.updateRelicRuntime(MutableGameState.FIXED_STEP)
        assertEquals(STASIS_MAX_BANK, state.stasisBank)
        assertEquals(30 - STASIS_MAX_BANK, state.projectiles.count { it.hostile })
        val slowed = state.dummy(x = 100f)
        assertEquals(1f - 0.45f, state.enemyMotionScale(slowed), 0.0001f)

        state.setBrake(false)
        assertEquals(0.7f, state.buildActivationBonus(), 0.0001f, "Activation −30 % while not braking")
        state.relicOnBrakeRelease()
        assertEquals(0, state.stasisBank)
        assertEquals(STASIS_MAX_BANK, state.projectiles.count { !it.hostile })

        // The meter holds for three seconds, then locks.
        state.setBrake(true)
        repeat(4) {
            state.elapsed += 1f
            state.updateRelicRuntime(1f)
        }
        assertEquals(0f, state.stasisMeter)
        assertFalse(state.stasisActive())
    }

    @Test
    fun stillWatersShotsDealElementlessDamageAndApplyCryoAfterIt() {
        val state = buildTestState(RelicId.STASIS_ANCHOR, RelicId.ABSOLUTE_ZERO)
        assertTrue(state.build.pairing(RelicPairingId.STILL_WATERS))
        // An Elite immune to Cryo.
        assertTrue(state.spawnEnemy(EnemyType.ELITE))
        val elite = state.enemies.last()
        assignAspect(elite, StatusElement.CRYO.ordinal, immune = true)
        val drifter = state.dummy(x = elite.x + 2_000f, y = elite.y)
        fun shootAt(target: kinetickk.ball.gameplay.nucleus.model.Enemy): Float {
            state.projectiles.clear()
            state.stasisBank = 1
            state.relicOnBrakeRelease()
            val shot = state.projectiles.single()
            assertEquals(StatusElement.CRYO.ordinal, shot.statusElement)
            shot.x = target.x
            shot.y = target.y
            shot.previousX = target.x
            shot.previousY = target.y
            val before = target.hp
            state.resolveProjectileHits()
            assertEquals(shot.damage * target.weaponTaken, before - target.hp, 0.01f, "${target.type} meets its weapon frame")
            return shot.damage
        }
        assertTrue(shootAt(elite) > 0f)
        assertEquals(0, elite.stacksOf(StatusElement.CRYO), "Only the Cryo rider is refused")
        shootAt(drifter)
        assertEquals(2, drifter.stacksOf(StatusElement.CRYO))
    }

    @Test
    fun theGyreRingDealsElementlessImpactDamageAndCarriesTheDominantElementAsARider() {
        val state = buildTestState(RelicId.GYRE_ENGINE).apply { own("Igniter") }
        assertEquals(StatusElement.THERMAL, state.build.dominant)
        // An Elite immune to Thermal.
        assertTrue(state.spawnEnemy(EnemyType.ELITE))
        val elite = state.enemies.last().apply { x = state.coreX + 100f; y = state.coreY }
        assignAspect(elite, StatusElement.THERMAL.ordinal, immune = true)
        val drifter = state.dummy(x = state.coreX - 100f, y = state.coreY)
        val ring = state.impactPower(state.speed) * state.relicValue(RelicId.GYRE_ENGINE, 0) / 100f
        state.relicOnTurn()
        assertTrue(ring > 0f)
        assertEquals(ring * elite.weaponTaken, elite.maxHp - elite.hp, 0.01f, "The Thermal-immune Elite still takes the ring")
        assertEquals(0, elite.stacksOf(StatusElement.THERMAL))
        assertEquals(2, drifter.stacksOf(StatusElement.THERMAL))
    }

    @Test
    fun stormheartChargesAtSpeedDischargesOnImpactAndPunishesHitsWhileCharged() {
        val state = buildTestState(RelicId.STORMHEART_CAPACITOR)
        state.setVelocityForTesting(800f, 0f)
        repeat(8) { state.updateRelicRuntime(0.5f) }
        assertEquals(1f, state.stormCharge, 0.0001f)

        val struck = state.dummy(x = 400f)
        val chained = List(6) { index -> state.dummy(x = 450f + index * 40f) }
        state.dealHit(struck, 10f, HitSource.IMPACT, canCrit = false)
        assertEquals(0f, state.stormCharge)
        assertEquals(5, chained.count { it.stacksOf(StatusElement.ION) == 3 })

        state.stormCharge = 0.6f
        state.takeDamage(1f, kind = IncomingDamage.PROJECTILE)
        assertEquals(state.maxHp - 1f - state.maxHp * 0.1f, state.hp, 0.001f)
        assertEquals(0f, state.stormCharge)
    }

    @Test
    fun aegisInversionTurnsHealingIntoShieldAndNovasOnABreakButNeverRestoresIntegrity() {
        val state = buildTestState(RelicId.AEGIS_INVERSION)
        assertEquals(state.maxHp * 0.6f, state.shieldCapacity(), 0.001f)
        state.hp = 50f
        state.heal(20f, HealSource.ITEM)
        assertEquals(50f, state.hp)
        assertEquals(20f, state.shield, 0.001f)

        state.shield = 0f
        state.addShield(40f)
        val near = state.dummy(x = 100f)
        state.takeDamage(50f, kind = IncomingDamage.PROJECTILE)
        assertEquals(0f, state.shield)
        assertEquals(near.maxHp - 40f * 1.5f * sqrt(state.runScale()), near.hp, 0.01f)

        state.timeSinceDamage = 10f
        state.shieldRechargeDelay = 0f
        state.updateSurvival(1f)
        assertEquals(0f, state.shield, "Shields no longer recharge on their own")
    }

    @Test
    fun neutronHeartAddsMassAndSoftensSlowCollisionsAtTheCostOfDashAndMagnetism() {
        val state = buildTestState(RelicId.NEUTRON_HEART)
        assertEquals(1.8f, state.effectiveMass(), 0.0001f)
        assertEquals(state.dashImpulse * 0.65f, state.effectiveDashImpulse(), 0.001f)
        assertEquals(state.magnetStrength * 0.8f, state.effectiveMagnetism(), 0.001f)

        fun contactDamage(relics: Array<RelicId>): Float {
            val probe = buildTestState(*relics)
            probe.addEnemyForTesting(x = 10f, y = 0f, hp = 1_000_000f)
            probe.resolveEnemyCoreCollisions()
            return probe.maxHp - probe.hp
        }
        val plain = contactDamage(emptyArray())
        assertTrue(plain > 0f)
        assertEquals(plain * 0.5f, contactDamage(arrayOf(RelicId.NEUTRON_HEART)), 0.001f)
    }

    @Test
    fun eventHorizonDragsEnemiesInAndPunishesGraviticTargetsButDoublesContactRisk() {
        val state = buildTestState(RelicId.EVENT_HORIZON)
        val pulled = state.dummy(x = 200f)
        state.updateRelicRuntime(0.1f)
        assertTrue(pulled.vx < 0f)
        val far = state.dummy(x = 400f)
        state.updateRelicRuntime(0.1f)
        assertEquals(0f, far.vx)

        pulled.status[StatusCell.STACKS + StatusElement.GRAVITIC.ordinal] = 1f
        assertEquals(0.04f + 0.4f, state.vulnerability(pulled, HitSource.IMPACT), 0.0001f)
        assertEquals(0f, state.vulnerability(pulled, HitSource.WEAPON), 0.0001f)

        // The liability removes the latched and level-up vacuums; the radius itself is unchanged.
        assertEquals(state.pickupRadius, state.effectivePickupRadius(), 0.001f)
        assertFalse(state.latchedVacuumActive())
        state.takeDamage(10f, kind = IncomingDamage.CONTACT)
        assertEquals(state.maxHp - 15f, state.hp, 0.001f)
    }

    @Test
    fun verdantRotHealsFromNearbyRotOnlyAndMakesItDangerous() {
        val state = buildTestState(RelicId.VERDANT_ROT)
        val struck = state.dummy()
        state.dealHit(struck, 10f, HitSource.IMPACT, canCrit = false)
        assertEquals(2, struck.stacksOf(StatusElement.ENTROPY))

        state.hp = 50f
        state.heal(20f, HealSource.REPAIR)
        assertEquals(50f, state.hp, "All other healing is disabled")
        val rotting = state.dummy(x = 100f).apply { status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 4f }
        state.updateRelicRuntime(1f)
        assertEquals(51f, state.hp, 0.001f)
        rotting.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 40f
        state.updateRelicRuntime(1f)
        assertEquals(51f + VERDANT_CAP, state.hp, 0.001f)

        rotting.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 10f
        state.hp = 50f
        state.takeDamage(10f, kind = IncomingDamage.PROJECTILE)
        assertEquals(50f - 11.5f, state.hp, 0.001f)
    }

    @Test
    fun pandemicCodexPassesStatusesToTwoNeighborsAndHastensCarriers() {
        val state = buildTestState(RelicId.PANDEMIC_CODEX)
        val carrier = state.dummy(x = 0f).apply { status[StatusCell.STACKS + StatusElement.ION.ordinal] = 5f }
        val first = state.dummy(x = 50f)
        val second = state.dummy(x = 100f)
        val third = state.dummy(x = 150f)
        assertEquals(1.2f, state.enemyMotionScale(carrier), 0.0001f)

        carrier.hp = 0f
        state.sweepKills()
        assertEquals(3, first.stacksOf(StatusElement.ION))
        assertEquals(1, first.generationOf(StatusElement.ION))
        assertEquals(3, second.stacksOf(StatusElement.ION))
        assertEquals(0, third.stacksOf(StatusElement.ION))
    }

    @Test
    fun agonyScepterDeepensWithRankAndHeatsEveryActivation() {
        val state = buildTestState(RelicId.AGONY_SCEPTER, rebirth = 1)
        assertEquals(1, state.agonyPower())
        state.acquireRelic(RelicId.AGONY_SCEPTER)
        assertEquals(3, state.agonyPower())
        state.onWeaponActivation()
        assertEquals(6f, state.heat, 0.001f)
        state.equipRunWeapon(WeaponId.FLUX_WAKE)
        state.heat = 0f
        state.updateRelicRuntime(1f)
        assertEquals(5f, state.heat, 0.001f)
    }

    @Test
    fun hollowCrownRewardsEmptySlotsNeverRanksUpAndLeavesYouExposed() {
        val state = buildTestState(RelicId.HOLLOW_CROWN, rebirth = 3)
        assertEquals(surge(3 * 0.35f), state.relicSurge(), 0.0001f)
        assertEquals(1f + 3 * 0.08f, state.buildActivationBonus(), 0.0001f)
        val matter = state.runMatter
        state.acquireRelic(RelicId.HOLLOW_CROWN)
        assertEquals(1, state.relicRank(RelicId.HOLLOW_CROWN))
        assertTrue(state.runMatter > matter, "A duplicate crown is salvaged")
        assertFalse(state.canMeld(RelicId.HOLLOW_CROWN))
        assertTrue(state.offerableRelics().none { it.id == RelicId.HOLLOW_CROWN })

        state.takeDamage(10f, kind = IncomingDamage.PROJECTILE)
        val expected = 10f * state.rebirthProfile.incomingDamageMultiplier * (1f + 3 * 0.1f)
        assertEquals(state.maxHp - expected, state.hp, 0.001f, "Each empty slot adds 10 % damage taken")
    }

    @Test
    fun ouroborosSealAnswersOneLethalBlowThenLocksItsSlot() {
        val state = buildTestState(RelicId.OUROBOROS_SEAL, rebirth = 5)
        assertEquals(0.75f, state.dataGainLiability())
        state.acquireRelic(RelicId.GYRE_ENGINE)
        val near = state.dummy(x = 100f)
        state.elapsed = 10f
        state.hp = 5f
        state.takeDamage(50f, kind = IncomingDamage.PROJECTILE)

        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals(state.maxHp, state.hp)
        assertTrue(state.ouroborosSpent)
        assertTrue(state.invulnerableUntil > state.elapsed)
        assertTrue(near.hp < near.maxHp, "The seal releases a nova")
        assertFalse(state.build.upside(RelicId.OUROBOROS_SEAL))
        assertTrue(state.build.liability(RelicId.OUROBOROS_SEAL))
        assertTrue(state.offerableRelics().none { it.id == RelicId.OUROBOROS_SEAL })
        assertFalse(state.canMeld(RelicId.OUROBOROS_SEAL))

        // The slot stays locked for two minutes; the bind choice omits it.
        state.replaceRelic(0, RelicId.BLINK_DRIVE)
        assertEquals(RelicId.OUROBOROS_SEAL, state.equippedRelics[0].id)
        state.phase = GamePhase.CHOICE
        state.pendingBindingRelic = RelicId.BLINK_DRIVE
        state.pendingRelicBindAction = RelicChoiceAction.REPLACE
        state.openRelicBindChoice()
        assertEquals(listOf(1), state.choices.mapNotNull { it.relicSlot })
        state.phase = GamePhase.RUNNING

        state.elapsed = 10f + OUROBOROS_LOCK_SECONDS + 0.1f
        state.replaceRelic(0, RelicId.BLINK_DRIVE)
        assertEquals(RelicId.BLINK_DRIVE, state.equippedRelics[0].id)

        // Spent is run-wide: another lethal blow ends the run.
        state.hurtCooldown = 0f
        state.invulnerableUntil = 0f
        state.hp = 5f
        state.takeDamage(50f, kind = IncomingDamage.PROJECTILE)
        assertEquals(GamePhase.GAME_OVER, state.phase)
    }

    @Test
    fun replacedRelicLeavesItsLiabilityBehindForTheSeveranceWindow() {
        val state = buildTestState(RelicId.GLASS_COMET)
        state.elapsed = 5f
        state.replaceRelic(0, RelicId.GYRE_ENGINE)
        assertFalse(state.build.upside(RelicId.GLASS_COMET))
        assertTrue(state.build.liability(RelicId.GLASS_COMET))
        state.takeDamage(10f, kind = IncomingDamage.PROJECTILE)
        assertEquals(state.maxHp - 20f, state.hp, 0.001f, "The severed Glass Comet still doubles damage")

        state.elapsed = 5f + state.content.build.policy.severanceSeconds + 0.01f
        state.updateRelicRuntime(MutableGameState.FIXED_STEP)
        assertTrue(state.severedRelics.isEmpty())
        assertFalse(state.build.liability(RelicId.GLASS_COMET))
    }

    @Test
    fun anOpenSlotOffersLeaveHollowAndAFullMatrixOffersMeldInstead() {
        val state = buildTestState(RelicId.GYRE_ENGINE)
        state.openRelicChoiceForTesting()
        assertEquals(ChoiceType.RELIC, state.choiceType)
        val decline = state.choices.last()
        assertEquals(RelicChoiceAction.DECLINE, decline.relicAction)
        val matter = state.runMatter
        state.choose(state.choices.lastIndex)
        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals(matter + 8L, state.runMatter)
        assertEquals(1, state.equippedRelics.size)

        val full = buildTestState(RelicId.GYRE_ENGINE, RelicId.GLASS_COMET, RelicId.ECHO_CHAMBER, RelicId.NEUTRON_HEART)
        full.openRelicChoiceForTesting()
        assertEquals(RelicChoiceAction.MELD, full.choices.last().relicAction)
        assertTrue(full.choices.none { it.relicAction == RelicChoiceAction.DECLINE })
    }

    @Test
    fun overdriveGainBonusesAddUpButCapAtTwoAndAHalf() {
        val state = buildTestState(RelicId.OVERDRIVE_CASCADE)
        state.overdriveGain = 3f
        state.gainOverdrive(10f)
        assertEquals(10f * state.content.build.policy.overdriveGainCap, state.overdriveCharge, 0.001f)
        assertEquals(2.5f, state.content.build.policy.overdriveGainCap)

        // Absolute Zero's liability applies after the cap.
        val smothered = buildTestState(RelicId.OVERDRIVE_CASCADE, RelicId.ABSOLUTE_ZERO)
        smothered.overdriveGain = 3f
        smothered.gainOverdrive(10f)
        assertEquals(15f, smothered.overdriveCharge, 0.001f)
    }

    @Test
    fun verdantRotDamageTakenFromNearbyRotIsCappedAtThirtyPercent() {
        val state = buildTestState(RelicId.VERDANT_ROT)
        val rotting = state.dummy(x = 100f)
        val distant = state.dummy(x = 200f).apply { status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 10f }
        fun taken(stacks: Int): Float {
            rotting.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = stacks.toFloat()
            state.hp = 50f
            state.hurtCooldown = 0f
            state.takeDamage(10f, kind = IncomingDamage.PROJECTILE)
            return 50f - state.hp
        }
        assertEquals(10f, taken(0), 0.001f, "Rot beyond 160 u does not count")
        assertEquals(11.5f, taken(10), 0.001f)
        assertEquals(13f, taken(20), 0.001f)
        assertEquals(13f, taken(40), 0.001f)
        assertEquals(10, distant.stacksOf(StatusElement.ENTROPY))
    }

    @Test
    fun cinderCrownBurnsAtMostOncePerThreeQuartersOfASecondAndOnlyDirectIgnitesSpread() {
        val state = buildTestState(RelicId.CINDER_CROWN)
        fun igniteAt(time: Float, x: Float, y: Float, generation: Int = 0) = state.dummy(x = x, y = y).also { target ->
            state.elapsed = time
            state.applyStatus(target, StatusElement.THERMAL, 5, generation)
            assertEquals(0, target.stacksOf(StatusElement.THERMAL), "Ignite at $time")
        }
        igniteAt(0f, 60f, 0f)
        assertEquals(96f, state.hp, 0.001f)
        igniteAt(0.5f, -60f, 0f)
        assertEquals(96f, state.hp, 0.001f, "The self-burn rests for 0.75 s")
        igniteAt(0.8f, 0f, 60f)
        assertEquals(92f, state.hp, 0.001f)

        // A cascaded Ignite neither spreads nor burns the Core.
        val neighbor = state.dummy(x = 0f, y = -140f)
        igniteAt(2f, 0f, -60f, generation = 1)
        assertEquals(0, neighbor.stacksOf(StatusElement.THERMAL))
        assertEquals(92f, state.hp, 0.001f)
        igniteAt(4f, 0f, -60f)
        assertEquals(2, neighbor.stacksOf(StatusElement.THERMAL), "A direct Ignite spreads")
    }

    @Test
    fun aegisInversionNovasRestThreeSeconds() {
        val state = buildTestState(RelicId.AEGIS_INVERSION)
        val near = state.dummy(x = 100f)
        fun nova(time: Float): Boolean {
            state.elapsed = time
            val before = near.hp
            state.relicOnShieldBreak(absorbed = 40f)
            return near.hp < before
        }
        assertTrue(nova(0f))
        assertFalse(nova(2.9f))
        assertTrue(nova(3.1f))
    }

    private fun surge(bonus: Float): Float = 1f + 3f * bonus / (3f + bonus)
}
