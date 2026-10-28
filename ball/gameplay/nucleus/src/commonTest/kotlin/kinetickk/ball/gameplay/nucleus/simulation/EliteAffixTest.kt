// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Elite affixes and champions (variety.md §4): when and how often they roll, that the rolls draw from no stream, each
 * affix's reused behaviour, the champion's integrity and rewards, the projection bits and the Elite banner. The bounds
 * (three links, three fragments in the cap, the champion chance) are in RunVarietyBoundsTest.
 */
class EliteAffixTest {
    private val content = canonicalGameplayContent
    private val affixes = content.variety.affixes
    private val champions = content.variety.champions

    /** Every Elite past the affix threat time rolls an affix. */
    private val alwaysAffixed = content.copy(variety = content.variety.copy(affixes = affixes.copy(baseChance = 1f)))

    /** Neither affixes nor champions; the Keynote and the personality still run as in [content]. */
    private val withoutAffixesOrChampions = content.copy(
        variety = content.variety.copy(affixes = affixes.copy(enabled = false), champions = champions.copy(enabled = false)),
    )

    /** Ordinary kinds a tier-0 run may spawn as champions. */
    private val ordinaryKinds = listOf(
        EnemyType.DRIFTER, EnemyType.SHOOTER, EnemyType.CHARGER, EnemyType.INTERCEPTOR, EnemyType.WEAVER,
        EnemyType.WARDEN, EnemyType.SPLITTER,
    )

    /** A started run at [at] seconds with no enemies and no obstacles, so nothing but the tested rule moves anything. */
    private fun arena(
        at: Float,
        rebirth: Int = 0,
        seed: Int = 5_150,
        content: GameplayContentSnapshot = this.content,
    ): MutableGameState = buildTestState(seed = seed, rebirth = rebirth, content = content).apply {
        elapsed = at
        clearObstaclesForTesting()
    }

    /** A live Elite with [affix] in the run, its other facts as an Elite spawned by the director. */
    private fun MutableGameState.affixedElite(affix: EliteAffix?, x: Float = coreX + 400f, y: Float = coreY, hp: Float = 1_000f): Enemy =
        addEnemyForTesting(x, y, hp = hp, radius = 26f, type = EnemyType.ELITE).also { it.affix = affix }

    @Test
    fun noEliteRollsAnAffixBeforeTheAffixThreatTime() {
        val start = affixes.fromThreatSeconds
        // Tier 0 at the last instant before the threat time and at it, then tiers whose threat offset moves the start.
        for ((tier, elapsed) in listOf(0 to start.nextBelow(), 0 to start, 5 to 59.99f, 5 to 60f, 10 to 0f)) {
            val state = arena(elapsed, rebirth = tier, content = alwaysAffixed)
            val affixed = state.threatElapsed >= start
            repeat(6) {
                val elite = state.spawnedEnemy(EnemyType.ELITE, 0f, 0f)
                assertEquals(affixed, elite.affix != null, "Tier $tier at $elapsed s (threat ${state.threatElapsed} s)")
            }
            // Only Elites roll affixes: no Herald and no ordinary kind ever does.
            assertNull(state.spawnedEnemy(EnemyType.HERALD, 0f, 0f).affix)
            ordinaryKinds.forEach { assertNull(state.spawnedEnemy(it, 0f, 0f).affix, "$it") }
        }
        // Switched off, no Elite rolls one at any time.
        val off = arena(600f, rebirth = 10, content = alwaysAffixed.withoutDirectorVariety())
        repeat(6) { assertNull(off.spawnedEnemy(EnemyType.ELITE, 0f, 0f).affix) }
    }

