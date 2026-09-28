// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.gameplay.interaction.rewards.overlayUiTextScale
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.interaction.terminal.WeaponSlotPlacementMemo
import kinetickk.ball.gameplay.interaction.terminal.drawPlacedWeaponSlot
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.roundToLong

/** Tone of a run statistic value in the build overview. */
internal enum class PauseValueTone { BONE, YOU, THREAT }

/** One run statistic panel ("Elites 2"). */
internal class PauseRunStat(val label: String, val value: String, val tone: PauseValueTone)

/**
 * The pause build overview of one committed frame: form, rebirth and level tags, the run's
 * weapon with its mastery, the relic matrix with its synergies, stats and run statistics.
 */
internal class PauseBuildOverview(
    val heading: String,
    val form: String,
    val formIcon: KkIcon,
    val rebirth: String,
    val levelLabel: String,
    val level: Int,
    val weaponLabel: String,
    val weaponIcon: KkIcon,
    val weaponName: String,
    val mastery: String,
    val masteryTier: WeaponMastery,
    val weaponLevel: Int,
    val weaponLevelText: String,
    val relicsLabel: String,
    val relics: List<OverlayRelicSlot?>,
    val links: List<OverlaySynergyLink>,
    val synergyNames: List<String>,
    val synergyDescriptions: List<String>,
    val statsLabel: String,
    val stats: List<OverlayStat>,
    val runLabel: String,
    val run: List<PauseRunStat>,
) {
    // Mobile-Pause shows six stats (impact, weapon power, magnetism, cooling, integrity, dash)
    // and four run panels (destroyed, elites, matter, chain); selected once, not per frame.
    val compactStats: List<OverlayStat> = stats.filterIndexed { index, _ -> index in CompactStatIndices }
    val compactRun: List<PauseRunStat> = run.filterIndexed { index, _ -> index in CompactRunIndices }
}

internal fun GameplayRenderModel.pauseBuildOverview(language: AppLanguage): PauseBuildOverview {
    val relics = List(content.relicPolicy.maxSlots) { index ->
        equippedRelics.getOrNull(index)?.let { OverlayRelicSlot(it.id, content.relic(it.id).aspect, it.rank) }
    }
    val links = overlaySynergyLinks(relics.map { it?.id }, content)
    val stats = runStatistics
    return PauseBuildOverview(
        heading = language.text(OverlayRedesignText.Build),
        form = content.coreShape(coreShape).displayName.localizedContent(language),
        formIcon = coreShape.overlayIcon(),
        rebirth = language.text(OverlayRedesignText.RebirthTag, rebirthLevel),
        levelLabel = language.text(OverlayRedesignText.LevelLabel),
        level = level,
        weaponLabel = language.text(OverlayRedesignText.Weapon),
        weaponIcon = weapon.overlayIcon(),
        weaponName = currentWeaponDefinition.name.localizedContent(language),
        mastery = currentWeaponMastery.displayLabel.localizedContent(language),
        masteryTier = currentWeaponMastery,
        weaponLevel = weaponLevel,
        weaponLevelText = overlayLevel(weaponLevel, language),
        relicsLabel = language.text(OverlayRedesignText.Relics) + " " + equippedRelics.size + "/" + content.relicPolicy.maxSlots,
        relics = relics,
        links = links,
        synergyNames = links.map { it.definition.name.localizedContent(language) },
        synergyDescriptions = links.map { it.definition.description.localizedContent(language) },
        statsLabel = language.text(OverlayRedesignText.Stats),
        stats = overlayBuildStats(language),
        runLabel = language.text(GameplayText.DetailedStatistics),
        run = listOf(
            PauseRunStat(language.text(OverlayRedesignText.RunTimeShort), formatRunTime(elapsed), PauseValueTone.BONE),
            PauseRunStat(language.text(OverlayRedesignText.DestroyedShort), overlayGrouped(kills.toLong(), language), PauseValueTone.BONE),
            PauseRunStat(language.text(OverlayRedesignText.ElitesShort), overlayGrouped(stats.eliteKills.toLong(), language), PauseValueTone.THREAT),
            PauseRunStat(language.text(OverlayRedesignText.MatterShort), "+" + overlayGrouped(runMatter, language), PauseValueTone.YOU),
            PauseRunStat(language.text(OverlayRedesignText.ChainShort), "×" + stats.bestCombo, PauseValueTone.YOU),
            PauseRunStat(language.text(OverlayRedesignText.DamageShort), overlayCompact(stats.damageDealt.roundToLong(), language), PauseValueTone.BONE),
        ),
    )
}

/**
 * Built once per paused frame and language: pause redraws every frame, and a paused run keeps
 * its clock, build and totals, so an equal frame reuses the overview without allocating.
 */
private object PauseBuildMemo {
    private var engine: GameplayRenderModel? = null
    private var language: AppLanguage? = null
    private var overview: PauseBuildOverview? = null

    fun of(engine: GameplayRenderModel, language: AppLanguage): PauseBuildOverview {
        val cached = overview
        val previous = this.engine
        if (cached != null && previous != null && this.language == language && (previous === engine || previous.sameBuild(engine))) {
            this.engine = engine
            return cached
        }
        return engine.pauseBuildOverview(language).also {
            this.engine = engine
            this.language = language
            overview = it
        }
    }

