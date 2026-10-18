// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every Core Matrix effect kind changes the simulation through the content data alone. Each test that
 * isolates one kind replaces the Impact Driver node's effects with just that kind, so nothing else moves.
 */
class MatrixEffectsTest {
    @Test
    fun bootstrapRanksFoldIntoRunStartStatsAndPercentRanksStackAdditively() {
        val profile = PlayerProfile(matrix = matrixOf(
            MatrixNodeId.REINFORCED_HULL to 3,
            MatrixNodeId.IMPACT_DRIVER to 3,
            MatrixNodeId.CRYO_LOOP to 2,
            MatrixNodeId.MAGNET_ARRAY to 1,
            // Above the maximum: a run never applies more than the node defines.
            MatrixNodeId.DATA_ARCHIVE to 5,
        ))
        val game = MutableGameState(canonicalGameplayContent, seed = 5, bootstrapProgress = profile.toGameplaySnapshot()).apply {
            startRun()
        }

        assertEquals(3, game.matrixRanks[MatrixNodeId.DATA_ARCHIVE.ordinal])
        assertEquals(175f, game.maxHp)
        assertEquals(175f, game.hp)
        assertEquals(1.45f, game.damageMultiplier, 1e-5f)
        assertEquals(19f * 1.4f, game.coolingRate, 1e-4f)
        assertEquals(36f * 0.8f, game.dashHeatCost, 1e-4f)
        assertEquals(canonicalGameplayContent.pickups.magnetRadius + 70f, game.pickupRadius)
        assertEquals(1.45f, game.dataGain, 1e-5f)

        val fresh = MutableGameState(canonicalGameplayContent, seed = 5).apply { startRun() }
        assertEquals(100f, fresh.maxHp)
        assertEquals(1f, fresh.damageMultiplier)
        assertEquals(MatrixModifiers(), fresh.matrix)
    }

    @Test
    fun damageMultiplierScalesHitsProcsStatusesAndAbilities() {
        val effect = MatrixEffect.DamageMultiplier(0.35f)
        fun dealt(withMatrix: Boolean, hit: MutableGameState.(kinetickk.ball.gameplay.nucleus.model.Enemy) -> Unit): Float {
            val game = isolated(effect, withMatrix)
            val enemy = game.addEnemyForTesting(game.coreX + 200f, game.coreY, hp = 100_000f)
            game.hit(enemy)
            return enemy.maxHp - enemy.hp
        }
        listOf<MutableGameState.(kinetickk.ball.gameplay.nucleus.model.Enemy) -> Unit>(
            { dealHit(it, 50f, HitSource.WEAPON, canCrit = false, qualified = false) },
            { dealProcDamage(it, 50f) },
            { dealStatusDamage(it, 50f, element = null) },
            { dealCharacterDamage(it, 50f) },
        ).forEachIndexed { index, hit ->
            val base = dealt(false, hit)
            assertTrue(base > 0f, "path $index")
            assertEquals(base * 1.35f, dealt(true, hit), base * 1e-4f, "path $index")
        }
    }

    @Test
    fun damageMultiplierAppliesOnceToProcsDerivedFromAHit() {
        // Calibrated Coil answers every weapon hit with a full-hit Strike and a half-hit Burst around the target.
        val coil = contentWithClauses("Calibrated Coil", EffectClause(EffectTrigger.WEAPON_HIT,
            actions = immutableListOf(EffectAction.Strike(DamageBasis.HIT, 1f), EffectAction.Burst(160f, DamageBasis.HIT, 0.5f))))
        fun dealt(withMatrix: Boolean): Pair<Float, Float> {
            val game = isolated(MatrixEffect.DamageMultiplier(0.35f), withMatrix, base = coil)
            game.own("Calibrated Coil")
            val target = game.addEnemyForTesting(game.coreX + 300f, game.coreY, hp = 100_000f)
            val bystander = game.addEnemyForTesting(game.coreX + 380f, game.coreY, hp = 100_000f)
            game.dealHit(target, 50f, HitSource.WEAPON, canCrit = false)
            return (target.maxHp - target.hp) to (bystander.maxHp - bystander.hp)
        }
        val (targetBase, bystanderBase) = dealt(false)
        val (targetBoosted, bystanderBoosted) = dealt(true)
        assertTrue(bystanderBase > 0f, "the Burst must reach the bystander")
        // Hit, Strike and Burst each take the multiplier once, not once more through the hit they measure.
        assertEquals(targetBase * 1.35f, targetBoosted, targetBase * 1e-4f)
        assertEquals(bystanderBase * 1.35f, bystanderBoosted, bystanderBase * 1e-4f)
    }

