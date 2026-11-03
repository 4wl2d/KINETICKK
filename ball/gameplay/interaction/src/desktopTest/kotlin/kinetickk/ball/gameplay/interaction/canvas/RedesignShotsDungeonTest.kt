// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.DungeonHazardKind
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.interaction.fx.DungeonMomentProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.dungeonMomentSeconds
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.render.DungeonBreachProjection
import kinetickk.ball.gameplay.nucleus.render.DungeonHazardProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.TrailPointProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.world.*

/**
 * Local visual QA harness of the dungeons, beside [RedesignShotsWorldTest] (whose file is at its size ceiling). With
 * `KK_SHOTS=1` it draws every scene at every size in both languages into build/redesign-shots/world/ (filter with
 * `KK_SHOTS_ONLY`); otherwise each scene draws once as a smoke and nothing is written. Scenes: a sealed fight in each
 * template's identity with its hazard telegraphing, a wave's breach portals, both hazards at their telegraph's end and
 * their strikes, a guardian's room with its sigil and entrance, a seal slamming its doors, a room's clear, the
 * structure's clear, the threshold of a structure still to fight under the HUD, and the palettes.
 */
class RedesignShotsDungeonTest {
    private val fullShots = System.getenv("KK_SHOTS") == "1"
    private val only = System.getenv("KK_SHOTS_ONLY")?.split(',')?.toSet()