    private fun GameplayRenderModel.sameBuild(other: GameplayRenderModel): Boolean =
        content === other.content && elapsed == other.elapsed && level == other.level && kills == other.kills &&
            weapon == other.weapon && weaponLevel == other.weaponLevel && runMatter == other.runMatter &&
            rebirthLevel == other.rebirthLevel && coreShape == other.coreShape && equippedRelics == other.equippedRelics &&
            runStatistics == other.runStatistics && damageMultiplier == other.damageMultiplier &&
            effectiveWeaponPower == other.effectiveWeaponPower && mass == other.mass && magnetStrength == other.magnetStrength &&
            coolingRate == other.coolingRate && maxHp == other.maxHp && dashImpulse == other.dashImpulse &&
            critChance == other.critChance && overdriveGain == other.overdriveGain && pickupRadius == other.pickupRadius
}

internal fun DrawScope.drawPauseBuild(engine: GameplayRenderModel, measurer: CanvasTextMeasurer, layout: PauseLayoutGeometry, time: Float) {
    val overview = PauseBuildMemo.of(engine, measurer.language)
    val ui = PauseMeasurers.ui(measurer, engine.settings.textScale)
    val display = PauseMeasurers.display(measurer, engine.settings.textScale)
    when (layout.mode) {
        GameplayLayoutMode.REGULAR -> drawRegularBuild(overview, ui, display, layout.build, layout.unit, time)
        GameplayLayoutMode.COMPACT_LANDSCAPE -> drawCompactBuild(overview, ui, display, layout.build, layout.unit, time, portrait = false)
        GameplayLayoutMode.COMPACT_PORTRAIT -> drawCompactBuild(overview, ui, display, layout.build, layout.unit, time, portrait = true)
    }
}

/**
 * Pause measurers derived from the host's: UI text follows the text-size setting relative to the
 * default (board size at 125 %); display type (title, frozen clock, menu items, level numerals)
 * ignores it. Rebuilt only when the host measurer or the setting changes.
 */
internal object PauseMeasurers {
    private var base: CanvasTextMeasurer? = null
    private var setting = Float.NaN
    private var ui: CanvasTextMeasurer? = null
    private var display: CanvasTextMeasurer? = null

    fun ui(base: CanvasTextMeasurer, setting: Float): CanvasTextMeasurer = update(base, setting).let { requireNotNull(ui) }

    fun display(base: CanvasTextMeasurer, setting: Float): CanvasTextMeasurer = update(base, setting).let { requireNotNull(display) }

    private fun update(base: CanvasTextMeasurer, setting: Float) {
        if (this.base === base && this.setting == setting) return
        this.base = base
        this.setting = setting
        ui = CanvasTextMeasurer(base.delegate, overlayUiTextScale(setting), base.language, base.typography, base.roles)
        display = CanvasTextMeasurer(base.delegate, 1f, base.language, base.typography, base.roles)
    }
}

/** What a pause text is, for the layout probe. */
internal enum class PauseTextKind {
    TITLE, TIMER, HEADING, TAG, LABEL, WEAPON_NAME, MASTERY, LEVEL, SYNERGY_NAME, SYNERGY_DESCRIPTION,
    STAT_LABEL, STAT_VALUE, RUN_LABEL, RUN_VALUE,
}

/** One laid-out pause text: where it was drawn ([rect], px) and the width it had to fit ([box]). */
internal class PauseTextRecord(val kind: PauseTextKind, val layout: TextLayoutResult, val rect: Rect, val box: Float)

/** Test probe: while [records] is set, every pause text is recorded as it is drawn. */
internal object PauseTextProbe {
    var records: MutableList<PauseTextRecord>? = null
}

/** Draws a laid-out pause text and reports it to the probe with the width it had to fit. */
internal fun DrawScope.drawPauseText(
    kind: PauseTextKind,
    layout: TextLayoutResult,
    x: Float,
    y: Float,
    color: Color,
    box: Float,
    align: KkAlign = KkAlign.START,
    valign: KkVAlign = KkVAlign.TOP,
) {
    drawKkText(layout, x, y, color, align, valign)
    val records = PauseTextProbe.records ?: return
    val left = when (align) {
        KkAlign.START -> x
        KkAlign.CENTER -> x - layout.size.width * 0.5f
        KkAlign.END -> x - layout.size.width
    }
    val top = when (valign) {
        KkVAlign.TOP -> y - layout.kkBoxTop
        KkVAlign.CENTER -> y - (layout.kkBoxTop + layout.kkBoxBottom) * 0.5f
        KkVAlign.BASELINE -> y - layout.firstBaseline
    }
    records += PauseTextRecord(kind, layout, Rect(left, top, left + layout.size.width, top + layout.size.height), box)
}

private fun masteryColor(tier: WeaponMastery, roles: KkRolePalette): Color = when (tier) {
    WeaponMastery.CALIBRATED -> Kk.Mute
    WeaponMastery.AMPLIFIED -> roles.you
    WeaponMastery.RESONANT -> Kk.Bone
    WeaponMastery.ASCENDED -> Kk.RLegend
}

private fun runColor(tone: PauseValueTone, roles: KkRolePalette): Color = when (tone) {
    PauseValueTone.BONE -> Kk.Bone
    PauseValueTone.YOU -> roles.you
    PauseValueTone.THREAT -> roles.threat
}

