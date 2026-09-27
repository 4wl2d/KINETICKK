// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_BOSS_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_PANEL_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.REGULAR_HUD_BOTTOM_DP
import kinetickk.ball.gameplay.interaction.layout.REGULAR_LOADOUT_HEIGHT_DP
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min

/**
 * Feed of run messages (banner plates) and build notifications (toast plates), docked under the
 * chain counter on the right so it never covers the Core. Every line sits on its plate: a toast is
 * its title row plus one line per detail, wrapping instead of cutting. Messages that only restate a
 * state the HUD shows (overheat, polarity strain, overdrive, dash online), trial rules (behind the
 * trial panel's (!)) and instruction tails ("choose a course") are not shown as text.
 */
internal fun DrawScope.drawHudFeed(
    engine: GameplayRenderModel,
    fx: VisualFxProjection,
    measurer: TextMeasurer,
    renderTime: Float,
    memory: HudPresentationMemory?,
) {
    val frame = HudScratch.frame.update(size.width, size.height, density)
    val language = measurer.language
    val right: Float
    var top: Float
    val maxWidth: Float
    val maxNotices: Int
    val maxDetails: Int
    val limit: Float
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            right = frame.width - frame.margin
            top = frame.chainTop + frame.u(90f)
            maxWidth = min(frame.u(380f), frame.width * 0.4f)
            maxNotices = 3
            maxDetails = DETAILS_PER_NOTICE
            // Stop above the Dash/Brake row that sits over the loadout.
            limit = frame.height - frame.u(REGULAR_HUD_BOTTOM_DP + REGULAR_LOADOUT_HEIGHT_DP + 18f + 52f + 12f)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            right = frame.width - frame.margin - compactLoadoutWidth(engine.content.relicPolicy.maxSlots.coerceIn(0, 8), frame.unit) - frame.u(12f)
            top = frame.u(62f)
            maxWidth = min(frame.u(260f) * frame.factor, right - frame.width * 0.5f + frame.u(60f)).coerceAtLeast(frame.u(140f))
            maxNotices = 1
            maxDetails = 2
            limit = frame.height * 0.55f
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            right = frame.width - frame.margin
            top = if (engine.activeTrial() != null) {
                TrialFeedLayout.update(frame.width, frame.height, density, measurer.scale).bottom + frame.u(8f)
            } else if (FeedBoss.select(engine).id >= 0) {
                frame.top + frame.u(PORTRAIT_PANEL_ROW_DP)
            } else {
                frame.top + frame.u(PORTRAIT_BOSS_ROW_DP)
            }
            maxWidth = frame.width - frame.margin * 2f
            maxNotices = 2
            maxDetails = 2
            limit = frame.height - frame.u(238f + 12f)
        }
    }
    val gap = frame.u(8f)
    val feedTop = top
    var feedLeft = right
    if (engine.showsMessage()) {
        FeedScratch.message.update(engine.message, language)
        val age = if (memory != null) memory.messageAge(renderTime) else Float.POSITIVE_INFINITY
        val alpha = (engine.messageTime / 0.45f).coerceIn(0f, 1f) * entranceAlpha(age)
        val shift = entranceShift(age) * frame.u(80f)
        val lines = FeedLines.clear()
        lines.title(measurer, frame, HudText.MESSAGE_TITLE, FeedScratch.message.title, maxWidth, banner = true)
        val detail = FeedScratch.message.detail
        if (detail != null) lines.detail(measurer, frame, HudText.MESSAGE_DETAIL, detail, maxWidth, banner = true)
        val height = lines.fit(frame, limit - top, banner = true)
        if (height > 0f) {
            translate(shift, 0f) { drawFeedPlate(measurer, frame, HudPath.MESSAGE, lines, right, top, height, alpha, banner = true) }
            feedLeft = min(feedLeft, right - lines.plateWidth(frame, banner = true))
            top += height + gap
        }
    }
    val notices = fx.buildNotifications
    var shown = 0
    var index = notices.size - 1
    while (index >= 0 && shown < maxNotices) {
        val notice = notices[index]
        val slot = shown
        val lines = FeedLines.clear()
        lines.title(measurer, frame, NoticeTitleSlots[slot], FeedScratch.titles[slot].of(notice.title, language), maxWidth, banner = false)
        val detailCount = min(notice.details.size, maxDetails)
        if (detailCount == 0) {
            lines.detail(measurer, frame, DetailSlots[slot * DETAILS_PER_NOTICE],
                FeedScratch.fallback.of(language, 0L) { language.text(GameplayText.BuildUpdated) }, maxWidth, banner = false)
        }
        for (line in 0 until detailCount) {
            val text = FeedScratch.details[slot * DETAILS_PER_NOTICE + line].of(notice.details[line], language)
            lines.detail(measurer, frame, DetailSlots[slot * DETAILS_PER_NOTICE + line], text, maxWidth, banner = false)
        }
        val height = lines.fit(frame, limit - top, banner = false)
        if (height <= 0f) break
        val age = NOTICE_LIFE_SECONDS - notice.life
        val alpha = (notice.life / 0.6f).coerceIn(0f, 1f) * entranceAlpha(age)
        val shift = entranceShift(age) * frame.u(80f)
        translate(shift, 0f) { drawFeedPlate(measurer, frame, NoticePaths[slot], lines, right, top, height, alpha, banner = false) }
        feedLeft = min(feedLeft, right - lines.plateWidth(frame, banner = false))
        top += height + gap
        shown++
        index--
    }
    if (top > feedTop) HudLayoutProbe.record(HudBlock.FEED, feedLeft, feedTop, right, top - gap)
}

