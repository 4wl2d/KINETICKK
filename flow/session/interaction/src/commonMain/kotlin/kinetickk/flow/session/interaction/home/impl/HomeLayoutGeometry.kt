// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.content.api.CoreShape
import kotlin.math.max
import kotlin.math.min

internal enum class HomeLayoutMode {
    REGULAR,
    COMPACT_PORTRAIT,
    COMPACT_LANDSCAPE,
}

internal enum class HomeLayoutTarget {
    CORE_ORB,
    CORE_PRISM,
    CORE_SHARD,
    CORE_RING,
    CORE_DIAMOND,
    CORE_TESSERACT,
    START,
    LAB,
    ARMORY,
    REBIRTH,
    CODEX,
    SETTINGS,
}

/** Drawn (!) buttons: the facts card (selected menu item) and the form showcase. */
internal enum class HomeInfoTarget { FACTS, FORM }

internal class HomeActionBounds(
    val target: HomeLayoutTarget,
    val bounds: Rect,
)

/** A (!) button: [bounds] is the drawn 24 dp square, [touch] its expanded press area. */
internal class HomeInfoBounds(
    val target: HomeInfoTarget,
    val bounds: Rect,
    val touch: Rect,
)

/**
 * Draw anchors shared by the renderer (px). Board px from the 1440 × 810 reference frame map to
 * px through [unit]; font sizes are px before the text-size setting.
 */
internal class HomeScene(
    val unit: Float,
    val gridSpacing: Float,
    val logo: Offset,
    val logoMark: Float,
    val wordmarkSize: Float,
    val chipsRight: Float,
    val chipsTop: Float,
    val chipHeight: Float,
    val chipCount: Int,
    val heroCenter: Offset,
    val heroRadius: Float,
    val wordLeft: Float,
    val wordTop: Float,
    val wordSize: Float,
    val menuFontSize: Float,
    val menuColumns: Int,
    val facts: Rect?,
    val formNameLeft: Float,
    val formNameCenterY: Float,
    val formNameSize: Float,
    val formDescription: Rect?,
    val legalRight: Float,
    val legalBaseline: Float,
    val legalSize: Float,
    /** The legal line (right-aligned at [legalRight]) must stay right of this x. */
    val legalLeft: Float,
)

internal class HomeLayoutGeometry(
    val mode: HomeLayoutMode,
    val actions: List<HomeActionBounds>,
    val infos: List<HomeInfoBounds>,
    val scene: HomeScene,
) {
    fun bounds(target: HomeLayoutTarget): Rect =
        requireNotNull(actions.firstOrNull { it.target == target }) { "Missing Home target $target" }.bounds

    fun info(target: HomeInfoTarget): HomeInfoBounds? = infos.firstOrNull { it.target == target }
}

internal val HomeCoreTargets = listOf(
    HomeLayoutTarget.CORE_ORB,
    HomeLayoutTarget.CORE_PRISM,
    HomeLayoutTarget.CORE_SHARD,
    HomeLayoutTarget.CORE_RING,
    HomeLayoutTarget.CORE_DIAMOND,
    HomeLayoutTarget.CORE_TESSERACT,
)

/** The game's menu order (labels and targets are the game's, never the board's). */
internal val HomeMenuTargets = listOf(
    HomeLayoutTarget.START,
    HomeLayoutTarget.LAB,
    HomeLayoutTarget.ARMORY,
    HomeLayoutTarget.REBIRTH,
    HomeLayoutTarget.CODEX,
    HomeLayoutTarget.SETTINGS,
)

