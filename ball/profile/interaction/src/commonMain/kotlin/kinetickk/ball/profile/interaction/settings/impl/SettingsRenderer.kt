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
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.WindowMode
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
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkTagVariant
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
import kinetickk.foundation.design.drawKkTag
import kinetickk.foundation.design.kkLoop
import kinetickk.foundation.design.kkProgress
import kinetickk.foundation.design.kkPulse
import kinetickk.foundation.design.kkTagSize
import kinetickk.foundation.design.label
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.drawKkThreatHatch
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.measureKkTooltip
import kinetickk.foundation.design.formatCompact
import kinetickk.foundation.design.formatMultiplier
import kinetickk.foundation.design.kkShearOffset
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
            SettingsTextKind.READOUT -> measureKkText(ui, text, readoutStyle(), uppercase = true).size.width.toFloat()
        }

        override fun rowLabelLineHeight(): Float =
            measureKkText(body, ROW_LABEL_PROBE, typography.condStyle(type.rowLabel), uppercase = true).kkBoxHeight
    }
}

/** Tallest Latin and Cyrillic capitals, for the row label line box. */
private const val ROW_LABEL_PROBE = "ЁЙAG"


private fun SettingsMeasurers.titleStyle(type: SettingsType): TextStyle = typography.condStyle(type.title, lineHeightEm = 1f)

internal fun SettingsMeasurers.outputStyle(): TextStyle = typography.condStyle(22f, tabular = true, lineHeightEm = 1f)

/** Readout value (`.t-mono` 16). */
internal fun SettingsMeasurers.readoutStyle(): TextStyle = typography.monoStyle(16f, lineHeightEm = 1f)

/** Key cap text in a key cell (`.t-label` 16). */
internal fun SettingsMeasurers.keyStyle(): TextStyle = typography.labelStyle(16f, trackingEm = 0.07f)

