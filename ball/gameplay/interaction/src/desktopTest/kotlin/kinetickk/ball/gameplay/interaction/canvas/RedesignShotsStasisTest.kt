// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.interaction.canvas.hud.HudPresentationMemory
import kinetickk.ball.gameplay.interaction.drawGameplay
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.CoreStasisMotion
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.localeList
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Local visual QA harness for Core Freeze: the frost, ring meter, stamp and screen frost at a quarter, 0.6 and 0.95 of
 * a freeze, a thaw, a hold and Easy mode, and the death frame of a Core frozen solid (encased, then bursting). With
 * `KK_SHOTS=1` every scene draws at three sizes in both languages (the urgent and death frames also in Mono) into
 * build/redesign-shots/stasis; otherwise each scene draws once and nothing is written.
 */
class RedesignShotsStasisTest {
    private val fullShots = System.getenv("KK_SHOTS") == "1"

    private fun <T> List<T>.matrix(): List<T> = if (fullShots) this else take(1)

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    private val output = File(root, "ball/gameplay/interaction/build/redesign-shots/stasis")

    @Test
    fun renderCoreFreezeStates() {
        if (fullShots) output.mkdirs()
        val only = System.getenv("KK_SHOTS_ONLY")?.split(',')
        for ((width, height) in listOf(1_440 to 810, 844 to 390, 390 to 844).matrix()) {
            val base = hudTestModel(width.toFloat(), height.toFloat())
            for (language in AppLanguage.entries.matrix()) for ((suffix, roles) in listOf("" to KkRolePalette.Default, "-mono" to KkRolePalette.Mono).matrix()) {
                val measurer = CanvasTextMeasurer(
                    TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                    1.25f, language, HudTestFonts.typography.copy(localeList = language.localeList()), roles,
                )
                for (shot in Shot.entries) {
                    if (only != null && shot.label !in only) continue
                    if (roles != KkRolePalette.Default && !shot.palettes) continue
                    val model = shot.model(base)
                    val bitmap = ImageBitmap(width, height)
                    val memory = HudPresentationMemory()
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                        drawGameplay(model, VisualFxProjection.EMPTY, measurer, RENDER_TIME, null, terminalElapsed = shot.terminalElapsed,
                            hudMemory = memory)
                    }
                    val lang = if (language == AppLanguage.English) "en" else "ru"
                    if (fullShots) write(bitmap, File(output, "stasis-${shot.label}-${width}x$height-$lang$suffix.png"))
                }
            }
        }
    }

    private enum class Shot(val label: String, val palettes: Boolean = false, val terminalElapsed: Float = 0f) {
        FREEZE_25("freeze-25"),
        FREEZE_60("freeze-60"),
        FREEZE_95("freeze-95", palettes = true),
        THAW_50("thaw-50"),
        HOLD_40("hold-40"),
        EASY_95("easy-95"),
        DEATH_ENCASED("death-encased", palettes = true, terminalElapsed = 0.25f),
        DEATH_BURST("death-burst", palettes = true, terminalElapsed = 0.62f),
        ;

        fun model(base: GameplayRenderModel): GameplayRenderModel = when (this) {
            FREEZE_25 -> base.with("coreStasis" to 0.25f, "coreStasisMotion" to CoreStasisMotion.FREEZING)
            FREEZE_60 -> base.with("coreStasis" to 0.6f, "coreStasisMotion" to CoreStasisMotion.FREEZING)
            FREEZE_95 -> base.with("coreStasis" to 0.95f, "coreStasisMotion" to CoreStasisMotion.FREEZING)
            THAW_50 -> base.with("coreStasis" to 0.5f, "coreStasisMotion" to CoreStasisMotion.THAWING)
            HOLD_40 -> base.with("coreStasis" to 0.4f, "coreStasisMotion" to CoreStasisMotion.HOLDING)
            EASY_95 -> base.with("coreStasis" to 0.95f, "coreStasisMotion" to CoreStasisMotion.FREEZING, "easyMode" to true)
            DEATH_ENCASED, DEATH_BURST -> base.with("phase" to GamePhase.GAME_OVER, "coreStasis" to 1f,
                "coreStasisMotion" to CoreStasisMotion.FROZEN, "message" to "CORE FROZEN")
        }
    }

    private fun write(bitmap: ImageBitmap, file: File) {
        val pixels = bitmap.toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        ImageIO.write(image, "png", file)
    }

    private companion object {
        /** A render time at which every pulse sits at a readable phase. */
        const val RENDER_TIME = 10.3f
    }
}
