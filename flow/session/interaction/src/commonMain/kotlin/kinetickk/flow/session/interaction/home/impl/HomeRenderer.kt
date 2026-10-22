// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.localizedContent
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkFormGeometry
import kinetickk.foundation.design.KkFormPaths
import kinetickk.foundation.design.KkHomePalette
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkMutatorSigil
import kinetickk.foundation.design.KkMutatorSigilPaths
import kinetickk.foundation.design.KkTime
import kinetickk.foundation.design.KkTooltipPlacement
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkButton
import kinetickk.foundation.design.drawKkGem
import kinetickk.foundation.design.drawKkGrid
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkHatch
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkInfoButton
import kinetickk.foundation.design.drawKkMenuItem
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.drawKkThreatHatch
import kinetickk.foundation.design.drawKkTile
import kinetickk.foundation.design.drawKkTooltip
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.kkChamfer
import kinetickk.foundation.design.kkLerp
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStampSize
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.wideStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Seconds per Core revolution around the singularity (ambient loops never run faster). */
internal const val HOME_ORBIT_SECONDS: Float = KkTime.AmbientMin / 1000f

/** cos 66°: the hero plane is tilted back 66° (CSS `rotateX(66deg)`, orthographic). */
private const val TILT = 0.40673664f
private const val SIN_TILT = 0.9135454f

/** Board angles (degrees, clockwise from 12 o'clock) of the four enemies placed on the orbit. */
private val EnemyAngles = floatArrayOf(60f, 150f, 250f, 320f)

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
        // The Hangar card is not a menu row: while it is active no row is selected.
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

/**
 * Seconds into the menu trail cycle (`kk-trail`: 0.9 s, lines delayed 0 / 0.15 / 0.3 s, opaque
 * between 18 % and 72 % of a cycle) at which all three speed lines are fully drawn.
 */
internal const val HOME_TRAIL_REST_SECONDS: Float = 0.55f

/** The trail runs from selection until [HOME_TRAIL_REST_SECONDS] and then rests there. */
internal fun homeTrailTime(secondsSinceSelected: Float): Float = secondsSinceSelected.coerceIn(0f, HOME_TRAIL_REST_SECONDS)

/**
 * Home palette accent of the selected/hovered menu item (applies to the items the game has). Each
 * color names what the item opens: volt is you (and Kinetic), the deeper volt the Workshop (permanent
 * upgrades paid in Matter, the palette's former Lab entry), gold Rebirth (the Rebirth screen keeps its
 * per-tier themes), mute the system.
 * The Codex sweeps through the element hues instead (see [HomeCodexSweep]); its static entry is Cryo.
 */
internal fun menuAccent(target: HomeLayoutTarget): Color = when (target) {
    HomeLayoutTarget.WORKSHOP -> KkHomePalette.LAB.accent
    HomeLayoutTarget.REBIRTH -> KkHomePalette.REBIRTH.accent
    HomeLayoutTarget.CODEX -> KkHomePalette.CODEX.accent
    HomeLayoutTarget.SETTINGS -> KkHomePalette.SETTINGS.accent
    else -> KkHomePalette.START.accent
}

/** One full sweep of the Codex accent through the seven element hues (about 1.7 s each). */
internal const val HOME_CODEX_SWEEP_SECONDS: Float = 12f

/**
 * Distinct accents of one sweep (30 per second); each is tinted once and then reused. With the
 * smooth blend below, one step moves a hue change by at most about 5 %, so the sweep reads as a glide.
 */
internal const val HOME_CODEX_SWEEP_STEPS: Int = 360

/** Sweep step shown at [seconds] into the sweep. */
internal fun homeCodexSweepStep(seconds: Float): Int =
    ((seconds.mod(HOME_CODEX_SWEEP_SECONDS) / HOME_CODEX_SWEEP_SECONDS) * HOME_CODEX_SWEEP_STEPS).toInt().coerceIn(0, HOME_CODEX_SWEEP_STEPS - 1)

/**
 * Accent at [phase] 0..1 of the Codex sweep: it holds each element hue for 40 % of its share,
 * then glides (a smooth in-and-out, steepest at 1.5 × the linear rate) into the next, wrapping
 * from Void back to Thermal.
 */
internal fun homeCodexSweepColor(phase: Float, hues: List<Color> = Kk.Elements): Color {
    val position = phase.mod(1f) * hues.size
    val index = position.toInt().coerceIn(0, hues.lastIndex)
    val blend = ((position - index - HOME_CODEX_SWEEP_HOLD) / (1f - HOME_CODEX_SWEEP_HOLD)).coerceIn(0f, 1f)
    return lerp(hues[index], hues[(index + 1) % hues.size], blend * blend * (3f - 2f * blend))
}

private const val HOME_CODEX_SWEEP_HOLD = 0.4f

/** The accents of one Codex sweep through [hues], indexed by [homeCodexSweepStep]. */
internal fun homeCodexSweep(hues: List<Color>): List<Color> =
    List(HOME_CODEX_SWEEP_STEPS) { step -> homeCodexSweepColor(step.toFloat() / HOME_CODEX_SWEEP_STEPS, hues) }

/** For each step of [sweep], the first step with the same accent, so equal accents share one cache slot. */
internal fun homeCodexSweepSlots(sweep: List<Color>): IntArray = IntArray(sweep.size) { step -> sweep.indexOf(sweep[step]) }

/** The default palette's Codex sweep. */
internal val HomeCodexSweep: List<Color> = homeCodexSweep(Kk.Elements)

internal val HomeMenuLabels = listOf(
    SessionText.START_RUN,
    SessionText.WORKSHOP,
    SessionText.REBIRTH,
    SessionText.CODEX,
    SessionText.SETTINGS,
)

/** Facts card content: the item's label, one big value, facts from real data and its (!) text. */
internal class HomeFacts(
    val label: String,
    val big: String,
    val facts: List<Pair<String, String>>,
    val info: String,
    val sub: String?,
    val stamp: String?,
)

/** The stamp menu row [target] carries when the game's state calls for it (see [homeFacts]). */
internal fun homeMenuStampText(target: HomeLayoutTarget, language: AppLanguage): String? = when (target) {
    HomeLayoutTarget.WORKSHOP -> language.text(SessionRedesignText.AFFORDABLE)
    HomeLayoutTarget.REBIRTH -> language.text(SessionRedesignText.READY)
    else -> null
}

