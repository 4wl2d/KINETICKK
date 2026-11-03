// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.*
import kotlin.math.min

/**
 * Feed of run messages (banner plates) and build notifications (toast plates), docked under the
 * chain counter on the right so it never covers the Core: on phones a plate that would reach the
 * Core narrows to the column beside it (landscape) or continues below it (portrait; on short screens
 * it may take the column beside the Core instead). Every line sits on its plate: a toast is its
 * title row plus one line per detail, wrapping (then shrinking) instead of cutting, and a line that
 * cannot show whole is not shown. Synergy changes ("+ " / "− " details) always stay on their toast;
 * stat deltas fill the remaining detail budget. A toast whose title and synergy lines do not fit its
 * room at the current text size steps its text down toward the smallest text setting until they do.
 * The newest toast's title and synergy lines outrank the banner: the banner shows while the toast
 * keeps all of them under it (at some text step, anywhere the toast may go) and yields otherwise.
 * Messages that only restate a state the HUD shows (overheat, polarity strain, overdrive, dash
 * online), trial rules (behind the trial panel's (!)) and instruction tails ("choose a course") are
 * not shown as text.
 *
 * Text follows the text-size setting around the design size: the feed's styles are the board sizes
 * over [REFERENCE_TEXT_SCALE], so the default setting renders them at the board size.
 */
internal fun DrawScope.drawHudFeed(
    engine: GameplayRenderModel,
    fx: VisualFxProjection,
    measurer: TextMeasurer,
    renderTime: Float,
    memory: HudPresentationMemory?,
) {
    FeedProbe.begin()
    val frame = HudScratch.frame.update(size.width, size.height, density)
    val area = FeedArea.update(engine, frame, measurer, density)
    val right = area.right
    val gap = frame.u(8f)
    var top = area.top
    var feedTop = Float.NaN
    var feedLeft = right
    val notices = fx.buildNotifications
    if (engine.showsMessage()) {
        FeedScratch.message.update(engine.message, measurer.language)
        if (bannerKeepsTheNewestToast(area, frame, measurer, notices, top, gap)) {
            val age = if (memory != null) memory.messageAge(renderTime) else Float.POSITIVE_INFINITY
            val alpha = (engine.messageTime / 0.45f).coerceIn(0f, 1f) * entranceAlpha(age)
            val shift = entranceShift(age) * frame.u(80f)
            val height = placeBanner(area, frame, measurer, top)
            if (height > 0f) {
                val lines = FeedLines
                translate(shift, 0f) { drawFeedPlate(measurer, frame, HudPath.MESSAGE, 0, lines, right, top, height, alpha, shift, banner = true) }
                feedLeft = min(feedLeft, right - lines.plateWidth(frame, banner = true))
                feedTop = top
                top += height + gap
            }
        }
    }
    var bandLimit = area.limit
    var shown = 0
    var index = notices.size - 1
    while (index >= 0 && shown < area.maxNotices) {
        val notice = notices[index]
        val slot = shown
        // Should even the smallest step hold no place with all its synergy lines, the newest toast's
        // title shows with the lines that fit.
        val height = placeToast(area, frame, measurer, notice, slot, top, bandLimit, relaxed = shown == 0)
        if (height <= 0f) break
        top = ToastSpot.top
        bandLimit = ToastSpot.bandLimit
        val lines = FeedLines
        // The entrance runs once from the toast's opening; a merged line does not replay it.
        val age = notice.age
        val alpha = (notice.life / 0.6f).coerceIn(0f, 1f) * entranceAlpha(age)
        val shift = entranceShift(age) * frame.u(80f)
        val style = FeedScratch.styles[slot].of(notice, engine.content)
        translate(shift, 0f) {
            drawFeedPlate(measurer, frame, NoticePaths[slot], 1 + slot, lines, right, top, height, alpha, shift, banner = false, style = style)
        }
        feedLeft = min(feedLeft, right - lines.plateWidth(frame, banner = false))
        if (feedTop.isNaN()) feedTop = top
        top += height + gap
        shown++
        index--
        // Nothing follows a toast beside the Core: the next one would start level with the Core.
        if (ToastSpot.side) break
    }
    if (!feedTop.isNaN()) HudLayoutProbe.record(HudBlock.FEED, feedLeft, feedTop, right, top - gap)
}

private const val ENTRANCE_SECONDS = 0.3f

private val NoticePaths = arrayOf(HudPath.NOTICE_0, HudPath.NOTICE_1, HudPath.NOTICE_2)

/** Banner entrance (`kk-fx-banner`): slides in from the left with the Pull overshoot. */
private fun entranceShift(age: Float): Float {
    if (age >= ENTRANCE_SECONDS) return 0f
    return -(1f - KkEase.Pull.transform((age / ENTRANCE_SECONDS).coerceIn(0f, 1f)))
}

private fun entranceAlpha(age: Float): Float = (age / (ENTRANCE_SECONDS * 0.4f)).coerceIn(0f, 1f)

