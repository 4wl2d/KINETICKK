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
import kinetickk.ball.content.api.DungeonHazardKind
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.interaction.*
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*
import kinetickk.ball.gameplay.interaction.fx.DungeonMomentProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.dungeonMomentSeconds
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.render.DungeonBreachProjection
import kinetickk.ball.gameplay.nucleus.render.DungeonHazardProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A dungeon fight on the hot path: the Foundry's furnace sealed around its living guardian with escorts, a wave's
 * breach portals closing, vents and a lance telegraphing and striking as the frames' time moves, every moment kind
 * playing, the motes, sconces, alarm and threshold of the floors in view, all drawn with the HUD (its alarm vignette),
 * allocate at most the world budget above an empty frame and build no dungeon path or light brush once warm.
 */
class WorldDungeonHotPathAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun aSealedFightWithItsTelegraphsMomentsAndAirDrawsWithoutAllocating() {
        for (roles in listOf(KkRolePalette.Default, KkRolePalette.Mono)) {
            val base = hudTestModel(WIDTH.toFloat(), HEIGHT.toFloat())
            val template = base.content.structures.template(StructureTemplateId.FOUNDRY)
            val room = template.rooms[template.finalRoom]
            val local = StructureFixture(template, 0f, 0f).roomCenter(template.finalRoom)
            val foundry = StructureFixture(template, base.coreX - local.first, base.coreY - local.second, clearedRooms = 0b0011,
                engagedRoom = template.finalRoom)
            val vault = StructureFixture(base.content.structures.template(StructureTemplateId.VAULT), base.coreX - 600f, base.coreY - 2_100f)
            val projection = structureProjection(foundry, vault, dungeon = foundry)
            val enemies = listOf(EnemyType.CHARGER to (0f to -150f), EnemyType.WARDEN to (-150f to 200f), EnemyType.BOMBER to (140f to 220f))
                .mapIndexed { index, (type, at) ->
                    val x = base.coreX + at.first
                    val y = base.coreY + at.second
                    EnemyProjection(GUARDIAN + index, type, x, y, 0f, 0f, 60f, 100f, 24f, 1f, 0f, 0f, 0f, x, y, false)
                }.toImmutableList()
            val (lanceAx, lanceAy) = foundry.at(room.minX + 24f, (room.minY + room.maxY) * 0.5f)
            val (lanceBx, lanceBy) = foundry.at(room.maxX - 24f, (room.minY + room.maxY) * 0.5f)
            val frames = List(FRAMES) { frame ->
                val time = START + frame * GameplayRenderModel.FIXED_STEP
                val hazards = immutableListOf(
                    DungeonHazardProjection(DungeonHazardKind.VENT, base.coreX + 40f, base.coreY, base.coreX + 40f, base.coreY, 120f, START, START + 1.2f),
                    DungeonHazardProjection(DungeonHazardKind.VENT, base.coreX - 90f, base.coreY - 260f, base.coreX - 90f, base.coreY - 260f, 120f,
                        START - 0.6f, START + 0.6f),
                    DungeonHazardProjection(DungeonHazardKind.LANCE, lanceAx, lanceAy, lanceBx, lanceBy, 36f, START - 0.2f, START + 1f),
                )
                val breaches = immutableListOf(DungeonBreachProjection(base.coreX + 160f, base.coreY - 300f, 16f, false),
                    DungeonBreachProjection(base.coreX - 170f, base.coreY + 310f, 23f, true))
                val dungeon = checkNotNull(projection.dungeon).copy(sealedAt = START - 0.1f, waves = 3, wavesLanded = 2, liveGarrison = 3,
                    guardianId = GUARDIAN, incomingAt = START + 1.5f, breaches = breaches, hazards = hazards)
                base.with("structures" to projection.copy(dungeon = dungeon), "enemies" to enemies, "elapsed" to time, "trial" to structureTrial(foundry))
            }
            val fx = List(FRAMES) { frame ->
                VisualFxProjection.EMPTY.copy(dungeonMoments = DungeonMomentKind.entries.map { kind ->
                    val life = dungeonMomentSeconds(kind)
                    DungeonMomentProjection(kind, StructureTemplateId.FOUNDRY, base.coreX, base.coreY, 250f, 400f, base.coreX, base.coreY - 150f,
                        life * (0.95f - frame * 0.003f), life, 7)
                }.toImmutableList())
            }
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                1.25f, AppLanguage.English, HudTestFonts.typography, roles)
            val canvas = Canvas(ImageBitmap(WIDTH, HEIGHT))
            val scope = CanvasDrawScope()
            val size = Size(WIDTH.toFloat(), HEIGHT.toFloat())
            val memory = HudPresentationMemory()
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun frame(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawGameplay(frames[index], fx[index], measurer, 10f + index * 0.0001f, pauseLayout = null, hudMemory = memory)
            }
            val thread = Thread.currentThread().id
            repeat(FRAMES) { empty(); frame(it) }
            val built = listOf(StructurePaths.builds, DungeonFloorPaths.builds, DungeonLightBrushes.builds)
            var before = threads.getThreadAllocatedBytes(thread)
            repeat(FRAMES) { empty() }
            val emptyBytes = threads.getThreadAllocatedBytes(thread) - before
            before = threads.getThreadAllocatedBytes(thread)
            repeat(FRAMES) { frame(it) }
            val frameBytes = threads.getThreadAllocatedBytes(thread) - before
            val perFrame = (frameBytes - emptyBytes) / FRAMES
            assertTrue(perFrame < 96, "a sealed Foundry fight ($roles) allocates $perFrame bytes above an empty frame")
            assertEquals(built, listOf(StructurePaths.builds, DungeonFloorPaths.builds, DungeonLightBrushes.builds),
                "a warm frame builds no structure or floor path and no light brush")
        }
    }

    private companion object {
        const val WIDTH = 1_440
        const val HEIGHT = 810
        const val FRAMES = 240
        const val GUARDIAN = 900
        const val START = 30f
    }
}