internal fun homeFacts(model: HomeUiModel, target: HomeLayoutTarget, language: AppLanguage, textScale: Float): HomeFacts {
    val label = if (target == HomeLayoutTarget.HANGAR) language.text(SessionText.CHARACTERS)
        else language.text(HomeMenuLabels[HomeMenuTargets.indexOf(target).coerceAtLeast(0)])
    val matter = homeNumber(model.totalMatter, language)
    val weapon = model.startingWeaponName?.localizedContent(language)
    val tierProfile = model.rebirthProfile
    return when (target) {
        HomeLayoutTarget.WORKSHOP -> {
            val completion = homeWorkshopCompletion(model)
            HomeFacts(
                label,
                language.text(SessionRedesignText.PERCENT, completion),
                buildList {
                    add(language.text(SessionRedesignText.WEAPONS_FACT) to "${model.unlockedWeaponCount}/${model.weaponCount}")
                    if (model.matrixMaxRanks > 0) {
                        add(language.text(SessionRedesignText.RANKS) to "${model.matrixRanks}/${model.matrixMaxRanks}")
                    }
                    if (model.upgradeLevelCap > 0) {
                        add(language.text(SessionRedesignText.UPGRADES) to "${model.upgradeLevels}/${model.upgradeLevelCap}")
                    }
                },
                language.text(SessionRedesignText.WORKSHOP_INFO),
                language.text(SessionRedesignText.PERCENT, completion),
                if (model.workshopAffordable) homeMenuStampText(target, language) else null,
            )
        }
        HomeLayoutTarget.REBIRTH -> HomeFacts(
            label,
            model.rebirthLevel.toString(),
            listOf(
                language.text(SessionRedesignText.TIER) to tierProfile.displayName.localizedContent(language),
                language.text(SessionRedesignText.RANK) to
                    if (model.ascendant) language.text(SessionRedesignText.ASCENDANT) else model.rebirthRank.toString(),
                language.text(SessionRedesignText.STATUS) to
                    language.text(if (model.canRebirth) SessionRedesignText.NEW_TIER else SessionRedesignText.SELECTED),
            ),
            language.text(SessionRedesignText.REBIRTH_INFO),
            null,
            if (model.canRebirth) homeMenuStampText(target, language) else null,
        )
        HomeLayoutTarget.CODEX -> HomeFacts(
            label,
            model.discoveredItemCount.toString(),
            buildList {
                add(language.text(SessionText.ITEMS_TITLE) to "${model.discoveredItemCount}/${model.itemCount}")
                if (model.relicCount > 0) add(language.text(SessionText.RELICS_TITLE) to "${model.discoveredRelicCount}/${model.relicCount}")
                add(language.text(SessionText.FORMS_TITLE) to "${model.unlockedCoreShapes.size}/${model.coreShapes.size}")
            },
            language.text(SessionRedesignText.CODEX_INFO),
            "${model.discoveredItemCount}/${model.itemCount}",
            null,
        )
        HomeLayoutTarget.HANGAR -> {
            val definition = model.coreShape(model.coreShape)
            HomeFacts(
                label,
                "${model.unlockedCoreShapes.size}/${model.coreShapes.size}",
                listOf(
                    language.text(SessionRedesignText.FORM) to definition.displayName.localizedContent(language),
                    language.text(SessionRedesignText.MECHANIC) to definition.mechanicName.localizedContent(language),
                ),
                language.text(SessionRedesignText.HANGAR_INFO),
                null,
                null,
            )
        }
        HomeLayoutTarget.SETTINGS -> HomeFacts(
            label,
            language.code.uppercase(),
            listOf(
                language.text(SessionRedesignText.LANGUAGE) to language.nativeName,
                language.text(SessionRedesignText.TEXT_SIZE) to "${(textScale * 100f + 0.5f).toInt()}%",
            ),
            language.text(SessionRedesignText.SETTINGS_INFO),
            null,
            null,
        )
        else -> HomeFacts(
            label,
            model.rebirthLevel.toString(),
            buildList {
                add(language.text(SessionRedesignText.TIER) to tierProfile.displayName.localizedContent(language))
                add(language.text(SessionRedesignText.FORM) to model.coreShape(model.coreShape).displayName.localizedContent(language))
            },
            rebirthTierDescription(tierProfile).localizedContent(language),
            language.text(SessionText.REBIRTH_LEVEL, model.rebirthLevel),
            null,
        )
    }
}

/**
 * How much of the Workshop is bought, in whole percent (rounded down): owned weapons, Matrix ranks and
 * Refit / Honing levels over every weapon, rank and level it sells. Unread parts count as nothing.
 */
internal fun homeWorkshopCompletion(model: HomeUiModel): Int {
    val total = model.weaponCount.toLong() + model.matrixMaxRanks + model.upgradeLevelCap
    if (total <= 0L) return 0
    val owned = model.unlockedWeaponCount.toLong() + model.matrixRanks + model.upgradeLevels
    return (owned.coerceIn(0L, total) * 100L / total).toInt()
}

/** What the selected tier adds: its newest mutator's rule, or the baseline cycle's line at tier 0. */
internal fun rebirthTierDescription(profile: kinetickk.ball.content.api.RebirthProfile): String =
    profile.newestMutator?.description ?: BASELINE_TIER_DESCRIPTION

private const val BASELINE_TIER_DESCRIPTION = "The original Architect cycle."

/** Grouped integer (1,284 / 1 284) for chips and facts. */
internal fun homeNumber(value: Long, language: AppLanguage): String {
    val digits = value.coerceAtLeast(0L).toString()
    val separator = if (language == AppLanguage.Russian) ' ' else ','
    return buildString(digits.length + digits.length / 3) {
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(separator)
            append(char)
        }
    }
}

/** The (!) text of the form panel: the current character's mechanic name and what it does. */
internal fun homeFormInfo(model: HomeUiModel, language: AppLanguage): String {
    val definition = model.coreShape(model.coreShape)
    return definition.mechanicName.localizedContent(language) + "\n" + definition.mechanicDescription.localizedContent(language)
}

/** The form icon of a character: [KkIcon.Forms] follows the character order. */
internal fun CoreShape.formIcon(): KkIcon = KkIcon.Forms[ordinal]

