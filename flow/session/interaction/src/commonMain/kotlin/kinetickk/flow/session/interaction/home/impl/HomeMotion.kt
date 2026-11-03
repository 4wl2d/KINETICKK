// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkHomePalette
import kinetickk.foundation.design.KkTime
import kinetickk.foundation.design.kkChamfer
import kotlin.math.max
import kotlin.math.min

/**
 * Draw-only Home motion and caches; semantic selection stays with HomeReducer and the feature.
 * Palette recolor (350 ms Out) and decorative motion follow the frame clock. Selection is drawn
 * directly from the current target so a departing pointer leaves no stale highlight behind.
 */
internal class HomeMenuMotion {
    private var activeIndex = 0
    private var accentFrom = KkHomePalette.START.accent
    private var accentTarget = HomeLayoutTarget.START
    private var accentElapsedMs = KkTime.PaletteShift.toFloat()
    var accent: Color = accentFrom
        private set

    /** Clock of the Codex accent sweep, and the sweep step the settled accent shows (−1 when none). */
    private var sweepSeconds = 0f
    private var sweepStep = -1
    private var sweepHues: List<Color>? = null
    private var sweepColors: List<Color> = HomeCodexSweep
    /** Cache slot of each sweep step: the first step showing the same accent (the holds share one). */
    private var sweepSlots: IntArray = homeCodexSweepSlots(HomeCodexSweep)

    /**
     * Advances the frame clock. The accent eases into the selected item's palette; on the Codex it
     * then follows the element sweep through [hues] in cached steps, so a steady frame allocates nothing.
     */
    fun advance(target: HomeLayoutTarget, deltaSeconds: Float, hues: List<Color> = Kk.Elements) {
        val delta = deltaSeconds.coerceIn(0f, MAX_HOME_PRESENTATION_FRAME_DELTA_SECONDS)
        // The Characters card is not a menu row: while it is active no row is selected.
        val index = HomeMenuTargets.indexOf(target)
        selectedSeconds = if (index == activeIndex) selectedSeconds + delta else 0f
        activeIndex = index
        sweepSeconds = (sweepSeconds + delta) % HOME_CODEX_SWEEP_SECONDS
        val sweep = homeCodexSweepStep(sweepSeconds)
        if (hues !== sweepHues) {
            // A new color-vision palette: its own sweep, and fresh tints for it.
            sweepHues = hues
            sweepColors = homeCodexSweep(hues)
            sweepSlots = homeCodexSweepSlots(sweepColors)
            sweepBase = null
            sweepBrushCenter = Offset.Unspecified
        }
        val next = if (target == HomeLayoutTarget.CODEX) sweepColors[sweep] else menuAccent(target)
        if (target != accentTarget) {
            accentFrom = accent
            accentTarget = target
            accentElapsedMs = 0f
        }
        accentElapsedMs = min(KkTime.PaletteShift.toFloat(), accentElapsedMs + delta * 1000f)
        val settled = accentElapsedMs >= KkTime.PaletteShift
        accent = if (settled) next else lerp(accentFrom, next, KkEase.Out.transform(accentElapsedMs / KkTime.PaletteShift))
        sweepStep = if (settled && target == HomeLayoutTarget.CODEX) sweepSlots[sweep] else -1
    }

    private var selectedSeconds = HOME_TRAIL_REST_SECONDS

    /** Speed-line clock of the selected item: the lines draw in once, then hold (never loop). */
    val trailTime: Float get() = homeTrailTime(selectedSeconds)

    // Caches (draw thread only): the accent-tinted measurer, strokes, paths and the trail brush.
    private var tintedBase: CanvasTextMeasurer? = null
    private var tintedAccent = Color.Unspecified
    private var tinted: CanvasTextMeasurer? = null

    /** Home palettes recolor the `you` accents of the menu components (the board overrides --volt). */
    fun tinted(measurer: CanvasTextMeasurer): CanvasTextMeasurer {
        val step = sweepStep
        if (step >= 0) {
            if (sweepBase !== measurer) {
                sweepTinted.fill(null)
                sweepMenu.fill(null)
                sweepBase = measurer
            }
            return sweepTinted[step] ?: CanvasTextMeasurer(measurer.delegate, measurer.scale, measurer.language, measurer.typography,
                measurer.roles.copy(you = accent)).also { sweepTinted[step] = it }
        }
        val cached = tinted
        if (cached != null && tintedBase === measurer && tintedAccent == accent) return cached
        return CanvasTextMeasurer(measurer.delegate, measurer.scale, measurer.language, measurer.typography,
            measurer.roles.copy(you = accent)).also {
            tinted = it
            tintedBase = measurer
            tintedAccent = accent
        }
    }

