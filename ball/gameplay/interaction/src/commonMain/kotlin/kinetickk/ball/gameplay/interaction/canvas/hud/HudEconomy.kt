// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_HEIGHT_DP
import kinetickk.ball.gameplay.interaction.layout.regularHeaderStep
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.layout.*

/**
 * Matter and key chips (and their phone forms). The key chip also shows at 0 Keys while a Totem chest waits (chests
 * publish in realspace only), with a lock in place of the key: the chest needs a Key.
 */
internal fun DrawScope.drawEconomy(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame, badgeBottom: Float) {
    val roles = hostMeasurer.roles
    val language = hostMeasurer.language
    // Chip values are UI text: they follow the text size (board size at the default).
    val measurer = HudMeasurers.ui(hostMeasurer)
    val matter = HudScratch.matter.of(language, engine.runMatter) { "+" + formatCompact(engine.runMatter, language) }
    val locked = engine.keys <= 0 && engine.totemChests.isNotEmpty()
    val keys = if (engine.keys > 0 || locked) HudScratch.keys.of(engine.keys.coerceAtLeast(0).toLong()) else null
    val keyIcon = if (locked) KkIcon.SYSTEM_LOCKED else KkIcon.SYSTEM_KEY
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            val height = frame.u(36f)
            val top = frame.u(24f)
            // Right-aligned left of the header's leftmost button: Build, or the FPS counter while it shows.
            val headerLeft = if (engine.settings.showFps) frame.buildCenterX - regularHeaderStep(frame.width, frame.density) else frame.buildCenterX
            var right = headerLeft - frame.u(20f + 10f)
            if (keys != null) right = drawHudChip(measurer, HudPath.KEY_CHIP, HudText.KEYS, keys, right, top, height, frame.t(21f), keyIcon, roles) - frame.u(8f)
            drawHudChip(measurer, HudPath.MATTER_CHIP, HudText.MATTER, matter, right, top, height, frame.t(21f), null, roles)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            // Inline with the badge: gem + value, key icon + count.
            val centerY = frame.u(12f) + (badgeBottom - frame.u(12f)) * 0.5f
            var x = frame.margin + kkLevelBadgeSize(HudMeasurers.display(hostMeasurer), engine.level.coerceAtLeast(0), density,
                HudScratch.label.current(), PHONE_BADGE_LABEL_SP * hudUiScale(hostMeasurer.scale), PHONE_BADGE_NUMBER_SP).width + frame.u(10f)
            drawKkGem(Offset(x + frame.u(5f), centerY), roles.you, 10f)
            x += frame.u(10f + 8f)
            // Display-cased like the chips of the other layouts ("+1,2 МЛН").
            val layout = HudDrawCache.layout(HudText.MATTER, measurer, matter, measurer.typography.condStyle(17f, tabular = true, lineHeightEm = 1f),
                uppercase = true)
            drawKkText(layout, x, centerY, Kk.Bone, valign = KkVAlign.CENTER)
            HudLayoutProbe.record(HudBlock.MATTER_CHIP, x - frame.u(18f), centerY - layout.kkBoxHeight * 0.5f, x + layout.size.width,
                centerY + layout.kkBoxHeight * 0.5f)
            x += layout.size.width + frame.u(12f)
            HudScratch.economyRight = x - frame.u(12f)
            if (keys != null) {
                drawKkIcon(keyIcon, Offset(x + frame.u(7f), centerY), frame.u(14f), Kk.Bone)
                val keyLayout = HudDrawCache.layout(HudText.KEYS, measurer, keys, measurer.typography.condStyle(17f, tabular = true, lineHeightEm = 1f),
                    uppercase = true)
                drawKkText(keyLayout, x + frame.u(19f), centerY, Kk.Bone, valign = KkVAlign.CENTER)
                HudLayoutProbe.record(HudBlock.KEY_CHIP, x, centerY - keyLayout.kkBoxHeight * 0.5f, x + frame.u(19f) + keyLayout.size.width,
                    centerY + keyLayout.kkBoxHeight * 0.5f)
                HudScratch.economyRight = x + frame.u(19f) + keyLayout.size.width
            }
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val top = frame.top + frame.u(PORTRAIT_CHIP_ROW_DP)
            val height = frame.u(PORTRAIT_CHIP_ROW_HEIGHT_DP)
            var left = frame.margin
            left = drawHudChipFromLeft(measurer, HudPath.MATTER_CHIP, HudText.MATTER, matter, left, top, height, 15f, null, roles)
            HudScratch.economyRight = left
            left += frame.u(6f)
            if (keys != null) {
                HudScratch.economyRight =
                    drawHudChipFromLeft(measurer, HudPath.KEY_CHIP, HudText.KEYS, keys, left, top, height, 15f, keyIcon, roles)
            }
        }
    }
}

