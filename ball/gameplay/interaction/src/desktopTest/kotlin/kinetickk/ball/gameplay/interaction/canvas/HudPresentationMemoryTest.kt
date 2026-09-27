// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunId
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
        val steady = base.with("polarityStability" to 0.5f)
        memory.observe(steady, 0f, boss.select(steady))
        memory.observe(steady, 0.1f, boss.select(steady))
        assertFalse(memory.polarityDraining(0.1f))
        val drained = base.with("polarityStability" to 0.48f)
        memory.observe(drained, 0.2f, boss.select(drained))
        assertTrue(memory.polarityDraining(0.2f))
        // A repeated draw of the same frame keeps the pulse briefly, then it settles.
        memory.observe(drained, 0.3f, boss.select(drained))
        assertTrue(memory.polarityDraining(0.3f))
        assertFalse(memory.polarityDraining(1f))
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
    requireNotNull(hudTestSnapshot(width, height).renderModel)

/** The render snapshot of a started run at [width] × [height] (density 1). */
internal fun hudTestSnapshot(width: Float = 1_440f, height: Float = 810f): kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot {
    val content = memoryFixtureContent()
    val profile = PlayerProfile(preferences = PlayerPreferences(language = AppLanguage.English))
    val started = (GameplayNucleus.decide(
        GameplayState.initial(RunId(1), content),
        GameplayNucleusPulse.StartRun,
        GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(
            content,
            GameplayProfileSnapshot(profile.preferences, profile.economy, profile.loadout, profile.labProgress, profile.collection, profile.rebirthProgress),
            seed = 731_991,
        ))),
    ) as GameplayDecision.Accepted).frame.nextState
    val resized = (GameplayNucleus.decide(
        started,
        GameplayNucleusPulse.Intent(GameplayInteractionPulse.ViewportChanged.fromValidated(width, height, 1f)),
    ) as GameplayDecision.Accepted).frame.nextState
    return GameplayNucleus.renderSnapshot(resized)
}

private fun memoryFixtureContent() = GameplayContentSnapshot(
    version = KINETICKK_CONTENT_VERSION,
    items = immutableListOf(
        ItemDefinition(
            0, "Cinder Ram", "Cinder Ram binds the Impact family to a Cinder component: +5% Impact damage and +4% Weapon power per stack (max 8).",
            ItemRarity.COMMON, ItemModifier(ItemEffect.IMPACT_DAMAGE, 0.05f), ItemModifier(ItemEffect.WEAPON_POWER, 0.04f),
            maxStacks = 8, unlockLevel = 1, family = "Impact",
        ),
    ),
    weapons = WeaponId.entries.map { WeaponDefinition(it, it.name, "Fixture.", listOf("TRAIL"), 0) }.toImmutableList(),
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    metaUpgrades = MetaUpgradeId.entries.map { id ->
        MetaUpgradeDefinition(id, "Fixture", "Fixture", 1, 1, ItemModifier(ItemEffect.MAX_INTEGRITY, 1f))
    }.toImmutableList(),
    relics = RelicId.entries.map { id -> RelicDefinition(id, id.name, RelicAspect.VECTOR, "Fixture", "Fixture") }.toImmutableList(),
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 0,
        profiles = immutableListOf(RebirthProfile(
            0, RebirthDirective.BASELINE, 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
            120, 0.09f, 24f,
        )),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 5),
)
