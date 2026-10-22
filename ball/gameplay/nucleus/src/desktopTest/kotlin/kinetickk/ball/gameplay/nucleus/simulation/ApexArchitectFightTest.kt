// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.ApexCell
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex").
 *
 * Fights: per character, tier (0 fresh; 10 with the strong build's Matrix, Arc Coil and Relics) and seed, the
 * no-POI bot plays the run to the Architect's arrival (the Core cannot be hurt on the way; the first three seeds of
 * a fixed list whose run reaches the Architect are used, since a singularity contact can still end one); the run
 * then forks into two arms that play the next 180 s with copies of the same bot: one
 * with the level forced to the cap (the bot casts the Apex by its rule), one without. In the fight the Core's
 * integrity is refilled every frame and its losses (shield and integrity) are counted.
 *
 * The contract holds in every Apex arm: each strike stays within the boss's hit cap, an activation spends at most
 * its strikes and its bolt budget, the boss's integrity never moves while it shifts, every shift starts at a phase
 * gate and lasts exactly 8 s, its escorts arrive, and the Architect fires at least 90 % of the volleys its own
 * timer schedules. In a scripted arena where the Architect is the only enemy, the character's own damage to it
 * during an activation (strikes included) is never below the same script's without the Apex.
 *
 * The benefit (tier 0, median over the seeds): the offensive characters' median time-to-kill with the Apex lies
 * in [0.75, 0.95] of the time without, each within ±40 % of the median benefit; the Prism and the Diamond lose
 * [0.55, 0.85] of what they lose in the same windows without it.
 */
class ApexArchitectFightTest {
    @Test
    fun everyApexKeepsTheArchitectContractAtTierZeroAndTen() {
        val broken = mutableListOf<String>()
        for (shape in CoreShape.entries) for (tier in listOf(0, 10)) {
            if (fightsOf(shape, tier).size < FIGHTS_PER_CASE) broken += "$shape tier $tier reached too few Architects"
        }
        for ((key, pair) in fights) {
            val (shape, tier, seed) = key
            val label = "$shape tier $tier seed $seed"
            val (apex, plain) = pair
            println("APEX-FIGHT $shape tier=$tier seed=$seed ${apex.line()} | without ${plain.line()}")
            if (apex.casts == 0) broken += "$label never cast its Apex"
            if (apex.strikeExcess > 1e-3f) broken += "$label strike over the cap by ${apex.strikeExcess}"
            if (apex.maxStrikes > apex.strikeBudget) broken += "$label spent ${apex.maxStrikes} strikes"
            if (apex.maxErasures > apex.boltBudget || apex.minBoltsLeft < 0f) broken += "$label erased ${apex.maxErasures}"
            if (apex.shiftingLoss > 0f) broken += "$label boss lost ${apex.shiftingLoss} while shifting"
            // A shift starts inside a frame (up to three fixed steps) at a phase gate and ends 8 s later.
            val earliest = ARCHITECT_SHIFT_SECONDS - FRAME - MutableGameState.FIXED_STEP
            if (apex.shiftLengths.any { it !in earliest..ARCHITECT_SHIFT_SECONDS + 1e-3f }) {
                broken += "$label shifts end ${apex.shiftLengths} s after their frame"
            }
            if (apex.shiftEscorts.any { it < 2 }) broken += "$label escorts ${apex.shiftEscorts}"
            if (apex.volleyShare < 0.9f) broken += "$label fired ${apex.volleyShare} of its scheduled volleys"
        }
        assertTrue(broken.isEmpty(), "Apex contract: $broken")
    }

    @Test
    fun anActivationNeverLowersTheCharactersDamageToTheArchitect() {
        val lowered = mutableListOf<String>()
        for (tier in listOf(0, 10)) for (shape in CoreShape.entries) {
            val apex = arena(shape, tier, apex = true)
            val plain = arena(shape, tier, apex = false)
            println("APEX-ARENA $shape tier=$tier mechanic=${"%.1f".format(apex.mechanic)} (strikes ${"%.1f".format(apex.strikes)}, " +
                "base ${"%.1f".format(apex.mechanic - apex.strikes)}) without=${"%.1f".format(plain.mechanic)}")
            if (apex.mechanic < plain.mechanic) lowered += "$shape tier $tier ${apex.mechanic} below ${plain.mechanic}"
        }
        assertTrue(lowered.isEmpty(), "an Apex lowered the damage its character deals the Architect: $lowered")
    }

