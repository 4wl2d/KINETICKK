// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.rebirthNumberScale
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.canvas.HudTestFonts
import kinetickk.ball.gameplay.interaction.canvas.hud.HudBlock
import kinetickk.ball.gameplay.interaction.canvas.hud.HudLayoutProbe
import kinetickk.ball.gameplay.interaction.canvas.hud.HudPresentationMemory
import kinetickk.ball.gameplay.interaction.canvas.hud.HudScratch
import kinetickk.ball.gameplay.interaction.canvas.hud.drawHud
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.overlay.terminalPresentation
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.formatCompactNumber
import kinetickk.foundation.design.CanvasTextMeasurer
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Rebirth 10 run through the public Nucleus contract shows every absolute number at the tier's display scale
 * (power units × 3,600) in the compact form: the damage numbers the reducer formats, the HUD integrity and the run
 * report's damage totals. The simulation itself keeps power units, so the render model's integrity is unscaled.
 */
class RebirthNumberScaleRenderTest {
    @Test
    fun aTierTenRunShowsItsDamageIntegrityAndTotalsAtTheDisplayScale() {
        val run = ScaledRun(tier = 10).also { it.play(seconds = 12f) }
        val model = run.model
        assertEquals(3_600.0, model.numberScale)
        assertEquals(rebirthNumberScale(10), model.numberScale)
        assertTrue(model.hp <= model.maxHp && model.maxHp < 1_000f, "integrity stays in power units: ${model.maxHp}")

        // Every hit at or above the five-power-unit floor reads at least 18K, and its text is the shared compact form.
        assertTrue(run.damageNumbers.isNotEmpty(), "the run published no damage number")
        run.damageNumbers.forEach { number ->
            assertTrue(number.amount >= 5L * 3_600L - 1_800L, "a damage number below the scaled floor: ${number.amount}")
            assertEquals(formatCompactNumber(number.amount, AppLanguage.English), number.compactAmount)
            assertTrue(number.compactAmount.last() in "KMB", number.compactAmount)
            assertEquals(formatCompactNumber(number.amount, AppLanguage.Russian), number.formattedAmount(DamageNumberFormat.COMPACT, AppLanguage.Russian))
        }

        // The HUD draws the integrity number as compact text: hp × 3,600 (a few hundred thousand and more).
        drawHudOnce(model)
        val shown = formatCompactNumber((model.hp.toDouble() * model.numberScale).toLong(), AppLanguage.English)
        assertEquals(shown, HudScratch.integrity.current())
        assertTrue(HudLayoutProbe.rect(HudBlock.INTEGRITY) != null, "the integrity block was not drawn")

        // The run report's damage totals read the same scale.
        val report = model.terminalPresentation(AppLanguage.English)
        val dealt = report.combat.single { it.label == GameplayText.DamageDealt }.value
        assertEquals(formatCompactNumber((model.runStatistics.damageDealt * 3_600.0).roundToLong(), AppLanguage.English), dealt)
    }

    @Test
    fun aTierZeroRunKeepsItsNumbersAsTheSimulationComputesThem() {
        val run = ScaledRun(tier = 0).also { it.play(seconds = 8f) }
        assertEquals(1.0, run.model.numberScale)
        assertTrue(run.damageNumbers.isNotEmpty(), "the run published no damage number")
        assertTrue(run.damageNumbers.any { it.amount < 1_000L && it.compactAmount == it.amount.toString() })
    }

    private fun drawHudOnce(model: GameplayRenderModel) {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
            AppLanguage.English, HudTestFonts.typography)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(1_440, 810)), Size(1_440f, 810f)) {
            drawHud(model, measurer, model.elapsed, 0f, 0f, HudPresentationMemory())
        }
    }
}

/**
 * A started run at Rebirth [tier] (with the full Matrix above tier 0) that steers at the nearest enemy and records each damage number the reducer formats
 * (the newest projected number after each damage cue; a full number list drops later cues, and those record nothing new).
 */
private class ScaledRun(tier: Int) {
    private val content = createContentCatalog().gameplayContent()
    private val reducer = InteractionFxReducer(seed = 17)
    private var state: GameplayState
    val damageNumbers = ArrayList<DamageNumberProjection>()

    init {
        val profile = PlayerProfile(
            preferences = PlayerPreferences(language = AppLanguage.English),
            rebirthProgress = RebirthProgress(level = tier, highestCleared = tier - 1),
            // A player at a high tier owns the Matrix (a fresh profile there dies in the first second of the pack); the
            // keystones stay out, since their flight changes carry this straight-at-the-pack steering into the singularity.
            matrix = if (tier > 0) MatrixProgress(content.matrixNodes.map { if (it.keystone) 0 else it.maxRanks }) else MatrixProgress(),
        )
        state = (GameplayNucleus.decide(
            GameplayState.initial(RunId(3), content),
            GameplayNucleusPulse.StartRun,
            GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(content,
                GameplayProfileSnapshot(profile.preferences, profile.economy, profile.loadout, profile.matrix, profile.collection,
                    profile.rebirthProgress), seed = 41))),
        ) as GameplayDecision.Accepted).frame.nextState
        accept(GameplayInteractionPulse.ViewportChanged.fromValidated(1_440f, 810f, 1f))
    }

    val model: GameplayRenderModel get() = requireNotNull(GameplayNucleus.renderSnapshot(state).renderModel)

    fun play(seconds: Float) {
        var decisions = 0
        while (model.elapsed < seconds && decisions++ < 20_000) {
            val current = model
            when (current.phase) {
                GamePhase.CHOICE -> accept(GameplayInteractionPulse.ChoiceSelected.fromValidated(
                    if (current.choiceType == ChoiceType.OMEN) current.choices.indexOfFirst { it.omenId == null }.coerceAtLeast(0) else 0))
                GamePhase.RUNNING -> {
                    // The singularity sits on the nearest enemy (clamped to the screen): the Core flies into the pack.
                    val target = current.enemies.filter { !it.dead }.minByOrNull {
                        (it.x - current.coreX) * (it.x - current.coreX) + (it.y - current.coreY) * (it.y - current.coreY)
                    }
                    if (target != null) {
                        accept(GameplayInteractionPulse.PointerMoved.fromValidated(
                            (target.x - current.cameraX + current.screenWidth * 0.5f).coerceIn(0f, current.screenWidth),
                            (target.y - current.cameraY + current.screenHeight * 0.5f).coerceIn(0f, current.screenHeight),
                        ))
                    }
                    accept(GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f))
                }
                else -> return
            }
        }
    }

    private fun accept(pulse: GameplayInteractionPulse) {
        val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))
        if (decision !is GameplayDecision.Accepted) return
        state = decision.frame.nextState
        for (output in decision.frame.outputs) {
            if (output !is GameplayOutput.EmitVisualFx) continue
            // Cue by cue: the newest projected number after a damage cue is the one that cue formatted.
            for (cue in output.cues) {
                reducer.apply(listOf(cue))
                if (cue is VisualFxCue.DamageNumberAdded) reducer.snapshot().damageNumbers.lastOrNull()?.let(damageNumbers::add)
            }
        }
    }
}