/** Interaction state the Canvas reflects (hover/focus come from the semantic overlay). */
internal class HomeDrawState(
    val activeTarget: HomeLayoutTarget,
    val focusedTarget: HomeLayoutTarget?,
    val openInfo: HomeInfoTarget?,
    val activeInfo: HomeInfoTarget?,
    /** The player's text size setting (the measurer carries the UI factor, [homeUiScale]). */
    val textScale: Float = HOME_DEFAULT_TEXT_SCALE,
)

/** The game's default text size, at which Home UI text renders at the design's size. */
internal const val HOME_DEFAULT_TEXT_SCALE = 1.25f

/**
 * Measurer scale for Home UI text at the player's [textScale]: 1 (the design's size) at the default
 * 125 %, 0.8 at 100 %, 1.4 at 175 %. Display type (menu labels, form name, hero word) divides it out.
 */
internal fun homeUiScale(textScale: Float): Float = textScale / HOME_DEFAULT_TEXT_SCALE

/**
 * Measurer scale of the menu items for a Home measurer of [uiScale]. The labels are display type
 * (drawn at the layout's menu font whatever the scale), and the foundation item sizes its slab,
 * echo and speed lines from the label as drawn, so they keep the design size at every text size
 * (see [HOME_MENU_REACH]). The scale stays at least 1 so the stamp and sub-value keep at least their
 * design size and grow with larger text.
 */
internal fun homeMenuMeasurerScale(uiScale: Float): Float = max(1f, uiScale)

/** Where a one-column menu item is drawn (px) and whether its optional sub-value is kept. */
internal class HomeMenuPlacement(val left: Float, val showSub: Boolean)

/**
 * Keeps a one-column menu item inside its row [bounds] (px): its label, slab and speed lines keep
 * their size at every text size, so it starts at the row (its speed lines fill the reach left of it,
 * see [homeSelectedMenuExtent]) with its stamp at [stampSize] (see [homeMenuStampSize]); when its
 * sub-value (shown only when selected) would run past the row, it is left out. Mirrors the foundation
 * item's layout: label after 22 px, each extra 18 px after the previous one, 26 px moved left when
 * selected (board px at font 64, scaled with the drawn label).
 */
internal fun homeMenuPlacement(
    menu: CanvasTextMeasurer,
    bounds: Rect,
    label: String,
    sub: String?,
    stamp: String?,
    menuFontPx: Float,
    stampSize: Float,
    density: Float,
): HomeMenuPlacement {
    val fontSize = max(1f, menuFontPx / density / menu.scale)
    // Gaps follow the drawn label; stamp and sub sizes are set before the measurer's scale.
    val k = fontSize * menu.scale / 64f
    val textK = fontSize / 64f
    fun u(value: Float) = value * k * density
    val text = measureKkText(menu, label, menu.typography.condStyle(fontSize, lineHeightEm = 1f), uppercase = true).size.width
    val subWidth = sub?.let {
        measureKkText(menu, it, menu.typography.monoStyle(11f * textK.coerceAtLeast(0.8f), trackingEm = 0.06f), uppercase = true).size.width
    }
    val stampWidth = stamp?.let { kkStampSize(menu, it, density, stampSize).width } ?: 0f
    val stampPart = if (stamp != null) u(18f) + stampWidth else 0f
    val resting = bounds.left + u(22f) + text + stampPart
    val selected = resting - u(26f) + if (subWidth != null) u(18f) + subWidth else 0f
    return HomeMenuPlacement(bounds.left, subWidth != null && max(resting, selected) <= bounds.right)
}

/**
 * Stamp size (before the menu measurer's scale) shared by the one-column menu's stamps: the
 * foundation item's size, which grows with the text size, stepped down until every row that can
 * carry a stamp (see [homeMenuStampText]) holds its label and stamp, but never below the size drawn
 * at the default text size (the layout fits the rows at that size). Stamps share one size.
 */
internal fun homeMenuStampSize(menu: CanvasTextMeasurer, layout: HomeLayoutGeometry, density: Float): Float {
    val menuFont = layout.scene.menuFontSize / density
    val fontSize = max(1f, menuFont / menu.scale)
    val full = 14f * (fontSize / 64f).coerceAtLeast(0.8f)
    if (layout.scene.menuColumns != 1) return full
    val floor = min(full, 14f * (menuFont / 64f).coerceAtLeast(0.8f) / menu.scale)
    val gaps = (22f + 18f) * menuFont / 64f * density
    var size = full
    HomeMenuTargets.forEachIndexed { index, target ->
        val stamp = homeMenuStampText(target, menu.language) ?: return@forEachIndexed
        val bounds = layout.bounds(target)
        val label = measureKkText(menu, menu.language.text(HomeMenuLabels[index]), menu.typography.condStyle(fontSize, lineHeightEm = 1f),
            uppercase = true).size.width
        val room = bounds.width - gaps - label
        while (size > floor && kkStampSize(menu, stamp, density, size).width > room) size = max(floor, size - HOME_STAMP_STEP)
    }
    return size
}

/** Step (px before the measurer's scale) by which a menu stamp shrinks to fit its row. */
private const val HOME_STAMP_STEP = 0.25f

internal fun DrawScope.drawHome(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    time: Float,
    layout: HomeLayoutGeometry,
    state: HomeDrawState,
    motion: HomeMenuMotion,
) {
    val scene = layout.scene
    drawRect(Kk.Ink)
    // The layout follows the measured viewport; skip frames drawn before it is known.
    if (scene.heroRadius <= 1f || size.width <= 1f || size.height <= 1f) return
    val accent = motion.accent
    val tinted = motion.tinted(text)
    val language = text.language
    val facts = motion.facts(model, state.activeTarget, language, state.textScale)
    drawRect(kkMix(Kk.Ink, accent, 0.07f))
    drawKkGrid(Rect(Offset.Zero, size), Kk.Bone.copy(alpha = 0.045f), scene.gridSpacing / density)
    drawBackgroundWord(text, facts.label, scene, accent)
    drawHero(model, text, time, scene, state, motion)
    drawHeader(model, text, scene, accent)
    drawMenu(model, tinted, layout, motion, state.activeTarget, state.textScale)
    scene.facts?.let { drawFactsCard(tinted, it, facts, layout, state, motion, accent) }
    drawFormPanel(model, tinted, layout, state, motion)
    drawLegal(text, layout)
    val open = state.openInfo ?: state.activeInfo
    if (open != null) {
        val info = layout.info(open)
        if (info != null) {
            val content = when (open) {
                HomeInfoTarget.FACTS -> facts.info
                HomeInfoTarget.FORM -> homeFormInfo(model, language)
            }
            val placement = if (layout.mode == HomeLayoutMode.COMPACT_PORTRAIT) KkTooltipPlacement.ABOVE else KkTooltipPlacement.ABOVE_END
            drawKkTooltip(text, info.bounds, content, placement)
        }
    }
}

