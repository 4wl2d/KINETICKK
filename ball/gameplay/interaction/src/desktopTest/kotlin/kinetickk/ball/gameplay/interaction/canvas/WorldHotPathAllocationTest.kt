// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.CoreShape
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
import kinetickk.ball.gameplay.interaction.fx.DamageNumberProjection
import kinetickk.ball.gameplay.interaction.fx.MotionEchoProjection
import kinetickk.ball.gameplay.interaction.fx.ParticleProjection
import kinetickk.ball.gameplay.interaction.fx.ShockwaveProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.WeaponArcProjection
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.ball.gameplay.nucleus.render.ProjectileProjection
import kinetickk.ball.gameplay.nucleus.render.TotemProjection
import kinetickk.ball.gameplay.nucleus.render.TrailPointProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The world layer runs every frame: once caches are warm (paths, strokes, brushes, strings, text
 * layouts), a busy frame allocates no JVM heap beyond the draw scope's own baseline. Also checks
 * that the world keeps its key colors through the Color vision palette.
 */
class WorldHotPathAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun busyWorldFrameDoesNotAllocate() {
        KkRolePalette.Default.let { roles -> assertFrameIsAllocationFree(roles, WeaponId.MORNINGSTAR) }
        assertFrameIsAllocationFree(KkRolePalette.Mono, WeaponId.ARC_COIL)
    }

    @Test
    fun worldColorsFollowTheRolePalette() {
        val model = busyModel(WeaponId.ARC_COIL)
        val fx = busyFx()
        listOf(KkRolePalette.Default, KkRolePalette.Protan).forEach { roles ->
            val pixels = render(model, fx, roles)
            fun count(color: androidx.compose.ui.graphics.Color): Int {
                val target = color.toArgb()
                return pixels.count { it == target }
            }
            assertTrue(count(roles.threat) > 400, "Enemies, bullets and the singularity use the threat role")
            assertTrue(count(roles.you) > 150, "Trail, weapon and progress use the you role")
            assertTrue(count(Kk.Bone) > 400, "The Core body is bone")
            val other = if (roles == KkRolePalette.Default) KkRolePalette.Protan else KkRolePalette.Default
            assertTrue(count(other.threat) < 20, "No hard-coded threat color leaks into the world")
        }
    }

    private fun assertFrameIsAllocationFree(roles: KkRolePalette, weapon: WeaponId) {
        val model = busyModel(weapon)
        val fx = busyFx()
        val measurer = measurer(roles)
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        val scope = CanvasDrawScope()
        val canvas = Canvas(bitmap)
        val thread = Thread.currentThread().id
        fun bytesPerFrame(block: DrawScope.() -> Unit): Long {
            fun frame() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(WIDTH.toFloat(), HEIGHT.toFloat()), block)
            repeat(200) { frame() }
            val before = threads.getThreadAllocatedBytes(thread)
            repeat(300) { frame() }
            return (threads.getThreadAllocatedBytes(thread) - before) / 300
        }
        val baseline = bytesPerFrame { }
        val world = bytesPerFrame {
            drawBackdrop(model, 1.5f, -1f, 2f, roles)
            drawWorld(model, fx, 1.5f, -1f, measurer)
            drawScreenFx(model, 2f, roles)
        }
        assertTrue(world - baseline < 96, "A busy world frame allocates ${world - baseline} bytes above the $baseline byte baseline")
    }

    private fun render(model: GameplayRenderModel, fx: VisualFxProjection, roles: KkRolePalette): IntArray {
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawRect(Kk.Ink)
            drawBackdrop(model, 0f, 0f, 2f, roles)
            drawWorld(model, fx, 0f, 0f, measurer(roles))
            drawScreenFx(model, 2f, roles)
        }
        val map = bitmap.toPixelMap()
        return IntArray(WIDTH * HEIGHT) { map[it % WIDTH, it / WIDTH].toArgb() }
    }

    private fun measurer(roles: KkRolePalette) = CanvasTextMeasurer(
        ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
        1.25f, AppLanguage.English, roles = roles,
    )

    private fun busyFx(): VisualFxProjection = VisualFxProjection(
        particles = (0 until 40).map { index ->
            ParticleProjection(CORE + (index % 8) * 30f - 120f, CORE - 150f + (index / 8) * 20f, 90f, -40f + index, 0.3f, 0.6f, index % 5,
                if (index % 10 < 4) 6f else 2f)
        }.toImmutableList(),
        motionEchoes = immutableListOf(MotionEchoProjection(CORE - 50f, CORE + 20f, 0.2f, 0.3f, 1f)),
        shockwaves = immutableListOf(
            ShockwaveProjection(CORE + 200f, CORE - 60f, 0.25f, 0.3f, 60f, 1),
            ShockwaveProjection(CORE + 120f, CORE + 80f, 0.22f, 0.26f, 110f, 3),
        ),
        damageNumbers = listOf(96L, 184L, 742L, 4_800L).mapIndexed { index, amount ->
            DamageNumberProjection(CORE + index * 70f, CORE - 90f, amount, critical = index == 3, life = 0.4f, driftX = 0.6f, driftY = -0.8f)
        }.toImmutableList(),
        weaponArcs = immutableListOf(WeaponArcProjection(CORE, CORE, CORE + 180f, CORE + 60f, 0.1f)),
    )

    private fun busyModel(weapon: WeaponId): GameplayRenderModel {
        val enemies = EnemyType.entries.mapIndexed { index, type ->
            val angle = index * 0.7f
            val distance = if (type == EnemyType.ARCHITECT) 260f else 180f + index * 12f
            EnemyProjection(index + 1, type, CORE + kotlin.math.cos(angle) * distance, CORE + kotlin.math.sin(angle) * distance, 0f, 0f,
                50f, 100f, if (type == EnemyType.ARCHITECT) 74f else 20f, 0.2f, if (index == 2) 1f else 0f, 0f, 0f, CORE, CORE, false)
        }
        return GameplayRenderModel(
            content = Content, phase = GamePhase.RUNNING, settings = PlayerPreferences(textScale = 1.25f), rebirthLevel = 5,
            screenWidth = WIDTH.toFloat(), screenHeight = HEIGHT.toFloat(), uiScale = 1f, coreX = CORE, coreY = CORE,
            velocityX = 420f, velocityY = -160f, cameraX = CORE, cameraY = CORE, pointerX = WIDTH * 0.5f + 180f,
            pointerY = HEIGHT * 0.5f - 120f, pointerActive = true, braking = true, elapsed = 3f, heat = 20f, overheated = false,
            dashPhaseTime = 0f, hp = 100f, maxHp = 180f, shield = 1f, maxShield = 3f, level = 5, data = 1, nextLevelData = 5,
            keys = 1, kills = 3, combo = 3, comboTime = 1f, runMatter = 10L, totalMatter = 10L, lastImpact = 20f, lastImpactTime = 0.3f,
            damageFlash = 0.6f, runGrace = 0f, screenShake = 0f, message = "", messageTime = 0f, mass = 1f, damageMultiplier = 1f,
            weaponPower = 1f, coolingRate = 1f, magnetStrength = 1f, dashImpulse = 1f, dashHeatCost = 20f, regenPerSecond = 0f,
            critChance = 0f, critMultiplier = 2f, pickupRadius = 150f, luck = 0f, dataGain = 1f, matterGain = 1f, attackSpeed = 1f,
            damageReduction = 0f, comboWindow = 2f, overdriveGain = 1f, dragCoefficient = 1f, polarityStability = 0.8f,
            weapon = weapon, weaponLevel = 3, overdriveCharge = 10f, overdriveTime = 0f, rerollsRemaining = 1, acquiredItemCount = 0,
            recentItem = null, equippedRelics = immutableListOf(), morningstarAngle = 0.3f, morningstarX = CORE + 60f,
            morningstarY = CORE + 40f, weaponBeamTime = 0f, weaponBeamStartX = 0f, weaponBeamStartY = 0f, weaponBeamEndX = 0f,
            weaponBeamEndY = 0f, totem = TotemProjection(CORE - 1_600f, CORE + 100f, 1f), coreShape = CoreShape.TESSERACT,
            enemies = enemies.toImmutableList(),
            projectiles = (0 until 12).map { index ->
                ProjectileProjection(CORE - 200f + index * 25f, CORE + 120f, 0f, 0f, 4.5f, 1f, index % 3 != 0, 1f, 0, 0, null,
                    CORE - 206f + index * 25f, CORE + 123f)
            }.toImmutableList(),
            pickups = PickupType.entries.mapIndexed { index, type ->
                PickupProjection(type, CORE - 150f + index * 30f, CORE - 60f, 0f, 0f, 3f, CORE - 155f + index * 30f, CORE - 57f)
            }.toImmutableList(),
            trail = (0 until 24).map { index -> TrailPointProjection(CORE - index * 15f, CORE + index * 8f, index * 0.08f) }.toImmutableList(),
            weaponNodes = immutableListOf(WeaponNodeProjection(WeaponNodeType.GRAVITY_MINE, CORE - 250f, CORE - 200f, 2f, 3f, 55f)),
            weaponOrbitals = immutableListOf(), choices = immutableListOf(), choiceType = ChoiceType.ITEM, pendingRelicChoiceCount = 0,
            itemStacks = immutableListOf(), discoveredItemIds = immutableSetOf(), relicRanks = immutableListOf(),
            pointsOfInterest = immutableListOf(
                PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Orbit", CORE + 60f, CORE + 40f, true, 14f, 1, 0.4f,
                    immutableListOf(), 0.5f, 0.3f),
                PointOfInterestProjection(PointOfInterestKind.SEALED_ANOMALY, "Sealed", CORE + 2_000f, CORE - 300f, false, 20f, 1, 0f,
                    immutableListOf(), 0f, 0f),
            ),
        )
    }

    private companion object {
        const val WIDTH = 960
        const val HEIGHT = 600
        const val CORE = 1_000f

        val Content = GameplayContentSnapshot(
            version = KINETICKK_CONTENT_VERSION,
            items = immutableListOf(ItemDefinition(0, "Ram", "Ram", ItemRarity.COMMON, ItemModifier(ItemEffect.IMPACT_DAMAGE, 0.05f),
                ItemModifier(ItemEffect.WEAPON_POWER, 0.04f), 8, 1, "Impact")),
            weapons = WeaponId.entries.map { WeaponDefinition(it, it.name, "Fixture", listOf("TAG"), 0) }.toImmutableList(),
            weaponMasteries = WeaponMastery.entries.toImmutableList(),
            metaUpgrades = MetaUpgradeId.entries.map { id ->
                MetaUpgradeDefinition(id, "F", "F", 1, 1, ItemModifier(ItemEffect.MAX_INTEGRITY, 1f))
            }.toImmutableList(),
            relics = RelicId.entries.map { id -> RelicDefinition(id, id.name, RelicAspect.entries.first(), "F", "F") }.toImmutableList(),
            rebirth = RebirthPolicySnapshot(
                minimumLevel = 0, maximumLevel = 0,
                profiles = immutableListOf(RebirthProfile(0, RebirthDirective.BASELINE, 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0, 120, 0.09f, 24f)),
                maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
            ),
            relicPolicy = RelicPolicy(4, 5),
        )
    }
}