private const val NOTICE_LIFE_SECONDS = 6f
private const val DETAILS_PER_NOTICE = 4
private const val ENTRANCE_SECONDS = 0.3f

private val DetailSlots = arrayOf(
    HudText.NOTICE_DETAIL_0, HudText.NOTICE_DETAIL_1, HudText.NOTICE_DETAIL_2, HudText.NOTICE_DETAIL_3,
    HudText.NOTICE_DETAIL_4, HudText.NOTICE_DETAIL_5, HudText.NOTICE_DETAIL_6, HudText.NOTICE_DETAIL_7,
    HudText.NOTICE_DETAIL_8, HudText.NOTICE_DETAIL_9, HudText.NOTICE_DETAIL_10, HudText.NOTICE_DETAIL_11,
)
private val NoticeTitleSlots = arrayOf(HudText.NOTICE_TITLE_0, HudText.NOTICE_TITLE_1, HudText.NOTICE_TITLE_2)
private val NoticePaths = arrayOf(HudPath.NOTICE_0, HudPath.NOTICE_1, HudPath.NOTICE_2)

/** Banner entrance (`kk-fx-banner`): slides in from the left with the Pull overshoot. */
private fun entranceShift(age: Float): Float {
    if (age >= ENTRANCE_SECONDS) return 0f
    return -(1f - KkEase.Pull.transform((age / ENTRANCE_SECONDS).coerceIn(0f, 1f)))
}

private fun entranceAlpha(age: Float): Float = (age / (ENTRANCE_SECONDS * 0.4f)).coerceIn(0f, 1f)

/**
 * The lines of one feed plate (title first, then details), measured once per change through the
 * HUD layout cache. Draw-thread scratch: reused for every plate, no allocation per frame.
 */
private object FeedLines {
    private val layouts = arrayOfNulls<TextLayoutResult>(1 + DETAILS_PER_NOTICE)
    var count = 0
        private set
    var shown = 0
        private set

    fun clear(): FeedLines {
        count = 0
        shown = 0
        return this
    }

    operator fun get(index: Int): TextLayoutResult = layouts[index]!!

    fun title(measurer: TextMeasurer, frame: HudFrame, slot: HudText, text: String, maxWidth: Float, banner: Boolean) {
        val size = if (frame.regular) frame.t(if (banner) 24f else 20f) else if (banner) 18f else 16f
        val available = maxWidth - startPadding(frame, banner) - endPadding(frame) - leadSpace(frame, banner)
        layouts[count++] = HudDrawCache.layout(slot, measurer, text, measurer.typography.condStyle(size, lineHeightEm = 1f),
            uppercase = true, maxWidth = available.coerceAtLeast(1f))
    }

    fun detail(measurer: TextMeasurer, frame: HudFrame, slot: HudText, text: String, maxWidth: Float, banner: Boolean) {
        if (count >= layouts.size) return
        val available = maxWidth - startPadding(frame, banner) - endPadding(frame) - leadSpace(frame, banner)
        // Details wrap to a second line on their plate rather than being cut.
        layouts[count++] = HudDrawCache.layout(slot, measurer, text, measurer.typography.monoStyle(if (frame.regular) frame.t(11f) else 10f),
            uppercase = true, maxWidth = available.coerceAtLeast(1f), maxLines = 2)
    }

    /** Plate height showing as many detail lines as fit in [room]; 0 when not even the title fits. */
    fun fit(frame: HudFrame, room: Float, banner: Boolean): Float {
        var height = verticalPadding(frame) * 2f + titleRowHeight(frame, banner)
        if (count == 0 || height > room) return 0f
        shown = 1
        for (index in 1 until count) {
            val next = height + detailGap(frame) + layouts[index]!!.kkBoxHeight
            if (next > room) break
            height = next
            shown = index + 1
        }
        return height
    }

    fun plateWidth(frame: HudFrame, banner: Boolean): Float {
        var content = leadSpace(frame, banner) + layouts[0]!!.size.width
        for (index in 1 until shown) content = max(content, leadSpace(frame, banner) + layouts[index]!!.size.width)
        return startPadding(frame, banner) + content + endPadding(frame)
    }
}