    @Test
    fun theAffixChanceGrowsByTierAndEveryAffixIsDrawn() {
        for (tier in listOf(0, 4, 10)) {
            val state = arena(400f, rebirth = tier)
            val counts = IntArray(EliteAffix.entries.size)
            var affixed = 0
            repeat(ELITE_SAMPLE) {
                val affix = state.spawnedEnemy(EnemyType.ELITE, 0f, 0f).affix ?: return@repeat
                affixed++
                counts[affix.ordinal]++
            }
            val chance = min(1f, affixes.baseChance + affixes.chancePerTier * tier)
            if (chance >= 1f) assertEquals(ELITE_SAMPLE, affixed, "Tier $tier affixes every Elite")
            else assertEquals(chance, affixed / ELITE_SAMPLE.toFloat(), 0.035f, "Tier $tier affix share")
            counts.forEachIndexed { ordinal, count ->
                assertEquals(1f / counts.size, count / affixed.toFloat(), 0.035f, "Tier $tier ${EliteAffix.entries[ordinal]} share")
            }
        }
    }

    @Test
    fun affixAndChampionRollsDrawNothingFromAStream() {
        for (seed in 1..6) {
            val on = arena(400f, rebirth = seed % 3 * 5, seed = seed)
            val off = arena(400f, rebirth = seed % 3 * 5, seed = seed, content = withoutAffixesOrChampions)
            var rolled = 0
            repeat(120) { index ->
                val type = if (index % 4 == 0) EnemyType.ELITE else ordinaryKinds[index % ordinaryKinds.size]
                val withVariety = on.spawnedEnemy(type, 0f, 0f)
                val without = off.spawnedEnemy(type, 0f, 0f)
                assertEquals(without.id, withVariety.id)
                assertEquals(without.aspect, withVariety.aspect, "Seed $seed $type aspect")
                if (withVariety.affix != null || withVariety.champion) rolled++
            }
            assertTrue(rolled > 0, "Seed $seed rolls some affixes or champions")
            assertEquals(off.spawnRandom.snapshot(), on.spawnRandom.snapshot(), "Seed $seed spawn stream")
            assertEquals(off.gameplayRandom.snapshot(), on.gameplayRandom.snapshot(), "Seed $seed gameplay stream")
            assertEquals(off.rewardRandom.snapshot(), on.rewardRandom.snapshot(), "Seed $seed reward stream")
        }
    }

    @Test
    fun aShieldedEliteRunsTheHeraldShieldCycleWithoutSummons() {
        val state = arena(400f, content = alwaysAffixed)
        var elite: Enemy? = null
        repeat(200) { if (elite == null) elite = state.spawnedEnemy(EnemyType.ELITE, state.coreX + 400f, state.coreY).takeIf { it.affix == EliteAffix.SHIELDED } }
        val shielded = assertNotNull(elite, "Some Elite rolls Shielded")
        assertEquals(state.elapsed + affixes.firstShieldSeconds, shielded.abilityAt, "The first shield waits")
        state.enemies += shielded
        state.updateEnemies(MutableGameState.FIXED_STEP)
        assertTrue(shielded.shieldUntil <= state.elapsed, "No shield before its time")

        state.elapsed = shielded.abilityAt
        state.updateEnemies(MutableGameState.FIXED_STEP)
        val raisedAt = state.elapsed
        assertEquals(raisedAt + affixes.shieldSeconds, shielded.shieldUntil)
        assertEquals(raisedAt + affixes.shieldIntervalSeconds, shielded.abilityAt)
        assertTrue(state.toRenderModel().enemies.single { it.id == shielded.id }.shielded, "The shield is projected")
        assertEquals(0, shielded.escortsPending, "A Shielded Elite summons nobody")
        assertEquals(1, state.enemies.size)

        // Weapons stop on the shield; an impact breaks it and lands.
        val hp = shielded.hp
        state.dealHit(shielded, 50f, HitSource.WEAPON, canCrit = false)
        assertEquals(hp, shielded.hp, "A weapon hit stops on the shield")
        state.dealHit(shielded, 50f, HitSource.IMPACT, canCrit = false)
        assertTrue(shielded.hp < hp, "An impact lands")
        assertEquals(state.elapsed, shielded.shieldUntil, "and breaks the shield")
        state.dealHit(shielded, 50f, HitSource.WEAPON, canCrit = false)
        assertTrue(shielded.hp < hp - 1f, "Weapons land again once it is broken")

        // The next shield comes one interval after the last one rose, not sooner.
        state.elapsed = raisedAt + affixes.shieldIntervalSeconds - 0.1f
        state.updateEnemies(MutableGameState.FIXED_STEP)
        assertTrue(shielded.shieldUntil <= state.elapsed)
        state.elapsed = raisedAt + affixes.shieldIntervalSeconds
        state.updateEnemies(MutableGameState.FIXED_STEP)
        assertEquals(state.elapsed + affixes.shieldSeconds, shielded.shieldUntil)
    }

