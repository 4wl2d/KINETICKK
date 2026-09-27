// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
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
    if (layout.mode == GameplayLayoutMode.REGULAR) {
        drawRegularBuild(overview, measurer, layout.build, layout.unit, time)
    } else {
        drawCompactBuild(overview, measurer, layout.build, layout.unit, time)
    }
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

/** Pause board: build header, weapon + relics column, stats column, six run panels. */
private fun DrawScope.drawRegularBuild(overview: PauseBuildOverview, measurer: CanvasTextMeasurer, area: Rect, unit: Float, time: Float) {
    val roles = measurer.roles
    val typography = measurer.typography
    val k = unit / density
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val width = area.width / unit
    drawKkText(measurer, overview.heading, typography.condStyle(40f * k), x(0f), y(0f), Kk.Bone, uppercase = true)
    drawBuildTags(overview, measurer, x(width), y(4f), k, 12f, 20f, time)
    val column = (width - 20f) / 2f
    // Weapon column.
    drawKkText(measurer, overview.weaponLabel, typography.labelStyle(15f * k), x(0f), y(58f), Kk.Mute, uppercase = true)
    val weaponRow = PauseRects.of(0, x(0f), y(83f), x(column), y(129f))
    drawRect(Kk.Ink2, weaponRow.topLeft, weaponRow.size)
    drawKkIcon(overview.weaponIcon, Offset(weaponRow.left + 24f * unit, weaponRow.center.y), 24f * unit, roles.you)
    drawKkText(measurer, overview.weaponName, typography.condStyle(21f * k), weaponRow.left + 48f * unit, weaponRow.center.y, Kk.Bone,
        valign = KkVAlign.CENTER, uppercase = true, maxWidth = column * unit * 0.42f)
    val levelNumber = drawKkText(measurer, kkIntString(overview.weaponLevel), typography.wideStyle(16f * k, tabular = true),
        weaponRow.right - 12f * unit, weaponRow.center.y, Kk.Bone, KkAlign.END, KkVAlign.CENTER)
    val levelLabel = drawKkText(measurer, overview.levelLabel, typography.labelStyle(10f * k), weaponRow.right - 16f * unit - levelNumber.size.width,
        weaponRow.center.y + 2f * unit, Kk.Mute, KkAlign.END, KkVAlign.CENTER, uppercase = true)
    drawKkText(measurer, overview.mastery, typography.monoStyle(11f * k), weaponRow.right - 30f * unit - levelNumber.size.width - levelLabel.size.width,
        weaponRow.center.y, masteryColor(overview.masteryTier, roles), KkAlign.END, KkVAlign.CENTER, uppercase = true)
    // Relic matrix.
    drawKkText(measurer, overview.relicsLabel, typography.labelStyle(15f * k), x(0f), y(147f), Kk.Mute, uppercase = true)
    val slotsRight = drawPauseRelics(overview, measurer, x(22f), y(194f), 44f * k, 52f * unit)
    for (index in 0 until minOf(2, overview.synergyNames.size)) {
        val name = overview.synergyNames[index]
        val top = y(172f + index * 44f)
        val color = overview.links[index].definition.overlayColor()
        drawKkText(measurer, name, typography.condStyle(18f * k), slotsRight + 12f * unit, top, color, uppercase = true,
            maxWidth = x(column) - slotsRight - 12f * unit)
        val description = measureKkText(measurer, overview.synergyDescriptions[index], typography.monoStyle(9f * k), uppercase = true,
            maxWidth = x(column) - slotsRight - 12f * unit, maxLines = 2)
        drawKkText(description, slotsRight + 12f * unit, top + 20f * unit, Kk.Mute)
    }
    // Stats column.
    val statsLeft = column + 20f
    drawKkText(measurer, overview.statsLabel, typography.labelStyle(15f * k), x(statsLeft), y(58f), Kk.Mute, uppercase = true)
    for (index in overview.stats.indices) {
        val stat = overview.stats[index]
        val top = y(83f + index * 29f)
        val bottom = top + 29f * unit
        val value = drawKkText(measurer, stat.value, typography.condStyle(20f * k, tabular = true), x(width), bottom - 6f * unit,
            if (stat.highlight) roles.you else Kk.Bone, KkAlign.END, KkVAlign.BASELINE)
        drawKkText(measurer, stat.label, typography.bodyStyle(15f * k), x(statsLeft), bottom - 6f * unit, Kk.Bone, valign = KkVAlign.BASELINE,
            maxWidth = column * unit - value.size.width - 10f * unit)
        drawRect(Kk.Line, Offset(x(statsLeft), bottom - unit), Size(column * unit, unit))
    }
    // Run statistics.
    val runTop = 83f + overview.stats.size * 29f + 20f
    drawKkText(measurer, overview.runLabel, typography.labelStyle(15f * k), x(0f), y(runTop), Kk.Mute, uppercase = true)
    drawRunPanels(overview.run, measurer, x(0f), y(runTop + 25f), width * unit, 8f * unit, 58f * unit, 6, k, 9f, 20f)
}