/**
 * Pause board: build header, weapon + relics column, stats column, six run panels. [ui] measures
 * text that follows the text-size setting, [display] the level numerals.
 */
private fun DrawScope.drawRegularBuild(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    area: Rect,
    unit: Float,
    time: Float,
) {
    val roles = ui.roles
    val k = unit / density
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val width = area.width / unit
    drawBuildHeader(overview, ui, display, x(0f), x(width), y(0f), y(4f), 40f * k, 16f * unit, k, 12f, 20f, time)
    val column = (width - 20f) / 2f
    // Weapon column.
    drawPauseLabel(overview.weaponLabel, ui, 15f * k, x(0f), y(58f), column * unit)
    val weaponRow = PauseRects.of(0, x(0f), y(83f), x(column), y(129f))
    drawRect(Kk.Ink2, weaponRow.topLeft, weaponRow.size)
    drawKkIcon(overview.weaponIcon, Offset(weaponRow.left + 24f * unit, weaponRow.center.y), 24f * unit, roles.you)
    // The level numeral is display type; its label, the mastery and the name follow the setting.
    val levelNumber = measureKkText(display, kkIntString(overview.weaponLevel), display.typography.wideStyle(16f * k, tabular = true))
    drawPauseText(PauseTextKind.LEVEL, levelNumber, weaponRow.right - 12f * unit, weaponRow.center.y, Kk.Bone, levelNumber.size.width.toFloat(),
        KkAlign.END, KkVAlign.CENTER)
    val textRoom = weaponRow.width - 12f * unit - levelNumber.size.width - 48f * unit
    val levelLabel = fitOverlayText(ui, overview.levelLabel, KkTextRole.LABEL, 10f * k, textRoom * 0.25f, uppercase = true)
    drawPauseText(PauseTextKind.LABEL, levelLabel, weaponRow.right - 16f * unit - levelNumber.size.width, weaponRow.center.y + 2f * unit,
        Kk.Mute, textRoom * 0.25f, KkAlign.END, KkVAlign.CENTER)
    // The weapon name keeps its size first; the mastery tier gives way when both do not fit.
    val shared = weaponRow.right - 30f * unit - levelNumber.size.width - levelLabel.size.width - 12f * unit - (weaponRow.left + 48f * unit)
    val nameWidth = measureKkText(ui, overview.weaponName, ui.typography.condStyle(21f * k), uppercase = true).size.width
    val masteryRoom = maxOf(shared - nameWidth, shared * 0.35f)
    val mastery = fitOverlayText(ui, overview.mastery, KkTextRole.MONO, 11f * k, masteryRoom, uppercase = true)
    drawPauseText(PauseTextKind.MASTERY, mastery, weaponRow.right - 30f * unit - levelNumber.size.width - levelLabel.size.width,
        weaponRow.center.y, masteryColor(overview.masteryTier, roles), masteryRoom, KkAlign.END, KkVAlign.CENTER)
    val nameRoom = shared - mastery.size.width
    drawPauseText(PauseTextKind.WEAPON_NAME, fitOverlayText(ui, overview.weaponName, KkTextRole.COND, 21f * k, nameRoom, uppercase = true),
        weaponRow.left + 48f * unit, weaponRow.center.y, Kk.Bone, nameRoom, valign = KkVAlign.CENTER)
    // Relic matrix.
    drawPauseLabel(overview.relicsLabel, ui, 15f * k, x(0f), y(147f), column * unit)
    drawPauseRelics(overview, x(22f), y(194f), 44f * k, 52f * unit)
    // Active synergies under the matrix: name, then the whole description on up to two lines.
    var synergyTop = y(230f)
    for (index in 0 until minOf(2, overview.synergyNames.size)) {
        val color = overview.links[index].definition.overlayColor()
        val name = fitOverlayText(ui, overview.synergyNames[index], KkTextRole.COND, 18f * k, column * unit, uppercase = true)
        drawPauseText(PauseTextKind.SYNERGY_NAME, name, x(0f), synergyTop, color, column * unit)
        val description = fitOverlayText(ui, overview.synergyDescriptions[index], KkTextRole.MONO, 9f * k, column * unit,
            maxLines = 2, uppercase = true, minScale = 0.85f)
        drawPauseText(PauseTextKind.SYNERGY_DESCRIPTION, description, x(0f), synergyTop + name.kkBoxHeight + 4f * unit, Kk.Mute, column * unit)
        synergyTop += name.kkBoxHeight + description.kkBoxHeight + 12f * unit
    }
    // Stats column.
    val statsLeft = column + 20f
    drawPauseLabel(overview.statsLabel, ui, 15f * k, x(statsLeft), y(58f), column * unit)
    drawStatRows(overview.stats, ui, display, area, unit, statsLeft, 83f, 29f, 1, column, 10f, 20f * k, 15f * k)
    // Run statistics.
    val runTop = 83f + overview.stats.size * 29f + 20f
    drawPauseLabel(overview.runLabel, ui, 15f * k, x(0f), y(runTop), width * unit)
    drawRunPanels(overview.run, ui, x(0f), y(runTop + 25f), width * unit, 8f * unit, 58f * unit, 6, k, 9f, 20f)
}

