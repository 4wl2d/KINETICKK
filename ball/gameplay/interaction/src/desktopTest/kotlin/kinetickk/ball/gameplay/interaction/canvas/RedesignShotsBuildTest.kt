// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.BuildNoticeKind
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.DamageNumberProjection
import kinetickk.ball.gameplay.interaction.fx.ElementBurstProjection
import kinetickk.ball.gameplay.interaction.fx.ReactionStampProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureModel
import kinetickk.ball.gameplay.interaction.terminal.TerminalContent
import kinetickk.ball.gameplay.nucleus.render.BuildProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SeveredRelicProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponSynergyProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.localeList
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/**
 * Local visual QA of the build system's in-run surfaces: enemy statuses, Break and reaction flashes, the
 * crowded level of detail, the HUD relic row with gauges, the boss status rail and discovery toasts, the
 * pause build view and the report recap. With `KK_SHOTS=1` every surface draws at every size and in both
 * languages (plus Protan and the largest pause text size) and is written to `KK_SHOTS_OUT` (default
 * `build/redesign-shots/build`). Otherwise the run is a smoke: each surface draws once (1440 × 810, English,
 * Default palette, text size 125 %) and nothing is written, so a surface that fails to draw still fails.
 */
@OptIn(ExperimentalTestApi::class)
class RedesignShotsBuildTest {
    private val fullShots = System.getenv("KK_SHOTS") == "1"

    /** The whole list with `KK_SHOTS=1`, else its first entry. */
    private fun <T> List<T>.matrix(): List<T> = if (fullShots) this else take(1)
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    private val output = File(System.getenv("KK_SHOTS_OUT") ?: File(root, "ball/gameplay/interaction/build/redesign-shots/build").path)
    private val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val only = System.getenv("KK_SHOTS_ONLY")?.split(',')?.toSet()

    private fun wanted(name: String) = only == null || name in only