    @Test
    fun theApexShortensOffensiveFightsAndShieldsTheDefensiveCharactersWithinItsBand() {
        val outside = mutableListOf<String>()
        fun armsOf(shape: CoreShape) = fightsOf(shape, 0)
        val ratios = OFFENSIVE.associateWith { shape ->
            val arms = armsOf(shape)
            median(arms.map { it.first.killSeconds }) / median(arms.map { it.second.killSeconds })
        }
        val median = median(ratios.values.toList())
        val benefit = 1f - median
        println("APEX-BENEFIT tier=0 median=$median " + ratios.entries.joinToString { (shape, ratio) -> "$shape=${"%.3f".format(ratio)}" })
        if (median !in 0.75f..0.95f) outside += "median time-to-kill ratio $median"
        ratios.forEach { (shape, ratio) ->
            val own = 1f - ratio
            if (own !in min(benefit * 0.6f, benefit * 1.4f)..max(benefit * 0.6f, benefit * 1.4f)) {
                outside += "$shape benefit $own (median $benefit)"
            }
        }
        for (shape in DEFENSIVE) {
            val arms = armsOf(shape)
            val lost = arms.sumOf { (apex, _) -> apex.windowLoss(apex.windows).toDouble() }.toFloat()
            val without = arms.sumOf { (apex, plain) -> plain.windowLoss(apex.windows).toDouble() }.toFloat()
            println("APEX-DEFENSE $shape lost=$lost without=$without ratio=${lost / without}")
            if (!(without > 0f) || lost / without !in 0.55f..0.85f) outside += "$shape lost $lost against $without"
        }
        assertTrue(outside.isEmpty(), "Apex benefit: $outside")
    }

    /** What one arm of a fight measured. Times are seconds since the Architect arrived. */
    private class Fight(
        val killSeconds: Float,
        val casts: Int,
        val strikeBudget: Int,
        val boltBudget: Int,
        val maxStrikes: Int,
        val maxErasures: Int,
        val minBoltsLeft: Float,
        val strikeExcess: Float,
        val shiftingLoss: Float,
        val shiftLengths: List<Float>,
        val shiftEscorts: List<Int>,
        /** Volleys fired as a share of the volleys the Architect's attack timer scheduled while it was open. */
        val volleyShare: Float,
        /** Apex windows (start, end) of this arm. */
        val windows: List<Pair<Float, Float>>,
        /** Per frame: time and the Core's loss (shield and integrity). */
        val losses: List<Pair<Float, Float>>,
        val apexBossDamage: Double,
    ) {
        fun windowLoss(windows: List<Pair<Float, Float>>): Float =
            losses.filter { (time, _) -> windows.any { (start, end) -> time > start && time <= end } }.sumOf { it.second.toDouble() }.toFloat()

        fun line(): String = "kill=${"%.1f".format(killSeconds)}s casts=$casts strikes≤$maxStrikes/$strikeBudget " +
            "erased≤$maxErasures/$boltBudget volleys=${"%.3f".format(volleyShare)} shifts=${shiftLengths.size} " +
            "escorts=$shiftEscorts apexBoss=${"%.0f".format(apexBossDamage)}"
    }

    private class ArenaOutcome(val mechanic: Float, val strikes: Float)

