// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.SkillId
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
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.ball.gameplay.nucleus.render.SkillBarProjection
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.KkLevelTier
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
    fun tierOneShotFiresOnlyWhenTheTierRises() {
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        fun observe(level: Int, at: Float) {
            val model = base.with("level" to level, "maxLevel" to CAP)
            memory.observe(model, at, boss.select(model))
        }
        observe(9, 0f)
        assertEquals(1f, memory.tierBurst(0f), "the first frame only records")
        observe(10, 1f)
        assertEquals(0f, memory.tierBurst(1f), "9 to 10 enters T2")
        assertEquals(KkLevelTier.T2, memory.tierTo)
        assertEquals(0.5f, memory.tierBurst(1.3f), 0.001f)
        assertEquals(1f, memory.tierBurst(1.6f))
        observe(11, 2f)
        assertEquals(1f, memory.tierBurst(2f), "10 to 11 stays in T2")
        observe(40, 3f)
        assertEquals(KkLevelTier.T5, memory.tierTo)
        assertEquals(0f, memory.tierBurst(3f))
        // The cap is the Apex unlock, not another tier burst.
        observe(41, 5f)
        observe(CAP, 6f)
        assertEquals(1f, memory.tierBurst(6f))
        assertEquals(0f, memory.apexUnlock(6f))
        // A memory whose first frame is already high fires nothing.
        val fresh = HudPresentationMemory()
        val high = base.with("level" to 25, "maxLevel" to CAP)
        fresh.observe(high, 0f, boss.select(high))
        assertEquals(1f, fresh.tierBurst(0f))
    }

    @Test
    fun apexOneShotsFollowTheirEdges() {
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        fun observe(level: Int, apex: ApexProjection, at: Float) {
            val model = base.with("level" to level, "maxLevel" to CAP, "apex" to apex)
            memory.observe(model, at, boss.select(model))
        }
        val ready = ApexProjection(unlocked = true, ready = true)
        val cooling = ApexProjection(unlocked = true, cooldown01 = 0.5f)
        observe(41, ApexProjection.LOCKED, 0f)
        // Unlock once, ready on unlock: the unlock plays, the ready edge pings nothing.
        observe(CAP, ready, 1f)
        assertEquals(0f, memory.apexUnlock(1f))
        assertEquals(1f, memory.apexPing(1f))
        assertEquals(0.5f, memory.apexUnlock(1.6f), 0.001f)
        observe(CAP, ready, 2f)
        assertEquals(1f, memory.apexUnlock(2.2f))
        observe(CAP, ready, 3f)
        assertEquals(1f, memory.apexUnlock(3f), "the unlock never repeats")
        // Cast: the activation starting; draining activation fires nothing more.
        observe(CAP, ApexProjection(unlocked = true, active01 = 1f), 4f)
        assertEquals(0f, memory.apexCast(4f))
        observe(CAP, ApexProjection(unlocked = true, active01 = 0.8f), 4.3f)
        assertEquals(0.5f, memory.apexCast(4.3f), 0.001f)
        // Cooling, then Data refunds: each rise flashes, a new cycle's reset does not.
        observe(CAP, cooling, 5f)
        observe(CAP, cooling.copy(refunded = 2f), 6f)
        assertEquals(0f, memory.apexRefund(6f))
        assertEquals(1f, memory.apexRefund(6.3f))
        observe(CAP, cooling.copy(refunded = 2f), 7f)
        assertEquals(1f, memory.apexRefund(7f))
        observe(CAP, cooling, 8f)
        assertEquals(1f, memory.apexRefund(8f))
        observe(CAP, cooling.copy(refunded = 1f), 9f)
        assertEquals(0f, memory.apexRefund(9f))
        // Ready again: the ping.
        observe(CAP, ready, 10f)
        assertEquals(0f, memory.apexPing(10f))
        assertEquals(0.5f, memory.apexPing(10.21f), 0.001f)
        observe(CAP, ready, 11f)
        assertEquals(1f, memory.apexPing(11f))
        // Ready a moment after the unlock (while it still plays): still no ping.
        val late = HudPresentationMemory()
        listOf(Triple(41, ApexProjection.LOCKED, 0f), Triple(CAP, cooling, 1f), Triple(CAP, ready, 1.2f)).forEach { (level, apex, at) ->
            val model = base.with("level" to level, "maxLevel" to CAP, "apex" to apex)
            late.observe(model, at, boss.select(model))
        }
        assertEquals(1f, late.apexPing(1.2f))
    }

    @Test
    fun skillSlotOneShotsFollowTheirEdges() {
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        val empty = SkillSlotProjection.EMPTY
        fun observe(first: SkillSlotProjection, second: SkillSlotProjection, at: Float) {
            val model = base.with("skillBar" to SkillBarProjection(immutableListOf(first, second, empty)))
            memory.observe(model, at, boss.select(model))
        }
        observe(empty, empty, 0f)
        // A draft slams the slot and slides the row in; its rank, ready and refund flashes stay quiet.
        val drafted = SkillSlotProjection(skill = 3, rank = 1, ready = true)
        observe(drafted, empty, 1f)
        assertEquals(0f, memory.skillAcquired(0, 1f))
        assertEquals(0f, memory.skillRow(1f))
        assertEquals(1f, memory.skillRanked(0, 1f))
        assertEquals(1f, memory.skillReady(0, 1f))
        assertEquals(1f, memory.skillAcquired(1, 1f))
        observe(drafted.copy(rank = 2), empty, 2f)
        assertEquals(0f, memory.skillRanked(0, 2f))
        assertEquals(0.5f, memory.skillRanked(0, 2.3f), 0.001f)
        observe(drafted.copy(rank = 2, ready = false, cooldown01 = 1f), empty, 3f)
        assertEquals(1f, memory.skillReady(0, 3f))
        observe(drafted.copy(rank = 2), empty, 4f)
        assertEquals(0f, memory.skillReady(0, 4f))
        observe(drafted.copy(rank = 2, refunded = 1.5f), empty, 5f)
        assertEquals(0f, memory.skillRefund(0, 5f))
        // A second draft slams its own slot; the row is already in.
        observe(drafted.copy(rank = 2, refunded = 1.5f), SkillSlotProjection(skill = 9, rank = 1), 6f)
        assertEquals(0f, memory.skillAcquired(1, 6f))
        assertEquals(1f, memory.skillAcquired(0, 6f))
        assertEquals(1f, memory.skillRow(6f))
    }

    @Test
    fun aReplacedActiveSlamsItsSlotOnTheNewEffectAndNeverRanksOrSlidesTheRow() {
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        val empty = SkillSlotProjection.EMPTY
        fun observe(first: SkillSlotProjection, at: Float) {
            val model = base.with("skillBar" to SkillBarProjection(immutableListOf(first, empty, empty)))
            memory.observe(model, at, boss.select(model))
        }
        observe(empty, 0f)
        observe(SkillSlotProjection(skill = 3, rank = 3, ready = true), 1f)
        // The slot's active item is replaced: another effect at rank I, ready at once.
        observe(SkillSlotProjection(skill = 7, rank = 1, ready = true), 5f)
        assertEquals(0f, memory.skillAcquired(0, 5f), "the slam keys on the slot's effect")
        assertEquals(1f, memory.skillRanked(0, 5f))
        assertEquals(1f, memory.skillReady(0, 5f))
        assertEquals(1f, memory.skillRow(5f))
        // Ranking the new effect up rings as usual.
        observe(SkillSlotProjection(skill = 7, rank = 2, ready = true), 6f)
        assertEquals(0f, memory.skillRanked(0, 6f))
    }

    @Test
    fun aNewRunReArmsTheOneShotsWithoutFiringThem() {
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        fun observe(level: Int, elapsed: Float, at: Float, skill: Int = -1) {
            val slot = if (skill < 0) SkillSlotProjection.EMPTY else SkillSlotProjection(skill = skill, rank = 1, ready = true)
            val model = base.with("level" to level, "maxLevel" to CAP, "elapsed" to elapsed,
                "skillBar" to SkillBarProjection(immutableListOf(slot, SkillSlotProjection.EMPTY, SkillSlotProjection.EMPTY)))
            memory.observe(model, at, boss.select(model))
        }
        observe(9, 300f, 0f)
        observe(10, 301f, 1f)
        assertEquals(0f, memory.tierBurst(1f))
        // The clock goes back: a new run. What the old run was playing stops; what the new one shows fires nothing.
        observe(25, 0f, 1.1f, skill = 4)
        assertEquals(1f, memory.tierBurst(1.1f))
        assertEquals(1f, memory.skillAcquired(0, 1.1f))
        assertEquals(1f, memory.skillRow(1.1f))
        // Armed again from the next frame on.
        observe(30, 1f, 2f, skill = 4)
        assertEquals(0f, memory.tierBurst(2f))
        assertEquals(KkLevelTier.T4, memory.tierTo)
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

    private companion object {
        /** The content's run level cap. */
        const val CAP = 42
    }
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

/** The content's run level cap the HUD fixtures play at (the badge's APEX tier). */
internal const val HUD_TEST_LEVEL_CAP = 42

/**
 * Overrides for a run at [level] under [HUD_TEST_LEVEL_CAP] with its Apex (ready at the cap, the
 * badge's widest state: both echoes, both ray sets and the glow).
 */
internal fun levelOverrides(
    level: Int = HUD_TEST_LEVEL_CAP,
    apex: ApexProjection = if (level >= HUD_TEST_LEVEL_CAP) ApexProjection(unlocked = true, ready = true) else ApexProjection.LOCKED,
): Array<Pair<String, Any?>> = arrayOf("level" to level, "maxLevel" to HUD_TEST_LEVEL_CAP, "apex" to apex)

/** A drafted skill row for HUD tests: Z ready at rank III, X cooling at rank II, C not drafted yet (locked). */
internal val MIXED_SKILL_BAR = SkillBarProjection(immutableListOf(
    SkillSlotProjection(skill = SkillId.STORM_COIL.ordinal, rank = 3, ready = true, charges = 1),
    SkillSlotProjection(skill = SkillId.METEOR.ordinal, rank = 2, cooldown01 = 0.6f),
    SkillSlotProjection.EMPTY,
))

/** Every slot drafted: two charges ready, one skill running, one in flight. */
internal val FULL_SKILL_BAR = SkillBarProjection(immutableListOf(
    SkillSlotProjection(skill = SkillId.FROST_SNAP.ordinal, rank = 1, ready = true, charges = 2, maxCharges = 2),
    SkillSlotProjection(skill = SkillId.SOLAR_FLARE.ordinal, rank = 2, cooldown01 = 0.8f, active01 = 0.5f),
    SkillSlotProjection(skill = SkillId.METEOR.ordinal, rank = 3, cooldown01 = 0.9f, pending01 = 0.4f),
))

/** The press log of a keyboard player: key caps show. */
internal fun keyboardControls(): kinetickk.ball.gameplay.interaction.input.RunningControlPressLog =
    kinetickk.ball.gameplay.interaction.input.RunningControlPressLog().apply { keyHints = true }

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