private fun startPadding(frame: HudFrame, banner: Boolean) = frame.u(if (banner) 18f else 12f)
private fun endPadding(frame: HudFrame) = frame.u(18f)
private fun leadSize(frame: HudFrame) = frame.u(if (frame.regular) 14f else 11f)
private fun leadSpace(frame: HudFrame, banner: Boolean) = if (banner) 0f else leadSize(frame) + frame.u(12f)
private fun verticalPadding(frame: HudFrame) = frame.u(if (frame.regular) 9f else 6f)
private fun detailGap(frame: HudFrame) = frame.u(3f)
private fun titleRowHeight(frame: HudFrame, banner: Boolean) = frame.u(if (frame.regular) 26f else 22f) + if (banner) frame.u(2f) else 0f

/** One feed plate right-aligned at [right]: banner (ink-4, message) or toast (ink-2 + gem, build). */
private fun DrawScope.drawFeedPlate(
    measurer: TextMeasurer,
    frame: HudFrame,
    path: HudPath,
    lines: FeedLines,
    right: Float,
    top: Float,
    height: Float,
    alpha: Float,
    banner: Boolean,
) {
    if (alpha <= 0f || lines.shown == 0) return
    val width = lines.plateWidth(frame, banner)
    val left = right - width
    drawPath(HudDrawCache.paths.slab(path.ordinal, left, top, right, top + height, frame.u(10f)),
        if (banner) Kk.Ink4 else Kk.Ink2, alpha)
    val textLeft = left + startPadding(frame, banner) + leadSpace(frame, banner)
    val rowTop = top + verticalPadding(frame)
    val rowCenter = rowTop + titleRowHeight(frame, banner) * 0.5f
    if (!banner) {
        val lead = leadSize(frame)
        drawKkGem(Offset(left + startPadding(frame, banner) + lead * 0.5f, rowCenter), measurer.roles.you.copy(alpha = alpha), lead / density)
    }
    drawKkText(lines[0], textLeft, rowCenter, Kk.Bone, valign = KkVAlign.CENTER, alpha = alpha)
    var y = rowTop + titleRowHeight(frame, banner)
    for (index in 1 until lines.shown) {
        y += detailGap(frame)
        drawKkText(lines[index], textLeft, y, Kk.Mute, alpha = alpha)
        y += lines[index].kkBoxHeight
    }
}

/** Game messages the HUD shows as state (stamps, stripes, glows, pips) rather than as text. */
private val StateMessages = hashSetOf("OVERHEAT", "POLARITY FIELD STRAIN", "DASH ONLINE", "KINETIC OVERDRIVE")

/** Whether the current run message appears in the feed (see [drawHudFeed]). */
internal fun GameplayRenderModel.showsMessage(): Boolean {
    if (messageTime <= 0f || message.isBlank() || message in StateMessages) return false
    val definitions = content.pointsOfInterest.definitions
    for (index in definitions.indices) if (definitions[index].instruction == message) return false
    return true
}

/**
 * Message tails that only tell the player what to do; the feed shows the event, not the
 * instruction (explanations live behind (!) buttons).
 */
private val InstructionTails = hashSetOf("CHOOSE A COURSE")

/**
 * A run message as feed text: the localized title (before the first " // ") and its detail, or
 * no detail when the tail is an instruction. Allocates; called only when the message changes.
 */
internal fun feedMessageParts(message: String, language: AppLanguage): Pair<String, String?> {
    val localized = message.localizedContent(language)
    val split = localized.indexOf(" // ")
    if (split < 0) return localized to null
    val sourceSplit = message.indexOf(" // ")
    val instruction = sourceSplit >= 0 && message.substring(sourceSplit + 4).trim() in InstructionTails
    val title = localized.substring(0, split)
    return title to if (instruction) null else localized.substring(split + 4).replace(" // ", "  ")
}

/** The current message's feed text, rebuilt only when the message or language changes. */
private class SplitMessage {
    private var source: String? = null
    private var language: AppLanguage? = null
    var title = ""
        private set
    var detail: String? = null
        private set

    fun update(message: String, language: AppLanguage) {
        if (message == source && language === this.language) return
        source = message
        this.language = language
        val parts = feedMessageParts(message, language)
        title = parts.first
        detail = parts.second
    }
}

/** A string localized once per (source, language). */
private class LocalizedText {
    private var source: String? = null
    private var language: AppLanguage? = null
    private var text = ""

    fun of(value: String, language: AppLanguage): String {
        if (value != source || language !== this.language) {
            source = value
            this.language = language
            text = value.localizedContent(language)
        }
        return text
    }
}

private object FeedScratch {
    val message = SplitMessage()
    val titles = Array(3) { LocalizedText() }
    val details = Array(3 * DETAILS_PER_NOTICE) { LocalizedText() }
    val fallback = HudKeyedText()
}

private val TrialFeedLayout = HudTrialPanelLayout()
private val FeedBoss = BossTarget()
