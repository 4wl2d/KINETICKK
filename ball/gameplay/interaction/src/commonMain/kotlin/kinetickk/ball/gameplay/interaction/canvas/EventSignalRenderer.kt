// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.sqrt

/** Half extent of a signal's mark: its kind's icon inside the remaining-life arc. */
internal const val SIGNAL_MARK_HALF = 58f

/** Size of the kind's icon at the signal's centre and the radius of its remaining-life arc. */
private const val SIGNAL_ICON_SIZE = 40f
private const val SIGNAL_LIFE_RADIUS = 52f

/** The touch ring turns once in 24 s (15 degrees per second), like an offer's entry ring. */
private const val SIGNAL_RING_DEGREES_PER_SECOND = 15f

/** Signal names: `.t-label` 15 px on the boards, like an offer's name. */
private val SIGNAL_NAME_SP = worldLabelSp(15f)

/**
 * Live event signals after the world event's ground look: a dashed bone ring at the touch radius turning once in 24 s,
 * the kind's icon at the centre inside a bone arc of the signal's remaining life, and the kind's name. Like an offer, the
 * mark and name yield to the signal's edge marker ([collectSignalEdgeMarker]) when the mark would be cut by the screen
 * edge or sit under the HUD, decided in the unshaken view so exactly one of them draws. Five draws per live signal.
 */
internal fun DrawScope.drawEventSignals(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    val signals = engine.eventSignals
    if (signals.isEmpty()) return
    val policy = engine.content.variety.signals
    val keepOut = WorldOverlayScratch.keepOut
    val scale = engine.worldScale
    val now = engine.renderElapsed
    for (index in signals.indices) {
        val signal = signals[index]
        val center = world(engine, signal.x, signal.y, shakeX, shakeY)
        if (!isOnScreen(center, policy.touchRadius + 20f)) continue
        rotate(now * SIGNAL_RING_DEGREES_PER_SECOND, center) {
            drawCircle(Kk.Bone.copy(alpha = 0.5f), policy.touchRadius, center, style = WorldStrokes.dashedThin)
        }
        if (!markShown(steady(center, shakeX, shakeY), SIGNAL_MARK_HALF, keepOut, scale)) continue
        recordMark(center, SIGNAL_MARK_HALF)
        val life = signalLifeShare(signal, policy.lifetimeSeconds, now)
        val arcTopLeft = Offset(center.x - SIGNAL_LIFE_RADIUS, center.y - SIGNAL_LIFE_RADIUS)
        val arcSize = Size(SIGNAL_LIFE_RADIUS * 2f, SIGNAL_LIFE_RADIUS * 2f)
        drawCircle(Kk.Bone.copy(alpha = 0.12f), SIGNAL_LIFE_RADIUS, center, style = kkStroke(2f))
        if (life > 0f) drawArc(Kk.Bone, -90f, 360f * life, false, arcTopLeft, arcSize, style = kkStroke(2f))
        drawKkIcon(signal.kind.worldIcon(), center, SIGNAL_ICON_SIZE, Kk.Bone, strokeWidth = 1.8f)
        inScreenUnits(scale) {
            drawSignalName(signal.kind, Offset(center.x * scale, center.y * scale), (policy.touchRadius + 8f) * scale, textMeasurer, keepOut)
        }
    }
}

/**
 * Adds the live signal whose mark is not shown to [batch] as a [EdgeMarkerIcon.SIGNAL] marker, after the Totem's (the
 * collection order is the priority order). Decided in the unshaken view in world units, like [drawEventSignals]; [batch]
 * receives device pixels.
 */
internal fun DrawScope.collectSignalEdgeMarker(engine: GameplayRenderModel, batch: EdgeMarkerBatch) {
    val signals = engine.eventSignals
    val scale = engine.worldScale
    for (index in signals.indices) {
        val signal = signals[index]
        val target = world(engine, signal.x, signal.y, 0f, 0f)
        if (markShown(target, SIGNAL_MARK_HALF, WorldOverlayScratch.keepOut, scale)) continue
        val dx = signal.x - engine.coreX
        val dy = signal.y - engine.coreY
        batch.add(target.x * scale, target.y * scale, sqrt(dx * dx + dy * dy), EdgeMarkerIcon.SIGNAL)
    }
}

