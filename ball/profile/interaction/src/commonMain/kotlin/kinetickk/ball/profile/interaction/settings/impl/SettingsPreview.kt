// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.KkVisionMode
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkGrid
import kinetickk.foundation.design.drawKkSheared
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.drawKkThreatHatch
import kinetickk.foundation.design.kkLoop
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Board width of the preview column (`Settings.dc.html` aside, 336 px); contents scale with it. */
private const val PREVIEW_UNITS = 336f

/** Preview role palette for the Color vision value being edited (the rest of the screen reads the provided palette). */
internal fun ColorVision.previewPalette(): KkRolePalette = when (this) {
    ColorVision.DEFAULT -> KkVisionMode.DEFAULT.palette
    ColorVision.PROTAN -> KkVisionMode.PROTAN.palette
    ColorVision.DEUTAN -> KkVisionMode.DEUTAN.palette
    ColorVision.TRITAN -> KkVisionMode.TRITAN.palette
    ColorVision.MONO -> KkVisionMode.MONO.palette
}

/** Height in board units of the preview for [group] (arena or scene box plus its companion strip). */
internal fun settingsPreviewUnits(group: SettingsGroup): Float = when (group) {
    SettingsGroup.GAME -> 240f
    SettingsGroup.SOUND -> 250f
    SettingsGroup.GRAPHICS -> 300f + 12f + 30f + 6f + 14f
    SettingsGroup.INTERFACE -> 110f + 12f + 110f
}

/** Live preview column: reacts to the current values of the selected tab. */
internal fun DrawScope.drawSettingsPreview(area: Rect, frame: SettingsFrame, measurers: SettingsMeasurers) {
    val group = frame.layout.group
    val u = area.width / PREVIEW_UNITS
    val scale = min(1f, area.height / (settingsPreviewUnits(group) * u))
    val unit = u * scale
    val box = Rect(area.left, area.top, area.left + PREVIEW_UNITS * unit, area.top)
    val preview = PreviewScope(this, box.left, box.top, unit, measurers, frame.preferences, frame.time)
    when (group) {
        SettingsGroup.GAME -> preview.game()
        SettingsGroup.SOUND -> preview.sound()
        SettingsGroup.GRAPHICS -> preview.graphics()
        SettingsGroup.INTERFACE -> preview.interfaceSample()
    }
}