    @Test
    fun reactionPowerAndStatusPotencyRaiseTheCompiledBuildAndReactionDamage() {
        fun game(withMatrix: Boolean) = isolated(null, withMatrix, MatrixNodeId.CATALYST_CORE)
        val boosted = game(true)
        assertEquals(1.2f, boosted.build.reactionPower, 1e-5f)
        StatusElement.entries.forEach { assertEquals(1.15f, boosted.build.potency(it), 1e-5f, "$it") }
        assertEquals(1f, game(false).build.reactionPower)

        fun reactionDamage(withMatrix: Boolean): Float {
            val state = game(withMatrix)
            val enemy = state.addEnemyForTesting(state.coreX + 300f, state.coreY, hp = 100_000f)
            state.applyStatus(enemy, StatusElement.THERMAL, 4, 0)
            state.applyStatus(enemy, StatusElement.ION, 4, 0)
            return enemy.maxHp - enemy.hp
        }
        val base = reactionDamage(false)
        assertTrue(base > 0f, "Thermal then Ion must react")
        assertTrue(reactionDamage(true) > base * 1.15f)
    }

    @Test
    fun dashHeatMultiplierAndTheFreeDashIntervalChangeTheHeatADashCosts() {
        val cooler = isolated(MatrixEffect.DashHeat(0.8f), true)
        cooler.performDash()
        assertEquals(36f * 0.8f, cooler.heat, 1e-4f)

        val free = isolated(MatrixEffect.FreeDashInterval(6f), true)
        free.performDash()
        assertEquals(0f, free.heat, "the first Dash is free")
        free.performDash()
        assertEquals(36f, free.heat, 1e-4f)
        free.elapsed += 6f
        val heatBefore = free.heat
        free.performDash()
        assertEquals(heatBefore, free.heat, "a Dash is free again 6 seconds later")

        val plain = isolated(null, false)
        plain.performDash()
        assertEquals(36f, plain.heat, 1e-4f)
    }

    @Test
    fun dragMultiplierLetsTheCoreKeepMoreSpeed() {
        fun coast(withMatrix: Boolean): Float = isolated(MatrixEffect.Drag(0.5f), withMatrix).run {
            setVelocityForTesting(900f, 0f)
            repeat(120) { updateCore(MutableGameState.FIXED_STEP) }
            speed
        }
        assertEquals(0.29f * 0.5f, isolated(MatrixEffect.Drag(0.5f), true).dragCoefficient, 1e-6f)
        assertTrue(coast(true) > coast(false) * 1.05f)
    }

    @Test
    fun startingWeaponLevelStartsHigherAndRaisesTheAutomaticCap() {
        val armed = isolated(MatrixEffect.StartingWeaponLevel(1), true, rank = 2)
        // A signature character (the Orb with Flux Wake) adds its own start level on top.
        assertEquals(3 + armed.signatureLevelBonus(), armed.weaponLevel)

        fun automaticCap(withMatrix: Boolean): Int = isolated(MatrixEffect.StartingWeaponLevel(1), withMatrix, rank = 2).run {
            weaponLevel = content.tempo.maximumAutomaticWeaponLevel + signatureLevelBonus()
            // Find the next level that grants an automatic amplification and reach it.
            while (!content.tempo.grantsWeaponAmplification(level + 1)) level++
            data = 0
            nextLevelData = content.tempo.dataRequiredForLevel(level)
            gainData(nextLevelData.toFloat() + 0.5f)
            weaponLevel - signatureLevelBonus()
        }
        val cap = canonicalGameplayContent.tempo.maximumAutomaticWeaponLevel
        assertEquals(cap, automaticCap(false))
        assertEquals(cap + 1, automaticCap(true))
    }

