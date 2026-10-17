// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.impactTaken
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnemyRosterBehaviorTest {
    @Test
    fun hostileShotsCarryTheirSourceDamageAndContactGrowsWithTheRun() {
        val state = quietState()
        state.elapsed = state.content.tempo.bossAtSeconds * 0.5f
        val shooter = state.spawn(EnemyType.SHOOTER, 300f, 0f).apply { actionTimer = 0f }
        state.updateEnemies(FIXED)
        val shot = state.projectiles.single { it.hostile }
        assertEquals(state.hostileDamage(shooter, 10f), shot.damage)
        assertEquals(12.5f, shot.damage, 1e-4f)

        // The Core takes exactly the damage the shot carries.
        state.projectiles.clear()
        state.projectiles += Projectile(state.coreX, state.coreY, 0f, 0f, 5f, 1f, damage = 9f)
        state.resolveProjectileHits()
        assertEquals(state.maxHp - 9f, state.hp, 1e-4f)

        val drifter = state.spawn(EnemyType.DRIFTER, 0f, 0f)
        assertEquals(state.hostileDamage(drifter, 7f + drifter.radius * 0.18f), state.contactDamage(drifter))
    }

    @Test
    fun disabledEnemiesSkipTheirBehaviorAndSilencedEnemiesSkipTheirAttack() {
        val state = quietState()
        val shooter = state.spawn(EnemyType.SHOOTER, 300f, 0f).apply { actionTimer = 0f }
        shooter.disabledUntil = 1f
        shooter.vx = 100f
        state.updateEnemies(FIXED)
        assertTrue(state.projectiles.isEmpty(), "A disabled Shooter never fires")
        assertTrue(shooter.vx < 100f && shooter.vx > 0f, "A disabled enemy only coasts")

        shooter.disabledUntil = 0f
        shooter.silencedUntil = 1f
        shooter.actionTimer = 0f
        state.updateEnemies(FIXED)
        assertTrue(state.projectiles.isEmpty(), "A silenced Shooter skips the shot")
        assertEquals(1.7f, shooter.actionTimer, 1e-4f, "and restarts its cadence")

        val bomber = state.spawn(EnemyType.BOMBER, 80f, 0f)
        bomber.silencedUntil = 1f
        state.updateEnemies(FIXED)
        assertEquals(EnemyBehavior.IDLE, bomber.mode, "A silenced Bomber does not prime")

        // Silence landing on a primed fuse makes it fizzle instead of exploding.
        bomber.mode = EnemyBehavior.PRIMED
        bomber.modeUntil = state.elapsed
        state.updateEnemies(FIXED)
        assertTrue(bomber in state.enemies && !bomber.dead, "The silenced fuse fizzles")
        assertEquals(EnemyBehavior.IDLE, bomber.mode)
        assertEquals(state.maxHp, state.hp)
    }

    @Test
    fun stunnedOrSilencedStrikersLoseTheirAnySpeedHit() {
        val state = quietState()
        val lancer = state.spawn(EnemyType.LANCER, 30f, 0f, hp = 10_000f)
        lancer.mode = EnemyBehavior.STRIKE
        lancer.modeUntil = state.elapsed + 0.5f
        assertEquals(state.hostileDamage(lancer, LANCER_DAMAGE), state.strikeDamage(lancer), 1e-4f)
        lancer.silencedUntil = state.elapsed + 1f
        assertEquals(0f, state.strikeDamage(lancer), "A silenced lance hits like an ordinary contact")
        lancer.silencedUntil = 0f
        lancer.disabledUntil = state.elapsed + 1f
        assertEquals(0f, state.strikeDamage(lancer), "A stunned Lancer strikes nothing")
        lancer.disabledUntil = 0f
        lancer.modeUntil = state.elapsed - FIXED
        assertEquals(0f, state.strikeDamage(lancer), "The strike ends with its window")

        // A fast Core meeting the silenced Lancer takes no contact damage.
        lancer.modeUntil = state.elapsed + 0.5f
        lancer.silencedUntil = state.elapsed + 1f
        state.previousCoreX = -10f
        state.setVelocityForTesting(900f, 0f)
        state.resolveEnemyCoreCollisions()
        assertEquals(state.maxHp, state.hp)
    }

    @Test
    fun bomberPrimesNearTheCoreAndItsBlastHurtsAtAnySpeedButNotThroughADash() {
        val state = quietState()
        val bomber = state.spawn(EnemyType.BOMBER, 90f, 0f)
        state.updateEnemies(FIXED)
        assertEquals(EnemyBehavior.PRIMED, bomber.mode)
        val blast = state.hostileDamage(bomber, BOMBER_BLAST_DAMAGE)
        val kills = state.kills
        state.runFor(BOMBER_PRIME_SECONDS - 3 * FIXED)
        assertTrue(bomber in state.enemies, "Still priming")
        // The Core is fast when the blast goes off; speed grants no immunity.
        state.setVelocityForTesting(0f, 1_500f)
        state.runFor(4 * FIXED)
        assertTrue(bomber !in state.enemies, "The blast removes the Bomber")
        assertEquals(kills, state.kills, "A detonation is not a kill")
        // The blast grew a hair with the run while the fuse burned.
        assertEquals(state.maxHp - blast, state.hp, 0.05f)

        val dashing = quietState()
        val second = dashing.spawn(EnemyType.BOMBER, 90f, 0f)
        dashing.updateEnemies(FIXED)
        dashing.dashPhaseTime = 5f
        dashing.runFor(BOMBER_PRIME_SECONDS + FIXED)
        assertTrue(second !in dashing.enemies)
        assertEquals(dashing.maxHp, dashing.hp, "A dash phase cancels the blast")
    }

    @Test
    fun aFastImpactDestroysAnUnprimedBomberWithoutABlast() {
        val state = quietState()
        val bomber = state.spawn(EnemyType.BOMBER, 30f, 0f, hp = 10_000f)
        state.previousCoreX = -10f
        state.setVelocityForTesting(600f, 0f)
        state.resolveEnemyCoreCollisions()
        assertFalse(bomber.alive)
        state.sweepKills()
        assertTrue(bomber !in state.enemies)
        assertEquals(state.maxHp, state.hp)

        // An Aegis tether does not save it from a fast impact.
        val tethered = quietState()
        val aegis = tethered.spawn(EnemyType.AEGIS, 900f, 0f)
        val linkedBomber = tethered.spawn(EnemyType.BOMBER, 30f, 0f, hp = 10_000f)
        linkedBomber.linkedBy = aegis.id
        linkedBomber.linkedUntil = tethered.elapsed + 1f
        assertTrue(tethered.isLinked(linkedBomber))
        tethered.previousCoreX = -10f
        tethered.setVelocityForTesting(600f, 0f)
        tethered.resolveEnemyCoreCollisions()
        assertFalse(linkedBomber.alive)

        // A slow bump only damages it.
        val slow = quietState()
        val sturdy = slow.spawn(EnemyType.BOMBER, 30f, 0f, hp = 10_000f)
        slow.previousCoreX = -10f
        slow.setVelocityForTesting(300f, 0f)
        slow.resolveEnemyCoreCollisions()
        assertTrue(sturdy.alive)
    }

    @Test
    fun lancerTelegraphsLocksItsLaneLancesAndRecovers() {
        val state = quietState()
        val lancer = state.spawn(EnemyType.LANCER, 400f, 0f).apply { abilityAt = 0f }
        state.updateEnemies(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, lancer.mode)
        val lane = lancer.facing
        assertEquals(PI.toFloat(), abs(lane), 1e-3f)
        state.runEnemiesFor(LANCER_TELEGRAPH_SECONDS - 0.05f)
        assertEquals(EnemyBehavior.TELEGRAPH, lancer.mode)
        assertTrue(abs(lancer.x - 400f) < 5f, "It holds still while the lane shows")
        state.runEnemiesFor(0.1f)
        assertEquals(EnemyBehavior.STRIKE, lancer.mode)
        assertEquals(LANCER_SPEED, sqrt(lancer.vx * lancer.vx + lancer.vy * lancer.vy), 1f)
        assertEquals(lane, lancer.facing)
        // A lance hurts a fast Core.
        assertEquals(state.hostileDamage(lancer, LANCER_DAMAGE), state.strikeDamage(lancer))
        state.runEnemiesFor(LANCER_DISTANCE / LANCER_SPEED + 0.05f)
        assertEquals(EnemyBehavior.RECOVERY, lancer.mode)
        state.runEnemiesFor(LANCER_RECOVERY_SECONDS)
        assertEquals(EnemyBehavior.IDLE, lancer.mode)
        assertTrue(lancer.abilityAt > state.elapsed, "A cooldown follows the recovery")
    }

    @Test
    fun sniperShowsItsSightLineThenFiresOneHeavyShot() {
        val state = quietState()
        val sniper = state.spawn(EnemyType.SNIPER, 700f, 0f).apply { abilityAt = 0f }
        state.updateEnemies(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, sniper.mode)
        assertEquals(SNIPER_AIM_SECONDS, state.behaviorRemaining(sniper), 0.02f)
        state.runEnemiesFor(SNIPER_AIM_SECONDS - 0.05f)
        assertTrue(state.projectiles.isEmpty(), "No shot during the aim")
        state.runEnemiesFor(0.1f)
        val shot = state.projectiles.single()
        assertEquals(state.hostileDamage(sniper, 22f), shot.damage, 1e-3f)
        assertEquals(SNIPER_SHOT_SPEED, sqrt(shot.vx * shot.vx + shot.vy * shot.vy), 1f)
        assertTrue(shot.vx < 0f, "Aimed at the Core")
    }

    @Test
    fun aegisTethersUpToThreeAlliesWhoTakeLessDamage() {
        val state = quietState()
        val aegis = state.spawn(EnemyType.AEGIS, 600f, 0f)
        val allies = List(4) { index -> state.addEnemyForTesting(600f + 60f + index * 30f, 0f, hp = 1_000f) }
        val herald = state.spawn(EnemyType.HERALD, 600f, 40f)
        state.updateEnemies(FIXED)
        assertEquals(3, allies.count { state.isLinked(it) })
        assertFalse(state.isLinked(allies.last()), "The farthest ally stays untethered")
        assertFalse(state.isLinked(herald), "The Herald is never linked")
        assertEquals(aegis.id, state.linkedTo(allies.first()))

        val linked = allies.first()
        val unlinked = allies.last()
        state.damageEnemy(linked, 100f)
        state.damageEnemy(unlinked, 100f)
        assertEquals(1_000f - 100f * (1f - AEGIS_LINK_REDUCTION), linked.hp, 1e-3f)
        assertEquals(900f, unlinked.hp, 1e-3f)

        // The tether lapses shortly after the Aegis is gone.
        state.enemies.remove(aegis)
        state.runEnemiesFor(0.25f)
        assertFalse(state.isLinked(linked))
    }

    @Test
    fun minesArmLateTriggerNearTheCoreAndADashSetsThemOffHarmlessly() {
        val state = quietState()
        val layer = state.spawn(EnemyType.MINELAYER, 400f, 0f).apply { actionTimer = 0f }
        state.updateEnemies(FIXED)
        val mine = state.projectiles.single()
        assertEquals(ProjectileKind.MINE, mine.kind)
        assertEquals(state.hostileDamage(layer, 14f), mine.damage, 1e-3f)
        assertEquals(0f, mine.vx)

        // Unarmed it ignores the Core; armed it detonates.
        state.enemies.clear()
        mine.x = state.coreX + 40f
        mine.y = state.coreY
        state.resolveProjectileHits()
        assertTrue(mine in state.projectiles)
        mine.life = MINE_LIFE_SECONDS - MINE_ARM_SECONDS
        state.resolveProjectileHits()
        assertTrue(mine !in state.projectiles)
        assertEquals(state.maxHp - mine.damage, state.hp, 1e-3f)

        val dashing = quietState()
        dashing.projectiles += Projectile(dashing.coreX + 30f, dashing.coreY, 0f, 0f, MINE_RADIUS, MINE_LIFE_SECONDS,
            damage = 14f, kind = ProjectileKind.MINE)
        dashing.dashPhaseTime = 1f
        dashing.resolveProjectileHits()
        assertTrue(dashing.projectiles.isEmpty(), "A dash contact sets even an unarmed mine off")
        assertEquals(dashing.maxHp, dashing.hp)

        // No drops right on top of the Core.
        val close = quietState()
        close.spawn(EnemyType.MINELAYER, 150f, 0f).apply { actionTimer = 0f }
        close.updateEnemies(FIXED)
        assertTrue(close.projectiles.isEmpty())
    }

    @Test
    fun phantomPhasesOutOfReachAndReappearsNearTheCoreToStrike() {
        val state = quietState()
        val phantom = state.spawn(EnemyType.PHANTOM, 500f, 0f, hp = 1_000f).apply { abilityAt = 0f }
        state.updateEnemies(FIXED)
        assertEquals(EnemyBehavior.PHASED, phantom.mode)
        assertTrue(state.isShifting(phantom))
        assertEquals(0f, state.dealHit(phantom, 50f, HitSource.WEAPON).amount, "Untargetable while phased")
        assertEquals(0f, state.dealCharacterDamage(phantom, 50f).amount)
        assertTrue(state.nearestEnemy(state.coreX, state.coreY, 2_000f) == null)

        state.runEnemiesFor(PHANTOM_PHASE_SECONDS)
        assertEquals(EnemyBehavior.TELEGRAPH, phantom.mode)
        val distance = sqrt((phantom.x - state.coreX) * (phantom.x - state.coreX) + (phantom.y - state.coreY) * (phantom.y - state.coreY))
        assertEquals(PHANTOM_REAPPEAR_DISTANCE, distance, 10f)
        assertFalse(state.isShifting(phantom))
        state.runEnemiesFor(PHANTOM_WINDUP_SECONDS + FIXED)
        assertEquals(EnemyBehavior.STRIKE, phantom.mode)
        assertEquals(state.hostileDamage(phantom, PHANTOM_STRIKE_DAMAGE), state.strikeDamage(phantom))
    }

    @Test
    fun aPhantomReappearanceIsATeleport() {
        val state = quietState()
        val phantom = state.spawn(EnemyType.PHANTOM, 500f, 0f).apply { abilityAt = 0f }
        state.updateEnemies(FIXED)
        state.elapsed = phantom.modeUntil
        state.updateEnemies(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, phantom.mode)
        assertEquals(phantom.previousX, phantom.x - phantom.vx * FIXED, 1e-3f)
        assertEquals(phantom.previousY, phantom.y - phantom.vy * FIXED, 1e-3f)
    }

    @Test
    fun broodmotherBirthsThreeSwarmlingsWithinTheCaps() {
        val state = quietState()
        val mother = state.spawn(EnemyType.BROODMOTHER, 500f, 0f).apply { actionTimer = 0f }
        state.updateEnemies(FIXED)
        assertEquals(3, state.enemies.count { it.type == EnemyType.SWARMLING })
        assertEquals(BROOD_SECONDS, mother.actionTimer, 1e-4f)

        val full = quietState()
        val cap = full.ordinaryEnemyCap().toInt()
        while (full.ordinaryCapLoad() < cap) full.spawn(EnemyType.DRIFTER, 900f + full.enemies.size * 40f, 0f)
        full.spawn(EnemyType.BROODMOTHER, 500f, 0f).apply { actionTimer = 0f }
        full.updateEnemies(FIXED)
        assertTrue(full.enemies.none { it.type == EnemyType.SWARMLING }, "No births past the ordinary cap")
    }

    @Test
    fun reflectorShieldsItsFrontAndReturnsShotsAtMostEveryThirdOfASecond() {
        val state = quietState()
        val reflector = state.spawn(EnemyType.REFLECTOR, 300f, 0f, hp = 10_000f)
        state.updateEnemies(FIXED)
        assertTrue(reflector.guarded, "It turns its shield to the Core")
        assertEquals(0.5f, reflector.impactTaken)
        assertEquals(0.25f, reflector.weaponTaken)

        // A shot from the front is stopped and sent back as the Reflector's own shot.
        fun shoot(fromX: Float): Projectile {
            val shot = Projectile(fromX, 0f, if (fromX < reflector.x) 900f else -900f, 0f, 5f, 2f,
                hostile = false, damage = 40f, pierce = 3)
            shot.x = reflector.x + if (fromX < reflector.x) -10f else 10f
            shot.previousX = fromX
            state.projectiles += shot
            return shot
        }
        val front = shoot(200f)
        state.resolveProjectileHits()
        assertTrue(front !in state.projectiles, "The shield stops the shot")
        assertEquals(10_000f - 40f * 0.25f, reflector.hp, 1e-2f)
        val returned = state.projectiles.single { it.hostile }
        assertEquals(state.hostileDamage(reflector, 10f), returned.damage, 1e-3f)
        assertEquals(REFLECTOR_SHOT_SPEED, sqrt(returned.vx * returned.vx + returned.vy * returned.vy), 1f)

        shoot(200f)
        state.resolveProjectileHits()
        assertEquals(1, state.projectiles.count { it.hostile }, "Reflections are rate limited")

        // From behind, the shot lands in full and passes on.
        val before = reflector.hp
        val rear = shoot(420f)
        state.resolveProjectileHits()
        assertEquals(40f, before - reflector.hp, 1e-2f)
        assertTrue(rear in state.projectiles)
        assertTrue(reflector.guarded, "The frame follows the Core again after the shot")
    }

    @Test
    fun heraldShieldStopsAllButImpactsAndTheHeraldIsAFreezeImmuneMiniBoss() {
        val state = quietState()
        val herald = state.spawn(EnemyType.HERALD, 300f, 0f, hp = 100_000f)
        assertTrue(herald.isMiniBoss && !herald.isBoss)
        assertTrue(herald.aspect >= 0 && herald.aspectImmune)
        assertEquals(HERALD_ESCORTS, herald.escortsPending)
        assertEquals(0.15f, victimFactor(herald))
        assertTrue(state.holds(EffectCondition.TargetElite, herald, HitSource.WEAPON, null))

        herald.shieldUntil = state.elapsed + HERALD_SHIELD_SECONDS
        assertEquals(0f, state.dealHit(herald, 500f, HitSource.WEAPON).amount)
        assertEquals(0f, state.dealProcDamage(herald, 500f).amount)
        assertTrue(state.shielded(herald))
        assertTrue(state.dealHit(herald, 500f, HitSource.IMPACT).amount > 0f, "An impact breaks the shield")
        assertFalse(state.shielded(herald))
        assertTrue(state.dealHit(herald, 500f, HitSource.WEAPON).amount > 0f)

        // A Cryo Break never freezes it.
        val cryo = StatusElement.CRYO.ordinal
        if (herald.aspect == cryo) assignAspect(herald, StatusElement.THERMAL.ordinal, immune = true)
        state.applyStatus(herald, StatusElement.CRYO, 12, generation = 0)
        assertFalse(state.isFrozen(herald))

        // Its shield cycles with escorts.
        herald.abilityAt = 0f
        herald.shieldUntil = 0f
        herald.escortsPending = 0
        state.updateEnemies(FIXED)
        assertTrue(state.shielded(herald))
        assertEquals(HERALD_ESCORTS, herald.escortsPending)
    }

    @Test
    fun theHeraldPaysOutLikeAnEliteAndStaysPutPastTheLeash() {
        val state = quietState()
        val herald = state.spawn(EnemyType.HERALD, 9_000f, 0f)
        state.updateEnemies(FIXED)
        assertTrue(herald in state.enemies)
        assertEquals(9_000f, herald.x, 50f, "The Herald is never relocated")

        val kills = state.eliteKills
        state.onEnemyKilled(herald)
        assertEquals(kills + 1, state.eliteKills)
        assertTrue(state.pickups.any { it.type == PickupType.KEY })
        assertTrue(state.pickups.any { it.type == PickupType.RELIC })
        assertEquals(EnemyType.HERALD.profile.dataDrops, state.pickups.count { it.type == PickupType.DATA })
    }

    @Test
    fun aChargerDashHurtsAFastCore() {
        val state = quietState()
        val charger = state.spawn(EnemyType.CHARGER, 30f, 0f, hp = 10_000f)
        charger.mode = EnemyBehavior.STRIKE
        charger.modeUntil = state.elapsed + 0.3f
        state.previousCoreX = -10f
        state.setVelocityForTesting(900f, 0f)
        state.resolveEnemyCoreCollisions()
        assertEquals(state.maxHp - state.contactDamage(charger), state.hp, 1e-3f)

        val idle = quietState()
        val calm = idle.spawn(EnemyType.CHARGER, 30f, 0f, hp = 10_000f)
        idle.previousCoreX = -10f
        idle.setVelocityForTesting(900f, 0f)
        idle.resolveEnemyCoreCollisions()
        assertTrue(calm.alive)
        assertEquals(idle.maxHp, idle.hp, "Outside its dash a fast impact still avoids contact damage")
    }

    @Test
    fun swarmlingsFillAThirdOfACapSlot() {
        val state = quietState()
        repeat(3) { state.spawn(EnemyType.SWARMLING, 800f + it * 30f, 0f) }
        state.spawn(EnemyType.DRIFTER, 900f, 0f)
        state.spawn(EnemyType.HERALD, 900f, 300f)
        assertEquals(2f, state.ordinaryCapLoad(), 1e-4f, "Three Swarmlings fill one slot; the Herald none")
    }

    /** A quiet arena with the pointer on the Core; the grace keeps the singularity from ending the run. */
    private fun quietState(): MutableGameState = buildTestState().apply {
        updatePointer(640f, 360f)
        runGrace = Float.MAX_VALUE
    }

    private fun MutableGameState.spawn(type: EnemyType, x: Float, y: Float, hp: Float? = null): Enemy {
        val enemy = spawnedEnemy(type, coreX + x, coreY + y)
        if (hp != null) enemy.hp = hp
        enemies += enemy
        return enemy
    }

    private fun MutableGameState.runEnemiesFor(seconds: Float) {
        var left = seconds
        while (left > 0f) {
            elapsed += FIXED
            updateEnemies(FIXED)
            left -= FIXED
        }
    }

    private fun MutableGameState.runFor(seconds: Float) {
        var left = seconds
        while (left > 0f) {
            simulateStep(FIXED)
            left -= FIXED
        }
    }

    private companion object {
        const val FIXED = MutableGameState.FIXED_STEP
    }
}
