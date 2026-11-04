// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.interaction.*
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kotlin.test.Test

/**
 * Opt-in render cost probe of a sealed dungeon room (`KK_DUNGEON_RENDER_BENCH=1`): the Foundry's furnace sealed around
 * three members with the Reliquary's chapel beside it, drawn with the HUD at 1440 × 810 for 3,000 frames after 600 warm
 * ones, the run's time moving. Prints the median of ten batches in µs per frame. Uses only facts every tree's render
 * model carries, so the same probe measures a change against its base.
 */
class DungeonRenderBenchmarkTest {
    @Test
    fun measureSealedRoomDraw() {
        if (System.getenv("KK_DUNGEON_RENDER_BENCH") != "1") return
        val base = hudTestModel(WIDTH.toFloat(), HEIGHT.toFloat())
        val template = base.content.structures.template(StructureTemplateId.FOUNDRY)
        val local = StructureFixture(template, 0f, 0f).roomCenter(template.finalRoom)
        val foundry = StructureFixture(template, base.coreX - local.first, base.coreY - local.second, clearedRooms = 0b0011,
            engagedRoom = template.finalRoom)
        val enemies = listOf(EnemyType.CHARGER to (0f to -150f), EnemyType.WARDEN to (-150f to 200f), EnemyType.BOMBER to (140f to 220f))
            .mapIndexed { index, (type, at) ->
                val x = base.coreX + at.first
                val y = base.coreY + at.second
                EnemyProjection(900 + index, type, x, y, 0f, 0f, 60f, 100f, 24f, 1f, 0f, 0f, 0f, x, y, false)
            }.toImmutableList()
        val model = base.with("structures" to structureProjection(foundry, dungeon = foundry), "enemies" to enemies,
            "trial" to structureTrial(foundry))
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
            1.25f, AppLanguage.English, HudTestFonts.typography)
        val canvas = Canvas(ImageBitmap(WIDTH, HEIGHT))
        val scope = CanvasDrawScope()
        val size = Size(WIDTH.toFloat(), HEIGHT.toFloat())
        val memory = HudPresentationMemory()
        val frames = List(64) { model.with("elapsed" to 20f + it * GameplayRenderModel.FIXED_STEP) }
        fun frame(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
            drawGameplay(frames[index % frames.size], VisualFxProjection.EMPTY, measurer, 10f + index * 0.0001f, pauseLayout = null, hudMemory = memory)
        }
        repeat(WARM) { frame(it) }
        val batches = List(BATCHES) {
            val start = System.nanoTime()
            repeat(FRAMES / BATCHES) { frame(it) }
            (System.nanoTime() - start) / 1_000.0 / (FRAMES / BATCHES)
        }.sorted()
        println("DUNGEON RENDER sealed furnace µs/frame median=${"%.1f".format(batches[BATCHES / 2])} " +
            "min=${"%.1f".format(batches.first())} max=${"%.1f".format(batches.last())}")
        val roles = kinetickk.foundation.design.KkRolePalette.Default
        val layers = listOf<Pair<String, androidx.compose.ui.graphics.drawscope.DrawScope.(GameplayRenderModel) -> Unit>>(
            "floors" to { m -> drawStructureFloors(m, 0f, 0f, roles) },
            "walls" to { m -> drawStructureWalls(m, 0f, 0f, roles) },
            "scrim" to { m -> drawStructureScrim(m, 0f, 0f) },
            "atmosphere" to { m -> drawDungeonAtmosphere(m, 0f, 0f, roles) },
        )
        for ((name, layer) in layers) {
            fun one(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { layer(frames[index % frames.size]) }
            repeat(200) { one(it) }
            val start = System.nanoTime()
            repeat(1_000) { one(it) }
            println("DUNGEON RENDER layer $name µs/frame=${"%.1f".format((System.nanoTime() - start) / 1_000.0 / 1_000)}")
        }
    }

    private companion object {
        const val WIDTH = 1_440
        const val HEIGHT = 810
        const val WARM = 600
        const val FRAMES = 3_000
        const val BATCHES = 10
    }
}