    @Test
    fun startingOverdriveAndOverdriveGainComeFromTheNode() {
        val coil = isolated(null, true, MatrixNodeId.OVERDRIVE_COIL)
        assertEquals(50f, coil.overdriveCharge)
        assertEquals(1.25f, coil.overdriveGain, 1e-5f)
        assertEquals(0f, isolated(null, false).overdriveCharge)
    }

    @Test
    fun secondWindSavesOneLethalHitPerRunAndOnlyOne() {
        val saved = isolated(MatrixEffect.SecondWind(0.3f, 2f), true)
        saved.takeDamage(10_000f)
        assertEquals(GamePhase.RUNNING, saved.phase)
        assertEquals(saved.maxHp * 0.3f, saved.hp, 1e-3f)
        assertEquals(saved.elapsed + 2f, saved.invulnerableUntil, 1e-5f)
        assertTrue(saved.secondWindSpent)
        saved.takeDamage(10_000f)
        assertEquals(GamePhase.RUNNING, saved.phase, "the invulnerability absorbs the next hit")

        saved.elapsed += 2.5f
        saved.hurtCooldown = 0f
        saved.takeDamage(10_000f)
        assertEquals(GamePhase.GAME_OVER, saved.phase)

        val plain = isolated(null, false)
        plain.takeDamage(10_000f)
        assertEquals(GamePhase.GAME_OVER, plain.phase)
    }

    @Test
    fun offerCardsWidenEveryFourthItemOffer() {
        fun sizes(withMatrix: Boolean): List<Int> = isolated(MatrixEffect.OfferCards(4), withMatrix).run {
            List(8) {
                openItemChoice()
                val size = choices.size
                choices = emptyList()
                phase = GamePhase.RUNNING
                size
            }
        }
        assertEquals(listOf(3, 3, 3, 4, 3, 3, 3, 4), sizes(true))
        assertEquals(List(8) { 3 }, sizes(false))
    }

    @Test
    fun rarityPityGuaranteesARareCardAfterFourOffersInsteadOfSix() {
        fun everyOfferHasARareCard(withMatrix: Boolean): Boolean = (0 until 24).all { seed ->
            isolated(MatrixEffect.RarityPity(4), withMatrix, seed = 900 + seed).run {
                // Every item is offerable, so a Rare card always exists for the pity card.
                level = 30
                offerPity = 4
                activeChoiceType = ChoiceType.ITEM
                buildItemChoices()
                choices.any { choice -> content.item(choice.itemId!!)!!.rarity.rank >= ItemRarity.RARE.rank }
            }
        }
        assertEquals(4, isolated(MatrixEffect.RarityPity(4), true).pityOffers())
        assertEquals(PITY_OFFERS, isolated(null, false).pityOffers())
        assertTrue(everyOfferHasARareCard(true))
        assertTrue(!everyOfferHasARareCard(false), "without the node four offers are not yet pity")
    }

    @Test
    fun pickupValueRaisesTheDataEachPickupGivesAndCountsPickups() {
        fun collected(withMatrix: Boolean): Pair<Float, Int> = isolated(MatrixEffect.PickupValue(0.5f), withMatrix).run {
            addPickup(Pickup(PickupType.DATA, coreX, coreY))
            addPickup(Pickup(PickupType.KEY, coreX, coreY))
            resolvePickupCollection()
            (dataCollected.toFloat() + dataFraction) to pendingDataPickups
        }
        val (base, basePickups) = collected(false)
        val (boosted, boostedPickups) = collected(true)
        assertEquals(base * 1.5f, boosted, base * 1e-4f)
        assertEquals(1, basePickups, "only the Data pickup counts")
        assertEquals(1, boostedPickups)
    }

    @Test
    fun eliteBountyQueuesAnExtraItemOfferOnEveryEliteKill() {
        fun pendingAfterEliteKill(withMatrix: Boolean): Int = isolated(MatrixEffect.EliteBounty, withMatrix).run {
            val elite = spawnedEnemy(kinetickk.ball.gameplay.nucleus.render.EnemyType.ELITE, coreX + 400f, coreY)
            enemies += elite
            onEnemyKilled(elite)
            pendingLevelChoices
        }
        assertEquals(pendingAfterEliteKill(false) + 1, pendingAfterEliteKill(true))
    }