    @Test
    fun aSplittingEliteBreaksIntoSplitterFragmentsOnARingWithoutAGameplayDraw() {
        val splitting = arena(400f)
        val plain = arena(400f)
        val elite = splitting.affixedElite(EliteAffix.SPLITTING, hp = 1f)
        val twin = plain.affixedElite(null, hp = 1f)
        assertEquals(twin.id, elite.id)
        splitting.dealHit(elite, 10f, HitSource.WEAPON, canCrit = false)
        plain.dealHit(twin, 10f, HitSource.WEAPON, canCrit = false)
        splitting.sweepKills()
        plain.sweepKills()
        assertEquals(plain.gameplayRandom.snapshot(), splitting.gameplayRandom.snapshot(), "The ring draws nothing")
        assertTrue(plain.enemies.isEmpty())

        // A Splitter killed at the same time leaves fragments of the same integrity and size.
        val splitterRun = arena(400f)
        val splitter = splitterRun.addEnemyForTesting(splitterRun.coreX + 400f, splitterRun.coreY, hp = 1f, type = EnemyType.SPLITTER)
        splitterRun.dealHit(splitter, 10f, HitSource.WEAPON, canCrit = false)
        splitterRun.sweepKills()
        val reference = splitterRun.enemies.first()

        val fragments = splitting.enemies
        assertEquals(affixes.splitFragments, fragments.size)
        fragments.forEachIndexed { index, fragment ->
            assertEquals(EnemyType.DRIFTER, fragment.type)
            assertEquals(reference.maxHp, fragment.maxHp)
            assertEquals(reference.radius, fragment.radius)
            assertEquals(13f, kotlin.math.hypot(fragment.x - elite.x, fragment.y - elite.y), 0.01f)
            val expected = index * TAU / fragments.size + (elite.id % 8) * 0.785f
            assertEquals(0f, angleBetween(expected, atan2(fragment.y - elite.y, fragment.x - elite.x)), 0.001f, "Fragment $index angle")
            assertEquals(0f, angleBetween(expected, atan2(fragment.vy, fragment.vx)), 0.001f, "Fragment $index flies outward")
            assertEquals(elite.ownerTag, fragment.ownerTag)
            assertTrue(!fragment.champion && fragment.affix == null)
        }
    }

    @Test
    fun aMagneticEliteRunsTheWardenWellAtItsStrength() {
        fun pull(type: EnemyType, affix: EliteAffix?, distance: Float = 300f): Float {
            val state = arena(400f)
            state.setVelocityForTesting(0f, 0f)
            state.addEnemyForTesting(state.coreX + distance, state.coreY, type = type).affix = affix
            state.updateEnemies(MutableGameState.FIXED_STEP)
            return state.velocityX
        }
        for (distance in listOf(120f, 300f, 430f)) {
            val warden = pull(EnemyType.WARDEN, null, distance)
            assertTrue(warden > 0f, "The Warden pulls the Core in at $distance u")
            assertEquals(warden * affixes.wellStrength, pull(EnemyType.ELITE, EliteAffix.MAGNETIC, distance), warden * 0.001f)
            assertEquals(0f, pull(EnemyType.ELITE, null, distance), "A plain Elite pulls nothing")
        }
        assertEquals(0f, pull(EnemyType.ELITE, EliteAffix.MAGNETIC, 450f), "Outside the Warden's reach")
    }

