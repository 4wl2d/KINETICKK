// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkButton
import kinetickk.foundation.design.drawKkGrid
import kinetickk.foundation.design.drawKkInfoButton
import kinetickk.foundation.design.drawKkSegment
import kinetickk.foundation.design.drawKkSheared
import kinetickk.foundation.design.drawKkSlab
import kinetickk.foundation.design.drawKkSlider
import kinetickk.foundation.design.drawKkStepButton
import kinetickk.foundation.design.drawKkTab
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.drawKkToggle
import kinetickk.foundation.design.drawKkSlip
import kinetickk.foundation.design.drawKkThreatHatch
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.measureKkTooltip
import kinetickk.foundation.design.formatCompact
import kinetickk.foundation.design.formatMultiplier
import kinetickk.foundation.design.kkShearOffset
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The design's 100 % text corresponds to the game's default 125 % text size. */
internal const val SETTINGS_TEXT_BASELINE = 1.25f

/**
 * Canvas text measurers for one frame: [ui] for controls (text size × mode factor), [body] for
 * explanations (never below the design size on phones) and [fit] for controls shrunk to fit.
 */
internal class SettingsMeasurers(
    private val delegate: TextMeasurer,
    val typography: InterfaceTypography,
    val language: AppLanguage,
    val roles: KkRolePalette,
    val uiScale: Float,
    val bodyScale: Float,
) {
    private val scaled = HashMap<Int, CanvasTextMeasurer>()
    val ui = at(uiScale)
    val body = at(bodyScale)
    /** For text inside fixed-size parts (toggle state words, the (!) mark): never above the design size. */
    val fixed = at(min(uiScale, 1f))

    /** Control text shrunk by [factor] (≤ 1) to fit its cells. */
    fun fit(factor: Float): CanvasTextMeasurer = if (factor >= 0.995f) ui else at(uiScale * factor)

    /** A measurer at an absolute text [scale], cached per percent. */
    fun at(scale: Float): CanvasTextMeasurer {
        val percent = (scale * 100f).roundToInt().coerceIn(10, 400)
        return scaled.getOrPut(percent) { CanvasTextMeasurer(delegate, percent / 100f, language, typography, roles) }
    }
}

internal fun settingsMeasurers(
    delegate: TextMeasurer,
    typography: InterfaceTypography,
    language: AppLanguage,
    roles: KkRolePalette,
    textScale: Float,
    mode: SettingsLayoutMode,
): SettingsMeasurers {
    val relative = textScale / SETTINGS_TEXT_BASELINE
    val phone = mode != SettingsLayoutMode.REGULAR
    val text = if (phone) relative.coerceAtLeast(1f) else relative
    return SettingsMeasurers(delegate, typography, language, roles, text * settingsModeTextFactor(mode), text)
}

/** Text widths for [settingsLayout], measured exactly as the renderer draws them. */
internal fun SettingsMeasurers.metrics(mode: SettingsLayoutMode): SettingsTextMetrics {
    val type = settingsType(mode)
    return object : SettingsTextMetrics {
        override fun width(text: String, kind: SettingsTextKind): Float = when (kind) {
            SettingsTextKind.TITLE -> measureKkText(body, text, titleStyle(type), uppercase = true).size.width.toFloat()
            SettingsTextKind.BUTTON -> measureKkText(ui, text, typography.condStyle(KkButtonSize.SM.fontSp, trackingEm = 0.02f, lineHeightEm = 1f), uppercase = true).size.width.toFloat()
            SettingsTextKind.TAB -> measureKkText(ui, text, typography.condStyle(21f, trackingEm = 0.03f), uppercase = true).size.width.toFloat()
            SettingsTextKind.SEGMENT -> measureKkText(ui, text, typography.labelStyle(16f, trackingEm = 0.07f), uppercase = true).size.width.toFloat()
            SettingsTextKind.OUTPUT -> measureKkText(ui, text, outputStyle(), uppercase = true).size.width.toFloat()
            SettingsTextKind.ROW_LABEL -> measureKkText(body, text, typography.condStyle(type.rowLabel), uppercase = true).size.width.toFloat()
        }

        override fun rowLabelLineHeight(): Float =
            measureKkText(body, ROW_LABEL_PROBE, typography.condStyle(type.rowLabel), uppercase = true).kkBoxHeight
    }
}