/**
 * One feed plate right-aligned at [right]: banner (ink-4, message) or toast (ink-2 + gem, build).
 * A fading plate is composited through one layer at [alpha]; its text is always painted opaque, so
 * the paragraphs keep their colour and are never laid out again while the plate fades.
 */
private fun DrawScope.drawFeedPlate(
    measurer: TextMeasurer,
    frame: HudFrame,
    path: HudPath,
    plate: Int,
    lines: FeedLines,
    right: Float,
    top: Float,
    height: Float,
    alpha: Float,
    shift: Float,
    banner: Boolean,
    style: FeedNoticeStyle? = null,
) {
    if (alpha <= 0f) return
    val width = lines.plateWidth(frame, banner)
    val left = right - width
    val bottom = top + height
    val fading = alpha < 1f
    if (fading) drawContext.canvas.saveLayer(FeedLayer.bounds(plate, left, top, right, bottom), FeedLayer.paint(alpha))
    drawPath(HudDrawCache.paths.slab(path.ordinal, left, top, right, bottom, frame.u(10f)), if (banner) Kk.Ink4 else Kk.Ink2)
    FeedProbe.plate(left + shift, top, right + shift, bottom, banner)
    val textLeft = left + startPadding(frame, banner) + leadSpace(frame, banner)
    val rowTop = top + verticalPadding(frame)
    val row = lines.titleRow(frame, banner)
    val rowCenter = rowTop + row * 0.5f
    val roles = measurer.roles
    if (!banner) {
        val lead = leadSize(frame)
        val gem = Offset(left + startPadding(frame, banner) + lead * 0.5f, rowCenter)
        val first = style?.first(roles) ?: roles.you
        val second = style?.second(roles) ?: first
        drawKkGem(gem, first, lead / density)
        // A reaction's gem is split between its two elements.
        if (second != first) clipRect(gem.x, gem.y - lead, gem.x + lead, gem.y + lead) { drawKkGem(gem, second, lead / density) }
    }
    val title = lines[0]
    drawKkText(title, textLeft, rowCenter, style?.title(roles) ?: Kk.Bone, valign = KkVAlign.CENTER)
    val titleTop = rowCenter - (title.kkBoxTop + title.kkBoxBottom) * 0.5f
    FeedProbe.line(title, textLeft + shift, titleTop, textLeft + shift + title.size.width, titleTop + title.size.height)
    var y = rowTop + row
    val detailColor = if (style != null && style.bright) Kk.Bone2 else Kk.Mute
    for (index in 1 until lines.count) {
        if (!lines.isVisible(index)) continue
        val detail = lines[index]
        y += detailGap(frame)
        drawKkText(detail, textLeft, y, detailColor)
        val detailTop = y - detail.kkBoxTop
        FeedProbe.line(detail, textLeft + shift, detailTop, textLeft + shift + detail.size.width, detailTop + detail.size.height)
        val tier = lines.tier(index)
        if (tier > 0) {
            // A resonance or affinity tier reads as lit pips (tag tiers in bone, element tiers in their hue).
            val element = lines.element(index)
            val color = if (element >= 0) roles.element(element) else Kk.Bone
            val scale = frame.unit / density * lines.shrinkFactor
            val pipHeight = TIER_PIP_HEIGHT_DP * scale
            drawKkPips(Offset(textLeft + lines.pipLeft(index), y + lines.pipCenter(index) - pipHeight * density * 0.5f), TIER_PIPS,
                tier.coerceIn(0, TIER_PIPS), color, sizeDp = pipHeight, widthDp = TIER_PIP_WIDTH_DP * scale, gapDp = TIER_PIP_GAP_DP * scale,
                emptyColor = Kk.Ink4)
        }
        y += detail.kkBoxHeight
    }
    if (fading) drawContext.canvas.restore()
}

/** The layer paint and per-plate layer bounds of fading plates (rebuilt only when a plate moves). */
private object FeedLayer {
    private val layerPaint = Paint()
    private val keys = FloatArray((1 + MAX_NOTICES) * 4) { Float.NaN }
    private val rects = arrayOfNulls<Rect>(1 + MAX_NOTICES)

    fun paint(alpha: Float): Paint {
        layerPaint.alpha = alpha
        return layerPaint
    }

    fun bounds(plate: Int, left: Float, top: Float, right: Float, bottom: Float): Rect {
        val base = plate * 4
        val cached = rects[plate]
        if (cached != null && keys[base] == left && keys[base + 1] == top && keys[base + 2] == right && keys[base + 3] == bottom) return cached
        keys[base] = left
        keys[base + 1] = top
        keys[base + 2] = right
        keys[base + 3] = bottom
        return Rect(left, top, right, bottom).also { rects[plate] = it }
    }
}

internal object FeedScratch {
    val message = SplitMessage()
    val titles = Array(MAX_NOTICES) { LocalizedText() }
    val details = Array(MAX_NOTICES * DETAILS_PER_NOTICE) { LocalizedText() }
    val styles = Array(MAX_NOTICES) { FeedNoticeStyle() }
    val fallback = HudKeyedText()
    val legendary = HudKeyedText()
}