    @Test
    fun aLinkedEliteTethersAlliesThatThenTakeTheAegisShareLess() {
        val state = arena(400f)
        val elite = state.affixedElite(EliteAffix.LINKED)
        val ally = state.addEnemyForTesting(elite.x + 60f, elite.y, hp = 1_000f)
        val loner = state.addEnemyForTesting(elite.x - 600f, elite.y, hp = 1_000f)
        state.updateEnemies(MutableGameState.FIXED_STEP)
        val model = state.toRenderModel()
        assertEquals(elite.id, model.enemies.single { it.id == ally.id }.linkedTo, "The link is projected from the Elite")
        assertEquals(-1, model.enemies.single { it.id == loner.id }.linkedTo)
        state.dealHit(ally, 100f, HitSource.WEAPON, canCrit = false)
        state.dealHit(loner, 100f, HitSource.WEAPON, canCrit = false)
        assertEquals((1_000f - loner.hp) * (1f - AEGIS_LINK_REDUCTION), 1_000f - ally.hp, 0.01f)

        // The link lapses once the Elite stops renewing it.
        state.enemies.remove(elite)
        state.elapsed += 0.5f
        assertEquals(-1, state.toRenderModel().enemies.single { it.id == ally.id }.linkedTo)
    }

    @Test
    fun aVolatileEliteArmsTheWiderBurstWithoutTheMutator() {
        fun killed(affix: EliteAffix?, rebirth: Int = 0, distance: Float = 100f): MutableGameState {
            val state = arena(400f, rebirth = rebirth)
            state.setVelocityForTesting(0f, 0f)
            val elite = state.affixedElite(affix, x = state.coreX + distance, hp = 1f)
            state.dealHit(elite, 10f, HitSource.WEAPON, canCrit = false)
            state.sweepKills()
            return state
        }
        val state = killed(EliteAffix.VOLATILE)
        val burst = state.volatileBursts.single()
        assertEquals(affixes.burstRadius, burst.radius)
        assertEquals(state.elapsed + affixes.burstDelaySeconds, burst.detonatesAt, 0.0001f)
        // A slow Core 100 u away is outside a Remains ring but inside this one.
        assertTrue(100f > VOLATILE_BURST_RADIUS + state.coreRadius && 100f < affixes.burstRadius + state.coreRadius)
        val hp = state.hp
        state.elapsed += affixes.burstDelaySeconds * 0.5f
        state.updateVolatileRemains()
        assertEquals(hp, state.hp, "Not before its delay")
        state.elapsed += affixes.burstDelaySeconds
        state.updateVolatileRemains()
        assertTrue(state.hp < hp, "A slow Core inside the ring takes the burst")

        // The same escape law: a fast Core slips out.
        val fast = killed(EliteAffix.VOLATILE)
        fast.setVelocityForTesting(VOLATILE_ESCAPE_SPEED, 0f)
        val fastHp = fast.hp
        fast.elapsed += affixes.burstDelaySeconds + 0.01f
        fast.updateVolatileRemains()
        assertEquals(fastHp, fast.hp)

        assertTrue(killed(null).volatileBursts.isEmpty(), "A plain Elite leaves no burst")
        assertTrue(killed(EliteAffix.HASTED).volatileBursts.isEmpty())
        // Under Volatile Remains an Elite still leaves no remains of its own: one burst, the affix's.
        assertEquals(affixes.burstRadius, killed(EliteAffix.VOLATILE, rebirth = 4).volatileBursts.single().radius)
    }

