// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.fixtureWeapon
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureRelics
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HudPresentationMemoryTest {
    private val base = hudTestModel()

    @Test
    fun hitGhostCoversTheLostIntegrityAndDrainsWithinHalfASecond() {
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        val full = base.with("hp" to 100f, "maxHp" to 100f)
        memory.observe(full, 1f, boss.select(full))
        assertEquals(0f, memory.integrityGhost(1f))
        val hit = full.with("hp" to 80f)
        memory.observe(hit, 1.1f, boss.select(hit))
        assertEquals(0.2f, memory.integrityGhost(1.1f), 0.0001f)
        assertEquals(0.1f, memory.integrityGhost(1.35f), 0.0001f)
        assertEquals(0f, memory.integrityGhost(1.6f))
        // Healing never creates a ghost; a fresh memory starts without one.
        memory.observe(full, 2f, boss.select(full))
        assertEquals(0f, memory.integrityGhost(2f))
        assertEquals(0f, HudPresentationMemory().integrityGhost(0f))
    }

    @Test
    fun levelBadgeSlamsOnlyWhenTheLevelRises() {
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        val first = base.with("level" to 4)
        memory.observe(first, 0f, boss.select(first))
        assertEquals(1f, memory.levelSlam(0f))
        val next = base.with("level" to 5)
        memory.observe(next, 3f, boss.select(next))
        assertEquals(0f, memory.levelSlam(3f))
        assertTrue(memory.levelSlam(3.11f) in 0.4f..0.6f)
        assertEquals(1f, memory.levelSlam(3.5f))
        val lower = base.with("level" to 1)
        memory.observe(lower, 5f, boss.select(lower))
        assertEquals(1f, memory.levelSlam(5f))
    }

    @Test
    fun polarityPulsesOnlyWhileItDrains() {
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        val steady = base.with("polarityStability" to 0.5f, "elapsed" to 4f)
        memory.observe(steady, 0f, boss.select(steady))
        val held = steady.with("elapsed" to 4.1f)
        memory.observe(held, 0.1f, boss.select(held))
        assertFalse(memory.polarityDraining(0.1f))
        val drained = steady.with("polarityStability" to 0.48f, "elapsed" to 4.2f)
        memory.observe(drained, 0.2f, boss.select(drained))
        assertTrue(memory.polarityDraining(0.2f))
        // A repeated draw of the same frame keeps the pulse briefly, then it settles.
        memory.observe(drained, 0.3f, boss.select(drained))
        assertTrue(memory.polarityDraining(0.3f))
        assertFalse(memory.polarityDraining(1f))
        // A new run restarts the clock: its full polarity re-arms the memory without a pulse.
        val restarted = base.with("polarityStability" to 1f, "elapsed" to 0f)
        memory.observe(restarted, 1.1f, boss.select(restarted))
        assertFalse(memory.polarityDraining(1.1f))
    }

    /**
     * Polarity changes only on 120 Hz steps, so a frame holds 0..n steps of drain depending on the
     * frame rate. The cue follows the drain per simulated second: one edge strain shows it (or not)
     * identically at 30, 60, 144 and 240 frames per second.
     */
    @Test
    fun polarityCueFollowsTheDrainRateAtAnyFrameRate() {
        val drainPerSecond = base.content.tempo.fatigue.edgeDrainPerSecond
        listOf(30, 60, 144, 240).forEach { fps ->
            // Strain 0.1 drains 0.04 per second: the cue shows from the first drained step on (the
            // first frame only arms the memory), between steps too.
            assertEquals(listOf(false) + List(fps - 1) { true }, drainFrames(fps, drainPerSecond * 0.1f), "strain 0.1 at $fps fps")
            // Strain 0.05 drains 0.02 per second: below the cue, so it never shows.
            assertEquals(List(fps) { false }, drainFrames(fps, drainPerSecond * 0.05f), "strain 0.05 at $fps fps")
        }
    }

    /** One second of a 120 Hz run draining at [rate] per second, drawn at [fps]: the cue per frame. */
    private fun drainFrames(fps: Int, rate: Float): List<Boolean> {
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        val step = GameplayRenderModel.FIXED_STEP
        return List(fps) { frame ->
            val renderTime = (frame + 1) / fps.toFloat()
            // Whole steps the simulation has run by this frame (the remainder is interpolation).
            val elapsed = floor(renderTime / step + 1e-3f) * step
            val model = base.with("elapsed" to elapsed, "polarityStability" to 0.9f - rate * elapsed)
            memory.observe(model, renderTime, boss.select(model))
            memory.polarityDraining(renderTime)
        }
    }

    @Test
    fun bossTargetPrefersTheArchitectAndIgnoresDeadEnemies() {
        val boss = BossTarget()
        assertEquals(-1, boss.select(base).id)
        val elite = enemy(7, EnemyType.ELITE, 0.5f)
        val architect = enemy(9, EnemyType.ARCHITECT, 0.25f)
        val dead = enemy(11, EnemyType.ELITE, 0.9f).let {
            EnemyProjection(it.id, it.type, 0f, 0f, 0f, 0f, 0f, 100f, 20f, 0f, 0f, 0f, 0f, 0f, 0f, true)
        }
        assertEquals(7, boss.select(base.with("enemies" to listOf(dead, elite).toImmutableList())).id)
        val selected = boss.select(base.with("enemies" to listOf(elite, architect).toImmutableList()))
        assertEquals(EnemyType.ARCHITECT, selected.type)
        assertEquals(0.25f, selected.integrity, 0.0001f)
    }

    @Test
    fun bossTargetTracksAHeraldOverElitesAndUnderTheArchitect() {
        val boss = BossTarget()
        val elite = enemy(7, EnemyType.ELITE, 0.5f)
        val herald = enemy(8, EnemyType.HERALD, 0.75f)
        val architect = enemy(9, EnemyType.ARCHITECT, 0.25f)
        val drifter = enemy(6, EnemyType.DRIFTER, 1f)
        val tracked = boss.select(base.with("enemies" to listOf(drifter, elite, herald).toImmutableList()))
        assertEquals(8, tracked.id)
        assertEquals(EnemyType.HERALD, tracked.type)
        assertEquals(0.75f, tracked.integrity, 0.0001f)
        assertEquals(9, boss.select(base.with("enemies" to listOf(herald, architect, elite).toImmutableList())).id)
    }

    @Test
    fun bossTargetCarriesTheTrackedBossesAffinity() {
        val boss = BossTarget()
        val shifting = bossAffinity(EnemyType.ARCHITECT)
        val architect = enemy(9, EnemyType.ARCHITECT, 0.5f).copy(affinityBits = shifting)
        val elite = enemy(7, EnemyType.ELITE, 0.5f).copy(affinityBits = bossAffinity(EnemyType.ELITE))
        assertEquals(shifting, boss.select(base.with("enemies" to listOf(elite, architect).toImmutableList())).affinityBits)
        // Selecting again without a boss forgets it.
        assertEquals(0, boss.select(base).affinityBits)
    }

    @Test
    fun stateMessagesAndTrialRulesStayOutOfTheFeed() {
        assertFalse(base.with("message" to "OVERHEAT", "messageTime" to 1f).showsMessage())
        assertFalse(base.with("message" to "POLARITY FIELD STRAIN", "messageTime" to 1f).showsMessage())
        assertFalse(base.with("message" to "KINETIC OVERDRIVE", "messageTime" to 1f).showsMessage())
        val rules = base.content.pointsOfInterest.definition(PointOfInterestKind.SEALED_ANOMALY).instruction
        assertFalse(base.with("message" to rules, "messageTime" to 4f).showsMessage())
        assertTrue(base.with("message" to "ELITE SIGNAL", "messageTime" to 1f).showsMessage())
        assertFalse(base.with("message" to "ELITE SIGNAL", "messageTime" to 0f).showsMessage())
    }

    @Test
    fun trialProgressReadsAsValuesWithoutRuleText() {
        val orbit = PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f, true, 12f, 0, 0.65f,
            immutableListOf(), 0f, 0f)
        val model = base.with("pointsOfInterest" to listOf(orbit).toImmutableList())
        assertEquals(orbit, model.activeTrial())
        assertEquals("5.2 / 8.0 s", trialProgressText(model, orbit, AppLanguage.English))
        assertEquals("5,2 / 8,0 с", trialProgressText(model, orbit, AppLanguage.Russian))
        assertEquals(8, trialSegments(model, orbit))
        val sealed = PointOfInterestProjection(PointOfInterestKind.SEALED_ANOMALY, "Sealed anomaly", 0f, 0f, true, 12f, 0, 2f / 3f,
            immutableListOf(1, 2, 3), 0f, 0f)
        assertEquals("2 / 3", trialProgressText(model, sealed, AppLanguage.English))
        assertEquals(base.content.pointsOfInterest.definition(PointOfInterestKind.SEALED_ANOMALY).instruction,
            model.trialRules(sealed, AppLanguage.English))
        val inactive = orbit.copy(active = false)
        assertEquals(null, base.with("pointsOfInterest" to listOf(inactive).toImmutableList()).activeTrial())
    }

    private fun enemy(id: Int, type: EnemyType, integrity: Float) =
        EnemyProjection(id, type, 0f, 0f, 0f, 0f, 100f * integrity, 100f, 20f, 0f, 0f, 0f, 0f, 0f, 0f, false)
}