private fun DrawScope.displaySize(text: CanvasTextMeasurer, px: Float): Float = max(1f, px / density / text.scale)

private fun DrawScope.drawBackgroundWord(text: CanvasTextMeasurer, label: String, scene: HomeScene, accent: Color) {
    val layout = measureKkText(text, label, text.typography.wideStyle(displaySize(text, scene.wordSize), lineHeightEm = 1f), uppercase = true)
    drawKkText(layout, scene.wordLeft, scene.wordTop, accent.copy(alpha = 0.07f))
}

private fun DrawScope.drawHero(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    time: Float,
    scene: HomeScene,
    state: HomeDrawState,
    motion: HomeMenuMotion,
) {
    val accent = motion.accent
    val roles = text.roles
    val center = scene.heroCenter
    val k = scene.heroRadius / 260f
    motion.strokes(scene.unit, density)
    // Halftone disc (accent @12 %), radially faded.
    val halftoneRadius = 310f * k
    val halftone = Rect(center.x - halftoneRadius, center.y - halftoneRadius, center.x + halftoneRadius, center.y + halftoneRadius)
    drawKkRadialFade(halftone) { drawKkHalftone(halftone, accent.copy(alpha = 0.12f)) }
    // Accretion rays: dashes crawling inward along 14 spokes.
    val rayScale = max(scene.unit, density)
    for (index in 0 until 14) {
        val angle = index / 14f * 2f * PI.toFloat() + 0.2f
        val outer = (300f - (index % 3) * 30f) * k
        val inner = 70f * k
        val period = 48f * k
        val loop = 0.9f + (index % 4) * 0.25f
        val phase = ((time / loop) % 1f) * period
        val dx = cos(angle)
        val dy = sin(angle)
        val color = accent.copy(alpha = if (index % 2 == 1) 0.35f else 0.6f)
        val width = (if (index % 2 == 1) 1.5f else 2.5f) * rayScale
        val length = outer - inner
        var start = phase - period
        while (start < length) {
            val a = max(0f, start)
            val b = min(length, start + 16f * k)
            if (b > a) {
                drawLine(
                    color,
                    Offset(center.x + dx * (outer - a), center.y + dy * (outer - a)),
                    Offset(center.x + dx * (outer - b), center.y + dy * (outer - b)),
                    width,
                )
            }
            start += period
        }
    }
    // Orbit plane rings (dashed outer, faint middle, accent inner).
    drawOval(Kk.Bone.copy(alpha = 0.18f), Offset(center.x - 300f * k, center.y - 300f * k * TILT), Size(600f * k, 600f * k * TILT), style = motion.dashedThin)
    drawOval(Kk.Bone.copy(alpha = 0.08f), Offset(center.x - 260f * k, center.y - 260f * k * TILT), Size(520f * k, 520f * k * TILT), style = kkStroke(max(k, 1f)))
    drawOval(accent.copy(alpha = 0.4f), Offset(center.x - 150f * k, center.y - 150f * k * TILT), Size(300f * k, 300f * k * TILT), style = motion.dashedThin)

    val spin = (time / HOME_ORBIT_SECONDS * 360f) % 360f
    val coreAngle = spin - 20f
    // Conic trail behind the Core, in the tilted plane.
    val trailRadius = 259.7f * k
    withTransform({
        scale(1f, TILT, center)
        rotate(coreAngle, center)
    }) {
        drawArc(
            motion.trailBrush(center), 100f, 167f, false,
            Offset(center.x - trailRadius, center.y - trailRadius), Size(trailRadius * 2f, trailRadius * 2f),
            style = kkStroke(28f * k),
        )
    }
    val coreRadians = coreAngle * PI.toFloat() / 180f
    val planeY = -cos(coreRadians)
    val core = Offset(center.x + 260f * k * sin(coreRadians), center.y + 260f * k * planeY * TILT)
    val coreFar = planeY < 0f
    val perspective = 1500f / (1500f - 260f * planeY * SIN_TILT)
    // Far half first: enemies and Core behind the singularity.
    drawEnemies(text, time, center, k, spin, accent, far = true, motion)
    if (coreFar) {
        drawTether(core, center, k, accent, roles.threat)
        drawCore(model, core, k, perspective, time, motion)
    }
    drawSingularity(center, k, time, roles.threat, motion)
    drawEnemies(text, time, center, k, spin, accent, far = false, motion)
    if (!coreFar) {
        drawTether(core, center, k, accent, roles.threat)
        drawCore(model, core, k, perspective, time, motion)
    }
}

private fun DrawScope.drawTether(core: Offset, center: Offset, k: Float, accent: Color, threat: Color) {
    val width = max(3f * k, density * 1.5f)
    for (segment in 0 until 8) {
        val a = segment / 8f
        val b = (segment + 1) / 8f
        drawLine(
            lerp(accent, threat.copy(alpha = 0.9f), (a + b) * 0.5f),
            Offset(kkLerp(core.x, center.x, a), kkLerp(core.y, center.y, a)),
            Offset(kkLerp(core.x, center.x, b), kkLerp(core.y, center.y, b)),
            width,
        )
    }
}

private fun DrawScope.drawSingularity(center: Offset, k: Float, time: Float, threat: Color, motion: HomeMenuMotion) {
    val turn = (time / HOME_ORBIT_SECONDS * 360f) % 360f
    rotate(turn, center) {
        drawCircle(threat.copy(alpha = 0.5f), 70f * k, center, style = motion.dashedRing)
    }
    rotate(-turn, center) {
        drawCircle(threat.copy(alpha = 0.7f), 44f * k, center, style = motion.dashedRing)
    }
    val ring = 24f * k
    val width = max(4f * k, density * 2f)
    drawCircle(threat.copy(alpha = 0.08f), ring, center, style = kkStroke(width * 4.5f))
    drawCircle(threat.copy(alpha = 0.16f), ring, center, style = kkStroke(width * 2.5f))
    drawCircle(threat, ring, center, style = kkStroke(width))
    drawCircle(Kk.Bone, max(6f * k, density * 3f), center)
}

