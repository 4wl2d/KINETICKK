// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
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
 * Feed of run messages (banner rows) and build notifications (toast rows), docked under the chain
 * counter on the right so it never covers the Core. Messages that only restate a state the HUD
 * already shows (overheat, polarity strain, overdrive, dash online) and trial rules (behind the
 * trial panel's (!)) are not repeated as text.
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
    val limit: Float
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            right = frame.width - frame.margin
            top = frame.chainTop + frame.u(90f)
            maxWidth = min(frame.u(380f), frame.width * 0.4f)
            maxNotices = 3
            // Stop above the Dash/Brake row that sits over the loadout.
            limit = frame.height - frame.u(REGULAR_HUD_BOTTOM_DP + REGULAR_LOADOUT_HEIGHT_DP + 18f + 52f + 12f)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            right = frame.width - frame.margin - compactLoadoutWidth(engine.content.relicPolicy.maxSlots.coerceIn(0, 8), frame.unit) - frame.u(12f)
            top = frame.u(62f)
            maxWidth = min(frame.u(260f) * frame.factor, right - frame.width * 0.5f + frame.u(60f)).coerceAtLeast(frame.u(140f))
            maxNotices = 1
            limit = frame.height * 0.5f
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            right = frame.width - frame.margin
            val trial = engine.activeTrial()
            top = if (trial != null) {
                TrialFeedLayout.update(frame.width, frame.height, density, measurer.scale).bottom + frame.u(8f)
            } else {
                frame.top + frame.u(61f + 26f + 12f)
            }
            maxWidth = frame.width - frame.margin * 2f
            maxNotices = 2
            limit = frame.height - frame.u(238f + 12f)
        }
    }
    val rowHeight = if (frame.regular) frame.u(44f) else frame.u(34f)
    val gap = frame.u(8f)
    if (engine.showsMessage() && top + rowHeight <= limit) {
        FeedScratch.message.update(engine.message, language)
        val age = if (memory != null) memory.messageAge(renderTime) else Float.POSITIVE_INFINITY
        val alpha = (engine.messageTime / 0.45f).coerceIn(0f, 1f) * entranceAlpha(age)
        val shift = entranceShift(age) * frame.u(80f)
        translate(shift, 0f) {
            drawFeedRow(measurer, frame, HudPath.MESSAGE, HudText.MESSAGE_TITLE, HudText.MESSAGE_DETAIL,
                FeedScratch.message.title, FeedScratch.message.detail, right, top, rowHeight, maxWidth, alpha, banner = true)
        }
        top += rowHeight + gap
    }
    val notices = fx.buildNotifications
    var shown = 0
    var index = notices.size - 1
    while (index >= 0 && shown < maxNotices && top + rowHeight <= limit) {
        val notice = notices[index]
        val slot = shown
        val title = FeedScratch.titles[slot].of(notice.title, language)
        val detailCount = notice.details.size
        val firstDetail = if (detailCount > 0) FeedScratch.details[slot * DETAILS_PER_NOTICE].of(notice.details[0], language)
        else FeedScratch.fallback.of(language, 0L) { language.text(GameplayText.BuildUpdated) }
        val age = NOTICE_LIFE_SECONDS - notice.life
        val alpha = (notice.life / 0.6f).coerceIn(0f, 1f) * entranceAlpha(age)
        val shift = entranceShift(age) * frame.u(80f)
        val path = when (slot) {
            0 -> HudPath.NOTICE_0
            1 -> HudPath.NOTICE_1
            else -> HudPath.NOTICE_2
        }
        val titleSlot = when (slot) {
            0 -> HudText.NOTICE_TITLE_0
            1 -> HudText.NOTICE_TITLE_1
            else -> HudText.NOTICE_TITLE_2
        }
        translate(shift, 0f) {
            drawFeedRow(measurer, frame, path, titleSlot, DetailSlots[slot * DETAILS_PER_NOTICE], title, firstDetail, right, top,
                rowHeight, maxWidth, alpha, banner = false)
        }
        top += rowHeight
        if (frame.regular) {
            // Further details stack under the toast, one per line.
            val lines = min(detailCount, DETAILS_PER_NOTICE)
            for (line in 1 until lines) {
                if (top + frame.u(4f + 18f) > limit) break
                val text = FeedScratch.details[slot * DETAILS_PER_NOTICE + line].of(notice.details[line], language)
                val layout = HudDrawCache.layout(DetailSlots[slot * DETAILS_PER_NOTICE + line], measurer, text,
                    measurer.typography.monoStyle(frame.t(11f)), uppercase = true, maxWidth = maxWidth - frame.u(18f))
                top += frame.u(4f)
                drawKkText(layout, right - frame.u(18f) + shift, top, Kk.Mute, KkAlign.END, alpha = alpha)
                top += layout.kkBoxHeight
            }
        }
        top += gap
        shown++
        index--
    }
}