/** A section label (label role, uppercase) fitted to [box]. */
private fun DrawScope.drawPauseLabel(text: String, measurer: CanvasTextMeasurer, size: Float, x: Float, y: Float, box: Float) {
    drawPauseText(PauseTextKind.LABEL, fitOverlayText(measurer, text, KkTextRole.LABEL, size, box, uppercase = true), x, y, Kk.Mute, box)
}

/**
 * Mobile-Pause board: smaller header, weapon slot, relic row, six stats and four run panels.
 * Landscape keeps two stat columns; portrait has the height for one column and the synergy
 * description.
 */
private fun DrawScope.drawCompactBuild(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    area: Rect,
    unit: Float,
    time: Float,
    portrait: Boolean,
) {
    val roles = ui.roles
    val k = unit / density
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val width = area.width / unit
    drawBuildHeader(overview, ui, display, x(0f), x(width), y(0f), y(2f), 28f * k, 10f * unit, k, 9f, 16f, time)
    // The slot's "Lvl N" belongs to the slot component (display type); it stays inside the sheared
    // face and clear of the icon at any level ("Ур. 10" is as wide as the face).
    val slot = PauseRects.of(1, x(0f), y(38f), x(50f), y(88f))
    val placement = WeaponSlotPlacementMemo.of(this, display, slot, overview.weaponIcon, overview.weaponLevelText, PauseSlotIconShare)
    drawPlacedWeaponSlot(display, slot, overview.weaponIcon, overview.weaponLevelText, overview.masteryTier == WeaponMastery.ASCENDED,
        ready = true, placement = placement)
    val nameRoom = (width - 62f) * unit
    drawPauseText(PauseTextKind.WEAPON_NAME, fitOverlayText(ui, overview.weaponName, KkTextRole.COND, 18f * k, nameRoom, uppercase = true),
        x(62f), y(46f), Kk.Bone, nameRoom)
    drawPauseText(PauseTextKind.MASTERY, fitOverlayText(ui, overview.mastery, KkTextRole.MONO, 10f * k, nameRoom, uppercase = true),
        x(62f), y(70f), masteryColor(overview.masteryTier, roles), nameRoom)
    val slotsRight = drawPauseRelics(overview, x(17f), y(117f), 34f * k, 40f * unit)
    var statsTop = 146f
    overview.synergyNames.firstOrNull()?.let { name ->
        val color = overview.links[0].definition.overlayColor()
        val room = x(width) - slotsRight - 6f * unit
        drawPauseText(PauseTextKind.SYNERGY_NAME, fitOverlayText(ui, name, KkTextRole.COND, 16f * k, room, uppercase = true),
            slotsRight + 6f * unit, y(117f), color, room, valign = KkVAlign.CENTER)
        if (portrait) {
            val description = fitOverlayText(ui, overview.synergyDescriptions[0], KkTextRole.MONO, 9f * k, width * unit,
                maxLines = 2, uppercase = true, minScale = 0.85f)
            drawPauseText(PauseTextKind.SYNERGY_DESCRIPTION, description, x(0f), y(142f), Kk.Mute, width * unit)
            statsTop = 146f + description.kkBoxHeight / unit + 4f
        }
    }
    val shown = overview.compactStats
    val columns = if (portrait) 1 else 2
    val column = (width - 20f * (columns - 1)) / columns
    val rows = (shown.size + columns - 1) / columns
    // Rows and run panels grow with the text size as far as the build area allows.
    val runHeight = 44f * maxOf(1f, ui.scale)
    val room = area.height / unit - statsTop - 12f - runHeight
    val rowHeight = minOf(maxOf(26f, 26f * ui.scale), room / rows).coerceAtLeast(26f)
    drawStatRows(shown, ui, display, area, unit, 0f, statsTop, rowHeight, columns, column, 8f, 18f * k, 14f * k)
    val runTop = statsTop + rows * rowHeight + 12f
    drawRunPanels(overview.compactRun, ui, x(0f), y(runTop), width * unit, 5f * unit, runHeight * unit, 4, k, 8f, 15f)
}

/** Mobile-Pause board: a 26 px icon in the 50 px slot. */
private const val PauseSlotIconShare = 26f / 50f

private val CompactStatIndices = setOf(0, 1, 3, 4, 5, 6)
private val CompactRunIndices = setOf(1, 2, 3, 4)

/**
 * "Build" heading at [left] and the tags right-aligned at [right] on one row: when both do not
 * fit, the tags shrink first (to 60 %), then the heading.
 */
private fun DrawScope.drawBuildHeader(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    left: Float,
    right: Float,
    headingTop: Float,
    tagsTop: Float,
    headingSize: Float,
    gap: Float,
    k: Float,
    badgeLabel: Float,
    badgeNumber: Float,
    time: Float,
) {
    val heading = measureKkText(ui, overview.heading, ui.typography.condStyle(headingSize), uppercase = true).size.width
    val natural = buildTagsWidth(overview, ui, display, k, 1f, badgeLabel, badgeNumber)
    val tagScale = ((right - left - heading - gap) / natural).coerceIn(0.6f, 1f)
    val tagsLeft = drawBuildTags(overview, ui, display, right, tagsTop, k, tagScale, badgeLabel, badgeNumber, time)
    val box = tagsLeft - left - gap
    drawPauseText(PauseTextKind.HEADING, fitOverlayText(ui, overview.heading, KkTextRole.COND, headingSize, box, uppercase = true),
        left, headingTop, Kk.Bone, box)
}

