// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.fixtureWeapon
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureRelics
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/** Local, uncommitted redesign benchmark: HUD + world draw cost per frame. */
class RedesignRenderBenchmarkTest {
    @Test
    fun measureGameplayDraw() {
        if (System.getenv("KK_RENDER_BENCH") != "1") return
        val width = 1440; val height = 810
        val scenes = scenes(width, height).map { busy(it) }
        val counts = scenes.map { "${it.enemies.size}/${it.projectiles.size}/${it.pickups.size}/${it.trail.size}" }
        val measurer = CanvasTextMeasurer(
            ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
            1.25f, AppLanguage.English,
        )
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val thread = Thread.currentThread().id
        fun draw(i: Int) {
            val model = scenes[i % scenes.size]
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
                drawGameplay(model, VisualFxProjection.EMPTY, measurer, i / 60f, null)
            }
        }
        repeat(600) { draw(it) }
        val samples = LongArray(1500)
        val allocatedBefore = threads.getThreadAllocatedBytes(thread)
        for (i in samples.indices) {
            val start = System.nanoTime(); draw(i); samples[i] = System.nanoTime() - start
        }
        val allocated = threads.getThreadAllocatedBytes(thread) - allocatedBefore
        samples.sort()
        val mean = samples.average() / 1e6
        val p50 = samples[samples.size / 2] / 1e6
        val p95 = samples[(samples.size * 0.95).toInt()] / 1e6
        val result = "label=${System.getenv("KK_RENDER_LABEL") ?: "run"} scenes=${scenes.size} counts=${counts.distinct().take(4)} " +
            "meanMs=${"%.3f".format(mean)} p50Ms=${"%.3f".format(p50)} p95Ms=${"%.3f".format(p95)} " +
            "allocKbPerFrame=${"%.1f".format(allocated / samples.size / 1024.0)}"
        println(result)
        File(System.getenv("KK_RENDER_OUT") ?: "build/redesign-bench.txt").appendText(result + "\n")
    }

    private fun scenes(width: Int, height: Int): List<GameplayRenderModel> {
        var state = startedState(width, height)
        val out = ArrayList<GameplayRenderModel>()
        var t = 0f
        for (frame in 0 until 60 * 150) {
            t += 1f / 60f
            val px = width * 0.5f + cos(t * 0.9f) * width * 0.3f
            val py = height * 0.5f + sin(t * 1.3f) * height * 0.3f
            state = step(state, GameplayInteractionPulse.PointerMoved.fromValidated(px, py))
            state = step(state, GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f))
            val model = requireNotNull(GameplayNucleus.renderSnapshot(state).renderModel)
            when (model.phase) {
                GamePhase.CHOICE -> state = step(state, GameplayInteractionPulse.ChoiceSelected.fromValidated(0))
                GamePhase.GAME_OVER, GamePhase.VICTORY -> break
                else -> Unit
            }
            if (model.phase == GamePhase.RUNNING && frame > 60 * 60 && frame % 30 == 0) out += model
        }
        check(out.isNotEmpty()) { "no running scenes collected" }
        return out
    }

    private fun busy(model: GameplayRenderModel): GameplayRenderModel {
        val random = java.util.Random(7)
        val types = kinetickk.ball.gameplay.nucleus.render.EnemyType.entries.filter { it != kinetickk.ball.gameplay.nucleus.render.EnemyType.ARCHITECT }
        val enemies = List(90) { index ->
            val x = model.coreX + (random.nextFloat() - 0.5f) * 1300f
            val y = model.coreY + (random.nextFloat() - 0.5f) * 740f
            kinetickk.ball.gameplay.nucleus.render.EnemyProjection(index + 1000, types[index % types.size], x, y,
                random.nextFloat() * 80f - 40f, random.nextFloat() * 80f - 40f, 40f, 60f, 14f + (index % 3) * 4f,
                random.nextFloat(), if (index % 11 == 0) 0.05f else 0f, 0f, random.nextFloat(), x - 2f, y - 2f, false)
        }.toImmutableList()
        val projectiles = List(120) { index ->
            val x = model.coreX + (random.nextFloat() - 0.5f) * 1300f
            val y = model.coreY + (random.nextFloat() - 0.5f) * 740f
            kinetickk.ball.gameplay.nucleus.render.ProjectileProjection(x, y, 200f, -120f, 5f, 1f, index % 2 == 0, 5f, 0,
                index % 5, null, x - 3f, y + 2f)
        }.toImmutableList()
        val pickups = List(60) { index ->
            val x = model.coreX + (random.nextFloat() - 0.5f) * 1300f
            val y = model.coreY + (random.nextFloat() - 0.5f) * 740f
            kinetickk.ball.gameplay.nucleus.render.PickupProjection(
                kinetickk.ball.gameplay.nucleus.render.PickupType.entries[index % 2], x, y, 0f, 0f, 5f, x, y)
        }.toImmutableList()
        val type = GameplayRenderModel::class.java
        val constructor = type.declaredConstructors.filter { !it.isSynthetic }.maxBy { it.parameterCount }
        val fields = type.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        check(fields.size == constructor.parameterCount) { "field/param mismatch ${fields.size} ${constructor.parameterCount}" }
        val args = fields.map { field ->
            field.isAccessible = true
            when (field.name) {
                "enemies" -> enemies
                "projectiles" -> projectiles
                "pickups" -> pickups
                else -> field.get(model)
            }
        }.toTypedArray()
        constructor.isAccessible = true
        return constructor.newInstance(*args) as GameplayRenderModel
    }

    private fun step(state: GameplayState, pulse: GameplayInteractionPulse): GameplayState =
        when (val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))) {
            is GameplayDecision.Accepted -> decision.frame.nextState
            else -> state
        }

    private fun startedState(width: Int, height: Int): GameplayState {
        val content = benchmarkFixtureContent()
        val profile = PlayerProfile(preferences = PlayerPreferences(language = AppLanguage.English))
        val initial = GameplayState.initial(RunId(1), content)
        val started = (GameplayNucleus.decide(
            initial,
            GameplayNucleusPulse.StartRun,
            GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(
                content,
                GameplayProfileSnapshot(profile.preferences, profile.economy, profile.loadout, profile.matrix, profile.collection, profile.rebirthProgress),
                seed = 731_991,
            ))),
        ) as GameplayDecision.Accepted).frame.nextState
        return step(started, GameplayInteractionPulse.ViewportChanged.fromValidated(width.toFloat(), height.toFloat(), 1f))
    }
}

private fun benchmarkFixtureContent() = GameplayContentSnapshot(
    version = KINETICKK_CONTENT_VERSION,
    items = listOf("Cinder", "Neon", "Gravitic").mapIndexed { index, component ->
        val name = "$component Ram"
        ItemDefinition(
            index, name,
            "$name binds the Impact family to a $component component: +5% Impact damage and +4% Weapon power per stack (max 8).",
            if (index == 1) ItemRarity.UNCOMMON else ItemRarity.COMMON,
            immutableListOf(BuildTag.RAM),
            stats = immutableListOf(StatDelta(ItemEffect.IMPACT_DAMAGE, 0.05f), StatDelta(ItemEffect.WEAPON_POWER, 0.04f)),
            maxStacks = 3, unlockLevel = 1,
        )
    }.toImmutableList(),
    weapons = immutableListOf(fixtureWeapon(
        WeaponId.FLUX_WAKE, "Flux Wake", "Your trail hardens into a cutting wake; enemies crossing it take damage.",
    )),
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    relics = rewardFixtureRelics.toImmutableList(),
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 0,
        profiles = immutableListOf(RebirthProfile(
            0, "Baseline", 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
            120, 0.09f, 24f,
        )),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 5),
)
