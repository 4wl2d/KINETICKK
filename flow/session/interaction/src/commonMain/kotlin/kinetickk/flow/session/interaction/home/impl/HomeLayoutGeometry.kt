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
            HomeMenuTargets.forEachIndexed { index, target ->
                val top = cy(124f + index * 74f)
                actions += HomeActionBounds(target, rect(rx(880f - index * 12f), top, w - 16f, top + 74f * s))
            }
            HomeCoreTargets.forEachIndexed { index, target ->
                val left = rx(826f + index * 96f)
                actions += HomeActionBounds(target, rect(left, by(680f), left + 86f * s, by(756f)))
            }
            infos += info(HomeInfoTarget.FORM, rx(1368f), by(620f))
            infos += info(HomeInfoTarget.FACTS, lx(572f), by(686f))
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
                facts = rect(lx(56f), by(640f), lx(616f), by(732f)),
                formNameLeft = px(rx(826f)),
                formNameCenterY = px(by(620f)),
                formNameSize = px(30f * s),
                formDescription = rect(rx(826f), by(642f), rx(1392f), by(666f)),
                legalRight = px(rx(1392f)),
                legalBaseline = px(by(788f)),
                legalSize = px(11f * s),
            )
        }
        HomeLayoutMode.COMPACT_LANDSCAPE -> {
            // Mobile-Home.png: hero left, stepped menu right, forms along the bottom-left.
            val side = (w * 0.04f).coerceIn(16f, 44f)
            val rowHeight = ((h - 60f) / 6f).coerceIn(48f, 52f)
            val menuLeft = w * 0.58f
            HomeMenuTargets.forEachIndexed { index, target ->
                val top = 50f + index * rowHeight
                actions += HomeActionBounds(target, rect(menuLeft - index * 9f, top, w - side, top + rowHeight))
            }
            val available = menuLeft - 5f * 9f - 12f - side
            val singleRow = (available - 30f) / 6f >= 56f
            val tileWidth = if (singleRow) min(84f, (available - 30f) / 6f) else min(84f, (available - 12f) / 3f)
            val tileHeight = if (singleRow && h >= 380f) 50f else 48f
            val tilesTop = if (singleRow) h - 10f - tileHeight else h - 10f - tileHeight * 2f - 6f
            HomeCoreTargets.forEachIndexed { index, target ->
                val column = if (singleRow) index else index % 3
                val row = if (singleRow) 0 else index / 3
                val left = side + column * (tileWidth + 6f)
                val top = tilesTop + row * (tileHeight + 6f)
                actions += HomeActionBounds(target, rect(left, top, left + tileWidth, top + tileHeight))
            }
            val tilesRight = side + (if (singleRow) 6 else 3) * (tileWidth + 6f) - 6f
            val nameCenter = tilesTop - 24f
            infos += info(HomeInfoTarget.FORM, tilesRight - 24f, nameCenter)
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
                menuFontSize = px(36f),
                menuColumns = 1,
                facts = null,
                formNameLeft = px(side),
                formNameCenterY = px(nameCenter),
                formNameSize = px(14f),
                formDescription = null,
                legalRight = px(w - side),
                legalBaseline = px(h - 5f),
                legalSize = px(8f),
            )
        }
        HomeLayoutMode.COMPACT_PORTRAIT -> {
            // Portrait adaptation: header, hero, form showcase, then the menu at thumb height.
            val side = 16f
            val columns = if (h < 660f) 2 else 1
            val rowHeight = if (columns == 1) 52f else 50f
            val menuTop = h - 16f - rowHeight * (6 / columns)
            val columnWidth = (w - side * 2f - 8f * (columns - 1)) / columns
            HomeMenuTargets.forEachIndexed { index, target ->
                if (columns == 1) {
                    val top = menuTop + index * rowHeight
                    actions += HomeActionBounds(target, rect(side + 30f - index * 6f, top, w - side, top + rowHeight))
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
                menuFontSize = px(if (columns == 1) 36f else 28f),
                menuColumns = columns,
                facts = null,
                formNameLeft = px(side),
                formNameCenterY = px(nameCenter),
                formNameSize = px(16f),
                formDescription = null,
                legalRight = px(w - side),
                legalBaseline = px(h - 4f),
                legalSize = px(8f),
            )
        }
    }
    return HomeLayoutGeometry(mode, actions, infos, scene)
}

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