internal fun homeLayoutGeometry(width: Float, height: Float, density: Float): HomeLayoutGeometry {
    val scale = density.coerceAtLeast(1f)
    val w = width / scale
    val h = height / scale
    val compactPhone = w < 900f || h < 560f
    val mode = when {
        !compactPhone -> HomeLayoutMode.REGULAR
        w <= h -> HomeLayoutMode.COMPACT_PORTRAIT
        else -> HomeLayoutMode.COMPACT_LANDSCAPE
    }
    fun px(value: Float): Float = value * scale
    fun rect(left: Float, top: Float, right: Float, bottom: Float) = Rect(px(left), px(top), px(right), px(bottom))
    fun info(target: HomeInfoTarget, left: Float, centerY: Float): HomeInfoBounds {
        val bounds = rect(left, centerY - 12f, left + 24f, centerY + 12f)
        return HomeInfoBounds(target, bounds, rect(left - 10f, centerY - 22f, left + 34f, centerY + 22f))
    }
    val actions = ArrayList<HomeActionBounds>(12)
    val infos = ArrayList<HomeInfoBounds>(2)
    val scene = when (mode) {
        HomeLayoutMode.REGULAR -> {
            // Home.png anchors: header and hero from the left, menu and form panel from the right,
            // the facts card and form panel pinned to the bottom, the menu centered vertically.
            val s = min(w / 1440f, h / 810f)
            fun lx(x: Float) = x * s
            fun rx(x: Float) = w - (1440f - x) * s
            fun ty(y: Float) = y * s
            fun by(y: Float) = h - (810f - y) * s
            val centerOffset = (h - 810f * s) * 0.5f
            fun cy(y: Float) = y * s + centerOffset
            var menuBottom = 0f
            HomeMenuTargets.forEachIndexed { index, target ->
                val top = cy(124f + index * 74f)
                val row = Rect(rx(880f - index * 12f), top, w - 16f, top + 74f * s)
                actions += HomeActionBounds(target, rect(row.left, row.top, row.right, row.bottom))
                homeSelectedMenuFootprint(row, 64f * s).forEach { menuBottom = max(menuBottom, it.bottom) }
            }
            // The form name (and its (!)) rises from the Deploy board's line by up to 12 px, as far as the
            // lowest selected menu item leaves the name's line box (0.65 em each way) clear, so a
            // description wrapped to two lines still clears the name's descenders (homeFormDescriptionSpan).
            val formNameSize = 30f * s
            val formNameY = (menuBottom + formNameSize * 0.65f + 0.5f).coerceIn(by(608f), by(620f))
            HomeCoreTargets.forEachIndexed { index, target ->
                val left = rx(826f + index * 96f)
                actions += HomeActionBounds(target, rect(left, by(680f), left + 86f * s, by(756f)))
            }
            infos += info(HomeInfoTarget.FORM, rx(1368f), formNameY)
            // The facts card keeps at least 480 px (board 560) on smaller desktops, short of the form panel.
            val factsRight = max(lx(616f), min(lx(56f) + 480f, rx(826f) - 24f))
            infos += info(HomeInfoTarget.FACTS, factsRight - 44f * s, by(686f))
            HomeScene(
                unit = px(s),
                gridSpacing = px(48f * s),
                logo = Offset(px(lx(56f)), px(ty(42f))),
                logoMark = px(30f * s),
                wordmarkSize = px(19f * s),
                chipsRight = px(rx(1392f)),
                chipsTop = px(ty(25f)),
                chipHeight = px(34f * s),
                chipCount = 4,
                heroCenter = Offset(px(lx(340f)), px(cy(430f))),
                heroRadius = px(260f * s),
                wordLeft = px(lx(24f)),
                wordTop = px(by(600f)),
                wordSize = px(200f * s),
                menuFontSize = px(64f * s),
                menuColumns = 1,
                facts = rect(lx(56f), by(640f), factsRight, by(732f)),
                formNameLeft = px(rx(826f)),
                formNameCenterY = px(formNameY),
                formNameSize = px(formNameSize),
                formDescription = rect(rx(826f), by(642f), rx(1392f), by(666f)),
                legalRight = px(rx(1392f)),
                legalBaseline = px(by(788f)),
                legalSize = px(11f * s),
                legalLeft = px(factsRight + 24f),
            )
        }
        HomeLayoutMode.COMPACT_LANDSCAPE -> {
            // Mobile-Home.png: hero left, stepped menu right, forms along the bottom-left.
            val side = (w * 0.04f).coerceIn(16f, 44f)
            val menuLeft = w * 0.58f
            val legalBaseline = h - 5f
            // The lowest (Settings) row is the widest and passes over the legal line. Selected, its echo
            // ends (49 + 40 turn) × font / 64 below the row's center, dropped by the −2° turn of the
            // row's left half (see homeSelectedMenuFootprint): the rows shrink from 52 toward 48 px,
            // then the font, until that edge ends 2 px above the notices' tallest glyphs.
            val lowestWidth = w - side - (menuLeft - (HomeMenuTargets.size - 1) * 9f)
            val lowestCenter = legalBaseline - HOME_LEGAL_ASCENT_EM * COMPACT_LEGAL_SIZE - 2f - HOME_MENU_TURN * lowestWidth * 0.5f
            val echoDrop = (49f + 40f * HOME_MENU_TURN) / 64f
            val menuTop = 48f
            // 36 px from 660 px wide; narrower screens scale it so Russian labels with a stamp still fit.
            val widthFont = 36f * min(1f, w / 660f)
            val rowHeight = ((lowestCenter - echoDrop * widthFont - menuTop) / 5.5f).coerceIn(48f, 52f)
            // Never below the two-column phone font: that floor binds only under 356 px tall, or past
            // about 1,400 px wide at 360 px tall (no target size).
            val menuFont = min(widthFont, (lowestCenter - menuTop - 5.5f * rowHeight) / echoDrop).coerceAtLeast(28f)
            val rows = HomeMenuTargets.indices.map { index ->
                val top = menuTop + index * rowHeight
                Rect(menuLeft - index * 9f, top, w - side, top + rowHeight)
            }
            HomeMenuTargets.forEachIndexed { index, target ->
                val row = rows[index]
                actions += HomeActionBounds(target, rect(row.left, row.top, row.right, row.bottom))
            }
            // Form tiles end 8 px short of every selected menu slab and speed-line trail that reaches
            // their band: one row while the tiles stay 56 wide, else two rows of three.
            fun tilesRight(top: Float): Float = rows.map { homeSelectedMenuExtent(it, menuFont) }
                .filter { it.bottom > top }.minOfOrNull { it.left - 8f } ?: (rows.minOf { it.left } - 12f)
            val singleHeight = if (h >= 380f) 50f else 48f
            val singleTop = h - 10f - singleHeight
            val singleWidth = (tilesRight(singleTop) - side - 30f) / 6f
            val singleRow = singleWidth >= 56f
            val tileHeight = if (singleRow) singleHeight else 48f
            val tilesTop = if (singleRow) singleTop else h - 10f - tileHeight * 2f - 6f
            val tileWidth = min(84f, if (singleRow) singleWidth else (tilesRight(tilesTop) - side - 12f) / 3f)
            HomeCoreTargets.forEachIndexed { index, target ->
                val column = if (singleRow) index else index % 3
                val row = if (singleRow) 0 else index / 3
                val left = side + column * (tileWidth + 6f)
                val top = tilesTop + row * (tileHeight + 6f)
                actions += HomeActionBounds(target, rect(left, top, left + tileWidth, top + tileHeight))
            }
            val tilesRight = side + (if (singleRow) 6 else 3) * (tileWidth + 6f) - 6f
            val nameCenter = tilesTop - 24f
            // The (!) leads the form name at the left edge, clear of the selected menu slab and trail.
            infos += info(HomeInfoTarget.FORM, side, nameCenter)
            val regionTop = 44f
            val regionBottom = nameCenter - 16f
            val heroRadius = min(135f, min((menuLeft - 45f - side) * 0.42f, (regionBottom - regionTop) * 0.9f))
            val wordSize = 104f * h / 390f
            HomeScene(
                unit = px(heroRadius / 260f),
                gridSpacing = px(32f),
                logo = Offset(px(side), px(26f)),
                logoMark = px(22f),
                wordmarkSize = px(14f),
                chipsRight = px(w - side),
                chipsTop = px(12f),
                chipHeight = px(28f),
                chipCount = 2,
                heroCenter = Offset(px(side + (menuLeft - 45f - side) * 0.44f), px((regionTop + regionBottom) * 0.5f)),
                heroRadius = px(heroRadius),
                wordLeft = px(12f),
                wordTop = px(h - wordSize * 1.02f),
                wordSize = px(wordSize),
                menuFontSize = px(menuFont),
                menuColumns = 1,
                facts = null,
                formNameLeft = px(side + 36f),
                formNameCenterY = px(nameCenter),
                formNameSize = px(14f),
                formDescription = null,
                legalRight = px(w - side),
                legalBaseline = px(legalBaseline),
                legalSize = px(COMPACT_LEGAL_SIZE),
                legalLeft = px(tilesRight + 12f),
            )
        }
        HomeLayoutMode.COMPACT_PORTRAIT -> {
            // Portrait adaptation: header, hero, form showcase, then the menu at thumb height.
            val side = 16f
            val columns = if (h < 660f) 2 else 1
            val rowHeight = if (columns == 1) 52f else 50f
            val menuTop = h - 16f - rowHeight * (6 / columns)
            val columnWidth = (w - side * 2f - 8f * (columns - 1)) / columns
            // One column: rows step 6 px left going down, and the lowest row sits so far right that
            // its selected speed lines end at the side margin. The font (36 px from 418 px wide)
            // scales down on narrower phones so a Russian label with its stamp still fits the row.
            // Two columns: 28 px from a 166 px column (the widest label, Russian ПЕРЕРОЖДЕНИЕ, takes
            // 163 px of it with its lead) scales down on narrower phones, so every label stays whole
            // in its column and a selected right-column item slides and casts its echo clear of the
            // label left of it.
            val menuFont = if (columns == 1) 36f * min(1f, w / 418f) else 28f * min(1f, columnWidth / 166f)
            val menuReach = HOME_MENU_REACH * menuFont / 64f
            HomeMenuTargets.forEachIndexed { index, target ->
                if (columns == 1) {
                    val top = menuTop + index * rowHeight
                    val left = side + menuReach + (HomeMenuTargets.size - 1 - index) * 6f
                    actions += HomeActionBounds(target, rect(left, top, w - side, top + rowHeight))
                } else {
                    val left = side + (index % 2) * (columnWidth + 8f)
                    val top = menuTop + (index / 2) * rowHeight
                    actions += HomeActionBounds(target, rect(left, top, left + columnWidth, top + rowHeight))
                }
            }
            val tileWidth = (w - side * 2f - 30f) / 6f
            val tileHeight = 56f
            val tilesTop = menuTop - (if (columns == 1) 20f else 16f) - tileHeight
            HomeCoreTargets.forEachIndexed { index, target ->
                val left = side + index * (tileWidth + 6f)
                actions += HomeActionBounds(target, rect(left, tilesTop, left + tileWidth, tilesTop + tileHeight))
            }
            val nameCenter = tilesTop - 24f
            infos += info(HomeInfoTarget.FORM, w - side - 24f, nameCenter)
            val regionTop = 52f
            val regionBottom = nameCenter - 20f
            val heroRadius = min(w * 0.42f, (regionBottom - regionTop) * 0.5f)
            val wordSize = w * 0.26f
            HomeScene(
                unit = px(heroRadius / 260f),
                gridSpacing = px(32f),
                logo = Offset(px(side), px(28f)),
                logoMark = px(22f),
                wordmarkSize = px(14f),
                chipsRight = px(w - side),
                chipsTop = px(14f),
                chipHeight = px(28f),
                chipCount = 2,
                heroCenter = Offset(px(w * 0.5f), px((regionTop + regionBottom) * 0.5f)),
                heroRadius = px(heroRadius),
                wordLeft = px(8f),
                wordTop = px(max(regionTop, nameCenter - 26f - wordSize)),
                wordSize = px(wordSize),
                menuFontSize = px(menuFont),
                menuColumns = columns,
                facts = null,
                formNameLeft = px(side),
                formNameCenterY = px(nameCenter),
                formNameSize = px(16f),
                formDescription = null,
                legalRight = px(w - side),
                legalBaseline = px(h - 4f),
                legalSize = px(8f),
                legalLeft = px(side),
            )
        }
    }
    return HomeLayoutGeometry(mode, actions, infos, scene)
}