    @Test
    fun heirloomOffersAddACardOrRaiseTheTakenHeirloomWhenNoCardFits() {
        /** Heirloom cards shown and the rank of the Heirloom taken from the first card. */
        fun heirloomOffer(withMatrix: Boolean, tier: Int, carried: Int): Pair<Int, Int> =
            isolated(MatrixEffect.HeirloomOffers(1), withMatrix, rebirth = tier) {
                heirloomCandidates = RelicId.entries.filter {
                    it != RelicId.HOLLOW_CROWN && content.relic(it).minimumRebirth == 0
                }.take(carried)
            }.run {
                openNextPendingChoice()
                val cards = choices.count { it.relicId != null }
                val taken = assertNotNull(choices.first().relicId)
                choose(0)
                cards to relicRank(taken)
            }
        // Below Rebirth 5 the extra is a third card.
        assertEquals(2 to 1, heirloomOffer(false, 1, carried = 4))
        assertEquals(3 to 1, heirloomOffer(true, 1, carried = 4))
        // From Rebirth 5 three cards and the decline card fill the choices: the taken Heirloom starts at rank 2.
        assertEquals(3 to 1, heirloomOffer(false, 5, carried = 4))
        assertEquals(3 to 2, heirloomOffer(true, 5, carried = 4))
        // Every carried Relic is already offered: the extra also becomes a rank.
        assertEquals(2 to 1, heirloomOffer(false, 1, carried = 2))
        assertEquals(2 to 2, heirloomOffer(true, 1, carried = 2))
    }

    @Test
    fun rebornChanceMakesRebornCardsAppearAtTierZero() {
        fun rebornCards(withMatrix: Boolean): Int = isolated(MatrixEffect.RebornChance(0.2f), withMatrix).run {
            activeChoiceType = ChoiceType.ITEM
            (0 until 60).sumOf {
                buildItemChoices()
                choices.count { it.reborn }
            }
        }
        assertEquals(0, rebornCards(false))
        assertTrue(rebornCards(true) > 10)
        assertEquals(0.2f, isolated(MatrixEffect.RebornChance(0.2f), true).rebornChance(), 1e-6f)
    }

    @Test
    fun enemyIntegrityMultiplierRaisesSpawnIntegrity() {
        val type = kinetickk.ball.gameplay.nucleus.render.EnemyType.DRIFTER
        val base = isolated(null, false).spawnIntegrity(type)
        val tougher = isolated(MatrixEffect.EnemyIntegrity(1.5f), true)
        assertEquals(base * 1.5f, tougher.spawnIntegrity(type), base * 1e-4f)
        assertEquals(base * 1.5f, tougher.spawnedEnemy(type, 0f, 500f).maxHp, base * 1e-4f)
    }

    @Test
    fun contactDamageMultiplierReducesOnlyContactDamage() {
        fun lost(withMatrix: Boolean, kind: IncomingDamage): Float = isolated(MatrixEffect.ContactDamage(0.7f), withMatrix).run {
            val before = hp
            takeDamage(10f, kind = kind)
            before - hp
        }
        assertEquals(lost(false, IncomingDamage.CONTACT) * 0.7f, lost(true, IncomingDamage.CONTACT), 1e-4f)
        assertEquals(lost(false, IncomingDamage.PROJECTILE), lost(true, IncomingDamage.PROJECTILE))
    }

    @Test
    fun rerollsAddToTheRunStart() {
        assertEquals(isolated(null, false).rerollsRemaining + 2, isolated(MatrixEffect.Rerolls(1), true, rank = 2).rerollsRemaining)
    }

    @Test
    fun weaponRunGrowthAddsItsBonusAsTheRunProgresses() {
        fun growth(withMatrix: Boolean, elapsed: Float): Float = isolated(MatrixEffect.WeaponRunGrowth(0.4f), withMatrix).run {
            this.elapsed = elapsed
            weaponRunGrowth()
        }
        val end = canonicalGameplayContent.tempo.bossAtSeconds
        assertEquals(growth(false, 0f), growth(true, 0f))
        assertEquals(growth(false, end) + 0.4f, growth(true, end), 1e-4f)
    }

