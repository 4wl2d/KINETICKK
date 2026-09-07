// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.ball.gameplay.interaction.canvas.drawCore
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.Cyan
import kinetickk.foundation.design.White
import kotlin.math.cos
import kotlin.math.sin

/** Bounded presentation only: the accepted run is already finished and cannot gain rewards. */
internal fun DrawScope.drawCoreDeath(engine: GameplayRenderModel, elapsed: Float) {
    val core = Offset(engine.coreX - engine.cameraX + size.width * 0.5f,
        engine.coreY - engine.cameraY + size.height * 0.5f)
    if (elapsed < 0.12f) {
        scale(1f + elapsed * 2.5f, pivot = core) { drawCore(engine, core) }
        return
    }
    val progress = ((elapsed - 0.12f) / 0.85f).coerceIn(0f, 1f)
    if (progress >= 1f) return
    val expansion = 1f - (1f - progress) * (1f - progress)
    val opacity = (1f - progress) * (1f - progress)
    drawCircle(Cyan.copy(alpha = opacity * 0.55f), 22f + expansion * 170f, core, style = Stroke(2f))
    drawCircle(White.copy(alpha = opacity * 0.22f), 18f + expansion * 70f, core, style = Stroke(5f * (1f - progress)))
    val count = when (engine.settings.particleDensity) {
        ParticleDensity.LOW -> 8
        ParticleDensity.NORMAL -> 16
        ParticleDensity.HIGH -> 24
    }
    repeat(count) { index ->
        val angle = index * 6.2831855f / count + 0.24f
        val direction = Offset(cos(angle), sin(angle))
        val radius = 18f + expansion * (85f + (index % 4) * 27f)
        val start = core + direction * radius
        val end = start + direction * ((7f + index % 3 * 4f) * (1f - progress))
        drawLine(if (index % 3 == 0) White.copy(alpha = opacity) else Cyan.copy(alpha = opacity),
            start, end, 2.5f, StrokeCap.Round)
    }
}