/**
 * Right end (px) of the form name: up to its (!) when that follows the name; in landscape (the (!)
 * leads the name) up to 8 px short of any menu row, selected slab or speed line on the name's band.
 */
internal fun homeFormNameRight(layout: HomeLayoutGeometry, density: Float): Float {
    val scene = layout.scene
    val info = layout.info(HomeInfoTarget.FORM)?.bounds
    if (info != null && info.left > scene.formNameLeft) return info.left - 12f * density
    val top = scene.formNameCenterY - scene.formNameSize * 0.65f
    val bottom = scene.formNameCenterY + scene.formNameSize * 0.65f
    var right = scene.legalRight
    HomeMenuTargets.forEach { target ->
        val bounds = layout.bounds(target)
        (listOf(bounds) + homeSelectedMenuFootprint(bounds, scene.menuFontSize)).forEach { area ->
            if (area.top < bottom && area.bottom > top) right = min(right, area.left - 8f * density)
        }
    }
    return right
}

/**
 * How far (board px at a 64 px menu font) a selected menu item reaches left of its row: it moves
 * 26 px left, and its longest speed line (120 px at 99 % when the lines come to rest, never longer
 * while they draw in) starts 20 px left of it. The item sizes it from its label as drawn, which keeps
 * the layout's menu font at every text size (see [homeMenuMeasurerScale]).
 */