private fun DrawScope.buildTagsWidth(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    k: Float,
    tagScale: Float,
    badgeLabel: Float,
    badgeNumber: Float,
): Float {
    val badge = kkLevelBadgeSize(display, overview.level, density, overview.levelLabel, badgeLabel, badgeNumber)
    val tagHeight = 22f * k * maxOf(1f, ui.scale) * tagScale
    val fontSize = 13f * k * tagScale
    return badge.width + 8f * density + 20f * density + kkTagSize(ui, overview.rebirth, density, tagHeight, fontSize).width +
        kkTagSize(ui, overview.form, density, tagHeight, fontSize).width + tagHeight * density * 0.8f
}

/**
 * One list of build stats: labels share one fitted size; values right-aligned in their column
 * ([PauseStatFit]). [base] measures at the default text size.
 */
private fun DrawScope.drawStatRows(
    stats: List<OverlayStat>,
    ui: CanvasTextMeasurer,
    base: CanvasTextMeasurer,
    area: Rect,
    unit: Float,
    firstLeft: Float,
    top: Float,
    rowHeight: Float,
    columns: Int,
    column: Float,
    valueGap: Float,
    valueSize: Float,
    labelSize: Float,
) {
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val roles = ui.roles
    val fit = PauseStatFit.of(stats, ui, base, column * unit, valueGap * unit, valueSize, labelSize)
    for (index in stats.indices) {
        val stat = stats[index]
        val left = firstLeft + if (index % columns == 0) 0f else column + 20f
        val bottom = y(top + (index / columns + 1) * rowHeight)
        val value = fit.value(index)
        drawPauseText(PauseTextKind.STAT_VALUE, value, x(left + column), bottom - 6f * unit, if (stat.highlight) roles.you else Kk.Bone,
            fit.valueBox(index), KkAlign.END, KkVAlign.BASELINE)
        drawPauseText(PauseTextKind.STAT_LABEL, fit.label(index), x(left), bottom - 6f * unit, Kk.Bone,
            column * unit - value.size.width - valueGap * unit, valign = KkVAlign.BASELINE)
        drawRect(Kk.Line, Offset(x(left), bottom - unit), Size(column * unit, unit))
    }
}

/**
 * The fitted rows of the last drawn stat list (a paused frame redraws the same list every frame,
 * so they are fitted once). At the default text size and below, values fit 45 % of the column
 * and the labels shrink together to the rest. Above the default, labels and values grow together
 * from the default fit only as far as every row allows, so a larger setting never leaves a label
 * smaller than the default did (long Russian labels in a phone's two columns).
 */
private object PauseStatFit {
    private var stats: List<OverlayStat>? = null
    private var ui: CanvasTextMeasurer? = null
    private var base: CanvasTextMeasurer? = null
    private val key = FloatArray(4) { Float.NaN }
    private var labels = arrayOfNulls<TextLayoutResult>(0)
    private var values = arrayOfNulls<TextLayoutResult>(0)
    private var valueBoxes = FloatArray(0)

    fun label(index: Int): TextLayoutResult = requireNotNull(labels[index])
    fun value(index: Int): TextLayoutResult = requireNotNull(values[index])
    fun valueBox(index: Int): Float = valueBoxes[index]

    fun of(
        stats: List<OverlayStat>,
        ui: CanvasTextMeasurer,
        base: CanvasTextMeasurer,
        column: Float,
        gap: Float,
        valueSize: Float,
        labelSize: Float,
    ): PauseStatFit {
        if (this.stats === stats && this.ui === ui && this.base === base && key[0] == column && key[1] == gap &&
            key[2] == valueSize && key[3] == labelSize
        ) return this
        this.stats = stats
        this.ui = ui
        this.base = base
        key[0] = column
        key[1] = gap
        key[2] = valueSize
        key[3] = labelSize
        fit(stats, if (ui.scale <= 1f) ui else base, column, gap, valueSize, labelSize)
        if (ui.scale > 1f) grow(stats, base, ui.scale, column, gap)
        return this
    }

    private fun fit(stats: List<OverlayStat>, measurer: CanvasTextMeasurer, column: Float, gap: Float, valueSize: Float, labelSize: Float) {
        labels = arrayOfNulls(stats.size)
        values = arrayOfNulls(stats.size)
        valueBoxes = FloatArray(stats.size) { column * 0.45f }
        var shared = 1f
        for (index in stats.indices) {
            val value = fitOverlayText(measurer, stats[index].value, KkTextRole.COND, valueSize, column * 0.45f, tabular = true)
            values[index] = value
            val fitted = fitOverlayText(measurer, stats[index].label, KkTextRole.BODY, labelSize, column - value.size.width - gap)
            shared = minOf(shared, fitted.layoutInput.style.fontSize.value / (labelSize * measurer.scale))
        }
        for (index in stats.indices) {
            labels[index] = fitOverlayText(measurer, stats[index].label, KkTextRole.BODY, labelSize * shared,
                column - value(index).size.width - gap)
        }
    }