private const val NOTICE_LIFE_SECONDS = 6f
private const val DETAILS_PER_NOTICE = 4
private const val ENTRANCE_SECONDS = 0.3f

private val DetailSlots = arrayOf(
    HudText.NOTICE_DETAIL_0, HudText.NOTICE_DETAIL_1, HudText.NOTICE_DETAIL_2, HudText.NOTICE_DETAIL_3,
    HudText.NOTICE_DETAIL_4, HudText.NOTICE_DETAIL_5, HudText.NOTICE_DETAIL_6, HudText.NOTICE_DETAIL_7,
    HudText.NOTICE_DETAIL_8, HudText.NOTICE_DETAIL_9, HudText.NOTICE_DETAIL_10, HudText.NOTICE_DETAIL_11,
)

/** Banner entrance (`kk-fx-banner`): slides in from the left with the Pull overshoot. */
private fun entranceShift(age: Float): Float {
    if (age >= ENTRANCE_SECONDS) return 0f
    return -(1f - KkEase.Pull.transform((age / ENTRANCE_SECONDS).coerceIn(0f, 1f)))
}

private fun entranceAlpha(age: Float): Float = (age / (ENTRANCE_SECONDS * 0.4f)).coerceIn(0f, 1f)

/** One feed row right-aligned at [right]: banner (ink-4, message) or toast (ink-2 + gem, build). */
private fun DrawScope.drawFeedRow(
    measurer: TextMeasurer,
    frame: HudFrame,
    path: HudPath,
    titleSlot: HudText,
    detailSlot: HudText,
    title: String,
    detail: String?,
    right: Float,
    top: Float,
    height: Float,
    maxWidth: Float,
    alpha: Float,
    banner: Boolean,
) {
    if (alpha <= 0f) return
    val roles = measurer.roles
    val startPadding = frame.u(if (banner) 18f else 12f)
    val endPadding = frame.u(18f)
    val gap = frame.u(12f)
    val lead = if (banner) 0f else frame.u(if (frame.regular) 14f else 11f)
    val leadSpace = if (lead > 0f) lead + gap else 0f
    val titleSize = if (frame.regular) frame.t(if (banner) 24f else 20f) else if (banner) 18f else 16f
    val available = maxWidth - startPadding - endPadding - leadSpace
    val titleLayout = HudDrawCache.layout(titleSlot, measurer, title, measurer.typography.condStyle(titleSize, lineHeightEm = 1f),
        uppercase = true, maxWidth = available.coerceAtLeast(1f))
    val detailAvailable = available - titleLayout.size.width - gap
    val detailLayout = if (detail != null && detailAvailable > frame.u(40f)) {
        HudDrawCache.layout(detailSlot, measurer, detail, measurer.typography.monoStyle(if (frame.regular) frame.t(11f) else 10f),
            uppercase = true, maxWidth = detailAvailable)
    } else {
        null
    }
    val width = startPadding + leadSpace + titleLayout.size.width + (if (detailLayout != null) gap + detailLayout.size.width else 0f) + endPadding
    val left = right - width
    drawPath(HudDrawCache.paths.slab(path.ordinal, left, top, right, top + height, frame.u(10f)),
        if (banner) Kk.Ink4 else Kk.Ink2, alpha)
    val cy = top + height * 0.5f
    var x = left + startPadding
    if (lead > 0f) {
        drawKkGem(Offset(x + lead * 0.5f, cy), roles.you.copy(alpha = alpha), lead / density)
        x += leadSpace
    }
    drawKkText(titleLayout, x, cy, Kk.Bone, valign = KkVAlign.CENTER, alpha = alpha)
    if (detailLayout != null) {
        drawKkText(detailLayout, x + titleLayout.size.width + gap, cy, Kk.Mute, valign = KkVAlign.CENTER, alpha = alpha)
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

/** A localized message split at its first " // " into a title and a detail element. */
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
        val localized = message.localizedContent(language)
        val split = localized.indexOf(" // ")
        if (split < 0) {
            title = localized
            detail = null
        } else {
            title = localized.substring(0, split)
            detail = localized.substring(split + 4).replace(" // ", "  ")
        }
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
