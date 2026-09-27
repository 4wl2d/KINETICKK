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
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkHomePalette
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkTime
import kinetickk.foundation.design.KkTooltipPlacement
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
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
 * Palette recolor (350 ms Out), menu selection slabs (240 ms Pull) and form tiles are
 * interpolated here from the frame clock.
 */
internal class HomeMenuMotion {
    private val menu = FloatArray(HomeMenuTargets.size).also { it[0] = 1f }
    private val tiles = FloatArray(CoreShape.entries.size)
    private var activeIndex = 0
    private var accentFrom = KkHomePalette.START.accent
    private var accentTo = accentFrom
    private var accentElapsedMs = KkTime.PaletteShift.toFloat()
    var accent: Color = accentFrom
        private set

    fun advance(target: HomeLayoutTarget, coreShape: CoreShape, deltaSeconds: Float) {
        val delta = deltaSeconds.coerceIn(0f, MAX_HOME_PRESENTATION_FRAME_DELTA_SECONDS)
        val index = HomeMenuTargets.indexOf(target).coerceAtLeast(0)
        selectedSeconds = if (index == activeIndex) selectedSeconds + delta else 0f
        activeIndex = index
        val step = delta * 1000f / KkTime.MenuSelect
        for (index in menu.indices) {
            menu[index] = if (index == activeIndex) min(1f, menu[index] + step) else max(0f, menu[index] - step)
        }
        for (index in tiles.indices) {
            tiles[index] = if (index == coreShape.ordinal) min(1f, tiles[index] + step) else max(0f, tiles[index] - step)
        }
        val next = menuAccent(target)
        if (next != accentTo) {
            accentFrom = accent
            accentTo = next
            accentElapsedMs = 0f
        }
        accentElapsedMs = min(KkTime.PaletteShift.toFloat(), accentElapsedMs + delta * 1000f)
        accent = lerp(accentFrom, accentTo, KkEase.Out.transform(accentElapsedMs / KkTime.PaletteShift))
    }

    private var selectedSeconds = HOME_TRAIL_REST_SECONDS

    /** Speed-line clock of the selected item: the lines draw in once, then hold (never loop). */
    val trailTime: Float get() = homeTrailTime(selectedSeconds)

    /** Selection slab progress of menu item [index] (Pull overshoot while growing). */
    fun menuSelection(index: Int): Float =
        if (index == activeIndex) KkEase.Pull.transform(menu[index]) else KkEase.Out.transform(menu[index])

    fun tileSelection(shape: CoreShape): Float = KkEase.Pull.transform(tiles[shape.ordinal])

    // Caches (draw thread only): the accent-tinted measurer, strokes, paths and the trail brush.
    private var tintedBase: CanvasTextMeasurer? = null
    private var tintedAccent = Color.Unspecified
    private var tinted: CanvasTextMeasurer? = null

    /** Home palettes recolor the `you` accents of the menu components (the board overrides --volt). */
    fun tinted(measurer: CanvasTextMeasurer): CanvasTextMeasurer {
        val cached = tinted
        if (cached != null && tintedBase === measurer && tintedAccent == accent) return cached
        return CanvasTextMeasurer(measurer.delegate, measurer.scale, measurer.language, measurer.typography,
            measurer.roles.copy(you = accent)).also {
            tinted = it
            tintedBase = measurer
            tintedAccent = accent
        }
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
        val cached = brush
        if (cached != null && brushAccent == accent && brushCenter == center) return cached
        return Brush.sweepGradient(
            0f to accent.copy(alpha = 0f),
            TRAIL_START / 360f to accent.copy(alpha = 0f),
            (TRAIL_START + TRAIL_SWEEP) / 360f to accent,
            (TRAIL_START + TRAIL_SWEEP + 1f) / 360f to accent.copy(alpha = 0f),
            1f to accent.copy(alpha = 0f),
            center = center,
        ).also {
            brush = it
            brushAccent = accent
            brushCenter = center
        }
    }

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
    val enemies: List<Path> = listOf(
        gridPath(12f, 3f, 21f, 20f, 3f, 20f),
        gridPath(12f, 2f, 22f, 12f, 12f, 22f, 2f, 12f),
        gridPath(7f, 3f, 17f, 3f, 22f, 12f, 17f, 21f, 7f, 21f, 2f, 12f),
        gridPath(12f, 3f, 21f, 10f, 17.5f, 21f, 6.5f, 21f, 3f, 10f),
    )
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