internal const val HOME_MENU_REACH = 165f

/** How far (board px at a 64 px menu font) a selected menu item moves left. */
internal const val HOME_MENU_SLIDE = 26f

/** How far (board px at a 64 px menu font) a selected menu item's echo reaches left of its slab. */
internal const val HOME_MENU_ECHO_LEFT = 14f

/**
 * Area a selected menu item covers (px) for a menu font of [fontSizePx]: its slab, echo and speed
 * lines reach [HOME_MENU_REACH] left, and its slab and echo add 6 px above and 16 px below the row
 * (`.mi` in kk.css, scaled by font / 64).
 */
internal fun homeSelectedMenuExtent(bounds: Rect, fontSizePx: Float): Rect {
    val k = fontSizePx / 64f
    return Rect(bounds.left - HOME_MENU_REACH * k, bounds.top - 6f * k, bounds.right, bounds.bottom + 16f * k)
}

/**
 * The two parts of a selected menu item at rest (px), tighter than [homeSelectedMenuExtent]: the
 * slab with its echo (from 40 px left of the row, 39 px above to 49 px below its center line) and
 * the speed lines (from [HOME_MENU_REACH] left to the row, 13 px above to 14 px below the center
 * line). The item turns −2° about its middle (at most the row's middle): what lies left of it
 * drops, what lies right of it (the slab out to the screen edge) rises, by sin 2° per px.
 */
