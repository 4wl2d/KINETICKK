// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every item matters at one stack: in scripted encounters chosen by its triggers, conditions, stats and
 * rules (fixed seeds, 40 s each), owning one copy moves at least one measured output (damage dealt, damage
 * taken, integrity left, time survived, kills, Data, Matter, Overdrive time, Dashes or offer quality) by at
 * least 5 % in the item's favor, against the same encounter without it. Each encounter is flown five times from
 * different seeds and orbit phases; an item whose best gain lands within 2.5 points of the floor is judged again
 * over fifteen, so a single lucky or unlucky draw of a chaotic encounter decides nothing. A Core that falls keeps
 * no integrity and still takes the scripted hits it would have met, so falling sooner or by less never reads as a gain;
 * [aLiabilityOrAnInertStatAloneScoresNoGain] holds the measure to that. Both runs hold the same supporting items (the
 * sources of the item's elements, a crit source for crit items, a shield for shield items) at full stacks.
 * Encounters run in dp at simulation speed 1, so each is reproducible; [JUSTIFIED_EXCEPTIONS] would list an
 * item no encounter can show, with the reason, and is empty. Armed encounters are flown with an Ion Swarm and
 * with a Sentry Grid: under the weapon proc rule (one on-hit trigger per activation, or per persistent source
 * every 0.25 s) a weapon's cadence decides how often WEAPON_HIT effects fire.
 *
 * Ricochets and slams are dispatched directly every 0.6 s; the collisions that fire them belong to the
 * obstacle field, which the scripted paths never reach (they stay inside the run origin's safe area).
 */
class PerceptibleItemImpactTest {
    @Test
    fun aLiabilityOrAnInertStatAloneScoresNoGain() {
        val ledger = canonicalGameplayContent.items.single { it.name == "Gambler's Ledger" }
        val controls = listOf(
            "Gambler's Ledger's liability alone" to ledger.copy(rules = immutableListOf(), risk = false),
            // MAX_INTEGRITY +0.001: a stat no encounter can feel.
            "an inert stat" to ledger.copy(stats = immutableListOf(StatDelta(ItemEffect.MAX_INTEGRITY, 0.001f)), rules = immutableListOf(),
                risk = false),
        )
        for ((label, control) in controls) {
            val content = canonicalGameplayContent.copy(items = canonicalGameplayContent.items.map { item ->
                if (item.id == control.id) control else item
            }.toImmutableList())
            // Every combat encounter: an owned card only leaves the offer pool, which says nothing about the measure.
            for (arena in Arena.entries.filter { it != Arena.OFFERS }) {
                for (weapon in arena.weapons()) {
                    val setup = Setup(arena, emptyList(), elite = false, weapon = weapon)
                    val (output, share) = bestGain(arena, run(setup, null, content), run(setup, control, content))
                    assertTrue(share < MIN_GAIN, "$label scores $arena/$weapon $output ${"%+.1f".format(share * 100f)} %")
                }
            }
        }
    }

    @Test
    fun everyItemAtOneStackMovesAMeasuredOutputByAtLeastFivePercent() {
        val baselines = HashMap<Setup, Outcome>()
        val report = ArrayList<String>()
        val misses = ArrayList<String>()
        val confirmedBaselines = HashMap<Setup, Outcome>()
        for (item in canonicalGameplayContent.items) {
            val measured = setupsFor(item).map { setup ->
                val baseline = baselines.getOrPut(setup) { run(setup, null) }
                val (output, share) = bestGain(setup.arena, baseline, run(setup, item))
                Triple(setup, output, share)
            }
            var best = measured.maxBy { it.third }
            var fights = VARIANTS
            if (best.third in MIN_GAIN - CONFIRM_BAND..MIN_GAIN + CONFIRM_BAND) {
                // A best gain near the floor is one draw of a chaotic measure: every encounter that came near is
                // flown again from more seeds and orbit phases, and the item is judged on the larger sample.
                fights = CONFIRM_VARIANTS
                best = measured.filter { it.third >= MIN_GAIN - CONFIRM_BAND }.map { (setup, _, _) ->
                    val baseline = confirmedBaselines.getOrPut(setup) { run(setup, null, variants = CONFIRM_VARIANTS) }
                    val (output, share) = bestGain(setup.arena, baseline, run(setup, item, variants = CONFIRM_VARIANTS))
                    Triple(setup, output, share)
                }.maxBy { it.third }
            }
            val (setup, output, share) = best
            val line = "${item.id} ${item.name}: ${setup.arena}/${setup.weapon} ${setup.support} $output ${"%+.1f".format(share * 100f)} % " +
                "($fights fights)"
            report += line
            if (share < MIN_GAIN && item.name !in JUSTIFIED_EXCEPTIONS) misses += line
        }
        report.forEach { println("IMPACT $it") }
        baselines.forEach { (setup, outcome) -> println("BASELINE ${setup.arena}/${setup.weapon} ${setup.support} $outcome") }
        assertTrue(JUSTIFIED_EXCEPTIONS.keys.all { name -> canonicalGameplayContent.items.any { it.name == name } }, "Every exception names an item")
        assertTrue(misses.isEmpty(), "Items that move no measured output by 5 %:\n" + misses.joinToString("\n"))
    }

    /**
     * One scripted encounter and the items held in it with and without the item under test. An armed arena is
     * flown with each of [ARENA_WEAPONS]: the proc rule lets one hit per activation fire WEAPON_HIT effects, so how
     * often they fire depends on the weapon's cadence (a discrete volley or a persistent source).
     */
    private data class Setup(val arena: Arena, val support: List<String>, val elite: Boolean, val weapon: WeaponId?)

    private fun Arena.weapons(): List<WeaponId?> = if (armed) ARENA_WEAPONS else listOf(null)

    private enum class Arena(
        /** False silences the weapon so only impacts and procs deal damage. */
        val armed: Boolean,
        val motion: Motion,
        val targets: Int,
        val integrity: Float,
        /** Seconds between Dash attempts (0 = none); a Dash is only requested while one is ready. */
        val dashEvery: Float = 0f,
        val brake: Boolean = false,
        /** Bursts of four hits of this size land every 6 s (0 = none). */
        val hits: Float = 0f,
        /** A ricochet and a slam on the nearest enemy every 0.6 s. */
        val obstacles: Boolean = false,
        /** Overdrive is forced every 10 s. */
        val forcedOverdrive: Boolean = false,
        /** Every target presses a steady attack on the Core, scaled by what its statuses leave of it. */
        val pressure: Boolean = false,
    ) {
        /** Impacts only: the weapon is silenced; the Core orbits through a ring and dashes into it. */
        IMPACT(false, Motion.ORBIT, 10, 600f, dashEvery = 0.8f),
        /** Impacts at high speed: straight passes through a column, dashing as often as heat allows. */
        FAST(false, Motion.PASS, 8, 600f, dashEvery = 0.5f),
        /** The weapon only: the Core circles slowly outside a cluster it never touches. */
        WEAPON(true, Motion.HOVER, 8, 900f),
        /** Weapon hits and impacts together on sturdy targets: statuses, Breaks and reactions. */
        STATUS(true, Motion.ORBIT, 10, 1_500f, dashEvery = 2f),
        /**
         * The status ring with weapon hits and impacts reduced to taps: they still apply statuses, but the damage is
         * almost all statuses, Breaks and reactions.
         */
        ALCHEMY(true, Motion.ORBIT, 10, 1_500f, dashEvery = 2f),
        /** Fragile targets that keep coming back: kills, Data, Matter and combos. */
        CROWD(true, Motion.ORBIT, 12, 260f, dashEvery = 2f),
        /**
         * Fragile targets felled by the weapon alone while the Core circles outside them, every other one a sturdy
         * neighbor that outlives them: kills without collisions, and something left standing to spread onto.
         */
        CULL(true, Motion.HOVER, 8, 140f),
        /** The crowd with levels counting: level-ups (and the weapon amplifications they bring) happen. */
        LEVELS(true, Motion.ORBIT, 12, 260f, dashEvery = 2f),
        /** Sturdier targets that fall seconds apart. */
        SPARSE(true, Motion.ORBIT, 10, 600f, dashEvery = 2f),
        /** The weapon circle while a straggler falls every 3 s: the combo window decides whether the chain survives. */
        COMBO(true, Motion.HOVER, 8, 900f),
        /** A slow circle close around a cluster while bursts of hits land every 6 s (shields recharge between). */
        DEFENSE(true, Motion.CLOSE_HOVER, 8, 1_500f, hits = 4.5f),
        /** The defense circle with a held Brake most of the time. */
        BRACE(true, Motion.CLOSE_HOVER, 8, 1_500f, brake = true, hits = 4.5f),
        /**
         * A ring of attackers around the Core circling slowly inside it: each presses a steady attack, which Chill
         * slows, a Freeze stops, Silence mutes and Hollow weakens, so their statuses show as damage not taken.
         */
        SIEGE(true, Motion.INNER_HOVER, 10, 1_500f, pressure = true),
        /** The status ring with a held Brake every 1.8 s. */
        BRAKE(true, Motion.ORBIT, 10, 1_500f, brake = true),
        /** A Dash every 0.7 s whenever one is ready: heat outruns cooling, so the Core overheats again and again. */
        HEAT(true, Motion.ORBIT, 10, 1_500f, dashEvery = 0.7f),
        /** The status ring with Overdrive forced every 10 s. */
        OVERDRIVE(true, Motion.ORBIT, 10, 1_500f, dashEvery = 2f, forcedOverdrive = true),
        /** The status ring with a ricochet and a slam every 0.6 s and Dashes. */
        OBSTACLE(true, Motion.ORBIT, 10, 1_500f, dashEvery = 0.6f, obstacles = true),
        /** Ricochets and slams while bursts of hits land, circling close outside a cluster. */
        SHELTER(true, Motion.CLOSE_HOVER, 8, 1_500f, hits = 4.5f, obstacles = true),
        /** Loose Data just beyond the build's own pickup radius: only a wider reach collects it. */
        REACH(true, Motion.HOVER, 8, 900f),
        /** No combat: thirty item offers at Reborn 10. */
        OFFERS(false, Motion.HOVER, 0, 0f),
        ;

        /** Only these arenas count integrity: elsewhere glancing contacts are topped up and integrity is ignored. */
        val defended: Boolean get() = hits > 0f || pressure
    }

    private enum class Motion { ORBIT, PASS, HOVER, CLOSE_HOVER, INNER_HOVER }

    /** The measured outputs; each arena reads the ones it is built to show. */
    private enum class Output {
        DAMAGE, TAKEN, INTEGRITY, SURVIVAL, KILLS, DATA, MATTER, DATA_PER_KILL, MATTER_PER_KILL, OVERDRIVE, DASHES, OFFERS,
    }

    private fun Arena.outputs(): List<Output> = when (this) {
        Arena.IMPACT, Arena.FAST, Arena.HEAT -> listOf(Output.DAMAGE, Output.DASHES)
        Arena.WEAPON, Arena.STATUS, Arena.ALCHEMY, Arena.BRAKE, Arena.OVERDRIVE -> listOf(Output.DAMAGE)
        Arena.CROWD, Arena.SPARSE, Arena.LEVELS, Arena.CULL, Arena.COMBO -> listOf(
            Output.DAMAGE, Output.KILLS, Output.DATA, Output.MATTER, Output.DATA_PER_KILL, Output.MATTER_PER_KILL, Output.OVERDRIVE,
        )
        Arena.DEFENSE, Arena.BRACE -> listOf(Output.DAMAGE, Output.TAKEN, Output.INTEGRITY, Output.SURVIVAL)
        Arena.SIEGE -> listOf(Output.DAMAGE, Output.TAKEN)
        Arena.OBSTACLE -> listOf(Output.DAMAGE, Output.DASHES, Output.OVERDRIVE, Output.DATA)
        Arena.SHELTER -> listOf(Output.DAMAGE, Output.TAKEN, Output.INTEGRITY, Output.SURVIVAL, Output.OVERDRIVE)
        Arena.REACH -> listOf(Output.DATA)
        Arena.OFFERS -> listOf(Output.OFFERS)
    }

    /** What an encounter measured. Higher is better except for damage taken. */
    private data class Outcome(
        val damage: Double,
        val taken: Double,
        val integrity: Float,
        /** Seconds the Core stayed in the fight. */
        val survival: Float,
        val kills: Int,
        val data: Long,
        val matter: Long,
        val overdrive: Float,
        val dashes: Int,
        val offers: Float,
    ) {
        fun value(output: Output): Double = when (output) {
            Output.DAMAGE -> damage
            Output.TAKEN -> -taken
            Output.INTEGRITY -> integrity.toDouble()
            Output.SURVIVAL -> survival.toDouble()
            Output.KILLS -> kills.toDouble()
            Output.DATA -> data.toDouble()
            Output.MATTER -> matter.toDouble()
            Output.DATA_PER_KILL -> data.toDouble() / max(1, kills)
            Output.MATTER_PER_KILL -> matter.toDouble() / max(1, kills)
            Output.OVERDRIVE -> overdrive.toDouble()
            Output.DASHES -> dashes.toDouble()
            Output.OFFERS -> offers.toDouble()
        }
    }

    /** The output [owned] improves most over [baseline], as a share of the baseline (never of a near-zero one). */
    private fun bestGain(arena: Arena, baseline: Outcome, owned: Outcome): Pair<String, Float> = arena.outputs().map { output ->
        val before = baseline.value(output)
        val after = owned.value(output)
        output.name.lowercase() to ((after - before) / max(abs(before), FLOORS.getValue(output))).toFloat()
    }.maxBy { it.second }

    /** Fights are flown [variants] times from different seeds and orbit phases and summed, which damps the chaos. */
    private fun run(setup: Setup, item: ItemDefinition?, content: GameplayContentSnapshot = canonicalGameplayContent,
        variants: Int = VARIANTS): Outcome =
        if (setup.arena == Arena.OFFERS) offers(setup, item, content)
        else (0 until variants).map { variant -> fight(setup, item, variant, content) }.reduce { a, b ->
            Outcome(a.damage + b.damage, a.taken + b.taken, a.integrity + b.integrity, a.survival + b.survival, a.kills + b.kills,
                a.data + b.data, a.matter + b.matter, a.overdrive + b.overdrive, a.dashes + b.dashes, a.offers + b.offers)
        }

    /** Thirty consecutive item offers at Reborn 10 with every item unlocked: the summed rarity of the cards. */
    private fun offers(setup: Setup, item: ItemDefinition?, content: GameplayContentSnapshot): Outcome {
        val game = MutableGameState(content, seed = SEED, initialMatter = 0, initialRebirthLevel = 10).apply {
            startRun()
            lifetimeMatter = 4_000L
            level = 20
        }
        setup.support.forEach { name -> game.acquireItem(game.itemNamed(name).id) }
        item?.let { game.acquireItem(it.id) }
        var ranks = 0
        repeat(OFFER_COUNT) {
            game.buildItemChoices()
            ranks += game.choices.sumOf { choice -> content.items[requireNotNull(choice.itemId)].rarity.rank }
        }
        return Outcome(0.0, 0.0, 0f, 0f, 0, 0L, 0L, 0f, 0, ranks.toFloat() / OFFER_COUNT + game.rerollsRemaining)
    }

    private fun fight(setup: Setup, item: ItemDefinition?, variant: Int, content: GameplayContentSnapshot): Outcome {
        val arena = setup.arena
        val phase = variant * 0.7f
        val game = MutableGameState(content, seed = SEED + variant, initialMatter = 0).apply {
            coreShape = CoreShape.ORB
            startingWeapon = setup.weapon ?: WeaponId.FLUX_WAKE
            unlockWeaponsForTesting(listOf(startingWeapon))
            resize(1_280f, 720f)
            startRun()
            settings = settings.copy(simulationSpeed = 1f)
            enemies.clear()
            projectiles.clear()
            pickups.clear()
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            runGrace = 0f
            nextPointOfferIndex = Int.MAX_VALUE / 2
        }
        setup.support.forEach { name -> game.own(name, stacks = game.itemNamed(name).maxStacks) }
        val reach = game.effectivePickupRadius() + 30f
        item?.let { game.acquireItem(it.id) }
        if (arena.pressure) {
            // A deep integrity pool: the attackers' damage is measured, never fatal.
            game.maxHp = 10_000f
            game.hp = 10_000f
        }
        game.shield = game.shieldCapacity()
        val slots = slotsFor(arena.motion, arena.targets)
        val targets = arrayOfNulls<Enemy>(slots.size)
        val path = PathFor.getValue(arena.motion)
        game.coreX = path.startX
        game.coreY = path.startY
        game.previousCoreX = game.coreX
        game.previousCoreY = game.coreY
        game.cameraX = game.coreX
        game.cameraY = game.coreY
        // The singularity starts ahead of the Core, so its first sweep never crosses it.
        var heading = if (arena.motion == Motion.PASS) 0f else TAU / 4f
        game.updatePointer(game.screenWidth / 2f + cos(heading) * 120f, game.screenHeight / 2f + sin(heading) * 120f)
        game.previousSingularityX = game.coreX + cos(heading) * 120f
        game.previousSingularityY = game.coreY + sin(heading) * 120f
        var dashes = 0
        var overdrive = 0f
        var nearObstacle = false
        val frames = (SECONDS * FPS).toInt()
        // The frame the Core fell on, or [frames] while it stands.
        var fellAt = frames
        for (frame in 0 until frames) {
            if (game.phase == GamePhase.CHOICE) dismissChoice(game)
            if (game.phase != GamePhase.RUNNING) {
                fellAt = frame
                break
            }
            val t = frame / FPS + phase
            // Anchored, idle targets that come back the moment they fall.
            for (index in slots.indices) {
                val current = targets[index]
                val (x, y) = slots[index]
                val target = if (current == null || current.dead || current.hp <= 0f || current !in game.enemies) {
                    val type = if (setup.elite && index == 0) EnemyType.ELITE else EnemyType.DRIFTER
                    val integrity = if (arena == Arena.CULL && index % 2 == 1) STURDY else arena.integrity
                    game.addEnemyForTesting(x, y, hp = integrity, type = type).also { spawned ->
                        if (type == EnemyType.ELITE) assignAspect(spawned, -1, immune = false)
                        targets[index] = spawned
                    }
                } else current
                target.x = x
                target.y = y
                target.previousX = x
                target.previousY = y
                target.vx = 0f
                target.vy = 0f
                target.disabledUntil = Float.MAX_VALUE
            }
            if (setup.weapon == null) game.weaponPower = 0f
            if (arena == Arena.ALCHEMY) {
                game.weaponPower = TAP
                game.damageMultiplier = TAP
            }
            // Level-ups amplify the weapon and open choices: outside their arena they would only add chaos.
            if (arena != Arena.LEVELS) game.nextLevelData = Int.MAX_VALUE / 2
            if (!arena.defended) game.hp = game.maxHp
            heading = if (arena.motion == Motion.PASS) {
                // One-way passes: past the far edge the Core wraps back to the near one with its velocity.
                if (game.coreX > PASS_TURN) {
                    game.coreX -= 2f * PASS_TURN
                    game.previousCoreX = game.coreX
                    game.cameraX -= 2f * PASS_TURN
                    game.previousCameraX = game.cameraX
                    game.previousSingularityX -= 2f * PASS_TURN
                }
                // A pilot who holds about 1,500 u/s instead of flying flat out.
                if (game.speed > PASS_SPEED) {
                    game.velocityX *= PASS_SPEED / game.speed
                    game.velocityY *= PASS_SPEED / game.speed
                }
                val angle = atan2(-game.coreY * 0.6f, PASS_PULL)
                point(game, angle, length(PASS_PULL, game.coreY * 0.6f))
                angle
            } else {
                steer(game, t, path.radius, path.angular, heading)
            }
            game.setBrake(arena.brake && if (arena.hits > 0f) t % 2f < 1.6f else t % 1.8f in 1.2f..1.7f)
            // Heat-limited Dashes count only when one is ready; the heat arena presses Dash regardless, to overheat.
            if (arena.dashEvery > 0f && frame % (arena.dashEvery * FPS).toInt() == 0 && game.buildDashReady()) {
                // Dash along the travel direction, into whatever lies ahead. For that one frame the singularity
                // sits beyond the Dash's reach (touching it ends the run); then steering takes the pointer back.
                if (game.speed > 1f) point(game, atan2(game.velocityY, game.velocityX), 480f)
                game.requestDash()
            }
            if (arena == Arena.CROWD || arena == Arena.SPARSE || arena == Arena.LEVELS || arena == Arena.CULL || arena == Arena.COMBO) {
                // Every drop is collected: Data measures what the kills and the build are worth, not the flight path.
                for (index in game.pickups.indices) {
                    val pickup = game.pickups[index]
                    if (pickup.type == PickupType.DATA) {
                        pickup.x = game.coreX
                        pickup.y = game.coreY
                    }
                }
            }
            if (arena == Arena.COMBO && frame % (3 * FPS).toInt() == 0) {
                // A straggler falls to the weapon every 3 s, a little longer than the base combo window.
                val straggler = game.addEnemyForTesting(game.coreX + 120f, game.coreY, hp = 1f)
                game.damageEnemy(straggler, 2f, killEligible = true, source = HitSource.WEAPON)
                game.onEnemyKilled(straggler)
                game.enemies.remove(straggler)
            }
            if (arena == Arena.REACH && frame % 30 == 0) {
                val angle = frame * 2.399f
                game.addPickup(Pickup(PickupType.DATA, game.coreX + cos(angle) * reach, game.coreY + sin(angle) * reach))
            }
            if (arena.pressure && frame % 30 == 15) {
                var pressed = 0f
                for (enemy in game.enemies) {
                    if (!enemy.alive || game.elapsed < enemy.silencedUntil) continue
                    pressed += game.hostileDamage(enemy, PRESSURE) * game.enemyMotionScale(enemy)
                }
                if (pressed > 0f) game.takeDamage(pressed)
            }
            if (arena.hits > 0f && burstHit(frame)) game.takeDamage(arena.hits)
            if (arena.forcedOverdrive && frame % (10 * FPS).toInt() == 60 && game.overdriveTime <= 0f) game.overdriveCharge = 100f
            if (arena.obstacles && frame % 36 == 0) {
                game.fireClauses(EffectTrigger.RICOCHET)
                val slammed = game.enemies.filter { it.alive }.minByOrNull { distanceSquared(it.x, it.y, game.coreX, game.coreY) }
                if (slammed != null) game.fireClauses(EffectTrigger.SLAM, slammed, hit = 20f)
            }
            val dashSequence = game.characterRuntime.dashSequence
            game.update(1f / FPS)
            // The scripted paths stay inside the obstacle-free start area: a collision never enters a measurement.
            game.forEachObstacleNear(game.coreX, game.coreY, OBSTACLE_MARGIN) { _, obstacle ->
                if (length(obstacle.x - game.coreX, obstacle.y - game.coreY) < obstacle.radius + game.coreRadius + OBSTACLE_MARGIN) {
                    nearObstacle = true
                }
            }
            dashes += game.characterRuntime.dashSequence - dashSequence
            game.takeVisualFxCues()
            game.takeSoundCues()
            game.takeProgressUpdate()
            if (game.overdriveTime > 0f) overdrive += 1f / FPS
        }
        assertFalse(nearObstacle, "${arena} came within $OBSTACLE_MARGIN of an obstacle")
        // A fallen Core keeps nothing and still meets every scripted hit it did not live to take.
        val integrity = if (arena.defended) max(0f, game.hp + game.shield) else 0f
        var taken = if (arena.defended) game.damageTaken else 0.0
        if (arena.hits > 0f) for (frame in fellAt until frames) if (burstHit(frame)) taken += arena.hits
        return Outcome(game.damageDealt, taken, integrity, fellAt / FPS, game.kills, game.dataCollected, game.runMatter, overdrive,
            dashes, 0f)
    }

    /** Four hits 0.4 s apart every 6 s: shields recharge between the bursts. */
    private fun burstHit(frame: Int): Boolean {
        val burstFrame = frame % (6 * FPS).toInt()
        return burstFrame in 90..90 + 72 && (burstFrame - 90) % 24 == 0
    }

    /** Where the Core starts and the circle it flies: through the ring, or slowly around a cluster. */
    private class Path(val startX: Float, val startY: Float, val radius: Float, val angular: Float)

    /** Level-ups and pickups may open choices; the encounter declines them so both builds stay equal. */
    private fun dismissChoice(game: MutableGameState) {
        game.choices = emptyList()
        game.pendingLevelChoices = 0
        game.pendingRelicChoices = 0
        game.directedReward = null
        game.phase = GamePhase.RUNNING
    }

    private fun slotsFor(motion: Motion, count: Int): List<Pair<Float, Float>> = (0 until count).map { index ->
        val angle = index * TAU / count
        when (motion) {
            Motion.ORBIT -> cos(angle) * RING to sin(angle) * RING
            Motion.PASS -> 0f to (index - (count - 1) / 2f) * 30f
            Motion.HOVER -> cos(angle) * 60f to sin(angle) * 60f
            Motion.CLOSE_HOVER -> cos(angle) * 40f to sin(angle) * 40f
            Motion.INNER_HOVER -> cos(angle) * 260f to sin(angle) * 260f
        }
    }

    /** A pointer that flies a circle of [radius] around the origin through the real steering field. */
    private fun steer(game: MutableGameState, t: Float, radius: Float, angular: Float, heading: Float): Float =
        steerToward(
            game, heading,
            cos(t * angular) * radius, sin(t * angular) * radius,
            -sin(t * angular) * radius * angular, cos(t * angular) * radius * angular,
            -cos(t * angular) * radius * angular * angular, -sin(t * angular) * radius * angular * angular,
        )

    /**
     * Turns the pointer toward the acceleration that brings the Core to the desired point and velocity. The
     * heading slews a little per frame, so the singularity swings around the Core and never sweeps across it.
     */
    private fun steerToward(
        game: MutableGameState,
        heading: Float,
        desiredX: Float,
        desiredY: Float,
        desiredVx: Float,
        desiredVy: Float,
        feedX: Float,
        feedY: Float,
    ): Float {
        val accelerationX = (desiredX - game.coreX) * 5f + (desiredVx - game.velocityX) * 3f + feedX
        val accelerationY = (desiredY - game.coreY) * 5f + (desiredVy - game.velocityY) * 3f + feedY
        val desiredHeading = atan2(accelerationY, accelerationX)
        val next = heading + atan2(sin(desiredHeading - heading), cos(desiredHeading - heading)).coerceIn(-0.12f, 0.12f)
        // Never closer than 65: the singularity ends the run on contact.
        val distance = ((length(accelerationX, accelerationY) - 92f) / game.magnetStrength).coerceIn(65f, 480f)
        point(game, next, distance)
        return next
    }

    /** Places the pointer [distance] from the Core along [angle], inside the screen's calm area. */
    private fun point(game: MutableGameState, angle: Float, distance: Float) {
        val edgeMargin = (1f - game.content.tempo.fatigue.edgeStrainStart) * 0.5f
        game.updatePointer(
            (game.coreX + cos(angle) * distance - game.cameraX + game.screenWidth / 2f)
                .coerceIn(game.screenWidth * edgeMargin, game.screenWidth * (1f - edgeMargin)),
            (game.coreY + sin(angle) * distance - game.cameraY + game.screenHeight / 2f)
                .coerceIn(game.screenHeight * edgeMargin, game.screenHeight * (1f - edgeMargin)),
        )
    }

    /** The encounters an item's triggers, conditions, stats and rules call for, each with its supporting items. */
    private fun setupsFor(item: ItemDefinition): List<Setup> {
        val arenas = LinkedHashSet<Arena>()
        val conditions = item.clauses.flatMap { it.conditions } +
            item.rules.mapNotNull { rule ->
                when (rule) {
                    is ItemRule.WhileModifier -> rule.condition
                    is ItemRule.AlwaysCrit -> rule.condition
                    is ItemRule.CritDamageVersus -> rule.condition
                    is ItemRule.DamageVersus -> rule.condition
                    else -> null
                }
            }
        item.clauses.forEach { clause ->
            arenas += when (clause.trigger) {
                EffectTrigger.IMPACT -> listOf(Arena.IMPACT, Arena.STATUS)
                EffectTrigger.WEAPON_HIT -> listOf(Arena.WEAPON, Arena.STATUS)
                EffectTrigger.CRIT -> listOf(Arena.WEAPON, Arena.IMPACT)
                EffectTrigger.KILL -> listOf(Arena.CROWD, Arena.CULL)
                EffectTrigger.DASH -> listOf(Arena.IMPACT, Arena.HEAT)
                EffectTrigger.DASH_HIT -> listOf(Arena.IMPACT, Arena.FAST, Arena.STATUS)
                EffectTrigger.BRAKE_RELEASE -> listOf(Arena.BRAKE)
                EffectTrigger.TURN -> listOf(Arena.IMPACT, Arena.STATUS)
                EffectTrigger.HURT, EffectTrigger.SHIELD_BREAK -> listOf(Arena.DEFENSE)
                EffectTrigger.OVERDRIVE -> listOf(Arena.OVERDRIVE)
                EffectTrigger.OVERHEAT -> listOf(Arena.HEAT)
                EffectTrigger.REACTION, EffectTrigger.BREAK -> listOf(Arena.ALCHEMY, Arena.STATUS, Arena.CROWD, Arena.WEAPON)
                EffectTrigger.LEVEL_UP -> listOf(Arena.LEVELS)
                EffectTrigger.PICKUP -> listOf(Arena.CROWD)
                EffectTrigger.RICOCHET, EffectTrigger.SLAM -> listOf(Arena.OBSTACLE, Arena.SHELTER)
            }
        }
        item.stats.forEach { stat ->
            arenas += when (stat.effect) {
                ItemEffect.IMPACT_DAMAGE, ItemEffect.MASS, ItemEffect.MAGNETISM, ItemEffect.DASH_POWER -> listOf(Arena.IMPACT, Arena.FAST)
                ItemEffect.WEAPON_POWER, ItemEffect.ATTACK_SPEED -> listOf(Arena.WEAPON)
                ItemEffect.COOLING, ItemEffect.DASH_EFFICIENCY -> listOf(Arena.HEAT)
                ItemEffect.MAX_INTEGRITY, ItemEffect.REGEN, ItemEffect.SHIELD_CAPACITY, ItemEffect.DAMAGE_REDUCTION -> listOf(Arena.DEFENSE)
                ItemEffect.CRIT_CHANCE, ItemEffect.CRIT_DAMAGE -> listOf(Arena.WEAPON, Arena.IMPACT)
                ItemEffect.PICKUP_RADIUS -> listOf(Arena.REACH, Arena.CROWD)
                ItemEffect.DATA_GAIN, ItemEffect.MATTER_GAIN -> listOf(Arena.CROWD)
                ItemEffect.COMBO_WINDOW -> listOf(Arena.COMBO, Arena.SPARSE)
                ItemEffect.LUCK -> listOf(Arena.OFFERS, Arena.CROWD)
                ItemEffect.OVERDRIVE_GAIN -> listOf(Arena.CROWD, Arena.OBSTACLE)
            }
        }
        item.rules.forEach { rule ->
            arenas += when (rule) {
                is ItemRule.WhileModifier -> conditionArenas(rule.condition) + if (rule.stat == BuffStat.DAMAGE_TAKEN) listOf(Arena.DEFENSE) else emptyList()
                is ItemRule.ElementPotency, is ItemRule.StatusDuration, is ItemRule.BreakThreshold, is ItemRule.BreakPower,
                is ItemRule.BreakSpread, is ItemRule.ReactionPower, is ItemRule.ReactionResidue, is ItemRule.ReactionRecoil ->
                    listOf(Arena.ALCHEMY, Arena.STATUS, Arena.CROWD)
                is ItemRule.AlwaysCrit, is ItemRule.CritDamageVersus, is ItemRule.DamageVersus, is ItemRule.NonCriticalDamage,
                is ItemRule.GuaranteedCritAfter -> listOf(Arena.WEAPON, Arena.IMPACT, Arena.STATUS)
                is ItemRule.ComboDamage -> listOf(Arena.CROWD, Arena.COMBO)
                is ItemRule.BonusRerolls, is ItemRule.OfferCards -> listOf(Arena.OFFERS)
                is ItemRule.FreeDash -> listOf(Arena.HEAT)
                is ItemRule.Convert -> listOf(Arena.DEFENSE, Arena.STATUS, Arena.WEAPON)
            }
        }
        conditions.forEach { arenas += conditionArenas(it) }
        // Elemental appliers show in the arena where statuses carry the damage.
        if (item.clauses.any { clause -> clause.actions.any { it is EffectAction.ApplyStatus } }) arenas += Arena.ALCHEMY
        // Slows, freezes and Hollow show as damage not taken from attacking enemies.
        if (StatusElement.CRYO in item.elements || StatusElement.VOID in item.elements) arenas += Arena.SIEGE
        val elite = conditions.any { it == EffectCondition.TargetElite }
        val support = supportFor(item, conditions)
        // Against the siege an applier starts from nothing: other sources of its own element would already hold
        // the attackers at the status it adds.
        val applied = item.clauses.flatMap { it.actions }.mapNotNull { (it as? EffectAction.ApplyStatus)?.pick?.element }.toSet()
        val siegeSupport = support.filter { name -> applied.none { element -> name in ElementSources.getValue(element) } }
        return arenas.flatMap { arena ->
            arena.weapons().map { weapon ->
                Setup(arena, when (arena) {
                    Arena.OFFERS -> emptyList()
                    Arena.SIEGE -> siegeSupport
                    else -> support
                }, elite, weapon)
            }
        }
    }

    private fun conditionArenas(condition: EffectCondition): List<Arena> = when (condition) {
        is EffectCondition.SpeedAtLeast -> listOf(Arena.FAST, Arena.IMPACT)
        is EffectCondition.SpeedBelow -> listOf(Arena.WEAPON, Arena.BRAKE)
        is EffectCondition.IntegrityBelow -> listOf(Arena.DEFENSE)
        is EffectCondition.HeatAtLeast -> listOf(Arena.HEAT)
        EffectCondition.ShieldUp -> listOf(Arena.DEFENSE)
        EffectCondition.OverdriveActive -> listOf(Arena.OVERDRIVE)
        EffectCondition.Braking -> listOf(Arena.BRAKE, Arena.BRACE)
        is EffectCondition.ComboAtLeast -> listOf(Arena.CROWD, Arena.COMBO)
        is EffectCondition.TargetBelow -> listOf(Arena.CROWD)
        else -> emptyList()
    }

    /** Items every encounter of [item] holds in both runs: its elements' sources, a crit source, a shield. */
    private fun supportFor(item: ItemDefinition, conditions: List<EffectCondition>): List<String> {
        val elements = LinkedHashSet<StatusElement>(item.elements)
        item.rules.forEach { rule ->
            when (rule) {
                is ItemRule.ElementPotency -> elements += rule.element
                is ItemRule.StatusDuration -> elements += rule.element
                is ItemRule.BreakThreshold -> elements += rule.element
                is ItemRule.BreakPower -> elements += rule.element
                is ItemRule.BreakSpread -> elements += rule.element
                else -> Unit
            }
        }
        conditions.forEach { condition ->
            when (condition) {
                is EffectCondition.TargetHas -> elements += condition.element ?: StatusElement.THERMAL
                is EffectCondition.ElementIs -> elements += condition.element
                EffectCondition.TargetFrozen -> elements += StatusElement.CRYO
                else -> Unit
            }
        }
        val actions = item.clauses.flatMap { it.actions }
        val detonates = actions.filterIsInstance<EffectAction.Detonate>()
        if (detonates.isNotEmpty()) {
            // A detonation needs its first element alone: two of them on one target react away first.
            elements.clear()
            elements += detonates.first().elements.first()
        }
        val reactive = item.rules.any { it is ItemRule.ReactionPower || it is ItemRule.ReactionResidue || it is ItemRule.ReactionRecoil } ||
            item.clauses.any { it.trigger == EffectTrigger.REACTION }
        if (reactive && elements.size < 2) {
            elements += StatusElement.THERMAL
            elements += StatusElement.ENTROPY
        }
        val picks = actions.mapNotNull {
            when (it) {
                is EffectAction.ApplyStatus -> it.pick
                is EffectAction.Chain -> it.pick
                is EffectAction.Shards -> it.pick
                is EffectAction.Well -> it.pick
                else -> null
            }
        }
        val support = ArrayList<String>()
        if (ElementPick.DOMINANT in picks || ElementPick.SECONDARY in picks) {
            support += "Kindling"
            if (ElementPick.SECONDARY in picks) support += "Necrotic Lattice"
        }
        elements.forEach { element -> support += ElementSources.getValue(element) }
        val crits = item.clauses.any { it.trigger == EffectTrigger.CRIT } ||
            item.rules.any { it is ItemRule.CritDamageVersus || it is ItemRule.Convert && it.kind == ConversionKind.CRIT_CHANCE_TO_DAMAGE } ||
            item.stats.any { it.effect == ItemEffect.CRIT_DAMAGE }
        if (crits) support += "Focus Lens"
        if (item.rules.any { it is ItemRule.Convert && it.kind == ConversionKind.REGENERATION_TO_DECAY }) support += "Nanite Mesh"
        val shields = actions.any { it is EffectAction.Shield } || item.clauses.any { it.trigger == EffectTrigger.SHIELD_BREAK } ||
            conditions.any { it == EffectCondition.ShieldUp } ||
            item.rules.any { it is ItemRule.Convert && it.kind == ConversionKind.OVERHEAL_TO_SHIELD }
        if (shields && item.stats.none { it.effect == ItemEffect.SHIELD_CAPACITY }) support += "Shield Cell"
        return support.distinct().filter { it != item.name }
    }

    private companion object {
        const val SEED = 90_173
        const val FPS = 60f
        const val SECONDS = 40f
        /** The sturdy neighbors in the cull arena. */
        const val STURDY = 1_500f
        /** Damage each siege attacker presses every half second at full strength. */
        const val PRESSURE = 0.6f
        const val RING = 220f
        const val PASS_TURN = 520f
        const val PASS_PULL = 240f
        const val PASS_SPEED = 1_500f
        /** The clearance every scripted path keeps from obstacles (the run origin's safe area holds them all). */
        const val OBSTACLE_MARGIN = 60f

        val PathFor: Map<Motion, Path> = mapOf(
            Motion.ORBIT to Path(RING, 0f, RING, 2.9f),
            Motion.PASS to Path(-PASS_TURN, 0f, 0f, 0f),
            Motion.HOVER to Path(330f, 0f, 330f, 0.8f),
            Motion.CLOSE_HOVER to Path(170f, 0f, 170f, 1.2f),
            Motion.INNER_HOVER to Path(90f, 0f, 90f, 0.8f),
        )
        const val OFFER_COUNT = 30
        const val VARIANTS = 5
        /** Weapon power and impact power in the alchemy arena: hits that land, with almost no damage of their own. */
        const val TAP = 0.02f
        const val MIN_GAIN = 0.05f
        /** A best gain this close to [MIN_GAIN] (either side) is confirmed over [CONFIRM_VARIANTS] fights. */
        const val CONFIRM_BAND = 0.025f
        const val CONFIRM_VARIANTS = 15

        /** The smallest baseline a gain is measured against, so a handful of kills is never "+200 %". */
        val FLOORS: Map<Output, Double> = mapOf(
            Output.DAMAGE to 200.0, Output.TAKEN to 10.0, Output.INTEGRITY to 10.0, Output.SURVIVAL to 10.0, Output.KILLS to 10.0,
            Output.DATA to 30.0,
            Output.MATTER to 30.0, Output.DATA_PER_KILL to 1.0, Output.MATTER_PER_KILL to 1.0, Output.OVERDRIVE to 3.0,
            Output.DASHES to 10.0, Output.OFFERS to 1.0,
        )

        /** Two sources of each element: the first on weapon hits, the second on impacts (or a Dash or crit where none exists). */
        val ElementSources: Map<StatusElement, List<String>> = mapOf(
            StatusElement.THERMAL to listOf("Incendiary Rounds", "Igniter"),
            StatusElement.CRYO to listOf("Rime Rounds", "Cold Snap"),
            StatusElement.ION to listOf("Arc Relay", "Static Brush"),
            StatusElement.GRAVITIC to listOf("Graviton Seed", "Lodestone"),
            StatusElement.ENTROPY to listOf("Rust Spores", "Corrosive Touch"),
            StatusElement.SONIC to listOf("Subwoofer Rounds", "Tuning Spike"),
            // Hollow Point needs crits to add its Void; Focus Lens supplies them.
            StatusElement.VOID to listOf("Null Rounds", "Hollow Point", "Focus Lens"),
        )

        /** Items whose effect no scripted encounter can show, by name, with the reason. None today. */
        val JUSTIFIED_EXCEPTIONS: Map<String, String> = emptyMap()
    }
}

/** The weapons each armed arena is flown with: an Ion Swarm volley and a Sentry Grid (a persistent source). */
private val ARENA_WEAPONS: List<WeaponId?> = listOf(WeaponId.ION_SWARM, WeaponId.SENTRY_GRID)