    @Test
    fun shieldRegenDelayShortensTheWaitAfterAHit() {
        fun delay(withMatrix: Boolean): Float = isolated(MatrixEffect.ShieldRegenDelay(0.6f), withMatrix).run {
            takeDamage(5f)
            shieldRechargeDelay
        }
        assertEquals(delay(false) * 0.6f, delay(true), 1e-5f)
        val aegis = isolated(null, true, MatrixNodeId.AEGIS_LATTICE)
        assertEquals(30f, aegis.maxShield)
        assertEquals(30f, aegis.shield)
    }

    @Test
    fun obstacleEffectsAreStoredForTheObstacleSystems() {
        val dampers = isolated(null, true, MatrixNodeId.IMPACT_DAMPERS, rank = 2).matrix
        assertEquals(0.5f, dampers.obstacleDamageMultiplier, 1e-6f)
        assertEquals(300f, dampers.obstacleLethalSpeedBonus)

        val surfer = MutableGameState(canonicalGameplayContent, seed = 3).apply {
            matrixRanks[MatrixNodeId.IMPACT_DAMPERS.ordinal] = 1
            matrixRanks[MatrixNodeId.RICOCHET_RIG.ordinal] = 1
            matrixRanks[MatrixNodeId.GRAVITY_SURFER.ordinal] = 1
            matrixRanks[MatrixNodeId.NEAR_MISS.ordinal] = 1
            startRun()
        }.matrix
        assertEquals(0.75f, surfer.obstacleDamageMultiplier, 1e-6f)
        assertEquals(400f, surfer.obstacleLethalSpeedBonus)
        assertEquals(MatrixEffect.RicochetRig(0.75f, 140f), surfer.ricochetRig)
        assertEquals(0.4f, surfer.ricochetPhaseSeconds)
        assertEquals(MatrixEffect.NearMiss(40f, 900f, 6f, 0.5f), surfer.nearMiss)

        val none = isolated(null, false).matrix
        assertEquals(1f, none.obstacleDamageMultiplier)
        assertEquals(0f, none.obstacleLethalSpeedBonus)
        assertNull(none.ricochetRig)
        assertNull(none.nearMiss)
    }

    @Test
    fun killsDataPickupsAndRicochetsReachTheProgressUpdateOnceAndDrain() {
        val game = isolated(null, false)
        val enemy = game.addEnemyForTesting(game.coreX + 300f, game.coreY, hp = 5f)
        game.onEnemyKilled(enemy)
        game.addPickup(Pickup(PickupType.DATA, game.coreX, game.coreY))
        game.resolvePickupCollection()
        game.pendingRicochets = 2
        // The counters wait for the run to bank (exit, death or victory).
        assertNull(game.takeProgressUpdate())
        assertEquals(1, game.pendingTotalKills)

        game.exitRun()
        val update = assertNotNull(game.takeProgressUpdate())
        assertTrue(update.bankedMatter > 0L)
        assertEquals(1, update.totalKills)
        assertEquals(1, update.dataPickups)
        assertEquals(2, update.ricochets)
        assertEquals(0, game.pendingTotalKills)
        assertEquals(0, game.pendingDataPickups)
        assertEquals(0, game.pendingRicochets)
        assertNull(game.takeProgressUpdate())
    }

    @Test
    fun buildSummaryReportsMatrixContributionsFromContent() {
        val game = isolated(null, true, MatrixNodeId.REINFORCED_HULL, rank = 3)
        val integrity = game.buildStats().first { it.name == "Integrity" }
        assertEquals(175f, integrity.value)
        assertEquals(75f, integrity.contributions.first { it.source == kinetickk.ball.gameplay.api.BuildStatSource.LAB }.amount)
    }