private class PreviewScope(
    val draw: DrawScope,
    val left: Float,
    val top: Float,
    val u: Float,
    val measurers: SettingsMeasurers,
    val preferences: PlayerPreferences,
    val time: Float,
) {
    val language = measurers.language
    /** Text measurer at the preview's own scale (board px × unit). */
    val text = measurers.at(u / draw.density)

    fun x(value: Float) = left + value * u
    fun y(value: Float) = top + value * u
    fun at(xValue: Float, yValue: Float) = Offset(x(xValue), y(yValue))
    fun rect(l: Float, t: Float, r: Float, b: Float) = Rect(x(l), y(t), x(r), y(b))

    /** `.pv`: ink-1 box with a 16 px grid and a 1 px line inset. */
    fun box(height: Float, grid: Boolean = true): Rect {
        val bounds = rect(0f, 0f, PREVIEW_UNITS, height)
        draw.drawRect(Kk.Ink1, bounds.topLeft, bounds.size)
        if (grid) draw.drawKkGrid(bounds, Kk.Bone.copy(alpha = 0.04f), spacingDp = 16f * u / draw.density, origin = bounds.topLeft)
        val inset = 0.5f * u
        draw.drawRect(Kk.Line, Offset(bounds.left + inset, bounds.top + inset), Size(bounds.width - inset * 2f, bounds.height - inset * 2f), style = kkStroke(u))
        return bounds
    }

    fun panel(bounds: Rect, outline: Color? = null) {
        draw.drawRect(Kk.Ink2, bounds.topLeft, bounds.size)
        if (outline != null) {
            val inset = 0.75f * u
            draw.drawRect(outline, Offset(bounds.left + inset, bounds.top + inset), Size(bounds.width - inset * 2f, bounds.height - inset * 2f), style = kkStroke(1.5f * u))
        }
    }

    /** Game: the Core orbits the singularity at the simulation speed; the word shows the language. */
    fun game() {
        val bounds = box(240f)
        val roles = measurers.roles
        val center = at(168f, 120f)
        val period = 3f / preferences.simulationSpeed.coerceAtLeast(0.1f)
        val angle = (kkLoop(time, period) * 2f * PI).toFloat() - PI.toFloat() * 0.35f
        val core = Offset(center.x + cos(angle) * 82f * u, center.y + sin(angle) * 82f * u)
        val tailAngle = angle - 20f * PI.toFloat() / 180f
        val tailEnd = Offset(center.x + cos(angle) * 70f * u, center.y + sin(angle) * 70f * u)
        val tailStart = Offset(tailEnd.x - cos(tailAngle) * 60f * u, tailEnd.y - sin(tailAngle) * 60f * u)
        draw.drawLine(Brush.linearGradient(listOf(Color.Transparent, roles.you), tailStart, tailEnd), tailStart, tailEnd, 2f * u)
        singularity(center, 15f, 4f)
        coreDisc(core, 12f)
        // `.t-cond` 34 px, 14 px from the right and 12 px from the bottom of its line box.
        draw.drawKkText(
            text, preferences.language.nativeName, measurers.typography.condStyle(34f),
            bounds.right - 14f * u, bounds.bottom - (12f + 34f * 0.86f * 0.5f) * u, Kk.Bone,
            align = KkAlign.END, valign = KkVAlign.CENTER, uppercase = true,
        )
    }

    /** Sound: level bars per channel (master bone, music polarity, effects you), pulsing while awake. */
    fun sound() {
        box(250f, grid = false)
        val roles = measurers.roles
        val master = preferences.masterVolume
        val channels = listOf(
            Triple(Kk.Bone, master, language.text(SettingsRedesignText.MasterShort)),
            Triple(roles.pol, if (preferences.musicEnabled) master else 0f, language.text(ProfileText.Music)),
            Triple(roles.you, if (preferences.soundEnabled) master else 0f, language.text(ProfileText.Sfx)),
        )
        val innerLeft = 18f
        val innerWidth = PREVIEW_UNITS - 36f
        val baseline = 250f - 46f
        channels.forEachIndexed { group, (color, level, label) ->
            val slot = innerWidth / channels.size
            val centerX = innerLeft + slot * (group + 0.5f)
            val barWidth = 9f
            val gap = 4f
            val total = barWidth * 5f + gap * 4f
            val height = max(8f, 170f * level)
            for (bar in 0 until 5) {
                val period = 0.6f + ((bar + group) % 3) * 0.25f
                val phase = kkLoop(time, period, delay = -bar * 0.17f)
                val wave = 0.5f - 0.5f * cos(phase * 2f * PI.toFloat())
                val barHeight = height * (0.35f + 0.65f * wave)
                val barLeft = centerX - total * 0.5f + (barWidth + gap) * bar
                draw.drawKkSheared(rect(barLeft, baseline - barHeight, barLeft + barWidth, baseline), color.copy(alpha = if (level > 0f) 1f else 0.35f))
            }
            draw.drawKkText(
                text, label, measurers.typography.labelStyle(12f), x(centerX), y(250f - 22f), Kk.Bone,
                align = KkAlign.CENTER, valign = KkVAlign.CENTER, uppercase = true,
            )
        }
    }

    /** Graphics: a mini arena with the edited palette, shake, particles and damage numbers. */
    fun graphics() {
        val palette = preferences.colorVision.previewPalette()
        val bounds = box(300f)
        val shake = if (preferences.screenShake) shakeOffset() else Offset.Zero
        draw.clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
            translate(shake.x * u, shake.y * u) {
                arena(palette)
            }
        }
        // Role swatches: five sheared chips with their names.
        val names = listOf(
            SettingsRedesignText.RoleYou to palette.you,
            SettingsRedesignText.RoleThreat to palette.threat,
            SettingsRedesignText.RoleHeat to palette.heat,
            SettingsRedesignText.RoleShield to palette.shield,
            SettingsRedesignText.RolePolarity to palette.pol,
        )
        val gap = 6f
        val width = (PREVIEW_UNITS - gap * 4f) / 5f
        names.forEachIndexed { index, (name, color) ->
            val chipLeft = (width + gap) * index
            val chip = rect(chipLeft, 312f, chipLeft + width, 342f)
            draw.drawKkSheared(chip, color)
            if (index == 1) draw.drawKkThreatHatch(chip, palette, Kk.Ink)
            // `.t-mono` 9 px; a longer translation falls back to the condensed label face.
            val label = language.text(name)
            val lane = (width + 4f) * u
            val mono = measureKkText(text, label, measurers.typography.monoStyle(9f), uppercase = true)
            val layout = if (mono.size.width <= lane) mono else {
                measureKkText(text, label, measurers.typography.labelStyle(11f, trackingEm = 0.04f), uppercase = true, maxWidth = lane)
            }
            draw.drawKkText(layout, x(chipLeft), y(348f), Kk.Mute)
        }
    }

    fun shakeOffset(): Offset {
        // pv-sh2: 0.32 s loop through (-4, 2), (4, -3), (-2, -2).
        val p = kkLoop(time, 0.32f)
        return when {
            p < 0.25f -> Offset(-4f * p / 0.25f, 2f * p / 0.25f)
            p < 0.5f -> lerpOffset(Offset(-4f, 2f), Offset(4f, -3f), (p - 0.25f) / 0.25f)
            p < 0.75f -> lerpOffset(Offset(4f, -3f), Offset(-2f, -2f), (p - 0.5f) / 0.25f)
            else -> lerpOffset(Offset(-2f, -2f), Offset.Zero, (p - 0.75f) / 0.25f)
        }
    }

    fun arena(palette: KkRolePalette) {
        val enemy = Kk.Ink1
        // Enemies: ink fill, threat outline; Mono hatches every threat.
        polygon(floatArrayOf(70f, 70f, 88f, 102f, 52f, 102f), enemy, palette)
        polygon(floatArrayOf(250f, 60f, 268f, 78f, 250f, 96f, 232f, 78f), enemy, palette)
        polygon(floatArrayOf(262f, 190f, 288f, 190f, 288f, 216f, 262f, 216f), enemy, palette)
        for ((bx, by) in listOf(120f to 200f, 140f to 186f, 232f to 170f)) {
            draw.drawCircle(palette.threat, 4.5f * u, at(bx, by))
        }
        // Tether from the Core towards the singularity.
        draw.drawLine(palette.you.copy(alpha = 0.45f), at(168f, 150f), at(58f, 205f), 10f * u)
        // Core halo: integrity arc (bone), heat arc, dashed shield ring, Core disc.
        val core = at(168f, 150f)
        draw.drawCircle(Kk.Bone.copy(alpha = 0.16f), 30f * u, core, style = Stroke(4f * u))
        arc(core, 30f, 138f, 84f, Kk.Bone, 4f)
        arc(core, 30f, -42f, 84f, palette.heat, 4f)
        draw.drawCircle(palette.shield, 36f * u, core, style = Stroke(1.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * u, 5f * u))))
        draw.drawCircle(Kk.Bone, 13f * u, core)
        // Singularity with its polarity arc.
        val singularity = at(250f, 130f)
        draw.drawCircle(palette.threat, 12f * u, singularity, style = Stroke(3f * u))
        draw.drawCircle(Kk.Bone, 3f * u, singularity)
        arc(singularity, 22f, 0f, 260f, palette.pol, 2.5f)
        particles(palette.you)
        if (preferences.damageNumbers) damageNumbers(palette)
    }

    fun particles(color: Color) {
        val count = when (preferences.particleDensity) {
            ParticleDensity.LOW -> 3
            ParticleDensity.NORMAL -> 7
            ParticleDensity.HIGH -> 12
        }
        for (index in 0 until count) {
            val baseX = 20f + (index * 53) % 300
            val baseY = 200f + (index * 29) % 80
            val period = 2f + (index % 3) * 0.6f
            val p = kkLoop(time, period, delay = -index * 0.37f)
            val alpha = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
            val centerY = baseY - 120f * p + 3f
            val c = at(baseX + 3f, centerY)
            val r = 3f * u
            val path = scratch.apply {
                rewind()
                moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
            }
            draw.drawPath(path, color, alpha = alpha.coerceIn(0f, 1f))
        }
    }

    fun damageNumbers(palette: KkRolePalette) {
        val threshold = preferences.damageNumberTierThreshold.coerceIn(
            DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first(), DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
        ).toLong()
        // One sample per color tier, placed clear of the arena's shapes; each fits its lane.
        val samples = listOf(
            DamageSample(max(1L, threshold / 2L), 16f, 30f, 150f, 0.95f, 0f),
            DamageSample(threshold * 2L, 176f, 30f, 144f, 1.03f, 0.6f),
            DamageSample(threshold * DAMAGE_NUMBER_POWERFUL_MULTIPLIER * 2L, 196f, 184f, 124f, 1.12f, 1.2f),
            DamageSample(threshold * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER * 2L, 16f, 262f, 190f, 1.25f, 1.8f),
        )
        samples.forEach { sample ->
            // Pop with overshoot, then drift away while awake (kk-demo-float, softened to stay readable).
            val p = kkLoop(time, 2.4f, delay = sample.delay)
            val rise = KkEase.Out.transform(p) * 10f
            val pop = if (p < 0.12f) 1f + 0.12f * (1f - p / 0.12f) else 1f
            val size = 24f * preferences.damageNumberSize.scale * sample.tierScale
            val label = settingsDamageSample(sample.amount, preferences.damageNumberFormat, language)
            val color = settingsDamageColor(sample.amount, threshold.toInt(), palette)
            val style = measurers.typography.condStyle(size, tabular = true, lineHeightEm = 1f)
            val layout = measureKkText(text, label, style)
            val origin = at(sample.x, sample.y - rise)
            val fit = min(1f, sample.lane * u / layout.size.width.coerceAtLeast(1))
            draw.scale(pop * fit, pop * fit, origin) {
                // `.dmg` ink shadow keeps numbers legible over the arena.
                drawKkText(layout, origin.x + 2f * u, origin.y + 2f * u, Kk.Ink, valign = KkVAlign.CENTER)
                drawKkText(layout, origin.x, origin.y, color, valign = KkVAlign.CENTER)
            }
        }
    }

    /** Interface: the text size on a real sentence, and the run statistics panel on its side. */
    fun interfaceSample() {
        val roles = measurers.roles
        val sampleBox = rect(0f, 0f, PREVIEW_UNITS, 110f)
        panel(sampleBox)
        val textScale = preferences.textScale / SETTINGS_TEXT_BASELINE
        val body = measurers.at(u / draw.density * textScale)
        val layout = measureKkText(
            body, SettingsRow.TEXT_SIZE.about(language), measurers.typography.bodyStyle(18f, lineHeightEm = 1.2f),
            maxWidth = sampleBox.width - 24f * u, maxLines = 3,
        )
        draw.clipRect(sampleBox.left, sampleBox.top, sampleBox.right, sampleBox.bottom) {
            drawKkText(layout, sampleBox.left + 12f * u, sampleBox.top + 12f * u, Kk.Bone)
        }
        val statsWidth = 120f
        val rowTop = 122f
        val statsLeft = if (preferences.runStatisticsOnLeft) 0f else PREVIEW_UNITS - statsWidth
        val mainLeft = if (preferences.runStatisticsOnLeft) statsWidth + 8f else 0f
        val main = rect(mainLeft, rowTop, mainLeft + PREVIEW_UNITS - statsWidth - 8f, rowTop + 110f)
        panel(main)
        bar(mainLeft + 12f, rowTop + 12f, (main.width / u - 24f) * 0.7f, 12f, Kk.Bone)
        bar(mainLeft + 12f, rowTop + 32f, (main.width / u - 24f) * 0.55f, 10f, Kk.Ink4)
        bar(mainLeft + 12f, rowTop + 50f, (main.width / u - 24f) * 0.6f, 10f, Kk.Ink4)
        val stats = rect(statsLeft, rowTop, statsLeft + statsWidth, rowTop + 110f)
        panel(stats, roles.you)
        bar(statsLeft + 12f, rowTop + 12f, statsWidth - 24f, 8f, roles.you)
        bar(statsLeft + 12f, rowTop + 26f, (statsWidth - 24f) * 0.7f, 8f, Kk.Ink4)
        bar(statsLeft + 12f, rowTop + 40f, (statsWidth - 24f) * 0.8f, 8f, Kk.Ink4)
        bar(statsLeft + 12f, rowTop + 54f, (statsWidth - 24f) * 0.6f, 8f, Kk.Ink4)
    }

    fun bar(l: Float, t: Float, width: Float, height: Float, color: Color) {
        draw.drawRect(color, Offset(x(l), y(t)), Size(width * u, height * u))
    }

    fun singularity(center: Offset, radius: Float, dot: Float) {
        val threat = measurers.roles.threat
        draw.drawCircle(threat.copy(alpha = 0.12f), (radius + 6f) * u, center, style = Stroke(6f * u))
        draw.drawCircle(threat, radius * u, center, style = Stroke(3f * u))
        draw.drawCircle(Kk.Bone, dot * u, center)
    }

    /** `.core`: bone disc with an ink ring and a soft bone glow (stacked strokes, no blur). */
    fun coreDisc(center: Offset, radius: Float) {
        draw.drawCircle(Kk.Bone.copy(alpha = 0.08f), (radius + 10f) * u, center)
        draw.drawCircle(Kk.Bone.copy(alpha = 0.12f), (radius + 5f) * u, center)
        draw.drawCircle(Kk.Ink, (radius + 3f) * u, center)
        draw.drawCircle(Kk.Bone, radius * u, center)
    }

    fun arc(center: Offset, radius: Float, startDegrees: Float, sweepDegrees: Float, color: Color, width: Float) {
        val r = radius * u
        draw.drawArc(
            color, startDegrees, sweepDegrees, useCenter = false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2f, r * 2f),
            style = Stroke(width * u, cap = StrokeCap.Butt),
        )
    }

    fun polygon(points: FloatArray, fill: Color, palette: KkRolePalette) {
        val path = scratch.apply {
            rewind()
            moveTo(x(points[0]), y(points[1]))
            var index = 2
            while (index < points.size) {
                lineTo(x(points[index]), y(points[index + 1]))
                index += 2
            }
            close()
        }
        draw.drawPath(path, fill)
        draw.drawKkThreatHatch(path, palette)
        draw.drawPath(path, palette.threat, style = Stroke(2f * u))
    }

    private val scratch: Path get() = PreviewPaths.scratch
}

private class DamageSample(val amount: Long, val x: Float, val y: Float, val lane: Float, val tierScale: Float, val delay: Float)

private fun lerpOffset(a: Offset, b: Offset, t: Float): Offset = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** One reusable scratch path; the preview is drawn on the UI thread only. */
private object PreviewPaths {
    val scratch = Path()
}
