// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.characterization

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.interaction.RewardShot
import kinetickk.ball.gameplay.interaction.TerminalShot
import kinetickk.ball.gameplay.interaction.WorldShot
import kinetickk.ball.gameplay.interaction.canvas.HudTestFonts
import kinetickk.ball.gameplay.interaction.canvas.RedesignShotsBuildTest
import kinetickk.ball.gameplay.interaction.canvas.RedesignShotsFeedTest
import kinetickk.ball.gameplay.interaction.canvas.RedesignShotsHudTest
import kinetickk.ball.gameplay.interaction.canvas.RedesignShotsWorldTest
import kinetickk.ball.gameplay.interaction.canvas.drawBackdrop
import kinetickk.ball.gameplay.interaction.canvas.drawGameplay
import kinetickk.ball.gameplay.interaction.canvas.drawHud
import kinetickk.ball.gameplay.interaction.canvas.drawScreenFx
import kinetickk.ball.gameplay.interaction.canvas.drawWorld
import kinetickk.ball.gameplay.interaction.canvas.HudPresentationMemory
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.runningBuildButtonBounds
import kinetickk.ball.gameplay.interaction.rewards.itemChoice
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureModel
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.localeList
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Characterizes rendered gameplay pixels through the redesign shot harnesses: world, HUD, pause and build, feed, the
 * reward overlay and the terminal report, at 1440×810 in English and Russian with each harness's fixed render time.
 * Each scene draws twice and the SHA-256 of the second frame's pixels is printed.
 */
@OptIn(ExperimentalTestApi::class)
class RenderPixelCharacterizationTest {
    @Test
    fun worldScenesHashStably() {
        val scenes = RedesignShotsWorldTest().scenes().associateBy { it.name }
        for (name in WORLD_SCENES) {
            val scene = checkNotNull(scenes[name]) { "The world harness has no scene $name" }
            for ((language, code) in PIXEL_LANGUAGES) {
                val measurer = CanvasTextMeasurer(
                    TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
                    scene.textScale, language, RedesignShotsWorldTest.Fonts.typography, scene.roles,
                )
                val model = scene.model(PIXEL_WIDTH.toFloat(), PIXEL_HEIGHT.toFloat())
                val fx = scene.fx(model)
                printCanvasHash("world-$name", code) {
                    drawRect(Kk.Ink)
                    drawBackdrop(model, 0f, 0f, scene.time, scene.roles)
                    drawWorld(model, fx, 0f, 0f, measurer)
                    drawScreenFx(model, scene.time, scene.roles)
                    if (scene.hud) drawHud(model, measurer, scene.time)
                }
            }
        }
    }

    @Test
    fun hudScenesHashStably() {
        val harness = RedesignShotsHudTest()
        val base = harness.baseModel(PIXEL_WIDTH, PIXEL_HEIGHT)
        for (shot in HUD_SHOTS) for ((language, code) in PIXEL_LANGUAGES) {
            val measurer = CanvasTextMeasurer(
                TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                1.25f, language, harness.typography.copy(localeList = language.localeList()), KkRolePalette.Default,
            )
            val scene = harness.scene(shot, base, PIXEL_WIDTH, PIXEL_HEIGHT)
            printCanvasHash("hud-${shot.name.lowercase()}", code) {
                drawGameplay(scene.model, scene.fx, measurer, scene.time, null, hudMemory = scene.memory,
                    trialInfoOpen = scene.focused, controls = scene.controls)
                outlineBuildButton()
            }
        }
    }

    @Test
    fun feedScenesHashStably() {
        val harness = RedesignShotsFeedTest()
        val base = harness.baseModel(PIXEL_WIDTH, PIXEL_HEIGHT)
        for (shot in FEED_SHOTS) for ((language, code) in PIXEL_LANGUAGES) {
            val measurer = CanvasTextMeasurer(
                TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                1.25f, language, harness.typography.copy(localeList = language.localeList()), KkRolePalette.Default,
            )
            val scene = harness.scene(shot, base, PIXEL_WIDTH, PIXEL_HEIGHT)
            printCanvasHash("feed-${shot.name.lowercase()}", code) {
                drawGameplay(scene.model, scene.fx, measurer, scene.time, null, hudMemory = scene.memory, trialInfoOpen = scene.focused)
                outlineBuildButton()
            }
        }
    }

    @Test
    fun pauseAndBuildScenesHashStably() {
        val harness = RedesignShotsBuildTest()
        for ((language, code) in PIXEL_LANGUAGES) {
            val measurer = CanvasTextMeasurer(
                TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                1.25f, language, HudTestFonts.typography.copy(localeList = language.localeList()), KkRolePalette.Default,
            )
            printCanvasHash("build-world-status", code) {
                val world = harness.statusWorld(PIXEL_WIDTH, PIXEL_HEIGHT)
                drawGameplay(world, harness.statusFx(PIXEL_WIDTH, PIXEL_HEIGHT), measurer, 1.3f, null)
            }
            printCanvasHash("build-world-crowd", code) {
                drawGameplay(harness.crowdWorld(PIXEL_WIDTH, PIXEL_HEIGHT), VisualFxProjection.EMPTY, measurer, 1.3f, null)
            }
            val memory = HudPresentationMemory()
            val hudBuild = harness.hudBuild(PIXEL_WIDTH, PIXEL_HEIGHT)
            printCanvasHash("build-hud-build", code) {
                drawGameplay(hudBuild, harness.hudFx(), measurer, 3.37f, null, hudMemory = memory)
            }
            val pause = harness.pauseBuild(PIXEL_WIDTH, PIXEL_HEIGHT, language, 1.25f)
            printCanvasHash("build-pause-build", code) {
                drawGameplay(pause, VisualFxProjection.EMPTY, measurer, 1.3f, pauseLayoutGeometry(size.width, size.height, 1f))
            }
        }
    }