    /** Grows the default fit (measured by [base], scale 1) by up to [limit], in 5 % steps. */
    private fun grow(stats: List<OverlayStat>, base: CanvasTextMeasurer, limit: Float, column: Float, gap: Float) {
        val labelFont = label(0).layoutInput.style.fontSize.value
        val valueFonts = FloatArray(stats.size) { value(it).layoutInput.style.fontSize.value }
        val grownLabels = arrayOfNulls<TextLayoutResult>(stats.size)
        val grownValues = arrayOfNulls<TextLayoutResult>(stats.size)
        var growth = limit
        while (growth > 1.001f) {
            var fits = true
            for (index in stats.indices) {
                val label = measureKkText(base, stats[index].label, base.typography.kkStyle(KkTextRole.BODY, labelFont * growth))
                val value = measureKkText(base, stats[index].value, base.typography.kkStyle(KkTextRole.COND, valueFonts[index] * growth, tabular = true))
                if (label.size.width + gap + value.size.width > column) {
                    fits = false
                    break
                }
                grownLabels[index] = label
                grownValues[index] = value
            }
            if (fits) {
                labels = grownLabels
                values = grownValues
                for (index in stats.indices) valueBoxes[index] = column - gap - label(index).size.width
                return
            }
            growth = maxOf(1f, growth - 0.05f)
        }
    }
}

/** Level badge (display type), rebirth tag and form tag, right-aligned at [right]; returns their left edge. */
private fun DrawScope.drawBuildTags(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    right: Float,
    top: Float,
    k: Float,
    tagScale: Float,
    badgeLabel: Float,
    badgeNumber: Float,
    time: Float,
): Float {
    val badge = kkLevelBadgeSize(display, overview.level, density, overview.levelLabel, badgeLabel, badgeNumber)
    var x = right - badge.width - 8f * density
    drawKkLevelBadge(display, Offset(x, top), overview.level, overview.levelLabel, time, 1f, badgeLabel, badgeNumber)
    val tagHeight = 22f * k * maxOf(1f, ui.scale) * tagScale
    val tagTop = top + (badge.height - tagHeight * density) * 0.5f
    val fontSize = 13f * k * tagScale
    val rebirth = kkTagSize(ui, overview.rebirth, density, tagHeight, fontSize)
    x -= 10f * density + rebirth.width
    drawKkTag(ui, overview.rebirth, Offset(x, tagTop), KkTagVariant.DEFAULT, tagHeight, fontSize)
    recordPauseTag(ui, overview.rebirth, x, tagTop, rebirth)
    val form = kkTagSize(ui, overview.form, density, tagHeight, fontSize)
    val iconSpace = tagHeight * density * 0.8f
    x -= 10f * density + form.width + iconSpace
    val formRect = PauseRects.of(2, x, tagTop, x + form.width + iconSpace, tagTop + tagHeight * density)
    drawKkSlab(formRect, Kk.Bone, 6f * density)
    drawKkIcon(overview.formIcon, Offset(formRect.left + 6f * density + iconSpace * 0.45f, formRect.center.y), iconSpace * 0.62f, Kk.Ink)
    drawKkTag(ui, overview.form, Offset(x + iconSpace, tagTop), KkTagVariant.BONE, tagHeight, fontSize, background = Color.Transparent)
    recordPauseTag(ui, overview.form, x, tagTop, Size(form.width + iconSpace, form.height))
    return x
}

/** Tags are drawn (and sized to their label) by the foundation; the probe records their plates. */
private fun recordPauseTag(measurer: CanvasTextMeasurer, text: String, x: Float, y: Float, size: Size) {
    val records = PauseTextProbe.records ?: return
    records += PauseTextRecord(PauseTextKind.TAG, measureKkText(measurer, text, measurer.typography.labelStyle(13f)), Rect(Offset(x, y), size),
        Float.POSITIVE_INFINITY)
}

/** Relic diamonds with synergy link bars; returns the right edge of the row. */
private fun DrawScope.drawPauseRelics(overview: PauseBuildOverview, firstCenterX: Float, centerY: Float, sizeDp: Float, pitch: Float): Float {
    val half = sizeDp * density * 0.5f
    for (linkIndex in overview.links.indices) {
        val link = overview.links[linkIndex]
        for (step in 0 until link.slots.size - 1) {
            val a = link.slots[step]
            val b = link.slots[step + 1]
            drawKkSynergyLink(Offset(firstCenterX + a * pitch + half, centerY), Offset(firstCenterX + b * pitch - half, centerY),
                link.definition.overlayColor())
        }
    }
    for (index in overview.relics.indices) {
        val relic = overview.relics[index]
        val center = Offset(firstCenterX + index * pitch, centerY)
        if (relic != null) drawKkRelicSlot(center, relic.aspect.overlayColor(), relic.aspect.overlayIcon(), sizeDp)
        else drawKkRelicSlot(center, Color.Unspecified, sizeDp = sizeDp)
    }
    return firstCenterX + (overview.relics.size - 1) * pitch + half
}

/**
 * Run statistic panels in one row ([PauseRunFit]): labels share one fitted size, as do values;
 * panels are equal unless a value or label needs more room.
 */