/** The current character's form ([KkFormPaths]) on the hero orbit, with its accent glow. */
private fun DrawScope.drawCore(
    model: HomeUiModel,
    center: Offset,
    k: Float,
    perspective: Float,
    time: Float,
    motion: HomeMenuMotion,
) {
    val shape = model.coreShape
    val radius = max(32f * k, density * 18f) * perspective
    val accent = motion.accent
    // Glow (box-shadow 46 px accent @85 %) as stacked translucent rings, never a real-time blur.
    for (index in 0 until 5) {
        drawCircle(accent.copy(alpha = 0.85f * (0.13f - index * 0.025f)), radius + (6f + index * 8f) * k, center)
    }
    val spin = when (shape) {
        CoreShape.PRISM, CoreShape.HEXAGON -> time / 60f * 360f
        CoreShape.TESSERACT, CoreShape.STAR -> time / 40f * 360f
        CoreShape.SPIRAL -> -time / 30f * 360f
        else -> 0f
    }
    val index = shape.formIndex()
    val path = KkFormPaths.of(index, radius)
    rotate(spin % 360f, center) {
        translate(center.x, center.y) {
            val outline = max(10f * k, density * 5f)
            if (KkFormGeometry.isStroked(index)) {
                val width = KkFormGeometry.strokeWidth(index, radius)
                drawPath(path, Kk.Ink, style = kkStroke(width + outline))
                drawPath(path, Kk.Bone, style = kkStroke(width))
            } else {
                drawPath(path, Kk.Ink, style = kkStroke(outline))
                drawPath(path, Kk.Bone)
            }
        }
    }
    if (shape == CoreShape.RING) {
        drawCircle(Kk.Ink, radius * 0.24f + max(5f * k, density * 2.5f), center)
        drawCircle(Kk.Bone, radius * 0.24f, center)
    }
}

/** The form index of a character: [KkFormGeometry] and [KkIcon.Forms] follow the character order. */
internal fun CoreShape.formIndex(): Int = ordinal

private fun DrawScope.drawEnemies(
    text: CanvasTextMeasurer,
    time: Float,
    center: Offset,
    k: Float,
    spin: Float,
    accent: Color,
    far: Boolean,
    motion: HomeMenuMotion,
) {
    val roles = text.roles
    val size = max(34f * k, density * 20f)
    val gridScale = size / 24f
    for (index in EnemyAngles.indices) {
        val angle = (EnemyAngles[index] - 20f) * PI.toFloat() / 180f
        val planeY = -cos(angle)
        if ((planeY < 0f) != far) continue
        val position = Offset(center.x + 260f * k * sin(angle), center.y + 260f * k * planeY * TILT)
        // Seconds since the Core last passed this enemy.
        val since = ((spin - EnemyAngles[index] + 360f) % 360f) / 360f * HOME_ORBIT_SECONDS
        val respawn = HOME_ORBIT_SECONDS * 0.35f
        val (scale, alpha, flash) = when {
            since < HIT_FLASH -> Triple(kkLerp(1.35f, 0.3f, since / HIT_FLASH), 1f - since / HIT_FLASH, true)
            since < respawn -> Triple(0f, 0f, false)
            since < respawn + 0.45f -> {
                val p = KkEase.Out.transform((since - respawn) / 0.45f)
                Triple(kkLerp(0.4f, 1f, p), p, false)
            }
            else -> Triple(1f, 1f, false)
        }
        if (alpha > 0.01f) {
            val path = motion.enemies[index]
            withTransform({
                translate(position.x - size * 0.5f * scale, position.y - size * 0.5f * scale)
                scale(gridScale * scale, gridScale * scale, Offset.Zero)
            }) {
                drawPath(path, if (flash) Kk.Bone else Kk.Ink1, alpha)
                drawPath(path, if (flash) Kk.Bone else roles.threat, alpha, motion.enemyStroke)
                if (!flash) drawKkThreatHatch(path, roles)
            }
        }
        if (since < 0.8f) {
            val p = KkEase.Out.transform(since / 0.8f)
            drawCircle(accent.copy(alpha = 1f - p), 40f * k * kkLerp(0.2f, 1.9f, p), position, style = kkStroke(max(3f * k, density * 1.5f)))
        }
        if (since < 1.2f) {
            val p = KkEase.Out.transform(since / 1.2f)
            drawShard(motion.shardPath, position, 0, p, k, roles.threat)
            drawShard(motion.shardPath, position, 1, p, k, Kk.Bone)
            drawShard(motion.shardPath, position, 2, p, k, accent)
        }
    }
}

private const val HIT_FLASH = 0.22f
private val ShardTravel = floatArrayOf(46f, -40f, 220f, -38f, 30f, -160f, 20f, 44f, 90f)
private val ShardSizes = floatArrayOf(12f, 10f, 9f)

private fun DrawScope.drawShard(path: Path, origin: Offset, index: Int, progress: Float, k: Float, color: Color) {
    val base = index * 3
    val size = ShardSizes[index] * max(k, 0.6f)
    val x = origin.x + ShardTravel[base] * k * progress
    val y = origin.y + ShardTravel[base + 1] * k * progress
    path.rewind()
    val h = size * 0.5f
    when (index) {
        0 -> { path.moveTo(x, y - h); path.lineTo(x + h, y + h); path.lineTo(x - h, y + h) }
        1 -> { path.moveTo(x - h, y - h); path.lineTo(x + h, y - h * 0.4f); path.lineTo(x - h * 0.6f, y + h) }
        else -> { path.moveTo(x, y - h); path.lineTo(x + h, y); path.lineTo(x, y + h); path.lineTo(x - h, y) }
    }
    path.close()
    rotate(ShardTravel[base + 2] * progress, Offset(x, y)) {
        drawPath(path, color, alpha = 1f - progress)
    }
}