    @Test
    fun renderBuildSurfaces() {
        if (fullShots) output.mkdirs()
        for ((width, height) in sizes.matrix()) for (language in AppLanguage.entries.matrix()) {
            val lang = if (language == AppLanguage.English) "en" else "ru"
            for ((suffix, roles) in listOf("" to KkRolePalette.Default, "-protan" to KkRolePalette.Protan).matrix()) {
                if (roles != KkRolePalette.Default && (language != AppLanguage.English || width != 1440)) continue
                val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64), 1.25f,
                    language, HudTestFonts.typography.copy(localeList = language.localeList()), roles)
                if (wanted("world-status")) shot("world-status-${width}x$height-$lang$suffix", width, height) {
                    drawGameplay(statusWorld(width, height), statusFx(width, height), measurer, 1.3f, null)
                }
                if (wanted("world-crowd") && roles == KkRolePalette.Default) shot("world-crowd-${width}x$height-$lang", width, height) {
                    drawGameplay(crowdWorld(width, height), VisualFxProjection.EMPTY, measurer, 1.3f, null)
                }
                if (wanted("hud-build")) shot("hud-build-${width}x$height-$lang$suffix", width, height) {
                    val model = hudBuild(width, height)
                    val memory = HudPresentationMemory()
                    drawGameplay(model, hudFx(), measurer, 3.37f, null, hudMemory = memory)
                }
            }
            for (textScale in listOf(1.25f, 1.75f).matrix()) {
                if (!wanted("pause-build")) continue
                val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64), textScale,
                    language, HudTestFonts.typography.copy(localeList = language.localeList()))
                val tag = if (textScale == 1.25f) "" else "-t175"
                shot("pause-build$tag-${width}x$height-$lang", width, height) {
                    val model = pauseBuild(width, height, language, textScale)
                    drawGameplay(model, VisualFxProjection.EMPTY, measurer, 1.3f, pauseLayoutGeometry(size.width, size.height, 1f))
                }
            }
            if (wanted("report-recap")) report(width, height, language, lang)
        }
    }

    private fun shot(name: String, width: Int, height: Int, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) { draw() }
        write(bitmap, File(output, "$name.png"))
    }

    private fun report(width: Int, height: Int, language: AppLanguage, lang: String) = runDesktopComposeUiTest(width, height) {
        val model = pauseBuild(width, height, language, 1.25f).with("phase" to GamePhase.VICTORY, "message" to "ARCHITECT DISMANTLED")
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag("shot")) {
                    TerminalContent(model, 4f, true, reactionsThisRun = (1 shl ReactionId.THERMAL_SHOCK.ordinal) or
                        (1 shl ReactionId.WILDFIRE.ordinal) or (1 shl ReactionId.FEEDBACK.ordinal),
                        discoveredThisRun = 1 shl ReactionId.FEEDBACK.ordinal) {}
                }
            }
        }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        write(onNodeWithTag("shot").captureToImage(), File(output, "report-recap-${width}x$height-$lang.png"))
    }

    /** Enemies around the Core showing every status mark, and the build that set their thresholds. */
    private fun statusWorld(width: Int, height: Int): GameplayRenderModel {
        val base = hudTestModel(width.toFloat(), height.toFloat())
        val statuses = listOf(
            EnemyType.DRIFTER to (3 shl 0),
            EnemyType.SHOOTER to ((7 shl 0) or STATUS_PRIMED),
            EnemyType.CHARGER to ((4 shl 4) or (3 shl 0)),
            EnemyType.INTERCEPTOR to ((2 shl 4) or STATUS_FROZEN or STATUS_BRITTLE),
            EnemyType.WEAVER to ((6 shl 8) or STATUS_PRIMED or (2 shl 12)),
            EnemyType.WARDEN to ((5 shl 12)),
            EnemyType.SPLITTER to ((8 shl 16) or STATUS_EMBRITTLED or (2 shl 4)),
            EnemyType.ELITE to ((4 shl 16) or (3 shl 8)),
            EnemyType.DRIFTER to (1 shl 12),
            EnemyType.SHOOTER to (9 shl 16),
        )
        val scale = minOf(width, height) / 810f
        val enemies = statuses.mapIndexed { index, (type, bits) ->
            val angle = index * 0.628f + 0.3f
            val distance = (if (index % 2 == 0) 250f else 330f) * scale.coerceAtLeast(0.6f)
            val radius = if (type == EnemyType.ELITE) 38f else if (type == EnemyType.WARDEN) 26f else 20f
            EnemyProjection(index + 1, type, cos(angle) * distance, sin(angle) * distance * 0.8f, 0f, 0f, 60f, 100f, radius, 1f, 0f, 0f, 0f,
                cos(angle) * distance, sin(angle) * distance * 0.8f, false, statusBits = bits)
        }
        return base.with(
            "enemies" to enemies.toImmutableList(),
            "elapsed" to 120.06f,
            "build" to BuildProjection(breakThresholds = immutableListOf(8, 6, 7, 6, 10), tagTiers = immutableListOf(0, 0, 3, 0, 0, 0, 0, 0, 0)),
        )
    }

    private fun statusFx(width: Int, height: Int): VisualFxProjection {
        val s = minOf(width, height) / 810f
        return VisualFxProjection.EMPTY.copy(
            elementBursts = ((0 until 5).map { element ->
                ElementBurstProjection(-300f * s + element * 150f * s, 300f * s, element, -1, -1, 80f, 0.28f, 0.5f, 1)
            } + listOf(
                ElementBurstProjection(-150f * s, -260f * s, 0, 1, ReactionId.THERMAL_SHOCK.ordinal, 90f, 0.42f, 0.7f, 2),
                ElementBurstProjection(170f * s, -250f * s, 2, 4, ReactionId.FEEDBACK.ordinal, 110f, 0.5f, 0.7f, 1),
            )).toImmutableList(),
            reactionStamps = immutableListOf(ReactionStampProjection(170f * s, -250f * s, ReactionId.FEEDBACK.ordinal, 0.6f, 0.9f)),
            damageNumbers = (0 until 6).map { tone ->
                DamageNumberProjection(-260f * s + tone * 100f * s, -120f * s, listOf(42L, 380L, 1_400L, 96L, 4_200L, 260L)[tone], tone == 4,
                    life = 0.4f, tone = tone)
            }.toImmutableList(),
        )
    }

    /** Ninety enemies on screen: ordinary ones drop their patterns for a flat tint. */
    private fun crowdWorld(width: Int, height: Int): GameplayRenderModel {
        val base = hudTestModel(width.toFloat(), height.toFloat())
        val enemies = (0 until 90).map { index ->
            val x = ((index % 15) - 7f) * width / 16f
            val y = ((index / 15) - 2.5f) * height / 7f
            val element = index % 5
            val type = if (index == 37) EnemyType.ELITE else EnemyType.entries[index % 7]
            EnemyProjection(index + 1, type, x, y, 0f, 0f, 60f, 100f, if (type == EnemyType.ELITE) 38f else 16f, 1f, 0f, 0f, 0f, x, y, false,
                statusBits = ((2 + index % 5) shl (element * 4)) or (if (index % 3 == 0) (2 shl (((element + 1) % 5) * 4)) else 0))
        }
        return base.with("enemies" to enemies.toImmutableList(), "elapsed" to 61f)
    }

    /** The HUD with four gauge relics, a lingering replaced relic, an elite carrying statuses and Burnout. */
    private fun hudBuild(width: Int, height: Int): GameplayRenderModel {
        val base = hudTestModel(width.toFloat(), height.toFloat())
        val sx = width / 1440f
        val sy = height / 810f
        return base.with(
            "equippedRelics" to listOf(EquippedRelic(RelicId.STORMHEART_CAPACITOR, 2), EquippedRelic(RelicId.STASIS_ANCHOR, 1),
                EquippedRelic(RelicId.GLASS_COMET, 1), EquippedRelic(RelicId.OUROBOROS_SEAL, 1)).toImmutableList(),
            "build" to BuildProjection(stormCharge = 0.65f, stasisMeter = 0.45f, stasisBank = 5, invulnerable = true, overdriveBurnout = true,
                ouroborosSpent = true, severed = immutableListOf(SeveredRelicProjection(RelicId.EVENT_HORIZON, 28f),
                    SeveredRelicProjection(RelicId.HOLLOW_CROWN, 12f)),
                breakThresholds = immutableListOf(8, 6, 7, 6, 10)),
            "enemies" to listOf(EnemyProjection(1, EnemyType.ELITE, 975f * sx - width * 0.5f, 325f * sy - height * 0.5f, 0f, 0f, 640f, 1000f,
                38f, 1f, 0f, 0f, 0f, 975f * sx - width * 0.5f, 325f * sy - height * 0.5f, false,
                statusBits = (7 shl 0) or (3 shl 8) or (2 shl 12) or STATUS_BRITTLE or STATUS_EMBRITTLED or STATUS_PRIMED)).toImmutableList(),
            "hp" to 142f, "maxHp" to 180f, "shield" to 40f, "maxShield" to 60f, "overdriveCharge" to 58f, "elapsed" to 462f,
            "combo" to 12, "comboTime" to 1.7f, "comboWindow" to 2.8f, "velocityX" to 412f,
        )
    }

    // The reducer's rank order, lowest first: an acquisition, the first Breaks, a discovery.
    private fun hudFx(): VisualFxProjection = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(
        BuildNotificationProjection("Cinder Ram", immutableListOf("Impact damage +0.05", "+ Ram II", "+ Thermal I"), 5.2f),
        BuildNotificationProjection("BREAK", immutableListOf("+ Ignite", "+ Freeze"), 3f, BuildNoticeKind.BREAK),
        BuildNotificationProjection("REACTION DISCOVERED", immutableListOf("+ Thermal Shock"), 5.8f, BuildNoticeKind.DISCOVERY),
    ))

    /** A paused run with a full build: brackets, tags, elements, reactions (one met), items and costs. */
    private fun pauseBuild(width: Int, height: Int, language: AppLanguage, textScale: Float): GameplayRenderModel =
        rewardFixtureModel(
            phase = GamePhase.PAUSED, language = language, width = width.toFloat(), height = height.toFloat(), textScale = textScale,
            relics = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1),
                EquippedRelic(RelicId.FURNACE_COVENANT, 1), EquippedRelic(RelicId.HOLLOW_CROWN, 1)),
            discoveredPairings = setOf(RelicPairingId.ENDLESS_GYRE),
            itemStacks = listOf(2, 1, 1, 3, 1),
            build = BuildProjection(
                tagPoints = immutableListOf(6, 4, 1, 2, 0, 0, 3, 1, 0),
                tagTiers = immutableListOf(2, 1, 0, 0, 0, 0, 1, 0, 0),
                affinity = immutableListOf(5, 2, 0, 1, 3),
                affinityTiers = immutableListOf(2, 1, 0, 0, 1),
                dominant = StatusElement.THERMAL,
                relicUpsides = RelicId.entries.map {
                    when (it) {
                        RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE -> 0.83f
                        RelicId.FURNACE_COVENANT -> 0.75f
                        RelicId.HOLLOW_CROWN -> 1f
                        else -> 1f
                    }
                }.toImmutableList(),
                severed = immutableListOf(SeveredRelicProjection(RelicId.EVENT_HORIZON, 30f)),
                suppressedItemIds = immutableListOf(4),
                rebornItemIds = immutableListOf(3),
                reachableReactions = immutableListOf(ReactionId.THERMAL_SHOCK, ReactionId.WILDFIRE, ReactionId.EMBRITTLE, ReactionId.FUSION),
            ),
        ).with("discoveredReactionMask" to ((1 shl ReactionId.WILDFIRE.ordinal) or (1 shl ReactionId.THERMAL_SHOCK.ordinal)), "maxShield" to 60f,
            "weaponSynergy" to WeaponSynergyProjection(tagPoints = 6, tagBonus = 0.3f, signatureActive = true,
                harmonizingRelic = RelicId.PERPETUAL_VECTOR, infusionElement = StatusElement.THERMAL, scalingMultiplier = 1.42f, masteryForms = 2,
                activeMasteryForm = "The trail lasts 4 s"))

    private fun write(bitmap: ImageBitmap, file: File) {
        if (!fullShots) return
        val pixels = bitmap.toPixelMap()
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private companion object {
        const val STATUS_FROZEN = EnemyStatusBits.FROZEN
        const val STATUS_BRITTLE = EnemyStatusBits.BRITTLE
        const val STATUS_EMBRITTLED = EnemyStatusBits.EMBRITTLED
        const val STATUS_PRIMED = EnemyStatusBits.PRIMED
    }
}