private fun DrawScope.drawRunPanels(
    run: List<PauseRunStat>,
    measurer: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    gap: Float,
    height: Float,
    columns: Int,
    k: Float,
    labelSize: Float,
    valueSize: Float,
) {
    val roles = measurer.roles
    val pad = 10f * k * density
    val fit = PauseRunFit.of(run, measurer, width, gap, pad, minOf(columns, run.size), labelSize * k, valueSize * k)
    var x = left
    for (index in 0 until fit.count) {
        val stat = run[index]
        val cell = fit.width(index)
        drawRect(Kk.Ink2, Offset(x, top), Size(cell, height))
        val room = cell - pad * 2f
        drawPauseText(PauseTextKind.RUN_LABEL, fit.label(index), x + pad, top + pad * 0.9f, Kk.Mute, room)
        drawPauseText(PauseTextKind.RUN_VALUE, fit.value(index), x + pad, top + height - pad * 0.9f, runColor(stat.tone, roles), room,
            valign = KkVAlign.BASELINE)
        x += cell + gap
    }
}

/**
 * The fitted run panels of the last drawn row. Panels share the row equally while every label and
 * value fits its panel; a panel whose text needs more (a long Russian total such as "184,3 тыс.",
 * "Лучшая серия" at a large text size) widens and the others give way evenly
 * ([overlayRowWidths]). Labels then share one fitted size and values another, so no panel's text
 * is smaller than its neighbours'.
 */
private object PauseRunFit {
    private var run: List<PauseRunStat>? = null
    private var measurer: CanvasTextMeasurer? = null
    private val key = FloatArray(5) { Float.NaN }
    var count = 0
        private set
    private var widths = FloatArray(0)
    private var labels = arrayOfNulls<TextLayoutResult>(0)
    private var values = arrayOfNulls<TextLayoutResult>(0)

    fun width(index: Int): Float = widths[index]
    fun label(index: Int): TextLayoutResult = requireNotNull(labels[index])
    fun value(index: Int): TextLayoutResult = requireNotNull(values[index])

    fun of(run: List<PauseRunStat>, measurer: CanvasTextMeasurer, width: Float, gap: Float, pad: Float, count: Int, labelSize: Float, valueSize: Float): PauseRunFit {
        if (this.run === run && this.measurer === measurer && this.count == count && key[0] == width && key[1] == gap && key[2] == pad &&
            key[3] == labelSize && key[4] == valueSize
        ) return this
        this.run = run
        this.measurer = measurer
        this.count = count
        key[0] = width
        key[1] = gap
        key[2] = pad
        key[3] = labelSize
        key[4] = valueSize
        val typography = measurer.typography
        val content = FloatArray(count) { index ->
            maxOf(
                measureKkText(measurer, run[index].label, typography.kkStyle(KkTextRole.MONO, labelSize), uppercase = true).size.width,
                measureKkText(measurer, run[index].value, typography.kkStyle(KkTextRole.WIDE, valueSize, tabular = true)).size.width,
            ).toFloat()
        }
        widths = overlayRowWidths(content, FloatArray(count) { pad * 2f }, FloatArray(count) { 1f }, width - gap * (count - 1)).first
        var labelScale = 1f
        var valueScale = 1f
        for (index in 0 until count) {
            val room = widths[index] - pad * 2f
            val label = fitOverlayText(measurer, run[index].label, KkTextRole.MONO, labelSize, room, uppercase = true, minScale = 0.65f)
            labelScale = minOf(labelScale, label.layoutInput.style.fontSize.value / (labelSize * measurer.scale))
            val value = fitOverlayText(measurer, run[index].value, KkTextRole.WIDE, valueSize, room, tabular = true, minScale = 0.3f)
            valueScale = minOf(valueScale, value.layoutInput.style.fontSize.value / (valueSize * measurer.scale))
        }
        labels = arrayOfNulls(count)
        values = arrayOfNulls(count)
        for (index in 0 until count) {
            val room = widths[index] - pad * 2f
            labels[index] = fitOverlayText(measurer, run[index].label, KkTextRole.MONO, labelSize * labelScale, room, uppercase = true, minScale = 0.65f)
            values[index] = fitOverlayText(measurer, run[index].value, KkTextRole.WIDE, valueSize * valueScale, room, tabular = true, minScale = 0.3f)
        }
        return this
    }
}

/**
 * Widths (px) of a row of boxes sharing [available] px, and one factor for all their text. A box
 * needs its content ([contents], px at full size) plus [pads]. With room to spare, the boxes with
 * a weight share the rest by [weights] and never get less than they need (weight 0 keeps the
 * need). Without room, every content shrinks by the same factor.
 */
internal fun overlayRowWidths(contents: FloatArray, pads: FloatArray, weights: FloatArray, available: Float): Pair<FloatArray, Float> {
    val count = contents.size
    val widths = FloatArray(count)
    val need = FloatArray(count) { contents[it] + pads[it] }
    if (need.sum() > available) {
        val factor = ((available - pads.sum()) / contents.sum()).coerceIn(0f, 1f)
        for (index in 0 until count) widths[index] = contents[index] * factor + pads[index]
        return widths to factor
    }
    val fixed = BooleanArray(count) { weights[it] <= 0f }
    while (true) {
        var rest = available
        var weight = 0f
        for (index in 0 until count) if (fixed[index]) rest -= need[index] else weight += weights[index]
        if (weight <= 0f) {
            for (index in 0 until count) widths[index] = need[index]
            return widths to 1f
        }
        val share = rest / weight
        var changed = false
        for (index in 0 until count) if (!fixed[index] && weights[index] * share < need[index]) {
            fixed[index] = true
            changed = true
        }
        if (!changed) {
            for (index in 0 until count) widths[index] = if (fixed[index]) need[index] else weights[index] * share
            return widths to 1f
        }
    }
}