private fun DrawScope.drawHeader(model: HomeUiModel, text: CanvasTextMeasurer, scene: HomeScene, accent: Color) {
    // Logo lockup (Title board): bone planet cut by an ink slash and an accent slash.
    val mark = scene.logoMark
    val unit = mark / 64f
    val origin = Offset(scene.logo.x, scene.logo.y - mark * 0.5f)
    drawCircle(Kk.Bone, 21f * unit, Offset(origin.x + 32f * unit, origin.y + 32f * unit))
    drawLine(Kk.Ink, Offset(origin.x + 4f * unit, origin.y + 46.6f * unit), Offset(origin.x + 60f * unit, origin.y + 17.4f * unit), 7f * unit)
    drawLine(accent, Offset(origin.x - 2f * unit, origin.y + 49.7f * unit), Offset(origin.x + 66f * unit, origin.y + 14.3f * unit), 3.5f * unit)
    val wordmark = drawKkText(
        text, "KINETICKK", text.typography.wideStyle(displaySize(text, scene.wordmarkSize), lineHeightEm = 1f),
        origin.x + mark + 12f * unit * 64f / 30f, scene.logo.y, Kk.Bone, valign = KkVAlign.CENTER,
    )
    val headerLeft = origin.x + mark + 12f * unit * 64f / 30f + wordmark.size.width + 16f * density
    // Chips from real Home data: Matter and the Rebirth rank (the Codex and the Workshop facts hold the
    // counts). The least important chip (the right end) is dropped when the row would reach the wordmark.
    val language = text.language
    val compact = scene.compactChips
    var count = scene.chipCount
    while (count > 0 && chipsWidth(model, text, scene, count, language) > scene.chipsRight - headerLeft) count--
    var right = scene.chipsRight
    for (index in count - 1 downTo 0) {
        // The chips keep their own meaning whatever item is selected: Matter is yours (volt), Rebirth gold.
        val chipAccent = if (index == 1) Kk.Gold else text.roles.you
        right -= drawChip(text, right, scene, homeChip(model, language, compact, index), chipAccent) + 8f * density
    }
}

/** A header chip; [crown] draws the gold Ascendant crown before the label. */
private class HomeChip(val gem: Boolean, val value: String, val label: String?, val labelFirst: Boolean, val crown: Boolean = false)

/**
 * The header chips. The gold Rebirth chip is the rank badge: "Rank 5" (the selected tier is in the
 * Rebirth facts), or "Ascendant 10" under the gold crown once every tier is cleared.
 */
private fun homeChip(model: HomeUiModel, language: AppLanguage, compact: Boolean, index: Int): HomeChip = when (index) {
    0 -> HomeChip(true, homeNumber(model.totalMatter, language), if (compact) null else language.text(SessionRedesignText.MATTER), false)
    else -> HomeChip(false, model.rebirthRank.toString(),
        language.text(if (model.ascendant) SessionRedesignText.ASCENDANT else SessionRedesignText.RANK), true, crown = model.ascendant)
}

private fun DrawScope.chipsWidth(model: HomeUiModel, text: CanvasTextMeasurer, scene: HomeScene, count: Int, language: AppLanguage): Float {
    var total = 0f
    for (index in 0 until count) total += chipWidth(text, scene, homeChip(model, language, scene.compactChips, index)) + 8f * density
    return total
}

private fun chipValueStyle(text: CanvasTextMeasurer, scene: HomeScene) =
    text.typography.condStyle(if (scene.compactChips) 16f else 21f, trackingEm = 0.02f, tabular = true, lineHeightEm = 1f)

private fun chipLabelStyle(text: CanvasTextMeasurer) = text.typography.monoStyle(9f, trackingEm = 0.06f)

private fun DrawScope.chipWidth(text: CanvasTextMeasurer, scene: HomeScene, chip: HomeChip): Float {
    val value = measureKkText(text, chip.value, chipValueStyle(text, scene), uppercase = true)
    val label = chip.label?.let { measureKkText(text, it, chipLabelStyle(text), uppercase = true) }
    val gap = 8f * density
    val gemSize = if (scene.compactChips) 10f else 14f
    return 10f * density + (if (chip.gem) gemSize * density + gap else 0f) + (if (chip.crown) gemSize * density + gap else 0f) +
        value.size.width + (if (label != null) gap + label.size.width else 0f) + 14f * density
}

/** Header chip (`.chip`) right-aligned at [right]; returns its width. */
private fun DrawScope.drawChip(text: CanvasTextMeasurer, right: Float, scene: HomeScene, chip: HomeChip, accent: Color): Float {
    val valueLayout = measureKkText(text, chip.value, chipValueStyle(text, scene), uppercase = true)
    val labelLayout = chip.label?.let { measureKkText(text, it, chipLabelStyle(text), uppercase = true) }
    val gap = 8f * density
    val gemSize = if (scene.compactChips) 10f else 14f
    val width = chipWidth(text, scene, chip)
    val left = right - width
    val top = scene.chipsTop
    val bottom = top + scene.chipHeight
    val cut = 8f * density
    val path = chipPath
    path.rewind()
    path.moveTo(left + cut, top)
    path.lineTo(right, top)
    path.lineTo(right - cut, bottom)
    path.lineTo(left, bottom)
    path.close()
    drawPath(path, Kk.Ink2)
    var x = left + 10f * density
    val cy = (top + bottom) * 0.5f
    if (chip.gem) {
        drawKkGem(Offset(x + gemSize * density * 0.5f, cy), accent, gemSize)
        x += gemSize * density + gap
    }
    if (chip.crown) {
        val radius = gemSize * density * 0.5f
        translate(x + radius, cy) { drawPath(KkMutatorSigilPaths.of(KkMutatorSigil.ASCENDANT, radius), accent) }
        x += gemSize * density + gap
    }
    if (chip.labelFirst && labelLayout != null) {
        drawKkText(labelLayout, x, cy, Kk.Mute, valign = KkVAlign.CENTER)
        x += labelLayout.size.width + gap
        drawKkText(valueLayout, x, cy, accent, valign = KkVAlign.CENTER)
    } else {
        drawKkText(valueLayout, x, cy, Kk.Bone, valign = KkVAlign.CENTER)
        x += valueLayout.size.width + gap
        if (labelLayout != null) drawKkText(labelLayout, x, cy, Kk.Mute, valign = KkVAlign.CENTER)
    }
    return width
}

private val chipPath = Path()

