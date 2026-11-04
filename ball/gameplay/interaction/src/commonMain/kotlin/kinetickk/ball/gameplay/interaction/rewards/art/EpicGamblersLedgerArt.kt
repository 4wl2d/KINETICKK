// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.sin

// Gambler's Ledger.

private const val LedgerPivotX = 150f
private const val LedgerPivotY = 486f
private const val LedgerCardW = 54f
private const val LedgerCardH = 80f
private const val LedgerCardLean = 4f
private const val LedgerCards = 5
private val LedgerAngles = floatArrayOf(-33f, -17f, -1f, 15f, 33f)
private val LedgerReach = floatArrayOf(112f, 112f, 112f, 112f, 128f)

/** Board x and y of the point ([u], [v]) (from the card's centre, v down) of fanned card [index]. */
private fun ledgerX(index: Int, u: Float, v: Float): Float {
    val a = LedgerAngles[index] * Deg
    val cx = LedgerPivotX + LedgerReach[index] * sin(a)
    val lean = LedgerCardLean * (0.5f - v / LedgerCardH)
    return cx + (u + lean) * cos(a) - v * sin(a)
}

private fun ledgerY(index: Int, u: Float, v: Float): Float {
    val a = LedgerAngles[index] * Deg
    val cy = LedgerPivotY - LedgerReach[index] * cos(a)
    val lean = LedgerCardLean * (0.5f - v / LedgerCardH)
    return cy + (u + lean) * sin(a) + v * cos(a)
}

private fun Path.ledgerQuad(index: Int, u0: Float, v0: Float, u1: Float, v1: Float) {
    moveB(ledgerX(index, u0, v0), ledgerY(index, u0, v0))
    lineB(ledgerX(index, u1, v0), ledgerY(index, u1, v0))
    lineB(ledgerX(index, u1, v1), ledgerY(index, u1, v1))
    lineB(ledgerX(index, u0, v1), ledgerY(index, u0, v1))
    close()
}

/** A diamond (a square turned on its corner) of half-diagonal [r] at ([u], [v]) on card [index]. */
private fun Path.ledgerDiamond(index: Int, u: Float, v: Float, r: Float) {
    moveB(ledgerX(index, u, v - r * 1.2f), ledgerY(index, u, v - r * 1.2f))
    lineB(ledgerX(index, u + r, v), ledgerY(index, u + r, v))
    lineB(ledgerX(index, u, v + r * 1.2f), ledgerY(index, u, v + r * 1.2f))
    lineB(ledgerX(index, u - r, v), ledgerY(index, u - r, v))
    close()
}

/**
 * Gambler's Ledger: a fanned hand of five little reward cards (leaning like the card itself, each
 * with its band and a diamond) over a ruled ledger with a margin and an amount column, the last
 * card drawn out of the hand: one more on the table.
 */
internal fun DrawScope.drawGamblersLedgerArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 404f - 140f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line, join = StrokeJoin.Round)
    val ink = cache.stroke(b.l(2f), StrokeCap.Round)
    val w = LedgerCardW * 0.5f
    val h = LedgerCardH * 0.5f
    fadedArt(frame, 150f, 404f, 196f, 140f, 0.5f) {
        // The ledger: ruled lines, a double margin rule and the amount column, entries in ink.
        drawPath(cache.path(0) {
            for (row in 0 until 11) segB(40f, 310f + row * 12.2f, 262f, 310f + row * 12.2f)
        }, hue, alpha = 0.14f, style = hair)
        drawPath(cache.path(1) {
            segB(68f, 298f, 68f, 442f)
            segB(72f, 298f, 72f, 442f)
        }, hue, alpha = 0.2f, style = hair)
        drawPath(cache.path(2) {
            for (row in 0 until 10) {
                val y = 316f + row * 12.2f - 4f
                val text = 18f + frame.random(500 + row) * 70f
                if (frame.random(520 + row) > 0.25f) segB(80f, y, 80f + text, y)
                val amount = 8f + frame.random(540 + row) * 22f
                segB(256f - amount, y, 256f, y)
            }
        }, hue, alpha = 0.12f, style = ink)
        for (index in 0 until LedgerCards) {
            val card = cache.path(3 + index * 3) { ledgerQuad(index, -w, -h, w, h) }
            val drawn = index == LedgerCards - 1
            drawPath(card, Kk.Ink, alpha = 0.94f)
            drawPath(card, cache.brush(index) {
                lightBrush(hue, ledgerX(index, -w, -h), ledgerY(index, -w, -h), ledgerX(index, w, h), ledgerY(index, w, h), if (drawn) 0.18f else 0.12f, 0.03f)
            })
            drawPath(cache.path(4 + index * 3) { ledgerQuad(index, -w, -h, w, -h + 12f) }, hue, alpha = if (drawn) 0.2f else 0.12f)
            drawPath(card, hue, alpha = if (drawn) RewardArtAlpha.HIGH else 0.18f, style = line)
            drawPath(cache.path(5 + index * 3) {
                ledgerQuad(index, -w + 5f, -h + 17f, w - 5f, h - 5f)
                ledgerDiamond(index, 0f, 6f, 9f)
                ledgerDiamond(index, -w + 10f, -h + 23f, 2.6f)
                ledgerDiamond(index, w - 10f, h - 11f, 2.6f)
            }, hue, alpha = if (drawn) 0.22f else 0.14f, style = hair)
        }
        // The drawn card's diamond, lit.
        drawPath(cache.path(18) { ledgerDiamond(LedgerCards - 1, 0f, 6f, 6f) }, hue, alpha = RewardArtAlpha.HIGH)
    }
}