    private companion object {
        /** The seed list (the Rebirth gates' sequence, i · 7,919 + 13 from i = 6); each case uses its first three fights. */
        val SEED_POOL = (6..20).map { it * 7_919 + 13 }
        const val FIGHTS_PER_CASE = 3
        const val FIGHT_SECONDS = 180f
        const val FRAME = 0.02f
        const val ARENA_ARCHITECT_INTEGRITY = 13_500f
        /** Frames after a shift's start in which its escorts are counted (they arrive in the step that resolves it). */
        const val ESCORT_FRAMES = 5
        val DEFENSIVE = listOf(CoreShape.PRISM, CoreShape.DIAMOND)
        val OFFENSIVE = CoreShape.entries - DEFENSIVE.toSet()
        val BotWeapons: List<WeaponId> = listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.ENTROPY_FIELD, WeaponId.PRISM_RELAY)
        val StrongRelics = listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN)

        /** Both arms of every fight (character, tier, seed), played once per test JVM. */
        val fights: Map<Triple<CoreShape, Int, Int>, Pair<Fight, Fight>> by lazy {
            val played = linkedMapOf<Triple<CoreShape, Int, Int>, Pair<Fight, Fight>>()
            for (shape in CoreShape.entries) for (tier in listOf(0, 10)) {
                var found = 0
                for (seed in SEED_POOL) {
                    if (found == FIGHTS_PER_CASE) break
                    val (state, bot) = approach(shape, tier, seed) ?: continue
                    val apex = fight(state.copyForReduction(), bot.copy(), apex = true, windows = emptyList())
                    played[Triple(shape, tier, seed)] = apex to fight(state.copyForReduction(), bot.copy(), apex = false, windows = apex.windows)
                    found++
                }
            }
            played
        }

        fun fightsOf(shape: CoreShape, tier: Int): List<Pair<Fight, Fight>> =
            fights.filterKeys { it.first == shape && it.second == tier }.values.toList()

        fun median(values: List<Float>): Float {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2f else sorted[middle]
        }

        /** The run up to the Architect's arrival, its integrity refilled; null when it never arrives. */
        fun approach(shape: CoreShape, tier: Int, seed: Int): Pair<MutableGameState, NoPoiBot>? {
            val content = canonicalGameplayContent
            val strong = tier > 0
            val state = MutableGameState(
                content,
                seed = seed,
                bootstrapProgress = GameplayProfileSnapshot(
                    preferences = PlayerPreferences(simulationSpeed = 1f),
                    economy = PlayerEconomy(),
                    loadout = PlayerLoadout(shape, if (strong) WeaponId.ARC_COIL else WeaponId.FLUX_WAKE, ReferenceArmory.toSet()),
                    matrix = MatrixProgress(content.matrixNodes.map { if (strong) it.maxRanks else 0 }),
                    collection = PlayerCollection(),
                    rebirthProgress = RebirthProgress(level = tier, highestCleared = tier - 1),
                ),
            )
            state.unlockWeaponsForTesting(ReferenceArmory)
            state.startRun()
            if (strong) StrongRelics.forEach(state::acquireRelicForTesting)
            val bot = NoPoiBot(archetypeTag = BuildTag.RAM, weaponOrder = BotWeapons)
            while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                state.invulnerableUntil = Float.MAX_VALUE
                if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                    bot.control(state)
                    state.update(FRAME)
                }
                state.hp = state.maxHp
                drain(state)
                if (state.liveArchitect() != null) {
                    state.invulnerableUntil = 0f
                    return state to bot
                }
            }
            println("APEX-FIGHT $shape tier=$tier seed=$seed reached no Architect: ${state.phase} ${state.message} at ${state.elapsed}")
            return null
        }

        /**
         * 180 s of the fight from the fork. With [apex] the level is forced to the cap, so the Apex unlocks ready and
         * the bot casts it by its rule. [windows] are the activations of the Apex arm (kept for the other arm).
         */
        fun fight(state: MutableGameState, bot: NoPoiBot, apex: Boolean, windows: List<Pair<Float, Float>>): Fight {
            val start = state.elapsed
            if (apex) {
                state.level = state.content.tempo.maximumLevel
                state.unlockApex()
            }
            val definition = state.apexDefinition
            val ownWindows = mutableListOf<Pair<Float, Float>>()
            val losses = mutableListOf<Pair<Float, Float>>()
            var killSeconds = FIGHT_SECONDS
            var casts = 0
            var maxStrikes = 0
            var maxErasures = 0
            var minBolts = Float.MAX_VALUE
            var strikeExcess = 0f
            var shiftingLoss = 0f
            val shiftLengths = mutableListOf<Float>()
            val shiftEscorts = mutableListOf<Int>()
            var volleys = 0
            var scheduled = 0
            // Escorts of the latest shift: enemies newer than the shift's frame, seen within the next few frames.
            var escortMark = Int.MAX_VALUE
            var escortFrames = 0
            val escortIds = HashSet<Int>()
            while (state.elapsed < start + FIGHT_SECONDS && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                if (state.phase == GamePhase.CHOICE) {
                    bot.choose(state)
                    continue
                }
                val boss = state.liveArchitect() ?: break
                val runtime = state.skillRuntime
                val castsBefore = runtime.apex(ApexCell.CAST_COUNT).toInt()
                val strikesBefore = runtime.apex(ApexCell.STRIKES_LEFT)
                val bossDamageBefore = runtime.apexBossDamage
                val hpBefore = boss.hp
                val coreBefore = state.hp + state.shield
                val shiftingBefore = state.isShifting(boss)
                val phaseBefore = boss.phase
                val shiftBefore = boss.shiftUntil
                val timerBefore = boss.actionTimer
                val newestBefore = state.enemies.maxOfOrNull { it.id } ?: -1
                // Every strike is held to its target's hit cap; a Herald's never exceeds the Architect's share.
                val cap = max(state.architectHitCap(boss), heraldCap(state))
                bot.control(state)
                state.update(FRAME)
                val now = state.elapsed - start
                val castsNow = runtime.apex(ApexCell.CAST_COUNT).toInt()
                if (castsNow > castsBefore) {
                    casts += castsNow - castsBefore
                    ownWindows += now to now + definition.durationSeconds
                }
                val strikesLeft = runtime.apex(ApexCell.STRIKES_LEFT)
                val strikesSpent = if (castsNow > castsBefore) definition.bossStrikes - strikesLeft else strikesBefore - strikesLeft
                maxStrikes = max(maxStrikes, (definition.bossStrikes - strikesLeft).toInt())
                maxErasures = max(maxErasures, (definition.boltBudget - runtime.apex(ApexCell.BOLTS_LEFT)).toInt())
                if (casts > 0) minBolts = min(minBolts, runtime.apex(ApexCell.BOLTS_LEFT))
                val struck = (runtime.apexBossDamage - bossDamageBefore).toFloat()
                if (strikesSpent > 0f) strikeExcess = max(strikeExcess, struck / strikesSpent - cap)
                val removed = if (boss.alive) hpBefore - boss.hp else hpBefore
                if (shiftingBefore && state.isShifting(boss)) shiftingLoss = max(shiftingLoss, removed)
                if (boss.shiftUntil != shiftBefore) {
                    // Only a phase gate starts a shift; it ends 8 s after the moment inside this frame it started.
                    shiftLengths += if (boss.phase > phaseBefore) boss.shiftUntil - state.elapsed else -1f
                    if (escortFrames > 0) shiftEscorts += escortIds.size
                    escortMark = newestBefore
                    escortFrames = ESCORT_FRAMES
                    escortIds.clear()
                }
                if (escortFrames > 0) {
                    state.enemies.forEach { if (it.id > escortMark) escortIds += it.id }
                    if (--escortFrames == 0) shiftEscorts += escortIds.size
                }
                // A volley is due when the attack timer resets (the Apex never writes it); it fires unless the boss
                // cannot attack or the projectile budget is full (Apex shots count toward it).
                if (!shiftingBefore && !state.isShifting(boss) && boss.actionTimer > timerBefore) {
                    scheduled++
                    if (state.canAttack(boss) && state.projectiles.size < ARCHITECT_VOLLEY_PROJECTILE_BUDGET) volleys++
                }
                losses += now to max(0f, coreBefore - state.hp - state.shield)
                state.hp = state.maxHp
                drain(state)
                if (!boss.alive || state.phase == GamePhase.VICTORY) {
                    killSeconds = now
                    break
                }
            }
            if (escortFrames > 0) shiftEscorts += escortIds.size
            return Fight(
                killSeconds = killSeconds,
                casts = casts,
                strikeBudget = definition.bossStrikes,
                boltBudget = definition.boltBudget,
                maxStrikes = maxStrikes,
                maxErasures = maxErasures,
                minBoltsLeft = if (minBolts == Float.MAX_VALUE) 0f else minBolts,
                strikeExcess = strikeExcess,
                shiftingLoss = shiftingLoss,
                shiftLengths = shiftLengths,
                shiftEscorts = shiftEscorts,
                volleyShare = if (scheduled > 0) volleys / scheduled.toFloat() else 1f,
                windows = if (apex) ownWindows else windows,
                losses = losses,
                apexBossDamage = state.skillRuntime.apexBossDamage,
            )
        }

        /** The largest hit cap of a live Herald (0 for none). */
        fun heraldCap(state: MutableGameState): Float =
            state.enemies.filter { it.type == EnemyType.HERALD && it.alive }.maxOfOrNull { state.architectHitCap(it) } ?: 0f

        /**
         * One activation's length of a scripted arena at the fight's moment (720 s): the Architect alone, at its last
         * phase so no gate or shift interrupts it, its integrity refilled; the pointer traces a figure eight, a Dash
         * fires every 1.5 s and a short brake follows each. With [apex] the Apex casts on the first frame. Returns
         * the character's damage to the Architect over the activation (strikes included) and the strikes' share.
         */
        fun arena(shape: CoreShape, tier: Int, apex: Boolean): ArenaOutcome {
            val content = canonicalGameplayContent.copy(tempo = canonicalGameplayContent.tempo.copy(bossAtSeconds = 1_000_000f))
            val state = MutableGameState(content, seed = 4_242, initialRebirthLevel = tier)
            state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
            state.resize(1_280f, 720f)
            state.coreShape = shape
            state.startRun()
            state.enemies.clear()
            state.nextEliteAt = Float.MAX_VALUE
            state.nextLevelData = Int.MAX_VALUE
            state.elapsed = 720f
            state.bossSpawned = true
            // The integrity the Architect arrives with at 720 s of a run (its tier-0 hit cap is then ≈ 203).
            val boss = state.addEnemyForTesting(state.coreX + 340f, state.coreY,
                hp = ARENA_ARCHITECT_INTEGRITY * state.rebirthProfile.enemyHealthMultiplier, radius = 74f, type = EnemyType.ARCHITECT)
            boss.phase = state.architectFinalPhase()
            if (apex) {
                state.level = content.tempo.maximumLevel
                state.unlockApex()
                state.requestApex()
            }
            val duration = state.apexDefinition.durationSeconds
            val startAbility = state.characterRuntime.abilityDamage
            var frame = 0
            val begin = state.elapsed
            while (state.elapsed < begin + duration && state.phase == GamePhase.RUNNING) {
                state.spawnBudget = 0f
                state.runGrace = 1f
                state.pointsOfInterest = emptyList()
                state.enemies.removeAll { !it.isBoss }
                val angle = frame / 60f * 1.4f
                state.updatePointer(state.screenWidth * 0.5f + cos(angle) * 300f, state.screenHeight * 0.5f + sin(angle * 2f) * 220f)
                if (frame % 90 == 45) state.requestDash()
                state.setBrake(frame % 90 in 60..70)
                state.update(1f / 60f)
                boss.hp = boss.maxHp
                state.hp = state.maxHp
                drain(state)
                frame++
            }
            val mechanic = (state.characterRuntime.abilityDamage - startAbility).toFloat()
            return ArenaOutcome(mechanic, state.skillRuntime.apexBossDamage.toFloat())
        }

        fun drain(state: MutableGameState) {
            state.takeSoundCues()
            state.takeVisualFxCues()
            state.takeProgressUpdate()
        }
    }
}