private fun DrawScope.drawMenu(
    model: HomeUiModel,
    tinted: CanvasTextMeasurer,
    layout: HomeLayoutGeometry,
    motion: HomeMenuMotion,
    activeTarget: HomeLayoutTarget,
    textScale: Float,
) {
    val scene = layout.scene
    val language = tinted.language
    val menu = motion.menuMeasurer(tinted)
    HomeMenuTargets.forEachIndexed { index, target ->
        val label = language.text(HomeMenuLabels[index])
        val facts = motion.menuFacts(model, index, language, textScale)
        // One column: the item stays inside its row (see homeMenuPlacement).
        val left = if (scene.menuColumns == 1) motion.menuPlacement(menu, layout, index, label, facts, density) else layout.bounds(target).left
        val showSub = scene.menuColumns == 1 && motion.menuShowsSub(index)
        val selection = if (target == activeTarget) 1f else 0f
        drawHomeMenuItem(menu, layout, index, label, facts, left, showSub, motion.menuStampSize, selection, motion.trailTime)
    }
}

/**
 * Menu item [index] as Home draws it with the menu measurer [menu]: from [left] (one column: see
 * [homeMenuPlacement], which also decides [showSub]) at its row's center, its stamp at [stampSize]
 * (see [homeMenuStampSize]), [selection] 0..1.
 */
internal fun DrawScope.drawHomeMenuItem(
    menu: CanvasTextMeasurer,
    layout: HomeLayoutGeometry,
    index: Int,
    label: String,
    facts: HomeFacts,
    left: Float,
    showSub: Boolean,
    stampSize: Float,
    selection: Float,
    time: Float,
) {
    val scene = layout.scene
    val bounds = layout.bounds(HomeMenuTargets[index])
    if (scene.menuColumns == 1) {
        drawKkMenuItem(
            menu, left, bounds.center.y, label,
            selection = selection,
            time = time,
            fontSize = displaySize(menu, scene.menuFontSize),
            // The board keeps the stamp right after the label; the sub value joins after it when selected.
            stamp = facts.stamp,
            sub = if (showSub && selection > 0.01f) facts.sub else null,
            edgeRight = size.width,
            stampFontSize = stampSize,
        )
    } else {
        // Two-column phone menus have no room for the stamp or the sub value. The clip keeps a selected
        // item to its row and column, but leaves room left of the column for its slide and echo so the
        // slab keeps its skewed cut and its lead before the label; the speed lines, which would cross
        // the neighbouring column, stay out.
        val room = (HOME_MENU_SLIDE + HOME_MENU_ECHO_LEFT) * scene.menuFontSize / 64f + density
        clipRect(max(0f, bounds.left - room), bounds.top - 2f * density, bounds.right + 4f * density, bounds.bottom + 2f * density) {
            drawKkMenuItem(
                menu, left, bounds.center.y, label,
                selection = selection,
                time = time,
                fontSize = displaySize(menu, scene.menuFontSize),
                edgeRight = bounds.right,
            )
        }
    }
}

/**
 * The form name at its display size [sizePx], or smaller when that is wider than [room] px (a long
 * locked name on the smallest landscape screens): shrunk to fit whole, never cut.
 */
internal fun homeFormNameLayout(text: CanvasTextMeasurer, name: String, sizePx: Float, room: Float, density: Float): TextLayoutResult {
    val size = max(1f, sizePx / density / text.scale)
    val full = measureKkText(text, name, text.typography.wideStyle(size), uppercase = true)
    if (full.size.width <= room) return full
    return measureKkText(text, name, text.typography.wideStyle(size * max(0.5f, room / full.size.width) * 0.99f), uppercase = true)
}

/** The form [name] from the scene's anchor, ending before [nameRight] (see [homeFormNameRight]). */
internal fun DrawScope.drawHomeFormName(text: CanvasTextMeasurer, scene: HomeScene, name: String, nameRight: Float, color: Color) {
    drawKkText(
        homeFormNameLayout(text, name, scene.formNameSize, nameRight - scene.formNameLeft, density),
        scene.formNameLeft, scene.formNameCenterY, color, valign = KkVAlign.CENTER,
    )
}

/** Measured facts card: the label and big value on the left, then one row per fact. */
internal class HomeFactsCardLayout(
    val label: TextLayoutResult,
    val big: TextLayoutResult,
    val left: Float,
    val columnLeft: Float,
    val columnRight: Float,
    val rows: List<Pair<TextLayoutResult, TextLayoutResult>>,
)

/**
 * Lays out the facts card inside [rect] (px, [unit] = px per board px) left of [infoLeft]. All rows
 * share one text size that shrinks (to 70 %) until the widest key/value pair fits the column, so
 * the key and value are shown whole; only content that cannot fit even then is truncated.
 */
internal fun homeFactsCardLayout(
    text: CanvasTextMeasurer,
    rect: Rect,
    facts: HomeFacts,
    unit: Float,
    infoLeft: Float,
    density: Float,
): HomeFactsCardLayout {
    fun display(px: Float) = max(1f, px / density / text.scale)
    val label = measureKkText(text, facts.label, text.typography.labelStyle(15f), uppercase = true)
    val bigSize = if (facts.big.length > 6) 24f else 40f
    val maxLeft = min(240f * unit, (infoLeft - rect.left) * 0.5f)
    val big = measureKkText(text, facts.big, text.typography.wideStyle(display(bigSize * unit), tabular = true), uppercase = true, maxWidth = maxLeft)
    val leftWidth = max(110f * unit, max(label.size.width.toFloat(), big.size.width.toFloat()))
    val left = rect.left + 22f * unit
    val columnLeft = left + leftWidth + 26f * unit
    val columnRight = infoLeft - 22f * unit
    val column = columnRight - columnLeft
    val rows = if (column <= 0f || facts.facts.isEmpty()) emptyList() else {
        val gap = 12f * density
        val widest = facts.facts.maxOf { (key, value) ->
            measureKkText(text, key, text.typography.monoStyle(11f), uppercase = true).size.width +
                measureKkText(text, value, text.typography.condStyle(21f, tabular = true), uppercase = true).size.width + gap
        }
        // A small margin keeps rounding of the scaled glyph advances from truncating a pair.
        val fit = (column * 0.97f / widest).coerceIn(0.7f, 1f)
        val keyStyle = text.typography.monoStyle(11f * fit)
        val valueStyle = text.typography.condStyle(21f * fit, tabular = true)
        facts.facts.map { (key, value) ->
            val keyWidth = measureKkText(text, key, keyStyle, uppercase = true).size.width.toFloat()
            val valueWidth = measureKkText(text, value, valueStyle, uppercase = true).size.width.toFloat()
            val keyMax = if (keyWidth + valueWidth + gap <= column + 1f) keyWidth else min(keyWidth, column * 0.45f)
            val keyLayout = measureKkText(text, key, keyStyle, uppercase = true, maxWidth = keyMax + 2f)
            val valueLayout = measureKkText(text, value, valueStyle, uppercase = true, maxWidth = column - keyLayout.size.width - gap + 2f)
            keyLayout to valueLayout
        }
    }
    return HomeFactsCardLayout(label, big, left, columnLeft, columnRight, rows)
}