internal fun GameplayRenderModel.with(vararg overrides: Pair<String, Any?>): GameplayRenderModel {
    val map = overrides.toMap()
    val type = GameplayRenderModel::class.java
    val constructor = type.declaredConstructors.filter { !it.isSynthetic }.maxBy { it.parameterCount }
    val fields = type.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
    check(fields.size == constructor.parameterCount)
    val args = fields.map { field ->
        field.isAccessible = true
        if (field.name in map) map[field.name] else field.get(this)
    }.toTypedArray()
    constructor.isAccessible = true
    return constructor.newInstance(*args) as GameplayRenderModel
}

/** A started run's render model at [width] × [height] (density 1) for HUD tests. */
internal fun hudTestModel(width: Float = 1_440f, height: Float = 810f): GameplayRenderModel =
    // Tests override positions without their previous-step values: present the exact state.
    requireNotNull(hudTestSnapshot(width, height).renderModel).with("interpolationAlpha" to 1f)

/** The render snapshot of a started run at [width] × [height] (density 1). */
internal fun hudTestSnapshot(width: Float = 1_440f, height: Float = 810f): kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot =
    GameplayNucleus.renderSnapshot(hudTestState(width, height))

/** A started run's committed state at [width] × [height] (density 1). */
internal fun hudTestState(width: Float = 1_440f, height: Float = 810f): GameplayState {
    val content = memoryFixtureContent()
    val profile = PlayerProfile(preferences = PlayerPreferences(language = AppLanguage.English))
    val started = (GameplayNucleus.decide(
        GameplayState.initial(RunId(1), content),
        GameplayNucleusPulse.StartRun,
        GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(
            content,
            GameplayProfileSnapshot(profile.preferences, profile.economy, profile.loadout, profile.matrix, profile.collection, profile.rebirthProgress),
            seed = 731_991,
        ))),
    ) as GameplayDecision.Accepted).frame.nextState
    return (GameplayNucleus.decide(
        started,
        GameplayNucleusPulse.Intent(GameplayInteractionPulse.ViewportChanged.fromValidated(width, height, 1f)),
    ) as GameplayDecision.Accepted).frame.nextState
}

private fun memoryFixtureContent() = GameplayContentSnapshot(
    version = KINETICKK_CONTENT_VERSION,
    items = immutableListOf(
        ItemDefinition(
            0, "Ballast Core", "Lead in the heart of the Core turns every collision into a shove.",
            ItemRarity.COMMON, immutableListOf(BuildTag.RAM),
            stats = immutableListOf(StatDelta(ItemEffect.MASS, 0.12f), StatDelta(ItemEffect.IMPACT_DAMAGE, 0.06f)),
            maxStacks = 3, unlockLevel = 1,
        ),
    ),
    weapons = WeaponId.entries.map { fixtureWeapon(it, description = "Fixture.") }.toImmutableList(),
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    relics = rewardFixtureRelics.toImmutableList(),
    // The shipped ladder, so HUD tests can show any tier's mutators (tier 0 is the plain baseline).
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 10,
        profiles = (0..10).map { kinetickk.ball.content.api.defaultRebirthProfile(it) }.toImmutableList(),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 3),
)