    @Test
    fun aHastedEliteMovesAndAttacksFaster() {
        fun stepped(affix: EliteAffix?): Enemy {
            val state = arena(400f)
            val elite = state.affixedElite(affix)
            elite.actionTimer = 1f
            state.updateEnemies(MutableGameState.FIXED_STEP)
            return elite
        }
        val plain = stepped(null)
        val hasted = stepped(EliteAffix.HASTED)
        assertEquals(affixes.hasteCadence, (1f - hasted.actionTimer) / (1f - plain.actionTimer), 0.001f, "Cadence")
        assertEquals(affixes.hasteSpeed, hasted.vx / plain.vx, 0.001f, "Speed")
        assertEquals(affixes.hasteSpeed, hasted.vy / plain.vy, 0.001f, "Speed")

        // The steering target itself is faster, at any agility.
        val state = arena(400f)
        val target = state.affixedElite(EliteAffix.HASTED)
        val reference = state.affixedElite(null)
        state.steerEnemy(target, 100f, 0f, 1_000f, 1f)
        state.steerEnemy(reference, 100f, 0f, 1_000f, 1f)
        assertEquals(reference.vx * affixes.hasteSpeed, target.vx, 0.001f)
        for (affix in EliteAffix.entries - EliteAffix.HASTED) {
            val other = state.affixedElite(affix)
            assertEquals(1f, state.affixSpeedFactor(other), "$affix speed")
            assertEquals(1f, state.affixCadence(other), "$affix cadence")
        }
    }

    @Test
    fun theAffixAndTheChampionFlagReachTheProjectionAndLeaveTheOtherBitsAlone() {
        val state = arena(400f)
        for (type in listOf(EnemyType.ELITE, EnemyType.WARDEN, EnemyType.INTERCEPTOR)) {
            val enemy = state.addEnemyForTesting(400f, 0f, type = type)
            enemy.aspect = 2
            val plain = state.affinityBits(enemy)
            assertEquals(EnemyAffinity.NONE, EnemyAffinity.affix(plain))
            for (affix in listOf<EliteAffix?>(null) + EliteAffix.entries) for (champion in listOf(false, true)) {
                enemy.affix = affix
                enemy.champion = champion
                val bits = state.affinityBits(enemy)
                assertEquals(affix?.ordinal ?: EnemyAffinity.NONE, EnemyAffinity.affix(bits), "$type $affix")
                assertEquals(champion, EnemyAffinity.champion(bits), "$type champion")
                assertEquals(plain, bits and 0xFFFF, "$type keeps bits 0-15")
                assertEquals(bits, state.toRenderModel().enemies.single { it.id == enemy.id }.affinityBits)
            }
            state.enemies.remove(enemy)
        }
    }

    @Test
    fun championsAreRareOrdinaryAmbientSpawnsWithMoreIntegrity() {
        val start = champions.fromThreatSeconds
        val state = arena(start + 10f)
        var count = 0
        repeat(CHAMPION_SAMPLE) { index ->
            val type = ordinaryKinds[index % ordinaryKinds.size]
            val enemy = state.spawnedEnemy(type, 0f, 0f)
            val integrity = state.spawnIntegrity(type)
            if (enemy.champion) {
                count++
                assertEquals(integrity * champions.integrityScale, enemy.maxHp, integrity * 1e-5f, "$type champion integrity")
            } else {
                assertEquals(integrity, enemy.maxHp, "$type plain integrity")
            }
            assertEquals(enemy.maxHp, enemy.hp)
        }
        assertEquals(champions.baseChance, count / CHAMPION_SAMPLE.toFloat(), 0.006f, "Champion share at tier 0")

        // Never a Swarmling, an Elite, a mini-boss or an encounter's member, and never before the champion threat time.
        repeat(2_000) { assertTrue(!state.spawnedEnemy(EnemyType.SWARMLING, 0f, 0f).champion, "Swarmling") }
        repeat(400) {
            assertTrue(!state.spawnedEnemy(EnemyType.ELITE, 0f, 0f).champion, "Elite")
            assertTrue(!state.spawnedEnemy(EnemyType.HERALD, 0f, 0f).champion, "Herald")
        }
        repeat(1_000) { index ->
            val type = ordinaryKinds[index % ordinaryKinds.size]
            assertTrue(!state.spawnedEnemy(type, 0f, 0f, ownerTag = 0x0100_0000_0000_0001L).champion, "Encounter $type")
        }
        val early = arena(start.nextBelow())
        repeat(2_000) { index -> assertTrue(!early.spawnedEnemy(ordinaryKinds[index % ordinaryKinds.size], 0f, 0f).champion, "Before $start s") }
        val off = arena(start + 10f, content = withoutAffixesOrChampions)
        repeat(2_000) { index -> assertTrue(!off.spawnedEnemy(ordinaryKinds[index % ordinaryKinds.size], 0f, 0f).champion, "Switched off") }
    }