    // One tinted measurer, menu measurer and trail brush per sweep step, built once and reused.
    private var sweepBase: CanvasTextMeasurer? = null
    private val sweepTinted = arrayOfNulls<CanvasTextMeasurer>(HOME_CODEX_SWEEP_STEPS)
    private val sweepMenu = arrayOfNulls<CanvasTextMeasurer>(HOME_CODEX_SWEEP_STEPS)
    private var sweepBrushCenter = Offset.Unspecified
    private val sweepBrushes = arrayOfNulls<Brush>(HOME_CODEX_SWEEP_STEPS)

    private var menuBase: CanvasTextMeasurer? = null
    private var menuMeasurerValue: CanvasTextMeasurer? = null

    /** Measurer of the menu items (see [homeMenuMeasurerScale]), derived from the tinted one. */
    fun menuMeasurer(tinted: CanvasTextMeasurer): CanvasTextMeasurer {
        val step = sweepStep
        if (step >= 0 && sweepTinted[step] === tinted) {
            return sweepMenu[step] ?: menuMeasurerOf(tinted).also { sweepMenu[step] = it }
        }
        val cached = menuMeasurerValue
        if (cached != null && menuBase === tinted) return cached
        return menuMeasurerOf(tinted).also {
            menuMeasurerValue = it
            menuBase = tinted
        }
    }

    private fun menuMeasurerOf(tinted: CanvasTextMeasurer): CanvasTextMeasurer {
        val scale = homeMenuMeasurerScale(tinted.scale)
        return if (scale == tinted.scale) tinted else CanvasTextMeasurer(tinted.delegate, scale, tinted.language, tinted.typography, tinted.roles)
    }

    private var placementLayout: HomeLayoutGeometry? = null
    private var placementScale = 0f
    private var placementLanguage: AppLanguage? = null
    private val placementFacts = arrayOfNulls<HomeFacts>(HomeMenuTargets.size)
    private val placementLeft = FloatArray(HomeMenuTargets.size)
    private val placementSub = BooleanArray(HomeMenuTargets.size)

    /** Stamp size of the one-column menu (see [homeMenuStampSize]), set with the [menuPlacement] cache. */
    var menuStampSize = Float.NaN
        private set

    /** Left edge (px) of menu item [index] and whether its sub-value fits (see [homeMenuPlacement]), cached. */
    fun menuPlacement(menu: CanvasTextMeasurer, layout: HomeLayoutGeometry, index: Int, label: String, facts: HomeFacts, density: Float): Float {
        if (placementLayout !== layout || placementScale != menu.scale || placementLanguage != menu.language) {
            placementFacts.fill(null)
            placementLayout = layout
            placementScale = menu.scale
            placementLanguage = menu.language
            menuStampSize = homeMenuStampSize(menu, layout, density)
        }
        if (placementFacts[index] !== facts) {
            val bounds = layout.bounds(HomeMenuTargets[index])
            val placement = homeMenuPlacement(menu, bounds, label, facts.sub, facts.stamp, layout.scene.menuFontSize, menuStampSize, density)
            placementLeft[index] = placement.left
            placementSub[index] = placement.showSub
            placementFacts[index] = facts
        }
        return placementLeft[index]
    }

    /** Whether the last [menuPlacement] of item [index] keeps its sub-value. */
    fun menuShowsSub(index: Int): Boolean = placementSub[index]

    private var formLayout: HomeLayoutGeometry? = null
    var formNameRight = 0f
        private set

    /** Caches the form name's right end (it follows the layout only). */
    fun formPanel(layout: HomeLayoutGeometry, density: Float) {
        if (formLayout === layout) return
        formLayout = layout
        formNameRight = homeFormNameRight(layout, density)
    }

    private var strokeUnit = -1f
    var dashedThin: Stroke = Stroke(1f)
        private set
    var dashedRing: Stroke = Stroke(1f)
        private set