/** Transient interaction state the canvas reflects; the feature owns it. */
internal class SettingsFrame(
    val layout: SettingsLayout,
    val preferences: PlayerPreferences,
    val activeRow: SettingsRow?,
    val hover: SettingsTarget?,
    val focus: SettingsTarget?,
    val info: SettingsRow?,
    val time: Float,
    /** Key capture and reset state (see [SettingsState]). */
    val capture: GameAction? = null,
    val swapped: GameAction? = null,
    val swappedAt: Float = SETTINGS_NEVER,
    val rejectAt: Float = SETTINGS_NEVER,
    val resetArmed: Boolean = false,
) {
    val platform: SettingsPlatform get() = layout.platform

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
    val text = measureKkTooltip(body, row.about(language, layout.platform), layout.density)
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
    layout.title?.let { title ->
        drawKkText(
            measurers.body, measurers.language.text(ProfileText.SettingsTitle), measurers.titleStyle(type),
            title.left, title.center.y, Kk.Bone, valign = KkVAlign.CENTER, uppercase = true,
            maxWidth = title.width,
        )
    }

    val tabMeasurer = measurers.fit(layout.tabScale)
    layout.tabs.forEach { tab ->
        val target = SettingsTarget.Tab(tab.group)
        drawKkTab(
            tabMeasurer, tab.bounds, measurers.language.text(tab.group.label), tab.group == layout.group,
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
    val platform = frame.platform
    if (frame.activeRow == row.row) {
        drawKkSlab(row.bounds, Kk.Ink3, cut = d(10f))
        val cy = row.bounds.center.y
        drawKkSheared(Rect(row.bounds.left - d(14f), cy - d(13f), row.bounds.left - d(9f), cy + d(13f)), roles.you)
    }
    drawKkText(settingsRowLabel(measurers, row, frame.layout.mode, type), row.label.left, row.label.center.y, Kk.Bone, valign = KkVAlign.CENTER)

    if (row.hasInfo) {
        val info = SettingsTarget.Info(row.row)
        drawKkInfoButton(measurers.fixed, row.info, active = frame.openInfo == row.row || frame.hover == info || frame.focus == info)
        if (frame.focus == info) drawSettingsFocus(row.info)
    }

    when (row.control) {
        SettingsControl.SEGMENTED -> {
            val selected = row.row.selectedOption(preferences, platform)
            val measurer = measurers.fit(row.controlScale)
            // The mode the system shows when it differs from the stored one (OS-driven): outlined, not filled.
            val live = if (row.row == SettingsRow.WINDOW_MODE) platform.liveModeOverride(preferences)?.ordinal else null
            row.options.forEachIndexed { option, cell ->
                val target = SettingsTarget.Option(row.row, option)
                drawKkSegment(
                    measurer, cell, row.row.optionLabel(option, language, platform), option == selected,
                    hovered = frame.hover == target, focused = frame.focus == target,
                )
                if (option == live) drawKkSheared(cell, roles.you, style = kkStroke(d(1.5f)))
            }
            if (row.swatches.isNotEmpty()) drawSettingsSwatches(row.swatches, preferences.colorVision.previewPalette(), measurers)
        }
        SettingsControl.TOGGLE -> {
            val toggle = row.toggle ?: return
            drawKkToggle(
                measurers.fixed, toggle, if (row.row.isOn(preferences, platform)) 1f else 0f,
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
            drawKkStepButton(decrease, plus = false, hovered = frame.hover == minus, enabled = row.row.canStep(preferences, -1, platform))
            drawKkStepButton(increase, plus = true, hovered = frame.hover == plus, enabled = row.row.canStep(preferences, 1, platform))
            if (frame.focus == minus) drawSettingsFocus(decrease)
            if (frame.focus == plus) drawSettingsFocus(increase)
            val enabled = row.row.isEnabled(preferences)
            if (enabled) drawKkSlider(track, row.row.sliderValue(preferences, platform), roles)
            else drawKkSlider(track, row.row.sliderValue(preferences, platform), roles.copy(you = Kk.Mute2))
            // A disabled row reads Mute 2; a live window size that differs from the stored preset reads Mute.
            val color = when {
                !enabled -> Kk.Mute2
                row.row == SettingsRow.WINDOW_SIZE && platform.liveSizeOverride(preferences) != null -> Kk.Mute
                else -> Kk.Bone
            }
            drawKkText(
                measurers.ui, row.row.sliderOutput(preferences, language, platform), measurers.outputStyle(),
                output.right, output.center.y, color, align = KkAlign.END, valign = KkVAlign.CENTER, uppercase = true,
            )
        }
        SettingsControl.KEYBIND -> drawSettingsKeyCell(frame, measurers, row)
        SettingsControl.READOUT -> {
            val readout = row.readout ?: return
            drawKkText(
                measurers.ui, settingValue(preferences, row.row, language, platform), measurers.readoutStyle(),
                readout.right, readout.center.y, Kk.Bone, align = KkAlign.END, valign = KkVAlign.CENTER, uppercase = true,
            )
        }
        SettingsControl.ACTION -> {
            val button = row.action ?: return
            val armed = frame.resetArmed
            drawKkButton(
                measurers.ui, button,
                language.text(if (armed) SettingsRedesignText.ResetBindingsConfirm else SettingsRedesignText.ResetBindingsButton),
                if (armed) KkButtonVariant.HAZARD else KkButtonVariant.GHOST, KkButtonSize.SM,
                hover = if (frame.hover == SettingsTarget.Reset) 1f else 0f, focused = frame.focus == SettingsTarget.Reset,
                enabled = settingsResetAvailable(preferences, platform), armed = armed, time = frame.time,
            )
        }
    }
}

/** Swap highlight length and the share of it spent fading out. */
internal const val SETTINGS_SWAP_SECONDS = 1.6f
private const val SETTINGS_SWAP_FADE_SECONDS = 0.4f
private const val SETTINGS_SWAP_SLIDE_SECONDS = 0.22f
/** A refused key flashes hazard for one frame (16 ms), then the cell shakes ±3 dp for 120 ms. */
private const val SETTINGS_REJECT_FLASH_SECONDS = 0.016f
private const val SETTINGS_REJECT_SHAKE_SECONDS = 0.12f
/** Capture caret: 530 ms on, 530 ms off; the volt echo pulses every 1.2 s. */
private const val SETTINGS_CARET_PERIOD_SECONDS = 1.06f
private const val SETTINGS_ECHO_PERIOD_SECONDS = 1.2f

/**
 * One key cell: ink-3 sheared cell with the key cap in bone (ink-4 on hover, bone outline on
 * focus); a volt face with a blinking ink caret and a pulsing echo, and no text, while it waits for
 * a key; a hazard flash and a shake for a refused key; a `you` pip when the key is not the
 * default; and, for the row that received the old key of a swap, a volt echo and a Swapped tag.
 */
private fun DrawScope.drawSettingsKeyCell(frame: SettingsFrame, measurers: SettingsMeasurers, row: SettingsRowLayout) {
    val cell = row.key ?: return
    val action = row.row.boundAction ?: return
    val roles = measurers.roles
    val target = SettingsTarget.Key(action)
    val capturing = frame.capture == action
    val bindings = frame.preferences.keyBindings
    val key = bindings.key(action)
    val sinceSwap = frame.time - frame.swappedAt
    val swapped = frame.swapped == action && sinceSwap in 0f..SETTINGS_SWAP_SECONDS
    val sinceReject = frame.time - frame.rejectAt
    val shakeX = if (capturing && sinceReject in SETTINGS_REJECT_FLASH_SECONDS..SETTINGS_REJECT_FLASH_SECONDS + SETTINGS_REJECT_SHAKE_SECONDS) {
        // Three half-swings of ±3 dp, damped to rest.
        val t = (sinceReject - SETTINGS_REJECT_FLASH_SECONDS) / SETTINGS_REJECT_SHAKE_SECONDS
        d(3f) * sin(t * PI.toFloat() * 3f) * (1f - t)
    } else 0f
    val face = translateRect(cell, shakeX)
    if (capturing) {
        val pulse = kkPulse(frame.time, SETTINGS_ECHO_PERIOD_SECONDS)
        drawKkSheared(translateRect(face, d(4f) * pulse, d(4f) * pulse), roles.you.copy(alpha = 0.45f))
        val flash = sinceReject in 0f..SETTINGS_REJECT_FLASH_SECONDS
        drawKkSheared(face, if (flash) Kk.Hazard else roles.you)
        if (kkLoop(frame.time, SETTINGS_CARET_PERIOD_SECONDS) < 0.5f) {
            val center = face.center
            drawKkSheared(Rect(center.x - d(1f), center.y - d(8f), center.x + d(1f), center.y + d(8f)), Kk.Ink)
        }
    } else {
        if (swapped) drawKkSheared(translateRect(face, d(4f), d(4f)), roles.you)
        drawKkSheared(face, if (frame.hover == target) Kk.Ink4 else Kk.Ink3)
        val slide = if (swapped) (1f - kkProgress(sinceSwap * 1000f, SETTINGS_SWAP_SLIDE_SECONDS * 1000f, KkEase.Pull)) * d(12f) else 0f
        drawKkText(
            measurers.ui, key.label(measurers.language), measurers.keyStyle(), face.center.x + slide, face.center.y, Kk.Bone,
            align = KkAlign.CENTER, valign = KkVAlign.CENTER, uppercase = true, maxWidth = face.width - d(16f),
        )
    }
    if (key != KeyBindings.DEFAULT.key(action)) {
        drawKkSheared(Rect(face.left + d(5f), face.center.y - d(6f), face.left + d(9f), face.center.y + d(6f)), roles.you)
    }
    if (frame.focus == target) drawSettingsFocus(face)
    if (swapped) {
        val fade = ((SETTINGS_SWAP_SECONDS - sinceSwap) / SETTINGS_SWAP_FADE_SECONDS).coerceIn(0f, 1f)
        val alpha = KkEase.Out.transform(fade)
        val tag = measurers.language.text(SettingsRedesignText.Swapped)
        val size = kkTagSize(measurers.fixed, tag, density)
        drawKkTag(
            measurers.fixed, tag, Offset(face.left - d(10f) - size.width, face.center.y - size.height * 0.5f),
            KkTagVariant.YOU, alpha = alpha,
        )
    }
}

private fun translateRect(rect: Rect, dx: Float, dy: Float = 0f): Rect =
    if (dx == 0f && dy == 0f) rect else Rect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)

/** Smallest factor a one-line row label shrinks to before it ends in an ellipsis. */
private const val SETTINGS_LABEL_MIN_FIT = 0.8f

/**
 * A row label in its box: compact landscape and the one-line rows of portrait wrap to two lines
 * (their layout made room); a single-line label wider than its box shrinks to 80 % first.
 */
private fun settingsRowLabel(
    measurers: SettingsMeasurers,
    row: SettingsRowLayout,
    mode: SettingsLayoutMode,
    type: SettingsType,
): TextLayoutResult {
    val text = row.row.label(measurers.language)
    val style = measurers.typography.condStyle(type.rowLabel)
    val stacked = row.control == SettingsControl.SEGMENTED || row.control == SettingsControl.SLIDER
    val lines = if (mode == SettingsLayoutMode.COMPACT_PORTRAIT && !stacked) 2 else type.rowLabelLines
    val wrapped = measureKkText(measurers.body, text, style, uppercase = true, maxWidth = row.label.width, maxLines = lines)
    if (!wrapped.hasVisualOverflow && (lines > 1 || wrapped.lineCount == 1)) return wrapped
    val natural = measureKkText(measurers.body, text, style, uppercase = true).size.width.toFloat()
    val factor = (row.label.width * lines / natural.coerceAtLeast(1f)).coerceIn(SETTINGS_LABEL_MIN_FIT, 1f)
    return measureKkText(measurers.at(measurers.bodyScale * factor), text, style, uppercase = true, maxWidth = row.label.width, maxLines = lines)
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
    SettingsRow.MINIMAP -> language.text(SettingsRedesignText.Minimap)
    SettingsRow.PAUSE_ON_FOCUS_LOSS -> language.text(SettingsRedesignText.PauseOnFocusLoss)
    SettingsRow.WINDOW_MODE -> language.text(SettingsRedesignText.WindowMode)
    SettingsRow.WINDOW_SIZE -> language.text(SettingsRedesignText.WindowSize)
    SettingsRow.FULLSCREEN -> language.text(SettingsRedesignText.Fullscreen)
    SettingsRow.VSYNC -> language.text(SettingsRedesignText.Vsync)
    SettingsRow.FRAME_RATE_CAP -> language.text(SettingsRedesignText.FrameRateCap)
    SettingsRow.REFRESH_RATE -> language.text(SettingsRedesignText.RefreshRate)
    SettingsRow.IMMERSIVE -> language.text(SettingsRedesignText.Immersive)
    SettingsRow.INTERFACE_SCALE -> language.text(SettingsRedesignText.InterfaceScale)
    SettingsRow.SHOW_FPS -> language.text(SettingsRedesignText.ShowFps)
    SettingsRow.REACTION_EFFECTS -> language.text(SettingsRedesignText.ReactionEffects)
    SettingsRow.RESET_BINDINGS -> language.text(SettingsRedesignText.ResetBindings)
    SettingsRow.BIND_DASH,
    SettingsRow.BIND_BRAKE,
    SettingsRow.BIND_SKILL_1,
    SettingsRow.BIND_SKILL_2,
    SettingsRow.BIND_SKILL_3,
    SettingsRow.BIND_APEX,
    SettingsRow.BIND_PAUSE,
    SettingsRow.BIND_CHOICE_1,
    SettingsRow.BIND_CHOICE_2,
    SettingsRow.BIND_CHOICE_3,
    SettingsRow.BIND_CHOICE_4,
    SettingsRow.BIND_REROLL,
    SettingsRow.BIND_RESTART,
    SettingsRow.BIND_PERFORMANCE,
    -> settingsActionName(checkNotNull(boundAction), language)
}

/** The name of a rebindable gameplay action ("Active 2" / «Активный эффект 2»). */
internal fun settingsActionName(action: GameAction, language: AppLanguage): String = when (action) {
    GameAction.DASH -> language.text(SettingsRedesignText.ActionDash)
    GameAction.BRAKE -> language.text(SettingsRedesignText.ActionBrake)
    GameAction.SKILL_1 -> language.text(SettingsRedesignText.ActionSkill, 1)
    GameAction.SKILL_2 -> language.text(SettingsRedesignText.ActionSkill, 2)
    GameAction.SKILL_3 -> language.text(SettingsRedesignText.ActionSkill, 3)
    GameAction.APEX -> language.text(SettingsRedesignText.ActionApex)
    GameAction.PAUSE -> language.text(SettingsRedesignText.ActionPause)
    GameAction.REROLL -> language.text(SettingsRedesignText.ActionReroll)
    GameAction.RESTART -> language.text(SettingsRedesignText.ActionRestart)
    GameAction.CHOICE_1 -> language.text(SettingsRedesignText.ActionChoice, 1)
    GameAction.CHOICE_2 -> language.text(SettingsRedesignText.ActionChoice, 2)
    GameAction.CHOICE_3 -> language.text(SettingsRedesignText.ActionChoice, 3)
    GameAction.CHOICE_4 -> language.text(SettingsRedesignText.ActionChoice, 4)
    GameAction.PERFORMANCE_HUD -> language.text(SettingsRedesignText.ActionPerformance)
}

/**
 * The row's (!) explanation; on key binding rows the key cell's accessibility hint. The refresh
 * rate explains a choice where [platform] offers one and the monitor readout elsewhere.
 */
internal fun SettingsRow.about(language: AppLanguage, platform: SettingsPlatform = SettingsPlatform.DEFAULT): String = when (this) {
    SettingsRow.LANGUAGE -> language.text(SettingsRedesignText.AboutLanguage)
    SettingsRow.SFX -> language.text(SettingsRedesignText.AboutSfx)
    SettingsRow.MUSIC -> language.text(SettingsRedesignText.AboutMusic)
    SettingsRow.MASTER_VOLUME -> language.text(SettingsRedesignText.AboutMasterVolume)
    SettingsRow.SIMULATION_SPEED -> language.text(SettingsRedesignText.AboutSimulationSpeed)
    SettingsRow.TEXT_SIZE -> language.text(SettingsRedesignText.AboutTextSize)
    SettingsRow.SCREEN_SHAKE -> language.text(SettingsRedesignText.AboutScreenShake)
    SettingsRow.PARTICLES -> language.text(SettingsRedesignText.AboutParticles)
    SettingsRow.DAMAGE_NUMBERS -> language.text(SettingsRedesignText.AboutDamageNumbers)
    SettingsRow.DAMAGE_NUMBER_SIZE -> language.text(SettingsRedesignText.AboutDamageNumberSize)
    SettingsRow.DAMAGE_NUMBER_FORMAT -> language.text(SettingsRedesignText.AboutDamageNumberFormat)
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> language.text(SettingsRedesignText.AboutDamageColorTiers)
    SettingsRow.RUN_STATISTICS_SIDE -> language.text(SettingsRedesignText.AboutRunStatisticsSide)
    SettingsRow.COLOR_VISION -> language.text(SettingsRedesignText.AboutColorVision)
    SettingsRow.MINIMAP -> language.text(SettingsRedesignText.AboutMinimap)
    SettingsRow.PAUSE_ON_FOCUS_LOSS -> language.text(SettingsRedesignText.AboutPauseOnFocusLoss)
    SettingsRow.WINDOW_MODE -> language.text(SettingsRedesignText.AboutWindowMode)
    SettingsRow.WINDOW_SIZE -> language.text(SettingsRedesignText.AboutWindowSize)
    SettingsRow.FULLSCREEN -> language.text(SettingsRedesignText.AboutFullscreen)
    SettingsRow.VSYNC -> language.text(SettingsRedesignText.AboutVsync)
    SettingsRow.FRAME_RATE_CAP -> language.text(SettingsRedesignText.AboutFrameRateCap)
    SettingsRow.REFRESH_RATE -> language.text(
        if (platform.control(this) == SettingsControl.READOUT) SettingsRedesignText.AboutRefreshRateDesktop
        else SettingsRedesignText.AboutRefreshRateAndroid,
    )
    SettingsRow.IMMERSIVE -> language.text(SettingsRedesignText.AboutImmersive)
    SettingsRow.INTERFACE_SCALE -> language.text(SettingsRedesignText.AboutInterfaceScale)
    SettingsRow.SHOW_FPS -> language.text(SettingsRedesignText.AboutShowFps)
    SettingsRow.REACTION_EFFECTS -> language.text(SettingsRedesignText.AboutReactionEffects)
    SettingsRow.RESET_BINDINGS -> language.text(SettingsRedesignText.AboutResetBindings)
    SettingsRow.BIND_DASH -> language.text(SettingsRedesignText.AboutBindDash)
    SettingsRow.BIND_BRAKE -> language.text(SettingsRedesignText.AboutBindBrake)
    SettingsRow.BIND_SKILL_1 -> language.text(SettingsRedesignText.AboutBindSkill, 1)
    SettingsRow.BIND_SKILL_2 -> language.text(SettingsRedesignText.AboutBindSkill, 2)
    SettingsRow.BIND_SKILL_3 -> language.text(SettingsRedesignText.AboutBindSkill, 3)
    SettingsRow.BIND_APEX -> language.text(SettingsRedesignText.AboutBindApex)
    SettingsRow.BIND_PAUSE -> language.text(SettingsRedesignText.AboutBindPause)
    SettingsRow.BIND_CHOICE_1 -> language.text(SettingsRedesignText.AboutBindChoice, 1)
    SettingsRow.BIND_CHOICE_2 -> language.text(SettingsRedesignText.AboutBindChoice, 2)
    SettingsRow.BIND_CHOICE_3 -> language.text(SettingsRedesignText.AboutBindChoice, 3)
    SettingsRow.BIND_CHOICE_4 -> language.text(SettingsRedesignText.AboutBindChoice, 4)
    SettingsRow.BIND_REROLL -> language.text(SettingsRedesignText.AboutBindReroll)
    SettingsRow.BIND_RESTART -> language.text(SettingsRedesignText.AboutBindRestart)
    SettingsRow.BIND_PERFORMANCE -> language.text(SettingsRedesignText.AboutBindPerformance)
}

internal fun SettingsRow.isOn(preferences: PlayerPreferences, platform: SettingsPlatform = SettingsPlatform.DEFAULT): Boolean = when (this) {
    SettingsRow.SFX -> preferences.soundEnabled
    SettingsRow.MUSIC -> preferences.musicEnabled
    SettingsRow.SCREEN_SHAKE -> preferences.screenShake
    SettingsRow.DAMAGE_NUMBERS -> preferences.damageNumbers
    SettingsRow.MINIMAP -> preferences.minimapEnabled
    SettingsRow.PAUSE_ON_FOCUS_LOSS -> preferences.pauseOnFocusLoss
    SettingsRow.VSYNC -> preferences.vsync
    SettingsRow.IMMERSIVE -> preferences.immersive
    SettingsRow.SHOW_FPS -> preferences.showFps
    // Live browser state, never stored.
    SettingsRow.FULLSCREEN -> platform.browserFullscreen
    else -> false
}

/** False while the row's control is disabled: the window size outside windowed mode. */
internal fun SettingsRow.isEnabled(preferences: PlayerPreferences): Boolean =
    this != SettingsRow.WINDOW_SIZE || preferences.windowMode == WindowMode.WINDOWED

/** Whether Reset keys can be pressed: some key is not its default. */
internal fun settingsResetAvailable(preferences: PlayerPreferences, platform: SettingsPlatform): Boolean =
    platform.offers(SettingsRow.RESET_BINDINGS) && !preferences.keyBindings.isDefault

/** Slider position 0..1 of a slider row. */
internal fun SettingsRow.sliderValue(preferences: PlayerPreferences, platform: SettingsPlatform = SettingsPlatform.DEFAULT): Float = when (this) {
    SettingsRow.MASTER_VOLUME -> preferences.masterVolume
    SettingsRow.TEXT_SIZE -> (preferences.textScale - 1f) / 0.75f
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> thresholdIndex(preferences).toFloat() / DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.lastIndex
    SettingsRow.WINDOW_SIZE -> settingsWindowSizePosition(preferences, platform)
    SettingsRow.FRAME_RATE_CAP -> FRAME_RATE_CAP_STEPS.indexOf(preferences.frameRateCap).coerceAtLeast(0).toFloat() / FRAME_RATE_CAP_STEPS.lastIndex
    else -> 0f
}.coerceIn(0f, 1f)

/** Whether one step in [direction] still changes the value (the stepper greys out at the ends). */
internal fun SettingsRow.canStep(
    preferences: PlayerPreferences,
    direction: Int,
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): Boolean = when (this) {
    SettingsRow.WINDOW_SIZE -> settingsWindowSizeStep(preferences, platform, direction) != null
    SettingsRow.FRAME_RATE_CAP -> settingsFrameRateCapStep(preferences, direction) != null
    else -> {
        val value = sliderValue(preferences, platform)
        if (direction < 0) value > 0f else value < 1f
    }
}

/** Short value next to a slider: percent, the first damage color threshold, a window size or a frame limit. */
internal fun SettingsRow.sliderOutput(
    preferences: PlayerPreferences,
    language: AppLanguage,
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): String = when (this) {
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> formatCompact(preferences.damageNumberTierThreshold.toLong(), language)
    SettingsRow.WINDOW_SIZE -> platform.liveSizeOverride(preferences)?.let { settingsWindowSizeText(it.widthDp, it.heightDp, language) }
        ?: settingsWindowSizeText(preferences.windowSize.widthDp, preferences.windowSize.heightDp, language)
    SettingsRow.FRAME_RATE_CAP -> settingsFrameRateText(preferences.frameRateCap, language)
    else -> "${(sliderValueRaw(preferences) * 100f).roundToInt()}%"
}

/** "1600×900". */
internal fun settingsWindowSizeText(widthDp: Int, heightDp: Int, language: AppLanguage): String =
    language.text(SettingsRedesignText.WindowSizeValue, widthDp, heightDp)

/** "60 FPS" / «60 к/с», or "Unlimited" for 0. */
internal fun settingsFrameRateText(fps: Int, language: AppLanguage): String =
    if (fps <= 0) language.text(SettingsRedesignText.FrameRateUnlimited) else language.text(SettingsRedesignText.FrameRateValue, fps)

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

private fun onOff(on: Boolean, language: AppLanguage): String = language.text(if (on) ProfileText.On else ProfileText.Off)

/** Spoken value of a row (state descriptions); matches what the row shows. */
internal fun settingValue(
    preferences: PlayerPreferences,
    row: SettingsRow,
    language: AppLanguage = preferences.language,
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): String = when (row) {
    SettingsRow.LANGUAGE -> preferences.language.nativeName
    SettingsRow.SFX -> onOff(preferences.soundEnabled, language)
    SettingsRow.MUSIC -> onOff(preferences.musicEnabled, language)
    SettingsRow.MASTER_VOLUME -> "${(preferences.masterVolume * 100f).roundToInt()}%"
    SettingsRow.SIMULATION_SPEED -> formatMultiplier(preferences.simulationSpeed, language)
    SettingsRow.TEXT_SIZE -> "${(preferences.textScale * 100f).roundToInt()}%"
    SettingsRow.RUN_STATISTICS_SIDE -> language.text(
        if (preferences.runStatisticsOnLeft) ProfileText.LeftSide else ProfileText.RightSide,
    )
    SettingsRow.SCREEN_SHAKE -> onOff(preferences.screenShake, language)
    SettingsRow.PARTICLES, SettingsRow.DAMAGE_NUMBER_SIZE, SettingsRow.DAMAGE_NUMBER_FORMAT, SettingsRow.COLOR_VISION,
    SettingsRow.WINDOW_MODE, SettingsRow.INTERFACE_SCALE, SettingsRow.REACTION_EFFECTS,
    -> row.optionLabel(row.selectedOption(preferences, platform), language, platform)
    SettingsRow.DAMAGE_NUMBERS -> onOff(preferences.damageNumbers, language)
    SettingsRow.MINIMAP -> onOff(preferences.minimapEnabled, language)
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
        val first = preferences.damageNumberTierThreshold.toLong()
        val second = first * DAMAGE_NUMBER_POWERFUL_MULTIPLIER
        val third = first * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER
        "${formatCompact(first, language)}/${formatCompact(second, language)}/${formatCompact(third, language)}"
    }
    SettingsRow.PAUSE_ON_FOCUS_LOSS, SettingsRow.VSYNC, SettingsRow.IMMERSIVE, SettingsRow.SHOW_FPS, SettingsRow.FULLSCREEN ->
        onOff(row.isOn(preferences, platform), language)
    SettingsRow.WINDOW_SIZE, SettingsRow.FRAME_RATE_CAP -> row.sliderOutput(preferences, language, platform)
    SettingsRow.REFRESH_RATE -> if (platform.control(row) == SettingsControl.READOUT) {
        val hz = platform.support.monitorRefreshHz
        if (hz > 0) refreshRateText(hz, language) else language.text(SettingsRedesignText.RefreshUnknown)
    } else {
        row.optionLabel(row.selectedOption(preferences, platform), language, platform)
    }
    SettingsRow.RESET_BINDINGS -> language.text(SettingsRedesignText.ResetBindingsButton)
    else -> preferences.keyBindings.key(checkNotNull(row.boundAction)).label(language)
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