/** Header chip (`.chip`) right-aligned at [right]; returns its left edge. */
private fun DrawScope.drawHudChip(
    measurer: TextMeasurer,
    path: HudPath,
    text: HudText,
    value: String,
    right: Float,
    top: Float,
    height: Float,
    fontSize: Float,
    icon: KkIcon?,
    roles: KkRolePalette,
): Float {
    val width = hudChipWidth(measurer, text, value, height, fontSize, icon)
    drawHudChipAt(measurer, path, text, value, right - width, top, width, height, fontSize, icon, roles)
    return right - width
}

private fun DrawScope.drawHudChipFromLeft(
    measurer: TextMeasurer,
    path: HudPath,
    text: HudText,
    value: String,
    left: Float,
    top: Float,
    height: Float,
    fontSize: Float,
    icon: KkIcon?,
    roles: KkRolePalette,
): Float {
    val width = hudChipWidth(measurer, text, value, height, fontSize, icon)
    drawHudChipAt(measurer, path, text, value, left, top, width, height, fontSize, icon, roles)
    return left + width
}

private fun DrawScope.hudChipWidth(measurer: TextMeasurer, text: HudText, value: String, height: Float, fontSize: Float, icon: KkIcon?): Float {
    val layout = HudDrawCache.layout(text, measurer, value, chipStyle(measurer, fontSize), uppercase = true)
    val k = height / d(34f)
    val lead = (if (icon == null) d(14f) else d(16f)) * k
    return d(10f) * k + lead + d(8f) * k + layout.size.width + d(14f) * k
}

private fun chipStyle(measurer: TextMeasurer, fontSize: Float) =
    measurer.typography.condStyle(fontSize, trackingEm = 0.02f, tabular = true, lineHeightEm = 1f)

private fun DrawScope.drawHudChipAt(
    measurer: TextMeasurer,
    path: HudPath,
    text: HudText,
    value: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    fontSize: Float,
    icon: KkIcon?,
    roles: KkRolePalette,
) {
    val k = height / d(34f)
    HudLayoutProbe.record(if (path == HudPath.KEY_CHIP) HudBlock.KEY_CHIP else HudBlock.MATTER_CHIP, left, top, left + width, top + height)
    drawPath(HudDrawCache.paths.slab(path.ordinal, left, top, left + width, top + height, d(8f) * k), Kk.Ink2.copy(alpha = 0.85f))
    val lead = (if (icon == null) d(14f) else d(16f)) * k
    val cy = top + height * 0.5f
    val leadX = left + d(10f) * k + lead * 0.5f
    if (icon == null) drawKkGem(Offset(leadX, cy), roles.you, 14f * k) else drawKkIcon(icon, Offset(leadX, cy), lead, Kk.Bone)
    val layout = HudDrawCache.layout(text, measurer, value, chipStyle(measurer, fontSize), uppercase = true)
    drawKkText(layout, left + d(10f) * k + lead + d(8f) * k, cy, Kk.Bone, valign = KkVAlign.CENTER)
}

