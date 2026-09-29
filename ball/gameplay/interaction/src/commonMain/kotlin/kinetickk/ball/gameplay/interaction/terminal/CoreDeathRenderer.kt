// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.ball.gameplay.interaction.canvas.drawCore
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkLerp
import kinetickk.foundation.design.kkStroke

/** Seconds after which the in-world death has fully faded (the report covers the world). */
internal const val CoreDeathDuration = 1.1f

private const val SwellSeconds = 0.12f

/**
 * Bounded presentation only: the accepted run is already finished and cannot gain rewards.
 * The Core swells, then shatters into threat-colored wedges around a black singularity with a
 * flash ring and debris; everything fades out by [CoreDeathDuration].
 */
internal fun DrawScope.drawCoreDeath(
    engine: GameplayRenderModel,
    elapsed: Float,
    roles: KkRolePalette,
) {
    val core = Offset(engine.coreX - engine.cameraX + size.width * 0.5f,
        engine.coreY - engine.cameraY + size.height * 0.5f)
    if (elapsed < SwellSeconds) {
        scale(1f + elapsed * 2.5f, pivot = core) { drawCore(engine, core, roles) }
        return
    }
    if (elapsed >= CoreDeathDuration) return
    val t = elapsed - SwellSeconds
    val fade = ((CoreDeathDuration - elapsed) / 0.3f).coerceIn(0f, 1f)
    val threat = roles.threat
    // Flash ring leaving the Core.
    val ring = (t / 0.6f).coerceIn(0f, 1f)
    if (ring < 1f) {
        val eased = KkEase.Out.transform(ring)
        drawCircle(threat.copy(alpha = (1f - eased) * fade), kkLerp(18f, 150f, eased), core, style = kkStroke(3f))
    }
    // Shards fly out on the board's timeline, compressed.
    val shape = CoreDeathShapes.shape
    val shardTime = 0.32f + t * 1.4f
    drawShatterShards(shape, CoreDeathShapes.paths, core, 0.26f, shardTime, threat, spread = 2.4f, alpha = fade, floating = false)
    val debris = when (engine.settings.particleDensity) {
        ParticleDensity.LOW -> 6
        ParticleDensity.NORMAL -> 12
        ParticleDensity.HIGH -> 18
    }
    drawShatterDebris(shape, core, 0.36f, shardTime, threat, debris, alpha = fade)
    // The black singularity that swallowed the Core pops in the middle.
    val pop = (t / 0.25f).coerceIn(0f, 1f)
    val popScale = if (pop < 0.6f) kkLerp(0f, 1.15f, KkEase.Pull.transform(pop / 0.6f)) else kkLerp(1.15f, 1f, (pop - 0.6f) / 0.4f)
    val radius = 11f * popScale
    if (radius > 0.5f) {
        for (index in GlowWidths.indices) {
            drawCircle(threat.copy(alpha = GlowAlphas[index] * fade), radius + GlowWidths[index], core)
        }
        drawCircle(Color.Black.copy(alpha = fade), radius, core)
        drawCircle(threat.copy(alpha = fade), radius + 1f, core, style = kkStroke(1.5f))
    }
}

// Glow (0 0 36px 8px threat @45 %) as stacked translucent discs, no blur.
private val GlowWidths = floatArrayOf(3f, 6f, 10f, 15f)
private val GlowAlphas = floatArrayOf(0.22f, 0.14f, 0.08f, 0.04f)

/** Defeat shatter geometry, built once on first draw. */
private object CoreDeathShapes {
    val shape: ReportShatterShape by lazy { reportShatterShape(victory = false) }
    val paths: List<Path> by lazy { shape.shards.map { it.path() } }
}
