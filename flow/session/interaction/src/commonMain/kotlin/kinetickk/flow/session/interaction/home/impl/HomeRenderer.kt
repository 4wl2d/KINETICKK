// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkHomePalette
import kinetickk.foundation.design.KkTime
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkHatch
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkTile
import kinetickk.foundation.design.kkStampSize
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kotlin.math.max
import kotlin.math.min

/** Seconds per Core revolution around the singularity (ambient loops never run faster). */
internal const val HOME_ORBIT_SECONDS: Float = KkTime.AmbientMin / 1000f

/** cos 66°: the hero plane is tilted back 66° (CSS `rotateX(66deg)`, orthographic). */
internal const val TILT = 0.40673664f
internal const val SIN_TILT = 0.9135454f

/** Board angles (degrees, clockwise from 12 o'clock) of the four enemies placed on the orbit. */
internal val EnemyAngles = floatArrayOf(60f, 150f, 250f, 320f)

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
 * upgrades paid in Matter), gold Rebirth (the Rebirth screen keeps its
 * per-tier themes), mute the system.
 * The Codex sweeps through the element hues instead (see [HomeCodexSweep]); its static entry is Cryo.
 */
internal fun menuAccent(target: HomeLayoutTarget): Color = when (target) {
    HomeLayoutTarget.WORKSHOP -> KkHomePalette.WORKSHOP.accent
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

/** Interaction state the Canvas reflects (hover/focus come from the semantic overlay). */
internal class HomeDrawState(
    val activeTarget: HomeLayoutTarget,
    val focusedTarget: HomeLayoutTarget?,
    val openInfo: HomeInfoTarget?,
    val activeInfo: HomeInfoTarget?,
    /** The player's text size setting (the measurer carries the UI factor, [homeUiScale]). */
    val textScale: Float = HOME_DEFAULT_TEXT_SCALE,
    /** The Quit button is hovered or focused. */
    val quitActive: Boolean = false,
    /** The next runs are Baby mode runs: a soft hero, the stamp under it and a lit toggle. */
    val easy: Boolean = false,
    /** The Baby mode toggle is hovered or focused: it is outlined and shows its tooltip. */
    val runModeActive: Boolean = false,
    /** The Baby mode toggle was just tapped: its tooltip shows until the next tap elsewhere. */
    val runModeTip: Boolean = false,
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