/** Tallest Latin and Cyrillic capitals, for the row label line box. */
private const val ROW_LABEL_PROBE = "ЁЙAG"


private fun SettingsMeasurers.titleStyle(type: SettingsType): TextStyle = typography.condStyle(type.title, lineHeightEm = 1f)

internal fun SettingsMeasurers.outputStyle(): TextStyle = typography.condStyle(22f, tabular = true, lineHeightEm = 1f)

/** Transient interaction state the canvas reflects; the feature owns it. */
internal class SettingsFrame(
    val layout: SettingsLayout,
    val preferences: PlayerPreferences,
    val activeRow: SettingsRow?,
    val hover: SettingsTarget?,
    val focus: SettingsTarget?,
    val info: SettingsRow?,
    val time: Float,
) {
    /** The (!) explanation on screen (see [settingsOpenInfo]). */
    val openInfo: SettingsRow?
        get() = settingsOpenInfo(focus, hover, info)
}

/** The (!) explanation on screen: keyboard focus first, then hover, then a pressed toggle. */
internal fun settingsOpenInfo(focus: SettingsTarget?, hover: SettingsTarget?, info: SettingsRow?): SettingsRow? =
    (focus as? SettingsTarget.Info)?.row ?: (hover as? SettingsTarget.Info)?.row ?: info

/**
 * Whether the open explanation takes the presses inside its slip: when a press pinned it or
 * keyboard focus holds it. A slip shown only while the pointer hovers its (!) lets presses
 * through: a press elsewhere means the pointer has left the (!), which closes that slip.
 */
internal fun settingsSlipTakesPresses(focus: SettingsTarget?, hover: SettingsTarget?, info: SettingsRow?): Boolean {
    val open = settingsOpenInfo(focus, hover, info) ?: return false
    return (focus as? SettingsTarget.Info)?.row == open || info == open
}

/**
 * The open (!) explanation: its [row], the slip [bounds] placed by [SettingsLayout.tooltipRect]
 * and the measured [body]. The feature draws it in its own layer above every control, and that
 * layer owns the presses inside [bounds] (see [settingsSlipTakesPresses]).
 */
internal class SettingsInfoSlip(val row: SettingsRow, val bounds: Rect, val body: TextLayoutResult)

internal fun SettingsMeasurers.infoSlip(layout: SettingsLayout, row: SettingsRow): SettingsInfoSlip? {
    val text = measureKkTooltip(body, row.about(language), layout.density)
    val bounds = layout.tooltipRect(row, text.kkBoxHeight) ?: return null
    return SettingsInfoSlip(row, bounds, text)
}

/** `.tipbox`: bone slip with a 10 px top-right cut; [origin] is the top-left of the layer drawn into. */
internal fun DrawScope.drawSettingsInfoSlip(slip: SettingsInfoSlip, origin: Offset) {
    val rect = slip.bounds.translate(-origin)
    drawKkSlip(rect, cutDp = 10f)
    drawKkText(slip.body, rect.left + d(13f), rect.top + d(11f), Kk.Ink)
}

internal class SettingsCanvasCache {
    val paths = KkPathCache(4)
}