private fun DrawScope.drawFactsCard(
    text: CanvasTextMeasurer,
    rect: Rect,
    facts: HomeFacts,
    layout: HomeLayoutGeometry,
    state: HomeDrawState,
    motion: HomeMenuMotion,
    accent: Color,
) {
    val u = layout.scene.unit
    drawPath(motion.factsPath(rect, 16f * u), Kk.Ink2.copy(alpha = 0.92f))
    val info = layout.info(HomeInfoTarget.FACTS)
    val infoLeft = info?.bounds?.left ?: (rect.right - 44f * u)
    val card = homeFactsCardLayout(text, rect, facts, u, infoLeft, density)
    val blockHeight = card.label.kkBoxHeight + 8f * u + card.big.kkBoxHeight
    var y = rect.center.y - blockHeight * 0.5f
    drawKkText(card.label, card.left, y, accent)
    y += card.label.kkBoxHeight + 8f * u
    drawKkText(card.big, card.left, y, Kk.Bone)
    if (card.rows.isNotEmpty()) {
        val rowHeight = card.rows.maxOf { (key, value) -> max(key.kkBoxHeight, value.kkBoxHeight) }
        val rowGap = 6f * density
        var rowY = rect.center.y - (rowHeight * card.rows.size + rowGap * (card.rows.size - 1)) * 0.5f
        card.rows.forEach { (key, value) ->
            val center = rowY + rowHeight * 0.5f
            drawKkText(key, card.columnLeft, center, Kk.Mute, valign = KkVAlign.CENTER)
            drawKkText(value, card.columnRight, center, Kk.Bone, align = KkAlign.END, valign = KkVAlign.CENTER)
            rowY += rowHeight + rowGap
        }
    }
    if (info != null) {
        drawKkInfoButton(text, info.bounds, active = state.activeInfo == HomeInfoTarget.FACTS || state.openInfo == HomeInfoTarget.FACTS)
    }
}

/** The current character: its name, its (!) (the mechanic) and the Characters card that changes it. */
private fun DrawScope.drawFormPanel(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    layout: HomeLayoutGeometry,
    state: HomeDrawState,
    motion: HomeMenuMotion,
) {
    val scene = layout.scene
    val language = text.language
    val definition = model.coreShape(model.coreShape)
    val info = layout.info(HomeInfoTarget.FORM)
    motion.formPanel(layout, density)
    drawHomeFormName(text, scene, definition.displayName.localizedContent(language), motion.formNameRight, Kk.Bone)
    if (info != null) {
        drawKkInfoButton(text, info.bounds, active = state.activeInfo == HomeInfoTarget.FORM || state.openInfo == HomeInfoTarget.FORM)
    }
    val card = layout.bounds(HomeLayoutTarget.HANGAR)
    drawKkButton(
        text, card, language.text(SessionText.CHARACTERS),
        variant = KkButtonVariant.GHOST,
        size = if (card.height < 52f * density) KkButtonSize.SM else KkButtonSize.MD,
        hover = if (state.activeTarget == HomeLayoutTarget.HANGAR) 1f else 0f,
        icon = model.coreShape.formIcon(),
    )
}

internal fun DrawScope.drawLegal(text: CanvasTextMeasurer, layout: HomeLayoutGeometry) {
    val scene = layout.scene
    val legal = homeLegalLayout(text, layout, density)
    var right = scene.legalRight
    for (index in legal.parts.indices.reversed()) {
        val part = legal.parts[index]
        drawKkText(part, right, scene.legalBaseline, Kk.Mute, align = KkAlign.END, valign = KkVAlign.BASELINE)
        right -= part.size.width + legal.gap
    }
}

/** The legal line as separate mono texts (no separators) and the gap between them (px). */
internal class HomeLegalLayout(val parts: List<TextLayoutResult>, val gap: Float) {
    val width: Float get() = parts.sumOf { it.size.width.toDouble() }.toFloat() + gap * (parts.size - 1).coerceAtLeast(0)
}

/**
 * GPL notices kept visible on Home: copyright, license, no-warranty note, source and version on
 * desktop; copyright, license and source on phones. The size shrinks (to 70 %) to fit between
 * [HomeScene.legalLeft] and [HomeScene.legalRight].
 */
internal fun homeLegalLayout(text: CanvasTextMeasurer, layout: HomeLayoutGeometry, density: Float): HomeLegalLayout {
    val scene = layout.scene
    val parts = if (layout.mode == HomeLayoutMode.REGULAR) LegalRegular else LegalCompact
    val strings = parts.map { text.language.text(it) }
    val available = scene.legalRight - scene.legalLeft
    fun measure(sizePx: Float): HomeLegalLayout {
        val style = text.typography.monoStyle(max(1f, sizePx / density / text.scale))
        val measured = strings.mapIndexed { index, value -> measureKkText(text, value, style, uppercase = parts[index] != SessionRedesignText.SOURCE) }
        return HomeLegalLayout(measured, sizePx * 2f)
    }
    val full = measure(scene.legalSize)
    if (full.width <= available) return full
    return measure(scene.legalSize * (available / full.width * 0.98f).coerceIn(0.7f, 1f))
}

private val LegalRegular = listOf(
    SessionRedesignText.COPYRIGHT, SessionRedesignText.LICENSE, SessionRedesignText.NO_WARRANTY,
    SessionRedesignText.SOURCE, SessionRedesignText.VERSION,
)
private val LegalCompact = listOf(SessionRedesignText.COPYRIGHT, SessionRedesignText.LICENSE, SessionRedesignText.SOURCE)