    private var labelKeyModel: Any? = null
    private var labelKeyMeasurer: CanvasTextMeasurer? = null
    private var labelKeyWidth = 0f

    private val labelSizes = FloatArray(CoreShape.entries.size)

    /** Label size of each form tile (see [homeTileLabelSize]), cached per model, measurer and tile. */
    fun tileLabelSize(model: HomeUiModel, text: CanvasTextMeasurer, tile: Rect, density: Float, shape: CoreShape): Float {
        if (labelKeyModel !== model || labelKeyMeasurer !== text || labelKeyWidth != tile.width) {
            labelKeyModel = model
            labelKeyMeasurer = text
            labelKeyWidth = tile.width
            CoreShape.entries.forEach { entry ->
                labelSizes[entry.ordinal] = homeTileLabelSize(model.coreShape(entry).displayName.localizedContent(text.language), tile, density) { name, size ->
                    measureKkText(text, name, text.typography.labelStyle(size), uppercase = true).size.width
                }
            }
        }
        return labelSizes[shape.ordinal]
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

/** Cut of a form tile's slab (px): smaller tiles use the smaller cut. */
internal fun homeTileCut(tile: Rect, density: Float): Float = (if (tile.width < 70f * density) 8f else 12f) * density

/** Horizontal padding kept on both sides of a tile label, inside the slanted face (px per dp). */
internal const val HOME_TILE_LABEL_PADDING_DP = 6f

/**
 * Label size (13 down to 7 px) at which [name] fits its tile's face with padding: the slanted face
 * near the label is the tile width minus one cut. 0 hides the label (icon only) when even 7 px
 * does not fit or the tile is too short.
 */
internal fun homeTileLabelSize(name: String, tile: Rect, density: Float, width: (String, Float) -> Int): Float {
    if (tile.height < 60f * density) return 0f
    val available = tile.width - homeTileCut(tile, density) - 2f * HOME_TILE_LABEL_PADDING_DP * density
    return HomeTileLabelSizes.firstOrNull { size -> width(name, size) <= available } ?: 0f
}

private val HomeTileLabelSizes = listOf(13f, 12f, 11f, 10f, 9f, 8f, 7f)

/**
 * Seconds into the menu trail cycle (`kk-trail`: 0.9 s, lines delayed 0 / 0.15 / 0.3 s, opaque
 * between 18 % and 72 % of a cycle) at which all three speed lines are fully drawn.
 */
internal const val HOME_TRAIL_REST_SECONDS: Float = 0.55f

/** The trail runs from selection until [HOME_TRAIL_REST_SECONDS] and then rests there. */
internal fun homeTrailTime(secondsSinceSelected: Float): Float = secondsSinceSelected.coerceIn(0f, HOME_TRAIL_REST_SECONDS)

/** Home palette accent of the selected/hovered menu item (applies to the items the game has). */
internal fun menuAccent(target: HomeLayoutTarget): Color = when (target) {
    HomeLayoutTarget.LAB -> KkHomePalette.LAB.accent
    HomeLayoutTarget.ARMORY -> KkHomePalette.ARMORY.accent
    HomeLayoutTarget.REBIRTH -> KkHomePalette.REBIRTH.accent
    HomeLayoutTarget.CODEX -> KkHomePalette.CODEX.accent
    HomeLayoutTarget.SETTINGS -> KkHomePalette.SETTINGS.accent
    else -> KkHomePalette.START.accent
}

internal val HomeMenuLabels = listOf(
    SessionText.START_RUN,
    SessionText.LAB,
    SessionText.ARMORY,
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

internal fun homeFacts(model: HomeUiModel, target: HomeLayoutTarget, language: AppLanguage, textScale: Float): HomeFacts {
    val label = language.text(HomeMenuLabels[HomeMenuTargets.indexOf(target).coerceAtLeast(0)])
    val matter = homeNumber(model.totalMatter, language)
    val weapon = model.startingWeaponName?.localizedContent(language)
    val directive = model.rebirthProfile.directive
    return when (target) {
        HomeLayoutTarget.LAB -> HomeFacts(
            label,
            if (model.labMaxRanks > 0) "${model.labRanks}/${model.labMaxRanks}" else matter,
            buildList {
                add(language.text(SessionRedesignText.MATTER) to matter)
                if (model.labUpgradeCount > 0) {
                    add(language.text(SessionRedesignText.MAXED) to "${model.labMaxedUpgrades}/${model.labUpgradeCount}")
                }
            },
            language.text(SessionRedesignText.LAB_INFO),
            if (model.labMaxRanks > 0) "${model.labRanks}/${model.labMaxRanks}" else null,
            null,
        )
        HomeLayoutTarget.ARMORY -> HomeFacts(
            label,
            "${model.unlockedWeaponCount}/${model.weaponCount}",
            buildList {
                if (weapon != null) add(language.text(SessionText.WEAPON) to weapon)
                add(language.text(SessionRedesignText.MATTER) to matter)
            },
            language.text(SessionRedesignText.ARMORY_INFO),
            "${model.unlockedWeaponCount}/${model.weaponCount}",
            if (model.weaponUnlockAffordable) language.text(SessionRedesignText.UNLOCKABLE) else null,
        )
        HomeLayoutTarget.REBIRTH -> HomeFacts(
            label,
            model.rebirthLevel.toString(),
            listOf(
                language.text(SessionRedesignText.DIRECTIVE) to directive.displayName.localizedContent(language),
                language.text(SessionRedesignText.STATUS) to
                    language.text(if (model.canRebirth) SessionRedesignText.READY else SessionText.LOCKED_STATE),
            ),
            language.text(SessionRedesignText.REBIRTH_INFO),
            null,
            if (model.canRebirth) language.text(SessionRedesignText.READY) else null,
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
            directive.displayName.localizedContent(language),
            buildList {
                add(language.text(SessionText.REBIRTH) to model.rebirthLevel.toString())
                add(language.text(SessionRedesignText.FORM) to model.coreShape(model.coreShape).displayName.localizedContent(language))
            },
            directive.description.localizedContent(language),
            language.text(SessionText.REBIRTH_LEVEL, model.rebirthLevel),
            null,
        )
    }
}

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

/** The (!) text of the form showcase: the mechanic, or the unlock goal and progress when locked. */
internal fun homeFormInfo(model: HomeUiModel, shape: CoreShape, language: AppLanguage): String {
    val definition = model.coreShape(shape)
    return if (model.isCoreShapeUnlocked(shape)) {
        definition.mechanicDescription.localizedContent(language)
    } else {
        definition.unlockDescription.localizedContent(language) + "\n" + language.text(
            SessionText.UNLOCK_PROGRESS,
            coreShapeUnlockProgress(definition, model.characterAchievements),
            definition.unlockTarget,
        )
    }
}

internal fun CoreShape.formIcon(): KkIcon = when (this) {
    CoreShape.ORB -> KkIcon.FORMS_CIRCLE
    CoreShape.PRISM -> KkIcon.FORMS_SQUARE
    CoreShape.SHARD -> KkIcon.FORMS_TRIANGLE
    CoreShape.RING -> KkIcon.FORMS_RING
    CoreShape.DIAMOND -> KkIcon.FORMS_DIAMOND
    CoreShape.TESSERACT -> KkIcon.FORMS_TESSERACT
}

/** Interaction state the Canvas reflects (hover/focus come from the semantic overlay). */
internal class HomeDrawState(
    val previewShape: CoreShape?,
    val activeTarget: HomeLayoutTarget,
    val focusedTarget: HomeLayoutTarget?,
    val hoveredTarget: HomeLayoutTarget?,
    val openInfo: HomeInfoTarget?,
    val activeInfo: HomeInfoTarget?,
)

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
    val facts = motion.facts(model, state.activeTarget, language, text.scale)
    drawRect(kkMix(Kk.Ink, accent, 0.07f))
    drawKkGrid(Rect(Offset.Zero, size), Kk.Bone.copy(alpha = 0.045f), scene.gridSpacing / density)
    drawBackgroundWord(text, facts.label, scene, accent)
    drawHero(model, text, time, scene, state, motion)
    drawHeader(model, text, scene, accent)
    drawMenu(model, tinted, layout, motion)
    scene.facts?.let { drawFactsCard(tinted, it, facts, layout, state, motion, accent) }
    drawFormPanel(model, tinted, layout, state, motion)
    drawLegal(text, layout)
    val open = state.openInfo ?: state.activeInfo
    if (open != null) {
        val info = layout.info(open)
        if (info != null) {
            val content = when (open) {
                HomeInfoTarget.FACTS -> facts.info
                HomeInfoTarget.FORM -> homeFormInfo(model, state.previewShape ?: model.coreShape, language)
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
        drawCore(model, text, core, k, perspective, time, state.previewShape, motion)
    }
    drawSingularity(center, k, time, roles.threat, motion)
    drawEnemies(text, time, center, k, spin, accent, far = false, motion)
    if (!coreFar) {
        drawTether(core, center, k, accent, roles.threat)
        drawCore(model, text, core, k, perspective, time, state.previewShape, motion)
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

private fun DrawScope.drawCore(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    center: Offset,
    k: Float,
    perspective: Float,
    time: Float,
    previewShape: CoreShape?,
    motion: HomeMenuMotion,
) {
    val shape = previewShape ?: model.coreShape
    val unlocked = model.isCoreShapeUnlocked(shape)
    val radius = max(32f * k, density * 18f) * perspective
    val accent = motion.accent
    // Glow (box-shadow 46 px accent @85 %) as stacked translucent rings, never a real-time blur.
    for (index in 0 until 5) {
        drawCircle(accent.copy(alpha = 0.85f * (0.13f - index * 0.025f)), radius + (6f + index * 8f) * k, center)
    }
    val spin = when (shape) {
        CoreShape.PRISM -> time / 60f * 360f
        CoreShape.TESSERACT -> time / 40f * 360f
        else -> 0f
    }
    val path = motion.shapePath
    rotate(spin % 360f, center) {
        buildCoreShape(path, shape, center, radius)
        val outline = kkStroke(max(10f * k, density * 5f))
        drawPath(path, Kk.Ink, style = outline)
        if (shape == CoreShape.RING || shape == CoreShape.TESSERACT) {
            val stroke = kkStroke(if (shape == CoreShape.RING) radius * 0.33f else radius * 0.12f)
            drawPath(path, if (unlocked) Kk.Bone else Kk.Mute2, style = stroke)
            if (shape == CoreShape.RING) {
                drawCircle(Kk.Ink, radius * 0.24f + max(5f * k, density * 2.5f), center)
                drawCircle(if (unlocked) Kk.Bone else Kk.Mute2, radius * 0.24f, center)
            }
        } else {
            drawPath(path, if (unlocked) Kk.Bone else Kk.Ink3)
            if (!unlocked) drawKkHatch(path)
        }
    }
    if (!unlocked) drawKkIcon(KkIcon.SYSTEM_LOCKED, center, radius * 0.9f, Kk.Mute)
}

/** Form silhouettes (Deploy board shapes) scaled so the disc form has [radius]. */
private fun buildCoreShape(path: Path, shape: CoreShape, center: Offset, radius: Float) {
    path.rewind()
    val u = radius / 42f
    fun p(x: Float, y: Float) = Offset(center.x + (x - 50f) * u, center.y + (y - 50f) * u)
    when (shape) {
        CoreShape.ORB -> path.addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
        CoreShape.RING -> {
            val r = 36f * u
            path.addOval(Rect(center.x - r, center.y - r, center.x + r, center.y + r))
        }
        CoreShape.PRISM -> path.addRect(Rect(p(22f, 22f), p(78f, 78f)))
        CoreShape.SHARD -> {
            val a = p(50f, 10f)
            val b = p(92f, 84f)
            val c = p(8f, 84f)
            path.moveTo(a.x, a.y)
            path.lineTo(b.x, b.y)
            path.lineTo(c.x, c.y)
            path.close()
        }
        CoreShape.DIAMOND -> {
            val a = p(50f, 6f)
            val b = p(94f, 50f)
            val c = p(50f, 94f)
            val d = p(6f, 50f)
            path.moveTo(a.x, a.y)
            path.lineTo(b.x, b.y)
            path.lineTo(c.x, c.y)
            path.lineTo(d.x, d.y)
            path.close()
        }
        CoreShape.TESSERACT -> {
            path.addRect(Rect(p(14f, 14f), p(64f, 64f)))
            path.addRect(Rect(p(36f, 36f), p(86f, 86f)))
            for ((x, y) in TesseractEdges) {
                val a = p(x, y)
                val b = p(x + 22f, y + 22f)
                path.moveTo(a.x, a.y)
                path.lineTo(b.x, b.y)
            }
        }
    }
}

private val TesseractEdges = listOf(14f to 14f, 64f to 14f, 14f to 64f, 64f to 64f)

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
    // Chips from real Home data: matter, rebirth, codex, weapons. The least important chips (the
    // right end) are dropped when the row would reach the wordmark.
    val language = text.language
    val compact = scene.chipCount < 4
    var count = scene.chipCount
    while (count > 0 && chipsWidth(model, text, scene, count, language) > scene.chipsRight - headerLeft) count--
    var right = scene.chipsRight
    for (index in count - 1 downTo 0) {
        right -= drawChip(text, right, scene, homeChip(model, language, compact, index), accent) + 8f * density
    }
}

private class HomeChip(val gem: Boolean, val value: String, val label: String?, val labelFirst: Boolean)

private fun homeChip(model: HomeUiModel, language: AppLanguage, compact: Boolean, index: Int): HomeChip = when (index) {
    0 -> HomeChip(true, homeNumber(model.totalMatter, language), if (compact) null else language.text(SessionRedesignText.MATTER), false)
    1 -> HomeChip(false, model.rebirthLevel.toString(), language.text(SessionText.REBIRTH), true)
    2 -> HomeChip(false, "${model.discoveredItemCount}/${model.itemCount}", language.text(SessionText.CODEX), false)
    else -> HomeChip(false, "${model.unlockedWeaponCount}/${model.weaponCount}", language.text(SessionText.WEAPONS_TITLE), false)
}

private fun DrawScope.chipsWidth(model: HomeUiModel, text: CanvasTextMeasurer, scene: HomeScene, count: Int, language: AppLanguage): Float {
    var total = 0f
    for (index in 0 until count) total += chipWidth(text, scene, homeChip(model, language, scene.chipCount < 4, index)) + 8f * density
    return total
}

private fun chipValueStyle(text: CanvasTextMeasurer, scene: HomeScene) =
    text.typography.condStyle(if (scene.chipCount < 4) 16f else 21f, trackingEm = 0.02f, tabular = true, lineHeightEm = 1f)

private fun chipLabelStyle(text: CanvasTextMeasurer) = text.typography.monoStyle(9f, trackingEm = 0.06f)

private fun DrawScope.chipWidth(text: CanvasTextMeasurer, scene: HomeScene, chip: HomeChip): Float {
    val value = measureKkText(text, chip.value, chipValueStyle(text, scene), uppercase = true)
    val label = chip.label?.let { measureKkText(text, it, chipLabelStyle(text), uppercase = true) }
    val gap = 8f * density
    val gemSize = if (scene.chipCount < 4) 10f else 14f
    return 10f * density + (if (chip.gem) gemSize * density + gap else 0f) + value.size.width +
        (if (label != null) gap + label.size.width else 0f) + 14f * density
}

/** Header chip (`.chip`) right-aligned at [right]; returns its width. */
private fun DrawScope.drawChip(text: CanvasTextMeasurer, right: Float, scene: HomeScene, chip: HomeChip, accent: Color): Float {
    val valueLayout = measureKkText(text, chip.value, chipValueStyle(text, scene), uppercase = true)
    val labelLayout = chip.label?.let { measureKkText(text, it, chipLabelStyle(text), uppercase = true) }
    val gap = 8f * density
    val gemSize = if (scene.chipCount < 4) 10f else 14f
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
) {
    val scene = layout.scene
    val language = tinted.language
    HomeMenuTargets.forEachIndexed { index, target ->
        val bounds = layout.bounds(target)
        val label = language.text(HomeMenuLabels[index])
        val facts = motion.menuFacts(model, index, language, tinted.scale)
        val selection = motion.menuSelection(index)
        val draw: DrawScope.() -> Unit = {
            drawKkMenuItem(
                tinted, bounds.left, bounds.center.y, label,
                selection = selection,
                time = motion.trailTime,
                fontSize = displaySize(tinted, scene.menuFontSize),
                // The board keeps the stamp right after the label; the sub value joins when selected.
                // Two-column phone menus have no room for either.
                stamp = if (scene.menuColumns == 1) facts.stamp else null,
                sub = if (scene.menuColumns == 1 && selection > 0.01f) facts.sub else null,
                edgeRight = if (scene.menuColumns == 1) size.width else bounds.right,
            )
        }
        if (scene.menuColumns == 1) draw() else clipRect(bounds.left - 4f * density, bounds.top - 2f * density, bounds.right + 4f * density, bounds.bottom + 2f * density) { draw() }
    }
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

private fun DrawScope.drawFormPanel(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    layout: HomeLayoutGeometry,
    state: HomeDrawState,
    motion: HomeMenuMotion,
) {
    val scene = layout.scene
    val language = text.language
    val shape = state.previewShape ?: model.coreShape
    val unlocked = model.isCoreShapeUnlocked(shape)
    val definition = model.coreShape(shape)
    val info = layout.info(HomeInfoTarget.FORM)
    val nameRight = homeFormNameRight(layout, density)
    val name = if (unlocked) definition.displayName.localizedContent(language) else language.text(SessionText.UNKNOWN_CORE)
    drawKkText(
        text, name, text.typography.wideStyle(displaySize(text, scene.formNameSize)),
        scene.formNameLeft, scene.formNameCenterY, if (unlocked) Kk.Bone else Kk.Mute,
        valign = KkVAlign.CENTER, uppercase = true, maxWidth = max(1f, nameRight - scene.formNameLeft),
    )
    if (info != null) {
        drawKkInfoButton(text, info.bounds, active = state.activeInfo == HomeInfoTarget.FORM || state.openInfo == HomeInfoTarget.FORM)
    }
    val description = scene.formDescription
    if (description != null && unlocked) {
        val layoutText = measureKkText(
            text, definition.mechanicDescription.localizedContent(language), text.typography.bodyStyle(16f),
            maxWidth = description.width,
        )
        drawKkText(layoutText, description.left, description.center.y, Kk.Bone2, valign = KkVAlign.CENTER)
    }
    HomeCoreTargets.forEach { target ->
        val tileShape = requireNotNull(target.coreShapeOrNull())
        val bounds = layout.bounds(target)
        val labelSize = motion.tileLabelSize(model, text, bounds, density, tileShape)
        val tileUnlocked = model.isCoreShapeUnlocked(tileShape)
        val label = if (tileUnlocked && labelSize > 0f) model.coreShape(tileShape).displayName.localizedContent(language) else null
        val iconSize = min(24f, bounds.height / density * 0.42f)
        drawKkTile(
            text, bounds,
            label = label,
            labelSize = if (labelSize > 0f) labelSize else 13f,
            icon = tileShape.formIcon(),
            selected = motion.tileSelection(tileShape),
            hovered = state.hoveredTarget == target || state.previewShape == tileShape,
            locked = !tileUnlocked,
            cutDp = homeTileCut(bounds, density) / density,
            iconSizeDp = iconSize,
            focused = state.focusedTarget == target,
        )
        if (!tileUnlocked) {
            val lift = -8f * density * motion.tileSelection(tileShape)
            drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(bounds.right - 14f * density, bounds.top + 11f * density + lift), 12f * density, Kk.Mute)
        }
    }
}

private fun DrawScope.drawLegal(text: CanvasTextMeasurer, layout: HomeLayoutGeometry) {
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