    @Test
    fun rendersDungeonStates() {
        val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)
        val languages = listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")
        val scenes = scenes().filter { only == null || it.name in only }
        for ((width, height) in if (fullShots) sizes else sizes.take(1)) {
            for ((language, code) in if (fullShots) languages else languages.take(1)) for (scene in scenes) {
                val measurer = CanvasTextMeasurer(
                    ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
                    scene.textScale, language, RedesignShotsWorldTest.Fonts.typography, scene.roles,
                )
                val model = scene.model(width.toFloat(), height.toFloat())
                val fx = scene.fx(model)
                val bitmap = ImageBitmap(width, height)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                    drawRect(Kk.Ink)
                    drawBackdrop(model, 0f, 0f, scene.time, scene.roles)
                    drawWorld(model, fx, 0f, 0f, measurer)
                    drawScreenFx(model, scene.time, scene.roles)
                    if (scene.hud) drawHud(model, measurer, scene.time)
                }
                write(bitmap, File("build/redesign-shots/world/world-${scene.name}-${width}x$height-$code.png"))
            }
        }
    }

    private fun scenes(): List<RedesignShotsWorldTest.Scene> = buildList {
        // A sealed fight in every template's identity, its hazard a moment from striking.
        for (fight in Fights) add(scene("dungeon-${fight.template.name.lowercase().replace('_', '-')}") { w, h -> fight(w, h, fight) })
        for ((suffix, roles) in listOf("-mono" to KkRolePalette.Mono, "-protan" to KkRolePalette.Protan)) {
            add(scene("dungeon-foundry$suffix", roles) { w, h -> fight(w, h, Fights[4]) })
        }
        add(scene("dungeon-waves") { w, h -> fight(w, h, Fights[0], breaches = true, hazards = false) })
        add(scene("dungeon-hazard-vent") { w, h -> fight(w, h, Fights[1], hazardProgress = 0.92f) })
        add(scene("dungeon-hazard-vent-strike") { w, h -> fight(w, h, Fights[1], hazardProgress = 1.1f) })
        add(scene("dungeon-hazard-lance") { w, h -> fight(w, h, Fights[2], hazardProgress = 0.85f) })
        add(scene("dungeon-hazard-lance-strike") { w, h -> fight(w, h, Fights[2], hazardProgress = 1.12f) })
        add(scene("dungeon-guardian", fx = { model -> moment(model, Guardian, DungeonMomentKind.GUARDIAN, 0.35f) }) { w, h ->
            fight(w, h, Guardian, guardian = true, hazards = false)
        })
        add(scene("dungeon-guardian-hud", hud = true) { w, h -> fight(w, h, Guardian, guardian = true) })
        add(scene("dungeon-seal-slam", fx = { model -> moment(model, Fights[0], DungeonMomentKind.SEALED, 0.12f) }) { w, h ->
            fight(w, h, Fights[0], sealedFor = 0.12f, hazards = false)
        })
        add(scene("dungeon-clear", fx = { model -> moment(model, Fights[5], DungeonMomentKind.ROOM_CLEARED, 0.4f) }) { w, h ->
            fight(w, h, Fights[5], cleared = true)
        })
        add(scene("dungeon-finale", fx = { model -> moment(model, Fights[4], DungeonMomentKind.STRUCTURE_CLEARED, 0.3f) }) { w, h ->
            fight(w, h, Fights[4], cleared = true)
        })
        add(scene("dungeon-threshold", hud = true) { w, h -> threshold(w, h) })
    }

    private fun scene(
        name: String,
        roles: KkRolePalette = KkRolePalette.Default,
        hud: Boolean = false,
        fx: (GameplayRenderModel) -> VisualFxProjection = { VisualFxProjection.EMPTY },
        model: (Float, Float) -> GameplayRenderModel,
    ) = RedesignShotsWorldTest.Scene(name, roles = roles, model = model, fx = fx, hud = hud)

    /** A template's fight: its room, the cleared rooms before it, its members (kind and template-space point) and hazard. */
    private class Fight(
        val template: StructureTemplateId,
        val room: Int,
        val cleared: Int,
        val members: List<Triple<EnemyType, Float, Float>>,
        val rotation: Int = 0,
    )

    /** The fight's structure placed with the sealed room's centre at the view's centre. */
    private fun fixture(fight: Fight, engaged: Boolean): StructureFixture {
        val template = Content.structures.template(fight.template)
        val spec = template.rooms[fight.room]
        val local = StructureFixture(template, 0f, 0f, fight.rotation).roomCenter(fight.room)
        val cleared = if (engaged) fight.cleared else fight.cleared or (1 shl fight.room)
        return StructureFixture(template, CENTRE - local.first, CENTRE - local.second, fight.rotation, cleared, if (engaged) fight.room else -1)
            .also { require(spec.garrison.isNotEmpty()) }
    }

    private fun fight(
        w: Float,
        h: Float,
        fight: Fight,
        hazards: Boolean = true,
        hazardProgress: Float = 0.7f,
        breaches: Boolean = false,
        guardian: Boolean = false,
        sealedFor: Float = 9f,
        cleared: Boolean = false,
    ): GameplayRenderModel {
        val structure = fixture(fight, engaged = !cleared)
        val members = fight.members.mapIndexed { index, (type, x, y) ->
            val (wx, wy) = structure.at(x, y)
            enemy(GUARDIAN_ID + index, type, wx, wy, if (guardian && index == 0) 0.55f else 0.9f)
        }
        val projection = structureProjection(structure, dungeon = structure)
        val telegraph = Content.structures.hazards.telegraphSeconds
        val spec = structure.template.rooms[fight.room]
        val hazardList = if (!hazards || cleared) emptyList() else when (structure.template.hazard) {
            DungeonHazardKind.VENT -> listOf(CENTRE + 40f to CENTRE + 30f, structure.at(spec.minX + 170f, spec.minY + 190f)).map { (x, y) ->
                DungeonHazardProjection(DungeonHazardKind.VENT, x, y, x, y, Content.structures.hazards.ventRadius, ELAPSED - telegraph * hazardProgress,
                    ELAPSED - telegraph * hazardProgress + telegraph)
            }
            else -> {
                val (ax, ay) = structure.at(spec.minX + 24f, (spec.minY + spec.maxY) * 0.5f + 60f)
                val (bx, by) = structure.at(spec.maxX - 24f, (spec.minY + spec.maxY) * 0.5f + 60f)
                listOf(DungeonHazardProjection(DungeonHazardKind.LANCE, ax, ay, bx, by, Content.structures.hazards.lanceHalfWidth,
                    ELAPSED - telegraph * hazardProgress, ELAPSED - telegraph * hazardProgress + telegraph))
            }
        }
        val breachList = if (!breaches) emptyList() else listOf(-260f to -180f, 250f to -200f, 230f to 210f, -250f to 190f).mapIndexed { index, (dx, dy) ->
            DungeonBreachProjection(CENTRE + dx, CENTRE + dy, 18f, guardian = index == 3)
        }
        val dungeon = checkNotNull(projection.dungeon).let { held ->
            if (cleared) held else held.copy(
                sealedAt = ELAPSED - sealedFor, waves = 3, wavesLanded = if (guardian) 3 else 1, liveGarrison = members.size,
                guardianId = if (guardian) GUARDIAN_ID else -1, incomingAt = if (breaches) ELAPSED + 0.45f else -1f,
                breaches = breachList.toImmutableList(), hazards = hazardList.toImmutableList(),
            )
        }
        return base(w, h).with(
            "structures" to projection.copy(dungeon = dungeon),
            "enemies" to (if (cleared) emptyList() else members).toImmutableList(),
            "trial" to structureTrial(structure),
        )
    }

    /** A Vault's entrance ahead of the Core with no fight begun: the threshold's light and brackets, under the HUD. */
    private fun threshold(w: Float, h: Float): GameplayRenderModel {
        val vault = StructureFixture(Content.structures.template(StructureTemplateId.VAULT), CENTRE + 150f, CENTRE - 1_450f)
        return base(w, h).with("structures" to structureProjection(vault, signals = listOf(vault)))
    }

    /** One [kind] moment of [fight]'s room (centred in view), [progress] through its life, focused on its guardian. */
    private fun moment(model: GameplayRenderModel, fight: Fight, kind: DungeonMomentKind, progress: Float): VisualFxProjection {
        val room = Content.structures.template(fight.template).rooms[fight.room]
        val turned = fight.rotation and 1 == 1
        val halfX = (room.maxX - room.minX) * 0.5f
        val halfY = (room.maxY - room.minY) * 0.5f
        val life = dungeonMomentSeconds(kind)
        val guardian = model.enemies.firstOrNull { it.id == GUARDIAN_ID }
        return VisualFxProjection.EMPTY.copy(dungeonMoments = immutableListOf(DungeonMomentProjection(
            kind, fight.template, CENTRE, CENTRE, if (turned) halfY else halfX, if (turned) halfX else halfY,
            guardian?.x ?: CENTRE, guardian?.y ?: CENTRE, life * (1f - progress), life, 7,
        )))
    }

    private fun base(w: Float, h: Float): GameplayRenderModel = hudTestModel(w, h).with(
        "coreX" to CENTRE - 60f, "coreY" to CENTRE + 40f, "previousCoreX" to CENTRE - 60f, "previousCoreY" to CENTRE + 40f,
        "cameraX" to CENTRE, "cameraY" to CENTRE, "previousCameraX" to CENTRE, "previousCameraY" to CENTRE,
        "velocityX" to 280f, "velocityY" to -120f, "pointerX" to w * 0.5f + 200f, "pointerY" to h * 0.5f - 120f,
        "elapsed" to ELAPSED, "obstacles" to immutableListOf<ObstacleProjection>(), "pickups" to immutableListOf<PickupProjection>(),
        "trail" to (0 until 18).map { TrailPointProjection(CENTRE - 60f - it * 16f, CENTRE + 40f + it * 7f, it * 0.08f) }.toImmutableList(),
        "sites" to immutableListOf<SiteProjection>(), "trial" to null,
    )

    private fun enemy(id: Int, type: EnemyType, x: Float, y: Float, hpShare: Float) =
        EnemyProjection(id, type, x, y, 0f, 0f, 100f * hpShare, 100f, Radii.getValue(type), 1f, 0f, 0f, 0f, x, y, false,
            affinityBits = runAffinity(type))

    private fun write(bitmap: ImageBitmap, file: File) {
        if (!fullShots) return
        val map = bitmap.toPixelMap()
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, map[x, y].toArgb())
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private companion object {
        val Content = hudTestModel().content
        const val CENTRE = 1_000f
        const val ELAPSED = 40f
        const val GUARDIAN_ID = 500

        /** The fixture kinds' run radii (the enemy profiles'). */
        val Radii = mapOf(
            EnemyType.SHOOTER to 20f, EnemyType.LANCER to 22f, EnemyType.SENTRY to 20f, EnemyType.CRAWLER to 12f, EnemyType.WEAVER to 21f,
            EnemyType.SNIPER to 19f, EnemyType.CHOIR to 25f, EnemyType.DRIFTER to 17f, EnemyType.MINELAYER to 24f, EnemyType.BOMBER to 16f,
            EnemyType.AEGIS to 26f, EnemyType.WARDEN to 29f, EnemyType.CHARGER to 23f,
        )

        /** Every template's fight (its second room unless it has passages), members in template space around its centre. */
        val Fights = listOf(
            Fight(StructureTemplateId.DERELICT_STATION, 1, 0b0001, listOf(
                Triple(EnemyType.SHOOTER, -200f, -300f), Triple(EnemyType.LANCER, 150f, 260f), Triple(EnemyType.SENTRY, -420f, -650f),
                Triple(EnemyType.CRAWLER, 240f, -180f))),
            Fight(StructureTemplateId.VAULT, 1, 0b0001, listOf(
                Triple(EnemyType.WEAVER, -900f, -400f), Triple(EnemyType.SNIPER, -600f, 200f), Triple(EnemyType.SENTRY, -1_000f, 700f))),
            Fight(StructureTemplateId.SPIRE, 1, 0b0001, listOf(
                Triple(EnemyType.CHOIR, -380f, 120f), Triple(EnemyType.CHOIR, 380f, 120f), Triple(EnemyType.DRIFTER, 100f, 420f))),
            Fight(StructureTemplateId.LABYRINTH, 4, 1 shl 6 or (1 shl 7), listOf(
                Triple(EnemyType.CRAWLER, -180f, -150f), Triple(EnemyType.CRAWLER, 160f, -200f), Triple(EnemyType.MINELAYER, 150f, 160f))),
            Fight(StructureTemplateId.FOUNDRY, 1, 0b0001, listOf(
                Triple(EnemyType.SENTRY, 100f, -650f), Triple(EnemyType.BOMBER, 420f, -200f), Triple(EnemyType.DRIFTER, 200f, 380f))),
            Fight(StructureTemplateId.RELIQUARY, 0, 1 shl 7, listOf(
                Triple(EnemyType.AEGIS, -950f, -950f), Triple(EnemyType.WARDEN, -650f, -700f), Triple(EnemyType.SENTRY, -1_100f, -500f))),
        )

        /** The Foundry's furnace fighting its Charger guardian and two escorts. */
        val Guardian = Fight(StructureTemplateId.FOUNDRY, 2, 0b0011, listOf(
            Triple(EnemyType.CHARGER, 1_000f, -120f), Triple(EnemyType.WARDEN, 820f, 250f), Triple(EnemyType.BOMBER, 1_080f, 220f)))
    }
}