    @Test
    fun aChampionPaysItsBonusDataAndMatterWithoutADraw() {
        for (type in listOf(EnemyType.DRIFTER, EnemyType.WARDEN)) {
            val champion = arena(400f)
            val plain = arena(400f)
            val enemy = champion.addEnemyForTesting(champion.coreX + 300f, champion.coreY, hp = 1f, type = type).also { it.champion = true }
            val twin = plain.addEnemyForTesting(plain.coreX + 300f, plain.coreY, hp = 1f, type = type)
            champion.dealHit(enemy, 10f, HitSource.WEAPON, canCrit = false)
            plain.dealHit(twin, 10f, HitSource.WEAPON, canCrit = false)
            champion.sweepKills()
            plain.sweepKills()
            assertEquals(plain.gameplayRandom.snapshot(), champion.gameplayRandom.snapshot(), "$type pays without a draw")
            assertEquals(plain.pickups, champion.pickups.dropLast(champions.bonusData), "$type keeps its own drops")
            val bonus = champion.pickups.takeLast(champions.bonusData)
            assertEquals(14f, bonus[0].x - enemy.x, 0.001f)
            assertEquals(-14f, bonus[1].x - enemy.x, 0.001f)
            bonus.forEach { pickup ->
                assertEquals(PickupType.DATA, pickup.type)
                assertEquals(0f, pickup.y - enemy.y, 0.0001f)
                assertEquals(40f, abs(pickup.vx), 0.0001f)
                assertTrue(pickup.vx * (pickup.x - enemy.x) > 0f, "$type bonus Data flies outward")
            }
            val plainMatter = plain.runMatter + plain.matterFraction
            assertTrue(plainMatter > 0f)
            assertEquals(plainMatter * champions.matterScale, champion.runMatter + champion.matterFraction, plainMatter * 1e-4f, "$type Matter")
        }
    }

    @Test
    fun theEliteBannerNamesTheAffix() {
        fun banner(content: GameplayContentSnapshot): Pair<String, Enemy> {
            val state = arena(0f, rebirth = 10, content = content)
            state.nextEliteAt = state.elapsed
            state.spawnWave(MutableGameState.FIXED_STEP)
            return state.message to state.enemies.last()
        }
        val (affixed, elite) = banner(alwaysAffixed)
        val affix = assertNotNull(elite.affix)
        assertEquals(EnemyType.ELITE, elite.type)
        assertEquals(affix.signalNotice, affixed)
        assertTrue(affixed.startsWith("$ELITE_SIGNAL // "))
        val (plain, plainElite) = banner(alwaysAffixed.withoutDirectorVariety())
        assertNull(plainElite.affix)
        assertEquals(ELITE_SIGNAL, plain)
    }

    private fun angleBetween(a: Float, b: Float): Float {
        var difference = (b - a) % TAU
        if (difference > TAU / 2f) difference -= TAU
        if (difference < -TAU / 2f) difference += TAU
        return difference
    }

    private companion object {
        const val ELITE_SAMPLE = 2_000
        const val CHAMPION_SAMPLE = 10_000
    }
}