internal fun DrawScope.drawSettings(frame: SettingsFrame, measurers: SettingsMeasurers, cache: SettingsCanvasCache) {
    val layout = frame.layout
    val roles = measurers.roles
    val type = settingsType(layout.mode)
    drawRect(Kk.Ink)
    drawKkGrid(layout.bounds, Kk.Bone.copy(alpha = 0.045f), spacingDp = 48f)
    layout.panelEdgeX?.let { edge ->
        val dx = kkShearOffset(size.height)
        val path = cache.paths.sheared(0, edge + dx, 0f, size.width + dx * 2f + d(20f), size.height)
        drawPath(path, Kk.Ink1)
        drawLine(Kk.Line, Offset(edge, size.height), Offset(edge + dx * 2f, 0f), d(1f))
    }

    // Header: back ghost button, divider and title.
    val backLabel = measurers.language.text(SettingsRedesignText.Back)
    drawKkButton(
        measurers.ui, layout.back, backLabel, KkButtonVariant.GHOST, KkButtonSize.SM,
        hover = if (frame.hover == SettingsTarget.Back) 1f else 0f, focused = frame.focus == SettingsTarget.Back,
    )
    layout.rule?.let { drawRect(Kk.Line, it.topLeft, it.size) }
    drawKkText(
        measurers.body, measurers.language.text(ProfileText.SettingsTitle), measurers.titleStyle(type),
        layout.title.left, layout.title.center.y, Kk.Bone, valign = KkVAlign.CENTER, uppercase = true,
        maxWidth = layout.title.width,
    )

    layout.tabs.forEach { tab ->
        val target = SettingsTarget.Tab(tab.group)
        drawKkTab(
            measurers.ui, tab.bounds, measurers.language.text(tab.group.label), tab.group == layout.group,
            hovered = frame.hover == target, focused = frame.focus == target,
        )
    }

    layout.rows.forEach { row -> drawSettingsRow(frame, measurers, row, type) }

    layout.pages.forEachIndexed { index, pip ->
        val selected = index == layout.page
        val target = SettingsTarget.Page(index)
        when {
            selected -> drawKkSheared(pip, roles.you)
            frame.hover == target -> drawKkSheared(pip, Kk.Bone)
            else -> drawKkSheared(pip, Kk.Line2)
        }
        if (frame.focus == target) drawSettingsFocus(pip.settingsTouch(density, 32f))
    }

    layout.preview?.let { drawSettingsPreview(it, frame, measurers) }
    // The open (!) explanation is not drawn here: see [SettingsInfoSlip].
}

private fun DrawScope.drawSettingsRow(
    frame: SettingsFrame,
    measurers: SettingsMeasurers,
    row: SettingsRowLayout,
    type: SettingsType,
) {
    val roles = measurers.roles
    val language = measurers.language
    val preferences = frame.preferences
    if (frame.activeRow == row.row) {
        drawKkSlab(row.bounds, Kk.Ink3, cut = d(10f))
        val cy = row.bounds.center.y
        drawKkSheared(Rect(row.bounds.left - d(14f), cy - d(13f), row.bounds.left - d(9f), cy + d(13f)), roles.you)
    }
    val labelLayout = measureKkText(
        measurers.body, row.row.label(language), measurers.typography.condStyle(type.rowLabel),
        uppercase = true, maxWidth = row.label.width, maxLines = type.rowLabelLines,
    )
    drawKkText(labelLayout, row.label.left, row.label.center.y, Kk.Bone, valign = KkVAlign.CENTER)

    val info = SettingsTarget.Info(row.row)
    drawKkInfoButton(measurers.fixed, row.info, active = frame.openInfo == row.row || frame.hover == info || frame.focus == info)
    if (frame.focus == info) drawSettingsFocus(row.info)

    when (row.row.control) {
        SettingsControl.SEGMENTED -> {
            val selected = row.row.selectedOption(preferences)
            val measurer = measurers.fit(row.controlScale)
            row.options.forEachIndexed { option, cell ->
                val target = SettingsTarget.Option(row.row, option)
                drawKkSegment(
                    measurer, cell, row.row.optionLabel(option, language), option == selected,
                    hovered = frame.hover == target, focused = frame.focus == target,
                )
            }
            if (row.swatches.isNotEmpty()) drawSettingsSwatches(row.swatches, preferences.colorVision.previewPalette(), measurers)
        }
        SettingsControl.TOGGLE -> {
            val toggle = row.toggle ?: return
            drawKkToggle(
                measurers.fixed, toggle, if (row.row.isOn(preferences)) 1f else 0f,
                onLabel = language.text(ProfileText.On), offLabel = language.text(ProfileText.Off),
                focused = frame.focus == SettingsTarget.Toggle(row.row),
            )
        }
        SettingsControl.SLIDER -> {
            // The master volume strip is a Compose control drawn above the canvas.
            if (row.row == SettingsRow.MASTER_VOLUME) return
            val decrease = row.decrease ?: return
            val increase = row.increase ?: return
            val track = row.track ?: return
            val output = row.output ?: return
            val minus = SettingsTarget.Step(row.row, -1)
            val plus = SettingsTarget.Step(row.row, 1)
            drawKkStepButton(decrease, plus = false, hovered = frame.hover == minus, enabled = row.row.canStep(preferences, -1))
            drawKkStepButton(increase, plus = true, hovered = frame.hover == plus, enabled = row.row.canStep(preferences, 1))
            if (frame.focus == minus) drawSettingsFocus(decrease)
            if (frame.focus == plus) drawSettingsFocus(increase)
            drawKkSlider(track, row.row.sliderValue(preferences), roles)
            drawKkText(
                measurers.ui, row.row.sliderOutput(preferences, language), measurers.outputStyle(),
                output.right, output.center.y, Kk.Bone, align = KkAlign.END, valign = KkVAlign.CENTER, uppercase = true,
            )
        }
    }
}