/** Mobile-Pause board: smaller header, weapon slot, relic row, two stat columns, four panels. */
private fun DrawScope.drawCompactBuild(overview: PauseBuildOverview, measurer: CanvasTextMeasurer, area: Rect, unit: Float, time: Float) {
    val roles = measurer.roles
    val typography = measurer.typography
    val k = unit / density
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val width = area.width / unit
    drawKkText(measurer, overview.heading, typography.condStyle(28f * k), x(0f), y(0f), Kk.Bone, uppercase = true)
    drawBuildTags(overview, measurer, x(width), y(2f), k, 9f, 16f, time)
    val slot = PauseRects.of(1, x(0f), y(38f), x(50f), y(88f))
    drawKkWeaponSlot(measurer, slot, overview.weaponIcon, overview.weaponLevelText, ready = true,
        maxLevel = overview.masteryTier == WeaponMastery.ASCENDED, iconSizeDp = 26f * k)
    drawKkText(measurer, overview.weaponName, typography.condStyle(18f * k), x(62f), y(46f), Kk.Bone, uppercase = true,
        maxWidth = (width - 62f) * unit)
    drawKkText(measurer, overview.mastery, typography.monoStyle(10f * k), x(62f), y(70f), masteryColor(overview.masteryTier, roles), uppercase = true)
    val slotsRight = drawPauseRelics(overview, measurer, x(17f), y(117f), 34f * k, 40f * unit)
    overview.synergyNames.firstOrNull()?.let { name ->
        drawKkText(measurer, name, typography.condStyle(16f * k), slotsRight + 6f * unit, y(117f), overview.links[0].definition.overlayColor(),
            valign = KkVAlign.CENTER, uppercase = true, maxWidth = x(width) - slotsRight - 6f * unit)
    }
    val column = (width - 20f) / 2f
    val shown = overview.compactStats
    for (index in shown.indices) {
        val stat = shown[index]
        val left = if (index % 2 == 0) 0f else column + 20f
        val bottom = y(146f + (index / 2 + 1) * 26f)
        val value = drawKkText(measurer, stat.value, typography.condStyle(18f * k, tabular = true), x(left + column), bottom - 6f * unit,
            if (stat.highlight) roles.you else Kk.Bone, KkAlign.END, KkVAlign.BASELINE)
        drawKkText(measurer, stat.label, typography.bodyStyle(14f * k), x(left), bottom - 6f * unit, Kk.Bone, valign = KkVAlign.BASELINE,
            maxWidth = column * unit - value.size.width - 8f * unit)
        drawRect(Kk.Line, Offset(x(left), bottom - unit), Size(column * unit, unit))
    }
    val runTop = 146f + ((shown.size + 1) / 2) * 26f + 12f
    drawRunPanels(overview.compactRun, measurer, x(0f), y(runTop), width * unit, 5f * unit, 44f * unit, 4, k, 8f, 15f)
}

private val CompactStatIndices = setOf(0, 1, 3, 4, 5, 6)
private val CompactRunIndices = setOf(1, 2, 3, 4)

/** Form tag, rebirth tag and level badge, right-aligned at [right]. */
private fun DrawScope.drawBuildTags(
    overview: PauseBuildOverview,
    measurer: CanvasTextMeasurer,
    right: Float,
    top: Float,
    k: Float,
    badgeLabel: Float,
    badgeNumber: Float,
    time: Float,
) {
    val badge = kkLevelBadgeSize(measurer, overview.level, density, overview.levelLabel, badgeLabel, badgeNumber)
    var x = right - badge.width - 8f * density
    drawKkLevelBadge(measurer, Offset(x, top), overview.level, overview.levelLabel, time, 1f, badgeLabel, badgeNumber)
    val tagHeight = 22f * k
    val tagTop = top + (badge.height - tagHeight * density) * 0.5f
    val fontSize = 13f * k
    val rebirth = kkTagSize(measurer, overview.rebirth, density, tagHeight, fontSize)
    x -= 10f * density + rebirth.width
    drawKkTag(measurer, overview.rebirth, Offset(x, tagTop), KkTagVariant.DEFAULT, tagHeight, fontSize)
    val form = kkTagSize(measurer, overview.form, density, tagHeight, fontSize)
    val iconSpace = tagHeight * density * 0.8f
    x -= 10f * density + form.width + iconSpace
    val formRect = PauseRects.of(2, x, tagTop, x + form.width + iconSpace, tagTop + tagHeight * density)
    drawKkSlab(formRect, Kk.Bone, 6f * density)
    drawKkIcon(overview.formIcon, Offset(formRect.left + 6f * density + iconSpace * 0.45f, formRect.center.y), iconSpace * 0.62f, Kk.Ink)
    drawKkTag(measurer, overview.form, Offset(x + iconSpace, tagTop), KkTagVariant.BONE, tagHeight, fontSize, background = Color.Transparent)
}

/** Relic diamonds with synergy link bars; returns the right edge of the row. */
private fun DrawScope.drawPauseRelics(overview: PauseBuildOverview, measurer: CanvasTextMeasurer, firstCenterX: Float, centerY: Float, sizeDp: Float, pitch: Float): Float {
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
    val typography = measurer.typography
    val cell = (width - gap * (columns - 1)) / columns
    for (index in 0 until minOf(columns, run.size)) {
        val stat = run[index]
        val x = left + index * (cell + gap)
        drawRect(Kk.Ink2, Offset(x, top), Size(cell, height))
        val pad = 10f * k * density
        drawKkText(measurer, stat.label, typography.monoStyle(labelSize * k), x + pad, top + pad * 0.9f, Kk.Mute, uppercase = true,
            maxWidth = cell - pad * 2f)
        drawKkText(measurer, stat.value, typography.wideStyle(valueSize * k, tabular = true), x + pad, top + height - pad * 0.9f,
            runColor(stat.tone, roles), valign = KkVAlign.BASELINE, maxWidth = cell - pad * 2f)
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