/** A few reusable rects for the paused frame (redrawn every frame with the same geometry). */
private object PauseRects {
    private val rects = arrayOfNulls<Rect>(3)

    fun of(slot: Int, left: Float, top: Float, right: Float, bottom: Float): Rect {
        val cached = rects[slot]
        if (cached != null && cached.left == left && cached.top == top && cached.right == right && cached.bottom == bottom) return cached
        return Rect(left, top, right, bottom).also { rects[slot] = it }
    }
}

/**
 * A pause text layout that fits [maxWidth] in [maxLines] without breaking inside a word: the
 * [role] font shrinks (to [minScale] of [size]) and, only if it still does not fit, further until
 * it does. Nothing is ever cut with an ellipsis. Memoized per text and box, so the paused frame
 * redraws without measuring.
 */
internal fun fitOverlayText(
    measurer: CanvasTextMeasurer,
    text: String,
    role: KkTextRole,
    size: Float,
    maxWidth: Float,
    maxLines: Int = 1,
    uppercase: Boolean = false,
    minScale: Float = 0.7f,
    tabular: Boolean = false,
): TextLayoutResult = PauseFitMemo.find(measurer, text, role, size, maxWidth, maxLines, uppercase, tabular)
    ?: measureFitted(measurer, text, role, size, maxWidth, maxLines, uppercase, minScale, tabular)
        .also { PauseFitMemo.put(measurer, text, role, size, maxWidth, maxLines, uppercase, tabular, it) }

private fun measureFitted(
    measurer: CanvasTextMeasurer,
    text: String,
    role: KkTextRole,
    size: Float,
    maxWidth: Float,
    maxLines: Int,
    uppercase: Boolean,
    minScale: Float,
    tabular: Boolean,
): TextLayoutResult {
    val typography = measurer.typography
    val words = (if (uppercase) text.uppercase() else text).split(' ').filter(String::isNotEmpty)
    fun fitted(scale: Float): TextLayoutResult? {
        val style = typography.kkStyle(role, size * scale, tabular)
        if (maxLines == 1) {
            val layout = measureKkText(measurer, text, style, uppercase)
            return layout.takeIf { it.size.width <= maxWidth }
        }
        val widest = words.maxOfOrNull { measureKkText(measurer, it, style).size.width } ?: 0
        if (widest > maxWidth) return null
        return measureKkText(measurer, text, style, uppercase, maxWidth, maxLines).takeIf { !it.hasVisualOverflow }
    }
    var scale = 1f
    while (scale >= minScale - 0.001f) {
        fitted(scale)?.let { return it }
        scale -= 0.05f
    }
    // Below the preferred floor the text keeps shrinking rather than being cut.
    scale = minScale - 0.05f
    while (scale > 0.2f) {
        fitted(scale)?.let { return it }
        scale -= 0.05f
    }
    return measureKkText(measurer, text, typography.kkStyle(role, size * 0.2f, tabular), uppercase, maxWidth, maxLines)
}

/** Recently fitted pause texts (draw-thread confined, round robin). */
private object PauseFitMemo {
    private const val CAPACITY = 48
    private val texts = arrayOfNulls<String>(CAPACITY)
    private val roles = arrayOfNulls<KkTextRole>(CAPACITY)
    private val delegates = arrayOfNulls<Any>(CAPACITY)
    private val floats = FloatArray(CAPACITY * 3)
    private val ints = IntArray(CAPACITY * 3)
    private val results = arrayOfNulls<TextLayoutResult>(CAPACITY)
    private var next = 0

    fun find(
        measurer: CanvasTextMeasurer,
        text: String,
        role: KkTextRole,
        size: Float,
        maxWidth: Float,
        maxLines: Int,
        uppercase: Boolean,
        tabular: Boolean,
    ): TextLayoutResult? {
        for (index in 0 until CAPACITY) {
            if (roles[index] !== role || delegates[index] !== measurer.delegate) continue
            val f = index * 3
            val i = index * 3
            if (floats[f] == size && floats[f + 1] == maxWidth && floats[f + 2] == measurer.scale &&
                ints[i] == maxLines && ints[i + 1] == (if (uppercase) 1 else 0) && ints[i + 2] == (if (tabular) 1 else 0) &&
                texts[index] == text
            ) return results[index]
        }
        return null
    }

    fun put(
        measurer: CanvasTextMeasurer,
        text: String,
        role: KkTextRole,
        size: Float,
        maxWidth: Float,
        maxLines: Int,
        uppercase: Boolean,
        tabular: Boolean,
        result: TextLayoutResult,
    ) {
        val index = next
        next = (next + 1) % CAPACITY
        texts[index] = text
        roles[index] = role
        delegates[index] = measurer.delegate
        floats[index * 3] = size
        floats[index * 3 + 1] = maxWidth
        floats[index * 3 + 2] = measurer.scale
        ints[index * 3] = maxLines
        ints[index * 3 + 1] = if (uppercase) 1 else 0
        ints[index * 3 + 2] = if (tabular) 1 else 0
        results[index] = result
    }
}