/** Keyboard focus ring (`:focus-visible`: 2 dp bone, 4 dp outside) for controls without one. */
internal fun DrawScope.drawSettingsFocus(bounds: Rect) {
    val inset = d(5f)
    drawRect(
        Kk.Bone, Offset(bounds.left - inset, bounds.top - inset),
        Size(bounds.width + inset * 2f, bounds.height + inset * 2f), style = kkStroke(d(2f)),
    )
}

/**
 * Compact role swatches (YOU, THREAT, HEAT, SHIELD, POLARITY) in the palette being edited, named
 * inside each sheared chip. Mono hatches the threat chip and keeps its name on a solid face.
 */
private fun DrawScope.drawSettingsSwatches(chips: List<Rect>, palette: KkRolePalette, measurers: SettingsMeasurers) {
    val measurer = measurers.at(min(measurers.bodyScale, 1.15f))
    val style = measurers.typography.labelStyle(11f, trackingEm = 0.05f)
    settingsRoleSwatches(palette).forEachIndexed { index, (name, color) ->
        val chip = chips.getOrNull(index) ?: return
        drawKkSheared(chip, color)
        if (index == SETTINGS_THREAT_SWATCH && palette.hatchThreats) {
            drawKkThreatHatch(chip, palette, Kk.Ink)
            val rim = d(3f)
            drawKkSheared(Rect(chip.left + rim, chip.top + rim, chip.right - rim, chip.bottom - rim), color)
        }
        val text = measurers.language.text(name)
        val natural = measureKkText(measurer, text, style, uppercase = true)
        val lane = chip.width - d(8f)
        val layout = if (natural.size.width <= lane) natural else {
            measureKkText(measurers.at(measurer.scale * lane / natural.size.width), text, style, uppercase = true)
        }
        drawKkText(layout, chip.center.x, chip.center.y, settingsChipLabelColor(color), align = KkAlign.CENTER, valign = KkVAlign.CENTER)
    }
}

/** Minimum contrast of the small chip labels against their chip (WCAG AA for small text). */
internal const val SETTINGS_CHIP_LABEL_CONTRAST = 4.5f

/**
 * Label color on a role chip of [fill]: ink or bone, whichever contrasts more. A mid-tone fill
 * where neither reaches [SETTINGS_CHIP_LABEL_CONTRAST] (Deutan's threat blue) takes pure black
 * or white, which always reach at least √21 ≈ 4.58:1.
 */
internal fun settingsChipLabelColor(fill: Color): Color {
    val token = if (contrastRatio(Kk.Ink, fill) >= contrastRatio(Kk.Bone, fill)) Kk.Ink else Kk.Bone
    if (contrastRatio(token, fill) >= SETTINGS_CHIP_LABEL_CONTRAST) return token
    return if (contrastRatio(Color.Black, fill) >= contrastRatio(Color.White, fill)) Color.Black else Color.White
}

/** WCAG contrast ratio of two opaque colors. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** Index of the threat chip in [settingsRoleSwatches]. */
internal const val SETTINGS_THREAT_SWATCH = 1

