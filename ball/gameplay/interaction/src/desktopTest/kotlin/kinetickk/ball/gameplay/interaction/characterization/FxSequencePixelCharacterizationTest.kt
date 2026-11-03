// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.characterization

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.canvas.FxTestScenes
import kinetickk.ball.gameplay.interaction.canvas.HudTestFonts
import kinetickk.ball.gameplay.interaction.canvas.drawApexBursts
import kinetickk.ball.gameplay.interaction.canvas.drawElementBursts
import kinetickk.ball.gameplay.interaction.canvas.drawGameplay
import kinetickk.ball.gameplay.interaction.canvas.drawSkillBursts
import kinetickk.ball.gameplay.interaction.fx.InteractionFxReducer
import kinetickk.ball.gameplay.interaction.fx.SkillFxSpecs
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.localeList
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Characterizes FX pixels: the reaction, Break, skill and Apex looks laid out on one 1440×810 frame through
 * [FxTestScenes], and a twelve-frame sequence that drives [InteractionFxReducer] with a fixed cue script and draws each
 * frame's projection in a full gameplay frame (English and Russian). Each frame draws twice and the SHA-256 of the
 * second frame's pixels is printed.
 */
class FxSequencePixelCharacterizationTest {
    @Test
    fun fxLooksHashStably() {
        val model = FxTestScenes.model(FX_WIDTH, FX_HEIGHT)
        val roles = KkRolePalette.Default
        val reactions = ReactionId.entries.mapIndexed { index, reaction ->
            FxTestScenes.reaction(reaction, cellX(index), cellY(index), FX_REACH, 0.3f)
        }
        printFxSheetHash("fx-reactions") { drawElementBursts(model, FxTestScenes.fx(reactions), 0f, 0f, roles) }
        val breaks = StatusElement.entries.indices.map { element -> FxTestScenes.breakBurst(element, cellX(element), cellY(element), 0.3f) }
        printFxSheetHash("fx-breaks") { drawElementBursts(model, FxTestScenes.fx(breaks), 0f, 0f, roles) }
        val looks = SkillId.entries.flatMap { skill ->
            (0 until SkillFxSpecs.PHASES).mapNotNull { phase -> SkillFxSpecs.of(skill, phase)?.let { skill to phase } }
        }
        val skills = looks.mapIndexed { index, (skill, phase) ->
            FxTestScenes.skill(skill, phase, cellX(index), cellY(index), FX_REACH, 0.5f, flags = 1)
        }
        printFxSheetHash("fx-skills") { drawSkillBursts(model, FxTestScenes.fx(skills = skills), 0f, 0f, roles) }
        val apex = CoreShape.entries.indices.map { shape -> FxTestScenes.apex(shape, cellX(shape), cellY(shape), 0.3f) }
        printFxSheetHash("fx-apex") { drawApexBursts(model, FxTestScenes.fx(apex = apex), 0f, 0f, roles) }
    }

    @Test
    fun fxReducerSequenceHashesStably() {
        val model = FxTestScenes.model(FX_WIDTH, FX_HEIGHT)
        for ((language, code) in listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")) {
            val measurer = CanvasTextMeasurer(
                TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                1.25f, language, HudTestFonts.typography.copy(localeList = language.localeList()), KkRolePalette.Default,
            )
            val reducer = InteractionFxReducer(FX_SEQUENCE_SEED)
            for (frame in 0 until FX_SEQUENCE_FRAMES) {
                reducer.apply(sequenceCues(frame))
                val fx = reducer.snapshot()
                fun drawFrame(): ImageBitmap = ImageBitmap(FX_WIDTH, FX_HEIGHT).also { bitmap ->
                    val size = Size(FX_WIDTH.toFloat(), FX_HEIGHT.toFloat())
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
                        drawGameplay(model, fx, measurer, FX_RENDER_TIME, null)
                    }
                }
                drawFrame()
                printFxPixelHash("fx-sequence-f${frame.toString().padStart(2, '0')}", code, drawFrame())
            }
        }
    }
}

private const val FX_WIDTH = 1440
private const val FX_HEIGHT = 810
private const val FX_REACH = 60f
private const val FX_COLUMNS = 10
private const val FX_SEQUENCE_SEED = 0x4B1D
private const val FX_SEQUENCE_FRAMES = 12
private const val FX_RENDER_TIME = 3f
private const val FX_STEP = 1f / 60f

private fun cellX(index: Int): Float = 72f + (index % FX_COLUMNS) * 144f

private fun cellY(index: Int): Float = 68f + (index / FX_COLUMNS) * 135f

/** The fixed cue script of [frame]: a motion sample and effect advance every frame, with new effects on even frames. */
private fun sequenceCues(frame: Int): List<VisualFxCue> = buildList {
    add(VisualFxCue.MotionSample(FX_STEP, 714f, 405f, 360f, 0f))
    when (frame) {
        0 -> {
            add(VisualFxCue.Burst(560f, 300f, 24, 1, ParticleDensity.NORMAL))
            add(VisualFxCue.ShockwaveAdded(720f, 405f, 0.6f, 180f, 0))
            add(VisualFxCue.DamageNumberAdded(820f, 330f, 1_234L, false))
            add(VisualFxCue.BuildChanged("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04")))
        }
        2 -> {
            add(VisualFxCue.DirectionalBurst(900f, 500f, 18, FxColorCode.elementTone(StatusElement.CRYO), 1f, 0f, ParticleDensity.HIGH))
            add(VisualFxCue.WeaponArcAdded(720f, 405f, 980f, 260f, 0.35f))
            add(VisualFxCue.DamageNumberAdded(980f, 260f, 98_765L, true, tone = 2))
        }
        4 -> {
            add(VisualFxCue.StatusBroken(600f, 520f, 1))
            add(VisualFxCue.ReactionTriggered(840f, 560f, 0, 90f, firstDiscovery = true))
        }
        6 -> {
            add(VisualFxCue.SkillCast(SkillId.entries.first(), 720f, 405f, 1f, 0f, 120f, 2))
            add(VisualFxCue.ShockwaveAdded(500f, 600f, 0.5f, 140f, FxColorCode.quake(StatusElement.THERMAL)))
        }
        8 -> {
            add(VisualFxCue.ApexActivated(CoreShape.ORB, 720f, 405f))
            add(VisualFxCue.Burst(300f, 200f, 40, FxColorCode.APEX_GOLD, ParticleDensity.HIGH))
        }
        10 -> {
            add(VisualFxCue.WorldRebased(12f, -8f))
            add(VisualFxCue.DamageNumberAdded(700f, 300f, 5L, false))
        }
    }
    add(VisualFxCue.EffectsAdvanced(FX_STEP))
    add(VisualFxCue.WeaponArcsAdvanced(FX_STEP))
}

/** Draws a language-free FX look sheet twice through [FxTestScenes.render] and prints the second frame's SHA-256. */
private fun printFxSheetHash(scene: String, block: DrawScope.() -> Unit) {
    FxTestScenes.render(FX_WIDTH, FX_HEIGHT, block = block)
    printFxPixelHash(scene, "any", FxTestScenes.render(FX_WIDTH, FX_HEIGHT, block = block))
}

private fun printFxPixelHash(scene: String, language: String, bitmap: ImageBitmap) {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    val bytes = ByteBuffer.allocate(pixels.size * 4)
    bytes.asIntBuffer().put(pixels)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    println("scene=$scene lang=$language sha256=$digest")
}