/** Live signals on the minimap: a hollow bone square, pinned to the rim when beyond its range. */
internal fun DrawScope.drawMinimapSignals(
    engine: GameplayRenderModel,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
) {
    val signals = engine.eventSignals
    val side = dot * 2.2f
    for (index in signals.indices) {
        val signal = signals[index]
        val at = pinned(centerX, centerY, half, (signal.x - engine.renderCoreX) * scale, (signal.y - engine.renderCoreY) * scale, side)
        drawRect(Kk.Bone, Offset(at.x - side * 0.5f, at.y - side * 0.5f), Size(side, side), style = kkStroke(1f))
    }
}

/** Share (0..1) of [signal]'s life left at [now] over its [lifetime]. */
internal fun signalLifeShare(signal: EventSignalProjection, lifetime: Float, now: Float): Float =
    ((signal.expiresAt - now) / lifetime.coerceAtLeast(0.01f)).coerceIn(0f, 1f)

/** The world mark of a signal's kind: what it trades (Matter, integrity, rerolls), a lock for the cache, a wreck. */
internal fun SignalKind.worldIcon(): KkIcon = when (this) {
    SignalKind.MERCHANT -> KkIcon.SYSTEM_MATTER
    SignalKind.SHRINE -> KkIcon.SYSTEM_INTEGRITY
    SignalKind.GAMBLE -> KkIcon.SYSTEM_REROLL
    SignalKind.CURSED_CACHE -> KkIcon.SYSTEM_LOCKED
    SignalKind.DERELICT -> KkIcon.SYSTEM_DISMANTLED
}

/**
 * The signal's name (`.t-label`, bone) [gap] below the mark at [center], or above it when the screen edge or the HUD is
 * in the way, else beside it ([pointLabelSide], left first) at the same gap; skipped only when no slot is clear. It moves
 * inward to stay on screen and shrinks when wider than it.
 */
private fun DrawScope.drawSignalName(kind: SignalKind, center: Offset, gap: Float, textMeasurer: TextMeasurer, keepOut: WorldHudKeepOut) {
    val color = edgeMarkerColor(EdgeMarkerIcon.SIGNAL, textMeasurer.roles)
    val text = SignalNames.name(kind, textMeasurer.language)
    val typography = textMeasurer.typography
    val edge = d(8f)
    val room = size.width - 2f * edge
    var name = measureKkText(textMeasurer, text, typography.labelStyle(SIGNAL_NAME_SP, color = color), uppercase = true)
    if (name.size.width > room) {
        name = measureKkText(textMeasurer, text, typography.labelStyle(SIGNAL_NAME_SP * room / name.size.width * 0.98f, color = color),
            uppercase = true)
    }
    val width = name.size.width.toFloat()
    val height = name.kkBoxHeight
    val half = width * 0.5f
    val x = if (room > width) center.x.coerceIn(edge + half, size.width - edge - half) else size.width * 0.5f
    var top = pointTimerTop(x, center.y, gap, width, height, size.width, size.height, edge, keepOut)
    var nameX = x
    if (top.isNaN()) {
        // Both slots under and over the mark touch the HUD (a landscape phone's buttons and relic row): beside it instead.
        nameX = pointLabelSide(center.x, center.y, gap, width, height, size.width, size.height, edge, keepOut)
        if (nameX.isNaN()) return
        top = center.y - height * 0.5f
    }
    drawKkText(name, nameX, top, color, KkAlign.CENTER, KkVAlign.TOP)
    WorldDrawProbe.record(WorldDrawn.POINT_NAME, nameX - half, top, nameX + half, top + max(height, 1f))
}

/** Signal kind names per language, resolved once (draw-thread confined). */
private object SignalNames {
    private val names = arrayOfNulls<String>(SignalKind.entries.size * AppLanguage.entries.size)

    fun name(kind: SignalKind, language: AppLanguage): String {
        val slot = kind.ordinal * AppLanguage.entries.size + language.ordinal
        return names[slot] ?: kind.displayName.localizedContent(language).also { names[slot] = it }
    }
}