/** `×N` chain counter with its draining window bar; hidden without a live combo. */
internal fun DrawScope.drawChain(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame) {
    HudScratch.chainLeft = Float.POSITIVE_INFINITY
    if (engine.combo < 2 || engine.comboTime <= 0f) return
    // Display type: the same size at every text-size setting.
    val measurer = HudMeasurers.display(hostMeasurer)
    val hot = engine.combo >= 20
    val color = if (hot) measurer.roles.you else Kk.Bone
    val tracking = CHAIN_TRACKING + colorTracking(if (hot) 1 else 0)
    val value = engine.combo.toLong()
    val window = engine.comboTime / engine.comboWindow.coerceAtLeast(0.001f)
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            val style = measurer.typography.condStyle(frame.t(60f), tabular = true, trackingEm = tracking, lineHeightEm = COND_LINE_HEIGHT_EM)
            val right = frame.width - frame.margin
            val top = frame.chainTop
            val width = drawKkTabularNumber(measurer, value, style, right, top, color, KkAlign.END, prefix = CHAIN_PREFIX)
            val barTop = top + digitBoxHeight(measurer, style) + frame.u(6f)
            val bar = HudDrawCache.rect(HudRect.CHAIN, right - frame.u(120f), barTop, right, barTop + frame.u(4f))
            drawKkMeter(bar, window, color, measurer.roles)
            HudLayoutProbe.record(HudBlock.CHAIN, min(right - width, bar.left), top, right, bar.bottom)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val style = measurer.typography.condStyle(26f, tabular = true, trackingEm = tracking, lineHeightEm = COND_LINE_HEIGHT_EM)
            val right = frame.buildCenterX - frame.u(24f + 10f)
            val center = frame.u(30f)
            val width = drawKkTabularNumber(measurer, value, style, right, center, color, KkAlign.END, KkVAlign.CENTER, prefix = CHAIN_PREFIX)
            val barTop = center + digitBoxHeight(measurer, style) * 0.5f + frame.u(3f)
            val bar = HudDrawCache.rect(HudRect.CHAIN, right - width, barTop, right, barTop + frame.u(2f))
            drawKkMeter(bar, window, color, measurer.roles)
            HudLayoutProbe.record(HudBlock.CHAIN, right - width, center - digitBoxHeight(measurer, style) * 0.5f, right, bar.bottom)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val style = measurer.typography.condStyle(30f, tabular = true, trackingEm = tracking, lineHeightEm = COND_LINE_HEIGHT_EM)
            val right = frame.width - frame.margin
            val center = frame.top + frame.u(PORTRAIT_CHIP_ROW_DP + PORTRAIT_CHIP_ROW_HEIGHT_DP * 0.5f)
            val width = drawKkTabularNumber(measurer, value, style, right, center, color, KkAlign.END, KkVAlign.CENTER, prefix = CHAIN_PREFIX)
            val barTop = center + digitBoxHeight(measurer, style) * 0.5f + frame.u(3f)
            val bar = HudDrawCache.rect(HudRect.CHAIN, right - width, barTop, right, barTop + frame.u(2f))
            drawKkMeter(bar, window, color, measurer.roles)
            HudLayoutProbe.record(HudBlock.CHAIN, right - width, center - digitBoxHeight(measurer, style) * 0.5f, right, bar.bottom)
            HudScratch.chainLeft = right - width
        }
    }
}

private const val CHAIN_PREFIX = "×"
private const val CHAIN_TRACKING = 0.005f

/**
 * A negligible extra tracking per color [variant] for numbers drawn in more than one color. The
 * text measurer shares one paragraph between styles that differ only in color, and painting a
 * paragraph in another color than last time reshapes it (allocating every frame when a value
 * flips between colors); a distinct style per color keeps each digit layout in one color.
 */
internal fun colorTracking(variant: Int): Float = variant * 0.0005f

/** CSS line-box height of a digit in [style] (numbers drawn with [drawKkTabularNumber]). */
internal fun digitBoxHeight(measurer: TextMeasurer, style: androidx.compose.ui.text.TextStyle): Float =
    measureKkText(measurer, "0", style).kkBoxHeight