    @Test
    fun enemyIntegrityReachesEverySpawnPath() {
        /** Maximum integrity of every enemy the director, a Herald, its escorts, a Broodmother and a Splitter add. */
        fun spawned(withMatrix: Boolean): List<Pair<EnemyType, Float>> =
            isolated(MatrixEffect.EnemyIntegrity(1.5f), withMatrix, rebirth = 3).run {
                enemies.clear()
                // Births first, while the ordinary cap has room for the brood.
                val brood = spawnedEnemy(EnemyType.BROODMOTHER, coreX + 500f, coreY).apply { actionTimer = 0f }
                enemies += brood
                updateBroodmother(brood, coreX - brood.x, coreY - brood.y, 500f, STEP, STEP)
                spawnOpeningPacks()
                check(spawnScheduled(EnemyType.HERALD))
                spawnPendingEscorts()
                val splitter = spawnedEnemy(EnemyType.SPLITTER, coreX + 600f, coreY)
                enemies += splitter
                val beforeFragments = enemies.size
                onEnemyKilled(splitter)
                check(enemies.size > beforeFragments) { "the Splitter leaves fragments" }
                enemies.filter { it !== splitter }.map { it.type to it.maxHp }
            }
        val plain = spawned(false)
        val tougher = spawned(true)
        assertEquals(plain.map { it.first }, tougher.map { it.first }, "the same enemies spawn")
        for (type in listOf(EnemyType.HERALD, EnemyType.SWARMLING, EnemyType.BROODMOTHER)) {
            assertTrue(plain.any { it.first == type }, "a $type spawned: $plain")
        }
        assertTrue(plain.size > 8, "a brood, packs, the Herald with its escorts and fragments: $plain")
        plain.zip(tougher).forEach { (base, boosted) ->
            assertEquals(base.second * 1.5f, boosted.second, base.second * 1e-4f, "${base.first} integrity")
        }
    }

    @Test
    fun eliteBountyAndTheKillCounterCountEveryKillPathOnce() {
        /** New pending item offers and counted kills after [kill] and the next collision pass. */
        fun after(withMatrix: Boolean, type: EnemyType, kill: MutableGameState.(Enemy) -> Unit): Pair<Int, Int> =
            isolated(MatrixEffect.EliteBounty, withMatrix, rebirth = 2).run {
                val target = spawnedEnemy(type, coreX + 300f, coreY)
                enemies += target
                val choices = pendingLevelChoices
                val kills = pendingTotalKills
                kill(target)
                // The kill sweep resolves every death once, however it was dealt.
                resolveCollisions(STEP)
                assertTrue(target.dead, "the $type died")
                (pendingLevelChoices - choices) to (pendingTotalKills - kills)
            }
        val paths = listOf<MutableGameState.(Enemy) -> Unit>(
            { damageEnemy(it, it.hp + 1f, killEligible = true, source = HitSource.WEAPON) },
            { executeEnemy(it, HitSource.STATUS) },
            { onEnemyKilled(it) },
        )
        paths.forEachIndexed { index, kill ->
            assertEquals(0 to 1, after(false, EnemyType.ELITE, kill), "kill path $index without the bounty")
            assertEquals(1 to 1, after(true, EnemyType.ELITE, kill), "kill path $index with the bounty")
        }
        // The Herald pays out like an Elite, the bounty included; an ordinary kill never does.
        assertEquals(1 to 1, after(true, EnemyType.HERALD, paths[0]))
        assertEquals(0 to 1, after(true, EnemyType.DRIFTER, paths[1]))
    }

    @Test
    fun secondWindAnswersHostileBlastsAndStrikes() {
        fun integrityAfter(hurt: MutableGameState.() -> Unit): Float = isolated(MatrixEffect.SecondWind(0.3f, 2f), true).run {
            hp = 1f
            hurt()
            assertEquals(GamePhase.RUNNING, phase)
            hp
        }
        val blast = integrityAfter {
            detonateBomber(spawnedEnemy(EnemyType.BOMBER, coreX + 20f, coreY).also { enemies += it })
        }
        assertEquals(maxHpOfFreshRun() * 0.3f, blast, 1e-3f, "a Bomber blast leaves 30 %")
        val lance = integrityAfter {
            takeDamage(strikeDamage(strikingLancer()), kind = IncomingDamage.CONTACT)
        }
        assertEquals(maxHpOfFreshRun() * 0.3f, lance, 1e-3f, "a lance leaves 30 %")
    }