    @Test
    fun rewardTerminalAndPauseOverlaysHashStably() {
        val relics3 = listOf(
            EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1), EquippedRelic(RelicId.FURNACE_COVENANT, 1),
        )
        val relics4 = relics3 + EquippedRelic(RelicId.AEGIS_INVERSION, 1)
        for ((language, code) in PIXEL_LANGUAGES) {
            val w = PIXEL_WIDTH.toFloat()
            val h = PIXEL_HEIGHT.toFloat()
            val scenes = listOf<Pair<String, @Composable () -> Unit>>(
                "overlay-reward-levelup" to {
                    RewardShot(rewardFixtureModel(choices = listOf(itemChoice(0), itemChoice(1), itemChoice(2)), language = language,
                        width = w, height = h, textScale = 1.25f))
                },
                "overlay-pause" to {
                    WorldShot(rewardFixtureModel(phase = GamePhase.PAUSED, relics = relics3, language = language, width = w, height = h,
                        textScale = 1.25f))
                },
                "overlay-report-victory" to {
                    TerminalShot(rewardFixtureModel(phase = GamePhase.VICTORY, relics = relics4, language = language, width = w,
                        height = h,
                        textScale = 1.25f, message = "ARCHITECT DISMANTLED", runMatter = 640L, totalMatter = 1_924L, kills = 1_412,
                        elapsed = 846f, level = 22, weaponLevel = 10))
                },
                "overlay-report-defeat" to {
                    TerminalShot(rewardFixtureModel(phase = GamePhase.GAME_OVER, relics = relics4, language = language, width = w,
                        height = h,
                        textScale = 1.25f, message = "SINGULARITY CONTACT", runMatter = 640L, totalMatter = 1_284L, kills = 901,
                        elapsed = 554f, level = 16, weaponLevel = 8))
                },
            )
            runDesktopComposeUiTest(PIXEL_WIDTH, PIXEL_HEIGHT, effectContext = object : MotionDurationScale {
                override val scaleFactor = 0f
            }) {
                val current = mutableStateOf<(@Composable () -> Unit)?>(null)
                setContent {
                    val content = current.value ?: return@setContent
                    CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize(PIXEL_WIDTH.dp, PIXEL_HEIGHT.dp).testTag("shot")) { content() }
                    }
                }
                for ((name, content) in scenes) {
                    // The second composition of the scene is the one hashed, after the first warmed every cache.
                    current.value = content
                    waitForIdle()
                    onNodeWithTag("shot").captureToImage()
                    current.value = null
                    waitForIdle()
                    current.value = content
                    waitForIdle()
                    printPixelHash(name, code, onNodeWithTag("shot").captureToImage())
                }
            }
        }
    }
}

private const val PIXEL_WIDTH = 1440
private const val PIXEL_HEIGHT = 810

private val PIXEL_LANGUAGES = listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")

private val WORLD_SCENES = listOf(
    "cruise", "roster", "roster-worlds", "elite", "architect-hud", "impact", "overdrive", "numbers-edge", "ribbon-dash",
    "poi-offer-hud", "site-offers-mixed-hud", "site-race-hud", "miniboss-throne", "signal-merchant", "structure-exterior",
    "dungeon-sealed", "terrain-hud", "easy-cruise-hud",
)

private val HUD_SHOTS = listOf(
    RedesignShotsHudTest.Shot.CRUISE, RedesignShotsHudTest.Shot.CRITICAL, RedesignShotsHudTest.Shot.ELITE,
    RedesignShotsHudTest.Shot.ARCHITECT, RedesignShotsHudTest.Shot.TRIAL, RedesignShotsHudTest.Shot.FEED,
    RedesignShotsHudTest.Shot.SYNERGY, RedesignShotsHudTest.Shot.REBIRTH_10, RedesignShotsHudTest.Shot.APEX_READY,
    RedesignShotsHudTest.Shot.SKILLS_FULL, RedesignShotsHudTest.Shot.TRIAL_RACE, RedesignShotsHudTest.Shot.EASY_TIER,
    RedesignShotsHudTest.Shot.MINIBOSS, RedesignShotsHudTest.Shot.DUNGEON, RedesignShotsHudTest.Shot.DIMENSION_NADIR,
)

private val FEED_SHOTS = listOf(
    RedesignShotsFeedTest.Shot.FEED, RedesignShotsFeedTest.Shot.FEED_LEVEL, RedesignShotsFeedTest.Shot.FEED_REPLACE,
    RedesignShotsFeedTest.Shot.FEED_SYNERGY4_WIDE,
)

/** The HUD harnesses' outline of the Compose Build button beside pause. */
private fun DrawScope.outlineBuildButton() {
    val build = runningBuildButtonBounds(size.width, size.height, 1f)
    drawRect(Kk.Ink3, build.topLeft + Offset(4f, 6f), Size(build.width - 8f, build.height - 12f))
}

/** Draws [draw] twice on fresh bitmaps and prints the SHA-256 of the second frame. */
private fun printCanvasHash(scene: String, language: String, draw: DrawScope.() -> Unit) {
    fun drawFrame(): ImageBitmap = ImageBitmap(PIXEL_WIDTH, PIXEL_HEIGHT).also { frame ->
        val size = Size(PIXEL_WIDTH.toFloat(), PIXEL_HEIGHT.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(frame), size) { draw() }
    }
    drawFrame()
    printPixelHash(scene, language, drawFrame())
}

private fun printPixelHash(scene: String, language: String, bitmap: ImageBitmap) {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    val bytes = ByteBuffer.allocate(pixels.size * 4)
    bytes.asIntBuffer().put(pixels)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    println("scene=$scene lang=$language sha256=$digest")
}
