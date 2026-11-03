// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.damageNumberTier
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*

/** How far a damage number drifts away from the Core over its life (world units). */
private const val DAMAGE_NUMBER_DRIFT = 52f

/** A layout-affecting difference that gives the ink shadow its own paragraph (see drawDamageNumbers). */
private const val SHADOW_LINE_HEIGHT = 1.001f

/**
 * Standard damage number size before the tier scale (×1, ×1.2, ×1.45, ×1.8), the size setting and
 * the text size: at the default text size the four tiers render at about 25/30/36/45 px.
 */
private const val DAMAGE_NUMBER_BASE_SP = 20f

/** The crit stamp next to a damage number: 12 px on the Feedback board at the default text size. */
private val CRIT_STAMP_SP = worldLabelSp(12f)

/**
 * Damage numbers (cond 900 italic, ink offset shadow): the hue of their element, size by tier, pop to 1.25× in 90 ms,
 * settle in 180 ms and drift away from the Core over 600 ms (Out), then shrink out (In).
 * Critical hits get a stamp. Text layouts that differ only in color share one paragraph, and
 * repainting it in another color or alpha rebuilds it, so the shadow gets its own layout (a
 * hair-different line height) and the exit is a scale, not a fade.
 */
internal fun DrawScope.drawDamageNumbers(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
) {
    val settings = engine.settings
    if (!settings.damageNumbers) return
    val roles = textMeasurer.roles
    val typography = textMeasurer.typography
    val numbers = visualFx.damageNumbers
    DamageNumberImages.beginFrame()
    for (index in numbers.indices) {
        val number = numbers[index]
        val elapsedMs = (InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS - number.life) * 1_000f
        val progress = (elapsedMs / KkTime.Drift).coerceIn(0f, 1f)
        val pop = damageNumberPop(elapsedMs, progress)
        if (pop <= 0.02f) continue
        val drift = KkEase.Out.transform(progress) * DAMAGE_NUMBER_DRIFT
        val location = worldOnScreen(engine, number.x + number.driftX * drift, number.y + number.driftY * drift, shakeX, shakeY)
        if (!isOnScreen(location, 80f)) continue
        val tier = damageNumberTier(number.amount, settings.damageNumberTierThreshold, number.critical)
        val fontSize = DAMAGE_NUMBER_BASE_SP * settings.damageNumberSize.scale * damageNumberScale(tier)
        val color = damageNumberColor(number.tone, roles)
        val text = number.formattedAmount(settings.damageNumberFormat, textMeasurer.language)
        val face = measureKkText(textMeasurer, text, typography.condStyle(fontSize, tabular = true, lineHeightEm = 1f, color = color))
        val shadow = measureKkText(textMeasurer, text, typography.condStyle(fontSize, tabular = true, lineHeightEm = SHADOW_LINE_HEIGHT, color = Kk.Ink))
        val image = DamageNumberImages.image(face, shadow, color)
        val tilt = ((number.amount % 9L).toInt() - 4) * 1.1f
        // Keep the whole number (and its crit stamp) inside the screen, e.g. long Russian amounts
        // at phone edges.
        val half = face.size.width * 0.5f * pop + 2f
        val halfHeight = face.kkBoxHeight * 0.5f * pop + 2f
        var right = half
        var up = halfHeight
        if (number.critical) {
            val stamp = kkStampSize(textMeasurer, WorldStrings.crit(textMeasurer.language), density, CRIT_STAMP_SP)
            right = half + (stamp.width + 3f) * pop
            up = max(halfHeight, (face.kkBoxHeight * 0.5f + 6f + stamp.height) * pop)
        }
        val edge = d(6f)
        val x = keepInside(location.x, half, right, size.width, edge)
        val y = keepInside(location.y, up, halfHeight, size.height, edge)
        withTransform({
            translate(x, y)
            rotate(tilt, Offset.Zero)
            scale(pop, pop, Offset.Zero)
        }) {
            if (image != null) image.draw(this, pop) else drawDamageNumberInk(face, shadow, color)
            if (number.critical) {
                drawKkStamp(
                    textMeasurer,
                    WorldStrings.crit(textMeasurer.language),
                    Offset(face.size.width * 0.5f + 3f, -face.kkBoxHeight * 0.5f - 6f),
                    KkStampVariant.THREAT,
                    fontSize = CRIT_STAMP_SP,
                )
            }
        }
    }
}

/**
 * Moves a label centered at [center] with extents [before]/[after] along one axis so it lies
 * within [edge]..[length] − [edge]; centered when it cannot fit.
 */
internal fun keepInside(center: Float, before: Float, after: Float, length: Float, edge: Float): Float {
    val low = edge + before
    val high = length - edge - after
    return if (low > high) (low + high) * 0.5f else center.coerceIn(low, high)
}

/**
 * Pop 0.4 to 1.25 in 90 ms and settle to 1 by 270 ms (`kk-demo-float`), hold, then shrink out
 * over the last quarter of the drift (In).
 */
internal fun damageNumberPop(elapsedMs: Float, progress: Float): Float = when {
    elapsedMs < 90f -> kkLerp(0.4f, 1.25f, KkEase.Out.transform((elapsedMs / 90f).coerceIn(0f, 1f)))
    elapsedMs < 270f -> kkLerp(1.25f, 1f, KkEase.Out.transform((elapsedMs - 90f) / 180f))
    progress > 0.75f -> 1f - KkEase.In.transform(((progress - 0.75f) / 0.25f).coerceIn(0f, 1f))
    else -> 1f
}