    @Test
    fun contactDamageMultiplierSoftensStrikesAndHollowSoftensThem() {
        fun lost(withMatrix: Boolean, hollow: Int): Float = isolated(MatrixEffect.ContactDamage(0.7f), withMatrix).run {
            val lancer = strikingLancer()
            lancer.status[StatusCell.STACKS + StatusElement.VOID.ordinal] = hollow.toFloat()
            val before = hp
            takeDamage(strikeDamage(lancer), kind = IncomingDamage.CONTACT)
            before - hp
        }
        assertTrue(lost(false, 0) > 0f)
        assertEquals(lost(false, 0) * 0.7f, lost(true, 0), 1e-3f, "Juggernaut softens a lance like any contact")
        assertTrue(lost(false, 4) < lost(false, 0), "Hollow cuts a lance")
    }

    @Test
    fun hollowRemembersDamageWithoutTheMatrixMultiplierSoUnmakeTakesItOnce() {
        /** The remembered damage after one proc, and the Unmake it then queues. */
        fun unmake(withMatrix: Boolean): Pair<Float, Float> = isolated(MatrixEffect.DamageMultiplier(0.35f), withMatrix).run {
            val enemy = addEnemyForTesting(coreX + 300f, coreY, hp = 100_000f)
            applyStatus(enemy, StatusElement.VOID, 3, generation = 0)
            dealProcDamage(enemy, 1_000f)
            val remembered = enemy.status[StatusCell.HOLLOW_MEMORY]
            applyStatus(enemy, StatusElement.VOID, 5, generation = 0)
            remembered to enemy.status[StatusCell.UNMAKE_DAMAGE]
        }
        val (plainMemory, plainUnmake) = unmake(false)
        val (boostedMemory, boostedUnmake) = unmake(true)
        assertTrue(plainMemory > 0f && plainUnmake > 0f)
        assertEquals(plainMemory, boostedMemory, plainMemory * 1e-4f, "the memory is measured without the Matrix")
        assertEquals(plainUnmake, boostedUnmake, plainUnmake * 1e-4f, "the strike applies the multiplier once, itself")
    }

    private fun maxHpOfFreshRun(): Float = isolated(null, false).maxHp

    /** A Lancer on the Core in the middle of its lance. */
    private fun MutableGameState.strikingLancer(): Enemy =
        spawnedEnemy(EnemyType.LANCER, coreX, coreY).also { lancer ->
            lancer.mode = EnemyBehavior.STRIKE
            lancer.modeUntil = elapsed + 0.5f
            enemies += lancer
        }

    private fun matrixOf(vararg ranks: Pair<MatrixNodeId, Int>): MatrixProgress =
        MatrixProgress(MatrixNodeId.entries.map { id -> ranks.firstOrNull { it.first == id }?.second ?: 0 })

    private fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot =
        GameplayProfileSnapshot(preferences, economy, loadout, matrix, collection, rebirthProgress)

    /**
     * A fresh run whose [node] owns [rank] ranks when [withMatrix]. A non-null [effect] replaces that
     * node's effects first, so the run differs from the baseline by exactly one effect kind.
     */
    private fun isolated(
        effect: MatrixEffect?,
        withMatrix: Boolean,
        node: MatrixNodeId = MatrixNodeId.IMPACT_DRIVER,
        rank: Int = 1,
        seed: Int = 77,
        rebirth: Int = 0,
        base: GameplayContentSnapshot = canonicalGameplayContent,
        setup: MutableGameState.() -> Unit = {},
    ): MutableGameState {
        val content = if (effect == null) base else base.withEffect(node, effect)
        return MutableGameState(content, seed = seed, initialRebirthLevel = rebirth).apply {
            if (withMatrix) matrixRanks[content.matrixNodes.indexOfFirst { it.id == node }] = rank
            setup()
            startRun()
        }
    }

    private fun GameplayContentSnapshot.withEffect(node: MatrixNodeId, effect: MatrixEffect): GameplayContentSnapshot =
        copy(matrixNodes = matrixNodes.map { definition ->
            if (definition.id == node) definition.copy(effects = immutableListOf(effect)) else definition
        }.toImmutableList())

    private companion object {
        const val STEP = MutableGameState.FIXED_STEP
    }
}
