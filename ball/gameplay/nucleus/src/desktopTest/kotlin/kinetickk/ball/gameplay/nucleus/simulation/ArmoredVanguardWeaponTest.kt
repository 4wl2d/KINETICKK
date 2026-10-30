// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Rebirth R8 Armored Vanguard against every weapon (SPEC 11.2): a plated enemy takes 40 % less weapon
 * damage until an impact cracks the plate, whichever of the 24 weapons' damage paths lands the hit
 * (projectiles, discs, sentries, shards, missiles, rifts, lenses and wells, beams, the tether, pulses,
 * auras, trails and orbitals).
 *
 * Each weapon flies the weapon-balance crowd arena twice, once against plated targets and once against
 * bare ones; nothing else differs. The targets are Elites made immune to the weapon's own element (an
 * ordinary enemy's aspect is fixed), so the damage they take is the weapon's own element-less hits, which
 * the plate scales; element damage is not weapon damage and keeps its full amount. The plates are set by
 * hand (the R8 roll itself is covered by RebirthMutatorTest). The Concussion Engine's
 * shockwaves come from impacts, so its pilot reports an impact to the weapon each frame without striking a
 * target (a real strike would crack that target first). After the first phase every plated target is
 * cracked by one impact (the bare twin takes the same impact), and the second phase must match the twin.
 * For Hollow Rift, one target stands in a rift that the real weapon laid: the plate contract needs a real
 * detonation hit, independently of how a movement retune aligns the pilot's route with the anchored ring.
 */
class ArmoredVanguardWeaponTest {
    @Test
    fun armoredVanguardPlatesBluntEveryWeaponUntilAnImpactCracksThem() {
        val failures = mutableListOf<String>()
        for (weapon in WeaponId.entries) {
            val plated = Flight(weapon, armored = true)
            val bare = Flight(weapon, armored = false)
            val platedIntact = plated.fly(PHASE_SECONDS)
            val bareIntact = bare.fly(PHASE_SECONDS)
            val stillIntact = plated.targets.all { it.armorIntact }
            plated.crack()
            bare.crack()
            val cracked = plated.targets.all { it.armored && it.armorCracked }
            val platedCracked = plated.fly(PHASE_SECONDS)
            val bareCracked = bare.fly(PHASE_SECONDS)
            val intactShare = platedIntact / bareIntact
            val crackedShare = platedCracked / bareCracked
            println("ARMORED-VANGUARD $weapon bare=${"%.0f".format(bareIntact)} intact=${"%.3f".format(intactShare)} " +
                "cracked=${"%.3f".format(crackedShare)}")
            when {
                bareIntact < MINIMUM_DAMAGE || bareCracked < MINIMUM_DAMAGE -> failures += "$weapon dealt too little to judge ($bareIntact, $bareCracked)"
                !stillIntact -> failures += "$weapon: a plate cracked without an impact"
                !cracked -> failures += "$weapon: an impact left a plate intact"
                intactShare !in 0.55..0.65 -> failures += "$weapon: plated targets took $intactShare of the weapon damage (expected 0.6)"
                crackedShare !in 0.97..1.03 -> failures += "$weapon: cracked plates still took $crackedShare of the weapon damage"
            }
        }
        assertTrue(failures.isEmpty(), "Armored Vanguard plates must blunt every weapon until cracked: $failures")
    }

    /** One weapon's crowd arena, flown by the weapon-balance pilot at the fresh level-1 checkpoint. */
    private class Flight(private val weapon: WeaponId, armored: Boolean) {
        private val game = WeaponBalanceTest.arenaState(weapon, WeaponBalanceTest.Checkpoint.FRESH, WeaponBalanceTest.nonSignature(weapon))
        /** The crowd arena's ring layout, as Elites: only an instance aspect can be immune to the weapon's element. */
        val targets: List<Enemy> = WeaponBalanceTest.cluster(game, CROWD_SIZE, ring = CROWD_RING, integrity = INTEGRITY).map { drifter ->
            game.enemies.remove(drifter)
            game.addEnemyForTesting(drifter.x, drifter.y, hp = INTEGRITY, type = EnemyType.ELITE)
        }
        private val anchors = targets.map { it.x to it.y }
        private val pilot = WeaponBalanceTest.Companion.Shuttle(reach = 280f, cruise = 650f)
        private var frame = 0

        init {
            val element = game.content.weapon(weapon).element
            for (target in targets) {
                target.armored = armored
                assignAspect(target, element?.ordinal ?: -1, immune = element != null)
            }
        }

        /** Damage the targets take over [seconds]. */
        fun fly(seconds: Float): Double {
            var dealt = 0.0
            val end = game.elapsed + seconds
            while (game.elapsed < end && game.phase == GamePhase.RUNNING) {
                WeaponBalanceTest.hold(game, targets, WeaponBalanceTest.Checkpoint.FRESH)
                for ((index, target) in targets.withIndex()) {
                    target.x = anchors[index].first
                    target.y = anchors[index].second
                    target.vx = 0f
                    target.vy = 0f
                    target.disabledUntil = Float.MAX_VALUE
                    // No Core contact: an impact would crack the plate.
                    target.contactCooldown = 1f
                }
                placeTargetInArmedRift()
                val t = frame / WeaponBalanceTest.FPS
                pilot.fly(game, 0f, 0f)
                game.setBrake(t % 3f in 2.6f..3f)
                if (frame > 0 && frame % (2 * WeaponBalanceTest.FPS).toInt() == 0) game.requestDash()
                val before = targets.sumOf { it.hp.toDouble() }
                if (weapon == WeaponId.CONCUSSION_ENGINE) game.onWeaponImpact(impactSpeed = 600f, impactDamage = 40f)
                game.update(1f / WeaponBalanceTest.FPS)
                dealt += before - targets.sumOf { it.hp.toDouble() }
                WeaponBalanceTest.drain(game)
                frame++
            }
            return dealt
        }

        /** Guarantee exposure, not damage: the normal fuse, collision and weapon-hit path still decide the hit. */
        private fun placeTargetInArmedRift() {
            if (weapon != WeaponId.HOLLOW_RIFT) return
            val rift = game.weaponRuntime.entities.firstOrNull {
                it.kind == WeaponEntityKind.RIFT && it.phase == RIFT_ARMED
            } ?: return
            targets.first().apply {
                x = (rift.x + rift.endX) * 0.5f
                y = (rift.y + rift.endY) * 0.5f
                previousX = x
                previousY = y
            }
        }

        /** One impact on every target: the contact pass's own direct hit. */
        fun crack() {
            for (target in targets) game.dealHit(target, 1f, HitSource.IMPACT, canCrit = false)
        }
    }

    private companion object {
        /**
         * Each phase's length: long enough that every weapon's damage path lands well past [MINIMUM_DAMAGE] against the
         * crowd. Trap weapons have time to arm and detonate; the Hollow Rift fixture guarantees a target in a real
         * rift instead of depending on the route's timing to cross an anchored target.
         */
        const val PHASE_SECONDS = 16f
        const val CROWD_SIZE = 8
        const val CROWD_RING = 150f
        const val INTEGRITY = 2_000f
        const val MINIMUM_DAMAGE = 50.0
    }
}
