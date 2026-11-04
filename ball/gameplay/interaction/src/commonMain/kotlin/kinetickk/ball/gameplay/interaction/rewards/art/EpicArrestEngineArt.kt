// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk

// Arrest Engine.

private const val ArrestX = 234f
private const val ArrestY = 354f
private const val ArrestR = 84f
private const val ArrestInner = 57f
private const val ArrestHat = 38f

/** The caliper body: an angular clamp over the disc's left edge (radius, angle pairs). */
private val ArrestCaliper = floatArrayOf(
    96f, 150f, 104f, 158f, 106f, 192f, 104f, 226f, 96f, 234f,
    54f, 230f, 52f, 211f, 52f, 173f, 54f, 154f,
)

/**
 * Arrest Engine: a cross-drilled, slotted brake disc cropped at the right edge, with an angular
 * caliper clamping its left side; the friction ring glows just past the pads.
 */
internal fun DrawScope.drawArrestEngineArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 372f - 140f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val slot = cache.stroke(b.l(2.4f), StrokeCap.Round)
    val edge = cache.stroke(b.line, join = StrokeJoin.Round)
    val x = ArrestX
    val y = ArrestY
    fadedArt(frame, 220f, 372f, 170f, 140f, 0.5f) {
        ovalB(Kk.Ink, x, y, ArrestR + 2f, ArrestR + 2f, Knockout)
        // The friction ring: machined metal, hottest just past the caliper.
        drawPath(cache.path(0) {
            fillType = PathFillType.EvenOdd
            ovalB(x, y, ArrestR, ArrestR)
            ovalB(x, y, ArrestInner, ArrestInner)
        }, cache.brush(0) {
            Brush.sweepGradient(
                0f to hue.copy(alpha = 0.07f), 0.12f to hue.copy(alpha = 0.11f), 0.25f to hue.copy(alpha = 0.05f), 0.4f to hue.copy(alpha = 0.09f),
                0.53f to hue.copy(alpha = 0.05f), 0.65f to hue.copy(alpha = RewardArtAlpha.HIGH), 0.76f to hue.copy(alpha = 0.12f), 0.88f to hue.copy(alpha = 0.05f),
                1f to hue.copy(alpha = 0.07f), center = b.at(x, y),
            )
        })
        drawPath(cache.path(1) {
            for (group in 0 until 20) {
                val base = group * 18f + 4f
                for (row in 0 until 3) {
                    val angle = base + row * 5f
                    val r = 63f + row * 7.5f
                    ovalB(polarX(x, r, angle), polarY(y, r, angle), 2.4f, 2.4f)
                }
            }
        }, Kk.Ink, alpha = 0.9f)
        drawPath(cache.path(2) {
            for (group in 0 until 10) {
                val angle = group * 36f + 13f
                segB(polarX(x, 61f, angle), polarY(y, 61f, angle), polarX(x, 82f, angle + 7f), polarY(y, 82f, angle + 7f))
            }
        }, Kk.Ink, alpha = 0.8f, style = slot)
        ovalB(hue, x, y, ArrestR, ArrestR, 0.22f, line)
        ovalB(hue, x, y, ArrestR - 4f, ArrestR - 4f, 0.08f, hair)
        ovalB(hue, x, y, ArrestInner, ArrestInner, 0.14f, hair)
        // The hat: lit face, five lug bolts, the bore.
        ovalB(cache.brush(1) { lightBrush(hue, x - ArrestHat, y - ArrestHat, x + ArrestHat, y + ArrestHat, 0.13f, 0.03f) }, x, y, ArrestHat, ArrestHat)
        ovalB(hue, x, y, ArrestHat, ArrestHat, 0.2f, line)
        ovalB(hue, x, y, 20f, 20f, 0.1f, hair)
        val lugs = cache.path(3) {
            for (lug in 0 until 5) {
                val angle = lug * 72f - 90f
                ovalB(polarX(x, 27f, angle), polarY(y, 27f, angle), 4.2f, 4.2f)
            }
        }
        drawPath(lugs, Kk.Ink, alpha = 0.85f)
        drawPath(lugs, hue, alpha = 0.18f, style = hair)
        ovalB(Kk.Ink, x, y, 12f, 12f, 0.9f)
        ovalB(hue, x, y, 12f, 12f, 0.2f, hair)
        // The caliper, clamping.
        val body = cache.path(4) {
            for (index in 0 until ArrestCaliper.size / 2) {
                val r = ArrestCaliper[index * 2]
                val angle = ArrestCaliper[index * 2 + 1]
                if (index == 0) moveB(polarX(x, r, angle), polarY(y, r, angle)) else lineB(polarX(x, r, angle), polarY(y, r, angle))
            }
            close()
        }
        drawPath(body, Kk.Ink, alpha = 0.92f)
        drawPath(body, cache.brush(2) { lightBrush(hue, x - 100f, y - 50f, x - 60f, y + 50f, 0.17f, 0.05f) })
        drawPath(body, hue, alpha = RewardArtAlpha.HIGH, style = edge)
        // The rotor's edge running through the caliper's slot, and the lit crown of the caliper.
        arcB(Kk.Ink, x, y, ArrestR, ArrestR, 157f, 70f, 0.9f, cache.stroke(b.l(3.4f)))
        arcB(hue, x, y, ArrestR + 2.5f, ArrestR + 2.5f, 157f, 70f, 0.18f, hair)
        drawPath(cache.path(7) {
            for (index in 0 until 5) {
                val r = ArrestCaliper[index * 2] - 2f
                val angle = ArrestCaliper[index * 2 + 1] + if (index == 0) 2f else if (index == 4) -2f else 0f
                if (index == 0) moveB(polarX(x, r, angle), polarY(y, r, angle)) else lineB(polarX(x, r, angle), polarY(y, r, angle))
            }
        }, hue, alpha = 0.3f, style = hair)
        drawPath(cache.path(5) {
            ovalB(polarX(x, 76f, 176f), polarY(y, 76f, 176f), 8f, 8f)
            ovalB(polarX(x, 76f, 208f), polarY(y, 76f, 208f), 8f, 8f)
            arcB(x, y, 62f, 62f, 160f, 66f, true)
            arcB(x, y, 98f, 98f, 162f, 60f, true)
        }, hue, alpha = 0.14f, style = hair)
        drawPath(cache.path(6) {
            ovalB(polarX(x, 88f, 160f), polarY(y, 88f, 160f), 3.4f, 3.4f)
            ovalB(polarX(x, 88f, 224f), polarY(y, 88f, 224f), 3.4f, 3.4f)
        }, hue, alpha = RewardArtAlpha.HIGH)
    }
}