/** The five role swatches in their board order with the palette's colors. */
internal fun settingsRoleSwatches(palette: KkRolePalette): List<Pair<SettingsRedesignText, Color>> = listOf(
    SettingsRedesignText.RoleYou to palette.you,
    SettingsRedesignText.RoleThreat to palette.threat,
    SettingsRedesignText.RoleHeat to palette.heat,
    SettingsRedesignText.RoleShield to palette.shield,
    SettingsRedesignText.RolePolarity to palette.pol,
)

private fun DrawScope.d(value: Float): Float = value * density

internal fun SettingsRow.label(language: AppLanguage): String = when (this) {
    SettingsRow.LANGUAGE -> language.text(ProfileText.Language)
    SettingsRow.SFX -> language.text(ProfileText.Sfx)
    SettingsRow.MUSIC -> language.text(ProfileText.Music)
    SettingsRow.MASTER_VOLUME -> language.text(ProfileText.MasterVolume)
    SettingsRow.SIMULATION_SPEED -> language.text(ProfileText.SimulationSpeed)
    SettingsRow.TEXT_SIZE -> language.text(ProfileText.TextSize)
    SettingsRow.SCREEN_SHAKE -> language.text(ProfileText.ScreenShake)
    SettingsRow.PARTICLES -> language.text(ProfileText.Particles)
    SettingsRow.DAMAGE_NUMBERS -> language.text(ProfileText.DamageNumbers)
    SettingsRow.DAMAGE_NUMBER_SIZE -> language.text(ProfileText.DamageNumberSize)
    SettingsRow.DAMAGE_NUMBER_FORMAT -> language.text(ProfileText.DamageNumberFormat)
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> language.text(ProfileText.DamageColorTiers)
    SettingsRow.RUN_STATISTICS_SIDE -> language.text(ProfileText.RunStatisticsSide)
    SettingsRow.COLOR_VISION -> language.text(SettingsRedesignText.ColorVision)
}

/** The row's (!) explanation. */
internal fun SettingsRow.about(language: AppLanguage): String = language.text(when (this) {
    SettingsRow.LANGUAGE -> SettingsRedesignText.AboutLanguage
    SettingsRow.SFX -> SettingsRedesignText.AboutSfx
    SettingsRow.MUSIC -> SettingsRedesignText.AboutMusic
    SettingsRow.MASTER_VOLUME -> SettingsRedesignText.AboutMasterVolume
    SettingsRow.SIMULATION_SPEED -> SettingsRedesignText.AboutSimulationSpeed
    SettingsRow.TEXT_SIZE -> SettingsRedesignText.AboutTextSize
    SettingsRow.SCREEN_SHAKE -> SettingsRedesignText.AboutScreenShake
    SettingsRow.PARTICLES -> SettingsRedesignText.AboutParticles
    SettingsRow.DAMAGE_NUMBERS -> SettingsRedesignText.AboutDamageNumbers
    SettingsRow.DAMAGE_NUMBER_SIZE -> SettingsRedesignText.AboutDamageNumberSize
    SettingsRow.DAMAGE_NUMBER_FORMAT -> SettingsRedesignText.AboutDamageNumberFormat
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> SettingsRedesignText.AboutDamageColorTiers
    SettingsRow.RUN_STATISTICS_SIDE -> SettingsRedesignText.AboutRunStatisticsSide
    SettingsRow.COLOR_VISION -> SettingsRedesignText.AboutColorVision
})

internal fun SettingsRow.isOn(preferences: PlayerPreferences): Boolean = when (this) {
    SettingsRow.SFX -> preferences.soundEnabled
    SettingsRow.MUSIC -> preferences.musicEnabled
    SettingsRow.SCREEN_SHAKE -> preferences.screenShake
    SettingsRow.DAMAGE_NUMBERS -> preferences.damageNumbers
    else -> false
}

/** Slider position 0..1 of a slider row. */
internal fun SettingsRow.sliderValue(preferences: PlayerPreferences): Float = when (this) {
    SettingsRow.MASTER_VOLUME -> preferences.masterVolume
    SettingsRow.TEXT_SIZE -> (preferences.textScale - 1f) / 0.75f
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> thresholdIndex(preferences).toFloat() / DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.lastIndex
    else -> 0f
}.coerceIn(0f, 1f)