internal fun homeSelectedMenuFootprint(bounds: Rect, fontSizePx: Float): List<Rect> {
    val k = fontSizePx / 64f
    val center = bounds.center.y
    val rise = HOME_MENU_TURN * (bounds.width + 64f * k)
    return listOf(
        Rect(bounds.left - 40f * k, center - 39f * k - rise, bounds.right, center + 49f * k + HOME_MENU_TURN * (bounds.width * 0.5f + 40f * k)),
        Rect(bounds.left - HOME_MENU_REACH * k, center - 13f * k, bounds.left,
            center + 14f * k + HOME_MENU_TURN * (bounds.width * 0.5f + HOME_MENU_REACH * k)),
    )
}

/**
 * How far the form name's lowest ink reaches below its center line, in em of its size: the wide
 * face's Cyrillic descenders (Д, Ц, Щ), with their antialiased edge (checked with the bundled font in
 * the Home pixel tests).
 */
internal const val HOME_FORM_NAME_INK_EM = 0.6f

/** sin 2°, rounded up: how far a point of the turned menu item moves per px from its middle. */
private const val HOME_MENU_TURN = 0.035f

/** The phone legal line's mono size (px before density; see [homeLegalLayout]). */
private const val COMPACT_LEGAL_SIZE = 8f

/**
 * Reserve a full em above the legal baseline: Linux rasterization reaches 8 px for the
 * bundled 8 px mono text, while macOS ink can be shorter. Keep the menu clear on both.
 */
internal const val HOME_LEGAL_ASCENT_EM = 1f

internal fun HomeLayoutTarget.toHomeAction(): HomeAction = when (this) {
    HomeLayoutTarget.CORE_ORB -> HomeAction.SelectCoreShape(CoreShape.ORB)
    HomeLayoutTarget.CORE_PRISM -> HomeAction.SelectCoreShape(CoreShape.PRISM)
    HomeLayoutTarget.CORE_SHARD -> HomeAction.SelectCoreShape(CoreShape.SHARD)
    HomeLayoutTarget.CORE_RING -> HomeAction.SelectCoreShape(CoreShape.RING)
    HomeLayoutTarget.CORE_DIAMOND -> HomeAction.SelectCoreShape(CoreShape.DIAMOND)
    HomeLayoutTarget.CORE_TESSERACT -> HomeAction.SelectCoreShape(CoreShape.TESSERACT)
    HomeLayoutTarget.START -> HomeAction.StartRun
    HomeLayoutTarget.LAB -> HomeAction.OpenLab
    HomeLayoutTarget.ARMORY -> HomeAction.OpenArmory
    HomeLayoutTarget.REBIRTH -> HomeAction.OpenRebirth
    HomeLayoutTarget.CODEX -> HomeAction.OpenCodex
    HomeLayoutTarget.SETTINGS -> HomeAction.OpenSettings
}

internal fun HomeLayoutTarget.coreShapeOrNull(): CoreShape? = when (this) {
    HomeLayoutTarget.CORE_ORB -> CoreShape.ORB
    HomeLayoutTarget.CORE_PRISM -> CoreShape.PRISM
    HomeLayoutTarget.CORE_SHARD -> CoreShape.SHARD
    HomeLayoutTarget.CORE_RING -> CoreShape.RING
    HomeLayoutTarget.CORE_DIAMOND -> CoreShape.DIAMOND
    HomeLayoutTarget.CORE_TESSERACT -> CoreShape.TESSERACT
    else -> null
}