    fun strokes(unit: Float, density: Float) {
        if (unit == strokeUnit) return
        strokeUnit = unit
        val width = max(1.5f * unit, density)
        dashedThin = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 3f, width * 3f)))
        dashedRing = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 4f, width * 2.5f)))
    }

    private var brushAccent = Color.Unspecified
    private var brushCenter = Offset.Unspecified
    private var brush: Brush? = null

    /** Conic trail (`conic-gradient` from 190° to 357°, transparent to accent) around [center]. */
    fun trailBrush(center: Offset): Brush {
        val step = sweepStep
        if (step >= 0) {
            if (sweepBrushCenter != center) {
                sweepBrushes.fill(null)
                sweepBrushCenter = center
            }
            return sweepBrushes[step] ?: trailBrushOf(center).also { sweepBrushes[step] = it }
        }
        val cached = brush
        if (cached != null && brushAccent == accent && brushCenter == center) return cached
        return trailBrushOf(center).also {
            brush = it
            brushAccent = accent
            brushCenter = center
        }
    }

    private fun trailBrushOf(center: Offset): Brush =
        Brush.sweepGradient(
            0f to accent.copy(alpha = 0f),
            TRAIL_START / 360f to accent.copy(alpha = 0f),
            (TRAIL_START + TRAIL_SWEEP) / 360f to accent,
            (TRAIL_START + TRAIL_SWEEP + 1f) / 360f to accent.copy(alpha = 0f),
            1f to accent.copy(alpha = 0f),
            center = center,
        )

    val shapePath = Path()
    val shardPath = Path()
    val factsPath = Path()
    private var factsRect = Rect.Zero

    fun factsPath(rect: Rect, cut: Float): Path {
        if (rect != factsRect) {
            factsRect = rect
            factsPath.kkChamfer(rect, cut)
        }
        return factsPath
    }

    /** Enemy silhouettes on the 24-unit grid (triangle, diamond, hexagon, pentagon). */
    val enemies: List<Path> by lazy {
        listOf(
            gridPath(12f, 3f, 21f, 20f, 3f, 20f),
            gridPath(12f, 2f, 22f, 12f, 12f, 22f, 2f, 12f),
            gridPath(7f, 3f, 17f, 3f, 22f, 12f, 17f, 21f, 7f, 21f, 2f, 12f),
            gridPath(12f, 3f, 21f, 10f, 17.5f, 21f, 6.5f, 21f, 3f, 10f),
        )
    }
    val enemyStroke = Stroke(2f, join = StrokeJoin.Miter)

    private var factsKey: Any? = null
    private var factsTarget: HomeLayoutTarget? = null
    private var factsLanguage: AppLanguage? = null
    private var factsScale = 0f
    private var factsValue: HomeFacts? = null

    /** Facts for [target], rebuilt only when the model, target, language or text size changes. */
    fun facts(model: HomeUiModel, target: HomeLayoutTarget, language: AppLanguage, textScale: Float): HomeFacts {
        val cached = factsValue
        if (cached != null && factsKey === model && factsTarget == target && factsLanguage == language && factsScale == textScale) {
            return cached
        }
        return homeFacts(model, target, language, textScale).also {
            factsValue = it
            factsKey = model
            factsTarget = target
            factsLanguage = language
            factsScale = textScale
        }
    }

    private var menuFactsKey: Any? = null
    private var menuFactsLanguage: AppLanguage? = null
    private var menuFactsScale = 0f
    private val menuFactsValue = arrayOfNulls<HomeFacts>(HomeMenuTargets.size)

    /** Sub value and stamp of every menu item, cached like [facts]. */
    fun menuFacts(model: HomeUiModel, index: Int, language: AppLanguage, textScale: Float): HomeFacts {
        if (menuFactsKey !== model || menuFactsLanguage != language || menuFactsScale != textScale) {
            menuFactsValue.fill(null)
            menuFactsKey = model
            menuFactsLanguage = language
            menuFactsScale = textScale
        }
        return menuFactsValue[index] ?: homeFacts(model, HomeMenuTargets[index], language, textScale)
            .also { menuFactsValue[index] = it }
    }

    private companion object {
        const val TRAIL_START = 100f
        const val TRAIL_SWEEP = 167f

        fun gridPath(vararg points: Float): Path = Path().apply {
            moveTo(points[0], points[1])
            var index = 2
            while (index < points.size) {
                lineTo(points[index], points[index + 1])
                index += 2
            }
            close()
        }
    }
}