/** Whether one step in [direction] still changes the value (the stepper greys out at the ends). */
internal fun SettingsRow.canStep(preferences: PlayerPreferences, direction: Int): Boolean {
    val value = sliderValue(preferences)
    return if (direction < 0) value > 0f else value < 1f
}

/** Short value next to a slider: percent, or the first damage color threshold. */
internal fun SettingsRow.sliderOutput(preferences: PlayerPreferences, language: AppLanguage): String = when (this) {
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> formatCompact(preferences.damageNumberTierThreshold.toLong(), language)
    else -> "${(sliderValueRaw(preferences) * 100f).roundToInt()}%"
}

private fun SettingsRow.sliderValueRaw(preferences: PlayerPreferences): Float = when (this) {
    SettingsRow.MASTER_VOLUME -> preferences.masterVolume
    SettingsRow.TEXT_SIZE -> preferences.textScale
    else -> 0f
}

internal fun settingsThresholdOutputSamples(language: AppLanguage): List<String> =
    DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.map { formatCompact(it.toLong(), language) }

private fun thresholdIndex(preferences: PlayerPreferences): Int =
    DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices.minByOrNull { index ->
        kotlin.math.abs(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[index].toLong() - preferences.damageNumberTierThreshold)
    } ?: 0

/** Spoken value of a row (state descriptions); matches what the row shows. */
internal fun settingValue(
    preferences: PlayerPreferences,
    row: SettingsRow,
    language: AppLanguage = preferences.language,
): String = when (row) {
    SettingsRow.LANGUAGE -> preferences.language.nativeName
    SettingsRow.SFX -> if (preferences.soundEnabled) language.text(ProfileText.On) else language.text(ProfileText.Off)
    SettingsRow.MUSIC -> if (preferences.musicEnabled) language.text(ProfileText.On) else language.text(ProfileText.Off)
    SettingsRow.MASTER_VOLUME -> "${(preferences.masterVolume * 100f).roundToInt()}%"
    SettingsRow.SIMULATION_SPEED -> formatMultiplier(preferences.simulationSpeed, language)
    SettingsRow.TEXT_SIZE -> "${(preferences.textScale * 100f).roundToInt()}%"
    SettingsRow.RUN_STATISTICS_SIDE -> language.text(
        if (preferences.runStatisticsOnLeft) ProfileText.LeftSide else ProfileText.RightSide,
    )
    SettingsRow.SCREEN_SHAKE -> if (preferences.screenShake) language.text(ProfileText.On) else language.text(ProfileText.Off)
    SettingsRow.PARTICLES, SettingsRow.DAMAGE_NUMBER_SIZE, SettingsRow.DAMAGE_NUMBER_FORMAT, SettingsRow.COLOR_VISION ->
        row.optionLabel(row.selectedOption(preferences), language)
    SettingsRow.DAMAGE_NUMBERS -> if (preferences.damageNumbers) language.text(ProfileText.On) else language.text(ProfileText.Off)
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
        val first = preferences.damageNumberTierThreshold.toLong()
        val second = first * DAMAGE_NUMBER_POWERFUL_MULTIPLIER
        val third = first * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER
        "${formatCompact(first, language)}/${formatCompact(second, language)}/${formatCompact(third, language)}"
    }
}

/** Damage number color tiers start at the threshold, four times it and twenty times it. */
internal const val DAMAGE_NUMBER_POWERFUL_MULTIPLIER = 4L
internal const val DAMAGE_NUMBER_DEVASTATING_MULTIPLIER = 20L

/** Damage number sample in the preview, formatted like the run formats it. */
internal fun settingsDamageSample(amount: Long, format: DamageNumberFormat, language: AppLanguage): String = when (format) {
    DamageNumberFormat.COMPACT -> formatCompact(amount, language)
    DamageNumberFormat.FULL -> amount.toString()
}

/** Tier color of a sample: bone, then you, heat and threat as the magnitude climbs. */
internal fun settingsDamageColor(amount: Long, threshold: Int, roles: KkRolePalette): Color = when {
    amount >= threshold * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER -> roles.threat
    amount >= threshold * DAMAGE_NUMBER_POWERFUL_MULTIPLIER -> roles.heat
    amount >= threshold -> roles.you
    else -> Kk.Bone
}
